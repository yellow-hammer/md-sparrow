/*
 * This file is a part of md-sparrow.
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
    String synonymRu,
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
    cfg = applyOptions(cfg, synonymRu, vendor, appVersion);
    Files.writeString(targetCfRoot.resolve(CfLayout.CONFIGURATION_XML), cfg, StandardCharsets.UTF_8);
  }

  private static String applyOptions(String xml, String synonymRu, String vendor, String appVersion) {
    if (vendor != null && !vendor.isEmpty()) {
      xml = ScaffoldPropertyEdit.setLeaf(xml, "Vendor", vendor);
    }
    if (appVersion != null && !appVersion.isEmpty()) {
      xml = ScaffoldPropertyEdit.setLeaf(xml, "Version", appVersion);
    }
    if (synonymRu != null && !synonymRu.isEmpty()) {
      xml = ScaffoldPropertyEdit.setSynonymRu(xml, synonymRu);
    }
    return xml;
  }

}
