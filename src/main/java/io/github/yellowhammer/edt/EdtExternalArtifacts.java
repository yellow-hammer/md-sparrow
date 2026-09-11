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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import io.github.yellowhammer.designerxml.cf.CatalogNameConstraints;
import io.github.yellowhammer.designerxml.cf.ExternalArtifactKind;

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
    Proto proto = proto(kind);
    for (String file : PROJECT_FILES) {
      String text = EdtObjectScaffold.golden(proto.resource() + file);
      text = EdtObjectScaffold.renamed(EdtObjectScaffold.renamed(text, PROTO_BASE, baseName), proto.name(), name);
      if (file.endsWith("PROJECT.PMF") && runtime != null) {
        text = RUNTIME_VERSION.matcher(text).replaceFirst("Runtime-Version: " + Matcher.quoteReplacement(runtime));
      }
      write(project.resolve(file), text);
    }
    String object = EdtObjectScaffold.parametrize(EdtObjectScaffold.golden(proto.resource() + proto.objectFile()), proto.name(), name);
    Path objectMdo = project.resolve("src").resolve(proto.directory()).resolve(name).resolve(name + ".mdo");
    write(objectMdo, object);
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

    rewrite(objectMdo, oldName, newName);
    rewrite(project.resolve(EdtLayout.PROJECT_FILE), oldName, newName);
    Files.move(objectMdo, objectDir.resolve(newName + ".mdo"));
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
    try (Stream<Path> files = Files.walk(project)) {
      for (Path file : files.toList()) {
        String relative = project.relativize(file).toString();
        Path target = copy.resolve(relative.replace(oldName, newName));
        if (Files.isDirectory(file)) {
          Files.createDirectories(target);
        } else if (file.getFileName().toString().endsWith(".mdo")) {
          String text = Files.readString(file, StandardCharsets.UTF_8);
          write(target, EdtObjectScaffold.parametrize(text, oldName, newName));
        } else if (file.getFileName().toString().equals(EdtLayout.PROJECT_FILE)) {
          write(target, EdtObjectScaffold.renamed(Files.readString(file, StandardCharsets.UTF_8), oldName, newName));
        } else {
          Files.createDirectories(target.getParent());
          Files.copy(file, target);
        }
      }
    }
    return copy.resolve(project.relativize(objectDir).toString().replace(oldName, newName)).resolve(newName + ".mdo");
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
