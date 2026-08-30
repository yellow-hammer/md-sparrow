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
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Состав плана обмена: лежит отдельным файлом {@code <План>/Ext/Content.xml}
 * парами «ссылка - авторегистрация». Схема extrnprops в JAXB-модель не входит,
 * файл регулярный: читается и пишется прямой обработкой текста.
 */
public final class ExchangePlanContentFile {

  private static final Pattern ITEM = Pattern.compile(
    "<Item>\\s*<Metadata>([^<]*)</Metadata>\\s*(?:<AutoRecord>([^<]*)</AutoRecord>\\s*)?</Item>");

  private ExchangePlanContentFile() {
  }

  /** Путь к файлу состава рядом с XML плана обмена. */
  public static Path contentPath(Path exchangePlanXml) {
    Path normalized = exchangePlanXml.toAbsolutePath().normalize();
    String stem = normalized.getFileName().toString().replaceFirst("[.][Xx][Mm][Ll]$", "");
    return normalized.getParent().resolve(stem).resolve("Ext").resolve("Content.xml");
  }

  /**
   * Члены состава: пустой список, когда файла нет.
   *
   * @param exchangePlanXml XML самого плана обмена
   */
  public static List<MdContentMemberDto> read(Path exchangePlanXml) throws IOException {
    Path content = contentPath(exchangePlanXml);
    List<MdContentMemberDto> out = new ArrayList<>();
    if (!Files.isRegularFile(content)) {
      return out;
    }
    Matcher matcher = ITEM.matcher(Files.readString(content, StandardCharsets.UTF_8));
    while (matcher.find()) {
      String ref = matcher.group(1).trim();
      if (!ref.isEmpty()) {
        out.add(new MdContentMemberDto(ref, matcher.group(2) == null ? "" : matcher.group(2).trim(), ""));
      }
    }
    return out;
  }

  /**
   * Пишет состав целиком: пролог и корневой тег существующего файла остаются
   * как были, новый файл создаётся с заголовком выгрузки.
   */
  public static void write(Path exchangePlanXml, SchemaVersion version, List<MdContentMemberDto> members)
    throws IOException {
    Path content = contentPath(exchangePlanXml);
    String existing = Files.isRegularFile(content)
      ? Files.readString(content, StandardCharsets.UTF_8)
      : null;
    String eol = existing != null && existing.contains("\r\n") ? "\r\n" : "\r\n";
    StringBuilder body = new StringBuilder();
    for (MdContentMemberDto member : members == null ? List.<MdContentMemberDto>of() : members) {
      if (member == null || member.ref == null || member.ref.isBlank()) {
        continue;
      }
      body.append("\t<Item>").append(eol);
      body.append("\t\t<Metadata>").append(escapeXml(member.ref.trim())).append("</Metadata>").append(eol);
      String mode = member.mode == null ? "" : member.mode.trim();
      if (!mode.isEmpty()) {
        body.append("\t\t<AutoRecord>").append(escapeXml(mode)).append("</AutoRecord>").append(eol);
      }
      body.append("\t</Item>").append(eol);
    }
    String text;
    if (existing != null) {
      int open = existing.indexOf('>', existing.indexOf("<ExchangePlanContent"));
      int close = existing.lastIndexOf("</ExchangePlanContent>");
      if (open < 0 || close < 0) {
        throw new IOException("Content.xml без корневого элемента ExchangePlanContent");
      }
      text = existing.substring(0, open + 1) + eol + body + existing.substring(close);
    } else {
      text = "﻿<?xml version=\"1.0\" encoding=\"UTF-8\"?>" + eol
        + "<ExchangePlanContent xmlns=\"http://v8.1c.ru/8.3/xcf/extrnprops\""
        + " xmlns:xr=\"http://v8.1c.ru/8.3/xcf/readable\""
        + " xmlns:xs=\"http://www.w3.org/2001/XMLSchema\""
        + " xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\""
        + " version=\"" + version.metadataObjectVersionAttribute() + "\">" + eol
        + body
        + "</ExchangePlanContent>";
      Files.createDirectories(content.getParent());
    }
    Files.writeString(content, text, StandardCharsets.UTF_8);
  }

  private static String escapeXml(String value) {
    return value
      .replace("&", "&amp;")
      .replace("<", "&lt;")
      .replace(">", "&gt;")
      .replace("\"", "&quot;");
  }
}
