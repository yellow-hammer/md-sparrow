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
package io.github.yellowhammer.edt;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import io.github.yellowhammer.designerxml.cf.ChildObjectEntry;

/**
 * Состав конфигурации в формате 1С:EDT.
 *
 * В {@code Configuration.mdo} объекты перечислены ссылками: элемент назван по
 * виду объектов, а значением идёт полная ссылка - {@code <catalogs>Catalog.Валюты</catalogs>}.
 * Из ссылки берётся и вид объекта, и его имя, поэтому состав получается тем же,
 * что читается из выгрузки конфигуратора, и дерево метаданных собирается общей
 * группировкой.
 *
 * Ссылками записаны и свойства - роль по умолчанию, язык, форма отчёта. Отличить
 * их от состава можно только по схеме, поэтому она и спрашивается.
 *
 * Читается потоком: конфигурация большой библиотеки - файл на тысячи строк, и
 * держать его в памяти незачем.
 */
public final class EdtConfigurationReader {

  private EdtConfigurationReader() {
  }

  /** Разделитель ссылки на объект: {@code Catalog.Валюты}. */
  private static final char REFERENCE_SEPARATOR = '.';

  /**
   * Читает состав конфигурации.
   *
   * @param configurationMdo файл {@code src/Configuration/Configuration.mdo}
   * @param model метамодель EDT: по ней состав отличается от свойств
   * @return вид объекта и его имя, в порядке файла
   * @throws IOException если файл не читается или повреждён
   */
  public static List<ChildObjectEntry> listChildObjects(Path configurationMdo, EdtModel model) throws IOException {
    List<ChildObjectEntry> objects = new ArrayList<>();
    Map<String, EdtModel.Composition> composition = new LinkedHashMap<>();
    model.composition("Configuration").forEach(item -> composition.put(item.feature(), item));

    XMLInputFactory factory = XMLInputFactory.newFactory();
    factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
    factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);

    try (InputStream stream = Files.newInputStream(configurationMdo)) {
      XMLStreamReader reader = factory.createXMLStreamReader(stream);
      int depth = 0;
      EdtModel.Composition current = null;
      String inlineType = null;
      StringBuilder text = new StringBuilder();

      while (reader.hasNext()) {
        switch (reader.next()) {
          case XMLStreamReader.START_ELEMENT -> {
            depth++;
            if (depth == 2) {
              // Состав перечислен прямо в корне конфигурации
              current = composition.get(reader.getLocalName());
            }
            // У объекта внутри файла имя лежит своим элементом
            inlineType = depth == 3 && current != null && current.inline()
                && reader.getLocalName().equals("name") ? current.objectType() : null;
            text.setLength(0);
          }
          case XMLStreamReader.CHARACTERS, XMLStreamReader.CDATA -> {
            // Текст приходит частями: разбор по одному событию терял бы длинные имена
            if (current != null && (depth == 2 || inlineType != null)) {
              text.append(reader.getText());
            }
          }
          case XMLStreamReader.END_ELEMENT -> {
            String value = text.toString().trim();
            if (inlineType != null && !value.isEmpty()) {
              objects.add(new ChildObjectEntry(inlineType, value));
            } else if (depth == 2 && current != null && !current.inline()) {
              reference(value).ifPresent(objects::add);
            }
            if (depth == 2) {
              current = null;
            }
            depth--;
            inlineType = null;
            text.setLength(0);
          }
          default -> {
            // Прочие события разметки состава не касаются
          }
        }
      }
      reader.close();
    } catch (XMLStreamException error) {
      throw new IOException("Не удалось прочитать состав конфигурации: " + configurationMdo, error);
    }

    return objects;
  }

  /**
   * Разбирает ссылку на объект.
   *
   * Ссылка состоит из вида и имени: {@code Catalog.Валюты}.
   *
   * @param value значение элемента
   * @return вид и имя объекта либо пусто, если значение не ссылка
   */
  private static Optional<ChildObjectEntry> reference(String value) {
    int separator = value.indexOf(REFERENCE_SEPARATOR);
    if (separator <= 0 || separator == value.length() - 1) {
      return Optional.empty();
    }
    return Optional.of(new ChildObjectEntry(value.substring(0, separator), value.substring(separator + 1)));
  }
}
