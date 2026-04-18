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
import java.util.Objects;

/**
 * Язык по умолчанию ({@link CfLayout#RUSSIAN_LANGUAGE_NAME}) в {@link CfLayout#LANGUAGES_DIR} — как в пустой выгрузке конфигуратора.
 */
public final class NewLanguageXml {

  private NewLanguageXml() {
  }

  public static void writeRussian(Path cfRoot, SchemaVersion schemaVersion) throws IOException, JAXBException {
    Objects.requireNonNull(cfRoot, "cfRoot");
    Objects.requireNonNull(schemaVersion, "schemaVersion");
    Path langDir = cfRoot.resolve(CfLayout.LANGUAGES_DIR);
    Files.createDirectories(langDir);
    Path out = langDir.resolve(CfLayout.RUSSIAN_LANGUAGE_NAME + ".xml");
    switch (schemaVersion) {
      case V2_20 -> writeRussianV220(out);
      case V2_21 -> writeRussianV221(out);
    }
  }

  private static void writeRussianV220(Path out) throws IOException, JAXBException {
    var factory = new io.github.yellowhammer.designerxml.jaxb.v2_20.mdclasses.ObjectFactory();
    var mdo = factory.createMetaDataObject();
    mdo.setVersion(SchemaVersion.V2_20.metadataObjectVersionAttribute());
    io.github.yellowhammer.designerxml.jaxb.v2_20.mdclasses.Language lang = factory.createLanguage();
    lang.setUuid(GoldenUuid.from("newLanguage|V2_20|Русский", "language.uuid"));
    var props = factory.createLanguageProperties();
    props.setName(CfLayout.RUSSIAN_LANGUAGE_NAME);
    props.setSynonym(localStringV220(CfLayout.RUSSIAN_LANGUAGE_NAME));
    props.setComment("");
    props.setObjectBelonging(io.github.yellowhammer.designerxml.jaxb.v2_20.v8_3_xcf_enums.ObjectBelonging.NATIVE);
    props.setLanguageCode("ru");
    lang.setProperties(props);
    mdo.setLanguage(lang);
    JAXBElement<io.github.yellowhammer.designerxml.jaxb.v2_20.mdclasses.MetaDataObject> root =
      factory.createMetaDataObject(mdo);
    DesignerXml.write(out, root, SchemaVersion.V2_20, WriteOptions.defaults());
  }

  private static void writeRussianV221(Path out) throws IOException, JAXBException {
    var factory = new io.github.yellowhammer.designerxml.jaxb.v2_21.mdclasses.ObjectFactory();
    var mdo = factory.createMetaDataObject();
    mdo.setVersion(SchemaVersion.V2_21.metadataObjectVersionAttribute());
    io.github.yellowhammer.designerxml.jaxb.v2_21.mdclasses.Language lang = factory.createLanguage();
    lang.setUuid(GoldenUuid.from("newLanguage|V2_21|Русский", "language.uuid"));
    var props = factory.createLanguageProperties();
    props.setName(CfLayout.RUSSIAN_LANGUAGE_NAME);
    props.setSynonym(localStringV221(CfLayout.RUSSIAN_LANGUAGE_NAME));
    props.setComment("");
    props.setObjectBelonging(io.github.yellowhammer.designerxml.jaxb.v2_21.v8_3_xcf_enums.ObjectBelonging.NATIVE);
    props.setLanguageCode("ru");
    lang.setProperties(props);
    mdo.setLanguage(lang);
    JAXBElement<io.github.yellowhammer.designerxml.jaxb.v2_21.mdclasses.MetaDataObject> root =
      factory.createMetaDataObject(mdo);
    DesignerXml.write(out, root, SchemaVersion.V2_21, WriteOptions.defaults());
  }

  private static io.github.yellowhammer.designerxml.jaxb.v2_20.v8_1_data_core.LocalStringType localStringV220(
    String ruContent) {
    var lst = new io.github.yellowhammer.designerxml.jaxb.v2_20.v8_1_data_core.LocalStringType();
    var item = new io.github.yellowhammer.designerxml.jaxb.v2_20.v8_1_data_core.LocalStringItemType();
    item.setLang("ru");
    item.setContent(ruContent == null ? "" : ruContent);
    lst.getItem().add(item);
    return lst;
  }

  private static io.github.yellowhammer.designerxml.jaxb.v2_21.v8_1_data_core.LocalStringType localStringV221(
    String ruContent) {
    var lst = new io.github.yellowhammer.designerxml.jaxb.v2_21.v8_1_data_core.LocalStringType();
    var item = new io.github.yellowhammer.designerxml.jaxb.v2_21.v8_1_data_core.LocalStringItemType();
    item.setLang("ru");
    item.setContent(ruContent == null ? "" : ruContent);
    lst.getItem().add(item);
    return lst;
  }
}
