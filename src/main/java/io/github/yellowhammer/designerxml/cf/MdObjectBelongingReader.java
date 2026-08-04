/*
 * This file is a part of md-sparrow.
 *
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package io.github.yellowhammer.designerxml.cf;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/**
 * Читает {@code ObjectBelonging} из {@code MetaDataObject/Properties}.
 */
public final class MdObjectBelongingReader {

  private MdObjectBelongingReader() {
  }

  public static String read(Path objectXml) throws XMLStreamException, IOException {
    try (InputStream in = Files.newInputStream(objectXml)) {
      return read(in);
    }
  }

  public static String read(InputStream in) throws XMLStreamException {
    XMLInputFactory f = XMLInputFactory.newInstance();
    f.setProperty(XMLInputFactory.SUPPORT_DTD, false);
    f.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
    XMLStreamReader reader = f.createXMLStreamReader(in);
    try {
      boolean inProperties = false;
      while (reader.hasNext()) {
        int ev = reader.next();
        if (ev == XMLStreamConstants.START_ELEMENT) {
          String ln = reader.getLocalName();
          if ("Properties".equals(ln)) {
            inProperties = true;
            continue;
          }
          if (inProperties && "ObjectBelonging".equals(ln)) {
            String value = reader.getElementText().trim();
            if (value.isEmpty()) {
              return null;
            }
            return value.toUpperCase(Locale.ROOT);
          }
        } else if (ev == XMLStreamConstants.END_ELEMENT) {
          String ln = reader.getLocalName();
          if ("Properties".equals(ln)) {
            return null;
          }
        }
      }
    } finally {
      reader.close();
    }
    return null;
  }
}
