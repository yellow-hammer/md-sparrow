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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Имена без учёта регистра: путь к данным поля набора, вычисляемого поля и
 * имя набора данных при добавлении и переименовании сверяются так же, как имена параметров
 * ({@code Сумма} и {@code СУММА} в выражениях схемы — одно имя). Смена регистра своего имени разрешена; путь
 * ресурса не уникален и не сверяется. В корпусах ssl31 и EDT-пары таких дублей нет.
 */
class DcsNamesIgnoringCaseTest {

  private static final SchemaVersion V = SchemaVersion.V2_20;

  /** Запрос «ОсновнойЗапрос» и объект «ВнешняяТаблица» со связью; два вычисляемых поля. */
  private static final String LINKED =
    "src/cf/Reports/РезультатыПроверкиУчета/Templates/ОсновнаяСхемаКомпоновкиДанных/Ext/Template.xml";

  private static final String QUERY = "ОсновнойЗапрос";

  @TempDir
  Path tempDir;

  @Test
  void наборДанныхСверяетсяБезУчётаРегистра() throws Exception {
    Path dcs = copy(LINKED);
    String before = Files.readString(dcs, StandardCharsets.UTF_8);
    assertThatThrownBy(() -> DcsEdit.addDataSet(dcs, V, "основнойзапрос", "query", "ИсточникДанных1"))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage("Набор данных «основнойзапрос» уже есть в схеме: «ОсновнойЗапрос».");
    assertThatThrownBy(() -> DcsEdit.addDataSet(dcs, V, QUERY, "union", null))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage("Набор данных «ОсновнойЗапрос» уже есть в схеме: «ОсновнойЗапрос».");
    assertThatThrownBy(() -> DcsEdit.renameDataSet(dcs, V, "ВнешняяТаблица", "ОСНОВНОЙЗАПРОС"))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage("Имя «ОСНОВНОЙЗАПРОС» уже занято: набор данных «ОсновнойЗапрос» уже есть в схеме.");
    assertThat(Files.readString(dcs, StandardCharsets.UTF_8)).isEqualTo(before);

    // Своё имя тем же текстом — no-op, другим регистром — переименование с каскадом
    DcsEdit.renameDataSet(dcs, V, "ВнешняяТаблица", "ВнешняяТаблица");
    assertThat(Files.readString(dcs, StandardCharsets.UTF_8)).isEqualTo(before);
    DcsEdit.renameDataSet(dcs, V, "ВнешняяТаблица", "ВНЕШНЯЯТАБЛИЦА");
    assertThat(Files.readString(dcs, StandardCharsets.UTF_8)).isEqualTo(before
      .replace("<name>ВнешняяТаблица</name>", "<name>ВНЕШНЯЯТАБЛИЦА</name>")
      .replace("<sourceDataSet>ВнешняяТаблица</sourceDataSet>", "<sourceDataSet>ВНЕШНЯЯТАБЛИЦА</sourceDataSet>"));
  }

  @Test
  void путьПоляНабораСверяетсяБезУчётаРегистра() throws Exception {
    Path dcs = copy(LINKED);
    String before = Files.readString(dcs, StandardCharsets.UTF_8);
    assertThatThrownBy(() -> DcsEdit.addField(dcs, V, QUERY, Map.of("dataPath", "выявлено", "field", "выявлено")))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage("Поле «выявлено» уже есть в наборе: «Выявлено».");
    assertThatThrownBy(() -> DcsEdit.setField(dcs, V, QUERY, "Выявлено", Map.of("newDataPath", "видпроверки")))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage("Путь к данным «видпроверки» уже занят: поле «ВидПроверки» уже есть в наборе.");
    assertThat(Files.readString(dcs, StandardCharsets.UTF_8)).isEqualTo(before);

    // Эхо своего пути — ни байта; смена регистра своего пути разрешена
    DcsEdit.setField(dcs, V, QUERY, "Выявлено", Map.of("newDataPath", "Выявлено"));
    assertThat(Files.readString(dcs, StandardCharsets.UTF_8)).isEqualTo(before);
    DcsEdit.setField(dcs, V, QUERY, "Выявлено", Map.of("newDataPath", "ВЫЯВЛЕНО"));
    assertThat(Files.readString(dcs, StandardCharsets.UTF_8))
      .isEqualTo(before.replace("<dataPath>Выявлено</dataPath>", "<dataPath>ВЫЯВЛЕНО</dataPath>"));
    // Поле с другим путём добавляется
    DcsEdit.addField(dcs, V, QUERY, Map.of("dataPath", "Новое", "field", "Новое"));
    assertThat(DcsXsdValidation.validate(dcs).get("ok")).isEqualTo(true);
  }

  @Test
  void путьВычисляемогоПоляСверяетсяБезУчётаРегистра() throws Exception {
    Path dcs = copy(LINKED);
    String before = Files.readString(dcs, StandardCharsets.UTF_8);
    assertThatThrownBy(() -> DcsRead.addCalculatedField(dcs, V, "расшифровкаполя", "1", null))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage("Вычисляемое поле «расшифровкаполя» уже есть в схеме: «РасшифровкаПоля».");
    // Тот же путь в том же регистре — тоже отказ, а не второе поле
    assertThatThrownBy(() -> DcsRead.addCalculatedField(dcs, V, "РасшифровкаПоля", "1", null))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage("Вычисляемое поле «РасшифровкаПоля» уже есть в схеме: «РасшифровкаПоля».");
    assertThatThrownBy(() -> DcsEdit.setCalculatedField(dcs, V, "РасшифровкаПоля",
      Map.of("newDataPath", "правилопроверкипричиныпроблемы")))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage("Путь к данным «правилопроверкипричиныпроблемы» уже занят: вычисляемое поле "
        + "«ПравилоПроверкиПричиныПроблемы» уже есть в схеме.");
    assertThat(Files.readString(dcs, StandardCharsets.UTF_8)).isEqualTo(before);

    DcsEdit.setCalculatedField(dcs, V, "РасшифровкаПоля", Map.of("newDataPath", "РасшифровкаПоля"));
    assertThat(Files.readString(dcs, StandardCharsets.UTF_8)).isEqualTo(before);
    DcsEdit.setCalculatedField(dcs, V, "РасшифровкаПоля", Map.of("newDataPath", "РАСШИФРОВКАПОЛЯ"));
    assertThat(Files.readString(dcs, StandardCharsets.UTF_8))
      .isEqualTo(before.replace("<dataPath>РасшифровкаПоля</dataPath>", "<dataPath>РАСШИФРОВКАПОЛЯ</dataPath>"));
  }

  @Test
  void путьРесурсаНеУникален() throws Exception {
    Path dcs = copy(LINKED);
    DcsEdit.addTotalField(dcs, V, Map.of("dataPath", "Х", "expression", "Сумма(1)"));
    DcsEdit.addTotalField(dcs, V, Map.of("dataPath", "х", "expression", "Количество(1)"));
    DcsEdit.addTotalField(dcs, V, Map.of("dataPath", "Х", "expression", "Максимум(1)"));
    List<Map<String, Object>> totals = rows(DcsRead.info(dcs, V), "totalFields");
    assertThat(totals.subList(totals.size() - 3, totals.size())).extracting(row -> row.get("name"))
      .containsExactly("Х", "х", "Х");
  }

  @Test
  void каналИСхемаEdtОтказываютТакЖе() throws Exception {
    Path dcs = copy(LINKED);
    Map<String, String> params = new HashMap<>();
    params.put("op", "cf-dcs-add-field");
    params.put("objectXml", dcs.toString());
    params.put("schemaVersion", "V2_20");
    params.put("dataSet", QUERY);
    params.put("payloadJson", "{\"dataPath\":\"ВЫЯВЛЕНО\",\"field\":\"ВЫЯВЛЕНО\"}");
    ChannelResult refused = applyMutation(params);
    assertThat(refused.exit()).isEqualTo(2);
    assertThat(refused.err()).contains("Поле «ВЫЯВЛЕНО» уже есть в наборе: «Выявлено».");

    Path source = Path.of(System.getProperty("fixtures.ssl31edt.root"), "ssl31", "src", "Reports",
      "РезультатыПроверкиУчета", "Templates", "ОсновнаяСхемаКомпоновкиДанных", "Template.dcs");
    Path edt = Files.createTempDirectory(tempDir, "edt").resolve("Template.dcs");
    Files.copy(source, edt);
    String before = Files.readString(edt, StandardCharsets.UTF_8);
    assertThatThrownBy(() -> DcsEdit.addDataSet(edt, V, "ВНЕШНЯЯТАБЛИЦА", "object", "ИсточникДанных1"))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage("Набор данных «ВНЕШНЯЯТАБЛИЦА» уже есть в схеме: «ВнешняяТаблица».");
    assertThatThrownBy(() -> DcsRead.addCalculatedField(edt, V, "РАСШИФРОВКАПОЛЯ", "1", null))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("уже есть в схеме");
    assertThat(Files.readString(edt, StandardCharsets.UTF_8)).isEqualTo(before);
  }

  @Test
  void вКорпусахНетДублейИменБезУчётаРегистра() throws Exception {
    List<String> designer = duplicates(schemaFiles(Ssl31SubmodulePaths.projectRoot().resolve("src")));
    List<String> edt = duplicates(schemaFiles(Path.of(System.getProperty("fixtures.ssl31edt.root"))));
    assertThat(designer).isEmpty();
    assertThat(edt).isEmpty();
  }

  /** Дубли без учёта регистра: поля каждого набора (и вложенных), вычисляемые поля, наборы верхнего уровня. */
  private static List<String> duplicates(List<Path> schemas) throws Exception {
    List<String> out = new ArrayList<>();
    for (Path schema : schemas) {
      Map<String, Object> info = DcsRead.info(schema, V);
      List<Map<String, Object>> dataSets = rows(info, "dataSets");
      collect(out, schema + ": наборы", dataSets.stream().map(row -> (String) row.get("name")).toList());
      collect(out, schema + ": вычисляемые поля",
        rows(info, "calculatedFields").stream().map(row -> (String) row.get("name")).toList());
      fieldDuplicates(out, schema.toString(), dataSets);
    }
    return out;
  }

  private static void fieldDuplicates(List<String> out, String schema, List<Map<String, Object>> dataSets) {
    for (Map<String, Object> dataSet : dataSets) {
      collect(out, schema + ": поля " + dataSet.get("name"),
        rows(dataSet, "fields").stream().map(row -> (String) row.get("dataPath")).toList());
      fieldDuplicates(out, schema, rows(dataSet, "items"));
    }
  }

  private static void collect(List<String> out, String where, List<String> names) {
    Map<String, String> seen = new HashMap<>();
    for (String name : names) {
      String previous = seen.putIfAbsent(name.toUpperCase(Locale.ROOT), name);
      if (previous != null) {
        out.add(where + ": " + previous + " / " + name);
      }
    }
  }

  private Path copy(String relativePath) throws IOException {
    Path copy = Files.createTempDirectory(tempDir, "dcs").resolve("Template.xml");
    Files.copy(Ssl31SubmodulePaths.projectRoot().resolve(relativePath), copy);
    return copy;
  }

  @SuppressWarnings("unchecked")
  private static List<Map<String, Object>> rows(Map<String, Object> info, String key) {
    Object value = info.get(key);
    return value == null ? List.of() : (List<Map<String, Object>>) value;
  }

  /** Результат канала: код выхода, stdout и stderr в UTF-8. */
  private record ChannelResult(int exit, String out, String err) {
  }

  private ChannelResult applyMutation(Map<String, ?> params) throws Exception {
    Path paramsFile = Files.createTempFile(tempDir, "params", ".json");
    Files.writeString(paramsFile, new Gson().toJson(params), StandardCharsets.UTF_8);
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    ByteArrayOutputStream err = new ByteArrayOutputStream();
    PrintStream prevOut = System.out;
    PrintStream prevErr = System.err;
    int exit;
    try {
      System.setOut(new PrintStream(out, true, StandardCharsets.UTF_8));
      System.setErr(new PrintStream(err, true, StandardCharsets.UTF_8));
      exit = new CommandLine(new DesignerXmlCli()).execute("apply-mutation", "--params", paramsFile.toString());
    } finally {
      System.setOut(prevOut);
      System.setErr(prevErr);
    }
    return new ChannelResult(exit, out.toString(StandardCharsets.UTF_8), err.toString(StandardCharsets.UTF_8));
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
}
