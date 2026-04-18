/*
 * This file is a part of md-sparrow.
 *
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package io.github.yellowhammer.designerxml.cf;

import java.util.List;

public enum MdObjectAddType {
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

  public static MdObjectAddType fromCliName(String s) {
    if (s == null || s.isBlank()) {
      throw new IllegalArgumentException("type required");
    }
    return MdObjectAddType.valueOf(s.trim().toUpperCase().replace('-', '_'));
  }
}
