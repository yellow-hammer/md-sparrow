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
 * Параметр выбора реквизита: имя параметра и его значение текстом.
 *
 * <p>Значение платформа хранит типизированным, поэтому здесь оно только читается: записать
 * его текстом значило бы потерять тип, а с ним и работоспособность отбора.
 */
public final class MdChoiceParameterDto {

  public String name;
  /** Значение параметра текстом, как оно лежит в XML; несколько значений идут через запятую. */
  public String valueText;

  public MdChoiceParameterDto() {
  }

  public MdChoiceParameterDto(String name, String valueText) {
    this.name = name;
    this.valueText = valueText;
  }
}
