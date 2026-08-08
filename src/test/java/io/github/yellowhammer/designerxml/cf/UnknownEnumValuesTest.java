/*
 * This file is a part of md-sparrow.
 *
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package io.github.yellowhammer.designerxml.cf;

import io.github.yellowhammer.designerxml.SchemaVersion;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/** Значения перечислений, которых нет в модели, доходят до вызывающей стороны. */
class UnknownEnumValuesTest {

  private static Path ssl31(String... parts) {
    String root = System.getProperty("fixtures.ssl31.root");
    assertThat(root).isNotBlank();
    return Path.of(root, parts);
  }

  @Test
  void режимСовместимостиЧитаетсяХотяВПеречисленииЕгоНет() throws Exception {
    // Перечисление обрывается на 8.3.12: несовместимых версий после неё не было, а метки версий есть.
    var props = ConfigurationPropertiesEdit.read(ssl31("src", "cf", "Configuration.xml"), SchemaVersion.V2_20);

    assertThat(props.compatibilityMode).isEqualTo("VERSION_8_3_24");
    assertThat(props.configurationExtensionCompatibilityMode).isEqualTo("VERSION_8_3_27");
  }

  @Test
  void значениеXmlПриводитсяКЗаписиМодели() {
    assertThat(UnknownEnumValues.constantName("Version8_3_24")).isEqualTo("VERSION_8_3_24");
    assertThat(UnknownEnumValues.constantName("Version8_5_1")).isEqualTo("VERSION_8_5_1");
    assertThat(UnknownEnumValues.constantName("TaxiEnableVersion8_2")).isEqualTo("TAXI_ENABLE_VERSION_8_2");
    assertThat(UnknownEnumValues.constantName("Adopted")).isEqualTo("ADOPTED");
    assertThat(UnknownEnumValues.constantName("")).isEmpty();
  }

  @Test
  void известноеМоделиЗначениеИзФайлаНеДочитывается() {
    assertThat(UnknownEnumValues.orFromXml("MANAGED", Path.of("нет-такого.xml"), "DataLockControlMode"))
      .isEqualTo("MANAGED");
  }
}
