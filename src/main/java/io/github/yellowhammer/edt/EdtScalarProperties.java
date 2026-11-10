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
import java.util.Set;

import org.eclipse.emf.ecore.EAttribute;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EEnum;
import org.eclipse.emf.ecore.EEnumLiteral;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.emf.ecore.EStructuralFeature;

import io.github.yellowhammer.designerxml.cf.MdObjectPropertiesDto;
import io.github.yellowhammer.designerxml.cf.MdScalarPropertyMeta;
import io.github.yellowhammer.edt.EdtObjectReader.EdtNode;

/**
 * Свойства вида без своего блока в контракте: подсистемы, функциональной опции,
 * последовательности, общей формы и других.
 *
 * Контракт держит их словарём: имя свойства выгрузки и значение. Состав словаря
 * задаёт схема: одиночные свойства, кроме имени и подписей, внешних свойств в
 * своих файлах и служебных отметок - так же, как у выгрузки конфигуратора.
 */
final class EdtScalarProperties {

  /** Имя и подписи несут свои поля, остальное - внешние свойства и служебные отметки. */
  private static final Set<String> SKIPPED = Set.of(
      "name", "synonym", "comment", "uuid", "extendedConfigurationObject", "form", "help", "picture",
      "module", "managerModule", "recordSetModule", "template", "commandInterface", "additionalIndexes",
      // Владельца вложенной подсистемы задаёт каталог, в файле его нет
      "parentSubsystem");

  /** Принадлежность объекта: правится заимствованием, а не значением. */
  static final String BELONGING = "objectBelonging";

  /**
   * Свойства, которые выгрузка пишет, только когда они заданы: без записи в
   * файле они пусты, а не равны умолчанию схемы. Принадлежность есть только у
   * заимствованного объекта.
   */
  private static final Set<String> OPTIONAL = Set.of(BELONGING, "useInInterfaceCompatibilityMode");

  private EdtScalarProperties() {
  }

  /**
   * Заполняет словарь свойств и их описания.
   *
   * @param dto свойства объекта
   * @param node узел объекта
   * @param eClass класс объекта в метамодели
   */
  static void read(MdObjectPropertiesDto dto, EdtNode node, EClass eClass) {
    Map<String, Object> scalars = new LinkedHashMap<>();
    Map<String, MdScalarPropertyMeta> meta = new LinkedHashMap<>();
    for (EStructuralFeature feature : features(eClass)) {
      String name = feature.getName();
      String key = Character.toUpperCase(name.charAt(0)) + name.substring(1);
      String type = type(feature);
      if (type.equals("boolean")) {
        scalars.put(key, EdtPropertyValues.flag(node, eClass, name));
      } else if (OPTIONAL.contains(name) && node.list(name).isEmpty()) {
        scalars.put(key, "");
      } else {
        scalars.put(key, EdtPropertyValues.text(node, eClass, name));
      }
      meta.put(key, new MdScalarPropertyMeta(type, allowed(feature)));
    }
    dto.scalars = scalars;
    dto.scalarMeta = meta;
  }

  /**
   * Свойство схемы по ключу словаря.
   *
   * @param eClass класс объекта в метамодели
   * @param key имя свойства выгрузки: {@code PrivilegedGetMode}
   * @return свойство или {@code null}, если в словаре вида такого нет
   */
  static EStructuralFeature feature(EClass eClass, String key) {
    if (key == null || key.isEmpty()) {
      return null;
    }
    String name = Character.toLowerCase(key.charAt(0)) + key.substring(1);
    for (EStructuralFeature feature : features(eClass)) {
      if (feature.getName().equals(name)) {
        return feature;
      }
    }
    return null;
  }

  /**
   * Тип значения в словаре.
   *
   * @param feature свойство схемы
   * @return {@code boolean}, {@code number}, {@code enum} или {@code string}
   */
  static String type(EStructuralFeature feature) {
    if (feature instanceof EAttribute attribute) {
      if (attribute.getEAttributeType() instanceof EEnum) {
        return "enum";
      }
      Class<?> value = attribute.getEAttributeType().getInstanceClass();
      if (value == boolean.class || value == Boolean.class) {
        return "boolean";
      }
      if (value != null && (value.isPrimitive() || Number.class.isAssignableFrom(value))) {
        return "number";
      }
    }
    return "string";
  }

  private static List<EStructuralFeature> features(EClass eClass) {
    List<EStructuralFeature> features = new ArrayList<>();
    if (eClass == null) {
      return features;
    }
    for (EStructuralFeature feature : eClass.getEAllStructuralFeatures()) {
      if (feature.isMany() || feature.isTransient() || feature.isDerived() || SKIPPED.contains(feature.getName())
          || feature instanceof EReference reference && reference.isContainment()
          || feature instanceof EAttribute attribute && !scalar(attribute)) {
        continue;
      }
      features.add(feature);
    }
    return features;
  }

  /** Значение пишется строкой: перечисление, логическое, число или текст. */
  private static boolean scalar(EAttribute attribute) {
    if (attribute.getEAttributeType() instanceof EEnum) {
      return true;
    }
    Class<?> value = attribute.getEAttributeType().getInstanceClass();
    return value == String.class || value == boolean.class || value == Boolean.class
        || value != null && (value.isPrimitive() || Number.class.isAssignableFrom(value));
  }

  private static List<String> allowed(EStructuralFeature feature) {
    List<String> allowed = new ArrayList<>();
    if (feature.getEType() instanceof EEnum type) {
      for (EEnumLiteral literal : type.getELiterals()) {
        allowed.add(EdtPropertyValues.constantName(literal.getName()));
      }
    }
    return allowed;
  }
}
