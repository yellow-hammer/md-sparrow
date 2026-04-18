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
package io.github.yellowhammer.designerxml;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/** Пути к данным submodule {@code fixtures/ssl31} (без копий в {@code src/test/resources}). */
public final class Ssl31SubmodulePaths {

  private Ssl31SubmodulePaths() {
  }

  public static Path configurationXml() {
    String root = System.getProperty("fixtures.ssl31.root");
    assertThat(root).isNotBlank();
    Path p = Path.of(root, "src", "cf", "Configuration.xml");
    assertThat(p).exists();
    return p;
  }

  /** Корень submodule {@code fixtures/ssl31} (проект с {@code src/cf}, {@code src/cfe}, …). */
  public static Path projectRoot() {
    String root = System.getProperty("fixtures.ssl31.root");
    assertThat(root).isNotBlank();
    Path p = Path.of(root);
    assertThat(p).exists();
    return p;
  }

  /** Любой {@code Catalogs/*.xml} в submodule (для тестов, где нужен образец структуры). */
  public static Path anyCatalogObjectXml() throws IOException {
    String root = System.getProperty("fixtures.ssl31.root");
    assertThat(root).isNotBlank();
    Path dir = Path.of(root, "src", "cf", "Catalogs");
    assertThat(dir).exists();
    try (Stream<Path> s = Files.list(dir)) {
      Path found = s
        .filter(Files::isRegularFile)
        .filter(p -> p.getFileName().toString().endsWith(".xml"))
        .sorted()
        .findFirst()
        .orElse(null);
      assertThat(found).as("catalog xml under %s", dir).isNotNull();
      return found;
    }
  }
}
