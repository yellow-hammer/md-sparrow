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
import static io.github.yellowhammer.designerxml.cf.MdPropertiesBridgeSupport.ensureAndSetRu;
import static io.github.yellowhammer.designerxml.cf.MdPropertiesBridgeSupport.nullIfBlank;

/**
 * Чтение и запись {@code ConstantProperties} через JAXB-рефлексию.
 */
public final class MdConstantPropertiesBridge {

  private MdConstantPropertiesBridge() {
  }

  public static void read(Object p, MdObjectPropertiesDto dto) {
    MdConstantPropertiesDto d = new MdConstantPropertiesDto();
    d.objectBelonging = enumName(p, "getObjectBelonging");
    d.extendedConfigurationObject = nullIfBlank(JaxbReflect.getStringOptional(p, "getExtendedConfigurationObject"));
    d.type = MdTypeDescriptionBridge.read(JaxbReflect.getOptional(p, "getType"));
    d.useStandardCommands = JaxbReflect.getBooleanOptional(p, "isUseStandardCommands");
    d.defaultForm = JaxbReflect.getStringOptional(p, "getDefaultForm");
    d.extendedPresentation = LocalStringSync.first(JaxbReflect.getOptional(p, "getExtendedPresentation"));
    d.explanation = LocalStringSync.first(JaxbReflect.getOptional(p, "getExplanation"));
    d.passwordMode = JaxbReflect.getBooleanOptional(p, "isPasswordMode");
    d.format = LocalStringSync.first(JaxbReflect.getOptional(p, "getFormat"));
    d.editFormat = LocalStringSync.first(JaxbReflect.getOptional(p, "getEditFormat"));
    d.toolTip = LocalStringSync.first(JaxbReflect.getOptional(p, "getToolTip"));
    d.markNegatives = JaxbReflect.getBooleanOptional(p, "isMarkNegatives");
    d.mask = JaxbReflect.getStringOptional(p, "getMask");
    d.multiLine = JaxbReflect.getBooleanOptional(p, "isMultiLine");
    d.extendedEdit = JaxbReflect.getBooleanOptional(p, "isExtendedEdit");
    d.fillChecking = enumName(p, "getFillChecking");
    d.choiceFoldersAndItems = enumName(p, "getChoiceFoldersAndItems");
    d.quickChoice = enumName(p, "getQuickChoice");
    d.choiceForm = JaxbReflect.getStringOptional(p, "getChoiceForm");
    d.choiceHistoryOnInput = enumName(p, "getChoiceHistoryOnInput");
    d.valueManagerModule = JaxbReflect.getStringOptional(p, "getValueManagerModule");
    d.managerModule = JaxbReflect.getStringOptional(p, "getManagerModule");
    d.dataLockControlMode = enumName(p, "getDataLockControlMode");
    d.dataHistory = enumName(p, "getDataHistory");
    d.updateDataHistoryImmediatelyAfterWrite =
      JaxbReflect.getBooleanOptional(p, "isUpdateDataHistoryImmediatelyAfterWrite");
    d.executeAfterWriteDataHistoryVersionProcessing =
      JaxbReflect.getBooleanOptional(p, "isExecuteAfterWriteDataHistoryVersionProcessing");
    dto.constant = d;
  }

  public static void apply(Object p, MdObjectPropertiesDto dto) {
    MdConstantPropertiesDto d = dto.constant;
    if (d == null) {
      throw new IllegalArgumentException("constant required");
    }
    MdPropertiesBridgeSupport.applyCommon(p, dto);
    JaxbReflect.setEnumOrKeep(p, "setObjectBelonging", d.objectBelonging);
    JaxbReflect.setOptional(p, "setExtendedConfigurationObject", nullIfBlank(d.extendedConfigurationObject));
    if (d.type != null) {
      MdTypeDescriptionBridge.apply(JaxbReflect.ensureOptional(p, "getType", "setType"), d.type);
    }
    JaxbReflect.setOptional(p, "setUseStandardCommands", d.useStandardCommands);
    JaxbReflect.setOptional(p, "setDefaultForm", d.defaultForm);
    ensureAndSetRu(p, "getExtendedPresentation", "setExtendedPresentation", d.extendedPresentation);
    ensureAndSetRu(p, "getExplanation", "setExplanation", d.explanation);
    JaxbReflect.setOptional(p, "setPasswordMode", d.passwordMode);
    ensureAndSetRu(p, "getFormat", "setFormat", d.format);
    ensureAndSetRu(p, "getEditFormat", "setEditFormat", d.editFormat);
    ensureAndSetRu(p, "getToolTip", "setToolTip", d.toolTip);
    JaxbReflect.setOptional(p, "setMarkNegatives", d.markNegatives);
    JaxbReflect.setOptional(p, "setMask", d.mask);
    JaxbReflect.setOptional(p, "setMultiLine", d.multiLine);
    JaxbReflect.setOptional(p, "setExtendedEdit", d.extendedEdit);
    JaxbReflect.setEnumOrKeep(p, "setFillChecking", d.fillChecking);
    JaxbReflect.setEnumOrKeep(p, "setChoiceFoldersAndItems", d.choiceFoldersAndItems);
    JaxbReflect.setEnumOrKeep(p, "setQuickChoice", d.quickChoice);
    JaxbReflect.setOptional(p, "setChoiceForm", d.choiceForm);
    JaxbReflect.setEnumOrKeep(p, "setChoiceHistoryOnInput", d.choiceHistoryOnInput);
    JaxbReflect.setOptional(p, "setValueManagerModule", d.valueManagerModule);
    JaxbReflect.setOptional(p, "setManagerModule", d.managerModule);
    JaxbReflect.setEnumOrKeep(p, "setDataLockControlMode", d.dataLockControlMode);
    JaxbReflect.setEnumOrKeep(p, "setDataHistory", d.dataHistory);
    JaxbReflect.setOptional(p, "setUpdateDataHistoryImmediatelyAfterWrite", d.updateDataHistoryImmediatelyAfterWrite);
    JaxbReflect.setOptional(p, "setExecuteAfterWriteDataHistoryVersionProcessing",
      d.executeAfterWriteDataHistoryVersionProcessing);
  }
}
