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
package io.github.yellowhammer.edt;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;

import io.github.yellowhammer.designerxml.SchemaVersion;

/**
 * Умолчания управляемой формы, которые 1С:EDT пишет явно, по формату проекта.
 *
 * Константа названа первым форматом, с которого 1С:EDT пишет форму так; формат проекта - по
 * {@code Runtime-Version} манифеста, проект без манифеста получает самый новый.
 */
enum EdtFormPlatform {

  V2_10("Forms/rules/2.10/Form.form"),
  V2_13("Forms/rules/2.10/Form.form"),
  V2_16("Forms/rules/2.16/Form.form"),
  V2_21("Forms/ФормаЭлемента/Form.form");

  private final String emptyForm;

  EdtFormPlatform(String emptyForm) {
    this.emptyForm = emptyForm;
  }

  /**
   * Умолчания проекта.
   *
   * @param projectDir каталог проекта
   * @return умолчания формата проекта
   * @throws IOException если манифест не читается
   */
  static EdtFormPlatform ofProject(Path projectDir) throws IOException {
    return of(projectDir == null ? Optional.empty() : EdtProjectManifest.format(projectDir));
  }

  /**
   * Умолчания проекта, в котором лежит объект.
   *
   * @param objectMdo описание объекта {@code <проект>/src/<Вид>/<Имя>/<Имя>.mdo}
   * @return умолчания формата проекта
   * @throws IOException если манифест не читается
   */
  static EdtFormPlatform ofObject(Path objectMdo) throws IOException {
    return of(EdtProjectManifest.formatOfObject(objectMdo));
  }

  /**
   * Умолчания проекта, в котором лежит форма: объекта или общая.
   *
   * @param formFile файл {@code Form.form}
   * @return умолчания формата проекта
   * @throws IOException если манифест не читается
   */
  static EdtFormPlatform ofForm(Path formFile) throws IOException {
    Path owner = EdtFormItemStructureEdit.ownerMdo(formFile);
    return owner == null ? of(Optional.empty()) : ofObject(owner);
  }

  private static EdtFormPlatform of(Optional<SchemaVersion> format) {
    EdtFormPlatform[] all = values();
    if (format.isEmpty()) {
      return all[all.length - 1];
    }
    EdtFormPlatform found = all[0];
    for (EdtFormPlatform candidate : all) {
      if (SchemaVersion.valueOf(candidate.name()).compareTo(format.get()) <= 0) {
        found = candidate;
      }
    }
    return found;
  }

  /**
   * Пустая форма, которую записала 1С:EDT.
   *
   * @return разметка {@code Form.form} с переводами строк LF
   * @throws IOException если эталона нет в сборке
   */
  String emptyForm() throws IOException {
    return EdtObjectScaffold.golden(emptyForm);
  }

  /** Поведение обычной группы; {@code null} - не пишется. */
  String usualGroupBehavior() {
    return this == V2_10 ? null : "Auto";
  }

  /** Отображение обычной группы. */
  String usualGroupRepresentation() {
    return this == V2_21 ? "Auto" : "WeakSeparation";
  }

  /** Отображение страниц. */
  String pagesRepresentation() {
    return this == V2_10 ? "TabsOnTop" : "Auto";
  }

  /** Группировка страницы. */
  String pageGroup() {
    return this == V2_21 ? "Auto" : "Vertical";
  }

  /** Отображение заголовка страницы. */
  String pageShowTitle() {
    return this == V2_21 ? "auto" : "true";
  }
}
