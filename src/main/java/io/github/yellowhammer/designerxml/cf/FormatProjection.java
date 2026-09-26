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

import io.github.yellowhammer.designerxml.SchemaVersion;
import jakarta.xml.bind.JAXBElement;
import jakarta.xml.bind.annotation.XmlAnyAttribute;
import jakarta.xml.bind.annotation.XmlAnyElement;
import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlElementDecl;
import jakarta.xml.bind.annotation.XmlElementRef;
import jakarta.xml.bind.annotation.XmlElementRefs;
import jakarta.xml.bind.annotation.XmlElements;
import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.XmlTransient;
import jakarta.xml.bind.annotation.XmlType;
import jakarta.xml.bind.annotation.XmlValue;

import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.WildcardType;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Файл формата V из канонического эталона - выгрузки платформы самого нового формата.
 *
 * <p>Между форматами 2.10 и 2.21 схемы выгрузки только прирастают: удалений и перестановок
 * элементов нет. Поэтому файл старого формата - это файл нового без элементов, которых старый
 * формат не знает. Состав формата берётся из JAXB-модели версии (классы
 * {@code io.github.yellowhammer.designerxml.jaxb.v2_XX}, сгенерированные из XSD), а порядок и
 * значения - из канонического файла: порядок записи платформы с порядком XSD не совпадает,
 * значений по умолчанию в XSD нет, а обязательность в XSD не равна тому, что платформа пишет.
 *
 * <p>Работа идёт по тексту: удаляются целые строки элементов, остальное остаётся как было,
 * поэтому BOM, CRLF, {@code <X/>} и отсутствие перевода строки в конце сохраняются.
 * В заголовке меняется атрибут {@code version} и снимаются объявления пространств имён,
 * которых в заголовке формата ещё нет. У {@code Configuration.xml} поверх этого применяются
 * правила значений {@link ConfigurationFormatRules}.
 *
 * <p>Поддерживаемые корни: {@code MetaDataObject} (объекты, конфигурация, расширение, внешние
 * отчёты и обработки), {@code Form} управляемой формы и {@code Rights} прав роли (модели прав
 * нет, у них меняется только версия). Описание WS-ссылки ({@code definitions} WSDL) формата
 * выгрузки не несёт и возвращается как есть: платформы 8.3.23, 8.3.24, 8.3.27 и 8.5.1 пишут его
 * одинаково.
 */
public final class FormatProjection {

  private static final String MD_CLASSES_NS = "http://v8.1c.ru/8.3/MDClasses";
  private static final String LOGFORM_NS = "http://v8.1c.ru/8.3/xcf/logform";
  private static final String ROLES_NS = "http://v8.1c.ru/8.2/roles";
  private static final String WSDL_NS = "http://schemas.xmlsoap.org/wsdl/";
  private static final String XSI_NS = "http://www.w3.org/2001/XMLSchema-instance";
  private static final String JAXB_BASE = "io.github.yellowhammer.designerxml.jaxb.";
  private static final String DEFAULT_NAME = "##default";

  private static final Pattern VERSION_ATTRIBUTE = Pattern.compile("\\sversion=\"([^\"]*)\"");

  /** Пространство имён в заголовке корня и формат, с которого платформа его объявляет. */
  private record LateNamespace(String prefix, String uri, SchemaVersion since) {
  }

  /**
   * Объявления в корне, появившиеся в заголовке позже остальных.
   *
   * <p>{@code pal}: корни {@code MetaDataObject} и {@code Form} объявляют его в выгрузке 8.5.1
   * (формат 2.21) и не объявляют в 2.10-2.20 - эталоны samples-1c-platform всех 12 форматов,
   * выгрузки ibcmd 8.3.23, 8.3.24, 8.3.27 и 8.5.1. Из XSD это не вывести: пространство палитры
   * не упоминает ни одна схема 2.10-2.21.
   */
  private static final List<LateNamespace> LATE_ROOT_NAMESPACES = List.of(
    new LateNamespace("pal", "http://v8.1c.ru/8.1/data/ui/colors/palette", SchemaVersion.V2_21));

  private enum Root {
    META_DATA_OBJECT(MD_CLASSES_NS, "MetaDataObject", "mdclasses.MetaDataObject"),
    FORM(LOGFORM_NS, "Form", "v8_3_xcf_logform.Form"),
    RIGHTS(ROLES_NS, "Rights", null),
    WSDL(WSDL_NS, "definitions", null);

    private final String namespace;
    private final String localName;
    private final String modelClass;

    Root(String namespace, String localName, String modelClass) {
      this.namespace = namespace;
      this.localName = localName;
      this.modelClass = modelClass;
    }

    static Root of(String namespace, String localName) {
      for (Root root : values()) {
        if (root.namespace.equals(namespace) && root.localName.equals(localName)) {
          return root;
        }
      }
      throw new IllegalArgumentException("неизвестный корень файла выгрузки: {" + namespace + "}" + localName);
    }
  }

  /** Состав элементов JAXB-класса: локальное имя → тип для спуска ({@code null} - не спускаться). */
  private record Members(Map<String, Class<?>> elements, boolean anyElement) {
  }

  private static final Map<Class<?>, Members> MEMBERS = new ConcurrentHashMap<>();

  private FormatProjection() {
  }

  /**
   * Файл формата {@code target} из канонического эталона.
   *
   * @param canonicalXml текст файла выгрузки платформы (формат не старше {@code target})
   * @param target формат результата
   * @return текст файла формата {@code target}
   * @throws IllegalArgumentException если {@code target} новее эталона, корень неизвестен или
   *   вида объекта в формате {@code target} ещё нет
   * @throws IllegalStateException если удаляемый элемент делит строку с другим текстом
   */
  public static String project(String canonicalXml, SchemaVersion target) {
    Objects.requireNonNull(canonicalXml, "canonicalXml");
    Objects.requireNonNull(target, "target");
    try {
      return projectChecked(canonicalXml, target);
    } catch (XMLStreamException e) {
      throw new IllegalArgumentException("эталон не разбирается как XML: " + e.getMessage(), e);
    }
  }

  /**
   * Есть ли вид объекта метаданных в формате.
   *
   * @param kind локальное имя элемента вида в {@code MetaDataObject} (например {@code Catalog})
   * @param version формат
   * @return {@code true}, если модель формата знает этот вид
   */
  public static boolean hasObjectKind(String kind, SchemaVersion version) {
    Objects.requireNonNull(kind, "kind");
    return members(modelClass(Root.META_DATA_OBJECT, version)).elements().containsKey(kind);
  }

  private static String projectChecked(String xml, SchemaVersion target) throws XMLStreamException {
    XMLStreamReader reader = XmlLines.reader(xml);
    try {
      while (reader.hasNext() && reader.next() != XMLStreamConstants.START_ELEMENT) {
        // пролог
      }
      if (!reader.isStartElement()) {
        throw new IllegalArgumentException("в эталоне нет корневого элемента");
      }
      Root root = Root.of(nullToEmpty(reader.getNamespaceURI()), reader.getLocalName());
      if (root == Root.WSDL) {
        return xml;
      }
      int rootStart = XmlLines.offset(reader);
      SchemaVersion source = sourceVersion(reader.getAttributeValue(null, "version"));
      if (target.compareTo(source) > 0) {
        throw new IllegalArgumentException(
          "формат " + target.metadataObjectVersionAttribute() + " новее эталона "
            + source.metadataObjectVersionAttribute() + ": проекция идёт только к более старым форматам");
      }
      List<int[]> removals = new ArrayList<>();
      String firstChild = root.modelClass == null
        ? null
        : walk(xml, reader, root, target, source, removals);
      String result = rewriteHeader(XmlLines.removeAll(xml, removals), rootStart, root, target);
      if (root == Root.META_DATA_OBJECT && "Configuration".equals(firstChild)) {
        result = ConfigurationFormatRules.apply(result, target);
      }
      return result;
    } finally {
      reader.close();
    }
  }

  /**
   * Обход от корня: элементы, которых нет в модели формата, уходят в {@code removals}.
   *
   * @return локальное имя первого потомка корня
   */
  private static String walk(
    String xml,
    XMLStreamReader reader,
    Root root,
    SchemaVersion target,
    SchemaVersion source,
    List<int[]> removals) throws XMLStreamException {
    // null в стеке - элемент без модели: его потомков не проверяем
    List<Class<?>> stack = new ArrayList<>();
    stack.add(modelClass(root, target));
    String firstChild = null;
    while (reader.hasNext()) {
      int event = reader.next();
      if (event == XMLStreamConstants.END_ELEMENT) {
        stack.remove(stack.size() - 1);
        continue;
      }
      if (event != XMLStreamConstants.START_ELEMENT) {
        continue;
      }
      String name = reader.getLocalName();
      boolean rootChild = stack.size() == 1;
      if (rootChild && firstChild == null) {
        firstChild = name;
      }
      Class<?> parent = stack.get(stack.size() - 1);
      if (parent == null) {
        stack.add(null);
        continue;
      }
      Members members = members(parent);
      if (members.elements().containsKey(name)) {
        Class<?> child = members.elements().get(name);
        // с xsi:type фактический тип - наследник объявленного, его состав тут не известен
        stack.add(reader.getAttributeValue(XSI_NS, "type") == null ? child : null);
        continue;
      }
      if (members.anyElement()) {
        stack.add(null);
        continue;
      }
      if (rootChild && root == Root.META_DATA_OBJECT) {
        throw kindAbsent(name, target, source);
      }
      int start = XmlLines.offset(reader);
      int end = XmlLines.skipElement(xml, reader);
      removals.add(XmlLines.wholeLines(xml, start, end, name));
    }
    return firstChild;
  }

  private static IllegalArgumentException kindAbsent(String kind, SchemaVersion target, SchemaVersion source) {
    for (SchemaVersion version : SchemaVersion.values()) {
      if (version.compareTo(target) > 0 && version.compareTo(source) <= 0 && hasObjectKind(kind, version)) {
        return new IllegalArgumentException(
          "вид " + kind + " появился в формате " + version.metadataObjectVersionAttribute()
            + ", в формате " + target.metadataObjectVersionAttribute() + " его нет");
      }
    }
    return new IllegalArgumentException(
      "вида " + kind + " нет в формате " + target.metadataObjectVersionAttribute());
  }

  private static String rewriteHeader(String xml, int rootStart, Root root, SchemaVersion target) {
    int tagEnd = XmlLines.startTagEnd(xml, rootStart);
    String tag = xml.substring(rootStart, tagEnd + 1);
    Matcher version = VERSION_ATTRIBUTE.matcher(tag);
    if (!version.find()) {
      throw new IllegalArgumentException("в корне эталона нет атрибута version");
    }
    String rewritten = tag.substring(0, version.start(1)) + target.metadataObjectVersionAttribute()
      + tag.substring(version.end(1));
    if (root != Root.RIGHTS) {
      for (LateNamespace namespace : LATE_ROOT_NAMESPACES) {
        if (target.compareTo(namespace.since()) < 0) {
          rewritten = rewritten.replaceFirst(
            "\\s+xmlns:" + namespace.prefix() + "=\"" + Pattern.quote(namespace.uri()) + "\"", "");
        }
      }
    }
    return xml.substring(0, rootStart) + rewritten + xml.substring(tagEnd + 1);
  }

  private static SchemaVersion sourceVersion(String attribute) {
    if (attribute == null) {
      throw new IllegalArgumentException("в корне эталона нет атрибута version");
    }
    return SchemaVersion.byVersionAttribute(attribute)
      .orElseThrow(() -> new IllegalArgumentException("неизвестный формат эталона: " + attribute));
  }

  private static Class<?> modelClass(Root root, SchemaVersion version) {
    String name = JAXB_BASE + version.name().toLowerCase(Locale.ROOT) + "." + root.modelClass;
    try {
      return Class.forName(name, false, FormatProjection.class.getClassLoader());
    } catch (ClassNotFoundException e) {
      throw new IllegalStateException("нет JAXB-модели формата " + version.metadataObjectVersionAttribute() + ": " + name, e);
    }
  }

  private static Members members(Class<?> type) {
    return MEMBERS.computeIfAbsent(type, FormatProjection::collectMembers);
  }

  private static Members collectMembers(Class<?> type) {
    Map<String, Class<?>> elements = new HashMap<>();
    boolean anyElement = false;
    for (Class<?> owner = type; owner != null && owner != Object.class; owner = owner.getSuperclass()) {
      for (Field field : owner.getDeclaredFields()) {
        if (Modifier.isStatic(field.getModifiers()) || field.isSynthetic() || notElement(field)) {
          continue;
        }
        boolean declared = false;
        XmlElement element = field.getAnnotation(XmlElement.class);
        if (element != null) {
          put(elements, name(element.name(), field), element.type(), field);
          declared = true;
        }
        XmlElements choice = field.getAnnotation(XmlElements.class);
        if (choice != null) {
          for (XmlElement option : choice.value()) {
            put(elements, name(option.name(), field), option.type(), field);
          }
          declared = true;
        }
        XmlElementRef ref = field.getAnnotation(XmlElementRef.class);
        if (ref != null) {
          putRef(elements, ref, field, owner);
          declared = true;
        }
        XmlElementRefs refs = field.getAnnotation(XmlElementRefs.class);
        if (refs != null) {
          for (XmlElementRef option : refs.value()) {
            putRef(elements, option, field, owner);
          }
          declared = true;
        }
        if (field.isAnnotationPresent(XmlAnyElement.class)) {
          anyElement = true;
          declared = true;
        }
        if (!declared) {
          // поле без аннотаций при доступе FIELD - элемент с именем поля
          put(elements, field.getName(), XmlElement.DEFAULT.class, field);
        }
      }
    }
    return new Members(elements, anyElement);
  }

  private static boolean notElement(Field field) {
    return field.isAnnotationPresent(XmlAttribute.class)
      || field.isAnnotationPresent(XmlAnyAttribute.class)
      || field.isAnnotationPresent(XmlValue.class)
      || field.isAnnotationPresent(XmlTransient.class);
  }

  private static String name(String annotated, Field field) {
    return DEFAULT_NAME.equals(annotated) ? field.getName() : annotated;
  }

  private static void put(Map<String, Class<?>> elements, String name, Class<?> annotatedType, Field field) {
    Class<?> type = annotatedType == XmlElement.DEFAULT.class ? elementType(field.getGenericType()) : annotatedType;
    elements.putIfAbsent(name, bean(type));
  }

  private static void putRef(Map<String, Class<?>> elements, XmlElementRef ref, Field field, Class<?> owner) {
    Class<?> refType = ref.type();
    if (refType != XmlElementRef.DEFAULT.class && refType != JAXBElement.class) {
      elements.putIfAbsent(rootElementName(ref.name(), refType), bean(refType));
      return;
    }
    Class<?> fieldType = elementType(field.getGenericType());
    if (refType == XmlElementRef.DEFAULT.class && fieldType != null && fieldType.isAnnotationPresent(XmlRootElement.class)) {
      elements.putIfAbsent(rootElementName(ref.name(), fieldType), bean(fieldType));
      return;
    }
    if (DEFAULT_NAME.equals(ref.name())) {
      return;
    }
    Class<?> type = fieldType != null && fieldType != Object.class && fieldType != JAXBElement.class
      ? fieldType
      : declaredValueType(owner, ref.name());
    elements.putIfAbsent(ref.name(), bean(type));
  }

  private static String rootElementName(String refName, Class<?> type) {
    if (!DEFAULT_NAME.equals(refName)) {
      return refName;
    }
    XmlRootElement root = type.getAnnotation(XmlRootElement.class);
    if (root != null && !DEFAULT_NAME.equals(root.name())) {
      return root.name();
    }
    String simple = type.getSimpleName();
    return Character.toLowerCase(simple.charAt(0)) + simple.substring(1);
  }

  /** Тип значения {@code JAXBElement} по объявлению элемента в {@code ObjectFactory} пакета владельца. */
  private static Class<?> declaredValueType(Class<?> owner, String name) {
    Class<?> factory;
    try {
      factory = Class.forName(owner.getPackageName() + ".ObjectFactory", false, owner.getClassLoader());
    } catch (ClassNotFoundException e) {
      return null;
    }
    Class<?> global = null;
    for (Method method : factory.getDeclaredMethods()) {
      XmlElementDecl decl = method.getAnnotation(XmlElementDecl.class);
      if (decl == null || !decl.name().equals(name) || method.getParameterCount() != 1) {
        continue;
      }
      if (decl.scope() == owner) {
        return method.getParameterTypes()[0];
      }
      if (decl.scope() == XmlElementDecl.GLOBAL.class) {
        global = method.getParameterTypes()[0];
      }
    }
    return global;
  }

  /** Тип элемента поля: {@code List<X>} и {@code JAXBElement<X>} дают {@code X}. */
  private static Class<?> elementType(Type type) {
    if (type instanceof Class<?> cls) {
      return cls;
    }
    if (type instanceof ParameterizedType parameterized && parameterized.getRawType() instanceof Class<?> raw) {
      if (Collection.class.isAssignableFrom(raw) || raw == JAXBElement.class) {
        return elementType(parameterized.getActualTypeArguments()[0]);
      }
      return raw;
    }
    if (type instanceof WildcardType wildcard) {
      return elementType(wildcard.getUpperBounds()[0]);
    }
    return null;
  }

  /** Спускаемся только в JAXB-бины: у простых типов и перечислений состава нет. */
  private static Class<?> bean(Class<?> type) {
    if (type == null || type.isEnum() || !type.isAnnotationPresent(XmlType.class)) {
      return null;
    }
    return type;
  }

  private static String nullToEmpty(String value) {
    return value == null ? "" : value;
  }
}
