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
package io.github.yellowhammer.designerxml.cli;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.yellowhammer.designerxml.SamplesSubmodulePaths;
import io.github.yellowhammer.designerxml.SchemaVersion;

import com.google.gson.Gson;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

/**
 * {@code dryRun} у {@code apply-mutation}: операция проходит целиком, ответ тот же,
 * что у настоящей, а файлы остаются байт в байт прежними. Упавшая операция диск
 * тоже не меняет. Операции - те, что пишут несколько файлов сразу (#37).
 */
class ApplyMutationDryRunTest {

  @TempDir
  Path temp;

  private Path cf;
  private String configuration;
  private String catalog;

  @BeforeEach
  void prepare() throws Exception {
    cf = SamplesSubmodulePaths.copy(SamplesSubmodulePaths.bareObjects(SchemaVersion.V2_20), temp.resolve("cf"));
    configuration = cf.resolve("Configuration.xml").toString();
    catalog = cf.resolve("Catalogs").resolve("Справочник1.xml").toString();
    // У справочника появляется каталог с формой: перенос и копирование затронут и его
    assertThat(run(Map.of("op", "cf-form-add", "objectXml", catalog, "name", "Форма"), false).exit).isZero();
  }

  @Test
  void dryRunKeepsFilesAndAnswersLikeRealRun() throws Exception {
    Map<String, Map<String, String>> operations = new LinkedHashMap<>();
    operations.put("добавление объекта", Map.of(
      "op", "add-md-object", "configurationXml", configuration, "type", "CATALOG", "autoName", "true"));
    operations.put("форма", Map.of("op", "cf-form-add", "objectXml", catalog, "name", "ФормаСписка"));
    operations.put("элемент формы", Map.of("op", "cf-form-item-add", "formXml",
      cf.resolve("Catalogs/Справочник1/Forms/Форма/Ext/Form.xml").toString(), "payloadJson", "{\"input\": \"Поле\"}"));
    operations.put("реквизит формы", Map.of("op", "cf-form-attribute-add", "formXml",
      cf.resolve("Catalogs/Справочник1/Forms/Форма/Ext/Form.xml").toString(),
      "payloadJson", "{\"name\": \"Флаг\", \"type\": {\"types\": [\"xs:boolean\"]}}"));
    operations.put("переименование", Map.of("op", "cf-md-object-rename", "configurationXml", configuration,
      "objectXml", catalog, "tag", "Catalog", "oldName", "Справочник1", "newName", "Товары"));
    operations.put("копирование", Map.of("op", "cf-md-object-duplicate", "configurationXml", configuration,
      "objectXml", catalog, "tag", "Catalog", "sourceName", "Справочник1", "newName", "Копия"));
    operations.put("удаление", Map.of("op", "cf-md-object-delete", "configurationXml", configuration,
      "objectXml", catalog, "tag", "Catalog", "name", "Справочник1"));
    operations.put("новая конфигурация", Map.of(
      "op", "init-empty-cf", "targetCfRoot", temp.resolve("новая").toString()));
    operations.put("новое расширение", Map.of("op", "init-empty-cfe",
      "targetCfeRoot", temp.resolve("cfe").toString(), "name", "Расширение", "namePrefix", "расш_",
      "mainConfigurationXml", configuration));

    for (Map.Entry<String, Map<String, String>> operation : operations.entrySet()) {
      Map<String, String> before = tree(temp);
      Result dry = run(operation.getValue(), true);
      assertThat(dry.exit).as(operation.getKey() + ": " + dry.err).isZero();
      assertThat(tree(temp)).as(operation.getKey()).isEqualTo(before);

      // Настоящий запуск с тем же ответом меняет диск: проверка без записи прошла ту же операцию
      Result real = run(operation.getValue(), false);
      assertThat(real.exit).as(operation.getKey() + ": " + real.err).isZero();
      assertThat(real.out).as(operation.getKey()).isEqualTo(dry.out);
      assertThat(tree(temp)).as(operation.getKey()).isNotEqualTo(before);
      prepareAgain();
    }
  }

  @Test
  void generationDryRunWritesNothing() throws Exception {
    // Проект EDT с немецким основным языком: генерация по нему идёт своим кодом
    Path edt = SamplesSubmodulePaths.copy(
      Path.of("src", "test", "resources", "edt-language-de", "Двуязычная").toAbsolutePath(), temp.resolve("edt"));
    String edtConfiguration = edt.resolve("src/Configuration/Configuration.mdo").toString();
    Map<String, Map<String, String>> operations = new LinkedHashMap<>();
    operations.put("новый проект EDT", Map.of(
      "op", "init-empty-cf", "targetCfRoot", temp.resolve("новый-edt").toString(), "format", "edt"));
    operations.put("объект EDT", Map.of(
      "op", "add-md-object", "configurationXml", edtConfiguration, "type", "CATALOG", "name", "Waren"));
    operations.put("объект EDT со свободным именем", Map.of(
      "op", "add-md-object", "configurationXml", edtConfiguration, "type", "DOCUMENT", "autoName", "true"));
    operations.put("форма EDT", Map.of("op", "cf-form-add",
      "objectXml", edt.resolve("src/Catalogs/Товары/Товары.mdo").toString(), "name", "Форма"));
    operations.put("расширение EDT", Map.of("op", "init-empty-cfe",
      "targetCfeRoot", temp.resolve("edt-cfe").toString(), "name", "Расширение", "namePrefix", "расш_",
      "mainConfigurationXml", edtConfiguration));
    operations.put("внешняя обработка EDT", Map.of("op", "external-artifact-add",
      "artifactsRoot", temp.resolve("edt-epf").toString(), "mainConfigurationXml", edtConfiguration,
      "name", "Обработка", "kind", "DATA_PROCESSOR"));
    operations.put("внешний отчёт", Map.of("op", "external-artifact-add",
      "artifactsRoot", temp.resolve("erf").toString(), "name", "Отчёт", "kind", "REPORT"));

    for (Map.Entry<String, Map<String, String>> operation : operations.entrySet()) {
      Map<String, String> before = tree(temp);
      Result dry = run(operation.getValue(), true);
      assertThat(dry.exit).as(operation.getKey() + ": " + dry.err).isZero();
      assertThat(tree(temp)).as(operation.getKey()).isEqualTo(before);

      Result real = run(operation.getValue(), false);
      assertThat(real.exit).as(operation.getKey() + ": " + real.err).isZero();
      assertThat(real.out).as(operation.getKey()).isEqualTo(dry.out);
      assertThat(tree(temp)).as(operation.getKey()).isNotEqualTo(before);
    }
  }

  @Test
  void dryRunBorrowKeepsExtension() throws Exception {
    Path cfe = temp.resolve("cfe");
    assertThat(run(Map.of("op", "init-empty-cfe", "targetCfeRoot", cfe.toString(), "name", "Расширение",
      "namePrefix", "расш_", "mainConfigurationXml", configuration), false).exit).isZero();
    Map<String, String> before = tree(temp);
    Map<String, String> borrow = Map.of("op", "cfe-borrow-object", "objectXml", catalog,
      "configurationXml", cfe.resolve("Configuration.xml").toString());

    Result dry = run(borrow, true);

    assertThat(dry.exit).as(dry.err).isZero();
    assertThat(tree(temp)).isEqualTo(before);
    assertThat(run(borrow, false).out).isEqualTo(dry.out);
  }

  @Test
  void failedOperationKeepsFiles() throws Exception {
    // Каталог копии уже есть, а описания нет: отказ приходит, когда описание и состав
    // конфигурации операция уже переписала
    Files.createDirectories(cf.resolve("Catalogs").resolve("Копия").resolve("Forms").resolve("Форма"));
    Map<String, String> before = tree(temp);
    Result result = run(Map.of("op", "cf-md-object-duplicate", "configurationXml", configuration,
      "objectXml", catalog, "tag", "Catalog", "sourceName", "Справочник1", "newName", "Копия"), false);

    assertThat(result.exit).isNotZero();
    assertThat(tree(temp)).isEqualTo(before);
  }

  /** Возвращает каталог к состоянию до теста: следующая операция начинает с того же. */
  private void prepareAgain() throws Exception {
    try (Stream<Path> all = Files.walk(temp)) {
      for (Path path : all.sorted(java.util.Comparator.reverseOrder()).toList()) {
        if (!path.equals(temp)) {
          Files.delete(path);
        }
      }
    }
    prepare();
  }

  private record Result(int exit, String out, String err) {
  }

  private Result run(Map<String, String> fields, boolean dryRun) throws IOException {
    Map<String, Object> params = new LinkedHashMap<>(fields);
    params.put("schemaVersion", "V2_20");
    params.put("dryRun", dryRun);
    params.computeIfPresent("autoName", (key, value) -> Boolean.parseBoolean((String) value));
    Path file = Files.createTempFile("params", ".json");
    Files.writeString(file, new Gson().toJson(params), StandardCharsets.UTF_8);
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
      Files.delete(file);
    }
  }

  /** Все файлы и каталоги: относительный путь и содержимое. */
  private static Map<String, String> tree(Path dir) throws IOException {
    Map<String, String> out = new TreeMap<>();
    try (Stream<Path> files = Files.walk(dir)) {
      for (Path path : files.toList()) {
        String name = dir.relativize(path).toString().replace('\\', '/');
        out.put(name, Files.isDirectory(path) ? "/" : java.util.HexFormat.of().formatHex(Files.readAllBytes(path)));
      }
    }
    return out;
  }
}
