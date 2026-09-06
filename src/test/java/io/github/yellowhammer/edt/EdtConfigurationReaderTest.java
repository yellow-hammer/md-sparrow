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
package io.github.yellowhammer.edt;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import io.github.yellowhammer.designerxml.cf.ChildObjectEntry;

/** Состав конфигурации в формате EDT читается на настоящей библиотеке. */
class EdtConfigurationReaderTest {

  private static Path configurationMdo;
  private static EdtModel model;

  @BeforeAll
  static void locate() throws Exception {
    model = EdtModel.bundled();
    String fixture = System.getProperty("fixtures.ssl31edt.root");
    assertThat(fixture).isNotBlank();
    configurationMdo = Path.of(fixture, "ssl31", "src", "Configuration", "Configuration.mdo");
    assertThat(configurationMdo).exists();
  }

  @Test
  void перечисляетОбъектыВидомИИменем() throws Exception {
    List<ChildObjectEntry> objects = EdtConfigurationReader.listChildObjects(configurationMdo, model);

    assertThat(objects).contains(new ChildObjectEntry("Catalog", "Валюты"));
    assertThat(objects).extracting(ChildObjectEntry::objectType).contains("Document", "CommonModule", "Enum");
    assertThat(objects).filteredOn(entry -> entry.objectType().equals("CommonModule")).hasSizeGreaterThan(100);
  }

  @Test
  void именаБезВидаОбъекта() throws Exception {
    List<ChildObjectEntry> objects = EdtConfigurationReader.listChildObjects(configurationMdo, model);

    assertThat(objects).extracting(ChildObjectEntry::name).allSatisfy(name -> assertThat(name).doesNotContain("."));
  }

  @Test
  void свойстваКонфигурацииВСоставНеПопадают() throws Exception {
    List<ChildObjectEntry> objects = EdtConfigurationReader.listChildObjects(configurationMdo, model);

    // Роли по умолчанию и язык записаны такими же ссылками, как состав
    assertThat(objects).extracting(ChildObjectEntry::objectType)
        .doesNotContain("version", "compatibilityMode", "vendor");
    assertThat(objects).filteredOn(entry -> entry.objectType().equals("Role"))
        .extracting(ChildObjectEntry::name)
        .doesNotHaveDuplicates();
  }

  @Test
  void составБерётсяИзСхемы() throws Exception {
    assertThat(model.composition("Configuration")).extracting(EdtModel.Composition::feature)
        .contains("catalogs", "documents", "commonModules", "roles", "subsystems", "languages")
        .doesNotContain("defaultRoles", "defaultLanguage", "content", "help", "version");
  }

  @Test
  void объектыВнутриФайлаТожеСостав() throws Exception {
    List<ChildObjectEntry> objects = EdtConfigurationReader.listChildObjects(configurationMdo, model);

    // Язык записан не ссылкой, а прямо в конфигурации: своего файла у него нет
    assertThat(objects).contains(new ChildObjectEntry("Language", "Русский"));
  }
}
