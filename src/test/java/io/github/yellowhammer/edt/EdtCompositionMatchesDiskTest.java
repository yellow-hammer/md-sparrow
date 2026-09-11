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

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import io.github.yellowhammer.designerxml.cf.ChildObjectEntry;

/** Прочитанный состав сверяется с каталогами объектов на диске. */
class EdtCompositionMatchesDiskTest {

  /** Вид объекта - каталог проекта. */
  private static final Map<String, String> DIRECTORIES = Map.of(
      "Catalog", "Catalogs",
      "Document", "Documents",
      "CommonModule", "CommonModules",
      "Enum", "Enums",
      "InformationRegister", "InformationRegisters");

  @Test
  void составСовпадаетСКаталогамиПроекта() throws Exception {
    Path project = Path.of(System.getProperty("fixtures.ssl31edt.root"), "ssl31");
    List<ChildObjectEntry> objects =
        EdtConfigurationReader.listChildObjects(
            project.resolve("src/Configuration/Configuration.mdo"), EdtModel.bundled());

    for (Map.Entry<String, String> entry : DIRECTORIES.entrySet()) {
      Path directory = project.resolve("src").resolve(entry.getValue());
      assertThat(directory).as("каталог вида %s", entry.getKey()).exists();

      Set<String> onDisk = new TreeSet<>();
      try (Stream<Path> children = Files.list(directory)) {
        children.filter(Files::isDirectory).map(path -> path.getFileName().toString()).forEach(onDisk::add);
      }

      Set<String> read = objects.stream()
          .filter(object -> object.objectType().equals(entry.getKey()))
          .map(ChildObjectEntry::name)
          .collect(java.util.stream.Collectors.toCollection(TreeSet::new));

      assertThat(read)
          .as("состав вида %s", entry.getKey())
          .isEqualTo(onDisk);
    }
  }
}
