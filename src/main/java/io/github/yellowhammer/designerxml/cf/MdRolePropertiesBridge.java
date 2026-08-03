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
 * Чтение и запись {@code RoleProperties} через JAXB-рефлексию. Состав прав живёт отдельным файлом Rights.xml и здесь не правится.
 */
public final class MdRolePropertiesBridge {

  private MdRolePropertiesBridge() {
  }

  public static void read(Object p, MdObjectPropertiesDto dto) {
    MdRolePropertiesDto d = new MdRolePropertiesDto();
    d.objectBelonging = enumName(p, "getObjectBelonging");
    d.extendedConfigurationObject = nullIfBlank(JaxbReflect.getStringOptional(p, "getExtendedConfigurationObject"));
    dto.role = d;
  }

  public static void apply(Object p, MdObjectPropertiesDto dto) {
    MdRolePropertiesDto d = dto.role;
    if (d == null) {
      throw new IllegalArgumentException("role required");
    }
    MdPropertiesBridgeSupport.applyCommon(p, dto);
    JaxbReflect.setEnumOrKeep(p, "setObjectBelonging", d.objectBelonging);
    JaxbReflect.setOptional(p, "setExtendedConfigurationObject", nullIfBlank(d.extendedConfigurationObject));
  }
}
