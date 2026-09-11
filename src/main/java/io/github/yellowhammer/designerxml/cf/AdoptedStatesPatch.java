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

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import com.ctc.wstx.stax.WstxInputFactory;

import io.github.yellowhammer.designerxml.SchemaVersion;
import jakarta.xml.bind.annotation.XmlType;

/**
 * Точечные правки заимствованного узла в выгрузке конфигуратора.
 *
 * Изменённое свойство платформа отмечает в {@code InternalInfo} записью
 * {@code xr:PropertyState} с состоянием «Extended»; так же его выгружает EDT.
 * Свойство, которого у заимствованного узла в файле не было, встаёт в
 * {@code Properties} на место по схеме, а не в конец: порядок элементов там
 * задан последовательностью.
 */
final class AdoptedStatesPatch {

  private static final String INTERNAL_INFO = "InternalInfo";
  private static final String PROPERTIES = "Properties";

  private AdoptedStatesPatch() {
  }

  /** Элемент выгрузки: имя и границы. */
  record Element(String name, int start, int end) {
  }

  /** Место нового элемента: начало строки и отступ, с которым он встаёт. */
  record Insertion(int at, String indent) {
  }

  /**
   * Порядок элементов {@code Properties} по схеме версии.
   *
   * @param containerLocal элемент узла: {@code Catalog}, {@code Attribute}
   * @return имена элементов выгрузки; пусто, если у схемы нет такого узла
   */
  static List<String> propertyOrder(SchemaVersion version, String containerLocal) {
    for (String pkg : version.jaxbContextPath().split(":")) {
      if (!pkg.endsWith(".mdclasses")) {
        continue;
      }
      try {
        XmlType type = Class.forName(pkg + "." + containerLocal + PROPERTIES).getAnnotation(XmlType.class);
        if (type == null) {
          return List.of();
        }
        List<String> order = new ArrayList<>();
        for (String field : type.propOrder()) {
          order.add(field.substring(0, 1).toUpperCase(Locale.ROOT) + field.substring(1));
        }
        return order;
      } catch (ClassNotFoundException absent) {
        return List.of();
      }
    }
    return List.of();
  }

  /**
   * Место нового свойства в {@code Properties}: строка за последним из записанных
   * элементов, которые схема ставит перед ним. Платформа и EDT расходятся в том,
   * где стоит принадлежность объекта, поэтому опора на предшественников, а не на
   * первый из последующих. Без предшественников место перед первым последующим,
   * а без тех строка закрывающего тега.
   */
  static Insertion insertionPoint(String xml, MdObjectXmlRegions.Region properties, List<String> order, String element)
      throws XMLStreamException {
    int place = order.indexOf(element);
    Element before = null;
    Element after = null;
    if (place >= 0) {
      for (Element written : children(xml, properties)) {
        int writtenPlace = order.indexOf(written.name());
        if (writtenPlace >= 0 && writtenPlace < place) {
          before = written;
        } else if (writtenPlace > place && after == null) {
          after = written;
        }
      }
    }
    if (before != null) {
      int eol = xml.indexOf('\n', before.end());
      int at = eol < 0 ? before.end() : eol + 1;
      return new Insertion(at, XmlGranularPatch.currentLineIndent(xml, before.start()));
    }
    if (after != null) {
      return new Insertion(lineStart(xml, after.start()), XmlGranularPatch.currentLineIndent(xml, after.start()));
    }
    int closing = xml.lastIndexOf("</", properties.end() - 1);
    return new Insertion(lineStart(xml, closing), XmlGranularPatch.currentLineIndent(xml, closing) + "\t");
  }

  /**
   * Замена, отмечающая свойства узла изменёнными.
   *
   * Запись состояния встаёт в {@code InternalInfo} после уже записанных, а
   * прежнее состояние того же свойства заменяется. Узлу без {@code InternalInfo}
   * блок ставится перед {@code Properties}.
   *
   * @param node границы узла: объекта или его подчинённого
   * @param properties имена элементов выгрузки
   */
  static XmlGranularPatch.Replacement extended(String xml, MdObjectXmlRegions.Region node, Set<String> properties)
      throws XMLStreamException {
    String eol = XmlGranularPatch.fileEol(xml);
    Element internalInfo = null;
    Element propertiesElement = null;
    for (Element child : children(xml, node)) {
      if (child.name().equals(INTERNAL_INFO)) {
        internalInfo = child;
      } else if (child.name().equals(PROPERTIES)) {
        propertiesElement = child;
      }
    }
    if (internalInfo == null) {
      if (propertiesElement == null) {
        throw new IllegalStateException("У заимствованного узла нет Properties.");
      }
      int at = lineStart(xml, propertiesElement.start());
      String indent = XmlGranularPatch.currentLineIndent(xml, propertiesElement.start());
      StringBuilder block = new StringBuilder(indent).append("<InternalInfo>").append(eol);
      for (String property : properties) {
        block.append(state(property, indent + "\t", eol));
      }
      block.append(indent).append("</InternalInfo>").append(eol);
      return new XmlGranularPatch.Replacement(at, at, block.toString());
    }

    String text = xml.substring(internalInfo.start(), internalInfo.end());
    String indent = XmlGranularPatch.currentLineIndent(xml, internalInfo.start());
    if (text.endsWith("/>")) {
      StringBuilder block = new StringBuilder("<InternalInfo>").append(eol);
      for (String property : properties) {
        block.append(state(property, indent + "\t", eol));
      }
      block.append(indent).append("</InternalInfo>");
      return new XmlGranularPatch.Replacement(internalInfo.start(), internalInfo.end(), block.toString());
    }
    StringBuilder appended = new StringBuilder();
    for (String property : properties) {
      Matcher written = Pattern.compile(
          "(<xr:PropertyState>\\s*<xr:Property>" + Pattern.quote(property) + "</xr:Property>\\s*<xr:State>)[^<]*(</xr:State>)")
          .matcher(text);
      if (written.find()) {
        text = text.substring(0, written.start()) + written.group(1) + AdoptedStates.EXTENDED + written.group(2)
            + text.substring(written.end());
      } else {
        appended.append(state(property, indent + "\t", eol));
      }
    }
    int closing = lineStart(text, text.lastIndexOf("</"));
    text = text.substring(0, closing) + appended + text.substring(closing);
    return new XmlGranularPatch.Replacement(internalInfo.start(), internalInfo.end(), text);
  }

  private static String state(String property, String indent, String eol) {
    return indent + "<xr:PropertyState>" + eol
        + indent + "\t<xr:Property>" + property + "</xr:Property>" + eol
        + indent + "\t<xr:State>" + AdoptedStates.EXTENDED + "</xr:State>" + eol
        + indent + "</xr:PropertyState>" + eol;
  }

  /** Начало строки, в которой лежит смещение. */
  static int lineStart(String xml, int offset) {
    int line = xml.lastIndexOf('\n', Math.max(offset - 1, 0));
    return line < 0 ? 0 : line + 1;
  }

  /** Прямые дочерние элементы узла в порядке файла. */
  static List<Element> children(String xml, MdObjectXmlRegions.Region parent) throws XMLStreamException {
    List<Element> children = new ArrayList<>();
    XMLInputFactory factory = new WstxInputFactory();
    factory.setProperty(XMLInputFactory.IS_NAMESPACE_AWARE, Boolean.TRUE);
    XMLStreamReader reader = factory.createXMLStreamReader(new StringReader(xml));
    try {
      int depth = 0;
      int parentDepth = -1;
      Element current = null;
      while (reader.hasNext()) {
        int event = reader.next();
        if (event == XMLStreamConstants.START_ELEMENT) {
          depth++;
          int start = reader.getLocation().getCharacterOffset();
          if (parentDepth < 0 && start == parent.start()) {
            parentDepth = depth;
          } else if (parentDepth >= 0 && depth == parentDepth + 1) {
            current = new Element(reader.getLocalName(), start, -1);
          }
        } else if (event == XMLStreamConstants.END_ELEMENT) {
          if (parentDepth >= 0 && depth == parentDepth + 1 && current != null) {
            int gt = xml.indexOf('>', reader.getLocation().getCharacterOffset());
            children.add(new Element(current.name(), current.start(), gt + 1));
            current = null;
          } else if (parentDepth >= 0 && depth == parentDepth) {
            break;
          }
          depth--;
        }
      }
    } finally {
      reader.close();
    }
    return children;
  }
}
