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

/**
 * Пути относительно каталога выгрузки {@code src/cf} (см. {@code docs/cf-layout.md}).
 */
public final class CfLayout {

  public static final String CONFIGURATION_XML = "Configuration.xml";
  /** Имя корневой конфигурации по умолчанию (как при создании в конфигураторе). */
  public static final String DEFAULT_CONFIGURATION_NAME = "Конфигурация";
  /** Основной язык по умолчанию; файл {@code Languages/<имя>.xml}, ссылка {@code Language.<имя>}. */
  public static final String RUSSIAN_LANGUAGE_NAME = "Русский";
  public static final String CONFIG_DUMP_INFO_XML = "ConfigDumpInfo.xml";
  public static final String LANGUAGES_DIR = "Languages";
  public static final String CATALOGS_DIR = "Catalogs";

  private CfLayout() {
  }

  /** {@code .../src/cf/Catalogs/<имя>.xml}. */
  public static Path catalogObjectXml(Path cfRoot, String catalogName) {
    return objectXmlInSubdir(cfRoot, CATALOGS_DIR, catalogName);
  }

  /** {@code .../src/cf/&lt;subdir&gt;/&lt;имя&gt;.xml}. */
  public static Path objectXmlInSubdir(Path cfRoot, String subdir, String objectName) {
    return cfRoot.resolve(subdir).resolve(objectName + ".xml");
  }

  /** {@code .../src/cf/Roles/&lt;роль&gt;/Ext/Rights.xml}. */
  public static Path roleExtRightsXml(Path cfRoot, String roleName) {
    return cfRoot.resolve("Roles").resolve(roleName).resolve("Ext").resolve("Rights.xml");
  }
}
