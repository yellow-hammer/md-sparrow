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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Очистка каталога выгрузки не трогает проект 1С:EDT. */
class CfTreeDeleteTest {

  @TempDir
  Path workspace;

  @Test
  void каталогВыгрузкиОчищаетсяЦеликом() throws Exception {
    Path dump = workspace.resolve("cf");
    Files.createDirectories(dump.resolve("Catalogs"));
    Files.writeString(dump.resolve("Configuration.xml"), "<x/>");
    Files.writeString(dump.resolve("Catalogs").resolve("Товары.xml"), "<x/>");

    CfTreeDelete.deleteAllContents(dump);

    assertThat(dump).isEmptyDirectory();
  }

  @Test
  void исходникиПроектаEdtНеОчищаются() throws Exception {
    Path project = workspace.resolve("Проект");
    Path source = project.resolve("src");
    Files.createDirectories(source.resolve("Configuration"));
    Files.writeString(source.resolve("Configuration").resolve("Configuration.mdo"), "<x/>");

    assertThatThrownBy(() -> CfTreeDelete.deleteAllContents(source))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("1С:EDT");
    assertThat(source.resolve("Configuration").resolve("Configuration.mdo")).exists();
  }

  @Test
  void каталогПроектаEdtНеОчищается() throws Exception {
    Path project = workspace.resolve("Проект");
    Files.createDirectories(project.resolve("DT-INF"));
    Files.writeString(project.resolve(".project"), "<projectDescription/>");
    Files.writeString(project.resolve("DT-INF").resolve("PROJECT.PMF"), "Manifest-Version: 1.0");

    assertThatThrownBy(() -> CfTreeDelete.deleteAllContents(project))
      .isInstanceOf(IllegalArgumentException.class);
    assertThat(project.resolve(".project")).exists();
  }
}
