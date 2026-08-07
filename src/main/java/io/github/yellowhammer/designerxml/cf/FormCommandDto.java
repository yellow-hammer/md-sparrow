/*
 * This file is a part of md-sparrow.
 *
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package io.github.yellowhammer.designerxml.cf;

/**
 * Команда формы.
 */
public final class FormCommandDto {

  public String name;

  /** Заголовок (ru). */
  public String title;

  /** Процедура-обработчик команды в модуле формы. */
  public String action;
}
