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

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Значения {@code Configuration.xml}, которые зависят от формата, но из XSD не выводятся.
 *
 * <p>Состав файла формата V даёт {@link FormatProjection}; здесь - то, чем эталоны разных
 * форматов отличаются сверх состава. Правила сняты сравнением эталонов samples-1c-platform
 * 2.10-2.21 (cf-bare-objects и cfe-empty) и выгрузками ibcmd 8.3.23, 8.3.24, 8.3.27 и 8.5.1:
 * <ul>
 *   <li>{@code ConfigurationExtensionCompatibilityMode} - {@code Version} плюс линейка платформы
 *       формата ({@link SchemaVersion#platformLine()}). Из XSD не взять: перечисление режимов
 *       совместимости во всех схемах 2.10-2.21 заканчивается на {@code Version8_3_12};</li>
 *   <li>{@code UsedMobileApplicationFunctionalities} - список канонического эталона без
 *       функциональностей, которых формат ещё не пишет ({@link #FUNCTIONALITY_SINCE}). Перечисление
 *       XSD отстаёт от платформы: {@code SpeechToText} и {@code TextToSpeech} платформа пишет
 *       с 2.17 и 2.18, а ни в одной схеме их нет;</li>
 *   <li>{@code RequiredMobileApplicationPermissions} в 2.10 - непустой список разрешений,
 *       с 2.11 элемент пустой. Список из XSD не выводится (пять значений перечисления не
 *       пишутся, два переставлены), поэтому берётся из эталона 2.10 ресурсом сборки;</li>
 *   <li>расширение: до 2.14 включительно {@code ObjectBelonging} стоит после
 *       {@code ConfigurationExtensionPurpose} (эталоны 2.10-2.14 и сериализатор EDT), с 2.15 - первым;
 *       у заимствованного объекта так же - после {@code Comment} до 2.14 и первым с 2.15
 *       ({@link #belongingFirst});</li>
 *   <li>расширение 2.19: в {@code InternalInfo} два {@code xr:PropertyState}
 *       ({@code CommandInterface} и {@code MainSectionCommandInterface} = {@code Extended}),
 *       которых нет ни в 2.18, ни в 2.20.</li>
 * </ul>
 */
final class ConfigurationFormatRules {

  /** Ресурс сборки: блок разрешений из эталона 2.10 (см. build.gradle.kts). */
  private static final String PERMISSIONS_2_10 = "golden/rules/2.10/RequiredMobileApplicationPermissions.xml";

  /**
   * Функциональности мобильного приложения, которые появились позже самого списка (2.11):
   * с какого формата их пишет платформа. Сняты с эталонов cf-bare-objects 2.11-2.21: в каждом
   * формате список - это список 2.21 в том же порядке без более поздних функциональностей.
   * Функциональности вне таблицы пишутся с 2.11.
   */
  private static final Map<String, SchemaVersion> FUNCTIONALITY_SINCE = Map.ofEntries(
    Map.entry("BackgroundAudioRecording", SchemaVersion.V2_12),
    Map.entry("Videoconferences", SchemaVersion.V2_15),
    Map.entry("NFC", SchemaVersion.V2_16),
    Map.entry("DocumentScanning", SchemaVersion.V2_16),
    Map.entry("SpeechToText", SchemaVersion.V2_17),
    Map.entry("Geofences", SchemaVersion.V2_17),
    Map.entry("IncomingShareRequests", SchemaVersion.V2_17),
    Map.entry("AllIncomingShareRequestsTypesProcessing", SchemaVersion.V2_17),
    Map.entry("TextToSpeech", SchemaVersion.V2_18));

  /** Последний формат с непустым списком разрешений мобильного приложения. */
  private static final SchemaVersion LAST_LISTED_PERMISSIONS = SchemaVersion.V2_10;

  /**
   * Последний формат, где {@code ObjectBelonging} стоит не первым: у расширения - после назначения,
   * у заимствованного объекта - после {@code Comment}.
   */
  private static final SchemaVersion LAST_BELONGING_AFTER_PURPOSE = SchemaVersion.V2_14;

  /**
   * Формат, в эталоне расширения которого есть {@code xr:PropertyState} командного интерфейса.
   * Подтверждено двумя сборками 8.3.26 и загрузкой расширения ibcmd 8.3.25-8.3.27 (roundtrip.py).
   */
  private static final SchemaVersion EXTENDED_COMMAND_INTERFACE = SchemaVersion.V2_19;

  private static final List<String> PROPERTIES = List.of("MetaDataObject", "Configuration", "Properties");
  private static final List<String> INTERNAL_INFO = List.of("MetaDataObject", "Configuration", "InternalInfo");
  private static final List<String> FUNCTIONALITIES =
    List.of("MetaDataObject", "Configuration", "Properties", "UsedMobileApplicationFunctionalities");

  private ConfigurationFormatRules() {
  }

  /**
   * Значения формата {@code target} в {@code Configuration.xml}, уже приведённом к составу формата.
   *
   * @param xml {@code Configuration.xml} конфигурации или расширения
   * @param target формат
   * @return текст с правилами формата
   */
  static String apply(String xml, SchemaVersion target) {
    String result = ScaffoldPropertyEdit.setLeaf(
      xml, "ConfigurationExtensionCompatibilityMode", compatibilityMode(target));
    result = mobileFunctionalities(result, target);
    result = mobilePermissions(result, target);
    if (property(result, "ConfigurationExtensionPurpose").isPresent()) {
      result = extensionObjectBelonging(result, target);
      result = extensionCommandInterfaceStates(result, target);
    }
    return result;
  }

  /**
   * Режим совместимости, равный платформе формата, - как в {@code ConfigurationExtensionCompatibilityMode}.
   *
   * @param version формат
   * @return например {@code Version8_3_27} для 2.20
   */
  static String compatibilityMode(SchemaVersion version) {
    return "Version" + version.platformLine().replace('.', '_');
  }

  /**
   * Свойства, которые у новой конфигурации ставит сама платформа.
   *
   * <p>Эталон cf-bare-objects снят с семени, где режим совместимости {@code Version8_3_12}
   * и пустой {@code UsePurposes}. Пустая база платформы ({@code ibcmd infobase create} и выгрузка
   * на 8.3.23, 8.3.24, 8.3.27, 8.5.1) пишет режим совместимости своей версии и назначение
   * {@code PlatformApplication}. Режим 8.3.12 к тому же не даёт подключить к конфигурации
   * расширение, переопределяющее свойства заимствованных объектов.
   *
   * @param xml {@code Configuration.xml} формата {@code version}
   * @param version формат
   * @return текст с умолчаниями новой конфигурации
   */
  static String asNewConfiguration(String xml, SchemaVersion version) {
    String result = ScaffoldPropertyEdit.setLeaf(xml, "CompatibilityMode", compatibilityMode(version));
    Optional<XmlLines.Node> purposes = property(result, "UsePurposes");
    if (purposes.isEmpty() || !result.startsWith("<UsePurposes/>", purposes.get().start())) {
      return result;
    }
    XmlLines.Node node = purposes.get();
    String indent = XmlLines.indentAt(result, node.start());
    String eol = XmlLines.newline(result);
    String filled = "<UsePurposes>" + eol
      + indent + "\t<v8:Value xsi:type=\"app:ApplicationUsePurpose\">PlatformApplication</v8:Value>" + eol
      + indent + "</UsePurposes>";
    return result.substring(0, node.start()) + filled + result.substring(node.end());
  }

  private static String mobileFunctionalities(String xml, SchemaVersion target) {
    List<int[]> removals = new ArrayList<>();
    for (XmlLines.Node functionality : XmlLines.children(xml, FUNCTIONALITIES)) {
      String name = functionality.leaves().get("functionality");
      if (name == null) {
        throw new IllegalStateException("в UsedMobileApplicationFunctionalities элемент без имени функциональности");
      }
      SchemaVersion since = FUNCTIONALITY_SINCE.get(name);
      if (since != null && target.compareTo(since) < 0) {
        removals.add(XmlLines.wholeLines(xml, functionality.start(), functionality.end(), name));
      }
    }
    return XmlLines.removeAll(xml, removals);
  }

  private static String mobilePermissions(String xml, SchemaVersion target) {
    if (target.compareTo(LAST_LISTED_PERMISSIONS) > 0) {
      return xml;
    }
    Optional<XmlLines.Node> permissions = property(xml, "RequiredMobileApplicationPermissions");
    if (permissions.isEmpty() || !xml.startsWith("<RequiredMobileApplicationPermissions/>", permissions.get().start())) {
      return xml;
    }
    int start = permissions.get().start();
    int lineStart = start - XmlLines.indentAt(xml, start).length();
    String block = resource(PERMISSIONS_2_10).replace("\r\n", "\n").replace("\n", XmlLines.newline(xml));
    return xml.substring(0, lineStart) + block + xml.substring(permissions.get().end());
  }

  /**
   * Пишет ли платформа {@code ObjectBelonging} первым свойством.
   *
   * <p>С 2.15 - да. До 2.14 включительно у самого расширения он стоит после
   * {@code ConfigurationExtensionPurpose}, а у заимствованного объекта - после {@code Comment}: так
   * выгружают ibcmd 8.3.17-8.3.19 и 8.3.21 после загрузки (tools/golden-snapshots/roundtrip.py), 8.3.22
   * и новее пишут его первым. 8.3.20 (2.13) расширение с ролью не загружает вовсе, даже своё: для 2.13
   * место выведено из соседних 2.12 и 2.14.
   *
   * @param version формат
   * @return {@code true} с 2.15
   */
  static boolean belongingFirst(SchemaVersion version) {
    return version.compareTo(LAST_BELONGING_AFTER_PURPOSE) > 0;
  }

  private static String extensionObjectBelonging(String xml, SchemaVersion target) {
    if (belongingFirst(target)) {
      return xml;
    }
    Optional<XmlLines.Node> belonging = property(xml, "ObjectBelonging");
    Optional<XmlLines.Node> purpose = property(xml, "ConfigurationExtensionPurpose");
    if (belonging.isEmpty() || purpose.isEmpty() || belonging.get().start() > purpose.get().start()) {
      return xml;
    }
    int[] moved = XmlLines.wholeLines(xml, belonging.get().start(), belonging.get().end(), "ObjectBelonging");
    int[] anchor = XmlLines.wholeLines(
      xml, purpose.get().start(), purpose.get().end(), "ConfigurationExtensionPurpose");
    return xml.substring(0, moved[0])
      + xml.substring(moved[1], anchor[1])
      + xml.substring(moved[0], moved[1])
      + xml.substring(anchor[1]);
  }

  private static String extensionCommandInterfaceStates(String xml, SchemaVersion target) {
    if (target != EXTENDED_COMMAND_INTERFACE) {
      return xml;
    }
    List<XmlLines.Node> info = XmlLines.children(xml, INTERNAL_INFO);
    if (info.isEmpty() || info.stream().anyMatch(node -> "PropertyState".equals(node.name()))) {
      return xml;
    }
    XmlLines.Node last = info.get(info.size() - 1);
    int[] lines = XmlLines.wholeLines(xml, last.start(), last.end(), last.name());
    String indent = XmlLines.indentAt(xml, last.start());
    String eol = XmlLines.newline(xml);
    StringBuilder states = new StringBuilder();
    for (String property : List.of("CommandInterface", "MainSectionCommandInterface")) {
      states.append(indent).append("<xr:PropertyState>").append(eol)
        .append(indent).append("\t<xr:Property>").append(property).append("</xr:Property>").append(eol)
        .append(indent).append("\t<xr:State>Extended</xr:State>").append(eol)
        .append(indent).append("</xr:PropertyState>").append(eol);
    }
    return xml.substring(0, lines[1]) + states + xml.substring(lines[1]);
  }

  private static Optional<XmlLines.Node> property(String xml, String name) {
    return XmlLines.children(xml, PROPERTIES).stream()
      .filter(node -> name.equals(node.name()))
      .findFirst();
  }

  private static String resource(String name) {
    try (InputStream in = ConfigurationFormatRules.class.getClassLoader().getResourceAsStream(name)) {
      if (in == null) {
        throw new IllegalStateException("в jar нет ресурса эталона " + name);
      }
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
