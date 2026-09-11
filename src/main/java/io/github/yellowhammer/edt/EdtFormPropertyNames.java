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
import java.util.Map;

import io.github.yellowhammer.designerxml.SchemaVersion;
import io.github.yellowhammer.designerxml.cf.FormItemPropertyDictionary;
import io.github.yellowhammer.designerxml.cf.FormItemPropertyDictionary.FormItemPropertyDto;

/**
 * Свойства элементов формы в записи конфигуратора.
 *
 * Метамодель EDT зовёт свойства со строчной буквы ({@code visible}), а выгрузка
 * конфигуратора - как элементы её XML ({@code Visible}, но атрибуты {@code name}
 * и {@code id}). Контракт панели один на оба формата, и это запись
 * конфигуратора: на ней держатся подписи и группы палитры. Написание берётся из
 * словаря свойств самой выгрузки, а не переделкой регистра: у атрибутов оно
 * своё.
 *
 * Свойства, которых у конфигуратора нет, панели не показываются: это служебные
 * признаки модели EDT, подписей и значений у панели для них нет.
 */
final class EdtFormPropertyNames {

  /** Словарь выгрузки последней версии: свойства элементов только прибавляются. */
  private static volatile Map<String, List<FormItemPropertyDto>> designer;

  private EdtFormPropertyNames() {
  }

  /**
   * Свойство вида элемента в записи конфигуратора.
   *
   * @param kind вид элемента в записи конфигуратора: {@code InputField}, {@code Form}
   * @param name имя свойства в метамодели EDT: {@code visible}
   * @return описание свойства из словаря выгрузки либо {@code null}, если такого у конфигуратора нет
   */
  static FormItemPropertyDto property(String kind, String name) {
    for (FormItemPropertyDto property : designer().getOrDefault(kind, List.of())) {
      if (property.name.equalsIgnoreCase(name)) {
        return property;
      }
    }
    return null;
  }

  /**
   * Значение перечислимого свойства в записи конфигуратора.
   *
   * Литералы схем в основном совпадают, но не всегда одним регистром:
   * прокрутку EDT пишет {@code auto}, конфигуратор - {@code Auto}. Незнакомый
   * литерал остаётся как записан.
   *
   * @param property свойство из словаря выгрузки
   * @param literal литерал метамодели EDT
   * @return написание конфигуратора либо сам литерал
   */
  static String value(FormItemPropertyDto property, String literal) {
    if (property == null || property.values == null) {
      return literal;
    }
    for (String value : property.values) {
      if (value.equalsIgnoreCase(literal)) {
        return value;
      }
    }
    return literal;
  }

  /** Вид элемента известен конфигуратору. */
  static boolean knownKind(String kind) {
    return designer().containsKey(kind);
  }

  private static Map<String, List<FormItemPropertyDto>> designer() {
    Map<String, List<FormItemPropertyDto>> loaded = designer;
    if (loaded == null) {
      SchemaVersion[] versions = SchemaVersion.values();
      loaded = FormItemPropertyDictionary.forVersion(versions[versions.length - 1]);
      designer = loaded;
    }
    return loaded;
  }

  /** Имя узла с заглавной буквы: так конфигуратор зовёт прикреплённые элементы. */
  static String capitalize(String name) {
    return name.isEmpty() ? name : name.substring(0, 1).toUpperCase(Locale.ROOT) + name.substring(1);
  }
}
