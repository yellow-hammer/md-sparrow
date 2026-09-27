/*
 * This file is a part of md-sparrow.
 *
 * Copyright (c) 2026
 * Ivan Karlo <i.karlo@outlook.com> and contributors
 *
 * SPDX-License-Identifier: LGPL-3.0-or-later
 *
 * md-sparrow is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3.0 of the License, or (at your option) any later version.
 *
 * md-sparrow is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with md-sparrow.
 */
package io.github.yellowhammer.edt;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import javax.xml.stream.XMLStreamException;

import io.github.yellowhammer.designerxml.cf.CatalogNameConstraints;
import io.github.yellowhammer.designerxml.cf.ExternalArtifactKind;
import io.github.yellowhammer.edt.EdtObjectRegions.Region;

/**
 * Внешние обработки и отчёты проекта 1С:EDT.
 *
 * У 1С:EDT внешний объект живёт отдельным проектом с базовым проектом в
 * манифесте: {@code <Имя>/src/ExternalDataProcessors/<Имя>/<Имя>.mdo}.
 * Заготовкой служит проект, который сама 1С:EDT записала при импорте пустого
 * внешнего объекта конфигуратора; у него меняются имя, базовый проект и
 * идентификаторы.
 */
public final class EdtExternalArtifacts {

  private static final String PROTO_BASE = "Основа";
  private static final List<String> PROJECT_FILES = List.of(
      ".project",
      ".settings/org.eclipse.core.resources.prefs",
      "DT-INF/PROJECT.PMF");
  private static final Pattern RUNTIME_VERSION = Pattern.compile("^Runtime-Version:\\s*(.+)$", Pattern.MULTILINE);

  /**
   * Приставки ссылок внешнего объекта на себя по виду: путь к форме или макету и порождённые типы.
   * Типы табличных частей внешнего объекта платформа называет без приставки External.
   */
  private static final Map<String, String> SELF_REFERENCE_PREFIXES = Map.of(
      "ExternalDataProcessor",
      "ExternalDataProcessor|ExternalDataProcessorObject|DataProcessorTabularSection|DataProcessorTabularSectionRow",
      "ExternalReport",
      "ExternalReport|ExternalReportObject|ReportTabularSection|ReportTabularSectionRow");

  private EdtExternalArtifacts() {
  }

  /** Эталон вида: каталог объектов, имя заготовки и её каталог в сборке. */
  private record Proto(String directory, String name) {

    String resource() {
      return directory.substring(0, directory.length() - 1) + "/" + name + "/";
    }

    String objectFile() {
      return "src/" + directory + "/" + name + "/" + name + ".mdo";
    }
  }

  private static Proto proto(ExternalArtifactKind kind) {
    return switch (kind) {
      case DATA_PROCESSOR -> new Proto("ExternalDataProcessors", "Обработка1");
      case REPORT -> new Proto("ExternalReports", "Отчет1");
    };
  }

  /**
   * Создаёт проект внешнего объекта.
   *
   * @param artifactsRoot каталог, в котором лежат проекты внешних объектов
   * @param baseConfigurationMdo описание конфигурации, к которой относится объект
   * @param name имя объекта: так же назовётся проект
   * @param kind обработка или отчёт
   * @return описание созданного объекта
   * @throws IOException если файлы не читаются или не пишутся
   */
  public static Path create(Path artifactsRoot, Path baseConfigurationMdo, String name, ExternalArtifactKind kind)
      throws IOException {
    CatalogNameConstraints.check(name);
    Path project = artifactsRoot.resolve(name);
    if (Files.exists(project)) {
      throw new IllegalArgumentException("Каталог уже есть: " + project);
    }
    Path baseProject = EdtObjectScaffold.sourceRoot(baseConfigurationMdo).getParent();
    if (baseProject == null || !EdtLayout.isProject(baseProject)) {
      throw new IllegalArgumentException("Конфигурация лежит не в проекте EDT: " + baseConfigurationMdo);
    }
    String baseName = EdtExtensionScaffold.projectName(baseProject);
    String runtime = runtimeVersion(baseProject);
    String base = Files.readString(baseConfigurationMdo, StandardCharsets.UTF_8);
    // Переводы строк - как у проекта конфигурации, к которой относится объект
    String eol = base.contains("\r\n") ? "\r\n" : "\n";
    Proto proto = proto(kind);
    for (String file : PROJECT_FILES) {
      String text = EdtObjectScaffold.golden(proto.resource() + file);
      text = EdtObjectScaffold.renamed(EdtObjectScaffold.renamed(text, PROTO_BASE, baseName), proto.name(), name);
      if (file.endsWith("PROJECT.PMF") && runtime != null) {
        text = RUNTIME_VERSION.matcher(text).replaceFirst("Runtime-Version: " + Matcher.quoteReplacement(runtime));
      }
      write(project.resolve(file), text.replace("\r\n", "\n").replace("\n", eol));
    }
    String seed = EdtObjectScaffold.seed("external|" + kind.name() + "|" + name, base);
    String object = EdtObjectScaffold.parametrize(
        EdtObjectScaffold.golden(proto.resource() + proto.objectFile()), proto.name(), name, seed);
    Path objectMdo = project.resolve("src").resolve(proto.directory()).resolve(name).resolve(name + ".mdo");
    write(objectMdo, object.replace("\r\n", "\n").replace("\n", eol));
    return objectMdo;
  }

  /**
   * Переименовывает внешний объект вместе с его проектом.
   *
   * @param objectMdo описание объекта
   * @param newName новое имя
   * @return описание под новым именем
   * @throws IOException если файлы не читаются или не пишутся
   */
  public static Path rename(Path objectMdo, String newName) throws IOException {
    CatalogNameConstraints.check(newName);
    Path objectDir = objectDir(objectMdo);
    String oldName = objectDir.getFileName().toString();
    Path project = projectDir(objectMdo);
    Path renamedProject = project.getFileName().toString().equals(oldName)
        ? project.resolveSibling(newName)
        : project;
    if (!renamedProject.equals(project) && Files.exists(renamedProject)) {
      throw new IllegalArgumentException("Каталог уже есть: " + renamedProject);
    }
    if (Files.exists(objectDir.resolveSibling(newName))) {
      throw new IllegalArgumentException("Объект уже есть: " + newName);
    }
    Path sourceMdo = objectDir.resolve(oldName + ".mdo");
    String kind = EdtObjectReader.read(sourceMdo).kind();

    Files.writeString(sourceMdo,
        renamedObject(Files.readString(sourceMdo, StandardCharsets.UTF_8), kind, oldName, newName),
        StandardCharsets.UTF_8);
    try (Stream<Path> files = Files.walk(objectDir)) {
      for (Path form : files.filter(EdtExternalArtifacts::isForm).toList()) {
        String text = Files.readString(form, StandardCharsets.UTF_8);
        String renamed = selfReferences(text, kind, oldName, newName);
        if (!renamed.equals(text)) {
          Files.writeString(form, renamed, StandardCharsets.UTF_8);
        }
      }
    }
    rewrite(project.resolve(EdtLayout.PROJECT_FILE), oldName, newName);
    Files.move(sourceMdo, objectDir.resolve(newName + ".mdo"));
    Path renamedDir = objectDir.resolveSibling(newName);
    Files.move(objectDir, renamedDir);
    if (!renamedProject.equals(project)) {
      Files.move(project, renamedProject);
      renamedDir = renamedProject.resolve(project.relativize(renamedDir).toString());
    }
    return renamedDir.resolve(newName + ".mdo");
  }

  /**
   * Копирует внешний объект в новый проект под новым именем и со своими идентификаторами.
   *
   * @param objectMdo описание объекта
   * @param newName имя копии
   * @return описание копии
   * @throws IOException если файлы не читаются или не пишутся
   */
  public static Path duplicate(Path objectMdo, String newName) throws IOException {
    CatalogNameConstraints.check(newName);
    Path project = projectDir(objectMdo);
    Path objectDir = objectDir(objectMdo);
    String oldName = objectDir.getFileName().toString();
    Path copy = project.resolveSibling(newName);
    if (Files.exists(copy)) {
      throw new IllegalArgumentException("Каталог уже есть: " + copy);
    }
    // Новое имя получают только каталог объекта и файл его описания: формы, макеты и модули
    // называются своими именами, даже если имя объекта входит в них частью
    Path sourceMdo = objectDir.resolve(oldName + ".mdo");
    String kind = EdtObjectReader.read(sourceMdo).kind();
    Path copyDir = copy.resolve(project.relativize(objectDir.getParent()).toString()).resolve(newName);
    Path copyMdo = copyDir.resolve(newName + ".mdo");
    try (Stream<Path> files = Files.walk(project)) {
      for (Path file : files.toList()) {
        Path target;
        if (file.equals(sourceMdo)) {
          target = copyMdo;
        } else if (file.startsWith(objectDir)) {
          target = copyDir.resolve(objectDir.relativize(file).toString());
        } else {
          target = copy.resolve(project.relativize(file).toString());
        }
        if (Files.isDirectory(file)) {
          Files.createDirectories(target);
        } else if (file.equals(sourceMdo)) {
          String text = Files.readString(file, StandardCharsets.UTF_8);
          write(target, EdtObjectScaffold.freshUuids(renamedObject(text, kind, oldName, newName),
              EdtObjectScaffold.seed("duplicate|" + newName, text)));
        } else if (file.startsWith(objectDir) && isForm(file)) {
          write(target, selfReferences(Files.readString(file, StandardCharsets.UTF_8), kind, oldName, newName));
        } else if (file.getFileName().toString().equals(EdtLayout.PROJECT_FILE)) {
          write(target, EdtObjectScaffold.renamed(Files.readString(file, StandardCharsets.UTF_8), oldName, newName));
        } else {
          Files.createDirectories(target.getParent());
          Files.copy(file, target);
        }
      }
    }
    return copyMdo;
  }

  /**
   * Удаляет внешний объект вместе с проектом.
   *
   * @param objectMdo описание объекта
   * @throws IOException если файлы не удаляются
   */
  public static void delete(Path objectMdo) throws IOException {
    Path project = projectDir(objectMdo);
    try (Stream<Path> files = Files.walk(project)) {
      for (Path file : files.sorted(Comparator.reverseOrder()).toList()) {
        Files.delete(file);
      }
    }
  }

  /** Каталог объекта: {@code src/<Вид>/<Имя>}. */
  private static Path objectDir(Path objectMdo) {
    Path dir = objectMdo.toAbsolutePath().normalize().getParent();
    if (dir == null || !objectMdo.getFileName().toString().equals(dir.getFileName() + ".mdo")) {
      throw new IllegalArgumentException("Описание внешнего объекта названо не по объекту: " + objectMdo);
    }
    return dir;
  }

  /** Каталог проекта: три уровня выше описания объекта. */
  private static Path projectDir(Path objectMdo) {
    Path project = objectDir(objectMdo).getParent().getParent().getParent();
    if (project == null || !Files.isRegularFile(project.resolve(EdtLayout.PROJECT_FILE))) {
      throw new IllegalArgumentException("Внешний объект лежит не в проекте EDT: " + objectMdo);
    }
    return project;
  }

  /**
   * Описание объекта под новым именем: меняются имя верхнего уровня и ссылки объекта на себя.
   * Форма, реквизит или табличная часть с тем же именем, что у объекта, и текст синонима остаются
   * как есть: форма под новым именем потеряла бы своё содержимое в {@code Forms/<имя формы>}.
   */
  private static String renamedObject(String text, String kind, String oldName, String newName) throws IOException {
    Region name;
    try {
      name = EdtObjectRegions.property(text, "name");
    } catch (XMLStreamException error) {
      throw new IOException("Не удалось разобрать описание внешнего объекта", error);
    }
    if (!name.found()) {
      throw new IllegalArgumentException("В описании внешнего объекта нет имени");
    }
    String renamed = text.substring(0, name.start()) + "<name>" + newName + "</name>" + text.substring(name.end());
    return selfReferences(renamed, kind, oldName, newName);
  }

  /**
   * Ссылки объекта на себя: путь к форме или макету ({@code ExternalDataProcessor.<Имя>.Form.<Форма>})
   * и порождённые типы. Типы 1С:EDT пишет именами типов платформы (у обработки конфигурации в
   * ssl31-edt - {@code DataProcessorObject.<Имя>}, как в выгрузке конфигуратора), поэтому в формах
   * меняются те же ссылки, что в выгрузке.
   */
  private static String selfReferences(String text, String kind, String oldName, String newName) {
    String prefixes = SELF_REFERENCE_PREFIXES.get(kind);
    if (prefixes == null) {
      return text;
    }
    Pattern self = Pattern.compile(
        "(?<![\\p{L}\\p{N}_])(" + prefixes + ")\\." + Pattern.quote(oldName) + "(?![\\p{L}\\p{N}_])");
    return self.matcher(text).replaceAll(match -> Matcher.quoteReplacement(match.group(1) + "." + newName));
  }

  private static boolean isForm(Path file) {
    return Files.isRegularFile(file) && file.getFileName().toString().endsWith(".form");
  }

  private static void rewrite(Path file, String oldName, String newName) throws IOException {
    String text = Files.readString(file, StandardCharsets.UTF_8);
    Files.writeString(file, EdtObjectScaffold.renamed(text, oldName, newName), StandardCharsets.UTF_8);
  }

  private static String runtimeVersion(Path projectDir) throws IOException {
    Path manifest = projectDir.resolve("DT-INF").resolve("PROJECT.PMF");
    if (!Files.isRegularFile(manifest)) {
      return null;
    }
    Matcher matcher = RUNTIME_VERSION.matcher(Files.readString(manifest, StandardCharsets.UTF_8));
    return matcher.find() ? matcher.group(1).trim() : null;
  }

  private static void write(Path target, String text) throws IOException {
    Files.createDirectories(target.getParent());
    Files.writeString(target, text, StandardCharsets.UTF_8);
  }
}
