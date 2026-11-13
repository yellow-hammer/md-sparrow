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

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Чтение вариантов настроек схем компоновки ({@code cf-dcs-settings-info}) на реальных схемах
 * ssl31: список вариантов только из прямых детей корня, один вариант целиком — структура с
 * таблицами, диаграммами и вложенными группировками, выбранные поля с
 * двумя формами заголовка, отбор с повторяемым right и группами, поля группировки с границами
 * периода, параметры данных и вывода с подчинёнными параметрами, условное оформление с
 * областями использования, типизированные значения с {@code xsi:type} дословно из файла и
 * {@code opaque} вне белого списка записи, пользовательские поля read-only и хвосты секций
 * ({@code viewMode}/{@code userSettingID}). Канал {@code read-json} отдаёт валидный JSON
 * с кириллицей в UTF-8, в том числе для схемы проекта 1С:EDT ({@code Template.dcs}).
 */
class DcsSettingsReadTest {

  /** Пять вариантов: таблицы со строками, диаграммы с точками, группы в три уровня, 24 УО. */
  private static final String LOG_ANALYSIS =
    "src/cf/Reports/АнализЖурналаРегистрации/Templates/ОсновнаяСхемаКомпоновкиДанных/Ext/Template.xml";

  /** Вложенный отчёт, вложенные схемы со своими вариантами, группы отбора, списки значений. */
  private static final String DEMO_FILES =
    "src/cf/Reports/_ДемоФайлы/Templates/ОсновнаяСхемаКомпоновкиДанных/Ext/Template.xml";

  /** Таблица column+row, d8p1:ChartSplineMode (неустойчивый префикс), области использования УО. */
  private static final String PERFORMANCE =
    "src/cf/Reports/ОценкаПроизводительности/Templates/ОсновнаяСхемаКомпоновкиДанных/Ext/Template.xml";

  /** Двойной right при NotInList и хвосты секций с userSettingID. */
  private static final String SURVEY =
    "src/cf/Reports/АналитическийОтчетПоАнкетированию/Templates/ОсновнаяСхемаКомпоновкиДанных/Ext/Template.xml";

  /** Шесть вариантов, periodAdditionType Month/Day, границы периода с xsi:nil. */
  private static final String DYNAMICS =
    "src/cf/Reports/_ДемоДинамикаИзмененийФайлов/Templates/ОсновнаяСхемаКомпоновкиДанных/Ext/Template.xml";

  /** Базовый вариант «Основной»: все секции корня по образцу файла, именованные группировки. */
  private static final String CHECKS =
    "src/cf/Reports/РезультатыПроверкиУчета/Templates/ОсновнаяСхемаКомпоновкиДанных/Ext/Template.xml";

  /** Группа ИЛИ с вложенными И, right со стандартной датой начала. */
  private static final String TASKS =
    "src/cf/Reports/Задачи/Templates/ОсновнаяСхемаКомпоновкиДанных/Ext/Template.xml";

  /** Заголовки полей выбора прямым элементом title (форма string). */
  private static final String JOBS =
    "src/cf/Reports/Задания/Templates/Макет/Ext/Template.xml";

  /** Пользовательские поля UserFieldExpression. */
  private static final String USER_FIELDS =
    "src/cf/Reports/ПользователиПоПодразделениям/Templates/Макет/Ext/Template.xml";

  @TempDir Path tempDir;

  @Test
  void listsVariantsOfRootOnly() throws Exception {
    List<Map<String, Object>> logVariants = variants(info(LOG_ANALYSIS, null));
    assertThat(logVariants).extracting(row -> row.get("name")).containsExactly(
      "АнализАктивностиПользователей", "АнализАктивностиПодразделений", "АктивностьПользователя",
      "КонтрольЖурналаРегистрации", "ПродолжительностьРаботыРегламентныхЗаданий");
    assertThat(logVariants.get(0).get("presentation")).isEqualTo("Анализ активности пользователей");
    // без имени варианта настройки не раскрываются: загрузка ленивая
    assertThat(logVariants.get(0)).doesNotContainKey("settings");

    // вложенные схемы _ДемоФайлы несут свои settingsVariant — в список попадают только дети корня
    List<Map<String, Object>> fileVariants = variants(info(DEMO_FILES, null));
    assertThat(fileVariants).extracting(row -> row.get("name")).containsExactly(
      "Основной", "ПоРазмеру", "ПоТипам", "ПоВерсиям", "Вспомогательный");

    List<Map<String, Object>> dynamicsVariants = variants(info(DYNAMICS, null));
    assertThat(dynamicsVariants).hasSize(6);
    // представление — строка на языке конфигурации, из многоязычной локализованной строки
    assertThat(dynamicsVariants.get(0).get("presentation")).isEqualTo("Демо: Динамика изменений файлов");
  }

  @Test
  void unknownDuplicateAndForeignFileRefused() throws Exception {
    Path source = Ssl31SubmodulePaths.projectRoot().resolve(DYNAMICS);
    assertThatThrownBy(() -> DcsSettingsRead.info(source, SchemaVersion.V2_20, "НетТакогоВарианта"))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("не найден");

    // XSD уникальность имён не контролирует: дубль — отказ, а не молчаливый выбор первого
    Path copy = Files.createTempDirectory(tempDir, "dcs").resolve("Template.xml");
    String text = Files.readString(source, StandardCharsets.UTF_8);
    Files.writeString(copy, text.replace(
      "<dcsset:name>Дополнительный2</dcsset:name>",
      "<dcsset:name>Дополнительный1</dcsset:name>"), StandardCharsets.UTF_8);
    assertThatThrownBy(() -> DcsSettingsRead.info(copy, SchemaVersion.V2_20, "Дополнительный1"))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("дважды");

    Path foreign = Files.createTempDirectory(tempDir, "dcs").resolve("Template.xml");
    Files.writeString(foreign, "<?xml version=\"1.0\"?><Other/>", StandardCharsets.UTF_8);
    assertThatThrownBy(() -> DcsSettingsRead.info(foreign, SchemaVersion.V2_20, null))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("не является схемой компоновки");
  }

  @Test
  void readsStructureTablesChartsAndNestedGroups() throws Exception {
    Map<String, Object> settings = settings(LOG_ANALYSIS, "АнализАктивностиПользователей");

    List<Map<String, Object>> structure = list(settings.get("structure"));
    assertThat(structure).extracting(node -> node.get("kind"))
      .containsExactly("table", "group", "chart", "chart");

    // таблица: одна строка-группировка, колонок в файле нет — нет и ключа
    Map<String, Object> table = structure.get(0);
    List<Map<String, Object>> rows = list(table.get("rows"));
    assertThat(rows).hasSize(1);
    assertThat(table).doesNotContainKey("columns");
    Map<String, Object> row = rows.get(0);
    assertThat(row.get("kind")).isEqualTo("group");

    // папка выбранных полей: заголовок строкой языка конфигурации, форма lws, поля прямыми детьми
    List<Map<String, Object>> selected = items(row, "selection");
    assertThat(selected).hasSize(7);
    Map<String, Object> folder = byKey(selected, "title", "Документы");
    assertThat(folder.get("kind")).isEqualTo("folder");
    assertThat(folder.get("titleKind")).isEqualTo("lws");
    assertThat(folder.get("placement")).isEqualTo("Auto");
    List<Map<String, Object>> folderItems = list(folder.get("items"));
    assertThat(folderItems).hasSize(3);
    assertThat(folderItems.get(0).get("field")).isEqualTo("СозданоДокументов");
    assertThat(folderItems.get(0).get("title")).isEqualTo("Введено");
    assertThat(folderItems.get(1).get("use")).isEqualTo(false);

    // безтиповая вложенная группировка строки таблицы — обычный узел structure вида group
    List<Map<String, Object>> nested = list(row.get("structure"));
    assertThat(nested).hasSize(1);
    assertThat(nested.get(0).get("kind")).isEqualTo("group");
    assertThat(list(nested.get(0).get("groupFields")).get(0).get("field")).isEqualTo("Неделя");

    // УО строки: дубли полей сохраняются, пустой отбор — пустым списком
    Map<String, Object> rowAppearance = items(row, "conditionalAppearance").get(0);
    assertThat(list(rowAppearance.get("fields"))).hasSize(2)
      .allSatisfy(field -> assertThat(field.get("field")).isEqualTo("СозданоДокументов.ПроцентОбщий"));
    assertThat(list(rowAppearance.get("filter"))).isEmpty();
    Map<String, Object> format = list(rowAppearance.get("appearance")).get(0);
    assertThat(format.get("parameter")).isEqualTo("Формат");
    assertThat(map(list(format.get("value")).get(0)).get("decoded")).isEqualTo("ЧДЦ=1");

    // группировка в три уровня: вложенные узлы идут деревом structure
    Map<String, Object> month = structure.get(1);
    Map<String, Object> monthField = list(month.get("groupFields")).get(0);
    assertThat(monthField.get("field")).isEqualTo("Месяц");
    assertThat(monthField.get("groupType")).isEqualTo("Items");
    assertThat(monthField.get("periodAdditionType")).isEqualTo("None");
    assertThat(map(monthField.get("periodAdditionBegin")))
      .containsEntry("type", "xs:dateTime")
      .containsEntry("text", "0001-01-01T00:00:00");
    Map<String, Object> second = list(month.get("structure")).get(0);
    Map<String, Object> third = list(second.get("structure")).get(0);
    assertThat(items(third, "selection").get(0).get("field"))
      .isEqualTo("СписокОдновременноРаботавшихПользователей");
  }

  @Test
  void readsChartSubParametersAndUserSettingIds() throws Exception {
    Map<String, Object> settings = settings(LOG_ANALYSIS, "АнализАктивностиПользователей");

    // параметры данных: xsi:type дословно из файла, xsi:nil — массивом из одного элемента
    List<Map<String, Object>> dataParameters = items(settings, "dataParameters");
    Map<String, Object> user = byKey(dataParameters, "parameter", "Пользователь");
    assertThat(user.get("use")).isEqualTo(false);
    assertThat(user.get("viewMode")).isEqualTo("Inaccessible");
    assertThat(map(list(user.get("value")).get(0)))
      .containsEntry("type", "dcscor:DesignTimeValue")
      .containsEntry("text", "Справочник.Пользователи.ПустаяСсылка")
      .doesNotContainKey("opaque");
    Map<String, Object> userGroups = byKey(dataParameters, "parameter", "ПользователиИГруппы");
    assertThat(list(userGroups.get("value"))).containsExactly(Map.of("nil", true));
    assertThat(userGroups.get("userSettingID")).isEqualTo("68f497d5-6ccd-4e09-b808-41381e412b71");
    Map<String, Object> period = byKey(dataParameters, "parameter", "Период");
    assertThat(map(map(list(period.get("value")).get(0)).get("period")))
      .containsEntry("variant", "FromBeginningOfThisWeek")
      .doesNotContainKeys("startDate", "endDate");
    Map<String, Object> serverDates = byKey(dataParameters, "parameter", "ДатыВЧасовомПоясеСервера");
    assertThat(map(list(serverDates.get("value")).get(0)).get("decoded")).isEqualTo(false);

    assertThat(items(settings, "order").get(0))
      .containsEntry("kind", "field")
      .containsEntry("field", "Дата")
      .containsEntry("orderType", "Desc");

    // диаграмма: точки — группировки, подчинённые параметры ТипДиаграммы.* — вложенными items
    Map<String, Object> chart = list(settings.get("structure")).get(2);
    assertThat(chart.get("kind")).isEqualTo("chart");
    assertThat(chart.get("viewMode")).isEqualTo("Inaccessible");
    assertThat(chart.get("userSettingID")).isEqualTo("b107874d-909e-4392-aa4f-0db8e22da5bd");
    assertThat(chart).doesNotContainKey("series");
    List<Map<String, Object>> points = list(chart.get("points"));
    assertThat(points).hasSize(1);
    assertThat(points.get(0).get("kind")).isEqualTo("group");
    assertThat(list(points.get(0).get("groupFields")).get(0).get("field")).isEqualTo("Пользователь");

    // тип диаграммы записываем, его под-параметры другого типа v8ui - только для чтения
    Map<String, Object> chartType = byKey(items(chart, "outputParameters"), "parameter", "ТипДиаграммы");
    assertThat(map(list(chartType.get("value")).get(0)))
      .containsEntry("type", "v8ui:ChartType")
      .containsEntry("text", "Column3D")
      .doesNotContainKey("opaque");
    assertThat(chartType.get("viewMode")).isEqualTo("Inaccessible");
    assertThat(chartType.get("userSettingID")).isEqualTo("c9fb7cc5-1a5c-4d86-8b12-9cd52a653e8b");
    assertThat(chartType.get("userSettingPresentation"))
      .isEqualTo("Диаграмма \"Введено документов и справочников\"");
    List<Map<String, Object>> subParameters = list(chartType.get("items"));
    assertThat(subParameters).hasSize(1);
    assertThat(subParameters.get(0).get("parameter")).isEqualTo("ТипДиаграммы.ВидПодписей");
    assertThat(map(list(subParameters.get(0).get("value")).get(0)))
      .containsEntry("type", "v8ui:ChartLabelType")
      .containsEntry("text", "Value")
      .containsEntry("opaque", true);
  }

  @Test
  void readsNestedObjectFilterGroupsAndValueLists() throws Exception {
    // вложенный отчёт — узел nestedObject: его настройки не раскрываются и не адресуются
    Map<String, Object> byTypes = settings(DEMO_FILES, "ПоТипам");
    Map<String, Object> nested = byKey(list(byTypes.get("structure")), "kind", "nestedObject");
    assertThat(nested.get("objectID")).isEqualTo("Вложенный");
    assertThat(nested).doesNotContainKeys("structure", "settings", "rows", "selection");

    Map<String, Object> auxiliary = settings(DEMO_FILES, "Вспомогательный");

    // выбранные поля: папка без заголовка, хвост секции с userSettingID не теряется
    Map<String, Object> selection = map(auxiliary.get("selection"));
    List<Map<String, Object>> selected = list(selection.get("items"));
    assertThat(selected).hasSize(8);
    Map<String, Object> folder = selected.get(1);
    assertThat(folder.get("kind")).isEqualTo("folder");
    assertThat(folder).doesNotContainKeys("title", "titleKind");
    assertThat(folder.get("placement")).isEqualTo("Together");
    assertThat(list(folder.get("items"))).hasSize(2);
    assertThat(selection.get("viewMode")).isEqualTo("Normal");
    assertThat(selection.get("userSettingID")).isEqualTo("b4dbf432-1217-4e43-b0c8-249c7474c0ae");

    // отбор: пустой список значений, группа И с прямыми детьми, right-поле, представления
    Map<String, Object> filter = map(auxiliary.get("filter"));
    List<Map<String, Object>> filterItems = list(filter.get("items"));
    assertThat(filterItems).hasSize(6);
    Map<String, Object> inList = filterItems.get(0);
    assertThat(inList.get("kind")).isEqualTo("comparison");
    assertThat(inList.get("comparisonType")).isEqualTo("InList");
    Map<String, Object> emptyList = map(list(inList.get("right")).get(0));
    assertThat(emptyList.get("type")).isEqualTo("v8:ValueListType");
    assertThat(list(map(emptyList.get("list")).get("items"))).isEmpty();

    Map<String, Object> andGroup = filterItems.get(3);
    assertThat(andGroup.get("kind")).isEqualTo("group");
    assertThat(andGroup.get("groupType")).isEqualTo("AndGroup");
    assertThat(andGroup.get("use")).isEqualTo(false);
    assertThat(andGroup.get("userSettingPresentation")).isEqualTo("Только занятые до 2015 г.");
    List<Map<String, Object>> grouped = list(andGroup.get("items"));
    assertThat(grouped).hasSize(2);
    assertThat(grouped).allSatisfy(item -> {
      assertThat(item.get("kind")).isEqualTo("comparison");
      assertThat(item.get("viewMode")).isEqualTo("Inaccessible");
    });
    assertThat(map(list(grouped.get(0).get("right")).get(0)))
      .containsEntry("type", "xs:dateTime")
      .containsEntry("text", "2015-01-01T00:00:00");

    Map<String, Object> notEqual = filterItems.get(5);
    assertThat(map(list(notEqual.get("right")).get(0)))
      .containsEntry("type", "dcscor:Field")
      .containsEntry("text", "ТекущаяВерсия")
      .doesNotContainKey("opaque");
    assertThat(notEqual.get("presentation")).isEqualTo("Только неактивные версии");
    assertThat(filter.get("userSettingID")).isEqualTo("f801037b-30af-491e-9d6b-0bc813f70472");
    assertThat(map(auxiliary.get("order")).get("userSettingID"))
      .isEqualTo("f5b3d460-43e9-4c6d-9467-94d03a1669cb");

    // цвет — тип из белого списка записи, без opaque
    Map<String, Object> appearance = items(auxiliary, "conditionalAppearance").get(0);
    Map<String, Object> color = byKey(list(appearance.get("appearance")), "parameter", "ЦветФона");
    assertThat(map(list(color.get("value")).get(0)))
      .containsEntry("type", "v8ui:Color")
      .containsEntry("text", "web:HoneyDew")
      .doesNotContainKey("opaque");
  }

  @Test
  void readsOpaqueChartEnumUseInAreasAndTableColumns() throws Exception {
    Map<String, Object> settings = settings(PERFORMANCE, "ОценкаПроизводительностиПоКлючевымОперациям");

    // УО: поля, отбор группой с прямыми детьми, оформление и явные области DontUse
    Map<String, Object> grey = Stream.concat(Stream.of(settings), flatNodes(settings).stream())
      .filter(owner -> owner.get("conditionalAppearance") != null)
      .flatMap(owner -> items(owner, "conditionalAppearance").stream())
      .filter(item -> item.get("appearance") != null && list(item.get("appearance")).stream()
        .anyMatch(parameter -> parameter.get("value") != null
          && "#CCCCCC".equals(map(list(parameter.get("value")).get(0)).get("text"))))
      .findFirst().orElseThrow();
    assertThat(map(grey.get("useIn"))).containsExactly(
      Map.entry("fieldsHeader", "DontUse"),
      Map.entry("header", "DontUse"),
      Map.entry("parameters", "DontUse"),
      Map.entry("filter", "DontUse"));
    assertThat(list(grey.get("fields")).get(0).get("field")).isEqualTo("APDEX");
    Map<String, Object> condition = list(grey.get("filter")).get(0);
    assertThat(condition.get("kind")).isEqualTo("group");
    assertThat(condition.get("groupType")).isEqualTo("AndGroup");
    assertThat(list(condition.get("items"))).hasSize(3);

    // неустойчивый префикс d8p1 — тип дословно из файла, запись закрыта (opaque)
    Map<String, Object> smoothing = flatNodes(settings).stream()
      .filter(node -> node.get("outputParameters") != null)
      .flatMap(node -> items(node, "outputParameters").stream())
      .filter(parameter -> "ТипДиаграммы".equals(parameter.get("parameter"))
        && parameter.get("items") != null)
      .flatMap(parameter -> list(parameter.get("items")).stream())
      .filter(sub -> "ТипДиаграммы.РежимСглаживания".equals(sub.get("parameter")))
      .findFirst().orElseThrow();
    assertThat(map(list(smoothing.get("value")).get(0))).containsExactly(
      Map.entry("type", "d8p1:ChartSplineMode"),
      Map.entry("text", "SmoothCurve"),
      Map.entry("opaque", true));

    // таблица с колонками и строками: колонки перед строками, обе — группировки
    Map<String, Object> inColumns = settings(PERFORMANCE, "ОценкаПроизводительностиПериодВКолонках");
    Map<String, Object> table = byKey(list(inColumns.get("structure")), "kind", "table");
    List<Map<String, Object>> columns = list(table.get("columns"));
    List<Map<String, Object>> rows = list(table.get("rows"));
    assertThat(columns).hasSize(1);
    assertThat(rows).hasSize(1);
    assertThat(columns.get(0).get("kind")).isEqualTo("group");
    assertThat(list(columns.get(0).get("groupFields")).get(0).get("field")).isEqualTo("ПериодЗамера");
    assertThat(list(rows.get(0).get("groupFields"))).hasSize(3);
  }

  @Test
  void readsRepeatedRightAndSectionTails() throws Exception {
    Map<String, Object> settings = settings(SURVEY, "ПросмотрОтветовПростыеВопросы");
    Map<String, Object> filter = map(settings.get("filter"));
    List<Map<String, Object>> filterItems = list(filter.get("items"));
    assertThat(filterItems).hasSize(6);

    // right в XSD повторяем: NotInList хранит два значения повтором элемента, не списком
    Map<String, Object> notInList = filterItems.get(1);
    assertThat(notInList.get("left")).isEqualTo("Вопрос.ТипВопроса");
    assertThat(notInList.get("comparisonType")).isEqualTo("NotInList");
    assertThat(notInList.get("viewMode")).isEqualTo("Inaccessible");
    List<Map<String, Object>> right = list(notInList.get("right"));
    assertThat(right).hasSize(2);
    assertThat(right).extracting(value -> value.get("type"))
      .containsExactly("dcscor:DesignTimeValue", "dcscor:DesignTimeValue");
    assertThat(right).extracting(value -> value.get("text")).containsExactly(
      "Перечисление.ТипыВопросовШаблонаАнкеты.Табличный",
      "Перечисление.ТипыВопросовШаблонаАнкеты.Комплексный");

    // сравнение без right (значение не задано) — ключа нет
    assertThat(filterItems.get(2)).doesNotContainKey("right");

    // хвосты секций: viewMode и неприкосновенный userSettingID самой коллекции
    assertThat(filter.get("viewMode")).isEqualTo("Normal");
    assertThat(filter.get("userSettingID")).isEqualTo("8e6cb7be-1c87-43ae-813b-fac7bc6b4983");
    assertThat(map(settings.get("selection")).get("userSettingID"))
      .isEqualTo("cc59db21-5bb3-4e5f-82c9-73dac56b9a3e");
  }

  @Test
  void readsPeriodAdditionsAndNilBoundaries() throws Exception {
    Map<String, Object> settings = settings(DYNAMICS, "Основной");
    List<Map<String, Object>> structure = list(settings.get("structure"));
    assertThat(structure).extracting(node -> node.get("kind")).containsExactly("group", "chart", "chart");

    Map<String, Object> monthly = structure.get(0);
    assertThat(list(monthly.get("groupFields")).get(0).get("periodAdditionType")).isEqualTo("Month");
    // пустая секция с одним хвостом обычна в выгрузках и переживает чтение
    Map<String, Object> groupFilter = map(monthly.get("filter"));
    assertThat(list(groupFilter.get("items"))).isEmpty();
    assertThat(groupFilter.get("viewMode")).isEqualTo("Normal");
    assertThat(items(monthly, "order").get(0).get("kind")).isEqualTo("auto");
    assertThat(monthly.get("userSettingID")).isEqualTo("bd57fb4b-2995-4ac0-9415-6f8bd1482b54");

    // границы добавления периода: xsi:nil — {nil:true}, дата — xs:dateTime дословно
    Map<String, Object> daily = list(structure.get(2).get("points")).get(0);
    Map<String, Object> dayField = list(daily.get("groupFields")).get(0);
    assertThat(dayField.get("periodAdditionType")).isEqualTo("Day");
    assertThat(map(dayField.get("periodAdditionBegin"))).containsExactly(Map.entry("nil", true));
    assertThat(map(dayField.get("periodAdditionEnd")))
      .containsEntry("type", "xs:dateTime")
      .containsEntry("text", "0001-01-01T00:00:00");

    // стандартный период с заданными границами
    Map<String, Object> period = byKey(items(settings, "dataParameters"), "parameter", "Период");
    assertThat(map(map(list(period.get("value")).get(0)).get("period"))).containsExactly(
      Map.entry("variant", "Custom"),
      Map.entry("startDate", "2014-01-01T00:00:00"),
      Map.entry("endDate", "2014-12-31T23:59:59"));

    // перечисление dcscor записываемо (без opaque), пустая локализованная строка — без decoded
    List<Map<String, Object>> output = items(settings, "outputParameters");
    Map<String, Object> totals = byKey(output, "parameter", "ВертикальноеРасположениеОбщихИтогов");
    assertThat(map(list(totals.get("value")).get(0)))
      .containsEntry("type", "dcscor:DataCompositionTotalPlacement")
      .containsEntry("text", "None")
      .doesNotContainKey("opaque");
    Map<String, Object> title = byKey(output, "parameter", "Заголовок");
    assertThat(title.get("use")).isEqualTo(false);
    assertThat(map(list(title.get("value")).get(0)))
      .containsEntry("type", "v8:LocalStringType")
      .doesNotContainKeys("decoded", "text");
  }

  @Test
  void readsMainVariantSectionsOfChecksReport() throws Exception {
    Map<String, Object> settings = settings(CHECKS, "Основной");
    assertThat(items(settings, "selection")).extracting(item -> item.get("field"))
      .containsExactly("ПроблемныйОбъект", "КоличествоПроблемСОбъектом");
    assertThat(items(settings, "filter")).hasSize(4);
    assertThat(items(settings, "dataParameters")).hasSize(2);
    assertThat(items(settings, "outputParameters")).isNotEmpty();
    assertThat(items(settings, "conditionalAppearance")).isNotEmpty();
    // сортировки у корня в файле нет — нет и ключа
    assertThat(settings).doesNotContainKey("order");

    // userSettingPresentation элемента отбора отдаётся строкой языка конфигурации
    assertThat(items(settings, "filter").get(1).get("userSettingPresentation"))
      .isEqualTo("Правило проверки");

    // именованная группировка с автосортировкой
    Map<String, Object> first = list(settings.get("structure")).get(0);
    assertThat(first.get("kind")).isEqualTo("group");
    assertThat(first.get("name")).isEqualTo("ИнформацияОПоследнейПроверке");
    assertThat(items(first, "order").get(0).get("kind")).isEqualTo("auto");
  }

  @Test
  void readsStringTitlesUserFieldsAndOrGroups() throws Exception {
    // заголовок прямым элементом title — форма string; писатель вернёт ту же форму
    Map<String, Object> jobs = settings(JOBS, "СписокЗаданий");
    Map<String, Object> started = byKey(items(jobs, "selection"), "field", "Дата");
    assertThat(started.get("title")).isEqualTo("Начато");
    assertThat(started.get("titleKind")).isEqualTo("string");

    // группа ИЛИ (в отборе элемента УО) с вложенными группами И; right — стандартная дата начала
    Map<String, Object> current = settings(TASKS, "ТекущиеЗадачи");
    Map<String, Object> orGroup = Stream.concat(Stream.of(current), flatNodes(current).stream())
      .filter(owner -> owner.get("conditionalAppearance") != null)
      .flatMap(owner -> items(owner, "conditionalAppearance").stream())
      .filter(appearanceItem -> appearanceItem.get("filter") != null)
      .flatMap(appearanceItem -> list(appearanceItem.get("filter")).stream())
      .filter(item -> "OrGroup".equals(item.get("groupType")))
      .findFirst().orElseThrow();
    List<Map<String, Object>> alternatives = list(orGroup.get("items"));
    assertThat(alternatives).hasSize(2);
    assertThat(alternatives)
      .allSatisfy(group -> assertThat(group.get("groupType")).isEqualTo("AndGroup"));
    Map<String, Object> deadline = list(alternatives.get(0).get("items")).get(1);
    Map<String, Object> beginning = map(list(deadline.get("right")).get(0));
    assertThat(beginning.get("type")).isEqualTo("v8:StandardBeginningDate");
    assertThat(map(beginning.get("beginningDate"))).containsExactly(
      Map.entry("variant", "BeginningOfNextDay"));

    // пользовательские поля read-only: вид, путь, заголовок и выражения
    Map<String, Object> departments = settings(USER_FIELDS, "Основной");
    List<Map<String, Object>> userFields = list(departments.get("userFields"));
    assertThat(userFields).hasSize(3);
    Map<String, Object> firstField = userFields.get(0);
    assertThat(firstField.get("kind")).isEqualTo("expression");
    assertThat(firstField.get("dataPath")).isEqualTo("ПользовательскиеПоля.Поле1");
    assertThat(firstField.get("title")).isEqualTo("Вышестоящее подразделение");
    assertThat((String) firstField.get("detailExpression")).contains("ВычислитьВыражение");
    assertThat((String) firstField.get("totalExpression")).contains("Иерархия");
  }

  @Test
  void readJsonChannelReturnsCyrillicJson() throws Exception {
    Path dcs = Ssl31SubmodulePaths.projectRoot().resolve(DYNAMICS);

    // с вариантом: валидный JSON с кириллицей в UTF-8
    ChannelResult result = readJson(Map.of(
      "op", "cf-dcs-settings-info", "objectXml", dcs.toString(), "schemaVersion", "V2_20",
      "variant", "Основной"));
    assertThat(result.exit()).isZero();
    assertThat(result.out()).contains("Демо: Динамика изменений файлов");
    Map<String, Object> parsed = parseJson(result.out());
    Map<String, Object> variant = list(parsed.get("variants")).get(0);
    assertThat(variant.get("name")).isEqualTo("Основной");
    assertThat(map(variant.get("settings")).get("structure")).isNotNull();

    // без варианта: только имена с представлениями
    ChannelResult names = readJson(Map.of(
      "op", "cf-dcs-settings-info", "objectXml", dcs.toString(), "schemaVersion", "V2_20"));
    assertThat(names.exit()).isZero();
    assertThat(list(parseJson(names.out()).get("variants"))).hasSize(6);

    // схема проекта 1С:EDT — тот же формат файла: отказ EDT-гейта операции не касается
    Path edt = Path.of(System.getProperty("fixtures.ssl31edt.root"), "ssl31", "src",
      "Reports", "_ДемоДинамикаИзмененийФайлов", "Templates", "ОсновнаяСхемаКомпоновкиДанных", "Template.dcs");
    ChannelResult edtResult = readJson(Map.of(
      "op", "cf-dcs-settings-info", "objectXml", edt.toString(), "variant", "Основной"));
    assertThat(edtResult.exit()).isZero();
    assertThat(list(parseJson(edtResult.out()).get("variants"))).hasSize(1);

    // отказ операции доходит из канала кодом 2
    ChannelResult missing = readJson(Map.of(
      "op", "cf-dcs-settings-info", "objectXml", dcs.toString(), "schemaVersion", "V2_20",
      "variant", "НетТакогоВарианта"));
    assertThat(missing.exit()).isEqualTo(2);
  }

  // ------------------------------------------------------------------ помощники

  private static Map<String, Object> info(String relativePath, String variant) throws Exception {
    Path dcs = Ssl31SubmodulePaths.projectRoot().resolve(relativePath);
    return DcsSettingsRead.info(dcs, SchemaVersion.V2_20, variant);
  }

  @SuppressWarnings("unchecked")
  private static List<Map<String, Object>> variants(Map<String, Object> info) {
    return (List<Map<String, Object>>) info.get("variants");
  }

  /** Настройки единственного варианта ответа. */
  private static Map<String, Object> settings(String relativePath, String variant) throws Exception {
    List<Map<String, Object>> variants = variants(info(relativePath, variant));
    assertThat(variants).hasSize(1);
    return map(variants.get(0).get("settings"));
  }

  /** Элементы секции; сама секция обязана быть в ответе. */
  private static List<Map<String, Object>> items(Map<String, Object> owner, String section) {
    Object dto = owner.get(section);
    assertThat(dto).as(section).isNotNull();
    return list(map(dto).get("items"));
  }

  @SuppressWarnings("unchecked")
  private static List<Map<String, Object>> list(Object value) {
    assertThat(value).isInstanceOf(List.class);
    return (List<Map<String, Object>>) value;
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> map(Object value) {
    assertThat(value).isInstanceOf(Map.class);
    return (Map<String, Object>) value;
  }

  private static Map<String, Object> byKey(List<Map<String, Object>> rows, String key, String value) {
    return rows.stream()
      .filter(row -> value.equals(row.get(key)))
      .findFirst()
      .orElseThrow(() -> new AssertionError(key + "=" + value + " не найден: " + rows));
  }

  /** Все узлы структуры рекурсивно: structure, строки, колонки, точки и серии. */
  private static List<Map<String, Object>> flatNodes(Map<String, Object> owner) {
    List<Map<String, Object>> out = new ArrayList<>();
    for (String key : List.of("structure", "rows", "columns", "points", "series")) {
      Object nodes = owner.get(key);
      if (nodes == null) {
        continue;
      }
      for (Map<String, Object> node : list(nodes)) {
        out.add(node);
        out.addAll(flatNodes(node));
      }
    }
    return out;
  }

  /** Результат канала read-json: код выхода и stdout в UTF-8. */
  private record ChannelResult(int exit, String out) {
  }

  /** Запуск канала read-json с параметрами из UTF-8 JSON-файла. */
  private ChannelResult readJson(Map<String, ?> params) throws Exception {
    Path paramsFile = Files.createTempFile(tempDir, "params", ".json");
    Files.writeString(paramsFile, new Gson().toJson(params), StandardCharsets.UTF_8);
    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    PrintStream prev = System.out;
    int exit;
    try {
      System.setOut(new PrintStream(buffer, true, StandardCharsets.UTF_8));
      exit = new CommandLine(new DesignerXmlCli()).execute("read-json", "--params", paramsFile.toString());
    } finally {
      System.setOut(prev);
    }
    return new ChannelResult(exit, buffer.toString(StandardCharsets.UTF_8));
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> parseJson(String json) {
    Map<String, Object> parsed = new Gson().fromJson(json, Map.class);
    assertThat(parsed).isNotNull();
    return parsed;
  }
}
