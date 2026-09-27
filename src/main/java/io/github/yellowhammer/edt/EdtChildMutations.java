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

import static io.github.yellowhammer.edt.EdtXmlText.escape;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.stream.XMLStreamException;

import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EStructuralFeature;

import io.github.yellowhammer.designerxml.cf.ConfigurationLanguage;
import io.github.yellowhammer.edt.EdtObjectRegions.Region;

/**
 * Правка состава объекта 1С:EDT.
 *
 * Реквизиты, табличные части, измерения и прочие узлы правятся точечно: узел
 * добавляется, переименовывается, копируется или вырезается целыми строками, а
 * остальной файл не трогается.
 *
 * Что бывает у вида объекта своим, знает схема: списка видов узлов здесь нет.
 */
public final class EdtChildMutations {

  /** Отступ уровня в файлах EDT. */
  private static final String INDENT = "  ";

  /** Порождаемые типы: 1С:EDT пишет их первыми, где бы они ни стояли в схеме. */
  private static final String PRODUCED_TYPES = "producedTypes";

  /** Пространства имён, на которые ссылаются значения в эталонах узлов. */
  private static final java.util.Map<String, String> NAMESPACES = java.util.Map.of(
      "xsi", "http://www.w3.org/2001/XMLSchema-instance",
      "core", "http://g5.1c.ru/v8/dt/mcore");
  private static final String XSI = "xsi";
  private static final String XMLNS = "xmlns:";

  /** Префикс атрибута ({@code xsi:type=}) и префикс типа в его значении ({@code "core:UndefinedValue"}). */
  private static final Pattern USED_PREFIX = Pattern.compile("(\\w+):\\w+=\"(?:(\\w+):)?");

  /** Корневой элемент файла объекта со всеми атрибутами. */
  private static final Pattern ROOT_TAG = Pattern.compile("<(\\w+:\\w+)((?:\\s+[\\w:]+=\"[^\"]*\")*)\\s*>");
  private static final Pattern ATTRIBUTE = Pattern.compile("\\s+([\\w:]+)=\"([^\"]*)\"");

  private EdtChildMutations() {
  }

  /**
   * Добавляет узел.
   *
   * @param objectMdo файл объекта
   * @param model метамодель EDT
   * @param feature вид узла: {@code attributes}, {@code tabularSections}
   * @param name имя нового узла
   * @throws IOException если файл не читается или не пишется
   */
  public static void add(Path objectMdo, EdtModel model, String feature, String name) throws IOException {
    addNested(objectMdo, model, null, null, feature, name);
  }

  /**
   * Добавляет узел внутрь другого узла.
   *
   * @param objectMdo файл объекта
   * @param model метамодель EDT
   * @param owner вид узла-владельца или {@code null}, если узел добавляется объекту
   * @param ownerName имя узла-владельца
   * @param feature вид нового узла
   * @param name имя нового узла
   * @throws IOException если файл не читается или не пишется
   */
  public static void addNested(
      Path objectMdo,
      EdtModel model,
      String owner,
      String ownerName,
      String feature,
      String name) throws IOException {
    requireName(name);
    editAll(objectMdo, xml -> {
      Region parent = owner == null ? null : requireChild(xml, null, owner, ownerName);
      List<Region> siblings = children(xml, parent, feature);
      if (EdtObjectRegions.names(xml, siblings).contains(name)) {
        throw new IllegalArgumentException("Узел уже есть: " + name);
      }

      // Класс узла записан не в файле, а в схеме владельца: у табличной части
      // справочника это CatalogTabularSection, а её реквизит - свой класс
      String objectClass = objectKind(xml);
      String ownerClass = owner == null ? objectClass : kindOf(model, objectClass, owner);
      String kind = kindOf(model, ownerClass, feature);
      String indent = parent == null ? INDENT : INDENT + INDENT;
      String seed = EdtObjectScaffold.seed("node|" + ownerName + "|" + kind + "|" + name, xml);
      String node = EdtNodeGolden.forProject(
          EdtNodeGolden.node(kind, name, ConfigurationLanguage.current(), indent, eol(xml), seed),
          objectMdo, objectClass, owner, feature);
      int at = insertionPoint(xml, model, parent, ownerClass, siblings, feature);
      List<Edit> edits = new ArrayList<>();
      edits.add(new Edit(at, at, node + eol(xml)));
      // Значения вроде <minValue xsi:type="core:UndefinedValue"/> требуют своих
      // пространств имён в корне файла
      Edit namespaces = declareNamespaces(xml, node);
      if (namespaces != null) {
        edits.add(namespaces);
      }
      return edits;
    });
  }

  /**
   * Переименовывает узел.
   *
   * @param objectMdo файл объекта
   * @param owner вид узла-владельца или {@code null}
   * @param ownerName имя узла-владельца
   * @param feature вид узла
   * @param oldName текущее имя
   * @param newName новое имя
   * @throws IOException если файл не читается или не пишется
   */
  public static void rename(
      Path objectMdo,
      String owner,
      String ownerName,
      String feature,
      String oldName,
      String newName) throws IOException {
    requireName(newName);
    edit(objectMdo, xml -> {
      Region parent = owner == null ? null : requireChild(xml, null, owner, ownerName);
      Region node = requireChild(xml, parent, feature, oldName);
      if (EdtObjectRegions.names(xml, children(xml, parent, feature)).contains(newName)) {
        throw new IllegalArgumentException("Узел уже есть: " + newName);
      }
      Region name = EdtObjectRegions.nameRegion(xml, node);
      if (!name.found()) {
        throw new IllegalArgumentException("У узла нет имени: " + oldName);
      }
      return new Edit(name.start(), name.end(), "<name>" + escape(newName) + "</name>");
    });
  }

  /**
   * Удаляет узел.
   *
   * @param objectMdo файл объекта
   * @param owner вид узла-владельца или {@code null}
   * @param ownerName имя узла-владельца
   * @param feature вид узла
   * @param name имя узла
   * @throws IOException если файл не читается или не пишется
   */
  public static void delete(Path objectMdo, String owner, String ownerName, String feature, String name)
      throws IOException {
    edit(objectMdo, xml -> {
      Region parent = owner == null ? null : requireChild(xml, null, owner, ownerName);
      Region node = requireChild(xml, parent, feature, name);
      // Узел вырезается целыми строками, вместе со своим отступом
      int start = EdtObjectRegions.lineStart(xml, node.start());
      int end = lineEnd(xml, node.end());
      return new Edit(start, end, "");
    });
  }

  /**
   * Копирует узел под новым именем.
   *
   * @param objectMdo файл объекта
   * @param owner вид узла-владельца или {@code null}
   * @param ownerName имя узла-владельца
   * @param feature вид узла
   * @param sourceName имя копируемого узла
   * @param newName имя копии
   * @throws IOException если файл не читается или не пишется
   */
  public static void duplicate(
      Path objectMdo,
      String owner,
      String ownerName,
      String feature,
      String sourceName,
      String newName) throws IOException {
    requireName(newName);
    edit(objectMdo, xml -> {
      Region parent = owner == null ? null : requireChild(xml, null, owner, ownerName);
      List<Region> siblings = children(xml, parent, feature);
      if (EdtObjectRegions.names(xml, siblings).contains(newName)) {
        throw new IllegalArgumentException("Узел уже есть: " + newName);
      }
      Region source = requireChild(xml, parent, feature, sourceName);
      Region name = EdtObjectRegions.nameRegion(xml, source);

      // Идентификаторы у копии свои все: и у самого узла, и у его порождаемых
      // типов, и у вложенных реквизитов - по ним платформа отличает узлы и типы
      String copy = EdtObjectScaffold.freshUuids(xml.substring(source.start(), source.end()),
          EdtObjectScaffold.seed("duplicate|" + ownerName + "|" + feature + "|" + sourceName + "|" + newName, xml));
      String renamed = name.found()
          ? copy.substring(0, name.start() - source.start())
              + "<name>" + escape(newName) + "</name>"
              + copy.substring(name.end() - source.start())
          : copy;

      int at = lineEnd(xml, source.end());
      String indent = xml.substring(EdtObjectRegions.lineStart(xml, source.start()), source.start());
      return new Edit(at, at, indent + renamed + eol(xml));
    });
  }

  /**
   * Переставляет узлы в заданном порядке.
   *
   * @param objectMdo файл объекта
   * @param owner вид узла-владельца или {@code null}
   * @param ownerName имя узла-владельца
   * @param feature вид узла
   * @param order имена узлов в нужном порядке
   * @throws IOException если файл не читается или не пишется
   */
  public static void reorder(
      Path objectMdo,
      String owner,
      String ownerName,
      String feature,
      List<String> order) throws IOException {
    editAll(objectMdo, xml -> {
      Region parent = owner == null ? null : requireChild(xml, null, owner, ownerName);
      List<Region> nodes = children(xml, parent, feature);
      List<String> names = EdtObjectRegions.names(xml, nodes);
      if (!new java.util.TreeSet<>(names).equals(new java.util.TreeSet<>(order))) {
        throw new IllegalArgumentException("Порядок задан не для всех узлов: " + feature);
      }

      // Узлы переписываются на местах друг друга: между ними может стоять что
      // угодно, и трогать это незачем
      List<Edit> edits = new ArrayList<>();
      for (int index = 0; index < nodes.size(); index++) {
        Region place = nodes.get(index);
        Region taken = nodes.get(names.indexOf(order.get(index)));
        edits.add(new Edit(place.start(), place.end(), xml.substring(taken.start(), taken.end())));
      }
      return edits;
    });
  }

  /** Правка файла: одна замена. */
  private interface SingleEdit {
    Edit apply(String xml) throws XMLStreamException, IOException;
  }

  /** Правка файла: несколько замен. */
  private interface MultiEdit {
    List<Edit> apply(String xml) throws XMLStreamException, IOException;
  }

  /** Замена участка файла. */
  private record Edit(int start, int end, String text) {
  }

  private static void edit(Path objectMdo, SingleEdit change) throws IOException {
    editAll(objectMdo, xml -> List.of(change.apply(xml)));
  }

  private static void editAll(Path objectMdo, MultiEdit change) throws IOException {
    try {
      ConfigurationLanguage.with(objectMdo, () -> {
        editAllInLanguage(objectMdo, change);
        return null;
      });
    } catch (jakarta.xml.bind.JAXBException error) {
      throw new IOException(error);
    }
  }

  private static void editAllInLanguage(Path objectMdo, MultiEdit change) throws IOException {
    if (!Files.isRegularFile(objectMdo)) {
      throw new IllegalArgumentException("Файл объекта не найден: " + objectMdo);
    }
    String xml = Files.readString(objectMdo, StandardCharsets.UTF_8);
    List<Edit> edits;
    try {
      edits = change.apply(xml);
    } catch (XMLStreamException error) {
      throw new IOException("Не удалось разобрать файл объекта: " + objectMdo, error);
    }

    List<Edit> ordered = new ArrayList<>(edits);
    ordered.sort((left, right) -> Integer.compare(right.start(), left.start()));
    StringBuilder text = new StringBuilder(xml);
    for (Edit edit : ordered) {
      text.replace(edit.start(), edit.end(), edit.text());
    }
    Files.writeString(objectMdo, text.toString(), StandardCharsets.UTF_8);
  }

  /** Узлы вида внутри объекта или внутри узла-владельца. */
  private static List<Region> children(String xml, Region parent, String feature) throws XMLStreamException {
    return parent == null
        ? EdtObjectRegions.properties(xml, feature)
        : EdtObjectRegions.nested(xml, parent, feature);
  }

  private static Region requireChild(String xml, Region parent, String feature, String name)
      throws XMLStreamException {
    Region region = EdtObjectRegions.byName(xml, children(xml, parent, feature), name);
    if (!region.found()) {
      throw new IllegalArgumentException("Узел не найден: " + name);
    }
    return region;
  }

  /** Куда встаёт новый узел: за последним таким же, иначе по порядку схемы. */
  private static int insertionPoint(
      String xml,
      EdtModel model,
      Region parent,
      String ownerClass,
      List<Region> siblings,
      String feature) throws XMLStreamException {
    if (!siblings.isEmpty()) {
      return lineEnd(xml, siblings.get(siblings.size() - 1).end());
    }
    List<String> order = order(model, ownerClass);
    if (parent == null) {
      return EdtObjectRegions.insertionPoint(xml, order, feature);
    }
    // Первый узел внутри владельца встаёт перед первым свойством, которое схема
    // ставит после него: у табличной части это длина номера строки
    int place = order.indexOf(feature);
    for (EdtObjectRegions.Child child : EdtObjectRegions.children(xml, parent)) {
      if (place >= 0 && order.indexOf(child.name()) > place) {
        return EdtObjectRegions.lineStart(xml, child.region().start());
      }
    }
    return EdtObjectRegions.lineStart(xml, parent.end());
  }

  /** Порядок свойств класса, как его пишет 1С:EDT: порождаемые типы первыми, дальше по схеме. */
  private static List<String> order(EdtModel model, String className) {
    List<String> order = new ArrayList<>();
    order.add(PRODUCED_TYPES);
    EClass eClass = model.classOf(className);
    if (eClass != null) {
      for (EStructuralFeature item : eClass.getEAllStructuralFeatures()) {
        if (!order.contains(item.getName())) {
          order.add(item.getName());
        }
      }
    }
    return order;
  }

  /**
   * Объявления пространств имён, которых не хватает узлу, в корне файла.
   *
   * 1С:EDT пишет их в корне и только те, что в файле встречаются: сначала
   * {@code xsi}, остальные по алфавиту. Остальные атрибуты корня не трогаются.
   *
   * @return правка корня либо {@code null}, если всё уже объявлено
   */
  private static Edit declareNamespaces(String xml, String node) {
    java.util.Set<String> used = new java.util.TreeSet<>();
    Matcher prefixes = USED_PREFIX.matcher(node);
    while (prefixes.find()) {
      used.add(prefixes.group(1));
      if (prefixes.group(2) != null) {
        used.add(prefixes.group(2));
      }
    }
    if (used.isEmpty()) {
      return null;
    }
    Matcher root = ROOT_TAG.matcher(xml);
    if (!root.find()) {
      throw new IllegalArgumentException("Не найден корневой узел объекта");
    }
    java.util.Map<String, String> declared = new java.util.LinkedHashMap<>();
    StringBuilder others = new StringBuilder();
    Matcher attributes = ATTRIBUTE.matcher(root.group(2));
    while (attributes.find()) {
      if (attributes.group(1).startsWith(XMLNS)) {
        declared.put(attributes.group(1).substring(XMLNS.length()), attributes.group(2));
      } else {
        others.append(attributes.group());
      }
    }
    boolean missing = false;
    for (String prefix : used) {
      if (!declared.containsKey(prefix)) {
        String uri = NAMESPACES.get(prefix);
        if (uri == null) {
          throw new IllegalStateException("Эталон узла ссылается на неизвестное пространство имён " + prefix);
        }
        declared.put(prefix, uri);
        missing = true;
      }
    }
    if (!missing) {
      return null;
    }
    List<String> sorted = new ArrayList<>(declared.keySet());
    sorted.sort((left, right) -> XSI.equals(left) ? -1 : XSI.equals(right) ? 1 : left.compareTo(right));
    StringBuilder tag = new StringBuilder("<").append(root.group(1));
    for (String prefix : sorted) {
      tag.append(" xmlns:").append(prefix).append("=\"").append(declared.get(prefix)).append('"');
    }
    tag.append(others).append('>');
    return new Edit(root.start(), root.end(), tag.toString());
  }

  /** Класс объекта: корневой тег файла без пространства имён. */
  private static String objectKind(String xml) {
    int open = xml.indexOf('<', xml.indexOf("?>") + 2);
    int space = open < 0 ? -1 : firstOf(xml, open, ' ', '>');
    String tag = open < 0 || space < 0 ? "" : xml.substring(open + 1, space);
    int colon = tag.indexOf(':');
    return colon < 0 ? tag : tag.substring(colon + 1);
  }

  private static int firstOf(String xml, int from, char first, char second) {
    int one = xml.indexOf(first, from);
    int two = xml.indexOf(second, from);
    if (one < 0) {
      return two;
    }
    return two < 0 ? one : Math.min(one, two);
  }

  /** Класс узла по схеме владельца: реквизит справочника метамодель зовёт CatalogAttribute. */
  private static String kindOf(EdtModel model, String owner, String feature) {
    for (EdtModel.Composition item : model.composition(owner)) {
      if (item.feature().equals(feature)) {
        return item.objectType();
      }
    }
    throw new IllegalArgumentException("У вида " + owner + " нет узлов " + feature);
  }

  /** Конец строки, в которой кончается участок. */
  private static int lineEnd(String xml, int end) {
    int line = xml.indexOf('\n', end);
    return line < 0 ? xml.length() : line + 1;
  }

  private static String eol(String xml) {
    return xml.contains("\r\n") ? "\r\n" : "\n";
  }

  private static void requireName(String name) {
    if (name == null || name.isBlank()) {
      throw new IllegalArgumentException("Введите имя узла.");
    }
  }
}
