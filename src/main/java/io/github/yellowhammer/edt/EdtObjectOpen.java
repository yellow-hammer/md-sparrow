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

import java.nio.file.Path;
import java.util.Set;

import io.github.yellowhammer.designerxml.cf.MdObjectOpen;

/**
 * Что открывать по клику на объект дерева: раскладка проекта 1С:EDT.
 *
 * Цель та же, что у выгрузки конфигуратора, поэтому IDE различать форматы не
 * приходится: она получает готовые пути к форме или модулю.
 */
public final class EdtObjectOpen {

  /** Виды объектов, которые сами являются формой. */
  private static final Set<String> FORM_TYPES = Set.of("CommonForm");

  /** Виды объектов, у которых открывается модуль. */
  private static final Set<String> MODULE_TYPES = Set.of("CommonModule", "HTTPService", "WebService");

  private EdtObjectOpen() {
  }

  /**
   * Цель открытия объекта.
   *
   * @param workspaceRoot корень рабочей области
   * @param objectType вид объекта
   * @param objectMdo файл объекта или {@code null}, если его нет
   * @return цель или {@code null}, если открывать нечего
   */
  public static MdObjectOpen.Target resolve(Path workspaceRoot, String objectType, Path objectMdo) {
    if (objectType == null || objectType.isBlank() || objectMdo == null) {
      return null;
    }
    if (FORM_TYPES.contains(objectType)) {
      Path directory = objectMdo.getParent();
      return MdObjectOpen.Target.form(
          relative(workspaceRoot, directory.resolve("Form.form")),
          relative(workspaceRoot, directory.resolve("Module.bsl")));
    }
    if (MODULE_TYPES.contains(objectType)) {
      return MdObjectOpen.Target.module(relative(workspaceRoot, objectMdo.getParent().resolve("Module.bsl")));
    }
    return MdObjectOpen.Target.properties();
  }

  private static String relative(Path workspaceRoot, Path target) {
    return workspaceRoot.relativize(target.toAbsolutePath().normalize()).toString().replace('\\', '/');
  }
}
