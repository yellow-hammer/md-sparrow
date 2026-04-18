/*
 * This file is a part of md-sparrow.
 *
 * Copyright (c) 2026
 * Ivan Karlo <i.karlo@outlook.com> and contributors
 *
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package io.github.yellowhammer.designerxml.cf;

/**
 * Поля справочника для формы редактирования (JSON в/из CLI); запись в XML только через {@link CatalogFormEdit}.
 */
public final class CatalogFormDto {

  /** Имя объекта (как в конфигураторе); не меняется через форму. */
  public String internalName;

  /** Синоним ru (и связанные представления — как при создании из прототипа). */
  public String synonymRu;

  /** Комментарий. */
  public String comment;

  public CatalogFormDto() {
  }

  public CatalogFormDto(String internalName, String synonymRu, String comment) {
    this.internalName = internalName;
    this.synonymRu = synonymRu;
    this.comment = comment;
  }
}
