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
 * Чтение и запись {@code ExternalDataSourceProperties} через JAXB-рефлексию. 
 */
public final class MdExternalDataSourcePropertiesBridge {

  private MdExternalDataSourcePropertiesBridge() {
  }

  public static void read(Object p, MdObjectPropertiesDto dto) {
    MdExternalDataSourcePropertiesDto d = new MdExternalDataSourcePropertiesDto();
    d.objectBelonging = enumName(p, "getObjectBelonging");
    d.extendedConfigurationObject = nullIfBlank(JaxbReflect.getStringOptional(p, "getExtendedConfigurationObject"));
    d.dataLockControlMode = enumName(p, "getDataLockControlMode");
    dto.externalDataSource = d;
  }

  public static void apply(Object p, MdObjectPropertiesDto dto) {
    MdExternalDataSourcePropertiesDto d = dto.externalDataSource;
    if (d == null) {
      throw new IllegalArgumentException("externalDataSource required");
    }
    MdPropertiesBridgeSupport.applyCommon(p, dto);
    JaxbReflect.setEnumOrKeep(p, "setObjectBelonging", d.objectBelonging);
    JaxbReflect.setOptional(p, "setExtendedConfigurationObject", nullIfBlank(d.extendedConfigurationObject));
    JaxbReflect.setEnumOrKeep(p, "setDataLockControlMode", d.dataLockControlMode);
  }
}
