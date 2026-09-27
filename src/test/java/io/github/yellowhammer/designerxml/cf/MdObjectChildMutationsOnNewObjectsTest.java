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
import io.github.yellowhammer.designerxml.SchemaVersion;

import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.nio.file.Path;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Подчинённые узлы на объектах, которые только что создал {@code add-md-object}: реквизит,
 * табличная часть и её реквизит, команда, значение перечисления, признаки учёта.
 *
 * <p>Созданный объект - копия эталона платформы своего формата, поэтому проверка идёт по всем
 * форматам: после правок файл читается моделью версии, и каждый добавленный узел на месте.
 */
class MdObjectChildMutationsOnNewObjectsTest {

  @TempDir
  Path workspace;

  @ParameterizedTest
  @EnumSource(SchemaVersion.class)
  void узлыДобавляютсяНаСозданныеОбъектыКаждогоФормата(SchemaVersion version) throws Exception {
    Path cf = workspace.resolve("cf");
    EmptyCfScaffold.writeEmptyTree(cf, CfLayout.DEFAULT_CONFIGURATION_NAME, null, null, null, version);
    Path configurationXml = cf.resolve(CfLayout.CONFIGURATION_XML);
    Set<ChildNodeOp> covered = EnumSet.noneOf(ChildNodeOp.class);
    boolean tabularAttributeCovered = false;

    for (MdObjectAddType type : MdObjectAddType.values()) {
      if (!type.existsIn(version)) {
        continue;
      }
      String objectName = MdObjectAdd.addWithNextAvailableName(configurationXml, version, type, null, false);
      Path objectXml = CfObjectPathResolver.objectXml(cf, type.configurationXmlTag(), objectName).orElseThrow();
      List<String> allowed = ChildNodeOp.schemaChildren(version, type.configurationXmlTag());

      for (ChildNodeOp op : ChildNodeOp.values()) {
        if (allowed.contains(op.element)) {
          op.add(objectXml, version, "Новый" + op.element);
          covered.add(op);
        }
      }
      if (allowed.contains(ChildNodeOp.TABULAR_SECTION.element)) {
        MdObjectChildMutations.addTabularAttribute(
          objectXml, version, "Новый" + ChildNodeOp.TABULAR_SECTION.element, "Количество");
        tabularAttributeCovered = true;
      }

      DesignerXml.read(objectXml, version);
      for (ChildNodeOp op : ChildNodeOp.values()) {
        if (allowed.contains(op.element)) {
          assertThat(op.names(objectXml, version)).as("%s: %s", type, op.element).containsExactly("Новый" + op.element);
        }
      }
      if (allowed.contains(ChildNodeOp.TABULAR_SECTION.element)) {
        assertThat(MdObjectStructureRead.read(objectXml, version).tabularSections)
          .as("%s: реквизит табличной части", type)
          .singleElement()
          .satisfies(section -> assertThat(section.attributes).extracting(node -> node.name)
            .containsExactly("Количество"));
      }
    }

    // Регистры тоже создаются: каждый вид узла добавлен хотя бы одному объекту
    assertThat(covered).containsExactlyInAnyOrder(ChildNodeOp.values());
    assertThat(tabularAttributeCovered).isTrue();
    assertThat(CfDumpValidation.validate(cf)).isEmpty();
  }
}
