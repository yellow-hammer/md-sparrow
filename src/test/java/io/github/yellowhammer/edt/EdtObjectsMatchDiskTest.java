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
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import io.github.yellowhammer.edt.EdtObjectReader.EdtNode;

/**
 * Прочитанные объекты сверяются с файлами проекта.
 *
 * Точечных проверок мало: разбор должен выдерживать всю конфигурацию и оба её
 * расширения, а не отдельно выбранные объекты.
 */
class EdtObjectsMatchDiskTest {

  /** Элемент первого уровня вложенности: EDT отступает содержимое на два пробела. */
  private static final Pattern TOP_LEVEL = Pattern.compile("^ {2}<([\\w.]+)[ >/]", Pattern.MULTILINE);

  /** Все файлы объектов рабочей области. */
  private static List<Path> objectFiles() throws IOException {
    Path root = Path.of(System.getProperty("fixtures.ssl31edt.root"));
    try (Stream<Path> files = Files.walk(root)) {
      return files.filter(path -> path.toString().endsWith(".mdo")).sorted().toList();
    }
  }

  /** Виды вложенных элементов и их количество, посчитанные по тексту файла. */
  private static Map<String, Integer> countByText(String content) {
    Map<String, Integer> counts = new LinkedHashMap<>();
    Matcher matcher = TOP_LEVEL.matcher(content);
    while (matcher.find()) {
      counts.merge(matcher.group(1), 1, Integer::sum);
    }
    return counts;
  }

  @Test
  void всеОбъектыЧитаютсяЦеликом() throws Exception {
    List<Path> files = objectFiles();
    assertThat(files).as("файлы объектов").hasSizeGreaterThan(100);

    List<String> mismatches = new ArrayList<>();
    for (Path file : files) {
      EdtNode object = EdtObjectReader.read(file);
      String content = Files.readString(file, StandardCharsets.UTF_8);

      Map<String, Integer> expected = countByText(content);
      Map<String, Integer> actual = new LinkedHashMap<>();
      object.children().forEach(child -> actual.merge(child.kind(), 1, Integer::sum));

      if (!expected.equals(actual)) {
        mismatches.add("%s: в файле %s, прочитано %s".formatted(file, expected, actual));
      }
    }

    assertThat(mismatches).as("расхождения состава объектов").isEmpty();
  }

  @Test
  void имяОбъектаСовпадаетСИменемФайла() throws Exception {
    List<String> mismatches = new ArrayList<>();
    for (Path file : objectFiles()) {
      String fileName = file.getFileName().toString().replace(".mdo", "");
      // Подчинённые объекты названы по своему виду: у формы это Form.mdo, а
      // корень конфигурации назван по каталогу, но зовётся именем конфигурации
      boolean named = file.getParent().getFileName().toString().equals(fileName);
      if (!named || fileName.equals("Configuration")) {
        continue;
      }

      EdtNode object = EdtObjectReader.read(file);
      if (!object.name().equals(fileName) || object.uuid().isEmpty()) {
        mismatches.add("%s: имя %s, идентификатор %s".formatted(file, object.name(), object.uuid()));
      }
    }

    assertThat(mismatches).as("объекты, не совпавшие с файлом").isEmpty();
  }
  @Test
  void читаетКореньКонфигурации() throws Exception {
    Path project = Path.of(System.getProperty("fixtures.ssl31edt.root"), "ssl31");
    EdtNode configuration = EdtObjectReader.read(project.resolve("src/Configuration/Configuration.mdo"));

    assertThat(configuration.kind()).isEqualTo("Configuration");
    assertThat(configuration.name()).isEqualTo("БиблиотекаСтандартныхПодсистемДемо");
    assertThat(configuration.property("version")).isNotEmpty();
    assertThat(configuration.list("catalogs")).extracting(EdtNode::value).contains("Catalog.Валюты");
  }
}
