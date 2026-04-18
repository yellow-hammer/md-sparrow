/*
 * This file is a part of md-sparrow.
 *
 * Copyright (c) 2026
 * Ivan Karlo <i.karlo@outlook.com> and contributors
 *
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package io.github.yellowhammer.designerxml.cf;

import java.util.ArrayList;
import java.util.List;

/**
 * Свойства объекта метаданных для JSON (cf-md-object-get/set). Поля зависят от {@link #kind}.
 *
 * <p>{@code kind}: {@code catalog} | {@code constant} | {@code enum} | {@code document} | {@code report} |
 * {@code dataProcessor} | {@code task} | {@code chartOfAccounts} | {@code chartOfCharacteristicTypes} |
 * {@code chartOfCalculationTypes} | {@code commonModule} | {@code subsystem} | {@code sessionParameter} |
 * {@code exchangePlan} | {@code commonAttribute} | {@code commonPicture} | {@code documentNumerator} |
 * {@code externalDataSource} | {@code role}.
 */
public final class MdObjectPropertiesDto {

  public String kind;
  public String internalName;
  public String synonymRu;
  public String comment;
  public List<MdNamedPropertyDto> attributes;
  public List<MdNamedPropertyDto> tabularSections;
  /** Подсистемы, вложенные в данную (только для kind=subsystem). */
  public List<String> nestedSubsystems;
  /**
   * Состав подсистемы (ссылки на объекты метаданных) — только чтение; при записи не изменяется.
   */
  public List<String> contentRefs;
  /** Поля {@code CatalogProperties} для {@code kind=catalog}; иначе {@code null}. */
  public MdCatalogPropertiesDto catalog;

  public MdObjectPropertiesDto() {
    this.attributes = new ArrayList<>();
    this.tabularSections = new ArrayList<>();
    this.nestedSubsystems = new ArrayList<>();
    this.contentRefs = new ArrayList<>();
  }
}
