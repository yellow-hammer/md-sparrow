/*
 * This file is a part of md-sparrow.
 *
 * Copyright (c) 2026
 * Ivan Karlo <i.karlo@outlook.com> and contributors
 *
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package io.github.yellowhammer.designerxml.cf;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.yellowhammer.designerxml.SchemaVersion;
import io.github.yellowhammer.designerxml.Ssl31SubmodulePaths;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

class SupportRulesTest {

  @Test
  void readsVendorAndModes() throws Exception {
    SupportRules.Rules rules = SupportRules.read(Ssl31SubmodulePaths.projectRoot().resolve("src/cf"));
    assertThat(rules.vendor).contains("1С");
    assertThat(rules.modeByUuid).isNotEmpty();
    // Демо-база на полной поддержке: изменение запрещено
    assertThat(rules.modeByUuid.get("4e1437bf-948b-4b05-9341-a2df3f301d7f")).isEqualTo(0);
  }

  @Test
  void writeIntoSupportedObjectIsRefusedAndFilesUntouched() throws Exception {
    Path objectXml = Ssl31SubmodulePaths.projectRoot().resolve("src/cf/Catalogs/_ДемоГруппыДоступаПартнеров.xml");
    byte[] before = Files.readAllBytes(objectXml);
    MdObjectPropertiesDto dto = MdObjectPropertiesEdit.readDto(objectXml, SchemaVersion.V2_20);
    dto.comment = "правка запрещена";
    assertThatThrownBy(() -> MdObjectPropertiesEdit.writeDto(objectXml, SchemaVersion.V2_20, dto))
      .isInstanceOf(IllegalStateException.class)
      .hasMessageContaining("на поддержке");
    assertThat(Files.readAllBytes(objectXml)).isEqualTo(before);
  }
}
