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

import jakarta.xml.bind.annotation.XmlEnumValue;

/**
 * Текст значения перечисления так, как его пишет платформа ({@code VeryHigh}, а не {@code VERY_HIGH}).
 *
 * <p>Редактор формы показывает значения свойств рядом с тем, что лежит в файле, поэтому имена
 * java-констант модели наружу не отдаём.
 */
public final class FormEnumText {

  private FormEnumText() {
  }

  /**
   * @param constant константа перечисления модели
   * @return значение из схемы, если оно объявлено, иначе имя константы
   */
  public static String of(Enum<?> constant) {
    try {
      XmlEnumValue annotation = constant.getClass().getField(constant.name()).getAnnotation(XmlEnumValue.class);
      return annotation == null ? constant.name() : annotation.value();
    } catch (NoSuchFieldException e) {
      return constant.name();
    }
  }
}
