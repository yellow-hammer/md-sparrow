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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import javax.xml.parsers.DocumentBuilderFactory;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Состав конфигурации: набор видов - из схемы формата, порядок блоков - как пишет платформа.
 */
class ConfigurationChildObjectsOrderTest {

  private static final String CONFIGURATION = "Configuration";

  @TempDir
  Path workspace;

  @ParameterizedTest
  @EnumSource(SchemaVersion.class)
  void видыСоставаТеЖеЧтоВСхемеФормата(SchemaVersion version) throws Exception {
    assertThat(ConfigurationChildObjectsOrder.tagOrder(version))
      .containsExactlyInAnyOrderElementsOf(ChildNodeOp.schemaChildren(version, CONFIGURATION));
  }

  /**
   * У видов, которые есть во всех форматах, порядок записи платформы совпадает с порядком схемы.
   * Расходятся только виды, появившиеся позже: их место берётся из выгрузки платформы.
   */
  @ParameterizedTest
  @EnumSource(SchemaVersion.class)
  void общиеВидыИдутВПорядкеСхемы(SchemaVersion version) throws Exception {
    List<String> common = ChildNodeOp.schemaChildren(SchemaVersion.values()[0], CONFIGURATION);
    List<String> order = new ArrayList<>(ConfigurationChildObjectsOrder.tagOrder(version));
    order.retainAll(common);

    assertThat(order).containsExactlyElementsOf(common);
  }

  @Test
  void цветПалитрыИзвестенФормату2_21() throws Exception {
    // Последний формат знает все виды: до 2.21 цвета палитры в составе не было
    SchemaVersion latest = SchemaVersion.values()[SchemaVersion.values().length - 1];
    SchemaVersion previous = SchemaVersion.values()[SchemaVersion.values().length - 2];
    List<String> added = new ArrayList<>(ChildNodeOp.schemaChildren(latest, CONFIGURATION));
    added.removeAll(ChildNodeOp.schemaChildren(previous, CONFIGURATION));

    assertThat(added).isNotEmpty();
    for (String kind : added) {
      assertThat(unknownTypes(latest, kind)).as("%s в %s", kind, latest).isEmpty();
      assertThat(unknownTypes(previous, kind)).as("%s в %s", kind, previous).containsExactly(kind);
    }
  }

  /**
   * Объекты всех видов формата, добавленные в обратном порядке, встают блоками в порядке
   * платформы, и проверка выгрузки не находит ни неизвестных видов, ни нарушенного порядка.
   */
  @ParameterizedTest
  @EnumSource(SchemaVersion.class)
  void объектыВсехВидовВстаютБлокамиВПорядкеПлатформы(SchemaVersion version) throws Exception {
    Path cf = workspace.resolve("cf");
    EmptyCfScaffold.writeEmptyTree(cf, CfLayout.DEFAULT_CONFIGURATION_NAME, null, null, null, version);
    Path configurationXml = cf.resolve(CfLayout.CONFIGURATION_XML);
    List<String> order = ConfigurationChildObjectsOrder.tagOrder(version);
    for (int i = order.size() - 1; i >= 0; i--) {
      ConfigurationChildObjectAppender.append(configurationXml, order.get(i), "Объект" + i);
    }

    assertThat(blocks(configurationXml)).containsExactlyElementsOf(order);
    assertThat(CfDumpValidation.validate(cf))
      .extracting(CfDumpFinding::kind)
      .doesNotContain(CfDumpValidation.KIND_UNKNOWN_TYPE, CfDumpValidation.KIND_CHILD_OBJECTS_ORDER);
  }

  /** Виды, которые проверка выгрузки формата сочла неизвестными, после добавления объекта вида. */
  private List<String> unknownTypes(SchemaVersion version, String kind) throws Exception {
    Path cf = workspace.resolve(version.name());
    EmptyCfScaffold.writeEmptyTree(cf, CfLayout.DEFAULT_CONFIGURATION_NAME, null, null, null, version);
    ConfigurationChildObjectAppender.append(cf.resolve(CfLayout.CONFIGURATION_XML), kind, "Объект1");
    return CfDumpValidation.validate(cf).stream()
      .filter(finding -> CfDumpValidation.KIND_UNKNOWN_TYPE.equals(finding.kind()))
      .map(CfDumpFinding::objectType)
      .toList();
  }

  /** Виды блоков состава в порядке файла. */
  private static List<String> blocks(Path configurationXml) throws Exception {
    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    factory.setNamespaceAware(true);
    Element childObjects = (Element) factory.newDocumentBuilder().parse(configurationXml.toFile())
      .getElementsByTagNameNS("*", "ChildObjects").item(0);
    List<String> blocks = new ArrayList<>();
    for (Node child = childObjects.getFirstChild(); child != null; child = child.getNextSibling()) {
      if (child instanceof Element element && (blocks.isEmpty() || !blocks.getLast().equals(element.getLocalName()))) {
        blocks.add(element.getLocalName());
      }
    }
    return blocks;
  }
}
