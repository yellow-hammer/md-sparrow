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
package io.github.yellowhammer.designerxml.cf;

import io.github.yellowhammer.designerxml.SchemaVersion;

import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Переименование и копирование внешнего объекта на выгрузках платформы (отчёт и обработка
 * с формами, реквизитами и табличными частями) и на внешних объектах ssl31 (модуль, справка,
 * формы с модулями, макеты). Платформа собирает объект из описания {@code <Имя>.xml} и каталога
 * содержимого {@code <Имя>/} рядом с ним, поэтому всё содержимое должно оказаться в каталоге
 * под новым именем: иначе ibcmd {@code config import --out} не находит формы, а модуль объекта
 * молча выпадает из собранного файла.
 */
class ExternalArtifactMutationsTest {

  private static final String RENAMED = "Переименованный";
  private static final String COPY = "Копия";
  private static final Pattern VERSION = Pattern.compile("<MetaDataObject\\b[^>]*\\bversion=\"([^\"]+)\"");
  private static final Pattern GENERATED_TYPE = Pattern.compile("GeneratedType name=\"([^\"]+)\" category=\"([^\"]+)\"");
  private static final Pattern UUID = Pattern.compile(
    "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");

  @TempDir
  Path workDir;

  /** Внешний объект из фикстур: его каталог и формат выгрузки. */
  record Source(Path dir, SchemaVersion version) {

    @Override
    public String toString() {
      return version.metadataObjectVersionAttribute() + " " + dir.getParent().getFileName() + "/" + dir.getFileName();
    }
  }

  /** Отчёт и обработка с формами, реквизитами и табличными частями в выгрузке платформы. */
  static Stream<Source> platformSources() {
    List<Source> out = new ArrayList<>();
    for (SchemaVersion version : SchemaVersion.values()) {
      out.addAll(objectsIn(GoldenSnapshots.format(version).resolve(GoldenSnapshots.EXTERNAL_FULL)));
    }
    assertThat(out).as("внешние объекты с формами в выгрузках платформы").isNotEmpty();
    return out.stream();
  }

  static Stream<Source> sources() {
    List<Source> out = new ArrayList<>(platformSources().toList());
    Path ssl = Path.of(System.getProperty("fixtures.ssl31.root"), "src");
    out.addAll(objectsIn(ssl.resolve("epf")));
    out.addAll(objectsIn(ssl.resolve("erf")));
    assertThat(out).as("внешние объекты в фикстурах").hasSizeGreaterThan(10);
    return out.stream();
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("sources")
  void переименованиеПереноситСодержимоеПодНовоеИмя(Source source) throws Exception {
    Path root = workDir.resolve("src");
    Path objectXml = copyOf(source, root);
    String stem = stem(objectXml);
    List<String> content = contentFiles(objectXml.getParent().resolve(stem));
    assertThat(content).as("содержимое исходника").isNotEmpty();

    ExternalArtifactMutations.rename(objectXml, source.version(), RENAMED);

    Path renamedDir = root.resolve(RENAMED);
    assertThat(renamedDir.resolve(RENAMED + ".xml")).isRegularFile();
    assertThat(renamedDir.resolve(stem)).doesNotExist();
    assertThat(contentFiles(renamedDir.resolve(RENAMED))).isEqualTo(content);
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("sources")
  void копияПолучаетСодержимоеПодСвоимИменем(Source source) throws Exception {
    Path root = workDir.resolve("src");
    Path objectXml = copyOf(source, root);
    String stem = stem(objectXml);
    List<String> content = contentFiles(objectXml.getParent().resolve(stem));

    Path copyXml = ExternalArtifactMutations.duplicate(objectXml, source.version(), COPY);

    assertThat(copyXml).isEqualTo(root.resolve(COPY).resolve(COPY + ".xml"));
    assertThat(copyXml.resolveSibling(stem)).doesNotExist();
    assertThat(contentFiles(copyXml.resolveSibling(COPY))).isEqualTo(content);
    assertThat(contentFiles(objectXml.getParent().resolve(stem))).as("исходник").isEqualTo(content);
  }

  /**
   * В выгрузке платформы имя объекта стоит только в его ссылках на себя: в путях к формам по
   * умолчанию, в порождённых типах и в типе основного реквизита форм. Поэтому после переименования
   * и копирования каждый XML совпадает с исходным, где имя заменено целым словом (UUID сравниваются
   * метками по порядку появления). Имя файла описания в этих выгрузках с именем объекта не
   * совпадает, и меняется именно имя объекта.
   */
  @ParameterizedTest(name = "{0}")
  @MethodSource("platformSources")
  void ссылкиНаСебяСледуютЗаИменем(Source source) throws Exception {
    Path root = workDir.resolve("src");
    Path objectXml = copyOf(source, root);
    String stem = stem(objectXml);
    String name = ExternalArtifactPropertiesEdit.read(objectXml, source.version()).name;
    Map<String, String> before = xmlTexts(objectXml.getParent(), stem);
    assertThat(String.join("", before.values()))
      .as("ссылки на себя в исходнике")
      .containsPattern("External(Report|DataProcessor)\\." + Pattern.quote(name) + "\\.Form\\.")
      .containsPattern("cfg:External(Report|DataProcessor)Object\\." + Pattern.quote(name) + "<");

    Path copyXml = ExternalArtifactMutations.duplicate(objectXml, source.version(), COPY);
    ExternalArtifactMutations.rename(objectXml, source.version(), RENAMED);

    assertRenamed(before, name, xmlTexts(root.resolve(RENAMED), RENAMED), RENAMED);
    assertRenamed(before, name, xmlTexts(copyXml.getParent(), COPY), COPY);
  }

  /**
   * У внешних объектов ssl31 ссылки на себя стоят в форме по умолчанию, в схеме компоновки отчёта
   * и в типе основного реквизита формы: после переименования и копирования со старым именем не
   * остаётся ни одной.
   */
  @ParameterizedTest(name = "{0}")
  @MethodSource("sources")
  void ссылокНаСтароеИмяНеОстаётся(Source source) throws Exception {
    Path root = workDir.resolve("src");
    Path objectXml = copyOf(source, root);
    String name = ExternalArtifactPropertiesEdit.read(objectXml, source.version()).name;
    long references = selfReferences(xmlTexts(objectXml.getParent(), stem(objectXml)), name);

    Path copyXml = ExternalArtifactMutations.duplicate(objectXml, source.version(), COPY);
    ExternalArtifactMutations.rename(objectXml, source.version(), RENAMED);

    Map<String, String> renamed = xmlTexts(root.resolve(RENAMED), RENAMED);
    Map<String, String> copy = xmlTexts(copyXml.getParent(), COPY);
    assertThat(selfReferences(renamed, name)).as("со старым именем").isZero();
    assertThat(selfReferences(copy, name)).as("со старым именем в копии").isZero();
    assertThat(selfReferences(renamed, RENAMED)).isEqualTo(references);
    // У копии ссылок на себя может стать больше: свои типы она называет сама (см. ниже)
    assertThat(selfReferences(copy, COPY)).isGreaterThanOrEqualTo(references);
    assertThat(ExternalArtifactPropertiesEdit.read(copyXml, source.version()).name).isEqualTo(COPY);
  }

  /**
   * У копии свои идентификаторы: у описания объекта, реквизитов, табличных частей, порождённых
   * типов и у описаний форм и макетов, а номер класса платформы тот же. Повтор той же копии даёт
   * те же файлы.
   */
  @ParameterizedTest(name = "{0}")
  @MethodSource("sources")
  void копияПолучаетСвоиИдентификаторы(Source source) throws Exception {
    Path objectXml = copyOf(source, workDir.resolve("a"));
    Map<String, String> original = xmlTexts(objectXml.getParent(), stem(objectXml));
    Set<String> originalIds = descriptorIds(original, false);
    assertThat(originalIds).as("идентификаторы исходника").isNotEmpty();

    Path copyXml = ExternalArtifactMutations.duplicate(objectXml, source.version(), COPY);
    Path again = ExternalArtifactMutations.duplicate(copyOf(source, workDir.resolve("b")), source.version(), COPY);

    Map<String, String> copy = xmlTexts(copyXml.getParent(), COPY);
    assertThat(descriptorIds(copy, false)).hasSameSizeAs(originalIds).doesNotContainAnyElementsOf(originalIds);
    assertThat(descriptorIds(copy, true)).as("номер класса платформы").isEqualTo(descriptorIds(original, true));
    assertThat(xmlTexts(again.getParent(), COPY)).as("повтор копии").isEqualTo(copy);
  }

  /**
   * Внешние обработки и отчёт ssl31 сохранены из объектов конфигурации с их идентификаторами типов:
   * тип объекта записан как {@code DataProcessorObject.<Имя>}, и так же на него ссылается основной
   * реквизит формы. У копии идентификаторы свои, поэтому и типы у неё собственные, названные так,
   * как их называет платформа у внешнего объекта, а формы копии ссылаются на них, а не на объект
   * конфигурации. Проверено ibcmd 8.3.27 в базе с конфигурацией ssl31: такая копия собирается, а
   * разобранная совпадает с разобранным исходником с точностью до имени, типов и идентификаторов.
   */
  @ParameterizedTest(name = "{0}")
  @MethodSource("sources")
  void типыКопииСобственные(Source source) throws Exception {
    Path objectXml = copyOf(source, workDir.resolve("src"));
    Map<String, String> before = generatedTypes(GoldenSnapshots.read(objectXml));
    assertThat(before).as("порождённые типы исходника").isNotEmpty();

    Path copyXml = ExternalArtifactMutations.duplicate(objectXml, source.version(), COPY);

    Map<String, String> copy = xmlTexts(copyXml.getParent(), COPY);
    Map<String, String> after = generatedTypes(copy.get("<описание>"));
    assertThat(List.copyOf(after.values())).isEqualTo(List.copyOf(before.values()));
    for (Map.Entry<String, String> type : after.entrySet()) {
      assertThat(type.getKey()).as(type.getValue()).matches(
        "(External(Report|DataProcessor)Object|(Report|DataProcessor)TabularSection(Row)?)\\." + COPY + "(\\.[^.]+)?");
    }
    String texts = String.join("", copy.values());
    for (String old : before.keySet()) {
      assertThat(texts).as("ссылки на тип исходника").doesNotContainPattern(
        "(?<![\\p{L}\\p{N}_.])" + Pattern.quote(old) + "(?![\\p{L}\\p{N}_.])");
    }
  }

  /** Порождённые типы описания: имя и категория в порядке файла. */
  private static Map<String, String> generatedTypes(String objectXml) {
    Map<String, String> out = new LinkedHashMap<>();
    Matcher type = GENERATED_TYPE.matcher(objectXml);
    while (type.find()) {
      out.put(type.group(1), type.group(2));
    }
    return out;
  }

  /** UUID в описаниях (корень {@code MetaDataObject}): идентификаторы объекта или номера класса платформы. */
  private static Set<String> descriptorIds(Map<String, String> texts, boolean classIds) {
    Set<String> out = new TreeSet<>();
    for (String text : texts.values()) {
      if (!text.contains("<MetaDataObject")) {
        continue;
      }
      Matcher uuid = UUID.matcher(text);
      while (uuid.find()) {
        boolean classId = text.startsWith("ClassId>", uuid.start() - "ClassId>".length());
        if (classId == classIds) {
          out.add(uuid.group().toLowerCase(Locale.ROOT));
        }
      }
    }
    return out;
  }

  private static void assertRenamed(Map<String, String> before, String oldName, Map<String, String> after, String newName) {
    assertThat(after.keySet()).isEqualTo(before.keySet());
    Pattern token = Pattern.compile("(?<![\\p{L}\\p{N}_])" + Pattern.quote(oldName) + "(?![\\p{L}\\p{N}_])");
    for (Map.Entry<String, String> file : before.entrySet()) {
      String expected = token.matcher(file.getValue()).replaceAll(Matcher.quoteReplacement(newName));
      assertThat(GoldenSnapshots.normalizeUuids(after.get(file.getKey())))
        .as("%s под именем %s", file.getKey(), newName)
        .isEqualTo(GoldenSnapshots.normalizeUuids(expected));
    }
  }

  /** Пути к формам и макетам объекта и тип объекта: {@code ExternalReport.Имя.}, {@code ExternalReportObject.Имя}. */
  private static long selfReferences(Map<String, String> texts, String name) {
    Pattern self = Pattern.compile(
      "External(Report|DataProcessor)(Object)?\\." + Pattern.quote(name) + "(?![\\p{L}\\p{N}_])");
    return texts.values().stream().mapToLong(text -> self.matcher(text).results().count()).sum();
  }

  /** XML объекта: описание под меткой и файлы содержимого относительными путями. */
  private static Map<String, String> xmlTexts(Path objectDir, String stem) throws IOException {
    Map<String, String> out = new TreeMap<>();
    out.put("<описание>", GoldenSnapshots.read(objectDir.resolve(stem + ".xml")));
    Path content = objectDir.resolve(stem);
    for (String file : contentFiles(content)) {
      if (file.endsWith(".xml")) {
        out.put(file, GoldenSnapshots.read(content.resolve(file)));
      }
    }
    return out;
  }

  /** Каталоги объектов в каталоге фикстур; формат - из заголовка описания. */
  private static List<Source> objectsIn(Path dir) {
    if (!Files.isDirectory(dir)) {
      return List.of();
    }
    try (Stream<Path> dirs = Files.list(dir)) {
      List<Source> out = new ArrayList<>();
      for (Path objectDir : dirs.filter(Files::isDirectory).sorted().toList()) {
        Matcher version = VERSION.matcher(Files.readString(objectXmlIn(objectDir)));
        assertThat(version.find()).as(objectDir.toString()).isTrue();
        out.add(new Source(objectDir, SchemaVersion.byVersionAttribute(version.group(1)).orElseThrow()));
      }
      return out;
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /** Описание объекта: единственный XML в его каталоге (имя файла не обязано совпадать с каталогом). */
  static Path objectXmlIn(Path objectDir) throws IOException {
    try (Stream<Path> files = Files.list(objectDir)) {
      List<Path> xml = files.filter(Files::isRegularFile)
        .filter(file -> file.getFileName().toString().endsWith(".xml"))
        .toList();
      assertThat(xml).as(objectDir.toString()).hasSize(1);
      return xml.get(0);
    }
  }

  /** Копия объекта в рабочем каталоге: индекс submodule не трогаем. */
  static Path copyOf(Source source, Path root) throws IOException {
    Path target = root.resolve(source.dir().getFileName().toString());
    try (Stream<Path> walk = Files.walk(source.dir())) {
      for (Path file : walk.toList()) {
        Path copy = target.resolve(source.dir().relativize(file).toString());
        if (Files.isDirectory(file)) {
          Files.createDirectories(copy);
        } else {
          Files.copy(file, copy);
        }
      }
    }
    return objectXmlIn(target);
  }

  /** Файлы каталога содержимого относительными путями через {@code /}. */
  static List<String> contentFiles(Path content) throws IOException {
    if (!Files.isDirectory(content)) {
      return List.of();
    }
    try (Stream<Path> walk = Files.walk(content)) {
      return walk.filter(Files::isRegularFile)
        .map(file -> content.relativize(file).toString().replace('\\', '/'))
        .sorted()
        .toList();
    }
  }

  static String stem(Path objectXml) {
    String name = objectXml.getFileName().toString();
    return name.substring(0, name.length() - ".xml".length());
  }
}
