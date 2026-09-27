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

import com.ctc.wstx.stax.WstxInputFactory;

import javax.xml.stream.Location;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Правка выгрузки платформы по строкам текста, без пересериализации.
 *
 * <p>Структуру разбирает StAX, а меняется исходный текст по смещениям: так сохраняются BOM,
 * CRLF, {@code <X/>} и отсутствие перевода строки в конце файла. Смещения Woodstox считает
 * в символах исходного текста, включая BOM и {@code \r}.
 */
final class XmlLines {

  /** Прямой потомок элемента: имя, границы в тексте и значения его простых потомков. */
  record Node(String name, int start, int end, Map<String, String> leaves) {
  }

  private XmlLines() {
  }

  static XMLStreamReader reader(String xml) throws XMLStreamException {
    XMLInputFactory factory = new WstxInputFactory();
    factory.setProperty(XMLInputFactory.IS_NAMESPACE_AWARE, Boolean.TRUE);
    factory.setProperty(XMLInputFactory.SUPPORT_DTD, Boolean.FALSE);
    factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, Boolean.FALSE);
    return factory.createXMLStreamReader(new StringReader(xml));
  }

  /** Смещение начала текущего события (для START_ELEMENT - символ {@code <}). */
  static int offset(XMLStreamReader reader) {
    Location location = reader.getLocation();
    int offset = location == null ? -1 : location.getCharacterOffset();
    if (offset < 0) {
      throw new IllegalStateException("разборщик XML не сообщает смещение в тексте");
    }
    return offset;
  }

  /** Индекс {@code >}, закрывающего открывающий тег с началом {@code lt}; кавычки атрибутов учитываются. */
  static int startTagEnd(String xml, int lt) {
    char quote = 0;
    for (int i = lt + 1; i < xml.length(); i++) {
      char c = xml.charAt(i);
      if (quote != 0) {
        if (c == quote) {
          quote = 0;
        }
      } else if (c == '"' || c == '\'') {
        quote = c;
      } else if (c == '>') {
        return i;
      }
    }
    throw new IllegalStateException("незакрытый тег в позиции " + lt);
  }

  /**
   * Конец элемента по событию END_ELEMENT: за {@code </x>} или, для {@code <x/>}, за самим тегом.
   *
   * @param endEventOffset смещение события END_ELEMENT
   * @return смещение первого символа после элемента
   */
  static int elementEnd(String xml, int endEventOffset) {
    if (xml.startsWith("</", endEventOffset)) {
      int gt = xml.indexOf('>', endEventOffset);
      if (gt < 0) {
        throw new IllegalStateException("незакрытый тег в позиции " + endEventOffset);
      }
      return gt + 1;
    }
    // у пустого элемента конец сообщается в позиции его единственного тега
    int gt = startTagEnd(xml, endEventOffset);
    if (xml.charAt(gt - 1) != '/') {
      throw new IllegalStateException("не найден конец элемента в позиции " + endEventOffset);
    }
    return gt + 1;
  }

  /**
   * Пропускает элемент, на START_ELEMENT которого стоит курсор, вместе с потомками.
   *
   * @return смещение первого символа после элемента; курсор - на его END_ELEMENT
   */
  static int skipElement(String xml, XMLStreamReader reader) throws XMLStreamException {
    int depth = 1;
    while (reader.hasNext()) {
      int event = reader.next();
      if (event == XMLStreamConstants.START_ELEMENT) {
        depth++;
      } else if (event == XMLStreamConstants.END_ELEMENT) {
        depth--;
        if (depth == 0) {
          return elementEnd(xml, offset(reader));
        }
      }
    }
    throw new IllegalStateException("элемент не закрыт до конца текста");
  }

  /**
   * Строки, которые элемент занимает целиком, вместе с переводом строки после него.
   *
   * <p>Удалять можно только так: если рядом с элементом на строке есть другой текст, вырезка
   * испортит соседей, поэтому такой файл отвергается, а не правится молча.
   *
   * @param start смещение {@code <} элемента
   * @param end смещение за концом элемента
   * @param what имя элемента для сообщения
   * @return {@code [начало, конец)} вырезаемого диапазона
   * @throws IllegalStateException если элемент делит строку с другим текстом
   */
  static int[] wholeLines(String xml, int start, int end, String what) {
    int lineStart = xml.lastIndexOf('\n', start - 1) + 1;
    if (!blank(xml, lineStart, start)) {
      throw new IllegalStateException(
        "элемент " + what + " начинается не с начала строки (позиция " + start + "): построчно не вырезать");
    }
    int after = end;
    while (after < xml.length() && (xml.charAt(after) == ' ' || xml.charAt(after) == '\t')) {
      after++;
    }
    if (after == xml.length()) {
      // последняя строка файла без перевода строки: забираем перевод строки перед ней
      int from = lineStart;
      if (from > 0 && xml.charAt(from - 1) == '\n') {
        from--;
        if (from > 0 && xml.charAt(from - 1) == '\r') {
          from--;
        }
      }
      return new int[] {from, after};
    }
    if (xml.startsWith("\r\n", after)) {
      return new int[] {lineStart, after + 2};
    }
    if (xml.charAt(after) == '\n') {
      return new int[] {lineStart, after + 1};
    }
    throw new IllegalStateException(
      "за элементом " + what + " на той же строке есть другой текст (позиция " + end + "): построчно не вырезать");
  }

  /** Вырезает непересекающиеся диапазоны {@code [начало, конец)}. */
  static String removeAll(String xml, List<int[]> spans) {
    if (spans.isEmpty()) {
      return xml;
    }
    List<int[]> sorted = new ArrayList<>(spans);
    sorted.sort(Comparator.comparingInt(span -> span[0]));
    StringBuilder out = new StringBuilder(xml.length());
    int copied = 0;
    for (int[] span : sorted) {
      if (span[0] < copied) {
        throw new IllegalStateException("пересекающиеся вырезки в позиции " + span[0]);
      }
      out.append(xml, copied, span[0]);
      copied = span[1];
    }
    out.append(xml, copied, xml.length());
    return out.toString();
  }

  /** Перевод строки файла: выгрузка платформы идёт с CRLF, вставки не должны выбиваться. */
  static String newline(String xml) {
    return xml.contains("\r\n") ? "\r\n" : "\n";
  }

  /** Пробелы и табуляции от начала строки до {@code offset}. */
  static String indentAt(String xml, int offset) {
    int lineStart = xml.lastIndexOf('\n', offset - 1) + 1;
    return xml.substring(lineStart, offset);
  }

  /**
   * Прямые потомки элемента, заданного путём локальных имён от корня (первое совпадение).
   *
   * @param path например {@code MetaDataObject, Configuration, Properties}
   * @return потомки в порядке текста; пусто, если элемента нет
   */
  static List<Node> children(String xml, List<String> path) {
    try {
      XMLStreamReader reader = reader(xml);
      try {
        List<Node> result = new ArrayList<>();
        List<String> stack = new ArrayList<>();
        while (reader.hasNext()) {
          int event = reader.next();
          if (event == XMLStreamConstants.START_ELEMENT) {
            if (stack.equals(path)) {
              result.add(readNode(xml, reader));
            } else {
              stack.add(reader.getLocalName());
            }
          } else if (event == XMLStreamConstants.END_ELEMENT) {
            if (stack.equals(path)) {
              return result;
            }
            stack.remove(stack.size() - 1);
          }
        }
        return result;
      } finally {
        reader.close();
      }
    } catch (XMLStreamException e) {
      throw new IllegalArgumentException("не удалось разобрать XML: " + e.getMessage(), e);
    }
  }

  /** Курсор на START_ELEMENT узла; после вызова - на его END_ELEMENT. */
  private static Node readNode(String xml, XMLStreamReader reader) throws XMLStreamException {
    String name = reader.getLocalName();
    int start = offset(reader);
    Map<String, String> leaves = new LinkedHashMap<>();
    int depth = 0;
    String leaf = null;
    StringBuilder text = new StringBuilder();
    while (reader.hasNext()) {
      int event = reader.next();
      if (event == XMLStreamConstants.START_ELEMENT) {
        depth++;
        leaf = depth == 1 ? reader.getLocalName() : null;
        text.setLength(0);
      } else if (event == XMLStreamConstants.CHARACTERS || event == XMLStreamConstants.CDATA) {
        if (depth == 1 && leaf != null) {
          text.append(reader.getText());
        }
      } else if (event == XMLStreamConstants.END_ELEMENT) {
        if (depth == 0) {
          return new Node(name, start, elementEnd(xml, offset(reader)), leaves);
        }
        if (depth == 1 && leaf != null) {
          leaves.putIfAbsent(leaf, text.toString());
        }
        depth--;
      }
    }
    throw new IllegalStateException("элемент " + name + " не закрыт до конца текста");
  }

  private static boolean blank(String xml, int from, int to) {
    for (int i = from; i < to; i++) {
      char c = xml.charAt(i);
      if (c != ' ' && c != '\t') {
        return false;
      }
    }
    return true;
  }
}
