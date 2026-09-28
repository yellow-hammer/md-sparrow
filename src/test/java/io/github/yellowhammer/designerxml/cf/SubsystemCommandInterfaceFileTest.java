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

  /** Исключения видимости по ролям из формы ssl31: у формы и командного интерфейса один тип значения. */
  static java.util.List<SubsystemCommandInterfaceFile.RoleValue> formRoleVisibility() throws Exception {
    String form = Files.readString(Ssl31SubmodulePaths.projectRoot()
      .resolve("src/cf/Documents/_ДемоПоступлениеТоваров/Forms/ФормаДокумента/Ext/Form.xml"));
    java.util.regex.Matcher block = java.util.regex.Pattern.compile(
      "<Visible>\\s*<xr:Common>false</xr:Common>(.*?)</Visible>", java.util.regex.Pattern.DOTALL).matcher(form);
    assertThat(block.find()).isTrue();
    java.util.regex.Matcher value = java.util.regex.Pattern.compile(
      "<xr:Value name=\"([^\"]+)\">([^<]+)</xr:Value>").matcher(block.group(1));
    java.util.List<SubsystemCommandInterfaceFile.RoleValue> out = new java.util.ArrayList<>();
    while (value.find()) {
      out.add(new SubsystemCommandInterfaceFile.RoleValue(value.group(1), value.group(2)));
    }
    assertThat(out).extracting(role -> role.value).contains("true", "false");
    return out;
  }

  @Test
  void writeVisibilityKeepsRoleExceptions() throws Exception {
    Path source = Ssl31SubmodulePaths.projectRoot()
      .resolve("src/cf/Subsystems/_ДемоАнкетирование/Ext/CommandInterface.xml");
    Path subsystemXml = tempDir.resolve("Подсистема.xml");
    Files.writeString(subsystemXml, "<x/>");
    Path target = SubsystemCommandInterfaceFile.interfacePath(subsystemXml);
    Files.createDirectories(target.getParent());
    Files.copy(source, target);
    java.util.List<SubsystemCommandInterfaceFile.RoleValue> roles = formRoleVisibility();

    SubsystemCommandInterfaceFile.Dto before = SubsystemCommandInterfaceFile.read(subsystemXml);
    before.visibility.get(0).roles = roles;
    SubsystemCommandInterfaceFile.writeVisibility(subsystemXml, SchemaVersion.V2_20, before.visibility);

    SubsystemCommandInterfaceFile.Dto after = SubsystemCommandInterfaceFile.read(subsystemXml);
    assertThat(after.visibility.get(0).roles).usingRecursiveFieldByFieldElementComparator().isEqualTo(roles);
    // Повторная запись прочитанного не теряет исключений
    SubsystemCommandInterfaceFile.writeVisibility(subsystemXml, SchemaVersion.V2_20, after.visibility);
    assertThat(SubsystemCommandInterfaceFile.read(subsystemXml).visibility.get(0).roles)
      .usingRecursiveFieldByFieldElementComparator().isEqualTo(roles);
    assertThat(after.visibility.subList(1, after.visibility.size())).allSatisfy(entry -> assertThat(entry.roles).isEmpty());
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
