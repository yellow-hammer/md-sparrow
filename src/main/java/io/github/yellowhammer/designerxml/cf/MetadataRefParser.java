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

import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Разбор и нормализация текстовых ссылок на объекты метаданных, встречающихся в Designer XML.
 *
 * <p>Поддержанные форматы:
 * <ul>
 *   <li>{@code MDObjectRef}-строки вида {@code Catalog.Имя}, {@code Document.Имя.TabularSection.Х.Attribute.Y},
 *     {@code Subsystem.Корневая.Дочерняя};</li>
 *   <li>{@code TypeDescription}-строки вида {@code cfg:CatalogRef.Имя}, {@code cfg:DocumentRef.Имя},
 *     {@code cfg:Characteristic.ПВХ}, {@code cfg:DefinedType.Имя}, {@code cfg:Constant.Имя}.</li>
 * </ul>
 */
public final class MetadataRefParser {

  /**
   * Суффиксы генерируемых типов платформы → корневой тип объекта метаданных.
   *
   * <p>Покрывает три класса ссылок, встречающихся в Designer XML:
   * <ul>
   *   <li>{@code *Ref} — ссылочные типы реквизитов (CatalogRef, DocumentRef, …);</li>
   *   <li>{@code *Object} — типы источника событий (EventSubscription/Properties/Source);</li>
   *   <li>{@code *RecordSet} — типы наборов записей (тоже в Source подписок на события).</li>
   * </ul>
   */
  private static final Map<String, String> REF_SUFFIX_TO_TYPE = Map.ofEntries(
    Map.entry("CatalogRef", "Catalog"),
    Map.entry("DocumentRef", "Document"),
    Map.entry("EnumRef", "Enum"),
    Map.entry("ExchangePlanRef", "ExchangePlan"),
    Map.entry("ChartOfCharacteristicTypesRef", "ChartOfCharacteristicTypes"),
    Map.entry("ChartOfAccountsRef", "ChartOfAccounts"),
    Map.entry("ChartOfCalculationTypesRef", "ChartOfCalculationTypes"),
    Map.entry("BusinessProcessRef", "BusinessProcess"),
    Map.entry("TaskRef", "Task"),
    Map.entry("DocumentJournalRef", "DocumentJournal"),
    Map.entry("CatalogObject", "Catalog"),
    Map.entry("DocumentObject", "Document"),
    Map.entry("TaskObject", "Task"),
    Map.entry("BusinessProcessObject", "BusinessProcess"),
    Map.entry("ExchangePlanObject", "ExchangePlan"),
    Map.entry("ChartOfCharacteristicTypesObject", "ChartOfCharacteristicTypes"),
    Map.entry("ChartOfAccountsObject", "ChartOfAccounts"),
    Map.entry("ChartOfCalculationTypesObject", "ChartOfCalculationTypes"),
    Map.entry("InformationRegisterRecordSet", "InformationRegister"),
    Map.entry("AccumulationRegisterRecordSet", "AccumulationRegister"),
    Map.entry("AccountingRegisterRecordSet", "AccountingRegister"),
    Map.entry("CalculationRegisterRecordSet", "CalculationRegister"));

  /** Псевдо-типы из {@code v8:TypeSet} / {@code Location}, разворачиваются в реальные типы метаданных. */
  private static final Map<String, String> ALIAS_TO_TYPE = Map.ofEntries(
    Map.entry("Characteristic", "ChartOfCharacteristicTypes"),
    Map.entry("DefinedType", "DefinedType"),
    Map.entry("Constant", "Constant"),
    Map.entry("CommonAttribute", "CommonAttribute"),
    Map.entry("SessionParameter", "SessionParameter"));

  /** Допустимые корневые типы метаданных в путях {@code MDObjectRef}. */
  /** Все типы корневых объектов метаданных, которые парсер умеет распознавать. */
  static final Set<String> KNOWN_ROOT_TYPES = Set.of(
    "Configuration",
    "Catalog",
    "Document",
    "DocumentJournal",
    "Enum",
    "Subsystem",
    "ExchangePlan",
    "FilterCriterion",
    "ChartOfCharacteristicTypes",
    "ChartOfAccounts",
    "ChartOfCalculationTypes",
    "InformationRegister",
    "AccumulationRegister",
    "AccountingRegister",
    "CalculationRegister",
    "BusinessProcess",
    "Task",
    "Constant",
    "CommonModule",
    "CommonAttribute",
    "CommonForm",
    "CommonCommand",
    "CommandGroup",
    "CommonTemplate",
    "CommonPicture",
    "SessionParameter",
    "Role",
    "Report",
    "DataProcessor",
    "FunctionalOption",
    "FunctionalOptionsParameter",
    "DefinedType",
    "Sequence",
    "EventSubscription",
    "ScheduledJob",
    "WebService",
    "HTTPService",
    "WSReference",
    "XDTOPackage",
    "Language",
    "DocumentNumerator",
    "ExternalDataSource",
    "Style",
    "StyleItem",
    "PaletteColor",
    "SettingsStorage",
    "Bot",
    "Interface");

  private MetadataRefParser() {
  }

  /**
   * Нормализует путь объекта вида {@code "Тип.Имя[.Часть.…]"} в стабильный ключ узла графа
   * ({@code "<objectType>.<topLevelName>"}).
   *
   * <p>Для подсистем берётся последний сегмент имени (короткое имя в каталоге выгрузки), для других типов —
   * первый сегмент после {@code <objectType>}. Это согласуется с тем, как {@link CfObjectPathResolver} ищет файлы.
   */
  public static Optional<String> normalizeMdObjectRef(String text) {
    if (text == null) {
      return Optional.empty();
    }
    String value = text.trim();
    if (value.isEmpty()) {
      return Optional.empty();
    }
    int firstDot = value.indexOf('.');
    if (firstDot <= 0) {
      return Optional.empty();
    }
    String head = value.substring(0, firstDot);
    String tail = value.substring(firstDot + 1);
    String objectType = ALIAS_TO_TYPE.getOrDefault(head, head);
    if (!KNOWN_ROOT_TYPES.contains(objectType)) {
      return Optional.empty();
    }
    String name;
    if ("Subsystem".equals(objectType)) {
      int lastDot = tail.lastIndexOf('.');
      name = lastDot >= 0 ? tail.substring(lastDot + 1) : tail;
    } else {
      int nextDot = tail.indexOf('.');
      name = nextDot >= 0 ? tail.substring(0, nextDot) : tail;
    }
    if (name.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(objectType + "." + name);
  }

  /**
   * Нормализует строку из {@code v8:Type} ({@code "cfg:CatalogRef.Имя"}, {@code "cfg:Characteristic.ПВХ"}).
   * Возвращает {@link Optional#empty()} для примитивных типов (Number/String/Date/Boolean/UUID и др.).
   */
  public static Optional<String> normalizeTypeRef(String text) {
    if (text == null) {
      return Optional.empty();
    }
    String value = text.trim();
    if (value.isEmpty()) {
      return Optional.empty();
    }
    int colon = value.indexOf(':');
    if (colon >= 0) {
      value = value.substring(colon + 1);
    }
    int dot = value.indexOf('.');
    if (dot <= 0) {
      return Optional.empty();
    }
    String head = value.substring(0, dot);
    String name = value.substring(dot + 1);
    String objectType = REF_SUFFIX_TO_TYPE.get(head);
    if (objectType == null) {
      objectType = ALIAS_TO_TYPE.get(head);
    }
    if (objectType == null) {
      return Optional.empty();
    }
    int nextDot = name.indexOf('.');
    if (nextDot >= 0) {
      name = name.substring(0, nextDot);
    }
    if (name.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(objectType + "." + name);
  }
}
