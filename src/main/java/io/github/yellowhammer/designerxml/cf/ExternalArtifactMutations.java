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

import io.github.yellowhammer.designerxml.SchemaVersion;
import jakarta.xml.bind.JAXBException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Objects;

public final class ExternalArtifactMutations {

  private ExternalArtifactMutations() {
  }

  public static void rename(Path objectXml, SchemaVersion version, String newName) throws IOException, JAXBException {
    Objects.requireNonNull(objectXml, "objectXml");
    String targetName = requireName(newName);
    if (!Files.isRegularFile(objectXml)) {
      throw new IllegalArgumentException("file not found: " + objectXml);
    }
    Path srcDir = objectXml.getParent();
    if (srcDir == null) {
      throw new IllegalArgumentException("invalid object xml path: " + objectXml);
    }
    Path rootDir = srcDir.getParent();
    if (rootDir == null) {
      throw new IllegalArgumentException("invalid external artifact directory: " + srcDir);
    }
    String oldName = stem(objectXml);
    if (oldName.equals(targetName)) {
      return;
    }
    Path dstDir = rootDir.resolve(targetName);
    if (Files.exists(dstDir)) {
      throw new IllegalArgumentException("target folder already exists: " + dstDir);
    }
    Path dstXml = dstDir.resolve(targetName + ".xml");

    ExternalArtifactPropertiesDto dto = ExternalArtifactPropertiesEdit.read(objectXml, version);
    dto.name = targetName;
    ExternalArtifactPropertiesEdit.write(objectXml, version, dto);

    Files.move(srcDir, dstDir);
    Path movedOldXml = dstDir.resolve(oldName + ".xml");
    if (Files.exists(movedOldXml)) {
      Files.move(movedOldXml, dstXml);
    }
  }

  public static void delete(Path objectXml) throws IOException {
    Objects.requireNonNull(objectXml, "objectXml");
    if (!Files.isRegularFile(objectXml)) {
      throw new IllegalArgumentException("file not found: " + objectXml);
    }
    Path dir = objectXml.getParent();
    if (dir == null || !Files.isDirectory(dir)) {
      throw new IllegalArgumentException("invalid external artifact directory: " + objectXml);
    }
    deleteRecursively(dir);
  }

  public static Path duplicate(Path objectXml, SchemaVersion version, String newName)
    throws IOException, JAXBException {
    Objects.requireNonNull(objectXml, "objectXml");
    String targetName = requireName(newName);
    if (!Files.isRegularFile(objectXml)) {
      throw new IllegalArgumentException("file not found: " + objectXml);
    }
    Path srcDir = objectXml.getParent();
    if (srcDir == null) {
      throw new IllegalArgumentException("invalid object xml path: " + objectXml);
    }
    Path rootDir = srcDir.getParent();
    if (rootDir == null) {
      throw new IllegalArgumentException("invalid external artifact directory: " + srcDir);
    }
    Path dstDir = rootDir.resolve(targetName);
    if (Files.exists(dstDir)) {
      throw new IllegalArgumentException("target folder already exists: " + dstDir);
    }
    copyRecursively(srcDir, dstDir);
    String oldName = stem(objectXml);
    Path srcXmlInCopy = dstDir.resolve(oldName + ".xml");
    Path dstXml = dstDir.resolve(targetName + ".xml");
    if (Files.exists(srcXmlInCopy)) {
      Files.move(srcXmlInCopy, dstXml);
    }
    ExternalArtifactPropertiesDto dto = ExternalArtifactPropertiesEdit.read(dstXml, version);
    dto.name = targetName;
    ExternalArtifactPropertiesEdit.write(dstXml, version, dto);
    return dstXml;
  }

  private static String requireName(String name) {
    String n = name == null ? "" : name.trim();
    if (n.isEmpty()) {
      throw new IllegalArgumentException("new name required");
    }
    CatalogNameConstraints.check(n);
    return n;
  }

  private static String stem(Path objectXml) {
    String fn = objectXml.getFileName().toString();
    if (!fn.toLowerCase().endsWith(".xml")) {
      throw new IllegalArgumentException("expected .xml file: " + objectXml);
    }
    return fn.substring(0, fn.length() - 4);
  }

  private static void copyRecursively(Path srcDir, Path dstDir) throws IOException {
    try (var walk = Files.walk(srcDir)) {
      for (Path src : walk.toList()) {
        Path rel = srcDir.relativize(src);
        Path dst = dstDir.resolve(rel);
        if (Files.isDirectory(src)) {
          Files.createDirectories(dst);
        } else {
          Path parent = dst.getParent();
          if (parent != null) {
            Files.createDirectories(parent);
          }
          Files.copy(src, dst);
        }
      }
    }
  }

  private static void deleteRecursively(Path dir) throws IOException {
    try (var walk = Files.walk(dir)) {
      for (Path p : walk.sorted(Comparator.reverseOrder()).toList()) {
        Files.deleteIfExists(p);
      }
    }
  }
}
