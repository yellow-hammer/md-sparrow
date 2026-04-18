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

final class CatalogNameConstraints {

  private CatalogNameConstraints() {
  }

  static void check(String catalogName) {
    if (catalogName == null || catalogName.isEmpty()) {
      throw new IllegalArgumentException("catalog name required");
    }
    char c0 = catalogName.charAt(0);
    if (c0 != '_' && !Character.isLetter(c0)) {
      throw new IllegalArgumentException(
        "catalog name must start with a letter or underscore: " + catalogName);
    }
    for (int i = 1; i < catalogName.length(); i++) {
      char c = catalogName.charAt(i);
      if (c == '_' || Character.isLetterOrDigit(c)) {
        continue;
      }
      throw new IllegalArgumentException(
        "catalog name has invalid character at " + i + ": " + catalogName);
    }
  }
}
