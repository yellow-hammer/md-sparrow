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
import java.util.Objects;

import static io.github.yellowhammer.designerxml.cf.MdCatalogPropertiesGranularSerial.basedOnElement;
import static io.github.yellowhammer.designerxml.cf.MdCatalogPropertiesGranularSerial.boolElement;
import static io.github.yellowhammer.designerxml.cf.MdCatalogPropertiesGranularSerial.dataLockFieldsElement;
import static io.github.yellowhammer.designerxml.cf.MdCatalogPropertiesGranularSerial.enumTextElement;
import static io.github.yellowhammer.designerxml.cf.MdCatalogPropertiesGranularSerial.inputByStringElement;
import static io.github.yellowhammer.designerxml.cf.MdCatalogPropertiesGranularSerial.localStringRuElement;
import static io.github.yellowhammer.designerxml.cf.MdCatalogPropertiesGranularSerial.mdListTypeRefsElement;
import static io.github.yellowhammer.designerxml.cf.MdCatalogPropertiesGranularSerial.nz;
import static io.github.yellowhammer.designerxml.cf.MdCatalogPropertiesGranularSerial.textElement;

/**
 * Сериализация отдельных прямых дочерних элементов {@code DocumentProperties} для гранулярной замены.
 */
public final class MdDocumentPropertiesGranularSerial {

  private MdDocumentPropertiesGranularSerial() {
  }

  static void appendDocumentScalarChanges(
    MdDocumentPropertiesDto b,
    MdDocumentPropertiesDto i,
    List<MdObjectPropertiesLeafDiff.GranularPatchChange> out) {
    if (!Objects.equals(b.objectBelonging, i.objectBelonging)) {
      out.add(MdObjectPropertiesLeafDiff.GranularPatchChange.objectProperty(
        "ObjectBelonging", enumTextElement("ObjectBelonging", nz(i.objectBelonging))));
    }
    if (b.useStandardCommands != i.useStandardCommands) {
      out.add(MdObjectPropertiesLeafDiff.GranularPatchChange.objectProperty(
        "UseStandardCommands", boolElement("UseStandardCommands", i.useStandardCommands)));
    }
    if (!Objects.equals(b.numerator, i.numerator)) {
      out.add(MdObjectPropertiesLeafDiff.GranularPatchChange.objectProperty(
        "Numerator", textElement("Numerator", nz(i.numerator))));
    }
    if (!Objects.equals(b.numberType, i.numberType)) {
      out.add(MdObjectPropertiesLeafDiff.GranularPatchChange.objectProperty(
        "NumberType", enumTextElement("NumberType", nz(i.numberType))));
    }
    if (!Objects.equals(b.numberLength, i.numberLength)) {
      out.add(MdObjectPropertiesLeafDiff.GranularPatchChange.objectProperty(
        "NumberLength", textElement("NumberLength", nz(i.numberLength))));
    }
    if (!Objects.equals(b.numberAllowedLength, i.numberAllowedLength)) {
      out.add(MdObjectPropertiesLeafDiff.GranularPatchChange.objectProperty(
        "NumberAllowedLength", enumTextElement("NumberAllowedLength", nz(i.numberAllowedLength))));
    }
    if (!Objects.equals(b.numberPeriodicity, i.numberPeriodicity)) {
      out.add(MdObjectPropertiesLeafDiff.GranularPatchChange.objectProperty(
        "NumberPeriodicity", enumTextElement("NumberPeriodicity", nz(i.numberPeriodicity))));
    }
    if (b.checkUnique != i.checkUnique) {
      out.add(MdObjectPropertiesLeafDiff.GranularPatchChange.objectProperty(
        "CheckUnique", boolElement("CheckUnique", i.checkUnique)));
    }
    if (b.autonumbering != i.autonumbering) {
      out.add(MdObjectPropertiesLeafDiff.GranularPatchChange.objectProperty(
        "Autonumbering", boolElement("Autonumbering", i.autonumbering)));
    }
    if (!MdObjectPropertiesDiff.looseXmlBlobEquals(b.standardAttributesXml, i.standardAttributesXml)) {
      out.add(MdObjectPropertiesLeafDiff.GranularPatchChange.objectProperty(
        "StandardAttributes", blobElement("StandardAttributes", i.standardAttributesXml)));
    }
    if (!MdObjectPropertiesDiff.looseXmlBlobEquals(b.characteristicsXml, i.characteristicsXml)) {
      out.add(MdObjectPropertiesLeafDiff.GranularPatchChange.objectProperty(
        "Characteristics", blobElement("Characteristics", i.characteristicsXml)));
    }
    if (!MdObjectPropertiesDiff.listStringEquals(b.basedOn, i.basedOn)) {
      out.add(MdObjectPropertiesLeafDiff.GranularPatchChange.objectProperty(
        "BasedOn", basedOnElement(i.basedOn)));
    }
    if (!MdObjectPropertiesDiff.listStringEquals(b.inputByString, i.inputByString)) {
      out.add(MdObjectPropertiesLeafDiff.GranularPatchChange.objectProperty(
        "InputByString", inputByStringElement(i.inputByString)));
    }
    if (!Objects.equals(b.createOnInput, i.createOnInput)) {
      out.add(MdObjectPropertiesLeafDiff.GranularPatchChange.objectProperty(
        "CreateOnInput", enumTextElement("CreateOnInput", nz(i.createOnInput))));
    }
    if (!Objects.equals(b.searchStringModeOnInputByString, i.searchStringModeOnInputByString)) {
      out.add(MdObjectPropertiesLeafDiff.GranularPatchChange.objectProperty(
        "SearchStringModeOnInputByString",
        enumTextElement("SearchStringModeOnInputByString", nz(i.searchStringModeOnInputByString))));
    }
    if (!Objects.equals(b.fullTextSearchOnInputByString, i.fullTextSearchOnInputByString)) {
      out.add(MdObjectPropertiesLeafDiff.GranularPatchChange.objectProperty(
        "FullTextSearchOnInputByString",
        enumTextElement("FullTextSearchOnInputByString", nz(i.fullTextSearchOnInputByString))));
    }
    if (!Objects.equals(b.choiceDataGetModeOnInputByString, i.choiceDataGetModeOnInputByString)) {
      out.add(MdObjectPropertiesLeafDiff.GranularPatchChange.objectProperty(
        "ChoiceDataGetModeOnInputByString",
        enumTextElement("ChoiceDataGetModeOnInputByString", nz(i.choiceDataGetModeOnInputByString))));
    }
    if (!Objects.equals(b.choiceHistoryOnInput, i.choiceHistoryOnInput)) {
      out.add(MdObjectPropertiesLeafDiff.GranularPatchChange.objectProperty(
        "ChoiceHistoryOnInput", enumTextElement("ChoiceHistoryOnInput", nz(i.choiceHistoryOnInput))));
    }
    if (!Objects.equals(b.defaultObjectForm, i.defaultObjectForm)) {
      out.add(MdObjectPropertiesLeafDiff.GranularPatchChange.objectProperty(
        "DefaultObjectForm", textElement("DefaultObjectForm", nz(i.defaultObjectForm))));
    }
    if (!Objects.equals(b.defaultListForm, i.defaultListForm)) {
      out.add(MdObjectPropertiesLeafDiff.GranularPatchChange.objectProperty(
        "DefaultListForm", textElement("DefaultListForm", nz(i.defaultListForm))));
    }
    if (!Objects.equals(b.defaultChoiceForm, i.defaultChoiceForm)) {
      out.add(MdObjectPropertiesLeafDiff.GranularPatchChange.objectProperty(
        "DefaultChoiceForm", textElement("DefaultChoiceForm", nz(i.defaultChoiceForm))));
    }
    if (!Objects.equals(b.auxiliaryObjectForm, i.auxiliaryObjectForm)) {
      out.add(MdObjectPropertiesLeafDiff.GranularPatchChange.objectProperty(
        "AuxiliaryObjectForm", textElement("AuxiliaryObjectForm", nz(i.auxiliaryObjectForm))));
    }
    if (!Objects.equals(b.auxiliaryListForm, i.auxiliaryListForm)) {
      out.add(MdObjectPropertiesLeafDiff.GranularPatchChange.objectProperty(
        "AuxiliaryListForm", textElement("AuxiliaryListForm", nz(i.auxiliaryListForm))));
    }
    if (!Objects.equals(b.auxiliaryChoiceForm, i.auxiliaryChoiceForm)) {
      out.add(MdObjectPropertiesLeafDiff.GranularPatchChange.objectProperty(
        "AuxiliaryChoiceForm", textElement("AuxiliaryChoiceForm", nz(i.auxiliaryChoiceForm))));
    }
    if (!Objects.equals(b.objectModule, i.objectModule)) {
      out.add(MdObjectPropertiesLeafDiff.GranularPatchChange.objectProperty(
        "ObjectModule", textElement("ObjectModule", nz(i.objectModule))));
    }
    if (!Objects.equals(b.managerModule, i.managerModule)) {
      out.add(MdObjectPropertiesLeafDiff.GranularPatchChange.objectProperty(
        "ManagerModule", textElement("ManagerModule", nz(i.managerModule))));
    }
    if (!Objects.equals(b.posting, i.posting)) {
      out.add(MdObjectPropertiesLeafDiff.GranularPatchChange.objectProperty(
        "Posting", enumTextElement("Posting", nz(i.posting))));
    }
    if (!Objects.equals(b.realTimePosting, i.realTimePosting)) {
      out.add(MdObjectPropertiesLeafDiff.GranularPatchChange.objectProperty(
        "RealTimePosting", enumTextElement("RealTimePosting", nz(i.realTimePosting))));
    }
    if (!Objects.equals(b.registerRecordsDeletion, i.registerRecordsDeletion)) {
      out.add(MdObjectPropertiesLeafDiff.GranularPatchChange.objectProperty(
        "RegisterRecordsDeletion", enumTextElement("RegisterRecordsDeletion", nz(i.registerRecordsDeletion))));
    }
    if (!Objects.equals(b.registerRecordsWritingOnPost, i.registerRecordsWritingOnPost)) {
      out.add(MdObjectPropertiesLeafDiff.GranularPatchChange.objectProperty(
        "RegisterRecordsWritingOnPost",
        enumTextElement("RegisterRecordsWritingOnPost", nz(i.registerRecordsWritingOnPost))));
    }
    if (!Objects.equals(b.sequenceFilling, i.sequenceFilling)) {
      out.add(MdObjectPropertiesLeafDiff.GranularPatchChange.objectProperty(
        "SequenceFilling", enumTextElement("SequenceFilling", nz(i.sequenceFilling))));
    }
    if (!MdObjectPropertiesDiff.listStringEquals(b.registerRecords, i.registerRecords)) {
      out.add(MdObjectPropertiesLeafDiff.GranularPatchChange.objectProperty(
        "RegisterRecords", mdListTypeRefsElement("RegisterRecords", i.registerRecords)));
    }
    if (b.postInPrivilegedMode != i.postInPrivilegedMode) {
      out.add(MdObjectPropertiesLeafDiff.GranularPatchChange.objectProperty(
        "PostInPrivilegedMode", boolElement("PostInPrivilegedMode", i.postInPrivilegedMode)));
    }
    if (b.unpostInPrivilegedMode != i.unpostInPrivilegedMode) {
      out.add(MdObjectPropertiesLeafDiff.GranularPatchChange.objectProperty(
        "UnpostInPrivilegedMode", boolElement("UnpostInPrivilegedMode", i.unpostInPrivilegedMode)));
    }
    if (b.includeHelpInContents != i.includeHelpInContents) {
      out.add(MdObjectPropertiesLeafDiff.GranularPatchChange.objectProperty(
        "IncludeHelpInContents", boolElement("IncludeHelpInContents", i.includeHelpInContents)));
    }
    if (!Objects.equals(b.help, i.help)) {
      out.add(MdObjectPropertiesLeafDiff.GranularPatchChange.objectProperty(
        "Help", textElement("Help", nz(i.help))));
    }
    if (!MdObjectPropertiesDiff.listStringEquals(b.dataLockFields, i.dataLockFields)) {
      out.add(MdObjectPropertiesLeafDiff.GranularPatchChange.objectProperty(
        "DataLockFields", dataLockFieldsElement(i.dataLockFields)));
    }
    if (!Objects.equals(b.dataLockControlMode, i.dataLockControlMode)) {
      out.add(MdObjectPropertiesLeafDiff.GranularPatchChange.objectProperty(
        "DataLockControlMode", enumTextElement("DataLockControlMode", nz(i.dataLockControlMode))));
    }
    if (!Objects.equals(b.fullTextSearch, i.fullTextSearch)) {
      out.add(MdObjectPropertiesLeafDiff.GranularPatchChange.objectProperty(
        "FullTextSearch", enumTextElement("FullTextSearch", nz(i.fullTextSearch))));
    }
    if (!Objects.equals(b.objectPresentationRu, i.objectPresentationRu)) {
      out.add(MdObjectPropertiesLeafDiff.GranularPatchChange.objectProperty(
        "ObjectPresentation", localStringRuElement("ObjectPresentation", i.objectPresentationRu)));
    }
    if (!Objects.equals(b.extendedObjectPresentationRu, i.extendedObjectPresentationRu)) {
      out.add(MdObjectPropertiesLeafDiff.GranularPatchChange.objectProperty(
        "ExtendedObjectPresentation",
        localStringRuElement("ExtendedObjectPresentation", i.extendedObjectPresentationRu)));
    }
    if (!Objects.equals(b.listPresentationRu, i.listPresentationRu)) {
      out.add(MdObjectPropertiesLeafDiff.GranularPatchChange.objectProperty(
        "ListPresentation", localStringRuElement("ListPresentation", i.listPresentationRu)));
    }
    if (!Objects.equals(b.extendedListPresentationRu, i.extendedListPresentationRu)) {
      out.add(MdObjectPropertiesLeafDiff.GranularPatchChange.objectProperty(
        "ExtendedListPresentation",
        localStringRuElement("ExtendedListPresentation", i.extendedListPresentationRu)));
    }
    if (!Objects.equals(b.explanationRu, i.explanationRu)) {
      out.add(MdObjectPropertiesLeafDiff.GranularPatchChange.objectProperty(
        "Explanation", localStringRuElement("Explanation", i.explanationRu)));
    }
    if (!Objects.equals(b.dataHistory, i.dataHistory)) {
      out.add(MdObjectPropertiesLeafDiff.GranularPatchChange.objectProperty(
        "DataHistory", enumTextElement("DataHistory", nz(i.dataHistory))));
    }
    if (b.updateDataHistoryImmediatelyAfterWrite != i.updateDataHistoryImmediatelyAfterWrite) {
      out.add(MdObjectPropertiesLeafDiff.GranularPatchChange.objectProperty(
        "UpdateDataHistoryImmediatelyAfterWrite",
        boolElement("UpdateDataHistoryImmediatelyAfterWrite", i.updateDataHistoryImmediatelyAfterWrite)));
    }
    if (b.executeAfterWriteDataHistoryVersionProcessing != i.executeAfterWriteDataHistoryVersionProcessing) {
      out.add(MdObjectPropertiesLeafDiff.GranularPatchChange.objectProperty(
        "ExecuteAfterWriteDataHistoryVersionProcessing",
        boolElement("ExecuteAfterWriteDataHistoryVersionProcessing",
          i.executeAfterWriteDataHistoryVersionProcessing)));
    }
    if (!Objects.equals(b.additionalIndexes, i.additionalIndexes)) {
      out.add(MdObjectPropertiesLeafDiff.GranularPatchChange.objectProperty(
        "AdditionalIndexes", textElement("AdditionalIndexes", nz(i.additionalIndexes))));
    }
  }

  private static String blobElement(String wrapperLocalName, String xml) {
    String x = xml == null ? "" : xml.trim();
    if (x.isEmpty()) {
      return "<" + wrapperLocalName + "/>";
    }
    return x;
  }
}
