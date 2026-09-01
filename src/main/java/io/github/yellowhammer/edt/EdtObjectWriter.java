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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import javax.xml.stream.XMLStreamException;

import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EEnum;
import org.eclipse.emf.ecore.EEnumLiteral;
import org.eclipse.emf.ecore.EStructuralFeature;

import com.google.gson.Gson;

import io.github.yellowhammer.designerxml.cf.MdNamedPropertyDto;
import io.github.yellowhammer.designerxml.cf.MdObjectPropertiesDto;

/**
 * Точечная запись свойств объекта 1С:EDT.
 *
 * Правка одного свойства меняет один участок файла: остальное остаётся байт в
 * байт, вместе с порядком свойств, отступами и переводами строк. Пересобирать
 * файл из модели нельзя - EDT хранит в нём и то, чего наш контракт не знает.
 */
public final class EdtObjectWriter {

  /** Отступ уровня в файлах EDT. */
  private static final String INDENT = "  ";

  /** Сравнение узлов идёт по значениям, а не по ссылкам. */
  private static final Gson NODE_STATE = new Gson();

  private EdtObjectWriter() {
  }

  /**
   * Записывает изменённые свойства объекта.
   *
   * @param objectMdo файл объекта
   * @param dto свойства целиком: изменения считаются сравнением с файлом
   * @param model метамодель EDT
   * @return число изменённых свойств
   * @throws IOException если файл не читается или не пишется
   */
  public static int writeDto(Path objectMdo, MdObjectPropertiesDto dto, EdtModel model) throws IOException {
    if (dto == null || dto.internalName == null || dto.internalName.isEmpty()) {
      throw new IllegalArgumentException("Нужны вид и имя объекта.");
    }
    if (!Files.isRegularFile(objectMdo)) {
      throw new IllegalArgumentException("Файл объекта не найден: " + objectMdo);
    }

    EdtObjectReader.EdtNode node = EdtObjectReader.read(objectMdo);
    EClass eClass = model.classOf(node.kind());
    MdObjectPropertiesDto baseline = EdtObjectProperties.readDto(objectMdo, model);
    List<Change> changes = changes(baseline, dto, node, eClass);
    if (changes.isEmpty()) {
      return 0;
    }

    String xml = Files.readString(objectMdo, StandardCharsets.UTF_8);
    try {
      Files.writeString(objectMdo, apply(xml, changes, eClass), StandardCharsets.UTF_8);
    } catch (XMLStreamException error) {
      throw new IOException("Не удалось разобрать файл объекта: " + objectMdo, error);
    }
    return changes.size();
  }

  /** Изменение одного свойства. */
  private record Change(String name, String value, boolean localized) {
  }

  /**
   * Сравнивает присланные свойства с файлом.
   *
   * Сравниваются только те поля, что несёт контракт: чего в нём нет, то в файле
   * и не трогается.
   */
  private static List<Change> changes(
      MdObjectPropertiesDto baseline,
      MdObjectPropertiesDto dto,
      EdtObjectReader.EdtNode node,
      EClass eClass) {
    refuseCompositionChange(baseline, dto);

    List<Change> changes = new ArrayList<>();
    if (dto.synonymRu != null && !dto.synonymRu.equals(baseline.synonymRu)) {
      changes.add(new Change("synonym", dto.synonymRu, true));
    }
    if (dto.comment != null && !dto.comment.equals(baseline.comment)) {
      changes.add(new Change("comment", dto.comment, false));
    }

    Object bridge = bridge(dto);
    Object baselineBridge = bridge(baseline);
    if (bridge == null || baselineBridge == null) {
      return changes;
    }
    for (Field field : bridge.getClass().getFields()) {
      if (Modifier.isStatic(field.getModifiers())) {
        continue;
      }
      Change change = change(field, bridge, baselineBridge, node, eClass);
      if (change != null) {
        changes.add(change);
      }
    }
    return changes;
  }

  /**
   * Отказывает в правке состава объекта.
   *
   * Реквизиты, табличные части и значения перечисления правятся своими
   * операциями, которых для формата EDT ещё нет. Промолчать здесь нельзя: правка
   * ушла бы в никуда, а панель показала бы её сохранённой.
   */
  private static void refuseCompositionChange(MdObjectPropertiesDto baseline, MdObjectPropertiesDto dto) {
    for (Field field : MdObjectPropertiesDto.class.getFields()) {
      if (Modifier.isStatic(field.getModifiers())
          || !field.getGenericType().getTypeName().endsWith("<" + MdNamedPropertyDto.class.getName() + ">")) {
        continue;
      }
      List<String> wanted = state(field, dto);
      if (wanted != null && !wanted.equals(state(field, baseline))) {
        throw new IllegalArgumentException(
          "Правка состава объекта в формате 1С:EDT пока не поддержана: " + field.getName());
      }
    }
  }

  /** Состояние узлов списка либо {@code null}, если список не прислан. */
  private static List<String> state(Field field, MdObjectPropertiesDto dto) {
    try {
      @SuppressWarnings("unchecked")
      List<MdNamedPropertyDto> nodes = (List<MdNamedPropertyDto>) field.get(dto);
      return nodes == null ? null : nodes.stream().map(EdtObjectWriter::state).toList();
    } catch (IllegalAccessException error) {
      throw new IllegalStateException("Не удалось прочитать узлы " + field.getName(), error);
    }
  }

  /**
   * Состояние узла для сравнения.
   *
   * Сравнивается узел целиком, а не одно имя: панель правит и синоним реквизита,
   * и его свойства, и такая правка тоже не должна пропасть молча.
   */
  private static String state(MdNamedPropertyDto node) {
    return NODE_STATE.toJson(node);
  }

  /** Изменение поля свойств вида объекта либо {@code null}, если оно прежнее. */
  private static Change change(
      Field field,
      Object bridge,
      Object baselineBridge,
      EdtObjectReader.EdtNode node,
      EClass eClass) {
    Object value;
    Object was;
    try {
      value = field.get(bridge);
      was = field.get(baselineBridge);
    } catch (IllegalAccessException error) {
      throw new IllegalStateException("Не удалось прочитать свойство " + field.getName(), error);
    }
    if (value == null || Objects.equals(value, was)) {
      return null;
    }

    String name = field.getName();
    Class<?> type = field.getType();
    if (type == boolean.class || type == Boolean.class) {
      return new Change(name, String.valueOf(value), false);
    }
    if (type != String.class) {
      // Списки ссылок и состав объекта правятся своими операциями
      return null;
    }
    if (name.endsWith("Ru")) {
      return new Change(name.substring(0, name.length() - 2), String.valueOf(value), true);
    }
    // Свойства, которых в файле нет, писать нечем: их значение задаёт схема
    if (eClass != null && eClass.getEStructuralFeature(name) == null && node.list(name).isEmpty()) {
      return null;
    }
    return new Change(name, literal(eClass, name, String.valueOf(value)), false);
  }

  /** Свойства вида объекта: {@code catalog}, {@code document}, {@code register}. */
  private static Object bridge(MdObjectPropertiesDto dto) {
    for (Field field : MdObjectPropertiesDto.class.getFields()) {
      if (field.getType().getName().startsWith("io.github.yellowhammer.designerxml.cf.Md")
          && field.getType().getSimpleName().endsWith("PropertiesDto")) {
        try {
          Object value = field.get(dto);
          if (value != null) {
            return value;
          }
        } catch (IllegalAccessException error) {
          throw new IllegalStateException("Не удалось прочитать свойства вида", error);
        }
      }
    }
    return null;
  }

  /**
   * Значение в написании формата.
   *
   * Контракт несёт имена констант ({@code DONT_USE}), а файл - литералы схемы
   * ({@code DontUse}). Литерал ищется в схеме: своей таблицы значений нет.
   */
  private static String literal(EClass eClass, String name, String value) {
    EStructuralFeature feature = eClass == null ? null : eClass.getEStructuralFeature(name);
    if (feature == null || !(feature.getEType() instanceof EEnum type)) {
      return value;
    }
    for (EEnumLiteral literal : type.getELiterals()) {
      if (EdtPropertyValues.constantName(literal.getName()).equals(value)) {
        return literal.getName();
      }
    }
    throw new IllegalArgumentException("Свойство " + name + " не принимает значение " + value);
  }

  /** Применяет правки к тексту файла, начиная с конца: смещения посчитаны по исходному тексту. */
  private static String apply(String xml, List<Change> changes, EClass eClass) throws XMLStreamException {
    List<String> order = order(eClass);
    List<Edit> edits = new ArrayList<>();
    for (Change change : changes) {
      edits.add(edit(xml, change, order));
    }
    edits.sort(Comparator.comparingInt(Edit::start).reversed());

    StringBuilder text = new StringBuilder(xml);
    for (Edit edit : edits) {
      text.replace(edit.start(), edit.end(), edit.text());
    }
    return text.toString();
  }

  /** Правка участка файла. */
  private record Edit(int start, int end, String text) {
  }

  /** Замена значения или вставка нового свойства. */
  private static Edit edit(String xml, Change change, List<String> order) throws XMLStreamException {
    EdtObjectRegions.Region region = EdtObjectRegions.property(xml, change.name());
    if (region.found()) {
      return new Edit(region.start(), region.end(), element(xml, change, indent(xml, region.start())));
    }
    // Новое свойство встаёт целой строкой, с отступом соседей
    int at = EdtObjectRegions.insertionPoint(xml, order, change.name());
    String indent = indent(xml, at + INDENT.length());
    return new Edit(at, at, indent + element(xml, change, indent) + eol(xml));
  }

  /** Разметка свойства: многоязычная строка занимает несколько строк. */
  private static String element(String xml, Change change, String indent) {
    String value = escape(change.value());
    if (!change.localized()) {
      return "<%s>%s</%s>".formatted(change.name(), value, change.name());
    }
    String eol = eol(xml);
    return new StringBuilder()
        .append("<").append(change.name()).append(">").append(eol)
        .append(indent).append(INDENT).append("<key>ru</key>").append(eol)
        .append(indent).append(INDENT).append("<value>").append(value).append("</value>").append(eol)
        .append(indent).append("</").append(change.name()).append(">")
        .toString();
  }

  /** Порядок свойств в схеме: по нему новое свойство встаёт на своё место. */
  private static List<String> order(EClass eClass) {
    List<String> order = new ArrayList<>();
    if (eClass != null) {
      for (EStructuralFeature feature : eClass.getEAllStructuralFeatures()) {
        order.add(feature.getName());
      }
    }
    return order;
  }

  /** Отступ строки, в которой начинается участок. */
  private static String indent(String xml, int start) {
    int line = EdtObjectRegions.lineStart(xml, start);
    int end = line;
    while (end < xml.length() && (xml.charAt(end) == ' ' || xml.charAt(end) == '\t')) {
      end++;
    }
    return xml.substring(line, end);
  }

  /** Перевод строки файла: смешивать переводы в одном файле нельзя. */
  private static String eol(String xml) {
    return xml.contains("\r\n") ? "\r\n" : "\n";
  }

  /** Экранирование значения: в файле оно лежит текстом элемента. */
  private static String escape(String value) {
    return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
  }
}
