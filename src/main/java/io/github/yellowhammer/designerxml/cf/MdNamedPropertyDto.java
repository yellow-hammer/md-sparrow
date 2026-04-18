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
 * Реквизит или табличная часть: имя не меняется через DTO, только синоним ru и комментарий.
 */
public final class MdNamedPropertyDto {

  public String name;
  public String synonymRu;
  public String comment;

  public MdNamedPropertyDto() {
  }

  public MdNamedPropertyDto(String name, String synonymRu, String comment) {
    this.name = name;
    this.synonymRu = synonymRu;
    this.comment = comment;
  }
}
