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
package io.github.yellowhammer.designerxml.cli;

import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Формат исходников, которые создаёт команда.
 *
 * Существующие исходники формат выдают сами - по файлу ({@code .mdo} у 1С:EDT),
 * а у новых его называет вызывающий.
 */
enum SourceFormat {

  /** Выгрузка конфигуратора: {@code Configuration.xml}. */
  DESIGNER,

  /** Проект 1С:EDT: {@code src/Configuration/Configuration.mdo}. */
  EDT;

  /**
   * Формат по имени из CLI или параметров: {@code designer}, {@code edt}, регистр не важен.
   *
   * @param name имя формата; пустое - выгрузка конфигуратора
   * @return формат
   * @throws IllegalArgumentException если формат неизвестен
   */
  static SourceFormat fromCliName(String name) {
    if (name == null || name.isBlank()) {
      return DESIGNER;
    }
    for (SourceFormat format : values()) {
      if (format.cliName().equals(name.trim().toLowerCase(Locale.ROOT))) {
        return format;
      }
    }
    throw new IllegalArgumentException("неизвестный формат исходников: " + name.trim() + " (допустимы: "
      + Arrays.stream(values()).map(SourceFormat::cliName).collect(Collectors.joining(", ")) + ")");
  }

  /** Имя формата в CLI и параметрах. */
  String cliName() {
    return name().toLowerCase(Locale.ROOT);
  }
}
