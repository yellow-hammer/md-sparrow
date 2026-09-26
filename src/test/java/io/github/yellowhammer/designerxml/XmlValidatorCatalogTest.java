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
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Карта import'ов строится в памяти из каталога версии: known namespace → реально существующий .xsd,
 * отсутствующие файлы (более узкий набор схем версии) пропускаются.
 */
class XmlValidatorCatalogTest {

  @TempDir
  Path xsdDir;

  @Test
  void mapsPresentNamespacesAndSkipsAbsent() throws Exception {
    Path coreXsd = xsdDir.resolve("v8.1c.ru-8.1-data-core.xsd");
    Files.writeString(coreXsd, "<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\"/>");
    // файл uobjects намеренно не создаём

    Map<String, Path> map = XmlValidator.buildSchemaUriMap(xsdDir);

    assertThat(map.get("http://v8.1c.ru/8.1/data/core")).isEqualTo(coreXsd.normalize());
    assertThat(map).doesNotContainKey("http://v8.1c.ru/8.2/uobjects");
  }
}
