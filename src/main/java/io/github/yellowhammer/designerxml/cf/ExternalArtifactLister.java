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
    return listSubdir(projectRoot, "src", "erf");
  }

  public static List<ExternalArtifactEntry> listEpf(Path projectRoot) throws IOException {
    return listSubdir(projectRoot, "src", "epf");
  }

  private static List<ExternalArtifactEntry> listSubdir(Path projectRoot, String first, String second)
    throws IOException {
    Path dir = projectRoot.resolve(first).resolve(second);
    if (!Files.isDirectory(dir)) {
      return List.of();
    }
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
        Path rel = projectRoot.relativize(xml);
        out.add(new ExternalArtifactEntry(name, rel.toString().replace('\\', '/')));
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
