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
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Правки вариантов настроек схемы компоновки данных ({@code settingsVariant} корня):
 * варианты и их свойства, узлы структуры, списки секций (выбранные поля, отбор, сортировка,
 * поля группировки), параметры данных и вывода, условное оформление.
 *
 * <p>Вариант адресуется по имени среди прямых детей корня: варианты вложенных схем отсюда
 * не видны. Узел структуры адресуется путём шагов {@code {list, index}} от корня настроек
 * варианта; внутрь вложенного объекта ({@code StructureItemNestedObject}) путь не заходит.
 * Как и в {@link DcsEdit}, меняется исходный текст по смещениям {@link XmlLines} в порядке
 * xs:sequence формата; перед записью схема перечитывается моделью, битый файл не пишется.
 *
 * <p>Set-операции списков пишут только изменённое: элементы, глубоко равные прочитанным
 * (по DTO чтения, включая passthrough-поля и {@code userSettingID}), сохраняют исходные байты
 * и встают в порядке запроса, снятые вырезаются, хвост секции ({@code viewMode},
 * {@code userSettingID}, {@code userSettingPresentation}) остаётся на месте.
 */
public final class DcsSettingsEdit {

  /** Путь к корню схемы для {@link XmlLines#children}. */
  private static final List<String> ROOT = List.of("DataCompositionSchema");

  /** Свойства нового варианта, которые принимает {@link #addSettingsVariant}. */
  private static final List<String> ADD_VARIANT_PAYLOAD = List.of("presentation");

  /** Свойства варианта, которые правит {@link #setSettingsVariantProps}. */
  private static final List<String> VARIANT_PROPS = List.of("newName", "presentation");

  /** Свойства payload, которые принимает {@link #addStructureItem}. */
  private static final List<String> ADD_ITEM_PAYLOAD = List.of("parentPath", "list", "index", "kind", "groupFields");

  /** Свойства payload, которые принимает {@link #removeStructureItem}. */
  private static final List<String> REMOVE_ITEM_PAYLOAD = List.of("path");

  /** Свойства payload, которые принимает {@link #moveStructureItem}. */
  private static final List<String> MOVE_ITEM_PAYLOAD = List.of("path", "newIndex");

  /** Виды нового узла структуры при {@code list=structure}. */
  private static final List<String> ITEM_KINDS = List.of("group", "table", "chart");

  /** Свойства поля группировки ({@code GroupField}) в {@code groupFields}. */
  private static final List<String> GROUP_FIELD_PROPS =
    List.of("field", "groupType", "periodAdditionType", "periodAdditionBegin", "periodAdditionEnd");

  /** Виды группировки поля ({@code dcscor:DataCompositionGroupType}). */
  private static final List<String> GROUP_TYPES = List.of("Items", "Hierarchy", "HierarchyOnly");

  /** Дополнения периода ({@code dcscor:DataCompositionPeriodAdditionType}). */
  private static final List<String> PERIOD_ADDITION_TYPES = List.of(
    "None", "Year", "HalfYear", "Quarter", "Month", "TenDays", "Week", "Day", "Hour", "Minute",
    "Second", "YearSinceBeginOfPeriod", "HalfYearSinceBeginOfPeriod", "QuarterSinceBeginOfPeriod",
    "MonthSinceBeginOfPeriod", "WeekSinceBeginOfPeriod", "DaySinceBeginOfPeriod",
    "HourSinceBeginOfPeriod", "MinuteSinceBeginOfPeriod", "YearSinceBeginOfPeriod445",
    "HalfYearSinceBeginOfPeriod445", "QuarterSinceBeginOfPeriod445", "MonthSinceBeginOfPeriod445");

  /** Граница дополнения периода, которую платформа пишет у новой группировки. */
  private static final String PERIOD_BOUND_DEFAULT = "0001-01-01T00:00:00";

  /** Дата, как её пишет платформа ({@code 0001-01-01T00:00:00}): лексика значений xs:dateTime. */
  private static final Pattern DATE_TIME_TEXT =
    Pattern.compile("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}");

  /** Дети корня настроек по xs:sequence формата. */
  static final List<String> SETTINGS_SEQUENCE = List.of(
    "userFields", "selection", "filter", "dataParameters", "order", "conditionalAppearance",
    "outputParameters", "item", "additionalProperties", "itemsViewMode", "itemsUserSettingID",
    "itemsUserSettingPresentation");

  /** Дети группировки (и группы строки/колонки/точки/серии) по xs:sequence формата. */
  static final List<String> GROUP_SEQUENCE = List.of(
    "use", "name", "groupItems", "filter", "order", "selection", "conditionalAppearance",
    "outputParameters", "item", "id", "viewMode", "userSettingID", "userSettingPresentation",
    "itemsViewMode", "itemsUserSettingID", "itemsUserSettingPresentation", "groupState");

  /** Дети таблицы по xs:sequence формата: column перед row. */
  static final List<String> TABLE_SEQUENCE = List.of(
    "use", "name", "column", "row", "selection", "conditionalAppearance", "outputParameters",
    "id", "viewMode", "userSettingID", "userSettingPresentation", "columnsViewMode",
    "columnsUserSettingID", "columnsUserSettingPresentation", "rowsViewMode",
    "rowsUserSettingID", "rowsUserSettingPresentation");

  /** Дети диаграммы по xs:sequence формата: point перед series. */
  static final List<String> CHART_SEQUENCE = List.of(
    "use", "name", "point", "series", "selection", "conditionalAppearance", "outputParameters",
    "id", "viewMode", "userSettingID", "userSettingPresentation", "pointsViewMode",
    "pointsUserSettingID", "pointsUserSettingPresentation", "seriesViewMode",
    "seriesUserSettingID", "seriesUserSettingPresentation");

  /** Свойства payload set-операций списков: путь узла и элементы секции целиком. */
  private static final List<String> SET_ITEMS_PAYLOAD = List.of("path", "items");

  /** Свойства поля выбора ({@code SelItem}) в items set-операции выбранных полей. */
  private static final List<String> SELECTION_ITEM_PROPS =
    List.of("kind", "use", "field", "title", "titleKind", "placement", "items", "viewMode");

  /** Свойства элемента сортировки ({@code OrderItem}) в items set-операции сортировки. */
  private static final List<String> ORDER_ITEM_PROPS =
    List.of("kind", "use", "field", "orderType", "autoOrder", "distinctNullValues", "viewMode");

  /** Свойства элемента отбора ({@code FilterItem}) в items set-операции отбора. */
  private static final List<String> FILTER_ITEM_PROPS = List.of(
    "kind", "use", "left", "comparisonType", "right", "presentation", "application",
    "groupType", "items", "viewMode", "userSettingID", "userSettingPresentation");

  /** Свойства сравнения отбора: групповые {@code groupType} и {@code items} - отказ. */
  private static final List<String> COMPARISON_PROPS = List.of(
    "kind", "use", "left", "comparisonType", "right", "presentation", "application",
    "viewMode", "userSettingID", "userSettingPresentation");

  /** Свойства группы отбора: {@code left}, {@code comparisonType} и {@code right} - отказ. */
  private static final List<String> FILTER_GROUP_PROPS = List.of(
    "kind", "use", "groupType", "items", "presentation", "application",
    "viewMode", "userSettingID", "userSettingPresentation");

  /** Виды сравнения отбора ({@code dcsset:DataCompositionComparisonType}). */
  private static final List<String> COMPARISON_TYPES = List.of(
    "Equal", "NotEqual", "Less", "LessOrEqual", "Greater", "GreaterOrEqual", "Contains",
    "InList", "InListByHierarchy", "InHierarchy", "NotContains", "NotInList",
    "NotInListByHierarchy", "NotInHierarchy", "Filled", "NotFilled", "BeginsWith",
    "NotBeginsWith", "Like", "NotLike");

  /** Виды группы отбора ({@code dcsset:DataCompositionFilterItemsGroupType}). */
  private static final List<String> FILTER_GROUP_TYPES = List.of("AndGroup", "OrGroup", "NotGroup");

  /** Применение отбора ({@code dcscor:DataCompositionFilterApplicationType}). */
  private static final List<String> FILTER_APPLICATIONS = List.of("Items", "Hierarchy", "HierarchyOnly");

  /** Свойства типизированного значения ({@code Value}) в payload мутаций. */
  private static final List<String> VALUE_PROPS = List.of(
    "type", "text", "nil", "decoded", "period", "beginningDate", "list", "attrs", "opaque");

  /** Ключи других типов: {@code v8:LocalStringType} с ними и без {@code decoded} - отказ. */
  private static final List<String> LOCAL_STRING_FOREIGN_KEYS =
    List.of("text", "period", "beginningDate", "list");

  /** Свойства payload {@link #setParameterValue}: адрес, имя параметра и правка. */
  private static final List<String> SET_PARAMETER_PAYLOAD =
    List.of("section", "path", "parameter", "value", "use", "remove");

  /** Свойства payload {@link #addAppearanceItem} и {@link #setAppearanceItem}. */
  private static final List<String> APPEARANCE_ITEM_PAYLOAD = List.of("path", "index", "item");

  /** Свойства payload {@link #removeAppearanceItem}. */
  private static final List<String> REMOVE_APPEARANCE_PAYLOAD = List.of("path", "index");

  /** Свойства элемента условного оформления ({@code CAItem}). */
  private static final List<String> APPEARANCE_ITEM_PROPS = List.of(
    "use", "fields", "filter", "appearance", "presentation", "useIn",
    "viewMode", "userSettingID", "userSettingPresentation");

  /** Свойства поля оформления в {@code fields} элемента условного оформления. */
  private static final List<String> APPEARANCE_FIELD_PROPS = List.of("use", "field");

  /** Свойства значения параметра ({@code ParamValue}) в {@code appearance} элемента оформления. */
  private static final List<String> PARAMETER_ITEM_PROPS = List.of(
    "use", "parameter", "value", "items", "viewMode", "userSettingID", "userSettingPresentation");

  /** Области использования элемента условного оформления по xs:sequence: ключ DTO → элемент файла. */
  static final List<Map.Entry<String, String>> USE_IN_AREAS = List.of(
    Map.entry("group", "useInGroup"),
    Map.entry("hierarchicalGroup", "useInHierarchicalGroup"),
    Map.entry("overall", "useInOverall"),
    Map.entry("fieldsHeader", "useInFieldsHeader"),
    Map.entry("header", "useInHeader"),
    Map.entry("parameters", "useInParameters"),
    Map.entry("filter", "useInFilter"),
    Map.entry("resourceFieldsHeader", "useInResourceFieldsHeader"),
    Map.entry("overallHeader", "useInOverallHeader"),
    Map.entry("overallResourceFieldsHeader", "useInOverallResourceFieldsHeader"));

  /** Ключи DTO областей использования в {@code useIn}. */
  private static final List<String> USE_IN_KEYS =
    USE_IN_AREAS.stream().map(Map.Entry::getKey).toList();

  /** Значения области использования ({@code dcsset:DataCompositionConditionalAppearanceUse}). */
  private static final List<String> USE_IN_MODES = List.of("Use", "DontUse");

  /** Дети секции-коллекции по xs:sequence формата: {@code item*} и хвост самой секции. */
  static final List<String> SECTION_SEQUENCE =
    List.of("item", "viewMode", "userSettingID", "userSettingPresentation");

  /**
   * Маркер перенесённых байтов в компактном XML элемента оформления: {@link XmlGranularPatch#formatInsertion}
   * выносит его в отдельную строку, которую заменяют исходные строки неизменённого элемента.
   */
  private static final String CARRIED_MARKER = "x-carried-";

  /** Лексема перечисления формата: латинские буквы и цифры. */
  private static final Pattern LEXEME_TEXT = Pattern.compile("[A-Za-z][A-Za-z0-9]*");

  /**
   * Варианты стандартного периода ({@code v8:StandardPeriodVariant});
   * перечисление data-core.xsd стабильно в версиях формата 2.10-2.21.
   */
  private static final List<String> STANDARD_PERIOD_VARIANTS = List.of(
    "Custom", "Today", "ThisWeek", "ThisTenDays", "ThisMonth", "ThisQuarter", "ThisHalfYear",
    "ThisYear", "FromBeginningOfThisWeek", "FromBeginningOfThisTenDays",
    "FromBeginningOfThisMonth", "FromBeginningOfThisQuarter", "FromBeginningOfThisHalfYear",
    "FromBeginningOfThisYear", "Yesterday", "LastWeek", "LastTenDays", "LastMonth", "LastQuarter",
    "LastHalfYear", "LastYear", "LastWeekTillSameWeekDay", "LastTenDaysTillSameDayNumber",
    "LastMonthTillSameDate", "LastQuarterTillSameDate", "LastHalfYearTillSameDate",
    "LastYearTillSameDate", "Tomorrow", "NextWeek", "NextTenDays", "NextMonth", "NextQuarter",
    "NextHalfYear", "NextYear", "NextWeekTillSameWeekDay", "NextTenDaysTillSameDayNumber",
    "NextMonthTillSameDate", "NextQuarterTillSameDate", "NextHalfYearTillSameDate",
    "NextYearTillSameDate", "TillEndOfThisWeek", "TillEndOfThisTenDays", "TillEndOfThisMonth",
    "TillEndOfThisQuarter", "TillEndOfThisHalfYear", "TillEndOfThisYear", "Last7Days",
    "Next7Days", "Month");

  /**
   * Варианты стандартной даты начала ({@code v8:StandardBeginningDateVariant});
   * перечисление data-core.xsd стабильно в версиях формата 2.10-2.21.
   */
  private static final List<String> STANDARD_BEGINNING_DATE_VARIANTS = List.of(
    "Custom", "BeginningOfThisDay", "BeginningOfThisWeek", "BeginningOfThisTenDays",
    "BeginningOfThisMonth", "BeginningOfThisQuarter", "BeginningOfThisHalfYear",
    "BeginningOfThisYear", "BeginningOfLastDay", "BeginningOfLastWeek", "BeginningOfLastTenDays",
    "BeginningOfLastMonth", "BeginningOfLastQuarter", "BeginningOfLastHalfYear",
    "BeginningOfLastYear", "BeginningOfNextDay", "BeginningOfNextWeek", "BeginningOfNextTenDays",
    "BeginningOfNextMonth", "BeginningOfNextQuarter", "BeginningOfNextHalfYear",
    "BeginningOfNextYear");

  /**
   * Лексемы типа диаграммы ({@code v8ui:ChartType}) из data-ui.xsd, одинаковые в форматах 2.10-2.21.
   * Список закрыт: перечитка JAXB незнакомую лексему пропускает, а XSD-проверка отвергает.
   */
  private static final List<String> CHART_TYPES = List.of(
    "Line", "Step", "StackedLine", "Area", "StackedArea", "NormalizedArea",
    "Column", "StackedColumn", "NormalizedColumn", "Column3D", "StackedColumn3D", "NormalizedColumn3D",
    "Bar", "StackedBar", "NormalizedBar", "Bar3D", "StackedBar3D", "NormalizedBar3D",
    "Pie", "Pie3D", "Stock", "OpenHighLowClose", "BarGraph", "CeilGraph", "TapeGraph", "PyramidGraph",
    "Waterfall", "WireframeSurface", "Honeycomb", "Surface", "ConvexSurface", "ConcaveSurface",
    "ShadedSurface", "RadarLine", "RadarArea", "RadarStackedLine", "RadarStackedArea",
    "RadarNormalizedArea", "Gauge", "Funnel", "Funnel3D", "NormalizedFunnel", "NormalizedFunnel3D",
    "Scatter", "Bubble", "Donut", "Donut3D");

  /**
   * Ранги параметров вывода в порядке, в котором их пишет платформа на всех уровнях настроек: новый
   * параметр встаёт на своё место, а не в конец секции. Имена одной группы на одном уровне вместе
   * не хранятся (запреты уровней ниже), поэтому ранг у них общий.
   */
  private static final Map<String, Integer> OUTPUT_PARAMETER_RANKS = ranks(List.of(
    List.of("МакетОформления"),
    List.of("КоличествоЗаписей"),
    List.of("ПроцентЗаписей"),
    List.of("ТипМакета"),
    List.of("РасположениеИтогов"),
    List.of("РасположениеПолейГруппировки"),
    List.of("РасположениеГруппировки"),
    List.of("РасположениеРеквизитов"),
    List.of("РасположениеРесурсов"),
    List.of("ГоризонтальноеРасположениеОбщихИтогов", "РасположениеОбщихИтогов"),
    List.of("ВертикальноеРасположениеОбщихИтогов"),
    List.of("ТипЗаголовкаПолей"),
    List.of("ТипДиаграммы"),
    List.of("ВыводитьЗаголовок"),
    List.of("Заголовок"),
    List.of("ВыводитьПараметрыДанных"),
    List.of("ВыводитьОтбор"),
    List.of("АвтоПозицияРесурсов"),
    List.of("ФиксацияСлева"),
    List.of("ВариантИспользованияГруппировки", "ФиксацияСверху")));

  /**
   * Параметры вывода группировки, которых платформа не хранит в корне настроек варианта: загрузка
   * принимает их там без ошибки, но молча отбрасывает, даже со значениями не по умолчанию.
   */
  private static final Set<String> ROOT_FORBIDDEN_OUTPUT_PARAMETERS = Set.of(
    "КоличествоЗаписей", "ПроцентЗаписей", "ТипМакета", "РасположениеОбщихИтогов",
    "ВариантИспользованияГруппировки");

  /**
   * Параметры вывода, которых платформа не хранит у группировки структуры
   * ({@code dcsset:StructureItemGroup}) на любой глубине: загрузка молча их отбрасывает. XSD имён
   * не ограничивает: секции всех видов узлов - пустые расширения {@code ParameterValues}.
   */
  private static final Set<String> GROUP_FORBIDDEN_OUTPUT_PARAMETERS = Set.of(
    "ФиксацияСверху", "ФиксацияСлева", "ВыводитьПараметрыДанных", "РасположениеОбщихИтогов",
    "ГоризонтальноеРасположениеОбщихИтогов");

  /**
   * Параметры вывода, которых платформа не хранит у таблицы ({@code StructureItemTable}): загрузка молча
   * их отбрасывает. Имена вне таблицы рангов не ограничиваются.
   */
  private static final Set<String> TABLE_FORBIDDEN_OUTPUT_PARAMETERS = Set.of(
    "ФиксацияСверху", "ФиксацияСлева", "ВариантИспользованияГруппировки", "ВыводитьОтбор",
    "ВыводитьПараметрыДанных", "ТипДиаграммы", "РасположениеОбщихИтогов", "ТипМакета", "ПроцентЗаписей",
    "КоличествоЗаписей");

  /**
   * Параметры вывода, которых платформа не хранит у группы таблицы: группы на пути через шаг
   * {@code rows} или {@code columns}. {@code РасположениеОбщихИтогов} хранится только здесь.
   */
  private static final Set<String> TABLE_GROUP_FORBIDDEN_OUTPUT_PARAMETERS = Set.of(
    "ФиксацияСверху", "ФиксацияСлева", "ВыводитьПараметрыДанных", "ТипДиаграммы",
    "ВертикальноеРасположениеОбщихИтогов", "ГоризонтальноеРасположениеОбщихИтогов", "ТипМакета");

  /**
   * Параметры вывода, которых платформа не хранит у диаграммы ({@code StructureItemChart}): из имён таблицы
   * рангов там хранятся только {@code ТипДиаграммы}, {@code ВыводитьЗаголовок} и {@code Заголовок}.
   */
  private static final Set<String> CHART_FORBIDDEN_OUTPUT_PARAMETERS = Set.of(
    "МакетОформления", "КоличествоЗаписей", "ПроцентЗаписей", "ТипМакета", "РасположениеИтогов",
    "РасположениеПолейГруппировки", "РасположениеГруппировки", "РасположениеРеквизитов", "РасположениеРесурсов",
    "ГоризонтальноеРасположениеОбщихИтогов", "РасположениеОбщихИтогов", "ВертикальноеРасположениеОбщихИтогов",
    "ТипЗаголовкаПолей", "ВыводитьПараметрыДанных", "ВыводитьОтбор", "АвтоПозицияРесурсов", "ФиксацияСлева",
    "ВариантИспользованияГруппировки", "ФиксацияСверху");

  /**
   * Параметры вывода, которых платформа не хранит у группы диаграммы: группы на пути через шаг
   * {@code points} или {@code series}. Из имён таблицы рангов там хранятся только
   * {@code КоличествоЗаписей} и {@code ПроцентЗаписей}.
   */
  private static final Set<String> CHART_GROUP_FORBIDDEN_OUTPUT_PARAMETERS = Set.of(
    "МакетОформления", "ТипМакета", "РасположениеИтогов", "РасположениеПолейГруппировки",
    "РасположениеГруппировки", "РасположениеРеквизитов", "РасположениеРесурсов",
    "ГоризонтальноеРасположениеОбщихИтогов", "РасположениеОбщихИтогов", "ВертикальноеРасположениеОбщихИтогов",
    "ТипЗаголовкаПолей", "ТипДиаграммы", "ВыводитьЗаголовок", "Заголовок", "ВыводитьПараметрыДанных",
    "ВыводитьОтбор", "АвтоПозицияРесурсов", "ФиксацияСлева", "ВариантИспользованияГруппировки", "ФиксацияСверху");

  /** Параметр вывода, которого платформа не хранит ни в корне настроек, ни у группировки структуры. */
  private static final String TOTAL_PLACEMENT = "РасположениеОбщихИтогов";

  /** Размещения папки выбора ({@code dcsset:DataCompositionFieldPlacement}). */
  private static final List<String> PLACEMENTS =
    List.of("Auto", "Horizontally", "Vertically", "SpecialColumn", "Together");

  /** Направления сортировки ({@code dcscor:DataCompositionSortDirection}). */
  private static final List<String> ORDER_TYPES = List.of("Asc", "Desc");

  /** Режимы просмотра элемента ({@code dcsset:DataCompositionSettingsItemViewMode}). */
  private static final List<String> VIEW_MODES = List.of("Normal", "QuickAccess", "Inaccessible", "Auto");

  /** Объявление пространства имён в корневом теге: ими оборачиваются фрагменты для разбора DTO. */
  private static final Pattern XMLNS_DECLARATION =
    Pattern.compile("xmlns(:[A-Za-z0-9_.\\-]+)?=\"[^\"]*\"");

  private static final Pattern XSI_TYPE = Pattern.compile("xsi:type=[\"']([^\"']+)[\"']");

  private DcsSettingsEdit() {
  }

  // ------------------------------------------------------------------ варианты

  /**
   * Добавляет вариант настроек, как его пишет конфигуратор: имя, представление
   * ({@code v8:LocalStringType}) и самозакрытый {@code dcsset:settings} с объявлениями
   * пространств имён формата ({@link DcsSettingsNamespaces#declarations}).
   *
   * <p>Вариант встаёт после последнего {@code settingsVariant}, без них - в конец корня
   * (по xs:sequence это последний элемент корня).
   *
   * @param name имя нового варианта; занятое имя - отказ
   * @param payload необязательное {@code presentation}; без него представлением становится имя
   * @throws IllegalArgumentException если имя занято или свойство неизвестно
   */
  public static void addSettingsVariant(
    Path templateXml, SchemaVersion version, String name, Map<String, Object> payload)
    throws IOException, JAXBException {
    String variantName = required(name, "name");
    if (payload != null) {
      for (String key : payload.keySet()) {
        if (!ADD_VARIANT_PAYLOAD.contains(key)) {
          throw new IllegalArgumentException("Неизвестное свойство варианта настроек: " + key);
        }
      }
    }
    String presentation = presentationText(payload == null ? null : payload.get("presentation"), variantName);
    ConfigurationLanguage.with(templateXml, () -> {
      String text = Files.readString(templateXml, StandardCharsets.UTF_8);
      List<XmlLines.Node> roots = XmlLines.children(text, ROOT);
      XmlLines.Node namesake = variantIgnoringCase(roots, variantName, null);
      if (namesake != null) {
        throw new IllegalArgumentException(variantName.equals(namesake.leaves().get("name"))
          ? "Вариант настроек уже есть: " + variantName
          : "Вариант настроек «" + variantName + "» уже есть в схеме: «" + namesake.leaves().get("name") + "».");
      }
      XmlLines.Node anchor = null;
      for (XmlLines.Node node : roots) {
        if ("settingsVariant".equals(node.name())) {
          anchor = node;
        }
      }
      String eol = XmlLines.newline(text);
      String block = "\t<settingsVariant>" + eol
        + "\t\t<dcsset:name>" + DcsRead.escape(variantName) + "</dcsset:name>" + eol
        + "\t\t<dcsset:presentation xsi:type=\"v8:LocalStringType\">" + eol
        + "\t\t\t<v8:item>" + eol
        + "\t\t\t\t<v8:lang>" + ConfigurationLanguage.current() + "</v8:lang>" + eol
        + "\t\t\t\t<v8:content>" + DcsRead.escape(presentation) + "</v8:content>" + eol
        + "\t\t\t</v8:item>" + eol
        + "\t\t</dcsset:presentation>" + eol
        + "\t\t<dcsset:settings" + DcsSettingsNamespaces.declarations(templateXml, version) + "/>" + eol
        + "\t</settingsVariant>" + eol;
      block = XmlGranularPatch.lineBreaks(block, eol);
      String updated;
      if (anchor != null) {
        int lineEnd = text.indexOf('\n', anchor.end());
        updated = lineEnd < 0
          ? text.substring(0, anchor.end()) + eol + block + text.substring(anchor.end())
          : text.substring(0, lineEnd + 1) + block + text.substring(lineEnd + 1);
      } else {
        int close = text.lastIndexOf("</DataCompositionSchema>");
        if (close < 0) {
          throw new IllegalArgumentException("Файл не является схемой компоновки данных.");
        }
        updated = text.substring(0, close) + block + text.substring(close);
      }
      DcsRead.verify(updated, version);
      Files.writeString(templateXml, updated, StandardCharsets.UTF_8);
      return null;
    });
  }

  /**
   * Удаляет вариант настроек целыми строками.
   *
   * @param name имя варианта
   * @throws IllegalArgumentException если варианта нет или имя встречается дважды
   */
  public static void removeSettingsVariant(Path templateXml, SchemaVersion version, String name)
    throws IOException, JAXBException {
    String variantName = required(name, "name");
    String text = Files.readString(templateXml, StandardCharsets.UTF_8);
    XmlLines.Node target = variant(text, variantName);
    String updated = XmlLines.removeAll(
      text, List.of(XmlLines.wholeLines(text, target.start(), target.end(), "settingsVariant")));
    DcsRead.verify(updated, version);
    Files.writeString(templateXml, updated, StandardCharsets.UTF_8);
  }

  /**
   * Меняет имя и представление варианта настроек.
   *
   * <p>Ключи, которых нет в {@code props}, не трогаются. {@code newName} требует непустое
   * свободное имя. {@code presentation} пишется как {@code v8:LocalStringType}: элемент
   * заменяется целиком или встаёт на место по xs:sequence; {@code null} или пустое значение
   * снимает элемент. Представление, равное прочитанному с точностью до переводов строк,
   * не переписывается, в том числе в форме {@code xs:string}. Многострочный текст пишется
   * с переводом строки файла.
   *
   * @param name имя варианта
   * @param props значения по именам свойств; неизвестное имя - отказ
   * @throws IllegalArgumentException если варианта нет, новое имя занято или значение не по формату
   */
  public static void setSettingsVariantProps(
    Path templateXml, SchemaVersion version, String name, Map<String, Object> props)
    throws IOException, JAXBException {
    String variantName = required(name, "name");
    if (props == null || props.isEmpty()) {
      return;
    }
    for (String key : props.keySet()) {
      if (!VARIANT_PROPS.contains(key)) {
        throw new IllegalArgumentException("Неизвестное свойство варианта настроек: " + key);
      }
    }
    ConfigurationLanguage.with(templateXml, () -> {
      String text = Files.readString(templateXml, StandardCharsets.UTF_8);
      List<XmlLines.Node> roots = XmlLines.children(text, ROOT);
      XmlLines.Node target = variantAmong(roots, variantName);
      List<XmlLines.Node> children = XmlLines.childrenAt(text, target.start());
      List<XmlGranularPatch.Replacement> replacements = new ArrayList<>();
      if (props.containsKey("newName")) {
        Object raw = props.get("newName");
        if (!(raw instanceof String fresh) || fresh.isBlank()) {
          throw new IllegalArgumentException("Значение newName должно быть непустой строкой.");
        }
        // Смена регистра своего имени допустима: совпадение ищется среди других вариантов
        if (!fresh.equals(variantName) && variantIgnoringCase(roots, fresh, target) != null) {
          throw new IllegalArgumentException("Имя варианта настроек уже занято: " + fresh);
        }
        replacements.add(childReplacement(text, children, "name", fresh));
      }
      if (props.containsKey("presentation")) {
        presentationReplacement(text, children, props.get("presentation")).ifPresent(replacements::add);
      }
      if (replacements.isEmpty()) {
        return null;
      }
      String updated = XmlGranularPatch.withoutOverlaps(replacements)
        .map(safe -> XmlGranularPatch.apply(text, safe))
        .orElseThrow(() -> new IllegalStateException("правки варианта настроек пересеклись"));
      if (updated.equals(text)) {
        return null;
      }
      DcsRead.verify(updated, version);
      Files.writeString(templateXml, updated, StandardCharsets.UTF_8);
      return null;
    });
  }

  // ------------------------------------------------------------------ адресация

  /** Шаг пути к узлу структуры: список и номер элемента в нём с нуля. */
  record Step(String list, int index) {
  }

  /** Вид узла структуры: он определяет допустимые списки шагов и состав секций узла. */
  enum NodeKind {
    /** Корень настроек варианта ({@code dcsset:settings}). */
    SETTINGS,
    /** Группировка, в том числе строка, колонка, точка и серия с их вложенными группами. */
    GROUP,
    /** Таблица ({@code StructureItemTable}). */
    TABLE,
    /** Диаграмма ({@code StructureItemChart}). */
    CHART,
    /** Вложенный объект ({@code StructureItemNestedObject}): внутрь путь не заходит. */
    NESTED_OBJECT
  }

  /** Узел структуры по пути: элемент текста и его вид. */
  record ResolvedNode(XmlLines.Node element, NodeKind kind) {
  }

  /**
   * Вариант настроек среди прямых детей корня.
   *
   * @param name имя варианта
   * @throws IllegalArgumentException если варианта нет или имя встречается дважды
   */
  static XmlLines.Node variant(String text, String name) {
    return variantAmong(XmlLines.children(text, ROOT), name);
  }

  /**
   * Узел структуры варианта по пути шагов {@code {list, index}} от корня настроек.
   *
   * <p>Списки: {@code structure} - прямые {@code item} корня настроек и группировок (у групп
   * строк, колонок, точек и серий без {@code xsi:type}); {@code rows}/{@code columns} -
   * {@code row}/{@code column} таблицы; {@code points}/{@code series} - {@code point}/{@code series}
   * диаграммы.
   *
   * @param variantName имя варианта среди прямых детей корня
   * @param path шаги пути; пустой путь - сам корень настроек варианта
   * @return узел и его вид
   * @throws IllegalArgumentException если варианта нет, список не применим к узлу,
   *     номера нет в списке или путь заходит внутрь вложенного объекта
   */
  static ResolvedNode resolveNode(String text, String variantName, List<Step> path) {
    XmlLines.Node variant = variant(text, variantName);
    XmlLines.Node settings = child(XmlLines.childrenAt(text, variant.start()), "settings");
    if (settings == null) {
      throw new IllegalArgumentException("У варианта настроек " + variantName + " нет элемента settings.");
    }
    XmlLines.Node current = settings;
    NodeKind kind = NodeKind.SETTINGS;
    for (Step step : path) {
      String list = step.list();
      String childName = listChildName(list);
      requireListAllowed(kind, list);
      List<XmlLines.Node> listed = named(XmlLines.childrenAt(text, current.start()), childName);
      current = listed.get(requireListIndex(list, step.index(), listed.size()));
      kind = nodeKind(text, current);
    }
    return new ResolvedNode(current, kind);
  }

  /** Имя элемента коллекции по имени списка пути; неизвестный список - отказ. */
  private static String listChildName(String list) {
    return switch (list) {
      case "structure" -> "item";
      case "rows" -> "row";
      case "columns" -> "column";
      case "points" -> "point";
      case "series" -> "series";
      default -> throw new IllegalArgumentException("Неизвестный список: " + list
        + " (допустимы structure, rows, columns, points, series).");
    };
  }

  /** Номер элемента коллекции: вне её границ - отказ. */
  private static int requireListIndex(String list, int index, int size) {
    if (index < 0 || index >= size) {
      throw new IllegalArgumentException(
        "В списке " + list + " нет элемента с номером " + index + ": элементов " + size + ".");
    }
    return index;
  }

  /** Допустимость списка у вида узла; внутрь вложенного объекта путь не заходит. */
  private static void requireListAllowed(NodeKind kind, String list) {
    boolean allowed = switch (list) {
      case "structure" -> kind == NodeKind.SETTINGS || kind == NodeKind.GROUP;
      case "rows", "columns" -> kind == NodeKind.TABLE;
      default -> kind == NodeKind.CHART;
    };
    if (allowed) {
      return;
    }
    if (kind == NodeKind.NESTED_OBJECT) {
      throw new IllegalArgumentException(
        "Путь не заходит внутрь вложенного отчёта (StructureItemNestedObject).");
    }
    throw new IllegalArgumentException("Список " + list + " не применим к узлу вида " + kindName(kind) + ".");
  }

  /**
   * Вид узла по имени и xsi:type. Строки, колонки, точки и серии устроены как группировка;
   * {@code item} без xsi:type - их вложенная группа.
   */
  private static NodeKind nodeKind(String text, XmlLines.Node node) {
    if (!"item".equals(node.name())) {
      return NodeKind.GROUP;
    }
    String tag = text.substring(node.start(), XmlLines.startTagEnd(text, node.start()) + 1);
    Matcher type = XSI_TYPE.matcher(tag);
    String value = type.find() ? type.group(1) : "";
    return switch (value.substring(value.indexOf(':') + 1)) {
      case "", "StructureItemGroup" -> NodeKind.GROUP;
      case "StructureItemTable" -> NodeKind.TABLE;
      case "StructureItemChart" -> NodeKind.CHART;
      case "StructureItemNestedObject" -> NodeKind.NESTED_OBJECT;
      default -> throw new IllegalArgumentException("Неизвестный вид узла структуры: " + value);
    };
  }

  /** Вид узла по-русски для текста отказа. */
  private static String kindName(NodeKind kind) {
    return switch (kind) {
      case SETTINGS -> "корень настроек";
      case GROUP -> "группировка";
      case TABLE -> "таблица";
      case CHART -> "диаграмма";
      case NESTED_OBJECT -> "вложенный отчёт";
    };
  }

  // ------------------------------------------------------------------ структура варианта

  /**
   * Добавляет узел структуры: группировку, таблицу или диаграмму в {@code structure}, группу
   * в {@code rows}/{@code columns} таблицы или {@code points}/{@code series} диаграммы.
   *
   * <p>{@code kind} задаётся только при {@code list=structure}: в прочих списках узел всегда
   * группа. Группировка пишется, как её пишет конфигуратор: полная пятёрка {@code GroupItemField}
   * на каждое поле из {@code groupFields} (без полей - детальные записи без {@code groupItems}),
   * {@code order} с {@code OrderItemAuto} и {@code selection} с {@code SelectedItemAuto};
   * таблица и диаграмма - пустые самозакрытые. Вложенная группа строки, колонки, точки или
   * серии пишется без {@code xsi:type}, как у платформы.
   *
   * <p>Узел встаёт в позицию {@code index} коллекции (по умолчанию в конец), коллекция - на место
   * по xs:sequence родителя. Самозакрытый родитель раскрывается.
   *
   * @param variant имя варианта среди прямых детей корня
   * @param payload {@code parentPath} - путь родителя (пустой - корень настроек),
   *     {@code list} - коллекция, {@code index} - позиция вставки, {@code kind} - вид узла
   *     при {@code list=structure}, {@code groupFields} - поля новой группировки
   * @throws IllegalArgumentException если адресация не разрешается, список или вид
   *     не применимы к родителю либо позиция вне границ коллекции
   */
  public static void addStructureItem(
    Path templateXml, SchemaVersion version, String variant, Map<String, Object> payload)
    throws IOException, JAXBException {
    String variantName = required(variant, "variant");
    knownKeys(payload, ADD_ITEM_PAYLOAD);
    List<Step> parentPath = steps(payload.get("parentPath"), "parentPath");
    Object rawList = payload.get("list");
    if (!(rawList instanceof String list) || list.isBlank()) {
      throw new IllegalArgumentException("обязательное поле не задано: list");
    }
    String childName = listChildName(list);
    String text = Files.readString(templateXml, StandardCharsets.UTF_8);
    ResolvedNode parent = resolveNode(text, variantName, parentPath);
    requireListAllowed(parent.kind(), list);
    boolean plainItem = untypedStructure(text, parent.element());
    String kind = itemKind(payload.get("kind"), list, plainItem);
    List<Map<String, Object>> groupFields = groupFields(payload.get("groupFields"), kind);
    String block = newItemXml(list, kind, plainItem, groupFields);
    List<XmlLines.Node> children = XmlLines.childrenAt(text, parent.element().start());
    List<XmlLines.Node> listed = named(children, childName);
    int index = insertionIndex(payload.get("index"), list, listed.size());
    String updated = XmlGranularPatch.apply(text, List.of(collectionInsertion(
      text, parent.element(), children, listed, sequenceOf(parent.kind()), childName, index, block)));
    DcsRead.verify(updated, version);
    Files.writeString(templateXml, updated, StandardCharsets.UTF_8);
  }

  /**
   * Удаляет узел структуры с потомками целыми строками. Опустевший родитель сворачивается
   * в самозакрытый тег ({@link #collapsedIfEmpty}).
   *
   * @param variant имя варианта среди прямых детей корня
   * @param payload {@code path} - путь узла; пустой путь (корень настроек) - отказ
   * @throws IllegalArgumentException если путь не разрешается или адресует корень настроек
   */
  public static void removeStructureItem(
    Path templateXml, SchemaVersion version, String variant, Map<String, Object> payload)
    throws IOException, JAXBException {
    String variantName = required(variant, "variant");
    knownKeys(payload, REMOVE_ITEM_PAYLOAD);
    List<Step> path = steps(payload.get("path"), "path");
    if (path.isEmpty()) {
      throw new IllegalArgumentException(
        "Пустой путь адресует корень настроек варианта: удалить можно только узел структуры.");
    }
    String text = Files.readString(templateXml, StandardCharsets.UTF_8);
    XmlLines.Node node = resolveNode(text, variantName, path).element();
    int parentStart = resolveNode(text, variantName, path.subList(0, path.size() - 1)).element().start();
    String updated = collapsedIfEmpty(XmlLines.removeAll(
      text, List.of(XmlLines.wholeLines(text, node.start(), node.end(), node.name()))), parentStart);
    DcsRead.verify(updated, version);
    Files.writeString(templateXml, updated, StandardCharsets.UTF_8);
  }

  /**
   * Переставляет узел структуры на новый номер в пределах его коллекции.
   *
   * <p>Строки узла переносятся без изменений. В другую коллекцию узел не переносится.
   *
   * @param variant имя варианта среди прямых детей корня
   * @param payload {@code path} - путь узла (пустой - отказ), {@code newIndex} - новый номер
   * @throws IllegalArgumentException если путь не разрешается или номер вне границ коллекции
   */
  public static void moveStructureItem(
    Path templateXml, SchemaVersion version, String variant, Map<String, Object> payload)
    throws IOException, JAXBException {
    String variantName = required(variant, "variant");
    knownKeys(payload, MOVE_ITEM_PAYLOAD);
    List<Step> path = steps(payload.get("path"), "path");
    if (path.isEmpty()) {
      throw new IllegalArgumentException(
        "Пустой путь адресует корень настроек варианта: переставить можно только узел структуры.");
    }
    int newIndex = index(payload.get("newIndex"), "newIndex");
    String text = Files.readString(templateXml, StandardCharsets.UTF_8);
    Step last = path.get(path.size() - 1);
    ResolvedNode parent = resolveNode(text, variantName, path.subList(0, path.size() - 1));
    String childName = listChildName(last.list());
    requireListAllowed(parent.kind(), last.list());
    List<XmlLines.Node> listed = named(XmlLines.childrenAt(text, parent.element().start()), childName);
    XmlLines.Node moved = listed.get(requireListIndex(last.list(), last.index(), listed.size()));
    if (newIndex < 0 || newIndex >= listed.size()) {
      throw new IllegalArgumentException("Новый номер " + newIndex + " вне границ списка "
        + last.list() + ": элементов " + listed.size() + ".");
    }
    if (newIndex == last.index()) {
      return;
    }
    int[] span = XmlLines.wholeLines(text, moved.start(), moved.end(), childName);
    XmlLines.Node at = listed.get(newIndex);
    int[] target = XmlLines.wholeLines(text, at.start(), at.end(), childName);
    String block = text.substring(span[0], span[1]);
    String updated = newIndex < last.index()
      ? text.substring(0, target[0]) + block + text.substring(target[0], span[0]) + text.substring(span[1])
      : text.substring(0, span[0]) + text.substring(span[1], target[1]) + block + text.substring(target[1]);
    DcsRead.verify(updated, version);
    Files.writeString(templateXml, updated, StandardCharsets.UTF_8);
  }

  // ------------------------------------------------------------------ set-операции списков

  /** Секция узла, которую set-операция заменяет целиком; у каждой свой писатель элементов. */
  private enum ListSection {
    /** Выбранные поля ({@code selection}): корень настроек, группировки, таблицы и диаграммы. */
    SELECTION("selection", "выбранных полей"),
    /** Отбор ({@code filter}): корень настроек и группировки. */
    FILTER("filter", "отбора"),
    /** Сортировка ({@code order}): корень настроек и группировки. */
    ORDER("order", "сортировки"),
    /** Поля группировки ({@code groupItems}): только группировки. */
    GROUP_FIELDS("groupItems", "полей группировки");

    /** Локальное имя элемента секции в файле. */
    private final String element;

    /** Название секции по-русски для текста отказа. */
    private final String title;

    ListSection(String element, String title) {
      this.element = element;
      this.title = title;
    }
  }

  /**
   * Заменяет выбранные поля узла целиком ({@code cf-dcs-set-selection}).
   *
   * <p>Новые и изменённые элементы пишутся по образцам платформы: заголовок - {@code lwsTitle}
   * языком конфигурации (при {@code titleKind: "string"} простая форма {@code title}), у новой
   * папки размещение по умолчанию {@code Auto}. Пустой {@code items} снимает секцию, если у неё
   * нет хвоста.
   *
   * @param variant имя варианта среди прямых детей корня
   * @param payload {@code path} - путь узла (пустой - корень настроек), {@code items} -
   *     выбранные поля целиком
   * @throws IllegalArgumentException если адресация не разрешается, у узла нет такой секции
   *     или элемент запроса не по формату
   */
  public static void setSelection(
    Path templateXml, SchemaVersion version, String variant, Map<String, Object> payload)
    throws IOException, JAXBException {
    spliceSection(templateXml, version, variant, payload, ListSection.SELECTION);
  }

  /**
   * Заменяет отбор узла целиком ({@code cf-dcs-set-filter}).
   *
   * <p>Сравнение: левое значение - поле ({@code dcscor:Field}), вид сравнения (по умолчанию
   * {@code Equal}), правые значения повтором {@code right}; у {@code Filled} и {@code NotFilled}
   * правых значений нет. Группа: вид ({@code AndGroup} по умолчанию) и вложенные элементы прямыми
   * детьми. Правые значения пишутся по белому списку типов: элемент с другим типом переносится
   * байтами, а его правка - отказ. {@code userSettingID} не генерируется.
   *
   * @param variant имя варианта среди прямых детей корня
   * @param payload {@code path} - путь узла (пустой - корень настроек), {@code items} -
   *     элементы отбора целиком
   * @throws IllegalArgumentException если адресация не разрешается, у узла нет отбора
   *     (таблица, диаграмма, вложенный отчёт) или элемент запроса не по формату
   */
  public static void setFilter(
    Path templateXml, SchemaVersion version, String variant, Map<String, Object> payload)
    throws IOException, JAXBException {
    spliceSection(templateXml, version, variant, payload, ListSection.FILTER);
  }

  /**
   * Заменяет сортировку узла целиком ({@code cf-dcs-set-order}).
   *
   * <p>Новый элемент - поле с направлением ({@code orderType}, по умолчанию {@code Asc}) либо
   * авто-элемент; {@code autoOrder} и {@code distinctNullValues} пишутся как даны.
   *
   * @param variant имя варианта среди прямых детей корня
   * @param payload {@code path} - путь узла, {@code items} - элементы сортировки целиком
   * @throws IllegalArgumentException если адресация не разрешается, у узла нет сортировки
   *     (таблица, диаграмма, вложенный отчёт) или элемент запроса не по формату
   */
  public static void setOrder(
    Path templateXml, SchemaVersion version, String variant, Map<String, Object> payload)
    throws IOException, JAXBException {
    spliceSection(templateXml, version, variant, payload, ListSection.ORDER);
  }

  /**
   * Заменяет поля группировки узла целиком ({@code cf-dcs-set-group-fields}).
   *
   * <p>Каждое поле пишется полной пятёркой {@code GroupItemField}, как пишет платформа. Граница
   * дополнения периода: дата {@code xs:dateTime}, {@code {nil: true}}, поле {@code dcscor:Field} или
   * лексема {@code dcscor:DataCompositionPeriodAdditionType}; поле с другим типом границы
   * переносится байтами, а его правка - отказ. Пустой {@code items} снимает {@code groupItems}:
   * группировка становится детальными записями.
   *
   * @param variant имя варианта среди прямых детей корня
   * @param payload {@code path} - путь узла-группировки, {@code items} - поля целиком
   * @throws IllegalArgumentException если адресация не разрешается, узел не группировка
   *     или поле запроса не по формату
   */
  public static void setGroupFields(
    Path templateXml, SchemaVersion version, String variant, Map<String, Object> payload)
    throws IOException, JAXBException {
    spliceSection(templateXml, version, variant, payload, ListSection.GROUP_FIELDS);
  }

  /** Set-операция списка: сплайс {@code item*} секции узла с проверкой перед записью. */
  private static void spliceSection(
    Path templateXml, SchemaVersion version, String variant, Map<String, Object> payload,
    ListSection section) throws IOException, JAXBException {
    String variantName = required(variant, "variant");
    knownKeys(payload, SET_ITEMS_PAYLOAD);
    List<Step> path = steps(payload.get("path"), "path");
    List<Map<String, Object>> items = itemMaps(payload.get("items"), "items");
    ConfigurationLanguage.with(templateXml, () -> {
      String text = Files.readString(templateXml, StandardCharsets.UTF_8);
      ResolvedNode node = resolveNode(text, variantName, path);
      requireSectionAllowed(node.kind(), section);
      String updated = spliced(text, node, section, items);
      if (!updated.equals(text)) {
        DcsRead.verify(updated, version);
        Files.writeString(templateXml, updated, StandardCharsets.UTF_8);
      }
      return null;
    });
  }

  /**
   * Сплайс {@code item*} секции. Пустой {@code items} снимает секцию, если у неё нет хвоста;
   * опустевший узел сворачивается ({@link #collapsedIfEmpty}), отсутствующая секция создаётся
   * по xs:sequence вида узла.
   */
  private static String spliced(
    String text, ResolvedNode node, ListSection section, List<Map<String, Object>> items) {
    List<XmlLines.Node> children = XmlLines.childrenAt(text, node.element().start());
    XmlLines.Node sectionEl = child(children, section.element);
    String eol = XmlLines.newline(text);
    if (sectionEl == null) {
      if (items.isEmpty()) {
        return text;
      }
      StringBuilder compact = new StringBuilder("<dcsset:").append(section.element).append('>');
      for (Map<String, Object> item : items) {
        compact.append(sectionItemXml(section, item));
      }
      compact.append("</dcsset:").append(section.element).append('>');
      return XmlGranularPatch.apply(text, List.of(collectionInsertion(text, node.element(), children,
        List.of(), sequenceOf(node.kind()), section.element, 0, compact.toString())));
    }
    List<XmlLines.Node> sectionChildren = XmlLines.childrenAt(text, sectionEl.start());
    List<XmlLines.Node> existing = named(sectionChildren, "item");
    boolean tail = sectionChildren.size() > existing.size();
    for (XmlLines.Node item : existing) {
      requireSpliceable(text, item, section);
    }
    if (items.isEmpty() && !tail) {
      return collapsedIfEmpty(XmlLines.removeAll(text,
        List.of(XmlLines.wholeLines(text, sectionEl.start(), sectionEl.end(), section.element))),
        node.element().start());
    }
    String xmlns = rootNamespaces(text);
    List<Object> read = new ArrayList<>(existing.size());
    List<int[]> spans = new ArrayList<>(existing.size());
    for (XmlLines.Node item : existing) {
      read.add(normalizedItem(DcsSettingsRead.sectionItemDto(
        text.substring(item.start(), item.end()), xmlns, section.element)));
      spans.add(XmlLines.wholeLines(text, item.start(), item.end(), "item"));
    }
    String itemIndent = existing.isEmpty()
      ? XmlLines.indentAt(text, sectionEl.start()) + "\t"
      : XmlLines.indentAt(text, existing.get(0).start());
    boolean[] moved = new boolean[existing.size()];
    StringBuilder blocks = new StringBuilder();
    for (Map<String, Object> item : items) {
      Object requested = normalizedItem(item);
      int kept = -1;
      for (int i = 0; i < existing.size(); i++) {
        if (!moved[i] && read.get(i).equals(requested)) {
          kept = i;
          break;
        }
      }
      if (kept >= 0) {
        moved[kept] = true;
        blocks.append(text, spans.get(kept)[0], spans.get(kept)[1]);
      } else {
        blocks.append(XmlGranularPatch.formatInsertion(text, itemIndent, sectionItemXml(section, item)))
          .append(eol);
      }
    }
    if (!existing.isEmpty()) {
      // item* идут подряд перед хвостом секции: заменяется только их область
      return text.substring(0, spans.get(0)[0]) + blocks + text.substring(spans.get(spans.size() - 1)[1]);
    }
    if (blocks.length() == 0) {
      return text;
    }
    if (tail) {
      // элементов не было, а хвост есть: item* встают перед ним
      int lineStart = text.lastIndexOf('\n', sectionChildren.get(0).start() - 1) + 1;
      return text.substring(0, lineStart) + blocks + text.substring(lineStart);
    }
    // секция без детей: раскрывается самозакрытый тег либо пустая пара
    int gt = XmlLines.startTagEnd(text, sectionEl.start());
    String indent = XmlLines.indentAt(text, sectionEl.start());
    String qualified = qualifiedName(text, sectionEl.start());
    if (text.charAt(gt - 1) == '/') {
      return text.substring(0, gt - 1) + ">" + eol + blocks + indent + "</" + qualified + ">"
        + text.substring(sectionEl.end());
    }
    int close = text.lastIndexOf("</", sectionEl.end() - 1);
    return text.substring(0, gt + 1) + eol + blocks + indent + text.substring(close);
  }

  /** Допустимость секции у вида узла: у таблиц и диаграмм нет сортировки и полей группировки. */
  private static void requireSectionAllowed(NodeKind kind, ListSection section) {
    boolean allowed = switch (section) {
      case SELECTION -> kind != NodeKind.NESTED_OBJECT;
      case FILTER, ORDER -> kind == NodeKind.SETTINGS || kind == NodeKind.GROUP;
      case GROUP_FIELDS -> kind == NodeKind.GROUP;
    };
    if (!allowed) {
      throw new IllegalArgumentException(
        "У узла вида \"" + kindName(kind) + "\" нет секции " + section.title + ".");
    }
  }

  /**
   * Отказ на поле группировки, отличном от {@code GroupItemField}: чтение его не показывает,
   * и сплайс не должен молча его снять.
   */
  private static void requireSpliceable(String text, XmlLines.Node item, ListSection section) {
    if (section != ListSection.GROUP_FIELDS) {
      return;
    }
    String tag = text.substring(item.start(), XmlLines.startTagEnd(text, item.start()) + 1);
    Matcher type = XSI_TYPE.matcher(tag);
    String value = type.find() ? type.group(1) : "";
    if (!"GroupItemField".equals(value.substring(value.indexOf(':') + 1))) {
      throw new IllegalArgumentException("Поле группировки записано элементом "
        + (value.isEmpty() ? "без xsi:type" : value) + ": сплайс знает только GroupItemField.");
    }
  }

  /** Компактный XML нового или изменённого элемента секции. */
  private static String sectionItemXml(ListSection section, Map<String, Object> item) {
    return switch (section) {
      case SELECTION -> selectionItemXml(item);
      case FILTER -> filterItemXml(item);
      case ORDER -> orderItemXml(item);
      case GROUP_FIELDS -> groupFieldItemXml(item);
    };
  }

  /** Поле выбора по образцам платформы; вложенные поля папки пишутся рекурсивно. */
  private static String selectionItemXml(Map<String, Object> item) {
    for (String key : item.keySet()) {
      if (!SELECTION_ITEM_PROPS.contains(key)) {
        throw new IllegalArgumentException("Неизвестное свойство поля выбора: " + key);
      }
    }
    String kind = itemKindOf(item, List.of("field", "auto", "folder"), "поля выбора");
    if ("auto".equals(kind)) {
      refuseKeys(item, List.of("kind", "use"), "авто-поля выбора");
      String use = useXml("dcsset:use", item.get("use"));
      return use.isEmpty()
        ? "<dcsset:item xsi:type=\"dcsset:SelectedItemAuto\"/>"
        : "<dcsset:item xsi:type=\"dcsset:SelectedItemAuto\">" + use + "</dcsset:item>";
    }
    if ("field".equals(kind)) {
      refuseKeys(item, List.of("kind", "use", "field", "title", "titleKind", "viewMode"), "поля выбора");
      if (!(item.get("field") instanceof String field) || field.isBlank()) {
        throw new IllegalArgumentException("У поля выбора не задан field.");
      }
      return "<dcsset:item xsi:type=\"dcsset:SelectedItemField\">"
        + useXml("dcsset:use", item.get("use"))
        + "<dcsset:field>" + DcsRead.escape(field) + "</dcsset:field>"
        + titleXml(item)
        + viewModeXml(item.get("viewMode"))
        + "</dcsset:item>";
    }
    StringBuilder xml = new StringBuilder("<dcsset:item xsi:type=\"dcsset:SelectedItemFolder\">")
      .append(useXml("dcsset:use", item.get("use")));
    Object field = item.get("field");
    if (field != null) {
      if (!(field instanceof String name) || name.isBlank()) {
        throw new IllegalArgumentException("Значение field папки выбора должно быть непустой строкой.");
      }
      xml.append("<dcsset:field>").append(DcsRead.escape(name)).append("</dcsset:field>");
    }
    xml.append(titleXml(item));
    Object nested = item.get("items");
    if (nested != null) {
      for (Map<String, Object> child : itemMaps(nested, "items")) {
        xml.append(selectionItemXml(child));
      }
    }
    // размещение у папки обязательно: по умолчанию Auto, как у платформы
    xml.append("<dcsset:placement>")
      .append(lexeme(item.get("placement"), "placement", PLACEMENTS, "Auto"))
      .append("</dcsset:placement>");
    return xml.append(viewModeXml(item.get("viewMode"))).append("</dcsset:item>").toString();
  }

  /** Элемент сортировки: поле с направлением (autoOrder и distinctNullValues passthrough) либо авто. */
  private static String orderItemXml(Map<String, Object> item) {
    for (String key : item.keySet()) {
      if (!ORDER_ITEM_PROPS.contains(key)) {
        throw new IllegalArgumentException("Неизвестное свойство элемента сортировки: " + key);
      }
    }
    String kind = itemKindOf(item, List.of("field", "auto"), "элемента сортировки");
    if ("auto".equals(kind)) {
      refuseKeys(item, List.of("kind", "use"), "авто-элемента сортировки");
      String use = useXml("dcsset:use", item.get("use"));
      return use.isEmpty()
        ? "<dcsset:item xsi:type=\"dcsset:OrderItemAuto\"/>"
        : "<dcsset:item xsi:type=\"dcsset:OrderItemAuto\">" + use + "</dcsset:item>";
    }
    if (!(item.get("field") instanceof String field) || field.isBlank()) {
      throw new IllegalArgumentException("У элемента сортировки не задан field.");
    }
    return "<dcsset:item xsi:type=\"dcsset:OrderItemField\">"
      + useXml("dcsset:use", item.get("use"))
      + "<dcsset:field>" + DcsRead.escape(field) + "</dcsset:field>"
      + "<dcsset:orderType>" + lexeme(item.get("orderType"), "orderType", ORDER_TYPES, "Asc")
      + "</dcsset:orderType>"
      + flagXml("autoOrder", item.get("autoOrder"))
      + flagXml("distinctNullValues", item.get("distinctNullValues"))
      + viewModeXml(item.get("viewMode"))
      + "</dcsset:item>";
  }

  /** Элемент отбора: сравнение с повторяемым {@code right} либо группа с вложенными элементами прямыми детьми. */
  private static String filterItemXml(Map<String, Object> item) {
    for (String key : item.keySet()) {
      if (!FILTER_ITEM_PROPS.contains(key)) {
        throw new IllegalArgumentException("Неизвестное свойство элемента отбора: " + key);
      }
    }
    String kind = itemKindOf(item, List.of("comparison", "group"), "элемента отбора");
    if ("group".equals(kind)) {
      refuseKeys(item, FILTER_GROUP_PROPS, "группы отбора");
      StringBuilder xml = new StringBuilder("<dcsset:item xsi:type=\"dcsset:FilterItemGroup\">")
        .append(useXml("dcsset:use", item.get("use")))
        .append("<dcsset:groupType>")
        .append(lexeme(item.get("groupType"), "groupType", FILTER_GROUP_TYPES, "AndGroup"))
        .append("</dcsset:groupType>");
      Object nested = item.get("items");
      if (nested != null) {
        for (Map<String, Object> child : itemMaps(nested, "items")) {
          xml.append(filterItemXml(child));
        }
      }
      return xml.append(filterTailXml(item)).append("</dcsset:item>").toString();
    }
    refuseKeys(item, COMPARISON_PROPS, "сравнения отбора");
    if (!(item.get("left") instanceof String left) || left.isBlank()) {
      throw new IllegalArgumentException("У сравнения отбора не задан left (поле левого значения).");
    }
    String comparisonType = lexeme(item.get("comparisonType"), "comparisonType", COMPARISON_TYPES, "Equal");
    StringBuilder xml = new StringBuilder("<dcsset:item xsi:type=\"dcsset:FilterItemComparison\">")
      .append(useXml("dcsset:use", item.get("use")))
      .append("<dcsset:left xsi:type=\"dcscor:Field\">").append(DcsRead.escape(left))
      .append("</dcsset:left>")
      .append("<dcsset:comparisonType>").append(comparisonType).append("</dcsset:comparisonType>");
    Object right = item.get("right");
    if (right != null) {
      if (!(right instanceof List<?> values)) {
        throw new IllegalArgumentException("Поле right должно быть массивом значений Value.");
      }
      if (!values.isEmpty() && ("Filled".equals(comparisonType) || "NotFilled".equals(comparisonType))) {
        throw new IllegalArgumentException("Сравнение " + comparisonType + " не несёт правого значения.");
      }
      for (Object value : values) {
        xml.append(valueXml("dcsset:right", value, false));
      }
    }
    return xml.append(filterTailXml(item)).append("</dcsset:item>").toString();
  }

  /**
   * Хвост элемента отбора по xs:sequence: {@code presentation}, {@code application},
   * {@code viewMode}, {@code userSettingID}, {@code userSettingPresentation}.
   */
  private static String filterTailXml(Map<String, Object> item) {
    StringBuilder xml = new StringBuilder(localizedXml("presentation", item.get("presentation")));
    if (item.get("application") != null) {
      xml.append("<dcsset:application>")
        .append(lexeme(item.get("application"), "application", FILTER_APPLICATIONS, "Items"))
        .append("</dcsset:application>");
    }
    return xml.append(settingsTailXml(item)).toString();
  }

  /**
   * Общий хвост элемента настроек: {@code viewMode}, {@code userSettingID},
   * {@code userSettingPresentation}.
   */
  private static String settingsTailXml(Map<String, Object> item) {
    StringBuilder xml = new StringBuilder(viewModeXml(item.get("viewMode")));
    Object id = item.get("userSettingID");
    if (id != null) {
      // userSettingID пишется как дан и не генерируется
      if (!(id instanceof String uuid) || uuid.isBlank()) {
        throw new IllegalArgumentException("Значение userSettingID должно быть непустой строкой.");
      }
      xml.append("<dcsset:userSettingID>").append(DcsRead.escape(uuid)).append("</dcsset:userSettingID>");
    }
    return xml.append(localizedXml("userSettingPresentation", item.get("userSettingPresentation")))
      .toString();
  }

  /**
   * Представление строкой языка конфигурации ({@code v8:LocalStringType}, основная форма в выгрузках);
   * без значения не пишется. Форма {@code xs:string} сохраняется только у неизменённого элемента.
   */
  private static String localizedXml(String element, Object raw) {
    if (raw == null) {
      return "";
    }
    if (!(raw instanceof String text)) {
      throw new IllegalArgumentException("Значение " + element + " должно быть строкой.");
    }
    return "<dcsset:" + element + " xsi:type=\"v8:LocalStringType\"><v8:item><v8:lang>"
      + ConfigurationLanguage.current() + "</v8:lang><v8:content>" + DcsRead.escape(text)
      + "</v8:content></v8:item></dcsset:" + element + ">";
  }

  /** Поле группировки set-операции: проверка свойств и пятёрка {@code GroupItemField} целиком. */
  private static String groupFieldItemXml(Map<String, Object> item) {
    for (String key : item.keySet()) {
      if (!GROUP_FIELD_PROPS.contains(key)) {
        throw new IllegalArgumentException("Неизвестное свойство поля группировки: " + key);
      }
    }
    if (!(item.get("field") instanceof String name) || name.isBlank()) {
      throw new IllegalArgumentException("У поля группировки не задан field.");
    }
    return groupFieldXml(item);
  }

  /**
   * Заголовок поля выбора: по умолчанию {@code lwsTitle} языком конфигурации, при
   * {@code titleKind: "string"} простая форма {@code title}, как у прочитанного элемента.
   */
  private static String titleXml(Map<String, Object> item) {
    Object title = item.get("title");
    Object titleKind = item.get("titleKind");
    if (titleKind != null && !"lws".equals(titleKind) && !"string".equals(titleKind)) {
      throw new IllegalArgumentException(
        "Значение titleKind должно быть lws или string, а не " + titleKind + ".");
    }
    if (title == null) {
      if (titleKind != null) {
        throw new IllegalArgumentException("titleKind без title не имеет смысла: задайте заголовок.");
      }
      return "";
    }
    if (!(title instanceof String text)) {
      throw new IllegalArgumentException("Значение title должно быть строкой.");
    }
    if ("string".equals(titleKind)) {
      return "<dcsset:title>" + DcsRead.escape(text) + "</dcsset:title>";
    }
    return "<dcsset:lwsTitle><v8:item><v8:lang>" + ConfigurationLanguage.current()
      + "</v8:lang><v8:content>" + DcsRead.escape(text) + "</v8:content></v8:item></dcsset:lwsTitle>";
  }

  /**
   * Флаг использования ({@code dcsset:use}, {@code dcscor:use}): пишется только {@code false}.
   * {@code true} - умолчание, которое платформа не хранит: загрузка его отбрасывает.
   *
   * @param qualified имя элемента с префиксом: {@code dcsset:use} либо {@code dcscor:use}
   * @throws IllegalArgumentException если значение не булево
   */
  private static String useXml(String qualified, Object raw) {
    if (raw == null) {
      return "";
    }
    if (!(raw instanceof Boolean flag)) {
      throw new IllegalArgumentException("Значение use должно быть true или false.");
    }
    return flag ? "" : "<" + qualified + ">false</" + qualified + ">";
  }

  /** Булев элемент по заданному значению; без значения не пишется, как у платформы. */
  private static String flagXml(String element, Object raw) {
    if (raw == null) {
      return "";
    }
    if (!(raw instanceof Boolean flag)) {
      throw new IllegalArgumentException("Значение " + element + " должно быть true или false.");
    }
    return "<dcsset:" + element + ">" + flag + "</dcsset:" + element + ">";
  }

  /** Режим просмотра элемента; без значения не пишется. */
  private static String viewModeXml(Object raw) {
    if (raw == null) {
      return "";
    }
    return "<dcsset:viewMode>" + lexeme(raw, "viewMode", VIEW_MODES, "Normal") + "</dcsset:viewMode>";
  }

  /** Вид элемента секции из его {@code kind}; не из допустимых - отказ. */
  private static String itemKindOf(Map<String, Object> item, List<String> allowed, String what) {
    Object raw = item.get("kind");
    if (!(raw instanceof String kind) || !allowed.contains(kind)) {
      throw new IllegalArgumentException(
        "Вид " + what + " (kind) должен быть одним из " + allowed + ", а не " + raw + ".");
    }
    return kind;
  }

  /** Отказ по свойству, которое не имеет смысла у данного вида элемента; null не считается. */
  private static void refuseKeys(Map<String, Object> item, List<String> allowed, String what) {
    for (Map.Entry<String, Object> entry : item.entrySet()) {
      if (entry.getValue() != null && !allowed.contains(entry.getKey())) {
        throw new IllegalArgumentException("У " + what + " нет свойства " + entry.getKey() + ".");
      }
    }
  }

  /** Элементы items set-операции: массив объектов; ключи приводятся к строкам. */
  private static List<Map<String, Object>> itemMaps(Object raw, String field) {
    if (raw == null) {
      throw new IllegalArgumentException("обязательное поле не задано: " + field);
    }
    if (!(raw instanceof List<?> rawItems)) {
      throw new IllegalArgumentException("Поле " + field + " должно быть массивом элементов секции.");
    }
    List<Map<String, Object>> items = new ArrayList<>(rawItems.size());
    for (Object rawItem : rawItems) {
      if (!(rawItem instanceof Map<?, ?> map)) {
        throw new IllegalArgumentException("Элемент " + field + " должен быть объектом.");
      }
      Map<String, Object> item = new LinkedHashMap<>();
      for (Map.Entry<?, ?> entry : map.entrySet()) {
        item.put(String.valueOf(entry.getKey()), entry.getValue());
      }
      items.add(item);
    }
    return items;
  }

  /**
   * Каноничный вид значения DTO для глубокого сравнения: ключи с {@code null} пропускаются,
   * порядок ключей не важен, числа сравниваются по величине (JSON несёт их дробными, чтение
   * отдаёт {@code BigDecimal}). Им же сверяет эхо {@link DcsEdit#setParameter}.
   */
  static Object normalized(Object value) {
    return normalized(value, false);
  }

  /**
   * Как {@link #normalized(Object)}, но {@code use: true} на любом уровне пропускается: это умолчание,
   * которое платформа не хранит ({@link #useXml}). Запрос с {@code use: true} равен прочитанному
   * элементу без {@code use}, и наоборот.
   */
  private static Object normalizedItem(Object value) {
    return normalized(value, true);
  }

  private static Object normalized(Object value, boolean defaultUse) {
    if (value instanceof Map<?, ?> map) {
      TreeMap<String, Object> out = new TreeMap<>();
      for (Map.Entry<?, ?> entry : map.entrySet()) {
        String key = String.valueOf(entry.getKey());
        boolean defaultFlag = defaultUse && "use".equals(key) && Boolean.TRUE.equals(entry.getValue());
        if (entry.getValue() == null || defaultFlag) {
          continue;
        }
        out.put(key, normalized(entry.getValue(), defaultUse));
      }
      return out;
    }
    if (value instanceof List<?> list) {
      List<Object> out = new ArrayList<>(list.size());
      for (Object element : list) {
        out.add(normalized(element, defaultUse));
      }
      return out;
    }
    if (value instanceof Number number) {
      return new BigDecimal(number.toString()).stripTrailingZeros();
    }
    return value;
  }

  /**
   * Объявления пространств имён корневого тега: ими оборачиваются фрагменты для разбора DTO,
   * в том числе в {@link DcsRead} и {@link DcsEdit}.
   */
  static String rootNamespaces(String text) {
    int start = text.indexOf("<DataCompositionSchema");
    if (start < 0) {
      throw new IllegalArgumentException("Файл не является схемой компоновки данных.");
    }
    StringBuilder out = new StringBuilder();
    Matcher xmlns = XMLNS_DECLARATION.matcher(text.substring(start, XmlLines.startTagEnd(text, start)));
    while (xmlns.find()) {
      out.append(' ').append(xmlns.group());
    }
    return out.toString();
  }

  /** Вид нового узла: kind обязателен при structure и недопустим у прочих списков. */
  private static String itemKind(Object raw, String list, boolean plainItem) {
    if (!"structure".equals(list)) {
      if (raw != null) {
        throw new IllegalArgumentException(
          "kind допустим только при list=structure: вид элемента " + list + " предрешён (группа).");
      }
      return "group";
    }
    if (!(raw instanceof String kind) || kind.isBlank()) {
      throw new IllegalArgumentException("обязательное поле не задано: kind (group, table или chart)");
    }
    if (!ITEM_KINDS.contains(kind)) {
      throw new IllegalArgumentException(
        "Вид узла структуры должен быть group, table или chart, а не " + kind + ".");
    }
    if (plainItem && !"group".equals(kind)) {
      throw new IllegalArgumentException("Внутрь группы строки, колонки, точки и серии "
        + "вкладываются только группировки: kind=" + kind + " не применим.");
    }
    return kind;
  }

  /** Поля группировки нового узла; у таблиц и диаграмм групповых полей нет. */
  private static List<Map<String, Object>> groupFields(Object raw, String kind) {
    if (raw == null) {
      return List.of();
    }
    if (!(raw instanceof List<?> rawFields)) {
      throw new IllegalArgumentException("Поле groupFields должно быть массивом объектов GroupField.");
    }
    List<Map<String, Object>> fields = new ArrayList<>();
    for (Object rawField : rawFields) {
      if (!(rawField instanceof Map<?, ?> field)) {
        throw new IllegalArgumentException("Элемент groupFields должен быть объектом GroupField.");
      }
      Map<String, Object> checked = new LinkedHashMap<>();
      for (Map.Entry<?, ?> entry : field.entrySet()) {
        String key = String.valueOf(entry.getKey());
        if (!GROUP_FIELD_PROPS.contains(key)) {
          throw new IllegalArgumentException("Неизвестное свойство поля группировки: " + key);
        }
        checked.put(key, entry.getValue());
      }
      if (!(checked.get("field") instanceof String name) || name.isBlank()) {
        throw new IllegalArgumentException("У поля группировки не задан field.");
      }
      fields.add(checked);
    }
    if (!fields.isEmpty() && !"group".equals(kind)) {
      throw new IllegalArgumentException("groupFields применимы только к группировке, а не к " + kind + ".");
    }
    return fields;
  }

  /** Компактный XML нового узла по скаффолдам конфигуратора. */
  private static String newItemXml(
    String list, String kind, boolean plainItem, List<Map<String, Object>> groupFields) {
    if ("table".equals(kind) || "chart".equals(kind)) {
      return "<dcsset:item xsi:type=\"dcsset:StructureItem"
        + ("table".equals(kind) ? "Table" : "Chart") + "\"/>";
    }
    String qualified = "dcsset:" + listChildName(list);
    StringBuilder xml = new StringBuilder("<").append(qualified);
    if ("structure".equals(list) && !plainItem) {
      xml.append(" xsi:type=\"dcsset:StructureItemGroup\"");
    }
    xml.append('>');
    if (!groupFields.isEmpty()) {
      xml.append("<dcsset:groupItems>");
      for (Map<String, Object> field : groupFields) {
        xml.append(groupFieldXml(field));
      }
      xml.append("</dcsset:groupItems>");
    }
    xml.append("<dcsset:order><dcsset:item xsi:type=\"dcsset:OrderItemAuto\"/></dcsset:order>");
    xml.append("<dcsset:selection><dcsset:item xsi:type=\"dcsset:SelectedItemAuto\"/></dcsset:selection>");
    return xml.append("</").append(qualified).append('>').toString();
  }

  /** Пятёрка {@code GroupItemField} целиком, как её пишет платформа. */
  private static String groupFieldXml(Map<String, Object> field) {
    return "<dcsset:item xsi:type=\"dcsset:GroupItemField\">"
      + "<dcsset:field>" + DcsRead.escape((String) field.get("field")) + "</dcsset:field>"
      + "<dcsset:groupType>" + lexeme(field.get("groupType"), "groupType", GROUP_TYPES, "Items")
      + "</dcsset:groupType>"
      + "<dcsset:periodAdditionType>"
      + lexeme(field.get("periodAdditionType"), "periodAdditionType", PERIOD_ADDITION_TYPES, "None")
      + "</dcsset:periodAdditionType>"
      + periodBound("periodAdditionBegin", field.get("periodAdditionBegin"))
      + periodBound("periodAdditionEnd", field.get("periodAdditionEnd"))
      + "</dcsset:item>";
  }

  /** Лексема перечисления из payload; без значения - умолчание платформы. */
  private static String lexeme(Object raw, String field, List<String> allowed, String fallback) {
    if (raw == null) {
      return fallback;
    }
    if (!(raw instanceof String value) || !allowed.contains(value)) {
      throw new IllegalArgumentException(
        "Значение " + field + " должно быть одним из " + allowed + ", а не " + raw + ".");
    }
    return value;
  }

  /**
   * Граница дополнения периода пятёрки {@code GroupItemField}: без значения - дата платформы
   * по умолчанию, {@code {nil: true}} - {@code xsi:nil}, {@code {type, text}} - дата
   * {@code xs:dateTime}, поле {@code dcscor:Field} либо лексема
   * {@code dcscor:DataCompositionPeriodAdditionType}. Прочие типы - отказ.
   */
  private static String periodBound(String element, Object raw) {
    String open = "<dcsset:" + element;
    if (raw == null) {
      return open + " xsi:type=\"xs:dateTime\">" + PERIOD_BOUND_DEFAULT + "</dcsset:" + element + ">";
    }
    if (raw instanceof Map<?, ?> value) {
      if (Boolean.TRUE.equals(value.get("nil"))) {
        return open + " xsi:nil=\"true\"/>";
      }
      Object type = value.get("type");
      Object text = lexicalText(value);
      if ("xs:dateTime".equals(type) && text instanceof String date
        && DATE_TIME_TEXT.matcher(date).matches()) {
        return open + " xsi:type=\"xs:dateTime\">" + date + "</dcsset:" + element + ">";
      }
      if ("dcscor:Field".equals(type) && text instanceof String field && !field.isBlank()) {
        return open + " xsi:type=\"dcscor:Field\">" + DcsRead.escape(field) + "</dcsset:" + element + ">";
      }
      if ("dcscor:DataCompositionPeriodAdditionType".equals(type) && text instanceof String addition
        && PERIOD_ADDITION_TYPES.contains(addition)) {
        return open + " xsi:type=\"dcscor:DataCompositionPeriodAdditionType\">" + addition
          + "</dcsset:" + element + ">";
      }
    }
    throw new IllegalArgumentException("Граница " + element + " пишется датой {type: \"xs:dateTime\","
      + " text: \"0001-01-01T00:00:00\"}, полем {type: \"dcscor:Field\", text}, дополнением"
      + " {type: \"dcscor:DataCompositionPeriodAdditionType\", text} либо {nil: true}; значение"
      + " другого типа доступно только для чтения.");
  }

  // ------------------------------------------------------------------ параметры данных и вывода

  /**
   * Правит значение параметра данных или вывода ({@code cf-dcs-set-parameter-value}).
   *
   * <p>У существующего {@code dcscor:item} заменяются только {@code use} и {@code value}, прочее
   * (под-параметры, {@code viewMode}, {@code userSettingID}) не трогается. {@code use} пишется
   * только значением {@code false} ({@link #useXml}), {@code use: true} снимает записанный
   * {@code false}. Без ключа {@code value} значение не меняется, {@code value: null} пишет
   * {@code xsi:nil}. Скаляры пишутся как {@code xs:string}, {@code xs:decimal} или
   * {@code xs:boolean}, объект {@code Value} - по белому списку записи.
   *
   * <p>Отсутствующий параметр создаётся из {@code dcscor:parameter} и {@code dcscor:value}
   * ({@code xsi:nil} вместо пустого значения). Новый параметр вывода встаёт по порядку платформы
   * перед первым параметром с большим рангом; параметр данных и имя вне таблицы рангов - в конец
   * {@code item*} перед хвостом секции. Отсутствующая секция встаёт по xs:sequence вида узла.
   * {@code remove: true} вырезает элемент и снимает опустевшую секцию без хвоста; опустевший узел
   * сворачивается ({@link #collapsedIfEmpty}).
   *
   * <p>Параметры данных есть только у корня настроек: {@code section=data} с непустым путём - отказ.
   * Под-параметры (имя с точкой) доступны только для чтения. Параметр вывода, которого платформа
   * на уровне узла не хранит ({@link #refuseUnkeptOutputParameter}), нельзя создать или изменить;
   * эхо и удаление уже записанного допустимы.
   *
   * @param variant имя варианта среди прямых детей корня
   * @param payload {@code section} - {@code data} либо {@code output}, {@code path} - путь узла
   *     (по умолчанию корень настроек), {@code parameter} - имя параметра, {@code value} -
   *     новое значение, {@code use} - флаг использования, {@code remove} - удалить элемент
   * @throws IllegalArgumentException если адресация не разрешается, имя с точкой,
   *     значение не по белому списку, удаляемого параметра нет или параметр вывода задаётся
   *     там, где платформа его не хранит
   */
  public static void setParameterValue(
    Path templateXml, SchemaVersion version, String variant, Map<String, Object> payload)
    throws IOException, JAXBException {
    String variantName = required(variant, "variant");
    knownKeys(payload, SET_PARAMETER_PAYLOAD);
    String section = parameterSection(payload.get("section"));
    List<Step> path = payload.get("path") == null ? List.of() : steps(payload.get("path"), "path");
    if ("dataParameters".equals(section) && !path.isEmpty()) {
      throw new IllegalArgumentException("Параметры данных есть только у корня настроек варианта:"
        + " path должен быть пустым (у узлов структуры секции dataParameters нет).");
    }
    if (!(payload.get("parameter") instanceof String parameter) || parameter.isBlank()) {
      throw new IllegalArgumentException("обязательное поле не задано: parameter");
    }
    if (parameter.indexOf('.') >= 0) {
      throw new IllegalArgumentException(
        "Под-параметры (имя с точкой) доступны только для чтения: " + parameter);
    }
    Object removeRaw = payload.get("remove");
    if (removeRaw != null && !(removeRaw instanceof Boolean)) {
      throw new IllegalArgumentException("Значение remove должно быть true или false.");
    }
    boolean remove = Boolean.TRUE.equals(removeRaw);
    if (remove && (payload.containsKey("value") || payload.get("use") != null)) {
      throw new IllegalArgumentException(
        "remove: true не сочетается с value и use: элемент удаляется целиком.");
    }
    ConfigurationLanguage.with(templateXml, () -> {
      String text = Files.readString(templateXml, StandardCharsets.UTF_8);
      ResolvedNode node = resolveNode(text, variantName, path);
      if (node.kind() == NodeKind.NESTED_OBJECT) {
        throw new IllegalArgumentException(
          "У узла вида \"вложенный отчёт\" параметры вывода не редактируются.");
      }
      String updated = parameterEdited(text, node, section, parameter, payload, remove);
      if (!updated.equals(text)) {
        if (!remove && "outputParameters".equals(section)) {
          refuseUnkeptOutputParameter(node, path, parameter);
        }
        DcsRead.verify(updated, version);
        Files.writeString(templateXml, updated, StandardCharsets.UTF_8);
      }
      return null;
    });
  }

  /**
   * Отказ в параметре вывода, которого платформа не хранит на уровне узла ({@link OutputLevel#of}).
   * Текст отказа подсказывает, где параметр хранится.
   */
  private static void refuseUnkeptOutputParameter(ResolvedNode node, List<Step> path, String parameter) {
    if (node.kind() == NodeKind.SETTINGS && ROOT_FORBIDDEN_OUTPUT_PARAMETERS.contains(parameter)) {
      throw new IllegalArgumentException("Параметр вывода «" + parameter + "»: платформа не хранит этот"
        + " параметр вывода в корне настроек; " + (TOTAL_PLACEMENT.equals(parameter)
          ? "задайте его у группы строк таблицы." : "задайте его у группировки."));
    }
    boolean structureGroup = node.kind() == NodeKind.GROUP
      && path.stream().allMatch(step -> "structure".equals(step.list()));
    if (structureGroup && GROUP_FORBIDDEN_OUTPUT_PARAMETERS.contains(parameter)) {
      throw new IllegalArgumentException("Параметр вывода «" + parameter + "»: платформа не хранит этот"
        + " параметр вывода у группировки структуры; " + (TOTAL_PLACEMENT.equals(parameter)
          ? "в корне настроек он тоже не хранится, задайте его у группы строк таблицы."
          : "задайте его в корне настроек."));
    }
    // Таблица, диаграмма и их группы
    OutputLevel level = OutputLevel.of(node.kind(), path);
    if (level != OutputLevel.ROOT && level != OutputLevel.STRUCTURE_GROUP && level.forbidden().contains(parameter)) {
      throw new IllegalArgumentException("Параметр вывода «" + parameter + "»: платформа не хранит этот"
        + " параметр вывода " + level.where + "; " + homes(parameter) + ".");
    }
  }

  /**
   * Уровни настроек, на которых платформа хранит разные наборы параметров вывода. Вложенные группы
   * относятся к уровню своей верхней группы.
   */
  private enum OutputLevel {
    ROOT("в корне настроек", "в корне настроек"),
    STRUCTURE_GROUP("у группировки структуры", "у группировки структуры"),
    TABLE("у таблицы", "у таблицы"),
    TABLE_GROUP("у группы строк или колонок таблицы", "у группы таблицы"),
    CHART("у диаграммы", "у диаграммы"),
    CHART_GROUP("у группы точек или серий диаграммы", "у группы диаграммы");

    /** Где не хранится - для текста отказа. */
    private final String where;

    /** Где хранится - для подсказки. */
    private final String home;

    OutputLevel(String where, String home) {
      this.where = where;
      this.home = home;
    }

    /** Имена таблицы рангов, которых платформа на этом уровне не хранит. */
    Set<String> forbidden() {
      return switch (this) {
        case ROOT -> ROOT_FORBIDDEN_OUTPUT_PARAMETERS;
        case STRUCTURE_GROUP -> GROUP_FORBIDDEN_OUTPUT_PARAMETERS;
        case TABLE -> TABLE_FORBIDDEN_OUTPUT_PARAMETERS;
        case TABLE_GROUP -> TABLE_GROUP_FORBIDDEN_OUTPUT_PARAMETERS;
        case CHART -> CHART_FORBIDDEN_OUTPUT_PARAMETERS;
        case CHART_GROUP -> CHART_GROUP_FORBIDDEN_OUTPUT_PARAMETERS;
      };
    }

    /**
     * Уровень узла: по виду, а у группы по шагам пути. Шаг {@code points} или {@code series} - группа
     * диаграммы, {@code rows} или {@code columns} - группа таблицы, только {@code structure} - группировка
     * структуры.
     */
    static OutputLevel of(NodeKind kind, List<Step> path) {
      return switch (kind) {
        case SETTINGS -> ROOT;
        case TABLE -> TABLE;
        case CHART -> CHART;
        case GROUP -> path.stream().anyMatch(step -> List.of("points", "series").contains(step.list()))
          ? CHART_GROUP
          : path.stream().anyMatch(step -> List.of("rows", "columns").contains(step.list()))
            ? TABLE_GROUP : STRUCTURE_GROUP;
        case NESTED_OBJECT -> throw new IllegalArgumentException(
          "У узла вида \"вложенный отчёт\" параметры вывода не редактируются.");
      };
    }
  }

  /** Подсказка, где параметр вывода хранится: уровни, на которых платформа его хранит. */
  private static String homes(String parameter) {
    List<String> kept = new ArrayList<>();
    for (OutputLevel level : OutputLevel.values()) {
      if (!level.forbidden().contains(parameter)) {
        kept.add(level.home);
      }
    }
    if (kept.size() == 1) {
      return "он хранится только " + kept.get(0);
    }
    return "он хранится " + String.join(", ", kept.subList(0, kept.size() - 1)) + " и " + kept.get(kept.size() - 1);
  }

  /** Имя элемента секции параметров по лексеме payload: {@code data} либо {@code output}. */
  private static String parameterSection(Object raw) {
    if (raw == null) {
      throw new IllegalArgumentException("обязательное поле не задано: section (data или output)");
    }
    return switch (raw instanceof String section ? section : "") {
      case "data" -> "dataParameters";
      case "output" -> "outputParameters";
      default -> throw new IllegalArgumentException(
        "Значение section должно быть data или output, а не " + raw + ".");
    };
  }

  /** Правка секции параметров: точечная замена, создание минимального элемента либо вырезка. */
  private static String parameterEdited(String text, ResolvedNode node, String section,
    String parameter, Map<String, Object> payload, boolean remove) {
    List<XmlLines.Node> children = XmlLines.childrenAt(text, node.element().start());
    XmlLines.Node sectionEl = child(children, section);
    List<XmlLines.Node> listed = sectionEl == null
      ? List.of()
      : named(XmlLines.childrenAt(text, sectionEl.start()), "item");
    XmlLines.Node target = null;
    for (XmlLines.Node item : listed) {
      if (!parameter.equals(item.leaves().get("parameter"))) {
        continue;
      }
      if (target != null) {
        throw new IllegalArgumentException("Параметр встречается в секции дважды: " + parameter);
      }
      target = item;
    }
    if (remove) {
      if (target == null) {
        throw new IllegalArgumentException("Параметр не найден: " + parameter);
      }
      if (XmlLines.childrenAt(text, sectionEl.start()).size() == 1) {
        // снимаемый элемент единственный: секция снимается целиком
        return collapsedIfEmpty(XmlLines.removeAll(text,
          List.of(XmlLines.wholeLines(text, sectionEl.start(), sectionEl.end(), section))), node.element().start());
      }
      return XmlLines.removeAll(text,
        List.of(XmlLines.wholeLines(text, target.start(), target.end(), "item")));
    }
    if (target == null) {
      String compact = newParameterItemXml(parameter, payload);
      if (sectionEl == null) {
        String block = "<dcsset:" + section + ">" + compact + "</dcsset:" + section + ">";
        return XmlGranularPatch.apply(text, List.of(collectionInsertion(
          text, node.element(), children, List.of(), sequenceOf(node.kind()), section, 0, block)));
      }
      int at = "outputParameters".equals(section) ? outputParameterIndex(listed, parameter) : listed.size();
      return XmlGranularPatch.apply(text, List.of(collectionInsertion(text, sectionEl,
        XmlLines.childrenAt(text, sectionEl.start()), listed, SECTION_SEQUENCE, "item",
        at, compact)));
    }
    return XmlGranularPatch.withoutOverlaps(parameterReplacements(text, target, parameter, payload))
      .map(safe -> XmlGranularPatch.apply(text, safe))
      .orElseThrow(() -> new IllegalStateException("правки параметра пересеклись"));
  }

  /**
   * Позиция нового параметра вывода: перед первым существующим с большим рангом. Существующие
   * имена вне таблицы рангов пропускаются, новое имя вне таблицы встаёт в конец.
   */
  private static int outputParameterIndex(List<XmlLines.Node> listed, String parameter) {
    Integer rank = OUTPUT_PARAMETER_RANKS.get(parameter);
    if (rank == null) {
      return listed.size();
    }
    for (int i = 0; i < listed.size(); i++) {
      Integer existing = OUTPUT_PARAMETER_RANKS.get(listed.get(i).leaves().get("parameter"));
      if (existing != null && existing > rank) {
        return i;
      }
    }
    return listed.size();
  }

  /** Ранги по группам имён: номер группы в списке; имена одной группы получают общий ранг. */
  private static Map<String, Integer> ranks(List<List<String>> groups) {
    Map<String, Integer> out = new LinkedHashMap<>();
    for (int i = 0; i < groups.size(); i++) {
      for (String name : groups.get(i)) {
        out.put(name, i);
      }
    }
    return Map.copyOf(out);
  }

  /**
   * Точечные замены {@code use} и {@code value} существующего элемента параметра. При {@code use: true}
   * записанный {@code false} снимается целой строкой, записанный {@code true} не трогается ({@link #useXml}).
   */
  private static List<XmlGranularPatch.Replacement> parameterReplacements(
    String text, XmlLines.Node target, String parameter, Map<String, Object> payload) {
    List<XmlGranularPatch.Replacement> replacements = new ArrayList<>();
    List<XmlLines.Node> itemChildren = XmlLines.childrenAt(text, target.start());
    Object use = payload.get("use");
    if (use != null) {
      if (!(use instanceof Boolean flag)) {
        throw new IllegalArgumentException("Значение use должно быть true или false.");
      }
      XmlLines.Node existing = child(itemChildren, "use");
      if (flag) {
        if (existing != null && "false".equals(target.leaves().getOrDefault("use", "").strip())) {
          int[] span = XmlLines.wholeLines(text, existing.start(), existing.end(), "use");
          replacements.add(new XmlGranularPatch.Replacement(span[0], span[1], ""));
        }
      } else if (existing != null) {
        String qualified = qualifiedName(text, existing.start());
        replacements.add(new XmlGranularPatch.Replacement(existing.start(), existing.end(),
          "<" + qualified + ">" + flag + "</" + qualified + ">"));
      } else {
        // по xs:sequence use стоит первым - перед dcscor:parameter
        XmlLines.Node anchor = child(itemChildren, "parameter");
        int lineStart = text.lastIndexOf('\n', anchor.start() - 1) + 1;
        replacements.add(new XmlGranularPatch.Replacement(lineStart, lineStart,
          XmlLines.indentAt(text, anchor.start()) + "<dcscor:use>" + flag + "</dcscor:use>"
            + XmlLines.newline(text)));
      }
    }
    if (payload.containsKey("value")) {
      List<XmlLines.Node> values = named(itemChildren, "value");
      if (values.size() > 1) {
        throw new IllegalArgumentException(
          "У параметра " + parameter + " несколько значений value: точечная правка не поддержана.");
      }
      if (values.isEmpty()) {
        // по xs:sequence value необязателен и стоит после parameter
        XmlLines.Node anchor = child(itemChildren, "parameter");
        String eol = XmlLines.newline(text);
        String block = XmlGranularPatch.formatInsertion(text, XmlLines.indentAt(text, anchor.start()),
          parameterValueXml("dcscor:value", payload.get("value")));
        int lineEnd = text.indexOf('\n', anchor.end());
        replacements.add(lineEnd < 0
          ? new XmlGranularPatch.Replacement(anchor.end(), anchor.end(), eol + block)
          : new XmlGranularPatch.Replacement(lineEnd + 1, lineEnd + 1, block + eol));
      } else {
        XmlLines.Node value = values.get(0);
        if (!echoOfWrittenValue(text, value, payload.get("value"))) {
          replacements.add(new XmlGranularPatch.Replacement(value.start(), value.end(),
            XmlGranularPatch.formatReplacementPreservingIndent(text, value.start(),
              parameterValueXml(qualifiedName(text, value.start()), payload.get("value")))));
        }
      }
    }
    return replacements;
  }

  /**
   * Запрос глубоко равен прочитанному значению параметра. Такое значение не переписывается: писатель
   * не воспроизводит, например, {@code v8:LocalStringType} на нескольких языках. Значения только для
   * чтения ({@code opaque}, {@code attrs}) эхом не считаются: их правку отвергает писатель.
   */
  private static boolean echoOfWrittenValue(String text, XmlLines.Node value, Object requested) {
    if (!(requested instanceof Map<?, ?> map) || Boolean.TRUE.equals(map.get("opaque")) || map.get("attrs") != null) {
      return false;
    }
    Map<String, Object> read = DcsSettingsRead.settingsValue(
      text.substring(value.start(), value.end()), rootNamespaces(text));
    return normalized(read).equals(normalized(requested));
  }

  /** Новый элемент параметра: {@code use} ({@link #useXml}), имя и значение. */
  private static String newParameterItemXml(String parameter, Map<String, Object> payload) {
    StringBuilder xml = new StringBuilder("<dcscor:item xsi:type=\"dcsset:SettingsParameterValue\">")
      .append(useXml("dcscor:use", payload.get("use")));
    xml.append("<dcscor:parameter>").append(DcsRead.escape(parameter)).append("</dcscor:parameter>")
      .append(parameterValueXml("dcscor:value", payload.get("value")));
    return xml.append("</dcscor:item>").toString();
  }

  /**
   * Значение параметра: {@code null} - {@code xsi:nil}, скаляр - {@code xs:string}, {@code xs:boolean}
   * или {@code xs:decimal}, объект {@code Value} - по белому списку записи.
   */
  private static String parameterValueXml(String qualified, Object raw) {
    if (raw == null) {
      return "<" + qualified + " xsi:nil=\"true\"/>";
    }
    if (raw instanceof String text) {
      return "<" + qualified + " xsi:type=\"xs:string\">" + DcsRead.escape(text) + "</" + qualified + ">";
    }
    if (raw instanceof Boolean flag) {
      return "<" + qualified + " xsi:type=\"xs:boolean\">" + flag + "</" + qualified + ">";
    }
    if (raw instanceof Number number) {
      return "<" + qualified + " xsi:type=\"xs:decimal\">"
        + new BigDecimal(number.toString()).stripTrailingZeros().toPlainString()
        + "</" + qualified + ">";
    }
    if (raw instanceof Map) {
      return valueXml(qualified, raw, false);
    }
    throw new IllegalArgumentException(
      "Значение параметра пишется объектом Value либо скаляром string/number/boolean, а не " + raw + ".");
  }

  // ------------------------------------------------------------------ условное оформление

  /**
   * Добавляет элемент условного оформления узла ({@code cf-dcs-add-appearance-item}).
   *
   * <p>Порядок детей по xs:sequence: {@code use}; {@code selection}, {@code filter} и
   * {@code appearance} всегда, пустые самозакрытыми, как у платформы; {@code presentation}, хвост
   * и заданные {@code useIn*}. Поля оформления - {@code dcsset:item} без типа с {@code use} и
   * {@code field}, отбор - элементы {@link #setFilter}, оформление - значения параметров по белому
   * списку записи. Элемент встаёт в позицию {@code index} (по умолчанию в конец), отсутствующая
   * секция создаётся по xs:sequence вида узла.
   *
   * @param variant имя варианта среди прямых детей корня
   * @param payload {@code path} - путь узла (пустой - корень настроек), {@code index} -
   *     позиция вставки, {@code item} - элемент условного оформления ({@code CAItem})
   * @throws IllegalArgumentException если адресация не разрешается, у узла нет условного
   *     оформления (вложенный отчёт) или элемент не по формату
   */
  public static void addAppearanceItem(
    Path templateXml, SchemaVersion version, String variant, Map<String, Object> payload)
    throws IOException, JAXBException {
    String variantName = required(variant, "variant");
    knownKeys(payload, APPEARANCE_ITEM_PAYLOAD);
    List<Step> path = steps(payload.get("path"), "path");
    Map<String, Object> item = appearanceItemMap(payload.get("item"));
    ConfigurationLanguage.with(templateXml, () -> {
      String text = Files.readString(templateXml, StandardCharsets.UTF_8);
      ResolvedNode node = resolveNode(text, variantName, path);
      requireAppearanceAllowed(node.kind());
      List<XmlLines.Node> children = XmlLines.childrenAt(text, node.element().start());
      XmlLines.Node sectionEl = child(children, "conditionalAppearance");
      String compact = appearanceItemXml(item, null, null);
      String updated;
      if (sectionEl == null) {
        insertionIndex(payload.get("index"), "условного оформления", 0);
        String block = "<dcsset:conditionalAppearance>" + compact + "</dcsset:conditionalAppearance>";
        updated = XmlGranularPatch.apply(text, List.of(collectionInsertion(text, node.element(),
          children, List.of(), sequenceOf(node.kind()), "conditionalAppearance", 0, block)));
      } else {
        List<XmlLines.Node> sectionChildren = XmlLines.childrenAt(text, sectionEl.start());
        List<XmlLines.Node> listed = named(sectionChildren, "item");
        int index = insertionIndex(payload.get("index"), "условного оформления", listed.size());
        updated = XmlGranularPatch.apply(text, List.of(collectionInsertion(
          text, sectionEl, sectionChildren, listed, SECTION_SEQUENCE, "item", index, compact)));
      }
      DcsRead.verify(updated, version);
      Files.writeString(templateXml, updated, StandardCharsets.UTF_8);
      return null;
    });
  }

  /**
   * Заменяет элемент условного оформления узла целиком ({@code cf-dcs-set-appearance-item}).
   *
   * <p>Запрос, равный прочитанному, файл не меняет. Изменённый элемент переписывается по правилам
   * {@link #addAppearanceItem}, но неизменённые вложенные элементы отбора и оформления сохраняют
   * исходные байты: значение типа вне белого списка (шрифт, картинка) переживает правку соседей,
   * а его собственная правка - отказ. {@code userSettingID} без запроса переносится из файла,
   * сменить его нельзя.
   *
   * @param variant имя варианта среди прямых детей корня
   * @param payload {@code path} - путь узла, {@code index} - номер элемента,
   *     {@code item} - элемент условного оформления целиком
   * @throws IllegalArgumentException если адресация не разрешается, номера нет в секции
   *     или элемент не по формату
   */
  public static void setAppearanceItem(
    Path templateXml, SchemaVersion version, String variant, Map<String, Object> payload)
    throws IOException, JAXBException {
    String variantName = required(variant, "variant");
    knownKeys(payload, APPEARANCE_ITEM_PAYLOAD);
    List<Step> path = steps(payload.get("path"), "path");
    Map<String, Object> item = appearanceItemMap(payload.get("item"));
    ConfigurationLanguage.with(templateXml, () -> {
      String text = Files.readString(templateXml, StandardCharsets.UTF_8);
      XmlLines.Node sectionEl = appearanceSection(text, resolveNode(text, variantName, path));
      XmlLines.Node target = appearanceItemAt(text, sectionEl, payload.get("index"));
      String xmlns = rootNamespaces(text);
      Map<String, Object> read = DcsSettingsRead.sectionItemDto(
        text.substring(target.start(), target.end()), xmlns, "conditionalAppearance");
      Map<String, Object> requested = new LinkedHashMap<>(item);
      Object readId = read.get("userSettingID");
      Object requestedId = requested.get("userSettingID");
      if (requestedId == null) {
        if (readId != null) {
          requested.put("userSettingID", readId);
        }
      } else if (!requestedId.equals(readId)) {
        throw new IllegalArgumentException("userSettingID элемента условного оформления "
          + "не генерируется и не меняется: он переносится как есть.");
      }
      if (normalizedItem(requested).equals(normalizedItem(read))) {
        return null;
      }
      List<String> carried = new ArrayList<>();
      CarriedBytes filterCarry = new CarriedBytes(text, target, "filter", xmlns, carried);
      CarriedBytes appearanceCarry = new CarriedBytes(text, target, "appearance", xmlns, carried);
      String compact = appearanceItemXml(requested, filterCarry, appearanceCarry);
      String formatted = XmlGranularPatch.formatInsertion(
        text, XmlLines.indentAt(text, target.start()), compact);
      String eol = XmlLines.newline(text);
      String block = resolveCarried(formatted, carried, eol);
      int[] span = XmlLines.wholeLines(text, target.start(), target.end(), "item");
      String updated = text.substring(0, span[0]) + block + eol + text.substring(span[1]);
      DcsRead.verify(updated, version);
      Files.writeString(templateXml, updated, StandardCharsets.UTF_8);
      return null;
    });
  }

  /**
   * Удаляет элемент условного оформления узла целыми строками
   * ({@code cf-dcs-remove-appearance-item}); опустевшая секция без хвоста снимается, а опустевший
   * узел сворачивается ({@link #collapsedIfEmpty}).
   *
   * @param variant имя варианта среди прямых детей корня
   * @param payload {@code path} - путь узла, {@code index} - номер элемента
   * @throws IllegalArgumentException если адресация не разрешается или номера нет в секции
   */
  public static void removeAppearanceItem(
    Path templateXml, SchemaVersion version, String variant, Map<String, Object> payload)
    throws IOException, JAXBException {
    String variantName = required(variant, "variant");
    knownKeys(payload, REMOVE_APPEARANCE_PAYLOAD);
    List<Step> path = steps(payload.get("path"), "path");
    String text = Files.readString(templateXml, StandardCharsets.UTF_8);
    ResolvedNode node = resolveNode(text, variantName, path);
    XmlLines.Node sectionEl = appearanceSection(text, node);
    XmlLines.Node target = appearanceItemAt(text, sectionEl, payload.get("index"));
    String updated = XmlLines.childrenAt(text, sectionEl.start()).size() == 1
      // снимаемый элемент единственный: секция снимается целиком
      ? collapsedIfEmpty(XmlLines.removeAll(text, List.of(XmlLines.wholeLines(
        text, sectionEl.start(), sectionEl.end(), "conditionalAppearance"))), node.element().start())
      : XmlLines.removeAll(text, List.of(XmlLines.wholeLines(text, target.start(), target.end(), "item")));
    DcsRead.verify(updated, version);
    Files.writeString(templateXml, updated, StandardCharsets.UTF_8);
  }

  /** У вложенного отчёта условное оформление не редактируется. */
  private static void requireAppearanceAllowed(NodeKind kind) {
    if (kind == NodeKind.NESTED_OBJECT) {
      throw new IllegalArgumentException(
        "У узла вида \"вложенный отчёт\" нет секции условного оформления.");
    }
  }

  /** Секция условного оформления узла; нет секции - отказ. */
  private static XmlLines.Node appearanceSection(String text, ResolvedNode node) {
    requireAppearanceAllowed(node.kind());
    XmlLines.Node sectionEl = child(
      XmlLines.childrenAt(text, node.element().start()), "conditionalAppearance");
    if (sectionEl == null) {
      throw new IllegalArgumentException("У узла нет секции условного оформления.");
    }
    return sectionEl;
  }

  /** Существующий элемент условного оформления секции по номеру. */
  private static XmlLines.Node appearanceItemAt(String text, XmlLines.Node sectionEl, Object rawIndex) {
    List<XmlLines.Node> listed = named(XmlLines.childrenAt(text, sectionEl.start()), "item");
    int index = index(rawIndex, "index");
    return listed.get(requireListIndex("условного оформления", index, listed.size()));
  }

  /** Элемент условного оформления из payload: объект со строковыми ключами. */
  private static Map<String, Object> appearanceItemMap(Object raw) {
    if (raw == null) {
      throw new IllegalArgumentException("обязательное поле не задано: item");
    }
    if (!(raw instanceof Map<?, ?> map)) {
      throw new IllegalArgumentException(
        "Поле item должно быть объектом CAItem (элементом условного оформления).");
    }
    Map<String, Object> item = new LinkedHashMap<>();
    for (Map.Entry<?, ?> entry : map.entrySet()) {
      item.put(String.valueOf(entry.getKey()), entry.getValue());
    }
    return item;
  }

  /**
   * Компактный XML элемента условного оформления в порядке xs:sequence ({@link #addAppearanceItem}).
   *
   * @param filterCarry перенос байтов неизменённых элементов отбора; {@code null} у нового элемента
   * @param appearanceCarry перенос байтов неизменённых значений оформления
   */
  private static String appearanceItemXml(
    Map<String, Object> item, CarriedBytes filterCarry, CarriedBytes appearanceCarry) {
    for (String key : item.keySet()) {
      if (!APPEARANCE_ITEM_PROPS.contains(key)) {
        throw new IllegalArgumentException("Неизвестное свойство элемента условного оформления: " + key);
      }
    }
    StringBuilder xml = new StringBuilder("<dcsset:item>").append(useXml("dcsset:use", item.get("use")));
    List<Map<String, Object>> fields = item.get("fields") == null
      ? List.of()
      : itemMaps(item.get("fields"), "fields");
    if (fields.isEmpty()) {
      xml.append("<dcsset:selection/>");
    } else {
      xml.append("<dcsset:selection>");
      for (Map<String, Object> field : fields) {
        xml.append(appearanceFieldXml(field));
      }
      xml.append("</dcsset:selection>");
    }
    List<Map<String, Object>> filter = item.get("filter") == null
      ? List.of()
      : itemMaps(item.get("filter"), "filter");
    if (filter.isEmpty()) {
      xml.append("<dcsset:filter/>");
    } else {
      xml.append("<dcsset:filter>");
      for (Map<String, Object> sub : filter) {
        String marker = filterCarry == null ? null : filterCarry.markerOrNull(sub);
        xml.append(marker != null ? marker : filterItemXml(sub));
      }
      xml.append("</dcsset:filter>");
    }
    List<Map<String, Object>> appearance = item.get("appearance") == null
      ? List.of()
      : itemMaps(item.get("appearance"), "appearance");
    if (appearance.isEmpty()) {
      xml.append("<dcsset:appearance/>");
    } else {
      xml.append("<dcsset:appearance>");
      for (Map<String, Object> sub : appearance) {
        String marker = appearanceCarry == null ? null : appearanceCarry.markerOrNull(sub);
        xml.append(marker != null ? marker : appearanceParameterXml(sub, appearanceCarry));
      }
      xml.append("</dcsset:appearance>");
    }
    // По xs:sequence представление и хвост стоят до useIn*: обратный порядок XSD-проверка отвергает
    xml.append(localizedXml("presentation", item.get("presentation"))).append(settingsTailXml(item));
    Object useIn = item.get("useIn");
    if (useIn != null) {
      Map<String, Object> areas = payloadObject(useIn, "useIn", USE_IN_KEYS);
      for (Map.Entry<String, String> area : USE_IN_AREAS) {
        Object mode = areas.get(area.getKey());
        if (mode == null) {
          continue;
        }
        xml.append("<dcsset:").append(area.getValue()).append('>')
          .append(lexeme(mode, "useIn." + area.getKey(), USE_IN_MODES, "Use"))
          .append("</dcsset:").append(area.getValue()).append('>');
      }
    }
    return xml.append("</dcsset:item>").toString();
  }

  /** Поле оформления: безтиповый {@code dcsset:item} с {@code use} и {@code field}. */
  private static String appearanceFieldXml(Map<String, Object> field) {
    for (String key : field.keySet()) {
      if (!APPEARANCE_FIELD_PROPS.contains(key)) {
        throw new IllegalArgumentException("Неизвестное свойство поля оформления: " + key);
      }
    }
    if (!(field.get("field") instanceof String name) || name.isBlank()) {
      throw new IllegalArgumentException("У поля оформления не задан field.");
    }
    return "<dcsset:item>" + useXml("dcsset:use", field.get("use"))
      + "<dcsset:field>" + DcsRead.escape(name) + "</dcsset:field></dcsset:item>";
  }

  /**
   * Значение параметра оформления ({@code SettingsParameterValue}): {@code use} ({@link #useXml}),
   * имя, {@code value} массивом по белому списку записи ({@code xsi:nil} без значения) и хвост.
   * Под-параметры только для чтения: неизменённый элемент переносит {@link CarriedBytes}.
   *
   * @param carry перенос байтов значений, равных прочитанным у параметра с тем же именем;
   *     {@code null} у нового элемента
   */
  private static String appearanceParameterXml(Map<String, Object> item, CarriedBytes carry) {
    for (String key : item.keySet()) {
      if (!PARAMETER_ITEM_PROPS.contains(key)) {
        throw new IllegalArgumentException("Неизвестное свойство значения параметра оформления: " + key);
      }
    }
    if (item.get("items") != null) {
      throw new IllegalArgumentException(
        "Под-параметры (вложенные item значения параметра) доступны только для чтения.");
    }
    if (!(item.get("parameter") instanceof String parameter) || parameter.isBlank()) {
      throw new IllegalArgumentException("У значения параметра оформления не задан parameter.");
    }
    if (parameter.indexOf('.') >= 0) {
      throw new IllegalArgumentException(
        "Под-параметры (имя с точкой) доступны только для чтения: " + parameter);
    }
    StringBuilder xml = new StringBuilder("<dcscor:item xsi:type=\"dcsset:SettingsParameterValue\">")
      .append(useXml("dcscor:use", item.get("use")));
    xml.append("<dcscor:parameter>").append(DcsRead.escape(parameter)).append("</dcscor:parameter>");
    Object value = item.get("value");
    if (value == null) {
      xml.append("<dcscor:value xsi:nil=\"true\"/>");
    } else {
      if (!(value instanceof List<?> values) || values.isEmpty()) {
        throw new IllegalArgumentException(
          "Поле value значения параметра оформления должно быть непустым массивом значений Value.");
      }
      for (int i = 0; i < values.size(); i++) {
        Object entry = values.get(i);
        String marker = carry == null ? null : carry.valueMarkerOrNull(parameter, i, entry);
        xml.append(marker != null ? marker : valueXml("dcscor:value", entry, false));
      }
    }
    return xml.append(settingsTailXml(item)).append("</dcscor:item>").toString();
  }

  /**
   * Перенос байтов неизменённых вложенных элементов при переписывании элемента условного
   * оформления: элемент запроса, равный прочитанному, становится маркером, а после раскладки
   * маркер заменяется исходными строками.
   */
  private static final class CarriedBytes {

    private final String text;
    private final List<int[]> spans = new ArrayList<>();
    private final List<Object> dtos = new ArrayList<>();
    private final boolean[] used;
    private final List<String> blocks;
    /** Имена параметров прочитанных значений оформления (секция {@code appearance}). */
    private final List<Object> parameters = new ArrayList<>();
    /** Строки элементов {@code value} каждого значения оформления; {@code null} - не построчно. */
    private final List<List<int[]>> valueSpans = new ArrayList<>();
    private final List<List<Object>> valueDtos = new ArrayList<>();

    /**
     * @param owner элемент условного оформления, чьи вложенные секции переносятся
     * @param section локальное имя вложенной секции: {@code filter} либо {@code appearance}
     * @param blocks общий список перенесённых блоков; номер маркера - позиция в нём
     */
    CarriedBytes(String text, XmlLines.Node owner, String section, String xmlns, List<String> blocks) {
      this.text = text;
      this.blocks = blocks;
      XmlLines.Node sectionEl = child(XmlLines.childrenAt(text, owner.start()), section);
      if (sectionEl != null) {
        for (XmlLines.Node item : named(XmlLines.childrenAt(text, sectionEl.start()), "item")) {
          spans.add(XmlLines.wholeLines(text, item.start(), item.end(), "item"));
          Map<String, Object> dto = DcsSettingsRead.sectionItemDto(
            text.substring(item.start(), item.end()), xmlns, section);
          dtos.add(normalizedItem(dto));
          if ("appearance".equals(section)) {
            readValues(item, dto.get("parameter"), xmlns);
          }
        }
      }
      used = new boolean[spans.size()];
    }

    /** Запоминает строки и DTO элементов {@code value} значения оформления для переноса по одному. */
    private void readValues(XmlLines.Node item, Object parameter, String xmlns) {
      List<int[]> lines = new ArrayList<>();
      List<Object> values = new ArrayList<>();
      for (XmlLines.Node value : named(XmlLines.childrenAt(text, item.start()), "value")) {
        int[] span;
        try {
          span = XmlLines.wholeLines(text, value.start(), value.end(), "value");
        } catch (IllegalStateException notOnOwnLines) {
          // значение делит строку с соседями: построчно его не перенести, пишет писатель
          span = null;
        }
        lines.add(span);
        values.add(normalized(DcsSettingsRead.settingsValue(text.substring(value.start(), value.end()), xmlns)));
      }
      parameters.add(parameter);
      valueSpans.add(lines);
      valueDtos.add(values);
    }

    /** Маркер переноса, если запрос глубоко равен ещё не перенесённому прочитанному элементу. */
    String markerOrNull(Map<String, Object> requested) {
      Object normalizedRequested = normalizedItem(requested);
      for (int i = 0; i < dtos.size(); i++) {
        if (used[i] || !dtos.get(i).equals(normalizedRequested)) {
          continue;
        }
        used[i] = true;
        blocks.add(text.substring(spans.get(i)[0], spans.get(i)[1]));
        return "<" + CARRIED_MARKER + (blocks.size() - 1) + "/>";
      }
      return null;
    }

    /**
     * Маркер переноса значения под номером {@code index}, если оно равно прочитанному у параметра
     * с тем же именем. Писатель пишет строку на одном языке конфигурации: без переноса правка
     * {@code use} теряла бы остальные языки. Значения только для чтения ({@code opaque},
     * {@code attrs}) не переносятся: их правку отвергает писатель.
     */
    String valueMarkerOrNull(String parameter, int index, Object requested) {
      if (!(requested instanceof Map<?, ?> map) || Boolean.TRUE.equals(map.get("opaque"))
        || map.get("attrs") != null) {
        return null;
      }
      Object normalizedRequested = normalized(requested);
      for (int i = 0; i < parameters.size(); i++) {
        if (used[i] || !parameter.equals(parameters.get(i))) {
          continue;
        }
        List<int[]> lines = valueSpans.get(i);
        if (index >= lines.size() || lines.get(index) == null
          || !valueDtos.get(i).get(index).equals(normalizedRequested)) {
          return null;
        }
        blocks.add(text.substring(lines.get(index)[0], lines.get(index)[1]));
        return "<" + CARRIED_MARKER + (blocks.size() - 1) + "/>";
      }
      return null;
    }
  }

  /** Заменяет строки маркеров переноса исходными строками перенесённых элементов. */
  private static String resolveCarried(String formatted, List<String> blocks, String eol) {
    String out = formatted;
    for (int i = 0; i < blocks.size(); i++) {
      String marker = "<" + CARRIED_MARKER + i + "/>";
      int at = out.indexOf(marker);
      if (at < 0) {
        throw new IllegalStateException("маркер переноса байтов потерян при раскладке элемента");
      }
      int lineStart = out.lastIndexOf('\n', at - 1) + 1;
      String block = blocks.get(i);
      String body = block.endsWith(eol) ? block.substring(0, block.length() - eol.length()) : block;
      out = out.substring(0, lineStart) + body + out.substring(at + marker.length());
    }
    return out;
  }

  // ------------------------------------------------------------------ писатель значений

  /**
   * Типизированное значение по белому списку записи: скаляры {@code xs:*}, поле и ссылка
   * конфигуратора {@code dcscor:Field}/{@code dcscor:DesignTimeValue}, локализованная строка,
   * стандартные период и дата начала, список значений, перечисления {@code dcsset:*}/{@code dcscor:*}
   * лексемами, цвет {@code v8ui:Color} текстом, тип диаграммы {@code v8ui:ChartType};
   * {@code {nil: true}} - {@code xsi:nil}. Прочие типы только для чтения: их правка - отказ.
   *
   * <p>Тем же списком пишется значение параметра схемы ({@link DcsValueTypeEdit}).
   *
   * @param qualified имя элемента значения с префиксом, например {@code dcsset:right}
   * @param raw значение Value из payload
   * @param scalarOnly в списке значений допустимы только простые скаляры
   */
  static String valueXml(String qualified, Object raw, boolean scalarOnly) {
    if (!(raw instanceof Map<?, ?> rawValue)) {
      throw new IllegalArgumentException("Значение " + qualified + " должно быть объектом Value.");
    }
    Map<String, Object> value = new LinkedHashMap<>();
    for (Map.Entry<?, ?> entry : rawValue.entrySet()) {
      String key = String.valueOf(entry.getKey());
      if (!VALUE_PROPS.contains(key)) {
        throw new IllegalArgumentException("Неизвестное свойство значения: " + key);
      }
      if (entry.getValue() != null) {
        value.put(key, entry.getValue());
      }
    }
    if (Boolean.TRUE.equals(value.get("opaque")) || value.containsKey("attrs")) {
      throw new IllegalArgumentException(
        "Значение типа " + value.get("type") + " доступно только для чтения.");
    }
    if (Boolean.TRUE.equals(value.get("nil"))) {
      return "<" + qualified + " xsi:nil=\"true\"/>";
    }
    if (!(value.get("type") instanceof String type) || type.isBlank()) {
      throw new IllegalArgumentException("У значения " + qualified + " не задан type (xsi:type файла).");
    }
    String body = switch (type) {
      case "xs:string" -> DcsRead.escape(valueText(value, type, true));
      case "xs:boolean" -> booleanText(value);
      case "xs:decimal" -> decimalText(value);
      case "xs:dateTime" -> dateTimeText(lexicalText(value), "text значения xs:dateTime");
      case "dcscor:Field", "dcscor:DesignTimeValue", "v8ui:Color" ->
        DcsRead.escape(valueText(value, type, false));
      case "v8ui:ChartType" -> chartTypeLexeme(value);
      case "v8:LocalStringType" -> {
        requireCompoundAllowed(scalarOnly, type);
        yield localStringBody(value);
      }
      case "v8:StandardPeriod" -> {
        requireCompoundAllowed(scalarOnly, type);
        yield periodBody(value);
      }
      case "v8:StandardBeginningDate" -> {
        requireCompoundAllowed(scalarOnly, type);
        yield beginningDateBody(value);
      }
      case "v8:ValueListType" -> {
        requireCompoundAllowed(scalarOnly, type);
        yield valueListBody(value);
      }
      default -> enumLexeme(value, type);
    };
    if (body.isEmpty()) {
      return "<" + qualified + " xsi:type=\"" + type + "\"/>";
    }
    return "<" + qualified + " xsi:type=\"" + type + "\">" + body + "</" + qualified + ">";
  }

  /** Составной тип в списке значений - отказ: там допустимы только простые скаляры. */
  private static void requireCompoundAllowed(boolean scalarOnly, String type) {
    if (scalarOnly) {
      throw new IllegalArgumentException(
        "В списке значений пишутся только простые скаляры, а не " + type + ".");
    }
  }

  /** Лексика значения: {@code text} файла либо строковый {@code decoded} от редактора значений. */
  private static String lexicalText(Map<?, ?> value) {
    if (value.get("text") instanceof String text) {
      return text;
    }
    if (value.get("decoded") instanceof String decoded) {
      return decoded;
    }
    return null;
  }

  /** Текст скалярного значения; у поля, ссылки и цвета пустой текст не имеет смысла. */
  private static String valueText(Map<String, Object> value, String type, boolean allowEmpty) {
    String text = lexicalText(value);
    if (text == null && allowEmpty) {
      return "";
    }
    if (text == null || (!allowEmpty && text.isBlank())) {
      throw new IllegalArgumentException("У значения типа " + type + " не задан text.");
    }
    return text;
  }

  /** Лексика xs:boolean: text дословно либо booleans из decoded. */
  private static String booleanText(Map<String, Object> value) {
    Object text = value.get("text");
    if (text instanceof String lexical && List.of("true", "false", "1", "0").contains(lexical)) {
      return lexical;
    }
    if (text == null && value.get("decoded") instanceof Boolean decoded) {
      return decoded.toString();
    }
    throw new IllegalArgumentException(
      "Значение xs:boolean пишется текстом true|false|1|0 либо {decoded: true|false}.");
  }

  /** Лексика xs:decimal: числовой text дословно либо число из decoded без хвостовых нулей. */
  private static String decimalText(Map<String, Object> value) {
    Object text = value.get("text");
    if (text instanceof String lexical) {
      try {
        new BigDecimal(lexical.trim());
      } catch (NumberFormatException e) {
        throw new IllegalArgumentException("Текст значения xs:decimal не является числом: " + lexical);
      }
      return lexical.trim();
    }
    if (text == null && value.get("decoded") instanceof Number decoded) {
      return new BigDecimal(decoded.toString()).stripTrailingZeros().toPlainString();
    }
    throw new IllegalArgumentException("Значение xs:decimal пишется числовым text либо {decoded: число}.");
  }

  /** Дата платформы вида {@code 0001-01-01T00:00:00}. */
  private static String dateTimeText(Object raw, String what) {
    if (!(raw instanceof String date) || !DATE_TIME_TEXT.matcher(date).matches()) {
      throw new IllegalArgumentException(
        "Значение " + what + " пишется датой вида 0001-01-01T00:00:00, а не " + raw + ".");
    }
    return date;
  }

  /** Лексема перечисления {@code dcsset:*}/{@code dcscor:*}; прочие типы только для чтения. */
  private static String enumLexeme(Map<String, Object> value, String type) {
    if (!type.startsWith("dcsset:") && !type.startsWith("dcscor:")) {
      throw new IllegalArgumentException("Значение типа " + type + " доступно только для чтения.");
    }
    Object raw = lexicalText(value);
    if (!(raw instanceof String lexical) || !LEXEME_TEXT.matcher(lexical).matches()) {
      throw new IllegalArgumentException(
        "Значение перечисления " + type + " пишется лексемой формата, а не " + raw + ".");
    }
    return lexical;
  }

  /** Лексема типа диаграммы только из перечисления XSD: незнакомую лексему перечитка не заметит. */
  private static String chartTypeLexeme(Map<String, Object> value) {
    String lexical = lexicalText(value);
    if (lexical == null || !CHART_TYPES.contains(lexical)) {
      throw new IllegalArgumentException("Тип диаграммы (v8ui:ChartType) пишется лексемой перечисления"
        + " формата (например Column, Pie3D), а не " + lexical + ".");
    }
    return lexical;
  }

  /**
   * Локализованная строка из {@code decoded} языком конфигурации.
   *
   * <p>Пустую строку платформа хранит элементом без {@code v8:item}, и чтение отдаёт её без
   * {@code decoded}. Такое значение и пустой {@code decoded} пишутся пустым телом, как у платформы.
   * Значение с ключами других типов ({@code text}, {@code period}, {@code list}) - отказ.
   */
  private static String localStringBody(Map<String, Object> value) {
    Object decoded = value.get("decoded");
    if ((decoded == null && LOCAL_STRING_FOREIGN_KEYS.stream().noneMatch(value::containsKey))
      || "".equals(decoded)) {
      return "";
    }
    if (!(decoded instanceof String text)) {
      throw new IllegalArgumentException(
        "Строка v8:LocalStringType пишется из decoded - текста на языке конфигурации.");
    }
    return "<v8:item><v8:lang>" + ConfigurationLanguage.current() + "</v8:lang><v8:content>"
      + DcsRead.escape(text) + "</v8:content></v8:item>";
  }

  /** Стандартный период: вариант с xsi:type и обе границы при {@code Custom}, как у платформы. */
  private static String periodBody(Map<String, Object> value) {
    Map<String, Object> period = payloadObject(
      value.get("period"), "period", List.of("variant", "startDate", "endDate"));
    Object rawVariant = period.get("variant");
    if (!(rawVariant instanceof String variant) || !STANDARD_PERIOD_VARIANTS.contains(variant)) {
      throw new IllegalArgumentException(
        "Вариант периода (period.variant) не из лексем v8:StandardPeriodVariant: " + rawVariant);
    }
    StringBuilder body = new StringBuilder("<v8:variant xsi:type=\"v8:StandardPeriodVariant\">")
      .append(variant).append("</v8:variant>");
    if ("Custom".equals(variant)) {
      // платформа пишет обе границы произвольного периода; незаданная - её датой по умолчанию
      body.append("<v8:startDate>").append(boundDate(period.get("startDate"), "period.startDate"))
        .append("</v8:startDate>")
        .append("<v8:endDate>").append(boundDate(period.get("endDate"), "period.endDate"))
        .append("</v8:endDate>");
    } else if (period.get("startDate") != null || period.get("endDate") != null) {
      throw new IllegalArgumentException("Границы периода пишутся только при variant=Custom.");
    }
    return body.toString();
  }

  /** Стандартная дата начала: вариант с xsi:type и дата при {@code Custom}. */
  private static String beginningDateBody(Map<String, Object> value) {
    Map<String, Object> beginningDate = payloadObject(
      value.get("beginningDate"), "beginningDate", List.of("variant", "date"));
    Object rawVariant = beginningDate.get("variant");
    if (!(rawVariant instanceof String variant)
      || !STANDARD_BEGINNING_DATE_VARIANTS.contains(variant)) {
      throw new IllegalArgumentException("Вариант даты начала (beginningDate.variant)"
        + " не из лексем v8:StandardBeginningDateVariant: " + rawVariant);
    }
    StringBuilder body = new StringBuilder("<v8:variant xsi:type=\"v8:StandardBeginningDateVariant\">")
      .append(variant).append("</v8:variant>");
    if ("Custom".equals(variant)) {
      body.append("<v8:date>").append(boundDate(beginningDate.get("date"), "beginningDate.date"))
        .append("</v8:date>");
    } else if (beginningDate.get("date") != null) {
      throw new IllegalArgumentException("Дата начала пишется только при variant=Custom.");
    }
    return body.toString();
  }

  /** Граница произвольного периода: дата платформы, по умолчанию {@code 0001-01-01T00:00:00}. */
  private static String boundDate(Object raw, String what) {
    return raw == null ? PERIOD_BOUND_DEFAULT : dateTimeText(raw, what);
  }

  /**
   * Список значений по образцу платформы: пустой {@code <v8:valueType/>}, {@code lastId} -1
   * с избыточным xsi:type и элементы со скалярным значением и необязательным представлением. У переписанного
   * списка {@code valueType} и {@code lastId} пишутся заново.
   */
  private static String valueListBody(Map<String, Object> value) {
    Map<String, Object> list = payloadObject(value.get("list"), "list", List.of("items"));
    StringBuilder body = new StringBuilder("<v8:valueType/>")
      .append("<v8:lastId xsi:type=\"xs:decimal\">-1</v8:lastId>");
    Object rawItems = list.get("items");
    if (rawItems == null) {
      return body.toString();
    }
    for (Map<String, Object> item : itemMaps(rawItems, "list.items")) {
      for (String key : item.keySet()) {
        if (!"value".equals(key) && !"presentation".equals(key)) {
          throw new IllegalArgumentException("Неизвестное свойство элемента списка значений: " + key);
        }
      }
      body.append("<v8:item>").append(valueXml("v8:value", item.get("value"), true));
      Object presentation = item.get("presentation");
      if (presentation != null) {
        if (!(presentation instanceof String text)) {
          throw new IllegalArgumentException("Представление элемента списка должно быть строкой.");
        }
        // в формате представление элемента списка - простая строка без xsi:type
        body.append("<v8:presentation>").append(DcsRead.escape(text)).append("</v8:presentation>");
      }
      body.append("</v8:item>");
    }
    return body.toString();
  }

  /** Объект payload по имени с белым списком свойств; значения-{@code null} не считаются. */
  private static Map<String, Object> payloadObject(Object raw, String field, List<String> allowed) {
    if (!(raw instanceof Map<?, ?> map)) {
      throw new IllegalArgumentException("обязательное поле не задано: " + field);
    }
    Map<String, Object> out = new LinkedHashMap<>();
    for (Map.Entry<?, ?> entry : map.entrySet()) {
      String key = String.valueOf(entry.getKey());
      if (!allowed.contains(key)) {
        throw new IllegalArgumentException("Неизвестное свойство " + field + ": " + key);
      }
      if (entry.getValue() != null) {
        out.put(key, entry.getValue());
      }
    }
    return out;
  }

  /** Последовательность детей вида узла по xs:sequence формата. */
  private static List<String> sequenceOf(NodeKind kind) {
    return switch (kind) {
      case SETTINGS -> SETTINGS_SEQUENCE;
      case GROUP -> GROUP_SEQUENCE;
      case TABLE -> TABLE_SEQUENCE;
      case CHART -> CHART_SEQUENCE;
      // вложенный отчёт отвергается раньше
      case NESTED_OBJECT -> throw new IllegalStateException("у вложенного отчёта нет коллекций структуры");
    };
  }

  /**
   * Вложенные группы узла пишутся без {@code xsi:type}: так у строк, колонок, точек, серий
   * и их вложенных групп ({@code item} без xsi:type).
   */
  private static boolean untypedStructure(String text, XmlLines.Node element) {
    return switch (element.name()) {
      case "row", "column", "point", "series" -> true;
      case "item" -> !XSI_TYPE.matcher(
        text.substring(element.start(), XmlLines.startTagEnd(text, element.start()) + 1)).find();
      default -> false;
    };
  }

  /**
   * Вставка узла в позицию коллекции: перед элементом, который займёт следующий номер,
   * после последнего - в конец, при пустой коллекции - на место по xs:sequence вида
   * родителя, а при пустом родителе - раскрытием его тега.
   */
  private static XmlGranularPatch.Replacement collectionInsertion(
    String text, XmlLines.Node parent, List<XmlLines.Node> children, List<XmlLines.Node> listed,
    List<String> sequence, String childName, int index, String compactXml) {
    String eol = XmlLines.newline(text);
    if (index < listed.size()) {
      XmlLines.Node at = listed.get(index);
      int[] span = XmlLines.wholeLines(text, at.start(), at.end(), childName);
      String block = XmlGranularPatch.formatInsertion(text, XmlLines.indentAt(text, at.start()), compactXml);
      return new XmlGranularPatch.Replacement(span[0], span[0], block + eol);
    }
    XmlLines.Node anchor = listed.isEmpty()
      ? insertionAnchor(children, sequence, childName)
      : listed.get(listed.size() - 1);
    if (anchor != null) {
      String block = XmlGranularPatch.formatInsertion(text, XmlLines.indentAt(text, anchor.start()), compactXml);
      int lineEnd = text.indexOf('\n', anchor.end());
      return lineEnd < 0
        ? new XmlGranularPatch.Replacement(anchor.end(), anchor.end(), eol + block)
        : new XmlGranularPatch.Replacement(lineEnd + 1, lineEnd + 1, block + eol);
    }
    if (!children.isEmpty()) {
      // предшественников нет, а дети есть: все они идут позже по xs:sequence
      XmlLines.Node successor = children.get(0);
      int[] span = XmlLines.wholeLines(text, successor.start(), successor.end(), successor.name());
      String block = XmlGranularPatch.formatInsertion(
        text, XmlLines.indentAt(text, successor.start()), compactXml);
      return new XmlGranularPatch.Replacement(span[0], span[0], block + eol);
    }
    return expandEmptyParent(text, parent, compactXml);
  }

  /** Первый узел пустого родителя: самозакрытый тег раскрывается, пустая пара получает содержимое. */
  private static XmlGranularPatch.Replacement expandEmptyParent(
    String text, XmlLines.Node parent, String compactXml) {
    String eol = XmlLines.newline(text);
    String indent = XmlLines.indentAt(text, parent.start());
    String block = XmlGranularPatch.formatInsertion(text, indent + "\t", compactXml);
    int gt = XmlLines.startTagEnd(text, parent.start());
    String qualified = qualifiedName(text, parent.start());
    if (text.charAt(gt - 1) == '/') {
      return new XmlGranularPatch.Replacement(
        gt - 1, parent.end(), ">" + eol + block + eol + indent + "</" + qualified + ">");
    }
    int close = text.lastIndexOf("</", parent.end() - 1);
    return new XmlGranularPatch.Replacement(gt + 1, close, eol + block + eol + indent);
  }

  /**
   * Обратное к {@link #expandEmptyParent}: узел без детей сворачивается в самозакрытый тег, как
   * платформа пишет пустые узлы настроек. Узел с комментарием или текстом между тегами не сворачивается.
   *
   * @param nodeStart смещение {@code <} открывающего тега узла в {@code text}
   */
  private static String collapsedIfEmpty(String text, int nodeStart) {
    int gt = XmlLines.startTagEnd(text, nodeStart);
    if (text.charAt(gt - 1) == '/' || !XmlLines.childrenAt(text, nodeStart).isEmpty()) {
      return text;
    }
    String close = "</" + qualifiedName(text, nodeStart) + ">";
    int at = gt + 1;
    while (at < text.length() && Character.isWhitespace(text.charAt(at))) {
      at++;
    }
    if (!text.startsWith(close, at)) {
      return text;
    }
    return text.substring(0, gt) + "/>" + text.substring(at + close.length());
  }

  /** Последний подэлемент, который по xs:sequence не позже вставляемого; после него и вставлять. */
  private static XmlLines.Node insertionAnchor(
    List<XmlLines.Node> children, List<String> sequence, String element) {
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

  /** Шаги пути из payload: массив объектов {@code {list, index}}. */
  private static List<Step> steps(Object raw, String field) {
    if (raw == null) {
      throw new IllegalArgumentException("обязательное поле не задано: " + field);
    }
    if (!(raw instanceof List<?> rawSteps)) {
      throw new IllegalArgumentException("Поле " + field + " должно быть массивом шагов {list, index}.");
    }
    List<Step> path = new ArrayList<>();
    for (Object rawStep : rawSteps) {
      if (!(rawStep instanceof Map<?, ?> step)) {
        throw new IllegalArgumentException("Шаг пути должен быть объектом {list, index}.");
      }
      if (!(step.get("list") instanceof String name) || name.isBlank()) {
        throw new IllegalArgumentException("В шаге пути не задан list.");
      }
      path.add(new Step(name, index(step.get("index"), "index")));
    }
    return path;
  }

  /** Целый номер из payload: JSON несёт числа дробными, нецелое значение - отказ. */
  private static int index(Object raw, String field) {
    if (!(raw instanceof Number number)) {
      throw new IllegalArgumentException("обязательное поле не задано: " + field + " (целое число)");
    }
    int value = number.intValue();
    if (number.doubleValue() != value) {
      throw new IllegalArgumentException("Поле " + field + " должно быть целым числом, а не " + raw + ".");
    }
    return value;
  }

  /** Позиция вставки: по умолчанию конец коллекции, вне {@code [0..размер]} - отказ. */
  private static int insertionIndex(Object raw, String list, int size) {
    if (raw == null) {
      return size;
    }
    int value = index(raw, "index");
    if (value < 0 || value > size) {
      throw new IllegalArgumentException(
        "Позиция вставки " + value + " вне границ списка " + list + ": элементов " + size + ".");
    }
    return value;
  }

  /** Отказ по свойству payload, которого операция не знает. */
  private static void knownKeys(Map<String, Object> payload, List<String> allowed) {
    if (payload == null) {
      throw new IllegalArgumentException("обязательное поле не задано: payloadJson");
    }
    for (String key : payload.keySet()) {
      if (!allowed.contains(key)) {
        throw new IllegalArgumentException("Неизвестное свойство операции структуры: " + key);
      }
    }
  }

  /** Прямые потомки с данным локальным именем. */
  private static List<XmlLines.Node> named(List<XmlLines.Node> children, String name) {
    List<XmlLines.Node> result = new ArrayList<>();
    for (XmlLines.Node node : children) {
      if (name.equals(node.name())) {
        result.add(node);
      }
    }
    return result;
  }

  // ------------------------------------------------------------------ помощники

  /** Вариант по имени среди прямых детей корня; нет - отказ. */
  private static XmlLines.Node variantAmong(List<XmlLines.Node> roots, String name) {
    XmlLines.Node found = variantOrNull(roots, name);
    if (found == null) {
      throw new IllegalArgumentException("Вариант настроек не найден: " + name);
    }
    return found;
  }

  /**
   * Вариант с тем же именем без учёта регистра, кроме {@code except}, либо {@code null}. Коллекция
   * вариантов схемы ищет по имени без учёта регистра, поэтому имена, различные только регистром,
   * недопустимы.
   */
  private static XmlLines.Node variantIgnoringCase(List<XmlLines.Node> roots, String name, XmlLines.Node except) {
    String key = name.toUpperCase(Locale.ROOT);
    for (XmlLines.Node node : roots) {
      String existing = "settingsVariant".equals(node.name()) && node != except ? node.leaves().get("name") : null;
      if (existing != null && existing.toUpperCase(Locale.ROOT).equals(key)) {
        return node;
      }
    }
    return null;
  }

  /** Вариант по имени либо {@code null}; дубль имени - отказ, а не молчаливый выбор первого. */
  private static XmlLines.Node variantOrNull(List<XmlLines.Node> roots, String name) {
    XmlLines.Node found = null;
    for (XmlLines.Node node : roots) {
      if (!"settingsVariant".equals(node.name()) || !name.equals(node.leaves().get("name"))) {
        continue;
      }
      if (found != null) {
        throw new IllegalArgumentException("Имя варианта настроек встречается дважды: " + name);
      }
      found = node;
    }
    return found;
  }

  /**
   * Правка представления варианта: замена существующего элемента целиком, вставка
   * после имени по xs:sequence либо снятие целыми строками при пустом значении.
   */
  private static Optional<XmlGranularPatch.Replacement> presentationReplacement(
    String text, List<XmlLines.Node> children, Object value) {
    if (value != null && !(value instanceof String)) {
      throw new IllegalArgumentException("Значение presentation должно быть строкой.");
    }
    String presentation = value == null ? "" : (String) value;
    XmlLines.Node current = child(children, "presentation");
    // Эхо не переписывает элемент: форма xs:string и строки на других языках сохраняются
    if (current != null && !presentation.isEmpty() && DcsEdit.sameText(DcsSettingsRead.localizedText(
      text.substring(current.start(), current.end()), rootNamespaces(text)), presentation)) {
      return Optional.empty();
    }
    if (presentation.isBlank()) {
      if (current == null) {
        return Optional.empty();
      }
      int[] span = XmlLines.wholeLines(text, current.start(), current.end(), "presentation");
      return Optional.of(new XmlGranularPatch.Replacement(span[0], span[1], ""));
    }
    String compact = "<dcsset:presentation xsi:type=\"v8:LocalStringType\"><v8:item><v8:lang>"
      + ConfigurationLanguage.current() + "</v8:lang><v8:content>" + DcsRead.escape(presentation)
      + "</v8:content></v8:item></dcsset:presentation>";
    if (current != null) {
      return Optional.of(new XmlGranularPatch.Replacement(current.start(), current.end(),
        XmlGranularPatch.formatReplacementPreservingIndent(text, current.start(), compact)));
    }
    XmlLines.Node anchor = child(children, "name");
    String eol = XmlLines.newline(text);
    String block = XmlGranularPatch.formatInsertion(text, XmlLines.indentAt(text, anchor.start()), compact);
    int lineEnd = text.indexOf('\n', anchor.end());
    if (lineEnd < 0) {
      return Optional.of(new XmlGranularPatch.Replacement(anchor.end(), anchor.end(), eol + block));
    }
    return Optional.of(new XmlGranularPatch.Replacement(lineEnd + 1, lineEnd + 1, block + eol));
  }

  /** Замена первого подэлемента с данным именем целиком, с его префиксом из файла. */
  private static XmlGranularPatch.Replacement childReplacement(
    String text, List<XmlLines.Node> children, String element, String value) {
    XmlLines.Node child = child(children, element);
    String qualified = qualifiedName(text, child.start());
    return new XmlGranularPatch.Replacement(child.start(), child.end(), "<" + qualified + ">"
      + XmlGranularPatch.lineBreaks(DcsRead.escape(value), XmlLines.newline(text)) + "</" + qualified + ">");
  }

  /** Имя открывающего тега с префиксом, как оно записано в файле. */
  private static String qualifiedName(String text, int lt) {
    int i = lt + 1;
    while (i < text.length() && " \t\r\n/>".indexOf(text.charAt(i)) < 0) {
      i++;
    }
    return text.substring(lt + 1, i);
  }

  /** Первый подэлемент с данным локальным именем либо {@code null}. */
  private static XmlLines.Node child(List<XmlLines.Node> children, String element) {
    for (XmlLines.Node node : children) {
      if (element.equals(node.name())) {
        return node;
      }
    }
    return null;
  }

  /** Представление из payload: строка, а пустое значение подменяется именем варианта. */
  private static String presentationText(Object value, String fallback) {
    if (value == null) {
      return fallback;
    }
    if (!(value instanceof String presentation)) {
      throw new IllegalArgumentException("Значение presentation должно быть строкой.");
    }
    return presentation.isBlank() ? fallback : presentation;
  }

  /** Обязательный строковый параметр операции. */
  private static String required(String value, String field) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("обязательное поле не задано: " + field);
    }
    return value;
  }
}
