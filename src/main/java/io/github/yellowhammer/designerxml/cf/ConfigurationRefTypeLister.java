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

import jakarta.xml.bind.JAXBException;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Ссылочные типы конфигурации: чем может быть реквизит или измерение.
 *
 * <p>Собирается из состава конфигурации и карты ссылочных типов платформы, поэтому потребителю
 * не нужно знать, у какого вида объекта ссылочный тип есть, а у какого нет, и как он называется.
 */
public final class ConfigurationRefTypeLister {

  private ConfigurationRefTypeLister() {
  }

  /**
   * Ссылочные типы конфигурации, сгруппированные по виду объекта.
   *
   * <p>Ключ - вид объекта как в составе конфигурации ({@code Catalog}), значения - готовые тексты
   * типов ({@code cfg:CatalogRef.Контрагенты}) в том виде, в каком они лежат в описании типа.
   *
   * @param configurationXml файл конфигурации
   * @param version версия формата выгрузки
   * @return вид объекта -&gt; тексты ссылочных типов; виды без объектов не попадают
   */
  public static Map<String, List<String>> listRefTypes(Path configurationXml, SchemaVersion version)
    throws JAXBException, IOException {
    Map<String, List<String>> byChildTag = ConfigurationChildObjectLister.listAll(configurationXml, version);
    Map<String, List<String>> out = new LinkedHashMap<>();
    for (Map.Entry<String, String> entry : MetadataRefParser.refTypeSuffixesByObjectType().entrySet()) {
      List<String> names = byChildTag.get(entry.getKey());
      if (names == null || names.isEmpty()) {
        continue;
      }
      List<String> types = new ArrayList<>();
      for (String name : names) {
        types.add("cfg:" + entry.getValue() + "." + name);
      }
      out.put(entry.getKey(), types);
    }
    return out;
  }
}
