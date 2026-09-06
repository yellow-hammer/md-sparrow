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
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import javax.xml.stream.XMLStreamException;

import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EEnum;
import org.eclipse.emf.ecore.EEnumLiteral;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.emf.ecore.EStructuralFeature;

import io.github.yellowhammer.designerxml.cf.FormItemPropertyChangeDto;
import io.github.yellowhammer.edt.EdtObjectReader.EdtNode;
import io.github.yellowhammer.edt.EdtObjectRegions.Region;

/**
 * Точечная правка свойств элементов управляемой формы 1С:EDT.
 *
 * Панель присылает свойство в записи конфигуратора: имя из словаря выгрузки и
 * значение в его литералах. Здесь оно переводится в свойство метамодели EDT и
 * встаёт в файл формы одной строкой: либо на место прежней записи, либо на
 * своё место по порядку схемы. Свойства вида элемента лежат в его описании
 * {@code extInfo}, и туда же пишутся.
 */
public final class EdtFormItemPropertyEdit {

  private static final String FORM = "http://g5.1c.ru/v8/dt/form";
  private static final String TYPE_PREFIX = "form:";
  private static final String LOCAL_STRING = "LocalStringMapEntry";
  private static final String INDENT = "  ";

  private EdtFormItemPropertyEdit() {
  }

  /**
   * Применяет правки к файлу формы.
   *
   * @param formFile файл {@code Form.form}
   * @param model метамодель EDT
   * @param changes элемент, свойство и значение; пустое значение убирает запись свойства
   * @throws IOException если файл не читается или не пишется
   */
  public static void apply(Path formFile, EdtModel model, List<FormItemPropertyChangeDto> changes)
      throws IOException {
    if (!Files.isRegularFile(formFile)) {
      throw new IllegalArgumentException("Файл формы не найден: " + formFile);
    }
    if (changes == null || changes.isEmpty()) {
      return;
    }
    String xml = Files.readString(formFile, StandardCharsets.UTF_8);
    EdtNode form = EdtObjectReader.parse(xml);
    EPackage formPackage = model.packageOf(FORM);
    if (formPackage == null) {
      throw new IllegalStateException("В сборке нет метамодели формы EDT.");
    }
    List<Edit> edits = new ArrayList<>();
    try {
      for (FormItemPropertyChangeDto change : changes) {
        Edit edit = edit(xml, form, formPackage, model, change);
        if (edit != null) {
          edits.add(edit);
        }
      }
    } catch (XMLStreamException error) {
      throw new IOException("Не удалось разобрать форму: " + formFile, error);
    }
    if (edits.isEmpty()) {
      return;
    }
    String patched = patched(xml, edits);
    verify(patched, changes);
    Files.writeString(formFile, patched, StandardCharsets.UTF_8);
  }

  /** Правка участка файла. */
  private record Edit(int start, int end, String text) {
  }

  /** Элемент формы: узел разбора, его границы, класс метамодели и вид в записи конфигуратора. */
  private record Item(EdtNode node, Region region, EClass eClass, String kind) {
  }

  /** Правка одного свойства либо {@code null}, если убирать нечего. */
  private static Edit edit(String xml, EdtNode form, EPackage formPackage, EdtModel model,
      FormItemPropertyChangeDto change) throws XMLStreamException {
    String itemId = required(change.itemId, "itemId");
    String name = required(change.property, "property");
    boolean clearing = change.value == null || change.value.isEmpty();
    Item item = locate(xml, form, formPackage, model, itemId);
    if (item == null) {
      throw new IllegalArgumentException("В форме нет элемента " + itemId);
    }
    Region owner = item.region();
    EClass ownerClass = item.eClass();
    EStructuralFeature feature = feature(ownerClass, name);
    if (feature == null) {
      EClass extInfo = extInfoClass(item, formPackage);
      feature = feature(extInfo, name);
      if (feature == null) {
        throw new IllegalArgumentException("У вида элемента " + item.kind() + " нет свойства " + name);
      }
      Region info = first(EdtObjectRegions.nested(xml, item.region(), EdtFormContent.EXT_INFO));
      if (!info.found()) {
        return clearing ? null : newExtInfo(xml, item, extInfo, feature, change.value);
      }
      if (EdtObjectRegions.emptyTag(xml, info)) {
        return clearing ? null : expandedExtInfo(xml, info, feature, change.value);
      }
      owner = info;
      ownerClass = extInfo;
    }
    Region current = first(EdtObjectRegions.nested(xml, owner, feature.getName()));
    if (clearing) {
      return current.found() ? removal(xml, current) : null;
    }
    if (current.found()) {
      return new Edit(current.start(), current.end(),
          element(xml, feature, change.value, indentOf(xml, current.start())));
    }
    int at = insertionPoint(xml, owner, ownerClass, feature.getName());
    String indent = indentOf(xml, owner.start()) + INDENT;
    return new Edit(at, at, indent + element(xml, feature, change.value, indent) + eol(xml));
  }

  /** Описания вида ещё нет: оно встаёт в элемент целиком вместе со свойством. */
  private static Edit newExtInfo(String xml, Item item, EClass extInfo, EStructuralFeature feature, String value)
      throws XMLStreamException {
    String indent = indentOf(xml, item.region().start()) + INDENT;
    String eol = eol(xml);
    String block = indent + "<" + EdtFormContent.EXT_INFO + " xsi:type=\"" + TYPE_PREFIX + extInfo.getName() + "\">" + eol
        + indent + INDENT + element(xml, feature, value, indent + INDENT) + eol
        + indent + "</" + EdtFormContent.EXT_INFO + ">" + eol;
    int at = insertionPoint(xml, item.region(), item.eClass(), EdtFormContent.EXT_INFO);
    return new Edit(at, at, block);
  }

  /** Описание записано пустым тегом: раскрывается, чтобы вместить свойство. */
  private static Edit expandedExtInfo(String xml, Region info, EStructuralFeature feature, String value) {
    String indent = indentOf(xml, info.start());
    String eol = eol(xml);
    String openTag = xml.substring(info.start(), info.end() - 2) + ">";
    String block = openTag + eol
        + indent + INDENT + element(xml, feature, value, indent + INDENT) + eol
        + indent + "</" + EdtFormContent.EXT_INFO + ">";
    return new Edit(info.start(), info.end(), block);
  }

  /**
   * Элемент формы по идентификатору.
   *
   * Узлы разбора и участки файла идут в одном порядке, поэтому соответствуют
   * друг другу по номеру.
   */
  private static Item locate(String xml, EdtNode form, EPackage formPackage, EdtModel model, String itemId)
      throws XMLStreamException {
    EClass formClass = formPackage.getEClassifier("Form") instanceof EClass found ? found : null;
    return locate(xml, form, null, formClass, formPackage, model, itemId);
  }

  private static Item locate(String xml, EdtNode owner, Region ownerRegion, EClass ownerClass,
      EPackage formPackage, EdtModel model, String itemId) throws XMLStreamException {
    for (String container : EdtFormContent.ITEM_NODES) {
      List<EdtNode> nodes = owner.list(container);
      if (nodes.isEmpty()) {
        continue;
      }
      List<Region> regions = ownerRegion == null
          ? EdtObjectRegions.properties(xml, container)
          : EdtObjectRegions.nested(xml, ownerRegion, container);
      for (int i = 0; i < nodes.size() && i < regions.size(); i++) {
        EdtNode node = nodes.get(i);
        EClass eClass = itemClass(node, container, ownerClass, formPackage);
        if (itemId.equals(node.property("id"))) {
          return new Item(node, regions.get(i), eClass, EdtFormContent.kindOf(node, container, model));
        }
        Item nested = locate(xml, node, regions.get(i), eClass, formPackage, model, itemId);
        if (nested != null) {
          return nested;
        }
      }
    }
    return null;
  }

  /** Класс элемента: по атрибуту xsi:type, а без него по ссылке владельца. */
  private static EClass itemClass(EdtNode node, String container, EClass ownerClass, EPackage formPackage) {
    String typeName = localName(node.attributes().getOrDefault("xsi:type", ""));
    if (!typeName.isEmpty() && formPackage.getEClassifier(typeName) instanceof EClass found) {
      return found;
    }
    EStructuralFeature reference = ownerClass == null ? null : ownerClass.getEStructuralFeature(container);
    return reference instanceof EReference typed ? typed.getEReferenceType() : null;
  }

  /** Класс описания вида: по записанному xsi:type либо по виду элемента. */
  private static EClass extInfoClass(Item item, EPackage formPackage) {
    for (EdtNode info : item.node().list(EdtFormContent.EXT_INFO)) {
      String typeName = localName(info.attributes().getOrDefault("xsi:type", ""));
      if (formPackage.getEClassifier(typeName) instanceof EClass found) {
        return found;
      }
    }
    String type = item.node().property("type");
    return EdtFormItemProperties.extInfo(formPackage, type.isEmpty() ? item.kind() : type);
  }

  private static String localName(String xsiType) {
    return xsiType.startsWith(TYPE_PREFIX) ? xsiType.substring(TYPE_PREFIX.length()) : xsiType;
  }

  /**
   * Свойство класса под именем конфигуратора: имена совпадают с точностью до
   * регистра. Состав элемента и служебные признаки свойствами не считаются.
   */
  private static EStructuralFeature feature(EClass eClass, String name) {
    if (eClass == null) {
      return null;
    }
    for (EStructuralFeature feature : eClass.getEAllStructuralFeatures()) {
      boolean localized = LOCAL_STRING.equals(feature.getEType().getName());
      if (feature.isTransient() || (!localized && (feature.isMany() || feature instanceof EReference))) {
        continue;
      }
      if (feature.getName().equalsIgnoreCase(name)) {
        return feature;
      }
    }
    return null;
  }

  /** Место нового свойства среди записанных: по порядку свойств в схеме. */
  private static int insertionPoint(String xml, Region owner, EClass ownerClass, String name)
      throws XMLStreamException {
    List<String> order = new ArrayList<>();
    if (ownerClass != null) {
      for (EStructuralFeature feature : ownerClass.getEAllStructuralFeatures()) {
        order.add(feature.getName());
      }
    }
    int place = order.indexOf(name);
    for (EdtObjectRegions.Child child : EdtObjectRegions.children(xml, owner)) {
      int written = order.indexOf(child.name());
      if (place >= 0 && written > place) {
        return EdtObjectRegions.lineStart(xml, child.region().start());
      }
    }
    return EdtObjectRegions.closingTagLine(xml, owner);
  }

  /** Разметка свойства в записи EDT. */
  private static String element(String xml, EStructuralFeature feature, String value, String indent) {
    String name = feature.getName();
    if (LOCAL_STRING.equals(feature.getEType().getName())) {
      String eol = eol(xml);
      return "<" + name + ">" + eol
          + indent + INDENT + "<key>ru</key>" + eol
          + indent + INDENT + "<value>" + escape(value) + "</value>" + eol
          + indent + "</" + name + ">";
    }
    return "<" + name + ">" + escape(literal(feature, value)) + "</" + name + ">";
  }

  /** Значение в литералах EDT: перечисления и флаги пишутся строго как в схеме. */
  private static String literal(EStructuralFeature feature, String value) {
    if (feature.getEType() instanceof EEnum type) {
      for (EEnumLiteral literal : type.getELiterals()) {
        if (literal.getLiteral().equalsIgnoreCase(value) || literal.getName().equalsIgnoreCase(value)) {
          return literal.getLiteral();
        }
      }
      throw new IllegalArgumentException("У свойства " + feature.getName() + " нет значения " + value);
    }
    String type = feature.getEType().getInstanceClassName();
    if ("boolean".equals(type) || "java.lang.Boolean".equals(type)) {
      if (!"true".equals(value) && !"false".equals(value)) {
        throw new IllegalArgumentException("Свойство " + feature.getName() + " булево, а значение " + value);
      }
      return value;
    }
    if ("int".equals(type) || "java.lang.Integer".equals(type) || "long".equals(type) || "java.lang.Long".equals(type)) {
      try {
        return new BigDecimal(value.trim()).toBigIntegerExact().toString();
      } catch (ArithmeticException | NumberFormatException error) {
        throw new IllegalArgumentException("Свойство " + feature.getName() + " числовое, а значение " + value);
      }
    }
    return value;
  }

  /** Узел свойства уходит вместе со своей строкой. */
  private static Edit removal(String xml, Region current) {
    int from = EdtObjectRegions.lineStart(xml, current.start());
    int to = current.end();
    if (to < xml.length() && xml.charAt(to) == '\r') {
      to++;
    }
    if (to < xml.length() && xml.charAt(to) == '\n') {
      to++;
    }
    return new Edit(from, to, "");
  }

  private static String patched(String xml, List<Edit> edits) {
    List<Edit> ordered = new ArrayList<>(edits);
    ordered.sort(Comparator.comparingInt(Edit::start).reversed());
    int guard = Integer.MAX_VALUE;
    for (Edit edit : ordered) {
      if (edit.end() > guard) {
        throw new IllegalArgumentException("В одном вызове две правки одного свойства");
      }
      guard = edit.start();
    }
    StringBuilder text = new StringBuilder(xml);
    for (Edit edit : ordered) {
      text.replace(edit.start(), edit.end(), edit.text());
    }
    return text.toString();
  }

  /** Записанное перечитывается: свойство должно лежать у элемента ровно тем значением. */
  private static void verify(String patched, List<FormItemPropertyChangeDto> changes) throws IOException {
    EdtNode form = EdtObjectReader.parse(patched);
    for (FormItemPropertyChangeDto change : changes) {
      EdtNode item = find(form, change.itemId.trim());
      if (item == null) {
        throw new IllegalStateException("После записи в форме нет элемента " + change.itemId);
      }
      String written = written(item, change.property.trim());
      boolean clearing = change.value == null || change.value.isEmpty();
      if (clearing ? written != null : !sameValue(written, change.value)) {
        throw new IllegalStateException(
            "Свойство " + change.property + " записано как " + written + ", а ожидали " + change.value);
      }
    }
  }

  private static EdtNode find(EdtNode owner, String itemId) {
    for (String container : EdtFormContent.ITEM_NODES) {
      for (EdtNode node : owner.list(container)) {
        if (itemId.equals(node.property("id"))) {
          return node;
        }
        EdtNode nested = find(node, itemId);
        if (nested != null) {
          return nested;
        }
      }
    }
    return null;
  }

  /** Значение свойства как оно записано: у элемента либо в описании его вида. */
  private static String written(EdtNode item, String property) {
    List<EdtNode> scopes = new ArrayList<>();
    scopes.add(item);
    scopes.addAll(item.list(EdtFormContent.EXT_INFO));
    for (EdtNode scope : scopes) {
      for (EdtNode child : scope.children()) {
        if (!child.kind().equalsIgnoreCase(property)) {
          continue;
        }
        return child.children().isEmpty() ? child.value() : EdtPropertyValues.russian(scope, child.kind());
      }
    }
    return null;
  }

  private static boolean sameValue(String written, String expected) {
    if (written == null) {
      return false;
    }
    if (written.equalsIgnoreCase(expected)) {
      return true;
    }
    try {
      return new BigDecimal(written.trim()).compareTo(new BigDecimal(expected.trim())) == 0;
    } catch (NumberFormatException error) {
      return false;
    }
  }

  private static Region first(List<Region> regions) {
    return regions.isEmpty() ? EdtObjectRegions.MISSING : regions.get(0);
  }

  private static String indentOf(String xml, int start) {
    int line = EdtObjectRegions.lineStart(xml, start);
    int end = line;
    while (end < xml.length() && (xml.charAt(end) == ' ' || xml.charAt(end) == '\t')) {
      end++;
    }
    return xml.substring(line, end);
  }

  private static String eol(String xml) {
    return xml.contains("\r\n") ? "\r\n" : "\n";
  }

  private static String escape(String value) {
    return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
  }

  private static String required(String value, String field) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("Не задано " + field);
    }
    return value.trim();
  }
}
