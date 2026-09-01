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
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

/**
 * Объект метаданных в формате 1С:EDT.
 *
 * Файл объекта читается как дерево узлов: имя элемента, его атрибуты, текст и
 * вложенные элементы в порядке файла. Разбор ничего не знает про виды объектов
 * и их свойства - что из прочитанного показывать, решает схема.
 *
 * Разбор свой, а не средствами EMF: ссылки на типы EDT записывает текстом и
 * оживляет собственным реестром типов платформы, которого вне EDT нет.
 */
public final class EdtObjectReader {

  private EdtObjectReader() {
  }

  /** Узел файла объекта. */
  public record EdtNode(
      /** Имя элемента без пространства имён: {@code Catalog}, {@code attributes}, {@code name}. */
      String kind,
      /** Текст элемента. */
      String value,
      /** Атрибуты элемента: {@code uuid}, {@code xsi:type}. */
      Map<String, String> attributes,
      /** Вложенные элементы в порядке файла. */
      List<EdtNode> children) {

    /**
     * Значение вложенного элемента.
     *
     * @param kind имя элемента
     * @return текст первого такого элемента или пустая строка
     */
    public String property(String kind) {
      return children.stream()
          .filter(child -> child.kind().equals(kind))
          .map(EdtNode::value)
          .findFirst()
          .orElse("");
    }

    /**
     * Вложенные элементы одного вида.
     *
     * @param kind имя элемента
     * @return узлы в порядке файла
     */
    public List<EdtNode> list(String kind) {
      return children.stream().filter(child -> child.kind().equals(kind)).toList();
    }

    /** Имя объекта или узла. */
    public String name() {
      return property("name");
    }

    /** Идентификатор узла, если он записан. */
    public String uuid() {
      return attributes.getOrDefault("uuid", "");
    }
  }

  /**
   * Читает объект метаданных.
   *
   * @param objectMdo файл {@code <Тип>/<Имя>/<Имя>.mdo}
   * @return корневой узел: класс объекта и всё его содержимое
   * @throws IOException если файл не читается или повреждён
   */
  public static EdtNode read(Path objectMdo) throws IOException {
    XMLInputFactory factory = XMLInputFactory.newFactory();
    factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
    factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);

    try (InputStream stream = Files.newInputStream(objectMdo)) {
      XMLStreamReader reader = factory.createXMLStreamReader(stream);
      Deque<Element> open = new ArrayDeque<>();
      EdtNode root = null;

      while (reader.hasNext()) {
        switch (reader.next()) {
          case XMLStreamReader.START_ELEMENT -> open.push(element(reader));
          case XMLStreamReader.CHARACTERS, XMLStreamReader.CDATA -> {
            // Текст приходит частями: разбор по одному событию терял бы длинные значения
            if (!open.isEmpty()) {
              open.peek().text.append(reader.getText());
            }
          }
          case XMLStreamReader.END_ELEMENT -> {
            EdtNode node = open.pop().build();
            if (open.isEmpty()) {
              root = node;
            } else {
              open.peek().children.add(node);
            }
          }
          default -> {
            // Прочие события разметки к содержимому объекта не относятся
          }
        }
      }
      reader.close();

      if (root == null) {
        throw new IOException("Файл объекта пуст: " + objectMdo);
      }
      return root;
    } catch (XMLStreamException error) {
      throw new IOException("Не удалось прочитать объект: " + objectMdo, error);
    }
  }

  /** Открытый элемент: собирается, пока не встретится его закрывающий тег. */
  private static final class Element {

    private final String kind;
    private final Map<String, String> attributes;
    private final List<EdtNode> children = new ArrayList<>();
    private final StringBuilder text = new StringBuilder();

    private Element(String kind, Map<String, String> attributes) {
      this.kind = kind;
      this.attributes = attributes;
    }

    private EdtNode build() {
      return new EdtNode(kind, text.toString().trim(), Map.copyOf(attributes), List.copyOf(children));
    }
  }

  /** Начатый элемент со своими атрибутами. */
  private static Element element(XMLStreamReader reader) {
    Map<String, String> attributes = new LinkedHashMap<>();
    for (int index = 0; index < reader.getAttributeCount(); index++) {
      String prefix = reader.getAttributePrefix(index);
      String local = reader.getAttributeLocalName(index);
      String name = prefix == null || prefix.isEmpty() ? local : prefix + ":" + local;
      attributes.put(name, reader.getAttributeValue(index));
    }
    return new Element(reader.getLocalName(), attributes);
  }
}
