/*
 * This file is a part of md-sparrow.
 *
 * Copyright (c) 2026
 * Ivan Karlo <i.karlo@outlook.com> and contributors
 *
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package io.github.yellowhammer.designerxml.cf;

import java.nio.file.Path;

/**
 * Каталоги исходников проекта относительно его корня (абсолютные пути тоже допустимы).
 * По умолчанию — стандартная раскладка {@code src/cf}, {@code src/cfe}, {@code src/epf}, {@code src/erf}.
 */
public record ProjectSourceDirs(String cf, String cfe, String epf, String erf) {

  public static final ProjectSourceDirs DEFAULTS = new ProjectSourceDirs("src/cf", "src/cfe", "src/epf", "src/erf");

  /** Значения из CLI: null/пустые заменяются дефолтами. */
  public static ProjectSourceDirs fromNullable(String cf, String cfe, String epf, String erf) {
    return new ProjectSourceDirs(
      orDefault(cf, DEFAULTS.cf()),
      orDefault(cfe, DEFAULTS.cfe()),
      orDefault(epf, DEFAULTS.epf()),
      orDefault(erf, DEFAULTS.erf()));
  }

  private static String orDefault(String value, String def) {
    return value == null || value.isBlank() ? def : value.trim();
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
}
