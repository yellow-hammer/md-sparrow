/*
 * This file is a part of md-sparrow.
 *
 * Copyright (c) 2026
 * Ivan Karlo <i.karlo@outlook.com> and contributors
 *
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package io.github.yellowhammer.designerxml.cf;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.yellowhammer.designerxml.SchemaVersion;
import io.github.yellowhammer.designerxml.Ssl31SubmodulePaths;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

class SubsystemCommandInterfaceFileTest {

  @TempDir Path tempDir;

  @Test
  void readsVisibilityAndPlacement() throws Exception {
    SubsystemCommandInterfaceFile.Dto dto = SubsystemCommandInterfaceFile.read(
      Ssl31SubmodulePaths.projectRoot().resolve("src/cf/Subsystems/_ДемоАнкетирование.xml"));
    assertThat(dto.visibility)
      .anyMatch(entry -> "Document.Анкета.StandardCommand.OpenList".equals(entry.command)
        && "false".equals(entry.value));
    assertThat(dto.placement).isNotEmpty();
  }

  @Test
  void writeVisibilityKeepsOtherSections() throws Exception {
    Path source = Ssl31SubmodulePaths.projectRoot()
      .resolve("src/cf/Subsystems/_ДемоАнкетирование/Ext/CommandInterface.xml");
    Path subsystemXml = tempDir.resolve("Подсистема.xml");
    Files.writeString(subsystemXml, "<x/>");
    Path target = SubsystemCommandInterfaceFile.interfacePath(subsystemXml);
    Files.createDirectories(target.getParent());
    Files.copy(source, target);

    SubsystemCommandInterfaceFile.Dto before = SubsystemCommandInterfaceFile.read(subsystemXml);
    before.visibility.get(0).value = "true";
    SubsystemCommandInterfaceFile.writeVisibility(subsystemXml, SchemaVersion.V2_20, before.visibility);

    SubsystemCommandInterfaceFile.Dto after = SubsystemCommandInterfaceFile.read(subsystemXml);
    assertThat(after.visibility.get(0).value).isEqualTo("true");
    assertThat(after.visibility).hasSameSizeAs(before.visibility);
    assertThat(after.placement).hasSameSizeAs(before.placement);
    assertThat(Files.readString(target)).contains("<CommandsPlacement>");
  }

  @Test
  void writeCreatesFileWhenMissing() throws Exception {
    Path subsystemXml = tempDir.resolve("Новая.xml");
    Files.writeString(subsystemXml, "<x/>");
    SubsystemCommandInterfaceFile.writeVisibility(
      subsystemXml,
      SchemaVersion.V2_20,
      java.util.List.of(new SubsystemCommandInterfaceFile.CommandEntry("Catalog.Товары.StandardCommand.OpenList", "true")));
    SubsystemCommandInterfaceFile.Dto dto = SubsystemCommandInterfaceFile.read(subsystemXml);
    assertThat(dto.visibility).hasSize(1);
  }
}
