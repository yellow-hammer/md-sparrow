/*
 * This file is a part of md-sparrow.
 *
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package io.github.yellowhammer.designerxml.cf;

import io.github.yellowhammer.designerxml.DesignerXml;
import io.github.yellowhammer.designerxml.SchemaVersion;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/** Первый справочник после {@link NewConfigurationXml#writeConfiguratorEmptyTree} — без готового {@code Catalogs/*.xml}. */
class AddCatalogFromEmptyCfTest {

  @TempDir
  Path workspace;

  @Test
  void firstCatalogV220WithoutExistingCatalogsXml() throws Exception {
    Path cf = workspace.resolve("cf");
    NewConfigurationXml.writeConfiguratorEmptyTree(
      cf, CfLayout.DEFAULT_CONFIGURATION_NAME, null, null, null, SchemaVersion.V2_20);
    Path cfg = cf.resolve(CfLayout.CONFIGURATION_XML);
    assertThat(cf.resolve(CfLayout.CATALOGS_DIR)).doesNotExist();
    assertThat(Files.readString(cfg)).contains("formatVersion=\"2.20\"");

    String name = "_ПервыйИзПустой";
    MdObjectAdd.add(cfg, name, SchemaVersion.V2_20, MdObjectAddType.CATALOG, "Первый", false);

    Path catXml = CfLayout.catalogObjectXml(cf, name);
    assertThat(catXml).exists();
    DesignerXml.read(catXml, SchemaVersion.V2_20);
    String text = Files.readString(catXml, java.nio.charset.StandardCharsets.UTF_8);
    assertThat(text).contains("<Name>" + name + "</Name>");
  }

  @Test
  void firstCatalogV221WithoutExistingCatalogsXml() throws Exception {
    Path cf = workspace.resolve("cf221");
    NewConfigurationXml.writeConfiguratorEmptyTree(
      cf, CfLayout.DEFAULT_CONFIGURATION_NAME, null, null, null, SchemaVersion.V2_21);
    Path cfg = cf.resolve(CfLayout.CONFIGURATION_XML);
    assertThat(Files.readString(cfg)).contains("formatVersion=\"2.21\"");

    String name = "_Первый221";
    MdObjectAdd.add(cfg, name, SchemaVersion.V2_21, MdObjectAddType.CATALOG, "Первый", false);

    Path catXml = CfLayout.catalogObjectXml(cf, name);
    assertThat(catXml).exists();
    DesignerXml.read(catXml, SchemaVersion.V2_21);
  }
}
