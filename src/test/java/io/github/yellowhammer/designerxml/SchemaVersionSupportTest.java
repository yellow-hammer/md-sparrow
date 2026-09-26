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
