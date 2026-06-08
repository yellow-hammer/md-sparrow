/*
 * This file is a part of md-sparrow.
 *
 * SPDX-License-Identifier: LGPL-3.0-or-later
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

  /**
   * @param configurationXml путь к {@code Configuration.xml}
   * @param version          версия XSD/JAXB (должна совпадать с выгрузкой)
   */
  public static List<ChildObjectEntry> readChildObjects(Path configurationXml, SchemaVersion version)
    throws JAXBException, IOException {
    Object mdo = JaxbReflect.value(DesignerXml.read(configurationXml, version));
    Object cfg = JaxbReflect.get(mdo, "getConfiguration");
    if (cfg == null) {
      throw new IOException("В Configuration.xml нет элемента Configuration");
    }
    Object child = JaxbReflect.get(cfg, "getChildObjects");
    if (child == null) {
      return List.of();
    }
    List<ChildObjectEntry> out = new ArrayList<>();
    for (Map.Entry<String, List<String>> e : JaxbReflect.orderedStringLists(child)) {
      append(out, e.getKey(), e.getValue());
    }
    return out;
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
