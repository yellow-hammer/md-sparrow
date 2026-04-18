/*
 * This file is a part of md-sparrow.
 *
 * Copyright (c) 2026
 * Ivan Karlo <i.karlo@outlook.com> and contributors
 *
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package io.github.yellowhammer.designerxml.cf;

import io.github.yellowhammer.designerxml.DesignerXml;
import io.github.yellowhammer.designerxml.SchemaVersion;
import io.github.yellowhammer.designerxml.WriteOptions;

import jakarta.xml.bind.JAXBElement;
import jakarta.xml.bind.JAXBException;

import javax.xml.namespace.QName;

/**
 * Маршалинг только {@code Properties} или {@code ChildObjects} объекта метаданных (фрагмент XML).
 */
public final class MdObjectPropertiesFragmentWriter {

  private static final QName Q_PROPERTIES = new QName(MdObjectXmlRegions.MD_CLASSES, "Properties");
  private static final QName Q_CHILD_OBJECTS = new QName(MdObjectXmlRegions.MD_CLASSES, "ChildObjects");

  private MdObjectPropertiesFragmentWriter() {
  }

  private static WriteOptions fragmentOptions() {
    return WriteOptions.builder().formatPretty(true).oneCNamespacePrefixes(true).build();
  }

  public static byte[] marshalPropertiesFragment(SchemaVersion version, JAXBElement<?> je, String kind)
    throws JAXBException {
    WriteOptions opt = fragmentOptions();
    return switch (version) {
      case V2_20 -> marshalPropertiesV20(je, kind, opt);
      case V2_21 -> marshalPropertiesV21(je, kind, opt);
    };
  }

  public static byte[] marshalChildObjectsFragment(SchemaVersion version, JAXBElement<?> je, String kind)
    throws JAXBException {
    WriteOptions opt = fragmentOptions();
    return switch (version) {
      case V2_20 -> marshalChildObjectsV20(je, kind, opt);
      case V2_21 -> marshalChildObjectsV21(je, kind, opt);
    };
  }

  private static byte[] marshalPropertiesV20(JAXBElement<?> je, String kind, WriteOptions opt) throws JAXBException {
    var mdo = (io.github.yellowhammer.designerxml.jaxb.v2_20.mdclasses.MetaDataObject) je.getValue();
    return switch (kind) {
      case "catalog" -> {
        var p = mdo.getCatalog().getProperties();
        var el = new JAXBElement<>(Q_PROPERTIES,
          io.github.yellowhammer.designerxml.jaxb.v2_20.mdclasses.CatalogProperties.class, p);
        yield DesignerXml.marshalFragment(SchemaVersion.V2_20, el, opt);
      }
      case "document" -> {
        var p = mdo.getDocument().getProperties();
        var el = new JAXBElement<>(Q_PROPERTIES,
          io.github.yellowhammer.designerxml.jaxb.v2_20.mdclasses.DocumentProperties.class, p);
        yield DesignerXml.marshalFragment(SchemaVersion.V2_20, el, opt);
      }
      case "exchangePlan" -> {
        var p = mdo.getExchangePlan().getProperties();
        var el = new JAXBElement<>(Q_PROPERTIES,
          io.github.yellowhammer.designerxml.jaxb.v2_20.mdclasses.ExchangePlanProperties.class, p);
        yield DesignerXml.marshalFragment(SchemaVersion.V2_20, el, opt);
      }
      case "subsystem" -> {
        var p = mdo.getSubsystem().getProperties();
        var el = new JAXBElement<>(Q_PROPERTIES,
          io.github.yellowhammer.designerxml.jaxb.v2_20.mdclasses.SubsystemProperties.class, p);
        yield DesignerXml.marshalFragment(SchemaVersion.V2_20, el, opt);
      }
      default -> throw new IllegalArgumentException("unknown kind: " + kind);
    };
  }

  private static byte[] marshalPropertiesV21(JAXBElement<?> je, String kind, WriteOptions opt) throws JAXBException {
    var mdo = (io.github.yellowhammer.designerxml.jaxb.v2_21.mdclasses.MetaDataObject) je.getValue();
    return switch (kind) {
      case "catalog" -> {
        var p = mdo.getCatalog().getProperties();
        var el = new JAXBElement<>(Q_PROPERTIES,
          io.github.yellowhammer.designerxml.jaxb.v2_21.mdclasses.CatalogProperties.class, p);
        yield DesignerXml.marshalFragment(SchemaVersion.V2_21, el, opt);
      }
      case "document" -> {
        var p = mdo.getDocument().getProperties();
        var el = new JAXBElement<>(Q_PROPERTIES,
          io.github.yellowhammer.designerxml.jaxb.v2_21.mdclasses.DocumentProperties.class, p);
        yield DesignerXml.marshalFragment(SchemaVersion.V2_21, el, opt);
      }
      case "exchangePlan" -> {
        var p = mdo.getExchangePlan().getProperties();
        var el = new JAXBElement<>(Q_PROPERTIES,
          io.github.yellowhammer.designerxml.jaxb.v2_21.mdclasses.ExchangePlanProperties.class, p);
        yield DesignerXml.marshalFragment(SchemaVersion.V2_21, el, opt);
      }
      case "subsystem" -> {
        var p = mdo.getSubsystem().getProperties();
        var el = new JAXBElement<>(Q_PROPERTIES,
          io.github.yellowhammer.designerxml.jaxb.v2_21.mdclasses.SubsystemProperties.class, p);
        yield DesignerXml.marshalFragment(SchemaVersion.V2_21, el, opt);
      }
      default -> throw new IllegalArgumentException("unknown kind: " + kind);
    };
  }

  private static byte[] marshalChildObjectsV20(JAXBElement<?> je, String kind, WriteOptions opt) throws JAXBException {
    var mdo = (io.github.yellowhammer.designerxml.jaxb.v2_20.mdclasses.MetaDataObject) je.getValue();
    return switch (kind) {
      case "catalog" -> {
        var co = mdo.getCatalog().getChildObjects();
        if (co == null) {
          throw new IllegalArgumentException("Catalog.ChildObjects is null");
        }
        var el = new JAXBElement<>(Q_CHILD_OBJECTS,
          io.github.yellowhammer.designerxml.jaxb.v2_20.mdclasses.CatalogChildObjects.class, co);
        yield DesignerXml.marshalFragment(SchemaVersion.V2_20, el, opt);
      }
      case "document" -> {
        var co = mdo.getDocument().getChildObjects();
        if (co == null) {
          throw new IllegalArgumentException("Document.ChildObjects is null");
        }
        var el = new JAXBElement<>(Q_CHILD_OBJECTS,
          io.github.yellowhammer.designerxml.jaxb.v2_20.mdclasses.DocumentChildObjects.class, co);
        yield DesignerXml.marshalFragment(SchemaVersion.V2_20, el, opt);
      }
      case "exchangePlan" -> {
        var co = mdo.getExchangePlan().getChildObjects();
        if (co == null) {
          throw new IllegalArgumentException("ExchangePlan.ChildObjects is null");
        }
        var el = new JAXBElement<>(Q_CHILD_OBJECTS,
          io.github.yellowhammer.designerxml.jaxb.v2_20.mdclasses.ExchangePlanChildObjects.class, co);
        yield DesignerXml.marshalFragment(SchemaVersion.V2_20, el, opt);
      }
      case "subsystem" -> {
        var co = mdo.getSubsystem().getChildObjects();
        if (co == null) {
          throw new IllegalArgumentException("Subsystem.ChildObjects is null");
        }
        var el = new JAXBElement<>(Q_CHILD_OBJECTS,
          io.github.yellowhammer.designerxml.jaxb.v2_20.mdclasses.SubsystemChildObjects.class, co);
        yield DesignerXml.marshalFragment(SchemaVersion.V2_20, el, opt);
      }
      default -> throw new IllegalArgumentException("unknown kind: " + kind);
    };
  }

  private static byte[] marshalChildObjectsV21(JAXBElement<?> je, String kind, WriteOptions opt) throws JAXBException {
    var mdo = (io.github.yellowhammer.designerxml.jaxb.v2_21.mdclasses.MetaDataObject) je.getValue();
    return switch (kind) {
      case "catalog" -> {
        var co = mdo.getCatalog().getChildObjects();
        if (co == null) {
          throw new IllegalArgumentException("Catalog.ChildObjects is null");
        }
        var el = new JAXBElement<>(Q_CHILD_OBJECTS,
          io.github.yellowhammer.designerxml.jaxb.v2_21.mdclasses.CatalogChildObjects.class, co);
        yield DesignerXml.marshalFragment(SchemaVersion.V2_21, el, opt);
      }
      case "document" -> {
        var co = mdo.getDocument().getChildObjects();
        if (co == null) {
          throw new IllegalArgumentException("Document.ChildObjects is null");
        }
        var el = new JAXBElement<>(Q_CHILD_OBJECTS,
          io.github.yellowhammer.designerxml.jaxb.v2_21.mdclasses.DocumentChildObjects.class, co);
        yield DesignerXml.marshalFragment(SchemaVersion.V2_21, el, opt);
      }
      case "exchangePlan" -> {
        var co = mdo.getExchangePlan().getChildObjects();
        if (co == null) {
          throw new IllegalArgumentException("ExchangePlan.ChildObjects is null");
        }
        var el = new JAXBElement<>(Q_CHILD_OBJECTS,
          io.github.yellowhammer.designerxml.jaxb.v2_21.mdclasses.ExchangePlanChildObjects.class, co);
        yield DesignerXml.marshalFragment(SchemaVersion.V2_21, el, opt);
      }
      case "subsystem" -> {
        var co = mdo.getSubsystem().getChildObjects();
        if (co == null) {
          throw new IllegalArgumentException("Subsystem.ChildObjects is null");
        }
        var el = new JAXBElement<>(Q_CHILD_OBJECTS,
          io.github.yellowhammer.designerxml.jaxb.v2_21.mdclasses.SubsystemChildObjects.class, co);
        yield DesignerXml.marshalFragment(SchemaVersion.V2_21, el, opt);
      }
      default -> throw new IllegalArgumentException("unknown kind: " + kind);
    };
  }
}
