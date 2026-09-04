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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.yellowhammer.designerxml.cf.ExternalArtifactKind;

/**
 * Внешние обработки и отчёты проекта EDT: проект на объект, как их пишет сама 1С:EDT.
 */
class EdtExternalArtifactsTest {

  private static final String UUID = "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}";

  private static Path fixture;

  @TempDir
  Path workDir;

  @BeforeAll
  static void locate() {
    fixture = Path.of("src", "test", "resources", "edt-extension").toAbsolutePath();
    assertThat(fixture.resolve("Основа")).isDirectory();
  }

  private Path baseConfiguration() throws IOException {
    Path base = workDir.resolve("Основа");
    EdtExtensionScaffoldTest.copy(fixture.resolve("Основа"), base);
    return base.resolve("src/Configuration/Configuration.mdo");
  }

  private static String withoutUuids(String text) {
    return text.replaceAll(UUID, "uuid");
  }

  @Test
  void обработкаИОтчётСовпадаютСЗаписьюEDT() throws Exception {
    Path base = baseConfiguration();
    Path artifacts = workDir.resolve("epf");

    Path processor = EdtExternalArtifacts.create(artifacts, base, "Обработка1", ExternalArtifactKind.DATA_PROCESSOR);
    Path report = EdtExternalArtifacts.create(artifacts, base, "Отчет1", ExternalArtifactKind.REPORT);

    assertThat(processor).isEqualTo(artifacts.resolve("Обработка1/src/ExternalDataProcessors/Обработка1/Обработка1.mdo"));
    assertThat(report).isEqualTo(artifacts.resolve("Отчет1/src/ExternalReports/Отчет1/Отчет1.mdo"));
    for (String[] golden : new String[][] {
        {"ExternalDataProcessor/Обработка1/", "Обработка1", "src/ExternalDataProcessors/Обработка1/Обработка1.mdo"},
        {"ExternalReport/Отчет1/", "Отчет1", "src/ExternalReports/Отчет1/Отчет1.mdo"}}) {
      for (String file : List.of(".project", ".settings/org.eclipse.core.resources.prefs", "DT-INF/PROJECT.PMF", golden[2])) {
        String expected = EdtObjectScaffold.golden(golden[0] + file);
        String actual = Files.readString(artifacts.resolve(golden[1]).resolve(file), StandardCharsets.UTF_8);
        assertThat(withoutUuids(actual)).as(golden[1] + "/" + file).isEqualTo(withoutUuids(expected));
      }
    }
    // Свои идентификаторы у каждого объекта
    String one = Files.readString(processor, StandardCharsets.UTF_8);
    String golden = EdtObjectScaffold.golden("ExternalDataProcessor/Обработка1/src/ExternalDataProcessors/Обработка1/Обработка1.mdo");
    java.util.regex.Matcher uuids = java.util.regex.Pattern.compile(UUID).matcher(golden);
    while (uuids.find()) {
      assertThat(one).doesNotContain(uuids.group());
    }
  }

  @Test
  void базовыйПроектИВерсияПлатформыБерутсяУКонфигурации() throws Exception {
    Path base = baseConfiguration();
    Path project = base.getParent().getParent().getParent();
    Files.writeString(project.resolve(".project"),
        Files.readString(project.resolve(".project"), StandardCharsets.UTF_8).replace("<name>Основа</name>", "<name>Учет</name>"),
        StandardCharsets.UTF_8);
    Files.writeString(project.resolve("DT-INF/PROJECT.PMF"), "Runtime-Version: 8.3.24\nManifest-Version: 1.0\n", StandardCharsets.UTF_8);

    EdtExternalArtifacts.create(workDir.resolve("epf"), base, "Загрузка", ExternalArtifactKind.DATA_PROCESSOR);

    assertThat(Files.readString(workDir.resolve("epf/Загрузка/DT-INF/PROJECT.PMF"), StandardCharsets.UTF_8))
        .contains("Base-Project: Учет", "Runtime-Version: 8.3.24");
    assertThat(Files.readString(workDir.resolve("epf/Загрузка/.project"), StandardCharsets.UTF_8))
        .contains("<name>Загрузка</name>");
  }

  @Test
  void переименованиеКопированиеИУдаление() throws Exception {
    Path base = baseConfiguration();
    Path artifacts = workDir.resolve("epf");
    Path created = EdtExternalArtifacts.create(artifacts, base, "Обработка1", ExternalArtifactKind.DATA_PROCESSOR);

    Path renamed = EdtExternalArtifacts.rename(created, "Выгрузка");

    assertThat(renamed).isEqualTo(artifacts.resolve("Выгрузка/src/ExternalDataProcessors/Выгрузка/Выгрузка.mdo"));
    assertThat(renamed).exists();
    assertThat(artifacts.resolve("Обработка1")).doesNotExist();
    assertThat(Files.readString(renamed, StandardCharsets.UTF_8)).contains("<name>Выгрузка</name>");
    assertThat(Files.readString(artifacts.resolve("Выгрузка/.project"), StandardCharsets.UTF_8)).contains("<name>Выгрузка</name>");

    Path copy = EdtExternalArtifacts.duplicate(renamed, "ВыгрузкаКопия");

    assertThat(copy).isEqualTo(artifacts.resolve("ВыгрузкаКопия/src/ExternalDataProcessors/ВыгрузкаКопия/ВыгрузкаКопия.mdo"));
    assertThat(Files.readString(copy, StandardCharsets.UTF_8)).contains("<name>ВыгрузкаКопия</name>");
    assertThat(withoutUuids(Files.readString(copy, StandardCharsets.UTF_8)))
        .isEqualTo(withoutUuids(Files.readString(renamed, StandardCharsets.UTF_8)).replace("Выгрузка", "ВыгрузкаКопия"));
    assertThat(Files.readString(copy, StandardCharsets.UTF_8)).doesNotContain(uuidOf(renamed));
    assertThat(Files.readString(artifacts.resolve("ВыгрузкаКопия/.project"), StandardCharsets.UTF_8))
        .contains("<name>ВыгрузкаКопия</name>");

    EdtExternalArtifacts.delete(copy);

    assertThat(artifacts.resolve("ВыгрузкаКопия")).doesNotExist();
    assertThat(renamed).exists();
    assertThatThrownBy(() -> EdtExternalArtifacts.create(artifacts, base, "Выгрузка", ExternalArtifactKind.REPORT))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("уже есть");
  }

  private static String uuidOf(Path objectMdo) throws IOException {
    java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("uuid=\"(" + UUID + ")\"")
        .matcher(Files.readString(objectMdo, StandardCharsets.UTF_8));
    assertThat(matcher.find()).isTrue();
    return matcher.group(1);
  }
}
