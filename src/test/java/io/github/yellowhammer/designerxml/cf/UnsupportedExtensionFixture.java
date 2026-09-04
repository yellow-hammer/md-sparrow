/*
 * This file is a part of md-sparrow.
 *
 * SPDX-License-Identifier: LGPL-3.0-or-later
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
