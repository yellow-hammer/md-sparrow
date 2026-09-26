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
 * Чтение и запись {@code EventSubscriptionProperties} через JAXB-рефлексию. 
 */
public final class MdEventSubscriptionPropertiesBridge {

  private MdEventSubscriptionPropertiesBridge() {
  }

  public static void read(Object p, MdObjectPropertiesDto dto) {
    MdEventSubscriptionPropertiesDto d = new MdEventSubscriptionPropertiesDto();
    d.objectBelonging = enumName(p, "getObjectBelonging");
    d.extendedConfigurationObject = nullIfBlank(JaxbReflect.getStringOptional(p, "getExtendedConfigurationObject"));
    d.source = MdTypeDescriptionBridge.read(JaxbReflect.getOptional(p, "getSource"));
    d.event = JaxbReflect.getStringOptional(p, "getEvent");
    d.handler = JaxbReflect.getStringOptional(p, "getHandler");
    dto.eventSubscription = d;
  }

  public static void apply(Object p, MdObjectPropertiesDto dto) {
    MdEventSubscriptionPropertiesDto d = dto.eventSubscription;
    if (d == null) {
      throw new IllegalArgumentException("eventSubscription required");
    }
    MdPropertiesBridgeSupport.applyCommon(p, dto);
    JaxbReflect.setEnumOrKeep(p, "setObjectBelonging", d.objectBelonging);
    JaxbReflect.setOptional(p, "setExtendedConfigurationObject", nullIfBlank(d.extendedConfigurationObject));
    if (d.source != null) {
      MdTypeDescriptionBridge.apply(JaxbReflect.ensureOptional(p, "getSource", "setSource"), d.source);
    }
    JaxbReflect.setOptional(p, "setEvent", d.event);
    JaxbReflect.setOptional(p, "setHandler", d.handler);
  }
}
