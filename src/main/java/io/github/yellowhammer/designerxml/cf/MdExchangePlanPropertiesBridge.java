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

import static io.github.yellowhammer.designerxml.cf.MdPropertiesBridgeSupport.enumName;
import static io.github.yellowhammer.designerxml.cf.MdPropertiesBridgeSupport.ensureAndSetRu;
import static io.github.yellowhammer.designerxml.cf.MdPropertiesBridgeSupport.nullIfBlank;

/**
 * Чтение и запись {@code ExchangePlanProperties} через JAXB-рефлексию.
 *
 * <p>Состав плана обмена ({@code Content}) не трогаем: он правится своими операциями.
 */
public final class MdExchangePlanPropertiesBridge {

  private MdExchangePlanPropertiesBridge() {
  }

  public static void read(SchemaVersion version, Object p, MdObjectPropertiesDto dto) {
    MdExchangePlanPropertiesDto d = new MdExchangePlanPropertiesDto();
    d.objectBelonging = enumName(p, "getObjectBelonging");
    d.extendedConfigurationObject = nullIfBlank(JaxbReflect.getStringOptional(p, "getExtendedConfigurationObject"));
    d.useStandardCommands = JaxbReflect.getBooleanOptional(p, "isUseStandardCommands");
    d.codeLength = MdPropertiesBridgeSupport.decimalOrZero(p, "getCodeLength");
    d.codeAllowedLength = enumName(p, "getCodeAllowedLength");
    d.descriptionLength = MdPropertiesBridgeSupport.decimalOrZero(p, "getDescriptionLength");
    d.defaultPresentation = enumName(p, "getDefaultPresentation");
    d.editType = enumName(p, "getEditType");
    d.quickChoice = JaxbReflect.getBooleanOptional(p, "isQuickChoice");
    d.choiceMode = enumName(p, "getChoiceMode");
    MdPropertiesBridgeSupport.addFields(d.inputByString, JaxbReflect.getOptional(p, "getInputByString"));
    d.searchStringModeOnInputByString = enumName(p, "getSearchStringModeOnInputByString");
    d.fullTextSearchOnInputByString = enumName(p, "getFullTextSearchOnInputByString");
    d.choiceDataGetModeOnInputByString = enumName(p, "getChoiceDataGetModeOnInputByString");
    d.choiceHistoryOnInput = enumName(p, "getChoiceHistoryOnInput");
    d.createOnInput = enumName(p, "getCreateOnInput");
    d.defaultObjectForm = JaxbReflect.getStringOptional(p, "getDefaultObjectForm");
    d.defaultListForm = JaxbReflect.getStringOptional(p, "getDefaultListForm");
    d.defaultChoiceForm = JaxbReflect.getStringOptional(p, "getDefaultChoiceForm");
    d.auxiliaryObjectForm = JaxbReflect.getStringOptional(p, "getAuxiliaryObjectForm");
    d.auxiliaryListForm = JaxbReflect.getStringOptional(p, "getAuxiliaryListForm");
    d.auxiliaryChoiceForm = JaxbReflect.getStringOptional(p, "getAuxiliaryChoiceForm");
    d.objectModule = JaxbReflect.getStringOptional(p, "getObjectModule");
    d.managerModule = JaxbReflect.getStringOptional(p, "getManagerModule");
    d.standardAttributesXml = MdPropertiesBridgeSupport.marshalStandardAttributesOrEmpty(
      version, JaxbReflect.getOptional(p, "getStandardAttributes"));
    d.characteristicsXml = MdPropertiesBridgeSupport.marshalCharacteristicsOrEmpty(
      version, JaxbReflect.getOptional(p, "getCharacteristics"));
    MdPropertiesBridgeSupport.addItems(d.basedOn, JaxbReflect.getOptional(p, "getBasedOn"));
    d.distributedInfoBase = JaxbReflect.getBooleanOptional(p, "isDistributedInfoBase");
    d.includeConfigurationExtensions = JaxbReflect.getBooleanOptional(p, "isIncludeConfigurationExtensions");
    d.includeHelpInContents = JaxbReflect.getBooleanOptional(p, "isIncludeHelpInContents");
    MdPropertiesBridgeSupport.addFields(d.dataLockFields, JaxbReflect.getOptional(p, "getDataLockFields"));
    d.dataLockControlMode = enumName(p, "getDataLockControlMode");
    d.fullTextSearch = enumName(p, "getFullTextSearch");
    d.objectPresentationRu = LocalStringSync.firstRu(JaxbReflect.getOptional(p, "getObjectPresentation"));
    d.extendedObjectPresentationRu =
      LocalStringSync.firstRu(JaxbReflect.getOptional(p, "getExtendedObjectPresentation"));
    d.listPresentationRu = LocalStringSync.firstRu(JaxbReflect.getOptional(p, "getListPresentation"));
    d.extendedListPresentationRu = LocalStringSync.firstRu(JaxbReflect.getOptional(p, "getExtendedListPresentation"));
    d.explanationRu = LocalStringSync.firstRu(JaxbReflect.getOptional(p, "getExplanation"));
    d.dataHistory = enumName(p, "getDataHistory");
    d.updateDataHistoryImmediatelyAfterWrite =
      JaxbReflect.getBooleanOptional(p, "isUpdateDataHistoryImmediatelyAfterWrite");
    d.executeAfterWriteDataHistoryVersionProcessing =
      JaxbReflect.getBooleanOptional(p, "isExecuteAfterWriteDataHistoryVersionProcessing");
    d.additionalIndexes = JaxbReflect.getStringOptional(p, "getAdditionalIndexes");
    dto.exchangePlan = d;
  }

  public static void apply(SchemaVersion version, Object p, MdObjectPropertiesDto dto) {
    MdExchangePlanPropertiesDto d = dto.exchangePlan;
    if (d == null) {
      throw new IllegalArgumentException("exchangePlan required");
    }
    MdPropertiesBridgeSupport.applyCommon(p, dto);
    JaxbReflect.setEnumOrKeep(p, "setObjectBelonging", d.objectBelonging);
    JaxbReflect.setOptional(p, "setExtendedConfigurationObject", nullIfBlank(d.extendedConfigurationObject));
    JaxbReflect.setOptional(p, "setUseStandardCommands", d.useStandardCommands);
    MdPropertiesBridgeSupport.setDecimal(p, "setCodeLength", d.codeLength);
    JaxbReflect.setEnumOrKeep(p, "setCodeAllowedLength", d.codeAllowedLength);
    MdPropertiesBridgeSupport.setDecimal(p, "setDescriptionLength", d.descriptionLength);
    JaxbReflect.setEnumOrKeep(p, "setDefaultPresentation", d.defaultPresentation);
    JaxbReflect.setEnumOrKeep(p, "setEditType", d.editType);
    JaxbReflect.setOptional(p, "setQuickChoice", d.quickChoice);
    JaxbReflect.setEnumOrKeep(p, "setChoiceMode", d.choiceMode);
    MdPropertiesBridgeSupport.setFields(JaxbReflect.getOptional(p, "getInputByString"), d.inputByString);
    JaxbReflect.setEnumOrKeep(p, "setSearchStringModeOnInputByString", d.searchStringModeOnInputByString);
    JaxbReflect.setEnumOrKeep(p, "setFullTextSearchOnInputByString", d.fullTextSearchOnInputByString);
    JaxbReflect.setEnumOrKeep(p, "setChoiceDataGetModeOnInputByString", d.choiceDataGetModeOnInputByString);
    JaxbReflect.setEnumOrKeep(p, "setChoiceHistoryOnInput", d.choiceHistoryOnInput);
    JaxbReflect.setEnumOrKeep(p, "setCreateOnInput", d.createOnInput);
    JaxbReflect.setOptional(p, "setDefaultObjectForm", d.defaultObjectForm);
    JaxbReflect.setOptional(p, "setDefaultListForm", d.defaultListForm);
    JaxbReflect.setOptional(p, "setDefaultChoiceForm", d.defaultChoiceForm);
    JaxbReflect.setOptional(p, "setAuxiliaryObjectForm", d.auxiliaryObjectForm);
    JaxbReflect.setOptional(p, "setAuxiliaryListForm", d.auxiliaryListForm);
    JaxbReflect.setOptional(p, "setAuxiliaryChoiceForm", d.auxiliaryChoiceForm);
    JaxbReflect.setOptional(p, "setObjectModule", d.objectModule);
    JaxbReflect.setOptional(p, "setManagerModule", d.managerModule);
    MdPropertiesBridgeSupport.applyStandardAttributes(version, p, d.standardAttributesXml);
    MdPropertiesBridgeSupport.applyCharacteristics(version, p, d.characteristicsXml);
    MdListTypeRefs.replaceItems(JaxbReflect.getOptional(p, "getBasedOn"), d.basedOn);
    JaxbReflect.setOptional(p, "setDistributedInfoBase", d.distributedInfoBase);
    JaxbReflect.setOptional(p, "setIncludeConfigurationExtensions", d.includeConfigurationExtensions);
    JaxbReflect.setOptional(p, "setIncludeHelpInContents", d.includeHelpInContents);
    MdPropertiesBridgeSupport.setFields(JaxbReflect.getOptional(p, "getDataLockFields"), d.dataLockFields);
    JaxbReflect.setEnumOrKeep(p, "setDataLockControlMode", d.dataLockControlMode);
    JaxbReflect.setEnumOrKeep(p, "setFullTextSearch", d.fullTextSearch);
    ensureAndSetRu(p, "getObjectPresentation", "setObjectPresentation", d.objectPresentationRu);
    ensureAndSetRu(p, "getExtendedObjectPresentation", "setExtendedObjectPresentation",
      d.extendedObjectPresentationRu);
    ensureAndSetRu(p, "getListPresentation", "setListPresentation", d.listPresentationRu);
    ensureAndSetRu(p, "getExtendedListPresentation", "setExtendedListPresentation", d.extendedListPresentationRu);
    ensureAndSetRu(p, "getExplanation", "setExplanation", d.explanationRu);
    JaxbReflect.setEnumOrKeep(p, "setDataHistory", d.dataHistory);
    JaxbReflect.setOptional(p, "setUpdateDataHistoryImmediatelyAfterWrite", d.updateDataHistoryImmediatelyAfterWrite);
    JaxbReflect.setOptional(p, "setExecuteAfterWriteDataHistoryVersionProcessing",
      d.executeAfterWriteDataHistoryVersionProcessing);
    JaxbReflect.setOptional(p, "setAdditionalIndexes", d.additionalIndexes);
  }

}
