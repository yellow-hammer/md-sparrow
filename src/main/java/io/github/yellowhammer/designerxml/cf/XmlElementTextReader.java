/*
 * This file is a part of md-sparrow.
 *
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package io.github.yellowhammer.designerxml.cf;

import com.ctc.wstx.stax.WstxInputFactory;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/** Текст первого элемента с заданным именем; читается потоково, файл целиком не разбирается. */
public final class XmlElementTextReader {

  private XmlElementTextReader() {
  }

  /**
   * Читает текст элемента.
   *
   * @param xml файл выгрузки
   * @param element локальное имя элемента
   * @return текст либо пусто, если элемента нет или файл недоступен
   */
  public static String read(Path xml, String element) {
    if (!Files.isRegularFile(xml)) {
      return "";
    }
    try (InputStream in = Files.newInputStream(xml)) {
      return read(in, element);
    } catch (IOException e) {
      return "";
    }
  }

  /**
   * Читает текст элемента из уже собранной разметки.
   *
   * @param xml разметка выгрузки
   * @param element локальное имя элемента
   * @return текст либо пусто, если элемента нет
   */
  public static String read(byte[] xml, String element) {
    return read(new ByteArrayInputStream(xml), element);
  }

  private static String read(InputStream in, String element) {
    XMLInputFactory factory = new WstxInputFactory();
    factory.setProperty(XMLInputFactory.IS_NAMESPACE_AWARE, Boolean.TRUE);
    try {
      XMLStreamReader reader = factory.createXMLStreamReader(in);
      try {
        while (reader.hasNext()) {
          if (reader.next() == XMLStreamConstants.START_ELEMENT
            && element.equals(reader.getLocalName())) {
            String text = reader.getElementText();
            return text == null ? "" : text.trim();
          }
        }
      } finally {
        reader.close();
      }
    } catch (XMLStreamException e) {
      return "";
    }
    return "";
  }
}
