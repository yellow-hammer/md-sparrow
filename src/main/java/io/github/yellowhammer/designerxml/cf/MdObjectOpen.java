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

import java.nio.file.Path;
import java.util.Set;

/**
 * Что открывать по клику на объект в дереве метаданных: раскладка выгрузки конфигуратора.
 *
 * <p>Клиент (IDE) не должен знать про {@code Ext/Form.xml} и {@code Ext/Module.bsl}.
 */
public final class MdObjectOpen {

  public static final String ACTION_FORM = "form";
  public static final String ACTION_MODULE = "module";
  public static final String ACTION_PROPERTIES = "properties";

  private static final Set<String> FORM_TYPES = Set.of("CommonForm");
  private static final Set<String> MODULE_TYPES = Set.of("CommonModule", "HTTPService", "WebService");

  private MdObjectOpen() {
  }

  /**
   * Цель открытия для IDE.
   *
   * @param action {@link #ACTION_FORM}, {@link #ACTION_MODULE} или {@link #ACTION_PROPERTIES}
   * @param relativePath путь к форме или модулю относительно корня проекта; пусто для свойств
   * @param moduleRelativePath модуль формы относительно корня проекта; только для {@link #ACTION_FORM}
   */
  public record Target(String action, String relativePath, String moduleRelativePath) {

    public static Target form(String formRelativePath, String moduleRelativePath) {
      return new Target(ACTION_FORM, formRelativePath, moduleRelativePath);
    }

    public static Target module(String moduleRelativePath) {
      return new Target(ACTION_MODULE, moduleRelativePath, null);
    }

    public static Target properties() {
      return new Target(ACTION_PROPERTIES, null, null);
    }
  }

  /**
   * Считает цель открытия по типу и XML объекта.
   *
   * @param objectType тип из {@code ChildObjects} ({@code CommonForm}, {@code Catalog}, …)
   * @param projectRoot корень проекта
   * @param objectXmlRelativePath путь к XML объекта относительно корня проекта
   * @return цель или {@code null}, если нет типа или пути к файлу объекта
   */
  public static Target resolve(String objectType, Path projectRoot, String objectXmlRelativePath) {
    if (objectType == null || objectType.isBlank()
        || objectXmlRelativePath == null || objectXmlRelativePath.isBlank()) {
      return null;
    }
    Path objectXml = projectRoot.resolve(objectXmlRelativePath);
    if (FORM_TYPES.contains(objectType)) {
      return Target.form(
        relativeToProject(projectRoot, CfLayout.objectExtFormXml(objectXml)),
        relativeToProject(projectRoot, CfLayout.objectExtFormModuleBsl(objectXml)));
    }
    if (MODULE_TYPES.contains(objectType)) {
      return Target.module(relativeToProject(projectRoot, CfLayout.objectExtModuleBsl(objectXml)));
    }
    return Target.properties();
  }

  private static String relativeToProject(Path projectRoot, Path target) {
    Path root = projectRoot.toAbsolutePath().normalize();
    Path abs = target.toAbsolutePath().normalize();
    if (abs.startsWith(root)) {
      return root.relativize(abs).toString().replace('\\', '/');
    }
    return abs.toString().replace('\\', '/');
  }
}
