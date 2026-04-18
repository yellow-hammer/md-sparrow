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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * Формирует текст {@code Catalogs/<имя>.xml}: только {@link NewCatalogXml} (JAXB по XSD), затем мутация
 * имён/синонимов, маршалинг, смена UUID. Чужие файлы в {@code Catalogs/} не читаются и не служат образцом.
 */
final class CatalogXmlEmitter {
  private CatalogXmlEmitter() {
  }

  /**
   * @param synonymRu       явный синоним ru; при {@code null} или пустой строке подставляется имя (если не {@code synonymEmpty})
   * @param synonymEmpty    если {@code true}, ru-синоним и связанные поля — пустые (как в эталонной выгрузке)
   */
  static String emit(String catalogName, String synonymRu, boolean synonymEmpty, SchemaVersion version)
    throws IOException, JAXBException {
    Objects.requireNonNull(catalogName, "catalogName");
    Objects.requireNonNull(version, "version");
    CatalogNameConstraints.check(catalogName);
    String syn;
    if (synonymEmpty) {
      syn = "";
    } else if (synonymRu == null || synonymRu.isEmpty()) {
      syn = catalogName;
    } else {
      syn = synonymRu;
    }

    Object root = NewCatalogXml.newCatalogRoot(version, catalogName, syn);
    applyMutation(root, version, catalogName, syn);

    ByteArrayOutputStream buf = new ByteArrayOutputStream();
    DesignerXml.marshal(version, root, buf, WriteOptions.defaults());
    String serialized = buf.toString(StandardCharsets.UTF_8);
    String uuidSeed = "catalogEmitter|" + version + "|" + catalogName;
    String withFreshUuids = DistinctUuidRewrite.remapDeterministic(serialized, uuidSeed);
    String normalizedFormatting = GoldenXmlPostProcessor.normalizeMetaDataObjectXml(withFreshUuids, version);

    try (
      ByteArrayInputStream bin = new ByteArrayInputStream(normalizedFormatting.getBytes(StandardCharsets.UTF_8))
    ) {
      DesignerXml.unmarshal(version, bin);
    }
    return normalizedFormatting;
  }

  private static void applyMutation(
    Object jaxbRoot,
    SchemaVersion version,
    String catalogName,
    String synonymRu) {
    if (!(jaxbRoot instanceof JAXBElement<?> je)) {
      throw new IllegalArgumentException("expected JAXBElement root");
    }
    if (version == SchemaVersion.V2_21) {
      mutateV221(je, catalogName, synonymRu);
      return;
    }
    mutateV220(je, catalogName, synonymRu);
  }

  private static void mutateV221(JAXBElement<?> je, String catalogName, String synonymRu) {
    var mdo = (io.github.yellowhammer.designerxml.jaxb.v2_21.mdclasses.MetaDataObject) je.getValue();
    mdo.setVersion(SchemaVersion.V2_21.metadataObjectVersionAttribute());
    var cat = mdo.getCatalog();
    if (cat == null) {
      throw new IllegalArgumentException("expected Catalog in MetaDataObject");
    }
    var props = cat.getProperties();
    props.setName(catalogName);
    replaceRuV21(props.getSynonym(), synonymRu);
    replaceRuV21(props.getObjectPresentation(), synonymRu);
    replaceRuV21(props.getExtendedObjectPresentation(), synonymRu);
    replaceRuV21(props.getListPresentation(), synonymRu);
    replaceRuV21(props.getExtendedListPresentation(), synonymRu);
    replaceRuV21(props.getExplanation(), synonymRu);
    applySnapshotLikePropertiesV221(props);
  }

  private static void replaceRuV21(
    io.github.yellowhammer.designerxml.jaxb.v2_21.v8_1_data_core.LocalStringType lst,
    String content) {
    if (lst == null) {
      return;
    }
    if (content == null || content.isEmpty()) {
      lst.getItem().clear();
      return;
    }
    for (var it : lst.getItem()) {
      if ("ru".equals(it.getLang())) {
        it.setContent(content);
        return;
      }
    }
  }

  private static void mutateV220(JAXBElement<?> je, String catalogName, String synonymRu) {
    var mdo = (io.github.yellowhammer.designerxml.jaxb.v2_20.mdclasses.MetaDataObject) je.getValue();
    mdo.setVersion(SchemaVersion.V2_20.metadataObjectVersionAttribute());
    var cat = mdo.getCatalog();
    if (cat == null) {
      throw new IllegalArgumentException("expected Catalog in MetaDataObject");
    }
    var props = cat.getProperties();
    props.setName(catalogName);
    replaceRuV20(props.getSynonym(), synonymRu);
    replaceRuV20(props.getObjectPresentation(), synonymRu);
    replaceRuV20(props.getExtendedObjectPresentation(), synonymRu);
    replaceRuV20(props.getListPresentation(), synonymRu);
    replaceRuV20(props.getExtendedListPresentation(), synonymRu);
    replaceRuV20(props.getExplanation(), synonymRu);
    applySnapshotLikePropertiesV220(props);
  }

  private static void applySnapshotLikePropertiesV220(
    io.github.yellowhammer.designerxml.jaxb.v2_20.mdclasses.CatalogProperties props) {
    props.setObjectBelonging(null);
    props.setStandardAttributes(null);
    props.setObjectModule(null);
    props.setManagerModule(null);
    props.setHelp(null);
    props.setPredefined(null);
    props.setAdditionalIndexes(null);
  }

  private static void applySnapshotLikePropertiesV221(
    io.github.yellowhammer.designerxml.jaxb.v2_21.mdclasses.CatalogProperties props) {
    props.setObjectBelonging(null);
    props.setStandardAttributes(null);
    props.setObjectModule(null);
    props.setManagerModule(null);
    props.setHelp(null);
    props.setPredefined(null);
    props.setAdditionalIndexes(null);
  }

  private static void replaceRuV20(
    io.github.yellowhammer.designerxml.jaxb.v2_20.v8_1_data_core.LocalStringType lst,
    String content) {
    if (lst == null) {
      return;
    }
    if (content == null || content.isEmpty()) {
      lst.getItem().clear();
      return;
    }
    for (var it : lst.getItem()) {
      if ("ru".equals(it.getLang())) {
        it.setContent(content);
        return;
      }
    }
  }

}
