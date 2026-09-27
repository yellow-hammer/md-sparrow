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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Добавляет одну строку {@code <Tag>имя</Tag>} в {@code Configuration/ChildObjects} для заданного
 * тега метаданных (порядок тегов — {@link ConfigurationChildObjectsOrder}). Внутри блока одного
 * тега — в конец, без сортировки по имени.
 */
final class ConfigurationChildObjectAppender {

  private ConfigurationChildObjectAppender() {
  }

  private static final Pattern OPENING_CHILD_TAG = Pattern.compile(
    "^\\s*<(?:[\\w.-]+:)?([A-Za-z][A-Za-z0-9]*)>");

  private static final String CHILD_OBJECTS_OPEN = "<ChildObjects>";

  private static final String CHILD_OBJECTS_CLOSE = "</ChildObjects>";

  private static final String CHILD_OBJECTS_EMPTY = "<ChildObjects/>";

  static void append(Path configurationXml, String xmlTag, String objectName) throws IOException {
    String content = new String(Files.readAllBytes(configurationXml), StandardCharsets.UTF_8);
    replace(configurationXml, appended(content, xmlTag, objectName));
    // Состав изменился - служебный файл версий объектов не должен от него отставать.
    ConfigDumpInfoSync.sync(configurationXml.getParent());
  }

  /**
   * Текст {@code Configuration.xml} со строкой нового объекта в составе. Файл не меняется: все
   * отказы (нет состава, объект уже в нём) случаются до записи.
   *
   * @param content текст {@code Configuration.xml}
   * @return новый текст
   */
  static String appended(String content, String xmlTag, String objectName) {
    Objects.requireNonNull(xmlTag, "xmlTag");
    if (xmlTag.isEmpty()) {
      throw new IllegalArgumentException("xmlTag must not be empty");
    }
    int cfgClose = content.lastIndexOf("</Configuration>");
    if (cfgClose < 0) {
      throw new IllegalArgumentException("Configuration.xml: not found </Configuration>");
    }
    int coClose = content.lastIndexOf(CHILD_OBJECTS_CLOSE, cfgClose);
    return coClose < 0
      ? intoEmptyChildObjects(content, cfgClose, xmlTag, objectName)
      : intoChildObjects(content, cfgClose, coClose, xmlTag, objectName);
  }

  /**
   * Заменяет {@code Configuration.xml} новым текстом целиком, через временный файл рядом: файл не
   * остаётся записанным наполовину. {@code ConfigDumpInfo.xml} не сверяется.
   */
  static void replace(Path configurationXml, String newContent) throws IOException {
    Path parent = configurationXml.getParent();
    if (parent == null) {
      throw new IllegalArgumentException("configuration XML has no parent directory");
    }
    Path tmp = Files.createTempFile(parent, "cfg-child-append-", ".tmp");
    try {
      Files.writeString(tmp, newContent, StandardCharsets.UTF_8);
      try {
        Files.move(tmp, configurationXml, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
      } catch (AtomicMoveNotSupportedException e) {
        Files.move(tmp, configurationXml, StandardCopyOption.REPLACE_EXISTING);
      }
    } finally {
      try {
        Files.deleteIfExists(tmp);
      } catch (IOException ignore) {
        /* */
      }
    }
  }

  private static String intoChildObjects(
    String content, int cfgClose, int coClose, String xmlTag, String objectName) {
    int childOpen = findMatchingChildObjectsOpen(content, coClose);
    if (childOpen < 0) {
      throw new IllegalArgumentException("Configuration.xml: not found <ChildObjects> for closing tag");
    }
    String childRegion = content.substring(childOpen, coClose);
    if (childRegionContainsTagEntry(childRegion, xmlTag, objectName)) {
      throw new IllegalArgumentException(xmlTag + " already in Configuration: " + objectName);
    }
    requireNothingBeforeConfigurationClose(content, coClose + CHILD_OBJECTS_CLOSE.length(), cfgClose);

    Pattern linePattern = linePatternForTag(xmlTag);
    int insertAt = findInsertIndex(content, childOpen, coClose, xmlTag, linePattern);
    String indentPrefix = detectLineIndent(childRegion, linePattern);
    String newLine = indentPrefix + entry(xmlTag, objectName) + newline(content);
    return content.substring(0, insertAt) + newLine + content.substring(insertAt);
  }

  /**
   * Пустой состав платформа пишет самозакрытым тегом: так выглядит расширение в режиме
   * совместимости 8.3.13 и ниже, пока в нём нет объектов. Тег раскрывается, а строка встаёт на
   * уровень глубже него, как её пишет платформа.
   */
  private static String intoEmptyChildObjects(String content, int cfgClose, String xmlTag, String objectName) {
    int empty = content.lastIndexOf(CHILD_OBJECTS_EMPTY, cfgClose);
    if (empty < 0) {
      throw new IllegalArgumentException("Configuration.xml: not found <ChildObjects> before </Configuration>");
    }
    int emptyEnd = empty + CHILD_OBJECTS_EMPTY.length();
    requireNothingBeforeConfigurationClose(content, emptyEnd, cfgClose);
    String newline = newline(content);
    String indent = XmlLines.indentAt(content, empty);
    return content.substring(0, empty)
      + CHILD_OBJECTS_OPEN + newline
      + indent + '\t' + entry(xmlTag, objectName) + newline
      + indent + CHILD_OBJECTS_CLOSE
      + content.substring(emptyEnd);
  }

  private static void requireNothingBeforeConfigurationClose(String content, int childObjectsEnd, int cfgClose) {
    if (!content.substring(childObjectsEnd, cfgClose).matches("\\R\\s*")) {
      throw new IllegalArgumentException("Configuration.xml: unexpected content before </Configuration>");
    }
  }

  private static String entry(String xmlTag, String objectName) {
    return "<" + xmlTag + ">" + escapeXmlText(objectName) + "</" + xmlTag + ">";
  }

  private static String newline(String content) {
    return content.contains("\r\n") ? "\r\n" : "\n";
  }

  /**
   * Строка состава с тегом {@code xmlTag}; группа 1 - её отступ. Отступ - только пробелы и табуляция:
   * с переводами строк он захватил бы и пустую строку перед строкой, и новая строка пришла бы с ней.
   */
  private static Pattern linePatternForTag(String xmlTag) {
    String q = Pattern.quote(xmlTag);
    return Pattern.compile(
      "(?m)^(\\h*)<(?:[\\w.-]+:)?" + q + ">\\s*([^<]*)\\s*</(?:[\\w.-]+:)?" + q + ">\\h*\\R?");
  }

  private record TagLine(int lineStart, int lineEnd) {
  }

  private static int findInsertIndex(
    String content,
    int childOpen,
    int coClose,
    String xmlTag,
    Pattern linePattern) {
    String region = content.substring(childOpen, coClose);
    List<TagLine> lines = new ArrayList<>();
    Matcher mc = linePattern.matcher(region);
    while (mc.find()) {
      lines.add(new TagLine(childOpen + mc.start(), childOpen + mc.end()));
    }
    if (!lines.isEmpty()) {
      return lines.get(lines.size() - 1).lineEnd;
    }
    return insertFirstOfTag(content, childOpen, region, xmlTag);
  }

  private static int insertFirstOfTag(
    String content,
    int childOpen,
    String region,
    String xmlTag) {
    Set<String> before = ConfigurationChildObjectsOrder.tagsStrictlyBefore(xmlTag);
    int gt = region.indexOf('>');
    if (gt < 0) {
      throw new IllegalArgumentException("Configuration.xml: malformed <ChildObjects>");
    }
    int innerBase = childOpen + gt + 1;
    String inner = region.substring(gt + 1);

    int lastBeforeEnd = -1;
    int firstAfterStart = -1;

    int lineStart = 0;
    while (lineStart < inner.length()) {
      int nl = inner.indexOf('\n', lineStart);
      if (nl < 0) {
        nl = inner.length();
      } else {
        nl++;
      }
      String line = inner.substring(lineStart, nl);
      if (!line.isBlank()) {
        Matcher om = OPENING_CHILD_TAG.matcher(line);
        if (om.find()) {
          String tag = om.group(1);
          int absLineStart = innerBase + lineStart;
          int absLineEnd = innerBase + nl;
          if (before.contains(tag)) {
            lastBeforeEnd = absLineEnd;
          } else if (!xmlTag.equals(tag) && firstAfterStart < 0) {
            firstAfterStart = absLineStart;
          }
        }
      }
      lineStart = nl;
    }

    if (firstAfterStart >= 0) {
      return firstAfterStart;
    }
    if (lastBeforeEnd >= 0) {
      return lastBeforeEnd;
    }
    int pos = innerBase;
    int limit = childOpen + region.length();
    while (pos < limit && (content.charAt(pos) == '\r' || content.charAt(pos) == '\n')) {
      pos++;
    }
    return pos;
  }

  private static String detectLineIndent(String childRegion, Pattern linePattern) {
    Matcher mc = linePattern.matcher(childRegion);
    String indent = null;
    while (mc.find()) {
      indent = mc.group(1);
    }
    if (indent != null) {
      return indent;
    }
    Pattern langIndent = Pattern.compile("(?m)^(\\h*)<(?:[\\w.-]+:)?Language>");
    Matcher ml = langIndent.matcher(childRegion);
    while (ml.find()) {
      indent = ml.group(1);
    }
    if (indent != null) {
      return indent;
    }
    // Ни строк этого вида, ни языка (состав расширения): отступ первой строки состава. Хвост
    // начинается переводом строки за <ChildObjects>, и он в отступ не входит
    int afterOpen = childRegion.indexOf('>');
    if (afterOpen >= 0) {
      String tail = childRegion.substring(afterOpen + 1);
      Matcher firstEl = Pattern.compile("(?m)^(\\h*)<(?:[\\w.-]+:)?[A-Za-z]").matcher(tail);
      if (firstEl.find()) {
        return firstEl.group(1);
      }
    }
    return "\t\t\t";
  }

  private static int findMatchingChildObjectsOpen(String content, int closeTagAngleBracket) {
    int depth = 1;
    int limit = closeTagAngleBracket;
    while (depth > 0 && limit > 0) {
      int prevClose = content.lastIndexOf("</ChildObjects>", limit - 1);
      int prevOpen = content.lastIndexOf("<ChildObjects>", limit - 1);
      if (prevOpen < 0) {
        return -1;
      }
      if (prevClose < prevOpen) {
        depth--;
        if (depth == 0) {
          return prevOpen;
        }
        limit = prevOpen;
      } else {
        depth++;
        limit = prevClose;
      }
    }
    return -1;
  }

  private static boolean childRegionContainsTagEntry(String childRegion, String xmlTag, String objectName) {
    String q = Pattern.quote(objectName);
    String tq = Pattern.quote(xmlTag);
    Pattern p = Pattern.compile("<(?:[\\w.-]+:)?" + tq + ">\\s*" + q + "\\s*</(?:[\\w.-]+:)?" + tq + ">");
    return p.matcher(childRegion).find();
  }

  private static String escapeXmlText(String s) {
    if (s == null) {
      return "";
    }
    return s
      .replace("&", "&amp;")
      .replace("<", "&lt;")
      .replace(">", "&gt;")
      .replace("\"", "&quot;")
      .replace("'", "&apos;");
  }
}
