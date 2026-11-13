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

import jakarta.xml.bind.JAXBException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Создание и удаление макета схемы компоновки данных «как конфигуратор».
 *
 * <p>Эталон - выгрузка платформы (ресурсы {@code golden/dcs/…}, формат - в
 * {@code golden/dcs/format.txt}; см. build.gradle.kts, источник - snapshots/2.20/cf-dcs в submodule
 * samples-1c-platform). Описание макета - параметризация эталона ({@link GoldenObjectTemplate}: имя
 * целым токеном, детерминированные UUID), содержимое {@code Ext/Template.xml} - байты эталона как
 * есть: пустая схема версии формата не несёт, а платформа нормализует её при выгрузке одинаково.
 * Описание для форматов 2.12-2.19 получается проекцией канона ({@link FormatProjection}).
 */
public final class DcsTemplateScaffold {

  private static final String META_DATA_OBJECT = "MetaDataObject";
  private static final String CHILD_OBJECTS = "ChildObjects";
  private static final String PROPERTIES = "Properties";
  private static final String TEMPLATE = "Template";
  private static final String COMMON_TEMPLATE = "CommonTemplate";
  private static final String CONFIGURATION = "Configuration";
  private static final String TEMPLATES_DIRECTORY = "Templates";
  private static final String COMMON_TEMPLATES_DIRECTORY = "CommonTemplates";
  private static final String MAIN_SCHEMA = "MainDataCompositionSchema";
  private static final String DCS_TEMPLATE_TYPE = "DataCompositionSchema";

  /** Канонический набор эталона макета СКД в jar. */
  private static final String GOLDEN_DCS = "golden/dcs/";

  /** Формат, в котором снят эталон макета СКД (он один: остальные получаются проекцией). */
  private static final String GOLDEN_DCS_FORMAT = GOLDEN_DCS + "format.txt";

  /** Имя макета-прототипа отчёта в эталоне. */
  private static final String TEMPLATE_PROTO = "ОсновнаяСхемаКомпоновкиДанных";

  /** Имя общего макета-прототипа в эталоне. */
  private static final String COMMON_TEMPLATE_PROTO = "ОбщийМакет1";

  /** Описание макета-прототипа отчёта. */
  private static final String TEMPLATE_DESCRIPTOR =
    GOLDEN_DCS + "Reports/Отчет1/Templates/" + TEMPLATE_PROTO + ".xml";

  /** Содержимое пустой схемы компоновки, как его записала платформа (у отчёта и общего макета оно одно). */
  private static final String TEMPLATE_CONTENT =
    GOLDEN_DCS + "Reports/Отчет1/Templates/" + TEMPLATE_PROTO + "/Ext/Template.xml";

  /** Описание общего макета-прототипа. */
  private static final String COMMON_TEMPLATE_DESCRIPTOR =
    GOLDEN_DCS + COMMON_TEMPLATES_DIRECTORY + "/" + COMMON_TEMPLATE_PROTO + ".xml";

  /** Ссылка основной схемы на удаляемый макет: {@code <Вид>.<Владелец>.Template.<Имя>}. */
  private static String mainSchemaPointsAt(String name) {
    return "<" + MAIN_SCHEMA + ">[^<]*[.]" + TEMPLATE + "[.]" + Pattern.quote(name) + "</" + MAIN_SCHEMA + ">";
  }

  private static volatile SchemaVersion canonicalDcsVersion;

  private DcsTemplateScaffold() {
  }

  /**
   * Добавляет макет СКД: описание, содержимое из эталона и запись в составе. Владелец
   * {@code Configuration.xml} получает общий макет, прочие виды - свой макет в
   * {@code Templates/<Имя>}.
   *
   * @param ownerXml описание владельца (объект метаданных, внешний отчёт/обработка или конфигурация)
   * @param name имя нового макета
   * @param makeMain заполнить {@code MainDataCompositionSchema} владельца; только у видов,
   *   имеющих это свойство
   */
  public static void add(Path ownerXml, SchemaVersion version, String name, boolean makeMain)
    throws IOException, JAXBException {
    SupportRules.ensureEditable(ownerXml);
    CatalogNameConstraints.check(name);
    requireDcsGolden(version);
    String ownerText = Files.readString(ownerXml, StandardCharsets.UTF_8);
    String ownerKind = ownerKind(ownerText);
    if (CONFIGURATION.equals(ownerKind)) {
      if (makeMain) {
        throw new IllegalArgumentException("У вида " + CONFIGURATION + " нет свойства " + MAIN_SCHEMA + ".");
      }
      addCommonTemplate(ownerXml, ownerText, version, name);
      return;
    }
    ChildObjectKinds.ensureAllowed(version, ownerKind, TEMPLATE);
    if (makeMain && !AdoptedStatesPatch.propertyOrder(version, ownerKind).contains(MAIN_SCHEMA)) {
      throw new IllegalArgumentException("У вида " + ownerKind + " нет свойства " + MAIN_SCHEMA + ".");
    }
    Path templates = templatesDir(ownerXml);
    Path descriptor = templates.resolve(name + ".xml");
    if (Files.exists(descriptor) || Files.isDirectory(templates.resolve(name))) {
      throw new IllegalArgumentException("Макет уже существует: " + name);
    }
    if (templateEntry(ownerText, ownerKind, name) != null) {
      throw new IllegalArgumentException("Макет уже объявлен в составе: " + name);
    }

    // Зерно UUID как у форм: правка того же владельца тем же именем даёт те же идентификаторы
    String seed = GoldenUuid.from(
      "dcsTemplate|" + version.name() + "|" + ownerKind + "." + ownerName(ownerText, ownerKind) + "|" + name,
      ownerText);
    String descriptorXml = GoldenObjectTemplate.parametrize(
      FormatProjection.project(resourceText(TEMPLATE_DESCRIPTOR), version), TEMPLATE_PROTO, name, seed);

    String updated = insertTemplateEntry(ownerText, ownerKind, name, version);
    if (makeMain) {
      updated = withMainSchema(updated, ownerKind, ownerKind + "." + ownerName(ownerText, ownerKind)
        + "." + TEMPLATE + "." + name, version);
    }
    // Битый состав не пишем: обновлённый владелец обязан читаться моделью формата
    MdObjectStructureRead.read(updated.getBytes(StandardCharsets.UTF_8), version);

    Files.createDirectories(descriptor.getParent());
    Files.writeString(descriptor, descriptorXml, StandardCharsets.UTF_8);
    Path content = templates.resolve(name).resolve("Ext").resolve(TEMPLATE + ".xml");
    Files.createDirectories(content.getParent());
    Files.write(content, templateContentBytes());
    Files.writeString(ownerXml, updated, StandardCharsets.UTF_8);
  }

  /**
   * Удаляет макет СКД: описание, каталог содержимого и запись в составе. Если
   * {@code MainDataCompositionSchema} владельца указывал на макет, свойство пустеет.
   *
   * @param ownerXml описание владельца
   * @param name имя макета
   */
  public static void remove(Path ownerXml, SchemaVersion version, String name)
    throws IOException, JAXBException {
    SupportRules.ensureEditable(ownerXml);
    if (name == null || name.isBlank()) {
      throw new IllegalArgumentException("Введите имя макета.");
    }
    String ownerText = Files.readString(ownerXml, StandardCharsets.UTF_8);
    String ownerKind = ownerKind(ownerText);
    if (CONFIGURATION.equals(ownerKind)) {
      removeCommonTemplate(ownerXml, name);
      return;
    }
    Path templates = templatesDir(ownerXml);
    Path descriptor = templates.resolve(name + ".xml");
    if (!Files.isRegularFile(descriptor)) {
      throw new IllegalArgumentException("Макет не найден: " + name);
    }
    // У макета своё правило поддержки: разрешение на объект его не открывает
    SupportRules.ensureEditable(descriptor);
    requireDcsTemplateType(Files.readString(descriptor, StandardCharsets.UTF_8), name);
    XmlLines.Node entry = templateEntry(ownerText, ownerKind, name);
    if (entry == null) {
      throw new IllegalArgumentException("Макет не объявлен в составе: " + name);
    }
    int[] line = XmlLines.wholeLines(ownerText, entry.start(), entry.end(), TEMPLATE);
    String updated = ownerText.substring(0, line[0]) + ownerText.substring(line[1]);
    updated = collapseEmptyChildObjects(updated, ownerKind);
    // Основная схема, указывавшая на удаляемый макет, пустеет: владелец не ссылается на несуществующее
    updated = updated.replaceFirst(mainSchemaPointsAt(name), "<" + MAIN_SCHEMA + "/>");
    MdObjectStructureRead.read(updated.getBytes(StandardCharsets.UTF_8), version);

    Files.writeString(ownerXml, updated, StandardCharsets.UTF_8);
    Files.deleteIfExists(descriptor);
    deleteRecursively(templates.resolve(name));
  }

  /** Общий макет: описание и содержимое в {@code CommonTemplates/}, запись в составе конфигурации. */
  private static void addCommonTemplate(Path configurationXml, String configuration, SchemaVersion version, String name)
    throws IOException {
    Path cfRoot = configurationXml.toAbsolutePath().normalize().getParent();
    Path descriptor = cfRoot.resolve(COMMON_TEMPLATES_DIRECTORY).resolve(name + ".xml");
    if (Files.exists(descriptor) || Files.isDirectory(cfRoot.resolve(COMMON_TEMPLATES_DIRECTORY).resolve(name))) {
      throw new IllegalArgumentException(UiLabels.alreadyExists(COMMON_TEMPLATE, name));
    }
    // Состав готовится до записи: его отказ (имя занято) не оставляет файлов макета без ссылки
    String updated = ConfigurationChildObjectAppender.appended(configuration, COMMON_TEMPLATE, name);
    String seed = GoldenUuid.from("dcsCommonTemplate|" + version.name() + "|" + name, configuration);
    String descriptorXml = GoldenObjectTemplate.parametrize(
      FormatProjection.project(resourceText(COMMON_TEMPLATE_DESCRIPTOR), version), COMMON_TEMPLATE_PROTO, name, seed);

    Files.createDirectories(descriptor.getParent());
    Files.writeString(descriptor, descriptorXml, StandardCharsets.UTF_8);
    Path content = descriptor.getParent().resolve(name).resolve("Ext").resolve(TEMPLATE + ".xml");
    Files.createDirectories(content.getParent());
    Files.write(content, templateContentBytes());
    ConfigurationChildObjectAppender.replace(configurationXml, updated);
    // Состав изменился - служебный файл версий объектов не должен от него отставать
    ConfigDumpInfoSync.sync(cfRoot);
  }

  private static void removeCommonTemplate(Path configurationXml, String name) throws IOException {
    Path cfRoot = configurationXml.toAbsolutePath().normalize().getParent();
    Path descriptor = cfRoot.resolve(COMMON_TEMPLATES_DIRECTORY).resolve(name + ".xml");
    if (!Files.isRegularFile(descriptor)) {
      throw new IllegalArgumentException("Макет не найден: " + name);
    }
    SupportRules.ensureEditable(descriptor);
    requireDcsTemplateType(Files.readString(descriptor, StandardCharsets.UTF_8), name);
    ConfigurationChildObjectMutator.remove(configurationXml, COMMON_TEMPLATE, name);
    Files.deleteIfExists(descriptor);
    deleteRecursively(cfRoot.resolve(COMMON_TEMPLATES_DIRECTORY).resolve(name));
  }

  /**
   * Удалять можно только схему компоновки: макеты других видов (табличный документ, двоичные
   * данные) несут содержимое, которое операция не разбирает.
   */
  private static void requireDcsTemplateType(String descriptorXml, String name) {
    List<XmlLines.Node> roots = XmlLines.children(descriptorXml, List.of(META_DATA_OBJECT));
    String type = roots.isEmpty() ? null : XmlLines.children(descriptorXml,
      List.of(META_DATA_OBJECT, roots.get(0).name(), PROPERTIES)).stream()
      .filter(node -> "TemplateType".equals(node.name()))
      .map(node -> text(descriptorXml, node))
      .findFirst()
      .orElse(null);
    if (!DCS_TEMPLATE_TYPE.equals(type)) {
      throw new IllegalArgumentException(
        "Макет «" + name + "» — не схема компоновки данных: удалите его средствами платформы.");
    }
  }

  /** Байты содержимого пустой схемы из эталона: BOM, CRLF и отсутствие перевода строки в конце - как у платформы. */
  public static byte[] templateContentBytes() throws IOException {
    return resourceBytes(TEMPLATE_CONTENT);
  }

  /** Формат канонического эталона макета СКД в jar. */
  static SchemaVersion canonicalDcsVersion() throws IOException {
    SchemaVersion cached = canonicalDcsVersion;
    if (cached == null) {
      String text = resourceText(GOLDEN_DCS_FORMAT).trim();
      cached = SchemaVersion.byVersionAttribute(text).orElseThrow(
        () -> new IllegalStateException("неизвестный формат эталона cf-dcs: " + text));
      canonicalDcsVersion = cached;
    }
    return cached;
  }

  /**
   * Отказ форматам без эталона: для форматов новее канона эталона нет, а для 2.10-2.11 нет полного
   * набора XSD СКД, и проверить содержимое там нечем.
   */
  private static void requireDcsGolden(SchemaVersion version) throws IOException {
    SchemaVersion canonical = canonicalDcsVersion();
    if (version.compareTo(canonical) > 0) {
      throw new IllegalArgumentException(
        "Эталон cf-dcs не снят для формата " + version.metadataObjectVersionAttribute()
          + ": эталон снят в формате " + canonical.metadataObjectVersionAttribute() + ".");
    }
    if (version.compareTo(SchemaVersion.V2_12) < 0) {
      throw new IllegalArgumentException(
        "Создание макета СКД в формате " + version.metadataObjectVersionAttribute()
          + " не поддержано: для форматов 2.10-2.11 нет полного набора XSD СКД.");
    }
  }

  /** Вид владельца: элемент под корнем {@code MetaDataObject} ({@code Report}, {@code Configuration}). */
  private static String ownerKind(String ownerXml) {
    List<XmlLines.Node> roots = XmlLines.children(ownerXml, List.of(META_DATA_OBJECT));
    if (roots.isEmpty()) {
      throw new IllegalArgumentException("Файл не описывает объект метаданных.");
    }
    return roots.get(0).name();
  }

  /**
   * Имя владельца из {@code Properties/Name}: у внешнего отчёта оно может отличаться от имени
   * файла, а ссылка основной схемы строится по имени объекта.
   */
  private static String ownerName(String ownerXml, String ownerKind) {
    for (XmlLines.Node node : XmlLines.children(ownerXml, List.of(META_DATA_OBJECT, ownerKind))) {
      if (PROPERTIES.equals(node.name())) {
        String name = node.leaves().get("Name");
        if (name != null && !name.isBlank()) {
          return name;
        }
      }
    }
    throw new IllegalArgumentException("У владельца нет свойства Name.");
  }

  /** Запись макета в корневом ChildObjects владельца; {@code null}, если её нет. */
  private static XmlLines.Node templateEntry(String ownerXml, String ownerKind, String name) {
    for (XmlLines.Node node : XmlLines.children(ownerXml, List.of(META_DATA_OBJECT, ownerKind, CHILD_OBJECTS))) {
      if (TEMPLATE.equals(node.name()) && name.equals(text(ownerXml, node))) {
        return node;
      }
    }
    return null;
  }

  /** Текст элемента без разметки: между открывающим и закрывающим тегами. */
  private static String text(String xml, XmlLines.Node node) {
    int open = xml.indexOf('>', node.start());
    int close = xml.lastIndexOf('<', node.end() - 1);
    return close <= open ? "" : xml.substring(open + 1, close);
  }

  /**
   * Запись макета в корневой ChildObjects владельца - там, где её пишет платформа: перед первым
   * узлом, который в схеме формата идёт после макетов, иначе последней строкой состава
   * (см. {@link ChildObjectKinds}). Пустой состав {@code <ChildObjects/>} раскрывается.
   */
  private static String insertTemplateEntry(String xml, String ownerKind, String name, SchemaVersion version) {
    String eol = XmlLines.newline(xml);
    String entry = "<" + TEMPLATE + ">" + escape(name) + "</" + TEMPLATE + ">";
    XmlLines.Node childObjects = XmlLines.children(xml, List.of(META_DATA_OBJECT, ownerKind)).stream()
      .filter(node -> CHILD_OBJECTS.equals(node.name()))
      .findFirst()
      .orElseThrow(() -> new IllegalArgumentException("В объекте нет узла ChildObjects."));
    String indent = XmlLines.indentAt(xml, childObjects.start());
    if (xml.startsWith("/>", childObjects.end() - 2)) {
      return xml.substring(0, childObjects.start())
        + "<" + CHILD_OBJECTS + ">" + eol
        + indent + '\t' + entry + eol
        + indent + "</" + CHILD_OBJECTS + ">"
        + xml.substring(childObjects.end());
    }
    List<String> order = ChildObjectKinds.of(version, ownerKind);
    int templateRank = order.indexOf(TEMPLATE);
    for (XmlLines.Node child : XmlLines.children(xml, List.of(META_DATA_OBJECT, ownerKind, CHILD_OBJECTS))) {
      if (order.indexOf(child.name()) > templateRank) {
        return insertLineBefore(xml, child.start(), XmlLines.indentAt(xml, child.start()) + entry + eol);
      }
    }
    // Все узлы состава идут раньше макетов: запись - последней строкой состава
    int closing = xml.lastIndexOf("</", childObjects.end() - 1);
    return insertLineBefore(xml, closing, indent + '\t' + entry + eol);
  }

  /**
   * Заполняет {@code MainDataCompositionSchema} владельца: существующий элемент заменяется на
   * месте, отсутствующий встаёт по месту свойства в sequence формата (за последним из
   * предшественников - как {@link AdoptedStatesPatch#insertionPoint}).
   */
  private static String withMainSchema(String xml, String ownerKind, String reference, SchemaVersion version) {
    XmlLines.Node properties = XmlLines.children(xml, List.of(META_DATA_OBJECT, ownerKind)).stream()
      .filter(node -> PROPERTIES.equals(node.name()))
      .findFirst()
      .orElseThrow(() -> new IllegalArgumentException("В объекте нет узла Properties."));
    String element = "<" + MAIN_SCHEMA + ">" + escape(reference) + "</" + MAIN_SCHEMA + ">";
    List<XmlLines.Node> written = XmlLines.childrenAt(xml, properties.start());
    for (XmlLines.Node node : written) {
      if (MAIN_SCHEMA.equals(node.name())) {
        return xml.substring(0, node.start()) + element + xml.substring(node.end());
      }
    }
    String eol = XmlLines.newline(xml);
    List<String> order = AdoptedStatesPatch.propertyOrder(version, ownerKind);
    int place = order.indexOf(MAIN_SCHEMA);
    XmlLines.Node before = null;
    XmlLines.Node after = null;
    for (XmlLines.Node node : written) {
      int writtenPlace = order.indexOf(node.name());
      if (writtenPlace >= 0 && writtenPlace < place) {
        before = node;
      } else if (writtenPlace > place && after == null) {
        after = node;
      }
    }
    if (before != null) {
      int lineEnd = xml.indexOf('\n', before.end());
      int at = lineEnd < 0 ? before.end() : lineEnd + 1;
      return xml.substring(0, at) + XmlLines.indentAt(xml, before.start()) + element + eol + xml.substring(at);
    }
    if (after != null) {
      return insertLineBefore(xml, after.start(), XmlLines.indentAt(xml, after.start()) + element + eol);
    }
    int closing = xml.lastIndexOf("</", properties.end() - 1);
    return insertLineBefore(xml, closing, XmlLines.indentAt(xml, properties.start()) + '\t' + element + eol);
  }

  /** Опустевший состав сворачивается в {@code <ChildObjects/>}: так его пишет платформа. */
  private static String collapseEmptyChildObjects(String xml, String ownerKind) {
    XmlLines.Node childObjects = XmlLines.children(xml, List.of(META_DATA_OBJECT, ownerKind)).stream()
      .filter(node -> CHILD_OBJECTS.equals(node.name()))
      .findFirst()
      .orElse(null);
    if (childObjects == null || xml.startsWith("/>", childObjects.end() - 2)) {
      return xml;
    }
    String inner = text(xml, childObjects);
    if (!inner.isBlank()) {
      return xml;
    }
    return xml.substring(0, childObjects.start()) + "<" + CHILD_OBJECTS + "/>" + xml.substring(childObjects.end());
  }

  /** Вставляет строку {@code line} перед строкой, на которой стоит {@code offset}. */
  private static String insertLineBefore(String xml, int offset, String line) {
    int lineStart = xml.lastIndexOf('\n', offset - 1) + 1;
    return xml.substring(0, lineStart) + line + xml.substring(lineStart);
  }

  private static Path templatesDir(Path ownerXml) {
    Path normalized = ownerXml.toAbsolutePath().normalize();
    return normalized.getParent().resolve(stem(normalized)).resolve(TEMPLATES_DIRECTORY);
  }

  /** Имя файла описания без расширения: каталог макетов лежит рядом, в каталоге этого имени. */
  private static String stem(Path objectXml) {
    return objectXml.getFileName().toString().replaceFirst("[.][Xx][Mm][Ll]$", "");
  }

  private static void deleteRecursively(Path directory) throws IOException {
    if (!Files.isDirectory(directory)) {
      return;
    }
    try (var walk = Files.walk(directory)) {
      for (Path path : walk.sorted(Comparator.reverseOrder()).toList()) {
        Files.deleteIfExists(path);
      }
    }
  }

  private static String escape(String value) {
    return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
  }

  private static String resourceText(String resource) throws IOException {
    return new String(resourceBytes(resource), StandardCharsets.UTF_8);
  }

  private static byte[] resourceBytes(String resource) throws IOException {
    try (InputStream in = DcsTemplateScaffold.class.getClassLoader().getResourceAsStream(resource)) {
      if (in == null) {
        throw new IOException(
          "Нет эталона " + resource + " в jar: соберите md-sparrow с submodule samples-1c-platform.");
      }
      return in.readAllBytes();
    }
  }
}
