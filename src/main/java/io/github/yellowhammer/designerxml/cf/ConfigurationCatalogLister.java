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

import io.github.yellowhammer.designerxml.SchemaVersion;

import jakarta.xml.bind.JAXBException;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * Reads catalog names from {@code Configuration.xml} child objects.
 */
public final class ConfigurationCatalogLister {

  private ConfigurationCatalogLister() {
  }

  /**
   * Lists catalog object names declared in {@code Configuration/ChildObjects/Catalog}.
   *
   * @param configurationXml path to {@code Configuration.xml}
   * @param version          schema version
   * @return sorted names (defensive copy)
   */
  public static List<String> listCatalogNames(Path configurationXml, SchemaVersion version)
    throws JAXBException, IOException {
    return ConfigurationChildObjectLister.listNames(configurationXml, version, "Catalog");
  }

  /**
   * JSON array of strings (UTF-8 one line), for CLI / tooling.
   *
   * @param names ordered list (typically already sorted)
   * @return {@code ["a","b"]}
   */
  public static String toJsonArray(List<String> names) {
    StringBuilder b = new StringBuilder();
    b.append('[');
    for (int i = 0; i < names.size(); i++) {
      if (i > 0) {
        b.append(',');
      }
      b.append('"').append(jsonEscape(names.get(i))).append('"');
    }
    b.append(']');
    return b.toString();
  }

  static String jsonEscape(String s) {
    StringBuilder b = new StringBuilder();
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      switch (c) {
        case '\\', '"' -> b.append('\\').append(c);
        case '\n' -> b.append("\\n");
        case '\r' -> b.append("\\r");
        case '\t' -> b.append("\\t");
        default -> {
          if (c < 0x20) {
            b.append(String.format("\\u%04x", (int) c));
          } else {
            b.append(c);
          }
        }
      }
    }
    return b.toString();
  }
}
