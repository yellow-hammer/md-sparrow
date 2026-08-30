/*
 * This file is a part of md-sparrow.
 *
 * Copyright (c) 2026
 * Ivan Karlo <i.karlo@outlook.com> and contributors
 *
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package io.github.yellowhammer.designerxml.cf;

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
 * Права роли: {@code Roles/<Имя>/Ext/Rights.xml}.
 *
 * <p>Файл хранит только выданные права: отсутствие записи означает запрет при
 * выключенном «устанавливать права для новых объектов». Правка выдаёт право
 * записью и снимает его удалением записи; опустевший объект уходит целиком.
 */
public final class RoleRightsFile {

  private static final Pattern OBJECT_BLOCK = Pattern.compile(
    "\\t<object>\\s*<name>([^<]+)</name>(.*?)</object>\\r?\\n", Pattern.DOTALL);
  private static final Pattern RIGHT_BLOCK = Pattern.compile(
    "<right>\\s*<name>([^<]+)</name>\\s*<value>([^<]+)</value>\\s*</right>");
  private static final Pattern FLAG = Pattern.compile("<(\\w+)>(true|false)</\\1>");

  private RoleRightsFile() {
  }

  /** Право одного объекта. */
  public static final class RightEntry {
    public String name;
    public boolean value;

    public RightEntry() {
    }

    public RightEntry(String name, boolean value) {
      this.name = name;
      this.value = value;
    }
  }

  /** Права объекта. */
  public static final class ObjectRights {
    public String name;
    public List<RightEntry> rights = new ArrayList<>();
  }

  /** Содержимое файла прав. */
  public static final class Dto {
    public boolean setForNewObjects;
    public boolean setForAttributesByDefault;
    public boolean independentRightsOfChildObjects;
    public List<ObjectRights> objects = new ArrayList<>();
  }

  /** Правка: объект, право, выдать или снять. */
  public static final class Edit {
    public String object;
    public String right;
    public boolean value;
  }

  /** Путь к файлу прав рядом с XML роли. */
  public static Path rightsPath(Path roleXml) {
    Path normalized = roleXml.toAbsolutePath().normalize();
    String stem = normalized.getFileName().toString().replaceFirst("[.][Xx][Mm][Ll]$", "");
    return normalized.getParent().resolve(stem).resolve("Ext").resolve("Rights.xml");
  }

  public static Dto read(Path roleXml) throws IOException {
    Dto out = new Dto();
    Path file = rightsPath(roleXml);
    if (!Files.isRegularFile(file)) {
      return out;
    }
    String text = Files.readString(file, StandardCharsets.UTF_8);
    Matcher flags = FLAG.matcher(text.substring(0, Math.min(text.length(), 600)));
    while (flags.find()) {
      boolean value = Boolean.parseBoolean(flags.group(2));
      switch (flags.group(1)) {
        case "setForNewObjects" -> out.setForNewObjects = value;
        case "setForAttributesByDefault" -> out.setForAttributesByDefault = value;
        case "independentRightsOfChildObjects" -> out.independentRightsOfChildObjects = value;
        default -> {
        }
      }
    }
    Matcher objects = OBJECT_BLOCK.matcher(text);
    while (objects.find()) {
      ObjectRights item = new ObjectRights();
      item.name = objects.group(1).trim();
      Matcher rights = RIGHT_BLOCK.matcher(objects.group(2));
      while (rights.find()) {
        item.rights.add(new RightEntry(rights.group(1).trim(), Boolean.parseBoolean(rights.group(2).trim())));
      }
      out.objects.add(item);
    }
    return out;
  }

  /**
   * Применяет правки прав: право выдаётся записью, снимается удалением записи,
   * объект без оставшихся прав уходит из файла целиком.
   */
  public static void applyEdits(Path roleXml, List<Edit> edits) throws IOException {
    Path file = rightsPath(roleXml);
    if (!Files.isRegularFile(file)) {
      throw new IllegalArgumentException("У роли нет файла прав: " + file);
    }
    String text = Files.readString(file, StandardCharsets.UTF_8);
    String eol = text.contains("\r\n") ? "\r\n" : "\n";
    // Правки группируются по объекту: блок пересобирается один раз
    Map<String, List<Edit>> byObject = new LinkedHashMap<>();
    for (Edit edit : edits == null ? List.<Edit>of() : edits) {
      if (edit == null || edit.object == null || edit.right == null) {
        continue;
      }
      byObject.computeIfAbsent(edit.object.trim(), key -> new ArrayList<>()).add(edit);
    }
    for (Map.Entry<String, List<Edit>> entry : byObject.entrySet()) {
      text = applyObjectEdits(text, entry.getKey(), entry.getValue(), eol);
    }
    Dto verified = new Dto();
    Matcher check = OBJECT_BLOCK.matcher(text);
    while (check.find()) {
      verified.objects.add(new ObjectRights());
    }
    Files.writeString(file, text, StandardCharsets.UTF_8);
  }

  private static String applyObjectEdits(String text, String objectName, List<Edit> edits, String eol) {
    Map<String, Boolean> desired = new LinkedHashMap<>();
    Matcher objects = OBJECT_BLOCK.matcher(text);
    int start = -1;
    int end = -1;
    while (objects.find()) {
      if (objects.group(1).trim().equals(objectName)) {
        start = objects.start();
        end = objects.end();
        Matcher rights = RIGHT_BLOCK.matcher(objects.group(2));
        while (rights.find()) {
          desired.put(rights.group(1).trim(), Boolean.parseBoolean(rights.group(2).trim()));
        }
        break;
      }
    }
    for (Edit edit : edits) {
      if (edit.value) {
        desired.put(edit.right.trim(), true);
      } else {
        desired.remove(edit.right.trim());
      }
    }
    String block = desired.isEmpty() ? "" : objectBlock(objectName, desired, eol);
    if (start >= 0) {
      return text.substring(0, start) + block + text.substring(end);
    }
    if (block.isEmpty()) {
      return text;
    }
    int closing = text.lastIndexOf("</Rights>");
    if (closing < 0) {
      throw new IllegalArgumentException("Файл прав без корневого элемента Rights.");
    }
    return text.substring(0, closing) + block + text.substring(closing);
  }

  private static String objectBlock(String objectName, Map<String, Boolean> rights, String eol) {
    StringBuilder out = new StringBuilder();
    out.append("\t<object>").append(eol);
    out.append("\t\t<name>").append(escapeXml(objectName)).append("</name>").append(eol);
    for (Map.Entry<String, Boolean> right : rights.entrySet()) {
      out.append("\t\t<right>").append(eol);
      out.append("\t\t\t<name>").append(escapeXml(right.getKey())).append("</name>").append(eol);
      out.append("\t\t\t<value>").append(right.getValue()).append("</value>").append(eol);
      out.append("\t\t</right>").append(eol);
    }
    out.append("\t</object>").append(eol);
    return out.toString();
  }

  private static String escapeXml(String value) {
    return value
      .replace("&", "&amp;")
      .replace("<", "&lt;")
      .replace(">", "&gt;")
      .replace("\"", "&quot;");
  }
}
