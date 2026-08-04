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
package io.github.yellowhammer.designerxml;

import org.glassfish.jaxb.runtime.marshaller.NamespacePrefixMapper;

import java.util.Map;
import java.util.Set;

/**
 * Префиксы пространств имён как в выгрузке конфигуратора 1С: у объекта метаданных на корне
 * {@code MetaDataObject}, у управляемой формы - на корне {@code Form}.
 *
 * <p>Пространство по умолчанию у объекта и у формы разное, а в контексте JAXB объявлены оба, поэтому
 * без префикса пишется то, которому принадлежит корень записываемого документа.
 */
final class OneCDesignerXmlNamespacePrefixMapper extends NamespacePrefixMapper {

  /** Пространства, которые 1С пишет без префикса: у своего документа каждое из них корневое. */
  private static final Set<String> ROOT_NAMESPACES = Set.of(
    "http://v8.1c.ru/8.3/MDClasses",
    "http://v8.1c.ru/8.3/xcf/logform");

  private static final Map<String, String> URI_TO_PREFIX = Map.ofEntries(
    Map.entry("http://v8.1c.ru/8.2/managed-application/core", "app"),
    Map.entry("http://v8.1c.ru/8.1/data/enterprise/current-config", "cfg"),
    Map.entry("http://v8.1c.ru/8.2/managed-application/cmi", "cmi"),
    Map.entry("http://v8.1c.ru/8.1/data-composition-system/core", "dcscor"),
    Map.entry("http://v8.1c.ru/8.1/data-composition-system/schema", "dcssch"),
    Map.entry("http://v8.1c.ru/8.1/data-composition-system/settings", "dcsset"),
    Map.entry("http://v8.1c.ru/8.1/data/enterprise", "ent"),
    Map.entry("http://v8.1c.ru/8.2/managed-application/logform", "lf"),
    Map.entry("http://v8.1c.ru/8.1/data/ui/style", "style"),
    Map.entry("http://v8.1c.ru/8.1/data/ui/fonts/system", "sys"),
    Map.entry("http://v8.1c.ru/8.1/data/core", "v8"),
    Map.entry("http://v8.1c.ru/8.1/data/ui", "v8ui"),
    Map.entry("http://v8.1c.ru/8.1/data/ui/colors/web", "web"),
    Map.entry("http://v8.1c.ru/8.1/data/ui/colors/windows", "win"),
    Map.entry("http://v8.1c.ru/8.3/xcf/enums", "xen"),
    Map.entry("http://v8.1c.ru/8.3/xcf/predef", "xpr"),
    Map.entry("http://v8.1c.ru/8.3/xcf/readable", "xr"),
    Map.entry("http://www.w3.org/2001/XMLSchema", "xs"),
    Map.entry("http://www.w3.org/2001/XMLSchema-instance", "xsi"),
    Map.entry("http://www.w3.org/XML/1998/namespace", "xml")
  );

  /** Пространство имён корня записываемого документа. */
  private final String rootNamespace;

  /**
   * @param rootNamespace пространство имён корневого элемента: оно пишется без префикса
   */
  OneCDesignerXmlNamespacePrefixMapper(String rootNamespace) {
    this.rootNamespace = rootNamespace == null ? "" : rootNamespace;
  }

  @Override
  public String getPreferredPrefix(
    String namespaceUri,
    String suggestion,
    boolean requirePrefix) {
    if (ROOT_NAMESPACES.contains(namespaceUri) && namespaceUri.equals(rootNamespace)) {
      return requirePrefix ? "m" : "";
    }
    String p = URI_TO_PREFIX.get(namespaceUri);
    if (p != null) {
      return p;
    }
    if (suggestion != null && !suggestion.isEmpty()) {
      return suggestion;
    }
    return "ns";
  }
}
