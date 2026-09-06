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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.yellowhammer.designerxml.SchemaVersion;
import io.github.yellowhammer.designerxml.cf.MdNamedPropertyDto;
import io.github.yellowhammer.designerxml.cf.MdObjectPropertiesDto;
import io.github.yellowhammer.designerxml.cf.MdObjectPropertiesEdit;

/**
 * Принадлежность и состояния свойств заимствованных объектов совпадают в двух форматах.
 *
 * Одно и то же демо-расширение лежит выгрузкой конфигуратора и проектом EDT:
 * у выгрузки состояния видны по наличию свойства и записям InternalInfo, у
 * проекта EDT по блоку extension. Панель получает одинаковый ответ.
 */
class EdtAdoptedStatesTest {

  private static EdtModel model;
  private static Path edtSource;
  private static Path designerCfe;

  @TempDir
  Path temp;

  @BeforeAll
  static void locate() throws Exception {
    model = EdtModel.bundled();
    edtSource = Path.of(System.getProperty("fixtures.ssl31edt.root"), "ssl31._ДемоРасширение", "src");
    designerCfe = Path.of(System.getProperty("fixtures.ssl31.root"), "src", "cfe", "_ДемоРасширение");
    assertThat(edtSource).isDirectory();
    assertThat(designerCfe).isDirectory();
  }

  /** Состояния под именами узлов: у самого объекта пустое имя, у реквизитов их имена. */
  private static Map<String, String> flatten(MdObjectPropertiesDto dto) {
    Map<String, String> flat = new TreeMap<>();
    put(flat, "", dto.objectBelonging, dto.propertyStates);
    for (List<MdNamedPropertyDto> nodes : List.of(
        list(dto.attributes), list(dto.tabularSections), list(dto.dimensions), list(dto.resources),
        list(dto.enumValues), list(dto.commands), list(dto.accountingFlags))) {
      for (MdNamedPropertyDto node : nodes) {
        put(flat, node.name, node.objectBelonging, node.propertyStates);
        for (MdNamedPropertyDto nested : list(node.attributes)) {
          put(flat, node.name + "/" + nested.name, nested.objectBelonging, nested.propertyStates);
        }
      }
    }
    return flat;
  }

  private static List<MdNamedPropertyDto> list(List<MdNamedPropertyDto> nodes) {
    return nodes == null ? List.of() : nodes;
  }

  private static void put(Map<String, String> flat, String node, String belonging, Map<String, String> states) {
    if (belonging != null) {
      flat.put(node + " belonging", belonging);
    }
    if (states != null) {
      states.forEach((property, state) -> {
        // Тип, дополненный расширением, у выгрузки многозначен, у EDT описан узлом; состав
        // подсистемы выгрузка пишет всегда и «изменён» у него не отмечает: сравнивать нечего
        boolean incomparable = (property.equals("type") && state.equals("MultiState")) || property.equals("content");
        if (!incomparable) {
          flat.put(node + " " + property, state);
        }
      });
    }
  }

  @Test
  void состоянияЗаимствованныхОбъектовСовпадаютСВыгрузкой() throws Exception {
    List<String> compared = new ArrayList<>();
    List<String> mismatches = new ArrayList<>();
    try (Stream<Path> files = Files.walk(edtSource)) {
      for (Path mdo : files.filter(path -> path.toString().endsWith(".mdo")).sorted().toList()) {
        Path relative = edtSource.relativize(mdo.getParent());
        if (relative.getNameCount() != 2) {
          continue;
        }
        Path designer = designerCfe.resolve(relative.getName(0).toString())
            .resolve(relative.getName(1) + ".xml");
        if (!Files.isRegularFile(designer)) {
          continue;
        }
        MdObjectPropertiesDto edt = EdtObjectProperties.readDto(mdo, model);
        if (!"Adopted".equals(edt.objectBelonging)) {
          continue;
        }
        MdObjectPropertiesDto written;
        try {
          written = MdObjectPropertiesEdit.readDto(designer, SchemaVersion.V2_21);
        } catch (IllegalArgumentException unsupported) {
          // Вид объекта выгрузка не читает: сравнивать нечего
          continue;
        }
        compared.add(relative.toString());
        Map<String, String> left = flatten(edt);
        Map<String, String> right = flatten(written);
        if (!left.equals(right)) {
          mismatches.add(relative + ": EDT " + left + " / выгрузка " + right);
        }
      }
    }
    assertThat(compared).hasSizeGreaterThan(20);
    assertThat(mismatches).isEmpty();
  }

  private Path copy(Path source) throws Exception {
    Path target = temp.resolve(source.getFileName());
    Files.copy(source, target);
    return target;
  }

  private static String text(Path file) throws Exception {
    return Files.readString(file, StandardCharsets.UTF_8).replace("\r\n", "\n");
  }

  /** Разметка сверена с файлом, который EDT записала при импорте той же выгрузки. */
  @Test
  void новыйСинонимЗаимствованногоОбъектаПомечаетсяИзменённым() throws Exception {
    Path catalog = copy(Path.of("src/test/resources/edt-extension/Основа.Надстройка/src/Catalogs/Товары/Товары.mdo"));
    MdObjectPropertiesDto dto = EdtObjectProperties.readDto(catalog, model);
    assertThat(dto.objectBelonging).isEqualTo("Adopted");
    assertThat(dto.propertyStates).isNull();

    dto.synonymRu = "Товары из расширения";
    EdtObjectWriter.writeDto(catalog, dto, model);

    assertThat(text(catalog)).contains("""
          <name>Товары</name>
          <synonym>
            <key>ru</key>
            <value>Товары из расширения</value>
          </synonym>
          <objectBelonging>Adopted</objectBelonging>
          <extension xsi:type="mdclassExtension:CatalogExtension">
            <synonym>Extended</synonym>
          </extension>
        </mdclass:Catalog>
        """);
    MdObjectPropertiesDto written = EdtObjectProperties.readDto(catalog, model);
    assertThat(written.synonymRu).isEqualTo("Товары из расширения");
    assertThat(written.propertyStates).isEqualTo(Map.of("synonym", "Extended"));
  }

  @Test
  void состояниеВстаётВБлокПоПорядкуКлассаРасширения() throws Exception {
    Path register = copy(edtSource.resolve("InformationRegisters/_ДемоЗаведующиеМестамиХранения/_ДемоЗаведующиеМестамиХранения.mdo"));
    MdObjectPropertiesDto dto = EdtObjectProperties.readDto(register, model);
    assertThat(dto.propertyStates).containsEntry("writeMode", "Checked").containsEntry("recordSetModule", "Extended");

    dto.synonymRu = "Заведующие из расширения";
    dto.register.writeMode = "RECORDER_SUBORDINATE";
    EdtObjectWriter.writeDto(register, dto, model);

    assertThat(text(register)).contains("""
          <extension xsi:type="mdclassExtension:InformationRegisterExtension">
            <synonym>Extended</synonym>
            <informationRegisterPeriodicity>Checked</informationRegisterPeriodicity>
            <writeMode>Extended</writeMode>
            <recordSetModule>Extended</recordSetModule>
            <managerModule>Extended</managerModule>
          </extension>
        """);
    MdObjectPropertiesDto written = EdtObjectProperties.readDto(register, model);
    assertThat(written.propertyStates).containsEntry("synonym", "Extended").containsEntry("writeMode", "Extended")
        .containsEntry("informationRegisterPeriodicity", "Checked");
  }

  @Test
  void комментарийЗаимствованногоСостоянияНеПолучает() throws Exception {
    Path catalog = copy(Path.of("src/test/resources/edt-extension/Основа.Надстройка/src/Catalogs/Товары/Товары.mdo"));
    MdObjectPropertiesDto dto = EdtObjectProperties.readDto(catalog, model);
    dto.comment = "Заимствован для формы";
    EdtObjectWriter.writeDto(catalog, dto, model);

    assertThat(text(catalog))
        .contains("<comment>Заимствован для формы</comment>")
        .contains("<extension xsi:type=\"mdclassExtension:CatalogExtension\"/>");
    assertThat(EdtObjectProperties.readDto(catalog, model).propertyStates).isNull();
  }

  @Test
  void типЗаимствованногоРеквизитаНеПравится() throws Exception {
    Path catalog = copy(edtSource.resolve("Catalogs/_ДемоПартнеры/_ДемоПартнеры.mdo"));
    MdObjectPropertiesDto dto = EdtObjectProperties.readDto(catalog, model);
    MdNamedPropertyDto adopted = dto.attributes.stream()
        .filter(attribute -> "Adopted".equals(attribute.objectBelonging))
        .findFirst()
        .orElseThrow();
    adopted.type = new io.github.yellowhammer.designerxml.cf.MdTypeDescriptionDto();
    adopted.type.types = List.of("String");

    assertThatThrownBy(() -> EdtObjectWriter.writeDto(catalog, dto, model))
        .hasMessageContaining("Тип заимствованного реквизита правится в расширяемой конфигурации");
  }

  @Test
  void свойОбъектБезСостояний() throws Exception {
    Path own = Path.of(System.getProperty("fixtures.ssl31edt.root"), "ssl31", "src", "Catalogs", "Валюты", "Валюты.mdo");
    MdObjectPropertiesDto dto = EdtObjectProperties.readDto(own, model);
    assertThat(dto.objectBelonging).isNull();
    assertThat(dto.propertyStates).isNull();
    Path designer = Path.of(System.getProperty("fixtures.ssl31.root"), "src", "cf", "Catalogs", "Валюты.xml");
    MdObjectPropertiesDto written = MdObjectPropertiesEdit.readDto(designer, SchemaVersion.V2_21);
    assertThat(written.objectBelonging).isNull();
    assertThat(written.propertyStates).isNull();
  }
}
