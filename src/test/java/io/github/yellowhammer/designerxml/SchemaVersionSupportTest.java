/*
 * This file is a part of md-sparrow.
 *
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package io.github.yellowhammer.designerxml;

import io.github.yellowhammer.designerxml.cf.SupportedSchemaVersions;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Регрессия на #106: метадерево падало на 2.17 («Поддерживаются только 2.20 и 2.21»). Проверяем, что
 * все форматы из {@code resources/namespace-forest} распознаются и их JAXB-модель загружается.
 */
class SchemaVersionSupportTest {

  @ParameterizedTest
  @EnumSource(SchemaVersion.class)
  void everyVersionHasLoadableJaxbContext(SchemaVersion version) throws Exception {
    assertThat(version.jaxbContext()).isNotNull();
  }

  @ParameterizedTest
  @EnumSource(SchemaVersion.class)
  void versionAttributeRoundTrips(SchemaVersion version) throws Exception {
    String attr = version.metadataObjectVersionAttribute();
    assertThat(SchemaVersion.byVersionAttribute(attr)).hasValue(version);
    assertThat(SupportedSchemaVersions.requireSupported(attr)).isEqualTo(version);
  }

  @Test
  void formatTwoSeventeenIsSupported() throws Exception {
    assertThat(SupportedSchemaVersions.requireSupported("2.17")).isEqualTo(SchemaVersion.V2_17);
  }
}
