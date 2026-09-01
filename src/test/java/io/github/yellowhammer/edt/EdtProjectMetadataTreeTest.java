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

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import io.github.yellowhammer.designerxml.cf.ProjectMetadataTreeBuilder;
import io.github.yellowhammer.designerxml.cf.ProjectMetadataTreeDto;

/** Дерево метаданных рабочей области 1С:EDT. */
class EdtProjectMetadataTreeTest {

  private static Path workspace;
  private static ProjectMetadataTreeDto tree;

  @BeforeAll
  static void locate() throws Exception {
    workspace = Path.of(System.getProperty("fixtures.ssl31edt.root"));
    assertThat(workspace).exists();
    tree = ProjectMetadataTreeBuilder.build(workspace);
  }

  private static ProjectMetadataTreeDto.MetadataSourceDto source(String id) {
    return tree.sources().stream().filter(item -> item.id().equals(id)).findFirst().orElseThrow();
  }

  private static List<ProjectMetadataTreeDto.MetadataItemDto> items(
      ProjectMetadataTreeDto.MetadataSourceDto source,
      String groupId) {
    return source.groups().stream()
        .filter(group -> group.id().equals(groupId))
        .findFirst()
        .orElseThrow()
        .items();
  }

  @Test
  void формаОпределяетсяПоФайлам() {
    // Каталогов выгрузки конфигуратора в рабочей области нет: формат виден сам
    assertThat(tree.projectRoot()).isEqualTo(workspace.toAbsolutePath().normalize().toString());
    assertThat(tree.mainSchemaVersion()).matches("[0-9]{4}[.][0-9]+");
  }

  @Test
  void источникПроектаБезВерсииСхемыНоСПоставкой() {
    ProjectMetadataTreeDto.MetadataSourceDto main = source("main");
    // Версию схем заменяет метамодель из сборки, а поставка читается из файла рядом с описанием
    assertThat(main.schemaSupported()).isTrue();
    assertThat(main.schemaVersion()).isEmpty();
    // Демо-конфигурация на полной поддержке: возможность изменения не включена
    assertThat(main.support()).isEqualTo("locked");
    assertThat(main.supportEditingEnabled()).isFalse();
    assertThat(main.supportGeneration()).hasSize(16);
    ProjectMetadataTreeDto.MetadataItemDto currency = main.groups().stream()
        .flatMap(group -> group.items().stream())
        .filter(item -> item.objectType().equals("Catalog") && item.name().equals("Валюты"))
        .findFirst().orElseThrow();
    assertThat(currency.support()).isEqualTo("locked");
    // Язык описан узлом конфигурации без своего файла, но правило поддержки у него своё
    ProjectMetadataTreeDto.MetadataItemDto language = main.groups().stream()
        .flatMap(group -> java.util.stream.Stream.concat(
            group.items().stream(), group.subgroups().stream().flatMap(sub -> sub.items().stream())))
        .filter(item -> item.objectType().equals("Language"))
        .findFirst().orElseThrow();
    assertThat(language.support()).isEqualTo("locked");
    // У расширения файла поставки нет
    ProjectMetadataTreeDto.MetadataSourceDto extension = source("ssl31._ДемоРасширение");
    assertThat(extension.support()).isNull();
    assertThat(extension.supportGeneration()).isNull();
  }

  @Test
  void конфигурацияИдётПередРасширениями() {
    assertThat(tree.sources()).extracting(ProjectMetadataTreeDto.MetadataSourceDto::kind)
        .startsWith("main")
        .contains("extension");
    assertThat(source("main").label()).isEqualTo("БиблиотекаСтандартныхПодсистемДемо");
    assertThat(source("ssl31._ДемоРасширение").kind()).isEqualTo("extension");
  }

  @Test
  void объектыСсылаютсяНаСвоиФайлы() {
    List<ProjectMetadataTreeDto.MetadataItemDto> catalogs = items(source("main"), "catalogs");

    ProjectMetadataTreeDto.MetadataItemDto currencies = catalogs.stream()
        .filter(item -> item.name().equals("Валюты"))
        .findFirst()
        .orElseThrow();
    assertThat(currencies.relativePath()).isEqualTo("ssl31/src/Catalogs/Валюты/Валюты.mdo");
    assertThat(workspace.resolve(currencies.relativePath())).exists();
    assertThat(catalogs).allSatisfy(item ->
        assertThat(Files.exists(workspace.resolve(item.relativePath()))).as(item.name()).isTrue());
  }

  @Test
  void общийМодульОткрываетсяМодулем() {
    ProjectMetadataTreeDto.MetadataItemDto module = source("main").groups().stream()
        .flatMap(group -> group.subgroups().stream())
        .flatMap(subgroup -> subgroup.items().stream())
        .filter(item -> item.objectType().equals("CommonModule") && item.name().equals("ОбщегоНазначения"))
        .findFirst()
        .orElseThrow();

    assertThat(module.open().action()).isEqualTo("module");
    assertThat(module.open().relativePath())
        .isEqualTo("ssl31/src/CommonModules/ОбщегоНазначения/Module.bsl");
    assertThat(workspace.resolve(module.open().relativePath())).exists();
  }

  @Test
  void заимствованныеОбъектыРасширенияПомечены() {
    List<ProjectMetadataTreeDto.MetadataItemDto> catalogs =
        items(source("ssl31._ДемоРасширение"), "catalogs");

    assertThat(catalogs).isNotEmpty();
    assertThat(catalogs).extracting(ProjectMetadataTreeDto.MetadataItemDto::objectBelonging)
        .contains("Adopted");
  }
}
