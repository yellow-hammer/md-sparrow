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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Поэлементные правки условного оформления ({@code cf-dcs-add-appearance-item},
 * {@code cf-dcs-set-appearance-item}, {@code cf-dcs-remove-appearance-item}).
 * Новый элемент собирается по xs:sequence формата: {@code use}, затем {@code selection},
 * {@code filter} и {@code appearance} всегда - даже пустыми самозакрытыми, - {@code presentation},
 * хвост и заданные области {@code useIn*} в строгом порядке; отсутствующая секция
 * создаётся по xs:sequence вида узла, а снятие последнего элемента убирает её - байтовый
 * возврат. Замена элемента со сплайс-семантикой: запрос, глубоко равный прочитанному, не
 * меняет файл байт в байт (главный тест гоняет эхо всех элементов четырёх фикстур), изменённый
 * элемент переписывается целиком, но неизменённые вложенные элементы отбора и оформления
 * переносят исходные байты - шрифт (тип вне белого списка записи) переживает смену цвета
 * соседа, а его собственная правка - отказ всей мутации. {@code userSettingID} элемента
 * сохраняется: без него в запросе переносится прочитанный, сменить его нельзя. Канал
 * apply-mutation выполняет операции, отказ доходит кодом 2 без изменения файла.
 */
class DcsSettingsAppearanceTest {

  /** Один вариант «Основной», условное оформление корня с десятком элементов. */
  private static final String ACCOUNTING_CHECK =
    "src/cf/Reports/РезультатыПроверкиУчета/Templates/ОсновнаяСхемаКомпоновкиДанных/Ext/Template.xml";

  /** Вариант «ПоВерсиям»: элемент оформления со шрифтом (тип вне белого списка) и userSettingID. */
  private static final String DEMO_FILES =
    "src/cf/Reports/_ДемоФайлы/Templates/ОсновнаяСхемаКомпоновкиДанных/Ext/Template.xml";

  /** Вариант «Дополнительный1» без условного оформления: создание секции на узле. */
  private static final String FILE_DYNAMICS =
    "src/cf/Reports/_ДемоДинамикаИзмененийФайлов/Templates/ОсновнаяСхемаКомпоновкиДанных/Ext/Template.xml";

  /** Таблицы и диаграммы с десятью флагами useIn* у элементов оформления. */
  private static final String PERFORMANCE =
    "src/cf/Reports/ОценкаПроизводительности/Templates/ОсновнаяСхемаКомпоновкиДанных/Ext/Template.xml";

  /** Первый элемент оформления корня — с шестью областями useIn* и без представления. */
  private static final String DEFERRED_UPDATE =
    "src/cf/Reports/ПрогрессОтложенногоОбновления/Templates/ОсновнаяСхемаКомпоновкиДанных/Ext/Template.xml";

  /** Пять вариантов, 24 элемента оформления на узлах всех видов. */
  private static final String LOG_ANALYSIS =
    "src/cf/Reports/АнализЖурналаРегистрации/Templates/ОсновнаяСхемаКомпоновкиДанных/Ext/Template.xml";

  @TempDir Path tempDir;

  // ------------------------------------------------------------------ главный тест сплайса

  @Test
  void noOpSetKeepsFixturesByteForByte() throws Exception {
    // каждый вариант, каждый узел, каждый элемент оформления: set с прочитанным DTO
    // не меняет файл байт в байт - чтение и писатель согласованы, включая useIn* и шрифты
    int ops = 0;
    for (String fixture : List.of(LOG_ANALYSIS, ACCOUNTING_CHECK, DEMO_FILES, PERFORMANCE)) {
      Path dcs = copySchema(fixture);
      String before = Files.readString(dcs, StandardCharsets.UTF_8);
      for (Map<String, Object> row : variants(info(dcs, null))) {
        String variant = (String) row.get("name");
        Map<String, Object> settings = map(variants(info(dcs, variant)).get(0).get("settings"));
        ops += echoNode(dcs, variant, before, settings, List.of());
      }
    }
    assertThat(ops).isGreaterThan(50);
  }

  /** Эхо элементов оформления узла и спуск в его детей; файл сверяется байт в байт. */
  private int echoNode(Path dcs, String variant, String before, Map<String, Object> node,
    List<Map<String, Object>> path) throws Exception {
    int ops = 0;
    if (node.get("conditionalAppearance") != null) {
      List<Map<String, Object>> items = list(map(node.get("conditionalAppearance")).get("items"));
      for (int i = 0; i < items.size(); i++) {
        DcsSettingsEdit.setAppearanceItem(dcs, SchemaVersion.V2_20, variant,
          Map.of("path", path, "index", i, "item", map(deepMutable(items.get(i)))));
        assertThat(Files.readString(dcs, StandardCharsets.UTF_8)).isEqualTo(before);
        ops++;
      }
    }
    for (String list : List.of("structure", "rows", "columns", "points", "series")) {
      if (!(node.get(list) instanceof List<?> children)) {
        continue;
      }
      for (int i = 0; i < children.size(); i++) {
        Map<String, Object> child = map(children.get(i));
        if ("nestedObject".equals(child.get("kind"))) {
          continue;
        }
        List<Map<String, Object>> childPath = new ArrayList<>(path);
        childPath.add(Map.of("list", list, "index", i));
        ops += echoNode(dcs, variant, before, child, childPath);
      }
    }
    return ops;
  }

  // ------------------------------------------------------------------ добавление элемента

  @Test
  void addsItemWithByteFormAndRemovesItBack() throws Exception {
    Path dcs = copySchema(ACCOUNTING_CHECK);
    String before = Files.readString(dcs, StandardCharsets.UTF_8);
    int count = appearanceItems(settings(dcs, "Основной")).size();

    // поле, условие и цвет: selection/filter/appearance пишутся всегда, useIn* - по порядку
    DcsSettingsEdit.addAppearanceItem(dcs, SchemaVersion.V2_20, "Основной", Map.of(
      "path", List.of(),
      "item", Map.of(
        "fields", List.of(Map.of("field", "ВидПроверки")),
        "filter", List.of(Map.of("kind", "comparison", "left", "ВидПроверки",
          "comparisonType", "Filled")),
        "appearance", List.of(Map.of("parameter", "ЦветФона",
          "value", List.of(Map.of("type", "v8ui:Color", "text", "web:Yellow")))),
        "useIn", Map.of("header", "DontUse", "fieldsHeader", "DontUse"))));
    String added = replaceFirst(before, "\t\t\t</dcsset:conditionalAppearance>",
      "\t\t\t\t<dcsset:item>\r\n"
        + "\t\t\t\t\t<dcsset:selection>\r\n"
        + "\t\t\t\t\t\t<dcsset:item>\r\n"
        + "\t\t\t\t\t\t\t<dcsset:field>ВидПроверки</dcsset:field>\r\n"
        + "\t\t\t\t\t\t</dcsset:item>\r\n"
        + "\t\t\t\t\t</dcsset:selection>\r\n"
        + "\t\t\t\t\t<dcsset:filter>\r\n"
        + "\t\t\t\t\t\t<dcsset:item xsi:type=\"dcsset:FilterItemComparison\">\r\n"
        + "\t\t\t\t\t\t\t<dcsset:left xsi:type=\"dcscor:Field\">ВидПроверки</dcsset:left>\r\n"
        + "\t\t\t\t\t\t\t<dcsset:comparisonType>Filled</dcsset:comparisonType>\r\n"
        + "\t\t\t\t\t\t</dcsset:item>\r\n"
        + "\t\t\t\t\t</dcsset:filter>\r\n"
        + "\t\t\t\t\t<dcsset:appearance>\r\n"
        + "\t\t\t\t\t\t<dcscor:item xsi:type=\"dcsset:SettingsParameterValue\">\r\n"
        + "\t\t\t\t\t\t\t<dcscor:parameter>ЦветФона</dcscor:parameter>\r\n"
        + "\t\t\t\t\t\t\t<dcscor:value xsi:type=\"v8ui:Color\">web:Yellow</dcscor:value>\r\n"
        + "\t\t\t\t\t\t</dcscor:item>\r\n"
        + "\t\t\t\t\t</dcsset:appearance>\r\n"
        + "\t\t\t\t\t<dcsset:useInFieldsHeader>DontUse</dcsset:useInFieldsHeader>\r\n"
        + "\t\t\t\t\t<dcsset:useInHeader>DontUse</dcsset:useInHeader>\r\n"
        + "\t\t\t\t</dcsset:item>\r\n"
        + "\t\t\t</dcsset:conditionalAppearance>");
    assertThat(Files.readString(dcs, StandardCharsets.UTF_8)).isEqualTo(added);

    // элемент читается обратно, и эхо прочитанного тем же номером не меняет файл
    List<Map<String, Object>> reread = appearanceItems(settings(dcs, "Основной"));
    assertThat(reread).hasSize(count + 1);
    Map<String, Object> item = reread.get(count);
    assertThat(list(item.get("fields")).get(0)).containsEntry("field", "ВидПроверки");
    assertThat(map(item.get("useIn")))
      .containsEntry("fieldsHeader", "DontUse").containsEntry("header", "DontUse");
    DcsSettingsEdit.setAppearanceItem(dcs, SchemaVersion.V2_20, "Основной",
      Map.of("path", List.of(), "index", count, "item", map(deepMutable(item))));
    assertThat(Files.readString(dcs, StandardCharsets.UTF_8)).isEqualTo(added);

    // снятие добавленного элемента возвращает файл байт в байт
    DcsSettingsEdit.removeAppearanceItem(dcs, SchemaVersion.V2_20, "Основной",
      Map.of("path", List.of(), "index", count));
    assertThat(Files.readString(dcs, StandardCharsets.UTF_8)).isEqualTo(before);
  }

  @Test
  void addCreatesMissingSectionOnNodeAndRemoveCollapsesIt() throws Exception {
    Path dcs = copySchema(FILE_DYNAMICS);
    String before = Files.readString(dcs, StandardCharsets.UTF_8);
    List<Map<String, Object>> path = List.of(Map.of("list", "structure", "index", 0));

    // у группировки нет условного оформления: секция с пустыми selection/filter встаёт
    // после selection по xs:sequence группировки
    DcsSettingsEdit.addAppearanceItem(dcs, SchemaVersion.V2_20, "Дополнительный1", Map.of(
      "path", path,
      "item", Map.of("appearance", List.of(Map.of("parameter", "ЦветФона",
        "value", List.of(Map.of("type", "v8ui:Color", "text", "web:Yellow")))))));
    assertThat(Files.readString(dcs, StandardCharsets.UTF_8)).isEqualTo(insertAfterFirst(before,
      "<dcsset:name>Дополнительный1</dcsset:name>",
      "\r\n\t\t\t\t</dcsset:selection>\r\n",
      "\t\t\t\t<dcsset:conditionalAppearance>\r\n"
        + "\t\t\t\t\t<dcsset:item>\r\n"
        + "\t\t\t\t\t\t<dcsset:selection/>\r\n"
        + "\t\t\t\t\t\t<dcsset:filter/>\r\n"
        + "\t\t\t\t\t\t<dcsset:appearance>\r\n"
        + "\t\t\t\t\t\t\t<dcscor:item xsi:type=\"dcsset:SettingsParameterValue\">\r\n"
        + "\t\t\t\t\t\t\t\t<dcscor:parameter>ЦветФона</dcscor:parameter>\r\n"
        + "\t\t\t\t\t\t\t\t<dcscor:value xsi:type=\"v8ui:Color\">web:Yellow</dcscor:value>\r\n"
        + "\t\t\t\t\t\t\t</dcscor:item>\r\n"
        + "\t\t\t\t\t\t</dcsset:appearance>\r\n"
        + "\t\t\t\t\t</dcsset:item>\r\n"
        + "\t\t\t\t</dcsset:conditionalAppearance>\r\n"));

    // снятие единственного элемента убирает опустевшую секцию - байтовый возврат
    DcsSettingsEdit.removeAppearanceItem(dcs, SchemaVersion.V2_20, "Дополнительный1",
      Map.of("path", path, "index", 0));
    assertThat(Files.readString(dcs, StandardCharsets.UTF_8)).isEqualTo(before);
  }

  // ------------------------------------------------------------------ замена элемента

  @Test
  void setChangesColorAndUseCarryingFontBytes() throws Exception {
    Path dcs = copySchema(DEMO_FILES);
    String before = Files.readString(dcs, StandardCharsets.UTF_8);

    // элемент «Подсвечивать активные версии»: цвет фона, цвет текста, шрифт и отступ
    List<Map<String, Object>> items = appearanceItems(settings(dcs, "ПоВерсиям"));
    Map<String, Object> item = map(deepMutable(items.get(0)));
    assertThat(item).containsEntry("userSettingID", "2abccdca-7ad0-4500-ad41-149e673683bb");
    List<Map<String, Object>> appearance = list(item.get("appearance"));
    assertThat(appearance.get(2)).containsEntry("parameter", "Шрифт");
    assertThat(map(list(appearance.get(2).get("value")).get(0))).containsEntry("opaque", true);

    // меняются только use элемента и цвет фона: шрифт переносится исходными байтами
    item.put("use", false);
    map(list(appearance.get(0).get("value")).get(0)).put("text", "web:Yellow");
    DcsSettingsEdit.setAppearanceItem(dcs, SchemaVersion.V2_20, "ПоВерсиям",
      Map.of("path", List.of(), "index", 0, "item", item));

    String yellow = replaceFirst(before, "web:HoneyDew", "web:Yellow");
    String recolored = insertAfterFirst(yellow,
      "<dcsset:name>ПоВерсиям</dcsset:name>",
      "\t\t\t<dcsset:conditionalAppearance>\r\n\t\t\t\t<dcsset:item>\r\n",
      "\t\t\t\t\t<dcsset:use>false</dcsset:use>\r\n");
    assertThat(Files.readString(dcs, StandardCharsets.UTF_8)).isEqualTo(recolored);

    // userSettingID на месте и без него в запросе: эхо прочитанного снова не меняет файл
    List<Map<String, Object>> reread = appearanceItems(settings(dcs, "ПоВерсиям"));
    assertThat(reread.get(0)).containsEntry("use", false)
      .containsEntry("userSettingID", "2abccdca-7ad0-4500-ad41-149e673683bb");
    Map<String, Object> echo = map(deepMutable(reread.get(0)));
    echo.remove("userSettingID");
    DcsSettingsEdit.setAppearanceItem(dcs, SchemaVersion.V2_20, "ПоВерсиям",
      Map.of("path", List.of(), "index", 0, "item", echo));
    assertThat(Files.readString(dcs, StandardCharsets.UTF_8)).isEqualTo(recolored);

    // use: true - умолчание, которое платформа не хранит: строка use снимается
    echo.put("use", true);
    DcsSettingsEdit.setAppearanceItem(dcs, SchemaVersion.V2_20, "ПоВерсиям",
      Map.of("path", List.of(), "index", 0, "item", echo));
    assertThat(Files.readString(dcs, StandardCharsets.UTF_8)).isEqualTo(yellow);
    assertThat(appearanceItems(settings(dcs, "ПоВерсиям")).get(0)).doesNotContainKey("use");
  }

  // ------------------------------------------------------------------ отказы

  @Test
  void refusesFontChangeAndBadShapes() throws Exception {
    Path dcs = copySchema(DEMO_FILES);
    String before = Files.readString(dcs, StandardCharsets.UTF_8);
    List<Map<String, Object>> root = List.of();

    // правка элемента оформления со шрифтом: значение типа вне белого списка записи
    List<Map<String, Object>> items = appearanceItems(settings(dcs, "ПоВерсиям"));
    Map<String, Object> fontUse = map(deepMutable(items.get(0)));
    list(fontUse.get("appearance")).get(2).put("use", true);
    assertThatThrownBy(() -> DcsSettingsEdit.setAppearanceItem(dcs, SchemaVersion.V2_20, "ПоВерсиям",
      Map.of("path", root, "index", 0, "item", fontUse)))
      .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("только для чтения");
    Map<String, Object> fontBold = map(deepMutable(items.get(0)));
    map(map(list(list(fontBold.get("appearance")).get(2).get("value")).get(0)).get("attrs"))
      .put("bold", "false");
    assertThatThrownBy(() -> DcsSettingsEdit.setAppearanceItem(dcs, SchemaVersion.V2_20, "ПоВерсиям",
      Map.of("path", root, "index", 0, "item", fontBold)))
      .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("только для чтения");

    // userSettingID не меняется и не выдумывается
    Map<String, Object> changedId = map(deepMutable(items.get(0)));
    changedId.put("userSettingID", "00000000-0000-0000-0000-000000000000");
    assertThatThrownBy(() -> DcsSettingsEdit.setAppearanceItem(dcs, SchemaVersion.V2_20, "ПоВерсиям",
      Map.of("path", root, "index", 0, "item", changedId)))
      .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("userSettingID");

    // номера, секции и формы элементов
    assertThatThrownBy(() -> DcsSettingsEdit.setAppearanceItem(dcs, SchemaVersion.V2_20, "ПоВерсиям",
      Map.of("path", root, "index", 9, "item", Map.of())))
      .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("нет элемента с номером");
    assertThatThrownBy(() -> DcsSettingsEdit.removeAppearanceItem(dcs, SchemaVersion.V2_20, "ПоВерсиям",
      Map.of("path", root, "index", 9)))
      .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("нет элемента с номером");
    assertThatThrownBy(() -> DcsSettingsEdit.addAppearanceItem(dcs, SchemaVersion.V2_20, "ПоВерсиям",
      Map.of("path", root, "index", 9, "item", Map.of())))
      .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("вне границ");
    assertThatThrownBy(() -> DcsSettingsEdit.addAppearanceItem(dcs, SchemaVersion.V2_20, "ПоВерсиям",
      Map.of("path", root, "item", Map.of("uuid", "х"))))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("Неизвестное свойство элемента условного оформления");
    assertThatThrownBy(() -> DcsSettingsEdit.addAppearanceItem(dcs, SchemaVersion.V2_20, "ПоВерсиям",
      Map.of("path", root, "item", Map.of("fields", List.of(Map.of("use", true))))))
      .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("не задан field");
    assertThatThrownBy(() -> DcsSettingsEdit.addAppearanceItem(dcs, SchemaVersion.V2_20, "ПоВерсиям",
      Map.of("path", root, "item", Map.of("useIn", Map.of("header", "Иногда")))))
      .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("useIn.header");
    assertThatThrownBy(() -> DcsSettingsEdit.addAppearanceItem(dcs, SchemaVersion.V2_20, "ПоВерсиям",
      Map.of("path", root, "item", Map.of("useIn", Map.of("везде", "Use")))))
      .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("useIn");
    assertThatThrownBy(() -> DcsSettingsEdit.addAppearanceItem(dcs, SchemaVersion.V2_20, "ПоВерсиям",
      Map.of("path", root, "item", Map.of("appearance", List.of(Map.of("parameter", "Текст",
        "value", Map.of("type", "xs:string", "text", "х")))))))
      .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("массивом значений");
    assertThatThrownBy(() -> DcsSettingsEdit.addAppearanceItem(dcs, SchemaVersion.V2_20, "ПоВерсиям",
      Map.of("path", root, "item", Map.of("appearance", List.of(Map.of("parameter", "ТипДиаграммы",
        "items", List.of(Map.of("parameter", "ТипДиаграммы.ВидПодписей"))))))))
      .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("только для чтения");

    // у узла без условного оформления заменять и снимать нечего
    Path dynamics = copySchema(FILE_DYNAMICS);
    String dynamicsBefore = Files.readString(dynamics, StandardCharsets.UTF_8);
    assertThatThrownBy(() -> DcsSettingsEdit.removeAppearanceItem(dynamics, SchemaVersion.V2_20,
      "Дополнительный1", Map.of("path", List.of(Map.of("list", "structure", "index", 0)), "index", 0)))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("нет секции условного оформления");
    assertThat(Files.readString(dynamics, StandardCharsets.UTF_8)).isEqualTo(dynamicsBefore);

    assertThat(Files.readString(dcs, StandardCharsets.UTF_8)).isEqualTo(before);
  }

  // ------------------------------------------------------------------ канал apply-mutation

  @Test
  void applyMutationChannelRunsAppearanceOps() throws Exception {
    Path dcs = copySchema(DEMO_FILES);
    String before = Files.readString(dcs, StandardCharsets.UTF_8);

    // отказ доходит кодом 2, файл не меняется: номера нет в секции
    assertThat(applyMutation(Map.of(
      "op", "cf-dcs-remove-appearance-item", "objectXml", dcs.toString(), "schemaVersion", "V2_20",
      "variant", "ПоВерсиям",
      "payloadJson", "{\"path\":[],\"index\":9}"))).isEqualTo(2);
    assertThat(Files.readString(dcs, StandardCharsets.UTF_8)).isEqualTo(before);

    // смена цвета через UTF-8 JSON-канал (числа приходят дробными)
    List<Map<String, Object>> items = appearanceItems(settings(dcs, "ПоВерсиям"));
    Map<String, Object> item = map(deepMutable(items.get(0)));
    map(list(list(item.get("appearance")).get(0).get("value")).get(0)).put("text", "web:Yellow");
    assertThat(applyMutation(Map.of(
      "op", "cf-dcs-set-appearance-item", "objectXml", dcs.toString(), "schemaVersion", "V2_20",
      "variant", "ПоВерсиям",
      "payloadJson", new Gson().toJson(Map.of("path", List.of(), "index", 0, "item", item)))))
      .isZero();
    String recolored = replaceFirst(before, "web:HoneyDew", "web:Yellow");
    assertThat(Files.readString(dcs, StandardCharsets.UTF_8)).isEqualTo(recolored);

    // добавление элемента через канал в конец секции
    assertThat(applyMutation(Map.of(
      "op", "cf-dcs-add-appearance-item", "objectXml", dcs.toString(), "schemaVersion", "V2_20",
      "variant", "ПоВерсиям",
      "payloadJson", new Gson().toJson(Map.of("path", List.of(), "item", Map.of(
        "fields", List.of(Map.of("field", "Версия")),
        "appearance", List.of(Map.of("parameter", "ЦветТекста",
          "value", List.of(Map.of("type", "v8ui:Color", "text", "web:Red"))))))))))
      .isZero();
    String added = insertAfterFirst(recolored, "<dcsset:name>ПоВерсиям</dcsset:name>",
      "\t\t\t\t\t<dcsset:userSettingID>2abccdca-7ad0-4500-ad41-149e673683bb"
        + "</dcsset:userSettingID>\r\n\t\t\t\t</dcsset:item>\r\n",
      "\t\t\t\t<dcsset:item>\r\n"
        + "\t\t\t\t\t<dcsset:selection>\r\n"
        + "\t\t\t\t\t\t<dcsset:item>\r\n"
        + "\t\t\t\t\t\t\t<dcsset:field>Версия</dcsset:field>\r\n"
        + "\t\t\t\t\t\t</dcsset:item>\r\n"
        + "\t\t\t\t\t</dcsset:selection>\r\n"
        + "\t\t\t\t\t<dcsset:filter/>\r\n"
        + "\t\t\t\t\t<dcsset:appearance>\r\n"
        + "\t\t\t\t\t\t<dcscor:item xsi:type=\"dcsset:SettingsParameterValue\">\r\n"
        + "\t\t\t\t\t\t\t<dcscor:parameter>ЦветТекста</dcscor:parameter>\r\n"
        + "\t\t\t\t\t\t\t<dcscor:value xsi:type=\"v8ui:Color\">web:Red</dcscor:value>\r\n"
        + "\t\t\t\t\t\t</dcscor:item>\r\n"
        + "\t\t\t\t\t</dcsset:appearance>\r\n"
        + "\t\t\t\t</dcsset:item>\r\n");
    assertThat(Files.readString(dcs, StandardCharsets.UTF_8)).isEqualTo(added);
  }

  // ------------------------------------------------------------------ порядок хвоста элемента

  @Test
  void presentationAndTailGoBeforeUseInAreas() throws Exception {
    // По xs:sequence формата представление, viewMode и userSettingID стоят до областей useIn*:
    // строгая проверка отвергает элемент с другим порядком
    Path dcs = copySchema(ACCOUNTING_CHECK);
    String before = Files.readString(dcs, StandardCharsets.UTF_8);
    DcsSettingsEdit.addAppearanceItem(dcs, SchemaVersion.V2_20, "Основной", Map.of(
      "path", List.of(),
      "item", Map.of(
        "fields", List.of(Map.of("field", "ВидПроверки")),
        "presentation", "Подсветка",
        "viewMode", "Normal",
        "useIn", Map.of("header", "DontUse"))));
    assertThat(Files.readString(dcs, StandardCharsets.UTF_8)).isEqualTo(replaceFirst(before,
      "\t\t\t</dcsset:conditionalAppearance>",
      "\t\t\t\t<dcsset:item>\r\n"
        + "\t\t\t\t\t<dcsset:selection>\r\n"
        + "\t\t\t\t\t\t<dcsset:item>\r\n"
        + "\t\t\t\t\t\t\t<dcsset:field>ВидПроверки</dcsset:field>\r\n"
        + "\t\t\t\t\t\t</dcsset:item>\r\n"
        + "\t\t\t\t\t</dcsset:selection>\r\n"
        + "\t\t\t\t\t<dcsset:filter/>\r\n"
        + "\t\t\t\t\t<dcsset:appearance/>\r\n"
        + "\t\t\t\t\t<dcsset:presentation xsi:type=\"v8:LocalStringType\">\r\n"
        + "\t\t\t\t\t\t<v8:item>\r\n"
        + "\t\t\t\t\t\t\t<v8:lang>ru</v8:lang>\r\n"
        + "\t\t\t\t\t\t\t<v8:content>Подсветка</v8:content>\r\n"
        + "\t\t\t\t\t\t</v8:item>\r\n"
        + "\t\t\t\t\t</dcsset:presentation>\r\n"
        + "\t\t\t\t\t<dcsset:viewMode>Normal</dcsset:viewMode>\r\n"
        + "\t\t\t\t\t<dcsset:useInHeader>DontUse</dcsset:useInHeader>\r\n"
        + "\t\t\t\t</dcsset:item>\r\n"
        + "\t\t\t</dcsset:conditionalAppearance>"));
    assertThat(DcsXsdValidation.validate(dcs).get("ok")).isEqualTo(true);

    // Элемент ssl31 с шестью областями использования без представления: представление
    // встаёт перед областями, файл проходит строгую проверку, эхо его не трогает
    Path deferred = copySchema(DEFERRED_UPDATE);
    String variant = (String) variants(info(deferred, null)).get(0).get("name");
    Map<String, Object> item = map(deepMutable(appearanceItems(settings(deferred, variant)).get(0)));
    assertThat(item).doesNotContainKey("presentation").containsKey("useIn");
    item.put("presentation", "Изменённое оформление");
    DcsSettingsEdit.setAppearanceItem(deferred, SchemaVersion.V2_20, variant,
      Map.of("path", List.of(), "index", 0, "item", item));
    String changed = Files.readString(deferred, StandardCharsets.UTF_8);
    assertThat(changed).contains("<v8:content>Изменённое оформление</v8:content>\r\n"
      + "\t\t\t\t\t\t</v8:item>\r\n"
      + "\t\t\t\t\t</dcsset:presentation>\r\n"
      + "\t\t\t\t\t<dcsset:useInHierarchicalGroup>DontUse</dcsset:useInHierarchicalGroup>\r\n");
    assertThat(DcsXsdValidation.validate(deferred).get("ok")).isEqualTo(true);
    Map<String, Object> reread = map(deepMutable(appearanceItems(settings(deferred, variant)).get(0)));
    assertThat(reread).containsEntry("presentation", "Изменённое оформление")
      .containsEntry("useIn", item.get("useIn"));
    DcsSettingsEdit.setAppearanceItem(deferred, SchemaVersion.V2_20, variant,
      Map.of("path", List.of(), "index", 0, "item", reread));
    assertThat(Files.readString(deferred, StandardCharsets.UTF_8)).isEqualTo(changed);
  }

  // ------------------------------------------------------------------ помощники

  /** Настройки варианта из cf-dcs-settings-info. */
  private static Map<String, Object> settings(Path dcs, String variant) throws Exception {
    return map(variants(info(dcs, variant)).get(0).get("settings"));
  }

  /** Элементы условного оформления узла; нет секции - тест падает. */
  private static List<Map<String, Object>> appearanceItems(Map<String, Object> node) {
    return list(map(node.get("conditionalAppearance")).get("items"));
  }

  /** Глубоко изменяемая копия DTO: чтение отдаёт неизменяемые списки. */
  private static Object deepMutable(Object value) {
    if (value instanceof Map<?, ?> map) {
      Map<String, Object> out = new LinkedHashMap<>();
      for (Map.Entry<?, ?> entry : map.entrySet()) {
        out.put(String.valueOf(entry.getKey()), deepMutable(entry.getValue()));
      }
      return out;
    }
    if (value instanceof List<?> items) {
      List<Object> out = new ArrayList<>();
      for (Object item : items) {
        out.add(deepMutable(item));
      }
      return out;
    }
    return value;
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

  /** Ожидание: заменено только первое вхождение подстроки. */
  private static String replaceFirst(String text, String target, String replacement) {
    int at = text.indexOf(target);
    assertThat(at).isNotNegative();
    return text.substring(0, at) + replacement + text.substring(at + target.length());
  }

  /** Ожидание: блок вставлен после первого вхождения якоря, идущего после метки from. */
  private static String insertAfterFirst(String text, String from, String anchor, String block) {
    int at = text.indexOf(from);
    assertThat(at).isNotNegative();
    int anchorAt = text.indexOf(anchor, at);
    assertThat(anchorAt).isNotNegative();
    int to = anchorAt + anchor.length();
    return text.substring(0, to) + block + text.substring(to);
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
}
