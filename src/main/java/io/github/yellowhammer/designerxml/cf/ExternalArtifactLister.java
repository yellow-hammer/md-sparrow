/*
 * This file is a part of md-sparrow.
 *
 * SPDX-License-Identifier: LGPL-3.0-or-later
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
    Path rootNorm = projectRoot.toAbsolutePath().normalize();
    List<ExternalArtifactEntry> out = new ArrayList<>();
    try (DirectoryStream<Path> ds = Files.newDirectoryStream(dir, Files::isDirectory)) {
      for (Path sub : ds) {
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
