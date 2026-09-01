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

import io.github.yellowhammer.designerxml.cf.ExternalArtifactPropertiesDto;

/**
 * Свойства внешнего отчёта и обработки в формате EDT.
 *
 * Внешний артефакт описан таким же файлом, как объект конфигурации, поэтому и
 * читается тем же кодом. Настоящей внешней обработки EDT под рукой нет: её
 * заводят в самой среде, а командной строки для этого у 1С:EDT нет.
 */
class EdtExternalArtifactTest {

  private static EdtModel model;

  @TempDir
  Path workDir;

  @BeforeAll
  static void locate() throws Exception {
    model = EdtModel.bundled();
  }

  /** Описание внешней обработки: та же разметка, что у объекта конфигурации. */
  private Path externalProcessor() throws Exception {
    Path file = workDir.resolve("ВнешняяОбработка.mdo");
    String uuid = "uuid=\"1d6b8425-360c-4ab1-9bab-cc9a3b590bb2\"";
    String namespace = "xmlns:mdclass=\"http://g5.1c.ru/v8/dt/metadata/mdclass\"";
    Files.writeString(file, String.join("\n",
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>",
        "<mdclass:ExternalDataProcessor " + namespace + " " + uuid + ">",
        "  <name>ВнешняяОбработка</name>",
        "  <synonym>",
        "    <key>ru</key>",
        "    <value>Внешняя обработка</value>",
        "  </synonym>",
        "  <comment>Проверка</comment>",
        "  <attributes uuid=\"9f67d228-79aa-44e6-8dc7-fae4fbdfef2a\">",
        "    <name>Параметр</name>",
        "  </attributes>",
        "</mdclass:ExternalDataProcessor>",
        ""), StandardCharsets.UTF_8);
    return file;
  }

  @Test
  void классВнешнейОбработкиЕстьВСхеме() {
    assertThat(model.classOf("ExternalDataProcessor")).isNotNull();
    assertThat(model.classOf("ExternalReport")).isNotNull();
  }

  @Test
  void читаетСвойстваВнешнейОбработки() throws Exception {
    ExternalArtifactPropertiesDto dto = EdtObjectProperties.readExternalDto(externalProcessor(), model);

    assertThat(dto.kind).isEqualTo("externalDataProcessor");
    assertThat(dto.name).isEqualTo("ВнешняяОбработка");
    assertThat(dto.synonymRu).isEqualTo("Внешняя обработка");
    assertThat(dto.comment).isEqualTo("Проверка");
  }

  @Test
  void пишетСинонимИКомментарийТочечно() throws Exception {
    Path file = externalProcessor();
    String before = Files.readString(file, StandardCharsets.UTF_8);

    ExternalArtifactPropertiesDto dto = EdtObjectProperties.readExternalDto(file, model);
    dto.synonymRu = "Обработка проверки";
    EdtObjectProperties.writeExternalDto(file, dto, model);

    String after = Files.readString(file, StandardCharsets.UTF_8);
    assertThat(after.lines().count()).isEqualTo(before.lines().count());
    assertThat(EdtObjectProperties.readExternalDto(file, model).synonymRu).isEqualTo("Обработка проверки");
    // Реквизит не тронут
    assertThat(after).contains("<name>Параметр</name>");
  }

  @Test
  void переименованиеОтклоняется() throws Exception {
    Path file = externalProcessor();
    ExternalArtifactPropertiesDto dto = EdtObjectProperties.readExternalDto(file, model);
    dto.name = "ДругоеИмя";

    assertThatThrownBy(() -> EdtObjectProperties.writeExternalDto(file, dto, model))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Переименование");
  }
}
