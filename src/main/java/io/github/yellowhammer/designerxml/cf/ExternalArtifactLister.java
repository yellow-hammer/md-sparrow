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

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Список внешних отчётов/обработок в {@code src/erf} и {@code src/epf} (каталог проекта).
 */
public final class ExternalArtifactLister {

  private ExternalArtifactLister() {
  }

  public record ExternalArtifactEntry(String name, String relativePath) {
  }

  public static List<ExternalArtifactEntry> listErf(Path projectRoot) throws IOException {
    return listArtifacts(projectRoot, projectRoot.resolve("src").resolve("erf"));
  }

  public static List<ExternalArtifactEntry> listEpf(Path projectRoot) throws IOException {
    return listArtifacts(projectRoot, projectRoot.resolve("src").resolve("epf"));
  }

  /** Артефакты в произвольном каталоге; relativePath — от корня проекта (вне корня — абсолютный). */
  public static List<ExternalArtifactEntry> listArtifacts(Path projectRoot, Path dir)
    throws IOException {
    if (!Files.isDirectory(dir)) {
      return List.of();
    }
    List<Path> subs = new ArrayList<>();
    try (DirectoryStream<Path> ds = Files.newDirectoryStream(dir, Files::isDirectory)) {
      for (Path sub : ds) {
        subs.add(sub);
      }
    }
    return artifactsAt(projectRoot, subs);
  }

  /**
   * Артефакты в заданных каталогах, где бы те ни лежали: каталог без описания пропускается.
   *
   * @param projectRoot корень проекта
   * @param dirs каталоги объектов
   * @return записи по имени без учёта регистра
   * @throws IOException если каталог не читается
   */
  public static List<ExternalArtifactEntry> artifactsAt(Path projectRoot, List<Path> dirs) throws IOException {
    Path rootNorm = projectRoot.toAbsolutePath().normalize();
    List<ExternalArtifactEntry> out = new ArrayList<>();
    for (Path sub : dirs) {
      if (!Files.isDirectory(sub)) {
        continue;
      }
      String name = sub.getFileName().toString();
      Path xml = sub.resolve(name + ".xml");
      if (!Files.isRegularFile(xml)) {
        xml = findFirstXmlInDir(sub);
      }
      if (xml == null || !Files.isRegularFile(xml)) {
        continue;
      }
      Path xmlNorm = xml.toAbsolutePath().normalize();
      String rel = xmlNorm.startsWith(rootNorm)
        ? rootNorm.relativize(xmlNorm).toString().replace('\\', '/')
        : xmlNorm.toString().replace('\\', '/');
      out.add(new ExternalArtifactEntry(name, rel));
    }
    out.sort(Comparator.comparing(ExternalArtifactEntry::name, String.CASE_INSENSITIVE_ORDER));
    return out;
  }

  private static Path findFirstXmlInDir(Path dir) throws IOException {
    try (DirectoryStream<Path> ds = Files.newDirectoryStream(dir, p -> {
      String n = p.getFileName().toString().toLowerCase(Locale.ROOT);
      return Files.isRegularFile(p) && n.endsWith(".xml");
    })) {
      for (Path p : ds) {
        return p;
      }
    }
    return null;
  }
}
