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
import java.util.List;
import java.util.stream.Stream;

/**
 * Правка состава формы на форме ssl31 с реквизитом-таблицей, условным оформлением, командами кнопок,
 * параметром и обработчиками. Каждая правка проверяется обратной: после неё файл байт в байт прежний.
 */
class FormCompositionEditTest {

  private static final String PROCESSOR = "src/cf/DataProcessors/ИнформационныйЦентр";
  private static final String FORM = "Forms/ВзаимодействияПоОбращению/Ext/Form.xml";
  private static final SchemaVersion V = SchemaVersion.V2_20;

  @TempDir
  Path temp;

  private Path form;
  private String original;

  @BeforeEach
  void copyProcessor() throws IOException {
    Path source = Ssl31SubmodulePaths.projectRoot().resolve(PROCESSOR);
    Path target = temp.resolve("cf/DataProcessors").resolve(source.getFileName());
    try (Stream<Path> files = Files.walk(source)) {
      for (Path file : files.filter(Files::isRegularFile).toList()) {
        Path to = target.resolve(source.relativize(file).toString());
        Files.createDirectories(to.getParent());
        Files.copy(file, to);
      }
    }
    form = target.resolve(FORM);
    original = text();
    assertThat(read(form)).contains("\r\n");
  }

  @Test
  void attributeWithColumnsIsAddedAfterLastAndDeletedBack() throws Exception {
    String id = FormCompositionEdit.addAttribute(form, V, "{\"name\": \"Товары\", \"title\": \"Товары\","
      + " \"type\": {\"types\": [\"v8:ValueTable\"]}, \"columns\": [{\"name\": \"Номенклатура\","
      + " \"type\": {\"types\": [\"cfg:CatalogRef.Пользователи\"]}}, {\"name\": \"Количество\", \"title\": \"Кол.\","
      + " \"type\": {\"types\": [\"xs:decimal\"], \"numberQualifiers\": {\"digits\": \"10\", \"fractionDigits\": \"3\","
      + " \"allowedSign\": \"ANY\"}}}]}");

    assertThat(id).isEqualTo("8");
    FormAttributeDto added = attribute("Товары");
    assertThat(added.columns).extracting(c -> c.name).containsExactly("Номенклатура", "Количество");
    String xml = text();
    // Реквизит встаёт перед условным оформлением, тип - без повторных объявлений префиксов корня
    assertThat(xml.indexOf("<Attribute name=\"Товары\" id=\"8\">")).isLessThan(xml.indexOf("<ConditionalAppearance>"));
    assertThat(xml).contains("\t\t\t\t<Column name=\"Количество\" id=\"2\">\n", "<v8:Type>cfg:CatalogRef.Пользователи</v8:Type>")
      .doesNotContain("xmlns:cfg=\"http://v8.1c.ru/8.1/data/enterprise/current-config\">cfg:");

    String column = FormCompositionEdit.addAttribute(form, V,
      "{\"name\": \"Товары.Сумма\", \"type\": {\"types\": [\"xs:decimal\"]}}");
    assertThat(column).isEqualTo("3");
    FormCompositionEdit.deleteAttribute(form, V, "Товары.Сумма");
    FormCompositionEdit.deleteAttribute(form, V, "Товары");
    assertThat(text()).isEqualTo(original);
  }

  @Test
  void valueListGetsItemTypeSettingsAndStringKeepsLocalPrefixOnlyWhenNeeded() throws Exception {
    FormCompositionEdit.addAttribute(form, V, "{\"name\": \"Список\", \"type\": {\"types\": [\"v8:ValueListType\"]}}");
    FormCompositionEdit.addAttribute(form, V,
      "{\"name\": \"Документ\", \"type\": {\"types\": [\"mxl:SpreadsheetDocument\"]}, \"savedData\": true}");

    assertThat(text()).contains("<Settings xsi:type=\"v8:TypeDescription\"/>",
      "<v8:Type xmlns:mxl=\"http://v8.1c.ru/8.2/data/spreadsheet\">mxl:SpreadsheetDocument</v8:Type>",
      "<SavedData>true</SavedData>");
    FormCompositionEdit.deleteAttribute(form, V, "Документ");
    FormCompositionEdit.deleteAttribute(form, V, "Список");
    assertThat(text()).isEqualTo(original);
  }

  @Test
  void renameAttributeCarriesDataPathsAndAppearance() throws Exception {
    FormCompositionEdit.renameAttribute(form, V, "СписокВзаимодействий", "Переписка");

    String xml = text();
    assertThat(xml).contains("<Attribute name=\"Переписка\" id=\"3\">", "<DataPath>Переписка</DataPath>",
      "<DataPath>Переписка.Дата</DataPath>", "<dcsset:left xsi:type=\"dcscor:Field\">Переписка.Просмотрено</dcsset:left>");
    // Поле оформляемых элементов - имя таблицы формы, а не реквизита: оно не меняется
    assertThat(xml).contains("<dcsset:field>СписокВзаимодействий</dcsset:field>")
      .doesNotContain("<DataPath>СписокВзаимодействий");
    assertThat(attribute("Переписка").columns).hasSize(10);

    FormCompositionEdit.renameAttribute(form, V, "Переписка", "СписокВзаимодействий");
    assertThat(text()).isEqualTo(original);
  }

  @Test
  void renameColumnCarriesItsPathsOnly() throws Exception {
    FormCompositionEdit.renameAttribute(form, V, "СписокВзаимодействий.Просмотрено", "Прочитано");

    assertThat(text()).contains("<Column name=\"Прочитано\" id=\"10\">",
      "<dcsset:left xsi:type=\"dcscor:Field\">СписокВзаимодействий.Прочитано</dcsset:left>");
    FormCompositionEdit.renameAttribute(form, V, "СписокВзаимодействий.Прочитано", "Просмотрено");
    assertThat(text()).isEqualTo(original);
  }

  @Test
  void referencedAttributeAndCommandAreNotDeleted() throws Exception {
    assertThatThrownBy(() -> FormCompositionEdit.deleteAttribute(form, V, "СписокВзаимодействий"))
      .hasMessageContaining("ссылается форма");
    assertThatThrownBy(() -> FormCompositionEdit.deleteAttribute(form, V, "СписокВзаимодействий.Просмотрено"))
      .hasMessageContaining("условное оформление");
    assertThatThrownBy(() -> FormCompositionEdit.deleteCommand(form, V, "Рассмотрено"))
      .hasMessageContaining("CommandName");
    assertThat(text()).isEqualTo(original);
  }

  @Test
  void wrongDefinitionsAreRefused() throws Exception {
    assertThatThrownBy(() -> FormCompositionEdit.addAttribute(form, V,
      "{\"name\": \"Рассмотрено\", \"type\": {\"types\": [\"xs:boolean\"]}}")).hasMessageContaining("уже есть");
    assertThatThrownBy(() -> FormCompositionEdit.addAttribute(form, V, "{\"name\": \"Без\"}"))
      .hasMessageContaining("Не задан тип");
    assertThatThrownBy(() -> FormCompositionEdit.addAttribute(form, V,
      "{\"name\": \"Список\", \"type\": {\"types\": [\"cfg:DynamicList\"]}}")).hasMessageContaining("Динамический");
    assertThatThrownBy(() -> FormCompositionEdit.addAttribute(form, V,
      "{\"name\": \"Рассмотрено.Колонка\", \"type\": {\"types\": [\"xs:boolean\"]}}"))
      .hasMessageContaining("не таблица");
    assertThatThrownBy(() -> FormCompositionEdit.addCommand(form, V, "{\"name\": \"Рассмотрено\"}"))
      .hasMessageContaining("уже есть");
    assertThatThrownBy(() -> FormCompositionEdit.setEvent(form, V, null, "При открытии", "Открыть", null))
      .hasMessageContaining("латиницей");
    assertThatThrownBy(() -> FormCompositionEdit.setEvent(form, V, null, "OnOpen", "Открыть", "Instead"))
      .hasMessageContaining("Вид вызова");
    assertThatThrownBy(() -> FormCompositionEdit.addAttribute(form, V, "{не json"))
      .hasMessageContaining("payloadJson");
    assertThat(text()).isEqualTo(original);
  }

  @Test
  void setAttributeTitleAndTypeInPlatformOrder() throws Exception {
    FormCompositionEdit.setAttribute(form, V, "Рассмотрено", "{\"title\": \"Прочитано\","
      + " \"type\": {\"types\": [\"xs:string\"], \"stringQualifiers\": {\"length\": \"10\", \"allowedLength\": \"VARIABLE\"}}}");

    FormAttributeDto attribute = attribute("Рассмотрено");
    assertThat(attribute.title).isEqualTo("Прочитано");
    assertThat(attribute.type.types).containsExactly("xs:string");
    String xml = text();
    int start = xml.indexOf("<Attribute name=\"Рассмотрено\"");
    assertThat(xml.indexOf("<Title>", start)).isLessThan(xml.indexOf("<Type>", start));
  }

  @Test
  void commandAddRenameCarriesButtonsAndSetsParts() throws Exception {
    String id = FormCompositionEdit.addCommand(form, V, "{\"name\": \"Обновить\", \"title\": \"Обновить\"}");
    assertThat(id).isEqualTo("3");
    assertThat(text()).contains("<Command name=\"Обновить\" id=\"3\">", "<Action>Обновить</Action>");
    FormCompositionEdit.deleteCommand(form, V, "Обновить");
    assertThat(text()).isEqualTo(original);

    FormCompositionEdit.renameCommand(form, V, "Рассмотрено", "Прочитано");
    assertThat(text()).contains("<CommandName>Form.Command.Прочитано</CommandName>")
      .doesNotContain("Form.Command.Рассмотрено");
    FormCompositionEdit.renameCommand(form, V, "Прочитано", "Рассмотрено");
    assertThat(text()).isEqualTo(original);

    FormCompositionEdit.setCommand(form, V, "ДобавитьКомментарий", "{\"action\": \"Комментировать\"}");
    assertThat(content().commands).filteredOn(c -> "ДобавитьКомментарий".equals(c.name))
      .extracting(c -> c.action).containsExactly("Комментировать");
  }

  @Test
  void parameterAddKeySetAndDelete() throws Exception {
    FormCompositionEdit.addParameter(form, V, "{\"name\": \"Отбор\", \"type\": {\"types\": [\"xs:string\"]}}");
    FormCompositionEdit.setParameter(form, V, "Отбор", "{\"key\": true}");
    assertThat(content().parameters).filteredOn(p -> "Отбор".equals(p.name)).extracting(p -> p.key)
      .containsExactly(true);
    FormCompositionEdit.setParameter(form, V, "Отбор", "{\"key\": false}");
    FormCompositionEdit.renameParameter(form, V, "Отбор", "Фильтр");
    FormCompositionEdit.deleteParameter(form, V, "Фильтр");
    assertThat(text()).isEqualTo(original);

    // Последний параметр уносит с собой и список
    FormCompositionEdit.deleteParameter(form, V, "ИдентификаторОбращения");
    assertThat(text()).doesNotContain("<Parameters>");
    FormCompositionEdit.addParameter(form, V,
      "{\"name\": \"ИдентификаторОбращения\", \"type\": {\"types\": [\"xs:string\"], \"stringQualifiers\":"
        + " {\"length\": \"0\", \"allowedLength\": \"VARIABLE\"}}}");
    assertThat(text()).contains("<Parameters>");
  }

  @Test
  void eventsAreSetReplacedAndRemoved() throws Exception {
    FormCompositionEdit.setEvent(form, V, null, "OnOpen", "ПриОткрытии", null);
    assertThat(content().events).extracting(e -> e.name).containsExactly(
      "NotificationProcessing", "OnCreateAtServer", "OnOpen");
    FormCompositionEdit.setEvent(form, V, null, "OnOpen", "", null);
    assertThat(text()).isEqualTo(original);

    // У надписи номера страницы обработчиков нет: блок встаёт за её служебными узлами
    String label = item("ТекущаяСтраница").id;
    FormCompositionEdit.setEvent(form, V, label, "Click", "ТекущаяСтраницаНажатие", "After");
    assertThat(item("ТекущаяСтраница").events).singleElement().satisfies(e -> {
      assertThat(e.handler).isEqualTo("ТекущаяСтраницаНажатие");
      assertThat(e.callType).isEqualTo("After");
    });
    FormCompositionEdit.setEvent(form, V, label, "Click", null, null);
    assertThat(text()).isEqualTo(original);

    String table = item("СписокВзаимодействий").id;
    FormCompositionEdit.setEvent(form, V, table, "Selection", "Выбор", null);
    assertThat(text()).contains("<Event name=\"Selection\">Выбор</Event>");
    FormCompositionEdit.setEvent(form, V, table, "Selection", "СписокВзаимодействийВыбор", null);
    assertThat(text()).isEqualTo(original);
  }

  @Test
  void eventAddsMainAttributeDefaultsLikePlatform822() throws Exception {
    Path catalog = mainAttributeForm("cfg:CatalogObject.Спр", "");
    FormCompositionEdit.setEvent(catalog, SchemaVersion.V2_15, null, "OnOpen", "ПриОткрытии", null);
    FormCompositionEdit.setEvent(catalog, SchemaVersion.V2_15, null, "OnClose", "ПриЗакрытии", null);
    assertThat(read(catalog)).contains("\t<UseForFoldersAndItems>Items</UseForFoldersAndItems>\n"
      + "\t<AutoCommandBar name=\"ФормаКоманднаяПанель\" id=\"-1\"/>\n"
      + "\t<Events>\n\t\t<Event name=\"OnOpen\">ПриОткрытии</Event>\n\t\t<Event name=\"OnClose\">ПриЗакрытии</Event>");
    assertThat(read(catalog).split("UseForFoldersAndItems", -1)).hasSize(3);

    // Обработчик элемента платформа 8.3.24 дополняет так же
    Path item = mainAttributeForm("cfg:DocumentObject.Док", "");
    Files.writeString(item, read(item).replace("\t<Attributes>", "\t<ChildItems>\n"
      + "\t\t<InputField name=\"Номер\" id=\"2\">\n\t\t\t<DataPath>Объект.Number</DataPath>\n"
      + "\t\t\t<ContextMenu name=\"НомерКонтекстноеМеню\" id=\"3\"/>\n"
      + "\t\t\t<ExtendedTooltip name=\"НомерРасширеннаяПодсказка\" id=\"4\"/>\n"
      + "\t\t</InputField>\n\t</ChildItems>\n\t<Attributes>"), StandardCharsets.UTF_8);
    FormCompositionEdit.setEvent(item, SchemaVersion.V2_17, "2", "OnChange", "НомерПриИзменении", null);
    assertThat(read(item)).contains("\t<AutoTime>CurrentOrLast</AutoTime>\n\t<UsePostingMode>Auto</UsePostingMode>\n"
      + "\t<RepostOnWrite>true</RepostOnWrite>\n\t<AutoCommandBar", "<Event name=\"OnChange\">НомерПриИзменении</Event>");

    // С 2.18 свойства пишет уже сборка формы, 2.20 без них платформа тоже примет как есть
    Path other = mainAttributeForm("cfg:CatalogObject.Спр", "");
    FormCompositionEdit.setEvent(other, V, null, "OnOpen", "ПриОткрытии", null);
    assertThat(read(other)).doesNotContain("UseForFoldersAndItems");

    // Свойства отчёта встают по порядку схемы вокруг тех, что уже есть
    Path report = mainAttributeForm("cfg:ReportObject.Отчет", "\t<VariantAppearance>Оформление</VariantAppearance>\n");
    FormCompositionEdit.setEvent(report, SchemaVersion.V2_16, null, "OnOpen", "ПриОткрытии", null);
    assertThat(read(report)).contains("\t<ReportFormType>Main</ReportFormType>\n"
      + "\t<VariantAppearance>Оформление</VariantAppearance>\n"
      + "\t<AutoShowState>Auto</AutoShowState>\n"
      + "\t<ReportResultViewMode>Auto</ReportResultViewMode>\n"
      + "\t<ViewModeApplicationOnSetReportResult>Auto</ViewModeApplicationOnSetReportResult>\n"
      + "\t<AutoCommandBar");
  }

  private Path mainAttributeForm(String type, String properties) throws IOException {
    Path file = Files.createTempDirectory(temp, "form").resolve("Form.xml");
    Files.writeString(file, "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
      + "<Form xmlns=\"http://v8.1c.ru/8.3/xcf/logform\" xmlns:cfg=\"http://v8.1c.ru/8.1/data/enterprise/current-config\""
      + " xmlns:v8=\"http://v8.1c.ru/8.1/data/core\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\""
      + " version=\"2.15\">\n"
      + properties
      + "\t<AutoCommandBar name=\"ФормаКоманднаяПанель\" id=\"-1\"/>\n"
      + "\t<Attributes>\n\t\t<Attribute name=\"Объект\" id=\"1\">\n\t\t\t<Type>\n\t\t\t\t<v8:Type>" + type
      + "</v8:Type>\n\t\t\t</Type>\n\t\t\t<MainAttribute>true</MainAttribute>\n\t\t\t<SavedData>true</SavedData>\n"
      + "\t\t</Attribute>\n\t</Attributes>\n</Form>", StandardCharsets.UTF_8);
    return file;
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
      FormContentDto content = FormContentRead.read(copy, V);
      String what = forms.get(i).toString();

      FormCompositionEdit.addAttribute(copy, V, "{\"name\": \"НовыйРеквизит\", \"type\": {\"types\": [\"xs:boolean\"]}}");
      FormCompositionEdit.deleteAttribute(copy, V, "НовыйРеквизит");
      assertThat(read(copy)).as("реквизит " + what).isEqualTo(before);

      FormCompositionEdit.addCommand(copy, V, "{\"name\": \"НоваяКоманда\", \"title\": \"Команда\"}");
      FormCompositionEdit.deleteCommand(copy, V, "НоваяКоманда");
      FormCompositionEdit.addParameter(copy, V, "{\"name\": \"НовыйПараметр\", \"type\": {\"types\": [\"xs:string\"]}}");
      FormCompositionEdit.deleteParameter(copy, V, "НовыйПараметр");
      FormCompositionEdit.setEvent(copy, V, null, "ExternalEvent", "НовыйОбработчик", null);
      FormCompositionEdit.setEvent(copy, V, null, "ExternalEvent", null, null);
      if (content.events.stream().noneMatch(e -> "ExternalEvent".equals(e.name))) {
        assertThat(read(copy)).as("команда, параметр, событие " + what).isEqualTo(before);
      }

      if (!content.attributes.isEmpty()) {
        String first = content.attributes.get(0).name;
        FormCompositionEdit.renameAttribute(copy, V, first, "ПереименованныйРеквизит");
        FormCompositionEdit.renameAttribute(copy, V, "ПереименованныйРеквизит", first);
        assertThat(read(copy)).as("переименование реквизита " + what).isEqualTo(before);
      }
      if (!content.commands.isEmpty()) {
        String first = content.commands.get(0).name;
        FormCompositionEdit.renameCommand(copy, V, first, "ПереименованнаяКоманда");
        FormCompositionEdit.renameCommand(copy, V, "ПереименованнаяКоманда", first);
        assertThat(read(copy)).as("переименование команды " + what).isEqualTo(before);
      }
      checked++;
    }
    assertThat(checked).isGreaterThan(200);
  }

  // ---------- помощники ----------

  private static String read(Path file) throws IOException {
    return Files.readString(file, StandardCharsets.UTF_8);
  }

  /** Текст формы с переводом строки {@code \n}: эталоны ssl31 выгружены с CRLF. */
  private String text() throws IOException {
    return read(form).replace("\r\n", "\n");
  }

  private FormContentDto content() throws Exception {
    return FormContentRead.read(form, V);
  }

  private FormAttributeDto attribute(String name) throws Exception {
    return content().attributes.stream().filter(a -> name.equals(a.name)).findFirst()
      .orElseThrow(() -> new AssertionError("нет реквизита " + name));
  }

  private FormItemDto item(String name) throws Exception {
    return find(content().items, name);
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
}
