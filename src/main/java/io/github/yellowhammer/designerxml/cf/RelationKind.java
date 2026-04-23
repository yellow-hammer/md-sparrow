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

/**
 * Типизированные виды связей между объектами метаданных, выявляемые при построении графа.
 *
 * <p>Стабильные строковые id ({@link #wireName()}) — публичный контракт CLI {@code cf-md-graph}
 * и расширения VS Code; не переименовывать без согласованной миграции.
 */
public enum RelationKind {

  /** Владельцы справочника (Properties/Owners). */
  CATALOG_OWNERS("catalogOwners", "0..*"),

  /** Тип реквизита/измерения/ресурса, константы, сессионного параметра,
   *  ОбщегоАтрибута, ОпределяемогоТипа и ПВХ (Type/v8:Type). */
  TYPE_COMPOSITE("typeComposite", "0..*"),

  /** Регистры, по которым проводится документ (Document/Properties/RegisterRecords). */
  DOCUMENT_POSTING_REGISTERS("documentPostingRegisters", "0..*"),

  /** Документы-основания (Document/Properties/BasedOn, Catalog/Properties/BasedOn). */
  DOCUMENT_BASED_ON("documentBasedOn", "0..*"),

  /** Тип измерения регистра. */
  REGISTER_DIMENSION_TYPE("registerDimensionType", "0..*"),

  /** Тип ресурса регистра. */
  REGISTER_RESOURCE_TYPE("registerResourceType", "0..*"),

  /** Состав подсистемы (Subsystem/Properties/Content). */
  SUBSYSTEM_MEMBERSHIP("subsystemMembership", "0..*"),

  /** Вложенная подсистема (Subsystem/ChildObjects/Subsystem). */
  SUBSYSTEM_NESTING("subsystemNesting", "0..*"),

  /** Документы последовательности (Sequence/Properties/Documents). */
  SEQUENCE_DOCUMENTS("sequenceDocuments", "0..*"),

  /** Регистры последовательности (Sequence/Properties/RegisterRecords). */
  SEQUENCE_REGISTERS("sequenceRegisters", "0..*"),

  /** Тип отбора критерия (FilterCriterion/Properties/Type). */
  FILTER_CRITERION_TYPE("filterCriterionType", "0..*"),

  /** Состав критерия отбора (FilterCriterion/Properties/Content). */
  FILTER_CRITERION_CONTENT("filterCriterionContent", "0..*"),

  /** Документы журнала документов (DocumentJournal/Properties/RegisteredDocuments). */
  DOCUMENT_JOURNAL_ENTRIES("documentJournalEntries", "0..*"),

  /** Хранилище значения функциональной опции (FunctionalOption/Properties/Location). */
  FUNCTIONAL_OPTION_LOCATION("functionalOptionLocation", "1..1"),

  /** Объекты, на которые влияет функциональная опция (FunctionalOption/Properties/Content). */
  FUNCTIONAL_OPTION_AFFECTED("functionalOptionAffected", "0..*"),

  /** Привязка параметра функциональной опции (FunctionalOptionsParameter/Properties/Use). */
  FOP_USE_BINDING("fopUseBinding", "1..*"),

  /** Право роли на объект метаданных (Roles/&lt;роль&gt;/Ext/Rights.xml). */
  ROLE_OBJECT_RIGHTS("roleObjectRights", "0..*"),

  /** Состав плана обмена (ExchangePlan/ChildObjects/Content). */
  EXCHANGE_PLAN_CONTENT("exchangePlanContent", "0..*"),

  /** Использование общего реквизита у объекта (CommonAttribute/Properties/Content). */
  COMMON_ATTRIBUTE_USAGE("commonAttributeUsage", "0..*"),

  /** Типы объектов-источников события (EventSubscription/Properties/Source). */
  SUBSCRIPTION_SOURCE("subscriptionSource", "0..*"),

  /** Общий модуль-обработчик подписки на событие (EventSubscription/Properties/Handler). */
  SUBSCRIPTION_HANDLER("subscriptionHandler", "0..1"),

  /** Общий модуль-обработчик регламентного задания (ScheduledJob/Properties/MethodName). */
  SCHEDULED_JOB_HANDLER("scheduledJobHandler", "0..1"),

  /** Тип параметра общей команды (CommonCommand/Properties/CommandParameterType). */
  COMMAND_PARAMETER_TYPE("commandParameterType", "0..*"),

  /** План счетов регистра бухгалтерии (AccountingRegister/Properties/ChartOfAccounts). */
  REGISTER_CHART_OF_ACCOUNTS("registerChartOfAccounts", "1..1"),

  /** План видов расчётов регистра расчётов (CalculationRegister/Properties/ChartOfCalculationTypes). */
  REGISTER_CHART_OF_CALCULATION_TYPES("registerChartOfCalculationTypes", "1..1"),

  /** Виды субконто плана счетов (ChartOfAccounts/Properties/ExtDimensionTypes → ПВХ). */
  CHART_OF_ACCOUNTS_EXT_DIMENSIONS("chartOfAccountsExtDimensions", "1..1"),

  /** Справочник значений плана видов характеристик (ChartOfCharacteristicTypes/Properties/CharacteristicExtValues). */
  CHARACTERISTIC_EXT_VALUES("characteristicExtValues", "0..1");

  private final String wireName;
  private final String defaultCardinality;

  RelationKind(String wireName, String defaultCardinality) {
    this.wireName = wireName;
    this.defaultCardinality = defaultCardinality;
  }

  /** Стабильный строковый id для JSON и контракта расширения. */
  public String wireName() {
    return wireName;
  }

  /** Кардинальность по умолчанию ({@code "0..*"}, {@code "1..1"}, {@code "1..*"}). */
  public String defaultCardinality() {
    return defaultCardinality;
  }
}
