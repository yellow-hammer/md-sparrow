/*
 * This file is a part of md-sparrow.
 *
 * Copyright (c) 2026
 * Ivan Karlo <i.karlo@outlook.com> and contributors
 *
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package io.github.yellowhammer.designerxml;

import java.nio.file.Files;
import java.nio.file.Path;

/** Пути к snapshot-фикстурам из репозитория 1c-platform-samples. */
public final class SamplesSnapshotPaths {

  private SamplesSnapshotPaths() {
  }

  public static Path emptyConfiguration220Root() {
    return samplesRoot()
      .resolve("snapshots")
      .resolve("2.20")
      .resolve("cf")
      .resolve("empty-configuration");
  }

  public static Path emptyFullObjects220Root() {
    return samplesRoot()
      .resolve("snapshots")
      .resolve("2.20")
      .resolve("cf")
      .resolve("empty-full-objects");
  }

  public static boolean has220Snapshots() {
    return Files.isDirectory(emptyConfiguration220Root()) && Files.isDirectory(emptyFullObjects220Root());
  }

  private static Path samplesRoot() {
    return Path.of(System.getProperty("samples.root", "../1c-platform-samples")).toAbsolutePath().normalize();
  }
}
