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

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Читает {@code Configuration/Properties/Name} (имя конфигурации или расширения).
 */
public final class ConfigurationObjectNameReader {

  private ConfigurationObjectNameReader() {
  }

  public static String readName(Path configurationXml) throws XMLStreamException, IOException {
    try (InputStream in = Files.newInputStream(configurationXml)) {
      return readName(in);
    }
  }

  public static String readName(InputStream in) throws XMLStreamException {
    XMLInputFactory f = XMLInputFactory.newInstance();
    f.setProperty(XMLInputFactory.SUPPORT_DTD, false);
    f.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
    XMLStreamReader reader = f.createXMLStreamReader(in);
    try {
      boolean inConfiguration = false;
      boolean inProperties = false;
      while (reader.hasNext()) {
        int ev = reader.next();
        if (ev == XMLStreamConstants.START_ELEMENT) {
          String ln = reader.getLocalName();
          if ("Configuration".equals(ln)) {
            inConfiguration = true;
            continue;
          }
          if (inConfiguration && "Properties".equals(ln)) {
            inProperties = true;
            continue;
          }
          if (inProperties && "Name".equals(ln)) {
            return reader.getElementText().trim();
          }
        } else if (ev == XMLStreamConstants.END_ELEMENT) {
          String ln = reader.getLocalName();
          if ("Configuration".equals(ln)) {
            break;
          }
          if ("Properties".equals(ln)) {
            inProperties = false;
          }
        }
      }
    } finally {
      reader.close();
    }
    return "";
  }
}
