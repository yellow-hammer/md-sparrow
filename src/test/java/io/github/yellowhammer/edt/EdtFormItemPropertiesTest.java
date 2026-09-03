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

import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import io.github.yellowhammer.designerxml.SchemaVersion;
import io.github.yellowhammer.designerxml.cf.FormItemPropertyDictionary;
import io.github.yellowhammer.designerxml.cf.FormItemPropertyDictionary.FormItemPropertyDto;

/**
 * Палитра свойств элементов формы EDT.
 *
 * Сверяется со словарём выгрузки конфигуратора: виды элементов у форматов одни
 * и те же, свойства называются одинаково.
 */
class EdtFormItemPropertiesTest {

  private static Map<String, List<FormItemPropertyDto>> edt;
  private static Map<String, List<FormItemPropertyDto>> designer;

  @BeforeAll
  static void collect() throws Exception {
    edt = EdtFormItemProperties.all(EdtModel.bundled());
    designer = FormItemPropertyDictionary.forVersion(SchemaVersion.V2_21);
  }

  private static TreeSet<String> names(List<FormItemPropertyDto> properties) {
    TreeSet<String> names = new TreeSet<>();
    properties.forEach(property -> names.add(property.name.toLowerCase(java.util.Locale.ROOT)));
    return names;
  }

  @Test
  void видыЭлементовТеЖе() {
    // Lable вместо Label - опечатка в схемах выгрузки, у EDT написание верное
    assertThat(edt.keySet())
        .containsAll(designer.keySet().stream().filter(kind -> !kind.startsWith("Lable")).toList());
  }

  @Test
  void свойстваПоляВЗаписиКонфигуратора() {
    List<String> fromEdt = edt.get("InputField").stream().map(property -> property.name).toList();
    List<String> fromDesigner = designer.get("InputField").stream().map(property -> property.name).toList();

    // Элементы XML конфигуратора с заглавной, атрибуты со строчной
    assertThat(fromEdt).contains("name", "id", "Title", "Visible", "Enabled", "ReadOnly", "DataPath");
    assertThat(fromDesigner).containsAll(fromEdt);
    assertThat(fromEdt).hasSizeGreaterThan(100);
  }

  @Test
  void служебныеПризнакиМоделиНеПоказываются() {
    for (String kind : List.of("InputField", "Page", "UsualGroup", "Form")) {
      List<String> names = edt.get(kind).stream().map(property -> property.name.toLowerCase(java.util.Locale.ROOT)).toList();
      assertThat(names).as(kind).doesNotContain("origin", "positionchanged", "adopted", "unchanged", "extinfo");
    }
  }

  @Test
  void значенияПеречисленийВНаписанииКонфигуратора() {
    FormItemPropertyDto scroll = designer.get("Form").stream()
        .filter(property -> property.name.equals("VerticalScroll")).findFirst().orElseThrow();
    FormItemPropertyDto fromEdt = edt.get("Form").stream()
        .filter(property -> property.name.equals("VerticalScroll")).findFirst().orElseThrow();
    assertThat(fromEdt.values).containsExactlyInAnyOrderElementsOf(scroll.values);
  }

  @Test
  void перечислимыеСвойстваНесутЗначения() {
    FormItemPropertyDto titleLocation = edt.get("InputField").stream()
        .filter(property -> property.name.equals("TitleLocation"))
        .findFirst()
        .orElseThrow();

    assertThat(titleLocation.kind).isEqualTo("enum");
    assertThat(titleLocation.values).contains("Left", "Top", "None");
  }

  @Test
  void видыГруппИКнопокРазвёрнуты() {
    assertThat(edt).containsKeys("UsualGroup", "Page", "Pages", "CommandBar", "ContextMenu");
    assertThat(edt).containsKeys("UsualButton", "Hyperlink", "CommandBarButton");
    assertThat(edt).containsKeys("LabelDecoration", "PictureDecoration");
    assertThat(edt).containsKeys("SearchStringAddition", "ViewStatusAddition", "SearchControlAddition");
  }

  @Test
  void свойстваФормыТожеЕсть() {
    assertThat(edt).containsKey("Form");
    assertThat(names(edt.get("Form"))).contains("title", "windowopeningmode", "autotitle");
  }
}
