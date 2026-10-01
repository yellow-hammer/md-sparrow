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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProjectMetadataTreeBuilderTest {

  @Test
  void ssl31ProjectHasMainAndExtensionsAndCatalogs() throws Exception {
    var dto = ProjectMetadataTreeBuilder.build(Ssl31SubmodulePaths.projectRoot());
    assertThat(dto.mainSchemaVersion()).isNotBlank();
    assertThat(dto.mainSchemaVersionFlag()).matches("V\\d+(_\\d+)+");
    List<ProjectMetadataTreeDto.MetadataSourceDto> sources = dto.sources();
    assertThat(sources).hasSizeGreaterThanOrEqualTo(3);
    ProjectMetadataTreeDto.MetadataSourceDto main =
      sources.stream().filter(s -> "main".equals(s.kind())).findFirst().orElseThrow();
    assertThat(main.label()).isEqualTo("Основная конфигурация");
    assertThat(main.configurationXmlRelativePath()).isEqualTo("src/cf/Configuration.xml");
    var catalogs =
      main.groups().stream().filter(g -> "catalogs".equals(g.id())).findFirst().orElseThrow();
    assertThat(catalogs.items()).isNotEmpty();
    long extensions = sources.stream().filter(s -> "extension".equals(s.kind())).count();
    assertThat(extensions).isGreaterThanOrEqualTo(2);
  }

  @Test
  void ssl31MainItemsHaveRelativePathsForRegistersAndEnums() throws Exception {
    var dto = ProjectMetadataTreeBuilder.build(Ssl31SubmodulePaths.projectRoot());
    ProjectMetadataTreeDto.MetadataSourceDto main =
      dto.sources().stream().filter(s -> "main".equals(s.kind())).findFirst().orElseThrow();

    var enums = main.groups().stream().filter(g -> "enums".equals(g.id())).findFirst().orElseThrow();
    assertThat(enums.items()).isNotEmpty();
    assertThat(enums.items().getFirst().relativePath()).isNotBlank();

    var informationRegisters =
      main.groups().stream().filter(g -> "informationRegisters".equals(g.id())).findFirst().orElseThrow();
    assertThat(informationRegisters.items()).isNotEmpty();
    assertThat(informationRegisters.items().getFirst().relativePath()).isNotBlank();
  }

  @Test
  void ssl31OpenTargetsFollowDumpLayout() throws Exception {
    Path project = Ssl31SubmodulePaths.projectRoot();
    var dto = ProjectMetadataTreeBuilder.build(project);
    ProjectMetadataTreeDto.MetadataSourceDto main =
      dto.sources().stream().filter(s -> "main".equals(s.kind())).findFirst().orElseThrow();
    var common = main.groups().stream().filter(g -> "common".equals(g.id())).findFirst().orElseThrow();

    var forms = subgroupItems(common, "common_commonform");
    assertThat(forms).isNotEmpty();
    var form = forms.getFirst();
    assertThat(form.open()).isNotNull();
    assertThat(form.open().action()).isEqualTo(MdObjectOpen.ACTION_FORM);
    assertThat(form.open().relativePath()).endsWith("/Ext/Form.xml");
    assertThat(form.open().moduleRelativePath()).endsWith("/Ext/Form/Module.bsl");
    assertThat(project.resolve(form.open().relativePath())).exists();

    var modules = subgroupItems(common, "common_commonmodule");
    assertThat(modules).isNotEmpty();
    var module = modules.getFirst();
    assertThat(module.open()).isNotNull();
    assertThat(module.open().action()).isEqualTo(MdObjectOpen.ACTION_MODULE);
    assertThat(module.open().relativePath()).endsWith("/Ext/Module.bsl");

    var catalogs =
      main.groups().stream().filter(g -> "catalogs".equals(g.id())).findFirst().orElseThrow();
    assertThat(catalogs.items()).isNotEmpty();
    var catalog = catalogs.items().getFirst();
    assertThat(catalog.open()).isNotNull();
    assertThat(catalog.open().action()).isEqualTo(MdObjectOpen.ACTION_PROPERTIES);
    assertThat(catalog.open().relativePath()).isNull();
  }

  @Test
  void unsupportedSchemaVersionThrows() {
    assertThatThrownBy(() -> SupportedSchemaVersions.requireSupported("2.99"))
      .isInstanceOf(IOException.class)
      .hasMessageContaining("не поддерживается");
  }

  @Test
  void extensionOfUnsupportedFormatStaysInTreeWithoutContent() throws Exception {
    var dto = ProjectMetadataTreeBuilder.build(UnsupportedExtensionFixture.projectRoot());

    var main = dto.sources().stream().filter(s -> "main".equals(s.kind())).findFirst().orElseThrow();
    assertThat(main.schemaSupported()).isTrue();
    assertThat(main.schemaVersion()).isEqualTo(dto.mainSchemaVersion());

    var old = sourceById(dto, UnsupportedExtensionFixture.OLD_EXTENSION_DIR);
    assertThat(old.kind()).isEqualTo("extension");
    assertThat(old.label()).isEqualTo(UnsupportedExtensionFixture.OLD_EXTENSION_NAME);
    assertThat(old.schemaVersion()).isEqualTo(UnsupportedExtensionFixture.OLD_EXTENSION_VERSION);
    assertThat(old.schemaSupported()).isFalse();
    assertThat(old.groups()).isEmpty();
    assertThat(old.configurationXmlRelativePath()).isEqualTo("src/cfe/Old/Configuration.xml");
    assertThat(old.metadataRootRelativePath()).isEqualTo("src/cfe/Old");

    var fresh = sourceById(dto, UnsupportedExtensionFixture.NEW_EXTENSION_DIR);
    assertThat(fresh.label()).isEqualTo(UnsupportedExtensionFixture.NEW_EXTENSION_NAME);
    assertThat(fresh.schemaSupported()).isTrue();
    assertThat(fresh.schemaVersion()).isEqualTo(dto.mainSchemaVersion());
    assertThat(fresh.groups()).isNotEmpty();
  }

  @Test
  void unsupportedMainConfigurationStillFails() {
    Path project = UnsupportedExtensionFixture.projectRoot();

    assertThatThrownBy(() -> ProjectMetadataTreeBuilder.build(project, UnsupportedExtensionFixture.oldExtensionAsMain()))
      .isInstanceOf(IOException.class)
      .satisfies(error -> assertThat(lines(error)).satisfiesExactly(
        first -> assertThat(first)
          .isEqualTo("Формат выгрузки " + UnsupportedExtensionFixture.OLD_EXTENSION_VERSION + " не поддерживается"),
        second -> assertThat(second).startsWith("Поддерживаются форматы ")));
  }

  @Test
  void missingSourcesAreReportedWithoutPaths(@TempDir Path project) {
    assertThatThrownBy(() -> ProjectMetadataTreeBuilder.build(project))
      .isInstanceOf(IOException.class)
      .satisfies(error -> assertThat(lines(error)).containsExactly(
        "Нет ни выгрузки конфигуратора, ни проекта 1С:EDT"));
  }

  @Test
  void brokenConfigurationNamesFileAndPlaceInDetails(@TempDir Path project) throws Exception {
    Path cf = SamplesSubmodulePaths.copy(emptyInfobase(), project.resolve("src/cf"));
    int line = insertConflictMarker(cf.resolve(CfLayout.CONFIGURATION_XML));

    assertThatThrownBy(() -> ProjectMetadataTreeBuilder.build(project))
      .isInstanceOf(IOException.class)
      .satisfies(error -> assertThat(lines(error)).satisfiesExactly(
        first -> assertThat(first).isEqualTo("Не удалось прочитать выгрузку конфигуратора"),
        second -> assertThat(second).startsWith("src/cf/Configuration.xml: строка " + line + ", столбец ")));
  }

  @Test
  void brokenExtensionIsNamedByItsDirectory(@TempDir Path project) throws Exception {
    SamplesSubmodulePaths.copy(emptyInfobase(), project.resolve("src/cf"));
    Path extensionSnapshot = SamplesSubmodulePaths.snapshot(latest(), "cfe-empty");
    Path extension = SamplesSubmodulePaths.copy(
      extensionSnapshot, project.resolve("src/cfe").resolve(extensionSnapshot.getFileName().toString()));
    insertConflictMarker(extension.resolve(CfLayout.CONFIGURATION_XML));

    assertThatThrownBy(() -> ProjectMetadataTreeBuilder.build(project))
      .satisfies(error -> assertThat(lines(error).getLast())
        .startsWith("src/cfe/" + extension.getFileName() + "/Configuration.xml: строка "));
  }

  @Test
  void configurationWithoutFormatVersionIsNamed(@TempDir Path project) throws Exception {
    Path cf = SamplesSubmodulePaths.copy(emptyInfobase(), project.resolve("src/cf"));
    Path xml = cf.resolve(CfLayout.CONFIGURATION_XML);
    String text = Files.readString(xml, StandardCharsets.UTF_8);
    Files.writeString(xml, text.replaceFirst("(<MetaDataObject\\b[^>]*?)\\s+version=\"[^\"]*\"", "$1"),
      StandardCharsets.UTF_8);

    assertThatThrownBy(() -> ProjectMetadataTreeBuilder.build(project))
      .satisfies(error -> assertThat(lines(error)).containsExactly(
        "Не удалось определить формат выгрузки",
        "src/cf/Configuration.xml: В начале файла нет атрибута version у MetaDataObject."));
  }

  private static SchemaVersion latest() {
    return SchemaVersion.values()[SchemaVersion.values().length - 1];
  }

  private static Path emptyInfobase() {
    return SamplesSubmodulePaths.snapshot(latest(), "cf-empty-infobase");
  }

  private static List<String> lines(Throwable error) {
    return error.getMessage().lines().toList();
  }

  /**
   * Вставляет метку конфликта слияния перед последней строкой файла, внутрь корневого элемента.
   *
   * @return номер строки с меткой
   */
  private static int insertConflictMarker(Path xml) throws IOException {
    List<String> lines = new ArrayList<>(Files.readAllLines(xml, StandardCharsets.UTF_8));
    lines.add(lines.size() - 1, "<<<<<<< HEAD");
    Files.write(xml, lines, StandardCharsets.UTF_8);
    return lines.size() - 1;
  }

  private static ProjectMetadataTreeDto.MetadataSourceDto sourceById(ProjectMetadataTreeDto dto, String id) {
    return dto.sources().stream().filter(s -> id.equals(s.id())).findFirst().orElseThrow();
  }

  @Test
  void emptyChildObjectsStillHasAllMetadataGroups() throws Exception {
    var groups = MetadataTreeTagGroups.buildGroups(List.of());
    assertThat(groups).hasSameSizeAs(MetadataTreeTagGroups.orderedGroups());
    assertThat(
      groups.stream().filter(g -> "catalogs".equals(g.id())).findFirst().orElseThrow().items())
      .isEmpty();
  }

  @Test
  void unmappedObjectTypeInBuildGroupsThrows() {
    var entries =
      List.of(
        new ChildObjectEntry("Catalog", "A"),
        new ChildObjectEntry("FutureMdTag", "B"));
    assertThatThrownBy(() -> MetadataTreeTagGroups.buildGroups(entries))
      .isInstanceOf(IOException.class)
      .hasMessageContaining("FutureMdTag");
  }

  private static List<ProjectMetadataTreeDto.MetadataItemDto> subgroupItems(
    ProjectMetadataTreeDto.MetadataGroupDto common,
    String subgroupId
  ) {
    return common.subgroups().stream()
      .filter(s -> subgroupId.equals(s.id()))
      .findFirst()
      .orElseThrow()
      .items();
  }

  @Test
  void keepsOriginalOrderInsideGroupFromConfigurationXml() throws Exception {
    var entries =
      List.of(
        new ChildObjectEntry("Catalog", "Бета"),
        new ChildObjectEntry("Catalog", "Альфа"),
        new ChildObjectEntry("Document", "Док2"),
        new ChildObjectEntry("Document", "Док1"));

    var groups = MetadataTreeTagGroups.buildGroups(entries);

    var catalogs =
      groups.stream().filter(g -> "catalogs".equals(g.id())).findFirst().orElseThrow();
    assertThat(catalogs.items())
      .extracting(MetadataTreeTagGroups.MetadataTreeItemPayload::name)
      .containsExactly("Бета", "Альфа");

    var documents =
      groups.stream().filter(g -> "documents".equals(g.id())).findFirst().orElseThrow();
    assertThat(documents.items())
      .extracting(MetadataTreeTagGroups.MetadataTreeItemPayload::name)
      .containsExactly("Док2", "Док1");
  }
}
