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

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Поддерживаемая версия набора XSD (подкаталог в корне репозитория {@code namespace-forest}, submodule).
 */
public enum SchemaVersion {

  /**
   * Схемы из каталога {@code schemas/2.20} (см. {@code gradle.properties} и {@code xjb/ns/2.20/}).
   */
  V2_20(
    "schemas/2.20",
    jaxbContextPath("io.github.yellowhammer.designerxml.jaxb.v2_20")),

  /**
   * Схемы из каталога {@code schemas/2.21} (см. {@code gradle.properties} и {@code xjb/ns/2.21/}).
   */
  V2_21(
    "schemas/2.21",
    jaxbContextPath("io.github.yellowhammer.designerxml.jaxb.v2_21"));

  private final String xsdDirectoryName;
  private final String jaxbContextPath;

  SchemaVersion(String xsdDirectoryName, String jaxbContextPath) {
    this.xsdDirectoryName = xsdDirectoryName;
    this.jaxbContextPath = jaxbContextPath;
  }

  /**
   * Подкаталог с {@code *.xsd} относительно корня {@code namespace-forest} (например {@code schemas/2.21}).
   *
   * @return относительный путь вида {@code schemas/2.21}
   */
  public String xsdDirectoryName() {
    return xsdDirectoryName;
  }

  /**
   * Значение атрибута {@code version} у корневого элемента {@code MetaDataObject} в XML метаданных.
   *
   * @return например {@code "2.20"} для {@link #V2_20}
   */
  public String metadataObjectVersionAttribute() {
    return switch (this) {
      case V2_20 -> "2.20";
      case V2_21 -> "2.21";
    };
  }

  /**
   * Строка контекста JAXB (пакеты через {@code :}).
   *
   * @return список пакетов для {@link JAXBContext#newInstance(String, ClassLoader)}
   */
  public String jaxbContextPath() {
    return jaxbContextPath;
  }

  private static final ConcurrentHashMap<SchemaVersion, JAXBContext> CONTEXT_CACHE = new ConcurrentHashMap<>();

  /**
   * Кэшированный {@link JAXBContext} для этой версии схем.
   *
   * @return готовый контекст (один экземпляр на enum-константу в рамках class loader)
   * @throws JAXBException если контекст создать нельзя
   */
  public JAXBContext jaxbContext() throws JAXBException {
    return CONTEXT_CACHE.computeIfAbsent(this, v -> {
      try {
        return JAXBContext.newInstance(v.jaxbContextPath(), Thread.currentThread().getContextClassLoader());
      } catch (JAXBException e) {
        throw new IllegalStateException("JAXBContext for " + v, e);
      }
    });
  }

  private static String jaxbContextPath(String base) {
    return String.join(":",
      base + ".mdclasses",
      base + ".v8_1_data_core",
      base + ".v8_1_data_enterprise",
      base + ".v8_1_data_ui",
      base + ".v8_2_managed_application_core",
      base + ".v8_2_managed_application_cmi",
      base + ".v8_2_managed_application_logform",
      base + ".v8_3_xcf_enums",
      base + ".v8_3_xcf_readable",
      base + ".v8_3_xcf_predef",
      base + ".v8_2_data_spreadsheet",
      base + ".v8_2_data_bsl",
      base + ".v8_2_managed_application_modules",
      base + ".v8_2_uobjects");
  }
}
