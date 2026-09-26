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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Инициализация каталога пустой выгрузки конфигурации из эталона (golden) нужной версии — для любого формата
 * (см. {@link GoldenScaffold}). Configuration.xml = эталон с обрезанным до Языка ChildObjects + параметризация;
 * Languages/Русский.xml — из эталона.
 */
public final class EmptyCfScaffold {

  private EmptyCfScaffold() {
  }

  public static void writeEmptyTree(
    Path targetCfRoot,
    String configurationName,
    String synonym,
    String vendor,
    String appVersion,
    SchemaVersion version) throws IOException {
    Objects.requireNonNull(targetCfRoot, "targetCfRoot");
    CatalogNameConstraints.check(configurationName);

    CfTreeDelete.deleteAllContents(targetCfRoot);
    Files.createDirectories(targetCfRoot);

    Path langDir = targetCfRoot.resolve(CfLayout.LANGUAGES_DIR);
    Files.createDirectories(langDir);
    Files.writeString(
      langDir.resolve(CfLayout.RUSSIAN_LANGUAGE_NAME + ".xml"),
      GoldenScaffold.generateRussianLanguage(version),
      StandardCharsets.UTF_8);

    String cfg = GoldenScaffold.generateEmptyConfiguration(configurationName, version);
    cfg = applyOptions(cfg, synonym, vendor, appVersion);
    Files.writeString(targetCfRoot.resolve(CfLayout.CONFIGURATION_XML), cfg, StandardCharsets.UTF_8);
  }

  private static String applyOptions(String xml, String synonym, String vendor, String appVersion) {
    if (vendor != null && !vendor.isEmpty()) {
      xml = ScaffoldPropertyEdit.setLeaf(xml, "Vendor", vendor);
    }
    if (appVersion != null && !appVersion.isEmpty()) {
      xml = ScaffoldPropertyEdit.setLeaf(xml, "Version", appVersion);
    }
    if (synonym != null && !synonym.isEmpty()) {
      xml = ScaffoldPropertyEdit.setSynonym(xml, synonym, ConfigurationLanguage.FALLBACK);
    }
    return xml;
  }

}
