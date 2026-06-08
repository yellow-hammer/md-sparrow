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

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Подмена UUID в XML: каждое уникальное значение в тексте получает свой новый UUID.
 * Содержимое {@code <xr:ClassId>} сохраняется без изменений — это фиксированный идентификатор
 * класса метаданных платформы (внешний отчёт/обработка, контейнеры конфигурации), а не
 * объектно-зависимый UUID; его подмена сделала бы выгрузку нечитаемой платформой.
 */
final class DistinctUuidRewrite {

  private static final Pattern UUID_TOKEN = Pattern.compile(
    "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");

  /** Закрывающий «>» элемента ClassId (с любым префиксом NS) непосредственно перед значением. */
  private static final String CLASS_ID_OPEN_END = "ClassId>";

  private DistinctUuidRewrite() {
  }

  static String remap(String xml) {
    return remap(xml, oldU -> java.util.UUID.randomUUID().toString());
  }

  static String remapDeterministic(String xml, String seed) {
    return remap(xml, oldU -> GoldenUuid.from(seed, oldU));
  }

  private static String remap(String xml, java.util.function.Function<String, String> uuidFactory) {
    Map<String, String> mapping = new LinkedHashMap<>();
    Matcher m = UUID_TOKEN.matcher(xml);
    StringBuilder sb = new StringBuilder();
    int last = 0;
    while (m.find()) {
      sb.append(xml, last, m.start());
      String oldU = m.group();
      String value = isClassId(xml, m.start()) ? oldU : mapping.computeIfAbsent(oldU, uuidFactory);
      sb.append(value);
      last = m.end();
    }
    sb.append(xml.substring(last));
    return sb.toString();
  }

  private static boolean isClassId(String xml, int valueStart) {
    int s = valueStart - CLASS_ID_OPEN_END.length();
    return s >= 0 && xml.regionMatches(s, CLASS_ID_OPEN_END, 0, CLASS_ID_OPEN_END.length());
  }
}
