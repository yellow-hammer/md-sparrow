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

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public enum MdObjectAddType {
  CATALOG("Catalog", "Catalogs", "Справочник", List.of("Object", "Ref", "Selection", "List", "Manager"), false),
  ENUM("Enum", "Enums", "Перечисление", List.of("Ref", "Manager", "List"), false),
  CONSTANT("Constant", "Constants", "Константа", List.of("Manager", "ValueManager", "ValueKey"), false),
  DOCUMENT("Document", "Documents", "Документ", List.of("Object", "Ref", "Selection", "List", "Manager"), false),
  REPORT("Report", "Reports", "Отчет", List.of("Object", "Manager"), false),
  DATA_PROCESSOR("DataProcessor", "DataProcessors", "Обработка", List.of("Object", "Manager"), false),
  TASK("Task", "Tasks", "Задача", List.of("Object", "Ref", "Selection", "List", "Manager"), false),
  CHART_OF_ACCOUNTS(
    "ChartOfAccounts",
    "ChartsOfAccounts",
    "ПланСчетов",
    List.of("Object", "Ref", "Selection", "List", "Manager", "ExtDimensionTypes", "ExtDimensionTypesRow"),
    false),
  CHART_OF_CHARACTERISTIC_TYPES(
    "ChartOfCharacteristicTypes",
    "ChartsOfCharacteristicTypes",
    "ПланВидовХарактеристик",
    List.of("Object", "Ref", "Selection", "List", "Characteristic", "Manager"),
    false),
  CHART_OF_CALCULATION_TYPES(
    "ChartOfCalculationTypes",
    "ChartsOfCalculationTypes",
    "ПланВидовРасчета",
    List.of(
      "Object",
      "Ref",
      "Selection",
      "List",
      "Manager",
      "DisplacingCalculationTypes",
      "DisplacingCalculationTypesRow",
      "BaseCalculationTypes",
      "BaseCalculationTypesRow",
      "LeadingCalculationTypes",
      "LeadingCalculationTypesRow"),
    false),
  COMMON_MODULE("CommonModule", "CommonModules", "ОбщийМодуль", List.of(), false),
  SUBSYSTEM("Subsystem", "Subsystems", "Подсистема", List.of(), false),
  SESSION_PARAMETER("SessionParameter", "SessionParameters", "ПараметрСеанса", List.of(), false),
  EXCHANGE_PLAN("ExchangePlan", "ExchangePlans", "ПланОбмена", List.of("Object", "Ref", "Selection", "List", "Manager"), false),
  COMMON_ATTRIBUTE("CommonAttribute", "CommonAttributes", "ОбщийРеквизит", List.of(), false),
  COMMON_PICTURE("CommonPicture", "CommonPictures", "ОбщаяКартинка", List.of(), false),
  DOCUMENT_NUMERATOR("DocumentNumerator", "DocumentNumerators", "НумераторДокументов", List.of(), false),
  EXTERNAL_DATA_SOURCE("ExternalDataSource", "ExternalDataSources", "ВнешнийИсточникДанных", List.of("Manager", "TablesManager", "CubesManager"), false),
  ROLE("Role", "Roles", "Роль", List.of(), true);

  private final String configurationXmlTag;
  private final String cfSubdir;
  private final String namePrefix;
  private final List<String> generatedTypeCategories;
  private final boolean roleWithExtRights;

  MdObjectAddType(
    String configurationXmlTag,
    String cfSubdir,
    String namePrefix,
    List<String> generatedTypeCategories,
    boolean roleWithExtRights) {
    this.configurationXmlTag = configurationXmlTag;
    this.cfSubdir = cfSubdir;
    this.namePrefix = namePrefix;
    this.generatedTypeCategories = generatedTypeCategories;
    this.roleWithExtRights = roleWithExtRights;
  }

  public String configurationXmlTag() {
    return configurationXmlTag;
  }

  public String cfSubdir() {
    return cfSubdir;
  }

  public String namePrefix() {
    return namePrefix;
  }

  public List<String> generatedTypeCategories() {
    return generatedTypeCategories;
  }

  public boolean roleWithExtRights() {
    return roleWithExtRights;
  }

  /**
   * Вид по имени из CLI: {@code CATALOG}, {@code data-processor}, регистр не важен.
   *
   * @param s имя вида
   * @return вид объекта
   * @throws IllegalArgumentException если вид не указан или md-sparrow не умеет его создавать
   */
  public static MdObjectAddType fromCliName(String s) {
    if (s == null || s.isBlank()) {
      throw new IllegalArgumentException("не указан вид объекта (допустимы: " + cliNames() + ")");
    }
    String normalized = s.trim().toUpperCase(Locale.ROOT).replace('-', '_');
    for (MdObjectAddType type : values()) {
      if (type.name().equals(normalized)) {
        return type;
      }
    }
    throw new IllegalArgumentException(
      "неизвестный вид объекта: " + s.trim() + " (допустимы: " + cliNames() + ")");
  }

  /** Имена видов для CLI через запятую: список растёт вместе с перечислением. */
  private static String cliNames() {
    return Arrays.stream(values()).map(Enum::name).collect(Collectors.joining(", "));
  }
}
