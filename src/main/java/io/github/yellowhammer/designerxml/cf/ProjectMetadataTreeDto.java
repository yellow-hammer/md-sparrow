/*
 * This file is a part of md-sparrow.
 *
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package io.github.yellowhammer.designerxml.cf;

import java.util.List;

/**
 * JSON-контракт дерева метаданных для IDE (корень проекта: {@code src/cf}, {@code src/cfe}, {@code src/epf}, {@code src/erf}).
 */
public record ProjectMetadataTreeDto(
  /** Абсолютный нормализованный путь к корню проекта. */
  String projectRoot,
  /** Значение {@code MetaDataObject/@version} основной выгрузки ({@code "2.20"} и т.д.). */
  String mainSchemaVersion,
  /** Флаг enum для CLI ({@code "V2_21"}). */
  String mainSchemaVersionFlag,
  List<MetadataSourceDto> sources
) {

  public record MetadataSourceDto(
    /**
     * {@code main}, {@code extension}, {@code externalErf} (внешние отчёты), {@code externalEpf} (внешние обработки).
     */
    String kind,
    /** Стабильный id: {@code main} или имя каталога расширения. */
    String id,
    /** Подпись в дереве. */
    String label,
    /** Путь к {@code Configuration.xml} относительно корня проекта. */
    String configurationXmlRelativePath,
    /** Каталог выгрузки ({@code src/cf} или {@code src/cfe/…}) относительно корня проекта. */
    String metadataRootRelativePath,
    List<MetadataGroupDto> groups
  ) {
  }

  public record MetadataGroupDto(
    String id,
    String label,
    /** Подсказка для иконки в IDE (codicon id). */
    String iconHint,
    /** Плоский список; для группы «Общие» — пусто, если заданы {@link #subgroups()}. */
    List<MetadataItemDto> items,
    /** Подгруппы (только для «Общие»); иначе пустой список. */
    List<MetadataSubgroupDto> subgroups
  ) {
  }

  public record MetadataSubgroupDto(
    String id,
    String label,
    String iconHint,
    List<MetadataItemDto> items
  ) {
  }

  public record MetadataItemDto(
    String objectType,
    String name,
    /** Путь к файлу объекта относительно корня проекта; для объектов без одного файла — пусто. */
    String relativePath
  ) {
  }
}
