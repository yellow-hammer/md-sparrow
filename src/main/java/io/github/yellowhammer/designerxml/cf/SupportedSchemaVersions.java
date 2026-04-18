/*
 * This file is a part of md-sparrow.
 *
 * SPDX-License-Identifier: LGPL-3.0-or-later
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
    for (SchemaVersion sv : SchemaVersion.values()) {
      if (sv.metadataObjectVersionAttribute().equals(v)) {
        return sv;
      }
    }
    throw new IOException(
      "Версия выгрузки " + v + " пока не поддерживается. Поддерживаются только 2.20 и 2.21.");
  }
}
