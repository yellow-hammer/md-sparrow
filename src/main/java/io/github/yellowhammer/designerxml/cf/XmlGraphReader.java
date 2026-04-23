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

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Лёгкие DOM-помощники для извлечения связей из Designer XML без привязки к версии схемы (v2.20/v2.21).
 *
 * <p>Используем DOM, а не JAXB, потому что для графа важны только текстовые ссылки, а структура тегов
 * (Owners, Type, Content, RegisterRecords, …) одинакова между версиями. Это устраняет N×M зоопарк
 * специальных reader-классов под каждую пару (тип объекта × версия схемы).
 */
public final class XmlGraphReader {

  private XmlGraphReader() {
  }

  /** Парсит XML-файл в DOM. Внешние сущности отключены (XXE-safe). */
  public static Document parse(Path xml) throws IOException {
    try {
      DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
      factory.setNamespaceAware(true);
      factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
      factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
      factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
      factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
      factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
      factory.setXIncludeAware(false);
      factory.setExpandEntityReferences(false);
      DocumentBuilder builder = factory.newDocumentBuilder();
      try (var in = Files.newInputStream(xml)) {
        return builder.parse(in);
      }
    } catch (ParserConfigurationException | SAXException e) {
      throw new IOException("Не удалось разобрать XML: " + xml, e);
    }
  }

  /**
   * Возвращает первый дочерний элемент с указанным локальным именем (без учёта namespace), или {@code null}.
   */
  public static Element firstChildLocal(Element parent, String localName) {
    if (parent == null) {
      return null;
    }
    Node n = parent.getFirstChild();
    while (n != null) {
      if (n.getNodeType() == Node.ELEMENT_NODE && localName.equals(n.getLocalName())) {
        return (Element) n;
      }
      n = n.getNextSibling();
    }
    return null;
  }

  /** Все дочерние элементы с указанным локальным именем. */
  public static List<Element> childrenLocal(Element parent, String localName) {
    List<Element> out = new ArrayList<>();
    if (parent == null) {
      return out;
    }
    Node n = parent.getFirstChild();
    while (n != null) {
      if (n.getNodeType() == Node.ELEMENT_NODE && localName.equals(n.getLocalName())) {
        out.add((Element) n);
      }
      n = n.getNextSibling();
    }
    return out;
  }

  /** Все потомки с указанным локальным именем (рекурсивно). */
  public static List<Element> descendantsLocal(Element root, String localName) {
    List<Element> out = new ArrayList<>();
    if (root == null) {
      return out;
    }
    NodeList all = root.getElementsByTagNameNS("*", localName);
    for (int i = 0; i < all.getLength(); i++) {
      out.add((Element) all.item(i));
    }
    return out;
  }

  /** Текст элемента (trim), {@code null} если элемент пуст или {@code null}. */
  public static String text(Element e) {
    if (e == null) {
      return null;
    }
    String t = e.getTextContent();
    if (t == null) {
      return null;
    }
    String trimmed = t.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }

  /** Корневой элемент {@code MetaDataObject}, или {@link Optional#empty()}, если документ пустой
   *  или корневой тег не является {@code MetaDataObject}. */
  public static Optional<Element> findMetadataObjectRoot(Document doc) {
    if (doc == null) {
      return Optional.empty();
    }
    Element root = doc.getDocumentElement();
    if (root == null) {
      return Optional.empty();
    }
    if ("MetaDataObject".equals(root.getLocalName())) {
      return Optional.of(root);
    }
    return Optional.empty();
  }
}
