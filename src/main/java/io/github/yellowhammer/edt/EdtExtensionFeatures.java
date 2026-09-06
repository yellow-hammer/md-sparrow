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
import java.util.List;
import java.util.Map;

import org.eclipse.emf.ecore.EAttribute;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EStructuralFeature;

import io.github.yellowhammer.designerxml.cf.AdoptedStates;
import io.github.yellowhammer.designerxml.cf.MdNamedPropertyDto;
import io.github.yellowhammer.designerxml.cf.MdObjectPropertiesDto;

/**
 * Свойства, которые расширение контролирует или меняет у заимствованного узла.
 *
 * Их перечисляет класс расширения метамодели EDT: у справочника
 * {@code CatalogExtension}, у реквизита {@code BasicFeatureExtension}. Свойство,
 * которого в классе нет, у заимствованного узла принадлежит расширяемой
 * конфигурации и в расширении не правится. Список один на оба формата: правило
 * платформы, а не формата файла.
 */
public final class EdtExtensionFeatures {

  /** Состояние свойства в метамодели: только такие свойства расширение и держит. */
  private static final String PROPERTY_STATE = "MdPropertyState";

  /** Класс расширения подчинённого узла по его списку в описании объекта. */
  private static final Map<String, String> NODE_CLASSES = Map.of(
      "attributes", "BasicFeatureExtension",
      "dimensions", "BasicFeatureExtension",
      "resources", "BasicFeatureExtension",
      "columns", "BasicFeatureExtension",
      "addressingAttributes", "BasicFeatureExtension",
      "accountingFlags", "BasicFeatureExtension",
      "extDimensionAccountingFlags", "BasicFeatureExtension",
      "commands", "BasicCommandExtension",
      "enumValues", "MdObjectExtension",
      "tabularSections", "MdObjectExtension");

  /** Список узла в описании объекта по его элементу в выгрузке конфигуратора. */
  private static final Map<String, String> DESIGNER_CONTAINERS = Map.of(
      "Attribute", "attributes",
      "Dimension", "dimensions",
      "Resource", "resources",
      "Column", "columns",
      "AddressingAttribute", "addressingAttributes",
      "AccountingFlag", "accountingFlags",
      "ExtDimensionAccountingFlag", "extDimensionAccountingFlags",
      "Command", "commands",
      "EnumValue", "enumValues",
      "TabularSection", "tabularSections");

  private EdtExtensionFeatures() {
  }

  /**
   * Правимые свойства по элементам выгрузки конфигуратора: объект под пустым ключом,
   * подчинённые узлы под своим элементом ({@code Attribute}).
   */
  public static Map<String, List<String>> byDesignerContainer(EdtModel model, String kind) {
    Map<String, List<String>> byContainer = new java.util.LinkedHashMap<>();
    byContainer.put("", ofObject(model, kind));
    for (Map.Entry<String, String> entry : DESIGNER_CONTAINERS.entrySet()) {
      byContainer.put(entry.getKey(), ofNode(model, entry.getValue()));
    }
    return byContainer;
  }

  /**
   * Свойства класса расширения объекта.
   *
   * @param kind вид объекта в написании контракта: {@code catalog}
   * @return имена свойств в порядке класса; пусто, если класса в метамодели нет
   */
  public static List<String> ofObject(EdtModel model, String kind) {
    if (kind == null || kind.isEmpty()) {
      return List.of();
    }
    return features(model, Character.toUpperCase(kind.charAt(0)) + kind.substring(1) + "Extension");
  }

  /**
   * Свойства класса расширения подчинённого узла.
   *
   * @param list список узла в описании объекта: {@code attributes}
   */
  public static List<String> ofNode(EdtModel model, String list) {
    String extensionClass = NODE_CLASSES.get(list);
    return extensionClass == null ? List.of() : features(model, extensionClass);
  }

  /**
   * Свойства класса расширения по его имени.
   *
   * @param extensionClass имя класса: {@code CatalogExtension}
   */
  public static List<String> features(EdtModel model, String extensionClass) {
    EClass eClass = model.classOf(extensionClass);
    List<String> names = new ArrayList<>();
    if (eClass == null) {
      return names;
    }
    for (EStructuralFeature feature : eClass.getEAllStructuralFeatures()) {
      if (feature instanceof EAttribute attribute && PROPERTY_STATE.equals(attribute.getEType().getName())) {
        names.add(feature.getName());
      }
    }
    return names;
  }

  /**
   * Заполняет списки правимых свойств у заимствованного объекта и его заимствованных узлов.
   *
   * @param dto описание объекта, прочитанное из любого формата
   */
  public static void apply(MdObjectPropertiesDto dto, EdtModel model) {
    if (AdoptedStates.ADOPTED.equals(dto.objectBelonging)) {
      dto.extendable = ofObject(model, dto.kind);
    }
    for (java.lang.reflect.Field field : MdObjectPropertiesDto.class.getFields()) {
      if (!NODE_CLASSES.containsKey(field.getName())) {
        continue;
      }
      Object nodes;
      try {
        nodes = field.get(dto);
      } catch (IllegalAccessException error) {
        throw new IllegalStateException("Не удалось прочитать узлы " + field.getName(), error);
      }
      if (nodes instanceof List<?> list) {
        applyNodes(list, model, field.getName());
      }
    }
  }

  private static void applyNodes(List<?> nodes, EdtModel model, String list) {
    for (Object item : nodes) {
      if (!(item instanceof MdNamedPropertyDto node)) {
        continue;
      }
      if (AdoptedStates.ADOPTED.equals(node.objectBelonging)) {
        node.extendable = ofNode(model, list);
      }
      if (node.attributes != null) {
        applyNodes(node.attributes, model, "attributes");
      }
    }
  }
}
