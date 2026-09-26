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

import jakarta.xml.bind.JAXBException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Добавление подчинённого узла корню объекта и то, где он виден после чтения моделью версии.
 *
 * <p>Здесь же ожидания из самой схемы формата: какие подчинённые у вида бывают, читается из XSD
 * submodule {@code resources/namespace-forest}, а не из модели, которой пользуется проверяемый код.
 */
enum ChildNodeOp {
  ATTRIBUTE("Attribute", MdObjectChildMutations::addAttribute,
    dto -> dto.attributes.stream().map(node -> node.name).toList()),
  TABULAR_SECTION("TabularSection", MdObjectChildMutations::addTabularSection,
    dto -> dto.tabularSections.stream().map(section -> section.name).toList()),
  COMMAND("Command", MdObjectChildMutations::addCommand, dto -> dto.commands),
  ENUM_VALUE("EnumValue", MdObjectChildMutations::addEnumValue, dto -> dto.values),
  DIMENSION("Dimension", MdObjectChildMutations::addDimension, dto -> dto.dimensions),
  RESOURCE("Resource", MdObjectChildMutations::addResource, dto -> dto.resources),
  ACCOUNTING_FLAG("AccountingFlag", MdObjectChildMutations::addAccountingFlag, dto -> dto.accountingFlags),
  EXT_DIMENSION_ACCOUNTING_FLAG("ExtDimensionAccountingFlag",
    MdObjectChildMutations::addExtDimensionAccountingFlag, dto -> dto.extDimensionAccountingFlags);

  private static final String XSD_NS = XMLConstants.W3C_XML_SCHEMA_NS_URI;

  /** Элемент узла в выгрузке. */
  final String element;
  private final Adder adder;
  private final Function<MdObjectStructureDto, List<String>> names;

  ChildNodeOp(String element, Adder adder, Function<MdObjectStructureDto, List<String>> names) {
    this.element = element;
    this.adder = adder;
    this.names = names;
  }

  void add(Path objectXml, SchemaVersion version, String name) throws IOException, JAXBException {
    adder.add(objectXml, version, name);
  }

  /** Имена узлов этого вида, прочитанные моделью версии. */
  List<String> names(Path objectXml, SchemaVersion version) throws IOException, JAXBException {
    return names.apply(MdObjectStructureRead.read(objectXml, version));
  }

  /**
   * Подчинённые вида по XSD формата: элементы {@code <Вид>ChildObjects}.
   *
   * @return пусто, если такого типа в схеме нет
   */
  static List<String> schemaChildren(SchemaVersion version, String ownerLocal) throws Exception {
    String root = System.getProperty("xsd.root");
    Path xsd = Path.of(root, version.xsdDirectoryName(), "v8.1c.ru-8.3-MDClasses.xsd");
    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    factory.setNamespaceAware(true);
    Document schema = factory.newDocumentBuilder().parse(xsd.toFile());
    NodeList types = schema.getElementsByTagNameNS(XSD_NS, "complexType");
    for (int i = 0; i < types.getLength(); i++) {
      Element type = (Element) types.item(i);
      if (!(ownerLocal + "ChildObjects").equals(type.getAttribute("name"))) {
        continue;
      }
      List<String> children = new ArrayList<>();
      NodeList elements = type.getElementsByTagNameNS(XSD_NS, "element");
      for (int j = 0; j < elements.getLength(); j++) {
        children.add(((Element) elements.item(j)).getAttribute("name"));
      }
      return children;
    }
    return List.of();
  }

  /** Вид объекта: элемент под {@code MetaDataObject}. */
  static String ownerOf(Path objectXml) throws Exception {
    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    factory.setNamespaceAware(true);
    Element root = factory.newDocumentBuilder().parse(objectXml.toFile()).getDocumentElement();
    for (Node child = root.getFirstChild(); child != null; child = child.getNextSibling()) {
      if (child instanceof Element element) {
        return element.getLocalName();
      }
    }
    throw new IllegalStateException("нет объекта в " + objectXml);
  }

  @FunctionalInterface
  private interface Adder {
    void add(Path objectXml, SchemaVersion version, String name) throws IOException, JAXBException;
  }
}
