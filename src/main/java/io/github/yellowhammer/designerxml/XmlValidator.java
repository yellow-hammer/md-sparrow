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
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.w3c.dom.bootstrap.DOMImplementationRegistry;
import org.w3c.dom.ls.DOMImplementationLS;
import org.w3c.dom.ls.LSInput;
import org.w3c.dom.ls.LSResourceResolver;
import org.xml.sax.SAXException;

import org.w3c.dom.Node;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.dom.DOMSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Валидация файла по корневой XSD и цепочке {@code import} через {@code catalog.xml} каталога версии.
 * <p>
 * Перед валидацией применяется учёт особенностей выгрузки платформы: если корень —
 * {@code MetaDataObject} с {@code version}, а у дочернего {@code Configuration} нет
 * {@code formatVersion}, подставляется то же значение (в XSD {@code formatVersion} обязателен,
 * в XML с диска атрибут часто отсутствует).
 * <p>
 * При разборе {@code v8.1c.ru-8.3-MDClasses.xsd} в памяти у типа {@code ConfigurationProperties}
 * у вложенного {@code xs:choice} задаётся {@code maxOccurs="unbounded"}: в поставляемой схеме
 * стоит одиночный {@code choice} (ровно один дочерний элемент), что не соответствует последовательности
 * свойств ({@code Name}, {@code Synonym}, …) в выгрузке платформы. Файлы на диске не меняются.
 * <p>
 * Зависимые XSD (перечисления, ссылки на объекты, мобильные возможности) подменяются в
 * {@link CatalogLsResourceResolver} — см. {@link #applyPlatformShimsToImportedSchema}.
 */
public final class XmlValidator {

  static final String NS_MD_CLASSES = "http://v8.1c.ru/8.3/MDClasses";

  private XmlValidator() {
  }

  /**
   * Проверка файла по главной XSD и импорту через catalog по умолчанию в каталоге схем.
   *
   * @param xmlPath путь к {@code .xml}
   * @param version версия (подкаталог под {@code xsdCollectionRoot})
   * @param xsdCollectionRoot корень коллекции XSD (обычно submodule {@code namespace-forest})
   * @throws SAXException ошибка схемы или несоответствие документа
   * @throws IOException ошибки чтения файлов или catalog
   */
  public static void validate(Path xmlPath, SchemaVersion version, Path xsdCollectionRoot) throws SAXException, IOException {
    validate(xmlPath, version, xsdCollectionRoot, null);
  }

  /**
   * Проверка файла по главной XSD с явным {@code catalog.xml}.
   *
   * @param xmlPath путь к {@code .xml}
   * @param version версия схем
   * @param xsdCollectionRoot корень коллекции XSD
   * @param catalogFile если {@code null}, используется {@code <xsd-каталог>/catalog.xml}; иначе OASIS catalog
   *                    (в этом репозитории — {@code xjb/ns/&lt;версия&gt;/catalog.xml}, если схемы без catalog в submodule).
   * @throws SAXException ошибка схемы или несоответствие документа
   * @throws IOException ошибки чтения файлов или catalog
   */
  public static void validate(Path xmlPath, SchemaVersion version, Path xsdCollectionRoot, Path catalogFile)
    throws SAXException, IOException {
    if (!Files.isRegularFile(xmlPath)) {
      throw new IllegalArgumentException(
        "Файл XML не найден или не обычный файл: "
          + xmlPath.toAbsolutePath()
          + ". Укажите реальный путь к .xml (в README path/to.xml — только шаблон).");
    }
    Path xsdDir = xsdCollectionRoot.resolve(version.xsdDirectoryName()).normalize();
    Path catalogPath = catalogFile != null ? catalogFile.normalize() : xsdDir.resolve("catalog.xml");
    Path mainXsd = xsdDir.resolve("v8.1c.ru-8.3-MDClasses.xsd");
    if (!Files.isRegularFile(mainXsd)) {
      throw new IllegalArgumentException("XSD not found: " + mainXsd);
    }
    if (!Files.isRegularFile(catalogPath)) {
      throw new IllegalArgumentException("catalog.xml not found: " + catalogPath);
    }
    Map<String, Path> uriToFile = loadCatalogUriMap(catalogPath, xsdDir);
    LSResourceResolver lsResolver = new CatalogLsResourceResolver(xsdDir, uriToFile);

    SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
    factory.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
    factory.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
    factory.setResourceResolver(lsResolver);

    String mdClassesRaw = stripUtf8Bom(Files.readString(mainXsd, StandardCharsets.UTF_8));
    String mdClassesPatched = patchConfigurationPropertiesChoiceUnbounded(mdClassesRaw);
    // Временный файл в каталоге версии схем — относительные schemaLocation в import.
    Path patchedSchema = Files.createTempFile(mainXsd.getParent(), "mdclasses-patched-", ".xsd");
    try {
      Files.writeString(patchedSchema, mdClassesPatched, StandardCharsets.UTF_8);
      Schema schema = factory.newSchema(patchedSchema.toFile());
      Document doc = parseXmlForValidation(xmlPath);
      applyConfigurationFormatVersionFromMetaDataObject(doc);

      Validator validator = schema.newValidator();
      validator.validate(new DOMSource(doc));
    } finally {
      Files.deleteIfExists(patchedSchema);
    }
  }

  static String stripUtf8Bom(String s) {
    if (s.isEmpty()) {
      return s;
    }
    if (s.charAt(0) == '\uFEFF') {
      return s.substring(1);
    }
    return s;
  }

  /** Патчи для зависимых файлов при разборе схемы (только в памяти). */
  static String applyPlatformShimsToImportedSchema(String xsdFileName, String raw) {
    return switch (xsdFileName) {
      case "v8.1c.ru-8.3-xcf-enums.xsd" -> patchXcfEnumsCompatibilityMode(raw);
      case "v8.1c.ru-8.3-xcf-readable.xsd" -> patchXcfReadableReferenceType(raw);
      case "v8.1c.ru-8.2-managed-application-core.xsd" -> patchManagedCoreMobileFunctionalities(raw);
      default -> raw;
    };
  }

  static String patchXcfEnumsCompatibilityMode(String xsd) {
    final String typeMarker = "<xs:simpleType name=\"CompatibilityMode\">";
    int block = xsd.indexOf(typeMarker);
    if (block < 0) {
      return xsd;
    }
    int blockEnd = xsd.indexOf("</xs:simpleType>", block);
    if (blockEnd < 0) {
      return xsd;
    }
    String slice = xsd.substring(block, blockEnd);
    if (slice.contains("Version8_3_27")) {
      return xsd;
    }
    final String needle = "<xs:enumeration value=\"Version8_3_12\"/>";
    int ins = xsd.indexOf(needle, block);
    if (ins < 0 || ins > blockEnd) {
      return xsd;
    }
    String tail = "\n\t\t\t<xs:enumeration value=\"Version8_3_27\"/>\n\t\t\t<xs:enumeration value=\"Version8_3_26\"/>\n"
      + "\t\t\t<xs:enumeration value=\"Version8_3_25\"/>\n\t\t\t<xs:enumeration value=\"Version8_3_24\"/>\n"
      + "\t\t\t<xs:enumeration value=\"Version8_3_23\"/>\n\t\t\t<xs:enumeration value=\"Version8_3_22\"/>\n"
      + "\t\t\t<xs:enumeration value=\"Version8_3_21\"/>\n\t\t\t<xs:enumeration value=\"Version8_3_20\"/>\n"
      + "\t\t\t<xs:enumeration value=\"Version8_3_19\"/>\n\t\t\t<xs:enumeration value=\"Version8_3_18\"/>\n"
      + "\t\t\t<xs:enumeration value=\"Version8_3_17\"/>\n\t\t\t<xs:enumeration value=\"Version8_3_16\"/>\n"
      + "\t\t\t<xs:enumeration value=\"Version8_3_15\"/>\n\t\t\t<xs:enumeration value=\"Version8_3_14\"/>\n"
      + "\t\t\t<xs:enumeration value=\"Version8_3_13\"/>\n";
    return xsd.substring(0, ins + needle.length()) + tail + xsd.substring(ins + needle.length());
  }

  static String patchXcfReadableReferenceType(String xsd) {
    final String open = "<xs:simpleType name=\"ReferenceType\">";
    int s = xsd.indexOf(open);
    if (s < 0) {
      return xsd;
    }
    int end = xsd.indexOf("</xs:simpleType>", s);
    if (end < 0) {
      return xsd;
    }
    int endInclusive = end + "</xs:simpleType>".length();
    String block = xsd.substring(s, endInclusive);
    if (!block.contains("<xs:pattern")) {
      return xsd;
    }
    String repl = open + "\n\t\t<xs:restriction base=\"xs:string\"/>\n\t</xs:simpleType>";
    return xsd.substring(0, s) + repl + xsd.substring(endInclusive);
  }

  static String patchManagedCoreMobileFunctionalities(String xsd) {
    final String typeMarker = "<xs:simpleType name=\"MobileApplicationFunctionalities\">";
    int block = xsd.indexOf(typeMarker);
    if (block < 0) {
      return xsd;
    }
    int blockEnd = xsd.indexOf("</xs:simpleType>", block);
    if (blockEnd < 0) {
      return xsd;
    }
    String slice = xsd.substring(block, blockEnd);
    if (slice.contains("SpeechToText")) {
      return xsd;
    }
    final String needle = "<xs:enumeration value=\"DocumentScanning\"/>";
    int ins = xsd.indexOf(needle, block);
    if (ins < 0 || ins > blockEnd) {
      return xsd;
    }
    String add = "\n\t\t\t<xs:enumeration value=\"SpeechToText\"/>\n\t\t\t<xs:enumeration value=\"TextToSpeech\"/>";
    return xsd.substring(0, ins + needle.length()) + add + xsd.substring(ins + needle.length());
  }

  /**
   * Ослабляет {@code ConfigurationProperties}: повторяющийся {@code choice}, как в XML платформы.
   */
  static String patchConfigurationPropertiesChoiceUnbounded(String mdClassesXsd) {
    final String typeMarker = "<xs:complexType name=\"ConfigurationProperties\">";
    int block = mdClassesXsd.indexOf(typeMarker);
    if (block < 0) {
      return mdClassesXsd;
    }
    int blockEnd = mdClassesXsd.indexOf("</xs:complexType>", block);
    if (blockEnd < 0) {
      return mdClassesXsd;
    }
    int choice = mdClassesXsd.indexOf("<xs:choice>", block);
    if (choice < 0 || choice > blockEnd) {
      return mdClassesXsd;
    }
    int windowEnd = Math.min(choice + 48, mdClassesXsd.length());
    if (mdClassesXsd.substring(choice, windowEnd).contains("maxOccurs=\"unbounded\"")) {
      return mdClassesXsd;
    }
    final String openChoice = "<xs:choice>";
    return mdClassesXsd.substring(0, choice)
      + "<xs:choice maxOccurs=\"unbounded\">"
      + mdClassesXsd.substring(choice + openChoice.length());
  }

  static Document parseXmlForValidation(Path xmlPath) throws IOException, SAXException {
    try {
      DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
      dbf.setNamespaceAware(true);
      dbf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
      try (InputStream in = Files.newInputStream(xmlPath)) {
        return dbf.newDocumentBuilder().parse(in);
      }
    } catch (ParserConfigurationException e) {
      throw new IllegalStateException("DocumentBuilderFactory", e);
    }
  }

  /**
   * Дополняет {@code Configuration/@formatVersion} из {@code MetaDataObject/@version}, если атрибут отсутствует.
   */
  static void applyConfigurationFormatVersionFromMetaDataObject(Document document) {
    Element root = document.getDocumentElement();
    if (root == null) {
      return;
    }
    if (!NS_MD_CLASSES.equals(root.getNamespaceURI()) || !"MetaDataObject".equals(root.getLocalName())) {
      return;
    }
    String rawMetaVersion = root.getAttribute("version");
    if (rawMetaVersion == null || rawMetaVersion.isBlank()) {
      return;
    }
    String metaVersion = rawMetaVersion.trim();
    NodeList children = root.getChildNodes();
    for (int i = 0; i < children.getLength(); i++) {
      Node n = children.item(i);
      if (!(n instanceof Element child)) {
        continue;
      }
      if (!NS_MD_CLASSES.equals(child.getNamespaceURI()) || !"Configuration".equals(child.getLocalName())) {
        continue;
      }
      if (!child.hasAttribute("formatVersion")) {
        child.setAttribute("formatVersion", metaVersion);
      }
    }
  }

  /**
   * Читает OASIS catalog (элементы {@code uri}) в мапу namespace → абсолютный путь к .xsd.
   */
  static Map<String, Path> loadCatalogUriMap(Path catalogFile, Path xsdDir) throws IOException {
    Map<String, Path> map = new HashMap<>();
    try {
      DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
      dbf.setNamespaceAware(true);
      dbf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
      Document doc = dbf.newDocumentBuilder().parse(catalogFile.toFile());
      NodeList nodes = doc.getElementsByTagNameNS("urn:oasis:names:tc:entity:xmlns:xml:catalog", "uri");
      if (nodes.getLength() == 0) {
        nodes = doc.getElementsByTagName("uri");
      }
      for (int i = 0; i < nodes.getLength(); i++) {
        if (!(nodes.item(i) instanceof Element el)) {
          continue;
        }
        String name = el.getAttribute("name");
        String uri = el.getAttribute("uri");
        if (name.isEmpty() || uri.isEmpty()) {
          continue;
        }
        map.put(name, xsdDir.resolve(uri).normalize());
      }
    } catch (Exception e) {
      throw new IOException("Failed to parse catalog: " + catalogFile, e);
    }
    return map;
  }

  private static final class CatalogLsResourceResolver implements LSResourceResolver {

    private final Path xsdDir;
    private final Map<String, Path> uriToFile;
    private final DOMImplementationLS domLs;

    CatalogLsResourceResolver(Path xsdDir, Map<String, Path> uriToFile) {
      this.xsdDir = xsdDir;
      this.uriToFile = uriToFile;
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
      try {
        String raw = stripUtf8Bom(Files.readString(resolved, StandardCharsets.UTF_8));
        String text = applyPlatformShimsToImportedSchema(resolved.getFileName().toString(), raw);
        InputStream in = new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8));
        LSInput input = domLs.createLSInput();
        input.setByteStream(in);
        input.setSystemId(resolved.toUri().toString());
        return input;
      } catch (IOException e) {
        return null;
      }
    }
  }
}
