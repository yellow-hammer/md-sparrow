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
  void contentCommandsFollowMemberKinds() {
    java.util.List<String> commands = SubsystemCommandInterfaceFile.contentCommands(java.util.List.of(
      "Catalog.Товары",
      "Report.Продажи",
      "CommonCommand.ОткрытьНастройки",
      "Constant.ОсновнаяВалюта",
      "InformationRegister.Курсы"));
    assertThat(commands).containsExactly(
      "Catalog.Товары.StandardCommand.OpenList",
      "Report.Продажи.StandardCommand.Open",
      "CommonCommand.ОткрытьНастройки",
      "InformationRegister.Курсы.StandardCommand.OpenList");
  }

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
  void writePlacementChangesGroupAndKeepsSections() throws Exception {
    Path source = Ssl31SubmodulePaths.projectRoot()
      .resolve("src/cf/Subsystems/_ДемоАнкетирование/Ext/CommandInterface.xml");
    Path subsystemXml = tempDir.resolve("Подсистема.xml");
    Files.writeString(subsystemXml, "<x/>");
    Path target = SubsystemCommandInterfaceFile.interfacePath(subsystemXml);
    Files.createDirectories(target.getParent());
    Files.copy(source, target);

    SubsystemCommandInterfaceFile.Dto before = SubsystemCommandInterfaceFile.read(subsystemXml);
    before.placement.get(0).value = "NavigationPanelImportant";
    SubsystemCommandInterfaceFile.writePlacement(subsystemXml, SchemaVersion.V2_20, before.placement);

    SubsystemCommandInterfaceFile.Dto after = SubsystemCommandInterfaceFile.read(subsystemXml);
    assertThat(after.placement.get(0).value).isEqualTo("NavigationPanelImportant");
    assertThat(after.placement).hasSameSizeAs(before.placement);
    assertThat(after.visibility).hasSameSizeAs(before.visibility);
    assertThat(after.order).hasSameSizeAs(before.order);
  }

  @Test
  void writeOrderReordersCommandsInsideSection() throws Exception {
    Path source = Ssl31SubmodulePaths.projectRoot()
      .resolve("src/cf/Subsystems/_ДемоАнкетирование/Ext/CommandInterface.xml");
    Path subsystemXml = tempDir.resolve("Подсистема.xml");
    Files.writeString(subsystemXml, "<x/>");
    Path target = SubsystemCommandInterfaceFile.interfacePath(subsystemXml);
    Files.createDirectories(target.getParent());
    Files.copy(source, target);

    SubsystemCommandInterfaceFile.Dto before = SubsystemCommandInterfaceFile.read(subsystemXml);
    java.util.List<SubsystemCommandInterfaceFile.CommandEntry> reversed = new java.util.ArrayList<>(before.order);
    java.util.Collections.reverse(reversed);
    SubsystemCommandInterfaceFile.writeOrder(subsystemXml, SchemaVersion.V2_20, reversed);

    SubsystemCommandInterfaceFile.Dto after = SubsystemCommandInterfaceFile.read(subsystemXml);
    assertThat(after.order.get(0).command).isEqualTo(before.order.get(before.order.size() - 1).command);
    assertThat(after.subsystemsOrder).isEqualTo(before.subsystemsOrder);
  }

  @Test
  void writeSubsystemsAndGroupsOrderKeepsOtherSections() throws Exception {
    Path source = Ssl31SubmodulePaths.projectRoot()
      .resolve("src/cf/Subsystems/_ДемоАнкетирование/Ext/CommandInterface.xml");
    Path subsystemXml = tempDir.resolve("Подсистема.xml");
    Files.writeString(subsystemXml, "<x/>");
    Path target = SubsystemCommandInterfaceFile.interfacePath(subsystemXml);
    Files.createDirectories(target.getParent());
    Files.copy(source, target);

    SubsystemCommandInterfaceFile.Dto before = SubsystemCommandInterfaceFile.read(subsystemXml);
    java.util.List<String> subsystems = new java.util.ArrayList<>(before.subsystemsOrder);
    java.util.Collections.reverse(subsystems);
    SubsystemCommandInterfaceFile.writeSubsystemsOrder(subsystemXml, SchemaVersion.V2_20, subsystems);
    SubsystemCommandInterfaceFile.writeGroupsOrder(subsystemXml, SchemaVersion.V2_20, before.groupsOrder);

    SubsystemCommandInterfaceFile.Dto after = SubsystemCommandInterfaceFile.read(subsystemXml);
    assertThat(after.subsystemsOrder).isEqualTo(subsystems);
    assertThat(after.groupsOrder).isEqualTo(before.groupsOrder);
    assertThat(after.visibility).hasSameSizeAs(before.visibility);
    assertThat(after.placement).hasSameSizeAs(before.placement);
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
