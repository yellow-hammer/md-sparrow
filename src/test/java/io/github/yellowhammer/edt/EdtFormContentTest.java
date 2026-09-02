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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import io.github.yellowhammer.designerxml.SchemaVersion;
import io.github.yellowhammer.designerxml.cf.FormContentDto;
import io.github.yellowhammer.designerxml.cf.FormContentRead;
import io.github.yellowhammer.designerxml.cf.FormItemDto;

/**
 * Содержимое управляемой формы в формате EDT.
 *
 * Сверяется с той же формой в выгрузке конфигуратора: разметка у форматов
 * разная, а состав элементов, реквизитов и команд обязан совпасть.
 */
class EdtFormContentTest {

  private static Path edtSource;
  private static Path designerCf;
  private static EdtModel model;

  @BeforeAll
  static void locate() throws Exception {
    model = EdtModel.bundled();
    edtSource = Path.of(System.getProperty("fixtures.ssl31edt.root"), "ssl31", "src");
    designerCf = Path.of(System.getProperty("fixtures.ssl31.root"), "src", "cf");
    assertThat(edtSource).exists();
    assertThat(designerCf).exists();
  }

  /** Имена элементов формы вместе с вложенными. */
  private static List<String> names(List<FormItemDto> items) {
    List<String> names = new ArrayList<>();
    for (FormItemDto item : items) {
      names.add(item.name);
      names.addAll(names(item.items));
    }
    return names;
  }

  @Test
  void составФормыСовпадаетСВыгрузкойКонфигуратора() throws Exception {
    FormContentDto edt = EdtFormContent.read(edtSource.resolve("Catalogs/Валюты/Forms/ФормаСписка/Form.form"), model);
    FormContentDto designer = FormContentRead.read(
        designerCf.resolve("Catalogs/Валюты/Forms/ФормаСписка/Ext/Form.xml"), SchemaVersion.V2_21);

    assertThat(new TreeSet<>(names(edt.items))).isEqualTo(new TreeSet<>(names(designer.items)));
    assertThat(edt.attributes).extracting(attribute -> attribute.name)
        .isEqualTo(designer.attributes.stream().map(attribute -> attribute.name).toList());
    assertThat(edt.commands).extracting(command -> command.name)
        .isEqualTo(designer.commands.stream().map(command -> command.name).toList());
    assertThat(edt.events).extracting(event -> event.name)
        .containsExactlyInAnyOrderElementsOf(designer.events.stream().map(event -> event.name).toList());
  }

  @Test
  void видЭлементаБерётсяИзРазметки() throws Exception {
    FormContentDto edt = EdtFormContent.read(edtSource.resolve("Catalogs/Валюты/Forms/ФормаСписка/Form.form"), model);

    assertThat(edt.items).extracting(item -> item.type).contains("UsualGroup");
    assertThat(names(edt.items)).contains("Валюты", "ВалютыКонтекстноеМеню");
    // Заголовок и признаки читаются вместе с элементом
    assertThat(edt.items.get(0).title).isNotEmpty();
    assertThat(edt.items.get(0).visible).isTrue();
  }

  @Test
  void реквизитФормыНесётТипИОсновнуюТаблицу() throws Exception {
    FormContentDto edt = EdtFormContent.read(edtSource.resolve("Catalogs/Валюты/Forms/ФормаСписка/Form.form"), model);

    assertThat(edt.attributes).hasSize(1);
    assertThat(edt.attributes.get(0).name).isEqualTo("Список");
    assertThat(edt.attributes.get(0).mainTable).isEqualTo("Catalog.Валюты");
    assertThat(edt.attributes.get(0).main).isTrue();
  }

  @Test
  void всеФормыБиблиотекиЧитаютсяЦеликом() throws Exception {
    List<Path> forms;
    try (Stream<Path> files = Files.walk(edtSource)) {
      forms = files.filter(path -> path.getFileName().toString().equals("Form.form")).sorted().toList();
    }
    assertThat(forms).hasSizeGreaterThan(100);

    List<String> broken = new ArrayList<>();
    int items = 0;
    for (Path form : forms) {
      FormContentDto dto = EdtFormContent.read(form, model);
      if (dto.items.isEmpty() && dto.attributes.isEmpty()) {
        broken.add(form.toString());
      }
      items += names(dto.items).size();
    }

    assertThat(broken).as("формы без содержимого").isEmpty();
    assertThat(items).isGreaterThan(1000);
  }

  @Test
  void формаОбщегоНазначенияЧитаетсяТакЖе() throws Exception {
    Path common = edtSource.resolve("CommonForms/_ДемоМоиНастройки/Form.form");
    if (!Files.isRegularFile(common)) {
      return;
    }

    FormContentDto edt = EdtFormContent.read(common, model);
    FormContentDto designer = FormContentRead.read(
        designerCf.resolve("CommonForms/_ДемоМоиНастройки/Ext/Form.xml"), SchemaVersion.V2_21);

    assertThat(new TreeSet<>(names(edt.items))).isEqualTo(new TreeSet<>(names(designer.items)));
  }
}
