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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.yellowhammer.designerxml.cf.EmptyCfeScaffold.Purpose;
import io.github.yellowhammer.designerxml.cf.ExternalArtifactKind;
import io.github.yellowhammer.designerxml.cf.MdObjectAddType;

/**
 * Одна и та же правка двух одинаковых проектов EDT даёт одинаковые файлы.
 *
 * Идентификаторы выводятся из того, что создаётся, и из текста файла, к
 * которому оно добавляется, как у выгрузки конфигуратора: без этого каждая
 * повторная генерация меняет все идентификаторы, и сравнить результат нельзя.
 */
class EdtScaffoldDeterminismTest {

  private static EdtModel model;
  private static Path configurationFixture;
  private static Path extensionFixture;

  @TempDir
  Path workDir;

  @BeforeAll
  static void locate() throws Exception {
    model = EdtModel.bundled();
    configurationFixture = Path.of(System.getProperty("fixtures.ssl31edt.root"), "ssl31", "src", "Configuration");
    extensionFixture = Path.of("src", "test", "resources", "edt-extension", "Основа").toAbsolutePath();
    assertThat(configurationFixture).isDirectory();
    assertThat(extensionFixture).isDirectory();
  }

  @Test
  void объектыФормыИУзлыОдинаковыхПроектовСовпадают() throws Exception {
    List<Path> projects = List.of(workDir.resolve("Первый"), workDir.resolve("Второй"));
    for (Path project : projects) {
      EdtExtensionScaffoldTest.copy(configurationFixture, project.resolve("src/Configuration"));
      Path configuration = project.resolve("src/Configuration/Configuration.mdo");

      EdtObjectScaffold.add(configuration, model, MdObjectAddType.CATALOG, "Товары");
      EdtObjectScaffold.addWithNextAvailableName(configuration, model, MdObjectAddType.DOCUMENT);
      EdtObjectScaffold.add(configuration, model, MdObjectAddType.ROLE, "Кладовщик");
      Path catalog = project.resolve("src/Catalogs/Товары/Товары.mdo");
      EdtObjectScaffold.addForm(catalog, model, "ФормаЭлемента");
      EdtChildMutations.add(catalog, model, "attributes", "Цена");
      EdtChildMutations.add(catalog, model, "commands", "Пересчитать");
      EdtChildMutations.add(catalog, model, "tabularSections", "Курсы");
      EdtChildMutations.addNested(catalog, model, "tabularSections", "Курсы", "attributes", "Курс");
      EdtChildMutations.duplicate(catalog, null, null, "tabularSections", "Курсы", "КурсыКопия");
      EdtObjectMutations.duplicate(configuration, catalog, "Catalog", "Товары", "ТоварыКопия");
    }

    assertSameTree(projects.get(0), projects.get(1));
  }

  @Test
  void расширениеИВнешниеОбъектыОдинаковыхПроектовСовпадают() throws Exception {
    List<Path> roots = List.of(workDir.resolve("Первый"), workDir.resolve("Второй"));
    for (Path root : roots) {
      EdtExtensionScaffoldTest.copy(extensionFixture, root.resolve("Основа"));
      Path configuration = root.resolve("Основа/src/Configuration/Configuration.mdo");

      Path extension = root.resolve("Основа.Надстройка");
      EdtExtensionScaffold.create(configuration, extension, "Надстройка", null, "нс_", Purpose.CUSTOMIZATION, model);
      try (Stream<Path> catalogs = Files.list(root.resolve("Основа/src/Catalogs"))) {
        Path catalog = catalogs.sorted().findFirst().orElseThrow();
        EdtBorrow.borrowObject(catalog.resolve(catalog.getFileName() + ".mdo"),
            extension.resolve("src/Configuration/Configuration.mdo"), model);
      }
      Path processor = EdtExternalArtifacts.create(
          root.resolve("epf"), configuration, "Загрузка", ExternalArtifactKind.DATA_PROCESSOR);
      EdtExternalArtifacts.create(root.resolve("epf"), configuration, "Сводка", ExternalArtifactKind.REPORT);
      EdtExternalArtifacts.duplicate(processor, "ЗагрузкаКопия");
    }

    assertSameTree(roots.get(0), roots.get(1));
  }

  @Test
  void разныеПравкиДаютРазныеИдентификаторы() throws Exception {
    Path project = workDir.resolve("Проект");
    EdtExtensionScaffoldTest.copy(configurationFixture, project.resolve("src/Configuration"));
    Path configuration = project.resolve("src/Configuration/Configuration.mdo");

    // Состав конфигурации после первой правки другой, поэтому и зерно другое
    EdtObjectScaffold.add(configuration, model, MdObjectAddType.CATALOG, "Первый");
    EdtObjectScaffold.add(configuration, model, MdObjectAddType.CATALOG, "Второй");

    String first = Files.readString(project.resolve("src/Catalogs/Первый/Первый.mdo"));
    for (String uuid : EdtExtensionScaffoldTest.objectIds(
        Files.readString(project.resolve("src/Catalogs/Второй/Второй.mdo")))) {
      assertThat(first).doesNotContain(uuid);
    }
  }

  private static void assertSameTree(Path left, Path right) throws IOException {
    List<String> leftFiles = files(left);
    assertThat(leftFiles).isNotEmpty().isEqualTo(files(right));
    for (String file : leftFiles) {
      assertThat(Files.readAllBytes(right.resolve(file))).as(file).isEqualTo(Files.readAllBytes(left.resolve(file)));
    }
  }

  private static List<String> files(Path root) throws IOException {
    try (Stream<Path> tree = Files.walk(root)) {
      return tree.filter(Files::isRegularFile).map(file -> root.relativize(file).toString()).sorted().toList();
    }
  }
}
