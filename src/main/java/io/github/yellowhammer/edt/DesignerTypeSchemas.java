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
package io.github.yellowhammer.edt;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import com.ctc.wstx.stax.WstxInputFactory;

import io.github.yellowhammer.designerxml.cf.MdTypeDescriptionBridge;

/**
 * Типы платформы, объявленные схемами выгрузки конфигуратора.
 *
 * Конфигуратор записывает тип реквизита именем с пространством имён, и какое
 * пространство у типа, знает только его схема: хранилище значения объявлено в
 * схеме ядра, картинка - в схеме оформления, компоновщик настроек - в схеме
 * компоновки данных. Схемы лежат в сборке, и имя ищется в них, а не в списке.
 *
 * Типы самой конфигурации схемами не объявлены: их пространство достаётся всему,
 * что в схемах не нашлось.
 */
final class DesignerTypeSchemas {

  private static final String SCHEMAS = "/designer-schemas";

  /** Пространство имён конфигурации: его типы в схемах не перечислены. */
  private static final String CONFIGURATION_PREFIX = "cfg";

  /** Имена типов по префиксу пространства имён, в порядке схем сборки. */
  private static volatile Map<String, Set<String>> declared;

  private DesignerTypeSchemas() {
  }

  /**
   * Тип в записи конфигуратора.
   *
   * @param prefix префикс пространства имён
   * @param name имя типа в схеме
   */
  record Declared(String prefix, String name) {

    /** Запись типа: {@code v8:ValueStorage}. */
    String text() {
      return prefix + ":" + name;
    }
  }

  /**
   * Объявление типа платформы по его имени в 1С:EDT.
   *
   * @param name имя типа без пространства имён: {@code ValueStorage}
   * @return объявление либо {@code null}, если такого типа в схемах нет
   */
  static Declared find(String name) {
    for (Map.Entry<String, Set<String>> schema : declared().entrySet()) {
      if (schema.getValue().contains(name)) {
        return new Declared(schema.getKey(), name);
      }
    }
    return null;
  }

  private static Map<String, Set<String>> declared() {
    Map<String, Set<String>> loaded = declared;
    if (loaded == null) {
      try {
        loaded = load();
      } catch (IOException | XMLStreamException error) {
        throw new UncheckedIOException(new IOException("Не удалось прочитать схемы конфигуратора из сборки", error));
      }
      declared = loaded;
    }
    return loaded;
  }

  private static Map<String, Set<String>> load() throws IOException, XMLStreamException {
    Map<String, Set<String>> byPrefix = new LinkedHashMap<>();
    for (String name : schemaNames()) {
      try (InputStream stream = resource(name)) {
        Schema schema = read(stream);
        String prefix = MdTypeDescriptionBridge.prefixOfNamespace(schema.namespace());
        if (prefix.isEmpty() || prefix.equals(CONFIGURATION_PREFIX)) {
          continue;
        }
        byPrefix.computeIfAbsent(prefix, key -> new LinkedHashSet<>()).addAll(schema.types());
      }
    }
    return byPrefix;
  }

  /** Схема: её пространство имён и объявленные типы. */
  private record Schema(String namespace, Set<String> types) {
  }

  /** Имена типов верхнего уровня: вложенные объявления типами платформы не являются. */
  private static Schema read(InputStream stream) throws XMLStreamException {
    XMLInputFactory factory = new WstxInputFactory();
    factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
    factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
    XMLStreamReader reader = factory.createXMLStreamReader(stream);
    String namespace = "";
    Set<String> types = new LinkedHashSet<>();
    try {
      int depth = 0;
      while (reader.hasNext()) {
        int event = reader.next();
        if (event == XMLStreamConstants.START_ELEMENT) {
          depth++;
          if (depth == 1) {
            namespace = attribute(reader, "targetNamespace");
          } else if (depth == 2 && isTypeDeclaration(reader.getLocalName())) {
            String name = attribute(reader, "name");
            if (!name.isEmpty()) {
              types.add(name);
            }
          }
        } else if (event == XMLStreamConstants.END_ELEMENT) {
          depth--;
        }
      }
    } finally {
      reader.close();
    }
    return new Schema(namespace, types);
  }

  private static boolean isTypeDeclaration(String localName) {
    return localName.equals("complexType") || localName.equals("simpleType");
  }

  private static String attribute(XMLStreamReader reader, String name) {
    String value = reader.getAttributeValue(null, name);
    return value == null ? "" : value.trim();
  }

  /** Имена файлов схем: каталог ресурсов внутри jar не перечислить. */
  private static List<String> schemaNames() throws IOException {
    List<String> names = new ArrayList<>();
    try (BufferedReader reader = new BufferedReader(
        new InputStreamReader(resource("index.txt"), StandardCharsets.UTF_8))) {
      for (String line = reader.readLine(); line != null; line = reader.readLine()) {
        String name = line.trim();
        if (!name.isEmpty()) {
          names.add(name);
        }
      }
    }
    return names;
  }

  private static InputStream resource(String name) throws IOException {
    InputStream stream = DesignerTypeSchemas.class.getResourceAsStream(SCHEMAS + "/" + name);
    if (stream == null) {
      throw new IOException("В сборке нет схемы конфигуратора: " + SCHEMAS + "/" + name);
    }
    return stream;
  }
}
