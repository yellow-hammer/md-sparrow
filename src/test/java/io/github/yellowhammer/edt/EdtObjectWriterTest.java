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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.yellowhammer.designerxml.cf.ConfigurationPropertiesDto;
import io.github.yellowhammer.designerxml.cf.MdCatalogPropertiesDto;
import io.github.yellowhammer.designerxml.cf.MdContentMemberDto;
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
    dto.synonym = "Денежные единицы";
    EdtObjectWriter.writeDto(file, dto, model);

    String after = Files.readString(file, StandardCharsets.UTF_8);
    assertThat(eolKinds(after)).containsExactly("LF");
    assertThat(after.lines().filter(line -> line.contains("<hierarchical>")).toList())
        .containsExactly("  <hierarchical>true</hierarchical>");
    assertThat(EdtObjectProperties.readDto(file, model).synonym).isEqualTo("Денежные единицы");
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
    dto.synonym = "Денежные единицы";
    EdtObjectWriter.writeDto(file, dto, model);

    String after = Files.readString(file, StandardCharsets.UTF_8);
    assertThat(changedLines(before, after)).containsExactlyInAnyOrder(
        "<value>Валюты</value>", "<value>Денежные единицы</value>");
    assertThat(EdtObjectProperties.readDto(file, model).synonym).isEqualTo("Денежные единицы");
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
    dto.attributes.get(0).synonym = "Курс из Интернета";
    EdtObjectWriter.writeDto(file, dto, model);

    String after = Files.readString(file, StandardCharsets.UTF_8);
    assertThat(changedLines(before, after)).containsExactlyInAnyOrder(
        "<value>Загружается из Интернета</value>", "<value>Курс из Интернета</value>");
    assertThat(EdtObjectProperties.readDto(file, model).attributes.get(0).synonym)
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
    attribute.synonym = "Язык записи";
    attribute.toolTip = "Код языка представления";
    EdtObjectWriter.writeDto(file, dto, model);

    MdNamedPropertyDto written = EdtObjectProperties.readDto(file, model)
        .tabularSections.get(0).attributes.get(0);
    assertThat(written.synonym).isEqualTo("Язык записи");
    assertThat(written.toolTip).isEqualTo("Код языка представления");
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
    // Пустая строка к числу не подходит: значение заполнения сброшено, как его сбрасывает платформа
    assertThat(middleLines(before, after)).contains("<fillValue xsi:type=\"core:UndefinedValue\"/>");
  }

  @Test
  void сменаТипаСтавитЗначениеЗаполненияПоКанонуПлатформы() throws Exception {
    Path file = copyOf("Catalogs/_ДемоПартнеры/_ДемоПартнеры.mdo");
    String before = Files.readString(file, StandardCharsets.UTF_8);

    // Булево «Истина» к строке не подходит: пустая строка, как у нового строкового реквизита
    MdObjectPropertiesDto dto = EdtObjectProperties.readDto(file, model);
    MdNamedPropertyDto client = dto.attributes.stream().filter(a -> a.name.equals("Клиент")).findFirst().orElseThrow();
    MdTypeDescriptionDto string = new MdTypeDescriptionDto();
    string.types.add("xs:string");
    string.stringQualifiers = new MdTypeDescriptionDto.MdStringQualifiersDto();
    string.stringQualifiers.length = "10";
    string.stringQualifiers.allowedLength = "VARIABLE";
    client.type = string;
    EdtObjectWriter.writeDto(file, dto, model);

    String after = Files.readString(file, StandardCharsets.UTF_8);
    assertThat(middleLines(before, after))
        .contains("<fillValue xsi:type=\"core:StringValue\"/>")
        .doesNotContain("<value>true</value>", "<fillValue xsi:type=\"core:UndefinedValue\"/>");

    // Составной тип со строкой пустую строку принимает: значение остаётся байт в байт
    MdObjectPropertiesDto again = EdtObjectProperties.readDto(file, model);
    MdNamedPropertyDto edited = again.attributes.stream().filter(a -> a.name.equals("Клиент")).findFirst().orElseThrow();
    edited.type.types.add("xs:boolean");
    EdtObjectWriter.writeDto(file, again, model);
    assertThat(middleLines(after, Files.readString(file, StandardCharsets.UTF_8)))
        .isNotEmpty()
        .noneSatisfy(line -> assertThat(line).contains("fillValue"));
  }

  @Test
  void значениеЗаполненияКаждогоУзлаКорпусаПодходитКЕгоТипу() throws IOException {
    // Эхо корпуса: запись типа узла ssl31-edt (тем же или с другими квалификаторами) его значение
    // заполнения не трогает
    java.util.regex.Pattern typed = java.util.regex.Pattern.compile(
        "<type>([\\s\\S]*?)</type>((?:(?!<type>)[\\s\\S])*?)<fillValue xsi:type=\"([^\"]*)\"");
    java.util.regex.Pattern types = java.util.regex.Pattern.compile("<types>([^<]*)</types>");
    java.util.Map<String, String> values = java.util.Map.of(
        "core:StringValue", "xs:string", "core:NumberValue", "xs:decimal",
        "core:BooleanValue", "xs:boolean", "core:DateValue", "xs:dateTime");
    java.util.Map<String, String> primitives = java.util.Map.of(
        "String", "xs:string", "Number", "xs:decimal", "Boolean", "xs:boolean", "Date", "xs:dateTime");
    int nodes = 0;
    List<String> touched = new ArrayList<>();
    List<Path> files;
    try (java.util.stream.Stream<Path> walk = Files.walk(source)) {
      files = walk.filter(f -> f.toString().endsWith(".mdo")).toList();
    }
    for (Path file : files) {
      String xml = Files.readString(file, StandardCharsets.UTF_8);
      java.util.regex.Matcher m = typed.matcher(xml);
      while (m.find()) {
        MdTypeDescriptionDto own = new MdTypeDescriptionDto();
        java.util.regex.Matcher t = types.matcher(m.group(1));
        while (t.find()) {
          String name = t.group(1);
          if (primitives.containsKey(name)) {
            own.types.add(primitives.get(name));
          } else if (name.startsWith("DefinedType.") || name.startsWith("Characteristic.")) {
            own.typeSets.add("cfg:" + name);
          } else {
            own.types.add("cfg:" + name);
          }
        }
        String value = m.group(3).equals("core:UndefinedValue") ? null : values.getOrDefault(m.group(3), m.group(3));
        nodes++;
        if (io.github.yellowhammer.designerxml.cf.FillValueCanon.afterTypeChange(value, own)
            != io.github.yellowhammer.designerxml.cf.FillValueCanon.Action.KEEP) {
          touched.add(file.getFileName() + " " + m.group(3));
        }
      }
    }
    assertThat(touched).isEmpty();
    assertThat(nodes).isGreaterThan(2500);
  }

  /** Строки нового текста между общим началом и общим концом двух файлов. */
  private static List<String> middleLines(String before, String after) {
    List<String> was = List.of(before.split("\\r?\\n", -1));
    List<String> now = List.of(after.split("\\r?\\n", -1));
    int head = 0;
    while (head < was.size() && head < now.size() && was.get(head).equals(now.get(head))) {
      head++;
    }
    int tail = 0;
    while (tail < was.size() - head && tail < now.size() - head
        && was.get(was.size() - 1 - tail).equals(now.get(now.size() - 1 - tail))) {
      tail++;
    }
    List<String> changed = new ArrayList<>();
    for (int index = head; index < now.size() - tail; index++) {
      changed.add(now.get(index).trim());
    }
    return changed;
  }

  @Test
  void прежнийТипФайлНеМеняет() throws Exception {
    Path file = copyOf("Catalogs/Валюты/Валюты.mdo");
    String before = Files.readString(file, StandardCharsets.UTF_8);

    MdObjectPropertiesDto dto = EdtObjectProperties.readDto(file, model);
    assertThat(EdtObjectWriter.writeDto(file, dto, model)).isZero();

    assertThat(Files.readString(file, StandardCharsets.UTF_8)).isEqualTo(before);
  }

  /**
   * Пакет XDTO из ssl31: единственный объект фикстуры с подписью на двух
   * языках, так её записала сама 1С:EDT.
   */
  @Test
  void подписьНаДругомЯзыкеПереживаетПравку() throws Exception {
    Path file = copyOf("XDTOPackages/EnterpriseData_1_20_2/EnterpriseData_1_20_2.mdo");
    String before = Files.readString(file, StandardCharsets.UTF_8);
    assertThat(before).contains("<key>en</key>", "<key>ru</key>");

    MdObjectPropertiesDto dto = EdtObjectProperties.readDto(file, model);
    dto.synonym = "Данные предприятия";
    EdtObjectWriter.writeDto(file, dto, model);

    String after = Files.readString(file, StandardCharsets.UTF_8);
    assertThat(after).contains("<value>EnterpriseData 1.19.1</value>", "<value>Данные предприятия</value>");
    assertThat(after.split("<key>en</key>", -1)).hasSize(2);
    assertThat(after.split("<key>ru</key>", -1)).hasSize(2);
    assertThat(EdtObjectProperties.readDto(file, model).synonym).isEqualTo("Данные предприятия");
    assertThat(after.lines().count()).isEqualTo(before.lines().count());
  }

  /** Строки свойства верхнего уровня уходят из файла целиком: так, будто его не было. */
  private static void removeProperty(Path file, String name) throws Exception {
    String xml = Files.readString(file, StandardCharsets.UTF_8);
    EdtObjectRegions.Region region = EdtObjectRegions.property(xml, name);
    assertThat(region.found()).as(name).isTrue();
    int start = EdtObjectRegions.lineStart(xml, region.start());
    int end = xml.indexOf('\n', region.end()) + 1;
    Files.writeString(file, xml.substring(0, start) + xml.substring(end), StandardCharsets.UTF_8);
  }

  /** Все элементы свойства-списка уходят из файла. */
  private static void removeList(Path file, String name) throws Exception {
    while (EdtObjectRegions.property(Files.readString(file, StandardCharsets.UTF_8), name).found()) {
      removeProperty(file, name);
    }
  }

  /**
   * Списки ссылок, записанные заново, совпадают с тем, что записала 1С:EDT:
   * состав, использование, движения, владельцы, ввод на основании.
   */
  @Test
  void спискиСсылокВстаютКакУEDT() throws Exception {
    Map<String, List<String>> lists = new LinkedHashMap<>();
    lists.put("Subsystems/_ДемоАнкетирование/_ДемоАнкетирование.mdo", List.of("content", "subsystems"));
    lists.put("FunctionalOptions/_ДемоИспользоватьАнкетированиеБСП/_ДемоИспользоватьАнкетированиеБСП.mdo",
        List.of("content"));
    lists.put("FilterCriteria/СвязанныеДокументы/СвязанныеДокументы.mdo", List.of("content"));
    lists.put("FunctionalOptionsParameters/_ДемоОрганизация/_ДемоОрганизация.mdo", List.of("use"));
    lists.put("Sequences/_ДемоДвижениеТоваров/_ДемоДвижениеТоваров.mdo", List.of("documents", "registerRecords"));
    lists.put("Catalogs/_ДемоБанковскиеСчета/_ДемоБанковскиеСчета.mdo", List.of("owners", "inputByString"));
    lists.put("Catalogs/ШаблоныСообщений/ШаблоныСообщений.mdo", List.of("basedOn"));
    lists.put("Catalogs/ВерсииФайлов/ВерсииФайлов.mdo", List.of("dataLockFields"));
    lists.put("Documents/_ДемоРеализацияТоваров/_ДемоРеализацияТоваров.mdo",
        List.of("registerRecords", "basedOn", "inputByString"));
    lists.put("DocumentJournals/_ДемоСкладскиеДокументы/_ДемоСкладскиеДокументы.mdo",
        List.of("registeredDocuments"));
    lists.put("ChartsOfCalculationTypes/_ДемоОсновныеНачисления/_ДемоОсновныеНачисления.mdo",
        List.of("baseCalculationTypes"));

    for (Map.Entry<String, List<String>> entry : lists.entrySet()) {
      Path file = copyOf(entry.getKey());
      String written = Files.readString(file, StandardCharsets.UTF_8);
      MdObjectPropertiesDto original = EdtObjectProperties.readDto(file, model);
      for (String name : entry.getValue()) {
        assertThat(written).as(entry.getKey()).contains("<" + name + ">");
        removeList(file, name);
      }

      EdtObjectWriter.writeDto(file, original, model);

      assertThat(Files.readString(file, StandardCharsets.UTF_8)).as(entry.getKey()).isEqualTo(written);
    }
  }

  @Test
  void основныеРолиКонфигурацииВстаютКакУEDT() throws Exception {
    Path file = copyOf("Configuration/Configuration.mdo");
    String written = Files.readString(file, StandardCharsets.UTF_8);
    ConfigurationPropertiesDto original = EdtConfigurationProperties.read(file, model);
    assertThat(original.defaultRoles).isNotEmpty();
    removeList(file, "defaultRoles");

    EdtConfigurationProperties.write(file, original, model);

    assertThat(Files.readString(file, StandardCharsets.UTF_8)).isEqualTo(written);
  }

  /** Состав общих реквизитов, записанный заново, совпадает с тем, что записала 1С:EDT. */
  @Test
  void составОбщихРеквизитовВстаётКакУEDT() throws Exception {
    List<Path> attributes;
    try (var children = Files.list(source.resolve("CommonAttributes"))) {
      attributes = children.filter(Files::isDirectory).sorted().toList();
    }
    int members = 0;
    for (Path attribute : attributes) {
      String name = attribute.getFileName().toString();
      Path file = copyOf("CommonAttributes/" + name + "/" + name + ".mdo");
      String written = Files.readString(file, StandardCharsets.UTF_8);
      MdObjectPropertiesDto original = EdtObjectProperties.readDto(file, model);
      members += original.contentMembers.size();
      removeList(file, "content");
      assertThat(EdtObjectProperties.readDto(file, model).contentMembers).as(name).isEmpty();

      EdtObjectWriter.writeDto(file, original, model);

      assertThat(Files.readString(file, StandardCharsets.UTF_8)).as(name).isEqualTo(written);
    }
    assertThat(members).as("элементов состава").isGreaterThan(400);
  }

  @Test
  void использованиеВСоставеОбщегоРеквизитаПравитсяОднойСтрокой() throws Exception {
    Path file = copyOf("CommonAttributes/КомментарийЯзык1/КомментарийЯзык1.mdo");
    List<String> before = Files.readString(file, StandardCharsets.UTF_8).lines().toList();

    MdObjectPropertiesDto dto = EdtObjectProperties.readDto(file, model);
    MdContentMemberDto member = dto.contentMembers.get(0);
    member.mode = member.mode.equals("USE") ? "DONT_USE" : "USE";
    EdtObjectWriter.writeDto(file, dto, model);

    List<String> after = Files.readString(file, StandardCharsets.UTF_8).lines().toList();
    assertThat(after).hasSameSizeAs(before);
    List<Integer> changed = new ArrayList<>();
    for (int index = 0; index < before.size(); index++) {
      if (!before.get(index).equals(after.get(index))) {
        changed.add(index);
      }
    }
    assertThat(changed).hasSize(1);
    assertThat(after.get(changed.get(0)).trim()).startsWith("<use>");
    assertThat(EdtObjectProperties.readDto(file, model).contentMembers).isEqualTo(dto.contentMembers);
  }

  /** Использование по умолчанию 1С:EDT не пишет: у такого элемента только ссылка. */
  @Test
  void использованиеПоУмолчаниюВФайлНеПишется() throws Exception {
    Path file = copyOf("CommonAttributes/КомментарийЯзык1/КомментарийЯзык1.mdo");
    Path subsystem = copyOf("Subsystems/_ДемоАнкетирование/_ДемоАнкетирование.mdo");
    long before = Files.readString(file, StandardCharsets.UTF_8).lines().count();

    MdObjectPropertiesDto dto = EdtObjectProperties.readDto(file, model);
    String ref = EdtObjectProperties.readDto(subsystem, model).contentRefs.stream()
        .filter(candidate -> dto.contentMembers.stream().noneMatch(member -> member.ref.equals(candidate)))
        .findFirst().orElseThrow();
    dto.contentMembers.add(new MdContentMemberDto(ref, "AUTO", ""));
    EdtObjectWriter.writeDto(file, dto, model);

    String after = Files.readString(file, StandardCharsets.UTF_8);
    assertThat(after.lines().count()).isEqualTo(before + 3);
    assertThat(after).contains("<metadata>" + ref + "</metadata>" + eol(after) + "  </content>");
    assertThat(EdtObjectProperties.readDto(file, model).contentMembers).last()
        .isEqualTo(new MdContentMemberDto(ref, "AUTO", ""));
  }

  private static String eol(String text) {
    return text.contains("\r\n") ? "\r\n" : "\n";
  }

  /**
   * Свойства видов без своего блока, записанные заново, совпадают с тем, что
   * записала 1С:EDT: у каждого такого объекта фикстуры.
   */
  @Test
  void свойстваВидовБезСвоегоБлокаВстаютКакУEDT() throws Exception {
    List<Path> directories;
    try (var children = Files.list(source)) {
      directories = children.filter(Files::isDirectory).sorted().toList();
    }
    int objects = 0;
    for (Path directory : directories) {
      List<Path> projects;
      try (var children = Files.list(directory)) {
        projects = children.filter(Files::isDirectory).sorted().toList();
      }
      for (Path project : projects) {
        Path original = project.resolve(project.getFileName() + ".mdo");
        if (!Files.isRegularFile(original)) {
          continue;
        }
        Path file = workDir.resolve(directory.getFileName().toString()).resolve(original.getFileName().toString());
        Files.createDirectories(file.getParent());
        Files.copy(original, file);
        MdObjectPropertiesDto dto = EdtObjectProperties.readDto(file, model);
        if (dto.scalars == null) {
          continue;
        }
        String written = Files.readString(file, StandardCharsets.UTF_8);
        for (String key : dto.scalars.keySet()) {
          removeList(file, Character.toLowerCase(key.charAt(0)) + key.substring(1));
        }

        EdtObjectWriter.writeDto(file, dto, model);

        assertThat(Files.readString(file, StandardCharsets.UTF_8)).as(original.toString()).isEqualTo(written);
        objects++;
      }
    }
    assertThat(objects).as("объектов").isGreaterThan(500);
  }

  @Test
  void перечислимоеСвойствоВидаБезСвоегоБлокаПравитсяСтрокой() throws Exception {
    Path file = copyOf("Sequences/_ДемоДвижениеТоваров/_ДемоДвижениеТоваров.mdo");
    String before = Files.readString(file, StandardCharsets.UTF_8);

    MdObjectPropertiesDto dto = EdtObjectProperties.readDto(file, model);
    assertThat(dto.scalars).containsEntry("DataLockControlMode", "MANAGED");
    assertThat(dto.scalarMeta.get("DataLockControlMode").allowed).contains("AUTOMATIC");
    dto.scalars.put("DataLockControlMode", "AUTOMATIC");
    EdtObjectWriter.writeDto(file, dto, model);

    String after = Files.readString(file, StandardCharsets.UTF_8);
    assertThat(changedLines(before, after)).containsExactlyInAnyOrder(
        "<dataLockControlMode>Managed</dataLockControlMode>", "<dataLockControlMode>Automatic</dataLockControlMode>");
    assertThat(EdtObjectProperties.readDto(file, model).scalars).containsEntry("DataLockControlMode", "AUTOMATIC");
  }

  /** Пустое значение снимает свойство: ссылки в файле больше нет. */
  @Test
  void пустоеЗначениеСнимаетСвойство() throws Exception {
    Path file = copyOf(
        "FunctionalOptions/_ДемоИспользоватьАнкетированиеБСП/_ДемоИспользоватьАнкетированиеБСП.mdo");
    String before = Files.readString(file, StandardCharsets.UTF_8);

    MdObjectPropertiesDto dto = EdtObjectProperties.readDto(file, model);
    String location = String.valueOf(dto.scalars.get("Location"));
    assertThat(location).isNotEmpty();
    dto.scalars.put("Location", "");
    EdtObjectWriter.writeDto(file, dto, model);

    String after = Files.readString(file, StandardCharsets.UTF_8);
    assertThat(changedLines(before, after)).containsExactly("<location>" + location + "</location>");
    assertThat(EdtObjectProperties.readDto(file, model).scalars).containsEntry("Location", "");
  }

  @Test
  void свойствоВнеСловаряВидаОтклоняется() throws Exception {
    Path file = copyOf("Sequences/_ДемоДвижениеТоваров/_ДемоДвижениеТоваров.mdo");
    Path catalog = copyOf("Catalogs/Валюты/Валюты.mdo");
    byte[] before = Files.readAllBytes(file);

    MdObjectPropertiesDto belonging = EdtObjectProperties.readDto(file, model);
    belonging.scalars.put("ObjectBelonging", "ADOPTED");
    assertThatThrownBy(() -> EdtObjectWriter.writeDto(file, belonging, model))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("ObjectBelonging");

    MdObjectPropertiesDto unknown = EdtObjectProperties.readDto(file, model);
    unknown.scalars.put("CodeLength", "5");
    assertThatThrownBy(() -> EdtObjectWriter.writeDto(file, unknown, model))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("CodeLength");
    assertThat(Files.readAllBytes(file)).isEqualTo(before);

    // У вида со своим блоком словаря нет: свойства правятся в блоке
    MdObjectPropertiesDto bridged = EdtObjectProperties.readDto(catalog, model);
    bridged.scalars = new LinkedHashMap<>(Map.of("CodeLength", "5"));
    assertThatThrownBy(() -> EdtObjectWriter.writeDto(catalog, bridged, model))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void составПодсистемыПравитсяПоСтроке() throws Exception {
    Path file = copyOf("Subsystems/_ДемоАнкетирование/_ДемоАнкетирование.mdo");
    Path other = copyOf("Subsystems/_ДемоБизнесПроцессыИЗадачи/_ДемоБизнесПроцессыИЗадачи.mdo");
    String before = Files.readString(file, StandardCharsets.UTF_8);

    MdObjectPropertiesDto dto = EdtObjectProperties.readDto(file, model);
    dto.contentRefs = new ArrayList<>(dto.contentRefs);
    String removed = dto.contentRefs.get(0);
    String added = EdtObjectProperties.readDto(other, model).contentRefs.stream()
        .filter(ref -> !dto.contentRefs.contains(ref))
        .findFirst().orElseThrow();
    dto.contentRefs.remove(removed);
    dto.contentRefs.add(added);
    EdtObjectWriter.writeDto(file, dto, model);

    String after = Files.readString(file, StandardCharsets.UTF_8);
    assertThat(changedLines(before, after)).containsExactlyInAnyOrder(
        "<content>" + added + "</content>", "<content>" + removed + "</content>");
    assertThat(EdtObjectProperties.readDto(file, model).contentRefs).isEqualTo(dto.contentRefs);
  }

  @Test
  void пустойСоставУходитИзФайла() throws Exception {
    Path file = copyOf("Subsystems/_ДемоАнкетирование/_ДемоАнкетирование.mdo");
    String before = Files.readString(file, StandardCharsets.UTF_8);

    MdObjectPropertiesDto dto = EdtObjectProperties.readDto(file, model);
    int lines = dto.contentRefs.size();
    dto.contentRefs = new ArrayList<>();
    EdtObjectWriter.writeDto(file, dto, model);

    String after = Files.readString(file, StandardCharsets.UTF_8);
    assertThat(after).doesNotContain("<content>");
    assertThat(after.lines().count()).isEqualTo(before.lines().count() - lines);
  }

  /** Элемент состава общего реквизита - узел с признаком использования: ссылкой его не записать. */
  @Test
  void составОбщегоРеквизитаОтклоняется() throws Exception {
    Path file = copyOf("CommonAttributes/КомментарийЯзык1/КомментарийЯзык1.mdo");
    Path subsystem = copyOf("Subsystems/_ДемоАнкетирование/_ДемоАнкетирование.mdo");
    byte[] before = Files.readAllBytes(file);

    MdObjectPropertiesDto dto = EdtObjectProperties.readDto(file, model);
    dto.contentRefs = new ArrayList<>(dto.contentRefs);
    dto.contentRefs.add(EdtObjectProperties.readDto(subsystem, model).contentRefs.get(0));

    assertThatThrownBy(() -> EdtObjectWriter.writeDto(file, dto, model))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining(dto.internalName);
    assertThat(Files.readAllBytes(file)).isEqualTo(before);
  }

  /**
   * Новые свойства с одной точкой вставки встают по порядку схемы: объект без
   * синонима и комментария после их записи совпадает с тем, что записала 1С:EDT.
   */
  @Test
  void новыеСвойстваВОднойТочкеВстаютПоПорядкуСхемы() throws Exception {
    Path file = copyOf("Catalogs/_ДемоКонтрагенты/_ДемоКонтрагенты.mdo");
    String written = Files.readString(file, StandardCharsets.UTF_8);
    MdObjectPropertiesDto original = EdtObjectProperties.readDto(file, model);
    removeProperty(file, "synonym");
    removeProperty(file, "comment");

    MdObjectPropertiesDto dto = EdtObjectProperties.readDto(file, model);
    dto.synonym = original.synonym;
    dto.comment = original.comment;
    EdtObjectWriter.writeDto(file, dto, model);

    assertThat(Files.readString(file, StandardCharsets.UTF_8)).isEqualTo(written);
  }

  @Test
  void новыеСвойстваКонфигурацииВОднойТочкеВстаютПоПорядкуСхемы() throws Exception {
    Path file = copyOf("Configuration/Configuration.mdo");
    String written = Files.readString(file, StandardCharsets.UTF_8);
    ConfigurationPropertiesDto original = EdtConfigurationProperties.read(file, model);
    removeProperty(file, "version");
    removeProperty(file, "updateCatalogAddress");

    ConfigurationPropertiesDto dto = EdtConfigurationProperties.read(file, model);
    dto.version = original.version;
    dto.updateCatalogAddress = original.updateCatalogAddress;
    EdtConfigurationProperties.write(file, dto, model);

    assertThat(Files.readString(file, StandardCharsets.UTF_8)).isEqualTo(written);
  }

  /** Кавычки в тексте 1С:EDT пишет {@code &quot;}: комментарий после записи совпадает с её файлом. */
  @Test
  void кавычкиВТекстеЭкранируютсяКакУEDT() throws Exception {
    Path file = copyOf("Constants/ПараметрыХраненияФайловВИБ/ПараметрыХраненияФайловВИБ.mdo");
    String written = Files.readString(file, StandardCharsets.UTF_8);
    MdObjectPropertiesDto original = EdtObjectProperties.readDto(file, model);
    assertThat(original.comment).contains("\"");
    removeProperty(file, "comment");

    MdObjectPropertiesDto dto = EdtObjectProperties.readDto(file, model);
    dto.comment = original.comment;
    EdtObjectWriter.writeDto(file, dto, model);

    assertThat(Files.readString(file, StandardCharsets.UTF_8)).isEqualTo(written);
  }

  /**
   * «Больше» в тексте 1С:EDT не экранирует: подсказка измерения с кавычками и
   * угловыми скобками после записи совпадает с её файлом.
   */
  @Test
  void большеВТекстеОстаётсяКакУEDT() throws Exception {
    Path file = copyOf(
        "InformationRegisters/ПериодическиеСерверныеОповещения/ПериодическиеСерверныеОповещения.mdo");
    String written = Files.readString(file, StandardCharsets.UTF_8);
    MdObjectPropertiesDto dto = EdtObjectProperties.readDto(file, model);
    MdNamedPropertyDto node = dto.dimensions.stream()
        .filter(dimension -> dimension.toolTip != null && dimension.toolTip.contains(">")
            && dimension.toolTip.contains("\""))
        .findFirst().orElseThrow();
    String toolTip = node.toolTip;
    node.toolTip = node.name;
    EdtObjectWriter.writeDto(file, dto, model);
    assertThat(Files.readString(file, StandardCharsets.UTF_8)).isNotEqualTo(written);

    MdObjectPropertiesDto again = EdtObjectProperties.readDto(file, model);
    again.dimensions.stream().filter(dimension -> dimension.name.equals(node.name)).findFirst().orElseThrow()
        .toolTip = toolTip;
    EdtObjectWriter.writeDto(file, again, model);

    assertThat(Files.readString(file, StandardCharsets.UTF_8)).isEqualTo(written);
  }
}
