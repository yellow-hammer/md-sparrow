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

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import io.github.yellowhammer.designerxml.SchemaVersion;
import io.github.yellowhammer.designerxml.cf.MdNamedPropertyDto;
import io.github.yellowhammer.designerxml.cf.MdObjectPropertiesDto;
import io.github.yellowhammer.designerxml.cf.MdObjectPropertiesEdit;
import io.github.yellowhammer.designerxml.cf.MdObjectStructureDto;
import io.github.yellowhammer.designerxml.cf.MdObjectStructureRead;
import io.github.yellowhammer.designerxml.cf.MdTypeDescriptionDto;

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
      "ChartsOfCharacteristicTypes", "ChartsOfCharacteristicTypes",
      "Constants", "Constants",
      "SessionParameters", "SessionParameters",
      "CommonAttributes", "CommonAttributes",
      "EventSubscriptions", "EventSubscriptions",
      "CommonCommands", "CommonCommands");

  /** Описания типов сравниваются по значениям. */
  private static final Gson STATE = new Gson();

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
    state.append("|type=").append(STATE.toJson(node.type));
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
          compare(mismatches, name, "синоним", edt.synonym, designer.synonym);
          compare(mismatches, name, "типы объекта", objectTypes(edt), objectTypes(designer));
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

  /** Виды со списками ссылок: состав, использование, движения, ввод на основании. */
  private static final List<String> REF_LIST_DIRECTORIES = List.of(
      "Subsystems", "FunctionalOptions", "FunctionalOptionsParameters", "FilterCriteria", "Sequences",
      "CommonAttributes", "DocumentJournals", "Catalogs", "Documents", "ChartsOfCharacteristicTypes",
      "ChartsOfAccounts", "ChartsOfCalculationTypes", "ExchangePlans", "BusinessProcesses", "Tasks");

  @Test
  void спискиСсылокСовпадают() throws Exception {
    List<String> mismatches = new ArrayList<>();
    int filled = 0;

    for (String directory : REF_LIST_DIRECTORIES) {
      try (var children = java.nio.file.Files.list(edtSource.resolve(directory))) {
        for (Path project : children.filter(java.nio.file.Files::isDirectory).sorted().toList()) {
          String name = project.getFileName().toString();
          Path objectMdo = project.resolve(name + ".mdo");
          Path objectXml = designerCf.resolve(directory).resolve(name + ".xml");
          if (!java.nio.file.Files.isRegularFile(objectMdo) || !java.nio.file.Files.isRegularFile(objectXml)) {
            continue;
          }

          Map<String, List<String>> edt = refLists(EdtObjectProperties.readDto(objectMdo, model));
          Map<String, List<String>> designer = refLists(MdObjectPropertiesEdit.readDto(objectXml, SchemaVersion.V2_21));
          compare(mismatches, directory + "/" + name, "списки ссылок", edt, designer);
          filled += (int) edt.values().stream().filter(values -> !values.isEmpty()).count();
        }
      }
    }

    assertThat(filled).as("непустых списков").isGreaterThan(300);
    assertThat(mismatches).as("расхождения списков").isEmpty();
  }

  /** Списки ссылок объекта и его вида, состав общего реквизита строками. */
  private static Map<String, List<String>> refLists(MdObjectPropertiesDto dto) throws ReflectiveOperationException {
    Map<String, List<String>> lists = new java.util.TreeMap<>();
    for (String field : List.of("contentRefs", "nestedSubsystems", "documents", "registerRecords")) {
      lists.put(field, strings(MdObjectPropertiesDto.class.getField(field).get(dto)));
    }
    lists.put("contentMembers", dto.contentMembers == null ? List.of() : dto.contentMembers.stream()
        .map(member -> member.ref + "|" + member.mode + "|" + member.conditionalSeparation)
        .toList());
    for (Field field : MdObjectPropertiesDto.class.getFields()) {
      Object bridge = field.get(dto);
      if (bridge == null || !field.getType().getSimpleName().endsWith("PropertiesDto")) {
        continue;
      }
      for (Field property : bridge.getClass().getFields()) {
        if (property.getType() == List.class && !property.getName().equals("extendable")) {
          lists.put(field.getName() + "." + property.getName(), strings(property.get(bridge)));
        }
      }
    }
    return lists;
  }

  private static List<String> strings(Object list) {
    return list == null ? List.of() : ((List<?>) list).stream().map(String::valueOf).toList();
  }

  /** Свойства видов без своего блока: словарь значений и их описания. */
  @Test
  void свойстваВидовБезСвоегоБлокаСовпадают() throws Exception {
    List<String> mismatches = new ArrayList<>();
    int values = 0;

    List<Path> directories;
    try (var children = java.nio.file.Files.list(edtSource)) {
      directories = children.filter(java.nio.file.Files::isDirectory).sorted().toList();
    }
    for (Path directory : directories) {
      try (var children = java.nio.file.Files.list(directory)) {
        for (Path project : children.filter(java.nio.file.Files::isDirectory).sorted().toList()) {
          String name = project.getFileName().toString();
          Path objectMdo = project.resolve(name + ".mdo");
          Path objectXml = designerCf.resolve(directory.getFileName().toString()).resolve(name + ".xml");
          if (!java.nio.file.Files.isRegularFile(objectMdo) || !java.nio.file.Files.isRegularFile(objectXml)) {
            continue;
          }
          MdObjectPropertiesDto designer = MdObjectPropertiesEdit.readDto(objectXml, SchemaVersion.V2_21);
          MdObjectPropertiesDto edt = EdtObjectProperties.readDto(objectMdo, model);
          String object = directory.getFileName() + "/" + name;
          compare(mismatches, object, "словарь свойств", texts(edt.scalars), texts(designer.scalars));
          compare(mismatches, object, "описания свойств", STATE.toJson(sorted(edt.scalarMeta)),
              STATE.toJson(sorted(designer.scalarMeta)));
          values += designer.scalars == null ? 0 : designer.scalars.size();
        }
      }
    }

    assertThat(values).as("сверено значений").isGreaterThan(1500);
    assertThat(mismatches).as("расхождения свойств").isEmpty();
  }

  private static Map<String, String> texts(Map<String, Object> scalars) {
    Map<String, String> texts = new java.util.TreeMap<>();
    if (scalars != null) {
      scalars.forEach((key, value) -> texts.put(key, String.valueOf(value)));
    }
    return texts;
  }

  private static <T> Map<String, T> sorted(Map<String, T> map) {
    return map == null ? Map.of() : new java.util.TreeMap<>(map);
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
          // Имя файла содержимого у форматов своё, а имя и вид формы обязаны совпадать
          compare(mismatches, name, "формы",
              edt.forms.stream().map(item -> item.name + ": " + item.formType).toList(),
              designer.forms.stream().map(item -> item.name + ": " + item.formType).toList());
          compare(mismatches, name, "команды", edt.commands, designer.commands);
          // Имя файла содержимого у форматов своё, а имя и вид макета обязаны совпадать
          compare(mismatches, name, "макеты",
              edt.templates.stream().map(item -> item.name + ": " + item.templateType).toList(),
              designer.templates.stream().map(item -> item.name + ": " + item.templateType).toList());
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

  @Test
  void стандартныеТабличныеЧастиСовпадают() throws Exception {
    for (String object : List.of(
        "ChartsOfAccounts/_ДемоОсновной", "ChartsOfCalculationTypes/_ДемоОсновныеНачисления")) {
      String name = object.substring(object.indexOf('/') + 1);
      MdObjectStructureDto edt = EdtObjectStructure.read(edtSource.resolve(object).resolve(name + ".mdo"), model);
      MdObjectStructureDto designer = MdObjectStructureRead.read(designerCf.resolve(object + ".xml"), SchemaVersion.V2_21);

      assertThat(standardTabularSections(edt)).as(object)
          .isNotEmpty()
          .isEqualTo(standardTabularSections(designer));
    }
  }

  /** Стандартные табличные части с подписями: порядок частей и подписей у форматов свой. */
  private static List<String> standardTabularSections(MdObjectStructureDto dto) {
    return dto.standardTabularSections.stream()
        .map(section -> section.name + ": " + section.synonym + " "
            + new java.util.TreeMap<>(section.standardAttributeSynonyms))
        .sorted()
        .toList();
  }

  /** Описания типов в свойствах вида объекта: тип константы, источник подписки. */
  private static Map<String, String> objectTypes(MdObjectPropertiesDto dto) throws ReflectiveOperationException {
    Map<String, String> types = new java.util.TreeMap<>();
    for (Field field : MdObjectPropertiesDto.class.getFields()) {
      Object bridge = field.get(dto);
      if (bridge == null || !field.getType().getSimpleName().endsWith("PropertiesDto")) {
        continue;
      }
      for (Field property : bridge.getClass().getFields()) {
        if (property.getType() == MdTypeDescriptionDto.class) {
          types.put(property.getName(), STATE.toJson(property.get(bridge)));
        }
      }
    }
    return types;
  }

  private static void compare(List<String> mismatches, String object, String what, Object edt, Object designer) {
    if (!edt.equals(designer)) {
      mismatches.add("%s: %s - EDT %s, конфигуратор %s".formatted(object, what, edt, designer));
    }
  }
}
