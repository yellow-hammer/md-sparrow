/*
 * This file is a part of md-sparrow.
 *
 * Copyright (c) 2026
 * Ivan Karlo <i.karlo@outlook.com> and contributors
 *
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package io.github.yellowhammer.designerxml.cf;

import io.github.yellowhammer.designerxml.SchemaVersion;
import io.github.yellowhammer.designerxml.reflect.JaxbReflect;

import jakarta.xml.bind.JAXBElement;
import jakarta.xml.bind.JAXBException;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Структура схемы компоновки данных: наборы с полями и запросами, вычисляемые
 * и итоговые поля, параметры. Чтение идёт JAXB-моделью схемы dcs.
 */
public final class DcsRead {

  private DcsRead() {
  }

  /** Структура схемы для JSON-ответа. */
  public static Map<String, Object> info(Path templateXml, SchemaVersion version)
    throws IOException, JAXBException {
    Object schema = schemaRoot(templateXml, version);
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("dataSets", dataSets(schema));
    out.put("calculatedFields", namedList(schema, "getCalculatedField", "getDataPath", "getExpression"));
    out.put("totalFields", namedList(schema, "getTotalField", "getDataPath", "getExpression"));
    out.put("parameters", namedList(schema, "getParameter", "getName", "getTitle"));
    return out;
  }

  /** Схема читается моделью: ошибка разбора возвращается текстом. */
  public static Map<String, Object> validate(Path templateXml, SchemaVersion version) {
    Map<String, Object> out = new LinkedHashMap<>();
    try {
      Object schema = schemaRoot(templateXml, version);
      List<?> dataSets = JaxbReflect.listOptional(schema, "getDataSet");
      out.put("valid", true);
      out.put("dataSetCount", dataSets.size());
    } catch (Exception e) {
      out.put("valid", false);
      out.put("error", e.getMessage());
    }
    return out;
  }

  private static Object schemaRoot(Path templateXml, SchemaVersion version) throws IOException, JAXBException {
    // Платформа пишет корень DataCompositionSchema с заглавной, а схема XSD
    // объявляет строчный элемент: читаем с явным типом корня
    Class<?> schemaClass;
    try {
      schemaClass = Class.forName(
        "io.github.yellowhammer.designerxml.jaxb." + version.name().toLowerCase(java.util.Locale.ROOT)
          + ".v8_1_dcs_schema.DataCompositionSchema");
    } catch (ClassNotFoundException e) {
      throw new IllegalStateException("Модель схемы компоновки не собрана для " + version, e);
    }
    try (var in = java.nio.file.Files.newInputStream(templateXml)) {
      JAXBElement<?> element = version.jaxbContext().createUnmarshaller()
        .unmarshal(new javax.xml.transform.stream.StreamSource(in), schemaClass);
      Object value = element.getValue();
      if (value == null) {
        throw new IllegalArgumentException("Файл не является схемой компоновки данных.");
      }
      return value;
    }
  }

  private static List<Map<String, Object>> dataSets(Object schema) {
    List<Map<String, Object>> out = new ArrayList<>();
    for (Object dataSet : JaxbReflect.<Object>listOptional(schema, "getDataSet")) {
      Map<String, Object> item = new LinkedHashMap<>();
      item.put("name", text(dataSet, "getName"));
      Object query = JaxbReflect.getOptional(dataSet, "getQuery");
      if (query != null) {
        item.put("query", String.valueOf(query));
      }
      List<Map<String, Object>> fields = new ArrayList<>();
      for (Object field : JaxbReflect.<Object>listOptional(dataSet, "getField")) {
        Map<String, Object> fieldItem = new LinkedHashMap<>();
        fieldItem.put("dataPath", text(field, "getDataPath"));
        String source = text(field, "getField");
        if (!source.isEmpty()) {
          fieldItem.put("field", source);
        }
        fields.add(fieldItem);
      }
      if (!fields.isEmpty()) {
        item.put("fields", fields);
      }
      out.add(item);
    }
    return out;
  }

  private static List<Map<String, Object>> namedList(
    Object schema, String listGetter, String keyGetter, String valueGetter) {
    List<Map<String, Object>> out = new ArrayList<>();
    for (Object item : JaxbReflect.<Object>listOptional(schema, listGetter)) {
      Map<String, Object> row = new LinkedHashMap<>();
      row.put("name", text(item, keyGetter));
      String value = text(item, valueGetter);
      if (!value.isEmpty()) {
        row.put("value", value);
      }
      out.add(row);
    }
    return out;
  }

  private static String text(Object target, String getter) {
    Object value = JaxbReflect.getOptional(target, getter);
    if (value == null) {
      return "";
    }
    if (value instanceof List<?> list) {
      // Локализованные строки: берём первый элемент содержимого
      for (Object item : list) {
        Object content = JaxbReflect.getOptional(item, "getContent");
        if (content != null) {
          return String.valueOf(content);
        }
      }
      return "";
    }
    return String.valueOf(value);
  }
}
