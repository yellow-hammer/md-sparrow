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
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

/**
 * Параметры вывода, которых не хранят таблица, диаграмма и их группы ({@code cf-dcs-set-parameter-value}).
 * Загрузка платформы на каждом из этих уровней молча отбрасывает часть из 22 имён таблицы рангов, а
 * оставшиеся выгружает по таблице рангов. Запись отказывает в отбрасываемых так же, как в корне настроек и у
 * группировки структуры: отказ кодом 2 канала без записи, эхо элемента, уже записанного в файле, - без правки
 * и без отказа, снятие разрешено. Вложенные группы строк, колонок, точек и серий ограничены так же, как
 * группы верхнего уровня.
 */
class DcsSettingsOutputLevelsTest {

  /** Вариант «Основной» с группировками, таблицей и параметрами вывода корня. */
  private static final String ACCOUNTING_CHECK =
    "src/cf/Reports/РезультатыПроверкиУчета/Templates/ОсновнаяСхемаКомпоновкиДанных/Ext/Template.xml";

  private static final String VARIANT = "Основной";

  /** Списки узлов структуры, по которым обход доходит до вложенных узлов. */
  private static final List<String> NODE_LISTS = List.of("structure", "columns", "rows", "points", "series");

  /** Обе группы имён равного ранга и {@code ФиксацияСлева} между ними, в порядке записи. */
  private static final List<String> PAIR_NAMES = List.of("ГоризонтальноеРасположениеОбщихИтогов",
    "РасположениеОбщихИтогов", "ФиксацияСлева", "ВариантИспользованияГруппировки", "ФиксацияСверху");

  private static final Gson GSON = new Gson();

  @TempDir Path tempDir;

  /**
   * Уровень настроек: название, место в тексте отказа, пути узлов в схеме {@link #fixture()} и имена, которые
   * платформа выгружает после записи всех 22 имён и после записи {@link #PAIR_NAMES}.
   */
  private record Level(String name, String where, List<List<Map<String, Object>>> paths, List<String> exported,
    List<String> pairsExported) {
  }

  // ------------------------------------------------------------------ имена таблицы рангов на каждом уровне

  @Test
  void writingRankedNamesGivesWhatPlatformExportsAtEveryLevel() throws Exception {
    // Все 22 имени в обратном порядке и имена пар в порядке записи: запись отказывает ровно в том, что
    // загрузка отбрасывает, а записанное ложится в порядке её выгрузки; вложенные группы - как верхние
    List<String> reversed = new ArrayList<>(values().keySet());
    Collections.reverse(reversed);
    Path fixture = fixture();
    for (Level level : levels()) {
      for (List<Map<String, Object>> path : level.paths()) {
        String where = level.name() + " " + path;
        for (List<String> written : List.of(reversed, PAIR_NAMES)) {
          Path dcs = copy(fixture);
          List<String> refused = new ArrayList<>();
          for (String name : written) {
            try {
              setParameter(dcs, Map.of("section", "output", "path", path, "parameter", name,
                "value", values().get(name)));
            } catch (IllegalArgumentException e) {
              assertThat(e.getMessage()).as(where + " " + name)
                .startsWith("Параметр вывода «" + name + "»: платформа не хранит этот параметр вывода " + level.where());
              refused.add(name);
            }
          }
          List<String> exported = written == PAIR_NAMES ? level.pairsExported() : level.exported();
          assertThat(outputNames(dcs, path)).as(where + " " + written.size()).containsExactlyElementsOf(exported);
          assertThat(refused).as(where + " " + written.size())
            .containsExactlyElementsOf(written.stream().filter(name -> !exported.contains(name)).toList());
          assertThat(DcsXsdValidation.validate(dcs)).as(where).containsEntry("ok", true);
        }
      }
    }
  }

  @Test
  void refusalTextsNameLevelAndWhereParameterIsKept() throws Exception {
    Path dcs = fixture();
    Map<String, List<Map<String, Object>>> paths = new LinkedHashMap<>();
    for (Level level : levels()) {
      paths.put(level.name(), level.paths().get(0));
    }
    Map<String[], String> expected = new LinkedHashMap<>();
    expected.put(new String[] {"таблица", "ТипМакета"}, "Параметр вывода «ТипМакета»: платформа не хранит этот"
      + " параметр вывода у таблицы; он хранится только у группировки структуры.");
    expected.put(new String[] {"таблица", "РасположениеОбщихИтогов"}, "Параметр вывода «РасположениеОбщихИтогов»:"
      + " платформа не хранит этот параметр вывода у таблицы; он хранится только у группы таблицы.");
    expected.put(new String[] {"группа таблицы", "ТипДиаграммы"}, "Параметр вывода «ТипДиаграммы»: платформа не"
      + " хранит этот параметр вывода у группы строк или колонок таблицы; он хранится в корне настроек,"
      + " у группировки структуры и у диаграммы.");
    expected.put(new String[] {"диаграмма", "МакетОформления"}, "Параметр вывода «МакетОформления»: платформа не"
      + " хранит этот параметр вывода у диаграммы; он хранится в корне настроек, у группировки структуры,"
      + " у таблицы и у группы таблицы.");
    expected.put(new String[] {"группа диаграммы", "Заголовок"}, "Параметр вывода «Заголовок»: платформа не хранит"
      + " этот параметр вывода у группы точек или серий диаграммы; он хранится в корне настроек, у группировки"
      + " структуры, у таблицы, у группы таблицы и у диаграммы.");
    expected.put(new String[] {"группа диаграммы", "ФиксацияСверху"}, "Параметр вывода «ФиксацияСверху»: платформа"
      + " не хранит этот параметр вывода у группы точек или серий диаграммы; он хранится только в корне настроек.");
    for (Map.Entry<String[], String> entry : expected.entrySet()) {
      String name = entry.getKey()[1];
      assertThatThrownBy(() -> setParameter(dcs, Map.of("section", "output", "path", paths.get(entry.getKey()[0]),
        "parameter", name, "value", values().get(name))))
        .isInstanceOf(IllegalArgumentException.class).hasMessage(entry.getValue());
    }
  }

  // ------------------------------------------------------------------ запись, снятие и отказы

  @Test
  void forbiddenNamesAreRefusedAndKeptNamesWrittenAndRemovedAtEveryLevel() throws Exception {
    Path dcs = fixture();
    String before = read(dcs);
    List<String> names = new ArrayList<>(values().keySet());
    names.add("СвойПараметрВывода");
    for (Level level : levels()) {
      for (List<Map<String, Object>> path : level.paths()) {
        boolean channelChecked = false;
        for (String name : names) {
          String where = level.name() + " " + path + " " + name;
          Object value = values().getOrDefault(name, "х");
          if (!level.exported().contains(name) && values().containsKey(name)) {
            // и со значением, и одним use - отказ без записи
            assertThatThrownBy(() -> setParameter(dcs, Map.of("section", "output", "path", path, "parameter", name,
              "value", value)))
              .as(where).isInstanceOf(IllegalArgumentException.class)
              .hasMessageContaining("«" + name + "»").hasMessageContaining(level.where() + "; он хранится");
            assertThatThrownBy(() -> setParameter(dcs, Map.of("section", "output", "path", path, "parameter", name,
              "use", false)))
              .as(where).isInstanceOf(IllegalArgumentException.class).hasMessageContaining(level.where());
            if (!channelChecked) {
              assertThat(applyMutation(Map.of("op", "cf-dcs-set-parameter-value", "objectXml", dcs.toString(),
                "schemaVersion", "V2_20", "variant", VARIANT, "payloadJson", GSON.toJson(Map.of(
                  "section", "output", "path", path, "parameter", name, "value", value))))).as(where).isEqualTo(2);
              channelChecked = true;
            }
            assertThat(read(dcs)).as(where).isEqualTo(before);
            continue;
          }
          // хранимое уровнем и имя вне таблицы рангов - пишется и снимается байтовым возвратом
          setParameter(dcs, Map.of("section", "output", "path", path, "parameter", name, "value", value));
          assertThat(outputNames(dcs, path)).as(where).containsExactly(name);
          setParameter(dcs, Map.of("section", "output", "path", path, "parameter", name, "remove", true));
          assertThat(read(dcs)).as(where).isEqualTo(before);
        }
        assertThat(channelChecked).as(level.name() + " " + path).isTrue();
      }
    }
  }

  @Test
  void forbiddenItemInFileEchoIsNoOpChangeIsRefusedAndRemoveWorks() throws Exception {
    // Элемент, который уже есть в файле на уровне, где платформа его не хранит (ручная правка): эхо - без записи
    // и без отказа, правка значения или use - отказ, remove - снимает. Элемент собран переименованием хранимого
    // соседа того же типа значения
    Path fixture = fixture();
    Map<String, Object> textOutput = Map.of("type", "dcsset:DataCompositionTextOutputType", "text", "DontOutput");
    Map<String, String[]> renames = new LinkedHashMap<>();
    renames.put("таблица", new String[] {"ВыводитьЗаголовок", "ВыводитьОтбор"});
    renames.put("группа таблицы", new String[] {"ВыводитьОтбор", "ВыводитьПараметрыДанных"});
    renames.put("диаграмма", new String[] {"ВыводитьЗаголовок", "ВыводитьОтбор"});
    renames.put("группа диаграммы", new String[] {"КоличествоЗаписей", "ВыводитьОтбор"});
    for (Level level : levels()) {
      for (List<Map<String, Object>> path : level.paths()) {
        String[] rename = renames.get(level.name());
        String where = level.name() + " " + path + " " + rename[1];
        Path dcs = copy(fixture);
        String before = read(dcs);
        setParameter(dcs, Map.of("section", "output", "path", path, "parameter", rename[0], "value", textOutput));
        String text = read(dcs);
        int start = DcsSettingsEdit.resolveNode(text, VARIANT, steps(path)).element().start();
        String from = "<dcscor:parameter>" + rename[0] + "</dcscor:parameter>";
        int at = text.indexOf(from, start);
        String legacy = text.substring(0, at) + "<dcscor:parameter>" + rename[1] + "</dcscor:parameter>"
          + text.substring(at + from.length());
        Files.writeString(dcs, legacy, StandardCharsets.UTF_8);
        assertThat(outputNames(dcs, path)).as(where).containsExactly(rename[1]);

        // эхо значения и use: true (умолчание, элемента use нет) - ни байта, в том числе каналом
        Map<String, Object> payload = Map.of("section", "output", "path", path, "parameter", rename[1]);
        setParameter(dcs, with(payload, "value", textOutput));
        setParameter(dcs, with(payload, "use", true));
        assertThat(applyMutation(Map.of("op", "cf-dcs-set-parameter-value", "objectXml", dcs.toString(),
          "schemaVersion", "V2_20", "variant", VARIANT, "payloadJson", GSON.toJson(with(payload, "value",
            textOutput))))).as(where).isZero();
        assertThat(read(dcs)).as(where).isEqualTo(legacy);
        // правка - отказ
        assertThatThrownBy(() -> setParameter(dcs, with(payload, "value",
          Map.of("type", "dcsset:DataCompositionTextOutputType", "text", "Output"))))
          .as(where).isInstanceOf(IllegalArgumentException.class).hasMessageContaining(level.where());
        assertThatThrownBy(() -> setParameter(dcs, with(payload, "use", false)))
          .as(where).isInstanceOf(IllegalArgumentException.class).hasMessageContaining(level.where());
        assertThat(read(dcs)).as(where).isEqualTo(legacy);
        // снятие - байтовый возврат к узлу до записи
        setParameter(dcs, with(payload, "remove", true));
        assertThat(read(dcs)).as(where).isEqualTo(before);
      }
    }
  }

  @Test
  void refusalInBatchLeavesFileIntact() throws Exception {
    Path dcs = fixture();
    byte[] before = Files.readAllBytes(dcs);
    Map<String, Level> levels = new LinkedHashMap<>();
    for (Level level : levels()) {
      levels.put(level.name(), level);
    }
    List<Map<String, Object>> operations = List.of(
      parameterOp(dcs, Map.of("section", "output", "path", levels.get("таблица").paths().get(0),
        "parameter", "ВыводитьЗаголовок", "value", values().get("ВыводитьЗаголовок"))),
      parameterOp(dcs, Map.of("section", "output", "path", levels.get("группа диаграммы").paths().get(1),
        "parameter", "Заголовок", "value", values().get("Заголовок"))));
    ByteArrayOutputStream err = new ByteArrayOutputStream();
    PrintStream previous = System.err;
    int exit;
    try {
      System.setErr(new PrintStream(err, true, StandardCharsets.UTF_8));
      exit = applyMutation(Map.of("op", "batch", "operations", operations));
    } finally {
      System.setErr(previous);
    }
    assertThat(exit).isEqualTo(2);
    assertThat(err.toString(StandardCharsets.UTF_8)).startsWith("Операция 2 (cf-dcs-set-parameter-value): "
      + "Параметр вывода «Заголовок»: платформа не хранит этот параметр вывода у группы точек или серий диаграммы");
    assertThat(Files.readAllBytes(dcs)).isEqualTo(before);
    // без отказа пакет пишет обе операции
    assertThat(applyMutation(Map.of("op", "batch", "operations", List.of(operations.get(0), parameterOp(dcs, Map.of(
      "section", "output", "path", levels.get("группа диаграммы").paths().get(1), "parameter", "КоличествоЗаписей",
      "value", values().get("КоличествоЗаписей"))))))).isZero();
    assertThat(outputNames(dcs, levels.get("таблица").paths().get(0))).containsExactly("ВыводитьЗаголовок");
    assertThat(outputNames(dcs, levels.get("группа диаграммы").paths().get(1))).containsExactly("КоличествоЗаписей");
  }

  // ------------------------------------------------------------------ корпусы

  @Test
  void corporaEchoAllOutputParametersAndEditKeptOnesWithoutRefusal() throws Exception {
    // Эхо каждого параметра вывода на каждом уровне (корень, группировки, таблицы, диаграммы и их группы) по
    // обоим корпусам - байтовый no-op; настоящая правка (use туда и обратно) каждого параметра таблиц,
    // диаграмм и их групп проходит без отказа: все записанные там имена уровень хранит
    Map<String, Integer> expected = new TreeMap<>(Map.of(
      "корень", 227, "группировка структуры", 124, "таблица", 3, "группа таблицы", 12, "диаграмма", 41,
      "группа диаграммы", 4));
    // Имена таблиц, диаграмм и их групп в корпусах: все - из хранимых уровнем
    Map<String, java.util.Set<String>> expectedNames = new TreeMap<>(Map.of(
      "таблица", new java.util.TreeSet<>(List.of("Заголовок", "ВыводитьЗаголовок")),
      "группа таблицы", new java.util.TreeSet<>(List.of("МакетОформления", "ТипЗаголовкаПолей", "ВыводитьЗаголовок",
        "ВыводитьОтбор", "РасположениеИтогов", "РасположениеОбщихИтогов", "ВариантИспользованияГруппировки")),
      "диаграмма", new java.util.TreeSet<>(List.of("ТипДиаграммы", "Заголовок", "ВыводитьЗаголовок")),
      "группа диаграммы", new java.util.TreeSet<>(List.of("КоличествоЗаписей"))));
    for (Path root : List.of(Ssl31SubmodulePaths.projectRoot().resolve("src"),
      Path.of(System.getProperty("fixtures.ssl31edt.root")))) {
      Map<String, Integer> counts = new TreeMap<>();
      names.clear();
      int index = 0;
      for (Path schema : schemaFiles(root)) {
        Path copy = Files.createTempDirectory(tempDir, "corpus" + index++).resolve(schema.getFileName());
        Files.copy(schema, copy);
        byte[] original = Files.readAllBytes(copy);
        for (Map<String, Object> variant : list(DcsSettingsRead.info(copy, SchemaVersion.V2_20, null)
          .get("variants"))) {
          String name = (String) variant.get("name");
          echoNode(copy, name, List.of(), "корень", settings(copy, name), counts, schema.toString());
        }
        assertThat(Files.readAllBytes(copy)).as(schema.toString()).isEqualTo(original);
      }
      assertThat(counts).as(root.toString()).isEqualTo(expected);
      assertThat(names).as(root.toString()).isEqualTo(expectedNames);
      for (Level level : levels()) {
        assertThat(level.exported()).as(level.name()).containsAll(names.get(level.name()));
      }
    }
  }

  /** Имена параметров вывода корпуса по уровням ниже группировки структуры. */
  private final Map<String, java.util.Set<String>> names = new TreeMap<>();

  /** Эхо параметров вывода узла и правка use у таблиц, диаграмм и их групп; рекурсивно - вложенные узлы. */
  private void echoNode(Path dcs, String variant, List<Map<String, Object>> path, String level,
    Map<String, Object> node, Map<String, Integer> counts, String where) throws Exception {
    if (node.get("outputParameters") instanceof Map<?, ?> section) {
      for (Map<String, Object> parameter : list(section.get("items"))) {
        String name = (String) parameter.get("parameter");
        String label = where + " " + variant + " " + path + " " + name;
        counts.merge(level, 1, Integer::sum);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("section", "output");
        payload.put("path", path);
        payload.put("parameter", name);
        if (parameter.get("use") != null) {
          payload.put("use", parameter.get("use"));
        }
        if (parameter.get("value") instanceof List<?> value && !value.isEmpty()) {
          payload.put("value", value.get(0));
        }
        byte[] before = Files.readAllBytes(dcs);
        DcsSettingsEdit.setParameterValue(dcs, SchemaVersion.V2_20, variant, payload);
        assertThat(Files.readAllBytes(dcs)).as(label).isEqualTo(before);
        if (!"корень".equals(level) && !"группировка структуры".equals(level)) {
          names.computeIfAbsent(level, key -> new java.util.TreeSet<>()).add(name);
          boolean off = Boolean.FALSE.equals(parameter.get("use"));
          Map<String, Object> flag = Map.of("section", "output", "path", path, "parameter", name);
          DcsSettingsEdit.setParameterValue(dcs, SchemaVersion.V2_20, variant, with(flag, "use", off));
          assertThat(Files.readAllBytes(dcs)).as(label).isNotEqualTo(before);
          DcsSettingsEdit.setParameterValue(dcs, SchemaVersion.V2_20, variant, with(flag, "use", !off));
          assertThat(Files.readAllBytes(dcs)).as(label).isEqualTo(before);
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
        String childLevel = switch (String.valueOf(child.get("kind"))) {
          case "table" -> "таблица";
          case "chart" -> "диаграмма";
          case "group" -> childPath.stream().anyMatch(step -> List.of("points", "series").contains(step.get("list")))
            ? "группа диаграммы"
            : childPath.stream().anyMatch(step -> List.of("rows", "columns").contains(step.get("list")))
              ? "группа таблицы" : "группировка структуры";
          default -> null;
        };
        if (childLevel != null) {
          echoNode(dcs, variant, childPath, childLevel, child, counts, where);
        }
      }
    }
  }

  // ------------------------------------------------------------------ помощники

  /**
   * Схема с узлами всех уровней в конце структуры «Основного»: таблица со строкой, колонкой и их вложенными
   * группами, диаграмма с точкой, серией и их вложенными группами - всё новое, без параметров вывода.
   */
  private Path fixture() throws Exception {
    Path dcs = Files.createTempDirectory(tempDir, "levels").resolve("Template.xml");
    Files.copy(Ssl31SubmodulePaths.projectRoot().resolve(ACCOUNTING_CHECK), dcs, StandardCopyOption.REPLACE_EXISTING);
    int roots = list(settings(dcs, VARIANT).get("structure")).size();
    Map<String, Object> table = step("structure", roots);
    Map<String, Object> chart = step("structure", roots + 1);
    addItem(dcs, List.of(), "structure", "table");
    addItem(dcs, List.of(table), "columns", null);
    addItem(dcs, List.of(table), "rows", null);
    addItem(dcs, List.of(table, step("columns", 0)), "structure", "group");
    addItem(dcs, List.of(table, step("rows", 0)), "structure", "group");
    addItem(dcs, List.of(), "structure", "chart");
    addItem(dcs, List.of(chart), "points", null);
    addItem(dcs, List.of(chart), "series", null);
    addItem(dcs, List.of(chart, step("points", 0)), "structure", "group");
    addItem(dcs, List.of(chart, step("series", 0)), "structure", "group");
    return dcs;
  }

  /** Уровни схемы {@link #fixture()} с путями узлов (верхние группы первыми) и хранимыми именами. */
  private List<Level> levels() throws Exception {
    Path probe = Files.createTempDirectory(tempDir, "roots").resolve("Template.xml");
    Files.copy(Ssl31SubmodulePaths.projectRoot().resolve(ACCOUNTING_CHECK), probe, StandardCopyOption.REPLACE_EXISTING);
    int roots = list(settings(probe, VARIANT).get("structure")).size();
    Map<String, Object> table = step("structure", roots);
    Map<String, Object> chart = step("structure", roots + 1);
    Map<String, Object> nested = step("structure", 0);
    return List.of(
      new Level("таблица", "у таблицы", List.of(List.of(table)),
        List.of("МакетОформления", "РасположениеИтогов", "РасположениеПолейГруппировки", "РасположениеГруппировки",
          "РасположениеРеквизитов", "РасположениеРесурсов", "ГоризонтальноеРасположениеОбщихИтогов",
          "ВертикальноеРасположениеОбщихИтогов", "ТипЗаголовкаПолей", "ВыводитьЗаголовок", "Заголовок",
          "АвтоПозицияРесурсов"),
        List.of("ГоризонтальноеРасположениеОбщихИтогов")),
      new Level("группа таблицы", "у группы строк или колонок таблицы", List.of(
        List.of(table, step("rows", 0)), List.of(table, step("columns", 0)),
        List.of(table, step("rows", 0), nested), List.of(table, step("columns", 0), nested)),
        List.of("МакетОформления", "КоличествоЗаписей", "ПроцентЗаписей", "РасположениеИтогов",
          "РасположениеПолейГруппировки", "РасположениеГруппировки", "РасположениеРеквизитов", "РасположениеРесурсов",
          "РасположениеОбщихИтогов", "ТипЗаголовкаПолей", "ВыводитьЗаголовок", "Заголовок", "ВыводитьОтбор",
          "АвтоПозицияРесурсов", "ВариантИспользованияГруппировки"),
        List.of("РасположениеОбщихИтогов", "ВариантИспользованияГруппировки")),
      new Level("диаграмма", "у диаграммы", List.of(List.of(chart)),
        List.of("ТипДиаграммы", "ВыводитьЗаголовок", "Заголовок"),
        List.of()),
      new Level("группа диаграммы", "у группы точек или серий диаграммы", List.of(
        List.of(chart, step("points", 0)), List.of(chart, step("series", 0)),
        List.of(chart, step("points", 0), nested), List.of(chart, step("series", 0), nested)),
        List.of("КоличествоЗаписей", "ПроцентЗаписей"),
        List.of()));
  }

  /** 22 имени таблицы рангов в её порядке и значения не по умолчанию. */
  private static Map<String, Object> values() {
    Map<String, Object> values = new LinkedHashMap<>();
    values.put("МакетОформления", "БезОформления");
    values.put("КоличествоЗаписей", 10);
    values.put("ПроцентЗаписей", 50);
    values.put("ТипМакета", enumValue("dcsset:DataCompositionGroupTemplateType", "Vertical"));
    values.put("РасположениеИтогов", enumValue("dcscor:DataCompositionTotalPlacement", "None"));
    values.put("РасположениеПолейГруппировки", enumValue("dcsset:DataCompositionGroupFieldsPlacement", "Together"));
    values.put("РасположениеГруппировки", enumValue("dcsset:DataCompositionGroupPlacement", "None"));
    values.put("РасположениеРеквизитов", enumValue("dcsset:DataCompositionAttributesPlacement", "Separately"));
    values.put("РасположениеРесурсов", enumValue("dcsset:DataCompositionResourcesPlacement", "Vertically"));
    values.put("ГоризонтальноеРасположениеОбщихИтогов", enumValue("dcscor:DataCompositionTotalPlacement", "None"));
    values.put("РасположениеОбщихИтогов", enumValue("dcscor:DataCompositionTotalPlacement", "None"));
    values.put("ВертикальноеРасположениеОбщихИтогов", enumValue("dcscor:DataCompositionTotalPlacement", "None"));
    values.put("ТипЗаголовкаПолей", enumValue("dcscor:DataCompositionFieldsTitleType", "Short"));
    values.put("ТипДиаграммы", enumValue("v8ui:ChartType", "Pie"));
    values.put("ВыводитьЗаголовок", enumValue("dcsset:DataCompositionTextOutputType", "Output"));
    values.put("Заголовок", Map.of("type", "v8:LocalStringType", "decoded", "Уровни параметров вывода"));
    values.put("ВыводитьПараметрыДанных", enumValue("dcsset:DataCompositionTextOutputType", "DontOutput"));
    values.put("ВыводитьОтбор", enumValue("dcsset:DataCompositionTextOutputType", "DontOutput"));
    values.put("АвтоПозицияРесурсов", enumValue("dcsset:DataCompositionResourcesAutoPosition", "DontUse"));
    values.put("ФиксацияСлева", enumValue("dcsset:DataCompositionFixation", "DontUse"));
    values.put("ВариантИспользованияГруппировки",
      enumValue("dcsset:DataCompositionGroupUseVariant", "AdditionalInformation"));
    values.put("ФиксацияСверху", enumValue("dcsset:DataCompositionFixation", "DontUse"));
    return values;
  }

  private static Map<String, Object> enumValue(String type, String lexeme) {
    return Map.of("type", type, "text", lexeme);
  }

  private static Map<String, Object> step(String list, int index) {
    return Map.of("list", list, "index", index);
  }

  private static List<DcsSettingsEdit.Step> steps(List<Map<String, Object>> path) {
    return path.stream()
      .map(step -> new DcsSettingsEdit.Step((String) step.get("list"), (Integer) step.get("index")))
      .toList();
  }

  private static void addItem(Path dcs, List<Map<String, Object>> parent, String list, String kind) throws Exception {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("parentPath", parent);
    payload.put("list", list);
    if (kind != null) {
      payload.put("kind", kind);
    }
    DcsSettingsEdit.addStructureItem(dcs, SchemaVersion.V2_20, VARIANT, payload);
  }

  /** Имена параметров вывода узла по пути в порядке файла; секции нет - пусто. */
  private static List<String> outputNames(Path dcs, List<Map<String, Object>> path) throws Exception {
    Map<String, Object> node = settings(dcs, VARIANT);
    for (Map<String, Object> step : path) {
      node = list(node.get(step.get("list"))).get((Integer) step.get("index"));
    }
    if (!(node.get("outputParameters") instanceof Map<?, ?> section)) {
      return List.of();
    }
    return list(section.get("items")).stream().map(item -> (String) item.get("parameter")).toList();
  }

  private Map<String, Object> parameterOp(Path dcs, Map<String, Object> payload) {
    return Map.of("op", "cf-dcs-set-parameter-value", "objectXml", dcs.toString(), "schemaVersion", "V2_20",
      "variant", VARIANT, "payloadJson", GSON.toJson(payload));
  }

  /** Копия payload с ещё одним ключом. */
  private static Map<String, Object> with(Map<String, Object> payload, String key, Object value) {
    Map<String, Object> out = new LinkedHashMap<>(payload);
    out.put(key, value);
    return out;
  }

  private void setParameter(Path dcs, Map<String, Object> payload) throws Exception {
    DcsSettingsEdit.setParameterValue(dcs, SchemaVersion.V2_20, VARIANT, payload);
  }

  private static Map<String, Object> settings(Path dcs, String variant) throws Exception {
    Map<String, Object> info = DcsSettingsRead.info(dcs, SchemaVersion.V2_20, variant);
    return map(map(list(info.get("variants")).get(0)).get("settings"));
  }

  private Path copy(Path source) throws IOException {
    Path target = Files.createTempDirectory(tempDir, "copy").resolve(source.getFileName());
    Files.copy(source, target);
    return target;
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

  private static String read(Path dcs) throws IOException {
    return Files.readString(dcs, StandardCharsets.UTF_8);
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

  private int applyMutation(Map<String, ?> params) throws Exception {
    Path paramsFile = Files.createTempFile(tempDir, "params", ".json");
    Files.writeString(paramsFile, GSON.toJson(params), StandardCharsets.UTF_8);
    return new CommandLine(new DesignerXmlCli()).execute("apply-mutation", "--params", paramsFile.toString());
  }
}
