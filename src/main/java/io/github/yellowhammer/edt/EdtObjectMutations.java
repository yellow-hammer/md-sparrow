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

import static io.github.yellowhammer.edt.EdtXmlText.escape;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import javax.xml.stream.XMLStreamException;

import io.github.yellowhammer.designerxml.cf.CatalogNameConstraints;
import io.github.yellowhammer.designerxml.cf.UiLabels;
import io.github.yellowhammer.edt.EdtObjectRegions.Region;

/**
 * Операции над объектом метаданных 1С:EDT целиком.
 *
 * Объект в EDT - это каталог со всем своим содержимым: описанием, модулями,
 * формами и макетами. Поэтому переименование двигает каталог, копирование
 * копирует его целиком с новыми идентификаторами, а удаление уносит вместе с
 * ним. Ссылка на объект в составе конфигурации правится тем же точечным
 * способом, что и любое другое свойство.
 *
 * Каталог не вернуть, поэтому до первой записи на диск объект сверяется с
 * тем, что о нём сказано: вид, имя, место файла и ссылка в составе. Отказ
 * оставляет проект нетронутым.
 */
public final class EdtObjectMutations {

  /** Класс конфигурации в схеме: по нему ищется элемент состава. */
  private static final String CONFIGURATION = "Configuration";

  /** Идентификатор объекта или узла и идентификаторы порождаемых типов. */
  private static final Pattern IDENTIFIER = Pattern.compile("\\b(uuid|typeId|valueTypeId)=\"([0-9a-fA-F-]{36})\"");

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
   * @throws IllegalArgumentException если объект не совпадает со сведениями о нём или имя занято
   */
  public static void rename(
      Path configurationMdo,
      Path objectMdo,
      String objectType,
      String oldName,
      String newName) throws IOException {
    requireNewName(newName);
    Located object = locate(configurationMdo, objectMdo, objectType, oldName);
    if (oldName.equals(newName)) {
      return;
    }

    Path objectDir = objectMdo.getParent();
    Path targetDir = objectDir.resolveSibling(newName);
    requireFree(object, targetDir, objectType, newName);

    // Всё новое собирается до первой записи: отказ посреди правки оставил бы
    // проект наполовину переименованным
    String configuration = object.replaced(reference(objectType, newName));
    String description = renamed(Files.readString(objectMdo, StandardCharsets.UTF_8), objectType, oldName, newName);
    Files.writeString(objectMdo, description, StandardCharsets.UTF_8);
    Files.move(objectMdo, objectDir.resolve(newName + ".mdo"));
    Files.move(objectDir, targetDir);
    Files.writeString(configurationMdo, configuration, StandardCharsets.UTF_8);
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
   * @throws IllegalArgumentException если объект не совпадает со сведениями о нём или имя занято
   */
  public static void duplicate(
      Path configurationMdo,
      Path objectMdo,
      String objectType,
      String sourceName,
      String newName) throws IOException {
    requireNewName(newName);
    Located source = locate(configurationMdo, objectMdo, objectType, sourceName);

    Path objectDir = objectMdo.getParent();
    Path targetDir = objectDir.resolveSibling(newName);
    requireFree(source, targetDir, objectType, newName);

    String configuration = source.appended(reference(objectType, newName));
    // У копии свои идентификаторы: по ним платформа отличает объекты друг от друга
    String seed = EdtObjectScaffold.seed("duplicate|" + objectType + "|" + sourceName + "|" + newName,
        source.configuration());
    String description = renamed(freshIdentifiers(Files.readString(objectMdo, StandardCharsets.UTF_8), seed),
        objectType, sourceName, newName);
    copyDirectory(objectDir, targetDir);
    Path copyMdo = targetDir.resolve(newName + ".mdo");
    Files.move(targetDir.resolve(sourceName + ".mdo"), copyMdo);
    Files.writeString(copyMdo, description, StandardCharsets.UTF_8);
    Files.writeString(configurationMdo, configuration, StandardCharsets.UTF_8);
  }

  /**
   * Удаляет объект.
   *
   * @param configurationMdo файл конфигурации
   * @param objectMdo файл объекта
   * @param objectType вид объекта
   * @param name имя объекта
   * @throws IOException если файлы не читаются или не пишутся
   * @throws IllegalArgumentException если объект не совпадает со сведениями о нём
   */
  public static void delete(Path configurationMdo, Path objectMdo, String objectType, String name)
      throws IOException {
    Located object = locate(configurationMdo, objectMdo, objectType, name);
    // Сначала состав, потом файлы: как у выгрузки
    Files.writeString(configurationMdo, object.removed(), StandardCharsets.UTF_8);
    deleteDirectory(objectMdo.getParent());
  }

  /**
   * Объект, сверенный с диском и с составом конфигурации.
   *
   * @param configuration текст конфигурации
   * @param feature элемент состава для вида объекта
   * @param references ссылки этого вида в составе, в порядке файла
   * @param reference ссылка на сам объект
   */
  private record Located(String configuration, String feature, List<Region> references, Region reference) {

    /** Конфигурация с другой ссылкой на месте прежней. */
    String replaced(String value) {
      return configuration.substring(0, reference.start()) + element(value) + configuration.substring(reference.end());
    }

    /** Конфигурация без ссылки на объект: её строка уходит целиком. */
    String removed() {
      int start = EdtObjectRegions.lineStart(configuration, reference.start());
      return configuration.substring(0, start) + configuration.substring(lineEnd(configuration, reference.end()));
    }

    /** Конфигурация с новой ссылкой последней среди своего вида. */
    String appended(String value) {
      Region last = references.get(references.size() - 1);
      int at = lineEnd(configuration, last.end());
      String eol = configuration.contains("\r\n") ? "\r\n" : "\n";
      return configuration.substring(0, at) + indentOf(configuration, last.start()) + element(value) + eol
          + configuration.substring(at);
    }

    private String element(String value) {
      return "<" + feature + ">" + value + "</" + feature + ">";
    }
  }

  /**
   * Сверяет объект со сведениями о нём до любой записи на диск.
   *
   * Каталог объекта уносит с собой модули, формы и макеты, поэтому операция идёт
   * только над тем объектом, о котором спрашивают: файл лежит в
   * {@code <Имя>/<Имя>.mdo}, описывает объект этого вида и с этим именем, а
   * ссылка на него есть в составе конфигурации.
   */
  private static Located locate(Path configurationMdo, Path objectMdo, String objectType, String name)
      throws IOException {
    if (!Files.isRegularFile(objectMdo)) {
      throw new IllegalArgumentException("Файл объекта не найден: " + objectMdo);
    }
    if (name == null || name.isBlank()) {
      throw new IllegalArgumentException("Не задано имя объекта.");
    }
    if (objectType == null || objectType.isBlank()) {
      throw new IllegalArgumentException("Не задан вид объекта.");
    }
    Path directory = objectMdo.getParent();
    if (directory == null || !directory.getFileName().toString().equals(name)
        || !objectMdo.getFileName().toString().equals(name + ".mdo")) {
      throw new IllegalArgumentException("Описание объекта " + name + " лежит в " + name + "/" + name
          + ".mdo, а не в " + objectMdo);
    }
    EdtObjectReader.EdtNode object = EdtObjectReader.read(objectMdo);
    if (!object.kind().equals(objectType) || !object.name().equals(name)) {
      throw new IllegalArgumentException("Файл описывает объект " + object.kind() + "." + object.name()
          + ", а не " + objectType + "." + name + ": " + objectMdo);
    }

    String xml = Files.readString(configurationMdo, StandardCharsets.UTF_8);
    try {
      String feature = featureOf(objectType);
      List<Region> references = EdtObjectRegions.properties(xml, feature);
      Region reference = referenceRegion(xml, references, objectType, name);
      if (reference == null) {
        throw new IllegalArgumentException("В составе конфигурации нет объекта: " + objectType + "." + name);
      }
      return new Located(xml, feature, references, reference);
    } catch (XMLStreamException error) {
      throw new IOException("Не удалось разобрать состав конфигурации: " + configurationMdo, error);
    }
  }

  /** Новое имя не занято ни каталогом, ни ссылкой в составе. */
  private static void requireFree(Located object, Path targetDir, String objectType, String newName) {
    if (Files.exists(targetDir)
        || referenceRegion(object.configuration(), object.references(), objectType, newName) != null) {
      throw new IllegalArgumentException(UiLabels.alreadyExists(objectType, newName));
    }
  }

  /**
   * Описание объекта под новым именем: его имя и ссылки вида
   * {@code Catalog.СтароеИмя} и {@code CatalogRef.СтароеИмя}. Текст синонима не
   * меняется: это отдельное свойство, а не путь к объекту.
   */
  private static String renamed(String xml, String objectType, String oldName, String newName) throws IOException {
    Region region;
    try {
      region = EdtObjectRegions.property(xml, "name");
    } catch (XMLStreamException error) {
      throw new IOException("Не удалось разобрать описание объекта " + oldName, error);
    }
    if (!region.found()) {
      throw new IllegalArgumentException("В описании объекта нет имени: " + oldName);
    }
    String named = xml.substring(0, region.start()) + "<name>" + escape(newName) + "</name>"
        + xml.substring(region.end());
    Pattern token = Pattern.compile("(?<![\\p{L}\\p{N}_])(" + Pattern.quote(objectType) + "[\\p{L}\\p{N}]*\\.)"
        + Pattern.quote(oldName) + "(?![\\p{L}\\p{N}_])");
    return token.matcher(named).replaceAll(match -> match.group(1) + newName);
  }

  /**
   * Новые идентификаторы объекта и его узлов: копия не должна повторять исходник.
   *
   * Меняются только идентификаторы самой копии, а не ссылки на другие объекты;
   * новый выводится из зерна и старого, поэтому повтор той же копии даёт те же.
   */
  private static String freshIdentifiers(String xml, String seed) {
    Matcher identifiers = IDENTIFIER.matcher(xml);
    StringBuilder out = new StringBuilder();
    while (identifiers.find()) {
      identifiers.appendReplacement(out, Matcher.quoteReplacement(identifiers.group(1) + "=\""
          + EdtObjectScaffold.derivedUuid(seed, identifiers.group(2)) + "\""));
    }
    identifiers.appendTail(out);
    return out.toString();
  }

  private static String reference(String objectType, String name) {
    return objectType + "." + escape(name);
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

  /**
   * Элемент состава для вида объекта по схеме.
   *
   * Ссылками записаны и свойства конфигурации: роли по умолчанию идут в файле
   * раньше состава, и первый элемент со ссылкой нужного вида был бы не тем.
   */
  private static String featureOf(String objectType) throws IOException {
    for (EdtModel.Composition item : EdtModel.bundled().composition(CONFIGURATION)) {
      if (!item.inline() && item.objectType().equals(objectType)) {
        return item.feature();
      }
    }
    throw new IllegalArgumentException("Схема конфигурации не знает вид объекта " + objectType);
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

  /** Новое имя - имя каталога объекта, поэтому оно проверяется как идентификатор. */
  private static void requireNewName(String name) {
    if (name == null || name.isBlank()) {
      throw new IllegalArgumentException("Введите имя объекта.");
    }
    CatalogNameConstraints.check(name);
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
}
