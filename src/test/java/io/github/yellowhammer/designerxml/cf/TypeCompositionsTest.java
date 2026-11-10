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

import io.github.yellowhammer.designerxml.SchemaVersion;
import io.github.yellowhammer.designerxml.Ssl31SubmodulePaths;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Составы определяемых типов и характеристик ({@link TypeCompositions}).
 *
 * <p>Корпус ssl31 сверяется тремя независимыми путями: выгрузка (разбор текста описания), проект EDT
 * (метамодель через {@code EdtObjectProperties.readType}) и прямое чтение первого описания типа
 * регулярным выражением. Вложенность, цикл, ненайденные описания и имена EDT без точки — на
 * синтетических выгрузках во временном каталоге.
 */
class TypeCompositionsTest {

  private static final String CURRENT_CONFIG = "http://v8.1c.ru/8.1/data/enterprise/current-config";

  private static final String EVENT_LOG_EDT =
    "Reports/АнализЖурналаРегистрации/Templates/ОсновнаяСхемаКомпоновкиДанных/Template.dcs";

  /** Прямое чтение выгрузки: первый {@code <Type>} свойств. */
  private static final Pattern DIRECT_DESIGNER_BODY = Pattern.compile("(?s)<Properties>.*?<Type>(.*?)</Type>");

  private static final Pattern DIRECT_DESIGNER_ENTRY = Pattern.compile(
    "<v8:(Type|TypeSet)>\\s*([^<\\s]+)\\s*</v8:(?:Type|TypeSet)>");

  /** Прямое чтение EDT: первый {@code <type>} и таблица примитивов. */
  private static final Pattern DIRECT_EDT_BODY = Pattern.compile("(?s)<type>(.*?)</type>");

  private static final Pattern DIRECT_EDT_ENTRY = Pattern.compile("<types>\\s*([^<\\s]+)\\s*</types>");

  private static final Map<String, String> DIRECT_EDT_PRIMITIVES = Map.of(
    "String", "xs:string", "Number", "xs:decimal", "Boolean", "xs:boolean", "Date", "xs:dateTime",
    "UUID", "v8:UUID", "StandardPeriod", "v8:StandardPeriod");

  @TempDir
  Path tempDir;

  // --------------------------------------------------------------- корпус

  @Test
  void составыВыгрузкиИПроектаEdtСовпадаютИРавныПрямомуЧтениюОписаний() throws Exception {
    Path cf = Ssl31SubmodulePaths.configurationXml();
    Path edt = edtConfiguration();
    TypeCompositions designer = TypeCompositions.ofConfiguration(cf);
    TypeCompositions project = TypeCompositions.ofConfiguration(edt);

    List<String> definedTypes = names(cf.resolveSibling("DefinedTypes"), ".xml");
    assertThat(definedTypes).hasSize(76);
    for (String name : definedTypes) {
      MdTypeDescriptionDto fromDump = designer.ofTypeSet("cfg:DefinedType." + name);
      MdTypeDescriptionDto fromEdt = project.ofTypeSet("cfg:DefinedType." + name);
      assertThat(fromDump).as(name).isNotNull();
      assertThat(fromEdt).as(name).isNotNull();
      assertThat(fromEdt.types).as(name).isEqualTo(fromDump.types);
      assertThat(fromEdt.typeSets).as(name).isEqualTo(fromDump.typeSets);

      // Вложенных наборов в корпусе нет: состав равен типам первого описания в порядке файла
      MdTypeDescriptionDto directDump = directDesigner(cf.resolveSibling("DefinedTypes").resolve(name + ".xml"));
      MdTypeDescriptionDto directEdt = directEdt(
        edt.getParent().getParent().resolve("DefinedTypes").resolve(name).resolve(name + ".mdo"));
      assertThat(fromDump.types).as(name).isEqualTo(directDump.types);
      assertThat(fromDump.typeSets).as(name).isEqualTo(directDump.typeSets);
      assertThat(fromEdt.types).as(name).isEqualTo(directEdt.types);
      assertThat(fromEdt.typeSets).as(name).isEqualTo(directEdt.typeSets);
    }

    List<String> characteristics = names(cf.resolveSibling("ChartsOfCharacteristicTypes"), ".xml");
    assertThat(characteristics).hasSize(5);
    for (String name : characteristics) {
      MdTypeDescriptionDto fromDump = designer.ofTypeSet("cfg:Characteristic." + name);
      MdTypeDescriptionDto fromEdt = project.ofTypeSet("cfg:Characteristic." + name);
      assertThat(fromDump).as(name).isNotNull();
      assertThat(fromEdt.types).as(name).isEqualTo(fromDump.types);
      assertThat(fromEdt.typeSets).as(name).isEqualTo(fromDump.typeSets);
      // Тип плана видов характеристик — тот же, что читает модель свойств объекта
      MdTypeDescriptionDto model = MdObjectPropertiesEdit.readDto(
        cf.resolveSibling("ChartsOfCharacteristicTypes").resolve(name + ".xml"), SchemaVersion.V2_20)
        .chartOfCharacteristicTypes.type;
      assertThat(fromDump.types).as(name).isEqualTo(model.types);
      assertThat(fromDump.typeSets).as(name).isEqualTo(model.typeSets);
    }

    assertThat(designer.ofTypeSet("cfg:DefinedType.ФизическоеЛицо").types)
      .containsExactly("cfg:CatalogRef._ДемоФизическиеЛица");
    assertThat(designer.ofTypeSet("cfg:DefinedType.ЗначениеДоступа").types).hasSize(25);
    assertThat(designer.ofTypeSet("cfg:Characteristic._ДемоВидыСубконто").types).hasSize(7)
      .allSatisfy(type -> assertThat(type).matches("cfg:(Catalog|Enum)Ref\\..+"));
  }

  @Test
  void определяемыйТипПоUuidБезУчётаРегистраИЗаписиВПорядкеПервогоОбращения() throws Exception {
    TypeCompositions compositions = TypeCompositions.of(Ssl31SubmodulePaths.configurationXml());

    assertThat(compositions.ofTypeSet("cfg:DefinedType.ФизическоеЛицо").types)
      .containsExactly("cfg:CatalogRef._ДемоФизическиеЛица");
    MdTypeDescriptionDto department = compositions.ofDefinedTypeId(" 92FB25E9-5B34-4411-B876-CD83FA55A6E0 ");
    assertThat(department.types).containsExactly("cfg:CatalogRef._ДемоПодразделения");
    // Ненайденное записей не даёт, повтор не дублирует запись
    assertThat(compositions.ofTypeSet("cfg:DefinedType.НетТакого")).isNull();
    assertThat(compositions.ofDefinedTypeId("00000000-0000-0000-0000-000000000000")).isNull();
    assertThat(compositions.ofTypeSet("cfg:CatalogRef")).isNull();
    assertThat(compositions.ofTypeSet("cfg:Characteristic.РазделыДатЗапретаИзменения").types).hasSize(4);
    compositions.ofTypeSet("cfg:DefinedType.ФизическоеЛицо");

    assertThat(compositions.collected()).containsExactly(
      Map.of("source", "cfg:DefinedType.ФизическоеЛицо",
        "types", List.of("cfg:CatalogRef._ДемоФизическиеЛица"), "typeSets", List.of()),
      Map.of("source", "92FB25E9-5B34-4411-B876-CD83FA55A6E0", "name", "Подразделение",
        "types", List.of("cfg:CatalogRef._ДемоПодразделения"), "typeSets", List.of()),
      Map.of("source", "cfg:Characteristic.РазделыДатЗапретаИзменения",
        "types", List.of("cfg:CatalogRef._ДемоБанковскиеСчета", "cfg:EnumRef._ДемоЮридическоеФизическоеЛицо",
          "cfg:CatalogRef._ДемоМестаХранения", "cfg:ChartOfCharacteristicTypesRef.РазделыДатЗапретаИзменения"),
        "typeSets", List.of()));
    assertThat(new ArrayList<>(compositions.collected().get(1).keySet()))
      .containsExactly("source", "name", "types", "typeSets");

    // Выданный состав — копия: правка потребителя не портит кэш
    compositions.ofTypeSet("cfg:DefinedType.ФизическоеЛицо").types.clear();
    assertThat(compositions.ofTypeSet("cfg:DefinedType.ФизическоеЛицо").types).hasSize(1);
  }

  // --------------------------------------------------------------- синтетика

  @Test
  void вложенныеНаборыПодставляютсяСоставомБезПовторов() throws Exception {
    Path dump = designerDump();
    definedType(dump, "А", type("xs:string") + typeSet("cfg:DefinedType.Б") + typeSet("cfg:Characteristic.В")
      + typeSet("cfg:CatalogRef"));
    definedType(dump, "Б", type("cfg:CatalogRef.Валюты") + type("xs:string") + typeSet("cfg:DefinedType.Г"));
    definedType(dump, "Г", type("xs:decimal"));
    characteristic(dump, "В", type("cfg:CatalogRef.Валюты") + type("xs:boolean"));

    TypeCompositions compositions = TypeCompositions.of(dump.resolve("Configuration.xml"));
    MdTypeDescriptionDto a = compositions.ofTypeSet("cfg:DefinedType.А");
    // Свои типы, затем типы вложенных по порядку; набор вида раскрывать нечем — он остаётся
    assertThat(a.types).containsExactly("xs:string", "cfg:CatalogRef.Валюты", "xs:decimal", "xs:boolean");
    assertThat(a.typeSets).containsExactly("cfg:CatalogRef");
    // Вложенный набор, который сам не запрашивали, отдельной записи не даёт
    assertThat(compositions.collected()).extracting(row -> row.get("source"))
      .containsExactly("cfg:DefinedType.А");

    // Вложенный набор без описания остаётся набором
    definedType(dump, "Д", type("xs:string") + typeSet("cfg:DefinedType.НетОписания"));
    assertThat(TypeCompositions.of(dump.resolve("Configuration.xml")).ofTypeSet("cfg:DefinedType.Д").typeSets)
      .containsExactly("cfg:DefinedType.НетОписания");
  }

  @Test
  void циклНеЗацикливаетИОставляетЗамыкающийНабор() throws Exception {
    Path dump = designerDump();
    definedType(dump, "А", type("xs:string") + typeSet("cfg:DefinedType.Б"));
    definedType(dump, "Б", type("xs:decimal") + typeSet("cfg:DefinedType.А"));
    definedType(dump, "Сам", type("xs:boolean") + typeSet("cfg:DefinedType.Сам"));

    TypeCompositions compositions = TypeCompositions.of(dump.resolve("Configuration.xml"));
    MdTypeDescriptionDto a = compositions.ofTypeSet("cfg:DefinedType.А");
    assertThat(a.types).containsExactly("xs:string", "xs:decimal");
    assertThat(a.typeSets).containsExactly("cfg:DefinedType.А");
    // Результат не зависит от того, что раскрывали раньше: путь посещённых — свой у каждого запроса
    MdTypeDescriptionDto b = compositions.ofTypeSet("cfg:DefinedType.Б");
    assertThat(b.types).containsExactly("xs:decimal", "xs:string");
    assertThat(b.typeSets).containsExactly("cfg:DefinedType.Б");
    MdTypeDescriptionDto self = compositions.ofTypeSet("cfg:DefinedType.Сам");
    assertThat(self.types).containsExactly("xs:boolean");
    assertThat(self.typeSets).containsExactly("cfg:DefinedType.Сам");
  }

  @Test
  void ненайденноеИНечитаемоеБезЗаписиИОшибки() throws Exception {
    // Вне выгрузки
    Path alone = tempDir.resolve("alone").resolve("Пользователи.xml");
    Files.createDirectories(alone.getParent());
    Files.copy(Ssl31SubmodulePaths.projectRoot().resolve("src/cf/Catalogs/Пользователи.xml"), alone);
    TypeCompositions outside = TypeCompositions.of(alone);
    assertThat(outside.ofTypeSet("cfg:DefinedType.ФизическоеЛицо")).isNull();
    assertThat(outside.ofDefinedTypeId("46c59dea-b12c-4de0-a78f-8d15311acab7")).isNull();
    assertThat(outside.collected()).isEmpty();

    Path dump = designerDump();
    // Описание без типа, пустой тип, тип только у реквизита плана видов характеристик
    Path noType = dump.resolve("DefinedTypes/БезТипа.xml");
    Files.writeString(noType, definedTypeXml("БезТипа", "11111111-1111-1111-1111-111111111111", "")
      .replaceFirst("(?s)<Type>.*?</Type>", ""), StandardCharsets.UTF_8);
    definedType(dump, "Пустой", "");
    characteristic(dump, "БезСвоегоТипа", null);
    // Нечитаемое имя и имя, похожее на путь
    Files.createDirectories(dump.resolve("DefinedTypes/Каталог.xml"));

    TypeCompositions compositions = TypeCompositions.of(dump.resolve("Configuration.xml"));
    assertThat(compositions.ofTypeSet("cfg:DefinedType.НетФайла")).isNull();
    assertThat(compositions.ofTypeSet("cfg:DefinedType.БезТипа")).isNull();
    assertThat(compositions.ofTypeSet("cfg:DefinedType.Пустой")).isNull();
    assertThat(compositions.ofTypeSet("cfg:Characteristic.БезСвоегоТипа")).isNull();
    assertThat(compositions.ofTypeSet("cfg:DefinedType.Каталог")).isNull();
    assertThat(compositions.ofTypeSet("cfg:DefinedType.../Catalogs/Пользователи")).isNull();
    assertThat(compositions.ofTypeSet("cfg:DefinedType.")).isNull();
    assertThat(compositions.ofTypeSet(null)).isNull();
    assertThat(compositions.ofDefinedTypeId(null)).isNull();
    assertThat(compositions.collected()).isEmpty();
  }

  @Test
  void кэшЖивётОдинЭкземпляр() throws Exception {
    Path dump = designerDump();
    definedType(dump, "А", type("xs:string"));
    TypeCompositions first = TypeCompositions.of(dump.resolve("Configuration.xml"));
    assertThat(first.ofTypeSet("cfg:DefinedType.А").types).containsExactly("xs:string");
    assertThat(first.ofTypeSet("cfg:DefinedType.Б")).isNull();

    definedType(dump, "А", type("xs:decimal"));
    definedType(dump, "Б", type("xs:boolean"));
    // Тот же вызов операции видит прочитанное в начале, следующий — файл заново
    assertThat(first.ofTypeSet("cfg:DefinedType.А").types).containsExactly("xs:string");
    assertThat(first.ofTypeSet("cfg:DefinedType.Б")).isNull();
    TypeCompositions next = TypeCompositions.of(dump.resolve("Configuration.xml"));
    assertThat(next.ofTypeSet("cfg:DefinedType.А").types).containsExactly("xs:decimal");
    assertThat(next.ofTypeSet("cfg:DefinedType.Б").types).containsExactly("xs:boolean");
  }

  @Test
  void именаEdtБезТочкиСтановятсяТипамиВыгрузкиАЛюбаяСсылкаВСхемеEdtОстаётсяAnyRef() throws Exception {
    Path src = tempDir.resolve("edt").resolve("ssl31").resolve("src");
    Files.createDirectories(src.resolve("Configuration"));
    Files.copy(edtConfiguration(), src.resolve("Configuration/Configuration.mdo"));
    String typeId = "46c59dea-b12c-4de0-a78f-8d15311acab7";
    edtDefinedType(src, "ФизическоеЛицо", typeId,
      "ValueStorage", "BinaryData", "AnyRef", "UUID", "CatalogRef", "StandardPeriod", "CatalogRef.Валюты");
    Path template = src.resolve(EVENT_LOG_EDT);
    Files.createDirectories(template.getParent());
    Files.copy(Path.of(System.getProperty("fixtures.ssl31edt.root"), "ssl31", "src").resolve(EVENT_LOG_EDT), template);

    // Запись конфигуратора: как у типов cf-md-query-tables
    MdTypeDescriptionDto designer = TypeCompositions.of(template).ofTypeSet("cfg:DefinedType.ФизическоеЛицо");
    assertThat(designer.types).containsExactly(
      "v8:ValueStorage", "xs:base64Binary", "v8:UUID", "v8:StandardPeriod", "cfg:CatalogRef.Валюты");
    assertThat(designer.typeSets).containsExactly("cfg:AnyIBRef", "cfg:CatalogRef");

    // Запись схемы EDT: «любая ссылка» — AnyRef, хранилище значения — тип, а не набор
    DcsValueTypes.Context context = DcsValueTypes.Context.of(template);
    assertThat(context.edt()).isTrue();
    MdTypeDescriptionDto schema = context.definedTypeComposition(typeId.toUpperCase());
    assertThat(schema.types).containsExactly(
      "v8:ValueStorage", "xs:base64Binary", "v8:UUID", "v8:StandardPeriod", "cfg:CatalogRef.Валюты");
    assertThat(schema.typeSets).containsExactly("cfg:AnyRef", "cfg:CatalogRef");

    Map<String, Object> row = definedTypeRow(DcsTypeDictionary.read(template, SchemaVersion.V2_20), "ФизическоеЛицо");
    assertThat(row.get("typeId")).isEqualTo(typeId);
    assertThat(row.get("composition")).isEqualTo(Map.of(
      "types", List.of("v8:ValueStorage", "xs:base64Binary", "v8:UUID", "v8:StandardPeriod", "cfg:CatalogRef.Валюты"),
      "typeSets", List.of("cfg:AnyRef", "cfg:CatalogRef"),
      "typeIds", List.of()));

    // Вложенный определяемый тип EDT пишет с точкой — это набор, и он раскрывается, как в выгрузке
    edtDefinedType(src, "Подразделение", "92fb25e9-5b34-4411-b876-cd83fa55a6e0",
      "Boolean", "DefinedType.ФизическоеЛицо");
    MdTypeDescriptionDto nested = TypeCompositions.of(template).ofTypeSet("cfg:DefinedType.Подразделение");
    assertThat(nested.types).containsExactly(
      "xs:boolean", "v8:ValueStorage", "xs:base64Binary", "v8:UUID", "v8:StandardPeriod", "cfg:CatalogRef.Валюты");
    assertThat(nested.typeSets).containsExactly("cfg:AnyIBRef", "cfg:CatalogRef");
  }

  // --------------------------------------------------------------- помощники

  private static Path edtConfiguration() {
    Path p = Path.of(System.getProperty("fixtures.ssl31edt.root"), "ssl31", "src", "Configuration", "Configuration.mdo");
    assertThat(p).exists();
    return p;
  }

  private static List<String> names(Path directory, String extension) throws IOException {
    try (Stream<Path> files = Files.list(directory)) {
      return files.map(file -> file.getFileName().toString())
        .filter(name -> name.endsWith(extension))
        .map(name -> name.substring(0, name.length() - extension.length()))
        .sorted()
        .toList();
    }
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> definedTypeRow(Map<String, Object> dictionary, String name) {
    for (Object row : (List<Object>) dictionary.get("definedTypes")) {
      Map<String, Object> map = (Map<String, Object>) row;
      if (name.equals(map.get("name"))) {
        return map;
      }
    }
    throw new AssertionError("нет определяемого типа " + name);
  }

  private static MdTypeDescriptionDto directDesigner(Path file) throws IOException {
    Matcher body = DIRECT_DESIGNER_BODY.matcher(Files.readString(file, StandardCharsets.UTF_8));
    assertThat(body.find()).as(file.toString()).isTrue();
    MdTypeDescriptionDto out = new MdTypeDescriptionDto();
    Matcher entry = DIRECT_DESIGNER_ENTRY.matcher(body.group(1));
    while (entry.find()) {
      ("Type".equals(entry.group(1)) ? out.types : out.typeSets).add(entry.group(2));
    }
    return out;
  }

  private static MdTypeDescriptionDto directEdt(Path file) throws IOException {
    Matcher body = DIRECT_EDT_BODY.matcher(Files.readString(file, StandardCharsets.UTF_8));
    assertThat(body.find()).as(file.toString()).isTrue();
    MdTypeDescriptionDto out = new MdTypeDescriptionDto();
    Matcher entry = DIRECT_EDT_ENTRY.matcher(body.group(1));
    while (entry.find()) {
      String token = entry.group(1);
      if (DIRECT_EDT_PRIMITIVES.containsKey(token)) {
        out.types.add(DIRECT_EDT_PRIMITIVES.get(token));
      } else if (token.contains(".")) {
        out.types.add("cfg:" + token);
      } else {
        out.typeSets.add("cfg:" + token);
      }
    }
    return out;
  }

  /** Выгрузка из одного описания конфигурации: поиск конфигурации видит {@code Configuration.xml}. */
  private Path designerDump() throws IOException {
    Path cf = tempDir.resolve("cf");
    Files.createDirectories(cf.resolve("DefinedTypes"));
    Files.createDirectories(cf.resolve("ChartsOfCharacteristicTypes"));
    Files.copy(Ssl31SubmodulePaths.configurationXml(), cf.resolve("Configuration.xml"));
    return cf;
  }

  private static String type(String text) {
    return "\t\t\t\t<v8:Type>" + text + "</v8:Type>\r\n";
  }

  private static String typeSet(String text) {
    return "\t\t\t\t<v8:TypeSet>" + text + "</v8:TypeSet>\r\n";
  }

  private static void definedType(Path dump, String name, String typeBody) throws IOException {
    String typeId = java.util.UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8)).toString();
    Files.writeString(dump.resolve("DefinedTypes").resolve(name + ".xml"), definedTypeXml(name, typeId, typeBody),
      StandardCharsets.UTF_8);
  }

  private static String header() {
    return "﻿<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n"
      + "<MetaDataObject xmlns=\"http://v8.1c.ru/8.3/MDClasses\" xmlns:cfg=\"" + CURRENT_CONFIG + "\""
      + " xmlns:v8=\"http://v8.1c.ru/8.1/data/core\" xmlns:xr=\"http://v8.1c.ru/8.3/xcf/readable\""
      + " xmlns:xs=\"http://www.w3.org/2001/XMLSchema\" version=\"2.20\">\r\n";
  }

  private static String definedTypeXml(String name, String typeId, String typeBody) {
    return header()
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
      + "</MetaDataObject>";
  }

  /**
   * План видов характеристик с реквизитом своего типа: состав берётся только из свойств самого плана.
   *
   * @param typeBody состав типа плана; {@code null} — у плана тип не записан, тип есть только у реквизита
   */
  private static void characteristic(Path dump, String name, String typeBody) throws IOException {
    String own = typeBody == null ? "" : "\t\t\t<Type>\r\n" + typeBody + "\t\t\t</Type>\r\n";
    Files.writeString(dump.resolve("ChartsOfCharacteristicTypes").resolve(name + ".xml"), header()
      + "\t<ChartOfCharacteristicTypes uuid=\"" + java.util.UUID.randomUUID() + "\">\r\n"
      + "\t\t<Properties>\r\n"
      + "\t\t\t<Name>" + name + "</Name>\r\n"
      + own
      + "\t\t</Properties>\r\n"
      + "\t\t<ChildObjects>\r\n"
      + "\t\t\t<Attribute uuid=\"" + java.util.UUID.randomUUID() + "\">\r\n"
      + "\t\t\t\t<Properties>\r\n"
      + "\t\t\t\t\t<Name>Реквизит</Name>\r\n"
      + "\t\t\t\t\t<Type>\r\n\t\t\t\t\t\t<v8:Type>xs:string</v8:Type>\r\n\t\t\t\t\t</Type>\r\n"
      + "\t\t\t\t</Properties>\r\n"
      + "\t\t\t</Attribute>\r\n"
      + "\t\t</ChildObjects>\r\n"
      + "\t</ChartOfCharacteristicTypes>\r\n"
      + "</MetaDataObject>", StandardCharsets.UTF_8);
  }

  private static void edtDefinedType(Path src, String name, String typeId, String... types) throws IOException {
    StringBuilder body = new StringBuilder();
    for (String type : types) {
      body.append("    <types>").append(type).append("</types>\n");
    }
    Path file = src.resolve("DefinedTypes").resolve(name).resolve(name + ".mdo");
    Files.createDirectories(file.getParent());
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
}
