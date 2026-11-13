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
import io.github.yellowhammer.designerxml.cli.DesignerXmlCli;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import picocli.CommandLine;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Значение заполнения узла после смены его типа ({@link FillValueCanon}).
 *
 * <p>{@code cf-fill-value-platform} - выгрузка платформы (ibcmd 8.3.27, формат 2.20) документа,
 * который записал md-sparrow: три новых реквизита со сменой типа на ссылку, число и перечисление.
 * Запись должна совпасть с ней байт в байт.
 */
class MdFillValueTest {

  private static final Path PLATFORM = Path.of("src", "test", "resources", "cf-fill-value-platform");
  private static final String NIL = "<FillValue xsi:nil=\"true\"/>";
  private static final String EMPTY_STRING = "<FillValue xsi:type=\"xs:string\"/>";

  @TempDir
  Path root;

  @BeforeEach
  void forgetLanguage() {
    ConfigurationLanguage.forget();
  }

  @Test
  void newAttributesWithChangedTypeAreWrittenAsPlatformExportsThem() throws Exception {
    Path cf = root.resolve("cf");
    op(Map.of("op", "init-empty-cf", "targetCfRoot", cf.toString()));
    String conf = cf.resolve("Configuration.xml").toString();
    op(Map.of("op", "add-md-object", "configurationXml", conf, "type", "CATALOG", "name", "Валюты"));
    op(Map.of("op", "add-md-object", "configurationXml", conf, "type", "ENUM", "name", "Статусы"));
    String enumXml = cf.resolve("Enums/Статусы.xml").toString();
    op(Map.of("op", "cf-md-enum-value-add", "objectXml", enumXml, "name", "Новый"));
    op(Map.of("op", "cf-md-enum-value-add", "objectXml", enumXml, "name", "Закрыт"));
    op(Map.of("op", "add-md-object", "configurationXml", conf, "type", "DOCUMENT", "name", "ЗаказПокупателя"));
    Path document = cf.resolve("Documents/ЗаказПокупателя.xml");
    for (String name : List.of("Валюта", "Сумма", "Статус")) {
      op(Map.of("op", "cf-md-attribute-add", "objectXml", document.toString(), "name", name));
    }
    // Новый реквизит - строка с пустым значением заполнения, как его создаёт платформа
    assertThat(count(Files.readString(document, StandardCharsets.UTF_8), EMPTY_STRING)).isEqualTo(3);

    MdObjectPropertiesDto dto = MdObjectPropertiesEdit.readDto(document, SchemaVersion.V2_20);
    attribute(dto, "Валюта").type = type("cfg:CatalogRef.Валюты");
    MdTypeDescriptionDto sum = type("xs:decimal");
    sum.numberQualifiers = new MdTypeDescriptionDto.MdNumberQualifiersDto();
    sum.numberQualifiers.digits = "15";
    sum.numberQualifiers.fractionDigits = "2";
    sum.numberQualifiers.allowedSign = "NONNEGATIVE";
    attribute(dto, "Сумма").type = sum;
    attribute(dto, "Статус").type = type("cfg:EnumRef.Статусы");
    op(Map.of("op", "cf-md-object-set", "objectXml", document.toString(), "payloadJson", new Gson().toJson(dto)));

    assertThat(Files.readString(document, StandardCharsets.UTF_8))
      .isEqualTo(Files.readString(PLATFORM.resolve("Documents/ЗаказПокупателя.xml"), StandardCharsets.UTF_8));
  }

  @Test
  void incompatibleValueBecomesEmptyValueOfNewType() throws Exception {
    // Булево «Истина» при смене типа на строку - пустая строка, как у нового строкового реквизита
    Path partners = corpusCopy("Catalogs/_ДемоПартнеры.xml");
    List<String> changed = changeType(partners, "Клиент", dto -> dto.type = string("10"));
    assertThat(changed).anySatisfy(line -> assertThat(line).contains(EMPTY_STRING));
    assertThat(changed).noneSatisfy(line -> assertThat(line).contains("xs:boolean\">true"));

    // Число 0 при смене типа на булево - nil
    Path task = corpusCopy("BusinessProcesses/Задание.xml");
    changed = changeType(task, "НомерИтерации", dto -> dto.type = type("xs:boolean"));
    assertThat(changed).anySatisfy(line -> assertThat(line).contains(NIL));

    // Строка при смене типа на составной без строки - nil
    Path kinds = corpusCopy("Catalogs/ВидыКонтактнойИнформации.xml");
    changed = changeType(kinds, "ВидПоляДругое", dto -> dto.type = type("xs:decimal", "xs:dateTime"));
    assertThat(changed).anySatisfy(line -> assertThat(line).contains(NIL));
  }

  @Test
  void compatibleValueNilAndConfigurationValueStayByteForByte() throws Exception {
    // Строка «МногострочноеШирокое» при смене длины строки и при составном типе со строкой остаётся
    Path kinds = corpusCopy("Catalogs/ВидыКонтактнойИнформации.xml");
    assertThat(changeType(kinds, "ВидПоляДругое", dto -> dto.type = string("100")))
      .allSatisfy(line -> assertThat(line).doesNotContain("FillValue"));
    assertThat(changeType(kinds, "ВидПоляДругое", dto -> {
      MdTypeDescriptionDto composite = string("100");
      composite.types.add("xs:boolean");
      dto.type = composite;
    })).allSatisfy(line -> assertThat(line).doesNotContain("FillValue"));

    // nil подходит к любому типу
    Path versions = corpusCopy("Catalogs/ВерсииФайлов.xml");
    assertThat(changeType(versions, "НомерВерсии", dto -> dto.type = string("10")))
      .isNotEmpty()
      .allSatisfy(line -> assertThat(line).doesNotContain("FillValue"));

    // Значение конфигурации остаётся: платформа хранит его и при чужом типе (ssl31, ВидФлажка)
    Path task = corpusCopy("BusinessProcesses/Задание.xml");
    assertThat(changeType(task, "Важность", dto -> dto.type = string("10")))
      .isNotEmpty()
      .allSatisfy(line -> assertThat(line).doesNotContain("FillValue"));

    // Состав определяемого типа лежит у другого объекта: значение остаётся
    Path partners = corpusCopy("Catalogs/_ДемоПартнеры.xml");
    assertThat(changeType(partners, "Клиент", dto -> {
      MdTypeDescriptionDto defined = new MdTypeDescriptionDto();
      defined.typeSets.add("cfg:DefinedType.ДенежнаяСуммаНеотрицательная");
      dto.type = defined;
    })).isNotEmpty().allSatisfy(line -> assertThat(line).doesNotContain("FillValue"));
  }

  @Test
  void typeOfEveryCorpusNodeKeepsItsFillValue() throws IOException {
    // Эхо корпуса: значение заполнения каждого узла ssl31 подходит к его же типу, поэтому
    // запись типа (той же или с другими квалификаторами) значение не трогает
    Pattern node = Pattern.compile("<(\\w+) uuid=\"[^\"]*\">\\s*<Properties>([\\s\\S]*?)</Properties>");
    Pattern typeBlock = Pattern.compile("<Type>([\\s\\S]*?)</Type>");
    Pattern fill = Pattern.compile("<FillValue\\b[^>]*>");
    Map<String, Integer> actions = new TreeMap<>();
    List<String> touched = new ArrayList<>();
    try (Stream<Path> files = Files.walk(Ssl31SubmodulePaths.projectRoot().resolve("src/cf"))) {
      for (Path file : files.filter(f -> f.toString().endsWith(".xml")).toList()) {
        String xml = Files.readString(file, StandardCharsets.UTF_8);
        if (!xml.contains("<FillValue")) {
          continue;
        }
        Matcher m = node.matcher(xml);
        while (m.find()) {
          Matcher f = fill.matcher(m.group(2));
          Matcher t = typeBlock.matcher(m.group(2));
          if (!f.find() || !t.find()) {
            continue;
          }
          MdTypeDescriptionDto own = new MdTypeDescriptionDto();
          own.types.addAll(texts(t.group(1), "v8:Type"));
          own.typeSets.addAll(texts(t.group(1), "v8:TypeSet"));
          FillValueCanon.Action action =
            FillValueCanon.afterTypeChange(MdObjectPropertiesGranularPatch.fillValueType(f.group()), own);
          actions.merge(action.name(), 1, Integer::sum);
          if (action != FillValueCanon.Action.KEEP) {
            touched.add(file.getFileName() + " " + f.group());
          }
        }
      }
    }
    assertThat(touched).isEmpty();
    assertThat(actions.get("KEEP")).isGreaterThan(2900);
  }

  @Test
  void canonByValueTypeAndNewType() {
    assertThat(FillValueCanon.afterTypeChange(null, type("cfg:CatalogRef.Валюты")))
      .isEqualTo(FillValueCanon.Action.KEEP);
    assertThat(FillValueCanon.afterTypeChange("xs:string", type("cfg:CatalogRef.Валюты")))
      .isEqualTo(FillValueCanon.Action.NIL);
    assertThat(FillValueCanon.afterTypeChange("xs:string", type("xs:decimal")))
      .isEqualTo(FillValueCanon.Action.NIL);
    assertThat(FillValueCanon.afterTypeChange("xs:string", type("cfg:EnumRef.Статусы")))
      .isEqualTo(FillValueCanon.Action.NIL);
    assertThat(FillValueCanon.afterTypeChange("xs:decimal", type("xs:string")))
      .isEqualTo(FillValueCanon.Action.EMPTY_STRING);
    assertThat(FillValueCanon.afterTypeChange("xs:decimal", type("xs:string", "xs:boolean")))
      .isEqualTo(FillValueCanon.Action.NIL);
    assertThat(FillValueCanon.afterTypeChange("xs:boolean", type("xs:boolean", "xs:string")))
      .isEqualTo(FillValueCanon.Action.KEEP);
    assertThat(FillValueCanon.afterTypeChange("xr:DesignTimeRef", type("xs:string")))
      .isEqualTo(FillValueCanon.Action.KEEP);
    MdTypeDescriptionDto characteristic = new MdTypeDescriptionDto();
    characteristic.typeSets.add("cfg:Characteristic.ВидыСвойств");
    assertThat(FillValueCanon.afterTypeChange("xs:string", characteristic)).isEqualTo(FillValueCanon.Action.KEEP);
    // Пустой тип: судить не по чему
    assertThat(FillValueCanon.afterTypeChange("xs:string", new MdTypeDescriptionDto()))
      .isEqualTo(FillValueCanon.Action.KEEP);
    assertThat(MdObjectPropertiesGranularPatch.fillValueType("<FillValue xsi:nil=\"true\"/>")).isNull();
    assertThat(MdObjectPropertiesGranularPatch.fillValueType("<FillValue xsi:type=\"xs:boolean\">true</FillValue>"))
      .isEqualTo("xs:boolean");
  }

  /** Меняет тип реквизита и отдаёт изменившиеся строки файла (их число прежнее или растёт у типа). */
  private static List<String> changeType(Path object, String name, Consumer<MdNamedPropertyDto> change)
    throws Exception {
    ConfigurationLanguage.forget();
    String before = Files.readString(object, StandardCharsets.UTF_8);
    MdObjectPropertiesDto dto = MdObjectPropertiesEdit.readDto(object, SchemaVersion.V2_20);
    change.accept(attribute(dto, name));
    MdObjectPropertiesEdit.writeDto(object, SchemaVersion.V2_20, dto);
    String after = Files.readString(object, StandardCharsets.UTF_8);
    List<String> was = before.lines().toList();
    List<String> now = after.lines().toList();
    List<String> changed = new ArrayList<>();
    // Строки до узла и после него совпадают: различие - середина
    int head = 0;
    while (head < was.size() && head < now.size() && was.get(head).equals(now.get(head))) {
      head++;
    }
    int tail = 0;
    while (tail < was.size() - head && tail < now.size() - head
      && was.get(was.size() - 1 - tail).equals(now.get(now.size() - 1 - tail))) {
      tail++;
    }
    for (int i = head; i < now.size() - tail; i++) {
      changed.add(now.get(i));
    }
    return changed;
  }

  private Path corpusCopy(String relative) throws IOException {
    Path source = Ssl31SubmodulePaths.projectRoot().resolve("src/cf").resolve(relative);
    Path copy = root.resolve(relative);
    Files.createDirectories(copy.getParent());
    if (!Files.exists(copy)) {
      Files.copy(source, copy);
    }
    return copy;
  }

  private static MdNamedPropertyDto attribute(MdObjectPropertiesDto dto, String name) {
    return dto.attributes.stream().filter(a -> name.equals(a.name)).findFirst().orElseThrow();
  }

  private static MdTypeDescriptionDto type(String... types) {
    MdTypeDescriptionDto dto = new MdTypeDescriptionDto();
    dto.types.addAll(List.of(types));
    return dto;
  }

  private static MdTypeDescriptionDto string(String length) {
    MdTypeDescriptionDto dto = type("xs:string");
    dto.stringQualifiers = new MdTypeDescriptionDto.MdStringQualifiersDto();
    dto.stringQualifiers.length = length;
    dto.stringQualifiers.allowedLength = "VARIABLE";
    return dto;
  }

  private static List<String> texts(String block, String tag) {
    List<String> out = new ArrayList<>();
    Matcher m = Pattern.compile("<" + tag + ">([^<]*)</" + tag + ">").matcher(block);
    while (m.find()) {
      out.add(m.group(1));
    }
    return out;
  }

  private static int count(String text, String part) {
    int n = 0;
    for (int at = text.indexOf(part); at >= 0; at = text.indexOf(part, at + part.length())) {
      n++;
    }
    return n;
  }

  /** Операция md-sparrow каналом apply-mutation, как её зовёт roundtrip.py. */
  private void op(Map<String, String> params) throws IOException {
    JsonObject json = new JsonObject();
    params.forEach(json::addProperty);
    json.addProperty("schemaVersion", "V2_20");
    Path file = root.resolve("params.json");
    Files.writeString(file, json.toString(), StandardCharsets.UTF_8);
    int exit = new CommandLine(new DesignerXmlCli()).execute("apply-mutation", "--params", file.toString());
    assertThat(exit).as("md-sparrow %s", params.get("op")).isZero();
  }
}
