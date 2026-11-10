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

import com.google.gson.Gson;
import io.github.yellowhammer.designerxml.SchemaVersion;
import io.github.yellowhammer.designerxml.Ssl31SubmodulePaths;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Многострочный текст элементов схемы: сплайс не меняет текстовое содержимое — продолжения строк
 * не получают отступа тега, а переводы строк текста становятся переводами строки файла.
 *
 * <p>Для каждого места записи текста — значения параметров схемы и настроек, выражения вычисляемых
 * полей, ресурсов, связей и параметров, заголовки, представления, строки роли поля, тексты условного
 * оформления — запись многострочного текста и чтение тем же {@code cf-dcs-info} или
 * {@code cf-dcs-settings-info} дают текст дословно: пустые строки, ведущие табуляции строк и
 * пробелы в конце сохраняются, переводы строк в файле — файловые. Эхо прочитанного по корпусам
 * ssl31 и EDT-пары не меняет ни байта.
 */
class DcsMultilineTextTest {

  private static final SchemaVersion V = SchemaVersion.V2_20;

  /** Пустая строка, ведущие табуляции, пробелы в конце и символы, которые экранируются. */
  private static final String TEXT = "Первая строка\n\tвторая с табуляцией\n\n\t\tчетвёртая после пустой\nx < y & z  ";

  private static final String OTHER = "Другой текст\n\n\tс отступом\n";

  /** Запрос, восемь параметров, два ресурса и пять вариантов настроек. */
  private static final String FILES =
    "src/cf/Reports/_ДемоФайлы/Templates/ОсновнаяСхемаКомпоновкиДанных/Ext/Template.xml";

  /** Четыре связи наборов; у первой записан startExpression. */
  private static final String LINKS = "src/cf/Reports/АнализПравДоступа/Templates/Макет/Ext/Template.xml";

  private static final String DATA_SET = "НаборДанных1";

  private static final String VARIANT = "Основной";

  private static final Gson GSON = new Gson();

  @TempDir
  Path tempDir;

  // --------------------------------------------------------------- схема

  @Test
  void параметрСхемыПишетИЧитаетМногострочныеЗаголовокВыражениеИЗначение() throws Exception {
    Path dcs = copy(FILES);
    // Вставка нового элемента целиком
    DcsEdit.addParameter(dcs, V, Map.of("name", "Многострочный", "title", TEXT, "expression", TEXT,
      "value", Map.of("type", "xs:string", "text", TEXT)));
    Map<String, Object> added = parameter(dcs, "Многострочный");
    assertThat(added).containsEntry("title", TEXT).containsEntry("expression", TEXT).containsEntry("value", TEXT);
    assertThat(added.get("valueTyped")).isEqualTo(Map.of("type", "xs:string", "text", TEXT));
    assertVerbatimCrlf(dcs, TEXT);

    // Замена записанных элементов
    DcsEdit.setParameter(dcs, V, "Многострочный", Map.of("title", OTHER, "expression", OTHER,
      "value", Map.of("type", "xs:string", "text", OTHER)));
    Map<String, Object> replaced = parameter(dcs, "Многострочный");
    assertThat(replaced).containsEntry("title", OTHER).containsEntry("expression", OTHER);
    assertThat(replaced.get("valueTyped")).isEqualTo(Map.of("type", "xs:string", "text", OTHER));
    // Вставка отсутствующего элемента в существующий параметр
    DcsEdit.setParameter(dcs, V, "АвторыВерсий", Map.of("expression", TEXT));
    assertThat(parameter(dcs, "АвторыВерсий")).containsEntry("expression", TEXT);
    assertVerbatimCrlf(dcs, OTHER);
    assertVerbatimCrlf(dcs, TEXT);

    // Эхо прочитанного — ни байта
    byte[] before = Files.readAllBytes(dcs);
    DcsEdit.setParameter(dcs, V, "Многострочный", channel(Map.of("title", replaced.get("title"),
      "expression", replaced.get("expression"), "value", replaced.get("valueTyped"))));
    DcsEdit.setParameter(dcs, V, "АвторыВерсий", channel(Map.of("expression", TEXT)));
    assertThat(Files.readAllBytes(dcs)).isEqualTo(before);
  }

  @Test
  void значениеИзОднихПробеловИПереводовСтрокНеСхлопывается() throws Exception {
    Path dcs = copy(FILES);
    // Пустой элемент платформа пишет самозакрытым, поэтому пробелы между тегами значения — текст
    String blank = "\n\t\n";
    DcsEdit.addParameter(dcs, V, Map.of("name", "Пробелы", "value", Map.of("type", "xs:string", "text", blank)));
    assertThat(parameter(dcs, "Пробелы").get("valueTyped")).isEqualTo(Map.of("type", "xs:string", "text", blank));
    DcsSettingsEdit.setParameterValue(dcs, V, VARIANT, Map.of("section", "data", "parameter", "Пробелы",
      "value", Map.of("type", "xs:string", "text", blank)));
    assertThat(dataParameter(dcs, "Пробелы").get("value"))
      .isEqualTo(List.of(Map.of("type", "xs:string", "text", blank)));
    assertThat(Files.readString(dcs, StandardCharsets.UTF_8)).contains("\"xs:string\">\r\n\t\r\n</");
  }

  @Test
  void полеНабораПишетИЧитаетМногострочныеЗаголовокИСтрокиРоли() throws Exception {
    Path dcs = copy(FILES);
    DcsEdit.addField(dcs, V, DATA_SET, Map.of("dataPath", "Многострочное", "field", "Многострочное", "title", TEXT));
    Map<String, Object> added = field(dcs, "Многострочное");
    assertThat(added).containsEntry("title", TEXT).containsEntry("titleLangs", Map.of("ru", TEXT));
    // Замена записанного заголовка и вставка отсутствующего вместе с ролью
    DcsEdit.setField(dcs, V, DATA_SET, "Редактирует", Map.of("title", OTHER));
    DcsEdit.setField(dcs, V, DATA_SET, "Зашифрован", Map.of("title", TEXT,
      "role", Map.of("accountTypeExpression", TEXT, "balanceGroupName", OTHER)));
    assertThat(field(dcs, "Редактирует")).containsEntry("title", OTHER);
    Map<String, Object> encrypted = field(dcs, "Зашифрован");
    assertThat(encrypted).containsEntry("title", TEXT);
    assertThat(encrypted.get("role")).isEqualTo(Map.of("accountTypeExpression", TEXT, "balanceGroupName", OTHER));
    assertVerbatimCrlf(dcs, TEXT);
    assertVerbatimCrlf(dcs, OTHER);

    byte[] before = Files.readAllBytes(dcs);
    DcsEdit.setField(dcs, V, DATA_SET, "Многострочное", channel(Map.of("title", added.get("title"))));
    DcsEdit.setField(dcs, V, DATA_SET, "Зашифрован", channel(Map.of("title", encrypted.get("title"),
      "role", encrypted.get("role"))));
    assertThat(Files.readAllBytes(dcs)).isEqualTo(before);
  }

  @Test
  void вычисляемоеПолеПишетИЧитаетМногострочныеВыражениеИЗаголовок() throws Exception {
    Path dcs = copy(FILES);
    DcsRead.addCalculatedField(dcs, V, "Многострочное", TEXT, OTHER);
    Map<String, Object> added = calculated(dcs, "Многострочное");
    assertThat(added).containsEntry("value", TEXT).containsEntry("title", OTHER);
    assertVerbatimCrlf(dcs, TEXT);
    assertVerbatimCrlf(dcs, OTHER);

    DcsEdit.setCalculatedField(dcs, V, "Многострочное", Map.of("expression", OTHER, "title", TEXT));
    Map<String, Object> replaced = calculated(dcs, "Многострочное");
    assertThat(replaced).containsEntry("value", OTHER).containsEntry("title", TEXT);
    assertVerbatimCrlf(dcs, TEXT);

    byte[] before = Files.readAllBytes(dcs);
    DcsEdit.setCalculatedField(dcs, V, "Многострочное", channel(Map.of("expression", replaced.get("value"),
      "title", replaced.get("title"))));
    assertThat(Files.readAllBytes(dcs)).isEqualTo(before);
  }

  @Test
  void ресурсПишетИЧитаетМногострочноеВыражение() throws Exception {
    Path dcs = copy(FILES);
    DcsEdit.addTotalField(dcs, V, Map.of("dataPath", "Размер", "expression", TEXT));
    assertThat(rows(info(dcs), "totalFields").get(2)).containsEntry("value", TEXT);
    DcsEdit.setTotalField(dcs, V, 0, Map.of("expression", OTHER));
    Map<String, Object> first = rows(info(dcs), "totalFields").get(0);
    assertThat(first).containsEntry("value", OTHER);
    assertVerbatimCrlf(dcs, TEXT);
    assertVerbatimCrlf(dcs, OTHER);

    byte[] before = Files.readAllBytes(dcs);
    DcsEdit.setTotalField(dcs, V, 0, channel(Map.of("dataPath", first.get("name"), "expression", OTHER)));
    DcsEdit.setTotalField(dcs, V, 2, channel(Map.of("expression", TEXT)));
    assertThat(Files.readAllBytes(dcs)).isEqualTo(before);
  }

  @Test
  void связьНаборовПишетИЧитаетМногострочныеВыражения() throws Exception {
    Path dcs = copy(LINKS);
    Map<String, Object> first = rows(info(dcs), "dataSetLinks").get(0);
    DcsEdit.addLink(dcs, V, Map.of("sourceDataSet", first.get("source"), "destinationDataSet", first.get("destination"),
      "sourceExpression", TEXT, "destinationExpression", OTHER, "linkConditionExpression", TEXT,
      "startExpression", OTHER));
    List<Map<String, Object>> links = rows(info(dcs), "dataSetLinks");
    Map<String, Object> added = links.get(links.size() - 1);
    assertThat(added).containsEntry("sourceExpression", TEXT).containsEntry("destinationExpression", OTHER)
      .containsEntry("linkConditionExpression", TEXT).containsEntry("startExpression", OTHER);
    // Замена обязательного и необязательного элемента, вставка отсутствующего
    DcsEdit.setLink(dcs, V, 0, Map.of("sourceExpression", OTHER, "startExpression", TEXT,
      "linkConditionExpression", OTHER));
    Map<String, Object> changed = rows(info(dcs), "dataSetLinks").get(0);
    assertThat(changed).containsEntry("sourceExpression", OTHER).containsEntry("startExpression", TEXT)
      .containsEntry("linkConditionExpression", OTHER);
    assertVerbatimCrlf(dcs, TEXT);
    assertVerbatimCrlf(dcs, OTHER);

    byte[] before = Files.readAllBytes(dcs);
    List<Map<String, Object>> all = rows(info(dcs), "dataSetLinks");
    for (int i = 0; i < all.size(); i++) {
      DcsEdit.setLink(dcs, V, i, channel(linkEcho(all.get(i))));
    }
    assertThat(Files.readAllBytes(dcs)).isEqualTo(before);
  }

  @Test
  void переводыСтрокВходаПриводятсяКПереводуСтрокиФайла() throws Exception {
    // CRLF во входе CRLF-файла — CRLF в файле, чтение отдаёт LF, как у текста запроса
    Path crlf = copy(FILES);
    DcsEdit.addParameter(crlf, V, Map.of("name", "П", "title", TEXT.replace("\n", "\r\n"),
      "expression", TEXT.replace("\n", "\r")));
    assertThat(parameter(crlf, "П")).containsEntry("title", TEXT).containsEntry("expression", TEXT);
    assertVerbatimCrlf(crlf, TEXT);

    // LF-файл: любой перевод строки входа — LF, ни одного CR
    Path lf = copy(FILES);
    Files.writeString(lf, Files.readString(lf, StandardCharsets.UTF_8).replace("\r\n", "\n"), StandardCharsets.UTF_8);
    DcsEdit.addParameter(lf, V, Map.of("name", "П", "title", TEXT.replace("\n", "\r\n"), "expression", TEXT));
    DcsEdit.addTotalField(lf, V, Map.of("dataPath", "Размер", "expression", TEXT.replace("\n", "\r\n")));
    DcsSettingsEdit.addSettingsVariant(lf, V, "Многострочный", Map.of("presentation", TEXT.replace("\n", "\r\n")));
    String text = Files.readString(lf, StandardCharsets.UTF_8);
    assertThat(text).doesNotContain("\r").contains(DcsRead.escape(TEXT));
    assertThat(parameter(lf, "П")).containsEntry("title", TEXT).containsEntry("expression", TEXT);
    assertThat(rows(info(lf), "totalFields").get(2)).containsEntry("value", TEXT);
    assertThat(variant(lf, "Многострочный")).containsEntry("presentation", TEXT);
    assertThat(DcsXsdValidation.validate(lf).get("ok")).isEqualTo(true);
  }

  // --------------------------------------------------------------- варианты настроек

  @Test
  void представлениеВариантаПишетсяИЧитаетсяДословно() throws Exception {
    Path dcs = copy(FILES);
    DcsSettingsEdit.addSettingsVariant(dcs, V, "Многострочный", Map.of("presentation", TEXT));
    assertThat(variant(dcs, "Многострочный")).containsEntry("presentation", TEXT);
    // Замена записанного представления, снятие и вставка отсутствующего
    DcsSettingsEdit.setSettingsVariantProps(dcs, V, VARIANT, Map.of("presentation", OTHER));
    assertThat(variant(dcs, VARIANT)).containsEntry("presentation", OTHER);
    Map<String, Object> none = new HashMap<>();
    none.put("presentation", null);
    DcsSettingsEdit.setSettingsVariantProps(dcs, V, "Многострочный", none);
    DcsSettingsEdit.setSettingsVariantProps(dcs, V, "Многострочный", Map.of("presentation", TEXT));
    assertThat(variant(dcs, "Многострочный")).containsEntry("presentation", TEXT);
    assertVerbatimCrlf(dcs, TEXT);
    assertVerbatimCrlf(dcs, OTHER);

    byte[] before = Files.readAllBytes(dcs);
    DcsSettingsEdit.setSettingsVariantProps(dcs, V, VARIANT, channel(Map.of("presentation", OTHER)));
    DcsSettingsEdit.setSettingsVariantProps(dcs, V, "Многострочный", channel(Map.of("presentation", TEXT)));
    assertThat(Files.readAllBytes(dcs)).isEqualTo(before);
  }

  @Test
  void значенияПараметровНастроекПишутсяИЧитаютсяДословно() throws Exception {
    Path dcs = copy(FILES);
    // Новый параметр данных, затем замена его значения
    DcsSettingsEdit.setParameterValue(dcs, V, VARIANT, Map.of("section", "data", "parameter", "Строка",
      "value", Map.of("type", "xs:string", "text", TEXT)));
    assertThat(dataParameter(dcs, "Строка").get("value")).isEqualTo(List.of(Map.of("type", "xs:string", "text", TEXT)));
    DcsSettingsEdit.setParameterValue(dcs, V, VARIANT, Map.of("section", "data", "parameter", "Строка",
      "value", Map.of("type", "xs:string", "text", OTHER)));
    assertThat(dataParameter(dcs, "Строка").get("value"))
      .isEqualTo(List.of(Map.of("type", "xs:string", "text", OTHER)));
    // Скаляр без обёртки и список значений с представлением элемента
    DcsSettingsEdit.setParameterValue(dcs, V, VARIANT, Map.of("section", "data", "parameter", "Скаляр", "value", TEXT));
    assertThat(dataParameter(dcs, "Скаляр").get("value")).isEqualTo(List.of(Map.of("type", "xs:string", "text", TEXT)));
    DcsSettingsEdit.setParameterValue(dcs, V, VARIANT, Map.of("section", "data", "parameter", "Список",
      "value", Map.of("type", "v8:ValueListType", "list", Map.of("items", List.of(
        Map.of("value", Map.of("type", "xs:string", "text", TEXT), "presentation", OTHER))))));
    assertThat(dataParameter(dcs, "Список").get("value")).isEqualTo(List.of(Map.of("type", "v8:ValueListType",
      "list", Map.of("items", List.of(Map.of("value", Map.of("type", "xs:string", "text", TEXT),
        "presentation", OTHER))))));
    // Параметр вывода — строка языка конфигурации, новая секция
    DcsSettingsEdit.setParameterValue(dcs, V, VARIANT, Map.of("section", "output", "parameter", "Заголовок",
      "value", Map.of("type", "v8:LocalStringType", "decoded", TEXT)));
    assertThat(sectionItems(settings(dcs), "outputParameters").get(0).get("value"))
      .isEqualTo(List.of(Map.of("type", "v8:LocalStringType", "decoded", TEXT)));
    assertVerbatimCrlf(dcs, TEXT);
    assertVerbatimCrlf(dcs, OTHER);

    byte[] before = Files.readAllBytes(dcs);
    for (String name : List.of("Строка", "Скаляр", "Список")) {
      DcsSettingsEdit.setParameterValue(dcs, V, VARIANT, channel(Map.of("section", "data", "parameter", name,
        "value", ((List<?>) dataParameter(dcs, name).get("value")).get(0))));
    }
    DcsSettingsEdit.setParameterValue(dcs, V, VARIANT, channel(Map.of("section", "output", "parameter", "Заголовок",
      "value", Map.of("type", "v8:LocalStringType", "decoded", TEXT))));
    assertThat(Files.readAllBytes(dcs)).isEqualTo(before);
  }

  @Test
  void отборИВыбранныеПоляПишутМногострочныеЗначенияЗаголовкиИПредставления() throws Exception {
    Path dcs = copy(FILES);
    Map<String, Object> settings = settings(dcs);
    List<Map<String, Object>> filter = new ArrayList<>(sectionItems(settings, "filter"));
    filter.add(Map.of("kind", "comparison", "left", "Размер", "comparisonType", "Equal",
      "right", List.of(Map.of("type", "xs:string", "text", TEXT)), "presentation", OTHER,
      "userSettingPresentation", TEXT));
    DcsSettingsEdit.setFilter(dcs, V, VARIANT, Map.of("path", List.of(), "items", filter));
    List<Map<String, Object>> selection = new ArrayList<>(sectionItems(settings, "selection"));
    selection.add(Map.of("kind", "field", "field", "Размер", "title", TEXT));
    selection.add(Map.of("kind", "field", "field", "Количество", "title", OTHER, "titleKind", "string"));
    DcsSettingsEdit.setSelection(dcs, V, VARIANT, Map.of("path", List.of(), "items", selection));

    Map<String, Object> read = settings(dcs);
    List<Map<String, Object>> filterRead = sectionItems(read, "filter");
    Map<String, Object> comparison = filterRead.get(filterRead.size() - 1);
    assertThat(comparison.get("right")).isEqualTo(List.of(Map.of("type", "xs:string", "text", TEXT)));
    assertThat(comparison).containsEntry("presentation", OTHER).containsEntry("userSettingPresentation", TEXT);
    List<Map<String, Object>> selectionRead = sectionItems(read, "selection");
    assertThat(selectionRead.get(selectionRead.size() - 2)).containsEntry("title", TEXT)
      .containsEntry("titleKind", "lws");
    assertThat(selectionRead.get(selectionRead.size() - 1)).containsEntry("title", OTHER)
      .containsEntry("titleKind", "string");
    assertVerbatimCrlf(dcs, TEXT);
    assertVerbatimCrlf(dcs, OTHER);

    byte[] before = Files.readAllBytes(dcs);
    DcsSettingsEdit.setFilter(dcs, V, VARIANT, channel(Map.of("path", List.of(), "items", filterRead)));
    DcsSettingsEdit.setSelection(dcs, V, VARIANT, channel(Map.of("path", List.of(), "items", selectionRead)));
    assertThat(Files.readAllBytes(dcs)).isEqualTo(before);
  }

  @Test
  void условноеОформлениеПишетМногострочныеТекстИПредставление() throws Exception {
    Path dcs = copy(FILES);
    DcsSettingsEdit.addAppearanceItem(dcs, V, VARIANT, Map.of("path", List.of(), "item", Map.of(
      "fields", List.of(Map.of("field", "Размер")),
      "appearance", List.of(Map.of("parameter", "Текст", "value", List.of(Map.of("type", "v8:LocalStringType",
        "decoded", TEXT)))),
      "presentation", OTHER)));
    List<Map<String, Object>> items = sectionItems(settings(dcs), "conditionalAppearance");
    int index = items.size() - 1;
    Map<String, Object> added = items.get(index);
    assertThat(added).containsEntry("presentation", OTHER);
    assertThat(added.get("appearance")).isEqualTo(List.of(Map.of("parameter", "Текст",
      "value", List.of(Map.of("type", "v8:LocalStringType", "decoded", TEXT)))));
    assertVerbatimCrlf(dcs, TEXT);

    // Переписывание элемента целиком: новые тексты, отбор с многострочным правым значением
    Map<String, Object> changed = new LinkedHashMap<>(added);
    changed.put("presentation", TEXT);
    changed.put("appearance", List.of(Map.of("parameter", "Текст", "value", List.of(Map.of("type",
      "v8:LocalStringType", "decoded", OTHER)))));
    changed.put("filter", List.of(Map.of("kind", "comparison", "left", "Размер", "comparisonType", "Equal",
      "right", List.of(Map.of("type", "xs:string", "text", TEXT)))));
    DcsSettingsEdit.setAppearanceItem(dcs, V, VARIANT, Map.of("path", List.of(), "index", index, "item", changed));
    Map<String, Object> read = sectionItems(settings(dcs), "conditionalAppearance").get(index);
    assertThat(read).containsEntry("presentation", TEXT);
    assertThat(read.get("appearance")).isEqualTo(List.of(Map.of("parameter", "Текст",
      "value", List.of(Map.of("type", "v8:LocalStringType", "decoded", OTHER)))));
    assertThat(((Map<?, ?>) ((List<?>) read.get("filter")).get(0)).get("right"))
      .isEqualTo(List.of(Map.of("type", "xs:string", "text", TEXT)));
    assertVerbatimCrlf(dcs, TEXT);
    assertVerbatimCrlf(dcs, OTHER);

    byte[] before = Files.readAllBytes(dcs);
    DcsSettingsEdit.setAppearanceItem(dcs, V, VARIANT,
      channel(Map.of("path", List.of(), "index", index, "item", read)));
    assertThat(Files.readAllBytes(dcs)).isEqualTo(before);
  }

  @Test
  void схемаПроектаEdtПишетМногострочныйТекстТакЖе() throws Exception {
    Path source = Path.of(System.getProperty("fixtures.ssl31edt.root"), "ssl31", "src",
      "Reports", "_ДемоФайлы", "Templates", "ОсновнаяСхемаКомпоновкиДанных", "Template.dcs");
    Path dcs = Files.createTempDirectory(tempDir, "edt").resolve("Template.dcs");
    Files.copy(source, dcs);
    String eol = XmlLines.newline(Files.readString(dcs, StandardCharsets.UTF_8));
    DcsEdit.addParameter(dcs, V, Map.of("name", "Многострочный", "title", TEXT,
      "value", Map.of("type", "xs:string", "text", TEXT)));
    DcsRead.addCalculatedField(dcs, V, "Многострочное", TEXT, null);
    DcsSettingsEdit.setParameterValue(dcs, V, VARIANT, Map.of("section", "output", "parameter", "Заголовок",
      "value", Map.of("type", "v8:LocalStringType", "decoded", TEXT)));
    assertThat(parameter(dcs, "Многострочный")).containsEntry("title", TEXT)
      .containsEntry("valueTyped", Map.of("type", "xs:string", "text", TEXT));
    assertThat(calculated(dcs, "Многострочное")).containsEntry("value", TEXT);
    assertThat(sectionItems(settings(dcs), "outputParameters").get(0).get("value"))
      .isEqualTo(List.of(Map.of("type", "v8:LocalStringType", "decoded", TEXT)));
    assertThat(Files.readString(dcs, StandardCharsets.UTF_8)).contains(DcsRead.escape(TEXT).replace("\n", eol));
    assertThat(DcsXsdValidation.validate(dcs).get("ok")).isEqualTo(true);
  }

  // --------------------------------------------------------------- эхо по корпусу

  @Test
  void эхоТекстовСхемИВариантовКорпусаНеМеняетНиБайта() throws Exception {
    int[] designer = echoCorpus(schemaFiles(Ssl31SubmodulePaths.projectRoot().resolve("src")), "Template.xml");
    int[] edt = echoCorpus(schemaFiles(Path.of(System.getProperty("fixtures.ssl31edt.root"))), "Template.dcs");
    // 1626 эхо в выгрузке и 1585 в EDT-паре; среди них 14 многострочных выражений вычисляемых полей и
    // ресурсов
    assertThat(List.of(designer[0], designer[1], edt[0], edt[1])).containsExactly(1626, 14, 1585, 14);
  }

  /**
   * Эхо текстов каждой схемы через JSON канала: заголовки, выражения и строки роли полей наборов
   * верхнего уровня, вычисляемые поля, ресурсы, связи, параметры (заголовок и выражение; значение
   * проверяет {@code DcsParameterValueTest}) и представления вариантов.
   *
   * @return {отправлено эхом, из них многострочных текстов}
   */
  private int[] echoCorpus(List<Path> schemas, String fileName) throws Exception {
    int echoed = 0;
    int multiline = 0;
    int index = 0;
    for (Path schema : schemas) {
      Path copy = Files.createTempDirectory(tempDir, "echo" + index++).resolve(fileName);
      Files.copy(schema, copy);
      String before = Files.readString(copy, StandardCharsets.UTF_8);
      Map<String, Object> info = info(copy);
      for (Map<String, Object> dataSet : rows(info, "dataSets")) {
        for (Map<String, Object> field : listOf(dataSet.get("fields"))) {
          if (!"field".equals(field.get("kind"))) {
            continue;
          }
          Map<String, Object> props = new LinkedHashMap<>();
          props.put("newDataPath", field.get("dataPath"));
          props.put("field", field.get("field"));
          for (String key : List.of("title", "role")) {
            if (field.get(key) != null) {
              props.put(key, field.get(key));
            }
          }
          DcsEdit.setField(copy, V, (String) dataSet.get("name"), (String) field.get("dataPath"), channel(props));
          assertUnchanged(copy, before, schema + ": поле " + field.get("dataPath"));
          echoed++;
        }
      }
      for (Map<String, Object> calculated : rows(info, "calculatedFields")) {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("newDataPath", calculated.get("name"));
        props.put("expression", calculated.get("value"));
        if (calculated.get("title") != null) {
          props.put("title", calculated.get("title"));
        }
        DcsEdit.setCalculatedField(copy, V, (String) calculated.get("name"), channel(props));
        assertUnchanged(copy, before, schema + ": вычисляемое поле " + calculated.get("name"));
        echoed++;
        multiline += String.valueOf(calculated.get("value")).contains("\n") ? 1 : 0;
      }
      List<Map<String, Object>> totals = rows(info, "totalFields");
      for (int i = 0; i < totals.size(); i++) {
        DcsEdit.setTotalField(copy, V, i, channel(Map.of("dataPath", totals.get(i).get("name"),
          "expression", totals.get(i).get("value"))));
        assertUnchanged(copy, before, schema + ": ресурс " + i);
        echoed++;
        multiline += String.valueOf(totals.get(i).get("value")).contains("\n") ? 1 : 0;
      }
      List<Map<String, Object>> links = rows(info, "dataSetLinks");
      for (int i = 0; i < links.size(); i++) {
        DcsEdit.setLink(copy, V, i, channel(linkEcho(links.get(i))));
        assertUnchanged(copy, before, schema + ": связь " + i);
        echoed++;
      }
      for (Map<String, Object> parameter : rows(info, "parameters")) {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("newName", parameter.get("name"));
        if (parameter.get("title") != null) {
          props.put("title", parameter.get("title"));
        }
        if (parameter.get("expression") != null) {
          props.put("expression", parameter.get("expression"));
        }
        DcsEdit.setParameter(copy, V, (String) parameter.get("name"), channel(props));
        assertUnchanged(copy, before, schema + ": параметр " + parameter.get("name"));
        echoed++;
      }
      for (Map<String, Object> variant : listOf(DcsSettingsRead.info(copy, V, null).get("variants"))) {
        if (variant.get("presentation") != null) {
          DcsSettingsEdit.setSettingsVariantProps(copy, V, (String) variant.get("name"),
            channel(Map.of("newName", variant.get("name"), "presentation", variant.get("presentation"))));
          assertUnchanged(copy, before, schema + ": вариант " + variant.get("name"));
          echoed++;
        }
      }
      assertThat(Files.readAllBytes(copy)).as(schema.toString()).isEqualTo(before.getBytes(StandardCharsets.UTF_8));
    }
    return new int[] {echoed, multiline};
  }

  /** Файл не изменился; иначе сообщение показывает первое расхождение, а не массивы байтов. */
  private static void assertUnchanged(Path copy, String before, String what) throws IOException {
    String after = Files.readString(copy, StandardCharsets.UTF_8);
    if (after.equals(before)) {
      return;
    }
    int at = 0;
    while (at < Math.min(before.length(), after.length()) && before.charAt(at) == after.charAt(at)) {
      at++;
    }
    int from = Math.max(0, at - 120);
    assertThat(after.substring(from, Math.min(after.length(), at + 200))).as(what)
      .isEqualTo(before.substring(from, Math.min(before.length(), at + 200)));
  }

  /** Свойства связи из {@code cf-dcs-info} ключами {@code cf-dcs-set-link}. */
  private static Map<String, Object> linkEcho(Map<String, Object> link) {
    Map<String, Object> props = new LinkedHashMap<>();
    props.put("sourceDataSet", link.get("source"));
    props.put("destinationDataSet", link.get("destination"));
    props.put("sourceExpression", link.get("sourceExpression"));
    props.put("destinationExpression", link.get("destinationExpression"));
    for (String key : List.of("parameter", "parameterListAllowed", "linkConditionExpression", "startExpression",
      "required")) {
      if (link.containsKey(key)) {
        props.put(key, link.get(key));
      }
    }
    return props;
  }

  // --------------------------------------------------------------- помощники

  /**
   * Текст записан в CRLF-файл дословно, с CRLF вместо переводов строк; одиночных LF и CR в файле
   * нет, строгая проверка проходит.
   */
  private static void assertVerbatimCrlf(Path dcs, String text) throws IOException {
    String file = Files.readString(dcs, StandardCharsets.UTF_8);
    assertThat(file).contains(DcsRead.escape(text).replace("\n", "\r\n"));
    assertThat(file.replace("\r\n", "")).doesNotContain("\n").doesNotContain("\r");
    assertThat(DcsXsdValidation.validate(dcs).get("ok")).isEqualTo(true);
  }

  private Path copy(String relativePath) throws IOException {
    Path copy = Files.createTempDirectory(tempDir, "dcs").resolve("Template.xml");
    Files.copy(Ssl31SubmodulePaths.projectRoot().resolve(relativePath), copy);
    return copy;
  }

  /** Payload так, как его доставляет канал: через JSON. */
  @SuppressWarnings("unchecked")
  private static Map<String, Object> channel(Map<String, ?> payload) {
    return GSON.fromJson(GSON.toJson(payload), Map.class);
  }

  private static Map<String, Object> info(Path dcs) throws Exception {
    return DcsRead.info(dcs, V);
  }

  private static Map<String, Object> parameter(Path dcs, String name) throws Exception {
    return byKey(rows(info(dcs), "parameters"), "name", name);
  }

  private static Map<String, Object> calculated(Path dcs, String name) throws Exception {
    return byKey(rows(info(dcs), "calculatedFields"), "name", name);
  }

  private static Map<String, Object> field(Path dcs, String dataPath) throws Exception {
    return byKey(listOf(byKey(rows(info(dcs), "dataSets"), "name", DATA_SET).get("fields")), "dataPath", dataPath);
  }

  private static Map<String, Object> variant(Path dcs, String name) throws Exception {
    return byKey(listOf(DcsSettingsRead.info(dcs, V, null).get("variants")), "name", name);
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> settings(Path dcs) throws Exception {
    return (Map<String, Object>) listOf(DcsSettingsRead.info(dcs, V, VARIANT).get("variants")).get(0).get("settings");
  }

  @SuppressWarnings("unchecked")
  private static List<Map<String, Object>> sectionItems(Map<String, Object> settings, String section) {
    return listOf(((Map<String, Object>) settings.get(section)).get("items"));
  }

  private static Map<String, Object> dataParameter(Path dcs, String name) throws Exception {
    return byKey(sectionItems(settings(dcs), "dataParameters"), "parameter", name);
  }

  @SuppressWarnings("unchecked")
  private static List<Map<String, Object>> rows(Map<String, Object> info, String key) {
    return (List<Map<String, Object>>) info.get(key);
  }

  @SuppressWarnings("unchecked")
  private static List<Map<String, Object>> listOf(Object value) {
    return value == null ? List.of() : (List<Map<String, Object>>) value;
  }

  private static Map<String, Object> byKey(List<Map<String, Object>> rows, String key, String value) {
    return rows.stream()
      .filter(row -> value.equals(row.get(key)))
      .findFirst()
      .orElseThrow(() -> new AssertionError(key + "=" + value + " не найден: " + rows));
  }

  /** Схемы компоновки под каталогом: макеты выгрузки и проекта EDT. */
  private static List<Path> schemaFiles(Path root) throws IOException {
    try (Stream<Path> files = Files.walk(root)) {
      return files
        .filter(file -> {
          String name = file.getFileName().toString();
          return name.equals("Template.xml") || name.equals("Template.dcs");
        })
        .filter(file -> {
          try {
            return Files.readString(file, StandardCharsets.UTF_8).contains("<DataCompositionSchema");
          } catch (IOException e) {
            throw new IllegalStateException(e);
          }
        })
        .sorted()
        .toList();
    }
  }
}
