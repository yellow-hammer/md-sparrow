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

import java.io.IOException;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.ProviderMismatchException;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.Objects;

/**
 * Путь промежуточной файловой системы: обёртка над путём диска.
 *
 * <p>Вся арифметика путей - у пути диска; обёртка только держит файловую систему,
 * чтобы операции {@code Files} с этим путём шли через неё.
 */
final class StagedPath implements Path {

  private final StagedFileSystem fs;
  private final Path delegate;

  StagedPath(StagedFileSystem fs, Path delegate) {
    this.fs = fs;
    this.delegate = delegate;
  }

  /** Путь диска, который представляет этот путь. */
  Path delegate() {
    return delegate;
  }

  private Path wrap(Path path) {
    return path == null ? null : new StagedPath(fs, path);
  }

  private Path unwrap(Path other) {
    if (other instanceof StagedPath staged) {
      if (staged.fs != fs) {
        throw new ProviderMismatchException("путь другой промежуточной файловой системы");
      }
      return staged.delegate;
    }
    if (other.getFileSystem() == delegate.getFileSystem()) {
      return other;
    }
    throw new ProviderMismatchException();
  }

  @Override
  public FileSystem getFileSystem() {
    return fs;
  }

  @Override
  public boolean isAbsolute() {
    return delegate.isAbsolute();
  }

  @Override
  public Path getRoot() {
    return wrap(delegate.getRoot());
  }

  @Override
  public Path getFileName() {
    return wrap(delegate.getFileName());
  }

  @Override
  public Path getParent() {
    return wrap(delegate.getParent());
  }

  @Override
  public int getNameCount() {
    return delegate.getNameCount();
  }

  @Override
  public Path getName(int index) {
    return wrap(delegate.getName(index));
  }

  @Override
  public Path subpath(int beginIndex, int endIndex) {
    return wrap(delegate.subpath(beginIndex, endIndex));
  }

  @Override
  public boolean startsWith(Path other) {
    return other.getFileSystem() == fs && delegate.startsWith(unwrap(other));
  }

  @Override
  public boolean endsWith(Path other) {
    return other.getFileSystem() == fs && delegate.endsWith(unwrap(other));
  }

  @Override
  public Path normalize() {
    return wrap(delegate.normalize());
  }

  @Override
  public Path resolve(Path other) {
    return wrap(delegate.resolve(unwrap(other)));
  }

  @Override
  public Path relativize(Path other) {
    return wrap(delegate.relativize(unwrap(other)));
  }

  @Override
  public URI toUri() {
    return delegate.toUri();
  }

  @Override
  public Path toAbsolutePath() {
    return wrap(delegate.toAbsolutePath());
  }

  @Override
  public Path toRealPath(LinkOption... options) throws IOException {
    // Файла может ещё не быть на диске: тогда реальный путь - нормализованный абсолютный
    Path absolute = delegate.toAbsolutePath().normalize();
    if (fs.provider().staged(absolute) != null) {
      return wrap(absolute);
    }
    return wrap(delegate.toRealPath(options));
  }

  @Override
  public WatchKey register(WatchService watcher, WatchEvent.Kind<?>[] events, WatchEvent.Modifier... modifiers) {
    throw new UnsupportedOperationException("наблюдение за промежуточной файловой системой не поддержано");
  }

  @Override
  public int compareTo(Path other) {
    return delegate.compareTo(unwrap(other));
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof StagedPath staged && staged.fs == fs && staged.delegate.equals(delegate);
  }

  @Override
  public int hashCode() {
    return Objects.hash(System.identityHashCode(fs), delegate);
  }

  @Override
  public String toString() {
    return delegate.toString();
  }
}
