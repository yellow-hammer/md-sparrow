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

import io.github.yellowhammer.designerxml.SchemaVersion;

import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * init-empty-cf даёт то же, что новая конфигурация платформы.
 *
 * <p>Пустая база ibcmd ({@code infobase create} и выгрузка на 8.3.23, 8.3.24, 8.3.27, 8.5.1) пишет
 * режим совместимости своей версии и назначение {@code PlatformApplication}; эталон cf-bare-objects
 * снят с семени, где режим 8.3.12 и назначение пустое. Где выгрузка пустой базы уже лежит в эталонах
 * ({@code snapshots/<формат>/cf-empty-infobase}), результат сверяется с ней целиком.
 */
class EmptyCfScaffoldTest {

  private static final Pattern CHILD_OBJECTS = Pattern.compile("(?s)<ChildObjects>.*?</ChildObjects>|<ChildObjects/>");
  private static final Pattern DEFAULT_LANGUAGE =
    Pattern.compile("(?s)<DefaultLanguage>.*?</DefaultLanguage>|<DefaultLanguage/>");
  private static final Pattern USE_PURPOSES = Pattern.compile("(?s)<UsePurposes>(.*?)</UsePurposes>");

  @TempDir
  Path workspace;

  private Path emptyConfiguration(SchemaVersion version, String name) throws IOException {
    Path cf = workspace.resolve(version.name());
    EmptyCfScaffold.writeEmptyTree(cf, name, null, null, null, version);
    return cf.resolve(CfLayout.CONFIGURATION_XML);
  }

  @ParameterizedTest
  @EnumSource(SchemaVersion.class)
  void режимСовместимостиИНазначениеКакУНовойКонфигурацииПлатформы(SchemaVersion version) throws IOException {
    String xml = GoldenSnapshots.read(emptyConfiguration(version, CfLayout.DEFAULT_CONFIGURATION_NAME));
    String platformMode = "Version" + version.platformLine().replace('.', '_');

    assertThat(ScaffoldPropertyEdit.leaf(xml, "CompatibilityMode")).hasValue(platformMode);
    assertThat(ScaffoldPropertyEdit.leaf(xml, "ConfigurationExtensionCompatibilityMode")).hasValue(platformMode);
    Matcher purposes = USE_PURPOSES.matcher(xml);
    assertThat(purposes.find()).as("UsePurposes заполнен").isTrue();
    assertThat(purposes.group(1)).contains(">PlatformApplication<");
  }

  @ParameterizedTest
  @EnumSource(SchemaVersion.class)
  void переводыСтрокКакУПлатформы(SchemaVersion version) throws IOException {
    String xml = GoldenSnapshots.read(emptyConfiguration(version, CfLayout.DEFAULT_CONFIGURATION_NAME));

    assertThat(xml).startsWith("﻿");
    assertThat(xml.replace("\r\n", "")).as("одиночный LF среди CRLF").doesNotContain("\n");
  }

  @ParameterizedTest
  @EnumSource(SchemaVersion.class)
  void совпадаетСВыгрузкойПустойБазыПлатформы(SchemaVersion version) throws IOException {
    Path platformXml = GoldenSnapshots.format(version).resolve(GoldenSnapshots.EMPTY_INFOBASE)
      .resolve(CfLayout.CONFIGURATION_XML);
    assumeTrue(Files.isRegularFile(platformXml),
      "нет выгрузки пустой базы формата " + version.metadataObjectVersionAttribute()
        + " (snapshots/<формат>/" + GoldenSnapshots.EMPTY_INFOBASE + " в samples-1c-platform)");
    String platform = GoldenSnapshots.read(platformXml);
    String name = ScaffoldPropertyEdit.leaf(platform, "Name").orElseThrow();

    String ours = GoldenSnapshots.read(emptyConfiguration(version, name));

    // состав ChildObjects и язык по умолчанию зависят от того, создана ли база с языком
    assertThat(comparable(ours)).isEqualTo(comparable(platform));
  }

  private static String comparable(String xml) {
    String withoutChildren = CHILD_OBJECTS.matcher(xml).replaceAll("<ChildObjects/>");
    String withoutLanguage = DEFAULT_LANGUAGE.matcher(withoutChildren).replaceAll("<DefaultLanguage/>");
    return GoldenSnapshots.normalizeUuids(withoutLanguage);
  }
}
