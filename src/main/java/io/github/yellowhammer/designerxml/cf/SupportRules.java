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
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Правила поддержки поставщика: {@code Ext/ParentConfigurations.bin} выгрузки.
 *
 * <p>Файл текстовый, скобочного формата: заголовок с поставщиком и версией,
 * затем записи «признак, режим, uuid, uuid» по каждому объекту. Объект с
 * режимом {@code 0} менять нельзя - правка разъехалась бы с правилами
 * поставщика; изменение самих правил остаётся конфигуратору, пока запись не
 * выверена byte-в-byte на его выгрузках.
 */
public final class SupportRules {

  private static final Pattern HEADER = Pattern.compile(
    "^\\{\\d+,\\d+,\\d+,[0-9a-f-]+,\\d+,[0-9a-f-]+,\"([^\"]*)\",\"(.*?)\",\"([^\"]*)\",(\\d+),");
  private static final Pattern ENTRY = Pattern.compile(
    "(\\d+),(\\d+),([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}),\\3");
  private static final Pattern OBJECT_UUID = Pattern.compile("<[A-Za-z]+ uuid=\"([0-9a-fA-F-]+)\">");

  /** Кэш разбора: файл большой, а guard дёргает его на каждую мутацию. */
  private static final Map<String, CacheEntry> CACHE = new ConcurrentHashMap<>();

  private SupportRules() {
  }

  /** Свод правил поддержки одной выгрузки. */
  public static final class Rules {
    /** Поставщик из заголовка. */
    public String vendor = "";
    /** Версия поставки. */
    public String version = "";
    /** Имя конфигурации поставщика. */
    public String name = "";
    /** Режим по uuid объекта: 0 - изменение запрещено. */
    public Map<String, Integer> modeByUuid = new HashMap<>();

    public boolean isEmpty() {
      return modeByUuid.isEmpty();
    }
  }

  private record CacheEntry(long modified, long size, Rules rules) {
  }

  /** Файл правил рядом с Configuration.xml выгрузки. */
  public static Path rulesPath(Path configurationRoot) {
    return configurationRoot.resolve("Ext").resolve("ParentConfigurations.bin");
  }

  /**
   * Читает правила поддержки выгрузки; пустые правила, когда файла нет.
   *
   * @param configurationRoot каталог с Configuration.xml
   */
  public static Rules read(Path configurationRoot) throws IOException {
    Path file = rulesPath(configurationRoot);
    if (!Files.isRegularFile(file)) {
      return new Rules();
    }
    String key = file.toAbsolutePath().normalize().toString();
    long modified = Files.getLastModifiedTime(file).toMillis();
    long size = Files.size(file);
    CacheEntry cached = CACHE.get(key);
    if (cached != null && cached.modified == modified && cached.size == size) {
      return cached.rules;
    }
    Rules rules = parse(Files.readString(file, StandardCharsets.UTF_8));
    CACHE.put(key, new CacheEntry(modified, size, rules));
    return rules;
  }

  static Rules parse(String text) {
    Rules rules = new Rules();
    String body = text.startsWith("﻿") ? text.substring(1) : text;
    Matcher header = HEADER.matcher(body);
    if (header.find()) {
      rules.version = header.group(1);
      rules.vendor = header.group(2).replace("\"\"", "\"");
      rules.name = header.group(3);
    }
    Matcher entry = ENTRY.matcher(body);
    while (entry.find()) {
      rules.modeByUuid.put(entry.group(3), Integer.parseInt(entry.group(2)));
    }
    return rules;
  }

  /**
   * Проверяет, что объект можно менять; иначе бросает с внятным текстом.
   *
   * <p>Корень выгрузки ищется от файла объекта вверх; правила без записи об
   * объекте изменению не мешают.
   */
  public static void ensureEditable(Path objectXml) throws IOException {
    Path normalized = objectXml.toAbsolutePath().normalize();
    Path root = findConfigurationRoot(normalized);
    if (root == null) {
      return;
    }
    Rules rules = read(root);
    if (rules.isEmpty()) {
      return;
    }
    String uuid = objectUuid(normalized);
    if (uuid == null) {
      return;
    }
    Integer mode = rules.modeByUuid.get(uuid.toLowerCase());
    if (mode != null && mode == 0) {
      throw new IllegalStateException(
        "Объект на поддержке поставщика «" + rules.vendor
          + "» без возможности изменения. Включите возможность изменения в конфигураторе"
          + " или снимите объект с поддержки.");
    }
  }

  private static String objectUuid(Path objectXml) throws IOException {
    try (var lines = Files.lines(objectXml, StandardCharsets.UTF_8)) {
      return lines
        .limit(8)
        .map(OBJECT_UUID::matcher)
        .filter(Matcher::find)
        .map(matcher -> matcher.group(1))
        .findFirst()
        .orElse(null);
    }
  }

  private static Path findConfigurationRoot(Path objectXml) {
    Path current = objectXml.getParent();
    for (int depth = 0; current != null && depth < 6; depth++) {
      if (Files.isRegularFile(current.resolve("Configuration.xml"))) {
        return current;
      }
      current = current.getParent();
    }
    return null;
  }
}
