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
import java.util.List;
import java.util.Map;

/**
 * Set-операции списков секций вариантов настроек (выбранные поля, сортировка, поля
 * группировки) и движок «сплайса только изменённого». Главный инвариант: запрос с теми же
 * элементами, что отдало чтение, не меняет файл байт в байт - на всех вариантах и узлах
 * АнализаЖурналаРегистрации для всех трёх операций. Правка одного элемента из многих трогает
 * одну зону, соседи и хвост секции с userSettingID нетронуты; перестановка существующих -
 * их исходные байты в новом порядке; добавленные пишутся по образцам платформы (заголовок -
 * lwsTitle языком конфигурации, форма title возвращается по titleKind=string, у новой папки
 * placement Auto, направление сортировки по умолчанию Asc). Отсутствующая секция создаётся
 * на месте по xs:sequence вида узла - у корня настроек и у группировки места разные; пустой
 * items оставляет секцию только при её хвосте, иначе снимает целиком. Элемент с opaque-
 * значением переносится байтами, а его правка - отказ всей мутации. Канал apply-mutation
 * выполняет все три операции, отказ доходит кодом 2 без изменения файла.
 */
class DcsSettingsSpliceTest {

  /** Пять вариантов, таблицы, диаграммы и группы в три уровня — главная байтовая проверка. */
  private static final String LOG_ANALYSIS =
    "src/cf/Reports/АнализЖурналаРегистрации/Templates/ОсновнаяСхемаКомпоновкиДанных/Ext/Template.xml";

  /** Выбор с хвостом userSettingID, сортировка только из хвоста — соседи правки. */
  private static final String DEMO_FILES =
    "src/cf/Reports/_ДемоФайлы/Templates/ОсновнаяСхемаКомпоновкиДанных/Ext/Template.xml";

  /** Компактная схема: выбор без хвоста, группы с хвостами, корень без сортировки. */
  private static final String DYNAMICS =
    "src/cf/Reports/_ДемоДинамикаИзмененийФайлов/Templates/ОсновнаяСхемаКомпоновкиДанных/Ext/Template.xml";

  /** Простая форма заголовка title (не lwsTitle) у поля выбора. */
  private static final String BUSINESS_PROCESSES =
    "src/cf/Reports/БизнесПроцессы/Templates/Макет/Ext/Template.xml";

  /** Единственный вариант с самозакрытым settings. */
  private static final String EMPTY_SCHEMA =
    "src/cf/Reports/_ДемоФайлыВспомогательный/Templates/ПустаяСхемаКомпоновкиДанных/Ext/Template.xml";

  /** Путь точки первой диаграммы варианта «Основной» _ДемоДинамики: группировка со всеми секциями. */
  private static final List<Map<String, Object>> DYNAMICS_POINT = List.of(
    Map.of("list", "structure", "index", 1), Map.of("list", "points", "index", 0));

  @TempDir Path tempDir;

  // ------------------------------------------------------------------ главный тест движка

  @Test
  void setWithReadItemsKeepsLogAnalysisByteForByte() throws Exception {
    Path dcs = copySchema(LOG_ANALYSIS);
    String before = Files.readString(dcs, StandardCharsets.UTF_8);

    // каждый вариант, каждый узел (корень, группы, таблицы со строками, диаграммы с точками):
    // set с теми же прочитанными items не меняет файл байт в байт
    int ops = 0;
    for (Map<String, Object> row : variants(info(dcs, null))) {
      String variant = (String) row.get("name");
      Map<String, Object> settings = map(variants(info(dcs, variant)).get(0).get("settings"));
      ops += echoNode(dcs, variant, before, settings, "settings", List.of());
    }
    assertThat(ops).isGreaterThan(40);
  }

  /** Эхо всех секций узла и спуск в его детей; на каждом шаге файл сверяется байт в байт. */
  private int echoNode(Path dcs, String variant, String before, Map<String, Object> node,
    String kind, List<Map<String, Object>> path) throws Exception {
    int ops = 0;
    if (!"nestedObject".equals(kind)) {
      ops += echoSection(dcs, variant, before, path, node, "selection");
    }
    boolean group = "group".equals(kind);
    if ("settings".equals(kind) || group) {
      ops += echoSection(dcs, variant, before, path, node, "order");
    }
    if (group) {
      Object fields = node.get("groupFields");
      DcsSettingsEdit.setGroupFields(dcs, SchemaVersion.V2_20, variant,
        Map.of("path", path, "items", fields == null ? List.of() : fields));
      assertThat(Files.readString(dcs, StandardCharsets.UTF_8)).isEqualTo(before);
      ops++;
    }
    for (String list : List.of("structure", "rows", "columns", "points", "series")) {
      if (!(node.get(list) instanceof List<?> children)) {
        continue;
      }
      for (int i = 0; i < children.size(); i++) {
        Map<String, Object> child = map(children.get(i));
        String childKind = (String) child.get("kind");
        if ("nestedObject".equals(childKind)) {
          continue;
        }
        List<Map<String, Object>> childPath = new ArrayList<>(path);
        childPath.add(Map.of("list", list, "index", i));
        ops += echoNode(dcs, variant, before, child, childKind, childPath);
      }
    }
    return ops;
  }

  /** Эхо одной секции узла: items из прочитанного DTO (нет секции - пустой список). */
  private int echoSection(Path dcs, String variant, String before, List<Map<String, Object>> path,
    Map<String, Object> node, String key) throws Exception {
    Object section = node.get(key);
    Object items = section == null ? null : map(section).get("items");
    Map<String, Object> payload = Map.of("path", path, "items", items == null ? List.of() : items);
    if ("selection".equals(key)) {
      DcsSettingsEdit.setSelection(dcs, SchemaVersion.V2_20, variant, payload);
    } else {
      DcsSettingsEdit.setOrder(dcs, SchemaVersion.V2_20, variant, payload);
    }
    assertThat(Files.readString(dcs, StandardCharsets.UTF_8)).isEqualTo(before);
    return 1;
  }

  // ------------------------------------------------------------------ сплайс и соседи

  @Test
  void changeOneItemKeepsNeighboursAndSectionTail() throws Exception {
    Path dcs = copySchema(DEMO_FILES);
    String before = Files.readString(dcs, StandardCharsets.UTF_8);

    // у корневого выбора «Основной» 15 полей и хвост viewMode+userSettingID; меняется одно поле
    List<Map<String, Object>> items = sectionItems(settings(dcs, "Основной"), "selection");
    assertThat(items).hasSize(15);
    int target = -1;
    for (int i = 0; i < items.size(); i++) {
      if ("ДатаЗаема".equals(items.get(i).get("field"))) {
        target = i;
      }
    }
    assertThat(items.get(target)).containsEntry("use", false).doesNotContainKey("title");
    items.get(target).put("title", "Дата займа");
    DcsSettingsEdit.setSelection(dcs, SchemaVersion.V2_20, "Основной",
      Map.of("path", List.of(), "items", items));

    String after = Files.readString(dcs, StandardCharsets.UTF_8);
    assertSingleEditZone(before, after);
    // перезаписан только изменённый элемент: соседи и хвост с userSettingID нетронуты байтово
    assertThat(after).isEqualTo(before.replace(
      "\t\t\t\t<dcsset:item xsi:type=\"dcsset:SelectedItemField\">\r\n"
        + "\t\t\t\t\t<dcsset:use>false</dcsset:use>\r\n"
        + "\t\t\t\t\t<dcsset:field>ДатаЗаема</dcsset:field>\r\n"
        + "\t\t\t\t</dcsset:item>\r\n",
      "\t\t\t\t<dcsset:item xsi:type=\"dcsset:SelectedItemField\">\r\n"
        + "\t\t\t\t\t<dcsset:use>false</dcsset:use>\r\n"
        + "\t\t\t\t\t<dcsset:field>ДатаЗаема</dcsset:field>\r\n"
        + "\t\t\t\t\t<dcsset:lwsTitle>\r\n"
        + "\t\t\t\t\t\t<v8:item>\r\n"
        + "\t\t\t\t\t\t\t<v8:lang>ru</v8:lang>\r\n"
        + "\t\t\t\t\t\t\t<v8:content>Дата займа</v8:content>\r\n"
        + "\t\t\t\t\t\t</v8:item>\r\n"
        + "\t\t\t\t\t</dcsset:lwsTitle>\r\n"
        + "\t\t\t\t</dcsset:item>\r\n"));
    assertThat(after).contains("<dcsset:userSettingID>e336b1b1-7c49-4a66-8dfc-39d9caf115c1");

    // эхо изменённого состояния снова не трогает файл
    DcsSettingsEdit.setSelection(dcs, SchemaVersion.V2_20, "Основной",
      Map.of("path", List.of(), "items", sectionItems(settings(dcs, "Основной"), "selection")));
    assertThat(Files.readString(dcs, StandardCharsets.UTF_8)).isEqualTo(after);
  }

  @Test
  void reordersRemovesAndAddsItemsAsByteBlocks() throws Exception {
    Path dcs = copySchema(DYNAMICS);
    String before = Files.readString(dcs, StandardCharsets.UTF_8);
    Map<String, Object> period = Map.of("kind", "field", "field", "Период");
    Map<String, Object> size = Map.of("kind", "field", "field", "Размер");
    Map<String, Object> count = Map.of("kind", "field", "field", "Количество");

    // перестановка существующих - их исходные байты в новом порядке
    DcsSettingsEdit.setSelection(dcs, SchemaVersion.V2_20, "Основной",
      Map.of("path", List.of(), "items", List.of(size, period, count)));
    String reordered = Files.readString(dcs, StandardCharsets.UTF_8);
    assertThat(reordered).isEqualTo(
      withRootSelectionItems(before, selectedField("Размер"), selectedField("Период"), selectedField("Количество")));

    // удаление: снятый элемент вырезается, прочие не переписываются
    DcsSettingsEdit.setSelection(dcs, SchemaVersion.V2_20, "Основной",
      Map.of("path", List.of(), "items", List.of(size, count)));
    String removed = Files.readString(dcs, StandardCharsets.UTF_8);
    assertThat(removed).isEqualTo(
      withRootSelectionItems(before, selectedField("Размер"), selectedField("Количество")));

    // добавление в начало: новый элемент пишется, существующие остаются байтами
    DcsSettingsEdit.setSelection(dcs, SchemaVersion.V2_20, "Основной",
      Map.of("path", List.of(), "items", List.of(Map.of("kind", "auto"), size, count)));
    String added = Files.readString(dcs, StandardCharsets.UTF_8);
    assertThat(added).isEqualTo(withRootSelectionItems(before,
      "\t\t\t\t<dcsset:item xsi:type=\"dcsset:SelectedItemAuto\"/>\r\n",
      selectedField("Размер"), selectedField("Количество")));

    // пустой items у секции без хвоста снимает её целиком
    DcsSettingsEdit.setSelection(dcs, SchemaVersion.V2_20, "Основной",
      Map.of("path", List.of(), "items", List.of()));
    String dropped = Files.readString(dcs, StandardCharsets.UTF_8);
    assertThat(dropped).isEqualTo(added.replace("\t\t\t<dcsset:selection>\r\n"
      + "\t\t\t\t<dcsset:item xsi:type=\"dcsset:SelectedItemAuto\"/>\r\n"
      + selectedField("Размер") + selectedField("Количество")
      + "\t\t\t</dcsset:selection>\r\n", ""));
    assertThat(settings(dcs, "Основной")).doesNotContainKey("selection");
  }

  // ------------------------------------------------------------------ создание секции

  @Test
  void createsMissingSectionBySequenceOfNodeKind() throws Exception {
    Path dcs = copySchema(DYNAMICS);
    String before = Files.readString(dcs, StandardCharsets.UTF_8);

    // у корня «Основной» нет сортировки: по xs:sequence Settings она встаёт ПОСЛЕ
    // dataParameters (и после selection) - перед outputParameters
    DcsSettingsEdit.setOrder(dcs, SchemaVersion.V2_20, "Основной", Map.of("path", List.of(),
      "items", List.of(Map.of("kind", "field", "field", "Период", "orderType", "Desc"))));
    String withOrder = Files.readString(dcs, StandardCharsets.UTF_8);
    assertThat(withOrder).isEqualTo(insertAfterFirst(before, "\t\t\t</dcsset:dataParameters>\r\n",
      "\t\t\t<dcsset:order>\r\n"
        + "\t\t\t\t<dcsset:item xsi:type=\"dcsset:OrderItemField\">\r\n"
        + "\t\t\t\t\t<dcsset:field>Период</dcsset:field>\r\n"
        + "\t\t\t\t\t<dcsset:orderType>Desc</dcsset:orderType>\r\n"
        + "\t\t\t\t</dcsset:item>\r\n"
        + "\t\t\t</dcsset:order>\r\n"));

    // снятие созданной секции возвращает файл байт в байт
    DcsSettingsEdit.setOrder(dcs, SchemaVersion.V2_20, "Основной",
      Map.of("path", List.of(), "items", List.of()));
    assertThat(Files.readString(dcs, StandardCharsets.UTF_8)).isEqualTo(before);

    // у группировки (точка диаграммы) место другое: сортировка - ПЕРЕД selection;
    // возврат к исходным байтам после снятия и создания заново доказывает позицию
    DcsSettingsEdit.setOrder(dcs, SchemaVersion.V2_20, "Основной",
      Map.of("path", DYNAMICS_POINT, "items", List.of()));
    String pointNoOrder = Files.readString(dcs, StandardCharsets.UTF_8);
    assertSingleEditZone(before, pointNoOrder);
    assertThat(nodeAt(settings(dcs, "Основной"), DYNAMICS_POINT)).doesNotContainKey("order");
    DcsSettingsEdit.setOrder(dcs, SchemaVersion.V2_20, "Основной",
      Map.of("path", DYNAMICS_POINT, "items", List.of(Map.of("kind", "auto"))));
    assertThat(Files.readString(dcs, StandardCharsets.UTF_8)).isEqualTo(before);

    // поля группировки встают первым ребёнком группировки - до сортировки
    List<Map<String, Object>> fields =
      list(nodeAt(settings(dcs, "Основной"), DYNAMICS_POINT).get("groupFields"));
    assertThat(fields).hasSize(1);
    DcsSettingsEdit.setGroupFields(dcs, SchemaVersion.V2_20, "Основной",
      Map.of("path", DYNAMICS_POINT, "items", List.of()));
    assertThat(nodeAt(settings(dcs, "Основной"), DYNAMICS_POINT)).doesNotContainKey("groupFields");
    DcsSettingsEdit.setGroupFields(dcs, SchemaVersion.V2_20, "Основной",
      Map.of("path", DYNAMICS_POINT, "items", fields));
    assertThat(Files.readString(dcs, StandardCharsets.UTF_8)).isEqualTo(before);

    // самозакрытый settings раскрывается создаваемой секцией
    Path empty = copySchema(EMPTY_SCHEMA);
    String emptyBefore = Files.readString(empty, StandardCharsets.UTF_8);
    DcsSettingsEdit.setSelection(empty, SchemaVersion.V2_20, "Основной",
      Map.of("path", List.of(), "items", List.of(Map.of("kind", "auto"))));
    String expanded = Files.readString(empty, StandardCharsets.UTF_8);
    assertThat(expanded).isEqualTo(emptyBefore.replace("windows\"/>", "windows\">\r\n"
      + "\t\t\t<dcsset:selection>\r\n"
      + "\t\t\t\t<dcsset:item xsi:type=\"dcsset:SelectedItemAuto\"/>\r\n"
      + "\t\t\t</dcsset:selection>\r\n"
      + "\t\t</dcsset:settings>"));
    // снятие последней секции сворачивает settings обратно в самозакрытый тег: файл байт в байт как до вставки
    DcsSettingsEdit.setSelection(empty, SchemaVersion.V2_20, "Основной",
      Map.of("path", List.of(), "items", List.of()));
    assertThat(Files.readString(empty, StandardCharsets.UTF_8)).isEqualTo(emptyBefore);
  }

  @Test
  void emptyItemsKeepSectionOnlyWithTail() throws Exception {
    // у «Основной» _ДемоФайлов сортировка корня состоит из одного хвоста: пустой items её не трогает
    Path demo = copySchema(DEMO_FILES);
    String demoBefore = Files.readString(demo, StandardCharsets.UTF_8);
    DcsSettingsEdit.setOrder(demo, SchemaVersion.V2_20, "Основной",
      Map.of("path", List.of(), "items", List.of()));
    assertThat(Files.readString(demo, StandardCharsets.UTF_8)).isEqualTo(demoBefore);

    // элемент встаёт перед хвостом; повторный пустой items снимает его и возвращает байты
    DcsSettingsEdit.setOrder(demo, SchemaVersion.V2_20, "Основной", Map.of("path", List.of(),
      "items", List.of(Map.of("kind", "field", "field", "ДатаСоздания", "orderType", "Asc"))));
    String withItem = Files.readString(demo, StandardCharsets.UTF_8);
    assertThat(withItem).isEqualTo(insertAfterFirst(demoBefore, "\t\t\t<dcsset:order>\r\n",
      "\t\t\t\t<dcsset:item xsi:type=\"dcsset:OrderItemField\">\r\n"
        + "\t\t\t\t\t<dcsset:field>ДатаСоздания</dcsset:field>\r\n"
        + "\t\t\t\t\t<dcsset:orderType>Asc</dcsset:orderType>\r\n"
        + "\t\t\t\t</dcsset:item>\r\n"));
    DcsSettingsEdit.setOrder(demo, SchemaVersion.V2_20, "Основной",
      Map.of("path", List.of(), "items", List.of()));
    assertThat(Files.readString(demo, StandardCharsets.UTF_8)).isEqualTo(demoBefore);

    // у группировки _ДемоДинамики сортировка - элемент auto плюс хвост viewMode: пустой items
    // вырезает только элемент, хвост остаётся; авто-элемент возвращает исходные байты
    Path dynamics = copySchema(DYNAMICS);
    String dynamicsBefore = Files.readString(dynamics, StandardCharsets.UTF_8);
    List<Map<String, Object>> groupPath = List.of(Map.of("list", "structure", "index", 0));
    DcsSettingsEdit.setOrder(dynamics, SchemaVersion.V2_20, "Основной",
      Map.of("path", groupPath, "items", List.of()));
    String withoutAuto = Files.readString(dynamics, StandardCharsets.UTF_8);
    assertSingleEditZone(dynamicsBefore, withoutAuto);
    Map<String, Object> order = map(nodeAt(settings(dynamics, "Основной"), groupPath).get("order"));
    assertThat(list(order.get("items"))).isEmpty();
    assertThat(order).containsEntry("viewMode", "Normal");
    DcsSettingsEdit.setOrder(dynamics, SchemaVersion.V2_20, "Основной",
      Map.of("path", groupPath, "items", List.of(Map.of("kind", "auto"))));
    assertThat(Files.readString(dynamics, StandardCharsets.UTF_8)).isEqualTo(dynamicsBefore);
  }

  // ------------------------------------------------------------------ заголовки и папки

  @Test
  void titleFormsArePreservedAndDefaultToLws() throws Exception {
    Path dcs = copySchema(BUSINESS_PROCESSES);
    String before = Files.readString(dcs, StandardCharsets.UTF_8);
    String variant = "СписокБизнесПроцессов";

    // эхо прочитанного сохраняет простую форму title байт в байт
    List<Map<String, Object>> items = sectionItems(settings(dcs, variant), "selection");
    assertThat(items.get(0)).containsEntry("title", "№").containsEntry("titleKind", "string");
    DcsSettingsEdit.setSelection(dcs, SchemaVersion.V2_20, variant,
      Map.of("path", List.of(), "items", items));
    assertThat(Files.readString(dcs, StandardCharsets.UTF_8)).isEqualTo(before);

    // правка заголовка с titleKind=string не ломает исходную форму элемента
    items.get(0).put("title", "Номер");
    DcsSettingsEdit.setSelection(dcs, SchemaVersion.V2_20, variant,
      Map.of("path", List.of(), "items", items));
    String renamed = Files.readString(dcs, StandardCharsets.UTF_8);
    assertThat(renamed).isEqualTo(before.replace(
      "<dcsset:title>№</dcsset:title>", "<dcsset:title>Номер</dcsset:title>"));

    // новый заголовок без titleKind пишется lwsTitle языком конфигурации, новая папка
    // получает placement Auto, её вложенные поля пишутся рекурсивно
    items.get(1).put("title", "Процесс");
    items.add(Map.of("kind", "folder", "title", "Сроки",
      "items", List.of(Map.of("kind", "field", "field", "ДатаСрока"))));
    DcsSettingsEdit.setSelection(dcs, SchemaVersion.V2_20, variant,
      Map.of("path", List.of(), "items", items));
    String extended = Files.readString(dcs, StandardCharsets.UTF_8);
    assertThat(extended).isEqualTo(replaceFirst(renamed
        .replace("\t\t\t\t<dcsset:item xsi:type=\"dcsset:SelectedItemField\">\r\n"
            + "\t\t\t\t\t<dcsset:field>БизнесПроцесс</dcsset:field>\r\n"
            + "\t\t\t\t</dcsset:item>\r\n",
          "\t\t\t\t<dcsset:item xsi:type=\"dcsset:SelectedItemField\">\r\n"
            + "\t\t\t\t\t<dcsset:field>БизнесПроцесс</dcsset:field>\r\n"
            + lwsTitle(5, "Процесс")
            + "\t\t\t\t</dcsset:item>\r\n"),
      "\t\t\t</dcsset:selection>\r\n",
      "\t\t\t\t<dcsset:item xsi:type=\"dcsset:SelectedItemFolder\">\r\n"
        + lwsTitle(5, "Сроки")
        + "\t\t\t\t\t<dcsset:item xsi:type=\"dcsset:SelectedItemField\">\r\n"
        + "\t\t\t\t\t\t<dcsset:field>ДатаСрока</dcsset:field>\r\n"
        + "\t\t\t\t\t</dcsset:item>\r\n"
        + "\t\t\t\t\t<dcsset:placement>Auto</dcsset:placement>\r\n"
        + "\t\t\t\t</dcsset:item>\r\n"
        + "\t\t\t</dcsset:selection>\r\n"));

    // эхо прочитанного после правок снова не меняет файл: DTO и писатель согласованы
    DcsSettingsEdit.setSelection(dcs, SchemaVersion.V2_20, variant,
      Map.of("path", List.of(), "items", sectionItems(settings(dcs, variant), "selection")));
    assertThat(Files.readString(dcs, StandardCharsets.UTF_8)).isEqualTo(extended);
  }

  // ------------------------------------------------------------------ opaque-значения

  @Test
  void opaqueBoundIsCarriedUnchangedAndRefusedOnChange() throws Exception {
    // граница периода с типом вне белого списка записи: в ssl31 таких нет, кейс готовится правкой копии
    Path dcs = copySchema(DYNAMICS);
    String original = Files.readString(dcs, StandardCharsets.UTF_8);
    Files.writeString(dcs, replaceFirst(original,
      "<dcsset:periodAdditionBegin xsi:type=\"xs:dateTime\">0001-01-01T00:00:00</dcsset:periodAdditionBegin>",
      "<dcsset:periodAdditionBegin xsi:type=\"v8ui:ChartLabelType\">Value</dcsset:periodAdditionBegin>"),
      StandardCharsets.UTF_8);
    String before = Files.readString(dcs, StandardCharsets.UTF_8);
    List<Map<String, Object>> groupPath = List.of(Map.of("list", "structure", "index", 0));
    List<Map<String, Object>> fields =
      sectionItemsOf(nodeAt(settings(dcs, "Основной"), groupPath), "groupFields");
    assertThat(map(fields.get(0).get("periodAdditionBegin")))
      .containsEntry("type", "v8ui:ChartLabelType")
      .containsEntry("text", "Value")
      .containsEntry("opaque", true);

    // неизменённый элемент с opaque-значением переносится исходными байтами
    DcsSettingsEdit.setGroupFields(dcs, SchemaVersion.V2_20, "Основной",
      Map.of("path", groupPath, "items", fields));
    assertThat(Files.readString(dcs, StandardCharsets.UTF_8)).isEqualTo(before);

    // правка элемента с opaque-значением - отказ всей мутации, файл не тронут
    fields.get(0).put("groupType", "Hierarchy");
    assertThatThrownBy(() -> DcsSettingsEdit.setGroupFields(dcs, SchemaVersion.V2_20, "Основной",
      Map.of("path", groupPath, "items", fields)))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("только для чтения");
    assertThat(Files.readString(dcs, StandardCharsets.UTF_8)).isEqualTo(before);

    // новый элемент с нечитаемым типом границы - тот же отказ
    assertThatThrownBy(() -> DcsSettingsEdit.setGroupFields(dcs, SchemaVersion.V2_20, "Основной",
      Map.of("path", groupPath, "items", List.of(Map.of(
        "field", "Период", "periodAdditionBegin", Map.of("type", "v8ui:Font"))))))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("только для чтения");
    assertThat(Files.readString(dcs, StandardCharsets.UTF_8)).isEqualTo(before);
  }

  @Test
  void writesFieldAndAdditionLexemePeriodBounds() throws Exception {
    // расширенные формы границ периода: поле и лексема дополнения пишутся и читаются обратно
    Path dcs = copySchema(DYNAMICS);
    DcsSettingsEdit.setGroupFields(dcs, SchemaVersion.V2_20, "Основной", Map.of(
      "path", DYNAMICS_POINT, "items", List.of(Map.of(
        "field", "Период", "groupType", "Items", "periodAdditionType", "Month",
        "periodAdditionBegin", Map.of("type", "dcscor:Field", "text", "ДатаНачала"),
        "periodAdditionEnd", Map.of("type", "dcscor:DataCompositionPeriodAdditionType", "text", "Day")))));
    String bounds = Files.readString(dcs, StandardCharsets.UTF_8);
    assertThat(bounds)
      .contains("<dcsset:periodAdditionBegin xsi:type=\"dcscor:Field\">ДатаНачала</dcsset:periodAdditionBegin>")
      .contains("<dcsset:periodAdditionEnd xsi:type=\"dcscor:DataCompositionPeriodAdditionType\">Day"
        + "</dcsset:periodAdditionEnd>");

    // эхо прочитанного после правки снова не меняет файл
    DcsSettingsEdit.setGroupFields(dcs, SchemaVersion.V2_20, "Основной", Map.of("path", DYNAMICS_POINT,
      "items", nodeAt(settings(dcs, "Основной"), DYNAMICS_POINT).get("groupFields")));
    assertThat(Files.readString(dcs, StandardCharsets.UTF_8)).isEqualTo(bounds);
  }

  @Test
  void writesPeriodBoundsFromDecodedAsWebviewSends() throws Exception {
    // редактор границ webview шлёт у новых значений только decoded - без text
    Path dcs = copySchema(DYNAMICS);
    DcsSettingsEdit.setGroupFields(dcs, SchemaVersion.V2_20, "Основной", Map.of(
      "path", DYNAMICS_POINT, "items", List.of(Map.of(
        "field", "Период", "groupType", "Items", "periodAdditionType", "Month",
        "periodAdditionBegin", Map.of("type", "xs:dateTime", "decoded", "2020-02-02T00:00:00"),
        "periodAdditionEnd", Map.of("type", "dcscor:Field", "decoded", "ДатаОкончания")))));
    assertThat(Files.readString(dcs, StandardCharsets.UTF_8))
      .contains("<dcsset:periodAdditionBegin xsi:type=\"xs:dateTime\">2020-02-02T00:00:00"
        + "</dcsset:periodAdditionBegin>")
      .contains("<dcsset:periodAdditionEnd xsi:type=\"dcscor:Field\">ДатаОкончания"
        + "</dcsset:periodAdditionEnd>");
  }

  // ------------------------------------------------------------------ отказы

  @Test
  void refusalsKeepFileIntact() throws Exception {
    Path dcs = copySchema(DYNAMICS);
    String before = Files.readString(dcs, StandardCharsets.UTF_8);
    List<Map<String, Object>> root = List.of();

    // секция не по виду узла: полей группировки нет у корня, сортировки нет у таблицы
    assertThatThrownBy(() -> DcsSettingsEdit.setGroupFields(dcs, SchemaVersion.V2_20, "Основной",
      Map.of("path", root, "items", List.of())))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("нет секции полей группировки");
    Path logAnalysis = copySchema(LOG_ANALYSIS);
    String logBefore = Files.readString(logAnalysis, StandardCharsets.UTF_8);
    assertThatThrownBy(() -> DcsSettingsEdit.setOrder(logAnalysis, SchemaVersion.V2_20,
      "АнализАктивностиПользователей",
      Map.of("path", List.of(Map.of("list", "structure", "index", 0)), "items", List.of())))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("нет секции сортировки").hasMessageContaining("таблица");
    assertThat(Files.readString(logAnalysis, StandardCharsets.UTF_8)).isEqualTo(logBefore);

    // вложенный отчёт не редактируется и выбранными полями
    Path demoFiles = copySchema(DEMO_FILES);
    String demoBefore = Files.readString(demoFiles, StandardCharsets.UTF_8);
    List<Map<String, Object>> structure = list(settings(demoFiles, "ПоТипам").get("structure"));
    int nested = -1;
    for (int i = 0; i < structure.size(); i++) {
      if ("nestedObject".equals(structure.get(i).get("kind"))) {
        nested = i;
      }
    }
    assertThat(nested).isNotNegative();
    int nestedIndex = nested;
    assertThatThrownBy(() -> DcsSettingsEdit.setSelection(demoFiles, SchemaVersion.V2_20, "ПоТипам",
      Map.of("path", List.of(Map.of("list", "structure", "index", nestedIndex)), "items", List.of())))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("вложенный отчёт");
    assertThat(Files.readString(demoFiles, StandardCharsets.UTF_8)).isEqualTo(demoBefore);

    // payload: неизвестное свойство, нет path, нет items, items не массив, элемент не объект
    assertThatThrownBy(() -> DcsSettingsEdit.setSelection(dcs, SchemaVersion.V2_20, "Основной",
      Map.of("path", root, "items", List.of(), "uuid", "x")))
      .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Неизвестное свойство");
    assertThatThrownBy(() -> DcsSettingsEdit.setSelection(dcs, SchemaVersion.V2_20, "Основной",
      Map.of("items", List.of())))
      .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("path");
    assertThatThrownBy(() -> DcsSettingsEdit.setSelection(dcs, SchemaVersion.V2_20, "Основной",
      Map.of("path", root)))
      .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("items");
    assertThatThrownBy(() -> DcsSettingsEdit.setSelection(dcs, SchemaVersion.V2_20, "Основной",
      Map.of("path", root, "items", "всё")))
      .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("массивом");
    assertThatThrownBy(() -> DcsSettingsEdit.setSelection(dcs, SchemaVersion.V2_20, "Основной",
      Map.of("path", root, "items", List.of("поле"))))
      .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("объектом");

    // элементы: неизвестный ключ, вид, свойство не по виду, не булев use, лексемы и заголовки
    assertThatThrownBy(() -> DcsSettingsEdit.setSelection(dcs, SchemaVersion.V2_20, "Основной",
      Map.of("path", root, "items", List.of(Map.of("kind", "field", "field", "Период", "uuid", "x")))))
      .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Неизвестное свойство поля выбора");
    assertThatThrownBy(() -> DcsSettingsEdit.setSelection(dcs, SchemaVersion.V2_20, "Основной",
      Map.of("path", root, "items", List.of(Map.of("kind", "element")))))
      .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("должен быть одним из");
    assertThatThrownBy(() -> DcsSettingsEdit.setSelection(dcs, SchemaVersion.V2_20, "Основной",
      Map.of("path", root, "items", List.of(Map.of("kind", "auto", "field", "Период")))))
      .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("нет свойства field");
    assertThatThrownBy(() -> DcsSettingsEdit.setSelection(dcs, SchemaVersion.V2_20, "Основной",
      Map.of("path", root, "items", List.of(Map.of("kind", "field")))))
      .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("не задан field");
    assertThatThrownBy(() -> DcsSettingsEdit.setSelection(dcs, SchemaVersion.V2_20, "Основной",
      Map.of("path", root, "items", List.of(Map.of("kind", "auto", "use", "да")))))
      .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("true или false");
    assertThatThrownBy(() -> DcsSettingsEdit.setSelection(dcs, SchemaVersion.V2_20, "Основной",
      Map.of("path", root, "items", List.of(Map.of("kind", "folder", "placement", "Везде")))))
      .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("placement");
    assertThatThrownBy(() -> DcsSettingsEdit.setSelection(dcs, SchemaVersion.V2_20, "Основной",
      Map.of("path", root, "items",
        List.of(Map.of("kind", "field", "field", "Период", "title", "Т", "titleKind", "plain")))))
      .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("lws или string");
    assertThatThrownBy(() -> DcsSettingsEdit.setSelection(dcs, SchemaVersion.V2_20, "Основной",
      Map.of("path", root, "items",
        List.of(Map.of("kind", "field", "field", "Период", "titleKind", "string")))))
      .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("titleKind без title");
    assertThatThrownBy(() -> DcsSettingsEdit.setOrder(dcs, SchemaVersion.V2_20, "Основной",
      Map.of("path", root, "items",
        List.of(Map.of("kind", "field", "field", "Период", "orderType", "Up")))))
      .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("orderType");
    assertThatThrownBy(() -> DcsSettingsEdit.setOrder(dcs, SchemaVersion.V2_20, "Основной",
      Map.of("path", root, "items", List.of(Map.of("kind", "folder")))))
      .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("должен быть одним из");
    assertThatThrownBy(() -> DcsSettingsEdit.setGroupFields(dcs, SchemaVersion.V2_20, "Основной",
      Map.of("path", List.of(Map.of("list", "structure", "index", 0)), "items",
        List.of(Map.of("groupType", "Items")))))
      .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("не задан field");
    assertThat(Files.readString(dcs, StandardCharsets.UTF_8)).isEqualTo(before);

    // поле группировки, которого DTO не выражает (GroupItemAuto), сплайс не трогает
    Path doctored = copySchema(DYNAMICS);
    Files.writeString(doctored, insertAfterFirst(Files.readString(doctored, StandardCharsets.UTF_8),
      "<dcsset:groupItems>\r\n", "\t\t\t\t\t<dcsset:item xsi:type=\"dcsset:GroupItemAuto\"/>\r\n"),
      StandardCharsets.UTF_8);
    String doctoredBefore = Files.readString(doctored, StandardCharsets.UTF_8);
    assertThatThrownBy(() -> DcsSettingsEdit.setGroupFields(doctored, SchemaVersion.V2_20, "Основной",
      Map.of("path", List.of(Map.of("list", "structure", "index", 0)), "items", List.of())))
      .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("GroupItemField");
    assertThat(Files.readString(doctored, StandardCharsets.UTF_8)).isEqualTo(doctoredBefore);
  }

  // ------------------------------------------------------------------ канал apply-mutation

  @Test
  void applyMutationChannelRunsSetOps() throws Exception {
    Path dcs = copySchema(LOG_ANALYSIS);
    String before = Files.readString(dcs, StandardCharsets.UTF_8);
    String variant = "АнализАктивностиПользователей";

    // отказ доходит кодом 2, файл не меняется: у таблицы нет сортировки
    assertThat(applyMutation(Map.of(
      "op", "cf-dcs-set-order", "objectXml", dcs.toString(), "schemaVersion", "V2_20",
      "variant", variant,
      "payloadJson", "{\"path\":[{\"list\":\"structure\",\"index\":0}],\"items\":[]}"))).isEqualTo(2);
    assertThat(Files.readString(dcs, StandardCharsets.UTF_8)).isEqualTo(before);

    // эхо прочитанного через JSON-канал (числа приходят дробными) не меняет файл байт в байт
    List<Map<String, Object>> orderItems = sectionItems(settings(dcs, variant), "order");
    assertThat(applyMutation(Map.of(
      "op", "cf-dcs-set-order", "objectXml", dcs.toString(), "schemaVersion", "V2_20",
      "variant", variant,
      "payloadJson", new Gson().toJson(Map.of("path", List.of(), "items", orderItems))))).isZero();
    assertThat(Files.readString(dcs, StandardCharsets.UTF_8)).isEqualTo(before);

    // смена направления сортировки корня: переписан один элемент, прочий файл нетронут
    orderItems.get(0).put("orderType", "Asc");
    assertThat(applyMutation(Map.of(
      "op", "cf-dcs-set-order", "objectXml", dcs.toString(), "schemaVersion", "V2_20",
      "variant", variant,
      "payloadJson", new Gson().toJson(Map.of("path", List.of(), "items", orderItems))))).isZero();
    String reordered = Files.readString(dcs, StandardCharsets.UTF_8);
    assertThat(reordered).isEqualTo(replaceFirst(before,
      "\t\t\t\t\t<dcsset:field>Дата</dcsset:field>\r\n"
        + "\t\t\t\t\t<dcsset:orderType>Desc</dcsset:orderType>\r\n",
      "\t\t\t\t\t<dcsset:field>Дата</dcsset:field>\r\n"
        + "\t\t\t\t\t<dcsset:orderType>Asc</dcsset:orderType>\r\n"));
    assertThat(sectionItems(settings(dcs, variant), "order").get(0))
      .containsEntry("orderType", "Asc");

    // выбранные поля строки таблицы и поля группировки строки
    List<Map<String, Object>> rowPath = List.of(
      Map.of("list", "structure", "index", 0), Map.of("list", "rows", "index", 0));
    Map<String, Object> row = nodeAt(settings(dcs, variant), rowPath);
    List<Map<String, Object>> rowSelection = sectionItems(row, "selection");
    rowSelection.add(Map.of("kind", "auto"));
    assertThat(applyMutation(Map.of(
      "op", "cf-dcs-set-selection", "objectXml", dcs.toString(), "schemaVersion", "V2_20",
      "variant", variant,
      "payloadJson", new Gson().toJson(Map.of("path", rowPath, "items", rowSelection))))).isZero();
    List<Map<String, Object>> rowFields = sectionItemsOf(row, "groupFields");
    rowFields.add(Map.of("field", "Неделя", "periodAdditionType", "Week"));
    assertThat(applyMutation(Map.of(
      "op", "cf-dcs-set-group-fields", "objectXml", dcs.toString(), "schemaVersion", "V2_20",
      "variant", variant,
      "payloadJson", new Gson().toJson(Map.of("path", rowPath, "items", rowFields))))).isZero();
    Map<String, Object> changed = nodeAt(settings(dcs, variant), rowPath);
    List<Map<String, Object>> changedSelection = sectionItems(changed, "selection");
    assertThat(changedSelection.get(changedSelection.size() - 1)).containsEntry("kind", "auto");
    assertThat(list(changed.get("groupFields"))).hasSize(2);
    assertThat(Files.readString(dcs, StandardCharsets.UTF_8))
      .contains("<dcsset:periodAdditionType>Week</dcsset:periodAdditionType>");
  }

  // ------------------------------------------------------------------ помощники

  /** Блок поля выбора без заголовка на глубине корневого выбора. */
  private static String selectedField(String field) {
    return "\t\t\t\t<dcsset:item xsi:type=\"dcsset:SelectedItemField\">\r\n"
      + "\t\t\t\t\t<dcsset:field>" + field + "</dcsset:field>\r\n"
      + "\t\t\t\t</dcsset:item>\r\n";
  }

  /** Блок локализованного заголовка языком конфигурации с заданным отступом. */
  private static String lwsTitle(int depth, String title) {
    String tabs = "\t".repeat(depth);
    return tabs + "<dcsset:lwsTitle>\r\n"
      + tabs + "\t<v8:item>\r\n"
      + tabs + "\t\t<v8:lang>ru</v8:lang>\r\n"
      + tabs + "\t\t<v8:content>" + title + "</v8:content>\r\n"
      + tabs + "\t</v8:item>\r\n"
      + tabs + "</dcsset:lwsTitle>\r\n";
  }

  /** Ожидание: содержимое ПЕРВОЙ секции выбора корня заменено на блоки. */
  private static String withRootSelectionItems(String text, String... blocks) {
    String open = "\t\t\t<dcsset:selection>\r\n";
    int from = text.indexOf(open);
    assertThat(from).isNotNegative();
    from += open.length();
    int to = text.indexOf("\t\t\t</dcsset:selection>", from);
    return text.substring(0, from) + String.join("", blocks) + text.substring(to);
  }

  /** Ожидание: блок вставлен сразу после первого вхождения якоря. */
  private static String insertAfterFirst(String text, String anchor, String block) {
    int at = text.indexOf(anchor);
    assertThat(at).isNotNegative();
    int from = at + anchor.length();
    return text.substring(0, from) + block + text.substring(from);
  }

  /** Ожидание: заменено только первое вхождение подстроки. */
  private static String replaceFirst(String text, String target, String replacement) {
    int at = text.indexOf(target);
    assertThat(at).isNotNegative();
    return text.substring(0, at) + replacement + text.substring(at + target.length());
  }

  /** Настройки варианта из cf-dcs-settings-info. */
  private Map<String, Object> settings(Path dcs, String variant) throws Exception {
    return map(variants(info(dcs, variant)).get(0).get("settings"));
  }

  /** Узел DTO настроек по шагам пути {list, index}. */
  private static Map<String, Object> nodeAt(Map<String, Object> settings, List<Map<String, Object>> path) {
    Map<String, Object> node = settings;
    for (Map<String, Object> step : path) {
      node = map(list(node.get(step.get("list"))).get((Integer) step.get("index")));
    }
    return node;
  }

  /** Элементы секции узла (selection/order) изменяемым списком изменяемых объектов. */
  private static List<Map<String, Object>> sectionItems(Map<String, Object> node, String key) {
    return mutableItems(map(node.get(key)).get("items"));
  }

  /** Поля группировки узла изменяемым списком изменяемых объектов. */
  private static List<Map<String, Object>> sectionItemsOf(Map<String, Object> node, String key) {
    return mutableItems(node.get(key));
  }

  /** Изменяемая копия списка DTO: чтение отдаёт неизменяемые списки и элементы. */
  private static List<Map<String, Object>> mutableItems(Object value) {
    List<Map<String, Object>> items = new ArrayList<>();
    for (Map<String, Object> item : list(value)) {
      items.add(new java.util.LinkedHashMap<>(item));
    }
    return items;
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
