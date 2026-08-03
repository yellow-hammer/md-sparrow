/*
 * This file is a part of md-sparrow.
 *
 * Copyright (c) 2026
 * Ivan Karlo <i.karlo@outlook.com> and contributors
 *
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package io.github.yellowhammer.designerxml.cf;

import io.github.yellowhammer.designerxml.reflect.JaxbReflect;

import static io.github.yellowhammer.designerxml.cf.MdPropertiesBridgeSupport.enumName;
import static io.github.yellowhammer.designerxml.cf.MdPropertiesBridgeSupport.nullIfBlank;

/**
 * Чтение и запись {@code CommonAttributeProperties} через JAXB-рефлексию. Разделение данных и автоиспользование решают, где реквизит появится.
 */
public final class MdCommonAttributePropertiesBridge {

  private MdCommonAttributePropertiesBridge() {
  }

  public static void read(Object p, MdObjectPropertiesDto dto) {
    MdCommonAttributePropertiesDto d = new MdCommonAttributePropertiesDto();
    d.objectBelonging = enumName(p, "getObjectBelonging");
    d.extendedConfigurationObject = nullIfBlank(JaxbReflect.getStringOptional(p, "getExtendedConfigurationObject"));
    d.type = MdTypeDescriptionBridge.read(JaxbReflect.getOptional(p, "getType"));
    d.autoUse = enumName(p, "getAutoUse");
    d.dataSeparation = enumName(p, "getDataSeparation");
    d.separatedDataUse = enumName(p, "getSeparatedDataUse");
    d.dataSeparationValue = JaxbReflect.getStringOptional(p, "getDataSeparationValue");
    d.dataSeparationUse = JaxbReflect.getStringOptional(p, "getDataSeparationUse");
    d.conditionalSeparation = JaxbReflect.getStringOptional(p, "getConditionalSeparation");
    d.usersSeparation = enumName(p, "getUsersSeparation");
    d.authenticationSeparation = enumName(p, "getAuthenticationSeparation");
    d.configurationExtensionsSeparation = enumName(p, "getConfigurationExtensionsSeparation");
    d.indexing = enumName(p, "getIndexing");
    d.fullTextSearch = enumName(p, "getFullTextSearch");
    d.dataHistory = enumName(p, "getDataHistory");
    d.toolTipRu = LocalStringSync.firstRu(JaxbReflect.getOptional(p, "getToolTip"));
    d.passwordMode = JaxbReflect.getBooleanOptional(p, "isPasswordMode");
    d.multiLine = JaxbReflect.getBooleanOptional(p, "isMultiLine");
    d.mask = JaxbReflect.getStringOptional(p, "getMask");
    d.quickChoice = enumName(p, "getQuickChoice");
    d.createOnInput = enumName(p, "getCreateOnInput");
    d.choiceHistoryOnInput = enumName(p, "getChoiceHistoryOnInput");
    d.fillChecking = enumName(p, "getFillChecking");
    d.choiceForm = JaxbReflect.getStringOptional(p, "getChoiceForm");
    dto.commonAttribute = d;
  }

  public static void apply(Object p, MdObjectPropertiesDto dto) {
    MdCommonAttributePropertiesDto d = dto.commonAttribute;
    if (d == null) {
      throw new IllegalArgumentException("commonAttribute required");
    }
    MdPropertiesBridgeSupport.applyCommon(p, dto);
    JaxbReflect.setEnumOrKeep(p, "setObjectBelonging", d.objectBelonging);
    JaxbReflect.setOptional(p, "setExtendedConfigurationObject", nullIfBlank(d.extendedConfigurationObject));
    if (d.type != null) {
      MdTypeDescriptionBridge.apply(JaxbReflect.ensureOptional(p, "getType", "setType"), d.type);
    }
    JaxbReflect.setEnumOrKeep(p, "setAutoUse", d.autoUse);
    JaxbReflect.setEnumOrKeep(p, "setDataSeparation", d.dataSeparation);
    JaxbReflect.setEnumOrKeep(p, "setSeparatedDataUse", d.separatedDataUse);
    JaxbReflect.setOptional(p, "setDataSeparationValue", d.dataSeparationValue);
    JaxbReflect.setOptional(p, "setDataSeparationUse", d.dataSeparationUse);
    JaxbReflect.setOptional(p, "setConditionalSeparation", d.conditionalSeparation);
    JaxbReflect.setEnumOrKeep(p, "setUsersSeparation", d.usersSeparation);
    JaxbReflect.setEnumOrKeep(p, "setAuthenticationSeparation", d.authenticationSeparation);
    JaxbReflect.setEnumOrKeep(p, "setConfigurationExtensionsSeparation", d.configurationExtensionsSeparation);
    JaxbReflect.setEnumOrKeep(p, "setIndexing", d.indexing);
    JaxbReflect.setEnumOrKeep(p, "setFullTextSearch", d.fullTextSearch);
    JaxbReflect.setEnumOrKeep(p, "setDataHistory", d.dataHistory);
    MdPropertiesBridgeSupport.ensureAndSetRu(p, "getToolTip", "setToolTip", d.toolTipRu);
    JaxbReflect.setOptional(p, "setPasswordMode", d.passwordMode);
    JaxbReflect.setOptional(p, "setMultiLine", d.multiLine);
    JaxbReflect.setOptional(p, "setMask", d.mask);
    JaxbReflect.setEnumOrKeep(p, "setQuickChoice", d.quickChoice);
    JaxbReflect.setEnumOrKeep(p, "setCreateOnInput", d.createOnInput);
    JaxbReflect.setEnumOrKeep(p, "setChoiceHistoryOnInput", d.choiceHistoryOnInput);
    JaxbReflect.setEnumOrKeep(p, "setFillChecking", d.fillChecking);
    JaxbReflect.setOptional(p, "setChoiceForm", d.choiceForm);
  }
}
