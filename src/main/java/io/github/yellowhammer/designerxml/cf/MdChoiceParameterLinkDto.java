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
 * Связь параметра выбора: чем реквизит отбирает список при вводе.
 *
 * <p>Все три поля текстовые, поэтому связь читается и пишется целиком, в отличие от
 * {@link MdChoiceParameterDto}, где значение типизировано.
 */
public final class MdChoiceParameterLinkDto {

  /** Имя параметра выбора, например {@code Отбор.Владелец}. */
  public String name;
  /** Путь к данным формы, откуда берётся значение. */
  public String dataPath;
  /** Режим изменения: {@code Any}, {@code Clear}, {@code DontClear}; пусто - как у платформы по умолчанию. */
  public String mode;

  public MdChoiceParameterLinkDto() {
  }

  public MdChoiceParameterLinkDto(String name, String dataPath, String mode) {
    this.name = name;
    this.dataPath = dataPath;
    this.mode = mode;
  }
}
