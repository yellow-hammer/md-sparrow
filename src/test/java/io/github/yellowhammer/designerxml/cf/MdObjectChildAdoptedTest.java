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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Свои поля данных у заимствованного в расширении объекта.
 *
 * <p>К заимствованным регистрам, последовательности и планам счетов, видов характеристик и видов
 * расчёта платформа не даёт добавить реквизит, табличную часть, измерение, ресурс и признаки учёта:
 * расширение с ними не проходит проверку и не применяется ({@code ibcmd infobase config check
 * --extension} на 8.3.27 и 8.5.1). Команды к ним и поля к заимствованным справочнику, документу и
 * прочим ссылочным объектам добавляются. Расширение - {@code _ДемоРасширение} из ssl31.
 */
class MdObjectChildAdoptedTest {

  private static final SchemaVersion VERSION = SchemaVersion.V2_20;

  @TempDir
  Path workspace;

  @Test
  void кЗаимствованномуРегиструСвоиПоляНеДобавляются() throws Exception {
    Path register = copyFromExtension("InformationRegisters", "_ДемоЗаведующиеМестамиХранения");
    assertRefused(register, "InformationRegister", "Dimension",
      () -> MdObjectChildMutations.addDimension(register, VERSION, "Склад"));
    assertRefused(register, "InformationRegister", "Attribute",
      () -> MdObjectChildMutations.addAttribute(register, VERSION, "Примечание"));

    Path accumulation = copyFromExtension("AccumulationRegisters", "_ДемоОстаткиТоваровВМестахХранения");
    assertRefused(accumulation, "AccumulationRegister", "Resource",
      () -> MdObjectChildMutations.duplicateResource(accumulation, VERSION, "Количество", "КоличествоКопия"));

    MdObjectChildMutations.addCommand(register, VERSION, "ОткрытьСписок");
    assertThat(MdObjectStructureRead.read(register, VERSION).commands).contains("ОткрытьСписок");
  }

  @Test
  void кЗаимствованномуПлануСчетовСвоиПоляНеДобавляются() throws Exception {
    Path extension = copyFromExtension("", "Configuration");
    Path chart = CfeBorrow.borrowObject(
      Ssl31SubmodulePaths.projectRoot().resolve("src/cf/ChartsOfAccounts/_ДемоОсновной.xml"), extension, VERSION);

    assertRefused(chart, "ChartOfAccounts", "AccountingFlag",
      () -> MdObjectChildMutations.addAccountingFlag(chart, VERSION, "Расш_Валютный"));
    assertRefused(chart, "ChartOfAccounts", "ExtDimensionAccountingFlag",
      () -> MdObjectChildMutations.addExtDimensionAccountingFlag(chart, VERSION, "Расш_Суммовой"));
    assertRefused(chart, "ChartOfAccounts", "TabularSection",
      () -> MdObjectChildMutations.addTabularSection(chart, VERSION, "Расш_Таблица"));

    MdObjectChildMutations.addCommand(chart, VERSION, "Расш_Команда");
    assertThat(MdObjectStructureRead.read(chart, VERSION).commands).contains("Расш_Команда");
  }

  @Test
  void кЗаимствованномуСправочникуИКСвоемуРегиструПоляДобавляются() throws Exception {
    Path catalog = copyFromExtension("Catalogs", "_ДемоНоменклатура");
    MdObjectChildMutations.addAttribute(catalog, VERSION, "Расш_Реквизит");
    MdObjectChildMutations.addTabularSection(catalog, VERSION, "Расш_Таблица");

    Path own = copyFromExtension("InformationRegisters", "_ДемоСегментыПартнеровРасширение");
    MdObjectChildMutations.addDimension(own, VERSION, "Расш_Измерение");

    assertThat(MdObjectStructureRead.read(catalog, VERSION).attributes).extracting(node -> node.name)
      .contains("Расш_Реквизит");
    assertThat(MdObjectStructureRead.read(own, VERSION).dimensions).contains("Расш_Измерение");
  }

  private Path copyFromExtension(String kindDirectory, String name) throws Exception {
    Path extension = Ssl31SubmodulePaths.projectRoot().resolve("src/cfe/_ДемоРасширение");
    Path source = (kindDirectory.isEmpty() ? extension : extension.resolve(kindDirectory)).resolve(name + ".xml");
    Path target = (kindDirectory.isEmpty() ? workspace : workspace.resolve(kindDirectory)).resolve(name + ".xml");
    Files.createDirectories(target.getParent());
    Files.copy(source, target);
    return target;
  }

  private static void assertRefused(Path file, String owner, String node, Edit edit) throws Exception {
    String before = Files.readString(file, StandardCharsets.UTF_8);
    assertThatThrownBy(edit::run)
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage("К заимствованному объекту вида " + owner + " расширение не добавляет подчинённых " + node
        + ": платформа такое расширение не применяет");
    assertThat(Files.readString(file, StandardCharsets.UTF_8)).as("файл после отказа").isEqualTo(before);
  }

  @FunctionalInterface
  private interface Edit {
    void run() throws Exception;
  }
}
