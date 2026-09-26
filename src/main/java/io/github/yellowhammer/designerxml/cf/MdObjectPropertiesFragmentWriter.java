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

import io.github.yellowhammer.designerxml.DesignerXml;
import io.github.yellowhammer.designerxml.SchemaVersion;
import io.github.yellowhammer.designerxml.WriteOptions;
import io.github.yellowhammer.designerxml.reflect.JaxbReflect;

import jakarta.xml.bind.JAXBElement;
import jakarta.xml.bind.JAXBException;

import javax.xml.namespace.QName;

import java.util.Map;

/**
 * Маршалинг только {@code Properties} или {@code ChildObjects} объекта метаданных (фрагмент XML).
 * Версионно-нейтрально через {@link JaxbReflect} (структура одинакова во всех версиях).
 */
public final class MdObjectPropertiesFragmentWriter {

  private static final QName Q_PROPERTIES = new QName(MdObjectXmlRegions.MD_CLASSES, "Properties");
  private static final QName Q_CHILD_OBJECTS = new QName(MdObjectXmlRegions.MD_CLASSES, "ChildObjects");

  private static final Map<String, String> KIND_TO_GETTER = Map.of(
    "catalog", "getCatalog",
    "document", "getDocument",
    "exchangePlan", "getExchangePlan",
    "subsystem", "getSubsystem");

  private MdObjectPropertiesFragmentWriter() {
  }

  private static WriteOptions fragmentOptions() {
    return WriteOptions.builder().formatPretty(true).oneCNamespacePrefixes(true).build();
  }

  public static byte[] marshalPropertiesFragment(SchemaVersion version, JAXBElement<?> je, String kind)
    throws JAXBException {
    Object node = objectNode(je, kind);
    Object props = JaxbReflect.get(node, "getProperties");
    if (props == null) {
      throw new IllegalArgumentException(kind + ".Properties is null");
    }
    return DesignerXml.marshalFragment(version, fragmentElement(Q_PROPERTIES, props), fragmentOptions());
  }

  public static byte[] marshalChildObjectsFragment(SchemaVersion version, JAXBElement<?> je, String kind)
    throws JAXBException {
    Object node = objectNode(je, kind);
    Object child = JaxbReflect.get(node, "getChildObjects");
    if (child == null) {
      throw new IllegalArgumentException(capitalize(kind) + ".ChildObjects is null");
    }
    return DesignerXml.marshalFragment(version, fragmentElement(Q_CHILD_OBJECTS, child), fragmentOptions());
  }

  private static Object objectNode(JAXBElement<?> je, String kind) {
    String getter = KIND_TO_GETTER.get(kind);
    if (getter == null) {
      throw new IllegalArgumentException("unknown kind: " + kind);
    }
    Object node = JaxbReflect.get(je.getValue(), getter);
    if (node == null) {
      throw new IllegalArgumentException("MetaDataObject is not a " + kind);
    }
    return node;
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private static JAXBElement<?> fragmentElement(QName q, Object value) {
    return new JAXBElement(q, value.getClass(), value);
  }

  private static String capitalize(String s) {
    return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
  }
}
