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

import io.github.yellowhammer.designerxml.Cancellation;
import io.github.yellowhammer.designerxml.SchemaVersion;

import jakarta.xml.bind.JAXBException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Дерево подсистем конфигурации с их составом: одна операция вместо чтения каждой подсистемы
 * по отдельности. Вложенность берётся из {@code ChildObjects/Subsystem} самой подсистемы.
 */
public final class SubsystemTreeBuilder {

  private SubsystemTreeBuilder() {
  }

  /**
   * Узел дерева подсистем.
   */
  public record SubsystemNodeDto(
    String name,
    /** Абсолютный нормализованный путь к XML подсистемы. */
    String xmlPath,
    /** Ссылки на объекты состава ({@code Catalog.Номенклатура}). */
    List<String> contentRefs,
    List<SubsystemNodeDto> children
  ) {
  }

  /**
   * @param configurationXml {@code Configuration.xml} конфигурации или расширения
   * @return подсистемы верхнего уровня с вложенными
   */
  public static List<SubsystemNodeDto> build(Path configurationXml, SchemaVersion version)
    throws JAXBException, IOException {
    Path root = configurationXml.toAbsolutePath().normalize().getParent();
    if (root == null) {
      throw new IllegalArgumentException("Configuration.xml without parent directory");
    }
    List<SubsystemNodeDto> out = new ArrayList<>();
    Set<Path> visited = new HashSet<>();
    for (String name : ConfigurationChildObjectLister.listNames(configurationXml, version, "Subsystem")) {
      SubsystemNodeDto node = readNode(root.resolve("Subsystems").resolve(name + ".xml"), version, visited);
      if (node != null) {
        out.add(node);
      }
    }
    return out;
  }

  private static SubsystemNodeDto readNode(Path xml, SchemaVersion version, Set<Path> visited)
    throws JAXBException, IOException {
    Cancellation.checkpoint();
    Path normalized = xml.toAbsolutePath().normalize();
    if (!Files.isRegularFile(normalized) || !visited.add(normalized)) {
      return null;
    }
    MdObjectPropertiesDto dto = MdObjectPropertiesEdit.readDto(normalized, version);
    List<SubsystemNodeDto> children = new ArrayList<>();
    for (String nested : dto.nestedSubsystems) {
      // Вложенные подсистемы лежат в каталоге владельца: Subsystems/<Имя>/Subsystems/<Вложенная>.xml
      Path nestedXml = normalized
        .getParent()
        .resolve(dto.internalName)
        .resolve("Subsystems")
        .resolve(nested + ".xml");
      SubsystemNodeDto child = readNode(nestedXml, version, visited);
      if (child != null) {
        children.add(child);
      }
    }
    return new SubsystemNodeDto(
      dto.internalName,
      normalized.toString(),
      new ArrayList<>(dto.contentRefs),
      children);
  }
}
