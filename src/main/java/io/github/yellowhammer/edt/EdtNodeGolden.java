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
package io.github.yellowhammer.edt;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Новый узел объекта 1С:EDT из эталона, который записала сама 1С:EDT.
 *
 * У узла много свойств, которые 1С:EDT пишет даже у нового: группа команды,
 * значения по умолчанию у реквизита. Если их не записать, 1С:EDT подставит
 * значения своей схемы, а они не всегда совпадают с тем, что ставит платформа.
 * Поэтому узел не собирается по схеме, а берётся готовым.
 *
 * Эталоны лежат в сборке по классам метамодели:
 * {@code edt-golden/Nodes/CatalogCommand.xml}. Их записала 1С:EDT 2026.1 при
 * импорте выгрузки платформы 8.3.27, где у объекта каждого вида было по узлу
 * каждого вида: платформа при загрузке дописала узлам свои значения по
 * умолчанию, а 1С:EDT переложила их в свой формат. Узел записан на уровне
 * свойства объекта, с отступом в два пробела.
 */
final class EdtNodeGolden {

  private static final String NODES = "Nodes/";
  private static final String GOLDEN_INDENT = "  ";
  private static final Pattern NAME = Pattern.compile("<name>([^<]+)</name>");

  private EdtNodeGolden() {
  }

  /**
   * Есть ли эталон узла такого класса.
   *
   * @param kind класс узла в метамодели: {@code CatalogCommand}
   * @return {@code true}, если эталон есть в сборке
   */
  static boolean exists(String kind) {
    return EdtNodeGolden.class.getResource("/edt-golden/" + NODES + kind + ".xml") != null;
  }

  /**
   * Разметка нового узла.
   *
   * @param kind класс узла в метамодели
   * @param name имя узла; им же становится подпись
   * @param language код языка подписи
   * @param indent отступ узла в файле
   * @param eol перевод строки файла
   * @param seed зерно идентификаторов узла
   * @return узел без перевода строки после закрывающего тега
   * @throws IOException если эталон не читается
   */
  static String node(String kind, String name, String language, String indent, String eol, String seed)
      throws IOException {
    if (!exists(kind)) {
      throw new IllegalArgumentException("Узлы вида " + kind + " в проекте 1С:EDT не создаются: нет эталона.");
    }
    String golden = EdtObjectScaffold.golden(NODES + kind + ".xml").replace("\r\n", "\n");
    Matcher proto = NAME.matcher(golden);
    if (!proto.find()) {
      throw new IllegalStateException("В эталоне узла " + kind + " нет имени");
    }
    String text = EdtObjectScaffold.retargeted(EdtObjectScaffold.renamed(golden, proto.group(1), name), language);
    text = EdtObjectScaffold.freshUuids(text, seed);

    StringBuilder node = new StringBuilder();
    for (String line : text.strip().split("\n")) {
      if (!node.isEmpty()) {
        node.append(eol);
      }
      node.append(indent).append(line.startsWith(GOLDEN_INDENT) ? line.substring(GOLDEN_INDENT.length()) : line);
    }
    return node.toString();
  }
}
