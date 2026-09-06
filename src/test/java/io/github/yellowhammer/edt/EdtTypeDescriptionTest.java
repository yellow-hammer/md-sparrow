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

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import io.github.yellowhammer.designerxml.cf.MdTypeDescriptionDto;
import io.github.yellowhammer.edt.EdtObjectReader.EdtNode;

/**
 * Описание типа: запись файла EDT против записи контракта.
 *
 * Разметка после круга чтение-запись сверяется с настоящими файлами проекта:
 * так проверяются и порядок частей, и умолчания квалификаторов, и имена типов.
 */
class EdtTypeDescriptionTest {

  private static EdtModel model;
  private static Path source;

  @BeforeAll
  static void locate() throws Exception {
    model = EdtModel.bundled();
    source = Path.of(System.getProperty("fixtures.ssl31edt.root"), "ssl31", "src");
    assertThat(source).exists();
  }

  private static EdtNode node(String kind, String value, EdtNode... children) {
    return new EdtNode(kind, value, Map.of(), List.of(children));
  }

  private static EdtNode types(String... names) {
    List<EdtNode> children = new ArrayList<>();
    for (String name : names) {
      children.add(node("types", name));
    }
    return new EdtNode("type", "", Map.of(), children);
  }

  private static EdtNode type(EdtNode... children) {
    return node("type", "", children);
  }

  @Test
  void примитивыЧитаютсяВЗаписиКонфигуратора() {
    MdTypeDescriptionDto string = EdtTypeDescription.read(type(
        node("types", "String"),
        node("stringQualifiers", "", node("length", "15"), node("fixed", "true"))), model);
    assertThat(string.types).containsExactly("xs:string");
    assertThat(string.typeSets).isEmpty();
    assertThat(string.stringQualifiers.length).isEqualTo("15");
    assertThat(string.stringQualifiers.allowedLength).isEqualTo("FIXED");

    MdTypeDescriptionDto number = EdtTypeDescription.read(type(
        node("types", "Number"),
        node("numberQualifiers", "", node("precision", "10"), node("scale", "2"), node("nonNegative", "true"))),
        model);
    assertThat(number.types).containsExactly("xs:decimal");
    assertThat(number.numberQualifiers.digits).isEqualTo("10");
    assertThat(number.numberQualifiers.fractionDigits).isEqualTo("2");
    assertThat(number.numberQualifiers.allowedSign).isEqualTo("NONNEGATIVE");

    MdTypeDescriptionDto date = EdtTypeDescription.read(type(
        node("types", "Date"),
        node("dateQualifiers", "", node("dateFractions", "Date"))), model);
    assertThat(date.types).containsExactly("xs:dateTime");
    assertThat(date.dateQualifiers.dateFractions).isEqualTo("DATE");

    assertThat(EdtTypeDescription.read(types("Boolean"), model).types).containsExactly("xs:boolean");
  }

  @Test
  void умолчанияКвалификаторовБерутсяИзСхемы() {
    MdTypeDescriptionDto dto = EdtTypeDescription.read(type(
        node("types", "String"),
        node("types", "Number"),
        node("types", "Date"),
        node("numberQualifiers", "", node("precision", "10")),
        node("stringQualifiers", ""),
        node("dateQualifiers", "")), model);

    assertThat(dto.stringQualifiers.length).isEqualTo("0");
    assertThat(dto.stringQualifiers.allowedLength).isEqualTo("VARIABLE");
    assertThat(dto.numberQualifiers.fractionDigits).isEqualTo("0");
    assertThat(dto.numberQualifiers.allowedSign).isEqualTo("ANY");
    assertThat(dto.dateQualifiers.dateFractions).isEqualTo("DATE_TIME");
  }

  @Test
  void семействаСтановятсяМножествамиТипов() {
    MdTypeDescriptionDto dto = EdtTypeDescription.read(types(
        "AnyRef", "CatalogRef", "CatalogObject", "InformationRegisterRecordSet", "ConstantValueManager",
        "DefinedType.Организация", "Characteristic.Свойства",
        "CatalogRef.Валюты", "ReportObject", "DocumentJournalManager"), model);

    assertThat(dto.typeSets).containsExactly(
        "cfg:AnyIBRef", "cfg:CatalogRef", "cfg:CatalogObject", "cfg:InformationRegisterRecordSet",
        "cfg:ConstantValueManager", "cfg:DefinedType.Организация", "cfg:Characteristic.Свойства");
    // У отчёта нет ссылочного типа, поэтому его объект - отдельный тип, а не множество
    assertThat(dto.types).containsExactly("cfg:CatalogRef.Валюты", "cfg:ReportObject", "cfg:DocumentJournalManager");
  }

  @Test
  void типыПлатформыПолучаютПространствоСвоейСхемы() {
    assertThat(EdtTypeDescription.designerType("ValueStorage").text()).isEqualTo("v8:ValueStorage");
    assertThat(EdtTypeDescription.designerType("UUID").text()).isEqualTo("v8:UUID");
    assertThat(EdtTypeDescription.designerType("FixedStructure").text()).isEqualTo("v8:FixedStructure");
    assertThat(EdtTypeDescription.designerType("StandardPeriod").text()).isEqualTo("v8:StandardPeriod");
    assertThat(EdtTypeDescription.designerType("TypeDescription").text()).isEqualTo("v8:TypeDescription");
    // Список значений схема ядра зовёт с суффиксом
    assertThat(EdtTypeDescription.designerType("ValueList").text()).isEqualTo("v8:ValueListType");
    assertThat(EdtTypeDescription.designerType("Picture").text()).isEqualTo("v8ui:Picture");
    assertThat(EdtTypeDescription.designerType("FormattedString").text()).isEqualTo("v8ui:FormattedString");
    // Типы компоновки данных EDT пишет с приставкой, а схема - без неё
    assertThat(EdtTypeDescription.designerType("DataCompositionSettingsComposer").text())
        .isEqualTo("dcsset:SettingsComposer");
    assertThat(EdtTypeDescription.designerType("DataCompositionFilter").text()).isEqualTo("dcsset:Filter");
    assertThat(EdtTypeDescription.designerType("DataCompositionComparisonType").text())
        .isEqualTo("dcsset:DataCompositionComparisonType");
    assertThat(EdtTypeDescription.designerType("DataCompositionGroupType").text())
        .isEqualTo("dcscor:DataCompositionGroupType");
    // Чего в схемах нет, принадлежит конфигурации
    assertThat(EdtTypeDescription.designerType("ConstantsSet").text()).isEqualTo("cfg:ConstantsSet");
    assertThat(EdtTypeDescription.designerType("CatalogManager").text()).isEqualTo("cfg:CatalogManager");
    assertThat(EdtTypeDescription.designerType("DynamicList").text()).isEqualTo("cfg:DynamicList");
  }

  @Test
  void обратныйПереводВозвращаетИменаEdt() {
    List<String> names = List.of(
        "String", "Number", "Boolean", "Date", "AnyRef", "CatalogRef", "CatalogRef.Валюты",
        "DefinedType.Организация", "ValueStorage", "UUID", "ValueList", "TypeDescription", "Picture",
        "DataCompositionSettingsComposer", "DataCompositionFilter", "DataCompositionComparisonType",
        "ConstantsSet", "ReportObject");
    for (String name : names) {
      assertThat(EdtTypeDescription.edtType(EdtTypeDescription.designerType(name).text()))
          .as(name).isEqualTo(name);
    }
  }

  @Test
  void разметкаПослеКругаСовпадаетСФайлами() throws Exception {
    Pattern block = Pattern.compile("<type>.*?</type>", Pattern.DOTALL);
    List<String> mismatches = new ArrayList<>();
    int checked = 0;
    for (String directory : List.of("Catalogs", "Documents", "Constants", "InformationRegisters",
        "CommonAttributes", "SessionParameters", "ChartsOfCharacteristicTypes")) {
      try (Stream<Path> files = Files.walk(source.resolve(directory))) {
        for (Path file : files.filter(path -> path.toString().endsWith(".mdo")).sorted().toList()) {
          String xml = Files.readString(file, StandardCharsets.UTF_8);
          List<EdtNode> nodes = new ArrayList<>();
          collect(EdtObjectReader.read(file), nodes);
          Matcher matcher = block.matcher(xml);
          List<int[]> spans = new ArrayList<>();
          while (matcher.find()) {
            spans.add(new int[] {matcher.start(), matcher.end()});
          }
          if (spans.size() != nodes.size()) {
            // Разметка внутри других узлов разбирается иначе: такой файл не сверяем
            continue;
          }
          String eol = xml.contains("\r\n") ? "\r\n" : "\n";
          for (int index = 0; index < nodes.size(); index++) {
            int[] span = spans.get(index);
            int line = xml.lastIndexOf('\n', span[0] - 1) + 1;
            String indent = xml.substring(line, span[0]);
            String written = xml.substring(span[0], span[1]);
            String rendered = EdtTypeDescription.render(
                EdtTypeDescription.read(nodes.get(index), model), "type", model, indent, eol);
            checked++;
            if (!written.equals(rendered)) {
              mismatches.add(source.relativize(file) + ": " + written.replace(eol, " ") + " -> "
                  + rendered.replace(eol, " "));
            }
          }
        }
      }
    }
    assertThat(checked).as("сверено описаний типа").isGreaterThan(1000);
    assertThat(mismatches).as("расхождения разметки").isEmpty();
  }

  /** Все узлы описания типа в порядке файла. */
  private static void collect(EdtNode node, List<EdtNode> types) {
    for (EdtNode child : node.children()) {
      if (child.kind().equals("type")) {
        types.add(child);
      } else {
        collect(child, types);
      }
    }
  }
}
