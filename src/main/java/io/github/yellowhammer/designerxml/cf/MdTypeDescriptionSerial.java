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

import java.util.List;

/**
 * Сериализация {@code v8:TypeDescription} для точечной замены в XML.
 *
 * <p>Элемент собирается в одну строку: отступы расставит {@link MdObjectPropertiesGranularPatch}
 * по месту замены. Пространство имён типа объявляем прямо на элементе {@code v8:Type} ({@code v8:TypeSet}) —
 * так же пишет конфигуратор, и замена не зависит от объявлений в корне файла.
 */
final class MdTypeDescriptionSerial {

  private MdTypeDescriptionSerial() {
  }

  /**
   * Типы ({@code v8:Type}), затем наборы типов ({@code v8:TypeSet}, источник подписки вида
   * {@code cfg:CatalogObject}) и квалификаторы в порядке схемы.
   *
   * @param localName имя элемента-обёртки ({@code Type}, {@code Source})
   */
  static String typeElement(String localName, MdTypeDescriptionDto dto) {
    List<String> types = dto == null || dto.types == null ? List.of() : dto.types;
    List<String> typeSets = dto == null || dto.typeSets == null ? List.of() : dto.typeSets;
    if (types.isEmpty() && typeSets.isEmpty()) {
      return "<" + localName + "/>";
    }
    StringBuilder sb = new StringBuilder();
    sb.append('<').append(localName).append('>');
    for (String type : types) {
      sb.append(typeTag("v8:Type", type));
    }
    for (String typeSet : typeSets) {
      sb.append(typeTag("v8:TypeSet", typeSet));
    }
    // Порядок квалификаторов задан схемой: число, строка, дата, двоичные данные
    if (dto.numberQualifiers != null) {
      sb.append("<v8:NumberQualifiers>")
        .append(leaf("v8:Digits", nz(dto.numberQualifiers.digits)))
        .append(leaf("v8:FractionDigits", nz(dto.numberQualifiers.fractionDigits)))
        .append(enumLeaf("v8:AllowedSign", dto.numberQualifiers.allowedSign))
        .append("</v8:NumberQualifiers>");
    }
    if (dto.stringQualifiers != null) {
      sb.append("<v8:StringQualifiers>")
        .append(leaf("v8:Length", nz(dto.stringQualifiers.length)))
        .append(enumLeaf("v8:AllowedLength", dto.stringQualifiers.allowedLength))
        .append("</v8:StringQualifiers>");
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

  private static String typeTag(String tag, String type) {
    String value = type == null ? "" : type.trim();
    int colon = value.indexOf(':');
    if (colon < 0) {
      return "<" + tag + ">" + escape(value) + "</" + tag + ">";
    }
    String prefix = value.substring(0, colon);
    String namespace = MdTypeDescriptionBridge.namespaceForPrefix(prefix);
    if (namespace.isEmpty()) {
      throw new IllegalArgumentException("неизвестный префикс типа: " + prefix);
    }
    return "<" + tag + " xmlns:" + prefix + "=\"" + namespace + "\">" + escape(value) + "</" + tag + ">";
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
