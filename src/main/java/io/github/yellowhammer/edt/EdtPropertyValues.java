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
package io.github.yellowhammer.edt;

import java.util.List;
import java.util.Locale;

import org.eclipse.emf.ecore.EAttribute;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EEnum;
import org.eclipse.emf.ecore.EStructuralFeature;

import io.github.yellowhammer.edt.EdtObjectReader.EdtNode;

/**
 * Значения свойств объекта 1С:EDT в терминах общего контракта.
 *
 * Файл EDT записывает не все свойства: со значением по умолчанию их там просто
 * нет, и умолчание приходится спрашивать у схемы. Перечислимые значения тоже
 * записаны по-своему ({@code DontUse}), а контракт несёт имена констант модели
 * ({@code DONT_USE}), общие для обоих форматов.
 */
final class EdtPropertyValues {

  private EdtPropertyValues() {
  }

  /**
   * Значение свойства как строка.
   *
   * @param node узел объекта
   * @param eClass класс объекта в метамодели
   * @param name имя свойства
   * @return значение из файла, иначе умолчание схемы, иначе пустая строка
   */
  static String text(EdtNode node, EClass eClass, String name) {
    List<EdtNode> written = node.list(name);
    if (!written.isEmpty()) {
      return enumConstant(feature(eClass, name), written.get(0).value());
    }
    Object fallback = defaultValue(eClass, name);
    return fallback == null ? "" : enumConstant(feature(eClass, name), String.valueOf(fallback));
  }

  /**
   * Значение многоязычного свойства на русском.
   *
   * Синоним, подсказка и пояснение записаны парами язык-значение, и панели
   * нужна русская строка.
   *
   * @param node узел объекта
   * @param name имя свойства
   * @return строка на русском или пустая
   */
  static String russian(EdtNode node, String name) {
    for (EdtNode entry : node.list(name)) {
      if (entry.property("key").equals("ru")) {
        return entry.property("value");
      }
    }
    return "";
  }

  /**
   * Значение логического свойства.
   *
   * @param node узел объекта
   * @param eClass класс объекта в метамодели
   * @param name имя свойства
   * @return значение из файла, иначе умолчание схемы
   */
  static boolean flag(EdtNode node, EClass eClass, String name) {
    List<EdtNode> written = node.list(name);
    if (!written.isEmpty()) {
      return Boolean.parseBoolean(written.get(0).value());
    }
    return Boolean.TRUE.equals(defaultValue(eClass, name));
  }

  /**
   * Значения свойства-списка.
   *
   * @param node узел объекта
   * @param name имя свойства
   * @return значения в порядке файла
   */
  static List<String> list(EdtNode node, String name) {
    return node.list(name).stream().map(EdtNode::value).filter(value -> !value.isEmpty()).toList();
  }

  /** Свойство класса или {@code null}, если такого в схеме нет. */
  private static EStructuralFeature feature(EClass eClass, String name) {
    return eClass == null ? null : eClass.getEStructuralFeature(name);
  }

  /** Значение по умолчанию из схемы. */
  private static Object defaultValue(EClass eClass, String name) {
    EStructuralFeature feature = feature(eClass, name);
    return feature instanceof EAttribute attribute ? attribute.getDefaultValue() : null;
  }

  /**
   * Имя константы перечислимого значения.
   *
   * @param feature свойство схемы
   * @param value значение из файла
   * @return {@code DONT_USE} для {@code DontUse}; прочие значения не меняются
   */
  private static String enumConstant(EStructuralFeature feature, String value) {
    if (feature == null || !(feature.getEType() instanceof EEnum) || value.isEmpty()) {
      return value;
    }
    StringBuilder constant = new StringBuilder();
    for (int index = 0; index < value.length(); index++) {
      char symbol = value.charAt(index);
      if (index > 0 && Character.isUpperCase(symbol) && !Character.isUpperCase(value.charAt(index - 1))) {
        constant.append('_');
      }
      constant.append(Character.toUpperCase(symbol));
    }
    return constant.toString().toUpperCase(Locale.ROOT);
  }
}
