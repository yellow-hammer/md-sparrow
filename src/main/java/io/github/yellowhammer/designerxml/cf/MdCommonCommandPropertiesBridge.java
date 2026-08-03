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
import static io.github.yellowhammer.designerxml.cf.MdPropertiesBridgeSupport.nullIfBlank;

/**
 * Чтение и запись {@code CommonCommandProperties} через JAXB-рефлексию. 
 */
public final class MdCommonCommandPropertiesBridge {

  private MdCommonCommandPropertiesBridge() {
  }

  public static void read(Object p, MdObjectPropertiesDto dto) {
    MdCommonCommandPropertiesDto d = new MdCommonCommandPropertiesDto();
    d.objectBelonging = enumName(p, "getObjectBelonging");
    d.extendedConfigurationObject = nullIfBlank(JaxbReflect.getStringOptional(p, "getExtendedConfigurationObject"));
    d.group = JaxbReflect.getStringOptional(p, "getGroup");
    d.representation = enumName(p, "getRepresentation");
    d.toolTipRu = LocalStringSync.firstRu(JaxbReflect.getOptional(p, "getToolTip"));
    d.shortcut = JaxbReflect.getStringOptional(p, "getShortcut");
    d.commandModule = JaxbReflect.getStringOptional(p, "getCommandModule");
    d.includeHelpInContents = JaxbReflect.getBooleanOptional(p, "isIncludeHelpInContents");
    d.commandParameterType = MdTypeDescriptionBridge.read(JaxbReflect.getOptional(p, "getCommandParameterType"));
    d.parameterUseMode = enumName(p, "getParameterUseMode");
    d.modifiesData = JaxbReflect.getBooleanOptional(p, "isModifiesData");
    d.onMainServerUnavalableBehavior = enumName(p, "getOnMainServerUnavalableBehavior");
    dto.commonCommand = d;
  }

  public static void apply(Object p, MdObjectPropertiesDto dto) {
    MdCommonCommandPropertiesDto d = dto.commonCommand;
    if (d == null) {
      throw new IllegalArgumentException("commonCommand required");
    }
    MdPropertiesBridgeSupport.applyCommon(p, dto);
    JaxbReflect.setEnumOrKeep(p, "setObjectBelonging", d.objectBelonging);
    JaxbReflect.setOptional(p, "setExtendedConfigurationObject", nullIfBlank(d.extendedConfigurationObject));
    JaxbReflect.setOptional(p, "setGroup", d.group);
    JaxbReflect.setEnumOrKeep(p, "setRepresentation", d.representation);
    MdPropertiesBridgeSupport.ensureAndSetRu(p, "getToolTip", "setToolTip", d.toolTipRu);
    JaxbReflect.setOptional(p, "setShortcut", d.shortcut);
    JaxbReflect.setOptional(p, "setCommandModule", d.commandModule);
    JaxbReflect.setOptional(p, "setncludeHelpInContents", d.includeHelpInContents);
    if (d.commandParameterType != null) {
      MdTypeDescriptionBridge.apply(JaxbReflect.ensureOptional(p, "getCommandParameterType", "setCommandParameterType"), d.commandParameterType);
    }
    JaxbReflect.setEnumOrKeep(p, "setParameterUseMode", d.parameterUseMode);
    JaxbReflect.setOptional(p, "setodifiesData", d.modifiesData);
    JaxbReflect.setEnumOrKeep(p, "setOnMainServerUnavalableBehavior", d.onMainServerUnavalableBehavior);
  }
}
