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

import static io.github.yellowhammer.edt.EdtXmlText.escape;

import io.github.yellowhammer.designerxml.cf.ConfigurationLanguage;
import io.github.yellowhammer.designerxml.cf.FillValueCanon;
import io.github.yellowhammer.designerxml.cf.LocalString;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.stream.XMLStreamException;

import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EEnum;
import org.eclipse.emf.ecore.EEnumLiteral;
import org.eclipse.emf.ecore.EStructuralFeature;

import com.google.gson.Gson;

import io.github.yellowhammer.designerxml.cf.MdContentMemberDto;
import io.github.yellowhammer.designerxml.cf.MdNamedPropertyDto;
import io.github.yellowhammer.designerxml.cf.MdObjectPropertiesDto;
import io.github.yellowhammer.designerxml.cf.MdTypeDescriptionDto;

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

  /** Принадлежность заимствованного узла. */
  private static final String ADOPTED = "Adopted";
  /** Состояние свойства, изменённого расширением. */
  private static final String EXTENDED = "Extended";
  /** Блок состояний заимствованного узла. */
  private static final String EXTENSION = "extension";
  /** Класс расширения в открывающем теге блока. */
  private static final Pattern EXTENSION_TYPE = Pattern.compile("xsi:type=\"[^\":]*:(\\w+)\"");

  /** Сравнение узлов идёт по значениям, а не по ссылкам. */
  private static final Gson NODE_STATE = new Gson();

  /** Описание типа узла: за его сменой идёт значение заполнения. */
  private static final String TYPE = "type";
  private static final String FILL_VALUE = "fillValue";
  /** Класс значения в открывающем теге. */
  private static final Pattern VALUE_TYPE = Pattern.compile("xsi:type=\"([^\"]*)\"");
  /**
   * Классы значений EDT в написании выгрузки конфигуратора; {@code core:UndefinedValue} - отсутствие
   * значения ({@code xsi:nil}).
   */
  private static final Map<String, String> DESIGNER_VALUE_TYPES = designerValueTypes();

  private EdtObjectWriter() {
  }

  private static Map<String, String> designerValueTypes() {
    Map<String, String> types = new java.util.HashMap<>();
    types.put("core:StringValue", "xs:string");
    types.put("core:NumberValue", "xs:decimal");
    types.put("core:BooleanValue", "xs:boolean");
    types.put("core:DateValue", "xs:dateTime");
    types.put("core:ReferenceValue", "xr:DesignTimeRef");
    types.put("core:UndefinedValue", null);
    return types;
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
    try {
      return ConfigurationLanguage.with(objectMdo, () -> writeDtoInLanguage(objectMdo, dto, model));
    } catch (jakarta.xml.bind.JAXBException error) {
      throw new IOException(error);
    }
  }

  private static int writeDtoInLanguage(Path objectMdo, MdObjectPropertiesDto dto, EdtModel model)
      throws IOException {
    if (dto == null || dto.internalName == null || dto.internalName.isEmpty()) {
      throw new IllegalArgumentException("Нужны вид и имя объекта.");
    }
    if (!Files.isRegularFile(objectMdo)) {
      throw new IllegalArgumentException("Файл объекта не найден: " + objectMdo);
    }

    EdtObjectReader.EdtNode node = EdtObjectReader.read(objectMdo);
    EClass eClass = model.classOf(node.kind());
    MdObjectPropertiesDto baseline = EdtObjectProperties.readDto(objectMdo, model);
    return apply(objectMdo, changes(baseline, dto, node, eClass, model), eClass, model);
  }

  /**
   * Изменение одного свойства.
   *
   * @param node узел, которому свойство принадлежит; {@code null} у самого объекта
   * @param name имя свойства
   * @param value новое значение в написании формата
   * @param localized свойство записано парами язык-значение
   * @param type описание типа, если свойство - тип; иначе {@code null}
   * @param values значения свойства-списка, по элементу на значение
   * @param members элементы состава из узлов в написании файла
   */
  private record Change(
      NodeRef node,
      String name,
      String value,
      boolean localized,
      MdTypeDescriptionDto type,
      List<String> values,
      List<MdContentMemberDto> members) {

    Change(NodeRef node, String name, String value, boolean localized) {
      this(node, name, value, localized, null, null, null);
    }

    Change(NodeRef node, String name, String value, boolean localized, MdTypeDescriptionDto type) {
      this(node, name, value, localized, type, null, null);
    }

    /** Свойство-список объекта: все прежние значения уходят, новые встают на их место. */
    static Change list(String name, List<String> values) {
      return new Change(null, name, "", false, null, values, null);
    }

    /** Состав объекта из узлов: весь состав пишется заново. */
    static Change members(String name, List<MdContentMemberDto> members) {
      return new Change(null, name, "", false, null, null, members);
    }

    boolean isList() {
      return values != null || members != null;
    }

    boolean isEmptyList() {
      return values != null ? values.isEmpty() : members.isEmpty();
    }
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
    try {
      return ConfigurationLanguage.with(objectMdo, () -> writeFieldsInLanguage(objectMdo, wanted, written, model));
    } catch (jakarta.xml.bind.JAXBException error) {
      throw new IOException(error);
    }
  }

  private static int writeFieldsInLanguage(Path objectMdo, Object wanted, Object written, EdtModel model)
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
    return apply(objectMdo, changes, eClass, model);
  }

  /** Применяет правки к файлу; без правок файл не трогается. */
  private static int apply(Path objectMdo, List<Change> changes, EClass eClass, EdtModel model)
      throws IOException {
    if (changes.isEmpty()) {
      return 0;
    }
    String xml = Files.readString(objectMdo, StandardCharsets.UTF_8);
    try {
      Files.writeString(objectMdo, apply(xml, changes, eClass, model), StandardCharsets.UTF_8);
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
    if (dto.synonym != null && !dto.synonym.equals(baseline.synonym)) {
      changes.add(new Change(null, "synonym", dto.synonym, true));
    }
    if (dto.comment != null && !dto.comment.equals(baseline.comment)) {
      changes.add(new Change(null, "comment", dto.comment, false));
    }
    Object bridge = bridge(dto);
    changes.addAll(scalarChanges(baseline, dto, eClass));
    changes.addAll(refListChanges(baseline, dto, eClass, bridge));
    Change members = membersChange(baseline, dto, eClass);
    if (members != null) {
      changes.add(members);
    }

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
   * Изменения словаря свойств вида без своего блока.
   *
   * Ключ - имя свойства выгрузки, в файле EDT то же имя со строчной буквы.
   * Пустое значение снимает свойство: в файле его больше нет.
   */
  private static List<Change> scalarChanges(MdObjectPropertiesDto baseline, MdObjectPropertiesDto dto, EClass eClass) {
    List<Change> changes = new ArrayList<>();
    if (dto.scalars == null || dto.scalars.isEmpty()) {
      return changes;
    }
    if (baseline.scalars == null) {
      throw new IllegalArgumentException("Свойства объекта " + dto.internalName + " правятся в блоке его вида.");
    }
    Map<String, Object> written = baseline.scalars;
    for (Map.Entry<String, Object> entry : dto.scalars.entrySet()) {
      String value = scalarText(entry.getValue());
      if (value.equals(scalarText(written.get(entry.getKey())))) {
        continue;
      }
      EStructuralFeature feature = EdtScalarProperties.feature(eClass, entry.getKey());
      if (feature == null || feature.getName().equals(EdtScalarProperties.BELONGING)) {
        throw new IllegalArgumentException("Свойство " + entry.getKey() + " объекта " + dto.internalName + " не правится.");
      }
      String name = feature.getName();
      if (EdtScalarProperties.type(feature).equals("boolean")) {
        changes.add(new Change(null, name, value, false));
      } else if (value.isEmpty()) {
        changes.add(Change.list(name, List.of()));
      } else {
        changes.add(new Change(null, name, literal(eClass, name, value), false));
      }
    }
    return changes;
  }

  /** Значение словаря строкой: число из JSON приходит дробным, а пишется как задано. */
  private static String scalarText(Object value) {
    if (value == null) {
      return "";
    }
    if (value instanceof Number number) {
      return new java.math.BigDecimal(number.toString()).stripTrailingZeros().toPlainString();
    }
    return String.valueOf(value);
  }

  /**
   * Изменения списков ссылок вне свойств вида: состав, вложенные подсистемы,
   * входящие документы, движения.
   *
   * Список, которого у вида нет, отклоняется: иначе панель показала бы правку
   * сохранённой. Список, который вид держит у себя, пишется вместе с его свойствами.
   */
  private static List<Change> refListChanges(
      MdObjectPropertiesDto baseline,
      MdObjectPropertiesDto dto,
      EClass eClass,
      Object bridge) {
    List<Change> changes = new ArrayList<>();
    for (String field : EdtContractNames.refFields()) {
      List<String> wanted = refs(dto, field);
      if (wanted == null || sameRefs(wanted, refs(baseline, field)) || EdtContractNames.declares(bridge, field)) {
        continue;
      }
      String feature = EdtContractNames.refsFeature(eClass, field);
      if (feature == null) {
        throw new IllegalArgumentException("Свойство " + field + " объекта " + dto.internalName + " не правится.");
      }
      changes.add(Change.list(feature, List.copyOf(wanted)));
    }
    return changes;
  }

  /** Изменение состава из узлов либо {@code null}, если он прежний. */
  private static Change membersChange(MdObjectPropertiesDto baseline, MdObjectPropertiesDto dto, EClass eClass) {
    if (dto.contentMembers == null || dto.contentMembers.equals(baseline.contentMembers)) {
      return null;
    }
    EClass item = EdtCommonAttributeContent.itemClass(eClass);
    if (item == null) {
      throw new IllegalArgumentException("Свойство contentMembers объекта " + dto.internalName + " не правится.");
    }
    return Change.members(EdtCommonAttributeContent.CONTENT,
        EdtCommonAttributeContent.written(dto.contentMembers, item));
  }

  @SuppressWarnings("unchecked")
  private static List<String> refs(MdObjectPropertiesDto dto, String field) {
    try {
      return (List<String>) MdObjectPropertiesDto.class.getField(field).get(dto);
    } catch (ReflectiveOperationException error) {
      throw new IllegalStateException("Не удалось прочитать список " + field, error);
    }
  }

  /** Списки ссылок совпадают: пустой и неприсланный - одно и то же. */
  private static boolean sameRefs(List<String> left, List<String> right) {
    return (left == null ? List.of() : left).equals(right == null ? List.of() : right);
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
      String feature = EdtContractNames.feature(field.getName());
      String nodeClass = classOfNode(model, dto.kind, feature);
      for (int index = 0; index < wanted.size(); index++) {
        NodeRef node = new NodeRef(feature, written.get(index).name, null);
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
    if (value == null || Objects.equals(value, was)) {
      return null;
    }
    if (value instanceof MdTypeDescriptionDto description) {
      return typeChange(ref, field.getName(), description, was);
    }
    if (field.getType() != String.class) {
      return null;
    }

    String name = field.getName();
    if (name.equals("name")) {
      throw new IllegalArgumentException("Переименование узла правится своей командой: " + written.name);
    }
    if (field.isAnnotationPresent(LocalString.class)) {
      return new Change(ref, name, String.valueOf(value), true);
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
    if (value instanceof MdTypeDescriptionDto description) {
      return typeChange(null, name, description, was);
    }
    if (value instanceof List<?> items) {
      return listChange(name, items, eClass);
    }
    if (type != String.class) {
      return null;
    }
    if (field.isAnnotationPresent(LocalString.class)) {
      return new Change(null, name, String.valueOf(value), true);
    }
    // Свойства, которых в файле нет, писать нечем: их значение задаёт схема
    if (eClass != null && eClass.getEStructuralFeature(name) == null && node.list(name).isEmpty()) {
      return null;
    }
    return new Change(null, name, literal(eClass, name, String.valueOf(value)), false);
  }

  /**
   * Изменение свойства-списка: ссылок или перечислимых значений.
   *
   * Список, которого в схеме нет, в файле не бывает; состав из своих узлов
   * строкой не записать, и правка отклоняется.
   */
  private static Change listChange(String name, List<?> items, EClass eClass) {
    EStructuralFeature feature = eClass == null ? null : eClass.getEStructuralFeature(name);
    if (feature == null) {
      return null;
    }
    if (!(feature.getEType() instanceof EEnum) && !EdtContractNames.isRefList(feature)) {
      throw new IllegalArgumentException("Свойство " + name + " не правится.");
    }
    List<String> values = new ArrayList<>();
    for (Object item : items) {
      values.add(literal(eClass, name, String.valueOf(item)));
    }
    return Change.list(name, values);
  }

  /**
   * Изменение описания типа либо {@code null}, если оно прежнее.
   *
   * Описания сравниваются по значениям: контракт присылает их целиком.
   */
  private static Change typeChange(NodeRef node, String name, MdTypeDescriptionDto wanted, Object written) {
    if (NODE_STATE.toJson(wanted).equals(NODE_STATE.toJson(written))) {
      return null;
    }
    return new Change(node, name, null, false, wanted);
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
  private static String apply(String xml, List<Change> changes, EClass eClass, EdtModel model)
      throws XMLStreamException {
    List<String> order = order(eClass);
    List<Edit> edits = new ArrayList<>();
    for (int index = 0; index < changes.size(); index++) {
      Change change = changes.get(index);
      // Новые свойства объекта в одной точке встают по порядку схемы, свойства
      // узла - в порядке правок
      int rank = change.node() == null && order.contains(change.name())
          ? order.indexOf(change.name())
          : order.size() + index;
      edits.add(edit(xml, change, order, model).ranked(rank));
      Edit fill = fillValueEdit(xml, change);
      if (fill != null) {
        edits.add(fill);
      }
    }
    edits.addAll(stateEdits(xml, changes, model));
    // С конца файла: смещения посчитаны по исходному тексту. В одной точке
    // сначала меняется прежний текст, потом вставляется новый, и вставки идут от
    // последней по порядку к первой: каждая следующая встаёт перед предыдущей
    edits.sort(Comparator.comparingInt(Edit::start).reversed()
        .thenComparing(Edit::insertion)
        .thenComparing(Comparator.comparingInt(Edit::rank).reversed()));

    StringBuilder text = new StringBuilder(xml);
    for (Edit edit : edits) {
      text.replace(edit.start(), edit.end(), edit.text());
    }
    return text.toString();
  }

  /**
   * Правка участка файла.
   *
   * @param rank место среди вставок в ту же точку
   */
  private record Edit(int start, int end, String text, int rank) {

    Edit(int start, int end, String text) {
      this(start, end, text, 0);
    }

    Edit ranked(int value) {
      return new Edit(start, end, text, value);
    }

    /** Вставка нового текста, а не замена прежнего. */
    boolean insertion() {
      return start == end;
    }
  }

  /**
   * Значение заполнения узла, тип которого меняет правка.
   *
   * <p>То же правило, что у выгрузки конфигуратора ({@link FillValueCanon}): значение, которого в
   * новом типе нет, становится пустым - {@code core:UndefinedValue}, у типа «только строка» пустой
   * {@code core:StringValue}, как у нового реквизита. Совместимое значение остаётся байт в байт.
   *
   * @return замена элемента {@code fillValue}; {@code null}, если менять нечего
   */
  private static Edit fillValueEdit(String xml, Change change) throws XMLStreamException {
    if (change.type() == null || !TYPE.equals(change.name())) {
      return null;
    }
    EdtObjectRegions.Region owner = nodeRegion(xml, change.node());
    EdtObjectRegions.Region region = first(owner == null
        ? EdtObjectRegions.properties(xml, FILL_VALUE)
        : EdtObjectRegions.nested(xml, owner, FILL_VALUE));
    if (!region.found()) {
      return null;
    }
    String tag = xml.substring(region.start(), region.end());
    Matcher type = VALUE_TYPE.matcher(tag.substring(0, tag.indexOf('>')));
    String valueType = type.find() ? DESIGNER_VALUE_TYPES.getOrDefault(type.group(1), type.group(1)) : "";
    return switch (FillValueCanon.afterTypeChange(valueType, change.type())) {
      case KEEP -> null;
      case NIL -> new Edit(region.start(), region.end(), "<fillValue xsi:type=\"core:UndefinedValue\"/>");
      case EMPTY_STRING -> new Edit(region.start(), region.end(), "<fillValue xsi:type=\"core:StringValue\"/>");
    };
  }

  /** Замена значения или вставка нового свойства. */
  private static Edit edit(String xml, Change change, List<String> order, EdtModel model)
      throws XMLStreamException {
    if (change.isList()) {
      return listEdit(xml, change, order);
    }
    EdtObjectRegions.Region owner = nodeRegion(xml, change.node());
    List<EdtObjectRegions.Region> regions = owner == null
        ? EdtObjectRegions.properties(xml, change.name())
        : EdtObjectRegions.nested(xml, owner, change.name());
    if (change.localized()) {
      return localizedEdit(xml, change, regions, order, owner, model);
    }
    EdtObjectRegions.Region region = first(regions);
    if (region.found()) {
      return new Edit(region.start(), region.end(), element(xml, change, indent(xml, region.start()), model));
    }

    // Новое свойство встаёт целой строкой, с отступом соседей
    if (owner == null) {
      int at = EdtObjectRegions.insertionPoint(xml, order, change.name());
      String indent = propertyIndent(xml, at);
      return new Edit(at, at, indent + element(xml, change, indent, model) + eol(xml));
    }
    int at = EdtObjectRegions.lineStart(xml, owner.end());
    String indent = indent(xml, owner.start()) + INDENT;
    return new Edit(at, at, indent + element(xml, change, indent, model) + eol(xml));
  }

  /**
   * Правка многоязычного свойства: у каждого языка свой элемент.
   *
   * Меняется значение на языке конфигурации, тексты на остальных языках остаются
   * как были. Если этого языка в файле ещё нет, элемент встаёт за одноимёнными.
   */
  private static Edit localizedEdit(
      String xml,
      Change change,
      List<EdtObjectRegions.Region> regions,
      List<String> order,
      EdtObjectRegions.Region owner,
      EdtModel model) throws XMLStreamException {
    EdtObjectRegions.Region mine =
        EdtObjectRegions.byKey(xml, regions, ConfigurationLanguage.current());
    if (mine.found()) {
      EdtObjectRegions.Region value = EdtObjectRegions.childRegion(xml, mine, "value");
      return value.found()
          ? new Edit(value.start(), value.end(), "<value>" + escape(change.value()) + "</value>")
          : new Edit(mine.start(), mine.end(), element(xml, change, indent(xml, mine.start()), model));
    }
    if (!regions.isEmpty()) {
      EdtObjectRegions.Region last = regions.get(regions.size() - 1);
      String indent = indent(xml, last.start());
      String added = eol(xml) + indent + element(xml, change, indent, model);
      return new Edit(last.end(), last.end(), added);
    }
    if (owner == null) {
      int at = EdtObjectRegions.insertionPoint(xml, order, change.name());
      String indent = propertyIndent(xml, at);
      return new Edit(at, at, indent + element(xml, change, indent, model) + eol(xml));
    }
    int at = EdtObjectRegions.lineStart(xml, owner.end());
    String indent = indent(xml, owner.start()) + INDENT;
    return new Edit(at, at, indent + element(xml, change, indent, model) + eol(xml));
  }

  /**
   * Правка свойства-списка: все прежние значения уходят, новые встают на их место.
   */
  private static Edit listEdit(String xml, Change change, List<String> order) throws XMLStreamException {
    List<EdtObjectRegions.Region> regions = EdtObjectRegions.properties(xml, change.name());
    String eol = eol(xml);
    if (regions.isEmpty()) {
      if (change.isEmptyList()) {
        return new Edit(0, 0, "");
      }
      int at = EdtObjectRegions.insertionPoint(xml, order, change.name());
      String indent = propertyIndent(xml, at);
      return new Edit(at, at, indent + elements(change, indent, eol) + eol);
    }
    int start = regions.get(0).start();
    int end = regions.get(regions.size() - 1).end();
    if (change.isEmptyList()) {
      // Пустой список: строки свойства уходят целиком, вместе с отступом и переводом строки
      int lineStart = EdtObjectRegions.lineStart(xml, start);
      int lineEnd = Math.min(xml.length(), end + eol.length());
      return new Edit(lineStart, lineEnd, "");
    }
    return new Edit(start, end, elements(change, indent(xml, start), eol));
  }

  /** Значения списка строками файла. */
  private static String elements(Change change, String indent, String eol) {
    if (change.members() != null) {
      return EdtCommonAttributeContent.render(change.members(), indent, eol);
    }
    StringBuilder text = new StringBuilder();
    for (String value : change.values()) {
      if (text.length() > 0) {
        text.append(eol).append(indent);
      }
      text.append("<%s>%s</%s>".formatted(change.name(), escape(value), change.name()));
    }
    return text.toString();
  }

  /**
   * Состояние «изменено» у свойств заимствованных узлов.
   *
   * Заимствованный узел держит состояния своих свойств в блоке {@code extension}
   * класса расширения: изменённое свойство получает там {@code Extended}, как
   * пишет сама EDT. Свои узлы расширения состояний не имеют. Правка блока одна
   * на узел, сколько бы свойств у него ни менялось.
   */
  private static List<Edit> stateEdits(String xml, List<Change> changes, EdtModel model)
      throws XMLStreamException {
    EdtObjectReader.EdtNode root;
    try {
      root = EdtObjectReader.parse(xml);
    } catch (IOException error) {
      throw new XMLStreamException(error);
    }
    Map<NodeRef, Set<String>> byNode = new LinkedHashMap<>();
    for (Change change : changes) {
      EdtObjectReader.EdtNode node = nodeOf(root, change.node());
      if (node == null || !ADOPTED.equals(node.property("objectBelonging"))) {
        continue;
      }
      if (change.type() != null) {
        throw new IllegalArgumentException(
            "Тип заимствованного реквизита правится в расширяемой конфигурации: " + change.node().name());
      }
      byNode.computeIfAbsent(change.node(), key -> new LinkedHashSet<>()).add(change.name());
    }
    List<Edit> edits = new ArrayList<>();
    for (Map.Entry<NodeRef, Set<String>> entry : byNode.entrySet()) {
      Edit edit = stateEdit(xml, entry.getKey(), entry.getValue(), model);
      if (edit != null) {
        edits.add(edit);
      }
    }
    return edits;
  }

  /** Узел файла по ссылке; {@code null}, если такого нет. */
  private static EdtObjectReader.EdtNode nodeOf(EdtObjectReader.EdtNode root, NodeRef ref) {
    if (ref == null) {
      return root;
    }
    EdtObjectReader.EdtNode owner = nodeOf(root, ref.owner());
    if (owner == null) {
      return null;
    }
    for (EdtObjectReader.EdtNode child : owner.list(ref.feature())) {
      if (child.name().equals(ref.name())) {
        return child;
      }
    }
    return null;
  }

  /**
   * Блок {@code extension} узла с состояниями изменённых свойств.
   *
   * Состояния идут в порядке класса расширения, прежние записи блока остаются
   * как были; свойство, которого класс расширения не знает, состояния не имеет.
   */
  private static Edit stateEdit(String xml, NodeRef ref, Set<String> features, EdtModel model)
      throws XMLStreamException {
    EdtObjectRegions.Region owner = nodeRegion(xml, ref);
    EdtObjectRegions.Region block = owner == null
        ? EdtObjectRegions.property(xml, EXTENSION)
        : first(EdtObjectRegions.nested(xml, owner, EXTENSION));
    if (!block.found()) {
      throw new IllegalStateException(
          "У заимствованного узла нет блока extension: " + (ref == null ? "объект" : ref.name()));
    }
    String text = xml.substring(block.start(), block.end());
    Matcher type = EXTENSION_TYPE.matcher(text);
    List<String> order = order(type.find() ? model.classOf(type.group(1)) : null);
    for (String feature : features) {
      if (!feature.equals("comment") && !order.contains(feature)) {
        throw new IllegalArgumentException(
            "Свойство " + feature + " заимствованного узла принадлежит расширяемой конфигурации.");
      }
    }
    Set<String> states = new LinkedHashSet<>();
    for (String feature : order) {
      if (features.contains(feature)) {
        states.add(feature);
      }
    }
    if (states.isEmpty()) {
      return null;
    }

    List<EdtObjectRegions.Child> written = EdtObjectRegions.emptyTag(xml, block)
        ? List.of()
        : EdtObjectRegions.children(xml, block);
    String eol = eol(xml);
    String indent = indent(xml, block.start());
    String inner = indent + INDENT;
    int open = text.indexOf('>');
    StringBuilder out = new StringBuilder(text.substring(0, open).stripTrailing());
    if (out.charAt(out.length() - 1) == '/') {
      out.setLength(out.length() - 1);
    }
    out.append('>').append(eol);
    Set<String> placed = new LinkedHashSet<>();
    for (String feature : order) {
      if (states.contains(feature)) {
        out.append(inner).append('<').append(feature).append('>').append(EXTENDED)
            .append("</").append(feature).append('>').append(eol);
        placed.add(feature);
        continue;
      }
      for (EdtObjectRegions.Child child : written) {
        if (child.name().equals(feature)) {
          out.append(inner).append(xml, child.region().start(), child.region().end()).append(eol);
          placed.add(feature);
        }
      }
    }
    for (EdtObjectRegions.Child child : written) {
      if (!placed.contains(child.name())) {
        out.append(inner).append(xml, child.region().start(), child.region().end()).append(eol);
      }
    }
    out.append(indent).append("</").append(EXTENSION).append('>');
    return new Edit(block.start(), block.end(), out.toString());
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

  /** Разметка свойства: многоязычная строка и описание типа занимают несколько строк. */
  private static String element(String xml, Change change, String indent, EdtModel model) {
    if (change.type() != null) {
      return EdtTypeDescription.render(change.type(), change.name(), model, indent, eol(xml));
    }
    String value = escape(change.value());
    if (!change.localized()) {
      return "<%s>%s</%s>".formatted(change.name(), value, change.name());
    }
    String eol = eol(xml);
    return new StringBuilder()
        .append("<").append(change.name()).append(">").append(eol)
        .append(indent).append(INDENT)
        .append("<key>").append(ConfigurationLanguage.current()).append("</key>").append(eol)
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

  /**
   * Отступ нового свойства объекта по строке, перед которой оно встаёт.
   *
   * Последнее по схеме свойство встаёт перед закрывающим тегом объекта, а тот
   * на уровень мельче свойств.
   */
  private static String propertyIndent(String xml, int at) {
    String indent = indent(xml, at + INDENT.length());
    return xml.startsWith("</", at + indent.length()) ? indent + INDENT : indent;
  }

  /** Перевод строки файла: смешивать переводы в одном файле нельзя. */
  private static String eol(String xml) {
    return xml.contains("\r\n") ? "\r\n" : "\n";
  }
}
