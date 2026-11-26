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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Параметры запросов схемы компоновки, не объявленные в схеме: предупреждения {@code cf-dcs-validate}.
 *
 * <p>Конфигуратор сам объявляет в схеме каждый {@code &}-параметр текста запроса, правка текста мимо
 * него (в том числе {@code cf-dcs-set-query}) этого не делает. Схема читается, но отчёт параметр не
 * заполнит, поэтому признак {@code ok} проверки предупреждение не меняет.
 */
public final class DcsQueryParameters {

  /** Значение ключа {@code kind} предупреждения. */
  public static final String KIND = "undeclaredQueryParameter";

  private static final List<String> ROOT = List.of("DataCompositionSchema");

  private DcsQueryParameters() {
  }

  /**
   * Предупреждения о необъявленных параметрах запросов всех наборов-запросов схемы, включая
   * вложенные в объединения.
   *
   * @param templateXml файл схемы
   * @return {@code [{message, parameter, dataSet, kind}]} в порядке наборов и параметров, {@code kind}:
   *     {@value #KIND}; пусто, если все параметры объявлены или файл не разбирается как XML
   */
  public static List<Map<String, Object>> warnings(Path templateXml) throws IOException {
    String text = Files.readString(templateXml, StandardCharsets.UTF_8);
    List<Map<String, Object>> out = new ArrayList<>();
    List<XmlLines.Node> roots;
    List<DcsDataSets.Target> queries;
    try {
      roots = XmlLines.children(text, ROOT);
      queries = DcsDataSets.queries(text);
    } catch (RuntimeException e) {
      return out;
    }
    Set<String> declared = new HashSet<>();
    for (XmlLines.Node node : roots) {
      if ("parameter".equals(node.name())) {
        declared.add(upper(node.leaves().getOrDefault("name", "")));
      } else if ("dataSetLink".equals(node.name())) {
        // Параметр связи наборов заполняет запрос приёмника значениями источника
        String linked = node.leaves().get("parameter");
        if (linked != null) {
          declared.add(upper(linked));
        }
      }
    }
    for (DcsDataSets.Target set : queries) {
      for (String name : names(set.node().leaves().getOrDefault("query", ""))) {
        if (declared.contains(upper(name))) {
          continue;
        }
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("message", "параметр запроса &" + name + " не объявлен в схеме (набор данных " + set.path() + ")");
        row.put("parameter", name);
        row.put("dataSet", set.path());
        row.put("kind", KIND);
        out.add(row);
      }
    }
    return out;
  }

  /**
   * Имена {@code &}-параметров текста запроса в порядке появления, без повторов и без учёта регистра,
   * как у платформы. Параметры в блоках {@code {…}} расширения языка запросов учитываются, в строках
   * {@code "…"} и комментариях {@code //…} нет.
   */
  static List<String> names(String query) {
    return DcsQueryModel.parametersOf(query);
  }

  private static String upper(String value) {
    return value.toUpperCase(Locale.ROOT);
  }
}
