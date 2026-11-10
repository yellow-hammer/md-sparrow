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
 * Set-операция отбора ({@code cf-dcs-set-filter}) на движке «сплайса только изменённого».
 * Главный инвариант: запрос с теми же элементами, что отдало чтение, не меняет файл байт
 * в байт - на всех вариантах и узлах четырёх фикстур, включая двойной {@code right}
 * Анкетирования и группы And/Or. Изменённое сравнение переписывается одной зоной: смена вида
 * сравнения и правого скаляра, правка текста {@code dcscor:DesignTimeValue}, {@code Filled}
 * без правого значения, новая вложенная группа {@code OrGroup} с двумя сравнениями, удаление
 * элемента группы с сохранением её хвоста ({@code userSettingID} неприкосновенен). Правое
 * значение пишется по белому списку: новый список значений по образцу платформы (пустой
 * {@code v8:valueType} и {@code lastId} -1), стандартный период {@code Custom} с границами
 * и дата начала без даты при готовом варианте, цвет, перечисление лексемой, локализованная
 * строка, {@code xsi:nil}. Несколько {@code right} заменяются только массивом целиком: правка
 * одного из двух переписывает оба повтором элемента. Значение типа вне белого списка
 * переносится байтами, его правка - отказ всей мутации. Канал apply-mutation выполняет
 * операцию, отказ доходит кодом 2 без изменения файла.
 */
class DcsSettingsFilterTest {

  /** Отборы групп с application Hierarchy, Filled без правого значения. */
  private static final String LOG_ANALYSIS =
    "src/cf/Reports/АнализЖурналаРегистрации/Templates/ОсновнаяСхемаКомпоновкиДанных/Ext/Template.xml";

  /** Группы And/Or с хвостами, списки значений, правые поле и дата. */
  private static final String DEMO_FILES =
    "src/cf/Reports/_ДемоФайлы/Templates/ОсновнаяСхемаКомпоновкиДанных/Ext/Template.xml";

  /** Девять вариантов с корневыми отборами; двойной right при NotInList. */
  private static final String QUESTIONNAIRE =
    "src/cf/Reports/АналитическийОтчетПоАнкетированию/Templates/ОсновнаяСхемаКомпоновкиДанных/Ext/Template.xml";

  /** Корневой отбор с булевым правым скаляром и пустыми списками значений. */
  private static final String ACCOUNTING_CHECK =
    "src/cf/Reports/РезультатыПроверкиУчета/Templates/ОсновнаяСхемаКомпоновкиДанных/Ext/Template.xml";

  @TempDir Path tempDir;

  // ------------------------------------------------------------------ главный тест движка

  @Test
  void setFilterWithReadItemsKeepsFixturesByteForByte() throws Exception {
    // каждый вариант, каждый узел с отбором (корень настроек и группировки всех уровней):
    // set-filter с теми же прочитанными items не меняет файл байт в байт
    int ops = 0;
    for (String fixture : List.of(LOG_ANALYSIS, DEMO_FILES, QUESTIONNAIRE, ACCOUNTING_CHECK)) {
      Path dcs = copySchema(fixture);
      String before = Files.readString(dcs, StandardCharsets.UTF_8);
      for (Map<String, Object> row : variants(info(dcs, null))) {
        String variant = (String) row.get("name");
        Map<String, Object> settings = map(variants(info(dcs, variant)).get(0).get("settings"));
        ops += echoNode(dcs, variant, before, settings, "settings", List.of());
      }
    }
    assertThat(ops).isGreaterThan(40);
  }

  /** Эхо отбора узла и спуск в его детей; на каждом шаге файл сверяется байт в байт. */
  private int echoNode(Path dcs, String variant, String before, Map<String, Object> node,
    String kind, List<Map<String, Object>> path) throws Exception {
    int ops = 0;
    if ("settings".equals(kind) || "group".equals(kind)) {
      DcsSettingsEdit.setFilter(dcs, SchemaVersion.V2_20, variant,
        Map.of("path", path, "items", filterItems(node)));
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

  // ------------------------------------------------------------------ правка сравнения

  @Test
  void changesComparisonTypeAndScalarRight() throws Exception {
    Path dcs = copySchema(ACCOUNTING_CHECK);
    String before = Files.readString(dcs, StandardCharsets.UTF_8);

    // у корневого отбора «Основной» меняются вид сравнения и правый скаляр одного элемента
    List<Map<String, Object>> items = filterItems(settings(dcs, "Основной"));
    Map<String, Object> target = itemByLeft(items, "ИгнорироватьПроблему");
    assertThat(target).containsEntry("comparisonType", "Equal");
    assertThat(map(list(target.get("right")).get(0))).containsEntry("text", "false");
    target.put("comparisonType", "NotEqual");
    target.put("right", List.of(Map.of("type", "xs:boolean", "decoded", true)));
    DcsSettingsEdit.setFilter(dcs, SchemaVersion.V2_20, "Основной",
      Map.of("path", List.of(), "items", items));

    // переписан только изменённый элемент: соседи с userSettingID нетронуты байтово
    String after = Files.readString(dcs, StandardCharsets.UTF_8);
    assertThat(after).isEqualTo(replaceFirst(before,
      "\t\t\t\t\t<dcsset:comparisonType>Equal</dcsset:comparisonType>\r\n"
        + "\t\t\t\t\t<dcsset:right xsi:type=\"xs:boolean\">false</dcsset:right>\r\n",
      "\t\t\t\t\t<dcsset:comparisonType>NotEqual</dcsset:comparisonType>\r\n"
        + "\t\t\t\t\t<dcsset:right xsi:type=\"xs:boolean\">true</dcsset:right>\r\n"));

    // эхо изменённого состояния снова не трогает файл: DTO и писатель согласованы
    DcsSettingsEdit.setFilter(dcs, SchemaVersion.V2_20, "Основной",
      Map.of("path", List.of(), "items", filterItems(settings(dcs, "Основной"))));
    assertThat(Files.readString(dcs, StandardCharsets.UTF_8)).isEqualTo(after);
  }

  @Test
  void writesFilledComparisonWithoutRight() throws Exception {
    Path dcs = copySchema(ACCOUNTING_CHECK);
    String before = Files.readString(dcs, StandardCharsets.UTF_8);
    List<Map<String, Object>> items = filterItems(settings(dcs, "Основной"));

    // правое значение у Filled/NotFilled не имеет смысла: отказ, файл не тронут
    List<Map<String, Object>> withRight = new ArrayList<>(items);
    withRight.add(Map.of("kind", "comparison", "left", "Ответственный", "comparisonType", "NotFilled",
      "right", List.of(Map.of("type", "xs:string", "text", "х"))));
    assertThatThrownBy(() -> DcsSettingsEdit.setFilter(dcs, SchemaVersion.V2_20, "Основной",
      Map.of("path", List.of(), "items", withRight)))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("не несёт правого значения");
    assertThat(Files.readString(dcs, StandardCharsets.UTF_8)).isEqualTo(before);

    // условие Filled пишется без right
    items.add(Map.of("kind", "comparison", "left", "ПричинаОшибки", "comparisonType", "Filled"));
    DcsSettingsEdit.setFilter(dcs, SchemaVersion.V2_20, "Основной",
      Map.of("path", List.of(), "items", items));
    assertThat(Files.readString(dcs, StandardCharsets.UTF_8)).isEqualTo(replaceFirst(before,
      "\t\t\t</dcsset:filter>",
      "\t\t\t\t<dcsset:item xsi:type=\"dcsset:FilterItemComparison\">\r\n"
        + "\t\t\t\t\t<dcsset:left xsi:type=\"dcscor:Field\">ПричинаОшибки</dcsset:left>\r\n"
        + "\t\t\t\t\t<dcsset:comparisonType>Filled</dcsset:comparisonType>\r\n"
        + "\t\t\t\t</dcsset:item>\r\n"
        + "\t\t\t</dcsset:filter>"));
    Map<String, Object> added = itemByLeft(filterItems(settings(dcs, "Основной")), "ПричинаОшибки");
    assertThat(added).containsEntry("comparisonType", "Filled").doesNotContainKey("right");
  }

  // ------------------------------------------------------------------ группы отбора

  @Test
  void writesNestedOrGroupWithTwoComparisons() throws Exception {
    Path dcs = copySchema(DEMO_FILES);
    String before = Files.readString(dcs, StandardCharsets.UTF_8);

    // новая группа ИЛИ с двумя сравнениями встаёт в конец элементов - перед хвостом секции
    List<Map<String, Object>> items = filterItems(settings(dcs, "Вспомогательный"));
    items.add(Map.of("kind", "group", "groupType", "OrGroup", "items", List.of(
      Map.of("kind", "comparison", "left", "Размер", "comparisonType", "Greater",
        "right", List.of(Map.of("type", "xs:decimal", "decoded", 1000))),
      Map.of("kind", "comparison", "left", "Автор",
        "right", List.of(Map.of("type", "dcscor:DesignTimeValue",
          "text", "Справочник.Пользователи.ПустаяСсылка"))))));
    DcsSettingsEdit.setFilter(dcs, SchemaVersion.V2_20, "Вспомогательный",
      Map.of("path", List.of(), "items", items));

    String tail = "\t\t\t\t<dcsset:viewMode>Normal</dcsset:viewMode>\r\n"
      + "\t\t\t\t<dcsset:userSettingID>f801037b-30af-491e-9d6b-0bc813f70472</dcsset:userSettingID>\r\n";
    String extended = Files.readString(dcs, StandardCharsets.UTF_8);
    assertThat(extended).isEqualTo(replaceFirst(before, tail,
      "\t\t\t\t<dcsset:item xsi:type=\"dcsset:FilterItemGroup\">\r\n"
        + "\t\t\t\t\t<dcsset:groupType>OrGroup</dcsset:groupType>\r\n"
        + "\t\t\t\t\t<dcsset:item xsi:type=\"dcsset:FilterItemComparison\">\r\n"
        + "\t\t\t\t\t\t<dcsset:left xsi:type=\"dcscor:Field\">Размер</dcsset:left>\r\n"
        + "\t\t\t\t\t\t<dcsset:comparisonType>Greater</dcsset:comparisonType>\r\n"
        + "\t\t\t\t\t\t<dcsset:right xsi:type=\"xs:decimal\">1000</dcsset:right>\r\n"
        + "\t\t\t\t\t</dcsset:item>\r\n"
        + "\t\t\t\t\t<dcsset:item xsi:type=\"dcsset:FilterItemComparison\">\r\n"
        + "\t\t\t\t\t\t<dcsset:left xsi:type=\"dcscor:Field\">Автор</dcsset:left>\r\n"
        + "\t\t\t\t\t\t<dcsset:comparisonType>Equal</dcsset:comparisonType>\r\n"
        + "\t\t\t\t\t\t<dcsset:right xsi:type=\"dcscor:DesignTimeValue\">"
        + "Справочник.Пользователи.ПустаяСсылка</dcsset:right>\r\n"
        + "\t\t\t\t\t</dcsset:item>\r\n"
        + "\t\t\t\t</dcsset:item>\r\n"
        + tail));

    // группа читается обратно и эхо прочитанного снова не меняет файл
    List<Map<String, Object>> reread = filterItems(settings(dcs, "Вспомогательный"));
    Map<String, Object> group = reread.get(reread.size() - 1);
    assertThat(group).containsEntry("kind", "group").containsEntry("groupType", "OrGroup");
    assertThat(list(group.get("items"))).hasSize(2);
    DcsSettingsEdit.setFilter(dcs, SchemaVersion.V2_20, "Вспомогательный",
      Map.of("path", List.of(), "items", reread));
    assertThat(Files.readString(dcs, StandardCharsets.UTF_8)).isEqualTo(extended);
  }

  @Test
  void removesGroupChildRewritingOnlyTheGroup() throws Exception {
    Path dcs = copySchema(DEMO_FILES);
    String before = Files.readString(dcs, StandardCharsets.UTF_8);

    // у группы И «Только занятые до 2015 г.» снимается второе вложенное сравнение
    List<Map<String, Object>> items = filterItems(settings(dcs, "Вспомогательный"));
    Map<String, Object> group = items.get(3);
    assertThat(group).containsEntry("kind", "group")
      .containsEntry("userSettingID", "f0c5e1b6-a7d8-4a97-a391-fa251db17ed5");
    List<Map<String, Object>> nested = list(group.get("items"));
    assertThat(nested).hasSize(2);
    assertThat(nested.get(1)).containsEntry("left", "Редактирует");
    nested.remove(1);
    DcsSettingsEdit.setFilter(dcs, SchemaVersion.V2_20, "Вспомогательный",
      Map.of("path", List.of(), "items", items));

    // переписана только сама группа: снятый ребёнок вырезан, первый ребёнок, хвост с
    // userSettingID и соседние элементы сохранили байты; представление настройки переписано
    // канонической локализованной формой (исходная xs:string выражается DTO той же строкой)
    String after = Files.readString(dcs, StandardCharsets.UTF_8);
    assertThat(after).isEqualTo(replaceFirst(replaceFirst(before,
      "\t\t\t\t\t<dcsset:item xsi:type=\"dcsset:FilterItemComparison\">\r\n"
        + "\t\t\t\t\t\t<dcsset:left xsi:type=\"dcscor:Field\">Редактирует</dcsset:left>\r\n"
        + "\t\t\t\t\t\t<dcsset:comparisonType>Filled</dcsset:comparisonType>\r\n"
        + "\t\t\t\t\t\t<dcsset:viewMode>Inaccessible</dcsset:viewMode>\r\n"
        + "\t\t\t\t\t</dcsset:item>\r\n", ""),
      "\t\t\t\t\t<dcsset:userSettingPresentation xsi:type=\"xs:string\">"
        + "Только занятые до 2015 г.</dcsset:userSettingPresentation>\r\n",
      "\t\t\t\t\t<dcsset:userSettingPresentation xsi:type=\"v8:LocalStringType\">\r\n"
        + "\t\t\t\t\t\t<v8:item>\r\n"
        + "\t\t\t\t\t\t\t<v8:lang>ru</v8:lang>\r\n"
        + "\t\t\t\t\t\t\t<v8:content>Только занятые до 2015 г.</v8:content>\r\n"
        + "\t\t\t\t\t\t</v8:item>\r\n"
        + "\t\t\t\t\t</dcsset:userSettingPresentation>\r\n"));

    // эхо прочитанного после правки снова не меняет файл
    DcsSettingsEdit.setFilter(dcs, SchemaVersion.V2_20, "Вспомогательный",
      Map.of("path", List.of(), "items", filterItems(settings(dcs, "Вспомогательный"))));
    assertThat(Files.readString(dcs, StandardCharsets.UTF_8)).isEqualTo(after);
  }

  // ------------------------------------------------------------------ двойной right

  @Test
  void doubleRightIsReplacedOnlyAsWholeArray() throws Exception {
    Path dcs = copySchema(QUESTIONNAIRE);
    String before = Files.readString(dcs, StandardCharsets.UTF_8);
    String variant = "ПросмотрОтветовПростыеВопросы";

    // NotInList хранит два правых значения повтором элемента right
    List<Map<String, Object>> items = filterItems(settings(dcs, variant));
    Map<String, Object> doubled = itemByLeft(items, "Вопрос.ТипВопроса");
    List<Map<String, Object>> rights = list(doubled.get("right"));
    assertThat(rights).hasSize(2);
    assertThat(map(rights.get(0)))
      .containsEntry("type", "dcscor:DesignTimeValue")
      .containsEntry("text", "Перечисление.ТипыВопросовШаблонаАнкеты.Табличный");

    // items запроса несут массив right целиком: правка одного из двух переписывает элемент
    // с обоими правыми значениями, неизменённое значение сохраняет лексику
    map(rights.get(1)).put("text", "Перечисление.ТипыВопросовШаблонаАнкеты.Условный");
    // заодно правится текст dcscor:DesignTimeValue одиночного right другого сравнения
    Map<String, Object> single = itemByLeft(items, "Анкета");
    map(list(single.get("right")).get(0)).put("text", "Документ.ПакетАнкет.ПустаяСсылка");
    DcsSettingsEdit.setFilter(dcs, SchemaVersion.V2_20, variant,
      Map.of("path", List.of(), "items", items));

    String after = Files.readString(dcs, StandardCharsets.UTF_8);
    assertThat(after).isEqualTo(replaceFirst(replaceFirst(before,
      "Перечисление.ТипыВопросовШаблонаАнкеты.Комплексный</dcsset:right>",
      "Перечисление.ТипыВопросовШаблонаАнкеты.Условный</dcsset:right>"),
      "Документ.Анкета.ПустаяСсылка</dcsset:right>",
      "Документ.ПакетАнкет.ПустаяСсылка</dcsset:right>"));

    // оба правых значения на месте и эхо прочитанного снова не меняет файл
    List<Map<String, Object>> reread = filterItems(settings(dcs, variant));
    List<Map<String, Object>> rereadRights = list(itemByLeft(reread, "Вопрос.ТипВопроса").get("right"));
    assertThat(rereadRights).hasSize(2);
    assertThat(map(rereadRights.get(1)))
      .containsEntry("text", "Перечисление.ТипыВопросовШаблонаАнкеты.Условный");
    DcsSettingsEdit.setFilter(dcs, SchemaVersion.V2_20, variant,
      Map.of("path", List.of(), "items", reread));
    assertThat(Files.readString(dcs, StandardCharsets.UTF_8)).isEqualTo(after);
  }

  // ------------------------------------------------------------------ белый список значений

  @Test
  void writesWhitelistValuesByPlatformSamples() throws Exception {
    Path dcs = copySchema(DEMO_FILES);
    String before = Files.readString(dcs, StandardCharsets.UTF_8);
    List<Map<String, Object>> items = filterItems(settings(dcs, "Вспомогательный"));

    // новый список значений: пустой valueType, lastId -1 и элементы со скалярами по образцу
    items.get(0).put("right", List.of(Map.of("type", "v8:ValueListType", "list", Map.of("items", List.of(
      Map.of("value", Map.of("type", "xs:string", "text", "ОК"), "presentation", "Статус"),
      Map.of("value", Map.of("type", "xs:decimal", "decoded", 2)))))));
    // стандартный период Custom с обеими границами
    items.get(1).put("right", List.of(Map.of("type", "v8:StandardPeriod", "period", Map.of(
      "variant", "Custom", "startDate", "2014-01-01T00:00:00", "endDate", "2014-12-31T23:59:59"))));
    // стандартная дата начала с готовым вариантом - без даты
    items.get(2).put("right", List.of(Map.of("type", "v8:StandardBeginningDate",
      "beginningDate", Map.of("variant", "BeginningOfThisDay"))));
    // повтор right: цвет, лексема перечисления, локализованная строка и xsi:nil
    items.get(4).put("right", List.of(
      Map.of("type", "v8ui:Color", "text", "web:Yellow"),
      Map.of("type", "dcsset:DataCompositionTextOutputType", "text", "DontOutput"),
      Map.of("type", "v8:LocalStringType", "decoded", "Привет"),
      Map.of("nil", true)));
    DcsSettingsEdit.setFilter(dcs, SchemaVersion.V2_20, "Вспомогательный",
      Map.of("path", List.of(), "items", items));

    String after = Files.readString(dcs, StandardCharsets.UTF_8);
    assertThat(after).isEqualTo(replaceFirst(replaceFirst(replaceFirst(replaceFirst(before,
      "\t\t\t\t\t<dcsset:right xsi:type=\"v8:ValueListType\">\r\n"
        + "\t\t\t\t\t\t<v8:valueType/>\r\n"
        + "\t\t\t\t\t\t<v8:lastId xsi:type=\"xs:decimal\">-1</v8:lastId>\r\n"
        + "\t\t\t\t\t</dcsset:right>\r\n"
        + "\t\t\t\t\t<dcsset:userSettingID>12d22a97-7de0-4a61-8546-d668717527a0</dcsset:userSettingID>\r\n",
      "\t\t\t\t\t<dcsset:right xsi:type=\"v8:ValueListType\">\r\n"
        + "\t\t\t\t\t\t<v8:valueType/>\r\n"
        + "\t\t\t\t\t\t<v8:lastId xsi:type=\"xs:decimal\">-1</v8:lastId>\r\n"
        + "\t\t\t\t\t\t<v8:item>\r\n"
        + "\t\t\t\t\t\t\t<v8:value xsi:type=\"xs:string\">ОК</v8:value>\r\n"
        + "\t\t\t\t\t\t\t<v8:presentation>Статус</v8:presentation>\r\n"
        + "\t\t\t\t\t\t</v8:item>\r\n"
        + "\t\t\t\t\t\t<v8:item>\r\n"
        + "\t\t\t\t\t\t\t<v8:value xsi:type=\"xs:decimal\">2</v8:value>\r\n"
        + "\t\t\t\t\t\t</v8:item>\r\n"
        + "\t\t\t\t\t</dcsset:right>\r\n"
        + "\t\t\t\t\t<dcsset:userSettingID>12d22a97-7de0-4a61-8546-d668717527a0</dcsset:userSettingID>\r\n"),
      "\t\t\t\t\t<dcsset:comparisonType>Equal</dcsset:comparisonType>\r\n"
        + "\t\t\t\t\t<dcsset:userSettingID>6a1ff498-636c-402b-8a14-180b4269c60a</dcsset:userSettingID>\r\n",
      "\t\t\t\t\t<dcsset:comparisonType>Equal</dcsset:comparisonType>\r\n"
        + "\t\t\t\t\t<dcsset:right xsi:type=\"v8:StandardPeriod\">\r\n"
        + "\t\t\t\t\t\t<v8:variant xsi:type=\"v8:StandardPeriodVariant\">Custom</v8:variant>\r\n"
        + "\t\t\t\t\t\t<v8:startDate>2014-01-01T00:00:00</v8:startDate>\r\n"
        + "\t\t\t\t\t\t<v8:endDate>2014-12-31T23:59:59</v8:endDate>\r\n"
        + "\t\t\t\t\t</dcsset:right>\r\n"
        + "\t\t\t\t\t<dcsset:userSettingID>6a1ff498-636c-402b-8a14-180b4269c60a</dcsset:userSettingID>\r\n"),
      "\t\t\t\t\t<dcsset:right xsi:type=\"v8:ValueListType\">\r\n"
        + "\t\t\t\t\t\t<v8:valueType/>\r\n"
        + "\t\t\t\t\t\t<v8:lastId xsi:type=\"xs:decimal\">-1</v8:lastId>\r\n"
        + "\t\t\t\t\t</dcsset:right>\r\n"
        + "\t\t\t\t\t<dcsset:viewMode>Normal</dcsset:viewMode>\r\n"
        + "\t\t\t\t\t<dcsset:userSettingID>520ef0e2-fdaf-437f-af5c-ac90019eabde</dcsset:userSettingID>\r\n",
      "\t\t\t\t\t<dcsset:right xsi:type=\"v8:StandardBeginningDate\">\r\n"
        + "\t\t\t\t\t\t<v8:variant xsi:type=\"v8:StandardBeginningDateVariant\">"
        + "BeginningOfThisDay</v8:variant>\r\n"
        + "\t\t\t\t\t</dcsset:right>\r\n"
        + "\t\t\t\t\t<dcsset:viewMode>Normal</dcsset:viewMode>\r\n"
        + "\t\t\t\t\t<dcsset:userSettingID>520ef0e2-fdaf-437f-af5c-ac90019eabde</dcsset:userSettingID>\r\n"),
      "\t\t\t\t\t<dcsset:right xsi:type=\"v8:ValueListType\">\r\n"
        + "\t\t\t\t\t\t<v8:valueType/>\r\n"
        + "\t\t\t\t\t\t<v8:lastId xsi:type=\"xs:decimal\">-1</v8:lastId>\r\n"
        + "\t\t\t\t\t</dcsset:right>\r\n"
        + "\t\t\t\t\t<dcsset:viewMode>Normal</dcsset:viewMode>\r\n"
        + "\t\t\t\t\t<dcsset:userSettingID>6ef84254-b20b-42de-bf5c-bad833b85475</dcsset:userSettingID>\r\n",
      "\t\t\t\t\t<dcsset:right xsi:type=\"v8ui:Color\">web:Yellow</dcsset:right>\r\n"
        + "\t\t\t\t\t<dcsset:right xsi:type=\"dcsset:DataCompositionTextOutputType\">"
        + "DontOutput</dcsset:right>\r\n"
        + "\t\t\t\t\t<dcsset:right xsi:type=\"v8:LocalStringType\">\r\n"
        + "\t\t\t\t\t\t<v8:item>\r\n"
        + "\t\t\t\t\t\t\t<v8:lang>ru</v8:lang>\r\n"
        + "\t\t\t\t\t\t\t<v8:content>Привет</v8:content>\r\n"
        + "\t\t\t\t\t\t</v8:item>\r\n"
        + "\t\t\t\t\t</dcsset:right>\r\n"
        + "\t\t\t\t\t<dcsset:right xsi:nil=\"true\"/>\r\n"
        + "\t\t\t\t\t<dcsset:viewMode>Normal</dcsset:viewMode>\r\n"
        + "\t\t\t\t\t<dcsset:userSettingID>6ef84254-b20b-42de-bf5c-bad833b85475</dcsset:userSettingID>\r\n"));

    // прочитанный список несёт значения с представлением, эхо снова не меняет файл
    List<Map<String, Object>> reread = filterItems(settings(dcs, "Вспомогательный"));
    List<Map<String, Object>> listItems =
      list(map(map(list(reread.get(0).get("right")).get(0)).get("list")).get("items"));
    assertThat(listItems).hasSize(2);
    assertThat(map(listItems.get(0))).containsEntry("presentation", "Статус");
    assertThat(map(map(listItems.get(0)).get("value"))).containsEntry("text", "ОК");
    DcsSettingsEdit.setFilter(dcs, SchemaVersion.V2_20, "Вспомогательный",
      Map.of("path", List.of(), "items", reread));
    assertThat(Files.readString(dcs, StandardCharsets.UTF_8)).isEqualTo(after);
  }

  @Test
  void writesValuesFromDecodedAsWebviewSends() throws Exception {
    // редактор значений webview шлёт у новых значений только decoded - без text
    Path dcs = copySchema(DEMO_FILES);
    List<Map<String, Object>> items = filterItems(settings(dcs, "Вспомогательный"));
    items.get(0).put("comparisonType", "Equal");
    items.get(0).put("right", List.of(
      Map.of("type", "xs:string", "decoded", "Смок"),
      Map.of("type", "dcscor:Field", "decoded", "Файл.Автор"),
      Map.of("type", "xs:dateTime", "decoded", "2020-02-02T00:00:00"),
      Map.of("type", "dcsset:DataCompositionTextOutputType", "decoded", "Output"),
      Map.of("type", "v8ui:Color", "decoded", "#FFE4B5")));
    DcsSettingsEdit.setFilter(dcs, SchemaVersion.V2_20, "Вспомогательный",
      Map.of("path", List.of(), "items", items));

    List<Map<String, Object>> reread = filterItems(settings(dcs, "Вспомогательный"));
    List<Map<String, Object>> right = list(reread.get(0).get("right")).stream()
      .map(DcsSettingsFilterTest::map).toList();
    assertThat(right).hasSize(5);
    assertThat(right.get(0)).containsEntry("type", "xs:string").containsEntry("text", "Смок");
    assertThat(right.get(1)).containsEntry("type", "dcscor:Field").containsEntry("text", "Файл.Автор");
    assertThat(right.get(2)).containsEntry("type", "xs:dateTime")
      .containsEntry("text", "2020-02-02T00:00:00");
    assertThat(right.get(3)).containsEntry("type", "dcsset:DataCompositionTextOutputType")
      .containsEntry("text", "Output");
    assertThat(right.get(4)).containsEntry("type", "v8ui:Color").containsEntry("text", "#FFE4B5");

    // эхо прочитанного снова не меняет файл
    String after = Files.readString(dcs, StandardCharsets.UTF_8);
    DcsSettingsEdit.setFilter(dcs, SchemaVersion.V2_20, "Вспомогательный",
      Map.of("path", List.of(), "items", filterItems(settings(dcs, "Вспомогательный"))));
    assertThat(Files.readString(dcs, StandardCharsets.UTF_8)).isEqualTo(after);
  }

  // ------------------------------------------------------------------ opaque-значения

  @Test
  void opaqueRightIsCarriedUnchangedAndRefusedOnChange() throws Exception {
    // правое значение с типом вне белого списка записи: в ssl31 таких нет, кейс готовится правкой копии
    Path dcs = copySchema(DEMO_FILES);
    Files.writeString(dcs, replaceFirst(Files.readString(dcs, StandardCharsets.UTF_8),
      "<dcsset:right xsi:type=\"xs:dateTime\">2015-01-01T00:00:00</dcsset:right>",
      "<dcsset:right xsi:type=\"v8ui:ChartLabelType\">Value</dcsset:right>"), StandardCharsets.UTF_8);
    String before = Files.readString(dcs, StandardCharsets.UTF_8);
    List<Map<String, Object>> items = filterItems(settings(dcs, "Вспомогательный"));
    Map<String, Object> value = map(list(map(list(items.get(3).get("items")).get(0)).get("right")).get(0));
    assertThat(value).containsEntry("type", "v8ui:ChartLabelType")
      .containsEntry("text", "Value")
      .containsEntry("opaque", true);

    // неизменённый элемент с opaque-значением переносится исходными байтами
    DcsSettingsEdit.setFilter(dcs, SchemaVersion.V2_20, "Вспомогательный",
      Map.of("path", List.of(), "items", items));
    assertThat(Files.readString(dcs, StandardCharsets.UTF_8)).isEqualTo(before);

    // правка элемента с opaque-значением внутри - отказ всей мутации, файл не тронут
    items.get(3).put("use", true);
    assertThatThrownBy(() -> DcsSettingsEdit.setFilter(dcs, SchemaVersion.V2_20, "Вспомогательный",
      Map.of("path", List.of(), "items", items)))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("только для чтения");
    assertThat(Files.readString(dcs, StandardCharsets.UTF_8)).isEqualTo(before);

    // новое правое значение нечитаемого типа - тот же отказ
    assertThatThrownBy(() -> DcsSettingsEdit.setFilter(dcs, SchemaVersion.V2_20, "Вспомогательный",
      Map.of("path", List.of(), "items", List.of(Map.of("kind", "comparison", "left", "Тип",
        "right", List.of(Map.of("type", "v8ui:Font", "attrs", Map.of("faceName", "Arial"))))))))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("только для чтения");
    assertThat(Files.readString(dcs, StandardCharsets.UTF_8)).isEqualTo(before);
  }

  // ------------------------------------------------------------------ создание секции

  @Test
  void createsMissingFilterSectionBySequenceOfNodeKind() throws Exception {
    Path dcs = copySchema(LOG_ANALYSIS);
    String before = Files.readString(dcs, StandardCharsets.UTF_8);
    String variant = "АнализАктивностиПользователей";

    // у корня варианта отбора нет: по xs:sequence Settings он встаёт ПЕРЕД dataParameters
    DcsSettingsEdit.setFilter(dcs, SchemaVersion.V2_20, variant, Map.of("path", List.of(),
      "items", List.of(Map.of("kind", "comparison", "left", "Пользователь",
        "comparisonType", "Filled", "use", false))));
    assertThat(Files.readString(dcs, StandardCharsets.UTF_8)).isEqualTo(replaceFirst(before,
      "\t\t\t<dcsset:dataParameters>",
      "\t\t\t<dcsset:filter>\r\n"
        + "\t\t\t\t<dcsset:item xsi:type=\"dcsset:FilterItemComparison\">\r\n"
        + "\t\t\t\t\t<dcsset:use>false</dcsset:use>\r\n"
        + "\t\t\t\t\t<dcsset:left xsi:type=\"dcscor:Field\">Пользователь</dcsset:left>\r\n"
        + "\t\t\t\t\t<dcsset:comparisonType>Filled</dcsset:comparisonType>\r\n"
        + "\t\t\t\t</dcsset:item>\r\n"
        + "\t\t\t</dcsset:filter>\r\n"
        + "\t\t\t<dcsset:dataParameters>"));

    // снятие созданной секции возвращает файл байт в байт
    DcsSettingsEdit.setFilter(dcs, SchemaVersion.V2_20, variant,
      Map.of("path", List.of(), "items", List.of()));
    assertThat(Files.readString(dcs, StandardCharsets.UTF_8)).isEqualTo(before);

    // у группировки (строка таблицы) место другое: отбор - после groupItems, перед order
    List<Map<String, Object>> rowPath = List.of(
      Map.of("list", "structure", "index", 0), Map.of("list", "rows", "index", 0));
    DcsSettingsEdit.setFilter(dcs, SchemaVersion.V2_20, variant, Map.of("path", rowPath,
      "items", List.of(Map.of("kind", "comparison", "left", "Пользователь", "comparisonType", "Filled"))));
    assertThat(Files.readString(dcs, StandardCharsets.UTF_8)).isEqualTo(insertAfterFirst(before,
      "\t\t\t\t\t</dcsset:groupItems>\r\n",
      "\t\t\t\t\t<dcsset:filter>\r\n"
        + "\t\t\t\t\t\t<dcsset:item xsi:type=\"dcsset:FilterItemComparison\">\r\n"
        + "\t\t\t\t\t\t\t<dcsset:left xsi:type=\"dcscor:Field\">Пользователь</dcsset:left>\r\n"
        + "\t\t\t\t\t\t\t<dcsset:comparisonType>Filled</dcsset:comparisonType>\r\n"
        + "\t\t\t\t\t\t</dcsset:item>\r\n"
        + "\t\t\t\t\t</dcsset:filter>\r\n"));
    DcsSettingsEdit.setFilter(dcs, SchemaVersion.V2_20, variant, Map.of("path", rowPath, "items", List.of()));
    assertThat(Files.readString(dcs, StandardCharsets.UTF_8)).isEqualTo(before);
  }

  // ------------------------------------------------------------------ отказы

  @Test
  void refusalsKeepFileIntact() throws Exception {
    Path dcs = copySchema(DEMO_FILES);
    String before = Files.readString(dcs, StandardCharsets.UTF_8);
    List<Map<String, Object>> root = List.of();
    String variant = "Вспомогательный";

    // секция не по виду узла: у таблицы отбора нет
    Path logAnalysis = copySchema(LOG_ANALYSIS);
    String logBefore = Files.readString(logAnalysis, StandardCharsets.UTF_8);
    assertThatThrownBy(() -> DcsSettingsEdit.setFilter(logAnalysis, SchemaVersion.V2_20,
      "АнализАктивностиПользователей",
      Map.of("path", List.of(Map.of("list", "structure", "index", 0)), "items", List.of())))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("нет секции отбора").hasMessageContaining("таблица");
    assertThat(Files.readString(logAnalysis, StandardCharsets.UTF_8)).isEqualTo(logBefore);

    // элементы: неизвестный ключ, вид, свойство не по виду, лексемы и формы значений
    assertThatThrownBy(() -> DcsSettingsEdit.setFilter(dcs, SchemaVersion.V2_20, variant,
      Map.of("path", root, "items", List.of(Map.of("kind", "comparison", "left", "Тип", "uuid", "x")))))
      .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Неизвестное свойство элемента отбора");
    assertThatThrownBy(() -> DcsSettingsEdit.setFilter(dcs, SchemaVersion.V2_20, variant,
      Map.of("path", root, "items", List.of(Map.of("kind", "folder")))))
      .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("должен быть одним из");
    assertThatThrownBy(() -> DcsSettingsEdit.setFilter(dcs, SchemaVersion.V2_20, variant,
      Map.of("path", root, "items", List.of(Map.of("kind", "group", "left", "Тип")))))
      .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("У группы отбора нет свойства left");
    assertThatThrownBy(() -> DcsSettingsEdit.setFilter(dcs, SchemaVersion.V2_20, variant,
      Map.of("path", root, "items", List.of(Map.of("kind", "comparison", "left", "Тип", "groupType", "OrGroup")))))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("У сравнения отбора нет свойства groupType");
    assertThatThrownBy(() -> DcsSettingsEdit.setFilter(dcs, SchemaVersion.V2_20, variant,
      Map.of("path", root, "items", List.of(Map.of("kind", "comparison")))))
      .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("не задан left");
    assertThatThrownBy(() -> DcsSettingsEdit.setFilter(dcs, SchemaVersion.V2_20, variant,
      Map.of("path", root, "items", List.of(Map.of("kind", "comparison", "left", "Тип",
        "comparisonType", "Около")))))
      .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("comparisonType");
    assertThatThrownBy(() -> DcsSettingsEdit.setFilter(dcs, SchemaVersion.V2_20, variant,
      Map.of("path", root, "items", List.of(Map.of("kind", "comparison", "left", "Тип",
        "application", "Везде")))))
      .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("application");
    assertThatThrownBy(() -> DcsSettingsEdit.setFilter(dcs, SchemaVersion.V2_20, variant,
      Map.of("path", root, "items", List.of(Map.of("kind", "comparison", "left", "Тип",
        "right", "всё")))))
      .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("массивом значений");
    assertThatThrownBy(() -> DcsSettingsEdit.setFilter(dcs, SchemaVersion.V2_20, variant,
      Map.of("path", root, "items", List.of(Map.of("kind", "comparison", "left", "Тип",
        "right", List.of("пусто"))))))
      .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("объектом Value");
    assertThatThrownBy(() -> DcsSettingsEdit.setFilter(dcs, SchemaVersion.V2_20, variant,
      Map.of("path", root, "items", List.of(Map.of("kind", "comparison", "left", "Тип",
        "userSettingID", 5)))))
      .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("userSettingID");

    // значения: неизвестное свойство, нет типа, лексика скаляров, границы периода, список
    assertThatThrownBy(() -> setRight(dcs, variant, Map.of("type", "xs:string", "uuid", "x")))
      .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Неизвестное свойство значения");
    assertThatThrownBy(() -> setRight(dcs, variant, Map.of("text", "х")))
      .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("не задан type");
    assertThatThrownBy(() -> setRight(dcs, variant, Map.of("type", "xs:boolean", "text", "да")))
      .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("true|false");
    assertThatThrownBy(() -> setRight(dcs, variant, Map.of("type", "xs:decimal", "text", "пять")))
      .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("не является числом");
    assertThatThrownBy(() -> setRight(dcs, variant, Map.of("type", "xs:dateTime", "text", "2015")))
      .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("датой вида");
    assertThatThrownBy(() -> setRight(dcs, variant, Map.of("type", "dcscor:Field", "text", " ")))
      .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("не задан text");
    assertThatThrownBy(() -> setRight(dcs, variant,
      Map.of("type", "dcsset:DataCompositionTextOutputType", "text", "не лексема")))
      .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("лексемой формата");
    assertThatThrownBy(() -> setRight(dcs, variant, Map.of("type", "v8:LocalStringType", "text", "х")))
      .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("decoded");
    assertThatThrownBy(() -> setRight(dcs, variant,
      Map.of("type", "v8:StandardPeriod", "period", Map.of("variant", "Вчера"))))
      .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("StandardPeriodVariant");
    assertThatThrownBy(() -> setRight(dcs, variant, Map.of("type", "v8:StandardPeriod",
      "period", Map.of("variant", "ThisMonth", "startDate", "2014-01-01T00:00:00"))))
      .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("только при variant=Custom");
    assertThatThrownBy(() -> setRight(dcs, variant, Map.of("type", "v8:StandardBeginningDate",
      "beginningDate", Map.of("variant", "BeginningOfThisDay", "date", "2014-01-01T00:00:00"))))
      .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("только при variant=Custom");
    assertThatThrownBy(() -> setRight(dcs, variant, Map.of("type", "v8:ValueListType",
      "list", Map.of("items", List.of(Map.of("value", Map.of("type", "xs:string", "text", "х"),
        "picture", "х"))))))
      .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("элемента списка значений");
    assertThatThrownBy(() -> setRight(dcs, variant, Map.of("type", "v8:ValueListType",
      "list", Map.of("items", List.of(Map.of("value",
        Map.of("type", "v8:StandardPeriod", "period", Map.of("variant", "ThisMonth"))))))))
      .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("только простые скаляры");
    assertThat(Files.readString(dcs, StandardCharsets.UTF_8)).isEqualTo(before);
  }

  /** Попытка записать одно правое значение нового сравнения корневого отбора. */
  private void setRight(Path dcs, String variant, Map<String, Object> value) throws Exception {
    DcsSettingsEdit.setFilter(dcs, SchemaVersion.V2_20, variant, Map.of("path", List.of(),
      "items", List.of(Map.of("kind", "comparison", "left", "Тип", "right", List.of(value)))));
  }

  // ------------------------------------------------------------------ канал apply-mutation

  @Test
  void applyMutationChannelRunsSetFilter() throws Exception {
    // отказ доходит кодом 2, файл не меняется: у таблицы нет отбора
    Path logAnalysis = copySchema(LOG_ANALYSIS);
    String logBefore = Files.readString(logAnalysis, StandardCharsets.UTF_8);
    assertThat(applyMutation(Map.of(
      "op", "cf-dcs-set-filter", "objectXml", logAnalysis.toString(), "schemaVersion", "V2_20",
      "variant", "АнализАктивностиПользователей",
      "payloadJson", "{\"path\":[{\"list\":\"structure\",\"index\":0}],\"items\":[]}"))).isEqualTo(2);
    assertThat(Files.readString(logAnalysis, StandardCharsets.UTF_8)).isEqualTo(logBefore);

    // эхо прочитанного через JSON-канал (числа приходят дробными) не меняет файл байт в байт
    Path dcs = copySchema(QUESTIONNAIRE);
    String before = Files.readString(dcs, StandardCharsets.UTF_8);
    String variant = "ПросмотрОтветовПростыеВопросы";
    List<Map<String, Object>> items = filterItems(settings(dcs, variant));
    assertThat(applyMutation(Map.of(
      "op", "cf-dcs-set-filter", "objectXml", dcs.toString(), "schemaVersion", "V2_20",
      "variant", variant,
      "payloadJson", new Gson().toJson(Map.of("path", List.of(), "items", items))))).isZero();
    assertThat(Files.readString(dcs, StandardCharsets.UTF_8)).isEqualTo(before);

    // смена вида сравнения: переписан один элемент, прочий файл нетронут
    itemByLeft(items, "Ответ").put("comparisonType", "Filled");
    assertThat(applyMutation(Map.of(
      "op", "cf-dcs-set-filter", "objectXml", dcs.toString(), "schemaVersion", "V2_20",
      "variant", variant,
      "payloadJson", new Gson().toJson(Map.of("path", List.of(), "items", items))))).isZero();
    assertThat(Files.readString(dcs, StandardCharsets.UTF_8)).isEqualTo(replaceFirst(before,
      "\t\t\t\t\t<dcsset:left xsi:type=\"dcscor:Field\">Ответ</dcsset:left>\r\n"
        + "\t\t\t\t\t<dcsset:comparisonType>Equal</dcsset:comparisonType>\r\n",
      "\t\t\t\t\t<dcsset:left xsi:type=\"dcscor:Field\">Ответ</dcsset:left>\r\n"
        + "\t\t\t\t\t<dcsset:comparisonType>Filled</dcsset:comparisonType>\r\n"));
    assertThat(itemByLeft(filterItems(settings(dcs, variant)), "Ответ"))
      .containsEntry("comparisonType", "Filled");
  }

  // ------------------------------------------------------------------ помощники

  /** Настройки варианта из cf-dcs-settings-info. */
  private Map<String, Object> settings(Path dcs, String variant) throws Exception {
    return map(variants(info(dcs, variant)).get(0).get("settings"));
  }

  /** Элементы отбора узла глубоко изменяемой копией; нет секции - пустой список. */
  private static List<Map<String, Object>> filterItems(Map<String, Object> node) {
    Object section = node.get("filter");
    Object items = section == null ? null : map(section).get("items");
    return items == null ? new ArrayList<>() : list(deepMutable(items));
  }

  /** Первое сравнение с данным left; нет такого - тест падает. */
  private static Map<String, Object> itemByLeft(List<Map<String, Object>> items, String left) {
    for (Map<String, Object> item : items) {
      if (left.equals(item.get("left"))) {
        return item;
      }
    }
    throw new AssertionError("в отборе нет сравнения с left " + left);
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

  /** Ожидание: блок вставлен сразу после первого вхождения якоря. */
  private static String insertAfterFirst(String text, String anchor, String block) {
    int at = text.indexOf(anchor);
    assertThat(at).isNotNegative();
    int from = at + anchor.length();
    return text.substring(0, from) + block + text.substring(from);
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
