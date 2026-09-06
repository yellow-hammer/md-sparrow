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

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.yellowhammer.designerxml.SchemaVersion;
import io.github.yellowhammer.designerxml.cf.MdObjectPropertiesDto;
import io.github.yellowhammer.designerxml.cf.MdObjectPropertiesEdit;

/**
 * Какие свойства заимствованного узла расширение вправе менять.
 *
 * Список берётся из класса расширения метамодели EDT и одинаково ограничивает
 * запись в оба формата: свойство вне класса принадлежит расширяемой конфигурации.
 */
class EdtExtensionFeaturesTest {

  private static EdtModel model;

  @TempDir
  Path temp;

  @BeforeAll
  static void load() throws Exception {
    model = EdtModel.bundled();
  }

  private Path copy(Path source) throws Exception {
    Path target = temp.resolve(source.getFileName());
    Files.copy(source, target);
    return target;
  }

  @Test
  void свойстваОбъектаИУзлаИдутИзКлассаРасширения() {
    assertThat(EdtExtensionFeatures.ofObject(model, "catalog"))
        .contains("synonym", "objectModule", "hierarchical", "codeLength", "defaultListForm")
        .doesNotContain("useStandardCommands", "inputByString", "name", "comment");
    assertThat(EdtExtensionFeatures.ofNode(model, "attributes"))
        .contains("synonym", "type", "tooltip", "choiceForm")
        .doesNotContain("indexing", "fullTextSearch");
    assertThat(EdtExtensionFeatures.ofNode(model, "commands")).contains("group", "commandModule", "picture");
    assertThat(EdtExtensionFeatures.ofNode(model, "forms")).isEmpty();
    assertThat(EdtExtensionFeatures.ofObject(model, "commonModule")).contains("module", "server", "global");
  }

  @Test
  void спискиЗаполняютсяТолькоУЗаимствованных() throws Exception {
    Path edt = Path.of(System.getProperty("fixtures.ssl31edt.root"), "ssl31._ДемоРасширение", "src",
        "Catalogs", "_ДемоПартнеры", "_ДемоПартнеры.mdo");
    MdObjectPropertiesDto dto = EdtObjectProperties.readDto(edt, model);
    EdtExtensionFeatures.apply(dto, model);
    assertThat(dto.extendable).contains("synonym", "hierarchical");
    assertThat(dto.attributes).anySatisfy(attribute -> {
      assertThat(attribute.objectBelonging).isEqualTo("Adopted");
      assertThat(attribute.extendable).contains("synonym", "type");
    });

    Path own = Path.of(System.getProperty("fixtures.ssl31edt.root"), "ssl31", "src", "Catalogs", "Валюты", "Валюты.mdo");
    MdObjectPropertiesDto ownDto = EdtObjectProperties.readDto(own, model);
    EdtExtensionFeatures.apply(ownDto, model);
    assertThat(ownDto.extendable).isNull();
    assertThat(ownDto.attributes).allSatisfy(attribute -> assertThat(attribute.extendable).isNull());
  }

  @Test
  void свойствоВнеКлассаРасширенияНеПишетсяВВыгрузку() throws Exception {
    Path catalog = copy(Path.of(System.getProperty("fixtures.ssl31.root"), "src", "cfe", "_ДемоРасширение",
        "Catalogs", "_ДемоГруппыДоступаПартнеров.xml"));
    MdObjectPropertiesDto dto = MdObjectPropertiesEdit.readDto(catalog, SchemaVersion.V2_21);
    dto.catalog.useStandardCommands = !dto.catalog.useStandardCommands;
    assertThatThrownBy(() -> MdObjectPropertiesEdit.writeDto(
        catalog, SchemaVersion.V2_21, dto, EdtExtensionFeatures.byDesignerContainer(model, dto.kind)))
        .hasMessageContaining("UseStandardCommands")
        .hasMessageContaining("принадлежит расширяемой конфигурации");

    MdObjectPropertiesDto allowed = MdObjectPropertiesEdit.readDto(catalog, SchemaVersion.V2_21);
    allowed.catalog.hierarchical = true;
    MdObjectPropertiesEdit.writeDto(
        catalog, SchemaVersion.V2_21, allowed, EdtExtensionFeatures.byDesignerContainer(model, allowed.kind));
    String xml = Files.readString(catalog, StandardCharsets.UTF_8);
    assertThat(xml).contains("<Hierarchical>true</Hierarchical>")
        .contains("<xr:Property>Hierarchical</xr:Property>");
    assertThat(MdObjectPropertiesEdit.readDto(catalog, SchemaVersion.V2_21).propertyStates)
        .containsEntry("hierarchical", "Extended");
  }

  @Test
  void свойствоВнеКлассаРасширенияНеПишетсяВПроектEdt() throws Exception {
    Path catalog = copy(Path.of("src/test/resources/edt-extension/Основа.Надстройка/src/Catalogs/Товары/Товары.mdo"));
    MdObjectPropertiesDto dto = EdtObjectProperties.readDto(catalog, model);
    dto.catalog.useStandardCommands = !dto.catalog.useStandardCommands;
    assertThatThrownBy(() -> EdtObjectWriter.writeDto(catalog, dto, model))
        .hasMessageContaining("useStandardCommands")
        .hasMessageContaining("принадлежит расширяемой конфигурации");

    MdObjectPropertiesDto allowed = EdtObjectProperties.readDto(catalog, model);
    allowed.catalog.hierarchical = true;
    EdtObjectWriter.writeDto(catalog, allowed, model);
    String xml = Files.readString(catalog, StandardCharsets.UTF_8);
    assertThat(xml).contains("<hierarchical>true</hierarchical>").contains("<hierarchical>Extended</hierarchical>");
    assertThat(EdtObjectProperties.readDto(catalog, model).propertyStates).containsEntry("hierarchical", "Extended");
  }
}
