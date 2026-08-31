/*
 * This file is a part of md-sparrow.
 *
 * Copyright (c) 2026
 * Ivan Karlo <i.karlo@outlook.com> and contributors
 *
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package io.github.yellowhammer.designerxml.cf;

import java.util.List;

/**
 * Описание скалярного свойства вида без своего моста: тип значения и допустимые
 * значения перечислимого свойства (имена Java-констант модели).
 */
public final class MdScalarPropertyMeta {

  /** Тип значения: {@code string}, {@code boolean}, {@code number}, {@code enum}. */
  public String type;

  /** Имена констант перечислимого свойства; у остальных типов пусто. */
  public List<String> allowed;

  public MdScalarPropertyMeta() {
  }

  public MdScalarPropertyMeta(String type, List<String> allowed) {
    this.type = type;
    this.allowed = allowed;
  }

  @Override
  public boolean equals(Object o) {
    return o instanceof MdScalarPropertyMeta other
      && java.util.Objects.equals(type, other.type)
      && java.util.Objects.equals(allowed, other.allowed);
  }

  @Override
  public int hashCode() {
    return java.util.Objects.hash(type, allowed);
  }
}
