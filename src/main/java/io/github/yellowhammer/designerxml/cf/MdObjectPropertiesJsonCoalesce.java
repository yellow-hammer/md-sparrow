/*
 * This file is a part of md-sparrow.
 *
 * Copyright (c) 2026
 * Ivan Karlo <i.karlo@outlook.com> and contributors
 *
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package io.github.yellowhammer.designerxml.cf;

import java.util.ArrayList;
import java.util.List;

/**
 * Заполняет в DTO из JSON ({@code cf-md-object-set}) поля {@code null} значениями из baseline (снимок с диска),
 * чтобы Gson не ломал сравнение с результатом {@link MdObjectPropertiesEdit#readDto} после точечной записи.
 */
public final class MdObjectPropertiesJsonCoalesce {

  private MdObjectPropertiesJsonCoalesce() {
  }

  /**
   * Мутирует {@code incoming}: null-поля и пустые списки, которые Gson не заполнил, подставляются из {@code baseline}.
   */
  public static void coalesceFromBaseline(MdObjectPropertiesDto incoming, MdObjectPropertiesDto baseline) {
    if (incoming == null || baseline == null) {
      return;
    }
    if (incoming.synonymRu == null) {
      incoming.synonymRu = baseline.synonymRu;
    }
    if (incoming.comment == null) {
      incoming.comment = baseline.comment;
    }
    if (incoming.attributes == null) {
      incoming.attributes = copyNamedList(baseline.attributes);
    }
    if (incoming.tabularSections == null) {
      incoming.tabularSections = copyNamedList(baseline.tabularSections);
    }
    if (incoming.nestedSubsystems == null) {
      incoming.nestedSubsystems = baseline.nestedSubsystems == null
        ? new ArrayList<>()
        : new ArrayList<>(baseline.nestedSubsystems);
    }
    if (incoming.contentRefs == null) {
      incoming.contentRefs = baseline.contentRefs == null
        ? new ArrayList<>()
        : new ArrayList<>(baseline.contentRefs);
    }
    if ("catalog".equals(incoming.kind) && baseline.catalog != null) {
      if (incoming.catalog == null) {
        incoming.catalog = copyCatalogFull(baseline.catalog);
      } else {
        coalesceCatalog(incoming.catalog, baseline.catalog);
      }
    }
  }

  private static MdCatalogPropertiesDto copyCatalogFull(MdCatalogPropertiesDto s) {
    MdCatalogPropertiesDto d = new MdCatalogPropertiesDto();
    d.objectBelonging = s.objectBelonging;
    d.extendedConfigurationObject = s.extendedConfigurationObject;
    d.hierarchical = s.hierarchical;
    d.hierarchyType = s.hierarchyType;
    d.limitLevelCount = s.limitLevelCount;
    d.levelCount = s.levelCount;
    d.foldersOnTop = s.foldersOnTop;
    d.useStandardCommands = s.useStandardCommands;
    d.owners = s.owners == null ? new ArrayList<>() : new ArrayList<>(s.owners);
    d.subordinationUse = s.subordinationUse;
    d.codeLength = s.codeLength;
    d.descriptionLength = s.descriptionLength;
    d.codeType = s.codeType;
    d.codeAllowedLength = s.codeAllowedLength;
    d.codeSeries = s.codeSeries;
    d.checkUnique = s.checkUnique;
    d.autonumbering = s.autonumbering;
    d.defaultPresentation = s.defaultPresentation;
    d.standardAttributesXml = s.standardAttributesXml;
    d.characteristicsXml = s.characteristicsXml;
    d.predefined = s.predefined;
    d.predefinedDataUpdate = s.predefinedDataUpdate;
    d.editType = s.editType;
    d.quickChoice = s.quickChoice;
    d.choiceMode = s.choiceMode;
    d.inputByString = s.inputByString == null ? new ArrayList<>() : new ArrayList<>(s.inputByString);
    d.searchStringModeOnInputByString = s.searchStringModeOnInputByString;
    d.fullTextSearchOnInputByString = s.fullTextSearchOnInputByString;
    d.choiceDataGetModeOnInputByString = s.choiceDataGetModeOnInputByString;
    d.defaultObjectForm = s.defaultObjectForm;
    d.defaultFolderForm = s.defaultFolderForm;
    d.defaultListForm = s.defaultListForm;
    d.defaultChoiceForm = s.defaultChoiceForm;
    d.defaultFolderChoiceForm = s.defaultFolderChoiceForm;
    d.auxiliaryObjectForm = s.auxiliaryObjectForm;
    d.auxiliaryFolderForm = s.auxiliaryFolderForm;
    d.auxiliaryListForm = s.auxiliaryListForm;
    d.auxiliaryChoiceForm = s.auxiliaryChoiceForm;
    d.auxiliaryFolderChoiceForm = s.auxiliaryFolderChoiceForm;
    d.objectModule = s.objectModule;
    d.managerModule = s.managerModule;
    d.includeHelpInContents = s.includeHelpInContents;
    d.help = s.help;
    d.basedOn = s.basedOn == null ? new ArrayList<>() : new ArrayList<>(s.basedOn);
    d.dataLockFields = s.dataLockFields == null ? new ArrayList<>() : new ArrayList<>(s.dataLockFields);
    d.dataLockControlMode = s.dataLockControlMode;
    d.fullTextSearch = s.fullTextSearch;
    d.objectPresentationRu = s.objectPresentationRu;
    d.extendedObjectPresentationRu = s.extendedObjectPresentationRu;
    d.listPresentationRu = s.listPresentationRu;
    d.extendedListPresentationRu = s.extendedListPresentationRu;
    d.explanationRu = s.explanationRu;
    d.createOnInput = s.createOnInput;
    d.choiceHistoryOnInput = s.choiceHistoryOnInput;
    d.dataHistory = s.dataHistory;
    d.updateDataHistoryImmediatelyAfterWrite = s.updateDataHistoryImmediatelyAfterWrite;
    d.executeAfterWriteDataHistoryVersionProcessing = s.executeAfterWriteDataHistoryVersionProcessing;
    d.additionalIndexes = s.additionalIndexes;
    return d;
  }

  private static List<MdNamedPropertyDto> copyNamedList(List<MdNamedPropertyDto> src) {
    if (src == null) {
      return new ArrayList<>();
    }
    List<MdNamedPropertyDto> out = new ArrayList<>();
    for (MdNamedPropertyDto x : src) {
      if (x == null) {
        continue;
      }
      MdNamedPropertyDto c = new MdNamedPropertyDto(x.name, x.synonymRu, x.comment);
      out.add(c);
    }
    return out;
  }

  private static void coalesceCatalog(MdCatalogPropertiesDto d, MdCatalogPropertiesDto b) {
    if (d.objectBelonging == null) {
      d.objectBelonging = b.objectBelonging;
    }
    if (d.extendedConfigurationObject == null) {
      d.extendedConfigurationObject = b.extendedConfigurationObject;
    }
    if (d.hierarchyType == null) {
      d.hierarchyType = b.hierarchyType;
    }
    if (d.levelCount == null) {
      d.levelCount = b.levelCount;
    }
    if (d.subordinationUse == null) {
      d.subordinationUse = b.subordinationUse;
    }
    if (d.codeLength == null) {
      d.codeLength = b.codeLength;
    }
    if (d.descriptionLength == null) {
      d.descriptionLength = b.descriptionLength;
    }
    if (d.codeType == null) {
      d.codeType = b.codeType;
    }
    if (d.codeAllowedLength == null) {
      d.codeAllowedLength = b.codeAllowedLength;
    }
    if (d.codeSeries == null) {
      d.codeSeries = b.codeSeries;
    }
    if (d.defaultPresentation == null) {
      d.defaultPresentation = b.defaultPresentation;
    }
    if (d.standardAttributesXml == null) {
      d.standardAttributesXml = b.standardAttributesXml;
    }
    if (d.characteristicsXml == null) {
      d.characteristicsXml = b.characteristicsXml;
    }
    if (d.predefined == null) {
      d.predefined = b.predefined;
    }
    if (d.predefinedDataUpdate == null) {
      d.predefinedDataUpdate = b.predefinedDataUpdate;
    }
    if (d.editType == null) {
      d.editType = b.editType;
    }
    if (d.choiceMode == null) {
      d.choiceMode = b.choiceMode;
    }
    if (d.searchStringModeOnInputByString == null) {
      d.searchStringModeOnInputByString = b.searchStringModeOnInputByString;
    }
    if (d.fullTextSearchOnInputByString == null) {
      d.fullTextSearchOnInputByString = b.fullTextSearchOnInputByString;
    }
    if (d.choiceDataGetModeOnInputByString == null) {
      d.choiceDataGetModeOnInputByString = b.choiceDataGetModeOnInputByString;
    }
    if (d.defaultObjectForm == null) {
      d.defaultObjectForm = b.defaultObjectForm;
    }
    if (d.defaultFolderForm == null) {
      d.defaultFolderForm = b.defaultFolderForm;
    }
    if (d.defaultListForm == null) {
      d.defaultListForm = b.defaultListForm;
    }
    if (d.defaultChoiceForm == null) {
      d.defaultChoiceForm = b.defaultChoiceForm;
    }
    if (d.defaultFolderChoiceForm == null) {
      d.defaultFolderChoiceForm = b.defaultFolderChoiceForm;
    }
    if (d.auxiliaryObjectForm == null) {
      d.auxiliaryObjectForm = b.auxiliaryObjectForm;
    }
    if (d.auxiliaryFolderForm == null) {
      d.auxiliaryFolderForm = b.auxiliaryFolderForm;
    }
    if (d.auxiliaryListForm == null) {
      d.auxiliaryListForm = b.auxiliaryListForm;
    }
    if (d.auxiliaryChoiceForm == null) {
      d.auxiliaryChoiceForm = b.auxiliaryChoiceForm;
    }
    if (d.auxiliaryFolderChoiceForm == null) {
      d.auxiliaryFolderChoiceForm = b.auxiliaryFolderChoiceForm;
    }
    if (d.objectModule == null) {
      d.objectModule = b.objectModule;
    }
    if (d.managerModule == null) {
      d.managerModule = b.managerModule;
    }
    if (d.help == null) {
      d.help = b.help;
    }
    if (d.dataLockControlMode == null) {
      d.dataLockControlMode = b.dataLockControlMode;
    }
    if (d.fullTextSearch == null) {
      d.fullTextSearch = b.fullTextSearch;
    }
    if (d.objectPresentationRu == null) {
      d.objectPresentationRu = b.objectPresentationRu;
    }
    if (d.extendedObjectPresentationRu == null) {
      d.extendedObjectPresentationRu = b.extendedObjectPresentationRu;
    }
    if (d.listPresentationRu == null) {
      d.listPresentationRu = b.listPresentationRu;
    }
    if (d.extendedListPresentationRu == null) {
      d.extendedListPresentationRu = b.extendedListPresentationRu;
    }
    if (d.explanationRu == null) {
      d.explanationRu = b.explanationRu;
    }
    if (d.createOnInput == null) {
      d.createOnInput = b.createOnInput;
    }
    if (d.choiceHistoryOnInput == null) {
      d.choiceHistoryOnInput = b.choiceHistoryOnInput;
    }
    if (d.dataHistory == null) {
      d.dataHistory = b.dataHistory;
    }
    if (d.additionalIndexes == null) {
      d.additionalIndexes = b.additionalIndexes;
    }
    if (d.owners == null) {
      d.owners = b.owners == null ? new ArrayList<>() : new ArrayList<>(b.owners);
    }
    if (d.basedOn == null) {
      d.basedOn = b.basedOn == null ? new ArrayList<>() : new ArrayList<>(b.basedOn);
    }
    if (d.inputByString == null) {
      d.inputByString = b.inputByString == null ? new ArrayList<>() : new ArrayList<>(b.inputByString);
    }
    if (d.dataLockFields == null) {
      d.dataLockFields = b.dataLockFields == null ? new ArrayList<>() : new ArrayList<>(b.dataLockFields);
    }
    canonicalizeCatalogXmlBlobsFromBaselineIfLooseEqual(d, b);
  }

  /**
   * Если из JSON пришёл тот же смысл blob, что и в baseline с диска, подставляем строку с диска — иначе
   * {@link MdCatalogPropertiesGranularSerial#appendCatalogScalarChanges} добавляет лишние замены в
   * {@code Catalog/Properties} и мешает гранулярной записи только реквизитов.
   */
  private static void canonicalizeCatalogXmlBlobsFromBaselineIfLooseEqual(
    MdCatalogPropertiesDto d,
    MdCatalogPropertiesDto b) {
    if (d.standardAttributesXml != null && b.standardAttributesXml != null
      && MdObjectPropertiesDiff.looseXmlBlobEquals(d.standardAttributesXml, b.standardAttributesXml)) {
      d.standardAttributesXml = b.standardAttributesXml;
    }
    if (d.characteristicsXml != null && b.characteristicsXml != null
      && MdObjectPropertiesDiff.looseXmlBlobEquals(d.characteristicsXml, b.characteristicsXml)) {
      d.characteristicsXml = b.characteristicsXml;
    }
  }
}
