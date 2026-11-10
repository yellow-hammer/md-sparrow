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

import com.google.gson.Gson;
import io.github.yellowhammer.designerxml.SchemaVersion;
import io.github.yellowhammer.designerxml.Ssl31SubmodulePaths;
import io.github.yellowhammer.designerxml.cli.DesignerXmlCli;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Заполнение полей набора-запроса по тексту его запроса: разбор списка выборки
 * ({@link DcsAutofill}) и мутация cf-dcs-autofill-fields ({@link DcsEdit#autofillFields}).
 * Парсер проверяется юнитами на все случаи контракта (алиасы, пути, префиксы, строки,
 * комментарии, скобки, последний запрос выборки пакета и первый запрос объединения, звёздочка), мутация - на копиях
 * реальной схемы ssl31: добавляются только недостающие поля в позицию field* формата, повтор
 * операции байтово пуст, отказы не меняют файл. Канал apply-mutation возвращает added/skipped
 * одним JSON в stdout и правит схему проекта 1С:EDT ({@code Template.dcs}) теми же операциями.
 */
class DcsAutofillTest {

  /** Схема с набором-запросом (все поля выборки уже заполнены платформой) и набором-объектом. */
  private static final String LINKED_SCHEMA =
    "src/cf/Reports/РезультатыПроверкиУчета/Templates/ОсновнаяСхемаКомпоновкиДанных/Ext/Template.xml";

  /** Схема с набором-объединением и вложенными в него наборами-объектами. */
  private static final String UNION_SCHEMA =
    "src/cf/Reports/ОбъемНенужныхФайлов/Templates/ОсновнаяСхемаКомпоновкиДанных/Ext/Template.xml";

  /** Пакет: первый запрос помещает во временную таблицу, поля набора даёт последний. */
  private static final String HISTORY_SCHEMA =
    "src/cf/Reports/ИсторияРазмераПриложения/Templates/ОсновнаяСхемаКомпоновкиДанных/Ext/Template.xml";

  /** Два объединения с одноимёнными вложенными наборами-запросами. */
  private static final String ENTERPRISE_DATA_SCHEMA =
    "src/cf/DataProcessors/ВыгрузкаЗагрузкаEnterpriseData/Templates/СхемаКомпоновкиДанных/Ext/Template.xml";

  /** Поля с путём к данным, отличным от колонки: {@code Заведующий} — колонка {@code Пользователь}. */
  private static final String STORAGE_PLACES_SCHEMA =
    "src/cf/Catalogs/_ДемоМестаХранения/Templates/ДополнительныеДанныеПечати/Ext/Template.xml";

  /** Поле в папке: путь {@code Итоги.СуммаОплаты}, колонка {@code СуммаОплаты}. */
  private static final String INVOICE_SCHEMA =
    "src/cf/Documents/_ДемоСчетНаОплатуПокупателю/Templates/ДанныеПечати/Ext/Template.xml";

  @TempDir Path tempDir;

  // --- юниты парсера -------------------------------------------------------------------------

  @Test
  void parserResolvesAliasesPathsAndPrefixes() {
    DcsAutofill.Selection selection = DcsAutofill.parseSelection("ВЫБРАТЬ РАЗРЕШЕННЫЕ РАЗЛИЧНЫЕ ПЕРВЫЕ 10\n"
      + "\tТаблица.Поле КАК Псевдоним,\n"
      + "\tТаблица.Вложено.Реквизит,\n"
      + "\tСсылка,\n"
      + "\tЕСТЬNULL(Таблица.Цена, 0) как Цена\n"
      + "ИЗ\n\tСправочник.Номенклатура КАК Таблица");

    assertThat(selection.fields()).extracting(DcsAutofill.Field::name)
      .containsExactly("Псевдоним", "ВложеноРеквизит", "Ссылка", "Цена");
    // у поля без псевдонима имя - склейка сегментов после алиаса, выражение сохраняется целиком
    assertThat(selection.fields().get(1).expression()).isEqualTo("Таблица.Вложено.Реквизит");
    assertThat(selection.fields().get(0).expression()).isEqualTo("Таблица.Поле");
    assertThat(selection.skipped()).isEmpty();
  }

  @Test
  void parserNamesDereferenceColumnByGluedSegmentsAfterAlias() {
    // Платформа называет колонку разыменования без псевдонима склейкой сегментов после алиаса
    // таблицы и под этим именем пишет поле набора
    DcsAutofill.Selection selection = DcsAutofill.parseSelection("ВЫБРАТЬ\n"
      + "\tФайлы.Ссылка,\n"
      + "\tФайлы.Автор.Наименование,\n"
      + "\tФайлы . Автор . Владелец . Код,\n"
      + "\tФайлы.Автор.Наименование КАК Автор\n"
      + "ИЗ\n\tСправочник.Файлы КАК Файлы");

    assertThat(selection.fields()).extracting(DcsAutofill.Field::name)
      .containsExactly("Ссылка", "АвторНаименование", "АвторВладелецКод", "Автор");
    assertThat(selection.fields().get(2).expression()).isEqualTo("Файлы . Автор . Владелец . Код");
    // путь из одного сегмента - сам сегмент; английский текст - по тем же правилам
    assertThat(DcsAutofill.parseSelection("SELECT Code, T.Owner.Description FROM Catalog.Items AS T").fields())
      .extracting(DcsAutofill.Field::name).containsExactly("Code", "OwnerDescription");
  }

  @Test
  void parserAcceptsEnglishKeywords() {
    DcsAutofill.Selection selection = DcsAutofill.parseSelection(
      "SELECT ALLOWED DISTINCT TOP 5\n\tT.Item AS Item,\n\tT.Code\nFROM Catalog.Items AS T");

    assertThat(selection.fields()).extracting(DcsAutofill.Field::name).containsExactly("Item", "Code");
    assertThat(selection.skipped()).isEmpty();
  }

  @Test
  void parserKeepsStringsCommentsAndBracketsWhole() {
    DcsAutofill.Selection selection = DcsAutofill.parseSelection("ВЫБРАТЬ // комментарий: запятая, ИЗ - не граница\n"
      + "\tВЫБОР КОГДА Т.Вид = \"Товар, услуга\" ТОГДА 1 ИНАЧЕ 0 КОНЕЦ КАК Признак,\n"
      + "\t\"Строка \"\"с кавычками\"\", и запятой\" КАК Константа,\n"
      + "\tСУММА(ВЫБОР КОГДА Т.Сумма > 0 ТОГДА Т.Сумма ИНАЧЕ 0 КОНЕЦ) КАК Сумма, // хвост строки\n"
      + "\t(ВЫБРАТЬ КОЛИЧЕСТВО(*) ИЗ Справочник.Валюты) КАК ЧислоВалют\n"
      + "ИЗ Документ.Счет КАК Т");

    // строки, комментарии и вложенные скобки не рвут записи; КАК и ИЗ внутри них - просто текст
    assertThat(selection.fields()).extracting(DcsAutofill.Field::name)
      .containsExactly("Признак", "Константа", "Сумма", "ЧислоВалют");
    assertThat(selection.skipped()).isEmpty();
  }

  @Test
  void parserTakesFirstQueryOfUnion() {
    // объединение: список выборки берётся у первого запроса
    assertThat(DcsAutofill.parseSelection(
      "ВЫБРАТЬ Т.А КАК Первый ИЗ Х КАК Т ОБЪЕДИНИТЬ ВСЕ ВЫБРАТЬ Б ИЗ У").fields())
      .extracting(DcsAutofill.Field::name).containsExactly("Первый");
    // объединение без ИЗ в первом запросе
    assertThat(DcsAutofill.parseSelection("ВЫБРАТЬ 1 КАК Один ОБЪЕДИНИТЬ ВЫБРАТЬ 2").fields())
      .extracting(DcsAutofill.Field::name).containsExactly("Один");
  }

  @Test
  void parserTakesLastSelectionQueryOfBatch() {
    // пакет запросов: поля набора даёт последний запрос выборки, а не первый
    assertThat(DcsAutofill.parseSelection("ВЫБРАТЬ 1 КАК Один;\nВЫБРАТЬ 2 КАК Два").fields())
      .extracting(DcsAutofill.Field::name).containsExactly("Два");
    // первые запросы пакета готовят временные таблицы: их выборка в набор не попадает
    assertThat(DcsAutofill.parseSelection(
      "ВЫБРАТЬ Т.А КАК Первый ПОМЕСТИТЬ ВТ ИЗ Х КАК Т;\nВЫБРАТЬ В.Б КАК Второй ИЗ ВТ КАК В").fields())
      .extracting(DcsAutofill.Field::name).containsExactly("Второй");
    // УНИЧТОЖИТЬ в конце пакета выборкой не является; ДОБАВИТЬ пишет во временную таблицу
    assertThat(DcsAutofill.parseSelection("ВЫБРАТЬ 1 КАК А ПОМЕСТИТЬ ВТ;\n"
      + "ВЫБРАТЬ В.А КАК Итог ИЗ ВТ КАК В;\n"
      + "ВЫБРАТЬ 2 КАК А ДОБАВИТЬ ВТ;\n"
      + "УНИЧТОЖИТЬ ВТ;").fields())
      .extracting(DcsAutofill.Field::name).containsExactly("Итог");
    // английские ключевые слова равноправны
    assertThat(DcsAutofill.parseSelection("SELECT 1 AS One INTO TT;\nSELECT TT.One AS Last FROM TT AS TT;\nDROP TT")
      .fields()).extracting(DcsAutofill.Field::name).containsExactly("Last");
  }

  @Test
  void parserDoesNotSplitBatchInsideStringsCommentsAndBraces() {
    // точка с запятой в строке, комментарии и блоке {…} запросы пакета не делит; ПОМЕСТИТЬ в строке —
    // текст, а поле с именем Добавить после точки — не ключевое слово
    DcsAutofill.Selection selection = DcsAutofill.parseSelection("ВЫБРАТЬ \"а;б ПОМЕСТИТЬ\" КАК Строка, "
      + "Т.Добавить КАК Флаг // ; ПОМЕСТИТЬ\nИЗ Х КАК Т {ГДЕ Т.А = \";\"}");

    assertThat(selection.fields()).extracting(DcsAutofill.Field::name).containsExactly("Строка", "Флаг");
  }

  @Test
  void parserEndsSelectionAtNextClauseOfQueryWithoutSource() {
    // запрос без ИЗ: за списком сразу ГДЕ (поля набора-заглушки в БСП) — последняя запись не поглощает условие
    DcsAutofill.Selection selection = DcsAutofill.parseSelection(
      "ВЫБРАТЬ\n\tNULL КАК ОбъектСсылка,\n\t0    КАК ОбщееКоличество\nГДЕ\n\tЛОЖЬ");
    assertThat(selection.fields()).extracting(DcsAutofill.Field::name).containsExactly("ОбъектСсылка", "ОбщееКоличество");
    assertThat(selection.skipped()).isEmpty();
    // блок {ГДЕ …} перед ГДЕ пропускается, граница — само ГДЕ
    assertThat(DcsAutofill.parseSelection(
      "ВЫБРАТЬ\n\t&Причина КАК Причина,\n\t&Е5 КАК Колонка\n{ГДЕ\n\t(&Причина) КАК Причина}\nГДЕ Ложь").fields())
      .extracting(DcsAutofill.Field::name).containsExactly("Причина", "Колонка");
    // прочие предложения и английские ключевые слова
    assertThat(DcsAutofill.parseSelection("SELECT 1 AS One WHERE FALSE").fields())
      .extracting(DcsAutofill.Field::name).containsExactly("One");
    assertThat(DcsAutofill.parseSelection("ВЫБРАТЬ 1 КАК Один УПОРЯДОЧИТЬ ПО Один").fields())
      .extracting(DcsAutofill.Field::name).containsExactly("Один");
    assertThat(DcsAutofill.parseSelection("ВЫБРАТЬ 1 КАК Один\nДЛЯ ИЗМЕНЕНИЯ").fields())
      .extracting(DcsAutofill.Field::name).containsExactly("Один");
  }

  @Test
  void parserKeepsFieldAndParameterNamedLikeClause() {
    // имя после точки или & — поле или параметр, а не начало предложения
    DcsAutofill.Selection selection = DcsAutofill.parseSelection(
      "ВЫБРАТЬ Т.Итоги КАК Сумма, Т.Для, &Где КАК Условие ИЗ Х КАК Т");
    assertThat(selection.fields()).extracting(DcsAutofill.Field::name).containsExactly("Сумма", "Для", "Условие");
    assertThat(selection.skipped()).isEmpty();
    assertThat(DcsAutofill.parseSelection("SELECT T.Index AS I, T . Order AS O, &Where AS W FROM X AS T").fields())
      .extracting(DcsAutofill.Field::name).containsExactly("I", "O", "W");
  }

  @Test
  void parserKeepsTableAndColumnAliasesNamedLikeClause() {
    // псевдоним таблицы перед точкой: список не обрывается на первом же поле
    DcsAutofill.Selection tableAlias = DcsAutofill.parseSelection(
      "ВЫБРАТЬ Итоги.Ссылка КАК Ссылка, Итоги . Код КАК Код ИЗ Справочник.Валюты КАК Итоги");
    assertThat(tableAlias.fields()).extracting(DcsAutofill.Field::name).containsExactly("Ссылка", "Код");
    assertThat(tableAlias.skipped()).isEmpty();
    // псевдоним колонки после КАК/AS
    DcsAutofill.Selection columnAlias = DcsAutofill.parseSelection(
      "ВЫБРАТЬ Т.Ссылка КАК Ссылка, Т.Код КАК Итоги, Т.Имя КАК Где2 ИЗ Справочник.Валюты КАК Т");
    assertThat(columnAlias.fields()).extracting(DcsAutofill.Field::name).containsExactly("Ссылка", "Итоги", "Где2");
    assertThat(columnAlias.skipped()).isEmpty();
    assertThat(DcsAutofill.parseSelection("SELECT T.Ref AS Ref, T.Code AS Order, T.Name AS Totals FROM X AS T").fields())
      .extracting(DcsAutofill.Field::name).containsExactly("Ref", "Order", "Totals");
    assertThat(DcsAutofill.parseSelection("SELECT Index.Ref AS Ref, Index.Code AS Index FROM X AS Index").fields())
      .extracting(DcsAutofill.Field::name).containsExactly("Ref", "Index");
    // первое слово предложения из двух слов без второго — имя, а не граница
    assertThat(DcsAutofill.parseSelection("ВЫБРАТЬ 1 КАК А, 2 КАК Для ГДЕ ЛОЖЬ").fields())
      .extracting(DcsAutofill.Field::name).containsExactly("А", "Для");
    // контроль: настоящие предложения заканчивают список
    assertThat(DcsAutofill.parseSelection("ВЫБРАТЬ NULL КАК А, 0 КАК Б ГДЕ ЛОЖЬ").fields())
      .extracting(DcsAutofill.Field::name).containsExactly("А", "Б");
    assertThat(DcsAutofill.parseSelection("SELECT 1 AS One ORDER BY One").fields())
      .extracting(DcsAutofill.Field::name).containsExactly("One");
    assertThat(DcsAutofill.parseSelection("ВЫБРАТЬ 1 КАК Один // примечание\nСГРУППИРОВАТЬ // ещё\n ПО Один").fields())
      .extracting(DcsAutofill.Field::name).containsExactly("Один");
  }

  @Test
  void parserRefusesBatchWithoutSelection() {
    assertThatThrownBy(() -> DcsAutofill.parseSelection("ВЫБРАТЬ 1 КАК А ПОМЕСТИТЬ ВТ;\nУНИЧТОЖИТЬ ВТ"))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("нет запроса выборки");
  }

  @Test
  void parserTreatsKeywordNamedParameterAsParameter() {
    // &Добавить и &Поместить — имена параметров, а не ключевые слова: единственный запрос остаётся выборкой
    assertThat(DcsAutofill.parseSelection("ВЫБРАТЬ Т.А КАК Поле ИЗ Х КАК Т ГДЕ Т.Б = &Добавить").fields())
      .extracting(DcsAutofill.Field::name).containsExactly("Поле");
    assertThat(DcsAutofill.parseSelection("SELECT T.A AS Field FROM X AS T WHERE T.B = &Into OR T.C = &Add")
      .fields()).extracting(DcsAutofill.Field::name).containsExactly("Field");
    // в пакете такой запрос не пропускается в пользу предыдущей выборки
    assertThat(DcsAutofill.parseSelection(
      "ВЫБРАТЬ 1 КАК Первый;\nВЫБРАТЬ Т.Б КАК Второй ИЗ Х КАК Т ГДЕ Т.В = &Поместить").fields())
      .extracting(DcsAutofill.Field::name).containsExactly("Второй");
  }

  @Test
  void parserSkipsDcsExtensionBraces() {
    // блок {ВЫБРАТЬ …} расширения языка запросов СКД - настройка автополей, а не выражение:
    // пропускается целиком, и звёздочка внутри него операции не мешает
    DcsAutofill.Selection selection = DcsAutofill.parseSelection(
      "ВЫБРАТЬ\n\tТ.А КАК А\n{ВЫБРАТЬ\n\tА.*}\nИЗ Х КАК Т");

    assertThat(selection.fields()).extracting(DcsAutofill.Field::name).containsExactly("А");
    assertThat(selection.skipped()).isEmpty();
  }

  @Test
  void parserSendsNamelessExpressionsToSkipped() {
    DcsAutofill.Selection selection = DcsAutofill.parseSelection("ВЫБРАТЬ\n"
      + "\tТ.Поле + 1,\n"
      + "\tКОЛИЧЕСТВО(Т.Поле),\n"
      + "\t&Параметр,\n"
      + "\t2,\n"
      + "\t\"строка\",\n"
      + "\tТ.Поле КАК 5,\n"
      + "\tТ.Годное КАК Годное,\n"
      + "ИЗ Х КАК Т");

    assertThat(selection.fields()).extracting(DcsAutofill.Field::name).containsExactly("Годное");
    assertThat(selection.skipped()).extracting(DcsAutofill.Skipped::expression).containsExactly(
      "Т.Поле + 1", "КОЛИЧЕСТВО(Т.Поле)", "&Параметр", "2", "\"строка\"", "Т.Поле КАК 5", "");
    // у каждой пропущенной записи своя причина
    assertThat(selection.skipped().get(0).reason()).contains("нет псевдонима");
    assertThat(selection.skipped().get(5).reason()).contains("псевдоним после КАК");
    assertThat(selection.skipped().get(6).reason()).contains("пустое выражение");
  }

  @Test
  void parserRefusesStarAndTextWithoutSelect() {
    assertThatThrownBy(() -> DcsAutofill.parseSelection("ВЫБРАТЬ *"))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("звёздочк");
    assertThatThrownBy(() -> DcsAutofill.parseSelection("ВЫБРАТЬ\n\tТ.* \nИЗ Х КАК Т"))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("звёздочк");
    assertThatThrownBy(() -> DcsAutofill.parseSelection("SELECT * FROM X"))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("звёздочк");
    assertThatThrownBy(() -> DcsAutofill.parseSelection("ПОМЕСТИТЬ ВТ"))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("не начинается с ВЫБРАТЬ");
    assertThatThrownBy(() -> DcsAutofill.parseSelection("  // только комментарий\n"))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("не начинается с ВЫБРАТЬ");
  }

  // --- мутация на копии фикстуры ssl31 -------------------------------------------------------

  @Test
  void autofillAddsOnlyMissingFieldsAfterExistingOnes() throws Exception {
    Path dcs = copySchema(LINKED_SCHEMA);
    DcsEdit.removeField(dcs, SchemaVersion.V2_20, "ОсновнойЗапрос", "ВажностьПроблемы");
    DcsEdit.removeField(dcs, SchemaVersion.V2_20, "ОсновнойЗапрос", "Ответственный");
    String before = Files.readString(dcs, StandardCharsets.UTF_8);

    Map<String, Object> answer = DcsEdit.autofillFields(dcs, SchemaVersion.V2_20, "ОсновнойЗапрос");

    // добавлены только недостающие поля, в порядке списка выборки запроса
    assertThat(answer.get("added")).isEqualTo(List.of("ВажностьПроблемы", "Ответственный"));
    assertThat((List<?>) answer.get("skipped")).isEmpty();
    String after = Files.readString(dcs, StandardCharsets.UTF_8);
    assertSingleEditZone(before, after);
    // новые поля написаны как у платформы (табы, CRLF), только dataPath и field: тип взять неоткуда
    assertThat(after).contains("\t\t<field xsi:type=\"DataSetFieldField\">\r\n"
      + "\t\t\t<dataPath>ВажностьПроблемы</dataPath>\r\n"
      + "\t\t\t<field>ВажностьПроблемы</field>\r\n"
      + "\t\t</field>\r\n"
      + "\t\t<field xsi:type=\"DataSetFieldField\">\r\n"
      + "\t\t\t<dataPath>Ответственный</dataPath>\r\n"
      + "\t\t\t<field>Ответственный</field>\r\n"
      + "\t\t</field>\r\n");
    // позиция field* последовательности набора: после полей идут только источник и запрос
    List<String> children = dataSetChildNames(after, "ОсновнойЗапрос");
    assertThat(children.subList(children.lastIndexOf("field") + 1, children.size()))
      .containsExactly("dataSource", "query");
    Map<String, Object> restored = byKey(
      fields(byKey(rows(DcsRead.info(dcs, SchemaVersion.V2_20), "dataSets"), "name", "ОсновнойЗапрос")),
      "dataPath", "Ответственный");
    assertThat(restored.get("kind")).isEqualTo("field");
    assertThat(restored.get("field")).isEqualTo("Ответственный");
  }

  @Test
  void autofillRepeatIsByteNoOp() throws Exception {
    Path dcs = copySchema(LINKED_SCHEMA);
    String fresh = Files.readString(dcs, StandardCharsets.UTF_8);

    // все поля выборки уже на месте: файл не пишется вовсе
    Map<String, Object> answer = DcsEdit.autofillFields(dcs, SchemaVersion.V2_20, "ОсновнойЗапрос");
    assertThat(answer.get("added")).isEqualTo(List.of());
    assertThat(Files.readString(dcs, StandardCharsets.UTF_8)).isEqualTo(fresh);

    // эхо-повтор после настоящего добавления тоже байтово пуст
    DcsEdit.removeField(dcs, SchemaVersion.V2_20, "ОсновнойЗапрос", "Ответственный");
    assertThat(DcsEdit.autofillFields(dcs, SchemaVersion.V2_20, "ОсновнойЗапрос").get("added"))
      .isEqualTo(List.of("Ответственный"));
    String filled = Files.readString(dcs, StandardCharsets.UTF_8);
    assertThat(DcsEdit.autofillFields(dcs, SchemaVersion.V2_20, "ОсновнойЗапрос").get("added"))
      .isEqualTo(List.of());
    assertThat(Files.readString(dcs, StandardCharsets.UTF_8)).isEqualTo(filled);
  }

  @Test
  void autofillComparesDataPathsCaseInsensitively() throws Exception {
    Path dcs = copySchema(LINKED_SCHEMA);
    // псевдоним отличается от существующего поля Выявлено только регистром
    DcsRead.setQuery(dcs, SchemaVersion.V2_20, "ОсновнойЗапрос", "ВЫБРАТЬ\n"
      + "\tРезультатыПроверкиУчета.Выявлено КАК ВЫЯВЛЕНО\n"
      + "ИЗ\n\tРегистрСведений.РезультатыПроверкиУчета КАК РезультатыПроверкиУчета");
    String before = Files.readString(dcs, StandardCharsets.UTF_8);

    Map<String, Object> answer = DcsEdit.autofillFields(dcs, SchemaVersion.V2_20, "ОсновнойЗапрос");

    assertThat(answer.get("added")).isEqualTo(List.of());
    assertThat(Files.readString(dcs, StandardCharsets.UTF_8)).isEqualTo(before);
  }

  @Test
  void autofillCountsColumnPresentByFieldAsWellAsDataPath() throws Exception {
    // Поле привязано к колонке через field, а путь у него другой: колонка уже есть, дублей
    // Пользователь, МестоХранения и СуммаОплаты не появляется
    for (String schema : List.of(STORAGE_PLACES_SCHEMA, INVOICE_SCHEMA)) {
      Path dcs = copySchema(schema);
      byte[] before = Files.readAllBytes(dcs);
      Map<String, Object> answer = DcsEdit.autofillFields(dcs, SchemaVersion.V2_20, "НаборДанных1");
      assertThat(answer.get("added")).as(schema).isEqualTo(List.of());
      assertThat(Files.readAllBytes(dcs)).as(schema).isEqualTo(before);
    }

    // Колонка совпадает с field другим регистром — тоже уже есть
    Path invoice = copySchema(INVOICE_SCHEMA);
    String query = rows(DcsRead.info(invoice, SchemaVersion.V2_20), "dataSets").get(0).get("query").toString();
    DcsRead.setQuery(invoice, SchemaVersion.V2_20, "НаборДанных1",
      query.replace("КАК СуммаОплаты,", "КАК СУММАОПЛАТЫ,"));
    String recased = Files.readString(invoice, StandardCharsets.UTF_8);
    assertThat(DcsEdit.autofillFields(invoice, SchemaVersion.V2_20, "НаборДанных1").get("added"))
      .isEqualTo(List.of());
    assertThat(Files.readString(invoice, StandardCharsets.UTF_8)).isEqualTo(recased);

    // Контроль: без поля с этой колонкой она снова дописывается — с путём, равным колонке
    DcsEdit.removeField(invoice, SchemaVersion.V2_20, "НаборДанных1", "Итоги.СуммаОплаты");
    assertThat(DcsEdit.autofillFields(invoice, SchemaVersion.V2_20, "НаборДанных1").get("added"))
      .isEqualTo(List.of("СУММАОПЛАТЫ"));
  }

  @Test
  void autofillKnowsDereferenceColumnUnderPlatformName() throws Exception {
    // Конструктор зовёт автозаполнение после каждого ОК: поле, которое платформа записала под
    // склеенным именем колонки разыменования, не должно получать двойника по последнему сегменту
    Path dcs = copySchema(LINKED_SCHEMA);
    DcsRead.setQuery(dcs, SchemaVersion.V2_20, "ОсновнойЗапрос", "ВЫБРАТЬ\r\n"
      + "\tРезультатыПроверкиУчета.Выявлено КАК Выявлено,\r\n"
      + "\tРезультатыПроверкиУчета.Ответственный.Наименование\r\n"
      + "ИЗ\r\n\tРегистрСведений.РезультатыПроверкиУчета КАК РезультатыПроверкиУчета");
    DcsEdit.addField(dcs, SchemaVersion.V2_20, "ОсновнойЗапрос",
      Map.of("dataPath", "ОтветственныйНаименование", "field", "ОтветственныйНаименование"));
    byte[] withPlatformField = Files.readAllBytes(dcs);

    Map<String, Object> answer = DcsEdit.autofillFields(dcs, SchemaVersion.V2_20, "ОсновнойЗапрос");

    assertThat(answer.get("added")).isEqualTo(List.of());
    assertThat(Files.readAllBytes(dcs)).isEqualTo(withPlatformField);

    // Контроль: без такого поля колонка дописывается под тем же именем, повтор - байтовый no-op
    DcsEdit.removeField(dcs, SchemaVersion.V2_20, "ОсновнойЗапрос", "ОтветственныйНаименование");
    assertThat(DcsEdit.autofillFields(dcs, SchemaVersion.V2_20, "ОсновнойЗапрос").get("added"))
      .isEqualTo(List.of("ОтветственныйНаименование"));
    byte[] filled = Files.readAllBytes(dcs);
    assertThat(DcsEdit.autofillFields(dcs, SchemaVersion.V2_20, "ОсновнойЗапрос").get("added"))
      .isEqualTo(List.of());
    assertThat(Files.readAllBytes(dcs)).isEqualTo(filled);
  }

  @Test
  void autofillAddsNothingOnEveryQueryDataSetOfSsl31() throws Exception {
    // Конструктор запроса зовёт автозаполнение после каждого ОК: на файлах платформы, где поля уже
    // заполнены конфигуратором, оно не должно дописать ни поля и ни байта
    Path root = Ssl31SubmodulePaths.projectRoot().resolve("src/cf");
    List<Path> schemas;
    try (Stream<Path> files = Files.walk(root)) {
      schemas = files.filter(file -> file.getFileName().toString().equals("Template.xml")).sorted().toList();
    }
    int querySets = 0;
    for (Path schema : schemas) {
      if (!Files.readString(schema, StandardCharsets.UTF_8).contains("<DataCompositionSchema")) {
        continue;
      }
      Path copy = Files.createTempDirectory(tempDir, "all").resolve("Template.xml");
      Files.copy(schema, copy);
      byte[] before = Files.readAllBytes(copy);
      for (String path : queryPaths(rows(DcsRead.info(copy, SchemaVersion.V2_20), "dataSets"), "")) {
        Map<String, Object> answer = DcsEdit.autofillFields(copy, SchemaVersion.V2_20, path);
        assertThat(answer.get("added")).as(root.relativize(schema) + " " + path).isEqualTo(List.of());
        querySets++;
      }
      assertThat(Files.readAllBytes(copy)).as(root.relativize(schema).toString()).isEqualTo(before);
    }
    assertThat(querySets).isEqualTo(38);
  }

  /** Пути наборов-запросов с непустым текстом, включая вложенные в объединения ({@code Объединение/Имя}). */
  private static List<String> queryPaths(List<Map<String, Object>> dataSets, String prefix) {
    List<String> out = new ArrayList<>();
    for (Map<String, Object> dataSet : dataSets == null ? List.<Map<String, Object>>of() : dataSets) {
      String path = prefix + dataSet.get("name");
      Object query = dataSet.get("query");
      if ("query".equals(dataSet.get("type")) && query != null && !query.toString().isBlank()) {
        out.add(path);
      }
      out.addAll(queryPaths(rows(dataSet, "items"), path + "/"));
    }
    return out;
  }

  @Test
  void autofillRefusals() throws Exception {
    Path dcs = copySchema(LINKED_SCHEMA);
    String before = Files.readString(dcs, StandardCharsets.UTF_8);

    assertThatThrownBy(() -> DcsEdit.autofillFields(dcs, SchemaVersion.V2_20, "НетНабора"))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("не найден");
    assertThatThrownBy(() -> DcsEdit.autofillFields(dcs, SchemaVersion.V2_20, " "))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("dataSet");
    // набор-объект запросом не является
    assertThatThrownBy(() -> DcsEdit.autofillFields(dcs, SchemaVersion.V2_20, "ВнешняяТаблица"))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("не запрос");
    assertThat(Files.readString(dcs, StandardCharsets.UTF_8)).isEqualTo(before);

    // набор-объединение тоже не запрос
    Path union = copySchema(UNION_SCHEMA);
    assertThatThrownBy(() -> DcsEdit.autofillFields(union, SchemaVersion.V2_20, "НаборДанных1"))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("не запрос");

    // новый набор-запрос создаётся с пустым <query/>: заполнять поля не из чего
    DcsEdit.addDataSet(dcs, SchemaVersion.V2_20, "НовыйЗапрос", "query", "ИсточникДанных1");
    assertThatThrownBy(() -> DcsEdit.autofillFields(dcs, SchemaVersion.V2_20, "НовыйЗапрос"))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("пуст");

    // звёздочку разворачивает платформа: отказ всей операции, файл не тронут
    DcsRead.setQuery(dcs, SchemaVersion.V2_20, "ОсновнойЗапрос", "ВЫБРАТЬ *");
    String starred = Files.readString(dcs, StandardCharsets.UTF_8);
    assertThatThrownBy(() -> DcsEdit.autofillFields(dcs, SchemaVersion.V2_20, "ОсновнойЗапрос"))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("звёздочк");
    assertThat(Files.readString(dcs, StandardCharsets.UTF_8)).isEqualTo(starred);
  }

  @Test
  void autofillTakesFieldsOfLastBatchQuery() throws Exception {
    // Первый запрос пакета помещает во временную таблицу, а поля Размер и
    // МаксимальныйПериодРасчетаОбъектаМетаданных есть только в последнем
    Path dcs = copySchema(HISTORY_SCHEMA);
    DcsEdit.removeField(dcs, SchemaVersion.V2_20, "РазмерПриложений", "Размер");
    DcsEdit.removeField(dcs, SchemaVersion.V2_20, "РазмерПриложений", "МаксимальныйПериодРасчетаОбъектаМетаданных");
    String before = Files.readString(dcs, StandardCharsets.UTF_8);

    Map<String, Object> answer = DcsEdit.autofillFields(dcs, SchemaVersion.V2_20, "РазмерПриложений");

    assertThat(answer.get("added")).isEqualTo(List.of("МаксимальныйПериодРасчетаОбъектаМетаданных", "Размер"));
    assertThat((List<?>) answer.get("skipped")).isEmpty();
    assertSingleEditZone(before, Files.readString(dcs, StandardCharsets.UTF_8));
    assertThat(DcsXsdValidation.validate(dcs).get("ok")).isEqualTo(true);
    // Повтор — байтовый no-op
    String filled = Files.readString(dcs, StandardCharsets.UTF_8);
    assertThat(DcsEdit.autofillFields(dcs, SchemaVersion.V2_20, "РазмерПриложений").get("added")).isEqualTo(List.of());
    assertThat(Files.readString(dcs, StandardCharsets.UTF_8)).isEqualTo(filled);
  }

  @Test
  void autofillAddressesNestedQueryByPath() throws Exception {
    // Вложенный набор объединения адресуется путём, как у cf-dcs-set-query
    Path dcs = copySchema(ENTERPRISE_DATA_SCHEMA);
    DcsRead.setQuery(dcs, SchemaVersion.V2_20, "РегистрацияИзменений/ОпределениеПолей",
      "ВЫБРАТЬ\n\tNULL КАК ОбъектСсылка,\n\tNULL КАК ОбъектТип,\n\tNULL КАК Узел");
    String before = Files.readString(dcs, StandardCharsets.UTF_8);

    Map<String, Object> answer = DcsEdit.autofillFields(dcs, SchemaVersion.V2_20,
      "РегистрацияИзменений/ОпределениеПолей");

    assertThat(answer.get("added")).isEqualTo(List.of("Узел"));
    // Колонка дописана и в поля объединения: его список полей — объединение полей вложенных наборов
    assertThat(answer.get("unions")).isEqualTo(
      List.of(Map.of("dataSet", "РегистрацияИзменений", "added", List.of("Узел"))));
    String after = Files.readString(dcs, StandardCharsets.UTF_8);
    // Поле встало во вложенный набор с его отступом, после его полей
    String nested = "\t\t\t<field xsi:type=\"DataSetFieldField\">\r\n"
      + "\t\t\t\t<dataPath>Узел</dataPath>\r\n"
      + "\t\t\t\t<field>Узел</field>\r\n"
      + "\t\t\t</field>\r\n"
      + "\t\t\t<dataSource>ОбщийИсточникДанных</dataSource>";
    // а у объединения — после его полей, перед вложенными наборами
    String union = "\t\t<field xsi:type=\"DataSetFieldField\">\r\n"
      + "\t\t\t<dataPath>Узел</dataPath>\r\n"
      + "\t\t\t<field>Узел</field>\r\n"
      + "\t\t</field>\r\n"
      + "\t\t<item xsi:type=\"DataSetQuery\">\r\n"
      + "\t\t\t<name>ОпределениеПолей</name>";
    assertThat(after).contains(nested).contains(union);
    // Кроме двух вставок файл не изменился
    assertThat(after.replace(nested, "\t\t\t<dataSource>ОбщийИсточникДанных</dataSource>")
      .replace(union, "\t\t<item xsi:type=\"DataSetQuery\">\r\n\t\t\t<name>ОпределениеПолей</name>"))
      .isEqualTo(before);
    assertThat(DcsXsdValidation.validate(dcs).get("ok")).isEqualTo(true);
    // Повтор — байтовый no-op: колонка уже есть и у набора, и у объединения
    assertThat(DcsEdit.autofillFields(dcs, SchemaVersion.V2_20, "РегистрацияИзменений/ОпределениеПолей")
      .get("added")).isEqualTo(List.of());
    assertThat(Files.readString(dcs, StandardCharsets.UTF_8)).isEqualTo(after);

    // Путь к объединению — отказ: его поля собираются из вложенных наборов
    assertThatThrownBy(() -> DcsEdit.autofillFields(dcs, SchemaVersion.V2_20, "РегистрацияИзменений"))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("объединение, а не запрос")
      .hasMessageContaining("РегистрацияИзменений/");
    // Имя вложенного набора без пути — не найден, с подсказкой пути
    assertThatThrownBy(() -> DcsEdit.autofillFields(dcs, SchemaVersion.V2_20, "ОпределениеПолей"))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("не найден")
      .hasMessageContaining("ОбщееКоличествоЭлементов/ОпределениеПолей");
    assertThat(Files.readString(dcs, StandardCharsets.UTF_8)).isEqualTo(after);
  }

  @Test
  void autofillReadsLastColumnOfQueryWithoutSource() throws Exception {
    // Вложенные наборы ВыгрузкаЗагрузкаEnterpriseData — запросы без ИЗ («… ГДЕ ЛОЖЬ»): новая последняя
    // колонка добавляется, а не уходит в пропущенные вместе с условием (так пишет её конструктор запроса)
    Path dcs = copySchema(ENTERPRISE_DATA_SCHEMA);
    DcsRead.setQuery(dcs, SchemaVersion.V2_20, "ОбщееКоличествоЭлементов/ОпределениеПолей",
      "ВЫБРАТЬ \n    NULL КАК Тип,\n    0    КАК ОбщееКоличество,\n\t&Узел КАК Узел\nГДЕ \n    ЛОЖЬ    ");

    Map<String, Object> answer = DcsEdit.autofillFields(dcs, SchemaVersion.V2_20,
      "ОбщееКоличествоЭлементов/ОпределениеПолей");

    assertThat(answer.get("added")).isEqualTo(List.of("Узел"));
    assertThat((List<?>) answer.get("skipped")).isEmpty();
    assertThat(DcsXsdValidation.validate(dcs).get("ok")).isEqualTo(true);
  }

  @Test
  void autofillDoesNotDuplicateColumnAlreadyInUnion() throws Exception {
    // Колонка, которая у объединения уже есть (по пути или колонке без учёта регистра), не дублируется
    Path dcs = copySchema(ENTERPRISE_DATA_SCHEMA);
    String item = "\t\t<item xsi:type=\"DataSetQuery\">\r\n\t\t\t<name>ОпределениеПолей</name>";
    String original = Files.readString(dcs, StandardCharsets.UTF_8);
    assertThat(original).contains(item);
    // у объединения поле есть (другим регистром), у вложенного набора — нет
    Files.writeString(dcs, original.replaceFirst(Pattern.quote(item), Matcher.quoteReplacement(
      "\t\t<field xsi:type=\"DataSetFieldField\">\r\n\t\t\t<dataPath>УЗЕЛ</dataPath>\r\n\t\t\t<field>УЗЕЛ</field>\r\n"
        + "\t\t</field>\r\n" + item)), StandardCharsets.UTF_8);
    DcsRead.setQuery(dcs, SchemaVersion.V2_20, "РегистрацияИзменений/ОпределениеПолей",
      "ВЫБРАТЬ\n\tNULL КАК ОбъектСсылка,\n\tNULL КАК ОбъектТип,\n\tNULL КАК Узел");
    String before = Files.readString(dcs, StandardCharsets.UTF_8);

    Map<String, Object> answer = DcsEdit.autofillFields(dcs, SchemaVersion.V2_20,
      "РегистрацияИзменений/ОпределениеПолей");

    assertThat(answer.get("added")).isEqualTo(List.of("Узел"));
    assertThat(answer).doesNotContainKey("unions");
    assertSingleEditZone(before, Files.readString(dcs, StandardCharsets.UTF_8));
    assertThat(DcsXsdValidation.validate(dcs).get("ok")).isEqualTo(true);
  }

  // --- канал apply-mutation ------------------------------------------------------------------

  @Test
  void applyMutationChannelReturnsAddedAndSkipped() throws Exception {
    Path dcs = copySchema(LINKED_SCHEMA);
    // в запросе одно недостающее поле и одно выражение без псевдонима
    DcsRead.setQuery(dcs, SchemaVersion.V2_20, "ОсновнойЗапрос", "ВЫБРАТЬ\n"
      + "\tРезультатыПроверкиУчета.Выявлено КАК НоваяКолонка,\n"
      + "\tРезультатыПроверкиУчета.Выявлено + 1\n"
      + "ИЗ\n\tРегистрСведений.РезультатыПроверкиУчета КАК РезультатыПроверкиУчета");

    ChannelResult result = applyMutation(Map.of(
      "op", "cf-dcs-autofill-fields", "objectXml", dcs.toString(), "schemaVersion", "V2_20",
      "dataSet", "ОсновнойЗапрос"));

    assertThat(result.exit()).isZero();
    Map<String, Object> answer = parseJson(result.out());
    assertThat(answer.get("added")).isEqualTo(List.of("НоваяКолонка"));
    assertThat(answer.get("skipped")).isEqualTo(List.of(Map.of(
      "expression", "РезультатыПроверкиУчета.Выявлено + 1",
      "reason", "у выражения нет псевдонима (КАК), а само оно не путь к полю")));
    Map<String, Object> added = byKey(
      fields(byKey(rows(DcsRead.info(dcs, SchemaVersion.V2_20), "dataSets"), "name", "ОсновнойЗапрос")),
      "dataPath", "НоваяКолонка");
    assertThat(added.get("kind")).isEqualTo("field");

    // отказ доходит из канала кодом 2 и не меняет файл
    String before = Files.readString(dcs, StandardCharsets.UTF_8);
    assertThat(applyMutation(Map.of(
      "op", "cf-dcs-autofill-fields", "objectXml", dcs.toString(), "schemaVersion", "V2_20",
      "dataSet", "НетНабора")).exit()).isEqualTo(2);
    assertThat(applyMutation(Map.of(
      "op", "cf-dcs-autofill-fields", "objectXml", dcs.toString(), "schemaVersion", "V2_20")).exit())
      .isEqualTo(2);
    assertThat(Files.readString(dcs, StandardCharsets.UTF_8)).isEqualTo(before);
  }

  @Test
  void applyMutationChannelFillsEdtTemplateDcs() throws Exception {
    // В проекте 1С:EDT схема компоновки - тот же формат файла с именем Template.dcs:
    // мутация идёт общим кодом правки схемы, EDT-гейт канала её не касается
    Path source = Path.of(System.getProperty("fixtures.ssl31edt.root"), "ssl31", "src",
      "Reports", "РезультатыПроверкиУчета", "Templates", "ОсновнаяСхемаКомпоновкиДанных", "Template.dcs");
    Path dcs = Files.createTempDirectory(tempDir, "dcs").resolve("Template.dcs");
    Files.copy(source, dcs, StandardCopyOption.REPLACE_EXISTING);
    DcsEdit.removeField(dcs, SchemaVersion.V2_20, "ОсновнойЗапрос", "Ответственный");
    String before = Files.readString(dcs, StandardCharsets.UTF_8);

    ChannelResult result = applyMutation(Map.of(
      "op", "cf-dcs-autofill-fields", "objectXml", dcs.toString(), "schemaVersion", "V2_20",
      "dataSet", "ОсновнойЗапрос"));

    assertThat(result.exit()).isZero();
    assertThat(parseJson(result.out()).get("added")).isEqualTo(List.of("Ответственный"));
    String after = Files.readString(dcs, StandardCharsets.UTF_8);
    assertSingleEditZone(before, after);
    Map<String, Object> restored = byKey(
      fields(byKey(rows(DcsRead.info(dcs, SchemaVersion.V2_20), "dataSets"), "name", "ОсновнойЗапрос")),
      "dataPath", "Ответственный");
    assertThat(restored.get("field")).isEqualTo("Ответственный");

    // эхо-повтор через канал: добавлять нечего, файл байтово прежний
    ChannelResult repeat = applyMutation(Map.of(
      "op", "cf-dcs-autofill-fields", "objectXml", dcs.toString(), "schemaVersion", "V2_20",
      "dataSet", "ОсновнойЗапрос"));
    assertThat(repeat.exit()).isZero();
    assertThat(parseJson(repeat.out()).get("added")).isEqualTo(List.of());
    assertThat(Files.readString(dcs, StandardCharsets.UTF_8)).isEqualTo(after);
  }

  // --- общее ---------------------------------------------------------------------------------

  /** Результат канала apply-mutation: код выхода и stdout в UTF-8. */
  private record ChannelResult(int exit, String out) {
  }

  /** Запуск канала apply-mutation с параметрами из UTF-8 JSON-файла и перехватом stdout. */
  private ChannelResult applyMutation(Map<String, ?> params) throws Exception {
    Path paramsFile = Files.createTempFile(tempDir, "params", ".json");
    Files.writeString(paramsFile, new Gson().toJson(params), StandardCharsets.UTF_8);
    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    PrintStream prev = System.out;
    int exit;
    try {
      System.setOut(new PrintStream(buffer, true, StandardCharsets.UTF_8));
      exit = new CommandLine(new DesignerXmlCli()).execute("apply-mutation", "--params", paramsFile.toString());
    } finally {
      System.setOut(prev);
    }
    return new ChannelResult(exit, buffer.toString(StandardCharsets.UTF_8));
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> parseJson(String json) {
    Map<String, Object> parsed = new Gson().fromJson(json, Map.class);
    assertThat(parsed).isNotNull();
    return parsed;
  }

  /** Копия схемы из submodule во временный каталог: индекс фикстур не трогается. */
  private Path copySchema(String relativePath) throws Exception {
    Path source = Ssl31SubmodulePaths.projectRoot().resolve(relativePath);
    Path copy = Files.createTempDirectory(tempDir, "dcs").resolve("Template.xml");
    Files.copy(source, copy, StandardCopyOption.REPLACE_EXISTING);
    return copy;
  }

  /**
   * Правка одной непрерывной зоной: вне неё файл совпадает байт в байт. Общий префикс и
   * суффикс двух текстов вместе покрывают весь короткий из них.
   */
  private static void assertSingleEditZone(String before, String after) {
    int shorter = Math.min(before.length(), after.length());
    int prefix = 0;
    while (prefix < shorter && before.charAt(prefix) == after.charAt(prefix)) {
      prefix++;
    }
    int suffix = 0;
    while (suffix < shorter - prefix
      && before.charAt(before.length() - 1 - suffix) == after.charAt(after.length() - 1 - suffix)) {
      suffix++;
    }
    assertThat(prefix + suffix).as("правка должна остаться одной зоной").isEqualTo(shorter);
  }

  /** Имена прямых подэлементов набора данных верхнего уровня в порядке файла. */
  private static List<String> dataSetChildNames(String xml, String setName) {
    for (XmlLines.Node node : XmlLines.children(xml, List.of("DataCompositionSchema"))) {
      if ("dataSet".equals(node.name()) && setName.equals(node.leaves().get("name"))) {
        return XmlLines.childrenAt(xml, node.start()).stream().map(XmlLines.Node::name).toList();
      }
    }
    throw new AssertionError("набор данных не найден: " + setName);
  }

  @SuppressWarnings("unchecked")
  private static List<Map<String, Object>> rows(Map<String, Object> info, String key) {
    return (List<Map<String, Object>>) info.get(key);
  }

  @SuppressWarnings("unchecked")
  private static List<Map<String, Object>> fields(Map<String, Object> dataSet) {
    return (List<Map<String, Object>>) dataSet.get("fields");
  }

  private static Map<String, Object> byKey(List<Map<String, Object>> rows, String key, String value) {
    return rows.stream()
      .filter(row -> value.equals(row.get(key)))
      .findFirst()
      .orElseThrow(() -> new AssertionError(key + "=" + value + " не найден: " + rows));
  }
}
