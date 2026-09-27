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

import java.util.OptionalInt;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Режим совместимости {@code VersionX_Y_Z} против версии платформы.
 *
 * <p>Значение приходит из {@code Configuration.xml} пользователя, поэтому части сравниваются как
 * строки цифр: число любой длины не переполняет {@code int} и не роняет операцию.
 */
final class CompatibilityModes {

  private static final Pattern VERSION_MODE = Pattern.compile("Version(\\d+(?:_\\d+)*)");

  private CompatibilityModes() {
  }

  /**
   * Сравнивает режим совместимости с версией платформы по частям.
   *
   * <p>Если все части версии совпали, режим с лишними частями считается старше:
   * {@code Version8_3_13_1} старше 8.3.13.
   *
   * @param compatibilityMode значение свойства, например {@code Version8_3_19}
   * @param version версия платформы по частям, например {@code 8, 3, 19}
   * @return отрицательное, ноль или положительное число, как у {@link Comparable#compareTo};
   *   пусто, если значение - не версия ({@code DontUse} и прочее)
   */
  static OptionalInt compare(String compatibilityMode, int... version) {
    Matcher mode = VERSION_MODE.matcher(compatibilityMode.trim());
    if (!mode.matches()) {
      return OptionalInt.empty();
    }
    String[] parts = mode.group(1).split("_");
    for (int i = 0; i < version.length; i++) {
      int order = i < parts.length ? compareDigits(parts[i], version[i]) : Integer.compare(0, version[i]);
      if (order != 0) {
        return OptionalInt.of(order);
      }
    }
    return OptionalInt.of(parts.length > version.length ? 1 : 0);
  }

  /** Строка цифр против числа: без разбора в {@code int}, ведущие нули не в счёт. */
  private static int compareDigits(String digits, int value) {
    String number = digits.replaceFirst("^0+(?=\\d)", "");
    String other = Integer.toString(value);
    if (number.length() != other.length()) {
      return Integer.compare(number.length(), other.length());
    }
    return Integer.signum(number.compareTo(other));
  }
}
