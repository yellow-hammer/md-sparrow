/*
 * This file is a part of md-sparrow.
 *
 * Copyright (c) 2026
 * Ivan Karlo <i.karlo@outlook.com> and contributors
 *
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package io.github.yellowhammer.designerxml.cf;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Дерево метаданных с настраиваемыми каталогами исходников (нестандартная раскладка проекта).
 */
class ProjectSourceDirsTest {

  @Test
  void treeBuildsWithCustomSourceDirs() throws IOException {
    Path ssl = Path.of(System.getProperty("fixtures.ssl31.root"));
    Path project = Files.createTempDirectory("custom-dirs-");
    // Конфигурация в нестандартном каталоге: конфигурация/исходники
    Path cfDir = project.resolve("конфигурация").resolve("исходники");
    Files.createDirectories(cfDir.getParent());
    copyMinimalCf(ssl.resolve("src").resolve("cf"), cfDir);

    ProjectSourceDirs dirs = ProjectSourceDirs.fromNullable("конфигурация/исходники", null, null, null);
    ProjectMetadataTreeDto dto = ProjectMetadataTreeBuilder.build(project, dirs);

    assertThat(dto.sources()).isNotEmpty();
    ProjectMetadataTreeDto.MetadataSourceDto main = dto.sources().get(0);
    assertThat(main.kind()).isEqualTo("main");
    assertThat(main.metadataRootRelativePath()).isEqualTo("конфигурация/исходники");
    assertThat(main.configurationXmlRelativePath()).isEqualTo("конфигурация/исходники/Configuration.xml");
  }

  @Test
  void nullDirsFallBackToDefaults() {
    ProjectSourceDirs dirs = ProjectSourceDirs.fromNullable(null, "", "  ", "custom/erf");
    assertThat(dirs.cf()).isEqualTo("src/cf");
    assertThat(dirs.cfe()).isEqualTo("src/cfe");
    assertThat(dirs.epf()).isEqualTo("src/epf");
    assertThat(dirs.erf()).isEqualTo("custom/erf");
  }

  @Test
  void explicitDirsFindNestedExtensionAndScatteredProcessor() throws IOException {
    Path ssl = Path.of(System.getProperty("fixtures.ssl31.root"));
    Path project = Files.createTempDirectory("explicit-dirs-");
    copyMinimalCf(ssl.resolve("src").resolve("cf"), project.resolve("src").resolve("cf"));
    // Расширение из репозитория, вложенного в каталог расширений
    Path nested = project.resolve("src/cfe/repo/src/cfe/Вложенное");
    Files.createDirectories(nested);
    Files.copy(ssl.resolve("src/cfe/_ДемоПустоеРасширение/Configuration.xml"), nested.resolve("Configuration.xml"));
    // Обработка вне общего каталога обработок
    Path processor = project.resolve("tools").resolve("Обработка");
    Files.createDirectories(processor);
    Files.writeString(processor.resolve("Обработка.xml"), "<ExternalDataProcessor/>");

    ProjectSourceDirs dirs = ProjectSourceDirs.fromNullable(null, null, null, null,
      List.of("src/cfe/repo/src/cfe/Вложенное"), List.of("tools/Обработка"), null);
    ProjectMetadataTreeDto dto = ProjectMetadataTreeBuilder.build(project, dirs);

    assertThat(dto.sources()).extracting(ProjectMetadataTreeDto.MetadataSourceDto::kind)
      .containsExactly("main", "extension", "externalEpf");
    assertThat(dto.sources().get(1).metadataRootRelativePath()).isEqualTo("src/cfe/repo/src/cfe/Вложенное");
    assertThat(dto.sources().get(2).groups().get(0).items())
      .extracting(ProjectMetadataTreeDto.MetadataItemDto::name)
      .containsExactly("Обработка");
  }

  @Test
  void emptyListsMeanContainerListing() {
    ProjectSourceDirs dirs = ProjectSourceDirs.fromNullable(null, null, null, null, List.of(), null, List.of());
    assertThat(dirs.cfeDirs()).isNull();
    assertThat(dirs.erfDirs()).isNull();
  }

  /** Копирует минимум выгрузки: Configuration.xml и один справочник (без содержимого папок). */
  private static void copyMinimalCf(Path srcCf, Path targetCf) throws IOException {
    Files.createDirectories(targetCf.resolve("Catalogs"));
    Files.copy(srcCf.resolve("Configuration.xml"), targetCf.resolve("Configuration.xml"));
    Files.copy(
      srcCf.resolve("Catalogs").resolve("_ДемоБанковскиеСчета.xml"),
      targetCf.resolve("Catalogs").resolve("_ДемоБанковскиеСчета.xml"));
  }
}
