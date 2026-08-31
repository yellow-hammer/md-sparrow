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
import java.util.LinkedHashMap;
import java.util.Map;
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

  /**
   * Весь состав конфигурации одним чтением: вид объекта - имена.
   *
   * <p>Панели с деревом состава нужен полный список объектов; читать его по
   * одному виду - сорок вызовов JVM вместо одного.
   *
   * @return локальное имя тега -> отсортированные имена; пустые виды опущены
   */
  public static Map<String, List<String>> listAll(Path configurationXml, SchemaVersion version)
    throws JAXBException, IOException {
    Object mdo = JaxbReflect.value(DesignerXml.read(configurationXml, version));
    Object cfg = JaxbReflect.get(mdo, "getConfiguration");
    if (cfg == null) {
      throw new IllegalStateException("Configuration.xml has no Configuration element");
    }
    Object child = JaxbReflect.get(cfg, "getChildObjects");
    Map<String, List<String>> out = new LinkedHashMap<>();
    if (child == null) {
      return out;
    }
    for (java.lang.reflect.Method method : child.getClass().getMethods()) {
      if (!method.getName().startsWith("get") || method.getParameterCount() != 0) {
        continue;
      }
      if (!List.class.isAssignableFrom(method.getReturnType())) {
        continue;
      }
      Object value;
      try {
        value = method.invoke(child);
      } catch (ReflectiveOperationException e) {
        continue;
      }
      if (!(value instanceof List<?> list) || list.isEmpty()) {
        continue;
      }
      List<String> names = new ArrayList<>();
      for (Object item : list) {
        if (item instanceof String name) {
          names.add(name);
        }
      }
      if (!names.isEmpty()) {
        out.put(method.getName().substring(3), sortedCopy(names));
      }
    }
    return out;
  }

  private static List<String> sortedCopy(List<String> raw) {
    List<String> sorted = new ArrayList<>(raw);
    Collections.sort(sorted);
    return sorted;
  }
}
