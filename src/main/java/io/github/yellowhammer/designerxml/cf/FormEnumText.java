/*
 * This file is a part of md-sparrow.
 *
 * SPDX-License-Identifier: LGPL-3.0-or-later
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
