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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.yellowhammer.designerxml.cf.ChildObjectEntry;

/** Операции над объектом метаданных 1С:EDT целиком. */
class EdtObjectMutationsTest {

  private static EdtModel model;
  private static Path fixture;

  @TempDir
  Path workDir;

  @BeforeAll
  static void locate() throws Exception {
    model = EdtModel.bundled();
    fixture = Path.of(System.getProperty("fixtures.ssl31edt.root"), "ssl31", "src");
    assertThat(fixture).exists();
  }

  /**
   * Небольшой срез конфигурации: копия всей библиотеки на каждый тест занимала
   * бы сотни мегабайт.
   */
  private Path source() throws IOException {
    Path root = workDir.resolve("src");
    Files.createDirectories(root.resolve("Catalogs"));
    copy(fixture.resolve("Configuration"), root.resolve("Configuration"));
    copy(fixture.resolve("Catalogs/Валюты"), root.resolve("Catalogs/Валюты"));
    return root;
  }

  private static void copy(Path from, Path to) throws IOException {
    try (Stream<Path> files = Files.walk(from)) {
      for (Path file : files.toList()) {
        Path target = to.resolve(from.relativize(file).toString());
        if (Files.isDirectory(file)) {
          Files.createDirectories(target);
        } else {
          Files.createDirectories(target.getParent());
          Files.copy(file, target);
        }
      }
    }
  }

  private static List<String> catalogs(Path root) throws IOException {
    return EdtConfigurationLists.names(root.resolve("Configuration/Configuration.mdo"), model, "Catalog");
  }

  @Test
  void переименовываетОбъектВместеСКаталогом() throws Exception {
    Path root = source();

    EdtObjectMutations.rename(
        root.resolve("Configuration/Configuration.mdo"),
        root.resolve("Catalogs/Валюты/Валюты.mdo"),
        "Catalog",
        "Валюты",
        "ДенежныеЕдиницы");

    assertThat(root.resolve("Catalogs/ДенежныеЕдиницы/ДенежныеЕдиницы.mdo")).exists();
    assertThat(root.resolve("Catalogs/Валюты")).doesNotExist();
    assertThat(EdtObjectProperties.readDto(
        root.resolve("Catalogs/ДенежныеЕдиницы/ДенежныеЕдиницы.mdo"), model).internalName)
        .isEqualTo("ДенежныеЕдиницы");
    assertThat(catalogs(root)).contains("ДенежныеЕдиницы").doesNotContain("Валюты");
    // Содержимое объекта переезжает целиком
    assertThat(root.resolve("Catalogs/ДенежныеЕдиницы/Forms/ФормаСписка/Form.form")).exists();
  }

  @Test
  void копияПолучаетСвоиИдентификаторы() throws Exception {
    Path root = source();

    EdtObjectMutations.duplicate(
        root.resolve("Configuration/Configuration.mdo"),
        root.resolve("Catalogs/Валюты/Валюты.mdo"),
        "Catalog",
        "Валюты",
        "ВалютыКопия");

    Path copy = root.resolve("Catalogs/ВалютыКопия/ВалютыКопия.mdo");
    Path origin = root.resolve("Catalogs/Валюты/Валюты.mdo");
    assertThat(copy).exists();
    assertThat(origin).exists();
    assertThat(catalogs(root)).contains("Валюты", "ВалютыКопия");

    EdtObjectReader.EdtNode original = EdtObjectReader.read(origin);
    EdtObjectReader.EdtNode duplicate = EdtObjectReader.read(copy);
    assertThat(duplicate.name()).isEqualTo("ВалютыКопия");
    assertThat(duplicate.uuid()).isNotEqualTo(original.uuid());
    assertThat(duplicate.list("attributes")).extracting(EdtObjectReader.EdtNode::uuid)
        .doesNotContainAnyElementsOf(original.list("attributes").stream()
            .map(EdtObjectReader.EdtNode::uuid).toList());
    // Порождаемые типы у копии тоже свои
    assertThat(typeIds(Files.readString(copy, StandardCharsets.UTF_8)))
        .doesNotContainAnyElementsOf(typeIds(Files.readString(origin, StandardCharsets.UTF_8)));
  }

  private static List<String> typeIds(String xml) {
    return Pattern.compile("typeId=\"([0-9a-f-]{36})\"").matcher(xml).results()
        .map(match -> match.group(1)).toList();
  }

  @Test
  void удаляетОбъектВместеСКаталогом() throws Exception {
    Path root = source();

    EdtObjectMutations.delete(
        root.resolve("Configuration/Configuration.mdo"),
        root.resolve("Catalogs/Валюты/Валюты.mdo"),
        "Catalog",
        "Валюты");

    assertThat(root.resolve("Catalogs/Валюты")).doesNotExist();
    assertThat(catalogs(root)).doesNotContain("Валюты");
  }

  @Test
  void составКонфигурацииМеняетсяТочечно() throws Exception {
    Path root = source();
    Path configuration = root.resolve("Configuration/Configuration.mdo");
    String before = Files.readString(configuration, StandardCharsets.UTF_8);

    EdtObjectMutations.rename(
        configuration, root.resolve("Catalogs/Валюты/Валюты.mdo"), "Catalog", "Валюты", "ДенежныеЕдиницы");

    String after = Files.readString(configuration, StandardCharsets.UTF_8);
    assertThat(after.lines().count()).isEqualTo(before.lines().count());
    assertThat(changedLines(before, after)).containsExactlyInAnyOrder(
        "<catalogs>Catalog.Валюты</catalogs>", "<catalogs>Catalog.ДенежныеЕдиницы</catalogs>");
  }

  private static List<String> changedLines(String before, String after) {
    List<String> beforeLines = List.of(before.split("\\r?\\n", -1));
    List<String> afterLines = List.of(after.split("\\r?\\n", -1));
    List<String> changed = new ArrayList<>();
    afterLines.stream().filter(line -> !beforeLines.contains(line)).map(String::trim).forEach(changed::add);
    beforeLines.stream().filter(line -> !afterLines.contains(line)).map(String::trim).forEach(changed::add);
    return changed;
  }

  @Test
  void занятоеИмяОтклоняется() throws Exception {
    Path root = source();
    Path configuration = root.resolve("Configuration/Configuration.mdo");
    Path object = root.resolve("Catalogs/Валюты/Валюты.mdo");
    EdtObjectMutations.duplicate(configuration, object, "Catalog", "Валюты", "ВалютыКопия");

    assertThatThrownBy(
        () -> EdtObjectMutations.duplicate(configuration, object, "Catalog", "Валюты", "ВалютыКопия"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("ВалютыКопия");
  }

  @Test
  void составОстальныхВидовНеТрогается() throws Exception {
    Path root = source();
    Path configuration = root.resolve("Configuration/Configuration.mdo");
    List<ChildObjectEntry> before = EdtConfigurationReader.listChildObjects(configuration, model);

    EdtObjectMutations.delete(
        configuration, root.resolve("Catalogs/Валюты/Валюты.mdo"), "Catalog", "Валюты");

    List<ChildObjectEntry> after = EdtConfigurationReader.listChildObjects(configuration, model);
    assertThat(after).hasSize(before.size() - 1);
    assertThat(after).noneMatch(entry -> entry.objectType().equals("Catalog") && entry.name().equals("Валюты"));
  }
}
