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
 * Чтение и запись {@code EnumProperties} через JAXB-рефлексию.
 */
public final class MdEnumPropertiesBridge {

  private MdEnumPropertiesBridge() {
  }

  public static void read(SchemaVersion version, Object p, MdObjectPropertiesDto dto) {
    MdEnumPropertiesDto d = new MdEnumPropertiesDto();
    d.objectBelonging = enumName(p, "getObjectBelonging");
    d.extendedConfigurationObject = nullIfBlank(JaxbReflect.getStringOptional(p, "getExtendedConfigurationObject"));
    d.useStandardCommands = JaxbReflect.getBooleanOptional(p, "isUseStandardCommands");
    d.standardAttributesXml = MdPropertiesBridgeSupport.marshalStandardAttributesOrEmpty(
      version, JaxbReflect.getOptional(p, "getStandardAttributes"));
    d.characteristicsXml = MdPropertiesBridgeSupport.marshalCharacteristicsOrEmpty(
      version, JaxbReflect.getOptional(p, "getCharacteristics"));
    d.quickChoice = JaxbReflect.getBooleanOptional(p, "isQuickChoice");
    d.choiceMode = enumName(p, "getChoiceMode");
    d.defaultListForm = JaxbReflect.getStringOptional(p, "getDefaultListForm");
    d.defaultChoiceForm = JaxbReflect.getStringOptional(p, "getDefaultChoiceForm");
    d.auxiliaryListForm = JaxbReflect.getStringOptional(p, "getAuxiliaryListForm");
    d.auxiliaryChoiceForm = JaxbReflect.getStringOptional(p, "getAuxiliaryChoiceForm");
    d.managerModule = JaxbReflect.getStringOptional(p, "getManagerModule");
    d.listPresentationRu = LocalStringSync.firstRu(JaxbReflect.getOptional(p, "getListPresentation"));
    d.extendedListPresentationRu = LocalStringSync.firstRu(JaxbReflect.getOptional(p, "getExtendedListPresentation"));
    d.explanationRu = LocalStringSync.firstRu(JaxbReflect.getOptional(p, "getExplanation"));
    d.choiceHistoryOnInput = enumName(p, "getChoiceHistoryOnInput");
    dto.enumeration = d;
  }

  public static void apply(SchemaVersion version, Object p, MdObjectPropertiesDto dto) {
    MdEnumPropertiesDto d = dto.enumeration;
    if (d == null) {
      throw new IllegalArgumentException("enum required");
    }
    MdPropertiesBridgeSupport.applyCommon(p, dto);
    JaxbReflect.setEnumOrKeep(p, "setObjectBelonging", d.objectBelonging);
    JaxbReflect.setOptional(p, "setExtendedConfigurationObject", nullIfBlank(d.extendedConfigurationObject));
    JaxbReflect.setOptional(p, "setUseStandardCommands", d.useStandardCommands);
    applyStandardAttributes(version, p, d);
    applyCharacteristics(version, p, d);
    JaxbReflect.setOptional(p, "setQuickChoice", d.quickChoice);
    JaxbReflect.setEnumOrKeep(p, "setChoiceMode", d.choiceMode);
    JaxbReflect.setOptional(p, "setDefaultListForm", d.defaultListForm);
    JaxbReflect.setOptional(p, "setDefaultChoiceForm", d.defaultChoiceForm);
    JaxbReflect.setOptional(p, "setAuxiliaryListForm", d.auxiliaryListForm);
    JaxbReflect.setOptional(p, "setAuxiliaryChoiceForm", d.auxiliaryChoiceForm);
    JaxbReflect.setOptional(p, "setManagerModule", d.managerModule);
    ensureAndSetRu(p, "getListPresentation", "setListPresentation", d.listPresentationRu);
    ensureAndSetRu(p, "getExtendedListPresentation", "setExtendedListPresentation", d.extendedListPresentationRu);
    ensureAndSetRu(p, "getExplanation", "setExplanation", d.explanationRu);
    JaxbReflect.setEnumOrKeep(p, "setChoiceHistoryOnInput", d.choiceHistoryOnInput);
  }

  private static void applyStandardAttributes(SchemaVersion version, Object p, MdEnumPropertiesDto d) {
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

  private static void applyCharacteristics(SchemaVersion version, Object p, MdEnumPropertiesDto d) {
    if (d.characteristicsXml == null || d.characteristicsXml.isBlank()) {
      return;
    }
    try {
      JaxbReflect.setOptional(p, "setCharacteristics",
        MdCfCatalogSubtreeXml.unmarshalCharacteristics(version, d.characteristicsXml.trim()));
    } catch (JAXBException e) {
      throw new IllegalArgumentException("characteristicsXml: " + e.getMessage(), e);
    }
  }
}
