/*
 * This file is a part of md-sparrow.
 *
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package io.github.yellowhammer.designerxml.cf;

import java.text.Collator;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Порядок дочерних ссылок в {@code Configuration/ChildObjects} по XSD {@code ConfigurationChildObjects}
 * (совпадает с выгрузкой конфигуратора: блоки по типу метаданных, внутри блока — по имени).
 */
final class ConfigurationChildObjectsOrder {

  private ConfigurationChildObjectsOrder() {
  }

  /**
   * Порядок имён элементов в {@code ConfigurationChildObjects} (как в JAXB {@code propOrder} / XSD sequence).
   */
  private static final List<String> TAG_ORDER = List.of(
    "Language",
    "Subsystem",
    "StyleItem",
    "Style",
    "CommonPicture",
    "Interface",
    "SessionParameter",
    "Role",
    "CommonTemplate",
    "FilterCriterion",
    "CommonModule",
    "CommonAttribute",
    "ExchangePlan",
    "XDTOPackage",
    "WebService",
    "HTTPService",
    "WSReference",
    "EventSubscription",
    "ScheduledJob",
    "SettingsStorage",
    "FunctionalOption",
    "FunctionalOptionsParameter",
    "DefinedType",
    "CommonCommand",
    "CommandGroup",
    "Constant",
    "CommonForm",
    "Catalog",
    "Document",
    "DocumentNumerator",
    "Sequence",
    "DocumentJournal",
    "Enum",
    "Report",
    "DataProcessor",
    "InformationRegister",
    "AccumulationRegister",
    "ChartOfCharacteristicTypes",
    "ChartOfAccounts",
    "AccountingRegister",
    "ChartOfCalculationTypes",
    "CalculationRegister",
    "BusinessProcess",
    "Task",
    "ExternalDataSource",
    "IntegrationService",
    "Bot",
    "WebSocketClient");

  /**
   * Имена элементов, стоящих в sequence <strong>строго до</strong> {@code xmlTag} (для первой вставки объекта
   * этого типа, когда ещё нет ни одной строки {@code <xmlTag>…</xmlTag>}).
   */
  static Set<String> tagsStrictlyBefore(String xmlTag) {
    int idx = TAG_ORDER.indexOf(xmlTag);
    if (idx < 0) {
      throw new IllegalArgumentException("unknown ChildObjects tag: " + xmlTag);
    }
    if (idx == 0) {
      return Set.of();
    }
    return Collections.unmodifiableSet(new HashSet<>(TAG_ORDER.subList(0, idx)));
  }

  /**
   * Локальные имена элементов, которые в схеме идут в {@code sequence} <strong>до</strong> {@code Catalog}.
   */
  static final Set<String> TAGS_BEFORE_CATALOG = tagsStrictlyBefore("Catalog");

  private static final Collator NAME_ORDER = Collator.getInstance(Locale.forLanguageTag("ru"));

  static {
    NAME_ORDER.setStrength(Collator.TERTIARY);
  }

  /**
   * Сравнение имён объектов в духе списка в типовой выгрузке (русская локаль).
   */
  static int compareObjectNames(String a, String b) {
    return NAME_ORDER.compare(a, b);
  }
}
