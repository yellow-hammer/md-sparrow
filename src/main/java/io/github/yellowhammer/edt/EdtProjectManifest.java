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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.github.yellowhammer.designerxml.SchemaVersion;

/**
 * Манифест проекта 1С:EDT {@code DT-INF/PROJECT.PMF}: версия платформы проекта.
 *
 * От версии платформы зависит, какие виды объектов в проекте бывают: вид, которого
 * платформа проекта ещё не знает, 1С:EDT в проекте не держит. От неё же зависит,
 * как 1С:EDT пишет управляемую форму.
 */
final class EdtProjectManifest {

  private static final Pattern RUNTIME_VERSION = Pattern.compile("^Runtime-Version:\\s*(.+)$", Pattern.MULTILINE);

  private EdtProjectManifest() {
  }

  /**
   * Версия платформы проекта.
   *
   * @param projectDir каталог проекта
   * @return {@code Runtime-Version} манифеста; пусто, если манифеста или версии в нём нет
   * @throws IOException если манифест не читается
   */
  static Optional<String> runtimeVersion(Path projectDir) throws IOException {
    Path manifest = projectDir.resolve("DT-INF").resolve("PROJECT.PMF");
    if (!Files.isRegularFile(manifest)) {
      return Optional.empty();
    }
    Matcher matcher = RUNTIME_VERSION.matcher(Files.readString(manifest, StandardCharsets.UTF_8));
    return matcher.find() ? Optional.of(matcher.group(1).trim()) : Optional.empty();
  }

  /**
   * Формат платформы проекта.
   *
   * @param projectDir каталог проекта
   * @return формат по {@code Runtime-Version} манифеста; пусто, если манифеста или версии в нём нет
   * @throws IOException если манифест не читается
   */
  static Optional<SchemaVersion> format(Path projectDir) throws IOException {
    return runtimeVersion(projectDir).map(SchemaVersion::ofPlatform);
  }

  /**
   * Формат платформы проекта, в котором лежит объект.
   *
   * @param objectMdo описание объекта {@code <проект>/src/<Вид>/<Имя>/<Имя>.mdo}
   * @return формат по {@code Runtime-Version} манифеста; пусто, если манифеста или версии в нём нет
   * @throws IOException если манифест не читается
   */
  static Optional<SchemaVersion> formatOfObject(Path objectMdo) throws IOException {
    Path project = objectMdo.toAbsolutePath();
    for (int level = 0; level < 4 && project != null; level++) {
      project = project.getParent();
    }
    return project == null ? Optional.empty() : format(project);
  }
}
