/*
 * This file is a part of md-sparrow.
 *
 * Copyright (c) 2026
 * Ivan Karlo <i.karlo@outlook.com> and contributors
 *
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package io.github.yellowhammer.designerxml.cf;

import jakarta.xml.bind.JAXBException;

import java.math.BigDecimal;

/**
 * Заполнение {@link MdCatalogPropertiesDto} из JAXB и обратная запись в {@code CatalogProperties} (2_20 / 2_21).
 */
public final class MdCatalogPropertiesBridge {

  private MdCatalogPropertiesBridge() {
  }

  /**
   * Пустой {@code ExtendedConfigurationObject} в выгрузке ({@code <ExtendedConfigurationObject/>}) даёт
   * пустую строку в JAXB; для типа UUID в XDTO пустое значение недопустимо при загрузке в ИБ — в DTO и при записи
   * используем {@code null}, тогда элемент опускается (minOccurs=0).
   */
  private static String nullIfBlankUuid(String s) {
    if (s == null || s.isBlank()) {
      return null;
    }
    return s;
  }

  public static void readV21(
    io.github.yellowhammer.designerxml.jaxb.v2_21.mdclasses.CatalogProperties p,
    MdObjectPropertiesDto dto) {
    MdCatalogPropertiesDto c = new MdCatalogPropertiesDto();
    if (p.getObjectBelonging() != null) {
      c.objectBelonging = p.getObjectBelonging().name();
    }
    c.extendedConfigurationObject = nullIfBlankUuid(p.getExtendedConfigurationObject());
    c.hierarchical = p.isHierarchical();
    if (p.getHierarchyType() != null) {
      c.hierarchyType = p.getHierarchyType().name();
    }
    c.limitLevelCount = p.isLimitLevelCount();
    c.levelCount = decimalToString(p.getLevelCount());
    c.foldersOnTop = p.isFoldersOnTop();
    c.useStandardCommands = p.isUseStandardCommands();
    if (p.getOwners() != null) {
      c.owners.addAll(MdListTypeRefs.readItemTexts(p.getOwners().getItem()));
    }
    if (p.getSubordinationUse() != null) {
      c.subordinationUse = p.getSubordinationUse().name();
    }
    c.codeLength = decimalToString(p.getCodeLength());
    c.descriptionLength = decimalToString(p.getDescriptionLength());
    if (p.getCodeType() != null) {
      c.codeType = p.getCodeType().name();
    }
    if (p.getCodeAllowedLength() != null) {
      c.codeAllowedLength = p.getCodeAllowedLength().name();
    }
    if (p.getCodeSeries() != null) {
      c.codeSeries = p.getCodeSeries().name();
    }
    c.checkUnique = p.isCheckUnique();
    c.autonumbering = p.isAutonumbering();
    if (p.getDefaultPresentation() != null) {
      c.defaultPresentation = p.getDefaultPresentation().name();
    }
    try {
      c.standardAttributesXml = MdCfCatalogSubtreeXml.marshalStandardAttributesV21(p.getStandardAttributes());
    } catch (JAXBException e) {
      c.standardAttributesXml = "";
    }
    try {
      c.characteristicsXml = MdCfCatalogSubtreeXml.marshalCharacteristicsV21(p.getCharacteristics());
    } catch (JAXBException e) {
      c.characteristicsXml = "";
    }
    c.predefined = p.getPredefined();
    if (p.getPredefinedDataUpdate() != null) {
      c.predefinedDataUpdate = p.getPredefinedDataUpdate().name();
    }
    if (p.getEditType() != null) {
      c.editType = p.getEditType().name();
    }
    c.quickChoice = p.isQuickChoice();
    if (p.getChoiceMode() != null) {
      c.choiceMode = p.getChoiceMode().name();
    }
    if (p.getInputByString() != null) {
      c.inputByString.addAll(p.getInputByString().getField());
    }
    if (p.getSearchStringModeOnInputByString() != null) {
      c.searchStringModeOnInputByString = p.getSearchStringModeOnInputByString().name();
    }
    if (p.getFullTextSearchOnInputByString() != null) {
      c.fullTextSearchOnInputByString = p.getFullTextSearchOnInputByString().name();
    }
    if (p.getChoiceDataGetModeOnInputByString() != null) {
      c.choiceDataGetModeOnInputByString = p.getChoiceDataGetModeOnInputByString().name();
    }
    c.defaultObjectForm = p.getDefaultObjectForm();
    c.defaultFolderForm = p.getDefaultFolderForm();
    c.defaultListForm = p.getDefaultListForm();
    c.defaultChoiceForm = p.getDefaultChoiceForm();
    c.defaultFolderChoiceForm = p.getDefaultFolderChoiceForm();
    c.auxiliaryObjectForm = p.getAuxiliaryObjectForm();
    c.auxiliaryFolderForm = p.getAuxiliaryFolderForm();
    c.auxiliaryListForm = p.getAuxiliaryListForm();
    c.auxiliaryChoiceForm = p.getAuxiliaryChoiceForm();
    c.auxiliaryFolderChoiceForm = p.getAuxiliaryFolderChoiceForm();
    c.objectModule = p.getObjectModule();
    c.managerModule = p.getManagerModule();
    c.includeHelpInContents = p.isIncludeHelpInContents();
    c.help = p.getHelp();
    if (p.getBasedOn() != null) {
      c.basedOn.addAll(MdListTypeRefs.readItemTexts(p.getBasedOn().getItem()));
    }
    if (p.getDataLockFields() != null) {
      c.dataLockFields.addAll(p.getDataLockFields().getField());
    }
    if (p.getDataLockControlMode() != null) {
      c.dataLockControlMode = p.getDataLockControlMode().name();
    }
    if (p.getFullTextSearch() != null) {
      c.fullTextSearch = p.getFullTextSearch().name();
    }
    c.objectPresentationRu = LocalStringSync.firstRuV21(p.getObjectPresentation());
    c.extendedObjectPresentationRu = LocalStringSync.firstRuV21(p.getExtendedObjectPresentation());
    c.listPresentationRu = LocalStringSync.firstRuV21(p.getListPresentation());
    c.extendedListPresentationRu = LocalStringSync.firstRuV21(p.getExtendedListPresentation());
    c.explanationRu = LocalStringSync.firstRuV21(p.getExplanation());
    if (p.getCreateOnInput() != null) {
      c.createOnInput = p.getCreateOnInput().name();
    }
    if (p.getChoiceHistoryOnInput() != null) {
      c.choiceHistoryOnInput = p.getChoiceHistoryOnInput().name();
    }
    if (p.getDataHistory() != null) {
      c.dataHistory = p.getDataHistory().name();
    }
    c.updateDataHistoryImmediatelyAfterWrite = p.isUpdateDataHistoryImmediatelyAfterWrite();
    c.executeAfterWriteDataHistoryVersionProcessing = p.isExecuteAfterWriteDataHistoryVersionProcessing();
    c.additionalIndexes = p.getAdditionalIndexes();
    dto.catalog = c;
  }

  public static void applyV21(
    io.github.yellowhammer.designerxml.jaxb.v2_21.mdclasses.CatalogProperties p,
    MdObjectPropertiesDto dto) {
    MdCatalogPropertiesDto c = dto.catalog;
    if (c == null) {
      throw new IllegalArgumentException("catalog required");
    }
    if (!dto.internalName.equals(p.getName())) {
      throw new IllegalArgumentException("internalName mismatch with XML");
    }
    String syn = dto.synonymRu == null ? "" : dto.synonymRu;
    LocalStringSync.setOrPutRuV21(p.getSynonym(), syn);
    p.setComment(dto.comment == null ? "" : dto.comment);
    if (c.objectBelonging != null && !c.objectBelonging.isEmpty()) {
      p.setObjectBelonging(
        io.github.yellowhammer.designerxml.jaxb.v2_21.v8_3_xcf_enums.ObjectBelonging.valueOf(c.objectBelonging));
    }
    p.setExtendedConfigurationObject(nullIfBlankUuid(c.extendedConfigurationObject));
    p.setHierarchical(c.hierarchical);
    if (c.hierarchyType != null && !c.hierarchyType.isEmpty()) {
      p.setHierarchyType(
        io.github.yellowhammer.designerxml.jaxb.v2_21.v8_3_xcf_enums.HierarchyType.valueOf(c.hierarchyType));
    }
    p.setLimitLevelCount(c.limitLevelCount);
    p.setLevelCount(new BigDecimal(nzDecimal(c.levelCount)));
    p.setFoldersOnTop(c.foldersOnTop);
    p.setUseStandardCommands(c.useStandardCommands);
    MdListTypeRefs.replaceItemsV21(p.getOwners(), c.owners);
    if (c.subordinationUse != null && !c.subordinationUse.isEmpty()) {
      p.setSubordinationUse(
        io.github.yellowhammer.designerxml.jaxb.v2_21.v8_3_xcf_enums.SubordinationUse.valueOf(c.subordinationUse));
    }
    p.setCodeLength(new BigDecimal(nzDecimal(c.codeLength)));
    p.setDescriptionLength(new BigDecimal(nzDecimal(c.descriptionLength)));
    if (c.codeType != null && !c.codeType.isEmpty()) {
      p.setCodeType(io.github.yellowhammer.designerxml.jaxb.v2_21.v8_3_xcf_enums.CatalogCodeType.valueOf(c.codeType));
    }
    if (c.codeAllowedLength != null && !c.codeAllowedLength.isEmpty()) {
      p.setCodeAllowedLength(
        io.github.yellowhammer.designerxml.jaxb.v2_21.v8_1_data_core.AllowedLength.valueOf(c.codeAllowedLength));
    }
    if (c.codeSeries != null && !c.codeSeries.isEmpty()) {
      p.setCodeSeries(
        io.github.yellowhammer.designerxml.jaxb.v2_21.v8_3_xcf_enums.CatalogCodesSeries.valueOf(c.codeSeries));
    }
    p.setCheckUnique(c.checkUnique);
    p.setAutonumbering(c.autonumbering);
    if (c.defaultPresentation != null && !c.defaultPresentation.isEmpty()) {
      p.setDefaultPresentation(
        io.github.yellowhammer.designerxml.jaxb.v2_21.v8_3_xcf_enums.CatalogMainPresentation.valueOf(
          c.defaultPresentation));
    }
    if (c.standardAttributesXml != null && !c.standardAttributesXml.isBlank()) {
      try {
        p.setStandardAttributes(MdCfCatalogSubtreeXml.unmarshalStandardAttributesV21(c.standardAttributesXml.trim()));
      } catch (JAXBException e) {
        throw new IllegalArgumentException("standardAttributesXml: " + e.getMessage(), e);
      }
    }
    if (c.characteristicsXml != null && !c.characteristicsXml.isBlank()) {
      try {
        p.setCharacteristics(MdCfCatalogSubtreeXml.unmarshalCharacteristicsV21(c.characteristicsXml.trim()));
      } catch (JAXBException e) {
        throw new IllegalArgumentException("characteristicsXml: " + e.getMessage(), e);
      }
    }
    p.setPredefined(c.predefined);
    if (c.predefinedDataUpdate != null && !c.predefinedDataUpdate.isEmpty()) {
      p.setPredefinedDataUpdate(
        io.github.yellowhammer.designerxml.jaxb.v2_21.v8_3_xcf_enums.PredefinedDataUpdate.valueOf(
          c.predefinedDataUpdate));
    }
    if (c.editType != null && !c.editType.isEmpty()) {
      p.setEditType(io.github.yellowhammer.designerxml.jaxb.v2_21.v8_3_xcf_enums.EditType.valueOf(c.editType));
    }
    p.setQuickChoice(c.quickChoice);
    if (c.choiceMode != null && !c.choiceMode.isEmpty()) {
      p.setChoiceMode(io.github.yellowhammer.designerxml.jaxb.v2_21.v8_3_xcf_enums.ChoiceMode.valueOf(c.choiceMode));
    }
    if (p.getInputByString() != null) {
      p.getInputByString().getField().clear();
      if (c.inputByString != null) {
        p.getInputByString().getField().addAll(c.inputByString);
      }
    }
    if (c.searchStringModeOnInputByString != null && !c.searchStringModeOnInputByString.isEmpty()) {
      p.setSearchStringModeOnInputByString(
        io.github.yellowhammer.designerxml.jaxb.v2_21.v8_2_managed_application_logform.SearchStringModeOnInputByString.valueOf(
          c.searchStringModeOnInputByString));
    }
    if (c.fullTextSearchOnInputByString != null && !c.fullTextSearchOnInputByString.isEmpty()) {
      p.setFullTextSearchOnInputByString(
        io.github.yellowhammer.designerxml.jaxb.v2_21.v8_2_managed_application_logform.FullTextSearchOnInputByString.valueOf(
          c.fullTextSearchOnInputByString));
    }
    if (c.choiceDataGetModeOnInputByString != null && !c.choiceDataGetModeOnInputByString.isEmpty()) {
      p.setChoiceDataGetModeOnInputByString(
        io.github.yellowhammer.designerxml.jaxb.v2_21.v8_2_managed_application_logform.ChoiceDataGetModeOnInputByString.valueOf(
          c.choiceDataGetModeOnInputByString));
    }
    p.setDefaultObjectForm(c.defaultObjectForm);
    p.setDefaultFolderForm(c.defaultFolderForm);
    p.setDefaultListForm(c.defaultListForm);
    p.setDefaultChoiceForm(c.defaultChoiceForm);
    p.setDefaultFolderChoiceForm(c.defaultFolderChoiceForm);
    p.setAuxiliaryObjectForm(c.auxiliaryObjectForm);
    p.setAuxiliaryFolderForm(c.auxiliaryFolderForm);
    p.setAuxiliaryListForm(c.auxiliaryListForm);
    p.setAuxiliaryChoiceForm(c.auxiliaryChoiceForm);
    p.setAuxiliaryFolderChoiceForm(c.auxiliaryFolderChoiceForm);
    p.setObjectModule(c.objectModule);
    p.setManagerModule(c.managerModule);
    p.setIncludeHelpInContents(c.includeHelpInContents);
    p.setHelp(c.help);
    MdListTypeRefs.replaceItemsV21(p.getBasedOn(), c.basedOn);
    if (p.getDataLockFields() != null) {
      p.getDataLockFields().getField().clear();
      if (c.dataLockFields != null) {
        p.getDataLockFields().getField().addAll(c.dataLockFields);
      }
    }
    if (c.dataLockControlMode != null && !c.dataLockControlMode.isEmpty()) {
      p.setDataLockControlMode(
        io.github.yellowhammer.designerxml.jaxb.v2_21.v8_3_xcf_enums.DefaultDataLockControlMode.valueOf(
          c.dataLockControlMode));
    }
    if (c.fullTextSearch != null && !c.fullTextSearch.isEmpty()) {
      p.setFullTextSearch(
        io.github.yellowhammer.designerxml.jaxb.v2_21.v8_3_xcf_enums.FullTextSearchUsing.valueOf(c.fullTextSearch));
    }
    ensureLsV21(p);
    LocalStringSync.setOrPutRuV21(p.getObjectPresentation(), c.objectPresentationRu == null ? "" : c.objectPresentationRu);
    LocalStringSync.setOrPutRuV21(
      p.getExtendedObjectPresentation(),
      c.extendedObjectPresentationRu == null ? "" : c.extendedObjectPresentationRu);
    LocalStringSync.setOrPutRuV21(p.getListPresentation(), c.listPresentationRu == null ? "" : c.listPresentationRu);
    LocalStringSync.setOrPutRuV21(
      p.getExtendedListPresentation(),
      c.extendedListPresentationRu == null ? "" : c.extendedListPresentationRu);
    LocalStringSync.setOrPutRuV21(p.getExplanation(), c.explanationRu == null ? "" : c.explanationRu);
    if (c.createOnInput != null && !c.createOnInput.isEmpty()) {
      p.setCreateOnInput(
        io.github.yellowhammer.designerxml.jaxb.v2_21.v8_3_xcf_enums.CreateOnInput.valueOf(c.createOnInput));
    }
    if (c.choiceHistoryOnInput != null && !c.choiceHistoryOnInput.isEmpty()) {
      p.setChoiceHistoryOnInput(
        io.github.yellowhammer.designerxml.jaxb.v2_21.v8_2_managed_application_logform.ChoiceHistoryOnInput.valueOf(
          c.choiceHistoryOnInput));
    }
    if (c.dataHistory != null && !c.dataHistory.isEmpty()) {
      p.setDataHistory(
        io.github.yellowhammer.designerxml.jaxb.v2_21.v8_3_xcf_enums.DataHistoryUse.valueOf(c.dataHistory));
    }
    p.setUpdateDataHistoryImmediatelyAfterWrite(c.updateDataHistoryImmediatelyAfterWrite);
    p.setExecuteAfterWriteDataHistoryVersionProcessing(c.executeAfterWriteDataHistoryVersionProcessing);
    p.setAdditionalIndexes(c.additionalIndexes);
  }

  private static void ensureLsV21(io.github.yellowhammer.designerxml.jaxb.v2_21.mdclasses.CatalogProperties p) {
    if (p.getObjectPresentation() == null) {
      p.setObjectPresentation(new io.github.yellowhammer.designerxml.jaxb.v2_21.v8_1_data_core.LocalStringType());
    }
    if (p.getExtendedObjectPresentation() == null) {
      p.setExtendedObjectPresentation(new io.github.yellowhammer.designerxml.jaxb.v2_21.v8_1_data_core.LocalStringType());
    }
    if (p.getListPresentation() == null) {
      p.setListPresentation(new io.github.yellowhammer.designerxml.jaxb.v2_21.v8_1_data_core.LocalStringType());
    }
    if (p.getExtendedListPresentation() == null) {
      p.setExtendedListPresentation(new io.github.yellowhammer.designerxml.jaxb.v2_21.v8_1_data_core.LocalStringType());
    }
    if (p.getExplanation() == null) {
      p.setExplanation(new io.github.yellowhammer.designerxml.jaxb.v2_21.v8_1_data_core.LocalStringType());
    }
  }

  public static void readV20(
    io.github.yellowhammer.designerxml.jaxb.v2_20.mdclasses.CatalogProperties p,
    MdObjectPropertiesDto dto) {
    MdCatalogPropertiesDto c = new MdCatalogPropertiesDto();
    if (p.getObjectBelonging() != null) {
      c.objectBelonging = p.getObjectBelonging().name();
    }
    c.extendedConfigurationObject = nullIfBlankUuid(p.getExtendedConfigurationObject());
    c.hierarchical = p.isHierarchical();
    if (p.getHierarchyType() != null) {
      c.hierarchyType = p.getHierarchyType().name();
    }
    c.limitLevelCount = p.isLimitLevelCount();
    c.levelCount = decimalToString(p.getLevelCount());
    c.foldersOnTop = p.isFoldersOnTop();
    c.useStandardCommands = p.isUseStandardCommands();
    if (p.getOwners() != null) {
      c.owners.addAll(MdListTypeRefs.readItemTexts(p.getOwners().getItem()));
    }
    if (p.getSubordinationUse() != null) {
      c.subordinationUse = p.getSubordinationUse().name();
    }
    c.codeLength = decimalToString(p.getCodeLength());
    c.descriptionLength = decimalToString(p.getDescriptionLength());
    if (p.getCodeType() != null) {
      c.codeType = p.getCodeType().name();
    }
    if (p.getCodeAllowedLength() != null) {
      c.codeAllowedLength = p.getCodeAllowedLength().name();
    }
    if (p.getCodeSeries() != null) {
      c.codeSeries = p.getCodeSeries().name();
    }
    c.checkUnique = p.isCheckUnique();
    c.autonumbering = p.isAutonumbering();
    if (p.getDefaultPresentation() != null) {
      c.defaultPresentation = p.getDefaultPresentation().name();
    }
    try {
      c.standardAttributesXml = MdCfCatalogSubtreeXml.marshalStandardAttributesV20(p.getStandardAttributes());
    } catch (JAXBException e) {
      c.standardAttributesXml = "";
    }
    try {
      c.characteristicsXml = MdCfCatalogSubtreeXml.marshalCharacteristicsV20(p.getCharacteristics());
    } catch (JAXBException e) {
      c.characteristicsXml = "";
    }
    c.predefined = p.getPredefined();
    if (p.getPredefinedDataUpdate() != null) {
      c.predefinedDataUpdate = p.getPredefinedDataUpdate().name();
    }
    if (p.getEditType() != null) {
      c.editType = p.getEditType().name();
    }
    c.quickChoice = p.isQuickChoice();
    if (p.getChoiceMode() != null) {
      c.choiceMode = p.getChoiceMode().name();
    }
    if (p.getInputByString() != null) {
      c.inputByString.addAll(p.getInputByString().getField());
    }
    if (p.getSearchStringModeOnInputByString() != null) {
      c.searchStringModeOnInputByString = p.getSearchStringModeOnInputByString().name();
    }
    if (p.getFullTextSearchOnInputByString() != null) {
      c.fullTextSearchOnInputByString = p.getFullTextSearchOnInputByString().name();
    }
    if (p.getChoiceDataGetModeOnInputByString() != null) {
      c.choiceDataGetModeOnInputByString = p.getChoiceDataGetModeOnInputByString().name();
    }
    c.defaultObjectForm = p.getDefaultObjectForm();
    c.defaultFolderForm = p.getDefaultFolderForm();
    c.defaultListForm = p.getDefaultListForm();
    c.defaultChoiceForm = p.getDefaultChoiceForm();
    c.defaultFolderChoiceForm = p.getDefaultFolderChoiceForm();
    c.auxiliaryObjectForm = p.getAuxiliaryObjectForm();
    c.auxiliaryFolderForm = p.getAuxiliaryFolderForm();
    c.auxiliaryListForm = p.getAuxiliaryListForm();
    c.auxiliaryChoiceForm = p.getAuxiliaryChoiceForm();
    c.auxiliaryFolderChoiceForm = p.getAuxiliaryFolderChoiceForm();
    c.objectModule = p.getObjectModule();
    c.managerModule = p.getManagerModule();
    c.includeHelpInContents = p.isIncludeHelpInContents();
    c.help = p.getHelp();
    if (p.getBasedOn() != null) {
      c.basedOn.addAll(MdListTypeRefs.readItemTexts(p.getBasedOn().getItem()));
    }
    if (p.getDataLockFields() != null) {
      c.dataLockFields.addAll(p.getDataLockFields().getField());
    }
    if (p.getDataLockControlMode() != null) {
      c.dataLockControlMode = p.getDataLockControlMode().name();
    }
    if (p.getFullTextSearch() != null) {
      c.fullTextSearch = p.getFullTextSearch().name();
    }
    c.objectPresentationRu = LocalStringSync.firstRuV20(p.getObjectPresentation());
    c.extendedObjectPresentationRu = LocalStringSync.firstRuV20(p.getExtendedObjectPresentation());
    c.listPresentationRu = LocalStringSync.firstRuV20(p.getListPresentation());
    c.extendedListPresentationRu = LocalStringSync.firstRuV20(p.getExtendedListPresentation());
    c.explanationRu = LocalStringSync.firstRuV20(p.getExplanation());
    if (p.getCreateOnInput() != null) {
      c.createOnInput = p.getCreateOnInput().name();
    }
    if (p.getChoiceHistoryOnInput() != null) {
      c.choiceHistoryOnInput = p.getChoiceHistoryOnInput().name();
    }
    if (p.getDataHistory() != null) {
      c.dataHistory = p.getDataHistory().name();
    }
    c.updateDataHistoryImmediatelyAfterWrite = p.isUpdateDataHistoryImmediatelyAfterWrite();
    c.executeAfterWriteDataHistoryVersionProcessing = p.isExecuteAfterWriteDataHistoryVersionProcessing();
    c.additionalIndexes = p.getAdditionalIndexes();
    dto.catalog = c;
  }

  public static void applyV20(
    io.github.yellowhammer.designerxml.jaxb.v2_20.mdclasses.CatalogProperties p,
    MdObjectPropertiesDto dto) {
    MdCatalogPropertiesDto c = dto.catalog;
    if (c == null) {
      throw new IllegalArgumentException("catalog required");
    }
    if (!dto.internalName.equals(p.getName())) {
      throw new IllegalArgumentException("internalName mismatch with XML");
    }
    String syn = dto.synonymRu == null ? "" : dto.synonymRu;
    LocalStringSync.setOrPutRuV20(p.getSynonym(), syn);
    p.setComment(dto.comment == null ? "" : dto.comment);
    if (c.objectBelonging != null && !c.objectBelonging.isEmpty()) {
      p.setObjectBelonging(
        io.github.yellowhammer.designerxml.jaxb.v2_20.v8_3_xcf_enums.ObjectBelonging.valueOf(c.objectBelonging));
    }
    p.setExtendedConfigurationObject(nullIfBlankUuid(c.extendedConfigurationObject));
    p.setHierarchical(c.hierarchical);
    if (c.hierarchyType != null && !c.hierarchyType.isEmpty()) {
      p.setHierarchyType(
        io.github.yellowhammer.designerxml.jaxb.v2_20.v8_3_xcf_enums.HierarchyType.valueOf(c.hierarchyType));
    }
    p.setLimitLevelCount(c.limitLevelCount);
    p.setLevelCount(new BigDecimal(nzDecimal(c.levelCount)));
    p.setFoldersOnTop(c.foldersOnTop);
    p.setUseStandardCommands(c.useStandardCommands);
    MdListTypeRefs.replaceItemsV20(p.getOwners(), c.owners);
    if (c.subordinationUse != null && !c.subordinationUse.isEmpty()) {
      p.setSubordinationUse(
        io.github.yellowhammer.designerxml.jaxb.v2_20.v8_3_xcf_enums.SubordinationUse.valueOf(c.subordinationUse));
    }
    p.setCodeLength(new BigDecimal(nzDecimal(c.codeLength)));
    p.setDescriptionLength(new BigDecimal(nzDecimal(c.descriptionLength)));
    if (c.codeType != null && !c.codeType.isEmpty()) {
      p.setCodeType(io.github.yellowhammer.designerxml.jaxb.v2_20.v8_3_xcf_enums.CatalogCodeType.valueOf(c.codeType));
    }
    if (c.codeAllowedLength != null && !c.codeAllowedLength.isEmpty()) {
      p.setCodeAllowedLength(
        io.github.yellowhammer.designerxml.jaxb.v2_20.v8_1_data_core.AllowedLength.valueOf(c.codeAllowedLength));
    }
    if (c.codeSeries != null && !c.codeSeries.isEmpty()) {
      p.setCodeSeries(
        io.github.yellowhammer.designerxml.jaxb.v2_20.v8_3_xcf_enums.CatalogCodesSeries.valueOf(c.codeSeries));
    }
    p.setCheckUnique(c.checkUnique);
    p.setAutonumbering(c.autonumbering);
    if (c.defaultPresentation != null && !c.defaultPresentation.isEmpty()) {
      p.setDefaultPresentation(
        io.github.yellowhammer.designerxml.jaxb.v2_20.v8_3_xcf_enums.CatalogMainPresentation.valueOf(
          c.defaultPresentation));
    }
    if (c.standardAttributesXml != null && !c.standardAttributesXml.isBlank()) {
      try {
        p.setStandardAttributes(MdCfCatalogSubtreeXml.unmarshalStandardAttributesV20(c.standardAttributesXml.trim()));
      } catch (JAXBException e) {
        throw new IllegalArgumentException("standardAttributesXml: " + e.getMessage(), e);
      }
    }
    if (c.characteristicsXml != null && !c.characteristicsXml.isBlank()) {
      try {
        p.setCharacteristics(MdCfCatalogSubtreeXml.unmarshalCharacteristicsV20(c.characteristicsXml.trim()));
      } catch (JAXBException e) {
        throw new IllegalArgumentException("characteristicsXml: " + e.getMessage(), e);
      }
    }
    p.setPredefined(c.predefined);
    if (c.predefinedDataUpdate != null && !c.predefinedDataUpdate.isEmpty()) {
      p.setPredefinedDataUpdate(
        io.github.yellowhammer.designerxml.jaxb.v2_20.v8_3_xcf_enums.PredefinedDataUpdate.valueOf(
          c.predefinedDataUpdate));
    }
    if (c.editType != null && !c.editType.isEmpty()) {
      p.setEditType(io.github.yellowhammer.designerxml.jaxb.v2_20.v8_3_xcf_enums.EditType.valueOf(c.editType));
    }
    p.setQuickChoice(c.quickChoice);
    if (c.choiceMode != null && !c.choiceMode.isEmpty()) {
      p.setChoiceMode(io.github.yellowhammer.designerxml.jaxb.v2_20.v8_3_xcf_enums.ChoiceMode.valueOf(c.choiceMode));
    }
    if (p.getInputByString() != null) {
      p.getInputByString().getField().clear();
      if (c.inputByString != null) {
        p.getInputByString().getField().addAll(c.inputByString);
      }
    }
    if (c.searchStringModeOnInputByString != null && !c.searchStringModeOnInputByString.isEmpty()) {
      p.setSearchStringModeOnInputByString(
        io.github.yellowhammer.designerxml.jaxb.v2_20.v8_2_managed_application_logform.SearchStringModeOnInputByString.valueOf(
          c.searchStringModeOnInputByString));
    }
    if (c.fullTextSearchOnInputByString != null && !c.fullTextSearchOnInputByString.isEmpty()) {
      p.setFullTextSearchOnInputByString(
        io.github.yellowhammer.designerxml.jaxb.v2_20.v8_2_managed_application_logform.FullTextSearchOnInputByString.valueOf(
          c.fullTextSearchOnInputByString));
    }
    if (c.choiceDataGetModeOnInputByString != null && !c.choiceDataGetModeOnInputByString.isEmpty()) {
      p.setChoiceDataGetModeOnInputByString(
        io.github.yellowhammer.designerxml.jaxb.v2_20.v8_2_managed_application_logform.ChoiceDataGetModeOnInputByString.valueOf(
          c.choiceDataGetModeOnInputByString));
    }
    p.setDefaultObjectForm(c.defaultObjectForm);
    p.setDefaultFolderForm(c.defaultFolderForm);
    p.setDefaultListForm(c.defaultListForm);
    p.setDefaultChoiceForm(c.defaultChoiceForm);
    p.setDefaultFolderChoiceForm(c.defaultFolderChoiceForm);
    p.setAuxiliaryObjectForm(c.auxiliaryObjectForm);
    p.setAuxiliaryFolderForm(c.auxiliaryFolderForm);
    p.setAuxiliaryListForm(c.auxiliaryListForm);
    p.setAuxiliaryChoiceForm(c.auxiliaryChoiceForm);
    p.setAuxiliaryFolderChoiceForm(c.auxiliaryFolderChoiceForm);
    p.setObjectModule(c.objectModule);
    p.setManagerModule(c.managerModule);
    p.setIncludeHelpInContents(c.includeHelpInContents);
    p.setHelp(c.help);
    MdListTypeRefs.replaceItemsV20(p.getBasedOn(), c.basedOn);
    if (p.getDataLockFields() != null) {
      p.getDataLockFields().getField().clear();
      if (c.dataLockFields != null) {
        p.getDataLockFields().getField().addAll(c.dataLockFields);
      }
    }
    if (c.dataLockControlMode != null && !c.dataLockControlMode.isEmpty()) {
      p.setDataLockControlMode(
        io.github.yellowhammer.designerxml.jaxb.v2_20.v8_3_xcf_enums.DefaultDataLockControlMode.valueOf(
          c.dataLockControlMode));
    }
    if (c.fullTextSearch != null && !c.fullTextSearch.isEmpty()) {
      p.setFullTextSearch(
        io.github.yellowhammer.designerxml.jaxb.v2_20.v8_3_xcf_enums.FullTextSearchUsing.valueOf(c.fullTextSearch));
    }
    ensureLsV20(p);
    LocalStringSync.setOrPutRuV20(p.getObjectPresentation(), c.objectPresentationRu == null ? "" : c.objectPresentationRu);
    LocalStringSync.setOrPutRuV20(
      p.getExtendedObjectPresentation(),
      c.extendedObjectPresentationRu == null ? "" : c.extendedObjectPresentationRu);
    LocalStringSync.setOrPutRuV20(p.getListPresentation(), c.listPresentationRu == null ? "" : c.listPresentationRu);
    LocalStringSync.setOrPutRuV20(
      p.getExtendedListPresentation(),
      c.extendedListPresentationRu == null ? "" : c.extendedListPresentationRu);
    LocalStringSync.setOrPutRuV20(p.getExplanation(), c.explanationRu == null ? "" : c.explanationRu);
    if (c.createOnInput != null && !c.createOnInput.isEmpty()) {
      p.setCreateOnInput(
        io.github.yellowhammer.designerxml.jaxb.v2_20.v8_3_xcf_enums.CreateOnInput.valueOf(c.createOnInput));
    }
    if (c.choiceHistoryOnInput != null && !c.choiceHistoryOnInput.isEmpty()) {
      p.setChoiceHistoryOnInput(
        io.github.yellowhammer.designerxml.jaxb.v2_20.v8_2_managed_application_logform.ChoiceHistoryOnInput.valueOf(
          c.choiceHistoryOnInput));
    }
    if (c.dataHistory != null && !c.dataHistory.isEmpty()) {
      p.setDataHistory(
        io.github.yellowhammer.designerxml.jaxb.v2_20.v8_3_xcf_enums.DataHistoryUse.valueOf(c.dataHistory));
    }
    p.setUpdateDataHistoryImmediatelyAfterWrite(c.updateDataHistoryImmediatelyAfterWrite);
    p.setExecuteAfterWriteDataHistoryVersionProcessing(c.executeAfterWriteDataHistoryVersionProcessing);
    p.setAdditionalIndexes(c.additionalIndexes);
  }

  private static void ensureLsV20(io.github.yellowhammer.designerxml.jaxb.v2_20.mdclasses.CatalogProperties p) {
    if (p.getObjectPresentation() == null) {
      p.setObjectPresentation(new io.github.yellowhammer.designerxml.jaxb.v2_20.v8_1_data_core.LocalStringType());
    }
    if (p.getExtendedObjectPresentation() == null) {
      p.setExtendedObjectPresentation(new io.github.yellowhammer.designerxml.jaxb.v2_20.v8_1_data_core.LocalStringType());
    }
    if (p.getListPresentation() == null) {
      p.setListPresentation(new io.github.yellowhammer.designerxml.jaxb.v2_20.v8_1_data_core.LocalStringType());
    }
    if (p.getExtendedListPresentation() == null) {
      p.setExtendedListPresentation(new io.github.yellowhammer.designerxml.jaxb.v2_20.v8_1_data_core.LocalStringType());
    }
    if (p.getExplanation() == null) {
      p.setExplanation(new io.github.yellowhammer.designerxml.jaxb.v2_20.v8_1_data_core.LocalStringType());
    }
  }

  private static String decimalToString(BigDecimal bd) {
    return bd == null ? "0" : bd.toPlainString();
  }

  private static String nzDecimal(String s) {
    if (s == null || s.isBlank()) {
      return "0";
    }
    return s.trim();
  }
}
