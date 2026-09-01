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
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.emf.ecore.EAttribute;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EEnumLiteral;
import org.eclipse.emf.ecore.EStructuralFeature;

import io.github.yellowhammer.designerxml.cf.FormAttributeDto;
import io.github.yellowhammer.designerxml.cf.FormCommandDto;
import io.github.yellowhammer.designerxml.cf.FormContentDto;
import io.github.yellowhammer.designerxml.cf.FormEventDto;
import io.github.yellowhammer.designerxml.cf.FormItemDto;
import io.github.yellowhammer.designerxml.cf.FormItemPropertyDictionary;
import io.github.yellowhammer.designerxml.cf.FormItemPropertyDictionary.FormItemPropertyDto;
import io.github.yellowhammer.designerxml.cf.FormParameterDto;
import io.github.yellowhammer.designerxml.cf.MdTypeDescriptionDto;
import io.github.yellowhammer.edt.EdtObjectReader.EdtNode;

/**
 * Содержимое управляемой формы 1С:EDT.
 *
 * Форма лежит своим файлом {@code Form.form}, и разметка у неё другая: вид
 * элемента записан атрибутом {@code xsi:type}, а вложенные элементы, реквизиты и
 * команды - узлами того же файла. Контракт для панели общий с выгрузкой
 * конфигуратора.
 */
public final class EdtFormContent {

  /** Префикс вида элемента: {@code form:FormField} - это поле. */
  private static final String TYPE_PREFIX = "form:";

  /** Вид элемента, у которого свой вид записан отдельным свойством: поле, группа, дополнение. */
  private static final String KIND_PROPERTY = "type";

  /** Украшение схема зовёт по существу, конфигуратор - с видом: Label и LabelDecoration. */
  private static final String DECORATION = "Decoration";

  private static final String FORM_NAMESPACE = "http://g5.1c.ru/v8/dt/form";

  private EdtFormContent() {
  }

  /**
   * Читает содержимое формы.
   *
   * @param formFile файл {@code Form.form}
   * @param model метамодель EDT
   * @return элементы, реквизиты, команды, параметры и события формы
   * @throws IOException если файл не читается
   */
  public static FormContentDto read(Path formFile, EdtModel model) throws IOException {
    EdtNode form = EdtObjectReader.read(formFile);

    FormContentDto dto = new FormContentDto();
    dto.title = EdtPropertyValues.russian(form, "title");
    dto.properties = scalars(form, FormItemPropertyDictionary.FORM_KIND);
    dto.items = items(form, model);
    dto.attributes = attributes(form, model);
    dto.commands = commands(form);
    dto.parameters = parameters(form, model);
    dto.events = events(form);
    return dto;
  }

  /**
   * Свойства, записанные простым значением, под именами конфигуратора.
   *
   * Свойств, которых у конфигуратора нет, панель не показывает, поэтому и
   * читать их незачем.
   */
  private static java.util.Map<String, String> scalars(EdtNode node, String kind) {
    java.util.Map<String, String> properties = new java.util.LinkedHashMap<>();
    for (EdtNode child : node.children()) {
      if (!child.children().isEmpty() || child.value().isEmpty()) {
        continue;
      }
      FormItemPropertyDto designer = EdtFormPropertyNames.property(kind, child.kind());
      if (designer != null) {
        properties.putIfAbsent(designer.name, EdtFormPropertyNames.value(designer, child.value()));
      }
    }
    return properties;
  }

  /**
   * Служебные элементы, которые лежат своими узлами.
   *
   * Подсказка, контекстное меню и командная панель - такие же элементы формы,
   * как поля и группы: конфигуратор перечисляет их вместе с остальными.
   */
  private static final List<String> ITEM_NODES = List.of(
      "items",
      "autoCommandBar",
      "commandBar",
      "extendedTooltip",
      "contextMenu",
      "searchStringAddition",
      "viewStatusAddition",
      "searchControlAddition");

  /** Элементы формы вместе с вложенными. */
  private static List<FormItemDto> items(EdtNode owner, EdtModel model) {
    List<FormItemDto> items = new ArrayList<>();
    for (String node : ITEM_NODES) {
      for (EdtNode child : owner.list(node)) {
        items.add(item(child, node, model));
      }
    }
    return items;
  }

  /** Один элемент формы. */
  private static FormItemDto item(EdtNode node, String container, EdtModel model) {
    FormItemDto item = new FormItemDto();
    item.type = kindOf(node, container, model);
    item.name = node.name();
    item.id = node.property("id");
    item.title = EdtPropertyValues.russian(node, "title");
    item.dataPath = node.property("dataPath");
    item.group = node.property("group");
    item.showTitle = node.property("showTitle");
    item.titleLocation = node.property("titleLocation");
    item.representation = node.property("representation");
    item.visible = flag(node, "visible");
    item.enabled = flag(node, "enabled");
    item.readOnly = flag(node, "readOnly");
    item.width = node.property("width");
    item.properties = scalars(node, item.type);
    item.events = events(node);
    item.items = items(node, model);
    return item;
  }

  /**
   * Вид элемента формы.
   *
   * У поля он записан вместе с видом поля: {@code form:FormField} и
   * {@code InputField} рядом. Панель показывает вид поля, если он есть.
   */
  private static String kindOf(EdtNode node, String container, EdtModel model) {
    // Прикреплённые элементы конфигуратор зовёт по узлу: контекстное меню, подсказка
    if (!container.equals("items")) {
      return EdtFormPropertyNames.capitalize(container);
    }
    String xsi = node.attributes().getOrDefault("xsi:type", "");
    String kind = xsi.startsWith(TYPE_PREFIX) ? xsi.substring(TYPE_PREFIX.length()) : xsi;
    if (EdtFormPropertyNames.knownKind(kind)) {
      return kind;
    }
    // У поля, группы, украшения и дополнения свой вид записан свойством, а
    // незаписанный вид схема подразумевает первым
    String type = node.property(KIND_PROPERTY);
    if (type.isEmpty()) {
      type = defaultKind(kind, model);
    }
    return kind.equals(DECORATION) ? type + DECORATION : type;
  }

  /** Вид элемента, который схема подразумевает без записи. */
  private static String defaultKind(String kind, EdtModel model) {
    EClass eClass = model.packageOf(FORM_NAMESPACE) == null
        ? null
        : model.packageOf(FORM_NAMESPACE).getEClassifier(kind) instanceof EClass found ? found : null;
    EStructuralFeature feature = eClass == null ? null : eClass.getEStructuralFeature(KIND_PROPERTY);
    Object fallback = feature instanceof EAttribute attribute ? attribute.getDefaultValue() : null;
    return fallback instanceof EEnumLiteral literal ? literal.getLiteral() : kind;
  }

  /** Логическое свойство: у формы они записаны словами. */
  private static Boolean flag(EdtNode node, String name) {
    String value = node.property(name);
    return value.isEmpty() ? null : Boolean.valueOf(value);
  }

  /** Реквизиты формы. */
  private static List<FormAttributeDto> attributes(EdtNode form, EdtModel model) {
    List<FormAttributeDto> attributes = new ArrayList<>();
    for (EdtNode node : form.list("attributes")) {
      FormAttributeDto attribute = new FormAttributeDto();
      attribute.name = node.name();
      attribute.type = type(node, model);
      attribute.main = Boolean.parseBoolean(node.property("main"));
      // У динамического списка основная таблица лежит в описании его вида
      attribute.mainTable = node.list("extInfo").stream()
          .map(info -> info.property("mainTable"))
          .filter(table -> !table.isEmpty())
          .findFirst()
          .orElse("");
      attributes.add(attribute);
    }
    return attributes;
  }

  /** Команды формы. */
  private static List<FormCommandDto> commands(EdtNode form) {
    List<FormCommandDto> commands = new ArrayList<>();
    for (EdtNode node : form.list("formCommands")) {
      FormCommandDto command = new FormCommandDto();
      command.name = node.name();
      command.title = EdtPropertyValues.russian(node, "title");
      command.action = node.property("action");
      commands.add(command);
    }
    return commands;
  }

  /** Параметры формы. */
  private static List<FormParameterDto> parameters(EdtNode form, EdtModel model) {
    List<FormParameterDto> parameters = new ArrayList<>();
    for (EdtNode node : form.list("parameters")) {
      FormParameterDto parameter = new FormParameterDto();
      parameter.name = node.name();
      parameter.type = type(node, model);
      parameters.add(parameter);
    }
    return parameters;
  }

  /**
   * Обработчики событий.
   *
   * У формы и у её элементов они записаны одинаково: имя события и имя
   * процедуры модуля.
   */
  private static List<FormEventDto> events(EdtNode owner) {
    List<FormEventDto> events = new ArrayList<>();
    for (EdtNode node : owner.list("handlers")) {
      FormEventDto event = new FormEventDto();
      event.name = node.property("event");
      event.handler = node.property("name");
      events.add(event);
    }
    return events;
  }

  /**
   * Тип значения в записи контракта.
   *
   * У реквизита формы тип назван {@code valueType}, у остальных узлов -
   * {@code type}.
   */
  private static MdTypeDescriptionDto type(EdtNode node, EdtModel model) {
    String feature = node.list("valueType").isEmpty() ? "type" : "valueType";
    return EdtTypeDescription.read(node, feature, model);
  }
}
