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

import jakarta.xml.bind.JAXBException;

import java.math.BigDecimal;
import java.util.List;

/**
 * Заполнение {@link MdCatalogPropertiesDto} из JAXB и обратная запись в {@code CatalogProperties} —
 * версионно-нейтрально через {@link JaxbReflect} (структура свойств одинакова во всех версиях схем).
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

  /**
   * Читает {@code CatalogProperties} ({@code p}, любой версии) в {@code dto.catalog}.
   */
  public static void read(SchemaVersion version, Object p, MdObjectPropertiesDto dto) {
    MdCatalogPropertiesDto c = new MdCatalogPropertiesDto();
    c.objectBelonging = en(p, "getObjectBelonging");
    c.extendedConfigurationObject = nullIfBlankUuid(JaxbReflect.getStringOptional(p, "getExtendedConfigurationObject"));
    c.hierarchical = JaxbReflect.getBooleanOptional(p, "isHierarchical");
    c.hierarchyType = en(p, "getHierarchyType");
    c.limitLevelCount = JaxbReflect.getBooleanOptional(p, "isLimitLevelCount");
    c.levelCount = dec(p, "getLevelCount");
    c.foldersOnTop = JaxbReflect.getBooleanOptional(p, "isFoldersOnTop");
    c.useStandardCommands = JaxbReflect.getBooleanOptional(p, "isUseStandardCommands");
    addItems(c.owners, JaxbReflect.getOptional(p, "getOwners"));
    c.subordinationUse = en(p, "getSubordinationUse");
    c.codeLength = dec(p, "getCodeLength");
    c.descriptionLength = dec(p, "getDescriptionLength");
    c.codeType = en(p, "getCodeType");
    c.codeAllowedLength = en(p, "getCodeAllowedLength");
    c.codeSeries = en(p, "getCodeSeries");
    c.checkUnique = JaxbReflect.getBooleanOptional(p, "isCheckUnique");
    c.autonumbering = JaxbReflect.getBooleanOptional(p, "isAutonumbering");
    c.defaultPresentation = en(p, "getDefaultPresentation");
    c.standardAttributesXml = tryMarshalStandardAttributes(version, JaxbReflect.getOptional(p, "getStandardAttributes"));
    c.characteristicsXml = tryMarshalCharacteristics(version, JaxbReflect.getOptional(p, "getCharacteristics"));
    c.predefined = JaxbReflect.getStringOptional(p, "getPredefined");
    c.predefinedDataUpdate = en(p, "getPredefinedDataUpdate");
    c.editType = en(p, "getEditType");
    c.quickChoice = JaxbReflect.getBooleanOptional(p, "isQuickChoice");
    c.choiceMode = en(p, "getChoiceMode");
    addFields(c.inputByString, JaxbReflect.getOptional(p, "getInputByString"));
    c.searchStringModeOnInputByString = en(p, "getSearchStringModeOnInputByString");
    c.fullTextSearchOnInputByString = en(p, "getFullTextSearchOnInputByString");
    c.choiceDataGetModeOnInputByString = en(p, "getChoiceDataGetModeOnInputByString");
    c.defaultObjectForm = JaxbReflect.getStringOptional(p, "getDefaultObjectForm");
    c.defaultFolderForm = JaxbReflect.getStringOptional(p, "getDefaultFolderForm");
    c.defaultListForm = JaxbReflect.getStringOptional(p, "getDefaultListForm");
    c.defaultChoiceForm = JaxbReflect.getStringOptional(p, "getDefaultChoiceForm");
    c.defaultFolderChoiceForm = JaxbReflect.getStringOptional(p, "getDefaultFolderChoiceForm");
    c.auxiliaryObjectForm = JaxbReflect.getStringOptional(p, "getAuxiliaryObjectForm");
    c.auxiliaryFolderForm = JaxbReflect.getStringOptional(p, "getAuxiliaryFolderForm");
    c.auxiliaryListForm = JaxbReflect.getStringOptional(p, "getAuxiliaryListForm");
    c.auxiliaryChoiceForm = JaxbReflect.getStringOptional(p, "getAuxiliaryChoiceForm");
    c.auxiliaryFolderChoiceForm = JaxbReflect.getStringOptional(p, "getAuxiliaryFolderChoiceForm");
    c.objectModule = JaxbReflect.getStringOptional(p, "getObjectModule");
    c.managerModule = JaxbReflect.getStringOptional(p, "getManagerModule");
    c.includeHelpInContents = JaxbReflect.getBooleanOptional(p, "isIncludeHelpInContents");
    c.help = JaxbReflect.getStringOptional(p, "getHelp");
    addItems(c.basedOn, JaxbReflect.getOptional(p, "getBasedOn"));
    addFields(c.dataLockFields, JaxbReflect.getOptional(p, "getDataLockFields"));
    c.dataLockControlMode = en(p, "getDataLockControlMode");
    c.fullTextSearch = en(p, "getFullTextSearch");
    c.objectPresentationRu = LocalStringSync.firstRu(JaxbReflect.getOptional(p, "getObjectPresentation"));
    c.extendedObjectPresentationRu = LocalStringSync.firstRu(JaxbReflect.getOptional(p, "getExtendedObjectPresentation"));
    c.listPresentationRu = LocalStringSync.firstRu(JaxbReflect.getOptional(p, "getListPresentation"));
    c.extendedListPresentationRu = LocalStringSync.firstRu(JaxbReflect.getOptional(p, "getExtendedListPresentation"));
    c.explanationRu = LocalStringSync.firstRu(JaxbReflect.getOptional(p, "getExplanation"));
    c.createOnInput = en(p, "getCreateOnInput");
    c.choiceHistoryOnInput = en(p, "getChoiceHistoryOnInput");
    c.dataHistory = en(p, "getDataHistory");
    c.updateDataHistoryImmediatelyAfterWrite = JaxbReflect.getBooleanOptional(p, "isUpdateDataHistoryImmediatelyAfterWrite");
    c.executeAfterWriteDataHistoryVersionProcessing =
      JaxbReflect.getBooleanOptional(p, "isExecuteAfterWriteDataHistoryVersionProcessing");
    c.additionalIndexes = JaxbReflect.getStringOptional(p, "getAdditionalIndexes");
    dto.catalog = c;
  }

  /**
   * Применяет {@code dto.catalog} к {@code CatalogProperties} ({@code p}, любой версии).
   */
  public static void apply(SchemaVersion version, Object p, MdObjectPropertiesDto dto) {
    MdCatalogPropertiesDto c = dto.catalog;
    if (c == null) {
      throw new IllegalArgumentException("catalog required");
    }
    if (!dto.internalName.equals(JaxbReflect.getStringOptional(p, "getName"))) {
      throw new IllegalArgumentException("internalName mismatch with XML");
    }
    String syn = dto.synonymRu == null ? "" : dto.synonymRu;
    LocalStringSync.setOrPutRu(JaxbReflect.getOptional(p, "getSynonym"), syn);
    JaxbReflect.setOptional(p, "setComment", dto.comment == null ? "" : dto.comment);
    JaxbReflect.setEnumOrKeep(p, "setObjectBelonging", c.objectBelonging);
    JaxbReflect.setOptional(p, "setExtendedConfigurationObject", nullIfBlankUuid(c.extendedConfigurationObject));
    JaxbReflect.setOptional(p, "setHierarchical", c.hierarchical);
    JaxbReflect.setEnumOrKeep(p, "setHierarchyType", c.hierarchyType);
    JaxbReflect.setOptional(p, "setLimitLevelCount", c.limitLevelCount);
    JaxbReflect.setOptional(p, "setLevelCount", new BigDecimal(nzDecimal(c.levelCount)));
    JaxbReflect.setOptional(p, "setFoldersOnTop", c.foldersOnTop);
    JaxbReflect.setOptional(p, "setUseStandardCommands", c.useStandardCommands);
    MdListTypeRefs.replaceItems(JaxbReflect.getOptional(p, "getOwners"), c.owners);
    JaxbReflect.setEnumOrKeep(p, "setSubordinationUse", c.subordinationUse);
    JaxbReflect.setOptional(p, "setCodeLength", new BigDecimal(nzDecimal(c.codeLength)));
    JaxbReflect.setOptional(p, "setDescriptionLength", new BigDecimal(nzDecimal(c.descriptionLength)));
    JaxbReflect.setEnumOrKeep(p, "setCodeType", c.codeType);
    JaxbReflect.setEnumOrKeep(p, "setCodeAllowedLength", c.codeAllowedLength);
    JaxbReflect.setEnumOrKeep(p, "setCodeSeries", c.codeSeries);
    JaxbReflect.setOptional(p, "setCheckUnique", c.checkUnique);
    JaxbReflect.setOptional(p, "setAutonumbering", c.autonumbering);
    JaxbReflect.setEnumOrKeep(p, "setDefaultPresentation", c.defaultPresentation);
    if (c.standardAttributesXml != null && !c.standardAttributesXml.isBlank()) {
      try {
        JaxbReflect.setOptional(p, "setStandardAttributes",
          MdCfCatalogSubtreeXml.unmarshalStandardAttributes(version, c.standardAttributesXml.trim()));
      } catch (JAXBException e) {
        throw new IllegalArgumentException("standardAttributesXml: " + e.getMessage(), e);
      }
    }
    if (c.characteristicsXml != null && !c.characteristicsXml.isBlank()) {
      try {
        JaxbReflect.setOptional(p, "setCharacteristics",
          MdCfCatalogSubtreeXml.unmarshalCharacteristics(version, c.characteristicsXml.trim()));
      } catch (JAXBException e) {
        throw new IllegalArgumentException("characteristicsXml: " + e.getMessage(), e);
      }
    }
    JaxbReflect.setOptional(p, "setPredefined", c.predefined);
    JaxbReflect.setEnumOrKeep(p, "setPredefinedDataUpdate", c.predefinedDataUpdate);
    JaxbReflect.setEnumOrKeep(p, "setEditType", c.editType);
    JaxbReflect.setOptional(p, "setQuickChoice", c.quickChoice);
    JaxbReflect.setEnumOrKeep(p, "setChoiceMode", c.choiceMode);
    setFields(JaxbReflect.getOptional(p, "getInputByString"), c.inputByString);
    JaxbReflect.setEnumOrKeep(p, "setSearchStringModeOnInputByString", c.searchStringModeOnInputByString);
    JaxbReflect.setEnumOrKeep(p, "setFullTextSearchOnInputByString", c.fullTextSearchOnInputByString);
    JaxbReflect.setEnumOrKeep(p, "setChoiceDataGetModeOnInputByString", c.choiceDataGetModeOnInputByString);
    JaxbReflect.setOptional(p, "setDefaultObjectForm", c.defaultObjectForm);
    JaxbReflect.setOptional(p, "setDefaultFolderForm", c.defaultFolderForm);
    JaxbReflect.setOptional(p, "setDefaultListForm", c.defaultListForm);
    JaxbReflect.setOptional(p, "setDefaultChoiceForm", c.defaultChoiceForm);
    JaxbReflect.setOptional(p, "setDefaultFolderChoiceForm", c.defaultFolderChoiceForm);
    JaxbReflect.setOptional(p, "setAuxiliaryObjectForm", c.auxiliaryObjectForm);
    JaxbReflect.setOptional(p, "setAuxiliaryFolderForm", c.auxiliaryFolderForm);
    JaxbReflect.setOptional(p, "setAuxiliaryListForm", c.auxiliaryListForm);
    JaxbReflect.setOptional(p, "setAuxiliaryChoiceForm", c.auxiliaryChoiceForm);
    JaxbReflect.setOptional(p, "setAuxiliaryFolderChoiceForm", c.auxiliaryFolderChoiceForm);
    JaxbReflect.setOptional(p, "setObjectModule", c.objectModule);
    JaxbReflect.setOptional(p, "setManagerModule", c.managerModule);
    JaxbReflect.setOptional(p, "setIncludeHelpInContents", c.includeHelpInContents);
    JaxbReflect.setOptional(p, "setHelp", c.help);
    MdListTypeRefs.replaceItems(JaxbReflect.getOptional(p, "getBasedOn"), c.basedOn);
    setFields(JaxbReflect.getOptional(p, "getDataLockFields"), c.dataLockFields);
    JaxbReflect.setEnumOrKeep(p, "setDataLockControlMode", c.dataLockControlMode);
    JaxbReflect.setEnumOrKeep(p, "setFullTextSearch", c.fullTextSearch);
    ensureAndSetRu(p, "getObjectPresentation", "setObjectPresentation", c.objectPresentationRu);
    ensureAndSetRu(p, "getExtendedObjectPresentation", "setExtendedObjectPresentation", c.extendedObjectPresentationRu);
    ensureAndSetRu(p, "getListPresentation", "setListPresentation", c.listPresentationRu);
    ensureAndSetRu(p, "getExtendedListPresentation", "setExtendedListPresentation", c.extendedListPresentationRu);
    ensureAndSetRu(p, "getExplanation", "setExplanation", c.explanationRu);
    JaxbReflect.setEnumOrKeep(p, "setCreateOnInput", c.createOnInput);
    JaxbReflect.setEnumOrKeep(p, "setChoiceHistoryOnInput", c.choiceHistoryOnInput);
    JaxbReflect.setEnumOrKeep(p, "setDataHistory", c.dataHistory);
    JaxbReflect.setOptional(p, "setUpdateDataHistoryImmediatelyAfterWrite", c.updateDataHistoryImmediatelyAfterWrite);
    JaxbReflect.setOptional(p, "setExecuteAfterWriteDataHistoryVersionProcessing",
      c.executeAfterWriteDataHistoryVersionProcessing);
    JaxbReflect.setOptional(p, "setAdditionalIndexes", c.additionalIndexes);
  }

  private static void ensureAndSetRu(Object p, String getter, String setter, String ru) {
    Object ls = JaxbReflect.ensureOptional(p, getter, setter);
    LocalStringSync.setOrPutRu(ls, ru == null ? "" : ru);
  }

  private static String en(Object p, String getter) {
    Object v = JaxbReflect.getOptional(p, getter);
    return v == null ? null : ((Enum<?>) v).name();
  }

  private static String dec(Object p, String getter) {
    Object v = JaxbReflect.getOptional(p, getter);
    return v == null ? "0" : ((BigDecimal) v).toPlainString();
  }

  private static void addItems(List<String> out, Object mdListType) {
    if (mdListType != null) {
      out.addAll(MdListTypeRefs.readItemTexts(JaxbReflect.list(mdListType, "getItem")));
    }
  }

  private static void addFields(List<String> out, Object fieldList) {
    if (fieldList != null) {
      out.addAll(JaxbReflect.<String>list(fieldList, "getField"));
    }
  }

  private static void setFields(Object fieldList, List<String> values) {
    if (fieldList == null) {
      return;
    }
    List<Object> field = JaxbReflect.list(fieldList, "getField");
    field.clear();
    if (values != null) {
      field.addAll(values);
    }
  }

  private static String tryMarshalStandardAttributes(SchemaVersion version, Object value) {
    try {
      return MdCfCatalogSubtreeXml.marshalStandardAttributes(version, value);
    } catch (JAXBException e) {
      return "";
    }
  }

  private static String tryMarshalCharacteristics(SchemaVersion version, Object value) {
    try {
      return MdCfCatalogSubtreeXml.marshalCharacteristics(version, value);
    } catch (JAXBException e) {
      return "";
    }
  }

  private static String nzDecimal(String s) {
    if (s == null || s.isBlank()) {
      return "0";
    }
    return s.trim();
  }
}
