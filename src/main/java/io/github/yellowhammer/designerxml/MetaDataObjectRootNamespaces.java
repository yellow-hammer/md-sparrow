/*
 * This file is a part of md-sparrow.
 *
 * Copyright (c) 2026
 * Ivan Karlo <i.karlo@outlook.com> and contributors
 *
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package io.github.yellowhammer.designerxml;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamReader;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Снимок объявлений пространств имён на корневом {@code MetaDataObject} и слияние с результатом marshaller'а.
 * <p>
 * Конфигуратор 1С часто выводит на корень длинный список {@code xmlns:*}; JAXB при записи оставляет только
 * те URI, которые нужны сериализованному дереву. Объединение восстанавливает недостающие декларации без
 * изменения семантики XML.
 */
public final class MetaDataObjectRootNamespaces {

  private static final String MD_CLASSES_NS = "http://v8.1c.ru/8.3/MDClasses";

  private MetaDataObjectRootNamespaces() {
  }

  /**
   * Объявление {@code xmlns} или {@code xmlns:префикс} на элементе корня.
   *
   * @param prefix пустая строка или {@code null} — пространство по умолчанию
   * @param uri    URI пространства имён
   */
  public record NamespaceDecl(String prefix, String uri) {
  }

  /**
   * Снимок объявлений на стартовом теге {@code MetaDataObject} (как в файле до перезаписи).
   *
   * @param namespaces объявления в порядке появления в XML
   */
  public record Snapshot(List<NamespaceDecl> namespaces) {
  }

  /**
   * Читает объявления пространств имён на первом элементе {@code MetaDataObject} в пространстве имён MDClasses.
   *
   * @param path путь к .xml
   * @return снимок или пустой список, если корень не найден
   */
  public static Snapshot readSnapshot(Path path) throws Exception {
    List<NamespaceDecl> out = new ArrayList<>();
    XMLInputFactory f = XMLInputFactory.newInstance();
    f.setProperty(XMLInputFactory.IS_NAMESPACE_AWARE, Boolean.TRUE);
    try (InputStream in = Files.newInputStream(path)) {
      XMLStreamReader r = f.createXMLStreamReader(in);
      try {
        while (r.hasNext()) {
          int ev = r.next();
          if (ev != XMLStreamConstants.START_ELEMENT) {
            continue;
          }
          if (!"MetaDataObject".equals(r.getLocalName()) || !MD_CLASSES_NS.equals(r.getNamespaceURI())) {
            continue;
          }
          int n = r.getNamespaceCount();
          for (int i = 0; i < n; i++) {
            String prefix = r.getNamespacePrefix(i);
            if (prefix == null) {
              prefix = "";
            }
            String uri = r.getNamespaceURI(i);
            if (uri == null) {
              continue;
            }
            out.add(new NamespaceDecl(prefix, uri));
          }
          break;
        }
      } finally {
        r.close();
      }
    }
    return new Snapshot(out);
  }

  /**
   * Дополняет корень результата marshaller'а объявлениями из снимка исходного файла (объединение без перезаписи
   * конфликтующих префиксов).
   *
   * @param originalXml      файл, с которого взять объявления (до перезаписи)
   * @param marshalledUtf8   байты UTF-8 после {@link DesignerXml#marshal}
   * @param formatPretty     как в {@link WriteOptions#formatPretty()}
   * @return байты UTF-8 готового XML
   */
  public static byte[] mergeMarshalledBytes(Path originalXml, byte[] marshalledUtf8, boolean formatPretty)
    throws Exception {
    Snapshot snap = readSnapshot(originalXml);
    if (snap.namespaces().isEmpty()) {
      return marshalledUtf8;
    }
    DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
    dbf.setNamespaceAware(true);
    Document doc = dbf.newDocumentBuilder().parse(new ByteArrayInputStream(marshalledUtf8));
    Element root = doc.getDocumentElement();
    if (root == null) {
      return marshalledUtf8;
    }
    if (!needsMerge(root, snap)) {
      return marshalledUtf8;
    }
    mergeNamespaces(root, snap);
    return documentToUtf8Bytes(doc, formatPretty, marshalledUtf8);
  }

  private static boolean needsMerge(Element root, Snapshot snap) {
    for (NamespaceDecl d : snap.namespaces()) {
      String prefix = d.prefix() == null ? "" : d.prefix();
      String uri = d.uri();
      String existing = prefix.isEmpty() ? root.lookupNamespaceURI(null) : root.lookupNamespaceURI(prefix);
      if (uri.equals(existing)) {
        continue;
      }
      if (existing != null && !existing.equals(uri)) {
        continue;
      }
      return true;
    }
    return false;
  }

  private static void mergeNamespaces(Element root, Snapshot snap) {
    String xmlns = XMLConstants.XMLNS_ATTRIBUTE_NS_URI;
    for (NamespaceDecl d : snap.namespaces()) {
      String prefix = d.prefix() == null ? "" : d.prefix();
      String uri = d.uri();
      String existingUri = prefix.isEmpty() ? root.lookupNamespaceURI(null) : root.lookupNamespaceURI(prefix);
      if (uri.equals(existingUri)) {
        continue;
      }
      if (existingUri != null && !existingUri.equals(uri)) {
        continue;
      }
      if (prefix.isEmpty()) {
        root.setAttributeNS(xmlns, "xmlns", uri);
      } else {
        root.setAttributeNS(xmlns, "xmlns:" + prefix, uri);
      }
    }
  }

  private static byte[] documentToUtf8Bytes(Document doc, boolean formatPretty, byte[] marshalledUtf8)
    throws Exception {
    TransformerFactory tf = TransformerFactory.newInstance();
    Transformer t = tf.newTransformer();
    t.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
    t.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
    String head = new String(marshalledUtf8, StandardCharsets.UTF_8);
    int declEnd = head.indexOf("?>");
    String decl = declEnd > 0 ? head.substring(0, declEnd) : "";
    if (decl.contains("standalone")) {
      t.setOutputProperty(OutputKeys.STANDALONE, decl.contains("standalone=\"yes\"") ? "yes" : "no");
    } else if (doc.getXmlStandalone()) {
      t.setOutputProperty(OutputKeys.STANDALONE, "yes");
    }
    if (formatPretty) {
      t.setOutputProperty(OutputKeys.INDENT, "yes");
      t.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
    } else {
      t.setOutputProperty(OutputKeys.INDENT, "no");
    }
    ByteArrayOutputStream buf = new ByteArrayOutputStream();
    t.transform(new DOMSource(doc), new StreamResult(buf));
    return buf.toByteArray();
  }
}
