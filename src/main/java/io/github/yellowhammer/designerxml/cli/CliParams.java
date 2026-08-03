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
import com.google.gson.JsonSyntaxException;
import io.github.yellowhammer.designerxml.SchemaVersion;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

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
  /** Операция; совпадает с именем соответствующей одиночной подкоманды. */
  String op;
  String configurationXml;
  String objectXml;
  String artifactsRoot;
  String targetCfRoot;

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
  String tag;
  String name;
  String oldName;
  String newName;
  String sourceName;
  String tabularSection;
  /** Версия схемы в формате {@code V2_20} (как флаг {@code -v}). */
  String schemaVersion;
  String type;
  String kind;
  String synonymRu;
  boolean synonymEmpty;
  boolean autoName;
  /** Полезная нагрузка для set-операций: JSON DTO как строка (вместо отдельного файла). */
  String payloadJson;

  /** Читает параметры из UTF-8 JSON-файла. */
  static CliParams read(Path paramsFile) throws IOException, JsonSyntaxException {
    String json = Files.readString(paramsFile, StandardCharsets.UTF_8);
    CliParams p = new Gson().fromJson(json, CliParams.class);
    if (p == null || p.op == null || p.op.isBlank()) {
      throw new IllegalArgumentException("в параметрах не задан op");
    }
    return p;
  }

  String req(String value, String field) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("обязательное поле не задано: " + field);
    }
    return value;
  }

  Path reqPath(String value, String field) {
    return Path.of(req(value, field));
  }

  SchemaVersion version() {
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
