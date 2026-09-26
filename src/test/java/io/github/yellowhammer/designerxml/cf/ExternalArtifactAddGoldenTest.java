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

import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * external-artifact-add пишет то же, что выгрузила бы платформа: в форматах, где внешние объекты
 * сняты платформой ({@code snapshots/<формат>/external-files/empty}), созданный файл совпадает
 * с эталоном байт в байт после замены имени на имя прототипа и UUID на метки ({@code xr:ClassId}
 * сравнивается как есть). В остальных форматах файл читается моделью формата и проходит XSD.
 */
class ExternalArtifactAddGoldenTest {

  @TempDir
  Path workspace;

  @ParameterizedTest
  @EnumSource(SchemaVersion.class)
  void совпадаетСВыгрузкойПлатформы(SchemaVersion version) throws Exception {
    List<String> platformFiles = GoldenSnapshots.files(version, GoldenSnapshots.EXTERNAL);
    if (version == GoldenSnapshots.canonical()) {
      assertThat(platformFiles).as("эталоны внешних объектов канонического формата").isNotEmpty();
    }
    SoftAssertions softly = new SoftAssertions();
    for (ExternalArtifactKind kind : ExternalArtifactKind.values()) {
      String proto = GoldenScaffold.externalProtoName(kind);
      String name = "Нов" + proto;
      Path xml = NewExternalArtifactXml.create(workspace.resolve(version.name()), name, kind, version);

      String text = GoldenSnapshots.read(xml);
      softly.assertThat(text).as("BOM %s в формате %s", kind, version).startsWith("﻿");
      softly.assertThat(text.replace("\r\n", "")).as("CRLF %s в формате %s", kind, version).doesNotContain("\n");
      DesignerXml.read(xml, version);
      XmlValidator.validate(xml, version, Path.of(System.getProperty("xsd.root")));

      String golden = proto + "/" + proto + ".xml";
      if (platformFiles.contains(golden)) {
        softly.assertThat(normalized(text, name, proto))
          .as("%s в формате %s", kind, version)
          .isEqualTo(GoldenSnapshots.normalizeUuids(GoldenSnapshots.read(version, GoldenSnapshots.EXTERNAL, golden)));
      }
    }
    softly.assertAll();
  }

  @ParameterizedTest
  @EnumSource(SchemaVersion.class)
  void одинаковоеИмяДаётОдинаковыйФайл(SchemaVersion version) throws Exception {
    for (ExternalArtifactKind kind : ExternalArtifactKind.values()) {
      String name = "Нов" + GoldenScaffold.externalProtoName(kind);
      Path first = NewExternalArtifactXml.create(workspace.resolve("a-" + version.name()), name, kind, version);
      Path second = NewExternalArtifactXml.create(workspace.resolve("b-" + version.name()), name, kind, version);

      assertThat(Files.readAllBytes(second)).as("%s в формате %s", kind, version).isEqualTo(Files.readAllBytes(first));
    }
  }

  private static String normalized(String text, String name, String proto) {
    Pattern token = Pattern.compile("(?<![\\p{L}\\p{N}_])" + Pattern.quote(name) + "(?![\\p{L}\\p{N}_])");
    return GoldenSnapshots.normalizeUuids(token.matcher(text).replaceAll(Matcher.quoteReplacement(proto)));
  }
}
