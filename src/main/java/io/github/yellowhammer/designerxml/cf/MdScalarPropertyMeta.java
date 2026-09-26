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
