/*
 * This file is a part of md-sparrow.
 *
 * Copyright (c) 2026
 * Ivan Karlo <i.karlo@outlook.com> and contributors
 *
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package io.github.yellowhammer.designerxml.cf;

import io.github.yellowhammer.designerxml.SchemaVersion;

import jakarta.xml.bind.JAXBElement;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Marshaller;
import jakarta.xml.bind.Unmarshaller;

import javax.xml.namespace.QName;
import javax.xml.transform.stream.StreamSource;

import java.io.StringReader;
import java.io.StringWriter;

/**
 * Сериализация узлов {@code StandardAttributes} и {@code Characteristics} справочника в строку и обратно
 * (полный round-trip вложенных типов JAXB). Версионно-нейтрально: класс выводится из пакета версии.
 */
public final class MdCfCatalogSubtreeXml {

  private static final String NS_MD_CLASSES = "http://v8.1c.ru/8.3/MDClasses";
  private static final String STANDARD_ATTRIBUTES = "StandardAttributes";
  private static final String CHARACTERISTICS = "Characteristics";

  private MdCfCatalogSubtreeXml() {
  }

  public static String marshalStandardAttributes(SchemaVersion version, Object value) throws JAXBException {
    return marshalFragment(version, new QName(NS_MD_CLASSES, STANDARD_ATTRIBUTES), value);
  }

  public static String marshalCharacteristics(SchemaVersion version, Object value) throws JAXBException {
    return marshalFragment(version, new QName(NS_MD_CLASSES, CHARACTERISTICS), value);
  }

  public static Object unmarshalStandardAttributes(SchemaVersion version, String xml) throws JAXBException {
    return unmarshalFragment(version, readableClass(version, "StandardAttributeDescriptions"), xml);
  }

  public static Object unmarshalCharacteristics(SchemaVersion version, String xml) throws JAXBException {
    return unmarshalFragment(version, readableClass(version, "CharacteristicsDescriptions"), xml);
  }

  private static Class<?> readableClass(SchemaVersion version, String simpleName) {
    String fqcn = "io.github.yellowhammer.designerxml.jaxb." + version.name().toLowerCase()
      + ".v8_3_xcf_readable." + simpleName;
    try {
      return Class.forName(fqcn);
    } catch (ClassNotFoundException e) {
      throw new IllegalStateException("нет класса " + fqcn, e);
    }
  }

  private static String marshalFragment(SchemaVersion version, QName q, Object value) throws JAXBException {
    if (value == null) {
      return "";
    }
    Marshaller m = version.jaxbContext().createMarshaller();
    m.setProperty(Marshaller.JAXB_ENCODING, "UTF-8");
    m.setProperty(Marshaller.JAXB_FRAGMENT, Boolean.TRUE);
    StringWriter sw = new StringWriter();
    @SuppressWarnings({"unchecked", "rawtypes"})
    JAXBElement<?> root = new JAXBElement(q, value.getClass(), value);
    m.marshal(root, sw);
    return sw.toString();
  }

  private static <T> T unmarshalFragment(SchemaVersion version, Class<T> declaredType, String xml)
    throws JAXBException {
    if (xml == null || xml.isBlank()) {
      return null;
    }
    Unmarshaller u = version.jaxbContext().createUnmarshaller();
    JAXBElement<T> je = u.unmarshal(new StreamSource(new StringReader(xml.trim())), declaredType);
    if (je == null || je.getValue() == null) {
      throw new JAXBException("empty unmarshal result for " + declaredType.getSimpleName());
    }
    return je.getValue();
  }
}
