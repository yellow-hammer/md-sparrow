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

import io.github.yellowhammer.designerxml.DesignerXml;
import io.github.yellowhammer.designerxml.SchemaVersion;
import io.github.yellowhammer.designerxml.reflect.JaxbReflect;

import jakarta.xml.bind.JAXBException;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Извлекает плоский список ссылок из {@code Configuration/ChildObjects} через JAXB (порядок — как в XSD:
 * блоки по типу в порядке {@code sequence}). Версионно-нейтрально: порядок и набор тегов берутся из
 * {@code @XmlType(propOrder)}/{@code @XmlElement} сгенерированной модели (см. {@link JaxbReflect#orderedStringLists}).
 */
public final class ConfigurationChildObjectsExtractor {

  private ConfigurationChildObjectsExtractor() {
  }

  /** Состав и свойство {@code Version} одного разбора. */
  record Contents(List<ChildObjectEntry> children, String version) {
  }

  /**
   * @param configurationXml путь к {@code Configuration.xml}
   * @param version          версия XSD/JAXB (должна совпадать с выгрузкой)
   */
  public static List<ChildObjectEntry> readChildObjects(Path configurationXml, SchemaVersion version)
    throws JAXBException, IOException {
    return read(configurationXml, version).children();
  }

  static Contents read(Path configurationXml, SchemaVersion version) throws JAXBException, IOException {
    Object mdo = JaxbReflect.value(DesignerXml.read(configurationXml, version));
    Object cfg = JaxbReflect.get(mdo, "getConfiguration");
    if (cfg == null) {
      throw new IOException("В Configuration.xml нет элемента Configuration");
    }
    String appVersion = JaxbReflect.getStringOptional(JaxbReflect.get(cfg, "getProperties"), "getVersion");
    Object child = JaxbReflect.get(cfg, "getChildObjects");
    List<ChildObjectEntry> out = new ArrayList<>();
    if (child != null) {
      for (Map.Entry<String, List<String>> e : JaxbReflect.orderedStringLists(child)) {
        append(out, e.getKey(), e.getValue());
      }
    }
    return new Contents(out, appVersion == null ? "" : appVersion.trim());
  }

  private static void append(List<ChildObjectEntry> out, String xmlTag, List<String> values) {
    if (values == null) {
      return;
    }
    for (String s : values) {
      if (s == null) {
        continue;
      }
      String t = s.trim();
      if (!t.isEmpty()) {
        out.add(new ChildObjectEntry(xmlTag, t));
      }
    }
  }
}
