/*
 * This file is a part of md-sparrow.
 *
 * Copyright (c) 2026
 * Ivan Karlo <i.karlo@outlook.com> and contributors
 *
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package io.github.yellowhammer.designerxml.cf;

/**
 * Типовой {@code Configuration/InternalInfo} для новой конфигурации (те же ClassId, что у платформы;
 * ObjectId — новые UUID).
 */
final class ConfigurationInternalInfoTemplates {

  private ConfigurationInternalInfoTemplates() {
  }

  private static final String[] CONTAINED_CLASS_IDS = {
    "9cd510cd-abfc-11d4-9434-004095e12fc7",
    "9fcd25a0-4822-11d4-9414-008048da11f9",
    "e3687481-0a87-462c-a166-9f34594f9bba",
    "9de14907-ec23-4a07-96f0-85521cb6b53b",
    "51f2d5d8-ea4d-4064-8892-82951750031e",
    "e68182ea-4237-4383-967f-90c1e3370bc7",
    "fb282519-d103-4dd3-bc12-cb271d631dfc",
  };

  static io.github.yellowhammer.designerxml.jaxb.v2_20.v8_3_xcf_readable.InternalInfo newInternalInfoV220(
    String seed) {
    var ii = new io.github.yellowhammer.designerxml.jaxb.v2_20.v8_3_xcf_readable.InternalInfo();
    for (int i = 0; i < CONTAINED_CLASS_IDS.length; i++) {
      String classId = CONTAINED_CLASS_IDS[i];
      var co = new io.github.yellowhammer.designerxml.jaxb.v2_20.v8_3_xcf_readable.ContainedObject();
      co.setClassId(classId);
      co.setObjectId(GoldenUuid.from(seed, "containedObject." + i));
      ii.getContainedObject().add(co);
    }
    return ii;
  }

  static io.github.yellowhammer.designerxml.jaxb.v2_21.v8_3_xcf_readable.InternalInfo newInternalInfoV221(
    String seed) {
    var ii = new io.github.yellowhammer.designerxml.jaxb.v2_21.v8_3_xcf_readable.InternalInfo();
    for (int i = 0; i < CONTAINED_CLASS_IDS.length; i++) {
      String classId = CONTAINED_CLASS_IDS[i];
      var co = new io.github.yellowhammer.designerxml.jaxb.v2_21.v8_3_xcf_readable.ContainedObject();
      co.setClassId(classId);
      co.setObjectId(GoldenUuid.from(seed, "containedObject." + i));
      ii.getContainedObject().add(co);
    }
    return ii;
  }
}
