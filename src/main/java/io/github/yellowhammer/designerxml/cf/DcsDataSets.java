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
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Адресация наборов данных схемы компоновки путём {@code Объединение/Вложенный}.
 *
 * <p>Имя вложенного набора уникально только внутри своего объединения: одно имя бывает у наборов
 * разных объединений схемы. Поэтому вложенный набор адресуется путём от набора верхнего уровня,
 * а имя без {@code /} означает набор верхнего уровня.
 * Наборы вложенных схем ({@code nestedSchema}) сюда не входят: у них своя схема.
 */
final class DcsDataSets {

  /** Набор данных: узел {@code dataSet} или {@code item} объединения, его вид и путь от корня. */
  record Target(XmlLines.Node node, String kind, String path) {
  }

  private static final List<String> ROOT = List.of("DataCompositionSchema");

  private static final Pattern XSI_TYPE = Pattern.compile("xsi:type=[\"']([^\"']+)[\"']");

  private DcsDataSets() {
  }

  /**
   * Набор по пути.
   *
   * @param text текст схемы
   * @param path имя набора верхнего уровня либо путь {@code Объединение/…/Вложенный}
   * @throws IllegalArgumentException если в пути пустое имя, звено пути не объединение или набора нет
   */
  static Target resolve(String text, String path) {
    String[] parts = path.split("/", -1);
    for (String part : parts) {
      if (part.isBlank()) {
        throw new IllegalArgumentException("В пути набора данных «" + path + "» пустое имя.");
      }
    }
    Target current = null;
    for (int i = 0; i < parts.length; i++) {
      String name = parts[i].trim();
      if (current != null && !"union".equals(current.kind())) {
        throw new IllegalArgumentException("Набор данных «" + current.path()
          + "» — не объединение: вложенных наборов у него нет.");
      }
      List<XmlLines.Node> candidates = current == null
        ? XmlLines.children(text, ROOT)
        : XmlLines.childrenAt(text, current.node().start());
      String element = current == null ? "dataSet" : "item";
      XmlLines.Node found = null;
      for (XmlLines.Node node : candidates) {
        if (element.equals(node.name()) && name.equals(node.leaves().get("name"))) {
          found = node;
          break;
        }
      }
      if (found == null) {
        if (current != null) {
          throw new IllegalArgumentException(
            "В объединении «" + current.path() + "» нет вложенного набора «" + name + "».");
        }
        throw new IllegalArgumentException("Набор данных не найден: " + path + nestedHint(text, name));
      }
      String walked = current == null ? name : current.path() + "/" + name;
      current = new Target(found, kind(text, found), walked);
    }
    return current;
  }

  /** Все наборы-запросы схемы на любой глубине объединений, в порядке файла. */
  static List<Target> queries(String text) {
    List<Target> out = new ArrayList<>();
    for (Target set : all(text)) {
      if ("query".equals(set.kind())) {
        out.add(set);
      }
    }
    return out;
  }

  /** Все наборы схемы: верхнего уровня и вложенные в объединения, в порядке файла. */
  static List<Target> all(String text) {
    List<Target> out = new ArrayList<>();
    for (XmlLines.Node node : XmlLines.children(text, ROOT)) {
      if ("dataSet".equals(node.name())) {
        collect(text, node, "", out);
      }
    }
    return out;
  }

  private static void collect(String text, XmlLines.Node node, String parent, List<Target> out) {
    String name = node.leaves().getOrDefault("name", "");
    String path = parent.isEmpty() ? name : parent + "/" + name;
    String kind = kind(text, node);
    out.add(new Target(node, kind, path));
    if (!"union".equals(kind)) {
      return;
    }
    for (XmlLines.Node child : XmlLines.childrenAt(text, node.start())) {
      if ("item".equals(child.name())) {
        collect(text, child, path, out);
      }
    }
  }

  /** Подсказка пути, когда имя без {@code /} нашлось только внутри объединений. */
  private static String nestedHint(String text, String name) {
    List<String> paths = new ArrayList<>();
    for (Target set : all(text)) {
      if (set.path().contains("/") && set.path().endsWith("/" + name)) {
        paths.add(set.path());
      }
    }
    if (paths.isEmpty()) {
      return "";
    }
    return " (вложенный набор с этим именем адресуется путём: " + String.join(", ", paths) + ")";
  }

  /** Вид набора по xsi:type открывающего тега: query, object или union. */
  static String kind(String text, XmlLines.Node set) {
    String tag = text.substring(set.start(), XmlLines.startTagEnd(text, set.start()) + 1);
    Matcher type = XSI_TYPE.matcher(tag);
    String value = type.find() ? type.group(1) : "";
    return switch (value.substring(value.indexOf(':') + 1)) {
      case "DataSetQuery" -> "query";
      case "DataSetObject" -> "object";
      case "DataSetUnion" -> "union";
      default -> throw new IllegalArgumentException("Неизвестный вид набора данных: " + value);
    };
  }
}
