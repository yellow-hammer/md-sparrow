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
 * Чтение и запись {@code SessionParameterProperties} через JAXB-рефлексию. Тип значения задаётся палитрой типов.
 */
public final class MdSessionParameterPropertiesBridge {

  private MdSessionParameterPropertiesBridge() {
  }

  public static void read(Object p, MdObjectPropertiesDto dto) {
    MdSessionParameterPropertiesDto d = new MdSessionParameterPropertiesDto();
    d.objectBelonging = enumName(p, "getObjectBelonging");
    d.extendedConfigurationObject = nullIfBlank(JaxbReflect.getStringOptional(p, "getExtendedConfigurationObject"));
    d.type = MdTypeDescriptionBridge.read(JaxbReflect.getOptional(p, "getType"));
    dto.sessionParameter = d;
  }

  public static void apply(Object p, MdObjectPropertiesDto dto) {
    MdSessionParameterPropertiesDto d = dto.sessionParameter;
    if (d == null) {
      throw new IllegalArgumentException("sessionParameter required");
    }
    MdPropertiesBridgeSupport.applyCommon(p, dto);
    JaxbReflect.setEnumOrKeep(p, "setObjectBelonging", d.objectBelonging);
    JaxbReflect.setOptional(p, "setExtendedConfigurationObject", nullIfBlank(d.extendedConfigurationObject));
    if (d.type != null) {
      MdTypeDescriptionBridge.apply(JaxbReflect.ensureOptional(p, "getType", "setType"), d.type);
    }
  }
}
