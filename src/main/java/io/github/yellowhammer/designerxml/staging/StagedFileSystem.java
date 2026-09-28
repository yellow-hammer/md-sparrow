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
package io.github.yellowhammer.designerxml.staging;

import java.nio.file.FileStore;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.WatchService;
import java.nio.file.attribute.UserPrincipalLookupService;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Промежуточная файловая система поверх диска: чтение проваливается на диск,
 * запись остаётся в памяти до публикации.
 */
final class StagedFileSystem extends FileSystem {

  private final StagedFileSystemProvider provider;
  private final FileSystem disk = FileSystems.getDefault();
  private volatile boolean open = true;

  StagedFileSystem(StagedFileSystemProvider provider) {
    this.provider = provider;
  }

  /** Путь этой файловой системы для пути диска. */
  Path wrap(Path diskPath) {
    return new StagedPath(this, diskPath);
  }

  @Override
  public StagedFileSystemProvider provider() {
    return provider;
  }

  @Override
  public void close() {
    open = false;
  }

  @Override
  public boolean isOpen() {
    return open;
  }

  @Override
  public boolean isReadOnly() {
    return false;
  }

  @Override
  public String getSeparator() {
    return disk.getSeparator();
  }

  @Override
  public Iterable<Path> getRootDirectories() {
    List<Path> roots = new ArrayList<>();
    for (Path root : disk.getRootDirectories()) {
      roots.add(wrap(root));
    }
    return roots;
  }

  @Override
  public Iterable<FileStore> getFileStores() {
    return disk.getFileStores();
  }

  @Override
  public Set<String> supportedFileAttributeViews() {
    return Set.of("basic");
  }

  @Override
  public Path getPath(String first, String... more) {
    return wrap(disk.getPath(first, more));
  }

  @Override
  public PathMatcher getPathMatcher(String syntaxAndPattern) {
    PathMatcher matcher = disk.getPathMatcher(syntaxAndPattern);
    return path -> matcher.matches(path instanceof StagedPath staged ? staged.delegate() : path);
  }

  @Override
  public UserPrincipalLookupService getUserPrincipalLookupService() {
    throw new UnsupportedOperationException();
  }

  @Override
  public WatchService newWatchService() {
    throw new UnsupportedOperationException();
  }
}
