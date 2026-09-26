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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Лёгкое чтение атрибутов корневого {@code MetaDataObject} без полного парсинга.
 */
public final class MetaDataObjectHeadReader {

  private static final int HEAD_BYTES = 65536;
  private static final Pattern VERSION_RE =
    Pattern.compile("<(?:[\\w.-]+:)?MetaDataObject\\b[^>]*\\bversion\\s*=\\s*\"([^\"]+)\"");

  private MetaDataObjectHeadReader() {
  }

  /**
   * Значение {@code version} у корневого элемента (например {@code "2.21"}).
   */
  public static String readMetaDataObjectVersion(Path configurationXml) throws IOException {
    byte[] buf = Files.readAllBytes(configurationXml);
    int n = Math.min(buf.length, HEAD_BYTES);
    String head = new String(buf, 0, n, StandardCharsets.UTF_8);
    Matcher m = VERSION_RE.matcher(head);
    if (!m.find()) {
      throw new IOException("В начале Configuration.xml не найден атрибут version у MetaDataObject");
    }
    return m.group(1).trim();
  }

  /**
   * Преобразует {@code "2.20"} в имя enum для CLI ({@code "V2_20"}).
   */
  public static String toSchemaVersionFlag(String metaDataObjectVersion) {
    String v = metaDataObjectVersion.trim();
    if (!v.matches("\\d+(?:\\.\\d+)*")) {
      throw new IllegalArgumentException("Некорректное значение version у MetaDataObject: " + v);
    }
    return "V" + v.replace('.', '_');
  }
}
