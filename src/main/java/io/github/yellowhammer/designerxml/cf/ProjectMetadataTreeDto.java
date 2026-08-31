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
    /**
     * Правило поддержки самого корня конфигурации: {@code locked} - не
     * редактируется, {@code editable} - редактируется с сохранением поддержки,
     * пусто - правил нет либо корень снят с поддержки.
     */
    String support,
    /**
     * Возможность изменения включена конфигуратором: без неё правила не правятся
     * и остаётся только снятие с поддержки.
     */
    boolean supportEditingEnabled,
    /** Отпечаток правил поддержки на момент чтения дерева: с ним правка сверяется. */
    String supportGeneration,
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
    String relativePath,
    /**
     * Принадлежность объекта расширения: {@code Adopted} у заимствованного из расширяемой
     * конфигурации. У объектов основной конфигурации пусто: там все объекты собственные.
     */
    String objectBelonging,
    /**
     * Поддержка поставщика: {@code locked} - изменение запрещено правилами,
     * {@code editable} - на поддержке с возможностью изменения, пусто - объект
     * не на поддержке либо правил в выгрузке нет.
     */
    String support,
    /**
     * Что открывать по клику в IDE: форма, модуль или окно свойств.
     * Пусто, если у объекта нет файла. Пути — относительно корня проекта.
     */
    MdObjectOpen.Target open
  ) {
  }
}
