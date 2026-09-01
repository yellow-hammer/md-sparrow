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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EClassifier;
import org.eclipse.emf.ecore.EEnum;
import org.eclipse.emf.ecore.EEnumLiteral;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.EStructuralFeature;

/**
 * Допустимые значения перечислимых свойств формата 1С:EDT.
 *
 * Панель свойств рисует такие свойства списком, и варианты ей нужны те же, что
 * знает формат. Своего перечня расширение не держит: он разошёлся бы с
 * метамоделью на первой же версии EDT.
 */
public final class EdtPropertyEnums {

  /** Пространство имён классов метаданных. */
  private static final String MDCLASS = "http://g5.1c.ru/v8/dt/metadata/mdclass";

  private EdtPropertyEnums() {
  }

  /**
   * Собирает словарь значений.
   *
   * @param model метамодель EDT
   * @return ключ {@code <вид>.<свойство>} - имена констант
   */
  public static Map<String, List<String>> all(EdtModel model) {
    Map<String, List<String>> values = new LinkedHashMap<>();
    EPackage mdclass = model.packageOf(MDCLASS);
    if (mdclass == null) {
      return values;
    }

    for (EClassifier classifier : mdclass.getEClassifiers()) {
      if (!(classifier instanceof EClass eClass) || eClass.isAbstract()) {
        continue;
      }
      String kind = contractKind(decapitalize(eClass.getName()));
      // Реквизит справочника метамодель зовёт CatalogAttribute, а контракт -
      // просто реквизитом: свойства у них те же
      String node = decapitalize(lastWord(eClass.getName()));
      for (EStructuralFeature feature : eClass.getEAllStructuralFeatures()) {
        if (feature.getEType() instanceof EEnum type) {
          values.put(kind + "." + feature.getName(), constants(type));
          values.putIfAbsent(node + "." + feature.getName(), constants(type));
        }
      }
    }
    return values;
  }

  /** Имена констант перечисления в написании общего контракта. */
  private static List<String> constants(EEnum type) {
    List<String> constants = new ArrayList<>();
    for (EEnumLiteral literal : type.getELiterals()) {
      constants.add(EdtPropertyValues.constantName(literal.getName()));
    }
    return constants;
  }

  /** Перечисление контракт зовёт полным словом: {@code enum} занят языком. */
  private static String contractKind(String kind) {
    return kind.equals("enum") ? "enumeration" : kind;
  }

  /** Последнее слово имени класса: {@code CatalogAttribute} - {@code Attribute}. */
  private static String lastWord(String name) {
    for (int index = name.length() - 1; index > 0; index--) {
      if (Character.isUpperCase(name.charAt(index))) {
        return name.substring(index);
      }
    }
    return name;
  }

  /** Вид объекта в терминах контракта: {@code Catalog} - {@code catalog}. */
  private static String decapitalize(String name) {
    return name.isEmpty() ? name : Character.toLowerCase(name.charAt(0)) + name.substring(1);
  }
}
