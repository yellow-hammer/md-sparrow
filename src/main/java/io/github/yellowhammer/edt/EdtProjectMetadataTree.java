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
import java.util.LinkedHashMap;
import java.util.Map;

import io.github.yellowhammer.designerxml.cf.ChildObjectEntry;
import io.github.yellowhammer.designerxml.cf.LocalStrings;
import io.github.yellowhammer.designerxml.cf.SupportRules;
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
    return build(root, EdtLayout.projects(root));
  }

  /**
   * Собирает дерево метаданных из заданных проектов: конфигурация и её расширения,
   * где бы в рабочей области они ни лежали.
   *
   * @param workspaceRoot корень рабочей области
   * @param projects каталоги проектов EDT
   * @return дерево источников: конфигурация и её расширения
   * @throws IOException если проекты не читаются
   */
  public static ProjectMetadataTreeDto build(Path workspaceRoot, List<Path> projects) throws IOException {
    Path root = workspaceRoot.toAbsolutePath().normalize();
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
    // Правила поддержки у проекта EDT лежат в файле поставки рядом с описанием конфигурации
    EdtSupportRules.Rules support = SupportRules.isEnforced()
        ? EdtSupportRules.read(configurationMdo)
        : new EdtSupportRules.Rules();
    // Язык и прочие объекты без своего файла описаны узлами конфигурации: их идентификаторы там же
    Map<String, String> inlineUuids = inlineUuids(configuration, model);
    List<ProjectMetadataTreeDto.MetadataGroupDto> groups = mapGroups(
        workspaceRoot, sourceRoot, MetadataTreeTagGroups.buildGroups(entries), isExtension, support, inlineUuids);

    String name = configuration.name();
    return new ProjectMetadataTreeDto.MetadataSourceDto(
        isExtension ? "extension" : "main",
        isExtension ? project.getFileName().toString() : "main",
        name.isEmpty() ? project.getFileName().toString() : name,
        relative(workspaceRoot, configurationMdo),
        relative(workspaceRoot, sourceRoot),
        "",
        true,
        support.isEmpty() ? null : support.effectiveState(configuration.uuid()),
        support.editingEnabled && !support.isEmpty(),
        support.isEmpty() ? null : support.generationId,
        groups);
  }

  private static List<ProjectMetadataTreeDto.MetadataGroupDto> mapGroups(
      Path workspaceRoot,
      Path sourceRoot,
      List<MetadataTreeTagGroups.MetadataTreeGroupPayload> payloads,
      boolean readBelonging,
      EdtSupportRules.Rules support,
      Map<String, String> inlineUuids) throws IOException {
    List<ProjectMetadataTreeDto.MetadataGroupDto> groups = new ArrayList<>();
    for (MetadataTreeTagGroups.MetadataTreeGroupPayload payload : payloads) {
      List<ProjectMetadataTreeDto.MetadataSubgroupDto> subgroups = new ArrayList<>();
      for (MetadataTreeTagGroups.MetadataSubgroupPayload subgroup : payload.subgroups()) {
        subgroups.add(new ProjectMetadataTreeDto.MetadataSubgroupDto(
            subgroup.id(),
            subgroup.label(),
            subgroup.iconHint(),
            items(workspaceRoot, sourceRoot, subgroup.items(), readBelonging, support, inlineUuids)));
      }
      groups.add(new ProjectMetadataTreeDto.MetadataGroupDto(
          payload.id(),
          payload.label(),
          payload.iconHint(),
          items(workspaceRoot, sourceRoot, payload.items(), readBelonging, support, inlineUuids),
          subgroups));
    }
    return groups;
  }

  private static List<ProjectMetadataTreeDto.MetadataItemDto> items(
      Path workspaceRoot,
      Path sourceRoot,
      List<MetadataTreeTagGroups.MetadataTreeItemPayload> payloads,
      boolean readBelonging,
      EdtSupportRules.Rules support,
      Map<String, String> inlineUuids) throws IOException {
    List<ProjectMetadataTreeDto.MetadataItemDto> items = new ArrayList<>();
    for (MetadataTreeTagGroups.MetadataTreeItemPayload payload : payloads) {
      Path objectMdo = EdtLayout.objectMdo(sourceRoot, payload.objectType(), payload.name()).orElse(null);
      String relativePath = objectMdo == null ? "" : relative(workspaceRoot, objectMdo);
      // Описание объекта читается один раз: из него и принадлежность, и синоним, и идентификатор
      EdtObjectReader.EdtNode node = objectMdo == null ? null : EdtObjectReader.read(objectMdo);
      items.add(new ProjectMetadataTreeDto.MetadataItemDto(
          payload.objectType(),
          payload.name(),
          relativePath,
          readBelonging ? belonging(node) : null,
          supportState(node, support, inlineUuids.get(payload.objectType() + "." + payload.name())),
          synonym(node),
          EdtObjectOpen.resolve(workspaceRoot, payload.objectType(), objectMdo)));
    }
    return items;
  }

  /** Состояние поддержки объекта по идентификатору из шапки его описания либо из узла конфигурации. */
  private static String supportState(
      EdtObjectReader.EdtNode node, EdtSupportRules.Rules support, String inlineUuid) {
    if (support.isEmpty()) {
      return null;
    }
    String uuid = node == null ? inlineUuid : node.uuid();
    return uuid == null || uuid.isEmpty() ? null : support.effectiveState(uuid);
  }

  /** Идентификаторы объектов, записанных узлами описания конфигурации: вид.имя. */
  private static Map<String, String> inlineUuids(EdtObjectReader.EdtNode configuration, EdtModel model) {
    Map<String, String> uuids = new java.util.HashMap<>();
    for (EdtModel.Composition item : model.composition("Configuration")) {
      if (!item.inline()) {
        continue;
      }
      for (EdtObjectReader.EdtNode node : configuration.list(item.feature())) {
        if (!node.uuid().isEmpty()) {
          uuids.put(item.objectType() + "." + node.name(), node.uuid());
        }
      }
    }
    return uuids;
  }

  /**
   * Принадлежность объекта расширения: у своих объектов свойство не записано.
   *
   * Спрашивается только у расширений: в основной конфигурации все объекты свои,
   * а файл пришлось бы открыть у каждого.
   */
  private static String belonging(EdtObjectReader.EdtNode node) {
    if (node == null) {
      return null;
    }
    String belonging = node.property("objectBelonging");
    return belonging.isEmpty() ? null : belonging;
  }

  /** Синоним объекта: по нему ищут в дереве. */
  private static String synonym(EdtObjectReader.EdtNode node) {
    if (node == null) {
      return null;
    }
    Map<String, String> byLanguage = new LinkedHashMap<>();
    for (EdtObjectReader.EdtNode entry : node.list("synonym")) {
      String key = entry.property("key");
      String value = entry.property("value");
      if (!key.isEmpty() && !value.isEmpty()) {
        byLanguage.put(key, value);
      }
    }
    return LocalStrings.pick(byLanguage);
  }

  private static String relative(Path workspaceRoot, Path target) {
    return workspaceRoot.relativize(target.toAbsolutePath().normalize()).toString().replace('\\', '/');
  }
}
