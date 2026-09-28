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

import static org.assertj.core.api.Assertions.assertThat;

import io.github.yellowhammer.designerxml.SchemaVersion;
import io.github.yellowhammer.designerxml.Ssl31SubmodulePaths;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Составной тип после правки пишется в порядке схемы {@code v8:TypeDescription}:
 * квалификаторы числа, строки, даты и двоичных данных. Иначе файл не проходит
 * проверку схемой и не загружается.
 */
class MdTypeDescriptionSerialTest {

  /** Реквизит с числом, строкой и датой в одном типе. */
  private static final String CATALOG = "src/cf/Catalogs/ВидыПроверок.xml";

  @TempDir
  Path temp;

  @Test
  void compositeTypeKeepsSchemaOrderOfQualifiers() throws Exception {
    Path source = Ssl31SubmodulePaths.projectRoot().resolve(CATALOG);
    Path xml = temp.resolve(source.getFileName().toString());
    Files.copy(source, xml);
    String before = Files.readString(source, StandardCharsets.UTF_8);

    MdObjectPropertiesDto dto = MdObjectPropertiesEdit.readDto(xml, SchemaVersion.V2_20);
    MdNamedPropertyDto attribute = dto.attributes.stream()
      .filter(item -> item.type != null && item.type.numberQualifiers != null
        && item.type.stringQualifiers != null && item.type.dateQualifiers != null)
      .findFirst()
      .orElseThrow(() -> new AssertionError("в фикстуре нет реквизита составного типа"));
    String length = attribute.type.stringQualifiers.length;
    attribute.type.stringQualifiers.length = String.valueOf(Integer.parseInt(length) + 1);
    MdObjectPropertiesEdit.writeDto(xml, SchemaVersion.V2_20, dto);

    // Ожидаем исходный файл, в котором поменялась только длина строки
    int name = before.indexOf("<Name>" + attribute.name + "</Name>");
    int at = before.indexOf("<v8:Length>" + length + "</v8:Length>", name);
    String expected = before.substring(0, at)
      + "<v8:Length>" + attribute.type.stringQualifiers.length + "</v8:Length>"
      + before.substring(at + ("<v8:Length>" + length + "</v8:Length>").length());
    assertThat(Files.readString(xml, StandardCharsets.UTF_8)).isEqualTo(expected);
  }
}
