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
