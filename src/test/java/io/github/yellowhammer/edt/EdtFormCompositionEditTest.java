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
 * Правка состава формы EDT: обратимость на форме ssl31-edt и запись новых реквизитов, команд,
 * параметров и обработчиков так, как их записала 1С:EDT для того же описания.
 */
class EdtFormCompositionEditTest {

  private static final String PROCESSOR = "DataProcessors/ИнформационныйЦентр";
  private static final String FORM = "Forms/ВзаимодействияПоОбращению/Form.form";

  private static EdtModel model;
  private static Path src;
  /** Проект, который записала 1С:EDT для той же платформы, что у ssl31-edt. */
  private static Path project;

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
    project = Path.of("src", "test", "resources", "edt-forms-platform",
        EdtProjectManifest.runtimeVersion(src.getParent()).orElseThrow(), "Формы").toAbsolutePath();
  }

  @BeforeEach
  void copyForm() throws IOException {
    form = copy(src.resolve(PROCESSOR), temp.resolve(PROCESSOR)).resolve(FORM);
    appearance = form.resolveSibling("ConditionalAppearance.dcssca");
    original = text(form);
    originalAppearance = text(appearance);
  }

  @Test
  void attributeCommandParameterAddedAndDeletedBack() throws Exception {
    String id = EdtFormCompositionEdit.addAttribute(form, model, "{\"name\": \"Товары\", \"type\": {\"types\":"
        + " [\"v8:ValueTable\"]}, \"columns\": [{\"name\": \"Цена\", \"type\": {\"types\": [\"xs:decimal\"]}}]}");
    assertThat(id).isEqualTo("8");
    String column = EdtFormCompositionEdit.addAttribute(form, model,
        "{\"name\": \"Товары.Сумма\", \"type\": {\"types\": [\"xs:decimal\"]}}");
    assertThat(column).isEqualTo("2");
    assertThat(content().attributes).filteredOn(a -> "Товары".equals(a.name)).singleElement()
        .satisfies(a -> assertThat(a.columns).extracting(c -> c.name).containsExactly("Цена", "Сумма"));
    EdtFormCompositionEdit.deleteAttribute(form, model, "Товары.Сумма");
    EdtFormCompositionEdit.deleteAttribute(form, model, "Товары");

    assertThat(EdtFormCompositionEdit.addCommand(form, model, "{\"name\": \"Обновить\", \"title\": \"Обновить\"}"))
        .isEqualTo("3");
    EdtFormCompositionEdit.deleteCommand(form, model, "Обновить");
    EdtFormCompositionEdit.addParameter(form, model, "{\"name\": \"Отбор\", \"type\": {\"types\": [\"xs:string\"]}}");
    EdtFormCompositionEdit.setParameter(form, model, "Отбор", "{\"key\": true}");
    assertThat(content().parameters).filteredOn(p -> "Отбор".equals(p.name)).extracting(p -> p.key)
        .containsExactly(true);
    EdtFormCompositionEdit.setParameter(form, model, "Отбор", "{\"key\": false}");
    EdtFormCompositionEdit.deleteParameter(form, model, "Отбор");
    assertThat(text(form)).isEqualTo(original);
  }

  @Test
  void renameAttributeCarriesSegmentsAndAppearance() throws Exception {
    EdtFormCompositionEdit.renameAttribute(form, model, "СписокВзаимодействий", "Переписка");

    assertThat(text(form)).contains("<segments>Переписка</segments>", "<segments>Переписка.Дата</segments>")
        .doesNotContain("<segments>СписокВзаимодействий");
    assertThat(text(appearance)).contains("<left xsi:type=\"dcscor:Field\">Переписка.Просмотрено</left>")
        .contains("<field>СписокВзаимодействий</field>");

    EdtFormCompositionEdit.renameAttribute(form, model, "Переписка", "СписокВзаимодействий");
    assertThat(text(form)).isEqualTo(original);
    assertThat(text(appearance)).isEqualTo(originalAppearance);
  }

  @Test
  void referencedAttributeAndCommandAreNotDeleted() throws Exception {
    assertThatThrownBy(() -> EdtFormCompositionEdit.deleteAttribute(form, model, "СписокВзаимодействий.Просмотрено"))
        .hasMessageContaining("условное оформление");
    assertThatThrownBy(() -> EdtFormCompositionEdit.deleteCommand(form, model, "Рассмотрено"))
        .hasMessageContaining("commandName");
    assertThatThrownBy(() -> EdtFormCompositionEdit.setEvent(form, model, null, "OnOpen", "Открыть", "After"))
        .hasMessageContaining("не поддержан");
    assertThatThrownBy(() -> EdtFormCompositionEdit.setEvent(form, model, null, "Неизвестное", "Открыть", null))
        .hasMessageContaining("латиницей");
    assertThatThrownBy(() -> EdtFormCompositionEdit.setEvent(form, model, null, "OnSomething", "Открыть", null))
        .hasMessageContaining("не известно");
    assertThat(text(form)).isEqualTo(original);
  }

  @Test
  void renameCommandCarriesButtons() throws Exception {
    EdtFormCompositionEdit.renameCommand(form, model, "Рассмотрено", "Прочитано");
    assertThat(text(form)).contains("<commandName>Form.Command.Прочитано</commandName>")
        .doesNotContain("Form.Command.Рассмотрено");
    EdtFormCompositionEdit.renameCommand(form, model, "Прочитано", "Рассмотрено");
    assertThat(text(form)).isEqualTo(original);

    EdtFormCompositionEdit.setCommand(form, model, "ДобавитьКомментарий", "{\"action\": \"Комментировать\"}");
    assertThat(content().commands).filteredOn(c -> "ДобавитьКомментарий".equals(c.name))
        .extracting(c -> c.action).containsExactly("Комментировать");
  }

  @Test
  void eventsGoToItemOrItsKindDescription() throws Exception {
    EdtFormCompositionEdit.setEvent(form, model, null, "OnOpen", "ПриОткрытии", null);
    assertThat(content().events).extracting(e -> e.name).contains("OnOpen");
    EdtFormCompositionEdit.setEvent(form, model, null, "OnOpen", null, null);
    assertThat(text(form)).isEqualTo(original);

    // Запись формы объекта EDT держит в описании вида формы, пустом у этой формы
    EdtFormCompositionEdit.setEvent(form, model, null, "OnReadAtServer", "ПриЧтенииНаСервере", null);
    assertThat(text(form)).contains("<extInfo xsi:type=\"form:ObjectFormExtInfo\">");
    assertThat(content().events).extracting(e -> e.name).contains("OnReadAtServer");

    String table = item("СписокВзаимодействий").id;
    EdtFormCompositionEdit.setEvent(form, model, table, "OnActivateRow", "СписокАктивизацияСтроки", null);
    EdtFormCompositionEdit.setEvent(form, model, table, "Selection", "Выбор", null);
    assertThat(item("СписокВзаимодействий").events).extracting(e -> e.handler)
        .contains("Выбор", "СписокАктивизацияСтроки");
  }

  /** Новая запись - как у 1С:EDT для того же описания в ssl31-edt; имена и номера не в счёт. */
  @ParameterizedTest
  @CsvSource(delimiter = '|', value = {
    "CommonForms/ПраваДоступаУпрощенно|attributes|Профили|attribute|{\"name\": \"NEW\", \"title\": \"Профили\", \"savedData\": true, \"type\": {\"types\": [\"v8:ValueTable\"]}, \"columns\": [{\"name\": \"Пометка\", \"title\": \"Пометка\", \"type\": {\"types\": [\"xs:boolean\"]}}, {\"name\": \"Профиль\", \"type\": {\"types\": [\"cfg:CatalogRef.ПрофилиГруппДоступа\"]}}, {\"name\": \"ПрофильПредставление\", \"title\": \"Профиль\", \"type\": {\"types\": [\"xs:string\"], \"stringQualifiers\": {\"length\": \"0\", \"allowedLength\": \"VARIABLE\"}}}, {\"name\": \"ГруппаДоступа\", \"type\": {\"types\": [\"cfg:CatalogRef.ГруппыДоступа\"]}}]}",
    "Catalogs/Валюты/Forms/ФормаЭлемента|attributes|ФормыВводаПрописей|attribute|{\"name\": \"NEW\", \"type\": {\"types\": [\"v8:ValueListType\"]}}",
    "Catalogs/Валюты/Forms/ФормаЭлемента|attributes|ПредставлениеФормулы|attribute|{\"name\": \"NEW\", \"type\": {\"types\": [\"xs:string\"], \"stringQualifiers\": {\"length\": \"0\", \"allowedLength\": \"VARIABLE\"}}}",
    "Catalogs/Валюты/Forms/ФормаЭлемента|formCommands|ПараметрыПрописиВалютыНаДругихЯзыках|command|{\"name\": \"NEW\", \"title\": \"На других языках...\", \"toolTip\": \"Параметры прописи валюты на других языках\", \"action\": \"NEW\"}",
    "CommonForms/ПраваДоступаУпрощенно|parameters|Пользователь|parameter|{\"name\": \"NEW\", \"key\": true, \"type\": {\"types\": [\"cfg:CatalogRef.ВнешниеПользователи\", \"cfg:CatalogRef.Пользователи\"]}}"})
  void newEntryMatchesEdtRecord(String formDir, String tag, String sample, String kind, String definition)
      throws Exception {
    Path copy = inProject(formDir);
    String expected = entry(text(copy), tag, sample);
    String json = definition.replace("NEW", "НоваяЗапись");
    switch (kind) {
      case "attribute" -> EdtFormCompositionEdit.addAttribute(copy, model, json);
      case "command" -> EdtFormCompositionEdit.addCommand(copy, model, json);
      default -> EdtFormCompositionEdit.addParameter(copy, model, json);
    }
    String actual = entry(text(copy), tag, "НоваяЗапись");
    assertThat(normalized(actual, "НоваяЗапись")).isEqualTo(normalized(expected, sample));
  }

  /**
   * Снятый и заново назначенный обработчик встаёт туда, где его держала 1С:EDT: у элемента, в описании
   * вида элемента или в описании вида формы.
   */
  @ParameterizedTest
  @CsvSource(delimiter = '|', value = {
    "Catalogs/Валюты/Forms/ФормаЭлемента|Код|OnChange|КодПриИзменении",
    "CommonForms/ПраваДоступаУпрощенно|ВидыДоступаВсеРазрешеныПредставление|ChoiceProcessing|ВидыДоступаВсеРазрешеныПредставлениеОбработкаВыбора",
    "Catalogs/Валюты/Forms/ФормаЭлемента||OnCreateAtServer|ПриСозданииНаСервере",
    "Documents/_ДемоПоручениеЭкспедитору/Forms/ФормаДокумента||OnReadAtServer|ПриЧтенииНаСервере"})
  void handlerReturnsToEdtPlace(String formDir, String itemName, String event, String handler) throws Exception {
    Path copy = copy(src.resolve(formDir), temp.resolve("handler"));
    String before = text(copy);
    String itemId = null;
    if (itemName != null) {
      List<FormItemDto> all = new ArrayList<>();
      collect(EdtFormContent.read(copy, model).items, all);
      itemId = all.stream().filter(i -> itemName.equals(i.name)).findFirst().orElseThrow().id;
    }
    EdtFormCompositionEdit.setEvent(copy, model, itemId, event, null, null);
    assertThat(text(copy)).doesNotContain("<name>" + handler + "</name>");
    EdtFormCompositionEdit.setEvent(copy, model, itemId, event, handler, null);
    assertThat(text(copy)).isEqualTo(before);
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
      String before = text(copy);
      FormContentDto content = EdtFormContent.read(copy, model);
      String what = forms.get(i).toString();

      EdtFormCompositionEdit.addAttribute(copy, model, "{\"name\": \"НовыйРеквизит\", \"type\": {\"types\": [\"xs:boolean\"]}}");
      EdtFormCompositionEdit.deleteAttribute(copy, model, "НовыйРеквизит");
      EdtFormCompositionEdit.addCommand(copy, model, "{\"name\": \"НоваяКоманда\", \"title\": \"Команда\"}");
      EdtFormCompositionEdit.deleteCommand(copy, model, "НоваяКоманда");
      EdtFormCompositionEdit.addParameter(copy, model, "{\"name\": \"НовыйПараметр\", \"type\": {\"types\": [\"xs:string\"]}}");
      EdtFormCompositionEdit.deleteParameter(copy, model, "НовыйПараметр");
      if (content.events.stream().noneMatch(e -> "ExternalEvent".equals(e.name))) {
        EdtFormCompositionEdit.setEvent(copy, model, null, "ExternalEvent", "НовыйОбработчик", null);
        EdtFormCompositionEdit.setEvent(copy, model, null, "ExternalEvent", null, null);
      }
      // Объявление префикса xsi, заведённое новой записью, остаётся и после её удаления
      String xsi = before.contains("xmlns:xsi=") ? "" : " xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"";
      assertThat(text(copy).replace("<form:Form" + xsi, "<form:Form")).as("добавление " + what).isEqualTo(before);

      if (!content.attributes.isEmpty()) {
        String first = content.attributes.get(0).name;
        EdtFormCompositionEdit.renameAttribute(copy, model, first, "ПереименованныйРеквизит");
        EdtFormCompositionEdit.renameAttribute(copy, model, "ПереименованныйРеквизит", first);
        assertThat(text(copy).replace("<form:Form" + xsi, "<form:Form")).as("переименование реквизита " + what).isEqualTo(before);
      }
      if (!content.commands.isEmpty()) {
        String first = content.commands.get(0).name;
        EdtFormCompositionEdit.renameCommand(copy, model, first, "ПереименованнаяКоманда");
        EdtFormCompositionEdit.renameCommand(copy, model, "ПереименованнаяКоманда", first);
        assertThat(text(copy).replace("<form:Form" + xsi, "<form:Form")).as("переименование команды " + what).isEqualTo(before);
      }
      checked++;
    }
    assertThat(checked).isGreaterThan(200);
  }

  // ---------- помощники ----------

  /** Копия формы ssl31-edt в копии проекта той же платформы: новая запись пишется по платформе проекта. */
  private Path inProject(String formDir) throws IOException {
    Path copy = temp.resolve(project.getFileName().toString());
    EdtExtensionScaffoldTest.copy(project, copy);
    return copy(src.resolve(formDir), copy.resolve("src").resolve(formDir));
  }

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

  /** Текст файла с переводом строки {@code \n}: на Windows эталоны выгружаются с CRLF. */
  private static String text(Path file) throws IOException {
    return Files.readString(file, StandardCharsets.UTF_8).replace("\r\n", "\n");
  }

  /** Запись корня формы по имени: {@code <attributes>}, {@code <formCommands>}, {@code <parameters>}. */
  private static String entry(String xml, String tag, String name) {
    Matcher m = Pattern.compile("\\n  <" + tag + ">\\n    <name>" + Pattern.quote(name) + "</name>\\n.*?\\n  </"
        + tag + ">", Pattern.DOTALL).matcher(xml);
    assertThat(m.find()).as(tag + " " + name).isTrue();
    return m.group();
  }

  /** Без номера и имени; у команды образца процедура названа так же, как она сама. */
  private static String normalized(String entry, String name) {
    return entry.replaceAll("<id>\\d+</id>", "<id>N</id>").replace("<name>" + name + "</name>", "<name>NAME</name>");
  }

  private FormContentDto content() throws IOException {
    return EdtFormContent.read(form, model);
  }

  private FormItemDto item(String name) throws IOException {
    List<FormItemDto> all = new ArrayList<>();
    collect(content().items, all);
    return all.stream().filter(i -> name.equals(i.name)).findFirst().orElseThrow();
  }

  private static void collect(List<FormItemDto> items, List<FormItemDto> out) {
    for (FormItemDto item : items) {
      out.add(item);
      collect(item.items, out);
    }
  }
}
