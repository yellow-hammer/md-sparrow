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

/**
 * Сериализация отдельных прямых дочерних элементов {@code CatalogProperties} в XML-строку для гранулярной замены.
 */
public final class MdCatalogPropertiesGranularSerial {

  private MdCatalogPropertiesGranularSerial() {
  }

  static void appendCatalogScalarChanges(
    MdCatalogPropertiesDto b,
    MdCatalogPropertiesDto i,
    List<MdObjectPropertiesLeafDiff.GranularPatchChange> out) {
    if (!Objects.equals(b.objectBelonging, i.objectBelonging)) {
      out.add(MdObjectPropertiesLeafDiff.GranularPatchChange.objectProperty(
        "ObjectBelonging", textElement("ObjectBelonging", nz(i.objectBelonging))));
    }
    if (!Objects.equals(b.extendedConfigurationObject, i.extendedConfigurationObject)) {
      out.add(MdObjectPropertiesLeafDiff.GranularPatchChange.objectProperty(
        "ExtendedConfigurationObject",
        uuidLikeElement("ExtendedConfigurationObject", i.extendedConfigurationObject)));
    }
    if (b.hierarchical != i.hierarchical) {
      out.add(MdObjectPropertiesLeafDiff.GranularPatchChange.objectProperty(
        "Hierarchical", boolElement("Hierarchical", i.hierarchical)));
    }
    if (!Objects.equals(b.hierarchyType, i.hierarchyType)) {
      out.add(MdObjectPropertiesLeafDiff.GranularPatchChange.objectProperty(
        "HierarchyType", textElement("HierarchyType", nz(i.hierarchyType))));
    }
    if (b.limitLevelCount != i.limitLevelCount) {
      out.add(MdObjectPropertiesLeafDiff.GranularPatchChange.objectProperty(
        "LimitLevelCount", boolElement("LimitLevelCount", i.limitLevelCount)));
    }
    if (!Objects.equals(b.levelCount, i.levelCount)) {
      out.add(MdObjectPropertiesLeafDiff.GranularPatchChange.objectProperty(
        "LevelCount", textElement("LevelCount", nz(i.levelCount))));
    }
    if (b.foldersOnTop != i.foldersOnTop) {
      out.add(MdObjectPropertiesLeafDiff.GranularPatchChange.objectProperty(
        "FoldersOnTop", boolElement("FoldersOnTop", i.foldersOnTop)));
    }
    if (b.useStandardCommands != i.useStandardCommands) {
      out.add(MdObjectPropertiesLeafDiff.GranularPatchChange.objectProperty(
        "UseStandardCommands", boolElement("UseStandardCommands", i.useStandardCommands)));
    }
    if (!MdObjectPropertiesDiff.listStringEquals(b.owners, i.owners)) {
      out.add(MdObjectPropertiesLeafDiff.GranularPatchChange.objectProperty(
        "Owners", ownersElement(i.owners)));
    }
    if (!Objects.equals(b.subordinationUse, i.subordinationUse)) {
      out.add(MdObjectPropertiesLeafDiff.GranularPatchChange.objectProperty(
        "SubordinationUse", textElement("SubordinationUse", nz(i.subordinationUse))));
    }
    if (!Objects.equals(b.codeLength, i.codeLength)) {
      out.add(MdObjectPropertiesLeafDiff.GranularPatchChange.objectProperty(
        "CodeLength", textElement("CodeLength", nz(i.codeLength))));
    }
    if (!Objects.equals(b.descriptionLength, i.descriptionLength)) {
      out.add(MdObjectPropertiesLeafDiff.GranularPatchChange.objectProperty(
        "DescriptionLength", textElement("DescriptionLength", nz(i.descriptionLength))));
    }
    if (!Objects.equals(b.codeType, i.codeType)) {
      out.add(MdObjectPropertiesLeafDiff.GranularPatchChange.objectProperty(
        "CodeType", textElement("CodeType", nz(i.codeType))));
    }
    if (!Objects.equals(b.codeAllowedLength, i.codeAllowedLength)) {
      out.add(MdObjectPropertiesLeafDiff.GranularPatchChange.objectProperty(
        "CodeAllowedLength", textElement("CodeAllowedLength", nz(i.codeAllowedLength))));
    }
    if (!Objects.equals(b.codeSeries, i.codeSeries)) {
      out.add(MdObjectPropertiesLeafDiff.GranularPatchChange.objectProperty(
        "CodeSeries", textElement("CodeSeries", nz(i.codeSeries))));
    }
    if (b.checkUnique != i.checkUnique) {
      out.add(MdObjectPropertiesLeafDiff.GranularPatchChange.objectProperty(
        "CheckUnique", boolElement("CheckUnique", i.checkUnique)));
    }
    if (b.autonumbering != i.autonumbering) {
      out.add(MdObjectPropertiesLeafDiff.GranularPatchChange.objectProperty(
        "Autonumbering", boolElement("Autonumbering", i.autonumbering)));
    }
    if (!Objects.equals(b.defaultPresentation, i.defaultPresentation)) {
      out.add(MdObjectPropertiesLeafDiff.GranularPatchChange.objectProperty(
        "DefaultPresentation", textElement("DefaultPresentation", nz(i.defaultPresentation))));
    }
    if (!MdObjectPropertiesDiff.looseXmlBlobEquals(b.standardAttributesXml, i.standardAttributesXml)) {
      out.add(MdObjectPropertiesLeafDiff.GranularPatchChange.objectProperty(
        "StandardAttributes", blobElement("StandardAttributes", i.standardAttributesXml)));
    }
    if (!MdObjectPropertiesDiff.looseXmlBlobEquals(b.characteristicsXml, i.characteristicsXml)) {
      out.add(MdObjectPropertiesLeafDiff.GranularPatchChange.objectProperty(
        "Characteristics", blobElement("Characteristics", i.characteristicsXml)));
    }
    if (!Objects.equals(b.predefined, i.predefined)) {
      out.add(MdObjectPropertiesLeafDiff.GranularPatchChange.objectProperty(
        "Predefined", textElement("Predefined", nz(i.predefined))));
    }
    if (!Objects.equals(b.predefinedDataUpdate, i.predefinedDataUpdate)) {
      out.add(MdObjectPropertiesLeafDiff.GranularPatchChange.objectProperty(
        "PredefinedDataUpdate", textElement("PredefinedDataUpdate", nz(i.predefinedDataUpdate))));
    }
    if (!Objects.equals(b.editType, i.editType)) {
      out.add(MdObjectPropertiesLeafDiff.GranularPatchChange.objectProperty(
        "EditType", textElement("EditType", nz(i.editType))));
    }
    if (b.quickChoice != i.quickChoice) {
      out.add(MdObjectPropertiesLeafDiff.GranularPatchChange.objectProperty(
        "QuickChoice", boolElement("QuickChoice", i.quickChoice)));
    }
    if (!Objects.equals(b.choiceMode, i.choiceMode)) {
      out.add(MdObjectPropertiesLeafDiff.GranularPatchChange.objectProperty(
        "ChoiceMode", textElement("ChoiceMode", nz(i.choiceMode))));
    }
    if (!MdObjectPropertiesDiff.listStringEquals(b.inputByString, i.inputByString)) {
      out.add(MdObjectPropertiesLeafDiff.GranularPatchChange.objectProperty(
        "InputByString", inputByStringElement(i.inputByString)));
    }
    if (!Objects.equals(b.searchStringModeOnInputByString, i.searchStringModeOnInputByString)) {
      out.add(MdObjectPropertiesLeafDiff.GranularPatchChange.objectProperty(
        "SearchStringModeOnInputByString",
        textElement("SearchStringModeOnInputByString", nz(i.searchStringModeOnInputByString))));
    }
    if (!Objects.equals(b.fullTextSearchOnInputByString, i.fullTextSearchOnInputByString)) {
      out.add(MdObjectPropertiesLeafDiff.GranularPatchChange.objectProperty(
        "FullTextSearchOnInputByString",
        textElement("FullTextSearchOnInputByString", nz(i.fullTextSearchOnInputByString))));
    }
    if (!Objects.equals(b.choiceDataGetModeOnInputByString, i.choiceDataGetModeOnInputByString)) {
      out.add(MdObjectPropertiesLeafDiff.GranularPatchChange.objectProperty(
        "ChoiceDataGetModeOnInputByString",
        textElement("ChoiceDataGetModeOnInputByString", nz(i.choiceDataGetModeOnInputByString))));
    }
    if (!Objects.equals(b.defaultObjectForm, i.defaultObjectForm)) {
      out.add(MdObjectPropertiesLeafDiff.GranularPatchChange.objectProperty(
        "DefaultObjectForm", textElement("DefaultObjectForm", nz(i.defaultObjectForm))));
    }
    if (!Objects.equals(b.defaultFolderForm, i.defaultFolderForm)) {
      out.add(MdObjectPropertiesLeafDiff.GranularPatchChange.objectProperty(
        "DefaultFolderForm", textElement("DefaultFolderForm", nz(i.defaultFolderForm))));
    }
    if (!Objects.equals(b.defaultListForm, i.defaultListForm)) {
      out.add(MdObjectPropertiesLeafDiff.GranularPatchChange.objectProperty(
        "DefaultListForm", textElement("DefaultListForm", nz(i.defaultListForm))));
    }
    if (!Objects.equals(b.defaultChoiceForm, i.defaultChoiceForm)) {
      out.add(MdObjectPropertiesLeafDiff.GranularPatchChange.objectProperty(
        "DefaultChoiceForm", textElement("DefaultChoiceForm", nz(i.defaultChoiceForm))));
    }
    if (!Objects.equals(b.defaultFolderChoiceForm, i.defaultFolderChoiceForm)) {
      out.add(MdObjectPropertiesLeafDiff.GranularPatchChange.objectProperty(
        "DefaultFolderChoiceForm", textElement("DefaultFolderChoiceForm", nz(i.defaultFolderChoiceForm))));
    }
    if (!Objects.equals(b.auxiliaryObjectForm, i.auxiliaryObjectForm)) {
      out.add(MdObjectPropertiesLeafDiff.GranularPatchChange.objectProperty(
        "AuxiliaryObjectForm", textElement("AuxiliaryObjectForm", nz(i.auxiliaryObjectForm))));
    }
    if (!Objects.equals(b.auxiliaryFolderForm, i.auxiliaryFolderForm)) {
      out.add(MdObjectPropertiesLeafDiff.GranularPatchChange.objectProperty(
        "AuxiliaryFolderForm", textElement("AuxiliaryFolderForm", nz(i.auxiliaryFolderForm))));
    }
    if (!Objects.equals(b.auxiliaryListForm, i.auxiliaryListForm)) {
      out.add(MdObjectPropertiesLeafDiff.GranularPatchChange.objectProperty(
        "AuxiliaryListForm", textElement("AuxiliaryListForm", nz(i.auxiliaryListForm))));
    }
    if (!Objects.equals(b.auxiliaryChoiceForm, i.auxiliaryChoiceForm)) {
      out.add(MdObjectPropertiesLeafDiff.GranularPatchChange.objectProperty(
        "AuxiliaryChoiceForm", textElement("AuxiliaryChoiceForm", nz(i.auxiliaryChoiceForm))));
    }
    if (!Objects.equals(b.auxiliaryFolderChoiceForm, i.auxiliaryFolderChoiceForm)) {
      out.add(MdObjectPropertiesLeafDiff.GranularPatchChange.objectProperty(
        "AuxiliaryFolderChoiceForm", textElement("AuxiliaryFolderChoiceForm", nz(i.auxiliaryFolderChoiceForm))));
    }
    if (!Objects.equals(b.objectModule, i.objectModule)) {
      out.add(MdObjectPropertiesLeafDiff.GranularPatchChange.objectProperty(
        "ObjectModule", textElement("ObjectModule", nz(i.objectModule))));
    }
    if (!Objects.equals(b.managerModule, i.managerModule)) {
      out.add(MdObjectPropertiesLeafDiff.GranularPatchChange.objectProperty(
        "ManagerModule", textElement("ManagerModule", nz(i.managerModule))));
    }
    if (b.includeHelpInContents != i.includeHelpInContents) {
      out.add(MdObjectPropertiesLeafDiff.GranularPatchChange.objectProperty(
        "IncludeHelpInContents", boolElement("IncludeHelpInContents", i.includeHelpInContents)));
    }
    if (!Objects.equals(b.help, i.help)) {
      out.add(MdObjectPropertiesLeafDiff.GranularPatchChange.objectProperty(
        "Help", textElement("Help", nz(i.help))));
    }
    if (!MdObjectPropertiesDiff.listStringEquals(b.basedOn, i.basedOn)) {
      out.add(MdObjectPropertiesLeafDiff.GranularPatchChange.objectProperty(
        "BasedOn", basedOnElement(i.basedOn)));
    }
    if (!MdObjectPropertiesDiff.listStringEquals(b.dataLockFields, i.dataLockFields)) {
      out.add(MdObjectPropertiesLeafDiff.GranularPatchChange.objectProperty(
        "DataLockFields", dataLockFieldsElement(i.dataLockFields)));
    }
    if (!Objects.equals(b.dataLockControlMode, i.dataLockControlMode)) {
      out.add(MdObjectPropertiesLeafDiff.GranularPatchChange.objectProperty(
        "DataLockControlMode", textElement("DataLockControlMode", nz(i.dataLockControlMode))));
    }
    if (!Objects.equals(b.fullTextSearch, i.fullTextSearch)) {
      out.add(MdObjectPropertiesLeafDiff.GranularPatchChange.objectProperty(
        "FullTextSearch", textElement("FullTextSearch", nz(i.fullTextSearch))));
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
    if (!Objects.equals(b.createOnInput, i.createOnInput)) {
      out.add(MdObjectPropertiesLeafDiff.GranularPatchChange.objectProperty(
        "CreateOnInput", textElement("CreateOnInput", nz(i.createOnInput))));
    }
    if (!Objects.equals(b.choiceHistoryOnInput, i.choiceHistoryOnInput)) {
      out.add(MdObjectPropertiesLeafDiff.GranularPatchChange.objectProperty(
        "ChoiceHistoryOnInput", textElement("ChoiceHistoryOnInput", nz(i.choiceHistoryOnInput))));
    }
    if (!Objects.equals(b.dataHistory, i.dataHistory)) {
      out.add(MdObjectPropertiesLeafDiff.GranularPatchChange.objectProperty(
        "DataHistory", textElement("DataHistory", nz(i.dataHistory))));
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

  private static String nz(String s) {
    return s == null ? "" : s;
  }

  static String textElement(String localName, String text) {
    return "<" + localName + ">" + escapeXml(text) + "</" + localName + ">";
  }

  static String boolElement(String localName, boolean v) {
    return "<" + localName + ">" + v + "</" + localName + ">";
  }

  static String commentElement(String comment) {
    String c = comment == null ? "" : comment;
    if (c.isEmpty()) {
      return "<Comment/>";
    }
    return "<Comment>" + escapeXml(c) + "</Comment>";
  }

  static String synonymElementRu(String ru) {
    String t = ru == null ? "" : ru;
    return "<Synonym>"
      + "<v8:item>"
      + "<v8:lang>ru</v8:lang>"
      + "<v8:content>" + escapeXml(t) + "</v8:content>"
      + "</v8:item>"
      + "</Synonym>";
  }

  static String localStringRuElement(String localName, String ru) {
    String t = ru == null ? "" : ru;
    return "<" + localName + ">"
      + "<v8:item>"
      + "<v8:lang>ru</v8:lang>"
      + "<v8:content>" + escapeXml(t) + "</v8:content>"
      + "</v8:item>"
      + "</" + localName + ">";
  }

  private static String uuidLikeElement(String localName, String uuid) {
    if (uuid == null || uuid.isBlank()) {
      return "<" + localName + "/>";
    }
    return textElement(localName, uuid.trim());
  }

  private static String blobElement(String wrapperLocalName, String xml) {
    String x = xml == null ? "" : xml.trim();
    if (x.isEmpty()) {
      return "<" + wrapperLocalName + "/>";
    }
    return x;
  }

  static String mdListTypeRefsElement(String listElementName, List<String> items) {
    StringBuilder sb = new StringBuilder();
    sb.append("<").append(listElementName).append(">");
    if (items != null) {
      for (String t : items) {
        if (t == null || t.isBlank()) {
          continue;
        }
        sb.append("<xr:Item xsi:type=\"xr:MDObjectRef\">")
          .append(escapeXml(t.trim()))
          .append("</xr:Item>");
      }
    }
    sb.append("</").append(listElementName).append(">");
    return sb.toString();
  }

  private static String ownersElement(List<String> owners) {
    return mdListTypeRefsElement("Owners", owners);
  }

  private static String basedOnElement(List<String> items) {
    return mdListTypeRefsElement("BasedOn", items);
  }

  private static String inputByStringElement(List<String> fields) {
    StringBuilder sb = new StringBuilder("<InputByString>");
    if (fields != null) {
      for (String f : fields) {
        if (f == null || f.isBlank()) {
          continue;
        }
        sb.append("<Field>").append(escapeXml(f.trim())).append("</Field>");
      }
    }
    sb.append("</InputByString>");
    return sb.toString();
  }

  private static String dataLockFieldsElement(List<String> fields) {
    StringBuilder sb = new StringBuilder("<DataLockFields>");
    if (fields != null) {
      for (String f : fields) {
        if (f == null || f.isBlank()) {
          continue;
        }
        sb.append("<Field>").append(escapeXml(f.trim())).append("</Field>");
      }
    }
    sb.append("</DataLockFields>");
    return sb.toString();
  }

  private static String escapeXml(String s) {
    return s.replace("&", "&amp;")
      .replace("<", "&lt;")
      .replace(">", "&gt;")
      .replace("\"", "&quot;")
      .replace("'", "&apos;");
  }
}
