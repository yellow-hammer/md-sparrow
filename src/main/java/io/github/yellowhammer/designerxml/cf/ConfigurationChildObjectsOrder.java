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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Порядок видов в {@code Configuration/ChildObjects}: блоками по виду, как пишет выгрузку платформа.
 * Внутри блока объекты идут в порядке добавления: новый встаёт в конец своего блока.
 *
 * <p>Набор видов у каждого формата свой и берётся из {@code ConfigurationChildObjects} его схемы.
 * Порядок блоков - порядок записи платформы, а он у новых видов расходится со схемой: схема
 * дописывает {@code Bot}, {@code WebSocketClient} и {@code PaletteColor} в конец, а платформа
 * ставит бота и цвет палитры за определяемыми типами, клиент WebSocket - за WS-ссылками.
 */
final class ConfigurationChildObjectsOrder {

  private static final String CONFIGURATION = "Configuration";

  /** Виды всех форматов в порядке записи платформы (выгрузка 8.5.1, формат 2.21). */
  private static final List<String> PLATFORM_ORDER = List.of(
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
    "WebSocketClient",
    "EventSubscription",
    "ScheduledJob",
    "SettingsStorage",
    "FunctionalOption",
    "FunctionalOptionsParameter",
    "DefinedType",
    "Bot",
    "PaletteColor",
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
    "IntegrationService");

  private ConfigurationChildObjectsOrder() {
  }

  /**
   * Виды состава формата в порядке записи платформы.
   *
   * <p>Вид, которого платформа ещё не выгружала, встаёт в конец: там новые виды дописывает схема.
   *
   * @param version версия формата
   * @return имена элементов {@code ChildObjects}: индекс в списке - место блока
   */
  static List<String> tagOrder(SchemaVersion version) {
    List<String> kinds = ChildObjectKinds.of(version, CONFIGURATION);
    List<String> order = new ArrayList<>();
    for (String kind : PLATFORM_ORDER) {
      if (kinds.contains(kind)) {
        order.add(kind);
      }
    }
    for (String kind : kinds) {
      if (!order.contains(kind)) {
        order.add(kind);
      }
    }
    return List.copyOf(order);
  }

  /**
   * Виды, блоки которых стоят <strong>строго до</strong> {@code xmlTag}: по ним ищется место
   * первого объекта вида, пока его блока в составе нет.
   *
   * <p>Формат здесь не нужен: вида, которого у формата нет, в его составе не бывает.
   */
  static Set<String> tagsStrictlyBefore(String xmlTag) {
    int idx = PLATFORM_ORDER.indexOf(xmlTag);
    if (idx < 0) {
      throw new IllegalArgumentException("unknown ChildObjects tag: " + xmlTag);
    }
    return Collections.unmodifiableSet(new HashSet<>(PLATFORM_ORDER.subList(0, idx)));
  }
}
