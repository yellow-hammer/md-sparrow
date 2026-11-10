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
import java.util.Map;
import java.util.Set;

import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.emf.ecore.EStructuralFeature;

/**
 * Списки узлов и ссылок, которые контракт зовёт иначе, чем схема 1С:EDT.
 *
 * Остальные узлы названы в контракте так же, как свойства схемы, и находятся по
 * имени поля.
 */
final class EdtContractNames {

  /** Свойство схемы по полю контракта. */
  private static final Map<String, String> FEATURE_BY_FIELD = Map.of("channels", "integrationServiceChannels");

  /**
   * Списки ссылок объекта вне свойств его вида: поле контракта и свойства схемы,
   * в которых оно лежит у разных видов. Состав параметра функциональной опции
   * схема зовёт использованием.
   */
  private static final Map<String, List<String>> REFS_BY_FIELD = Map.of(
      "contentRefs", List.of("content", "use"),
      "nestedSubsystems", List.of("subsystems"),
      "documents", List.of("documents"),
      "registerRecords", List.of("registerRecords"));

  private EdtContractNames() {
  }

  /**
   * Поля контракта со списками ссылок объекта вне свойств его вида.
   *
   * @return {@code contentRefs}, {@code nestedSubsystems}, {@code documents}, {@code registerRecords}
   */
  static Set<String> refFields() {
    return REFS_BY_FIELD.keySet();
  }

  /**
   * Свойство схемы, в котором лежит список ссылок поля контракта.
   *
   * @param eClass класс объекта в метамодели
   * @param field поле контракта: {@code contentRefs}
   * @return свойство схемы: {@code content}, у параметра функциональной опции
   *     {@code use}; {@code null}, если у вида такого списка нет
   */
  static String refsFeature(EClass eClass, String field) {
    for (String name : REFS_BY_FIELD.getOrDefault(field, List.of(field))) {
      EStructuralFeature feature = eClass == null ? null : eClass.getEStructuralFeature(name);
      if (isRefList(feature)) {
        return name;
      }
    }
    return null;
  }

  /**
   * Свойство - список ссылок: в файле строка на ссылку.
   *
   * @param feature свойство схемы
   * @return {@code false} у одиночного свойства и у состава из своих узлов
   */
  static boolean isRefList(EStructuralFeature feature) {
    return feature instanceof EReference reference && reference.isMany() && !reference.isContainment();
  }

  /**
   * Вид держит поле у себя: движения документа лежат в его свойствах, а не рядом.
   *
   * @param bridge свойства вида объекта или {@code null}
   * @param field поле контракта
   * @return {@code true}, если в свойствах вида есть поле с этим именем
   */
  static boolean declares(Object bridge, String field) {
    if (bridge == null) {
      return false;
    }
    try {
      bridge.getClass().getField(field);
      return true;
    } catch (NoSuchFieldException absent) {
      return false;
    }
  }

  /**
   * Свойство схемы, в котором лежат узлы поля контракта.
   *
   * @param field поле контракта: {@code attributes}, {@code channels}
   * @return свойство схемы: {@code attributes}, {@code integrationServiceChannels}
   */
  static String feature(String field) {
    return FEATURE_BY_FIELD.getOrDefault(field, field);
  }

  /**
   * Поле контракта, в которое читаются узлы свойства схемы.
   *
   * @param feature свойство схемы: {@code attributes}, {@code integrationServiceChannels}
   * @return поле контракта: {@code attributes}, {@code channels}
   */
  static String field(String feature) {
    for (Map.Entry<String, String> entry : FEATURE_BY_FIELD.entrySet()) {
      if (entry.getValue().equals(feature)) {
        return entry.getKey();
      }
    }
    return feature;
  }
}
