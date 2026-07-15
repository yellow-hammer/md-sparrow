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
