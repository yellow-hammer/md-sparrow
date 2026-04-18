/*
 * This file is a part of md-sparrow.
 *
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package io.github.yellowhammer.designerxml.cf;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Точечные мутации строк вида {@code <Tag>Name</Tag>} внутри {@code Configuration/ChildObjects}.
 */
final class ConfigurationChildObjectMutator {

  private ConfigurationChildObjectMutator() {
  }

  static void remove(Path configurationXml, String xmlTag, String objectName) throws IOException {
    Objects.requireNonNull(xmlTag, "xmlTag");
    Objects.requireNonNull(objectName, "objectName");
    if (xmlTag.isEmpty()) {
      throw new IllegalArgumentException("xmlTag must not be empty");
    }
    String content = Files.readString(configurationXml, StandardCharsets.UTF_8);
    Pattern linePattern = linePatternForTagAndName(xmlTag, objectName);
    Matcher matcher = linePattern.matcher(content);
    if (!matcher.find()) {
      throw new IllegalArgumentException(xmlTag + " not found in Configuration: " + objectName);
    }
    String updated = matcher.replaceFirst("");
    writeAtomically(configurationXml, updated);
  }

  static void rename(Path configurationXml, String xmlTag, String oldName, String newName) throws IOException {
    Objects.requireNonNull(oldName, "oldName");
    Objects.requireNonNull(newName, "newName");
    if (oldName.equals(newName)) {
      return;
    }
    String content = Files.readString(configurationXml, StandardCharsets.UTF_8);
    if (contains(content, xmlTag, newName)) {
      throw new IllegalArgumentException(xmlTag + " already in Configuration: " + newName);
    }
    Pattern linePattern = linePatternForTagAndName(xmlTag, oldName);
    Matcher matcher = linePattern.matcher(content);
    if (!matcher.find()) {
      throw new IllegalArgumentException(xmlTag + " not found in Configuration: " + oldName);
    }
    String indent = matcher.group(1) == null ? "" : matcher.group(1);
    String lineEnd = matcher.group(2) == null ? "" : matcher.group(2);
    String replacement = indent + "<" + xmlTag + ">" + escapeXmlText(newName) + "</" + xmlTag + ">" + lineEnd;
    String updated = matcher.replaceFirst(Matcher.quoteReplacement(replacement));
    writeAtomically(configurationXml, updated);
  }

  private static boolean contains(String content, String xmlTag, String objectName) {
    Pattern p = linePatternForTagAndName(xmlTag, objectName);
    return p.matcher(content).find();
  }

  private static Pattern linePatternForTagAndName(String xmlTag, String objectName) {
    String qTag = Pattern.quote(xmlTag);
    String qName = Pattern.quote(objectName);
    return Pattern.compile(
      "(?m)^(\\s*)<(?:[\\w.-]+:)?" + qTag + ">\\s*" + qName + "\\s*</(?:[\\w.-]+:)?" + qTag + ">(\\h*\\R?)");
  }

  private static String escapeXmlText(String s) {
    return s
      .replace("&", "&amp;")
      .replace("<", "&lt;")
      .replace(">", "&gt;")
      .replace("\"", "&quot;")
      .replace("'", "&apos;");
  }

  private static void writeAtomically(Path target, String text) throws IOException {
    Path parent = target.getParent();
    if (parent == null) {
      throw new IllegalArgumentException("target XML has no parent directory");
    }
    Path tmp = Files.createTempFile(parent, "cfg-child-mutate-", ".tmp");
    try {
      Files.writeString(tmp, text, StandardCharsets.UTF_8);
      try {
        Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
      } catch (AtomicMoveNotSupportedException e) {
        Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
      }
    } finally {
      try {
        Files.deleteIfExists(tmp);
      } catch (IOException ignore) {
        /* */
      }
    }
  }
}
