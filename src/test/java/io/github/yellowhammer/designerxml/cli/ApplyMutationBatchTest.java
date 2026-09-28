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

import io.github.yellowhammer.designerxml.Cancellation;
import io.github.yellowhammer.designerxml.SamplesSubmodulePaths;
import io.github.yellowhammer.designerxml.SchemaVersion;
import io.github.yellowhammer.designerxml.cf.MdObjectPropertiesDto;
import io.github.yellowhammer.designerxml.cf.MdObjectPropertiesEdit;
import io.github.yellowhammer.designerxml.cf.SupportRules;
import io.github.yellowhammer.designerxml.staging.WriteSession;
import io.github.yellowhammer.designerxml.staging.WriteSessionFaults;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

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
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * Пакет операций {@code apply-mutation}: операции идут одним сеансом записи и видят
 * правки друг друга, первый отказ отменяет весь пакет, успешный публикуется
 * одной публикацией. После проверки без записи и после любого отказа файлы
 * остаются байт в байт прежними (#37).
 */
class ApplyMutationBatchTest {

  /** Выгрузка с немецким основным языком. */
  private static final Path LANGUAGE_FIXTURE =
    Path.of("src", "test", "resources", "cf-language-de").toAbsolutePath();

  /** Проект, записанный 1С:EDT, с немецким основным языком. */
  private static final Path EDT_FIXTURE =
    Path.of("src", "test", "resources", "edt-language-de", "Двуязычная").toAbsolutePath();

  /** Идентификатор справочника {@code Справочник1} голой выгрузки. */
  private static final String CATALOG_UUID = "c144a879-35a6-3d17-aa07-3efc25982be0";

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
  }

  // ---------- порядок и ответы ----------

  @Test
  void операцииВидятПравкиПредыдущихИОтвечаютМассивом() throws Exception {
    JsonObject batch = batch(
      op("add-md-object", "configurationXml", configuration, "type", "CATALOG", "name", "Товары"),
      op("cf-form-add", "objectXml", cf.resolve("Catalogs/Товары.xml").toString(), "name", "Форма"),
      op("cf-form-item-add", "formXml", cf.resolve("Catalogs/Товары/Forms/Форма/Ext/Form.xml").toString(),
        "payloadJson", "{\"input\": \"Поле\"}"));
    Map<String, String> before = tree(temp);

    Result dry = run(dryRun(batch));

    assertThat(dry.exit).as(dry.err).isZero();
    assertThat(tree(temp)).isEqualTo(before);
    JsonArray answers = JsonParser.parseString(dry.out).getAsJsonArray();
    assertThat(answers).hasSize(3);
    assertThat(answers.get(0).getAsString()).isEqualTo("OK");

    Result real = run(batch);

    assertThat(real.exit).as(real.err).isZero();
    assertThat(real.out).isEqualTo(dry.out);
    assertThat(Files.readString(cf.resolve("Catalogs/Товары/Forms/Форма/Ext/Form.xml"), StandardCharsets.UTF_8))
      .contains("name=\"Поле\"");
    assertThat(Files.readString(cf.resolve("Configuration.xml"), StandardCharsets.UTF_8))
      .contains("<Catalog>Товары</Catalog>");
  }

  @Test
  void новаяКонфигурацияСОбъектомБезЗаписиНичегоНеСоздаёт() throws Exception {
    Path target = temp.resolve("новая");
    JsonObject batch = batch(
      op("init-empty-cf", "targetCfRoot", target.toString()),
      op("add-md-object", "configurationXml", target.resolve("Configuration.xml").toString(),
        "type", "CATALOG", "autoName", true));
    Map<String, String> before = tree(temp);

    Result dry = run(dryRun(batch));

    assertThat(dry.exit).as(dry.err).isZero();
    assertThat(tree(temp)).isEqualTo(before);
    assertThat(target).doesNotExist();

    Result real = run(batch);

    assertThat(real.exit).as(real.err).isZero();
    assertThat(real.out).isEqualTo(dry.out);
    String name = JsonParser.parseString(real.out).getAsJsonArray().get(1).getAsString();
    assertThat(target.resolve("Catalogs").resolve(name + ".xml")).isRegularFile();
  }

  @Test
  void языкИзменённыйПредыдущейОперациейДостаётсяСледующей() throws Exception {
    Path project = SamplesSubmodulePaths.copy(LANGUAGE_FIXTURE, temp.resolve("de"));
    Path language = project.resolve("Languages/Немецкий.xml");
    Path partners = project.resolve("Catalogs/Партнеры.xml");
    MdObjectPropertiesDto languageDto = MdObjectPropertiesEdit.readDto(language, SchemaVersion.V2_20);
    assertThat(languageDto.scalars).containsEntry("LanguageCode", "de");
    languageDto.scalars.put("LanguageCode", "at");
    MdObjectPropertiesDto partnersDto = MdObjectPropertiesEdit.readDto(partners, SchemaVersion.V2_20);
    partnersDto.synonym = "Lieferanten";
    // Язык запоминает уже первая операция; описание конфигурации, по которому узнаётся
    // его правка, первая операция не меняет
    JsonObject batch = batch(
      op("cf-md-object-set", "objectXml", language.toString(), "payloadJson", new Gson().toJson(languageDto)),
      op("cf-md-object-set", "objectXml", partners.toString(), "payloadJson", new Gson().toJson(partnersDto)));

    Result result = run(batch);

    assertThat(result.exit).as(result.err).isZero();
    String xml = Files.readString(partners, StandardCharsets.UTF_8);
    assertThat(xml).contains("<v8:lang>at</v8:lang>", "<v8:content>Lieferanten</v8:content>");
    assertThat(xml).contains("<v8:lang>de</v8:lang>", "<v8:content>Geschäftspartner</v8:content>");
  }

  @Test
  void режимПоддержкиИзПредыдущейОперацииДействуетВСледующей() throws Exception {
    writeLockedRules();
    JsonObject form = op("cf-form-add", "objectXml", catalog, "name", "Форма");
    Map<String, String> before = tree(temp);

    // Справочник на поддержке: без разрешения правки форма не добавляется
    assertRefused(batch(form), before, "Операция 1 (cf-form-add): ", "на поддержке");

    Result allowed = run(batch(
      op("cf-support-object-mode-set", "objectXml", catalog, "name", "1"),
      form));

    assertThat(allowed.exit).as(allowed.err).isZero();
    assertThat(allowed.out.strip()).isEqualTo("[\"OK\",\"OK\"]");
    assertThat(cf.resolve("Catalogs/Справочник1/Forms/Форма/Ext/Form.xml")).isRegularFile();
  }

  @Test
  void безУчётаПоддержкиПакетПравитОбъектНаПоддержке() throws Exception {
    writeLockedRules();
    JsonObject batch = batch(op("cf-form-add", "objectXml", catalog, "name", "Форма"));
    batch.addProperty("ignoreSupport", true);

    Result result = run(batch);

    assertThat(result.exit).as(result.err).isZero();
    assertThat(cf.resolve("Catalogs/Справочник1/Forms/Форма/Ext/Form.xml")).isRegularFile();
  }

  // ---------- отказы ----------

  @Test
  void неверныйJsonПакета() throws Exception {
    Map<String, String> before = tree(temp);

    Result broken = run("{\"op\":\"batch\",\"operations\":[", WriteSession::open);
    Result notObject = run("{\"op\":\"batch\",\"operations\":[1]}", WriteSession::open);

    assertThat(broken.exit).isEqualTo(2);
    assertThat(broken.err).contains("некорректный JSON параметров");
    assertThat(notObject.exit).isEqualTo(2);
    assertThat(notObject.err).contains("некорректный JSON параметров");
    assertThat(tree(temp)).isEqualTo(before);
  }

  @Test
  void неверныеПоляОпераций() throws Exception {
    Map<String, String> before = tree(temp);
    JsonObject added = op("add-md-object", "configurationXml", configuration, "type", "CATALOG", "name", "Товары");
    JsonObject wrongType = op("cf-form-add", "objectXml", catalog);
    wrongType.add("name", new JsonObject());

    assertRefused(batch(), before, "в пакете нет операций");
    JsonObject withoutOperations = new JsonObject();
    withoutOperations.addProperty("op", "batch");
    assertRefused(withoutOperations, before, "в пакете нет операций");
    assertRefused(batch(added, wrongType), before, "Операция 2 (cf-form-add): некорректный JSON параметров");
    assertRefused(batch(added, op("cf-form-add", "objectXml", catalog)), before,
      "Операция 2 (cf-form-add): обязательное поле не задано: name");
    assertRefused(batch(added, op("cf-form-add", "objectXml", catalog, "name", "Форма", "dryRun", true)), before,
      "Операция 2 (cf-form-add): поле dryRun задаётся у пакета, а не у его операции");
    assertRefused(batch(added, op("cf-form-add", "objectXml", catalog, "name", "Форма", "ignoreSupport", true)),
      before, "Операция 2 (cf-form-add): поле ignoreSupport задаётся у пакета");
    assertRefused(batch(added, op("batch")), before, "Операция 2 (batch): пакет не вкладывается в пакет");
    assertRefused(batch(added, new JsonObject()), before, "Операция 2: в параметрах не задан op");
  }

  @Test
  void неизвестнаяОперация() throws Exception {
    Map<String, String> before = tree(temp);

    assertRefused(batch(
      op("add-md-object", "configurationXml", configuration, "type", "CATALOG", "name", "Товары"),
      op("cf-no-such-op")), before, "Операция 2 (cf-no-such-op): неизвестный op: cf-no-such-op");
  }

  @Test
  void конфликтИмени() throws Exception {
    Map<String, String> before = tree(temp);

    // Имя занимает предыдущая операция того же пакета
    assertRefused(batch(
      op("add-md-object", "configurationXml", configuration, "type", "CATALOG", "name", "Товары"),
      op("cf-md-object-rename", "configurationXml", configuration, "objectXml", catalog, "tag", "Catalog",
        "oldName", "Справочник1", "newName", "Товары")),
      before, "Операция 2 (cf-md-object-rename): ");
    assertRefused(batch(
      op("cf-form-add", "objectXml", catalog, "name", "Форма"),
      op("cf-form-add", "objectXml", catalog, "name", "Форма")),
      before, "Операция 2 (cf-form-add): ");
  }

  @Test
  void отказВСерединеПослеУспешныхОпераций() throws Exception {
    Map<String, String> before = tree(temp);

    assertRefused(batch(
      op("add-md-object", "configurationXml", configuration, "type", "CATALOG", "name", "Товары"),
      op("cf-form-add", "objectXml", cf.resolve("Catalogs/Товары.xml").toString(), "name", "Форма"),
      op("cf-form-item-add", "formXml", cf.resolve("Catalogs/Товары/Forms/Другая/Ext/Form.xml").toString(),
        "payloadJson", "{\"input\": \"Поле\"}"),
      op("cf-form-add", "objectXml", catalog, "name", "Форма")),
      before, "Операция 3 (cf-form-item-add): ");
  }

  @Test
  void файлИзменилсяНаДискеДоПубликации() throws Exception {
    Path configurationFile = Path.of(configuration);
    for (boolean dryRun : new boolean[] {true, false}) {
      String text = Files.readString(configurationFile, StandardCharsets.UTF_8);
      WriteSession session = WriteSession.open();
      // Пакет прочитал описание конфигурации, а потом его поправили на диске
      Files.readAllBytes(session.path(configurationFile));
      Files.writeString(configurationFile, text + " ", StandardCharsets.UTF_8);
      Map<String, String> before = tree(temp);
      JsonObject batch = batch(
        op("add-md-object", "configurationXml", configuration, "type", "CATALOG", "name", "Товары"),
        op("cf-form-add", "objectXml", cf.resolve("Catalogs/Товары.xml").toString(), "name", "Форма"));

      Result result = run((dryRun ? dryRun(batch) : batch).toString(), () -> session);

      assertThat(result.exit).isEqualTo(2);
      assertThat(result.out).isEmpty();
      assertThat(result.err).contains("Файл изменился на диске во время операции", "Configuration.xml");
      assertThat(tree(temp)).isEqualTo(before);
    }
  }

  @Test
  void сбойЗаписиПриПубликацииОткатываетсяЦеликом() throws Exception {
    Map<String, String> before = tree(temp);
    JsonObject batch = batch(
      op("add-md-object", "configurationXml", configuration, "type", "CATALOG", "name", "Товары"),
      op("cf-form-add", "objectXml", cf.resolve("Catalogs/Товары.xml").toString(), "name", "Форма"),
      op("cf-form-add", "objectXml", catalog, "name", "Форма"));

    Result result = run(batch.toString(), () -> WriteSessionFaults.failingOnWrite(3));

    assertThat(result.exit).isEqualTo(2);
    assertThat(result.out).isEmpty();
    assertThat(result.err).contains("Изменения не записаны, диск возвращён в прежнее состояние", "сбой записи");
    assertThat(tree(temp)).isEqualTo(before);
  }

  @Test
  void отменаПрерываетПакетДоПубликации() throws Exception {
    Map<String, String> before = tree(temp);
    Cancellation.Token token = new Cancellation.Token();
    token.cancel();

    Result result = Cancellation.run(token, () -> run(batch(
      op("add-md-object", "configurationXml", configuration, "type", "CATALOG", "name", "Товары"))));

    assertThat(token.stopped()).isTrue();
    assertThat(result.exit).isNotZero();
    assertThat(tree(temp)).isEqualTo(before);
  }

  // ---------- проект 1С:EDT ----------

  @Test
  void проектEdt() throws Exception {
    Path project = SamplesSubmodulePaths.copy(EDT_FIXTURE, temp.resolve("edt"));
    Path catalogMdo = project.resolve("src/Catalogs/Waren/Waren.mdo");
    Path form = project.resolve("src/Catalogs/Waren/Forms/Форма/Form.form");
    JsonObject batch = batch(
      op("add-md-object", "configurationXml", project.resolve("src/Configuration/Configuration.mdo").toString(),
        "type", "CATALOG", "name", "Waren", "synonym", "Warenkatalog"),
      op("cf-form-add", "objectXml", catalogMdo.toString(), "name", "Форма"),
      op("cf-form-item-add", "formXml", form.toString(), "payloadJson", "{\"input\": \"Поле\", \"title\": \"Feld\"}"));
    Map<String, String> before = tree(temp);

    Result dry = run(dryRun(batch));

    assertThat(dry.exit).as(dry.err).isZero();
    assertThat(tree(temp)).isEqualTo(before);

    // Привязка к реквизиту, которого у новой формы нет, отменяет весь пакет
    JsonObject failing = batch.deepCopy();
    failing.getAsJsonArray("operations").add(
      op("cf-form-item-bind", "formXml", form.toString(), "itemId", "1", "dataPath", "Объект.Code"));
    assertRefused(failing, before, "Операция 4 (cf-form-item-bind): ");

    Result real = run(batch);

    assertThat(real.exit).as(real.err).isZero();
    assertThat(real.out).isEqualTo(dry.out);
    assertThat(Files.readString(catalogMdo, StandardCharsets.UTF_8)).contains("<value>Warenkatalog</value>");
    assertThat(Files.readString(form, StandardCharsets.UTF_8))
      .contains("<name>Поле</name>", "<key>de</key>", "<value>Feld</value>");
  }

  // ---------- вспомогательное ----------

  /** Правила поддержки: справочник на поддержке без права изменения, изменение поставки включено. */
  private void writeLockedRules() throws IOException {
    String rules = "﻿{6,0,1,"
      + "dddddddd-4444-4444-8444-444444444444,0,"
      + "eeeeeeee-5555-4555-8555-555555555555,"
      + "\"1.0.0.1\",\"Поставщик\",\"ТестоваяПоставка\",1,"
      + "0,0," + CATALOG_UUID + "," + CATALOG_UUID + ","
      + "0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}";
    Files.createDirectories(SupportRules.vendorPayloadDir(cf));
    Files.writeString(SupportRules.vendorPayloadDir(cf).resolve("ТестоваяПоставка.cf"), "поставка");
    Files.write(SupportRules.rulesPath(cf), rules.getBytes(StandardCharsets.UTF_8));
  }

  /** Пакет отказывает и без записи, и настоящим запуском, а файлы остаются прежними. */
  private void assertRefused(JsonObject batch, Map<String, String> before, String... messages) throws IOException {
    for (JsonObject params : new JsonObject[] {dryRun(batch), batch}) {
      Result result = run(params);
      assertThat(result.exit).as(result.err).isEqualTo(2);
      assertThat(result.out).isEmpty();
      assertThat(result.err).contains(messages);
      assertThat(tree(temp)).isEqualTo(before);
    }
  }

  private static JsonObject batch(JsonObject... operations) {
    JsonObject batch = new JsonObject();
    batch.addProperty("op", "batch");
    JsonArray list = new JsonArray();
    for (JsonObject operation : operations) {
      list.add(operation);
    }
    batch.add("operations", list);
    return batch;
  }

  private static JsonObject dryRun(JsonObject batch) {
    JsonObject copy = batch.deepCopy();
    copy.addProperty("dryRun", true);
    return copy;
  }

  /** Операция: имя и пары поле-значение; формат выгрузки задан у каждой. */
  private static JsonObject op(String name, Object... fields) {
    JsonObject op = new JsonObject();
    op.addProperty("op", name);
    op.addProperty("schemaVersion", "V2_20");
    if (fields.length % 2 != 0) {
      throw new IllegalArgumentException("поля операции задаются парами имя-значение");
    }
    for (int i = 0; i + 1 < fields.length; i += 2) {
      Object value = fields[i + 1];
      if (value instanceof Boolean flag) {
        op.addProperty((String) fields[i], flag);
      } else {
        op.addProperty((String) fields[i], (String) value);
      }
    }
    return op;
  }

  private record Result(int exit, String out, String err) {
  }

  private Result run(JsonObject params) throws IOException {
    return run(params.toString(), WriteSession::open);
  }

  private Result run(String params, Supplier<WriteSession> sessions) throws IOException {
    Path file = Files.createTempFile("params", ".json");
    Files.writeString(file, params, StandardCharsets.UTF_8);
    ApplyMutationCmd command = new ApplyMutationCmd();
    command.sessions = sessions;
    CommandLine.IFactory factory = new CommandLine.IFactory() {
      @Override
      public <K> K create(Class<K> type) throws Exception {
        return type == ApplyMutationCmd.class ? type.cast(command) : CommandLine.defaultFactory().create(type);
      }
    };
    PrintStream stdout = System.out;
    PrintStream stderr = System.err;
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    ByteArrayOutputStream err = new ByteArrayOutputStream();
    try {
      System.setOut(new PrintStream(out, true, StandardCharsets.UTF_8));
      System.setErr(new PrintStream(err, true, StandardCharsets.UTF_8));
      int exit = new CommandLine(new DesignerXmlCli(), factory).execute("apply-mutation", "--params", file.toString());
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
