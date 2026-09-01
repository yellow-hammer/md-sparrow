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
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import javax.xml.stream.XMLStreamException;

import io.github.yellowhammer.edt.EdtObjectRegions.Region;

/**
 * Операции над объектом метаданных 1С:EDT целиком.
 *
 * Объект в EDT - это каталог со всем своим содержимым: описанием, модулями,
 * формами и макетами. Поэтому переименование двигает каталог, копирование
 * копирует его целиком с новыми идентификаторами, а удаление уносит вместе с
 * ним. Ссылка на объект в составе конфигурации правится тем же точечным
 * способом, что и любое другое свойство.
 */
public final class EdtObjectMutations {

  /** Идентификатор объекта или узла в файле. */
  private static final Pattern UUID_ATTRIBUTE = Pattern.compile("uuid=\"[0-9a-fA-F-]{36}\"");

  /** Идентификаторы порождаемых типов. */
  private static final Pattern TYPE_ID = Pattern.compile("(typeId|valueTypeId)=\"[0-9a-fA-F-]{36}\"");

  private EdtObjectMutations() {
  }

  /**
   * Переименовывает объект.
   *
   * @param configurationMdo файл конфигурации
   * @param objectMdo файл объекта
   * @param objectType вид объекта: {@code Catalog}
   * @param oldName текущее имя
   * @param newName новое имя
   * @throws IOException если файлы не читаются или не пишутся
   */
  public static void rename(
      Path configurationMdo,
      Path objectMdo,
      String objectType,
      String oldName,
      String newName) throws IOException {
    requireName(newName);
    requireObject(objectMdo, oldName);
    if (oldName.equals(newName)) {
      return;
    }

    Path objectDir = objectMdo.getParent();
    Path targetDir = objectDir.resolveSibling(newName);
    if (Files.exists(targetDir)) {
      throw new IllegalArgumentException("Объект уже есть: " + newName);
    }

    writeName(objectMdo, newName);
    Files.move(objectMdo, objectDir.resolve(newName + ".mdo"));
    Files.move(objectDir, targetDir);
    replaceReference(configurationMdo, objectType, oldName, newName);
  }

  /**
   * Копирует объект под новым именем.
   *
   * @param configurationMdo файл конфигурации
   * @param objectMdo файл копируемого объекта
   * @param objectType вид объекта
   * @param sourceName имя копируемого объекта
   * @param newName имя копии
   * @throws IOException если файлы не читаются или не пишутся
   */
  public static void duplicate(
      Path configurationMdo,
      Path objectMdo,
      String objectType,
      String sourceName,
      String newName) throws IOException {
    requireName(newName);
    requireObject(objectMdo, sourceName);

    Path objectDir = objectMdo.getParent();
    Path targetDir = objectDir.resolveSibling(newName);
    if (Files.exists(targetDir)) {
      throw new IllegalArgumentException("Объект уже есть: " + newName);
    }

    copyDirectory(objectDir, targetDir);
    Path copyMdo = targetDir.resolve(sourceName + ".mdo");
    Files.move(copyMdo, targetDir.resolve(newName + ".mdo"));
    Path renamed = targetDir.resolve(newName + ".mdo");
    // У копии свои идентификаторы: по ним платформа отличает объекты друг от друга
    Files.writeString(renamed, freshIdentifiers(Files.readString(renamed, StandardCharsets.UTF_8)),
        StandardCharsets.UTF_8);
    writeName(renamed, newName);
    appendReference(configurationMdo, objectType, newName);
  }

  /**
   * Удаляет объект.
   *
   * @param configurationMdo файл конфигурации
   * @param objectMdo файл объекта
   * @param objectType вид объекта
   * @param name имя объекта
   * @throws IOException если файлы не читаются или не пишутся
   */
  public static void delete(Path configurationMdo, Path objectMdo, String objectType, String name)
      throws IOException {
    requireObject(objectMdo, name);
    deleteDirectory(objectMdo.getParent());
    removeReference(configurationMdo, objectType, name);
  }

  /** Имя объекта в его описании. */
  private static void writeName(Path objectMdo, String name) throws IOException {
    String xml = Files.readString(objectMdo, StandardCharsets.UTF_8);
    try {
      Region region = EdtObjectRegions.property(xml, "name");
      if (!region.found()) {
        throw new IllegalArgumentException("В описании объекта нет имени: " + objectMdo);
      }
      Files.writeString(objectMdo,
          xml.substring(0, region.start()) + "<name>" + escape(name) + "</name>" + xml.substring(region.end()),
          StandardCharsets.UTF_8);
    } catch (XMLStreamException error) {
      throw new IOException("Не удалось разобрать описание объекта: " + objectMdo, error);
    }
  }

  /** Новые идентификаторы объекта и его узлов: копия не должна повторять исходник. */
  private static String freshIdentifiers(String xml) {
    String withUuids = replaceAll(UUID_ATTRIBUTE.matcher(xml), () -> "uuid=\"" + UUID.randomUUID() + "\"");
    Matcher types = TYPE_ID.matcher(withUuids);
    StringBuilder out = new StringBuilder();
    while (types.find()) {
      types.appendReplacement(out, Matcher.quoteReplacement(types.group(1) + "=\"" + UUID.randomUUID() + "\""));
    }
    types.appendTail(out);
    return out.toString();
  }

  private static String replaceAll(Matcher matcher, java.util.function.Supplier<String> value) {
    StringBuilder out = new StringBuilder();
    while (matcher.find()) {
      matcher.appendReplacement(out, Matcher.quoteReplacement(value.get()));
    }
    matcher.appendTail(out);
    return out.toString();
  }

  /** Ссылка на объект в составе конфигурации. */
  private static void replaceReference(Path configurationMdo, String objectType, String oldName, String newName)
      throws IOException {
    editConfiguration(configurationMdo, objectType, oldName, reference(objectType, newName), false);
  }

  private static void removeReference(Path configurationMdo, String objectType, String name) throws IOException {
    editConfiguration(configurationMdo, objectType, name, null, false);
  }

  private static void appendReference(Path configurationMdo, String objectType, String name) throws IOException {
    editConfiguration(configurationMdo, objectType, null, reference(objectType, name), true);
  }

  private static String reference(String objectType, String name) {
    return objectType + "." + escape(name);
  }

  /**
   * Правит состав конфигурации.
   *
   * @param anchorName имя объекта, чью ссылку правим; {@code null} при добавлении
   * @param value новое значение ссылки; {@code null} при удалении
   * @param append добавить ссылку последней среди своего вида
   */
  private static void editConfiguration(
      Path configurationMdo,
      String objectType,
      String anchorName,
      String value,
      boolean append) throws IOException {
    String xml = Files.readString(configurationMdo, StandardCharsets.UTF_8);
    try {
      String feature = featureOf(xml, objectType);
      List<Region> regions = EdtObjectRegions.properties(xml, feature);
      String eol = xml.contains("\r\n") ? "\r\n" : "\n";

      if (append) {
        int at = regions.isEmpty()
            ? EdtObjectRegions.lineStart(xml, xml.lastIndexOf("</"))
            : lineEnd(xml, regions.get(regions.size() - 1).end());
        String indent = regions.isEmpty() ? "  " : indentOf(xml, regions.get(regions.size() - 1).start());
        String element = indent + "<" + feature + ">" + value + "</" + feature + ">" + eol;
        Files.writeString(configurationMdo, xml.substring(0, at) + element + xml.substring(at),
            StandardCharsets.UTF_8);
        return;
      }

      Region target = referenceRegion(xml, regions, objectType, anchorName);
      if (target == null) {
        throw new IllegalArgumentException("В составе конфигурации нет объекта: " + anchorName);
      }
      if (value == null) {
        int start = EdtObjectRegions.lineStart(xml, target.start());
        int end = lineEnd(xml, target.end());
        Files.writeString(configurationMdo, xml.substring(0, start) + xml.substring(end), StandardCharsets.UTF_8);
        return;
      }
      Files.writeString(configurationMdo,
          xml.substring(0, target.start()) + "<" + feature + ">" + value + "</" + feature + ">"
              + xml.substring(target.end()),
          StandardCharsets.UTF_8);
    } catch (XMLStreamException error) {
      throw new IOException("Не удалось разобрать состав конфигурации: " + configurationMdo, error);
    }
  }

  /** Границы ссылки на объект среди одноимённых элементов состава. */
  private static Region referenceRegion(String xml, List<Region> regions, String objectType, String name) {
    String wanted = objectType + "." + name;
    for (Region region : regions) {
      String element = xml.substring(region.start(), region.end());
      int open = element.indexOf('>');
      int close = element.lastIndexOf("</");
      if (open >= 0 && close > open && element.substring(open + 1, close).trim().equals(wanted)) {
        return region;
      }
    }
    return null;
  }

  /** Имя элемента состава для вида объекта: его подсказывает сам файл конфигурации. */
  private static String featureOf(String xml, String objectType) throws XMLStreamException {
    for (String feature : EdtObjectRegions.propertyNames(xml)) {
      List<Region> regions = EdtObjectRegions.properties(xml, feature);
      for (Region region : regions) {
        String element = xml.substring(region.start(), region.end());
        int open = element.indexOf('>');
        int close = element.lastIndexOf("</");
        if (open >= 0 && close > open && element.substring(open + 1, close).trim().startsWith(objectType + ".")) {
          return feature;
        }
      }
    }
    throw new IllegalArgumentException("В конфигурации нет объектов вида " + objectType);
  }

  private static void copyDirectory(Path source, Path target) throws IOException {
    try (Stream<Path> files = Files.walk(source)) {
      for (Path file : files.toList()) {
        Path copy = target.resolve(source.relativize(file).toString());
        if (Files.isDirectory(file)) {
          Files.createDirectories(copy);
        } else {
          Files.createDirectories(copy.getParent());
          Files.copy(file, copy);
        }
      }
    }
  }

  private static void deleteDirectory(Path directory) throws IOException {
    try (Stream<Path> files = Files.walk(directory)) {
      List<Path> ordered = new ArrayList<>(files.toList());
      ordered.sort(Comparator.reverseOrder());
      for (Path file : ordered) {
        Files.deleteIfExists(file);
      }
    }
  }

  private static void requireObject(Path objectMdo, String name) {
    if (!Files.isRegularFile(objectMdo)) {
      throw new IllegalArgumentException("Файл объекта не найден: " + objectMdo);
    }
    if (name == null || name.isBlank()) {
      throw new IllegalArgumentException("Не задано имя объекта.");
    }
  }

  private static void requireName(String name) {
    if (name == null || name.isBlank()) {
      throw new IllegalArgumentException("Введите имя объекта.");
    }
  }

  private static int lineEnd(String xml, int end) {
    int line = xml.indexOf('\n', end);
    return line < 0 ? xml.length() : line + 1;
  }

  private static String indentOf(String xml, int start) {
    int line = EdtObjectRegions.lineStart(xml, start);
    int end = line;
    while (end < xml.length() && (xml.charAt(end) == ' ' || xml.charAt(end) == '\t')) {
      end++;
    }
    return xml.substring(line, end);
  }

  private static String escape(String value) {
    return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
  }
}
