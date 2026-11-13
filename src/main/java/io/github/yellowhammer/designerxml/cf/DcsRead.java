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
import io.github.yellowhammer.designerxml.reflect.JaxbReflect;

import jakarta.xml.bind.JAXBElement;
import jakarta.xml.bind.JAXBException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Структура схемы компоновки данных: наборы с полями и запросами, вычисляемые
 * и итоговые поля, параметры. Чтение идёт JAXB-моделью схемы dcs.
 */
public final class DcsRead {

  private static final String VALUE_LIST = "v8:ValueListType";

  /** Булев элемент, взведённый лексемой xs:boolean. */
  private static final Pattern TRUE_TEXT = Pattern.compile(">\\s*(?:true|1)\\s*<");

  private DcsRead() {
  }

  /**
   * Структура схемы для JSON-ответа.
   *
   * <p>Последним идёт {@code typeCompositions}: составы определяемых типов и характеристик,
   * встреченных в {@code valueType} полей наборов (и вложенных наборов объединений), вычисляемых
   * полей и параметров. Ключа нет, если таких типов нет, схема вне выгрузки или описания не найдены.
   */
  public static Map<String, Object> info(Path templateXml, SchemaVersion version)
    throws IOException, JAXBException {
    Object schema = schemaRoot(templateXml, version);
    DcsValueTypes.Context types = DcsValueTypes.Context.of(templateXml);
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("dataSets", dataSets(schema, types));
    out.put("calculatedFields", calculatedFields(schema, types));
    out.put("totalFields", totalFields(schema));
    out.put("parameters", parameters(schema, types, typedValues(templateXml)));
    out.put("dataSources", dataSources(schema));
    out.put("dataSetLinks", dataSetLinks(schema));
    out.put("nestedSchemas", namedList(schema, "getNestedSchema", "getName", "getTitle"));
    out.put("settingsVariants", namedList(schema, "getSettingsVariant", "getName", "getPresentation"));
    List<Map<String, Object>> compositions = types.typeCompositions();
    if (!compositions.isEmpty()) {
      out.put("typeCompositions", compositions);
    }
    return out;
  }

  /** Источники данных схемы: имя, тип и строка соединения. */
  private static List<Map<String, Object>> dataSources(Object schema) {
    List<Map<String, Object>> out = new ArrayList<>();
    for (Object source : JaxbReflect.<Object>listOptional(schema, "getDataSource")) {
      Map<String, Object> row = new LinkedHashMap<>();
      row.put("name", text(source, "getName"));
      String type = text(source, "getDataSourceType");
      if (!type.isEmpty()) {
        row.put("type", type);
      }
      String connection = text(source, "getConnectionString");
      if (!connection.isEmpty()) {
        row.put("connectionString", connection);
      }
      out.add(row);
    }
    return out;
  }

  /** Связи наборов данных: источник и приёмник с выражениями и параметрами связи. */
  private static List<Map<String, Object>> dataSetLinks(Object schema) {
    List<Map<String, Object>> out = new ArrayList<>();
    for (Object link : JaxbReflect.<Object>listOptional(schema, "getDataSetLink")) {
      Map<String, Object> row = new LinkedHashMap<>();
      row.put("source", text(link, "getSourceDataSet"));
      row.put("destination", text(link, "getDestinationDataSet"));
      row.put("sourceExpression", text(link, "getSourceExpression"));
      row.put("destinationExpression", text(link, "getDestinationExpression"));
      putText(row, "parameter", link, "getParameter");
      putFlag(row, "parameterListAllowed", link, "isParameterListAllowed");
      putText(row, "linkConditionExpression", link, "getLinkConditionExpression");
      putText(row, "startExpression", link, "getStartExpression");
      putFlag(row, "required", link, "isRequired");
      out.add(row);
    }
    return out;
  }

  /**
   * Параметры схемы: имя, заголовок, тип и значение по умолчанию.
   *
   * @param typed значения параметров корня в порядке файла ({@link #typedValues})
   */
  private static List<Map<String, Object>> parameters(
    Object schema, DcsValueTypes.Context types, List<TypedValue> typed) {
    List<Map<String, Object>> out = new ArrayList<>();
    List<Object> parameters = JaxbReflect.listOptional(schema, "getParameter");
    for (int index = 0; index < parameters.size(); index++) {
      Object parameter = parameters.get(index);
      Map<String, Object> row = new LinkedHashMap<>();
      String name = text(parameter, "getName");
      row.put("name", name);
      String title = text(parameter, "getTitle");
      if (!title.isEmpty()) {
        row.put("title", title);
      }
      String type = compactType(JaxbReflect.getOptional(parameter, "getValueType"));
      if (!type.isEmpty()) {
        row.put("type", type);
      }
      putValueType(row, parameter, types, null);
      String value = text(parameter, "getValue");
      if (!value.isEmpty()) {
        row.put("value", value);
      }
      // Модель и текстовый слой обходят одни и те же элементы корня в одном порядке; имя сверяется,
      // чтобы при расхождении не приписать параметру чужое значение
      TypedValue typedValue = index < typed.size() ? typed.get(index) : null;
      if (typedValue != null && typedValue.value() != null && typedValue.name().equals(name)) {
        row.put("valueTyped", typedValue.value());
      }
      String expression = text(parameter, "getExpression");
      if (!expression.isEmpty()) {
        row.put("expression", expression);
      }
      putFlag(row, "useRestriction", parameter, "isUseRestriction");
      putEnum(row, "use", parameter, "getUse");
      putFlag(row, "availableAsField", parameter, "isAvailableAsField");
      putFlag(row, "denyIncompleteValues", parameter, "isDenyIncompleteValues");
      putFlag(row, "valueListAllowed", parameter, "isValueListAllowed");
      String presentation = presentValue(JaxbReflect.getOptional(parameter, "getValue"));
      if (!presentation.isEmpty()) {
        row.put("valuePresentation", presentation);
      }
      // Смена типа может рассогласовать доступные значения и параметры выбора: UI предупреждает
      if (!JaxbReflect.listOptional(parameter, "getAvailableValue").isEmpty()) {
        row.put("hasAvailableValues", true);
      }
      if (JaxbReflect.getOptional(parameter, "getInputParameters") != null) {
        row.put("hasInputParameters", true);
      }
      out.add(row);
    }
    return out;
  }

  /** Значение параметра корня по текстовому слою: имя параметра и форма значения либо {@code null}. */
  private record TypedValue(String name, Map<String, Object> value) {
  }

  /**
   * Значения параметров корня ({@code parameter/value}) для ключа {@code valueTyped}: та же форма,
   * что принимает {@code value} у {@code cf-dcs-set-parameter}.
   *
   * <p>Читается текстовый слой, а не модель: после анмаршалинга {@code xs:string}, поле и ссылка
   * конфигуратора неразличимы, а редактору для эха и сверки типа нужен {@code xsi:type} дословно.
   * Язык строк — язык конфигурации, как у записи значения.
   *
   * @return по элементу на каждый {@code parameter} корня в порядке файла
   */
  private static List<TypedValue> typedValues(Path templateXml) throws IOException, JAXBException {
    String text = Files.readString(templateXml, StandardCharsets.UTF_8);
    return ConfigurationLanguage.with(templateXml, () -> {
      List<TypedValue> out = new ArrayList<>();
      String xmlns = null;
      for (XmlLines.Node node : XmlLines.children(text, List.of("DataCompositionSchema"))) {
        if (!"parameter".equals(node.name())) {
          continue;
        }
        List<XmlLines.Node> children = XmlLines.childrenAt(text, node.start());
        Map<String, Object> value = null;
        if (children.stream().anyMatch(child -> "value".equals(child.name()))) {
          xmlns = xmlns == null ? DcsSettingsEdit.rootNamespaces(text) : xmlns;
          value = parameterValueTyped(text, children, xmlns);
        }
        out.add(new TypedValue(node.leaves().getOrDefault("name", ""), value));
      }
      return out;
    });
  }

  /**
   * Значение параметра корня ({@code value}) формой {@code valueTyped}; по нему же
   * {@code cf-dcs-set-parameter} узнаёт эхо.
   *
   * <p>Список значений платформа хранит повторяющимися {@code value} по одному на элемент, без
   * представлений, и они отдаются формой элемента {@code v8:ValueListType}:
   * {@code {type: "v8:ValueListType", list: {items: [{value}]}}} — при нескольких {@code value} и при
   * одном у параметра с {@code valueListAllowed=true} (список из одного элемента). Один {@code value}
   * без списка значений — само значение; элемент {@code v8:ValueListType} — как записан, с представлениями.
   *
   * <p>Одиночный {@code <value xsi:nil="true"/>} при {@code valueListAllowed=true} — список из одного
   * элемента «значение не задано» ({@code [nil]}): платформа хранит его как записан и отличает от пустого
   * списка (тот записан без {@code value}). {@code nil} рядом с другими {@code value} загрузка отбрасывает;
   * такой список читается как есть, его эхо — без правки, а запись — отказ
   * ({@link DcsValueTypeEdit#parameterValuesXml}).
   *
   * @param children прямые потомки параметра
   * @param xmlns объявления пространств имён корня файла
   * @return {@code null}, если {@code value} нет
   */
  static Map<String, Object> parameterValueTyped(String text, List<XmlLines.Node> children, String xmlns) {
    List<XmlLines.Node> values = children.stream().filter(child -> "value".equals(child.name())).toList();
    if (values.isEmpty()) {
      return null;
    }
    boolean listAllowed = valueListAllowed(text, children);
    if (values.size() == 1) {
      XmlLines.Node only = values.get(0);
      Map<String, Object> single = DcsSettingsRead.schemaParameterValue(text.substring(only.start(), only.end()), xmlns);
      if (!listAllowed || VALUE_LIST.equals(single.get("type"))) {
        return single;
      }
    }
    List<Map<String, Object>> items = new ArrayList<>();
    for (XmlLines.Node value : values) {
      Map<String, Object> item = new LinkedHashMap<>();
      item.put("value", DcsSettingsRead.schemaParameterValue(text.substring(value.start(), value.end()), xmlns));
      items.add(item);
    }
    Map<String, Object> list = new LinkedHashMap<>();
    list.put("items", items);
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("type", VALUE_LIST);
    out.put("list", list);
    return out;
  }

  /**
   * Взведён ли у параметра корня признак {@code valueListAllowed} (лексемой {@code true} или {@code 1}).
   *
   * @param children прямые потомки параметра
   */
  static boolean valueListAllowed(String text, List<XmlLines.Node> children) {
    return children.stream().anyMatch(child -> "valueListAllowed".equals(child.name())
      && TRUE_TEXT.matcher(text.substring(child.start(), child.end())).find());
  }

  /** Вычисляемые поля: путь, выражение, заголовок, ограничение использования и оформление. */
  private static List<Map<String, Object>> calculatedFields(Object schema, DcsValueTypes.Context types) {
    List<Map<String, Object>> out = new ArrayList<>();
    for (Object field : JaxbReflect.<Object>listOptional(schema, "getCalculatedField")) {
      Map<String, Object> row = new LinkedHashMap<>();
      row.put("name", text(field, "getDataPath"));
      String expression = text(field, "getExpression");
      if (!expression.isEmpty()) {
        row.put("value", expression);
      }
      putText(row, "title", field, "getTitle");
      Object useRestriction = JaxbReflect.getOptional(field, "getUseRestriction");
      if (useRestriction != null) {
        row.put("useRestriction", fieldUseRestriction(useRestriction));
      }
      if (JaxbReflect.getOptional(field, "getAppearance") != null) {
        row.put("hasAppearance", true);
      }
      putValueType(row, field, types, null);
      out.add(row);
    }
    return out;
  }

  /**
   * Структурный тип значения владельца ключом {@code valueType}; без типа ключа нет.
   *
   * @param ownerReadOnlyReason почему владелец не правится; {@code null} — правится
   */
  private static void putValueType(
    Map<String, Object> row, Object owner, DcsValueTypes.Context types, String ownerReadOnlyReason) {
    Map<String, Object> valueType = DcsValueTypes.read(
      JaxbReflect.getOptional(owner, "getValueType"), types, ownerReadOnlyReason);
    if (valueType != null) {
      row.put("valueType", valueType);
      // Состав — рядом, ключом верхнего уровня: valueType уходит эхом в писатель как есть
      types.collectCompositions(valueType);
    }
  }

  /** Поля вложенных наборов объединения мутации не адресуют: их тип тоже только читается. */
  private static final String UNION_ITEM_FIELD_READ_ONLY =
    "поле вложенного набора объединения доступно только для чтения";

  /** Ресурсы: путь к данным, выражение и группировки, по которым считать итог. */
  private static List<Map<String, Object>> totalFields(Object schema) {
    List<Map<String, Object>> out = new ArrayList<>();
    for (Object field : JaxbReflect.<Object>listOptional(schema, "getTotalField")) {
      Map<String, Object> row = new LinkedHashMap<>();
      row.put("name", text(field, "getDataPath"));
      String expression = text(field, "getExpression");
      if (!expression.isEmpty()) {
        row.put("value", expression);
      }
      List<String> groups = new ArrayList<>();
      for (Object group : JaxbReflect.<Object>listOptional(field, "getGroup")) {
        groups.add(String.valueOf(group));
      }
      if (!groups.isEmpty()) {
        row.put("groups", groups);
      }
      out.add(row);
    }
    return out;
  }

  /** Составной тип в подпись: локальные имена QName через запятую. */
  private static String compactType(Object typeDescription) {
    if (typeDescription == null) {
      return "";
    }
    List<String> parts = new ArrayList<>();
    for (Object qname : JaxbReflect.<Object>listOptional(typeDescription, "getType")) {
      if (qname instanceof javax.xml.namespace.QName typed) {
        parts.add(typed.getLocalPart());
      }
    }
    for (Object qname : JaxbReflect.<Object>listOptional(typeDescription, "getTypeSet")) {
      if (qname instanceof javax.xml.namespace.QName typed) {
        parts.add(typed.getLocalPart());
      }
    }
    return String.join(", ", parts);
  }

  /** Схема читается моделью: ошибка разбора возвращается текстом. */
  public static Map<String, Object> validate(Path templateXml, SchemaVersion version) {
    Map<String, Object> out = new LinkedHashMap<>();
    try {
      Object schema = schemaRoot(templateXml, version);
      List<?> dataSets = JaxbReflect.listOptional(schema, "getDataSet");
      out.put("valid", true);
      out.put("dataSetCount", dataSets.size());
    } catch (Exception e) {
      out.put("valid", false);
      out.put("error", e.getMessage());
    }
    return out;
  }

  /**
   * Заменяет текст запроса набора данных; после правки схема перечитывается
   * моделью, битый файл не пишется.
   *
   * <p>Переводы строк текста приводятся к переводу строки файла: XML-разбор отдаёт текст
   * запроса с LF, а выгрузка платформы пишет его с CRLF, и без приведения эхо прочитанного
   * давало бы файл со смешанными концами строк. Текст, равный записанному (с точностью до
   * переводов строк и экранирования), файл не трогает вовсе: эхо — байтовый no-op.
   *
   * @param dataSetName имя набора верхнего уровня либо путь {@code Объединение/Вложенный};
   *     пусто — единственный набор-запрос схемы (на любой глубине объединений)
   * @throws IllegalArgumentException если набора нет, их несколько без имени, или путь указывает
   *     на объединение либо набор-объект: у них нет своего текста запроса
   */
  public static void setQuery(Path templateXml, SchemaVersion version, String dataSetName, String queryText)
    throws IOException, JAXBException {
    String text = java.nio.file.Files.readString(templateXml, java.nio.charset.StandardCharsets.UTF_8);
    DcsDataSets.Target target = queryTarget(text, dataSetName);
    XmlLines.Node query = null;
    for (XmlLines.Node child : XmlLines.childrenAt(text, target.node().start())) {
      if ("query".equals(child.name())) {
        query = child;
        break;
      }
    }
    if (query == null) {
      throw new IllegalArgumentException("У набора данных «" + target.path()
        + "» нет элемента query: файл не похож на выгрузку платформы.");
    }
    String lf = queryText == null ? "" : queryText.replace("\r\n", "\n").replace('\r', '\n');
    String recorded = target.node().leaves().getOrDefault("query", "");
    if (lf.equals(recorded.replace("\r\n", "\n").replace('\r', '\n'))) {
      return;
    }
    String escaped = escape("\r\n".equals(XmlLines.newline(text)) ? lf.replace("\n", "\r\n") : lf);
    // Новый набор-запрос записан пустым самозакрытым тегом: текст пишется и в него
    boolean emptyTag = text.startsWith("/>", query.end() - 2);
    String updated;
    if (emptyTag) {
      updated = text.substring(0, query.start()) + "<query>" + escaped + "</query>" + text.substring(query.end());
    } else {
      int contentStart = XmlLines.startTagEnd(text, query.start()) + 1;
      int contentEnd = text.lastIndexOf("</", query.end() - 1);
      updated = text.substring(0, contentStart) + escaped + text.substring(contentEnd);
    }
    verify(updated, version);
    java.nio.file.Files.writeString(templateXml, updated, java.nio.charset.StandardCharsets.UTF_8);
  }

  /** Набор-запрос, текст которого пишет {@link #setQuery}. */
  private static DcsDataSets.Target queryTarget(String text, String dataSetName) {
    if (dataSetName == null || dataSetName.isBlank()) {
      List<DcsDataSets.Target> queries = DcsDataSets.queries(text);
      if (queries.isEmpty()) {
        throw new IllegalArgumentException("Набор данных с запросом не найден.");
      }
      if (queries.size() > 1) {
        throw new IllegalArgumentException("Наборов с запросом несколько: укажите имя набора.");
      }
      return queries.get(0);
    }
    DcsDataSets.Target target = DcsDataSets.resolve(text, dataSetName.trim());
    switch (target.kind()) {
      case "union" -> throw new IllegalArgumentException("Набор данных «" + target.path()
        + "» — объединение: своего запроса у него нет, укажите путь к вложенному набору-запросу ("
        + target.path() + "/Имя).");
      case "object" -> throw new IllegalArgumentException("Набор данных «" + target.path()
        + "» — объект, а не запрос: текста запроса у него нет.");
      default -> {
        return target;
      }
    }
  }

  /** Элементы корня схемы, которые по её порядку идут перед вычисляемыми полями. */
  private static final List<String> CALCULATED_FIELD_PREDECESSORS =
    List.of("dataSource", "dataSet", "dataSetLink", "calculatedField");

  /**
   * Добавляет вычисляемое поле: путь к данным и выражение.
   *
   * <p>Путь занят, если в корне схемы есть вычисляемое поле с тем же путём без учёта регистра:
   * выражения схемы регистр имён не различают. Многострочные выражение и заголовок пишутся
   * дословно, с переводом строки файла.
   *
   * @throws IllegalArgumentException если путь занят или в схеме нет наборов данных
   */
  public static void addCalculatedField(
    Path templateXml, SchemaVersion version, String dataPath, String expression, String title)
    throws IOException, JAXBException {
    ConfigurationLanguage.with(templateXml, () -> {
      addCalculatedFieldInLanguage(templateXml, version, dataPath, expression, title);
      return null;
    });
  }

  private static void addCalculatedFieldInLanguage(
    Path templateXml, SchemaVersion version, String dataPath, String expression, String title)
    throws IOException, JAXBException {
    String text = java.nio.file.Files.readString(templateXml, java.nio.charset.StandardCharsets.UTF_8);
    String eol = text.contains("\r\n") ? "\r\n" : "\n";
    StringBuilder block = new StringBuilder();
    block.append("	<calculatedField>").append(eol);
    block.append("		<dataPath>").append(escape(dataPath)).append("</dataPath>").append(eol);
    block.append("		<expression>").append(escape(expression)).append("</expression>").append(eol);
    if (title != null && !title.isBlank()) {
      block.append("		<title xsi:type=\"v8:LocalStringType\">").append(eol);
      block.append("			<v8:item>").append(eol);
      block.append("				<v8:lang>").append(ConfigurationLanguage.current())
        .append("</v8:lang>").append(eol);
      block.append("				<v8:content>").append(escape(title)).append("</v8:content>").append(eol);
      block.append("			</v8:item>").append(eol);
      block.append("		</title>").append(eol);
    }
    block.append("	</calculatedField>").append(eol);
    // Поле встаёт по порядку схемы: после наборов, их связей и прежних вычисляемых
    // полей корня, но не внутри вложенных схем
    XmlLines.Node anchor = null;
    boolean dataSets = false;
    String wanted = dataPath == null ? "" : dataPath.toUpperCase(java.util.Locale.ROOT);
    for (XmlLines.Node node : XmlLines.children(text, List.of("DataCompositionSchema"))) {
      if (CALCULATED_FIELD_PREDECESSORS.contains(node.name())) {
        anchor = node;
      }
      dataSets |= "dataSet".equals(node.name());
      String existing = "calculatedField".equals(node.name()) ? node.leaves().get("dataPath") : null;
      if (existing != null && existing.toUpperCase(java.util.Locale.ROOT).equals(wanted)) {
        throw new IllegalArgumentException(
          "Вычисляемое поле «" + dataPath + "» уже есть в схеме: «" + existing + "».");
      }
    }
    if (!dataSets || anchor == null) {
      throw new IllegalArgumentException("В схеме нет наборов данных.");
    }
    // Разметка блока уже с переводом строки файла; приводятся переводы строк многострочного текста
    String inserted = XmlGranularPatch.lineBreaks(block.toString(), eol);
    int lineEnd = text.indexOf('\n', anchor.end());
    String updated = lineEnd < 0
      ? text.substring(0, anchor.end()) + eol + inserted + text.substring(anchor.end())
      : text.substring(0, lineEnd + 1) + inserted + text.substring(lineEnd + 1);
    verify(updated, version);
    java.nio.file.Files.writeString(templateXml, updated, java.nio.charset.StandardCharsets.UTF_8);
  }

  /** Перечитывает правленный текст моделью схемы: битый файл не пишется. */
  static void verify(String xml, SchemaVersion version) throws IOException {
    Path temp = java.nio.file.Files.createTempFile("dcs", ".xml");
    try {
      java.nio.file.Files.writeString(temp, xml, java.nio.charset.StandardCharsets.UTF_8);
      schemaRoot(temp, version);
    } catch (JAXBException e) {
      throw new IOException("Схема после правки не читается моделью: " + e.getMessage(), e);
    } finally {
      java.nio.file.Files.deleteIfExists(temp);
    }
  }

  /** Экранирует текстовый узел так же, как платформа: только {@code &}, {@code <}, {@code >}. */
  static String escape(String value) {
    return value == null
      ? ""
      : value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
  }

  /** Модель схемы; открыта пакету: проверка корпуса обходит и вложенные схемы, которых {@link #info} не раскрывает. */
  static Object schemaRoot(Path templateXml, SchemaVersion version) throws IOException, JAXBException {
    // Платформа пишет корень DataCompositionSchema с заглавной, а схема XSD
    // объявляет строчный элемент: читаем с явным типом корня
    Class<?> schemaClass;
    try {
      schemaClass = Class.forName(
        "io.github.yellowhammer.designerxml.jaxb." + version.name().toLowerCase(java.util.Locale.ROOT)
          + ".v8_1_dcs_schema.DataCompositionSchema");
    } catch (ClassNotFoundException e) {
      throw new IllegalStateException("Модель схемы компоновки не собрана для " + version, e);
    }
    try (var in = java.nio.file.Files.newInputStream(templateXml)) {
      JAXBElement<?> element = version.jaxbContext().createUnmarshaller()
        .unmarshal(new javax.xml.transform.stream.StreamSource(in), schemaClass);
      Object value = element.getValue();
      if (value == null) {
        throw new IllegalArgumentException("Файл не является схемой компоновки данных.");
      }
      return value;
    }
  }

  private static List<Map<String, Object>> dataSets(Object schema, DcsValueTypes.Context types) {
    List<Map<String, Object>> out = new ArrayList<>();
    for (Object dataSet : JaxbReflect.<Object>listOptional(schema, "getDataSet")) {
      out.add(dataSetRow(dataSet, types, false));
    }
    return out;
  }

  /**
   * Набор данных: вид, источник и поля; у объединения — вложенные наборы рекурсивно.
   *
   * @param unionItem набор вложен в объединение
   */
  private static Map<String, Object> dataSetRow(Object dataSet, DcsValueTypes.Context types, boolean unionItem) {
    Map<String, Object> item = new LinkedHashMap<>();
    item.put("name", text(dataSet, "getName"));
    String type = dataSetType(dataSet);
    if (!type.isEmpty()) {
      item.put("type", type);
    }
    putText(item, "dataSource", dataSet, "getDataSource");
    putText(item, "objectName", dataSet, "getObjectName");
    Object query = JaxbReflect.getOptional(dataSet, "getQuery");
    if (query != null) {
      item.put("query", String.valueOf(query));
    }
    if ("query".equals(type)) {
      // Платформа не пишет autoFillFields со значением по умолчанию: пропуск значит true
      Object autoFill = JaxbReflect.getOptional(dataSet, "isAutoFillFields");
      item.put("autoFillFields", !(autoFill instanceof Boolean flag) || flag);
    }
    List<Map<String, Object>> fields = new ArrayList<>();
    for (Object field : JaxbReflect.<Object>listOptional(dataSet, "getField")) {
      fields.add(fieldRow(field, types, unionItem ? UNION_ITEM_FIELD_READ_ONLY : null));
    }
    if (!fields.isEmpty()) {
      item.put("fields", fields);
    }
    if ("union".equals(type)) {
      List<Map<String, Object>> items = new ArrayList<>();
      for (Object nested : JaxbReflect.<Object>listOptional(dataSet, "getItem")) {
        items.add(dataSetRow(nested, types, true));
      }
      if (!items.isEmpty()) {
        item.put("items", items);
      }
    }
    return item;
  }

  /** Вид набора данных по типу модели: запрос, объект или объединение. */
  private static String dataSetType(Object dataSet) {
    return switch (dataSet.getClass().getSimpleName()) {
      case "DataSetQuery" -> "query";
      case "DataSetObject" -> "object";
      case "DataSetUnion" -> "union";
      default -> "";
    };
  }

  /** Поле набора: вид, заголовки по языкам, ограничения, роль и признаки наличия секций. */
  private static Map<String, Object> fieldRow(
    Object field, DcsValueTypes.Context types, String ownerReadOnlyReason) {
    Map<String, Object> row = new LinkedHashMap<>();
    row.put("dataPath", text(field, "getDataPath"));
    String kind = fieldKind(field);
    if (!kind.isEmpty()) {
      row.put("kind", kind);
    }
    putText(row, "field", field, "getField");
    putText(row, "title", field, "getTitle");
    Map<String, String> titleLangs = localizedItems(JaxbReflect.getOptional(field, "getTitle"));
    if (!titleLangs.isEmpty()) {
      row.put("titleLangs", titleLangs);
    }
    Object useRestriction = JaxbReflect.getOptional(field, "getUseRestriction");
    if (useRestriction != null) {
      row.put("useRestriction", fieldUseRestriction(useRestriction));
    }
    Object attributeUseRestriction = JaxbReflect.getOptional(field, "getAttributeUseRestriction");
    if (attributeUseRestriction != null) {
      row.put("attributeUseRestriction", fieldUseRestriction(attributeUseRestriction));
    }
    Object role = JaxbReflect.getOptional(field, "getRole");
    if (role != null) {
      row.put("role", roleRow(role));
    }
    String valueType = compactType(JaxbReflect.getOptional(field, "getValueType"));
    if (!valueType.isEmpty()) {
      row.put("valueTypePresentation", valueType);
    }
    putValueType(row, field, types, ownerReadOnlyReason);
    if (JaxbReflect.getOptional(field, "getAppearance") != null) {
      row.put("hasAppearance", true);
    }
    if (JaxbReflect.getOptional(field, "getInputParameters") != null) {
      row.put("hasInputParameters", true);
    }
    return row;
  }

  /** Вид поля набора по типу модели: поле, папка или вложенный набор. */
  private static String fieldKind(Object field) {
    return switch (field.getClass().getSimpleName()) {
      case "DataSetFieldField" -> "field";
      case "DataSetFieldFolder" -> "folder";
      case "DataSetFieldNestedDataSet" -> "nested";
      default -> "";
    };
  }

  /** Четыре флага ограничения использования; невзведённые платформа в файл не пишет. */
  private static Map<String, Object> fieldUseRestriction(Object restriction) {
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("field", JaxbReflect.getBooleanOptional(restriction, "isField"));
    out.put("condition", JaxbReflect.getBooleanOptional(restriction, "isCondition"));
    out.put("group", JaxbReflect.getBooleanOptional(restriction, "isGroup"));
    out.put("order", JaxbReflect.getBooleanOptional(restriction, "isOrder"));
    return out;
  }

  /** Роль поля набора: состав по dcscom:DataSetFieldRole, пишутся только заданные значения. */
  private static Map<String, Object> roleRow(Object role) {
    Map<String, Object> out = new LinkedHashMap<>();
    Object periodNumber = JaxbReflect.getOptional(role, "getPeriodNumber");
    if (periodNumber != null) {
      out.put("periodNumber", periodNumber);
    }
    putEnum(out, "periodType", role, "getPeriodType");
    putFlag(out, "dimension", role, "isDimension");
    putText(out, "parentDimension", role, "getParentDimension");
    putFlag(out, "account", role, "isAccount");
    putText(out, "accountTypeExpression", role, "getAccountTypeExpression");
    putFlag(out, "balance", role, "isBalance");
    putText(out, "balanceGroupName", role, "getBalanceGroupName");
    putEnum(out, "balanceType", role, "getBalanceType");
    putEnum(out, "accountingBalanceType", role, "getAccountingBalanceType");
    putText(out, "accountField", role, "getAccountField");
    putFlag(out, "ignoreNullValues", role, "isIgnoreNullValues");
    putFlag(out, "required", role, "isRequired");
    putFlag(out, "dimensionAttribute", role, "isDimensionAttribute");
    putFlag(out, "isMain", role, "isIsMain");
    return out;
  }

  /** Все языки локализованной строки: код языка → содержимое. */
  private static Map<String, String> localizedItems(Object localized) {
    Map<String, String> out = new LinkedHashMap<>();
    if (localized == null) {
      return out;
    }
    for (Object item : JaxbReflect.listOptional(localized, "getItem")) {
      String lang = stringify(JaxbReflect.getOptional(item, "getLang"));
      if (!lang.isEmpty()) {
        out.put(lang, stringify(JaxbReflect.getOptional(item, "getContent")));
      }
    }
    return out;
  }

  /** Строка из геттера в JSON; пустые значения не пишутся. */
  private static void putText(Map<String, Object> row, String key, Object target, String getter) {
    String value = text(target, getter);
    if (!value.isEmpty()) {
      row.put(key, value);
    }
  }

  /** Булев элемент: отсутствует в файле — ключа нет, явные true/false сохраняются. */
  private static void putFlag(Map<String, Object> row, String key, Object target, String getter) {
    Object value = JaxbReflect.getOptional(target, getter);
    if (value instanceof Boolean flag) {
      row.put(key, flag);
    }
  }

  /** Enum-значение геттера в JSON; отсутствие значения — ключа нет. */
  private static void putEnum(Map<String, Object> row, String key, Object target, String getter) {
    String value = enumValue(JaxbReflect.getOptional(target, getter));
    if (!value.isEmpty()) {
      row.put(key, value);
    }
  }

  /** Лексическое XML-имя enum-значения модели (из value()), а не имя Java-константы. */
  private static String enumValue(Object enumConstant) {
    if (enumConstant == null) {
      return "";
    }
    Object value = JaxbReflect.getOptional(enumConstant, "value");
    if (value != null) {
      return value.toString();
    }
    return enumConstant instanceof Enum<?> constant ? constant.name() : "";
  }

  /**
   * Значение параметра строкой для JSON: скаляры и даты лексически, enum именем из формата,
   * стандартный период — вариантом; «Неопределено» и неизвестные объекты — пустая строка.
   */
  private static String presentValue(Object value) {
    if (value == null) {
      return "";
    }
    if (value instanceof JAXBElement<?> element) {
      return element.isNil() ? "" : presentValue(element.getValue());
    }
    if (value instanceof String || value instanceof Number || value instanceof Boolean) {
      return String.valueOf(value);
    }
    if (value instanceof Enum<?>) {
      return enumValue(value);
    }
    if (value instanceof javax.xml.datatype.XMLGregorianCalendar calendar) {
      return calendar.toXMLFormat();
    }
    if (value instanceof javax.xml.namespace.QName qname) {
      return qname.getLocalPart();
    }
    if (value instanceof List<?> list) {
      List<String> parts = new ArrayList<>();
      for (Object item : list) {
        String part = presentValue(item);
        if (!part.isEmpty()) {
          parts.add(part);
        }
      }
      return String.join(", ", parts);
    }
    Object variant = JaxbReflect.getOptional(value, "getVariant");
    if (variant != null) {
      return presentValue(variant);
    }
    return stringify(value);
  }

  private static List<Map<String, Object>> namedList(
    Object schema, String listGetter, String keyGetter, String valueGetter) {
    List<Map<String, Object>> out = new ArrayList<>();
    for (Object item : JaxbReflect.<Object>listOptional(schema, listGetter)) {
      Map<String, Object> row = new LinkedHashMap<>();
      row.put("name", text(item, keyGetter));
      String value = text(item, valueGetter);
      if (!value.isEmpty()) {
        row.put("value", value);
      }
      out.add(row);
    }
    return out;
  }

  /**
   * Значение в текст для JSON: скаляры как есть, локализованные строки содержимым,
   * прочие объекты модели наружу не выходят - Java-представление читателю не нужно.
   */
  private static String text(Object target, String getter) {
    return stringify(JaxbReflect.getOptional(target, getter));
  }

  private static String stringify(Object value) {
    if (value == null) {
      return "";
    }
    if (value instanceof String || value instanceof Number || value instanceof Boolean
      || value instanceof Enum<?>) {
      return String.valueOf(value);
    }
    if (value instanceof List<?> list) {
      List<String> parts = new ArrayList<>();
      for (Object item : list) {
        String part = stringify(item);
        if (!part.isEmpty()) {
          parts.add(part);
        }
      }
      return String.join(", ", parts);
    }
    // Локализованная строка: элементы item с lang/content, предпочитается русский
    List<Object> items = JaxbReflect.listOptional(value, "getItem");
    if (!items.isEmpty()) {
      String first = "";
      for (Object item : items) {
        String content = stringify(JaxbReflect.getOptional(item, "getContent"));
        if (first.isEmpty()) {
          first = content;
        }
        if (ConfigurationLanguage.current().equals(stringify(JaxbReflect.getOptional(item, "getLang")))) {
          return content;
        }
      }
      return first;
    }
    Object content = JaxbReflect.getOptional(value, "getContent");
    if (content != null && content != value) {
      return stringify(content);
    }
    if (value instanceof javax.xml.namespace.QName qname) {
      return qname.getLocalPart();
    }
    return "";
  }
}
