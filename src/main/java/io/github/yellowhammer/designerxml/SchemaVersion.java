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

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Поддерживаемая версия набора XSD (подкаталог в submodule {@code resources/namespace-forest}).
 */
public enum SchemaVersion {

  // Линейка платформы формата - по schemas/designer/processed-versions.json (namespace-forest):
  // каждой линейке x.y.z соответствует ровно один формат
  V2_10("8.3.17"), V2_11("8.3.18"), V2_12("8.3.19"), V2_13("8.3.20"), V2_14("8.3.21"), V2_15("8.3.22"),
  V2_16("8.3.23"), V2_17("8.3.24"), V2_18("8.3.25"), V2_19("8.3.26"), V2_20("8.3.27"), V2_21("8.5.1");

  private final String platformLine;

  SchemaVersion(String platformLine) {
    this.platformLine = platformLine;
  }

  /**
   * Линейка платформы, которая пишет выгрузку этого формата.
   *
   * @return например {@code "8.3.27"} для {@link #V2_20} и {@code "8.5.1"} для {@link #V2_21}
   */
  public String platformLine() {
    return platformLine;
  }

  /**
   * Формат, в котором пишет выгрузку платформа этой версии: формат самой новой линейки, не новее её.
   *
   * <p>Так выбирается формат проекта 1С:EDT по его {@code Runtime-Version}. Платформа новее последней
   * линейки получает самый новый известный формат, старше первой - самый старый: других моделей
   * формата в md-sparrow нет.
   *
   * @param platformVersion версия платформы: {@code 8.3.27}, {@code 8.3.27.1859}, {@code 8.5.1}
   * @return формат выгрузки
   * @throws IllegalArgumentException если версия не разбирается
   */
  public static SchemaVersion ofPlatform(String platformVersion) {
    int[] version = platformNumbers(platformVersion);
    SchemaVersion format = values()[0];
    for (SchemaVersion candidate : values()) {
      if (Arrays.compare(platformNumbers(candidate.platformLine), version) <= 0) {
        format = candidate;
      }
    }
    return format;
  }

  /** Первые три числа версии платформы: номер сборки на формат не влияет. */
  private static int[] platformNumbers(String platformVersion) {
    String[] parts = platformVersion == null ? new String[0] : platformVersion.trim().split("\\.");
    if (parts.length < 3) {
      throw new IllegalArgumentException("версия платформы не разбирается: " + platformVersion);
    }
    int[] numbers = new int[3];
    try {
      for (int i = 0; i < numbers.length; i++) {
        numbers[i] = Integer.parseInt(parts[i]);
      }
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException("версия платформы не разбирается: " + platformVersion, e);
    }
    return numbers;
  }

  /**
   * Подкаталог с {@code *.xsd} относительно корня {@code resources/namespace-forest} (например {@code schemas/designer/2.21}).
   *
   * @return относительный путь вида {@code schemas/designer/2.21}
   */
  public String xsdDirectoryName() {
    return "schemas/designer/" + metadataObjectVersionAttribute();
  }

  /**
   * Значение атрибута {@code version} у корневого элемента {@code MetaDataObject} в XML метаданных.
   *
   * @return например {@code "2.20"} для {@link #V2_20} (выводится из имени константы)
   */
  public String metadataObjectVersionAttribute() {
    return name().substring(1).replace('_', '.');
  }

  /**
   * Поддерживаемый {@link SchemaVersion} по значению атрибута {@code version} (или {@link java.util.Optional#empty()}).
   *
   * @param versionAttribute например {@code "2.17"}
   */
  public static java.util.Optional<SchemaVersion> byVersionAttribute(String versionAttribute) {
    if (versionAttribute == null) {
      return java.util.Optional.empty();
    }
    String v = versionAttribute.trim();
    for (SchemaVersion sv : values()) {
      if (sv.metadataObjectVersionAttribute().equals(v)) {
        return java.util.Optional.of(sv);
      }
    }
    return java.util.Optional.empty();
  }

  /**
   * Строка контекста JAXB (пакеты через {@code :}); пакет выводится из имени константы
   * (например {@code V2_17} → {@code …jaxb.v2_17.*}, см. сгенерированные XJC-модели в build.gradle.kts).
   *
   * @return список пакетов для {@link JAXBContext#newInstance(String, ClassLoader)}
   */
  public String jaxbContextPath() {
    return jaxbContextPath("io.github.yellowhammer.designerxml.jaxb." + name().toLowerCase());
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

  /** Сегменты пакетов моделей; в каталоге версии часть схем может отсутствовать, тогда пакета нет. */
  private static final List<String> MODEL_PACKAGES = List.of(
    "mdclasses",
    "v8_1_data_core",
    "v8_1_data_enterprise",
    "v8_1_data_ui",
    "v8_2_managed_application_core",
    "v8_2_managed_application_cmi",
    "v8_2_managed_application_logform",
    "v8_3_xcf_enums",
    "v8_3_xcf_readable",
    "v8_3_xcf_predef",
    "v8_2_data_spreadsheet",
    "v8_2_data_bsl",
    "v8_2_managed_application_modules",
    "v8_2_uobjects",
    "v8_3_xcf_logform",
    "v8_2_managed_application_dynamic_list_data",
    "v8_3_data_pdf",
    "v8_1_dcs_core",
    "v8_1_dcs_common",
    "v8_1_dcs_details",
    "v8_1_dcs_schema",
    "v8_1_dcs_settings",
    "v8_1_dcs_area_template");

  private static String jaxbContextPath(String base) {
    return MODEL_PACKAGES.stream()
      .map(pkg -> base + "." + pkg)
      .filter(SchemaVersion::generated)
      .collect(java.util.stream.Collectors.joining(":"));
  }

  /** Пакет модели собран для этой версии (у старых форматов нет схем формы). */
  private static boolean generated(String pkg) {
    try {
      Class.forName(pkg + ".ObjectFactory", false, Thread.currentThread().getContextClassLoader());
      return true;
    } catch (ClassNotFoundException e) {
      return false;
    }
  }
}
