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

import io.github.yellowhammer.designerxml.SchemaVersion;

import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlType;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Какие подчинённые узлы бывают у вида по схеме формата.
 *
 * <p>Состав берётся из модели версии ({@code <Вид>ChildObjects}), а не из списка в коде. Узел,
 * которого у вида нет, JAXB при чтении молча пропускает, а платформа при загрузке выбрасывает
 * с предупреждением, поэтому запись такого узла отвергается заранее.
 */
final class ChildObjectKinds {

  private static final String CHILD_OBJECTS = "ChildObjects";

  private static final Map<String, List<String>> CACHE = new ConcurrentHashMap<>();

  private ChildObjectKinds() {
  }

  /**
   * Подчинённые узлы вида в порядке схемы.
   *
   * @param version версия формата
   * @param ownerLocal элемент владельца: {@code Catalog}, {@code TabularSection}
   * @return имена элементов {@code ChildObjects}; пусто, если подчинённых у вида нет
   */
  static List<String> of(SchemaVersion version, String ownerLocal) {
    return CACHE.computeIfAbsent(version.name() + "." + ownerLocal, key -> read(version, ownerLocal));
  }

  /**
   * Отказ, если у вида нет подчинённых такого вида.
   *
   * @param version версия формата
   * @param ownerLocal элемент владельца: {@code Catalog}, {@code TabularSection}
   * @param childLocal элемент подчинённого: {@code Attribute}, {@code Dimension}
   * @throws IllegalArgumentException если схема формата не допускает такой узел у вида
   */
  static void ensureAllowed(SchemaVersion version, String ownerLocal, String childLocal) {
    if (!of(version, ownerLocal).contains(childLocal)) {
      throw new IllegalArgumentException("У вида " + ownerLocal + " нет подчинённых " + childLocal);
    }
  }

  private static List<String> read(SchemaVersion version, String ownerLocal) {
    Class<?> type = childObjectsClass(version, ownerLocal);
    if (type == null) {
      return List.of();
    }
    XmlType xmlType = type.getAnnotation(XmlType.class);
    if (xmlType == null) {
      return List.of();
    }
    List<String> names = new ArrayList<>();
    for (String property : xmlType.propOrder()) {
      if (!property.isEmpty()) {
        names.add(elementName(type, property));
      }
    }
    return List.copyOf(names);
  }

  private static Class<?> childObjectsClass(SchemaVersion version, String ownerLocal) {
    for (String pkg : version.jaxbContextPath().split(":")) {
      if (!pkg.endsWith(".mdclasses")) {
        continue;
      }
      try {
        return Class.forName(pkg + "." + ownerLocal + CHILD_OBJECTS);
      } catch (ClassNotFoundException absent) {
        return null;
      }
    }
    return null;
  }

  /** Имя элемента выгрузки: из аннотации поля, у XJC оно всегда есть. */
  private static String elementName(Class<?> type, String property) {
    try {
      Field field = type.getDeclaredField(property);
      XmlElement element = field.getAnnotation(XmlElement.class);
      if (element != null && !"##default".equals(element.name())) {
        return element.name();
      }
    } catch (NoSuchFieldException absent) {
      // Поле названо иначе, чем свойство: имя восстанавливается из свойства
    }
    String bare = property.startsWith("_") ? property.substring(1) : property;
    return bare.substring(0, 1).toUpperCase(Locale.ROOT) + bare.substring(1);
  }
}
