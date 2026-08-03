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
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Имена объектов из {@code Configuration/ChildObjects} по локальному имени тега (как в XSD).
 * Версионно-нейтрально: навигация по JAXB-объекту через {@link JaxbReflect}.
 */
public final class ConfigurationChildObjectLister {

  private ConfigurationChildObjectLister() {
  }

  /**
   * Имена объектов конфигурации по тегу состава.
   *
   * <p>Список тегов не перечисляем: он берётся из самой модели формата, иначе новый вид
   * пришлось бы дописывать здесь руками, а его отсутствие выглядело бы как ошибка вызова.
   *
   * @param childTag локальное имя тега, например {@code Catalog}, {@code SettingsStorage}
   */
  public static List<String> listNames(Path configurationXml, SchemaVersion version, String childTag)
    throws JAXBException, IOException {
    Objects.requireNonNull(childTag, "childTag");
    Object mdo = JaxbReflect.value(DesignerXml.read(configurationXml, version));
    Object cfg = JaxbReflect.get(mdo, "getConfiguration");
    if (cfg == null) {
      throw new IllegalStateException("Configuration.xml has no Configuration element");
    }
    Object child = JaxbReflect.get(cfg, "getChildObjects");
    if (child == null) {
      return new ArrayList<>();
    }
    Object names = JaxbReflect.getOptional(child, "get" + childTag);
    if (names == null) {
      throw new IllegalArgumentException(
        "в составе конфигурации формата " + version.metadataObjectVersionAttribute()
          + " нет тега " + childTag);
    }
    if (!(names instanceof List<?> list)) {
      throw new IllegalArgumentException(childTag + ": тег состава не является списком имён");
    }
    List<String> raw = new ArrayList<>();
    for (Object name : list) {
      raw.add(String.valueOf(name));
    }
    return sortedCopy(raw);
  }

  private static List<String> sortedCopy(List<String> raw) {
    List<String> sorted = new ArrayList<>(raw);
    Collections.sort(sorted);
    return sorted;
  }
}
