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

import io.github.yellowhammer.designerxml.SchemaVersion;

import jakarta.xml.bind.JAXBException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Точечные правки схемы компоновки данных: наборы данных, их поля, связи наборов, ресурсы,
 * вычисляемые поля и параметры.
 *
 * <p>Операции адресуют элементы верхнего уровня схемы; заполнение полей по запросу принимает и путь
 * к вложенному набору. Наборы, поля и параметры адресуются по имени или пути к данным, связи и ресурсы
 * порядковым номером в файле с нуля: ключа у связи нет, путь к данным ресурса не уникален. Текст
 * меняется по смещениям {@link XmlLines} в порядке xs:sequence; перед записью схема перечитывается
 * моделью, битый файл не пишется.
 */
public final class DcsEdit {

  /** Путь к корню схемы для {@link XmlLines#children}. */
  private static final List<String> ROOT = List.of("DataCompositionSchema");

  /** Подэлементы набора, которые правит {@link #setDataSetProps}. */
  private static final List<String> KNOWN_PROPS = List.of("autoFillFields", "dataSource", "objectName");

  /** Подэлементы набора-запроса по xs:sequence формата (DataSet + DataSetQuery). */
  static final List<String> QUERY_SEQUENCE =
    List.of("name", "field", "dataSource", "query", "autoFillFields", "useQueryGroupIfPossible");

  /** Подэлементы набора-объекта по xs:sequence формата (DataSet + DataSetObject). */
  static final List<String> OBJECT_SEQUENCE = List.of("name", "field", "dataSource", "objectName");

  /** Подэлементы набора-объединения по xs:sequence формата (DataSet + DataSetUnion). */
  static final List<String> UNION_SEQUENCE = List.of("name", "field", "item");

  /** Подэлементы набора, которые по xs:sequence идут не позже полей: общее начало DataSet всех видов. */
  static final List<String> FIELD_PREDECESSORS = List.of("name", "field");

  /** Свойства нового поля набора, которые принимает {@link #addField}. */
  private static final List<String> ADD_FIELD_PAYLOAD = List.of("dataPath", "field", "title");

  /** Свойства поля набора, которые правит {@link #setField}. */
  private static final List<String> FIELD_PROPS =
    List.of("newDataPath", "field", "title", "useRestriction", "attributeUseRestriction", "role", "valueType");

  /** Глубины владельцев типа от корня схемы (корень: 1): по ним ссылочный тип получает префикс {@code d{N}p1}. */
  private static final int PARAMETER_DEPTH = 2;

  private static final int CALCULATED_FIELD_DEPTH = 2;

  private static final int FIELD_DEPTH = 3;

  /** Подэлементы поля набора по xs:sequence формата (DataSetFieldField). */
  static final List<String> FIELD_SEQUENCE = List.of(
    "dataPath", "field", "title", "useRestriction", "attributeUseRestriction", "role",
    "presentationExpression", "orderExpression", "inHierarchyDataSet", "inHierarchyDataSetParameter",
    "valueType", "appearance", "availableValue", "inputParameters");

  /** Флаги ограничения использования по xs:sequence формата (FieldUseRestriction). */
  static final List<String> USE_RESTRICTION_FLAGS = List.of("field", "condition", "group", "order");

  /** Подэлементы роли поля по xs:sequence формата (dcscom:DataSetFieldRole). */
  static final List<String> ROLE_SEQUENCE = List.of(
    "periodNumber", "periodType", "dimension", "parentDimension", "account", "accountTypeExpression",
    "balance", "balanceGroupName", "balanceType", "accountingBalanceType", "accountField",
    "ignoreNullValues", "required", "dimensionAttribute", "isMain");

  /** Булевы подэлементы роли: платформа пишет только взведённые. */
  private static final Set<String> ROLE_FLAGS = Set.of(
    "dimension", "account", "balance", "ignoreNullValues", "required", "dimensionAttribute", "isMain");

  private static final Set<String> ROLE_STRINGS = Set.of(
    "parentDimension", "accountTypeExpression", "balanceGroupName", "accountField");

  /** Перечисления роли и их значения по XSD пространства common. */
  private static final Map<String, List<String>> ROLE_ENUMS = Map.of(
    "periodType", List.of("Main", "Specify", "Additional"),
    "balanceType", List.of("None", "OpeningBalance", "ClosingBalance"),
    "accountingBalanceType", List.of("None", "Debit", "Credit"));

  /** Элементы корня схемы, которые по её порядку идут не позже наборов данных. */
  static final List<String> DATA_SET_PREDECESSORS = List.of("dataSource", "dataSet");

  /** Элементы корня схемы, которые по её порядку идут не позже связей наборов. */
  static final List<String> LINK_PREDECESSORS = List.of("dataSource", "dataSet", "dataSetLink");

  /** Подэлементы связи наборов по xs:sequence формата (DataSetLink). */
  static final List<String> LINK_SEQUENCE = List.of(
    "sourceDataSet", "destinationDataSet", "sourceExpression", "destinationExpression",
    "parameter", "parameterListAllowed", "linkConditionExpression", "startExpression", "required");

  /** Обязательные по формату подэлементы связи наборов: начало её последовательности. */
  static final List<String> LINK_REQUIRED = List.of(
    "sourceDataSet", "destinationDataSet", "sourceExpression", "destinationExpression");

  private static final Set<String> LINK_FLAGS = Set.of("parameterListAllowed", "required");

  /** Флаг списка у связи с параметром, когда он не задан: загрузка в платформу дописывает явный {@code false}. */
  private static final String PARAMETER_LIST_DEFAULT = "<parameterListAllowed>false</parameterListAllowed>";

  /** Отказ записать флаг списка у связи без параметра: загрузка такой флаг отбрасывает. */
  private static final String LINK_LIST_FLAG_REFUSAL = "Флаг «Список параметров» связи наборов"
    + " (parameterListAllowed=true) без параметра связи не записать: загрузка платформы не хранит флаг"
    + " без параметра связи и молча отбрасывает его. Задайте parameter или снимите флаг (false или null).";

  /** Отказ взвести обязательность связи: XSD её допускает, но загрузка отбрасывает {@code true}. */
  private static final String LINK_REQUIRED_REFUSAL = "Обязательность связи наборов (required=true) не записать:"
    + " загрузка платформы этот флаг не хранит ни при каком составе связи и молча отбрасывает его."
    + " Снимите флаг (required: false или null).";

  /** Элементы корня схемы, которые по её порядку идут не позже ресурсов. */
  static final List<String> TOTAL_FIELD_PREDECESSORS =
    List.of("dataSource", "dataSet", "dataSetLink", "calculatedField", "totalField");

  /** Подэлементы ресурса по xs:sequence формата (TotalField). */
  static final List<String> TOTAL_FIELD_SEQUENCE = List.of("dataPath", "expression", "group");

  /** Свойства ресурса, которые принимают {@link #addTotalField} и {@link #setTotalField}. */
  private static final List<String> TOTAL_FIELD_PROPS = List.of("dataPath", "expression", "groups");

  /** Подэлементы вычисляемого поля по xs:sequence формата (CalculatedField). */
  static final List<String> CALCULATED_FIELD_SEQUENCE = List.of(
    "dataPath", "expression", "title", "useRestriction", "presentationExpression", "orderExpression",
    "appearance", "availableValue", "valueType", "inputParameters");

  /** Свойства вычисляемого поля, которые правит {@link #setCalculatedField}. */
  private static final List<String> CALCULATED_FIELD_PROPS =
    List.of("newDataPath", "expression", "title", "valueType");

  /** Элементы корня схемы, которые по её порядку идут не позже параметров. */
  static final List<String> PARAMETER_PREDECESSORS =
    List.of("dataSource", "dataSet", "dataSetLink", "calculatedField", "totalField", "parameter");

  /** Подэлементы параметра схемы по xs:sequence формата (Parameter). */
  static final List<String> PARAMETER_SEQUENCE = List.of(
    "name", "title", "valueType", "value", "useRestriction", "expression", "availableValue",
    "valueListAllowed", "availableAsField", "functionalOptionsParameter", "inputParameters",
    "denyIncompleteValues", "use");

  /** Свойства нового параметра схемы, которые принимает {@link #addParameter}. */
  private static final List<String> ADD_PARAMETER_PAYLOAD =
    List.of("name", "title", "expression", "use", "useRestriction", "valueType", "value");

  /** Свойства параметра схемы, которые правит {@link #setParameter}. */
  private static final List<String> PARAMETER_PROPS = List.of(
    "newName", "title", "useRestriction", "expression", "use", "valueType", "value", "valueListAllowed",
    "availableAsField", "denyIncompleteValues");

  /**
   * Булевы признаки параметра и их значения по умолчанию. Умолчание платформа не пишет и при загрузке
   * не хранит, даже записанное явно.
   */
  private static final Map<String, Boolean> PARAMETER_FLAG_DEFAULTS = Map.of(
    "valueListAllowed", false, "availableAsField", true, "denyIncompleteValues", false);

  /** Значения использования параметра по XSD пространства core (DataCompositionParameterUse). */
  private static final List<String> PARAMETER_USE = List.of("Always", "Auto");

  private static final Pattern XSI_TYPE = Pattern.compile("xsi:type=[\"']([^\"']+)[\"']");

  /** Булев элемент, взведённый лексемой xs:boolean. */
  private static final Pattern TRUE_FLAG = Pattern.compile("<[^>]*>\\s*(?:true|1)\\s*</[^>]*>");

  private static final Pattern XSI_NIL = Pattern.compile("\\bxsi:nil=[\"'](?:true|1)[\"']");

  private DcsEdit() {
  }

  /**
   * Добавляет минимальный набор данных после последнего набора, иначе после источников данных.
   *
   * <p>Запрос получает источник и пустой {@code <query/>} без {@code autoFillFields}: умолчание true
   * платформа не пишет. Имя объекта совпадает с именем набора, объединение добавляется пустым.
   *
   * @param name имя нового набора, свободное без учёта регистра
   * @param type вид набора: {@code query}, {@code object} или {@code union}
   * @param dataSource имя источника данных; у объединения не используется
   * @throws IllegalArgumentException если имя занято или вид набора неизвестен
   */
  public static void addDataSet(Path templateXml, SchemaVersion version, String name, String type, String dataSource)
    throws IOException, JAXBException {
    String setName = required(name, "name");
    String setType = required(type, "type");
    String text = Files.readString(templateXml, StandardCharsets.UTF_8);
    List<XmlLines.Node> roots = XmlLines.children(text, ROOT);
    XmlLines.Node namesake = namesake(roots, "dataSet", "name", setName, null);
    if (namesake != null) {
      throw new IllegalArgumentException("Набор данных «" + setName + "» уже есть в схеме: «"
        + namesake.leaves().get("name") + "».");
    }
    String eol = XmlLines.newline(text);
    String block = switch (setType) {
      case "query" -> "\t<dataSet xsi:type=\"DataSetQuery\">" + eol
        + "\t\t<name>" + DcsRead.escape(setName) + "</name>" + eol
        + "\t\t<dataSource>" + DcsRead.escape(required(dataSource, "dataSource")) + "</dataSource>" + eol
        + "\t\t<query/>" + eol
        + "\t</dataSet>" + eol;
      case "object" -> "\t<dataSet xsi:type=\"DataSetObject\">" + eol
        + "\t\t<name>" + DcsRead.escape(setName) + "</name>" + eol
        + "\t\t<dataSource>" + DcsRead.escape(required(dataSource, "dataSource")) + "</dataSource>" + eol
        + "\t\t<objectName>" + DcsRead.escape(setName) + "</objectName>" + eol
        + "\t</dataSet>" + eol;
      case "union" -> "\t<dataSet xsi:type=\"DataSetUnion\">" + eol
        + "\t\t<name>" + DcsRead.escape(setName) + "</name>" + eol
        + "\t</dataSet>" + eol;
      default -> throw new IllegalArgumentException(
        "Вид набора данных должен быть query, object или union, а не " + setType + ".");
    };
    XmlLines.Node anchor = null;
    for (XmlLines.Node node : roots) {
      if (DATA_SET_PREDECESSORS.contains(node.name())) {
        anchor = node;
      }
    }
    String updated = insertBlock(text, anchor, block, eol);
    DcsRead.verify(updated, version);
    Files.writeString(templateXml, updated, StandardCharsets.UTF_8);
  }

  /** Вставляет блок со строки после якоря; без якоря - со строки после открывающего тега корня. */
  private static String insertBlock(String text, XmlLines.Node anchor, String block, String eol) {
    int after;
    if (anchor != null) {
      after = anchor.end();
    } else {
      int root = text.indexOf("<DataCompositionSchema");
      if (root < 0) {
        throw new IllegalArgumentException("Файл не является схемой компоновки данных.");
      }
      after = XmlLines.startTagEnd(text, root) + 1;
    }
    int lineEnd = text.indexOf('\n', after);
    return lineEnd < 0
      ? text.substring(0, after) + eol + block + text.substring(after)
      : text.substring(0, lineEnd + 1) + block + text.substring(lineEnd + 1);
  }

  /**
   * Удаляет набор данных верхнего уровня.
   *
   * @param name имя набора
   * @throws IllegalArgumentException если набора нет или он упомянут в связях наборов
   */
  public static void removeDataSet(Path templateXml, SchemaVersion version, String name)
    throws IOException, JAXBException {
    String setName = required(name, "name");
    String text = Files.readString(templateXml, StandardCharsets.UTF_8);
    List<XmlLines.Node> roots = XmlLines.children(text, ROOT);
    XmlLines.Node target = dataSet(roots, setName);
    if (target == null) {
      throw new IllegalArgumentException("Набор данных не найден: " + setName);
    }
    long links = roots.stream()
      .filter(node -> "dataSetLink".equals(node.name()) && linkReferences(node, setName))
      .count();
    if (links > 0) {
      throw new IllegalArgumentException(
        "Набор данных " + setName + " упомянут в связях наборов (" + links + "): сначала удалите эти связи.");
    }
    String updated = XmlLines.removeAll(
      text, List.of(XmlLines.wholeLines(text, target.start(), target.end(), "dataSet")));
    DcsRead.verify(updated, version);
    Files.writeString(templateXml, updated, StandardCharsets.UTF_8);
  }

  /**
   * Переименовывает набор данных верхнего уровня и правит ссылки на него в связях наборов.
   *
   * @param name прежнее имя набора
   * @param newName новое имя, не занятое другим набором без учёта регистра
   * @throws IllegalArgumentException если набора нет или новое имя занято
   */
  public static void renameDataSet(Path templateXml, SchemaVersion version, String name, String newName)
    throws IOException, JAXBException {
    String oldName = required(name, "name");
    String freshName = required(newName, "newName");
    String text = Files.readString(templateXml, StandardCharsets.UTF_8);
    List<XmlLines.Node> roots = XmlLines.children(text, ROOT);
    XmlLines.Node target = dataSet(roots, oldName);
    if (target == null) {
      throw new IllegalArgumentException("Набор данных не найден: " + oldName);
    }
    XmlLines.Node occupied = namesake(roots, "dataSet", "name", freshName, target);
    if (occupied != null) {
      throw new IllegalArgumentException("Имя «" + freshName + "» уже занято: набор данных «"
        + occupied.leaves().get("name") + "» уже есть в схеме.");
    }
    List<XmlGranularPatch.Replacement> replacements = new ArrayList<>();
    replacements.add(childReplacement(text, target, "name", freshName));
    for (XmlLines.Node node : roots) {
      if (!"dataSetLink".equals(node.name())) {
        continue;
      }
      if (oldName.equals(node.leaves().get("sourceDataSet"))) {
        replacements.add(childReplacement(text, node, "sourceDataSet", freshName));
      }
      if (oldName.equals(node.leaves().get("destinationDataSet"))) {
        replacements.add(childReplacement(text, node, "destinationDataSet", freshName));
      }
    }
    String updated = XmlGranularPatch.apply(text, replacements);
    if (updated.equals(text)) {
      return;
    }
    DcsRead.verify(updated, version);
    Files.writeString(templateXml, updated, StandardCharsets.UTF_8);
  }

  /**
   * Меняет подэлементы набора данных: {@code autoFillFields}, {@code dataSource}, {@code objectName}.
   *
   * <p>Ключи вне {@code props} не трогаются. {@code autoFillFields=false} пишется элементом,
   * {@code true} и {@code null} снимают его, как платформа. Источник и имя объекта требуют непустой
   * текст. Подэлемент, которого нет у вида набора, отклоняется.
   *
   * @param name имя набора
   * @param props значения по именам подэлементов; неизвестное имя - отказ
   */
  public static void setDataSetProps(Path templateXml, SchemaVersion version, String name, Map<String, Object> props)
    throws IOException, JAXBException {
    String setName = required(name, "name");
    if (props == null || props.isEmpty()) {
      return;
    }
    for (String key : props.keySet()) {
      if (!KNOWN_PROPS.contains(key)) {
        throw new IllegalArgumentException("Неизвестное свойство набора данных: " + key);
      }
    }
    String text = Files.readString(templateXml, StandardCharsets.UTF_8);
    List<XmlLines.Node> roots = XmlLines.children(text, ROOT);
    XmlLines.Node target = dataSet(roots, setName);
    if (target == null) {
      throw new IllegalArgumentException("Набор данных не найден: " + setName);
    }
    String kind = DcsDataSets.kind(text, target);
    List<String> sequence = switch (kind) {
      case "query" -> QUERY_SEQUENCE;
      case "object" -> OBJECT_SEQUENCE;
      default -> UNION_SEQUENCE;
    };
    List<XmlLines.Node> children = XmlLines.childrenAt(text, target.start());
    // Подэлементы идут в порядке xs:sequence, чтобы вставки в одну точку не перепутались
    List<XmlGranularPatch.Replacement> replacements = new ArrayList<>();
    if (props.containsKey("dataSource")) {
      replacements.add(requiredTextProp(text, kind, sequence, children, "dataSource", props.get("dataSource")));
    }
    if (props.containsKey("objectName")) {
      replacements.add(requiredTextProp(text, kind, sequence, children, "objectName", props.get("objectName")));
    }
    if (props.containsKey("autoFillFields")) {
      autoFillFields(text, kind, sequence, children, props.get("autoFillFields")).ifPresent(replacements::add);
    }
    if (replacements.isEmpty()) {
      return;
    }
    String updated = XmlGranularPatch.withoutOverlaps(replacements)
      .map(safe -> XmlGranularPatch.apply(text, safe))
      .orElseThrow(() -> new IllegalStateException("правки свойств набора данных пересеклись"));
    if (updated.equals(text)) {
      return;
    }
    DcsRead.verify(updated, version);
    Files.writeString(templateXml, updated, StandardCharsets.UTF_8);
  }

  /**
   * Добавляет обычное поле ({@code DataSetFieldField}) после существующих полей набора.
   *
   * <p>Заголовок пишется {@code v8:LocalStringType} с языком конфигурации. Путь к данным сверяется
   * без учёта регистра: {@code Сумма} и {@code СУММА} в схеме одно поле.
   *
   * @param dataSet имя набора данных верхнего уровня
   * @param payload свойства поля: обязательные {@code dataPath} и {@code field}, необязательный {@code title}
   * @throws IllegalArgumentException если набора нет, путь к данным занят или свойство неизвестно
   */
  public static void addField(Path templateXml, SchemaVersion version, String dataSet, Map<String, Object> payload)
    throws IOException, JAXBException {
    String setName = required(dataSet, "dataSet");
    if (payload == null || payload.isEmpty()) {
      throw new IllegalArgumentException("обязательное поле не задано: payload");
    }
    for (String key : payload.keySet()) {
      if (!ADD_FIELD_PAYLOAD.contains(key)) {
        throw new IllegalArgumentException("Неизвестное свойство поля набора данных: " + key);
      }
    }
    ConfigurationLanguage.with(templateXml, () -> {
      String text = Files.readString(templateXml, StandardCharsets.UTF_8);
      XmlLines.Node target = dataSet(XmlLines.children(text, ROOT), setName);
      if (target == null) {
        throw new IllegalArgumentException("Набор данных не найден: " + setName);
      }
      List<XmlLines.Node> children = XmlLines.childrenAt(text, target.start());
      String dataPath = requiredPayloadString(payload.get("dataPath"), "dataPath");
      XmlLines.Node namesake = namesake(children, "field", "dataPath", dataPath, null);
      if (namesake != null) {
        throw new IllegalArgumentException("Поле «" + dataPath + "» уже есть в наборе: «"
          + namesake.leaves().get("dataPath") + "».");
      }
      String block = "<field xsi:type=\"DataSetFieldField\">"
        + "<dataPath>" + DcsRead.escape(dataPath) + "</dataPath>"
        + "<field>" + DcsRead.escape(requiredPayloadString(payload.get("field"), "field")) + "</field>"
        + titleXml(payload.get("title"))
        + "</field>";
      String updated = XmlGranularPatch.apply(
        text, List.of(blockInsertion(text, children, FIELD_PREDECESSORS, "field", block)));
      DcsRead.verify(updated, version);
      Files.writeString(templateXml, updated, StandardCharsets.UTF_8);
      return null;
    });
  }

  /**
   * Меняет подэлементы обычного поля набора по xs:sequence: замена, вставка или снятие.
   *
   * <p>Ключи вне {@code props} не трогаются. {@code newDataPath} и {@code field} требуют непустой
   * текст; путь, занятый другим полем без учёта регистра, отклоняется. Остальные свойства заменяются
   * целиком, {@code null} или пустое значение снимает элемент, флаги ограничений и роли пишутся
   * только взведённые. Папки, вложенные наборы и поля внутри объединения не правятся. Правка поля с
   * {@code balance} без имени группы остатка дописывает пустое {@code <dcscom:balanceGroupName/>},
   * как платформа. Эхо прочитанного файл не трогает.
   *
   * @throws IllegalArgumentException если набора или поля нет, поле не обычное или значение не по формату
   */
  public static void setField(
    Path templateXml, SchemaVersion version, String dataSet, String dataPath, Map<String, Object> props)
    throws IOException, JAXBException {
    String setName = required(dataSet, "dataSet");
    String path = required(dataPath, "dataPath");
    if (props == null || props.isEmpty()) {
      return;
    }
    for (String key : props.keySet()) {
      if (!FIELD_PROPS.contains(key)) {
        throw new IllegalArgumentException("Неизвестное свойство поля набора данных: " + key);
      }
    }
    ConfigurationLanguage.with(templateXml, () -> {
      String text = Files.readString(templateXml, StandardCharsets.UTF_8);
      List<XmlLines.Node> roots = XmlLines.children(text, ROOT);
      XmlLines.Node target = dataSet(roots, setName);
      if (target == null) {
        String union = enclosingUnion(text, roots, setName);
        if (union != null) {
          throw new IllegalArgumentException("Набор данных не найден на верхнем уровне схемы: «" + setName
            + "» вложен в объединение «" + union + "», а поля вложенных наборов объединения доступны только "
            + "для чтения.");
        }
        throw new IllegalArgumentException("Набор данных не найден: " + setName);
      }
      List<XmlLines.Node> setChildren = XmlLines.childrenAt(text, target.start());
      XmlLines.Node field = plainField(text, setChildren, path);
      List<XmlLines.Node> children = XmlLines.childrenAt(text, field.start());
      List<XmlGranularPatch.Replacement> replacements = new ArrayList<>();
      List<XmlGranularPatch.Replacement> completions = new ArrayList<>();
      Optional<XmlGranularPatch.Replacement> roleCompletion = balanceGroupNameCompletion(text, children);
      if (props.containsKey("newDataPath")) {
        String fresh = requiredPayloadString(props.get("newDataPath"), "dataPath");
        XmlLines.Node occupied = fresh.equals(path) ? null : namesake(setChildren, "field", "dataPath", fresh, field);
        if (occupied != null) {
          throw new IllegalArgumentException("Путь к данным «" + fresh + "» уже занят: поле «"
            + occupied.leaves().get("dataPath") + "» уже есть в наборе.");
        }
        replacements.add(childReplacement(text, field, "dataPath", fresh));
      }
      if (props.containsKey("field")) {
        replacements.add(childReplacement(text, field, "field", requiredPayloadString(props.get("field"), "field")));
      }
      if (props.containsKey("title")) {
        titleBlock(text, children, FIELD_SEQUENCE, props.get("title"))
          .ifPresent(replacements::add);
      }
      if (props.containsKey("useRestriction")) {
        optionalBlock(text, children, FIELD_SEQUENCE, "useRestriction",
          useRestrictionXml("useRestriction", props.get("useRestriction"))).ifPresent(replacements::add);
      }
      if (props.containsKey("attributeUseRestriction")) {
        optionalBlock(text, children, FIELD_SEQUENCE, "attributeUseRestriction",
          useRestrictionXml("attributeUseRestriction", props.get("attributeUseRestriction")))
          .ifPresent(replacements::add);
      }
      if (props.containsKey("role")) {
        Optional<XmlGranularPatch.Replacement> role =
          optionalBlock(text, children, FIELD_SEQUENCE, "role", roleXml(props.get("role"), version));
        // Эхо роли, отличное от файла только пустым balanceGroupName, считается дополнением, а не правкой
        boolean echo = role.isPresent() && roleCompletion.isPresent()
          && fieldPatched(text, List.of(role.get())).equals(fieldPatched(text, List.of(roleCompletion.get())));
        role.ifPresent(echo ? completions::add : replacements::add);
      } else {
        roleCompletion.ifPresent(completions::add);
      }
      if (props.containsKey("valueType")) {
        valueTypeBlock(templateXml, version, text, children, FIELD_SEQUENCE, props.get("valueType"), FIELD_DEPTH)
          .ifPresent(replacements::add);
      }
      if (replacements.isEmpty()) {
        return null;
      }
      if (fieldPatched(text, replacements).equals(text)) {
        return null;
      }
      completions.addAll(replacements);
      String updated = fieldPatched(text, completions);
      DcsRead.verify(updated, version);
      Files.writeString(templateXml, updated, StandardCharsets.UTF_8);
      return null;
    });
  }

  /**
   * Удаляет обычное поле набора данных.
   *
   * @param dataSet имя набора данных верхнего уровня
   * @param dataPath путь к данным поля
   * @throws IllegalArgumentException если набора или поля нет либо поле - папка или вложенный набор
   */
  public static void removeField(Path templateXml, SchemaVersion version, String dataSet, String dataPath)
    throws IOException, JAXBException {
    String setName = required(dataSet, "dataSet");
    String path = required(dataPath, "dataPath");
    String text = Files.readString(templateXml, StandardCharsets.UTF_8);
    XmlLines.Node target = dataSet(XmlLines.children(text, ROOT), setName);
    if (target == null) {
      throw new IllegalArgumentException("Набор данных не найден: " + setName);
    }
    XmlLines.Node field = plainField(text, XmlLines.childrenAt(text, target.start()), path);
    String updated = XmlLines.removeAll(
      text, List.of(XmlLines.wholeLines(text, field.start(), field.end(), "field")));
    DcsRead.verify(updated, version);
    Files.writeString(templateXml, updated, StandardCharsets.UTF_8);
  }

  /**
   * Дописывает в набор-запрос поля по списку выборки его текста ({@link DcsQueryModel}).
   *
   * <p>Тип поля из текста не вывести, поэтому новое поле получает только {@code dataPath} и {@code field}.
   * Добавляются недостающие колонки (сверка по {@code dataPath} и {@code field} без учёта регистра)
   * после существующих полей; существующие не меняются. Если добавлять нечего, файл не пишется.
   *
   * @param dataSet имя набора верхнего уровня или путь {@code Объединение/Вложенный}; набор должен быть
   *     запросом с непустым текстом
   * @return {@code added}: добавленные пути в порядке выборки; {@code skipped}: пропущенные выражения
   *     с причинами; {@code unions}: колонки, дописанные в объединения, если такие есть
   * @throws IllegalArgumentException если набора нет, он не запрос, текст запроса пуст
   *     или в выборке звёздочка
   */
  public static Map<String, Object> autofillFields(Path templateXml, SchemaVersion version, String dataSet)
    throws IOException, JAXBException {
    String setName = required(dataSet, "dataSet").trim();
    String text = Files.readString(templateXml, StandardCharsets.UTF_8);
    DcsDataSets.Target resolved = DcsDataSets.resolve(text, setName);
    XmlLines.Node target = resolved.node();
    if ("union".equals(resolved.kind())) {
      throw new IllegalArgumentException("Набор данных " + setName + " - объединение, а не запрос: укажите путь "
        + "к вложенному набору-запросу (" + setName + "/Имя).");
    }
    if (!"query".equals(resolved.kind())) {
      throw new IllegalArgumentException(
        "Набор данных " + setName + " - не запрос: поля заполняются по тексту запроса.");
    }
    String query = target.leaves().get("query");
    if (query == null || query.isBlank()) {
      throw new IllegalArgumentException(
        "Текст запроса набора данных " + setName + " пуст: заполнять поля не из чего.");
    }
    DcsQueryModel.Selection selection = DcsQueryModel.parseSelection(query);
    List<XmlLines.Node> children = XmlLines.childrenAt(text, target.start());
    // Сверка и по field: путь поля может отличаться от колонки (Итоги.СуммаОплаты у СуммаОплаты).
    // Регистр не учитывается, как в платформе
    Set<String> taken = new HashSet<>();
    for (XmlLines.Node child : children) {
      if (!"field".equals(child.name())) {
        continue;
      }
      for (String key : List.of("dataPath", "field")) {
        String name = child.leaves().get(key);
        if (name != null) {
          taken.add(name.toUpperCase(Locale.ROOT));
        }
      }
    }
    List<String> added = new ArrayList<>();
    StringBuilder block = new StringBuilder();
    for (DcsQueryModel.Field field : selection.fields()) {
      // повтор имени внутри выборки тоже пропускается
      if (!taken.add(field.name().toUpperCase(Locale.ROOT))) {
        continue;
      }
      added.add(field.name());
      block.append("<field xsi:type=\"DataSetFieldField\">")
        .append("<dataPath>").append(DcsRead.escape(field.name())).append("</dataPath>")
        .append("<field>").append(DcsRead.escape(field.name())).append("</field>")
        .append("</field>");
    }
    List<Map<String, String>> skipped = new ArrayList<>();
    for (DcsQueryModel.Skipped miss : selection.skipped()) {
      Map<String, String> row = new LinkedHashMap<>();
      row.put("expression", miss.expression());
      row.put("reason", miss.reason());
      skipped.add(row);
    }
    Map<String, Object> answer = new LinkedHashMap<>();
    answer.put("added", added);
    answer.put("skipped", skipped);
    if (added.isEmpty()) {
      return answer;
    }
    List<XmlGranularPatch.Replacement> edits = new ArrayList<>();
    edits.add(blockInsertion(text, children, FIELD_PREDECESSORS, "field", block.toString()));
    List<Map<String, Object>> unions = unionInsertions(text, resolved.path(), added, edits);
    if (!unions.isEmpty()) {
      answer.put("unions", unions);
    }
    String updated = XmlGranularPatch.apply(text, edits);
    DcsRead.verify(updated, version);
    Files.writeString(templateXml, updated, StandardCharsets.UTF_8);
    return answer;
  }

  /**
   * Дописывает добавленные колонки вложенного набора в поля объединений над ним: конфигуратор включает
   * в поля объединения поля всех вложенных наборов, а строгая проверка расхождения не видит. Колонки,
   * которые у объединения уже есть (по {@code dataPath} или {@code field} без учёта регистра), пропускаются.
   *
   * @param path путь вложенного набора
   * @param edits правки файла, сюда добавляются вставки
   * @return {@code {dataSet, added}} по каждому дополненному объединению, сверху вниз
   */
  private static List<Map<String, Object>> unionInsertions(
    String text, String path, List<String> added, List<XmlGranularPatch.Replacement> edits) {
    List<Map<String, Object>> out = new ArrayList<>();
    String[] parts = path.split("/");
    StringBuilder prefix = new StringBuilder();
    for (int i = 0; i < parts.length - 1; i++) {
      if (i > 0) {
        prefix.append('/');
      }
      prefix.append(parts[i]);
      DcsDataSets.Target union = DcsDataSets.resolve(text, prefix.toString());
      List<XmlLines.Node> children = XmlLines.childrenAt(text, union.node().start());
      Set<String> taken = new HashSet<>();
      for (XmlLines.Node child : children) {
        if (!"field".equals(child.name())) {
          continue;
        }
        for (String key : List.of("dataPath", "field")) {
          String name = child.leaves().get(key);
          if (name != null) {
            taken.add(name.toUpperCase(Locale.ROOT));
          }
        }
      }
      List<String> missing = new ArrayList<>();
      StringBuilder block = new StringBuilder();
      for (String name : added) {
        if (!taken.add(name.toUpperCase(Locale.ROOT))) {
          continue;
        }
        missing.add(name);
        block.append("<field xsi:type=\"DataSetFieldField\">")
          .append("<dataPath>").append(DcsRead.escape(name)).append("</dataPath>")
          .append("<field>").append(DcsRead.escape(name)).append("</field>")
          .append("</field>");
      }
      if (missing.isEmpty()) {
        continue;
      }
      edits.add(blockInsertion(text, children, FIELD_PREDECESSORS, "field", block.toString()));
      Map<String, Object> row = new LinkedHashMap<>();
      row.put("dataSet", union.path());
      row.put("added", missing);
      out.add(row);
    }
    return out;
  }

  /**
   * Добавляет связь наборов данных после последней связи, иначе после наборов данных.
   *
   * <p>Наборы связи должны существовать на верхнем уровне схемы, необязательные подэлементы пишутся
   * только заданные. У связи с параметром незаданный {@code parameterListAllowed} пишется явным
   * {@code false}, как хранит платформа; {@code parameterListAllowed=true} без параметра и
   * {@code required=true} отклоняются: загрузка их не хранит.
   *
   * @throws IllegalArgumentException если свойство неизвестно, обязательное не задано, набора
   *     связи нет в схеме или заданы недопустимые {@code required}/{@code parameterListAllowed}
   */
  public static void addLink(Path templateXml, SchemaVersion version, Map<String, Object> payload)
    throws IOException, JAXBException {
    if (payload == null || payload.isEmpty()) {
      throw new IllegalArgumentException("обязательное поле не задано: payload");
    }
    for (String key : payload.keySet()) {
      if (!LINK_SEQUENCE.contains(key)) {
        throw new IllegalArgumentException("Неизвестное свойство связи наборов данных: " + key);
      }
    }
    String text = Files.readString(templateXml, StandardCharsets.UTF_8);
    List<XmlLines.Node> roots = XmlLines.children(text, ROOT);
    String eol = XmlLines.newline(text);
    boolean withParameter = payload.get("parameter") instanceof String parameter && !parameter.isBlank();
    StringBuilder block = new StringBuilder("\t<dataSetLink>").append(eol);
    for (String element : LINK_SEQUENCE) {
      String elementXml;
      if (LINK_REQUIRED.contains(element)) {
        String value = linkRequiredText(roots, element, payload.get(element));
        elementXml = "<" + element + ">" + DcsRead.escape(value) + "</" + element + ">";
      } else if ("parameterListAllowed".equals(element) && !withParameter) {
        // Без параметра загрузка флаг не хранит: true отклоняется, false и null не пишутся
        if (linkListFlag(payload.get(element))) {
          throw new IllegalArgumentException(LINK_LIST_FLAG_REFUSAL);
        }
        elementXml = "";
      } else if ("parameterListAllowed".equals(element) && payload.get(element) == null) {
        elementXml = PARAMETER_LIST_DEFAULT;
      } else {
        elementXml = linkOptionalXml(element, payload.get(element));
      }
      if (!elementXml.isEmpty()) {
        block.append("\t\t").append(elementXml).append(eol);
      }
    }
    block.append("\t</dataSetLink>").append(eol);
    XmlLines.Node anchor = null;
    for (XmlLines.Node node : roots) {
      if (LINK_PREDECESSORS.contains(node.name())) {
        anchor = node;
      }
    }
    String updated = insertBlock(text, anchor, XmlGranularPatch.lineBreaks(block.toString(), eol), eol);
    DcsRead.verify(updated, version);
    Files.writeString(templateXml, updated, StandardCharsets.UTF_8);
  }

  /**
   * Меняет подэлементы связи наборов по xs:sequence: замена, вставка или снятие.
   *
   * <p>Ключи вне {@code props} не трогаются, обязательные подэлементы требуют непустой текст, наборы
   * должны существовать, необязательные снимаются {@code null}, строковые также пустым текстом.
   * {@code required=true} допустим только эхом записанного ({@link #linkRequiredEdit}). У связи с
   * параметром {@code parameterListAllowed=null} пишется явным {@code false}, без параметра флаг
   * разбирает {@link #linkListFlagEdit}. Эхо прочитанного файл не трогает.
   *
   * @param index порядковый номер {@code dataSetLink} в файле с нуля
   * @throws IllegalArgumentException если связи нет, свойство неизвестно, значение не по формату,
   *     взводится {@code required=true} или {@code parameterListAllowed=true} у связи без параметра
   */
  public static void setLink(Path templateXml, SchemaVersion version, int index, Map<String, Object> props)
    throws IOException, JAXBException {
    if (props == null || props.isEmpty()) {
      return;
    }
    for (String key : props.keySet()) {
      if (!LINK_SEQUENCE.contains(key)) {
        throw new IllegalArgumentException("Неизвестное свойство связи наборов данных: " + key);
      }
    }
    String text = Files.readString(templateXml, StandardCharsets.UTF_8);
    List<XmlLines.Node> roots = XmlLines.children(text, ROOT);
    XmlLines.Node target = linkAt(roots, index);
    List<XmlLines.Node> children = XmlLines.childrenAt(text, target.start());
    Object parameterAfter = props.containsKey("parameter")
      ? props.get("parameter")
      : target.leaves().get("parameter");
    boolean withParameter = parameterAfter instanceof String parameter && !parameter.isBlank();
    String writtenParameter = target.leaves().get("parameter");
    boolean parameterRemoved = !withParameter && props.containsKey("parameter")
      && writtenParameter != null && !writtenParameter.isBlank();
    List<XmlGranularPatch.Replacement> edits = new ArrayList<>();
    List<XmlGranularPatch.Replacement> withCompletions = new ArrayList<>();
    for (String element : LINK_SEQUENCE) {
      Optional<XmlGranularPatch.Replacement> replacement = Optional.empty();
      Optional<XmlGranularPatch.Replacement> completion = Optional.empty();
      if ("parameterListAllowed".equals(element) && !withParameter) {
        replacement = linkListFlagEdit(text, children, props, parameterRemoved);
      } else if ("parameterListAllowed".equals(element) && props.get(element) == null) {
        if (child(children, element) == null) {
          completion = optionalBlock(text, children, LINK_SEQUENCE, element, PARAMETER_LIST_DEFAULT);
        } else if (props.containsKey(element) && flagSet(text, children, element)) {
          // null у связи с параметром означает умолчание платформы (false), а не снятие флага
          replacement = optionalBlock(text, children, LINK_SEQUENCE, element, PARAMETER_LIST_DEFAULT);
        }
      } else if (!props.containsKey(element)) {
        continue;
      } else if (LINK_REQUIRED.contains(element)) {
        replacement = Optional.of(
          childReplacement(text, target, element, linkRequiredText(roots, element, props.get(element))));
      } else if ("required".equals(element)) {
        replacement = linkRequiredEdit(text, children, props.get(element));
      } else {
        replacement = optionalBlock(text, children, LINK_SEQUENCE, element,
          linkOptionalXml(element, props.get(element)));
      }
      replacement.ifPresent(edits::add);
      replacement.ifPresent(withCompletions::add);
      completion.ifPresent(withCompletions::add);
    }
    if (edits.isEmpty()) {
      return;
    }
    if (linkPatched(text, edits).equals(text)) {
      return;
    }
    String updated = linkPatched(text, withCompletions);
    DcsRead.verify(updated, version);
    Files.writeString(templateXml, updated, StandardCharsets.UTF_8);
  }

  /**
   * Удаляет связь наборов данных.
   *
   * @param index порядковый номер {@code dataSetLink} в файле с нуля
   * @throws IllegalArgumentException если связи с таким номером нет
   */
  public static void removeLink(Path templateXml, SchemaVersion version, int index)
    throws IOException, JAXBException {
    String text = Files.readString(templateXml, StandardCharsets.UTF_8);
    XmlLines.Node target = linkAt(XmlLines.children(text, ROOT), index);
    String updated = XmlLines.removeAll(
      text, List.of(XmlLines.wholeLines(text, target.start(), target.end(), "dataSetLink")));
    DcsRead.verify(updated, version);
    Files.writeString(templateXml, updated, StandardCharsets.UTF_8);
  }

  /**
   * Добавляет ресурс после последнего ресурса, иначе после вычисляемых полей, связей и наборов.
   *
   * <p>Группировки пишутся элементами {@code group} в порядке списка; без них ресурс считается по всем
   * группировкам. Путь к данным ресурса не уникален: одно поле может считаться разными выражениями
   * по разным группировкам.
   *
   * @param payload свойства ресурса: обязательные {@code dataPath} и {@code expression},
   *     необязательный {@code groups} - список имён группировок
   * @throws IllegalArgumentException если свойство неизвестно или обязательное не задано
   */
  public static void addTotalField(Path templateXml, SchemaVersion version, Map<String, Object> payload)
    throws IOException, JAXBException {
    if (payload == null || payload.isEmpty()) {
      throw new IllegalArgumentException("обязательное поле не задано: payload");
    }
    for (String key : payload.keySet()) {
      if (!TOTAL_FIELD_PROPS.contains(key)) {
        throw new IllegalArgumentException("Неизвестное свойство ресурса: " + key);
      }
    }
    String text = Files.readString(templateXml, StandardCharsets.UTF_8);
    String eol = XmlLines.newline(text);
    StringBuilder block = new StringBuilder("\t<totalField>").append(eol);
    block.append("\t\t<dataPath>")
      .append(DcsRead.escape(requiredPayloadString(payload.get("dataPath"), "dataPath")))
      .append("</dataPath>").append(eol);
    block.append("\t\t<expression>")
      .append(DcsRead.escape(requiredPayloadString(payload.get("expression"), "expression")))
      .append("</expression>").append(eol);
    for (String group : groupList(payload.get("groups"))) {
      block.append("\t\t").append(groupXml(group)).append(eol);
    }
    block.append("\t</totalField>").append(eol);
    XmlLines.Node anchor = null;
    for (XmlLines.Node node : XmlLines.children(text, ROOT)) {
      if (TOTAL_FIELD_PREDECESSORS.contains(node.name())) {
        anchor = node;
      }
    }
    String updated = insertBlock(text, anchor, XmlGranularPatch.lineBreaks(block.toString(), eol), eol);
    DcsRead.verify(updated, version);
    Files.writeString(templateXml, updated, StandardCharsets.UTF_8);
  }

  /**
   * Меняет подэлементы ресурса: путь к данным, выражение и список группировок.
   *
   * <p>Ключи вне {@code props} не трогаются. {@code dataPath} и {@code expression} требуют непустой
   * текст. {@code groups} заменяет все {@code group}; пустой список и {@code null} снимают их.
   *
   * @param index порядковый номер {@code totalField} в файле с нуля
   * @param props значения по именам свойств; неизвестное имя - отказ
   * @throws IllegalArgumentException если ресурса с таким номером нет, свойство неизвестно
   *     или значение не по формату
   */
  public static void setTotalField(Path templateXml, SchemaVersion version, int index, Map<String, Object> props)
    throws IOException, JAXBException {
    if (props == null || props.isEmpty()) {
      return;
    }
    for (String key : props.keySet()) {
      if (!TOTAL_FIELD_PROPS.contains(key)) {
        throw new IllegalArgumentException("Неизвестное свойство ресурса: " + key);
      }
    }
    String text = Files.readString(templateXml, StandardCharsets.UTF_8);
    XmlLines.Node target = totalFieldAt(XmlLines.children(text, ROOT), index);
    List<XmlLines.Node> children = XmlLines.childrenAt(text, target.start());
    List<XmlGranularPatch.Replacement> replacements = new ArrayList<>();
    if (props.containsKey("dataPath")) {
      replacements.add(childReplacement(text, target, "dataPath",
        requiredPayloadString(props.get("dataPath"), "dataPath")));
    }
    if (props.containsKey("expression")) {
      replacements.add(childReplacement(text, target, "expression",
        requiredPayloadString(props.get("expression"), "expression")));
    }
    if (props.containsKey("groups")) {
      groupsReplacement(text, children, groupList(props.get("groups"))).ifPresent(replacements::add);
    }
    if (replacements.isEmpty()) {
      return;
    }
    String updated = XmlGranularPatch.withoutOverlaps(replacements)
      .map(safe -> XmlGranularPatch.apply(text, safe))
      .orElseThrow(() -> new IllegalStateException("правки ресурса пересеклись"));
    if (updated.equals(text)) {
      return;
    }
    DcsRead.verify(updated, version);
    Files.writeString(templateXml, updated, StandardCharsets.UTF_8);
  }

  /**
   * Удаляет ресурс (итоговое поле).
   *
   * @param index порядковый номер {@code totalField} в файле с нуля
   * @throws IllegalArgumentException если ресурса с таким номером нет
   */
  public static void removeTotalField(Path templateXml, SchemaVersion version, int index)
    throws IOException, JAXBException {
    String text = Files.readString(templateXml, StandardCharsets.UTF_8);
    XmlLines.Node target = totalFieldAt(XmlLines.children(text, ROOT), index);
    String updated = XmlLines.removeAll(
      text, List.of(XmlLines.wholeLines(text, target.start(), target.end(), "totalField")));
    DcsRead.verify(updated, version);
    Files.writeString(templateXml, updated, StandardCharsets.UTF_8);
  }

  /**
   * Меняет путь к данным, выражение, заголовок и тип значения вычисляемого поля корня схемы.
   *
   * <p>Ключи вне {@code props} не трогаются. {@code newDataPath} и {@code expression} требуют непустой
   * текст; путь, занятый другим вычисляемым полем без учёта регистра, отклоняется. {@code title}
   * ({@code v8:LocalStringType} с языком конфигурации) и {@code valueType} (форма {@code cf-dcs-info})
   * заменяются целиком, {@code null} и пустое значение снимают элемент. Эхо прочитанного файл не трогает.
   *
   * @param dataPath путь к данным вычисляемого поля
   * @param props значения по именам свойств; неизвестное имя - отказ
   * @throws IllegalArgumentException если поля нет, свойство неизвестно или значение не по формату
   */
  public static void setCalculatedField(
    Path templateXml, SchemaVersion version, String dataPath, Map<String, Object> props)
    throws IOException, JAXBException {
    String path = required(dataPath, "dataPath");
    if (props == null || props.isEmpty()) {
      return;
    }
    for (String key : props.keySet()) {
      if (!CALCULATED_FIELD_PROPS.contains(key)) {
        throw new IllegalArgumentException("Неизвестное свойство вычисляемого поля: " + key);
      }
    }
    ConfigurationLanguage.with(templateXml, () -> {
      String text = Files.readString(templateXml, StandardCharsets.UTF_8);
      List<XmlLines.Node> roots = XmlLines.children(text, ROOT);
      XmlLines.Node target = calculatedField(roots, path);
      if (target == null) {
        throw new IllegalArgumentException("Вычисляемое поле не найдено: " + path);
      }
      List<XmlLines.Node> children = XmlLines.childrenAt(text, target.start());
      List<XmlGranularPatch.Replacement> replacements = new ArrayList<>();
      if (props.containsKey("newDataPath")) {
        String fresh = requiredPayloadString(props.get("newDataPath"), "dataPath");
        XmlLines.Node occupied = fresh.equals(path)
          ? null
          : namesake(roots, "calculatedField", "dataPath", fresh, target);
        if (occupied != null) {
          throw new IllegalArgumentException("Путь к данным «" + fresh + "» уже занят: вычисляемое поле «"
            + occupied.leaves().get("dataPath") + "» уже есть в схеме.");
        }
        replacements.add(childReplacement(text, target, "dataPath", fresh));
      }
      if (props.containsKey("expression")) {
        replacements.add(childReplacement(text, target, "expression",
          requiredPayloadString(props.get("expression"), "expression")));
      }
      if (props.containsKey("title")) {
        titleBlock(text, children, CALCULATED_FIELD_SEQUENCE, props.get("title"))
          .ifPresent(replacements::add);
      }
      if (props.containsKey("valueType")) {
        valueTypeBlock(templateXml, version, text, children, CALCULATED_FIELD_SEQUENCE, props.get("valueType"),
          CALCULATED_FIELD_DEPTH).ifPresent(replacements::add);
      }
      if (replacements.isEmpty()) {
        return null;
      }
      String updated = XmlGranularPatch.withoutOverlaps(replacements)
        .map(safe -> XmlGranularPatch.apply(text, safe))
        .orElseThrow(() -> new IllegalStateException("правки вычисляемого поля пересеклись"));
      if (updated.equals(text)) {
        return null;
      }
      DcsRead.verify(updated, version);
      Files.writeString(templateXml, updated, StandardCharsets.UTF_8);
      return null;
    });
  }

  /**
   * Удаляет вычисляемое поле корня схемы.
   *
   * @param dataPath путь к данным вычисляемого поля
   * @throws IllegalArgumentException если поля нет
   */
  public static void removeCalculatedField(Path templateXml, SchemaVersion version, String dataPath)
    throws IOException, JAXBException {
    String path = required(dataPath, "dataPath");
    String text = Files.readString(templateXml, StandardCharsets.UTF_8);
    XmlLines.Node target = calculatedField(XmlLines.children(text, ROOT), path);
    if (target == null) {
      throw new IllegalArgumentException("Вычисляемое поле не найдено: " + path);
    }
    String updated = XmlLines.removeAll(
      text, List.of(XmlLines.wholeLines(text, target.start(), target.end(), "calculatedField")));
    DcsRead.verify(updated, version);
    Files.writeString(templateXml, updated, StandardCharsets.UTF_8);
  }

  /**
   * Добавляет параметр после последнего параметра, иначе после ресурсов, вычисляемых полей, связей
   * и наборов данных.
   *
   * <p>Пишется как у платформы: заголовок {@code v8:LocalStringType} с языком конфигурации,
   * {@code use} только со значением {@code Always}, {@code value} и {@code useRestriction} всегда
   * (без значения {@code xsi:nil}, без ограничения явный {@code false}). Значение должно подходить
   * к типу {@code valueType}.
   *
   * @throws IllegalArgumentException если свойство неизвестно, имя не задано или занято без учёта
   *     регистра, значение не по формату
   */
  public static void addParameter(Path templateXml, SchemaVersion version, Map<String, Object> payload)
    throws IOException, JAXBException {
    if (payload == null || payload.isEmpty()) {
      throw new IllegalArgumentException("обязательное поле не задано: payload");
    }
    for (String key : payload.keySet()) {
      if (!ADD_PARAMETER_PAYLOAD.contains(key)) {
        throw new IllegalArgumentException("Неизвестное свойство параметра схемы: " + key);
      }
    }
    ConfigurationLanguage.with(templateXml, () -> {
      String text = Files.readString(templateXml, StandardCharsets.UTF_8);
      List<XmlLines.Node> roots = XmlLines.children(text, ROOT);
      String name = requiredPayloadString(payload.get("name"), "name");
      XmlLines.Node namesake = parameterIgnoringCase(roots, name);
      if (namesake != null) {
        throw new IllegalArgumentException("Параметр «" + name + "» уже есть в схеме: «"
          + namesake.leaves().get("name") + "».");
      }
      DcsValueTypeEdit.Edit type = DcsValueTypeEdit.added(
        templateXml, version, text, payload.get("valueType"), PARAMETER_DEPTH);
      // valueListAllowed при добавлении не задаётся, поэтому список значений недопустим
      if (DcsValueTypeEdit.isValueList(payload.get("value"))) {
        throw new IllegalArgumentException("Список значений задаётся у созданного параметра: cf-dcs-set-parameter "
          + "с valueListAllowed=true и value-списком.");
      }
      List<String> values = DcsValueTypeEdit.parameterValuesXml(payload.get("value"), false);
      DcsValueTypeEdit.checkParameterValue(templateXml, text, List.of(), type, values, false, version);
      StringBuilder block = new StringBuilder("<parameter>");
      block.append("<name>").append(DcsRead.escape(name)).append("</name>");
      for (String element : PARAMETER_SEQUENCE) {
        // value и useRestriction платформа пишет у каждого параметра
        boolean always = "value".equals(element) || "useRestriction".equals(element);
        if ("name".equals(element) || !always && !payload.containsKey(element)) {
          continue;
        }
        block.append(switch (element) {
          case "title" -> titleXml(payload.get(element));
          case "valueType" -> type.changed() ? type.xml() : "";
          case "value" -> String.join("", values);
          default -> parameterOptionalXml(element, payload.get(element));
        });
      }
      block.append("</parameter>");
      XmlLines.Node anchor = insertionAnchor(roots, PARAMETER_PREDECESSORS, "parameter");
      String indent = anchor == null ? "\t" : XmlLines.indentAt(text, anchor.start());
      String eol = XmlLines.newline(text);
      String updated = insertBlock(text, anchor, XmlGranularPatch.formatInsertion(text, indent, block.toString()) + eol, eol);
      DcsRead.verify(updated, version);
      Files.writeString(templateXml, updated, StandardCharsets.UTF_8);
      return null;
    });
  }

  /**
   * Меняет подэлементы параметра схемы по xs:sequence: замена, вставка или снятие.
   *
   * <p>Ключи вне {@code props} не трогаются, необязательные снимаются {@code null}, строковые также
   * пустым текстом; имя, занятое другим параметром без учёта регистра, отклоняется. Значение (у
   * списка каждый элемент) должно подходить к типу; список допустим только при
   * {@code valueListAllowed=true} и пишется без представлений, как хранит платформа.
   *
   * <p>Любая настоящая правка дописывает недостающие {@code value} ({@code xsi:nil}) и
   * {@code useRestriction} ({@code false}), как платформа. Эхо прочитанного файл не трогает.
   *
   * @throws IllegalArgumentException если параметра нет, свойство неизвестно или значение не по формату
   */
  public static void setParameter(Path templateXml, SchemaVersion version, String name, Map<String, Object> props)
    throws IOException, JAXBException {
    String paramName = required(name, "name");
    if (props == null || props.isEmpty()) {
      return;
    }
    for (String key : props.keySet()) {
      if (!PARAMETER_PROPS.contains(key)) {
        throw new IllegalArgumentException("Неизвестное свойство параметра схемы: " + key);
      }
    }
    ConfigurationLanguage.with(templateXml, () -> {
      String text = Files.readString(templateXml, StandardCharsets.UTF_8);
      List<XmlLines.Node> roots = XmlLines.children(text, ROOT);
      XmlLines.Node target = parameter(roots, paramName);
      if (target == null) {
        throw new IllegalArgumentException("Параметр схемы не найден: " + paramName);
      }
      List<XmlLines.Node> children = XmlLines.childrenAt(text, target.start());
      List<XmlGranularPatch.Replacement> edits = new ArrayList<>();
      List<XmlGranularPatch.Replacement> withCompletions = new ArrayList<>();
      if (props.containsKey("newName")) {
        String fresh = requiredPayloadString(props.get("newName"), "name");
        List<XmlLines.Node> others = roots.stream().filter(node -> node != target).toList();
        XmlLines.Node occupied = parameterIgnoringCase(others, fresh);
        if (occupied != null) {
          throw new IllegalArgumentException("Имя «" + fresh + "» уже занято: параметр «"
            + occupied.leaves().get("name") + "» уже есть в схеме.");
        }
        XmlGranularPatch.Replacement renamed = childReplacement(text, target, "name", fresh);
        edits.add(renamed);
        withCompletions.add(renamed);
      }
      DcsValueTypeEdit.Edit type = props.containsKey("valueType")
        ? DcsValueTypeEdit.edit(templateXml, version, text, children, props.get("valueType"), PARAMETER_DEPTH)
        : null;
      boolean writtenList = flagSet(text, children, "valueListAllowed");
      boolean listAllowed = props.containsKey("valueListAllowed")
        ? Boolean.TRUE.equals(parameterFlagValue(props.get("valueListAllowed"), "valueListAllowed"))
        : writtenList;
      List<XmlLines.Node> currentValues = children.stream().filter(node -> "value".equals(node.name())).toList();
      // Эхо значения не правит файл; при смене типа записанное значение сверяется с новым, как без value
      boolean valueEcho = props.containsKey("value") && sameParameterValue(text, children, props.get("value"));
      List<String> values = props.containsKey("value") && !valueEcho
        ? DcsValueTypeEdit.parameterValuesXml(props.get("value"), listAllowed)
        : null;
      DcsValueTypeEdit.checkParameterValue(templateXml, text, children, type, values,
        DcsValueTypeEdit.isValueList(props.get("value")), version);
      if (values == null && writtenList && !listAllowed && currentValues.size() > 1) {
        throw new IllegalArgumentException("У параметра «" + paramName + "» список из " + currentValues.size()
          + " значений: снять valueListAllowed можно, только задав в том же payload одно значение value.");
      }
      for (String element : PARAMETER_SEQUENCE) {
        Optional<XmlGranularPatch.Replacement> completion = Optional.empty();
        Optional<XmlGranularPatch.Replacement> replacement = Optional.empty();
        if ("value".equals(element)) {
          replacement = valueChange(text, children, currentValues, values, listAllowed, writtenList);
          if (values == null && !listAllowed && currentValues.isEmpty()) {
            completion = Optional.of(
              blockInsertion(text, children, PARAMETER_SEQUENCE, element, DcsValueTypeEdit.NIL_VALUE));
          }
        } else if (!props.containsKey(element)) {
          if ("useRestriction".equals(element) && child(children, element) == null) {
            completion = Optional.of(
              blockInsertion(text, children, PARAMETER_SEQUENCE, element, parameterOptionalXml(element, null)));
          }
        } else {
          replacement = switch (element) {
            case "title" -> titleBlock(text, children, PARAMETER_SEQUENCE, props.get("title"));
            case "valueType" -> type.changed()
              ? optionalBlock(text, children, PARAMETER_SEQUENCE, "valueType", type.xml())
              : Optional.empty();
            case "valueListAllowed", "availableAsField", "denyIncompleteValues" ->
              parameterFlag(text, children, element, props.get(element));
            default -> optionalBlock(text, children, PARAMETER_SEQUENCE, element,
              parameterOptionalXml(element, props.get(element)));
          };
        }
        replacement.ifPresent(edits::add);
        replacement.ifPresent(withCompletions::add);
        completion.ifPresent(withCompletions::add);
      }
      if (edits.isEmpty()) {
        return null;
      }
      if (patched(text, edits).equals(text)) {
        return null;
      }
      String updated = patched(text, withCompletions);
      DcsRead.verify(updated, version);
      Files.writeString(templateXml, updated, StandardCharsets.UTF_8);
      return null;
    });
  }

  /**
   * Удаляет параметр схемы.
   *
   * @param name имя параметра
   * @throws IllegalArgumentException если параметра нет
   */
  public static void removeParameter(Path templateXml, SchemaVersion version, String name)
    throws IOException, JAXBException {
    String paramName = required(name, "name");
    String text = Files.readString(templateXml, StandardCharsets.UTF_8);
    XmlLines.Node target = parameter(XmlLines.children(text, ROOT), paramName);
    if (target == null) {
      throw new IllegalArgumentException("Параметр схемы не найден: " + paramName);
    }
    String updated = XmlLines.removeAll(
      text, List.of(XmlLines.wholeLines(text, target.start(), target.end(), "parameter")));
    DcsRead.verify(updated, version);
    Files.writeString(templateXml, updated, StandardCharsets.UTF_8);
  }

  /** Обязательный по формату текстовый подэлемент: замена, а если его нет в файле - вставка. */
  private static XmlGranularPatch.Replacement requiredTextProp(
    String text, String kind, List<String> sequence, List<XmlLines.Node> children, String element, Object value) {
    if (!sequence.contains(element)) {
      throw new IllegalArgumentException("У набора данных вида " + kind + " нет подэлемента " + element + ".");
    }
    if (!(value instanceof String string) || string.isBlank()) {
      throw new IllegalArgumentException(
        "Подэлемент " + element + " обязателен: нужно непустое строковое значение.");
    }
    String elementXml = "<" + element + ">" + DcsRead.escape(string) + "</" + element + ">";
    XmlLines.Node current = child(children, element);
    if (current != null) {
      return new XmlGranularPatch.Replacement(current.start(), current.end(), elementXml);
    }
    return insertion(text, children, sequence, element, elementXml);
  }

  /** Автозаполнение полей запроса: false пишется элементом, true и null - снятием элемента. */
  private static Optional<XmlGranularPatch.Replacement> autoFillFields(
    String text, String kind, List<String> sequence, List<XmlLines.Node> children, Object value) {
    if (!sequence.contains("autoFillFields")) {
      throw new IllegalArgumentException("У набора данных вида " + kind + " нет подэлемента autoFillFields.");
    }
    if (value != null && !(value instanceof Boolean)) {
      throw new IllegalArgumentException("Значение autoFillFields должно быть булевым.");
    }
    XmlLines.Node current = child(children, "autoFillFields");
    if (!Boolean.FALSE.equals(value)) {
      if (current == null) {
        return Optional.empty();
      }
      int[] span = XmlLines.wholeLines(text, current.start(), current.end(), "autoFillFields");
      return Optional.of(new XmlGranularPatch.Replacement(span[0], span[1], ""));
    }
    String elementXml = "<autoFillFields>false</autoFillFields>";
    if (current != null) {
      return Optional.of(new XmlGranularPatch.Replacement(current.start(), current.end(), elementXml));
    }
    return Optional.of(insertion(text, children, sequence, "autoFillFields", elementXml));
  }

  /** Вставка подэлемента на место по xs:sequence: отдельной строкой после последнего предшественника. */
  private static XmlGranularPatch.Replacement insertion(
    String text, List<XmlLines.Node> children, List<String> sequence, String element, String elementXml) {
    XmlLines.Node anchor = insertionAnchor(children, sequence, element);
    if (anchor == null) {
      throw new IllegalStateException("у набора данных нет подэлемента name: файл не похож на выгрузку платформы");
    }
    String eol = XmlLines.newline(text);
    String indent = XmlLines.indentAt(text, anchor.start());
    int lineEnd = text.indexOf('\n', anchor.end());
    if (lineEnd < 0) {
      return new XmlGranularPatch.Replacement(anchor.end(), anchor.end(), eol + indent + elementXml);
    }
    return new XmlGranularPatch.Replacement(lineEnd + 1, lineEnd + 1, indent + elementXml + eol);
  }

  /** Последний подэлемент, который по xs:sequence не позже вставляемого; после него и вставлять. */
  private static XmlLines.Node insertionAnchor(List<XmlLines.Node> children, List<String> sequence, String element) {
    int position = sequence.indexOf(element);
    XmlLines.Node anchor = null;
    for (XmlLines.Node child : children) {
      int index = sequence.indexOf(child.name());
      if (index >= 0 && index <= position) {
        anchor = child;
      }
    }
    return anchor;
  }

  /** Вставка многострочного подэлемента на место по xs:sequence: блок строк с отступом якоря. */
  private static XmlGranularPatch.Replacement blockInsertion(
    String text, List<XmlLines.Node> children, List<String> sequence, String element, String compactXml) {
    XmlLines.Node anchor = insertionAnchor(children, sequence, element);
    if (anchor == null) {
      throw new IllegalStateException(
        "перед " + element + " нет ни одного подэлемента формата: файл не похож на выгрузку платформы");
    }
    String eol = XmlLines.newline(text);
    String block = XmlGranularPatch.formatInsertion(text, XmlLines.indentAt(text, anchor.start()), compactXml);
    int lineEnd = text.indexOf('\n', anchor.end());
    if (lineEnd < 0) {
      return new XmlGranularPatch.Replacement(anchor.end(), anchor.end(), eol + block);
    }
    return new XmlGranularPatch.Replacement(lineEnd + 1, lineEnd + 1, block + eol);
  }

  /** Необязательный подэлемент: замена целиком, вставка по xs:sequence либо снятие пустого. */
  private static Optional<XmlGranularPatch.Replacement> optionalBlock(
    String text, List<XmlLines.Node> children, List<String> sequence, String element, String blockXml) {
    XmlLines.Node current = child(children, element);
    if (blockXml.isEmpty()) {
      if (current == null) {
        return Optional.empty();
      }
      int[] span = XmlLines.wholeLines(text, current.start(), current.end(), element);
      return Optional.of(new XmlGranularPatch.Replacement(span[0], span[1], ""));
    }
    if (current != null) {
      return Optional.of(new XmlGranularPatch.Replacement(current.start(), current.end(),
        XmlGranularPatch.formatReplacementPreservingIndent(text, current.start(), blockXml)));
    }
    return Optional.of(blockInsertion(text, children, sequence, element, blockXml));
  }

  /**
   * Тип значения владельца: правка по {@link DcsValueTypeEdit}; эхо прочитанного типа - без правки.
   *
   * @param ownerDepth глубина владельца от корня схемы: по ней префикс ссылочного типа
   */
  private static Optional<XmlGranularPatch.Replacement> valueTypeBlock(Path templateXml, SchemaVersion version,
    String text, List<XmlLines.Node> children, List<String> sequence, Object payload, int ownerDepth)
    throws IOException, JAXBException {
    DcsValueTypeEdit.Edit edit = DcsValueTypeEdit.edit(templateXml, version, text, children, payload, ownerDepth);
    return edit.changed() ? optionalBlock(text, children, sequence, "valueType", edit.xml()) : Optional.empty();
  }

  /**
   * Булев признак параметра: умолчание ({@link #PARAMETER_FLAG_DEFAULTS}) и {@code null} снимают элемент,
   * иное значение пишется. Значение, совпадающее с файлом, файл не трогает, даже записанное явно умолчание.
   */
  private static Optional<XmlGranularPatch.Replacement> parameterFlag(
    String text, List<XmlLines.Node> children, String element, Object value) {
    if (value != null && !(value instanceof Boolean)) {
      throw new IllegalArgumentException("Значение " + element + " параметра должно быть булевым.");
    }
    boolean byDefault = PARAMETER_FLAG_DEFAULTS.get(element);
    XmlLines.Node current = child(children, element);
    if (value != null && current != null) {
      boolean written = TRUE_FLAG.matcher(text.substring(current.start(), current.end())).matches();
      if (written == (Boolean) value) {
        return Optional.empty();
      }
    }
    boolean wanted = value == null ? byDefault : (Boolean) value;
    return optionalBlock(text, children, PARAMETER_SEQUENCE, element,
      wanted == byDefault ? "" : "<" + element + ">" + wanted + "</" + element + ">");
  }

  /** Булев признак параметра из payload; {@code null}, если не задан (значение по умолчанию). */
  private static Boolean parameterFlagValue(Object value, String element) {
    if (value != null && !(value instanceof Boolean)) {
      throw new IllegalArgumentException("Значение " + element + " параметра должно быть булевым.");
    }
    return (Boolean) value;
  }

  /** Признак записан в файле взведённым. */
  private static boolean flagSet(String text, List<XmlLines.Node> children, String element) {
    XmlLines.Node current = child(children, element);
    return current != null && TRUE_FLAG.matcher(text.substring(current.start(), current.end())).matches();
  }

  /**
   * Правка значений параметра: зона подряд стоящих {@code value} заменяется целиком ({@link #valuesBlock}),
   * совпавшие байты файл не трогают. Без {@code value} в payload меняется одно: включение списка снимает
   * {@code xsi:nil}, пустой список платформа хранит без {@code value}.
   *
   * @param current элементы {@code value} в файле
   * @param values новые элементы; {@code null}, если значения в payload нет
   */
  private static Optional<XmlGranularPatch.Replacement> valueChange(String text, List<XmlLines.Node> children,
    List<XmlLines.Node> current, List<String> values, boolean listAllowed, boolean writtenList) {
    List<String> fresh = values;
    if (fresh == null) {
      boolean listTurnedOn = listAllowed && !writtenList;
      if (!listTurnedOn || current.size() != 1 || !XSI_NIL.matcher(startTag(text, current.get(0))).find()) {
        return Optional.empty();
      }
      fresh = List.of();
    }
    return valuesBlock(text, children, current, fresh).filter(change -> !unchanged(text, change));
  }

  /**
   * Зона элементов {@code value}: замена от первого до последнего целиком с раскладкой по месту,
   * вставка по xs:sequence, если значений не было, либо снятие целыми строками.
   */
  private static Optional<XmlGranularPatch.Replacement> valuesBlock(
    String text, List<XmlLines.Node> children, List<XmlLines.Node> current, List<String> values) {
    String compact = String.join("", values);
    if (current.isEmpty()) {
      return compact.isEmpty()
        ? Optional.empty()
        : Optional.of(blockInsertion(text, children, PARAMETER_SEQUENCE, "value", compact));
    }
    XmlLines.Node first = current.get(0);
    XmlLines.Node last = current.get(current.size() - 1);
    if (compact.isEmpty()) {
      int[] span = XmlLines.wholeLines(text, first.start(), last.end(), "value");
      return Optional.of(new XmlGranularPatch.Replacement(span[0], span[1], ""));
    }
    return Optional.of(new XmlGranularPatch.Replacement(first.start(), last.end(),
      XmlGranularPatch.formatReplacementPreservingIndent(text, first.start(), compact)));
  }

  private static String startTag(String text, XmlLines.Node node) {
    return text.substring(node.start(), XmlLines.startTagEnd(text, node.start()) + 1);
  }

  /** Текст после правок; пересечение правок означает ошибку писателя. */
  private static String patched(String text, List<XmlGranularPatch.Replacement> replacements) {
    return XmlGranularPatch.withoutOverlaps(replacements)
      .map(safe -> XmlGranularPatch.apply(text, safe))
      .orElseThrow(() -> new IllegalStateException("правки параметра схемы пересеклись"));
  }

  /** Правка, которая кладёт на место те же байты: эхо значения, файл не трогается. */
  private static boolean unchanged(String text, XmlGranularPatch.Replacement change) {
    return change.start() != change.end() && text.substring(change.start(), change.end()).equals(change.text());
  }

  /**
   * Объединение, в которое вложен набор с этим именем (на любой глубине); {@code null} - такого
   * вложенного набора нет.
   */
  private static String enclosingUnion(String text, List<XmlLines.Node> nodes, String name) {
    for (XmlLines.Node node : nodes) {
      if (!"dataSet".equals(node.name()) && !"item".equals(node.name())) {
        continue;
      }
      for (XmlLines.Node child : XmlLines.childrenAt(text, node.start())) {
        if (!"item".equals(child.name())) {
          continue;
        }
        if (name.equals(child.leaves().get("name"))) {
          return node.leaves().get("name");
        }
        String deeper = enclosingUnion(text, List.of(child), name);
        if (deeper != null) {
          return deeper;
        }
      }
    }
    return null;
  }

  /**
   * Обычное поле набора данных по пути к данным.
   *
   * @throws IllegalArgumentException если поля нет либо это папка или вложенный набор
   */
  private static XmlLines.Node plainField(String text, List<XmlLines.Node> setChildren, String dataPath) {
    XmlLines.Node field = fieldByPath(setChildren, dataPath);
    if (field == null) {
      throw new IllegalArgumentException("Поле набора данных не найдено: " + dataPath);
    }
    switch (fieldKind(text, field)) {
      case "folder" -> throw new IllegalArgumentException(
        "Поле " + dataPath + " - папка полей: правка папок пока не поддержана.");
      case "nested" -> throw new IllegalArgumentException(
        "Поле " + dataPath + " - вложенный набор данных: правка таких полей пока не поддержана.");
      default -> {
      }
    }
    return field;
  }

  /** Прямое поле набора по пути к данным; поля вложенных наборов объединения не ищутся. */
  private static XmlLines.Node fieldByPath(List<XmlLines.Node> setChildren, String dataPath) {
    for (XmlLines.Node node : setChildren) {
      if ("field".equals(node.name()) && dataPath.equals(node.leaves().get("dataPath"))) {
        return node;
      }
    }
    return null;
  }

  /** Вид поля набора по xsi:type открывающего тега: field, folder или nested. */
  private static String fieldKind(String text, XmlLines.Node field) {
    String tag = text.substring(field.start(), XmlLines.startTagEnd(text, field.start()) + 1);
    Matcher type = XSI_TYPE.matcher(tag);
    String value = type.find() ? type.group(1) : "";
    return switch (value.substring(value.indexOf(':') + 1)) {
      case "DataSetFieldField" -> "field";
      case "DataSetFieldFolder" -> "folder";
      case "DataSetFieldNestedDataSet" -> "nested";
      default -> throw new IllegalArgumentException("Неизвестный вид поля набора данных: " + value);
    };
  }

  /**
   * Заголовок владельца: замена, вставка по xs:sequence либо снятие пустого.
   *
   * <p>Эхо прочитанного заголовка файл не трогает: запись потеряла бы строки на других языках, форму
   * {@code xs:string} и заголовок из одного пробела (так платформа прячет подпись колонки).
   */
  private static Optional<XmlGranularPatch.Replacement> titleBlock(
    String text, List<XmlLines.Node> children, List<String> sequence, Object value) {
    XmlLines.Node current = child(children, "title");
    if (current != null && value instanceof String title && !title.isEmpty()
      && sameText(DcsSettingsRead.localizedText(text.substring(current.start(), current.end()),
        DcsSettingsEdit.rootNamespaces(text)), title)) {
      return Optional.empty();
    }
    return optionalBlock(text, children, sequence, "title", titleXml(value));
  }

  /** Заголовок как пишет платформа: v8:LocalStringType с языком конфигурации; пусто - без заголовка. */
  private static String titleXml(Object value) {
    if (value == null) {
      return "";
    }
    if (!(value instanceof String title)) {
      throw new IllegalArgumentException("Значение title должно быть строкой.");
    }
    if (title.isBlank()) {
      return "";
    }
    return "<title xsi:type=\"v8:LocalStringType\"><v8:item><v8:lang>" + ConfigurationLanguage.current()
      + "</v8:lang><v8:content>" + DcsRead.escape(title) + "</v8:content></v8:item></title>";
  }

  /** Ограничение использования: пишутся только взведённые флаги; без единого взведённого - пусто. */
  private static String useRestrictionXml(String element, Object value) {
    if (value == null) {
      return "";
    }
    if (!(value instanceof Map<?, ?> flags)) {
      throw new IllegalArgumentException("Значение " + element + " должно быть объектом с флагами "
        + String.join(", ", USE_RESTRICTION_FLAGS) + ".");
    }
    for (Object key : flags.keySet()) {
      if (!USE_RESTRICTION_FLAGS.contains(String.valueOf(key))) {
        throw new IllegalArgumentException("Неизвестный флаг ограничения использования: " + key);
      }
    }
    StringBuilder children = new StringBuilder();
    for (String flag : USE_RESTRICTION_FLAGS) {
      Object raw = flags.get(flag);
      if (raw == null) {
        continue;
      }
      if (!(raw instanceof Boolean set)) {
        throw new IllegalArgumentException("Значение флага " + flag + " должно быть булевым.");
      }
      if (set) {
        children.append('<').append(flag).append(">true</").append(flag).append('>');
      }
    }
    return children.isEmpty() ? "" : "<" + element + ">" + children + "</" + element + ">";
  }

  /**
   * Роль поля: дети в пространстве dcscom по xs:sequence; без единого значения - пусто. При взведённом
   * {@code balance} пустое имя группы остатка пишется {@code <dcscom:balanceGroupName/>}, как платформа.
   */
  private static String roleXml(Object value, SchemaVersion version) {
    if (value == null) {
      return "";
    }
    if (!(value instanceof Map<?, ?> role)) {
      throw new IllegalArgumentException("Значение role должно быть объектом со свойствами роли поля.");
    }
    for (Object key : role.keySet()) {
      if (!ROLE_SEQUENCE.contains(String.valueOf(key))) {
        throw new IllegalArgumentException("Неизвестное свойство роли поля: " + key);
      }
    }
    StringBuilder children = new StringBuilder();
    boolean balance = false;
    for (String name : ROLE_SEQUENCE) {
      String text = roleChildText(name, role.get(name));
      if ("balance".equals(name)) {
        balance = text != null;
      }
      if (text != null) {
        if ("isMain".equals(name) && version.compareTo(SchemaVersion.V2_21) < 0) {
          throw new IllegalArgumentException("Признак «Основное поле» (isMain) появился в формате 2.21. Формат файла: "
            + version.metadataObjectVersionAttribute() + ".");
        }
        children.append("<dcscom:").append(name).append('>').append(text)
          .append("</dcscom:").append(name).append('>');
      } else if (balance && "balanceGroupName".equals(name)) {
        children.append("<dcscom:balanceGroupName/>");
      }
    }
    return children.isEmpty() ? "" : "<role>" + children + "</role>";
  }

  /**
   * Пустой {@code <dcscom:balanceGroupName/>} строкой после взведённого {@code balance} без имени группы,
   * как дописывает платформа. Если {@code balance} делит строку с соседями, роль не дополняется.
   */
  private static Optional<XmlGranularPatch.Replacement> balanceGroupNameCompletion(
    String text, List<XmlLines.Node> children) {
    XmlLines.Node role = child(children, "role");
    if (role == null || !"true".equals(role.leaves().getOrDefault("balance", "").strip())
      || role.leaves().containsKey("balanceGroupName")) {
      return Optional.empty();
    }
    List<XmlLines.Node> roleChildren = XmlLines.childrenAt(text, role.start());
    XmlLines.Node balance = child(roleChildren, "balance");
    int lineEnd = text.indexOf('\n', balance.end());
    if (lineEnd < 0 || !text.substring(balance.end(), lineEnd).isBlank()) {
      return Optional.empty();
    }
    return Optional.of(insertion(text, roleChildren, ROLE_SEQUENCE, "balanceGroupName", "<dcscom:balanceGroupName/>"));
  }

  /** Текст поля после правок; пересечение правок означает ошибку писателя. */
  private static String fieldPatched(String text, List<XmlGranularPatch.Replacement> replacements) {
    return XmlGranularPatch.withoutOverlaps(replacements)
      .map(safe -> XmlGranularPatch.apply(text, safe))
      .orElseThrow(() -> new IllegalStateException("правки поля набора данных пересеклись"));
  }

  /** Текст подэлемента роли; {@code null} - подэлемент не пишется (значение по умолчанию). */
  private static String roleChildText(String name, Object value) {
    if (value == null) {
      return null;
    }
    if (ROLE_FLAGS.contains(name)) {
      if (!(value instanceof Boolean set)) {
        throw new IllegalArgumentException("Значение " + name + " роли должно быть булевым.");
      }
      return set ? "true" : null;
    }
    if (ROLE_STRINGS.contains(name)) {
      if (!(value instanceof String string)) {
        throw new IllegalArgumentException("Значение " + name + " роли должно быть строкой.");
      }
      return string.isBlank() ? null : DcsRead.escape(string);
    }
    if ("periodNumber".equals(name)) {
      if (!(value instanceof Number number) || number.longValue() != number.doubleValue()) {
        throw new IllegalArgumentException("Значение periodNumber роли должно быть целым числом.");
      }
      return String.valueOf(number.longValue());
    }
    List<String> allowed = ROLE_ENUMS.get(name);
    if (!(value instanceof String string) || !allowed.contains(string)) {
      throw new IllegalArgumentException(
        "Значение " + name + " роли должно быть одним из: " + String.join(", ", allowed) + ".");
    }
    return string;
  }

  /** Обязательный по формату текст из payload: нужна непустая строка. */
  private static String requiredPayloadString(Object value, String element) {
    if (!(value instanceof String string) || string.isBlank()) {
      throw new IllegalArgumentException(
        "Подэлемент " + element + " обязателен: нужно непустое строковое значение.");
    }
    return string;
  }

  /**
   * Замена первого подэлемента с данным именем целиком: {@code <x>значение</x>}. Значение, равное
   * записанному с точностью до переводов строк, оставляет прежние байты (CRLF и экранирование
   * конфигуратора); новое пишется с переводом строки файла.
   */
  private static XmlGranularPatch.Replacement childReplacement(
    String text, XmlLines.Node owner, String element, String value) {
    XmlLines.Node child = child(XmlLines.childrenAt(text, owner.start()), element);
    if (child == null) {
      throw new IllegalStateException("у элемента " + owner.name() + " нет подэлемента " + element);
    }
    String recorded = owner.leaves().get(element);
    if (recorded != null && sameText(recorded, value)) {
      return new XmlGranularPatch.Replacement(child.start(), child.end(), text.substring(child.start(), child.end()));
    }
    return new XmlGranularPatch.Replacement(child.start(), child.end(), "<" + element + ">"
      + XmlGranularPatch.lineBreaks(DcsRead.escape(value), XmlLines.newline(text)) + "</" + element + ">");
  }

  /** Тексты равны с точностью до вида перевода строки: XML-разбор приводит их к LF. */
  static boolean sameText(String a, String b) {
    return XmlGranularPatch.lineBreaks(a, "\n").equals(XmlGranularPatch.lineBreaks(b, "\n"));
  }

  /**
   * Первый узел {@code element}, у которого простой подэлемент {@code key} совпадает с {@code name}
   * без учёта регистра (язык выражений схемы регистр не различает); {@code except} не считается.
   */
  private static XmlLines.Node namesake(
    List<XmlLines.Node> nodes, String element, String key, String name, XmlLines.Node except) {
    String wanted = name.toUpperCase(Locale.ROOT);
    for (XmlLines.Node node : nodes) {
      String existing = element.equals(node.name()) && node != except ? node.leaves().get(key) : null;
      if (existing != null && existing.toUpperCase(Locale.ROOT).equals(wanted)) {
        return node;
      }
    }
    return null;
  }

  /** Набор данных верхнего уровня по имени; вложенные наборы объединений не ищутся. */
  private static XmlLines.Node dataSet(List<XmlLines.Node> roots, String name) {
    for (XmlLines.Node node : roots) {
      if ("dataSet".equals(node.name()) && name.equals(node.leaves().get("name"))) {
        return node;
      }
    }
    return null;
  }

  /** Связь наборов ссылается на набор как на источник или приёмник. */
  private static boolean linkReferences(XmlLines.Node link, String name) {
    return name.equals(link.leaves().get("sourceDataSet"))
      || name.equals(link.leaves().get("destinationDataSet"));
  }

  /** Связь наборов по порядковому номеру dataSetLink в файле с нуля. */
  private static XmlLines.Node linkAt(List<XmlLines.Node> roots, int index) {
    List<XmlLines.Node> links = roots.stream()
      .filter(node -> "dataSetLink".equals(node.name()))
      .toList();
    if (index < 0 || index >= links.size()) {
      throw new IllegalArgumentException(
        "Связь наборов данных не найдена: номер " + index + " при " + links.size() + " связях в схеме.");
    }
    return links.get(index);
  }

  /** Текст связи наборов после правок; пересечение правок означает ошибку писателя. */
  private static String linkPatched(String text, List<XmlGranularPatch.Replacement> replacements) {
    return XmlGranularPatch.withoutOverlaps(replacements)
      .map(safe -> XmlGranularPatch.apply(text, safe))
      .orElseThrow(() -> new IllegalStateException("правки связи наборов данных пересеклись"));
  }

  /** Ресурс по порядковому номеру totalField в файле с нуля. */
  private static XmlLines.Node totalFieldAt(List<XmlLines.Node> roots, int index) {
    List<XmlLines.Node> totals = roots.stream()
      .filter(node -> "totalField".equals(node.name()))
      .toList();
    if (index < 0 || index >= totals.size()) {
      throw new IllegalArgumentException(
        "Ресурс не найден: номер " + index + " при " + totals.size() + " ресурсах в схеме.");
    }
    return totals.get(index);
  }

  /** Вычисляемое поле корня по пути к данным; вложенные схемы не ищутся. */
  private static XmlLines.Node calculatedField(List<XmlLines.Node> roots, String dataPath) {
    for (XmlLines.Node node : roots) {
      if ("calculatedField".equals(node.name()) && dataPath.equals(node.leaves().get("dataPath"))) {
        return node;
      }
    }
    return null;
  }

  /** Параметр схемы верхнего уровня по имени; параметры вложенных схем не ищутся. */
  private static XmlLines.Node parameter(List<XmlLines.Node> roots, String name) {
    for (XmlLines.Node node : roots) {
      if ("parameter".equals(node.name()) && name.equals(node.leaves().get("name"))) {
        return node;
      }
    }
    return null;
  }

  /** Параметр корня с тем же именем без учёта регистра: {@code &Размер} и {@code &размер} один параметр. */
  private static XmlLines.Node parameterIgnoringCase(List<XmlLines.Node> roots, String name) {
    String key = name.toUpperCase(Locale.ROOT);
    for (XmlLines.Node node : roots) {
      String existing = "parameter".equals(node.name()) ? node.leaves().get("name") : null;
      if (existing != null && existing.toUpperCase(Locale.ROOT).equals(key)) {
        return node;
      }
    }
    return null;
  }

  /**
   * Значение из payload совпадает по смыслу с прочитанным ({@link DcsRead#parameterValueTyped}): это эхо,
   * файл не трогается, даже если писатель дал бы другие байты. Ключи с {@code null} не считаются, числа
   * сравниваются по величине. Значения только для чтения ({@code opaque}, {@code attrs}) эхом не считаются.
   */
  private static boolean sameParameterValue(String text, List<XmlLines.Node> children, Object requested) {
    if (child(children, "value") == null || !(requested instanceof Map<?, ?> value)
      || Boolean.TRUE.equals(value.get("opaque")) || value.containsKey("attrs")) {
      return false;
    }
    Map<String, Object> read = DcsRead.parameterValueTyped(text, children, DcsSettingsEdit.rootNamespaces(text));
    return DcsSettingsEdit.normalized(value).equals(DcsSettingsEdit.normalized(read));
  }

  /** Список группировок ресурса: непустые строки; {@code null} - пустой список. */
  private static List<String> groupList(Object value) {
    if (value == null) {
      return List.of();
    }
    if (!(value instanceof List<?> raw)) {
      throw new IllegalArgumentException("Значение groups должно быть списком строк.");
    }
    List<String> out = new ArrayList<>();
    for (Object item : raw) {
      if (!(item instanceof String group) || group.isBlank()) {
        throw new IllegalArgumentException("Группировка ресурса должна быть непустой строкой.");
      }
      out.add(group);
    }
    return out;
  }

  private static String groupXml(String group) {
    return "<group>" + DcsRead.escape(group) + "</group>";
  }

  /**
   * Группировки ресурса: элементы {@code group} заменяются целиком списком; пустой список
   * снимает их все, а в ресурс без группировок список встаёт после выражения по xs:sequence.
   */
  private static Optional<XmlGranularPatch.Replacement> groupsReplacement(
    String text, List<XmlLines.Node> children, List<String> groups) {
    List<XmlLines.Node> current = children.stream()
      .filter(node -> "group".equals(node.name()))
      .toList();
    String eol = XmlLines.newline(text);
    if (current.isEmpty()) {
      if (groups.isEmpty()) {
        return Optional.empty();
      }
      XmlLines.Node anchor = insertionAnchor(children, TOTAL_FIELD_SEQUENCE, "group");
      if (anchor == null) {
        throw new IllegalStateException(
          "у ресурса нет подэлементов dataPath и expression: файл не похож на выгрузку платформы");
      }
      String indent = XmlLines.indentAt(text, anchor.start());
      StringBuilder block = new StringBuilder();
      for (String group : groups) {
        block.append(indent).append(groupXml(group)).append(eol);
      }
      int lineEnd = text.indexOf('\n', anchor.end());
      if (lineEnd < 0) {
        return Optional.of(new XmlGranularPatch.Replacement(
          anchor.end(), anchor.end(), eol + block.substring(0, block.length() - eol.length())));
      }
      return Optional.of(new XmlGranularPatch.Replacement(lineEnd + 1, lineEnd + 1, block.toString()));
    }
    // по xs:sequence группировки стоят подряд: зона от первой до последней заменяется целиком
    int[] span = XmlLines.wholeLines(
      text, current.get(0).start(), current.get(current.size() - 1).end(), "group");
    if (groups.isEmpty()) {
      return Optional.of(new XmlGranularPatch.Replacement(span[0], span[1], ""));
    }
    String indent = XmlLines.indentAt(text, current.get(0).start());
    StringBuilder block = new StringBuilder();
    for (String group : groups) {
      block.append(indent).append(groupXml(group)).append(eol);
    }
    return Optional.of(new XmlGranularPatch.Replacement(span[0], span[1], block.toString()));
  }

  /**
   * Обязательный текст подэлемента связи; у {@code sourceDataSet} и {@code destinationDataSet}
   * названный набор данных должен существовать на верхнем уровне схемы.
   */
  private static String linkRequiredText(List<XmlLines.Node> roots, String element, Object value) {
    String string = requiredPayloadString(value, element);
    if (("sourceDataSet".equals(element) || "destinationDataSet".equals(element))
      && dataSet(roots, string) == null) {
      throw new IllegalArgumentException("Набор данных не найден: " + string);
    }
    return string;
  }

  /**
   * Необязательный подэлемент связи: булев пишется как задан, {@code null} и пустой текст снимают элемент.
   * {@code required=true} отклоняется, {@code false} не пишется. Флаг списка приходит сюда только заданным
   * и только у связи с параметром.
   */
  private static String linkOptionalXml(String element, Object value) {
    if (value == null) {
      return "";
    }
    if (LINK_FLAGS.contains(element)) {
      if (!(value instanceof Boolean flag)) {
        throw new IllegalArgumentException("Значение " + element + " связи должно быть булевым.");
      }
      if ("required".equals(element)) {
        if (flag) {
          throw new IllegalArgumentException(LINK_REQUIRED_REFUSAL);
        }
        return "";
      }
      return "<" + element + ">" + flag + "</" + element + ">";
    }
    if (!(value instanceof String string)) {
      throw new IllegalArgumentException("Значение " + element + " связи должно быть строкой.");
    }
    return string.isBlank() ? "" : "<" + element + ">" + DcsRead.escape(string) + "</" + element + ">";
  }

  /**
   * Обязательность существующей связи: {@code true} отклоняется, кроме эха уже записанного {@code true}.
   * {@code false} и {@code null} снимают элемент, но записанный {@code false} при эхе остаётся.
   */
  private static Optional<XmlGranularPatch.Replacement> linkRequiredEdit(
    String text, List<XmlLines.Node> children, Object value) {
    if (value != null && !(value instanceof Boolean)) {
      throw new IllegalArgumentException("Значение required связи должно быть булевым.");
    }
    XmlLines.Node current = child(children, "required");
    boolean written = current != null
      && TRUE_FLAG.matcher(text.substring(current.start(), current.end())).matches();
    if (Boolean.TRUE.equals(value)) {
      if (written) {
        return Optional.empty();
      }
      throw new IllegalArgumentException(LINK_REQUIRED_REFUSAL);
    }
    if (Boolean.FALSE.equals(value) && current != null && !written) {
      return Optional.empty();
    }
    return optionalBlock(text, children, LINK_SEQUENCE, "required", "");
  }

  /** Флаг списка связи из payload: {@code null} - не взведён; не булево значение - отказ. */
  private static boolean linkListFlag(Object value) {
    if (value != null && !(value instanceof Boolean)) {
      throw new IllegalArgumentException("Значение parameterListAllowed связи должно быть булевым.");
    }
    return Boolean.TRUE.equals(value);
  }

  /**
   * Флаг списка связи, у которой после правки нет параметра (платформа такой флаг не хранит):
   * <ul>
   *   <li>снятие параметра снимает и флаг, {@code true} в том же payload отклоняется;</li>
   *   <li>{@code true} отклоняется, кроме эха уже записанного {@code true};</li>
   *   <li>{@code false} и {@code null} снимают элемент, но записанный {@code false} при эхе остаётся;</li>
   *   <li>без флага и параметра в payload записанный флаг не трогается.</li>
   * </ul>
   */
  private static Optional<XmlGranularPatch.Replacement> linkListFlagEdit(
    String text, List<XmlLines.Node> children, Map<String, Object> props, boolean parameterRemoved) {
    String element = "parameterListAllowed";
    boolean listFlag = linkListFlag(props.get(element));
    XmlLines.Node current = child(children, element);
    boolean written = flagSet(text, children, element);
    if (listFlag) {
      if (written && !parameterRemoved) {
        return Optional.empty();
      }
      throw new IllegalArgumentException(LINK_LIST_FLAG_REFUSAL);
    }
    if (parameterRemoved) {
      return optionalBlock(text, children, LINK_SEQUENCE, element, "");
    }
    if (!props.containsKey(element)
      || (Boolean.FALSE.equals(props.get(element)) && current != null && !written)) {
      return Optional.empty();
    }
    return optionalBlock(text, children, LINK_SEQUENCE, element, "");
  }

  /**
   * Прочий подэлемент параметра: {@code useRestriction} пишется всегда ({@code null} даёт {@code false}),
   * {@code use=Auto}, пустая строка и {@code null} снимают элемент.
   */
  private static String parameterOptionalXml(String element, Object value) {
    if ("useRestriction".equals(element)) {
      if (value != null && !(value instanceof Boolean)) {
        throw new IllegalArgumentException("Значение useRestriction параметра должно быть булевым.");
      }
      return "<useRestriction>" + Boolean.TRUE.equals(value) + "</useRestriction>";
    }
    if (value == null) {
      return "";
    }
    if ("use".equals(element)) {
      if (!(value instanceof String use) || !PARAMETER_USE.contains(use)) {
        throw new IllegalArgumentException(
          "Значение use параметра должно быть одним из: " + String.join(", ", PARAMETER_USE) + ".");
      }
      // Auto: значение по умолчанию, платформа его не пишет
      return "Auto".equals(use) ? "" : "<use>" + use + "</use>";
    }
    if (!(value instanceof String string)) {
      throw new IllegalArgumentException("Значение " + element + " параметра должно быть строкой.");
    }
    return string.isBlank() ? "" : "<" + element + ">" + DcsRead.escape(string) + "</" + element + ">";
  }

  /** Первый прямой потомок с данным локальным именем. */
  private static XmlLines.Node child(List<XmlLines.Node> children, String element) {
    for (XmlLines.Node node : children) {
      if (element.equals(node.name())) {
        return node;
      }
    }
    return null;
  }

  private static String required(String value, String field) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("обязательное поле не задано: " + field);
    }
    return value;
  }
}
