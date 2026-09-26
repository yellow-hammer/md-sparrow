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

/**
 * Поля справочника для формы редактирования (JSON в/из CLI); запись в XML только через {@link CatalogFormEdit}.
 */
public final class CatalogFormDto {

  /** Имя объекта (как в конфигураторе); не меняется через форму. */
  public String internalName;

  /** Синоним ru (и связанные представления — как при создании из прототипа). */
  @LocalString
  public String synonym;

  /** Комментарий. */
  public String comment;

  public CatalogFormDto() {
  }

  public CatalogFormDto(String internalName, String synonym, String comment) {
    this.internalName = internalName;
    this.synonym = synonym;
    this.comment = comment;
  }
}
