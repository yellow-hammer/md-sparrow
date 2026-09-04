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
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.yellowhammer.designerxml.cf.SupportRules;

/**
 * Правила поддержки проекта EDT из файла поставки, который записала сама 1С:EDT.
 */
class EdtSupportRulesTest {

  private static Path fixture;

  @TempDir
  Path workDir;

  @BeforeAll
  static void locate() {
    fixture = Path.of("src", "test", "resources", "edt-support", "Основа").toAbsolutePath();
    assertThat(fixture.resolve("src/Configuration/Configuration.distr")).exists();
  }

  @AfterEach
  void enforceAgain() {
    SupportRules.setEnforced(true);
  }

  private Path project() throws IOException {
    Path project = workDir.resolve("Основа");
    EdtExtensionScaffoldTest.copy(fixture, project);
    return project;
  }

  @Test
  void правилаЧитаютсяИзФайлаПоставки() throws Exception {
    Path project = project();
    Path configuration = project.resolve("src/Configuration/Configuration.mdo");

    EdtSupportRules.Rules rules = EdtSupportRules.read(configuration);

    assertThat(rules.vendor).isEqualTo("Фирма \"1С\"");
    assertThat(rules.version).isEqualTo("3.1.11.392");
    assertThat(rules.name).isEqualTo("БиблиотекаСтандартныхПодсистемДемо");
    assertThat(rules.modeByUuid).hasSize(5);
    assertThat(rules.editingEnabled).isTrue();
    assertThat(rules.configurationState()).isEqualTo("editable");
    assertThat(rules.generationId).hasSize(16);
    assertThat(EdtSupportRules.objectState(configuration)).isEqualTo("editable");
    assertThat(EdtSupportRules.objectState(project.resolve("src/Catalogs/Товары/Товары.mdo"))).isEqualTo("editable");
    assertThat(EdtSupportRules.objectState(project.resolve("src/Documents/Заказ/Заказ.mdo"))).isEqualTo("locked");
    // Модуль отвечает своим объектом
    assertThat(EdtSupportRules.objectState(project.resolve("src/Documents/Заказ/ObjectModule.bsl"))).isEqualTo("locked");
  }

  @Test
  void состоянияОбъектаКлючамиКонфигуратора() throws Exception {
    Path project = project();
    Path catalog = project.resolve("src/Catalogs/Товары/Товары.mdo");

    Map<String, String> states = EdtSupportRules.statesForObject(catalog);

    assertThat(states).containsEntry("Catalogs/Товары/Товары.mdo", "editable");
    assertThat(states.keySet()).allMatch(key -> key.startsWith("Catalogs/Товары/") || key.startsWith("element:"));
  }

  @Test
  void запертыйОбъектНеПравится() throws Exception {
    Path project = project();
    Path document = project.resolve("src/Documents/Заказ/Заказ.mdo");

    assertThatThrownBy(() -> EdtSupportRules.ensureEditable(document))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("поставщика «Фирма \"1С\"» без возможности изменения");
    EdtSupportRules.ensureEditable(project.resolve("src/Catalogs/Товары/Товары.mdo"));

    SupportRules.setEnforced(false);
    EdtSupportRules.ensureEditable(document);
  }

  @Test
  void режимМеняетсяТочечноИСледитЗаПоколением() throws Exception {
    Path project = project();
    Path document = project.resolve("src/Documents/Заказ/Заказ.mdo");
    Path rulesFile = project.resolve("src/Configuration/Configuration.distr");
    List<String> before = Files.readAllLines(rulesFile, StandardCharsets.UTF_8);
    String generation = EdtSupportRules.read(document).generationId;

    EdtSupportRules.setModeForFile(document, SupportRules.MODE_EDITABLE, false, generation);

    List<String> after = Files.readAllLines(rulesFile, StandardCharsets.UTF_8);
    assertThat(after).hasSameSizeAs(before);
    long changed = 0;
    for (int i = 0; i < after.size(); i++) {
      if (!after.get(i).equals(before.get(i))) {
        changed++;
        assertThat(after.get(i)).contains("userMode=\"ChangesAllowed\"");
      }
    }
    assertThat(changed).isEqualTo(1);
    assertThat(EdtSupportRules.objectState(document)).isEqualTo("editable");

    // Прежнее поколение уже не годится: файл менялся
    assertThatThrownBy(() -> EdtSupportRules.setModeForFile(document, SupportRules.MODE_NOT_EDITABLE, false, generation))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("изменился");

    EdtSupportRules.setModeForFile(document, SupportRules.MODE_REMOVED, false, null);
    assertThat(EdtSupportRules.objectState(document)).isNull();
  }

  @Test
  void режимКонфигурацииРаспространяетсяНаСоставТолькоПоПросьбе() throws Exception {
    Path project = project();
    Path configuration = project.resolve("src/Configuration/Configuration.mdo");
    Path catalog = project.resolve("src/Catalogs/Товары/Товары.mdo");

    EdtSupportRules.setModeForFile(configuration, SupportRules.MODE_NOT_EDITABLE, false, null);

    // Запрет на корень запирает только корень: возможность изменения остаётся включённой
    assertThat(EdtSupportRules.read(configuration).editingEnabled).isTrue();
    assertThat(EdtSupportRules.objectState(configuration)).isEqualTo("locked");
    assertThat(EdtSupportRules.objectState(catalog)).isEqualTo("editable");

    EdtSupportRules.setModeForFile(configuration, SupportRules.MODE_NOT_EDITABLE, true, null);

    assertThat(EdtSupportRules.objectState(catalog)).isEqualTo("locked");

    EdtSupportRules.setModeForFile(configuration, SupportRules.MODE_EDITABLE, true, null);

    assertThat(EdtSupportRules.objectState(project.resolve("src/Documents/Заказ/Заказ.mdo"))).isEqualTo("editable");
  }

  @Test
  void полнаяПоддержкаЗапираетВсёНезависимоОтРежимов() throws Exception {
    Path project = project();
    Path rulesFile = project.resolve("src/Configuration/Configuration.distr");
    Files.writeString(rulesFile,
        Files.readString(rulesFile, StandardCharsets.UTF_8).replace("fileState=\"Normal\"", "fileState=\"Distributive\""),
        StandardCharsets.UTF_8);
    Path configuration = project.resolve("src/Configuration/Configuration.mdo");

    EdtSupportRules.Rules rules = EdtSupportRules.read(configuration);

    assertThat(rules.editingEnabled).isFalse();
    assertThat(rules.configurationState()).isEqualTo("locked");
    assertThat(EdtSupportRules.objectState(project.resolve("src/Catalogs/Товары/Товары.mdo"))).isEqualTo("locked");
    assertThatThrownBy(() -> EdtSupportRules.ensureEditable(project.resolve("src/Catalogs/Товары/Товары.mdo")))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void снятиеСПоддержкиУбираетФайлПоставки() throws Exception {
    Path project = project();
    Path configuration = project.resolve("src/Configuration/Configuration.mdo");

    EdtSupportRules.removeSupport(configuration, null);

    assertThat(project.resolve("src/Configuration/Configuration.distr")).doesNotExist();
    assertThat(EdtSupportRules.read(configuration).isEmpty()).isTrue();
    assertThat(EdtSupportRules.objectState(project.resolve("src/Documents/Заказ/Заказ.mdo"))).isNull();
    assertThatThrownBy(() -> EdtSupportRules.removeSupport(configuration, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("не на поддержке");
  }
}
