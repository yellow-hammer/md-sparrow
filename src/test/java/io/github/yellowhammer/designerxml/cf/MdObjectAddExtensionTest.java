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

import io.github.yellowhammer.designerxml.DesignerXml;
import io.github.yellowhammer.designerxml.SamplesSubmodulePaths;
import io.github.yellowhammer.designerxml.SchemaVersion;

import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * add-md-object в расширении: свой объект встаёт в состав расширения так, как его пишет платформа.
 */
class MdObjectAddExtensionTest {

  private static final String EMPTY_CHILD_OBJECTS = "<ChildObjects/>";

  @TempDir
  Path workspace;

  /**
   * Расширение в режиме совместимости 8.3.13 и ниже, пока в нём нет объектов, платформа пишет с
   * пустым составом {@code <ChildObjects/>}. Первый свой объект любого вида раскрывает этот тег и
   * встаёт в него строкой на уровень глубже.
   */
  @ParameterizedTest
  @EnumSource(SchemaVersion.class)
  void первыйОбъектВстаётВПустойСостав(SchemaVersion version) throws Exception {
    SoftAssertions softly = new SoftAssertions();
    for (MdObjectAddType type : MdObjectAddType.values()) {
      if (!type.existsIn(version)) {
        continue;
      }
      Path cfe = extensionWithoutObjects(version, workspace.resolve(version.name() + "-" + type));
      Path configuration = cfe.resolve(CfLayout.CONFIGURATION_XML);
      String before = GoldenSnapshots.read(configuration);
      softly.assertThat(before).as("состав расширения до добавления").contains(EMPTY_CHILD_OBJECTS);

      String name = MdObjectAdd.addWithNextAvailableName(configuration, version, type, null, false);

      String eol = before.contains("\r\n") ? "\r\n" : "\n";
      String indent = XmlLines.indentAt(before, before.indexOf(EMPTY_CHILD_OBJECTS));
      String tag = type.configurationXmlTag();
      softly.assertThat(GoldenSnapshots.read(configuration)).as("%s в формате %s", type, version)
        .isEqualTo(before.replace(EMPTY_CHILD_OBJECTS,
          "<ChildObjects>" + eol
            + indent + "\t<" + tag + ">" + name + "</" + tag + ">" + eol
            + indent + "</ChildObjects>"));
      DesignerXml.read(configuration, version);
      softly.assertThat(CfDumpValidation.validate(cfe)).as("находки validate-dump: %s", type).isEmpty();
    }
    softly.assertAll();
  }

  /**
   * Отказ посреди записи не оставляет файлов объекта, на которые не ссылается состав: записанное
   * удаляется. Здесь каталог объекта занят файлом с тем же именем, поэтому файлы из каталога
   * прототипа ({@code Ext/…}) записать нельзя, хотя описание объекта уже записано.
   */
  @ParameterizedTest
  @EnumSource(SchemaVersion.class)
  void отказПриЗаписиНеОставляетФайловОбъекта(SchemaVersion version) throws Exception {
    SoftAssertions softly = new SoftAssertions();
    int refused = 0;
    for (MdObjectAddType type : MdObjectAddType.values()) {
      if (!type.existsIn(version) || GoldenScaffold.prototypeFiles(type).size() < 2) {
        continue;
      }
      Path cfe = extensionWithoutObjects(version, workspace.resolve(version.name() + "-" + type));
      Path configuration = cfe.resolve(CfLayout.CONFIGURATION_XML);
      String name = "Нов" + GoldenScaffold.protoName(type);
      Path obstacle = cfe.resolve(type.cfSubdir()).resolve(name);
      Files.createDirectories(obstacle.getParent());
      Files.createFile(obstacle);
      String before = GoldenSnapshots.read(configuration);
      List<Path> filesBefore = filesUnder(cfe);

      softly.assertThatThrownBy(() -> MdObjectAdd.add(configuration, name, version, type))
        .as("%s в формате %s", type, version)
        .isInstanceOf(IOException.class);

      softly.assertThat(filesUnder(cfe)).as("файлы после отказа: %s", type).isEqualTo(filesBefore);
      softly.assertThat(GoldenSnapshots.read(configuration)).as("состав после отказа: %s", type).isEqualTo(before);
      refused++;
    }
    softly.assertAll();
    assertThat(refused).as("видов с файлами в каталоге объекта").isPositive();
  }

  /**
   * В составе нового расширения нет языка, только роль по умолчанию. Строки объектов всех видов
   * (по два каждого) встают с отступом роли, без пустых строк между ними, в порядке видов платформы.
   */
  @ParameterizedTest
  @EnumSource(SchemaVersion.class)
  void составБезЯзыкаОстаётсяБезПустыхСтрок(SchemaVersion version) throws Exception {
    Path cfe = newExtension(version, workspace.resolve(version.name()));
    Path configuration = cfe.resolve(CfLayout.CONFIGURATION_XML);
    List<String> initial = childObjectLines(GoldenSnapshots.read(configuration));
    assertThat(initial).as("состав нового расширения").hasSize(1);
    assertThat(initial.get(0).strip()).doesNotStartWith("<Language>");
    String indent = initial.get(0).substring(0, initial.get(0).indexOf('<'));

    List<MdObjectAddType> kinds = new ArrayList<>();
    for (MdObjectAddType type : MdObjectAddType.values()) {
      if (type.existsIn(version)) {
        kinds.add(type);
      }
    }
    Collections.shuffle(kinds, new Random(version.ordinal()));
    for (int round = 0; round < 2; round++) {
      for (MdObjectAddType type : kinds) {
        MdObjectAdd.addWithNextAvailableName(configuration, version, type, null, false);
      }
    }

    List<String> lines = childObjectLines(GoldenSnapshots.read(configuration));
    assertThat(lines).as("строк в составе").hasSize(1 + 2 * kinds.size());
    assertThat(lines).as("отступ строк состава")
      .allSatisfy(line -> assertThat(line).startsWith(indent + "<"));
    assertThat(CfDumpValidation.validate(cfe)).as("находки validate-dump").isEmpty();
  }

  /** Строки между {@code <ChildObjects>} и {@code </ChildObjects>} как есть, с отступами и пустыми. */
  private static List<String> childObjectLines(String configuration) {
    int start = configuration.indexOf("<ChildObjects>");
    assertThat(start).as("ChildObjects").isNotNegative();
    int open = configuration.indexOf('\n', start) + 1;
    int close = configuration.lastIndexOf('\n', configuration.indexOf("</ChildObjects>", start)) + 1;
    return configuration.substring(open, close).lines().toList();
  }

  /**
   * Новое расширение: выгрузка пустого расширения платформой, где она снята (с 2.14), иначе
   * {@code init-empty-cfe}, который пишет то же самое.
   */
  private static Path newExtension(SchemaVersion version, Path cfe) throws Exception {
    if (GoldenSnapshots.files(version, GoldenSnapshots.CFE).isEmpty()) {
      EmptyCfeScaffold.writeEmptyTree(cfe, "Расширение", null, null, EmptyCfeScaffold.Purpose.ADD_ON, null, null, version);
      return cfe;
    }
    return SamplesSubmodulePaths.copy(SamplesSubmodulePaths.snapshot(version, GoldenSnapshots.CFE), cfe);
  }

  private static List<Path> filesUnder(Path root) throws IOException {
    try (Stream<Path> walk = Files.walk(root)) {
      return walk.map(root::relativize).sorted().toList();
    }
  }

  /**
   * Расширение без объектов, как его создаёт платформа к конфигурации в старом режиме совместимости:
   * голые объекты эталона сняты с базы в режиме 8.3.12.
   */
  private static Path extensionWithoutObjects(SchemaVersion version, Path cfe) throws Exception {
    Path main = SamplesSubmodulePaths.bareObjects(version).resolve(CfLayout.CONFIGURATION_XML);
    EmptyCfeScaffold.writeEmptyTreeFromConfiguration(
      cfe, "Расширение", null, null, EmptyCfeScaffold.Purpose.CUSTOMIZATION, main, version);
    return cfe;
  }
}
