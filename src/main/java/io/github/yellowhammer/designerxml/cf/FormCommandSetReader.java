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
import java.util.ArrayList;
import java.util.List;

/**
 * Состав команд формы: {@code CommandSet/ExcludedCommand} у корня {@code Form}.
 *
 * <p>Читается потоком, а не моделью: схема объявляет {@code ExcludedCommand} одиночным узлом, а
 * конфигуратор пишет его столько раз, сколько команд исключено, и разбор моделью оставил бы одну
 * последнюю. Такой же узел есть у поля и у таблицы, поэтому берём только прямого потомка корня.
 */
final class FormCommandSetReader {

  private static final String COMMAND_SET = "CommandSet";
  private static final String EXCLUDED_COMMAND = "ExcludedCommand";

  private FormCommandSetReader() {
  }

  /**
   * @param utf8Xml байты {@code Ext/Form.xml}
   * @return имена исключённых команд в порядке файла
   */
  static List<String> excludedCommands(byte[] utf8Xml) {
    List<String> out = new ArrayList<>();
    XMLInputFactory factory = new WstxInputFactory();
    factory.setProperty(XMLInputFactory.IS_NAMESPACE_AWARE, Boolean.TRUE);
    try {
      XMLStreamReader reader = factory.createXMLStreamReader(new ByteArrayInputStream(utf8Xml));
      try {
        // Глубина: 1 - корень формы, 2 - его прямые потомки. Состав команд поля и таблицы лежит глубже.
        int depth = 0;
        while (reader.hasNext()) {
          int event = reader.next();
          if (event == XMLStreamConstants.END_ELEMENT) {
            depth -= 1;
          } else if (event == XMLStreamConstants.START_ELEMENT) {
            depth += 1;
            if (depth == 2 && COMMAND_SET.equals(reader.getLocalName())) {
              readCommandSet(reader, out);
              depth -= 1;
            }
          }
        }
      } finally {
        reader.close();
      }
    } catch (XMLStreamException e) {
      return out;
    }
    return out;
  }

  /** Читает содержимое {@code CommandSet} до его закрывающего тега. */
  private static void readCommandSet(XMLStreamReader reader, List<String> out) throws XMLStreamException {
    while (reader.hasNext()) {
      int event = reader.next();
      if (event == XMLStreamConstants.END_ELEMENT) {
        return;
      }
      if (event == XMLStreamConstants.START_ELEMENT && EXCLUDED_COMMAND.equals(reader.getLocalName())) {
        String text = reader.getElementText();
        if (text != null && !text.isBlank()) {
          out.add(text.trim());
        }
      }
    }
  }
}
