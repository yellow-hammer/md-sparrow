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
import java.util.Map;
import java.util.Optional;

import io.github.yellowhammer.designerxml.cf.MdObjectGraphExtractor;
import io.github.yellowhammer.designerxml.cf.MetadataRefParser;
import io.github.yellowhammer.designerxml.cf.RelationKind;
import io.github.yellowhammer.edt.EdtObjectReader.EdtNode;

/**
 * Связи объекта 1С:EDT для диаграммы.
 *
 * Ссылки на другие объекты в файле записаны текстом: {@code CatalogRef.Валюты} у
 * типа реквизита, {@code Catalog.Валюты} у владельца или состава подсистемы. Вид
 * связи виден по имени элемента, в котором ссылка стоит.
 */
public final class EdtObjectGraph {

  /**
   * Вид связи по элементу, в котором стоит ссылка.
   *
   * Ключ - имя элемента, а у типов ещё и владелец: тип измерения регистра и тип
   * реквизита справочника диаграмма показывает разными связями.
   */
  private static final Map<String, RelationKind> BY_FEATURE = Map.ofEntries(
      Map.entry("owners", RelationKind.CATALOG_OWNERS),
      Map.entry("use", RelationKind.FOP_USE_BINDING),
      Map.entry("basedOn", RelationKind.DOCUMENT_BASED_ON),
      Map.entry("subsystems", RelationKind.SUBSYSTEM_NESTING),
      Map.entry("documents", RelationKind.SEQUENCE_DOCUMENTS),
      Map.entry("registers", RelationKind.SEQUENCE_REGISTERS),
      Map.entry("registeredDocuments", RelationKind.DOCUMENT_JOURNAL_ENTRIES),
      Map.entry("location", RelationKind.FUNCTIONAL_OPTION_LOCATION),
      Map.entry("source", RelationKind.SUBSCRIPTION_SOURCE),
      Map.entry("handler", RelationKind.SUBSCRIPTION_HANDLER),
      Map.entry("methodName", RelationKind.SCHEDULED_JOB_HANDLER),
      Map.entry("chartOfAccounts", RelationKind.REGISTER_CHART_OF_ACCOUNTS),
      Map.entry("chartOfCalculationTypes", RelationKind.REGISTER_CHART_OF_CALCULATION_TYPES),
      Map.entry("extDimensionTypes", RelationKind.CHART_OF_ACCOUNTS_EXT_DIMENSIONS),
      Map.entry("characteristicExtValues", RelationKind.CHARACTERISTIC_EXT_VALUES),
      Map.entry("commandParameterType", RelationKind.COMMAND_PARAMETER_TYPE));

  /** Вид связи у движений: у документа это проведение, у последовательности - её регистры. */
  private static final Map<String, RelationKind> RECORDS_BY_OWNER = Map.of(
      "Sequence", RelationKind.SEQUENCE_REGISTERS);

  /** Вид связи у состава: он бывает у подсистемы, общего реквизита и функциональной опции. */
  private static final Map<String, RelationKind> CONTENT_BY_OWNER = Map.of(
      "Subsystem", RelationKind.SUBSYSTEM_MEMBERSHIP,
      "CommonAttribute", RelationKind.COMMON_ATTRIBUTE_USAGE,
      "FunctionalOption", RelationKind.FUNCTIONAL_OPTION_AFFECTED,
      "FilterCriterion", RelationKind.FILTER_CRITERION_CONTENT);

  /** Вид связи у типа: он зависит от того, чей это узел. */
  private static final Map<String, RelationKind> TYPE_BY_NODE = Map.of(
      "dimensions", RelationKind.REGISTER_DIMENSION_TYPE,
      "resources", RelationKind.REGISTER_RESOURCE_TYPE,
      "commands", RelationKind.COMMAND_PARAMETER_TYPE);

  private EdtObjectGraph() {
  }

  /**
   * Читает синоним и исходящие связи объекта.
   *
   * @param objectMdo файл объекта
   * @param objectType вид объекта: {@code Catalog}
   * @return синоним, связи и признак неполного разбора
   * @throws IOException если файл не читается
   */
  public static MdObjectGraphExtractor.Inspection inspect(Path objectMdo, String objectType) throws IOException {
    EdtNode object = EdtObjectReader.read(objectMdo);
    List<MdObjectGraphExtractor.OutEdge> edges = new ArrayList<>();
    collect(object, objectType.isEmpty() ? object.kind() : objectType, "", edges);
    return new MdObjectGraphExtractor.Inspection(
        EdtPropertyValues.russian(object, "synonym"), edges, false);
  }

  /** Обходит узел за узлом: ссылки встречаются на любой глубине. */
  private static void collect(
      EdtNode node,
      String objectType,
      String path,
      List<MdObjectGraphExtractor.OutEdge> edges) {
    for (EdtNode child : node.children()) {
      String via = path.isEmpty() ? child.kind() : path + "/" + child.kind();
      RelationKind kind = kindOf(child.kind(), via, objectType);
      if (kind != null) {
        // Вложенные подсистемы перечислены именами: вид у них и так известен
        String value = child.value();
        Optional<String> targetKey = kind == RelationKind.SUBSYSTEM_NESTING && !value.isEmpty()
            ? Optional.of("Subsystem." + value)
            : target(value);
        targetKey.ifPresent(key -> edges.add(new MdObjectGraphExtractor.OutEdge(key, kind, via)));
      }
      collect(child, objectType, via, edges);
    }
  }

  /**
   * Вид связи или {@code null}, если ссылка диаграмме не нужна.
   *
   * @param feature имя элемента, в котором стоит ссылка
   * @param via путь к элементу от корня объекта
   * @param objectType вид объекта
   */
  private static RelationKind kindOf(String feature, String via, String objectType) {
    // Состав общего реквизита ссылается на объект вложенным элементом: рядом
    // лежит режим использования
    if (feature.equals("content") || via.endsWith("content/metadata")) {
      return CONTENT_BY_OWNER.get(objectType);
    }
    if (feature.equals("types")) {
      return typeKind(via, objectType);
    }
    if (feature.equals("registerRecords")) {
      return RECORDS_BY_OWNER.getOrDefault(objectType, RelationKind.DOCUMENT_POSTING_REGISTERS);
    }
    // Основание ввода бизнес-процесса диаграмма не показывает
    if (feature.equals("basedOn") && objectType.equals("BusinessProcess")) {
      return null;
    }
    return BY_FEATURE.get(feature);
  }

  /** Вид связи у типа значения: он зависит от узла, которому тип принадлежит. */
  private static RelationKind typeKind(String via, String objectType) {
    // У последовательности связи - это её документы и регистры, а измерения
    // повторяют их же
    if (objectType.equals("Sequence")) {
      return null;
    }
    for (Map.Entry<String, RelationKind> node : TYPE_BY_NODE.entrySet()) {
      if (via.startsWith(node.getKey() + "/")) {
        return node.getValue();
      }
    }
    if (via.startsWith("source/")) {
      return RelationKind.SUBSCRIPTION_SOURCE;
    }
    if (via.contains("commandParameterType/")) {
      return RelationKind.COMMAND_PARAMETER_TYPE;
    }
    return objectType.equals("FilterCriterion")
        ? RelationKind.FILTER_CRITERION_TYPE
        : RelationKind.TYPE_COMPOSITE;
  }

  /**
   * Объект, на который указывает значение.
   *
   * Ссылка бывает и на объект ({@code Catalog.Валюты}), и на его тип
   * ({@code CatalogRef.Валюты}): для диаграммы это один и тот же объект.
   */
  private static Optional<String> target(String value) {
    if (value.isEmpty() || value.indexOf('.') < 0) {
      return Optional.empty();
    }
    Optional<String> byType = MetadataRefParser.normalizeTypeRef(value);
    return byType.isPresent() ? byType : MetadataRefParser.normalizeMdObjectRef(value);
  }

}
