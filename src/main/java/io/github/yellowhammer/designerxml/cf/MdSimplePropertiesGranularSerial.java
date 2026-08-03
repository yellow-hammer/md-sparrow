/*
 * This file is a part of md-sparrow.
 *
 * Copyright (c) 2026
 * Ivan Karlo <i.karlo@outlook.com> and contributors
 *
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package io.github.yellowhammer.designerxml.cf;

import java.util.List;

/**
 * Точечные замены прямых дочерних элементов {@code Properties} перечисления, константы, общего
 * модуля и регистров.
 */
final class MdSimplePropertiesGranularSerial {

  private MdSimplePropertiesGranularSerial() {
  }

  static void appendEnumScalarChanges(
    MdEnumPropertiesDto b,
    MdEnumPropertiesDto i,
    List<MdObjectPropertiesLeafDiff.GranularPatchChange> out) {
    MdPropertiesGranularChanges c = new MdPropertiesGranularChanges(out);
    c.enumText("ObjectBelonging", b.objectBelonging, i.objectBelonging);
    c.bool("UseStandardCommands", b.useStandardCommands, i.useStandardCommands);
    c.xmlBlob("StandardAttributes", b.standardAttributesXml, i.standardAttributesXml);
    c.xmlBlob("Characteristics", b.characteristicsXml, i.characteristicsXml);
    c.bool("QuickChoice", b.quickChoice, i.quickChoice);
    c.enumText("ChoiceMode", b.choiceMode, i.choiceMode);
    c.text("DefaultListForm", b.defaultListForm, i.defaultListForm);
    c.text("DefaultChoiceForm", b.defaultChoiceForm, i.defaultChoiceForm);
    c.text("AuxiliaryListForm", b.auxiliaryListForm, i.auxiliaryListForm);
    c.text("AuxiliaryChoiceForm", b.auxiliaryChoiceForm, i.auxiliaryChoiceForm);
    c.text("ManagerModule", b.managerModule, i.managerModule);
    c.localStringRu("ListPresentation", b.listPresentationRu, i.listPresentationRu);
    c.localStringRu("ExtendedListPresentation", b.extendedListPresentationRu, i.extendedListPresentationRu);
    c.localStringRu("Explanation", b.explanationRu, i.explanationRu);
    c.enumText("ChoiceHistoryOnInput", b.choiceHistoryOnInput, i.choiceHistoryOnInput);
  }

  static void appendConstantScalarChanges(
    MdConstantPropertiesDto b,
    MdConstantPropertiesDto i,
    List<MdObjectPropertiesLeafDiff.GranularPatchChange> out) {
    MdPropertiesGranularChanges c = new MdPropertiesGranularChanges(out);
    c.enumText("ObjectBelonging", b.objectBelonging, i.objectBelonging);
    if (!MdFlatDtoSupport.equalsFlat(b.type, i.type, false)) {
      out.add(MdObjectPropertiesLeafDiff.GranularPatchChange.objectProperty(
        "Type", MdTypeDescriptionSerial.typeElement("Type", i.type)));
    }
    c.bool("UseStandardCommands", b.useStandardCommands, i.useStandardCommands);
    c.text("DefaultForm", b.defaultForm, i.defaultForm);
    c.localStringRu("ExtendedPresentation", b.extendedPresentationRu, i.extendedPresentationRu);
    c.localStringRu("Explanation", b.explanationRu, i.explanationRu);
    c.bool("PasswordMode", b.passwordMode, i.passwordMode);
    c.localStringRu("Format", b.formatRu, i.formatRu);
    c.localStringRu("EditFormat", b.editFormatRu, i.editFormatRu);
    c.localStringRu("ToolTip", b.toolTipRu, i.toolTipRu);
    c.bool("MarkNegatives", b.markNegatives, i.markNegatives);
    c.text("Mask", b.mask, i.mask);
    c.bool("MultiLine", b.multiLine, i.multiLine);
    c.bool("ExtendedEdit", b.extendedEdit, i.extendedEdit);
    c.enumText("FillChecking", b.fillChecking, i.fillChecking);
    c.enumText("ChoiceFoldersAndItems", b.choiceFoldersAndItems, i.choiceFoldersAndItems);
    c.enumText("QuickChoice", b.quickChoice, i.quickChoice);
    c.text("ChoiceForm", b.choiceForm, i.choiceForm);
    c.enumText("ChoiceHistoryOnInput", b.choiceHistoryOnInput, i.choiceHistoryOnInput);
    c.text("ValueManagerModule", b.valueManagerModule, i.valueManagerModule);
    c.text("ManagerModule", b.managerModule, i.managerModule);
    c.enumText("DataLockControlMode", b.dataLockControlMode, i.dataLockControlMode);
    c.enumText("DataHistory", b.dataHistory, i.dataHistory);
    c.bool("UpdateDataHistoryImmediatelyAfterWrite",
      b.updateDataHistoryImmediatelyAfterWrite, i.updateDataHistoryImmediatelyAfterWrite);
    c.bool("ExecuteAfterWriteDataHistoryVersionProcessing",
      b.executeAfterWriteDataHistoryVersionProcessing, i.executeAfterWriteDataHistoryVersionProcessing);
  }

  static void appendRegisterScalarChanges(
    MdRegisterPropertiesDto b,
    MdRegisterPropertiesDto i,
    List<MdObjectPropertiesLeafDiff.GranularPatchChange> out) {
    MdPropertiesGranularChanges c = new MdPropertiesGranularChanges(out);
    c.enumText("ObjectBelonging", b.objectBelonging, i.objectBelonging);
    c.bool("UseStandardCommands", b.useStandardCommands, i.useStandardCommands);
    c.enumText("EditType", b.editType, i.editType);
    c.text("DefaultRecordForm", b.defaultRecordForm, i.defaultRecordForm);
    c.text("DefaultListForm", b.defaultListForm, i.defaultListForm);
    c.text("AuxiliaryRecordForm", b.auxiliaryRecordForm, i.auxiliaryRecordForm);
    c.text("AuxiliaryListForm", b.auxiliaryListForm, i.auxiliaryListForm);
    c.xmlBlob("StandardAttributes", b.standardAttributesXml, i.standardAttributesXml);
    c.enumText("InformationRegisterPeriodicity", b.informationRegisterPeriodicity, i.informationRegisterPeriodicity);
    c.enumText("WriteMode", b.writeMode, i.writeMode);
    c.bool("MainFilterOnPeriod", b.mainFilterOnPeriod, i.mainFilterOnPeriod);
    c.enumText("RegisterType", b.registerType, i.registerType);
    c.bool("IncludeHelpInContents", b.includeHelpInContents, i.includeHelpInContents);
    c.text("Help", b.help, i.help);
    c.text("RecordSetModule", b.recordSetModule, i.recordSetModule);
    c.text("ManagerModule", b.managerModule, i.managerModule);
    c.enumText("DataLockControlMode", b.dataLockControlMode, i.dataLockControlMode);
    c.enumText("FullTextSearch", b.fullTextSearch, i.fullTextSearch);
    c.bool("EnableTotalsSliceFirst", b.enableTotalsSliceFirst, i.enableTotalsSliceFirst);
    c.bool("EnableTotalsSliceLast", b.enableTotalsSliceLast, i.enableTotalsSliceLast);
    c.bool("EnableTotalsSplitting", b.enableTotalsSplitting, i.enableTotalsSplitting);
    c.text("Aggregates", b.aggregates, i.aggregates);
    c.localStringRu("RecordPresentation", b.recordPresentationRu, i.recordPresentationRu);
    c.localStringRu("ExtendedRecordPresentation", b.extendedRecordPresentationRu, i.extendedRecordPresentationRu);
    c.localStringRu("ListPresentation", b.listPresentationRu, i.listPresentationRu);
    c.localStringRu("ExtendedListPresentation", b.extendedListPresentationRu, i.extendedListPresentationRu);
    c.localStringRu("Explanation", b.explanationRu, i.explanationRu);
    c.enumText("DataHistory", b.dataHistory, i.dataHistory);
    c.bool("UpdateDataHistoryImmediatelyAfterWrite",
      b.updateDataHistoryImmediatelyAfterWrite, i.updateDataHistoryImmediatelyAfterWrite);
    c.bool("ExecuteAfterWriteDataHistoryVersionProcessing",
      b.executeAfterWriteDataHistoryVersionProcessing, i.executeAfterWriteDataHistoryVersionProcessing);
    c.text("AdditionalIndexes", b.additionalIndexes, i.additionalIndexes);
  }

  static void appendReportScalarChanges(
    MdReportPropertiesDto b,
    MdReportPropertiesDto i,
    List<MdObjectPropertiesLeafDiff.GranularPatchChange> out) {
    MdPropertiesGranularChanges c = new MdPropertiesGranularChanges(out);
    c.enumText("ObjectBelonging", b.objectBelonging, i.objectBelonging);
    c.bool("UseStandardCommands", b.useStandardCommands, i.useStandardCommands);
    c.text("DefaultForm", b.defaultForm, i.defaultForm);
    c.text("AuxiliaryForm", b.auxiliaryForm, i.auxiliaryForm);
    c.text("MainDataCompositionSchema", b.mainDataCompositionSchema, i.mainDataCompositionSchema);
    c.text("DefaultSettingsForm", b.defaultSettingsForm, i.defaultSettingsForm);
    c.text("AuxiliarySettingsForm", b.auxiliarySettingsForm, i.auxiliarySettingsForm);
    c.text("DefaultVariantForm", b.defaultVariantForm, i.defaultVariantForm);
    c.text("AuxiliaryVariantForm", b.auxiliaryVariantForm, i.auxiliaryVariantForm);
    c.text("VariantsStorage", b.variantsStorage, i.variantsStorage);
    c.text("SettingsStorage", b.settingsStorage, i.settingsStorage);
    c.text("ObjectModule", b.objectModule, i.objectModule);
    c.text("ManagerModule", b.managerModule, i.managerModule);
    c.bool("IncludeHelpInContents", b.includeHelpInContents, i.includeHelpInContents);
    c.localStringRu("ExtendedPresentation", b.extendedPresentationRu, i.extendedPresentationRu);
    c.localStringRu("Explanation", b.explanationRu, i.explanationRu);
  }

  static void appendChartOfCalculationTypesScalarChanges(
    MdChartOfCalculationTypesPropertiesDto b,
    MdChartOfCalculationTypesPropertiesDto i,
    List<MdObjectPropertiesLeafDiff.GranularPatchChange> out) {
    MdPropertiesGranularChanges c = new MdPropertiesGranularChanges(out);
    c.enumText("ObjectBelonging", b.objectBelonging, i.objectBelonging);
    c.bool("UseStandardCommands", b.useStandardCommands, i.useStandardCommands);
    c.text("CodeLength", b.codeLength, i.codeLength);
    c.text("DescriptionLength", b.descriptionLength, i.descriptionLength);
    c.enumText("CodeType", b.codeType, i.codeType);
    c.enumText("CodeAllowedLength", b.codeAllowedLength, i.codeAllowedLength);
    c.enumText("EditType", b.editType, i.editType);
    c.fields("InputByString", b.inputByString, i.inputByString);
    c.enumText("CreateOnInput", b.createOnInput, i.createOnInput);
    c.enumText("SearchStringModeOnInputByString",
      b.searchStringModeOnInputByString, i.searchStringModeOnInputByString);
    c.enumText("ChoiceDataGetModeOnInputByString",
      b.choiceDataGetModeOnInputByString, i.choiceDataGetModeOnInputByString);
    c.enumText("FullTextSearchOnInputByString",
      b.fullTextSearchOnInputByString, i.fullTextSearchOnInputByString);
    c.enumText("ChoiceHistoryOnInput", b.choiceHistoryOnInput, i.choiceHistoryOnInput);
    c.text("DefaultObjectForm", b.defaultObjectForm, i.defaultObjectForm);
    c.text("DefaultListForm", b.defaultListForm, i.defaultListForm);
    c.text("DefaultChoiceForm", b.defaultChoiceForm, i.defaultChoiceForm);
    c.text("AuxiliaryObjectForm", b.auxiliaryObjectForm, i.auxiliaryObjectForm);
    c.text("AuxiliaryListForm", b.auxiliaryListForm, i.auxiliaryListForm);
    c.text("AuxiliaryChoiceForm", b.auxiliaryChoiceForm, i.auxiliaryChoiceForm);
    c.text("ObjectModule", b.objectModule, i.objectModule);
    c.text("ManagerModule", b.managerModule, i.managerModule);
    c.refs("BasedOn", b.basedOn, i.basedOn);
    c.enumText("DependenceOnCalculationTypes", b.dependenceOnCalculationTypes, i.dependenceOnCalculationTypes);
    c.refs("BaseCalculationTypes", b.baseCalculationTypes, i.baseCalculationTypes);
    c.bool("ActionPeriodUse", b.actionPeriodUse, i.actionPeriodUse);
    c.xmlBlob("StandardAttributes", b.standardAttributesXml, i.standardAttributesXml);
    c.xmlBlob("Characteristics", b.characteristicsXml, i.characteristicsXml);
    c.enumText("PredefinedDataUpdate", b.predefinedDataUpdate, i.predefinedDataUpdate);
    c.bool("IncludeHelpInContents", b.includeHelpInContents, i.includeHelpInContents);
    c.fields("DataLockFields", b.dataLockFields, i.dataLockFields);
    c.enumText("DataLockControlMode", b.dataLockControlMode, i.dataLockControlMode);
    c.enumText("FullTextSearch", b.fullTextSearch, i.fullTextSearch);
    c.localStringRu("ObjectPresentation", b.objectPresentationRu, i.objectPresentationRu);
    c.localStringRu("ExtendedObjectPresentation", b.extendedObjectPresentationRu, i.extendedObjectPresentationRu);
    c.localStringRu("ListPresentation", b.listPresentationRu, i.listPresentationRu);
    c.localStringRu("ExtendedListPresentation", b.extendedListPresentationRu, i.extendedListPresentationRu);
    c.localStringRu("Explanation", b.explanationRu, i.explanationRu);
    c.enumText("DataHistory", b.dataHistory, i.dataHistory);
    c.bool("UpdateDataHistoryImmediatelyAfterWrite",
      b.updateDataHistoryImmediatelyAfterWrite, i.updateDataHistoryImmediatelyAfterWrite);
    c.bool("ExecuteAfterWriteDataHistoryVersionProcessing",
      b.executeAfterWriteDataHistoryVersionProcessing, i.executeAfterWriteDataHistoryVersionProcessing);
    c.text("AdditionalIndexes", b.additionalIndexes, i.additionalIndexes);
  }

  static void appendChartOfAccountsScalarChanges(
    MdChartOfAccountsPropertiesDto b,
    MdChartOfAccountsPropertiesDto i,
    List<MdObjectPropertiesLeafDiff.GranularPatchChange> out) {
    MdPropertiesGranularChanges c = new MdPropertiesGranularChanges(out);
    c.enumText("ObjectBelonging", b.objectBelonging, i.objectBelonging);
    c.bool("UseStandardCommands", b.useStandardCommands, i.useStandardCommands);
    c.bool("IncludeHelpInContents", b.includeHelpInContents, i.includeHelpInContents);
    c.refs("BasedOn", b.basedOn, i.basedOn);
    c.text("ExtDimensionTypes", b.extDimensionTypes, i.extDimensionTypes);
    c.text("MaxExtDimensionCount", b.maxExtDimensionCount, i.maxExtDimensionCount);
    c.text("CodeMask", b.codeMask, i.codeMask);
    c.text("CodeLength", b.codeLength, i.codeLength);
    c.text("DescriptionLength", b.descriptionLength, i.descriptionLength);
    c.enumText("CodeSeries", b.codeSeries, i.codeSeries);
    c.bool("CheckUnique", b.checkUnique, i.checkUnique);
    c.enumText("DefaultPresentation", b.defaultPresentation, i.defaultPresentation);
    c.xmlBlob("StandardAttributes", b.standardAttributesXml, i.standardAttributesXml);
    c.xmlBlob("Characteristics", b.characteristicsXml, i.characteristicsXml);
    c.enumText("PredefinedDataUpdate", b.predefinedDataUpdate, i.predefinedDataUpdate);
    c.enumText("EditType", b.editType, i.editType);
    c.bool("QuickChoice", b.quickChoice, i.quickChoice);
    c.enumText("ChoiceMode", b.choiceMode, i.choiceMode);
    c.fields("InputByString", b.inputByString, i.inputByString);
    c.enumText("SearchStringModeOnInputByString",
      b.searchStringModeOnInputByString, i.searchStringModeOnInputByString);
    c.enumText("FullTextSearchOnInputByString",
      b.fullTextSearchOnInputByString, i.fullTextSearchOnInputByString);
    c.enumText("ChoiceDataGetModeOnInputByString",
      b.choiceDataGetModeOnInputByString, i.choiceDataGetModeOnInputByString);
    c.enumText("CreateOnInput", b.createOnInput, i.createOnInput);
    c.enumText("ChoiceHistoryOnInput", b.choiceHistoryOnInput, i.choiceHistoryOnInput);
    c.text("DefaultObjectForm", b.defaultObjectForm, i.defaultObjectForm);
    c.text("DefaultListForm", b.defaultListForm, i.defaultListForm);
    c.text("DefaultChoiceForm", b.defaultChoiceForm, i.defaultChoiceForm);
    c.text("AuxiliaryObjectForm", b.auxiliaryObjectForm, i.auxiliaryObjectForm);
    c.text("AuxiliaryListForm", b.auxiliaryListForm, i.auxiliaryListForm);
    c.text("AuxiliaryChoiceForm", b.auxiliaryChoiceForm, i.auxiliaryChoiceForm);
    c.text("ObjectModule", b.objectModule, i.objectModule);
    c.text("ManagerModule", b.managerModule, i.managerModule);
    c.fields("DataLockFields", b.dataLockFields, i.dataLockFields);
    c.enumText("DataLockControlMode", b.dataLockControlMode, i.dataLockControlMode);
    c.enumText("FullTextSearch", b.fullTextSearch, i.fullTextSearch);
    c.localStringRu("ObjectPresentation", b.objectPresentationRu, i.objectPresentationRu);
    c.localStringRu("ExtendedObjectPresentation", b.extendedObjectPresentationRu, i.extendedObjectPresentationRu);
    c.localStringRu("ListPresentation", b.listPresentationRu, i.listPresentationRu);
    c.localStringRu("ExtendedListPresentation", b.extendedListPresentationRu, i.extendedListPresentationRu);
    c.localStringRu("Explanation", b.explanationRu, i.explanationRu);
    c.enumText("DataHistory", b.dataHistory, i.dataHistory);
    c.bool("UpdateDataHistoryImmediatelyAfterWrite",
      b.updateDataHistoryImmediatelyAfterWrite, i.updateDataHistoryImmediatelyAfterWrite);
    c.bool("ExecuteAfterWriteDataHistoryVersionProcessing",
      b.executeAfterWriteDataHistoryVersionProcessing, i.executeAfterWriteDataHistoryVersionProcessing);
    c.text("AdditionalIndexes", b.additionalIndexes, i.additionalIndexes);
  }

  static void appendBusinessProcessScalarChanges(
    MdBusinessProcessPropertiesDto b,
    MdBusinessProcessPropertiesDto i,
    List<MdObjectPropertiesLeafDiff.GranularPatchChange> out) {
    MdPropertiesGranularChanges c = new MdPropertiesGranularChanges(out);
    c.enumText("ObjectBelonging", b.objectBelonging, i.objectBelonging);
    c.bool("UseStandardCommands", b.useStandardCommands, i.useStandardCommands);
    c.text("ObjectModule", b.objectModule, i.objectModule);
    c.text("ManagerModule", b.managerModule, i.managerModule);
    c.text("Flowchart", b.flowchart, i.flowchart);
    c.enumText("EditType", b.editType, i.editType);
    c.fields("InputByString", b.inputByString, i.inputByString);
    c.enumText("CreateOnInput", b.createOnInput, i.createOnInput);
    c.enumText("SearchStringModeOnInputByString",
      b.searchStringModeOnInputByString, i.searchStringModeOnInputByString);
    c.enumText("ChoiceDataGetModeOnInputByString",
      b.choiceDataGetModeOnInputByString, i.choiceDataGetModeOnInputByString);
    c.enumText("FullTextSearchOnInputByString",
      b.fullTextSearchOnInputByString, i.fullTextSearchOnInputByString);
    c.text("DefaultObjectForm", b.defaultObjectForm, i.defaultObjectForm);
    c.text("DefaultListForm", b.defaultListForm, i.defaultListForm);
    c.text("DefaultChoiceForm", b.defaultChoiceForm, i.defaultChoiceForm);
    c.text("AuxiliaryObjectForm", b.auxiliaryObjectForm, i.auxiliaryObjectForm);
    c.text("AuxiliaryListForm", b.auxiliaryListForm, i.auxiliaryListForm);
    c.text("AuxiliaryChoiceForm", b.auxiliaryChoiceForm, i.auxiliaryChoiceForm);
    c.enumText("ChoiceHistoryOnInput", b.choiceHistoryOnInput, i.choiceHistoryOnInput);
    c.enumText("NumberType", b.numberType, i.numberType);
    c.text("NumberLength", b.numberLength, i.numberLength);
    c.enumText("NumberAllowedLength", b.numberAllowedLength, i.numberAllowedLength);
    c.bool("CheckUnique", b.checkUnique, i.checkUnique);
    c.xmlBlob("StandardAttributes", b.standardAttributesXml, i.standardAttributesXml);
    c.xmlBlob("Characteristics", b.characteristicsXml, i.characteristicsXml);
    c.bool("Autonumbering", b.autonumbering, i.autonumbering);
    c.refs("BasedOn", b.basedOn, i.basedOn);
    c.enumText("NumberPeriodicity", b.numberPeriodicity, i.numberPeriodicity);
    c.text("Task", b.task, i.task);
    c.bool("CreateTaskInPrivilegedMode", b.createTaskInPrivilegedMode, i.createTaskInPrivilegedMode);
    c.fields("DataLockFields", b.dataLockFields, i.dataLockFields);
    c.enumText("DataLockControlMode", b.dataLockControlMode, i.dataLockControlMode);
    c.bool("IncludeHelpInContents", b.includeHelpInContents, i.includeHelpInContents);
    c.enumText("FullTextSearch", b.fullTextSearch, i.fullTextSearch);
    c.localStringRu("ObjectPresentation", b.objectPresentationRu, i.objectPresentationRu);
    c.localStringRu("ExtendedObjectPresentation", b.extendedObjectPresentationRu, i.extendedObjectPresentationRu);
    c.localStringRu("ListPresentation", b.listPresentationRu, i.listPresentationRu);
    c.localStringRu("ExtendedListPresentation", b.extendedListPresentationRu, i.extendedListPresentationRu);
    c.localStringRu("Explanation", b.explanationRu, i.explanationRu);
    c.enumText("DataHistory", b.dataHistory, i.dataHistory);
    c.bool("UpdateDataHistoryImmediatelyAfterWrite",
      b.updateDataHistoryImmediatelyAfterWrite, i.updateDataHistoryImmediatelyAfterWrite);
    c.bool("ExecuteAfterWriteDataHistoryVersionProcessing",
      b.executeAfterWriteDataHistoryVersionProcessing, i.executeAfterWriteDataHistoryVersionProcessing);
    c.text("AdditionalIndexes", b.additionalIndexes, i.additionalIndexes);
  }

  static void appendTaskScalarChanges(
    MdTaskPropertiesDto b,
    MdTaskPropertiesDto i,
    List<MdObjectPropertiesLeafDiff.GranularPatchChange> out) {
    MdPropertiesGranularChanges c = new MdPropertiesGranularChanges(out);
    c.enumText("ObjectBelonging", b.objectBelonging, i.objectBelonging);
    c.bool("UseStandardCommands", b.useStandardCommands, i.useStandardCommands);
    c.text("ObjectModule", b.objectModule, i.objectModule);
    c.text("ManagerModule", b.managerModule, i.managerModule);
    c.enumText("NumberType", b.numberType, i.numberType);
    c.text("NumberLength", b.numberLength, i.numberLength);
    c.enumText("NumberAllowedLength", b.numberAllowedLength, i.numberAllowedLength);
    c.bool("CheckUnique", b.checkUnique, i.checkUnique);
    c.bool("Autonumbering", b.autonumbering, i.autonumbering);
    c.enumText("TaskNumberAutoPrefix", b.taskNumberAutoPrefix, i.taskNumberAutoPrefix);
    c.text("DescriptionLength", b.descriptionLength, i.descriptionLength);
    c.text("Addressing", b.addressing, i.addressing);
    c.text("MainAddressingAttribute", b.mainAddressingAttribute, i.mainAddressingAttribute);
    c.text("CurrentPerformer", b.currentPerformer, i.currentPerformer);
    c.refs("BasedOn", b.basedOn, i.basedOn);
    c.xmlBlob("StandardAttributes", b.standardAttributesXml, i.standardAttributesXml);
    c.xmlBlob("Characteristics", b.characteristicsXml, i.characteristicsXml);
    c.enumText("DefaultPresentation", b.defaultPresentation, i.defaultPresentation);
    c.enumText("EditType", b.editType, i.editType);
    c.fields("InputByString", b.inputByString, i.inputByString);
    c.enumText("SearchStringModeOnInputByString",
      b.searchStringModeOnInputByString, i.searchStringModeOnInputByString);
    c.enumText("FullTextSearchOnInputByString",
      b.fullTextSearchOnInputByString, i.fullTextSearchOnInputByString);
    c.enumText("ChoiceDataGetModeOnInputByString",
      b.choiceDataGetModeOnInputByString, i.choiceDataGetModeOnInputByString);
    c.enumText("CreateOnInput", b.createOnInput, i.createOnInput);
    c.text("DefaultObjectForm", b.defaultObjectForm, i.defaultObjectForm);
    c.text("DefaultListForm", b.defaultListForm, i.defaultListForm);
    c.text("DefaultChoiceForm", b.defaultChoiceForm, i.defaultChoiceForm);
    c.text("AuxiliaryObjectForm", b.auxiliaryObjectForm, i.auxiliaryObjectForm);
    c.text("AuxiliaryListForm", b.auxiliaryListForm, i.auxiliaryListForm);
    c.text("AuxiliaryChoiceForm", b.auxiliaryChoiceForm, i.auxiliaryChoiceForm);
    c.enumText("ChoiceHistoryOnInput", b.choiceHistoryOnInput, i.choiceHistoryOnInput);
    c.bool("IncludeHelpInContents", b.includeHelpInContents, i.includeHelpInContents);
    c.fields("DataLockFields", b.dataLockFields, i.dataLockFields);
    c.enumText("DataLockControlMode", b.dataLockControlMode, i.dataLockControlMode);
    c.enumText("FullTextSearch", b.fullTextSearch, i.fullTextSearch);
    c.localStringRu("ObjectPresentation", b.objectPresentationRu, i.objectPresentationRu);
    c.localStringRu("ExtendedObjectPresentation", b.extendedObjectPresentationRu, i.extendedObjectPresentationRu);
    c.localStringRu("ListPresentation", b.listPresentationRu, i.listPresentationRu);
    c.localStringRu("ExtendedListPresentation", b.extendedListPresentationRu, i.extendedListPresentationRu);
    c.localStringRu("Explanation", b.explanationRu, i.explanationRu);
    c.enumText("DataHistory", b.dataHistory, i.dataHistory);
    c.bool("UpdateDataHistoryImmediatelyAfterWrite",
      b.updateDataHistoryImmediatelyAfterWrite, i.updateDataHistoryImmediatelyAfterWrite);
    c.bool("ExecuteAfterWriteDataHistoryVersionProcessing",
      b.executeAfterWriteDataHistoryVersionProcessing, i.executeAfterWriteDataHistoryVersionProcessing);
    c.text("AdditionalIndexes", b.additionalIndexes, i.additionalIndexes);
  }

  static void appendChartOfCharacteristicTypesScalarChanges(
    MdChartOfCharacteristicTypesPropertiesDto b,
    MdChartOfCharacteristicTypesPropertiesDto i,
    List<MdObjectPropertiesLeafDiff.GranularPatchChange> out) {
    MdPropertiesGranularChanges c = new MdPropertiesGranularChanges(out);
    c.enumText("ObjectBelonging", b.objectBelonging, i.objectBelonging);
    c.bool("UseStandardCommands", b.useStandardCommands, i.useStandardCommands);
    c.bool("IncludeHelpInContents", b.includeHelpInContents, i.includeHelpInContents);
    c.text("CharacteristicExtValues", b.characteristicExtValues, i.characteristicExtValues);
    if (!MdFlatDtoSupport.equalsFlat(b.type, i.type, false)) {
      out.add(MdObjectPropertiesLeafDiff.GranularPatchChange.objectProperty(
        "Type", MdTypeDescriptionSerial.typeElement("Type", i.type)));
    }
    c.bool("Hierarchical", b.hierarchical, i.hierarchical);
    c.bool("FoldersOnTop", b.foldersOnTop, i.foldersOnTop);
    c.text("CodeLength", b.codeLength, i.codeLength);
    c.enumText("CodeAllowedLength", b.codeAllowedLength, i.codeAllowedLength);
    c.text("DescriptionLength", b.descriptionLength, i.descriptionLength);
    c.enumText("CodeSeries", b.codeSeries, i.codeSeries);
    c.bool("CheckUnique", b.checkUnique, i.checkUnique);
    c.bool("Autonumbering", b.autonumbering, i.autonumbering);
    c.enumText("DefaultPresentation", b.defaultPresentation, i.defaultPresentation);
    c.xmlBlob("StandardAttributes", b.standardAttributesXml, i.standardAttributesXml);
    c.xmlBlob("Characteristics", b.characteristicsXml, i.characteristicsXml);
    c.enumText("PredefinedDataUpdate", b.predefinedDataUpdate, i.predefinedDataUpdate);
    c.enumText("EditType", b.editType, i.editType);
    c.bool("QuickChoice", b.quickChoice, i.quickChoice);
    c.enumText("ChoiceMode", b.choiceMode, i.choiceMode);
    c.fields("InputByString", b.inputByString, i.inputByString);
    c.enumText("CreateOnInput", b.createOnInput, i.createOnInput);
    c.enumText("SearchStringModeOnInputByString",
      b.searchStringModeOnInputByString, i.searchStringModeOnInputByString);
    c.enumText("ChoiceDataGetModeOnInputByString",
      b.choiceDataGetModeOnInputByString, i.choiceDataGetModeOnInputByString);
    c.enumText("FullTextSearchOnInputByString",
      b.fullTextSearchOnInputByString, i.fullTextSearchOnInputByString);
    c.enumText("ChoiceHistoryOnInput", b.choiceHistoryOnInput, i.choiceHistoryOnInput);
    c.text("DefaultObjectForm", b.defaultObjectForm, i.defaultObjectForm);
    c.text("DefaultFolderForm", b.defaultFolderForm, i.defaultFolderForm);
    c.text("DefaultListForm", b.defaultListForm, i.defaultListForm);
    c.text("DefaultChoiceForm", b.defaultChoiceForm, i.defaultChoiceForm);
    c.text("DefaultFolderChoiceForm", b.defaultFolderChoiceForm, i.defaultFolderChoiceForm);
    c.text("AuxiliaryObjectForm", b.auxiliaryObjectForm, i.auxiliaryObjectForm);
    c.text("AuxiliaryFolderForm", b.auxiliaryFolderForm, i.auxiliaryFolderForm);
    c.text("AuxiliaryListForm", b.auxiliaryListForm, i.auxiliaryListForm);
    c.text("AuxiliaryChoiceForm", b.auxiliaryChoiceForm, i.auxiliaryChoiceForm);
    c.text("AuxiliaryFolderChoiceForm", b.auxiliaryFolderChoiceForm, i.auxiliaryFolderChoiceForm);
    c.text("ObjectModule", b.objectModule, i.objectModule);
    c.text("ManagerModule", b.managerModule, i.managerModule);
    c.refs("BasedOn", b.basedOn, i.basedOn);
    c.fields("DataLockFields", b.dataLockFields, i.dataLockFields);
    c.enumText("DataLockControlMode", b.dataLockControlMode, i.dataLockControlMode);
    c.enumText("FullTextSearch", b.fullTextSearch, i.fullTextSearch);
    c.localStringRu("ObjectPresentation", b.objectPresentationRu, i.objectPresentationRu);
    c.localStringRu("ExtendedObjectPresentation", b.extendedObjectPresentationRu, i.extendedObjectPresentationRu);
    c.localStringRu("ListPresentation", b.listPresentationRu, i.listPresentationRu);
    c.localStringRu("ExtendedListPresentation", b.extendedListPresentationRu, i.extendedListPresentationRu);
    c.localStringRu("Explanation", b.explanationRu, i.explanationRu);
    c.enumText("DataHistory", b.dataHistory, i.dataHistory);
    c.bool("UpdateDataHistoryImmediatelyAfterWrite",
      b.updateDataHistoryImmediatelyAfterWrite, i.updateDataHistoryImmediatelyAfterWrite);
    c.bool("ExecuteAfterWriteDataHistoryVersionProcessing",
      b.executeAfterWriteDataHistoryVersionProcessing, i.executeAfterWriteDataHistoryVersionProcessing);
    c.text("AdditionalIndexes", b.additionalIndexes, i.additionalIndexes);
  }

  static void appendExchangePlanScalarChanges(
    MdExchangePlanPropertiesDto b,
    MdExchangePlanPropertiesDto i,
    List<MdObjectPropertiesLeafDiff.GranularPatchChange> out) {
    MdPropertiesGranularChanges c = new MdPropertiesGranularChanges(out);
    c.enumText("ObjectBelonging", b.objectBelonging, i.objectBelonging);
    c.bool("UseStandardCommands", b.useStandardCommands, i.useStandardCommands);
    c.text("CodeLength", b.codeLength, i.codeLength);
    c.enumText("CodeAllowedLength", b.codeAllowedLength, i.codeAllowedLength);
    c.text("DescriptionLength", b.descriptionLength, i.descriptionLength);
    c.enumText("DefaultPresentation", b.defaultPresentation, i.defaultPresentation);
    c.enumText("EditType", b.editType, i.editType);
    c.bool("QuickChoice", b.quickChoice, i.quickChoice);
    c.enumText("ChoiceMode", b.choiceMode, i.choiceMode);
    c.fields("InputByString", b.inputByString, i.inputByString);
    c.enumText("SearchStringModeOnInputByString",
      b.searchStringModeOnInputByString, i.searchStringModeOnInputByString);
    c.enumText("FullTextSearchOnInputByString",
      b.fullTextSearchOnInputByString, i.fullTextSearchOnInputByString);
    c.enumText("ChoiceDataGetModeOnInputByString",
      b.choiceDataGetModeOnInputByString, i.choiceDataGetModeOnInputByString);
    c.enumText("ChoiceHistoryOnInput", b.choiceHistoryOnInput, i.choiceHistoryOnInput);
    c.enumText("CreateOnInput", b.createOnInput, i.createOnInput);
    c.text("DefaultObjectForm", b.defaultObjectForm, i.defaultObjectForm);
    c.text("DefaultListForm", b.defaultListForm, i.defaultListForm);
    c.text("DefaultChoiceForm", b.defaultChoiceForm, i.defaultChoiceForm);
    c.text("AuxiliaryObjectForm", b.auxiliaryObjectForm, i.auxiliaryObjectForm);
    c.text("AuxiliaryListForm", b.auxiliaryListForm, i.auxiliaryListForm);
    c.text("AuxiliaryChoiceForm", b.auxiliaryChoiceForm, i.auxiliaryChoiceForm);
    c.text("ObjectModule", b.objectModule, i.objectModule);
    c.text("ManagerModule", b.managerModule, i.managerModule);
    c.xmlBlob("StandardAttributes", b.standardAttributesXml, i.standardAttributesXml);
    c.xmlBlob("Characteristics", b.characteristicsXml, i.characteristicsXml);
    c.refs("BasedOn", b.basedOn, i.basedOn);
    c.bool("DistributedInfoBase", b.distributedInfoBase, i.distributedInfoBase);
    c.bool("IncludeConfigurationExtensions", b.includeConfigurationExtensions, i.includeConfigurationExtensions);
    c.bool("IncludeHelpInContents", b.includeHelpInContents, i.includeHelpInContents);
    c.fields("DataLockFields", b.dataLockFields, i.dataLockFields);
    c.enumText("DataLockControlMode", b.dataLockControlMode, i.dataLockControlMode);
    c.enumText("FullTextSearch", b.fullTextSearch, i.fullTextSearch);
    c.localStringRu("ObjectPresentation", b.objectPresentationRu, i.objectPresentationRu);
    c.localStringRu("ExtendedObjectPresentation", b.extendedObjectPresentationRu, i.extendedObjectPresentationRu);
    c.localStringRu("ListPresentation", b.listPresentationRu, i.listPresentationRu);
    c.localStringRu("ExtendedListPresentation", b.extendedListPresentationRu, i.extendedListPresentationRu);
    c.localStringRu("Explanation", b.explanationRu, i.explanationRu);
    c.enumText("DataHistory", b.dataHistory, i.dataHistory);
    c.bool("UpdateDataHistoryImmediatelyAfterWrite",
      b.updateDataHistoryImmediatelyAfterWrite, i.updateDataHistoryImmediatelyAfterWrite);
    c.bool("ExecuteAfterWriteDataHistoryVersionProcessing",
      b.executeAfterWriteDataHistoryVersionProcessing, i.executeAfterWriteDataHistoryVersionProcessing);
    c.text("AdditionalIndexes", b.additionalIndexes, i.additionalIndexes);
  }

  static void appendDocumentJournalScalarChanges(
    MdDocumentJournalPropertiesDto b,
    MdDocumentJournalPropertiesDto i,
    List<MdObjectPropertiesLeafDiff.GranularPatchChange> out) {
    MdPropertiesGranularChanges c = new MdPropertiesGranularChanges(out);
    c.enumText("ObjectBelonging", b.objectBelonging, i.objectBelonging);
    c.bool("UseStandardCommands", b.useStandardCommands, i.useStandardCommands);
    c.text("DefaultForm", b.defaultForm, i.defaultForm);
    c.text("AuxiliaryForm", b.auxiliaryForm, i.auxiliaryForm);
    c.refs("RegisteredDocuments", b.registeredDocuments, i.registeredDocuments);
    c.text("ManagerModule", b.managerModule, i.managerModule);
    c.bool("IncludeHelpInContents", b.includeHelpInContents, i.includeHelpInContents);
    c.xmlBlob("StandardAttributes", b.standardAttributesXml, i.standardAttributesXml);
    c.localStringRu("ListPresentation", b.listPresentationRu, i.listPresentationRu);
    c.localStringRu("ExtendedListPresentation", b.extendedListPresentationRu, i.extendedListPresentationRu);
    c.localStringRu("Explanation", b.explanationRu, i.explanationRu);
    c.text("AdditionalIndexes", b.additionalIndexes, i.additionalIndexes);
  }

  static void appendCommonModuleScalarChanges(
    MdCommonModulePropertiesDto b,
    MdCommonModulePropertiesDto i,
    List<MdObjectPropertiesLeafDiff.GranularPatchChange> out) {
    MdPropertiesGranularChanges c = new MdPropertiesGranularChanges(out);
    c.enumText("ObjectBelonging", b.objectBelonging, i.objectBelonging);
    c.bool("Global", b.global, i.global);
    c.bool("ClientManagedApplication", b.clientManagedApplication, i.clientManagedApplication);
    c.bool("Server", b.server, i.server);
    c.bool("ExternalConnection", b.externalConnection, i.externalConnection);
    c.bool("ClientOrdinaryApplication", b.clientOrdinaryApplication, i.clientOrdinaryApplication);
    c.bool("Client", b.client, i.client);
    c.bool("ServerCall", b.serverCall, i.serverCall);
    c.bool("Privileged", b.privileged, i.privileged);
    c.enumText("ReturnValuesReuse", b.returnValuesReuse, i.returnValuesReuse);
  }

  static void appendSessionParameterScalarChanges(
    MdSessionParameterPropertiesDto b,
    MdSessionParameterPropertiesDto i,
    List<MdObjectPropertiesLeafDiff.GranularPatchChange> out) {
    MdPropertiesGranularChanges c = new MdPropertiesGranularChanges(out);
    c.enumText("ObjectBelonging", b.objectBelonging, i.objectBelonging);
  }

  static void appendDocumentNumeratorScalarChanges(
    MdDocumentNumeratorPropertiesDto b,
    MdDocumentNumeratorPropertiesDto i,
    List<MdObjectPropertiesLeafDiff.GranularPatchChange> out) {
    MdPropertiesGranularChanges c = new MdPropertiesGranularChanges(out);
    c.enumText("ObjectBelonging", b.objectBelonging, i.objectBelonging);
    c.enumText("NumberType", b.numberType, i.numberType);
    c.text("NumberLength", b.numberLength, i.numberLength);
    c.enumText("NumberAllowedLength", b.numberAllowedLength, i.numberAllowedLength);
    c.enumText("NumberPeriodicity", b.numberPeriodicity, i.numberPeriodicity);
    c.bool("CheckUnique", b.checkUnique, i.checkUnique);
  }

  static void appendEventSubscriptionScalarChanges(
    MdEventSubscriptionPropertiesDto b,
    MdEventSubscriptionPropertiesDto i,
    List<MdObjectPropertiesLeafDiff.GranularPatchChange> out) {
    MdPropertiesGranularChanges c = new MdPropertiesGranularChanges(out);
    c.enumText("ObjectBelonging", b.objectBelonging, i.objectBelonging);
    c.text("Event", b.event, i.event);
    c.text("Handler", b.handler, i.handler);
  }

  static void appendScheduledJobScalarChanges(
    MdScheduledJobPropertiesDto b,
    MdScheduledJobPropertiesDto i,
    List<MdObjectPropertiesLeafDiff.GranularPatchChange> out) {
    MdPropertiesGranularChanges c = new MdPropertiesGranularChanges(out);
    c.enumText("ObjectBelonging", b.objectBelonging, i.objectBelonging);
    c.text("MethodName", b.methodName, i.methodName);
    c.text("Description", b.description, i.description);
    c.text("Key", b.key, i.key);
    c.text("Schedule", b.schedule, i.schedule);
    c.bool("Use", b.use, i.use);
    c.bool("Predefined", b.predefined, i.predefined);
    c.text("RestartCountOnFailure", b.restartCountOnFailure, i.restartCountOnFailure);
    c.text("RestartIntervalOnFailure", b.restartIntervalOnFailure, i.restartIntervalOnFailure);
  }

  static void appendCommonCommandScalarChanges(
    MdCommonCommandPropertiesDto b,
    MdCommonCommandPropertiesDto i,
    List<MdObjectPropertiesLeafDiff.GranularPatchChange> out) {
    MdPropertiesGranularChanges c = new MdPropertiesGranularChanges(out);
    c.enumText("ObjectBelonging", b.objectBelonging, i.objectBelonging);
    c.text("Group", b.group, i.group);
    c.enumText("Representation", b.representation, i.representation);
    c.localStringRu("ToolTip", b.toolTipRu, i.toolTipRu);
    c.text("Shortcut", b.shortcut, i.shortcut);
    c.text("CommandModule", b.commandModule, i.commandModule);
    c.bool("IncludeHelpInContents", b.includeHelpInContents, i.includeHelpInContents);
    c.enumText("ParameterUseMode", b.parameterUseMode, i.parameterUseMode);
    c.bool("ModifiesData", b.modifiesData, i.modifiesData);
    c.enumText("OnMainServerUnavalableBehavior", b.onMainServerUnavalableBehavior, i.onMainServerUnavalableBehavior);
  }

  static void appendCommonAttributeScalarChanges(
    MdCommonAttributePropertiesDto b,
    MdCommonAttributePropertiesDto i,
    List<MdObjectPropertiesLeafDiff.GranularPatchChange> out) {
    MdPropertiesGranularChanges c = new MdPropertiesGranularChanges(out);
    c.enumText("ObjectBelonging", b.objectBelonging, i.objectBelonging);
    c.enumText("AutoUse", b.autoUse, i.autoUse);
    c.enumText("DataSeparation", b.dataSeparation, i.dataSeparation);
    c.enumText("SeparatedDataUse", b.separatedDataUse, i.separatedDataUse);
    c.text("DataSeparationValue", b.dataSeparationValue, i.dataSeparationValue);
    c.text("DataSeparationUse", b.dataSeparationUse, i.dataSeparationUse);
    c.text("ConditionalSeparation", b.conditionalSeparation, i.conditionalSeparation);
    c.enumText("UsersSeparation", b.usersSeparation, i.usersSeparation);
    c.enumText("AuthenticationSeparation", b.authenticationSeparation, i.authenticationSeparation);
    c.enumText("ConfigurationExtensionsSeparation",
      b.configurationExtensionsSeparation, i.configurationExtensionsSeparation);
    c.enumText("Indexing", b.indexing, i.indexing);
    c.enumText("FullTextSearch", b.fullTextSearch, i.fullTextSearch);
    c.enumText("DataHistory", b.dataHistory, i.dataHistory);
    c.localStringRu("ToolTip", b.toolTipRu, i.toolTipRu);
    c.bool("PasswordMode", b.passwordMode, i.passwordMode);
    c.bool("MultiLine", b.multiLine, i.multiLine);
    c.text("Mask", b.mask, i.mask);
    c.enumText("QuickChoice", b.quickChoice, i.quickChoice);
    c.enumText("CreateOnInput", b.createOnInput, i.createOnInput);
    c.enumText("ChoiceHistoryOnInput", b.choiceHistoryOnInput, i.choiceHistoryOnInput);
    c.enumText("FillChecking", b.fillChecking, i.fillChecking);
    c.text("ChoiceForm", b.choiceForm, i.choiceForm);
  }

  static void appendCommonPictureScalarChanges(
    MdCommonPicturePropertiesDto b,
    MdCommonPicturePropertiesDto i,
    List<MdObjectPropertiesLeafDiff.GranularPatchChange> out) {
    MdPropertiesGranularChanges c = new MdPropertiesGranularChanges(out);
    c.enumText("ObjectBelonging", b.objectBelonging, i.objectBelonging);
    c.bool("AvailabilityForChoice", b.availabilityForChoice, i.availabilityForChoice);
    c.bool("AvailabilityForAppearance", b.availabilityForAppearance, i.availabilityForAppearance);
  }

  static void appendRoleScalarChanges(
    MdRolePropertiesDto b,
    MdRolePropertiesDto i,
    List<MdObjectPropertiesLeafDiff.GranularPatchChange> out) {
    MdPropertiesGranularChanges c = new MdPropertiesGranularChanges(out);
    c.enumText("ObjectBelonging", b.objectBelonging, i.objectBelonging);
  }

  static void appendExternalDataSourceScalarChanges(
    MdExternalDataSourcePropertiesDto b,
    MdExternalDataSourcePropertiesDto i,
    List<MdObjectPropertiesLeafDiff.GranularPatchChange> out) {
    MdPropertiesGranularChanges c = new MdPropertiesGranularChanges(out);
    c.enumText("ObjectBelonging", b.objectBelonging, i.objectBelonging);
    c.enumText("DataLockControlMode", b.dataLockControlMode, i.dataLockControlMode);
  }
}
