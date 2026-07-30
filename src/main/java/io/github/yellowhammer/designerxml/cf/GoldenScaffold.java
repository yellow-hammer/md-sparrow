/*
 * This file is a part of md-sparrow.
 *
 * SPDX-License-Identifier: LGPL-3.0-or-later
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
 * Scaffold нового объекта метаданных из эталона (golden) «голого» объекта нужной версии, забандленного в jar
 * (ресурсы {@code golden/<формат>/<подкаталог>/<прототип>.xml} — см. build.gradle.kts; источник —
 * submodule samples-1c-platform). Значения по умолчанию берутся из выгрузки конфигуратора этой версии
 * (в XSD их нет); новый объект — параметризация эталона (имя + детерминированные UUID) через
 * {@link GoldenObjectTemplate}. Работает для любой версии, у которой есть эталон.
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

  /** Есть ли в jar эталон для типа в этой версии формата. */
  public static boolean hasGolden(MdObjectAddType type, SchemaVersion version) {
    return resourceUrl(objectResource(type, version)) != null;
  }

  /** XML нового объекта типа {@code type} с именем {@code targetName} в формате {@code version}. */
  public static String generateObject(MdObjectAddType type, String targetName, SchemaVersion version)
    throws IOException {
    String golden = readResource(objectResource(type, version), version);
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
    String golden = readResource(externalResource(kind, version), version);
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
    String res = "golden/" + version.metadataObjectVersionAttribute() + "/Roles/" + proto + "/Ext/Rights.xml";
    String golden = readResource(res, version);
    String seed = "scaffoldRights|" + version.name() + "|" + targetRoleName;
    return GoldenObjectTemplate.parametrize(golden, proto, targetRoleName, seed);
  }

  /**
   * Пустая конфигурация формата {@code version}: эталон Configuration.xml с обрезанным до Языка
   * {@code ChildObjects}, параметризованный именем {@code targetName} и ремапом UUID. Для init-empty-cf.
   */
  public static String generateEmptyConfiguration(String targetName, SchemaVersion version) throws IOException {
    String golden = readResource("golden/" + version.metadataObjectVersionAttribute() + "/Configuration.xml", version);
    String stripped = stripChildObjectsToLanguage(golden);
    String seed = "scaffoldEmptyCf|" + version.name() + "|" + targetName;
    return GoldenObjectTemplate.parametrize(stripped, EMPTY_CONFIG_PROTO, targetName, seed);
  }

  /** Есть ли в jar эталон пустого расширения в этой версии формата. */
  public static boolean hasExtensionGolden(SchemaVersion version) {
    return resourceUrl(extensionResource(version, "Configuration.xml")) != null;
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
    String golden = readResource(extensionResource(version, "Configuration.xml"), version);
    String seed = "scaffoldEmptyCfe|" + version.name() + "|" + targetName;
    return GoldenObjectTemplate.parametrize(golden, EMPTY_EXTENSION_PROTO, targetName, seed);
  }

  /**
   * Имя роли по умолчанию, которую платформа заводит в новом расширении, - из состава эталона.
   *
   * <p>Имя зависит от локали платформы, снявшей эталон ({@code ОсновнаяРоль} против
   * {@code DefaultRole}), поэтому берётся из файла, а не задаётся здесь.
   */
  public static String extensionDefaultRoleName(SchemaVersion version) throws IOException {
    String golden = readResource(extensionResource(version, "Configuration.xml"), version);
    Matcher role = Pattern.compile("<Role>([^<]+)</Role>").matcher(golden);
    if (!role.find()) {
      throw new IOException(
        "в эталоне расширения формата " + version.metadataObjectVersionAttribute() + " нет роли по умолчанию");
    }
    return role.group(1).trim();
  }

  /** {@code Roles/<роль по умолчанию>.xml} расширения формата {@code version} из эталона (ремап UUID). */
  public static String generateExtensionDefaultRole(String extensionName, SchemaVersion version) throws IOException {
    String roleName = extensionDefaultRoleName(version);
    String golden = readResource(extensionResource(version, "Roles/" + roleName + ".xml"), version);
    String seed = "scaffoldCfeRole|" + version.name() + "|" + extensionName;
    return GoldenObjectTemplate.parametrize(golden, roleName, roleName, seed);
  }

  private static String extensionResource(SchemaVersion version, String relative) {
    return "golden-cfe/" + version.metadataObjectVersionAttribute() + "/" + relative;
  }

  /** {@code Languages/Русский.xml} формата {@code version} из эталона (ремап UUID). Для init-empty-cf. */
  public static String generateRussianLanguage(SchemaVersion version) throws IOException {
    String res = "golden/" + version.metadataObjectVersionAttribute() + "/Languages/Русский.xml";
    String golden = readResource(res, version);
    String seed = "scaffoldLang|" + version.name() + "|Русский";
    return GoldenObjectTemplate.parametrize(golden, "Русский", "Русский", seed);
  }

  /** Оставляет в {@code ChildObjects} только {@code <Language>…</Language>}, удаляя ссылки на объекты. */
  private static String stripChildObjectsToLanguage(String xml) {
    Matcher m = Pattern.compile("(?s)<ChildObjects>(.*?)</ChildObjects>").matcher(xml);
    if (!m.find()) {
      return xml;
    }
    StringBuilder kept = new StringBuilder("<ChildObjects>\n");
    for (String line : m.group(1).split("\n")) {
      if (line.contains("<Language>")) {
        kept.append(line).append('\n');
      }
    }
    kept.append("\t\t</ChildObjects>");
    return xml.substring(0, m.start()) + kept + xml.substring(m.end());
  }

  private static String objectResource(MdObjectAddType type, SchemaVersion version) {
    return "golden/" + version.metadataObjectVersionAttribute() + "/" + type.cfSubdir() + "/" + protoName(type) + ".xml";
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

  private static String readResource(String resource, SchemaVersion version) throws IOException {
    try (InputStream in = GoldenScaffold.class.getClassLoader().getResourceAsStream(resource)) {
      if (in == null) {
        throw new IOException(
          "Нет эталона формата " + version.metadataObjectVersionAttribute() + " (ресурс " + resource
            + "). Добавьте выгрузку этой версии в samples-1c-platform (cf-bare-objects).");
      }
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }
  }
}
