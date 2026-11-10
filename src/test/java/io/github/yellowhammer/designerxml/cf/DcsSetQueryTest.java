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
import java.util.stream.Stream;

/**
 * Запись текста запроса {@code cf-dcs-set-query} ({@link DcsRead#setQuery}): переводы строк текста
 * приводятся к переводу строки файла, эхо прочитанного {@code cf-dcs-info} текста — байтовый no-op
 * на каждом запросе ssl31, наборы внутри объединений адресуются путём {@code Объединение/Вложенный},
 * а путь к самому объединению — отказ, а не запись в первый вложенный набор.
 */
class DcsSetQueryTest {

  /** Схема с CRLF-файлом и одним набором-запросом. */
  private static final String STATUSES_SCHEMA =
    "src/cf/Reports/_ДемоСтатусыЗаказовПокупателей/Templates/ОсновнаяСхемаКомпоновкиДанных/Ext/Template.xml";

  /** Два объединения, и в каждом вложенный набор-запрос с одним и тем же именем ОпределениеПолей. */
  private static final String UNION_SCHEMA =
    "src/cf/DataProcessors/ВыгрузкаЗагрузкаEnterpriseData/Templates/СхемаКомпоновкиДанных/Ext/Template.xml";

  @TempDir Path tempDir;

  // --- концы строк и эхо ---------------------------------------------------------------------

  @Test
  void эхоКаждогоЗапросаSsl31НеМеняетФайл() throws Exception {
    int echoed = 0;
    int nested = 0;
    for (Path schema : ssl31Schemas()) {
      Path copy = copy(schema);
      byte[] before = Files.readAllBytes(copy);
      for (String[] query : queries(DcsRead.info(copy, SchemaVersion.V2_20))) {
        DcsRead.setQuery(copy, SchemaVersion.V2_20, query[0], query[1]);
        assertThat(Files.readAllBytes(copy)).as(schema + " / " + query[0]).isEqualTo(before);
        echoed++;
        nested += query[0].contains("/") ? 1 : 0;
      }
    }
    // 38 текстов наборов корня и объединений (запросы вложенных схем cf-dcs-info не отдаёт), два — внутри объединений
    assertThat(echoed).isGreaterThanOrEqualTo(38);
    assertThat(nested).isGreaterThanOrEqualTo(2);
  }

  @Test
  void новыйТекстПишетсяСПереводомСтрокиФайла() throws Exception {
    Path dcs = copy(Ssl31SubmodulePaths.projectRoot().resolve(STATUSES_SCHEMA));
    String before = Files.readString(dcs, StandardCharsets.UTF_8);
    assertThat(before).contains("\r\n");
    String name = onlyQuerySet(dcs);

    // Текст из textarea приходит с LF: в CRLF-файле он обязан лечь с CRLF
    DcsRead.setQuery(dcs, SchemaVersion.V2_20, name, "ВЫБРАТЬ\n\tЗаказ.Ссылка КАК Ссылка\nИЗ\n\tДокумент.Заказ КАК Заказ");

    String after = Files.readString(dcs, StandardCharsets.UTF_8);
    assertThat(after).contains("<query>ВЫБРАТЬ\r\n\tЗаказ.Ссылка КАК Ссылка\r\nИЗ\r\n\tДокумент.Заказ КАК Заказ</query>");
    assertThat(after.replace("\r\n", "")).doesNotContain("\n").doesNotContain("\r");
    // Вне текста запроса файл байт в байт прежний
    assertThat(after).isEqualTo(withQuery(before, "<dataSet",
      "ВЫБРАТЬ\r\n\tЗаказ.Ссылка КАК Ссылка\r\nИЗ\r\n\tДокумент.Заказ КАК Заказ"));
    assertThat(DcsXsdValidation.validate(dcs).get("ok")).isEqualTo(true);

    // Тот же текст с CRLF и с одиночными CR — те же байты: запись идемпотентна
    byte[] written = Files.readAllBytes(dcs);
    DcsRead.setQuery(dcs, SchemaVersion.V2_20, name, "ВЫБРАТЬ\r\n\tЗаказ.Ссылка КАК Ссылка\r\nИЗ\r\n\tДокумент.Заказ КАК Заказ");
    assertThat(Files.readAllBytes(dcs)).isEqualTo(written);
    DcsRead.setQuery(dcs, SchemaVersion.V2_20, name, "ВЫБРАТЬ\r\tЗаказ.Ссылка КАК Ссылка\rИЗ\r\tДокумент.Заказ КАК Заказ");
    assertThat(Files.readAllBytes(dcs)).isEqualTo(written);

    // Эхо прочитанного текста (cf-dcs-info отдаёт его с LF) — байтовый no-op
    String read = (String) dataSet(DcsRead.info(dcs, SchemaVersion.V2_20), name).get("query");
    assertThat(read).doesNotContain("\r");
    DcsRead.setQuery(dcs, SchemaVersion.V2_20, name, read);
    assertThat(Files.readAllBytes(dcs)).isEqualTo(written);
  }

  @Test
  void вФайлеСLfТекстПишетсяСLf() throws Exception {
    Path dcs = copy(Ssl31SubmodulePaths.projectRoot().resolve(STATUSES_SCHEMA));
    Files.writeString(dcs, Files.readString(dcs, StandardCharsets.UTF_8).replace("\r\n", "\n"), StandardCharsets.UTF_8);
    String name = onlyQuerySet(dcs);

    DcsRead.setQuery(dcs, SchemaVersion.V2_20, name, "ВЫБРАТЬ\r\n\t1 КАК Поле1");

    String after = Files.readString(dcs, StandardCharsets.UTF_8);
    assertThat(after).contains("<query>ВЫБРАТЬ\n\t1 КАК Поле1</query>").doesNotContain("\r");
  }

  @Test
  void текстСоСпецсимволамиЭкранируетсяКакУПлатформы() throws Exception {
    Path dcs = copy(Ssl31SubmodulePaths.projectRoot().resolve(STATUSES_SCHEMA));
    String name = onlyQuerySet(dcs);
    String query = "ВЫБРАТЬ\n\tТ.Сумма КАК Сумма\nИЗ\n\tДокумент.Заказ КАК Т\nГДЕ\n\tТ.Сумма > &Порог И Т.Имя <> \"А&Б\"";

    DcsRead.setQuery(dcs, SchemaVersion.V2_20, name, query);

    assertThat(Files.readString(dcs, StandardCharsets.UTF_8))
      .contains("Т.Сумма &gt; &amp;Порог И Т.Имя &lt;&gt; \"А&amp;Б\"</query>");
    assertThat(dataSet(DcsRead.info(dcs, SchemaVersion.V2_20), name).get("query")).isEqualTo(query);
    // Эхо после записи — no-op
    byte[] written = Files.readAllBytes(dcs);
    DcsRead.setQuery(dcs, SchemaVersion.V2_20, name, query);
    assertThat(Files.readAllBytes(dcs)).isEqualTo(written);
  }

  // --- наборы внутри объединений -------------------------------------------------------------

  @Test
  void вложенныйНаборАдресуетсяПутём() throws Exception {
    Path dcs = copy(Ssl31SubmodulePaths.projectRoot().resolve(UNION_SCHEMA));
    String before = Files.readString(dcs, StandardCharsets.UTF_8);
    String second = nestedQuery(DcsRead.info(dcs, SchemaVersion.V2_20), "ОбщееКоличествоЭлементов", "ОпределениеПолей");

    DcsRead.setQuery(dcs, SchemaVersion.V2_20, "РегистрацияИзменений/ОпределениеПолей",
      "ВЫБРАТЬ\n\tNULL КАК ОбъектСсылка,\n\tNULL КАК ОбъектТип");

    String after = Files.readString(dcs, StandardCharsets.UTF_8);
    assertThat(after).isEqualTo(withQuery(before, "<name>РегистрацияИзменений</name>",
      "ВЫБРАТЬ\r\n\tNULL КАК ОбъектСсылка,\r\n\tNULL КАК ОбъектТип"));
    Map<String, Object> info = DcsRead.info(dcs, SchemaVersion.V2_20);
    assertThat(nestedQuery(info, "РегистрацияИзменений", "ОпределениеПолей"))
      .isEqualTo("ВЫБРАТЬ\n\tNULL КАК ОбъектСсылка,\n\tNULL КАК ОбъектТип");
    // Одноимённый набор другого объединения не тронут
    assertThat(nestedQuery(info, "ОбщееКоличествоЭлементов", "ОпределениеПолей")).isEqualTo(second);
    assertThat(after).contains("<query>ВЫБРАТЬ\r\n\tNULL КАК ОбъектСсылка,\r\n\tNULL КАК ОбъектТип</query>");
    assertThat(DcsXsdValidation.validate(dcs).get("ok")).isEqualTo(true);

    // Пробелы вокруг звеньев пути не мешают
    byte[] written = Files.readAllBytes(dcs);
    DcsRead.setQuery(dcs, SchemaVersion.V2_20, " РегистрацияИзменений / ОпределениеПолей ",
      "ВЫБРАТЬ\n\tNULL КАК ОбъектСсылка,\n\tNULL КАК ОбъектТип");
    assertThat(Files.readAllBytes(dcs)).isEqualTo(written);
  }

  @Test
  void путьКОбъединениюИНеверныеПутиОтказывают() throws Exception {
    Path dcs = copy(Ssl31SubmodulePaths.projectRoot().resolve(UNION_SCHEMA));
    byte[] before = Files.readAllBytes(dcs);

    // Объединение — не запрос: текст не уходит в его первый вложенный набор
    assertThatThrownBy(() -> DcsRead.setQuery(dcs, SchemaVersion.V2_20, "РегистрацияИзменений", "ВЫБРАТЬ 1"))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("объединение")
      .hasMessageContaining("РегистрацияИзменений/");
    // Имя без пути ищет набор верхнего уровня; подсказка называет оба пути одноимённого набора
    assertThatThrownBy(() -> DcsRead.setQuery(dcs, SchemaVersion.V2_20, "ОпределениеПолей", "ВЫБРАТЬ 1"))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("не найден")
      .hasMessageContaining("РегистрацияИзменений/ОпределениеПолей")
      .hasMessageContaining("ОбщееКоличествоЭлементов/ОпределениеПолей");
    assertThatThrownBy(() -> DcsRead.setQuery(dcs, SchemaVersion.V2_20, "РегистрацияИзменений/Нет", "ВЫБРАТЬ 1"))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("нет вложенного набора «Нет»");
    assertThatThrownBy(() -> DcsRead.setQuery(
      dcs, SchemaVersion.V2_20, "РегистрацияИзменений/ОпределениеПолей/Глубже", "ВЫБРАТЬ 1"))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("не объединение");
    assertThatThrownBy(() -> DcsRead.setQuery(dcs, SchemaVersion.V2_20, "РегистрацияИзменений//", "ВЫБРАТЬ 1"))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("пустое имя");
    // Без имени: наборов-запросов в схеме два (оба вложенные) — нужен путь
    assertThatThrownBy(() -> DcsRead.setQuery(dcs, SchemaVersion.V2_20, "", "ВЫБРАТЬ 1"))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("несколько");
    assertThat(Files.readAllBytes(dcs)).isEqualTo(before);
  }

  @Test
  void наборОбъектНеЗапрос() throws Exception {
    Path dcs = copy(Ssl31SubmodulePaths.projectRoot().resolve(
      "src/cf/Reports/РезультатыПроверкиУчета/Templates/ОсновнаяСхемаКомпоновкиДанных/Ext/Template.xml"));
    byte[] before = Files.readAllBytes(dcs);

    assertThatThrownBy(() -> DcsRead.setQuery(dcs, SchemaVersion.V2_20, "ВнешняяТаблица", "ВЫБРАТЬ 1"))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("объект, а не запрос");
    assertThat(Files.readAllBytes(dcs)).isEqualTo(before);
  }

  @Test
  void каналApplyMutationПишетВложенныйНаборTemplateDcs() throws Exception {
    // В проекте 1С:EDT схема — тот же формат с именем Template.dcs: адресация путём работает и там
    Path source = Path.of(System.getProperty("fixtures.ssl31edt.root"), "ssl31", "src", "DataProcessors",
      "ВыгрузкаЗагрузкаEnterpriseData", "Templates", "СхемаКомпоновкиДанных", "Template.dcs");
    Path dcs = Files.createTempDirectory(tempDir, "edt").resolve("Template.dcs");
    Files.copy(source, dcs, StandardCopyOption.REPLACE_EXISTING);
    byte[] before = Files.readAllBytes(dcs);

    // Эхо через канал — файл байтово прежний
    String recorded = nestedQuery(DcsRead.info(dcs, SchemaVersion.V2_20), "ОбщееКоличествоЭлементов",
      "ОпределениеПолей");
    assertThat(applyMutation(Map.of("op", "cf-dcs-set-query", "objectXml", dcs.toString(),
      "tag", "ОбщееКоличествоЭлементов/ОпределениеПолей", "payloadJson", recorded))).isZero();
    assertThat(Files.readAllBytes(dcs)).isEqualTo(before);

    assertThat(applyMutation(Map.of("op", "cf-dcs-set-query", "objectXml", dcs.toString(),
      "tag", "ОбщееКоличествоЭлементов/ОпределениеПолей", "payloadJson", "ВЫБРАТЬ\n\t0 КАК ОбщееКоличество"))).isZero();
    assertThat(nestedQuery(DcsRead.info(dcs, SchemaVersion.V2_20), "ОбщееКоличествоЭлементов", "ОпределениеПолей"))
      .isEqualTo("ВЫБРАТЬ\n\t0 КАК ОбщееКоличество");
    assertThat(DcsXsdValidation.validate(dcs).get("ok")).isEqualTo(true);

    // Путь к объединению — отказ кодом 2, файл не меняется
    byte[] written = Files.readAllBytes(dcs);
    assertThat(applyMutation(Map.of("op", "cf-dcs-set-query", "objectXml", dcs.toString(),
      "tag", "ОбщееКоличествоЭлементов", "payloadJson", "ВЫБРАТЬ 1"))).isEqualTo(2);
    assertThat(Files.readAllBytes(dcs)).isEqualTo(written);
  }

  // --- общее ---------------------------------------------------------------------------------

  /** Все схемы компоновки ssl31 (выгрузка конфигуратора). */
  private static List<Path> ssl31Schemas() throws Exception {
    List<Path> out = new ArrayList<>();
    try (Stream<Path> files = Files.walk(Ssl31SubmodulePaths.projectRoot().resolve("src").resolve("cf"))) {
      files.filter(p -> p.getFileName().toString().equals("Template.xml"))
        .filter(p -> {
          try {
            return Files.readString(p, StandardCharsets.UTF_8).contains("<DataCompositionSchema");
          } catch (Exception e) {
            return false;
          }
        })
        .forEach(out::add);
    }
    return out;
  }

  /** Пары «путь набора, текст запроса» по ответу cf-dcs-info, вложенные наборы — путём. */
  @SuppressWarnings("unchecked")
  private static List<String[]> queries(Map<String, Object> info) {
    List<String[]> out = new ArrayList<>();
    collect((List<Map<String, Object>>) info.get("dataSets"), "", out);
    return out;
  }

  @SuppressWarnings("unchecked")
  private static void collect(List<Map<String, Object>> sets, String parent, List<String[]> out) {
    if (sets == null) {
      return;
    }
    for (Map<String, Object> set : sets) {
      String path = parent.isEmpty() ? String.valueOf(set.get("name")) : parent + "/" + set.get("name");
      if ("query".equals(set.get("type")) && set.get("query") != null) {
        out.add(new String[] {path, (String) set.get("query")});
      }
      collect((List<Map<String, Object>>) set.get("items"), path, out);
    }
  }

  private static String onlyQuerySet(Path dcs) throws Exception {
    List<String[]> queries = queries(DcsRead.info(dcs, SchemaVersion.V2_20));
    assertThat(queries).hasSize(1);
    return queries.get(0)[0];
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> dataSet(Map<String, Object> info, String name) {
    return ((List<Map<String, Object>>) info.get("dataSets")).stream()
      .filter(set -> name.equals(set.get("name"))).findFirst()
      .orElseThrow(() -> new AssertionError("набор не найден: " + name));
  }

  @SuppressWarnings("unchecked")
  private static String nestedQuery(Map<String, Object> info, String union, String item) {
    return ((List<Map<String, Object>>) dataSet(info, union).get("items")).stream()
      .filter(set -> item.equals(set.get("name"))).findFirst()
      .map(set -> (String) set.get("query"))
      .orElseThrow(() -> new AssertionError("вложенный набор не найден: " + union + "/" + item));
  }

  private Path copy(Path source) throws Exception {
    Path copy = Files.createTempDirectory(tempDir, "dcs").resolve("Template.xml");
    Files.copy(source, copy, StandardCopyOption.REPLACE_EXISTING);
    return copy;
  }

  private int applyMutation(Map<String, ?> params) throws Exception {
    Path paramsFile = Files.createTempFile(tempDir, "params", ".json");
    Files.writeString(paramsFile, new Gson().toJson(params), StandardCharsets.UTF_8);
    return new CommandLine(new DesignerXmlCli()).execute("apply-mutation", "--params", paramsFile.toString());
  }

  /** Текст схемы, где содержимое первого {@code <query>} после якоря заменено на {@code raw}. */
  private static String withQuery(String text, String anchor, String raw) {
    int from = text.indexOf(anchor);
    assertThat(from).as(anchor).isNotNegative();
    int start = text.indexOf("<query>", from) + "<query>".length();
    int end = text.indexOf("</query>", start);
    return text.substring(0, start) + raw + text.substring(end);
  }
}
