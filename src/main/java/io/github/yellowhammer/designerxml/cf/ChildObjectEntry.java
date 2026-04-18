/*
 * This file is a part of md-sparrow.
 *
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package io.github.yellowhammer.designerxml.cf;

/**
 * Одна строка в {@code Configuration/ChildObjects}: имя XML-элемента (тип объекта) и текст (имя объекта).
 */
public record ChildObjectEntry(String objectType, String name) {

  public ChildObjectEntry {
    if (objectType == null || objectType.isEmpty()) {
      throw new IllegalArgumentException("objectType");
    }
    if (name == null) {
      name = "";
    }
  }
}
