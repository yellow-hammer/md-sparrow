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
 * Реквизит, табличная часть, значение перечисления, измерение или ресурс: имя не меняется через DTO,
 * только синоним ru, комментарий и тип.
 *
 * <p>{@link #type} есть у того, у чего платформа его требует; у табличных частей и значений
 * перечисления он {@code null}.
 */
public final class MdNamedPropertyDto {

  public String name;
  public String synonymRu;
  public String comment;
  public MdTypeDescriptionDto type;

  public MdNamedPropertyDto() {
  }

  public MdNamedPropertyDto(String name, String synonymRu, String comment) {
    this.name = name;
    this.synonymRu = synonymRu;
    this.comment = comment;
  }
}
