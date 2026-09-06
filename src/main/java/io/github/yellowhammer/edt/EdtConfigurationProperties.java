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

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EEnum;
import org.eclipse.emf.ecore.EEnumLiteral;

import io.github.yellowhammer.designerxml.cf.ConfigurationPropertiesDto;

/**
 * Свойства конфигурации 1С:EDT.
 *
 * Отдаются тем же контрактом, что у выгрузки конфигуратора: поля названы так же,
 * как свойства схемы, а варианты назначений берутся из метамодели.
 */
public final class EdtConfigurationProperties {

  private EdtConfigurationProperties() {
  }

  /**
   * Читает свойства конфигурации.
   *
   * @param configurationMdo файл {@code src/Configuration/Configuration.mdo}
   * @param model метамодель EDT
   * @return свойства в общем контракте
   * @throws IOException если файл не читается
   */
  public static ConfigurationPropertiesDto read(Path configurationMdo, EdtModel model) throws IOException {
    EdtObjectReader.EdtNode node = EdtObjectReader.read(configurationMdo);
    EClass eClass = model.classOf(node.kind());

    ConfigurationPropertiesDto dto = new ConfigurationPropertiesDto();
    for (Field field : ConfigurationPropertiesDto.class.getFields()) {
      if (Modifier.isStatic(field.getModifiers())) {
        continue;
      }
      Object value = value(field, node, eClass);
      if (value != null) {
        try {
          field.set(dto, value);
        } catch (IllegalAccessException error) {
          throw new IllegalStateException("Не удалось заполнить свойство " + field.getName(), error);
        }
      }
    }
    dto.usePurposeOptions = options(eClass, "usePurposes");
    return dto;
  }

  /**
   * Записывает изменённые свойства конфигурации.
   *
   * Правка идёт точечно, как и у объекта: меняются только тронутые участки.
   *
   * @param configurationMdo файл конфигурации
   * @param dto свойства целиком: изменения считаются сравнением с файлом
   * @param model метамодель EDT
   * @return число изменённых свойств
   * @throws IOException если файл не читается или не пишется
   */
  public static int write(Path configurationMdo, ConfigurationPropertiesDto dto, EdtModel model)
      throws IOException {
    return EdtObjectWriter.writeFields(configurationMdo, dto, read(configurationMdo, model), model);
  }

  /** Значение поля контракта по свойству схемы. */
  private static Object value(Field field, EdtObjectReader.EdtNode node, EClass eClass) {
    String name = field.getName();
    if (name.equals("usePurposeOptions")) {
      return null;
    }
    if (field.getType() == List.class) {
      return EdtPropertyValues.list(node, name);
    }
    if (field.getType() != String.class) {
      return null;
    }
    // Краткая информация, авторские права и адреса записаны парами язык-значение
    return name.endsWith("Ru")
        ? EdtPropertyValues.russian(node, name.substring(0, name.length() - 2))
        : EdtPropertyValues.text(node, eClass, name);
  }

  /** Допустимые значения перечислимого свойства: контракт несёт имена констант. */
  private static List<String> options(EClass eClass, String name) {
    if (eClass == null || !(eClass.getEStructuralFeature(name) != null
        && eClass.getEStructuralFeature(name).getEType() instanceof EEnum type)) {
      return List.of();
    }
    List<String> options = new ArrayList<>();
    for (EEnumLiteral literal : type.getELiterals()) {
      options.add(EdtPropertyValues.constantName(literal.getName()));
    }
    return options;
  }
}
