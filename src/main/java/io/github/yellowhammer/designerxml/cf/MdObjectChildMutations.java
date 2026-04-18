/*
 * This file is a part of md-sparrow.
 *
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package io.github.yellowhammer.designerxml.cf;

import io.github.yellowhammer.designerxml.SchemaVersion;

import jakarta.xml.bind.JAXBException;

import javax.xml.stream.XMLStreamException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * CRUD мутации узлов ChildObjects (реквизиты, ТЧ, реквизиты ТЧ) с гранулярной записью.
 */
public final class MdObjectChildMutations {

  private static final String CLOSE_CHILD_OBJECTS = "</ChildObjects>";
  private static final Pattern NAME_TAG = Pattern.compile("<Name>([\\s\\S]*?)</Name>");

  private MdObjectChildMutations() {
  }

  /**
   * Добавляет реквизит в корневой {@code ChildObjects} объекта.
   *
   * @param objectXml путь к XML объекта метаданных
   * @param version версия схемы Designer XML
   * @param newName имя нового реквизита
   * @throws IOException если не удалось прочитать/записать XML
   * @throws JAXBException если итоговый XML невалиден для JAXB-модели
   */
  public static void addAttribute(Path objectXml, SchemaVersion version, String newName)
    throws IOException, JAXBException {
    mutateAndWrite(objectXml, version, (xml, containerLocal) -> {
      ensureNotBlank(newName, "Введите имя реквизита.");
      ensureMissingNamedChild(xml, containerLocal, "Attribute", newName, "Реквизит уже существует: " + newName);
      return insertIntoRootChildObjects(
        xml,
        containerLocal,
        buildAttributeSnippet(newName, newName, "")
      );
    });
  }

  /**
   * Переименовывает реквизит в корневом {@code ChildObjects}.
   *
   * @param objectXml путь к XML объекта метаданных
   * @param version версия схемы Designer XML
   * @param oldName текущее имя реквизита
   * @param newName новое имя реквизита
   * @throws IOException если не удалось прочитать/записать XML
   * @throws JAXBException если итоговый XML невалиден для JAXB-модели
   */
  public static void renameAttribute(Path objectXml, SchemaVersion version, String oldName, String newName)
    throws IOException, JAXBException {
    mutateAndWrite(objectXml, version, (xml, containerLocal) -> renameNamedChild(
      xml,
      containerLocal,
      "Attribute",
      oldName,
      newName,
      "Реквизит"
    ));
  }

  /**
   * Удаляет реквизит из корневого {@code ChildObjects}.
   *
   * @param objectXml путь к XML объекта метаданных
   * @param version версия схемы Designer XML
   * @param name имя удаляемого реквизита
   * @throws IOException если не удалось прочитать/записать XML
   * @throws JAXBException если итоговый XML невалиден для JAXB-модели
   */
  public static void deleteAttribute(Path objectXml, SchemaVersion version, String name)
    throws IOException, JAXBException {
    mutateAndWrite(objectXml, version, (xml, containerLocal) -> deleteNamedChild(
      xml,
      containerLocal,
      "Attribute",
      name,
      "Реквизит"
    ));
  }

  /**
   * Создаёт копию реквизита в корневом {@code ChildObjects}.
   *
   * @param objectXml путь к XML объекта метаданных
   * @param version версия схемы Designer XML
   * @param sourceName имя исходного реквизита
   * @param newName имя копии
   * @throws IOException если не удалось прочитать/записать XML
   * @throws JAXBException если итоговый XML невалиден для JAXB-модели
   */
  public static void duplicateAttribute(Path objectXml, SchemaVersion version, String sourceName, String newName)
    throws IOException, JAXBException {
    mutateAndWrite(objectXml, version, (xml, containerLocal) -> duplicateNamedChild(
      xml,
      containerLocal,
      "Attribute",
      sourceName,
      newName,
      "Реквизит"
    ));
  }

  /**
   * Добавляет табличную часть в корневой {@code ChildObjects}.
   *
   * @param objectXml путь к XML объекта метаданных
   * @param version версия схемы Designer XML
   * @param newName имя новой табличной части
   * @throws IOException если не удалось прочитать/записать XML
   * @throws JAXBException если итоговый XML невалиден для JAXB-модели
   */
  public static void addTabularSection(Path objectXml, SchemaVersion version, String newName)
    throws IOException, JAXBException {
    mutateAndWrite(objectXml, version, (xml, containerLocal) -> {
      ensureNotBlank(newName, "Введите имя табличной части.");
      ensureMissingNamedChild(xml, containerLocal, "TabularSection", newName, "Табличная часть уже существует: " + newName);
      return insertIntoRootChildObjects(
        xml,
        containerLocal,
        buildTabularSectionSnippet(newName, newName, "")
      );
    });
  }

  /**
   * Переименовывает табличную часть в корневом {@code ChildObjects}.
   *
   * @param objectXml путь к XML объекта метаданных
   * @param version версия схемы Designer XML
   * @param oldName текущее имя табличной части
   * @param newName новое имя табличной части
   * @throws IOException если не удалось прочитать/записать XML
   * @throws JAXBException если итоговый XML невалиден для JAXB-модели
   */
  public static void renameTabularSection(Path objectXml, SchemaVersion version, String oldName, String newName)
    throws IOException, JAXBException {
    mutateAndWrite(objectXml, version, (xml, containerLocal) -> renameNamedChild(
      xml,
      containerLocal,
      "TabularSection",
      oldName,
      newName,
      "Табличная часть"
    ));
  }

  /**
   * Удаляет табличную часть из корневого {@code ChildObjects}.
   *
   * @param objectXml путь к XML объекта метаданных
   * @param version версия схемы Designer XML
   * @param name имя удаляемой табличной части
   * @throws IOException если не удалось прочитать/записать XML
   * @throws JAXBException если итоговый XML невалиден для JAXB-модели
   */
  public static void deleteTabularSection(Path objectXml, SchemaVersion version, String name)
    throws IOException, JAXBException {
    mutateAndWrite(objectXml, version, (xml, containerLocal) -> deleteNamedChild(
      xml,
      containerLocal,
      "TabularSection",
      name,
      "Табличная часть"
    ));
  }

  /**
   * Создаёт копию табличной части в корневом {@code ChildObjects}.
   *
   * @param objectXml путь к XML объекта метаданных
   * @param version версия схемы Designer XML
   * @param sourceName имя исходной табличной части
   * @param newName имя копии
   * @throws IOException если не удалось прочитать/записать XML
   * @throws JAXBException если итоговый XML невалиден для JAXB-модели
   */
  public static void duplicateTabularSection(Path objectXml, SchemaVersion version, String sourceName, String newName)
    throws IOException, JAXBException {
    mutateAndWrite(objectXml, version, (xml, containerLocal) -> duplicateNamedChild(
      xml,
      containerLocal,
      "TabularSection",
      sourceName,
      newName,
      "Табличная часть"
    ));
  }

  /**
   * Добавляет реквизит в табличную часть.
   *
   * @param objectXml путь к XML объекта метаданных
   * @param version версия схемы Designer XML
   * @param tabularSectionName имя табличной части
   * @param newName имя нового реквизита табличной части
   * @throws IOException если не удалось прочитать/записать XML
   * @throws JAXBException если итоговый XML невалиден для JAXB-модели
   */
  public static void addTabularAttribute(Path objectXml, SchemaVersion version, String tabularSectionName, String newName)
    throws IOException, JAXBException {
    mutateAndWrite(objectXml, version, (xml, containerLocal) -> {
      ensureNotBlank(newName, "Введите имя реквизита табличной части.");
      MdObjectXmlRegions.Region tsRegion = MdObjectXmlRegions.findNamedChildObjectRegion(
        xml,
        containerLocal,
        "TabularSection",
        tabularSectionName
      );
      if (!tsRegion.isValid()) {
        throw new IllegalArgumentException("Табличная часть не найдена: " + tabularSectionName);
      }
      MdObjectXmlRegions.Region existing = MdObjectXmlRegions.findNamedNestedChildObjectRegion(
        xml,
        containerLocal,
        "TabularSection",
        tabularSectionName,
        "Attribute",
        newName
      );
      if (existing.isValid()) {
        throw new IllegalArgumentException("Реквизит ТЧ уже существует: " + newName);
      }
      String tsXml = xml.substring(tsRegion.start(), tsRegion.end());
      String updatedTsXml = insertIntoTabularSectionChildObjects(tsXml, buildAttributeSnippet(newName, newName, ""));
      return xml.substring(0, tsRegion.start()) + updatedTsXml + xml.substring(tsRegion.end());
    });
  }

  /**
   * Переименовывает реквизит табличной части.
   *
   * @param objectXml путь к XML объекта метаданных
   * @param version версия схемы Designer XML
   * @param tabularSectionName имя табличной части
   * @param oldName текущее имя реквизита
   * @param newName новое имя реквизита
   * @throws IOException если не удалось прочитать/записать XML
   * @throws JAXBException если итоговый XML невалиден для JAXB-модели
   */
  public static void renameTabularAttribute(
    Path objectXml,
    SchemaVersion version,
    String tabularSectionName,
    String oldName,
    String newName
  ) throws IOException, JAXBException {
    mutateAndWrite(objectXml, version, (xml, containerLocal) -> {
      MdObjectXmlRegions.Region target = MdObjectXmlRegions.findNamedNestedChildObjectRegion(
        xml,
        containerLocal,
        "TabularSection",
        tabularSectionName,
        "Attribute",
        oldName
      );
      if (!target.isValid()) {
        throw new IllegalArgumentException("Реквизит ТЧ не найден: " + oldName);
      }
      MdObjectXmlRegions.Region duplicate = MdObjectXmlRegions.findNamedNestedChildObjectRegion(
        xml,
        containerLocal,
        "TabularSection",
        tabularSectionName,
        "Attribute",
        newName
      );
      if (duplicate.isValid() && !oldName.equals(newName)) {
        throw new IllegalArgumentException("Реквизит ТЧ уже существует: " + newName);
      }
      String nodeXml = xml.substring(target.start(), target.end());
      String replaced = replaceName(nodeXml, oldName, newName);
      return xml.substring(0, target.start()) + replaced + xml.substring(target.end());
    });
  }

  /**
   * Удаляет реквизит из табличной части.
   *
   * @param objectXml путь к XML объекта метаданных
   * @param version версия схемы Designer XML
   * @param tabularSectionName имя табличной части
   * @param name имя удаляемого реквизита
   * @throws IOException если не удалось прочитать/записать XML
   * @throws JAXBException если итоговый XML невалиден для JAXB-модели
   */
  public static void deleteTabularAttribute(Path objectXml, SchemaVersion version, String tabularSectionName, String name)
    throws IOException, JAXBException {
    mutateAndWrite(objectXml, version, (xml, containerLocal) -> {
      MdObjectXmlRegions.Region target = MdObjectXmlRegions.findNamedNestedChildObjectRegion(
        xml,
        containerLocal,
        "TabularSection",
        tabularSectionName,
        "Attribute",
        name
      );
      if (!target.isValid()) {
        throw new IllegalArgumentException("Реквизит ТЧ не найден: " + name);
      }
      return removeRegion(xml, target);
    });
  }

  /**
   * Создаёт копию реквизита табличной части.
   *
   * @param objectXml путь к XML объекта метаданных
   * @param version версия схемы Designer XML
   * @param tabularSectionName имя табличной части
   * @param sourceName имя исходного реквизита
   * @param newName имя копии
   * @throws IOException если не удалось прочитать/записать XML
   * @throws JAXBException если итоговый XML невалиден для JAXB-модели
   */
  public static void duplicateTabularAttribute(
    Path objectXml,
    SchemaVersion version,
    String tabularSectionName,
    String sourceName,
    String newName
  ) throws IOException, JAXBException {
    mutateAndWrite(objectXml, version, (xml, containerLocal) -> {
      MdObjectXmlRegions.Region source = MdObjectXmlRegions.findNamedNestedChildObjectRegion(
        xml,
        containerLocal,
        "TabularSection",
        tabularSectionName,
        "Attribute",
        sourceName
      );
      if (!source.isValid()) {
        throw new IllegalArgumentException("Реквизит ТЧ не найден: " + sourceName);
      }
      MdObjectXmlRegions.Region duplicate = MdObjectXmlRegions.findNamedNestedChildObjectRegion(
        xml,
        containerLocal,
        "TabularSection",
        tabularSectionName,
        "Attribute",
        newName
      );
      if (duplicate.isValid()) {
        throw new IllegalArgumentException("Реквизит ТЧ уже существует: " + newName);
      }
      return duplicateRegion(xml, source, sourceName, newName);
    });
  }

  private static void mutateAndWrite(Path objectXml, SchemaVersion version, XmlMutator mutator)
    throws IOException, JAXBException {
    if (!Files.isRegularFile(objectXml)) {
      throw new IllegalArgumentException("file not found: " + objectXml);
    }
    MdObjectStructureDto structure = MdObjectStructureRead.read(objectXml, version);
    String containerLocal = MdObjectPropertiesGranularPatch.containerLocalForKind(structure.kind);
    if (containerLocal == null || containerLocal.isBlank()) {
      throw new IllegalArgumentException("Тип объекта не поддерживает ChildObjects.");
    }
    String original = Files.readString(objectXml, StandardCharsets.UTF_8);
    String updated;
    try {
      updated = mutator.apply(original, containerLocal);
    } catch (XMLStreamException e) {
      throw new IOException("Не удалось разобрать XML для мутации: " + e.getMessage(), e);
    }
    MdObjectStructureRead.read(updated.getBytes(StandardCharsets.UTF_8), version);
    Files.writeString(objectXml, updated, StandardCharsets.UTF_8);
  }

  private static String renameNamedChild(
    String xml,
    String containerLocal,
    String childTag,
    String oldName,
    String newName,
    String label
  ) throws XMLStreamException {
    ensureNotBlank(newName, "Введите новое имя.");
    MdObjectXmlRegions.Region target = MdObjectXmlRegions.findNamedChildObjectRegion(xml, containerLocal, childTag, oldName);
    if (!target.isValid()) {
      throw new IllegalArgumentException(label + " не найден(а): " + oldName);
    }
    MdObjectXmlRegions.Region duplicate = MdObjectXmlRegions.findNamedChildObjectRegion(xml, containerLocal, childTag, newName);
    if (duplicate.isValid() && !oldName.equals(newName)) {
      throw new IllegalArgumentException(label + " уже существует: " + newName);
    }
    String nodeXml = xml.substring(target.start(), target.end());
    String replaced = replaceName(nodeXml, oldName, newName);
    return xml.substring(0, target.start()) + replaced + xml.substring(target.end());
  }

  private static String deleteNamedChild(
    String xml,
    String containerLocal,
    String childTag,
    String name,
    String label
  ) throws XMLStreamException {
    MdObjectXmlRegions.Region target = MdObjectXmlRegions.findNamedChildObjectRegion(xml, containerLocal, childTag, name);
    if (!target.isValid()) {
      throw new IllegalArgumentException(label + " не найден(а): " + name);
    }
    return removeRegion(xml, target);
  }

  private static String duplicateNamedChild(
    String xml,
    String containerLocal,
    String childTag,
    String sourceName,
    String newName,
    String label
  ) throws XMLStreamException {
    ensureNotBlank(newName, "Введите имя копии.");
    MdObjectXmlRegions.Region source = MdObjectXmlRegions.findNamedChildObjectRegion(xml, containerLocal, childTag, sourceName);
    if (!source.isValid()) {
      throw new IllegalArgumentException(label + " не найден(а): " + sourceName);
    }
    MdObjectXmlRegions.Region duplicate = MdObjectXmlRegions.findNamedChildObjectRegion(xml, containerLocal, childTag, newName);
    if (duplicate.isValid()) {
      throw new IllegalArgumentException(label + " уже существует: " + newName);
    }
    return duplicateRegion(xml, source, sourceName, newName);
  }

  private static void ensureMissingNamedChild(
    String xml,
    String containerLocal,
    String childTag,
    String name,
    String message
  ) throws XMLStreamException {
    MdObjectXmlRegions.Region region = MdObjectXmlRegions.findNamedChildObjectRegion(xml, containerLocal, childTag, name);
    if (region.isValid()) {
      throw new IllegalArgumentException(message);
    }
  }

  private static String duplicateRegion(String xml, MdObjectXmlRegions.Region source, String oldName, String newName) {
    String sourceXml = xml.substring(source.start(), source.end());
    String copy = replaceName(DistinctUuidRewrite.remap(sourceXml), oldName, newName);
    String indent = currentLineIndent(xml, source.start());
    String normalizedCopy = normalizeBlockIndent(copy, indent);
    return xml.substring(0, source.end()) + "\n" + normalizedCopy + xml.substring(source.end());
  }

  private static String insertIntoRootChildObjects(String xml, String containerLocal, String snippet)
    throws XMLStreamException {
    MdObjectXmlRegions.Region childObjectsRegion = MdObjectXmlRegions.findChildObjectsRegion(xml, containerLocal);
    if (!childObjectsRegion.isValid()) {
      throw new IllegalArgumentException("В объекте нет узла ChildObjects.");
    }
    int insertAt = xml.lastIndexOf(CLOSE_CHILD_OBJECTS, childObjectsRegion.end());
    if (insertAt < childObjectsRegion.start()) {
      throw new IllegalArgumentException("Не найден закрывающий тег ChildObjects.");
    }
    String parentIndent = currentLineIndent(xml, insertAt);
    String childIndent = parentIndent + "\t";
    String normalized = normalizeBlockIndent(snippet, childIndent);
    return xml.substring(0, insertAt) + "\n" + normalized + "\n" + parentIndent + xml.substring(insertAt);
  }

  private static String insertIntoTabularSectionChildObjects(String tabularSectionXml, String snippet)
    throws XMLStreamException {
    int childObjectsOpen = tabularSectionXml.indexOf("<ChildObjects");
    if (childObjectsOpen >= 0) {
      int childObjectsTagEnd = tabularSectionXml.indexOf('>', childObjectsOpen);
      if (childObjectsTagEnd < 0) {
        throw new IllegalArgumentException("Не найден закрывающий символ тега ChildObjects табличной части.");
      }
      String openTag = tabularSectionXml.substring(childObjectsOpen, childObjectsTagEnd + 1);
      if (openTag.endsWith("/>")) {
        String parentIndent = currentLineIndent(tabularSectionXml, childObjectsOpen);
        String childIndent = parentIndent + "\t";
        String normalized = normalizeBlockIndent(snippet, childIndent);
        String replacement = "<ChildObjects>\n"
          + normalized
          + "\n"
          + parentIndent
          + "</ChildObjects>";
        return tabularSectionXml.substring(0, childObjectsOpen)
          + replacement
          + tabularSectionXml.substring(childObjectsTagEnd + 1);
      }
      int childObjectsClose = tabularSectionXml.indexOf(CLOSE_CHILD_OBJECTS, childObjectsOpen);
      if (childObjectsClose < 0) {
        throw new IllegalArgumentException("Не найден закрывающий тег ChildObjects табличной части.");
      }
      int insertAt = childObjectsClose;
      String parentIndent = currentLineIndent(tabularSectionXml, insertAt);
      String childIndent = parentIndent + "\t";
      String normalized = normalizeBlockIndent(snippet, childIndent);
      return tabularSectionXml.substring(0, insertAt)
        + "\n"
        + normalized
        + "\n"
        + parentIndent
        + tabularSectionXml.substring(insertAt);
    }
    int closeTs = tabularSectionXml.lastIndexOf("</TabularSection>");
    if (closeTs < 0) {
      throw new IllegalArgumentException("Не найден закрывающий тег TabularSection.");
    }
    String tsIndent = currentLineIndent(tabularSectionXml, closeTs);
    String childObjectsIndent = tsIndent + "\t";
    String itemIndent = childObjectsIndent + "\t";
    String normalized = normalizeBlockIndent(snippet, itemIndent);
    String block = "\n"
      + childObjectsIndent
      + "<ChildObjects>\n"
      + normalized
      + "\n"
      + childObjectsIndent
      + "</ChildObjects>\n"
      + tsIndent;
    return tabularSectionXml.substring(0, closeTs) + block + tabularSectionXml.substring(closeTs);
  }

  private static String replaceName(String nodeXml, String oldName, String newName) {
    Matcher matcher = NAME_TAG.matcher(nodeXml);
    if (!matcher.find()) {
      throw new IllegalArgumentException("Не найден тег Name.");
    }
    String current = unescapeXml(matcher.group(1));
    if (!current.equals(oldName)) {
      throw new IllegalArgumentException("Ожидалось имя " + oldName + ", найдено " + current + ".");
    }
    String escaped = escapeXml(newName);
    return nodeXml.substring(0, matcher.start(1)) + escaped + nodeXml.substring(matcher.end(1));
  }

  private static String removeRegion(String xml, MdObjectXmlRegions.Region region) {
    String left = xml.substring(0, region.start());
    String right = xml.substring(region.end());
    if (left.endsWith("\n") && right.startsWith("\n")) {
      right = right.substring(1);
    }
    return left + right;
  }

  private static String buildAttributeSnippet(String name, String synonymRu, String comment) {
    return "<Attribute uuid=\"" + UUID.randomUUID() + "\">\n"
      + "\t<Properties>\n"
      + "\t\t<Name>" + escapeXml(name) + "</Name>\n"
      + "\t\t<Synonym>\n"
      + "\t\t\t<v8:item>\n"
      + "\t\t\t\t<v8:lang>ru</v8:lang>\n"
      + "\t\t\t\t<v8:content>" + escapeXml(synonymRu) + "</v8:content>\n"
      + "\t\t\t</v8:item>\n"
      + "\t\t</Synonym>\n"
      + (comment == null || comment.isBlank()
      ? "\t\t<Comment/>\n"
      : "\t\t<Comment>" + escapeXml(comment) + "</Comment>\n")
      + "\t</Properties>\n"
      + "</Attribute>";
  }

  private static String buildTabularSectionSnippet(String name, String synonymRu, String comment) {
    return "<TabularSection uuid=\"" + UUID.randomUUID() + "\">\n"
      + "\t<Properties>\n"
      + "\t\t<Name>" + escapeXml(name) + "</Name>\n"
      + "\t\t<Synonym>\n"
      + "\t\t\t<v8:item>\n"
      + "\t\t\t\t<v8:lang>ru</v8:lang>\n"
      + "\t\t\t\t<v8:content>" + escapeXml(synonymRu) + "</v8:content>\n"
      + "\t\t\t</v8:item>\n"
      + "\t\t</Synonym>\n"
      + (comment == null || comment.isBlank()
      ? "\t\t<Comment/>\n"
      : "\t\t<Comment>" + escapeXml(comment) + "</Comment>\n")
      + "\t</Properties>\n"
      + "\t<ChildObjects/>\n"
      + "</TabularSection>";
  }

  private static String normalizeBlockIndent(String block, String indent) {
    String normalized = block.replace("\r\n", "\n").replace('\r', '\n').trim();
    String[] lines = normalized.split("\n");
    int minLead = Integer.MAX_VALUE;
    for (String line : lines) {
      if (line.isBlank()) {
        continue;
      }
      int lead = 0;
      while (lead < line.length() && (line.charAt(lead) == ' ' || line.charAt(lead) == '\t')) {
        lead++;
      }
      minLead = Math.min(minLead, lead);
    }
    if (minLead == Integer.MAX_VALUE) {
      minLead = 0;
    }
    List<String> out = new ArrayList<>();
    for (String line : lines) {
      String core = line.length() >= minLead ? line.substring(minLead) : line.trim();
      out.add(indent + core);
    }
    return String.join("\n", out);
  }

  private static String currentLineIndent(String xml, int offset) {
    int start = offset - 1;
    while (start >= 0 && xml.charAt(start) != '\n' && xml.charAt(start) != '\r') {
      start--;
    }
    start++;
    int i = start;
    while (i < xml.length() && (xml.charAt(i) == ' ' || xml.charAt(i) == '\t')) {
      i++;
    }
    return xml.substring(start, i);
  }

  private static void ensureNotBlank(String value, String message) {
    if (value == null || value.trim().isEmpty()) {
      throw new IllegalArgumentException(message);
    }
  }

  private static String escapeXml(String value) {
    String v = value == null ? "" : value;
    return v.replace("&", "&amp;")
      .replace("<", "&lt;")
      .replace(">", "&gt;")
      .replace("\"", "&quot;")
      .replace("'", "&apos;");
  }

  private static String unescapeXml(String value) {
    return value.replace("&lt;", "<")
      .replace("&gt;", ">")
      .replace("&quot;", "\"")
      .replace("&apos;", "'")
      .replace("&amp;", "&");
  }

  private interface XmlMutator {
    String apply(String xml, String containerLocal) throws XMLStreamException;
  }
}
