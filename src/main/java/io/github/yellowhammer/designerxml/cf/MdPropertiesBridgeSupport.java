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
import io.github.yellowhammer.designerxml.reflect.JaxbReflect;

import jakarta.xml.bind.JAXBException;

import java.math.BigDecimal;
import java.util.List;

/**
 * Общие приёмы чтения и записи {@code *Properties} через JAXB-рефлексию.
 */
final class MdPropertiesBridgeSupport {

  private MdPropertiesBridgeSupport() {
  }

  /** Поддерево стандартных реквизитов как XML; пустая строка, если версия схемы его не знает. */
  static String marshalStandardAttributesOrEmpty(SchemaVersion version, Object value) {
    try {
      return MdCfCatalogSubtreeXml.marshalStandardAttributes(version, value);
    } catch (JAXBException e) {
      return "";
    }
  }

  /** Поддерево характеристик как XML; пустая строка, если версия схемы его не знает. */
  static String marshalCharacteristicsOrEmpty(SchemaVersion version, Object value) {
    try {
      return MdCfCatalogSubtreeXml.marshalCharacteristics(version, value);
    } catch (JAXBException e) {
      return "";
    }
  }

  /** Значение перечисления как имя Java-константы ({@code AUTO}); {@code null}, если элемента нет. */
  static String enumName(Object properties, String getter) {
    Object value = JaxbReflect.getOptional(properties, getter);
    return value == null ? null : ((Enum<?>) value).name();
  }

  /** Создаёт локализованную строку, если её ещё нет, и кладёт русское содержимое. */
  static void ensureAndSetRu(Object properties, String getter, String setter, String ru) {
    Object localString = JaxbReflect.ensureOptional(properties, getter, setter);
    LocalStringSync.setOrPutRu(localString, ru == null ? "" : ru);
  }

  static String nullIfBlank(String value) {
    return value == null || value.isBlank() ? null : value;
  }

  /** Число из XML как строка без экспоненты; {@code "0"}, если элемента нет. */
  static String decimalOrZero(Object properties, String getter) {
    Object value = JaxbReflect.getOptional(properties, getter);
    return value == null ? "0" : ((BigDecimal) value).toPlainString();
  }

  /** Пишет число, если значение задано; пустую строку игнорирует. */
  static void setDecimal(Object properties, String setter, String value) {
    if (value == null || value.isBlank()) {
      return;
    }
    JaxbReflect.setOptional(properties, setter, new BigDecimal(value.trim()));
  }

  /** Ссылки на объекты метаданных из {@code MDListType}. */
  static void addItems(List<String> out, Object mdListType) {
    if (mdListType != null) {
      out.addAll(MdListTypeRefs.readItemTexts(JaxbReflect.list(mdListType, "getItem")));
    }
  }

  /** Поля объекта из {@code FieldList}. */
  static void addFields(List<String> out, Object fieldList) {
    if (fieldList != null) {
      out.addAll(JaxbReflect.<String>list(fieldList, "getField"));
    }
  }

  /** Замена состава {@code FieldList}; отсутствующий список пропускается. */
  static void setFields(Object fieldList, List<String> values) {
    if (fieldList == null) {
      return;
    }
    List<Object> field = JaxbReflect.list(fieldList, "getField");
    field.clear();
    if (values != null) {
      field.addAll(values);
    }
  }

  /** Пишет поддерево стандартных реквизитов; пустое значение оставляет XML как есть. */
  static void applyStandardAttributes(SchemaVersion version, Object properties, String xml) {
    applySubtree(version, properties, "setStandardAttributes", xml, true);
  }

  /** Пишет поддерево характеристик; пустое значение оставляет XML как есть. */
  static void applyCharacteristics(SchemaVersion version, Object properties, String xml) {
    applySubtree(version, properties, "setCharacteristics", xml, false);
  }

  private static void applySubtree(
    SchemaVersion version, Object properties, String setter, String xml, boolean standardAttributes) {
    if (xml == null || xml.isBlank()) {
      return;
    }
    try {
      Object value = standardAttributes
        ? MdCfCatalogSubtreeXml.unmarshalStandardAttributes(version, xml.trim())
        : MdCfCatalogSubtreeXml.unmarshalCharacteristics(version, xml.trim());
      JaxbReflect.setOptional(properties, setter, value);
    } catch (JAXBException e) {
      throw new IllegalArgumentException(setter + ": " + e.getMessage(), e);
    }
  }

  /** Проверяет, что DTO относится к тому же объекту, и пишет общие для всех видов свойства. */
  static void applyCommon(Object properties, MdObjectPropertiesDto dto) {
    if (!dto.internalName.equals(JaxbReflect.getStringOptional(properties, "getName"))) {
      throw new IllegalArgumentException("internalName mismatch with XML");
    }
    LocalStringSync.setOrPutRu(
      JaxbReflect.getOptional(properties, "getSynonym"),
      dto.synonymRu == null ? "" : dto.synonymRu);
    JaxbReflect.setOptional(properties, "setComment", dto.comment == null ? "" : dto.comment);
  }
}
