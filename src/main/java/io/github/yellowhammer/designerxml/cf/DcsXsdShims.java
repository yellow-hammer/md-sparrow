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

import javax.xml.XMLConstants;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Подмены XSD схемы компоновки данных в памяти, приводящие XDTO-схемы 1С к файлам, которые пишет платформа
 * (тот же приём, что {@code XsdPlatformShims} у проверки описаний объектов, но свой набор: общие схемы
 * {@code data/core} здесь правятся под значения настроек СКД, и эти послабления не должны попадать в проверку
 * описаний объектов).
 * <p>
 * Каждая подмена ослабляет схему ровно на одно отличие модели XDTO от файла; набор покрывает все схемы
 * компоновки fixtures/ssl31 и ssl31-edt. Неизвестные элементы, неверные значения и элементы не на своём месте
 * остаются ошибкой.
 */
final class DcsXsdShims {

  static final String DCS_SCHEMA_XSD = "v8.1c.ru-8.1-data-composition-system-schema.xsd";
  static final String DCS_AREA_TEMPLATE_XSD = "v8.1c.ru-8.1-data-composition-system-area-template.xsd";
  static final String DATA_CORE_XSD = "v8.1c.ru-8.1-data-core.xsd";

  private static final String XS = XMLConstants.W3C_XML_SCHEMA_NS_URI;

  private DcsXsdShims() {
  }

  /**
   * Применяет подмены к разобранной схеме по имени её файла; схемы без подмен не меняются.
   *
   * @param xsdFileName имя файла XSD в ресурсах сборки
   * @param xsd разобранная схема (с учётом пространств имён), меняется на месте
   */
  static void apply(String xsdFileName, Document xsd) {
    switch (xsdFileName) {
      case DCS_SCHEMA_XSD -> {
        renameRootToPlatformCase(xsd);
        makeNestedSchemaUrlOptional(xsd);
        unchainGroupTemplate(xsd);
      }
      case DCS_AREA_TEMPLATE_XSD -> makeAreaParameterExpressionOptional(xsd);
      case DATA_CORE_XSD -> relaxValueList(xsd);
      default -> {
      }
    }
  }

  /**
   * Корень файла: платформа и 1С:EDT пишут {@code DataCompositionSchema} с большой буквы, а XDTO объявляет
   * элемент {@code dataCompositionSchema}. Объявление переименовывается (а не дублируется): корня с маленькой
   * буквы в выгрузках не бывает, и файл с ним — не файл платформы.
   *
   * @param xsd схема {@code data-composition-system/schema}
   */
  static void renameRootToPlatformCase(Document xsd) {
    for (Element element : children(xsd.getDocumentElement(), "element")) {
      if ("dataCompositionSchema".equals(element.getAttribute("name"))) {
        element.setAttribute("name", "DataCompositionSchema");
      }
    }
  }

  /**
   * {@code NestedDataCompositionSchema}: в XDTO {@code URL} обязателен, а платформа пишет вложенную схему
   * телом ({@code schema}) без {@code URL}.
   *
   * @param xsd схема {@code data-composition-system/schema}
   */
  static void makeNestedSchemaUrlOptional(Document xsd) {
    topLevelType(xsd, "NestedDataCompositionSchema")
      .flatMap(type -> descendantElement(type, "URL"))
      .ifPresent(url -> url.setAttribute("minOccurs", "0"));
  }

  /**
   * {@code GroupTemplate} (шаблоны группировок {@code groupHeaderTemplate}/{@code groupTemplate}): XDTO
   * описывает одиночный {@code xs:choice} (ровно одно свойство), а платформа пишет заполненные свойства
   * подряд в порядке объявления ({@code groupName} и {@code templateType} перед {@code template}).
   * {@code xs:all}, как у {@code XsdPlatformShims#unorder}, не подходит: у {@code groupField}
   * {@code maxOccurs="unbounded"}, что в XSD 1.0 внутри {@code xs:all} запрещено.
   *
   * @param xsd схема {@code data-composition-system/schema}
   */
  static void unchainGroupTemplate(Document xsd) {
    topLevelType(xsd, "GroupTemplate").ifPresent(DcsXsdShims::choiceToSequenceOfOptionals);
  }

  /**
   * {@code ExpressionAreaTemplateParameter}: пустое выражение параметра области платформа не пишет вовсе,
   * а в XDTO {@code expression} обязателен.
   *
   * @param xsd схема {@code data-composition-system/area-template}
   */
  static void makeAreaParameterExpressionOptional(Document xsd) {
    topLevelType(xsd, "ExpressionAreaTemplateParameter")
      .flatMap(type -> descendantElement(type, "expression"))
      .ifPresent(expression -> expression.setAttribute("minOccurs", "0"));
  }

  /**
   * {@code ValueListType} (список значений в отборах и значениях параметров): XDTO описывает одиночный
   * {@code xs:choice}, а платформа пишет свойства подряд ({@code valueType} и {@code lastId} вместе).
   * {@code lastId} платформа пишет с {@code xsi:type="xs:decimal"}, который от объявленного {@code xs:long}
   * не выводится (наоборот, {@code long} — сужение {@code decimal}), поэтому тип меняется на {@code xs:decimal}.
   *
   * @param xsd схема {@code data/core}
   */
  static void relaxValueList(Document xsd) {
    topLevelType(xsd, "ValueListType").ifPresent(type -> {
      choiceToSequenceOfOptionals(type);
      descendantElement(type, "lastId")
        .ifPresent(lastId -> lastId.setAttribute("type", qualified(xsd.getDocumentElement(), "decimal")));
    });
  }

  /**
   * Заменяет одиночный {@code xs:choice} типа на {@code xs:sequence} тех же элементов в объявленном порядке,
   * где каждый необязателен, а {@code maxOccurs} сохраняется: платформа пишет только заполненные свойства,
   * но в порядке модели XDTO.
   *
   * @param type глобальный {@code complexType}
   */
  private static void choiceToSequenceOfOptionals(Element type) {
    Optional<Element> model = firstChild(type, "choice");
    if (model.isEmpty()) {
      return;
    }
    Element choice = model.get();
    Element sequence = type.getOwnerDocument().createElementNS(XS, qualified(choice, "sequence"));
    while (choice.getFirstChild() != null) {
      Node child = choice.getFirstChild();
      if (child instanceof Element element && XS.equals(element.getNamespaceURI())
        && "element".equals(element.getLocalName()) && !element.hasAttribute("minOccurs")) {
        element.setAttribute("minOccurs", "0");
      }
      sequence.appendChild(child);
    }
    type.replaceChild(sequence, choice);
  }

  private static String qualified(Element sibling, String localName) {
    String prefix = sibling.getPrefix();
    return prefix == null ? localName : prefix + ":" + localName;
  }

  private static Optional<Element> topLevelType(Document xsd, String name) {
    return children(xsd.getDocumentElement(), "complexType").stream()
      .filter(type -> name.equals(type.getAttribute("name")))
      .findFirst();
  }

  /** Объявление {@code xs:element name="…"} в любом месте типа (внутри sequence или extension). */
  private static Optional<Element> descendantElement(Element type, String name) {
    var nodes = type.getElementsByTagNameNS(XS, "element");
    for (int i = 0; i < nodes.getLength(); i++) {
      Element element = (Element) nodes.item(i);
      if (name.equals(element.getAttribute("name"))) {
        return Optional.of(element);
      }
    }
    return Optional.empty();
  }

  private static Optional<Element> firstChild(Element parent, String... kinds) {
    List<String> wanted = List.of(kinds);
    for (Node n = parent.getFirstChild(); n != null; n = n.getNextSibling()) {
      if (n instanceof Element e && XS.equals(e.getNamespaceURI()) && wanted.contains(e.getLocalName())) {
        return Optional.of(e);
      }
    }
    return Optional.empty();
  }

  private static List<Element> children(Element parent, String kind) {
    List<Element> out = new ArrayList<>();
    for (Node n = parent.getFirstChild(); n != null; n = n.getNextSibling()) {
      if (n instanceof Element e && XS.equals(e.getNamespaceURI()) && kind.equals(e.getLocalName())) {
        out.add(e);
      }
    }
    return out;
  }
}
