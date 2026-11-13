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

import java.util.List;
import java.util.Set;

/**
 * Значение заполнения узла (реквизит, измерение, ресурс, признак учёта) после смены его типа.
 *
 * <p>Загрузка платформы сбрасывает значение заполнения, которое не подходит к типу узла, в
 * {@code xsi:nil="true"} (например, пустую строку у реквизита ссылочного или числового типа).
 * Запись делает то же сразу, и выгрузка совпадает с записанным.
 *
 * <p>Тип значения, которого в новом типе узла нет, заменяется пустым значением: у типа «только
 * строка» это пустая строка (так платформа создаёт строковый реквизит), у остальных — nil, основная
 * форма пустого значения у каждого вида типа.
 *
 * <p>Совместимое значение и nil не трогаются: эхо не меняет файл. Значение конфигурации
 * ({@code xr:DesignTimeRef}) остаётся как есть: платформа хранит его и при чужом типе узла (значение
 * одного перечисления у реквизита типа другого перечисления). Определяемый тип и характеристика держат
 * свой состав у другого объекта: при них значение тоже не трогается.
 */
public final class FillValueCanon {

  /** Что сделать со значением заполнения. */
  public enum Action {
    /** Оставить как есть. */
    KEEP,
    /** Заменить на {@code xsi:nil="true"}. */
    NIL,
    /** Заменить на пустую строку. */
    EMPTY_STRING
  }

  /** Типы значений, совместимость которых с типом узла видна по самому типу. */
  private static final Set<String> PRIMITIVE = Set.of("xs:string", "xs:decimal", "xs:boolean", "xs:dateTime");

  private FillValueCanon() {
  }

  /**
   * Значение заполнения после смены типа узла.
   *
   * @param valueType тип значения в написании выгрузки ({@code xs:string}, {@code xs:decimal},
   *     {@code xr:DesignTimeRef}, …); {@code null}, если значения нет ({@code xsi:nil})
   * @param type новый тип узла
   * @return действие над значением
   */
  public static Action afterTypeChange(String valueType, MdTypeDescriptionDto type) {
    if (valueType == null || !PRIMITIVE.contains(valueType) || type == null) {
      return Action.KEEP;
    }
    List<String> types = type.types == null ? List.of() : type.types;
    List<String> sets = type.typeSets == null ? List.of() : type.typeSets;
    if (types.isEmpty() && sets.isEmpty()) {
      return Action.KEEP;
    }
    if (types.contains(valueType) || hidesComposition(types) || hidesComposition(sets)) {
      return Action.KEEP;
    }
    return types.equals(List.of("xs:string")) && sets.isEmpty() ? Action.EMPTY_STRING : Action.NIL;
  }

  /** Определяемый тип или характеристика: их состав лежит у другого объекта конфигурации. */
  private static boolean hidesComposition(List<String> types) {
    for (String type : types) {
      if (type != null && (type.startsWith("cfg:DefinedType.") || type.startsWith("cfg:Characteristic."))) {
        return true;
      }
    }
    return false;
  }
}
