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
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Пути к эталонам платформы из submodule {@code fixtures/samples-1c-platform} и их копии для правки.
 */
public final class SamplesSubmodulePaths {

  private SamplesSubmodulePaths() {
  }

  /** Каталог {@code snapshots/<формат>/<набор>}, например {@code cf-bare-objects}. */
  public static Path snapshot(SchemaVersion version, String set) {
    String root = System.getProperty("samples.root");
    assertThat(root).isNotBlank();
    Path dir = Path.of(root, "snapshots", version.metadataObjectVersionAttribute(), set);
    assertThat(dir).isDirectory();
    return dir;
  }

  /** Голые объекты конфигурации формата: выгрузка платформы со всеми видами, которые умеет md-sparrow. */
  public static Path bareObjects(SchemaVersion version) {
    return snapshot(version, "cf-bare-objects");
  }

  /**
   * Копия каталога эталона: правки идут в копию, индекс submodule не трогается.
   *
   * @return корень копии
   */
  public static Path copy(Path source, Path target) throws IOException {
    try (Stream<Path> walk = Files.walk(source)) {
      for (Path path : walk.toList()) {
        Path to = target.resolve(source.relativize(path).toString());
        if (Files.isDirectory(path)) {
          Files.createDirectories(to);
        } else {
          Files.copy(path, to);
        }
      }
    }
    return target;
  }

  /** Файлы объектов верхнего уровня: {@code <подкаталог вида>/<Имя>.xml}. */
  public static List<Path> objectXmls(Path cfRoot) throws IOException {
    try (Stream<Path> kinds = Files.list(cfRoot)) {
      return kinds
        .filter(Files::isDirectory)
        .flatMap(SamplesSubmodulePaths::xmlFiles)
        .sorted()
        .toList();
    }
  }

  private static Stream<Path> xmlFiles(Path dir) {
    try (Stream<Path> files = Files.list(dir)) {
      return files
        .filter(Files::isRegularFile)
        .filter(file -> file.getFileName().toString().endsWith(".xml"))
        .toList()
        .stream();
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }
}
