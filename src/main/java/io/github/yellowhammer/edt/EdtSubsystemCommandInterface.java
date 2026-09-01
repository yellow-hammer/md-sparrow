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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import io.github.yellowhammer.designerxml.cf.SubsystemCommandInterfaceFile;
import io.github.yellowhammer.edt.EdtObjectReader.EdtNode;

/**
 * Командный интерфейс подсистемы 1С:EDT.
 *
 * Лежит своим файлом рядом с описанием подсистемы: {@code CommandInterface.cmi}
 * вместо {@code Ext/CommandInterface.xml}. Разметка другая - команды собраны во
 * фрагменты по группам размещения, а видимость записана пустым элементом, - но
 * контракт для панели общий.
 */
public final class EdtSubsystemCommandInterface {

  /** Отступ уровня в файлах EDT. */
  private static final String INDENT = "  ";

  private EdtSubsystemCommandInterface() {
  }

  /**
   * Файл командного интерфейса рядом с описанием подсистемы.
   *
   * @param subsystemMdo описание подсистемы
   * @return файл командного интерфейса, даже если его ещё нет
   */
  public static Path interfacePath(Path subsystemMdo) {
    return subsystemMdo.toAbsolutePath().normalize().getParent().resolve("CommandInterface.cmi");
  }

  /**
   * Читает командный интерфейс.
   *
   * @param subsystemMdo описание подсистемы
   * @return секции интерфейса; пустые, если файла нет
   * @throws IOException если файл не читается
   */
  public static SubsystemCommandInterfaceFile.Dto read(Path subsystemMdo) throws IOException {
    SubsystemCommandInterfaceFile.Dto dto = new SubsystemCommandInterfaceFile.Dto();
    Path file = interfacePath(subsystemMdo);
    if (!Files.isRegularFile(file)) {
      return dto;
    }

    EdtNode root = EdtObjectReader.read(file);
    for (EdtNode fragment : section(root, "commandsVisibility", "visibilityFragments")) {
      String command = fragment.property("command");
      if (!command.isEmpty()) {
        // Видимость записана самим присутствием элемента, без значения
        dto.visibility.add(new SubsystemCommandInterfaceFile.CommandEntry(
            command, fragment.list("visible").isEmpty() ? "false" : "true"));
      }
    }
    for (EdtNode fragment : section(root, "commandsPlacement", "placementFragments")) {
      String group = fragment.property("group");
      for (EdtNode command : fragment.list("commands")) {
        dto.placement.add(new SubsystemCommandInterfaceFile.CommandEntry(command.value(), "", group));
      }
    }
    for (EdtNode fragment : section(root, "commandsOrder", "orderFragments")) {
      String group = fragment.property("group");
      for (EdtNode command : fragment.list("commands")) {
        dto.order.add(new SubsystemCommandInterfaceFile.CommandEntry(command.value(), "", group));
      }
    }
    for (EdtNode order : root.list("subsystemsOrder")) {
      order.list("subsystems").forEach(item -> dto.subsystemsOrder.add(item.value()));
    }
    for (EdtNode order : root.list("groupsOrder")) {
      order.list("groups").forEach(item -> dto.groupsOrder.add(item.value()));
    }
    return dto;
  }

  /**
   * Записывает командный интерфейс целиком.
   *
   * @param subsystemMdo описание подсистемы
   * @param dto секции интерфейса
   * @throws IOException если файл не пишется
   */
  public static void write(Path subsystemMdo, SubsystemCommandInterfaceFile.Dto dto) throws IOException {
    Path file = interfacePath(subsystemMdo);
    String eol = eolOf(file);

    StringBuilder xml = new StringBuilder();
    xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>").append(eol);
    xml.append("<cmi:CommandInterface xmlns:cmi=\"http://g5.1c.ru/v8/dt/cmi\">").append(eol);
    appendVisibility(xml, dto.visibility, eol);
    appendGrouped(xml, "commandsPlacement", "placementFragments", dto.placement, eol);
    appendGrouped(xml, "commandsOrder", "orderFragments", dto.order, eol);
    appendList(xml, "subsystemsOrder", "subsystems", dto.subsystemsOrder, eol);
    appendList(xml, "groupsOrder", "groups", dto.groupsOrder, eol);
    xml.append("</cmi:CommandInterface>").append(eol);

    Files.createDirectories(file.getParent());
    Files.writeString(file, xml.toString(), StandardCharsets.UTF_8);
  }

  /** Видимость команд: элемент без значения означает видимую команду. */
  private static void appendVisibility(
      StringBuilder xml,
      List<SubsystemCommandInterfaceFile.CommandEntry> entries,
      String eol) {
    if (entries.isEmpty()) {
      return;
    }
    xml.append(INDENT).append("<commandsVisibility>").append(eol);
    for (SubsystemCommandInterfaceFile.CommandEntry entry : entries) {
      xml.append(INDENT).append(INDENT).append("<visibilityFragments>").append(eol);
      xml.append(INDENT.repeat(3)).append("<command>").append(escape(entry.command)).append("</command>")
          .append(eol);
      if (!"false".equals(entry.value)) {
        xml.append(INDENT.repeat(3)).append("<visible/>").append(eol);
      }
      xml.append(INDENT).append(INDENT).append("</visibilityFragments>").append(eol);
    }
    xml.append(INDENT).append("</commandsVisibility>").append(eol);
  }

  /** Размещение и порядок: команды собраны во фрагменты по группам. */
  private static void appendGrouped(
      StringBuilder xml,
      String section,
      String fragment,
      List<SubsystemCommandInterfaceFile.CommandEntry> entries,
      String eol) {
    if (entries.isEmpty()) {
      return;
    }
    xml.append(INDENT).append("<").append(section).append(">").append(eol);
    String group = null;
    boolean open = false;
    for (SubsystemCommandInterfaceFile.CommandEntry entry : entries) {
      String entryGroup = entry.place == null ? "" : entry.place;
      if (!open || !entryGroup.equals(group)) {
        if (open) {
          xml.append(INDENT).append(INDENT).append("</").append(fragment).append(">").append(eol);
        }
        xml.append(INDENT).append(INDENT).append("<").append(fragment).append(">").append(eol);
        if (!entryGroup.isEmpty()) {
          xml.append(INDENT.repeat(3)).append("<group>").append(escape(entryGroup)).append("</group>").append(eol);
        }
        group = entryGroup;
        open = true;
      }
      xml.append(INDENT.repeat(3)).append("<commands>").append(escape(entry.command)).append("</commands>")
          .append(eol);
    }
    if (open) {
      xml.append(INDENT).append(INDENT).append("</").append(fragment).append(">").append(eol);
    }
    xml.append(INDENT).append("</").append(section).append(">").append(eol);
  }

  /** Порядок подсистем и групп: список ссылок. */
  private static void appendList(StringBuilder xml, String section, String item, List<String> values, String eol) {
    if (values.isEmpty()) {
      return;
    }
    xml.append(INDENT).append("<").append(section).append(">").append(eol);
    for (String value : values) {
      xml.append(INDENT).append(INDENT).append("<").append(item).append(">").append(escape(value))
          .append("</").append(item).append(">").append(eol);
    }
    xml.append(INDENT).append("</").append(section).append(">").append(eol);
  }

  /** Фрагменты секции: у секции их бывает несколько. */
  private static List<EdtNode> section(EdtNode root, String section, String fragment) {
    List<EdtNode> fragments = new ArrayList<>();
    for (EdtNode node : root.list(section)) {
      fragments.addAll(node.list(fragment));
    }
    return fragments;
  }

  /** Перевод строки: у существующего файла берётся его собственный. */
  private static String eolOf(Path file) throws IOException {
    if (!Files.isRegularFile(file)) {
      return "\n";
    }
    return Files.readString(file, StandardCharsets.UTF_8).contains("\r\n") ? "\r\n" : "\n";
  }

  private static String escape(String value) {
    return value == null ? "" : value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
  }
}
