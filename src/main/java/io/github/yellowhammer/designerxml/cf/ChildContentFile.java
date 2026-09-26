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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * Файл содержимого подчинённого объекта: формы или макета.
 *
 * <p>Выгрузка конфигуратора держит содержимое в каталоге {@code Ext} и зовёт
 * файл одинаково у всех видов. Проект EDT кладёт его рядом с описанием и даёт
 * каждому виду своё расширение: у макетов это табличный документ, схема
 * компоновки, текст и прочее, у форм управляемая и обычная.
 *
 * <p>Имя файла не вычисляется по виду, а ищется на диске: так работают и виды,
 * которых мы ещё не встречали.
 */
public final class ChildContentFile {

  /** Расширения файлов, содержимое которых текстом не показать. */
  private static final List<String> BINARY_SUFFIXES = List.of(".bin", ".addin");

  private ChildContentFile() {
  }

  /**
   * Файл содержимого в каталоге подчинённого объекта.
   *
   * @param childDir каталог формы или макета
   * @param prefix имя файла без расширения: {@code Form} либо {@code Template}
   * @return файл содержимого либо {@code null}, если рядом его нет
   */
  public static Path fileIn(Path childDir, String prefix) {
    Path ext = childDir.resolve("Ext");
    Path designer = firstContentFile(ext, prefix);
    if (designer != null) {
      return designer;
    }
    return firstContentFile(childDir, prefix);
  }

  /** Содержимое двоичное: показывать его нечем, и открывать текстом нельзя. */
  public static boolean isBinary(Path contentFile) {
    if (contentFile == null) {
      return false;
    }
    String name = contentFile.getFileName().toString().toLowerCase(Locale.ROOT);
    return BINARY_SUFFIXES.stream().anyMatch(name::endsWith);
  }

  private static Path firstContentFile(Path directory, String prefix) {
    if (!Files.isDirectory(directory)) {
      return null;
    }
    try (Stream<Path> entries = Files.list(directory)) {
      return entries
        .filter(Files::isRegularFile)
        .filter(path -> path.getFileName().toString().startsWith(prefix + "."))
        .sorted()
        .findFirst()
        .orElse(null);
    } catch (IOException error) {
      return null;
    }
  }
}
