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
package io.github.yellowhammer.designerxml;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Всё, что записала платформа, проходит проверку по XSD: каждый файл описания объекта выгрузки ssl31
 * (конфигурация, расширения, внешние обработки и отчёты — формат 2.20) и эталоны samples-1c-platform всех форматов.
 */
class XmlValidatorPlatformOutputTest {

  private static Path xsdRoot() {
    String root = System.getProperty("xsd.root");
    assertThat(root).as("xsd.root").isNotBlank();
    return Path.of(root);
  }

  @Test
  void ssl31ObjectsPass() throws IOException {
    List<Path> files = objectDescriptions(Ssl31SubmodulePaths.projectRoot().resolve("src"));

    assertThat(files).hasSizeGreaterThan(5000);
    assertThat(failures(files, SchemaVersion.V2_20)).isEmpty();
  }

  @ParameterizedTest
  @EnumSource(SchemaVersion.class)
  void snapshotsPass(SchemaVersion version) throws IOException {
    String root = System.getProperty("samples.root");
    assertThat(root).as("samples.root").isNotBlank();
    List<Path> files = objectDescriptions(Path.of(root, "snapshots", version.metadataObjectVersionAttribute()));

    assertThat(kinds(files)).as("виды объектов эталона").hasSizeGreaterThan(15);
    assertThat(failures(files, version)).isEmpty();
  }

  /** Файлы описаний объектов: всё {@code *.xml} вне {@code Ext}, кроме {@code ConfigDumpInfo.xml}. */
  private static List<Path> objectDescriptions(Path dir) throws IOException {
    assertThat(dir).isDirectory();
    try (Stream<Path> walk = Files.walk(dir)) {
      return walk
        .filter(Files::isRegularFile)
        .filter(p -> p.getFileName().toString().endsWith(".xml"))
        .filter(p -> !p.getFileName().toString().equals("ConfigDumpInfo.xml"))
        .filter(p -> {
          for (Path part : dir.relativize(p)) {
            if (part.toString().equals("Ext")) {
              return false;
            }
          }
          return true;
        })
        .sorted()
        .toList();
    }
  }

  /** Подкаталоги видов ({@code Catalogs}, {@code Roles}, …): эталон должен покрывать разные виды объектов. */
  private static Set<String> kinds(List<Path> files) {
    Set<String> kinds = new TreeSet<>();
    for (Path file : files) {
      kinds.add(file.getParent().getFileName().toString());
    }
    return kinds;
  }

  private static List<String> failures(List<Path> files, SchemaVersion version) {
    List<String> failures = new ArrayList<>();
    for (Path file : files) {
      try {
        XmlValidator.validate(file, version, xsdRoot());
      } catch (Exception e) {
        failures.add(file + ": " + e.getMessage());
      }
    }
    return failures;
  }
}
