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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.yellowhammer.designerxml.SchemaVersion;

/**
 * Правка свойств заимствованного объекта в выгрузке конфигуратора.
 *
 * Изменённое свойство получает запись состояния «Extended» в InternalInfo, а
 * свойство, которого у заимствованного объекта не было, встаёт на место по схеме.
 * Разметка сверена с тем, как то же расширение выгружает EDT.
 */
class AdoptedStatesPatchTest {

  private static final Path CFE = Path.of(System.getProperty("fixtures.ssl31.root"), "src", "cfe", "_ДемоРасширение");

  @TempDir
  Path temp;

  private Path copy(String relative) throws Exception {
    Path source = CFE.resolve(relative);
    Path target = temp.resolve(source.getFileName());
    Files.copy(source, target);
    return target;
  }

  private static String text(Path file) throws Exception {
    return Files.readString(file, StandardCharsets.UTF_8).replace("\r\n", "\n");
  }

  @Test
  void новыйСинонимВстаётПослеИмениИПомечаетсяИзменённым() throws Exception {
    Path catalog = copy("Catalogs/_ДемоГруппыДоступаПартнеров.xml");
    MdObjectPropertiesDto dto = MdObjectPropertiesEdit.readDto(catalog, SchemaVersion.V2_21);
    assertThat(dto.objectBelonging).isEqualTo(AdoptedStates.ADOPTED);
    assertThat(dto.propertyStates).isNull();

    dto.synonymRu = "Группы доступа партнёров из расширения";
    MdObjectPropertiesEdit.writeDto(catalog, SchemaVersion.V2_21, dto);

    String xml = text(catalog);
    assertThat(xml).contains("""
        		<Properties>
        			<ObjectBelonging>Adopted</ObjectBelonging>
        			<Name>_ДемоГруппыДоступаПартнеров</Name>
        			<Synonym>
        				<v8:item>
        					<v8:lang>ru</v8:lang>
        					<v8:content>Группы доступа партнёров из расширения</v8:content>
        				</v8:item>
        			</Synonym>
        			<Comment/>
        		</Properties>
        """);
    // Синоним расширение только переопределяет: платформа не держит для него записи состояния
    assertThat(xml).doesNotContain("PropertyState");
    MdObjectPropertiesDto written = MdObjectPropertiesEdit.readDto(catalog, SchemaVersion.V2_21);
    assertThat(written.synonymRu).isEqualTo("Группы доступа партнёров из расширения");
    assertThat(written.propertyStates).isEqualTo(Map.of("synonym", AdoptedStates.EXTENDED));
  }

  @Test
  void контролируемоеСвойствоМеняетсяНаМестеИСтановитсяИзменённым() throws Exception {
    Path module = copy("CommonModules/ОбщегоНазначенияПереопределяемый.xml");
    MdObjectPropertiesDto dto = MdObjectPropertiesEdit.readDto(module, SchemaVersion.V2_21);
    assertThat(dto.propertyStates).containsEntry("server", AdoptedStates.CHECKED)
        .containsEntry("module", AdoptedStates.EXTENDED);

    dto.commonModule.serverCall = true;
    MdObjectPropertiesEdit.writeDto(module, SchemaVersion.V2_21, dto);

    String xml = text(module);
    assertThat(xml).contains("""
        		<InternalInfo>
        			<xr:PropertyState>
        				<xr:Property>Module</xr:Property>
        				<xr:State>Extended</xr:State>
        			</xr:PropertyState>
        			<xr:PropertyState>
        				<xr:Property>ServerCall</xr:Property>
        				<xr:State>Extended</xr:State>
        			</xr:PropertyState>
        		</InternalInfo>
        """);
    assertThat(xml).contains("<ServerCall>true</ServerCall>");
    MdObjectPropertiesDto written = MdObjectPropertiesEdit.readDto(module, SchemaVersion.V2_21);
    assertThat(written.propertyStates).containsEntry("serverCall", AdoptedStates.EXTENDED)
        .containsEntry("server", AdoptedStates.CHECKED);
  }

  @Test
  void комментарийЗаимствованногоСостоянияНеПолучает() throws Exception {
    Path catalog = copy("Catalogs/_ДемоГруппыДоступаПартнеров.xml");
    MdObjectPropertiesDto dto = MdObjectPropertiesEdit.readDto(catalog, SchemaVersion.V2_21);
    dto.comment = "Заимствован для прав";
    MdObjectPropertiesEdit.writeDto(catalog, SchemaVersion.V2_21, dto);

    assertThat(text(catalog)).contains("<Comment>Заимствован для прав</Comment>").doesNotContain("PropertyState");
    assertThat(MdObjectPropertiesEdit.readDto(catalog, SchemaVersion.V2_21).propertyStates).isNull();
  }

  @Test
  void типЗаимствованногоРеквизитаНеПравится() throws Exception {
    Path catalog = copy("Catalogs/_ДемоПартнеры.xml");
    MdObjectPropertiesDto dto = MdObjectPropertiesEdit.readDto(catalog, SchemaVersion.V2_21);
    MdNamedPropertyDto adopted = dto.attributes.stream()
        .filter(attribute -> AdoptedStates.ADOPTED.equals(attribute.objectBelonging))
        .findFirst()
        .orElseThrow();
    adopted.type = new MdTypeDescriptionDto();
    adopted.type.types = java.util.List.of("xs:string");

    assertThatThrownBy(() -> MdObjectPropertiesEdit.writeDto(catalog, SchemaVersion.V2_21, dto))
        .hasMessageContaining("Тип заимствованного реквизита правится в расширяемой конфигурации");
  }
}
