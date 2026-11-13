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
import com.google.gson.reflect.TypeToken;
import io.github.yellowhammer.designerxml.SchemaVersion;
import io.github.yellowhammer.designerxml.Ssl31SubmodulePaths;
import io.github.yellowhammer.designerxml.cli.DesignerXmlCli;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Stream;

/**
 * Точечная правка параметров данных и вывода ({@code cf-dcs-set-parameter-value}).
 * Существующий элемент правится по отдельности: замена значения не трогает {@code use},
 * вложенные под-параметры и {@code userSettingID}, правка одного {@code use} не трогает
 * значение (заменой элемента либо вставкой на место первым по xs:sequence). Новый параметр
 * пишется минимальным элементом (значение присутствует всегда - {@code xsi:nil} вместо
 * отсутствия): параметр вывода - на место по порядку платформы, имя вне таблицы рангов и
 * параметр данных - в конец {@code item*} секции; отсутствующая секция создаётся по
 * xs:sequence вида узла, а {@code remove: true} вырезает элемент и снимает опустевшую
 * секцию без хвоста - байтовый
 * возврат к исходнику. Параметры данных есть только у корня настроек, параметры вывода
 * правятся и у узлов структуры (группировка, колонка таблицы). Скаляры пишутся без обёртки
 * ({@code xs:string}/{@code xs:decimal}/{@code xs:boolean}), объект {@code Value} - по белому
 * списку записи (в нём и тип диаграммы {@code v8ui:ChartType} лексемой XSD), явный
 * {@code value: null} - {@code xsi:nil}. Имя с точкой (под-параметр) - отказ. Канал
 * apply-mutation выполняет операцию, отказ доходит кодом 2 без изменения файла. Пустая
 * строка языка ({@code v8:LocalStringType} без {@code decoded}) пишется самозакрытым
 * элементом, как её хранит платформа: эхо по обоим корпусам - байтовый no-op.
 */
class DcsSettingsParameterTest {

  /** Один вариант «Основной»: параметры данных и вывода корня, группы и колонка таблицы. */
  private static final String ACCOUNTING_CHECK =
    "src/cf/Reports/РезультатыПроверкиУчета/Templates/ОсновнаяСхемаКомпоновкиДанных/Ext/Template.xml";

  /** Вариант «Дополнительный1» без секций параметров: создание секции по xs:sequence. */
  private static final String FILE_DYNAMICS =
    "src/cf/Reports/_ДемоДинамикаИзмененийФайлов/Templates/ОсновнаяСхемаКомпоновкиДанных/Ext/Template.xml";

  /**
   * Диаграмма «Основного» с ТипДиаграммы Pie3D и под-параметром ТипДиаграммы.ВидПодписей
   * в параметрах вывода узла; в корне настроек - один параметр вывода Заголовок.
   */
  private static final String ORDER_STATUSES =
    "src/cf/Reports/_ДемоСтатусыЗаказовПокупателей/Templates/ОсновнаяСхемаКомпоновкиДанных/Ext/Template.xml";

  /** Путь диаграммы варианта «Основной» в {@link #ORDER_STATUSES}. */
  private static final List<Map<String, Object>> CHART_PATH = List.of(Map.of("list", "structure", "index", 0));

  /** В корне варианта {@link #STALLED_TASKS_VARIANT} - оформление «Текст» с пустой строкой языка. */
  private static final String STALLED_TASKS =
    "src/cf/Reports/ЗависшиеЗадачи/Templates/ОсновнаяСхемаКомпоновкиДанных/Ext/Template.xml";

  private static final String STALLED_TASKS_VARIANT = "СводкаПоЗависшимЗадачам";

  /** Списки узлов структуры, по которым обход доходит до параметров вывода вложенных узлов. */
  private static final List<String> NODE_LISTS = List.of("structure", "columns", "rows", "points", "series");

  private static final Gson GSON = new Gson();

  private static final Type MAP_TYPE = new TypeToken<Map<String, Object>>() { }.getType();

  @TempDir Path tempDir;

  // ------------------------------------------------------------------ параметры вывода корня

  @Test
  void editsOutputParametersPointwise() throws Exception {
    Path dcs = copySchema(ACCOUNTING_CHECK);
    String before = Files.readString(dcs, StandardCharsets.UTF_8);

    // замена значения Заголовок (локализованная строка) не трогает его use и соседей
    setParameter(dcs, Map.of("section", "output", "parameter", "Заголовок",
      "value", Map.of("type", "v8:LocalStringType", "decoded", "Сводка контроля учета")));
    String titled = replaceFirst(before,
      "\t\t\t\t\t<dcscor:value xsi:type=\"v8:LocalStringType\">\r\n"
        + "\t\t\t\t\t\t<v8:item>\r\n"
        + "\t\t\t\t\t\t\t<v8:lang>ru</v8:lang>\r\n"
        + "\t\t\t\t\t\t\t<v8:content>Контроль ведения учета</v8:content>\r\n"
        + "\t\t\t\t\t\t</v8:item>\r\n"
        + "\t\t\t\t\t</dcscor:value>",
      "\t\t\t\t\t<dcscor:value xsi:type=\"v8:LocalStringType\">\r\n"
        + "\t\t\t\t\t\t<v8:item>\r\n"
        + "\t\t\t\t\t\t\t<v8:lang>ru</v8:lang>\r\n"
        + "\t\t\t\t\t\t\t<v8:content>Сводка контроля учета</v8:content>\r\n"
        + "\t\t\t\t\t\t</v8:item>\r\n"
        + "\t\t\t\t\t</dcscor:value>");
    assertThat(Files.readString(dcs, StandardCharsets.UTF_8)).isEqualTo(titled);

    // правка одного use не трогает значение; use: true - умолчание, которое платформа не хранит:
    // записанный false снимается целой строкой
    setParameter(dcs, Map.of("section", "output", "parameter", "Заголовок", "use", true));
    String used = replaceFirst(titled,
      "\t\t\t\t\t<dcscor:use>false</dcscor:use>\r\n"
        + "\t\t\t\t\t<dcscor:parameter>Заголовок</dcscor:parameter>",
      "\t\t\t\t\t<dcscor:parameter>Заголовок</dcscor:parameter>");
    assertThat(Files.readString(dcs, StandardCharsets.UTF_8)).isEqualTo(used);
    // повтор use: true у элемента без use - файл не тронут
    setParameter(dcs, Map.of("section", "output", "parameter", "Заголовок", "use", true));
    assertThat(Files.readString(dcs, StandardCharsets.UTF_8)).isEqualTo(used);

    // у ВыводитьОтбор use не было: он встаёт первым по xs:sequence - перед parameter
    setParameter(dcs, Map.of("section", "output", "parameter", "ВыводитьОтбор", "use", false));
    String flagged = replaceFirst(used,
      "\t\t\t\t\t<dcscor:parameter>ВыводитьОтбор</dcscor:parameter>",
      "\t\t\t\t\t<dcscor:use>false</dcscor:use>\r\n"
        + "\t\t\t\t\t<dcscor:parameter>ВыводитьОтбор</dcscor:parameter>");
    assertThat(Files.readString(dcs, StandardCharsets.UTF_8)).isEqualTo(flagged);

    // замена значения-перечисления лексемой белого списка
    setParameter(dcs, Map.of("section", "output", "parameter", "ВыводитьОтбор",
      "value", Map.of("type", "dcsset:DataCompositionTextOutputType", "text", "Output")));
    String shown = replaceFirst(flagged,
      "\t\t\t\t\t<dcscor:parameter>ВыводитьОтбор</dcscor:parameter>\r\n"
        + "\t\t\t\t\t<dcscor:value xsi:type=\"dcsset:DataCompositionTextOutputType\">"
        + "DontOutput</dcscor:value>",
      "\t\t\t\t\t<dcscor:parameter>ВыводитьОтбор</dcscor:parameter>\r\n"
        + "\t\t\t\t\t<dcscor:value xsi:type=\"dcsset:DataCompositionTextOutputType\">"
        + "Output</dcscor:value>");
    assertThat(Files.readString(dcs, StandardCharsets.UTF_8)).isEqualTo(shown);

    // нового параметра нет: минимальный элемент по порядку платформы, строка - xs:string;
    // МакетОформления у платформы раньше ВыводитьЗаголовок - он встаёт первым в секции
    setParameter(dcs, Map.of("section", "output", "parameter", "МакетОформления", "value", "БезОформления"));
    assertThat(Files.readString(dcs, StandardCharsets.UTF_8)).isEqualTo(replaceFirst(shown,
      "\t\t\t<dcsset:outputParameters>\r\n",
      "\t\t\t<dcsset:outputParameters>\r\n"
        + "\t\t\t\t<dcscor:item xsi:type=\"dcsset:SettingsParameterValue\">\r\n"
        + "\t\t\t\t\t<dcscor:parameter>МакетОформления</dcscor:parameter>\r\n"
        + "\t\t\t\t\t<dcscor:value xsi:type=\"xs:string\">БезОформления</dcscor:value>\r\n"
        + "\t\t\t\t</dcscor:item>\r\n"));
    Map<String, Object> created = parameterByName(
      outputParameters(settings(dcs, "Основной")), "МакетОформления");
    assertThat(map(list(created.get("value")).get(0))).containsEntry("text", "БезОформления");

    // remove: true вырезает элемент целиком - байтовый возврат
    setParameter(dcs, Map.of("section", "output", "parameter", "МакетОформления", "remove", true));
    assertThat(Files.readString(dcs, StandardCharsets.UTF_8)).isEqualTo(shown);
  }

  // ------------------------------------------------------------------ параметры вывода группировки

  @Test
  void groupOutputParametersAreRefusedInRootAndKeptAtGroups() throws Exception {
    // Пять параметров вывода группировки платформа в корне настроек не хранит: загрузка молча
    // отбрасывает их и при значениях не по умолчанию; в корпусах в корне их нет, у группировок - есть
    Map<String, Object> values = new LinkedHashMap<>();
    values.put("КоличествоЗаписей", 10);
    values.put("ПроцентЗаписей", 50);
    values.put("ТипМакета", Map.of("type", "dcsset:DataCompositionGroupTemplateType", "text", "Vertical"));
    values.put("РасположениеОбщихИтогов", Map.of("type", "dcscor:DataCompositionTotalPlacement", "text", "None"));
    values.put("ВариантИспользованияГруппировки",
      Map.of("type", "dcsset:DataCompositionGroupUseVariant", "text", "AdditionalInformation"));
    Path dcs = copySchema(ACCOUNTING_CHECK);
    String before = read(dcs);
    List<Map<String, Object>> group = List.of(Map.of("list", "structure", "index", 0));
    List<Map<String, Object>> tableColumn =
      List.of(Map.of("list", "structure", "index", 2), Map.of("list", "columns", "index", 0));
    for (Map.Entry<String, Object> entry : values.entrySet()) {
      String name = entry.getKey();
      // корень: и со значением, и одним use - отказ без записи, в том числе кодом 2 канала;
      // РасположениеОбщихИтогов не хранится и у группировки - подсказка ведёт к группе строк
      String hint = "РасположениеОбщихИтогов".equals(name) ? "задайте его у группы строк таблицы"
        : "задайте его у группировки";
      Map<String, Object> rootValue = Map.of("section", "output", "parameter", name, "value", entry.getValue());
      assertThatThrownBy(() -> setParameter(dcs, rootValue))
        .as(name).isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("«" + name + "»")
        .hasMessageContaining("платформа не хранит этот параметр вывода в корне настроек; " + hint);
      assertThatThrownBy(() -> setParameter(dcs, Map.of("section", "output", "parameter", name, "use", false)))
        .as(name).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("в корне настроек");
      assertThat(applyMutation(Map.of("op", "cf-dcs-set-parameter-value", "objectXml", dcs.toString(),
        "schemaVersion", "V2_20", "variant", "Основной", "payloadJson", GSON.toJson(Map.of(
          "section", "output", "parameter", name, "value", entry.getValue()))))).as(name).isEqualTo(2);
      assertThat(read(dcs)).as(name).isEqualTo(before);

      // группировка структуры и группа колонок таблицы - пишется и снимается байтовым возвратом там, где уровень
      // параметр хранит: РасположениеОбщихИтогов не хранит группировка структуры (см.
      // rootOutputParametersAreRefusedAtStructureGroupsAndByOwnRulesBelowTables), ТипМакета - группа таблицы, а у
      // самой таблицы не хранится ни один из пяти (см. DcsSettingsOutputLevelsTest)
      for (List<Map<String, Object>> path : List.of(group, tableColumn)) {
        if (path == group && "РасположениеОбщихИтогов".equals(name)
          || path == tableColumn && "ТипМакета".equals(name)) {
          continue;
        }
        setParameter(dcs, Map.of("section", "output", "path", path, "parameter", name, "value", entry.getValue()));
        assertThat(read(dcs)).as(name + " " + path).contains("<dcscor:parameter>" + name + "</dcscor:parameter>");
        setParameter(dcs, Map.of("section", "output", "path", path, "parameter", name, "remove", true));
        assertThat(read(dcs)).as(name + " " + path).isEqualTo(before);
      }

      // параметр данных с тем же именем правилу не подчиняется
      setParameter(dcs, Map.of("section", "data", "parameter", name, "value", entry.getValue()));
      setParameter(dcs, Map.of("section", "data", "parameter", name, "remove", true));
      assertThat(read(dcs)).as(name).isEqualTo(before);
    }

    // элемент, уже записанный в корне файла: эхо - без записи, правка - отказ, remove - снимает
    String rooted = insertedBeforeParameter(before, "<dcsset:settings", "ВыводитьЗаголовок",
      parameterItem(4, "КоличествоЗаписей", "<dcscor:value xsi:type=\"xs:decimal\">10</dcscor:value>"));
    Files.writeString(dcs, rooted, StandardCharsets.UTF_8);
    setParameter(dcs, Map.of("section", "output", "parameter", "КоличествоЗаписей",
      "value", Map.of("type", "xs:decimal", "text", "10")));
    setParameter(dcs, Map.of("section", "output", "parameter", "КоличествоЗаписей", "use", true));
    assertThat(read(dcs)).isEqualTo(rooted);
    assertThatThrownBy(() -> setParameter(dcs, Map.of("section", "output", "parameter", "КоличествоЗаписей",
      "value", 20)))
      .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("задайте его у группировки");
    assertThat(read(dcs)).isEqualTo(rooted);
    setParameter(dcs, Map.of("section", "output", "parameter", "КоличествоЗаписей", "remove", true));
    assertThat(read(dcs)).isEqualTo(before);
  }

  @Test
  void corporaKeepGroupOutputParametersBelowRootOnly() throws Exception {
    // корпусы правилу не противоречат: в корне настроек вариантов пяти имён нет ни разу, а ниже
    // корня их 36 (КоличествоЗаписей - 22 группировки, 3 точки и серия; ПроцентЗаписей и ТипМакета -
    // по 4 группировки; РасположениеОбщихИтогов и ВариантИспользованияГруппировки - вложенная группа строки)
    for (Path root : List.of(Ssl31SubmodulePaths.projectRoot().resolve("src"),
      Path.of(System.getProperty("fixtures.ssl31edt.root")))) {
      int[] counts = new int[2];
      for (Path schema : schemaFiles(root)) {
        Map<String, Object> info = DcsSettingsRead.info(schema, SchemaVersion.V2_20, null);
        for (Map<String, Object> variant : list(info.get("variants"))) {
          countGroupOutputParameters(settings(schema, (String) variant.get("name")), true, counts);
        }
      }
      assertThat(counts).as(root.toString()).containsExactly(0, 36);
    }
  }

  @Test
  void rootOutputParametersAreRefusedAtStructureGroupsAndByOwnRulesBelowTables() throws Exception {
    // Пять параметров вывода платформа не хранит у группировки структуры на любой глубине: загрузка их
    // отбрасывает, хотя четыре из них с теми же значениями в корне настроек сохраняются
    // (РасположениеОбщихИтогов не хранится и в корне). У таблицы, диаграммы и их групп запреты свои: из этих
    // пяти таблица хранит только ГоризонтальноеРасположениеОбщихИтогов, группы таблицы - только
    // РасположениеОбщихИтогов, диаграмма и её группы - ни одного; подсказка отказа там называет свой уровень
    Map<String, Object> values = new LinkedHashMap<>();
    values.put("ФиксацияСверху", enumValue("dcsset:DataCompositionFixation", "DontUse"));
    values.put("ФиксацияСлева", enumValue("dcsset:DataCompositionFixation", "DontUse"));
    values.put("ВыводитьПараметрыДанных", enumValue("dcsset:DataCompositionTextOutputType", "DontOutput"));
    values.put("РасположениеОбщихИтогов", enumValue("dcscor:DataCompositionTotalPlacement", "None"));
    values.put("ГоризонтальноеРасположениеОбщихИтогов", enumValue("dcscor:DataCompositionTotalPlacement", "None"));
    Path dcs = copySchema(ACCOUNTING_CHECK);
    Map<String, Object> group = Map.of("list", "structure", "index", 0);
    // вложенная группировка структуры (детальные записи) внутри первой
    DcsSettingsEdit.addStructureItem(dcs, SchemaVersion.V2_20, "Основной",
      Map.of("parentPath", List.of(group), "list", "structure", "kind", "group"));
    // диаграмма с точкой и вложенной группой точки
    Path chart = copySchema(ORDER_STATUSES);
    DcsSettingsEdit.addStructureItem(chart, SchemaVersion.V2_20, "Основной",
      Map.of("parentPath", CHART_PATH, "list", "points"));
    List<Map<String, Object>> point = List.of(CHART_PATH.get(0), Map.of("list", "points", "index", 0));
    DcsSettingsEdit.addStructureItem(chart, SchemaVersion.V2_20, "Основной",
      Map.of("parentPath", point, "list", "structure", "kind", "group"));
    String before = read(dcs);
    String chartBefore = read(chart);
    Map<String, Object> table = Map.of("list", "structure", "index", 2);
    List<List<Map<String, Object>>> refused = List.of(List.of(group), List.of(group, group));
    List<List<Map<String, Object>>> tableLevels = List.of(List.of(table),
      List.of(table, Map.of("list", "columns", "index", 0)), List.of(table, Map.of("list", "rows", "index", 0)),
      List.of(table, Map.of("list", "rows", "index", 0), Map.of("list", "structure", "index", 0)));
    List<List<Map<String, Object>>> chartLevels = List.of(CHART_PATH, point,
      List.of(CHART_PATH.get(0), Map.of("list", "points", "index", 0), Map.of("list", "structure", "index", 0)));
    for (Map.Entry<String, Object> entry : values.entrySet()) {
      String name = entry.getKey();
      String hint = "РасположениеОбщихИтогов".equals(name)
        ? "в корне настроек он тоже не хранится, задайте его у группы строк таблицы" : "задайте его в корне настроек";
      // группировка структуры на любой глубине: и значение, и один use - отказ без записи, кодом 2 канала
      for (List<Map<String, Object>> path : refused) {
        assertThatThrownBy(() -> setParameter(dcs, Map.of("section", "output", "path", path, "parameter", name,
          "value", entry.getValue())))
          .as(name + " " + path).isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("«" + name + "»")
          .hasMessageContaining("платформа не хранит этот параметр вывода у группировки структуры; " + hint);
        assertThatThrownBy(() -> setParameter(dcs, Map.of("section", "output", "path", path, "parameter", name,
          "use", false)))
          .as(name + " " + path).isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("у группировки структуры");
        assertThat(applyMutation(Map.of("op", "cf-dcs-set-parameter-value", "objectXml", dcs.toString(),
          "schemaVersion", "V2_20", "variant", "Основной", "payloadJson", GSON.toJson(Map.of(
            "section", "output", "path", path, "parameter", name, "value", entry.getValue())))))
          .as(name + " " + path).isEqualTo(2);
        assertThat(read(dcs)).as(name + " " + path).isEqualTo(before);
      }
      // таблица, её колонка, строка и вложенная группа строки: хранимое уровнем пишется и снимается байтовым
      // возвратом, остальное - отказ своего уровня
      for (List<Map<String, Object>> path : tableLevels) {
        boolean tableItself = path.size() == 1;
        if (!name.equals(tableItself ? "ГоризонтальноеРасположениеОбщихИтогов" : "РасположениеОбщихИтогов")) {
          assertThatThrownBy(() -> setParameter(dcs, Map.of("section", "output", "path", path, "parameter", name,
            "value", entry.getValue())))
            .as(name + " " + path).isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining(tableItself ? "у таблицы;" : "у группы строк или колонок таблицы;");
          assertThat(read(dcs)).as(name + " " + path).isEqualTo(before);
          continue;
        }
        setParameter(dcs, Map.of("section", "output", "path", path, "parameter", name, "value", entry.getValue()));
        assertThat(read(dcs)).as(name + " " + path).contains("<dcscor:parameter>" + name + "</dcscor:parameter>");
        setParameter(dcs, Map.of("section", "output", "path", path, "parameter", name, "remove", true));
        assertThat(read(dcs)).as(name + " " + path).isEqualTo(before);
      }
      // диаграмма, точка и группа точки: ни одного из пяти
      for (List<Map<String, Object>> path : chartLevels) {
        assertThatThrownBy(() -> setParameter(chart, Map.of("section", "output", "path", path, "parameter", name,
          "value", entry.getValue())))
          .as(name + " " + path).isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining(path.size() == 1 ? "у диаграммы;" : "у группы точек или серий диаграммы;");
        assertThat(read(chart)).as(name + " " + path).isEqualTo(chartBefore);
      }
    }

    // элемент, уже записанный у группировки в файле: эхо - без записи, правка - отказ, remove - снимает
    String grouped = insertedBeforeParameter(before, "<dcsset:name>ИнформацияОПоследнейПроверке</dcsset:name>",
      "ТипЗаголовкаПолей", parameterItem(5, "ГоризонтальноеРасположениеОбщихИтогов",
        "<dcscor:value xsi:type=\"dcscor:DataCompositionTotalPlacement\">None</dcscor:value>"));
    Files.writeString(dcs, grouped, StandardCharsets.UTF_8);
    Map<String, Object> placement = Map.of("section", "output", "path", List.of(group),
      "parameter", "ГоризонтальноеРасположениеОбщихИтогов");
    setParameter(dcs, with(placement, "value", enumValue("dcscor:DataCompositionTotalPlacement", "None")));
    setParameter(dcs, with(placement, "use", true));
    assertThat(read(dcs)).isEqualTo(grouped);
    assertThatThrownBy(() -> setParameter(dcs, with(placement, "value",
      enumValue("dcscor:DataCompositionTotalPlacement", "Begin"))))
      .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("задайте его в корне настроек");
    assertThat(read(dcs)).isEqualTo(grouped);
    setParameter(dcs, with(placement, "remove", true));
    assertThat(read(dcs)).isEqualTo(before);
  }

  @Test
  void corporaKeepRootOutputParametersAwayFromStructureGroups() throws Exception {
    // Где в корпусах встречаются пять параметров, запрещённых у группировки структуры: в корне настроек
    // (ВыводитьПараметрыДанных и ГоризонтальноеРасположениеОбщихИтогов) и у вложенной группы строки
    // таблицы (РасположениеОбщихИтогов); у групп структуры - ни разу, ФиксацияСлева и ФиксацияСверху -
    // нигде. Настоящая правка каждого из них (флаг use туда и обратно) проходит без отказа и
    // возвращает файл байт в байт
    Map<String, Integer> expected = Map.of(
      "ВыводитьПараметрыДанных @ корень", 38,
      "ГоризонтальноеРасположениеОбщихИтогов @ корень", 21,
      "РасположениеОбщихИтогов @ группа таблицы", 1);
    for (Path root : List.of(Ssl31SubmodulePaths.projectRoot().resolve("src"),
      Path.of(System.getProperty("fixtures.ssl31edt.root")))) {
      Map<String, Integer> counts = new java.util.TreeMap<>();
      int index = 0;
      for (Path schema : schemaFiles(root)) {
        Path copy = null;
        byte[] original = null;
        for (Map<String, Object> variant : list(DcsSettingsRead.info(schema, SchemaVersion.V2_20, null)
          .get("variants"))) {
          String name = (String) variant.get("name");
          List<Object[]> found = new ArrayList<>();
          collectRootOutputParameters(settings(schema, name), List.of(), "корень", found);
          for (Object[] hit : found) {
            String parameter = (String) hit[0];
            counts.merge(parameter + " @ " + hit[2], 1, Integer::sum);
            if (copy == null) {
              copy = Files.createTempDirectory(tempDir, "corpus" + index++).resolve(schema.getFileName());
              Files.copy(schema, copy);
              original = Files.readAllBytes(copy);
            }
            boolean off = Boolean.FALSE.equals(hit[3]);
            Map<String, Object> payload = Map.of("section", "output", "path", hit[1], "parameter", parameter);
            DcsSettingsEdit.setParameterValue(copy, SchemaVersion.V2_20, name, with(payload, "use", off));
            assertThat(Files.readAllBytes(copy)).as(schema + " " + parameter).isNotEqualTo(original);
            DcsSettingsEdit.setParameterValue(copy, SchemaVersion.V2_20, name, with(payload, "use", !off));
            assertThat(Files.readAllBytes(copy)).as(schema + " " + parameter).isEqualTo(original);
          }
        }
      }
      assertThat(counts).as(root.toString()).isEqualTo(expected);
    }
  }

  /**
   * Пять параметров, запрещённых у группировки структуры, во всех секциях вывода узла и его потомков:
   * {имя, путь, вид владельца, use}.
   */
  private static void collectRootOutputParameters(Map<String, Object> node, List<Map<String, Object>> path,
    String owner, List<Object[]> found) {
    List<String> names = List.of("ФиксацияСверху", "ФиксацияСлева", "ВыводитьПараметрыДанных",
      "РасположениеОбщихИтогов", "ГоризонтальноеРасположениеОбщихИтогов");
    if (node.get("outputParameters") instanceof Map<?, ?> section) {
      for (Map<String, Object> parameter : list(section.get("items"))) {
        if (names.contains(parameter.get("parameter"))) {
          found.add(new Object[] {parameter.get("parameter"), path, owner, parameter.get("use")});
        }
      }
    }
    for (String key : NODE_LISTS) {
      if (!(node.get(key) instanceof List<?> children)) {
        continue;
      }
      for (int i = 0; i < children.size(); i++) {
        Map<String, Object> child = map(children.get(i));
        List<Map<String, Object>> childPath = new ArrayList<>(path);
        childPath.add(Map.of("list", key, "index", i));
        boolean structureOnly = childPath.stream().allMatch(step -> "structure".equals(step.get("list")));
        String kind = (String) child.get("kind");
        String childOwner = switch (kind) {
          case "table" -> "таблица";
          case "chart" -> "диаграмма";
          case "group" -> structureOnly ? "группировка структуры"
            : childPath.stream().anyMatch(step -> List.of("points", "series").contains(step.get("list")))
              ? "группа диаграммы" : "группа таблицы";
          default -> "вложенный отчёт";
        };
        collectRootOutputParameters(child, childPath, childOwner, found);
      }
    }
  }

  @Test
  void removingLastChildCollapsesSelfClosedTablesBackOnBothCorpora() throws Exception {
    // Пустую таблицу платформа пишет самозакрытым тегом (по 10 таких в ssl31 и EDT-паре), первая вставка его
    // раскрывает. Снятие последнего ребёнка сворачивает узел обратно, поэтому remove только что добавленного
    // возвращает файл байт в байт и здесь: параметр вывода, выбранное поле, условное оформление и строка
    // таблицы. Параметры вывода - из хранимых у таблицы, в том числе ГоризонтальноеРасположениеОбщихИтогов,
    // которого не хранит группировка структуры
    for (Path root : List.of(Ssl31SubmodulePaths.projectRoot().resolve("src"),
      Path.of(System.getProperty("fixtures.ssl31edt.root")))) {
      int tables = 0;
      for (Path schema : schemaFiles(root)) {
        if (!read(schema).contains("<dcsset:item xsi:type=\"dcsset:StructureItemTable\"/>")) {
          continue;
        }
        Path copy = Files.createTempDirectory(tempDir, "empty-table").resolve(schema.getFileName());
        Files.copy(schema, copy);
        String original = read(copy);
        for (Map<String, Object> variant : list(DcsSettingsRead.info(schema, SchemaVersion.V2_20, null)
          .get("variants"))) {
          String name = (String) variant.get("name");
          List<List<Map<String, Object>>> found = new ArrayList<>();
          collectSelfClosedTables(original, name, settings(schema, name), List.of(), found);
          for (List<Map<String, Object>> path : found) {
            tables++;
            String where = schema + " " + name + " " + path;
            for (String parameter : List.of("МакетОформления", "ГоризонтальноеРасположениеОбщихИтогов",
              "ВыводитьЗаголовок")) {
              Object value = switch (parameter) {
                case "МакетОформления" -> "БезОформления";
                case "ГоризонтальноеРасположениеОбщихИтогов" -> enumValue("dcscor:DataCompositionTotalPlacement", "None");
                default -> enumValue("dcsset:DataCompositionTextOutputType", "DontOutput");
              };
              Map<String, Object> payload = Map.of("section", "output", "path", path, "parameter", parameter);
              DcsSettingsEdit.setParameterValue(copy, SchemaVersion.V2_20, name, with(payload, "value", value));
              assertThat(read(copy)).as(where + " " + parameter).isNotEqualTo(original);
              DcsSettingsEdit.setParameterValue(copy, SchemaVersion.V2_20, name, with(payload, "remove", true));
              assertThat(read(copy)).as(where + " " + parameter).isEqualTo(original);
            }
            DcsSettingsEdit.setSelection(copy, SchemaVersion.V2_20, name, Map.of("path", path,
              "items", List.of(Map.of("kind", "field", "field", "Поле"))));
            assertThat(read(copy)).as(where + " selection").isNotEqualTo(original);
            DcsSettingsEdit.setSelection(copy, SchemaVersion.V2_20, name, Map.of("path", path, "items", List.of()));
            assertThat(read(copy)).as(where + " selection").isEqualTo(original);
            DcsSettingsEdit.addAppearanceItem(copy, SchemaVersion.V2_20, name, Map.of("path", path,
              "item", Map.of("appearance", List.of(Map.of("parameter", "ЦветФона",
                "value", List.of(Map.of("type", "v8ui:Color", "text", "web:Yellow")))))));
            assertThat(read(copy)).as(where + " appearance").isNotEqualTo(original);
            DcsSettingsEdit.removeAppearanceItem(copy, SchemaVersion.V2_20, name, Map.of("path", path, "index", 0));
            assertThat(read(copy)).as(where + " appearance").isEqualTo(original);
            DcsSettingsEdit.addStructureItem(copy, SchemaVersion.V2_20, name, Map.of("parentPath", path,
              "list", "rows"));
            assertThat(read(copy)).as(where + " rows").isNotEqualTo(original);
            List<Map<String, Object>> row = new ArrayList<>(path);
            row.add(Map.of("list", "rows", "index", 0));
            DcsSettingsEdit.removeStructureItem(copy, SchemaVersion.V2_20, name, Map.of("path", row));
            assertThat(read(copy)).as(where + " rows").isEqualTo(original);
          }
        }
      }
      assertThat(tables).as(root.toString()).isEqualTo(10);
    }
  }

  /** Пути узлов-таблиц, записанных в файле самозакрытым тегом. */
  private static void collectSelfClosedTables(String text, String variant, Map<String, Object> node,
    List<Map<String, Object>> path, List<List<Map<String, Object>>> found) {
    for (String key : NODE_LISTS) {
      if (!(node.get(key) instanceof List<?> children)) {
        continue;
      }
      for (int i = 0; i < children.size(); i++) {
        Map<String, Object> child = map(children.get(i));
        List<Map<String, Object>> childPath = new ArrayList<>(path);
        childPath.add(Map.of("list", key, "index", i));
        if ("table".equals(child.get("kind"))) {
          List<DcsSettingsEdit.Step> steps = childPath.stream()
            .map(step -> new DcsSettingsEdit.Step((String) step.get("list"), (Integer) step.get("index")))
            .toList();
          int start = DcsSettingsEdit.resolveNode(text, variant, steps).element().start();
          if (text.charAt(XmlLines.startTagEnd(text, start) - 1) == '/') {
            found.add(childPath);
          }
        }
        collectSelfClosedTables(text, variant, child, childPath, found);
      }
    }
  }

  /** Копия payload с ещё одним ключом. */
  private static Map<String, Object> with(Map<String, Object> payload, String key, Object value) {
    Map<String, Object> out = new LinkedHashMap<>(payload);
    out.put(key, value);
    return out;
  }

  /** Считает пять параметров вывода группировки: {в корне, ниже корня}. */
  private static void countGroupOutputParameters(Map<String, Object> node, boolean root, int[] counts) {
    List<String> names = List.of("КоличествоЗаписей", "ПроцентЗаписей", "ТипМакета", "РасположениеОбщихИтогов",
      "ВариантИспользованияГруппировки");
    if (node.get("outputParameters") instanceof Map<?, ?> section) {
      for (Map<String, Object> parameter : list(section.get("items"))) {
        if (names.contains(parameter.get("parameter"))) {
          counts[root ? 0 : 1]++;
        }
      }
    }
    for (String key : NODE_LISTS) {
      if (node.get(key) instanceof List<?> children) {
        for (Object child : children) {
          countGroupOutputParameters(map(child), false, counts);
        }
      }
    }
  }

  // ------------------------------------------------------------------ параметры данных

  @Test
  void editsDataParametersAtRootOnly() throws Exception {
    Path dcs = copySchema(ACCOUNTING_CHECK);
    String before = Files.readString(dcs, StandardCharsets.UTF_8);

    // замена стандартного периода готовым вариантом не трогает userSettingID элемента
    setParameter(dcs, Map.of("section", "data", "parameter", "Период",
      "value", Map.of("type", "v8:StandardPeriod", "period", Map.of("variant", "ThisMonth"))));
    String monthly = replaceFirst(before,
      "\t\t\t\t\t<dcscor:value xsi:type=\"v8:StandardPeriod\">\r\n"
        + "\t\t\t\t\t\t<v8:variant xsi:type=\"v8:StandardPeriodVariant\">Custom</v8:variant>\r\n"
        + "\t\t\t\t\t\t<v8:startDate>0001-01-01T00:00:00</v8:startDate>\r\n"
        + "\t\t\t\t\t\t<v8:endDate>0001-01-01T00:00:00</v8:endDate>\r\n"
        + "\t\t\t\t\t</dcscor:value>",
      "\t\t\t\t\t<dcscor:value xsi:type=\"v8:StandardPeriod\">\r\n"
        + "\t\t\t\t\t\t<v8:variant xsi:type=\"v8:StandardPeriodVariant\">ThisMonth</v8:variant>\r\n"
        + "\t\t\t\t\t</dcscor:value>");
    assertThat(Files.readString(dcs, StandardCharsets.UTF_8)).isEqualTo(monthly);
    assertThat(monthly).contains("eb8c1726-3dca-45be-aaab-4467fd5575f1");

    // явный value: null пишет xsi:nil (значение в формате присутствует всегда)
    Map<String, Object> toNil = new LinkedHashMap<>();
    toNil.put("section", "data");
    toNil.put("parameter", "Период");
    toNil.put("value", null);
    setParameter(dcs, toNil);
    String nil = replaceFirst(monthly,
      "\t\t\t\t\t<dcscor:value xsi:type=\"v8:StandardPeriod\">\r\n"
        + "\t\t\t\t\t\t<v8:variant xsi:type=\"v8:StandardPeriodVariant\">ThisMonth</v8:variant>\r\n"
        + "\t\t\t\t\t</dcscor:value>",
      "\t\t\t\t\t<dcscor:value xsi:nil=\"true\"/>");
    assertThat(Files.readString(dcs, StandardCharsets.UTF_8)).isEqualTo(nil);

    // use-правка без value не трогает значение и хвост элемента: use: true снимает строку use=false
    setParameter(dcs, Map.of("section", "data", "parameter", "ВыводитьОтветственного", "use", true));
    String used = replaceFirst(nil,
      "\t\t\t\t\t<dcscor:use>false</dcscor:use>\r\n"
        + "\t\t\t\t\t<dcscor:parameter>ВыводитьОтветственного</dcscor:parameter>",
      "\t\t\t\t\t<dcscor:parameter>ВыводитьОтветственного</dcscor:parameter>");
    assertThat(Files.readString(dcs, StandardCharsets.UTF_8)).isEqualTo(used);
    Map<String, Object> reread = parameterByName(
      list(map(settings(dcs, "Основной").get("dataParameters")).get("items")),
      "ВыводитьОтветственного");
    assertThat(reread).doesNotContainKey("use")
      .containsEntry("userSettingID", "6140f08b-fb31-4f1c-b649-9dadd3dc2200");
    assertThat(map(list(reread.get("value")).get(0))).containsEntry("text", "false");

    // use: false возвращает строку на место - байтовый возврат
    setParameter(dcs, Map.of("section", "data", "parameter", "ВыводитьОтветственного", "use", false));
    assertThat(Files.readString(dcs, StandardCharsets.UTF_8)).isEqualTo(nil);
  }

  // ------------------------------------------------------------------ параметры узлов структуры

  @Test
  void editsNodeOutputParameters() throws Exception {
    Path dcs = copySchema(ACCOUNTING_CHECK);
    String before = Files.readString(dcs, StandardCharsets.UTF_8);

    // параметр вывода группировки (первый узел структуры)
    setParameter(dcs, Map.of("section", "output",
      "path", List.of(Map.of("list", "structure", "index", 0)),
      "parameter", "МакетОформления", "value", "Основной"));
    String styled = replaceFirst(before,
      "\t\t\t\t\t\t<dcscor:value xsi:type=\"xs:string\">БезОформления</dcscor:value>",
      "\t\t\t\t\t\t<dcscor:value xsi:type=\"xs:string\">Основной</dcscor:value>");
    assertThat(Files.readString(dcs, StandardCharsets.UTF_8)).isEqualTo(styled);

    // параметр вывода колонки таблицы (третий узел структуры)
    setParameter(dcs, Map.of("section", "output",
      "path", List.of(Map.of("list", "structure", "index", 2), Map.of("list", "columns", "index", 0)),
      "parameter", "ВыводитьЗаголовок", "use", true));
    assertThat(Files.readString(dcs, StandardCharsets.UTF_8)).isEqualTo(replaceFirst(styled,
      "\t\t\t\t\t\t\t<dcscor:use>false</dcscor:use>\r\n"
        + "\t\t\t\t\t\t\t<dcscor:parameter>ВыводитьЗаголовок</dcscor:parameter>",
      "\t\t\t\t\t\t\t<dcscor:parameter>ВыводитьЗаголовок</dcscor:parameter>"));
  }

  @Test
  void createsMissingSectionsBySequenceAndCollapsesOnRemove() throws Exception {
    Path dcs = copySchema(FILE_DYNAMICS);
    String before = Files.readString(dcs, StandardCharsets.UTF_8);
    String from = "<dcsset:name>Дополнительный1</dcsset:name>";

    // у корня варианта нет dataParameters: секция встаёт после selection по xs:sequence,
    // значение нового параметра присутствует всегда - без value пишется xsi:nil
    DcsSettingsEdit.setParameterValue(dcs, SchemaVersion.V2_20, "Дополнительный1",
      Map.of("section", "data", "parameter", "Период"));
    assertThat(Files.readString(dcs, StandardCharsets.UTF_8)).isEqualTo(insertAfterFirst(before, from,
      "\r\n\t\t\t</dcsset:selection>\r\n",
      "\t\t\t<dcsset:dataParameters>\r\n"
        + "\t\t\t\t<dcscor:item xsi:type=\"dcsset:SettingsParameterValue\">\r\n"
        + "\t\t\t\t\t<dcscor:parameter>Период</dcscor:parameter>\r\n"
        + "\t\t\t\t\t<dcscor:value xsi:nil=\"true\"/>\r\n"
        + "\t\t\t\t</dcscor:item>\r\n"
        + "\t\t\t</dcsset:dataParameters>\r\n"));

    // remove: true последнего элемента снимает опустевшую секцию - байтовый возврат
    DcsSettingsEdit.setParameterValue(dcs, SchemaVersion.V2_20, "Дополнительный1",
      Map.of("section", "data", "parameter", "Период", "remove", true));
    assertThat(Files.readString(dcs, StandardCharsets.UTF_8)).isEqualTo(before);

    // параметры вывода создаются тем же способом и там же снимаются
    DcsSettingsEdit.setParameterValue(dcs, SchemaVersion.V2_20, "Дополнительный1",
      Map.of("section", "output", "parameter", "Заголовок", "value", "Тест"));
    assertThat(Files.readString(dcs, StandardCharsets.UTF_8)).isEqualTo(insertAfterFirst(before, from,
      "\r\n\t\t\t</dcsset:selection>\r\n",
      "\t\t\t<dcsset:outputParameters>\r\n"
        + "\t\t\t\t<dcscor:item xsi:type=\"dcsset:SettingsParameterValue\">\r\n"
        + "\t\t\t\t\t<dcscor:parameter>Заголовок</dcscor:parameter>\r\n"
        + "\t\t\t\t\t<dcscor:value xsi:type=\"xs:string\">Тест</dcscor:value>\r\n"
        + "\t\t\t\t</dcscor:item>\r\n"
        + "\t\t\t</dcsset:outputParameters>\r\n"));
    DcsSettingsEdit.setParameterValue(dcs, SchemaVersion.V2_20, "Дополнительный1",
      Map.of("section", "output", "parameter", "Заголовок", "remove", true));
    assertThat(Files.readString(dcs, StandardCharsets.UTF_8)).isEqualTo(before);
  }

  // ------------------------------------------------------------------ порядок новых параметров вывода

  @Test
  void newOutputParameterTakesPlatformPlaceInRoot() throws Exception {
    // корень «Основного» - один Заголовок: РасположениеИтогов у платформы раньше него
    Path statuses = copySchema(ORDER_STATUSES);
    String original = read(statuses);
    setParameter(statuses, Map.of("section", "output", "parameter", "РасположениеИтогов",
      "value", Map.of("type", "dcscor:DataCompositionTotalPlacement", "text", "Begin")));
    String placed = insertedBeforeParameter(original, "<dcsset:settings", "Заголовок",
      parameterItem(4, "РасположениеИтогов",
        "<dcscor:value xsi:type=\"dcscor:DataCompositionTotalPlacement\">Begin</dcscor:value>"));
    assertThat(read(statuses)).isEqualTo(placed);

    // ТипДиаграммы в корень (так пишет мастер настроек): после РасположениеИтогов, перед Заголовок
    setParameter(statuses, Map.of("section", "output", "parameter", "ТипДиаграммы",
      "value", Map.of("type", "v8ui:ChartType", "decoded", "Pie")));
    String charted = insertedBeforeParameter(placed, "<dcsset:settings", "Заголовок",
      parameterItem(4, "ТипДиаграммы", "<dcscor:value xsi:type=\"v8ui:ChartType\">Pie</dcscor:value>"));
    assertThat(read(statuses)).isEqualTo(charted);

    // существующие не переставляются: эхо прочитанного Заголовок - байтовый no-op
    Map<String, Object> title = parameterByName(outputParameters(settings(statuses, "Основной")), "Заголовок");
    setParameter(statuses, Map.of("section", "output", "parameter", "Заголовок",
      "use", title.get("use"), "value", list(title.get("value")).get(0)));
    assertThat(read(statuses)).isEqualTo(charted);

    // снятие новых - байтовый возврат к исходнику
    setParameter(statuses, Map.of("section", "output", "parameter", "ТипДиаграммы", "remove", true));
    setParameter(statuses, Map.of("section", "output", "parameter", "РасположениеИтогов", "remove", true));
    assertThat(read(statuses)).isEqualTo(original);

    // корень «Основного» РезультатыПроверкиУчета: ВыводитьЗаголовок, Заголовок,
    // ВыводитьПараметрыДанных, ВыводитьОтбор - ТипДиаграммы встаёт перед первым из оставшихся
    Path accounting = copySchema(ACCOUNTING_CHECK);
    String full = read(accounting);
    Map<String, Object> pie = Map.of("type", "v8ui:ChartType", "decoded", "Pie");
    String chartItem = parameterItem(4, "ТипДиаграммы", "<dcscor:value xsi:type=\"v8ui:ChartType\">Pie</dcscor:value>");
    String current = full;
    for (String[] step : new String[][] {
      {null, "ВыводитьЗаголовок"}, {"ВыводитьЗаголовок", "Заголовок"},
      {"Заголовок", "ВыводитьПараметрыДанных"}, {"ВыводитьПараметрыДанных", "ВыводитьОтбор"}}) {
      if (step[0] != null) {
        setParameter(accounting, Map.of("section", "output", "parameter", step[0], "remove", true));
        current = read(accounting);
      }
      setParameter(accounting, Map.of("section", "output", "parameter", "ТипДиаграммы", "value", pie));
      assertThat(read(accounting)).as("ТипДиаграммы перед " + step[1])
        .isEqualTo(insertedBeforeParameter(current, "<dcsset:settings", step[1], chartItem));
      setParameter(accounting, Map.of("section", "output", "parameter", "ТипДиаграммы", "remove", true));
      assertThat(read(accounting)).isEqualTo(current);
    }
  }

  @Test
  void newOutputParameterTakesPlatformPlaceInStructureNode() throws Exception {
    // первая группировка: МакетОформления, ТипЗаголовкаПолей, ВыводитьЗаголовок
    Path dcs = copySchema(ACCOUNTING_CHECK);
    String original = read(dcs);
    List<Map<String, Object>> group = List.of(Map.of("list", "structure", "index", 0));
    String node = "<dcsset:name>ИнформацияОПоследнейПроверке</dcsset:name>";

    // РасположениеИтогов - между МакетОформления и ТипЗаголовкаПолей
    setParameter(dcs, Map.of("section", "output", "path", group, "parameter", "РасположениеИтогов",
      "value", Map.of("type", "dcscor:DataCompositionTotalPlacement", "text", "None")));
    String totals = insertedBeforeParameter(original, node, "ТипЗаголовкаПолей", parameterItem(5,
      "РасположениеИтогов", "<dcscor:value xsi:type=\"dcscor:DataCompositionTotalPlacement\">None</dcscor:value>"));
    assertThat(read(dcs)).isEqualTo(totals);

    // ТипДиаграммы платформа выгружает после ТипЗаголовкаПолей: новый - после него, перед ВыводитьЗаголовок
    setParameter(dcs, Map.of("section", "output", "path", group, "parameter", "ТипДиаграммы",
      "value", Map.of("type", "v8ui:ChartType", "text", "Column3D")));
    String charted = insertedBeforeParameter(totals, node, "ВыводитьЗаголовок", parameterItem(5,
      "ТипДиаграммы", "<dcscor:value xsi:type=\"v8ui:ChartType\">Column3D</dcscor:value>"));
    assertThat(read(dcs)).isEqualTo(charted);

    // КоличествоЗаписей - сразу за МакетОформления, перед только что вставленным РасположениеИтогов
    setParameter(dcs, Map.of("section", "output", "path", group, "parameter", "КоличествоЗаписей", "value", 10));
    String counted = insertedBeforeParameter(charted, node, "РасположениеИтогов", parameterItem(5,
      "КоличествоЗаписей", "<dcscor:value xsi:type=\"xs:decimal\">10</dcscor:value>"));
    assertThat(read(dcs)).isEqualTo(counted);

    // имя вне таблицы рангов - в конец секции
    setParameter(dcs, Map.of("section", "output", "path", group, "parameter", "СвойПараметрВывода", "value", "х"));
    int sectionEnd = counted.indexOf("\t\t\t\t</dcsset:outputParameters>", counted.indexOf(node));
    String unknown = counted.substring(0, sectionEnd)
      + parameterItem(5, "СвойПараметрВывода", "<dcscor:value xsi:type=\"xs:string\">х</dcscor:value>")
      + counted.substring(sectionEnd);
    assertThat(read(dcs)).isEqualTo(unknown);

    for (String parameter : List.of("СвойПараметрВывода", "КоличествоЗаписей", "ТипДиаграммы", "РасположениеИтогов")) {
      setParameter(dcs, Map.of("section", "output", "path", group, "parameter", parameter, "remove", true));
    }
    assertThat(read(dcs)).isEqualTo(original);

    // параметры данных ранги не задают: новый - в конец секции, хотя имя есть в таблице вывода
    setParameter(dcs, Map.of("section", "data", "parameter", "МакетОформления", "value", "х"));
    int dataEnd = original.indexOf("\t\t\t</dcsset:dataParameters>");
    assertThat(read(dcs)).isEqualTo(original.substring(0, dataEnd)
      + parameterItem(4, "МакетОформления", "<dcscor:value xsi:type=\"xs:string\">х</dcscor:value>")
      + original.substring(dataEnd));
  }

  @Test
  void newOutputParametersFollowPlatformExportOrder() throws Exception {
    // Порядок, в котором платформа выгружает 17 параметров вывода корня: загрузка сортирует их сама. Пары
    // строгие: ТипЗаголовкаПолей перед ТипДиаграммы, АвтоПозицияРесурсов перед ФиксацияСлева, та - перед
    // ФиксацияСверху. В каком бы порядке ни добавлять новые параметры, файл совпадает с выгрузкой
    List<String> exported = List.of("МакетОформления", "РасположениеИтогов", "РасположениеПолейГруппировки",
      "РасположениеГруппировки", "РасположениеРеквизитов", "РасположениеРесурсов",
      "ГоризонтальноеРасположениеОбщихИтогов", "ВертикальноеРасположениеОбщихИтогов", "ТипЗаголовкаПолей",
      "ТипДиаграммы", "ВыводитьЗаголовок", "Заголовок", "ВыводитьПараметрыДанных", "ВыводитьОтбор",
      "АвтоПозицияРесурсов", "ФиксацияСлева", "ФиксацияСверху");
    Map<String, Object> values = new LinkedHashMap<>();
    values.put("МакетОформления", "БезОформления");
    values.put("РасположениеИтогов", enumValue("dcscor:DataCompositionTotalPlacement", "None"));
    values.put("РасположениеПолейГруппировки", enumValue("dcsset:DataCompositionGroupFieldsPlacement", "Together"));
    values.put("РасположениеГруппировки", enumValue("dcsset:DataCompositionGroupPlacement", "None"));
    values.put("РасположениеРеквизитов", enumValue("dcsset:DataCompositionAttributesPlacement", "Separately"));
    values.put("РасположениеРесурсов", enumValue("dcsset:DataCompositionResourcesPlacement", "Vertically"));
    values.put("ГоризонтальноеРасположениеОбщихИтогов", enumValue("dcscor:DataCompositionTotalPlacement", "None"));
    values.put("ВертикальноеРасположениеОбщихИтогов", enumValue("dcscor:DataCompositionTotalPlacement", "None"));
    values.put("ТипЗаголовкаПолей", enumValue("dcscor:DataCompositionFieldsTitleType", "Short"));
    values.put("ТипДиаграммы", enumValue("v8ui:ChartType", "Pie"));
    values.put("ВыводитьЗаголовок", enumValue("dcsset:DataCompositionTextOutputType", "Output"));
    values.put("Заголовок", Map.of("type", "v8:LocalStringType", "decoded", "Порядок параметров вывода"));
    values.put("ВыводитьПараметрыДанных", enumValue("dcsset:DataCompositionTextOutputType", "DontOutput"));
    values.put("ВыводитьОтбор", enumValue("dcsset:DataCompositionTextOutputType", "DontOutput"));
    values.put("АвтоПозицияРесурсов", enumValue("dcsset:DataCompositionResourcesAutoPosition", "DontUse"));
    values.put("ФиксацияСлева", enumValue("dcsset:DataCompositionFixation", "DontUse"));
    values.put("ФиксацияСверху", enumValue("dcsset:DataCompositionFixation", "DontUse"));
    assertThat(values.keySet()).containsExactlyElementsOf(exported);

    List<String> reversed = new ArrayList<>(exported);
    Collections.reverse(reversed);
    List<String> shuffled = new ArrayList<>(exported);
    Collections.shuffle(shuffled, new Random(546));
    String expected = null;
    for (List<String> order : List.of(reversed, exported, shuffled,
      // пары по отдельности - в обоих направлениях
      List.of("ТипДиаграммы", "ТипЗаголовкаПолей"), List.of("ТипЗаголовкаПолей", "ТипДиаграммы"),
      List.of("ФиксацияСлева", "АвтоПозицияРесурсов"), List.of("ФиксацияСверху", "ФиксацияСлева"))) {
      Path dcs = copySchema(FILE_DYNAMICS);
      for (String name : order) {
        DcsSettingsEdit.setParameterValue(dcs, SchemaVersion.V2_20, "Дополнительный1",
          Map.of("section", "output", "parameter", name, "value", values.get(name)));
      }
      List<String> names = outputParameters(settings(dcs, "Дополнительный1")).stream()
        .map(parameter -> (String) parameter.get("parameter")).toList();
      assertThat(names).as(order.toString())
        .containsExactlyElementsOf(exported.stream().filter(order::contains).toList());
      if (order.size() == exported.size()) {
        // полный набор пишется одним и тем же файлом при любом порядке добавления
        expected = expected == null ? read(dcs) : expected;
        assertThat(read(dcs)).as(order.toString()).isEqualTo(expected);
        assertThat(DcsXsdValidation.validate(dcs)).containsEntry("ok", true);
      }
    }
  }

  private static Map<String, Object> enumValue(String type, String lexeme) {
    return Map.of("type", type, "text", lexeme);
  }

  // ------------------------------------------------------------------ тип диаграммы

  @Test
  void chartTypeIsWrittenByXsdLexemeWhileSubParametersStayReadOnly() throws Exception {
    Path dcs = copySchema(ORDER_STATUSES);
    String before = read(dcs);

    // тип диаграммы записываемый, под-параметр другого типа v8ui - только для чтения
    Map<String, Object> chartType = parameterByName(chartParameters(dcs), "ТипДиаграммы");
    Map<String, Object> value = map(list(chartType.get("value")).get(0));
    assertThat(value).containsEntry("type", "v8ui:ChartType").containsEntry("text", "Pie3D")
      .doesNotContainKey("opaque");
    Map<String, Object> subParameter = list(chartType.get("items")).get(0);
    assertThat(subParameter).containsEntry("parameter", "ТипДиаграммы.ВидПодписей");
    assertThat(map(list(subParameter.get("value")).get(0)))
      .containsEntry("type", "v8ui:ChartLabelType").containsEntry("opaque", true);

    // эхо прочитанного значения и та же лексема из редактора значений (decoded) - байтовый no-op
    setChartType(dcs, value);
    setChartType(dcs, Map.of("type", "v8ui:ChartType", "decoded", "Pie3D"));
    assertThat(read(dcs)).isEqualTo(before);

    // смена лексемы правит только значение: под-параметр остаётся байтами исходника
    setChartType(dcs, Map.of("type", "v8ui:ChartType", "decoded", "Column"));
    String column = replaceFirst(before,
      "\t\t\t\t\t\t<dcscor:value xsi:type=\"v8ui:ChartType\">Pie3D</dcscor:value>\r\n",
      "\t\t\t\t\t\t<dcscor:value xsi:type=\"v8ui:ChartType\">Column</dcscor:value>\r\n");
    assertThat(read(dcs)).isEqualTo(column);
    assertThat(map(list(parameterByName(chartParameters(dcs), "ТипДиаграммы").get("value")).get(0)))
      .containsEntry("text", "Column");
    assertThat(DcsXsdValidation.validate(dcs)).containsEntry("ok", true);

    // лексема вне перечисления XSD (и другим регистром), значение без лексемы - отказ
    for (Map<String, Object> wrong : List.<Map<String, Object>>of(
      Map.of("type", "v8ui:ChartType", "text", "Pie4D"),
      Map.of("type", "v8ui:ChartType", "decoded", "column"),
      Map.of("type", "v8ui:ChartType"))) {
      assertThatThrownBy(() -> setChartType(dcs, wrong))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("v8ui:ChartType").hasMessageContaining("лексемой");
    }
    // под-параметры только для чтения: и по имени с точкой, и по типу значения
    Map<String, Object> labels = Map.of("type", "v8ui:ChartLabelType", "text", "Value");
    assertThatThrownBy(() -> setParameter(dcs, Map.of("section", "output", "path", CHART_PATH,
      "parameter", "ТипДиаграммы.ВидПодписей", "value", labels)))
      .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("только для чтения");
    assertThatThrownBy(() -> setChartType(dcs, labels))
      .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("только для чтения");
    assertThat(read(dcs)).isEqualTo(column);

    // канал apply-mutation: неизвестная лексема - код 2 без записи, возврат лексемы - к исходнику
    assertThat(applyMutation(chartTypeParams(dcs, "Pie4D"))).isEqualTo(2);
    assertThat(read(dcs)).isEqualTo(column);
    assertThat(applyMutation(chartTypeParams(dcs, "Pie3D"))).isZero();
    assertThat(read(dcs)).isEqualTo(before);
  }

  @Test
  void chartTypeEditGoesThroughEdtTemplateDcs() throws Exception {
    // та же схема в проекте 1С:EDT: Template.dcs без BOM правится тем же каналом
    Path source = Path.of(System.getProperty("fixtures.ssl31edt.root"), "ssl31", "src", "Reports",
      "_ДемоСтатусыЗаказовПокупателей", "Templates", "ОсновнаяСхемаКомпоновкиДанных", "Template.dcs");
    Path dcs = Files.createTempDirectory(tempDir, "dcs").resolve("Template.dcs");
    Files.copy(source, dcs, StandardCopyOption.REPLACE_EXISTING);
    String before = read(dcs);
    assertThat(before).startsWith("<?xml");

    assertThat(applyMutation(chartTypeParams(dcs, "Bar3D"))).isZero();
    String bar = replaceFirst(before, "<dcscor:value xsi:type=\"v8ui:ChartType\">Pie3D</dcscor:value>",
      "<dcscor:value xsi:type=\"v8ui:ChartType\">Bar3D</dcscor:value>");
    assertThat(read(dcs)).isEqualTo(bar).doesNotContain("﻿");

    // новый ТипДиаграммы корня встаёт по порядку платформы и в EDT
    assertThat(applyMutation(Map.of("op", "cf-dcs-set-parameter-value", "objectXml", dcs.toString(),
      "schemaVersion", "V2_20", "variant", "Основной", "payloadJson", new Gson().toJson(Map.of("section", "output",
        "parameter", "ТипДиаграммы", "value", Map.of("type", "v8ui:ChartType", "text", "Line")))))).isZero();
    assertThat(read(dcs)).isEqualTo(insertedBeforeParameter(bar, "<dcsset:settings", "Заголовок",
      parameterItem(4, "ТипДиаграммы", "<dcscor:value xsi:type=\"v8ui:ChartType\">Line</dcscor:value>")));
    assertThat(DcsXsdValidation.validate(dcs)).containsEntry("ok", true);
  }

  // ------------------------------------------------------------------ пустая локализованная строка

  @Test
  void emptyLocalStringEchoIsByteNoOpOnBothCorpora() throws Exception {
    // платформа хранит пустую строку языка самозакрытым <dcscor:value xsi:type="v8:LocalStringType"/>,
    // чтение отдаёт её {type} без decoded, а писатель принимает эту форму. Эхо идёт через JSON канала
    // apply-mutation, как его шлёт webview
    List<String> designer = echoEmptyLocalStrings(
      schemaFiles(Ssl31SubmodulePaths.projectRoot().resolve("src/cf")), "Template.xml");
    List<String> edt = echoEmptyLocalStrings(
      schemaFiles(Path.of(System.getProperty("fixtures.ssl31edt.root"), "ssl31", "src")), "Template.dcs");
    // «Заголовок» корня варианта - в четырёх отчётах, в узлах структуры - ещё у двух обработок
    String exchange = "DataProcessors/ИнтерактивноеИзменениеВыгрузки/Templates/СхемаКомпоновкиДанных ";
    List<String> expected = List.of(
      exchange + "ПользовательскиеДанные [structure#1] Заголовок",
      exchange + "РасшифровкаПоВидуОбъекта [structure#1] Заголовок",
      "DataProcessors/ПереходНаВерсию312/Templates/СхемаКомпоновкиДанных Основной [structure#0] Заголовок",
      "Reports/_ДемоДинамикаИзмененийФайлов/Templates/ОсновнаяСхемаКомпоновкиДанных Основной [] Заголовок",
      "Reports/АнализВерсийОбъектов/Templates/ОсновнаяСхемаКомпоновкиДанных Основной [] Заголовок",
      "Reports/АнализЖурналаРегистрации/Templates/ОсновнаяСхемаКомпоновкиДанных"
        + " ПродолжительностьРаботыРегламентныхЗаданий [] Заголовок",
      "Reports/ДвиженияДокумента/Templates/ПустаяСхемаКомпоновкиДанных Основной [] Заголовок");
    assertThat(designer).containsExactlyElementsOf(expected);
    assertThat(edt).containsExactlyElementsOf(expected);
  }

  @Test
  void newEmptyLocalStringIsWrittenAsItIsRead() throws Exception {
    Path dcs = copySchema(ACCOUNTING_CHECK);
    String before = read(dcs);
    String filled = "\t\t\t\t\t<dcscor:value xsi:type=\"v8:LocalStringType\">\r\n"
      + "\t\t\t\t\t\t<v8:item>\r\n"
      + "\t\t\t\t\t\t\t<v8:lang>ru</v8:lang>\r\n"
      + "\t\t\t\t\t\t\t<v8:content>Контроль ведения учета</v8:content>\r\n"
      + "\t\t\t\t\t\t</v8:item>\r\n"
      + "\t\t\t\t\t</dcscor:value>";
    String emptied = replaceFirst(before, filled, "\t\t\t\t\t<dcscor:value xsi:type=\"v8:LocalStringType\"/>");

    // значение без decoded (форма чтения) - самозакрытый элемент, как у платформы, а не v8:item
    // с пустым v8:content; перечитка отдаёт ту же форму
    setParameter(dcs, Map.of("section", "output", "parameter", "Заголовок",
      "value", Map.of("type", "v8:LocalStringType")));
    assertThat(read(dcs)).isEqualTo(emptied);
    assertThat(list(parameterByName(outputParameters(settings(dcs, "Основной")), "Заголовок").get("value")))
      .containsExactly(Map.of("type", "v8:LocalStringType"));
    assertThat(DcsXsdValidation.validate(dcs)).containsEntry("ok", true);

    // пустой decoded редактора значений - та же форма, повтор файл не трогает
    setParameter(dcs, Map.of("section", "output", "parameter", "Заголовок",
      "value", Map.of("type", "v8:LocalStringType", "decoded", "")));
    assertThat(read(dcs)).isEqualTo(emptied);

    // непустой текст возвращает строку языка конфигурации - байтовый возврат к исходнику
    setParameter(dcs, Map.of("section", "output", "parameter", "Заголовок",
      "value", Map.of("type", "v8:LocalStringType", "decoded", "Контроль ведения учета")));
    assertThat(read(dcs)).isEqualTo(before);

    // ключи других типов без decoded пустой строкой не считаются: молча потерять их нельзя
    for (Map<String, Object> wrong : List.<Map<String, Object>>of(
      Map.of("type", "v8:LocalStringType", "text", "х"),
      Map.of("type", "v8:LocalStringType", "list", Map.of("items", List.of())),
      Map.of("type", "v8:LocalStringType", "decoded", 5))) {
      assertThatThrownBy(() -> setParameter(dcs, Map.of("section", "output", "parameter", "Заголовок",
        "value", wrong)))
        .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("пишется из decoded");
    }
    assertThat(read(dcs)).isEqualTo(before);

    // новый параметр с пустой строкой - тем же самозакрытым элементом; снятие - байтовый возврат
    Path dynamics = copySchema(FILE_DYNAMICS);
    String original = read(dynamics);
    DcsSettingsEdit.setParameterValue(dynamics, SchemaVersion.V2_20, "Дополнительный1",
      Map.of("section", "output", "parameter", "Заголовок", "use", false,
        "value", Map.of("type", "v8:LocalStringType", "decoded", "")));
    assertThat(read(dynamics)).isEqualTo(insertAfterFirst(original, "<dcsset:name>Дополнительный1</dcsset:name>",
      "\r\n\t\t\t</dcsset:selection>\r\n",
      "\t\t\t<dcsset:outputParameters>\r\n"
        + "\t\t\t\t<dcscor:item xsi:type=\"dcsset:SettingsParameterValue\">\r\n"
        + "\t\t\t\t\t<dcscor:use>false</dcscor:use>\r\n"
        + "\t\t\t\t\t<dcscor:parameter>Заголовок</dcscor:parameter>\r\n"
        + "\t\t\t\t\t<dcscor:value xsi:type=\"v8:LocalStringType\"/>\r\n"
        + "\t\t\t\t</dcscor:item>\r\n"
        + "\t\t\t</dcsset:outputParameters>\r\n"));
    assertThat(DcsXsdValidation.validate(dynamics)).containsEntry("ok", true);
    DcsSettingsEdit.setParameterValue(dynamics, SchemaVersion.V2_20, "Дополнительный1",
      Map.of("section", "output", "parameter", "Заголовок", "remove", true));
    assertThat(read(dynamics)).isEqualTo(original);
  }

  @Test
  void appearanceTextWithEmptyLocalStringIsRewrittenInPlace() throws Exception {
    // «Текст» оформления с пустой строкой: правка его use переписывает элемент оформления, и
    // значение пишется той же самозакрытой формой; use: true платформа не хранит - строка
    // use=false снимается
    Path dcs = copySchema(STALLED_TASKS);
    String before = read(dcs);
    List<Map<String, Object>> items =
      list(map(settings(dcs, STALLED_TASKS_VARIANT).get("conditionalAppearance")).get("items"));
    int index = -1;
    for (int i = 0; i < items.size() && index < 0; i++) {
      for (Map<String, Object> parameter : list(items.get(i).getOrDefault("appearance", List.of()))) {
        if ("Текст".equals(parameter.get("parameter"))
          && List.of(Map.of("type", "v8:LocalStringType")).equals(parameter.get("value"))) {
          index = i;
        }
      }
    }
    assertThat(index).isNotNegative();
    Map<String, Object> item = GSON.fromJson(GSON.toJson(items.get(index)), MAP_TYPE);
    for (Map<String, Object> parameter : list(item.get("appearance"))) {
      if ("Текст".equals(parameter.get("parameter"))) {
        parameter.put("use", true);
      }
    }
    DcsSettingsEdit.setAppearanceItem(dcs, SchemaVersion.V2_20, STALLED_TASKS_VARIANT,
      Map.of("path", List.of(), "index", index, "item", item));
    String used = replaceFirst(before,
      "\t\t\t\t\t\t\t<dcscor:use>false</dcscor:use>\r\n"
        + "\t\t\t\t\t\t\t<dcscor:parameter>Текст</dcscor:parameter>\r\n"
        + "\t\t\t\t\t\t\t<dcscor:value xsi:type=\"v8:LocalStringType\"/>\r\n",
      "\t\t\t\t\t\t\t<dcscor:parameter>Текст</dcscor:parameter>\r\n"
        + "\t\t\t\t\t\t\t<dcscor:value xsi:type=\"v8:LocalStringType\"/>\r\n");
    assertThat(read(dcs)).isEqualTo(used);
    assertThat(DcsXsdValidation.validate(dcs)).containsEntry("ok", true);
  }

  @Test
  void appearanceUseChangeKeepsLanguagesOfValue() throws Exception {
    // Правка use у «Текста» оформления переписывает элемент оформления, а писатель строки языка
    // пишет один язык конфигурации, поэтому значение, равное прочитанному, переносится дословно:
    // иначе значение на нескольких языках потеряло бы остальные языки. Формы нет в корпусах - она
    // собрана из пустого «Текста» схемы зависших задач
    Path dcs = copySchema(STALLED_TASKS);
    String empty = "\t\t\t\t\t\t\t<dcscor:use>false</dcscor:use>\r\n"
      + "\t\t\t\t\t\t\t<dcscor:parameter>Текст</dcscor:parameter>\r\n"
      + "\t\t\t\t\t\t\t<dcscor:value xsi:type=\"v8:LocalStringType\"/>\r\n";
    String languages = "\t\t\t\t\t\t\t<dcscor:value xsi:type=\"v8:LocalStringType\">\r\n"
      + "\t\t\t\t\t\t\t\t<v8:item>\r\n"
      + "\t\t\t\t\t\t\t\t\t<v8:lang>ru</v8:lang>\r\n"
      + "\t\t\t\t\t\t\t\t\t<v8:content>Текст РУ</v8:content>\r\n"
      + "\t\t\t\t\t\t\t\t</v8:item>\r\n"
      + "\t\t\t\t\t\t\t\t<v8:item>\r\n"
      + "\t\t\t\t\t\t\t\t\t<v8:lang>en</v8:lang>\r\n"
      + "\t\t\t\t\t\t\t\t\t<v8:content>Text EN</v8:content>\r\n"
      + "\t\t\t\t\t\t\t\t</v8:item>\r\n"
      + "\t\t\t\t\t\t\t</dcscor:value>\r\n";
    String head = "\t\t\t\t\t\t\t<dcscor:parameter>Текст</dcscor:parameter>\r\n";
    String text = replaceFirst(read(dcs), empty, empty.replace(
      "\t\t\t\t\t\t\t<dcscor:value xsi:type=\"v8:LocalStringType\"/>\r\n", languages));
    Files.writeString(dcs, text, StandardCharsets.UTF_8);
    List<Map<String, Object>> items =
      list(map(settings(dcs, STALLED_TASKS_VARIANT).get("conditionalAppearance")).get("items"));
    int index = -1;
    for (int i = 0; i < items.size() && index < 0; i++) {
      for (Map<String, Object> parameter : list(items.get(i).getOrDefault("appearance", List.of()))) {
        if ("Текст".equals(parameter.get("parameter")) && List.of(
          Map.of("type", "v8:LocalStringType", "decoded", "Текст РУ")).equals(parameter.get("value"))) {
          index = i;
        }
      }
    }
    assertThat(index).isNotNegative();
    String json = GSON.toJson(items.get(index));

    // эхо элемента целиком: ни байта
    DcsSettingsEdit.setAppearanceItem(dcs, SchemaVersion.V2_20, STALLED_TASKS_VARIANT,
      Map.of("path", List.of(), "index", index, "item", GSON.fromJson(json, MAP_TYPE)));
    assertThat(read(dcs)).isEqualTo(text);

    // правка одного use: снимается строка use (use=true платформа не хранит),
    // оба языка значения остаются
    Map<String, Object> item = GSON.fromJson(json, MAP_TYPE);
    for (Map<String, Object> parameter : list(item.get("appearance"))) {
      if ("Текст".equals(parameter.get("parameter"))) {
        parameter.put("use", true);
      }
    }
    DcsSettingsEdit.setAppearanceItem(dcs, SchemaVersion.V2_20, STALLED_TASKS_VARIANT,
      Map.of("path", List.of(), "index", index, "item", item));
    String used = replaceFirst(text, "\t\t\t\t\t\t\t<dcscor:use>false</dcscor:use>\r\n" + head + languages,
      head + languages);
    assertThat(read(dcs)).isEqualTo(used);
    assertThat(DcsXsdValidation.validate(dcs)).containsEntry("ok", true);

    // настоящая правка текста пишет строку одного языка конфигурации
    for (Map<String, Object> parameter : list(item.get("appearance"))) {
      if ("Текст".equals(parameter.get("parameter"))) {
        parameter.put("value", List.of(Map.of("type", "v8:LocalStringType", "decoded", "Новый текст")));
      }
    }
    DcsSettingsEdit.setAppearanceItem(dcs, SchemaVersion.V2_20, STALLED_TASKS_VARIANT,
      Map.of("path", List.of(), "index", index, "item", item));
    assertThat(read(dcs)).isEqualTo(replaceFirst(used, languages,
      "\t\t\t\t\t\t\t<dcscor:value xsi:type=\"v8:LocalStringType\">\r\n"
        + "\t\t\t\t\t\t\t\t<v8:item>\r\n"
        + "\t\t\t\t\t\t\t\t\t<v8:lang>ru</v8:lang>\r\n"
        + "\t\t\t\t\t\t\t\t\t<v8:content>Новый текст</v8:content>\r\n"
        + "\t\t\t\t\t\t\t\t</v8:item>\r\n"
        + "\t\t\t\t\t\t\t</dcscor:value>\r\n"));
  }

  @Test
  void localStringEchoKeepsFormsTheWriterCannotReproduce() throws Exception {
    // Писатель строки языка пишет один язык конфигурации, поэтому значение, глубоко равное прочитанному,
    // не переписывается: иначе эхо значения на нескольких языках потеряло бы остальные языки, а
    // «ru пусто + en» превратилось бы в ru с текстом en. Форм нет в корпусах - они собраны из «Заголовка»
    // схемы проверки учёта
    String filled = "\t\t\t\t\t<dcscor:value xsi:type=\"v8:LocalStringType\">\r\n"
      + "\t\t\t\t\t\t<v8:item>\r\n"
      + "\t\t\t\t\t\t\t<v8:lang>ru</v8:lang>\r\n"
      + "\t\t\t\t\t\t\t<v8:content>Контроль ведения учета</v8:content>\r\n"
      + "\t\t\t\t\t\t</v8:item>\r\n"
      + "\t\t\t\t\t</dcscor:value>";
    String english = "\t\t\t\t\t\t<v8:item>\r\n"
      + "\t\t\t\t\t\t\t<v8:lang>en</v8:lang>\r\n"
      + "\t\t\t\t\t\t\t<v8:content>Accounting check</v8:content>\r\n"
      + "\t\t\t\t\t\t</v8:item>\r\n";
    String ruItem = "\t\t\t\t\t\t<v8:item>\r\n"
      + "\t\t\t\t\t\t\t<v8:lang>ru</v8:lang>\r\n"
      + "\t\t\t\t\t\t\t<v8:content>Контроль ведения учета</v8:content>\r\n"
      + "\t\t\t\t\t\t</v8:item>\r\n";
    Map<String, String> forms = new LinkedHashMap<>();
    forms.put("ru и en", filled.replace("\t\t\t\t\t</dcscor:value>", english + "\t\t\t\t\t</dcscor:value>"));
    forms.put("только en", filled.replace(ruItem, english));
    forms.put("пустой v8:content", filled.replace("<v8:content>Контроль ведения учета</v8:content>", "<v8:content/>"));

    for (Map.Entry<String, String> form : forms.entrySet()) {
      Path dcs = copySchema(ACCOUNTING_CHECK);
      String text = replaceFirst(read(dcs), filled, form.getValue());
      Files.writeString(dcs, text, StandardCharsets.UTF_8);
      Map<String, Object> parameter = parameterByName(outputParameters(settings(dcs, "Основной")), "Заголовок");
      Object value = list(parameter.get("value")).get(0);

      // эхо через JSON канала, как его шлёт webview: ни байта
      assertThat(applyMutation(Map.of("op", "cf-dcs-set-parameter-value", "objectXml", dcs.toString(),
        "schemaVersion", "V2_20", "variant", "Основной", "payloadJson", GSON.toJson(Map.of(
          "section", "output", "parameter", "Заголовок", "use", false, "value", value)))))
        .as(form.getKey()).isZero();
      assertThat(read(dcs)).as(form.getKey()).isEqualTo(text);

      // правка флага с тем же значением снимает одну строку use (true платформа не хранит),
      // значение переносится дословно
      setParameter(dcs, GSON.fromJson(GSON.toJson(Map.of(
        "section", "output", "parameter", "Заголовок", "use", true, "value", value)), MAP_TYPE));
      String used = replaceFirst(text, "\t\t\t\t\t<dcscor:use>false</dcscor:use>\r\n"
        + "\t\t\t\t\t<dcscor:parameter>Заголовок</dcscor:parameter>",
        "\t\t\t\t\t<dcscor:parameter>Заголовок</dcscor:parameter>");
      assertThat(read(dcs)).as(form.getKey()).isEqualTo(used);
      assertThat(DcsXsdValidation.validate(dcs)).as(form.getKey()).containsEntry("ok", true);

      // настоящая правка текста пишет строку одного языка конфигурации
      setParameter(dcs, Map.of("section", "output", "parameter", "Заголовок",
        "value", Map.of("type", "v8:LocalStringType", "decoded", "Новый заголовок")));
      assertThat(read(dcs)).as(form.getKey()).isEqualTo(replaceFirst(used, form.getValue(),
        filled.replace("Контроль ведения учета", "Новый заголовок")));
    }
  }

  // ------------------------------------------------------------------ отказы

  @Test
  void refusalsKeepFileIntact() throws Exception {
    Path dcs = copySchema(ACCOUNTING_CHECK);
    String before = Files.readString(dcs, StandardCharsets.UTF_8);

    // имя с точкой адресует под-параметр: они доступны только для чтения
    assertThatThrownBy(() -> setParameter(dcs, Map.of("section", "output",
      "parameter", "ТипДиаграммы.ВидПодписей", "use", true)))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("од-параметр").hasMessageContaining("только для чтения");

    // параметры данных есть только у корня настроек
    assertThatThrownBy(() -> setParameter(dcs, Map.of("section", "data",
      "path", List.of(Map.of("list", "structure", "index", 0)), "parameter", "Период", "use", true)))
      .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("только у корня");

    // лексема секции, обязательность полей и формы значений
    assertThatThrownBy(() -> setParameter(dcs, Map.of("section", "both", "parameter", "Период")))
      .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("data или output");
    assertThatThrownBy(() -> setParameter(dcs, Map.of("parameter", "Период")))
      .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("section");
    assertThatThrownBy(() -> setParameter(dcs, Map.of("section", "output")))
      .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("parameter");
    assertThatThrownBy(() -> setParameter(dcs, Map.of("section", "output", "parameter", "Заголовок",
      "remove", true, "use", false)))
      .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("не сочетается");
    assertThatThrownBy(() -> setParameter(dcs, Map.of("section", "output", "parameter", "Нет",
      "remove", true)))
      .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("не найден");
    assertThatThrownBy(() -> setParameter(dcs, Map.of("section", "output", "parameter", "Заголовок",
      "value", List.of(Map.of("type", "xs:string", "text", "х")))))
      .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("скаляром");
    assertThatThrownBy(() -> setParameter(dcs, Map.of("section", "output", "parameter", "Заголовок",
      "use", "да")))
      .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("true или false");
    assertThatThrownBy(() -> setParameter(dcs, Map.of("section", "output", "parameter", "Заголовок",
      "uuid", "х")))
      .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Неизвестное свойство");
    assertThatThrownBy(() -> setParameter(dcs, Map.of("section", "output", "parameter", "Заголовок",
      "value", Map.of("text", "х"))))
      .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("не задан type");

    // значение типа вне белого списка записи
    assertThatThrownBy(() -> setParameter(dcs, Map.of("section", "output", "parameter", "Заголовок",
      "value", Map.of("type", "v8ui:Font", "attrs", Map.of("faceName", "Arial")))))
      .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("только для чтения");

    assertThat(Files.readString(dcs, StandardCharsets.UTF_8)).isEqualTo(before);
  }

  // ------------------------------------------------------------------ канал apply-mutation

  @Test
  void applyMutationChannelRunsSetParameterValue() throws Exception {
    Path dcs = copySchema(ACCOUNTING_CHECK);
    String before = Files.readString(dcs, StandardCharsets.UTF_8);

    // отказ доходит кодом 2, файл не меняется: имя с точкой
    assertThat(applyMutation(Map.of(
      "op", "cf-dcs-set-parameter-value", "objectXml", dcs.toString(), "schemaVersion", "V2_20",
      "variant", "Основной",
      "payloadJson", "{\"section\":\"output\",\"parameter\":\"ТипДиаграммы.ВидПодписей\",\"use\":true}")))
      .isEqualTo(2);
    assertThat(Files.readString(dcs, StandardCharsets.UTF_8)).isEqualTo(before);

    // не-ASCII значение через UTF-8 JSON-канал
    assertThat(applyMutation(Map.of(
      "op", "cf-dcs-set-parameter-value", "objectXml", dcs.toString(), "schemaVersion", "V2_20",
      "variant", "Основной",
      "payloadJson", new Gson().toJson(Map.of("section", "output", "path", List.of(),
        "parameter", "Заголовок",
        "value", Map.of("type", "v8:LocalStringType", "decoded", "Сводка")))))).isZero();
    String titled = replaceFirst(before,
      "\t\t\t\t\t\t\t<v8:content>Контроль ведения учета</v8:content>",
      "\t\t\t\t\t\t\t<v8:content>Сводка</v8:content>");
    assertThat(Files.readString(dcs, StandardCharsets.UTF_8)).isEqualTo(titled);

    // явный value: null в JSON канала пишет xsi:nil
    assertThat(applyMutation(Map.of(
      "op", "cf-dcs-set-parameter-value", "objectXml", dcs.toString(), "schemaVersion", "V2_20",
      "variant", "Основной",
      "payloadJson", "{\"section\":\"output\",\"parameter\":\"Заголовок\",\"value\":null}")))
      .isZero();
    assertThat(Files.readString(dcs, StandardCharsets.UTF_8)).isEqualTo(replaceFirst(titled,
      "\t\t\t\t\t<dcscor:value xsi:type=\"v8:LocalStringType\">\r\n"
        + "\t\t\t\t\t\t<v8:item>\r\n"
        + "\t\t\t\t\t\t\t<v8:lang>ru</v8:lang>\r\n"
        + "\t\t\t\t\t\t\t<v8:content>Сводка</v8:content>\r\n"
        + "\t\t\t\t\t\t</v8:item>\r\n"
        + "\t\t\t\t\t</dcscor:value>",
      "\t\t\t\t\t<dcscor:value xsi:nil=\"true\"/>"));
  }

  // ------------------------------------------------------------------ помощники

  /**
   * Эхо каждого параметра вывода с пустой строкой языка ({@code value} - ровно
   * {@code [{type: v8:LocalStringType}]}) во всех вариантах, в корне и узлах структуры, через
   * JSON канала apply-mutation; файл после всех эхо обязан совпасть байт в байт.
   *
   * @return строки «каталог макета, вариант, путь, параметр» по порядку обхода
   */
  private List<String> echoEmptyLocalStrings(List<Path> schemas, String fileName) throws Exception {
    List<String> echoed = new ArrayList<>();
    int index = 0;
    for (Path schema : schemas) {
      if (!read(schema).contains("xsi:type=\"v8:LocalStringType\"/>")) {
        continue;
      }
      Path copy = Files.createTempDirectory(tempDir, "echo" + index++).resolve(fileName);
      Files.copy(schema, copy);
      byte[] before = Files.readAllBytes(copy);
      String owner = templateDir(schema);
      for (Map<String, Object> variant : list(DcsSettingsRead.info(copy, SchemaVersion.V2_20, null).get("variants"))) {
        String name = (String) variant.get("name");
        echoNode(copy, name, List.of(), settings(copy, name), owner, echoed);
      }
      assertThat(Files.readAllBytes(copy)).as(schema.toString()).isEqualTo(before);
    }
    return echoed;
  }

  /** Эхо параметров вывода узла и рекурсивно - его вложенных узлов. */
  private void echoNode(Path dcs, String variant, List<Map<String, Object>> path, Map<String, Object> node,
    String owner, List<String> echoed) throws Exception {
    if (node.get("outputParameters") instanceof Map<?, ?> section) {
      for (Map<String, Object> parameter : list(section.get("items"))) {
        if (!List.of(Map.of("type", "v8:LocalStringType")).equals(parameter.get("value"))) {
          continue;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("section", "output");
        payload.put("path", path);
        payload.put("parameter", parameter.get("parameter"));
        if (parameter.get("use") != null) {
          payload.put("use", parameter.get("use"));
        }
        payload.put("value", list(parameter.get("value")).get(0));
        assertThat(applyMutation(Map.of("op", "cf-dcs-set-parameter-value", "objectXml", dcs.toString(),
          "schemaVersion", "V2_20", "variant", variant, "payloadJson", GSON.toJson(payload))))
          .as(owner + " " + variant + " " + path + " " + parameter.get("parameter")).isZero();
        echoed.add(owner + " " + variant + " " + pathLabel(path) + " " + parameter.get("parameter"));
      }
    }
    for (String key : NODE_LISTS) {
      if (!(node.get(key) instanceof List<?> children)) {
        continue;
      }
      for (int i = 0; i < children.size(); i++) {
        List<Map<String, Object>> childPath = new ArrayList<>(path);
        childPath.add(Map.of("list", key, "index", i));
        echoNode(dcs, variant, childPath, map(children.get(i)), owner, echoed);
      }
    }
  }

  /** Путь узла коротко: {@code [structure#0, columns#1]}. */
  private static String pathLabel(List<Map<String, Object>> path) {
    return path.stream().map(step -> step.get("list") + "#" + step.get("index")).toList().toString();
  }

  /**
   * Каталог макета от вида объекта: {@code Reports/Х/Templates/Y} - одинаково у выгрузки
   * ({@code …/Y/Ext/Template.xml}) и проекта EDT ({@code …/Y/Template.dcs}).
   */
  private static String templateDir(Path schema) {
    Path dir = schema.getParent();
    if ("Ext".equals(dir.getFileName().toString())) {
      dir = dir.getParent();
    }
    int count = dir.getNameCount();
    return dir.subpath(count - 4, count).toString().replace('\\', '/');
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

  /** Правка параметра варианта «Основной» прямым вызовом. */
  private void setParameter(Path dcs, Map<String, Object> payload) throws Exception {
    DcsSettingsEdit.setParameterValue(dcs, SchemaVersion.V2_20, "Основной", payload);
  }

  /** Настройки варианта из cf-dcs-settings-info. */
  private static Map<String, Object> settings(Path dcs, String variant) throws Exception {
    Map<String, Object> info = DcsSettingsRead.info(dcs, SchemaVersion.V2_20, variant);
    return map(map(list(info.get("variants")).get(0)).get("settings"));
  }

  /** Значения параметров вывода корня настроек. */
  private static List<Map<String, Object>> outputParameters(Map<String, Object> settings) {
    return list(map(settings.get("outputParameters")).get("items"));
  }

  /** Параметры вывода диаграммы варианта «Основной» схемы {@link #ORDER_STATUSES}. */
  private static List<Map<String, Object>> chartParameters(Path dcs) throws Exception {
    Map<String, Object> chart = list(settings(dcs, "Основной").get("structure")).get(0);
    assertThat(chart).containsEntry("kind", "chart");
    return outputParameters(chart);
  }

  /** Правка ТипДиаграммы диаграммы варианта «Основной» прямым вызовом. */
  private void setChartType(Path dcs, Map<String, Object> value) throws Exception {
    setParameter(dcs, Map.of("section", "output", "path", CHART_PATH, "parameter", "ТипДиаграммы", "value", value));
  }

  /** Параметры канала apply-mutation: ТипДиаграммы диаграммы «Основного» лексемой из редактора. */
  private static Map<String, Object> chartTypeParams(Path dcs, String lexeme) {
    return Map.of("op", "cf-dcs-set-parameter-value", "objectXml", dcs.toString(), "schemaVersion", "V2_20",
      "variant", "Основной", "payloadJson", new Gson().toJson(Map.of("section", "output", "path", CHART_PATH,
        "parameter", "ТипДиаграммы", "value", Map.of("type", "v8ui:ChartType", "decoded", lexeme))));
  }

  /** Минимальный элемент параметра, как его пишет правка: имя и значение строками файла с CRLF. */
  private static String parameterItem(int depth, String parameter, String value) {
    String tabs = "\t".repeat(depth);
    return tabs + "<dcscor:item xsi:type=\"dcsset:SettingsParameterValue\">\r\n"
      + tabs + "\t<dcscor:parameter>" + parameter + "</dcscor:parameter>\r\n"
      + tabs + "\t" + value + "\r\n"
      + tabs + "</dcscor:item>\r\n";
  }

  /**
   * Ожидание вставки: блок строк встаёт перед элементом параметра {@code parameter} - первым
   * после метки {@code from}.
   */
  private static String insertedBeforeParameter(String text, String from, String parameter, String block) {
    int anchor = text.indexOf(from);
    assertThat(anchor).isNotNegative();
    int at = text.indexOf("<dcscor:parameter>" + parameter + "</dcscor:parameter>", anchor);
    assertThat(at).isPositive();
    int lineStart = text.lastIndexOf('\n', text.lastIndexOf("<dcscor:item ", at)) + 1;
    return text.substring(0, lineStart) + block + text.substring(lineStart);
  }

  private static String read(Path dcs) throws Exception {
    return Files.readString(dcs, StandardCharsets.UTF_8);
  }

  /** Первый параметр с данным именем; нет такого - тест падает. */
  private static Map<String, Object> parameterByName(List<Map<String, Object>> items, String name) {
    for (Map<String, Object> item : items) {
      if (name.equals(item.get("parameter"))) {
        return item;
      }
    }
    throw new AssertionError("в секции нет параметра " + name);
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

  /** Ожидание: заменено только первое вхождение подстроки. */
  private static String replaceFirst(String text, String target, String replacement) {
    int at = text.indexOf(target);
    assertThat(at).isNotNegative();
    return text.substring(0, at) + replacement + text.substring(at + target.length());
  }

  /** Ожидание: блок вставлен после первого вхождения якоря, идущего после метки from. */
  private static String insertAfterFirst(String text, String from, String anchor, String block) {
    int at = text.indexOf(from);
    assertThat(at).isNotNegative();
    int anchorAt = text.indexOf(anchor, at);
    assertThat(anchorAt).isNotNegative();
    int to = anchorAt + anchor.length();
    return text.substring(0, to) + block + text.substring(to);
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
}
