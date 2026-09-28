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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.yellowhammer.designerxml.SchemaVersion;
import io.github.yellowhammer.designerxml.Ssl31SubmodulePaths;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

class FormScaffoldTest {

  @TempDir Path tempDir;

  private Path copyCatalog() throws Exception {
    Path src = Ssl31SubmodulePaths.projectRoot().resolve("src/cf/Catalogs/_ДемоБанковскиеСчета.xml");
    Path copy = tempDir.resolve(src.getFileName());
    Files.copy(src, copy, StandardCopyOption.REPLACE_EXISTING);
    return copy;
  }

  @Test
  void addFormCreatesFilesAndEntry() throws Exception {
    Path objectXml = copyCatalog();
    FormScaffold.addForm(objectXml, SchemaVersion.V2_20, "НоваяФорма");

    String xml = Files.readString(objectXml);
    assertThat(xml).contains("<Form>НоваяФорма</Form>");
    Path forms = tempDir.resolve("_ДемоБанковскиеСчета").resolve("Forms");
    assertThat(Files.isRegularFile(forms.resolve("НоваяФорма.xml"))).isTrue();
    assertThat(Files.isRegularFile(forms.resolve("НоваяФорма").resolve("Ext").resolve("Form.xml"))).isTrue();

    MdObjectStructureDto structure = MdObjectStructureRead.read(objectXml, SchemaVersion.V2_20);
    assertThat(structure.forms).extracting(form -> form.name).contains("НоваяФорма");
    MdObjectPropertiesDto descriptor = MdObjectPropertiesEdit.readDto(
      forms.resolve("НоваяФорма.xml"), SchemaVersion.V2_20);
    assertThat(descriptor.kind).isEqualTo("form");
    assertThat(descriptor.internalName).isEqualTo("НоваяФорма");

    assertThatThrownBy(() -> FormScaffold.addForm(objectXml, SchemaVersion.V2_20, "НоваяФорма"))
      .hasMessageContaining("уже");
  }

  @Test
  void compileFormBuildsItemsReadableBySchema() throws Exception {
    Path objectXml = copyCatalog();
    String definition = """
      {
        "synonym": "Карточка счёта",
        "mainAttribute": {"name": "Объект", "type": "cfg:CatalogObject._ДемоБанковскиеСчета"},
        "items": [
          {"group": "Шапка", "direction": "horizontal", "items": [
            {"input": "Наименование", "dataPath": "Объект.Description"},
            {"check": "Основной", "dataPath": "Объект.Основной"}
          ]},
          {"label": "Подсказка", "title": "Реквизиты банка ниже"},
          {"table": "Счета", "dataPath": "Объект.Счета", "columns": [
            {"input": "Номер", "dataPath": "Объект.Счета.Номер"}
          ]}
        ]
      }
      """;
    FormScaffold.compileForm(objectXml, SchemaVersion.V2_20, "Карточка", definition);

    Path content = tempDir.resolve("_ДемоБанковскиеСчета").resolve("Forms")
      .resolve("Карточка").resolve("Ext").resolve("Form.xml");
    String xml = Files.readString(content);
    assertThat(xml).contains("<UsualGroup name=\"Шапка\"");
    assertThat(xml).contains("<Table name=\"Счета\"");
    assertThat(xml).contains("<MainAttribute>true</MainAttribute>");

    // Содержимое обязано читаться и нашей высокоуровневой операцией формы
    FormContentDtoReadCheck.check(content);
  }

  /**
   * Служебные узлы элемента формы: их состав и порядок задаёт платформа. Вложенные
   * элементы идут после них, но у таблицы эталона колонок нет, и они сверяются отдельно.
   */
  private static final java.util.Set<String> SERVICE_NODES = java.util.Set.of(
    "ContextMenu", "AutoCommandBar", "ExtendedTooltip", "AdditionSource",
    "SearchStringAddition", "ViewStatusAddition", "SearchControlAddition");

  @Test
  void compileFormWritesServiceNodesLikePlatform() throws Exception {
    Path objectXml = copyCatalog();
    String definition = """
      {
        "items": [
          {"pages": "Страницы", "items": [
            {"page": "Основное", "title": "Основное", "items": [
              {"group": "Шапка", "items": [
                {"check": "Основной", "dataPath": "Объект.Основной"},
                {"label": "Подсказка", "title": "Реквизиты банка ниже"}
              ]}
            ]}
          ]},
          {"table": "Счета", "dataPath": "Объект.Счета", "items": [
            {"check": "Отметка", "dataPath": "Объект.Счета.Отметка"}
          ]}
        ]
      }
      """;
    FormScaffold.compileForm(objectXml, SchemaVersion.V2_20, "Карточка", definition);
    Path content = tempDir.resolve("_ДемоБанковскиеСчета").resolve("Forms")
      .resolve("Карточка").resolve("Ext").resolve("Form.xml");
    org.w3c.dom.Element ours = parse(content).getDocumentElement();

    // Эталон - форма, которую выгрузила платформа: у каждого вида элемента свой набор узлов
    java.util.Map<String, List<String>> expected = new java.util.TreeMap<>();
    try (java.util.stream.Stream<Path> files = Files.walk(
      io.github.yellowhammer.designerxml.SamplesSubmodulePaths.snapshot(SchemaVersion.V2_20, "external-files"))) {
      for (Path form : files.filter(file -> file.getFileName().toString().equals("Form.xml")).sorted().toList()) {
        collectServiceNodes(parse(form).getDocumentElement(), expected);
      }
    }
    java.util.Map<String, List<String>> actual = new java.util.TreeMap<>();
    collectServiceNodes(ours, actual);
    assertThat(actual.keySet()).containsExactlyInAnyOrder(
      "CheckBoxField", "LabelDecoration", "UsualGroup", "Pages", "Page", "Table",
      "SearchStringAddition", "ViewStatusAddition", "SearchControlAddition");
    actual.forEach((kind, nodes) -> assertThat(nodes).as(kind).isEqualTo(expected.get(kind)));
    assertChildItemsLast(ours);
    // Узлы, которые платформа дописывает при загрузке и выгрузке (проверка загрузкой, 8.3.17-8.5.1)
    String xml = Files.readString(content);
    assertThat(xml).containsPattern("<DataPath>Объект.Основной</DataPath>\\s*<CheckBoxType>Auto</CheckBoxType>\\s*<ContextMenu");
    assertThat(xml).containsPattern("<LabelDecoration name=\"Подсказка\" id=\"\\d+\">\\s*<Title formatted=\"false\">");
    assertThat(xml).containsPattern("<DataPath>Объект.Счета</DataPath>\\s*<RowFilter xsi:nil=\"true\"/>\\s*<ContextMenu");

    // Номера элементов платформа раздаёт подряд в порядке файла
    List<Integer> ids = new java.util.ArrayList<>();
    collectIds(ours, ids);
    assertThat(ids).isEqualTo(java.util.stream.IntStream.rangeClosed(1, ids.size()).boxed().toList());
  }

  @Test
  void checkBoxTypeByDefaultOnlyBefore8_5() throws Exception {
    String definition = "{\"items\": [{\"check\": \"Основной\", \"dataPath\": \"Объект.Основной\"}]}";
    Path content = tempDir.resolve("_ДемоБанковскиеСчета").resolve("Forms")
      .resolve("Флажок").resolve("Ext").resolve("Form.xml");
    Path objectXml = copyCatalog();
    // Проверка загрузкой: 8.5.1 (2.21) вид флажка по умолчанию не выгружает, 8.3.17-8.3.27 выгружают
    FormScaffold.compileForm(objectXml, SchemaVersion.V2_21, "Флажок", definition);
    assertThat(Files.readString(content)).doesNotContain("<CheckBoxType>");
    FormScaffold.compileForm(objectXml, SchemaVersion.V2_20, "Флажок", definition);
    assertThat(Files.readString(content)).contains("<CheckBoxType>Auto</CheckBoxType>");
  }

  private static org.w3c.dom.Document parse(Path xml) throws Exception {
    javax.xml.parsers.DocumentBuilderFactory factory = javax.xml.parsers.DocumentBuilderFactory.newInstance();
    factory.setNamespaceAware(true);
    return factory.newDocumentBuilder().parse(xml.toFile());
  }

  /** Первый встреченный элемент каждого вида: служебные узлы по порядку, у дополнения ещё его источник. */
  private static void collectServiceNodes(org.w3c.dom.Element element, java.util.Map<String, List<String>> out) {
    for (org.w3c.dom.Element child : children(element)) {
      if (!child.getAttribute("id").isEmpty() && !"-1".equals(child.getAttribute("id"))
        && !child.getLocalName().equals("ContextMenu") && !child.getLocalName().equals("ExtendedTooltip")
        && !child.getLocalName().equals("AutoCommandBar")) {
        List<String> nodes = new java.util.ArrayList<>();
        for (org.w3c.dom.Element node : children(child)) {
          if (SERVICE_NODES.contains(node.getLocalName())) {
            nodes.add(node.getLocalName());
          }
        }
        out.putIfAbsent(child.getLocalName(), nodes);
      }
      collectServiceNodes(child, out);
    }
  }

  /** Вложенные элементы платформа пишет после всех служебных узлов, в том числе после подсказки. */
  private static void assertChildItemsLast(org.w3c.dom.Element element) {
    List<org.w3c.dom.Element> nodes = children(element);
    for (int i = 0; i < nodes.size(); i++) {
      if (nodes.get(i).getLocalName().equals("ChildItems")) {
        for (org.w3c.dom.Element after : nodes.subList(i + 1, nodes.size())) {
          assertThat(SERVICE_NODES).as(element.getAttribute("name")).doesNotContain(after.getLocalName());
        }
      }
      assertChildItemsLast(nodes.get(i));
    }
  }

  private static void collectIds(org.w3c.dom.Element element, List<Integer> out) {
    for (org.w3c.dom.Element child : children(element)) {
      if (!child.getAttribute("id").isEmpty() && !child.getLocalName().equals("Attribute")
        && !"-1".equals(child.getAttribute("id"))) {
        out.add(Integer.parseInt(child.getAttribute("id")));
      }
      collectIds(child, out);
    }
  }

  private static List<org.w3c.dom.Element> children(org.w3c.dom.Element element) {
    List<org.w3c.dom.Element> out = new java.util.ArrayList<>();
    for (org.w3c.dom.Node node = element.getFirstChild(); node != null; node = node.getNextSibling()) {
      if (node instanceof org.w3c.dom.Element child) {
        out.add(child);
      }
    }
    return out;
  }

  /** Отдельный хелпер: контент формы читается операцией cf-form-content-get. */
  static final class FormContentDtoReadCheck {
    static void check(Path content) throws Exception {
      Object root = io.github.yellowhammer.designerxml.DesignerXml.read(content, SchemaVersion.V2_20);
      assertThat(root).isNotNull();
    }
  }

  @Test
  void dcsEditQueryAndCalculatedField() throws Exception {
    java.nio.file.Path source = Ssl31SubmodulePaths.projectRoot().resolve(
      "src/erf/_ДемоОтчетНоменклатураОперации/_ДемоНоменклатураОперации/Templates/ОсновнаяСхемаКомпоновкиДанных/Ext/Template.xml");
    java.nio.file.Path dcs = tempDir.resolve("Template.xml");
    Files.copy(source, dcs, StandardCopyOption.REPLACE_EXISTING);

    DcsRead.setQuery(dcs, SchemaVersion.V2_20, null, "ВЫБРАТЬ 1 КАК Поле1");
    DcsRead.addCalculatedField(dcs, SchemaVersion.V2_20, "Наценка", "Поле1 * 2", "Наценка");

    java.util.Map<String, Object> info = DcsRead.info(dcs, SchemaVersion.V2_20);
    assertThat(String.valueOf(info)).contains("ВЫБРАТЬ 1 КАК Поле1");
    assertThat(String.valueOf(info.get("calculatedFields"))).contains("Наценка");
  }

  @Test
  void dcsInfoReadsSchema() throws Exception {
    java.nio.file.Path dcs = Ssl31SubmodulePaths.projectRoot().resolve(
      "src/erf/_ДемоОтчетНоменклатураОперации/_ДемоНоменклатураОперации/Templates/ОсновнаяСхемаКомпоновкиДанных/Ext/Template.xml");
    java.util.Map<String, Object> info = DcsRead.info(dcs, SchemaVersion.V2_20);
    assertThat(info.get("dataSets")).asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.LIST).isNotEmpty();
    java.util.Map<String, Object> valid = DcsRead.validate(dcs, SchemaVersion.V2_20);
    assertThat(valid.get("valid")).isEqualTo(true);
  }
}
