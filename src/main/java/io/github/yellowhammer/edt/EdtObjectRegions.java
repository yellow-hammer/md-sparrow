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

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import com.ctc.wstx.stax.WstxInputFactory;

/**
 * Границы элементов в файле объекта 1С:EDT.
 *
 * Правка одного свойства должна менять один участок файла, а остальное
 * оставлять байт в байт. Границы считаются разбором, а не поиском по тексту:
 * искать теги строкой значило бы гадать про переносы, отступы и вложенность.
 *
 * Разбирается всегда файл целиком, даже когда нужен узел внутри узла: у
 * вырезанного куска пространства имён объявлены в корне, и сам по себе он не
 * разбирается.
 */
public final class EdtObjectRegions {

  private EdtObjectRegions() {
  }

  /**
   * Участок файла.
   *
   * @param start смещение первого символа
   * @param end смещение за последним символом
   */
  public record Region(int start, int end) {

    /** Найден ли участок. */
    public boolean found() {
      return start >= 0 && end > start;
    }

    /** Лежит ли участок внутри другого. */
    public boolean inside(Region outer) {
      return start >= outer.start() && end <= outer.end();
    }
  }

  /** Ненайденный участок. */
  public static final Region MISSING = new Region(-1, -1);

  /** Весь файл: узлы ищутся по всей его глубине первого уровня. */
  private static final Region ANYWHERE = new Region(0, Integer.MAX_VALUE);

  /**
   * Границы свойства объекта.
   *
   * @param xml содержимое файла
   * @param name имя элемента: {@code hierarchical}, {@code synonym}
   * @return границы первого такого элемента верхнего уровня
   * @throws XMLStreamException если файл не разбирается
   */
  public static Region property(String xml, String name) throws XMLStreamException {
    List<Region> found = properties(xml, name);
    return found.isEmpty() ? MISSING : found.get(0);
  }

  /**
   * Границы всех одноимённых свойств объекта.
   *
   * @param xml содержимое файла
   * @param name имя элемента
   * @return границы элементов верхнего уровня в порядке файла
   * @throws XMLStreamException если файл не разбирается
   */
  public static List<Region> properties(String xml, String name) throws XMLStreamException {
    return elements(xml, name, 2, ANYWHERE);
  }

  /**
   * Границы узлов, лежащих внутри другого узла.
   *
   * @param xml содержимое файла
   * @param parent границы узла-владельца
   * @param feature вид вложенного узла: {@code attributes} у табличной части
   * @return границы узлов в порядке файла
   * @throws XMLStreamException если файл не разбирается
   */
  public static List<Region> nested(String xml, Region parent, String feature) throws XMLStreamException {
    return elements(xml, feature, 0, parent);
  }

  /**
   * Границы узла по его имени.
   *
   * @param xml содержимое файла
   * @param regions границы узлов одного вида
   * @param name имя узла
   * @return границы узла или {@link #MISSING}
   * @throws XMLStreamException если файл не разбирается
   */
  public static Region byName(String xml, List<Region> regions, String name) throws XMLStreamException {
    for (Region region : regions) {
      if (name.equals(nameOf(xml, region))) {
        return region;
      }
    }
    return MISSING;
  }

  /**
   * Имена узлов в порядке файла.
   *
   * @param xml содержимое файла
   * @param regions границы узлов одного вида
   * @return имена узлов
   * @throws XMLStreamException если файл не разбирается
   */
  public static List<String> names(String xml, List<Region> regions) throws XMLStreamException {
    List<String> names = new ArrayList<>();
    for (Region region : regions) {
      String name = nameOf(xml, region);
      if (name != null) {
        names.add(name);
      }
    }
    return names;
  }

  /**
   * Границы имени узла.
   *
   * @param xml содержимое файла
   * @param node границы узла
   * @return границы его элемента {@code name} или {@link #MISSING}
   * @throws XMLStreamException если файл не разбирается
   */
  public static Region nameRegion(String xml, Region node) throws XMLStreamException {
    XMLStreamReader reader = reader(xml);
    try {
      while (reader.hasNext()) {
        if (reader.next() != XMLStreamConstants.START_ELEMENT) {
          continue;
        }
        int start = offset(reader);
        if (start < node.start()) {
          continue;
        }
        if (start >= node.end()) {
          return MISSING;
        }
        if (reader.getLocalName().equals("name")) {
          int end = skipElement(xml, reader);
          if (end > start) {
            return new Region(start, end);
          }
        }
      }
    } finally {
      reader.close();
    }
    return MISSING;
  }

  /** Имя узла: его собственный элемент {@code name}. */
  private static String nameOf(String xml, Region node) throws XMLStreamException {
    Region name = nameRegion(xml, node);
    if (!name.found()) {
      return null;
    }
    String element = xml.substring(name.start(), name.end());
    int open = element.indexOf('>');
    int close = element.lastIndexOf("</");
    return open < 0 || close < open ? "" : element.substring(open + 1, close);
  }

  /**
   * Имена свойств объекта в порядке файла.
   *
   * @param xml содержимое файла
   * @return имена элементов верхнего уровня, с повторами
   * @throws XMLStreamException если файл не разбирается
   */
  public static List<String> propertyNames(String xml) throws XMLStreamException {
    List<String> names = new ArrayList<>();
    XMLStreamReader reader = reader(xml);
    try {
      int depth = 0;
      while (reader.hasNext()) {
        int event = reader.next();
        if (event == XMLStreamConstants.START_ELEMENT) {
          depth++;
          if (depth == 2) {
            names.add(reader.getLocalName());
          }
        } else if (event == XMLStreamConstants.END_ELEMENT) {
          depth--;
        }
      }
    } finally {
      reader.close();
    }
    return names;
  }

  /**
   * Место, куда встаёт новое свойство.
   *
   * Порядок свойств в файле повторяет порядок схемы, поэтому новое встаёт перед
   * первым из тех, что схема ставит после него. Если таких в файле нет, свойство
   * идёт последним, перед закрывающим тегом объекта.
   *
   * @param xml содержимое файла
   * @param order имена свойств в порядке схемы
   * @param name имя нового свойства
   * @return смещение начала строки, перед которой встаёт свойство
   * @throws XMLStreamException если файл не разбирается
   */
  public static int insertionPoint(String xml, List<String> order, String name) throws XMLStreamException {
    int place = order.indexOf(name);
    for (String written : propertyNames(xml)) {
      int writtenPlace = order.indexOf(written);
      if (place >= 0 && writtenPlace > place) {
        Region region = property(xml, written);
        if (region.found()) {
          return lineStart(xml, region.start());
        }
      }
    }
    return closingTagStart(xml);
  }

  /** Начало строки, в которой лежит смещение: вставка идёт целой строкой. */
  public static int lineStart(String xml, int offset) {
    int line = xml.lastIndexOf('\n', Math.max(offset - 1, 0));
    return line < 0 ? offset : line + 1;
  }

  /** Начало закрывающего тега объекта: за ним файла уже нет. */
  private static int closingTagStart(String xml) {
    int closing = xml.lastIndexOf("</");
    return closing < 0 ? xml.length() : lineStart(xml, closing);
  }

  /**
   * Границы одноимённых элементов внутри участка.
   *
   * @param depth глубина от корня файла; {@code 0} - непосредственно внутри
   *     участка, на какой бы глубине тот ни лежал
   */
  private static List<Region> elements(String xml, String name, int depth, Region within)
      throws XMLStreamException {
    List<Region> regions = new ArrayList<>();
    XMLStreamReader reader = reader(xml);
    try {
      int current = 0;
      int wanted = depth;
      while (reader.hasNext()) {
        int event = reader.next();
        if (event == XMLStreamConstants.START_ELEMENT) {
          current++;
          int start = offset(reader);
          if (wanted == 0 && start == within.start()) {
            // Глубина участка становится известна, когда разбор до него дошёл
            wanted = current + 1;
            continue;
          }
          if (current == wanted && reader.getLocalName().equals(name)) {
            int end = start < 0 ? -1 : skipElement(xml, reader);
            if (end > start && new Region(start, end).inside(within)) {
              regions.add(new Region(start, end));
            }
            current--;
          }
        } else if (event == XMLStreamConstants.END_ELEMENT) {
          current--;
        }
      }
    } finally {
      reader.close();
    }
    return regions;
  }

  private static XMLStreamReader reader(String xml) throws XMLStreamException {
    XMLInputFactory factory = new WstxInputFactory();
    factory.setProperty(XMLInputFactory.IS_NAMESPACE_AWARE, Boolean.TRUE);
    factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
    factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
    return factory.createXMLStreamReader(new StringReader(xml));
  }

  private static int offset(XMLStreamReader reader) {
    javax.xml.stream.Location location = reader.getLocation();
    return location == null ? -1 : location.getCharacterOffset();
  }

  /** Курсор стоит на открывающем теге; после вызова - за элементом. */
  private static int skipElement(String xml, XMLStreamReader reader) throws XMLStreamException {
    int depth = 1;
    while (reader.hasNext()) {
      int event = reader.next();
      if (event == XMLStreamConstants.START_ELEMENT) {
        depth++;
      } else if (event == XMLStreamConstants.END_ELEMENT) {
        depth--;
        if (depth == 0) {
          int endTag = offset(reader);
          if (endTag < 0) {
            return -1;
          }
          int close = xml.indexOf('>', endTag);
          return close < 0 ? -1 : close + 1;
        }
      }
    }
    return -1;
  }
}
