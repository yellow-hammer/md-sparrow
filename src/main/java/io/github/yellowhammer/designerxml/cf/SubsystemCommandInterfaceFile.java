/*
 * This file is a part of md-sparrow.
 *
 * Copyright (c) 2026
 * Ivan Karlo <i.karlo@outlook.com> and contributors
 *
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package io.github.yellowhammer.designerxml.cf;

import io.github.yellowhammer.designerxml.SchemaVersion;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Командный интерфейс подсистемы: {@code <Подсистема>/Ext/CommandInterface.xml}.
 *
 * <p>Схема extrnprops в JAXB-модель не входит; файл регулярный и читается
 * прямой обработкой текста. Запись меняет только блок видимости команд,
 * размещение и порядок остаются как были.
 */
public final class SubsystemCommandInterfaceFile {

  private static final Pattern VISIBILITY_COMMAND = Pattern.compile(
    "<Command name=\"([^\"]+)\">\\s*<Visibility>\\s*<xr:Common>([^<]+)</xr:Common>\\s*</Visibility>\\s*</Command>");
  private static final Pattern PLACEMENT_COMMAND = Pattern.compile(
    "<Command name=\"([^\"]+)\">\\s*<CommandGroup>([^<]+)</CommandGroup>"
      + "(?:\\s*<Placement>([^<]+)</Placement>)?");
  private static final Pattern SUBSYSTEM_ENTRY = Pattern.compile("<Subsystem>([^<]+)</Subsystem>");
  private static final Pattern GROUP_ENTRY = Pattern.compile("<Group>([^<]+)</Group>");

  private SubsystemCommandInterfaceFile() {
  }

  /** Настройка видимости или размещения одной команды. */
  public static final class CommandEntry {
    public String command;
    public String value;
    /** Способ размещения из CommandsPlacement; у других секций пусто. */
    public String place;

    public CommandEntry() {
    }

    public CommandEntry(String command, String value) {
      this.command = command;
      this.value = value;
    }

    public CommandEntry(String command, String value, String place) {
      this.command = command;
      this.value = value;
      this.place = place;
    }
  }

  /** Содержимое командного интерфейса подсистемы: все секции файла. */
  public static final class Dto {
    public List<CommandEntry> visibility = new ArrayList<>();
    public List<CommandEntry> placement = new ArrayList<>();
    public List<CommandEntry> order = new ArrayList<>();
    public List<String> subsystemsOrder = new ArrayList<>();
    public List<String> groupsOrder = new ArrayList<>();
  }

  /** Путь к файлу рядом с XML подсистемы. */
  public static Path interfacePath(Path subsystemXml) {
    Path normalized = subsystemXml.toAbsolutePath().normalize();
    String stem = normalized.getFileName().toString().replaceFirst("[.][Xx][Mm][Ll]$", "");
    return normalized.getParent().resolve(stem).resolve("Ext").resolve("CommandInterface.xml");
  }

  public static Dto read(Path subsystemXml) throws IOException {
    Dto out = new Dto();
    Path file = interfacePath(subsystemXml);
    if (!Files.isRegularFile(file)) {
      return out;
    }
    String text = Files.readString(file, StandardCharsets.UTF_8);
    Matcher visibility = VISIBILITY_COMMAND.matcher(section(text, "CommandsVisibility"));
    while (visibility.find()) {
      out.visibility.add(new CommandEntry(visibility.group(1), visibility.group(2).trim()));
    }
    Matcher placement = PLACEMENT_COMMAND.matcher(section(text, "CommandsPlacement"));
    while (placement.find()) {
      out.placement.add(new CommandEntry(
        placement.group(1),
        placement.group(2).trim(),
        placement.group(3) == null ? "" : placement.group(3).trim()));
    }
    Matcher order = PLACEMENT_COMMAND.matcher(section(text, "CommandsOrder"));
    while (order.find()) {
      out.order.add(new CommandEntry(order.group(1), order.group(2).trim()));
    }
    Matcher subsystems = SUBSYSTEM_ENTRY.matcher(section(text, "SubsystemsOrder"));
    while (subsystems.find()) {
      out.subsystemsOrder.add(subsystems.group(1).trim());
    }
    Matcher groups = GROUP_ENTRY.matcher(section(text, "GroupsOrder"));
    while (groups.find()) {
      out.groupsOrder.add(groups.group(1).trim());
    }
    return out;
  }

  /**
   * Пишет видимость команд: блок CommandsVisibility заменяется целиком,
   * остальные секции не трогаются. Пустой список убирает блок.
   */
  public static void writeVisibility(Path subsystemXml, SchemaVersion version, List<CommandEntry> visibility)
    throws IOException {
    writeSection(subsystemXml, version, "CommandsVisibility", visibility, (block, entry, eol) -> {
      block.append("\t\t<Command name=\"").append(escapeXml(entry.command.trim())).append("\">").append(eol);
      block.append("\t\t\t<Visibility>").append(eol);
      block.append("\t\t\t\t<xr:Common>").append("true".equals(entry.value) ? "true" : "false")
        .append("</xr:Common>").append(eol);
      block.append("\t\t\t</Visibility>").append(eol);
      block.append("\t\t</Command>").append(eol);
    });
  }

  /** Пишет размещение команд: группа и способ; блок CommandsPlacement заменяется целиком. */
  public static void writePlacement(Path subsystemXml, SchemaVersion version, List<CommandEntry> placement)
    throws IOException {
    writeSection(subsystemXml, version, "CommandsPlacement", placement, (block, entry, eol) -> {
      block.append("\t\t<Command name=\"").append(escapeXml(entry.command.trim())).append("\">").append(eol);
      block.append("\t\t\t<CommandGroup>").append(escapeXml(entry.value.trim())).append("</CommandGroup>").append(eol);
      block.append("\t\t\t<Placement>")
        .append(escapeXml(entry.place == null || entry.place.isBlank() ? "Auto" : entry.place.trim()))
        .append("</Placement>").append(eol);
      block.append("\t\t</Command>").append(eol);
    });
  }

  /** Пишет порядок команд внутри групп; блок CommandsOrder заменяется целиком. */
  public static void writeOrder(Path subsystemXml, SchemaVersion version, List<CommandEntry> order)
    throws IOException {
    writeSection(subsystemXml, version, "CommandsOrder", order, (block, entry, eol) -> {
      block.append("\t\t<Command name=\"").append(escapeXml(entry.command.trim())).append("\">").append(eol);
      block.append("\t\t\t<CommandGroup>").append(escapeXml(entry.value.trim())).append("</CommandGroup>").append(eol);
      block.append("\t\t</Command>").append(eol);
    });
  }

  private interface EntryRenderer {
    void render(StringBuilder block, CommandEntry entry, String eol);
  }

  /** Секции файла в порядке схемы: новый блок встаёт после предыдущей существующей. */
  private static final List<String> SECTION_ORDER = List.of(
    "CommandsVisibility", "CommandsPlacement", "CommandsOrder", "SubsystemsOrder", "GroupsOrder");

  private static void writeSection(
    Path subsystemXml,
    SchemaVersion version,
    String sectionName,
    List<CommandEntry> entries,
    EntryRenderer renderer
  ) throws IOException {
    SupportRules.ensureEditable(subsystemXml);
    Path file = interfacePath(subsystemXml);
    String eol = "\r\n";
    String existing = null;
    if (Files.isRegularFile(file)) {
      existing = Files.readString(file, StandardCharsets.UTF_8);
      eol = existing.contains("\r\n") ? "\r\n" : "\n";
    }
    StringBuilder block = new StringBuilder();
    List<CommandEntry> safeEntries = entries == null ? List.of() : entries;
    if (!safeEntries.isEmpty()) {
      block.append('\t').append('<').append(sectionName).append('>').append(eol);
      for (CommandEntry entry : safeEntries) {
        if (entry == null || entry.command == null || entry.command.isBlank()) {
          continue;
        }
        renderer.render(block, entry, eol);
      }
      block.append('\t').append("</").append(sectionName).append('>').append(eol);
    }
    String text;
    if (existing != null) {
      int start = existing.indexOf('<' + sectionName + '>');
      int end = existing.indexOf("</" + sectionName + '>');
      if (start >= 0 && end >= 0) {
        int lineStart = existing.lastIndexOf('\n', start);
        int blockEnd = existing.indexOf('\n', end);
        text = existing.substring(0, lineStart + 1) + block + existing.substring(blockEnd + 1);
      } else {
        int at = insertionPoint(existing, sectionName);
        text = existing.substring(0, at) + block + existing.substring(at);
      }
    } else {
      text = "﻿<?xml version=\"1.0\" encoding=\"UTF-8\"?>" + eol
        + "<CommandInterface xmlns=\"http://v8.1c.ru/8.3/xcf/extrnprops\""
        + " xmlns:xr=\"http://v8.1c.ru/8.3/xcf/readable\""
        + " xmlns:xs=\"http://www.w3.org/2001/XMLSchema\""
        + " xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\""
        + " version=\"" + version.metadataObjectVersionAttribute() + "\">" + eol
        + block
        + "</CommandInterface>";
      Files.createDirectories(file.getParent());
    }
    Files.writeString(file, text, StandardCharsets.UTF_8);
  }

  /** Точка вставки нового блока: после последней секции, идущей раньше по схеме. */
  private static int insertionPoint(String existing, String sectionName) {
    int anchor = -1;
    for (String section : SECTION_ORDER) {
      if (section.equals(sectionName)) {
        break;
      }
      int end = existing.indexOf("</" + section + '>');
      if (end >= 0) {
        anchor = existing.indexOf('\n', end);
      }
    }
    if (anchor >= 0) {
      return anchor + 1;
    }
    int open = existing.indexOf('>', existing.indexOf("<CommandInterface"));
    return skipEol(existing, open + 1);
  }

  private static int skipEol(String text, int index) {
    int out = index;
    while (out < text.length() && (text.charAt(out) == '\r' || text.charAt(out) == '\n')) {
      out += 1;
    }
    return out;
  }

  private static String section(String text, String name) {
    int start = text.indexOf('<' + name + '>');
    int end = text.indexOf("</" + name + '>');
    if (start < 0 || end < 0) {
      return "";
    }
    return text.substring(start, end);
  }

  private static String escapeXml(String value) {
    return value
      .replace("&", "&amp;")
      .replace("<", "&lt;")
      .replace(">", "&gt;")
      .replace("\"", "&quot;");
  }

  /** Для JSON-ответа CLI: все секции файла. */
  public static Map<String, Object> toJsonModel(Dto dto) {
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("visibility", dto.visibility);
    out.put("placement", dto.placement);
    out.put("order", dto.order);
    out.put("subsystemsOrder", dto.subsystemsOrder);
    out.put("groupsOrder", dto.groupsOrder);
    return out;
  }

  /** Виды, чей стандартной командой в интерфейсе подсистемы открывается список. */
  private static final List<String> OPEN_LIST_KINDS = List.of(
    "Catalog", "Document", "DocumentJournal", "Enum",
    "ChartOfCharacteristicTypes", "ChartOfAccounts", "ChartOfCalculationTypes",
    "InformationRegister", "AccumulationRegister", "AccountingRegister", "CalculationRegister",
    "ExchangePlan", "BusinessProcess", "Task", "FilterCriterion");

  /** Виды, чья стандартная команда открывает сам объект. */
  private static final List<String> OPEN_KINDS = List.of("Report", "DataProcessor", "CommonForm");

  /**
   * Стандартные команды объектов состава подсистемы: их конфигуратор показывает в
   * командном интерфейсе и без записей в файле настроек. Ссылки видов без
   * стандартной команды пропускаются.
   */
  public static List<String> contentCommands(List<String> contentRefs) {
    List<String> out = new ArrayList<>();
    for (String ref : contentRefs == null ? List.<String>of() : contentRefs) {
      if (ref == null) {
        continue;
      }
      int dot = ref.indexOf('.');
      if (dot <= 0) {
        continue;
      }
      String kind = ref.substring(0, dot);
      if (OPEN_LIST_KINDS.contains(kind)) {
        out.add(ref + ".StandardCommand.OpenList");
      } else if (OPEN_KINDS.contains(kind)) {
        out.add(ref + ".StandardCommand.Open");
      } else if ("CommonCommand".equals(kind)) {
        out.add(ref);
      }
    }
    return out;
  }
}
