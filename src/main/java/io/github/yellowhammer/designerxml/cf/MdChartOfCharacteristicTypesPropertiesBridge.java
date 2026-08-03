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
 * Чтение и запись {@code ChartOfCharacteristicTypesProperties} через JAXB-рефлексию.
 *
 * <p>Предопределённые характеристики ({@code Predefined}) не трогаем: они правятся своими
 * операциями.
 */
public final class MdChartOfCharacteristicTypesPropertiesBridge {

  private MdChartOfCharacteristicTypesPropertiesBridge() {
  }

  public static void read(SchemaVersion version, Object p, MdObjectPropertiesDto dto) {
    MdChartOfCharacteristicTypesPropertiesDto d = new MdChartOfCharacteristicTypesPropertiesDto();
    d.objectBelonging = enumName(p, "getObjectBelonging");
    d.extendedConfigurationObject = nullIfBlank(JaxbReflect.getStringOptional(p, "getExtendedConfigurationObject"));
    d.useStandardCommands = JaxbReflect.getBooleanOptional(p, "isUseStandardCommands");
    d.includeHelpInContents = JaxbReflect.getBooleanOptional(p, "isIncludeHelpInContents");
    d.characteristicExtValues = JaxbReflect.getStringOptional(p, "getCharacteristicExtValues");
    d.type = MdTypeDescriptionBridge.read(JaxbReflect.getOptional(p, "getType"));
    d.hierarchical = JaxbReflect.getBooleanOptional(p, "isHierarchical");
    d.foldersOnTop = JaxbReflect.getBooleanOptional(p, "isFoldersOnTop");
    d.codeLength = MdPropertiesBridgeSupport.decimalOrZero(p, "getCodeLength");
    d.codeAllowedLength = enumName(p, "getCodeAllowedLength");
    d.descriptionLength = MdPropertiesBridgeSupport.decimalOrZero(p, "getDescriptionLength");
    d.codeSeries = enumName(p, "getCodeSeries");
    d.checkUnique = JaxbReflect.getBooleanOptional(p, "isCheckUnique");
    d.autonumbering = JaxbReflect.getBooleanOptional(p, "isAutonumbering");
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
    d.createOnInput = enumName(p, "getCreateOnInput");
    d.searchStringModeOnInputByString = enumName(p, "getSearchStringModeOnInputByString");
    d.choiceDataGetModeOnInputByString = enumName(p, "getChoiceDataGetModeOnInputByString");
    d.fullTextSearchOnInputByString = enumName(p, "getFullTextSearchOnInputByString");
    d.choiceHistoryOnInput = enumName(p, "getChoiceHistoryOnInput");
    d.defaultObjectForm = JaxbReflect.getStringOptional(p, "getDefaultObjectForm");
    d.defaultFolderForm = JaxbReflect.getStringOptional(p, "getDefaultFolderForm");
    d.defaultListForm = JaxbReflect.getStringOptional(p, "getDefaultListForm");
    d.defaultChoiceForm = JaxbReflect.getStringOptional(p, "getDefaultChoiceForm");
    d.defaultFolderChoiceForm = JaxbReflect.getStringOptional(p, "getDefaultFolderChoiceForm");
    d.auxiliaryObjectForm = JaxbReflect.getStringOptional(p, "getAuxiliaryObjectForm");
    d.auxiliaryFolderForm = JaxbReflect.getStringOptional(p, "getAuxiliaryFolderForm");
    d.auxiliaryListForm = JaxbReflect.getStringOptional(p, "getAuxiliaryListForm");
    d.auxiliaryChoiceForm = JaxbReflect.getStringOptional(p, "getAuxiliaryChoiceForm");
    d.auxiliaryFolderChoiceForm = JaxbReflect.getStringOptional(p, "getAuxiliaryFolderChoiceForm");
    d.objectModule = JaxbReflect.getStringOptional(p, "getObjectModule");
    d.managerModule = JaxbReflect.getStringOptional(p, "getManagerModule");
    MdPropertiesBridgeSupport.addItems(d.basedOn, JaxbReflect.getOptional(p, "getBasedOn"));
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
    dto.chartOfCharacteristicTypes = d;
  }

  public static void apply(SchemaVersion version, Object p, MdObjectPropertiesDto dto) {
    MdChartOfCharacteristicTypesPropertiesDto d = dto.chartOfCharacteristicTypes;
    if (d == null) {
      throw new IllegalArgumentException("chartOfCharacteristicTypes required");
    }
    MdPropertiesBridgeSupport.applyCommon(p, dto);
    JaxbReflect.setEnumOrKeep(p, "setObjectBelonging", d.objectBelonging);
    JaxbReflect.setOptional(p, "setExtendedConfigurationObject", nullIfBlank(d.extendedConfigurationObject));
    JaxbReflect.setOptional(p, "setUseStandardCommands", d.useStandardCommands);
    JaxbReflect.setOptional(p, "setIncludeHelpInContents", d.includeHelpInContents);
    JaxbReflect.setOptional(p, "setCharacteristicExtValues", d.characteristicExtValues);
    if (d.type != null) {
      MdTypeDescriptionBridge.apply(JaxbReflect.ensureOptional(p, "getType", "setType"), d.type);
    }
    JaxbReflect.setOptional(p, "setHierarchical", d.hierarchical);
    JaxbReflect.setOptional(p, "setFoldersOnTop", d.foldersOnTop);
    MdPropertiesBridgeSupport.setDecimal(p, "setCodeLength", d.codeLength);
    JaxbReflect.setEnumOrKeep(p, "setCodeAllowedLength", d.codeAllowedLength);
    MdPropertiesBridgeSupport.setDecimal(p, "setDescriptionLength", d.descriptionLength);
    JaxbReflect.setEnumOrKeep(p, "setCodeSeries", d.codeSeries);
    JaxbReflect.setOptional(p, "setCheckUnique", d.checkUnique);
    JaxbReflect.setOptional(p, "setAutonumbering", d.autonumbering);
    JaxbReflect.setEnumOrKeep(p, "setDefaultPresentation", d.defaultPresentation);
    MdPropertiesBridgeSupport.applyStandardAttributes(version, p, d.standardAttributesXml);
    MdPropertiesBridgeSupport.applyCharacteristics(version, p, d.characteristicsXml);
    JaxbReflect.setEnumOrKeep(p, "setPredefinedDataUpdate", d.predefinedDataUpdate);
    JaxbReflect.setEnumOrKeep(p, "setEditType", d.editType);
    JaxbReflect.setOptional(p, "setQuickChoice", d.quickChoice);
    JaxbReflect.setEnumOrKeep(p, "setChoiceMode", d.choiceMode);
    MdPropertiesBridgeSupport.setFields(JaxbReflect.getOptional(p, "getInputByString"), d.inputByString);
    JaxbReflect.setEnumOrKeep(p, "setCreateOnInput", d.createOnInput);
    JaxbReflect.setEnumOrKeep(p, "setSearchStringModeOnInputByString", d.searchStringModeOnInputByString);
    JaxbReflect.setEnumOrKeep(p, "setChoiceDataGetModeOnInputByString", d.choiceDataGetModeOnInputByString);
    JaxbReflect.setEnumOrKeep(p, "setFullTextSearchOnInputByString", d.fullTextSearchOnInputByString);
    JaxbReflect.setEnumOrKeep(p, "setChoiceHistoryOnInput", d.choiceHistoryOnInput);
    JaxbReflect.setOptional(p, "setDefaultObjectForm", d.defaultObjectForm);
    JaxbReflect.setOptional(p, "setDefaultFolderForm", d.defaultFolderForm);
    JaxbReflect.setOptional(p, "setDefaultListForm", d.defaultListForm);
    JaxbReflect.setOptional(p, "setDefaultChoiceForm", d.defaultChoiceForm);
    JaxbReflect.setOptional(p, "setDefaultFolderChoiceForm", d.defaultFolderChoiceForm);
    JaxbReflect.setOptional(p, "setAuxiliaryObjectForm", d.auxiliaryObjectForm);
    JaxbReflect.setOptional(p, "setAuxiliaryFolderForm", d.auxiliaryFolderForm);
    JaxbReflect.setOptional(p, "setAuxiliaryListForm", d.auxiliaryListForm);
    JaxbReflect.setOptional(p, "setAuxiliaryChoiceForm", d.auxiliaryChoiceForm);
    JaxbReflect.setOptional(p, "setAuxiliaryFolderChoiceForm", d.auxiliaryFolderChoiceForm);
    JaxbReflect.setOptional(p, "setObjectModule", d.objectModule);
    JaxbReflect.setOptional(p, "setManagerModule", d.managerModule);
    MdListTypeRefs.replaceItems(JaxbReflect.getOptional(p, "getBasedOn"), d.basedOn);
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
