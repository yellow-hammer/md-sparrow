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
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.List;

/**
 * Сеанс записи одной операции: всё, что она пишет, остаётся в памяти до
 * публикации.
 *
 * <p>Операция получает пути сеанса ({@link #path}) и работает с ними обычными
 * {@code Files}: чтение идёт с диска, а запись, создание каталогов, удаление и
 * перенос - в память. Затем либо {@link #verify} (проверка без записи), либо
 * {@link #publish}: сверка всего прочитанного с диском, запись изменений и откат
 * уже записанного при сбое. Незавершённый сеанс диск не трогает.
 *
 * <p>Сеанс однопоточный: его пути отдаются одной операции.
 */
public final class WriteSession implements AutoCloseable {

  private final StagedFileSystemProvider provider = new StagedFileSystemProvider();

  private WriteSession() {
  }

  /**
   * Открывает сеанс.
   *
   * @return пустой сеанс поверх диска
   */
  public static WriteSession open() {
    return new WriteSession();
  }

  /**
   * Путь сеанса для пути диска.
   *
   * @param diskPath путь файловой системы по умолчанию; путь сеанса возвращается как есть
   * @return путь, операции с которым идут через сеанс
   */
  public Path path(Path diskPath) {
    if (diskPath == null || diskPath instanceof StagedPath) {
      return diskPath;
    }
    if (diskPath.getFileSystem() != FileSystems.getDefault()) {
      throw new IllegalArgumentException("сеанс записи работает только с путями диска: " + diskPath);
    }
    return provider.fileSystem().wrap(diskPath);
  }

  /**
   * Проверка без записи: всё, что операция прочитала, на диске прежнее.
   *
   * @throws StaleInputException если диск изменился
   * @throws IOException если диск не читается
   */
  public void verify() throws IOException {
    provider.verifyInputs();
  }

  /**
   * Пути диска, которые изменит публикация.
   *
   * @return абсолютные пути в порядке имён
   * @throws IOException если диск не читается
   */
  public List<Path> changes() throws IOException {
    return provider.changes();
  }

  /**
   * Публикует изменения: сверка входов, затем запись. При сбое записи уже
   * записанное возвращается к прежнему содержимому.
   *
   * @throws StaleInputException если диск изменился с первого обращения операции
   * @throws IOException если запись не удалась; диск тогда возвращён в прежнее состояние
   */
  public void publish() throws IOException {
    provider.publish();
  }

  StagedFileSystemProvider provider() {
    return provider;
  }

  @Override
  public void close() {
    provider.fileSystem().close();
  }
}
