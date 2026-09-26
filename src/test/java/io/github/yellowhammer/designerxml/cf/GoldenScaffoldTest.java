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
import io.github.yellowhammer.designerxml.SchemaVersion;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Scaffold по golden работает для ЛЮБОГО формата (а не только 2.20/2.21): в jar канонический набор
 * эталонов (golden/cf/…), файл формата - его проекция, объект параметризуется именем и читается
 * JAXB-моделью версии.
 */
class GoldenScaffoldTest {

  @Test
  void jarCarriesNewestPlatformSnapshotAsCanonicalSet() {
    assertThat(GoldenScaffold.canonicalVersion()).isEqualTo(GoldenSnapshots.canonical());
  }

  /** Эталон вида есть в каждом формате, где вид существует; в более старом формате его нет. */
  @ParameterizedTest
  @EnumSource(SchemaVersion.class)
  void everyAddTypeHasGoldenWhereKindExists(SchemaVersion version) {
    for (MdObjectAddType type : MdObjectAddType.values()) {
      assertThat(GoldenScaffold.hasGolden(type, version))
        .as("эталон %s в формате %s", type, version)
        .isEqualTo(type.existsIn(version));
    }
    for (MdObjectAddType type : MdObjectAddType.values()) {
      assertThat(GoldenScaffold.hasGolden(type, GoldenScaffold.canonicalVersion()))
        .as("эталон %s в каноническом наборе", type)
        .isTrue();
    }
  }

  /** Вид, появившийся позже формата, не создаётся: отказ называет формат, где он появился. */
  @ParameterizedTest
  @EnumSource(SchemaVersion.class)
  void kindAbsentInFormatIsRefused(SchemaVersion version) {
    for (MdObjectAddType type : MdObjectAddType.values()) {
      if (type.existsIn(version)) {
        continue;
      }
      SchemaVersion since = null;
      for (SchemaVersion newer : SchemaVersion.values()) {
        if (since == null && type.existsIn(newer)) {
          since = newer;
        }
      }
      assertThat(since).as("формат, где появился %s", type).isNotNull().isGreaterThan(version);
      assertThatThrownBy(() -> type.requireIn(version))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("вид " + type.configurationXmlTag() + " появился в формате "
          + since.metadataObjectVersionAttribute());
      assertThatThrownBy(() -> GoldenScaffold.generateObjectFiles(type, "Тест", version))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("появился в формате " + since.metadataObjectVersionAttribute());
    }
  }

  /** Файлы нового объекта - все файлы прототипа: описание первым, под новым именем. */
  @ParameterizedTest
  @EnumSource(SchemaVersion.class)
  void generatesEveryPrototypeFile(SchemaVersion version) throws Exception {
    for (MdObjectAddType type : MdObjectAddType.values()) {
      if (!type.existsIn(version)) {
        continue;
      }
      String proto = GoldenScaffold.protoName(type);
      Map<String, String> files = GoldenScaffold.generateObjectFiles(type, "Тест", version);
      assertThat(files.keySet()).as("%s в формате %s", type, version)
        .first().isEqualTo(type.cfSubdir() + "/Тест.xml");
      assertThat(files.keySet().stream().map(path -> path.replace("/Тест", "/" + proto)).toList())
        .as("%s в формате %s", type, version)
        .containsExactlyElementsOf(GoldenScaffold.prototypeFiles(type));
      assertThat(files.get(type.cfSubdir() + "/Тест.xml")).contains("<Name>Тест</Name>");
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

  @ParameterizedTest
  @EnumSource(SchemaVersion.class)
  void retargetsEveryFormatToConfigurationLanguage(SchemaVersion version) throws Exception {
    String golden = GoldenScaffold.generateObject(MdObjectAddType.CATALOG, "Waren", version);

    String retargeted = LocalStringElement.retarget(golden, "de");

    assertThat(LocalStringElement.items(retargeted))
      .as("строки эталона %s", version)
      .isNotEmpty()
      .allSatisfy(item -> assertThat(item.lang()).isEqualTo("de"));
    assertThat(LocalStringElement.items(retargeted)).hasSameSizeAs(LocalStringElement.items(golden));
    DesignerXml.unmarshal(version, new ByteArrayInputStream(retargeted.getBytes(StandardCharsets.UTF_8)));
  }

  @Test
  void scaffoldsDocumentInOldFormat() throws Exception {
    String xml = GoldenScaffold.generateObject(MdObjectAddType.DOCUMENT, "ТестДок", SchemaVersion.V2_10);
    assertThat(xml).contains("version=\"2.10\"").contains("<Name>ТестДок</Name>");
  }

  @ParameterizedTest
  @EnumSource(SchemaVersion.class)
  void generatesExternalReportInEveryFormat(SchemaVersion version) throws Exception {
    String xml = GoldenScaffold.generateExternalArtifact(ExternalArtifactKind.REPORT, "ТестВнешнийОтчет", version);
    assertThat(xml)
      .contains("version=\"" + version.metadataObjectVersionAttribute() + "\"")
      .contains("<Name>ТестВнешнийОтчет</Name>")
      // ClassId платформы сохранён (не ремапнут), порядок InternalInfo - как у конфигуратора
      .contains("<xr:ClassId>e41aff26-25cf-4bb6-b6c1-3f478a75f374</xr:ClassId>");
    assertThat(xml.indexOf("<xr:ContainedObject")).isLessThan(xml.indexOf("<xr:GeneratedType"));
    DesignerXml.unmarshal(version, new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
  }

  @ParameterizedTest
  @EnumSource(SchemaVersion.class)
  void generatesExternalDataProcessorInEveryFormat(SchemaVersion version) throws Exception {
    String xml =
      GoldenScaffold.generateExternalArtifact(ExternalArtifactKind.DATA_PROCESSOR, "ТестВнешняяОбработка", version);
    assertThat(xml)
      .contains("version=\"" + version.metadataObjectVersionAttribute() + "\"")
      .contains("<Name>ТестВнешняяОбработка</Name>")
      .contains("<xr:ClassId>c3831ec8-d8d5-4f93-8a22-f9bfae07327f</xr:ClassId>");
    DesignerXml.unmarshal(version, new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
  }
}
