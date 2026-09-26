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
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ConfigurationPropertiesEditTest {

  @TempDir
  Path tempDir;

  @Test
  void read_fromSsl31_configuration() throws Exception {
    Path cfg = Ssl31SubmodulePaths.configurationXml();
    ConfigurationPropertiesDto dto = ConfigurationPropertiesEdit.read(cfg, SchemaVersion.V2_20);
    assertThat(dto.name).isNotBlank();
    assertThat(dto.synonym).isNotNull();
    assertThat(dto.defaultRunMode).isNotBlank();
    assertThat(dto.compatibilityMode).isNotNull();
  }

  @Test
  void write_roundTrip_preservesChangedFields() throws Exception {
    Path cfg = Ssl31SubmodulePaths.configurationXml();
    Path copy = tempDir.resolve("Configuration.xml");
    Files.copy(cfg, copy);

    ConfigurationPropertiesDto dto = ConfigurationPropertiesEdit.read(copy, SchemaVersion.V2_20);
    dto.comment = "Тестовый комментарий";
    dto.vendor = "Тестовый поставщик";
    dto.version = "9.9.9";
    dto.defaultRoles = dto.defaultRoles == null ? java.util.List.of() : dto.defaultRoles;

    ConfigurationPropertiesEdit.write(copy, SchemaVersion.V2_20, dto);
    ConfigurationPropertiesDto after = ConfigurationPropertiesEdit.read(copy, SchemaVersion.V2_20);

    assertThat(after.comment).isEqualTo("Тестовый комментарий");
    assertThat(after.vendor).isEqualTo("Тестовый поставщик");
    assertThat(after.version).isEqualTo("9.9.9");
  }

  @Test
  void usePurposes_readAsConstantNames_matchOptions() throws Exception {
    Path cfg = Ssl31SubmodulePaths.configurationXml();

    ConfigurationPropertiesDto dto = ConfigurationPropertiesEdit.read(cfg, SchemaVersion.V2_20);

    // Панель отмечает варианты по совпадению значения со списком: написание одно
    assertThat(dto.usePurposeOptions).contains("PLATFORM_APPLICATION");
    assertThat(dto.usePurposes).isSubsetOf(dto.usePurposeOptions);
  }

  @Test
  void usePurposes_writtenByConstantName() throws Exception {
    Path cfg = Ssl31SubmodulePaths.configurationXml();
    Path copy = tempDir.resolve("Configuration.xml");
    Files.copy(cfg, copy);

    ConfigurationPropertiesDto dto = ConfigurationPropertiesEdit.read(copy, SchemaVersion.V2_20);
    dto.usePurposes = java.util.List.of("PLATFORM_APPLICATION", "MOBILE_PLATFORM_APPLICATION");
    ConfigurationPropertiesEdit.write(copy, SchemaVersion.V2_20, dto);

    ConfigurationPropertiesDto after = ConfigurationPropertiesEdit.read(copy, SchemaVersion.V2_20);
    assertThat(after.usePurposes).containsExactly("PLATFORM_APPLICATION", "MOBILE_PLATFORM_APPLICATION");
  }
}
