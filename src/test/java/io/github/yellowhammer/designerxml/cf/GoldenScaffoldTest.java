/*
 * This file is a part of md-sparrow.
 *
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package io.github.yellowhammer.designerxml.cf;

import io.github.yellowhammer.designerxml.DesignerXml;
import io.github.yellowhammer.designerxml.SchemaVersion;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Scaffold по golden работает для ЛЮБОГО формата, у которого есть эталон (а не только 2.20/2.21):
 * эталоны забандлены в jar (golden/&lt;формат&gt;/…), объект параметризуется именем и читается JAXB-моделью версии.
 */
class GoldenScaffoldTest {

  @ParameterizedTest
  @EnumSource(SchemaVersion.class)
  void everyAddTypeHasGoldenInEveryVersion(SchemaVersion version) {
    for (MdObjectAddType type : MdObjectAddType.values()) {
      assertThat(GoldenScaffold.hasGolden(type, version))
        .as("эталон %s в формате %s", type, version)
        .isTrue();
    }
  }

  @ParameterizedTest
  @EnumSource(SchemaVersion.class)
  void generatesValidCatalogInEveryFormat(SchemaVersion version) throws Exception {
    String xml = GoldenScaffold.generateObject(MdObjectAddType.CATALOG, "ТестКаталог", version);
    assertThat(xml)
      .contains("version=\"" + version.metadataObjectVersionAttribute() + "\"")
      .contains("<Name>ТестКаталог</Name>");
    // читается JAXB-моделью этой версии (структурно валиден)
    DesignerXml.unmarshal(version, new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
  }

  @Test
  void scaffoldsDocumentInOldFormat() throws Exception {
    String xml = GoldenScaffold.generateObject(MdObjectAddType.DOCUMENT, "ТестДок", SchemaVersion.V2_10);
    assertThat(xml).contains("version=\"2.10\"").contains("<Name>ТестДок</Name>");
  }

  @ParameterizedTest
  @EnumSource(SchemaVersion.class)
  void everyVersionHasExternalGolden(SchemaVersion version) {
    assertThat(GoldenScaffold.hasExternalGolden(ExternalArtifactKind.REPORT, version)).isTrue();
    assertThat(GoldenScaffold.hasExternalGolden(ExternalArtifactKind.DATA_PROCESSOR, version)).isTrue();
  }

  @ParameterizedTest
  @EnumSource(SchemaVersion.class)
  void generatesNormalizedExternalReportInEveryFormat(SchemaVersion version) throws Exception {
    String xml = GoldenScaffold.generateExternalArtifact(ExternalArtifactKind.REPORT, "ТестВнешнийОтчет", version);
    assertThat(xml)
      .contains("version=\"" + version.metadataObjectVersionAttribute() + "\"")
      .contains("<Name>ТестВнешнийОтчет</Name>")
      .doesNotContain("standalone=\"yes\"")
      // ClassId платформы сохранён (не ремапнут), порядок InternalInfo — как у конфигуратора
      .contains("<xr:ClassId>e41aff26-25cf-4bb6-b6c1-3f478a75f374</xr:ClassId>");
    assertThat(xml.indexOf("<xr:ContainedObject")).isLessThan(xml.indexOf("<xr:GeneratedType"));
    DesignerXml.unmarshal(version, new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
  }

  @ParameterizedTest
  @EnumSource(SchemaVersion.class)
  void generatesNormalizedExternalDataProcessorInEveryFormat(SchemaVersion version) throws Exception {
    String xml =
      GoldenScaffold.generateExternalArtifact(ExternalArtifactKind.DATA_PROCESSOR, "ТестВнешняяОбработка", version);
    assertThat(xml)
      .contains("version=\"" + version.metadataObjectVersionAttribute() + "\"")
      .contains("<Name>ТестВнешняяОбработка</Name>")
      .doesNotContain("standalone=\"yes\"")
      .contains("<xr:ClassId>c3831ec8-d8d5-4f93-8a22-f9bfae07327f</xr:ClassId>");
    DesignerXml.unmarshal(version, new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
  }
}
