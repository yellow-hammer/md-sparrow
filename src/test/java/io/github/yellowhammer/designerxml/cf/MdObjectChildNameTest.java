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

import io.github.yellowhammer.designerxml.SchemaVersion;
import io.github.yellowhammer.designerxml.Ssl31SubmodulePaths;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Имя нового узла состава не должно совпадать с именем соседа так, как их сравнивает платформа.
 *
 * <p>Объекты - из ssl31. Что платформа отвергает, проверено загрузкой {@code ibcmd infobase config
 * import} на 8.3.23, 8.3.24, 8.3.27 и 8.5.1: имена она сравнивает без учёта регистра, и выгрузка с
 * реквизитами «Цена» и «ЦЕНА» у одного справочника не загружается целиком («Дублирование имени
 * объекта метаданных»). Поля данных разных видов (реквизит, табличная часть, измерение, ресурс,
 * признак учёта, реквизит адресации) одно имя не делят, а команды, формы, макеты и реквизиты
 * табличной части называются независимо. Имена стандартных реквизитов и свойств объекта («Код»,
 * «ЭтотОбъект», «Движения») поля не получают. Отвергнутая правка файл не меняет.
 */
class MdObjectChildNameTest {

  private static final SchemaVersion VERSION = SchemaVersion.V2_20;

  @TempDir
  Path workspace;

  @Test
  void реквизитОтличающийсяРегистромОтвергается() throws Exception {
    Path catalog = copy("Catalogs", "_ДемоНоменклатура");

    assertRefused(catalog, "Реквизит уже существует: Цена",
      () -> MdObjectChildMutations.addAttribute(catalog, VERSION, "ЦЕНА"));
    assertRefused(catalog, "Реквизит уже существует: Цена",
      () -> MdObjectChildMutations.renameAttribute(catalog, VERSION, "Штрихкод", "цена"));
    assertRefused(catalog, "Реквизит уже существует: Цена",
      () -> MdObjectChildMutations.duplicateAttribute(catalog, VERSION, "Артикул", "ЦЕНА"));
    assertRefused(catalog, "Табличная часть уже существует: Аналоги",
      () -> MdObjectChildMutations.addTabularSection(catalog, VERSION, "аналоги"));
  }

  @Test
  void реквизитТабличнойЧастиОтличающийсяРегистромОтвергается() throws Exception {
    Path catalog = copy("Catalogs", "_ДемоНоменклатура");

    assertRefused(catalog, "Реквизит ТЧ уже существует: Аналог",
      () -> MdObjectChildMutations.addTabularAttribute(catalog, VERSION, "Аналоги", "АНАЛОГ"));
    assertRefused(catalog, "Реквизит ТЧ уже существует: Наименование",
      () -> MdObjectChildMutations.renameTabularAttribute(catalog, VERSION, "Представления", "КодЯзыка",
        "наименование"));
    assertRefused(catalog, "Реквизит ТЧ уже существует: Артикул",
      () -> MdObjectChildMutations.duplicateTabularAttribute(catalog, VERSION, "Представления", "Артикул",
        "артикул"));
  }

  @Test
  void узлыПрочихВидовОтличающиесяРегистромОтвергаются() throws Exception {
    Path register = copy("InformationRegisters", "ИсполнителиЗадач");
    assertRefused(register, "Измерение уже существует: Исполнитель",
      () -> MdObjectChildMutations.addDimension(register, VERSION, "исполнитель"));
    assertRefused(register, "Команда уже существует: ИсполнителиРоли",
      () -> MdObjectChildMutations.addCommand(register, VERSION, "ИСПОЛНИТЕЛИРОЛИ"));

    Path enumeration = copy("Enums", "_ДемоЮридическоеФизическоеЛицо");
    assertRefused(enumeration, "Значение уже существует: ФизическоеЛицо",
      () -> MdObjectChildMutations.addEnumValue(enumeration, VERSION, "ФИЗИЧЕСКОЕЛИЦО"));

    Path chart = copy("ChartsOfAccounts", "_ДемоОсновной");
    assertRefused(chart, "Признак учёта уже существует: Валютный",
      () -> MdObjectChildMutations.addAccountingFlag(chart, VERSION, "валютный"));
    assertRefused(chart, "Признак учёта субконто уже существует: Суммовой",
      () -> MdObjectChildMutations.addExtDimensionAccountingFlag(chart, VERSION, "СУММОВОЙ"));
  }

  @Test
  void поляДанныхРазныхВидовНеДелятИмя() throws Exception {
    Path catalog = copy("Catalogs", "_ДемоНоменклатура");
    assertRefused(catalog, "Имя аналоги уже занято: табличная часть Аналоги",
      () -> MdObjectChildMutations.addAttribute(catalog, VERSION, "аналоги"));
    assertRefused(catalog, "Имя ЦЕНА уже занято: реквизит Цена",
      () -> MdObjectChildMutations.addTabularSection(catalog, VERSION, "ЦЕНА"));
    assertRefused(catalog, "Имя Представления уже занято: табличная часть Представления",
      () -> MdObjectChildMutations.renameAttribute(catalog, VERSION, "Цена", "Представления"));
    assertRefused(catalog, "Имя Цена уже занято: реквизит Цена",
      () -> MdObjectChildMutations.duplicateTabularSection(catalog, VERSION, "Аналоги", "Цена"));

    Path register = copy("InformationRegisters", "ИсполнителиЗадач");
    assertRefused(register, "Имя Исполнитель уже занято: измерение Исполнитель",
      () -> MdObjectChildMutations.addResource(register, VERSION, "Исполнитель"));
    assertRefused(register, "Имя РОЛЬИСПОЛНИТЕЛЯ уже занято: измерение РольИсполнителя",
      () -> MdObjectChildMutations.addAttribute(register, VERSION, "РОЛЬИСПОЛНИТЕЛЯ"));
    assertRefused(register, "Имя ГруппаИсполнителейЗадач уже занято: реквизит ГруппаИсполнителейЗадач",
      () -> MdObjectChildMutations.addDimension(register, VERSION, "ГруппаИсполнителейЗадач"));

    Path task = copy("Tasks", "ЗадачаИсполнителя");
    assertRefused(task, "Имя Исполнитель уже занято: реквизит адресации Исполнитель",
      () -> MdObjectChildMutations.addAttribute(task, VERSION, "Исполнитель"));
    assertRefused(task, "Имя ОсновнойОбъектАдресации уже занято: реквизит адресации ОсновнойОбъектАдресации",
      () -> MdObjectChildMutations.addTabularSection(task, VERSION, "ОсновнойОбъектАдресации"));

    Path chart = copy("ChartsOfAccounts", "_ДемоОсновной");
    assertRefused(chart, "Имя Валютный уже занято: признак учёта Валютный",
      () -> MdObjectChildMutations.addAttribute(chart, VERSION, "Валютный"));
    assertRefused(chart, "Имя количественный уже занято: признак учёта Количественный",
      () -> MdObjectChildMutations.addTabularSection(chart, VERSION, "количественный"));
    assertRefused(chart, "Имя Комментарий уже занято: реквизит Комментарий",
      () -> MdObjectChildMutations.addAccountingFlag(chart, VERSION, "Комментарий"));
  }

  /** Пары, которые платформа загружает: имена в разных пространствах. */
  @Test
  void командыФормыМакетыИРеквизитыТабличнойЧастиНазываютсяНезависимо() throws Exception {
    Path catalog = copy("Catalogs", "_ДемоНоменклатура");
    MdObjectChildMutations.addCommand(catalog, VERSION, "Цена");
    MdObjectChildMutations.addAttribute(catalog, VERSION, "ФормаЭлемента");
    MdObjectChildMutations.addAttribute(catalog, VERSION, "ЗагрузкаИзФайла");
    MdObjectChildMutations.addTabularAttribute(catalog, VERSION, "Аналоги", "Цена");
    MdObjectChildMutations.addTabularAttribute(catalog, VERSION, "Аналоги", "Аналоги");
    MdObjectChildMutations.addTabularAttribute(catalog, VERSION, "Аналоги", "Представления");

    Path chart = copy("ChartsOfAccounts", "_ДемоОсновной");
    MdObjectChildMutations.addAttribute(chart, VERSION, "Суммовой");

    Path register = copy("InformationRegisters", "ИсполнителиЗадач");
    MdObjectChildMutations.addCommand(register, VERSION, "Исполнитель");

    Path task = copy("Tasks", "ЗадачаИсполнителя");
    MdObjectChildMutations.addCommand(task, VERSION, "Исполнитель");

    assertThat(MdObjectStructureRead.read(catalog, VERSION).commands).contains("Цена");
    assertThat(MdObjectStructureRead.read(chart, VERSION).attributes).extracting(node -> node.name)
      .contains("Суммовой");
    assertThat(MdObjectStructureRead.read(register, VERSION).commands).contains("Исполнитель");
    assertThat(MdObjectStructureRead.read(task, VERSION).commands).contains("Исполнитель");
  }

  /**
   * Внешнюю обработку с реквизитом и табличной частью «Реквизит1» платформа собирает без ошибок,
   * а второй реквизит «РЕКВИЗИТ1» отвергает. Обработка - из эталона платформы cf-object-nodes.
   */
  @Test
  void уВнешнейОбработкиПоляРазныхВидовМогутСовпадатьПоИмени() throws Exception {
    Path processor = copyGolden("ExternalDataProcessors/ВнешняяОбработка1.xml");

    MdObjectChildMutations.addTabularSection(processor, VERSION, "РЕКВИЗИТ1");
    assertRefused(processor, "Реквизит уже существует: Реквизит1",
      () -> MdObjectChildMutations.addAttribute(processor, VERSION, "реквизит1"));

    assertThat(MdObjectStructureRead.read(processor, VERSION).tabularSections)
      .extracting(section -> section.name).contains("РЕКВИЗИТ1");
  }

  /** Имена стандартных реквизитов и свойств объекта платформа полям данных не даёт, на обоих языках. */
  @Test
  void именаСтандартныхРеквизитовИСвойствОбъектаОтвергаются() throws Exception {
    String taken = " занято платформой: так называется стандартный реквизит или свойство объекта";
    Path catalog = copy("Catalogs", "_ДемоНоменклатура");
    assertRefused(catalog, "Имя Наименование" + taken,
      () -> MdObjectChildMutations.addAttribute(catalog, VERSION, "Наименование"));
    assertRefused(catalog, "Имя code" + taken,
      () -> MdObjectChildMutations.addAttribute(catalog, VERSION, "code"));
    assertRefused(catalog, "Имя ЭтотОбъект" + taken,
      () -> MdObjectChildMutations.renameAttribute(catalog, VERSION, "Цена", "ЭтотОбъект"));
    assertRefused(catalog, "Имя Владелец" + taken,
      () -> MdObjectChildMutations.addTabularSection(catalog, VERSION, "Владелец"));

    Path document = copy("Documents", "_ДемоЗаказПокупателя");
    assertRefused(document, "Имя Движения" + taken,
      () -> MdObjectChildMutations.addAttribute(document, VERSION, "Движения"));

    Path chart = copy("ChartsOfAccounts", "_ДемоОсновной");
    assertRefused(chart, "Имя Предопределенный" + taken,
      () -> MdObjectChildMutations.addAttribute(chart, VERSION, "Предопределенный"));
    assertRefused(chart, "Имя Вид" + taken,
      () -> MdObjectChildMutations.addAccountingFlag(chart, VERSION, "Вид"));

    Path register = copy("InformationRegisters", "ИсполнителиЗадач");
    assertRefused(register, "Имя Период" + taken,
      () -> MdObjectChildMutations.addDimension(register, VERSION, "Период"));
    assertRefused(register, "Имя регистратор" + taken,
      () -> MdObjectChildMutations.addResource(register, VERSION, "регистратор"));

    Path calculation = copy("CalculationRegisters", "_ДемоОсновныеНачисления");
    assertRefused(calculation, "Имя ВидРасчета" + taken,
      () -> MdObjectChildMutations.addDimension(calculation, VERSION, "ВидРасчета"));

    Path exchange = copy("ExchangePlans", "_ДемоМобильныйКлиент");
    assertRefused(exchange, "Имя ДополнительныеСвойства" + taken,
      () -> MdObjectChildMutations.addAttribute(exchange, VERSION, "ДополнительныеСвойства"));

    Path task = copy("Tasks", "ЗадачаИсполнителя");
    assertRefused(task, "Имя ТочкаМаршрута" + taken,
      () -> MdObjectChildMutations.addAttribute(task, VERSION, "ТочкаМаршрута"));

    Path processor = copy("DataProcessors", "ЗагрузкаКурсовВалют");
    assertRefused(processor, "Имя ThisObject" + taken,
      () -> MdObjectChildMutations.addAttribute(processor, VERSION, "ThisObject"));
  }

  @Test
  void именаСтандартныхРеквизитовТабличнойЧастиОтвергаются() throws Exception {
    String taken = " занято платформой: так называется стандартный реквизит табличной части";
    Path catalog = copy("Catalogs", "_ДемоНоменклатура");
    assertRefused(catalog, "Имя НомерСтроки" + taken,
      () -> MdObjectChildMutations.addTabularAttribute(catalog, VERSION, "Аналоги", "НомерСтроки"));
    assertRefused(catalog, "Имя REF" + taken,
      () -> MdObjectChildMutations.duplicateTabularAttribute(catalog, VERSION, "Аналоги", "Аналог", "REF"));

    Path processor = copy("DataProcessors", "ЗагрузкаКурсовВалют");
    assertRefused(processor, "Имя LineNumber" + taken,
      () -> MdObjectChildMutations.renameTabularAttribute(processor, VERSION, "СписокВалют", "Курс", "LineNumber"));

    Path external = copyGolden("ExternalDataProcessors/ВнешняяОбработка1.xml");
    assertRefused(external, "Имя номерстроки" + taken,
      () -> MdObjectChildMutations.addTabularAttribute(external, VERSION, "ТабличнаяЧасть2", "номерстроки"));
  }

  /** Имена, которые платформа принимает, хотя похожи на стандартные. */
  @Test
  void прочиеИменаСтандартнымиНеСчитаются() throws Exception {
    Path catalog = copy("Catalogs", "_ДемоНоменклатура");
    MdObjectChildMutations.addAttribute(catalog, VERSION, "Предопределенный");
    MdObjectChildMutations.addCommand(catalog, VERSION, "Код");
    MdObjectChildMutations.addTabularAttribute(catalog, VERSION, "Аналоги", "Код");

    Path chart = copy("ChartsOfAccounts", "_ДемоОсновной");
    MdObjectChildMutations.addAttribute(chart, VERSION, "Порядок");
    MdObjectChildMutations.addExtDimensionAccountingFlag(chart, VERSION, "Вид");

    Path calculation = copy("CalculationRegisters", "_ДемоОсновныеНачисления");
    MdObjectChildMutations.addDimension(calculation, VERSION, "Регистратор");

    Path enumeration = copy("Enums", "_ДемоЮридическоеФизическоеЛицо");
    MdObjectChildMutations.addEnumValue(enumeration, VERSION, "Ссылка");

    Path processor = copy("DataProcessors", "ЗагрузкаКурсовВалют");
    MdObjectChildMutations.addTabularAttribute(processor, VERSION, "СписокВалют", "Ссылка");

    Path external = copyGolden("ExternalDataProcessors/ВнешняяОбработка1.xml");
    MdObjectChildMutations.addAttribute(external, VERSION, "ЭтотОбъект");

    assertThat(MdObjectStructureRead.read(catalog, VERSION).attributes).extracting(node -> node.name)
      .contains("Предопределенный");
    assertThat(MdObjectStructureRead.read(chart, VERSION).extDimensionAccountingFlags).contains("Вид");
    assertThat(MdObjectStructureRead.read(calculation, VERSION).dimensions).contains("Регистратор");
    assertThat(MdObjectStructureRead.read(enumeration, VERSION).values).contains("Ссылка");
    assertThat(MdObjectStructureRead.read(external, VERSION).attributes).extracting(node -> node.name)
      .contains("ЭтотОбъект");
  }

  @Test
  void регистрИмениУзлаМожноСменить() throws Exception {
    Path catalog = copy("Catalogs", "_ДемоНоменклатура");

    MdObjectChildMutations.renameAttribute(catalog, VERSION, "Цена", "ЦЕНА");
    MdObjectChildMutations.renameTabularAttribute(catalog, VERSION, "Аналоги", "Аналог", "АНАЛОГ");

    MdObjectStructureDto structure = MdObjectStructureRead.read(catalog, VERSION);
    assertThat(structure.attributes).extracting(node -> node.name).contains("ЦЕНА").doesNotContain("Цена");
    assertThat(structure.tabularSections).filteredOn(section -> section.name.equals("Аналоги"))
      .singleElement()
      .satisfies(section -> assertThat(section.attributes).extracting(node -> node.name).contains("АНАЛОГ"));
  }

  private Path copy(String kindDirectory, String name) throws Exception {
    Path source = Ssl31SubmodulePaths.projectRoot().resolve("src/cf").resolve(kindDirectory).resolve(name + ".xml");
    Path target = workspace.resolve(kindDirectory).resolve(name + ".xml");
    Files.createDirectories(target.getParent());
    Files.copy(source, target);
    return target;
  }

  /** Файл эталона платформы с узлами объекта канонического формата. */
  private Path copyGolden(String relative) throws Exception {
    Path target = workspace.resolve(relative);
    Files.createDirectories(target.getParent());
    Files.writeString(target, GoldenSnapshots.read(VERSION, "cf-object-nodes", relative), StandardCharsets.UTF_8);
    return target;
  }

  private static void assertRefused(Path file, String message, Edit edit) throws Exception {
    String before = Files.readString(file, StandardCharsets.UTF_8);
    assertThatThrownBy(edit::run).isInstanceOf(IllegalArgumentException.class).hasMessage(message);
    assertThat(Files.readString(file, StandardCharsets.UTF_8)).as("файл после отказа").isEqualTo(before);
  }

  @FunctionalInterface
  private interface Edit {
    void run() throws Exception;
  }
}
