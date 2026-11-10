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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Связи наборов с флагом «Список параметров» без параметра связи, который загрузка в платформу отбросит:
 * предупреждения {@code cf-dcs-validate} вида {@value #KIND}.
 *
 * <p>У связи без {@code parameter} загрузка молча отбрасывает записанный {@code parameterListAllowed} — и
 * {@code false}, и {@code true}; у связи с параметром флаг хранится. {@code cf-dcs-add-link} и
 * {@code cf-dcs-set-link} такой флаг не пишут, но он бывает в файле, правленном мимо них. Файл читается и
 * переносится эхом без изменений: предупреждение называет связь, у которой загрузка флаг потеряет, а
 * признак {@code ok} проверки не меняет.
 *
 * <p>Правило совпадает с тем, что видно в {@code cf-dcs-info}: у связи есть {@code parameterListAllowed}, а
 * {@code parameter} нет или он пустой. Учитываются связи корня схемы; связи вложенных схем только читаются.
 */
public final class DcsLegacyLinkListFlags {

  /** Значение ключа {@code kind} предупреждения. */
  public static final String KIND = "linkListFlagWithoutParameter";

  private static final List<String> ROOT = List.of("DataCompositionSchema");

  private static final String FLAG = "parameterListAllowed";

  private static final Pattern TRUE_FLAG = Pattern.compile("<[^>]*>\\s*(?:true|1)\\s*</[^>]*>");

  private DcsLegacyLinkListFlags() {
  }

  /**
   * Предупреждения о связях корня схемы с флагом списка без параметра связи.
   *
   * @param templateXml файл схемы (выгрузка конфигуратора или {@code Template.dcs} 1С:EDT)
   * @return {@code [{message, link, kind}]} в порядке связей, {@code link} - порядковый номер связи в файле с
   *     нуля (как {@code index} у {@code cf-dcs-set-link}); пусто, если таких нет или файл не разбирается как
   *     XML (о битой разметке говорит проверка)
   */
  public static List<Map<String, Object>> warnings(Path templateXml) throws IOException {
    String text = Files.readString(templateXml, StandardCharsets.UTF_8);
    try {
      return collect(text);
    } catch (RuntimeException e) {
      return new ArrayList<>();
    }
  }

  private static List<Map<String, Object>> collect(String text) {
    List<Map<String, Object>> out = new ArrayList<>();
    int index = -1;
    for (XmlLines.Node node : XmlLines.children(text, ROOT)) {
      if (!"dataSetLink".equals(node.name())) {
        continue;
      }
      index++;
      String parameter = node.leaves().get("parameter");
      if (parameter != null && !parameter.isBlank()) {
        continue;
      }
      XmlLines.Node flag = null;
      for (XmlLines.Node child : XmlLines.childrenAt(text, node.start())) {
        if (FLAG.equals(child.name())) {
          flag = child;
          break;
        }
      }
      if (flag == null) {
        continue;
      }
      boolean value = TRUE_FLAG.matcher(text.substring(flag.start(), flag.end())).matches();
      Map<String, Object> row = new LinkedHashMap<>();
      row.put("message", message(node, index, value));
      row.put("link", index);
      row.put("kind", KIND);
      out.add(row);
    }
    return out;
  }

  private static String message(XmlLines.Node link, int index, boolean value) {
    return "связь наборов " + link.leaves().getOrDefault("sourceDataSet", "?") + " → "
      + link.leaves().getOrDefault("destinationDataSet", "?") + " (номер " + index + "): флаг «Список параметров» "
      + "(parameterListAllowed=" + value + ") записан без параметра связи; при загрузке платформа его отбросит — "
      + "задайте parameter или снимите флаг";
  }
}
