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

import javax.xml.stream.XMLStreamException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Точечная замена регионов {@code Properties} / {@code ChildObjects} в исходной UTF-8 строке.
 */
public final class MdObjectPropertiesSplice {

  private record Replacement(int start, int end, String text) {
  }

  private MdObjectPropertiesSplice() {
  }

  /**
   * @return результат UTF-8 или пусто, если регионы не найдены или сборка/проверка не удалась
   */
  public static Optional<byte[]> trySplice(
    String originalXmlUtf8,
    SchemaVersion version,
    JAXBElement<?> rootAfterApply,
    MdObjectPropertiesDto incoming,
    MdObjectPropertiesDiff.ChangeMask mask) {
    if (!mask.propertiesRegion() && !mask.childObjectsRegion()) {
      return Optional.empty();
    }
    String container = containerForKind(incoming.kind);
    if (container.isEmpty()) {
      return Optional.empty();
    }
    try {
      List<Replacement> reps = new ArrayList<>();
      if (mask.propertiesRegion()) {
        MdObjectXmlRegions.Region reg = MdObjectXmlRegions.findPropertiesRegion(originalXmlUtf8, container);
        if (!reg.isValid()) {
          return Optional.empty();
        }
        byte[] frag = MdObjectPropertiesFragmentWriter.marshalPropertiesFragment(version, rootAfterApply, incoming.kind);
        reps.add(new Replacement(reg.start(), reg.end(), new String(frag, StandardCharsets.UTF_8)));
      }
      if (mask.childObjectsRegion()) {
        MdObjectXmlRegions.Region reg = MdObjectXmlRegions.findChildObjectsRegion(originalXmlUtf8, container);
        if (!reg.isValid()) {
          return Optional.empty();
        }
        byte[] frag = MdObjectPropertiesFragmentWriter.marshalChildObjectsFragment(version, rootAfterApply, incoming.kind);
        reps.add(new Replacement(reg.start(), reg.end(), new String(frag, StandardCharsets.UTF_8)));
      }
      reps.sort(Comparator.comparingInt(Replacement::start).reversed());
      String result = originalXmlUtf8;
      for (Replacement r : reps) {
        if (r.start() < 0 || r.end() > result.length() || r.start() > r.end()) {
          return Optional.empty();
        }
        result = result.substring(0, r.start()) + r.text() + result.substring(r.end());
      }
      return Optional.of(result.getBytes(StandardCharsets.UTF_8));
    } catch (XMLStreamException | JAXBException | IllegalArgumentException e) {
      return Optional.empty();
    }
  }

  private static String containerForKind(String kind) {
    if (kind == null) {
      return "";
    }
    return switch (kind) {
      case "catalog" -> "Catalog";
      case "document" -> "Document";
      case "exchangePlan" -> "ExchangePlan";
      case "subsystem" -> "Subsystem";
      default -> "";
    };
  }
}
