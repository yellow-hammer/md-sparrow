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
