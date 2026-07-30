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

import jakarta.xml.bind.JAXBException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Приводит {@code ConfigDumpInfo.xml} в соответствие составу выгрузки.
 *
 * <p>Служебный файл хранит версии объектов и нужен платформе при загрузке конфигурации из файлов.
 * Если добавить или удалить объект и не тронуть его, состав выгрузки и служебный файл расходятся.
 * Поэтому каждая мутация состава заканчивается сверкой: записи объектов, которых в составе больше
 * нет, убираются, для новых объектов записи добавляются.
 *
 * <p>Записи существующих объектов переносятся как есть, вместе с вложенными: их версии считает
 * платформа, и трогать их нельзя. У новой записи версия заполняется нулями: настоящее значение
 * платформа выводит из своего хранилища конфигурации, а не из XML, и придумывать его нельзя -
 * совпадение с настоящим означало бы, что платформа сочтёт новый объект уже загруженным.
 */
public final class ConfigDumpInfoSync {

  /** Версия неизвестна: значение заведомо не совпадает ни с одной настоящей версией объекта. */
  static final String UNKNOWN_CONFIG_VERSION = "0".repeat(40);

  private static final String CONFIG_VERSIONS_EMPTY = "<ConfigVersions/>";
  private static final String CONFIG_VERSIONS_OPEN = "<ConfigVersions>";
  private static final String CONFIG_VERSIONS_CLOSE = "</ConfigVersions>";
  private static final String ROOT_RECORD_PREFIX = "Configuration.";
  private static final String METADATA_OPEN = "<Metadata ";
  private static final String METADATA_CLOSE = "</Metadata>";
  private static final Pattern METADATA_NAME = Pattern.compile("<Metadata name=\"([^\"]+)\"");
  private static final Pattern UUID_ATTRIBUTE = Pattern.compile("uuid=\"([^\"]+)\"");

  private ConfigDumpInfoSync() {
  }

  /**
   * Сверяет {@code ConfigDumpInfo.xml} с составом из {@code Configuration.xml}.
   *
   * <p>Если служебного файла в выгрузке нет, сверять нечего: платформа соберёт его сама при
   * следующей выгрузке.
   *
   * @param cfRoot каталог выгрузки
   */
  public static void sync(Path cfRoot) throws IOException {
    Path dumpInfo = cfRoot.resolve(CfLayout.CONFIG_DUMP_INFO_XML);
    Path configurationXml = cfRoot.resolve(CfLayout.CONFIGURATION_XML);
    if (!Files.isRegularFile(dumpInfo) || !Files.isRegularFile(configurationXml)) {
      return;
    }
    String text = Files.readString(dumpInfo, StandardCharsets.UTF_8);
    String updated = reconcile(text, declaredKeys(configurationXml), cfRoot);
    if (!updated.equals(text)) {
      Files.writeString(dumpInfo, updated, StandardCharsets.UTF_8);
    }
  }

  /** Ключи объявленных объектов вида {@code Catalog.Валюты} в порядке состава. */
  private static Set<String> declaredKeys(Path configurationXml) throws IOException {
    SchemaVersion version = SupportedSchemaVersions.requireSupported(
      MetaDataObjectHeadReader.readMetaDataObjectVersion(configurationXml));
    List<ChildObjectEntry> declared;
    try {
      declared = ConfigurationChildObjectsExtractor.readChildObjects(configurationXml, version);
    } catch (JAXBException e) {
      throw new IOException("не удалось прочитать состав конфигурации: " + e.getMessage(), e);
    }
    Set<String> keys = new LinkedHashSet<>();
    for (ChildObjectEntry entry : declared) {
      keys.add(entry.objectType() + "." + entry.name());
    }
    return keys;
  }

  /**
   * Переписывает {@code ConfigVersions}: оставляет записи объявленных объектов в прежнем порядке,
   * убирает записи исчезнувших, дописывает записи новых.
   */
  private static String reconcile(String text, Set<String> declaredKeys, Path cfRoot) throws IOException {
    int start = text.indexOf(CONFIG_VERSIONS_OPEN);
    int end = text.indexOf(CONFIG_VERSIONS_CLOSE);
    boolean empty = start < 0 || end < start;
    if (empty && !text.contains(CONFIG_VERSIONS_EMPTY)) {
      return text;
    }
    String body = empty ? "" : text.substring(start + CONFIG_VERSIONS_OPEN.length(), end);
    Map<String, String> blocks = topLevelBlocks(body);
    String indent = detectIndent(body);

    StringBuilder rebuilt = new StringBuilder();
    for (Map.Entry<String, String> block : blocks.entrySet()) {
      if (keepsPlace(block.getKey(), declaredKeys)) {
        rebuilt.append(indent).append(block.getValue().strip()).append('\n');
      }
    }
    for (String key : declaredKeys) {
      if (blocks.containsKey(key)) {
        continue;
      }
      rebuilt.append(indent).append(newEntry(key, cfRoot)).append('\n');
    }

    if (rebuilt.length() == 0) {
      return empty ? text : text.substring(0, start) + CONFIG_VERSIONS_EMPTY
        + text.substring(end + CONFIG_VERSIONS_CLOSE.length());
    }
    String rendered = CONFIG_VERSIONS_OPEN + "\n" + rebuilt + closingIndent(indent) + CONFIG_VERSIONS_CLOSE;
    return empty
      ? text.replace(CONFIG_VERSIONS_EMPTY, rendered)
      : text.substring(0, start) + rendered + text.substring(end + CONFIG_VERSIONS_CLOSE.length());
  }

  /**
   * Остаётся ли запись после сверки.
   *
   * <p>Кроме записей самих объектов платформа держит на верхнем уровне записи их частей -
   * {@code Catalog.Валюты.Form.ФормаСписка}, {@code Catalog.Валюты.Help} - и запись самой
   * конфигурации, которой в {@code ChildObjects} нет и быть не может. Часть остаётся вместе
   * со своим объектом и уходит вместе с ним.
   */
  private static boolean keepsPlace(String name, Set<String> declaredKeys) {
    if (name.startsWith(ROOT_RECORD_PREFIX)) {
      return true;
    }
    return declaredKeys.contains(ownerKey(name));
  }

  /** Объект, которому принадлежит запись: первые две части имени. */
  private static String ownerKey(String name) {
    int first = name.indexOf('.');
    if (first < 0) {
      return name;
    }
    int second = name.indexOf('.', first + 1);
    return second < 0 ? name : name.substring(0, second);
  }

  /** Записи верхнего уровня по имени объекта; вложенные записи остаются внутри своей. */
  private static Map<String, String> topLevelBlocks(String body) {
    Map<String, String> blocks = new LinkedHashMap<>();
    int cursor = 0;
    while (true) {
      int open = body.indexOf(METADATA_OPEN, cursor);
      if (open < 0) {
        return blocks;
      }
      int tagEnd = body.indexOf('>', open);
      if (tagEnd < 0) {
        return blocks;
      }
      int blockEnd;
      if (body.charAt(tagEnd - 1) == '/') {
        blockEnd = tagEnd + 1;
      } else {
        blockEnd = closingTagEnd(body, tagEnd + 1);
        if (blockEnd < 0) {
          return blocks;
        }
      }
      String block = body.substring(open, blockEnd);
      Matcher name = METADATA_NAME.matcher(block);
      if (name.find()) {
        blocks.put(name.group(1), block);
      }
      cursor = blockEnd;
    }
  }

  /** Конец записи с вложенными: ищет свой {@code </Metadata>}, считая вложенные открытия. */
  private static int closingTagEnd(String body, int from) {
    int depth = 1;
    int cursor = from;
    while (depth > 0) {
      int open = body.indexOf(METADATA_OPEN, cursor);
      int close = body.indexOf(METADATA_CLOSE, cursor);
      if (close < 0) {
        return -1;
      }
      if (open >= 0 && open < close) {
        int tagEnd = body.indexOf('>', open);
        if (tagEnd < 0) {
          return -1;
        }
        if (body.charAt(tagEnd - 1) != '/') {
          depth++;
        }
        cursor = tagEnd + 1;
        continue;
      }
      depth--;
      cursor = close + METADATA_CLOSE.length();
    }
    return cursor;
  }

  private static String newEntry(String key, Path cfRoot) throws IOException {
    String[] parts = key.split("\\.", 2);
    String id = objectUuid(cfRoot, parts[0], parts[1]).orElse("");
    return "<Metadata name=\"" + key + "\" id=\"" + id
      + "\" configVersion=\"" + UNKNOWN_CONFIG_VERSION + "\"/>";
  }

  /** Идентификатор объекта берётся из его же файла: в служебном файле он должен быть тот же. */
  private static Optional<String> objectUuid(Path cfRoot, String objectType, String name) throws IOException {
    Optional<Path> file = CfObjectPathResolver.objectXml(cfRoot, objectType, name);
    if (file.isEmpty()) {
      return Optional.empty();
    }
    List<String> head = new ArrayList<>();
    try (var reader = Files.newBufferedReader(file.get(), StandardCharsets.UTF_8)) {
      for (String line = reader.readLine(); line != null && head.size() < 20; line = reader.readLine()) {
        head.add(line);
      }
    }
    Matcher matcher = UUID_ATTRIBUTE.matcher(String.join("\n", head));
    return matcher.find() ? Optional.of(matcher.group(1)) : Optional.empty();
  }

  private static String detectIndent(String body) {
    Matcher matcher = Pattern.compile("(?m)^([\t ]+)<Metadata ").matcher(body);
    return matcher.find() ? matcher.group(1) : "\t\t";
  }

  private static String closingIndent(String indent) {
    return indent.isEmpty() ? "" : indent.substring(0, Math.max(0, indent.length() - 1));
  }
}
