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

import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Объект любого вида, созданный {@code add-md-object}, читается всем, что показывает выгрузку:
 * свойства ({@code cf-md-object-get}), структура ({@code cf-md-object-structure-get}), дерево
 * ({@code project-metadata-tree}) и граф проекта. В дереве объект стоит в группе своего вида.
 */
class MdObjectAllKindsReadTest {

  @TempDir
  Path workspace;

  @ParameterizedTest
  @EnumSource(SchemaVersion.class)
  void объектКаждогоВидаЧитаетсяИСтоитВДереве(SchemaVersion version) throws Exception {
    Path project = workspace.resolve(version.name());
    Path cf = ProjectSourceDirs.DEFAULTS.cfPath(project);
    EmptyCfScaffold.writeEmptyTree(cf, CfLayout.DEFAULT_CONFIGURATION_NAME, null, null, null, version);
    Path configuration = cf.resolve(CfLayout.CONFIGURATION_XML);

    Map<String, MdObjectAddType> created = new LinkedHashMap<>();
    for (MdObjectAddType type : MdObjectAddType.values()) {
      if (!type.existsIn(version)) {
        continue;
      }
      String name = MdObjectAdd.addWithNextAvailableName(configuration, version, type, null, false);
      created.put(type.configurationXmlTag() + "." + name, type);

      Path objectXml = CfObjectPathResolver.objectXml(cf, type.configurationXmlTag(), name).orElseThrow();
      MdObjectPropertiesDto properties = MdObjectPropertiesEdit.readDto(objectXml, version);
      assertThat(properties.internalName).as("%s в формате %s", type, version).isEqualTo(name);
      assertThat(properties.kind).as("%s в формате %s", type, version).isNotBlank();
      MdObjectStructureRead.read(objectXml, version);
    }

    ProjectMetadataTreeDto tree = ProjectMetadataTreeBuilder.build(project);
    ProjectMetadataTreeDto.MetadataSourceDto main = tree.sources().getFirst();
    Map<String, String> groupByKey = new LinkedHashMap<>();
    Map<String, String> pathByKey = new LinkedHashMap<>();
    for (ProjectMetadataTreeDto.MetadataGroupDto group : main.groups()) {
      List<ProjectMetadataTreeDto.MetadataItemDto> items = new ArrayList<>(group.items());
      group.subgroups().forEach(subgroup -> items.addAll(subgroup.items()));
      for (ProjectMetadataTreeDto.MetadataItemDto item : items) {
        String key = item.objectType() + "." + item.name();
        groupByKey.put(key, group.id());
        pathByKey.put(key, item.relativePath());
      }
    }
    Map<String, String> expectedGroup = new LinkedHashMap<>();
    for (MetadataTreeTagGroups.GroupDef group : MetadataTreeTagGroups.orderedGroups()) {
      group.tags().forEach(tag -> expectedGroup.put(tag, group.id()));
    }
    for (Map.Entry<String, MdObjectAddType> object : created.entrySet()) {
      MdObjectAddType type = object.getValue();
      String name = object.getKey().substring(type.configurationXmlTag().length() + 1);
      assertThat(groupByKey).as("дерево формата %s", version).containsKey(object.getKey());
      // Виды без своей группы лежат в «Общих»
      assertThat(groupByKey.get(object.getKey())).as("группа %s", object.getKey())
        .isEqualTo(expectedGroup.getOrDefault(type.configurationXmlTag(), MetadataTreeTagGroups.GROUP_ID_COMMON));
      assertThat(pathByKey.get(object.getKey())).as("файл %s", object.getKey())
        .isEqualTo("src/cf/" + GoldenScaffold.objectRelative(type, name));
    }

    ProjectMetadataGraphDto graph = ProjectMetadataGraphBuilder.build(project);
    assertThat(graph.nodes()).extracting(ProjectMetadataGraphDto.NodeDto::key)
      .as("граф формата %s", version)
      .containsAll(created.keySet());
  }
}
