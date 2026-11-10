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

import jakarta.xml.bind.JAXBException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Списки значений параметров схемы, записанные элементом {@code v8:ValueListType}, чьи представления
 * потеряет загрузка в платформу: предупреждения {@code cf-dcs-validate} вида {@value #KIND}.
 *
 * <p>Платформа хранит список значений параметра корня повторяющимися {@code value} без представлений.
 * Список, записанный одним элементом {@code <value xsi:type="v8:ValueListType">} с представлениями
 * элементов, загрузка разворачивает в отдельные значения и молча теряет представления. Такой файл
 * читается и переносится эхом без изменений, признак {@code ok} проверки предупреждение не меняет.
 *
 * <p>Правило совпадает с тем, что видно в {@code cf-dcs-info}: предупреждение получает параметр корня с
 * {@code valueListAllowed=true}, у которого в {@code valueTyped} список ({@code type = v8:ValueListType}) и
 * хотя бы у одного элемента списка есть непустой {@code presentation}. Представление у элемента
 * {@code valueTyped} бывает только у элемента {@code v8:ValueListType}: повторяющиеся {@code value}
 * читаются без него. Список без представлений платформа разворачивает без потерь — о нём не
 * предупреждается; о параметре без признака списка значений — тоже. Списки значений в вариантах
 * настроек (отбор, параметры данных и вывода) платформа хранит элементом {@code v8:ValueListType} как есть,
 * их правило не касается; параметры вложенных схем — тоже (они только читаются).
 */
public final class DcsLegacyValueLists {

  /** Значение ключа {@code kind} предупреждения. */
  public static final String KIND = "valueListPresentationsLost";

  /** Сколько элементов с представлениями называть в тексте; остальные — числом. */
  private static final int NAMED_ITEMS = 3;

  private static final List<String> ROOT = List.of("DataCompositionSchema");

  private DcsLegacyValueLists() {
  }

  /**
   * Предупреждения о параметрах корня схемы, чей список значений при загрузке теряет представления.
   *
   * @param templateXml файл схемы (выгрузка конфигуратора или {@code Template.dcs} 1С:EDT)
   * @return {@code [{message, parameter, kind}]} в порядке параметров; пусто, если таких нет или файл не
   *     разбирается как XML (о битой разметке говорит проверка)
   */
  public static List<Map<String, Object>> warnings(Path templateXml) throws IOException {
    String text = Files.readString(templateXml, StandardCharsets.UTF_8);
    try {
      // Язык — как у чтения cf-dcs-info: представления те же, что в valueTyped
      return ConfigurationLanguage.with(templateXml, () -> collect(text));
    } catch (JAXBException e) {
      throw new IllegalStateException(e);
    } catch (RuntimeException e) {
      return new ArrayList<>();
    }
  }

  private static List<Map<String, Object>> collect(String text) {
    List<Map<String, Object>> out = new ArrayList<>();
    String xmlns = null;
    for (XmlLines.Node node : XmlLines.children(text, ROOT)) {
      if (!"parameter".equals(node.name())) {
        continue;
      }
      List<XmlLines.Node> children = XmlLines.childrenAt(text, node.start());
      if (!DcsRead.valueListAllowed(text, children)
        || children.stream().noneMatch(child -> "value".equals(child.name()))) {
        continue;
      }
      xmlns = xmlns == null ? DcsSettingsEdit.rootNamespaces(text) : xmlns;
      List<String> presented = presentedItems(DcsRead.parameterValueTyped(text, children, xmlns));
      if (presented.isEmpty()) {
        continue;
      }
      String name = node.leaves().getOrDefault("name", "");
      Map<String, Object> row = new LinkedHashMap<>();
      row.put("message", message(name, presented));
      row.put("parameter", name);
      row.put("kind", KIND);
      out.add(row);
    }
    return out;
  }

  /** Элементы списка {@code valueTyped} с непустым представлением подписью «значение — «представление»». */
  private static List<String> presentedItems(Map<String, Object> valueTyped) {
    List<String> out = new ArrayList<>();
    if (valueTyped == null || !"v8:ValueListType".equals(valueTyped.get("type"))
      || !(valueTyped.get("list") instanceof Map<?, ?> list) || !(list.get("items") instanceof List<?> items)) {
      return out;
    }
    for (Object item : items) {
      if (item instanceof Map<?, ?> row && row.get("presentation") instanceof String presentation
        && !presentation.isEmpty()) {
        out.add(valueLabel(row.get("value")) + " — «" + presentation + "»");
      }
    }
    return out;
  }

  /** Значение элемента списка коротко: текст, «не задано», «пустая строка» или вариант периода. */
  private static String valueLabel(Object value) {
    if (!(value instanceof Map<?, ?> map)) {
      return "?";
    }
    if (Boolean.TRUE.equals(map.get("nil"))) {
      return "не задано";
    }
    if (map.get("text") instanceof String text) {
      return text;
    }
    if (map.get("decoded") instanceof String decoded) {
      return decoded;
    }
    for (String key : List.of("period", "beginningDate")) {
      if (map.get(key) instanceof Map<?, ?> nested && nested.get("variant") instanceof String variant) {
        return variant;
      }
    }
    Object type = map.get("type");
    if ("xs:string".equals(type) || "v8:LocalStringType".equals(type)) {
      return "пустая строка";
    }
    return type == null ? "?" : String.valueOf(type);
  }

  private static String message(String parameter, List<String> presented) {
    StringBuilder items = new StringBuilder();
    for (int index = 0; index < presented.size() && index < NAMED_ITEMS; index++) {
      if (index > 0) {
        items.append(", ");
      }
      items.append(presented.get(index));
    }
    if (presented.size() > NAMED_ITEMS) {
      items.append(" и ещё ").append(presented.size() - NAMED_ITEMS);
    }
    return "параметр схемы " + parameter + ": список значений записан старой формой v8:ValueListType с "
      + "представлениями элементов (" + items + "); при загрузке платформа развернёт его в отдельные "
      + "значения, а представления потеряет — уберите представления или оставьте список как есть, если их "
      + "потеря не важна";
  }
}
