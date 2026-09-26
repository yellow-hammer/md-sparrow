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

import java.io.IOException;

/**
 * Соответствие атрибута {@code MetaDataObject/@version} поддерживаемому {@link SchemaVersion}.
 */
public final class SupportedSchemaVersions {

  private SupportedSchemaVersions() {
  }

  /**
   * @param metaDataObjectVersion значение из XML (например {@code "2.21"})
   * @throws IOException если версия не поддерживается md-sparrow
   */
  public static SchemaVersion requireSupported(String metaDataObjectVersion) throws IOException {
    String v = metaDataObjectVersion == null ? "" : metaDataObjectVersion.trim();
    return SchemaVersion.byVersionAttribute(v).orElseThrow(() -> {
      StringBuilder supported = new StringBuilder();
      for (SchemaVersion sv : SchemaVersion.values()) {
        if (supported.length() > 0) {
          supported.append(", ");
        }
        supported.append(sv.metadataObjectVersionAttribute());
      }
      return new IOException(
        "Версия выгрузки " + v + " пока не поддерживается. Поддерживаются: " + supported + ".");
    });
  }
}
