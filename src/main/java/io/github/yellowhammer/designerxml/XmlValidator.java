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
package io.github.yellowhammer.designerxml;

import org.w3c.dom.Document;
import org.w3c.dom.bootstrap.DOMImplementationRegistry;
import org.w3c.dom.ls.DOMImplementationLS;
import org.w3c.dom.ls.LSInput;
import org.w3c.dom.ls.LSResourceResolver;
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
import java.net.URI;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Проверка файла описания объекта (корень {@code MetaDataObject}) по XSD формата: {@code v8.1c.ru-8.3-MDClasses.xsd}
 * и цепочка её {@code import}. Карта {@code namespace → файл XSD} строится из каталога версии
 * ({@code schemas/designer/<версия>}) по соглашению имён 1С. Схема собирается один раз на каталог версии.
 * <p>
 * Схемы {@code namespace-forest} — XDTO-выгрузка самой платформы, они описывают модель XDTO, а не файлы выгрузки.
 * Отличия от того, что пишет платформа, сглаживаются в памяти (файлы схем не меняются), см. {@link XsdPlatformShims}:
 * <ul>
 *   <li>{@code *Properties} и {@code StandardAttributeDescription}: свойства в любом порядке, каждое не больше
 *       раза, любое может отсутствовать ({@code xs:all} вместо последовательности или одиночного {@code xs:choice});</li>
 *   <li>{@code ConfigurationChildObjects}: {@code WebSocketClient} после {@code WSReference}, {@code Bot} и
 *       {@code PaletteColor} после {@code DefinedType};</li>
 *   <li>{@code Configuration/@formatVersion} необязателен;</li>
 *   <li>элементы без типа ({@code MinValue}, {@code MaxValue}, {@code FillValue}) допускают {@code xsi:nil};</li>
 *   <li>{@code InternalInfo}: {@code GeneratedType} и {@code ContainedObject} вперемешку;</li>
 *   <li>{@code TypeLink}: {@code DataPath} необязателен (пустой {@code LinkByType});</li>
 *   <li>{@code ValueList}: сколько угодно {@code Item}, в том числе ни одного;</li>
 *   <li>{@code Picture}: свойства в любом порядке ({@code Ref} и {@code LoadTransparent} вместе);</li>
 *   <li>{@code ReferenceType}: без шаблона {@code ([A-Za-z0-9_])*}, ссылки вида {@code Role.ПолныеПрава};
 *       {@code DataPath} — строка ({@code xsi:type="xs:string"} в связях параметров выбора);</li>
 *   <li>{@code ExtendedProperty} выводится из {@code v8:TypeDescription}: расширяемое свойство {@code Type}
 *       заимствованного определяемого типа;</li>
 *   <li>{@code CompatibilityMode}: шаблон {@code DontUse|Version\d+(_\d+)+} вместо перечисления XDTO — оно не перечень
 *       значений файла;</li>
 *   <li>{@code MobileApplicationFunctionalities}: {@code SpeechToText} с 2.17, {@code TextToSpeech} с 2.18;</li>
 *   <li>{@code RequiredPermission}: {@code description} необязателен (формат 2.10).</li>
 * </ul>
 * Неизвестные элементы, повтор свойства, неверные значения, нарушение вложенности и порядка вне свойств
 * ({@code ChildObjects}, {@code InternalInfo}) остаются ошибками. Файлы с другим корнем (содержимое {@code Ext},
 * {@code ConfigDumpInfo.xml}, {@code .mdo} 1С:EDT) отклоняются до проверки.
 */
public final class XmlValidator {

  static final String NS_MD_CLASSES = "http://v8.1c.ru/8.3/MDClasses";

  /**
   * Пространства имён 1С → имя файла XSD в каталоге версии. Набор и имена одинаковы во всех версиях;
   * корневая {@code MDClasses} в карту import'ов не входит. Совпадает со списком в build.gradle.kts.
   */
  private static final Map<String, String> NS_TO_XSD_FILE = Map.ofEntries(
    Map.entry("http://v8.1c.ru/8.1/data/core", "v8.1c.ru-8.1-data-core.xsd"),
    Map.entry("http://v8.1c.ru/8.1/data/enterprise", "v8.1c.ru-8.1-data-enterprise.xsd"),
    Map.entry("http://v8.1c.ru/8.1/data/ui", "v8.1c.ru-8.1-data-ui.xsd"),
    Map.entry("http://v8.1c.ru/8.2/managed-application/core", "v8.1c.ru-8.2-managed-application-core.xsd"),
    Map.entry("http://v8.1c.ru/8.2/managed-application/cmi", "v8.1c.ru-8.2-managed-application-cmi.xsd"),
    Map.entry("http://v8.1c.ru/8.2/managed-application/logform", "v8.1c.ru-8.2-managed-application-logform.xsd"),
    Map.entry("http://v8.1c.ru/8.3/xcf/enums", "v8.1c.ru-8.3-xcf-enums.xsd"),
    Map.entry("http://v8.1c.ru/8.3/xcf/readable", "v8.1c.ru-8.3-xcf-readable.xsd"),
    Map.entry("http://v8.1c.ru/8.3/xcf/predef", "v8.1c.ru-8.3-xcf-predef.xsd"),
    Map.entry("http://v8.1c.ru/8.2/data/spreadsheet", "v8.1c.ru-8.2-data-spreadsheet.xsd"),
    Map.entry("http://v8.1c.ru/8.2/data/bsl", "v8.1c.ru-8.2-data-bsl.xsd"),
    Map.entry("http://v8.1c.ru/8.2/managed-application/modules", "v8.1c.ru-8.2-managed-application-modules.xsd"),
    Map.entry("http://v8.1c.ru/8.2/uobjects", "v8.1c.ru-8.2-uobjects.xsd"));

  /** Собранные схемы по каталогу версии: сборка с подменами занимает секунды, а схема неизменяема. */
  private static final Map<Path, Schema> SCHEMAS = new ConcurrentHashMap<>();

  private XmlValidator() {
  }

  /**
   * Проверка файла описания объекта по схеме формата с подменами под вывод платформы.
   *
   * @param xmlPath путь к {@code .xml}
   * @param version версия (подкаталог под {@code xsdCollectionRoot})
   * @param xsdCollectionRoot корень коллекции XSD (обычно submodule {@code resources/namespace-forest})
   * @throws SAXException несоответствие документа схеме ({@link SAXParseException} с номером строки)
   * @throws IOException ошибки чтения файлов
   * @throws IllegalArgumentException файла нет, его корень не {@code MetaDataObject}, формат файла
   *     ({@code MetaDataObject/@version}) не {@code version}, схемы формата нет или она не собирается
   */
  public static void validate(Path xmlPath, SchemaVersion version, Path xsdCollectionRoot)
    throws SAXException, IOException {
    if (!Files.isRegularFile(xmlPath)) {
      throw new IllegalArgumentException(
        "Файл XML не найден или не обычный файл: "
          + xmlPath.toAbsolutePath()
          + ". Укажите реальный путь к .xml (в README path/to.xml — только шаблон).");
    }
    requireObjectDescription(xmlPath, version);
    Validator validator = schema(xsdCollectionRoot.resolve(version.xsdDirectoryName()), version).newValidator();
    validator.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
    validator.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
    validator.validate(new StreamSource(xmlPath.toFile()));
  }

  /**
   * Подмены схем сделаны только для описаний объектов. У файлов с другим корнем ответом было бы «нет объявления
   * элемента» (права, справка, {@code .mdo} 1С:EDT) или ошибки по схеме, которая без подмен с выводом платформы
   * не сходится (формы, макеты, предопределённые данные).
   * <p>
   * Формат файла должен совпадать с форматом схемы: перечисление версий в схеме включает все прежние форматы,
   * и схема более нового формата пропустит то, чего формат файла ещё не знает.
   */
  private static void requireObjectDescription(Path xmlPath, SchemaVersion version) throws IOException {
    XMLInputFactory factory = XMLInputFactory.newFactory();
    factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
    factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
    try (InputStream in = Files.newInputStream(xmlPath)) {
      XMLStreamReader reader = factory.createXMLStreamReader(in);
      try {
        while (reader.hasNext()) {
          if (reader.next() == XMLStreamConstants.START_ELEMENT) {
            if (!NS_MD_CLASSES.equals(reader.getNamespaceURI()) || !"MetaDataObject".equals(reader.getLocalName())) {
              throw new IllegalArgumentException(
                "Проверяются только описания объектов (корень MetaDataObject), а корень файла — {"
                  + reader.getNamespaceURI() + "}" + reader.getLocalName()
                  + ". Файл: " + xmlPath.toAbsolutePath());
            }
            String fileVersion = reader.getAttributeValue(null, "version");
            if (fileVersion != null && !fileVersion.equals(version.metadataObjectVersionAttribute())) {
              throw new IllegalArgumentException(
                "Формат файла — " + fileVersion + ", а проверка запрошена по схеме "
                  + version.metadataObjectVersionAttribute() + ". Файл: " + xmlPath.toAbsolutePath());
            }
            return;
          }
        }
      } finally {
        reader.close();
      }
    } catch (XMLStreamException e) {
      // Испорченную разметку опишет с номером строки сама проверка по схеме.
    }
  }

  /**
   * Схема формата из каталога версии с подменами {@link XsdPlatformShims}; собирается один раз на каталог.
   *
   * @param xsdDir каталог версии ({@code schemas/designer/<версия>})
   * @param version формат схем каталога
   * @return собранная схема
   * @throws SAXException фабрика схем не принимает ограничения доступа к внешним ресурсам
   * @throws IOException ошибка чтения схем
   * @throws IllegalArgumentException схемы нет или она не собирается: в тексте файл XSD и место ошибки
   */
  static Schema schema(Path xsdDir, SchemaVersion version) throws SAXException, IOException {
    Path dir = xsdDir.toAbsolutePath().normalize();
    Path mainXsd = dir.resolve(XsdPlatformShims.MD_CLASSES_XSD);
    if (!Files.isRegularFile(mainXsd)) {
      throw new IllegalArgumentException("XSD not found: " + mainXsd);
    }
    Schema cached = SCHEMAS.get(dir);
    if (cached != null) {
      return cached;
    }
    SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
    factory.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
    factory.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
    factory.setResourceResolver(new SchemaImportResolver(dir, buildSchemaUriMap(dir), version));
    Schema schema;
    try {
      schema = factory.newSchema(new StreamSource(new StringReader(patchedSchema(mainXsd, version)), mainXsd.toUri().toString()));
    } catch (UncheckedIOException e) {
      throw e.getCause();
    } catch (SAXException e) {
      throw schemaError(e, mainXsd);
    }
    SCHEMAS.putIfAbsent(dir, schema);
    return SCHEMAS.get(dir);
  }

  /**
   * Текст схемы после подмен {@link XsdPlatformShims} (для файлов без подмен — та же схема).
   *
   * @param xsd файл схемы
   * @param version формат схемы
   * @return текст XSD
   * @throws SAXException схема не разбирается
   * @throws IOException ошибка чтения
   */
  static String patchedSchema(Path xsd, SchemaVersion version) throws SAXException, IOException {
    Document doc = parseSchema(xsd);
    XsdPlatformShims.apply(xsd.getFileName().toString(), doc, version);
    try {
      TransformerFactory factory = TransformerFactory.newInstance();
      factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
      factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");
      Transformer transformer = factory.newTransformer();
      StringWriter out = new StringWriter();
      transformer.transform(new DOMSource(doc), new StreamResult(out));
      String text = out.toString();
      // Перевод строки после объявления XML в DOM не хранится: без него строки ошибок сборки сдвинутся на одну.
      int prolog = text.startsWith("<?xml") ? text.indexOf("?>") + 2 : -1;
      if (prolog > 1 && prolog < text.length() && text.charAt(prolog) == '<') {
        text = text.substring(0, prolog) + "\n" + text.substring(prolog);
      }
      return text;
    } catch (TransformerException e) {
      throw new IllegalStateException("Сериализация XSD " + xsd, e);
    }
  }

  private static Document parseSchema(Path xsd) throws SAXException, IOException {
    try {
      DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
      dbf.setNamespaceAware(true);
      dbf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
      var builder = dbf.newDocumentBuilder();
      // Без своего обработчика разборщик печатает «[Fatal Error]» в stderr помимо исключения.
      builder.setErrorHandler(new DefaultHandler());
      try (InputStream in = Files.newInputStream(xsd)) {
        return builder.parse(in, xsd.toUri().toString());
      }
    } catch (ParserConfigurationException e) {
      throw new IllegalStateException("DocumentBuilderFactory", e);
    }
  }

  /**
   * Ошибка сборки схемы — не ошибка проверяемого файла: в тексте файл XSD и место в нём. В схемах с подменами
   * {@link XsdPlatformShims} место ошибки сборки относится к тексту после подмен и может не совпасть с файлом.
   *
   * @param e ошибка разбора или сборки схемы
   * @param fallback файл схемы, если в ошибке его нет
   * @return исключение для ответа «схема не собирается»
   */
  private static IllegalArgumentException schemaError(SAXException e, Path fallback) {
    String where = fallback.toString();
    if (e instanceof SAXParseException parse && parse.getSystemId() != null) {
      String systemId = parse.getSystemId();
      try {
        where = Path.of(URI.create(systemId)).toString();
      } catch (IllegalArgumentException | FileSystemNotFoundException notFile) {
        where = systemId;
      }
      where += ":" + parse.getLineNumber() + ":" + parse.getColumnNumber();
    }
    return new IllegalArgumentException("Схема формата не собирается: " + where + ": " + e.getMessage(), e);
  }

  /**
   * Строит мапу {@code namespace → абсолютный путь к .xsd} в каталоге версии по соглашению имён 1С.
   * Включаются только реально существующие файлы (набор схем версии может быть уже).
   */
  static Map<String, Path> buildSchemaUriMap(Path xsdDir) {
    Map<String, Path> map = new HashMap<>();
    for (Map.Entry<String, String> e : NS_TO_XSD_FILE.entrySet()) {
      Path xsd = xsdDir.resolve(e.getValue()).normalize();
      if (Files.isRegularFile(xsd)) {
        map.put(e.getKey(), xsd);
      }
    }
    return map;
  }

  private static final class SchemaImportResolver implements LSResourceResolver {

    private final Path xsdDir;
    private final Map<String, Path> uriToFile;
    private final SchemaVersion version;
    private final DOMImplementationLS domLs;

    SchemaImportResolver(Path xsdDir, Map<String, Path> uriToFile, SchemaVersion version) {
      this.xsdDir = xsdDir;
      this.uriToFile = uriToFile;
      this.version = version;
      try {
        DOMImplementationRegistry registry = DOMImplementationRegistry.newInstance();
        this.domLs = (DOMImplementationLS) registry.getDOMImplementation("LS 3.0");
      } catch (Exception e) {
        throw new IllegalStateException("DOM LS 3.0 not available", e);
      }
    }

    @Override
    public LSInput resolveResource(String type, String namespaceURI, String publicId, String systemId, String baseURI) {
      Path resolved = null;
      if (systemId != null && !systemId.isBlank()) {
        Path p = xsdDir.resolve(systemId).normalize();
        if (Files.isRegularFile(p)) {
          resolved = p;
        }
      }
      if (resolved == null && namespaceURI != null) {
        Path p = uriToFile.get(namespaceURI);
        if (p != null && Files.isRegularFile(p)) {
          resolved = p;
        }
      }
      if (resolved == null) {
        return null;
      }
      String text;
      try {
        text = patchedSchema(resolved, version);
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      } catch (SAXException e) {
        throw schemaError(e, resolved);
      }
      LSInput input = domLs.createLSInput();
      input.setStringData(text);
      input.setSystemId(resolved.toUri().toString());
      return input;
    }
  }
}
