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

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import org.xml.sax.SAXException;

/**
 * Отступы для фрагмента XML без декларации: JAXB с {@link jakarta.xml.bind.Marshaller#JAXB_FRAGMENT} часто
 * не применяет {@link jakarta.xml.bind.Marshaller#JAXB_FORMATTED_OUTPUT}.
 */
public final class XmlFragmentUtf8Indent {

  private XmlFragmentUtf8Indent() {
  }

  /**
   * Переформатирует один корневой элемент UTF-8 без {@code <?xml …?>}.
   *
   * @param utf8WithoutDeclaration байты фрагмента
   * @return тот же UTF-8 с переносами и отступами
   */
  public static byte[] indent(byte[] utf8WithoutDeclaration) throws Exception {
    Document doc = parseXml(utf8WithoutDeclaration);
    TransformerFactory tf = TransformerFactory.newInstance();
    Transformer t = tf.newTransformer();
    t.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
    t.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
    t.setOutputProperty(OutputKeys.INDENT, "yes");
    t.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
    t.setOutputProperty(OutputKeys.METHOD, "xml");
    ByteArrayOutputStream buf = new ByteArrayOutputStream();
    t.transform(new DOMSource(doc), new StreamResult(buf));
    return buf.toByteArray();
  }

  private static Document parseXml(byte[] utf8) throws ParserConfigurationException, IOException, SAXException {
    DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
    dbf.setNamespaceAware(true);
    dbf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
    return dbf.newDocumentBuilder().parse(new ByteArrayInputStream(utf8));
  }
}
