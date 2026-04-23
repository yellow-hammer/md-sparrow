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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * Собирает {@link ProjectMetadataGraphDto} по корню проекта (одна основная выгрузка + расширения {@code src/cfe}
 * + внешние отчёты/обработки {@code src/erf}, {@code src/epf}).
 *
 * <p>Поток:
 * <ol>
 *   <li>Перечень объектов берётся из {@link ProjectMetadataTreeBuilder};</li>
 *   <li>Для каждого объекта с XML-файлом запускается {@link MdObjectGraphExtractor};</li>
 *   <li>Для ролей дополнительно читается {@code Ext/Rights.xml} через {@link RoleRightsGraphReader};</li>
 *   <li>Подсистема пишет в свои узлы {@link ProjectMetadataGraphDto.NodeDto#subsystemKeys()} —
 *     инверсия {@link RelationKind#SUBSYSTEM_MEMBERSHIP};</li>
 *   <li>Дубли рёбер по тройке (sourceKey, targetKey, kind) сжимаются, {@code via} аккумулируется.</li>
 * </ol>
 */
public final class ProjectMetadataGraphBuilder {

  private ProjectMetadataGraphBuilder() {
  }

  /**
   * @param projectRoot корень проекта (где лежат {@code src/cf}, при наличии — {@code src/cfe}, {@code src/epf}, {@code src/erf})
   */
  public static ProjectMetadataGraphDto build(Path projectRoot) throws IOException {
    ProjectMetadataTreeDto tree = ProjectMetadataTreeBuilder.build(projectRoot);
    Path normalized = Path.of(tree.projectRoot());
    Map<String, ProjectMetadataGraphDto.NodeDto> nodes = new LinkedHashMap<>();
    Map<String, List<String>> subsystemKeysByTarget = new LinkedHashMap<>();
    Map<EdgeKey, MutableEdge> edges = new LinkedHashMap<>();
    for (ProjectMetadataTreeDto.MetadataSourceDto source : tree.sources()) {
      collectSource(normalized, source, nodes, edges, subsystemKeysByTarget);
    }
    for (Map.Entry<String, List<String>> entry : subsystemKeysByTarget.entrySet()) {
      ProjectMetadataGraphDto.NodeDto node = nodes.get(entry.getKey());
      if (node != null) {
        nodes.put(entry.getKey(), withSubsystems(node, entry.getValue()));
      }
    }
    List<ProjectMetadataGraphDto.NodeDto> nodeList = new ArrayList<>(nodes.values());
    List<ProjectMetadataGraphDto.EdgeDto> edgeList = new ArrayList<>(edges.size());
    for (MutableEdge me : edges.values()) {
      edgeList.add(new ProjectMetadataGraphDto.EdgeDto(
        me.sourceKey,
        me.targetKey,
        me.kind.wireName(),
        me.kind.defaultCardinality(),
        List.copyOf(me.via)));
    }
    return new ProjectMetadataGraphDto(
      tree.projectRoot(),
      tree.mainSchemaVersion(),
      tree.mainSchemaVersionFlag(),
      nodeList.size(),
      edgeList.size(),
      nodeList,
      edgeList);
  }

  private static void collectSource(
    Path projectRoot,
    ProjectMetadataTreeDto.MetadataSourceDto source,
    Map<String, ProjectMetadataGraphDto.NodeDto> nodes,
    Map<EdgeKey, MutableEdge> edges,
    Map<String, List<String>> subsystemKeysByTarget
  ) throws IOException {
    for (ProjectMetadataTreeDto.MetadataGroupDto group : source.groups()) {
      for (ProjectMetadataTreeDto.MetadataItemDto item : group.items()) {
        processItem(projectRoot, source, item, nodes, edges, subsystemKeysByTarget);
      }
      for (ProjectMetadataTreeDto.MetadataSubgroupDto subgroup : group.subgroups()) {
        for (ProjectMetadataTreeDto.MetadataItemDto item : subgroup.items()) {
          processItem(projectRoot, source, item, nodes, edges, subsystemKeysByTarget);
        }
      }
    }
    appendNestedSubsystems(projectRoot, source, nodes, edges, subsystemKeysByTarget);
  }

  /**
   * Дерево {@link ProjectMetadataTreeBuilder} перечисляет только корневые подсистемы из {@code Configuration.xml}.
   * Для графа важны и вложенные подсистемы — обходим каталог {@code Subsystems} рекурсивно и добавляем их.
   */
  private static void appendNestedSubsystems(
    Path projectRoot,
    ProjectMetadataTreeDto.MetadataSourceDto source,
    Map<String, ProjectMetadataGraphDto.NodeDto> nodes,
    Map<EdgeKey, MutableEdge> edges,
    Map<String, List<String>> subsystemKeysByTarget
  ) throws IOException {
    Path metadataRoot = projectRoot.resolve(source.metadataRootRelativePath());
    Path subsystemsRoot = metadataRoot.resolve("Subsystems");
    if (!Files.isDirectory(subsystemsRoot)) {
      return;
    }
    try (var walk = Files.walk(subsystemsRoot)) {
      var files = walk
        .filter(Files::isRegularFile)
        .filter(p -> p.getFileName().toString().endsWith(".xml"))
        .toList();
      for (Path xml : files) {
        String fileName = xml.getFileName().toString();
        String name = fileName.substring(0, fileName.length() - 4);
        String key = "Subsystem." + name;
        if (nodes.containsKey(key)) {
          continue;
        }
        String relativePath = projectRoot.relativize(xml).toString().replace('\\', '/');
        ProjectMetadataTreeDto.MetadataItemDto fakeItem =
          new ProjectMetadataTreeDto.MetadataItemDto("Subsystem", name, relativePath);
        processItem(projectRoot, source, fakeItem, nodes, edges, subsystemKeysByTarget);
      }
    }
  }

  private static void processItem(
    Path projectRoot,
    ProjectMetadataTreeDto.MetadataSourceDto source,
    ProjectMetadataTreeDto.MetadataItemDto item,
    Map<String, ProjectMetadataGraphDto.NodeDto> nodes,
    Map<EdgeKey, MutableEdge> edges,
    Map<String, List<String>> subsystemKeysByTarget
  ) throws IOException {
    String key = item.objectType() + "." + item.name();
    Path xml = item.relativePath() == null || item.relativePath().isEmpty()
      ? null
      : projectRoot.resolve(item.relativePath());
    String synonym = "";
    boolean partial = !MdObjectGraphExtractor.isSupported(item.objectType());
    if (xml != null && Files.isRegularFile(xml) && hasMdObjectRoot(item.objectType())) {
      MdObjectGraphExtractor.Inspection inspection = MdObjectGraphExtractor.inspect(xml, item.objectType());
      synonym = inspection.synonymRu();
      partial = inspection.partial();
      for (MdObjectGraphExtractor.OutEdge edge : inspection.edges()) {
        addEdge(edges, key, edge.targetKey(), edge.kind(), edge.via());
        if (edge.kind() == RelationKind.SUBSYSTEM_MEMBERSHIP) {
          // membership: source — подсистема (ключ key), target — объект-член; навешиваем подсистему на target
          List<String> subs = subsystemKeysByTarget.computeIfAbsent(edge.targetKey(), k -> new ArrayList<>());
          if (!subs.contains(key)) {
            subs.add(key);
          }
        }
      }
    }
    if ("Role".equals(item.objectType())) {
      Path rightsXml = projectRoot
        .resolve(source.metadataRootRelativePath())
        .resolve("Roles")
        .resolve(item.name())
        .resolve("Ext")
        .resolve("Rights.xml");
      for (MdObjectGraphExtractor.OutEdge edge : RoleRightsGraphReader.readEdges(rightsXml)) {
        addEdge(edges, key, edge.targetKey(), edge.kind(), edge.via());
      }
    }
    nodes.putIfAbsent(key, new ProjectMetadataGraphDto.NodeDto(
      key,
      item.objectType(),
      item.name(),
      synonym,
      source.id(),
      item.relativePath(),
      List.of(),
      partial));
  }

  /** Внешние отчёты/обработки лежат в .erf/.epf — у них нет XML-корня {@code MetaDataObject} в обычном виде. */
  private static boolean hasMdObjectRoot(String objectType) {
    return !"ExternalReport".equals(objectType) && !"ExternalDataProcessor".equals(objectType);
  }

  private static void addEdge(
    Map<EdgeKey, MutableEdge> edges,
    String sourceKey,
    String targetKey,
    RelationKind kind,
    String via
  ) {
    if (sourceKey.equals(targetKey)) {
      return;
    }
    EdgeKey k = new EdgeKey(sourceKey, targetKey, kind);
    MutableEdge me = edges.computeIfAbsent(k, key -> new MutableEdge(sourceKey, targetKey, kind));
    if (via != null && !via.isEmpty()) {
      me.via.add(via);
    }
  }

  private static ProjectMetadataGraphDto.NodeDto withSubsystems(
    ProjectMetadataGraphDto.NodeDto node,
    List<String> subsystemKeys
  ) {
    return new ProjectMetadataGraphDto.NodeDto(
      node.key(),
      node.objectType(),
      node.name(),
      node.synonymRu(),
      node.sourceId(),
      node.relativePath(),
      List.copyOf(subsystemKeys),
      node.partial());
  }

  private record EdgeKey(String sourceKey, String targetKey, RelationKind kind) {
  }

  private static final class MutableEdge {
    final String sourceKey;
    final String targetKey;
    final RelationKind kind;
    final LinkedHashSet<String> via = new LinkedHashSet<>();

    MutableEdge(String sourceKey, String targetKey, RelationKind kind) {
      this.sourceKey = sourceKey;
      this.targetKey = targetKey;
      this.kind = kind;
    }
  }
}
