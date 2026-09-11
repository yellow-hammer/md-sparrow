/*
 * This file is a part of md-sparrow.
 *
 * Copyright (c) 2026
 * Ivan Karlo <i.karlo@outlook.com> and contributors
 *
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package io.github.yellowhammer.designerxml.cf;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * Удаление дерева файлов внутри каталога выгрузки без удаления самого корня.
 */
public final class CfTreeDelete {

  private CfTreeDelete() {
  }

  /**
   * Удаляет всё содержимое каталога выгрузки ({@code src/cf}): объекты метаданных, старые корневые XML и
   * т.д. Сам каталог {@code cfRoot} остаётся (создаётся заново при необходимости вызывающим кодом).
   *
   * @param cfRoot каталог выгрузки; если отсутствует или не каталог — без действия / {@link IllegalArgumentException}
   */
  public static void deleteAllContents(Path cfRoot) throws IOException {
    Objects.requireNonNull(cfRoot, "cfRoot");
    if (!Files.exists(cfRoot)) {
      return;
    }
    if (!Files.isDirectory(cfRoot)) {
      throw new IllegalArgumentException("Ожидается каталог выгрузки: " + cfRoot);
    }
    if (belongsToEdtProject(cfRoot)) {
      throw new IllegalArgumentException(
        "Каталог принадлежит проекту 1С:EDT, выгрузка конфигуратора туда не пишется: " + cfRoot);
    }
    try (Stream<Path> stream = Files.list(cfRoot)) {
      for (Path child : stream.toList()) {
        deleteRecursively(child);
      }
    }
  }

  /**
   * Каталог проекта 1С:EDT или его {@code src}: у выгрузки конфигуратора ни описания
   * {@code Configuration/Configuration.mdo}, ни файлов проекта не бывает.
   */
  private static boolean belongsToEdtProject(Path directory) {
    Path parent = directory.getParent();
    return Files.isRegularFile(directory.resolve("Configuration").resolve("Configuration.mdo"))
      || Files.isRegularFile(directory.resolve(".project"))
      || (parent != null && Files.isRegularFile(parent.resolve(".project")));
  }

  private static void deleteRecursively(Path path) throws IOException {
    if (Files.isDirectory(path)) {
      try (Stream<Path> stream = Files.list(path)) {
        for (Path child : stream.toList()) {
          deleteRecursively(child);
        }
      }
    }
    Files.delete(path);
  }
}
