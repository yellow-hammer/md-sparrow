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
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/** Подписи стандартных реквизитов, снятые с платформы. */
class StandardAttributeLabelsTest {

  @Test
  void подписаныСтандартныеРеквизитыСправочника() {
    assertThat(StandardAttributeLabels.ofObject("catalog"))
      .containsEntry("Ref", "Ссылка")
      .containsEntry("Code", "Код")
      .containsEntry("Description", "Наименование")
      .containsEntry("DeletionMark", "Пометка удаления")
      .containsEntry("Owner", "Владелец")
      .containsEntry("Parent", "Родитель")
      .containsEntry("IsFolder", "Это группа")
      .containsEntry("Predefined", "Предопределенный");
  }

  @Test
  void подписаныСтандартныеРеквизитыДокумента() {
    assertThat(StandardAttributeLabels.ofObject("document"))
      .containsEntry("Ref", "Ссылка")
      .containsEntry("Date", "Дата")
      .containsEntry("Number", "Номер")
      .containsEntry("Posted", "Проведен");
  }

  @Test
  void подписаныСтандартныеРеквизитыРегистров() {
    assertThat(StandardAttributeLabels.ofObject("informationRegister"))
      .containsEntry("Recorder", "Регистратор")
      .containsEntry("Period", "Период")
      .containsEntry("Active", "Активность")
      .containsEntry("LineNumber", "N");
    assertThat(StandardAttributeLabels.ofObject("accumulationRegister"))
      .containsEntry("RecordType", "Вид движения");
    assertThat(StandardAttributeLabels.ofObject("calculationRegister"))
      .containsEntry("CalculationType", "Вид расчета")
      .containsEntry("RegistrationPeriod", "Период регистрации");
  }

  @Test
  void видыСОсобымиРеквизитамиНеЗабыты() {
    assertThat(StandardAttributeLabels.ofObject("task"))
      .containsEntry("Executed", "Выполнена")
      .containsEntry("RoutePoint", "Точка маршрута");
    assertThat(StandardAttributeLabels.ofObject("businessProcess"))
      .containsEntry("Started", "Стартован")
      .containsEntry("HeadTask", "Ведущая задача");
    assertThat(StandardAttributeLabels.ofObject("exchangePlan"))
      .containsEntry("SentNo", "Номер отправленного сообщения")
      .containsEntry("ExchangeDate", "Дата актуальности");
    assertThat(StandardAttributeLabels.ofObject("chartOfAccounts"))
      .containsEntry("OffBalance", "Забалансовый")
      .containsEntry("Order", "Порядок");
    assertThat(StandardAttributeLabels.ofObject("documentJournal"))
      .containsEntry("Type", "Вид документа");
  }

  @Test
  void видБезСтандартныхРеквизитовОтдаётПустоеСоответствие() {
    assertThat(StandardAttributeLabels.ofObject("dataProcessor")).isEmpty();
    assertThat(StandardAttributeLabels.ofObject("такогоВидаНет")).isEmpty();
    assertThat(StandardAttributeLabels.ofObject(null)).isEmpty();
  }

  @Test
  void номерСтрокиТабличнойЧастиСверенСКонфигуратором() {
    // Платформа отдаёт у табличной части пустую подпись, конфигуратор пишет в заголовке N.
    assertThat(StandardAttributeLabels.ofTabularSection()).containsEntry("LineNumber", "N");
  }

  @Test
  void подписаныСтандартныеТабличныеЧасти() {
    assertThat(StandardAttributeLabels.standardTabularSectionLabel("chartOfAccounts", "ExtDimensionTypes"))
      .isEqualTo("Виды субконто");
    assertThat(StandardAttributeLabels.ofStandardTabularSection("chartOfAccounts", "ExtDimensionTypes"))
      .containsEntry("ExtDimensionType", "Вид субконто")
      .containsEntry("TurnoversOnly", "Только обороты");
    assertThat(StandardAttributeLabels.ofStandardTabularSection("chartOfCalculationTypes", "BaseCalculationTypes"))
      .containsEntry("CalculationType", "Вид расчета");
    assertThat(StandardAttributeLabels.ofStandardTabularSection("catalog", "ExtDimensionTypes")).isEmpty();
    assertThat(StandardAttributeLabels.standardTabularSectionLabel("catalog", "ExtDimensionTypes")).isEmpty();
  }

  @Test
  void пустаяПодписьВСловарьНеПопадает() {
    for (String kind : new String[] {"catalog", "document", "informationRegister"}) {
      assertThat(StandardAttributeLabels.ofObject(kind).values()).doesNotContain("");
    }
  }

  @Test
  void известнаВерсияПлатформыСнятия() {
    assertThat(StandardAttributeLabels.platformVersion()).matches("\\d+\\.\\d+\\.\\d+\\.\\d+");
  }

  @Test
  void структураОбъектаВыгрузкиОтдаётПодписиПлатформы() throws Exception {
    assertThat(structure("Catalogs", "ПодписантыСервисаМобильнойПодписи").standardAttributeSynonyms)
      .containsEntry("Ref", "Ссылка")
      .containsEntry("Code", "Код")
      .containsEntry("Description", "Наименование")
      .containsEntry("DeletionMark", "Пометка удаления");

    MdObjectStructureDto document = structure("Documents", "_ДемоЗаказПокупателя");
    assertThat(document.standardAttributeSynonyms)
      .containsEntry("Date", "Дата")
      .containsEntry("Number", "Номер")
      .containsEntry("Posted", "Проведен");
    assertThat(document.tabularSections).isNotEmpty();
    assertThat(document.tabularSections.get(0).standardAttributeSynonyms)
      .containsEntry("LineNumber", "N");
  }

  @Test
  void структураОбъектаВыгрузкиОтдаётСтандартныеТабличныеЧасти() throws Exception {
    MdObjectStructureDto accounts = structure("ChartsOfAccounts", "_ДемоОсновной");
    assertThat(accounts.standardTabularSections).singleElement().satisfies(section -> {
      assertThat(section.name).isEqualTo("ExtDimensionTypes");
      assertThat(section.synonym).isEqualTo("Виды субконто");
      assertThat(section.standardAttributes).contains("TurnoversOnly", "ExtDimensionType");
      assertThat(section.standardAttributeSynonyms)
        .containsEntry("TurnoversOnly", "Только обороты")
        .containsEntry("ExtDimensionType", "Вид субконто");
    });

    MdObjectStructureDto calculationTypes = structure("ChartsOfCalculationTypes", "_ДемоОсновныеНачисления");
    assertThat(calculationTypes.standardTabularSections)
      .extracting(section -> section.name + ": " + section.synonym)
      .containsExactlyInAnyOrder(
        "BaseCalculationTypes: Базовые виды расчета",
        "LeadingCalculationTypes: Ведущие виды расчета",
        "DisplacingCalculationTypes: Вытесняющие виды расчета");
    assertThat(calculationTypes.standardTabularSections)
      .allSatisfy(section -> assertThat(section.standardAttributeSynonyms).containsEntry("CalculationType", "Вид расчета"));

    assertThat(structure("Catalogs", "_ДемоБанковскиеСчета").standardTabularSections).isEmpty();
  }

  /** Переопределённый синоним из файла подпись платформы вытесняет. */
  @Test
  void синонимИзФайлаСильнееПодписиПлатформы() throws Exception {
    assertThat(structure("Catalogs", "_ДемоБанковскиеСчета").standardAttributeSynonyms)
      .containsEntry("Owner", "Владелец счета")
      .containsEntry("Parent", "Группа счетов")
      .containsEntry("Code", "Код");

    assertThat(structure("AccountingRegisters", "_ДемоЖурналПроводокБухгалтерскогоУчета").standardAttributeSynonyms)
      .containsEntry("Recorder", "Платежный документ")
      .containsEntry("Period", "Дата")
      .containsEntry("Active", "Активность");
  }

  private static MdObjectStructureDto structure(String kindDirectory, String name) throws Exception {
    String root = System.getProperty("fixtures.ssl31.root");
    assertThat(root).isNotBlank();
    Path path = Path.of(root, "src", "cf", kindDirectory, name + ".xml");
    assertThat(path).exists();
    return MdObjectStructureRead.read(path, SchemaVersion.V2_20);
  }
}
