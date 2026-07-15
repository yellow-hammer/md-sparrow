/*
 * This file is a part of md-sparrow.
 *
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package io.github.yellowhammer.designerxml.cf;

import io.github.yellowhammer.designerxml.DesignerXml;
import io.github.yellowhammer.designerxml.SchemaVersion;

import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Первый справочник после {@link EmptyCfScaffold#writeEmptyTree} — без готового {@code Catalogs/*.xml}.
 * Проверяем в каждом поддерживаемом формате, а не в паре выбранных.
 */
class AddCatalogFromEmptyCfTest {

  @TempDir
  Path workspace;

  @ParameterizedTest
  @EnumSource(SchemaVersion.class)
  void firstCatalogWithoutExistingCatalogsXml(SchemaVersion version) throws Exception {
    Path cf = workspace.resolve("cf-" + version.name());
    EmptyCfScaffold.writeEmptyTree(cf, CfLayout.DEFAULT_CONFIGURATION_NAME, null, null, null, version);
    Path cfg = cf.resolve(CfLayout.CONFIGURATION_XML);
    assertThat(cf.resolve(CfLayout.CATALOGS_DIR)).doesNotExist();
    assertThat(Files.readString(cfg)).contains("version=\"" + version.metadataObjectVersionAttribute() + "\"");

    String name = "_ПервыйИзПустой";
    MdObjectAdd.add(cfg, name, version, MdObjectAddType.CATALOG, "Первый", false);

    Path catXml = CfLayout.catalogObjectXml(cf, name);
    assertThat(catXml).exists();
    DesignerXml.read(catXml, version);
    assertThat(Files.readString(catXml, StandardCharsets.UTF_8)).contains("<Name>" + name + "</Name>");
  }
}
