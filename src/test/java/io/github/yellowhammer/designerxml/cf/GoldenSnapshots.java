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

import io.github.yellowhammer.designerxml.SchemaVersion;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Эталоны выгрузки платформы из submodule {@code fixtures/samples-1c-platform}: {@code snapshots/<формат>/…}.
 */
final class GoldenSnapshots {

  /** Голые объекты конфигурации. */
  static final String CF = "cf-bare-objects";

  /** Пустое расширение. */
  static final String CFE = "cfe-empty";

  /** Выгрузка пустой базы платформы (снимается workflow golden-snapshots). */
  static final String EMPTY_INFOBASE = "cf-empty-infobase";

  /** Объекты-владельцы с дочерним узлом каждого вида. */
  static final String NODES = "cf-object-nodes";

  /** Голые внешние отчёт и обработка ({@code <имя>/<имя>.xml}). */
  static final String EXTERNAL = "external-files/empty";

  /** Внешние отчёт и обработка с формами. */
  static final String EXTERNAL_FULL = "external-files/empty-full-objects";

  private static final Pattern UUID = Pattern.compile(
    "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");

  private GoldenSnapshots() {
  }

  /** Каталог эталонов формата. */
  static Path format(SchemaVersion version) {
    String root = System.getProperty("samples.root");
    assertThat(root).as("системное свойство samples.root").isNotBlank();
    Path snapshots = Path.of(root, "snapshots");
    assertThat(snapshots).isDirectory();
    return snapshots.resolve(version.metadataObjectVersionAttribute());
  }

  /** Канонический формат - самый новый, у которого есть голые объекты (то же правило, что в сборке). */
  static SchemaVersion canonical() {
    SchemaVersion canonical = null;
    for (SchemaVersion version : SchemaVersion.values()) {
      if (Files.isDirectory(format(version).resolve(CF))) {
        canonical = version;
      }
    }
    assertThat(canonical).as("эталоны %s в samples-1c-platform", CF).isNotNull();
    return canonical;
  }

  /** Файлы набора {@code set} формата: относительный путь через {@code /}; пусто, если набора нет. */
  static List<String> files(SchemaVersion version, String set) {
    Path base = format(version).resolve(set);
    if (!Files.isDirectory(base)) {
      return List.of();
    }
    try (Stream<Path> walk = Files.walk(base)) {
      return walk.filter(Files::isRegularFile)
        .filter(path -> path.getFileName().toString().endsWith(".xml"))
        .map(path -> base.relativize(path).toString().replace('\\', '/'))
        .sorted()
        .toList();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /** Текст файла как есть: BOM остаётся символом U+FEFF, переводы строк не меняются. */
  static String read(Path file) {
    try {
      return new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  static String read(SchemaVersion version, String set, String relative) {
    return read(format(version).resolve(set).resolve(relative));
  }

  /**
   * UUID по порядку появления заменены метками; {@code xr:ClassId} - идентификатор класса
   * платформы, он остаётся как есть и сравнивается.
   */
  static String normalizeUuids(String xml) {
    Map<String, String> seen = new LinkedHashMap<>();
    Matcher matcher = UUID.matcher(xml);
    StringBuilder out = new StringBuilder();
    while (matcher.find()) {
      String uuid = matcher.group();
      boolean classId = xml.startsWith("ClassId>", matcher.start() - "ClassId>".length());
      String replacement = classId ? uuid : seen.computeIfAbsent(uuid, key -> "UUID-" + seen.size());
      matcher.appendReplacement(out, Matcher.quoteReplacement(replacement));
    }
    matcher.appendTail(out);
    return out.toString();
  }
}
