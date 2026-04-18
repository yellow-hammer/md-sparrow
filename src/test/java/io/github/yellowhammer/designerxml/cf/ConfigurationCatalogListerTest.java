/*
 * This file is a part of md-sparrow.
 *
 * Copyright (c) 2026
 * Ivan Karlo <i.karlo@outlook.com> and contributors
 *
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package io.github.yellowhammer.designerxml.cf;

import io.github.yellowhammer.designerxml.SchemaVersion;
import io.github.yellowhammer.designerxml.Ssl31SubmodulePaths;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

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
  void listCatalogNames_fromSsl31_configuration_containsProtoStem() throws Exception {
    Path cfg = Ssl31SubmodulePaths.configurationXml();
    List<String> names = ConfigurationCatalogLister.listCatalogNames(cfg, SchemaVersion.V2_20);
    assertThat(names).isNotEmpty();
    Path proto = Ssl31SubmodulePaths.anyCatalogObjectXml();
    String stem = proto.getFileName().toString().replaceFirst("\\.xml$", "");
    assertThat(names).contains(stem);
  }
}
