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
 * Находка проверки выгрузки: что не так и где.
 *
 * @param path путь относительно корня выгрузки; пустой, если находка не привязана к файлу
 * @param objectType тип объекта, например {@code Catalog}; пустой для находок уровня выгрузки
 * @param objectName имя объекта; пустое для находок уровня выгрузки
 * @param kind вид проблемы, см. {@link CfDumpValidation}
 * @param message текст для показа человеку
 */
public record CfDumpFinding(String path, String objectType, String objectName, String kind, String message) {

  static CfDumpFinding of(String path, String kind, String message) {
    return new CfDumpFinding(path, "", "", kind, message);
  }

  static CfDumpFinding ofObject(String path, String objectType, String objectName, String kind, String message) {
    return new CfDumpFinding(path, objectType, objectName, kind, message);
  }
}
