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
import io.github.yellowhammer.designerxml.SchemaVersion;
import io.github.yellowhammer.designerxml.XmlValidator;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class GoldenWriterCreateDeterminismTest {

  @TempDir
  Path workspace;

  @Test
  void initEmptyCfIsDeterministicAndIdempotentV220() throws Exception {
    Path cfA = workspace.resolve("cfA");
    Path cfB = workspace.resolve("cfB");

    EmptyCfScaffold.writeEmptyTree(
      cfA, CfLayout.DEFAULT_CONFIGURATION_NAME, null, null, null, SchemaVersion.V2_20);
    EmptyCfScaffold.writeEmptyTree(
      cfB, CfLayout.DEFAULT_CONFIGURATION_NAME, null, null, null, SchemaVersion.V2_20);

    Path cfgA = cfA.resolve(CfLayout.CONFIGURATION_XML);
    Path cfgB = cfB.resolve(CfLayout.CONFIGURATION_XML);
    String first = Files.readString(cfgA);
    String second = Files.readString(cfgB);
    assertThat(first).isEqualTo(second);

    EmptyCfScaffold.writeEmptyTree(
      cfA, CfLayout.DEFAULT_CONFIGURATION_NAME, null, null, null, SchemaVersion.V2_20);
    String third = Files.readString(cfgA);
    assertThat(third).isEqualTo(first);

    DesignerXml.read(cfgA, SchemaVersion.V2_20);
    Path xsdRoot = Path.of(System.getProperty("xsd.root"));
    XmlValidator.validate(cfgA, SchemaVersion.V2_20, xsdRoot);
  }
}
