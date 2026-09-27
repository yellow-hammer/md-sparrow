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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Заимствование объекта конфигурации в расширение.
 *
 * <p>Платформа связывает заимствованный объект с оригиналом по имени, а все
 * идентификаторы у него свои: шапка и список порождаемых типов берутся из
 * оригинала, идентификаторы заменяются детерминированными новыми, свойства
 * сводятся к принадлежности, имени и комментарию. Пустой {@code ChildObjects}
 * пишется только у видов, в схеме которых он есть.
 */
public final class CfeBorrow {

  private static final Pattern ROOT_NODE = Pattern.compile("<([A-Za-z]+) uuid=\"[0-9a-fA-F-]+\">");
  private static final Pattern ANY_UUID = Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");

  /**
   * Виды, у которых в MDClasses есть элемент {@code ChildObjects}.
   * В форматах 2.10–2.21 набор один и тот же.
   */
  private static final Set<String> CHILD_OBJECT_TYPES = Set.of(
    "AccountingRegister",
    "AccumulationRegister",
    "BusinessProcess",
    "CalculationRegister",
    "Catalog",
    "ChartOfAccounts",
    "ChartOfCalculationTypes",
    "ChartOfCharacteristicTypes",
    "Configuration",
    "Cube",
    "DataProcessor",
    "DimensionTable",
    "Document",
    "DocumentJournal",
    "Enum",
    "ExchangePlan",
    "ExternalDataProcessor",
    "ExternalDataSource",
    "ExternalReport",
    "FilterCriterion",
    "HTTPService",
    "InformationRegister",
    "IntegrationService",
    "Operation",
    "Recalculation",
    "Report",
    "Sequence",
    "SettingsStorage",
    "Subsystem",
    "Table",
    "TabularSection",
    "Task",
    "URLTemplate",
    "WebService");

  private CfeBorrow() {
  }

  /**
   * Создаёт заимствованный объект в расширении и регистрирует его в составе.
   *
   * @param objectXml XML объекта основной конфигурации
   * @param extensionConfigurationXml Configuration.xml расширения
   * @return путь к созданному XML заимствованного объекта
   */
  public static Path borrowObject(Path objectXml, Path extensionConfigurationXml, SchemaVersion version)
    throws IOException, JAXBException {
    String original = Files.readString(objectXml, StandardCharsets.UTF_8);
    Matcher root = ROOT_NODE.matcher(original);
    if (!root.find()) {
      throw new IllegalArgumentException("Не найден корневой узел объекта в " + objectXml);
    }
    String containerLocal = root.group(1);
    String name = objectName(original);
    String subdir = objectXml.toAbsolutePath().normalize().getParent().getFileName().toString();

    Path extensionRoot = extensionConfigurationXml.toAbsolutePath().normalize().getParent();
    Path target = extensionRoot.resolve(subdir).resolve(name + ".xml");
    if (Files.exists(target)) {
      throw new IllegalArgumentException("Объект уже заимствован: " + name);
    }

    String adopted = buildAdoptedXml(original, containerLocal, name, version);
    MdObjectStructureRead.read(adopted.getBytes(StandardCharsets.UTF_8), version);

    String configuration = Files.readString(extensionConfigurationXml, StandardCharsets.UTF_8);
    String updated = insertChildEntry(configuration, containerLocal, name);
    io.github.yellowhammer.designerxml.DesignerXml.unmarshal(
      version, new java.io.ByteArrayInputStream(updated.getBytes(StandardCharsets.UTF_8)));

    Files.createDirectories(target.getParent());
    Files.writeString(target, adopted, StandardCharsets.UTF_8);
    Files.writeString(extensionConfigurationXml, updated, StandardCharsets.UTF_8);
    return target;
  }

  private static String objectName(String xml) {
    Matcher matcher = Pattern.compile("<Name>([^<]+)</Name>").matcher(xml);
    if (!matcher.find()) {
      throw new IllegalArgumentException("У объекта нет имени.");
    }
    return matcher.group(1).trim();
  }

  private static String buildAdoptedXml(
    String original, String containerLocal, String name, SchemaVersion version) {
    String eol = original.contains("\r\n") ? "\r\n" : "\n";
    int rootStart = original.indexOf("<" + containerLocal + " uuid=");
    String header = original.substring(0, rootStart);

    StringBuilder out = new StringBuilder(header);
    out.append('<').append(containerLocal).append(" uuid=\"")
      .append(seededUuid("borrow|" + containerLocal + '|' + name + "|root")).append("\">").append(eol);
    String internalInfo = internalInfoWithNewIds(original, containerLocal, name, eol);
    if (!internalInfo.isEmpty()) {
      out.append(internalInfo);
    }
    // до 2.14 включительно платформа пишет принадлежность после комментария, с 2.15 - первой
    String belonging = "\t\t\t<ObjectBelonging>Adopted</ObjectBelonging>" + eol;
    boolean belongingFirst = ConfigurationFormatRules.belongingFirst(version);
    out.append("\t\t<Properties>").append(eol);
    if (belongingFirst) {
      out.append(belonging);
    }
    out.append("\t\t\t<Name>").append(name).append("</Name>").append(eol);
    out.append("\t\t\t<Comment/>").append(eol);
    if (!belongingFirst) {
      out.append(belonging);
    }
    out.append("\t\t</Properties>").append(eol);
    if (CHILD_OBJECT_TYPES.contains(containerLocal)) {
      out.append("\t\t<ChildObjects/>").append(eol);
    }
    out.append("\t</").append(containerLocal).append('>').append(eol);
    out.append("</MetaDataObject>");
    return out.toString();
  }

  /** Порождаемые типы оригинала с новыми детерминированными идентификаторами. */
  private static String internalInfoWithNewIds(String original, String containerLocal, String name, String eol) {
    int start = original.indexOf("<InternalInfo>");
    int end = original.indexOf("</InternalInfo>");
    if (start < 0 || end < 0) {
      return "";
    }
    String block = "\t\t" + original.substring(start, end + "</InternalInfo>".length()) + eol;
    Matcher matcher = ANY_UUID.matcher(block);
    StringBuilder out = new StringBuilder();
    int index = 0;
    while (matcher.find()) {
      index += 1;
      matcher.appendReplacement(out, seededUuid("borrow|" + containerLocal + '|' + name + '|' + index));
    }
    matcher.appendTail(out);
    return out.toString();
  }

  /**
   * Запись в состав расширения там же, где её пишет платформа: в блоке своего вида в порядке видов
   * ({@link ConfigurationChildObjectAppender}), как у своего объекта расширения.
   */
  private static String insertChildEntry(String configurationXml, String containerLocal, String name) {
    String entry = "<" + containerLocal + ">" + name + "</" + containerLocal + ">";
    if (configurationXml.contains(entry)) {
      throw new IllegalArgumentException("Объект уже в составе расширения: " + name);
    }
    return ConfigurationChildObjectAppender.appended(configurationXml, containerLocal, name);
  }

  private static String seededUuid(String seed) {
    return UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8)).toString();
  }
}
