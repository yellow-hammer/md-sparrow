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
import jakarta.xml.bind.JAXBException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Создание отдельного внешнего объекта (.erf/.epf) — параметризация эталона external-files/empty
 * нужной версии формата (см. {@link GoldenScaffold}): имя + детерминированные UUID, фиксированный
 * ClassId платформы сохраняется. Работает для любой версии, у которой есть эталон.
 */
public final class NewExternalArtifactXml {

  private NewExternalArtifactXml() {
  }

  public static Path create(
    Path artifactsRoot,
    String artifactName,
    ExternalArtifactKind kind,
    SchemaVersion version) throws IOException, JAXBException {
    if (artifactsRoot == null) {
      throw new IllegalArgumentException("artifactsRoot required");
    }
    if (artifactName == null || artifactName.trim().isEmpty()) {
      throw new IllegalArgumentException("artifactName required");
    }
    String name = artifactName.trim();
    CatalogNameConstraints.check(name);
    Path dir = artifactsRoot.resolve(name);
    Path xmlPath = dir.resolve(name + ".xml");
    if (Files.exists(xmlPath)) {
      throw new IllegalArgumentException("file already exists: " + xmlPath);
    }
    // Эталон снят на русской конфигурации: подпись уезжает в язык той, к которой объект относится
    String xml = LocalStringElement.retarget(
      GoldenScaffold.generateExternalArtifact(kind, name, version),
      ConfigurationLanguage.codeOf(xmlPath));
    try (ByteArrayInputStream in = new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8))) {
      DesignerXml.unmarshal(version, in);
    }
    Files.createDirectories(dir);
    Files.writeString(xmlPath, xml, StandardCharsets.UTF_8);
    return xmlPath;
  }
}
