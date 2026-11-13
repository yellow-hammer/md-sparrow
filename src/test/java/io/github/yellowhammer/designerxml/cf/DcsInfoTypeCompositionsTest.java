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
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Составы определяемых типов и характеристик в ответе {@code cf-dcs-info}: ключ верхнего уровня
 * {@code typeCompositions}.
 *
 * <p>В схеме компоновки определяемый тип записан одним UUID, а характеристика — набором
 * {@code cfg:Characteristic.X}; ссылки, по которым редактор разыменовывает поле «через точку»,
 * лежат в описании другого объекта. Проверяется, что каждый такой тип полей наборов (и вложенных
 * наборов объединений), вычисляемых полей и параметров получает ровно одну запись, составы равны
 * словарю окна типа и типу плана видов характеристик, проект EDT отвечает так же, а сам
 * {@code valueType} составов не содержит и его эхо не меняет файл ни на байт.
 */
class DcsInfoTypeCompositionsTest {

  /** Поля и параметр определяемых типов ЗначениеДоступа (25 ссылок) и ВладелецНастроекПрав. */
  private static final String ACCESS_RIGHTS = "src/cf/Reports/АнализПравДоступа/Templates/Макет/Ext/Template.xml";

  /** Параметр с характеристиками плана видов характеристик. */
  private static final String CHARACTERISTICS =
    "src/cf/Reports/ДатыЗапретаЗагрузки/Templates/ОсновнаяСхемаКомпоновкиДанных/Ext/Template.xml";

  /** Объединение с типами у полей и вложенных наборов, но без определяемых типов и характеристик. */
  private static final String UNION =
    "src/cf/Reports/ОбъемНенужныхФайлов/Templates/ОсновнаяСхемаКомпоновкиДанных/Ext/Template.xml";

  private static final String ACCESS_VALUE_ID = "9a33b5dc-a8f1-496e-9961-95890984cd86";

  private static final String RIGHTS_OWNER_ID = "a02405f8-0a56-4b5a-a885-91715df29d5c";

  private static final String CURRENT_CONFIG = "http://v8.1c.ru/8.1/data/enterprise/current-config";

  /** Ключи {@code valueType}, которые принимает писатель типов: новых ключей в типе быть не должно. */
  private static final Set<String> WRITER_KEYS = Set.of(
    "types", "typeSets", "typeIds", "definedTypes", "stringQualifiers", "numberQualifiers",
    "dateQualifiers", "binaryDataQualifiers", "editable", "readOnlyReason");

  private static final Gson GSON = new Gson();

  @TempDir
  Path tempDir;

  // --------------------------------------------------------------- целевые схемы

  @Test
  void определяемыйТипСхемыПолучаетЗаписьПоUuidСИменемИСоставом() throws Exception {
    Map<String, Object> info = info(ACCESS_RIGHTS);
    // Ключ идёт последним, после ключей схемы
    assertThat(new ArrayList<>(info.keySet())).containsExactly("dataSets", "calculatedFields", "totalFields",
      "parameters", "dataSources", "dataSetLinks", "nestedSchemas", "settingsVariants", "typeCompositions");

    List<Map<String, Object>> compositions = rows(info, "typeCompositions");
    assertThat(compositions).extracting(row -> row.get("source"))
      .containsExactly(ACCESS_VALUE_ID, RIGHTS_OWNER_ID);
    Map<String, Object> accessValue = compositions.get(0);
    assertThat(new ArrayList<>(accessValue.keySet())).containsExactly("source", "name", "types", "typeSets");
    assertThat(accessValue.get("name")).isEqualTo("ЗначениеДоступа");
    assertThat(strings(accessValue.get("types"))).hasSize(25)
      .allSatisfy(type -> assertThat(type).matches("cfg:\\w+Ref\\..+"));
    assertThat(strings(accessValue.get("typeSets"))).isEmpty();
    assertThat(compositions.get(1).get("name")).isEqualTo("ВладелецНастроекПрав");

    // Состав тот же, что у словаря окна типа (без квалификаторов — они у словаря)
    Map<String, Map<String, Object>> dictionary = dictionaryCompositions(Ssl31SubmodulePaths.projectRoot()
      .resolve(ACCESS_RIGHTS));
    for (Map<String, Object> row : compositions) {
      Map<String, Object> expected = dictionary.get((String) row.get("name"));
      assertThat(row.get("types")).as((String) row.get("name")).isEqualTo(expected.get("types"));
      assertThat(row.get("typeSets")).as((String) row.get("name")).isEqualTo(expected.get("typeSets"));
    }

    // Тип параметра — UUID с именем, без состава внутри: его эхо уходит в писатель как есть
    Map<String, Object> parameter = valueType(byKey(rows(info, "parameters"), "name", "ЗначениеДоступа"));
    assertThat(parameter).isEqualTo(Map.of("types", List.of(), "typeSets", List.of(),
      "typeIds", List.of(ACCESS_VALUE_ID), "definedTypes", List.of("ЗначениеДоступа"), "editable", true));
  }

  @Test
  void характеристикаПараметраДаётЗаписьСТипомПланаВидовХарактеристик() throws Exception {
    Map<String, Object> info = info(CHARACTERISTICS);
    List<Map<String, Object>> compositions = rows(info, "typeCompositions");
    assertThat(compositions).hasSize(1);
    Map<String, Object> row = compositions.get(0);
    // У набора имени нет: оно уже в source
    assertThat(new ArrayList<>(row.keySet())).containsExactly("source", "types", "typeSets");
    assertThat(row.get("source")).isEqualTo("cfg:Characteristic.РазделыДатЗапретаИзменения");
    MdTypeDescriptionDto chart = MdObjectPropertiesEdit.readDto(Ssl31SubmodulePaths.projectRoot()
        .resolve("src/cf/ChartsOfCharacteristicTypes/РазделыДатЗапретаИзменения.xml"), SchemaVersion.V2_20)
      .chartOfCharacteristicTypes.type;
    assertThat(row.get("types")).isEqualTo(chart.types);
    assertThat(row.get("typeSets")).isEqualTo(chart.typeSets);
    assertThat(strings(row.get("types"))).hasSize(4);
    assertThat(valueType(byKey(rows(info, "parameters"), "name", "Объекты")).get("typeSets"))
      .isEqualTo(List.of("cfg:Characteristic.РазделыДатЗапретаИзменения"));
  }

  @Test
  void безОпределяемыхТиповВнеВыгрузкиИБезОписанияКлючаНет() throws Exception {
    // Типы есть (и у полей вложенных наборов объединения), но наборов-составов среди них нет
    assertThat(info(UNION)).doesNotContainKey("typeCompositions");

    // Вне выгрузки составы взять неоткуда: ключа нет, ответ без ошибки, UUID в типе как был
    Path alone = tempDir.resolve("Отдельно").resolve("Template.xml");
    copyFile(Ssl31SubmodulePaths.projectRoot().resolve(ACCESS_RIGHTS), alone);
    Map<String, Object> outside = DcsRead.info(alone, SchemaVersion.V2_20);
    assertThat(outside).doesNotContainKey("typeCompositions");
    assertThat(valueType(byKey(rows(outside, "parameters"), "name", "ЗначениеДоступа")).get("typeIds"))
      .isEqualTo(List.of(ACCESS_VALUE_ID));

    // В выгрузке нет описания одного из типов: его записи нет, у второго — есть
    Path source = Ssl31SubmodulePaths.projectRoot().resolve("src/cf");
    Path cf = tempDir.resolve("dump").resolve("cf");
    copyFile(source.resolve("Configuration.xml"), cf.resolve("Configuration.xml"));
    copyFile(source.resolve("DefinedTypes/ВладелецНастроекПрав.xml"), cf.resolve("DefinedTypes/ВладелецНастроекПрав.xml"));
    Path schema = cf.resolve(ACCESS_RIGHTS.substring("src/cf/".length()));
    copyFile(Ssl31SubmodulePaths.projectRoot().resolve(ACCESS_RIGHTS), schema);
    assertThat(rows(DcsRead.info(schema, SchemaVersion.V2_20), "typeCompositions"))
      .extracting(row -> row.get("source")).containsExactly(RIGHTS_OWNER_ID);
  }

  // --------------------------------------------------------------- синтетика

  @Test
  void записиВПорядкеПервогоВхожденияБезПовторовИзВсехВладельцевТипа() throws Exception {
    Path cf = tempDir.resolve("syn").resolve("cf");
    copyFile(Ssl31SubmodulePaths.configurationXml(), cf.resolve("Configuration.xml"));
    String a = "aaaaaaaa-1111-2222-3333-444444444444";
    definedType(cf, "А", a, type("xs:string") + type("cfg:CatalogRef.Валюты") + typeSet("cfg:AnyIBRef"));
    definedType(cf, "Б", "bbbbbbbb-1111-2222-3333-444444444444", type("xs:boolean") + typeSet("cfg:DefinedType.Г"));
    definedType(cf, "Г", "cccccccc-1111-2222-3333-444444444444", type("xs:decimal"));
    characteristic(cf, "В", type("cfg:CatalogRef.Валюты") + type("xs:string"));

    Path template = cf.resolve("Reports/Тест/Templates/Макет/Ext/Template.xml");
    copyFile(null, template);
    Files.writeString(template, schemaXml(
      "\t<dataSet xsi:type=\"DataSetUnion\">\r\n"
        + "\t\t<name>Объединение</name>\r\n"
        + field("\t\t", "Б", configTypeSet("d4p1", "DefinedType.Б"))
        + "\t\t<item xsi:type=\"DataSetObject\">\r\n"
        + "\t\t\t<name>Вложенный</name>\r\n"
        + field("\t\t\t", "В", configTypeSet("d5p1", "Characteristic.В"))
        + "\t\t\t<dataSource>ИсточникДанных1</dataSource>\r\n"
        + "\t\t\t<objectName>Вложенный</objectName>\r\n"
        + "\t\t</item>\r\n"
        + "\t</dataSet>\r\n"
        + "\t<calculatedField>\r\n\t\t<dataPath>Вычисляемое</dataPath>\r\n\t\t<expression>1</expression>\r\n"
        + "\t\t<valueType><v8:TypeId>" + a.toUpperCase(Locale.ROOT) + "</v8:TypeId></valueType>\r\n"
        + "\t</calculatedField>\r\n"
        + parameter("П1", "<v8:TypeId>" + a + "</v8:TypeId>")
        + parameter("П2", "<v8:TypeId>00000000-0000-0000-0000-000000000001</v8:TypeId>")
        + parameter("П3", configTypeSet("d4p1", "Characteristic.В"))
        + parameter("П4", configTypeSet("d4p1", "Characteristic.НетТакого"))
        + parameter("П5", "<v8:Type>xs:string</v8:Type>" + configTypeSet("d4p1", "CatalogRef"))),
      StandardCharsets.UTF_8);

    Map<String, Object> info = DcsRead.info(template, SchemaVersion.V2_20);
    // Поле объединения, поле вложенного набора, вычисляемое поле, параметры — по порядку ответа;
    // тот же UUID другим регистром и повтор характеристики записей не дублируют; неизвестный UUID,
    // набор без описания и набор вида записей не дают; вложенный определяемый тип подставлен
    assertThat(rows(info, "typeCompositions")).containsExactly(
      Map.of("source", "cfg:DefinedType.Б", "types", List.of("xs:boolean", "xs:decimal"), "typeSets", List.of()),
      Map.of("source", "cfg:Characteristic.В", "types", List.of("cfg:CatalogRef.Валюты", "xs:string"),
        "typeSets", List.of()),
      Map.of("source", a.toUpperCase(Locale.ROOT), "name", "А",
        "types", List.of("xs:string", "cfg:CatalogRef.Валюты"), "typeSets", List.of("cfg:AnyIBRef")));
    // Типы владельцев составами не раскрываются: набор DefinedType в схеме только читается
    Map<String, Object> union = rows(info, "dataSets").get(0);
    assertThat(valueType(byKey(fields(union), "dataPath", "Б")).get("editable")).isEqualTo(false);
    assertThat(valueType(byKey(rows(info, "parameters"), "name", "П1"))).isEqualTo(Map.of("types", List.of(),
      "typeSets", List.of(), "typeIds", List.of(a), "definedTypes", List.of("А"), "editable", true));
  }

  @Test
  void схемаПроектаEdtДаётЗаписиВЗаписиСхемыСЛюбойСсылкойAnyRef() throws Exception {
    Path src = tempDir.resolve("edt").resolve("Проект").resolve("src");
    copyFile(Path.of(System.getProperty("fixtures.ssl31edt.root"), "ssl31", "src", "Configuration", "Configuration.mdo"),
      src.resolve("Configuration/Configuration.mdo"));
    String typeId = "dddddddd-1111-2222-3333-444444444444";
    edtDefinedType(src, "Любой", typeId, "AnyRef", "Boolean", "ValueStorage");
    Path template = src.resolve("Reports/Тест/Templates/Макет/Template.dcs");
    copyFile(null, template);
    Files.writeString(template, schemaXml(parameter("П", "<v8:TypeId>" + typeId + "</v8:TypeId>")),
      StandardCharsets.UTF_8);

    // Класс составов отдаёт запись конфигуратора, ответ схемы EDT — запись своего файла
    assertThat(TypeCompositions.of(template).ofDefinedTypeId(typeId).typeSets).containsExactly("cfg:AnyIBRef");
    assertThat(rows(DcsRead.info(template, SchemaVersion.V2_20), "typeCompositions")).containsExactly(
      Map.of("source", typeId, "name", "Любой", "types", List.of("xs:boolean", "v8:ValueStorage"),
        "typeSets", List.of("cfg:AnyRef")));
  }

  // --------------------------------------------------------------- корпуса

  @Test
  void корпусSsl31ЗаписьУКаждогоСоставаИЛишнихНет() throws Exception {
    List<Path> schemas = schemaFiles(Ssl31SubmodulePaths.projectRoot().resolve("src"));
    assertThat(schemas).hasSize(74);
    Map<String, Map<String, Object>> dictionary = dictionaryCompositions(Ssl31SubmodulePaths.projectRoot()
      .resolve(ACCESS_RIGHTS));
    int withKey = 0;
    int byId = 0;
    int byTypeSet = 0;
    for (Path schema : schemas) {
      Map<String, Object> info = DcsRead.info(schema, SchemaVersion.V2_20);
      List<Map<String, Object>> types = infoTypes(info);
      assertThat(types).allSatisfy(type -> assertThat(WRITER_KEYS).containsAll(type.keySet()));
      Set<String> expected = expectedSources(types);
      if (expected.isEmpty()) {
        assertThat(info).as(schema.toString()).doesNotContainKey("typeCompositions");
        continue;
      }
      withKey++;
      List<Map<String, Object>> compositions = rows(info, "typeCompositions");
      // Ровно встреченные наборы в порядке первого вхождения: все составы корпуса находятся
      assertThat(compositions).as(schema.toString())
        .extracting(row -> sourceKey((String) row.get("source"))).containsExactlyElementsOf(expected);
      for (Map<String, Object> row : compositions) {
        String source = (String) row.get("source");
        if (source.startsWith("cfg:Characteristic.")) {
          byTypeSet++;
          String chart = source.substring("cfg:Characteristic.".length());
          MdTypeDescriptionDto own = MdObjectPropertiesEdit.readDto(ConfigurationLanguage.configurationFileOf(schema)
            .resolveSibling("ChartsOfCharacteristicTypes").resolve(chart + ".xml"), SchemaVersion.V2_20)
            .chartOfCharacteristicTypes.type;
          assertThat(row.get("types")).as(source).isEqualTo(own.types);
          assertThat(row.get("typeSets")).as(source).isEqualTo(own.typeSets);
        } else {
          byId++;
          Map<String, Object> composition = dictionary.get((String) row.get("name"));
          assertThat(composition).as(source).isNotNull();
          assertThat(row.get("types")).as(source).isEqualTo(composition.get("types"));
          assertThat(row.get("typeSets")).as(source).isEqualTo(composition.get("typeSets"));
        }
      }
    }
    // 7 схем с UUID (в двух — по два определяемых типа) и характеристики в двух отчётах дат запрета
    assertThat(withKey).isEqualTo(9);
    assertThat(byId).isEqualTo(9);
    assertThat(byTypeSet).isEqualTo(2);
  }

  @Test
  void проектEdtОтвечаетТакЖеКакВыгрузка() throws Exception {
    Path cfRoot = Ssl31SubmodulePaths.projectRoot().resolve("src/cf");
    Path edtRoot = Path.of(System.getProperty("fixtures.ssl31edt.root"), "ssl31", "src");
    int pairs = 0;
    int withKey = 0;
    for (Path schema : schemaFiles(cfRoot)) {
      String relative = cfRoot.relativize(schema).toString().replace('\\', '/');
      assertThat(relative).endsWith("/Ext/Template.xml");
      Path edt = edtRoot.resolve(relative.substring(0, relative.length() - "/Ext/Template.xml".length()))
        .resolve("Template.dcs");
      if (!Files.isRegularFile(edt)) {
        continue;
      }
      pairs++;
      Object designer = DcsRead.info(schema, SchemaVersion.V2_20).get("typeCompositions");
      Object project = DcsRead.info(edt, SchemaVersion.V2_20).get("typeCompositions");
      assertThat(project).as(relative).isEqualTo(designer);
      if (designer != null) {
        withKey++;
      }
    }
    // У каждой из 69 схем выгрузки есть пара в проекте EDT
    assertThat(pairs).isEqualTo(69);
    assertThat(withKey).isEqualTo(9);
  }

  /**
   * Эхо всех блоков типа корпуса — в копиях внутри выгрузки, где в ответе есть {@code typeCompositions}
   * (у отдельной копии вне выгрузки ключа нет): ни один файл не меняется.
   */
  @Test
  void эхоВсехБлоковТипаВВыгрузкеСоСоставамиНеМеняетНиБайта() throws Exception {
    Path source = Ssl31SubmodulePaths.projectRoot().resolve("src");
    Path copyRoot = tempDir.resolve("echo").resolve("src");
    // Выгрузки корпуса без объектов: описания конфигурации, определяемые типы, планы видов характеристик
    try (Stream<Path> files = Files.walk(source, 3)) {
      for (Path configuration : files.filter(file -> file.getFileName().toString().equals("Configuration.xml")).toList()) {
        Path dump = configuration.getParent();
        copyFile(configuration, copyRoot.resolve(source.relativize(configuration).toString()));
        for (String directory : List.of("DefinedTypes", "ChartsOfCharacteristicTypes")) {
          if (!Files.isDirectory(dump.resolve(directory))) {
            continue;
          }
          try (Stream<Path> described = Files.list(dump.resolve(directory))) {
            for (Path file : described.filter(Files::isRegularFile).toList()) {
              copyFile(file, copyRoot.resolve(source.relativize(file).toString()));
            }
          }
        }
      }
    }

    int echoed = 0;
    int unionItems = 0;
    int withKey = 0;
    List<Path> schemas = schemaFiles(source);
    assertThat(schemas).hasSize(74);
    for (Path schema : schemas) {
      Path copy = copyRoot.resolve(source.relativize(schema).toString());
      copyFile(schema, copy);
      byte[] before = Files.readAllBytes(copy);
      Map<String, Object> info = DcsRead.info(copy, SchemaVersion.V2_20);
      // Копия в мини-выгрузке отвечает так же, как схема корпуса на месте
      assertThat(info.get("typeCompositions")).as(schema.toString())
        .isEqualTo(DcsRead.info(schema, SchemaVersion.V2_20).get("typeCompositions"));
      if (info.containsKey("typeCompositions")) {
        withKey++;
      }
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
    // 732 блока: 698 адресуют мутации, 2 — поля вложенных наборов объединения, 32 — во вложенных схемах
    assertThat(echoed).isEqualTo(698);
    assertThat(unionItems).isEqualTo(2);
    assertThat(withKey).isEqualTo(9);
  }

  @Test
  void каналReadJsonПишетКлючПоследним() throws Exception {
    Path schema = Ssl31SubmodulePaths.projectRoot().resolve(ACCESS_RIGHTS);
    Path paramsFile = Files.createTempFile(tempDir, "params", ".json");
    Files.writeString(paramsFile, GSON.toJson(Map.of(
      "op", "cf-dcs-info", "objectXml", schema.toString(), "schemaVersion", "V2_20")), StandardCharsets.UTF_8);
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    PrintStream previous = System.out;
    int exit;
    try {
      System.setOut(new PrintStream(out, true, StandardCharsets.UTF_8));
      exit = new CommandLine(new DesignerXmlCli()).execute("read-json", "--params", paramsFile.toString());
    } finally {
      System.setOut(previous);
    }
    String json = out.toString(StandardCharsets.UTF_8);
    assertThat(exit).isZero();
    assertThat(json).contains("\"typeCompositions\":[{\"source\":\"" + ACCESS_VALUE_ID
      + "\",\"name\":\"ЗначениеДоступа\",\"types\":[\"cfg:");
    assertThat(json.indexOf("\"typeCompositions\"")).isGreaterThan(json.indexOf("\"settingsVariants\""));
  }

  // --------------------------------------------------------------- помощники

  private static Map<String, Object> info(String relativePath) throws Exception {
    return DcsRead.info(Ssl31SubmodulePaths.projectRoot().resolve(relativePath), SchemaVersion.V2_20);
  }

  /** Составы определяемых типов словаря окна типа по имени: с ними сверяются записи по UUID. */
  @SuppressWarnings("unchecked")
  private static Map<String, Map<String, Object>> dictionaryCompositions(Path schema) throws Exception {
    Map<String, Map<String, Object>> out = new LinkedHashMap<>();
    for (Object row : (List<Object>) DcsTypeDictionary.read(schema, SchemaVersion.V2_20).get("definedTypes")) {
      Map<String, Object> map = (Map<String, Object>) row;
      if (map.get("composition") instanceof Map<?, ?> composition) {
        out.put((String) map.get("name"), (Map<String, Object>) composition);
      }
    }
    return out;
  }

  /** Типы ответа в порядке чтения: поля наборов (и вложенных наборов), вычисляемые поля, параметры. */
  private static List<Map<String, Object>> infoTypes(Map<String, Object> info) {
    List<Map<String, Object>> out = new ArrayList<>();
    for (Map<String, Object> dataSet : rows(info, "dataSets")) {
      dataSetTypes(dataSet, out);
    }
    for (Map<String, Object> field : rows(info, "calculatedFields")) {
      addType(field, out);
    }
    for (Map<String, Object> parameter : rows(info, "parameters")) {
      addType(parameter, out);
    }
    return out;
  }

  private static void dataSetTypes(Map<String, Object> dataSet, List<Map<String, Object>> out) {
    for (Map<String, Object> field : fields(dataSet)) {
      addType(field, out);
    }
    for (Map<String, Object> item : rows(dataSet, "items")) {
      dataSetTypes(item, out);
    }
  }

  private static void addType(Map<String, Object> row, List<Map<String, Object>> out) {
    if (row.containsKey("valueType")) {
      out.add(valueType(row));
    }
  }

  /**
   * Источники, которые должны получить запись: наборы-составы из {@code typeSets} и UUID с
   * найденными именами, в порядке первого вхождения (UUID — без учёта регистра).
   */
  private static Set<String> expectedSources(List<Map<String, Object>> types) {
    Set<String> out = new LinkedHashSet<>();
    for (Map<String, Object> type : types) {
      for (String typeSet : strings(type.get("typeSets"))) {
        if (typeSet.startsWith("cfg:DefinedType.") || typeSet.startsWith("cfg:Characteristic.")) {
          out.add(typeSet);
        }
      }
      if (type.containsKey("definedTypes")) {
        for (String typeId : strings(type.get("typeIds"))) {
          out.add(sourceKey(typeId));
        }
      }
    }
    return out;
  }

  private static String sourceKey(String source) {
    return source.startsWith("cfg:") ? source : source.trim().toLowerCase(Locale.ROOT);
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

  /** Копия файла с созданием каталогов; {@code from == null} — только каталоги. */
  private static void copyFile(Path from, Path to) throws IOException {
    Files.createDirectories(to.getParent());
    if (from != null) {
      Files.copy(from, to);
    }
  }

  /** Payload эха: прочитанный тип через JSON, как его шлёт канал. */
  private static Map<String, Object> echo(Map<String, Object> row) {
    return channel(Map.of("valueType", row.get("valueType")));
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> channel(Map<String, Object> payload) {
    return GSON.fromJson(GSON.toJson(payload), Map.class);
  }

  private static String schemaXml(String body) {
    return "﻿<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n"
      + "<DataCompositionSchema xmlns=\"http://v8.1c.ru/8.1/data-composition-system/schema\""
      + " xmlns:dcscom=\"http://v8.1c.ru/8.1/data-composition-system/common\""
      + " xmlns:dcscor=\"http://v8.1c.ru/8.1/data-composition-system/core\""
      + " xmlns:dcsset=\"http://v8.1c.ru/8.1/data-composition-system/settings\""
      + " xmlns:v8=\"http://v8.1c.ru/8.1/data/core\" xmlns:v8ui=\"http://v8.1c.ru/8.1/data/ui\""
      + " xmlns:xs=\"http://www.w3.org/2001/XMLSchema\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">\r\n"
      + "\t<dataSource>\r\n\t\t<name>ИсточникДанных1</name>\r\n\t\t<dataSourceType>Local</dataSourceType>\r\n"
      + "\t</dataSource>\r\n"
      + body
      + "</DataCompositionSchema>";
  }

  private static String field(String indent, String name, String valueTypeContent) {
    return indent + "<field xsi:type=\"DataSetFieldField\">\r\n"
      + indent + "\t<dataPath>" + name + "</dataPath>\r\n"
      + indent + "\t<field>" + name + "</field>\r\n"
      + indent + "\t<valueType>" + valueTypeContent + "</valueType>\r\n"
      + indent + "</field>\r\n";
  }

  private static String parameter(String name, String valueTypeContent) {
    return "\t<parameter>\r\n\t\t<name>" + name + "</name>\r\n\t\t<valueType>" + valueTypeContent
      + "</valueType>\r\n\t</parameter>\r\n";
  }

  /** Набор конфигурации с префиксом на самом элементе, как пишет платформа. */
  private static String configTypeSet(String prefix, String text) {
    return "<v8:TypeSet xmlns:" + prefix + "=\"" + CURRENT_CONFIG + "\">" + prefix + ":" + text + "</v8:TypeSet>";
  }

  private static String type(String text) {
    return "\t\t\t\t<v8:Type>" + text + "</v8:Type>\r\n";
  }

  private static String typeSet(String text) {
    return "\t\t\t\t<v8:TypeSet>" + text + "</v8:TypeSet>\r\n";
  }

  private static String header() {
    return "﻿<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n"
      + "<MetaDataObject xmlns=\"http://v8.1c.ru/8.3/MDClasses\" xmlns:cfg=\"" + CURRENT_CONFIG + "\""
      + " xmlns:v8=\"http://v8.1c.ru/8.1/data/core\" xmlns:xr=\"http://v8.1c.ru/8.3/xcf/readable\""
      + " xmlns:xs=\"http://www.w3.org/2001/XMLSchema\" version=\"2.20\">\r\n";
  }

  /** Определяемый тип выгрузки: UUID сгенерированного типа и состав в свойствах. */
  private static void definedType(Path dump, String name, String typeId, String typeBody) throws IOException {
    Path file = dump.resolve("DefinedTypes").resolve(name + ".xml");
    copyFile(null, file);
    Files.writeString(file, header()
      + "\t<DefinedType uuid=\"" + java.util.UUID.randomUUID() + "\">\r\n"
      + "\t\t<InternalInfo>\r\n"
      + "\t\t\t<xr:GeneratedType name=\"DefinedType." + name + "\" category=\"DefinedType\">\r\n"
      + "\t\t\t\t<xr:TypeId>" + typeId + "</xr:TypeId>\r\n"
      + "\t\t\t\t<xr:ValueId>" + java.util.UUID.randomUUID() + "</xr:ValueId>\r\n"
      + "\t\t\t</xr:GeneratedType>\r\n"
      + "\t\t</InternalInfo>\r\n"
      + "\t\t<Properties>\r\n"
      + "\t\t\t<Name>" + name + "</Name>\r\n"
      + "\t\t\t<Comment/>\r\n"
      + "\t\t\t<Type>\r\n" + typeBody + "\t\t\t</Type>\r\n"
      + "\t\t</Properties>\r\n"
      + "\t</DefinedType>\r\n"
      + "</MetaDataObject>", StandardCharsets.UTF_8);
  }

  /** План видов характеристик выгрузки: состав — тип значения в его свойствах. */
  private static void characteristic(Path dump, String name, String typeBody) throws IOException {
    Path file = dump.resolve("ChartsOfCharacteristicTypes").resolve(name + ".xml");
    copyFile(null, file);
    Files.writeString(file, header()
      + "\t<ChartOfCharacteristicTypes uuid=\"" + java.util.UUID.randomUUID() + "\">\r\n"
      + "\t\t<Properties>\r\n"
      + "\t\t\t<Name>" + name + "</Name>\r\n"
      + "\t\t\t<Type>\r\n" + typeBody + "\t\t\t</Type>\r\n"
      + "\t\t</Properties>\r\n"
      + "\t</ChartOfCharacteristicTypes>\r\n"
      + "</MetaDataObject>", StandardCharsets.UTF_8);
  }

  /** Определяемый тип проекта EDT: UUID атрибутом контейнера, состав именами EDT. */
  private static void edtDefinedType(Path src, String name, String typeId, String... types) throws IOException {
    StringBuilder body = new StringBuilder();
    for (String type : types) {
      body.append("    <types>").append(type).append("</types>\n");
    }
    Path file = src.resolve("DefinedTypes").resolve(name).resolve(name + ".mdo");
    copyFile(null, file);
    Files.writeString(file, "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
      + "<mdclass:DefinedType xmlns:mdclass=\"http://g5.1c.ru/v8/dt/metadata/mdclass\" uuid=\""
      + java.util.UUID.randomUUID() + "\">\n"
      + "  <producedTypes>\n"
      + "    <containerType typeId=\"" + typeId + "\" valueTypeId=\"" + java.util.UUID.randomUUID() + "\"/>\n"
      + "  </producedTypes>\n"
      + "  <name>" + name + "</name>\n"
      + "  <type>\n" + body + "  </type>\n"
      + "</mdclass:DefinedType>\n", StandardCharsets.UTF_8);
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
  private static List<String> strings(Object value) {
    assertThat(value).isInstanceOf(List.class);
    return (List<String>) value;
  }

  private static Map<String, Object> byKey(List<Map<String, Object>> rows, String key, String value) {
    return rows.stream()
      .filter(row -> value.equals(row.get(key)))
      .findFirst()
      .orElseThrow(() -> new AssertionError(key + "=" + value + " не найден: " + rows));
  }
}
