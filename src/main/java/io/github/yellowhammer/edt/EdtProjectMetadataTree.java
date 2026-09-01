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

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import io.github.yellowhammer.designerxml.cf.ChildObjectEntry;
import io.github.yellowhammer.designerxml.cf.MetadataTreeTagGroups;
import io.github.yellowhammer.designerxml.cf.ProjectMetadataTreeDto;

/**
 * Дерево метаданных проектов 1С:EDT.
 *
 * Дерево то же самое, что у выгрузки конфигуратора: те же группы, порядок и
 * контракт. Различается только раскладка файлов, и она вся собрана в
 * {@link EdtLayout}.
 *
 * Расширения в EDT - отдельные проекты рабочей области, поэтому источник дерева
 * получается на каждый проект.
 */
public final class EdtProjectMetadataTree {

  private EdtProjectMetadataTree() {
  }

  /**
   * Собирает дерево метаданных рабочей области.
   *
   * @param workspaceRoot корень рабочей области с проектами EDT
   * @return дерево источников: конфигурация и её расширения
   * @throws IOException если проекты не читаются
   */
  public static ProjectMetadataTreeDto build(Path workspaceRoot) throws IOException {
    Path root = workspaceRoot.toAbsolutePath().normalize();
    List<Path> projects = EdtLayout.projects(root);
    if (projects.isEmpty()) {
      throw new IOException("В рабочей области нет проектов 1С:EDT: " + root);
    }

    EdtModel model = EdtModel.bundled();
    List<ProjectMetadataTreeDto.MetadataSourceDto> sources = new ArrayList<>();
    List<ProjectMetadataTreeDto.MetadataSourceDto> extensions = new ArrayList<>();
    for (Path project : projects) {
      ProjectMetadataTreeDto.MetadataSourceDto source = buildSource(root, project, model);
      // Расширения идут после конфигурации, как в выгрузке конфигуратора
      (source.kind().equals("extension") ? extensions : sources).add(source);
    }
    sources.addAll(extensions);

    // Версия схемы - свойство выгрузки конфигуратора; у EDT её место занимает
    // версия метамодели, по которой прочитаны проекты
    return new ProjectMetadataTreeDto(root.toString(), model.version(), "", sources);
  }

  /** Источник дерева: один проект EDT. */
  private static ProjectMetadataTreeDto.MetadataSourceDto buildSource(
      Path workspaceRoot,
      Path project,
      EdtModel model) throws IOException {
    Path configurationMdo = EdtLayout.configurationMdo(project);
    EdtObjectReader.EdtNode configuration = EdtObjectReader.read(configurationMdo);
    boolean isExtension = !configuration.list("extension").isEmpty();

    Path sourceRoot = project.resolve(EdtLayout.SOURCE_DIR);
    List<ChildObjectEntry> entries = EdtConfigurationReader.listChildObjects(configurationMdo, model);
    List<ProjectMetadataTreeDto.MetadataGroupDto> groups = mapGroups(
        workspaceRoot, sourceRoot, MetadataTreeTagGroups.buildGroups(entries), isExtension);

    String name = configuration.name();
    return new ProjectMetadataTreeDto.MetadataSourceDto(
        isExtension ? "extension" : "main",
        isExtension ? project.getFileName().toString() : "main",
        name.isEmpty() ? project.getFileName().toString() : name,
        relative(workspaceRoot, configurationMdo),
        relative(workspaceRoot, sourceRoot),
        "",
        true,
        null,
        false,
        null,
        groups);
  }

  private static List<ProjectMetadataTreeDto.MetadataGroupDto> mapGroups(
      Path workspaceRoot,
      Path sourceRoot,
      List<MetadataTreeTagGroups.MetadataTreeGroupPayload> payloads,
      boolean readBelonging) throws IOException {
    List<ProjectMetadataTreeDto.MetadataGroupDto> groups = new ArrayList<>();
    for (MetadataTreeTagGroups.MetadataTreeGroupPayload payload : payloads) {
      List<ProjectMetadataTreeDto.MetadataSubgroupDto> subgroups = new ArrayList<>();
      for (MetadataTreeTagGroups.MetadataSubgroupPayload subgroup : payload.subgroups()) {
        subgroups.add(new ProjectMetadataTreeDto.MetadataSubgroupDto(
            subgroup.id(),
            subgroup.label(),
            subgroup.iconHint(),
            items(workspaceRoot, sourceRoot, subgroup.items(), readBelonging)));
      }
      groups.add(new ProjectMetadataTreeDto.MetadataGroupDto(
          payload.id(),
          payload.label(),
          payload.iconHint(),
          items(workspaceRoot, sourceRoot, payload.items(), readBelonging),
          subgroups));
    }
    return groups;
  }

  private static List<ProjectMetadataTreeDto.MetadataItemDto> items(
      Path workspaceRoot,
      Path sourceRoot,
      List<MetadataTreeTagGroups.MetadataTreeItemPayload> payloads,
      boolean readBelonging) throws IOException {
    List<ProjectMetadataTreeDto.MetadataItemDto> items = new ArrayList<>();
    for (MetadataTreeTagGroups.MetadataTreeItemPayload payload : payloads) {
      Path objectMdo = EdtLayout.objectMdo(sourceRoot, payload.objectType(), payload.name()).orElse(null);
      String relativePath = objectMdo == null ? "" : relative(workspaceRoot, objectMdo);
      items.add(new ProjectMetadataTreeDto.MetadataItemDto(
          payload.objectType(),
          payload.name(),
          relativePath,
          readBelonging ? belonging(objectMdo) : null,
          null,
          EdtObjectOpen.resolve(workspaceRoot, payload.objectType(), objectMdo)));
    }
    return items;
  }

  /**
   * Принадлежность объекта расширения: у своих объектов свойство не записано.
   *
   * Спрашивается только у расширений: в основной конфигурации все объекты свои,
   * а файл пришлось бы открыть у каждого.
   */
  private static String belonging(Path objectMdo) throws IOException {
    if (objectMdo == null) {
      return null;
    }
    String belonging = EdtObjectReader.read(objectMdo).property("objectBelonging");
    return belonging.isEmpty() ? null : belonging;
  }

  private static String relative(Path workspaceRoot, Path target) {
    return workspaceRoot.relativize(target.toAbsolutePath().normalize()).toString().replace('\\', '/');
  }
}
