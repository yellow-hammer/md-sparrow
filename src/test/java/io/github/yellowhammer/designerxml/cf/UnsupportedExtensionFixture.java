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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Фикстура {@code fixtures/unsupported-extension}: основная конфигурация и расширение
 * читаемого формата рядом с расширением выгрузки 2.9, только {@code Configuration.xml}.
 */
final class UnsupportedExtensionFixture {

  static final String OLD_EXTENSION_DIR = "Old";
  static final String OLD_EXTENSION_NAME = "СтарыйФормат";
  static final String OLD_EXTENSION_VERSION = "2.9";
  static final String NEW_EXTENSION_DIR = "New";
  static final String NEW_EXTENSION_NAME = "НовыйФормат";

  private UnsupportedExtensionFixture() {
  }

  static Path projectRoot() {
    String root = System.getProperty("fixtures.unsupportedExtension.root");
    assertThat(root).isNotBlank();
    Path p = Path.of(root);
    assertThat(p).exists();
    return p;
  }

  /** Каталог старого расширения вместо {@code src/cf}: отказ по версии основной конфигурации. */
  static ProjectSourceDirs oldExtensionAsMain() {
    return ProjectSourceDirs.fromNullable("src/cfe/" + OLD_EXTENSION_DIR, null, null, null);
  }
}
