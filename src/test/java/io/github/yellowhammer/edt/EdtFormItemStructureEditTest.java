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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import io.github.yellowhammer.designerxml.cf.FormContentDto;
import io.github.yellowhammer.designerxml.cf.FormItemDto;

/**
 * Структурная правка элементов формы EDT на той же форме ssl31, что и у выгрузки
 * конфигуратора: с группами, таблицей, дополнениями и условным оформлением отдельным файлом.
 * Каждая правка проверяется обратной: после неё файлы байт в байт прежние.
 */
class EdtFormItemStructureEditTest {

  private static final String PROCESSOR = "DataProcessors/ИнформационныйЦентр";
  private static final String FORM = "Forms/ВзаимодействияПоОбращению/Form.form";
  /**
   * Служебные узлы в чтении формы. Командная панель-группа ({@code CommandBar}) - свой элемент,
   * а не служебный узел, поэтому её здесь нет.
   */
  private static final java.util.Set<String> ATTACHED = java.util.Set.of("ContextMenu", "ExtendedTooltip",
      "AutoCommandBar", "SearchStringAddition", "ViewStatusAddition", "SearchControlAddition");

  private static EdtModel model;
  private static Path src;

  @TempDir
  Path temp;

  private Path form;
  private Path appearance;
  private String original;
  private String originalAppearance;

  @BeforeAll
  static void locate() throws IOException {
    model = EdtModel.bundled();
    src = Path.of(System.getProperty("fixtures.ssl31edt.root"), "ssl31", "src");
  }

  @BeforeEach
  void copyForm() throws IOException {
    // Обработка копируется целиком: путь к данным проверяется по её описанию
    form = copy(src.resolve(PROCESSOR), temp.resolve(PROCESSOR)).resolve(FORM);
    appearance = form.resolveSibling("ConditionalAppearance.dcssca");
    original = text();
    originalAppearance = Files.readString(appearance, StandardCharsets.UTF_8);
  }

  @Test
  void addedElementGetsNextIdsAndDeleteRestoresFile() throws Exception {
    FormItemDto group = item("ПодвалПереписки");
    int max = maxId();

    String id = EdtFormItemStructureEdit.add(form, model, group.id, null,
        "{\"input\": \"Комментарий\", \"dataPath\": \"Рассмотрено\", \"title\": \"Комментарий\"}");

    assertThat(id).isEqualTo(String.valueOf(max + 1));
    FormItemDto added = item("Комментарий");
    assertThat(added.id).isEqualTo(id);
    assertThat(added.type).isEqualTo("InputField");
    assertThat(own(item("ПодвалПереписки"))).last().extracting(i -> i.name).isEqualTo("Комментарий");
    // Номера служебных узлов - как у формы конфигуратора: меню, затем подсказка
    assertThat(item("КомментарийКонтекстноеМеню").id).isEqualTo(String.valueOf(max + 2));
    assertThat(item("КомментарийРасширеннаяПодсказка").id).isEqualTo(String.valueOf(max + 3));

    EdtFormItemStructureEdit.delete(form, model, id);
    assertThat(text()).isEqualTo(original);
  }

  @Test
  void addBeforeSiblingAndIntoEmptyGroup() throws Exception {
    FormItemDto group = item("ПодвалПереписки");
    String sibling = own(group).get(1).id;

    String check = EdtFormItemStructureEdit.add(form, model, group.id, sibling, "{\"check\": \"Флажок\"}");
    assertThat(own(item("ПодвалПереписки")).get(1).name).isEqualTo("Флажок");

    String newGroup = EdtFormItemStructureEdit.add(form, model, null, null, "{\"group\": \"Новая\"}");
    String label = EdtFormItemStructureEdit.add(form, model, newGroup, null,
        "{\"label\": \"Надпись\", \"title\": \"Текст\"}");
    assertThat(own(item("Новая"))).extracting(i -> i.name).containsExactly("Надпись");

    EdtFormItemStructureEdit.delete(form, model, label);
    EdtFormItemStructureEdit.delete(form, model, newGroup);
    EdtFormItemStructureEdit.delete(form, model, check);
    assertThat(text()).isEqualTo(original);
  }

  @Test
  void renameCarriesServiceNodesAndReferences() throws Exception {
    FormItemDto table = item("СписокВзаимодействий");

    EdtFormItemStructureEdit.rename(form, model, table.id, "Переписка");

    String xml = text();
    assertThat(xml).contains("<name>Переписка</name>", "<name>ПерепискаКонтекстноеМеню</name>",
        "<name>ПерепискаКоманднаяПанель</name>", "<name>ПерепискаСтрокаПоиска</name>",
        "<name>ПерепискаСтрокаПоискаКонтекстноеМеню</name>", "<source>Переписка</source>");
    // Колонки таблицы - свои элементы: их имена и путь к данным не меняются
    assertThat(xml).contains("<name>СписокВзаимодействийДата</name>",
        "<segments>СписокВзаимодействий.Дата</segments>", "<segments>СписокВзаимодействий</segments>");
    assertThat(xml).doesNotContain("<source>СписокВзаимодействий</source>");
    assertThat(Files.readString(appearance, StandardCharsets.UTF_8)).contains("<field>Переписка</field>");

    EdtFormItemStructureEdit.rename(form, model, table.id, "СписокВзаимодействий");
    assertThat(text()).isEqualTo(original);
    assertThat(Files.readString(appearance, StandardCharsets.UTF_8)).isEqualTo(originalAppearance);
  }

  @Test
  void moveAndBackRestoresFile() throws Exception {
    FormItemDto footer = item("ПодвалПереписки");
    FormItemDto owner = parentOf(footer.id);
    List<FormItemDto> siblings = own(owner);
    int at = indexOf(siblings, footer.id);
    String next = at + 1 < siblings.size() ? siblings.get(at + 1).id : null;

    EdtFormItemStructureEdit.move(form, model, footer.id, null, null);
    assertThat(own(content().items)).last().extracting(i -> i.name).isEqualTo("ПодвалПереписки");
    assertThat(item("ПодвалПереписки").id).isEqualTo(footer.id);

    EdtFormItemStructureEdit.move(form, model, footer.id, owner.id, next);
    assertThat(text()).isEqualTo(original);
  }

  @Test
  void referencedElementIsNotDeleted() throws Exception {
    // На таблицу ссылается условное оформление формы
    String table = item("СписокВзаимодействий").id;
    assertThatThrownBy(() -> EdtFormItemStructureEdit.delete(form, model, table))
        .hasMessageContaining("СписокВзаимодействий").hasMessageContaining("условное оформление");
    String group = parentOf(table).id;
    assertThatThrownBy(() -> EdtFormItemStructureEdit.delete(form, model, group))
        .hasMessageContaining("СписокВзаимодействий");
    assertThat(text()).isEqualTo(original);
  }

  @Test
  void serviceNodesAndWrongPlacesAreRefused() throws Exception {
    String tooltip = item("ПодвалПерепискиРасширеннаяПодсказка").id;
    String table = item("СписокВзаимодействий").id;
    assertThatThrownBy(() -> EdtFormItemStructureEdit.add(form, model, table, null, "{\"group\": \"Г\"}"))
        .hasMessageContaining("не размещается");
    assertThatThrownBy(() -> EdtFormItemStructureEdit.add(form, model, null, null, "{\"page\": \"Стр\"}"))
        .hasMessageContaining("не размещается");
    assertThatThrownBy(() -> EdtFormItemStructureEdit.add(form, model, null, null,
        "{\"input\": \"ПодвалПереписки\"}")).hasMessageContaining("уже есть");
    assertThatThrownBy(() -> EdtFormItemStructureEdit.rename(form, model,
        item("ПодвалПереписки").id, "КнопкаЛево")).hasMessageContaining("уже есть");
    assertThatThrownBy(() -> EdtFormItemStructureEdit.delete(form, model, tooltip))
        .hasMessageContaining("Служебный узел");
    assertThat(text()).isEqualTo(original);
  }

  @Test
  void bindChecksDataPath() throws Exception {
    String field = item("СписокВзаимодействийДата").id;

    EdtFormItemStructureEdit.bind(form, model, field, "СписокВзаимодействий.Описание");
    assertThat(text()).contains("<segments>СписокВзаимодействий.Описание</segments>");

    assertThatThrownBy(() -> EdtFormItemStructureEdit.bind(form, model, field, "НетТакого"))
        .hasMessageContaining("нет реквизита НетТакого");
    assertThatThrownBy(() -> EdtFormItemStructureEdit.bind(form, model, field, "СписокВзаимодействий.Нет"))
        .hasMessageContaining("нет колонки Нет");
    assertThatThrownBy(() -> EdtFormItemStructureEdit.bind(form, model, field, "Объект.Нет"))
        .hasMessageContaining("нет реквизита Нет");

    EdtFormItemStructureEdit.bind(form, model, field, "СписокВзаимодействий.Дата");
    assertThat(text()).isEqualTo(original);
  }

  @Test
  void bindWritesPathToUnboundFieldAndChecksObject() throws Exception {
    // Владелец формы узнаётся по каталогу: копия лежит под тем же именем
    Path document = copy(src.resolve("Documents/_ДемоПоручениеЭкспедитору"),
        temp.resolve("Documents/_ДемоПоручениеЭкспедитору"));
    Path content = document.resolve("Forms/ФормаДокумента/Form.form");
    String added = EdtFormItemStructureEdit.add(content, model, null, null, "{\"input\": \"НовоеПоле\"}");

    EdtFormItemStructureEdit.bind(content, model, added, "Объект.ДатаВыполнения");
    EdtFormItemStructureEdit.bind(content, model, added, "Объект.Number");
    assertThat(Files.readString(content, StandardCharsets.UTF_8)).contains("<segments>Объект.Number</segments>");
    assertThatThrownBy(() -> EdtFormItemStructureEdit.bind(content, model, added, "Объект.Нет"))
        .hasMessageContaining("нет реквизита Нет");
  }

  /**
   * Новый элемент записан так же, как 1С:EDT записала элемент с тем же описанием у выгрузки
   * конфигуратора ssl31: эталон - пара из ssl31 и ssl31-edt, имена и номера не в счёт.
   */
  @ParameterizedTest
  @CsvSource(delimiter = '|', value = {
    "Documents/_ДемоПоручениеЭкспедитору/Forms/ФормаДокумента|ДатаВыполнения|{\"input\": \"НовыйЭлемент\", \"dataPath\": \"Объект.ДатаВыполнения\"}",
    "Catalogs/МашиночитаемыеДоверенности/Forms/ФормаЭлемента|ПолномочияУтрачиваютсяПриПередоверии|{\"check\": \"НовыйЭлемент\", \"dataPath\": \"Объект.ПолномочияУтрачиваютсяПриПередоверии\"}",
    "CommonForms/ПоддерживаемыеКлиентскиеПриложения|MacOSChrome|{\"label\": \"НовыйЭлемент\", \"title\": \"TITLE\"}",
    "Catalogs/СертификатыКлючейЭлектроннойПодписиИШифрования/Forms/ПодписаниеДанных|ГруппаПодписание|{\"group\": \"НовыйЭлемент\", \"title\": \"TITLE\", \"direction\": \"vertical\"}",
    "Documents/Встреча/Forms/ФормаДокумента|СтраницаОписание|{\"page\": \"НовыйЭлемент\", \"title\": \"TITLE\"}",
    "Catalogs/ПодписантыСервисаМобильнойПодписи/Forms/ФормаЭлемента|ГруппаСтраницы|{\"pages\": \"НовыйЭлемент\"}"})
  void newElementMatchesEdtRecordOfSameDescription(String formDir, String sample, String definition)
      throws Exception {
    Path copy = copy(src.resolve(formDir), temp.resolve("pair"));
    String xml = Files.readString(copy, StandardCharsets.UTF_8);
    String expected = element(xml, sample);
    Matcher title = Pattern.compile("^  <title>\\n    <key>ru</key>\\n    <value>([^<]*)</value>", Pattern.MULTILINE)
        .matcher(expected);
    String json = definition.replace("TITLE", title.find() ? title.group(1) : "");
    String parent = definition.contains("\"page\"") ? parentGroupOf(copy, sample) : null;

    EdtFormItemStructureEdit.add(copy, model, parent, null, json);

    if (!definition.contains("TITLE")) {
      // Заголовок страниц описание конфигуратора не пишет, а у эталона он есть
      expected = expected.replaceAll("(?s)\\n  <title>\\n.*?\\n  </title>", "");
    }
    String actual = element(Files.readString(copy, StandardCharsets.UTF_8), "НовыйЭлемент");
    assertThat(normalized(actual, "НовыйЭлемент")).isEqualTo(normalized(expected, sample));
  }

  /**
   * Таблица записана так, как 1С:EDT записала таблицу коллекции ssl31; у образца в конфигураторе
   * заданы представление-список и командная панель без автозаполнения, у новой таблицы - умолчания.
   */
  @Test
  void newTableMatchesEdtTableOfCollection() throws Exception {
    Path copy = copy(src.resolve("DataProcessors/МастерПереходаВОблако/Forms/МастерПереходаВОблако"),
        temp.resolve("table"));
    String expected = element(Files.readString(copy, StandardCharsets.UTF_8), "РасширенияДляВосстановления");

    String id = EdtFormItemStructureEdit.add(copy, model, null, null,
        "{\"table\": \"НовыйЭлемент\", \"dataPath\": \"РасширенияДляВосстановления\"}");

    // На Windows эталоны выгружаются с CRLF
    String xml = Files.readString(copy, StandardCharsets.UTF_8).replace("\r\n", "\n");
    String actual = normalized(element(xml, "НовыйЭлемент"), "НовыйЭлемент")
        .replace("  <representation>HierarchicalList</representation>\n", "")
        .replace("    <autoFill>true</autoFill>\n  </autoCommandBar>", "  </autoCommandBar>");
    assertThat(actual).isEqualTo(normalized(expected, "РасширенияДляВосстановления"));
    List<FormItemDto> all = new ArrayList<>();
    collect(EdtFormContent.read(copy, model).items, all);
    assertThat(all).filteredOn(i -> "НовыйЭлемент".equals(i.name)).singleElement()
        .satisfies(table -> assertThat(table.id).isEqualTo(id));
    // Номера служебных узлов - как у формы конфигуратора: меню, панель, подсказка, дополнения
    int first = Integer.parseInt(id);
    assertThat(xml).contains("<name>НовыйЭлементУправлениеПоискомРасширеннаяПодсказка</name>\n"
        + "        <id>" + (first + 12) + "</id>");
  }

  @Test
  void dynamicListTableIsRefused() throws Exception {
    Path copy = copy(src.resolve("Catalogs/Валюты/Forms/ФормаСписка"), temp.resolve("list"));
    assertThatThrownBy(() -> EdtFormItemStructureEdit.add(copy, model, null, null,
        "{\"table\": \"ЕщёСписок\", \"dataPath\": \"Список\"}")).hasMessageContaining("динамического списка");
  }

  @Test
  void newFormWithoutXsiPrefixGetsIt() throws Exception {
    Path copy = temp.resolve("new/Form.form");
    Files.createDirectories(copy.getParent());
    try (var in = EdtFormItemStructureEditTest.class.getResourceAsStream("/edt-golden/Forms/ФормаЭлемента/Form.form")) {
      Files.copy(in, copy);
    }
    EdtFormItemStructureEdit.add(copy, model, null, null, "{\"input\": \"Поле\"}");
    // На Windows эталон ресурса выгружается с CRLF
    String xml = Files.readString(copy, StandardCharsets.UTF_8).replace("\r\n", "\n");
    assertThat(xml).startsWith("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
        + "<form:Form xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xmlns:form=\"http://g5.1c.ru/v8/dt/form\">\n"
        + "  <items xsi:type=\"form:FormField\">\n"
        + "    <name>Поле</name>\n");
    assertThat(EdtFormContent.read(copy, model).items).extracting(i -> i.name).contains("Поле");
  }

  @Test
  void everyFifthSslFormSurvivesInverseEdits() throws Exception {
    List<Path> forms;
    try (Stream<Path> files = Files.walk(src)) {
      forms = files.filter(f -> f.getFileName().toString().equals("Form.form")).sorted().toList();
    }
    int checked = 0;
    for (int i = 0; i < forms.size(); i += 5) {
      Path copy = temp.resolve("forms").resolve(String.valueOf(i)).resolve("Form.form");
      Files.createDirectories(copy.getParent());
      Files.copy(forms.get(i), copy);
      String before = Files.readString(copy, StandardCharsets.UTF_8);
      List<FormItemDto> own = own(EdtFormContent.read(copy, model).items);
      if (own.isEmpty()) {
        continue;
      }
      FormItemDto first = own.get(0);
      String what = forms.get(i).toString();

      EdtFormItemStructureEdit.rename(copy, model, first.id, "ПереименованныйЭлемент");
      EdtFormItemStructureEdit.rename(copy, model, first.id, first.name);
      assertThat(Files.readString(copy, StandardCharsets.UTF_8)).as("переименование " + what).isEqualTo(before);

      String added = EdtFormItemStructureEdit.add(copy, model, null, first.id, "{\"label\": \"НоваяНадпись\"}");
      EdtFormItemStructureEdit.delete(copy, model, added);
      assertThat(Files.readString(copy, StandardCharsets.UTF_8)).as("добавление " + what).isEqualTo(before);

      if (own.size() > 1) {
        EdtFormItemStructureEdit.move(copy, model, first.id, null, null);
        EdtFormItemStructureEdit.move(copy, model, first.id, null, own.get(1).id);
        assertThat(Files.readString(copy, StandardCharsets.UTF_8)).as("перенос " + what).isEqualTo(before);
      }
      checked++;
    }
    assertThat(checked).isGreaterThan(150);
  }

  // ---------- помощники ----------

  /** Копирует каталог формы или объекта; возвращает {@code Form.form} либо каталог копии. */
  private static Path copy(Path source, Path target) throws IOException {
    try (Stream<Path> files = Files.walk(source)) {
      for (Path file : files.filter(Files::isRegularFile).toList()) {
        Path to = target.resolve(source.relativize(file).toString());
        Files.createDirectories(to.getParent());
        Files.copy(file, to);
      }
    }
    Path content = target.resolve("Form.form");
    return Files.exists(content) ? content : target;
  }

  /**
   * Текст элемента по имени, с отступом от его строки. Перевод строки приводится к {@code \n}:
   * на Windows эталоны выгружаются с CRLF, и новый элемент пишется так же.
   */
  private static String element(String text, String name) {
    String xml = text.replace("\r\n", "\n");
    Matcher m = Pattern.compile("\\n( *)<items xsi:type=\"[^\"]*\">\\n\\1  <name>" + Pattern.quote(name)
        + "</name>\\n.*?\\n\\1</items>", Pattern.DOTALL).matcher(xml);
    assertThat(m.find()).as("элемент " + name).isTrue();
    String indent = m.group(1);
    StringBuilder out = new StringBuilder();
    for (String line : m.group().substring(1).split("\n")) {
      out.append(line.substring(Math.min(indent.length(), line.length()))).append('\n');
    }
    return out.toString();
  }

  /** Без вложенных элементов, имён и номеров: остаётся то, что записано по описанию. */
  private static String normalized(String element, String name) {
    return element
        .replaceAll("(?s)\\n  <items xsi:type=[^>]*>\\n.*?\\n  </items>", "")
        .replaceAll("<segments>[^<]*</segments>", "<segments>P</segments>")
        .replaceAll("<id>-?\\d+</id>", "<id>N</id>")
        .replace(name, "NAME");
  }

  private String parentGroupOf(Path content, String name) throws IOException {
    FormContentDto read = EdtFormContent.read(content, model);
    List<FormItemDto> all = new ArrayList<>();
    collect(read.items, all);
    return all.stream().filter(i -> i.items.stream().anyMatch(child -> name.equals(child.name)))
        .findFirst().orElseThrow().id;
  }

  private String text() throws IOException {
    return Files.readString(form, StandardCharsets.UTF_8);
  }

  private FormContentDto content() throws IOException {
    return EdtFormContent.read(form, model);
  }

  private FormItemDto item(String name) throws IOException {
    return all().stream().filter(i -> name.equals(i.name)).findFirst()
        .orElseThrow(() -> new AssertionError("нет элемента " + name));
  }

  /** Вложенные элементы без служебных узлов. */
  private static List<FormItemDto> own(FormItemDto owner) {
    return own(owner.items);
  }

  private static List<FormItemDto> own(List<FormItemDto> items) {
    return items.stream().filter(i -> !ATTACHED.contains(i.type)).toList();
  }

  private FormItemDto parentOf(String id) throws IOException {
    return all().stream().filter(i -> i.items.stream().anyMatch(child -> id.equals(child.id))).findFirst()
        .orElseGet(() -> {
          FormItemDto root = new FormItemDto();
          try {
            root.items = content().items;
          } catch (IOException e) {
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

  private int maxId() throws IOException {
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

  private List<FormItemDto> all() throws IOException {
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
