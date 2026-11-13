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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.gson.Gson;
import io.github.yellowhammer.designerxml.SchemaVersion;
import io.github.yellowhammer.designerxml.Ssl31SubmodulePaths;
import io.github.yellowhammer.designerxml.cli.DesignerXmlCli;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Редактор схемы компоновки данных на реальных схемах ssl31: чтение cf-dcs-info по контракту
 * редактора, мутации наборов данных (добавление, удаление, переименование с каскадом по
 * связям, замена подэлементов), их полей (добавление, правка подэлементов по xs:sequence,
 * удаление), связей наборов и ресурсов (добавление, правка и удаление по порядковому номеру
 * в файле), вычисляемых полей (правка и удаление по пути к данным) и параметров схемы
 * (добавление, правка и удаление по имени).
 * У мутаций проверяется и байтовая сторона: нетронутые части файла не меняются, порядок
 * детей корня, поля и связи соответствует xs:sequence формата.
 * Схема проекта 1С:EDT ({@code Template.dcs} из ssl31-edt) правится теми же операциями:
 * формат файла один и тот же, отказ EDT-гейта канала её не касается.
 */
class DcsEditTest {

  /** Схема со связью наборов: запрос, объект и dataSetLink между ними. */
  private static final String LINKED_SCHEMA =
    "src/cf/Reports/РезультатыПроверкиУчета/Templates/ОсновнаяСхемаКомпоновкиДанных/Ext/Template.xml";

  /** Схема с двумя наборами-объектами без связей. */
  private static final String TWO_OBJECTS_SCHEMA =
    "src/cf/Reports/ПрогрессОбновленияОбластейДанных/Templates/ОсновнаяСхемаКомпоновкиДанных/Ext/Template.xml";

  /** Схема с набором-объединением и вложенными в него наборами-объектами. */
  private static final String UNION_SCHEMA =
    "src/cf/Reports/ОбъемНенужныхФайлов/Templates/ОсновнаяСхемаКомпоновкиДанных/Ext/Template.xml";

  /** Схема с записанным в файл autoFillFields=false у набора-запроса. */
  private static final String AUTO_FILL_SCHEMA =
    "src/cf/Reports/ИсторияРазмераПриложения/Templates/ОсновнаяСхемаКомпоновкиДанных/Ext/Template.xml";

  /** Схема с полем-папкой («Итоги») и полем-вложенным набором («Товары»). */
  private static final String FOLDER_SCHEMA =
    "src/cf/Documents/_ДемоСчетНаОплатуПокупателю/Templates/ДанныеПечати/Ext/Template.xml";

  /** Схема с четырьмя связями наборов; у первой записан startExpression. */
  private static final String MULTI_LINK_SCHEMA =
    "src/cf/Reports/АнализПравДоступа/Templates/Макет/Ext/Template.xml";

  @TempDir Path tempDir;

  private static Map<String, Object> info(String relativePath) throws Exception {
    Path dcs = Ssl31SubmodulePaths.projectRoot().resolve(relativePath);
    return DcsRead.info(dcs, SchemaVersion.V2_20);
  }

  @SuppressWarnings("unchecked")
  private static List<Map<String, Object>> rows(Map<String, Object> info, String key) {
    return (List<Map<String, Object>>) info.get(key);
  }

  @SuppressWarnings("unchecked")
  private static List<Map<String, Object>> fields(Map<String, Object> dataSet) {
    return (List<Map<String, Object>>) dataSet.get("fields");
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> nested(Map<String, Object> row, String key) {
    Object value = row.get(key);
    assertThat(value).as(key).isInstanceOf(Map.class);
    return (Map<String, Object>) value;
  }

  private static Map<String, Object> byKey(List<Map<String, Object>> rows, String key, String value) {
    return rows.stream()
      .filter(row -> value.equals(row.get(key)))
      .findFirst()
      .orElseThrow(() -> new AssertionError(key + "=" + value + " не найден: " + rows));
  }

  @Test
  void infoReadsSetsLinksResourcesAndParameters() throws Exception {
    Map<String, Object> info = info(
      "src/cf/Reports/РезультатыПроверкиУчета/Templates/ОсновнаяСхемаКомпоновкиДанных/Ext/Template.xml");

    Map<String, Object> source = rows(info, "dataSources").get(0);
    assertThat(source.get("name")).isEqualTo("ИсточникДанных1");
    assertThat(source.get("type")).isEqualTo("Local");

    Map<String, Object> query = byKey(rows(info, "dataSets"), "name", "ОсновнойЗапрос");
    assertThat(query.get("type")).isEqualTo("query");
    assertThat(query.get("dataSource")).isEqualTo("ИсточникДанных1");
    // autoFillFields в файле не записан: по умолчанию платформы это true
    assertThat(query.get("autoFillFields")).isEqualTo(true);
    assertThat((String) query.get("query")).contains("ВЫБРАТЬ РАЗРЕШЕННЫЕ");
    Map<String, Object> dimension = byKey(fields(query), "dataPath", "ВидПроверки");
    assertThat(dimension.get("kind")).isEqualTo("field");
    assertThat(dimension.get("field")).isEqualTo("ВидПроверки");
    assertThat(nested(dimension, "role").get("dimension")).isEqualTo(true);

    Map<String, Object> object = byKey(rows(info, "dataSets"), "name", "ВнешняяТаблица");
    assertThat(object.get("type")).isEqualTo("object");
    assertThat(object.get("objectName")).isEqualTo("ВнешняяТаблица");
    assertThat(object).doesNotContainKeys("query", "autoFillFields");
    Map<String, Object> external = byKey(fields(object), "dataPath", "ОбработчикПереходаКИсправлению");
    assertThat(external.get("title")).isEqualTo("Обработчик перехода к исправлению");
    assertThat(nested(external, "titleLangs"))
      .containsExactly(Map.entry("ru", "Обработчик перехода к исправлению"));
    assertThat(nested(external, "attributeUseRestriction"))
      .containsEntry("field", true)
      .containsEntry("condition", true)
      .containsEntry("group", true)
      .containsEntry("order", true);
    assertThat(external.get("valueTypePresentation")).isEqualTo("string");

    Map<String, Object> link = rows(info, "dataSetLinks").get(0);
    assertThat(link.get("source")).isEqualTo("ВнешняяТаблица");
    assertThat(link.get("destination")).isEqualTo("ОсновнойЗапрос");
    assertThat(link.get("sourceExpression")).isEqualTo("Идентификатор");
    assertThat(link.get("destinationExpression")).isEqualTo("ИдентификаторПроверки");
    // чего нет в файле, того нет и в ответе
    assertThat(link).doesNotContainKeys(
      "parameter", "parameterListAllowed", "linkConditionExpression", "startExpression", "required");

    Map<String, Object> calculated = byKey(rows(info, "calculatedFields"), "name", "ПравилоПроверкиПричиныПроблемы");
    assertThat((String) calculated.get("value")).contains("ПравилоПроверкиПричиныПроблемы");
    assertThat(calculated.get("title")).isEqualTo("Причины проблемы");

    Map<String, Object> total = byKey(rows(info, "totalFields"), "name", "КоличествоПроблемСОбъектом");
    assertThat(total.get("value")).isEqualTo("Количество(КоличествоПроблемСОбъектом)");
    assertThat(total.get("groups")).isEqualTo(List.of(
      "ВидПроверки", "Ответственный", "ОтветственныйГруппировка", "ПравилоПроверки", "ОбщийИтог"));

    Map<String, Object> context = byKey(rows(info, "parameters"), "name", "Контекст");
    assertThat(context.get("type")).isEqualTo("AnyIBRef");
    assertThat(context.get("useRestriction")).isEqualTo(true);
    assertThat(context.get("valueListAllowed")).isEqualTo(true);
    assertThat(context.get("availableAsField")).isEqualTo(false);
    assertThat(context.get("denyIncompleteValues")).isEqualTo(true);

    Map<String, Object> flag = byKey(rows(info, "parameters"), "name", "ВыводитьОтветственного");
    assertThat(flag.get("use")).isEqualTo("Always");
    assertThat(flag.get("useRestriction")).isEqualTo(false);
    assertThat(flag.get("valuePresentation")).isEqualTo("false");
  }

  @Test
  void infoReadsUnionItemsRecursively() throws Exception {
    Map<String, Object> info = info(
      "src/cf/Reports/ОбъемНенужныхФайлов/Templates/ОсновнаяСхемаКомпоновкиДанных/Ext/Template.xml");

    Map<String, Object> union = byKey(rows(info, "dataSets"), "name", "НаборДанных1");
    assertThat(union.get("type")).isEqualTo("union");
    assertThat(union).doesNotContainKeys("query", "autoFillFields", "dataSource");
    assertThat(fields(union)).isNotEmpty();

    @SuppressWarnings("unchecked")
    List<Map<String, Object>> items = (List<Map<String, Object>>) union.get("items");
    assertThat(items).hasSize(2);
    Map<String, Object> item = byKey(items, "name", "ОбъемНенужныхФайлов");
    assertThat(item.get("type")).isEqualTo("object");
    assertThat(item.get("dataSource")).isEqualTo("ИсточникДанных1");
    assertThat(item.get("objectName")).isEqualTo("ОбъемНенужныхФайлов");
    assertThat(fields(item)).isNotEmpty();
  }

  @Test
  void infoReadsFieldRoles() throws Exception {
    Map<String, Object> info = info(
      "src/cf/Reports/_ДемоОборотноСальдоваяВедомость/Templates/ОсновнаяСхемаКомпоновкиДанных/Ext/Template.xml");
    List<Map<String, Object>> fields = fields(byKey(rows(info, "dataSets"), "name", "НаборДанных1"));

    Map<String, Object> account = nested(byKey(fields, "dataPath", "Счет"), "role");
    assertThat(account.get("dimension")).isEqualTo(true);
    assertThat(account.get("account")).isEqualTo(true);
    assertThat(account.get("accountTypeExpression")).isEqualTo("Счет.Вид");

    Map<String, Object> balance = nested(byKey(fields, "dataPath", "ВалютнаяСуммаКонОстДт"), "role");
    assertThat(balance.get("balance")).isEqualTo(true);
    assertThat(balance.get("balanceGroupName")).isEqualTo("ВалютнаяСумма");
    // значения перечислений отдаются лексическими именами формата, не Java-константами
    assertThat(balance.get("balanceType")).isEqualTo("ClosingBalance");
    assertThat(balance.get("accountingBalanceType")).isEqualTo("Debit");
    assertThat(balance.get("accountField")).isEqualTo("Счет");

    Map<String, Object> period = nested(byKey(fields, "dataPath", "ПлатежныйДокумент"), "role");
    assertThat(String.valueOf(period.get("periodNumber"))).isEqualTo("1");
    assertThat(period.get("periodType")).isEqualTo("Main");
  }

  @Test
  void infoReadsFieldKindsAndAppearanceFlag() throws Exception {
    Map<String, Object> info = info(
      "src/cf/Documents/_ДемоСчетНаОплатуПокупателю/Templates/ДанныеПечати/Ext/Template.xml");
    List<Map<String, Object>> fields = fields(rows(info, "dataSets").get(0));

    Map<String, Object> folder = byKey(fields, "dataPath", "Итоги");
    assertThat(folder.get("kind")).isEqualTo("folder");
    assertThat(folder.get("title")).isEqualTo("Итоги");

    Map<String, Object> nestedField = byKey(fields, "dataPath", "Товары");
    assertThat(nestedField.get("kind")).isEqualTo("nested");
    assertThat(nestedField.get("field")).isEqualTo("Товары");

    Map<String, Object> date = byKey(fields, "dataPath", "Дата");
    assertThat(date.get("kind")).isEqualTo("field");
    assertThat(date.get("hasAppearance")).isEqualTo(true);
    assertThat(date).doesNotContainKey("hasInputParameters");
  }

  @Test
  void infoReadsAutoFillFieldsAndCalculatedFieldDetails() throws Exception {
    Map<String, Object> info = info(
      "src/cf/Reports/ИсторияРазмераПриложения/Templates/ОсновнаяСхемаКомпоновкиДанных/Ext/Template.xml");
    Map<String, Object> dataSet = rows(info, "dataSets").get(0);
    assertThat(dataSet.get("type")).isEqualTo("query");
    // в файле записано autoFillFields=false — явное значение не подменяется умолчанием
    assertThat(dataSet.get("autoFillFields")).isEqualTo(false);

    Map<String, Object> calculated = byKey(rows(info, "calculatedFields"), "name", "ВидМетаданных");
    assertThat(calculated.get("title")).isEqualTo("Вид метаданных");
    Map<String, Object> useRestriction = nested(calculated, "useRestriction");
    assertThat(useRestriction.get("condition")).isEqualTo(true);
    assertThat(useRestriction.get("field")).isEqualTo(false);
    assertThat(useRestriction.get("group")).isEqualTo(false);
    assertThat(useRestriction.get("order")).isEqualTo(false);

    Map<String, Object> journal = info(
      "src/cf/Reports/АнализЖурналаРегистрации/Templates/ОсновнаяСхемаКомпоновкиДанных/Ext/Template.xml");
    Map<String, Object> month = byKey(rows(journal, "calculatedFields"), "name", "Месяц");
    assertThat(month.get("hasAppearance")).isEqualTo(true);

    Map<String, Object> rights = info("src/cf/Reports/АнализПравДоступа/Templates/Макет/Ext/Template.xml");
    Map<String, Object> linked = rows(rights, "dataSetLinks").get(0);
    assertThat(linked.get("startExpression")).isEqualTo("Неопределено");
    assertThat(linked).doesNotContainKeys("parameter", "required");
  }

  @Test
  void setQueryWritesIntoFreshQueryDataSet() throws Exception {
    // Базовый поток конфигуратора: добавить набор-запрос и сразу написать запрос.
    // Новый набор записан пустым <query/> - setQuery обязан раскрыть его в парный тег.
    Path dcs = copySchema(LINKED_SCHEMA);
    DcsEdit.addDataSet(dcs, SchemaVersion.V2_20, "СвежийЗапрос", "query", "ИсточникДанных1");

    DcsRead.setQuery(dcs, SchemaVersion.V2_20, "СвежийЗапрос", "ВЫБРАТЬ 1 КАК Поле1");

    Map<String, Object> fresh = byKey(rows(DcsRead.info(dcs, SchemaVersion.V2_20), "dataSets"), "name", "СвежийЗапрос");
    assertThat(fresh.get("query")).isEqualTo("ВЫБРАТЬ 1 КАК Поле1");
  }

  @Test
  void addDataSetKeepsRootOrderAndFilePhysics() throws Exception {
    Path dcs = copySchema(LINKED_SCHEMA);
    String before = Files.readString(dcs, StandardCharsets.UTF_8);

    DcsEdit.addDataSet(dcs, SchemaVersion.V2_20, "НовыйЗапрос", "query", "ИсточникДанных1");
    String afterQuery = Files.readString(dcs, StandardCharsets.UTF_8);
    assertSingleEditZone(before, afterQuery);

    DcsEdit.addDataSet(dcs, SchemaVersion.V2_20, "ВнешниеДанные", "object", "ИсточникДанных1");
    DcsEdit.addDataSet(dcs, SchemaVersion.V2_20, "Объединение", "union", null);

    Map<String, Object> info = DcsRead.info(dcs, SchemaVersion.V2_20);
    Map<String, Object> query = byKey(rows(info, "dataSets"), "name", "НовыйЗапрос");
    assertThat(query.get("type")).isEqualTo("query");
    assertThat(query.get("dataSource")).isEqualTo("ИсточникДанных1");
    assertThat(query.get("query")).isEqualTo("");
    // элемент autoFillFields не записан: по умолчанию платформы автозаполнение включено
    assertThat(query.get("autoFillFields")).isEqualTo(true);
    Map<String, Object> object = byKey(rows(info, "dataSets"), "name", "ВнешниеДанные");
    assertThat(object.get("type")).isEqualTo("object");
    assertThat(object.get("objectName")).isEqualTo("ВнешниеДанные");
    Map<String, Object> union = byKey(rows(info, "dataSets"), "name", "Объединение");
    assertThat(union.get("type")).isEqualTo("union");
    assertThat(union).doesNotContainKeys("dataSource", "objectName", "query");

    String after = Files.readString(dcs, StandardCharsets.UTF_8);
    // autoFillFields со значением по умолчанию не записан ни у одного нового набора
    assertThat(after).doesNotContain("<autoFillFields");
    // физика файла платформы: BOM, CRLF и конец без перевода строки не сломаны
    assertThat(after).startsWith("﻿<?xml");
    assertThat(after).contains("\r\n");
    assertThat(after).endsWith("</DataCompositionSchema>");
    // порядок детей корня соответствует последовательности из XSD, наборы встали до связей
    List<String> order = schemaSequence();
    List<String> roots = XmlLines.children(after, List.of("DataCompositionSchema")).stream()
      .map(XmlLines.Node::name)
      .toList();
    assertThat(roots.stream().map(order::indexOf).toList()).isSorted();
    assertThat(roots).containsSubsequence("dataSet", "dataSet", "dataSet", "dataSet", "dataSet", "dataSetLink");
  }

  @Test
  void addDataSetRefusals() throws Exception {
    Path dcs = copySchema(LINKED_SCHEMA);
    String before = Files.readString(dcs, StandardCharsets.UTF_8);

    assertThatThrownBy(() -> DcsEdit.addDataSet(dcs, SchemaVersion.V2_20, "ОсновнойЗапрос", "query", "Источник"))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("уже есть");
    assertThatThrownBy(() -> DcsEdit.addDataSet(dcs, SchemaVersion.V2_20, "Новый", "table", "Источник"))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("query, object или union");
    assertThatThrownBy(() -> DcsEdit.addDataSet(dcs, SchemaVersion.V2_20, "Новый", "query", null))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("dataSource");

    assertThat(Files.readString(dcs, StandardCharsets.UTF_8)).isEqualTo(before);
  }

  @Test
  void removeDataSetDeletesWholeElement() throws Exception {
    Path dcs = copySchema(TWO_OBJECTS_SCHEMA);
    String before = Files.readString(dcs, StandardCharsets.UTF_8);

    DcsEdit.removeDataSet(dcs, SchemaVersion.V2_20, "СводнаяИнформация");

    String after = Files.readString(dcs, StandardCharsets.UTF_8);
    assertSingleEditZone(before, after);
    assertThat(after).doesNotContain("СводнаяИнформация");
    List<Map<String, Object>> sets = rows(DcsRead.info(dcs, SchemaVersion.V2_20), "dataSets");
    assertThat(sets).hasSize(1);
    assertThat(sets.get(0).get("name")).isEqualTo("ОбластиСПроблемами");

    // объединение удаляется целиком вместе с вложенными наборами
    Path unionDcs = copySchema(UNION_SCHEMA);
    DcsEdit.removeDataSet(unionDcs, SchemaVersion.V2_20, "НаборДанных1");
    assertThat(rows(DcsRead.info(unionDcs, SchemaVersion.V2_20), "dataSets")).isEmpty();
  }

  @Test
  void removeDataSetRefusals() throws Exception {
    Path dcs = copySchema(LINKED_SCHEMA);
    String before = Files.readString(dcs, StandardCharsets.UTF_8);

    // набор упомянут в dataSetLink: сначала надо удалить связи
    assertThatThrownBy(() -> DcsEdit.removeDataSet(dcs, SchemaVersion.V2_20, "ВнешняяТаблица"))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("связях наборов");
    assertThatThrownBy(() -> DcsEdit.removeDataSet(dcs, SchemaVersion.V2_20, "НетТакогоНабора"))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("не найден");
    assertThat(Files.readString(dcs, StandardCharsets.UTF_8)).isEqualTo(before);

    // вложенный набор объединения по имени не адресуется: операции только первого уровня
    Path unionDcs = copySchema(UNION_SCHEMA);
    assertThatThrownBy(() -> DcsEdit.removeDataSet(unionDcs, SchemaVersion.V2_20, "ОбъемНенужныхФайлов"))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("не найден");
  }

  @Test
  void renameDataSetCascadesLinks() throws Exception {
    Path dcs = copySchema(LINKED_SCHEMA);
    String before = Files.readString(dcs, StandardCharsets.UTF_8);

    DcsEdit.renameDataSet(dcs, SchemaVersion.V2_20, "ВнешняяТаблица", "ВнешнийНабор");

    // меняются ровно имя набора и ссылка связи; objectName с тем же текстом не трогается
    String after = Files.readString(dcs, StandardCharsets.UTF_8);
    assertThat(after).isEqualTo(before
      .replace("<name>ВнешняяТаблица</name>", "<name>ВнешнийНабор</name>")
      .replace("<sourceDataSet>ВнешняяТаблица</sourceDataSet>", "<sourceDataSet>ВнешнийНабор</sourceDataSet>"));
    assertThat(after).contains("<objectName>ВнешняяТаблица</objectName>");
    Map<String, Object> info = DcsRead.info(dcs, SchemaVersion.V2_20);
    Map<String, Object> link = rows(info, "dataSetLinks").get(0);
    assertThat(link.get("source")).isEqualTo("ВнешнийНабор");
    assertThat(link.get("destination")).isEqualTo("ОсновнойЗапрос");
    assertThat(byKey(rows(info, "dataSets"), "name", "ВнешнийНабор").get("objectName"))
      .isEqualTo("ВнешняяТаблица");

    assertThatThrownBy(() -> DcsEdit.renameDataSet(dcs, SchemaVersion.V2_20, "ВнешнийНабор", "ОсновнойЗапрос"))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("занято");
    assertThatThrownBy(() -> DcsEdit.renameDataSet(dcs, SchemaVersion.V2_20, "НетТакогоНабора", "Новый"))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("не найден");
  }

  @Test
  void setDataSetPropsWritesAutoFillFieldsLikePlatform() throws Exception {
    Path dcs = copySchema(AUTO_FILL_SCHEMA);
    String before = Files.readString(dcs, StandardCharsets.UTF_8);

    // true - значение по умолчанию: элемент снимается, как его не пишет и платформа
    DcsEdit.setDataSetProps(dcs, SchemaVersion.V2_20, "РазмерПриложений", Map.of("autoFillFields", true));
    String withoutElement = Files.readString(dcs, StandardCharsets.UTF_8);
    assertThat(withoutElement)
      .isEqualTo(before.replace("\t\t<autoFillFields>false</autoFillFields>\r\n", ""));
    assertThat(DcsRead.info(dcs, SchemaVersion.V2_20).get("dataSets").toString()).contains("autoFillFields=true");

    // повторное true ничего не меняет: элемента и так нет
    DcsEdit.setDataSetProps(dcs, SchemaVersion.V2_20, "РазмерПриложений", Map.of("autoFillFields", true));
    assertThat(Files.readString(dcs, StandardCharsets.UTF_8)).isEqualTo(withoutElement);

    // false возвращает элемент ровно на его место по xs:sequence - файл совпадает с исходным
    DcsEdit.setDataSetProps(dcs, SchemaVersion.V2_20, "РазмерПриложений", Map.of("autoFillFields", false));
    assertThat(Files.readString(dcs, StandardCharsets.UTF_8)).isEqualTo(before);

    // null равносилен значению по умолчанию: элемент снимается
    Map<String, Object> reset = new HashMap<>();
    reset.put("autoFillFields", null);
    DcsEdit.setDataSetProps(dcs, SchemaVersion.V2_20, "РазмерПриложений", reset);
    assertThat(Files.readString(dcs, StandardCharsets.UTF_8)).isEqualTo(withoutElement);
  }

  @Test
  void setDataSetPropsReplacesSourceAndObjectName() throws Exception {
    Path dcs = copySchema(TWO_OBJECTS_SCHEMA);
    String before = Files.readString(dcs, StandardCharsets.UTF_8);

    DcsEdit.setDataSetProps(dcs, SchemaVersion.V2_20, "ОбластиСПроблемами",
      Map.of("dataSource", "ВторойИсточник", "objectName", "ТаблицаПроблем"));

    String after = Files.readString(dcs, StandardCharsets.UTF_8);
    assertThat(after).isEqualTo(before.replace(
      "<dataSource>ИсточникДанных1</dataSource>\r\n\t\t<objectName>ОбластиСПроблемами</objectName>",
      "<dataSource>ВторойИсточник</dataSource>\r\n\t\t<objectName>ТаблицаПроблем</objectName>"));
    Map<String, Object> set = byKey(rows(DcsRead.info(dcs, SchemaVersion.V2_20), "dataSets"),
      "name", "ОбластиСПроблемами");
    assertThat(set.get("dataSource")).isEqualTo("ВторойИсточник");
    assertThat(set.get("objectName")).isEqualTo("ТаблицаПроблем");
  }

  @Test
  void setDataSetPropsRefusals() throws Exception {
    Path objects = copySchema(TWO_OBJECTS_SCHEMA);
    String before = Files.readString(objects, StandardCharsets.UTF_8);
    // у набора-объекта нет автозаполнения полей
    assertThatThrownBy(() -> DcsEdit.setDataSetProps(
      objects, SchemaVersion.V2_20, "СводнаяИнформация", Map.of("autoFillFields", false)))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("object").hasMessageContaining("autoFillFields");
    // обязательный по формату подэлемент не удаляется и не очищается
    Map<String, Object> drop = new HashMap<>();
    drop.put("objectName", null);
    assertThatThrownBy(() -> DcsEdit.setDataSetProps(objects, SchemaVersion.V2_20, "СводнаяИнформация", drop))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("обязателен");
    assertThatThrownBy(() -> DcsEdit.setDataSetProps(
      objects, SchemaVersion.V2_20, "СводнаяИнформация", Map.of("uuid", "x")))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("Неизвестное свойство");
    assertThatThrownBy(() -> DcsEdit.setDataSetProps(
      objects, SchemaVersion.V2_20, "НетТакогоНабора", Map.of("dataSource", "Источник")))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("не найден");
    assertThat(Files.readString(objects, StandardCharsets.UTF_8)).isEqualTo(before);

    // у набора-запроса нет objectName, у объединения нет источника данных
    Path query = copySchema(AUTO_FILL_SCHEMA);
    assertThatThrownBy(() -> DcsEdit.setDataSetProps(
      query, SchemaVersion.V2_20, "РазмерПриложений", Map.of("objectName", "Объект")))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("query").hasMessageContaining("objectName");
    assertThatThrownBy(() -> DcsEdit.setDataSetProps(
      query, SchemaVersion.V2_20, "РазмерПриложений", Map.of("autoFillFields", "нет")))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("булевым");
    Path union = copySchema(UNION_SCHEMA);
    assertThatThrownBy(() -> DcsEdit.setDataSetProps(
      union, SchemaVersion.V2_20, "НаборДанных1", Map.of("dataSource", "Источник")))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("union").hasMessageContaining("dataSource");
  }

  @Test
  void applyMutationChannelRunsDataSetOps() throws Exception {
    Path dcs = copySchema(LINKED_SCHEMA);

    assertThat(applyMutation(Map.of(
      "op", "cf-dcs-add-data-set", "objectXml", dcs.toString(), "schemaVersion", "V2_20",
      "name", "НаборЧерезКанал", "type", "query", "dataSource", "ИсточникДанных1"))).isZero();
    assertThat(Files.readString(dcs, StandardCharsets.UTF_8)).contains("<name>НаборЧерезКанал</name>");

    assertThat(applyMutation(Map.of(
      "op", "cf-dcs-set-data-set-props", "objectXml", dcs.toString(), "schemaVersion", "V2_20",
      "name", "НаборЧерезКанал", "payloadJson", "{\"autoFillFields\":false}"))).isZero();
    assertThat(Files.readString(dcs, StandardCharsets.UTF_8)).contains("<autoFillFields>false</autoFillFields>");

    assertThat(applyMutation(Map.of(
      "op", "cf-dcs-rename-data-set", "objectXml", dcs.toString(), "schemaVersion", "V2_20",
      "name", "НаборЧерезКанал", "newName", "НаборПереименован"))).isZero();
    assertThat(applyMutation(Map.of(
      "op", "cf-dcs-remove-data-set", "objectXml", dcs.toString(), "schemaVersion", "V2_20",
      "name", "НаборПереименован"))).isZero();
    assertThat(Files.readString(dcs, StandardCharsets.UTF_8)).doesNotContain("НаборПереименован");

    // отказ операции доходит из канала кодом 2, файл не меняется
    String before = Files.readString(dcs, StandardCharsets.UTF_8);
    assertThat(applyMutation(Map.of(
      "op", "cf-dcs-remove-data-set", "objectXml", dcs.toString(), "schemaVersion", "V2_20",
      "name", "ВнешняяТаблица"))).isEqualTo(2);
    assertThat(Files.readString(dcs, StandardCharsets.UTF_8)).isEqualTo(before);
  }

  @Test
  void addFieldInsertsIntoFieldPositionOfSequence() throws Exception {
    Path dcs = copySchema(LINKED_SCHEMA);
    String before = Files.readString(dcs, StandardCharsets.UTF_8);

    DcsEdit.addField(dcs, SchemaVersion.V2_20, "ОсновнойЗапрос",
      Map.of("dataPath", "НоваяКолонка", "field", "НоваяКолонка", "title", "Новая колонка"));

    String after = Files.readString(dcs, StandardCharsets.UTF_8);
    assertSingleEditZone(before, after);
    // блок поля написан как у платформы: табы, CRLF, заголовок v8:item с языком конфигурации
    assertThat(after).contains("\t\t<field xsi:type=\"DataSetFieldField\">\r\n"
      + "\t\t\t<dataPath>НоваяКолонка</dataPath>\r\n"
      + "\t\t\t<field>НоваяКолонка</field>\r\n"
      + "\t\t\t<title xsi:type=\"v8:LocalStringType\">\r\n"
      + "\t\t\t\t<v8:item>\r\n"
      + "\t\t\t\t\t<v8:lang>ru</v8:lang>\r\n"
      + "\t\t\t\t\t<v8:content>Новая колонка</v8:content>\r\n"
      + "\t\t\t\t</v8:item>\r\n"
      + "\t\t\t</title>\r\n"
      + "\t\t</field>\r\n");
    // поле встало в позицию field* последовательности набора: до источника данных и запроса
    List<String> children = dataSetChildNames(after, "ОсновнойЗапрос");
    assertThat(children.subList(children.lastIndexOf("field") + 1, children.size()))
      .containsExactly("dataSource", "query");
    Map<String, Object> field = byKey(
      fields(byKey(rows(DcsRead.info(dcs, SchemaVersion.V2_20), "dataSets"), "name", "ОсновнойЗапрос")),
      "dataPath", "НоваяКолонка");
    assertThat(field.get("kind")).isEqualTo("field");
    assertThat(field.get("field")).isEqualTo("НоваяКолонка");
    assertThat(field.get("title")).isEqualTo("Новая колонка");

    // в наборе без полей поле встаёт сразу после имени; без title заголовок не пишется
    DcsEdit.addDataSet(dcs, SchemaVersion.V2_20, "НовыйЗапрос", "query", "ИсточникДанных1");
    DcsEdit.addField(dcs, SchemaVersion.V2_20, "НовыйЗапрос", Map.of("dataPath", "Поле1", "field", "Поле1"));
    assertThat(dataSetChildNames(Files.readString(dcs, StandardCharsets.UTF_8), "НовыйЗапрос"))
      .containsExactly("name", "field", "dataSource", "query");
    Map<String, Object> bare = byKey(
      fields(byKey(rows(DcsRead.info(dcs, SchemaVersion.V2_20), "dataSets"), "name", "НовыйЗапрос")),
      "dataPath", "Поле1");
    assertThat(bare).doesNotContainKey("title");
  }

  @Test
  void addFieldRefusals() throws Exception {
    Path dcs = copySchema(LINKED_SCHEMA);
    String before = Files.readString(dcs, StandardCharsets.UTF_8);

    assertThatThrownBy(() -> DcsEdit.addField(dcs, SchemaVersion.V2_20, "НетНабора",
      Map.of("dataPath", "Поле", "field", "Поле")))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("не найден");
    assertThatThrownBy(() -> DcsEdit.addField(dcs, SchemaVersion.V2_20, "ОсновнойЗапрос",
      Map.of("dataPath", "Выявлено", "field", "Выявлено")))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("уже есть");
    // обязательные по формату dataPath и field требуют непустой текст
    assertThatThrownBy(() -> DcsEdit.addField(dcs, SchemaVersion.V2_20, "ОсновнойЗапрос",
      Map.of("dataPath", "Новое")))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("field");
    assertThatThrownBy(() -> DcsEdit.addField(dcs, SchemaVersion.V2_20, "ОсновнойЗапрос",
      Map.of("dataPath", " ", "field", "Новое")))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("dataPath");
    assertThatThrownBy(() -> DcsEdit.addField(dcs, SchemaVersion.V2_20, "ОсновнойЗапрос",
      Map.of("dataPath", "Новое", "field", "Новое", "role", Map.of("dimension", true))))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("Неизвестное свойство");

    assertThat(Files.readString(dcs, StandardCharsets.UTF_8)).isEqualTo(before);
  }

  @Test
  void setFieldChangesTitleRoleAndRestrictions() throws Exception {
    Path dcs = copySchema(LINKED_SCHEMA);
    String before = Files.readString(dcs, StandardCharsets.UTF_8);

    // заголовок меняется ровно в своём блоке, формат платформы сохраняется байт в байт
    DcsEdit.setField(dcs, SchemaVersion.V2_20, "ОсновнойЗапрос", "ПравилоПроверкиРекомендации",
      Map.of("title", "Правило"));
    String titled = Files.readString(dcs, StandardCharsets.UTF_8);
    assertThat(titled).isEqualTo(before.replace(
      "<v8:content>Правило проверки рекомендации</v8:content>",
      "<v8:content>Правило</v8:content>"));

    // роль измерение + обязательное заменяет существующую роль целиком
    DcsEdit.setField(dcs, SchemaVersion.V2_20, "ОсновнойЗапрос", "ВидПроверки",
      Map.of("role", Map.of("dimension", true, "required", true)));
    String withRole = Files.readString(dcs, StandardCharsets.UTF_8);
    assertSingleEditZone(titled, withRole);
    assertThat(withRole).contains("\t\t\t<role>\r\n"
      + "\t\t\t\t<dcscom:dimension>true</dcscom:dimension>\r\n"
      + "\t\t\t\t<dcscom:required>true</dcscom:required>\r\n"
      + "\t\t\t</role>");

    // ограничения и заголовок вставляются в голое поле на свои места по xs:sequence
    DcsEdit.setField(dcs, SchemaVersion.V2_20, "ОсновнойЗапрос", "Выявлено", Map.of(
      "title", "Выявлено проблем",
      "useRestriction", Map.of("condition", true, "field", false),
      "attributeUseRestriction", Map.of("field", true, "condition", true, "group", true, "order", true)));
    String restricted = Files.readString(dcs, StandardCharsets.UTF_8);
    assertSingleEditZone(withRole, restricted);
    // невзведённые флаги не пишутся, как их не пишет и платформа
    assertThat(restricted).contains("\t\t\t<useRestriction>\r\n"
      + "\t\t\t\t<condition>true</condition>\r\n"
      + "\t\t\t</useRestriction>");
    assertThat(fieldChildNames(restricted, "ОсновнойЗапрос", "Выявлено"))
      .containsExactly("dataPath", "field", "title", "useRestriction", "attributeUseRestriction");

    // замена пути к данным и имени поля не трогает заголовок
    DcsEdit.setField(dcs, SchemaVersion.V2_20, "ОсновнойЗапрос", "НадписьОшибка",
      Map.of("newDataPath", "НадписьОшибки", "field", "НадписьОшибки"));

    Map<String, Object> info = DcsRead.info(dcs, SchemaVersion.V2_20);
    List<Map<String, Object>> fields = fields(byKey(rows(info, "dataSets"), "name", "ОсновнойЗапрос"));
    assertThat(byKey(fields, "dataPath", "ПравилоПроверкиРекомендации").get("title")).isEqualTo("Правило");
    Map<String, Object> role = nested(byKey(fields, "dataPath", "ВидПроверки"), "role");
    assertThat(role.get("dimension")).isEqualTo(true);
    assertThat(role.get("required")).isEqualTo(true);
    assertThat(role).doesNotContainKeys("account", "balance", "periodNumber");
    Map<String, Object> bare = byKey(fields, "dataPath", "Выявлено");
    assertThat(bare.get("title")).isEqualTo("Выявлено проблем");
    assertThat(nested(bare, "useRestriction"))
      .containsEntry("condition", true)
      .containsEntry("field", false);
    assertThat(nested(bare, "attributeUseRestriction")).containsEntry("order", true);
    Map<String, Object> renamed = byKey(fields, "dataPath", "НадписьОшибки");
    assertThat(renamed.get("field")).isEqualTo("НадписьОшибки");
    assertThat(renamed.get("title")).isEqualTo("[ЗаголовокСкрыт]");
    assertThat(fields.stream().map(row -> row.get("dataPath"))).doesNotContain("НадписьОшибка");
  }

  /**
   * Роль с остатком: имя группы остатка при {@code balance=true} пишется всегда, без имени — пустым
   * самозакрытым элементом сразу после {@code balance}, как его дописывает загрузка в платформу.
   */
  @Test
  void setFieldBalanceRoleWritesBalanceGroupNameAsPlatform() throws Exception {
    Path dcs = copySchema(LINKED_SCHEMA);
    String before = Files.readString(dcs, StandardCharsets.UTF_8);
    DcsEdit.setField(dcs, SchemaVersion.V2_20, "ОсновнойЗапрос", "ВидПроверки",
      Map.of("role", Map.of("balance", true, "balanceType", "ClosingBalance")));
    String balanced = Files.readString(dcs, StandardCharsets.UTF_8);
    assertThat(balanced).isEqualTo(before.replaceFirst(
      "\t\t\t<role>\r\n\t\t\t\t<dcscom:dimension>true</dcscom:dimension>\r\n\t\t\t</role>",
      "\t\t\t<role>\r\n"
        + "\t\t\t\t<dcscom:balance>true</dcscom:balance>\r\n"
        + "\t\t\t\t<dcscom:balanceGroupName/>\r\n"
        + "\t\t\t\t<dcscom:balanceType>ClosingBalance</dcscom:balanceType>\r\n"
        + "\t\t\t</role>"));
    assertThat(DcsXsdValidation.validate(dcs).get("ok")).isEqualTo(true);
    // Чтение пустого имени не отдаёт, а эхо прочитанной роли файл не трогает
    Map<String, Object> role = nested(byKey(fields(byKey(rows(DcsRead.info(dcs, SchemaVersion.V2_20), "dataSets"),
      "name", "ОсновнойЗапрос")), "dataPath", "ВидПроверки"), "role");
    assertThat(role).isEqualTo(Map.of("balance", true, "balanceType", "ClosingBalance"));
    DcsEdit.setField(dcs, SchemaVersion.V2_20, "ОсновнойЗапрос", "ВидПроверки", Map.of("role", role));
    assertThat(Files.readString(dcs, StandardCharsets.UTF_8)).isEqualTo(balanced);
    // Пустое и пробельное имя — тот же пустой элемент
    DcsEdit.setField(dcs, SchemaVersion.V2_20, "ОсновнойЗапрос", "ВидПроверки",
      Map.of("role", Map.of("balance", true, "balanceGroupName", " ", "balanceType", "ClosingBalance")));
    assertThat(Files.readString(dcs, StandardCharsets.UTF_8)).isEqualTo(balanced);

    // Имя группы пишется как задано, на своём месте
    DcsEdit.setField(dcs, SchemaVersion.V2_20, "ОсновнойЗапрос", "ВидПроверки",
      Map.of("role", Map.of("balance", true, "balanceGroupName", "Сумма", "balanceType", "ClosingBalance")));
    assertThat(Files.readString(dcs, StandardCharsets.UTF_8)).isEqualTo(balanced.replace(
      "<dcscom:balanceGroupName/>", "<dcscom:balanceGroupName>Сумма</dcscom:balanceGroupName>"));

    // Снятие balance уносит и пустое имя группы: без balance платформа его не пишет
    DcsEdit.setField(dcs, SchemaVersion.V2_20, "ОсновнойЗапрос", "ВидПроверки",
      Map.of("role", Map.of("balance", false, "balanceGroupName", "", "dimension", true)));
    assertThat(Files.readString(dcs, StandardCharsets.UTF_8)).contains("\t\t\t<role>\r\n"
      + "\t\t\t\t<dcscom:dimension>true</dcscom:dimension>\r\n"
      + "\t\t\t</role>");
    assertThat(Files.readString(dcs, StandardCharsets.UTF_8)).doesNotContain("balanceGroupName");
  }

  /**
   * Роль с balance без имени группы в файле: эхо её и любое эхо поля — байтовый no-op, а любая настоящая
   * правка поля дописывает пустое имя группы, как параметр схемы без value и useRestriction получает
   * недостающие элементы.
   */
  @Test
  void setFieldCompletesBalanceRoleWithoutGroupNameOnlyWithRealEdit() throws Exception {
    Path dcs = copySchema(LINKED_SCHEMA);
    String oldRole = "\t\t\t<role>\r\n"
      + "\t\t\t\t<dcscom:balance>true</dcscom:balance>\r\n"
      + "\t\t\t\t<dcscom:balanceType>ClosingBalance</dcscom:balanceType>\r\n"
      + "\t\t\t</role>";
    String platformRole = oldRole.replace("</dcscom:balance>\r\n",
      "</dcscom:balance>\r\n\t\t\t\t<dcscom:balanceGroupName/>\r\n");
    String old = Files.readString(dcs, StandardCharsets.UTF_8).replaceFirst(
      "\t\t\t<role>\r\n\t\t\t\t<dcscom:dimension>true</dcscom:dimension>\r\n\t\t\t</role>", oldRole);
    assertThat(old).contains(oldRole);
    Files.writeString(dcs, old, StandardCharsets.UTF_8);

    // Эхо прочитанной роли и эхо без роли файл не трогают
    Map<String, Object> role = nested(byKey(fields(byKey(rows(DcsRead.info(dcs, SchemaVersion.V2_20), "dataSets"),
      "name", "ОсновнойЗапрос")), "dataPath", "ВидПроверки"), "role");
    assertThat(role).isEqualTo(Map.of("balance", true, "balanceType", "ClosingBalance"));
    DcsEdit.setField(dcs, SchemaVersion.V2_20, "ОсновнойЗапрос", "ВидПроверки", Map.of("role", role));
    DcsEdit.setField(dcs, SchemaVersion.V2_20, "ОсновнойЗапрос", "ВидПроверки",
      Map.of("role", role, "field", "ВидПроверки"));
    assertThat(Files.readString(dcs, StandardCharsets.UTF_8)).isEqualTo(old);

    // Настоящая правка заголовка вместе с эхом роли и без него дописывает пустое имя группы
    String titled = old.replace("\t\t\t<field>ВидПроверки</field>\r\n" + oldRole,
      "\t\t\t<field>ВидПроверки</field>\r\n"
        + "\t\t\t<title xsi:type=\"v8:LocalStringType\">\r\n"
        + "\t\t\t\t<v8:item>\r\n"
        + "\t\t\t\t\t<v8:lang>ru</v8:lang>\r\n"
        + "\t\t\t\t\t<v8:content>Вид</v8:content>\r\n"
        + "\t\t\t\t</v8:item>\r\n"
        + "\t\t\t</title>\r\n" + platformRole);
    assertThat(titled).isNotEqualTo(old);
    DcsEdit.setField(dcs, SchemaVersion.V2_20, "ОсновнойЗапрос", "ВидПроверки", Map.of("title", "Вид"));
    assertThat(Files.readString(dcs, StandardCharsets.UTF_8)).isEqualTo(titled);
    assertThat(DcsXsdValidation.validate(dcs).get("ok")).isEqualTo(true);
    Files.writeString(dcs, old, StandardCharsets.UTF_8);
    DcsEdit.setField(dcs, SchemaVersion.V2_20, "ОсновнойЗапрос", "ВидПроверки",
      Map.of("title", "Вид", "role", role));
    assertThat(Files.readString(dcs, StandardCharsets.UTF_8)).isEqualTo(titled);

    // Настоящая правка роли пишет её целиком в форме платформы
    Files.writeString(dcs, old, StandardCharsets.UTF_8);
    DcsEdit.setField(dcs, SchemaVersion.V2_20, "ОсновнойЗапрос", "ВидПроверки",
      Map.of("role", Map.of("balance", true, "balanceType", "OpeningBalance")));
    assertThat(Files.readString(dcs, StandardCharsets.UTF_8))
      .isEqualTo(old.replace(oldRole, platformRole.replace("ClosingBalance", "OpeningBalance")));
  }

  @Test
  void setFieldRemovesOptionalElements() throws Exception {
    Path dcs = copySchema(LINKED_SCHEMA);
    String before = Files.readString(dcs, StandardCharsets.UTF_8);

    // null снимает необязательные элементы целиком
    Map<String, Object> drop = new HashMap<>();
    drop.put("title", null);
    drop.put("role", null);
    DcsEdit.setField(dcs, SchemaVersion.V2_20, "ОсновнойЗапрос", "ПравилоПроверки", drop);
    String after = Files.readString(dcs, StandardCharsets.UTF_8);
    assertSingleEditZone(before, after);
    assertThat(fieldChildNames(after, "ОсновнойЗапрос", "ПравилоПроверки"))
      .containsExactly("dataPath", "field");
    Map<String, Object> field = byKey(
      fields(byKey(rows(DcsRead.info(dcs, SchemaVersion.V2_20), "dataSets"), "name", "ОсновнойЗапрос")),
      "dataPath", "ПравилоПроверки");
    assertThat(field).doesNotContainKeys("title", "titleLangs", "role");

    // ограничение без единого взведённого флага равносильно снятию элемента
    DcsEdit.setField(dcs, SchemaVersion.V2_20, "ВнешняяТаблица", "ОбработчикПереходаКИсправлению",
      Map.of("attributeUseRestriction", Map.of("field", false, "condition", false)));
    String unrestricted = Files.readString(dcs, StandardCharsets.UTF_8);
    // соседние по последовательности presentationExpression и valueType не тронуты
    assertThat(fieldChildNames(unrestricted, "ВнешняяТаблица", "ОбработчикПереходаКИсправлению"))
      .containsExactly("dataPath", "field", "title", "presentationExpression", "valueType");
  }

  @Test
  void setFieldAndRemoveFieldRefusals() throws Exception {
    Path dcs = copySchema(LINKED_SCHEMA);
    String before = Files.readString(dcs, StandardCharsets.UTF_8);

    assertThatThrownBy(() -> DcsEdit.setField(
      dcs, SchemaVersion.V2_20, "НетНабора", "Выявлено", Map.of("title", "Х")))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("Набор данных не найден");
    assertThatThrownBy(() -> DcsEdit.setField(
      dcs, SchemaVersion.V2_20, "ОсновнойЗапрос", "НетПоля", Map.of("title", "Х")))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("Поле набора данных не найдено");
    assertThatThrownBy(() -> DcsEdit.removeField(dcs, SchemaVersion.V2_20, "ОсновнойЗапрос", "НетПоля"))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("не найдено");
    assertThatThrownBy(() -> DcsEdit.setField(
      dcs, SchemaVersion.V2_20, "ОсновнойЗапрос", "Выявлено", Map.of("newDataPath", "ВидПроверки")))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("занят");
    assertThatThrownBy(() -> DcsEdit.setField(
      dcs, SchemaVersion.V2_20, "ОсновнойЗапрос", "Выявлено", Map.of("field", " ")))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("field");
    assertThatThrownBy(() -> DcsEdit.setField(
      dcs, SchemaVersion.V2_20, "ОсновнойЗапрос", "Выявлено", Map.of("uuid", "x")))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("Неизвестное свойство");
    assertThatThrownBy(() -> DcsEdit.setField(
      dcs, SchemaVersion.V2_20, "ОсновнойЗапрос", "Выявлено", Map.of("useRestriction", Map.of("total", true))))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("Неизвестный флаг");
    assertThatThrownBy(() -> DcsEdit.setField(
      dcs, SchemaVersion.V2_20, "ОсновнойЗапрос", "Выявлено", Map.of("useRestriction", Map.of("field", "да"))))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("булевым");
    assertThatThrownBy(() -> DcsEdit.setField(
      dcs, SchemaVersion.V2_20, "ОсновнойЗапрос", "Выявлено", Map.of("role", Map.of("цвет", true))))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("Неизвестное свойство роли");
    assertThatThrownBy(() -> DcsEdit.setField(
      dcs, SchemaVersion.V2_20, "ОсновнойЗапрос", "Выявлено", Map.of("role", Map.of("dimension", "да"))))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("булевым");
    assertThatThrownBy(() -> DcsEdit.setField(
      dcs, SchemaVersion.V2_20, "ОсновнойЗапрос", "Выявлено", Map.of("role", Map.of("periodType", "Годовой"))))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("одним из");
    assertThatThrownBy(() -> DcsEdit.setField(
      dcs, SchemaVersion.V2_20, "ОсновнойЗапрос", "Выявлено", Map.of("role", Map.of("periodNumber", 1.5))))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("целым");
    assertThat(Files.readString(dcs, StandardCharsets.UTF_8)).isEqualTo(before);

    // папки полей и поля-вложенные наборы не правятся
    Path doc = copySchema(FOLDER_SCHEMA);
    String docBefore = Files.readString(doc, StandardCharsets.UTF_8);
    assertThatThrownBy(() -> DcsEdit.setField(
      doc, SchemaVersion.V2_20, "НаборДанных1", "Итоги", Map.of("title", "Итого")))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("папка");
    assertThatThrownBy(() -> DcsEdit.setField(
      doc, SchemaVersion.V2_20, "НаборДанных1", "Товары", Map.of("title", "Товары документа")))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("вложенный набор");
    assertThatThrownBy(() -> DcsEdit.removeField(doc, SchemaVersion.V2_20, "НаборДанных1", "Итоги"))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("папка");
    assertThat(Files.readString(doc, StandardCharsets.UTF_8)).isEqualTo(docBefore);
  }

  @Test
  void removeFieldDeletesWholeElement() throws Exception {
    Path dcs = copySchema(LINKED_SCHEMA);
    String before = Files.readString(dcs, StandardCharsets.UTF_8);
    int count = fields(byKey(rows(DcsRead.info(dcs, SchemaVersion.V2_20), "dataSets"), "name", "ОсновнойЗапрос"))
      .size();

    DcsEdit.removeField(dcs, SchemaVersion.V2_20, "ОсновнойЗапрос", "НадписьРекомендации");

    String after = Files.readString(dcs, StandardCharsets.UTF_8);
    assertSingleEditZone(before, after);
    // элемент поля вырезан целиком, а упоминания пути в вариантах настроек не тронуты
    assertThat(after).doesNotContain("<dataPath>НадписьРекомендации</dataPath>");
    assertThat(after).contains("НадписьРекомендации");
    List<Map<String, Object>> fields =
      fields(byKey(rows(DcsRead.info(dcs, SchemaVersion.V2_20), "dataSets"), "name", "ОсновнойЗапрос"));
    assertThat(fields).hasSize(count - 1);
    assertThat(fields.stream().map(row -> row.get("dataPath"))).doesNotContain("НадписьРекомендации");
  }

  @Test
  void applyMutationChannelRunsFieldOps() throws Exception {
    Path dcs = copySchema(LINKED_SCHEMA);

    assertThat(applyMutation(Map.of(
      "op", "cf-dcs-add-field", "objectXml", dcs.toString(), "schemaVersion", "V2_20",
      "dataSet", "ОсновнойЗапрос",
      "payloadJson", "{\"dataPath\":\"КаналПоле\",\"field\":\"КаналПоле\",\"title\":\"Канал\"}"))).isZero();
    assertThat(applyMutation(Map.of(
      "op", "cf-dcs-set-field", "objectXml", dcs.toString(), "schemaVersion", "V2_20",
      "dataSet", "ОсновнойЗапрос", "dataPath", "КаналПоле",
      "payloadJson",
      "{\"useRestriction\":{\"order\":true},\"role\":{\"periodNumber\":1,\"periodType\":\"Main\"}}"))).isZero();

    String text = Files.readString(dcs, StandardCharsets.UTF_8);
    // номер периода из JSON-числа пишется целым, роль - детьми пространства dcscom
    assertThat(text).contains("<dcscom:periodNumber>1</dcscom:periodNumber>\r\n"
      + "\t\t\t\t<dcscom:periodType>Main</dcscom:periodType>");
    assertThat(fieldChildNames(text, "ОсновнойЗапрос", "КаналПоле"))
      .containsExactly("dataPath", "field", "title", "useRestriction", "role");
    Map<String, Object> field = byKey(
      fields(byKey(rows(DcsRead.info(dcs, SchemaVersion.V2_20), "dataSets"), "name", "ОсновнойЗапрос")),
      "dataPath", "КаналПоле");
    assertThat(field.get("title")).isEqualTo("Канал");
    assertThat(nested(field, "role").get("periodType")).isEqualTo("Main");

    assertThat(applyMutation(Map.of(
      "op", "cf-dcs-remove-field", "objectXml", dcs.toString(), "schemaVersion", "V2_20",
      "dataSet", "ОсновнойЗапрос", "dataPath", "КаналПоле"))).isZero();
    assertThat(Files.readString(dcs, StandardCharsets.UTF_8)).doesNotContain("КаналПоле");

    // отказ операции доходит из канала кодом 2: поле-папка не правится, файл не меняется
    Path doc = copySchema(FOLDER_SCHEMA);
    String before = Files.readString(doc, StandardCharsets.UTF_8);
    assertThat(applyMutation(Map.of(
      "op", "cf-dcs-set-field", "objectXml", doc.toString(), "schemaVersion", "V2_20",
      "dataSet", "НаборДанных1", "dataPath", "Итоги",
      "payloadJson", "{\"title\":\"Итого\"}"))).isEqualTo(2);
    assertThat(Files.readString(doc, StandardCharsets.UTF_8)).isEqualTo(before);
  }

  @Test
  void addLinkInsertsIntoLinkPositionOfSequence() throws Exception {
    // в схеме без связей новая связь встаёт после наборов данных, до вычисляемых полей
    Path dcs = copySchema(TWO_OBJECTS_SCHEMA);
    String before = Files.readString(dcs, StandardCharsets.UTF_8);

    Map<String, Object> payload = new HashMap<>();
    payload.put("sourceDataSet", "ОбластиСПроблемами");
    payload.put("destinationDataSet", "СводнаяИнформация");
    payload.put("sourceExpression", "ОбластьДанных");
    payload.put("destinationExpression", "Проблемы");
    payload.put("parameter", "ОбластьДанных");
    payload.put("parameterListAllowed", true);
    payload.put("linkConditionExpression", "Проблемы > 0");
    payload.put("startExpression", "Неопределено");
    // обязательность связи платформа не хранит: false не пишется
    payload.put("required", false);
    DcsEdit.addLink(dcs, SchemaVersion.V2_20, payload);

    String after = Files.readString(dcs, StandardCharsets.UTF_8);
    assertSingleEditZone(before, after);
    // блок связи написан как у платформы: табы, CRLF, подэлементы по xs:sequence, > экранирован
    assertThat(after).contains("\t<dataSetLink>\r\n"
      + "\t\t<sourceDataSet>ОбластиСПроблемами</sourceDataSet>\r\n"
      + "\t\t<destinationDataSet>СводнаяИнформация</destinationDataSet>\r\n"
      + "\t\t<sourceExpression>ОбластьДанных</sourceExpression>\r\n"
      + "\t\t<destinationExpression>Проблемы</destinationExpression>\r\n"
      + "\t\t<parameter>ОбластьДанных</parameter>\r\n"
      + "\t\t<parameterListAllowed>true</parameterListAllowed>\r\n"
      + "\t\t<linkConditionExpression>Проблемы &gt; 0</linkConditionExpression>\r\n"
      + "\t\t<startExpression>Неопределено</startExpression>\r\n"
      + "\t</dataSetLink>\r\n");
    // физика файла платформы: BOM, CRLF и конец без перевода строки не сломаны
    assertThat(after).startsWith("﻿<?xml");
    assertThat(after).endsWith("</DataCompositionSchema>");
    // порядок детей корня соответствует последовательности из XSD
    List<String> order = schemaSequence();
    List<String> roots = XmlLines.children(after, List.of("DataCompositionSchema")).stream()
      .map(XmlLines.Node::name)
      .toList();
    assertThat(roots.stream().map(order::indexOf).toList()).isSorted();
    assertThat(roots).containsSubsequence("dataSet", "dataSet", "dataSetLink", "calculatedField");
    Map<String, Object> link = rows(DcsRead.info(dcs, SchemaVersion.V2_20), "dataSetLinks").get(0);
    assertThat(link.get("source")).isEqualTo("ОбластиСПроблемами");
    assertThat(link.get("destination")).isEqualTo("СводнаяИнформация");
    assertThat(link.get("sourceExpression")).isEqualTo("ОбластьДанных");
    assertThat(link.get("destinationExpression")).isEqualTo("Проблемы");
    assertThat(link.get("parameter")).isEqualTo("ОбластьДанных");
    assertThat(link.get("parameterListAllowed")).isEqualTo(true);
    assertThat(link.get("linkConditionExpression")).isEqualTo("Проблемы > 0");
    assertThat(link.get("startExpression")).isEqualTo("Неопределено");
    assertThat(link).doesNotContainKey("required");

    // в схеме со связью минимальная связь встаёт после существующей, незаданное не пишется
    Path linked = copySchema(LINKED_SCHEMA);
    DcsEdit.addLink(linked, SchemaVersion.V2_20, Map.of(
      "sourceDataSet", "ОсновнойЗапрос", "destinationDataSet", "ВнешняяТаблица",
      "sourceExpression", "ИдентификаторПроверки", "destinationExpression", "Идентификатор"));
    List<Map<String, Object>> links = rows(DcsRead.info(linked, SchemaVersion.V2_20), "dataSetLinks");
    assertThat(links).hasSize(2);
    assertThat(links.get(0).get("source")).isEqualTo("ВнешняяТаблица");
    Map<String, Object> minimal = links.get(1);
    assertThat(minimal.get("source")).isEqualTo("ОсновнойЗапрос");
    assertThat(minimal.get("destination")).isEqualTo("ВнешняяТаблица");
    assertThat(minimal).doesNotContainKeys(
      "parameter", "parameterListAllowed", "linkConditionExpression", "startExpression", "required");
    assertThat(linkChildNames(Files.readString(linked, StandardCharsets.UTF_8), 1))
      .containsExactly("sourceDataSet", "destinationDataSet", "sourceExpression", "destinationExpression");
  }

  @Test
  void addLinkRefusals() throws Exception {
    Path dcs = copySchema(LINKED_SCHEMA);
    String before = Files.readString(dcs, StandardCharsets.UTF_8);

    // наборы связи обязаны существовать на верхнем уровне схемы
    assertThatThrownBy(() -> DcsEdit.addLink(dcs, SchemaVersion.V2_20, Map.of(
      "sourceDataSet", "НетНабора", "destinationDataSet", "ОсновнойЗапрос",
      "sourceExpression", "Идентификатор", "destinationExpression", "Идентификатор")))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("Набор данных не найден");
    assertThatThrownBy(() -> DcsEdit.addLink(dcs, SchemaVersion.V2_20, Map.of(
      "sourceDataSet", "ОсновнойЗапрос", "destinationDataSet", "НетНабора",
      "sourceExpression", "Идентификатор", "destinationExpression", "Идентификатор")))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("Набор данных не найден");
    // обязательные по формату подэлементы требуют непустой текст
    assertThatThrownBy(() -> DcsEdit.addLink(dcs, SchemaVersion.V2_20, Map.of(
      "sourceDataSet", "ОсновнойЗапрос", "destinationDataSet", "ВнешняяТаблица",
      "sourceExpression", "Идентификатор")))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("destinationExpression");
    assertThatThrownBy(() -> DcsEdit.addLink(dcs, SchemaVersion.V2_20, Map.of("sourceDataSet", "ОсновнойЗапрос")))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("destinationDataSet");
    assertThatThrownBy(() -> DcsEdit.addLink(dcs, SchemaVersion.V2_20, Map.of(
      "sourceDataSet", "ОсновнойЗапрос", "destinationDataSet", "ВнешняяТаблица",
      "sourceExpression", "Идентификатор", "destinationExpression", "Идентификатор",
      "uuid", "x")))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("Неизвестное свойство");
    assertThatThrownBy(() -> DcsEdit.addLink(dcs, SchemaVersion.V2_20, Map.of(
      "sourceDataSet", "ОсновнойЗапрос", "destinationDataSet", "ВнешняяТаблица",
      "sourceExpression", "Идентификатор", "destinationExpression", "Идентификатор",
      "required", "да")))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("булевым");
    assertThatThrownBy(() -> DcsEdit.addLink(dcs, SchemaVersion.V2_20, Map.of(
      "sourceDataSet", "ОсновнойЗапрос", "destinationDataSet", "ВнешняяТаблица",
      "sourceExpression", "Идентификатор", "destinationExpression", "Идентификатор",
      "parameter", 5)))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("строкой");
    assertThatThrownBy(() -> DcsEdit.addLink(dcs, SchemaVersion.V2_20, Map.of()))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("payload");

    assertThat(Files.readString(dcs, StandardCharsets.UTF_8)).isEqualTo(before);
  }

  @Test
  void setLinkEditsSubElementsBySequence() throws Exception {
    Path dcs = copySchema(MULTI_LINK_SCHEMA);
    String before = Files.readString(dcs, StandardCharsets.UTF_8);

    // необязательные подэлементы вставляются в связь без них на свои места по xs:sequence;
    // required: false у связи без флага файл не трогает
    DcsEdit.setLink(dcs, SchemaVersion.V2_20, 1, Map.of(
      "parameter", "Родитель",
      "parameterListAllowed", true,
      "linkConditionExpression", "Условие",
      "startExpression", "Неопределено",
      "required", false));
    String inserted = Files.readString(dcs, StandardCharsets.UTF_8);
    assertSingleEditZone(before, inserted);
    assertThat(inserted).contains("\t\t<parameter>Родитель</parameter>\r\n"
      + "\t\t<parameterListAllowed>true</parameterListAllowed>\r\n"
      + "\t\t<linkConditionExpression>Условие</linkConditionExpression>\r\n"
      + "\t\t<startExpression>Неопределено</startExpression>\r\n"
      + "\t</dataSetLink>");
    assertThat(linkChildNames(inserted, 1)).containsExactly(
      "sourceDataSet", "destinationDataSet", "sourceExpression", "destinationExpression",
      "parameter", "parameterListAllowed", "linkConditionExpression", "startExpression");

    // замена обязательного выражения и снятие необязательного null-значением - у связи 0
    Map<String, Object> drop = new HashMap<>();
    drop.put("sourceExpression", "НовыйКлюч");
    drop.put("startExpression", null);
    DcsEdit.setLink(dcs, SchemaVersion.V2_20, 0, drop);
    assertThat(linkChildNames(Files.readString(dcs, StandardCharsets.UTF_8), 0))
      .containsExactly("sourceDataSet", "destinationDataSet", "sourceExpression", "destinationExpression");

    // наборы связи заменяются на существующие
    DcsEdit.setLink(dcs, SchemaVersion.V2_20, 2, Map.of(
      "sourceDataSet", "ПраваПользователей", "destinationDataSet", "НастройкиПравЛегенда"));

    List<Map<String, Object>> links = rows(DcsRead.info(dcs, SchemaVersion.V2_20), "dataSetLinks");
    assertThat(links).hasSize(4);
    Map<String, Object> first = links.get(0);
    assertThat(first.get("sourceExpression")).isEqualTo("НовыйКлюч");
    assertThat(first).doesNotContainKey("startExpression");
    Map<String, Object> second = links.get(1);
    // индексная адресация не задела соседнюю связь с тем же исходным выражением
    assertThat(second.get("sourceExpression")).isEqualTo("ВладелецИлиПользовательНастроек");
    assertThat(second.get("parameter")).isEqualTo("Родитель");
    assertThat(second.get("parameterListAllowed")).isEqualTo(true);
    assertThat(second.get("linkConditionExpression")).isEqualTo("Условие");
    assertThat(second.get("startExpression")).isEqualTo("Неопределено");
    assertThat(second).doesNotContainKey("required");
    Map<String, Object> third = links.get(2);
    assertThat(third.get("source")).isEqualTo("ПраваПользователей");
    assertThat(third.get("destination")).isEqualTo("НастройкиПравЛегенда");
    assertThat(third.get("sourceExpression")).isEqualTo("ВладелецНастроек");

    // пустой набор правок ничего не меняет
    String settled = Files.readString(dcs, StandardCharsets.UTF_8);
    DcsEdit.setLink(dcs, SchemaVersion.V2_20, 0, Map.of());
    assertThat(Files.readString(dcs, StandardCharsets.UTF_8)).isEqualTo(settled);
  }

  @Test
  void removeLinkDeletesWholeElementByIndex() throws Exception {
    Path dcs = copySchema(MULTI_LINK_SCHEMA);
    String before = Files.readString(dcs, StandardCharsets.UTF_8);
    assertThat(rows(DcsRead.info(dcs, SchemaVersion.V2_20), "dataSetLinks")).hasSize(4);

    DcsEdit.removeLink(dcs, SchemaVersion.V2_20, 0);

    String after = Files.readString(dcs, StandardCharsets.UTF_8);
    assertSingleEditZone(before, after);
    List<Map<String, Object>> links = rows(DcsRead.info(dcs, SchemaVersion.V2_20), "dataSetLinks");
    assertThat(links).hasSize(3);
    assertThat(links.stream().map(row -> row.get("destinationExpression")))
      .doesNotContain("РодительВладельцаИлиПользователяНастроек");

    // после удаления номера связей сдвигаются: последняя - под номером 2
    DcsEdit.removeLink(dcs, SchemaVersion.V2_20, 2);
    links = rows(DcsRead.info(dcs, SchemaVersion.V2_20), "dataSetLinks");
    assertThat(links.stream().map(row -> row.get("sourceExpression")))
      .containsExactly("ВладелецИлиПользовательНастроек", "ВладелецНастроек");
  }

  @Test
  void setLinkAndRemoveLinkRefusals() throws Exception {
    Path dcs = copySchema(MULTI_LINK_SCHEMA);
    String before = Files.readString(dcs, StandardCharsets.UTF_8);

    // связей в схеме четыре: номера за пределами файла - отказ
    assertThatThrownBy(() -> DcsEdit.setLink(dcs, SchemaVersion.V2_20, 4, Map.of("required", true)))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("не найдена");
    assertThatThrownBy(() -> DcsEdit.setLink(dcs, SchemaVersion.V2_20, -1, Map.of("required", true)))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("не найдена");
    assertThatThrownBy(() -> DcsEdit.removeLink(dcs, SchemaVersion.V2_20, 4))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("не найдена");
    // ключи правок - имена подэлементов формата, а не поля ответа cf-dcs-info
    assertThatThrownBy(() -> DcsEdit.setLink(dcs, SchemaVersion.V2_20, 0, Map.of("source", "х")))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("Неизвестное свойство");
    assertThatThrownBy(() -> DcsEdit.setLink(dcs, SchemaVersion.V2_20, 0, Map.of("sourceDataSet", "НетНабора")))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("Набор данных не найден");
    assertThatThrownBy(() -> DcsEdit.setLink(dcs, SchemaVersion.V2_20, 0, Map.of("sourceExpression", " ")))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("sourceExpression");
    assertThatThrownBy(() -> DcsEdit.setLink(dcs, SchemaVersion.V2_20, 0, Map.of("required", "да")))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("булевым");
    assertThatThrownBy(() -> DcsEdit.setLink(dcs, SchemaVersion.V2_20, 0, Map.of("parameter", 5)))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("строкой");

    assertThat(Files.readString(dcs, StandardCharsets.UTF_8)).isEqualTo(before);
  }

  @Test
  void applyMutationChannelRunsLinkOps() throws Exception {
    Path dcs = copySchema(TWO_OBJECTS_SCHEMA);

    assertThat(applyMutation(Map.of(
      "op", "cf-dcs-add-link", "objectXml", dcs.toString(), "schemaVersion", "V2_20",
      "payloadJson", "{\"sourceDataSet\":\"ОбластиСПроблемами\",\"destinationDataSet\":\"СводнаяИнформация\","
        + "\"sourceExpression\":\"ОбластьДанных\",\"destinationExpression\":\"Проблемы\"}"))).isZero();
    assertThat(Files.readString(dcs, StandardCharsets.UTF_8))
      .contains("<sourceDataSet>ОбластиСПроблемами</sourceDataSet>");

    // порядковый номер связи приходит из JSON числом
    assertThat(applyMutation(Map.of(
      "op", "cf-dcs-set-link", "objectXml", dcs.toString(), "schemaVersion", "V2_20",
      "index", 0, "payloadJson", "{\"required\":false,\"startExpression\":\"Неопределено\"}"))).isZero();
    String text = Files.readString(dcs, StandardCharsets.UTF_8);
    assertThat(text).contains("\t\t<startExpression>Неопределено</startExpression>\r\n\t</dataSetLink>");
    Map<String, Object> link = rows(DcsRead.info(dcs, SchemaVersion.V2_20), "dataSetLinks").get(0);
    assertThat(link.get("startExpression")).isEqualTo("Неопределено");
    assertThat(link).doesNotContainKey("required");

    // required: true платформа не хранит - отказ кодом 2, файл не тронут
    assertThat(applyMutation(Map.of(
      "op", "cf-dcs-set-link", "objectXml", dcs.toString(), "schemaVersion", "V2_20",
      "index", 0, "payloadJson", "{\"required\":true}"))).isEqualTo(2);
    assertThat(Files.readString(dcs, StandardCharsets.UTF_8)).isEqualTo(text);

    assertThat(applyMutation(Map.of(
      "op", "cf-dcs-remove-link", "objectXml", dcs.toString(), "schemaVersion", "V2_20",
      "index", 0))).isZero();
    assertThat(Files.readString(dcs, StandardCharsets.UTF_8)).doesNotContain("<dataSetLink>");

    // отказ операции доходит из канала кодом 2: связей не осталось, без номера связи - тоже отказ
    String before = Files.readString(dcs, StandardCharsets.UTF_8);
    assertThat(applyMutation(Map.of(
      "op", "cf-dcs-remove-link", "objectXml", dcs.toString(), "schemaVersion", "V2_20",
      "index", 0))).isEqualTo(2);
    assertThat(applyMutation(Map.of(
      "op", "cf-dcs-set-link", "objectXml", dcs.toString(), "schemaVersion", "V2_20",
      "payloadJson", "{\"required\":true}"))).isEqualTo(2);
    assertThat(Files.readString(dcs, StandardCharsets.UTF_8)).isEqualTo(before);
  }

  @Test
  void linkRequiredTrueIsRefusedAndWrittenFlagSurvivesEcho() throws Exception {
    // Обязательность связи платформа не хранит: загрузка отбрасывает записанный <required>true</required>
    // при любом составе связи
    Path dcs = copySchema(MULTI_LINK_SCHEMA);
    String before = Files.readString(dcs, StandardCharsets.UTF_8);

    // новая связь и связь без флага: true - отказ без записи, в том числе кодом 2 канала
    Map<String, Object> link = new HashMap<>(Map.of(
      "sourceDataSet", "ПраваПользователей", "destinationDataSet", "НастройкиПравЛегенда",
      "sourceExpression", "ВладелецНастроек", "destinationExpression", "ВладелецНастроек", "required", true));
    assertThatThrownBy(() -> DcsEdit.addLink(dcs, SchemaVersion.V2_20, link))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("required=true")
      .hasMessageContaining("не хранит")
      .hasMessageContaining("Снимите флаг");
    assertThatThrownBy(() -> DcsEdit.setLink(dcs, SchemaVersion.V2_20, 1, Map.of("required", true)))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("required=true");
    assertThat(applyMutation(Map.of("op", "cf-dcs-add-link", "objectXml", dcs.toString(), "schemaVersion", "V2_20",
      "payloadJson", new Gson().toJson(link)))).isEqualTo(2);
    assertThat(applyMutation(Map.of("op", "cf-dcs-set-link", "objectXml", dcs.toString(), "schemaVersion", "V2_20",
      "index", 1, "payloadJson", "{\"startExpression\":\"Неопределено\",\"required\":true}"))).isEqualTo(2);
    // пакет: отказ второй операции отменяет и первую, диск не тронут
    assertThat(applyMutation(Map.of("op", "batch", "operations", List.of(
      Map.of("op", "cf-dcs-set-link", "objectXml", dcs.toString(), "schemaVersion", "V2_20",
        "index", 0, "payloadJson", "{\"startExpression\":\"Неопределено\"}"),
      Map.of("op", "cf-dcs-set-link", "objectXml", dcs.toString(), "schemaVersion", "V2_20",
        "index", 1, "payloadJson", "{\"required\":true}"))))).isEqualTo(2);
    // false и null у связи без флага ничего не меняют
    Map<String, Object> cleared = new HashMap<>();
    cleared.put("required", null);
    DcsEdit.setLink(dcs, SchemaVersion.V2_20, 1, Map.of("required", false));
    DcsEdit.setLink(dcs, SchemaVersion.V2_20, 1, cleared);
    assertThat(Files.readString(dcs, StandardCharsets.UTF_8)).isEqualTo(before);

    // флаг, уже записанный в файле: читается, эхо и правка соседнего свойства его не снимают, повтор
    // true - тоже эхо
    String flagged = withLinkLine(before, 1, "\t\t<required>true</required>\r\n");
    Files.writeString(dcs, flagged, StandardCharsets.UTF_8);
    Map<String, Object> read = rows(DcsRead.info(dcs, SchemaVersion.V2_20), "dataSetLinks").get(1);
    assertThat(read.get("required")).isEqualTo(true);
    assertThat(applyMutation(Map.of("op", "cf-dcs-set-link", "objectXml", dcs.toString(), "schemaVersion", "V2_20",
      "index", 1, "payloadJson", new Gson().toJson(linkProps(read))))).isZero();
    DcsEdit.setLink(dcs, SchemaVersion.V2_20, 1, Map.of("required", true));
    assertThat(Files.readString(dcs, StandardCharsets.UTF_8)).isEqualTo(flagged);
    DcsEdit.setLink(dcs, SchemaVersion.V2_20, 1, Map.of("required", true, "startExpression", "Неопределено"));
    String started = Files.readString(dcs, StandardCharsets.UTF_8);
    assertThat(started).isEqualTo(withLinkLine(before, 1,
      "\t\t<startExpression>Неопределено</startExpression>\r\n\t\t<required>true</required>\r\n"));
    // false снимает флаг целой строкой
    DcsEdit.setLink(dcs, SchemaVersion.V2_20, 1, Map.of("required", false, "startExpression", ""));
    assertThat(Files.readString(dcs, StandardCharsets.UTF_8)).isEqualTo(before);

    // явный false в файле: эхо false - без записи, true - отказ, null - снимает
    String explicitFalse = withLinkLine(before, 1, "\t\t<required>false</required>\r\n");
    Files.writeString(dcs, explicitFalse, StandardCharsets.UTF_8);
    DcsEdit.setLink(dcs, SchemaVersion.V2_20, 1,
      linkProps(rows(DcsRead.info(dcs, SchemaVersion.V2_20), "dataSetLinks").get(1)));
    DcsEdit.setLink(dcs, SchemaVersion.V2_20, 1, Map.of("required", false));
    assertThat(Files.readString(dcs, StandardCharsets.UTF_8)).isEqualTo(explicitFalse);
    assertThatThrownBy(() -> DcsEdit.setLink(dcs, SchemaVersion.V2_20, 1, Map.of("required", true)))
      .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("required=true");
    assertThat(Files.readString(dcs, StandardCharsets.UTF_8)).isEqualTo(explicitFalse);
    DcsEdit.setLink(dcs, SchemaVersion.V2_20, 1, cleared);
    assertThat(Files.readString(dcs, StandardCharsets.UTF_8)).isEqualTo(before);
  }

  @Test
  void linkRequiredFalseAndNullAreByteNoOpOnBothCorpora() throws Exception {
    // в корпусах required у связей нет ни разу (по 15 связей ssl31 и EDT-пары): снятие ничего не меняет,
    // а true - отказ без записи
    List<Path> designer = schemaFiles(Ssl31SubmodulePaths.projectRoot().resolve("src"));
    List<Path> edt = schemaFiles(Path.of(System.getProperty("fixtures.ssl31edt.root")));
    assertThat(List.of(clearLinkRequired(designer, "Template.xml"), clearLinkRequired(edt, "Template.dcs")))
      .containsExactly(15, 15);
  }

  /**
   * Снятие обязательности у каждой связи схем: {@code false} и {@code null} - байтовый no-op,
   * {@code true} - отказ без записи.
   *
   * @return число связей
   */
  private int clearLinkRequired(List<Path> schemas, String fileName) throws Exception {
    int links = 0;
    int index = 0;
    for (Path schema : schemas) {
      int count = rows(DcsRead.info(schema, SchemaVersion.V2_20), "dataSetLinks").size();
      if (count == 0) {
        continue;
      }
      Path copy = Files.createTempDirectory(tempDir, "links" + index++).resolve(fileName);
      Files.copy(schema, copy);
      byte[] original = Files.readAllBytes(copy);
      for (int i = 0; i < count; i++) {
        int link = i;
        Map<String, Object> cleared = new HashMap<>();
        cleared.put("required", null);
        DcsEdit.setLink(copy, SchemaVersion.V2_20, link, Map.of("required", false));
        DcsEdit.setLink(copy, SchemaVersion.V2_20, link, cleared);
        assertThatThrownBy(() -> DcsEdit.setLink(copy, SchemaVersion.V2_20, link, Map.of("required", true)))
          .as(schema + ": связь " + link).isInstanceOf(IllegalArgumentException.class);
        assertThat(Files.readAllBytes(copy)).as(schema + ": связь " + link).isEqualTo(original);
        links++;
      }
    }
    return links;
  }

  @Test
  void addLinkWithParameterWritesListFlagFalseAsPlatform() throws Exception {
    // Загрузка платформы у связи с parameter без parameterListAllowed дописывает явный false; без параметра
    // флаг не появляется, записанный true сохраняется
    Path dcs = copySchema(LINKED_SCHEMA);
    String before = Files.readString(dcs, StandardCharsets.UTF_8);

    DcsEdit.addLink(dcs, SchemaVersion.V2_20, newLink("Проверка", Map.of("parameter", "Проверка")));
    String after = Files.readString(dcs, StandardCharsets.UTF_8);
    assertSingleEditZone(before, after);
    // флаг встаёт сразу после параметра по xs:sequence, разметкой платформы
    assertThat(after).contains("\t\t<destinationExpression>Идентификатор</destinationExpression>\r\n"
      + "\t\t<parameter>Проверка</parameter>\r\n"
      + "\t\t<parameterListAllowed>false</parameterListAllowed>\r\n"
      + "\t</dataSetLink>\r\n");

    // null - то же, что незаданный флаг; явные true и false - как заданы; без параметра и с пустым
    // параметром незаданный флаг не пишется
    Map<String, Object> nulled = newLink("Нуль", Map.of("parameter", "Проверка"));
    nulled.put("parameterListAllowed", null);
    DcsEdit.addLink(dcs, SchemaVersion.V2_20, nulled);
    DcsEdit.addLink(dcs, SchemaVersion.V2_20,
      newLink("Список", Map.of("parameter", "Проверка", "parameterListAllowed", true)));
    DcsEdit.addLink(dcs, SchemaVersion.V2_20,
      newLink("НеСписок", Map.of("parameter", "Проверка", "parameterListAllowed", false)));
    DcsEdit.addLink(dcs, SchemaVersion.V2_20, newLink("БезПараметра", Map.of()));
    DcsEdit.addLink(dcs, SchemaVersion.V2_20, newLink("ПустойПараметр", Map.of("parameter", " ")));

    List<Map<String, Object>> links = rows(DcsRead.info(dcs, SchemaVersion.V2_20), "dataSetLinks");
    assertThat(links).hasSize(7);
    assertThat(links.subList(1, 7).stream().map(link -> link.get("parameterListAllowed")).toList())
      .containsExactly(false, false, true, false, null, null);
    assertThat(links.subList(5, 7)).allSatisfy(link -> assertThat(link).doesNotContainKey("parameter"));
    String text = Files.readString(dcs, StandardCharsets.UTF_8);
    for (int i = 1; i <= 4; i++) {
      assertThat(linkChildNames(text, i)).as("связь " + i).containsExactly(
        "sourceDataSet", "destinationDataSet", "sourceExpression", "destinationExpression",
        "parameter", "parameterListAllowed");
    }
    for (int i = 5; i <= 6; i++) {
      assertThat(linkChildNames(text, i)).as("связь " + i)
        .containsExactly("sourceDataSet", "destinationDataSet", "sourceExpression", "destinationExpression");
    }
  }

  @Test
  void setLinkCompletesListFlagOfParameterLinkOnlyWithRealEdit() throws Exception {
    Path dcs = copySchema(MULTI_LINK_SCHEMA);
    String before = Files.readString(dcs, StandardCharsets.UTF_8);
    String parameterLine = "\t\t<parameter>Родитель</parameter>\r\n";
    String falseLine = "\t\t<parameterListAllowed>false</parameterListAllowed>\r\n";
    String trueLine = "\t\t<parameterListAllowed>true</parameterListAllowed>\r\n";
    String startLine = "\t\t<startExpression>Неопределено</startExpression>\r\n";
    Map<String, Object> nullFlag = new HashMap<>();
    nullFlag.put("parameterListAllowed", null);

    // задание параметра у связи без флага дописывает false сразу после параметра; эхо - байтовый no-op
    DcsEdit.setLink(dcs, SchemaVersion.V2_20, 1, Map.of("parameter", "Родитель"));
    String completed = withLinkLine(before, 1, parameterLine + falseLine);
    assertThat(Files.readString(dcs, StandardCharsets.UTF_8)).isEqualTo(completed);
    Map<String, Object> read = rows(DcsRead.info(dcs, SchemaVersion.V2_20), "dataSetLinks").get(1);
    assertThat(read).containsEntry("parameter", "Родитель").containsEntry("parameterListAllowed", false);
    DcsEdit.setLink(dcs, SchemaVersion.V2_20, 1, linkProps(read));
    assertThat(Files.readString(dcs, StandardCharsets.UTF_8)).isEqualTo(completed);

    // связь с параметром без флага в файле: эхо, в том числе каналом, null флага и правка, которая не меняет
    // ни байта, ничего не дописывают
    String old = withLinkLine(before, 1, parameterLine);
    Files.writeString(dcs, old, StandardCharsets.UTF_8);
    Map<String, Object> oldRead = rows(DcsRead.info(dcs, SchemaVersion.V2_20), "dataSetLinks").get(1);
    assertThat(oldRead).containsEntry("parameter", "Родитель").doesNotContainKey("parameterListAllowed");
    DcsEdit.setLink(dcs, SchemaVersion.V2_20, 1, linkProps(oldRead));
    assertThat(applyMutation(Map.of("op", "cf-dcs-set-link", "objectXml", dcs.toString(), "schemaVersion", "V2_20",
      "index", 1, "payloadJson", new Gson().toJson(linkProps(oldRead))))).isZero();
    DcsEdit.setLink(dcs, SchemaVersion.V2_20, 1, nullFlag);
    DcsEdit.setLink(dcs, SchemaVersion.V2_20, 1, Map.of("required", false, "parameter", "Родитель"));
    assertThat(Files.readString(dcs, StandardCharsets.UTF_8)).isEqualTo(old);
    // настоящая правка доводит связь до формы платформы
    DcsEdit.setLink(dcs, SchemaVersion.V2_20, 1, Map.of("startExpression", "Неопределено"));
    String started = withLinkLine(before, 1, parameterLine + falseLine + startLine);
    assertThat(Files.readString(dcs, StandardCharsets.UTF_8)).isEqualTo(started);

    // явный флаг - как задан; null у связи с параметром - false платформы, а не снятие
    DcsEdit.setLink(dcs, SchemaVersion.V2_20, 1, Map.of("parameterListAllowed", true));
    assertThat(Files.readString(dcs, StandardCharsets.UTF_8))
      .isEqualTo(withLinkLine(before, 1, parameterLine + trueLine + startLine));
    DcsEdit.setLink(dcs, SchemaVersion.V2_20, 1, nullFlag);
    assertThat(Files.readString(dcs, StandardCharsets.UTF_8)).isEqualTo(started);
    DcsEdit.setLink(dcs, SchemaVersion.V2_20, 1, nullFlag);
    assertThat(Files.readString(dcs, StandardCharsets.UTF_8)).isEqualTo(started);

    // снятие параметра снимает и флаг той же правкой: без параметра платформа флаг не хранит
    Map<String, Object> noParameter = new HashMap<>();
    noParameter.put("parameter", null);
    DcsEdit.setLink(dcs, SchemaVersion.V2_20, 1, noParameter);
    String unparametrized = withLinkLine(before, 1, startLine);
    assertThat(Files.readString(dcs, StandardCharsets.UTF_8)).isEqualTo(unparametrized);
    // параметр, заданный снова, дописывает false платформы; пустой параметр снова снимает оба
    DcsEdit.setLink(dcs, SchemaVersion.V2_20, 1, Map.of("parameter", "Родитель"));
    assertThat(Files.readString(dcs, StandardCharsets.UTF_8)).isEqualTo(started);
    DcsEdit.setLink(dcs, SchemaVersion.V2_20, 1, Map.of("parameter", ""));
    assertThat(Files.readString(dcs, StandardCharsets.UTF_8)).isEqualTo(unparametrized);
    // у связи без параметра правка других свойств флаг не дописывает, null флага ничего не меняет
    DcsEdit.setLink(dcs, SchemaVersion.V2_20, 1, nullFlag);
    assertThat(Files.readString(dcs, StandardCharsets.UTF_8)).isEqualTo(unparametrized);
    DcsEdit.setLink(dcs, SchemaVersion.V2_20, 1, Map.of("startExpression", ""));
    assertThat(Files.readString(dcs, StandardCharsets.UTF_8)).isEqualTo(before);

    // параметр вместе с явным флагом - как задан; снятие обоих возвращает связь байт в байт
    DcsEdit.setLink(dcs, SchemaVersion.V2_20, 1, Map.of("parameter", "Родитель", "parameterListAllowed", true));
    assertThat(Files.readString(dcs, StandardCharsets.UTF_8))
      .isEqualTo(withLinkLine(before, 1, parameterLine + trueLine));
    Map<String, Object> cleared = new HashMap<>(nullFlag);
    cleared.put("parameter", "");
    DcsEdit.setLink(dcs, SchemaVersion.V2_20, 1, cleared);
    assertThat(Files.readString(dcs, StandardCharsets.UTF_8)).isEqualTo(before);

    // пустой параметр в файле - не параметр: правка флага не дописывает
    String emptyParameter = "\t\t<parameter/>\r\n";
    Files.writeString(dcs, withLinkLine(before, 1, emptyParameter), StandardCharsets.UTF_8);
    DcsEdit.setLink(dcs, SchemaVersion.V2_20, 1, Map.of("startExpression", "Неопределено"));
    assertThat(Files.readString(dcs, StandardCharsets.UTF_8))
      .isEqualTo(withLinkLine(before, 1, emptyParameter + startLine));
  }

  @Test
  void applyMutationChannelAndBatchWriteListFlagOfParameterLink() throws Exception {
    Path dcs = copySchema(MULTI_LINK_SCHEMA);
    String link = "{\"sourceDataSet\":\"ПраваПользователей\",\"destinationDataSet\":\"НастройкиПравЛегенда\","
      + "\"sourceExpression\":\"ВладелецНастроек\",\"destinationExpression\":\"ВладелецНастроек\","
      + "\"parameter\":\"Владелец\"";

    // канал: новая связь с параметром без флага и с null в JSON, задание параметра у связи без флага
    assertThat(applyMutation(Map.of("op", "cf-dcs-add-link", "objectXml", dcs.toString(), "schemaVersion", "V2_20",
      "payloadJson", link + "}"))).isZero();
    assertThat(applyMutation(Map.of("op", "cf-dcs-add-link", "objectXml", dcs.toString(), "schemaVersion", "V2_20",
      "payloadJson", link + ",\"parameterListAllowed\":null}"))).isZero();
    assertThat(applyMutation(Map.of("op", "cf-dcs-set-link", "objectXml", dcs.toString(), "schemaVersion", "V2_20",
      "index", 1, "payloadJson", "{\"parameter\":\"Родитель\"}"))).isZero();
    List<Map<String, Object>> links = rows(DcsRead.info(dcs, SchemaVersion.V2_20), "dataSetLinks");
    assertThat(links).hasSize(6);
    assertThat(links.stream().map(row -> row.get("parameterListAllowed")).toList())
      .containsExactly(null, false, null, null, false, false);

    // пакет: отказ второй операции отменяет и дописанный флаг первой, диск не тронут
    Path batched = copySchema(MULTI_LINK_SCHEMA);
    String clean = Files.readString(batched, StandardCharsets.UTF_8);
    assertThat(applyMutation(Map.of("op", "batch", "operations", List.of(
      Map.of("op", "cf-dcs-set-link", "objectXml", batched.toString(), "schemaVersion", "V2_20",
        "index", 2, "payloadJson", "{\"parameter\":\"Родитель\"}"),
      Map.of("op", "cf-dcs-set-link", "objectXml", batched.toString(), "schemaVersion", "V2_20",
        "index", 1, "payloadJson", "{\"required\":true}"))))).isEqualTo(2);
    assertThat(Files.readString(batched, StandardCharsets.UTF_8)).isEqualTo(clean);
    // пакет без отказа: флаг дописывают обе операции
    assertThat(applyMutation(Map.of("op", "batch", "operations", List.of(
      Map.of("op", "cf-dcs-set-link", "objectXml", batched.toString(), "schemaVersion", "V2_20",
        "index", 2, "payloadJson", "{\"parameter\":\"Родитель\"}"),
      Map.of("op", "cf-dcs-add-link", "objectXml", batched.toString(), "schemaVersion", "V2_20",
        "payloadJson", link + "}"))))).isZero();
    links = rows(DcsRead.info(batched, SchemaVersion.V2_20), "dataSetLinks");
    assertThat(links.stream().map(row -> row.get("parameterListAllowed")).toList())
      .containsExactly(null, null, false, null, false);
    assertThat(linkChildNames(Files.readString(batched, StandardCharsets.UTF_8), 2)).containsExactly(
      "sourceDataSet", "destinationDataSet", "sourceExpression", "destinationExpression",
      "parameter", "parameterListAllowed");
  }

  @Test
  void linkEchoIsByteNoOpOnBothCorporaWithoutParameterLinks() throws Exception {
    // в корпусах связей с параметром нет (по 15 связей ssl31 и EDT-пары): флаг списка дописывать некому,
    // эхо каждой связи - байтовый no-op
    List<Path> designer = schemaFiles(Ssl31SubmodulePaths.projectRoot().resolve("src"));
    List<Path> edt = schemaFiles(Path.of(System.getProperty("fixtures.ssl31edt.root")));
    assertThat(List.of(echoLinks(designer, "Template.xml"), echoLinks(edt, "Template.dcs")))
      .containsExactly(List.of(15, 0), List.of(15, 0));
  }

  /**
   * Эхо каждой связи схем из {@code cf-dcs-info} - байтовый no-op.
   *
   * @return число связей и число связей с параметром
   */
  private List<Integer> echoLinks(List<Path> schemas, String fileName) throws Exception {
    int links = 0;
    int withParameter = 0;
    int index = 0;
    for (Path schema : schemas) {
      List<Map<String, Object>> rows = rows(DcsRead.info(schema, SchemaVersion.V2_20), "dataSetLinks");
      if (rows.isEmpty()) {
        continue;
      }
      Path copy = Files.createTempDirectory(tempDir, "echo" + index++).resolve(fileName);
      Files.copy(schema, copy);
      byte[] original = Files.readAllBytes(copy);
      for (int i = 0; i < rows.size(); i++) {
        withParameter += rows.get(i).containsKey("parameter") ? 1 : 0;
        DcsEdit.setLink(copy, SchemaVersion.V2_20, i, linkProps(rows.get(i)));
        assertThat(Files.readAllBytes(copy)).as(schema + ": связь " + i).isEqualTo(original);
        links++;
      }
    }
    return List.of(links, withParameter);
  }

  @Test
  void addLinkRefusesListFlagWithoutParameterAndDropsFalse() throws Exception {
    // Флаг списка без параметра платформа не хранит: загрузка у связи без parameter отбрасывает и false, и true
    Path dcs = copySchema(LINKED_SCHEMA);
    String before = Files.readString(dcs, StandardCharsets.UTF_8);

    // true без параметра и с пустым параметром - отказ без записи, в том числе кодом 2 канала
    for (Map<String, Object> extra : List.<Map<String, Object>>of(Map.of("parameterListAllowed", true),
      Map.of("parameter", " ", "parameterListAllowed", true), Map.of("parameter", "", "parameterListAllowed", true))) {
      assertThatThrownBy(() -> DcsEdit.addLink(dcs, SchemaVersion.V2_20, newLink("Флаг", extra)))
        .as(extra.toString()).isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("parameterListAllowed=true")
        .hasMessageContaining("не хранит флаг без параметра связи")
        .hasMessageContaining("Задайте parameter");
      assertThat(applyMutation(Map.of("op", "cf-dcs-add-link", "objectXml", dcs.toString(), "schemaVersion", "V2_20",
        "payloadJson", new Gson().toJson(newLink("Флаг", extra))))).as(extra.toString()).isEqualTo(2);
    }
    assertThatThrownBy(() -> DcsEdit.addLink(dcs, SchemaVersion.V2_20,
      newLink("Флаг", Map.of("parameterListAllowed", "да"))))
      .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("булевым");
    assertThat(Files.readString(dcs, StandardCharsets.UTF_8)).isEqualTo(before);

    // false и null без параметра не пишутся, как required=false; с параметром true - как задан
    Map<String, Object> nulled = newLink("Нуль", Map.of());
    nulled.put("parameterListAllowed", null);
    DcsEdit.addLink(dcs, SchemaVersion.V2_20, newLink("Ложь", Map.of("parameterListAllowed", false)));
    DcsEdit.addLink(dcs, SchemaVersion.V2_20, nulled);
    DcsEdit.addLink(dcs, SchemaVersion.V2_20, newLink("ПустойЛожь", Map.of("parameter", "", "parameterListAllowed",
      false)));
    DcsEdit.addLink(dcs, SchemaVersion.V2_20, newLink("Список", Map.of("parameter", "Проверка",
      "parameterListAllowed", true)));
    String text = Files.readString(dcs, StandardCharsets.UTF_8);
    for (int i = 1; i <= 3; i++) {
      assertThat(linkChildNames(text, i)).as("связь " + i)
        .containsExactly("sourceDataSet", "destinationDataSet", "sourceExpression", "destinationExpression");
    }
    assertThat(linkChildNames(text, 4)).containsExactly("sourceDataSet", "destinationDataSet", "sourceExpression",
      "destinationExpression", "parameter", "parameterListAllowed");
    assertThat(rows(DcsRead.info(dcs, SchemaVersion.V2_20), "dataSetLinks").get(4))
      .containsEntry("parameterListAllowed", true);
    assertThat(DcsXsdValidation.validate(dcs).get("warnings")).isEqualTo(List.of());
  }

  @Test
  void setLinkRemovesListFlagWithParameterAndRefusesTrueWithoutIt() throws Exception {
    Path dcs = copySchema(MULTI_LINK_SCHEMA);
    String before = Files.readString(dcs, StandardCharsets.UTF_8);
    String parameterLine = "\t\t<parameter>Родитель</parameter>\r\n";
    String falseLine = "\t\t<parameterListAllowed>false</parameterListAllowed>\r\n";
    String trueLine = "\t\t<parameterListAllowed>true</parameterListAllowed>\r\n";
    String startLine = "\t\t<startExpression>Неопределено</startExpression>\r\n";

    // связь без параметра: true - отказ без записи, в том числе кодом 2 канала и вместе с правкой соседа
    assertThatThrownBy(() -> DcsEdit.setLink(dcs, SchemaVersion.V2_20, 1, Map.of("parameterListAllowed", true)))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("parameterListAllowed=true").hasMessageContaining("Задайте parameter");
    assertThatThrownBy(() -> DcsEdit.setLink(dcs, SchemaVersion.V2_20, 1,
      Map.of("parameterListAllowed", true, "startExpression", "Неопределено", "parameter", "")))
      .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("parameterListAllowed=true");
    assertThat(applyMutation(Map.of("op", "cf-dcs-set-link", "objectXml", dcs.toString(), "schemaVersion", "V2_20",
      "index", 1, "payloadJson", "{\"parameterListAllowed\":true}"))).isEqualTo(2);
    assertThatThrownBy(() -> DcsEdit.setLink(dcs, SchemaVersion.V2_20, 1, Map.of("parameterListAllowed", 1)))
      .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("булевым");
    // false и null у связи без флага ничего не меняют
    Map<String, Object> nullFlag = new HashMap<>();
    nullFlag.put("parameterListAllowed", null);
    DcsEdit.setLink(dcs, SchemaVersion.V2_20, 1, Map.of("parameterListAllowed", false));
    DcsEdit.setLink(dcs, SchemaVersion.V2_20, 1, nullFlag);
    assertThat(Files.readString(dcs, StandardCharsets.UTF_8)).isEqualTo(before);

    // связь с параметром и флагом: снятие параметра (null или пустая строка) снимает флаг той же правкой -
    // и true, и false, в том числе с явными false и null флага в том же payload
    for (String flagLine : List.of(trueLine, falseLine)) {
      for (Object parameter : java.util.Arrays.asList(null, "", " ")) {
        for (Object flag : java.util.Arrays.asList("нет ключа", null, false)) {
          String linked = withLinkLine(before, 1, parameterLine + flagLine + startLine);
          Files.writeString(dcs, linked, StandardCharsets.UTF_8);
          Map<String, Object> props = new HashMap<>();
          props.put("parameter", parameter);
          if (!"нет ключа".equals(flag)) {
            props.put("parameterListAllowed", flag);
          }
          DcsEdit.setLink(dcs, SchemaVersion.V2_20, 1, props);
          assertThat(Files.readString(dcs, StandardCharsets.UTF_8)).as(flagLine + props)
            .isEqualTo(withLinkLine(before, 1, startLine));
        }
        // true в том же payload - отказ, файл не тронут (и через канал)
        String linked = withLinkLine(before, 1, parameterLine + flagLine + startLine);
        Files.writeString(dcs, linked, StandardCharsets.UTF_8);
        Map<String, Object> props = new HashMap<>();
        props.put("parameter", parameter);
        props.put("parameterListAllowed", true);
        assertThatThrownBy(() -> DcsEdit.setLink(dcs, SchemaVersion.V2_20, 1, props))
          .as(flagLine + props).isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("parameterListAllowed=true");
        // null в JSON канала нужен явный: Gson по умолчанию null-ключи опускает
        assertThat(applyMutation(Map.of("op", "cf-dcs-set-link", "objectXml", dcs.toString(), "schemaVersion",
          "V2_20", "index", 1, "payloadJson", new com.google.gson.GsonBuilder().serializeNulls().create()
            .toJson(props)))).as(flagLine + props).isEqualTo(2);
        assertThat(Files.readString(dcs, StandardCharsets.UTF_8)).as(flagLine + props).isEqualTo(linked);
      }
    }
    // через канал: окно шлёт parameter "" и null флага - флага без параметра не остаётся
    Files.writeString(dcs, withLinkLine(before, 1, parameterLine + trueLine), StandardCharsets.UTF_8);
    assertThat(applyMutation(Map.of("op", "cf-dcs-set-link", "objectXml", dcs.toString(), "schemaVersion", "V2_20",
      "index", 1, "payloadJson", "{\"parameter\":\"\",\"parameterListAllowed\":null}"))).isZero();
    assertThat(Files.readString(dcs, StandardCharsets.UTF_8)).isEqualTo(before);
  }

  @Test
  void listFlagWithoutParameterInFileSurvivesEchoAndWarnsInStrictValidate() throws Exception {
    // Флаг без параметра бывает в файле, правленном мимо cf-dcs-add-link и cf-dcs-set-link
    Path dcs = copySchema(MULTI_LINK_SCHEMA);
    String before = Files.readString(dcs, StandardCharsets.UTF_8);
    String trueLine = "\t\t<parameterListAllowed>true</parameterListAllowed>\r\n";
    String falseLine = "\t\t<parameterListAllowed>false</parameterListAllowed>\r\n";
    String startLine = "\t\t<startExpression>Неопределено</startExpression>\r\n";

    for (String flagLine : List.of(trueLine, falseLine)) {
      boolean value = flagLine.equals(trueLine);
      String legacy = withLinkLine(before, 1, flagLine);
      Files.writeString(dcs, legacy, StandardCharsets.UTF_8);
      Map<String, Object> read = rows(DcsRead.info(dcs, SchemaVersion.V2_20), "dataSetLinks").get(1);
      assertThat(read).containsEntry("parameterListAllowed", value).doesNotContainKey("parameter");

      // строгая проверка предупреждает, ok не меняется
      Map<String, Object> strict = DcsXsdValidation.validate(dcs);
      assertThat(strict.get("ok")).isEqualTo(true);
      assertThat(strict.get("warnings")).as(flagLine).isEqualTo(List.of(Map.of(
        "message", "связь наборов " + read.get("source") + " → " + read.get("destination") + " (номер 1): флаг "
          + "«Список параметров» (parameterListAllowed=" + value + ") записан без параметра связи; при загрузке "
          + "платформа его отбросит — задайте parameter или снимите флаг",
        "link", 1,
        "kind", "linkListFlagWithoutParameter")));

      // эхо (прямо и каналом), повтор значения, пустой параметр у связи без параметра - ни байта
      DcsEdit.setLink(dcs, SchemaVersion.V2_20, 1, linkProps(read));
      assertThat(applyMutation(Map.of("op", "cf-dcs-set-link", "objectXml", dcs.toString(), "schemaVersion",
        "V2_20", "index", 1, "payloadJson", new Gson().toJson(linkProps(read))))).isZero();
      DcsEdit.setLink(dcs, SchemaVersion.V2_20, 1, Map.of("parameterListAllowed", value));
      DcsEdit.setLink(dcs, SchemaVersion.V2_20, 1, Map.of("parameter", ""));
      Map<String, Object> nullParameter = new HashMap<>();
      nullParameter.put("parameter", null);
      DcsEdit.setLink(dcs, SchemaVersion.V2_20, 1, nullParameter);
      assertThat(Files.readString(dcs, StandardCharsets.UTF_8)).as(flagLine).isEqualTo(legacy);

      // настоящая правка других свойств флаг молча не снимает (как и required), в том числе с эхом флага
      DcsEdit.setLink(dcs, SchemaVersion.V2_20, 1, Map.of("startExpression", "Неопределено"));
      assertThat(Files.readString(dcs, StandardCharsets.UTF_8)).as(flagLine)
        .isEqualTo(withLinkLine(before, 1, flagLine + startLine));
      DcsEdit.setLink(dcs, SchemaVersion.V2_20, 1, Map.of("startExpression", "", "parameterListAllowed", value));
      assertThat(Files.readString(dcs, StandardCharsets.UTF_8)).as(flagLine).isEqualTo(legacy);

      // у записанного false true - отказ; у записанного true отказ только при снятии параметра (его нет)
      if (!value) {
        assertThatThrownBy(() -> DcsEdit.setLink(dcs, SchemaVersion.V2_20, 1, Map.of("parameterListAllowed", true)))
          .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("parameterListAllowed=true");
        assertThat(Files.readString(dcs, StandardCharsets.UTF_8)).isEqualTo(legacy);
      }

      // пакет: отказ второй операции отменяет и первую, диск не тронут
      assertThat(applyMutation(Map.of("op", "batch", "operations", List.of(
        Map.of("op", "cf-dcs-set-link", "objectXml", dcs.toString(), "schemaVersion", "V2_20",
          "index", 1, "payloadJson", "{\"startExpression\":\"Неопределено\"}"),
        Map.of("op", "cf-dcs-set-link", "objectXml", dcs.toString(), "schemaVersion", "V2_20",
          "index", 0, "payloadJson", "{\"parameterListAllowed\":true}"))))).isEqualTo(2);
      assertThat(Files.readString(dcs, StandardCharsets.UTF_8)).as(flagLine).isEqualTo(legacy);

      // параметр, заданный такой связи, флаг оставляет: у связи с параметром он хранится
      DcsEdit.setLink(dcs, SchemaVersion.V2_20, 1, Map.of("parameter", "Родитель"));
      assertThat(Files.readString(dcs, StandardCharsets.UTF_8)).as(flagLine)
        .isEqualTo(withLinkLine(before, 1, "\t\t<parameter>Родитель</parameter>\r\n" + flagLine));
      assertThat(DcsXsdValidation.validate(dcs).get("warnings")).as(flagLine).isEqualTo(List.of());
      Files.writeString(dcs, legacy, StandardCharsets.UTF_8);

      // явное снятие: false у записанного true и null у любого - элемент целой строкой, предупреждение уходит
      Map<String, Object> nullFlag = new HashMap<>();
      nullFlag.put("parameterListAllowed", null);
      DcsEdit.setLink(dcs, SchemaVersion.V2_20, 1, value ? Map.of("parameterListAllowed", false) : nullFlag);
      assertThat(Files.readString(dcs, StandardCharsets.UTF_8)).as(flagLine).isEqualTo(before);
      assertThat(DcsXsdValidation.validate(dcs).get("warnings")).as(flagLine).isEqualTo(List.of());
    }
  }

  @Test
  void linkListFlagWithoutParameterIsAbsentFromBothCorpora() throws Exception {
    // в корпусах флага без параметра нет ни разу (по 15 связей ssl31 и EDT-пары, параметра нет ни у одной):
    // предупреждений строгой проверки 0, false и null флага - байтовый no-op, true - отказ без записи
    List<Path> designer = schemaFiles(Ssl31SubmodulePaths.projectRoot().resolve("src"));
    List<Path> edt = schemaFiles(Path.of(System.getProperty("fixtures.ssl31edt.root")));
    assertThat(List.of(listFlagsWithoutParameter(designer, "Template.xml"),
      listFlagsWithoutParameter(edt, "Template.dcs"))).containsExactly(List.of(15, 0, 0), List.of(15, 0, 0));
  }

  /**
   * Флаг списка у каждой связи без параметра: {@code false} и {@code null} - байтовый no-op, {@code true} -
   * отказ без записи.
   *
   * @return число связей, связей с флагом без параметра и предупреждений строгой проверки
   */
  private List<Integer> listFlagsWithoutParameter(List<Path> schemas, String fileName) throws Exception {
    int links = 0;
    int flagged = 0;
    int warnings = 0;
    int index = 0;
    for (Path schema : schemas) {
      warnings += DcsLegacyLinkListFlags.warnings(schema).size();
      List<Map<String, Object>> rows = rows(DcsRead.info(schema, SchemaVersion.V2_20), "dataSetLinks");
      if (rows.isEmpty()) {
        continue;
      }
      Path copy = Files.createTempDirectory(tempDir, "flags" + index++).resolve(fileName);
      Files.copy(schema, copy);
      byte[] original = Files.readAllBytes(copy);
      for (int i = 0; i < rows.size(); i++) {
        int link = i;
        links++;
        boolean withParameter = rows.get(i).get("parameter") instanceof String parameter && !parameter.isBlank();
        flagged += !withParameter && rows.get(i).containsKey("parameterListAllowed") ? 1 : 0;
        if (withParameter) {
          continue;
        }
        Map<String, Object> cleared = new HashMap<>();
        cleared.put("parameterListAllowed", null);
        DcsEdit.setLink(copy, SchemaVersion.V2_20, link, Map.of("parameterListAllowed", false));
        DcsEdit.setLink(copy, SchemaVersion.V2_20, link, cleared);
        assertThatThrownBy(() -> DcsEdit.setLink(copy, SchemaVersion.V2_20, link,
          Map.of("parameterListAllowed", true)))
          .as(schema + ": связь " + link).isInstanceOf(IllegalArgumentException.class);
        assertThat(Files.readAllBytes(copy)).as(schema + ": связь " + link).isEqualTo(original);
      }
    }
    return List.of(links, flagged, warnings);
  }

  /** Новая связь «ОсновнойЗапрос» -> «ВнешняяТаблица» схемы со связью и необязательные свойства. */
  private static Map<String, Object> newLink(String sourceExpression, Map<String, Object> extra) {
    Map<String, Object> link = new HashMap<>(Map.of(
      "sourceDataSet", "ОсновнойЗапрос", "destinationDataSet", "ВнешняяТаблица",
      "sourceExpression", sourceExpression, "destinationExpression", "Идентификатор"));
    link.putAll(extra);
    return link;
  }

  /** Свойства связи из {@code cf-dcs-info} ключами {@code cf-dcs-set-link}. */
  private static Map<String, Object> linkProps(Map<String, Object> link) {
    Map<String, Object> props = new HashMap<>();
    props.put("sourceDataSet", link.get("source"));
    props.put("destinationDataSet", link.get("destination"));
    for (String key : List.of("sourceExpression", "destinationExpression", "parameter", "parameterListAllowed",
      "linkConditionExpression", "startExpression", "required")) {
      if (link.containsKey(key)) {
        props.put(key, link.get(key));
      }
    }
    return props;
  }

  /** Строки, вставленные перед закрывающим тегом связи с номером {@code index}. */
  private static String withLinkLine(String xml, int index, String lines) {
    List<XmlLines.Node> links = XmlLines.children(xml, List.of("DataCompositionSchema")).stream()
      .filter(node -> "dataSetLink".equals(node.name()))
      .toList();
    int close = xml.lastIndexOf("</dataSetLink>", links.get(index).end());
    int lineStart = xml.lastIndexOf('\n', close) + 1;
    return xml.substring(0, lineStart) + lines + xml.substring(lineStart);
  }

  /** Схемы компоновки под каталогом: макеты выгрузки и проекта EDT. */
  private static List<Path> schemaFiles(Path root) throws IOException {
    try (Stream<Path> files = Files.walk(root)) {
      return files
        .filter(file -> {
          String name = file.getFileName().toString();
          return name.equals("Template.xml") || name.equals("Template.dcs");
        })
        .filter(file -> {
          try {
            return Files.readString(file, StandardCharsets.UTF_8).contains("<DataCompositionSchema");
          } catch (IOException e) {
            throw new IllegalStateException(e);
          }
        })
        .sorted()
        .toList();
    }
  }

  @Test
  void addTotalFieldInsertsIntoTotalFieldPositionOfSequence() throws Exception {
    // в схеме без ресурсов новый встаёт сразу после наборов данных, до вариантов настроек
    Path dcs = copySchema(FOLDER_SCHEMA);
    String before = Files.readString(dcs, StandardCharsets.UTF_8);

    DcsEdit.addTotalField(dcs, SchemaVersion.V2_20, Map.of(
      "dataPath", "СуммаДокумента",
      "expression", "Сумма(СуммаДокумента) > 0",
      "groups", List.of("Организация", "ОбщийИтог")));

    String after = Files.readString(dcs, StandardCharsets.UTF_8);
    assertSingleEditZone(before, after);
    // блок ресурса написан как у платформы: табы, CRLF, группировки в порядке списка, > экранирован
    assertThat(after).contains("\t<totalField>\r\n"
      + "\t\t<dataPath>СуммаДокумента</dataPath>\r\n"
      + "\t\t<expression>Сумма(СуммаДокумента) &gt; 0</expression>\r\n"
      + "\t\t<group>Организация</group>\r\n"
      + "\t\t<group>ОбщийИтог</group>\r\n"
      + "\t</totalField>\r\n");
    // физика файла платформы: BOM, CRLF и конец без перевода строки не сломаны
    assertThat(after).startsWith("﻿<?xml");
    assertThat(after).endsWith("</DataCompositionSchema>");
    // порядок детей корня соответствует последовательности из XSD
    List<String> order = schemaSequence();
    List<String> roots = XmlLines.children(after, List.of("DataCompositionSchema")).stream()
      .map(XmlLines.Node::name)
      .toList();
    assertThat(roots.stream().map(order::indexOf).toList()).isSorted();
    assertThat(roots).containsSubsequence("dataSet", "totalField", "settingsVariant");
    Map<String, Object> total = rows(DcsRead.info(dcs, SchemaVersion.V2_20), "totalFields").get(0);
    assertThat(total.get("name")).isEqualTo("СуммаДокумента");
    assertThat(total.get("value")).isEqualTo("Сумма(СуммаДокумента) > 0");
    assertThat(total.get("groups")).isEqualTo(List.of("Организация", "ОбщийИтог"));

    // в схеме с ресурсами новый встаёт после последнего из них, до параметров; без groups
    // элементов group нет, а путь к данным не уникален: то же поле считается другим выражением
    Path linked = copySchema(LINKED_SCHEMA);
    DcsEdit.addTotalField(linked, SchemaVersion.V2_20, Map.of(
      "dataPath", "КоличествоПроблемСОбъектом", "expression", "Максимум(КоличествоПроблемСОбъектом)"));
    String linkedAfter = Files.readString(linked, StandardCharsets.UTF_8);
    List<String> linkedRoots = XmlLines.children(linkedAfter, List.of("DataCompositionSchema")).stream()
      .map(XmlLines.Node::name)
      .toList();
    assertThat(linkedRoots).containsSubsequence("calculatedField", "totalField", "totalField", "parameter");
    List<Map<String, Object>> totals = rows(DcsRead.info(linked, SchemaVersion.V2_20), "totalFields");
    assertThat(totals).hasSize(2);
    Map<String, Object> added = totals.get(1);
    assertThat(added.get("name")).isEqualTo("КоличествоПроблемСОбъектом");
    assertThat(added.get("value")).isEqualTo("Максимум(КоличествоПроблемСОбъектом)");
    assertThat(added).doesNotContainKey("groups");
  }

  @Test
  void addTotalFieldRefusals() throws Exception {
    Path dcs = copySchema(LINKED_SCHEMA);
    String before = Files.readString(dcs, StandardCharsets.UTF_8);

    // обязательные по формату путь и выражение требуют непустой текст
    assertThatThrownBy(() -> DcsEdit.addTotalField(dcs, SchemaVersion.V2_20, Map.of("dataPath", "Поле")))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("expression");
    assertThatThrownBy(() -> DcsEdit.addTotalField(dcs, SchemaVersion.V2_20, Map.of("expression", "Сумма(Поле)")))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("dataPath");
    assertThatThrownBy(() -> DcsEdit.addTotalField(dcs, SchemaVersion.V2_20, Map.of(
      "dataPath", " ", "expression", "Сумма(Поле)")))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("dataPath");
    // ключ группировок - groups со списком имён, а не элемент group
    assertThatThrownBy(() -> DcsEdit.addTotalField(dcs, SchemaVersion.V2_20, Map.of(
      "dataPath", "Поле", "expression", "Сумма(Поле)", "group", List.of("Итог"))))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("Неизвестное свойство");
    assertThatThrownBy(() -> DcsEdit.addTotalField(dcs, SchemaVersion.V2_20, Map.of(
      "dataPath", "Поле", "expression", "Сумма(Поле)", "groups", "Итог")))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("списком");
    assertThatThrownBy(() -> DcsEdit.addTotalField(dcs, SchemaVersion.V2_20, Map.of(
      "dataPath", "Поле", "expression", "Сумма(Поле)", "groups", List.of(" "))))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("непустой строкой");
    assertThatThrownBy(() -> DcsEdit.addTotalField(dcs, SchemaVersion.V2_20, Map.of()))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("payload");

    assertThat(Files.readString(dcs, StandardCharsets.UTF_8)).isEqualTo(before);
  }

  @Test
  void setTotalFieldEditsSubElementsByIndex() throws Exception {
    Path dcs = copySchema(MULTI_LINK_SCHEMA);
    String before = Files.readString(dcs, StandardCharsets.UTF_8);

    // путь к данным у ресурсов 3 и 4 одинаковый: индексная адресация меняет ровно четвёртый
    DcsEdit.setTotalField(dcs, SchemaVersion.V2_20, 4, Map.of("expression", "Минимум(ПравоОтчета)"));
    String replaced = Files.readString(dcs, StandardCharsets.UTF_8);
    assertThat(replaced).isEqualTo(before.replace(
      "<dataPath>ПравоИнтерактивное</dataPath>\r\n\t\t<expression>Максимум(ПравоОтчета)</expression>",
      "<dataPath>ПравоИнтерактивное</dataPath>\r\n\t\t<expression>Минимум(ПравоОтчета)</expression>"));

    // список группировок заменяется целиком и в порядке списка
    DcsEdit.setTotalField(dcs, SchemaVersion.V2_20, 4, Map.of("groups", List.of("ОбщийИтог", "Отчет")));
    String regrouped = Files.readString(dcs, StandardCharsets.UTF_8);
    assertSingleEditZone(replaced, regrouped);
    assertThat(regrouped).contains("\t<totalField>\r\n"
      + "\t\t<dataPath>ПравоИнтерактивное</dataPath>\r\n"
      + "\t\t<expression>Минимум(ПравоОтчета)</expression>\r\n"
      + "\t\t<group>ОбщийИтог</group>\r\n"
      + "\t\t<group>Отчет</group>\r\n"
      + "\t</totalField>");

    // пустой список снимает все группировки: ресурс считается по всем
    DcsEdit.setTotalField(dcs, SchemaVersion.V2_20, 3, Map.of("groups", List.of()));
    // в ресурс без группировок список встаёт после выражения, путь меняется той же правкой
    DcsEdit.setTotalField(dcs, SchemaVersion.V2_20, 0, Map.of(
      "dataPath", "ПравоПросмотраФормы", "groups", List.of("ОбъектМетаданных")));
    String after = Files.readString(dcs, StandardCharsets.UTF_8);
    assertThat(totalFieldChildNames(after, 0)).containsExactly("dataPath", "expression", "group");
    assertThat(totalFieldChildNames(after, 3)).containsExactly("dataPath", "expression");

    List<Map<String, Object>> totals = rows(DcsRead.info(dcs, SchemaVersion.V2_20), "totalFields");
    Map<String, Object> first = totals.get(0);
    assertThat(first.get("name")).isEqualTo("ПравоПросмотраФормы");
    assertThat(first.get("value")).isEqualTo("Максимум(ПравоПросмотр)");
    assertThat(first.get("groups")).isEqualTo(List.of("ОбъектМетаданных"));
    Map<String, Object> third = totals.get(3);
    assertThat(third.get("name")).isEqualTo("ПравоИнтерактивное");
    assertThat(third.get("value")).isEqualTo("Максимум(ПравоИнтерактивное)");
    assertThat(third).doesNotContainKey("groups");
    Map<String, Object> fourth = totals.get(4);
    assertThat(fourth.get("name")).isEqualTo("ПравоИнтерактивное");
    assertThat(fourth.get("value")).isEqualTo("Минимум(ПравоОтчета)");
    assertThat(fourth.get("groups")).isEqualTo(List.of("ОбщийИтог", "Отчет"));
    // дальний ресурс с тем же исходным выражением не задет индексной правкой
    Map<String, Object> tenth = totals.get(10);
    assertThat(tenth.get("value")).isEqualTo("Максимум(ПравоОтчета)");
    assertThat(tenth.get("groups")).isEqualTo(List.of("Отчет"));

    // null равносилен пустому списку: группировки снимаются
    Map<String, Object> reset = new HashMap<>();
    reset.put("groups", null);
    DcsEdit.setTotalField(dcs, SchemaVersion.V2_20, 4, reset);
    assertThat(totalFieldChildNames(Files.readString(dcs, StandardCharsets.UTF_8), 4))
      .containsExactly("dataPath", "expression");

    // пустой набор правок ничего не меняет
    String settled = Files.readString(dcs, StandardCharsets.UTF_8);
    DcsEdit.setTotalField(dcs, SchemaVersion.V2_20, 0, Map.of());
    assertThat(Files.readString(dcs, StandardCharsets.UTF_8)).isEqualTo(settled);
  }

  @Test
  void removeTotalFieldDeletesWholeElementByIndex() throws Exception {
    Path dcs = copySchema(TWO_OBJECTS_SCHEMA);
    String before = Files.readString(dcs, StandardCharsets.UTF_8);
    assertThat(rows(DcsRead.info(dcs, SchemaVersion.V2_20), "totalFields")).hasSize(5);

    DcsEdit.removeTotalField(dcs, SchemaVersion.V2_20, 0);

    String after = Files.readString(dcs, StandardCharsets.UTF_8);
    assertSingleEditZone(before, after);
    assertThat(after).isEqualTo(before.replace("\t<totalField>\r\n"
      + "\t\t<dataPath>Выполняется</dataPath>\r\n"
      + "\t\t<expression>Максимум(Выполняется)</expression>\r\n"
      + "\t</totalField>\r\n", ""));

    // после удаления номера ресурсов сдвигаются: последний - под номером 3
    DcsEdit.removeTotalField(dcs, SchemaVersion.V2_20, 3);
    List<Map<String, Object>> totals = rows(DcsRead.info(dcs, SchemaVersion.V2_20), "totalFields");
    assertThat(totals.stream().map(row -> row.get("name")))
      .containsExactly("Обновлено", "Ожидают", "Проблемы");
  }

  @Test
  void setTotalFieldAndRemoveTotalFieldRefusals() throws Exception {
    Path dcs = copySchema(LINKED_SCHEMA);
    String before = Files.readString(dcs, StandardCharsets.UTF_8);

    // ресурс в схеме один: номера за пределами файла - отказ
    assertThatThrownBy(() -> DcsEdit.setTotalField(dcs, SchemaVersion.V2_20, 1, Map.of("expression", "Сумма(Х)")))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("не найден");
    assertThatThrownBy(() -> DcsEdit.setTotalField(dcs, SchemaVersion.V2_20, -1, Map.of("expression", "Сумма(Х)")))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("не найден");
    assertThatThrownBy(() -> DcsEdit.removeTotalField(dcs, SchemaVersion.V2_20, 1))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("не найден");
    // ключи правок - имена подэлементов формата, а не поля ответа cf-dcs-info
    assertThatThrownBy(() -> DcsEdit.setTotalField(dcs, SchemaVersion.V2_20, 0, Map.of("name", "Х")))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("Неизвестное свойство");
    // обязательные по формату путь и выражение не очищаются
    assertThatThrownBy(() -> DcsEdit.setTotalField(dcs, SchemaVersion.V2_20, 0, Map.of("dataPath", " ")))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("dataPath");
    assertThatThrownBy(() -> DcsEdit.setTotalField(dcs, SchemaVersion.V2_20, 0, Map.of("expression", " ")))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("expression");
    assertThatThrownBy(() -> DcsEdit.setTotalField(dcs, SchemaVersion.V2_20, 0, Map.of("groups", List.of(5))))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("непустой строкой");
    assertThat(Files.readString(dcs, StandardCharsets.UTF_8)).isEqualTo(before);

    // в схеме без ресурсов удалять нечего
    Path doc = copySchema(FOLDER_SCHEMA);
    assertThatThrownBy(() -> DcsEdit.removeTotalField(doc, SchemaVersion.V2_20, 0))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("0 ресурсах");
  }

  @Test
  void setCalculatedFieldEditsPathExpressionAndTitle() throws Exception {
    Path dcs = copySchema(LINKED_SCHEMA);
    String before = Files.readString(dcs, StandardCharsets.UTF_8);

    // заголовок меняется ровно в своём блоке, формат платформы сохраняется байт в байт
    DcsEdit.setCalculatedField(dcs, SchemaVersion.V2_20, "ПравилоПроверкиПричиныПроблемы",
      Map.of("title", "Причины"));
    String titled = Files.readString(dcs, StandardCharsets.UTF_8);
    assertThat(titled).isEqualTo(before.replace(
      "<v8:content>Причины проблемы</v8:content>",
      "<v8:content>Причины</v8:content>"));

    // выражение заменяется с экранированием < > & как у платформы
    DcsEdit.setCalculatedField(dcs, SchemaVersion.V2_20, "РасшифровкаПоля",
      Map.of("expression", "ВЫБОР КОГДА Выявлено > 0 ТОГДА \"есть\" ИНАЧЕ \"нет\" КОНЕЦ"));
    String reexpressed = Files.readString(dcs, StandardCharsets.UTF_8);
    assertThat(reexpressed).isEqualTo(titled.replace(
      "<expression>КонтрольВеденияУчетаСлужебный.РасшифровкаЯчейки(ПроблемныйОбъект, ПравилоПроверки,"
        + " ВидПроверки, УточнениеПроблемы, КлючУникальности)</expression>",
      "<expression>ВЫБОР КОГДА Выявлено &gt; 0 ТОГДА \"есть\" ИНАЧЕ \"нет\" КОНЕЦ</expression>"));

    // null снимает заголовок целиком; соседний по последовательности valueType не тронут
    Map<String, Object> drop = new HashMap<>();
    drop.put("title", null);
    DcsEdit.setCalculatedField(dcs, SchemaVersion.V2_20, "ПравилоПроверкиПричиныПроблемы", drop);
    assertThat(calculatedFieldChildNames(
      Files.readString(dcs, StandardCharsets.UTF_8), "ПравилоПроверкиПричиныПроблемы"))
      .containsExactly("dataPath", "expression", "valueType");

    // заголовок возвращается на своё место по xs:sequence между выражением и типом значения -
    // файл совпадает с состоянием до снятия байт в байт
    DcsEdit.setCalculatedField(dcs, SchemaVersion.V2_20, "ПравилоПроверкиПричиныПроблемы",
      Map.of("title", "Причины"));
    assertThat(Files.readString(dcs, StandardCharsets.UTF_8)).isEqualTo(reexpressed);

    // замена пути к данным не трогает выражение и заголовок
    DcsEdit.setCalculatedField(dcs, SchemaVersion.V2_20, "ПроблемныйОбъектПредставление",
      Map.of("newDataPath", "ПредставлениеПроблемногоОбъекта"));

    List<Map<String, Object>> calculated = rows(DcsRead.info(dcs, SchemaVersion.V2_20), "calculatedFields");
    assertThat(byKey(calculated, "name", "ПравилоПроверкиПричиныПроблемы").get("title")).isEqualTo("Причины");
    assertThat(byKey(calculated, "name", "РасшифровкаПоля").get("value"))
      .isEqualTo("ВЫБОР КОГДА Выявлено > 0 ТОГДА \"есть\" ИНАЧЕ \"нет\" КОНЕЦ");
    Map<String, Object> renamed = byKey(calculated, "name", "ПредставлениеПроблемногоОбъекта");
    assertThat(renamed.get("title")).isEqualTo("Проблемный объект представление [ЗаголовокСкрыт]");
    assertThat(calculated.stream().map(row -> row.get("name"))).doesNotContain("ПроблемныйОбъектПредставление");

    // пустой набор правок ничего не меняет
    String settled = Files.readString(dcs, StandardCharsets.UTF_8);
    DcsEdit.setCalculatedField(dcs, SchemaVersion.V2_20, "РасшифровкаПоля", Map.of());
    assertThat(Files.readString(dcs, StandardCharsets.UTF_8)).isEqualTo(settled);
  }

  @Test
  void setCalculatedFieldAndRemoveCalculatedFieldRefusals() throws Exception {
    Path dcs = copySchema(LINKED_SCHEMA);
    String before = Files.readString(dcs, StandardCharsets.UTF_8);

    assertThatThrownBy(() -> DcsEdit.setCalculatedField(dcs, SchemaVersion.V2_20, "НетПоля", Map.of("title", "Х")))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("не найдено");
    // поля наборов данных вычисляемыми полями не адресуются
    assertThatThrownBy(() -> DcsEdit.setCalculatedField(dcs, SchemaVersion.V2_20, "Выявлено", Map.of("title", "Х")))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("не найдено");
    assertThatThrownBy(() -> DcsEdit.removeCalculatedField(dcs, SchemaVersion.V2_20, "НетПоля"))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("не найдено");
    assertThatThrownBy(() -> DcsEdit.setCalculatedField(dcs, SchemaVersion.V2_20, "РасшифровкаПоля",
      Map.of("newDataPath", "ПравилоПроверкиПричиныПроблемы")))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("занят");
    assertThatThrownBy(() -> DcsEdit.setCalculatedField(dcs, SchemaVersion.V2_20, "РасшифровкаПоля",
      Map.of("newDataPath", " ")))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("dataPath");
    assertThatThrownBy(() -> DcsEdit.setCalculatedField(dcs, SchemaVersion.V2_20, "РасшифровкаПоля",
      Map.of("expression", " ")))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("expression");
    assertThatThrownBy(() -> DcsEdit.setCalculatedField(dcs, SchemaVersion.V2_20, "РасшифровкаПоля",
      Map.of("useRestriction", Map.of("field", true))))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("Неизвестное свойство");
    assertThatThrownBy(() -> DcsEdit.setCalculatedField(dcs, SchemaVersion.V2_20, "РасшифровкаПоля",
      Map.of("title", 5)))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("строкой");

    assertThat(Files.readString(dcs, StandardCharsets.UTF_8)).isEqualTo(before);
  }

  @Test
  void removeCalculatedFieldDeletesWholeElement() throws Exception {
    Path dcs = copySchema(LINKED_SCHEMA);
    String before = Files.readString(dcs, StandardCharsets.UTF_8);

    DcsEdit.removeCalculatedField(dcs, SchemaVersion.V2_20, "РасшифровкаПоля");

    String after = Files.readString(dcs, StandardCharsets.UTF_8);
    assertSingleEditZone(before, after);
    // элемент вычисляемого поля вырезан целиком, упоминания пути в настройках не тронуты
    assertThat(after).doesNotContain("<dataPath>РасшифровкаПоля</dataPath>");
    assertThat(after).contains("РасшифровкаПоля");
    List<Map<String, Object>> calculated = rows(DcsRead.info(dcs, SchemaVersion.V2_20), "calculatedFields");
    assertThat(calculated).hasSize(2);
    assertThat(calculated.stream().map(row -> row.get("name"))).doesNotContain("РасшифровкаПоля");
  }

  @Test
  void applyMutationChannelRunsTotalAndCalculatedFieldOps() throws Exception {
    Path dcs = copySchema(TWO_OBJECTS_SCHEMA);

    assertThat(applyMutation(Map.of(
      "op", "cf-dcs-add-total-field", "objectXml", dcs.toString(), "schemaVersion", "V2_20",
      "payloadJson", "{\"dataPath\":\"Проблемы\",\"expression\":\"Сумма(Проблемы)\","
        + "\"groups\":[\"ОбластьДанных\",\"ОбщийИтог\"]}"))).isZero();
    assertThat(Files.readString(dcs, StandardCharsets.UTF_8)).contains("\t<totalField>\r\n"
      + "\t\t<dataPath>Проблемы</dataPath>\r\n"
      + "\t\t<expression>Сумма(Проблемы)</expression>\r\n"
      + "\t\t<group>ОбластьДанных</group>\r\n"
      + "\t\t<group>ОбщийИтог</group>\r\n"
      + "\t</totalField>\r\n");

    // порядковый номер ресурса приходит из JSON числом
    assertThat(applyMutation(Map.of(
      "op", "cf-dcs-set-total-field", "objectXml", dcs.toString(), "schemaVersion", "V2_20",
      "index", 5, "payloadJson", "{\"groups\":[\"ОбщийИтог\"]}"))).isZero();
    List<Map<String, Object>> totals = rows(DcsRead.info(dcs, SchemaVersion.V2_20), "totalFields");
    assertThat(totals).hasSize(6);
    assertThat(totals.get(5).get("groups")).isEqualTo(List.of("ОбщийИтог"));

    assertThat(applyMutation(Map.of(
      "op", "cf-dcs-remove-total-field", "objectXml", dcs.toString(), "schemaVersion", "V2_20",
      "index", 5))).isZero();
    assertThat(rows(DcsRead.info(dcs, SchemaVersion.V2_20), "totalFields")).hasSize(5);

    assertThat(applyMutation(Map.of(
      "op", "cf-dcs-set-calculated-field", "objectXml", dcs.toString(), "schemaVersion", "V2_20",
      "dataPath", "Количество",
      "payloadJson", "{\"expression\":\"2\",\"title\":\"Количество строк\"}"))).isZero();
    Map<String, Object> calculated = byKey(
      rows(DcsRead.info(dcs, SchemaVersion.V2_20), "calculatedFields"), "name", "Количество");
    assertThat(calculated.get("value")).isEqualTo("2");
    assertThat(calculated.get("title")).isEqualTo("Количество строк");

    assertThat(applyMutation(Map.of(
      "op", "cf-dcs-remove-calculated-field", "objectXml", dcs.toString(), "schemaVersion", "V2_20",
      "dataPath", "Количество"))).isZero();
    assertThat(rows(DcsRead.info(dcs, SchemaVersion.V2_20), "calculatedFields")).isEmpty();

    // отказ операции доходит из канала кодом 2, файл не меняется
    String before = Files.readString(dcs, StandardCharsets.UTF_8);
    assertThat(applyMutation(Map.of(
      "op", "cf-dcs-remove-calculated-field", "objectXml", dcs.toString(), "schemaVersion", "V2_20",
      "dataPath", "Количество"))).isEqualTo(2);
    assertThat(applyMutation(Map.of(
      "op", "cf-dcs-set-total-field", "objectXml", dcs.toString(), "schemaVersion", "V2_20",
      "payloadJson", "{\"groups\":[]}"))).isEqualTo(2);
    assertThat(Files.readString(dcs, StandardCharsets.UTF_8)).isEqualTo(before);
  }

  @Test
  void addParameterInsertsIntoParameterPositionOfSequence() throws Exception {
    // в схеме без параметров новый встаёт сразу после наборов данных, до вариантов настроек
    Path dcs = copySchema(FOLDER_SCHEMA);
    String before = Files.readString(dcs, StandardCharsets.UTF_8);

    DcsEdit.addParameter(dcs, SchemaVersion.V2_20, Map.of(
      "name", "Период",
      "title", "Период отчёта",
      "expression", "&Период.ДатаНачала",
      "useRestriction", false,
      "use", "Always"));

    String after = Files.readString(dcs, StandardCharsets.UTF_8);
    assertSingleEditZone(before, after);
    // блок параметра написан как у платформы: табы, CRLF, подэлементы в порядке xs:sequence,
    // & экранирован, заголовок v8:item с языком конфигурации, значение без value в payload - xsi:nil
    assertThat(after).contains("\t<parameter>\r\n"
      + "\t\t<name>Период</name>\r\n"
      + "\t\t<title xsi:type=\"v8:LocalStringType\">\r\n"
      + "\t\t\t<v8:item>\r\n"
      + "\t\t\t\t<v8:lang>ru</v8:lang>\r\n"
      + "\t\t\t\t<v8:content>Период отчёта</v8:content>\r\n"
      + "\t\t\t</v8:item>\r\n"
      + "\t\t</title>\r\n"
      + "\t\t<value xsi:nil=\"true\"/>\r\n"
      + "\t\t<useRestriction>false</useRestriction>\r\n"
      + "\t\t<expression>&amp;Период.ДатаНачала</expression>\r\n"
      + "\t\t<use>Always</use>\r\n"
      + "\t</parameter>\r\n");
    // физика файла платформы: BOM, CRLF и конец без перевода строки не сломаны
    assertThat(after).startsWith("﻿<?xml");
    assertThat(after).endsWith("</DataCompositionSchema>");
    // порядок детей корня соответствует последовательности из XSD
    List<String> order = schemaSequence();
    List<String> roots = XmlLines.children(after, List.of("DataCompositionSchema")).stream()
      .map(XmlLines.Node::name)
      .toList();
    assertThat(roots.stream().map(order::indexOf).toList()).isSorted();
    assertThat(roots).containsSubsequence("dataSet", "parameter", "settingsVariant");
    Map<String, Object> added = rows(DcsRead.info(dcs, SchemaVersion.V2_20), "parameters").get(0);
    assertThat(added.get("name")).isEqualTo("Период");
    assertThat(added.get("title")).isEqualTo("Период отчёта");
    assertThat(added.get("expression")).isEqualTo("&Период.ДатаНачала");
    assertThat(added.get("useRestriction")).isEqualTo(false);
    assertThat(added.get("use")).isEqualTo("Always");

    // в схеме с параметрами новый встаёт после последнего из них; использование Auto платформа
    // не пишет - это значение по умолчанию, а значение и ограничение доступности пишет у каждого
    // параметра: минимальный параметр - имя, xsi:nil и useRestriction=false, как в выгрузке 8.3.27
    Path linked = copySchema(LINKED_SCHEMA);
    DcsEdit.addParameter(linked, SchemaVersion.V2_20, Map.of("name", "НовыйПараметр", "use", "Auto"));
    String linkedAfter = Files.readString(linked, StandardCharsets.UTF_8);
    assertThat(parameterChildNames(linkedAfter, "НовыйПараметр")).containsExactly("name", "value", "useRestriction");
    assertThat(linkedAfter).contains("\t<parameter>\r\n"
      + "\t\t<name>НовыйПараметр</name>\r\n"
      + "\t\t<value xsi:nil=\"true\"/>\r\n"
      + "\t\t<useRestriction>false</useRestriction>\r\n"
      + "\t</parameter>\r\n");
    List<String> linkedRoots = XmlLines.children(linkedAfter, List.of("DataCompositionSchema")).stream()
      .map(XmlLines.Node::name)
      .toList();
    assertThat(linkedRoots).containsSubsequence("totalField", "parameter", "settingsVariant");
    List<Map<String, Object>> parameters = rows(DcsRead.info(linked, SchemaVersion.V2_20), "parameters");
    assertThat(parameters).hasSize(12);
    Map<String, Object> minimal = parameters.get(11);
    assertThat(minimal.get("name")).isEqualTo("НовыйПараметр");
    assertThat(minimal).doesNotContainKeys("title", "expression", "use")
      .containsEntry("useRestriction", false).containsEntry("valueTyped", Map.of("nil", true));
  }

  @Test
  void addParameterRefusals() throws Exception {
    Path dcs = copySchema(LINKED_SCHEMA);
    String before = Files.readString(dcs, StandardCharsets.UTF_8);

    assertThatThrownBy(() -> DcsEdit.addParameter(dcs, SchemaVersion.V2_20, Map.of()))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("payload");
    // обязательное по формату имя требует непустой текст
    assertThatThrownBy(() -> DcsEdit.addParameter(dcs, SchemaVersion.V2_20, Map.of("title", "Без имени")))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("name");
    assertThatThrownBy(() -> DcsEdit.addParameter(dcs, SchemaVersion.V2_20, Map.of("name", " ")))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("name");
    assertThatThrownBy(() -> DcsEdit.addParameter(dcs, SchemaVersion.V2_20, Map.of("name", "Контекст")))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("уже есть");
    // тип значения - объект формы cf-dcs-info, а не строка; доступные значения не создаются
    assertThatThrownBy(() -> DcsEdit.addParameter(dcs, SchemaVersion.V2_20, Map.of(
      "name", "Новый", "valueType", "xs:string")))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("должно быть объектом");
    assertThatThrownBy(() -> DcsEdit.addParameter(dcs, SchemaVersion.V2_20, Map.of(
      "name", "Новый", "availableValue", "1")))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("Неизвестное свойство");
    assertThatThrownBy(() -> DcsEdit.addParameter(dcs, SchemaVersion.V2_20, Map.of(
      "name", "Новый", "use", "Sometimes")))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("Always, Auto");
    assertThatThrownBy(() -> DcsEdit.addParameter(dcs, SchemaVersion.V2_20, Map.of(
      "name", "Новый", "useRestriction", "true")))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("булевым");
    assertThatThrownBy(() -> DcsEdit.addParameter(dcs, SchemaVersion.V2_20, Map.of(
      "name", "Новый", "title", 5)))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("строкой");

    assertThat(Files.readString(dcs, StandardCharsets.UTF_8)).isEqualTo(before);
  }

  @Test
  void setParameterEditsSubElementsBySequence() throws Exception {
    Path dcs = copySchema(LINKED_SCHEMA);
    String before = Files.readString(dcs, StandardCharsets.UTF_8);

    // переименование меняет ровно элемент name: ссылок на параметр по имени формат не хранит
    DcsEdit.setParameter(dcs, SchemaVersion.V2_20, "Контекст", Map.of("newName", "КонтекстПроверки"));
    String renamed = Files.readString(dcs, StandardCharsets.UTF_8);
    assertThat(renamed).isEqualTo(before.replace(
      "<name>Контекст</name>", "<name>КонтекстПроверки</name>"));

    // заголовок меняется в своём блоке; ограничение доступности пишется и true, и false - как у платформы
    DcsEdit.setParameter(dcs, SchemaVersion.V2_20, "КонтекстПроверки", Map.of(
      "title", "Контекст проверки", "useRestriction", false));
    String titled = Files.readString(dcs, StandardCharsets.UTF_8);
    assertThat(titled).isEqualTo(renamed
      .replace("<v8:content>Контекст</v8:content>", "<v8:content>Контекст проверки</v8:content>")
      .replace("<useRestriction>true</useRestriction>\r\n\t\t<valueListAllowed>",
        "<useRestriction>false</useRestriction>\r\n\t\t<valueListAllowed>"));

    // использование Auto снимает элемент: отсутствие и есть Auto; существующие тип и значение не тронуты
    DcsEdit.setParameter(dcs, SchemaVersion.V2_20, "ВыводитьОтветственного", Map.of("use", "Auto"));
    String autoUse = Files.readString(dcs, StandardCharsets.UTF_8);
    assertThat(autoUse).isEqualTo(titled.replace("\t\t<use>Always</use>\r\n", ""));
    assertThat(parameterChildNames(autoUse, "ВыводитьОтветственного")).containsExactly(
      "name", "title", "valueType", "value", "useRestriction", "availableAsField");

    // выражение и использование встают на свои места по xs:sequence: выражение сразу после
    // ограничения доступности, использование в конец параметра; тип значения не тронут
    DcsEdit.setParameter(dcs, SchemaVersion.V2_20, "КонтекстПроверки", Map.of(
      "expression", "ВЫБОР КОГДА Истина ТОГДА 1 ИНАЧЕ 0 КОНЕЦ", "use", "Always"));
    String extended = Files.readString(dcs, StandardCharsets.UTF_8);
    assertThat(parameterChildNames(extended, "КонтекстПроверки")).containsExactly(
      "name", "title", "valueType", "useRestriction", "expression",
      "valueListAllowed", "availableAsField", "denyIncompleteValues", "use");

    // замена выражения экранирует & < > как платформа
    DcsEdit.setParameter(dcs, SchemaVersion.V2_20, "ДатаНачала", Map.of(
      "expression", "&Период.ДатаНачала + 1"));
    String reexpressed = Files.readString(dcs, StandardCharsets.UTF_8);
    assertThat(reexpressed).isEqualTo(extended.replace(
      "<expression>&amp;Период.ДатаНачала</expression>",
      "<expression>&amp;Период.ДатаНачала + 1</expression>"));

    // null снимает необязательный элемент целиком
    Map<String, Object> drop = new HashMap<>();
    drop.put("expression", null);
    DcsEdit.setParameter(dcs, SchemaVersion.V2_20, "ДатаНачала", drop);
    assertThat(parameterChildNames(Files.readString(dcs, StandardCharsets.UTF_8), "ДатаНачала"))
      .containsExactly("name", "title", "valueType", "value", "useRestriction");

    List<Map<String, Object>> parameters = rows(DcsRead.info(dcs, SchemaVersion.V2_20), "parameters");
    Map<String, Object> context = byKey(parameters, "name", "КонтекстПроверки");
    assertThat(context.get("title")).isEqualTo("Контекст проверки");
    assertThat(context.get("useRestriction")).isEqualTo(false);
    assertThat(context.get("expression")).isEqualTo("ВЫБОР КОГДА Истина ТОГДА 1 ИНАЧЕ 0 КОНЕЦ");
    assertThat(context.get("use")).isEqualTo("Always");
    // тип значения параметра правками не задет
    assertThat(context.get("type")).isEqualTo("AnyIBRef");
    assertThat(parameters.stream().map(row -> row.get("name"))).doesNotContain("Контекст");
    assertThat(byKey(parameters, "name", "ВыводитьОтветственного")).doesNotContainKey("use");

    // составное значение StandardPeriod параметра Период не тронуто ни одной правкой
    String settled = Files.readString(dcs, StandardCharsets.UTF_8);
    assertThat(settled).contains("<value xsi:type=\"v8:StandardPeriod\">\r\n"
      + "\t\t\t<v8:variant xsi:type=\"v8:StandardPeriodVariant\">Custom</v8:variant>\r\n"
      + "\t\t\t<v8:startDate>0001-01-01T00:00:00</v8:startDate>\r\n"
      + "\t\t\t<v8:endDate>0001-01-01T00:00:00</v8:endDate>\r\n"
      + "\t\t</value>");

    // пустой набор правок ничего не меняет
    DcsEdit.setParameter(dcs, SchemaVersion.V2_20, "Период", Map.of());
    assertThat(Files.readString(dcs, StandardCharsets.UTF_8)).isEqualTo(settled);
  }

  /**
   * Форма платформы у параметра схемы: {@code value} ({@code xsi:nil} без значения) и явный
   * {@code useRestriction} у каждого параметра без списка значений; у параметра со списком пустой
   * список — без {@code value}. Любая настоящая правка доводит до неё параметр без этих элементов,
   * эхо — нет.
   */
  @Test
  void setParameterCompletesPlatformFormOnRealEditOnly() throws Exception {
    Path dcs = copySchema(TWO_OBJECTS_SCHEMA);
    DcsEdit.addParameter(dcs, SchemaVersion.V2_20, Map.of("name", "НачалоПериода"));
    String canonical = Files.readString(dcs, StandardCharsets.UTF_8);
    // Параметр из одного имени, без value и useRestriction
    String old = canonical.replace("\t\t<value xsi:nil=\"true\"/>\r\n\t\t<useRestriction>false</useRestriction>\r\n", "");
    assertThat(parameterChildNames(old, "НачалоПериода")).containsExactly("name");
    Files.writeString(dcs, old, StandardCharsets.UTF_8);

    // Эхо имени и пустой payload — файл не тронут, дополнений нет
    DcsEdit.setParameter(dcs, SchemaVersion.V2_20, "НачалоПериода", Map.of("newName", "НачалоПериода"));
    DcsEdit.setParameter(dcs, SchemaVersion.V2_20, "НачалоПериода", Map.of());
    assertThat(Files.readString(dcs, StandardCharsets.UTF_8)).isEqualTo(old);

    // Настоящая правка (заголовок) — и value с useRestriction на своих местах по xs:sequence
    DcsEdit.setParameter(dcs, SchemaVersion.V2_20, "НачалоПериода", Map.of("title", "Начало"));
    String titled = Files.readString(dcs, StandardCharsets.UTF_8);
    assertThat(titled).contains("\t<parameter>\r\n"
      + "\t\t<name>НачалоПериода</name>\r\n"
      + "\t\t<title xsi:type=\"v8:LocalStringType\">\r\n"
      + "\t\t\t<v8:item>\r\n"
      + "\t\t\t\t<v8:lang>ru</v8:lang>\r\n"
      + "\t\t\t\t<v8:content>Начало</v8:content>\r\n"
      + "\t\t\t</v8:item>\r\n"
      + "\t\t</title>\r\n"
      + "\t\t<value xsi:nil=\"true\"/>\r\n"
      + "\t\t<useRestriction>false</useRestriction>\r\n"
      + "\t</parameter>\r\n");
    assertThat(DcsXsdValidation.validate(dcs).get("ok")).isEqualTo(true);
    // Снять заголовок — вернуться к форме, которую пишет добавление
    Map<String, Object> untitled = new HashMap<>();
    untitled.put("title", null);
    DcsEdit.setParameter(dcs, SchemaVersion.V2_20, "НачалоПериода", untitled);
    assertThat(Files.readString(dcs, StandardCharsets.UTF_8)).isEqualTo(canonical);

    // useRestriction: null — явный false, а не снятие элемента
    DcsEdit.setParameter(dcs, SchemaVersion.V2_20, "НачалоПериода", Map.of("useRestriction", true));
    Map<String, Object> restriction = new HashMap<>();
    restriction.put("useRestriction", null);
    DcsEdit.setParameter(dcs, SchemaVersion.V2_20, "НачалоПериода", restriction);
    assertThat(Files.readString(dcs, StandardCharsets.UTF_8)).isEqualTo(canonical);

    // Включение списка снимает xsi:nil (пустой список — без value), выключение возвращает его
    DcsEdit.setParameter(dcs, SchemaVersion.V2_20, "НачалоПериода", Map.of("valueListAllowed", true));
    String listed = Files.readString(dcs, StandardCharsets.UTF_8);
    assertThat(parameterChildNames(listed, "НачалоПериода")).containsExactly("name", "useRestriction",
      "valueListAllowed");
    assertThat(byKey(rows(DcsRead.info(dcs, SchemaVersion.V2_20), "parameters"), "name", "НачалоПериода"))
      .doesNotContainKey("valueTyped");
    // У параметра со списком «не задано» ({nil: true} и null) — ни одного value: эхо без записи
    DcsEdit.setParameter(dcs, SchemaVersion.V2_20, "НачалоПериода", Map.of("value", Map.of("nil", true)));
    Map<String, Object> none = new HashMap<>();
    none.put("value", null);
    DcsEdit.setParameter(dcs, SchemaVersion.V2_20, "НачалоПериода", none);
    assertThat(Files.readString(dcs, StandardCharsets.UTF_8)).isEqualTo(listed);
    DcsEdit.setParameter(dcs, SchemaVersion.V2_20, "НачалоПериода", Map.of("valueListAllowed", false));
    assertThat(Files.readString(dcs, StandardCharsets.UTF_8)).isEqualTo(canonical);

    // Значение у параметра со списком сохраняется при включении списка и не дублируется при выключении
    DcsEdit.setParameter(dcs, SchemaVersion.V2_20, "НачалоПериода",
      Map.of("value", Map.of("type", "xs:string", "text", "А")));
    String valued = Files.readString(dcs, StandardCharsets.UTF_8);
    DcsEdit.setParameter(dcs, SchemaVersion.V2_20, "НачалоПериода", Map.of("valueListAllowed", true));
    DcsEdit.setParameter(dcs, SchemaVersion.V2_20, "НачалоПериода", Map.of("valueListAllowed", false));
    assertThat(Files.readString(dcs, StandardCharsets.UTF_8)).isEqualTo(valued);
  }

  @Test
  void setParameterAndRemoveParameterRefusals() throws Exception {
    Path dcs = copySchema(LINKED_SCHEMA);
    String before = Files.readString(dcs, StandardCharsets.UTF_8);

    assertThatThrownBy(() -> DcsEdit.setParameter(dcs, SchemaVersion.V2_20, "НетПараметра", Map.of("title", "Х")))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("не найден");
    assertThatThrownBy(() -> DcsEdit.removeParameter(dcs, SchemaVersion.V2_20, "НетПараметра"))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("не найден");
    // поля наборов данных параметрами не адресуются
    assertThatThrownBy(() -> DcsEdit.removeParameter(dcs, SchemaVersion.V2_20, "Выявлено"))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("не найден");
    assertThatThrownBy(() -> DcsEdit.setParameter(dcs, SchemaVersion.V2_20, "Контекст",
      Map.of("newName", "Период")))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("занято");
    assertThatThrownBy(() -> DcsEdit.setParameter(dcs, SchemaVersion.V2_20, "Контекст",
      Map.of("newName", " ")))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("name");
    // ключи правок - имена подэлементов формата; значение - объект Value, а не строка
    assertThatThrownBy(() -> DcsEdit.setParameter(dcs, SchemaVersion.V2_20, "Контекст", Map.of("value", "1")))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("объектом Value");
    assertThatThrownBy(() -> DcsEdit.setParameter(dcs, SchemaVersion.V2_20, "Контекст",
      Map.of("availableValue", "1")))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("Неизвестное свойство");
    assertThatThrownBy(() -> DcsEdit.setParameter(dcs, SchemaVersion.V2_20, "Контекст", Map.of("use", "Никогда")))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("Always, Auto");
    assertThatThrownBy(() -> DcsEdit.setParameter(dcs, SchemaVersion.V2_20, "Контекст",
      Map.of("useRestriction", 1)))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("булевым");
    assertThatThrownBy(() -> DcsEdit.setParameter(dcs, SchemaVersion.V2_20, "Контекст",
      Map.of("expression", 5)))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("строкой");

    assertThat(Files.readString(dcs, StandardCharsets.UTF_8)).isEqualTo(before);
  }

  @Test
  void removeParameterDeletesWholeElement() throws Exception {
    Path dcs = copySchema(LINKED_SCHEMA);
    String before = Files.readString(dcs, StandardCharsets.UTF_8);
    assertThat(rows(DcsRead.info(dcs, SchemaVersion.V2_20), "parameters")).hasSize(11);

    DcsEdit.removeParameter(dcs, SchemaVersion.V2_20, "НадписьОшибка");

    String after = Files.readString(dcs, StandardCharsets.UTF_8);
    assertSingleEditZone(before, after);
    // элемент параметра вырезан целиком, а одноимённое поле набора и упоминания в настройках не тронуты
    assertThat(after).doesNotContain("<name>НадписьОшибка</name>");
    assertThat(after).contains("<dataPath>НадписьОшибка</dataPath>");
    assertThat(after).contains("<dcsset:field>НадписьОшибка</dcsset:field>");
    List<Map<String, Object>> parameters = rows(DcsRead.info(dcs, SchemaVersion.V2_20), "parameters");
    assertThat(parameters).hasSize(10);
    assertThat(parameters.stream().map(row -> row.get("name"))).doesNotContain("НадписьОшибка");
  }

  @Test
  void applyMutationChannelRunsParameterOps() throws Exception {
    Path dcs = copySchema(TWO_OBJECTS_SCHEMA);

    assertThat(applyMutation(Map.of(
      "op", "cf-dcs-add-parameter", "objectXml", dcs.toString(), "schemaVersion", "V2_20",
      "payloadJson", "{\"name\":\"ОбластьДанных\",\"title\":\"Область данных\","
        + "\"useRestriction\":true,\"use\":\"Always\"}"))).isZero();
    assertThat(Files.readString(dcs, StandardCharsets.UTF_8)).contains("\t<parameter>\r\n"
      + "\t\t<name>ОбластьДанных</name>\r\n"
      + "\t\t<title xsi:type=\"v8:LocalStringType\">\r\n"
      + "\t\t\t<v8:item>\r\n"
      + "\t\t\t\t<v8:lang>ru</v8:lang>\r\n"
      + "\t\t\t\t<v8:content>Область данных</v8:content>\r\n"
      + "\t\t\t</v8:item>\r\n"
      + "\t\t</title>\r\n"
      + "\t\t<value xsi:nil=\"true\"/>\r\n"
      + "\t\t<useRestriction>true</useRestriction>\r\n"
      + "\t\t<use>Always</use>\r\n"
      + "\t</parameter>\r\n");

    assertThat(applyMutation(Map.of(
      "op", "cf-dcs-set-parameter", "objectXml", dcs.toString(), "schemaVersion", "V2_20",
      "name", "ОбластьДанных",
      "payloadJson", "{\"newName\":\"Область\",\"use\":\"Auto\",\"expression\":\"&НомерОбласти\"}"))).isZero();
    List<Map<String, Object>> parameters = rows(DcsRead.info(dcs, SchemaVersion.V2_20), "parameters");
    Map<String, Object> renamed = byKey(parameters, "name", "Область");
    assertThat(renamed.get("expression")).isEqualTo("&НомерОбласти");
    assertThat(renamed).doesNotContainKey("use");

    assertThat(applyMutation(Map.of(
      "op", "cf-dcs-remove-parameter", "objectXml", dcs.toString(), "schemaVersion", "V2_20",
      "name", "Область"))).isZero();
    assertThat(rows(DcsRead.info(dcs, SchemaVersion.V2_20), "parameters")).isEmpty();

    // отказ операции доходит из канала кодом 2, файл не меняется
    String before = Files.readString(dcs, StandardCharsets.UTF_8);
    assertThat(applyMutation(Map.of(
      "op", "cf-dcs-remove-parameter", "objectXml", dcs.toString(), "schemaVersion", "V2_20",
      "name", "Область"))).isEqualTo(2);
    assertThat(applyMutation(Map.of(
      "op", "cf-dcs-set-parameter", "objectXml", dcs.toString(), "schemaVersion", "V2_20",
      "payloadJson", "{\"title\":\"Х\"}"))).isEqualTo(2);
    assertThat(Files.readString(dcs, StandardCharsets.UTF_8)).isEqualTo(before);
  }

  @Test
  void applyMutationChannelEditsEdtTemplateDcs() throws Exception {
    // В проекте 1С:EDT схема компоновки - тот же формат файла, другое имя: Template.dcs
    // вместо Ext/Template.xml. Отказ refuseEdtWrite ловит только файлы .mdo, поэтому
    // мутации СКД по .dcs идут общим кодом правки схемы - закрепляем это каналом.
    Path source = Path.of(System.getProperty("fixtures.ssl31edt.root"), "ssl31", "src",
      "Reports", "РезультатыПроверкиУчета", "Templates", "ОсновнаяСхемаКомпоновкиДанных", "Template.dcs");
    Path dcs = Files.createTempDirectory(tempDir, "dcs").resolve("Template.dcs");
    Files.copy(source, dcs, StandardCopyOption.REPLACE_EXISTING);
    String before = Files.readString(dcs, StandardCharsets.UTF_8);

    assertThat(applyMutation(Map.of(
      "op", "cf-dcs-add-parameter", "objectXml", dcs.toString(), "schemaVersion", "V2_20",
      "payloadJson", "{\"name\":\"ОбластьДанных\",\"use\":\"Always\"}"))).isZero();
    String after = Files.readString(dcs, StandardCharsets.UTF_8);
    assertSingleEditZone(before, after);
    Map<String, Object> parameter = byKey(
      rows(DcsRead.info(dcs, SchemaVersion.V2_20), "parameters"), "name", "ОбластьДанных");
    assertThat(parameter.get("use")).isEqualTo("Always");

    assertThat(applyMutation(Map.of(
      "op", "cf-dcs-set-data-set-props", "objectXml", dcs.toString(), "schemaVersion", "V2_20",
      "name", "ОсновнойЗапрос", "payloadJson", "{\"autoFillFields\":false}"))).isZero();
    Map<String, Object> set = byKey(
      rows(DcsRead.info(dcs, SchemaVersion.V2_20), "dataSets"), "name", "ОсновнойЗапрос");
    assertThat(set.get("autoFillFields")).isEqualTo(false);
  }

  /** Запуск канала apply-mutation с параметрами из UTF-8 JSON-файла. */
  private int applyMutation(Map<String, ?> params) throws Exception {
    Path paramsFile = Files.createTempFile(tempDir, "params", ".json");
    Files.writeString(paramsFile, new Gson().toJson(params), StandardCharsets.UTF_8);
    return new CommandLine(new DesignerXmlCli()).execute("apply-mutation", "--params", paramsFile.toString());
  }

  /** Копия схемы из submodule во временный каталог: индекс фикстур не трогается. */
  private Path copySchema(String relativePath) throws Exception {
    Path source = Ssl31SubmodulePaths.projectRoot().resolve(relativePath);
    Path copy = Files.createTempDirectory(tempDir, "dcs").resolve("Template.xml");
    Files.copy(source, copy, StandardCopyOption.REPLACE_EXISTING);
    return copy;
  }

  /**
   * Правка одной непрерывной зоной: вне неё файл совпадает байт в байт. Общий префикс и
   * суффикс двух текстов вместе покрывают весь короткий из них.
   */
  private static void assertSingleEditZone(String before, String after) {
    int shorter = Math.min(before.length(), after.length());
    int prefix = 0;
    while (prefix < shorter && before.charAt(prefix) == after.charAt(prefix)) {
      prefix++;
    }
    int suffix = 0;
    while (suffix < shorter - prefix
      && before.charAt(before.length() - 1 - suffix) == after.charAt(after.length() - 1 - suffix)) {
      suffix++;
    }
    assertThat(prefix + suffix).as("правка должна остаться одной зоной").isEqualTo(shorter);
  }

  /** Имена прямых подэлементов набора данных верхнего уровня в порядке файла. */
  private static List<String> dataSetChildNames(String xml, String setName) {
    XmlLines.Node set = dataSetNode(xml, setName);
    return XmlLines.childrenAt(xml, set.start()).stream().map(XmlLines.Node::name).toList();
  }

  /** Имена прямых подэлементов поля набора в порядке файла. */
  private static List<String> fieldChildNames(String xml, String setName, String dataPath) {
    XmlLines.Node set = dataSetNode(xml, setName);
    for (XmlLines.Node field : XmlLines.childrenAt(xml, set.start())) {
      if ("field".equals(field.name()) && dataPath.equals(field.leaves().get("dataPath"))) {
        return XmlLines.childrenAt(xml, field.start()).stream().map(XmlLines.Node::name).toList();
      }
    }
    throw new AssertionError("поле не найдено: " + setName + "/" + dataPath);
  }

  /** Имена прямых подэлементов ресурса по его порядковому номеру в файле. */
  private static List<String> totalFieldChildNames(String xml, int index) {
    List<XmlLines.Node> totals = XmlLines.children(xml, List.of("DataCompositionSchema")).stream()
      .filter(node -> "totalField".equals(node.name()))
      .toList();
    return XmlLines.childrenAt(xml, totals.get(index).start()).stream().map(XmlLines.Node::name).toList();
  }

  /** Имена прямых подэлементов вычисляемого поля корня по пути к данным. */
  private static List<String> calculatedFieldChildNames(String xml, String dataPath) {
    for (XmlLines.Node node : XmlLines.children(xml, List.of("DataCompositionSchema"))) {
      if ("calculatedField".equals(node.name()) && dataPath.equals(node.leaves().get("dataPath"))) {
        return XmlLines.childrenAt(xml, node.start()).stream().map(XmlLines.Node::name).toList();
      }
    }
    throw new AssertionError("вычисляемое поле не найдено: " + dataPath);
  }

  /** Имена прямых подэлементов связи наборов по её порядковому номеру в файле. */
  private static List<String> linkChildNames(String xml, int index) {
    List<XmlLines.Node> links = XmlLines.children(xml, List.of("DataCompositionSchema")).stream()
      .filter(node -> "dataSetLink".equals(node.name()))
      .toList();
    return XmlLines.childrenAt(xml, links.get(index).start()).stream().map(XmlLines.Node::name).toList();
  }

  /** Имена прямых подэлементов параметра схемы по его имени. */
  private static List<String> parameterChildNames(String xml, String name) {
    for (XmlLines.Node node : XmlLines.children(xml, List.of("DataCompositionSchema"))) {
      if ("parameter".equals(node.name()) && name.equals(node.leaves().get("name"))) {
        return XmlLines.childrenAt(xml, node.start()).stream().map(XmlLines.Node::name).toList();
      }
    }
    throw new AssertionError("параметр не найден: " + name);
  }

  private static XmlLines.Node dataSetNode(String xml, String setName) {
    for (XmlLines.Node node : XmlLines.children(xml, List.of("DataCompositionSchema"))) {
      if ("dataSet".equals(node.name()) && setName.equals(node.leaves().get("name"))) {
        return node;
      }
    }
    throw new AssertionError("набор данных не найден: " + setName);
  }

  /** Порядок элементов корня схемы компоновки по XSD формата. */
  private static List<String> schemaSequence() throws Exception {
    Path xsd = Path.of(System.getProperty("xsd.root"), "schemas", "designer", "2.20",
      "v8.1c.ru-8.1-data-composition-system-schema.xsd");
    String text = Files.readString(xsd);
    int type = text.indexOf("<xs:complexType name=\"DataCompositionSchema\">");
    String sequence = text.substring(type, text.indexOf("</xs:complexType>", type));
    java.util.regex.Matcher element = java.util.regex.Pattern.compile("<xs:element name=\"(\\w+)\"").matcher(sequence);
    List<String> out = new java.util.ArrayList<>();
    while (element.find()) {
      out.add(element.group(1));
    }
    assertThat(out).contains("dataSource", "dataSet", "dataSetLink");
    return out;
  }
}
