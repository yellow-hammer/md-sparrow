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
