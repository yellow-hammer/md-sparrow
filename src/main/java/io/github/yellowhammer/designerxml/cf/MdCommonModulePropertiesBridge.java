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
 * Чтение и запись {@code CommonModuleProperties} через JAXB-рефлексию.
 */
public final class MdCommonModulePropertiesBridge {

  private MdCommonModulePropertiesBridge() {
  }

  public static void read(Object p, MdObjectPropertiesDto dto) {
    MdCommonModulePropertiesDto d = new MdCommonModulePropertiesDto();
    d.objectBelonging = enumName(p, "getObjectBelonging");
    d.extendedConfigurationObject = nullIfBlank(JaxbReflect.getStringOptional(p, "getExtendedConfigurationObject"));
    d.global = JaxbReflect.getBooleanOptional(p, "isGlobal");
    d.clientManagedApplication = JaxbReflect.getBooleanOptional(p, "isClientManagedApplication");
    d.server = JaxbReflect.getBooleanOptional(p, "isServer");
    d.externalConnection = JaxbReflect.getBooleanOptional(p, "isExternalConnection");
    d.clientOrdinaryApplication = JaxbReflect.getBooleanOptional(p, "isClientOrdinaryApplication");
    d.client = JaxbReflect.getBooleanOptional(p, "isClient");
    d.serverCall = JaxbReflect.getBooleanOptional(p, "isServerCall");
    d.privileged = JaxbReflect.getBooleanOptional(p, "isPrivileged");
    d.returnValuesReuse = enumName(p, "getReturnValuesReuse");
    dto.commonModule = d;
  }

  public static void apply(Object p, MdObjectPropertiesDto dto) {
    MdCommonModulePropertiesDto d = dto.commonModule;
    if (d == null) {
      throw new IllegalArgumentException("commonModule required");
    }
    MdPropertiesBridgeSupport.applyCommon(p, dto);
    JaxbReflect.setEnumOrKeep(p, "setObjectBelonging", d.objectBelonging);
    JaxbReflect.setOptional(p, "setExtendedConfigurationObject", nullIfBlank(d.extendedConfigurationObject));
    JaxbReflect.setOptional(p, "setGlobal", d.global);
    JaxbReflect.setOptional(p, "setClientManagedApplication", d.clientManagedApplication);
    JaxbReflect.setOptional(p, "setServer", d.server);
    JaxbReflect.setOptional(p, "setExternalConnection", d.externalConnection);
    JaxbReflect.setOptional(p, "setClientOrdinaryApplication", d.clientOrdinaryApplication);
    JaxbReflect.setOptional(p, "setClient", d.client);
    JaxbReflect.setOptional(p, "setServerCall", d.serverCall);
    JaxbReflect.setOptional(p, "setPrivileged", d.privileged);
    JaxbReflect.setEnumOrKeep(p, "setReturnValuesReuse", d.returnValuesReuse);
  }
}
