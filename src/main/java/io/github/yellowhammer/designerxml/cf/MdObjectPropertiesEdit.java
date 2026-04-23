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

import jakarta.xml.bind.JAXBElement;
import jakarta.xml.bind.JAXBException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Чтение и запись расширенного DTO свойств объектов метаданных (справочник, документ, подсистема, план обмена) через JAXB.
 */
public final class MdObjectPropertiesEdit {

  private static final List<SimpleKindDef> SIMPLE_KINDS = List.of(
    new SimpleKindDef("constant", "getConstant"),
    new SimpleKindDef("enum", "getEnum"),
    new SimpleKindDef("report", "getReport"),
    new SimpleKindDef("dataProcessor", "getDataProcessor"),
    new SimpleKindDef("task", "getTask"),
    new SimpleKindDef("chartOfAccounts", "getChartOfAccounts"),
    new SimpleKindDef("chartOfCharacteristicTypes", "getChartOfCharacteristicTypes"),
    new SimpleKindDef("chartOfCalculationTypes", "getChartOfCalculationTypes"),
    new SimpleKindDef("commonModule", "getCommonModule"),
    new SimpleKindDef("sessionParameter", "getSessionParameter"),
    new SimpleKindDef("commonAttribute", "getCommonAttribute"),
    new SimpleKindDef("commonPicture", "getCommonPicture"),
    new SimpleKindDef("documentNumerator", "getDocumentNumerator"),
    new SimpleKindDef("externalDataSource", "getExternalDataSource"),
    new SimpleKindDef("role", "getRole"),
    new SimpleKindDef("eventSubscription", "getEventSubscription"),
    new SimpleKindDef("scheduledJob", "getScheduledJob"),
    new SimpleKindDef("commonCommand", "getCommonCommand")
  );

  private MdObjectPropertiesEdit() {
  }

  public static MdObjectPropertiesDto readDto(Path objectXml, SchemaVersion version) throws IOException, JAXBException {
    if (!Files.isRegularFile(objectXml)) {
      throw new IllegalArgumentException("file not found: " + objectXml);
    }
    Object root = DesignerXml.read(objectXml, version);
    if (!(root instanceof JAXBElement<?> je)) {
      throw new IllegalArgumentException("expected JAXBElement root");
    }
    return switch (version) {
      case V2_20 -> readDtoV20(je);
      case V2_21 -> readDtoV21(je);
    };
  }

  /**
   * Чтение DTO из UTF-8 байтов (проверка после точечной записи).
   */
  public static MdObjectPropertiesDto readDto(byte[] utf8Xml, SchemaVersion version) throws JAXBException {
    Object root = DesignerXml.unmarshal(version, new ByteArrayInputStream(utf8Xml));
    if (!(root instanceof JAXBElement<?> je)) {
      throw new IllegalArgumentException("expected JAXBElement root");
    }
    return switch (version) {
      case V2_20 -> readDtoV20(je);
      case V2_21 -> readDtoV21(je);
    };
  }

  public static void writeDto(Path objectXml, SchemaVersion version, MdObjectPropertiesDto dto)
    throws IOException, JAXBException {
    if (dto == null || dto.kind == null || dto.internalName == null || dto.internalName.isEmpty()) {
      throw new IllegalArgumentException("kind and internalName required");
    }
    if (!Files.isRegularFile(objectXml)) {
      throw new IllegalArgumentException("file not found: " + objectXml);
    }
    MdObjectPropertiesDto baseline = readDto(objectXml, version);
    MdObjectPropertiesJsonCoalesce.coalesceFromBaseline(dto, baseline);
    if (dto.attributes == null) {
      dto.attributes = new ArrayList<>();
    }
    if (dto.tabularSections == null) {
      dto.tabularSections = new ArrayList<>();
    }
    if (dto.nestedSubsystems == null) {
      dto.nestedSubsystems = new ArrayList<>();
    }
    if (dto.contentRefs == null) {
      dto.contentRefs = new ArrayList<>();
    }
    if (MdObjectPropertiesDiff.equalsDto(baseline, dto)) {
      return;
    }
    checkStemMatches(objectXml, dto.internalName);
    String xml = Files.readString(objectXml, StandardCharsets.UTF_8);
    String container = MdObjectPropertiesGranularPatch.containerLocalForKind(dto.kind);
    if (!container.isEmpty()) {
      Optional<byte[]> granular = MdObjectPropertiesGranularPatch.tryApply(xml, container, version, baseline, dto);
      if (granular.isPresent()) {
        Files.write(objectXml, granular.get());
        return;
      }
    }
    Object root = DesignerXml.read(objectXml, version);
    if (!(root instanceof JAXBElement<?> je)) {
      throw new IllegalArgumentException("expected JAXBElement root");
    }
    switch (version) {
      case V2_20 -> applyDtoV20(je, dto);
      case V2_21 -> applyDtoV21(je, dto);
    }
    MdObjectPropertiesDiff.ChangeMask mask = MdObjectPropertiesDiff.computeChangeMask(baseline, dto);
    Optional<byte[]> spliced = MdObjectPropertiesSplice.trySplice(xml, version, je, dto, mask);
    if (spliced.isPresent()) {
      MdObjectPropertiesDto verified = readDto(spliced.get(), version);
      if (MdObjectPropertiesDiff.equalsDto(verified, dto, true)
        || MdObjectPropertiesDiff.equalsDtoLenientJson(verified, dto)
        || MdObjectPropertiesDiff.matchesAfterSpliceStructural(verified, dto)) {
        Files.write(objectXml, spliced.get());
        return;
      }
    }
    Optional<String> granularReason = MdObjectPropertiesGranularPatch.describeFirstUnpatchableChange(
      xml,
      container,
      baseline,
      dto);
    String reason = granularReason.orElse("причина не определена");
    throw new IllegalStateException(
      "Не удалось применить изменения точечно (" + reason + "). "
        + "Полная пересборка XML через JAXB предотвращена.");
  }

  private static void checkStemMatches(Path objectXml, String internalName) {
    String fn = objectXml.getFileName().toString();
    if (!fn.endsWith(".xml")) {
      throw new IllegalArgumentException("expected .xml file");
    }
    String stem = fn.substring(0, fn.length() - 4);
    if (!stem.equals(internalName)) {
      throw new IllegalArgumentException("file name must match internal name: " + stem + " vs " + internalName);
    }
  }

  private static MdObjectPropertiesDto readDtoV20(JAXBElement<?> je) {
    var mdo = (io.github.yellowhammer.designerxml.jaxb.v2_20.mdclasses.MetaDataObject) je.getValue();
    if (mdo.getCatalog() != null) {
      return readCatalogV20(mdo.getCatalog());
    }
    if (mdo.getDocument() != null) {
      return readDocumentV20(mdo.getDocument());
    }
    if (mdo.getSubsystem() != null) {
      return readSubsystemV20(mdo.getSubsystem());
    }
    if (mdo.getExchangePlan() != null) {
      return readExchangePlanV20(mdo.getExchangePlan());
    }
    MdObjectPropertiesDto simple = tryReadSimpleKind(mdo);
    if (simple != null) {
      return simple;
    }
    throw new IllegalArgumentException("unsupported MetaDataObject for cf-md-object");
  }

  private static MdObjectPropertiesDto readDtoV21(JAXBElement<?> je) {
    var mdo = (io.github.yellowhammer.designerxml.jaxb.v2_21.mdclasses.MetaDataObject) je.getValue();
    if (mdo.getCatalog() != null) {
      return readCatalogV21(mdo.getCatalog());
    }
    if (mdo.getDocument() != null) {
      return readDocumentV21(mdo.getDocument());
    }
    if (mdo.getSubsystem() != null) {
      return readSubsystemV21(mdo.getSubsystem());
    }
    if (mdo.getExchangePlan() != null) {
      return readExchangePlanV21(mdo.getExchangePlan());
    }
    MdObjectPropertiesDto simple = tryReadSimpleKind(mdo);
    if (simple != null) {
      return simple;
    }
    throw new IllegalArgumentException("unsupported MetaDataObject for cf-md-object");
  }

  private static MdObjectPropertiesDto readCatalogV20(
    io.github.yellowhammer.designerxml.jaxb.v2_20.mdclasses.Catalog cat) {
    MdObjectPropertiesDto dto = baseCatalogLikeV20(cat.getProperties());
    dto.kind = "catalog";
    MdCatalogPropertiesBridge.readV20(cat.getProperties(), dto);
    var co = cat.getChildObjects();
    if (co != null) {
      for (var a : co.getAttribute()) {
        dto.attributes.add(attrDtoV20(a));
      }
      for (var ts : co.getTabularSection()) {
        dto.tabularSections.add(tsDtoV20(ts));
      }
    }
    return dto;
  }

  private static MdObjectPropertiesDto readCatalogV21(
    io.github.yellowhammer.designerxml.jaxb.v2_21.mdclasses.Catalog cat) {
    MdObjectPropertiesDto dto = baseCatalogLikeV21(cat.getProperties());
    dto.kind = "catalog";
    MdCatalogPropertiesBridge.readV21(cat.getProperties(), dto);
    var co = cat.getChildObjects();
    if (co != null) {
      for (var a : co.getAttribute()) {
        dto.attributes.add(attrDtoV21(a));
      }
      for (var ts : co.getTabularSection()) {
        dto.tabularSections.add(tsDtoV21(ts));
      }
    }
    return dto;
  }

  private static MdObjectPropertiesDto readDocumentV20(
    io.github.yellowhammer.designerxml.jaxb.v2_20.mdclasses.Document doc) {
    MdObjectPropertiesDto dto = baseCatalogLikeV20(doc.getProperties());
    dto.kind = "document";
    var co = doc.getChildObjects();
    if (co != null) {
      for (var a : co.getAttribute()) {
        dto.attributes.add(attrDtoV20(a));
      }
      for (var ts : co.getTabularSection()) {
        dto.tabularSections.add(tsDtoV20(ts));
      }
    }
    return dto;
  }

  private static MdObjectPropertiesDto readDocumentV21(
    io.github.yellowhammer.designerxml.jaxb.v2_21.mdclasses.Document doc) {
    MdObjectPropertiesDto dto = baseCatalogLikeV21(doc.getProperties());
    dto.kind = "document";
    var co = doc.getChildObjects();
    if (co != null) {
      for (var a : co.getAttribute()) {
        dto.attributes.add(attrDtoV21(a));
      }
      for (var ts : co.getTabularSection()) {
        dto.tabularSections.add(tsDtoV21(ts));
      }
    }
    return dto;
  }

  private static MdObjectPropertiesDto readExchangePlanV20(
    io.github.yellowhammer.designerxml.jaxb.v2_20.mdclasses.ExchangePlan ep) {
    MdObjectPropertiesDto dto = baseCatalogLikeV20(ep.getProperties());
    dto.kind = "exchangePlan";
    var co = ep.getChildObjects();
    if (co != null) {
      for (var a : co.getAttribute()) {
        dto.attributes.add(attrDtoV20(a));
      }
      for (var ts : co.getTabularSection()) {
        dto.tabularSections.add(tsDtoV20(ts));
      }
    }
    return dto;
  }

  private static MdObjectPropertiesDto readExchangePlanV21(
    io.github.yellowhammer.designerxml.jaxb.v2_21.mdclasses.ExchangePlan ep) {
    MdObjectPropertiesDto dto = baseCatalogLikeV21(ep.getProperties());
    dto.kind = "exchangePlan";
    var co = ep.getChildObjects();
    if (co != null) {
      for (var a : co.getAttribute()) {
        dto.attributes.add(attrDtoV21(a));
      }
      for (var ts : co.getTabularSection()) {
        dto.tabularSections.add(tsDtoV21(ts));
      }
    }
    return dto;
  }

  private static MdObjectPropertiesDto baseCatalogLikeV20(
    io.github.yellowhammer.designerxml.jaxb.v2_20.mdclasses.CatalogProperties props) {
    MdObjectPropertiesDto dto = new MdObjectPropertiesDto();
    dto.internalName = props.getName();
    dto.synonymRu = LocalStringSync.firstRuV20(props.getSynonym());
    dto.comment = props.getComment() == null ? "" : props.getComment();
    return dto;
  }

  private static MdObjectPropertiesDto baseCatalogLikeV20(
    io.github.yellowhammer.designerxml.jaxb.v2_20.mdclasses.DocumentProperties props) {
    MdObjectPropertiesDto dto = new MdObjectPropertiesDto();
    dto.internalName = props.getName();
    dto.synonymRu = LocalStringSync.firstRuV20(props.getSynonym());
    dto.comment = props.getComment() == null ? "" : props.getComment();
    return dto;
  }

  private static MdObjectPropertiesDto baseCatalogLikeV20(
    io.github.yellowhammer.designerxml.jaxb.v2_20.mdclasses.ExchangePlanProperties props) {
    MdObjectPropertiesDto dto = new MdObjectPropertiesDto();
    dto.internalName = props.getName();
    dto.synonymRu = LocalStringSync.firstRuV20(props.getSynonym());
    dto.comment = props.getComment() == null ? "" : props.getComment();
    return dto;
  }

  private static MdObjectPropertiesDto baseCatalogLikeV21(
    io.github.yellowhammer.designerxml.jaxb.v2_21.mdclasses.CatalogProperties props) {
    MdObjectPropertiesDto dto = new MdObjectPropertiesDto();
    dto.internalName = props.getName();
    dto.synonymRu = LocalStringSync.firstRuV21(props.getSynonym());
    dto.comment = props.getComment() == null ? "" : props.getComment();
    return dto;
  }

  private static MdObjectPropertiesDto baseCatalogLikeV21(
    io.github.yellowhammer.designerxml.jaxb.v2_21.mdclasses.DocumentProperties props) {
    MdObjectPropertiesDto dto = new MdObjectPropertiesDto();
    dto.internalName = props.getName();
    dto.synonymRu = LocalStringSync.firstRuV21(props.getSynonym());
    dto.comment = props.getComment() == null ? "" : props.getComment();
    return dto;
  }

  private static MdObjectPropertiesDto baseCatalogLikeV21(
    io.github.yellowhammer.designerxml.jaxb.v2_21.mdclasses.ExchangePlanProperties props) {
    MdObjectPropertiesDto dto = new MdObjectPropertiesDto();
    dto.internalName = props.getName();
    dto.synonymRu = LocalStringSync.firstRuV21(props.getSynonym());
    dto.comment = props.getComment() == null ? "" : props.getComment();
    return dto;
  }

  private static MdNamedPropertyDto attrDtoV20(io.github.yellowhammer.designerxml.jaxb.v2_20.mdclasses.Attribute a) {
    var p = a.getProperties();
    return new MdNamedPropertyDto(
      p.getName(),
      LocalStringSync.firstRuV20(p.getSynonym()),
      p.getComment() == null ? "" : p.getComment());
  }

  private static MdNamedPropertyDto attrDtoV21(io.github.yellowhammer.designerxml.jaxb.v2_21.mdclasses.Attribute a) {
    var p = a.getProperties();
    return new MdNamedPropertyDto(
      p.getName(),
      LocalStringSync.firstRuV21(p.getSynonym()),
      p.getComment() == null ? "" : p.getComment());
  }

  private static MdNamedPropertyDto tsDtoV20(io.github.yellowhammer.designerxml.jaxb.v2_20.mdclasses.TabularSection ts) {
    var p = ts.getProperties();
    return new MdNamedPropertyDto(
      p.getName(),
      LocalStringSync.firstRuV20(p.getSynonym()),
      p.getComment() == null ? "" : p.getComment());
  }

  private static MdNamedPropertyDto tsDtoV21(io.github.yellowhammer.designerxml.jaxb.v2_21.mdclasses.TabularSection ts) {
    var p = ts.getProperties();
    return new MdNamedPropertyDto(
      p.getName(),
      LocalStringSync.firstRuV21(p.getSynonym()),
      p.getComment() == null ? "" : p.getComment());
  }

  private static MdObjectPropertiesDto readSubsystemV20(
    io.github.yellowhammer.designerxml.jaxb.v2_20.mdclasses.Subsystem sub) {
    MdObjectPropertiesDto dto = new MdObjectPropertiesDto();
    dto.kind = "subsystem";
    var props = sub.getProperties();
    dto.internalName = props.getName();
    dto.synonymRu = LocalStringSync.firstRuV20(props.getSynonym());
    dto.comment = props.getComment() == null ? "" : props.getComment();
    if (props.getContent() != null) {
      dto.contentRefs.addAll(MdListTypeRefs.readItemTexts(props.getContent().getItem()));
    }
    var ch = sub.getChildObjects();
    if (ch != null) {
      dto.nestedSubsystems.addAll(ch.getSubsystem());
    }
    return dto;
  }

  private static MdObjectPropertiesDto readSubsystemV21(
    io.github.yellowhammer.designerxml.jaxb.v2_21.mdclasses.Subsystem sub) {
    MdObjectPropertiesDto dto = new MdObjectPropertiesDto();
    dto.kind = "subsystem";
    var props = sub.getProperties();
    dto.internalName = props.getName();
    dto.synonymRu = LocalStringSync.firstRuV21(props.getSynonym());
    dto.comment = props.getComment() == null ? "" : props.getComment();
    if (props.getContent() != null) {
      dto.contentRefs.addAll(MdListTypeRefs.readItemTexts(props.getContent().getItem()));
    }
    var ch = sub.getChildObjects();
    if (ch != null) {
      dto.nestedSubsystems.addAll(ch.getSubsystem());
    }
    return dto;
  }

  /** Для тестов: применить DTO к корню JAXB без записи файла. */
  static void applyDtoForTest(JAXBElement<?> je, SchemaVersion version, MdObjectPropertiesDto dto) {
    switch (version) {
      case V2_20 -> applyDtoV20(je, dto);
      case V2_21 -> applyDtoV21(je, dto);
    }
  }

  private static void applyDtoV20(JAXBElement<?> je, MdObjectPropertiesDto dto) {
    var mdo = (io.github.yellowhammer.designerxml.jaxb.v2_20.mdclasses.MetaDataObject) je.getValue();
    if (tryApplySimpleKind(mdo, dto)) {
      return;
    }
    switch (dto.kind) {
      case "catalog" -> {
        var cat = mdo.getCatalog();
        if (cat == null) {
          throw new IllegalArgumentException("MetaDataObject is not a Catalog");
        }
        if (dto.catalog != null) {
          MdCatalogPropertiesBridge.applyV20(cat.getProperties(), dto);
        } else {
          applyCatalogLikeV20(cat.getProperties(), dto);
        }
        applyAttrsV20(cat.getChildObjects(), dto);
      }
      case "document" -> {
        var doc = mdo.getDocument();
        if (doc == null) {
          throw new IllegalArgumentException("MetaDataObject is not a Document");
        }
        applyCatalogLikeV20(doc.getProperties(), dto);
        applyAttrsV20(doc.getChildObjects(), dto);
      }
      case "exchangePlan" -> {
        var ep = mdo.getExchangePlan();
        if (ep == null) {
          throw new IllegalArgumentException("MetaDataObject is not an ExchangePlan");
        }
        applyCatalogLikeV20(ep.getProperties(), dto);
        applyAttrsV20(ep.getChildObjects(), dto);
      }
      case "subsystem" -> {
        var sub = mdo.getSubsystem();
        if (sub == null) {
          throw new IllegalArgumentException("MetaDataObject is not a Subsystem");
        }
        applySubsystemV20(sub, dto);
      }
      default -> throw new IllegalArgumentException("unknown kind: " + dto.kind);
    }
  }

  private static void applyDtoV21(JAXBElement<?> je, MdObjectPropertiesDto dto) {
    var mdo = (io.github.yellowhammer.designerxml.jaxb.v2_21.mdclasses.MetaDataObject) je.getValue();
    if (tryApplySimpleKind(mdo, dto)) {
      return;
    }
    switch (dto.kind) {
      case "catalog" -> {
        var cat = mdo.getCatalog();
        if (cat == null) {
          throw new IllegalArgumentException("MetaDataObject is not a Catalog");
        }
        if (dto.catalog != null) {
          MdCatalogPropertiesBridge.applyV21(cat.getProperties(), dto);
        } else {
          applyCatalogLikeV21(cat.getProperties(), dto);
        }
        applyAttrsV21(cat.getChildObjects(), dto);
      }
      case "document" -> {
        var doc = mdo.getDocument();
        if (doc == null) {
          throw new IllegalArgumentException("MetaDataObject is not a Document");
        }
        applyCatalogLikeV21(doc.getProperties(), dto);
        applyAttrsV21(doc.getChildObjects(), dto);
      }
      case "exchangePlan" -> {
        var ep = mdo.getExchangePlan();
        if (ep == null) {
          throw new IllegalArgumentException("MetaDataObject is not an ExchangePlan");
        }
        applyCatalogLikeV21(ep.getProperties(), dto);
        applyAttrsV21(ep.getChildObjects(), dto);
      }
      case "subsystem" -> {
        var sub = mdo.getSubsystem();
        if (sub == null) {
          throw new IllegalArgumentException("MetaDataObject is not a Subsystem");
        }
        applySubsystemV21(sub, dto);
      }
      default -> throw new IllegalArgumentException("unknown kind: " + dto.kind);
    }
  }

  private static void applyCatalogLikeV20(
    io.github.yellowhammer.designerxml.jaxb.v2_20.mdclasses.CatalogProperties props,
    MdObjectPropertiesDto dto) {
    if (!dto.internalName.equals(props.getName())) {
      throw new IllegalArgumentException("internalName mismatch with XML");
    }
    String syn = dto.synonymRu == null ? "" : dto.synonymRu;
    LocalStringSync.setOrPutRuV20(props.getSynonym(), syn);
    LocalStringSync.replaceRuV20(props.getObjectPresentation(), syn);
    LocalStringSync.replaceRuV20(props.getExtendedObjectPresentation(), syn);
    LocalStringSync.replaceRuV20(props.getListPresentation(), syn);
    LocalStringSync.replaceRuV20(props.getExtendedListPresentation(), syn);
    LocalStringSync.replaceRuV20(props.getExplanation(), syn);
    props.setComment(dto.comment == null ? "" : dto.comment);
  }

  private static void applyCatalogLikeV20(
    io.github.yellowhammer.designerxml.jaxb.v2_20.mdclasses.DocumentProperties props,
    MdObjectPropertiesDto dto) {
    if (!dto.internalName.equals(props.getName())) {
      throw new IllegalArgumentException("internalName mismatch with XML");
    }
    String syn = dto.synonymRu == null ? "" : dto.synonymRu;
    LocalStringSync.setOrPutRuV20(props.getSynonym(), syn);
    LocalStringSync.replaceRuV20(props.getObjectPresentation(), syn);
    LocalStringSync.replaceRuV20(props.getExtendedObjectPresentation(), syn);
    LocalStringSync.replaceRuV20(props.getListPresentation(), syn);
    LocalStringSync.replaceRuV20(props.getExtendedListPresentation(), syn);
    LocalStringSync.replaceRuV20(props.getExplanation(), syn);
    props.setComment(dto.comment == null ? "" : dto.comment);
  }

  private static void applyCatalogLikeV20(
    io.github.yellowhammer.designerxml.jaxb.v2_20.mdclasses.ExchangePlanProperties props,
    MdObjectPropertiesDto dto) {
    if (!dto.internalName.equals(props.getName())) {
      throw new IllegalArgumentException("internalName mismatch with XML");
    }
    String syn = dto.synonymRu == null ? "" : dto.synonymRu;
    LocalStringSync.setOrPutRuV20(props.getSynonym(), syn);
    LocalStringSync.replaceRuV20(props.getObjectPresentation(), syn);
    LocalStringSync.replaceRuV20(props.getExtendedObjectPresentation(), syn);
    LocalStringSync.replaceRuV20(props.getListPresentation(), syn);
    LocalStringSync.replaceRuV20(props.getExtendedListPresentation(), syn);
    LocalStringSync.replaceRuV20(props.getExplanation(), syn);
    props.setComment(dto.comment == null ? "" : dto.comment);
  }

  private static void applyCatalogLikeV21(
    io.github.yellowhammer.designerxml.jaxb.v2_21.mdclasses.CatalogProperties props,
    MdObjectPropertiesDto dto) {
    if (!dto.internalName.equals(props.getName())) {
      throw new IllegalArgumentException("internalName mismatch with XML");
    }
    String syn = dto.synonymRu == null ? "" : dto.synonymRu;
    LocalStringSync.setOrPutRuV21(props.getSynonym(), syn);
    LocalStringSync.replaceRuV21(props.getObjectPresentation(), syn);
    LocalStringSync.replaceRuV21(props.getExtendedObjectPresentation(), syn);
    LocalStringSync.replaceRuV21(props.getListPresentation(), syn);
    LocalStringSync.replaceRuV21(props.getExtendedListPresentation(), syn);
    LocalStringSync.replaceRuV21(props.getExplanation(), syn);
    props.setComment(dto.comment == null ? "" : dto.comment);
  }

  private static void applyCatalogLikeV21(
    io.github.yellowhammer.designerxml.jaxb.v2_21.mdclasses.DocumentProperties props,
    MdObjectPropertiesDto dto) {
    if (!dto.internalName.equals(props.getName())) {
      throw new IllegalArgumentException("internalName mismatch with XML");
    }
    String syn = dto.synonymRu == null ? "" : dto.synonymRu;
    LocalStringSync.setOrPutRuV21(props.getSynonym(), syn);
    LocalStringSync.replaceRuV21(props.getObjectPresentation(), syn);
    LocalStringSync.replaceRuV21(props.getExtendedObjectPresentation(), syn);
    LocalStringSync.replaceRuV21(props.getListPresentation(), syn);
    LocalStringSync.replaceRuV21(props.getExtendedListPresentation(), syn);
    LocalStringSync.replaceRuV21(props.getExplanation(), syn);
    props.setComment(dto.comment == null ? "" : dto.comment);
  }

  private static void applyCatalogLikeV21(
    io.github.yellowhammer.designerxml.jaxb.v2_21.mdclasses.ExchangePlanProperties props,
    MdObjectPropertiesDto dto) {
    if (!dto.internalName.equals(props.getName())) {
      throw new IllegalArgumentException("internalName mismatch with XML");
    }
    String syn = dto.synonymRu == null ? "" : dto.synonymRu;
    LocalStringSync.setOrPutRuV21(props.getSynonym(), syn);
    LocalStringSync.replaceRuV21(props.getObjectPresentation(), syn);
    LocalStringSync.replaceRuV21(props.getExtendedObjectPresentation(), syn);
    LocalStringSync.replaceRuV21(props.getListPresentation(), syn);
    LocalStringSync.replaceRuV21(props.getExtendedListPresentation(), syn);
    LocalStringSync.replaceRuV21(props.getExplanation(), syn);
    props.setComment(dto.comment == null ? "" : dto.comment);
  }

  private static void applyAttrsV20(
    io.github.yellowhammer.designerxml.jaxb.v2_20.mdclasses.CatalogChildObjects co,
    MdObjectPropertiesDto dto) {
    if (co == null) {
      return;
    }
    applyNamedListV20(co.getAttribute(), co.getTabularSection(), dto);
  }

  private static void applyAttrsV20(
    io.github.yellowhammer.designerxml.jaxb.v2_20.mdclasses.DocumentChildObjects co,
    MdObjectPropertiesDto dto) {
    if (co == null) {
      return;
    }
    applyNamedListV20(co.getAttribute(), co.getTabularSection(), dto);
  }

  private static void applyAttrsV20(
    io.github.yellowhammer.designerxml.jaxb.v2_20.mdclasses.ExchangePlanChildObjects co,
    MdObjectPropertiesDto dto) {
    if (co == null) {
      return;
    }
    applyNamedListV20(co.getAttribute(), co.getTabularSection(), dto);
  }

  private static void applyAttrsV21(
    io.github.yellowhammer.designerxml.jaxb.v2_21.mdclasses.CatalogChildObjects co,
    MdObjectPropertiesDto dto) {
    if (co == null) {
      return;
    }
    applyNamedListV21(co.getAttribute(), co.getTabularSection(), dto);
  }

  private static void applyAttrsV21(
    io.github.yellowhammer.designerxml.jaxb.v2_21.mdclasses.DocumentChildObjects co,
    MdObjectPropertiesDto dto) {
    if (co == null) {
      return;
    }
    applyNamedListV21(co.getAttribute(), co.getTabularSection(), dto);
  }

  private static void applyAttrsV21(
    io.github.yellowhammer.designerxml.jaxb.v2_21.mdclasses.ExchangePlanChildObjects co,
    MdObjectPropertiesDto dto) {
    if (co == null) {
      return;
    }
    applyNamedListV21(co.getAttribute(), co.getTabularSection(), dto);
  }

  private static void applyNamedListV20(
    List<io.github.yellowhammer.designerxml.jaxb.v2_20.mdclasses.Attribute> attrs,
    List<io.github.yellowhammer.designerxml.jaxb.v2_20.mdclasses.TabularSection> sections,
    MdObjectPropertiesDto dto) {
    validateNamed(dto.attributes, attrs, "attribute");
    validateNamed(dto.tabularSections, sections, "tabularSection");
    for (int i = 0; i < attrs.size(); i++) {
      applyAttrDtoV20(attrs.get(i), dto.attributes.get(i));
    }
    for (int i = 0; i < sections.size(); i++) {
      applyTsDtoV20(sections.get(i), dto.tabularSections.get(i));
    }
  }

  private static void applyNamedListV21(
    List<io.github.yellowhammer.designerxml.jaxb.v2_21.mdclasses.Attribute> attrs,
    List<io.github.yellowhammer.designerxml.jaxb.v2_21.mdclasses.TabularSection> sections,
    MdObjectPropertiesDto dto) {
    validateNamed(dto.attributes, attrs, "attribute");
    validateNamed(dto.tabularSections, sections, "tabularSection");
    for (int i = 0; i < attrs.size(); i++) {
      applyAttrDtoV21(attrs.get(i), dto.attributes.get(i));
    }
    for (int i = 0; i < sections.size(); i++) {
      applyTsDtoV21(sections.get(i), dto.tabularSections.get(i));
    }
  }

  private static <T> void validateNamed(List<MdNamedPropertyDto> dtos, List<T> xml, String label) {
    if (dtos == null) {
      throw new IllegalArgumentException("missing list: " + label);
    }
    if (dtos.size() != xml.size()) {
      throw new IllegalArgumentException("count mismatch for " + label + ": JSON " + dtos.size() + " vs XML " + xml.size());
    }
  }

  private static void applyAttrDtoV20(
    io.github.yellowhammer.designerxml.jaxb.v2_20.mdclasses.Attribute a,
    MdNamedPropertyDto d) {
    var p = a.getProperties();
    if (d == null || d.name == null || !d.name.equals(p.getName())) {
      throw new IllegalArgumentException("attribute name mismatch");
    }
    String syn = d.synonymRu == null ? "" : d.synonymRu;
    LocalStringSync.setOrPutRuV20(p.getSynonym(), syn);
    p.setComment(d.comment == null ? "" : d.comment);
  }

  private static void applyAttrDtoV21(
    io.github.yellowhammer.designerxml.jaxb.v2_21.mdclasses.Attribute a,
    MdNamedPropertyDto d) {
    var p = a.getProperties();
    if (d == null || d.name == null || !d.name.equals(p.getName())) {
      throw new IllegalArgumentException("attribute name mismatch");
    }
    String syn = d.synonymRu == null ? "" : d.synonymRu;
    LocalStringSync.setOrPutRuV21(p.getSynonym(), syn);
    p.setComment(d.comment == null ? "" : d.comment);
  }

  private static void applyTsDtoV20(
    io.github.yellowhammer.designerxml.jaxb.v2_20.mdclasses.TabularSection ts,
    MdNamedPropertyDto d) {
    var p = ts.getProperties();
    if (d == null || d.name == null || !d.name.equals(p.getName())) {
      throw new IllegalArgumentException("tabular section name mismatch");
    }
    String syn = d.synonymRu == null ? "" : d.synonymRu;
    LocalStringSync.setOrPutRuV20(p.getSynonym(), syn);
    p.setComment(d.comment == null ? "" : d.comment);
  }

  private static void applyTsDtoV21(
    io.github.yellowhammer.designerxml.jaxb.v2_21.mdclasses.TabularSection ts,
    MdNamedPropertyDto d) {
    var p = ts.getProperties();
    if (d == null || d.name == null || !d.name.equals(p.getName())) {
      throw new IllegalArgumentException("tabular section name mismatch");
    }
    String syn = d.synonymRu == null ? "" : d.synonymRu;
    LocalStringSync.setOrPutRuV21(p.getSynonym(), syn);
    p.setComment(d.comment == null ? "" : d.comment);
  }

  private static void applySubsystemV20(
    io.github.yellowhammer.designerxml.jaxb.v2_20.mdclasses.Subsystem sub,
    MdObjectPropertiesDto dto) {
    var props = sub.getProperties();
    if (!dto.internalName.equals(props.getName())) {
      throw new IllegalArgumentException("internalName mismatch with XML");
    }
    String syn = dto.synonymRu == null ? "" : dto.synonymRu;
    LocalStringSync.setOrPutRuV20(props.getSynonym(), syn);
    props.setComment(dto.comment == null ? "" : dto.comment);
    var ch = sub.getChildObjects();
    if (ch == null) {
      return;
    }
    if (dto.nestedSubsystems == null) {
      throw new IllegalArgumentException("nestedSubsystems required");
    }
    ch.getSubsystem().clear();
    ch.getSubsystem().addAll(new ArrayList<>(dto.nestedSubsystems));
  }

  private static void applySubsystemV21(
    io.github.yellowhammer.designerxml.jaxb.v2_21.mdclasses.Subsystem sub,
    MdObjectPropertiesDto dto) {
    var props = sub.getProperties();
    if (!dto.internalName.equals(props.getName())) {
      throw new IllegalArgumentException("internalName mismatch with XML");
    }
    String syn = dto.synonymRu == null ? "" : dto.synonymRu;
    LocalStringSync.setOrPutRuV21(props.getSynonym(), syn);
    props.setComment(dto.comment == null ? "" : dto.comment);
    var ch = sub.getChildObjects();
    if (ch == null) {
      return;
    }
    if (dto.nestedSubsystems == null) {
      throw new IllegalArgumentException("nestedSubsystems required");
    }
    ch.getSubsystem().clear();
    ch.getSubsystem().addAll(new ArrayList<>(dto.nestedSubsystems));
  }

  private static MdObjectPropertiesDto tryReadSimpleKind(Object metaDataObject) {
    for (SimpleKindDef def : SIMPLE_KINDS) {
      Object child = invokeNoArgOrNull(metaDataObject, def.getterName);
      if (child != null) {
        return readSimpleDto(def.kind, child);
      }
    }
    return null;
  }

  private static boolean tryApplySimpleKind(Object metaDataObject, MdObjectPropertiesDto dto) {
    SimpleKindDef def = simpleKindByName(dto.kind);
    if (def == null) {
      return false;
    }
    Object child = invokeNoArgOrNull(metaDataObject, def.getterName);
    if (child == null) {
      throw new IllegalArgumentException("MetaDataObject is not a " + dto.kind);
    }
    applySimpleDto(child, dto);
    return true;
  }

  private static MdObjectPropertiesDto readSimpleDto(String kind, Object objectNode) {
    Object props = invokeNoArg(objectNode, "getProperties");
    MdObjectPropertiesDto dto = new MdObjectPropertiesDto();
    dto.kind = kind;
    dto.internalName = toStringOrEmpty(invokeNoArg(props, "getName"));
    dto.synonymRu = readLocalStringRu(invokeNoArgOrNull(props, "getSynonym"));
    dto.comment = toStringOrEmpty(invokeNoArgOrNull(props, "getComment"));
    return dto;
  }

  private static void applySimpleDto(Object objectNode, MdObjectPropertiesDto dto) {
    Object props = invokeNoArg(objectNode, "getProperties");
    String currentName = toStringOrEmpty(invokeNoArg(props, "getName"));
    if (!dto.internalName.equals(currentName)) {
      throw new IllegalArgumentException("internalName mismatch with XML");
    }
    String syn = dto.synonymRu == null ? "" : dto.synonymRu;
    writeLocalStringRu(props, syn);
    invokeSetterString(props, "setComment", dto.comment == null ? "" : dto.comment);
  }

  private static String readLocalStringRu(Object localString) {
    if (localString == null) {
      return "";
    }
    Object items = invokeNoArgOrNull(localString, "getItem");
    if (!(items instanceof List<?> list)) {
      return "";
    }
    for (Object item : list) {
      if ("ru".equals(toStringOrEmpty(invokeNoArgOrNull(item, "getLang")))) {
        return toStringOrEmpty(invokeNoArgOrNull(item, "getContent"));
      }
    }
    return "";
  }

  private static void writeLocalStringRu(Object props, String value) {
    Object localString = invokeNoArgOrNull(props, "getSynonym");
    Method getSynonym;
    try {
      getSynonym = props.getClass().getMethod("getSynonym");
    } catch (NoSuchMethodException e) {
      throw new IllegalStateException("synonym accessor not found", e);
    }
    if (localString == null) {
      localString = newInstance(getSynonym.getReturnType());
      invokeSetter(props, "setSynonym", getSynonym.getReturnType(), localString);
    }
    Object itemsObj = invokeNoArg(localString, "getItem");
    if (!(itemsObj instanceof List<?>)) {
      throw new IllegalStateException("local string items list not found");
    }
    @SuppressWarnings("unchecked")
    List<Object> items = (List<Object>) itemsObj;
    for (Object item : items) {
      if ("ru".equals(toStringOrEmpty(invokeNoArgOrNull(item, "getLang")))) {
        invokeSetterString(item, "setContent", value);
        return;
      }
    }
    Class<?> itemType = resolveLocalStringItemType(localString, items);
    Object newItem = newInstance(itemType);
    invokeSetterString(newItem, "setLang", "ru");
    invokeSetterString(newItem, "setContent", value);
    items.add(newItem);
  }

  private static Class<?> resolveLocalStringItemType(Object localString, List<Object> items) {
    if (!items.isEmpty()) {
      return items.get(0).getClass();
    }
    try {
      Method m = localString.getClass().getMethod("getItem");
      Type generic = m.getGenericReturnType();
      if (generic instanceof ParameterizedType p) {
        Type arg = p.getActualTypeArguments()[0];
        if (arg instanceof Class<?> c) {
          return c;
        }
      }
    } catch (NoSuchMethodException e) {
      throw new IllegalStateException("local string getItem not found", e);
    }
    throw new IllegalStateException("local string item type cannot be resolved");
  }

  private static Object invokeNoArg(Object target, String methodName) {
    Object value = invokeNoArgOrNull(target, methodName);
    if (value == null) {
      throw new IllegalStateException("method returned null: " + methodName);
    }
    return value;
  }

  private static Object invokeNoArgOrNull(Object target, String methodName) {
    try {
      Method method = target.getClass().getMethod(methodName);
      return method.invoke(target);
    } catch (NoSuchMethodException e) {
      return null;
    } catch (InvocationTargetException | IllegalAccessException e) {
      throw new IllegalStateException("invoke failed: " + methodName, e);
    }
  }

  private static void invokeSetterString(Object target, String methodName, String value) {
    invokeSetter(target, methodName, String.class, value);
  }

  private static void invokeSetter(Object target, String methodName, Class<?> argType, Object value) {
    try {
      Method m = target.getClass().getMethod(methodName, argType);
      m.invoke(target, value);
    } catch (NoSuchMethodException e) {
      throw new IllegalStateException("setter not found: " + methodName, e);
    } catch (InvocationTargetException | IllegalAccessException e) {
      throw new IllegalStateException("setter failed: " + methodName, e);
    }
  }

  private static Object newInstance(Class<?> type) {
    try {
      return type.getDeclaredConstructor().newInstance();
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException("cannot create instance of " + type.getName(), e);
    }
  }

  private static String toStringOrEmpty(Object value) {
    return value == null ? "" : String.valueOf(value);
  }

  private static SimpleKindDef simpleKindByName(String kind) {
    for (SimpleKindDef def : SIMPLE_KINDS) {
      if (def.kind.equals(kind)) {
        return def;
      }
    }
    return null;
  }

  private record SimpleKindDef(String kind, String getterName) {
  }
}
