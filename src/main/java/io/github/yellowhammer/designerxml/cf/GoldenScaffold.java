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
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Scaffold нового объекта метаданных из канонического эталона «голого» объекта, забандленного в jar.
 *
 * <p>В jar лежит один набор эталонов - выгрузка платформы самого нового формата (ресурсы
 * {@code golden/cf/…} и {@code golden/cfe/…}, формат - в {@code golden/format.txt}; см. build.gradle.kts,
 * источник - submodule samples-1c-platform). Файл формата V получается проекцией канонического
 * ({@link FormatProjection}), новый объект - параметризацией результата (имя и детерминированные UUID,
 * {@link GoldenObjectTemplate}). Значения по умолчанию - из выгрузки платформы: в XSD их нет.
 *
 * <p>Внешние объекты ({@code golden-ext/<формат>/…}) пока берутся из эталона своего формата.
 */
public final class GoldenScaffold {

  /** Имя объекта-прототипа в эталоне (как в семени samples-1c-platform/seed). */
  private static final Map<MdObjectAddType, String> PROTO = Map.ofEntries(
    Map.entry(MdObjectAddType.CATALOG, "Справочник1"),
    Map.entry(MdObjectAddType.ENUM, "Перечисление1"),
    Map.entry(MdObjectAddType.CONSTANT, "Константа1"),
    Map.entry(MdObjectAddType.DOCUMENT, "Документ1"),
    Map.entry(MdObjectAddType.REPORT, "Отчет1"),
    Map.entry(MdObjectAddType.DATA_PROCESSOR, "Обработка1"),
    Map.entry(MdObjectAddType.TASK, "Задача1"),
    Map.entry(MdObjectAddType.CHART_OF_ACCOUNTS, "ПланСчетов1"),
    Map.entry(MdObjectAddType.CHART_OF_CHARACTERISTIC_TYPES, "ПланВидовХарактеристик1"),
    Map.entry(MdObjectAddType.CHART_OF_CALCULATION_TYPES, "ПланВидовРасчета1"),
    Map.entry(MdObjectAddType.COMMON_MODULE, "ОбщийМодуль1"),
    Map.entry(MdObjectAddType.SUBSYSTEM, "Подсистема1"),
    Map.entry(MdObjectAddType.SESSION_PARAMETER, "ПараметрСеанса1"),
    Map.entry(MdObjectAddType.EXCHANGE_PLAN, "ПланОбмена1"),
    Map.entry(MdObjectAddType.COMMON_ATTRIBUTE, "ОбщийРеквизит1"),
    Map.entry(MdObjectAddType.COMMON_PICTURE, "ОбщаяКартинка1"),
    Map.entry(MdObjectAddType.DOCUMENT_NUMERATOR, "Нумератор1"),
    Map.entry(MdObjectAddType.EXTERNAL_DATA_SOURCE, "ВнешнийИсточник1"),
    Map.entry(MdObjectAddType.ROLE, "Роль1"));

  /** Имя внешнего объекта-прототипа в эталоне (external-files/empty). */
  private static final Map<ExternalArtifactKind, String> EXTERNAL_PROTO = Map.ofEntries(
    Map.entry(ExternalArtifactKind.REPORT, "ВнешнийОтчет1"),
    Map.entry(ExternalArtifactKind.DATA_PROCESSOR, "ВнешняяОбработка1"));

  /** Имя конфигурации-прототипа в эталоне (семя samples-1c-platform/seed). */
  private static final String EMPTY_CONFIG_PROTO = "ЭталонСемя";

  /** Имя расширения в эталоне: подменяется на имя создаваемого расширения. */
  private static final String EMPTY_EXTENSION_PROTO = "ПустоеРасширение";

  /** Канонический набор: голые объекты конфигурации (cf-bare-objects). */
  private static final String CANONICAL_CF = "golden/cf/";

  /** Канонический набор: пустое расширение (cfe-empty). */
  private static final String CANONICAL_CFE = "golden/cfe/";

  /** Формат канонического набора. */
  private static final String CANONICAL_FORMAT = "golden/format.txt";

  private static volatile SchemaVersion canonicalVersion;

  /** InternalInfo внешнего объекта в «сыром» (транскодер) порядке: GeneratedType перед ContainedObject. */
  private static final Pattern INTERNAL_INFO_GENERATED_THEN_CONTAINED = Pattern.compile(
    "(?s)<InternalInfo>\\s*(<xr:GeneratedType\\b.*?</xr:GeneratedType>)\\s*"
      + "(<xr:ContainedObject>.*?</xr:ContainedObject>)\\s*</InternalInfo>");

  private GoldenScaffold() {
  }

  static String protoName(MdObjectAddType type) {
    String proto = PROTO.get(type);
    if (proto == null) {
      throw new IllegalArgumentException("нет прототипа для " + type);
    }
    return proto;
  }

  /**
   * Формат канонического набора эталонов в jar.
   *
   * @return формат, из которого проецируются остальные
   * @throws IllegalStateException если jar собран без эталонов
   */
  static SchemaVersion canonicalVersion() {
    SchemaVersion cached = canonicalVersion;
    if (cached == null) {
      String text;
      try {
        text = readResource(CANONICAL_FORMAT).trim();
      } catch (IOException e) {
        throw new IllegalStateException(e.getMessage(), e);
      }
      cached = SchemaVersion.byVersionAttribute(text).orElseThrow(
        () -> new IllegalStateException("неизвестный формат канонического набора эталонов: " + text));
      canonicalVersion = cached;
    }
    return cached;
  }

  /**
   * Можно ли создать объект вида {@code type} в формате {@code version}: канонический эталон вида
   * есть в jar, формат не новее канонического и вид в формате уже существует.
   */
  public static boolean hasGolden(MdObjectAddType type, SchemaVersion version) {
    return resourceUrl(CANONICAL_CF + objectRelative(type)) != null
      && version.compareTo(canonicalVersion()) <= 0
      && FormatProjection.hasObjectKind(type.configurationXmlTag(), version);
  }

  /** XML нового объекта типа {@code type} с именем {@code targetName} в формате {@code version}. */
  public static String generateObject(MdObjectAddType type, String targetName, SchemaVersion version)
    throws IOException {
    String golden = projected(CANONICAL_CF + objectRelative(type), version);
    String seed = "scaffold|" + version.name() + "|" + type + "|" + targetName;
    return GoldenObjectTemplate.parametrize(golden, protoName(type), targetName, seed);
  }

  /** Есть ли в jar эталон внешнего объекта вида {@code kind} в этой версии формата. */
  public static boolean hasExternalGolden(ExternalArtifactKind kind, SchemaVersion version) {
    return resourceUrl(externalResource(kind, version)) != null;
  }

  /**
   * XML отдельного внешнего объекта (отчёт/обработка) с именем {@code targetName} в формате
   * {@code version} — параметризация эталона external-files/empty. ClassId платформы сохраняется.
   * Эталоны не-2.20 версий получены транскодером (платформа их выгрузить не может), поэтому здесь
   * приводятся к стилю конфигуратора: канонический заголовок, табы, порядок InternalInfo
   * (ContainedObject перед GeneratedType). На уже чистом эталоне 2.20 нормализация идемпотентна.
   */
  public static String generateExternalArtifact(ExternalArtifactKind kind, String targetName, SchemaVersion version)
    throws IOException {
    String proto = externalProtoName(kind);
    String golden = readResource(externalResource(kind, version));
    String normalized = reorderInternalInfoContainedFirst(
      GoldenXmlPostProcessor.normalizeMetaDataObjectXml(golden, version));
    String seed = "scaffoldExt|" + version.name() + "|" + kind + "|" + targetName;
    return GoldenObjectTemplate.parametrize(normalized, proto, targetName, seed);
  }

  /** Внешние объекты: ContainedObject перед GeneratedType внутри InternalInfo (как в выгрузке конфигуратора). */
  private static String reorderInternalInfoContainedFirst(String xml) {
    Matcher m = INTERNAL_INFO_GENERATED_THEN_CONTAINED.matcher(xml);
    if (!m.find()) {
      return xml;
    }
    return xml.substring(0, m.start())
      + "<InternalInfo>\n\t\t\t" + m.group(2) + "\n\t\t\t" + m.group(1) + "\n\t\t</InternalInfo>"
      + xml.substring(m.end());
  }

  /** {@code Ext/Rights.xml} новой роли из эталона (пустые права нужной версии формата). */
  public static String generateRoleRights(String targetRoleName, SchemaVersion version) throws IOException {
    String proto = protoName(MdObjectAddType.ROLE);
    String golden = projected(CANONICAL_CF + "Roles/" + proto + "/Ext/Rights.xml", version);
    String seed = "scaffoldRights|" + version.name() + "|" + targetRoleName;
    return GoldenObjectTemplate.parametrize(golden, proto, targetRoleName, seed);
  }

  /**
   * Пустая конфигурация формата {@code version} - такая, какую создаёт платформа: эталон
   * Configuration.xml с обрезанным до Языка {@code ChildObjects} и умолчаниями новой конфигурации
   * ({@link ConfigurationFormatRules#asNewConfiguration}), параметризованный именем {@code targetName}
   * и ремапом UUID. Для init-empty-cf.
   */
  public static String generateEmptyConfiguration(String targetName, SchemaVersion version) throws IOException {
    String golden = projected(CANONICAL_CF + CfLayout.CONFIGURATION_XML, version);
    String empty = ConfigurationFormatRules.asNewConfiguration(stripChildObjectsToLanguage(golden), version);
    String seed = "scaffoldEmptyCf|" + version.name() + "|" + targetName;
    return GoldenObjectTemplate.parametrize(empty, EMPTY_CONFIG_PROTO, targetName, seed);
  }

  /**
   * {@code Configuration.xml} пустого расширения с именем {@code targetName} в формате
   * {@code version}: эталон выгрузки расширения, параметризованный именем и ремапом UUID.
   * Для init-empty-cfe.
   *
   * <p>Состав эталона такой, каким расширение создаёт платформа: одна роль по умолчанию,
   * без языка.
   */
  public static String generateEmptyExtension(String targetName, SchemaVersion version) throws IOException {
    String golden = projected(CANONICAL_CFE + CfLayout.CONFIGURATION_XML, version);
    String seed = "scaffoldEmptyCfe|" + version.name() + "|" + targetName;
    return GoldenObjectTemplate.parametrize(golden, EMPTY_EXTENSION_PROTO, targetName, seed);
  }

  /**
   * Имя роли по умолчанию, которую платформа заводит в новом расширении, - из состава эталона.
   *
   * <p>Имя зависит от локали платформы, снявшей эталон ({@code ОсновнаяРоль} против
   * {@code DefaultRole}), поэтому берётся из файла, а не задаётся здесь.
   */
  public static String extensionDefaultRoleName() throws IOException {
    String golden = readResource(CANONICAL_CFE + CfLayout.CONFIGURATION_XML);
    Matcher role = Pattern.compile("<Role>([^<]+)</Role>").matcher(golden);
    if (!role.find()) {
      throw new IOException("в эталоне расширения нет роли по умолчанию");
    }
    return role.group(1).trim();
  }

  /** {@code Roles/<роль по умолчанию>.xml} расширения формата {@code version} из эталона (ремап UUID). */
  public static String generateExtensionDefaultRole(String extensionName, SchemaVersion version) throws IOException {
    String roleName = extensionDefaultRoleName();
    String golden = projected(CANONICAL_CFE + "Roles/" + roleName + ".xml", version);
    String seed = "scaffoldCfeRole|" + version.name() + "|" + extensionName;
    return GoldenObjectTemplate.parametrize(golden, roleName, roleName, seed);
  }

  /** {@code Languages/Русский.xml} формата {@code version} из эталона (ремап UUID). Для init-empty-cf. */
  public static String generateRussianLanguage(SchemaVersion version) throws IOException {
    String golden = projected(CANONICAL_CF + "Languages/Русский.xml", version);
    String seed = "scaffoldLang|" + version.name() + "|Русский";
    return GoldenObjectTemplate.parametrize(golden, "Русский", "Русский", seed);
  }

  /**
   * Оставляет в {@code ChildObjects} только {@code <Language>…</Language>}, удаляя ссылки на объекты.
   * Строки и перевод строки - как в файле.
   */
  private static String stripChildObjectsToLanguage(String xml) {
    Matcher m = Pattern.compile("(?s)<ChildObjects>(.*?)</ChildObjects>").matcher(xml);
    if (!m.find()) {
      return xml;
    }
    String eol = XmlLines.newline(xml);
    String[] lines = m.group(1).split("\\r?\\n", -1);
    StringBuilder kept = new StringBuilder("<ChildObjects>").append(eol);
    for (String line : lines) {
      if (line.contains("<Language>")) {
        kept.append(line).append(eol);
      }
    }
    // последний кусок - отступ закрывающего тега
    kept.append(lines[lines.length - 1]).append("</ChildObjects>");
    return xml.substring(0, m.start()) + kept + xml.substring(m.end());
  }

  /**
   * Файл канонического набора в формате {@code version}.
   *
   * @throws IOException если формат новее канонического набора
   */
  private static String projected(String resource, SchemaVersion version) throws IOException {
    SchemaVersion canonical = canonicalVersion();
    if (version.compareTo(canonical) > 0) {
      throw new IOException(
        "Нет эталона формата " + version.metadataObjectVersionAttribute() + ": эталоны сняты в формате "
          + canonical.metadataObjectVersionAttribute()
          + ". Добавьте в samples-1c-platform выгрузку платформы нового формата.");
    }
    return FormatProjection.project(readResource(resource), version);
  }

  private static String objectRelative(MdObjectAddType type) {
    return type.cfSubdir() + "/" + protoName(type) + ".xml";
  }

  static String externalProtoName(ExternalArtifactKind kind) {
    String proto = EXTERNAL_PROTO.get(kind);
    if (proto == null) {
      throw new IllegalArgumentException("нет прототипа внешнего объекта для " + kind);
    }
    return proto;
  }

  private static String externalResource(ExternalArtifactKind kind, SchemaVersion version) {
    String proto = externalProtoName(kind);
    return "golden-ext/" + version.metadataObjectVersionAttribute() + "/" + proto + "/" + proto + ".xml";
  }

  private static URL resourceUrl(String resource) {
    return GoldenScaffold.class.getClassLoader().getResource(resource);
  }

  private static String readResource(String resource) throws IOException {
    try (InputStream in = GoldenScaffold.class.getClassLoader().getResourceAsStream(resource)) {
      if (in == null) {
        throw new IOException(
          "Нет эталона " + resource + " в jar: соберите md-sparrow с submodule samples-1c-platform.");
      }
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }
  }
}
