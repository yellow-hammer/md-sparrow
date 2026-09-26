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

import io.github.yellowhammer.designerxml.SchemaVersion;

import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Виды объектов верхнего уровня, которые создаёт {@code add-md-object}: и в выгрузке конфигуратора,
 * и в проекте 1С:EDT.
 *
 * <p>Вид - имя элемента в {@code Configuration/ChildObjects} и префикс имени нового объекта, как его
 * называет конфигуратор («Справочник1»). Каталог выгрузки берётся из общей таблицы
 * {@link CfObjectPathResolver#subdirsByType()}. Есть ли вид в формате, решает модель формата
 * ({@link FormatProjection#hasObjectKind}): вид, появившийся позже, в старом формате не создаётся.
 */
public enum MdObjectAddType {
  CATALOG("Catalog", "Справочник"),
  ENUM("Enum", "Перечисление"),
  CONSTANT("Constant", "Константа"),
  DOCUMENT("Document", "Документ"),
  REPORT("Report", "Отчет"),
  DATA_PROCESSOR("DataProcessor", "Обработка"),
  TASK("Task", "Задача"),
  CHART_OF_ACCOUNTS("ChartOfAccounts", "ПланСчетов"),
  CHART_OF_CHARACTERISTIC_TYPES("ChartOfCharacteristicTypes", "ПланВидовХарактеристик"),
  CHART_OF_CALCULATION_TYPES("ChartOfCalculationTypes", "ПланВидовРасчета"),
  COMMON_MODULE("CommonModule", "ОбщийМодуль"),
  SUBSYSTEM("Subsystem", "Подсистема"),
  SESSION_PARAMETER("SessionParameter", "ПараметрСеанса"),
  EXCHANGE_PLAN("ExchangePlan", "ПланОбмена"),
  COMMON_ATTRIBUTE("CommonAttribute", "ОбщийРеквизит"),
  COMMON_PICTURE("CommonPicture", "ОбщаяКартинка"),
  DOCUMENT_NUMERATOR("DocumentNumerator", "НумераторДокументов"),
  EXTERNAL_DATA_SOURCE("ExternalDataSource", "ВнешнийИсточникДанных"),
  ROLE("Role", "Роль"),
  STYLE_ITEM("StyleItem", "ЭлементСтиля"),
  STYLE("Style", "Стиль"),
  COMMON_TEMPLATE("CommonTemplate", "ОбщийМакет"),
  FILTER_CRITERION("FilterCriterion", "КритерийОтбора"),
  XDTO_PACKAGE("XDTOPackage", "ПакетXDTO"),
  WEB_SERVICE("WebService", "WebСервис"),
  HTTP_SERVICE("HTTPService", "HTTPСервис"),
  WS_REFERENCE("WSReference", "WSСсылка"),
  WEB_SOCKET_CLIENT("WebSocketClient", "WebSocketКлиент"),
  EVENT_SUBSCRIPTION("EventSubscription", "ПодпискаНаСобытие"),
  SCHEDULED_JOB("ScheduledJob", "РегламентноеЗадание"),
  SETTINGS_STORAGE("SettingsStorage", "ХранилищеНастроек"),
  FUNCTIONAL_OPTION("FunctionalOption", "ФункциональнаяОпция"),
  FUNCTIONAL_OPTIONS_PARAMETER("FunctionalOptionsParameter", "ПараметрФункциональныхОпций"),
  DEFINED_TYPE("DefinedType", "ОпределяемыйТип"),
  BOT("Bot", "Бот"),
  PALETTE_COLOR("PaletteColor", "ЦветПалитры"),
  COMMON_COMMAND("CommonCommand", "ОбщаяКоманда"),
  COMMAND_GROUP("CommandGroup", "ГруппаКоманд"),
  COMMON_FORM("CommonForm", "ОбщаяФорма"),
  SEQUENCE("Sequence", "Последовательность"),
  DOCUMENT_JOURNAL("DocumentJournal", "ЖурналДокументов"),
  INFORMATION_REGISTER("InformationRegister", "РегистрСведений"),
  ACCUMULATION_REGISTER("AccumulationRegister", "РегистрНакопления"),
  ACCOUNTING_REGISTER("AccountingRegister", "РегистрБухгалтерии"),
  CALCULATION_REGISTER("CalculationRegister", "РегистрРасчета"),
  BUSINESS_PROCESS("BusinessProcess", "БизнесПроцесс"),
  INTEGRATION_SERVICE("IntegrationService", "СервисИнтеграции");

  private final String configurationXmlTag;
  private final String namePrefix;

  MdObjectAddType(String configurationXmlTag, String namePrefix) {
    this.configurationXmlTag = configurationXmlTag;
    this.namePrefix = namePrefix;
  }

  /**
   * Имя элемента вида в {@code Configuration/ChildObjects} и корня {@code MetaDataObject}.
   *
   * @return например {@code Catalog}
   */
  public String configurationXmlTag() {
    return configurationXmlTag;
  }

  /**
   * Каталог объектов вида в выгрузке и в проекте EDT.
   *
   * @return например {@code Catalogs}
   */
  public String cfSubdir() {
    return CfObjectPathResolver.subdirsByType().get(configurationXmlTag);
  }

  /**
   * Префикс имени нового объекта: к нему добавляется номер, как в конфигураторе.
   *
   * @return например {@code Справочник}
   */
  public String namePrefix() {
    return namePrefix;
  }

  /**
   * Есть ли вид в формате.
   *
   * @param version формат
   * @return {@code true}, если модель формата знает вид
   */
  public boolean existsIn(SchemaVersion version) {
    return FormatProjection.hasObjectKind(configurationXmlTag, version);
  }

  /**
   * Отказ, если вида в формате ещё нет.
   *
   * @param version формат выгрузки или проекта
   * @throws IllegalArgumentException вид появился в более новом формате
   */
  public void requireIn(SchemaVersion version) {
    if (existsIn(version)) {
      return;
    }
    for (SchemaVersion newer : SchemaVersion.values()) {
      if (newer.compareTo(version) > 0 && existsIn(newer)) {
        throw new IllegalArgumentException(
          "вид " + configurationXmlTag + " появился в формате " + newer.metadataObjectVersionAttribute()
            + " (платформа " + newer.platformLine() + "), в формате " + version.metadataObjectVersionAttribute()
            + " его нет");
      }
    }
    throw new IllegalArgumentException(
      "вида " + configurationXmlTag + " нет в формате " + version.metadataObjectVersionAttribute());
  }

  /**
   * Вид по имени из CLI: {@code CATALOG}, {@code data-processor}, {@code XDTOPackage}; регистр и
   * разделители слов не важны.
   *
   * @param s имя вида
   * @return вид объекта
   * @throws IllegalArgumentException если вид не указан или md-sparrow не умеет его создавать
   */
  public static MdObjectAddType fromCliName(String s) {
    if (s == null || s.isBlank()) {
      throw new IllegalArgumentException("не указан вид объекта (допустимы: " + cliNames() + ")");
    }
    // Имя элемента состава без разделителей тоже годится: XDTOPackage - это XDTO_PACKAGE
    String normalized = withoutSeparators(s.trim().toUpperCase(Locale.ROOT));
    for (MdObjectAddType type : values()) {
      if (withoutSeparators(type.name()).equals(normalized)) {
        return type;
      }
    }
    throw new IllegalArgumentException(
      "неизвестный вид объекта: " + s.trim() + " (допустимы: " + cliNames() + ")");
  }

  private static String withoutSeparators(String name) {
    return name.replace("_", "").replace("-", "");
  }

  /** Имена видов для CLI через запятую: список растёт вместе с перечислением. */
  private static String cliNames() {
    return Arrays.stream(values()).map(Enum::name).collect(Collectors.joining(", "));
  }
}
