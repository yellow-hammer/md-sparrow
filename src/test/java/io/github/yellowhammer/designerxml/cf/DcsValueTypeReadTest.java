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
import io.github.yellowhammer.designerxml.cli.DesignerXmlCli;
import io.github.yellowhammer.designerxml.reflect.JaxbReflect;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Структурный тип значения в {@code cf-dcs-info} ({@code valueType} полей наборов, вычисляемых
 * полей и параметров) и словарь окна типа {@code cf-dcs-type-dictionary}.
 *
 * <p>Главная проверка — весь корпус ssl31: каждый из 732 блоков {@code valueType} 74 схем
 * (cf, cfe, epf, erf; вместе с вложенными схемами) читается без исключений, состав совпадает с
 * текстом файла, и всё, что записала платформа, входит в белый словарь редактора. Целевые формы
 * сверяются по образцам корпуса, формы вне словаря — на синтетических схемах в копиях.
 */
class DcsValueTypeReadTest {

  /** Схема со строкой и «любым справочником», строкой и «любой ссылкой», определяемым типом. */
  private static final String EVENT_LOG =
    "src/cf/Reports/АнализЖурналаРегистрации/Templates/ОсновнаяСхемаКомпоновкиДанных/Ext/Template.xml";

  /** Схема с составным типом из ссылок, числом, датой, стандартным периодом и вложенной схемой. */
  private static final String FILES =
    "src/cf/Reports/_ДемоФайлы/Templates/ОсновнаяСхемаКомпоновкиДанных/Ext/Template.xml";

  /** Строка фиксированной длины. */
  private static final String FIXED_STRING =
    "src/cf/Reports/ИзменениеРазрешенныхЗначенийГруппДоступа/Templates/Макет/Ext/Template.xml";

  /** Дата без времени. */
  private static final String DATE_ONLY =
    "src/cf/Documents/АктОбУничтоженииПерсональныхДанных/Templates/ДанныеПечати/Ext/Template.xml";

  /** Характеристики плана видов характеристик. */
  private static final String CHARACTERISTICS =
    "src/cf/Reports/ДатыЗапретаЗагрузки/Templates/ОсновнаяСхемаКомпоновкиДанных/Ext/Template.xml";

  /** «Любая ссылка» у поля набора-объекта. */
  private static final String ANY_REF =
    "DataProcessors/ЗаменаИОбъединениеЭлементов/Templates/ОсновнаяСхемаКомпоновкиДанных";

  /** Вычисляемые поля с числом. */
  private static final String CALCULATED =
    "src/cf/CommonTemplates/ПолучениеДереваОбработчиковОбновления/Ext/Template.xml";

  /** Объединение двух наборов-объектов с типами у полей и у вложенных наборов. */
  private static final String UNION =
    "src/cf/Reports/ОбъемНенужныхФайлов/Templates/ОсновнаяСхемаКомпоновкиДанных/Ext/Template.xml";

  /** Блок типа в тексте файла: им сверяется полнота чтения моделью. */
  private static final Pattern VALUE_TYPE_BLOCK = Pattern.compile("<valueType>(.*?)</valueType>", Pattern.DOTALL);

  private static final Pattern TYPE = Pattern.compile("<v8:Type[\\s>]");

  private static final Pattern TYPE_SET = Pattern.compile("<v8:TypeSet[\\s>]");

  private static final Pattern TYPE_ID = Pattern.compile("<v8:TypeId>");

  /** Префикс в духе файла ({@code d5p1:}) после нормализации остаться не должен. */
  private static final Pattern RAW_PREFIX = Pattern.compile("^d\\d+p1:");

  @TempDir
  Path tempDir;

  // --------------------------------------------------------------- корпус целиком

  @Test
  void всеБлокиТипаКорпусаЧитаютсяПолностьюИВходятВСловарь() throws Exception {
    List<Path> schemas = schemaFiles(Ssl31SubmodulePaths.projectRoot().resolve("src"));
    assertThat(schemas).hasSize(74);

    int textBlocks = 0;
    int textTypes = 0;
    int textTypeSets = 0;
    int textTypeIds = 0;
    List<Map<String, Object>> modelBlocks = new ArrayList<>();
    int infoBlocks = 0;
    int unionItemBlocks = 0;
    for (Path schema : schemas) {
      String text = Files.readString(schema, StandardCharsets.UTF_8);
      Matcher blocks = VALUE_TYPE_BLOCK.matcher(text);
      while (blocks.find()) {
        textBlocks++;
        textTypes += count(TYPE, blocks.group(1));
        textTypeSets += count(TYPE_SET, blocks.group(1));
        textTypeIds += count(TYPE_ID, blocks.group(1));
      }

      // Модель целиком, вместе с вложенными схемами, которых cf-dcs-info не раскрывает
      DcsValueTypes.Context context = DcsValueTypes.Context.of(schema);
      walkTypes(DcsRead.schemaRoot(schema, SchemaVersion.V2_20),
        typeDescription -> modelBlocks.add(DcsValueTypes.read(typeDescription, context, null)));

      // Ответ cf-dcs-info: valueType рядом со строковым представлением типа, поля объединений — только чтение
      Map<String, Object> info = DcsRead.info(schema, SchemaVersion.V2_20);
      for (Map<String, Object> row : rows(info, "parameters")) {
        infoBlocks += checkInfoRow(row, "type");
      }
      for (Map<String, Object> row : rows(info, "calculatedFields")) {
        infoBlocks += checkInfoRow(row, null);
      }
      for (Map<String, Object> dataSet : rows(info, "dataSets")) {
        int[] counts = checkDataSet(dataSet, false);
        infoBlocks += counts[0];
        unionItemBlocks += counts[1];
      }
    }

    assertThat(textBlocks).isEqualTo(732);
    assertThat(modelBlocks).hasSize(732);
    assertThat(modelBlocks.stream().mapToInt(block -> list(block.get("types")).size()).sum()).isEqualTo(textTypes);
    assertThat(modelBlocks.stream().mapToInt(block -> list(block.get("typeSets")).size()).sum())
      .isEqualTo(textTypeSets);
    assertThat(modelBlocks.stream().mapToInt(block -> list(block.get("typeIds")).size()).sum())
      .isEqualTo(textTypeIds)
      .isEqualTo(19);
    // Всё, что пишет платформа в корпусе, редактор умеет записать её байтами
    assertThat(modelBlocks).allSatisfy(block -> {
      assertThat(block.get("editable")).as(String.valueOf(block)).isEqualTo(true);
      assertThat(block).doesNotContainKey("readOnlyReason");
      for (Object type : list(block.get("types"))) {
        assertThat((String) type).doesNotContainPattern(RAW_PREFIX);
      }
      for (Object typeSet : list(block.get("typeSets"))) {
        assertThat((String) typeSet).doesNotContainPattern(RAW_PREFIX);
      }
    });
    // Определяемые типы все найдены по выгрузке: 7 разных UUID, каждый с именем
    Set<String> definedTypes = new LinkedHashSet<>();
    for (Map<String, Object> block : modelBlocks) {
      if (!list(block.get("typeIds")).isEmpty()) {
        assertThat(list(block.get("definedTypes"))).hasSameSizeAs(list(block.get("typeIds")));
        definedTypes.add(list(block.get("typeIds")).get(0) + "=" + list(block.get("definedTypes")).get(0));
      }
    }
    assertThat(definedTypes).hasSize(7).contains(
      "92fb25e9-5b34-4411-b876-cd83fa55a6e0=Подразделение",
      "89432067-978f-4e8e-a7dc-cb87badd9341=ВладелецФайлов");
    // cf-dcs-info видит всё, кроме 32 блоков вложенных схем; 2 поля вложенных наборов объединения
    assertThat(infoBlocks).isEqualTo(700);
    assertThat(unionItemBlocks).isEqualTo(2);
  }

  @Test
  void схемыПроектаEdtЧитаютсяСЛюбойСсылкойEdtИОпределяемымиТипамиИзMdo() throws Exception {
    Path edtRoot = Path.of(System.getProperty("fixtures.ssl31edt.root"));
    List<Path> schemas = schemaFiles(edtRoot);
    assertThat(schemas).isNotEmpty().allSatisfy(schema -> assertThat(schema.toString()).endsWith(".dcs"));

    List<Map<String, Object>> blocks = new ArrayList<>();
    for (Path schema : schemas) {
      DcsValueTypes.Context context = DcsValueTypes.Context.of(schema);
      assertThat(context.edt()).isTrue();
      walkTypes(DcsRead.schemaRoot(schema, SchemaVersion.V2_20),
        typeDescription -> blocks.add(DcsValueTypes.read(typeDescription, context, null)));
    }
    assertThat(blocks).hasSize(729).allSatisfy(block ->
      assertThat(block.get("editable")).as(String.valueOf(block)).isEqualTo(true));

    Path sourceRoot = edtRoot.resolve("ssl31").resolve("src");
    Map<String, Object> eventLog = DcsRead.info(sourceRoot.resolve(
      "Reports/АнализЖурналаРегистрации/Templates/ОсновнаяСхемаКомпоновкиДанных/Template.dcs"), SchemaVersion.V2_20);
    Map<String, Object> department = valueType(byKey(rows(eventLog, "parameters"), "name", "Подразделение"));
    assertThat(department.get("typeIds")).isEqualTo(List.of("92fb25e9-5b34-4411-b876-cd83fa55a6e0"));
    assertThat(department.get("definedTypes")).isEqualTo(List.of("Подразделение"));
    assertThat(department.get("editable")).isEqualTo(true);

    // «Любая ссылка» EDT пишет AnyRef: в формате проекта это тип словаря
    Map<String, Object> anyRef = DcsRead.info(sourceRoot.resolve(ANY_REF + "/Template.dcs"), SchemaVersion.V2_20);
    Map<String, Object> reference = valueType(byKey(fields(byKey(rows(anyRef, "dataSets"), "name",
      "МестаИспользования")), "dataPath", "Ссылка"));
    assertThat(reference.get("typeSets")).isEqualTo(List.of("cfg:AnyRef"));
    assertThat(reference.get("editable")).isEqualTo(true);

    // Тот же файл под именем выгрузки: AnyRef там не пишется, тип только читается
    Path designerCopy = tempDir.resolve("Template.xml");
    Files.copy(sourceRoot.resolve(ANY_REF + "/Template.dcs"), designerCopy);
    Map<String, Object> copied = valueType(byKey(fields(byKey(rows(DcsRead.info(designerCopy, SchemaVersion.V2_20),
      "dataSets"), "name", "МестаИспользования")), "dataPath", "Ссылка"));
    assertThat(copied.get("editable")).isEqualTo(false);
    assertThat((String) copied.get("readOnlyReason")).contains("cfg:AnyRef");
  }

  // --------------------------------------------------------------- целевые формы

  @Test
  void целевыеФормыКорпусаЧитаютсяСоставомИКвалификаторами() throws Exception {
    Map<String, Object> eventLog = info(EVENT_LOG);
    Map<String, Object> main = rows(eventLog, "dataSets").get(0);

    // Строка с «любым справочником»: Type и TypeSet различаются, квалификатор строки при своём типе
    Map<String, Object> user = valueType(byKey(fields(main), "dataPath", "Пользователь"));
    assertThat(user.get("types")).isEqualTo(List.of("xs:string"));
    assertThat(user.get("typeSets")).isEqualTo(List.of("cfg:CatalogRef"));
    assertThat(user.get("typeIds")).isEqualTo(List.of());
    assertThat(user.get("stringQualifiers")).isEqualTo(Map.of("length", "0", "allowedLength", "VARIABLE"));
    assertThat(user).doesNotContainKeys("numberQualifiers", "dateQualifiers", "definedTypes", "readOnlyReason");
    assertThat(user.get("editable")).isEqualTo(true);
    // Строковое представление типа — рядом со структурным
    assertThat(byKey(fields(main), "dataPath", "Пользователь").get("valueTypePresentation"))
      .isEqualTo("string, CatalogRef");

    Map<String, Object> departmentField = valueType(byKey(fields(main), "dataPath", "Подразделение"));
    assertThat(departmentField.get("types")).isEqualTo(List.of("xs:string"));
    assertThat(departmentField.get("typeSets")).isEqualTo(List.of("cfg:AnyIBRef"));
    assertThat(departmentField.get("stringQualifiers")).isEqualTo(Map.of("length", "150", "allowedLength", "VARIABLE"));

    // Определяемый тип пишется только UUID: имя — по DefinedTypes выгрузки
    Map<String, Object> department = byKey(rows(eventLog, "parameters"), "name", "Подразделение");
    assertThat(department).doesNotContainKey("type");
    assertThat(valueType(department).get("types")).isEqualTo(List.of());
    assertThat(valueType(department).get("typeIds")).isEqualTo(List.of("92fb25e9-5b34-4411-b876-cd83fa55a6e0"));
    assertThat(valueType(department).get("definedTypes")).isEqualTo(List.of("Подразделение"));
    assertThat(valueType(department).get("editable")).isEqualTo(true);
    // Признаки владельца, которые смена типа может рассогласовать
    assertThat(department).doesNotContainKeys("hasAvailableValues", "hasInputParameters");
    assertThat(byKey(rows(eventLog, "parameters"), "name", "Пользователь").get("hasInputParameters")).isEqualTo(true);
    assertThat(byKey(rows(eventLog, "parameters"), "name", "ВариантОтчета").get("hasAvailableValues")).isEqualTo(true);

    Map<String, Object> files = info(FILES);
    // Составной тип из ссылок: порядок файла, префикс d4p1 нормализован в cfg
    Map<String, Object> authors = valueType(byKey(rows(files, "parameters"), "name", "АвторыВерсий"));
    assertThat(authors.get("types")).isEqualTo(List.of(
      "cfg:CatalogRef.ВнешниеПользователи",
      "cfg:CatalogRef.ГруппыВнешнихПользователей",
      "cfg:CatalogRef.ГруппыПользователей",
      "cfg:CatalogRef.Пользователи"));
    assertThat(authors.get("editable")).isEqualTo(true);
    // Префикс поля набора — d5p1, нормализация та же
    assertThat(valueType(byKey(fields(rows(files, "dataSets").get(0)), "dataPath", "Редактирует")).get("types"))
      .isEqualTo(List.of("cfg:CatalogRef.Пользователи"));
    Map<String, Object> size = valueType(byKey(rows(files, "parameters"), "name", "Размер"));
    assertThat(size.get("types")).isEqualTo(List.of("xs:decimal"));
    assertThat(size.get("numberQualifiers"))
      .isEqualTo(Map.of("digits", "25", "fractionDigits", "0", "allowedSign", "NONNEGATIVE"));
    Map<String, Object> period = valueType(byKey(rows(files, "parameters"), "name", "Период"));
    assertThat(period.get("types")).isEqualTo(List.of("v8:StandardPeriod"));
    assertThat(period).doesNotContainKeys("stringQualifiers", "numberQualifiers", "dateQualifiers");
    assertThat(valueType(byKey(rows(files, "parameters"), "name", "НачалоПериода")).get("dateQualifiers"))
      .isEqualTo(Map.of("dateFractions", "DATE_TIME"));
    Map<String, Object> folder = valueType(byKey(rows(files, "parameters"), "name", "Папка"));
    assertThat(folder.get("definedTypes")).isEqualTo(List.of("ВладелецФайлов"));

    assertThat(valueType(byKey(fields(rows(info(FIXED_STRING), "dataSets").get(0)), "dataPath",
      "ВидИзмененияВидаДоступа")).get("stringQualifiers")).isEqualTo(Map.of("length", "1", "allowedLength", "FIXED"));
    assertThat(valueType(byKey(fields(rows(info(DATE_ONLY), "dataSets").get(0)), "dataPath", "ДатаУничтожения"))
      .get("dateQualifiers")).isEqualTo(Map.of("dateFractions", "DATE"));

    Map<String, Object> characteristics = info(CHARACTERISTICS);
    assertThat(valueType(byKey(rows(characteristics, "parameters"), "name", "Объекты")).get("typeSets"))
      .isEqualTo(List.of("cfg:Characteristic.РазделыДатЗапретаИзменения"));
    assertThat(valueType(byKey(rows(characteristics, "parameters"), "name", "Разделы")).get("types"))
      .isEqualTo(List.of("cfg:ChartOfCharacteristicTypesRef.РазделыДатЗапретаИзменения"));

    Map<String, Object> anyRef = info("src/cf/" + ANY_REF + "/Ext/Template.xml");
    Map<String, Object> reference = valueType(byKey(fields(byKey(rows(anyRef, "dataSets"), "name",
      "МестаИспользования")), "dataPath", "Ссылка"));
    assertThat(reference.get("typeSets")).isEqualTo(List.of("cfg:AnyIBRef"));
    assertThat(reference.get("editable")).isEqualTo(true);

    Map<String, Object> weight = valueType(byKey(rows(info(CALCULATED), "calculatedFields"), "name", "ВесВерсии"));
    assertThat(weight.get("types")).isEqualTo(List.of("xs:decimal"));
    assertThat(weight.get("numberQualifiers")).isEqualTo(Map.of("digits", "13", "fractionDigits", "0",
      "allowedSign", "ANY"));
  }

  @Test
  void типПоляВложенногоНабораОбъединенияТолькоДляЧтения() throws Exception {
    Map<String, Object> union = rows(info(UNION), "dataSets").get(0);
    assertThat(union.get("type")).isEqualTo("union");
    // Поле самого объединения адресуется мутациями: тип правится
    assertThat(valueType(byKey(fields(union), "dataPath", "ОбъемВсего")).get("editable")).isEqualTo(true);
    Map<String, Object> item = byKey(rows(union, "items"), "name", "ОбъемДанныхВсего");
    Map<String, Object> nested = valueType(byKey(fields(item), "dataPath", "ОбъемВсего"));
    assertThat(nested.get("types")).isEqualTo(List.of("xs:decimal"));
    assertThat(nested.get("editable")).isEqualTo(false);
    assertThat((String) nested.get("readOnlyReason")).contains("объединения");
  }

  // --------------------------------------------------------------- вне словаря

  @Test
  void типыВнеБелогоСловаряТолькоДляЧтенияСПричиной() throws Exception {
    Path dump = fakeDump();
    Map<String, String> cases = new LinkedHashMap<>();
    cases.put("<v8:Type>v8:ValueTable</v8:Type>", "«v8:ValueTable»");
    cases.put("<v8:Type>v8:ValueStorage</v8:Type>", "«v8:ValueStorage»");
    cases.put("<v8:Type>v8:Null</v8:Type>", "«v8:Null»");
    cases.put("<v8:Type>v8:Type</v8:Type>", "«v8:Type»");
    cases.put("<v8:Type>v8:StandardBeginningDate</v8:Type>", "«v8:StandardBeginningDate»");
    cases.put("<v8:Type>v8:ValueListType</v8:Type>", "«v8:ValueListType»");
    cases.put("<v8:Type xmlns:d4p1=\"http://v8.1c.ru/8.1/data/enterprise\">d4p1:ЧужойТип</v8:Type>", "«d4p1:ЧужойТип»");
    cases.put(configType("Type", "DocumentJournalRef.Взаимодействия"), "«cfg:DocumentJournalRef.Взаимодействия»");
    cases.put(configType("Type", "BusinessProcessRoutePointRef.Задание"), "«cfg:BusinessProcessRoutePointRef.Задание»");
    cases.put(configType("TypeSet", "DocumentJournalRef"), "«cfg:DocumentJournalRef»");
    // В выгрузке конфигуратора «любая ссылка» — AnyIBRef; AnyRef — форма EDT
    cases.put(configType("TypeSet", "AnyRef"), "«cfg:AnyRef»");
    cases.put("<v8:Type>xs:string</v8:Type><v8:StringQualifiers><v8:Length>0</v8:Length>"
      + "<v8:AllowedLength>Variable</v8:AllowedLength></v8:StringQualifiers><v8:BinaryDataQualifiers>"
      + "<v8:Length>0</v8:Length><v8:AllowedLength>Variable</v8:AllowedLength></v8:BinaryDataQualifiers>",
      "двоичных данных");
    cases.put("<v8:TypeId>00000000-0000-0000-0000-000000000001</v8:TypeId>",
      "«00000000-0000-0000-0000-000000000001» не найден");
    cases.put("<v8:Type>xs:boolean</v8:Type><v8:TypeId>92fb25e9-5b34-4411-b876-cd83fa55a6e0</v8:TypeId>", "составном");
    for (Map.Entry<String, String> entry : cases.entrySet()) {
      Map<String, Object> valueType = parameterType(dump, entry.getKey());
      assertThat(valueType.get("editable")).as(entry.getKey()).isEqualTo(false);
      assertThat((String) valueType.get("readOnlyReason")).as(entry.getKey()).contains(entry.getValue());
    }

    // Те же примитивы, ссылки и наборы словаря — правятся
    for (String allowed : List.of(
      "<v8:Type>v8:UUID</v8:Type>",
      configType("Type", "TaskRef.ЗадачаИсполнителя"),
      configType("TypeSet", "BusinessProcessRef"),
      configType("TypeSet", "AnyIBRef"),
      "<v8:TypeId>92fb25e9-5b34-4411-b876-cd83fa55a6e0</v8:TypeId>")) {
      Map<String, Object> valueType = parameterType(dump, allowed);
      assertThat(valueType.get("editable")).as(allowed).isEqualTo(true);
    }
    assertThat(parameterType(dump, "<v8:TypeId>92FB25E9-5B34-4411-B876-CD83FA55A6E0</v8:TypeId>")
      .get("definedTypes")).isEqualTo(List.of("Подразделение"));
  }

  @Test
  void макетВнеВыгрузкиПереноситЗаписанныеСсылкиАОпределяемыйТипТолькоЧитает() throws Exception {
    Path alone = tempDir.resolve("Отдельно").resolve("Template.xml");
    Files.createDirectories(alone.getParent());
    Files.copy(Ssl31SubmodulePaths.projectRoot().resolve(EVENT_LOG), alone);

    Map<String, Object> info = DcsRead.info(alone, SchemaVersion.V2_20);
    Map<String, Object> department = valueType(byKey(rows(info, "parameters"), "name", "Подразделение"));
    assertThat(department.get("typeIds")).isEqualTo(List.of("92fb25e9-5b34-4411-b876-cd83fa55a6e0"));
    assertThat(department).doesNotContainKey("definedTypes");
    assertThat(department.get("editable")).isEqualTo(false);
    assertThat((String) department.get("readOnlyReason")).contains("в выгрузке конфигурации");
    // Записанные ссылочные типы сохраняются эхом и снимаются: тип правится
    Map<String, Object> user = valueType(byKey(fields(rows(info, "dataSets").get(0)), "dataPath", "Пользователь"));
    assertThat(user.get("editable")).isEqualTo(true);
  }

  @Test
  void метаданныеНеПолучаютКлючаTypeIds() throws Exception {
    Path catalog = Ssl31SubmodulePaths.projectRoot().resolve("src/cf/Catalogs/_ДемоНоменклатура.xml");
    String json = new Gson().toJson(MdObjectStructureRead.read(catalog, SchemaVersion.V2_20));
    assertThat(json).contains("\"types\"").doesNotContain("typeIds");
  }

  // --------------------------------------------------------------- словарь

  @Test
  void словарьВыгрузкиSsl31() throws Exception {
    Map<String, Object> dictionary = DcsTypeDictionary.read(
      Ssl31SubmodulePaths.projectRoot().resolve(EVENT_LOG), SchemaVersion.V2_20);
    assertThat(dictionary.get("configurationFound")).isEqualTo(true);
    assertThat(dictionary.get("anyRef")).isEqualTo("cfg:AnyIBRef");
    assertPrimitives(dictionary);

    List<Map<String, Object>> kinds = maps(dictionary.get("refKinds"));
    assertThat(kinds).extracting(kind -> kind.get("kind")).containsExactly(
      "Catalog", "Document", "Enum", "ChartOfCharacteristicTypes", "ChartOfAccounts", "ChartOfCalculationTypes",
      "ExchangePlan", "BusinessProcess", "Task");
    Map<String, Object> catalog = kinds.get(0);
    assertThat(catalog.get("label")).isEqualTo("Справочник");
    assertThat(catalog.get("platformName")).isEqualTo("СправочникСсылка");
    assertThat(catalog.get("typeSet")).isEqualTo("cfg:CatalogRef");
    assertThat(list(catalog.get("types"))).hasSize(114).contains("cfg:CatalogRef.Пользователи");
    assertThat(list(byKey(kinds, "kind", "Enum").get("types"))).hasSize(123);
    assertThat(byKey(kinds, "kind", "ChartOfCharacteristicTypes").get("platformName"))
      .isEqualTo("ПланВидовХарактеристикСсылка");
    assertThat(byKey(kinds, "kind", "Task").get("label")).isEqualTo("Задача");
    // Журналов документов и точек маршрута в словаре нет: у первых ссылочного типа нет вовсе
    assertThat(kinds.toString()).doesNotContain("DocumentJournal").doesNotContain("RoutePoint");

    assertThat(list(dictionary.get("characteristics"))).hasSize(5)
      .contains("cfg:Characteristic.РазделыДатЗапретаИзменения");
    List<Map<String, Object>> definedTypes = maps(dictionary.get("definedTypes"));
    assertThat(definedTypes).hasSize(76);
    assertThat(byKey(definedTypes, "name", "Подразделение").get("typeId"))
      .isEqualTo("92fb25e9-5b34-4411-b876-cd83fa55a6e0");
    assertThat(byKey(definedTypes, "name", "ВладелецФайлов").get("typeId"))
      .isEqualTo("89432067-978f-4e8e-a7dc-cb87badd9341");
  }

  @Test
  void словарьПроектаEdtСЛюбойСсылкойAnyRef() throws Exception {
    Path schema = Path.of(System.getProperty("fixtures.ssl31edt.root"), "ssl31", "src",
      "Reports", "АнализЖурналаРегистрации", "Templates", "ОсновнаяСхемаКомпоновкиДанных", "Template.dcs");
    Map<String, Object> dictionary = DcsTypeDictionary.read(schema, SchemaVersion.V2_20);
    Map<String, Object> designer = DcsTypeDictionary.read(
      Ssl31SubmodulePaths.projectRoot().resolve(EVENT_LOG), SchemaVersion.V2_20);
    assertThat(dictionary.get("configurationFound")).isEqualTo(true);
    assertThat(dictionary.get("anyRef")).isEqualTo("cfg:AnyRef");
    assertPrimitives(dictionary);
    // Тот же состав, что у выгрузки: EDT отличается только записью «любой ссылки»
    assertThat(dictionary.get("refKinds")).isEqualTo(designer.get("refKinds"));
    assertThat(dictionary.get("characteristics")).isEqualTo(designer.get("characteristics"));
    assertThat(dictionary.get("definedTypes")).isEqualTo(designer.get("definedTypes"));
  }

  @Test
  void словарьОтдаётСоставОпределяемыхТиповФормойValueType() throws Exception {
    Path schema = Ssl31SubmodulePaths.projectRoot().resolve(EVENT_LOG);
    List<Map<String, Object>> definedTypes =
      maps(DcsTypeDictionary.read(schema, SchemaVersion.V2_20).get("definedTypes"));
    assertThat(definedTypes).hasSize(76).allSatisfy(row -> assertThat(row).containsKey("composition"));
    assertThat(composition(definedTypes, "ДенежнаяСуммаЛюбогоЗнака")).isEqualTo(Map.of(
      "types", List.of("xs:decimal"), "typeSets", List.of(), "typeIds", List.of(),
      "numberQualifiers", Map.of("digits", "15", "fractionDigits", "2", "allowedSign", "ANY")));
    assertThat(composition(definedTypes, "КурсВалюты").get("numberQualifiers"))
      .isEqualTo(Map.of("digits", "10", "fractionDigits", "4", "allowedSign", "NONNEGATIVE"));
    assertThat(composition(definedTypes, "БезопасныйРежим")).isEqualTo(Map.of(
      "types", List.of("xs:boolean", "xs:string"), "typeSets", List.of(), "typeIds", List.of(),
      "stringQualifiers", Map.of("length", "120", "allowedLength", "VARIABLE")));
    assertThat(composition(definedTypes, "Подразделение")).isEqualTo(Map.of(
      "types", List.of("cfg:CatalogRef._ДемоПодразделения"), "typeSets", List.of(), "typeIds", List.of()));
    // Типы объектов вне белого словаря редактора отдаются как есть: состав — подсказка окну, а не правка
    assertThat(list(composition(definedTypes, "ВладелецКонтактнойИнформации").get("types")))
      .contains("cfg:CatalogObject._ДемоОрганизации");

    // Тот же состав, что раскрывает правило совместимости значения параметра
    DcsValueTypes.Context context = DcsValueTypes.Context.of(schema);
    for (Map<String, Object> row : definedTypes) {
      MdTypeDescriptionDto rule = context.definedTypeComposition((String) row.get("typeId"));
      Map<String, Object> composition = composition(definedTypes, (String) row.get("name"));
      assertThat(composition.get("types")).as((String) row.get("name")).isEqualTo(rule.types);
      assertThat(composition.get("typeSets")).as((String) row.get("name")).isEqualTo(rule.typeSets);
    }

    // Форма сверена с valueType cf-dcs-info: тип каждого определяемого типа, записанный типом параметра
    // схемы, читается тем же составом и квалификаторами (признаки редактора не в счёт)
    Path dump = fakeDump();
    Path definedDir = Ssl31SubmodulePaths.projectRoot().resolve("src/cf/DefinedTypes");
    Pattern body = Pattern.compile("(?s)<Properties>.*?<Type>(.*?)</Type>");
    for (Map<String, Object> row : definedTypes) {
      String name = (String) row.get("name");
      Matcher type = body.matcher(Files.readString(definedDir.resolve(name + ".xml"), StandardCharsets.UTF_8));
      assertThat(type.find()).as(name).isTrue();
      Map<String, Object> read = new LinkedHashMap<>(parameterType(dump, type.group(1)
        .replace("<v8:Type>cfg:", "<v8:Type xmlns:cfg=\"" + CURRENT_CONFIG + "\">cfg:")
        .replace("<v8:TypeSet>cfg:", "<v8:TypeSet xmlns:cfg=\"" + CURRENT_CONFIG + "\">cfg:")));
      read.remove("editable");
      read.remove("readOnlyReason");
      assertThat(read).as(name).isEqualTo(composition(definedTypes, name));
    }
  }

  @Test
  void составОпределяемыхТиповПроектаEdtТотЖеЧтоВВыгрузке() throws Exception {
    Path schema = Path.of(System.getProperty("fixtures.ssl31edt.root"), "ssl31", "src",
      "Reports", "АнализЖурналаРегистрации", "Templates", "ОсновнаяСхемаКомпоновкиДанных", "Template.dcs");
    List<Map<String, Object>> edt = maps(DcsTypeDictionary.read(schema, SchemaVersion.V2_20).get("definedTypes"));
    List<Map<String, Object>> designer = maps(DcsTypeDictionary.read(
      Ssl31SubmodulePaths.projectRoot().resolve(EVENT_LOG), SchemaVersion.V2_20).get("definedTypes"));
    assertThat(edt).hasSize(76).allSatisfy(row -> assertThat(row).containsKey("composition"));
    // В .mdo состав записан именами EDT (Number, CatalogRef.Имя), а квалификаторы — без умолчаний:
    // их дополняет метамодель, и ответ совпадает с выгрузкой
    assertThat(edt).isEqualTo(designer);
    // Версия формата описанию EDT не нужна
    assertThat(maps(DcsTypeDictionary.read(schema, null).get("definedTypes"))).isEqualTo(edt);
  }

  @Test
  void безОписанияОпределяемогоТипаКлючаCompositionНет() throws Exception {
    Path dump = fakeDump();
    // UUID определяемого типа есть, а описания типа (Properties/Type) нет: состава нет и у правила
    Path file = dump.resolve("DefinedTypes/Подразделение.xml");
    String text = Files.readString(file, StandardCharsets.UTF_8);
    Files.writeString(file, text.replaceFirst("(?s)(<Properties>.*?)<Type>.*?</Type>", "$1"), StandardCharsets.UTF_8);
    parameterType(dump, "<v8:Type>xs:boolean</v8:Type>");
    Path template = dump.resolve("Reports/Тест/Templates/Макет/Ext/Template.xml");
    Map<String, Object> department = byKey(maps(DcsTypeDictionary.read(template, SchemaVersion.V2_20)
      .get("definedTypes")), "name", "Подразделение");
    assertThat(department).containsEntry("typeId", "92fb25e9-5b34-4411-b876-cd83fa55a6e0")
      .doesNotContainKey("composition");
  }

  @Test
  void словарьМакетаВнеВыгрузкиТолькоПримитивы() throws Exception {
    Path alone = tempDir.resolve("Template.xml");
    Files.copy(Ssl31SubmodulePaths.projectRoot().resolve(EVENT_LOG), alone);
    Map<String, Object> dictionary = DcsTypeDictionary.read(alone, SchemaVersion.V2_20);
    assertThat(dictionary.get("configurationFound")).isEqualTo(false);
    assertThat(dictionary.get("anyRef")).isEqualTo("cfg:AnyIBRef");
    assertPrimitives(dictionary);
    assertThat(list(dictionary.get("refKinds"))).isEmpty();
    assertThat(list(dictionary.get("characteristics"))).isEmpty();
    assertThat(list(dictionary.get("definedTypes"))).isEmpty();
  }

  @Test
  void каналReadJsonОтдаётСловарьИОбъясняетФормуВерсии() throws Exception {
    Path schema = Ssl31SubmodulePaths.projectRoot().resolve(EVENT_LOG);
    ChannelResult ok = readJson(Map.of(
      "op", "cf-dcs-type-dictionary", "objectXml", schema.toString(), "schemaVersion", "V2_20"));
    assertThat(ok.exit()).isZero();
    Map<String, Object> parsed = parseJson(ok.out());
    assertThat(parsed.get("configurationFound")).isEqualTo(true);
    assertThat(ok.out()).contains("\"СправочникСсылка\"");

    // cf-dcs-info того же канала отдаёт структурный тип
    ChannelResult info = readJson(Map.of(
      "op", "cf-dcs-info", "objectXml", schema.toString(), "schemaVersion", "V2_20"));
    assertThat(info.exit()).isZero();
    assertThat(info.out()).contains("\"definedTypes\":[\"Подразделение\"]").contains("\"editable\":true");

    // Номер формата вместо имени: отказ называет нужную форму, а не «2.20 не поддержан»
    ChannelResult dotted = readJson(Map.of(
      "op", "cf-dcs-type-dictionary", "objectXml", schema.toString(), "schemaVersion", "2.20"));
    assertThat(dotted.exit()).isEqualTo(2);
    assertThat(dotted.err()).contains("V2_20").contains("«2.20»").doesNotContain("не поддержан");
  }

  // --------------------------------------------------------------- помощники

  private static Map<String, Object> info(String relativePath) throws Exception {
    return DcsRead.info(Ssl31SubmodulePaths.projectRoot().resolve(relativePath), SchemaVersion.V2_20);
  }

  private static void assertPrimitives(Map<String, Object> dictionary) {
    List<Map<String, Object>> primitives = maps(dictionary.get("primitives"));
    assertThat(primitives).extracting(row -> row.get("type")).containsExactly(
      "xs:string", "xs:decimal", "xs:dateTime", "xs:boolean", "v8:StandardPeriod", "v8:UUID");
    assertThat(byKey(primitives, "type", "xs:string")).isEqualTo(
      Map.of("type", "xs:string", "label", "Строка", "qualifiers", "string"));
    assertThat(byKey(primitives, "type", "xs:decimal").get("qualifiers")).isEqualTo("number");
    assertThat(byKey(primitives, "type", "xs:dateTime").get("qualifiers")).isEqualTo("date");
    assertThat(byKey(primitives, "type", "v8:StandardPeriod")).isEqualTo(
      Map.of("type", "v8:StandardPeriod", "label", "Стандартный период"));
  }

  private static final String CURRENT_CONFIG = "http://v8.1c.ru/8.1/data/enterprise/current-config";

  @SuppressWarnings("unchecked")
  private static Map<String, Object> composition(List<Map<String, Object>> definedTypes, String name) {
    Object composition = byKey(definedTypes, "name", name).get("composition");
    assertThat(composition).as("composition у " + name).isInstanceOf(Map.class);
    return (Map<String, Object>) composition;
  }

  /** Ссылочный тип конфигурации с префиксом на самом элементе, как пишет платформа. */
  private static String configType(String element, String text) {
    return "<v8:" + element + " xmlns:d4p1=\"http://v8.1c.ru/8.1/data/enterprise/current-config\">d4p1:"
      + text + "</v8:" + element + ">";
  }

  /**
   * Копия выгрузки из одного определяемого типа: поиск конфигурации видит {@code Configuration.xml},
   * имена определяемых типов берутся из {@code DefinedTypes}.
   */
  private Path fakeDump() throws IOException {
    Path cf = tempDir.resolve("cf");
    Path source = Ssl31SubmodulePaths.projectRoot().resolve("src/cf");
    Files.createDirectories(cf.resolve("DefinedTypes"));
    Files.copy(source.resolve("Configuration.xml"), cf.resolve("Configuration.xml"));
    Files.copy(source.resolve("DefinedTypes/Подразделение.xml"), cf.resolve("DefinedTypes/Подразделение.xml"));
    return cf;
  }

  /** Тип единственного параметра синтетической схемы макета внутри выгрузки. */
  private static Map<String, Object> parameterType(Path dump, String valueTypeContent) throws Exception {
    Path template = dump.resolve("Reports/Тест/Templates/Макет/Ext/Template.xml");
    Files.createDirectories(template.getParent());
    Files.writeString(template, "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n"
      + "<DataCompositionSchema xmlns=\"http://v8.1c.ru/8.1/data-composition-system/schema\""
      + " xmlns:dcscom=\"http://v8.1c.ru/8.1/data-composition-system/common\""
      + " xmlns:dcscor=\"http://v8.1c.ru/8.1/data-composition-system/core\""
      + " xmlns:dcsset=\"http://v8.1c.ru/8.1/data-composition-system/settings\""
      + " xmlns:v8=\"http://v8.1c.ru/8.1/data/core\" xmlns:v8ui=\"http://v8.1c.ru/8.1/data/ui\""
      + " xmlns:xs=\"http://www.w3.org/2001/XMLSchema\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">\r\n"
      + "\t<dataSource>\r\n\t\t<name>ИсточникДанных1</name>\r\n\t\t<dataSourceType>Local</dataSourceType>\r\n"
      + "\t</dataSource>\r\n"
      + "\t<parameter>\r\n\t\t<name>П</name>\r\n\t\t<valueType>" + valueTypeContent + "</valueType>\r\n"
      + "\t</parameter>\r\n"
      + "</DataCompositionSchema>", StandardCharsets.UTF_8);
    return valueType(rows(DcsRead.info(template, SchemaVersion.V2_20), "parameters").get(0));
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

  /** Все описания типов схемы: параметры, вычисляемые поля, поля наборов и вложенные схемы. */
  private static void walkTypes(Object schema, Consumer<Object> sink) {
    for (Object parameter : JaxbReflect.listOptional(schema, "getParameter")) {
      accept(JaxbReflect.getOptional(parameter, "getValueType"), sink);
    }
    for (Object field : JaxbReflect.listOptional(schema, "getCalculatedField")) {
      accept(JaxbReflect.getOptional(field, "getValueType"), sink);
    }
    for (Object dataSet : JaxbReflect.listOptional(schema, "getDataSet")) {
      walkDataSet(dataSet, sink);
    }
    for (Object nested : JaxbReflect.listOptional(schema, "getNestedSchema")) {
      Object inner = JaxbReflect.getOptional(nested, "getSchema");
      if (inner != null) {
        walkTypes(inner, sink);
      }
    }
  }

  private static void walkDataSet(Object dataSet, Consumer<Object> sink) {
    for (Object field : JaxbReflect.listOptional(dataSet, "getField")) {
      accept(JaxbReflect.getOptional(field, "getValueType"), sink);
    }
    for (Object item : JaxbReflect.listOptional(dataSet, "getItem")) {
      walkDataSet(item, sink);
    }
  }

  private static void accept(Object typeDescription, Consumer<Object> sink) {
    if (typeDescription != null) {
      sink.accept(typeDescription);
    }
  }

  /**
   * Строка ответа с типом: структурный тип рядом со строковым представлением, корпус — целиком в словаре.
   *
   * @param presentationKey ключ строкового представления типа; {@code null} — у владельца его нет
   * @return 1, если у строки есть тип
   */
  private static int checkInfoRow(Map<String, Object> row, String presentationKey) {
    if (!row.containsKey("valueType")) {
      return 0;
    }
    Map<String, Object> valueType = valueType(row);
    assertThat(valueType.get("editable")).as(String.valueOf(row)).isEqualTo(true);
    if (presentationKey != null
      && !(list(valueType.get("types")).isEmpty() && list(valueType.get("typeSets")).isEmpty())) {
      assertThat(row).containsKey(presentationKey);
    }
    return 1;
  }

  /** @return {число блоков типа, из них у полей вложенных наборов объединения} */
  private static int[] checkDataSet(Map<String, Object> dataSet, boolean unionItem) {
    int blocks = 0;
    int unionItemBlocks = 0;
    for (Map<String, Object> field : fields(dataSet)) {
      if (!field.containsKey("valueType")) {
        continue;
      }
      blocks++;
      Map<String, Object> valueType = valueType(field);
      if (unionItem) {
        unionItemBlocks++;
        assertThat(valueType.get("editable")).isEqualTo(false);
        assertThat((String) valueType.get("readOnlyReason")).contains("объединения");
      } else {
        assertThat(valueType.get("editable")).as(String.valueOf(field)).isEqualTo(true);
        if (!list(valueType.get("types")).isEmpty() || !list(valueType.get("typeSets")).isEmpty()) {
          assertThat(field).containsKey("valueTypePresentation");
        }
      }
    }
    if (dataSet.get("items") != null) {
      for (Map<String, Object> item : rows(dataSet, "items")) {
        int[] nested = checkDataSet(item, true);
        blocks += nested[0];
        unionItemBlocks += nested[1];
      }
    }
    return new int[] {blocks, unionItemBlocks};
  }

  private static int count(Pattern pattern, String text) {
    Matcher matcher = pattern.matcher(text);
    int found = 0;
    while (matcher.find()) {
      found++;
    }
    return found;
  }

  @SuppressWarnings("unchecked")
  private static List<Map<String, Object>> rows(Map<String, Object> info, String key) {
    return (List<Map<String, Object>>) info.get(key);
  }

  @SuppressWarnings("unchecked")
  private static List<Map<String, Object>> fields(Map<String, Object> dataSet) {
    Object fields = dataSet.get("fields");
    return fields == null ? List.of() : (List<Map<String, Object>>) fields;
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

  @SuppressWarnings("unchecked")
  private static List<Map<String, Object>> maps(Object value) {
    assertThat(value).isInstanceOf(List.class);
    return (List<Map<String, Object>>) value;
  }

  private static Map<String, Object> byKey(List<Map<String, Object>> rows, String key, String value) {
    return rows.stream()
      .filter(row -> value.equals(row.get(key)))
      .findFirst()
      .orElseThrow(() -> new AssertionError(key + "=" + value + " не найден: " + rows));
  }

  /** Результат канала read-json: код выхода, stdout и stderr в UTF-8. */
  private record ChannelResult(int exit, String out, String err) {
  }

  /** Запуск канала read-json с параметрами из UTF-8 JSON-файла. */
  private ChannelResult readJson(Map<String, ?> params) throws Exception {
    Path paramsFile = Files.createTempFile(tempDir, "params", ".json");
    Files.writeString(paramsFile, new Gson().toJson(params), StandardCharsets.UTF_8);
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    ByteArrayOutputStream err = new ByteArrayOutputStream();
    PrintStream prevOut = System.out;
    PrintStream prevErr = System.err;
    int exit;
    try {
      System.setOut(new PrintStream(out, true, StandardCharsets.UTF_8));
      System.setErr(new PrintStream(err, true, StandardCharsets.UTF_8));
      exit = new CommandLine(new DesignerXmlCli()).execute("read-json", "--params", paramsFile.toString());
    } finally {
      System.setOut(prevOut);
      System.setErr(prevErr);
    }
    return new ChannelResult(exit, out.toString(StandardCharsets.UTF_8), err.toString(StandardCharsets.UTF_8));
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> parseJson(String json) {
    Map<String, Object> parsed = new Gson().fromJson(json, Map.class);
    assertThat(parsed).isNotNull();
    return parsed;
  }
}
