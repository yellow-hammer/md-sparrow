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
    "<Command name=\"([^\"]+)\">\\s*<CommandGroup>([^<]+)</CommandGroup>");

  private SubsystemCommandInterfaceFile() {
  }

  /** Настройка видимости или размещения одной команды. */
  public static final class CommandEntry {
    public String command;
    public String value;

    public CommandEntry() {
    }

    public CommandEntry(String command, String value) {
      this.command = command;
      this.value = value;
    }
  }

  /** Содержимое командного интерфейса подсистемы. */
  public static final class Dto {
    public List<CommandEntry> visibility = new ArrayList<>();
    public List<CommandEntry> placement = new ArrayList<>();
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
      out.placement.add(new CommandEntry(placement.group(1), placement.group(2).trim()));
    }
    return out;
  }

  /**
   * Пишет видимость команд: блок CommandsVisibility заменяется целиком,
   * остальные секции не трогаются. Пустой список убирает блок.
   */
  public static void writeVisibility(Path subsystemXml, SchemaVersion version, List<CommandEntry> visibility)
    throws IOException {
    Path file = interfacePath(subsystemXml);
    String eol = "\r\n";
    String existing = null;
    if (Files.isRegularFile(file)) {
      existing = Files.readString(file, StandardCharsets.UTF_8);
      eol = existing.contains("\r\n") ? "\r\n" : "\n";
    }
    StringBuilder block = new StringBuilder();
    List<CommandEntry> entries = visibility == null ? List.of() : visibility;
    if (!entries.isEmpty()) {
      block.append("\t<CommandsVisibility>").append(eol);
      for (CommandEntry entry : entries) {
        if (entry == null || entry.command == null || entry.command.isBlank()) {
          continue;
        }
        block.append("\t\t<Command name=\"").append(escapeXml(entry.command.trim())).append("\">").append(eol);
        block.append("\t\t\t<Visibility>").append(eol);
        block.append("\t\t\t\t<xr:Common>").append("true".equals(entry.value) ? "true" : "false")
          .append("</xr:Common>").append(eol);
        block.append("\t\t\t</Visibility>").append(eol);
        block.append("\t\t</Command>").append(eol);
      }
      block.append("\t</CommandsVisibility>").append(eol);
    }
    String text;
    if (existing != null) {
      int start = existing.indexOf("<CommandsVisibility>");
      int end = existing.indexOf("</CommandsVisibility>");
      if (start >= 0 && end >= 0) {
        int lineStart = existing.lastIndexOf('\n', start);
        int blockEnd = existing.indexOf('\n', end);
        text = existing.substring(0, lineStart + 1) + block + existing.substring(blockEnd + 1);
      } else {
        int open = existing.indexOf('>', existing.indexOf("<CommandInterface"));
        text = existing.substring(0, open + 1) + eol + block
          + existing.substring(skipEol(existing, open + 1));
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

  /** Для JSON-ответа CLI: видимость и размещение картами «команда - значение». */
  public static Map<String, Object> toJsonModel(Dto dto) {
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("visibility", dto.visibility);
    out.put("placement", dto.placement);
    return out;
  }
}
