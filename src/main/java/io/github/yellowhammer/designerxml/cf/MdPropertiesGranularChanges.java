/*
 * This file is a part of md-sparrow.
 *
 * Copyright (c) 2026
 * Ivan Karlo <i.karlo@outlook.com> and contributors
 *
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package io.github.yellowhammer.designerxml.cf;

import java.util.List;
import java.util.Objects;

/**
 * Накопление точечных замен прямых дочерних элементов {@code Properties}: по строке на свойство.
 * Замена добавляется, только если значение изменилось.
 */
final class MdPropertiesGranularChanges {

  private final List<MdObjectPropertiesLeafDiff.GranularPatchChange> out;

  MdPropertiesGranularChanges(List<MdObjectPropertiesLeafDiff.GranularPatchChange> out) {
    this.out = out;
  }

  void text(String localName, String baseline, String incoming) {
    if (!Objects.equals(baseline, incoming)) {
      add(localName, MdCatalogPropertiesGranularSerial.textElement(
        localName, MdCatalogPropertiesGranularSerial.nz(incoming)));
    }
  }

  /** Значение — имя Java-константы ({@code AUTO}), в XML уходит как {@code Auto}. */
  void enumText(String localName, String baseline, String incoming) {
    if (!Objects.equals(baseline, incoming)) {
      add(localName, MdCatalogPropertiesGranularSerial.enumTextElement(
        localName, MdCatalogPropertiesGranularSerial.nz(incoming)));
    }
  }

  void bool(String localName, boolean baseline, boolean incoming) {
    if (baseline != incoming) {
      add(localName, MdCatalogPropertiesGranularSerial.boolElement(localName, incoming));
    }
  }

  void localStringRu(String localName, String baseline, String incoming) {
    if (!Objects.equals(baseline, incoming)) {
      add(localName, MdCatalogPropertiesGranularSerial.localStringRuElement(localName, incoming));
    }
  }

  void refs(String localName, List<String> baseline, List<String> incoming) {
    if (!MdObjectPropertiesDiff.listStringEquals(baseline, incoming)) {
      add(localName, MdCatalogPropertiesGranularSerial.mdListTypeRefsElement(localName, incoming));
    }
  }

  /** Поддерево, которое расширение не разбирает: пишем как есть либо пустым элементом. */
  void xmlBlob(String localName, String baseline, String incoming) {
    if (MdObjectPropertiesDiff.looseXmlBlobEquals(baseline, incoming)) {
      return;
    }
    String xml = incoming == null ? "" : incoming.trim();
    add(localName, xml.isEmpty() ? "<" + localName + "/>" : xml);
  }

  private void add(String localName, String elementXml) {
    out.add(MdObjectPropertiesLeafDiff.GranularPatchChange.objectProperty(localName, elementXml));
  }
}
