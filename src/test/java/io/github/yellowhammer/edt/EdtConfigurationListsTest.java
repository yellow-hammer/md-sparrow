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
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import io.github.yellowhammer.designerxml.SchemaVersion;
import io.github.yellowhammer.designerxml.cf.ConfigurationChildObjectLister;
import io.github.yellowhammer.designerxml.cf.ConfigurationPropertiesDto;
import io.github.yellowhammer.designerxml.cf.ConfigurationPropertiesEdit;
import io.github.yellowhammer.designerxml.cf.ConfigurationRefTypeLister;
import io.github.yellowhammer.designerxml.cf.RoleRightsFile;
import io.github.yellowhammer.designerxml.cf.SubsystemTreeBuilder;

/**
 * Списки и свойства конфигурации в формате EDT сверяются с выгрузкой конфигуратора.
 *
 * Библиотека одна и та же, поэтому состав, ссылочные типы и подсистемы обязаны
 * совпасть. Порядок берётся из файла и у форматов свой, поэтому сверяются
 * множества.
 */
class EdtConfigurationListsTest {

  private static EdtModel model;
  private static Path edtConfiguration;
  private static Path designerConfiguration;

  @BeforeAll
  static void locate() throws Exception {
    model = EdtModel.bundled();
    edtConfiguration = Path.of(System.getProperty("fixtures.ssl31edt.root"), "ssl31", "src",
        "Configuration", "Configuration.mdo");
    designerConfiguration = Path.of(System.getProperty("fixtures.ssl31.root"), "src", "cf", "Configuration.xml");
    assertThat(edtConfiguration).exists();
    assertThat(designerConfiguration).exists();
  }

  @Test
  void составСовпадаетСВыгрузкойКонфигуратора() throws Exception {
    Map<String, List<String>> edt = EdtConfigurationLists.all(edtConfiguration, model);
    Map<String, List<String>> designer =
        ConfigurationChildObjectLister.listAll(designerConfiguration, SchemaVersion.V2_21);

    assertThat(edt.keySet()).isEqualTo(designer.keySet());
    for (Map.Entry<String, List<String>> entry : designer.entrySet()) {
      assertThat(new TreeSet<>(edt.get(entry.getKey())))
          .as("объекты вида %s", entry.getKey())
          .isEqualTo(new TreeSet<>(entry.getValue()));
    }
  }

  @Test
  void ссылочныеТипыБезПрефиксаПространстваИмён() throws Exception {
    Map<String, List<String>> refs = EdtConfigurationLists.refTypes(edtConfiguration, model);
    Map<String, List<String>> designer =
        ConfigurationRefTypeLister.listRefTypes(designerConfiguration, SchemaVersion.V2_21);

    assertThat(refs.keySet()).isEqualTo(designer.keySet());
    // Тип в файле EDT записан без пространства имён, и панель сверяет его с ним же
    assertThat(refs.get("Catalog")).contains("CatalogRef.Валюты").allSatisfy(
        type -> assertThat(type).doesNotStartWith("cfg:"));
    assertThat(refs.get("Catalog")).hasSameSizeAs(designer.get("Catalog"));
  }

  @Test
  void подсистемыСовпадаютСВыгрузкойКонфигуратора() throws Exception {
    List<SubsystemTreeBuilder.SubsystemNodeDto> edt =
        EdtConfigurationLists.subsystems(edtConfiguration, model);
    List<SubsystemTreeBuilder.SubsystemNodeDto> designer =
        SubsystemTreeBuilder.build(designerConfiguration, SchemaVersion.V2_21);

    assertThat(names(edt)).isEqualTo(names(designer));
    SubsystemTreeBuilder.SubsystemNodeDto first = edt.get(0);
    assertThat(first.children()).isNotEmpty();
    assertThat(first.contentRefs()).isNotEmpty();
  }

  private static List<String> names(List<SubsystemTreeBuilder.SubsystemNodeDto> nodes) {
    return nodes.stream().map(SubsystemTreeBuilder.SubsystemNodeDto::name).sorted().toList();
  }

  @Test
  void свойстваКонфигурацииСовпадают() throws Exception {
    ConfigurationPropertiesDto edt = EdtConfigurationProperties.read(edtConfiguration, model);
    ConfigurationPropertiesDto designer =
        ConfigurationPropertiesEdit.read(designerConfiguration, SchemaVersion.V2_21);

    assertThat(edt.name).isEqualTo(designer.name);
    assertThat(edt.synonymRu).isEqualTo(designer.synonymRu);
    assertThat(edt.vendor).isEqualTo(designer.vendor);
    assertThat(edt.version).isEqualTo(designer.version);
    assertThat(edt.scriptVariant).isEqualTo(designer.scriptVariant);
    assertThat(edt.compatibilityMode).isEqualTo(designer.compatibilityMode);
    assertThat(edt.usePurposeOptions).contains("PERSONAL_COMPUTER", "MOBILE_DEVICE");
  }

  @Test
  void свойстваКонфигурацииПравятсяТочечно(@org.junit.jupiter.api.io.TempDir Path workDir) throws Exception {
    Path copy = workDir.resolve("Configuration.mdo");
    java.nio.file.Files.copy(edtConfiguration, copy);
    String before = java.nio.file.Files.readString(copy, java.nio.charset.StandardCharsets.UTF_8);

    ConfigurationPropertiesDto dto = EdtConfigurationProperties.read(copy, model);
    dto.version = "3.1.99.999";
    int changed = EdtConfigurationProperties.write(copy, dto, model);

    String after = java.nio.file.Files.readString(copy, java.nio.charset.StandardCharsets.UTF_8);
    assertThat(changed).isEqualTo(1);
    assertThat(after.lines().count()).isEqualTo(before.lines().count());
    assertThat(EdtConfigurationProperties.read(copy, model).version).isEqualTo("3.1.99.999");
    // Без правок файл не меняется
    assertThat(EdtConfigurationProperties.write(copy, EdtConfigurationProperties.read(copy, model), model))
        .isZero();
  }

  @Test
  void праваРолиЧитаютсяИзФайлаРядомСОписанием() throws Exception {
    Path role = edtConfiguration.getParent().getParent()
        .resolve("Roles").resolve("_ДемоБазовыеПраваБСП").resolve("_ДемоБазовыеПраваБСП.mdo");
    Path designerRole = designerConfiguration.getParent()
        .resolve("Roles").resolve("_ДемоБазовыеПраваБСП.xml");

    RoleRightsFile.Dto edt = RoleRightsFile.read(role);
    RoleRightsFile.Dto designer = RoleRightsFile.read(designerRole);

    assertThat(edt.objects).isNotEmpty();
    assertThat(edt.objects).hasSameSizeAs(designer.objects);
    assertThat(edt.setForAttributesByDefault).isEqualTo(designer.setForAttributesByDefault);
  }
}
