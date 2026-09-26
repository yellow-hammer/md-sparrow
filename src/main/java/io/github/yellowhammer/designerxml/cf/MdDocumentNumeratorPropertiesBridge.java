/*
 * This file is a part of md-sparrow.
 *
 * Copyright (c) 2026
 * Ivan Karlo <i.karlo@outlook.com> and contributors
 *
 * SPDX-License-Identifier: LGPL-3.0-or-later
 *
 * md-sparrow is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3.0 of the License, or (at your option) any later version.
 *
 * md-sparrow is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with md-sparrow.
 */
package io.github.yellowhammer.designerxml.cf;

import io.github.yellowhammer.designerxml.reflect.JaxbReflect;

import static io.github.yellowhammer.designerxml.cf.MdPropertiesBridgeSupport.enumName;
import static io.github.yellowhammer.designerxml.cf.MdPropertiesBridgeSupport.nullIfBlank;

/**
 * Чтение и запись {@code DocumentNumeratorProperties} через JAXB-рефлексию. 
 */
public final class MdDocumentNumeratorPropertiesBridge {

  private MdDocumentNumeratorPropertiesBridge() {
  }

  public static void read(Object p, MdObjectPropertiesDto dto) {
    MdDocumentNumeratorPropertiesDto d = new MdDocumentNumeratorPropertiesDto();
    d.objectBelonging = enumName(p, "getObjectBelonging");
    d.extendedConfigurationObject = nullIfBlank(JaxbReflect.getStringOptional(p, "getExtendedConfigurationObject"));
    d.numberType = enumName(p, "getNumberType");
    d.numberLength = MdPropertiesBridgeSupport.decimalOrZero(p, "getNumberLength");
    d.numberAllowedLength = enumName(p, "getNumberAllowedLength");
    d.numberPeriodicity = enumName(p, "getNumberPeriodicity");
    d.checkUnique = JaxbReflect.getBooleanOptional(p, "isCheckUnique");
    dto.documentNumerator = d;
  }

  public static void apply(Object p, MdObjectPropertiesDto dto) {
    MdDocumentNumeratorPropertiesDto d = dto.documentNumerator;
    if (d == null) {
      throw new IllegalArgumentException("documentNumerator required");
    }
    MdPropertiesBridgeSupport.applyCommon(p, dto);
    JaxbReflect.setEnumOrKeep(p, "setObjectBelonging", d.objectBelonging);
    JaxbReflect.setOptional(p, "setExtendedConfigurationObject", nullIfBlank(d.extendedConfigurationObject));
    JaxbReflect.setEnumOrKeep(p, "setNumberType", d.numberType);
    MdPropertiesBridgeSupport.setDecimal(p, "setNumberLength", d.numberLength);
    JaxbReflect.setEnumOrKeep(p, "setNumberAllowedLength", d.numberAllowedLength);
    JaxbReflect.setEnumOrKeep(p, "setNumberPeriodicity", d.numberPeriodicity);
    JaxbReflect.setOptional(p, "setheckUnique", d.checkUnique);
  }
}
