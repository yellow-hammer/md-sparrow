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

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.yellowhammer.designerxml.DesignerXml;
import io.github.yellowhammer.designerxml.SchemaVersion;
import io.github.yellowhammer.designerxml.reflect.JaxbReflect;
import io.github.yellowhammer.edt.EdtLayout;
import io.github.yellowhammer.edt.EdtModel;
import io.github.yellowhammer.edt.EdtObjectProperties;
import io.github.yellowhammer.edt.EdtObjectStructure;

import jakarta.xml.bind.JAXBException;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Таблицы языка запросов объекта метаданных ({@code cf-md-query-tables}): основная таблица,
 * табличные части, стандартные табличные части и виртуальные таблицы регистров — с полями,
 * их типами и параметрами виртуальных таблиц по позициям. Критерий отбора даёт таблицу с
 * обязательным параметром-значением, последовательность — основную таблицу и её границы.
 *
 * <p>Состав стандартных реквизитов выводится из вида и свойств объекта, а не из XML: файл
 * перечисляет стандартные реквизиты с их настройками, даже когда у объекта их нет (у
 * неиерархического справочника записаны {@code Parent} и {@code IsFolder}), и не перечисляет
 * те, что платформа добавляет всегда ({@code ВерсияДанных}, {@code Представление}). Имена
 * языка запросов — словарь {@code query-names.json}.
 *
 * <p>Тип стандартного реквизита задаётся там, где правило очевидно (ссылка на сам объект,
 * код и номер по длине и виду, владелец по списку владельцев, булевы признаки, даты); где
 * тип зависит от других объектов или от перечислений платформы ({@code Регистратор},
 * {@code ВидДвижения}, {@code МоментВремени}), отдаётся {@code null}: лучше честно не знать.
 * У последовательности {@code Регистратор} — ссылки на её документы: их список задан в ней самой.
 *
 * <p>Общие реквизиты, применённые к объекту ({@link CommonAttributeUsage}), — поля его основной
 * таблицы (вид {@code separator} у разделителя, {@code commonAttribute} у прочих), а у виртуальных
 * таблиц итогов регистров накопления и бухгалтерии — только разделители. Файл вне выгрузки полей
 * общих реквизитов не даёт: их взять неоткуда.
 *
 * <p>Определяемые типы и характеристики в типах полей остаются наборами, как их записывает
 * конфигуратор; их составы читаются из выгрузки ({@link TypeCompositions}) и отдаются отдельным
 * ключом {@code typeCompositions}.
 */
public final class MdQueryTables {

  private static final String RESOURCE = "query-names.json";

  private static final Names NAMES = Names.load();

  /** Поля периода виртуальной таблицы в порядке периодичности: от записи к году. */
  private static final List<String> PERIOD_FIELDS = List.of(
    "Period", "PeriodSecond", "PeriodMinute", "PeriodHour", "PeriodDay", "PeriodWeek",
    "PeriodTenDays", "PeriodMonth", "PeriodQuarter", "PeriodHalfYear", "PeriodYear");

  /** Ссылочные виды, у которых ссылка пишется {@code cfg:<Вид>Ref.<Имя>}. */
  private static final List<String> REF_KINDS = List.of(
    "Catalog", "Document", "Enum", "ChartOfCharacteristicTypes", "ChartOfAccounts",
    "ChartOfCalculationTypes", "ExchangePlan", "BusinessProcess", "Task");

  private MdQueryTables() {
  }

  /**
   * Таблицы объекта.
   *
   * @param objectFile файл объекта: {@code <Вид>/<Имя>.xml} выгрузки или {@code <Вид>/<Имя>/<Имя>.mdo} EDT
   * @param version формат выгрузки; у проекта EDT не нужен
   * @param lang {@code ru} (по умолчанию) или {@code en} — язык имён таблиц, полей и параметров
   * @return {@code {object, kind, tables:[{name, kind, synonym, params, fields}], typeCompositions?, notes?}};
   *     {@code typeCompositions} — только если в полях встретились определяемые типы или
   *     характеристики и их описания найдены в выгрузке
   * @throws IllegalArgumentException вид объекта в запросах не описан или язык неизвестен
   */
  public static Map<String, Object> read(Path objectFile, SchemaVersion version, String lang)
    throws IOException, JAXBException {
    boolean en = english(lang);
    if (!Files.isRegularFile(objectFile)) {
      throw new IllegalArgumentException("Файл объекта не найден: " + objectFile.toAbsolutePath());
    }
    boolean edt = EdtLayout.isObjectFile(objectFile);
    MdObjectPropertiesDto props = edt
      ? EdtObjectProperties.readDto(objectFile, EdtModel.bundled())
      : MdObjectPropertiesEdit.readDto(objectFile, version);
    String tag = tagOf(props.kind);
    if (!NAMES.objects.containsKey(tag)) {
      throw new IllegalArgumentException("Таблиц языка запросов у объекта вида «" + props.kind
        + "» нет или они ещё не описаны.");
    }
    // Структура нужна ради синонимов стандартных реквизитов: свойства их не несут
    MdObjectStructureDto structure = edt
      ? EdtObjectStructure.read(objectFile, EdtModel.bundled())
      : MdObjectStructureRead.read(objectFile, version);
    Builder b = new Builder(objectFile, edt, version, en, tag, props, structure);
    switch (tag) {
      case "Catalog" -> b.catalog();
      case "Document" -> b.document();
      case "DocumentJournal" -> b.documentJournal();
      case "Enum" -> b.enumeration();
      case "ChartOfCharacteristicTypes" -> b.chartOfCharacteristicTypes();
      case "ChartOfAccounts" -> b.chartOfAccounts();
      case "ChartOfCalculationTypes" -> b.chartOfCalculationTypes();
      case "ExchangePlan" -> b.exchangePlan();
      case "BusinessProcess" -> b.businessProcess();
      case "Task" -> b.task();
      case "InformationRegister" -> b.informationRegister();
      case "AccumulationRegister" -> b.accumulationRegister();
      case "AccountingRegister" -> b.accountingRegister();
      case "CalculationRegister" -> b.calculationRegister();
      case "Constant" -> b.constant();
      case "FilterCriterion" -> b.filterCriterion();
      case "Sequence" -> b.sequence();
      default -> throw new IllegalStateException("вид без описания таблиц: " + tag);
    }
    List<Map<String, Object>> compositions = typeCompositions(objectFile, b.tables);
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("object", b.objectName());
    out.put("kind", props.kind);
    out.put("tables", b.tables);
    if (!compositions.isEmpty()) {
      out.put("typeCompositions", compositions);
    }
    if (!b.notes.isEmpty()) {
      out.put("notes", b.notes);
    }
    return out;
  }

  /**
   * Составы наборов типов, встреченных в полях всех таблиц ответа: определяемых типов
   * ({@code cfg:DefinedType.X}) и характеристик ({@code cfg:Characteristic.X}).
   *
   * <p>Состав отдаётся одной записью на набор отдельным ключом, а не внутри каждого {@code type}: у
   * определяемого типа из двух десятков ссылок, стоящего в десятках полей (и в их копиях в
   * виртуальных таблицах), ответ вырос бы в разы.
   *
   * <p>Типизированный параметр таблицы (значение критерия отбора) обходится так же, как поле, и раньше
   * полей своей таблицы — в порядке ключей ответа: значение отбора тоже сравнивают со ссылками состава.
   *
   * @return записи {@code {source, types, typeSets}} в порядке первого вхождения; пусто — ни одного
   *     набора нет, файл вне выгрузки или описания наборов не найдены
   */
  @SuppressWarnings("unchecked")
  private static List<Map<String, Object>> typeCompositions(Path objectFile, List<Map<String, Object>> tables) {
    TypeCompositions compositions = null;
    for (Map<String, Object> table : tables) {
      List<Map<String, Object>> typed = new ArrayList<>((List<Map<String, Object>>) table.get("params"));
      typed.addAll((List<Map<String, Object>>) table.get("fields"));
      for (Map<String, Object> field : typed) {
        if (!(field.get("type") instanceof Map<?, ?> type) || !(type.get("typeSets") instanceof List<?> typeSets)) {
          continue;
        }
        for (Object typeSet : typeSets) {
          if (typeSet instanceof String name && (name.startsWith(TypeCompositions.DEFINED_TYPE_PREFIX)
            || name.startsWith(TypeCompositions.CHARACTERISTIC_PREFIX))) {
            // Выгрузку ищем только при первом наборе: у большинства объектов наборов в полях нет
            if (compositions == null) {
              compositions = TypeCompositions.of(objectFile);
            }
            // Запись копится внутри класса; ненайденный состав её не даёт и ответ не ломает
            compositions.ofTypeSet(name);
          }
        }
      }
    }
    return compositions == null ? List.of() : compositions.collected();
  }

  private static boolean english(String lang) {
    if (lang == null || lang.isBlank() || "ru".equalsIgnoreCase(lang.trim())) {
      return false;
    }
    if ("en".equalsIgnoreCase(lang.trim())) {
      return true;
    }
    throw new IllegalArgumentException("lang задаётся как ru или en, а не «" + lang + "»");
  }

  /** Вид в написании формата выгрузки: в DTO он со строчной ({@code catalog}). */
  private static String tagOf(String kind) {
    if (kind == null || kind.isEmpty()) {
      return "";
    }
    return Character.toUpperCase(kind.charAt(0)) + kind.substring(1);
  }

  /** Сборщик таблиц одного объекта: имена на выбранном языке, синонимы на языке конфигурации. */
  private static final class Builder {

    private final Path objectFile;
    private final boolean edt;
    private final SchemaVersion version;
    private final boolean english;
    private final String tag;
    private final MdObjectPropertiesDto props;
    private final MdObjectStructureDto structure;
    private final List<Map<String, Object>> tables = new ArrayList<>();
    private final List<String> notes = new ArrayList<>();
    /** Общие реквизиты объекта; {@code null} — ещё не читались. */
    private List<CommonAttributeUsage.Applied> common;

    Builder(Path objectFile, boolean edt, SchemaVersion version, boolean english, String tag,
      MdObjectPropertiesDto props, MdObjectStructureDto structure) {
      this.objectFile = objectFile;
      this.edt = edt;
      this.version = version;
      this.english = english;
      this.tag = tag;
      this.props = props;
      this.structure = structure;
    }

    String objectName() {
      return (english ? tag : NAMES.objects.get(tag)) + "." + props.internalName;
    }

    // --- виды объектов ---------------------------------------------------------------------

    void catalog() {
      MdCatalogPropertiesDto c = props.catalog;
      List<Map<String, Object>> fields = new ArrayList<>();
      fields.add(std("Ref", self()));
      fields.add(std("DataVersion", Types.string()));
      fields.add(std("DeletionMark", Types.bool()));
      fields.add(std("Predefined", Types.bool()));
      fields.add(std("PredefinedDataName", Types.string()));
      boolean hierarchical = c != null && c.hierarchical;
      if (c != null && positive(c.codeLength)) {
        fields.add(std("Code", Types.code(c.codeType, c.codeLength, c.codeAllowedLength)));
      }
      if (c != null && positive(c.descriptionLength)) {
        fields.add(std("Description", Types.string(c.descriptionLength, "VARIABLE")));
      }
      if (hierarchical) {
        fields.add(std("Parent", self()));
        // Группы есть только у иерархии групп и элементов: у иерархии элементов каждый узел — элемент
        if (!"HIERARCHY_OF_ITEMS".equals(c.hierarchyType)) {
          fields.add(std("IsFolder", Types.bool()));
        }
      }
      if (c != null && c.owners != null && !c.owners.isEmpty()) {
        fields.add(std("Owner", Types.refs(c.owners)));
      }
      attributes(fields, props.attributes, "attribute");
      commonAttributes(fields);
      fields.add(std("Presentation", Types.string()));
      main(fields);
      tabularSections();
    }

    void document() {
      MdDocumentPropertiesDto d = props.document;
      List<Map<String, Object>> fields = new ArrayList<>();
      fields.add(std("Ref", self()));
      fields.add(std("DataVersion", Types.string()));
      fields.add(std("DeletionMark", Types.bool()));
      if (d != null && positive(d.numberLength)) {
        fields.add(std("Number", Types.code(d.numberType, d.numberLength, d.numberAllowedLength)));
      }
      fields.add(std("Date", Types.dateTime()));
      // Документ с запретом проведения признака «Проведен» не несёт
      if (d == null || !"DENY".equals(d.posting)) {
        fields.add(std("Posted", Types.bool()));
      }
      fields.add(std("PointInTime", null));
      attributes(fields, props.attributes, "attribute");
      commonAttributes(fields);
      fields.add(std("Presentation", Types.string()));
      main(fields);
      tabularSections();
    }

    void documentJournal() {
      MdDocumentJournalPropertiesDto j = props.documentJournal;
      List<Map<String, Object>> fields = new ArrayList<>();
      List<String> documents = j == null || j.registeredDocuments == null ? List.of() : j.registeredDocuments;
      fields.add(std("Ref", documents.isEmpty() ? null : Types.refs(documents)));
      fields.add(std("Type", null));
      fields.add(std("Date", Types.dateTime()));
      fields.add(std("Number", null));
      fields.add(std("Posted", Types.bool()));
      fields.add(std("DeletionMark", Types.bool()));
      fields.add(std("PointInTime", null));
      attributes(fields, props.columns, "column");
      fields.add(std("Presentation", Types.string()));
      main(fields);
    }

    void enumeration() {
      List<Map<String, Object>> fields = new ArrayList<>();
      fields.add(std("Ref", self()));
      fields.add(std("Order", Types.decimal()));
      fields.add(std("Presentation", Types.string()));
      main(fields);
    }

    void chartOfCharacteristicTypes() {
      MdChartOfCharacteristicTypesPropertiesDto c = props.chartOfCharacteristicTypes;
      List<Map<String, Object>> fields = new ArrayList<>();
      fields.add(std("Ref", self()));
      fields.add(std("DataVersion", Types.string()));
      fields.add(std("DeletionMark", Types.bool()));
      fields.add(std("Predefined", Types.bool()));
      fields.add(std("PredefinedDataName", Types.string()));
      if (c != null && positive(c.codeLength)) {
        fields.add(std("Code", Types.string(c.codeLength, c.codeAllowedLength)));
      }
      if (c != null && positive(c.descriptionLength)) {
        fields.add(std("Description", Types.string(c.descriptionLength, "VARIABLE")));
      }
      // Иерархия плана видов характеристик — только группы: родитель и признак группы идут вместе
      if (c != null && c.hierarchical) {
        fields.add(std("Parent", self()));
        fields.add(std("IsFolder", Types.bool()));
      }
      fields.add(std("ValueType", null));
      attributes(fields, props.attributes, "attribute");
      commonAttributes(fields);
      fields.add(std("Presentation", Types.string()));
      main(fields);
      tabularSections();
    }

    void chartOfAccounts() {
      MdChartOfAccountsPropertiesDto c = props.chartOfAccounts;
      List<Map<String, Object>> fields = new ArrayList<>();
      fields.add(std("Ref", self()));
      fields.add(std("DataVersion", Types.string()));
      fields.add(std("DeletionMark", Types.bool()));
      fields.add(std("Predefined", Types.bool()));
      fields.add(std("PredefinedDataName", Types.string()));
      // Код плана счетов задаётся маской: это всегда строка
      if (c != null && positive(c.codeLength)) {
        fields.add(std("Code", Types.string(c.codeLength, "VARIABLE")));
      }
      if (c != null && positive(c.descriptionLength)) {
        fields.add(std("Description", Types.string(c.descriptionLength, "VARIABLE")));
      }
      fields.add(std("Parent", self()));
      fields.add(std("Order", Types.string()));
      fields.add(std("Type", null));
      fields.add(std("OffBalance", Types.bool()));
      attributes(fields, props.accountingFlags, "accountingFlag");
      attributes(fields, props.attributes, "attribute");
      commonAttributes(fields);
      fields.add(std("Presentation", Types.string()));
      main(fields);
      tabularSections();
      String characteristics = c == null ? "" : referenceName(c.extDimensionTypes);
      if (c != null && positive(c.maxExtDimensionCount)) {
        List<Map<String, Object>> section = new ArrayList<>();
        section.add(sectionStd("ExtDimensionTypes", "Ref", self()));
        section.add(sectionStd("ExtDimensionTypes", "LineNumber", Types.decimal()));
        section.add(sectionStd("ExtDimensionTypes", "ExtDimensionType", characteristics.isEmpty()
          ? null : Types.ref("ChartOfCharacteristicTypes", characteristics)));
        section.add(sectionStd("ExtDimensionTypes", "Predefined", Types.bool()));
        section.add(sectionStd("ExtDimensionTypes", "TurnoversOnly", Types.bool()));
        attributes(section, props.extDimensionAccountingFlags, "extDimensionAccountingFlag");
        standardTabularSection("ExtDimensionTypes", section);
      }
    }

    void chartOfCalculationTypes() {
      MdChartOfCalculationTypesPropertiesDto c = props.chartOfCalculationTypes;
      List<Map<String, Object>> fields = new ArrayList<>();
      fields.add(std("Ref", self()));
      fields.add(std("DataVersion", Types.string()));
      fields.add(std("DeletionMark", Types.bool()));
      fields.add(std("Predefined", Types.bool()));
      fields.add(std("PredefinedDataName", Types.string()));
      if (c != null && positive(c.codeLength)) {
        fields.add(std("Code", Types.code(c.codeType, c.codeLength, c.codeAllowedLength)));
      }
      if (c != null && positive(c.descriptionLength)) {
        fields.add(std("Description", Types.string(c.descriptionLength, "VARIABLE")));
      }
      boolean actionPeriod = c != null && c.actionPeriodUse;
      if (actionPeriod) {
        fields.add(std("ActionPeriodIsBasic", Types.bool()));
      }
      attributes(fields, props.attributes, "attribute");
      commonAttributes(fields);
      fields.add(std("Presentation", Types.string()));
      main(fields);
      tabularSections();
      if (c != null && c.dependenceOnCalculationTypes != null && !"DONT_USE".equals(c.dependenceOnCalculationTypes)) {
        List<String> base = c.baseCalculationTypes == null ? List.of() : c.baseCalculationTypes;
        calculationTypesSection("BaseCalculationTypes", base.isEmpty() ? self() : Types.refs(base));
      }
      calculationTypesSection("LeadingCalculationTypes", self());
      if (actionPeriod) {
        calculationTypesSection("DisplacingCalculationTypes", self());
      }
    }

    void exchangePlan() {
      MdExchangePlanPropertiesDto x = props.exchangePlan;
      List<Map<String, Object>> fields = new ArrayList<>();
      fields.add(std("Ref", self()));
      fields.add(std("DataVersion", Types.string()));
      fields.add(std("DeletionMark", Types.bool()));
      if (x != null && positive(x.codeLength)) {
        fields.add(std("Code", Types.string(x.codeLength, x.codeAllowedLength)));
      }
      if (x != null && positive(x.descriptionLength)) {
        fields.add(std("Description", Types.string(x.descriptionLength, "VARIABLE")));
      }
      fields.add(std("SentNo", Types.decimal()));
      fields.add(std("ReceivedNo", Types.decimal()));
      fields.add(std("ThisNode", Types.bool()));
      attributes(fields, props.attributes, "attribute");
      commonAttributes(fields);
      fields.add(std("Presentation", Types.string()));
      main(fields);
      tabularSections();
    }

    void businessProcess() {
      MdBusinessProcessPropertiesDto p = props.businessProcess;
      List<Map<String, Object>> fields = new ArrayList<>();
      fields.add(std("Ref", self()));
      fields.add(std("DataVersion", Types.string()));
      fields.add(std("DeletionMark", Types.bool()));
      if (p != null && positive(p.numberLength)) {
        fields.add(std("Number", Types.code(p.numberType, p.numberLength, p.numberAllowedLength)));
      }
      fields.add(std("Date", Types.dateTime()));
      // Ведущей задачей бывает задача любого бизнес-процесса: тип по одному объекту не вывести
      fields.add(std("HeadTask", null));
      fields.add(std("Started", Types.bool()));
      fields.add(std("Completed", Types.bool()));
      attributes(fields, props.attributes, "attribute");
      commonAttributes(fields);
      fields.add(std("Presentation", Types.string()));
      main(fields);
      tabularSections();
    }

    void task() {
      MdTaskPropertiesDto t = props.task;
      List<Map<String, Object>> fields = new ArrayList<>();
      fields.add(std("Ref", self()));
      fields.add(std("DataVersion", Types.string()));
      fields.add(std("DeletionMark", Types.bool()));
      if (t != null && positive(t.numberLength)) {
        fields.add(std("Number", Types.code(t.numberType, t.numberLength, t.numberAllowedLength)));
      }
      fields.add(std("Date", Types.dateTime()));
      if (t != null && positive(t.descriptionLength)) {
        fields.add(std("Description", Types.string(t.descriptionLength, "VARIABLE")));
      }
      // Бизнес-процесс задачи и его точка маршрута известны только по бизнес-процессам конфигурации
      fields.add(std("BusinessProcess", null));
      fields.add(std("RoutePoint", null));
      fields.add(std("Executed", Types.bool()));
      attributes(fields, props.addressingAttributes, "addressingAttribute");
      attributes(fields, props.attributes, "attribute");
      commonAttributes(fields);
      fields.add(std("Presentation", Types.string()));
      main(fields);
      tabularSections();
      if (t != null && t.addressing != null && !t.addressing.isBlank()) {
        virtual("TaskByPerformer", List.of(param("Performer", "performer"), param("Condition", "condition")),
          copy(fields));
      }
    }

    void informationRegister() {
      MdRegisterPropertiesDto r = props.register;
      boolean periodic = r != null && r.informationRegisterPeriodicity != null
        && !r.informationRegisterPeriodicity.isBlank() && !"NONPERIODICAL".equals(r.informationRegisterPeriodicity);
      boolean subordinate = r != null && "RECORDER_SUBORDINATE".equals(r.writeMode);
      List<Map<String, Object>> fields = new ArrayList<>();
      if (periodic) {
        fields.add(std("Period", Types.dateTime()));
      }
      if (subordinate) {
        fields.add(std("Recorder", null));
        fields.add(std("LineNumber", Types.decimal()));
        fields.add(std("Active", Types.bool()));
      }
      attributes(fields, props.dimensions, "dimension");
      attributes(fields, props.resources, "resource");
      attributes(fields, props.attributes, "attribute");
      commonAttributes(fields);
      main(fields);
      if (periodic) {
        List<Map<String, Object>> params = List.of(param("Period", "period"), param("Condition", "condition"));
        virtual("SliceLast", params, copy(fields));
        virtual("SliceFirst", params, copy(fields));
      }
    }

    void accumulationRegister() {
      MdRegisterPropertiesDto r = props.register;
      boolean balance = r == null || !"TURNOVERS".equals(r.registerType);
      List<Map<String, Object>> fields = new ArrayList<>();
      fields.add(std("Period", Types.dateTime()));
      fields.add(std("Recorder", null));
      fields.add(std("LineNumber", Types.decimal()));
      fields.add(std("Active", Types.bool()));
      if (balance) {
        fields.add(std("RecordType", null));
      }
      fields.add(std("PointInTime", null));
      attributes(fields, props.dimensions, "dimension");
      attributes(fields, props.resources, "resource");
      attributes(fields, props.attributes, "attribute");
      commonAttributes(fields);
      main(fields);

      if (balance) {
        List<Map<String, Object>> rest = new ArrayList<>();
        attributes(rest, props.dimensions, "dimension");
        separators(rest);
        derived(rest, props.resources, null, "Balance");
        virtual("Balance", List.of(param("Period", "period"), param("Condition", "condition")), rest);
      }
      List<Map<String, Object>> turnovers = new ArrayList<>();
      attributes(turnovers, props.dimensions, "dimension");
      separators(turnovers);
      if (balance) {
        derived(turnovers, props.resources, null, "Turnover", "Receipt", "Expense");
      } else {
        derived(turnovers, props.resources, null, "Turnover");
      }
      recorderAndPeriods(turnovers, true, false);
      virtual("Turnovers", List.of(param("BeginOfPeriod", "periodStart"), param("EndOfPeriod", "periodEnd"),
        periodicity(false), param("Condition", "condition")), turnovers);
      if (balance) {
        List<Map<String, Object>> both = new ArrayList<>();
        attributes(both, props.dimensions, "dimension");
        separators(both);
        derived(both, props.resources, null, "OpeningBalance", "Turnover", "Receipt", "Expense", "ClosingBalance");
        recorderAndPeriods(both, true, false);
        virtual("BalanceAndTurnovers", List.of(param("BeginOfPeriod", "periodStart"),
          param("EndOfPeriod", "periodEnd"), periodicity(false), complementMethod(), param("Condition", "condition")),
          both);
      }
    }

    void accountingRegister() throws IOException, JAXBException {
      ObjectFacts facts = ObjectFacts.read(objectFile, edt, tag);
      boolean correspondence = "true".equals(facts.props().get("correspondence"));
      String chart = referenceName(facts.props().get("chartOfAccounts"));
      int extDimensions = 0;
      String characteristics = "";
      if (chart.isEmpty()) {
        notes.add("У регистра не задан план счетов: поля счёта и субконто без типа.");
      } else {
        Optional<MdObjectPropertiesDto> chartDto = sibling("ChartOfAccounts", chart);
        if (chartDto.isPresent() && chartDto.get().chartOfAccounts != null) {
          extDimensions = integer(chartDto.get().chartOfAccounts.maxExtDimensionCount);
          characteristics = referenceName(chartDto.get().chartOfAccounts.extDimensionTypes);
        } else {
          // Без плана счетов неизвестно число субконто: перечислять их наугад нельзя
          notes.add("План счетов " + chart + " не найден рядом с регистром: субконто не перечислены.");
        }
      }
      Map<String, Object> accountType = chart.isEmpty() ? null : Types.ref("ChartOfAccounts", chart);
      Map<String, Object> valueType = characteristics.isEmpty() ? null : Types.characteristic(characteristics);
      Map<String, Object> kindType = characteristics.isEmpty()
        ? null : Types.ref("ChartOfCharacteristicTypes", characteristics);
      // Уточнение периода (8.3.9) включает свойство «Длина уточнения периода»: при 0 его нет
      boolean adjustment = integer(facts.props().get("periodAdjustmentLength")) > 0;

      List<Map<String, Object>> fields = new ArrayList<>();
      fields.add(std("Period", Types.dateTime()));
      if (adjustment) {
        fields.add(std("PeriodAdjustment", Types.decimal()));
      }
      fields.add(std("Recorder", null));
      fields.add(std("LineNumber", Types.decimal()));
      fields.add(std("Active", Types.bool()));
      fields.add(std("PointInTime", null));
      if (correspondence) {
        fields.add(std("AccountDr", accountType));
        fields.add(std("AccountCr", accountType));
        numbered(fields, "ExtDimensionDr", extDimensions, valueType);
        numbered(fields, "ExtDimensionCr", extDimensions, valueType);
        numbered(fields, "ExtDimensionTypeDr", extDimensions, kindType);
        numbered(fields, "ExtDimensionTypeCr", extDimensions, kindType);
      } else {
        fields.add(std("Account", accountType));
        numbered(fields, "ExtDimension", extDimensions, valueType);
        numbered(fields, "ExtDimensionType", extDimensions, kindType);
        fields.add(std("RecordType", null));
      }
      // Небалансовое измерение и ресурс у регистра с корреспонденцией хранятся по дебету и кредиту
      balanceSplit(fields, props.dimensions, "dimension", correspondence, facts);
      balanceSplit(fields, props.resources, "resource", correspondence, facts);
      attributes(fields, props.attributes, "attribute");
      commonAttributes(fields);
      main(fields);

      List<Map<String, Object>> balance = new ArrayList<>();
      balance.add(std("Account", accountType));
      numbered(balance, "ExtDimension", extDimensions, valueType);
      balanceFlagged(balance, props.dimensions, "dimension", facts);
      separators(balance);
      derived(balance, props.resources, facts, "Balance", "BalanceDr", "BalanceCr", "SplittedBalanceDr", "SplittedBalanceCr");
      virtual("Balance", List.of(param("Period", "period"), param("AccountCondition", "account"),
        param("ExtDimensions", "extDimensions"), param("Condition", "condition")), balance);

      List<Map<String, Object>> turnovers = new ArrayList<>();
      turnovers.add(std("Account", accountType));
      numbered(turnovers, "ExtDimension", extDimensions, valueType);
      if (correspondence) {
        turnovers.add(std("BalancedAccount", accountType));
        numbered(turnovers, "BalancedExtDimension", extDimensions, valueType);
      }
      turnoverDimensions(turnovers, correspondence, facts);
      separators(turnovers);
      for (MdNamedPropertyDto resource : list(props.resources)) {
        derivedOne(turnovers, resource, facts, "Turnover", "TurnoverDr", "TurnoverCr");
        // Корреспондирующий оборот бывает только у небалансового ресурса
        if (correspondence && !facts.balance("resource", resource.name)) {
          derivedOne(turnovers, resource, facts, "BalancedTurnover", "BalancedTurnoverDr", "BalancedTurnoverCr");
        }
      }
      recorderAndPeriods(turnovers, true, adjustment);
      List<Map<String, Object>> turnoverParams = new ArrayList<>(List.of(param("BeginOfPeriod", "periodStart"),
        param("EndOfPeriod", "periodEnd"), periodicity(adjustment), param("AccountCondition", "account"),
        param("ExtDimensions", "extDimensions"), param("Condition", "condition")));
      if (correspondence) {
        turnoverParams.add(param("BalancedAccountCondition", "corAccount"));
        turnoverParams.add(param("BalancedExtDimensions", "corExtDimensions"));
      }
      virtual("Turnovers", turnoverParams, turnovers);

      List<Map<String, Object>> both = new ArrayList<>();
      both.add(std("Account", accountType));
      numbered(both, "ExtDimension", extDimensions, valueType);
      balanceFlagged(both, props.dimensions, "dimension", facts);
      separators(both);
      derived(both, props.resources, facts, "OpeningBalance", "OpeningBalanceDr", "OpeningBalanceCr",
        "OpeningSplittedBalanceDr", "OpeningSplittedBalanceCr", "Turnover", "TurnoverDr", "TurnoverCr",
        "ClosingBalance", "ClosingBalanceDr", "ClosingBalanceCr", "ClosingSplittedBalanceDr",
        "ClosingSplittedBalanceCr");
      recorderAndPeriods(both, true, adjustment);
      virtual("BalanceAndTurnovers", List.of(param("BeginOfPeriod", "periodStart"), param("EndOfPeriod", "periodEnd"),
        periodicity(adjustment), complementMethod(), param("AccountCondition", "account"),
        param("ExtDimensions", "extDimensions"), param("Condition", "condition")), both);

      if (correspondence) {
        List<Map<String, Object>> drCr = new ArrayList<>();
        drCr.add(std("AccountDr", accountType));
        drCr.add(std("AccountCr", accountType));
        numbered(drCr, "ExtDimensionDr", extDimensions, valueType);
        numbered(drCr, "ExtDimensionCr", extDimensions, valueType);
        balanceSplit(drCr, props.dimensions, "dimension", true, facts);
        separators(drCr);
        for (MdNamedPropertyDto resource : list(props.resources)) {
          if (facts.balance("resource", resource.name)) {
            derivedOne(drCr, resource, facts, "Turnover");
          } else {
            derivedOne(drCr, resource, facts, "TurnoverDr", "TurnoverCr");
          }
        }
        recorderAndPeriods(drCr, false, adjustment);
        virtual("DrCrTurnovers", List.of(param("BeginOfPeriod", "periodStart"), param("EndOfPeriod", "periodEnd"),
          periodicity(adjustment), param("AccountDrCondition", "accountDr"),
          param("ExtDimensionsDr", "extDimensionsDr"), param("AccountCrCondition", "accountCr"),
          param("ExtDimensionsCr", "extDimensionsCr"), param("Condition", "condition")), drCr);
      }

      // Движения с субконто — поля основной таблицы, но из общих реквизитов, как у прочих виртуальных
      // таблиц регистра, только разделители
      List<Map<String, Object>> records = copy(fields);
      records.removeIf(row -> "commonAttribute".equals(row.get("kind")));
      virtual("RecordsWithExtDimensions", List.of(param("BeginOfPeriod", "periodStart"),
        param("EndOfPeriod", "periodEnd"), param("Condition", "condition"), param("Order", "order"),
        param("Top", "top")), records);
    }

    void calculationRegister() throws IOException {
      ObjectFacts facts = ObjectFacts.read(objectFile, edt, tag);
      boolean actionPeriod = "true".equals(facts.props().get("actionPeriod"));
      boolean basePeriod = "true".equals(facts.props().get("basePeriod"));
      String chart = referenceName(facts.props().get("chartOfCalculationTypes"));
      List<Map<String, Object>> fields = new ArrayList<>();
      fields.add(std("Recorder", null));
      fields.add(std("LineNumber", Types.decimal()));
      fields.add(std("Active", Types.bool()));
      fields.add(std("CalculationType", chart.isEmpty() ? null : Types.ref("ChartOfCalculationTypes", chart)));
      fields.add(std("RegistrationPeriod", Types.dateTime()));
      fields.add(std("ReversingEntry", Types.bool()));
      if (actionPeriod) {
        fields.add(std("ActionPeriod", Types.dateTime()));
        fields.add(std("BegOfActionPeriod", Types.dateTime()));
        fields.add(std("EndOfActionPeriod", Types.dateTime()));
      }
      if (basePeriod) {
        fields.add(std("BegOfBasePeriod", Types.dateTime()));
        fields.add(std("EndOfBasePeriod", Types.dateTime()));
      }
      attributes(fields, props.dimensions, "dimension");
      attributes(fields, props.resources, "resource");
      attributes(fields, props.attributes, "attribute");
      commonAttributes(fields);
      main(fields);
      if (actionPeriod) {
        virtual("ActualActionPeriod", List.of(param("Condition", "condition")), copy(fields));
      }
    }

    void constant() {
      MdConstantPropertiesDto c = props.constant;
      List<Map<String, Object>> fields = new ArrayList<>();
      fields.add(field(fieldRu("Value"), "Value", "standard", c == null ? null : Types.of(c.type), ""));
      main(fields);
    }

    /**
     * Критерий отбора — таблица с одним параметром: значением, по которому отбираются объекты. Это
     * единственная основная таблица с параметрами: своей таблицы без значения у критерия нет, а
     * основной она числится, чтобы имя объекта без хвоста находило её так же, как у прочих видов.
     *
     * <p>{@code Ссылка} — ссылки на объекты, чьи реквизиты входят в состав критерия (у реквизита
     * табличной части — на сам объект); тип параметра — свойство «Тип» критерия.
     */
    void filterCriterion() throws IOException, JAXBException {
      List<Map<String, Object>> fields = new ArrayList<>();
      fields.add(std("Ref", Types.refs(ObjectFacts.contentOwners(props.contentRefs))));
      Map<String, Object> value = param("Value", "value");
      value.put("required", true);
      value.put("type", Types.of(ObjectFacts.filterCriterionType(objectFile, edt, version)));
      tables.add(table(objectName(), "main", nonNull(props.synonym), List.of(value), fields));
    }

    /**
     * Последовательность: основная таблица и её границы с одними и теми же полями — период,
     * регистратор (ссылки на документы последовательности), момент времени и измерения.
     * {@code Границы} — без параметров.
     */
    void sequence() throws IOException, JAXBException {
      List<String> documents = ObjectFacts.sequenceDocuments(objectFile, edt, props);
      List<Map<String, Object>> fields = new ArrayList<>();
      fields.add(std("Period", Types.dateTime()));
      fields.add(std("Recorder", Types.refs(documents)));
      fields.add(std("PointInTime", null));
      attributes(fields, ObjectFacts.sequenceDimensions(objectFile, edt, version, props), "dimension");
      main(fields);
      virtual("Boundaries", List.of(), copy(fields));
    }

    // --- таблицы ---------------------------------------------------------------------------

    private void main(List<Map<String, Object>> fields) {
      tables.add(table(objectName(), "main", nonNull(props.synonym), List.of(), fields));
    }

    private void tabularSections() {
      for (MdNamedPropertyDto section : list(props.tabularSections)) {
        Map<String, String> synonyms = tabularSynonyms(section.name);
        List<Map<String, Object>> fields = new ArrayList<>();
        // Ссылка табличной части — ссылка на её владельца, номер строки нумерует строки внутри него
        fields.add(field(fieldRu("Ref"), "Ref", "standard", self(), synonyms.getOrDefault("Ref", "")));
        fields.add(field(fieldRu("LineNumber"), "LineNumber", "standard", Types.decimal(),
          synonyms.getOrDefault("LineNumber", "")));
        attributes(fields, section.attributes, "attribute");
        tables.add(table(objectName() + "." + section.name, "tabularSection", nonNull(section.synonym),
          List.of(), fields));
      }
    }

    private void standardTabularSection(String en, List<Map<String, Object>> fields) {
      String name = english ? en : NAMES.standardTabularSections.get(en);
      String synonym = standardSection(en).map(s -> nonNull(s.synonym))
        .orElse(StandardAttributeLabels.standardTabularSectionLabel(props.kind, en));
      tables.add(table(objectName() + "." + name, "standardTabularSection", synonym, List.of(), fields));
    }

    private void calculationTypesSection(String en, Map<String, Object> calculationType) {
      List<Map<String, Object>> section = new ArrayList<>();
      section.add(sectionStd(en, "Ref", self()));
      section.add(sectionStd(en, "LineNumber", Types.decimal()));
      section.add(sectionStd(en, "CalculationType", calculationType));
      section.add(sectionStd(en, "Predefined", Types.bool()));
      standardTabularSection(en, section);
    }

    private void virtual(String en, List<Map<String, Object>> params, List<Map<String, Object>> fields) {
      String name = english ? en : NAMES.tables.get(en);
      tables.add(table(objectName() + "." + name, "virtual", "", params, fields));
    }

    private static Map<String, Object> table(
      String name, String kind, String synonym, List<Map<String, Object>> params, List<Map<String, Object>> fields) {
      Map<String, Object> row = new LinkedHashMap<>();
      row.put("name", name);
      row.put("kind", kind);
      row.put("synonym", synonym);
      row.put("params", params);
      row.put("fields", fields);
      return row;
    }

    // --- параметры виртуальных таблиц ------------------------------------------------------

    private Map<String, Object> param(String en, String kind) {
      Map<String, Object> row = new LinkedHashMap<>();
      row.put("name", english ? en : NAMES.params.get(en));
      row.put("kind", kind);
      return row;
    }

    /**
     * Параметр «Периодичность» со значениями в порядке списка конструктора: Период, Запись, Регистратор,
     * [УточнениеПериода], Секунда … Год, Авто.
     *
     * @param adjustment есть ли значение {@code УточнениеПериода}: только у оборотных таблиц регистра
     *     бухгалтерии с длиной уточнения периода больше 0
     */
    private Map<String, Object> periodicity(boolean adjustment) {
      Map<String, String> values = new LinkedHashMap<>(NAMES.periodicity);
      if (!adjustment) {
        values.remove("PeriodAdjustment");
      }
      Map<String, Object> row = param("Periodicity", "periodicity");
      row.put("values", values(values));
      return row;
    }

    /** Параметр «МетодДополнения» таблицы остатков и оборотов (английский ключ словаря — PeriodAdjustment). */
    private Map<String, Object> complementMethod() {
      Map<String, Object> row = param("PeriodAdjustment", "method");
      row.put("values", values(NAMES.periodAdjustment));
      return row;
    }

    private List<String> values(Map<String, String> dictionary) {
      return new ArrayList<>(english ? dictionary.keySet() : dictionary.values());
    }

    // --- поля ------------------------------------------------------------------------------

    /** Стандартный реквизит объекта: подпись — переопределённый синоним либо подпись платформы. */
    private Map<String, Object> std(String en, Map<String, Object> type) {
      return field(fieldRu(en), en, "standard", type, structure.standardAttributeSynonyms.getOrDefault(en, ""));
    }

    /** Стандартный реквизит стандартной табличной части. */
    private Map<String, Object> sectionStd(String section, String en, Map<String, Object> type) {
      String synonym = standardSection(section)
        .map(s -> s.standardAttributeSynonyms.getOrDefault(en, ""))
        .orElse(StandardAttributeLabels.ofStandardTabularSection(props.kind, section).getOrDefault(en, ""));
      return field(fieldRu(en), en, "standard", type, synonym);
    }

    private Map<String, Object> field(String ru, String enName, String kind, Map<String, Object> type, String synonym) {
      Map<String, Object> row = new LinkedHashMap<>();
      row.put("name", english ? enName : ru);
      row.put("nameEn", enName);
      row.put("kind", kind);
      row.put("type", type);
      row.put("synonym", synonym == null ? "" : synonym);
      return row;
    }

    private String fieldRu(String en) {
      Map<String, String> own = NAMES.fieldsByObject.getOrDefault(tag, Map.of());
      return own.getOrDefault(en, NAMES.fields.getOrDefault(en, en));
    }

    /** Свои узлы объекта: имя одно на обоих языках, синоним — из описания узла. */
    private void attributes(List<Map<String, Object>> out, List<MdNamedPropertyDto> nodes, String kind) {
      for (MdNamedPropertyDto node : list(nodes)) {
        out.add(field(node.name, node.name, kind, Types.of(node.type), node.synonym));
      }
    }

    /**
     * Поля общих реквизитов, применённых к объекту, — у основной таблицы (и её копий: срезов,
     * задач по исполнителю, фактического периода действия): разделители и прочие реквизиты по имени.
     */
    private void commonAttributes(List<Map<String, Object>> out) {
      for (CommonAttributeUsage.Applied attribute : common()) {
        out.add(commonField(attribute));
      }
    }

    /** Только разделители — у виртуальных таблиц итогов регистров накопления и бухгалтерии. */
    private void separators(List<Map<String, Object>> out) {
      for (CommonAttributeUsage.Applied attribute : common()) {
        if (attribute.separator()) {
          out.add(commonField(attribute));
        }
      }
    }

    /** Применённые общие реквизиты: читаются один раз на объект и только у видов, где они бывают. */
    private List<CommonAttributeUsage.Applied> common() {
      if (common == null) {
        CommonAttributeUsage.Result result =
          CommonAttributeUsage.of(objectFile, tag + "." + props.internalName, version);
        notes.addAll(result.notes());
        common = result.applied();
      }
      return common;
    }

    /**
     * Поле общего реквизита: имя одно на обоих языках, вид {@code separator} у разделителя и
     * {@code commonAttribute} у прочих, {@code source} — ссылка на реквизит; у разделителя ещё режим
     * использования разделяемых данных.
     */
    private Map<String, Object> commonField(CommonAttributeUsage.Applied attribute) {
      Map<String, Object> row = field(attribute.name(), attribute.name(),
        attribute.separator() ? "separator" : "commonAttribute", Types.of(attribute.type()), attribute.synonym());
      row.put("source", "CommonAttribute." + attribute.name());
      if (attribute.separator()) {
        row.put("separatedDataUse", attribute.separatedDataUse());
      }
      return row;
    }

    /** Поля с номером: субконто и их виды, по одному на каждое из {@code count}. */
    private void numbered(List<Map<String, Object>> out, String en, int count, Map<String, Object> type) {
      for (int i = 1; i <= count; i++) {
        out.add(field(fieldRu(en) + i, en + i, "standard", type, ""));
      }
    }

    /**
     * Поля ресурсов с суффиксами виртуальной таблицы: для каждого ресурса — все суффиксы подряд.
     *
     * @param facts признаки «балансовый» регистра бухгалтерии; {@code null} у прочих регистров
     */
    private void derived(
      List<Map<String, Object>> out, List<MdNamedPropertyDto> resources, ObjectFacts facts, String... suffixes) {
      for (MdNamedPropertyDto resource : list(resources)) {
        derivedOne(out, resource, facts, suffixes);
      }
    }

    private void derivedOne(
      List<Map<String, Object>> out, MdNamedPropertyDto resource, ObjectFacts facts, String... suffixes) {
      for (String suffix : suffixes) {
        Map<String, Object> row = field(resource.name + NAMES.suffixes.get(suffix), resource.name + suffix,
          "resource", Types.of(resource.type), "");
        row.put("source", resource.name);
        if (facts != null) {
          row.put("balance", facts.balance("resource", resource.name));
        }
        out.add(row);
      }
    }

    /** Измерения регистра бухгалтерии одним полем с признаком «балансовый». */
    private void balanceFlagged(
      List<Map<String, Object>> out, List<MdNamedPropertyDto> nodes, String kind, ObjectFacts facts) {
      for (MdNamedPropertyDto node : list(nodes)) {
        Map<String, Object> row = field(node.name, node.name, kind, Types.of(node.type), node.synonym);
        row.put("balance", facts.balance(kind, node.name));
        out.add(row);
      }
    }

    /**
     * Измерения таблицы {@code Обороты} регистра бухгалтерии: каждое одним полем с признаком
     * «балансовый», а у небалансового при корреспонденции следом ещё корреспондирующее поле
     * {@code <Имя>Кор} ({@code <Имя>Balanced}) с тем же типом — значение измерения по
     * корреспондирующей стороне проводки, как {@code КорСчет} у счёта.
     */
    private void turnoverDimensions(List<Map<String, Object>> out, boolean correspondence, ObjectFacts facts) {
      for (MdNamedPropertyDto node : list(props.dimensions)) {
        boolean balance = facts.balance("dimension", node.name);
        Map<String, Object> row = field(node.name, node.name, "dimension", Types.of(node.type), node.synonym);
        row.put("balance", balance);
        out.add(row);
        if (correspondence && !balance) {
          Map<String, Object> cor = field(node.name + NAMES.suffixes.get("Balanced"), node.name + "Balanced",
            "dimension", Types.of(node.type), node.synonym);
          cor.put("source", node.name);
          cor.put("balance", false);
          out.add(cor);
        }
      }
    }

    /** Балансовый узел — одно поле, небалансовый при корреспонденции — пара полей Дт и Кт. */
    private void balanceSplit(List<Map<String, Object>> out, List<MdNamedPropertyDto> nodes, String kind,
      boolean correspondence, ObjectFacts facts) {
      for (MdNamedPropertyDto node : list(nodes)) {
        boolean balance = facts.balance(kind, node.name);
        if (balance || !correspondence) {
          Map<String, Object> row = field(node.name, node.name, kind, Types.of(node.type), node.synonym);
          row.put("balance", balance);
          out.add(row);
          continue;
        }
        for (String side : List.of("Dr", "Cr")) {
          Map<String, Object> row = field(node.name + NAMES.suffixes.get(side), node.name + side, kind,
            Types.of(node.type), node.synonym);
          row.put("source", node.name);
          row.put("balance", false);
          out.add(row);
        }
      }
    }

    /**
     * Регистратор, номер строки и поля периода оборотных таблиц.
     *
     * @param adjustment поле {@code УточнениеПериода} (число) сразу за {@code Период}: у регистра
     *     бухгалтерии с длиной уточнения периода больше 0 — его выбирают при периодичности УточнениеПериода
     */
    private void recorderAndPeriods(List<Map<String, Object>> out, boolean lineNumber, boolean adjustment) {
      if (lineNumber) {
        out.add(std("LineNumber", Types.decimal()));
      }
      out.add(std("Recorder", null));
      for (String en : PERIOD_FIELDS) {
        out.add(field(NAMES.periods.get(en), en, "period", Types.dateTime(), ""));
        if (adjustment && "Period".equals(en)) {
          out.add(field(fieldRu("PeriodAdjustment"), "PeriodAdjustment", "period", Types.decimal(), ""));
        }
      }
    }

    // --- вспомогательное -------------------------------------------------------------------

    private Map<String, Object> self() {
      return Types.ref(tag, props.internalName);
    }

    private Map<String, String> tabularSynonyms(String section) {
      for (MdObjectStructureDto.MdTabularSectionDto ts : structure.tabularSections) {
        if (section.equals(ts.name)) {
          return ts.standardAttributeSynonyms;
        }
      }
      return StandardAttributeLabels.ofTabularSection();
    }

    private Optional<MdObjectStructureDto.MdTabularSectionDto> standardSection(String en) {
      return structure.standardTabularSections.stream().filter(s -> en.equals(s.name)).findFirst();
    }

    /**
     * Объект того же вида конфигурации рядом с описываемым: план счетов регистра бухгалтерии.
     *
     * @return свойства объекта; пусто, если файла нет (макет вне выгрузки, частичная копия)
     */
    private Optional<MdObjectPropertiesDto> sibling(String kind, String name) throws IOException, JAXBException {
      // Выгрузка: <корень>/<Вид>/<Имя>.xml; проект EDT: <src>/<Вид>/<Имя>/<Имя>.mdo
      Path root = ancestor(objectFile.toAbsolutePath(), edt ? 3 : 2);
      if (root == null) {
        return Optional.empty();
      }
      if (edt) {
        Optional<Path> file = EdtLayout.objectMdo(root, kind, name);
        return file.isEmpty()
          ? Optional.empty()
          : Optional.of(EdtObjectProperties.readDto(file.get(), EdtModel.bundled()));
      }
      String directory = CfObjectPathResolver.subdirsByType().get(kind);
      Path file = root.resolve(directory).resolve(name + ".xml");
      return Files.isRegularFile(file)
        ? Optional.of(MdObjectPropertiesEdit.readDto(file, version))
        : Optional.empty();
    }

    private static Path ancestor(Path file, int levels) {
      Path out = file;
      for (int i = 0; i < levels && out != null; i++) {
        out = out.getParent();
      }
      return out;
    }

    private static List<Map<String, Object>> copy(List<Map<String, Object>> fields) {
      List<Map<String, Object>> out = new ArrayList<>();
      for (Map<String, Object> field : fields) {
        out.add(new LinkedHashMap<>(field));
      }
      return out;
    }
  }

  /** Имя объекта из ссылки {@code Вид.Имя}; пустая или битая ссылка — пустая строка. */
  private static String referenceName(String reference) {
    if (reference == null) {
      return "";
    }
    int dot = reference.indexOf('.');
    return dot < 0 ? "" : reference.substring(dot + 1).trim();
  }

  private static boolean positive(String number) {
    return integer(number) > 0;
  }

  private static int integer(String number) {
    if (number == null || number.isBlank()) {
      return 0;
    }
    try {
      return Integer.parseInt(number.trim());
    } catch (NumberFormatException e) {
      return 0;
    }
  }

  private static String nonNull(String value) {
    return value == null ? "" : value;
  }

  private static <T> List<T> list(List<T> values) {
    return values == null ? List.of() : values;
  }

  /** Типы полей формой {@code MdTypeDescriptionDto}: в JSON только заданные части. */
  private static final class Types {

    private Types() {
    }

    static Map<String, Object> of(MdTypeDescriptionDto dto) {
      if (dto == null) {
        return null;
      }
      Map<String, Object> out = new LinkedHashMap<>();
      out.put("types", dto.types == null ? List.of() : dto.types);
      out.put("typeSets", dto.typeSets == null ? List.of() : dto.typeSets);
      if (dto.typeIds != null) {
        out.put("typeIds", dto.typeIds);
      }
      // Ответ пишется с null-ами (у поля без типа ключ type обязан быть), поэтому квалификаторы
      // раскладываются только заданными частями — как их отдаёт cf-md-object-get
      if (dto.stringQualifiers != null) {
        out.put("stringQualifiers", qualifiers(
          "length", dto.stringQualifiers.length, "allowedLength", dto.stringQualifiers.allowedLength));
      }
      if (dto.numberQualifiers != null) {
        out.put("numberQualifiers", qualifiers("digits", dto.numberQualifiers.digits,
          "fractionDigits", dto.numberQualifiers.fractionDigits, "allowedSign", dto.numberQualifiers.allowedSign));
      }
      if (dto.dateQualifiers != null) {
        out.put("dateQualifiers", qualifiers("dateFractions", dto.dateQualifiers.dateFractions));
      }
      if (dto.binaryDataQualifiers != null) {
        out.put("binaryDataQualifiers", qualifiers(
          "length", dto.binaryDataQualifiers.length, "allowedLength", dto.binaryDataQualifiers.allowedLength));
      }
      return out;
    }

    /** Пары ключ-значение без пустых значений. */
    private static Map<String, Object> qualifiers(String... pairs) {
      Map<String, Object> out = new LinkedHashMap<>();
      for (int i = 0; i + 1 < pairs.length; i += 2) {
        if (pairs[i + 1] != null) {
          out.put(pairs[i], pairs[i + 1]);
        }
      }
      return out;
    }

    static Map<String, Object> bool() {
      return primitive("xs:boolean");
    }

    static Map<String, Object> decimal() {
      return primitive("xs:decimal");
    }

    static Map<String, Object> string() {
      return primitive("xs:string");
    }

    static Map<String, Object> string(String length, String allowedLength) {
      Map<String, Object> out = primitive("xs:string");
      Map<String, Object> qualifiers = new LinkedHashMap<>();
      qualifiers.put("length", length.trim());
      qualifiers.put("allowedLength", allowedLength == null || allowedLength.isBlank() ? "VARIABLE" : allowedLength);
      out.put("stringQualifiers", qualifiers);
      return out;
    }

    static Map<String, Object> dateTime() {
      Map<String, Object> out = primitive("xs:dateTime");
      out.put("dateQualifiers", Map.of("dateFractions", "DATE_TIME"));
      return out;
    }

    /** Код или номер: строка заданной длины либо неотрицательное целое из стольких же цифр. */
    static Map<String, Object> code(String codeType, String length, String allowedLength) {
      if ("NUMBER".equals(codeType)) {
        Map<String, Object> out = primitive("xs:decimal");
        Map<String, Object> qualifiers = new LinkedHashMap<>();
        qualifiers.put("digits", length.trim());
        qualifiers.put("fractionDigits", "0");
        qualifiers.put("allowedSign", "NONNEGATIVE");
        out.put("numberQualifiers", qualifiers);
        return out;
      }
      return string(length, allowedLength);
    }

    static Map<String, Object> ref(String kind, String name) {
      Map<String, Object> out = new LinkedHashMap<>();
      out.put("types", List.of("cfg:" + kind + "Ref." + name));
      out.put("typeSets", List.of());
      return out;
    }

    /** Ссылки по списку {@code Вид.Имя}: владельцы справочника, документы журнала, базовые виды расчёта. */
    static Map<String, Object> refs(List<String> references) {
      List<String> types = new ArrayList<>();
      for (String reference : references) {
        int dot = reference.indexOf('.');
        if (dot < 0) {
          continue;
        }
        String kind = reference.substring(0, dot);
        if (REF_KINDS.contains(kind)) {
          types.add("cfg:" + kind + "Ref." + reference.substring(dot + 1));
        }
      }
      if (types.isEmpty()) {
        return null;
      }
      Map<String, Object> out = new LinkedHashMap<>();
      out.put("types", types);
      out.put("typeSets", List.of());
      return out;
    }

    /** Значение субконто: характеристика плана видов характеристик, как пишет его тип платформа. */
    static Map<String, Object> characteristic(String name) {
      Map<String, Object> out = new LinkedHashMap<>();
      out.put("types", List.of());
      out.put("typeSets", List.of("cfg:Characteristic." + name));
      return out;
    }

    private static Map<String, Object> primitive(String type) {
      Map<String, Object> out = new LinkedHashMap<>();
      out.put("types", List.of(type));
      out.put("typeSets", List.of());
      return out;
    }
  }

  /**
   * Свойства объекта прямо из его файла: план счетов и корреспонденция регистра бухгалтерии,
   * периоды регистра расчёта, признак «балансовый» измерений и ресурсов. DTO EDT этих свойств
   * не несёт, а признака «балансовый» нет ни в одном DTO.
   *
   * @param props свойства верхнего уровня, имя со строчной буквы ({@code chartOfAccounts})
   * @param balance {@code вид:имя} узла ({@code dimension:Организация}) → балансовый ли он
   */
  private record ObjectFacts(Map<String, String> props, Map<String, Boolean> balance) {

    boolean balance(String kind, String name) {
      return Boolean.TRUE.equals(balance.get(kind + ":" + name));
    }

    static ObjectFacts read(Path file, boolean edt, String tag) throws IOException {
      String xml = Files.readString(file, StandardCharsets.UTF_8);
      Map<String, String> props = new HashMap<>();
      Map<String, Boolean> balance = new HashMap<>();
      if (edt) {
        List<XmlLines.Node> roots = XmlLines.children(xml, List.of());
        if (roots.isEmpty()) {
          return new ObjectFacts(props, balance);
        }
        XmlLines.Node root = roots.get(0);
        props.putAll(root.leaves());
        for (XmlLines.Node child : XmlLines.childrenAt(xml, root.start())) {
          String kind = switch (child.name()) {
            case "dimensions" -> "dimension";
            case "resources" -> "resource";
            default -> null;
          };
          if (kind != null) {
            balance.put(kind + ":" + child.leaves().get("name"), "true".equals(child.leaves().get("balance")));
          }
        }
        return new ObjectFacts(props, balance);
      }
      for (XmlLines.Node node : XmlLines.children(xml, List.of("MetaDataObject", tag))) {
        if ("Properties".equals(node.name())) {
          node.leaves().forEach((key, value) -> props.put(lowerFirst(key), value));
        } else if ("ChildObjects".equals(node.name())) {
          for (XmlLines.Node child : XmlLines.childrenAt(xml, node.start())) {
            String kind = switch (child.name()) {
              case "Dimension" -> "dimension";
              case "Resource" -> "resource";
              default -> null;
            };
            if (kind == null) {
              continue;
            }
            for (XmlLines.Node part : XmlLines.childrenAt(xml, child.start())) {
              if ("Properties".equals(part.name())) {
                balance.put(kind + ":" + part.leaves().get("Name"), "true".equals(part.leaves().get("Balance")));
              }
            }
          }
        }
      }
      return new ObjectFacts(props, balance);
    }

    private static String lowerFirst(String name) {
      return name.isEmpty() ? name : Character.toLowerCase(name.charAt(0)) + name.substring(1);
    }

    // Критерии отбора и последовательности читаются здесь, а не в модели свойств объекта: модель питает
    // cf-md-object-get и запись свойств, а DTO двух форматов у этих видов расходятся (у выгрузки нет
    // измерений последовательности, у EDT — её документов).

    /**
     * Объекты, чьи реквизиты входят в состав критерия отбора: {@code Вид.Имя} из ссылок
     * {@code Document.X.Attribute.Y} и {@code Document.X.TabularSection.T.Attribute.Y}.
     *
     * @return без повторов, в порядке первого появления
     */
    static List<String> contentOwners(List<String> content) {
      LinkedHashSet<String> owners = new LinkedHashSet<>();
      for (String reference : list(content)) {
        String[] parts = reference.split("\\.", 3);
        if (parts.length >= 2 && !parts[0].isBlank() && !parts[1].isBlank()) {
          owners.add(parts[0].trim() + "." + parts[1].trim());
        }
      }
      return new ArrayList<>(owners);
    }

    /**
     * Свойство «Тип» критерия отбора — тип значения его параметра. В DTO свойств его нет ни у одного
     * формата: у выгрузки оно читается моделью формата, у проекта EDT — корневой {@code type}.
     *
     * @return описание; пустой тип (так он записан у нового критерия) — {@code null}
     */
    static MdTypeDescriptionDto filterCriterionType(Path file, boolean edt, SchemaVersion version)
      throws IOException, JAXBException {
      MdTypeDescriptionDto type = edt
        ? EdtObjectProperties.readType(file, "type", EdtModel.bundled())
        : MdTypeDescriptionBridge.read(JaxbReflect.getOptional(designerProperties(file, version, "getFilterCriterion"),
          "getType"));
      return type == null || list(type.types).isEmpty() && list(type.typeSets).isEmpty()
        && list(type.typeIds).isEmpty() ? null : type;
    }

    /** Документы последовательности: у выгрузки они в DTO, у проекта EDT — только в файле. */
    static List<String> sequenceDocuments(Path file, boolean edt, MdObjectPropertiesDto props) throws IOException {
      return edt ? EdtObjectProperties.readList(file, "documents") : list(props.documents);
    }

    /**
     * Измерения последовательности: у проекта EDT они в DTO, у выгрузки — только в файле (чтение
     * свойств перебирает измерения лишь у регистров).
     */
    static List<MdNamedPropertyDto> sequenceDimensions(
      Path file, boolean edt, SchemaVersion version, MdObjectPropertiesDto props) throws IOException, JAXBException {
      if (edt) {
        return list(props.dimensions);
      }
      // Синонимы — на языке конфигурации, как у прочих узлов ответа
      return ConfigurationLanguage.with(file, () -> {
        List<MdNamedPropertyDto> out = new ArrayList<>();
        Object sequence = designerObject(file, version, "getSequence");
        Object children = sequence == null ? null : JaxbReflect.getOptional(sequence, "getChildObjects");
        for (Object dimension : JaxbReflect.<Object>listOptional(children, "getDimension")) {
          Object p = JaxbReflect.get(dimension, "getProperties");
          String comment = JaxbReflect.getStringOptional(p, "getComment");
          MdNamedPropertyDto node = new MdNamedPropertyDto(JaxbReflect.getString(p, "getName"),
            LocalStringSync.first(JaxbReflect.getOptional(p, "getSynonym")), comment == null ? "" : comment);
          node.type = MdTypeDescriptionBridge.read(JaxbReflect.getOptional(p, "getType"));
          out.add(node);
        }
        return out;
      });
    }

    /** Свойства объекта выгрузки моделью формата; {@code null} — объект другого вида. */
    private static Object designerProperties(Path file, SchemaVersion version, String getter) throws IOException,
      JAXBException {
      Object object = designerObject(file, version, getter);
      return object == null ? null : JaxbReflect.getOptional(object, "getProperties");
    }

    private static Object designerObject(Path file, SchemaVersion version, String getter) throws IOException,
      JAXBException {
      try (InputStream in = Files.newInputStream(file)) {
        Object root = JaxbReflect.value(DesignerXml.unmarshal(version, in));
        return root == null ? null : JaxbReflect.getOptional(root, getter);
      }
    }
  }

  /** Словарь имён языка запросов: английское имя → русское. */
  private static final class Names {
    private final Map<String, String> objects;
    private final Map<String, String> fields;
    private final Map<String, Map<String, String>> fieldsByObject;
    private final Map<String, String> tables;
    private final Map<String, String> standardTabularSections;
    private final Map<String, String> params;
    private final Map<String, String> suffixes;
    private final Map<String, String> periods;
    private final Map<String, String> periodicity;
    private final Map<String, String> periodAdjustment;

    private Names(JsonObject root) {
      objects = map(root, "objects");
      fields = map(root, "fields");
      Map<String, Map<String, String>> byObject = new LinkedHashMap<>();
      JsonObject byObjectNode = root.getAsJsonObject("fieldsByObject");
      for (String kind : byObjectNode.keySet()) {
        byObject.put(kind, map(byObjectNode, kind));
      }
      fieldsByObject = Collections.unmodifiableMap(byObject);
      tables = map(root, "tables");
      standardTabularSections = map(root, "standardTabularSections");
      params = map(root, "params");
      suffixes = map(root, "suffixes");
      periods = map(root, "periods");
      periodicity = map(root, "periodicity");
      periodAdjustment = map(root, "periodAdjustment");
    }

    private static Map<String, String> map(JsonObject root, String key) {
      Map<String, String> out = new LinkedHashMap<>();
      for (Map.Entry<String, JsonElement> e : root.getAsJsonObject(key).entrySet()) {
        out.put(e.getKey(), e.getValue().getAsString());
      }
      return Collections.unmodifiableMap(out);
    }

    static Names load() {
      try (InputStream stream = MdQueryTables.class.getResourceAsStream(RESOURCE)) {
        if (stream == null) {
          throw new IllegalStateException("ресурс не найден: " + RESOURCE);
        }
        try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
          return new Names(new Gson().fromJson(reader, JsonObject.class));
        }
      } catch (IOException e) {
        throw new IllegalStateException("не прочитан ресурс " + RESOURCE, e);
      }
    }
  }

}
