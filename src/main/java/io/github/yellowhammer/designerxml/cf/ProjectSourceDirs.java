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
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Каталоги исходников проекта относительно его корня (абсолютные пути тоже допустимы).
 * По умолчанию стандартная раскладка {@code src/cf}, {@code src/cfe}, {@code src/epf}, {@code src/erf}.
 *
 * <p>Явные списки {@code cfeDirs}, {@code epfDirs} и {@code erfDirs} заменяют перечисление
 * подкаталогов общего каталога: так в дерево попадает расширение из репозитория, вложенного
 * в каталог расширений, и внешние объекты, разложенные по разным каталогам.
 */
public record ProjectSourceDirs(
    String cf,
    String cfe,
    String epf,
    String erf,
    List<String> cfeDirs,
    List<String> epfDirs,
    List<String> erfDirs) {

  public static final ProjectSourceDirs DEFAULTS = new ProjectSourceDirs("src/cf", "src/cfe", "src/epf", "src/erf");

  public ProjectSourceDirs(String cf, String cfe, String epf, String erf) {
    this(cf, cfe, epf, erf, null, null, null);
  }

  /** Значения из CLI: null/пустые заменяются дефолтами. */
  public static ProjectSourceDirs fromNullable(String cf, String cfe, String epf, String erf) {
    return fromNullable(cf, cfe, epf, erf, null, null, null);
  }

  /** Значения из CLI: null/пустые заменяются дефолтами, пустые списки считаются отсутствующими. */
  public static ProjectSourceDirs fromNullable(
      String cf,
      String cfe,
      String epf,
      String erf,
      List<String> cfeDirs,
      List<String> epfDirs,
      List<String> erfDirs) {
    return new ProjectSourceDirs(
      orDefault(cf, DEFAULTS.cf()),
      orDefault(cfe, DEFAULTS.cfe()),
      orDefault(epf, DEFAULTS.epf()),
      orDefault(erf, DEFAULTS.erf()),
      orNull(cfeDirs),
      orNull(epfDirs),
      orNull(erfDirs));
  }

  private static String orDefault(String value, String def) {
    return value == null || value.isBlank() ? def : value.trim();
  }

  private static List<String> orNull(List<String> values) {
    return values == null || values.isEmpty() ? null : List.copyOf(values);
  }

  public Path cfPath(Path projectRoot) {
    return projectRoot.resolve(cf).normalize();
  }

  public Path cfePath(Path projectRoot) {
    return projectRoot.resolve(cfe).normalize();
  }

  public Path epfPath(Path projectRoot) {
    return projectRoot.resolve(epf).normalize();
  }

  public Path erfPath(Path projectRoot) {
    return projectRoot.resolve(erf).normalize();
  }

  /**
   * Каталоги расширений: явный список либо подкаталоги общего каталога.
   *
   * @param projectRoot корень проекта
   * @return каталоги в порядке списка либо по имени
   * @throws IOException если общий каталог не читается
   */
  public List<Path> extensionDirs(Path projectRoot) throws IOException {
    if (cfeDirs != null) {
      return resolveAll(projectRoot, cfeDirs);
    }
    Path root = cfePath(projectRoot);
    if (!Files.isDirectory(root)) {
      return List.of();
    }
    try (var stream = Files.list(root)) {
      return stream.filter(Files::isDirectory).sorted().collect(Collectors.toList());
    }
  }

  /** Явные каталоги внешних обработок; {@code null}, когда их перечисляет общий каталог. */
  public List<Path> explicitEpfDirs(Path projectRoot) {
    return epfDirs == null ? null : resolveAll(projectRoot, epfDirs);
  }

  /** Явные каталоги внешних отчётов; {@code null}, когда их перечисляет общий каталог. */
  public List<Path> explicitErfDirs(Path projectRoot) {
    return erfDirs == null ? null : resolveAll(projectRoot, erfDirs);
  }

  private static List<Path> resolveAll(Path projectRoot, List<String> dirs) {
    List<Path> out = new ArrayList<>(dirs.size());
    for (String dir : dirs) {
      out.add(projectRoot.resolve(dir).normalize());
    }
    return out;
  }
}
