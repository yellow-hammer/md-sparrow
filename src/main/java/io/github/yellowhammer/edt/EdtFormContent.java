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

import io.github.yellowhammer.designerxml.cf.FormAttributeDto;
import io.github.yellowhammer.designerxml.cf.FormCommandDto;
import io.github.yellowhammer.designerxml.cf.FormContentDto;
import io.github.yellowhammer.designerxml.cf.FormEventDto;
import io.github.yellowhammer.designerxml.cf.FormItemDto;
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
  private static final String TYPE_PREFIX = "form:Form";

  private EdtFormContent() {
  }

  /**
   * Читает содержимое формы.
   *
   * @param formFile файл {@code Form.form}
   * @return элементы, реквизиты, команды, параметры и события формы
   * @throws IOException если файл не читается
   */
  public static FormContentDto read(Path formFile) throws IOException {
    EdtNode form = EdtObjectReader.read(formFile);

    FormContentDto dto = new FormContentDto();
    dto.title = EdtPropertyValues.russian(form, "title");
    dto.properties = scalars(form);
    dto.items = items(form);
    dto.attributes = attributes(form);
    dto.commands = commands(form);
    dto.parameters = parameters(form);
    dto.events = events(form);
    return dto;
  }

  /** Свойства формы: всё, что записано простым значением. */
  private static java.util.Map<String, String> scalars(EdtNode form) {
    java.util.Map<String, String> properties = new java.util.LinkedHashMap<>();
    for (EdtNode child : form.children()) {
      if (child.children().isEmpty() && !child.value().isEmpty()) {
        properties.putIfAbsent(child.kind(), child.value());
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
  private static List<FormItemDto> items(EdtNode owner) {
    List<FormItemDto> items = new ArrayList<>();
    for (String node : ITEM_NODES) {
      for (EdtNode child : owner.list(node)) {
        items.add(item(child));
      }
    }
    return items;
  }

  /** Один элемент формы. */
  private static FormItemDto item(EdtNode node) {
    FormItemDto item = new FormItemDto();
    item.type = typeOf(node);
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
    item.properties = scalars(node);
    item.events = events(node);
    item.items = items(node);
    return item;
  }

  /**
   * Вид элемента формы.
   *
   * У поля он записан вместе с видом поля: {@code form:FormField} и
   * {@code InputField} рядом. Панель показывает вид поля, если он есть.
   */
  private static String typeOf(EdtNode node) {
    String kind = node.attributes().getOrDefault("xsi:type", "");
    String type = node.property("type");
    if (!type.isEmpty()) {
      return type;
    }
    return kind.startsWith(TYPE_PREFIX) ? kind.substring(TYPE_PREFIX.length()) : kind;
  }

  /** Логическое свойство: у формы они записаны словами. */
  private static Boolean flag(EdtNode node, String name) {
    String value = node.property(name);
    return value.isEmpty() ? null : Boolean.valueOf(value);
  }

  /** Реквизиты формы. */
  private static List<FormAttributeDto> attributes(EdtNode form) {
    List<FormAttributeDto> attributes = new ArrayList<>();
    for (EdtNode node : form.list("attributes")) {
      FormAttributeDto attribute = new FormAttributeDto();
      attribute.name = node.name();
      attribute.type = type(node);
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
  private static List<FormParameterDto> parameters(EdtNode form) {
    List<FormParameterDto> parameters = new ArrayList<>();
    for (EdtNode node : form.list("parameters")) {
      FormParameterDto parameter = new FormParameterDto();
      parameter.name = node.name();
      parameter.type = type(node);
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
   * Тип значения: EDT записывает его именами платформы.
   *
   * У реквизита формы тип назван {@code valueType}, у остальных узлов -
   * {@code type}.
   */
  private static MdTypeDescriptionDto type(EdtNode node) {
    List<EdtNode> types = node.list("valueType").isEmpty() ? node.list("type") : node.list("valueType");
    if (types.isEmpty()) {
      return null;
    }
    MdTypeDescriptionDto dto = new MdTypeDescriptionDto();
    dto.types = EdtPropertyValues.list(types.get(0), "types");
    dto.typeSets = EdtPropertyValues.list(types.get(0), "typeSet");
    return dto;
  }
}
