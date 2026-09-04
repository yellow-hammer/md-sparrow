/*
 * This file is a part of md-sparrow.
 *
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package io.github.yellowhammer.designerxml.cf;

import io.github.yellowhammer.designerxml.Ssl31SubmodulePaths;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
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
      .hasMessageContaining(UnsupportedExtensionFixture.OLD_EXTENSION_VERSION);
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
