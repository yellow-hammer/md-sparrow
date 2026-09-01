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
    return apply(objectMdo, changes(baseline, dto, node, eClass, model), eClass);
  }

  /**
   * Изменение одного свойства.
   *
   * @param node узел, которому свойство принадлежит; {@code null} у самого объекта
   * @param name имя свойства
   * @param value новое значение в написании формата
   * @param localized свойство записано парами язык-значение
   */
  private record Change(NodeRef node, String name, String value, boolean localized) {
  }

  /**
   * Узел объекта.
   *
   * @param feature вид узла: {@code attributes}, {@code tabularSections}
   * @param name имя узла
   * @param owner узел-владелец или {@code null}
   */
  private record NodeRef(String feature, String name, NodeRef owner) {
  }

  /**
   * Записывает изменённые свойства корня файла.
   *
   * Сравниваются поля двух описаний: у объекта это свойства его вида, у
   * конфигурации - её собственные. Пишется только то, что отличается.
   *
   * @param objectMdo файл объекта или конфигурации
   * @param wanted присланные свойства
   * @param written свойства, прочитанные из файла
   * @param model метамодель EDT
   * @return число изменённых свойств
   * @throws IOException если файл не читается или не пишется
   */
  public static int writeFields(Path objectMdo, Object wanted, Object written, EdtModel model)
      throws IOException {
    EdtObjectReader.EdtNode node = EdtObjectReader.read(objectMdo);
    EClass eClass = model.classOf(node.kind());

    List<Change> changes = new ArrayList<>();
    for (Field field : wanted.getClass().getFields()) {
      if (Modifier.isStatic(field.getModifiers())) {
        continue;
      }
      Change change = change(field, wanted, written, node, eClass);
      if (change != null) {
        changes.add(change);
      }
    }
    return apply(objectMdo, changes, eClass);
  }

  /** Применяет правки к файлу; без правок файл не трогается. */
  private static int apply(Path objectMdo, List<Change> changes, EClass eClass) throws IOException {
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
      EClass eClass,
      EdtModel model) {
    List<Change> changes = new ArrayList<>(nodeChanges(baseline, dto, model));
    if (dto.synonymRu != null && !dto.synonymRu.equals(baseline.synonymRu)) {
      changes.add(new Change(null, "synonym", dto.synonymRu, true));
    }
    if (dto.comment != null && !dto.comment.equals(baseline.comment)) {
      changes.add(new Change(null, "comment", dto.comment, false));
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
   * Сравнивает узлы объекта с файлом.
   *
   * Состав узлов правится своими командами, поэтому здесь меняются только
   * свойства уже существующих узлов, а появление или пропажа узла отклоняется:
   * иначе правка ушла бы в никуда, а панель показала бы её сохранённой.
   */
  private static List<Change> nodeChanges(
      MdObjectPropertiesDto baseline,
      MdObjectPropertiesDto dto,
      EdtModel model) {
    List<Change> changes = new ArrayList<>();
    for (Field field : MdObjectPropertiesDto.class.getFields()) {
      if (Modifier.isStatic(field.getModifiers())
          || !field.getGenericType().getTypeName().endsWith("<" + MdNamedPropertyDto.class.getName() + ">")) {
        continue;
      }
      List<MdNamedPropertyDto> wanted = nodes(field, dto);
      List<MdNamedPropertyDto> written = nodes(field, baseline);
      if (wanted == null) {
        continue;
      }
      if (written == null || !names(wanted).equals(names(written))) {
        throw new IllegalArgumentException(
          "Состав объекта правится своими командами: " + field.getName());
      }
      String nodeClass = classOfNode(model, dto.kind, field.getName());
      for (int index = 0; index < wanted.size(); index++) {
        NodeRef node = new NodeRef(field.getName(), written.get(index).name, null);
        changes.addAll(nodeChanges(node, written.get(index), wanted.get(index), model, nodeClass));
      }
    }
    return changes;
  }

  /** Изменения свойств одного узла и его собственных узлов. */
  private static List<Change> nodeChanges(
      NodeRef ref,
      MdNamedPropertyDto written,
      MdNamedPropertyDto wanted,
      EdtModel model,
      String nodeClass) {
    List<Change> changes = new ArrayList<>();
    if (NODE_STATE.toJson(written).equals(NODE_STATE.toJson(wanted))) {
      return changes;
    }
    for (Field field : MdNamedPropertyDto.class.getFields()) {
      if (Modifier.isStatic(field.getModifiers())) {
        continue;
      }
      Change change = nodeChange(ref, field, written, wanted, model.classOf(nodeClass));
      if (change != null) {
        changes.add(change);
      }
    }

    // У табличной части свои реквизиты, и правятся они так же
    if (wanted.attributes != null && written.attributes != null
        && names(wanted.attributes).equals(names(written.attributes))) {
      String attributeClass = classOfNode(model, nodeClass, "attributes");
      for (int index = 0; index < wanted.attributes.size(); index++) {
        NodeRef nested = new NodeRef("attributes", written.attributes.get(index).name, ref);
        changes.addAll(nodeChanges(
            nested, written.attributes.get(index), wanted.attributes.get(index), model, attributeClass));
      }
    }
    return changes;
  }

  /** Изменение одного свойства узла либо {@code null}, если оно прежнее. */
  private static Change nodeChange(
      NodeRef ref,
      Field field,
      MdNamedPropertyDto written,
      MdNamedPropertyDto wanted,
      EClass nodeClass) {
    Object value;
    Object was;
    try {
      value = field.get(wanted);
      was = field.get(written);
    } catch (IllegalAccessException error) {
      throw new IllegalStateException("Не удалось прочитать свойство узла " + field.getName(), error);
    }
    if (value == null || Objects.equals(value, was) || field.getType() != String.class) {
      return null;
    }

    String name = field.getName();
    if (name.equals("name")) {
      throw new IllegalArgumentException("Переименование узла правится своей командой: " + written.name);
    }
    if (name.endsWith("Ru")) {
      return new Change(ref, name.substring(0, name.length() - 2), String.valueOf(value), true);
    }
    return new Change(ref, name, literal(nodeClass, name, String.valueOf(value)), false);
  }

  /**
   * Класс узла по схеме владельца.
   *
   * Реквизит справочника метамодель зовёт CatalogAttribute, и значения его
   * перечислимых свойств лежат именно там.
   */
  private static String classOfNode(EdtModel model, String ownerClass, String feature) {
    String owner = ownerClass == null ? "" : Character.toUpperCase(ownerClass.charAt(0)) + ownerClass.substring(1);
    for (EdtModel.Composition item : model.composition(owner)) {
      if (item.feature().equals(feature)) {
        return item.objectType();
      }
    }
    return null;
  }

  /** Узлы списка либо {@code null}, если список не прислан. */
  private static List<MdNamedPropertyDto> nodes(Field field, MdObjectPropertiesDto dto) {
    try {
      @SuppressWarnings("unchecked")
      List<MdNamedPropertyDto> nodes = (List<MdNamedPropertyDto>) field.get(dto);
      return nodes;
    } catch (IllegalAccessException error) {
      throw new IllegalStateException("Не удалось прочитать узлы " + field.getName(), error);
    }
  }

  private static List<String> names(List<MdNamedPropertyDto> nodes) {
    return nodes.stream().map(node -> node.name).toList();
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
      return new Change(null, name, String.valueOf(value), false);
    }
    if (type != String.class) {
      // Списки ссылок и состав объекта правятся своими операциями
      return null;
    }
    if (name.endsWith("Ru")) {
      return new Change(null, name.substring(0, name.length() - 2), String.valueOf(value), true);
    }
    // Свойства, которых в файле нет, писать нечем: их значение задаёт схема
    if (eClass != null && eClass.getEStructuralFeature(name) == null && node.list(name).isEmpty()) {
      return null;
    }
    return new Change(null, name, literal(eClass, name, String.valueOf(value)), false);
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
        // В файл идёт литерал схемы: у режимов совместимости он не совпадает с
        // именем константы
        return literal.getLiteral();
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
    EdtObjectRegions.Region owner = nodeRegion(xml, change.node());
    EdtObjectRegions.Region region = owner == null
        ? EdtObjectRegions.property(xml, change.name())
        : first(EdtObjectRegions.nested(xml, owner, change.name()));
    if (region.found()) {
      return new Edit(region.start(), region.end(), element(xml, change, indent(xml, region.start())));
    }

    // Новое свойство встаёт целой строкой, с отступом соседей
    if (owner == null) {
      int at = EdtObjectRegions.insertionPoint(xml, order, change.name());
      String indent = indent(xml, at + INDENT.length());
      return new Edit(at, at, indent + element(xml, change, indent) + eol(xml));
    }
    int at = EdtObjectRegions.lineStart(xml, owner.end());
    String indent = indent(xml, owner.start()) + INDENT;
    return new Edit(at, at, indent + element(xml, change, indent) + eol(xml));
  }

  /** Границы узла, которому принадлежит свойство; {@code null} у самого объекта. */
  private static EdtObjectRegions.Region nodeRegion(String xml, NodeRef node) throws XMLStreamException {
    if (node == null) {
      return null;
    }
    EdtObjectRegions.Region owner = nodeRegion(xml, node.owner());
    List<EdtObjectRegions.Region> siblings = owner == null
        ? EdtObjectRegions.properties(xml, node.feature())
        : EdtObjectRegions.nested(xml, owner, node.feature());
    EdtObjectRegions.Region region = EdtObjectRegions.byName(xml, siblings, node.name());
    if (!region.found()) {
      throw new IllegalArgumentException("Узел не найден: " + node.name());
    }
    return region;
  }

  private static EdtObjectRegions.Region first(List<EdtObjectRegions.Region> regions) {
    return regions.isEmpty() ? EdtObjectRegions.MISSING : regions.get(0);
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
