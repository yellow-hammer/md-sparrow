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
package io.github.yellowhammer.designerxml;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilderFactory;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class XmlValidatorTest {

  private static Path mdClassesXsd2_20() {
    String xsdRoot = System.getProperty("xsd.root");
    assertThat(xsdRoot).as("xsd.root").isNotBlank();
    Path p = Path.of(xsdRoot, "schemas", "2.20", "v8.1c.ru-8.3-MDClasses.xsd");
    assertThat(p)
      .as("Ожидается XSD в submodule namespace-forest (git submodule update --init)")
      .exists();
    return p;
  }

  @Test
  void applyConfigurationFormatVersionFromMetaDataObject_insertsFromRootVersion() throws Exception {
    DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
    dbf.setNamespaceAware(true);
    try (InputStream in = Files.newInputStream(Ssl31SubmodulePaths.configurationXml())) {
      Document doc = dbf.newDocumentBuilder().parse(in);
      XmlValidator.applyConfigurationFormatVersionFromMetaDataObject(doc);
      Element cfg = (Element) doc.getDocumentElement()
        .getElementsByTagNameNS(XmlValidator.NS_MD_CLASSES, "Configuration")
        .item(0);
      assertThat(cfg).isNotNull();
      assertThat(cfg.getAttribute("formatVersion")).isEqualTo("2.20");
    }
  }

  @Test
  void patchConfigurationPropertiesChoiceUnbounded_insertsMaxOccurs() throws Exception {
    String xsd = Files.readString(mdClassesXsd2_20());
    String out = XmlValidator.patchConfigurationPropertiesChoiceUnbounded(xsd);
    int props = out.indexOf("<xs:complexType name=\"ConfigurationProperties\">");
    assertThat(props).isGreaterThanOrEqualTo(0);
    int propsEnd = out.indexOf("</xs:complexType>", props);
    assertThat(propsEnd).isGreaterThan(props);
    String propsBlock = out.substring(props, propsEnd);
    assertThat(propsBlock).contains("<xs:choice maxOccurs=\"unbounded\">");
    assertThat(propsBlock).doesNotContain("<xs:choice>");
  }

  /**
   * Файлы submodule не трогаем: сценарий «уже есть formatVersion» задаётся только в памяти DOM.
   */
  @Test
  void applyConfigurationFormatVersionFromMetaDataObject_doesNotOverrideExisting() throws Exception {
    DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
    dbf.setNamespaceAware(true);
    try (InputStream in = Files.newInputStream(Ssl31SubmodulePaths.configurationXml())) {
      Document doc = dbf.newDocumentBuilder().parse(in);
      Element root = doc.getDocumentElement();
      root.setAttribute("version", "2.21");
      Element cfg = (Element) root
        .getElementsByTagNameNS(XmlValidator.NS_MD_CLASSES, "Configuration")
        .item(0);
      assertThat(cfg).isNotNull();
      cfg.setAttribute("formatVersion", "2.20");

      XmlValidator.applyConfigurationFormatVersionFromMetaDataObject(doc);
      assertThat(cfg.getAttribute("formatVersion")).isEqualTo("2.20");
    }
  }
}
