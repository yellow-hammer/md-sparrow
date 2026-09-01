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

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import io.github.yellowhammer.designerxml.SchemaVersion;
import io.github.yellowhammer.designerxml.cf.MdNamedPropertyDto;
import io.github.yellowhammer.designerxml.cf.MdObjectPropertiesDto;
import io.github.yellowhammer.designerxml.cf.MdObjectPropertiesEdit;
import io.github.yellowhammer.designerxml.cf.MdObjectStructureDto;
import io.github.yellowhammer.designerxml.cf.MdObjectStructureRead;

/**
 * Свойства объекта в двух форматах сверяются между собой.
 *
 * Библиотека одна и та же, поэтому имя, синоним и состав подчинённых узлов
 * обязаны совпасть. Свойства самих узлов сверяются там, где форматы описывают
 * их одинаково.
 */
class EdtPropertiesMatchDesignerTest {

  /** Вид объекта - каталоги в проекте EDT и в выгрузке конфигуратора. */
  private static final Map<String, String> DIRECTORIES = Map.of(
      "Catalogs", "Catalogs",
      "Documents", "Documents",
      "Enums", "Enums",
      "InformationRegisters", "InformationRegisters",
      "ChartsOfCharacteristicTypes", "ChartsOfCharacteristicTypes");

  /** Свойства узлов, записанные в обоих форматах одинаково. */
  private static final List<String> NODE_PROPERTIES =
      List.of("fillChecking", "indexing", "fullTextSearch", "dataHistory", "use");

  private static Path edtSource;
  private static Path designerCf;
  private static EdtModel model;

  @BeforeAll
  static void locate() throws Exception {
    edtSource = Path.of(System.getProperty("fixtures.ssl31edt.root"), "ssl31", "src");
    designerCf = Path.of(System.getProperty("fixtures.ssl31.root"), "src", "cf");
    assertThat(edtSource).exists();
    assertThat(designerCf).exists();
    model = EdtModel.bundled();
  }

  private static List<String> names(List<MdNamedPropertyDto> nodes) {
    return nodes == null ? List.of() : nodes.stream().map(node -> node.name).toList();
  }

  /** Значения свойств узла, которые сверяются между форматами. */
  private static String nodeState(MdNamedPropertyDto node) {
    StringBuilder state = new StringBuilder(node.name);
    for (String property : NODE_PROPERTIES) {
      try {
        Object value = MdNamedPropertyDto.class.getField(property).get(node);
        state.append('|').append(property).append('=').append(value == null ? "" : value);
      } catch (ReflectiveOperationException error) {
        throw new IllegalStateException(property, error);
      }
    }
    return state.toString();
  }

  @Test
  void свойстваОбъектовСовпадают() throws Exception {
    List<String> mismatches = new ArrayList<>();
    int checked = 0;

    for (Map.Entry<String, String> directory : DIRECTORIES.entrySet()) {
      Path edtDirectory = edtSource.resolve(directory.getKey());
      try (var children = java.nio.file.Files.list(edtDirectory)) {
        for (Path project : children.filter(java.nio.file.Files::isDirectory).sorted().toList()) {
          String name = project.getFileName().toString();
          Path objectMdo = project.resolve(name + ".mdo");
          Path objectXml = designerCf.resolve(directory.getValue()).resolve(name + ".xml");
          if (!java.nio.file.Files.isRegularFile(objectMdo) || !java.nio.file.Files.isRegularFile(objectXml)) {
            continue;
          }

          MdObjectPropertiesDto edt = EdtObjectProperties.readDto(objectMdo, model);
          MdObjectPropertiesDto designer = MdObjectPropertiesEdit.readDto(objectXml, SchemaVersion.V2_21);
          checked++;

          compare(mismatches, name, "вид", edt.kind, designer.kind);
          compare(mismatches, name, "имя", edt.internalName, designer.internalName);
          compare(mismatches, name, "синоним", edt.synonymRu, designer.synonymRu);
          compare(mismatches, name, "реквизиты", names(edt.attributes), names(designer.attributes));
          compare(mismatches, name, "табличные части",
              names(edt.tabularSections), names(designer.tabularSections));
          compare(mismatches, name, "значения перечисления",
              names(edt.enumValues), names(designer.enumValues));
          compare(mismatches, name, "измерения", names(edt.dimensions), names(designer.dimensions));
          compare(mismatches, name, "ресурсы", names(edt.resources), names(designer.resources));

          if (edt.attributes != null && designer.attributes != null
              && edt.attributes.size() == designer.attributes.size()) {
            for (int index = 0; index < edt.attributes.size(); index++) {
              compare(mismatches, name, "реквизит",
                  nodeState(edt.attributes.get(index)), nodeState(designer.attributes.get(index)));
            }
          }
        }
      }
    }

    assertThat(checked).as("сверено объектов").isGreaterThan(200);
    assertThat(mismatches).as("расхождения свойств").isEmpty();
  }

  @Test
  void строениеОбъектовСовпадает() throws Exception {
    List<String> mismatches = new ArrayList<>();
    int checked = 0;

    for (Map.Entry<String, String> directory : DIRECTORIES.entrySet()) {
      try (var children = java.nio.file.Files.list(edtSource.resolve(directory.getKey()))) {
        for (Path project : children.filter(java.nio.file.Files::isDirectory).sorted().toList()) {
          String name = project.getFileName().toString();
          Path objectMdo = project.resolve(name + ".mdo");
          Path objectXml = designerCf.resolve(directory.getValue()).resolve(name + ".xml");
          if (!java.nio.file.Files.isRegularFile(objectMdo) || !java.nio.file.Files.isRegularFile(objectXml)) {
            continue;
          }

          MdObjectStructureDto edt = EdtObjectStructure.read(objectMdo, model);
          MdObjectStructureDto designer = MdObjectStructureRead.read(objectXml, SchemaVersion.V2_21);
          checked++;

          compare(mismatches, name, "вид", edt.kind, designer.kind);
          compare(mismatches, name, "формы", edt.forms, designer.forms);
          compare(mismatches, name, "команды", edt.commands, designer.commands);
          compare(mismatches, name, "макеты", edt.templates, designer.templates);
          compare(mismatches, name, "значения", edt.values, designer.values);
          compare(mismatches, name, "стандартные реквизиты",
              List.copyOf(edt.standardAttributes), List.copyOf(designer.standardAttributes));
          compare(mismatches, name, "подписи стандартных реквизитов",
              edt.standardAttributeSynonyms, designer.standardAttributeSynonyms);
          compare(mismatches, name, "табличные части",
              edt.tabularSections.stream().map(section -> section.name).toList(),
              designer.tabularSections.stream().map(section -> section.name).toList());
          compare(mismatches, name, "реквизиты",
              edt.attributes.stream().map(node -> node.name).toList(),
              designer.attributes.stream().map(node -> node.name).toList());
        }
      }
    }

    assertThat(checked).as("сверено объектов").isGreaterThan(200);
    assertThat(mismatches).as("расхождения строения").isEmpty();
  }

  private static void compare(List<String> mismatches, String object, String what, Object edt, Object designer) {
    if (!edt.equals(designer)) {
      mismatches.add("%s: %s - EDT %s, конфигуратор %s".formatted(object, what, edt, designer));
    }
  }
}
