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
import org.w3c.dom.Document;
import org.w3c.dom.bootstrap.DOMImplementationRegistry;
import org.w3c.dom.ls.DOMImplementationLS;
import org.w3c.dom.ls.LSInput;
import org.w3c.dom.ls.LSResourceResolver;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Pattern;

/**
 * Строгая проверка схемы компоновки данных: XSD-валидация по схемам СКД {@code namespace-forest}
 * с поправками «как пишет платформа» ({@link DcsXsdShims}) плюс обычное JAXB-чтение.
 * <p>
 * Схемы берутся из ресурсов сборки ({@code designer-schemas/…} — набор самого нового формата submodule):
 * содержимое Template.xml версии формата не несёт, а схема более нового формата принимает файлы прежних.
 * Кроме шести схем СКД и их import-цепочки ({@code data/core}, {@code data/ui}, {@code data/spreadsheet})
 * в набор входит {@code data/enterprise}: его перечисления ({@code FoldersAndItemsUse},
 * {@code LinkedValueChangeMode}) платформа пишет в {@code xsi:type} значений настроек, хотя схемы СКД это
 * пространство не импортируют.
 * <p>
 * Значения параметров и отборов платформа пишет с {@code xsi:type} и таких пространств, схем которых в
 * коллекции нет вовсе и быть не может: типы конфигурации ({@code data/enterprise/current-config}),
 * диаграммы ({@code 8.2/data/chart}), цвета и стили ({@code data/ui/colors/*}, {@code data/ui/style}).
 * Такие типы проверяются как непрозрачные значения: перед проверкой файл просматривается, и для каждого
 * употреблённого типа из неизвестного пространства в набор схем добавляется заглушка, принимающая любое
 * содержимое. Ошибкой остаются неизвестные элементы, элементы не на месте и неверные значения известных типов.
 */
public final class DcsXsdValidation {

  /** Каталог схем конфигуратора в ресурсах сборки (кладёт gradle-задача prepareDesignerTypeSchemas). */
  private static final String SCHEMAS_RESOURCE_DIR = "/designer-schemas";

  /**
   * Пространства имён набора строгой проверки → имя файла XSD в ресурсах сборки.
   * Шесть схем СКД, их import-цепочка и {@code data/enterprise} (см. javadoc класса).
   */
  private static final Map<String, String> NS_TO_XSD_FILE = Map.ofEntries(
    Map.entry("http://v8.1c.ru/8.1/data-composition-system/schema", DcsXsdShims.DCS_SCHEMA_XSD),
    Map.entry("http://v8.1c.ru/8.1/data-composition-system/core", "v8.1c.ru-8.1-data-composition-system-core.xsd"),
    Map.entry("http://v8.1c.ru/8.1/data-composition-system/common", "v8.1c.ru-8.1-data-composition-system-common.xsd"),
    Map.entry("http://v8.1c.ru/8.1/data-composition-system/details", "v8.1c.ru-8.1-data-composition-system-details.xsd"),
    Map.entry("http://v8.1c.ru/8.1/data-composition-system/settings", "v8.1c.ru-8.1-data-composition-system-settings.xsd"),
    Map.entry("http://v8.1c.ru/8.1/data-composition-system/area-template", DcsXsdShims.DCS_AREA_TEMPLATE_XSD),
    Map.entry("http://v8.1c.ru/8.1/data/core", DcsXsdShims.DATA_CORE_XSD),
    Map.entry("http://v8.1c.ru/8.1/data/ui", "v8.1c.ru-8.1-data-ui.xsd"),
    Map.entry("http://v8.1c.ru/8.2/data/spreadsheet", "v8.1c.ru-8.2-data-spreadsheet.xsd"),
    Map.entry("http://v8.1c.ru/8.1/data/enterprise", "v8.1c.ru-8.1-data-enterprise.xsd"));

  /** Имя типа в {@code xsi:type}, под которое можно объявить заглушку (NCName; битые имена отдаются валидатору). */
  private static final Pattern STUB_TYPE_NAME = Pattern.compile("[\\p{L}_][\\p{L}\\p{N}._\\-]*");

  /**
   * Схема проверки и заглушенные ею типы. Набор заглушек только растёт (объединение по всем проверенным
   * файлам): лишние глобальные типы на проверку не влияют, зато схема не пересобирается на каждый файл
   * с теми же ссылочными типами конфигурации.
   */
  private static Schema schema;
  private static final Map<String, Set<String>> stubbed = new TreeMap<>();

  private DcsXsdValidation() {
  }

  /**
   * Строгая проверка файла схемы компоновки (выгрузка конфигуратора или {@code Template.dcs} 1С:EDT).
   *
   * @param templateXml путь к файлу схемы
   * @return {@code {ok, errors: [{message, line?, column?}], warnings: [{message, parameter?, dataSet?, link?,
   *     kind}]}}; строки и колонки — как их дал валидатор; предупреждения на {@code ok} не влияют: сначала
   *     необъявленные параметры запросов ({@link DcsQueryParameters}, с {@code dataSet}), затем списки
   *     значений элементом {@code v8:ValueListType}, чьи представления потеряет загрузка
   *     ({@link DcsLegacyValueLists}), затем связи наборов с флагом списка без параметра связи
   *     ({@link DcsLegacyLinkListFlags}, с {@code link} вместо {@code parameter})
   * @throws IllegalArgumentException файла нет или схемы сборки не собираются
   */
  public static Map<String, Object> validate(Path templateXml) {
    if (!Files.isRegularFile(templateXml)) {
      throw new IllegalArgumentException("Файл схемы компоновки не найден: " + templateXml.toAbsolutePath());
    }
    List<Map<String, Object>> errors = xsdErrors(templateXml);
    if (errors.isEmpty()) {
      // JAXB-разбор поверх чистой XSD-проверки: модель обязана читаться, иначе валидный по схеме файл
      // всё равно не возьмут операции чтения и правки
      jaxbError(templateXml).ifPresent(errors::add);
    }
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("ok", errors.isEmpty());
    out.put("errors", errors);
    try {
      List<Map<String, Object>> warnings = new ArrayList<>(DcsQueryParameters.warnings(templateXml));
      warnings.addAll(DcsLegacyValueLists.warnings(templateXml));
      warnings.addAll(DcsLegacyLinkListFlags.warnings(templateXml));
      out.put("warnings", warnings);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    return out;
  }

  /** Все несоответствия схеме: валидатор не останавливается на первом, кроме испорченной разметки. */
  private static List<Map<String, Object>> xsdErrors(Path templateXml) {
    List<Map<String, Object>> errors = new ArrayList<>();
    try {
      Validator validator = schemaFor(templateXml).newValidator();
      validator.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
      validator.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
      validator.setErrorHandler(new ErrorHandler() {
        @Override
        public void warning(SAXParseException e) {
        }

        @Override
        public void error(SAXParseException e) {
          errors.add(errorEntry(e));
        }

        @Override
        public void fatalError(SAXParseException e) throws SAXException {
          errors.add(errorEntry(e));
          throw e;
        }
      });
      try (InputStream in = Files.newInputStream(templateXml)) {
        validator.validate(new StreamSource(in, templateXml.toUri().toString()));
      }
    } catch (SAXException e) {
      // Испорченная разметка: ошибка уже собрана обработчиком, а если нет (сбой до разбора) — добавляется
      if (errors.isEmpty()) {
        errors.add(errorEntry(e));
      }
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    return errors;
  }

  /** Ошибка JAXB-чтения модели ({@link DcsRead#validate}); разбор файла, прошедшего XSD, обычно молчит. */
  private static java.util.Optional<Map<String, Object>> jaxbError(Path templateXml) {
    Map<String, Object> read = DcsRead.validate(templateXml, latestVersion());
    if (Boolean.TRUE.equals(read.get("valid"))) {
      return java.util.Optional.empty();
    }
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("message", String.valueOf(read.get("error")));
    return java.util.Optional.of(out);
  }

  /** Версия для JAXB-чтения: модель схемы компоновки одна во всех форматах, берётся самая новая. */
  private static SchemaVersion latestVersion() {
    SchemaVersion[] all = SchemaVersion.values();
    return all[all.length - 1];
  }

  private static Map<String, Object> errorEntry(SAXException e) {
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("message", String.valueOf(e.getMessage()));
    if (e instanceof SAXParseException parse) {
      if (parse.getLineNumber() > 0) {
        out.put("line", parse.getLineNumber());
      }
      if (parse.getColumnNumber() > 0) {
        out.put("column", parse.getColumnNumber());
      }
    }
    return out;
  }

  /**
   * Схема проверки, покрывающая {@code xsi:type} файла: типы из пространств без XSD заглушаются.
   * Собирается один раз и пересобирается, только когда файл приносит ещё не заглушенный тип.
   */
  private static synchronized Schema schemaFor(Path templateXml) throws IOException, SAXException {
    Map<String, Set<String>> unknown = unknownXsiTypes(templateXml);
    boolean grown = false;
    for (Map.Entry<String, Set<String>> e : unknown.entrySet()) {
      grown |= stubbed.computeIfAbsent(e.getKey(), ns -> new TreeSet<>()).addAll(e.getValue());
    }
    if (schema == null || grown) {
      schema = buildSchema();
    }
    return schema;
  }

  /** Типы из {@code xsi:type}, пространства которых в наборе схем нет: пространство → имена типов. */
  private static Map<String, Set<String>> unknownXsiTypes(Path templateXml) throws IOException {
    Map<String, Set<String>> out = new TreeMap<>();
    XMLInputFactory factory = XMLInputFactory.newFactory();
    factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
    factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
    try (InputStream in = Files.newInputStream(templateXml)) {
      XMLStreamReader reader = factory.createXMLStreamReader(in);
      try {
        while (reader.hasNext()) {
          if (reader.next() != XMLStreamConstants.START_ELEMENT) {
            continue;
          }
          String value = reader.getAttributeValue(XMLConstants.W3C_XML_SCHEMA_INSTANCE_NS_URI, "type");
          if (value == null) {
            continue;
          }
          int colon = value.indexOf(':');
          String prefix = colon < 0 ? XMLConstants.DEFAULT_NS_PREFIX : value.substring(0, colon);
          String local = colon < 0 ? value : value.substring(colon + 1);
          String ns = reader.getNamespaceContext().getNamespaceURI(prefix);
          if (ns == null || ns.isEmpty() || NS_TO_XSD_FILE.containsKey(ns)
            || XMLConstants.W3C_XML_SCHEMA_NS_URI.equals(ns)
            || XMLConstants.W3C_XML_SCHEMA_INSTANCE_NS_URI.equals(ns)
            || !STUB_TYPE_NAME.matcher(local).matches()) {
            continue;
          }
          out.computeIfAbsent(ns, key -> new TreeSet<>()).add(local);
        }
      } finally {
        reader.close();
      }
    } catch (XMLStreamException e) {
      // Испорченную разметку опишет с номером строки сама проверка по схеме.
    }
    return out;
  }

  /** Собирает схему из ресурсов сборки с подменами {@link DcsXsdShims} и заглушками неизвестных пространств. */
  private static Schema buildSchema() throws IOException, SAXException {
    SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
    factory.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
    factory.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
    factory.setResourceResolver(new ResourceSchemaResolver());
    List<Source> sources = new ArrayList<>();
    for (Map.Entry<String, Set<String>> e : stubbed.entrySet()) {
      sources.add(new StreamSource(new StringReader(stubSchema(e.getKey(), e.getValue())),
        "urn:md-sparrow:dcs-stub:" + e.getKey()));
    }
    // data/enterprise никем из схем СКД не импортируется, поэтому входит отдельным источником
    sources.add(patchedSource("v8.1c.ru-8.1-data-enterprise.xsd"));
    sources.add(patchedSource(DcsXsdShims.DCS_SCHEMA_XSD));
    try {
      return factory.newSchema(sources.toArray(Source[]::new));
    } catch (SAXException e) {
      throw schemaError(e);
    } catch (UncheckedIOException e) {
      throw e.getCause();
    }
  }

  /**
   * Заглушка пространства без XSD: каждый употреблённый тип принимает любое содержимое и атрибуты.
   * Значения таких типов проверить нечем, но файл с ними остаётся проверяемым целиком.
   */
  private static String stubSchema(String namespace, Set<String> types) {
    StringBuilder out = new StringBuilder();
    out.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
      .append("<xs:schema xmlns:xs=\"").append(XMLConstants.W3C_XML_SCHEMA_NS_URI).append("\" targetNamespace=\"")
      .append(namespace.replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;"))
      .append("\">\n");
    for (String type : types) {
      out.append("\t<xs:complexType name=\"").append(type).append("\" mixed=\"true\">\n")
        .append("\t\t<xs:sequence><xs:any processContents=\"skip\" minOccurs=\"0\" maxOccurs=\"unbounded\"/></xs:sequence>\n")
        .append("\t\t<xs:anyAttribute processContents=\"skip\"/>\n")
        .append("\t</xs:complexType>\n");
    }
    return out.append("</xs:schema>\n").toString();
  }

  /** Текст схемы из ресурсов сборки после подмен {@link DcsXsdShims}. */
  private static Source patchedSource(String fileName) throws IOException, SAXException {
    return new StreamSource(new StringReader(patchedSchema(fileName)), resourceSystemId(fileName));
  }

  /** Адрес схемы в ресурсах сборки; вызывается, когда ресурс уже прочитан. */
  private static String resourceSystemId(String fileName) {
    return DcsXsdValidation.class.getResource(SCHEMAS_RESOURCE_DIR + "/" + fileName).toString();
  }

  private static String patchedSchema(String fileName) throws IOException, SAXException {
    Document doc = parseSchema(fileName);
    DcsXsdShims.apply(fileName, doc);
    try {
      TransformerFactory factory = TransformerFactory.newInstance();
      factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
      factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");
      Transformer transformer = factory.newTransformer();
      StringWriter out = new StringWriter();
      transformer.transform(new DOMSource(doc), new StreamResult(out));
      return out.toString();
    } catch (TransformerException e) {
      throw new IllegalStateException("Сериализация XSD " + fileName, e);
    }
  }

  private static Document parseSchema(String fileName) throws IOException, SAXException {
    InputStream stream = DcsXsdValidation.class.getResourceAsStream(SCHEMAS_RESOURCE_DIR + "/" + fileName);
    if (stream == null) {
      throw new IllegalArgumentException(
        "В сборке нет схемы конфигуратора: " + SCHEMAS_RESOURCE_DIR + "/" + fileName);
    }
    try (InputStream in = stream) {
      DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
      dbf.setNamespaceAware(true);
      dbf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
      var builder = dbf.newDocumentBuilder();
      // Без своего обработчика разборщик печатает «[Fatal Error]» в stderr помимо исключения.
      builder.setErrorHandler(new DefaultHandler());
      return builder.parse(in, resourceSystemId(fileName));
    } catch (ParserConfigurationException e) {
      throw new IllegalStateException("DocumentBuilderFactory", e);
    }
  }

  /** Ошибка сборки схем — не ошибка проверяемого файла: отдельный отказ с местом в XSD. */
  private static IllegalArgumentException schemaError(SAXException e) {
    String where = "";
    if (e instanceof SAXParseException parse && parse.getSystemId() != null) {
      where = parse.getSystemId() + ":" + parse.getLineNumber() + ":" + parse.getColumnNumber() + ": ";
    }
    return new IllegalArgumentException("Схемы строгой проверки СКД не собираются: " + where + e.getMessage(), e);
  }

  /** Разрешение import схем набора по пространству имён из ресурсов сборки, с подменами. */
  private static final class ResourceSchemaResolver implements LSResourceResolver {

    private final DOMImplementationLS domLs;

    ResourceSchemaResolver() {
      try {
        DOMImplementationRegistry registry = DOMImplementationRegistry.newInstance();
        this.domLs = (DOMImplementationLS) registry.getDOMImplementation("LS 3.0");
      } catch (Exception e) {
        throw new IllegalStateException("DOM LS 3.0 not available", e);
      }
    }

    @Override
    public LSInput resolveResource(String type, String namespaceURI, String publicId, String systemId, String baseURI) {
      String file = namespaceURI == null ? null : NS_TO_XSD_FILE.get(namespaceURI);
      if (file == null) {
        return null;
      }
      String text;
      try {
        text = patchedSchema(file);
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      } catch (SAXException e) {
        throw schemaError(e);
      }
      LSInput input = domLs.createLSInput();
      input.setStringData(text);
      input.setSystemId(resourceSystemId(file));
      return input;
    }
  }
}
