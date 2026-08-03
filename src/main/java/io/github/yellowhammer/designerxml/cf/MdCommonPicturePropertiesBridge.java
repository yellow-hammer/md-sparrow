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
 * Чтение и запись {@code CommonPictureProperties} через JAXB-рефлексию. Файл картинки правится не панелью, здесь только признаки доступности.
 */
public final class MdCommonPicturePropertiesBridge {

  private MdCommonPicturePropertiesBridge() {
  }

  public static void read(Object p, MdObjectPropertiesDto dto) {
    MdCommonPicturePropertiesDto d = new MdCommonPicturePropertiesDto();
    d.objectBelonging = enumName(p, "getObjectBelonging");
    d.extendedConfigurationObject = nullIfBlank(JaxbReflect.getStringOptional(p, "getExtendedConfigurationObject"));
    d.availabilityForChoice = JaxbReflect.getBooleanOptional(p, "isAvailabilityForChoice");
    d.availabilityForAppearance = JaxbReflect.getBooleanOptional(p, "isAvailabilityForAppearance");
    dto.commonPicture = d;
  }

  public static void apply(Object p, MdObjectPropertiesDto dto) {
    MdCommonPicturePropertiesDto d = dto.commonPicture;
    if (d == null) {
      throw new IllegalArgumentException("commonPicture required");
    }
    MdPropertiesBridgeSupport.applyCommon(p, dto);
    JaxbReflect.setEnumOrKeep(p, "setObjectBelonging", d.objectBelonging);
    JaxbReflect.setOptional(p, "setExtendedConfigurationObject", nullIfBlank(d.extendedConfigurationObject));
    JaxbReflect.setOptional(p, "setAvailabilityForChoice", d.availabilityForChoice);
    JaxbReflect.setOptional(p, "setAvailabilityForAppearance", d.availabilityForAppearance);
  }
}
