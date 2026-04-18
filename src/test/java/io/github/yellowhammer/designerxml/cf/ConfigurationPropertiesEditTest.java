/*
 * This file is a part of md-sparrow.
 *
 * SPDX-License-Identifier: LGPL-3.0-or-later
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
    assertThat(dto.synonymRu).isNotNull();
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
}
