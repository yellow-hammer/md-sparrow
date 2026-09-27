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

import io.github.yellowhammer.designerxml.DesignerXml;
import io.github.yellowhammer.designerxml.SchemaVersion;

import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Проекция канонического эталона в формат V равна выгрузке платформы формата V.
 *
 * <p>Эталоны всех форматов лежат в submodule samples-1c-platform: они и есть проверка проекции.
 * Объекты, внешние объекты и {@code Configuration.xml} сверяются побайтно (BOM, CRLF, {@code <X/>}), пустое
 * расширение - после замены UUID метками: платформа выдаёт их случайно в каждом снимке.
 */
class FormatProjectionTest {

  private static SchemaVersion canonical() {
    return GoldenSnapshots.canonical();
  }

  private static List<String> canonicalFiles(String set) {
    List<String> files = GoldenSnapshots.files(canonical(), set);
    assertThat(files).as("канонический набор %s", set).isNotEmpty();
    return files;
  }

  @ParameterizedTest
  @EnumSource(SchemaVersion.class)
  void объектыИКонфигурацияСовпадаютСВыгрузкойПлатформыПобайтно(SchemaVersion version) {
    SoftAssertions softly = new SoftAssertions();
    int compared = 0;
    for (String file : canonicalFiles(GoldenSnapshots.CF)) {
      String source = GoldenSnapshots.read(canonical(), GoldenSnapshots.CF, file);
      Path expected = GoldenSnapshots.format(version).resolve(GoldenSnapshots.CF).resolve(file);
      String projected;
      try {
        projected = FormatProjection.project(source, version);
      } catch (IllegalArgumentException absent) {
        // вида ещё нет в формате: нет его и в выгрузке платформы этого формата
        softly.assertThat(absent).as("%s в формате %s", file, version).hasMessageContaining("появился в формате");
        softly.assertThat(expected).as("%s в формате %s", file, version).doesNotExist();
        continue;
      }
      if (!Files.isRegularFile(expected)) {
        // эталон формата снят семенем, где этого вида не было
        continue;
      }
      String actual = GoldenSnapshots.read(expected);
      if (CfLayout.CONFIGURATION_XML.equals(file)) {
        projected = withCompositionOf(projected, actual);
      }
      softly.assertThat(projected).as("%s в формате %s", file, version).isEqualTo(actual);
      compared++;
    }
    softly.assertAll();
    assertThat(compared)
      .as("сверено файлов формата %s: каждый файл эталона формата", version)
      .isEqualTo(GoldenSnapshots.files(version, GoldenSnapshots.CF).size());
  }

  /**
   * Состав {@code ChildObjects} конфигурации - это состав семени, с которого снят эталон, а не
   * свойство формата: у эталонов, снятых прежним семенем, в нём меньше видов. Оставляет
   * в проекции только строки состава, которые есть в эталоне, и проверяет, что эталон не знает
   * строк, которых нет в проекции.
   */
  private static String withCompositionOf(String projected, String actual) {
    List<String> actualItems = compositionLines(actual);
    List<String> projectedItems = compositionLines(projected);
    assertThat(projectedItems).as("состав конфигурации").containsAll(actualItems);
    int start = projected.indexOf("<ChildObjects>");
    int end = projected.indexOf("</ChildObjects>", start);
    StringBuilder kept = new StringBuilder();
    for (String line : projected.substring(start, end).split("(?<=\n)")) {
      if (!isCompositionLine(line) || actualItems.contains(line.strip())) {
        kept.append(line);
      }
    }
    return projected.substring(0, start) + kept + projected.substring(end);
  }

  private static List<String> compositionLines(String configuration) {
    int start = configuration.indexOf("<ChildObjects>");
    int end = configuration.indexOf("</ChildObjects>", start);
    return configuration.substring(start, end).lines()
      .filter(FormatProjectionTest::isCompositionLine)
      .map(String::strip)
      .toList();
  }

  private static boolean isCompositionLine(String line) {
    String item = line.strip();
    return item.startsWith("<") && !item.startsWith("<ChildObjects") && item.endsWith(">");
  }

  @ParameterizedTest
  @EnumSource(SchemaVersion.class)
  void пустоеРасширениеСовпадаетСВыгрузкойПлатформыСТочностьюДоUuid(SchemaVersion version) {
    SoftAssertions softly = new SoftAssertions();
    for (String file : GoldenSnapshots.files(version, GoldenSnapshots.CFE)) {
      String projected = FormatProjection.project(
        GoldenSnapshots.read(canonical(), GoldenSnapshots.CFE, file), version);
      softly.assertThat(GoldenSnapshots.normalizeUuids(projected))
        .as("%s в формате %s", file, version)
        .isEqualTo(GoldenSnapshots.normalizeUuids(GoldenSnapshots.read(version, GoldenSnapshots.CFE, file)));
    }
    softly.assertAll();
  }

  @ParameterizedTest
  @EnumSource(SchemaVersion.class)
  void пустоеРасширениеЧитаетсяМодельюФормата(SchemaVersion version) throws Exception {
    // проекция канонического расширения читается моделью каждого формата
    for (String file : canonicalFiles(GoldenSnapshots.CFE)) {
      String projected = FormatProjection.project(
        GoldenSnapshots.read(canonical(), GoldenSnapshots.CFE, file), version);
      assertThat(projected).contains("version=\"" + version.metadataObjectVersionAttribute() + "\"");
      unmarshal(version, projected);
    }
  }

  @Test
  void каноническийФайлВСвоёмФорматеНеМеняется() {
    for (String set : List.of(
      GoldenSnapshots.CF, GoldenSnapshots.CFE, GoldenSnapshots.EXTERNAL, GoldenSnapshots.EXTERNAL_FULL)) {
      for (String file : canonicalFiles(set)) {
        String source = GoldenSnapshots.read(canonical(), set, file);
        assertThat(FormatProjection.project(source, canonical())).as(file).isEqualTo(source);
      }
    }
  }

  @Test
  void кБолееНовомуФорматуНеПроецирует() {
    SchemaVersion older = SchemaVersion.values()[canonical().ordinal() - 1];
    String file = canonicalFiles(GoldenSnapshots.CF).get(0);
    String olderXml = GoldenSnapshots.read(older, GoldenSnapshots.CF, file);

    assertThatThrownBy(() -> FormatProjection.project(olderXml, canonical()))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining(canonical().metadataObjectVersionAttribute());
  }

  @Test
  void элементНеНаОтдельнойСтрокеНеВырезаетсяМолча() {
    // строка, которой нет в соседнем старом формате, - элемент, который проекция вырезает;
    // ставим его на одну строку с предыдущим элементом
    SchemaVersion older = SchemaVersion.values()[canonical().ordinal() - 1];
    String file = null;
    int line = -1;
    for (String candidate : canonicalFiles(GoldenSnapshots.CF)) {
      if (!Files.isRegularFile(GoldenSnapshots.format(older).resolve(GoldenSnapshots.CF).resolve(candidate))) {
        continue;
      }
      List<String> newer = GoldenSnapshots.read(canonical(), GoldenSnapshots.CF, candidate).lines().toList();
      List<String> old = GoldenSnapshots.read(older, GoldenSnapshots.CF, candidate).lines().toList();
      int differs = firstDifferentLine(newer, old);
      if (differs > 0 && newer.size() > old.size() && newer.get(differs).trim().endsWith("/>")) {
        file = candidate;
        line = differs;
        break;
      }
    }
    assertThat(file).as("файл, где старый формат теряет пустой элемент").isNotNull();

    List<String> lines = GoldenSnapshots.read(canonical(), GoldenSnapshots.CF, file).lines().toList();
    String joined = String.join("\r\n", lines.subList(0, line - 1))
      + "\r\n" + lines.get(line - 1) + lines.get(line).trim()
      + "\r\n" + String.join("\r\n", lines.subList(line + 1, lines.size()));

    assertThatThrownBy(() -> FormatProjection.project(joined, older))
      .isInstanceOf(IllegalStateException.class)
      .hasMessageContaining("построчно не вырезать");
  }

  /**
   * Вид есть в модели формата тогда и только тогда, когда его выгружает платформа формата: сверка
   * с эталонами, снятыми со всеми видами. Виды только прибавляются: появившись, вид есть и во всех
   * более новых форматах.
   */
  @ParameterizedTest
  @EnumSource(SchemaVersion.class)
  void видОбъектаЕстьВМоделиФорматаЕслиЕгоВыгружаетПлатформа(SchemaVersion version) {
    Path snapshot = GoldenSnapshots.format(version).resolve(GoldenSnapshots.CF);
    List<String> exported = compositionLines(GoldenSnapshots.read(snapshot.resolve(CfLayout.CONFIGURATION_XML)))
      .stream()
      .map(line -> line.substring(1, line.indexOf('>')))
      .distinct()
      .toList();
    for (MdObjectAddType type : MdObjectAddType.values()) {
      String kind = type.configurationXmlTag();
      if (exported.contains(kind)) {
        assertThat(FormatProjection.hasObjectKind(kind, version)).as("%s в формате %s", kind, version).isTrue();
      }
      if (FormatProjection.hasObjectKind(kind, version)) {
        for (SchemaVersion newer : SchemaVersion.values()) {
          if (newer.compareTo(version) > 0) {
            assertThat(FormatProjection.hasObjectKind(kind, newer)).as("%s в формате %s", kind, newer).isTrue();
          }
        }
      }
    }
    boolean complete = java.util.Arrays.stream(MdObjectAddType.values())
      .filter(type -> FormatProjection.hasObjectKind(type.configurationXmlTag(), version))
      .allMatch(type -> exported.contains(type.configurationXmlTag()));
    if (complete) {
      // эталон со всеми видами формата: и обратное - вида нет в выгрузке, значит нет и в модели
      for (MdObjectAddType type : MdObjectAddType.values()) {
        assertThat(FormatProjection.hasObjectKind(type.configurationXmlTag(), version))
          .as("%s в формате %s", type, version)
          .isEqualTo(exported.contains(type.configurationXmlTag()));
      }
    }
  }

  @ParameterizedTest
  @EnumSource(SchemaVersion.class)
  void внешниеОбъектыСовпадаютСВыгрузкойПлатформыПобайтно(SchemaVersion version) throws Exception {
    SoftAssertions softly = new SoftAssertions();
    List<String> platformFiles = GoldenSnapshots.files(version, GoldenSnapshots.EXTERNAL);
    for (String file : canonicalFiles(GoldenSnapshots.EXTERNAL)) {
      String projected = FormatProjection.project(
        GoldenSnapshots.read(canonical(), GoldenSnapshots.EXTERNAL, file), version);
      // формат, которого нет среди снятых платформой, проверяется моделью
      unmarshal(version, projected);
      if (platformFiles.contains(file)) {
        softly.assertThat(projected)
          .as("%s в формате %s", file, version)
          .isEqualTo(GoldenSnapshots.read(version, GoldenSnapshots.EXTERNAL, file));
      }
    }
    softly.assertAll();
    assertThat(platformFiles)
      .as("эталоны формата %s - те же объекты, что в каноническом наборе", version)
      .isSubsetOf(canonicalFiles(GoldenSnapshots.EXTERNAL));
  }

  /**
   * Внешние объекты с формами: описания объектов и форм ({@code MetaDataObject}) и содержимое форм
   * ({@code Form} logform), в том числе пустая форма - эталон cf-form-add.
   *
   * <p>Содержимое формы проекция восстанавливает не всё: 8.5.1 (2.21) у таблицы пишет
   * {@code HorizontalLinesBWA}, {@code VerticalLinesBWA} и {@code UseAlternationRowColorBWA} вместо
   * {@code HorizontalLines}, {@code VerticalLines} и {@code UseAlternationRowColor}, а у флажка не пишет
   * {@code CheckBoxType} со значением {@code Auto}. Прежние свойства есть в моделях всех форматов, но
   * в каноническом эталоне их нет, поэтому в проекции их нет тоже; при сверке их строки убираются из
   * эталона формата, если в каноническом файле такого элемента нет вовсе. В пустой форме их нет.
   */
  @ParameterizedTest
  @EnumSource(SchemaVersion.class)
  void формыСовпадаютСВыгрузкойПлатформыПобайтно(SchemaVersion version) throws Exception {
    SoftAssertions softly = new SoftAssertions();
    List<String> platformFiles = GoldenSnapshots.files(version, GoldenSnapshots.EXTERNAL_FULL);
    List<String> canonicalFiles = canonicalFiles(GoldenSnapshots.EXTERNAL_FULL);
    assertThat(canonicalFiles).as("содержимое форм").anyMatch(file -> file.endsWith("/Ext/Form.xml"));
    for (String file : canonicalFiles) {
      String source = GoldenSnapshots.read(canonical(), GoldenSnapshots.EXTERNAL_FULL, file);
      String projected = FormatProjection.project(source, version);
      softly.assertThat(projected).as("%s в формате %s", file, version)
        .contains("version=\"" + version.metadataObjectVersionAttribute() + "\"");
      unmarshal(version, projected);
      if (platformFiles.contains(file)) {
        softly.assertThat(projected)
          .as("%s в формате %s", file, version)
          .isEqualTo(withoutFormPropertiesRenamedIn221(
            GoldenSnapshots.read(version, GoldenSnapshots.EXTERNAL_FULL, file), source));
      }
    }
    softly.assertAll();
    assertThat(platformFiles).as("эталоны формата %s", version).isSubsetOf(canonicalFiles);
  }

  private static String withoutFormPropertiesRenamedIn221(String platform, String canonical) {
    String result = platform;
    for (String name : List.of("HorizontalLines", "VerticalLines", "UseAlternationRowColor", "CheckBoxType")) {
      if (!canonical.contains("<" + name + ">")) {
        result = result.replaceAll("(?m)^[ \\t]*<" + name + ">[^<]*</" + name + ">\\r?\\n", "");
      }
    }
    return result;
  }

  /** Читает текст моделью формата; BOM разбирается из байтов, а не из символов. */
  private static void unmarshal(SchemaVersion version, String xml) throws Exception {
    DesignerXml.unmarshal(version, new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
  }

  /** Первая различающаяся строка после заголовка (объявление XML и корень с версией). */
  private static int firstDifferentLine(List<String> first, List<String> second) {
    for (int i = 2; i < Math.min(first.size(), second.size()); i++) {
      if (!first.get(i).equals(second.get(i))) {
        return i;
      }
    }
    return -1;
  }
}
