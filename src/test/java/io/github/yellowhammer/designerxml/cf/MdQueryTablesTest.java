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
import com.google.gson.GsonBuilder;
import io.github.yellowhammer.designerxml.SamplesSubmodulePaths;
import io.github.yellowhammer.designerxml.SchemaVersion;
import io.github.yellowhammer.designerxml.Ssl31SubmodulePaths;
import io.github.yellowhammer.designerxml.cli.DesignerXmlCli;
import io.github.yellowhammer.edt.EdtModel;
import io.github.yellowhammer.edt.EdtObjectProperties;
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
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Таблицы языка запросов объекта ({@link MdQueryTables}, {@code cf-md-query-tables}) на объектах ssl31:
 * состав стандартных реквизитов по свойствам объекта (а не по XML), их типы по правилам, табличные
 * части, стандартные табличные части, виртуальные таблицы регистров с параметрами по позициям и
 * полями с суффиксами, признак «балансовый» регистра бухгалтерии, критерий отбора (таблица с
 * параметром-значением) и последовательность (с границами), составы определяемых типов и
 * характеристик ({@code typeCompositions}), английские имена, проект EDT (тот же ответ, что у
 * выгрузки) и канал {@code read-json}.
 */
class MdQueryTablesTest {

  @TempDir Path tempDir;

  // --- справочники ---------------------------------------------------------------------------

  @Test
  void неиерархическийСправочникБезРодителяИПризнакаГруппы() throws Exception {
    Map<String, Object> answer = read("Catalogs/Валюты");

    assertThat(answer.get("object")).isEqualTo("Справочник.Валюты");
    assertThat(answer.get("kind")).isEqualTo("catalog");
    Map<String, Object> main = table(answer, "Справочник.Валюты");
    assertThat(main.get("kind")).isEqualTo("main");
    // В XML у Валют записаны Parent и IsFolder, но справочник неиерархический: в запросе их нет
    assertThat(names(main)).startsWith("Ссылка", "ВерсияДанных", "ПометкаУдаления", "Предопределенный",
      "ИмяПредопределенныхДанных", "Код", "Наименование")
      .doesNotContain("Родитель", "ЭтоГруппа", "Владелец", "Ref", "Parent")
      .contains("НаименованиеПолное", "ОсновнаяВалюта")
      .endsWith("Представление");
    Map<String, Object> code = field(main, "Код");
    assertThat(code.get("nameEn")).isEqualTo("Code");
    assertThat(code.get("kind")).isEqualTo("standard");
    assertThat(code.get("type")).isEqualTo(Map.of("types", List.of("xs:string"), "typeSets", List.of(),
      "stringQualifiers", Map.of("length", "3", "allowedLength", "VARIABLE")));
    // Переопределённый синоним стандартного реквизита берётся из файла
    assertThat(code.get("synonym")).isEqualTo("Цифровой код");
    assertThat(field(main, "Ссылка").get("type")).isEqualTo(ref("cfg:CatalogRef.Валюты"));
    assertThat(field(main, "ОсновнаяВалюта").get("kind")).isEqualTo("attribute");
    assertThat(field(main, "ОсновнаяВалюта").get("synonym")).isEqualTo("Основная валюта");

    Map<String, Object> section = table(answer, "Справочник.Валюты.Представления");
    assertThat(section.get("kind")).isEqualTo("tabularSection");
    assertThat(names(section)).containsExactly("Ссылка", "НомерСтроки", "КодЯзыка", "ПараметрыПрописи");
    assertThat(field(section, "Ссылка").get("type")).isEqualTo(ref("cfg:CatalogRef.Валюты"));
  }

  @Test
  void иерархическийСправочникСВладельцем() throws Exception {
    // Иерархия групп и элементов, числовой код, владелец — справочник
    Map<String, Object> questions = table(read("Catalogs/ВопросыШаблонаАнкеты"), "Справочник.ВопросыШаблонаАнкеты");
    assertThat(names(questions)).containsSubsequence("Код", "Наименование", "Родитель", "ЭтоГруппа", "Владелец");
    assertThat(field(questions, "Код").get("type")).isEqualTo(Map.of("types", List.of("xs:decimal"),
      "typeSets", List.of(),
      "numberQualifiers", Map.of("digits", "9", "fractionDigits", "0", "allowedSign", "NONNEGATIVE")));
    assertThat(field(questions, "Родитель").get("type")).isEqualTo(ref("cfg:CatalogRef.ВопросыШаблонаАнкеты"));
    assertThat(field(questions, "Владелец").get("type")).isEqualTo(ref("cfg:CatalogRef.ШаблоныАнкет"));

    // Иерархия элементов: родитель есть, а групп нет
    Map<String, Object> contracts = table(read("Catalogs/_ДемоДоговорыКонтрагентов"),
      "Справочник._ДемоДоговорыКонтрагентов");
    assertThat(names(contracts)).contains("Родитель", "Владелец").doesNotContain("ЭтоГруппа");
    assertThat(field(contracts, "Владелец").get("type")).isEqualTo(ref("cfg:CatalogRef._ДемоКонтрагенты"));

    // Владелец — план видов характеристик, длина кода 0: кода у справочника нет
    Map<String, Object> values = table(read("Catalogs/ЗначенияСвойствОбъектов"), "Справочник.ЗначенияСвойствОбъектов");
    assertThat(names(values)).doesNotContain("Код");
    assertThat(field(values, "Владелец").get("type"))
      .isEqualTo(ref("cfg:ChartOfCharacteristicTypesRef.ДополнительныеРеквизитыИСведения"));
  }

  // --- документ, перечисление, журнал --------------------------------------------------------

  @Test
  void документСТабличнойЧастью() throws Exception {
    Map<String, Object> answer = read("Documents/_ДемоСчетНаОплатуПокупателю");
    Map<String, Object> main = table(answer, "Документ._ДемоСчетНаОплатуПокупателю");
    assertThat(names(main)).startsWith("Ссылка", "ВерсияДанных", "ПометкаУдаления", "Номер", "Дата", "Проведен",
      "МоментВремени").endsWith("Представление");
    assertThat(field(main, "Номер").get("type")).isEqualTo(Map.of("types", List.of("xs:string"),
      "typeSets", List.of(), "stringQualifiers", Map.of("length", "11", "allowedLength", "VARIABLE")));
    assertThat(field(main, "Дата").get("type")).isEqualTo(Map.of("types", List.of("xs:dateTime"),
      "typeSets", List.of(), "dateQualifiers", Map.of("dateFractions", "DATE_TIME")));
    // Тип, зависящий от перечислений платформы, не выводится
    assertThat(field(main, "МоментВремени")).containsEntry("type", null);

    Map<String, Object> goods = table(answer, "Документ._ДемоСчетНаОплатуПокупателю.Товары");
    assertThat(names(goods)).startsWith("Ссылка", "НомерСтроки", "Номенклатура");
    assertThat(field(goods, "Ссылка").get("type")).isEqualTo(ref("cfg:DocumentRef._ДемоСчетНаОплатуПокупателю"));
    assertThat(field(goods, "НомерСтроки").get("synonym")).isEqualTo("N");
    assertThat(field(goods, "Сумма").get("type"))
      .isEqualTo(Map.of("types", List.of(), "typeSets", List.of("cfg:DefinedType.ДенежнаяСуммаНеотрицательная")));
  }

  @Test
  void перечислениеИЖурналДокументов() throws Exception {
    Map<String, Object> enumeration = table(read("Enums/СпособыУстановкиКурсаВалюты"),
      "Перечисление.СпособыУстановкиКурсаВалюты");
    assertThat(names(enumeration)).containsExactly("Ссылка", "Порядок", "Представление");
    assertThat(tables(read("Enums/СпособыУстановкиКурсаВалюты"))).hasSize(1);

    Map<String, Object> journal = table(read("DocumentJournals/_ДемоСкладскиеДокументы"),
      "ЖурналДокументов._ДемоСкладскиеДокументы");
    assertThat(names(journal)).startsWith("Ссылка", "Тип", "Дата", "Номер", "Проведен", "ПометкаУдаления",
      "МоментВремени").contains("Организация").endsWith("Представление");
    // Ссылка журнала — составной тип из ссылок его документов
    assertThat(field(journal, "Ссылка").get("type")).isEqualTo(Map.of("types", List.of(
      "cfg:DocumentRef._ДемоСписаниеТоваров", "cfg:DocumentRef._ДемоПеремещениеТоваров",
      "cfg:DocumentRef._ДемоОприходованиеТоваров"), "typeSets", List.of()));
    assertThat(field(journal, "Организация").get("kind")).isEqualTo("column");
  }

  // --- регистры сведений ---------------------------------------------------------------------

  @Test
  void регистрыСведенийПериодическийИНепериодический() throws Exception {
    Map<String, Object> rates = read("InformationRegisters/КурсыВалют");
    Map<String, Object> main = table(rates, "РегистрСведений.КурсыВалют");
    // Независимый периодический: период есть, полей регистратора нет
    assertThat(names(main)).containsExactly("Период", "Валюта", "Курс", "Кратность",
      "ОбластьДанныхОсновныеДанные");
    assertThat(field(main, "Валюта").get("kind")).isEqualTo("dimension");
    assertThat(field(main, "Курс").get("kind")).isEqualTo("resource");
    Map<String, Object> slice = table(rates, "РегистрСведений.КурсыВалют.СрезПоследних");
    assertThat(slice.get("kind")).isEqualTo("virtual");
    assertThat(params(slice)).containsExactly("Период:period", "Условие:condition");
    assertThat(names(slice)).isEqualTo(names(main));
    assertThat(tableNames(rates)).containsExactly("РегистрСведений.КурсыВалют",
      "РегистрСведений.КурсыВалют.СрезПоследних", "РегистрСведений.КурсыВалют.СрезПервых");

    // Непериодический независимый: ни периода, ни срезов
    Map<String, Object> schedules = read("InformationRegisters/_ДемоГрафикиРаботы");
    assertThat(tableNames(schedules)).containsExactly("РегистрСведений._ДемоГрафикиРаботы");
    assertThat(names(table(schedules, "РегистрСведений._ДемоГрафикиРаботы"))).containsExactly("Дата", "Значение",
      "ОбластьДанныхОсновныеДанные");

    // Подчинённый регистратору: регистратор, номер строки и активность
    Map<String, Object> answers = table(read("InformationRegisters/ОтветыНаВопросыАнкет"),
      "РегистрСведений.ОтветыНаВопросыАнкет");
    assertThat(names(answers)).startsWith("Регистратор", "НомерСтроки", "Активность", "Анкета")
      .doesNotContain("Период");
  }

  // --- регистры накопления -------------------------------------------------------------------

  @Test
  void регистрНакопленияОстатковИОборотов() throws Exception {
    Map<String, Object> answer = read("AccumulationRegisters/_ДемоОстаткиТоваровВМестахХранения");
    String name = "РегистрНакопления._ДемоОстаткиТоваровВМестахХранения";
    assertThat(tableNames(answer)).containsExactly(name, name + ".Остатки", name + ".Обороты",
      name + ".ОстаткиИОбороты");
    assertThat(names(table(answer, name))).startsWith("Период", "Регистратор", "НомерСтроки", "Активность",
      "ВидДвижения", "МоментВремени", "Номенклатура");

    Map<String, Object> balance = table(answer, name + ".Остатки");
    assertThat(params(balance)).containsExactly("Период:period", "Условие:condition");
    assertThat(names(balance)).containsExactly("Номенклатура", "МестоХранения", "Организация",
      "ОбластьДанныхОсновныеДанные", "КоличествоОстаток");
    Map<String, Object> rest = field(balance, "КоличествоОстаток");
    assertThat(rest.get("nameEn")).isEqualTo("КоличествоBalance");
    assertThat(rest.get("source")).isEqualTo("Количество");
    assertThat(rest.get("type")).isEqualTo(field(table(answer, name), "Количество").get("type"));

    Map<String, Object> turnovers = table(answer, name + ".Обороты");
    assertThat(params(turnovers)).containsExactly("НачалоПериода:periodStart", "КонецПериода:periodEnd",
      "Периодичность:periodicity", "Условие:condition");
    assertThat(names(turnovers)).containsSubsequence("КоличествоОборот", "КоличествоПриход", "КоличествоРасход",
      "НомерСтроки", "Регистратор", "Период", "ПериодСекунда", "ПериодДекада", "ПериодГод");
    assertThat(field(turnovers, "ПериодМесяц").get("kind")).isEqualTo("period");
    // Значения периодичности перечислены у параметра в порядке списка конструктора; уточнения периода
    // у регистра накопления нет
    assertThat(param(turnovers, "Периодичность").get("values")).isEqualTo(PERIODICITY);
    assertThat(names(turnovers)).doesNotContain("УточнениеПериода");

    Map<String, Object> both = table(answer, name + ".ОстаткиИОбороты");
    assertThat(params(both)).containsExactly("НачалоПериода:periodStart", "КонецПериода:periodEnd",
      "Периодичность:periodicity", "МетодДополнения:method", "Условие:condition");
    assertThat(names(both)).containsSubsequence("КоличествоНачальныйОстаток", "КоличествоОборот",
      "КоличествоПриход", "КоличествоРасход", "КоличествоКонечныйОстаток");
    assertThat(param(both, "МетодДополнения").get("values")).isEqualTo(List.of("Движения", "ДвиженияИГраницыПериода"));
    assertThat(param(both, "Периодичность").get("values")).isEqualTo(PERIODICITY);
  }

  @Test
  void оборотныйРегистрНакопления() throws Exception {
    Map<String, Object> answer = read("AccumulationRegisters/_ДемоОборотыПоСчетамНаОплату");
    String name = "РегистрНакопления._ДемоОборотыПоСчетамНаОплату";
    // У оборотного регистра нет остатков и вида движения, а у оборотов нет прихода и расхода
    assertThat(tableNames(answer)).containsExactly(name, name + ".Обороты");
    assertThat(names(table(answer, name))).doesNotContain("ВидДвижения");
    assertThat(names(table(answer, name + ".Обороты"))).contains("СуммаОборот")
      .doesNotContain("СуммаПриход", "СуммаРасход");
  }

  // --- регистр бухгалтерии и план счетов -----------------------------------------------------

  @Test
  void регистрБухгалтерииСКорреспонденцией() throws Exception {
    Map<String, Object> answer = read("AccountingRegisters/_ДемоЖурналПроводокБухгалтерскогоУчета");
    String name = "РегистрБухгалтерии._ДемоЖурналПроводокБухгалтерскогоУчета";
    assertThat(tableNames(answer)).containsExactly(name, name + ".Остатки", name + ".Обороты",
      name + ".ОстаткиИОбороты", name + ".ОборотыДтКт", name + ".ДвиженияССубконто");
    assertThat(answer).doesNotContainKey("notes");

    Map<String, Object> main = table(answer, name);
    // Три субконто — по MaxExtDimensionCount плана счетов, по дебету и кредиту
    assertThat(names(main)).containsSubsequence("СчетДт", "СчетКт", "СубконтоДт1", "СубконтоДт3", "СубконтоКт1",
      "СубконтоКт3", "ВидСубконтоДт1", "ВидСубконтоКт3").doesNotContain("Счет", "СубконтоДт4", "ВидДвижения");
    assertThat(field(main, "СчетДт").get("type")).isEqualTo(ref("cfg:ChartOfAccountsRef._ДемоОсновной"));
    assertThat(field(main, "СубконтоКт2").get("type"))
      .isEqualTo(Map.of("types", List.of(), "typeSets", List.of("cfg:Characteristic._ДемоВидыСубконто")));
    assertThat(field(main, "ВидСубконтоДт1").get("type"))
      .isEqualTo(ref("cfg:ChartOfCharacteristicTypesRef._ДемоВидыСубконто"));
    // Признак «балансовый» читается из XML: балансовое — одно поле, небалансовое — пара Дт/Кт
    assertThat(field(main, "Организация")).containsEntry("balance", true);
    assertThat(names(main)).contains("ВалютаДт", "ВалютаКт", "Сумма", "ВалютнаяСуммаДт", "КоличествоКт")
      .doesNotContain("Валюта", "СуммаДт", "ВалютнаяСумма");
    assertThat(field(main, "ВалютаДт")).containsEntry("balance", false).containsEntry("source", "Валюта")
      .containsEntry("nameEn", "ВалютаDr");

    Map<String, Object> balance = table(answer, name + ".Остатки");
    assertThat(params(balance)).containsExactly("Период:period", "УсловиеСчета:account",
      "Субконто:extDimensions", "Условие:condition");
    assertThat(names(balance)).startsWith("Счет", "Субконто1", "Субконто2", "Субконто3", "Организация", "Валюта",
      "ОбластьДанныхОсновныеДанные",
      "СуммаОстаток", "СуммаОстатокДт", "СуммаОстатокКт", "СуммаРазвернутыйОстатокДт", "СуммаРазвернутыйОстатокКт");

    Map<String, Object> turnovers = table(answer, name + ".Обороты");
    assertThat(params(turnovers)).hasSize(8).endsWith("УсловиеКорСчета:corAccount", "КорСубконто:corExtDimensions");
    assertThat(names(turnovers)).contains("КорСчет", "КорСубконто1", "СуммаОборотДт", "ВалютнаяСуммаКорОборотКт")
      // Корреспондирующий оборот — только у небалансового ресурса
      .doesNotContain("СуммаКорОборот");
    // Небалансовое измерение даёт в оборотах ещё корреспондирующее поле сразу за собой, балансовое — нет
    assertThat(names(turnovers)).containsSubsequence("Организация", "Валюта", "ВалютаКор")
      .doesNotContain("ОрганизацияКор", "ВалютаДт", "ВалютаКт");
    assertThat(names(turnovers).indexOf("ВалютаКор")).isEqualTo(names(turnovers).indexOf("Валюта") + 1);
    Map<String, Object> currency = field(turnovers, "Валюта");
    assertThat(field(turnovers, "ВалютаКор")).containsEntry("nameEn", "ВалютаBalanced")
      .containsEntry("kind", "dimension").containsEntry("source", "Валюта").containsEntry("balance", false)
      .containsEntry("type", currency.get("type")).containsEntry("synonym", currency.get("synonym"));
    assertThat(currency).containsEntry("balance", false).doesNotContainKey("source");
    // В остатках и остатках с оборотами у любого измерения одно поле, в оборотах Дт/Кт — пара
    assertThat(names(balance)).contains("Валюта").noneMatch(n -> n.endsWith("Кор"));
    assertThat(names(table(answer, name + ".ОстаткиИОбороты"))).contains("Валюта").noneMatch(n -> n.endsWith("Кор"));

    assertThat(params(table(answer, name + ".ОстаткиИОбороты"))).hasSize(7);
    Map<String, Object> drCr = table(answer, name + ".ОборотыДтКт");
    assertThat(params(drCr)).containsExactly("НачалоПериода:periodStart", "КонецПериода:periodEnd",
      "Периодичность:periodicity", "УсловиеСчетаДт:accountDr", "СубконтоДт:extDimensionsDr",
      "УсловиеСчетаКт:accountCr", "СубконтоКт:extDimensionsCr", "Условие:condition");
    assertThat(names(drCr)).contains("СуммаОборот", "ВалютнаяСуммаОборотДт", "ВалютнаяСуммаОборотКт")
      .doesNotContain("СуммаОборотДт", "ВалютнаяСуммаОборот");
    assertThat(names(drCr)).contains("Организация", "ВалютаДт", "ВалютаКт").doesNotContain("Валюта", "ВалютаКор");
    assertThat(names(table(answer, name + ".ДвиженияССубконто"))).contains("ВалютаДт", "ВалютаКт")
      .doesNotContain("ВалютаКор");
    assertThat(params(table(answer, name + ".ДвиженияССубконто"))).containsExactly("НачалоПериода:periodStart",
      "КонецПериода:periodEnd", "Условие:condition", "Порядок:order", "Первые:top");
  }

  @Test
  void регистрБухгалтерииБезКорреспонденции() throws Exception {
    Map<String, Object> answer = read("AccountingRegisters/_ДемоЖурналПроводокБухгалтерскогоУчетаБезКорреспонденции");
    String name = "РегистрБухгалтерии._ДемоЖурналПроводокБухгалтерскогоУчетаБезКорреспонденции";
    // Без корреспонденции нет оборотов Дт/Кт, у оборотов шесть параметров
    assertThat(tableNames(answer)).doesNotContain(name + ".ОборотыДтКт");
    Map<String, Object> main = table(answer, name);
    assertThat(names(main)).contains("Счет", "Субконто1", "ВидСубконто3", "ВидДвижения", "Валюта", "Сумма")
      .doesNotContain("СчетДт", "ВалютаДт");
    assertThat(params(table(answer, name + ".Обороты"))).hasSize(6);
    // Без корреспонденции корреспондирующей стороны нет: ни КорСчет, ни полей <Измерение>Кор
    assertThat(names(table(answer, name + ".Обороты"))).contains("Валюта").doesNotContain("КорСчет")
      .noneMatch(n -> n.endsWith("Кор"));
  }

  @Test
  void уточнениеПериодаТолькоУРегистраБухгалтерииСДлинойУточнения() throws Exception {
    String name = "РегистрБухгалтерии._ДемоЖурналПроводокБухгалтерскогоУчета";
    // В ssl31 длина уточнения периода 0: значения и поля нет, порядок значений — как у регистра накопления
    Map<String, Object> plain = read("AccountingRegisters/_ДемоЖурналПроводокБухгалтерскогоУчета");
    for (String virtual : List.of(".Обороты", ".ОстаткиИОбороты", ".ОборотыДтКт")) {
      assertThat(param(table(plain, name + virtual), "Периодичность").get("values")).as(virtual).isEqualTo(PERIODICITY);
    }
    for (Map<String, Object> table : tables(plain)) {
      assertThat(names(table)).as(table.get("name").toString()).doesNotContain("УточнениеПериода");
    }

    // Мини-выгрузка с длиной 1, как у Хозрасчетного БП с 3.0.106: план счетов рядом, чтобы были субконто
    Path dump = tempDir.resolve("cf");
    Files.createDirectories(dump.resolve("AccountingRegisters"));
    Files.createDirectories(dump.resolve("ChartsOfAccounts"));
    Files.copy(Ssl31SubmodulePaths.configurationXml(), dump.resolve("Configuration.xml"));
    Files.copy(cf("ChartsOfAccounts/_ДемоОсновной"), dump.resolve("ChartsOfAccounts/_ДемоОсновной.xml"));
    Path register = dump.resolve("AccountingRegisters/_ДемоЖурналПроводокБухгалтерскогоУчета.xml");
    String xml = Files.readString(cf("AccountingRegisters/_ДемоЖурналПроводокБухгалтерскогоУчета"),
      StandardCharsets.UTF_8);
    assertThat(xml).containsOnlyOnce("<PeriodAdjustmentLength>0</PeriodAdjustmentLength>");
    Files.writeString(register, xml.replace("<PeriodAdjustmentLength>0</PeriodAdjustmentLength>",
      "<PeriodAdjustmentLength>1</PeriodAdjustmentLength>"), StandardCharsets.UTF_8);
    Map<String, Object> adjusted = MdQueryTables.read(register, SchemaVersion.V2_20, "ru");

    List<String> withAdjustment = new ArrayList<>(PERIODICITY);
    withAdjustment.add(withAdjustment.indexOf("Регистратор") + 1, "УточнениеПериода");
    for (String virtual : List.of(".Обороты", ".ОстаткиИОбороты", ".ОборотыДтКт")) {
      Map<String, Object> table = table(adjusted, name + virtual);
      assertThat(param(table, "Периодичность").get("values")).as(virtual).isEqualTo(withAdjustment);
      // Поле уточнения выбирается при периодичности УточнениеПериода: оно среди полей периода за Период
      assertThat(names(table)).as(virtual).containsSubsequence("Регистратор", "Период", "УточнениеПериода",
        "ПериодСекунда");
      assertThat(field(table, "УточнениеПериода")).containsEntry("nameEn", "PeriodAdjustment")
        .containsEntry("kind", "period").containsEntry("type", Map.of("types", List.of("xs:decimal"),
          "typeSets", List.of()));
    }
    // В основной таблице и движениях с субконто — стандартный реквизит за Период, в остатках его нет
    for (String table : List.of(name, name + ".ДвиженияССубконто")) {
      assertThat(names(table(adjusted, table))).as(table).startsWith("Период", "УточнениеПериода", "Регистратор");
      assertThat(field(table(adjusted, table), "УточнениеПериода")).containsEntry("kind", "standard");
    }
    assertThat(names(table(adjusted, name + ".Остатки"))).doesNotContain("УточнениеПериода");

    // По-английски — PeriodAdjustment и у значения, и у поля
    Map<String, Object> english = MdQueryTables.read(register, SchemaVersion.V2_20, "en");
    Map<String, Object> turnovers = table(english, "AccountingRegister._ДемоЖурналПроводокБухгалтерскогоУчета.Turnovers");
    assertThat(param(turnovers, "Periodicity").get("values")).isEqualTo(List.of("Period", "Record", "Recorder",
      "PeriodAdjustment", "Second", "Minute", "Hour", "Day", "Week", "TenDays", "Month", "Quarter", "HalfYear", "Year",
      "Auto"));
    assertThat(names(turnovers)).containsSubsequence("Recorder", "Period", "PeriodAdjustment", "PeriodSecond")
      .contains("ВалютаBalanced");

    // Проект EDT: та же длина своим элементом — ответ тот же
    Path src = tempDir.resolve("edt").resolve("src");
    Files.createDirectories(src.resolve("Configuration"));
    Files.copy(edt().resolve("Configuration/Configuration.mdo"), src.resolve("Configuration/Configuration.mdo"));
    Files.createDirectories(src.resolve("ChartsOfAccounts/_ДемоОсновной"));
    Files.copy(edt().resolve("ChartsOfAccounts/_ДемоОсновной/_ДемоОсновной.mdo"),
      src.resolve("ChartsOfAccounts/_ДемоОсновной/_ДемоОсновной.mdo"));
    String object = "AccountingRegisters/_ДемоЖурналПроводокБухгалтерскогоУчета";
    Path mdo = Files.createDirectories(src.resolve(object)).resolve("_ДемоЖурналПроводокБухгалтерскогоУчета.mdo");
    String edtXml = Files.readString(edt().resolve(object).resolve("_ДемоЖурналПроводокБухгалтерскогоУчета.mdo"),
      StandardCharsets.UTF_8);
    assertThat(edtXml).doesNotContain("periodAdjustmentLength").containsOnlyOnce("<correspondence>true</correspondence>");
    Files.writeString(mdo, edtXml.replace("<correspondence>true</correspondence>",
      "<correspondence>true</correspondence>\n  <periodAdjustmentLength>1</periodAdjustmentLength>"),
      StandardCharsets.UTF_8);
    Gson gson = new GsonBuilder().serializeNulls().create();
    assertThat(gson.toJson(MdQueryTables.read(mdo, null, "ru"))).isEqualTo(gson.toJson(adjusted));
  }

  @Test
  void регистрБухгалтерииБезПланаСчетовРядом() throws Exception {
    // Регистр скопирован без своей выгрузки: число субконто взять неоткуда, их не придумываем
    Path source = cf("AccountingRegisters/_ДемоЖурналПроводокБухгалтерскогоУчета");
    Path copy = Files.createDirectories(tempDir.resolve("cf").resolve("AccountingRegisters"))
      .resolve(source.getFileName().toString());
    Files.copy(source, copy, StandardCopyOption.REPLACE_EXISTING);

    Map<String, Object> answer = MdQueryTables.read(copy, SchemaVersion.V2_20, "ru");

    assertThat(String.valueOf(answer.get("notes"))).contains("План счетов _ДемоОсновной не найден")
      .doesNotContain("Общий реквизит");
    Map<String, Object> main = table(answer, "РегистрБухгалтерии._ДемоЖурналПроводокБухгалтерскогоУчета");
    assertThat(names(main)).contains("СчетДт").noneMatch(n -> n.startsWith("СубконтоДт"));
    assertThat(field(main, "СчетДт").get("type")).isEqualTo(ref("cfg:ChartOfAccountsRef._ДемоОсновной"));
    // Общих реквизитов вне выгрузки взять неоткуда: их полей нет ни в одной таблице, ошибки тоже нет
    for (Map<String, Object> table : tables(answer)) {
      assertThat(fields(table)).as(table.get("name").toString())
        .noneMatch(f -> "separator".equals(f.get("kind")) || "commonAttribute".equals(f.get("kind")));
    }
  }

  // --- общие реквизиты -----------------------------------------------------------------------

  @Test
  void разделительВОсновнойИВиртуальныхТаблицахРегистров() throws Exception {
    // ОбластьДанныхОсновныеДанные ssl31: разделитель «Независимо» с автоиспользованием, регистров в его
    // составе нет — он применён ко всем; конфигуратор показывает его в «Условии» оборотов
    String accumulation = "РегистрНакопления._ДемоОстаткиТоваровВМестахХранения";
    Map<String, Object> answer = read("AccumulationRegisters/_ДемоОстаткиТоваровВМестахХранения");
    Map<String, Object> separator = field(table(answer, accumulation), "ОбластьДанныхОсновныеДанные");
    assertThat(new ArrayList<>(separator.keySet())).containsExactly("name", "nameEn", "kind", "type", "synonym",
      "source", "separatedDataUse");
    assertThat(separator).containsEntry("nameEn", "ОбластьДанныхОсновныеДанные").containsEntry("kind", "separator")
      .containsEntry("synonym", "Область данных основные данные")
      .containsEntry("source", "CommonAttribute.ОбластьДанныхОсновныеДанные")
      .containsEntry("separatedDataUse", "INDEPENDENTLY")
      .containsEntry("type", Map.of("types", List.of("xs:decimal"), "typeSets", List.of(),
        "numberQualifiers", Map.of("digits", "7", "fractionDigits", "0", "allowedSign", "NONNEGATIVE")));
    // В основной таблице — за своими реквизитами, в таблицах итогов — сразу за измерениями
    assertThat(names(table(answer, accumulation))).endsWith("ОбластьДанныхОсновныеДанные");
    for (String virtual : List.of(".Остатки", ".Обороты", ".ОстаткиИОбороты")) {
      List<String> names = names(table(answer, accumulation + virtual));
      assertThat(names).as(virtual).containsOnlyOnce("ОбластьДанныхОсновныеДанные");
      assertThat(names.indexOf("ОбластьДанныхОсновныеДанные")).as(virtual)
        .isEqualTo(names.indexOf("Организация") + 1);
      assertThat(field(table(answer, accumulation + virtual), "ОбластьДанныхОсновныеДанные")).isEqualTo(separator);
    }

    String accounting = "РегистрБухгалтерии._ДемоЖурналПроводокБухгалтерскогоУчета";
    Map<String, Object> journal = read("AccountingRegisters/_ДемоЖурналПроводокБухгалтерскогоУчета");
    assertThat(names(table(journal, accounting))).endsWith("ОбластьДанныхОсновныеДанные");
    assertThat(names(table(journal, accounting + ".Обороты"))).containsSubsequence("Валюта", "ВалютаКор",
      "ОбластьДанныхОсновныеДанные", "СуммаОборот");
    assertThat(names(table(journal, accounting + ".ОстаткиИОбороты"))).containsSubsequence("Организация", "Валюта",
      "ОбластьДанныхОсновныеДанные", "СуммаНачальныйОстаток");
    assertThat(names(table(journal, accounting + ".ОборотыДтКт"))).containsSubsequence("ВалютаДт", "ВалютаКт",
      "ОбластьДанныхОсновныеДанные", "СуммаОборот");
    assertThat(names(table(journal, accounting + ".ДвиженияССубконто"))).endsWith("ОбластьДанныхОсновныеДанные");
    for (Map<String, Object> table : tables(journal)) {
      assertThat(names(table)).as(table.get("name").toString()).containsOnlyOnce("ОбластьДанныхОсновныеДанные");
    }

    // Срезы — копии основной таблицы; табличных частей, перечислений, журналов и констант поле не касается
    Map<String, Object> rates = read("InformationRegisters/КурсыВалют");
    assertThat(names(table(rates, "РегистрСведений.КурсыВалют.СрезПервых"))).endsWith("ОбластьДанныхОсновныеДанные");
    Map<String, Object> currencies = read("Catalogs/Валюты");
    assertThat(names(table(currencies, "Справочник.Валюты"))).endsWith("ОбластьДанныхОсновныеДанные", "Представление");
    assertThat(names(table(currencies, "Справочник.Валюты.Представления"))).doesNotContain("ОбластьДанныхОсновныеДанные");
    assertThat(names(table(read("DocumentJournals/_ДемоСкладскиеДокументы"), "ЖурналДокументов._ДемоСкладскиеДокументы")))
      .doesNotContain("ОбластьДанныхОсновныеДанные");
  }

  @Test
  void составИАвтоиспользованиеОбщихРеквизитов() throws Exception {
    // ДоступныеЛицензии: в составе ОсновныеДанные — «Не использовать», в составе ВспомогательныеДанные
    // (без автоиспользования) — «Использовать»
    Map<String, Object> licenses = table(read("InformationRegisters/ДоступныеЛицензии"),
      "РегистрСведений.ДоступныеЛицензии");
    assertThat(names(licenses)).contains("ОбластьДанныхВспомогательныеДанные")
      .doesNotContain("ОбластьДанныхОсновныеДанные");
    assertThat(field(licenses, "ОбластьДанныхВспомогательныеДанные")).containsEntry("kind", "separator")
      .containsEntry("separatedDataUse", "INDEPENDENTLY_AND_SIMULTANEOUSLY");

    // Не разделители — вид commonAttribute без режима разделения; применённые реквизиты идут по имени
    // перед Представление
    Map<String, Object> chart = table(read("ChartsOfAccounts/_ДемоОсновной"), "ПланСчетов._ДемоОсновной");
    assertThat(names(chart)).endsWith("ОбластьДанныхОсновныеДанные", "ОтредактированныеПредопределенныеРеквизиты",
      "Представление");
    Map<String, Object> edited = field(chart, "ОтредактированныеПредопределенныеРеквизиты");
    assertThat(new ArrayList<>(edited.keySet())).containsExactly("name", "nameEn", "kind", "type", "synonym", "source");
    assertThat(edited).containsEntry("kind", "commonAttribute")
      .containsEntry("source", "CommonAttribute.ОтредактированныеПредопределенныеРеквизиты");
    assertThat(((Map<?, ?>) edited.get("type")).get("types")).isEqualTo(List.of("xs:string"));
    assertThat(names(table(read("ChartsOfAccounts/_ДемоОсновной"), "ПланСчетов._ДемоОсновной.ВидыСубконто")))
      .noneMatch(n -> n.startsWith("ОбластьДанных") || n.startsWith("Отредактированные"));

    Map<String, Object> goods = table(read("Catalogs/_ДемоНоменклатура"), "Справочник._ДемоНоменклатура");
    assertThat(names(goods)).endsWith("КомментарийЯзык1", "КомментарийЯзык2", "НаименованиеЯзык1",
      "НаименованиеЯзык2", "ОбластьДанныхОсновныеДанные", "Представление");
    // Реквизит, в составе которого объекта нет и автоиспользования нет, не применён
    assertThat(names(goods)).doesNotContain("ОбластьДанныхВспомогательныеДанные",
      "ОтредактированныеПредопределенныеРеквизиты");

    // Регистр сведений с обычными общими реквизитами
    Map<String, Object> managers = table(read("InformationRegisters/_ДемоЗаведующиеМестамиХранения"),
      "РегистрСведений._ДемоЗаведующиеМестамиХранения");
    assertThat(names(managers)).endsWith("КомментарийЯзык1", "КомментарийЯзык2", "ОбластьДанныхОсновныеДанные");
  }

  @Test
  void общиеРеквизитыЭталонаПлатформы() throws Exception {
    // Голая выгрузка: ОбщийРеквизит1 без состава и автоиспользования — ни к чему не применён
    Path bare = SamplesSubmodulePaths.bareObjects(SchemaVersion.V2_20);
    Path dump = tempDir.resolve("cf");
    for (String file : List.of("Configuration.xml", "Catalogs/Справочник1.xml",
      "AccumulationRegisters/РегистрНакопления1.xml", "AccountingRegisters/РегистрБухгалтерии1.xml",
      "ChartsOfAccounts/ПланСчетов1.xml", "CommonAttributes/ОбщийРеквизит1.xml")) {
      Files.createDirectories(dump.resolve(file).getParent());
      Files.copy(bare.resolve(file), dump.resolve(file));
    }
    Path catalog = dump.resolve("Catalogs/Справочник1.xml");
    Path register = dump.resolve("AccumulationRegisters/РегистрНакопления1.xml");
    assertThat(names(table(MdQueryTables.read(catalog, SchemaVersion.V2_20, "ru"), "Справочник.Справочник1")))
      .doesNotContain("ОбщийРеквизит1");

    // Автоиспользование: реквизит появляется в основных таблицах, но не в таблицах итогов регистра
    Path attribute = dump.resolve("CommonAttributes/ОбщийРеквизит1.xml");
    String xml = Files.readString(attribute, StandardCharsets.UTF_8);
    assertThat(xml).containsOnlyOnce("<AutoUse>DontUse</AutoUse>").containsOnlyOnce("<Content/>")
      .containsOnlyOnce("<DataSeparation>DontUse</DataSeparation>");
    Files.writeString(attribute, xml.replace("<AutoUse>DontUse</AutoUse>", "<AutoUse>Use</AutoUse>"),
      StandardCharsets.UTF_8);
    Map<String, Object> catalogTables = MdQueryTables.read(catalog, SchemaVersion.V2_20, "ru");
    assertThat(names(table(catalogTables, "Справочник.Справочник1"))).endsWith("ОбщийРеквизит1", "Представление");
    assertThat(field(table(catalogTables, "Справочник.Справочник1"), "ОбщийРеквизит1"))
      .containsEntry("kind", "commonAttribute").containsEntry("synonym", "")
      .containsEntry("type", Map.of("types", List.of("xs:string"), "typeSets", List.of(),
        "stringQualifiers", Map.of("length", "10", "allowedLength", "VARIABLE")));
    Map<String, Object> registerTables = MdQueryTables.read(register, SchemaVersion.V2_20, "ru");
    assertThat(names(table(registerTables, "РегистрНакопления.РегистрНакопления1"))).endsWith("ОбщийРеквизит1");
    for (String virtual : List.of(".Остатки", ".Обороты", ".ОстаткиИОбороты")) {
      assertThat(names(table(registerTables, "РегистрНакопления.РегистрНакопления1" + virtual))).as(virtual)
        .doesNotContain("ОбщийРеквизит1");
    }

    // Разделитель с автоиспользованием, справочник исключён составом: у регистра он и в таблицах итогов
    Files.writeString(dump.resolve("CommonAttributes/Разделитель1.xml"), xml
      .replace("<Name>ОбщийРеквизит1</Name>", "<Name>Разделитель1</Name>")
      .replace("<AutoUse>DontUse</AutoUse>", "<AutoUse>Use</AutoUse>")
      .replace("<DataSeparation>DontUse</DataSeparation>", "<DataSeparation>Separate</DataSeparation>")
      .replace("<Content/>", "<Content><xr:Item><xr:Metadata>Catalog.Справочник1</xr:Metadata>"
        + "<xr:Use>DontUse</xr:Use><xr:ConditionalSeparation/></xr:Item></Content>"), StandardCharsets.UTF_8);
    assertThat(names(table(MdQueryTables.read(catalog, SchemaVersion.V2_20, "ru"), "Справочник.Справочник1")))
      .doesNotContain("Разделитель1");
    registerTables = MdQueryTables.read(register, SchemaVersion.V2_20, "ru");
    assertThat(names(table(registerTables, "РегистрНакопления.РегистрНакопления1")))
      .endsWith("ОбщийРеквизит1", "Разделитель1");
    for (String virtual : List.of(".Остатки", ".Обороты", ".ОстаткиИОбороты")) {
      Map<String, Object> table = table(registerTables, "РегистрНакопления.РегистрНакопления1" + virtual);
      assertThat(names(table)).as(virtual).contains("Разделитель1").doesNotContain("ОбщийРеквизит1");
      assertThat(field(table, "Разделитель1")).containsEntry("kind", "separator")
        .containsEntry("separatedDataUse", "INDEPENDENTLY");
    }

    // Регистр бухгалтерии с корреспонденцией: обычный реквизит — только в основной таблице; разделитель — и в таблицах
    // итогов, и в движениях с субконто (копии основной таблицы без обычных общих реквизитов)
    Path accounting = dump.resolve("AccountingRegisters/РегистрБухгалтерии1.xml");
    String accountingXml = Files.readString(accounting, StandardCharsets.UTF_8);
    assertThat(accountingXml).containsOnlyOnce("<Correspondence>false</Correspondence>");
    Files.writeString(accounting, accountingXml.replace("<Correspondence>false</Correspondence>",
      "<Correspondence>true</Correspondence>"), StandardCharsets.UTF_8);
    String journal = "РегистрБухгалтерии.РегистрБухгалтерии1";
    Map<String, Object> accountingTables = MdQueryTables.read(accounting, SchemaVersion.V2_20, "ru");
    assertThat(names(table(accountingTables, journal))).endsWith("ОбщийРеквизит1", "Разделитель1");
    for (String virtual : List.of(".Остатки", ".Обороты", ".ОстаткиИОбороты", ".ОборотыДтКт", ".ДвиженияССубконто")) {
      Map<String, Object> table = table(accountingTables, journal + virtual);
      assertThat(names(table)).as(virtual).containsOnlyOnce("Разделитель1").doesNotContain("ОбщийРеквизит1");
      assertThat(field(table, "Разделитель1")).as(virtual).containsEntry("kind", "separator");
    }

    // Нечитаемый файл реквизита не роняет ответ: его полей нет, остальные на месте, причина — в notes
    Files.writeString(dump.resolve("CommonAttributes/Битый.xml"), "<MetaDataObject>", StandardCharsets.UTF_8);
    registerTables = MdQueryTables.read(register, SchemaVersion.V2_20, "ru");
    assertThat(names(table(registerTables, "РегистрНакопления.РегистрНакопления1")))
      .endsWith("ОбщийРеквизит1", "Разделитель1");
    assertThat(String.valueOf(registerTables.get("notes"))).contains("Общий реквизит Битый не прочитан");
  }

  @Test
  void составСАвтоФайлДругогоВидаИНечитаемыйРеквизитEdt() throws Exception {
    Path bare = SamplesSubmodulePaths.bareObjects(SchemaVersion.V2_20);
    Path dump = tempDir.resolve("cf");
    for (String file : List.of("Configuration.xml", "Catalogs/Справочник1.xml",
      "CommonAttributes/ОбщийРеквизит1.xml")) {
      Files.createDirectories(dump.resolve(file).getParent());
      Files.copy(bare.resolve(file), dump.resolve(file));
    }
    Path catalog = dump.resolve("Catalogs/Справочник1.xml");
    Path attribute = dump.resolve("CommonAttributes/ОбщийРеквизит1.xml");
    String main = "Справочник.Справочник1";

    // «Авто» в составе (конфигуратор пишет его при условном разделении) — решает автоиспользование, в обе стороны
    String auto = Files.readString(attribute, StandardCharsets.UTF_8).replace("<Content/>",
      "<Content><xr:Item><xr:Metadata>Catalog.Справочник1</xr:Metadata><xr:Use>Auto</xr:Use>"
        + "<xr:ConditionalSeparation/></xr:Item></Content>");
    assertThat(auto).contains("<xr:Use>Auto</xr:Use>").containsOnlyOnce("<AutoUse>DontUse</AutoUse>");
    Files.writeString(attribute, auto, StandardCharsets.UTF_8);
    assertThat(names(table(MdQueryTables.read(catalog, SchemaVersion.V2_20, "ru"), main)))
      .doesNotContain("ОбщийРеквизит1");
    Files.writeString(attribute, auto.replace("<AutoUse>DontUse</AutoUse>", "<AutoUse>Use</AutoUse>"),
      StandardCharsets.UTF_8);
    Map<String, Object> answer = MdQueryTables.read(catalog, SchemaVersion.V2_20, "ru");
    assertThat(names(table(answer, main))).endsWith("ОбщийРеквизит1", "Представление");
    assertThat(answer).doesNotContainKey("notes");

    // Файл другого вида в каталоге общих реквизитов пропускается с записью, прочие реквизиты на месте
    Files.copy(catalog, dump.resolve("CommonAttributes/Справочник1.xml"));
    answer = MdQueryTables.read(catalog, SchemaVersion.V2_20, "ru");
    assertThat(names(table(answer, main))).endsWith("ОбщийРеквизит1", "Представление");
    assertThat(answer.get("notes")).isEqualTo(
      List.of("Файл Справочник1.xml в каталоге CommonAttributes — не общий реквизит, пропущен."));

    // Проект EDT: элемент состава без use — «Авто» (умолчание модели), решает autoUse; нечитаемый .mdo — запись
    Path src = tempDir.resolve("edt").resolve("src");
    Files.createDirectories(src.resolve("Configuration"));
    Files.copy(edt().resolve("Configuration/Configuration.mdo"), src.resolve("Configuration/Configuration.mdo"));
    Path currencies = Files.createDirectories(src.resolve("Catalogs/Валюты")).resolve("Валюты.mdo");
    Files.copy(edt().resolve("Catalogs/Валюты/Валюты.mdo"), currencies);
    String comment = Files.readString(edt().resolve("CommonAttributes/КомментарийЯзык1/КомментарийЯзык1.mdo"),
      StandardCharsets.UTF_8);
    String withoutUse = comment.replace("<name>КомментарийЯзык1</name>", "<name>Реквизит1</name>")
      .replaceAll("(?s)  <content>.*</content>\\R",
        "  <content>\n    <metadata>Catalog.Валюты</metadata>\n  </content>\n");
    assertThat(withoutUse).containsOnlyOnce("<content>").doesNotContain("<use>")
      .containsOnlyOnce("<autoUse>DontUse</autoUse>");
    Path own = Files.createDirectories(src.resolve("CommonAttributes/Реквизит1")).resolve("Реквизит1.mdo");
    Files.writeString(own, withoutUse, StandardCharsets.UTF_8);
    String edtMain = "Справочник.Валюты";
    assertThat(names(table(MdQueryTables.read(currencies, null, "ru"), edtMain))).doesNotContain("Реквизит1");
    Files.writeString(own, withoutUse.replace("<autoUse>DontUse</autoUse>", "<autoUse>Use</autoUse>"),
      StandardCharsets.UTF_8);
    Files.writeString(Files.createDirectories(src.resolve("CommonAttributes/Битый")).resolve("Битый.mdo"),
      "<mdclass:CommonAttribute", StandardCharsets.UTF_8);
    Map<String, Object> edtAnswer = MdQueryTables.read(currencies, null, "ru");
    assertThat(names(table(edtAnswer, edtMain))).endsWith("Реквизит1", "Представление");
    assertThat(field(table(edtAnswer, edtMain), "Реквизит1")).containsEntry("kind", "commonAttribute");
    assertThat(String.valueOf(edtAnswer.get("notes"))).contains("Общий реквизит Битый не прочитан");
  }

  @Test
  void заимствованныйРасширениемОбщийРеквизитНеПеречисляетсяВОбеихРаскладках() throws Exception {
    // Свойства заимствованного реквизита — в основной конфигурации: у EDT без них подставились бы умолчания модели
    // (разделитель с автоиспользованием), у выгрузки — реквизит без типа. Полей нет, запись одна у обеих раскладок.
    // Весь ответ не сравнивается: у заимствованного регистра EDT типы измерений и ресурсов не пишет, выгрузка пишет
    String register = "_ДемоОстаткиТоваровВМестахХранения";
    String expected = "Общий реквизит КомментарийЯзык1 заимствован расширением: его свойства — в основной "
      + "конфигурации, поля не перечислены.";
    Path extension = Ssl31SubmodulePaths.projectRoot().resolve("src/cfe/_ДемоРасширение");
    Path dump = tempDir.resolve("cfe");
    Files.createDirectories(dump.resolve("AccumulationRegisters"));
    Files.createDirectories(dump.resolve("CommonAttributes"));
    Files.copy(extension.resolve("Configuration.xml"), dump.resolve("Configuration.xml"));
    Path xmlRegister = dump.resolve("AccumulationRegisters/" + register + ".xml");
    Files.copy(extension.resolve("AccumulationRegisters/" + register + ".xml"), xmlRegister);
    Files.writeString(dump.resolve("CommonAttributes/КомментарийЯзык1.xml"), """
      <?xml version="1.0" encoding="UTF-8"?>
      <MetaDataObject xmlns="http://v8.1c.ru/8.3/MDClasses" xmlns:xr="http://v8.1c.ru/8.3/xcf/readable" \
      xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" version="2.20">
      \t<CommonAttribute uuid="11111111-2222-3333-4444-555555555555">
      \t\t<Properties>
      \t\t\t<ObjectBelonging>Adopted</ObjectBelonging>
      \t\t\t<Name>КомментарийЯзык1</Name>
      \t\t\t<Comment/>
      \t\t\t<ExtendedConfigurationObject>12bdc7f4-1b8b-4ea4-9a22-bd7a4d1b0a3d</ExtendedConfigurationObject>
      \t\t\t<Content>
      \t\t\t\t<xr:Item>
      \t\t\t\t\t<xr:Metadata>AccumulationRegister._ДемоОстаткиТоваровВМестахХранения</xr:Metadata>
      \t\t\t\t\t<xr:Use>Use</xr:Use>
      \t\t\t\t\t<xr:ConditionalSeparation/>
      \t\t\t\t</xr:Item>
      \t\t\t</Content>
      \t\t</Properties>
      \t</CommonAttribute>
      </MetaDataObject>
      """, StandardCharsets.UTF_8);
    Map<String, Object> designer = MdQueryTables.read(xmlRegister, SchemaVersion.V2_20, "ru");

    Path edtExtension = Path.of(System.getProperty("fixtures.ssl31edt.root"), "ssl31._ДемоРасширение", "src");
    Path src = tempDir.resolve("edt").resolve("src");
    Files.createDirectories(src.resolve("Configuration"));
    Files.copy(edtExtension.resolve("Configuration/Configuration.mdo"), src.resolve("Configuration/Configuration.mdo"));
    Path mdoRegister = Files.createDirectories(src.resolve("AccumulationRegisters/" + register))
      .resolve(register + ".mdo");
    Files.copy(edtExtension.resolve("AccumulationRegisters/" + register + "/" + register + ".mdo"), mdoRegister);
    Files.writeString(Files.createDirectories(src.resolve("CommonAttributes/КомментарийЯзык1"))
      .resolve("КомментарийЯзык1.mdo"), """
      <?xml version="1.0" encoding="UTF-8"?>
      <mdclass:CommonAttribute xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" \
      xmlns:mdclass="http://g5.1c.ru/v8/dt/metadata/mdclass" \
      xmlns:mdclassExtension="http://g5.1c.ru/v8/dt/metadata/mdclass/extension" \
      uuid="11111111-2222-3333-4444-555555555555">
        <name>КомментарийЯзык1</name>
        <objectBelonging>Adopted</objectBelonging>
        <extension xsi:type="mdclassExtension:CommonAttributeExtension"/>
      </mdclass:CommonAttribute>
      """, StandardCharsets.UTF_8);
    Map<String, Object> edtAnswer = MdQueryTables.read(mdoRegister, null, "ru");

    for (Map<String, Object> answer : List.of(designer, edtAnswer)) {
      assertThat(answer.get("notes")).isEqualTo(List.of(expected));
      for (Map<String, Object> table : tables(answer)) {
        assertThat(fields(table)).as(table.get("name").toString())
          .noneMatch(f -> "separator".equals(f.get("kind")) || "commonAttribute".equals(f.get("kind")));
      }
    }
  }

  @Test
  void планСчетовСВидамиСубконто() throws Exception {
    Map<String, Object> answer = read("ChartsOfAccounts/_ДемоОсновной");
    Map<String, Object> main = table(answer, "ПланСчетов._ДемоОсновной");
    assertThat(names(main)).containsSubsequence("Код", "Наименование", "Родитель", "Порядок", "Вид", "Забалансовый",
      "Валютный", "Количественный", "Комментарий", "Представление");
    // Type плана счетов в запросе — Вид, а не Тип, как у журнала документов
    assertThat(field(main, "Вид").get("nameEn")).isEqualTo("Type");
    assertThat(field(main, "Валютный").get("kind")).isEqualTo("accountingFlag");

    Map<String, Object> kinds = table(answer, "ПланСчетов._ДемоОсновной.ВидыСубконто");
    assertThat(kinds.get("kind")).isEqualTo("standardTabularSection");
    assertThat(kinds.get("synonym")).isEqualTo("Виды субконто");
    assertThat(names(kinds)).containsExactly("Ссылка", "НомерСтроки", "ВидСубконто", "Предопределенный",
      "ТолькоОбороты", "Валютный", "Количественный", "Суммовой");
    assertThat(field(kinds, "ВидСубконто").get("type"))
      .isEqualTo(ref("cfg:ChartOfCharacteristicTypesRef._ДемоВидыСубконто"));
    assertThat(field(kinds, "Суммовой").get("kind")).isEqualTo("extDimensionAccountingFlag");
  }

  // --- задача и бизнес-процесс ---------------------------------------------------------------

  @Test
  void задачаСАдресациейИБизнесПроцесс() throws Exception {
    Map<String, Object> task = read("Tasks/ЗадачаИсполнителя");
    Map<String, Object> main = table(task, "Задача.ЗадачаИсполнителя");
    assertThat(names(main)).startsWith("Ссылка", "ВерсияДанных", "ПометкаУдаления", "Номер", "Дата", "Наименование",
      "БизнесПроцесс", "ТочкаМаршрута", "Выполнена").contains("Исполнитель").endsWith("Представление");
    assertThat(field(main, "Исполнитель").get("kind")).isEqualTo("addressingAttribute");
    assertThat(field(main, "Номер").get("type")).isEqualTo(Map.of("types", List.of("xs:string"),
      "typeSets", List.of(), "stringQualifiers", Map.of("length", "14", "allowedLength", "FIXED")));
    Map<String, Object> byPerformer = table(task, "Задача.ЗадачаИсполнителя.ЗадачиПоИсполнителю");
    assertThat(params(byPerformer)).containsExactly("Исполнитель:performer", "Условие:condition");

    Map<String, Object> process = table(read("BusinessProcesses/Задание"), "БизнесПроцесс.Задание");
    assertThat(names(process)).startsWith("Ссылка", "ВерсияДанных", "ПометкаУдаления", "Номер", "Дата",
      "ВедущаяЗадача", "Стартован", "Завершен").endsWith("Представление");
    assertThat(field(process, "Ссылка").get("type")).isEqualTo(ref("cfg:BusinessProcessRef.Задание"));
  }

  @Test
  void прочиеВидыОбъектов() throws Exception {
    Map<String, Object> calculation = read("ChartsOfCalculationTypes/_ДемоОсновныеНачисления");
    assertThat(tableNames(calculation)).containsExactly("ПланВидовРасчета._ДемоОсновныеНачисления",
      "ПланВидовРасчета._ДемоОсновныеНачисления.БазовыеВидыРасчета",
      "ПланВидовРасчета._ДемоОсновныеНачисления.ВедущиеВидыРасчета",
      "ПланВидовРасчета._ДемоОсновныеНачисления.ВытесняющиеВидыРасчета");

    Map<String, Object> exchange = table(read("ExchangePlans/_ДемоАвтономнаяРабота"), "ПланОбмена._ДемоАвтономнаяРабота");
    assertThat(names(exchange)).containsSubsequence("НомерОтправленного", "НомерПринятого", "ЭтотУзел");

    Map<String, Object> register = read("CalculationRegisters/_ДемоОсновныеНачисления");
    assertThat(names(table(register, "РегистрРасчета._ДемоОсновныеНачисления"))).startsWith("Регистратор",
      "НомерСтроки", "Активность", "ВидРасчета", "ПериодРегистрации", "Сторно", "ПериодДействия");
    assertThat(tableNames(register)).contains("РегистрРасчета._ДемоОсновныеНачисления.ФактическийПериодДействия");

    Map<String, Object> characteristics = table(read("ChartsOfCharacteristicTypes/_ДемоВидыСубконто"),
      "ПланВидовХарактеристик._ДемоВидыСубконто");
    assertThat(names(characteristics)).contains("ТипЗначения");

    Map<String, Object> constant = read("Constants/_ДемоИмяКонфигурацииВОбменеСБиблиотекойСтандартныхПодсистем");
    assertThat(names(table(constant,
      "Константа._ДемоИмяКонфигурацииВОбменеСБиблиотекойСтандартныхПодсистем"))).containsExactly("Значение");
  }

  // --- критерии отбора и последовательности -------------------------------------------------

  @Test
  void критерийОтбораТаблицаСОбязательнымПараметромЗначения() throws Exception {
    Map<String, Object> answer = read("FilterCriteria/СвязанныеДокументы");

    assertThat(answer.get("object")).isEqualTo("КритерийОтбора.СвязанныеДокументы");
    assertThat(answer.get("kind")).isEqualTo("filterCriterion");
    // Одна таблица: основная с параметром — имя объекта без хвоста находит её, как у прочих видов
    assertThat(tableNames(answer)).containsExactly("КритерийОтбора.СвязанныеДокументы");
    Map<String, Object> main = table(answer, "КритерийОтбора.СвязанныеДокументы");
    assertThat(main.get("kind")).isEqualTo("main");
    assertThat(main.get("synonym")).isEqualTo("Связанные документы");
    assertThat(params(main)).containsExactly("Значение:value");
    Map<String, Object> value = param(main, "Значение");
    assertThat(new ArrayList<>(value.keySet())).containsExactly("name", "kind", "required", "type");
    assertThat(value.get("required")).isEqualTo(true);
    // Тип значения — свойство «Тип» критерия в порядке файла
    assertThat(value.get("type")).isEqualTo(Map.of("types", List.of(
      "cfg:DocumentRef._ДемоСчетНаОплатуПокупателю", "cfg:CatalogRef._ДемоДоговорыКонтрагентов",
      "cfg:DocumentRef._ДемоЗаказПокупателя", "cfg:DocumentRef._ДемоРеализацияТоваров"), "typeSets", List.of()));
    // Ссылка — владельцы реквизитов состава без повторов в порядке появления; реквизит табличной части
    // даёт ссылку на сам документ, а справочник из «Типа» в состав не входит
    assertThat(names(main)).containsExactly("Ссылка");
    Map<String, Object> ref = field(main, "Ссылка");
    assertThat(ref.get("nameEn")).isEqualTo("Ref");
    assertThat(ref.get("kind")).isEqualTo("standard");
    assertThat(ref.get("type")).isEqualTo(Map.of("types", List.of("cfg:DocumentRef._ДемоЗаказПокупателя",
      "cfg:DocumentRef._ДемоРеализацияТоваров", "cfg:DocumentRef._ДемоСчетНаОплатуПокупателю"),
      "typeSets", List.of()));
    assertThat(answer).doesNotContainKeys("typeCompositions", "notes");
  }

  @Test
  void критерийОтбораБезТипаИСоставаЭталонаПлатформы() throws Exception {
    // Новый критерий конфигуратора: пустые «Тип» и «Состав» — типов не выдумываем, параметр остаётся
    Path file = SamplesSubmodulePaths.snapshot(SchemaVersion.V2_20, "cf-bare-objects")
      .resolve("FilterCriteria").resolve("КритерийОтбора1.xml");
    Map<String, Object> main = table(MdQueryTables.read(file, SchemaVersion.V2_20, "ru"), "КритерийОтбора.КритерийОтбора1");

    assertThat(params(main)).containsExactly("Значение:value");
    assertThat(param(main, "Значение")).containsEntry("required", true).containsEntry("type", null);
    assertThat(field(main, "Ссылка")).containsEntry("type", null);
  }

  @Test
  void последовательностьОсновнаяТаблицаИГраницы() throws Exception {
    Map<String, Object> answer = read("Sequences/_ДемоДвижениеТоваров");
    String name = "Последовательность._ДемоДвижениеТоваров";

    assertThat(answer.get("object")).isEqualTo(name);
    assertThat(answer.get("kind")).isEqualTo("sequence");
    assertThat(tableNames(answer)).containsExactly(name, name + ".Границы");
    Map<String, Object> main = table(answer, name);
    assertThat(main.get("kind")).isEqualTo("main");
    assertThat(main.get("synonym")).isEqualTo("Демо: Движение товаров");
    assertThat(params(main)).isEmpty();
    assertThat(names(main)).containsExactly("Период", "Регистратор", "МоментВремени", "Номенклатура", "МестоХранения");
    assertThat(field(main, "Период").get("type")).isEqualTo(Map.of("types", List.of("xs:dateTime"),
      "typeSets", List.of(), "dateQualifiers", Map.of("dateFractions", "DATE_TIME")));
    // Регистратор — ссылки на документы последовательности в порядке свойства «Документы»
    assertThat(field(main, "Регистратор").get("type")).isEqualTo(Map.of("types", List.of(
      "cfg:DocumentRef._ДемоРеализацияТоваров", "cfg:DocumentRef._ДемоПоступлениеТоваров"), "typeSets", List.of()));
    assertThat(field(main, "МоментВремени")).containsEntry("type", null);
    // Измерения читаются из файла: модель свойств выгрузки у последовательности их не перебирает
    Map<String, Object> place = field(main, "МестоХранения");
    assertThat(place.get("kind")).isEqualTo("dimension");
    assertThat(place.get("synonym")).isEqualTo("Место хранения");
    assertThat(place.get("type")).isEqualTo(ref("cfg:CatalogRef._ДемоМестаХранения"));

    // Границы — виртуальная таблица без параметров с теми же полями
    Map<String, Object> boundaries = table(answer, name + ".Границы");
    assertThat(boundaries.get("kind")).isEqualTo("virtual");
    assertThat(boundaries.get("synonym")).isEqualTo("");
    assertThat(params(boundaries)).isEmpty();
    assertThat(fields(boundaries)).isEqualTo(fields(main));
    assertThat(answer).doesNotContainKeys("typeCompositions", "notes");
  }

  @Test
  void последовательностьЭталоновПлатформы() throws Exception {
    // С измерением по умолчанию (строка 10) и без документов: Регистратору не из чего взять тип
    Path withDimension = SamplesSubmodulePaths.snapshot(SchemaVersion.V2_20, "cf-object-nodes")
      .resolve("Sequences").resolve("Последовательность1.xml");
    Map<String, Object> nodes = table(MdQueryTables.read(withDimension, SchemaVersion.V2_20, "ru"),
      "Последовательность.Последовательность1");
    assertThat(names(nodes)).containsExactly("Период", "Регистратор", "МоментВремени", "Измерение1");
    assertThat(field(nodes, "Регистратор")).containsEntry("type", null);
    assertThat(field(nodes, "Измерение1").get("type")).isEqualTo(Map.of("types", List.of("xs:string"),
      "typeSets", List.of(), "stringQualifiers", Map.of("length", "10", "allowedLength", "VARIABLE")));

    // Голая последовательность: только стандартные поля
    Path bare = SamplesSubmodulePaths.bareObjects(SchemaVersion.V2_20).resolve("Sequences")
      .resolve("Последовательность1.xml");
    Map<String, Object> answer = MdQueryTables.read(bare, SchemaVersion.V2_20, "ru");
    assertThat(tableNames(answer)).containsExactly("Последовательность.Последовательность1",
      "Последовательность.Последовательность1.Границы");
    assertThat(names(table(answer, "Последовательность.Последовательность1.Границы")))
      .containsExactly("Период", "Регистратор", "МоментВремени");
  }

  @Test
  void критерийИПоследовательностьПоАнглийски() throws Exception {
    Map<String, Object> criterion = MdQueryTables.read(cf("FilterCriteria/СвязанныеДокументы"), SchemaVersion.V2_20, "en");
    assertThat(criterion.get("object")).isEqualTo("FilterCriterion.СвязанныеДокументы");
    Map<String, Object> main = table(criterion, "FilterCriterion.СвязанныеДокументы");
    assertThat(params(main)).containsExactly("Value:value");
    assertThat(names(main)).containsExactly("Ref");

    Map<String, Object> sequence = MdQueryTables.read(cf("Sequences/_ДемоДвижениеТоваров"), SchemaVersion.V2_20, "en");
    assertThat(tableNames(sequence)).containsExactly("Sequence._ДемоДвижениеТоваров",
      "Sequence._ДемоДвижениеТоваров.Boundaries");
    // Свои имена измерений не переводятся
    assertThat(names(table(sequence, "Sequence._ДемоДвижениеТоваров.Boundaries")))
      .containsExactly("Period", "Recorder", "PointInTime", "Номенклатура", "МестоХранения");
  }

  @Test
  void составыОпределяемыхТиповВПараметреКритерияИИзмеренииПоследовательности() throws Exception {
    // В ssl31 у критерия и последовательности определяемых типов нет: мини-выгрузка, где тип критерия
    // и измерения — определяемый тип ФизическоеЛицо. Тип самого параметра и поля остаётся набором
    Path dump = tempDir.resolve("cf");
    Files.createDirectories(dump.resolve("FilterCriteria"));
    Files.createDirectories(dump.resolve("Sequences"));
    Files.createDirectories(dump.resolve("DefinedTypes"));
    Files.copy(Ssl31SubmodulePaths.configurationXml(), dump.resolve("Configuration.xml"));
    Files.copy(Ssl31SubmodulePaths.configurationXml().resolveSibling("DefinedTypes").resolve("ФизическоеЛицо.xml"),
      dump.resolve("DefinedTypes/ФизическоеЛицо.xml"));
    Path criterion = dump.resolve("FilterCriteria/СвязанныеДокументы.xml");
    Files.writeString(criterion, Files.readString(cf("FilterCriteria/СвязанныеДокументы"), StandardCharsets.UTF_8)
      .replace("<v8:Type>cfg:CatalogRef._ДемоДоговорыКонтрагентов</v8:Type>",
        "<v8:TypeSet>cfg:DefinedType.ФизическоеЛицо</v8:TypeSet>"), StandardCharsets.UTF_8);
    Path sequence = dump.resolve("Sequences/_ДемоДвижениеТоваров.xml");
    Files.writeString(sequence, Files.readString(cf("Sequences/_ДемоДвижениеТоваров"), StandardCharsets.UTF_8)
      .replace("<v8:Type>cfg:CatalogRef._ДемоМестаХранения</v8:Type>",
        "<v8:TypeSet>cfg:DefinedType.ФизическоеЛицо</v8:TypeSet>"), StandardCharsets.UTF_8);
    Map<String, Object> expected = Map.of("source", "cfg:DefinedType.ФизическоеЛицо",
      "types", List.of("cfg:CatalogRef._ДемоФизическиеЛица"), "typeSets", List.of());

    Map<String, Object> fromCriterion = MdQueryTables.read(criterion, SchemaVersion.V2_20, "ru");
    assertThat(compositions(fromCriterion)).containsExactly(expected);
    assertThat(((Map<?, ?>) param(table(fromCriterion, "КритерийОтбора.СвязанныеДокументы"), "Значение")
      .get("type")).get("typeSets")).isEqualTo(List.of("cfg:DefinedType.ФизическоеЛицо"));
    Map<String, Object> fromSequence = MdQueryTables.read(sequence, SchemaVersion.V2_20, "ru");
    assertThat(compositions(fromSequence)).containsExactly(expected);
    assertThat(field(table(fromSequence, "Последовательность._ДемоДвижениеТоваров.Границы"), "МестоХранения")
      .get("type")).isEqualTo(Map.of("types", List.of(), "typeSets", List.of("cfg:DefinedType.ФизическоеЛицо")));

    // Проект EDT: те же правки в своей записи, составы — из .mdo определяемого типа
    Path src = tempDir.resolve("edt").resolve("src");
    Files.createDirectories(src.resolve("Configuration"));
    Files.copy(edt().resolve("Configuration/Configuration.mdo"), src.resolve("Configuration/Configuration.mdo"));
    Files.createDirectories(src.resolve("DefinedTypes/ФизическоеЛицо"));
    Files.copy(edt().resolve("DefinedTypes/ФизическоеЛицо/ФизическоеЛицо.mdo"),
      src.resolve("DefinedTypes/ФизическоеЛицо/ФизическоеЛицо.mdo"));
    Path edtSequence = Files.createDirectories(src.resolve("Sequences/_ДемоДвижениеТоваров"))
      .resolve("_ДемоДвижениеТоваров.mdo");
    Files.writeString(edtSequence, Files.readString(
        edt().resolve("Sequences/_ДемоДвижениеТоваров/_ДемоДвижениеТоваров.mdo"), StandardCharsets.UTF_8)
      .replace("<types>CatalogRef._ДемоМестаХранения</types>", "<types>DefinedType.ФизическоеЛицо</types>"),
      StandardCharsets.UTF_8);
    Gson gson = new GsonBuilder().serializeNulls().create();
    assertThat(gson.toJson(MdQueryTables.read(edtSequence, null, "ru"))).isEqualTo(gson.toJson(fromSequence));
  }

  @Test
  void чтениеТаблицКритерияИПоследовательностиНеМеняетСвойстваОбъекта() throws Exception {
    Gson gson = new GsonBuilder().serializeNulls().create();
    for (String object : List.of("FilterCriteria/СвязанныеДокументы", "Sequences/_ДемоДвижениеТоваров")) {
      String name = object.substring(object.indexOf('/') + 1);
      for (Path file : List.of(cf(object), edt().resolve(object).resolve(name + ".mdo"))) {
        boolean isEdt = file.toString().endsWith(".mdo");
        byte[] bytes = Files.readAllBytes(file);
        String before = gson.toJson(isEdt ? EdtObjectProperties.readDto(file,
          EdtModel.bundled()) : MdObjectPropertiesEdit.readDto(file, SchemaVersion.V2_20));
        MdQueryTables.read(file, isEdt ? null : SchemaVersion.V2_20, "ru");
        String after = gson.toJson(isEdt ? EdtObjectProperties.readDto(file,
          EdtModel.bundled()) : MdObjectPropertiesEdit.readDto(file, SchemaVersion.V2_20));
        assertThat(after).as(file.toString()).isEqualTo(before);
        assertThat(Files.readAllBytes(file)).as(file.toString()).isEqualTo(bytes);
      }
    }
  }

  // --- составы определяемых типов и характеристик --------------------------------------------

  @Test
  void составОпределяемогоТипаОтдельнымКлючомБезПравкиТипаПоля() throws Exception {
    Map<String, Object> answer = read("Catalogs/Пользователи");

    // Ключ идёт после таблиц и только при найденных составах
    assertThat(new ArrayList<>(answer.keySet())).containsExactly("object", "kind", "tables", "typeCompositions");
    assertThat(composition(answer, "cfg:DefinedType.ФизическоеЛицо")).isEqualTo(Map.of(
      "source", "cfg:DefinedType.ФизическоеЛицо", "types", List.of("cfg:CatalogRef._ДемоФизическиеЛица"),
      "typeSets", List.of()));
    assertThat(new ArrayList<>(composition(answer, "cfg:DefinedType.ФизическоеЛицо").keySet()))
      .containsExactly("source", "types", "typeSets");
    // Тип поля остаётся набором: состав лежит только в отдельном ключе
    assertThat(field(table(answer, "Справочник.Пользователи"), "ФизическоеЛицо").get("type"))
      .isEqualTo(Map.of("types", List.of(), "typeSets", List.of("cfg:DefinedType.ФизическоеЛицо")));
    // Одна запись на набор, у характеристики из табличной части — своя
    assertThat(compositions(answer)).extracting(row -> row.get("source")).doesNotHaveDuplicates()
      .contains("cfg:DefinedType.Подразделение", "cfg:Characteristic.ДополнительныеРеквизитыИСведения");
    // Составы — типы, а не имена языка запросов: язык ответа на них не влияет
    assertThat(MdQueryTables.read(cf("Catalogs/Пользователи"), SchemaVersion.V2_20, "en").get("typeCompositions"))
      .isEqualTo(answer.get("typeCompositions"));
    // Нет наборов в полях — нет и ключа
    assertThat(read("Catalogs/Валюты")).doesNotContainKey("typeCompositions");
  }

  @Test
  @SuppressWarnings("unchecked")
  void составХарактеристикиСубконтоРавенТипуПланаВидовХарактеристик() throws Exception {
    Map<String, Object> answer = read("AccountingRegisters/_ДемоЖурналПроводокБухгалтерскогоУчета");

    Map<String, Object> row = composition(answer, "cfg:Characteristic._ДемоВидыСубконто");
    assertThat((List<String>) row.get("types")).hasSize(7)
      .allSatisfy(type -> assertThat(type).matches("cfg:(Catalog|Enum)Ref\\..+"));
    MdTypeDescriptionDto chart = chartType("_ДемоВидыСубконто");
    assertThat(row.get("types")).isEqualTo(chart.types);
    assertThat(row.get("typeSets")).isEqualTo(chart.typeSets);
    // Субконто во всех таблицах регистра — один набор, запись одна
    assertThat(compositions(answer)).extracting(r -> r.get("source"))
      .containsOnlyOnce("cfg:Characteristic._ДемоВидыСубконто");
    assertThat(field(table(answer, "РегистрБухгалтерии._ДемоЖурналПроводокБухгалтерскогоУчета.Остатки"), "Субконто1")
      .get("type")).isEqualTo(Map.of("types", List.of(), "typeSets", List.of("cfg:Characteristic._ДемоВидыСубконто")));
  }

  @Test
  void внеВыгрузкиИБезОписанияТипаЗаписиНетИОшибкиНет() throws Exception {
    // Объект скопирован без выгрузки: составы взять неоткуда, ключа нет, тип поля — набор
    Path alone = Files.createDirectories(tempDir.resolve("alone").resolve("Catalogs")).resolve("Пользователи.xml");
    Files.copy(cf("Catalogs/Пользователи"), alone);
    Map<String, Object> outside = MdQueryTables.read(alone, SchemaVersion.V2_20, "ru");
    assertThat(outside).doesNotContainKey("typeCompositions");
    assertThat(field(table(outside, "Справочник.Пользователи"), "ФизическоеЛицо").get("type"))
      .isEqualTo(Map.of("types", List.of(), "typeSets", List.of("cfg:DefinedType.ФизическоеЛицо")));

    // Мини-выгрузка: описание конфигурации и Подразделение есть, ФизическоеЛицо и планов видов
    // характеристик нет — записи только у найденного
    Path dump = tempDir.resolve("cf");
    Files.createDirectories(dump.resolve("Catalogs"));
    Files.createDirectories(dump.resolve("DefinedTypes"));
    Files.copy(Ssl31SubmodulePaths.configurationXml(), dump.resolve("Configuration.xml"));
    Files.copy(cf("Catalogs/Пользователи"), dump.resolve("Catalogs/Пользователи.xml"));
    Files.copy(Ssl31SubmodulePaths.configurationXml().resolveSibling("DefinedTypes").resolve("Подразделение.xml"),
      dump.resolve("DefinedTypes/Подразделение.xml"));
    Map<String, Object> partial = MdQueryTables.read(dump.resolve("Catalogs/Пользователи.xml"), SchemaVersion.V2_20, "ru");
    assertThat(compositions(partial)).extracting(row -> row.get("source"))
      .containsExactly("cfg:DefinedType.Подразделение");
    assertThat(composition(partial, "cfg:DefinedType.Подразделение").get("types"))
      .isEqualTo(List.of("cfg:CatalogRef._ДемоПодразделения"));
  }

  @Test
  @SuppressWarnings("unchecked")
  void составыКорпусаРавныСловарюИТипуПланаВидовХарактеристикАEdtОтвечаетТакЖе() throws Exception {
    // Составы словаря окна «Тип значения» — независимый путь к тем же описаниям определяемых типов
    Map<String, Map<String, Object>> dictionary = new HashMap<>();
    Path template = cf("Reports/АнализЖурналаРегистрации/Templates/ОсновнаяСхемаКомпоновкиДанных/Ext/Template");
    for (Object row : (List<Object>) DcsTypeDictionary.read(template, SchemaVersion.V2_20).get("definedTypes")) {
      Map<String, Object> map = (Map<String, Object>) row;
      dictionary.put((String) map.get("name"), (Map<String, Object>) map.get("composition"));
    }
    assertThat(dictionary).hasSize(76).doesNotContainValue(null);
    Map<String, MdTypeDescriptionDto> charts = new HashMap<>();
    Gson gson = new GsonBuilder().serializeNulls().create();
    int definedFields = 0;
    int characteristicFields = 0;
    int objects = 0;
    for (String kind : KINDS) {
      for (Path file : objectFiles(kind)) {
        Map<String, Object> answer = MdQueryTables.read(file, SchemaVersion.V2_20, "ru");
        // Наборы в параметрах и полях всех таблиц в порядке первого вхождения — так же копит записи
        // операция (типизированный параметр есть только у критерия отбора)
        Set<String> used = new LinkedHashSet<>();
        for (Map<String, Object> table : tables(answer)) {
          List<Map<String, Object>> typed = new ArrayList<>((List<Map<String, Object>>) table.get("params"));
          typed.addAll(fields(table));
          for (Map<String, Object> field : typed) {
            Map<String, Object> type = (Map<String, Object>) field.get("type");
            for (String typeSet : type == null ? List.<String>of() : (List<String>) type.get("typeSets")) {
              if (typeSet.startsWith("cfg:DefinedType.")) {
                definedFields++;
                used.add(typeSet);
              } else if (typeSet.startsWith("cfg:Characteristic.")) {
                characteristicFields++;
                used.add(typeSet);
              }
            }
          }
        }
        if (used.isEmpty()) {
          assertThat(answer).as(file.toString()).doesNotContainKey("typeCompositions");
          continue;
        }
        List<Map<String, Object>> rows = compositions(answer);
        // У каждого встреченного набора есть запись, записей без поля нет: все описания в ssl31 на месте
        assertThat(rows).as(file.toString()).extracting(row -> row.get("source")).containsExactlyElementsOf(used);
        for (Map<String, Object> row : rows) {
          String source = (String) row.get("source");
          if (source.startsWith("cfg:DefinedType.")) {
            Map<String, Object> expected = dictionary.get(source.substring("cfg:DefinedType.".length()));
            assertThat(expected).as(source).isNotNull();
            assertThat(row.get("types")).as(file + " " + source).isEqualTo(expected.get("types"));
            assertThat(row.get("typeSets")).as(file + " " + source).isEqualTo(expected.get("typeSets"));
          } else {
            MdTypeDescriptionDto chart = charts.computeIfAbsent(source.substring("cfg:Characteristic.".length()),
              MdQueryTablesTest::chartType);
            assertThat(row.get("types")).as(file + " " + source).isEqualTo(chart.types);
            assertThat(row.get("typeSets")).as(file + " " + source).isEqualTo(chart.typeSets);
          }
        }
        // Проект EDT читает описания своей моделью, а составы отдаёт теми же записями
        String name = file.getFileName().toString().replaceFirst("\\.xml$", "");
        Path mdo = edt().resolve(kind).resolve(name).resolve(name + ".mdo");
        assertThat(mdo).as(file.toString()).isRegularFile();
        assertThat(gson.toJson(MdQueryTables.read(mdo, null, "ru").get("typeCompositions"))).as(mdo.toString())
          .isEqualTo(gson.toJson(rows));
        objects++;
      }
    }
    // В метаданных ssl31 183 узла с определяемым типом и 38 с характеристикой в 101 объекте; полей больше:
    // копии в виртуальных таблицах и субконто регистров бухгалтерии (288 и 85 полей, 111 объектов)
    assertThat(definedFields).isGreaterThanOrEqualTo(183);
    assertThat(characteristicFields).isGreaterThanOrEqualTo(38);
    assertThat(objects).isGreaterThanOrEqualTo(101);
  }

  // --- язык, отказы, EDT, канал --------------------------------------------------------------

  @Test
  void английскиеИмена() throws Exception {
    Map<String, Object> answer = MdQueryTables.read(
      cf("AccumulationRegisters/_ДемоОстаткиТоваровВМестахХранения"), SchemaVersion.V2_20, "en");

    assertThat(answer.get("object")).isEqualTo("AccumulationRegister._ДемоОстаткиТоваровВМестахХранения");
    Map<String, Object> balance = table(answer, "AccumulationRegister._ДемоОстаткиТоваровВМестахХранения.Balance");
    assertThat(params(balance)).containsExactly("Period:period", "Condition:condition");
    // Свои имена объекта не переводятся, суффикс виртуальной таблицы — английский
    assertThat(names(balance)).containsExactly("Номенклатура", "МестоХранения", "Организация",
      "ОбластьДанныхОсновныеДанные", "КоличествоBalance");
    Map<String, Object> main = table(answer, "AccumulationRegister._ДемоОстаткиТоваровВМестахХранения");
    assertThat(names(main)).startsWith("Period", "Recorder", "LineNumber", "Active", "RecordType");
    // Синонимы остаются на языке конфигурации
    assertThat(field(main, "Period").get("synonym")).isEqualTo("Период");
  }

  @Test
  void отказы() throws Exception {
    assertThatThrownBy(() -> MdQueryTables.read(cf("CommonModules/ОбщегоНазначения"), SchemaVersion.V2_20, "ru"))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("commonModule");
    assertThatThrownBy(() -> MdQueryTables.read(cf("Catalogs/Валюты"), SchemaVersion.V2_20, "de"))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("ru или en");
    assertThatThrownBy(() -> MdQueryTables.read(tempDir.resolve("Нет.xml"), SchemaVersion.V2_20, "ru"))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("не найден");
    // Таблиц внешних источников данных операция не строит: отказ, как у прочих видов без таблиц
    assertThatThrownBy(() -> MdQueryTables.read(SamplesSubmodulePaths.bareObjects(SchemaVersion.V2_20)
      .resolve("ExternalDataSources").resolve("ВнешнийИсточник1.xml"), SchemaVersion.V2_20, "ru"))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("externalDataSource");
  }

  @Test
  void проектEdtОтвечаетТакЖеКакВыгрузка() throws Exception {
    // Свойства EDT-объекта читаются своей моделью, план счетов ищется в src проекта: ответ — тот же
    Gson gson = new GsonBuilder().serializeNulls().create();
    for (String object : List.of("Catalogs/Валюты", "Catalogs/ВопросыШаблонаАнкеты",
      "Documents/_ДемоСчетНаОплатуПокупателю", "InformationRegisters/КурсыВалют",
      "AccumulationRegisters/_ДемоОстаткиТоваровВМестахХранения",
      "AccountingRegisters/_ДемоЖурналПроводокБухгалтерскогоУчета", "ChartsOfAccounts/_ДемоОсновной",
      "ChartsOfCalculationTypes/_ДемоОсновныеНачисления", "CalculationRegisters/_ДемоОсновныеНачисления",
      "Tasks/ЗадачаИсполнителя", "DocumentJournals/_ДемоСкладскиеДокументы",
      // Составы определяемых типов и характеристик (typeCompositions) — тоже из своей модели EDT
      "Catalogs/Пользователи", "Catalogs/_ДемоКонтрагенты", "Documents/ЭлектронноеПисьмоВходящее",
      "Catalogs/НастройкиТранспортаСообщенийОбмена",
      // DTO свойств этих видов у форматов расходятся (у выгрузки нет измерений последовательности, у EDT —
      // документов и типа критерия), а таблицы — те же
      "FilterCriteria/СвязанныеДокументы", "Sequences/_ДемоДвижениеТоваров",
      // Общие реквизиты: свойства — моделью EDT (с её умолчаниями), состав — из .mdo реквизита
      "InformationRegisters/ДоступныеЛицензии", "Catalogs/_ДемоНоменклатура")) {
      String name = object.substring(object.indexOf('/') + 1);
      Path mdo = edt().resolve(object).resolve(name + ".mdo");
      assertThat(gson.toJson(MdQueryTables.read(mdo, null, "ru"))).as(object)
        .isEqualTo(gson.toJson(MdQueryTables.read(cf(object), SchemaVersion.V2_20, "ru")));
    }
  }

  @Test
  void всеОбъектыSsl31ЧитаютсяБезОшибок() throws Exception {
    // Обход корпуса: у каждого объекта поддержанных видов основная таблица с полями и имена без пустых
    int objects = 0;
    for (String kind : KINDS) {
      for (Path file : objectFiles(kind)) {
        Map<String, Object> answer = MdQueryTables.read(file, SchemaVersion.V2_20, "ru");
        List<Map<String, Object>> tables = tables(answer);
        assertThat(tables.get(0).get("kind")).as(file.toString()).isEqualTo("main");
        // Все общие реквизиты ssl31 читаются: заметок о них нет
        assertThat(String.valueOf(answer.get("notes"))).as(file.toString()).doesNotContain("Общий реквизит");
        for (Map<String, Object> table : tables) {
          assertThat(fields(table)).as(table.get("name").toString()).isNotEmpty()
            .allSatisfy(f -> assertThat(String.valueOf(f.get("name"))).isNotBlank())
            .allSatisfy(f -> assertThat(f).containsKeys("name", "nameEn", "kind", "type", "synonym"));
        }
        objects++;
      }
    }
    assertThat(objects).isGreaterThan(500);
  }

  @Test
  void каналReadJsonПишетТипNullЯвно() throws Exception {
    String json = readJson("Documents/_ДемоСчетНаОплатуПокупателю");
    // Поле без выводимого типа видно с type: null, а не пропадает из ответа
    assertThat(json).contains("\"name\":\"МоментВремени\",\"nameEn\":\"PointInTime\",\"kind\":\"standard\","
      + "\"type\":null");
    assertThat(json).startsWith("{\"object\":\"Документ._ДемоСчетНаОплатуПокупателю\"");
    // Ключи параметра критерия отбора идут в канале в своём порядке
    assertThat(readJson("FilterCriteria/СвязанныеДокументы"))
      .contains("\"params\":[{\"name\":\"Значение\",\"kind\":\"value\",\"required\":true,\"type\":{\"types\":[");
  }

  @Test
  void каналReadJsonПишетСоставыПослеТаблиц() throws Exception {
    String json = readJson("Catalogs/Пользователи");
    assertThat(json).contains("]}],\"typeCompositions\":[{\"source\":\"cfg:DefinedType.")
      .contains("{\"source\":\"cfg:DefinedType.ФизическоеЛицо\",\"types\":[\"cfg:CatalogRef._ДемоФизическиеЛица\"],"
        + "\"typeSets\":[]}")
      .endsWith("]}");
    // Тип поля в канале — набор, а не его состав
    assertThat(json).contains("\"name\":\"ФизическоеЛицо\",\"nameEn\":\"ФизическоеЛицо\",\"kind\":\"attribute\","
      + "\"type\":{\"types\":[],\"typeSets\":[\"cfg:DefinedType.ФизическоеЛицо\"]}");
    assertThat(readJson("Catalogs/Валюты")).doesNotContain("typeCompositions");
  }

  // --- общее ---------------------------------------------------------------------------------

  /** Значения периодичности без уточнения периода в порядке списка конструктора. */
  private static final List<String> PERIODICITY = List.of("Период", "Запись", "Регистратор", "Секунда", "Минута",
    "Час", "День", "Неделя", "Декада", "Месяц", "Квартал", "Полугодие", "Год", "Авто");

  private static final List<String> KINDS = List.of("Catalogs", "Documents", "DocumentJournals", "Enums",
    "ChartsOfCharacteristicTypes", "ChartsOfAccounts", "ChartsOfCalculationTypes", "ExchangePlans",
    "BusinessProcesses", "Tasks", "InformationRegisters", "AccumulationRegisters", "AccountingRegisters",
    "CalculationRegisters", "Constants", "FilterCriteria", "Sequences");

  /** Файлы объектов одного вида выгрузки ssl31 в порядке имён: обход корпуса воспроизводим. */
  private static List<Path> objectFiles(String kind) throws Exception {
    Path dir = Ssl31SubmodulePaths.projectRoot().resolve("src").resolve("cf").resolve(kind);
    try (Stream<Path> list = Files.list(dir)) {
      return list.filter(p -> p.getFileName().toString().endsWith(".xml")).sorted().toList();
    }
  }

  /** Ответ канала {@code read-json} на объект выгрузки ssl31 — так операцию зовёт расширение. */
  private String readJson(String object) throws Exception {
    Path paramsFile = Files.createTempFile(tempDir, "params", ".json");
    Files.writeString(paramsFile, new Gson().toJson(Map.of("op", "cf-md-query-tables",
      "objectXml", cf(object).toString(), "schemaVersion", "V2_20")), StandardCharsets.UTF_8);
    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    PrintStream prev = System.out;
    int exit;
    try {
      System.setOut(new PrintStream(buffer, true, StandardCharsets.UTF_8));
      exit = new CommandLine(new DesignerXmlCli()).execute("read-json", "--params", paramsFile.toString());
    } finally {
      System.setOut(prev);
    }
    assertThat(exit).as(object).isZero();
    return buffer.toString(StandardCharsets.UTF_8).strip();
  }

  /** Тип значения плана видов характеристик так, как его читает модель свойств объекта. */
  private static MdTypeDescriptionDto chartType(String name) {
    try {
      return MdObjectPropertiesEdit.readDto(cf("ChartsOfCharacteristicTypes/" + name), SchemaVersion.V2_20)
        .chartOfCharacteristicTypes.type;
    } catch (Exception e) {
      throw new AssertionError("план видов характеристик " + name + " не прочитан", e);
    }
  }

  @SuppressWarnings("unchecked")
  private static List<Map<String, Object>> compositions(Map<String, Object> answer) {
    assertThat(answer).containsKey("typeCompositions");
    return (List<Map<String, Object>>) answer.get("typeCompositions");
  }

  private static Map<String, Object> composition(Map<String, Object> answer, String source) {
    return compositions(answer).stream().filter(row -> source.equals(row.get("source"))).findFirst()
      .orElseThrow(() -> new AssertionError("состава " + source + " нет: " + answer.get("typeCompositions")));
  }

  private static Path cf(String object) {
    return Ssl31SubmodulePaths.projectRoot().resolve("src").resolve("cf").resolve(object + ".xml");
  }

  private static Path edt() {
    return Path.of(System.getProperty("fixtures.ssl31edt.root"), "ssl31", "src");
  }

  private static Map<String, Object> read(String object) throws Exception {
    return MdQueryTables.read(cf(object), SchemaVersion.V2_20, "ru");
  }

  private static Map<String, Object> ref(String type) {
    return Map.of("types", List.of(type), "typeSets", List.of());
  }

  @SuppressWarnings("unchecked")
  private static List<Map<String, Object>> tables(Map<String, Object> answer) {
    return (List<Map<String, Object>>) answer.get("tables");
  }

  private static List<String> tableNames(Map<String, Object> answer) {
    return tables(answer).stream().map(t -> String.valueOf(t.get("name"))).toList();
  }

  private static Map<String, Object> table(Map<String, Object> answer, String name) {
    return tables(answer).stream().filter(t -> name.equals(t.get("name"))).findFirst()
      .orElseThrow(() -> new AssertionError("таблица " + name + " не найдена: " + tableNames(answer)));
  }

  @SuppressWarnings("unchecked")
  private static List<Map<String, Object>> fields(Map<String, Object> table) {
    return (List<Map<String, Object>>) table.get("fields");
  }

  private static List<String> names(Map<String, Object> table) {
    return fields(table).stream().map(f -> String.valueOf(f.get("name"))).toList();
  }

  private static Map<String, Object> field(Map<String, Object> table, String name) {
    return fields(table).stream().filter(f -> name.equals(f.get("name"))).findFirst()
      .orElseThrow(() -> new AssertionError("поле " + name + " не найдено: " + names(table)));
  }

  @SuppressWarnings("unchecked")
  private static List<String> params(Map<String, Object> table) {
    return ((List<Map<String, Object>>) table.get("params")).stream()
      .map(p -> p.get("name") + ":" + p.get("kind")).toList();
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> param(Map<String, Object> table, String name) {
    return ((List<Map<String, Object>>) table.get("params")).stream().filter(p -> name.equals(p.get("name")))
      .findFirst().orElseThrow(() -> new AssertionError("параметр " + name + " не найден"));
  }
}
