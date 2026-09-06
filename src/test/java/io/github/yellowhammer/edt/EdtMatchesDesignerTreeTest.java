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

import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import org.junit.jupiter.api.Test;

import io.github.yellowhammer.designerxml.cf.ProjectMetadataTreeBuilder;
import io.github.yellowhammer.designerxml.cf.ProjectMetadataTreeDto;

/**
 * Одна библиотека в двух форматах даёт одно дерево.
 *
 * Проверка перекрёстная: состав, прочитанный из проекта 1С:EDT, сверяется с
 * составом той же конфигурации в выгрузке конфигуратора.
 */
class EdtMatchesDesignerTreeTest {

  /** Вид объекта - его имена, из источника дерева. */
  private static Map<String, Set<String>> objects(ProjectMetadataTreeDto tree, String sourceId) {
    ProjectMetadataTreeDto.MetadataSourceDto source = tree.sources().stream()
        .filter(item -> item.id().equals(sourceId))
        .findFirst()
        .orElseThrow();

    Map<String, Set<String>> objects = new TreeMap<>();
    for (ProjectMetadataTreeDto.MetadataGroupDto group : source.groups()) {
      collect(objects, group.items());
      group.subgroups().forEach(subgroup -> collect(objects, subgroup.items()));
    }
    return objects;
  }

  private static void collect(
      Map<String, Set<String>> objects,
      java.util.List<ProjectMetadataTreeDto.MetadataItemDto> items) {
    for (ProjectMetadataTreeDto.MetadataItemDto item : items) {
      objects.computeIfAbsent(item.objectType(), key -> new TreeSet<>()).add(item.name());
    }
  }

  @Test
  void составСовпадаетСВыгрузкойКонфигуратора() throws Exception {
    ProjectMetadataTreeDto edt =
        ProjectMetadataTreeBuilder.build(Path.of(System.getProperty("fixtures.ssl31edt.root")));
    ProjectMetadataTreeDto designer =
        ProjectMetadataTreeBuilder.build(Path.of(System.getProperty("fixtures.ssl31.root")));

    Map<String, Set<String>> fromEdt = objects(edt, "main");
    Map<String, Set<String>> fromDesigner = objects(designer, "main");

    assertThat(new LinkedHashSet<>(fromEdt.keySet())).isEqualTo(new LinkedHashSet<>(fromDesigner.keySet()));
    assertThat(fromEdt).isEqualTo(fromDesigner);
  }

  @Test
  void составРасширенияСовпадает() throws Exception {
    ProjectMetadataTreeDto edt =
        ProjectMetadataTreeBuilder.build(Path.of(System.getProperty("fixtures.ssl31edt.root")));
    ProjectMetadataTreeDto designer =
        ProjectMetadataTreeBuilder.build(Path.of(System.getProperty("fixtures.ssl31.root")));

    assertThat(objects(edt, "ssl31._ДемоРасширение")).isEqualTo(objects(designer, "_ДемоРасширение"));
  }
}
