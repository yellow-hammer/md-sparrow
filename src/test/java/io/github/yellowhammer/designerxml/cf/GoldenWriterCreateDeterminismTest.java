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
import io.github.yellowhammer.designerxml.XmlValidator;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Пустая выгрузка одинакова от запуска к запуску, переписывается идемпотентно, читается моделью
 * своей версии и проходит проверку по XSD — в каждом поддерживаемом формате.
 */
class GoldenWriterCreateDeterminismTest {

  @TempDir
  Path workspace;

  @ParameterizedTest
  @EnumSource(SchemaVersion.class)
  void initEmptyCfIsDeterministicAndIdempotent(SchemaVersion version) throws Exception {
    Path cfA = workspace.resolve("cfA-" + version.name());
    Path cfB = workspace.resolve("cfB-" + version.name());

    EmptyCfScaffold.writeEmptyTree(cfA, CfLayout.DEFAULT_CONFIGURATION_NAME, null, null, null, version);
    EmptyCfScaffold.writeEmptyTree(cfB, CfLayout.DEFAULT_CONFIGURATION_NAME, null, null, null, version);

    Path cfgA = cfA.resolve(CfLayout.CONFIGURATION_XML);
    Path cfgB = cfB.resolve(CfLayout.CONFIGURATION_XML);
    String first = Files.readString(cfgA);
    String second = Files.readString(cfgB);
    assertThat(first).isEqualTo(second);

    EmptyCfScaffold.writeEmptyTree(cfA, CfLayout.DEFAULT_CONFIGURATION_NAME, null, null, null, version);
    String third = Files.readString(cfgA);
    assertThat(third).isEqualTo(first);

    DesignerXml.read(cfgA, version);
    try (Stream<Path> written = Files.walk(cfA)) {
      for (Path xml : written.filter(p -> p.toString().endsWith(".xml")).toList()) {
        XmlValidator.validate(xml, version, Path.of(System.getProperty("xsd.root")));
      }
    }
  }
}
