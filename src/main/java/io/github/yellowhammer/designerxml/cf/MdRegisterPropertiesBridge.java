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

import static io.github.yellowhammer.designerxml.cf.MdPropertiesBridgeSupport.enumName;
import static io.github.yellowhammer.designerxml.cf.MdPropertiesBridgeSupport.ensureAndSetRu;
import static io.github.yellowhammer.designerxml.cf.MdPropertiesBridgeSupport.nullIfBlank;

/**
 * Чтение и запись свойств регистров сведений и накопления через JAXB-рефлексию.
 *
 * <p>Поля, которых нет у вида регистра, {@code JaxbReflect} молча пропускает: у регистра
 * накопления нет периодичности, у регистра сведений — вида регистра.
 */
public final class MdRegisterPropertiesBridge {

  private MdRegisterPropertiesBridge() {
  }

  public static void read(SchemaVersion version, Object p, MdObjectPropertiesDto dto) {
    MdRegisterPropertiesDto d = new MdRegisterPropertiesDto();
    d.objectBelonging = enumName(p, "getObjectBelonging");
    d.extendedConfigurationObject = nullIfBlank(JaxbReflect.getStringOptional(p, "getExtendedConfigurationObject"));
    d.useStandardCommands = JaxbReflect.getBooleanOptional(p, "isUseStandardCommands");
    d.standardAttributesXml = MdPropertiesBridgeSupport.marshalStandardAttributesOrEmpty(
      version, JaxbReflect.getOptional(p, "getStandardAttributes"));
    d.defaultListForm = JaxbReflect.getStringOptional(p, "getDefaultListForm");
    d.auxiliaryListForm = JaxbReflect.getStringOptional(p, "getAuxiliaryListForm");
    d.includeHelpInContents = JaxbReflect.getBooleanOptional(p, "isIncludeHelpInContents");
    d.help = JaxbReflect.getStringOptional(p, "getHelp");
    d.recordSetModule = JaxbReflect.getStringOptional(p, "getRecordSetModule");
    d.managerModule = JaxbReflect.getStringOptional(p, "getManagerModule");
    d.dataLockControlMode = enumName(p, "getDataLockControlMode");
    d.fullTextSearch = enumName(p, "getFullTextSearch");
    d.listPresentationRu = LocalStringSync.firstRu(JaxbReflect.getOptional(p, "getListPresentation"));
    d.extendedListPresentationRu = LocalStringSync.firstRu(JaxbReflect.getOptional(p, "getExtendedListPresentation"));
    d.explanationRu = LocalStringSync.firstRu(JaxbReflect.getOptional(p, "getExplanation"));
    d.additionalIndexes = JaxbReflect.getStringOptional(p, "getAdditionalIndexes");

    d.editType = enumName(p, "getEditType");
    d.defaultRecordForm = JaxbReflect.getStringOptional(p, "getDefaultRecordForm");
    d.auxiliaryRecordForm = JaxbReflect.getStringOptional(p, "getAuxiliaryRecordForm");
    d.informationRegisterPeriodicity = enumName(p, "getInformationRegisterPeriodicity");
    d.writeMode = enumName(p, "getWriteMode");
    d.mainFilterOnPeriod = JaxbReflect.getBooleanOptional(p, "isMainFilterOnPeriod");
    d.enableTotalsSliceFirst = JaxbReflect.getBooleanOptional(p, "isEnableTotalsSliceFirst");
    d.enableTotalsSliceLast = JaxbReflect.getBooleanOptional(p, "isEnableTotalsSliceLast");
    d.recordPresentationRu = LocalStringSync.firstRu(JaxbReflect.getOptional(p, "getRecordPresentation"));
    d.extendedRecordPresentationRu =
      LocalStringSync.firstRu(JaxbReflect.getOptional(p, "getExtendedRecordPresentation"));
    d.dataHistory = enumName(p, "getDataHistory");
    d.updateDataHistoryImmediatelyAfterWrite =
      JaxbReflect.getBooleanOptional(p, "isUpdateDataHistoryImmediatelyAfterWrite");
    d.executeAfterWriteDataHistoryVersionProcessing =
      JaxbReflect.getBooleanOptional(p, "isExecuteAfterWriteDataHistoryVersionProcessing");

    d.registerType = enumName(p, "getRegisterType");
    d.enableTotalsSplitting = JaxbReflect.getBooleanOptional(p, "isEnableTotalsSplitting");
    d.aggregates = JaxbReflect.getStringOptional(p, "getAggregates");
    dto.register = d;
  }

  public static void apply(SchemaVersion version, Object p, MdObjectPropertiesDto dto) {
    MdRegisterPropertiesDto d = dto.register;
    if (d == null) {
      throw new IllegalArgumentException("register required");
    }
    MdPropertiesBridgeSupport.applyCommon(p, dto);
    JaxbReflect.setEnumOrKeep(p, "setObjectBelonging", d.objectBelonging);
    JaxbReflect.setOptional(p, "setExtendedConfigurationObject", nullIfBlank(d.extendedConfigurationObject));
    JaxbReflect.setOptional(p, "setUseStandardCommands", d.useStandardCommands);
    applyStandardAttributes(version, p, d);
    JaxbReflect.setOptional(p, "setDefaultListForm", d.defaultListForm);
    JaxbReflect.setOptional(p, "setAuxiliaryListForm", d.auxiliaryListForm);
    JaxbReflect.setOptional(p, "setIncludeHelpInContents", d.includeHelpInContents);
    JaxbReflect.setOptional(p, "setHelp", d.help);
    JaxbReflect.setOptional(p, "setRecordSetModule", d.recordSetModule);
    JaxbReflect.setOptional(p, "setManagerModule", d.managerModule);
    JaxbReflect.setEnumOrKeep(p, "setDataLockControlMode", d.dataLockControlMode);
    JaxbReflect.setEnumOrKeep(p, "setFullTextSearch", d.fullTextSearch);
    ensureAndSetRu(p, "getListPresentation", "setListPresentation", d.listPresentationRu);
    ensureAndSetRu(p, "getExtendedListPresentation", "setExtendedListPresentation", d.extendedListPresentationRu);
    ensureAndSetRu(p, "getExplanation", "setExplanation", d.explanationRu);
    JaxbReflect.setOptional(p, "setAdditionalIndexes", d.additionalIndexes);

    JaxbReflect.setEnumOrKeep(p, "setEditType", d.editType);
    JaxbReflect.setOptional(p, "setDefaultRecordForm", d.defaultRecordForm);
    JaxbReflect.setOptional(p, "setAuxiliaryRecordForm", d.auxiliaryRecordForm);
    JaxbReflect.setEnumOrKeep(p, "setInformationRegisterPeriodicity", d.informationRegisterPeriodicity);
    JaxbReflect.setEnumOrKeep(p, "setWriteMode", d.writeMode);
    JaxbReflect.setOptional(p, "setMainFilterOnPeriod", d.mainFilterOnPeriod);
    JaxbReflect.setOptional(p, "setEnableTotalsSliceFirst", d.enableTotalsSliceFirst);
    JaxbReflect.setOptional(p, "setEnableTotalsSliceLast", d.enableTotalsSliceLast);
    ensureAndSetRu(p, "getRecordPresentation", "setRecordPresentation", d.recordPresentationRu);
    ensureAndSetRu(p, "getExtendedRecordPresentation", "setExtendedRecordPresentation", d.extendedRecordPresentationRu);
    JaxbReflect.setEnumOrKeep(p, "setDataHistory", d.dataHistory);
    JaxbReflect.setOptional(p, "setUpdateDataHistoryImmediatelyAfterWrite", d.updateDataHistoryImmediatelyAfterWrite);
    JaxbReflect.setOptional(p, "setExecuteAfterWriteDataHistoryVersionProcessing",
      d.executeAfterWriteDataHistoryVersionProcessing);

    JaxbReflect.setEnumOrKeep(p, "setRegisterType", d.registerType);
    JaxbReflect.setOptional(p, "setEnableTotalsSplitting", d.enableTotalsSplitting);
    JaxbReflect.setOptional(p, "setAggregates", d.aggregates);
  }

  private static void applyStandardAttributes(SchemaVersion version, Object p, MdRegisterPropertiesDto d) {
    if (d.standardAttributesXml == null || d.standardAttributesXml.isBlank()) {
      return;
    }
    try {
      JaxbReflect.setOptional(p, "setStandardAttributes",
        MdCfCatalogSubtreeXml.unmarshalStandardAttributes(version, d.standardAttributesXml.trim()));
    } catch (JAXBException e) {
      throw new IllegalArgumentException("standardAttributesXml: " + e.getMessage(), e);
    }
  }
}
