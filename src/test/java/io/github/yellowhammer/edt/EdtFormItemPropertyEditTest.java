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
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.yellowhammer.designerxml.cf.FormContentDto;
import io.github.yellowhammer.designerxml.cf.FormItemDto;
import io.github.yellowhammer.designerxml.cf.FormItemPropertyChangeDto;

/**
 * Свойства элементов формы EDT правятся точечно и в записи конфигуратора.
 */
class EdtFormItemPropertyEditTest {

  /** Группа пользовательских настроек формы списка валют. */
  private static final String GROUP_ID = "66";
  /** Кнопка подбора из классификатора в командной панели той же формы. */
  private static final String BUTTON_ID = "44";

  private static EdtModel model;
  private static Path fixture;

  @TempDir
  Path workDir;

  @BeforeAll
  static void locate() throws Exception {
    model = EdtModel.bundled();
    fixture = Path.of(System.getProperty("fixtures.ssl31edt.root"), "ssl31", "src",
        "Catalogs", "Валюты", "Forms", "ФормаСписка", "Form.form");
    assertThat(fixture).exists();
  }

  private Path form() throws IOException {
    Path copy = workDir.resolve("Form.form");
    Files.copy(fixture, copy);
    return copy;
  }

  private static FormItemPropertyChangeDto change(String itemId, String property, String value) {
    FormItemPropertyChangeDto change = new FormItemPropertyChangeDto();
    change.itemId = itemId;
    change.property = property;
    change.value = value;
    return change;
  }

  private static FormItemDto item(FormContentDto content, String id) {
    List<FormItemDto> all = new ArrayList<>();
    collect(content.items, all);
    return all.stream().filter(item -> id.equals(item.id)).findFirst().orElseThrow();
  }

  private static EdtObjectReader.EdtNode findNode(EdtObjectReader.EdtNode owner, String id) {
    for (EdtObjectReader.EdtNode child : owner.children()) {
      if (id.equals(child.property("id")) && !child.name().isEmpty()) {
        return child;
      }
      EdtObjectReader.EdtNode nested = findNode(child, id);
      if (nested != null) {
        return nested;
      }
    }
    return null;
  }

  private static void collect(List<FormItemDto> items, List<FormItemDto> out) {
    for (FormItemDto item : items) {
      out.add(item);
      collect(item.items, out);
    }
  }

  @Test
  void флагЭлементаМеняетсяОднойСтрокой() throws Exception {
    Path form = form();
    List<String> before = Files.readAllLines(form, StandardCharsets.UTF_8);

    EdtFormItemPropertyEdit.apply(form, model, List.of(change(GROUP_ID, "Visible", "false")));

    List<String> after = Files.readAllLines(form, StandardCharsets.UTF_8);
    assertThat(after).hasSameSizeAs(before);
    assertThat(changedLines(before, after)).containsExactly("    <visible>false</visible>");
    assertThat(item(EdtFormContent.read(form, model), GROUP_ID).visible).isFalse();
  }

  @Test
  void свойствоВидаПишетсяВОписаниеВида() throws Exception {
    Path form = form();

    EdtFormItemPropertyEdit.apply(form, model, List.of(change(GROUP_ID, "Group", "Horizontal")));

    String xml = Files.readString(form, StandardCharsets.UTF_8);
    int info = xml.indexOf("<extInfo xsi:type=\"form:UsualGroupExtInfo\">");
    assertThat(info).isPositive();
    assertThat(xml.indexOf("<group>Horizontal</group>", info)).isLessThan(xml.indexOf("</extInfo>", info));
    assertThat(item(EdtFormContent.read(form, model), GROUP_ID).properties).containsEntry("Group", "Horizontal");
  }

  @Test
  void новоеСвойствоВстаётПоПорядкуСхемы() throws Exception {
    Path form = form();

    EdtFormItemPropertyEdit.apply(form, model, List.of(change(BUTTON_ID, "DefaultButton", "true")));

    EdtObjectReader.EdtNode button = findNode(EdtObjectReader.read(form), BUTTON_ID);
    List<String> written = button.children().stream().map(EdtObjectReader.EdtNode::kind).toList();
    assertThat(written).contains("defaultButton");
    // Соседи нового свойства стоят так, как их перечисляет схема кнопки
    List<String> order = new ArrayList<>();
    org.eclipse.emf.ecore.EClass buttonClass =
        (org.eclipse.emf.ecore.EClass) model.packageOf("http://g5.1c.ru/v8/dt/form").getEClassifier("Button");
    buttonClass.getEAllStructuralFeatures().forEach(feature -> order.add(feature.getName()));
    int place = order.indexOf("defaultButton");
    int at = written.indexOf("defaultButton");
    for (int i = 0; i < written.size(); i++) {
      int other = order.indexOf(written.get(i));
      if (other >= 0 && other != place) {
        assertThat(i < at).as(written.get(i)).isEqualTo(other < place);
      }
    }
    assertThat(item(EdtFormContent.read(form, model), BUTTON_ID).properties).containsEntry("DefaultButton", "true");
  }

  @Test
  void заголовокПишетсяПаройЯзыкЗначение() throws Exception {
    Path form = form();

    EdtFormItemPropertyEdit.apply(form, model, List.of(change(BUTTON_ID, "Title", "По справочнику")));

    String xml = Files.readString(form, StandardCharsets.UTF_8);
    String eol = xml.contains("\r\n") ? "\r\n" : "\n";
    int button = xml.indexOf("<id>" + BUTTON_ID + "</id>");
    assertThat(xml.substring(button)).contains(
        "<title>" + eol + "          <key>ru</key>" + eol + "          <value>По справочнику</value>" + eol + "        </title>");
    assertThat(item(EdtFormContent.read(form, model), BUTTON_ID).title).isEqualTo("По справочнику");
  }

  @Test
  void пустоеЗначениеУбираетЗапись() throws Exception {
    Path form = form();
    List<String> before = Files.readAllLines(form, StandardCharsets.UTF_8);

    EdtFormItemPropertyEdit.apply(form, model, List.of(change(GROUP_ID, "Visible", "")));

    List<String> after = Files.readAllLines(form, StandardCharsets.UTF_8);
    assertThat(after).hasSize(before.size() - 1);
    assertThat(item(EdtFormContent.read(form, model), GROUP_ID).visible).isNull();
  }

  @Test
  void чужоеСвойствоИЧужойЛитералОтклоняются() throws Exception {
    Path form = form();
    String before = Files.readString(form, StandardCharsets.UTF_8);

    assertThatThrownBy(() -> EdtFormItemPropertyEdit.apply(form, model, List.of(change(GROUP_ID, "Mask", "99"))))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("нет свойства");
    assertThatThrownBy(() -> EdtFormItemPropertyEdit.apply(form, model, List.of(change(GROUP_ID, "Group", "Sideways"))))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("нет значения");
    assertThatThrownBy(() -> EdtFormItemPropertyEdit.apply(form, model, List.of(change("999", "Visible", "true"))))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("нет элемента");
    assertThat(Files.readString(form, StandardCharsets.UTF_8)).isEqualTo(before);
  }

  private static List<String> changedLines(List<String> before, List<String> after) {
    List<String> changed = new ArrayList<>();
    for (int i = 0; i < after.size(); i++) {
      if (!after.get(i).equals(before.get(i))) {
        changed.add(after.get(i));
      }
    }
    return changed;
  }
}
