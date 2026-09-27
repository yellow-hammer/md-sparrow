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
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.github.yellowhammer.designerxml.SchemaVersion;
import io.github.yellowhammer.designerxml.cf.FormatProjection;

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
  private static final String CHILD_OBJECTS = "ChildObjects";
  private static final String PROPERTIES = "Properties";
  private static final Pattern NAME = Pattern.compile("<name>([^<]+)</name>");
  /** Открывающий тег строки: имя свойства узла. */
  private static final Pattern OPENING = Pattern.compile("<(\\w+)");

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

  /**
   * Узел в формате платформы проекта.
   *
   * Эталон снят с выгрузки 8.3.27, а свойство, которого в формате проекта ещё
   * нет, 1С:EDT при импорте выгрузки этого формата не пишет: в файле остаётся
   * умолчание схемы. Так длина номера строки табличной части появилась в
   * формате 2.20, и в проекте платформы 8.3.24 её нет. Что знает формат,
   * говорит его модель: свойство EDT называется так же, как в выгрузке, со
   * строчной буквы. Проект без манифеста получает узел эталона как есть.
   *
   * @param node разметка узла из {@link #node}
   * @param objectMdo файл объекта {@code <проект>/src/<Вид>/<Имя>/<Имя>.mdo}
   * @param objectClass класс объекта: {@code Catalog}
   * @param owner вид узла-владельца или {@code null}, если узел принадлежит объекту
   * @param feature вид узла: {@code tabularSections}
   * @return узел без свойств, которых формат проекта не знает
   * @throws IOException если манифест проекта не читается
   */
  static String forProject(String node, Path objectMdo, String objectClass, String owner, String feature)
      throws IOException {
    Optional<SchemaVersion> format = projectFormat(objectMdo);
    if (format.isEmpty()) {
      return node;
    }
    List<String> path = new ArrayList<>(List.of(objectClass, CHILD_OBJECTS));
    if (owner != null) {
      path.add(designerNode(owner));
      path.add(CHILD_OBJECTS);
    }
    path.add(designerNode(feature));
    path.add(PROPERTIES);

    SchemaVersion[] all = SchemaVersion.values();
    SchemaVersion canonical = all[all.length - 1];
    // Свойства узла - строки на уровень глубже его открывающего тега
    String[] lines = node.split("\n", -1);
    String indent = lines[0].substring(0, lines[0].indexOf('<')) + GOLDEN_INDENT;
    List<String> kept = new ArrayList<>();
    String skipped = null;
    for (String line : lines) {
      if (skipped != null) {
        // Свойство в несколько строк уходит до своего закрывающего тега
        if (line.startsWith(indent + "</" + skipped + ">")) {
          skipped = null;
        }
        continue;
      }
      Matcher opening = OPENING.matcher(line);
      if (line.startsWith(indent) && opening.region(indent.length(), line.length()).lookingAt()) {
        String name = opening.group(1);
        List<String> element = new ArrayList<>(path);
        element.add(Character.toUpperCase(name.charAt(0)) + name.substring(1));
        if (FormatProjection.hasElement(canonical, element) && !FormatProjection.hasElement(format.get(), element)) {
          if (!line.contains("</" + name + ">") && !line.stripTrailing().endsWith("/>")) {
            skipped = name;
          }
          continue;
        }
      }
      kept.add(line);
    }
    return String.join("\n", kept);
  }

  /** Формат платформы проекта по {@code Runtime-Version} манифеста. */
  private static Optional<SchemaVersion> projectFormat(Path objectMdo) throws IOException {
    Path project = objectMdo.toAbsolutePath();
    // <проект>/src/<Вид>/<Имя>/<Имя>.mdo
    for (int level = 0; level < 4 && project != null; level++) {
      project = project.getParent();
    }
    if (project == null) {
      return Optional.empty();
    }
    return EdtProjectManifest.runtimeVersion(project).map(SchemaVersion::ofPlatform);
  }

  /** Узел в выгрузке: {@code tabularSections} - {@code TabularSection}. */
  private static String designerNode(String feature) {
    String single = feature.endsWith("s") ? feature.substring(0, feature.length() - 1) : feature;
    return Character.toUpperCase(single.charAt(0)) + single.substring(1);
  }
}
