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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class NewConfigurationXmlTest {

  @TempDir
  Path workspace;

  @Test
  void v220CreatesEmptyChildObjectsAndRoundTrip() throws Exception {
    Path cf = workspace.resolve("src").resolve("cf");
    String name = "_ПустаяКонфигурация";
    String syn = "Пустая конфигурация (md-sparrow)";

    NewConfigurationXml.write(cf, name, syn, "Тестовый поставщик", "0.0.1", SchemaVersion.V2_20);

    Path out = cf.resolve("Configuration.xml");
    assertThat(out).exists();
    String xml = Files.readString(out, StandardCharsets.UTF_8);
    assertThat(xml).containsPattern("<ChildObjects\\s*/>|<ChildObjects>\\s*</ChildObjects>");
    assertThat(xml).contains("<Name>" + name + "</Name>");
    assertThat(xml).containsPattern("<[^:>]*DefaultRoles\\s*/>");
    assertThat(xml).containsPattern("<[^:>]*DefaultLanguage>\\s*</[^:>]*DefaultLanguage>|"
      + "<[^:>]*DefaultLanguage\\s*/>");
    assertThat(xml).contains("<Vendor>Тестовый поставщик</Vendor>");
    assertThat(xml).contains("<Version>0.0.1</Version>");
    assertThat(Pattern.compile(Pattern.quote(syn)).matcher(xml).find()).isTrue();

    DesignerXml.read(out, SchemaVersion.V2_20);
    String xsdRoot = System.getProperty("xsd.root");
    assertThat(xsdRoot).as("xsd.root").isNotBlank();
    Path catalog220 = Path.of(System.getProperty("user.dir")).resolve("xjb/ns/2.20/catalog.xml").normalize();
    assertThat(catalog220).as("catalog 2.20").exists();
    XmlValidator.validate(out, SchemaVersion.V2_20, Path.of(xsdRoot), catalog220);
  }

  @Test
  void configuratorEmptyTreeWipesPriorObjectFiles() throws Exception {
    Path cf = workspace.resolve("cfg-wipe");
    Files.createDirectories(cf.resolve(CfLayout.CATALOGS_DIR));
    Files.writeString(cf.resolve(CfLayout.CATALOGS_DIR).resolve("_Old.xml"), "<xml/>");
    Files.writeString(cf.resolve("junk.txt"), "x");

    NewConfigurationXml.writeConfiguratorEmptyTree(cf, CfLayout.DEFAULT_CONFIGURATION_NAME, null, null, null, SchemaVersion.V2_21);

    assertThat(cf.resolve(CfLayout.CATALOGS_DIR)).doesNotExist();
    assertThat(cf.resolve("junk.txt")).doesNotExist();
    assertThat(cf.resolve(CfLayout.CONFIGURATION_XML)).exists();
  }

  @Test
  void configuratorEmptyTreeV221MatchesConfiguratorLayout() throws Exception {
    Path cf = workspace.resolve("cfg-empty");
    NewConfigurationXml.writeConfiguratorEmptyTree(cf, CfLayout.DEFAULT_CONFIGURATION_NAME, null, null, null, SchemaVersion.V2_21);

    assertThat(cf.resolve(CfLayout.CONFIG_DUMP_INFO_XML)).doesNotExist();
    assertThat(cf.resolve(CfLayout.LANGUAGES_DIR).resolve(CfLayout.RUSSIAN_LANGUAGE_NAME + ".xml")).exists();
    Path out = cf.resolve(CfLayout.CONFIGURATION_XML);
    String xml = Files.readString(out, StandardCharsets.UTF_8);
    assertThat(xml).contains("<Name>" + CfLayout.DEFAULT_CONFIGURATION_NAME + "</Name>");
    assertThat(xml).contains("<Language>" + CfLayout.RUSSIAN_LANGUAGE_NAME + "</Language>");
    assertThat(xml).contains("Language." + CfLayout.RUSSIAN_LANGUAGE_NAME);

    DesignerXml.read(out, SchemaVersion.V2_21);
  }

  @Test
  void v221RoundTripAndValidate() throws Exception {
    Path cf = workspace.resolve("cf221");
    NewConfigurationXml.write(cf, "Пустая221", "Синоним", "", "1.0.0", SchemaVersion.V2_21);
    Path out = cf.resolve("Configuration.xml");
    DesignerXml.read(out, SchemaVersion.V2_21);
    String xsdRoot = System.getProperty("xsd.root");
    assertThat(xsdRoot).as("xsd.root").isNotBlank();
    Path catalog221 = Path.of(System.getProperty("user.dir")).resolve("xjb/ns/2.21/catalog.xml").normalize();
    assertThat(catalog221).as("catalog 2.21").exists();
    XmlValidator.validate(out, SchemaVersion.V2_21, Path.of(xsdRoot), catalog221);
  }
}
