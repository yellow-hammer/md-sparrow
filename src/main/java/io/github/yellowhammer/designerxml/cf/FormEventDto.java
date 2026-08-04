/*
 * This file is a part of md-sparrow.
 *
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package io.github.yellowhammer.designerxml.cf;

/**
 * Обработчик события формы или её элемента.
 */
public final class FormEventDto {

  /** Имя события, например {@code OnCreateAtServer}. */
  public String name;

  /** Имя процедуры в модуле формы. */
  public String handler;

  /** Вид вызова обработчика. */
  public String callType;
}
