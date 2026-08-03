/*
 * This file is a part of md-sparrow.
 *
 * Copyright (c) 2026
 * Ivan Karlo <i.karlo@outlook.com> and contributors
 *
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package io.github.yellowhammer.designerxml.cf;

import io.github.yellowhammer.designerxml.reflect.JaxbReflect;

import static io.github.yellowhammer.designerxml.cf.MdPropertiesBridgeSupport.enumName;
import static io.github.yellowhammer.designerxml.cf.MdPropertiesBridgeSupport.ensureAndSetRu;
import static io.github.yellowhammer.designerxml.cf.MdPropertiesBridgeSupport.nullIfBlank;

/**
 * Чтение и запись {@code ReportProperties} и {@code DataProcessorProperties} через
 * JAXB-рефлексию.
 *
 * <p>Свойств отчёта у обработки нет: рефлексия пропускает отсутствующие геттеры и сеттеры,
 * поэтому мост общий, а поля отчёта у обработки остаются пустыми.
 */
public final class MdReportPropertiesBridge {

  private MdReportPropertiesBridge() {
  }

  public static void read(Object p, MdObjectPropertiesDto dto) {
    MdReportPropertiesDto d = new MdReportPropertiesDto();
    d.objectBelonging = enumName(p, "getObjectBelonging");
    d.extendedConfigurationObject = nullIfBlank(JaxbReflect.getStringOptional(p, "getExtendedConfigurationObject"));
    d.useStandardCommands = JaxbReflect.getBooleanOptional(p, "isUseStandardCommands");
    d.defaultForm = JaxbReflect.getStringOptional(p, "getDefaultForm");
    d.auxiliaryForm = JaxbReflect.getStringOptional(p, "getAuxiliaryForm");
    d.objectModule = JaxbReflect.getStringOptional(p, "getObjectModule");
    d.managerModule = JaxbReflect.getStringOptional(p, "getManagerModule");
    d.includeHelpInContents = JaxbReflect.getBooleanOptional(p, "isIncludeHelpInContents");
    d.extendedPresentationRu = LocalStringSync.firstRu(JaxbReflect.getOptional(p, "getExtendedPresentation"));
    d.explanationRu = LocalStringSync.firstRu(JaxbReflect.getOptional(p, "getExplanation"));
    d.mainDataCompositionSchema = JaxbReflect.getStringOptional(p, "getMainDataCompositionSchema");
    d.defaultSettingsForm = JaxbReflect.getStringOptional(p, "getDefaultSettingsForm");
    d.auxiliarySettingsForm = JaxbReflect.getStringOptional(p, "getAuxiliarySettingsForm");
    d.defaultVariantForm = JaxbReflect.getStringOptional(p, "getDefaultVariantForm");
    d.auxiliaryVariantForm = JaxbReflect.getStringOptional(p, "getAuxiliaryVariantForm");
    d.variantsStorage = JaxbReflect.getStringOptional(p, "getVariantsStorage");
    d.settingsStorage = JaxbReflect.getStringOptional(p, "getSettingsStorage");
    dto.report = d;
  }

  public static void apply(Object p, MdObjectPropertiesDto dto) {
    MdReportPropertiesDto d = dto.report;
    if (d == null) {
      throw new IllegalArgumentException("report required");
    }
    MdPropertiesBridgeSupport.applyCommon(p, dto);
    JaxbReflect.setEnumOrKeep(p, "setObjectBelonging", d.objectBelonging);
    JaxbReflect.setOptional(p, "setExtendedConfigurationObject", nullIfBlank(d.extendedConfigurationObject));
    JaxbReflect.setOptional(p, "setUseStandardCommands", d.useStandardCommands);
    JaxbReflect.setOptional(p, "setDefaultForm", d.defaultForm);
    JaxbReflect.setOptional(p, "setAuxiliaryForm", d.auxiliaryForm);
    JaxbReflect.setOptional(p, "setObjectModule", d.objectModule);
    JaxbReflect.setOptional(p, "setManagerModule", d.managerModule);
    JaxbReflect.setOptional(p, "setIncludeHelpInContents", d.includeHelpInContents);
    ensureAndSetRu(p, "getExtendedPresentation", "setExtendedPresentation", d.extendedPresentationRu);
    ensureAndSetRu(p, "getExplanation", "setExplanation", d.explanationRu);
    JaxbReflect.setOptional(p, "setMainDataCompositionSchema", d.mainDataCompositionSchema);
    JaxbReflect.setOptional(p, "setDefaultSettingsForm", d.defaultSettingsForm);
    JaxbReflect.setOptional(p, "setAuxiliarySettingsForm", d.auxiliarySettingsForm);
    JaxbReflect.setOptional(p, "setDefaultVariantForm", d.defaultVariantForm);
    JaxbReflect.setOptional(p, "setAuxiliaryVariantForm", d.auxiliaryVariantForm);
    JaxbReflect.setOptional(p, "setVariantsStorage", d.variantsStorage);
    JaxbReflect.setOptional(p, "setSettingsStorage", d.settingsStorage);
  }
}
