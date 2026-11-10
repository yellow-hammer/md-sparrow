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
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Значение параметра схемы: {@code valueTyped} в {@code cf-dcs-info} (форма значений настроек) и
 * его эхо в {@code cf-dcs-set-parameter}, совместимость пустой строки с любым типом и имена
 * параметров без учёта регистра.
 *
 * <p>Главные проверки — по корпусу: эхо {@code valueTyped} каждого параметра ssl31 и EDT-пары не
 * меняет ни байта (а писатель значений даёт те же байты, что в файле), круг «сменить тип → вернуть»
 * у каждого типизированного параметра ssl31 возвращает файлы байт в байт.
 */
class DcsParameterValueTest {

  private static final String FILES =
    "src/cf/Reports/_ДемоФайлы/Templates/ОсновнаяСхемаКомпоновкиДанных/Ext/Template.xml";

  private static final String EVENT_LOG =
    "src/cf/Reports/АнализЖурналаРегистрации/Templates/ОсновнаяСхемаКомпоновкиДанных/Ext/Template.xml";

  private static final String HANDLERS =
    "src/cf/CommonTemplates/ПолучениеДереваОбработчиковОбновления/Ext/Template.xml";

  /** Параметр с типом {@code v8:UUID} и значением — пустой строкой («значение не задано»). */
  private static final String USERS_INFO =
    "src/cf/Reports/СведенияОПользователях/Templates/ОсновнаяСхемаКомпоновкиДанных/Ext/Template.xml";

  private static final String MISSING_USER_IDS = "ИдентификаторыНесуществующихПользователейИБ";

  private static final Gson GSON = new Gson();

  @TempDir
  Path tempDir;

  // --------------------------------------------------------------- valueTyped

  @Test
  void valueTypedОтдаётЗначениеФормойЗначенийНастроек() throws Exception {
    Path log = copy(EVENT_LOG);
    List<Map<String, Object>> parameters = rows(DcsRead.info(log, SchemaVersion.V2_20), "parameters");
    assertThat(byKey(parameters, "name", "Период").get("valueTyped")).isEqualTo(Map.of(
      "type", "v8:StandardPeriod",
      "period", Map.of("variant", "Custom", "startDate", "0001-01-01T00:00:00", "endDate", "0001-01-01T00:00:00")));
    Map<String, Object> background = byKey(parameters, "name", "ОтображатьФоновыеЗадания");
    assertThat(background.get("valueTyped")).isEqualTo(Map.of("type", "xs:boolean", "text", "false", "decoded", false));
    // Лексический текст — рядом, в value и valuePresentation
    assertThat(background).containsEntry("value", "false").containsEntry("valuePresentation", "false");
    assertThat(byKey(parameters, "name", "МинимальнаяПродолжительностьСеансовРегламентныхЗаданий").get("valueTyped"))
      .isEqualTo(Map.of("type", "xs:decimal", "text", "1", "decoded", new BigDecimal("1")));
    assertThat(byKey(parameters, "name", "ПериодДень").get("valueTyped"))
      .isEqualTo(Map.of("type", "xs:dateTime", "text", "0001-01-01T00:00:00"));
    // Ссылка конфигуратора: тип дословно из файла; текстового value у неё нет
    Map<String, Object> user = byKey(parameters, "name", "Пользователь");
    assertThat(user.get("valueTyped"))
      .isEqualTo(Map.of("type", "dcscor:DesignTimeValue", "text", "Справочник.Пользователи.ПустаяСсылка"));
    assertThat(user).doesNotContainKeys("value", "valuePresentation");
    // Пустая строка — {type} без text; значения нет — нет и ключа
    assertThat(byKey(parameters, "name", "ВариантОтчета").get("valueTyped")).isEqualTo(Map.of("type", "xs:string"));
    assertThat(byKey(parameters, "name", "ПользователиИГруппы")).doesNotContainKey("valueTyped");

    // xsi:nil — {nil: true}
    Path files = copy(FILES);
    Map<String, Object> folder = byKey(rows(DcsRead.info(files, SchemaVersion.V2_20), "parameters"), "name", "Папка");
    assertThat(folder.get("valueTyped")).isEqualTo(Map.of("nil", true));
    assertThat(folder).doesNotContainKey("value");

    // v8:UUID у параметра схемы пишется, поэтому не opaque (в настройках такого типа нет)
    Path handlers = copy(HANDLERS);
    Map<String, Object> uuid = rows(DcsRead.info(handlers, SchemaVersion.V2_20), "parameters").stream()
      .filter(row -> row.get("valueTyped") instanceof Map<?, ?> value && "v8:UUID".equals(value.get("type")))
      .findFirst().orElseThrow();
    assertThat(uuid.get("valueTyped"))
      .isEqualTo(Map.of("type", "v8:UUID", "text", "00000000-0000-0000-0000-000000000000"));
    // {nil: true} с типом UUID — обычный xsi:nil, а не отказ писателя UUID
    DcsEdit.setParameter(handlers, SchemaVersion.V2_20, (String) uuid.get("name"),
      Map.of("value", Map.of("type", "v8:UUID", "nil", true)));
    assertThat(byKey(rows(DcsRead.info(handlers, SchemaVersion.V2_20), "parameters"), "name", (String) uuid.get("name"))
      .get("valueTyped")).isEqualTo(Map.of("nil", true));
    String nil = Files.readString(handlers, StandardCharsets.UTF_8);
    XmlLines.Node element = valueElement(nil, (String) uuid.get("name"));
    assertThat(nil.substring(element.start(), element.end())).isEqualTo("<value xsi:nil=\"true\"/>");
  }

  @Test
  void эхоValueTypedНаВсехПараметрахSsl31ИEdtНеМеняетНиБайта() throws Exception {
    // 201 значение выгрузки и 198 EDT-пары; типов вне белого списка записи (opaque) в корпусах нет
    int[] designer = echoCorpus(schemaFiles(Ssl31SubmodulePaths.projectRoot().resolve("src")), "Template.xml");
    int[] edt = echoCorpus(schemaFiles(Path.of(System.getProperty("fixtures.ssl31edt.root"))), "Template.dcs");
    assertThat(List.of(designer[0], designer[1], edt[0], edt[1])).containsExactly(201, 0, 198, 0);
  }

  /**
   * Эхо {@code valueTyped} каждого параметра через JSON канала; писатель значений для того же DTO
   * обязан дать байты файла, а файл после всех эхо — не измениться.
   *
   * @return {отправлено эхом, opaque — не отправлялось}
   */
  private int[] echoCorpus(List<Path> schemas, String fileName) throws Exception {
    int echoed = 0;
    int opaque = 0;
    int index = 0;
    for (Path schema : schemas) {
      Path copy = Files.createTempDirectory(tempDir, "echo" + index++).resolve(fileName);
      Files.copy(schema, copy);
      byte[] before = Files.readAllBytes(copy);
      String text = Files.readString(copy, StandardCharsets.UTF_8);
      for (Map<String, Object> parameter : rows(DcsRead.info(copy, SchemaVersion.V2_20), "parameters")) {
        Object typed = parameter.get("valueTyped");
        if (typed == null) {
          continue;
        }
        if (GSON.toJson(typed).contains("\"opaque\":true")) {
          opaque++;
          continue;
        }
        Object value = channelObject(typed);
        XmlLines.Node element = valueElement(text, (String) parameter.get("name"));
        String written = XmlGranularPatch.formatReplacementPreservingIndent(
          text, element.start(), DcsValueTypeEdit.parameterValueXml(value));
        assertThat(written).as(schema + ": " + parameter.get("name"))
          .isEqualTo(text.substring(element.start(), element.end()));
        DcsEdit.setParameter(copy, SchemaVersion.V2_20, (String) parameter.get("name"), mapOf("value", value));
        echoed++;
      }
      assertThat(Files.readAllBytes(copy)).as(schema.toString()).isEqualTo(before);
    }
    return new int[] {echoed, opaque};
  }

  /**
   * Список значений параметра схемы платформа хранит повторяющимися {@code value} без представлений:
   * записанный элемент {@code v8:ValueListType} загрузка разворачивает и теряет представление элемента.
   * Запись и чтение следуют этой форме, а {@code valueTyped} отдаёт список формой {@code v8:ValueListType}.
   */
  @Test
  void списокЗначенийПишетсяПовторяющимисяValueБезПредставлений() throws Exception {
    Path dcs = copy(FILES);
    String original = Files.readString(dcs, StandardCharsets.UTF_8);
    Map<String, Object> list = valueList(Map.of("type", "xs:decimal", "text", "5"),
      Map.of("type", "xs:decimal", "text", "10"));

    // Список без valueListAllowed и представление элемента — отказ, файл прежний
    assertThatThrownBy(() -> DcsEdit.setParameter(dcs, SchemaVersion.V2_20, "Размер", Map.of("value", list)))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("только при valueListAllowed=true");
    Map<String, Object> presented = Map.of("type", "v8:ValueListType", "list", Map.of("items", List.of(
      Map.of("value", Map.of("type", "xs:decimal", "text", "5")),
      Map.of("value", Map.of("type", "xs:decimal", "text", "10"), "presentation", "десять"))));
    assertThatThrownBy(() -> DcsEdit.setParameter(dcs, SchemaVersion.V2_20, "Размер",
      Map.of("valueListAllowed", true, "value", presented)))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("платформа не хранит представления значений параметра схемы");
    assertThat(applyMutation(Map.of("op", "cf-dcs-set-parameter", "objectXml", dcs.toString(),
      "schemaVersion", "V2_20", "name", "Размер",
      "payloadJson", GSON.toJson(Map.of("valueListAllowed", true, "value", presented))))).isEqualTo(2);
    assertThatThrownBy(() -> DcsEdit.addParameter(dcs, SchemaVersion.V2_20, Map.of("name", "Новый", "value", list)))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("у созданного параметра");
    assertThat(Files.readString(dcs, StandardCharsets.UTF_8)).isEqualTo(original);

    // Как в выгрузке платформы: по value на элемент подряд, перед useRestriction, без v8:ValueListType
    DcsEdit.setParameter(dcs, SchemaVersion.V2_20, "Размер", Map.of("valueListAllowed", true, "value", list));
    String listed = Files.readString(dcs, StandardCharsets.UTF_8);
    assertThat(listed).isEqualTo(inSize(inSize(original,
      "\t\t<value xsi:type=\"xs:decimal\">0</value>\r\n",
      "\t\t<value xsi:type=\"xs:decimal\">5</value>\r\n\t\t<value xsi:type=\"xs:decimal\">10</value>\r\n"),
      SIZE_TAIL, SIZE_LIST_TAIL));
    assertThat(DcsXsdValidation.validate(dcs).get("ok")).isEqualTo(true);
    // Чтение отдаёт список формой v8:ValueListType
    Map<String, Object> read = byKey(rows(DcsRead.info(dcs, SchemaVersion.V2_20), "parameters"), "name", "Размер");
    assertThat(read.get("valueTyped")).isEqualTo(valueList(
      Map.of("type", "xs:decimal", "text", "5", "decoded", new BigDecimal("5")),
      Map.of("type", "xs:decimal", "text", "10", "decoded", new BigDecimal("10"))));
    // Эхо прочитанного списка — ни байта
    DcsEdit.setParameter(dcs, SchemaVersion.V2_20, "Размер", channel(Map.of("value", read.get("valueTyped"))));
    assertThat(Files.readString(dcs, StandardCharsets.UTF_8)).isEqualTo(listed);

    // Эхо значения вместе со сменой типа: значение не меняется и сверяется с новым типом как записанное
    assertThatThrownBy(() -> DcsEdit.setParameter(dcs, SchemaVersion.V2_20, "Размер", channel(Map.of(
      "value", read.get("valueTyped"), "valueType", Map.of("types", List.of("xs:boolean"))))))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("не подходит к новому типу");
    // Элемент списка чужого типа и пустая строка у числа — не подходят (пустая строка — «не задано»
    // только у самого значения, не у элемента списка)
    assertThatThrownBy(() -> DcsEdit.setParameter(dcs, SchemaVersion.V2_20, "Размер", Map.of("value",
      valueList(Map.of("type", "xs:decimal", "text", "5"), Map.of("type", "xs:boolean", "text", "true")))))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("не подходит к его типу");
    assertThatThrownBy(() -> DcsEdit.setParameter(dcs, SchemaVersion.V2_20, "Размер", Map.of("value",
      valueList(Map.of("type", "xs:string")))))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("не подходит к его типу");
    assertThat(Files.readString(dcs, StandardCharsets.UTF_8)).isEqualTo(listed);

    // Элемент «не задано» рядом с другими загрузка платформы отбрасывает ([nil, "A"] остаётся как ["A"]) —
    // отказ без записи, в том числе кодом 2 канала и у nil с типом v8:UUID
    Map<String, Object> mixed = valueList(Map.of("type", "xs:decimal", "text", "7"), Map.of("nil", true),
      Map.of("type", "xs:decimal", "text", "9"));
    assertThatThrownBy(() -> DcsEdit.setParameter(dcs, SchemaVersion.V2_20, "Размер", Map.of("value", mixed)))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("рядом с другими элементами списка")
      .hasMessageContaining("оставьте единственным элементом");
    assertThat(applyMutation(Map.of("op", "cf-dcs-set-parameter", "objectXml", dcs.toString(),
      "schemaVersion", "V2_20", "name", "Размер", "payloadJson", GSON.toJson(Map.of("value", mixed))))).isEqualTo(2);
    assertThatThrownBy(() -> DcsEdit.setParameter(dcs, SchemaVersion.V2_20, "Размер", Map.of("value",
      valueList(Map.of("type", "v8:UUID", "nil", true), Map.of("type", "xs:decimal", "text", "7")))))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("рядом с другими элементами списка");
    assertThat(Files.readString(dcs, StandardCharsets.UTF_8)).isEqualTo(listed);

    // Правка списка заменяет зону всех value
    DcsEdit.setParameter(dcs, SchemaVersion.V2_20, "Размер", Map.of("value",
      valueList(Map.of("type", "xs:decimal", "text", "7"), Map.of("type", "xs:decimal", "text", "9"))));
    assertThat(Files.readString(dcs, StandardCharsets.UTF_8)).isEqualTo(inSize(listed,
      "\t\t<value xsi:type=\"xs:decimal\">5</value>\r\n\t\t<value xsi:type=\"xs:decimal\">10</value>\r\n",
      "\t\t<value xsi:type=\"xs:decimal\">7</value>\r\n\t\t<value xsi:type=\"xs:decimal\">9</value>\r\n"));
    // Единственный элемент «не задано» платформа хранит — один xsi:nil; читается списком
    // [nil] (а не пустым списком), эхо — ни байта
    DcsEdit.setParameter(dcs, SchemaVersion.V2_20, "Размер", Map.of("value", valueList(Map.of("nil", true))));
    String nilOnly = inSize(inSize(original, "<value xsi:type=\"xs:decimal\">0</value>", "<value xsi:nil=\"true\"/>"),
      SIZE_TAIL, SIZE_LIST_TAIL);
    assertThat(Files.readString(dcs, StandardCharsets.UTF_8)).isEqualTo(nilOnly);
    assertThat(DcsXsdValidation.validate(dcs).get("ok")).isEqualTo(true);
    Map<String, Object> nilRead = byKey(rows(DcsRead.info(dcs, SchemaVersion.V2_20), "parameters"), "name", "Размер");
    assertThat(nilRead.get("valueTyped")).isEqualTo(valueList(Map.of("nil", true)));
    DcsEdit.setParameter(dcs, SchemaVersion.V2_20, "Размер", channel(Map.of("value", nilRead.get("valueTyped"))));
    assertThat(Files.readString(dcs, StandardCharsets.UTF_8)).isEqualTo(nilOnly);
    // Список из одного элемента — одно value; читается списком, пока взведён valueListAllowed
    DcsEdit.setParameter(dcs, SchemaVersion.V2_20, "Размер", Map.of("value",
      valueList(Map.of("type", "xs:decimal", "text", "0"))));
    assertThat(Files.readString(dcs, StandardCharsets.UTF_8)).isEqualTo(inSize(original, SIZE_TAIL, SIZE_LIST_TAIL));
    assertThat(byKey(rows(DcsRead.info(dcs, SchemaVersion.V2_20), "parameters"), "name", "Размер").get("valueTyped"))
      .isEqualTo(valueList(Map.of("type", "xs:decimal", "text", "0", "decoded", new BigDecimal("0"))));
    // Пустой список и null — ни одного value; выключение списка возвращает xsi:nil
    DcsEdit.setParameter(dcs, SchemaVersion.V2_20, "Размер", Map.of("value", valueList()));
    assertThat(parameterChildNames(Files.readString(dcs, StandardCharsets.UTF_8), "Размер"))
      .containsExactly("name", "title", "valueType", "useRestriction", "availableValue", "availableValue",
        "availableValue", "valueListAllowed");
    DcsEdit.setParameter(dcs, SchemaVersion.V2_20, "Размер", Map.of("valueListAllowed", false));
    assertThat(Files.readString(dcs, StandardCharsets.UTF_8)).isEqualTo(inSize(original,
      "<value xsi:type=\"xs:decimal\">0</value>", "<value xsi:nil=\"true\"/>"));
  }

  @Test
  void повторяющиесяValueВыгрузкиЧитаютсяСпискомИПравятсяЦеликом() throws Exception {
    // Так выгрузка платформы хранит список: чтение и правка берут все value, а не только первое
    Path dcs = copy(FILES);
    String text = Files.readString(dcs, StandardCharsets.UTF_8);
    String platform = inSize(inSize(text, "\t\t<value xsi:type=\"xs:decimal\">0</value>\r\n",
        "\t\t<value xsi:type=\"xs:decimal\">1</value>\r\n\t\t<value xsi:type=\"xs:decimal\">2</value>\r\n"),
      SIZE_TAIL, SIZE_LIST_TAIL);
    assertThat(platform).isNotEqualTo(text);
    Files.writeString(dcs, platform, StandardCharsets.UTF_8);
    Map<String, Object> size = byKey(rows(DcsRead.info(dcs, SchemaVersion.V2_20), "parameters"), "name", "Размер");
    assertThat(size.get("valueTyped")).isEqualTo(valueList(
      Map.of("type", "xs:decimal", "text", "1", "decoded", new BigDecimal("1")),
      Map.of("type", "xs:decimal", "text", "2", "decoded", new BigDecimal("2"))));
    // Ключ value — лексический текст всех значений
    assertThat(size.get("value")).isEqualTo("1, 2");
    // Правка соседнего свойства и эхо значения файл не трогают, кроме самой правки
    DcsEdit.setParameter(dcs, SchemaVersion.V2_20, "Размер", channel(Map.of("value", size.get("valueTyped"))));
    assertThat(Files.readString(dcs, StandardCharsets.UTF_8)).isEqualTo(platform);
    // Выключить список, оставив несколько значений, нельзя — отказ, файл прежний
    assertThatThrownBy(() -> DcsEdit.setParameter(dcs, SchemaVersion.V2_20, "Размер",
      Map.of("valueListAllowed", false)))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("список из 2 значений");
    assertThat(Files.readString(dcs, StandardCharsets.UTF_8)).isEqualTo(platform);
    // null у списка снимает все value, а не только первое
    Map<String, Object> clear = new HashMap<>();
    clear.put("value", null);
    DcsEdit.setParameter(dcs, SchemaVersion.V2_20, "Размер", clear);
    String cleared = Files.readString(dcs, StandardCharsets.UTF_8);
    assertThat(cleared).doesNotContain("<value xsi:type=\"xs:decimal\">1</value>")
      .doesNotContain("<value xsi:type=\"xs:decimal\">2</value>");
    assertThat(byKey(rows(DcsRead.info(dcs, SchemaVersion.V2_20), "parameters"), "name", "Размер"))
      .doesNotContainKey("valueTyped");
    // Выключение списка вместе с одним значением — замена всей зоны одним value
    Files.writeString(dcs, platform, StandardCharsets.UTF_8);
    DcsEdit.setParameter(dcs, SchemaVersion.V2_20, "Размер", Map.of("valueListAllowed", false,
      "value", Map.of("type", "xs:decimal", "text", "0")));
    assertThat(Files.readString(dcs, StandardCharsets.UTF_8)).isEqualTo(text);
  }

  @Test
  void nilСредиЭлементовСпискаРучнойЗаписиЧитаетсяАЭхоНеТрогаетФайл() throws Exception {
    // Список [nil, 5] запись не пишет (загрузка платформы отбрасывает nil), но такой файл бывает от ручной
    // правки: он читается как есть, эхо и правка соседнего свойства его не трогают
    Path dcs = copy(FILES);
    String text = Files.readString(dcs, StandardCharsets.UTF_8);
    String mixed = inSize(inSize(text, "\t\t<value xsi:type=\"xs:decimal\">0</value>\r\n",
        "\t\t<value xsi:nil=\"true\"/>\r\n\t\t<value xsi:type=\"xs:decimal\">5</value>\r\n"),
      SIZE_TAIL, SIZE_LIST_TAIL);
    Files.writeString(dcs, mixed, StandardCharsets.UTF_8);
    Map<String, Object> size = byKey(rows(DcsRead.info(dcs, SchemaVersion.V2_20), "parameters"), "name", "Размер");
    assertThat(size.get("valueTyped")).isEqualTo(valueList(Map.of("nil", true),
      Map.of("type", "xs:decimal", "text", "5", "decoded", new BigDecimal("5"))));
    assertThat(applyMutation(Map.of("op", "cf-dcs-set-parameter", "objectXml", dcs.toString(),
      "schemaVersion", "V2_20", "name", "Размер",
      "payloadJson", GSON.toJson(Map.of("value", size.get("valueTyped")))))).isZero();
    assertThat(Files.readString(dcs, StandardCharsets.UTF_8)).isEqualTo(mixed);
    DcsEdit.setParameter(dcs, SchemaVersion.V2_20, "Размер", Map.of("denyIncompleteValues", true));
    String denied = Files.readString(dcs, StandardCharsets.UTF_8);
    assertThat(denied).isEqualTo(inSize(mixed, SIZE_LIST_TAIL,
      "\t\t</availableValue>\r\n\t\t<valueListAllowed>true</valueListAllowed>\r\n"
        + "\t\t<denyIncompleteValues>true</denyIncompleteValues>\r\n\t</parameter>"));
    // Тот же список в другом порядке — уже правка: отказ, файл прежний
    assertThatThrownBy(() -> DcsEdit.setParameter(dcs, SchemaVersion.V2_20, "Размер", Map.of("value",
      valueList(Map.of("type", "xs:decimal", "text", "5"), Map.of("nil", true)))))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("рядом с другими элементами списка");
    assertThat(Files.readString(dcs, StandardCharsets.UTF_8)).isEqualTo(denied);
  }

  @Test
  void списокЭлементомValueListTypeЧитаетсяАЭхоНеТрогаетФайл() throws Exception {
    // Список одним элементом v8:ValueListType: представления и служебные valueType/lastId читаются,
    // эхо сверяется по смыслу и файл не трогает
    Path dcs = copy(FILES);
    String text = Files.readString(dcs, StandardCharsets.UTF_8);
    String legacy = inSize(text, "\t\t<value xsi:type=\"xs:decimal\">0</value>\r\n",
      "\t\t<value xsi:type=\"v8:ValueListType\">\r\n"
        + "\t\t\t<v8:valueType/>\r\n"
        + "\t\t\t<v8:lastId xsi:type=\"xs:decimal\">1</v8:lastId>\r\n"
        + "\t\t\t<v8:item>\r\n"
        + "\t\t\t\t<v8:value xsi:type=\"xs:decimal\">10</v8:value>\r\n"
        + "\t\t\t\t<v8:presentation>десять</v8:presentation>\r\n"
        + "\t\t\t</v8:item>\r\n"
        + "\t\t</value>\r\n");
    Files.writeString(dcs, legacy, StandardCharsets.UTF_8);
    Map<String, Object> size = byKey(rows(DcsRead.info(dcs, SchemaVersion.V2_20), "parameters"), "name", "Размер");
    assertThat(size.get("valueTyped")).isEqualTo(Map.of("type", "v8:ValueListType", "list", Map.of("items", List.of(
      Map.of("value", Map.of("type", "xs:decimal", "text", "10", "decoded", new BigDecimal("10")),
        "presentation", "десять")))));
    DcsEdit.setParameter(dcs, SchemaVersion.V2_20, "Размер", channel(Map.of("value", size.get("valueTyped"))));
    assertThat(Files.readString(dcs, StandardCharsets.UTF_8)).isEqualTo(legacy);
    // Правка соседнего свойства переносит его байты; смена типа сверяет элементы списка
    DcsEdit.setParameter(dcs, SchemaVersion.V2_20, "Размер", Map.of("denyIncompleteValues", true));
    assertThat(Files.readString(dcs, StandardCharsets.UTF_8)).contains("<v8:presentation>десять</v8:presentation>");
    assertThatThrownBy(() -> DcsEdit.setParameter(dcs, SchemaVersion.V2_20, "Размер",
      Map.of("valueType", Map.of("types", List.of("xs:boolean")))))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("не подходит к новому типу");
  }

  /** Конец параметра «Размер» в ssl31: последнее доступное значение. */
  private static final String SIZE_TAIL = "\t\t</availableValue>\r\n\t</parameter>";

  /** Тот же конец со взведённым valueListAllowed: место признака по xs:sequence. */
  private static final String SIZE_LIST_TAIL =
    "\t\t</availableValue>\r\n\t\t<valueListAllowed>true</valueListAllowed>\r\n\t</parameter>";

  /** Замена первого вхождения внутри параметра «Размер» (такие же строки есть у других параметров). */
  private static String inSize(String text, String from, String to) {
    for (XmlLines.Node node : XmlLines.children(text, List.of("DataCompositionSchema"))) {
      if ("parameter".equals(node.name()) && "Размер".equals(node.leaves().get("name"))) {
        int at = text.indexOf(from, node.start());
        assertThat(at).as(from).isNotNegative();
        assertThat(at + from.length()).as(from).isLessThanOrEqualTo(node.end());
        return text.substring(0, at) + to + text.substring(at + from.length());
      }
    }
    throw new AssertionError("параметр Размер не найден");
  }

  /** Список значений формой valueTyped из значений элементов. */
  @SafeVarargs
  private static Map<String, Object> valueList(Map<String, Object>... values) {
    return Map.of("type", "v8:ValueListType", "list", Map.of("items",
      Stream.of(values).map(value -> Map.<String, Object>of("value", value)).toList()));
  }

  /** Имена прямых подэлементов параметра корня в порядке файла. */
  private static List<String> parameterChildNames(String text, String name) {
    for (XmlLines.Node node : XmlLines.children(text, List.of("DataCompositionSchema"))) {
      if ("parameter".equals(node.name()) && name.equals(node.leaves().get("name"))) {
        return XmlLines.childrenAt(text, node.start()).stream().map(XmlLines.Node::name).toList();
      }
    }
    throw new AssertionError("параметр не найден: " + name);
  }

  @Test
  void значениеТолькоДляЧтенияЭхомНеПринимается() throws Exception {
    Path dcs = copy(FILES);
    // Тип вне белого списка записи: так его записал бы конфигуратор
    String text = Files.readString(dcs, StandardCharsets.UTF_8);
    String sizeValue = "<value xsi:type=\"xs:decimal\">0</value>";
    int at = text.indexOf(sizeValue, text.indexOf("<name>Размер</name>"));
    String original = text.substring(0, at) + "<value xsi:type=\"xs:base64Binary\">AAAA</value>"
      + text.substring(at + sizeValue.length());
    Files.writeString(dcs, original, StandardCharsets.UTF_8);
    Map<String, Object> size = byKey(rows(DcsRead.info(dcs, SchemaVersion.V2_20), "parameters"), "name", "Размер");
    assertThat(size.get("valueTyped")).isEqualTo(Map.of("type", "xs:base64Binary", "text", "AAAA", "opaque", true));

    // Как у значений настроек: эхо только для чтения — отказ писателя, файл не тронут
    assertThatThrownBy(() -> DcsEdit.setParameter(dcs, SchemaVersion.V2_20, "Размер",
      channel(Map.of("value", size.get("valueTyped")))))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("только для чтения");
    assertThat(Files.readString(dcs, StandardCharsets.UTF_8)).isEqualTo(original);
    // Правка соседних свойств байты такого значения переносит
    DcsEdit.setParameter(dcs, SchemaVersion.V2_20, "Размер", Map.of("denyIncompleteValues", true));
    assertThat(Files.readString(dcs, StandardCharsets.UTF_8))
      .contains("<value xsi:type=\"xs:base64Binary\">AAAA</value>").contains("<denyIncompleteValues>true");
  }

  // --------------------------------------------------------------- пустая строка подходит к любому типу

  @Test
  void пустаяСтрокаПодходитКЛюбомуТипуАКругСменыТипаВозвращаетФайл() throws Exception {
    Path dcs = inDump(USERS_INFO);
    String original = Files.readString(dcs, StandardCharsets.UTF_8);
    Map<String, Object> parameter = byKey(rows(DcsRead.info(dcs, SchemaVersion.V2_20), "parameters"),
      "name", MISSING_USER_IDS);
    assertThat(parameter.get("valueTyped")).isEqualTo(Map.of("type", "xs:string"));
    Map<String, Object> uuid = channel(Map.of("valueType", parameter.get("valueType")));

    // Смена типа без value: пустая строка — «значение не задано», подходит к булеву
    DcsEdit.setParameter(dcs, SchemaVersion.V2_20, MISSING_USER_IDS,
      Map.of("valueType", Map.of("types", List.of("xs:boolean"))));
    String flipped = Files.readString(dcs, StandardCharsets.UTF_8);
    assertThat(flipped).contains("\t\t\t<v8:Type>xs:boolean</v8:Type>\r\n\t\t</valueType>\r\n"
      + "\t\t<value xsi:type=\"xs:string\"/>\r\n");
    assertThat(DcsXsdValidation.validate(dcs).get("ok")).isEqualTo(true);
    // Возврат прежнего типа: пустая строка подходит и к UUID, файл байт в байт
    DcsEdit.setParameter(dcs, SchemaVersion.V2_20, MISSING_USER_IDS, uuid);
    assertThat(Files.readString(dcs, StandardCharsets.UTF_8)).isEqualTo(original);
    // Снять тип и вернуть — тоже байт в байт
    Map<String, Object> untyped = new HashMap<>();
    untyped.put("valueType", null);
    DcsEdit.setParameter(dcs, SchemaVersion.V2_20, MISSING_USER_IDS, untyped);
    DcsEdit.setParameter(dcs, SchemaVersion.V2_20, MISSING_USER_IDS, uuid);
    assertThat(Files.readString(dcs, StandardCharsets.UTF_8)).isEqualTo(original);

    // Контроль: непустая строка к UUID не подходит
    DcsEdit.setParameter(dcs, SchemaVersion.V2_20, MISSING_USER_IDS, Map.of(
      "valueType", Map.of("types", List.of("xs:string"),
        "stringQualifiers", Map.of("length", "0", "allowedLength", "VARIABLE")),
      "value", Map.of("type", "xs:string", "text", "abc")));
    String text = Files.readString(dcs, StandardCharsets.UTF_8);
    assertThatThrownBy(() -> DcsEdit.setParameter(dcs, SchemaVersion.V2_20, MISSING_USER_IDS, uuid))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("не подходит к новому типу");
    assertThat(Files.readString(dcs, StandardCharsets.UTF_8)).isEqualTo(text);
  }

  @Test
  void кругСменыТипаУВсехТипизированныхПараметровSsl31БайтВБайт() throws Exception {
    Path cf = corpusDump();
    int removed = 0;
    int flipped = 0;
    int emptyStrings = 0;
    for (Path schema : schemaFiles(cf)) {
      byte[] before = Files.readAllBytes(schema);
      for (Map<String, Object> parameter : rows(DcsRead.info(schema, SchemaVersion.V2_20), "parameters")) {
        if (!(parameter.get("valueType") instanceof Map<?, ?> valueType)) {
          continue;
        }
        assertThat(valueType.get("editable")).as(schema + ": " + parameter.get("name")).isEqualTo(true);
        String name = (String) parameter.get("name");
        Map<String, Object> restore = channel(Map.of("valueType", valueType));
        // Снять тип (параметр без типа принимает любое значение) и вернуть прежний
        Map<String, Object> untyped = new HashMap<>();
        untyped.put("valueType", null);
        DcsEdit.setParameter(schema, SchemaVersion.V2_20, name, untyped);
        DcsEdit.setParameter(schema, SchemaVersion.V2_20, name, restore);
        removed++;
        // Сменить на другой тип словаря можно без value, только если значения нет или оно не задано
        Object typed = parameter.get("valueTyped");
        boolean emptyString = Map.of("type", "xs:string").equals(typed);
        if (typed == null || Map.of("nil", true).equals(typed) || emptyString) {
          DcsEdit.setParameter(schema, SchemaVersion.V2_20, name, Map.of("valueType", flip(valueType)));
          DcsEdit.setParameter(schema, SchemaVersion.V2_20, name, restore);
          flipped++;
          emptyStrings += emptyString ? 1 : 0;
        }
      }
      assertThat(Files.readAllBytes(schema)).as(cf.relativize(schema).toString()).isEqualTo(before);
    }
    // 226 типизированных параметров; 95 из них без значения, с xsi:nil или пустой строкой, и у 27
    // пустых строк смена на другой тип без value упиралась бы в отказ без правила пустой строки
    assertThat(List.of(removed, flipped, emptyStrings)).containsExactly(226, 95, 27);
  }

  /** Другой тип словаря: булево меняется на строку, всё прочее — на булево. */
  private static Map<String, Object> flip(Map<?, ?> valueType) {
    return List.of("xs:boolean").equals(valueType.get("types")) && list(valueType.get("typeSets")).isEmpty()
      ? Map.of("types", List.of("xs:string"), "stringQualifiers", Map.of("length", "7", "allowedLength", "FIXED"))
      : Map.of("types", List.of("xs:boolean"));
  }

  // --------------------------------------------------------------- определяемый тип

  @Test
  void значениеОпределяемогоТипаСверяетсяСЕгоСоставомИзВыгрузки() throws Exception {
    Path dcs = inDump(FILES);
    Map<String, Object> money = Map.of("definedTypes", List.of("ДенежнаяСуммаЛюбогоЗнака"));
    // Так пишет платформа: определяемый тип числом и значение из его состава
    DcsEdit.addParameter(dcs, SchemaVersion.V2_20, Map.of("name", "Сумма", "valueType", money,
      "value", Map.of("type", "xs:decimal", "text", "0")));
    DcsEdit.setParameter(dcs, SchemaVersion.V2_20, "Сумма", Map.of("value", Map.of("type", "xs:decimal", "text", "100")));
    String written = Files.readString(dcs, StandardCharsets.UTF_8);
    assertThat(written).contains("<value xsi:type=\"xs:decimal\">100</value>");
    assertThat(DcsXsdValidation.validate(dcs).get("ok")).isEqualTo(true);
    // Значение вне состава — отказ, файл не тронут
    assertThatThrownBy(() -> DcsEdit.setParameter(dcs, SchemaVersion.V2_20, "Сумма",
      Map.of("value", Map.of("type", "xs:boolean", "text", "true"))))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("не подходит к его типу");
    assertThat(Files.readString(dcs, StandardCharsets.UTF_8)).isEqualTo(written);

    // Круг «снять тип → вернуть» байт в байт
    Map<String, Object> restore = channel(Map.of("valueType",
      byKey(rows(DcsRead.info(dcs, SchemaVersion.V2_20), "parameters"), "name", "Сумма").get("valueType")));
    Map<String, Object> untyped = new HashMap<>();
    untyped.put("valueType", null);
    DcsEdit.setParameter(dcs, SchemaVersion.V2_20, "Сумма", untyped);
    DcsEdit.setParameter(dcs, SchemaVersion.V2_20, "Сумма", restore);
    assertThat(Files.readString(dcs, StandardCharsets.UTF_8)).isEqualTo(written);

    // Смена на другой определяемый тип без value: число подходит к числовому, не подходит к булеву/строке
    assertThatThrownBy(() -> DcsEdit.setParameter(dcs, SchemaVersion.V2_20, "Сумма",
      Map.of("valueType", Map.of("definedTypes", List.of("БезопасныйРежим")))))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("не подходит к новому типу");
    DcsEdit.setParameter(dcs, SchemaVersion.V2_20, "Сумма",
      Map.of("valueType", Map.of("definedTypes", List.of("ДенежнаяСуммаНеотрицательная"))));
    assertThat(valueTypeOf(dcs, "Сумма").get("definedTypes")).isEqualTo(List.of("ДенежнаяСуммаНеотрицательная"));

    // Ссылочный определяемый тип: ссылка конфигуратора своего объекта подходит, чужого — нет
    Map<String, Object> department = Map.of("definedTypes", List.of("Подразделение"));
    DcsEdit.addParameter(dcs, SchemaVersion.V2_20, Map.of("name", "Подразделение", "valueType", department,
      "value", Map.of("type", "dcscor:DesignTimeValue", "text", "Справочник._ДемоПодразделения.ПустаяСсылка")));
    assertThatThrownBy(() -> DcsEdit.addParameter(dcs, SchemaVersion.V2_20, Map.of("name", "Подразделение2",
      "valueType", department,
      "value", Map.of("type", "dcscor:DesignTimeValue", "text", "Справочник.Пользователи.ПустаяСсылка"))))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("не подходит к его типу");
    assertThat(DcsXsdValidation.validate(dcs).get("ok")).isEqualTo(true);
  }

  @Test
  void значениеОпределяемогоТипаСверяетсяСЕгоСоставомВПроектеEdt() throws Exception {
    Path project = tempDir.resolve("edt");
    Path source = Path.of(System.getProperty("fixtures.ssl31edt.root"), "ssl31", "src");
    copyFile(source.resolve("Configuration/Configuration.mdo"), project.resolve("src/Configuration/Configuration.mdo"));
    for (String name : List.of("ДенежнаяСуммаЛюбогоЗнака", "Подразделение")) {
      copyFile(source.resolve("DefinedTypes/" + name + "/" + name + ".mdo"),
        project.resolve("src/DefinedTypes/" + name + "/" + name + ".mdo"));
    }
    String schema = "Reports/АнализЖурналаРегистрации/Templates/ОсновнаяСхемаКомпоновкиДанных/Template.dcs";
    Path dcs = project.resolve("src").resolve(schema);
    copyFile(source.resolve(schema), dcs);
    // В .mdo состав записан именами EDT (Number, CatalogRef.Имя) — сверка та же, что в выгрузке
    DcsEdit.addParameter(dcs, SchemaVersion.V2_20, Map.of("name", "Сумма",
      "valueType", Map.of("definedTypes", List.of("ДенежнаяСуммаЛюбогоЗнака")),
      "value", Map.of("type", "xs:decimal", "text", "5")));
    DcsEdit.addParameter(dcs, SchemaVersion.V2_20, Map.of("name", "Отдел",
      "valueType", Map.of("definedTypes", List.of("Подразделение")),
      "value", Map.of("type", "dcscor:DesignTimeValue", "text", "Справочник._ДемоПодразделения.ПустаяСсылка")));
    String written = Files.readString(dcs, StandardCharsets.UTF_8);
    assertThatThrownBy(() -> DcsEdit.setParameter(dcs, SchemaVersion.V2_20, "Сумма",
      Map.of("value", Map.of("type", "xs:string", "text", "abc"))))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("не подходит к его типу");
    assertThat(Files.readString(dcs, StandardCharsets.UTF_8)).isEqualTo(written);
    assertThat(DcsXsdValidation.validate(dcs).get("ok")).isEqualTo(true);
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> valueTypeOf(Path dcs, String name) throws Exception {
    return (Map<String, Object>) byKey(rows(DcsRead.info(dcs, SchemaVersion.V2_20), "parameters"), "name", name)
      .get("valueType");
  }

  // --------------------------------------------------------------- имена без учёта регистра

  @Test
  void имяПараметраЗанятоБезУчётаРегистра() throws Exception {
    Path dcs = inDump(FILES);
    String original = Files.readString(dcs, StandardCharsets.UTF_8);
    assertThatThrownBy(() -> DcsEdit.addParameter(dcs, SchemaVersion.V2_20, Map.of("name", "размер")))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage("Параметр «размер» уже есть в схеме: «Размер».");
    assertThatThrownBy(() -> DcsEdit.addParameter(dcs, SchemaVersion.V2_20, Map.of("name", "Размер")))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage("Параметр «Размер» уже есть в схеме: «Размер».");
    // Переименование в имя другого параметра другим регистром — тот же отказ
    assertThatThrownBy(() -> DcsEdit.setParameter(dcs, SchemaVersion.V2_20, "Период", Map.of("newName", "РАЗМЕР")))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage("Имя «РАЗМЕР» уже занято: параметр «Размер» уже есть в схеме.");
    assertThat(Files.readString(dcs, StandardCharsets.UTF_8)).isEqualTo(original);
    // Канал отдаёт отказ кодом 2, файл прежний
    assertThat(applyMutation(Map.of("op", "cf-dcs-add-parameter", "objectXml", dcs.toString(),
      "schemaVersion", "V2_20", "payloadJson", "{\"name\":\"ПАПКА\"}"))).isEqualTo(2);
    assertThat(Files.readString(dcs, StandardCharsets.UTF_8)).isEqualTo(original);

    // Смена регистра собственного имени — не занятое имя
    DcsEdit.setParameter(dcs, SchemaVersion.V2_20, "Размер", Map.of("newName", "РАЗМЕР"));
    assertThat(Files.readString(dcs, StandardCharsets.UTF_8)).contains("<name>РАЗМЕР</name>");
    DcsEdit.setParameter(dcs, SchemaVersion.V2_20, "РАЗМЕР", Map.of("newName", "Размер"));
    assertThat(Files.readString(dcs, StandardCharsets.UTF_8)).isEqualTo(original);
    // Новое имя без соперников добавляется
    DcsEdit.addParameter(dcs, SchemaVersion.V2_20, Map.of("name", "РазмерМакс"));
    assertThat(rows(DcsRead.info(dcs, SchemaVersion.V2_20), "parameters").stream().map(row -> row.get("name")))
      .contains("Размер", "РазмерМакс");
  }

  // --------------------------------------------------------------- помощники

  /** Элемент {@code value} параметра корня по имени. */
  private static XmlLines.Node valueElement(String text, String name) {
    for (XmlLines.Node node : XmlLines.children(text, List.of("DataCompositionSchema"))) {
      if ("parameter".equals(node.name()) && name.equals(node.leaves().get("name"))) {
        for (XmlLines.Node child : XmlLines.childrenAt(text, node.start())) {
          if ("value".equals(child.name())) {
            return child;
          }
        }
      }
    }
    throw new AssertionError("value параметра не найден: " + name);
  }

  /** Копия схемы отдельным файлом: выгрузки над ним нет. */
  private Path copy(String relativePath) throws IOException {
    Path target = Files.createTempDirectory(tempDir, "dcs").resolve("Template.xml");
    Files.copy(Ssl31SubmodulePaths.projectRoot().resolve(relativePath), target);
    return target;
  }

  /** Копия схемы в выгрузке: {@code Configuration.xml} и {@code DefinedTypes} рядом, схема — на своём месте. */
  private Path inDump(String relativePath) throws IOException {
    Path cf = dumpRoot();
    Path schema = cf.resolve(relativePath.substring("src/cf/".length()));
    copyFile(Ssl31SubmodulePaths.projectRoot().resolve(relativePath), schema);
    return schema;
  }

  /** Одна выгрузка со всеми схемами корпуса на своих местах: проверки состава видят объекты и типы. */
  private Path corpusDump() throws IOException {
    Path cf = dumpRoot();
    Path source = Ssl31SubmodulePaths.projectRoot().resolve("src/cf");
    for (Path schema : schemaFiles(source)) {
      copyFile(schema, cf.resolve(source.relativize(schema).toString()));
    }
    return cf;
  }

  private Path dumpRoot() throws IOException {
    Path source = Ssl31SubmodulePaths.projectRoot().resolve("src/cf");
    Path cf = Files.createTempDirectory(tempDir, "dump").resolve("cf");
    copyFile(source.resolve("Configuration.xml"), cf.resolve("Configuration.xml"));
    try (Stream<Path> definedTypes = Files.list(source.resolve("DefinedTypes"))) {
      for (Path file : definedTypes.filter(Files::isRegularFile).toList()) {
        copyFile(file, cf.resolve("DefinedTypes").resolve(file.getFileName().toString()));
      }
    }
    return cf;
  }

  private static void copyFile(Path from, Path to) throws IOException {
    Files.createDirectories(to.getParent());
    Files.copy(from, to);
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

  private int applyMutation(Map<String, ?> params) throws Exception {
    Path paramsFile = Files.createTempFile(tempDir, "params", ".json");
    Files.writeString(paramsFile, GSON.toJson(params), StandardCharsets.UTF_8);
    return new CommandLine(new DesignerXmlCli()).execute("apply-mutation", "--params", paramsFile.toString());
  }

  /** Payload через JSON, как его шлёт канал: числа приходят дробными. */
  @SuppressWarnings("unchecked")
  private static Map<String, Object> channel(Map<String, Object> payload) {
    return GSON.fromJson(GSON.toJson(payload), Map.class);
  }

  private static Object channelObject(Object value) {
    return GSON.fromJson(GSON.toJson(value), Object.class);
  }

  private static Map<String, Object> mapOf(String key, Object value) {
    Map<String, Object> out = new LinkedHashMap<>();
    out.put(key, value);
    return out;
  }

  @SuppressWarnings("unchecked")
  private static List<Map<String, Object>> rows(Map<String, Object> info, String key) {
    Object value = info.get(key);
    return value == null ? List.of() : (List<Map<String, Object>>) value;
  }

  private static List<?> list(Object value) {
    return value == null ? List.of() : (List<?>) value;
  }

  private static Map<String, Object> byKey(List<Map<String, Object>> rows, String key, String value) {
    return rows.stream()
      .filter(row -> value.equals(row.get(key)))
      .findFirst()
      .orElseThrow(() -> new AssertionError(key + "=" + value + " не найден: " + rows));
  }
}
