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

import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * add-md-object пишет то же, что выгрузила бы платформа: для каждого вида и каждого формата
 * созданный файл совпадает с эталоном {@code snapshots/<формат>/cf-bare-objects} байт в байт
 * после замены имени на имя прототипа и UUID на метки ({@code xr:ClassId} сравнивается как есть).
 */
class MdObjectAddGoldenTest {

  @TempDir
  Path workspace;

  @ParameterizedTest
  @EnumSource(SchemaVersion.class)
  void каждыйВидСовпадаетСВыгрузкойПлатформы(SchemaVersion version) throws Exception {
    Path cf = workspace.resolve(version.name());
    EmptyCfScaffold.writeEmptyTree(cf, CfLayout.DEFAULT_CONFIGURATION_NAME, null, null, null, version);
    Path configuration = cf.resolve(CfLayout.CONFIGURATION_XML);

    SoftAssertions softly = new SoftAssertions();
    for (MdObjectAddType type : MdObjectAddType.values()) {
      String proto = GoldenScaffold.protoName(type);
      String name = "Нов" + proto;
      MdObjectAdd.add(configuration, name, version, type);

      String object = type.cfSubdir() + "/" + proto + ".xml";
      softly.assertThat(normalized(CfLayout.objectXmlInSubdir(cf, type.cfSubdir(), name), name, proto))
        .as("%s в формате %s", type, version)
        .isEqualTo(GoldenSnapshots.normalizeUuids(GoldenSnapshots.read(version, GoldenSnapshots.CF, object)));
      if (type.roleWithExtRights()) {
        String rights = type.cfSubdir() + "/" + proto + "/Ext/Rights.xml";
        softly.assertThat(normalized(CfLayout.roleExtRightsXml(cf, name), name, proto))
          .as("права %s в формате %s", type, version)
          .isEqualTo(GoldenSnapshots.normalizeUuids(GoldenSnapshots.read(version, GoldenSnapshots.CF, rights)));
      }
    }
    softly.assertAll();

    // выгрузка платформы идёт с CRLF: одиночный LF в Configuration.xml - порча файла
    assertThat(GoldenSnapshots.read(configuration).replace("\r\n", "")).doesNotContain("\n");
  }

  private static String normalized(Path file, String name, String proto) {
    Pattern token = Pattern.compile("(?<![\\p{L}\\p{N}_])" + Pattern.quote(name) + "(?![\\p{L}\\p{N}_])");
    String renamed = token.matcher(GoldenSnapshots.read(file)).replaceAll(Matcher.quoteReplacement(proto));
    return GoldenSnapshots.normalizeUuids(renamed);
  }
}
