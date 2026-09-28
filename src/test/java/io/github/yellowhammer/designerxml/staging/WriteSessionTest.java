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
package io.github.yellowhammer.designerxml.staging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.yellowhammer.designerxml.SamplesSubmodulePaths;
import io.github.yellowhammer.designerxml.SchemaVersion;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

/**
 * Сеанс записи: изменения живут в памяти, диск меняет только публикация, и только
 * если прочитанное операцией на нём прежнее. Данные - копия эталона платформы.
 */
class WriteSessionTest {

  @TempDir
  Path temp;

  private Path root;
  private Map<String, String> before;

  @BeforeEach
  void copySample() throws IOException {
    root = SamplesSubmodulePaths.copy(
      SamplesSubmodulePaths.bareObjects(SchemaVersion.V2_20), temp.resolve("cf"));
    before = tree(root);
    Files.copy(root.resolve("Configuration.xml"), temp.resolve("original.xml"));
  }

  @Test
  void writesStayInMemoryUntilPublish() throws Exception {
    try (WriteSession session = WriteSession.open()) {
      Path configuration = session.path(root.resolve("Configuration.xml"));
      String text = Files.readString(configuration, StandardCharsets.UTF_8);
      Files.writeString(configuration, text + "\n", StandardCharsets.UTF_8);
      Path module = session.path(root.resolve("CommonModules/ОбщийМодуль1/Ext/Module.bsl"));
      Files.createDirectories(module.getParent());
      Files.writeString(module, "// модуль", StandardCharsets.UTF_8);

      // Операция видит свою правку, диск - нет
      assertThat(Files.readString(configuration, StandardCharsets.UTF_8)).isEqualTo(text + "\n");
      assertThat(Files.isRegularFile(module)).isTrue();
      try (Stream<Path> list = Files.list(module.getParent().getParent().getParent())) {
        assertThat(list.map(p -> p.getFileName().toString())).contains("ОбщийМодуль1.xml", "ОбщийМодуль1");
      }
      assertThat(tree(root)).isEqualTo(before);
      assertThat(session.changes()).hasSize(4);

      session.publish();
    }
    assertThat(Files.readString(root.resolve("Configuration.xml"), StandardCharsets.UTF_8))
      .isEqualTo(new String(Files.readAllBytes(temp.resolve("original.xml")), StandardCharsets.UTF_8) + "\n");
    assertThat(root.resolve("CommonModules/ОбщийМодуль1/Ext/Module.bsl")).hasContent("// модуль");
  }

  @Test
  void closedSessionLeavesDiskUntouched() throws Exception {
    try (WriteSession session = WriteSession.open()) {
      Path catalogs = session.path(root.resolve("Catalogs"));
      try (Stream<Path> files = Files.list(catalogs)) {
        for (Path file : files.toList()) {
          Files.delete(file);
        }
      }
      Files.delete(catalogs);
      assertThat(Files.exists(catalogs)).isFalse();
      session.verify();
    }
    assertThat(tree(root)).isEqualTo(before);
  }

  @Test
  void directoryMoveCarriesItsContent() throws Exception {
    try (WriteSession session = WriteSession.open()) {
      Path from = session.path(root.resolve("Catalogs"));
      Path to = session.path(root.resolve("Справочники"));
      Files.move(from, to);
      assertThat(Files.exists(from)).isFalse();
      session.publish();
    }
    Map<String, String> after = tree(root);
    assertThat(after.keySet()).noneMatch(name -> name.startsWith("Catalogs/"));
    before.forEach((name, bytes) -> {
      String moved = name.startsWith("Catalogs/") ? "Справочники/" + name.substring("Catalogs/".length()) : name;
      assertThat(after.get(moved)).as(moved).isEqualTo(bytes);
    });
  }

  @Test
  void changedInputBlocksPublication() throws Exception {
    try (WriteSession session = WriteSession.open()) {
      Path configuration = session.path(root.resolve("Configuration.xml"));
      String text = Files.readString(configuration, StandardCharsets.UTF_8);
      Files.writeString(session.path(root.resolve("Новый.xml")), text, StandardCharsets.UTF_8);
      // Чужая правка прочитанного файла, пока операция держит изменения в памяти
      Files.writeString(root.resolve("Configuration.xml"), text + " ", StandardCharsets.UTF_8);

      assertThatThrownBy(session::verify).isInstanceOf(StaleInputException.class);
      assertThatThrownBy(session::publish).isInstanceOf(StaleInputException.class)
        .hasMessageContaining("Configuration.xml");
    }
    assertThat(root.resolve("Новый.xml")).doesNotExist();
  }

  @Test
  void failedPublicationRestoresDisk() throws Exception {
    try (WriteSession session = WriteSession.open()) {
      Path configuration = session.path(root.resolve("Configuration.xml"));
      Files.writeString(configuration, "первый", StandardCharsets.UTF_8);
      Files.writeString(session.path(root.resolve("Новый.xml")), "второй", StandardCharsets.UTF_8);
      Files.delete(session.path(root.resolve("Catalogs/Справочник1.xml")));
      Files.createDirectory(session.path(root.resolve("НовыйКаталог")));
      // Запись второго файла срывается, когда первый уже на диске
      StagedFileSystemProvider.FileWriter real = session.provider().writer;
      int[] count = {0};
      session.provider().writer = (key, bytes) -> {
        if (count[0]++ == 1) {
          throw new IOException("сбой записи");
        }
        real.write(key, bytes);
      };

      assertThatThrownBy(session::publish).isInstanceOf(IOException.class).hasMessageContaining("сбой записи");
    }
    assertThat(tree(root)).isEqualTo(before);
    assertThat(root.resolve("НовыйКаталог")).doesNotExist();
  }

  /** Все файлы каталога: относительный путь и содержимое в шестнадцатеричном виде. */
  private static Map<String, String> tree(Path dir) throws IOException {
    Map<String, String> out = new TreeMap<>();
    try (Stream<Path> files = Files.walk(dir)) {
      for (Path file : files.filter(Files::isRegularFile).toList()) {
        out.put(dir.relativize(file).toString().replace('\\', '/'),
          java.util.HexFormat.of().formatHex(Files.readAllBytes(file)));
      }
    }
    return out;
  }
}
