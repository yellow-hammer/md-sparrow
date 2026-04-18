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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Чтение и запись полей справочника через JAXB ({@link DesignerXml}); XML на диске не трогает ничто, кроме этой библиотеки.
 */
public final class CatalogFormEdit {

  private CatalogFormEdit() {
  }

  /**
   * Снимок свойств для формы: имя, синоним ru, комментарий.
   */
  public static CatalogFormDto readDto(Path catalogXml, SchemaVersion version) throws IOException, JAXBException {
    if (!Files.isRegularFile(catalogXml)) {
      throw new IllegalArgumentException("file not found: " + catalogXml);
    }
    Object root = DesignerXml.read(catalogXml, version);
    if (!(root instanceof JAXBElement<?> je)) {
      throw new IllegalArgumentException("expected JAXBElement root");
    }
    return switch (version) {
      case V2_20 -> readDtoV20(je);
      case V2_21 -> readDtoV21(je);
    };
  }

  /**
   * Записывает изменения из DTO; {@code internalName} должен совпадать с именем в файле и с именем {@code Catalogs/&lt;имя&gt;.xml}.
   */
  public static void writeDto(Path catalogXml, SchemaVersion version, CatalogFormDto dto)
    throws IOException, JAXBException {
    if (dto == null || dto.internalName == null || dto.internalName.isEmpty()) {
      throw new IllegalArgumentException("internalName required");
    }
    if (!Files.isRegularFile(catalogXml)) {
      throw new IllegalArgumentException("file not found: " + catalogXml);
    }
    checkStemMatches(catalogXml, dto.internalName);
    Object root = DesignerXml.read(catalogXml, version);
    if (!(root instanceof JAXBElement<?> je)) {
      throw new IllegalArgumentException("expected JAXBElement root");
    }
    switch (version) {
      case V2_20 -> applyDtoV20(je, dto);
      case V2_21 -> applyDtoV21(je, dto);
    }
    DesignerXml.write(catalogXml, root, version, WriteOptions.forMdObjectEdit(catalogXml));
  }

  private static void checkStemMatches(Path catalogXml, String internalName) {
    String fn = catalogXml.getFileName().toString();
    if (!fn.endsWith(".xml")) {
      throw new IllegalArgumentException("expected .xml file");
    }
    String stem = fn.substring(0, fn.length() - 4);
    if (!stem.equals(internalName)) {
      throw new IllegalArgumentException("file name must match internal name: " + stem + " vs " + internalName);
    }
  }

  private static CatalogFormDto readDtoV20(JAXBElement<?> je) {
    var mdo = (io.github.yellowhammer.designerxml.jaxb.v2_20.mdclasses.MetaDataObject) je.getValue();
    var cat = mdo.getCatalog();
    if (cat == null) {
      throw new IllegalArgumentException("MetaDataObject is not a Catalog");
    }
    var props = cat.getProperties();
    String name = props.getName();
    String syn = LocalStringSync.firstRuV20(props.getSynonym());
    String comment = props.getComment() == null ? "" : props.getComment();
    return new CatalogFormDto(name, syn, comment);
  }

  private static CatalogFormDto readDtoV21(JAXBElement<?> je) {
    var mdo = (io.github.yellowhammer.designerxml.jaxb.v2_21.mdclasses.MetaDataObject) je.getValue();
    var cat = mdo.getCatalog();
    if (cat == null) {
      throw new IllegalArgumentException("MetaDataObject is not a Catalog");
    }
    var props = cat.getProperties();
    String name = props.getName();
    String syn = LocalStringSync.firstRuV21(props.getSynonym());
    String comment = props.getComment() == null ? "" : props.getComment();
    return new CatalogFormDto(name, syn, comment);
  }

  private static void applyDtoV20(JAXBElement<?> je, CatalogFormDto dto) {
    var mdo = (io.github.yellowhammer.designerxml.jaxb.v2_20.mdclasses.MetaDataObject) je.getValue();
    var cat = mdo.getCatalog();
    if (cat == null) {
      throw new IllegalArgumentException("MetaDataObject is not a Catalog");
    }
    var props = cat.getProperties();
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

  private static void applyDtoV21(JAXBElement<?> je, CatalogFormDto dto) {
    var mdo = (io.github.yellowhammer.designerxml.jaxb.v2_21.mdclasses.MetaDataObject) je.getValue();
    var cat = mdo.getCatalog();
    if (cat == null) {
      throw new IllegalArgumentException("MetaDataObject is not a Catalog");
    }
    var props = cat.getProperties();
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
}
