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

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Пустая выгрузка одинакова от запуска к запуску, переписывается идемпотентно и читается моделью
 * своей версии — в каждом поддерживаемом формате.
 */
class GoldenWriterCreateDeterminismTest {

  /**
   * Форматы, где XSD из {@code namespace-forest} расходятся с выводом самой платформы, поэтому
   * эталон не проходит проверку по схеме (#6):
   * <ul>
   *   <li>2.10 — {@code app:permission} платформа пишет без {@code app:description};</li>
   *   <li>2.21 — {@code ConfigurationExtensionCompatibilityMode} имеет значение {@code Version8_5_1},
   *       которого нет в перечислении схемы.</li>
   * </ul>
   */
  private static final Set<SchemaVersion> XSD_MISMATCH_WITH_PLATFORM =
    EnumSet.of(SchemaVersion.V2_10, SchemaVersion.V2_21);

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
    if (!XSD_MISMATCH_WITH_PLATFORM.contains(version)) {
      XmlValidator.validate(cfgA, version, Path.of(System.getProperty("xsd.root")));
    }
  }
}
