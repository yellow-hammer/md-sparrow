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

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Объявления пространств имён управляемой формы, которые зависят от режима совместимости конфигурации.
 *
 * <p>Пространство схемы компоновки данных ({@code xmlns:dcssch}) платформа объявляет в корне
 * {@code Form} только в режиме совместимости 8.3.19 и выше. В режиме 8.3.18 и ниже и в
 * {@code DontUse} (его платформа записывает как 8.3.8) объявления нет, а содержимое формы то же
 * самое. Правило снято выгрузкой ibcmd 8.3.23 и 8.5.1 одной общей формы в конфигурации с разными
 * режимами; так же записаны все формы ssl31 (режим 8.3.24). Эталон cf-bare-objects снят с семени
 * в режиме 8.3.12, поэтому в нём этого объявления нет.
 */
final class FormNamespaceRules {

  private static final String LOGFORM_ROOT = "<Form xmlns=\"http://v8.1c.ru/8.3/xcf/logform\"";

  private static final String COMPOSITION_SCHEMA_PREFIX = "dcssch";

  private static final String COMPOSITION_SCHEMA =
    " xmlns:" + COMPOSITION_SCHEMA_PREFIX + "=\"http://v8.1c.ru/8.1/data-composition-system/schema\"";

  /** Первый режим совместимости, в котором форма объявляет {@code dcssch}. */
  private static final int[] COMPOSITION_SCHEMA_SINCE = {8, 3, 19};

  private static final Pattern VERSION_MODE = Pattern.compile("Version(\\d+(?:_\\d+)*)");

  private static final Pattern PREFIXED_DECLARATION = Pattern.compile(" xmlns:([A-Za-z0-9]+)=\"[^\"]*\"");

  private FormNamespaceRules() {
  }

  /**
   * Корень управляемой формы с объявлениями, как их пишет платформа в этом режиме совместимости.
   *
   * @param xml файл; не форма возвращается как есть
   * @param compatibilityMode {@code CompatibilityMode} конфигурации; без него файл не меняется
   * @return текст с объявлением {@code dcssch} или без него
   */
  static String forCompatibility(String xml, String compatibilityMode) {
    int root = xml.indexOf(LOGFORM_ROOT);
    if (root < 0 || compatibilityMode == null || compatibilityMode.isBlank()) {
      return xml;
    }
    int end = xml.indexOf('>', root);
    String head = xml.substring(root, end);
    boolean declared = head.contains(COMPOSITION_SCHEMA);
    boolean wanted = declaresCompositionSchema(compatibilityMode.trim());
    if (declared == wanted) {
      return xml;
    }
    String changed = wanted ? withCompositionSchema(head) : head.replace(COMPOSITION_SCHEMA, "");
    return xml.substring(0, root) + changed + xml.substring(end);
  }

  /**
   * Объявляет ли форма пространство схемы компоновки в этом режиме совместимости.
   *
   * @param compatibilityMode значение {@code CompatibilityMode}: {@code Version8_3_19}, {@code DontUse}
   * @return {@code true} с режима 8.3.19
   */
  static boolean declaresCompositionSchema(String compatibilityMode) {
    Matcher mode = VERSION_MODE.matcher(compatibilityMode);
    if (!mode.matches()) {
      return false;
    }
    String[] parts = mode.group(1).split("_");
    for (int i = 0; i < COMPOSITION_SCHEMA_SINCE.length; i++) {
      int part = i < parts.length ? Integer.parseInt(parts[i]) : 0;
      if (part != COMPOSITION_SCHEMA_SINCE[i]) {
        return part > COMPOSITION_SCHEMA_SINCE[i];
      }
    }
    return true;
  }

  /** Объявления с префиксом платформа пишет по алфавиту префиксов: {@code dcssch} - между соседями. */
  private static String withCompositionSchema(String head) {
    Matcher declaration = PREFIXED_DECLARATION.matcher(head);
    int at = -1;
    while (declaration.find()) {
      if (declaration.group(1).compareTo(COMPOSITION_SCHEMA_PREFIX) > 0) {
        at = declaration.start();
        break;
      }
      at = declaration.end();
    }
    if (at < 0) {
      at = LOGFORM_ROOT.length();
    }
    return head.substring(0, at) + COMPOSITION_SCHEMA + head.substring(at);
  }
}
