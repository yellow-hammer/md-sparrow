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
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Шапка файла объекта метаданных: идентификатор, принадлежность и синоним.
 *
 * <p>Дерево метаданных строится по {@code Configuration.xml} и файлы объектов
 * целиком не читает. Всё, что ему нужно из самого объекта, лежит среди первых
 * свойств, поэтому файл сканируется потоково и до состава объекта.
 */
public final class ObjectHead {

  private static final String BELONGING = "ObjectBelonging";
  private static final String SYNONYM = "Synonym";
  private static final String CHILD_OBJECTS = "ChildObjects";
  private static final String LANGUAGE = "lang";
  private static final String CONTENT = "content";

  private ObjectHead() {
  }

  /**
   * Шапка объекта.
   *
   * @param uuid идентификатор корневого узла; пусто, если файл не разобран
   * @param objectBelonging {@code Adopted} у заимствованного объекта расширения, иначе пусто
   * @param synonym синоним по языкам: код языка -> текст, в порядке файла
   */
  public record Head(String uuid, String objectBelonging, Map<String, String> synonym) {

    /** Пустая шапка: файла нет или он не разобран. */
    public static final Head EMPTY = new Head(null, null, Map.of());
  }

  /**
   * Читает шапку объекта.
   *
   * @param objectXml путь к XML объекта
   * @return шапка; {@link Head#EMPTY}, если файл недоступен
   */
  public static Head read(Path objectXml) {
    if (!Files.isRegularFile(objectXml)) {
      return Head.EMPTY;
    }
    XMLInputFactory factory = new WstxInputFactory();
    factory.setProperty(XMLInputFactory.IS_NAMESPACE_AWARE, Boolean.TRUE);
    String uuid = null;
    String belonging = null;
    Map<String, String> synonym = new LinkedHashMap<>();
    try (InputStream in = Files.newInputStream(objectXml)) {
      XMLStreamReader reader = factory.createXMLStreamReader(in);
      try {
        while (reader.hasNext()) {
          if (reader.next() != XMLStreamConstants.START_ELEMENT) {
            continue;
          }
          String element = reader.getLocalName();
          if (uuid == null) {
            uuid = attribute(reader, "uuid");
          }
          if (BELONGING.equals(element)) {
            belonging = text(reader);
          } else if (SYNONYM.equals(element)) {
            readLocalStrings(reader, synonym);
          } else if (CHILD_OBJECTS.equals(element)) {
            // Состав объекта идёт после свойств: дальше читать нечего
            break;
          }
        }
      } finally {
        reader.close();
      }
    } catch (IOException | XMLStreamException error) {
      return new Head(uuid, belonging, Map.copyOf(synonym));
    }
    return new Head(uuid, belonging, Map.copyOf(synonym));
  }

  /**
   * Многоязычная строка: пары язык-значение внутри своего элемента.
   *
   * <p>Первый {@code Synonym} в файле принадлежит самому объекту, дальше идут
   * синонимы его узлов, поэтому чтение останавливается на закрытии элемента.
   */
  private static void readLocalStrings(XMLStreamReader reader, Map<String, String> out)
      throws XMLStreamException {
    if (!out.isEmpty()) {
      return;
    }
    String language = null;
    int depth = 0;
    while (reader.hasNext()) {
      int event = reader.next();
      if (event == XMLStreamConstants.START_ELEMENT) {
        depth++;
        String element = reader.getLocalName();
        if (LANGUAGE.equals(element)) {
          language = text(reader);
          depth--;
        } else if (CONTENT.equals(element)) {
          String value = text(reader);
          depth--;
          if (language != null && value != null) {
            out.put(language, value);
          }
          language = null;
        }
      } else if (event == XMLStreamConstants.END_ELEMENT) {
        if (depth == 0) {
          return;
        }
        depth--;
      }
    }
  }

  private static String attribute(XMLStreamReader reader, String name) {
    for (int index = 0; index < reader.getAttributeCount(); index++) {
      if (name.equals(reader.getAttributeLocalName(index))) {
        String value = reader.getAttributeValue(index);
        return value == null || value.isBlank() ? null : value.trim();
      }
    }
    return null;
  }

  private static String text(XMLStreamReader reader) throws XMLStreamException {
    String value = reader.getElementText();
    return value == null || value.isBlank() ? null : value.trim();
  }
}
