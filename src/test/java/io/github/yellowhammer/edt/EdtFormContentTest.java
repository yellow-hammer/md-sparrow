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
import java.util.Map;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import io.github.yellowhammer.designerxml.SchemaVersion;
import io.github.yellowhammer.designerxml.cf.FormContentDto;
import io.github.yellowhammer.designerxml.cf.FormItemPropertyDictionary.FormItemPropertyDto;
import io.github.yellowhammer.designerxml.cf.FormItemPropertyDictionary;
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
  private static Map<String, List<FormItemPropertyDto>> dictionary;

  @BeforeAll
  static void locate() throws Exception {
    model = EdtModel.bundled();
    dictionary = FormItemPropertyDictionary.forVersion(SchemaVersion.V2_21);
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
  void видыИСвойстваЭлементовКакУКонфигуратора() throws Exception {
    for (String[] form : new String[][] {
        {"Catalogs/Валюты/Forms/ФормаСписка/Form.form", "Catalogs/Валюты/Forms/ФормаСписка/Ext/Form.xml"},
        {"CommonForms/_ДемоМоиНастройки/Form.form", "CommonForms/_ДемоМоиНастройки/Ext/Form.xml"}}) {
      FormContentDto edt = EdtFormContent.read(edtSource.resolve(form[0]), model);
      FormContentDto designer = FormContentRead.read(designerCf.resolve(form[1]), SchemaVersion.V2_21);
      Map<String, FormItemDto> written = new java.util.HashMap<>();
      collect(designer.items, written);
      List<FormItemDto> items = new ArrayList<>();
      collect(edt.items, items);

      for (FormItemDto item : items) {
        FormItemDto other = written.get(item.name);
        assertThat(other).as(form[0] + ": " + item.name).isNotNull();
        assertThat(item.type).as(form[0] + ": вид " + item.name).isEqualTo(other.type);
        // Свойства названы как у конфигуратора, а совпадающие ещё и записаны тем же значением
        for (Map.Entry<String, String> property : item.properties.entrySet()) {
          assertThat(FormItemPropertyDictionary.find(dictionary, item.type, property.getKey()))
              .as(item.name + "." + property.getKey()).isPresent();
          String designerValue = other.properties.get(property.getKey());
          if (designerValue != null) {
            assertThat(property.getValue()).as(item.name + "." + property.getKey()).isEqualTo(designerValue);
          }
        }
      }
    }
  }

  private static void collect(List<FormItemDto> items, Map<String, FormItemDto> out) {
    for (FormItemDto item : items) {
      out.put(item.name, item);
      collect(item.items, out);
    }
  }

  private static void collect(List<FormItemDto> items, List<FormItemDto> out) {
    for (FormItemDto item : items) {
      out.add(item);
      collect(item.items, out);
    }
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
