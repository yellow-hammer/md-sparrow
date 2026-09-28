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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.yellowhammer.designerxml.SchemaVersion;
import io.github.yellowhammer.designerxml.Ssl31SubmodulePaths;
import io.github.yellowhammer.edt.EdtFormCompositionEdit;
import io.github.yellowhammer.edt.EdtFormContent;
import io.github.yellowhammer.edt.EdtFormItemStructureEdit;
import io.github.yellowhammer.edt.EdtModel;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

/**
 * Обычная форма ({@code FormType} = {@code Ordinary}) не читается и не правится: операции отказывают
 * внятно и ничего не пишут, а управляемая форма того же объекта работает как прежде.
 */
class OrdinaryFormsTest {

  private static final SchemaVersion V = SchemaVersion.V2_20;

  @TempDir
  Path temp;

  @Test
  void designerOrdinaryFormIsRefused() throws Exception {
    Path catalog = copy(Ssl31SubmodulePaths.projectRoot().resolve("src/cf/Catalogs/Валюты"), temp.resolve("Валюты"));
    Path descriptor = catalog.resolve("Forms/ФормаЭлемента.xml");
    Files.writeString(descriptor, Files.readString(descriptor, StandardCharsets.UTF_8)
      .replace("<FormType>Managed</FormType>", "<FormType>Ordinary</FormType>"), StandardCharsets.UTF_8);
    Path form = catalog.resolve("Forms/ФормаЭлемента/Ext/Form.xml");
    String before = Files.readString(form, StandardCharsets.UTF_8);

    for (ThrowingCall call : List.<ThrowingCall>of(
      () -> FormContentRead.read(form, V),
      () -> FormItemStructureEdit.add(form, V, null, null, "{\"input\": \"Поле\"}"),
      () -> FormCompositionEdit.addCommand(form, V, "{\"name\": \"Команда\"}"),
      () -> FormItemPropertyEdit.apply(form, V, List.of()))) {
      assertThatThrownBy(call::run).hasMessageContaining("обычная");
    }
    assertThat(Files.readString(form, StandardCharsets.UTF_8)).isEqualTo(before);
    assertThat(FormContentRead.read(catalog.resolve("Forms/ФормаСписка/Ext/Form.xml"), V).items).isNotEmpty();
  }

  @Test
  void edtOrdinaryFormIsRefused() throws Exception {
    EdtModel model = EdtModel.bundled();
    Path src = Path.of(System.getProperty("fixtures.ssl31edt.root"), "ssl31", "src");
    Path catalog = copy(src.resolve("Catalogs/Валюты"), temp.resolve("edt/Catalogs/Валюты"));
    Path mdo = catalog.resolve("Валюты.mdo");
    Files.writeString(mdo, Files.readString(mdo, StandardCharsets.UTF_8).replace(
      "<name>ФормаЭлемента</name>", "<name>ФормаЭлемента</name>\n    <formType>Ordinary</formType>"),
      StandardCharsets.UTF_8);
    Path form = catalog.resolve("Forms/ФормаЭлемента/Form.form");
    String before = Files.readString(form, StandardCharsets.UTF_8);

    for (ThrowingCall call : List.<ThrowingCall>of(
      () -> EdtFormContent.read(form, model),
      () -> EdtFormItemStructureEdit.add(form, model, null, null, "{\"input\": \"Поле\"}"),
      () -> EdtFormCompositionEdit.addCommand(form, model, "{\"name\": \"Команда\"}"))) {
      assertThatThrownBy(call::run).hasMessageContaining("обычная");
    }
    assertThat(Files.readString(form, StandardCharsets.UTF_8)).isEqualTo(before);
    assertThat(EdtFormContent.read(catalog.resolve("Forms/ФормаСписка/Form.form"), model).items).isNotEmpty();
  }

  @FunctionalInterface
  private interface ThrowingCall {
    void run() throws Exception;
  }

  private static Path copy(Path source, Path target) throws IOException {
    try (Stream<Path> files = Files.walk(source)) {
      for (Path file : files.filter(Files::isRegularFile).toList()) {
        Path to = target.resolve(source.relativize(file).toString());
        Files.createDirectories(to.getParent());
        Files.copy(file, to);
      }
    }
    Path descriptor = source.resolveSibling(source.getFileName() + ".xml");
    if (Files.isRegularFile(descriptor)) {
      Files.copy(descriptor, target.resolveSibling(target.getFileName() + ".xml"));
    }
    return target;
  }
}
