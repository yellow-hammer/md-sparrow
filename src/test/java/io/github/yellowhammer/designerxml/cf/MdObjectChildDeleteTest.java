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
import io.github.yellowhammer.designerxml.Ssl31SubmodulePaths;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Удаление узла, добавленного к объекту без узлов, возвращает файл объекта байт в байт.
 *
 * <p>Объект без узлов платформа пишет с {@code <ChildObjects/>}, так же - табличную часть без
 * реквизитов. Опустевший после удаления {@code ChildObjects} должен свернуться обратно: иначе
 * следующая выгрузка платформы меняет файл. То же у формы: её запись уходит из состава строкой
 * целиком. Объекты - из ssl31.
 */
class MdObjectChildDeleteTest {

  private static final SchemaVersion VERSION = SchemaVersion.V2_20;

  @TempDir
  Path workspace;

  @Test
  void удалениеПоследнегоУзлаСворачиваетChildObjects() throws Exception {
    Path plan = copy("ExchangePlans", "_ДемоМобильныйКлиент");
    String original = read(plan);
    assertThat(original).contains("<ChildObjects/>");

    MdObjectChildMutations.addAttribute(plan, VERSION, "Р1");
    MdObjectChildMutations.deleteAttribute(plan, VERSION, "Р1");
    assertThat(read(plan)).as("реквизит").isEqualTo(original);

    MdObjectChildMutations.addCommand(plan, VERSION, "К1");
    MdObjectChildMutations.deleteCommand(plan, VERSION, "К1");
    assertThat(read(plan)).as("команда").isEqualTo(original);
  }

  @Test
  void удалениеПоследнегоРеквизитаТабличнойЧастиСворачиваетЕёChildObjects() throws Exception {
    Path catalog = copy("Catalogs", "_ДемоКассы");
    String original = read(catalog);

    MdObjectChildMutations.addTabularSection(catalog, VERSION, "Т");
    String withSection = read(catalog);
    MdObjectChildMutations.addTabularAttribute(catalog, VERSION, "Т", "Р1");
    MdObjectChildMutations.deleteTabularAttribute(catalog, VERSION, "Т", "Р1");
    assertThat(read(catalog)).as("табличная часть без реквизитов").isEqualTo(withSection);

    MdObjectChildMutations.deleteTabularSection(catalog, VERSION, "Т");
    assertThat(read(catalog)).as("объект без узлов").isEqualTo(original);
  }

  /** Запись формы уходит из состава строкой целиком: в файле с CRLF не остаётся лишнего CR. */
  @Test
  void удалениеФормыУбираетЕёСтрокуЦеликом() throws Exception {
    Path catalog = copy("Catalogs", "_ДемоНоменклатура");
    long lines = read(catalog).lines().count();

    MdObjectChildMutations.deleteForm(catalog, VERSION, "ФормаСписка");

    String after = read(catalog);
    assertThat(after).doesNotContain("<Form>ФормаСписка</Form>").doesNotContain("\r\r");
    assertThat(after.lines().count()).isEqualTo(lines - 1);
  }

  @Test
  void удалениеДобавленнойФормыВозвращаетФайлОбъекта() throws Exception {
    Path catalog = copy("Catalogs", "_ДемоКассы");
    String original = read(catalog);

    FormScaffold.addForm(catalog, VERSION, "ФормаЭлемента");
    MdObjectChildMutations.deleteForm(catalog, VERSION, "ФормаЭлемента");

    assertThat(read(catalog)).isEqualTo(original);
  }

  private Path copy(String kindDirectory, String name) throws Exception {
    Path source = Ssl31SubmodulePaths.projectRoot().resolve("src/cf").resolve(kindDirectory).resolve(name + ".xml");
    Path target = workspace.resolve(kindDirectory).resolve(name + ".xml");
    Files.createDirectories(target.getParent());
    Files.copy(source, target);
    return target;
  }

  private static String read(Path file) throws Exception {
    return Files.readString(file, StandardCharsets.UTF_8);
  }
}
