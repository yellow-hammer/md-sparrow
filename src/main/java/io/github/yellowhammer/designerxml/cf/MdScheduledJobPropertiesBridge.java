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
 * Чтение и запись {@code ScheduledJobProperties} через JAXB-рефлексию. Расписание правится строкой формата платформы.
 */
public final class MdScheduledJobPropertiesBridge {

  private MdScheduledJobPropertiesBridge() {
  }

  public static void read(Object p, MdObjectPropertiesDto dto) {
    MdScheduledJobPropertiesDto d = new MdScheduledJobPropertiesDto();
    d.objectBelonging = enumName(p, "getObjectBelonging");
    d.extendedConfigurationObject = nullIfBlank(JaxbReflect.getStringOptional(p, "getExtendedConfigurationObject"));
    d.methodName = JaxbReflect.getStringOptional(p, "getMethodName");
    d.description = JaxbReflect.getStringOptional(p, "getDescription");
    d.key = JaxbReflect.getStringOptional(p, "getKey");
    d.schedule = JaxbReflect.getStringOptional(p, "getSchedule");
    d.use = JaxbReflect.getBooleanOptional(p, "isUse");
    d.predefined = JaxbReflect.getBooleanOptional(p, "isPredefined");
    d.restartCountOnFailure = MdPropertiesBridgeSupport.decimalOrZero(p, "getRestartCountOnFailure");
    d.restartIntervalOnFailure = MdPropertiesBridgeSupport.decimalOrZero(p, "getRestartIntervalOnFailure");
    dto.scheduledJob = d;
  }

  public static void apply(Object p, MdObjectPropertiesDto dto) {
    MdScheduledJobPropertiesDto d = dto.scheduledJob;
    if (d == null) {
      throw new IllegalArgumentException("scheduledJob required");
    }
    MdPropertiesBridgeSupport.applyCommon(p, dto);
    JaxbReflect.setEnumOrKeep(p, "setObjectBelonging", d.objectBelonging);
    JaxbReflect.setOptional(p, "setExtendedConfigurationObject", nullIfBlank(d.extendedConfigurationObject));
    JaxbReflect.setOptional(p, "setMethodName", d.methodName);
    JaxbReflect.setOptional(p, "setDescription", d.description);
    JaxbReflect.setOptional(p, "setKey", d.key);
    JaxbReflect.setOptional(p, "setSchedule", d.schedule);
    JaxbReflect.setOptional(p, "setse", d.use);
    JaxbReflect.setOptional(p, "setredefined", d.predefined);
    MdPropertiesBridgeSupport.setDecimal(p, "setRestartCountOnFailure", d.restartCountOnFailure);
    MdPropertiesBridgeSupport.setDecimal(p, "setRestartIntervalOnFailure", d.restartIntervalOnFailure);
  }
}
