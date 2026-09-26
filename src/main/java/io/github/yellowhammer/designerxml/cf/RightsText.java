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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Текст файла прав роли: шапка с флагами и блоки объектов с позициями в тексте.
 *
 * <p>У выгрузки конфигуратора ({@code Ext/Rights.xml}) и проекта 1С:EDT
 * ({@code Rights.rights}) разметка одна. Файл разбирается по тексту, а не деревом:
 * правка меняет один блок и оставляет остальной файл байт в байт.
 */
final class RightsText {

  private static final Pattern FLAG = Pattern.compile("<(\\w+)>(true|false)</\\1>");
  private static final Pattern RIGHT = Pattern.compile(
    "<right>\\s*<name>([^<]+)</name>\\s*<value>([^<]*)</value>(.*?)</right>", Pattern.DOTALL);
  private static final Pattern RESTRICTION = Pattern.compile(
    "<restrictionByCondition>(.*?)</restrictionByCondition>", Pattern.DOTALL);
  private static final Pattern FIELD = Pattern.compile("<field>([^<]*)</field>");
  private static final Pattern CONDITION = Pattern.compile("<condition>(.*?)</condition>", Pattern.DOTALL);
  private static final String OBJECT_OPEN = "<object>";
  private static final String OBJECT_CLOSE = "</object>";
  private static final String NAME_OPEN = "<name>";

  private RightsText() {
  }

  /**
   * Право объекта в файле.
   *
   * @param tail всё, что стоит в праве после значения: ограничения доступа по условию
   */
  record Right(String name, boolean value, String tail) {

    boolean restricted() {
      return tail.contains("<restrictionByCondition>");
    }
  }

  /**
   * Блок объекта.
   *
   * @param start начало строки с открывающим тегом
   * @param end позиция за концом строки с закрывающим тегом
   */
  record Block(String name, int start, int end, List<Right> rights) {
  }

  /** Флаг «Устанавливать права для новых объектов» из шапки файла. */
  static boolean setForNewObjects(String text) {
    int head = text.indexOf(OBJECT_OPEN);
    Matcher matcher = FLAG.matcher(head < 0 ? text : text.substring(0, head));
    while (matcher.find()) {
      if ("setForNewObjects".equals(matcher.group(1))) {
        return Boolean.parseBoolean(matcher.group(2));
      }
    }
    return false;
  }

  /** Все блоки объектов в порядке файла. */
  static List<Block> blocks(String text) {
    List<Block> out = new ArrayList<>();
    int from = 0;
    while (true) {
      int open = text.indexOf(OBJECT_OPEN, from);
      if (open < 0) {
        return out;
      }
      Block block = blockAt(text, open);
      if (block == null) {
        return out;
      }
      out.add(block);
      from = block.end();
    }
  }

  /**
   * Блоки объекта и его подчинённых: сам {@code Catalog.Товары} и
   * {@code Catalog.Товары.Attribute.Цена}, но не {@code Catalog.ТоварыПоставщиков}.
   */
  static List<Block> blocksOf(String text, String objectName) {
    List<Block> out = new ArrayList<>();
    String needle = NAME_OPEN + objectName;
    int from = 0;
    while (true) {
      int at = text.indexOf(needle, from);
      if (at < 0) {
        return out;
      }
      from = at + needle.length();
      char next = from < text.length() ? text.charAt(from) : ' ';
      if (next != '<' && next != '.') {
        continue;
      }
      int open = text.lastIndexOf(OBJECT_OPEN, at);
      if (open < 0 || !text.substring(open + OBJECT_OPEN.length(), at).isBlank()) {
        continue;
      }
      Block block = blockAt(text, open);
      if (block != null) {
        out.add(block);
        from = block.end();
      }
    }
  }

  private static Block blockAt(String text, int open) {
    int nameStart = text.indexOf(NAME_OPEN, open);
    int nameEnd = text.indexOf("</name>", nameStart);
    int close = text.indexOf(OBJECT_CLOSE, open);
    if (nameStart < 0 || nameEnd < 0 || close < 0 || nameEnd > close) {
      return null;
    }
    String name = text.substring(nameStart + NAME_OPEN.length(), nameEnd).trim();
    List<Right> rights = new ArrayList<>();
    Matcher matcher = RIGHT.matcher(text.substring(nameEnd, close));
    while (matcher.find()) {
      rights.add(new Right(matcher.group(1).trim(), "true".equals(matcher.group(2).trim()), matcher.group(3)));
    }
    int start = lineStart(text, open);
    int end = close + OBJECT_CLOSE.length();
    int lineEnd = text.indexOf('\n', end);
    if (lineEnd >= 0 && text.substring(end, lineEnd).isBlank()) {
      end = lineEnd + 1;
    }
    return new Block(name, start, end, rights);
  }

  static int lineStart(String text, int position) {
    int previous = text.lastIndexOf('\n', position - 1);
    return previous < 0 ? 0 : previous + 1;
  }

  /** Ограничения доступа права: поля и текст условия. */
  static List<Map<String, Object>> restrictions(String tail) {
    List<Map<String, Object>> out = new ArrayList<>();
    Matcher matcher = RESTRICTION.matcher(tail);
    while (matcher.find()) {
      List<String> fields = new ArrayList<>();
      Matcher field = FIELD.matcher(matcher.group(1));
      while (field.find()) {
        fields.add(unescape(field.group(1).trim()));
      }
      Matcher condition = CONDITION.matcher(matcher.group(1));
      Map<String, Object> item = new LinkedHashMap<>();
      item.put("fields", fields);
      // Разбор XML приводит переводы строк в тексте к одному символу
      item.put("condition", condition.find() ? unescape(condition.group(1).replace("\r\n", "\n")) : "");
      out.add(item);
    }
    return out;
  }

  static String unescape(String value) {
    return value
      .replace("&lt;", "<")
      .replace("&gt;", ">")
      .replace("&quot;", "\"")
      .replace("&apos;", "'")
      .replace("&amp;", "&");
  }
}
