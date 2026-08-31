/*
 * This file is a part of md-sparrow.
 *
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package io.github.yellowhammer.designerxml.cf;

import io.github.yellowhammer.designerxml.SchemaVersion;
import io.github.yellowhammer.designerxml.cf.FormItemPropertyDictionary.FormItemPropertyDto;
import io.github.yellowhammer.designerxml.cf.FormXmlRegions.FormItemRegion;

import jakarta.xml.bind.JAXBException;

import javax.xml.stream.XMLStreamException;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Точечная запись свойств элементов управляемой формы: правится только сам узел свойства, остальной
 * файл остаётся байт в байт как был.
 *
 * <p>Полная пересборка формы через JAXB непригодна: платформа пишет в файл лишь изменённые свойства,
 * а marshaller записал бы весь состав со значениями по умолчанию.
 */
public final class FormItemPropertyEdit {

  private FormItemPropertyEdit() {
  }

  /**
   * Применяет изменения свойств к {@code Ext/Form.xml}.
   *
   * @param formXml путь к {@code Ext/Form.xml}
   * @param version версия формата выгрузки
   * @param changes изменения; пустой список ничего не делает
   */
  public static void apply(Path formXml, SchemaVersion version, List<FormItemPropertyChangeDto> changes)
    throws IOException, JAXBException {
    if (!Files.isRegularFile(formXml)) {
      throw new IllegalArgumentException("file not found: " + formXml);
    }
    if (changes == null || changes.isEmpty()) {
      return;
    }
    SupportRules.ensureEditable(formXml);
    String xml = Files.readString(formXml, StandardCharsets.UTF_8);
    Map<String, List<FormItemPropertyDto>> dictionary = FormItemPropertyDictionary.forVersion(version);
    List<XmlGranularPatch.Replacement> replacements = new ArrayList<>();
    for (FormItemPropertyChangeDto change : changes) {
      replacement(xml, dictionary, change).ifPresent(replacements::add);
    }
    if (replacements.isEmpty()) {
      return;
    }
    String patched = XmlGranularPatch.withoutOverlaps(replacements)
      .map(safe -> XmlGranularPatch.apply(xml, safe))
      .orElseThrow(() -> new IllegalArgumentException("в одном вызове два разных значения одного свойства"));
    byte[] bytes = patched.getBytes(StandardCharsets.UTF_8);
    verify(bytes, version, changes);
    Files.write(formXml, bytes);
  }

  private static Optional<XmlGranularPatch.Replacement> replacement(
    String xml,
    Map<String, List<FormItemPropertyDto>> dictionary,
    FormItemPropertyChangeDto change) {
    String itemId = required(change.itemId, "itemId");
    String name = required(change.property, "property");
    FormItemRegion item = findItem(xml, itemId);
    FormItemPropertyDto spec = FormItemPropertyDictionary.find(dictionary, item.type(), name)
      .orElseThrow(() -> new IllegalArgumentException(
        "у вида элемента " + item.type() + " нет свойства " + name));
    if (Boolean.TRUE.equals(spec.attribute)) {
      throw new IllegalArgumentException("свойство " + name + " записано атрибутом, точечная запись его не меняет");
    }
    MdObjectXmlRegions.Region current = item.properties().get(name);
    if (change.value == null) {
      return current == null ? Optional.empty() : Optional.of(removal(xml, current));
    }
    String elementXml = elementXml(spec, name, change.value);
    if (current != null) {
      return Optional.of(new XmlGranularPatch.Replacement(
        current.start(),
        current.end(),
        XmlGranularPatch.formatReplacementPreservingIndent(xml, current.start(), elementXml)));
    }
    return Optional.of(insertion(xml, item, elementXml));
  }

  /** Свойства нет в файле: добавляем узел перед составом элемента, у пустого тега раскрываем сам тег. */
  private static XmlGranularPatch.Replacement insertion(String xml, FormItemRegion item, String elementXml) {
    String eol = XmlGranularPatch.fileEol(xml);
    if (item.emptyTag()) {
      String indent = XmlGranularPatch.currentLineIndent(xml, item.start());
      String body = XmlGranularPatch.formatInsertion(xml, indent + "\t", elementXml);
      return new XmlGranularPatch.Replacement(
        item.startTagEnd() - 1,
        item.end(),
        ">" + eol + body + eol + indent + "</" + item.type() + ">");
    }
    return new XmlGranularPatch.Replacement(
      item.insertPos(),
      item.insertPos(),
      XmlGranularPatch.formatInsertion(xml, item.insertIndent(), elementXml) + eol);
  }

  /** Убирает узел свойства вместе с отступом его строки, чтобы не осталось пустой строки. */
  private static XmlGranularPatch.Replacement removal(String xml, MdObjectXmlRegions.Region current) {
    int from = current.start();
    while (from > 0 && (xml.charAt(from - 1) == ' ' || xml.charAt(from - 1) == '\t')) {
      from--;
    }
    if (from > 0 && xml.charAt(from - 1) == '\n') {
      from--;
    }
    if (from > 0 && xml.charAt(from - 1) == '\r') {
      from--;
    }
    return new XmlGranularPatch.Replacement(from, current.end(), "");
  }

  /** Узел свойства в том виде, в каком его пишет платформа. */
  private static String elementXml(FormItemPropertyDto spec, String name, String value) {
    switch (spec.kind) {
      case "boolean":
        if (!"true".equals(value) && !"false".equals(value)) {
          throw new IllegalArgumentException("свойство " + name + " булево, а значение " + value);
        }
        return MdCatalogPropertiesGranularSerial.textElement(name, value);
      case "number":
        try {
          return MdCatalogPropertiesGranularSerial.textElement(name, new BigDecimal(value.trim()).toPlainString());
        } catch (NumberFormatException e) {
          throw new IllegalArgumentException("свойство " + name + " числовое, а значение " + value);
        }
      case "enum":
        if (spec.values != null && !spec.values.contains(value)) {
          throw new IllegalArgumentException(
            "у свойства " + name + " нет значения " + value + "; возможны " + String.join(", ", spec.values));
        }
        return MdCatalogPropertiesGranularSerial.textElement(name, value);
      case "string":
        return MdCatalogPropertiesGranularSerial.textElement(name, value);
      case "localString":
        return MdCatalogPropertiesGranularSerial.localStringRuElement(name, value);
      case "formattedString":
        // Признак форматирования платформа пишет всегда; форматированный текст палитра не редактирует.
        return MdCatalogPropertiesGranularSerial.localStringRuElement(name, value)
          .replaceFirst("^<" + name + ">", "<" + name + " formatted=\"false\">");
      default:
        throw new IllegalArgumentException("свойство " + name + " составное, точечная запись его не меняет");
    }
  }

  private static FormItemRegion findItem(String xml, String itemId) {
    FormItemRegion item;
    try {
      item = FormXmlRegions.findItem(xml, itemId);
    } catch (XMLStreamException e) {
      throw new IllegalArgumentException("не удалось разобрать форму: " + e.getMessage());
    }
    if (item == null) {
      throw new IllegalArgumentException("в форме нет элемента с идентификатором " + itemId);
    }
    return item;
  }

  /**
   * Сверка записанного: форма читается обратно и у каждого изменённого свойства значение то же,
   * что просили. Иначе файл не переписываем.
   */
  private static void verify(byte[] bytes, SchemaVersion version, List<FormItemPropertyChangeDto> changes)
    throws JAXBException {
    FormContentDto content = FormContentRead.read(bytes, version);
    for (FormItemPropertyChangeDto change : changes) {
      FormItemDto item = findItem(content.items, change.itemId);
      if (item == null) {
        throw new IllegalStateException("после записи в форме нет элемента " + change.itemId);
      }
      String written = item.properties.get(change.property);
      if (!sameValue(written, change.value)) {
        throw new IllegalStateException(
          "свойство " + change.property + " записано как " + written + ", а ожидали " + change.value);
      }
    }
  }

  /** Пустое значение платформа не пишет вовсе, число пишет без незначащих нулей. */
  private static boolean sameValue(String written, String expected) {
    if (expected == null || expected.isEmpty()) {
      return written == null;
    }
    if (expected.equals(written)) {
      return true;
    }
    return written != null && numeric(written) && numeric(expected)
      && new BigDecimal(written).compareTo(new BigDecimal(expected)) == 0;
  }

  private static boolean numeric(String text) {
    try {
      new BigDecimal(text.trim());
      return true;
    } catch (NumberFormatException e) {
      return false;
    }
  }

  private static FormItemDto findItem(List<FormItemDto> items, String itemId) {
    for (FormItemDto item : items) {
      if (itemId.equals(item.id)) {
        return item;
      }
      FormItemDto nested = findItem(item.items, itemId);
      if (nested != null) {
        return nested;
      }
    }
    return null;
  }

  private static String required(String value, String field) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("обязательное поле не задано: " + field);
    }
    return value;
  }
}
