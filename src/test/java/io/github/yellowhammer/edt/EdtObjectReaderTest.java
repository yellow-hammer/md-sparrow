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

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import io.github.yellowhammer.edt.EdtObjectReader.EdtNode;

/** Чтение объекта метаданных на настоящей конфигурации. */
class EdtObjectReaderTest {

  private static Path project;

  @BeforeAll
  static void locate() {
    project = Path.of(System.getProperty("fixtures.ssl31edt.root"), "ssl31");
    assertThat(project).exists();
  }

  private static EdtNode read(String objectPath) throws Exception {
    return EdtObjectReader.read(project.resolve("src").resolve(objectPath));
  }

  @Test
  void читаетКлассИмяИИдентификаторСправочника() throws Exception {
    EdtNode object = read("Catalogs/Валюты/Валюты.mdo");

    assertThat(object.kind()).isEqualTo("Catalog");
    assertThat(object.name()).isEqualTo("Валюты");
    assertThat(object.uuid()).isEqualTo("1d6b8425-360c-4ab1-9bab-cc9a3b590bb2");
  }

  @Test
  void читаетРеквизитыСправочника() throws Exception {
    List<EdtNode> attributes = read("Catalogs/Валюты/Валюты.mdo").list("attributes");

    assertThat(attributes).extracting(EdtNode::name).contains("ЗагружаетсяИзИнтернета", "НаименованиеПолное");
    assertThat(attributes).allSatisfy(attribute -> assertThat(attribute.uuid()).isNotEmpty());
  }

  @Test
  void читаетРеквизитыТабличнойЧасти() throws Exception {
    List<EdtNode> sections = read("Catalogs/Валюты/Валюты.mdo").list("tabularSections");

    assertThat(sections).extracting(EdtNode::name).containsExactly("Представления");
    assertThat(sections.get(0).list("attributes")).extracting(EdtNode::name)
        .containsExactly("КодЯзыка", "ПараметрыПрописи");
  }

  @Test
  void читаетФормыСправочника() throws Exception {
    List<EdtNode> forms = read("Catalogs/Валюты/Валюты.mdo").list("forms");

    assertThat(forms).extracting(EdtNode::name).contains("ФормаСписка", "ФормаЭлемента");
  }

  @Test
  void сохраняетПовторяющиесяСвойства() throws Exception {
    EdtNode object = read("Catalogs/Валюты/Валюты.mdo");

    // Ввод по строке перечислен несколькими элементами: единственным значением
    // свойства такой список не описать
    assertThat(object.list("inputByString")).extracting(EdtNode::value)
        .containsExactly("Catalog.Валюты.StandardAttribute.Description", "Catalog.Валюты.StandardAttribute.Code");
  }

  @Test
  void читаетТипРеквизита() throws Exception {
    EdtNode attribute = read("Catalogs/Валюты/Валюты.mdo").list("attributes").stream()
        .filter(child -> child.name().equals("ЗагружаетсяИзИнтернета"))
        .findFirst()
        .orElseThrow();

    assertThat(attribute.list("type").get(0).property("types")).isEqualTo("Boolean");
  }

  @Test
  void читаетДокумент() throws Exception {
    EdtNode object = read("Documents/Анкета/Анкета.mdo");

    assertThat(object.kind()).isEqualTo("Document");
    assertThat(object.name()).isEqualTo("Анкета");
  }

  @Test
  void читаетОбщийМодуль() throws Exception {
    EdtNode object = read("CommonModules/ОбщегоНазначения/ОбщегоНазначения.mdo");

    assertThat(object.kind()).isEqualTo("CommonModule");
    assertThat(object.name()).isEqualTo("ОбщегоНазначения");
    assertThat(object.property("server")).isEqualTo("true");
  }
}
