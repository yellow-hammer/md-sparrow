/*
 * This file is a part of md-sparrow.
 *
 * Copyright (c) 2026
 * Ivan Karlo <i.karlo@outlook.com> and contributors
 *
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package io.github.yellowhammer.designerxml.cf;

import io.github.yellowhammer.designerxml.SchemaVersion;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Добавляет одну строку {@code <Catalog>…</Catalog>} в {@code Configuration.xml}.
 *
 * @see ConfigurationChildObjectAppender
 */
final class ConfigurationCatalogAppender {

  private ConfigurationCatalogAppender() {
  }

  static void append(Path configurationXml, String catalogName, @SuppressWarnings("unused") SchemaVersion version)
    throws IOException {
    ConfigurationChildObjectAppender.append(configurationXml, "Catalog", catalogName);
  }
}
