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
 * (полный round-trip вложенных типов JAXB).
 */
public final class MdCfCatalogSubtreeXml {

  private static final String NS_MD_CLASSES = "http://v8.1c.ru/8.3/MDClasses";

  private MdCfCatalogSubtreeXml() {
  }

  public static String marshalStandardAttributesV21(
    io.github.yellowhammer.designerxml.jaxb.v2_21.v8_3_xcf_readable.StandardAttributeDescriptions value)
    throws JAXBException {
    return marshalFragment(
      SchemaVersion.V2_21,
      new QName(NS_MD_CLASSES, "StandardAttributes"),
      io.github.yellowhammer.designerxml.jaxb.v2_21.v8_3_xcf_readable.StandardAttributeDescriptions.class,
      value);
  }

  public static String marshalCharacteristicsV21(
    io.github.yellowhammer.designerxml.jaxb.v2_21.v8_3_xcf_readable.CharacteristicsDescriptions value)
    throws JAXBException {
    return marshalFragment(
      SchemaVersion.V2_21,
      new QName(NS_MD_CLASSES, "Characteristics"),
      io.github.yellowhammer.designerxml.jaxb.v2_21.v8_3_xcf_readable.CharacteristicsDescriptions.class,
      value);
  }

  public static String marshalStandardAttributesV20(
    io.github.yellowhammer.designerxml.jaxb.v2_20.v8_3_xcf_readable.StandardAttributeDescriptions value)
    throws JAXBException {
    return marshalFragment(
      SchemaVersion.V2_20,
      new QName(NS_MD_CLASSES, "StandardAttributes"),
      io.github.yellowhammer.designerxml.jaxb.v2_20.v8_3_xcf_readable.StandardAttributeDescriptions.class,
      value);
  }

  public static String marshalCharacteristicsV20(
    io.github.yellowhammer.designerxml.jaxb.v2_20.v8_3_xcf_readable.CharacteristicsDescriptions value)
    throws JAXBException {
    return marshalFragment(
      SchemaVersion.V2_20,
      new QName(NS_MD_CLASSES, "Characteristics"),
      io.github.yellowhammer.designerxml.jaxb.v2_20.v8_3_xcf_readable.CharacteristicsDescriptions.class,
      value);
  }

  public static io.github.yellowhammer.designerxml.jaxb.v2_21.v8_3_xcf_readable.StandardAttributeDescriptions
      unmarshalStandardAttributesV21(String xml)
    throws JAXBException {
    return unmarshalFragment(
      SchemaVersion.V2_21,
      io.github.yellowhammer.designerxml.jaxb.v2_21.v8_3_xcf_readable.StandardAttributeDescriptions.class,
      xml);
  }

  public static io.github.yellowhammer.designerxml.jaxb.v2_21.v8_3_xcf_readable.CharacteristicsDescriptions
      unmarshalCharacteristicsV21(String xml)
    throws JAXBException {
    return unmarshalFragment(
      SchemaVersion.V2_21,
      io.github.yellowhammer.designerxml.jaxb.v2_21.v8_3_xcf_readable.CharacteristicsDescriptions.class,
      xml);
  }

  public static io.github.yellowhammer.designerxml.jaxb.v2_20.v8_3_xcf_readable.StandardAttributeDescriptions
      unmarshalStandardAttributesV20(String xml)
    throws JAXBException {
    return unmarshalFragment(
      SchemaVersion.V2_20,
      io.github.yellowhammer.designerxml.jaxb.v2_20.v8_3_xcf_readable.StandardAttributeDescriptions.class,
      xml);
  }

  public static io.github.yellowhammer.designerxml.jaxb.v2_20.v8_3_xcf_readable.CharacteristicsDescriptions
      unmarshalCharacteristicsV20(String xml)
    throws JAXBException {
    return unmarshalFragment(
      SchemaVersion.V2_20,
      io.github.yellowhammer.designerxml.jaxb.v2_20.v8_3_xcf_readable.CharacteristicsDescriptions.class,
      xml);
  }

  private static <T> String marshalFragment(SchemaVersion v, QName q, Class<T> declaredType, T value)
    throws JAXBException {
    if (value == null) {
      return "";
    }
    Marshaller m = v.jaxbContext().createMarshaller();
    m.setProperty(Marshaller.JAXB_ENCODING, "UTF-8");
    m.setProperty(Marshaller.JAXB_FRAGMENT, Boolean.TRUE);
    StringWriter sw = new StringWriter();
    JAXBElement<T> root = new JAXBElement<>(q, declaredType, value);
    m.marshal(root, sw);
    return sw.toString();
  }

  private static <T> T unmarshalFragment(SchemaVersion v, Class<T> declaredType, String xml)
    throws JAXBException {
    if (xml == null || xml.isBlank()) {
      return null;
    }
    Unmarshaller u = v.jaxbContext().createUnmarshaller();
    JAXBElement<T> je = u.unmarshal(new StreamSource(new StringReader(xml.trim())), declaredType);
    if (je == null || je.getValue() == null) {
      throw new JAXBException("empty unmarshal result for " + declaredType.getSimpleName());
    }
    return je.getValue();
  }
}
