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

/**
 * Текст элемента в файлах 1С:EDT.
 *
 * Описания объектов и форм 1С:EDT пишет сериализацией EMF: в тексте элемента
 * экранированы амперсанд, «меньше» и кавычка, а «больше» остаётся как есть,
 * кроме конца секции {@code ]]>}, - так записаны все тексты ssl31-edt. Тот же
 * текст, записанный иначе, 1С:EDT при первом сохранении перепишет.
 */
final class EdtXmlText {

  private EdtXmlText() {
  }

  /**
   * Значение для текста элемента.
   *
   * @param value значение
   * @return значение с экранированием, как у 1С:EDT
   */
  static String escape(String value) {
    return value.replace("&", "&amp;").replace("<", "&lt;").replace("\"", "&quot;").replace("]]>", "]]&gt;");
  }
}
