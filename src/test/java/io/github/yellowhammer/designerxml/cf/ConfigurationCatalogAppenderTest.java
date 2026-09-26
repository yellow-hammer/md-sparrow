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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ConfigurationCatalogChildObjectAppenderTest {

  @TempDir
  Path dir;

  @Test
  void insertsCatalogAfterSubsystemNotImmediatelyAfterLanguage() throws Exception {
    Path cfg = dir.resolve("Configuration.xml");
    Files.writeString(
      cfg,
      """
      <?xml version="1.0" encoding="UTF-8"?>
      <MetaDataObject xmlns="http://v8.1c.ru/8.3/MDClasses" version="2.20">
      <Configuration uuid="00000000-0000-0000-0000-000000000001">
      \t\t<ChildObjects>
      \t\t\t<Language>Русский</Language>
      \t\t\t<Subsystem>Подсистема1</Subsystem>
      \t\t</ChildObjects>
      </Configuration>
      </MetaDataObject>
      """);
    ConfigurationChildObjectAppender.append(cfg, "Catalog", "_НовыйСправочник");
    String text = Files.readString(cfg);
    int idxLang = text.indexOf("<Language>");
    int idxSub = text.indexOf("<Subsystem>");
    int idxCat = text.indexOf("<Catalog>_НовыйСправочник</Catalog>");
    assertThat(idxCat).isGreaterThan(idxSub);
    assertThat(idxSub).isGreaterThan(idxLang);
  }

  @Test
  void insertsCatalogAtEndOfCatalogBlockNotSortedByName() throws Exception {
    Path cfg = dir.resolve("Configuration.xml");
    Files.writeString(
      cfg,
      """
      <?xml version="1.0" encoding="UTF-8"?>
      <MetaDataObject xmlns="http://v8.1c.ru/8.3/MDClasses" version="2.20">
      <Configuration uuid="00000000-0000-0000-0000-000000000001">
      \t\t<ChildObjects>
      \t\t\t<Language>Русский</Language>
      \t\t\t<Catalog>Аа</Catalog>
      \t\t\t<Catalog>Вв</Catalog>
      \t\t</ChildObjects>
      </Configuration>
      </MetaDataObject>
      """);
    ConfigurationChildObjectAppender.append(cfg, "Catalog", "Бб");
    String text = Files.readString(cfg);
    int a = text.indexOf("<Catalog>Аа</Catalog>");
    int b = text.indexOf("<Catalog>Вв</Catalog>");
    int c = text.indexOf("<Catalog>Бб</Catalog>");
    assertThat(a).isLessThan(b);
    assertThat(b).isLessThan(c);
  }
}
