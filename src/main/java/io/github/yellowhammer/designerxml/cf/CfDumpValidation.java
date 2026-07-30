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
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Проверка целостности выгрузки: состав, ссылки, версии формата.
 *
 * <p>Проверяется то, что можно установить по самой выгрузке, без запуска платформы: объявленный
 * состав против файлов на диске, ссылки на объекты, единая версия формата и соответствие
 * {@code ConfigDumpInfo.xml} составу. Порядок объектов внутри типа не проверяется: конфигуратор
 * сортирует имена своим сравнением, которое не совпадает с обычным алфавитным.
 */
public final class CfDumpValidation {

  /** Каталог не похож на выгрузку: нет {@code Configuration.xml}. */
  public static final String KIND_CONFIGURATION_MISSING = "configuration-missing";
  /** Версия формата выгрузки не поддерживается md-sparrow. */
  public static final String KIND_VERSION_UNSUPPORTED = "version-unsupported";
  /** Объект объявлен в составе, файла нет. */
  public static final String KIND_MISSING_FILE = "missing-file";
  /** Файл объекта есть, в составе не объявлен. */
  public static final String KIND_ORPHAN_FILE = "orphan-file";
  /** Объект объявлен в составе дважды. */
  public static final String KIND_DUPLICATE_ENTRY = "duplicate-entry";
  /** Тип объекта неизвестен формату выгрузки. */
  public static final String KIND_UNKNOWN_TYPE = "unknown-type";
  /** Типы в составе идут не в том порядке, что задан схемой. */
  public static final String KIND_CHILD_OBJECTS_ORDER = "child-objects-order";
  /** Версия формата у объекта отличается от версии конфигурации. */
  public static final String KIND_VERSION_MISMATCH = "version-mismatch";
  /** Ссылка ведёт на объект, которого нет. */
  public static final String KIND_DANGLING_REFERENCE = "dangling-reference";
  /** Версия формата в {@code ConfigDumpInfo.xml} отличается от версии конфигурации. */
  public static final String KIND_DUMP_INFO_VERSION = "dump-info-version";
  /** В {@code ConfigDumpInfo.xml} записан объект, которого в выгрузке нет. */
  public static final String KIND_DUMP_INFO_EXTRA = "dump-info-extra";
  /** У файла объекта не читается версия формата: файл не похож на выгрузку объекта. */
  public static final String KIND_VERSION_UNREADABLE = "version-unreadable";

  /** Версия формата с корневого элемента; {@code <?xml version="1.0"?>} не подходит, потому и {@code [A-Za-z]}. */
  /** Версия формата в {@code ConfigDumpInfo.xml}: корень там не {@code MetaDataObject}. */
  private static final Pattern DUMP_INFO_VERSION =
    Pattern.compile("<ConfigDumpInfo\\b[^>]*\\sversion=\"([^\"]+)\"");
  private static final Pattern OBJECT_REFERENCE = Pattern.compile(">([A-Za-z][A-Za-z0-9]*)\\.([^<>\\s\"]+)<");
  private static final String CHILD_OBJECTS_OPEN = "<ChildObjects>";
  private static final String CHILD_OBJECTS_CLOSE = "</ChildObjects>";
  private static final Pattern CHILD_OBJECT = Pattern.compile("<(\\w+)>([^<]*)</\\1>");
  private static final Pattern DUMP_INFO_NAME = Pattern.compile("<Metadata name=\"([^\"]+)\"");
  /** Корневой элемент с десятком объявлений пространств имён не длиннее этого; версия - в его конце. */
  private static final int HEAD_CHARS = 4096;

  private CfDumpValidation() {
  }

  /**
   * Проверяет выгрузку конфигурации или расширения.
   *
   * @param cfRoot каталог выгрузки, где лежит {@code Configuration.xml}
   * @return находки в порядке проверок; пустой список - выгрузка цела
   */
  public static List<CfDumpFinding> validate(Path cfRoot) throws IOException {
    List<CfDumpFinding> findings = new ArrayList<>();
    Path configurationXml = cfRoot.resolve(CfLayout.CONFIGURATION_XML);
    if (!Files.isRegularFile(configurationXml)) {
      findings.add(CfDumpFinding.of(
        CfLayout.CONFIGURATION_XML,
        KIND_CONFIGURATION_MISSING,
        "нет " + CfLayout.CONFIGURATION_XML + ": каталог не похож на выгрузку"));
      return findings;
    }

    String configurationText = Files.readString(configurationXml, StandardCharsets.UTF_8);
    String versionAttribute = metaDataObjectVersion(configurationXml).orElse("");
    Optional<SchemaVersion> version = SchemaVersion.byVersionAttribute(versionAttribute);
    if (version.isEmpty()) {
      findings.add(CfDumpFinding.of(
        CfLayout.CONFIGURATION_XML,
        KIND_VERSION_UNSUPPORTED,
        "версия формата выгрузки " + (versionAttribute.isEmpty() ? "не указана" : versionAttribute)
          + " не поддерживается"));
      return findings;
    }

    List<ChildObjectEntry> declared = readDeclared(configurationText);
    checkTypes(declared, findings);
    Set<String> declaredKeys = checkDuplicates(declared, findings);
    checkDeclaredFiles(cfRoot, declared, findings);
    checkOrphanFiles(cfRoot, declared, findings);
    checkObjectVersions(cfRoot, declared, versionAttribute, findings);
    checkReferences(cfRoot, configurationText, declaredKeys, findings);
    checkDumpInfo(cfRoot, declaredKeys, versionAttribute, findings);
    return findings;
  }

  /**
   * Состав в том порядке, в каком он записан в файле.
   *
   * <p>Читается из текста, а не через JAXB: модель отдаёт объекты по порядку схемы и молча
   * выбрасывает неизвестные элементы, то есть ровно то, что проверка и должна увидеть.
   */
  private static List<ChildObjectEntry> readDeclared(String configurationText) {
    int start = configurationText.indexOf(CHILD_OBJECTS_OPEN);
    int end = configurationText.indexOf(CHILD_OBJECTS_CLOSE);
    if (start < 0 || end < start) {
      return List.of();
    }
    String body = configurationText.substring(start + CHILD_OBJECTS_OPEN.length(), end);
    List<ChildObjectEntry> declared = new ArrayList<>();
    Matcher matcher = CHILD_OBJECT.matcher(body);
    while (matcher.find()) {
      String name = matcher.group(2).trim();
      if (!name.isEmpty()) {
        declared.add(new ChildObjectEntry(matcher.group(1), name));
      }
    }
    return declared;
  }

  /** Неизвестные типы и нарушенный порядок типов в составе. */
  private static void checkTypes(List<ChildObjectEntry> declared, List<CfDumpFinding> findings) {
    List<String> order = ConfigurationChildObjectsOrder.tagOrder();
    String previousType = null;
    int previousIndex = -1;
    Set<String> reportedUnknown = new HashSet<>();
    for (ChildObjectEntry entry : declared) {
      int index = order.indexOf(entry.objectType());
      if (index < 0) {
        if (reportedUnknown.add(entry.objectType())) {
          findings.add(CfDumpFinding.ofObject(
            CfLayout.CONFIGURATION_XML, entry.objectType(), entry.name(), KIND_UNKNOWN_TYPE,
            "неизвестный тип объекта в составе: " + entry.objectType()));
        }
        continue;
      }
      if (entry.objectType().equals(previousType)) {
        continue;
      }
      if (index < previousIndex) {
        findings.add(CfDumpFinding.ofObject(
          CfLayout.CONFIGURATION_XML, entry.objectType(), entry.name(), KIND_CHILD_OBJECTS_ORDER,
          "тип " + entry.objectType() + " стоит после " + previousType
            + ", а по схеме формата должен идти раньше"));
      }
      previousType = entry.objectType();
      previousIndex = index;
    }
  }

  /**
   * Повторы в составе.
   *
   * @return ключи объявленных объектов вида {@code Catalog.Валюты}
   */
  private static Set<String> checkDuplicates(List<ChildObjectEntry> declared, List<CfDumpFinding> findings) {
    Set<String> keys = new LinkedHashSet<>();
    for (ChildObjectEntry entry : declared) {
      String key = entry.objectType() + "." + entry.name();
      if (!keys.add(key)) {
        findings.add(CfDumpFinding.ofObject(
          CfLayout.CONFIGURATION_XML, entry.objectType(), entry.name(), KIND_DUPLICATE_ENTRY,
          "объект объявлен в составе дважды: " + key));
      }
    }
    return keys;
  }

  /** Объявленные объекты, которых нет на диске. */
  private static void checkDeclaredFiles(Path cfRoot, List<ChildObjectEntry> declared, List<CfDumpFinding> findings)
    throws IOException {
    for (ChildObjectEntry entry : declared) {
      if (CfObjectPathResolver.objectXml(cfRoot, entry.objectType(), entry.name()).isPresent()) {
        continue;
      }
      String subdir = CfObjectPathResolver.subdirsByType().get(entry.objectType());
      String expected = subdir == null ? "" : subdir + "/" + entry.name() + ".xml";
      findings.add(CfDumpFinding.ofObject(
        expected, entry.objectType(), entry.name(), KIND_MISSING_FILE,
        "объект объявлен в составе, файла нет: " + entry.objectType() + "." + entry.name()));
    }
  }

  /** Файлы объектов, не объявленные в составе. */
  private static void checkOrphanFiles(Path cfRoot, List<ChildObjectEntry> declared, List<CfDumpFinding> findings)
    throws IOException {
    Map<String, Set<String>> namesByType = new HashMap<>();
    for (ChildObjectEntry entry : declared) {
      namesByType.computeIfAbsent(entry.objectType(), type -> new HashSet<>()).add(entry.name());
    }
    for (Map.Entry<String, String> type : new TreeMap<>(CfObjectPathResolver.subdirsByType()).entrySet()) {
      Path dir = cfRoot.resolve(type.getValue());
      if (!Files.isDirectory(dir)) {
        continue;
      }
      Set<String> declaredNames = namesByType.getOrDefault(type.getKey(), Set.of());
      for (String name : xmlNamesIn(dir)) {
        if (declaredNames.contains(name)) {
          continue;
        }
        findings.add(CfDumpFinding.ofObject(
          type.getValue() + "/" + name + ".xml", type.getKey(), name, KIND_ORPHAN_FILE,
          "файл объекта есть, в составе не объявлен: " + type.getKey() + "." + name));
      }
    }
  }

  /** Версия формата объектов против версии конфигурации. */
  private static void checkObjectVersions(
    Path cfRoot, List<ChildObjectEntry> declared, String expectedVersion, List<CfDumpFinding> findings)
    throws IOException {
    for (ChildObjectEntry entry : declared) {
      Optional<Path> file = CfObjectPathResolver.objectXml(cfRoot, entry.objectType(), entry.name());
      if (file.isEmpty()) {
        continue;
      }
      Optional<String> version = metaDataObjectVersion(file.get());
      if (version.isEmpty()) {
        findings.add(CfDumpFinding.ofObject(
          relative(cfRoot, file.get()), entry.objectType(), entry.name(), KIND_VERSION_UNREADABLE,
          "у файла объекта не читается версия формата"));
        continue;
      }
      if (version.get().equals(expectedVersion)) {
        continue;
      }
      findings.add(CfDumpFinding.ofObject(
        relative(cfRoot, file.get()), entry.objectType(), entry.name(), KIND_VERSION_MISMATCH,
        "версия формата " + version.get() + " не совпадает с версией конфигурации " + expectedVersion));
    }
  }

  /**
   * Ссылки на объекты из {@code Configuration.xml} и составов подсистем.
   *
   * <p>Ссылка вида {@code Role.ПолныеПрава} считается целой, если объект есть на диске: вложенные
   * подсистемы в состав конфигурации не входят, но ссылаться на них можно.
   */
  private static void checkReferences(
    Path cfRoot, String configurationText, Set<String> declaredKeys, List<CfDumpFinding> findings)
    throws IOException {
    Map<String, Boolean> resolved = new HashMap<>();
    checkReferencesIn(cfRoot, CfLayout.CONFIGURATION_XML, configurationText, declaredKeys, resolved, findings);
    Path subsystems = cfRoot.resolve("Subsystems");
    if (!Files.isDirectory(subsystems)) {
      return;
    }
    List<Path> files;
    try (Stream<Path> walk = Files.walk(subsystems)) {
      files = walk
        .filter(p -> Files.isRegularFile(p) && p.getFileName().toString().endsWith(".xml"))
        .sorted()
        .toList();
    }
    for (Path file : files) {
      checkReferencesIn(
        cfRoot, relative(cfRoot, file), Files.readString(file, StandardCharsets.UTF_8),
        declaredKeys, resolved, findings);
    }
  }

  private static void checkReferencesIn(
    Path cfRoot,
    String path,
    String text,
    Set<String> declaredKeys,
    Map<String, Boolean> resolved,
    List<CfDumpFinding> findings) throws IOException {
    Set<String> seen = new LinkedHashSet<>();
    Matcher matcher = OBJECT_REFERENCE.matcher(text);
    while (matcher.find()) {
      String type = matcher.group(1);
      if (!CfObjectPathResolver.subdirsByType().containsKey(type)) {
        continue;
      }
      String name = matcher.group(2).split("\\.", 2)[0];
      String key = type + "." + name;
      if (!seen.add(key) || declaredKeys.contains(key)) {
        continue;
      }
      Boolean exists = resolved.get(key);
      if (exists == null) {
        exists = CfObjectPathResolver.objectXml(cfRoot, type, name).isPresent();
        resolved.put(key, exists);
      }
      if (!exists) {
        findings.add(CfDumpFinding.ofObject(
          path, type, name, KIND_DANGLING_REFERENCE, "ссылка ведёт на объект, которого нет: " + key));
      }
    }
  }

  /** Версия и состав {@code ConfigDumpInfo.xml}. */
  private static void checkDumpInfo(
    Path cfRoot, Set<String> declaredKeys, String expectedVersion, List<CfDumpFinding> findings) throws IOException {
    Path dumpInfo = cfRoot.resolve(CfLayout.CONFIG_DUMP_INFO_XML);
    if (!Files.isRegularFile(dumpInfo)) {
      return;
    }
    String text = Files.readString(dumpInfo, StandardCharsets.UTF_8);
    dumpInfoVersion(text)
      .filter(version -> !version.equals(expectedVersion))
      .ifPresent(version -> findings.add(CfDumpFinding.of(
        CfLayout.CONFIG_DUMP_INFO_XML, KIND_DUMP_INFO_VERSION,
        "версия формата " + version + " не совпадает с версией конфигурации " + expectedVersion)));

    Set<String> seen = new LinkedHashSet<>();
    Matcher matcher = DUMP_INFO_NAME.matcher(text);
    while (matcher.find()) {
      String name = matcher.group(1);
      String[] parts = name.split("\\.");
      if (parts.length != 2 || !CfObjectPathResolver.subdirsByType().containsKey(parts[0])) {
        continue;
      }
      if (declaredKeys.contains(name) || !seen.add(name)) {
        continue;
      }
      if (CfObjectPathResolver.objectXml(cfRoot, parts[0], parts[1]).isEmpty()) {
        findings.add(CfDumpFinding.ofObject(
          CfLayout.CONFIG_DUMP_INFO_XML, parts[0], parts[1], KIND_DUMP_INFO_EXTRA,
          "в " + CfLayout.CONFIG_DUMP_INFO_XML + " записан объект, которого в выгрузке нет: " + name));
      }
    }
  }

  private static List<String> xmlNamesIn(Path dir) throws IOException {
    try (Stream<Path> entries = Files.list(dir)) {
      return entries
        .filter(Files::isRegularFile)
        .map(p -> p.getFileName().toString())
        .filter(fileName -> fileName.endsWith(".xml"))
        .map(fileName -> fileName.substring(0, fileName.length() - ".xml".length()))
        .sorted()
        .toList();
    }
  }

  /** Версия формата у корневого {@code MetaDataObject}; пусто, если файл на выгрузку не похож. */
  private static Optional<String> metaDataObjectVersion(Path file) {
    try {
      return Optional.of(MetaDataObjectHeadReader.readMetaDataObjectVersion(file));
    } catch (IOException e) {
      return Optional.empty();
    }
  }

  private static Optional<String> dumpInfoVersion(String xml) {
    Matcher matcher = DUMP_INFO_VERSION.matcher(xml);
    return matcher.find() ? Optional.of(matcher.group(1)) : Optional.empty();
  }

  private static String relative(Path cfRoot, Path file) {
    return cfRoot.relativize(file).toString().replace('\\', '/');
  }
}
