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
 * Чтение и запись {@code ChartOfCalculationTypesProperties} через JAXB-рефлексию.
 *
 * <p>Вытесняющие и ведущие виды расчёта живут в составе объекта, а не в свойствах: здесь их нет.
 */
public final class MdChartOfCalculationTypesPropertiesBridge {

  private MdChartOfCalculationTypesPropertiesBridge() {
  }

  public static void read(SchemaVersion version, Object p, MdObjectPropertiesDto dto) {
    MdChartOfCalculationTypesPropertiesDto d = new MdChartOfCalculationTypesPropertiesDto();
    d.objectBelonging = enumName(p, "getObjectBelonging");
    d.extendedConfigurationObject = nullIfBlank(JaxbReflect.getStringOptional(p, "getExtendedConfigurationObject"));
    d.useStandardCommands = JaxbReflect.getBooleanOptional(p, "isUseStandardCommands");
    d.codeLength = MdPropertiesBridgeSupport.decimalOrZero(p, "getCodeLength");
    d.descriptionLength = MdPropertiesBridgeSupport.decimalOrZero(p, "getDescriptionLength");
    d.codeType = enumName(p, "getCodeType");
    d.codeAllowedLength = enumName(p, "getCodeAllowedLength");
    d.editType = enumName(p, "getEditType");
    MdPropertiesBridgeSupport.addFields(d.inputByString, JaxbReflect.getOptional(p, "getInputByString"));
    d.createOnInput = enumName(p, "getCreateOnInput");
    d.searchStringModeOnInputByString = enumName(p, "getSearchStringModeOnInputByString");
    d.choiceDataGetModeOnInputByString = enumName(p, "getChoiceDataGetModeOnInputByString");
    d.fullTextSearchOnInputByString = enumName(p, "getFullTextSearchOnInputByString");
    d.choiceHistoryOnInput = enumName(p, "getChoiceHistoryOnInput");
    d.defaultObjectForm = JaxbReflect.getStringOptional(p, "getDefaultObjectForm");
    d.defaultListForm = JaxbReflect.getStringOptional(p, "getDefaultListForm");
    d.defaultChoiceForm = JaxbReflect.getStringOptional(p, "getDefaultChoiceForm");
    d.auxiliaryObjectForm = JaxbReflect.getStringOptional(p, "getAuxiliaryObjectForm");
    d.auxiliaryListForm = JaxbReflect.getStringOptional(p, "getAuxiliaryListForm");
    d.auxiliaryChoiceForm = JaxbReflect.getStringOptional(p, "getAuxiliaryChoiceForm");
    d.objectModule = JaxbReflect.getStringOptional(p, "getObjectModule");
    d.managerModule = JaxbReflect.getStringOptional(p, "getManagerModule");
    MdPropertiesBridgeSupport.addItems(d.basedOn, JaxbReflect.getOptional(p, "getBasedOn"));
    d.dependenceOnCalculationTypes = enumName(p, "getDependenceOnCalculationTypes");
    MdPropertiesBridgeSupport.addItems(d.baseCalculationTypes, JaxbReflect.getOptional(p, "getBaseCalculationTypes"));
    d.actionPeriodUse = JaxbReflect.getBooleanOptional(p, "isActionPeriodUse");
    d.standardAttributesXml = MdPropertiesBridgeSupport.marshalStandardAttributesOrEmpty(
      version, JaxbReflect.getOptional(p, "getStandardAttributes"));
    d.characteristicsXml = MdPropertiesBridgeSupport.marshalCharacteristicsOrEmpty(
      version, JaxbReflect.getOptional(p, "getCharacteristics"));
    d.predefinedDataUpdate = enumName(p, "getPredefinedDataUpdate");
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
    dto.chartOfCalculationTypes = d;
  }

  public static void apply(SchemaVersion version, Object p, MdObjectPropertiesDto dto) {
    MdChartOfCalculationTypesPropertiesDto d = dto.chartOfCalculationTypes;
    if (d == null) {
      throw new IllegalArgumentException("chartOfCalculationTypes required");
    }
    MdPropertiesBridgeSupport.applyCommon(p, dto);
    JaxbReflect.setEnumOrKeep(p, "setObjectBelonging", d.objectBelonging);
    JaxbReflect.setOptional(p, "setExtendedConfigurationObject", nullIfBlank(d.extendedConfigurationObject));
    JaxbReflect.setOptional(p, "setUseStandardCommands", d.useStandardCommands);
    MdPropertiesBridgeSupport.setDecimal(p, "setCodeLength", d.codeLength);
    MdPropertiesBridgeSupport.setDecimal(p, "setDescriptionLength", d.descriptionLength);
    JaxbReflect.setEnumOrKeep(p, "setCodeType", d.codeType);
    JaxbReflect.setEnumOrKeep(p, "setCodeAllowedLength", d.codeAllowedLength);
    JaxbReflect.setEnumOrKeep(p, "setEditType", d.editType);
    MdPropertiesBridgeSupport.setFields(JaxbReflect.getOptional(p, "getInputByString"), d.inputByString);
    JaxbReflect.setEnumOrKeep(p, "setCreateOnInput", d.createOnInput);
    JaxbReflect.setEnumOrKeep(p, "setSearchStringModeOnInputByString", d.searchStringModeOnInputByString);
    JaxbReflect.setEnumOrKeep(p, "setChoiceDataGetModeOnInputByString", d.choiceDataGetModeOnInputByString);
    JaxbReflect.setEnumOrKeep(p, "setFullTextSearchOnInputByString", d.fullTextSearchOnInputByString);
    JaxbReflect.setEnumOrKeep(p, "setChoiceHistoryOnInput", d.choiceHistoryOnInput);
    JaxbReflect.setOptional(p, "setDefaultObjectForm", d.defaultObjectForm);
    JaxbReflect.setOptional(p, "setDefaultListForm", d.defaultListForm);
    JaxbReflect.setOptional(p, "setDefaultChoiceForm", d.defaultChoiceForm);
    JaxbReflect.setOptional(p, "setAuxiliaryObjectForm", d.auxiliaryObjectForm);
    JaxbReflect.setOptional(p, "setAuxiliaryListForm", d.auxiliaryListForm);
    JaxbReflect.setOptional(p, "setAuxiliaryChoiceForm", d.auxiliaryChoiceForm);
    JaxbReflect.setOptional(p, "setObjectModule", d.objectModule);
    JaxbReflect.setOptional(p, "setManagerModule", d.managerModule);
    MdListTypeRefs.replaceItems(JaxbReflect.getOptional(p, "getBasedOn"), d.basedOn);
    JaxbReflect.setEnumOrKeep(p, "setDependenceOnCalculationTypes", d.dependenceOnCalculationTypes);
    MdListTypeRefs.replaceItems(JaxbReflect.getOptional(p, "getBaseCalculationTypes"), d.baseCalculationTypes);
    JaxbReflect.setOptional(p, "setActionPeriodUse", d.actionPeriodUse);
    MdPropertiesBridgeSupport.applyStandardAttributes(version, p, d.standardAttributesXml);
    MdPropertiesBridgeSupport.applyCharacteristics(version, p, d.characteristicsXml);
    JaxbReflect.setEnumOrKeep(p, "setPredefinedDataUpdate", d.predefinedDataUpdate);
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
