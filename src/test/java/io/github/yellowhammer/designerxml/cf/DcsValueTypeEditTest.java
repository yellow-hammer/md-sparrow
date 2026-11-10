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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Запись типа значения ({@code valueType}) мутациями {@code cf-dcs-set-field},
 * {@code cf-dcs-set-calculated-field}, {@code cf-dcs-set-parameter} и {@code cf-dcs-add-parameter},
 * а у параметра — значения и признаков рядом с типом.
 *
 * <p>Главная проверка — эхо: каждый блок типа корпуса ssl31, который адресуют мутации, читается
 * {@code cf-dcs-info} и уходит обратно как есть, и ни один файл не меняется ни на байт. Писатель
 * сверяется с байтами платформы на всех 732 блоках (и на 729 блоках EDT): тип, прочитанный из
 * блока, записывается в точности теми же байтами. Целевые правки проверяют форму записи
 * ({@code d4p1}/{@code d5p1} по глубине, порядок, квалификаторы), отказы — что диск не тронут,
 * а после каждой правки схема проходит строгую {@code cf-dcs-validate}.
 */
class DcsValueTypeEditTest {

  private static final String FILES =
    "src/cf/Reports/_ДемоФайлы/Templates/ОсновнаяСхемаКомпоновкиДанных/Ext/Template.xml";

  private static final String EVENT_LOG =
    "src/cf/Reports/АнализЖурналаРегистрации/Templates/ОсновнаяСхемаКомпоновкиДанных/Ext/Template.xml";

  private static final String HANDLERS =
    "src/cf/CommonTemplates/ПолучениеДереваОбработчиковОбновления/Ext/Template.xml";

  private static final String UNION =
    "src/cf/Reports/ОбъемНенужныхФайлов/Templates/ОсновнаяСхемаКомпоновкиДанных/Ext/Template.xml";

  private static final String EDT_EVENT_LOG =
    "Reports/АнализЖурналаРегистрации/Templates/ОсновнаяСхемаКомпоновкиДанных/Template.dcs";

  private static final String CONFIG = "http://v8.1c.ru/8.1/data/enterprise/current-config";

  private static final Pattern VALUE_TYPE_BLOCK = Pattern.compile("<valueType>.*?</valueType>", Pattern.DOTALL);

  private static final Gson GSON = new Gson();

  @TempDir
  Path tempDir;

  // --------------------------------------------------------------- корпус целиком

  @Test
  void эхоВсехАдресуемыхБлоковТипаКорпусаНеМеняетНиБайта() throws Exception {
    List<Path> schemas = schemaFiles(Ssl31SubmodulePaths.projectRoot().resolve("src"));
    assertThat(schemas).hasSize(74);
    // 732 блока: 698 адресуют мутации, 2 — поля вложенных наборов объединения, 32 — во вложенных схемах
    assertThat(echoCorpus(schemas, "Template.xml")).containsExactly(698, 2);
    // Пара EDT (729 блоков): тот же код по Template.dcs
    List<Path> edt = schemaFiles(Path.of(System.getProperty("fixtures.ssl31edt.root")));
    assertThat(echoCorpus(edt, "Template.dcs")).containsExactly(695, 2);
  }

  /**
   * Эхо каждого адресуемого блока типа (у параметров — вместе с записанными признаками) в копиях
   * схем; файлы не должны измениться ни на байт.
   *
   * @return {отправлено эхом, отказов у полей вложенных наборов объединения}
   */
  private int[] echoCorpus(List<Path> schemas, String fileName) throws Exception {
    int echoed = 0;
    int unionItems = 0;
    int index = 0;
    for (Path schema : schemas) {
      Path copy = Files.createTempDirectory(tempDir, "echo" + index++).resolve(fileName);
      Files.copy(schema, copy);
      byte[] before = Files.readAllBytes(copy);
      Map<String, Object> info = DcsRead.info(copy, SchemaVersion.V2_20);
      for (Map<String, Object> parameter : rows(info, "parameters")) {
        Map<String, Object> payload = new LinkedHashMap<>();
        for (String key : List.of("valueType", "valueListAllowed", "availableAsField", "denyIncompleteValues")) {
          if (parameter.containsKey(key)) {
            payload.put(key, parameter.get(key));
          }
        }
        if (!payload.isEmpty()) {
          DcsEdit.setParameter(copy, SchemaVersion.V2_20, (String) parameter.get("name"), channel(payload));
        }
        if (parameter.containsKey("valueType")) {
          echoed++;
        }
      }
      for (Map<String, Object> field : rows(info, "calculatedFields")) {
        if (field.containsKey("valueType")) {
          DcsEdit.setCalculatedField(copy, SchemaVersion.V2_20, (String) field.get("name"), echo(field));
          echoed++;
        }
      }
      for (Map<String, Object> dataSet : rows(info, "dataSets")) {
        for (Map<String, Object> field : fields(dataSet)) {
          if (field.containsKey("valueType")) {
            DcsEdit.setField(copy, SchemaVersion.V2_20, (String) dataSet.get("name"),
              (String) field.get("dataPath"), echo(field));
            echoed++;
          }
        }
        for (Map<String, Object> item : rows(dataSet, "items")) {
          for (Map<String, Object> field : fields(item)) {
            if (field.containsKey("valueType")) {
              assertThatThrownBy(() -> DcsEdit.setField(copy, SchemaVersion.V2_20, (String) item.get("name"),
                (String) field.get("dataPath"), echo(field)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("только для чтения");
              unionItems++;
            }
          }
        }
      }
      assertThat(Files.readAllBytes(copy)).as(schema.toString()).isEqualTo(before);
    }
    return new int[] {echoed, unionItems};
  }

  @Test
  void писательДаётБайтыПлатформыНаВсехБлокахКорпусов() throws Exception {
    assertThat(rewriteMismatches(schemaFiles(Ssl31SubmodulePaths.projectRoot().resolve("src")), 732)).isEmpty();
    assertThat(rewriteMismatches(schemaFiles(Path.of(System.getProperty("fixtures.ssl31edt.root"))), 729))
      .isEmpty();
  }

  @Test
  void эхоВВыгрузкеСИменамиОпределяемыхТиповНеМеняетФайл() throws Exception {
    Path dcs = inDump(FILES);
    byte[] before = Files.readAllBytes(dcs);
    Map<String, Object> folder = byKey(rows(DcsRead.info(dcs, SchemaVersion.V2_20), "parameters"), "name", "Папка");
    assertThat(valueType(folder).get("definedTypes")).isEqualTo(List.of("ВладелецФайлов"));
    DcsEdit.setParameter(dcs, SchemaVersion.V2_20, "Папка", echo(folder));
    // Тот же тип одним именем: UUID находится по выгрузке, тип тот же — файл не трогается
    DcsEdit.setParameter(dcs, SchemaVersion.V2_20, "Папка",
      Map.of("valueType", Map.of("definedTypes", List.of("ВладелецФайлов"))));
    // Тот же тип с числами квалификатора числом JSON и перечислением текстом файла
    DcsEdit.setParameter(dcs, SchemaVersion.V2_20, "Размер", channel(Map.of("valueType", Map.of(
      "types", List.of("xs:decimal"),
      "numberQualifiers", Map.of("digits", 25, "fractionDigits", 0, "allowedSign", "Nonnegative")))));
    assertThat(Files.readAllBytes(dcs)).isEqualTo(before);
  }

  // --------------------------------------------------------------- параметры

  @Test
  void сменаТипаПараметраСоЗначениемИКругОбратноБайтВБайт() throws Exception {
    Path dcs = inDump(FILES);
    String original = Files.readString(dcs, StandardCharsets.UTF_8);
    Map<String, Object> size = byKey(rows(DcsRead.info(dcs, SchemaVersion.V2_20), "parameters"), "name", "Размер");
    Map<String, Object> string = Map.of("types", List.of("xs:string"),
      "stringQualifiers", Map.of("length", "100", "allowedLength", "VARIABLE"));

    // Значение 0 типа xs:decimal к строке не подходит: без value в payload — отказ, диск не тронут
    assertThatThrownBy(() -> DcsEdit.setParameter(dcs, SchemaVersion.V2_20, "Размер", Map.of("valueType", string)))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("Значение параметра не подходит к новому типу");
    assertThat(Files.readString(dcs, StandardCharsets.UTF_8)).isEqualTo(original);
    // Значение другого типа в payload — тоже отказ
    assertThatThrownBy(() -> DcsEdit.setParameter(dcs, SchemaVersion.V2_20, "Размер",
      Map.of("valueType", string, "value", Map.of("type", "xs:decimal", "text", "5"))))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("не подходит к его типу");
    assertThat(Files.readString(dcs, StandardCharsets.UTF_8)).isEqualTo(original);

    DcsEdit.setParameter(dcs, SchemaVersion.V2_20, "Размер",
      Map.of("valueType", string, "value", Map.of("type", "xs:string", "text", "")));
    String edited = Files.readString(dcs, StandardCharsets.UTF_8);
    assertOnlyParameterChanged(original, edited, "Размер");
    assertThat(edited).contains("\t\t<name>Размер</name>\r\n").contains("\t\t<valueType>\r\n"
      + "\t\t\t<v8:Type>xs:string</v8:Type>\r\n"
      + "\t\t\t<v8:StringQualifiers>\r\n"
      + "\t\t\t\t<v8:Length>100</v8:Length>\r\n"
      + "\t\t\t\t<v8:AllowedLength>Variable</v8:AllowedLength>\r\n"
      + "\t\t\t</v8:StringQualifiers>\r\n"
      + "\t\t</valueType>\r\n"
      + "\t\t<value xsi:type=\"xs:string\"/>\r\n"
      + "\t\t<useRestriction>false</useRestriction>\r\n");
    assertStrict(dcs);
    Map<String, Object> read = valueType(byKey(rows(DcsRead.info(dcs, SchemaVersion.V2_20), "parameters"),
      "name", "Размер"));
    assertThat(read.get("types")).isEqualTo(List.of("xs:string"));
    assertThat(read.get("stringQualifiers")).isEqualTo(Map.of("length", "100", "allowedLength", "VARIABLE"));

    // Полный круг: прежний тип и прежнее значение — файл байт в байт как был
    DcsEdit.setParameter(dcs, SchemaVersion.V2_20, "Размер", Map.of(
      "valueType", channelObject(size.get("valueType")), "value", Map.of("type", "xs:decimal", "text", "0")));
    assertThat(Files.readString(dcs, StandardCharsets.UTF_8)).isEqualTo(original);
  }

  @Test
  void составнойТипПараметраИПоляПолучаетПрефиксПоГлубине() throws Exception {
    Path dcs = inDump(FILES);
    String original = Files.readString(dcs, StandardCharsets.UTF_8);
    Map<String, Object> info = DcsRead.info(dcs, SchemaVersion.V2_20);

    // Параметр корня: тип на глубине 4 — d4p1; прочитанный порядок сохраняется, новый — в конце
    Map<String, Object> authors = valueType(byKey(rows(info, "parameters"), "name", "АвторыВерсий"));
    List<Object> types = new ArrayList<>(list(authors.get("types")));
    types.add("cfg:CatalogRef._ДемоОрганизации");
    DcsEdit.setParameter(dcs, SchemaVersion.V2_20, "АвторыВерсий", Map.of("valueType", Map.of("types", types)));
    String withParameter = Files.readString(dcs, StandardCharsets.UTF_8);
    assertOnlyParameterChanged(original, withParameter, "АвторыВерсий");
    assertThat(withParameter).contains(
      "\t\t\t<v8:Type xmlns:d4p1=\"" + CONFIG + "\">d4p1:CatalogRef.Пользователи</v8:Type>\r\n"
        + "\t\t\t<v8:Type xmlns:d4p1=\"" + CONFIG + "\">d4p1:CatalogRef._ДемоОрганизации</v8:Type>\r\n"
        + "\t\t</valueType>\r\n"
        + "\t\t<useRestriction>false</useRestriction>\r\n");
    assertStrict(dcs);

    // Поле набора верхнего уровня: глубина 5 — d5p1
    DcsEdit.setField(dcs, SchemaVersion.V2_20, "НаборДанных1", "Редактирует", Map.of("valueType", Map.of(
      "types", List.of("cfg:CatalogRef.Пользователи", "cfg:CatalogRef.ВнешниеПользователи"))));
    String withField = Files.readString(dcs, StandardCharsets.UTF_8);
    assertOnlyFieldChanged(withParameter, withField, "НаборДанных1", "Редактирует");
    assertThat(withField).contains("\t\t\t<valueType>\r\n"
      + "\t\t\t\t<v8:Type xmlns:d5p1=\"" + CONFIG + "\">d5p1:CatalogRef.Пользователи</v8:Type>\r\n"
      + "\t\t\t\t<v8:Type xmlns:d5p1=\"" + CONFIG + "\">d5p1:CatalogRef.ВнешниеПользователи</v8:Type>\r\n"
      + "\t\t\t</valueType>\r\n");
    assertStrict(dcs);
    Map<String, Object> field = valueType(byKey(fields(byKey(rows(DcsRead.info(dcs, SchemaVersion.V2_20),
      "dataSets"), "name", "НаборДанных1")), "dataPath", "Редактирует"));
    assertThat(field.get("types")).isEqualTo(List.of("cfg:CatalogRef.Пользователи", "cfg:CatalogRef.ВнешниеПользователи"));
    assertThat(field.get("editable")).isEqualTo(true);
  }

  @Test
  void определяемыйТипПоИмениИСнятиеТипа() throws Exception {
    Path dcs = inDump(FILES);
    String original = Files.readString(dcs, StandardCharsets.UTF_8);

    // Значение nil подходит к любому типу: замена определяемого типа проходит без value
    DcsEdit.setParameter(dcs, SchemaVersion.V2_20, "Папка",
      Map.of("valueType", Map.of("definedTypes", List.of("Подразделение"))));
    String renamed = Files.readString(dcs, StandardCharsets.UTF_8);
    assertOnlyParameterChanged(original, renamed, "Папка");
    assertThat(renamed).contains("\t\t<valueType>\r\n"
      + "\t\t\t<v8:TypeId>92fb25e9-5b34-4411-b876-cd83fa55a6e0</v8:TypeId>\r\n"
      + "\t\t</valueType>\r\n"
      + "\t\t<value xsi:nil=\"true\"/>\r\n");
    Map<String, Object> folder = byKey(rows(DcsRead.info(dcs, SchemaVersion.V2_20), "parameters"), "name", "Папка");
    assertThat(valueType(folder).get("definedTypes")).isEqualTo(List.of("Подразделение"));
    assertStrict(dcs);

    // null снимает тип целиком: пустого <valueType/> платформа не пишет
    Map<String, Object> untyped = new HashMap<>();
    untyped.put("valueType", null);
    DcsEdit.setParameter(dcs, SchemaVersion.V2_20, "Папка", untyped);
    String removed = Files.readString(dcs, StandardCharsets.UTF_8);
    assertSingleEditZone(original, removed);
    assertThat(parameterChildNames(removed, "Папка")).containsExactly("name", "title", "value", "useRestriction");
    assertStrict(dcs);
    // Пустой тип — то же снятие; повтор уже ничего не меняет
    DcsEdit.setParameter(dcs, SchemaVersion.V2_20, "Папка", Map.of("valueType", Map.of("types", List.of())));
    assertThat(Files.readString(dcs, StandardCharsets.UTF_8)).isEqualTo(removed);

    // Вставка типа обратно встаёт на место по xs:sequence, байты — как у платформы
    DcsEdit.setParameter(dcs, SchemaVersion.V2_20, "Папка",
      Map.of("valueType", Map.of("typeIds", List.of("89432067-978f-4e8e-a7dc-cb87badd9341"))));
    assertThat(Files.readString(dcs, StandardCharsets.UTF_8)).isEqualTo(original);
  }

  @Test
  void признакиПараметраПишутсяКакУПлатформыАЭхоНичегоНеМеняет() throws Exception {
    Path dcs = inDump(FILES);
    String original = Files.readString(dcs, StandardCharsets.UTF_8);

    DcsEdit.setParameter(dcs, SchemaVersion.V2_20, "Период",
      Map.of("valueListAllowed", true, "availableAsField", false, "denyIncompleteValues", true));
    String flagged = Files.readString(dcs, StandardCharsets.UTF_8);
    assertSingleEditZone(original, flagged);
    assertThat(flagged).contains("\t\t<useRestriction>false</useRestriction>\r\n"
      + "\t\t<valueListAllowed>true</valueListAllowed>\r\n"
      + "\t\t<availableAsField>false</availableAsField>\r\n"
      + "\t\t<denyIncompleteValues>true</denyIncompleteValues>\r\n"
      + "\t</parameter>");
    assertStrict(dcs);
    Map<String, Object> period = byKey(rows(DcsRead.info(dcs, SchemaVersion.V2_20), "parameters"), "name", "Период");
    assertThat(period).containsEntry("valueListAllowed", true).containsEntry("availableAsField", false)
      .containsEntry("denyIncompleteValues", true);

    // Эхо прочитанного — ни байта
    DcsEdit.setParameter(dcs, SchemaVersion.V2_20, "Период",
      Map.of("valueListAllowed", true, "availableAsField", false, "denyIncompleteValues", true));
    assertThat(Files.readString(dcs, StandardCharsets.UTF_8)).isEqualTo(flagged);

    // Значения по умолчанию снимают элементы: availableAsField=true, прочие — false либо null
    Map<String, Object> defaults = new HashMap<>();
    defaults.put("valueListAllowed", null);
    defaults.put("availableAsField", true);
    defaults.put("denyIncompleteValues", false);
    DcsEdit.setParameter(dcs, SchemaVersion.V2_20, "Период", defaults);
    assertThat(Files.readString(dcs, StandardCharsets.UTF_8)).isEqualTo(original);
    // Умолчание при отсутствии элемента — тоже без правки
    DcsEdit.setParameter(dcs, SchemaVersion.V2_20, "Период", Map.of("availableAsField", true));
    assertThat(Files.readString(dcs, StandardCharsets.UTF_8)).isEqualTo(original);

    assertThatThrownBy(() -> DcsEdit.setParameter(dcs, SchemaVersion.V2_20, "Период",
      Map.of("valueListAllowed", "да")))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("булевым");
  }

  @Test
  void значениеПараметраNilСнятиеИUuid() throws Exception {
    Path dcs = inDump(FILES);
    String original = Files.readString(dcs, StandardCharsets.UTF_8);
    // Эхо значения теми же байтами (многострочный стандартный период) файл не трогает
    DcsEdit.setParameter(dcs, SchemaVersion.V2_20, "Период", Map.of("value",
      Map.of("type", "v8:StandardPeriod", "period", Map.of("variant", "Custom"))));
    assertThat(Files.readString(dcs, StandardCharsets.UTF_8)).isEqualTo(original);

    DcsEdit.setParameter(dcs, SchemaVersion.V2_20, "Размер", Map.of("value", Map.of("nil", true)));
    String nil = Files.readString(dcs, StandardCharsets.UTF_8);
    assertOnlyParameterChanged(original, nil, "Размер");
    assertThat(nil).contains("\t\t</valueType>\r\n\t\t<value xsi:nil=\"true\"/>\r\n\t\t<useRestriction>false");
    // null — тоже «значение не задано»: у параметра без списка значений платформа хранит xsi:nil, а не
    // снимает элемент, поэтому повтор после {nil: true} ничего не меняет
    Map<String, Object> clear = new HashMap<>();
    clear.put("value", null);
    DcsEdit.setParameter(dcs, SchemaVersion.V2_20, "Размер", clear);
    assertThat(Files.readString(dcs, StandardCharsets.UTF_8)).isEqualTo(nil);
    DcsEdit.setParameter(dcs, SchemaVersion.V2_20, "Размер", Map.of("value", Map.of("type", "xs:decimal", "text", "0")));
    assertThat(Files.readString(dcs, StandardCharsets.UTF_8)).isEqualTo(original);
    DcsEdit.setParameter(dcs, SchemaVersion.V2_20, "Размер", clear);
    assertThat(Files.readString(dcs, StandardCharsets.UTF_8)).isEqualTo(nil);
    assertThat(parameterChildNames(nil, "Размер"))
      .containsExactly("name", "title", "valueType", "value", "useRestriction", "availableValue", "availableValue",
        "availableValue");
    DcsEdit.setParameter(dcs, SchemaVersion.V2_20, "Размер", Map.of("value", Map.of("type", "xs:decimal", "text", "0")));
    assertThat(Files.readString(dcs, StandardCharsets.UTF_8)).isEqualTo(original);
    assertStrict(dcs);

    // v8:UUID белый список настроек не знает, у параметра схемы он бывает
    Path handlers = inDump(HANDLERS);
    String before = Files.readString(handlers, StandardCharsets.UTF_8);
    String name = (String) rows(DcsRead.info(handlers, SchemaVersion.V2_20), "parameters").stream()
      .filter(row -> row.containsKey("valueType")
        && List.of("v8:UUID").equals(valueType(row).get("types")))
      .findFirst().orElseThrow().get("name");
    DcsEdit.setParameter(handlers, SchemaVersion.V2_20, name,
      Map.of("value", Map.of("type", "v8:UUID", "text", "00000000-0000-0000-0000-000000000000")));
    assertThat(Files.readString(handlers, StandardCharsets.UTF_8)).isEqualTo(before);
    DcsEdit.setParameter(handlers, SchemaVersion.V2_20, name,
      Map.of("value", Map.of("type", "v8:UUID", "text", "92fb25e9-5b34-4411-b876-cd83fa55a6e0")));
    assertThat(Files.readString(handlers, StandardCharsets.UTF_8))
      .contains("<value xsi:type=\"v8:UUID\">92fb25e9-5b34-4411-b876-cd83fa55a6e0</value>");
    assertStrict(handlers);
    assertThatThrownBy(() -> DcsEdit.setParameter(handlers, SchemaVersion.V2_20, name,
      Map.of("value", Map.of("type", "v8:UUID", "text", "не-uuid"))))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("v8:UUID");
  }

  @Test
  void новыйПараметрСразуСТипомИЗначением() throws Exception {
    Path dcs = inDump(FILES);
    String original = Files.readString(dcs, StandardCharsets.UTF_8);
    DcsEdit.addParameter(dcs, SchemaVersion.V2_20, Map.of(
      "name", "ПериодОтчета",
      "valueType", Map.of("types", List.of("v8:StandardPeriod")),
      "value", Map.of("type", "v8:StandardPeriod", "period", Map.of("variant", "Custom")),
      "useRestriction", false));
    String added = Files.readString(dcs, StandardCharsets.UTF_8);
    assertSingleEditZone(original, added);
    // Та же форма, что у параметра «Период», записанного платформой
    assertThat(added).contains("\t<parameter>\r\n"
      + "\t\t<name>ПериодОтчета</name>\r\n"
      + "\t\t<valueType>\r\n"
      + "\t\t\t<v8:Type>v8:StandardPeriod</v8:Type>\r\n"
      + "\t\t</valueType>\r\n"
      + "\t\t<value xsi:type=\"v8:StandardPeriod\">\r\n"
      + "\t\t\t<v8:variant xsi:type=\"v8:StandardPeriodVariant\">Custom</v8:variant>\r\n"
      + "\t\t\t<v8:startDate>0001-01-01T00:00:00</v8:startDate>\r\n"
      + "\t\t\t<v8:endDate>0001-01-01T00:00:00</v8:endDate>\r\n"
      + "\t\t</value>\r\n"
      + "\t\t<useRestriction>false</useRestriction>\r\n"
      + "\t</parameter>\r\n");
    assertStrict(dcs);

    DcsEdit.addParameter(dcs, SchemaVersion.V2_20, Map.of(
      "name", "Организация",
      "valueType", Map.of("types", List.of("cfg:CatalogRef._ДемоОрганизации")),
      "value", Map.of("nil", true)));
    assertThat(Files.readString(dcs, StandardCharsets.UTF_8)).contains("\t\t<name>Организация</name>\r\n"
      + "\t\t<valueType>\r\n"
      + "\t\t\t<v8:Type xmlns:d4p1=\"" + CONFIG + "\">d4p1:CatalogRef._ДемоОрганизации</v8:Type>\r\n"
      + "\t\t</valueType>\r\n"
      + "\t\t<value xsi:nil=\"true\"/>\r\n"
      + "\t\t<useRestriction>false</useRestriction>\r\n"
      + "\t</parameter>");
    assertStrict(dcs);
    // Без value в payload — то же xsi:nil: значение платформа пишет у каждого параметра без списка
    DcsEdit.addParameter(dcs, SchemaVersion.V2_20, Map.of(
      "name", "Контрагент",
      "valueType", Map.of("types", List.of("cfg:CatalogRef._ДемоОрганизации")),
      "useRestriction", true));
    assertThat(Files.readString(dcs, StandardCharsets.UTF_8)).contains("\t\t<name>Контрагент</name>\r\n"
      + "\t\t<valueType>\r\n"
      + "\t\t\t<v8:Type xmlns:d4p1=\"" + CONFIG + "\">d4p1:CatalogRef._ДемоОрганизации</v8:Type>\r\n"
      + "\t\t</valueType>\r\n"
      + "\t\t<value xsi:nil=\"true\"/>\r\n"
      + "\t\t<useRestriction>true</useRestriction>\r\n"
      + "\t</parameter>");

    // Значение чужого типа к новому параметру — отказ, файл прежний
    String before = Files.readString(dcs, StandardCharsets.UTF_8);
    assertThatThrownBy(() -> DcsEdit.addParameter(dcs, SchemaVersion.V2_20, Map.of(
      "name", "Флаг", "valueType", Map.of("types", List.of("xs:boolean")),
      "value", Map.of("type", "xs:string", "text", "да"))))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("не подходит");
    assertThatThrownBy(() -> DcsEdit.addParameter(dcs, SchemaVersion.V2_20, Map.of(
      "name", "Ссылка", "valueType", Map.of("types", List.of("cfg:CatalogRef.НетТакого")))))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("нет в выгрузке");
    assertThat(Files.readString(dcs, StandardCharsets.UTF_8)).isEqualTo(before);
  }

  // --------------------------------------------------------------- поля и вычисляемые поля

  @Test
  void типПоляНабораВставкаЛюбаяСсылкаИСнятие() throws Exception {
    Path dcs = inDump(FILES);
    String original = Files.readString(dcs, StandardCharsets.UTF_8);
    DcsEdit.setField(dcs, SchemaVersion.V2_20, "НаборДанных1", "Зашифрован",
      Map.of("valueType", Map.of("types", List.of("xs:boolean"))));
    String typed = Files.readString(dcs, StandardCharsets.UTF_8);
    assertSingleEditZone(original, typed);
    assertThat(typed).contains("\t\t\t<field>Зашифрован</field>\r\n"
      + "\t\t\t<valueType>\r\n"
      + "\t\t\t\t<v8:Type>xs:boolean</v8:Type>\r\n"
      + "\t\t\t</valueType>\r\n"
      + "\t\t</field>\r\n");
    assertStrict(dcs);

    // «Любая ссылка» выгрузки конфигуратора — AnyIBRef; в паре с заголовком порядок по xs:sequence
    DcsEdit.setField(dcs, SchemaVersion.V2_20, "НаборДанных1", "Зашифрован",
      Map.of("title", "Зашифрован ли", "valueType", Map.of("typeSets", List.of("cfg:AnyIBRef"))));
    String anyRef = Files.readString(dcs, StandardCharsets.UTF_8);
    assertThat(anyRef).contains("\t\t\t<valueType>\r\n"
      + "\t\t\t\t<v8:TypeSet xmlns:d5p1=\"" + CONFIG + "\">d5p1:AnyIBRef</v8:TypeSet>\r\n"
      + "\t\t\t</valueType>\r\n");
    assertThat(fieldChildNames(anyRef, "НаборДанных1", "Зашифрован"))
      .containsExactly("dataPath", "field", "title", "valueType");
    assertStrict(dcs);
    assertThatThrownBy(() -> DcsEdit.setField(dcs, SchemaVersion.V2_20, "НаборДанных1", "Зашифрован",
      Map.of("valueType", Map.of("typeSets", List.of("cfg:AnyRef")))))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("cfg:AnyIBRef");

    Map<String, Object> remove = new HashMap<>();
    remove.put("valueType", null);
    remove.put("title", null);
    DcsEdit.setField(dcs, SchemaVersion.V2_20, "НаборДанных1", "Зашифрован", remove);
    assertThat(Files.readString(dcs, StandardCharsets.UTF_8)).isEqualTo(original);
  }

  @Test
  void типВычисляемогоПоляЗаменаИВставка() throws Exception {
    Path dcs = inDump(HANDLERS);
    String original = Files.readString(dcs, StandardCharsets.UTF_8);
    DcsEdit.setCalculatedField(dcs, SchemaVersion.V2_20, "ВесВерсии", channel(Map.of("valueType", Map.of(
      "types", List.of("xs:decimal"),
      "numberQualifiers", Map.of("digits", 15, "fractionDigits", 2, "allowedSign", "NONNEGATIVE")))));
    String edited = Files.readString(dcs, StandardCharsets.UTF_8);
    assertOnlyCalculatedChanged(original, edited, "ВесВерсии");
    assertThat(edited).contains("\t\t<valueType>\r\n"
      + "\t\t\t<v8:Type>xs:decimal</v8:Type>\r\n"
      + "\t\t\t<v8:NumberQualifiers>\r\n"
      + "\t\t\t\t<v8:Digits>15</v8:Digits>\r\n"
      + "\t\t\t\t<v8:FractionDigits>2</v8:FractionDigits>\r\n"
      + "\t\t\t\t<v8:AllowedSign>Nonnegative</v8:AllowedSign>\r\n"
      + "\t\t\t</v8:NumberQualifiers>\r\n"
      + "\t\t</valueType>\r\n"
      + "\t</calculatedField>");
    assertStrict(dcs);

    // Новое вычисляемое поле без типа: тип встаёт после заголовка, ссылка — с префиксом d4p1
    DcsRead.addCalculatedField(dcs, SchemaVersion.V2_20, "Автор", "Выражение", "Автор");
    String withField = Files.readString(dcs, StandardCharsets.UTF_8);
    DcsEdit.setCalculatedField(dcs, SchemaVersion.V2_20, "Автор", Map.of("valueType", Map.of(
      "types", List.of("xs:string", "cfg:CatalogRef.Пользователи"),
      "stringQualifiers", Map.of("length", 0, "allowedLength", "VARIABLE"))));
    String typed = Files.readString(dcs, StandardCharsets.UTF_8);
    assertSingleEditZone(withField, typed);
    assertThat(typed).contains("\t\t</title>\r\n"
      + "\t\t<valueType>\r\n"
      + "\t\t\t<v8:Type>xs:string</v8:Type>\r\n"
      + "\t\t\t<v8:Type xmlns:d4p1=\"" + CONFIG + "\">d4p1:CatalogRef.Пользователи</v8:Type>\r\n"
      + "\t\t\t<v8:StringQualifiers>\r\n"
      + "\t\t\t\t<v8:Length>0</v8:Length>\r\n"
      + "\t\t\t\t<v8:AllowedLength>Variable</v8:AllowedLength>\r\n"
      + "\t\t\t</v8:StringQualifiers>\r\n"
      + "\t\t</valueType>\r\n"
      + "\t</calculatedField>");
    assertStrict(dcs);
    Map<String, Object> author = valueType(byKey(rows(DcsRead.info(dcs, SchemaVersion.V2_20), "calculatedFields"),
      "name", "Автор"));
    assertThat(author.get("types")).isEqualTo(List.of("xs:string", "cfg:CatalogRef.Пользователи"));
  }

  @Test
  void полеВложенногоНабораОбъединенияТолькоДляЧтения() throws Exception {
    Path dcs = inDump(UNION);
    String original = Files.readString(dcs, StandardCharsets.UTF_8);
    assertThatThrownBy(() -> DcsEdit.setField(dcs, SchemaVersion.V2_20, "ОбъемДанныхВсего", "ОбъемВсего",
      Map.of("valueType", Map.of("types", List.of("xs:boolean")))))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("Набор данных не найден")
      .hasMessageContaining("вложен в объединение")
      .hasMessageContaining("только для чтения");
    assertThat(Files.readString(dcs, StandardCharsets.UTF_8)).isEqualTo(original);
    // Поле самого объединения правится
    Map<String, Object> union = rows(DcsRead.info(dcs, SchemaVersion.V2_20), "dataSets").get(0);
    DcsEdit.setField(dcs, SchemaVersion.V2_20, (String) union.get("name"), "ОбъемВсего",
      echo(byKey(fields(union), "dataPath", "ОбъемВсего")));
    assertThat(Files.readString(dcs, StandardCharsets.UTF_8)).isEqualTo(original);
  }

  // --------------------------------------------------------------- отказы

  @Test
  void отказыБелогоСловаряНеТрогаютДиск() throws Exception {
    Path dcs = inDump(FILES);
    String original = Files.readString(dcs, StandardCharsets.UTF_8);
    Map<String, Object> string = Map.of("length", "10", "allowedLength", "VARIABLE");
    Map<Object, String> cases = new LinkedHashMap<>();
    cases.put(Map.of("types", List.of("xs:int")), "не из словаря");
    cases.put(Map.of("types", List.of("v8:ValueTable")), "не из словаря");
    cases.put(Map.of("types", List.of("cfg:DocumentJournalRef._ДемоЖурналВсехДокументов")), "журнала документов");
    cases.put(Map.of("typeSets", List.of("cfg:DocumentJournalRef")), "журнала документов");
    cases.put(Map.of("types", List.of("cfg:CatalogRef.НетТакого")), "нет в выгрузке");
    cases.put(Map.of("typeSets", List.of("cfg:Characteristic.НетТакого")), "нет в выгрузке");
    cases.put(Map.of("typeSets", List.of("cfg:AnyRef")), "cfg:AnyIBRef");
    cases.put(Map.of("types", List.of("xs:boolean", "xs:boolean")), "дважды");
    cases.put(Map.of("types", List.of("xs:boolean"), "typeIds", List.of("92fb25e9-5b34-4411-b876-cd83fa55a6e0")),
      "без других типов");
    cases.put(Map.of("typeIds", List.of("00000000-0000-0000-0000-000000000001")), "не найден");
    cases.put(Map.of("definedTypes", List.of("НетТакого")), "не найден");
    cases.put(Map.of("typeIds", List.of("92fb25e9-5b34-4411-b876-cd83fa55a6e0"),
      "definedTypes", List.of("ВладелецФайлов")), "не совпадают");
    cases.put(Map.of("types", List.of("xs:boolean"), "stringQualifiers", string), "без типа xs:string");
    cases.put(Map.of("types", List.of("xs:string")), "нужны квалификаторы");
    cases.put(Map.of("types", List.of("xs:dateTime")), "нужны квалификаторы");
    cases.put(Map.of("types", List.of("xs:string"), "stringQualifiers", Map.of("length", "-1",
      "allowedLength", "VARIABLE")), "неотрицательным");
    cases.put(Map.of("types", List.of("xs:string"), "stringQualifiers", Map.of("length", "10")), "allowedLength");
    cases.put(Map.of("types", List.of("xs:string"), "stringQualifiers", Map.of("length", "10",
      "allowedLength", "Любая")), "VARIABLE");
    cases.put(Map.of("types", List.of("xs:decimal"), "numberQualifiers", Map.of("digits", "5",
      "fractionDigits", "6", "allowedSign", "ANY")), "больше разрядности");
    cases.put(Map.of("types", List.of("xs:decimal"), "numberQualifiers", Map.of("digits", "5.5",
      "fractionDigits", "0", "allowedSign", "ANY")), "целым");
    // 65 разрядов загрузка платформы молча обрезает до 64
    cases.put(Map.of("types", List.of("xs:decimal"), "numberQualifiers", Map.of("digits", "65",
      "fractionDigits", "0", "allowedSign", "ANY")), "Разрядность числа (65) больше 64");
    cases.put(Map.of("types", List.of("xs:decimal"), "numberQualifiers", Map.of("digits", "100",
      "fractionDigits", "2", "allowedSign", "ANY")), "больше 64");
    cases.put(Map.of("types", List.of("xs:string"), "stringQualifiers", string,
      "binaryDataQualifiers", Map.of("length", "0", "allowedLength", "VARIABLE")), "двоичных данных");
    cases.put(Map.of("types", List.of("xs:boolean"), "format", "x"), "Неизвестное свойство типа");
    cases.put("xs:boolean", "должно быть объектом");
    for (Map.Entry<Object, String> entry : cases.entrySet()) {
      assertThatThrownBy(() -> DcsEdit.setParameter(dcs, SchemaVersion.V2_20, "АвторыВерсий",
        Map.of("valueType", entry.getKey())))
        .as(String.valueOf(entry.getKey()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining(entry.getValue());
    }
    assertThat(Files.readString(dcs, StandardCharsets.UTF_8)).isEqualTo(original);
  }

  /**
   * Границы квалификаторов, которые хранит загрузка платформы: разрядность до 64 и дробная часть,
   * равная разрядности, хранятся как записаны; длина строки сверху не ограничена — 1024, 1025,
   * 100000 переменной и 1025 фиксированной длины.
   */
  @Test
  void границыКвалификаторовКакУЗагрузкиПлатформы() throws Exception {
    Path dcs = inDump(FILES);
    List<Map<String, Object>> accepted = List.of(
      Map.of("types", List.of("xs:decimal"), "numberQualifiers",
        Map.of("digits", 64, "fractionDigits", 0, "allowedSign", "ANY")),
      Map.of("types", List.of("xs:decimal"), "numberQualifiers",
        Map.of("digits", "38", "fractionDigits", "38", "allowedSign", "ANY")),
      Map.of("types", List.of("xs:decimal"), "numberQualifiers",
        Map.of("digits", "39", "fractionDigits", "0", "allowedSign", "NONNEGATIVE")),
      Map.of("types", List.of("xs:string"), "stringQualifiers", Map.of("length", 1024, "allowedLength", "VARIABLE")),
      Map.of("types", List.of("xs:string"), "stringQualifiers", Map.of("length", 1025, "allowedLength", "VARIABLE")),
      Map.of("types", List.of("xs:string"), "stringQualifiers",
        Map.of("length", "100000", "allowedLength", "VARIABLE")),
      Map.of("types", List.of("xs:string"), "stringQualifiers", Map.of("length", 1025, "allowedLength", "FIXED")));
    int index = 0;
    for (Map<String, Object> type : accepted) {
      String name = "Граница" + index++;
      DcsEdit.addParameter(dcs, SchemaVersion.V2_20, channel(Map.of("name", name, "valueType", type)));
      Map<String, Object> read = valueType(byKey(rows(DcsRead.info(dcs, SchemaVersion.V2_20), "parameters"),
        "name", name));
      assertThat(read.get("types")).as(name).isEqualTo(type.get("types"));
    }
    String text = Files.readString(dcs, StandardCharsets.UTF_8);
    assertThat(text).contains("<v8:Digits>64</v8:Digits>", "<v8:FractionDigits>38</v8:FractionDigits>",
      "<v8:Length>100000</v8:Length>", "<v8:Length>1025</v8:Length>\r\n\t\t\t\t<v8:AllowedLength>Fixed");
    assertStrict(dcs);

    // Добавление с 65 разрядами — отказ кодом 2 канала, файл прежний
    assertThat(applyMutation(Map.of("op", "cf-dcs-add-parameter", "objectXml", dcs.toString(),
      "schemaVersion", "V2_20", "payloadJson", "{\"name\":\"Широкое\",\"valueType\":{\"types\":[\"xs:decimal\"],"
        + "\"numberQualifiers\":{\"digits\":65,\"fractionDigits\":0,\"allowedSign\":\"ANY\"}}}"))).isEqualTo(2);
    assertThat(Files.readString(dcs, StandardCharsets.UTF_8)).isEqualTo(text);
    // Эхо уже записанной в файле разрядности больше 64 — не проверка нового типа, а no-op
    String wide = text.replace("<v8:Digits>64</v8:Digits>", "<v8:Digits>65</v8:Digits>");
    Files.writeString(dcs, wide, StandardCharsets.UTF_8);
    Map<String, Object> written = byKey(rows(DcsRead.info(dcs, SchemaVersion.V2_20), "parameters"), "name", "Граница0");
    DcsEdit.setParameter(dcs, SchemaVersion.V2_20, "Граница0", channel(Map.of("valueType", written.get("valueType"))));
    assertThat(Files.readString(dcs, StandardCharsets.UTF_8)).isEqualTo(wide);
  }

  @Test
  void типВнеСловаряПереноситсяТолькоЭхом() throws Exception {
    Path dcs = inDump(FILES);
    // Тип вне словаря у параметра: так его записал бы конфигуратор
    String text = Files.readString(dcs, StandardCharsets.UTF_8);
    String booleanType = "\t\t\t<v8:Type>xs:boolean</v8:Type>\r\n";
    int at = text.indexOf(booleanType, text.indexOf("<name>ПоказыватьВсеВерсии</name>")) + booleanType.length();
    String original = text.substring(0, at) + "\t\t\t<v8:Type>v8:ValueTable</v8:Type>\r\n" + text.substring(at);
    Files.writeString(dcs, original, StandardCharsets.UTF_8);
    Map<String, Object> parameter = byKey(rows(DcsRead.info(dcs, SchemaVersion.V2_20), "parameters"),
      "name", "ПоказыватьВсеВерсии");
    assertThat(valueType(parameter).get("editable")).isEqualTo(false);
    DcsEdit.setParameter(dcs, SchemaVersion.V2_20, "ПоказыватьВсеВерсии", echo(parameter));
    assertThat(Files.readString(dcs, StandardCharsets.UTF_8)).isEqualTo(original);
    assertThatThrownBy(() -> DcsEdit.setParameter(dcs, SchemaVersion.V2_20, "ПоказыватьВсеВерсии",
      Map.of("valueType", Map.of("types", List.of("v8:ValueTable")))))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("только без изменений");
    assertThat(Files.readString(dcs, StandardCharsets.UTF_8)).isEqualTo(original);
    // Замена целиком на тип словаря — обычная правка
    DcsEdit.setParameter(dcs, SchemaVersion.V2_20, "ПоказыватьВсеВерсии",
      Map.of("valueType", Map.of("types", List.of("xs:boolean"))));
    assertThat(valueType(byKey(rows(DcsRead.info(dcs, SchemaVersion.V2_20), "parameters"), "name",
      "ПоказыватьВсеВерсии")).get("editable")).isEqualTo(true);
    assertStrict(dcs);
  }

  @Test
  void внеВыгрузкиЗаписанныеСсылкиСохраняютсяАНовыеНет() throws Exception {
    // Отдельный файл: выгрузки над схемой нет, состав базы неизвестен
    Path alone = tempDir.resolve("Отдельно").resolve("Template.xml");
    Files.createDirectories(alone.getParent());
    Files.copy(Ssl31SubmodulePaths.projectRoot().resolve(EVENT_LOG), alone);
    String original = Files.readString(alone, StandardCharsets.UTF_8);
    Map<String, Object> main = rows(DcsRead.info(alone, SchemaVersion.V2_20), "dataSets").get(0);
    String set = (String) main.get("name");

    assertThatThrownBy(() -> DcsEdit.setField(alone, SchemaVersion.V2_20, set, "Пользователь",
      Map.of("valueType", Map.of("types", List.of("cfg:CatalogRef.Пользователи")))))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("только макету в выгрузке");
    assertThatThrownBy(() -> DcsEdit.setParameter(alone, SchemaVersion.V2_20, "Подразделение",
      Map.of("valueType", Map.of("definedTypes", List.of("Подразделение")))))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("в выгрузке");
    assertThat(Files.readString(alone, StandardCharsets.UTF_8)).isEqualTo(original);

    // Записанный набор «любой справочник» остаётся, строка снимается; «любая ссылка» — новая, но всегда доступна
    DcsEdit.setField(alone, SchemaVersion.V2_20, set, "Пользователь",
      Map.of("valueType", Map.of("typeSets", List.of("cfg:CatalogRef", "cfg:AnyIBRef"))));
    assertThat(valueType(byKey(fields(rows(DcsRead.info(alone, SchemaVersion.V2_20), "dataSets").get(0)),
      "dataPath", "Пользователь")).get("typeSets")).isEqualTo(List.of("cfg:CatalogRef", "cfg:AnyIBRef"));
    assertStrict(alone);
  }

  // --------------------------------------------------------------- EDT и канал

  @Test
  void каналApplyMutationПравитТипВTemplateDcsПроектаEdt() throws Exception {
    Path project = tempDir.resolve("edt");
    Path source = Path.of(System.getProperty("fixtures.ssl31edt.root"), "ssl31", "src");
    copyFile(source.resolve("Configuration/Configuration.mdo"), project.resolve("src/Configuration/Configuration.mdo"));
    copyFile(source.resolve("DefinedTypes/Подразделение/Подразделение.mdo"),
      project.resolve("src/DefinedTypes/Подразделение/Подразделение.mdo"));
    copyFile(source.resolve("DefinedTypes/Организация/Организация.mdo"),
      project.resolve("src/DefinedTypes/Организация/Организация.mdo"));
    Path dcs = project.resolve("src").resolve(EDT_EVENT_LOG);
    copyFile(source.resolve(EDT_EVENT_LOG), dcs);
    String original = Files.readString(dcs, StandardCharsets.UTF_8);
    String eol = XmlLines.newline(original);
    Map<String, Object> info = DcsRead.info(dcs, SchemaVersion.V2_20);
    Map<String, Object> main = rows(info, "dataSets").get(0);
    String set = (String) main.get("name");
    Map<String, Object> department = byKey(fields(main), "dataPath", "Подразделение");
    assertThat(valueType(department).get("typeSets")).isEqualTo(List.of("cfg:AnyRef"));

    // Эхо поля и параметра (с именем определяемого типа из .mdo) — ни байта
    assertThat(mutate("cf-dcs-set-field", dcs, Map.of("dataSet", set, "dataPath", "Подразделение"),
      echo(department))).isZero();
    assertThat(mutate("cf-dcs-set-parameter", dcs, Map.of("name", "Подразделение"),
      echo(byKey(rows(info, "parameters"), "name", "Подразделение")))).isZero();
    assertThat(Files.readString(dcs, StandardCharsets.UTF_8)).isEqualTo(original);

    // «Любая ссылка» EDT — AnyRef; AnyIBRef выгрузки конфигуратора здесь — отказ кодом 2
    assertThat(mutate("cf-dcs-set-field", dcs, Map.of("dataSet", set, "dataPath", "Подразделение"),
      Map.of("valueType", Map.of("typeSets", List.of("cfg:AnyIBRef"))))).isEqualTo(2);
    assertThat(Files.readString(dcs, StandardCharsets.UTF_8)).isEqualTo(original);
    assertThat(mutate("cf-dcs-set-field", dcs, Map.of("dataSet", set, "dataPath", "Подразделение"),
      Map.of("valueType", Map.of("typeSets", List.of("cfg:AnyRef", "cfg:CatalogRef"))))).isZero();
    String edited = Files.readString(dcs, StandardCharsets.UTF_8);
    assertOnlyFieldChanged(original, edited, set, "Подразделение");
    assertThat(edited).contains("\t\t\t<valueType>" + eol
      + "\t\t\t\t<v8:TypeSet xmlns:d5p1=\"" + CONFIG + "\">d5p1:AnyRef</v8:TypeSet>" + eol
      + "\t\t\t\t<v8:TypeSet xmlns:d5p1=\"" + CONFIG + "\">d5p1:CatalogRef</v8:TypeSet>" + eol
      + "\t\t\t</valueType>" + eol);
    assertStrict(dcs);

    // Новая ссылка сверяется с составом Configuration.mdo, имя определяемого типа — с DefinedTypes проекта
    assertThat(mutate("cf-dcs-set-parameter", dcs, Map.of("name", "Подразделение"), Map.of("valueType",
      Map.of("types", List.of("cfg:CatalogRef.Пользователи", "cfg:CatalogRef.НетТакого"))))).isEqualTo(2);
    assertThat(mutate("cf-dcs-set-parameter", dcs, Map.of("name", "Подразделение"), Map.of("valueType",
      Map.of("definedTypes", List.of("Организация"))))).isZero();
    Map<String, Object> parameter = valueType(byKey(rows(DcsRead.info(dcs, SchemaVersion.V2_20), "parameters"),
      "name", "Подразделение"));
    assertThat(parameter.get("definedTypes")).isEqualTo(List.of("Организация"));
    assertThat(mutate("cf-dcs-set-parameter", dcs, Map.of("name", "Подразделение"), Map.of("valueType",
      Map.of("types", List.of("cfg:CatalogRef.Пользователи"))))).isZero();
    assertThat(Files.readString(dcs, StandardCharsets.UTF_8)).contains("<v8:Type xmlns:d4p1=\"" + CONFIG
      + "\">d4p1:CatalogRef.Пользователи</v8:Type>");
    assertStrict(dcs);
  }

  @Test
  void каналApplyMutationПринимаетЧислаJsonИОтказываетКодом2() throws Exception {
    Path dcs = inDump(FILES);
    String payload = "{\"valueType\":{\"types\":[\"xs:string\"],\"stringQualifiers\":{\"length\":50,"
      + "\"allowedLength\":\"Variable\"}},\"value\":{\"type\":\"xs:string\",\"text\":\"\"},"
      + "\"valueListAllowed\":true}";
    assertThat(applyMutation(Map.of("op", "cf-dcs-set-parameter", "objectXml", dcs.toString(),
      "schemaVersion", "V2_20", "name", "Размер", "payloadJson", payload))).isZero();
    Map<String, Object> size = byKey(rows(DcsRead.info(dcs, SchemaVersion.V2_20), "parameters"), "name", "Размер");
    assertThat(valueType(size).get("stringQualifiers")).isEqualTo(Map.of("length", "50", "allowedLength", "VARIABLE"));
    assertThat(size.get("valueListAllowed")).isEqualTo(true);
    assertStrict(dcs);

    String before = Files.readString(dcs, StandardCharsets.UTF_8);
    assertThat(applyMutation(Map.of("op", "cf-dcs-set-parameter", "objectXml", dcs.toString(),
      "schemaVersion", "V2_20", "name", "Размер",
      "payloadJson", "{\"valueType\":{\"types\":[\"xs:int\"]}}"))).isEqualTo(2);
    assertThat(applyMutation(Map.of("op", "cf-dcs-add-parameter", "objectXml", dcs.toString(),
      "schemaVersion", "V2_20", "payloadJson",
      "{\"name\":\"Новый\",\"valueType\":{\"types\":[\"xs:boolean\"]},\"value\":{\"type\":\"xs:boolean\","
        + "\"text\":\"false\"}}"))).isZero();
    assertThat(Files.readString(dcs, StandardCharsets.UTF_8)).isNotEqualTo(before)
      .contains("\t\t<name>Новый</name>\r\n\t\t<valueType>\r\n\t\t\t<v8:Type>xs:boolean</v8:Type>\r\n"
        + "\t\t</valueType>\r\n\t\t<value xsi:type=\"xs:boolean\">false</value>\r\n"
        + "\t\t<useRestriction>false</useRestriction>\r\n\t</parameter>");
    assertStrict(dcs);
  }

  @Test
  void писательМетаданныхНеПишетПустыхTypeIds() {
    MdTypeDescriptionDto dto = new MdTypeDescriptionDto();
    dto.types.add("xs:string");
    dto.stringQualifiers = new MdTypeDescriptionDto.MdStringQualifiersDto();
    dto.stringQualifiers.length = "10";
    dto.stringQualifiers.allowedLength = "VARIABLE";
    String expected = "<Type><v8:Type xmlns:xs=\"http://www.w3.org/2001/XMLSchema\">xs:string</v8:Type>"
      + "<v8:StringQualifiers><v8:Length>10</v8:Length><v8:AllowedLength>Variable</v8:AllowedLength>"
      + "</v8:StringQualifiers></Type>";
    assertThat(MdTypeDescriptionSerial.typeElement("Type", dto)).isEqualTo(expected);
    dto.typeIds = new ArrayList<>();
    assertThat(MdTypeDescriptionSerial.typeElement("Type", dto)).isEqualTo(expected);
    assertThat(MdTypeDescriptionSerial.typeElement("Type", new MdTypeDescriptionDto())).isEqualTo("<Type/>");
  }

  // --------------------------------------------------------------- помощники

  /**
   * Каждый блок типа переписывается писателем из прочитанного DTO на той же глубине: расхождения
   * с байтами файла — список «файл: блок».
   */
  private static List<String> rewriteMismatches(List<Path> schemas, int expectedBlocks) throws IOException {
    List<String> mismatches = new ArrayList<>();
    int blocks = 0;
    for (Path schema : schemas) {
      String text = Files.readString(schema, StandardCharsets.UTF_8);
      Matcher block = VALUE_TYPE_BLOCK.matcher(text);
      while (block.find()) {
        blocks++;
        // Глубина владельца — число табов строки valueType: корень схемы без отступа на глубине 1
        int ownerDepth = XmlLines.indentAt(text, block.start()).length();
        MdTypeDescriptionDto dto = DcsValueTypeEdit.read(text, block.start(), block.end(), SchemaVersion.V2_20);
        String written = XmlGranularPatch.formatReplacementPreservingIndent(
          text, block.start(), DcsValueTypeEdit.xml(text, dto, ownerDepth));
        if (!written.equals(block.group())) {
          mismatches.add(schema + ": " + block.group() + " -> " + written);
        }
      }
    }
    assertThat(blocks).isEqualTo(expectedBlocks);
    return mismatches;
  }

  /** Payload эха: прочитанный тип через JSON, как его шлёт канал. */
  private static Map<String, Object> echo(Map<String, Object> row) {
    return channel(Map.of("valueType", row.get("valueType")));
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> channel(Map<String, Object> payload) {
    return GSON.fromJson(GSON.toJson(payload), Map.class);
  }

  private static Object channelObject(Object value) {
    return GSON.fromJson(GSON.toJson(value), Object.class);
  }

  /**
   * Копия схемы в выгрузке: {@code Configuration.xml} и все {@code DefinedTypes} рядом, схема — на
   * своём месте; так работают проверки состава и имена определяемых типов.
   */
  private Path inDump(String relativePath) throws IOException {
    Path source = Ssl31SubmodulePaths.projectRoot().resolve("src/cf");
    Path cf = Files.createTempDirectory(tempDir, "dump").resolve("cf");
    copyFile(source.resolve("Configuration.xml"), cf.resolve("Configuration.xml"));
    try (Stream<Path> definedTypes = Files.list(source.resolve("DefinedTypes"))) {
      for (Path file : definedTypes.filter(Files::isRegularFile).toList()) {
        copyFile(file, cf.resolve("DefinedTypes").resolve(file.getFileName().toString()));
      }
    }
    String inside = relativePath.substring("src/cf/".length());
    Path schema = cf.resolve(inside);
    copyFile(Ssl31SubmodulePaths.projectRoot().resolve(relativePath), schema);
    return schema;
  }

  private static void copyFile(Path from, Path to) throws IOException {
    Files.createDirectories(to.getParent());
    Files.copy(from, to);
  }

  private static void assertStrict(Path dcs) {
    Map<String, Object> verdict = DcsXsdValidation.validate(dcs);
    assertThat(verdict.get("ok")).as(String.valueOf(verdict.get("errors"))).isEqualTo(true);
  }

  /** Канал apply-mutation: операция, адрес и payload JSON-строкой. */
  private int mutate(String op, Path dcs, Map<String, String> address, Map<String, Object> payload) throws Exception {
    Map<String, Object> params = new LinkedHashMap<>(address);
    params.put("op", op);
    params.put("objectXml", dcs.toString());
    params.put("schemaVersion", "V2_20");
    params.put("payloadJson", GSON.toJson(payload));
    return applyMutation(params);
  }

  private int applyMutation(Map<String, ?> params) throws Exception {
    Path paramsFile = Files.createTempFile(tempDir, "params", ".json");
    Files.writeString(paramsFile, GSON.toJson(params), StandardCharsets.UTF_8);
    return new CommandLine(new DesignerXmlCli()).execute("apply-mutation", "--params", paramsFile.toString());
  }

  /** Вне элемента-владельца файл совпадает байт в байт: правка не задела соседей. */
  private static void assertOnlyInside(String before, String after, int[] was, int[] now) {
    assertThat(after.substring(0, now[0])).isEqualTo(before.substring(0, was[0]));
    assertThat(after.substring(now[1])).isEqualTo(before.substring(was[1]));
  }

  private static void assertOnlyParameterChanged(String before, String after, String name) {
    assertOnlyInside(before, after, rootSpan(before, "parameter", "name", name),
      rootSpan(after, "parameter", "name", name));
  }

  private static void assertOnlyCalculatedChanged(String before, String after, String dataPath) {
    assertOnlyInside(before, after, rootSpan(before, "calculatedField", "dataPath", dataPath),
      rootSpan(after, "calculatedField", "dataPath", dataPath));
  }

  private static void assertOnlyFieldChanged(String before, String after, String setName, String dataPath) {
    assertOnlyInside(before, after, fieldSpan(before, setName, dataPath), fieldSpan(after, setName, dataPath));
  }

  /** Границы элемента корня схемы по значению простого потомка. */
  private static int[] rootSpan(String xml, String element, String key, String value) {
    for (XmlLines.Node node : XmlLines.children(xml, List.of("DataCompositionSchema"))) {
      if (element.equals(node.name()) && value.equals(node.leaves().get(key))) {
        return new int[] {node.start(), node.end()};
      }
    }
    throw new AssertionError(element + " не найден: " + value);
  }

  private static int[] fieldSpan(String xml, String setName, String dataPath) {
    int[] set = rootSpan(xml, "dataSet", "name", setName);
    for (XmlLines.Node field : XmlLines.childrenAt(xml, set[0])) {
      if ("field".equals(field.name()) && dataPath.equals(field.leaves().get("dataPath"))) {
        return new int[] {field.start(), field.end()};
      }
    }
    throw new AssertionError("поле не найдено: " + setName + "/" + dataPath);
  }

  /** Правка одной непрерывной вставкой или вырезкой: вне неё файл совпадает байт в байт. */
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

  private static List<String> parameterChildNames(String xml, String name) {
    for (XmlLines.Node node : XmlLines.children(xml, List.of("DataCompositionSchema"))) {
      if ("parameter".equals(node.name()) && name.equals(node.leaves().get("name"))) {
        return XmlLines.childrenAt(xml, node.start()).stream().map(XmlLines.Node::name).toList();
      }
    }
    throw new AssertionError("параметр не найден: " + name);
  }

  private static List<String> fieldChildNames(String xml, String setName, String dataPath) {
    for (XmlLines.Node set : XmlLines.children(xml, List.of("DataCompositionSchema"))) {
      if (!"dataSet".equals(set.name()) || !setName.equals(set.leaves().get("name"))) {
        continue;
      }
      for (XmlLines.Node field : XmlLines.childrenAt(xml, set.start())) {
        if ("field".equals(field.name()) && dataPath.equals(field.leaves().get("dataPath"))) {
          return XmlLines.childrenAt(xml, field.start()).stream().map(XmlLines.Node::name).toList();
        }
      }
    }
    throw new AssertionError("поле не найдено: " + setName + "/" + dataPath);
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

  @SuppressWarnings("unchecked")
  private static List<Map<String, Object>> rows(Map<String, Object> info, String key) {
    Object value = info.get(key);
    return value == null ? List.of() : (List<Map<String, Object>>) value;
  }

  private static List<Map<String, Object>> fields(Map<String, Object> dataSet) {
    return rows(dataSet, "fields");
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> valueType(Map<String, Object> row) {
    Object value = row.get("valueType");
    assertThat(value).as("valueType у " + row).isInstanceOf(Map.class);
    return (Map<String, Object>) value;
  }

  @SuppressWarnings("unchecked")
  private static List<Object> list(Object value) {
    assertThat(value).isInstanceOf(List.class);
    return (List<Object>) value;
  }

  private static Map<String, Object> byKey(List<Map<String, Object>> rows, String key, String value) {
    return rows.stream()
      .filter(row -> value.equals(row.get(key)))
      .findFirst()
      .orElseThrow(() -> new AssertionError(key + "=" + value + " не найден: " + rows));
  }
}
