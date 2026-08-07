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

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Принадлежность объекта расширения: собственный он или заимствованный из расширяемой конфигурации.
 *
 * <p>Дерево метаданных строится по {@code Configuration.xml} и файлы объектов не открывает, поэтому
 * признак читается отдельно и потоково: он лежит среди первых свойств объекта, и разбирать файл
 * целиком незачем.
 */
public final class ObjectBelongingReader {

  private static final String PROPERTY = "ObjectBelonging";

  private ObjectBelongingReader() {
  }

  /**
   * Читает {@code ObjectBelonging} объекта метаданных.
   *
   * @param objectXml путь к XML объекта
   * @return значение свойства либо пусто, если объект его не объявляет или файл недоступен
   */
  public static String read(Path objectXml) {
    if (!Files.isRegularFile(objectXml)) {
      return null;
    }
    XMLInputFactory factory = new WstxInputFactory();
    factory.setProperty(XMLInputFactory.IS_NAMESPACE_AWARE, Boolean.TRUE);
    try (InputStream in = Files.newInputStream(objectXml)) {
      XMLStreamReader reader = factory.createXMLStreamReader(in);
      try {
        while (reader.hasNext()) {
          if (reader.next() != XMLStreamConstants.START_ELEMENT) {
            continue;
          }
          if (PROPERTY.equals(reader.getLocalName())) {
            String text = reader.getElementText();
            return text == null || text.isBlank() ? null : text.trim();
          }
          // Состав объекта идёт после свойств: если дошли до него, свойства кончились.
          if ("ChildObjects".equals(reader.getLocalName())) {
            return null;
          }
        }
      } finally {
        reader.close();
      }
    } catch (IOException | XMLStreamException e) {
      return null;
    }
    return null;
  }
}
