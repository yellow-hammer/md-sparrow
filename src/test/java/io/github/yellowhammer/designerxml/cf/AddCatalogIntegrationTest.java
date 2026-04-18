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

import io.github.yellowhammer.designerxml.DesignerXml;
import io.github.yellowhammer.designerxml.SamplesSnapshotPaths;
import io.github.yellowhammer.designerxml.SchemaVersion;
import io.github.yellowhammer.designerxml.Ssl31SubmodulePaths;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Assumptions;

class AddCatalogIntegrationTest {

  @TempDir
  Path workspace;

  @Test
  void addsCatalogXmlAndConfigurationReference() throws Exception {
    Path srcCf = workspace.resolve("src").resolve("cf");
    Files.createDirectories(srcCf);
    Path cfgSrc = Ssl31SubmodulePaths.configurationXml();
    Path cfg = srcCf.resolve(CfLayout.CONFIGURATION_XML);
    Files.copy(cfgSrc, cfg);
    String configurationHeadBefore = readConfigHead(cfg);

    String name = "_MdSparrowAddCatIt";
    String synonym = "Тест md-sparrow";

    AddCatalog.add(cfg, name, synonym, false, SchemaVersion.V2_20);

    Path catXml = CfLayout.catalogObjectXml(srcCf, name);
    assertThat(catXml).exists();
    DesignerXml.read(catXml, SchemaVersion.V2_20);

    String catText = Files.readString(catXml, java.nio.charset.StandardCharsets.UTF_8);
    Pattern catalogNameTag = Pattern.compile(
      "<(?:[A-Za-z0-9_]+:)?Name>" + Pattern.quote(name) + "</(?:[A-Za-z0-9_]+:)?Name>");
    assertThat(catalogNameTag.matcher(catText).find()).as("Properties/Name in catalog XML").isTrue();
    Pattern ruSynonymContent = Pattern.compile(
      "<(?:[A-Za-z0-9_]+:)?content>" + Pattern.quote(synonym) + "</(?:[A-Za-z0-9_]+:)?content>");
    assertThat(ruSynonymContent.matcher(catText).find())
      .as("ru synonym text in generated catalog XML")
      .isTrue();
    assertThat(catText).contains("xmlns=\"http://v8.1c.ru/8.3/MDClasses\"");
    assertThat(catText).doesNotContain("ns2:");

    String configurationText = Files.readString(cfg, java.nio.charset.StandardCharsets.UTF_8);
    Pattern catalogEntry =
      Pattern.compile("<(?:[A-Za-z0-9]+:)?Catalog>" + Pattern.quote(name) + "</(?:[A-Za-z0-9]+:)?Catalog>");
    var catM = catalogEntry.matcher(configurationText);
    assertThat(catM.find())
      .as("fragment <…Catalog>%s</…Catalog> in Configuration.xml", name)
      .isTrue();
    int idxNewCatalog = catM.start();
    int idxTask = configurationText.indexOf("<Task>");
    assertThat(idxNewCatalog).as("новый Catalog не в конце ChildObjects после других типов (например Task)").isLessThan(idxTask);

    int lineStart = configurationText.lastIndexOf('\n', idxNewCatalog) + 1;
    int nameTag = configurationText.indexOf("<Catalog>" + name, lineStart);
    assertThat(nameTag - lineStart)
      .as("перед <Catalog> нового справочника только отступ (табы/пробелы), без лишних символов")
      .isGreaterThan(0);
    String indent = configurationText.substring(lineStart, nameTag);
    assertThat(indent).as("отступ совпадает с последней строкой Catalog в исходной выгрузке").matches("^\\s+$");

    String configurationHeadAfter = readConfigHead(cfg);
    assertThat(configurationHeadAfter)
      .as("корень Configuration.xml не переписывается marshaller'ом (префиксы, version, xmlns)")
      .isEqualTo(configurationHeadBefore);
  }

  /** Пустой ru-синоним (как в 1c-platform-samples …/empty-full-objects/Catalogs/Справочник1.xml). */
  @Test
  void addsCatalogWithEmptySynonymLikeSnapshot() throws Exception {
    Path srcCf = workspace.resolve("src").resolve("cfEmptySyn");
    Files.createDirectories(srcCf);
    Path cfgSrc = Ssl31SubmodulePaths.configurationXml();
    Path cfg = srcCf.resolve(CfLayout.CONFIGURATION_XML);
    Files.copy(cfgSrc, cfg);

    String name = "_MdSparrowEmptySyn";
    AddCatalog.add(cfg, name, "долженИгнорироваться", true, SchemaVersion.V2_20);

    Path catXml = CfLayout.catalogObjectXml(srcCf, name);
    assertThat(catXml).exists();
    String catText = Files.readString(catXml, java.nio.charset.StandardCharsets.UTF_8);
    assertThat(catText).contains("<Name>" + name + "</Name>");
    assertThat(catText).doesNotContain("<content>" + name + "</content>");
  }

  @Test
  void addsCatalogKeepsChildObjectsEmptyAndCloserToSnapshotProfile() throws Exception {
    Assumptions.assumeTrue(SamplesSnapshotPaths.has220Snapshots(), "1c-platform-samples snapshots 2.20 are available");

    Path srcCf = workspace.resolve("src").resolve("cfProfile");
    Files.createDirectories(srcCf);
    Path cfgSrc = Ssl31SubmodulePaths.configurationXml();
    Path cfg = srcCf.resolve(CfLayout.CONFIGURATION_XML);
    Files.copy(cfgSrc, cfg);

    String name = "_MdSparrowProfile";
    AddCatalog.add(cfg, name, "", true, SchemaVersion.V2_20);

    Path catXml = CfLayout.catalogObjectXml(srcCf, name);
    String catText = Files.readString(catXml, java.nio.charset.StandardCharsets.UTF_8);
    assertThat(catText).contains("<Name>" + name + "</Name>");
    assertThat(catText).contains("<ChildObjects/>");
    assertThat(catText).doesNotContain("standalone=\"yes\"");
    assertThat(catText).doesNotContain("<StandardAttributes>");
    assertThat(catText).doesNotContain("<ObjectBelonging>");
    assertThat(catText).contains("<DefaultObjectForm");
    assertThat(catText).contains("xmlns:cfg=");
    assertThat(catText).contains("xmlns:style=");
    assertThat(catText).contains("xmlns:sys=");
    assertThat(catText).contains("xmlns:web=");
    assertThat(catText).contains("xmlns:win=");
    assertThat(catText).contains("xmlns:xen=");
    assertThat(catText).contains("xmlns:xs=");
    assertThat(catText).contains("xmlns:xsi=");

    Path snapshot = SamplesSnapshotPaths.emptyFullObjects220Root().resolve("Catalogs").resolve("Справочник1.xml");
    String snapshotText = Files.readString(snapshot, java.nio.charset.StandardCharsets.UTF_8);
    assertThat(snapshotText).contains("<ChildObjects>");
  }

  private static String readConfigHead(Path cfg) throws Exception {
    byte[] all = Files.readAllBytes(cfg);
    int n = Math.min(all.length, 2048);
    return new String(all, 0, n, java.nio.charset.StandardCharsets.UTF_8);
  }
}
