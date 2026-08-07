/*
 * This file is a part of md-sparrow.
 *
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package io.github.yellowhammer.designerxml.cf;

/**
 * Одно изменение свойства элемента формы.
 *
 * <p>В файле лежат только изменённые свойства, поэтому «свойство со значением по умолчанию» и
 * «свойство убрано из файла» - одно и то же: чтобы вернуть значение по умолчанию, {@link #value}
 * не задают.
 */
public final class FormItemPropertyChangeDto {

  /** Идентификатор элемента формы: атрибут {@code id}. */
  public String itemId;

  /** Имя узла свойства в XML: {@code Visible}, {@code Title}. */
  public String property;

  /** Новое значение как в файле; не задано - свойство убирается из файла. */
  public String value;
}
