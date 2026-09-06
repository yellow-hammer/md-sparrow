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
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.yellowhammer.designerxml.cf.EmptyCfeScaffold.Purpose;

/**
 * Расширение проекта EDT и заимствование в него.
 *
 * Эталон записала сама 1С:EDT: пустое расширение конфигуратора с тремя
 * заимствованными объектами она превратила в проект, и здесь тот же проект
 * должен получиться из заготовки и заимствования.
 */
class EdtExtensionScaffoldTest {

  private static final String UUID = "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}";

  private static EdtModel model;
  private static Path fixture;

  @TempDir
  Path workDir;

  @BeforeAll
  static void locate() throws Exception {
    model = EdtModel.bundled();
    fixture = Path.of("src", "test", "resources", "edt-extension").toAbsolutePath();
    assertThat(fixture.resolve("Основа.Надстройка")).isDirectory();
  }

  private Path base() throws IOException {
    Path base = workDir.resolve("Основа");
    copy(fixture.resolve("Основа"), base);
    return base;
  }

  static void copy(Path from, Path to) throws IOException {
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

  private static String withoutUuids(Path file) throws IOException {
    return Files.readString(file, StandardCharsets.UTF_8).replaceAll(UUID, "uuid");
  }

  @Test
  void расширениеСЗаимствованнымиОбъектамиСовпадаетСЗаписьюEDT() throws Exception {
    Path base = base();
    Path baseConfiguration = base.resolve("src/Configuration/Configuration.mdo");
    Path extension = workDir.resolve("Основа.Надстройка");

    EdtExtensionScaffold.create(baseConfiguration, extension, "Надстройка", "Надстройка", "нс_", Purpose.CUSTOMIZATION, model);
    Path extensionConfiguration = extension.resolve("src/Configuration/Configuration.mdo");
    // Порядок заимствования не совпадает с порядком состава: ссылки встают по схеме
    for (String object : List.of("Documents/Заказ/Заказ.mdo", "Catalogs/Товары/Товары.mdo",
        "CommonModules/ОбщийМодуль/ОбщийМодуль.mdo")) {
      EdtBorrow.borrowObject(base.resolve("src").resolve(object), extensionConfiguration, model);
    }

    Path written = fixture.resolve("Основа.Надстройка");
    try (Stream<Path> files = Files.walk(written)) {
      for (Path expected : files.filter(Files::isRegularFile).toList()) {
        String relative = written.relativize(expected).toString();
        Path actual = extension.resolve(relative);
        assertThat(actual).as(relative).exists();
        assertThat(withoutUuids(actual)).as(relative).isEqualTo(withoutUuids(expected));
      }
    }
    // У заимствованного объекта свои идентификаторы, не идентификаторы оригинала
    String adopted = Files.readString(extension.resolve("src/Catalogs/Товары/Товары.mdo"), StandardCharsets.UTF_8);
    java.util.regex.Matcher original = java.util.regex.Pattern.compile(UUID)
        .matcher(Files.readString(base.resolve("src/Catalogs/Товары/Товары.mdo"), StandardCharsets.UTF_8));
    while (original.find()) {
      assertThat(adopted).doesNotContain(original.group());
    }
  }

  @Test
  void идентификаторыРасширенияСвои() throws Exception {
    Path base = base();
    Path baseConfiguration = base.resolve("src/Configuration/Configuration.mdo");
    Path first = workDir.resolve("Основа.Первое");
    Path second = workDir.resolve("Основа.Второе");

    EdtExtensionScaffold.create(baseConfiguration, first, "Первое", "", "", Purpose.PATCH, model);
    EdtExtensionScaffold.create(baseConfiguration, second, "Второе", "Второе расширение", "вт_", Purpose.CUSTOMIZATION, model);

    String one = Files.readString(first.resolve("src/Configuration/Configuration.mdo"), StandardCharsets.UTF_8);
    String two = Files.readString(second.resolve("src/Configuration/Configuration.mdo"), StandardCharsets.UTF_8);
    assertThat(one).contains("<name>Первое</name>", "<value>Первое</value>",
        "<configurationExtensionPurpose>Patch</configurationExtensionPurpose>");
    assertThat(one).doesNotContain("<namePrefix>");
    assertThat(two).contains("<namePrefix>вт_</namePrefix>", "<value>Второе расширение</value>");
    java.util.regex.Matcher uuids = java.util.regex.Pattern.compile(UUID).matcher(one);
    while (uuids.find()) {
      assertThat(two).doesNotContain(uuids.group());
    }
    assertThat(Files.readString(first.resolve(".project"), StandardCharsets.UTF_8)).contains("<name>Основа.Первое</name>");
  }

  @Test
  void повторноеЗаимствованиеИЗанятыйКаталогОтклоняются() throws Exception {
    Path base = base();
    Path baseConfiguration = base.resolve("src/Configuration/Configuration.mdo");
    Path extension = workDir.resolve("Основа.Надстройка");
    EdtExtensionScaffold.create(baseConfiguration, extension, "Надстройка", null, null, Purpose.CUSTOMIZATION, model);
    Path extensionConfiguration = extension.resolve("src/Configuration/Configuration.mdo");
    Path catalog = base.resolve("src/Catalogs/Товары/Товары.mdo");
    EdtBorrow.borrowObject(catalog, extensionConfiguration, model);

    assertThatThrownBy(() -> EdtBorrow.borrowObject(catalog, extensionConfiguration, model))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("уже заимствован");
    assertThatThrownBy(() -> EdtExtensionScaffold.create(
        baseConfiguration, extension, "Надстройка", null, null, Purpose.CUSTOMIZATION, model))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("уже есть");
  }
}
