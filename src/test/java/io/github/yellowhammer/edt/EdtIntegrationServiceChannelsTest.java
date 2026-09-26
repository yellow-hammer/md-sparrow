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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.yellowhammer.designerxml.cf.ConfigurationLanguage;
import io.github.yellowhammer.designerxml.cf.MdObjectPropertiesDto;
import io.github.yellowhammer.designerxml.cf.MdObjectStructureDto;

/**
 * Каналы сервиса интеграции в проекте 1С:EDT.
 *
 * <p>Фикстура {@code edt-integration-service} импортирована самой 1С:EDT из
 * выгрузки платформы: сервис с двумя каналами, у отправки есть синоним.
 */
class EdtIntegrationServiceChannelsTest {

  private static final Path FIXTURE =
    Path.of("src", "test", "resources", "edt-integration-service", "Каналы").toAbsolutePath();

  private static final String SERVICE = "src/IntegrationServices/СервисИнтеграции1/СервисИнтеграции1.mdo";

  private static EdtModel model;

  @TempDir
  Path workDir;

  @BeforeAll
  static void bundle() throws Exception {
    model = EdtModel.bundled();
  }

  @BeforeEach
  void forgetLanguages() {
    ConfigurationLanguage.forget();
  }

  @Test
  void каналыВходятВСтроение() throws Exception {
    MdObjectStructureDto dto = EdtObjectStructure.read(FIXTURE.resolve(SERVICE), model);

    assertThat(dto.kind).isEqualTo("integrationService");
    assertThat(dto.channels).containsExactly("Отправка", "Получение");
  }

  @Test
  void каналыЧитаютсяСоСвойствами() throws Exception {
    MdObjectPropertiesDto dto = EdtObjectProperties.readDto(FIXTURE.resolve(SERVICE), model);

    assertThat(dto.channels).extracting(node -> node.name).containsExactly("Отправка", "Получение");
    assertThat(dto.channels).extracting(node -> node.synonym).containsExactly("Отправка заказов", "");
  }

  @Test
  void синонимКаналаПишетсяВСвойКанал() throws Exception {
    Path object = copyFixture().resolve(SERVICE);

    MdObjectPropertiesDto dto = EdtObjectProperties.readDto(object, model);
    dto.channels.get(1).synonym = "Получение ответов";
    EdtObjectWriter.writeDto(object, dto, model);

    String xml = Files.readString(object, StandardCharsets.UTF_8);
    assertThat(xml).contains("<value>Получение ответов</value>");
    assertThat(xml).doesNotContain("<channels");
    assertThat(EdtObjectProperties.readDto(object, model).channels)
      .extracting(node -> node.synonym)
      .containsExactly("Отправка заказов", "Получение ответов");
  }

  private Path copyFixture() throws IOException {
    Path target = workDir.resolve("Каналы");
    try (Stream<Path> tree = Files.walk(FIXTURE)) {
      for (Path path : tree.toList()) {
        Path copy = target.resolve(FIXTURE.relativize(path).toString());
        if (Files.isDirectory(path)) {
          Files.createDirectories(copy);
        } else {
          Files.createDirectories(copy.getParent());
          Files.copy(path, copy);
        }
      }
    }
    return target;
  }
}
