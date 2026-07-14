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
 * Чтение и запись {@code DocumentProperties} через JAXB-рефлексию (гранулярный DTO документа).
 */
public final class MdDocumentPropertiesBridge {

  private MdDocumentPropertiesBridge() {
  }

  /**
   * Читает {@code DocumentProperties} ({@code p}, любой версии) в {@code dto.document}.
   */
  public static void read(SchemaVersion version, Object p, MdObjectPropertiesDto dto) {
    MdDocumentPropertiesDto d = new MdDocumentPropertiesDto();
    d.objectBelonging = en(p, "getObjectBelonging");
    d.extendedConfigurationObject = nullIfBlankUuid(JaxbReflect.getStringOptional(p, "getExtendedConfigurationObject"));
    d.useStandardCommands = JaxbReflect.getBooleanOptional(p, "isUseStandardCommands");
    d.numerator = JaxbReflect.getStringOptional(p, "getNumerator");
    d.numberType = en(p, "getNumberType");
    d.numberLength = dec(p, "getNumberLength");
    d.numberAllowedLength = en(p, "getNumberAllowedLength");
    d.numberPeriodicity = en(p, "getNumberPeriodicity");
    d.checkUnique = JaxbReflect.getBooleanOptional(p, "isCheckUnique");
    d.autonumbering = JaxbReflect.getBooleanOptional(p, "isAutonumbering");
    d.standardAttributesXml = tryMarshalStandardAttributes(version, JaxbReflect.getOptional(p, "getStandardAttributes"));
    d.characteristicsXml = tryMarshalCharacteristics(version, JaxbReflect.getOptional(p, "getCharacteristics"));
    addItems(d.basedOn, JaxbReflect.getOptional(p, "getBasedOn"));
    addFields(d.inputByString, JaxbReflect.getOptional(p, "getInputByString"));
    d.createOnInput = en(p, "getCreateOnInput");
    d.searchStringModeOnInputByString = en(p, "getSearchStringModeOnInputByString");
    d.fullTextSearchOnInputByString = en(p, "getFullTextSearchOnInputByString");
    d.choiceDataGetModeOnInputByString = en(p, "getChoiceDataGetModeOnInputByString");
    d.choiceHistoryOnInput = en(p, "getChoiceHistoryOnInput");
    d.defaultObjectForm = JaxbReflect.getStringOptional(p, "getDefaultObjectForm");
    d.defaultListForm = JaxbReflect.getStringOptional(p, "getDefaultListForm");
    d.defaultChoiceForm = JaxbReflect.getStringOptional(p, "getDefaultChoiceForm");
    d.auxiliaryObjectForm = JaxbReflect.getStringOptional(p, "getAuxiliaryObjectForm");
    d.auxiliaryListForm = JaxbReflect.getStringOptional(p, "getAuxiliaryListForm");
    d.auxiliaryChoiceForm = JaxbReflect.getStringOptional(p, "getAuxiliaryChoiceForm");
    d.objectModule = JaxbReflect.getStringOptional(p, "getObjectModule");
    d.managerModule = JaxbReflect.getStringOptional(p, "getManagerModule");
    d.posting = en(p, "getPosting");
    d.realTimePosting = en(p, "getRealTimePosting");
    d.registerRecordsDeletion = en(p, "getRegisterRecordsDeletion");
    d.registerRecordsWritingOnPost = en(p, "getRegisterRecordsWritingOnPost");
    d.sequenceFilling = en(p, "getSequenceFilling");
    addItems(d.registerRecords, JaxbReflect.getOptional(p, "getRegisterRecords"));
    d.postInPrivilegedMode = JaxbReflect.getBooleanOptional(p, "isPostInPrivilegedMode");
    d.unpostInPrivilegedMode = JaxbReflect.getBooleanOptional(p, "isUnpostInPrivilegedMode");
    d.includeHelpInContents = JaxbReflect.getBooleanOptional(p, "isIncludeHelpInContents");
    d.help = JaxbReflect.getStringOptional(p, "getHelp");
    addFields(d.dataLockFields, JaxbReflect.getOptional(p, "getDataLockFields"));
    d.dataLockControlMode = en(p, "getDataLockControlMode");
    d.fullTextSearch = en(p, "getFullTextSearch");
    d.objectPresentationRu = LocalStringSync.firstRu(JaxbReflect.getOptional(p, "getObjectPresentation"));
    d.extendedObjectPresentationRu = LocalStringSync.firstRu(JaxbReflect.getOptional(p, "getExtendedObjectPresentation"));
    d.listPresentationRu = LocalStringSync.firstRu(JaxbReflect.getOptional(p, "getListPresentation"));
    d.extendedListPresentationRu = LocalStringSync.firstRu(JaxbReflect.getOptional(p, "getExtendedListPresentation"));
    d.explanationRu = LocalStringSync.firstRu(JaxbReflect.getOptional(p, "getExplanation"));
    d.dataHistory = en(p, "getDataHistory");
    d.updateDataHistoryImmediatelyAfterWrite = JaxbReflect.getBooleanOptional(p, "isUpdateDataHistoryImmediatelyAfterWrite");
    d.executeAfterWriteDataHistoryVersionProcessing =
      JaxbReflect.getBooleanOptional(p, "isExecuteAfterWriteDataHistoryVersionProcessing");
    d.additionalIndexes = JaxbReflect.getStringOptional(p, "getAdditionalIndexes");
    dto.document = d;
  }

  /**
   * Применяет {@code dto.document} к {@code DocumentProperties} ({@code p}, любой версии).
   */
  public static void apply(SchemaVersion version, Object p, MdObjectPropertiesDto dto) {
    MdDocumentPropertiesDto d = dto.document;
    if (d == null) {
      throw new IllegalArgumentException("document required");
    }
    if (!dto.internalName.equals(JaxbReflect.getStringOptional(p, "getName"))) {
      throw new IllegalArgumentException("internalName mismatch with XML");
    }
    String syn = dto.synonymRu == null ? "" : dto.synonymRu;
    LocalStringSync.setOrPutRu(JaxbReflect.getOptional(p, "getSynonym"), syn);
    JaxbReflect.setOptional(p, "setComment", dto.comment == null ? "" : dto.comment);
    JaxbReflect.setEnumOrKeep(p, "setObjectBelonging", d.objectBelonging);
    JaxbReflect.setOptional(p, "setExtendedConfigurationObject", nullIfBlankUuid(d.extendedConfigurationObject));
    JaxbReflect.setOptional(p, "setUseStandardCommands", d.useStandardCommands);
    JaxbReflect.setOptional(p, "setNumerator", d.numerator);
    JaxbReflect.setEnumOrKeep(p, "setNumberType", d.numberType);
    JaxbReflect.setOptional(p, "setNumberLength", new BigDecimal(nzDecimal(d.numberLength)));
    JaxbReflect.setEnumOrKeep(p, "setNumberAllowedLength", d.numberAllowedLength);
    JaxbReflect.setEnumOrKeep(p, "setNumberPeriodicity", d.numberPeriodicity);
    JaxbReflect.setOptional(p, "setCheckUnique", d.checkUnique);
    JaxbReflect.setOptional(p, "setAutonumbering", d.autonumbering);
    if (d.standardAttributesXml != null && !d.standardAttributesXml.isBlank()) {
      try {
        JaxbReflect.setOptional(p, "setStandardAttributes",
          MdCfCatalogSubtreeXml.unmarshalStandardAttributes(version, d.standardAttributesXml.trim()));
      } catch (JAXBException e) {
        throw new IllegalArgumentException("standardAttributesXml: " + e.getMessage(), e);
      }
    }
    if (d.characteristicsXml != null && !d.characteristicsXml.isBlank()) {
      try {
        JaxbReflect.setOptional(p, "setCharacteristics",
          MdCfCatalogSubtreeXml.unmarshalCharacteristics(version, d.characteristicsXml.trim()));
      } catch (JAXBException e) {
        throw new IllegalArgumentException("characteristicsXml: " + e.getMessage(), e);
      }
    }
    MdListTypeRefs.replaceItems(JaxbReflect.getOptional(p, "getBasedOn"), d.basedOn);
    setFields(JaxbReflect.getOptional(p, "getInputByString"), d.inputByString);
    JaxbReflect.setEnumOrKeep(p, "setCreateOnInput", d.createOnInput);
    JaxbReflect.setEnumOrKeep(p, "setSearchStringModeOnInputByString", d.searchStringModeOnInputByString);
    JaxbReflect.setEnumOrKeep(p, "setFullTextSearchOnInputByString", d.fullTextSearchOnInputByString);
    JaxbReflect.setEnumOrKeep(p, "setChoiceDataGetModeOnInputByString", d.choiceDataGetModeOnInputByString);
    JaxbReflect.setEnumOrKeep(p, "setChoiceHistoryOnInput", d.choiceHistoryOnInput);
    JaxbReflect.setOptional(p, "setDefaultObjectForm", d.defaultObjectForm);
    JaxbReflect.setOptional(p, "setDefaultListForm", d.defaultListForm);
    JaxbReflect.setOptional(p, "setDefaultChoiceForm", d.defaultChoiceForm);
    JaxbReflect.setOptional(p, "setAuxiliaryObjectForm", d.auxiliaryObjectForm);
    JaxbReflect.setOptional(p, "setAuxiliaryListForm", d.auxiliaryListForm);
    JaxbReflect.setOptional(p, "setAuxiliaryChoiceForm", d.auxiliaryChoiceForm);
    JaxbReflect.setOptional(p, "setObjectModule", d.objectModule);
    JaxbReflect.setOptional(p, "setManagerModule", d.managerModule);
    JaxbReflect.setEnumOrKeep(p, "setPosting", d.posting);
    JaxbReflect.setEnumOrKeep(p, "setRealTimePosting", d.realTimePosting);
    JaxbReflect.setEnumOrKeep(p, "setRegisterRecordsDeletion", d.registerRecordsDeletion);
    JaxbReflect.setEnumOrKeep(p, "setRegisterRecordsWritingOnPost", d.registerRecordsWritingOnPost);
    JaxbReflect.setEnumOrKeep(p, "setSequenceFilling", d.sequenceFilling);
    MdListTypeRefs.replaceItems(JaxbReflect.getOptional(p, "getRegisterRecords"), d.registerRecords);
    JaxbReflect.setOptional(p, "setPostInPrivilegedMode", d.postInPrivilegedMode);
    JaxbReflect.setOptional(p, "setUnpostInPrivilegedMode", d.unpostInPrivilegedMode);
    JaxbReflect.setOptional(p, "setIncludeHelpInContents", d.includeHelpInContents);
    JaxbReflect.setOptional(p, "setHelp", d.help);
    setFields(JaxbReflect.getOptional(p, "getDataLockFields"), d.dataLockFields);
    JaxbReflect.setEnumOrKeep(p, "setDataLockControlMode", d.dataLockControlMode);
    JaxbReflect.setEnumOrKeep(p, "setFullTextSearch", d.fullTextSearch);
    ensureAndSetRu(p, "getObjectPresentation", "setObjectPresentation", d.objectPresentationRu);
    ensureAndSetRu(p, "getExtendedObjectPresentation", "setExtendedObjectPresentation", d.extendedObjectPresentationRu);
    ensureAndSetRu(p, "getListPresentation", "setListPresentation", d.listPresentationRu);
    ensureAndSetRu(p, "getExtendedListPresentation", "setExtendedListPresentation", d.extendedListPresentationRu);
    ensureAndSetRu(p, "getExplanation", "setExplanation", d.explanationRu);
    JaxbReflect.setEnumOrKeep(p, "setDataHistory", d.dataHistory);
    JaxbReflect.setOptional(p, "setUpdateDataHistoryImmediatelyAfterWrite", d.updateDataHistoryImmediatelyAfterWrite);
    JaxbReflect.setOptional(p, "setExecuteAfterWriteDataHistoryVersionProcessing",
      d.executeAfterWriteDataHistoryVersionProcessing);
    JaxbReflect.setOptional(p, "setAdditionalIndexes", d.additionalIndexes);
  }

  private static void ensureAndSetRu(Object p, String getter, String setter, String ru) {
    Object ls = JaxbReflect.ensureOptional(p, getter, setter);
    LocalStringSync.setOrPutRu(ls, ru == null ? "" : ru);
  }

  private static String nullIfBlankUuid(String s) {
    if (s == null || s.isBlank()) {
      return null;
    }
    return s;
  }

  private static String nzDecimal(String s) {
    return s == null || s.isBlank() ? "0" : s;
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
}
