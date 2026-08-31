/*
 * This file is a part of md-sparrow.
 *
 * Copyright (c) 2026
 * Ivan Karlo <i.karlo@outlook.com> and contributors
 *
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package io.github.yellowhammer.designerxml.cf;

import java.util.Objects;

/**
 * Участник состава с режимом: у общего реквизита ссылка несёт использование,
 * условное разделение сохраняется как прочитано.
 */
public final class MdContentMemberDto {

  /** Ссылка на объект: {@code Catalog.Номенклатура}. */
  public String ref;

  /** Режим: имя константы модели ({@code USE}, {@code DONT_USE}, {@code AUTO}). */
  public String mode;

  /** Условное разделение: переписывается как есть. */
  public String conditionalSeparation;

  public MdContentMemberDto() {
  }

  public MdContentMemberDto(String ref, String mode, String conditionalSeparation) {
    this.ref = ref;
    this.mode = mode;
    this.conditionalSeparation = conditionalSeparation;
  }

  @Override
  public boolean equals(Object o) {
    return o instanceof MdContentMemberDto other
      && Objects.equals(ref, other.ref)
      && Objects.equals(mode, other.mode)
      && Objects.equals(conditionalSeparation, other.conditionalSeparation);
  }

  @Override
  public int hashCode() {
    return Objects.hash(ref, mode, conditionalSeparation);
  }
}
