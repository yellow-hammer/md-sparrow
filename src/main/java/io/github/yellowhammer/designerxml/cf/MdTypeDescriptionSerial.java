/*
 * This file is a part of md-sparrow.
 *
 * Copyright (c) 2026
 * Ivan Karlo <i.karlo@outlook.com> and contributors
 *
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package io.github.yellowhammer.designerxml.cf;

/**
 * Сериализация {@code v8:TypeDescription} для точечной замены в XML.
 *
 * <p>Элемент собирается в одну строку: отступы расставит {@link MdObjectPropertiesGranularPatch}
 * по месту замены. Пространство имён типа объявляем прямо на элементе {@code v8:Type} — так же
 * пишет конфигуратор, и замена не зависит от объявлений в корне файла.
 */
final class MdTypeDescriptionSerial {

  private MdTypeDescriptionSerial() {
  }

  /**
   * @param localName имя элемента-обёртки ({@code Type})
   */
  static String typeElement(String localName, MdTypeDescriptionDto dto) {
    if (dto == null || dto.types.isEmpty()) {
      return "<" + localName + "/>";
    }
    StringBuilder sb = new StringBuilder();
    sb.append('<').append(localName).append('>');
    for (String type : dto.types) {
      sb.append(typeTag(type));
    }
    if (dto.stringQualifiers != null) {
      sb.append("<v8:StringQualifiers>")
        .append(leaf("v8:Length", nz(dto.stringQualifiers.length)))
        .append(enumLeaf("v8:AllowedLength", dto.stringQualifiers.allowedLength))
        .append("</v8:StringQualifiers>");
    }
    if (dto.numberQualifiers != null) {
      sb.append("<v8:NumberQualifiers>")
        .append(leaf("v8:Digits", nz(dto.numberQualifiers.digits)))
        .append(leaf("v8:FractionDigits", nz(dto.numberQualifiers.fractionDigits)))
        .append(enumLeaf("v8:AllowedSign", dto.numberQualifiers.allowedSign))
        .append("</v8:NumberQualifiers>");
    }
    if (dto.dateQualifiers != null) {
      sb.append("<v8:DateQualifiers>")
        .append(enumLeaf("v8:DateFractions", dto.dateQualifiers.dateFractions))
        .append("</v8:DateQualifiers>");
    }
    if (dto.binaryDataQualifiers != null) {
      sb.append("<v8:BinaryDataQualifiers>")
        .append(leaf("v8:Length", nz(dto.binaryDataQualifiers.length)))
        .append(enumLeaf("v8:AllowedLength", dto.binaryDataQualifiers.allowedLength))
        .append("</v8:BinaryDataQualifiers>");
    }
    sb.append("</").append(localName).append('>');
    return sb.toString();
  }

  private static String typeTag(String type) {
    String value = type == null ? "" : type.trim();
    int colon = value.indexOf(':');
    if (colon < 0) {
      return "<v8:Type>" + escape(value) + "</v8:Type>";
    }
    String prefix = value.substring(0, colon);
    String namespace = MdTypeDescriptionBridge.namespaceForPrefix(prefix);
    if (namespace.isEmpty()) {
      throw new IllegalArgumentException("неизвестный префикс типа: " + prefix);
    }
    return "<v8:Type xmlns:" + prefix + "=\"" + namespace + "\">" + escape(value) + "</v8:Type>";
  }

  private static String leaf(String tag, String text) {
    return "<" + tag + ">" + escape(text) + "</" + tag + ">";
  }

  private static String enumLeaf(String tag, String constantName) {
    if (constantName == null || constantName.isBlank()) {
      return "";
    }
    return leaf(tag, MdCatalogPropertiesGranularSerial.enumConstantToXmlText(constantName));
  }

  private static String nz(String value) {
    return value == null || value.isBlank() ? "0" : value;
  }

  private static String escape(String s) {
    return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
  }
}
