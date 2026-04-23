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

import java.util.List;

/**
 * JSON-контракт графа метаданных проекта: узлы (объекты метаданных) и типизированные ссылочные связи между ними.
 *
 * <p>Контракт предназначен для одного вызова из IDE: расширение получает граф и фильтрует/визуализирует его без
 * дополнительных вызовов CLI на каждый объект.
 */
public record ProjectMetadataGraphDto(
  /** Абсолютный нормализованный путь к корню проекта. */
  String projectRoot,
  /** Значение {@code MetaDataObject/@version} основной выгрузки ({@code "2.20"} и т.д.). */
  String mainSchemaVersion,
  /** Флаг enum для CLI ({@code "V2_21"}). */
  String mainSchemaVersionFlag,
  int nodeCount,
  int edgeCount,
  List<NodeDto> nodes,
  List<EdgeDto> edges
) {

  /**
   * Узел графа метаданных.
   */
  public record NodeDto(
    /** Стабильный ключ {@code <objectType>.<name>} (короткое имя без префикса источника). */
    String key,
    /** Тип объекта (Catalog, Document, Subsystem, …). */
    String objectType,
    String name,
    /** Синоним на русском, если есть. */
    String synonymRu,
    /** Источник: {@code main}, имя расширения {@code <name>}, {@code external-erf}, {@code external-epf}. */
    String sourceId,
    /** Путь к XML относительно корня проекта; пусто, если объект без отдельного файла. */
    String relativePath,
    /** Подсистемы, в состав которых входит объект (по {@link RelationKind#SUBSYSTEM_MEMBERSHIP}). */
    List<String> subsystemKeys,
    /** {@code true}, если граф для типа собран частично (нет специализированного reader-а). */
    boolean partial
  ) {
  }

  /**
   * Ребро графа: типизированная ссылка от исходного объекта к целевому.
   */
  public record EdgeDto(
    /** Ключ источника ({@code <objectType>.<name>}). */
    String sourceKey,
    /** Ключ цели ({@code <objectType>.<name>}). */
    String targetKey,
    /** Стабильный id вида связи (см. {@link RelationKind#wireName()}). */
    String kind,
    /** Кардинальность: {@code "0..*"} | {@code "1..1"} | {@code "1..*"}. */
    String cardinality,
    /** Где в исходном объекте найдена ссылка (логический путь, например {@code "owners[0]"}). */
    List<String> via
  ) {
  }
}
