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
package io.github.yellowhammer.designerxml.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import picocli.CommandLine;

/**
 * Канал {@code apply-mutation} на проекте 1С:EDT: параметры те же, что у выгрузки конфигуратора.
 */
class ApplyMutationCmdEdtTest {

  /** Проект, записанный 1С:EDT, с немецким основным языком. */
  private static final Path FIXTURE =
    Path.of("src", "test", "resources", "edt-language-de", "Двуязычная").toAbsolutePath();

  @TempDir
  Path workDir;

  @Test
  void синонимСправочникаЗаписывается() throws Exception {
    Path configuration = copyFixture().resolve("src/Configuration/Configuration.mdo");

    int exit = run("{"
      + "\"op\":\"add-md-object\","
      + "\"configurationXml\":" + json(configuration.toString()) + ","
      + "\"type\":\"CATALOG\","
      + "\"name\":\"Waren\","
      + "\"synonym\":\"Warenkatalog\""
      + "}");

    assertThat(exit).isZero();
    String xml = Files.readString(workDir.resolve("Двуязычная/src/Catalogs/Waren/Waren.mdo"), StandardCharsets.UTF_8);
    assertThat(xml).contains("<key>de</key>", "<value>Warenkatalog</value>");
  }

  @Test
  void синонимДругогоВидаОтклоняется() throws Exception {
    Path configuration = copyFixture().resolve("src/Configuration/Configuration.mdo");
    byte[] before = Files.readAllBytes(configuration);

    int exit = run("{"
      + "\"op\":\"add-md-object\","
      + "\"configurationXml\":" + json(configuration.toString()) + ","
      + "\"type\":\"ENUM\","
      + "\"name\":\"Farben\","
      + "\"synonymEmpty\":true"
      + "}");

    assertThat(exit).isEqualTo(2);
    assertThat(Files.readAllBytes(configuration)).isEqualTo(before);
  }

  @Test
  void элементФормыДобавляетсяПривязываетсяИУдаляется() throws Exception {
    Path catalog = copyFixture().resolve("src/Catalogs/Товары/Товары.mdo");
    assertThat(run("{\"op\":\"cf-form-add\",\"objectXml\":" + json(catalog.toString()) + ",\"name\":\"Форма\"}"))
      .isZero();
    Path form = catalog.resolveSibling("Forms/Форма/Form.form");
    String empty = Files.readString(form, StandardCharsets.UTF_8);
    String payload = json("{\"input\": \"Поле\", \"title\": \"Feld\"}");

    // Реквизита у новой формы нет: привязка отказывает, а правка без записи ничего не пишет
    assertThat(run("{\"op\":\"cf-form-item-add\",\"formXml\":" + json(form.toString())
      + ",\"payloadJson\":" + payload + ",\"dryRun\":true}")).isZero();
    assertThat(Files.readString(form, StandardCharsets.UTF_8)).isEqualTo(empty);
    assertThat(run("{\"op\":\"cf-form-item-add\",\"formXml\":" + json(form.toString())
      + ",\"payloadJson\":" + payload + "}")).isZero();
    String xml = Files.readString(form, StandardCharsets.UTF_8);
    assertThat(xml).contains("<name>Поле</name>", "<id>1</id>", "<key>de</key>", "<value>Feld</value>");
    assertThat(run("{\"op\":\"cf-form-item-bind\",\"formXml\":" + json(form.toString())
      + ",\"itemId\":\"1\",\"dataPath\":\"Объект.Code\"}")).isEqualTo(2);

    assertThat(run("{\"op\":\"cf-form-item-delete\",\"formXml\":" + json(form.toString()) + ",\"itemId\":\"1\"}"))
      .isZero();
    // Объявление префикса xsi, заведённое первым элементом, остаётся
    assertThat(Files.readString(form, StandardCharsets.UTF_8))
      .isEqualTo(empty.replace("<form:Form ", "<form:Form xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" "));
  }

  private int run(String params) throws IOException {
    Path file = workDir.resolve("params.json");
    Files.writeString(file, params, StandardCharsets.UTF_8);
    return new CommandLine(new DesignerXmlCli()).execute("apply-mutation", "--params", file.toString());
  }

  private static String json(String raw) {
    return "\"" + raw.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
  }

  private Path copyFixture() throws IOException {
    Path target = workDir.resolve("Двуязычная");
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
