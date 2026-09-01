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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import io.github.yellowhammer.designerxml.cf.ChildObjectEntry;
import io.github.yellowhammer.designerxml.cf.MetadataRefParser;
import io.github.yellowhammer.designerxml.cf.SubsystemTreeBuilder;

/**
 * Списки объектов конфигурации 1С:EDT.
 *
 * Панель свойств спрашивает их, когда предлагает выбор: владельца справочника,
 * тип реквизита, состав подсистемы. Ответы те же, что у выгрузки конфигуратора,
 * поэтому вызывающей программе форматы различать не приходится.
 */
public final class EdtConfigurationLists {

  private EdtConfigurationLists() {
  }

  /**
   * Объекты одного вида.
   *
   * @param configurationMdo файл конфигурации
   * @param model метамодель EDT
   * @param objectType вид объекта: {@code Catalog}
   * @return имена объектов в порядке файла
   * @throws IOException если файл не читается
   */
  public static List<String> names(Path configurationMdo, EdtModel model, String objectType) throws IOException {
    return EdtConfigurationReader.listChildObjects(configurationMdo, model).stream()
        .filter(entry -> entry.objectType().equals(objectType))
        .map(ChildObjectEntry::name)
        .toList();
  }

  /**
   * Объекты всех видов.
   *
   * @param configurationMdo файл конфигурации
   * @param model метамодель EDT
   * @return вид объекта - имена объектов
   * @throws IOException если файл не читается
   */
  public static Map<String, List<String>> all(Path configurationMdo, EdtModel model) throws IOException {
    Map<String, List<String>> objects = new LinkedHashMap<>();
    for (ChildObjectEntry entry : EdtConfigurationReader.listChildObjects(configurationMdo, model)) {
      objects.computeIfAbsent(entry.objectType(), key -> new ArrayList<>()).add(entry.name());
    }
    return objects;
  }

  /**
   * Ссылочные типы объектов конфигурации.
   *
   * @param configurationMdo файл конфигурации
   * @param model метамодель EDT
   * @return вид объекта - тексты ссылочных типов
   * @throws IOException если файл не читается
   */
  public static Map<String, List<String>> refTypes(Path configurationMdo, EdtModel model) throws IOException {
    Map<String, List<String>> byType = all(configurationMdo, model);
    Map<String, List<String>> refs = new LinkedHashMap<>();
    for (Map.Entry<String, String> suffix : MetadataRefParser.refTypeSuffixesByObjectType().entrySet()) {
      List<String> names = byType.get(suffix.getKey());
      if (names == null || names.isEmpty()) {
        continue;
      }
      List<String> types = new ArrayList<>();
      for (String name : names) {
        types.add(suffix.getValue() + "." + name);
      }
      refs.put(suffix.getKey(), types);
    }
    return refs;
  }

  /**
   * Дерево подсистем.
   *
   * Вложенные подсистемы в EDT лежат каталогами внутри родителя, а их состав
   * записан ссылками на объекты.
   *
   * @param configurationMdo файл конфигурации
   * @param model метамодель EDT
   * @return подсистемы верхнего уровня с вложенными
   * @throws IOException если файлы не читаются
   */
  public static List<SubsystemTreeBuilder.SubsystemNodeDto> subsystems(Path configurationMdo, EdtModel model)
      throws IOException {
    Path sourceRoot = configurationMdo.getParent().getParent();
    List<SubsystemTreeBuilder.SubsystemNodeDto> nodes = new ArrayList<>();
    for (String name : names(configurationMdo, model, "Subsystem")) {
      Path subsystemMdo = sourceRoot.resolve("Subsystems").resolve(name).resolve(name + ".mdo");
      if (Files.isRegularFile(subsystemMdo)) {
        nodes.add(subsystem(subsystemMdo));
      }
    }
    return nodes;
  }

  /** Подсистема со своим составом и вложенными подсистемами. */
  private static SubsystemTreeBuilder.SubsystemNodeDto subsystem(Path subsystemMdo) throws IOException {
    EdtObjectReader.EdtNode node = EdtObjectReader.read(subsystemMdo);
    List<SubsystemTreeBuilder.SubsystemNodeDto> children = new ArrayList<>();
    Path nested = subsystemMdo.getParent().resolve("Subsystems");
    if (Files.isDirectory(nested)) {
      try (Stream<Path> directories = Files.list(nested)) {
        for (Path child : directories.filter(Files::isDirectory).sorted().toList()) {
          Path childMdo = child.resolve(child.getFileName() + ".mdo");
          if (Files.isRegularFile(childMdo)) {
            children.add(subsystem(childMdo));
          }
        }
      }
    }
    return new SubsystemTreeBuilder.SubsystemNodeDto(
        node.name(),
        subsystemMdo.toAbsolutePath().normalize().toString(),
        node.list("content").stream().map(EdtObjectReader.EdtNode::value).filter(ref -> !ref.isEmpty()).toList(),
        children);
  }
}
