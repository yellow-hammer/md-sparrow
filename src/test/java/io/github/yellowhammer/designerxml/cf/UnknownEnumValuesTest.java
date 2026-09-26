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
    // Аббревиатура остаётся словом целиком, а не рассыпается по буквам
    assertThat(UnknownEnumValues.constantName("HTMLDocument")).isEqualTo("HTML_DOCUMENT");
    assertThat(UnknownEnumValues.constantName("AddIn")).isEqualTo("ADD_IN");
    assertThat(UnknownEnumValues.constantName("XDTOPackage")).isEqualTo("XDTO_PACKAGE");
    assertThat(UnknownEnumValues.constantName("")).isEmpty();
  }

  @Test
  void известноеМоделиЗначениеИзФайлаНеДочитывается() {
    assertThat(UnknownEnumValues.orFromXml("MANAGED", Path.of("нет-такого.xml"), "DataLockControlMode"))
      .isEqualTo("MANAGED");
  }
}
