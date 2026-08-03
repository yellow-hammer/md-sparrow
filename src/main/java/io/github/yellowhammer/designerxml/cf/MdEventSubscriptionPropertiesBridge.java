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
