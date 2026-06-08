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
import io.github.yellowhammer.designerxml.reflect.JaxbReflect;

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
    return readDto(je);
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
    applyDto(je, dto);
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

  private static CatalogFormDto readDto(JAXBElement<?> je) {
    Object props = catalogProperties(je);
    String name = JaxbReflect.getString(props, "getName");
    String syn = LocalStringSync.firstRu(JaxbReflect.get(props, "getSynonym"));
    String comment = JaxbReflect.getString(props, "getComment");
    return new CatalogFormDto(name, syn, comment == null ? "" : comment);
  }

  private static void applyDto(JAXBElement<?> je, CatalogFormDto dto) {
    Object props = catalogProperties(je);
    if (!dto.internalName.equals(JaxbReflect.getString(props, "getName"))) {
      throw new IllegalArgumentException("internalName mismatch with XML");
    }
    String syn = dto.synonymRu == null ? "" : dto.synonymRu;
    LocalStringSync.setOrPutRu(JaxbReflect.get(props, "getSynonym"), syn);
    // Поля-представления версионно-вариативны (в старых форматах могут отсутствовать) → tolerant.
    LocalStringSync.replaceRu(JaxbReflect.getOptional(props, "getObjectPresentation"), syn);
    LocalStringSync.replaceRu(JaxbReflect.getOptional(props, "getExtendedObjectPresentation"), syn);
    LocalStringSync.replaceRu(JaxbReflect.getOptional(props, "getListPresentation"), syn);
    LocalStringSync.replaceRu(JaxbReflect.getOptional(props, "getExtendedListPresentation"), syn);
    LocalStringSync.replaceRu(JaxbReflect.getOptional(props, "getExplanation"), syn);
    JaxbReflect.set(props, "setComment", dto.comment == null ? "" : dto.comment);
  }

  private static Object catalogProperties(JAXBElement<?> je) {
    Object cat = JaxbReflect.get(je.getValue(), "getCatalog");
    if (cat == null) {
      throw new IllegalArgumentException("MetaDataObject is not a Catalog");
    }
    return JaxbReflect.get(cat, "getProperties");
  }
}
