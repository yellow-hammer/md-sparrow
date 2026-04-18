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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ExternalArtifactAddGoldenTest {
  private static final String CONTAINED_OBJECT_TAG = "<xr:ContainedObject>";

  @TempDir
  Path workspace;

  @Test
  void createReportDeterministicAndNormalizedV220() throws Exception {
    Path rootA = workspace.resolve("erfA");
    Path rootB = workspace.resolve("erfB");
    String name = "_ВнешнийОтчетТест";
    Path xmlA = NewExternalArtifactXml.create(rootA, name, ExternalArtifactKind.REPORT, SchemaVersion.V2_20);
    Path xmlB = NewExternalArtifactXml.create(rootB, name, ExternalArtifactKind.REPORT, SchemaVersion.V2_20);
    String textA = Files.readString(xmlA, StandardCharsets.UTF_8);
    String textB = Files.readString(xmlB, StandardCharsets.UTF_8);
    assertThat(textA).isEqualTo(textB);
    assertThat(textA).contains("version=\"2.20\"");
    assertThat(textA).contains("xmlns:cfg=");
    assertThat(textA).contains("\n\t<");
    assertThat(textA).doesNotContain("standalone=\"yes\"");
    assertThat(textA).contains(CONTAINED_OBJECT_TAG);
    assertThat(textA).contains("<xr:ClassId>e41aff26-25cf-4bb6-b6c1-3f478a75f374</xr:ClassId>");
    assertThat(textA).contains("<Synonym/>");
    assertThat(textA.indexOf(CONTAINED_OBJECT_TAG))
      .isLessThan(textA.indexOf("<xr:GeneratedType"));
    DesignerXml.read(xmlA, SchemaVersion.V2_20);
  }

  @Test
  void createDataProcessorDeterministicAndReadableV221() throws Exception {
    Path rootA = workspace.resolve("epfA");
    Path rootB = workspace.resolve("epfB");
    String name = "_ВнешняяОбработкаТест";
    Path xmlA = NewExternalArtifactXml.create(rootA, name, ExternalArtifactKind.DATA_PROCESSOR, SchemaVersion.V2_21);
    Path xmlB = NewExternalArtifactXml.create(rootB, name, ExternalArtifactKind.DATA_PROCESSOR, SchemaVersion.V2_21);
    String textA = Files.readString(xmlA, StandardCharsets.UTF_8);
    String textB = Files.readString(xmlB, StandardCharsets.UTF_8);
    assertThat(textA).isEqualTo(textB);
    assertThat(textA).contains("version=\"2.21\"");
    assertThat(textA).contains("<ExternalDataProcessor");
    assertThat(textA).doesNotContain("standalone=\"yes\"");
    assertThat(textA).contains(CONTAINED_OBJECT_TAG);
    assertThat(textA).contains("<xr:ClassId>c3831ec8-d8d5-4f93-8a22-f9bfae07327f</xr:ClassId>");
    assertThat(textA).contains("<Synonym/>");
    assertThat(textA.indexOf(CONTAINED_OBJECT_TAG))
      .isLessThan(textA.indexOf("<xr:GeneratedType"));
    DesignerXml.read(xmlA, SchemaVersion.V2_21);
  }
}
