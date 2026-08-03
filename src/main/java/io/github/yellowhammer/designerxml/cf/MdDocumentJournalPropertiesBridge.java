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
import io.github.yellowhammer.designerxml.reflect.JaxbReflect;

import jakarta.xml.bind.JAXBException;

import static io.github.yellowhammer.designerxml.cf.MdPropertiesBridgeSupport.enumName;
import static io.github.yellowhammer.designerxml.cf.MdPropertiesBridgeSupport.ensureAndSetRu;
import static io.github.yellowhammer.designerxml.cf.MdPropertiesBridgeSupport.nullIfBlank;

/**
 * Чтение и запись {@code DocumentJournalProperties} через JAXB-рефлексию.
 */
public final class MdDocumentJournalPropertiesBridge {

  private MdDocumentJournalPropertiesBridge() {
  }

  public static void read(SchemaVersion version, Object p, MdObjectPropertiesDto dto) {
    MdDocumentJournalPropertiesDto d = new MdDocumentJournalPropertiesDto();
    d.objectBelonging = enumName(p, "getObjectBelonging");
    d.extendedConfigurationObject = nullIfBlank(JaxbReflect.getStringOptional(p, "getExtendedConfigurationObject"));
    d.useStandardCommands = JaxbReflect.getBooleanOptional(p, "isUseStandardCommands");
    d.defaultForm = JaxbReflect.getStringOptional(p, "getDefaultForm");
    d.auxiliaryForm = JaxbReflect.getStringOptional(p, "getAuxiliaryForm");
    d.managerModule = JaxbReflect.getStringOptional(p, "getManagerModule");
    d.includeHelpInContents = JaxbReflect.getBooleanOptional(p, "isIncludeHelpInContents");
    d.standardAttributesXml = MdPropertiesBridgeSupport.marshalStandardAttributesOrEmpty(
      version, JaxbReflect.getOptional(p, "getStandardAttributes"));
    d.listPresentationRu = LocalStringSync.firstRu(JaxbReflect.getOptional(p, "getListPresentation"));
    d.extendedListPresentationRu = LocalStringSync.firstRu(JaxbReflect.getOptional(p, "getExtendedListPresentation"));
    d.explanationRu = LocalStringSync.firstRu(JaxbReflect.getOptional(p, "getExplanation"));
    d.additionalIndexes = JaxbReflect.getStringOptional(p, "getAdditionalIndexes");
    Object registered = JaxbReflect.getOptional(p, "getRegisteredDocuments");
    if (registered != null) {
      d.registeredDocuments.addAll(MdListTypeRefs.readItemTexts(JaxbReflect.list(registered, "getItem")));
    }
    dto.documentJournal = d;
  }

  public static void apply(SchemaVersion version, Object p, MdObjectPropertiesDto dto) {
    MdDocumentJournalPropertiesDto d = dto.documentJournal;
    if (d == null) {
      throw new IllegalArgumentException("documentJournal required");
    }
    MdPropertiesBridgeSupport.applyCommon(p, dto);
    JaxbReflect.setEnumOrKeep(p, "setObjectBelonging", d.objectBelonging);
    JaxbReflect.setOptional(p, "setExtendedConfigurationObject", nullIfBlank(d.extendedConfigurationObject));
    JaxbReflect.setOptional(p, "setUseStandardCommands", d.useStandardCommands);
    JaxbReflect.setOptional(p, "setDefaultForm", d.defaultForm);
    JaxbReflect.setOptional(p, "setAuxiliaryForm", d.auxiliaryForm);
    JaxbReflect.setOptional(p, "setManagerModule", d.managerModule);
    JaxbReflect.setOptional(p, "setIncludeHelpInContents", d.includeHelpInContents);
    applyStandardAttributes(version, p, d);
    ensureAndSetRu(p, "getListPresentation", "setListPresentation", d.listPresentationRu);
    ensureAndSetRu(p, "getExtendedListPresentation", "setExtendedListPresentation", d.extendedListPresentationRu);
    ensureAndSetRu(p, "getExplanation", "setExplanation", d.explanationRu);
    JaxbReflect.setOptional(p, "setAdditionalIndexes", d.additionalIndexes);
    MdListTypeRefs.replaceItems(JaxbReflect.getOptional(p, "getRegisteredDocuments"), d.registeredDocuments);
  }

  private static void applyStandardAttributes(SchemaVersion version, Object p, MdDocumentJournalPropertiesDto d) {
    if (d.standardAttributesXml == null || d.standardAttributesXml.isBlank()) {
      return;
    }
    try {
      JaxbReflect.setOptional(p, "setStandardAttributes",
        MdCfCatalogSubtreeXml.unmarshalStandardAttributes(version, d.standardAttributesXml.trim()));
    } catch (JAXBException e) {
      throw new IllegalArgumentException("standardAttributesXml: " + e.getMessage(), e);
    }
  }
}
