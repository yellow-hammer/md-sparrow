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

import jakarta.xml.bind.JAXBException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Добавляет справочник в дерево выгрузки: файл {@code Catalogs/<имя>.xml} (структура только из JAXB —
 * {@link NewCatalogXml}) и запись в {@code Configuration.xml}.
 */
public final class AddCatalog {

  private AddCatalog() {
  }

  /**
   * @param configurationXml путь к {@code .../src/cf/Configuration.xml}
   * @param catalogName      имя объекта метаданных (как в платформе)
   * @param synonymRu        синоним для ru (можно {@code null} — тогда совпадает с именем, если не {@code synonymEmpty})
   * @param synonymEmpty     пустой ru-синоним (как в эталонной выгрузке); имеет приоритет над {@code synonymRu}
   * @param version          версия XSD/JAXB
   */
  public static void add(
    Path configurationXml,
    String catalogName,
    String synonymRu,
    boolean synonymEmpty,
    SchemaVersion version) throws IOException, JAXBException {
    Path cfRoot = configurationXml.getParent();
    if (cfRoot == null || !Files.isRegularFile(configurationXml)) {
      throw new IllegalArgumentException("configuration XML must exist: " + configurationXml);
    }
    Path catalogFile = CfLayout.catalogObjectXml(cfRoot, catalogName);
    if (Files.exists(catalogFile)) {
      throw new IllegalArgumentException("catalog file already exists: " + catalogFile);
    }

    String xml = CatalogXmlEmitter.emit(catalogName, synonymRu, synonymEmpty, version);
    Files.createDirectories(catalogFile.getParent());
    Files.writeString(catalogFile, xml, java.nio.charset.StandardCharsets.UTF_8);

    ConfigurationCatalogAppender.append(configurationXml, catalogName, version);
  }
}
