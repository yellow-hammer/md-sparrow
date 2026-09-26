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

import io.github.yellowhammer.designerxml.SamplesSubmodulePaths;
import io.github.yellowhammer.designerxml.SchemaVersion;
import io.github.yellowhammer.designerxml.Ssl31SubmodulePaths;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Пространство схемы компоновки в корне новой общей формы объявляется по режиму совместимости
 * конфигурации, как у платформы: с 8.3.19 есть, раньше нет.
 */
class FormNamespaceRulesTest {

  private static final String COMPOSITION_SCHEMA =
    " xmlns:dcssch=\"http://v8.1c.ru/8.1/data-composition-system/schema\"";

  @TempDir
  Path workspace;

  @Test
  void схемаКомпоновкиСРежима8_3_19() {
    assertThat(FormNamespaceRules.declaresCompositionSchema("Version8_3_19")).isTrue();
    assertThat(FormNamespaceRules.declaresCompositionSchema("Version8_3_24")).isTrue();
    assertThat(FormNamespaceRules.declaresCompositionSchema("Version8_5_1")).isTrue();
    assertThat(FormNamespaceRules.declaresCompositionSchema("Version8_3_18")).isFalse();
    assertThat(FormNamespaceRules.declaresCompositionSchema("Version8_3_12")).isFalse();
    assertThat(FormNamespaceRules.declaresCompositionSchema("Version8_2_16")).isFalse();
    assertThat(FormNamespaceRules.declaresCompositionSchema("Version8_1")).isFalse();
    // DontUse платформа записывает как Version8_3_8
    assertThat(FormNamespaceRules.declaresCompositionSchema("DontUse")).isFalse();
  }

  /**
   * В режиме 8.3.18 форма - эталон как есть, в 8.3.19 - эталон с объявлением {@code dcssch} между
   * {@code dcscor} и {@code dcsset}; в режиме новой конфигурации - по линейке платформы формата.
   */
  @ParameterizedTest
  @EnumSource(SchemaVersion.class)
  void общаяФормаОбъявляетСхемуКомпоновкиПоРежиму(SchemaVersion version) throws Exception {
    String golden = GoldenScaffold.generateObjectFiles(MdObjectAddType.COMMON_FORM, "Форма", version)
      .get("CommonForms/Форма/Ext/Form.xml");
    assertThat(golden).doesNotContain(COMPOSITION_SCHEMA);
    String declared = golden.replace(
      " xmlns:dcsset=", COMPOSITION_SCHEMA + " xmlns:dcsset=");
    assertThat(declared).isNotEqualTo(golden);

    assertThat(commonForm(version, "Version8_3_18")).isEqualTo(golden);
    assertThat(commonForm(version, "Version8_3_19")).isEqualTo(declared);
    assertThat(commonForm(version, "DontUse")).isEqualTo(golden);
    assertThat(commonForm(version, null))
      .as("режим новой конфигурации %s", version.platformLine())
      .isEqualTo(FormNamespaceRules.declaresCompositionSchema("Version" + version.platformLine().replace('.', '_'))
        ? declared
        : golden);
  }

  /** Так же объявлены пространства имён у общих форм ssl31: формат 2.20, режим 8.3.24. */
  @Test
  void кореньОбщейФормыКакУФормSsl31() throws Exception {
    String mode = ScaffoldPropertyEdit.leaf(
      Files.readString(Ssl31SubmodulePaths.configurationXml(), StandardCharsets.UTF_8), "CompatibilityMode")
      .orElseThrow();
    Path ssl31Form;
    try (Stream<Path> forms = Files.list(Ssl31SubmodulePaths.projectRoot().resolve("src/cf/CommonForms"))) {
      ssl31Form = forms.filter(Files::isDirectory).sorted().findFirst().orElseThrow()
        .resolve("Ext").resolve("Form.xml");
    }

    assertThat(rootTag(commonForm(SchemaVersion.V2_20, mode))).isEqualTo(rootTag(GoldenSnapshots.read(ssl31Form)));
  }

  /**
   * В расширении формы объявляют схему компоновки по режиму совместимости самого расширения
   * ({@code ConfigurationExtensionCompatibilityMode}), общего режима у него нет. Расширение к
   * конфигурации в режиме 8.3.12 получает её режим, новое расширение - режим платформы формата.
   */
  @ParameterizedTest
  @EnumSource(SchemaVersion.class)
  void формыРасширенияОбъявляютСхемуКомпоновкиПоРежимуРасширения(SchemaVersion version) throws Exception {
    Path old = workspace.resolve(version.name() + "-старое");
    EmptyCfeScaffold.writeEmptyTreeFromConfiguration(old, "Расширение", null, null,
      EmptyCfeScaffold.Purpose.CUSTOMIZATION,
      SamplesSubmodulePaths.bareObjects(version).resolve(CfLayout.CONFIGURATION_XML), version);
    Path fresh = workspace.resolve(version.name() + "-новое");
    EmptyCfeScaffold.writeEmptyTree(fresh, "Расширение", null, null, EmptyCfeScaffold.Purpose.ADD_ON, null, null, version);

    for (Path cfe : List.of(old, fresh)) {
      Path configuration = cfe.resolve(CfLayout.CONFIGURATION_XML);
      String mode = ScaffoldPropertyEdit.leaf(GoldenSnapshots.read(configuration), "ConfigurationExtensionCompatibilityMode")
        .orElseThrow();
      boolean declared = FormNamespaceRules.declaresCompositionSchema(mode);
      for (Path form : newForms(configuration, version)) {
        assertThat(GoldenSnapshots.read(form).contains(COMPOSITION_SCHEMA))
          .as("объявление dcssch в %s, расширение в режиме %s", cfe.relativize(form), mode)
          .isEqualTo(declared);
      }
    }
  }

  /** Новые формы в расширениях ssl31 объявляют те же пространства имён, что формы этих расширений. */
  @Test
  void кореньФормРасширенияКакУФормРасширенийSsl31() throws Exception {
    int compared = 0;
    try (Stream<Path> extensions = Files.list(Ssl31SubmodulePaths.projectRoot().resolve("src/cfe"))) {
      for (Path extension : extensions.filter(Files::isDirectory).sorted().toList()) {
        List<String> roots;
        try (Stream<Path> files = Files.walk(extension)) {
          roots = files.filter(file -> file.endsWith(Path.of("Ext", "Form.xml")))
            .map(file -> rootTag(GoldenSnapshots.read(file)))
            .distinct()
            .toList();
        }
        if (roots.isEmpty()) {
          continue;
        }
        assertThat(roots).as("корень форм %s", extension.getFileName()).hasSize(1);
        Path copy = SamplesSubmodulePaths.copy(extension, workspace.resolve("ssl31-" + compared));
        for (Path form : newForms(copy.resolve(CfLayout.CONFIGURATION_XML), SchemaVersion.V2_20)) {
          assertThat(rootTag(GoldenSnapshots.read(form))).as("%s", copy.relativize(form)).isEqualTo(roots.get(0));
        }
        compared++;
      }
    }
    assertThat(compared).as("расширений ssl31 с формами").isPositive();
  }

  /**
   * Новые формы в конфигурации или расширении: общая форма из add-md-object и форма нового справочника
   * из cf-form-add.
   *
   * @return файлы {@code Ext/Form.xml} этих форм
   */
  private static List<Path> newForms(Path configuration, SchemaVersion version) throws Exception {
    Path cfRoot = configuration.getParent();
    String commonForm = MdObjectAdd.addWithNextAvailableName(
      configuration, version, MdObjectAddType.COMMON_FORM, null, false);
    String catalog = MdObjectAdd.addWithNextAvailableName(
      configuration, version, MdObjectAddType.CATALOG, null, false);
    Path catalogXml = CfLayout.objectXmlInSubdir(cfRoot, MdObjectAddType.CATALOG.cfSubdir(), catalog);
    FormScaffold.addForm(catalogXml, version, "ФормаЭлемента");
    return List.of(
      cfRoot.resolve(MdObjectAddType.COMMON_FORM.cfSubdir()).resolve(commonForm).resolve("Ext").resolve("Form.xml"),
      catalogXml.resolveSibling(catalog).resolve("Forms").resolve("ФормаЭлемента").resolve("Ext").resolve("Form.xml"));
  }

  /** Файл {@code Ext/Form.xml} новой общей формы в конфигурации с режимом {@code mode}; без режима - как создана. */
  private String commonForm(SchemaVersion version, String mode) throws Exception {
    Path cf = workspace.resolve(version.name() + "-" + mode);
    EmptyCfScaffold.writeEmptyTree(cf, CfLayout.DEFAULT_CONFIGURATION_NAME, null, null, null, version);
    Path configuration = cf.resolve(CfLayout.CONFIGURATION_XML);
    if (mode != null) {
      setCompatibilityMode(configuration, mode);
    }
    MdObjectAdd.add(configuration, "Форма", version, MdObjectAddType.COMMON_FORM);
    return GoldenSnapshots.read(cf.resolve("CommonForms/Форма/Ext/Form.xml"));
  }

  static void setCompatibilityMode(Path configuration, String mode) throws IOException {
    String xml = Files.readString(configuration, StandardCharsets.UTF_8);
    Files.writeString(configuration, ScaffoldPropertyEdit.setLeaf(xml, "CompatibilityMode", mode), StandardCharsets.UTF_8);
  }

  private static String rootTag(String form) {
    int start = form.indexOf("<Form ");
    return form.substring(start, form.indexOf('>', start) + 1).replaceAll(" version=\"[0-9.]+\"", "");
  }
}
