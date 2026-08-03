/*
 * This file is a part of md-sparrow.
 *
 * Copyright (c) 2026
 * Ivan Karlo <i.karlo@outlook.com> and contributors
 *
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package io.github.yellowhammer.designerxml.cf;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

/**
 * Файлы команд объекта: модуль команды лежит рядом с XML объекта в
 * {@code <Объект>/Commands/<Имя>/Ext/CommandModule.bsl}.
 *
 * <p>Узел команды живёт в самом XML объекта, а модуль - отдельным файлом, поэтому переименование и
 * удаление команды затрагивают оба места: осиротевший каталог модуля конфигуратор считает мусором.
 */
public final class CommandFiles {

  private CommandFiles() {
  }

  /**
   * Переименовывает каталог модуля команды, если он есть.
   *
   * @param objectXml путь к XML объекта
   * @param oldName текущее имя команды
   * @param newName новое имя команды
   * @throws IOException если каталог не удалось переименовать
   */
  public static void renameCommandDirectory(Path objectXml, String oldName, String newName) throws IOException {
    Path from = commandDirectory(objectXml, oldName);
    if (from == null || !Files.isDirectory(from)) {
      return;
    }
    Path to = from.resolveSibling(newName);
    Files.move(from, to);
  }

  /**
   * Удаляет каталог модуля команды вместе с содержимым, если он есть.
   *
   * @param objectXml путь к XML объекта
   * @param name имя команды
   * @throws IOException если каталог не удалось удалить
   */
  public static void deleteCommandDirectory(Path objectXml, String name) throws IOException {
    Path dir = commandDirectory(objectXml, name);
    if (dir == null || !Files.isDirectory(dir)) {
      return;
    }
    try (Stream<Path> walk = Files.walk(dir)) {
      for (Path path : walk.sorted(Comparator.reverseOrder()).toList()) {
        Files.delete(path);
      }
    }
  }

  private static Path commandDirectory(Path objectXml, String name) {
    if (name == null || name.isBlank()) {
      return null;
    }
    String fileName = objectXml.getFileName().toString();
    if (!fileName.endsWith(".xml")) {
      return null;
    }
    Path objectDir = objectXml.resolveSibling(fileName.substring(0, fileName.length() - 4));
    return objectDir.resolve("Commands").resolve(name);
  }
}
