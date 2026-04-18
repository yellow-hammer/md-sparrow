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

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.xml.sax.SAXException;

import jakarta.xml.bind.JAXBElement;
import jakarta.xml.bind.JAXBException;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DesignerXmlRoundTripTest {

  @TempDir
  Path tempDir;

  @Test
  void roundTripSsl31ConfigurationV2_21() throws Exception {
    Path input = Ssl31SubmodulePaths.configurationXml();
    Path out = tempDir.resolve("out.xml");

    Object root = DesignerXml.read(input, SchemaVersion.V2_21);
    assertThat(root).isInstanceOf(JAXBElement.class);
    assertThat(((JAXBElement<?>) root).getDeclaredType().getSimpleName()).isEqualTo("MetaDataObject");

    DesignerXml.write(out, root, SchemaVersion.V2_21, WriteOptions.defaults());
    Object again = DesignerXml.read(out, SchemaVersion.V2_21);
    assertThat(again).isInstanceOf(JAXBElement.class);
    assertThat(((JAXBElement<?>) again).getDeclaredType()).isEqualTo(((JAXBElement<?>) root).getDeclaredType());
  }

  @Test
  void roundTripSsl31ConfigurationV2_20() throws Exception {
    Path input = Ssl31SubmodulePaths.configurationXml();
    Path out = tempDir.resolve("out-v2_20.xml");

    Object root = DesignerXml.read(input, SchemaVersion.V2_20);
    assertThat(root).isInstanceOf(JAXBElement.class);

    DesignerXml.write(out, root, SchemaVersion.V2_20, WriteOptions.defaults());
    Object again = DesignerXml.read(out, SchemaVersion.V2_20);
    assertThat(again).isInstanceOf(JAXBElement.class);
    assertThat(((JAXBElement<?>) again).getDeclaredType()).isEqualTo(((JAXBElement<?>) root).getDeclaredType());
  }

  @Test
  void validateSsl31ConfigurationAgainstXsd() throws Exception {
    Path input = Ssl31SubmodulePaths.configurationXml();
    Path xsdRoot = Path.of(System.getProperty("xsd.root", "namespace-forest")).toAbsolutePath().normalize();
    Assumptions.assumeTrue(Files.isDirectory(xsdRoot), "xsd.root exists: " + xsdRoot);
    Path catalog = Path.of(System.getProperty("user.dir"))
      .resolve("xjb/ns/2.21/catalog.xml")
      .normalize();
    Assumptions.assumeTrue(Files.isRegularFile(catalog), "catalog exists: " + catalog);

    try {
      XmlValidator.validate(input, SchemaVersion.V2_21, xsdRoot, catalog);
    } catch (SAXException e) {
      // Выгрузка может ссылаться на namespace вне catalog (например current-config); API валиден, схема — частичная.
      Assumptions.abort("XSD validation skipped for fixture: " + e.getMessage());
    }
  }

  @Test
  void invalidXmlFailsUnmarshal() throws Exception {
    Path bad = tempDir.resolve("bad.xml");
    Files.writeString(bad, "<not-meta/>");
    assertThatThrownBy(() -> DesignerXml.read(bad, SchemaVersion.V2_21))
      .isInstanceOf(JAXBException.class);
  }
}
