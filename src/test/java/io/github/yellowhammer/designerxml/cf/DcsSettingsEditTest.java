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

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Правки вариантов настроек схем компоновки ssl31: добавление варианта блоком, как его пишет
 * платформа (табы, CRLF, представление {@code v8:LocalStringType} языком конфигурации,
 * самозакрытый {@code dcsset:settings} с четвёркой xmlns, позиция в конце корня), возврат
 * файла байт в байт после добавления и удаления, переименование с проверкой занятости имени,
 * представление (замена, вставка по xs:sequence и снятие) и отказы: занятое и неизвестное
 * имя, дубль имени в файле, вариант вложенной схемы не виден из корня. Резолвер адресации
 * ведёт путь {@code {list, index}} по структуре (таблицы со строками, диаграммы с точками,
 * безтиповые вложенные группировки строк) и не заходит внутрь вложенного отчёта.
 * Правки структуры: добавление узлов скаффолдами конфигуратора (группировка с пятёркой
 * {@code GroupItemField} и порядком/выбором auto, самозакрытые таблица и диаграмма,
 * вложенная группа строки без {@code xsi:type}) в позицию коллекции по xs:sequence вида
 * родителя ({@code column} перед {@code row}, {@code point} перед {@code series}, раскрытие
 * пустого родителя), удаление целыми строками с потомками и перестановка в пределах своей
 * коллекции байтовой перестановкой строк. Канал {@code apply-mutation} выполняет операции
 * вариантов и структуры, отказ доходит кодом 2 без изменения файла. Схема проекта 1С:EDT
 * ({@code Template.dcs} из ssl31-edt) правится теми же операциями с сохранением её физики:
 * без BOM, CRLF и конец без перевода строки.
 */
class DcsSettingsEditTest {

  /** Компактная схема: шесть вариантов, хороша для добавления, удаления и переименования. */
  private static final String DYNAMICS =
    "src/cf/Reports/_ДемоДинамикаИзмененийФайлов/Templates/ОсновнаяСхемаКомпоновкиДанных/Ext/Template.xml";

  /** Вложенный отчёт и вложенные схемы со своими вариантами «Вариант1». */
  private static final String DEMO_FILES =
    "src/cf/Reports/_ДемоФайлы/Templates/ОсновнаяСхемаКомпоновкиДанных/Ext/Template.xml";

  /** Таблицы со строками, диаграммы с точками, вложенные группировки — для резолвера пути. */
  private static final String LOG_ANALYSIS =
    "src/cf/Reports/АнализЖурналаРегистрации/Templates/ОсновнаяСхемаКомпоновкиДанных/Ext/Template.xml";

  /** Эталон нового варианта: один вариант с пустыми настройками и ничего больше. */
  private static final String EMPTY_SCHEMA =
    "src/cf/Reports/_ДемоФайлыВспомогательный/Templates/ПустаяСхемаКомпоновкиДанных/Ext/Template.xml";

  /** Один вариант «Основной»: две группы и таблица в корне — скаффолды структуры байт в байт. */
  private static final String ACCOUNTING_CHECKS =
    "src/cf/Reports/РезультатыПроверкиУчета/Templates/ОсновнаяСхемаКомпоновкиДанных/Ext/Template.xml";

  /** Таблица column+row: строки, колонки и безтиповые вложенные группы. */
  private static final String PERFORMANCE =
    "src/cf/Reports/ОценкаПроизводительности/Templates/ОсновнаяСхемаКомпоновкиДанных/Ext/Template.xml";

  /** Вариант с таблицей и диаграммами: резолвер, удаление и перестановка узлов. */
  private static final String USERS_VARIANT = "АнализАктивностиПользователей";

  /** Вариант с таблицей column+row в конце структуры. */
  private static final String PERIOD_COLUMNS_VARIANT = "ОценкаПроизводительностиПериодВКолонках";

  /** Блок нового варианта байт в байт, как его пишет конфигуратор. */
  private static final String NEW_VARIANT_BLOCK = "\t<settingsVariant>\r\n"
    + "\t\t<dcsset:name>НовыйВариант</dcsset:name>\r\n"
    + "\t\t<dcsset:presentation xsi:type=\"v8:LocalStringType\">\r\n"
    + "\t\t\t<v8:item>\r\n"
    + "\t\t\t\t<v8:lang>ru</v8:lang>\r\n"
    + "\t\t\t\t<v8:content>Новый вариант</v8:content>\r\n"
    + "\t\t\t</v8:item>\r\n"
    + "\t\t</dcsset:presentation>\r\n"
    + "\t\t<dcsset:settings xmlns:style=\"http://v8.1c.ru/8.1/data/ui/style\""
    + " xmlns:sys=\"http://v8.1c.ru/8.1/data/ui/fonts/system\""
    + " xmlns:web=\"http://v8.1c.ru/8.1/data/ui/colors/web\""
    + " xmlns:win=\"http://v8.1c.ru/8.1/data/ui/colors/windows\"/>\r\n"
    + "\t</settingsVariant>\r\n";

  /** Представление «Дополнительный5» в файле: один язык, канонная форма платформы. */
  private static final String FIFTH_PRESENTATION =
    "\t\t<dcsset:presentation xsi:type=\"v8:LocalStringType\">\r\n"
      + "\t\t\t<v8:item>\r\n"
      + "\t\t\t\t<v8:lang>ru</v8:lang>\r\n"
      + "\t\t\t\t<v8:content>Демо: Дополнительный 5</v8:content>\r\n"
      + "\t\t\t</v8:item>\r\n"
      + "\t\t</dcsset:presentation>";

  @TempDir Path tempDir;

  @Test
  void addVariantWritesPlatformBlockAtRootEnd() throws Exception {
    Path dcs = copySchema(DYNAMICS);
    String before = Files.readString(dcs, StandardCharsets.UTF_8);

    DcsSettingsEdit.addSettingsVariant(
      dcs, SchemaVersion.V2_20, "НовыйВариант", Map.of("presentation", "Новый вариант"));

    String after = Files.readString(dcs, StandardCharsets.UTF_8);
    assertSingleEditZone(before, after);
    // физика файла платформы: BOM, CRLF и конец без перевода строки не сломаны
    assertThat(after).startsWith("﻿<?xml");
    assertThat(after).contains("\r\n");
    // блок встал после последнего settingsVariant, то есть в самый конец корня
    assertThat(after).endsWith(NEW_VARIANT_BLOCK + "</DataCompositionSchema>");

    Map<String, Object> variant = variants(info(dcs, "НовыйВариант")).get(0);
    assertThat(variant.get("presentation")).isEqualTo("Новый вариант");
    assertThat(map(variant.get("settings"))).isEmpty();

    // без payload представлением становится имя; второй новый вариант встаёт после первого
    DcsSettingsEdit.addSettingsVariant(dcs, SchemaVersion.V2_20, "ЕщёВариант", Map.of());
    String second = Files.readString(dcs, StandardCharsets.UTF_8);
    assertThat(second).contains("<v8:content>ЕщёВариант</v8:content>");
    assertThat(variants(info(dcs, null))).extracting(row -> row.get("name")).containsExactly(
      "Основной", "Дополнительный1", "Дополнительный2", "Дополнительный3", "Дополнительный4",
      "Дополнительный5", "НовыйВариант", "ЕщёВариант");
  }

  @Test
  void addThenRemoveRestoresOriginalBytes() throws Exception {
    Path dcs = copySchema(DYNAMICS);
    String before = Files.readString(dcs, StandardCharsets.UTF_8);

    DcsSettingsEdit.addSettingsVariant(
      dcs, SchemaVersion.V2_20, "Временный", Map.of("presentation", "Временный вариант"));
    DcsSettingsEdit.removeSettingsVariant(dcs, SchemaVersion.V2_20, "Временный");
    assertThat(Files.readString(dcs, StandardCharsets.UTF_8)).isEqualTo(before);

    // существующий вариант вырезается целыми строками одной зоной
    DcsSettingsEdit.removeSettingsVariant(dcs, SchemaVersion.V2_20, "Дополнительный3");
    String after = Files.readString(dcs, StandardCharsets.UTF_8);
    assertSingleEditZone(before, after);
    assertThat(after).doesNotContain("Дополнительный3");
    assertThat(variants(info(dcs, null))).hasSize(5);
  }

  @Test
  void addWithoutAnchorInsertsAtRootCloseAndRestoresReference() throws Exception {
    // эталон нового варианта: удаление единственного варианта и добавление с теми же
    // именем и представлением возвращают файл платформы байт в байт
    Path dcs = copySchema(EMPTY_SCHEMA);
    String before = Files.readString(dcs, StandardCharsets.UTF_8);

    DcsSettingsEdit.removeSettingsVariant(dcs, SchemaVersion.V2_20, "Основной");
    assertThat(variants(info(dcs, null))).isEmpty();

    DcsSettingsEdit.addSettingsVariant(dcs, SchemaVersion.V2_20, "Основной",
      Map.of("presentation", "Вспомогательный отчет по файлам"));
    assertThat(Files.readString(dcs, StandardCharsets.UTF_8)).isEqualTo(before);
  }

  @Test
  void renameChecksOccupiedNameAndRenames() throws Exception {
    Path dcs = copySchema(DYNAMICS);
    String before = Files.readString(dcs, StandardCharsets.UTF_8);

    // занятое имя - отказ без изменения файла
    assertThatThrownBy(() -> DcsSettingsEdit.setSettingsVariantProps(
      dcs, SchemaVersion.V2_20, "Дополнительный1", Map.of("newName", "Дополнительный2")))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("занято");
    assertThat(Files.readString(dcs, StandardCharsets.UTF_8)).isEqualTo(before);

    // переименование меняет ровно содержимое dcsset:name
    DcsSettingsEdit.setSettingsVariantProps(
      dcs, SchemaVersion.V2_20, "Дополнительный1", Map.of("newName", "Переименованный"));
    String after = Files.readString(dcs, StandardCharsets.UTF_8);
    assertThat(after).isEqualTo(before.replace(
      "<dcsset:name>Дополнительный1</dcsset:name>", "<dcsset:name>Переименованный</dcsset:name>"));
    assertThat(variants(info(dcs, "Переименованный"))).hasSize(1);
    assertThatThrownBy(() -> info(dcs, "Дополнительный1"))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("не найден");

    // собственное имя варианта не считается занятым
    DcsSettingsEdit.setSettingsVariantProps(
      dcs, SchemaVersion.V2_20, "Переименованный", Map.of("newName", "Переименованный"));
    assertThat(Files.readString(dcs, StandardCharsets.UTF_8)).isEqualTo(after);
  }

  @Test
  void variantNamesAreComparedIgnoringCase() throws Exception {
    // Код конфигурации берёт вариант из коллекции схемы по имени без учёта регистра, а мутации
    // адресуют его точным именем: «основной» рядом с «Основной» не различили бы ни те, ни другие
    Path dcs = copySchema(DYNAMICS);
    String before = Files.readString(dcs, StandardCharsets.UTF_8);

    assertThatThrownBy(() -> DcsSettingsEdit.addSettingsVariant(dcs, SchemaVersion.V2_20, "основной", Map.of()))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("уже есть")
      .hasMessageContaining("«Основной»");
    assertThatThrownBy(() -> DcsSettingsEdit.setSettingsVariantProps(
      dcs, SchemaVersion.V2_20, "Дополнительный1", Map.of("newName", "ДОПОЛНИТЕЛЬНЫЙ2")))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("занято");
    assertThat(Files.readString(dcs, StandardCharsets.UTF_8)).isEqualTo(before);

    // смена регистра своего же имени - не занятое имя
    DcsSettingsEdit.setSettingsVariantProps(
      dcs, SchemaVersion.V2_20, "Дополнительный1", Map.of("newName", "ДОПОЛНИТЕЛЬНЫЙ1"));
    assertThat(Files.readString(dcs, StandardCharsets.UTF_8)).isEqualTo(before.replace(
      "<dcsset:name>Дополнительный1</dcsset:name>", "<dcsset:name>ДОПОЛНИТЕЛЬНЫЙ1</dcsset:name>"));
  }

  @Test
  void setPresentationReplacesInsertsAndRemoves() throws Exception {
    Path dcs = copySchema(DYNAMICS);
    String before = Files.readString(dcs, StandardCharsets.UTF_8);

    // замена: существующий элемент переписывается канонной формой v8:LocalStringType
    DcsSettingsEdit.setSettingsVariantProps(
      dcs, SchemaVersion.V2_20, "Дополнительный5", Map.of("presentation", "Пятый вариант"));
    String replaced = Files.readString(dcs, StandardCharsets.UTF_8);
    assertThat(replaced).isEqualTo(before.replace(
      "<v8:content>Демо: Дополнительный 5</v8:content>", "<v8:content>Пятый вариант</v8:content>"));
    assertThat(variants(info(dcs, "Дополнительный5")).get(0).get("presentation"))
      .isEqualTo("Пятый вариант");

    // пустое значение снимает элемент целыми строками: представление по формату необязательно
    Map<String, Object> drop = new HashMap<>();
    drop.put("presentation", null);
    DcsSettingsEdit.setSettingsVariantProps(dcs, SchemaVersion.V2_20, "Дополнительный5", drop);
    String removed = Files.readString(dcs, StandardCharsets.UTF_8);
    assertThat(removed).isEqualTo(replaced.replace(
      FIFTH_PRESENTATION.replace("Демо: Дополнительный 5", "Пятый вариант") + "\r\n", ""));
    assertThat(variants(info(dcs, "Дополнительный5")).get(0)).doesNotContainKey("presentation");

    // вставка на место по xs:sequence (после имени) возвращает файл к состоянию до снятия
    DcsSettingsEdit.setSettingsVariantProps(
      dcs, SchemaVersion.V2_20, "Дополнительный5", Map.of("presentation", "Пятый вариант"));
    assertThat(Files.readString(dcs, StandardCharsets.UTF_8)).isEqualTo(replaced);

    // многоязычное представление заменяется строкой языка конфигурации
    DcsSettingsEdit.setSettingsVariantProps(
      dcs, SchemaVersion.V2_20, "Дополнительный1", Map.of("presentation", "Один"));
    assertThat(variants(info(dcs, "Дополнительный1")).get(0).get("presentation")).isEqualTo("Один");
  }

  @Test
  void refusalsKeepFileIntact() throws Exception {
    Path dcs = copySchema(DYNAMICS);
    String before = Files.readString(dcs, StandardCharsets.UTF_8);

    // дубль имени при добавлении
    assertThatThrownBy(() -> DcsSettingsEdit.addSettingsVariant(dcs, SchemaVersion.V2_20, "Основной", Map.of()))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("уже есть");
    assertThatThrownBy(() -> DcsSettingsEdit.addSettingsVariant(dcs, SchemaVersion.V2_20, " ", Map.of()))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("name");
    assertThatThrownBy(() -> DcsSettingsEdit.addSettingsVariant(
      dcs, SchemaVersion.V2_20, "Новый", Map.of("uuid", "x")))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("Неизвестное свойство");
    assertThatThrownBy(() -> DcsSettingsEdit.addSettingsVariant(
      dcs, SchemaVersion.V2_20, "Новый", Map.of("presentation", 1)))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("строкой");

    // неизвестный вариант
    assertThatThrownBy(() -> DcsSettingsEdit.removeSettingsVariant(dcs, SchemaVersion.V2_20, "НетТакого"))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("не найден");
    assertThatThrownBy(() -> DcsSettingsEdit.setSettingsVariantProps(
      dcs, SchemaVersion.V2_20, "НетТакого", Map.of("newName", "Новый")))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("не найден");
    assertThatThrownBy(() -> DcsSettingsEdit.setSettingsVariantProps(
      dcs, SchemaVersion.V2_20, "Основной", Map.of("uuid", "x")))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("Неизвестное свойство");
    assertThatThrownBy(() -> DcsSettingsEdit.setSettingsVariantProps(
      dcs, SchemaVersion.V2_20, "Основной", Map.of("newName", " ")))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("непустой строкой");

    // пустые свойства - нет и правки
    DcsSettingsEdit.setSettingsVariantProps(dcs, SchemaVersion.V2_20, "Основной", Map.of());
    assertThat(Files.readString(dcs, StandardCharsets.UTF_8)).isEqualTo(before);

    // XSD уникальность имён не контролирует: дубль в файле - отказ, а не молчаливый выбор первого
    Path doubled = Files.createTempDirectory(tempDir, "dcs").resolve("Template.xml");
    Files.writeString(doubled, before.replace(
      "<dcsset:name>Дополнительный2</dcsset:name>",
      "<dcsset:name>Дополнительный1</dcsset:name>"), StandardCharsets.UTF_8);
    String doubledText = Files.readString(doubled, StandardCharsets.UTF_8);
    assertThatThrownBy(() -> DcsSettingsEdit.removeSettingsVariant(doubled, SchemaVersion.V2_20, "Дополнительный1"))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("дважды");
    assertThatThrownBy(() -> DcsSettingsEdit.setSettingsVariantProps(
      doubled, SchemaVersion.V2_20, "Дополнительный1", Map.of("presentation", "Х")))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("дважды");
    assertThat(Files.readString(doubled, StandardCharsets.UTF_8)).isEqualTo(doubledText);

    // вариант вложенной схемы не адресуется по имени из корня
    Path demoFiles = copySchema(DEMO_FILES);
    String demoBefore = Files.readString(demoFiles, StandardCharsets.UTF_8);
    assertThat(demoBefore).contains("<dcsset:name>Вариант1</dcsset:name>");
    assertThatThrownBy(() -> DcsSettingsEdit.removeSettingsVariant(demoFiles, SchemaVersion.V2_20, "Вариант1"))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("не найден");
    assertThat(Files.readString(demoFiles, StandardCharsets.UTF_8)).isEqualTo(demoBefore);
  }

  @Test
  void resolverWalksStructureListsAndRefusesNestedObject() throws Exception {
    String text = Files.readString(
      Ssl31SubmodulePaths.projectRoot().resolve(LOG_ANALYSIS), StandardCharsets.UTF_8);
    String variant = "АнализАктивностиПользователей";

    // пустой путь - сам корень настроек варианта
    DcsSettingsEdit.ResolvedNode root = DcsSettingsEdit.resolveNode(text, variant, List.of());
    assertThat(root.kind()).isEqualTo(DcsSettingsEdit.NodeKind.SETTINGS);
    assertThat(root.element().name()).isEqualTo("settings");

    // structure корня, rows таблицы и structure строки (безтиповый item вложенной группы)
    assertThat(resolve(text, variant, step("structure", 0)).kind())
      .isEqualTo(DcsSettingsEdit.NodeKind.TABLE);
    assertThat(resolve(text, variant, step("structure", 1)).kind())
      .isEqualTo(DcsSettingsEdit.NodeKind.GROUP);
    DcsSettingsEdit.ResolvedNode row = resolve(text, variant, step("structure", 0), step("rows", 0));
    assertThat(row.kind()).isEqualTo(DcsSettingsEdit.NodeKind.GROUP);
    assertThat(row.element().name()).isEqualTo("row");
    DcsSettingsEdit.ResolvedNode nestedGroup =
      resolve(text, variant, step("structure", 0), step("rows", 0), step("structure", 0));
    assertThat(nestedGroup.kind()).isEqualTo(DcsSettingsEdit.NodeKind.GROUP);
    assertThat(nestedGroup.element().name()).isEqualTo("item");

    // точки диаграммы - группировки
    DcsSettingsEdit.ResolvedNode point = resolve(text, variant, step("structure", 2), step("points", 0));
    assertThat(point.kind()).isEqualTo(DcsSettingsEdit.NodeKind.GROUP);
    assertThat(point.element().name()).isEqualTo("point");

    // отказы: список не по виду узла, номер вне списка, неизвестный список, неизвестный вариант
    assertThatThrownBy(() -> resolve(text, variant, step("rows", 0)))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("не применим").hasMessageContaining("корень настроек");
    assertThatThrownBy(() -> resolve(text, variant, step("structure", 1), step("rows", 0)))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("не применим").hasMessageContaining("группировка");
    assertThatThrownBy(() -> resolve(text, variant, step("structure", 0), step("columns", 0)))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("нет элемента с номером 0").hasMessageContaining("элементов 0");
    assertThatThrownBy(() -> resolve(text, variant, step("structure", 99)))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("нет элемента с номером 99");
    assertThatThrownBy(() -> resolve(text, variant, step("колонки", 0)))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("Неизвестный список");
    assertThatThrownBy(() -> DcsSettingsEdit.resolveNode(text, "НетТакого", List.of()))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("не найден");

    // вложенный отчёт адресуется, но внутрь путь не заходит
    Path demoFiles = Ssl31SubmodulePaths.projectRoot().resolve(DEMO_FILES);
    String demoText = Files.readString(demoFiles, StandardCharsets.UTF_8);
    List<Map<String, Object>> structure = list(
      map(variants(DcsSettingsRead.info(demoFiles, SchemaVersion.V2_20, "ПоТипам")).get(0).get("settings"))
        .get("structure"));
    int nested = -1;
    for (int i = 0; i < structure.size(); i++) {
      if ("nestedObject".equals(structure.get(i).get("kind"))) {
        nested = i;
      }
    }
    assertThat(nested).isNotNegative();
    assertThat(resolve(demoText, "ПоТипам", step("structure", nested)).kind())
      .isEqualTo(DcsSettingsEdit.NodeKind.NESTED_OBJECT);
    int nestedIndex = nested;
    assertThatThrownBy(() -> resolve(demoText, "ПоТипам", step("structure", nestedIndex), step("structure", 0)))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("внутрь вложенного отчёта");
  }

  @Test
  void applyMutationChannelRunsVariantOps() throws Exception {
    Path dcs = copySchema(DYNAMICS);

    assertThat(applyMutation(Map.of(
      "op", "cf-dcs-add-settings-variant", "objectXml", dcs.toString(), "schemaVersion", "V2_20",
      "name", "ЧерезКанал", "payloadJson", "{\"presentation\":\"Вариант через канал\"}"))).isZero();
    assertThat(Files.readString(dcs, StandardCharsets.UTF_8))
      .contains("<dcsset:name>ЧерезКанал</dcsset:name>")
      .contains("<v8:content>Вариант через канал</v8:content>");

    // payload необязателен: представлением становится имя
    assertThat(applyMutation(Map.of(
      "op", "cf-dcs-add-settings-variant", "objectXml", dcs.toString(), "schemaVersion", "V2_20",
      "name", "БезПредставления"))).isZero();
    assertThat(Files.readString(dcs, StandardCharsets.UTF_8))
      .contains("<v8:content>БезПредставления</v8:content>");

    assertThat(applyMutation(Map.of(
      "op", "cf-dcs-set-settings-variant-props", "objectXml", dcs.toString(), "schemaVersion", "V2_20",
      "name", "ЧерезКанал",
      "payloadJson", "{\"newName\":\"КаналПереименован\",\"presentation\":\"Новое представление\"}"))).isZero();
    String renamed = Files.readString(dcs, StandardCharsets.UTF_8);
    assertThat(renamed).contains("<dcsset:name>КаналПереименован</dcsset:name>");
    assertThat(renamed).contains("<v8:content>Новое представление</v8:content>");
    assertThat(renamed).doesNotContain("<dcsset:name>ЧерезКанал</dcsset:name>");

    assertThat(applyMutation(Map.of(
      "op", "cf-dcs-remove-settings-variant", "objectXml", dcs.toString(), "schemaVersion", "V2_20",
      "name", "КаналПереименован"))).isZero();
    assertThat(applyMutation(Map.of(
      "op", "cf-dcs-remove-settings-variant", "objectXml", dcs.toString(), "schemaVersion", "V2_20",
      "name", "БезПредставления"))).isZero();
    assertThat(Files.readString(dcs, StandardCharsets.UTF_8))
      .doesNotContain("КаналПереименован")
      .doesNotContain("БезПредставления");

    // отказ операции доходит из канала кодом 2, файл не меняется
    String before = Files.readString(dcs, StandardCharsets.UTF_8);
    assertThat(applyMutation(Map.of(
      "op", "cf-dcs-remove-settings-variant", "objectXml", dcs.toString(), "schemaVersion", "V2_20",
      "name", "НетТакого"))).isEqualTo(2);
    assertThat(applyMutation(Map.of(
      "op", "cf-dcs-add-settings-variant", "objectXml", dcs.toString(), "schemaVersion", "V2_20",
      "name", "Основной"))).isEqualTo(2);
    assertThat(Files.readString(dcs, StandardCharsets.UTF_8)).isEqualTo(before);
  }

  @Test
  void addGroupWritesAutoScaffoldAtRootEnd() throws Exception {
    Path dcs = copySchema(ACCOUNTING_CHECKS);
    String before = Files.readString(dcs, StandardCharsets.UTF_8);

    // детальные записи: без groupFields нет и groupItems, но порядок и выбор auto на месте
    DcsSettingsEdit.addStructureItem(dcs, SchemaVersion.V2_20, "Основной",
      Map.of("parentPath", List.of(), "list", "structure", "kind", "group"));

    String after = Files.readString(dcs, StandardCharsets.UTF_8);
    assertSingleEditZone(before, after);
    // узел встал в конец списка item корня настроек: прямо перед закрывающим тегом settings
    assertThat(after).isEqualTo(before.replace("\t\t</dcsset:settings>",
      autoScaffold(3, "<dcsset:item xsi:type=\"dcsset:StructureItemGroup\">", "</dcsset:item>")
        + "\t\t</dcsset:settings>"));
    List<Map<String, Object>> structure = structure(dcs, "Основной");
    assertThat(structure).hasSize(4);
    assertThat(structure.get(3).get("kind")).isEqualTo("group");
    assertThat(structure.get(3)).doesNotContainKey("groupFields");

    // удаление добавленного узла возвращает файл байт в байт
    DcsSettingsEdit.removeStructureItem(dcs, SchemaVersion.V2_20, "Основной",
      Map.of("path", List.of(Map.of("list", "structure", "index", 3))));
    assertThat(Files.readString(dcs, StandardCharsets.UTF_8)).isEqualTo(before);
  }

  @Test
  void addGroupWithFieldsWritesFullFivePack() throws Exception {
    Path dcs = copySchema(ACCOUNTING_CHECKS);
    String before = Files.readString(dcs, StandardCharsets.UTF_8);

    // позиция 0: узел встаёт перед первым узлом корня; пятёрка пишется на каждое поле
    DcsSettingsEdit.addStructureItem(dcs, SchemaVersion.V2_20, "Основной", Map.of(
      "parentPath", List.of(), "list", "structure", "kind", "group", "index", 0,
      "groupFields", List.of(
        Map.of("field", "Организация"),
        Map.of("field", "Период", "groupType", "Hierarchy", "periodAdditionType", "Month",
          "periodAdditionBegin", Map.of("nil", true),
          "periodAdditionEnd", Map.of("type", "xs:dateTime", "text", "2026-01-01T23:59:59")))));

    String after = Files.readString(dcs, StandardCharsets.UTF_8);
    assertSingleEditZone(before, after);
    String block = "\t\t\t<dcsset:item xsi:type=\"dcsset:StructureItemGroup\">\r\n"
      + "\t\t\t\t<dcsset:groupItems>\r\n"
      + "\t\t\t\t\t<dcsset:item xsi:type=\"dcsset:GroupItemField\">\r\n"
      + "\t\t\t\t\t\t<dcsset:field>Организация</dcsset:field>\r\n"
      + "\t\t\t\t\t\t<dcsset:groupType>Items</dcsset:groupType>\r\n"
      + "\t\t\t\t\t\t<dcsset:periodAdditionType>None</dcsset:periodAdditionType>\r\n"
      + "\t\t\t\t\t\t<dcsset:periodAdditionBegin xsi:type=\"xs:dateTime\">0001-01-01T00:00:00"
      + "</dcsset:periodAdditionBegin>\r\n"
      + "\t\t\t\t\t\t<dcsset:periodAdditionEnd xsi:type=\"xs:dateTime\">0001-01-01T00:00:00"
      + "</dcsset:periodAdditionEnd>\r\n"
      + "\t\t\t\t\t</dcsset:item>\r\n"
      + "\t\t\t\t\t<dcsset:item xsi:type=\"dcsset:GroupItemField\">\r\n"
      + "\t\t\t\t\t\t<dcsset:field>Период</dcsset:field>\r\n"
      + "\t\t\t\t\t\t<dcsset:groupType>Hierarchy</dcsset:groupType>\r\n"
      + "\t\t\t\t\t\t<dcsset:periodAdditionType>Month</dcsset:periodAdditionType>\r\n"
      + "\t\t\t\t\t\t<dcsset:periodAdditionBegin xsi:nil=\"true\"/>\r\n"
      + "\t\t\t\t\t\t<dcsset:periodAdditionEnd xsi:type=\"xs:dateTime\">2026-01-01T23:59:59"
      + "</dcsset:periodAdditionEnd>\r\n"
      + "\t\t\t\t\t</dcsset:item>\r\n"
      + "\t\t\t\t</dcsset:groupItems>\r\n"
      + "\t\t\t\t<dcsset:order>\r\n"
      + "\t\t\t\t\t<dcsset:item xsi:type=\"dcsset:OrderItemAuto\"/>\r\n"
      + "\t\t\t\t</dcsset:order>\r\n"
      + "\t\t\t\t<dcsset:selection>\r\n"
      + "\t\t\t\t\t<dcsset:item xsi:type=\"dcsset:SelectedItemAuto\"/>\r\n"
      + "\t\t\t\t</dcsset:selection>\r\n"
      + "\t\t\t</dcsset:item>\r\n";
    assertThat(after).isEqualTo(insertedBefore(before,
      DcsSettingsEdit.resolveNode(before, "Основной", List.of(step("structure", 0))).element(), block));
    List<Map<String, Object>> fields = list(structure(dcs, "Основной").get(0).get("groupFields"));
    assertThat(fields).extracting(row -> row.get("field")).containsExactly("Организация", "Период");
  }

  @Test
  void addIntoSecondLevelGroupAppendsAfterExistingItems() throws Exception {
    Path dcs = copySchema(LOG_ANALYSIS);
    String before = Files.readString(dcs, StandardCharsets.UTF_8);

    // группа 2-го уровня уже несёт один вложенный узел: новый встаёт после него, до хвоста
    DcsSettingsEdit.addStructureItem(dcs, SchemaVersion.V2_20, USERS_VARIANT, Map.of(
      "parentPath", List.of(
        Map.of("list", "structure", "index", 1), Map.of("list", "structure", "index", 0)),
      "list", "structure", "kind", "group"));

    String after = Files.readString(dcs, StandardCharsets.UTF_8);
    assertSingleEditZone(before, after);
    DcsSettingsEdit.ResolvedNode parent = DcsSettingsEdit.resolveNode(
      before, USERS_VARIANT, List.of(step("structure", 1), step("structure", 0)));
    assertThat(after).isEqualTo(insertedAtClose(before, parent.element(),
      autoScaffold(5, "<dcsset:item xsi:type=\"dcsset:StructureItemGroup\">", "</dcsset:item>")));
    Map<String, Object> node = structure(dcs, USERS_VARIANT).get(1);
    assertThat(list(list(node.get("structure")).get(0).get("structure"))).hasSize(2);
  }

  @Test
  void addRowAndUntypedNestedGroupOnTable() throws Exception {
    Path dcs = copySchema(PERFORMANCE);
    String before = Files.readString(dcs, StandardCharsets.UTF_8);
    List<Map<String, Object>> tablePath = List.of(Map.of("list", "structure", "index", 1));

    // новая строка таблицы с полем группировки: элемент dcsset:row с пятёркой и auto
    DcsSettingsEdit.addStructureItem(dcs, SchemaVersion.V2_20, PERIOD_COLUMNS_VARIANT, Map.of(
      "parentPath", tablePath, "list", "rows",
      "groupFields", List.of(Map.of("field", "Хост"))));
    String withRow = Files.readString(dcs, StandardCharsets.UTF_8);
    assertSingleEditZone(before, withRow);
    String rowBlock = "\t\t\t\t<dcsset:row>\r\n"
      + "\t\t\t\t\t<dcsset:groupItems>\r\n"
      + "\t\t\t\t\t\t<dcsset:item xsi:type=\"dcsset:GroupItemField\">\r\n"
      + "\t\t\t\t\t\t\t<dcsset:field>Хост</dcsset:field>\r\n"
      + "\t\t\t\t\t\t\t<dcsset:groupType>Items</dcsset:groupType>\r\n"
      + "\t\t\t\t\t\t\t<dcsset:periodAdditionType>None</dcsset:periodAdditionType>\r\n"
      + "\t\t\t\t\t\t\t<dcsset:periodAdditionBegin xsi:type=\"xs:dateTime\">0001-01-01T00:00:00"
      + "</dcsset:periodAdditionBegin>\r\n"
      + "\t\t\t\t\t\t\t<dcsset:periodAdditionEnd xsi:type=\"xs:dateTime\">0001-01-01T00:00:00"
      + "</dcsset:periodAdditionEnd>\r\n"
      + "\t\t\t\t\t\t</dcsset:item>\r\n"
      + "\t\t\t\t\t</dcsset:groupItems>\r\n"
      + "\t\t\t\t\t<dcsset:order>\r\n"
      + "\t\t\t\t\t\t<dcsset:item xsi:type=\"dcsset:OrderItemAuto\"/>\r\n"
      + "\t\t\t\t\t</dcsset:order>\r\n"
      + "\t\t\t\t\t<dcsset:selection>\r\n"
      + "\t\t\t\t\t\t<dcsset:item xsi:type=\"dcsset:SelectedItemAuto\"/>\r\n"
      + "\t\t\t\t\t</dcsset:selection>\r\n"
      + "\t\t\t\t</dcsset:row>\r\n";
    assertThat(withRow).isEqualTo(insertedAtClose(before, DcsSettingsEdit.resolveNode(
      before, PERIOD_COLUMNS_VARIANT, List.of(step("structure", 1))).element(), rowBlock));

    // вложенная группа строки пишется элементом dcsset:item БЕЗ xsi:type
    DcsSettingsEdit.addStructureItem(dcs, SchemaVersion.V2_20, PERIOD_COLUMNS_VARIANT, Map.of(
      "parentPath", List.of(Map.of("list", "structure", "index", 1), Map.of("list", "rows", "index", 1)),
      "list", "structure", "kind", "group"));
    String withNested = Files.readString(dcs, StandardCharsets.UTF_8);
    assertThat(withNested).isEqualTo(insertedAtClose(withRow, DcsSettingsEdit.resolveNode(
      withRow, PERIOD_COLUMNS_VARIANT, List.of(step("structure", 1), step("rows", 1))).element(),
      autoScaffold(5, "<dcsset:item>", "</dcsset:item>")));

    // новая колонка встаёт после колонок и ПЕРЕД строками: column* в формате раньше row*
    DcsSettingsEdit.addStructureItem(dcs, SchemaVersion.V2_20, PERIOD_COLUMNS_VARIANT,
      Map.of("parentPath", tablePath, "list", "columns"));
    String withColumn = Files.readString(dcs, StandardCharsets.UTF_8);
    assertThat(withColumn).isEqualTo(insertedBefore(withNested, DcsSettingsEdit.resolveNode(
      withNested, PERIOD_COLUMNS_VARIANT, List.of(step("structure", 1), step("rows", 0))).element(),
      autoScaffold(4, "<dcsset:column>", "</dcsset:column>")));

    Map<String, Object> table = structure(dcs, PERIOD_COLUMNS_VARIANT).get(1);
    assertThat(list(table.get("columns"))).hasSize(2);
    assertThat(list(table.get("rows"))).hasSize(2);
    assertThat(list(list(table.get("rows")).get(1).get("structure")).get(0).get("kind")).isEqualTo("group");
  }

  @Test
  void addsSelfClosedTableAndChartAndExpandsThem() throws Exception {
    Path dcs = copySchema(ACCOUNTING_CHECKS);
    String before = Files.readString(dcs, StandardCharsets.UTF_8);

    // таблица и диаграмма пишутся пустыми самозакрытыми элементами
    DcsSettingsEdit.addStructureItem(dcs, SchemaVersion.V2_20, "Основной",
      Map.of("parentPath", List.of(), "list", "structure", "kind", "table"));
    DcsSettingsEdit.addStructureItem(dcs, SchemaVersion.V2_20, "Основной",
      Map.of("parentPath", List.of(), "list", "structure", "kind", "chart"));
    String selfClosed = Files.readString(dcs, StandardCharsets.UTF_8);
    assertThat(selfClosed).isEqualTo(before.replace("\t\t</dcsset:settings>",
      "\t\t\t<dcsset:item xsi:type=\"dcsset:StructureItemTable\"/>\r\n"
        + "\t\t\t<dcsset:item xsi:type=\"dcsset:StructureItemChart\"/>\r\n"
        + "\t\t</dcsset:settings>"));

    // первая строка раскрывает самозакрытую таблицу
    DcsSettingsEdit.addStructureItem(dcs, SchemaVersion.V2_20, "Основной",
      Map.of("parentPath", List.of(Map.of("list", "structure", "index", 3)), "list", "rows"));
    String withRow = Files.readString(dcs, StandardCharsets.UTF_8);
    assertThat(withRow).isEqualTo(selfClosed.replace(
      "\t\t\t<dcsset:item xsi:type=\"dcsset:StructureItemTable\"/>",
      "\t\t\t<dcsset:item xsi:type=\"dcsset:StructureItemTable\">\r\n"
        + autoScaffold(4, "<dcsset:row>", "</dcsset:row>")
        + "\t\t\t</dcsset:item>"));

    // колонка в таблицу со строкой встаёт перед строкой
    DcsSettingsEdit.addStructureItem(dcs, SchemaVersion.V2_20, "Основной",
      Map.of("parentPath", List.of(Map.of("list", "structure", "index", 3)), "list", "columns"));
    String withColumn = Files.readString(dcs, StandardCharsets.UTF_8);
    assertThat(withColumn).isEqualTo(insertedBefore(withRow, DcsSettingsEdit.resolveNode(
      withRow, "Основной", List.of(step("structure", 3), step("rows", 0))).element(),
      autoScaffold(4, "<dcsset:column>", "</dcsset:column>")));

    // серия раскрывает самозакрытую диаграмму, точка встаёт перед серией
    DcsSettingsEdit.addStructureItem(dcs, SchemaVersion.V2_20, "Основной",
      Map.of("parentPath", List.of(Map.of("list", "structure", "index", 4)), "list", "series"));
    DcsSettingsEdit.addStructureItem(dcs, SchemaVersion.V2_20, "Основной",
      Map.of("parentPath", List.of(Map.of("list", "structure", "index", 4)), "list", "points"));
    String withChartGroups = Files.readString(dcs, StandardCharsets.UTF_8);
    assertThat(withChartGroups).isEqualTo(withColumn.replace(
      "\t\t\t<dcsset:item xsi:type=\"dcsset:StructureItemChart\"/>",
      "\t\t\t<dcsset:item xsi:type=\"dcsset:StructureItemChart\">\r\n"
        + autoScaffold(4, "<dcsset:point>", "</dcsset:point>")
        + autoScaffold(4, "<dcsset:series>", "</dcsset:series>")
        + "\t\t\t</dcsset:item>"));

    List<Map<String, Object>> structure = structure(dcs, "Основной");
    assertThat(list(structure.get(3).get("columns"))).hasSize(1);
    assertThat(list(structure.get(3).get("rows"))).hasSize(1);
    assertThat(list(structure.get(4).get("points"))).hasSize(1);
    assertThat(list(structure.get(4).get("series"))).hasSize(1);
  }

  @Test
  void removeCutsNodeWithDescendants() throws Exception {
    Path dcs = copySchema(LOG_ANALYSIS);
    String before = Files.readString(dcs, StandardCharsets.UTF_8);

    // группировка с вложенными группами 3-го уровня вырезается целыми строками одной зоной
    DcsSettingsEdit.removeStructureItem(dcs, SchemaVersion.V2_20, USERS_VARIANT,
      Map.of("path", List.of(Map.of("list", "structure", "index", 1))));

    String after = Files.readString(dcs, StandardCharsets.UTF_8);
    assertSingleEditZone(before, after);
    DcsSettingsEdit.ResolvedNode node = DcsSettingsEdit.resolveNode(
      before, USERS_VARIANT, List.of(step("structure", 1)));
    int[] span = XmlLines.wholeLines(before, node.element().start(), node.element().end(), "item");
    assertThat(after).isEqualTo(before.substring(0, span[0]) + before.substring(span[1]));
    List<Map<String, Object>> structure = structure(dcs, USERS_VARIANT);
    assertThat(structure).hasSize(3);
    assertThat(structure).extracting(row -> row.get("kind")).containsExactly("table", "chart", "chart");
  }

  @Test
  void moveReordersWithinOwnCollection() throws Exception {
    Path dcs = copySchema(LOG_ANALYSIS);
    String before = Files.readString(dcs, StandardCharsets.UTF_8);

    // вверх: третий узел корня (диаграмма) встаёт первым, остальные сдвигаются
    DcsSettingsEdit.moveStructureItem(dcs, SchemaVersion.V2_20, USERS_VARIANT,
      Map.of("path", List.of(Map.of("list", "structure", "index", 2)), "newIndex", 0));
    String movedUp = Files.readString(dcs, StandardCharsets.UTF_8);
    assertThat(movedUp).isEqualTo(permutedStructure(before, USERS_VARIANT, 2, 0, 1, 3));

    // вниз: таблица (после переноса вверх - второй узел) уходит в конец
    DcsSettingsEdit.moveStructureItem(dcs, SchemaVersion.V2_20, USERS_VARIANT,
      Map.of("path", List.of(Map.of("list", "structure", "index", 1)), "newIndex", 3));
    String movedDown = Files.readString(dcs, StandardCharsets.UTF_8);
    assertThat(movedDown).isEqualTo(permutedStructure(movedUp, USERS_VARIANT, 0, 2, 3, 1));
    assertThat(structure(dcs, USERS_VARIANT))
      .extracting(row -> row.get("kind")).containsExactly("chart", "group", "chart", "table");

    // тот же номер: перенос не нужен, файл не меняется
    DcsSettingsEdit.moveStructureItem(dcs, SchemaVersion.V2_20, USERS_VARIANT,
      Map.of("path", List.of(Map.of("list", "structure", "index", 2)), "newIndex", 2));
    assertThat(Files.readString(dcs, StandardCharsets.UTF_8)).isEqualTo(movedDown);
  }

  @Test
  void structureRefusalsKeepFileIntact() throws Exception {
    Path dcs = copySchema(PERFORMANCE);
    String before = Files.readString(dcs, StandardCharsets.UTF_8);
    List<Map<String, Object>> tablePath = List.of(Map.of("list", "structure", "index", 1));

    // плохой путь: нет parentPath, шаг без list, номер вне списка, нецелый номер
    assertThatThrownBy(() -> DcsSettingsEdit.addStructureItem(dcs, SchemaVersion.V2_20,
      PERIOD_COLUMNS_VARIANT, Map.of("list", "structure", "kind", "group")))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("parentPath");
    assertThatThrownBy(() -> DcsSettingsEdit.addStructureItem(dcs, SchemaVersion.V2_20,
      PERIOD_COLUMNS_VARIANT, Map.of(
        "parentPath", List.of(Map.of("index", 0)), "list", "structure", "kind", "group")))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("не задан list");
    assertThatThrownBy(() -> DcsSettingsEdit.addStructureItem(dcs, SchemaVersion.V2_20,
      PERIOD_COLUMNS_VARIANT, Map.of(
        "parentPath", List.of(Map.of("list", "structure", "index", 99)), "list", "rows")))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("нет элемента с номером 99");
    assertThatThrownBy(() -> DcsSettingsEdit.addStructureItem(dcs, SchemaVersion.V2_20,
      PERIOD_COLUMNS_VARIANT, Map.of(
        "parentPath", List.of(Map.of("list", "structure", "index", 1.5)), "list", "rows")))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("целым числом");

    // плохой список: неизвестное имя и список не по виду узла
    assertThatThrownBy(() -> DcsSettingsEdit.addStructureItem(dcs, SchemaVersion.V2_20,
      PERIOD_COLUMNS_VARIANT, Map.of("parentPath", List.of(), "list", "колонки", "kind", "group")))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("Неизвестный список");
    assertThatThrownBy(() -> DcsSettingsEdit.addStructureItem(dcs, SchemaVersion.V2_20,
      PERIOD_COLUMNS_VARIANT, Map.of("parentPath", List.of(), "list", "rows")))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("не применим").hasMessageContaining("корень настроек");

    // плохой вид: не задан, неизвестен, задан при предрешённом списке, не группа в строке
    assertThatThrownBy(() -> DcsSettingsEdit.addStructureItem(dcs, SchemaVersion.V2_20,
      PERIOD_COLUMNS_VARIANT, Map.of("parentPath", List.of(), "list", "structure")))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("kind");
    assertThatThrownBy(() -> DcsSettingsEdit.addStructureItem(dcs, SchemaVersion.V2_20,
      PERIOD_COLUMNS_VARIANT, Map.of("parentPath", List.of(), "list", "structure", "kind", "nestedObject")))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("group, table или chart");
    assertThatThrownBy(() -> DcsSettingsEdit.addStructureItem(dcs, SchemaVersion.V2_20,
      PERIOD_COLUMNS_VARIANT, Map.of("parentPath", tablePath, "list", "rows", "kind", "group")))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("kind допустим только при list=structure");
    assertThatThrownBy(() -> DcsSettingsEdit.addStructureItem(dcs, SchemaVersion.V2_20,
      PERIOD_COLUMNS_VARIANT, Map.of(
        "parentPath", List.of(Map.of("list", "structure", "index", 1), Map.of("list", "rows", "index", 0)),
        "list", "structure", "kind", "table")))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("только группировки");

    // поля группировки не у группировки, позиция вне границ, неизвестное свойство
    assertThatThrownBy(() -> DcsSettingsEdit.addStructureItem(dcs, SchemaVersion.V2_20,
      PERIOD_COLUMNS_VARIANT, Map.of("parentPath", List.of(), "list", "structure", "kind", "table",
        "groupFields", List.of(Map.of("field", "Хост")))))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("применимы только к группировке");
    assertThatThrownBy(() -> DcsSettingsEdit.addStructureItem(dcs, SchemaVersion.V2_20,
      PERIOD_COLUMNS_VARIANT, Map.of(
        "parentPath", List.of(), "list", "structure", "kind", "group", "index", 99)))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("вне границ");
    assertThatThrownBy(() -> DcsSettingsEdit.addStructureItem(dcs, SchemaVersion.V2_20,
      PERIOD_COLUMNS_VARIANT, Map.of(
        "parentPath", List.of(), "list", "structure", "kind", "group", "uuid", "x")))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("Неизвестное свойство");

    // пустой путь и выход за границы у удаления и переноса
    assertThatThrownBy(() -> DcsSettingsEdit.removeStructureItem(dcs, SchemaVersion.V2_20,
      PERIOD_COLUMNS_VARIANT, Map.of("path", List.of())))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("Пустой путь");
    assertThatThrownBy(() -> DcsSettingsEdit.moveStructureItem(dcs, SchemaVersion.V2_20,
      PERIOD_COLUMNS_VARIANT, Map.of("path", List.of(), "newIndex", 0)))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("Пустой путь");
    assertThatThrownBy(() -> DcsSettingsEdit.moveStructureItem(dcs, SchemaVersion.V2_20,
      PERIOD_COLUMNS_VARIANT,
      Map.of("path", List.of(Map.of("list", "structure", "index", 0)), "newIndex", 2)))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("вне границ списка structure");
    assertThat(Files.readString(dcs, StandardCharsets.UTF_8)).isEqualTo(before);

    // путь внутрь вложенного отчёта не разрешается и для правок структуры
    Path demoFiles = copySchema(DEMO_FILES);
    String demoBefore = Files.readString(demoFiles, StandardCharsets.UTF_8);
    List<Map<String, Object>> demoStructure = list(map(variants(
      DcsSettingsRead.info(demoFiles, SchemaVersion.V2_20, "ПоТипам")).get(0).get("settings"))
      .get("structure"));
    int nested = -1;
    for (int i = 0; i < demoStructure.size(); i++) {
      if ("nestedObject".equals(demoStructure.get(i).get("kind"))) {
        nested = i;
      }
    }
    assertThat(nested).isNotNegative();
    int nestedIndex = nested;
    assertThatThrownBy(() -> DcsSettingsEdit.addStructureItem(demoFiles, SchemaVersion.V2_20, "ПоТипам",
      Map.of("parentPath", List.of(Map.of("list", "structure", "index", nestedIndex)),
        "list", "structure", "kind", "group")))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("внутрь вложенного отчёта");
    assertThatThrownBy(() -> DcsSettingsEdit.removeStructureItem(demoFiles, SchemaVersion.V2_20, "ПоТипам",
      Map.of("path", List.of(Map.of("list", "structure", "index", nestedIndex),
        Map.of("list", "structure", "index", 0)))))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("внутрь вложенного отчёта");
    assertThat(Files.readString(demoFiles, StandardCharsets.UTF_8)).isEqualTo(demoBefore);
  }

  @Test
  void applyMutationChannelRunsStructureOps() throws Exception {
    Path dcs = copySchema(ACCOUNTING_CHECKS);
    String before = Files.readString(dcs, StandardCharsets.UTF_8);

    assertThat(applyMutation(Map.of(
      "op", "cf-dcs-add-structure-item", "objectXml", dcs.toString(), "schemaVersion", "V2_20",
      "variant", "Основной",
      "payloadJson", "{\"parentPath\":[],\"list\":\"structure\",\"kind\":\"group\","
        + "\"groupFields\":[{\"field\":\"Организация\"}]}"))).isZero();
    assertThat(Files.readString(dcs, StandardCharsets.UTF_8))
      .contains("<dcsset:field>Организация</dcsset:field>");

    assertThat(applyMutation(Map.of(
      "op", "cf-dcs-move-structure-item", "objectXml", dcs.toString(), "schemaVersion", "V2_20",
      "variant", "Основной",
      "payloadJson", "{\"path\":[{\"list\":\"structure\",\"index\":3}],\"newIndex\":0}"))).isZero();
    assertThat(list(structure(dcs, "Основной").get(0).get("groupFields")))
      .extracting(row -> row.get("field")).containsExactly("Организация");

    // добавленный, передвинутый и удалённый узел возвращает файл байт в байт
    assertThat(applyMutation(Map.of(
      "op", "cf-dcs-remove-structure-item", "objectXml", dcs.toString(), "schemaVersion", "V2_20",
      "variant", "Основной",
      "payloadJson", "{\"path\":[{\"list\":\"structure\",\"index\":0}]}"))).isZero();
    assertThat(Files.readString(dcs, StandardCharsets.UTF_8)).isEqualTo(before);

    // отказ операции доходит из канала кодом 2, файл не меняется
    assertThat(applyMutation(Map.of(
      "op", "cf-dcs-move-structure-item", "objectXml", dcs.toString(), "schemaVersion", "V2_20",
      "variant", "Основной",
      "payloadJson", "{\"path\":[{\"list\":\"structure\",\"index\":0}],\"newIndex\":9}"))).isEqualTo(2);
    assertThat(applyMutation(Map.of(
      "op", "cf-dcs-add-structure-item", "objectXml", dcs.toString(), "schemaVersion", "V2_20",
      "variant", "Основной",
      "payloadJson", "{\"parentPath\":[],\"list\":\"rows\"}"))).isEqualTo(2);
    assertThat(Files.readString(dcs, StandardCharsets.UTF_8)).isEqualTo(before);
  }

  @Test
  void applyMutationChannelEditsEdtTemplateDcs() throws Exception {
    // В проекте 1С:EDT схема компоновки - тот же формат файла с другой физикой: Template.dcs
    // без BOM вместо Ext/Template.xml с BOM. Мутации вариантов настроек идут по .dcs общим
    // кодом правки схемы - закрепляем канал и сохранение физики на фикстуре ssl31-edt.
    Path source = Path.of(System.getProperty("fixtures.ssl31edt.root"), "ssl31", "src",
      "Reports", "АнализЖурналаРегистрации", "Templates", "ОсновнаяСхемаКомпоновкиДанных", "Template.dcs");
    Path dcs = Files.createTempDirectory(tempDir, "dcs").resolve("Template.dcs");
    Files.copy(source, dcs, StandardCopyOption.REPLACE_EXISTING);
    String before = Files.readString(dcs, StandardCharsets.UTF_8);
    assertThat(before).startsWith("<?xml");

    // смена направления сортировки корня настроек через канал
    List<Map<String, Object>> orderItems = list(map(map(
      variants(info(dcs, USERS_VARIANT)).get(0).get("settings")).get("order")).get("items"));
    assertThat(orderItems.get(0)).containsEntry("orderType", "Desc");
    orderItems.get(0).put("orderType", "Asc");
    assertThat(applyMutation(Map.of(
      "op", "cf-dcs-set-order", "objectXml", dcs.toString(), "schemaVersion", "V2_20",
      "variant", USERS_VARIANT,
      "payloadJson", new Gson().toJson(Map.of("path", List.of(), "items", orderItems))))).isZero();

    // одна зона правки: вне заменённых строк сортировки файл совпадает байт в байт
    String after = Files.readString(dcs, StandardCharsets.UTF_8);
    assertThat(after).isEqualTo(replaceFirst(before,
      "\t\t\t\t\t<dcsset:field>Дата</dcsset:field>\r\n"
        + "\t\t\t\t\t<dcsset:orderType>Desc</dcsset:orderType>\r\n",
      "\t\t\t\t\t<dcsset:field>Дата</dcsset:field>\r\n"
        + "\t\t\t\t\t<dcsset:orderType>Asc</dcsset:orderType>\r\n"));
    // перечитка моделью: направление сменилось
    assertThat(list(map(map(variants(info(dcs, USERS_VARIANT)).get(0).get("settings"))
      .get("order")).get("items")).get(0)).containsEntry("orderType", "Asc");
    // физика файла EDT сохранена: без BOM, CRLF, конец без перевода строки
    assertThat(after).startsWith("<?xml").doesNotContain("﻿");
    assertThat(after).contains("\r\n").endsWith("</DataCompositionSchema>");
  }

  // ------------------------------------------------------------------ помощники

  /** Шаг пути резолвера. */
  private static DcsSettingsEdit.Step step(String list, int index) {
    return new DcsSettingsEdit.Step(list, index);
  }

  /** Блок скаффолда группировки-«детальных записей»: порядок и выбор auto, как пишет конфигуратор. */
  private static String autoScaffold(int depth, String openTag, String closeTag) {
    String tabs = "\t".repeat(depth);
    return tabs + openTag + "\r\n"
      + tabs + "\t<dcsset:order>\r\n"
      + tabs + "\t\t<dcsset:item xsi:type=\"dcsset:OrderItemAuto\"/>\r\n"
      + tabs + "\t</dcsset:order>\r\n"
      + tabs + "\t<dcsset:selection>\r\n"
      + tabs + "\t\t<dcsset:item xsi:type=\"dcsset:SelectedItemAuto\"/>\r\n"
      + tabs + "\t</dcsset:selection>\r\n"
      + tabs + closeTag + "\r\n";
  }

  /** Ожидание вставки: блок строк встаёт перед строкой, с которой начинается узел. */
  private static String insertedBefore(String text, XmlLines.Node node, String block) {
    int lineStart = text.lastIndexOf('\n', node.start() - 1) + 1;
    return text.substring(0, lineStart) + block + text.substring(lineStart);
  }

  /** Ожидание вставки в конец узла: блок строк встаёт перед строкой его закрывающего тега. */
  private static String insertedAtClose(String text, XmlLines.Node node, String block) {
    int close = text.lastIndexOf("</", node.end() - 1);
    int lineStart = text.lastIndexOf('\n', close) + 1;
    return text.substring(0, lineStart) + block + text.substring(lineStart);
  }

  /**
   * Ожидание переноса: блоки целых строк корневых узлов структуры переставляются в заданном
   * порядке, остальной текст не меняется. Узлы идут в тексте подряд - проверяется и это.
   */
  private static String permutedStructure(String text, String variant, int... order) {
    List<int[]> spans = new ArrayList<>();
    for (int i = 0; i < order.length; i++) {
      DcsSettingsEdit.ResolvedNode node = DcsSettingsEdit.resolveNode(
        text, variant, List.of(step("structure", i)));
      int[] span = XmlLines.wholeLines(text, node.element().start(), node.element().end(), "item");
      if (!spans.isEmpty()) {
        assertThat(span[0]).isEqualTo(spans.get(spans.size() - 1)[1]);
      }
      spans.add(span);
    }
    StringBuilder out = new StringBuilder(text.substring(0, spans.get(0)[0]));
    for (int i : order) {
      out.append(text, spans.get(i)[0], spans.get(i)[1]);
    }
    return out.append(text.substring(spans.get(spans.size() - 1)[1])).toString();
  }

  /** Узлы структуры варианта из cf-dcs-settings-info. */
  private List<Map<String, Object>> structure(Path dcs, String variant) throws Exception {
    return list(map(variants(info(dcs, variant)).get(0).get("settings")).get("structure"));
  }

  /** Узел по шагам пути. */
  private static DcsSettingsEdit.ResolvedNode resolve(
    String text, String variant, DcsSettingsEdit.Step... path) {
    return DcsSettingsEdit.resolveNode(text, variant, List.of(path));
  }

  /** Варианты настроек схемы: без имени - все, с именем - один с настройками. */
  private static Map<String, Object> info(Path dcs, String variant) throws Exception {
    return DcsSettingsRead.info(dcs, SchemaVersion.V2_20, variant);
  }

  @SuppressWarnings("unchecked")
  private static List<Map<String, Object>> variants(Map<String, Object> info) {
    return (List<Map<String, Object>>) info.get("variants");
  }

  @SuppressWarnings("unchecked")
  private static List<Map<String, Object>> list(Object value) {
    assertThat(value).isInstanceOf(List.class);
    return (List<Map<String, Object>>) value;
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> map(Object value) {
    assertThat(value).isInstanceOf(Map.class);
    return (Map<String, Object>) value;
  }

  /** Запуск канала apply-mutation с параметрами из UTF-8 JSON-файла. */
  private int applyMutation(Map<String, ?> params) throws Exception {
    Path paramsFile = Files.createTempFile(tempDir, "params", ".json");
    Files.writeString(paramsFile, new Gson().toJson(params), StandardCharsets.UTF_8);
    return new CommandLine(new DesignerXmlCli()).execute("apply-mutation", "--params", paramsFile.toString());
  }

  /** Копия схемы из submodule во временный каталог: индекс фикстур не трогается. */
  private Path copySchema(String relativePath) throws Exception {
    Path source = Ssl31SubmodulePaths.projectRoot().resolve(relativePath);
    Path copy = Files.createTempDirectory(tempDir, "dcs").resolve("Template.xml");
    Files.copy(source, copy, StandardCopyOption.REPLACE_EXISTING);
    return copy;
  }

  /** Замена только первого вхождения: ожидание точечной правки при повторах блока в файле. */
  private static String replaceFirst(String text, String target, String replacement) {
    int at = text.indexOf(target);
    assertThat(at).isNotNegative();
    return text.substring(0, at) + replacement + text.substring(at + target.length());
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
}
