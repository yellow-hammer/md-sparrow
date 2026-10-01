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

import org.xml.sax.SAXParseException;

import javax.xml.stream.Location;
import javax.xml.stream.XMLStreamException;

import java.io.IOException;

/**
 * Отказ чтения файла исходников: первая строка текста - что не прочитано, вторая - файл и место
 * ошибки разбора, если разборщик его сообщил.
 *
 * <p>Место ищется по всей цепочке причин: JAXB передаёт его вложенным исключением SAX, а своего
 * текста у исключения JAXB может не быть вовсе.
 */
public final class ReadFailure {

  private ReadFailure() {
  }

  /**
   * Отказ чтения файла.
   *
   * @param summary что не прочитано, без путей
   * @param file путь к файлу для подробностей
   * @param error исключение чтения или разбора
   * @return отказ с исходным исключением в причине
   */
  public static IOException of(String summary, String file, Throwable error) {
    String place = place(error);
    return new IOException(summary + "\n" + file + (place.isEmpty() ? "" : ": " + place), error);
  }

  /** Строка, столбец и причина от разборщика; пусто, если места в цепочке нет. */
  private static String place(Throwable error) {
    for (Throwable cause = error; cause != null; cause = cause.getCause()) {
      if (cause instanceof SAXParseException parse && parse.getLineNumber() > 0) {
        return place(parse.getLineNumber(), parse.getColumnNumber(), parse.getMessage());
      }
      if (cause instanceof XMLStreamException stream
          && stream.getLocation() != null
          && stream.getLocation().getLineNumber() > 0) {
        Location at = stream.getLocation();
        return place(at.getLineNumber(), at.getColumnNumber(), firstLine(stream.getMessage()));
      }
    }
    return "";
  }

  private static String place(int line, int column, String reason) {
    StringBuilder text = new StringBuilder("строка ").append(line);
    if (column > 0) {
      text.append(", столбец ").append(column);
    }
    if (reason != null && !reason.isBlank()) {
      text.append(": ").append(reason.strip());
    }
    return text.toString();
  }

  /** Woodstox дописывает место к причине со следующей строки, а место уже взято из исключения. */
  private static String firstLine(String message) {
    if (message == null) {
      return "";
    }
    int newline = message.indexOf('\n');
    return newline < 0 ? message : message.substring(0, newline);
  }
}
