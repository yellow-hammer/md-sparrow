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

/** Сеансы записи со сбоем публикации для тестов команд. */
public final class WriteSessionFaults {

  private WriteSessionFaults() {
  }

  /**
   * Сеанс, публикация которого срывается на записи файла с заданным номером.
   *
   * @param failing номер записи файла с нуля
   * @return открытый сеанс
   */
  public static WriteSession failingOnWrite(int failing) {
    WriteSession session = WriteSession.open();
    StagedFileSystemProvider.FileWriter real = session.provider().writer;
    int[] count = {0};
    session.provider().writer = (key, bytes) -> {
      if (count[0]++ == failing) {
        throw new IOException("сбой записи");
      }
      real.write(key, bytes);
    };
    return session;
  }
}
