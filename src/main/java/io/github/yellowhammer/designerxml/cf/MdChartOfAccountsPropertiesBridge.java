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
 * Чтение и запись {@code ChartOfAccountsProperties} через JAXB-рефлексию.
 *
 * <p>Признаки учёта и стандартные табличные части не трогаем: они правятся своими операциями.
 */
public final class MdChartOfAccountsPropertiesBridge {

  private MdChartOfAccountsPropertiesBridge() {
  }

  public static void read(SchemaVersion version, Object p, MdObjectPropertiesDto dto) {
    MdChartOfAccountsPropertiesDto d = new MdChartOfAccountsPropertiesDto();
    d.objectBelonging = enumName(p, "getObjectBelonging");
    d.extendedConfigurationObject = nullIfBlank(JaxbReflect.getStringOptional(p, "getExtendedConfigurationObject"));
    d.useStandardCommands = JaxbReflect.getBooleanOptional(p, "isUseStandardCommands");
    d.includeHelpInContents = JaxbReflect.getBooleanOptional(p, "isIncludeHelpInContents");
    MdPropertiesBridgeSupport.addItems(d.basedOn, JaxbReflect.getOptional(p, "getBasedOn"));
    d.extDimensionTypes = JaxbReflect.getStringOptional(p, "getExtDimensionTypes");
    d.maxExtDimensionCount = MdPropertiesBridgeSupport.decimalOrZero(p, "getMaxExtDimensionCount");
    d.codeMask = JaxbReflect.getStringOptional(p, "getCodeMask");
    d.codeLength = MdPropertiesBridgeSupport.decimalOrZero(p, "getCodeLength");
    d.descriptionLength = MdPropertiesBridgeSupport.decimalOrZero(p, "getDescriptionLength");
    d.codeSeries = enumName(p, "getCodeSeries");
    d.checkUnique = JaxbReflect.getBooleanOptional(p, "isCheckUnique");
    d.defaultPresentation = enumName(p, "getDefaultPresentation");
    d.standardAttributesXml = MdPropertiesBridgeSupport.marshalStandardAttributesOrEmpty(
      version, JaxbReflect.getOptional(p, "getStandardAttributes"));
    d.characteristicsXml = MdPropertiesBridgeSupport.marshalCharacteristicsOrEmpty(
      version, JaxbReflect.getOptional(p, "getCharacteristics"));
    d.predefinedDataUpdate = enumName(p, "getPredefinedDataUpdate");
    d.editType = enumName(p, "getEditType");
    d.quickChoice = JaxbReflect.getBooleanOptional(p, "isQuickChoice");
    d.choiceMode = enumName(p, "getChoiceMode");
    MdPropertiesBridgeSupport.addFields(d.inputByString, JaxbReflect.getOptional(p, "getInputByString"));
    d.searchStringModeOnInputByString = enumName(p, "getSearchStringModeOnInputByString");
    d.fullTextSearchOnInputByString = enumName(p, "getFullTextSearchOnInputByString");
    d.choiceDataGetModeOnInputByString = enumName(p, "getChoiceDataGetModeOnInputByString");
    d.createOnInput = enumName(p, "getCreateOnInput");
    d.choiceHistoryOnInput = enumName(p, "getChoiceHistoryOnInput");
    d.defaultObjectForm = JaxbReflect.getStringOptional(p, "getDefaultObjectForm");
    d.defaultListForm = JaxbReflect.getStringOptional(p, "getDefaultListForm");
    d.defaultChoiceForm = JaxbReflect.getStringOptional(p, "getDefaultChoiceForm");
    d.auxiliaryObjectForm = JaxbReflect.getStringOptional(p, "getAuxiliaryObjectForm");
    d.auxiliaryListForm = JaxbReflect.getStringOptional(p, "getAuxiliaryListForm");
    d.auxiliaryChoiceForm = JaxbReflect.getStringOptional(p, "getAuxiliaryChoiceForm");
    d.objectModule = JaxbReflect.getStringOptional(p, "getObjectModule");
    d.managerModule = JaxbReflect.getStringOptional(p, "getManagerModule");
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
    dto.chartOfAccounts = d;
  }

  public static void apply(SchemaVersion version, Object p, MdObjectPropertiesDto dto) {
    MdChartOfAccountsPropertiesDto d = dto.chartOfAccounts;
    if (d == null) {
      throw new IllegalArgumentException("chartOfAccounts required");
    }
    MdPropertiesBridgeSupport.applyCommon(p, dto);
    JaxbReflect.setEnumOrKeep(p, "setObjectBelonging", d.objectBelonging);
    JaxbReflect.setOptional(p, "setExtendedConfigurationObject", nullIfBlank(d.extendedConfigurationObject));
    JaxbReflect.setOptional(p, "setUseStandardCommands", d.useStandardCommands);
    JaxbReflect.setOptional(p, "setIncludeHelpInContents", d.includeHelpInContents);
    MdListTypeRefs.replaceItems(JaxbReflect.getOptional(p, "getBasedOn"), d.basedOn);
    JaxbReflect.setOptional(p, "setExtDimensionTypes", d.extDimensionTypes);
    MdPropertiesBridgeSupport.setDecimal(p, "setMaxExtDimensionCount", d.maxExtDimensionCount);
    JaxbReflect.setOptional(p, "setCodeMask", d.codeMask);
    MdPropertiesBridgeSupport.setDecimal(p, "setCodeLength", d.codeLength);
    MdPropertiesBridgeSupport.setDecimal(p, "setDescriptionLength", d.descriptionLength);
    JaxbReflect.setEnumOrKeep(p, "setCodeSeries", d.codeSeries);
    JaxbReflect.setOptional(p, "setCheckUnique", d.checkUnique);
    JaxbReflect.setEnumOrKeep(p, "setDefaultPresentation", d.defaultPresentation);
    MdPropertiesBridgeSupport.applyStandardAttributes(version, p, d.standardAttributesXml);
    MdPropertiesBridgeSupport.applyCharacteristics(version, p, d.characteristicsXml);
    JaxbReflect.setEnumOrKeep(p, "setPredefinedDataUpdate", d.predefinedDataUpdate);
    JaxbReflect.setEnumOrKeep(p, "setEditType", d.editType);
    JaxbReflect.setOptional(p, "setQuickChoice", d.quickChoice);
    JaxbReflect.setEnumOrKeep(p, "setChoiceMode", d.choiceMode);
    MdPropertiesBridgeSupport.setFields(JaxbReflect.getOptional(p, "getInputByString"), d.inputByString);
    JaxbReflect.setEnumOrKeep(p, "setSearchStringModeOnInputByString", d.searchStringModeOnInputByString);
    JaxbReflect.setEnumOrKeep(p, "setFullTextSearchOnInputByString", d.fullTextSearchOnInputByString);
    JaxbReflect.setEnumOrKeep(p, "setChoiceDataGetModeOnInputByString", d.choiceDataGetModeOnInputByString);
    JaxbReflect.setEnumOrKeep(p, "setCreateOnInput", d.createOnInput);
    JaxbReflect.setEnumOrKeep(p, "setChoiceHistoryOnInput", d.choiceHistoryOnInput);
    JaxbReflect.setOptional(p, "setDefaultObjectForm", d.defaultObjectForm);
    JaxbReflect.setOptional(p, "setDefaultListForm", d.defaultListForm);
    JaxbReflect.setOptional(p, "setDefaultChoiceForm", d.defaultChoiceForm);
    JaxbReflect.setOptional(p, "setAuxiliaryObjectForm", d.auxiliaryObjectForm);
    JaxbReflect.setOptional(p, "setAuxiliaryListForm", d.auxiliaryListForm);
    JaxbReflect.setOptional(p, "setAuxiliaryChoiceForm", d.auxiliaryChoiceForm);
    JaxbReflect.setOptional(p, "setObjectModule", d.objectModule);
    JaxbReflect.setOptional(p, "setManagerModule", d.managerModule);
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
