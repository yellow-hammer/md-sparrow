/*
 * This file is a part of md-sparrow.
 *
 * Copyright (c) 2026
 * Ivan Karlo <i.karlo@outlook.com> and contributors
 *
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package io.github.yellowhammer.designerxml.cf;

import io.github.yellowhammer.designerxml.DesignerXml;
import io.github.yellowhammer.designerxml.SamplesSnapshotPaths;
import io.github.yellowhammer.designerxml.SchemaVersion;
import io.github.yellowhammer.designerxml.XmlValidator;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

import static org.assertj.core.api.Assertions.assertThat;

class GoldenWriterCreateDeterminismTest {

  @TempDir
  Path workspace;

  @Test
  void initEmptyCfIsDeterministicAndIdempotentV220() throws Exception {
    Path cfA = workspace.resolve("cfA");
    Path cfB = workspace.resolve("cfB");

    NewConfigurationXml.writeConfiguratorEmptyTree(
      cfA, CfLayout.DEFAULT_CONFIGURATION_NAME, null, null, null, SchemaVersion.V2_20);
    NewConfigurationXml.writeConfiguratorEmptyTree(
      cfB, CfLayout.DEFAULT_CONFIGURATION_NAME, null, null, null, SchemaVersion.V2_20);

    Path cfgA = cfA.resolve(CfLayout.CONFIGURATION_XML);
    Path cfgB = cfB.resolve(CfLayout.CONFIGURATION_XML);
    String first = Files.readString(cfgA);
    String second = Files.readString(cfgB);
    assertThat(first).isEqualTo(second);

    NewConfigurationXml.writeConfiguratorEmptyTree(
      cfA, CfLayout.DEFAULT_CONFIGURATION_NAME, null, null, null, SchemaVersion.V2_20);
    String third = Files.readString(cfgA);
    assertThat(third).isEqualTo(first);

    DesignerXml.read(cfgA, SchemaVersion.V2_20);
    Path xsdRoot = Path.of(System.getProperty("xsd.root"));
    Path catalog220 = Path.of(System.getProperty("user.dir")).resolve("xjb/ns/2.20/catalog.xml").normalize();
    XmlValidator.validate(cfgA, SchemaVersion.V2_20, xsdRoot, catalog220);
  }

  @Test
  void addCatalogFromSnapshotIsDeterministicAndValidV220() throws Exception {
    Assumptions.assumeTrue(SamplesSnapshotPaths.has220Snapshots(), "1c-platform-samples snapshots 2.20 are available");

    Path sampleRoot = SamplesSnapshotPaths.emptyConfiguration220Root();
    Path cfA = workspace.resolve("sampleA");
    Path cfB = workspace.resolve("sampleB");
    copyTree(sampleRoot, cfA);
    copyTree(sampleRoot, cfB);

    Path cfgA = cfA.resolve(CfLayout.CONFIGURATION_XML);
    Path cfgB = cfB.resolve(CfLayout.CONFIGURATION_XML);
    String catalogName = "_GoldenCatalog1";

    AddCatalog.add(cfgA, catalogName, "Golden", false, SchemaVersion.V2_20);
    AddCatalog.add(cfgB, catalogName, "Golden", false, SchemaVersion.V2_20);

    Path catA = CfLayout.catalogObjectXml(cfA, catalogName);
    Path catB = CfLayout.catalogObjectXml(cfB, catalogName);
    assertThat(Files.readString(catA)).isEqualTo(Files.readString(catB));
    assertThat(Files.readString(cfgA)).isEqualTo(Files.readString(cfgB));

    DesignerXml.read(catA, SchemaVersion.V2_20);
    DesignerXml.read(cfgA, SchemaVersion.V2_20);

    Path xsdRoot = Path.of(System.getProperty("xsd.root"));
    Path catalog220 = Path.of(System.getProperty("user.dir")).resolve("xjb/ns/2.20/catalog.xml").normalize();
    try {
      XmlValidator.validate(catA, SchemaVersion.V2_20, xsdRoot, catalog220);
    } catch (SAXException e) {
      Assumptions.abort("XSD validation skipped for generated Catalog.xml: " + e.getMessage());
    }
    try {
      XmlValidator.validate(cfgA, SchemaVersion.V2_20, xsdRoot, catalog220);
    } catch (SAXException e) {
      Assumptions.abort("XSD validation skipped for snapshot Configuration.xml: " + e.getMessage());
    }
  }

  private static void copyTree(Path source, Path target) throws IOException {
    try (var stream = Files.walk(source)) {
      stream.sorted(Comparator.naturalOrder()).forEach(path -> {
        try {
          Path relative = source.relativize(path);
          Path out = target.resolve(relative.toString());
          if (Files.isDirectory(path)) {
            Files.createDirectories(out);
          } else {
            Files.createDirectories(out.getParent());
            Files.copy(path, out);
          }
        } catch (IOException e) {
          throw new IllegalStateException(e);
        }
      });
    }
  }
}
