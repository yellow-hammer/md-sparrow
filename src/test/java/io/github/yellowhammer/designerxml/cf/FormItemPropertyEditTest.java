/*
 * This file is a part of md-sparrow.
 *
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package io.github.yellowhammer.designerxml.cf;

import io.github.yellowhammer.designerxml.SchemaVersion;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Точечная запись свойств элементов формы на реальной форме выгрузки. */
class FormItemPropertyEditTest {

  private static final SchemaVersion VERSION = SchemaVersion.V2_20;

  @TempDir
  Path tempDir;

  /** Копия формы: тесты правят файл, а фикстура - submodule. */
  private Path form() throws Exception {
    String root = System.getProperty("fixtures.ssl31.root");
    assertThat(root).isNotBlank();
    Path source = Path.of(root, "src", "cf", "Catalogs", "Валюты", "Forms", "ФормаЭлемента", "Ext", "Form.xml");
    assertThat(source).exists();
    Path copy = tempDir.resolve("Form.xml");
    Files.copy(source, copy);
    return copy;
  }

  private static FormItemPropertyChangeDto change(String itemId, String property, String value) {
    FormItemPropertyChangeDto dto = new FormItemPropertyChangeDto();
    dto.itemId = itemId;
    dto.property = property;
    dto.value = value;
    return dto;
  }

  private static FormItemDto item(FormContentDto content, String name) {
    return find(content.items, name);
  }

  private static FormItemDto find(List<FormItemDto> items, String name) {
    for (FormItemDto item : items) {
      if (name.equals(item.name)) {
        return item;
      }
      FormItemDto nested = find(item.items, name);
      if (nested != null) {
        return nested;
      }
    }
    return null;
  }

  private static String text(Path form) throws Exception {
    return Files.readString(form, StandardCharsets.UTF_8);
  }

  @Test
  void меняетЗаписанноеСвойствоНеТрогаяОстальнойФайл() throws Exception {
    Path form = form();
    String before = text(form);

    FormItemPropertyEdit.apply(form, VERSION, List.of(change("150", "ShowTitle", "true")));

    String after = text(form);
    assertThat(after).contains("<ShowTitle>true</ShowTitle>");
    assertThat(after.replace("<ShowTitle>true</ShowTitle>", "<ShowTitle>false</ShowTitle>")).isEqualTo(before);
    assertThat(item(FormContentRead.read(form, VERSION), "ГруппаШапка").showTitle).isEqualTo("true");
  }

  @Test
  void добавляетНезаписанноеСвойствоПередСоставомЭлемента() throws Exception {
    Path form = form();

    FormItemPropertyEdit.apply(form, VERSION, List.of(change("4", "ReadOnly", "true")));

    assertThat(text(form)).contains(
      "\t\t\t\t\t<ReadOnly>true</ReadOnly>\r\n"
        + "\t\t\t\t\t<ContextMenu name=\"НаименованиеПолноеКонтекстноеМеню\" id=\"23\"/>");
    assertThat(item(FormContentRead.read(form, VERSION), "НаименованиеПолное").readOnly).isTrue();
  }

  /** Подсказка в файле - пустой тег: чтобы дописать свойство, его надо раскрыть. */
  @Test
  void раскрываетПустойТегЭлемента() throws Exception {
    Path form = form();

    FormItemPropertyEdit.apply(form, VERSION, List.of(change("151", "Title", "Подсказка шапки")));

    assertThat(text(form)).contains(
      "\t\t\t<ExtendedTooltip name=\"ГруппаШапкаРасширеннаяПодсказка\" id=\"151\">\r\n"
        + "\t\t\t\t<Title formatted=\"false\">\r\n"
        + "\t\t\t\t\t<v8:item>\r\n"
        + "\t\t\t\t\t\t<v8:lang>ru</v8:lang>\r\n"
        + "\t\t\t\t\t\t<v8:content>Подсказка шапки</v8:content>\r\n"
        + "\t\t\t\t\t</v8:item>\r\n"
        + "\t\t\t\t</Title>\r\n"
        + "\t\t\t</ExtendedTooltip>");
    assertThat(item(FormContentRead.read(form, VERSION), "ГруппаШапкаРасширеннаяПодсказка").title)
      .isEqualTo("Подсказка шапки");
  }

  @Test
  void убираетСвойствоКогдаЗначениеНеЗадано() throws Exception {
    Path form = form();

    FormItemPropertyEdit.apply(form, VERSION, List.of(change("150", "ShowTitle", null)));

    assertThat(text(form)).contains(
      "\t\t\t<Representation>None</Representation>\r\n"
        + "\t\t\t<ExtendedTooltip name=\"ГруппаШапкаРасширеннаяПодсказка\" id=\"151\"/>");
    assertThat(item(FormContentRead.read(form, VERSION), "ГруппаШапка").showTitle).isNull();
  }

  @Test
  void меняетНесколькоСвойствЗаОдинВызов() throws Exception {
    Path form = form();

    FormItemPropertyEdit.apply(form, VERSION, List.of(
      change("150", "ShowTitle", "true"),
      change("150", "Title", "Новая шапка"),
      change("4", "Width", "40")));

    FormContentDto content = FormContentRead.read(form, VERSION);
    assertThat(item(content, "ГруппаШапка").showTitle).isEqualTo("true");
    assertThat(item(content, "ГруппаШапка").title).isEqualTo("Новая шапка");
    assertThat(item(content, "НаименованиеПолное").width).isEqualTo("40");
  }

  @Test
  void значенияПеречисленияСверяетСоСловарём() throws Exception {
    Path form = form();

    FormItemPropertyEdit.apply(form, VERSION, List.of(change("150", "Group", "Horizontal")));
    assertThat(item(FormContentRead.read(form, VERSION), "ГруппаШапка").group).isEqualTo("Horizontal");

    assertThatThrownBy(() -> FormItemPropertyEdit.apply(form, VERSION, List.of(change("150", "Group", "Поперёк"))))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("Vertical");
  }

  @Test
  void неПишетТогоЧегоУВидаЭлементаНет() throws Exception {
    Path form = form();

    assertThatThrownBy(() -> FormItemPropertyEdit.apply(form, VERSION, List.of(change("150", "Mask", "999"))))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("UsualGroup");
    assertThatThrownBy(() -> FormItemPropertyEdit.apply(form, VERSION, List.of(change("150", "name", "Другая"))))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("атрибутом");
    assertThatThrownBy(() -> FormItemPropertyEdit.apply(form, VERSION, List.of(change("4", "Font", "sys:Dialog"))))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("составное");
    assertThatThrownBy(() -> FormItemPropertyEdit.apply(form, VERSION, List.of(change("4", "Width", "широко"))))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("числовое");
  }

  /** Идентификатор уникален среди элементов, но не по всему файлу: у реквизита он может совпасть. */
  @Test
  void ищетТолькоСредиЭлементовФормы() throws Exception {
    Path form = form();

    assertThatThrownBy(() -> FormItemPropertyEdit.apply(form, VERSION, List.of(change("9999", "Visible", "false"))))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("9999");
  }
}
