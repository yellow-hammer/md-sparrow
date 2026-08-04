/*
 * This file is a part of md-sparrow.
 *
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package io.github.yellowhammer.designerxml.cf;

import io.github.yellowhammer.designerxml.Ssl31SubmodulePaths;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProjectMetadataTreeBuilderTest {

  @TempDir
  Path tempDir;

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
  void unsupportedSchemaVersionThrows() {
    assertThatThrownBy(() -> SupportedSchemaVersions.requireSupported("2.99"))
      .isInstanceOf(IOException.class)
      .hasMessageContaining("не поддерживается");
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

  @Test
  void extensionItemsContainObjectBelongingForAdoptedOnly() throws Exception {
    Path projectRoot = tempDir.resolve("project");
    Path mainRoot = projectRoot.resolve("src").resolve("cf");
    Path extRoot = projectRoot.resolve("src").resolve("cfe").resolve("ExtA");
    Files.createDirectories(mainRoot.resolve("Catalogs"));
    Files.createDirectories(extRoot.resolve("Catalogs"));

    write(
      mainRoot.resolve("Configuration.xml"),
      """
      <?xml version="1.0" encoding="UTF-8"?>
      <MetaDataObject xmlns="http://v8.1c.ru/8.3/MDClasses" version="2.20">
      <Configuration uuid="00000000-0000-0000-0000-000000000001">
      \t\t<Properties><Name>MainCfg</Name></Properties>
      \t\t<ChildObjects></ChildObjects>
      </Configuration>
      </MetaDataObject>
      """);

    write(
      extRoot.resolve("Configuration.xml"),
      """
      <?xml version="1.0" encoding="UTF-8"?>
      <MetaDataObject xmlns="http://v8.1c.ru/8.3/MDClasses" version="2.20">
      <Configuration uuid="00000000-0000-0000-0000-000000000002">
      \t\t<Properties><Name>РасширениеА</Name></Properties>
      \t\t<ChildObjects>
      \t\t\t<Catalog>ЗаимствованныйКаталог</Catalog>
      \t\t\t<Catalog>СобственныйКаталог</Catalog>
      \t\t</ChildObjects>
      </Configuration>
      </MetaDataObject>
      """);

    write(
      extRoot.resolve("Catalogs").resolve("ЗаимствованныйКаталог.xml"),
      """
      <?xml version="1.0" encoding="UTF-8"?>
      <MetaDataObject xmlns="http://v8.1c.ru/8.3/MDClasses" version="2.20">
      <Catalog uuid="00000000-0000-0000-0000-000000000003">
      \t\t<Properties>
      \t\t\t<Name>ЗаимствованныйКаталог</Name>
      \t\t\t<ObjectBelonging>Adopted</ObjectBelonging>
      \t\t</Properties>
      </Catalog>
      </MetaDataObject>
      """);

    write(
      extRoot.resolve("Catalogs").resolve("СобственныйКаталог.xml"),
      """
      <?xml version="1.0" encoding="UTF-8"?>
      <MetaDataObject xmlns="http://v8.1c.ru/8.3/MDClasses" version="2.20">
      <Catalog uuid="00000000-0000-0000-0000-000000000004">
      \t\t<Properties>
      \t\t\t<Name>СобственныйКаталог</Name>
      \t\t</Properties>
      </Catalog>
      </MetaDataObject>
      """);

    ProjectMetadataTreeDto dto = ProjectMetadataTreeBuilder.build(projectRoot);
    ProjectMetadataTreeDto.MetadataSourceDto extension = dto.sources()
      .stream()
      .filter(s -> "extension".equals(s.kind()))
      .findFirst()
      .orElseThrow();
    ProjectMetadataTreeDto.MetadataGroupDto catalogs = extension.groups()
      .stream()
      .filter(g -> "catalogs".equals(g.id()))
      .findFirst()
      .orElseThrow();

    assertThat(catalogs.items()).extracting(ProjectMetadataTreeDto.MetadataItemDto::name)
      .containsExactly("ЗаимствованныйКаталог", "СобственныйКаталог");
    assertThat(catalogs.items()).extracting(ProjectMetadataTreeDto.MetadataItemDto::objectBelonging)
      .containsExactly("ADOPTED", null);
  }

  private static void write(Path path, String content) throws IOException {
    Files.createDirectories(path.getParent());
    Files.writeString(path, content, StandardCharsets.UTF_8);
  }
}
