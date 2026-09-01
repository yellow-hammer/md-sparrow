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
import java.util.TreeMap;

import org.eclipse.emf.ecore.EAttribute;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EClassifier;
import org.eclipse.emf.ecore.EEnum;
import org.eclipse.emf.ecore.EEnumLiteral;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.emf.ecore.EStructuralFeature;

import io.github.yellowhammer.designerxml.cf.FormItemPropertyDictionary.FormItemPropertyDto;

/**
 * Свойства элементов управляемой формы 1С:EDT.
 *
 * Панель рисует палитру свойств по этому словарю: у каждого вида элемента свои
 * свойства, у перечислимых - свои значения. Всё берётся из схемы формы, поэтому
 * новая версия EDT приносит новые свойства сама.
 */
public final class EdtFormItemProperties {

  /** Пространство имён управляемой формы. */
  private static final String FORM = "http://g5.1c.ru/v8/dt/form";

  /**
   * Перечисление видов - класс элемента, который эти виды принимает.
   *
   * У поля, группы, украшения и дополнения формы свои наборы видов, и схема
   * держит их перечислениями рядом с классом элемента.
   */
  private static final Map<String, String> ITEM_KINDS = Map.of(
      "ManagedFormFieldType", "FormField",
      "ManagedFormGroupType", "FormGroup",
      "ManagedFormDecorationType", "Decoration",
      "ManagedFormAdditionType", "Addition",
      "ManagedFormButtonType", "Button");

  /**
   * Элементы без своего перечисления видов.
   *
   * Таблица, подсказка и кнопка описаны одним классом: вид у них один, и он
   * совпадает с именем класса.
   */
  private static final List<String> SINGLE_KINDS = List.of("Table", "ExtendedTooltip", "Button");

  /** Многоязычная строка: заголовок, подсказка, пояснение. */
  private static final String LOCAL_STRING = "LocalStringMapEntry";

  /** Написание вида у панели, если оно расходится со схемой. */
  private static final Map<String, String> ALIASES = Map.of("SpreadsheetDocumentField", "SpreadSheetDocumentField");

  private EdtFormItemProperties() {
  }

  /**
   * Собирает словарь свойств.
   *
   * У каждого вида элемента свои свойства: общие берутся у класса элемента, а
   * особенные - у описания вида, которое схема зовёт {@code ExtInfo}.
   *
   * @param model метамодель EDT
   * @return вид элемента - его свойства
   */
  public static Map<String, List<FormItemPropertyDto>> all(EdtModel model) {
    Map<String, List<FormItemPropertyDto>> dictionary = new TreeMap<>();
    EPackage form = model.packageOf(FORM);
    if (form == null) {
      return dictionary;
    }

    for (Map.Entry<String, String> kinds : ITEM_KINDS.entrySet()) {
      if (!(form.getEClassifier(kinds.getKey()) instanceof EEnum types)) {
        continue;
      }
      EClass item = form.getEClassifier(kinds.getValue()) instanceof EClass found ? found : null;
      if (item == null) {
        continue;
      }
      for (EEnumLiteral literal : types.getELiterals()) {
        String kind = literal.getName();
        if (kind.equals("None")) {
          continue;
        }
        // Украшение схема зовёт по существу: Label и Picture, а панель -
        // LabelDecoration и PictureDecoration
        String name = kinds.getValue().equals("Decoration") ? kind + "Decoration" : kind;
        dictionary.put(name, properties(item, extInfo(form, kind)));
      }
    }
    for (String kind : SINGLE_KINDS) {
      if (form.getEClassifier(kind) instanceof EClass single) {
        dictionary.put(kind, properties(single, extInfo(form, kind)));
      }
    }
    // Свойства самой формы панель показывает так же, как свойства элемента
    if (form.getEClassifier("Form") instanceof EClass formClass) {
      dictionary.put("Form", properties(formClass, null));
    }
    ALIASES.forEach((kind, alias) -> {
      List<FormItemPropertyDto> properties = dictionary.get(kind);
      if (properties != null) {
        dictionary.putIfAbsent(alias, properties);
      }
    });
    return dictionary;
  }

  /**
   * Описание вида элемента.
   *
   * Имена расходятся с видами: у радиокнопок вид {@code RadioButtonField}, а
   * описание - {@code RadioButtonsFieldExtInfo}, поэтому подбирается ближайшее.
   */
  private static EClass extInfo(EPackage form, String kind) {
    String exact = kind + "ExtInfo";
    if (form.getEClassifier(exact) instanceof EClass found) {
      return found;
    }
    for (EClassifier classifier : form.getEClassifiers()) {
      String name = classifier.getName();
      if (classifier instanceof EClass candidate && name.endsWith("ExtInfo")
          && sameKind(name.substring(0, name.length() - "ExtInfo".length()), kind)) {
        return candidate;
      }
    }
    return null;
  }

  /** Одно ли это, если убрать разницу в написании вида. */
  private static boolean sameKind(String left, String right) {
    return normalize(left).equals(normalize(right));
  }

  private static String normalize(String name) {
    return name.toLowerCase(java.util.Locale.ROOT)
        .replace("document", "doc")
        .replace("picture", "image")
        .replace("buttons", "button")
        .replace("s ", " ");
  }

  /** Свойства элемента: общие у класса, особенные у описания вида. */
  private static List<FormItemPropertyDto> properties(EClass item, EClass extInfo) {
    List<FormItemPropertyDto> properties = new ArrayList<>();
    java.util.Set<String> seen = new java.util.LinkedHashSet<>();
    for (EClass source : extInfo == null ? List.of(item) : List.of(item, extInfo)) {
      for (EStructuralFeature feature : source.getEAllStructuralFeatures()) {
        // Заголовок и подсказка записаны парами язык-значение, а панель правит
        // их одной строкой
        boolean localized = feature.getEType().getName().equals(LOCAL_STRING);
        if ((feature.isMany() && !localized) || isStructure(feature) || !seen.add(feature.getName())) {
          continue;
        }
        properties.add(property(feature));
      }
    }
    return properties;
  }

  /** Вложенные элементы - это состав формы, он виден в дереве, а не в палитре. */
  private static boolean isStructure(EStructuralFeature feature) {
    return feature instanceof EReference reference
        && reference.getEReferenceType().getName().startsWith("Form")
        && reference.isContainment();
  }

  /** Описание одного свойства: вид значения и допустимые варианты. */
  private static FormItemPropertyDto property(EStructuralFeature feature) {
    FormItemPropertyDto dto = new FormItemPropertyDto();
    dto.name = feature.getName();
    dto.kind = kindOfValue(feature);
    if (feature.getEType() instanceof EEnum type) {
      List<String> values = new ArrayList<>();
      for (EEnumLiteral literal : type.getELiterals()) {
        values.add(literal.getLiteral());
      }
      dto.values = values;
    }
    Object fallback = feature instanceof EAttribute attribute ? attribute.getDefaultValue() : null;
    if (fallback != null) {
      dto.defaultValue = String.valueOf(fallback);
    }
    return dto;
  }

  /** Вид значения свойства в терминах панели. */
  private static String kindOfValue(EStructuralFeature feature) {
    if (feature.getEType() instanceof EEnum) {
      return "enum";
    }
    if (feature.getEType().getName().equals(LOCAL_STRING)) {
      return "string";
    }
    String type = feature.getEType().getInstanceClassName();
    if ("boolean".equals(type) || "java.lang.Boolean".equals(type)) {
      return "boolean";
    }
    if ("int".equals(type) || "java.lang.Integer".equals(type)
        || "long".equals(type) || "java.lang.Long".equals(type)) {
      return "number";
    }
    return "string";
  }
}
