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

import io.github.yellowhammer.designerxml.SamplesSubmodulePaths;
import io.github.yellowhammer.designerxml.SchemaVersion;

import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Подчинённый узел добавляется только тому виду, у которого он есть по схеме формата.
 *
 * <p>Платформа узел чужого вида (измерение у справочника, реквизит у перечисления) молча
 * выбрасывает при загрузке, поэтому запись такого узла - потеря данных без предупреждения.
 * Проверка идёт на голых объектах платформы каждого формата: у них есть все виды, которые
 * умеет создавать md-sparrow.
 */
class MdObjectChildKindsTest {

  @TempDir
  Path workspace;

  @ParameterizedTest
  @EnumSource(SchemaVersion.class)
  void узелДобавляетсяТолькоВидуУКоторогоОнЕстьПоСхеме(SchemaVersion version) throws Exception {
    Path cf = SamplesSubmodulePaths.copy(SamplesSubmodulePaths.bareObjects(version), workspace.resolve("cf"));
    int added = 0;
    int refused = 0;
    for (Path objectXml : SamplesSubmodulePaths.objectXmls(cf)) {
      String owner = ChildNodeOp.ownerOf(objectXml);
      List<String> allowed = ChildNodeOp.schemaChildren(version, owner);
      for (ChildNodeOp op : ChildNodeOp.values()) {
        String name = "Новый" + op.element;
        if (allowed.contains(op.element)) {
          op.add(objectXml, version, name);
          assertThat(op.names(objectXml, version)).as("%s: %s", owner, op.element).contains(name);
          added++;
          continue;
        }
        byte[] before = Files.readAllBytes(objectXml);
        assertThatThrownBy(() -> op.add(objectXml, version, name))
          .as("%s: %s", owner, op.element)
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("У вида " + owner + " нет подчинённых " + op.element);
        assertThat(Files.readAllBytes(objectXml)).as("%s не изменён", owner).isEqualTo(before);
        refused++;
      }
    }
    assertThat(added).isPositive();
    assertThat(refused).isPositive();
  }

  @ParameterizedTest
  @EnumSource(SchemaVersion.class)
  void реквизитТабличнойЧастиТолькоУВидаСТабличнымиЧастями(SchemaVersion version) throws Exception {
    Path cf = SamplesSubmodulePaths.copy(SamplesSubmodulePaths.bareObjects(version), workspace.resolve("cf"));
    for (Path objectXml : SamplesSubmodulePaths.objectXmls(cf)) {
      String owner = ChildNodeOp.ownerOf(objectXml);
      if (ChildNodeOp.schemaChildren(version, owner).contains(ChildNodeOp.TABULAR_SECTION.element)) {
        ChildNodeOp.TABULAR_SECTION.add(objectXml, version, "Состав");
        MdObjectChildMutations.addTabularAttribute(objectXml, version, "Состав", "Количество");
        MdObjectStructureDto structure = MdObjectStructureRead.read(objectXml, version);
        assertThat(structure.tabularSections).as(owner).singleElement()
          .satisfies(section -> assertThat(section.attributes).extracting(node -> node.name)
            .containsExactly("Количество"));
        continue;
      }
      byte[] before = Files.readAllBytes(objectXml);
      assertThatThrownBy(() -> MdObjectChildMutations.addTabularAttribute(objectXml, version, "Состав", "Количество"))
        .as(owner)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("У вида " + owner + " нет подчинённых TabularSection");
      assertThat(Files.readAllBytes(objectXml)).isEqualTo(before);
    }
  }
}
