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
import io.github.yellowhammer.designerxml.Ssl31SubmodulePaths;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConfigurationCatalogListerTest {

  @Test
  void jsonEscape_handlesQuotesAndControlChars() {
    assertThat(ConfigurationCatalogLister.jsonEscape("a\"b\\c"))
      .isEqualTo("a\\\"b\\\\c");
    assertThat(ConfigurationCatalogLister.jsonEscape("x\ny"))
      .isEqualTo("x\\ny");
  }

  @Test
  void toJsonArray_empty() {
    assertThat(ConfigurationCatalogLister.toJsonArray(List.of())).isEqualTo("[]");
  }

  @Test
  void listNames_supportsAnyTagOfTheFormat() throws Exception {
    Path cfg = Ssl31SubmodulePaths.configurationXml();
    // теги берутся из модели формата, а не из зашитого списка: раньше состав вроде
    // SettingsStorage отвергался как неизвестный
    for (String tag : List.of("Catalog", "SettingsStorage", "DocumentJournal", "BusinessProcess", "WebService")) {
      assertThat(ConfigurationChildObjectLister.listNames(cfg, SchemaVersion.V2_20, tag))
        .as("состав по тегу %s", tag)
        .isNotNull();
    }
  }

  @Test
  void listNames_unknownTag_reportsFormat() throws Exception {
    Path cfg = Ssl31SubmodulePaths.configurationXml();
    assertThatThrownBy(() -> ConfigurationChildObjectLister.listNames(cfg, SchemaVersion.V2_20, "ТегКоторогоНет"))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("2.20")
      .hasMessageContaining("ТегКоторогоНет");
  }

  @Test
  void listCatalogNames_fromSsl31_configuration_containsProtoStem() throws Exception {
    Path cfg = Ssl31SubmodulePaths.configurationXml();
    List<String> names = ConfigurationCatalogLister.listCatalogNames(cfg, SchemaVersion.V2_20);
    assertThat(names).isNotEmpty();
    Path proto = Ssl31SubmodulePaths.anyCatalogObjectXml();
    String stem = proto.getFileName().toString().replaceFirst("\\.xml$", "");
    assertThat(names).contains(stem);
  }
}
