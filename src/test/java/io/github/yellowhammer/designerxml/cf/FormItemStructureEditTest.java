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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Структурная правка элементов формы на форме ssl31 с группами, таблицей, дополнениями
 * таблицы и условным оформлением. Каждая правка проверяется обратной: после неё файл
 * байт в байт прежний, то есть правка трогает только строки своего элемента.
 */
class FormItemStructureEditTest {

  private static final String PROCESSOR = "src/cf/DataProcessors/ИнформационныйЦентр";
  private static final String FORM = "Forms/ВзаимодействияПоОбращению/Ext/Form.xml";

  @TempDir
  Path temp;

  private Path form;
  private String original;

  @BeforeEach
  void copyProcessor() throws IOException {
    Path cf = Ssl31SubmodulePaths.projectRoot().resolve("src/cf");
    Path source = Ssl31SubmodulePaths.projectRoot().resolve(PROCESSOR);
    Path target = temp.resolve("cf/DataProcessors").resolve(source.getFileName());
    try (Stream<Path> files = Files.walk(source)) {
      for (Path file : files.filter(Files::isRegularFile).toList()) {
        Path to = target.resolve(source.relativize(file).toString());
        Files.createDirectories(to.getParent());
        Files.copy(file, to);
      }
    }
    Files.copy(source.resolveSibling(source.getFileName() + ".xml"), target.resolveSibling(target.getFileName() + ".xml"));
    Files.copy(cf.resolve("Configuration.xml"), temp.resolve("cf/Configuration.xml"));
    form = target.resolve(FORM);
    original = text();
  }

  @Test
  void addedElementGetsNextIdsAndDeleteRestoresFile() throws Exception {
    FormItemDto group = item("ПодвалПереписки");
    int max = maxId();

    String id = FormItemStructureEdit.add(form, SchemaVersion.V2_20, group.id, null,
      "{\"input\": \"Комментарий\", \"dataPath\": \"Рассмотрено\", \"title\": \"Комментарий\"}");

    assertThat(id).isEqualTo(String.valueOf(max + 1));
    FormItemDto added = item("Комментарий");
    assertThat(added.id).isEqualTo(id);
    assertThat(item("ПодвалПереписки").items).last().extracting(i -> i.name).isEqualTo("Комментарий");
    // Служебные узлы нового поля пронумерованы дальше подряд
    assertThat(text()).contains("<ContextMenu name=\"КомментарийКонтекстноеМеню\" id=\"" + (max + 2) + "\"/>",
      "<ExtendedTooltip name=\"КомментарийРасширеннаяПодсказка\" id=\"" + (max + 3) + "\"/>");

    FormItemStructureEdit.delete(form, SchemaVersion.V2_20, id);
    assertThat(text()).isEqualTo(original);
  }

  @Test
  void addBeforeSiblingAndIntoEmptyGroup() throws Exception {
    FormItemDto group = item("ПодвалПереписки");
    String sibling = group.items.get(1).id;

    String check = FormItemStructureEdit.add(form, SchemaVersion.V2_20, group.id, sibling, "{\"check\": \"Флажок\"}");
    assertThat(item("ПодвалПереписки").items.get(1).name).isEqualTo("Флажок");

    // У новой группы вложенных элементов нет: список заводится первым же элементом
    String newGroup = FormItemStructureEdit.add(form, SchemaVersion.V2_20, null, null, "{\"group\": \"Новая\"}");
    String label = FormItemStructureEdit.add(form, SchemaVersion.V2_20, newGroup, null,
      "{\"label\": \"Надпись\", \"title\": \"Текст\"}");
    assertThat(item("Новая").items).extracting(i -> i.name).containsExactly("НоваяРасширеннаяПодсказка", "Надпись");

    FormItemStructureEdit.delete(form, SchemaVersion.V2_20, label);
    FormItemStructureEdit.delete(form, SchemaVersion.V2_20, newGroup);
    FormItemStructureEdit.delete(form, SchemaVersion.V2_20, check);
    assertThat(text()).isEqualTo(original);
  }

  @Test
  void renameCarriesServiceNodesAndReferences() throws Exception {
    FormItemDto table = item("СписокВзаимодействий");

    FormItemStructureEdit.rename(form, SchemaVersion.V2_20, table.id, "Переписка");

    String xml = text();
    assertThat(xml).contains("<Table name=\"Переписка\"", "name=\"ПерепискаКонтекстноеМеню\"",
      "name=\"ПерепискаКоманднаяПанель\"", "name=\"ПерепискаСтрокаПоиска\"",
      "name=\"ПерепискаСтрокаПоискаКонтекстноеМеню\"", "<Item>Переписка</Item>",
      "<dcsset:field>Переписка</dcsset:field>");
    // Кнопки и колонки таблицы - свои элементы: их имена и путь к данным не меняются
    assertThat(xml).contains("name=\"СписокВзаимодействийДата\"", "<DataPath>СписокВзаимодействий.Дата</DataPath>",
      "<DataPath>СписокВзаимодействий</DataPath>");
    assertThat(xml).doesNotContain("<Item>СписокВзаимодействий</Item>");

    FormItemStructureEdit.rename(form, SchemaVersion.V2_20, table.id, "СписокВзаимодействий");
    assertThat(text()).isEqualTo(original);
  }

  @Test
  void moveAndBackRestoresFile() throws Exception {
    FormItemDto footer = item("ПодвалПереписки");
    FormItemDto owner = parentOf(footer.id);
    List<FormItemDto> siblings = owner.items;
    int at = indexOf(siblings, footer.id);
    String next = at + 1 < siblings.size() ? siblings.get(at + 1).id : null;

    FormItemStructureEdit.move(form, SchemaVersion.V2_20, footer.id, null, null);
    assertThat(content().items).last().extracting(i -> i.name).isEqualTo("ПодвалПереписки");
    assertThat(item("ПодвалПереписки").id).isEqualTo(footer.id);

    FormItemStructureEdit.move(form, SchemaVersion.V2_20, footer.id, owner.id, next);
    assertThat(text()).isEqualTo(original);
  }

  @Test
  void referencedElementIsNotDeleted() throws Exception {
    // На таблицу ссылается условное оформление формы
    String table = item("СписокВзаимодействий").id;
    assertThatThrownBy(() -> FormItemStructureEdit.delete(form, SchemaVersion.V2_20, table))
      .hasMessageContaining("СписокВзаимодействий").hasMessageContaining("условное оформление");
    String group = parentOf(table).id;
    assertThatThrownBy(() -> FormItemStructureEdit.delete(form, SchemaVersion.V2_20, group))
      .hasMessageContaining("СписокВзаимодействий");
    assertThat(text()).isEqualTo(original);
  }

  @Test
  void serviceNodesAndWrongPlacesAreRefused() throws Exception {
    String tooltip = item("ПодвалПереписки").items.stream().findFirst().map(i -> i.id).orElseThrow();
    String table = item("СписокВзаимодействий").id;
    assertThatThrownBy(() -> FormItemStructureEdit.add(form, SchemaVersion.V2_20, table, null, "{\"group\": \"Г\"}"))
      .hasMessageContaining("не размещается");
    assertThatThrownBy(() -> FormItemStructureEdit.add(form, SchemaVersion.V2_20, null, null, "{\"page\": \"Стр\"}"))
      .hasMessageContaining("не размещается");
    assertThatThrownBy(() -> FormItemStructureEdit.add(form, SchemaVersion.V2_20, null, null,
      "{\"input\": \"ПодвалПереписки\"}")).hasMessageContaining("уже есть");
    assertThatThrownBy(() -> FormItemStructureEdit.rename(form, SchemaVersion.V2_20,
      item("ПодвалПереписки").id, "КнопкаЛево")).hasMessageContaining("уже есть");
    assertThatThrownBy(() -> FormItemStructureEdit.delete(form, SchemaVersion.V2_20, tooltip))
      .hasMessageContaining("Служебный узел");
    assertThat(text()).isEqualTo(original);
  }

  @Test
  void bindChecksDataPath() throws Exception {
    String field = item("СписокВзаимодействийДата").id;

    FormItemStructureEdit.bind(form, SchemaVersion.V2_20, field, "СписокВзаимодействий.Описание");
    assertThat(item("СписокВзаимодействийДата").dataPath).isEqualTo("СписокВзаимодействий.Описание");

    assertThatThrownBy(() -> FormItemStructureEdit.bind(form, SchemaVersion.V2_20, field, "НетТакого"))
      .hasMessageContaining("нет реквизита НетТакого");
    assertThatThrownBy(() -> FormItemStructureEdit.bind(form, SchemaVersion.V2_20, field, "СписокВзаимодействий.Нет"))
      .hasMessageContaining("нет колонки Нет");
    // У основного реквизита путь идёт по реквизитам обработки, а у неё реквизитов нет
    assertThatThrownBy(() -> FormItemStructureEdit.bind(form, SchemaVersion.V2_20, field, "Объект.Нет"))
      .hasMessageContaining("нет реквизита Нет");

    FormItemStructureEdit.bind(form, SchemaVersion.V2_20, field, "СписокВзаимодействий.Дата");
    assertThat(text()).isEqualTo(original);
  }

  @Test
  void bindAcceptsStandardAttributesNotWrittenInObject(@TempDir Path bare) throws Exception {
    // У голого справочника эталона стандартные реквизиты в файле не записаны
    Path cf = io.github.yellowhammer.designerxml.SamplesSubmodulePaths.copy(
      io.github.yellowhammer.designerxml.SamplesSubmodulePaths.bareObjects(SchemaVersion.V2_20), bare.resolve("cf"));
    Path catalog = cf.resolve("Catalogs/Справочник1.xml");
    FormScaffold.compileForm(catalog, SchemaVersion.V2_20, "Форма",
      "{\"mainAttribute\": {\"name\": \"Объект\", \"type\": \"cfg:CatalogObject.Справочник1\"},"
        + " \"items\": [{\"input\": \"Поле\"}]}");
    Path content = cf.resolve("Catalogs/Справочник1/Forms/Форма/Ext/Form.xml");
    String field = FormContentRead.read(content, SchemaVersion.V2_20).items.stream()
      .filter(i -> "Поле".equals(i.name)).findFirst().orElseThrow().id;

    FormItemStructureEdit.bind(content, SchemaVersion.V2_20, field, "Объект.Code");

    assertThat(Files.readString(content, StandardCharsets.UTF_8)).contains("<DataPath>Объект.Code</DataPath>");
    assertThatThrownBy(() -> FormItemStructureEdit.bind(content, SchemaVersion.V2_20, field, "Объект.Нет"))
      .hasMessageContaining("нет реквизита Нет");
  }

  @Test
  void everyFifthSslFormSurvivesInverseEdits() throws Exception {
    List<Path> forms;
    try (Stream<Path> files = Files.walk(Ssl31SubmodulePaths.projectRoot().resolve("src/cf"))) {
      forms = files.filter(f -> f.getFileName().toString().equals("Form.xml")
        && f.getParent().getFileName().toString().equals("Ext")).sorted().toList();
    }
    int checked = 0;
    for (int i = 0; i < forms.size(); i += 5) {
      Path copy = temp.resolve("forms").resolve(i + "/Ext/Form.xml");
      Files.createDirectories(copy.getParent());
      Files.copy(forms.get(i), copy);
      String before = Files.readString(copy, StandardCharsets.UTF_8);
      FormContentDto content = FormContentRead.read(copy, SchemaVersion.V2_20);
      List<FormItemDto> own = content.items.stream()
        .filter(item -> !FormTree.ATTACHED.contains(item.type)).toList();
      if (own.isEmpty()) {
        continue;
      }
      FormItemDto first = own.get(0);
      String what = forms.get(i).toString();

      FormItemStructureEdit.rename(copy, SchemaVersion.V2_20, first.id, "ПереименованныйЭлемент");
      FormItemStructureEdit.rename(copy, SchemaVersion.V2_20, first.id, first.name);
      assertThat(Files.readString(copy, StandardCharsets.UTF_8)).as("переименование " + what).isEqualTo(before);

      String added = FormItemStructureEdit.add(copy, SchemaVersion.V2_20, null, first.id, "{\"label\": \"НоваяНадпись\"}");
      FormItemStructureEdit.delete(copy, SchemaVersion.V2_20, added);
      assertThat(Files.readString(copy, StandardCharsets.UTF_8)).as("добавление " + what).isEqualTo(before);

      if (own.size() > 1) {
        FormItemStructureEdit.move(copy, SchemaVersion.V2_20, first.id, null, null);
        FormItemStructureEdit.move(copy, SchemaVersion.V2_20, first.id, null, own.get(1).id);
        assertThat(Files.readString(copy, StandardCharsets.UTF_8)).as("перенос " + what).isEqualTo(before);
      }
      checked++;
    }
    assertThat(checked).isGreaterThan(150);
  }

  // ---------- помощники ----------

  private String text() throws IOException {
    return Files.readString(form, StandardCharsets.UTF_8);
  }

  private FormContentDto content() throws Exception {
    return FormContentRead.read(form, SchemaVersion.V2_20);
  }

  private FormItemDto item(String name) throws Exception {
    return all().stream().filter(i -> name.equals(i.name)).findFirst()
      .orElseThrow(() -> new AssertionError("нет элемента " + name));
  }

  private FormItemDto parentOf(String id) throws Exception {
    return all().stream().filter(i -> i.items.stream().anyMatch(child -> id.equals(child.id))).findFirst()
      .orElseGet(() -> {
        FormItemDto root = new FormItemDto();
        try {
          root.items = content().items;
        } catch (Exception e) {
          throw new IllegalStateException(e);
        }
        return root;
      });
  }

  private static int indexOf(List<FormItemDto> items, String id) {
    for (int i = 0; i < items.size(); i++) {
      if (id.equals(items.get(i).id)) {
        return i;
      }
    }
    return -1;
  }

  private int maxId() throws Exception {
    return all().stream().mapToInt(i -> number(i.id)).max().orElse(0);
  }

  /** Номер элемента формы - целое число; иное в форме - дефект, на котором тест и падает. */
  private static int number(String id) {
    try {
      return Integer.parseInt(id);
    } catch (NumberFormatException e) {
      throw new AssertionError("Нечисловой номер элемента формы: " + id, e);
    }
  }

  private List<FormItemDto> all() throws Exception {
    List<FormItemDto> out = new ArrayList<>();
    collect(content().items, out);
    return out;
  }

  private static void collect(List<FormItemDto> items, List<FormItemDto> out) {
    for (FormItemDto item : items) {
      out.add(item);
      collect(item.items, out);
    }
  }
}
