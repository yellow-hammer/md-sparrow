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

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import io.github.yellowhammer.designerxml.SchemaVersion;
import io.github.yellowhammer.edt.EdtLayout;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Function;

/**
 * Параметры команд {@code apply-mutation}/{@code read-json}, читаемые из UTF-8 JSON-файла.
 *
 * <p>Канал нужен, чтобы не передавать кириллические пути и имена через {@code argv}: на Windows
 * лаунчер {@code java.exe} декодирует {@code argv} через ANSI-кодовую страницу ОС (её расширение
 * не контролирует, а {@code -Dsun.jnu.encoding} на это не влияет — свойство read-only), из-за чего
 * не-ASCII значения превращаются в {@code ?}. Здесь все строки приходят из UTF-8 JSON, а в
 * {@code argv} остаётся только ASCII-путь к файлу параметров.
 */
final class CliParams {
  /** Операция пакета: вложенные операции идут одним сеансом записи и одной публикацией. */
  static final String BATCH = "batch";

  /** Поля пакета: их задают у пакета целиком, а не у его операций. */
  private static final java.util.List<String> BATCH_FIELDS = java.util.List.of("dryRun", "ignoreSupport", "operations");

  /** Операция; совпадает с именем соответствующей одиночной подкоманды. */
  String op;
  /** Операции пакета ({@code op = batch}): объекты с полем {@code op} и полями операции. */
  java.util.List<JsonObject> operations;
  String configurationXml;
  String objectXml;
  /** Файл содержимого формы: {@code Forms/<Имя>/Ext/Form.xml}. */
  String formXml;
  String artifactsRoot;
  /** Каталог новой конфигурации для init-empty-cf: {@code src/cf} либо каталог проекта EDT. */
  String targetCfRoot;
  /** Формат новых исходников: {@code designer} (по умолчанию) или {@code edt}. */
  String format;
  /** Имя нового проекта EDT; по умолчанию имя его каталога. */
  String projectName;

  /** Каталог расширения для init-empty-cfe. */
  String targetCfeRoot;
  /** Configuration.xml расширяемой конфигурации: источник режимов совместимости. */
  String mainConfigurationXml;

  /** Префикс имён объектов расширения. */
  String namePrefix;

  /** Назначение расширения: patch, customization, add-on. */
  String purpose;

  /** Режим совместимости расширения из основной конфигурации. */
  String compatibilityMode;

  /** Режим совместимости интерфейса из основной конфигурации. */
  String interfaceCompatibilityMode;
  /** Каталог проверяемой выгрузки: {@code src/cf} или каталог расширения. */
  String cfRoot;
  String projectRoot;
  /** Каталоги исходников относительно projectRoot (null — стандартные src/cf, src/cfe, src/epf, src/erf). */
  String cfDir;
  String cfeDir;
  String epfDir;
  String erfDir;
  /** Точные каталоги расширений и внешних объектов: заменяют перечисление подкаталогов. */
  java.util.List<String> cfeDirs;
  java.util.List<String> epfDirs;
  java.util.List<String> erfDirs;
  String tag;
  String name;
  String oldName;
  String newName;
  String sourceName;
  String tabularSection;
  /** Номер элемента формы (атрибут {@code id}) для структурной правки. */
  String itemId;
  /** Номер элемента-владельца формы; пусто - сама форма. */
  String parentId;
  /** Номер элемента того же владельца, перед которым встаёт элемент; пусто - в конец. */
  String beforeId;
  /** Путь к данным элемента формы: {@code Объект.Реквизит}. */
  String dataPath;
  /** Имя события платформы у формы или элемента: {@code OnCreateAtServer}, {@code OnChange}. */
  String event;
  /** Процедура модуля формы, обрабатывающая событие; пусто - снять обработчик. */
  String handler;
  /** Вид вызова обработчика у формы расширения: {@code Before}, {@code After}, {@code Override}. */
  String callType;
  /** Версия схемы в формате {@code V2_20} (как флаг {@code -v}). */
  String schemaVersion;
  String type;
  String kind;
  String synonym;
  boolean synonymEmpty;
  boolean autoName;
  /**
   * Не смотреть на правила поддержки: правка идёт так, будто поставки нет.
   *
   * <p>Решение принимает вызывающая программа: у неё это отдельная настройка.
   */
  boolean ignoreSupport;
  /**
   * Проверка без записи: операция проходит целиком в памяти, ответ тот же, что у
   * настоящей, а диск не меняется.
   */
  boolean dryRun;
  /** Полезная нагрузка для set-операций: JSON DTO как строка (вместо отдельного файла). */
  /** Отпечаток прочитанных правил поддержки: правка поверх устаревшего снимка отклоняется. */
  String expectedGeneration;

  String payloadJson;

  /** Путь из значения поля: относительный считается от рабочего каталога команды. */
  private transient Function<String, Path> paths = Path::of;

  /**
   * Читает параметры из UTF-8 JSON-файла.
   *
   * @param paths путь из значения поля
   */
  static CliParams read(Path paramsFile, Function<String, Path> paths) throws IOException, JsonSyntaxException {
    String json = Files.readString(paramsFile, StandardCharsets.UTF_8);
    CliParams p = new Gson().fromJson(json, CliParams.class);
    if (p == null || p.op == null || p.op.isBlank()) {
      throw new IllegalArgumentException("в параметрах не задан op");
    }
    p.paths = paths;
    return p;
  }

  /**
   * Операция пакета: поля берутся из её элемента, а проверка без записи и правила
   * поддержки - у пакета.
   *
   * @param index номер операции с нуля
   * @throws IllegalArgumentException если у операции нет {@code op}, она сама пакет
   *     или задаёт поле пакета
   * @throws JsonSyntaxException если поле операции не того типа
   */
  CliParams operation(int index) {
    JsonObject fields = operations.get(index);
    if (fields == null) {
      throw new IllegalArgumentException("операция не задана");
    }
    for (String field : BATCH_FIELDS) {
      if (fields.has(field)) {
        throw new IllegalArgumentException("поле " + field + " задаётся у пакета, а не у его операции");
      }
    }
    CliParams p = new Gson().fromJson(fields, CliParams.class);
    if (p == null || p.op == null || p.op.isBlank()) {
      throw new IllegalArgumentException("в параметрах не задан op");
    }
    if (BATCH.equals(p.op)) {
      throw new IllegalArgumentException("пакет не вкладывается в пакет");
    }
    p.dryRun = dryRun;
    p.ignoreSupport = ignoreSupport;
    p.paths = paths;
    return p;
  }

  /**
   * Имя операции пакета для текста отказа.
   *
   * @param index номер операции с нуля
   * @return {@code " (op)"} либо пусто, если имени нет
   */
  String operationName(int index) {
    JsonObject fields = operations.get(index);
    JsonElement name = fields == null ? null : fields.get("op");
    return name != null && name.isJsonPrimitive() && !name.getAsString().isBlank()
      ? " (" + name.getAsString() + ")"
      : "";
  }

  String req(String value, String field) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("обязательное поле не задано: " + field);
    }
    return value;
  }

  Path path(String value) {
    return paths.apply(value);
  }

  Path reqPath(String value, String field) {
    return path(req(value, field));
  }

  SchemaVersion version() {
    // Схему компоновки описывает одна и та же модель во всех версиях формата, а
    // у проекта 1С:EDT версии выгрузки нет вовсе
    if ((schemaVersion == null || schemaVersion.isBlank()) && EdtLayout.isSchemaFile(objectXml)) {
      SchemaVersion[] all = SchemaVersion.values();
      return all[all.length - 1];
    }
    String v = req(schemaVersion, "schemaVersion");
    try {
      return SchemaVersion.valueOf(v);
    } catch (IllegalArgumentException e) {
      SchemaVersion[] all = SchemaVersion.values();
      throw new IllegalArgumentException("формат выгрузки " + v.replaceFirst("^V", "").replace('_', '.')
        + " не поддержан; поддержаны " + all[0].metadataObjectVersionAttribute()
        + "-" + all[all.length - 1].metadataObjectVersionAttribute());
    }
  }
}
