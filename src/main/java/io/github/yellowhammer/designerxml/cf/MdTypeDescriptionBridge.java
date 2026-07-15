/*
 * This file is a part of md-sparrow.
 *
 * Copyright (c) 2026
 * Ivan Karlo <i.karlo@outlook.com> and contributors
 *
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package io.github.yellowhammer.designerxml.cf;

import io.github.yellowhammer.designerxml.reflect.JaxbReflect;

import javax.xml.namespace.QName;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Чтение и запись {@code v8:TypeDescription}: состав типов и квалификаторы.
 *
 * <p>В XML тип записан как имя с префиксом ({@code xs:string}, {@code cfg:CatalogRef.Номенклатура}),
 * в модели JAXB — как {@link QName}. Префиксы фиксированы платформой, поэтому держим их таблицей:
 * так строка типа одинаково читается и пишется независимо от объявлений в конкретном файле.
 */
public final class MdTypeDescriptionBridge {

  /** Префикс → пространство имён, как их пишет конфигуратор. */
  private static final Map<String, String> NS_BY_PREFIX = new LinkedHashMap<>();

  static {
    NS_BY_PREFIX.put("xs", "http://www.w3.org/2001/XMLSchema");
    NS_BY_PREFIX.put("cfg", "http://v8.1c.ru/8.1/data/enterprise/current-config");
    NS_BY_PREFIX.put("v8", "http://v8.1c.ru/8.1/data/core");
    NS_BY_PREFIX.put("xen", "http://v8.1c.ru/8.3/xcf/enums");
    NS_BY_PREFIX.put("app", "http://v8.1c.ru/8.2/managed-application/core");
    NS_BY_PREFIX.put("style", "http://v8.1c.ru/8.1/data/ui/style");
    NS_BY_PREFIX.put("sys", "http://v8.1c.ru/8.1/data/ui/fonts/system");
    NS_BY_PREFIX.put("web", "http://v8.1c.ru/8.1/data/ui/colors/web");
    NS_BY_PREFIX.put("win", "http://v8.1c.ru/8.1/data/ui/colors/windows");
  }

  private MdTypeDescriptionBridge() {
  }

  /**
   * @param typeDescription узел {@code v8:TypeDescription} или {@code null}
   * @return DTO либо {@code null}, если типа нет
   */
  public static MdTypeDescriptionDto read(Object typeDescription) {
    if (typeDescription == null) {
      return null;
    }
    MdTypeDescriptionDto dto = new MdTypeDescriptionDto();
    for (QName type : JaxbReflect.<QName>list(typeDescription, "getType")) {
      dto.types.add(typeText(type));
    }
    Object sq = JaxbReflect.getOptional(typeDescription, "getStringQualifiers");
    if (sq != null) {
      MdTypeDescriptionDto.MdStringQualifiersDto q = new MdTypeDescriptionDto.MdStringQualifiersDto();
      q.length = decimalText(JaxbReflect.getOptional(sq, "getLength"));
      q.allowedLength = enumName(JaxbReflect.getOptional(sq, "getAllowedLength"));
      dto.stringQualifiers = q;
    }
    Object nq = JaxbReflect.getOptional(typeDescription, "getNumberQualifiers");
    if (nq != null) {
      MdTypeDescriptionDto.MdNumberQualifiersDto q = new MdTypeDescriptionDto.MdNumberQualifiersDto();
      q.digits = decimalText(JaxbReflect.getOptional(nq, "getDigits"));
      q.fractionDigits = decimalText(JaxbReflect.getOptional(nq, "getFractionDigits"));
      q.allowedSign = enumName(JaxbReflect.getOptional(nq, "getAllowedSign"));
      dto.numberQualifiers = q;
    }
    Object dq = JaxbReflect.getOptional(typeDescription, "getDateQualifiers");
    if (dq != null) {
      MdTypeDescriptionDto.MdDateQualifiersDto q = new MdTypeDescriptionDto.MdDateQualifiersDto();
      q.dateFractions = enumName(JaxbReflect.getOptional(dq, "getDateFractions"));
      dto.dateQualifiers = q;
    }
    Object bq = JaxbReflect.getOptional(typeDescription, "getBinaryDataQualifiers");
    if (bq != null) {
      MdTypeDescriptionDto.MdBinaryDataQualifiersDto q = new MdTypeDescriptionDto.MdBinaryDataQualifiersDto();
      q.length = decimalText(JaxbReflect.getOptional(bq, "getLength"));
      q.allowedLength = enumName(JaxbReflect.getOptional(bq, "getAllowedLength"));
      dto.binaryDataQualifiers = q;
    }
    return dto;
  }

  /**
   * Применяет DTO к узлу {@code v8:TypeDescription}.
   */
  public static void apply(Object typeDescription, MdTypeDescriptionDto dto) {
    if (typeDescription == null || dto == null) {
      return;
    }
    List<QName> types = JaxbReflect.list(typeDescription, "getType");
    types.clear();
    for (String text : dto.types) {
      types.add(qname(text));
    }
    applyStringQualifiers(typeDescription, dto);
    applyNumberQualifiers(typeDescription, dto);
    applyDateQualifiers(typeDescription, dto);
    applyBinaryDataQualifiers(typeDescription, dto);
  }

  private static void applyStringQualifiers(Object typeDescription, MdTypeDescriptionDto dto) {
    if (dto.stringQualifiers == null) {
      return;
    }
    Object sq = JaxbReflect.ensureOptional(typeDescription, "getStringQualifiers", "setStringQualifiers");
    if (sq == null) {
      return;
    }
    JaxbReflect.setOptional(sq, "setLength", decimalOrZero(dto.stringQualifiers.length));
    JaxbReflect.setEnumOrKeep(sq, "setAllowedLength", dto.stringQualifiers.allowedLength);
  }

  private static void applyNumberQualifiers(Object typeDescription, MdTypeDescriptionDto dto) {
    if (dto.numberQualifiers == null) {
      return;
    }
    Object nq = JaxbReflect.ensureOptional(typeDescription, "getNumberQualifiers", "setNumberQualifiers");
    if (nq == null) {
      return;
    }
    JaxbReflect.setOptional(nq, "setDigits", decimalOrZero(dto.numberQualifiers.digits));
    JaxbReflect.setOptional(nq, "setFractionDigits", decimalOrZero(dto.numberQualifiers.fractionDigits));
    JaxbReflect.setEnumOrKeep(nq, "setAllowedSign", dto.numberQualifiers.allowedSign);
  }

  private static void applyDateQualifiers(Object typeDescription, MdTypeDescriptionDto dto) {
    if (dto.dateQualifiers == null) {
      return;
    }
    Object dq = JaxbReflect.ensureOptional(typeDescription, "getDateQualifiers", "setDateQualifiers");
    if (dq == null) {
      return;
    }
    JaxbReflect.setEnumOrKeep(dq, "setDateFractions", dto.dateQualifiers.dateFractions);
  }

  private static void applyBinaryDataQualifiers(Object typeDescription, MdTypeDescriptionDto dto) {
    if (dto.binaryDataQualifiers == null) {
      return;
    }
    Object bq = JaxbReflect.ensureOptional(typeDescription, "getBinaryDataQualifiers", "setBinaryDataQualifiers");
    if (bq == null) {
      return;
    }
    JaxbReflect.setOptional(bq, "setLength", decimalOrZero(dto.binaryDataQualifiers.length));
    JaxbReflect.setEnumOrKeep(bq, "setAllowedLength", dto.binaryDataQualifiers.allowedLength);
  }

  /** {@code xs:string} из QName: префикс берём по пространству имён, как пишет конфигуратор. */
  static String typeText(QName type) {
    String prefix = prefixForNamespace(type.getNamespaceURI(), type.getPrefix());
    return prefix.isEmpty() ? type.getLocalPart() : prefix + ":" + type.getLocalPart();
  }

  /** QName из {@code xs:string}: пространство имён берём по префиксу. */
  static QName qname(String text) {
    String value = text == null ? "" : text.trim();
    int colon = value.indexOf(':');
    if (colon < 0) {
      return new QName(value);
    }
    String prefix = value.substring(0, colon);
    String local = value.substring(colon + 1);
    String ns = NS_BY_PREFIX.get(prefix);
    if (ns == null) {
      throw new IllegalArgumentException("неизвестный префикс типа: " + prefix);
    }
    return new QName(ns, local, prefix);
  }

  /** Пространство имён префикса; пусто, если префикс неизвестен. */
  static String namespaceForPrefix(String prefix) {
    return NS_BY_PREFIX.getOrDefault(prefix, "");
  }

  private static String prefixForNamespace(String namespace, String parsedPrefix) {
    for (Map.Entry<String, String> e : NS_BY_PREFIX.entrySet()) {
      if (e.getValue().equals(namespace)) {
        return e.getKey();
      }
    }
    return parsedPrefix == null ? "" : parsedPrefix;
  }

  private static String decimalText(Object value) {
    return value == null ? "0" : ((BigDecimal) value).toPlainString();
  }

  private static BigDecimal decimalOrZero(String text) {
    return new BigDecimal(text == null || text.isBlank() ? "0" : text.trim());
  }

  private static String enumName(Object value) {
    return value == null ? null : ((Enum<?>) value).name();
  }
}
