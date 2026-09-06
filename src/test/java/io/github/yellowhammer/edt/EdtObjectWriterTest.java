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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.yellowhammer.designerxml.cf.MdCatalogPropertiesDto;
import io.github.yellowhammer.designerxml.cf.MdNamedPropertyDto;
import io.github.yellowhammer.designerxml.cf.MdObjectPropertiesDto;
import io.github.yellowhammer.designerxml.cf.MdTypeDescriptionDto;

/**
 * Точечная запись свойств объекта 1С:EDT.
 *
 * Проверяется то же, что и у выгрузки конфигуратора: круг чтение-запись,
 * неизменность файла без правок и одна правка - один изменённый участок.
 */
class EdtObjectWriterTest {

  private static EdtModel model;
  private static Path source;

  @TempDir
  Path workDir;

  @BeforeAll
  static void locate() throws Exception {
    model = EdtModel.bundled();
    source = Path.of(System.getProperty("fixtures.ssl31edt.root"), "ssl31", "src");
    assertThat(source).exists();
  }

  /** Копия объекта во временном каталоге: фикстуру не правим. */
  private Path copyOf(String objectPath) throws IOException {
    Path original = source.resolve(objectPath);
    Path copy = workDir.resolve(original.getFileName());
    Files.copy(original, copy);
    return copy;
  }

  /** Строки, которыми файлы различаются. */
  private static List<String> changedLines(String before, String after) {
    List<String> beforeLines = List.of(before.split("\\r?\\n", -1));
    List<String> afterLines = List.of(after.split("\\r?\\n", -1));
    List<String> changed = new ArrayList<>();
    for (String line : afterLines) {
      if (!beforeLines.contains(line)) {
        changed.add(line.trim());
      }
    }
    for (String line : beforeLines) {
      if (!afterLines.contains(line)) {
        changed.add(line.trim());
      }
    }
    return changed;
  }

  @Test
  void безПравокФайлНеМеняется() throws Exception {
    Path file = copyOf("Catalogs/Валюты/Валюты.mdo");
    byte[] before = Files.readAllBytes(file);

    MdObjectPropertiesDto dto = EdtObjectProperties.readDto(file, model);
    int changed = EdtObjectWriter.writeDto(file, dto, model);

    assertThat(changed).isZero();
    assertThat(Files.readAllBytes(file)).isEqualTo(before);
  }

  @Test
  void однаПравкаМеняетОднуСтроку() throws Exception {
    Path file = copyOf("Catalogs/Валюты/Валюты.mdo");
    String before = Files.readString(file, StandardCharsets.UTF_8);

    MdObjectPropertiesDto dto = EdtObjectProperties.readDto(file, model);
    MdCatalogPropertiesDto catalog = dto.catalog;
    assertThat(catalog.codeLength).isEqualTo("3");
    catalog.codeLength = "5";
    EdtObjectWriter.writeDto(file, dto, model);

    String after = Files.readString(file, StandardCharsets.UTF_8);
    assertThat(changedLines(before, after)).containsExactlyInAnyOrder(
        "<codeLength>3</codeLength>", "<codeLength>5</codeLength>");
    assertThat(EdtObjectProperties.readDto(file, model).catalog.codeLength).isEqualTo("5");
  }

  @Test
  void переводыСтрокСохраняются() throws Exception {
    Path file = copyOf("Catalogs/Валюты/Валюты.mdo");
    String before = Files.readString(file, StandardCharsets.UTF_8);

    MdObjectPropertiesDto dto = EdtObjectProperties.readDto(file, model);
    dto.catalog.codeLength = "7";
    EdtObjectWriter.writeDto(file, dto, model);

    // Перевод строки берётся у самого файла: смешивать их в одном файле нельзя
    String after = Files.readString(file, StandardCharsets.UTF_8);
    assertThat(eolKinds(after)).isEqualTo(eolKinds(before));
    assertThat(after.lines().count()).isEqualTo(before.lines().count());
  }

  @Test
  void файлСДругимПереводомСтрокНеСмешивается() throws Exception {
    // Файлы приходят и с CRLF, и с LF: перевод строки берётся у самого файла
    Path file = copyOf("Catalogs/Валюты/Валюты.mdo");
    Files.writeString(file, Files.readString(file, StandardCharsets.UTF_8).replace("\r\n", "\n"),
        StandardCharsets.UTF_8);

    MdObjectPropertiesDto dto = EdtObjectProperties.readDto(file, model);
    dto.catalog.hierarchical = true;
    dto.synonymRu = "Денежные единицы";
    EdtObjectWriter.writeDto(file, dto, model);

    String after = Files.readString(file, StandardCharsets.UTF_8);
    assertThat(eolKinds(after)).containsExactly("LF");
    assertThat(after.lines().filter(line -> line.contains("<hierarchical>")).toList())
        .containsExactly("  <hierarchical>true</hierarchical>");
    assertThat(EdtObjectProperties.readDto(file, model).synonymRu).isEqualTo("Денежные единицы");
  }

  /** Какими переводами строк написан файл. */
  private static Set<String> eolKinds(String text) {
    Set<String> kinds = new LinkedHashSet<>();
    for (int index = 0; index < text.length(); index++) {
      if (text.charAt(index) == '\n') {
        kinds.add(index > 0 && text.charAt(index - 1) == '\r' ? "CRLF" : "LF");
      }
    }
    return kinds;
  }

  @Test
  void перечислимоеСвойствоПишетсяЛитераломСхемы() throws Exception {
    Path file = copyOf("Catalogs/Валюты/Валюты.mdo");

    MdObjectPropertiesDto dto = EdtObjectProperties.readDto(file, model);
    dto.catalog.defaultPresentation = "AS_CODE";
    EdtObjectWriter.writeDto(file, dto, model);

    assertThat(Files.readString(file, StandardCharsets.UTF_8)).contains("<defaultPresentation>AsCode</defaultPresentation>");
    assertThat(EdtObjectProperties.readDto(file, model).catalog.defaultPresentation).isEqualTo("AS_CODE");
  }

  @Test
  void свойствоСоЗначениемПоУмолчаниюДописывается() throws Exception {
    Path file = copyOf("Catalogs/Валюты/Валюты.mdo");
    String before = Files.readString(file, StandardCharsets.UTF_8);
    assertThat(before).doesNotContain("hierarchical");

    MdObjectPropertiesDto dto = EdtObjectProperties.readDto(file, model);
    assertThat(dto.catalog.hierarchical).isFalse();
    dto.catalog.hierarchical = true;
    EdtObjectWriter.writeDto(file, dto, model);

    String after = Files.readString(file, StandardCharsets.UTF_8);
    assertThat(changedLines(before, after)).containsExactly("<hierarchical>true</hierarchical>");
    // Отступ у нового свойства тот же, что у соседей
    assertThat(after.lines().filter(line -> line.contains("<hierarchical>")).toList())
        .containsExactly("  <hierarchical>true</hierarchical>");
    assertThat(EdtObjectProperties.readDto(file, model).catalog.hierarchical).isTrue();
  }

  @Test
  void новоеСвойствоВстаётПоПорядкуСхемы() throws Exception {
    Path file = copyOf("Catalogs/Валюты/Валюты.mdo");

    MdObjectPropertiesDto dto = EdtObjectProperties.readDto(file, model);
    dto.catalog.hierarchical = true;
    EdtObjectWriter.writeDto(file, dto, model);

    String after = Files.readString(file, StandardCharsets.UTF_8);
    // Схема ставит иерархию между пояснением и числом уровней
    assertThat(after.indexOf("<hierarchical>")).isGreaterThan(after.indexOf("<explanation>"));
    assertThat(after.indexOf("<hierarchical>")).isLessThan(after.indexOf("<levelCount>"));
  }

  @Test
  void синонимПравитсяНаМесте() throws Exception {
    Path file = copyOf("Catalogs/Валюты/Валюты.mdo");
    String before = Files.readString(file, StandardCharsets.UTF_8);

    MdObjectPropertiesDto dto = EdtObjectProperties.readDto(file, model);
    dto.synonymRu = "Денежные единицы";
    EdtObjectWriter.writeDto(file, dto, model);

    String after = Files.readString(file, StandardCharsets.UTF_8);
    assertThat(changedLines(before, after)).containsExactlyInAnyOrder(
        "<value>Валюты</value>", "<value>Денежные единицы</value>");
    assertThat(EdtObjectProperties.readDto(file, model).synonymRu).isEqualTo("Денежные единицы");
  }

  @Test
  void несколькоПравокМеняютТолькоСвоиСтроки() throws Exception {
    Path file = copyOf("Documents/Анкета/Анкета.mdo");
    String before = Files.readString(file, StandardCharsets.UTF_8);

    MdObjectPropertiesDto dto = EdtObjectProperties.readDto(file, model);
    dto.document.numberLength = "9";
    dto.document.checkUnique = false;
    dto.comment = "Правка панели";
    int changed = EdtObjectWriter.writeDto(file, dto, model);

    String after = Files.readString(file, StandardCharsets.UTF_8);
    assertThat(changed).isEqualTo(3);
    assertThat(changedLines(before, after)).containsExactlyInAnyOrder(
        "<numberLength>11</numberLength>",
        "<numberLength>9</numberLength>",
        "<checkUnique>true</checkUnique>",
        "<checkUnique>false</checkUnique>",
        "<comment>Правка панели</comment>");

    MdObjectPropertiesDto written = EdtObjectProperties.readDto(file, model);
    assertThat(written.document.numberLength).isEqualTo("9");
    assertThat(written.document.checkUnique).isFalse();
    assertThat(written.comment).isEqualTo("Правка панели");
  }

  @Test
  void кругЧтениеЗаписьНеТеряетОстальное() throws Exception {
    Path file = copyOf("Catalogs/Валюты/Валюты.mdo");
    String before = Files.readString(file, StandardCharsets.UTF_8);

    MdObjectPropertiesDto dto = EdtObjectProperties.readDto(file, model);
    dto.catalog.codeLength = "4";
    EdtObjectWriter.writeDto(file, dto, model);
    MdObjectPropertiesDto written = EdtObjectProperties.readDto(file, model);
    written.catalog.codeLength = "3";
    EdtObjectWriter.writeDto(file, written, model);

    assertThat(Files.readString(file, StandardCharsets.UTF_8)).isEqualTo(before);
  }

  @Test
  void правкаСоставаОтклоняется() throws Exception {
    Path file = copyOf("Catalogs/Валюты/Валюты.mdo");
    byte[] before = Files.readAllBytes(file);

    MdObjectPropertiesDto dto = EdtObjectProperties.readDto(file, model);
    dto.attributes.remove(0);

    assertThatThrownBy(() -> EdtObjectWriter.writeDto(file, dto, model))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("attributes");
    assertThat(Files.readAllBytes(file)).isEqualTo(before);
  }

  @Test
  void правитСинонимРеквизита() throws Exception {
    Path file = copyOf("Catalogs/Валюты/Валюты.mdo");
    String before = Files.readString(file, StandardCharsets.UTF_8);

    MdObjectPropertiesDto dto = EdtObjectProperties.readDto(file, model);
    dto.attributes.get(0).synonymRu = "Курс из Интернета";
    EdtObjectWriter.writeDto(file, dto, model);

    String after = Files.readString(file, StandardCharsets.UTF_8);
    assertThat(changedLines(before, after)).containsExactlyInAnyOrder(
        "<value>Загружается из Интернета</value>", "<value>Курс из Интернета</value>");
    assertThat(EdtObjectProperties.readDto(file, model).attributes.get(0).synonymRu)
        .isEqualTo("Курс из Интернета");
  }

  @Test
  void правитПеречислимоеСвойствоРеквизита() throws Exception {
    Path file = copyOf("Catalogs/Валюты/Валюты.mdo");

    MdObjectPropertiesDto dto = EdtObjectProperties.readDto(file, model);
    assertThat(dto.attributes.get(0).fullTextSearch).isEqualTo("USE");
    dto.attributes.get(0).fullTextSearch = "DONT_USE";
    EdtObjectWriter.writeDto(file, dto, model);

    assertThat(EdtObjectProperties.readDto(file, model).attributes.get(0).fullTextSearch)
        .isEqualTo("DONT_USE");
    // Соседние реквизиты не тронуты
    assertThat(EdtObjectProperties.readDto(file, model).attributes.get(1).fullTextSearch)
        .isEqualTo(dto.attributes.get(1).fullTextSearch);
  }

  @Test
  void правитСвойстваРеквизитаТабличнойЧасти() throws Exception {
    Path file = copyOf("Catalogs/Валюты/Валюты.mdo");

    MdObjectPropertiesDto dto = EdtObjectProperties.readDto(file, model);
    MdNamedPropertyDto attribute = dto.tabularSections.get(0).attributes.get(0);
    attribute.synonymRu = "Язык записи";
    attribute.toolTipRu = "Код языка представления";
    EdtObjectWriter.writeDto(file, dto, model);

    MdNamedPropertyDto written = EdtObjectProperties.readDto(file, model)
        .tabularSections.get(0).attributes.get(0);
    assertThat(written.synonymRu).isEqualTo("Язык записи");
    assertThat(written.toolTipRu).isEqualTo("Код языка представления");
  }

  @Test
  void переименованиеУзлаОтклоняется() throws Exception {
    Path file = copyOf("Catalogs/Валюты/Валюты.mdo");
    byte[] before = Files.readAllBytes(file);

    MdObjectPropertiesDto dto = EdtObjectProperties.readDto(file, model);
    dto.attributes.get(0).name = "ДругоеИмя";

    assertThatThrownBy(() -> EdtObjectWriter.writeDto(file, dto, model))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(Files.readAllBytes(file)).isEqualTo(before);
  }

  @Test
  void значениеВнеСхемыОтклоняется() throws Exception {
    Path file = copyOf("Catalogs/Валюты/Валюты.mdo");

    MdObjectPropertiesDto dto = EdtObjectProperties.readDto(file, model);
    dto.catalog.defaultPresentation = "AS_NOTHING";

    assertThatThrownBy(() -> EdtObjectWriter.writeDto(file, dto, model))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("defaultPresentation");
  }

  @Test
  void правитТипКонстантыОднойСтрокой() throws Exception {
    String constant = "_ДемоИмяКонфигурацииВОбменеСБиблиотекойСтандартныхПодсистем";
    Path file = copyOf("Constants/" + constant + "/" + constant + ".mdo");
    String before = Files.readString(file, StandardCharsets.UTF_8);

    MdObjectPropertiesDto dto = EdtObjectProperties.readDto(file, model);
    assertThat(dto.constant.type.types).containsExactly("xs:string");
    assertThat(dto.constant.type.stringQualifiers.length).isEqualTo("15");
    dto.constant.type.stringQualifiers.length = "20";
    EdtObjectWriter.writeDto(file, dto, model);

    String after = Files.readString(file, StandardCharsets.UTF_8);
    assertThat(changedLines(before, after)).containsExactlyInAnyOrder("<length>15</length>", "<length>20</length>");
    assertThat(EdtObjectProperties.readDto(file, model).constant.type.stringQualifiers.length).isEqualTo("20");
  }

  @Test
  void правитТипРеквизитаЦелымОписанием() throws Exception {
    Path file = copyOf("Catalogs/Валюты/Валюты.mdo");
    String before = Files.readString(file, StandardCharsets.UTF_8);

    MdObjectPropertiesDto dto = EdtObjectProperties.readDto(file, model);
    MdNamedPropertyDto attribute = dto.attributes.get(1);
    assertThat(attribute.name).isEqualTo("НаименованиеПолное");
    MdTypeDescriptionDto type = new MdTypeDescriptionDto();
    type.types.add("xs:decimal");
    type.numberQualifiers = new MdTypeDescriptionDto.MdNumberQualifiersDto();
    type.numberQualifiers.digits = "10";
    type.numberQualifiers.fractionDigits = "2";
    type.numberQualifiers.allowedSign = "NONNEGATIVE";
    attribute.type = type;
    EdtObjectWriter.writeDto(file, dto, model);

    String after = Files.readString(file, StandardCharsets.UTF_8);
    // Описание строки занимало шесть строк, описание числа занимает восемь
    assertThat(after.lines().count()).isEqualTo(before.lines().count() + 2);
    MdObjectPropertiesDto written = EdtObjectProperties.readDto(file, model);
    assertThat(written.attributes.get(1).type.types).containsExactly("xs:decimal");
    assertThat(written.attributes.get(1).type.numberQualifiers.allowedSign).isEqualTo("NONNEGATIVE");
    assertThat(written.attributes.get(1).type.numberQualifiers.fractionDigits).isEqualTo("2");
    // Соседние реквизиты не тронуты
    assertThat(written.attributes.get(0).type.types).containsExactly("xs:boolean");
    assertThat(written.attributes.get(2).type).usingRecursiveComparison().isEqualTo(dto.attributes.get(2).type);
  }

  @Test
  void прежнийТипФайлНеМеняет() throws Exception {
    Path file = copyOf("Catalogs/Валюты/Валюты.mdo");
    String before = Files.readString(file, StandardCharsets.UTF_8);

    MdObjectPropertiesDto dto = EdtObjectProperties.readDto(file, model);
    assertThat(EdtObjectWriter.writeDto(file, dto, model)).isZero();

    assertThat(Files.readString(file, StandardCharsets.UTF_8)).isEqualTo(before);
  }
}
