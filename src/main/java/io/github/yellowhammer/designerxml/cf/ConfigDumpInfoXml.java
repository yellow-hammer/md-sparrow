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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/**
 * {@link CfLayout#CONFIG_DUMP_INFO_XML} — служебный файл выгрузки; XSD в проекте не генерируется,
 * текст совпадает с типовой пустой выгрузкой конфигуратора.
 */
public final class ConfigDumpInfoXml {

  private ConfigDumpInfoXml() {
  }

  /** Записывает иерархический dump info с версией, совпадающей с {@link SchemaVersion}. */
  public static void write(Path cfRoot, SchemaVersion schemaVersion) throws IOException {
    Objects.requireNonNull(cfRoot, "cfRoot");
    Objects.requireNonNull(schemaVersion, "schemaVersion");
    String ver = schemaVersion.metadataObjectVersionAttribute();
    String xml =
      "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
        + "<ConfigDumpInfo xmlns=\"http://v8.1c.ru/8.3/xcf/dumpinfo\" xmlns:xen=\"http://v8.1c.ru/8.3/xcf/enums\" "
        + "xmlns:xs=\"http://www.w3.org/2001/XMLSchema\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" "
        + "format=\"Hierarchical\" version=\""
        + ver
        + "\">\n"
        + "\t<ConfigVersions/>\n"
        + "</ConfigDumpInfo>\n";
    Path out = cfRoot.resolve(CfLayout.CONFIG_DUMP_INFO_XML);
    Files.createDirectories(cfRoot);
    Files.writeString(out, xml, StandardCharsets.UTF_8);
  }
}
