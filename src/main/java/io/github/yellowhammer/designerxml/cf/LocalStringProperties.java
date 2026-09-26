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

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Имена свойств, за которыми стоит язык.
 *
 * <p>Вызывающей стороне нужно знать, у каких свойств текст многоязычный: панель
 * подписывает их языком конфигурации и правит только его строку. Список считается
 * по пометке {@link LocalString}, поэтому новое такое свойство попадает в него
 * само, без снимка имён на стороне расширения.
 */
public final class LocalStringProperties {

  /** Классы контракта, где живут свойства объектов, конфигурации и подчинённых узлов. */
  private static final List<Class<?>> CONTRACT = List.of(
    MdObjectPropertiesDto.class,
    ConfigurationPropertiesDto.class,
    MdNamedPropertyDto.class);

  private static final List<String> NAMES = collect();

  private LocalStringProperties() {
  }

  /** Имена многоязычных свойств контракта, в порядке объявления. */
  public static List<String> names() {
    return NAMES;
  }

  private static List<String> collect() {
    Set<String> names = new LinkedHashSet<>();
    for (Class<?> type : CONTRACT) {
      collectFrom(type, names, new LinkedHashSet<>());
    }
    return List.copyOf(names);
  }

  private static void collectFrom(Class<?> type, Set<String> names, Set<Class<?>> seen) {
    if (type == null || !seen.add(type) || type.getName().startsWith("java.")) {
      return;
    }
    for (Field field : type.getFields()) {
      if (field.isAnnotationPresent(LocalString.class)) {
        names.add(field.getName());
        continue;
      }
      // Свойства вида объекта лежат вложенными записями: справочник, документ, регистр
      if (field.getType().getName().startsWith("io.github.yellowhammer")) {
        collectFrom(field.getType(), names, seen);
      }
    }
  }

  /** Список для ответа: копия, чтобы вызывающий не менял общий. */
  public static List<String> forResponse() {
    return new ArrayList<>(NAMES);
  }
}
