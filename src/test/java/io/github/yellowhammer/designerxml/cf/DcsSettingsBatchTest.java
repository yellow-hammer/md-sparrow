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

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
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
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Пакеты {@code apply-mutation op:"batch"} из мутаций настроек {@code cf-dcs-*} - так их шлёт
 * мастер настроек. Структура варианта снимается и собирается заново
 * одним пакетом из прочитанных DTO: у образцов с чистой структурой мастера файл совпадает
 * байт в байт, в том числе в проекте 1С:EDT. Каждая операция видит правки предыдущих, отказ
 * любой из них оставляет диск нетронутым, а «В новый вариант…» - добавление варианта и
 * операции над ним - идёт тем же пакетом.
 */
class DcsSettingsBatchTest {

  /** Список: Организация → ДатаОплаты → детальные записи, часть выбранных полей выключена. */
  private static final String INVOICES =
    "Reports/_ДемоОтчетПоСчетамНаОплатуГлобальный/Templates/ОсновнаяСхемаКомпоновкиДанных";

  /** Список с иерархией: ВладелецФайла (Hierarchy) → детальные записи. */
  private static final String FILES = "Reports/_ДемоФайлы/Templates/ОсновнаяСхемаКомпоновкиДанных";

  /** Таблица: колонка, строка с вложенной безтиповой группой; отбор и условное оформление корня. */
  private static final String SURVEY =
    "Reports/АналитическийОтчетПоАнкетированию/Templates/ОсновнаяСхемаКомпоновкиДанных";

  /** Вариант-таблица отчёта {@link #SURVEY}. */
  private static final String SURVEY_TABLE = "ПростыеВопросыКоличествоОтветовСравнениеПоОпросам";

  /** Диаграмма с одной серией, своим selection=[Auto] и ТипДиаграммы с под-параметром в узле. */
  private static final String ORDER_STATUSES =
    "Reports/_ДемоСтатусыЗаказовПокупателей/Templates/ОсновнаяСхемаКомпоновкиДанных";

  /** Под-параметр ТипДиаграммы диаграммы {@link #ORDER_STATUSES}: только для чтения, пакет его не пишет. */
  private static final String CHART_LABELS =
    "\t\t\t\t\t\t<dcscor:item xsi:type=\"dcsset:SettingsParameterValue\">\r\n"
      + "\t\t\t\t\t\t\t<dcscor:parameter>ТипДиаграммы.ВидПодписей</dcscor:parameter>\r\n"
      + "\t\t\t\t\t\t\t<dcscor:value xsi:type=\"v8ui:ChartLabelType\">SeriesValue</dcscor:value>\r\n"
      + "\t\t\t\t\t\t</dcscor:item>\r\n";

  private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

  @TempDir Path tempDir;

  // ------------------------------------------------------------------ круговые пересборки

  @Test
  void rebuildsListWithGroupsByteForByte() throws Exception {
    assertRebuiltByteForByte(designerCopy(INVOICES), "Основной");
  }

  @Test
  void rebuildsHierarchyListByteForByte() throws Exception {
    assertRebuiltByteForByte(designerCopy(FILES), "Основной");
  }

  @Test
  void rebuildsTableByteForByte() throws Exception {
    assertRebuiltByteForByte(designerCopy(SURVEY), SURVEY_TABLE);
  }

  @Test
  void rebuildsEdtTemplateDcsByteForByte() throws Exception {
    // в проекте 1С:EDT тот же пакет идёт по Template.dcs без BOM
    for (String schema : List.of(INVOICES, FILES)) {
      Path dcs = edtCopy(schema);
      assertThat(Files.readString(dcs, StandardCharsets.UTF_8)).startsWith("<?xml");
      assertRebuiltByteForByte(dcs, "Основной");
    }
  }

  @Test
  void rebuildsChartWithAutoSelectionAndChartType() throws Exception {
    // диаграмма: скаффолд, затем свой selection=[Auto] и ТипДиаграммы узла - всё одним пакетом;
    // под-параметр ТипДиаграммы.* только для чтения, поэтому без него пересборка - байты исходника
    Path dcs = designerCopy(ORDER_STATUSES);
    String before = Files.readString(dcs, StandardCharsets.UTF_8);
    Map<String, Object> settings = settings(dcs, "Основной");
    Map<String, Object> chart = list(settings.get("structure")).get(0);
    Map<String, Object> chartType = list(map(chart.get("outputParameters")).get("items")).get(0);
    assertThat(chartType).containsEntry("parameter", "ТипДиаграммы");

    List<JsonObject> operations = rebuildOperations(dcs, "Основной", settings);
    operations.add(op(dcs, "cf-dcs-set-parameter-value", "Основной", Map.of("section", "output",
      "path", List.of(step("structure", 0)), "parameter", "ТипДиаграммы",
      "value", list(chartType.get("value")).get(0))));
    assertThat(operations).extracting(o -> o.get("op").getAsString()).containsExactly(
      "cf-dcs-remove-structure-item", "cf-dcs-set-selection", "cf-dcs-add-structure-item",
      "cf-dcs-add-structure-item", "cf-dcs-set-selection", "cf-dcs-set-parameter-value");

    Result result = run(batch(operations));

    assertThat(result.exit).as(result.err).isZero();
    assertThat(Files.readString(dcs, StandardCharsets.UTF_8)).isEqualTo(remove(before, CHART_LABELS));
    assertThat(DcsXsdValidation.validate(dcs)).containsEntry("ok", true);
  }

  // ------------------------------------------------------------------ «В новый вариант…»

  @Test
  void newVariantWithWizardStructureGoesInOneBatch() throws Exception {
    // мастер «В новый вариант…»: вариант, выбранные поля, диаграмма с серией и своим выбором,
    // ТипДиаграммы в корне нового варианта - пакетом; «Основной» не трогается
    Path dcs = designerCopy(ORDER_STATUSES);
    String before = Files.readString(dcs, StandardCharsets.UTF_8);
    Map<String, Object> main = settings(dcs, "Основной");
    String name = "Мастер1";

    List<JsonObject> operations = new ArrayList<>();
    JsonObject variant = new JsonObject();
    variant.addProperty("op", "cf-dcs-add-settings-variant");
    variant.addProperty("objectXml", dcs.toString());
    variant.addProperty("schemaVersion", "V2_20");
    variant.addProperty("name", name);
    variant.addProperty("payloadJson", GSON.toJson(Map.of("presentation", "Статусы (мастер)")));
    operations.add(variant);
    List<JsonObject> rebuilt = rebuildOperations(dcs, name, main);
    // в новом варианте снимать нечего: пересборка «Основного» без удалений
    rebuilt.removeIf(o -> "cf-dcs-remove-structure-item".equals(o.get("op").getAsString()));
    operations.addAll(rebuilt);
    operations.add(op(dcs, "cf-dcs-set-parameter-value", name, Map.of("section", "output",
      "parameter", "ТипДиаграммы", "value", Map.of("type", "v8ui:ChartType", "decoded", "Pie3D"))));

    Result dry = run(dryRun(batch(operations)));
    assertThat(dry.exit).as(dry.err).isZero();
    assertThat(Files.readString(dcs, StandardCharsets.UTF_8)).isEqualTo(before);

    Result result = run(batch(operations));

    assertThat(result.exit).as(result.err).isZero();
    assertThat(result.out).isEqualTo(dry.out);
    JsonArray answers = JsonParser.parseString(result.out).getAsJsonArray();
    assertThat(answers).hasSize(operations.size());
    String after = Files.readString(dcs, StandardCharsets.UTF_8);
    // «Основной» и всё до него - байты исходника; новый вариант встаёт следом
    int mainEnd = before.lastIndexOf("</settingsVariant>") + "</settingsVariant>".length();
    assertThat(after).startsWith(before.substring(0, mainEnd)).endsWith(before.substring(mainEnd));
    // узел диаграммы нового варианта - байты узла «Основного» без его параметров вывода
    assertThat(chartNode(after, after.indexOf("<dcsset:name>" + name + "</dcsset:name>")))
      .isEqualTo(withoutOutputParameters(chartNode(before, 0)));

    Map<String, Object> created = settings(dcs, name);
    assertThat(created.get("selection")).isEqualTo(main.get("selection"));
    List<Map<String, Object>> rootParameters = list(map(created.get("outputParameters")).get("items"));
    assertThat(rootParameters).hasSize(1);
    assertThat(rootParameters.get(0)).containsEntry("parameter", "ТипДиаграммы");
    assertThat(DcsXsdValidation.validate(dcs)).containsEntry("ok", true);
  }

  // ------------------------------------------------------------------ отказы

  @Test
  void refusalInSecondOperationLeavesFileIntact() throws Exception {
    Path dcs = designerCopy(ORDER_STATUSES);
    byte[] before = Files.readAllBytes(dcs);
    List<JsonObject> operations = List.of(
      op(dcs, "cf-dcs-remove-structure-item", "Основной", Map.of("path", List.of(step("structure", 0)))),
      op(dcs, "cf-dcs-add-structure-item", "Основной", Map.of("parentPath", List.of(), "list", "nope",
        "kind", "group")),
      op(dcs, "cf-dcs-add-structure-item", "Основной", Map.of("parentPath", List.of(), "list", "structure",
        "kind", "group")));

    for (JsonObject batch : List.of(dryRun(batch(operations)), batch(operations))) {
      Result result = run(batch);
      assertThat(result.exit).as(result.err).isEqualTo(2);
      assertThat(result.out).isEmpty();
      assertThat(result.err).startsWith("Операция 2 (cf-dcs-add-structure-item): ");
      assertThat(Files.readAllBytes(dcs)).isEqualTo(before);
    }
  }

  @Test
  void refusalLateInWizardBatchDropsNewVariantToo() throws Exception {
    // поздний отказ (незнакомая лексема типа диаграммы) отменяет и вариант, и всю его структуру
    Path dcs = designerCopy(ORDER_STATUSES);
    byte[] before = Files.readAllBytes(dcs);
    JsonObject variant = new JsonObject();
    variant.addProperty("op", "cf-dcs-add-settings-variant");
    variant.addProperty("objectXml", dcs.toString());
    variant.addProperty("schemaVersion", "V2_20");
    variant.addProperty("name", "Мастер1");
    List<JsonObject> operations = List.of(variant,
      op(dcs, "cf-dcs-add-structure-item", "Мастер1", Map.of("parentPath", List.of(), "list", "structure",
        "kind", "chart")),
      op(dcs, "cf-dcs-set-selection", "Мастер1", Map.of("path", List.of(step("structure", 0)),
        "items", List.of(Map.of("kind", "auto")))),
      op(dcs, "cf-dcs-set-parameter-value", "Мастер1", Map.of("section", "output", "parameter", "ТипДиаграммы",
        "value", Map.of("type", "v8ui:ChartType", "decoded", "Круговая"))));

    Result result = run(batch(operations));

    assertThat(result.exit).as(result.err).isEqualTo(2);
    assertThat(result.err).startsWith("Операция 4 (cf-dcs-set-parameter-value): ")
      .contains("v8ui:ChartType", "Круговая");
    assertThat(Files.readAllBytes(dcs)).isEqualTo(before);
  }

  // ------------------------------------------------------------------ помощники

  /**
   * Пересборка структуры пакетом: снять корневые узлы, выбранные поля и порядок корня прочитанными
   * DTO (эхо), узлы заново из прочитанных DTO; заодно проверяется, что одни удаления структуру
   * действительно снимают (иначе круг ничего не доказывает).
   */
  private void assertRebuiltByteForByte(Path dcs, String variant) throws Exception {
    byte[] before = Files.readAllBytes(dcs);
    Map<String, Object> settings = settings(dcs, variant);
    assertThat(list(settings.get("structure"))).isNotEmpty();
    List<JsonObject> operations = rebuildOperations(dcs, variant, settings);

    Path removedOnly = Files.createTempDirectory(tempDir, "removed").resolve(dcs.getFileName());
    Files.copy(dcs, removedOnly);
    List<JsonObject> removals = new ArrayList<>();
    for (JsonObject operation : operations) {
      if ("cf-dcs-remove-structure-item".equals(operation.get("op").getAsString())) {
        JsonObject copy = operation.deepCopy();
        copy.addProperty("objectXml", removedOnly.toString());
        removals.add(copy);
      }
    }
    Result removed = run(batch(removals));
    assertThat(removed.exit).as(removed.err).isZero();
    assertThat(settings(removedOnly, variant)).doesNotContainKey("structure");

    Result result = run(batch(operations));

    assertThat(result.exit).as(result.err).isZero();
    assertThat(JsonParser.parseString(result.out).getAsJsonArray()).hasSize(operations.size());
    assertThat(Files.readAllBytes(dcs)).isEqualTo(before);
  }

  /**
   * Операции пересборки, как их построит мастер: удаления корневых узлов (всегда индекс 0),
   * выбранные поля и порядок корня, затем узлы по порядку с путями, известными заранее.
   */
  private static List<JsonObject> rebuildOperations(Path dcs, String variant, Map<String, Object> settings) {
    List<JsonObject> operations = new ArrayList<>();
    List<Map<String, Object>> structure = settings.containsKey("structure")
      ? list(settings.get("structure")) : List.of();
    for (int i = 0; i < structure.size(); i++) {
      operations.add(op(dcs, "cf-dcs-remove-structure-item", variant, Map.of("path", List.of(step("structure", 0)))));
    }
    if (settings.containsKey("selection")) {
      operations.add(op(dcs, "cf-dcs-set-selection", variant,
        Map.of("path", List.of(), "items", map(settings.get("selection")).get("items"))));
    }
    if (settings.containsKey("order")) {
      operations.add(op(dcs, "cf-dcs-set-order", variant,
        Map.of("path", List.of(), "items", map(settings.get("order")).get("items"))));
    }
    for (int i = 0; i < structure.size(); i++) {
      addNode(dcs, variant, structure.get(i), List.of(), "structure", i, operations);
    }
    return operations;
  }

  /** Узел и его потомки: скаффолд add-structure-item, у таблицы и диаграммы - и свой выбор. */
  private static void addNode(Path dcs, String variant, Map<String, Object> node, List<Map<String, Object>> parent,
    String list, int index, List<JsonObject> operations) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("parentPath", parent);
    payload.put("list", list);
    if ("structure".equals(list)) {
      payload.put("kind", node.get("kind"));
    }
    if (node.containsKey("groupFields")) {
      payload.put("groupFields", node.get("groupFields"));
    }
    operations.add(op(dcs, "cf-dcs-add-structure-item", variant, payload));
    List<Map<String, Object>> path = new ArrayList<>(parent);
    path.add(step(list, index));
    for (String children : List.of("columns", "rows", "points", "series")) {
      if (node.containsKey(children)) {
        List<Map<String, Object>> nodes = list(node.get(children));
        for (int i = 0; i < nodes.size(); i++) {
          addNode(dcs, variant, nodes.get(i), path, children, i, operations);
        }
      }
    }
    if (!"group".equals(node.get("kind")) && node.containsKey("selection")) {
      operations.add(op(dcs, "cf-dcs-set-selection", variant,
        Map.of("path", path, "items", map(node.get("selection")).get("items"))));
    }
    if (node.containsKey("structure")) {
      List<Map<String, Object>> nodes = list(node.get("structure"));
      for (int i = 0; i < nodes.size(); i++) {
        addNode(dcs, variant, nodes.get(i), path, "structure", i, operations);
      }
    }
  }

  /** Операция пакета: файл схемы, формат, вариант и payload строкой JSON. */
  private static JsonObject op(Path dcs, String name, String variant, Map<String, Object> payload) {
    JsonObject op = new JsonObject();
    op.addProperty("op", name);
    op.addProperty("objectXml", dcs.toString());
    op.addProperty("schemaVersion", "V2_20");
    op.addProperty("variant", variant);
    op.addProperty("payloadJson", GSON.toJson(payload));
    return op;
  }

  private static Map<String, Object> step(String list, int index) {
    return Map.of("list", list, "index", index);
  }

  private static JsonObject batch(List<JsonObject> operations) {
    JsonObject batch = new JsonObject();
    batch.addProperty("op", "batch");
    JsonArray list = new JsonArray();
    operations.forEach(list::add);
    batch.add("operations", list);
    return batch;
  }

  private static JsonObject dryRun(JsonObject batch) {
    JsonObject copy = batch.deepCopy();
    copy.addProperty("dryRun", true);
    return copy;
  }

  /** Узел диаграммы: целые строки от открывающего тега, первого после {@code from}, до его закрытия. */
  private static String chartNode(String text, int from) {
    int open = text.indexOf("<dcsset:item xsi:type=\"dcsset:StructureItemChart\">", from);
    assertThat(open).isNotNegative();
    int lineStart = text.lastIndexOf('\n', open) + 1;
    String indent = text.substring(lineStart, open);
    int close = text.indexOf("\r\n" + indent + "</dcsset:item>", open);
    assertThat(close).isPositive();
    return text.substring(lineStart, close + 2 + indent.length() + "</dcsset:item>".length());
  }

  /** Узел без секции параметров вывода (целыми строками). */
  private static String withoutOutputParameters(String node) {
    int open = node.indexOf("<dcsset:outputParameters>");
    assertThat(open).isPositive();
    int lineStart = node.lastIndexOf('\n', open) + 1;
    String closeTag = "</dcsset:outputParameters>\r\n";
    int close = node.indexOf(closeTag, open) + closeTag.length();
    return node.substring(0, lineStart) + node.substring(close);
  }

  /** Текст без единственного вхождения блока. */
  private static String remove(String text, String block) {
    int at = text.indexOf(block);
    assertThat(at).isNotNegative();
    assertThat(text.indexOf(block, at + 1)).isNegative();
    return text.substring(0, at) + text.substring(at + block.length());
  }

  private static Map<String, Object> settings(Path dcs, String variant) throws Exception {
    Map<String, Object> info = DcsSettingsRead.info(dcs, SchemaVersion.V2_20, variant);
    return map(list(info.get("variants")).get(0).get("settings"));
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

  private record Result(int exit, String out, String err) {
  }

  /** Канал apply-mutation с параметрами из UTF-8 JSON-файла; ответ и отказ - из потоков вывода. */
  private Result run(JsonObject params) throws Exception {
    Path file = Files.createTempFile(tempDir, "params", ".json");
    Files.writeString(file, params.toString(), StandardCharsets.UTF_8);
    PrintStream stdout = System.out;
    PrintStream stderr = System.err;
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    ByteArrayOutputStream err = new ByteArrayOutputStream();
    try {
      System.setOut(new PrintStream(out, true, StandardCharsets.UTF_8));
      System.setErr(new PrintStream(err, true, StandardCharsets.UTF_8));
      int exit = new CommandLine(new DesignerXmlCli()).execute("apply-mutation", "--params", file.toString());
      return new Result(exit, out.toString(StandardCharsets.UTF_8), err.toString(StandardCharsets.UTF_8));
    } finally {
      System.setOut(stdout);
      System.setErr(stderr);
    }
  }

  /** Копия схемы выгрузки конфигуратора ({@code Ext/Template.xml}) во временный каталог. */
  private Path designerCopy(String schema) throws Exception {
    Path source = Ssl31SubmodulePaths.projectRoot().resolve("src/cf").resolve(schema).resolve("Ext/Template.xml");
    Path copy = Files.createTempDirectory(tempDir, "dcs").resolve("Template.xml");
    Files.copy(source, copy, StandardCopyOption.REPLACE_EXISTING);
    return copy;
  }

  /** Копия схемы проекта 1С:EDT ({@code Template.dcs}) во временный каталог. */
  private Path edtCopy(String schema) throws Exception {
    Path source = Path.of(System.getProperty("fixtures.ssl31edt.root"), "ssl31", "src")
      .resolve(schema).resolve("Template.dcs");
    Path copy = Files.createTempDirectory(tempDir, "dcs").resolve("Template.dcs");
    Files.copy(source, copy, StandardCopyOption.REPLACE_EXISTING);
    return copy;
  }
}
