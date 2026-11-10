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
import io.github.yellowhammer.designerxml.Ssl31SubmodulePaths;
import io.github.yellowhammer.designerxml.cli.DesignerXmlCli;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Строгая проверка схемы компоновки ({@link DcsXsdValidation}): XSD-валидация по схемам СКД с поправками
 * «как пишет платформа». Главный тест — корпус: каждая реальная схема ssl31 (все {@code Template.xml}
 * с корнем {@code DataCompositionSchema}) и EDT-пары ({@code Template.dcs}) обязаны проходить строгую
 * проверку без ошибок; поправки {@link DcsXsdShims} покрывают формы этого корпуса. Отрицательные тесты —
 * битый элемент и неверный тип дают {@code ok=false} с текстом и местом ошибки. Канал {@code read-json}
 * без {@code strict} отвечает JAXB-чтением.
 */
class DcsXsdValidationTest {

  /** Обычная схема отчёта: набор-запрос, параметры, варианты настроек. */
  private static final String PLAIN_SCHEMA =
    "src/cf/Reports/РезультатыПроверкиУчета/Templates/ОсновнаяСхемаКомпоновкиДанных/Ext/Template.xml";

  /**
   * EDT-пары по одной на поправку {@link DcsXsdShims} и заглушки: перечисление диаграмм в xsi:type
   * (ОценкаПроизводительности), вложенная схема без URL и lastId списка значений (_ДемоФайлы,
   * _ДемоОтчетПоСчетамНаОплатуГлобальный), шаблоны областей и группировок (ИнтерактивноеИзменениеВыгрузки),
   * обычная схема (РезультатыПроверкиУчета).
   */
  private static final List<String> EDT_SCHEMAS = List.of(
    "ssl31/src/Reports/ОценкаПроизводительности/Templates/ОсновнаяСхемаКомпоновкиДанных/Template.dcs",
    "ssl31/src/Reports/_ДемоФайлы/Templates/ОсновнаяСхемаКомпоновкиДанных/Template.dcs",
    "ssl31/src/Reports/_ДемоОтчетПоСчетамНаОплатуГлобальный/Templates/ОсновнаяСхемаКомпоновкиДанных/Template.dcs",
    "ssl31/src/DataProcessors/ИнтерактивноеИзменениеВыгрузки/Templates/СхемаКомпоновкиДанных/Template.dcs",
    "ssl31/src/Reports/РезультатыПроверкиУчета/Templates/ОсновнаяСхемаКомпоновкиДанных/Template.dcs");

  @TempDir Path tempDir;

  // --- корпус: реальные схемы проходят строгую проверку без ошибок -----------------------------

  @Test
  void allSsl31DesignerSchemasPassStrict() throws Exception {
    List<Path> corpus = designerCorpus();
    // Нижняя граница числа схем: просадка счётчика означает, что корпус потерян, а не что стало чище
    assertThat(corpus).hasSizeGreaterThanOrEqualTo(70);
    assertThat(failures(corpus)).isEmpty();
  }

  @Test
  void edtSchemasPassStrict() {
    String root = System.getProperty("fixtures.ssl31edt.root");
    assertThat(root).isNotBlank();
    List<Path> corpus = EDT_SCHEMAS.stream().map(relative -> Path.of(root).resolve(relative)).toList();
    corpus.forEach(file -> assertThat(file).exists());
    assertThat(failures(corpus)).isEmpty();
  }

  /** Все найденные несоответствия корпуса одной строкой на файл: видно и файл, и первую причину. */
  private static List<String> failures(List<Path> corpus) {
    List<String> out = new ArrayList<>();
    for (Path file : corpus) {
      Map<String, Object> answer = DcsXsdValidation.validate(file);
      if (!Boolean.TRUE.equals(answer.get("ok"))) {
        out.add(file + " -> " + answer.get("errors"));
      }
    }
    return out;
  }

  /**
   * Все Template.xml submodule ssl31 с корнем DataCompositionSchema (прочие виды макетов — не СКД):
   * кроме выгрузки конфигурации ({@code src/cf}) схемы лежат и у внешних отчётов ({@code src/erf}).
   */
  private static List<Path> designerCorpus() throws Exception {
    Path root = Ssl31SubmodulePaths.projectRoot();
    try (Stream<Path> walk = Files.walk(root)) {
      return walk
        .filter(p -> p.getFileName().toString().equals("Template.xml"))
        .filter(DcsXsdValidationTest::hasDcsRoot)
        .sorted()
        .toList();
    }
  }

  private static boolean hasDcsRoot(Path file) {
    try {
      byte[] head = new byte[256];
      int read;
      try (var in = Files.newInputStream(file)) {
        read = in.readNBytes(head, 0, head.length);
      }
      return new String(head, 0, Math.max(read, 0), StandardCharsets.UTF_8).contains("<DataCompositionSchema");
    } catch (Exception e) {
      return false;
    }
  }

  // --- отрицательные: ошибки с текстом и местом ------------------------------------------------

  @Test
  void brokenElementFailsWithPlace() throws Exception {
    Path dcs = copyPlainSchema();
    replaceOnce(dcs, "<dataSourceType>Local</dataSourceType>",
      "<dataSourceType>Local</dataSourceType><чужойЭлемент/>");

    Map<String, Object> answer = DcsXsdValidation.validate(dcs);

    assertThat(answer.get("ok")).isEqualTo(false);
    Map<String, Object> error = firstError(answer);
    assertThat(String.valueOf(error.get("message"))).contains("чужойЭлемент");
    // Xerces даёт и строку, и колонку: ответ отдаёт их, где они есть
    assertThat(((Number) error.get("line")).intValue()).isGreaterThan(0);
    assertThat(((Number) error.get("column")).intValue()).isGreaterThan(0);
  }

  @Test
  void wrongTypeFailsWithMessage() throws Exception {
    Path dcs = copyPlainSchema();
    replaceOnce(dcs, "<useRestriction>true</useRestriction>",
      "<useRestriction>может быть</useRestriction>");

    Map<String, Object> answer = DcsXsdValidation.validate(dcs);

    assertThat(answer.get("ok")).isEqualTo(false);
    Map<String, Object> error = firstError(answer);
    assertThat(String.valueOf(error.get("message"))).contains("может быть");
    assertThat(((Number) error.get("line")).intValue()).isGreaterThan(0);
  }

  @Test
  void foreignRootFailsInsteadOfPassingSilently() throws Exception {
    // Описание объекта (корень MetaDataObject) — не схема компоновки: строгая проверка говорит об этом
    Map<String, Object> answer = DcsXsdValidation.validate(Ssl31SubmodulePaths.anyReportObjectXml());

    assertThat(answer.get("ok")).isEqualTo(false);
    assertThat(String.valueOf(firstError(answer).get("message"))).contains("MetaDataObject");
  }

  @Test
  void unknownXsiTypeOfKnownNamespaceStaysAnError() throws Exception {
    // Заглушки — только для пространств без XSD; опечатка в типе известного пространства остаётся ошибкой
    Path dcs = copyPlainSchema();
    replaceOnce(dcs, "<useRestriction>true</useRestriction>",
      "<useRestriction xsi:type=\"v8:НетТакогоТипа\">true</useRestriction>");

    Map<String, Object> answer = DcsXsdValidation.validate(dcs);

    assertThat(answer.get("ok")).isEqualTo(false);
    assertThat(String.valueOf(firstError(answer).get("message"))).contains("НетТакогоТипа");
  }

  // --- канал read-json -------------------------------------------------------------------------

  @Test
  void readJsonChannelAnswersBothModes() throws Exception {
    Path dcs = copyPlainSchema();

    Map<String, Object> strict = parseJson(readJson(Map.of(
      "op", "cf-dcs-validate", "objectXml", dcs.toString(), "strict", true)));
    assertThat(strict.get("ok")).isEqualTo(true);
    assertThat(strict.get("errors")).isEqualTo(List.of());

    // без strict — ответ JAXB-чтения: valid и dataSetCount, без ok и errors
    Map<String, Object> plain = parseJson(readJson(Map.of(
      "op", "cf-dcs-validate", "objectXml", dcs.toString(), "schemaVersion", "V2_20")));
    assertThat(plain.get("valid")).isEqualTo(true);
    assertThat(plain).containsKey("dataSetCount");
    assertThat(plain).doesNotContainKeys("ok", "errors");
  }

  @Test
  void readJsonChannelReportsStrictErrors() throws Exception {
    Path dcs = copyPlainSchema();
    replaceOnce(dcs, "<dataSourceType>Local</dataSourceType>",
      "<dataSourceType>Local</dataSourceType><чужойЭлемент/>");

    Map<String, Object> answer = parseJson(readJson(Map.of(
      "op", "cf-dcs-validate", "objectXml", dcs.toString(), "strict", true)));

    assertThat(answer.get("ok")).isEqualTo(false);
    assertThat(String.valueOf(answer.get("errors"))).contains("чужойЭлемент");
  }

  // --- общее -----------------------------------------------------------------------------------

  /** Запуск канала read-json с параметрами из UTF-8 JSON-файла и перехватом stdout. */
  private String readJson(Map<String, ?> params) throws Exception {
    Path paramsFile = Files.createTempFile(tempDir, "params", ".json");
    Files.writeString(paramsFile, new Gson().toJson(params), StandardCharsets.UTF_8);
    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    PrintStream prev = System.out;
    int exit;
    try {
      System.setOut(new PrintStream(buffer, true, StandardCharsets.UTF_8));
      exit = new CommandLine(new DesignerXmlCli()).execute("read-json", "--params", paramsFile.toString());
    } finally {
      System.setOut(prev);
    }
    assertThat(exit).isZero();
    return buffer.toString(StandardCharsets.UTF_8);
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> parseJson(String json) {
    Map<String, Object> parsed = new Gson().fromJson(json, Map.class);
    assertThat(parsed).isNotNull();
    return parsed;
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> firstError(Map<String, Object> answer) {
    List<Map<String, Object>> errors = (List<Map<String, Object>>) answer.get("errors");
    assertThat(errors).isNotEmpty();
    return errors.get(0);
  }

  /** Копия обычной схемы из submodule во временный каталог: индекс фикстур не трогается. */
  private Path copyPlainSchema() throws Exception {
    Path source = Ssl31SubmodulePaths.projectRoot().resolve(PLAIN_SCHEMA);
    Path copy = Files.createTempDirectory(tempDir, "dcs").resolve("Template.xml");
    Files.copy(source, copy, StandardCopyOption.REPLACE_EXISTING);
    return copy;
  }

  /** Точечная порча копии: якорь обязан быть в файле ровно в этом виде. */
  private static void replaceOnce(Path file, String anchor, String replacement) throws Exception {
    String text = Files.readString(file, StandardCharsets.UTF_8);
    assertThat(text).contains(anchor);
    Files.writeString(file, text.replaceFirst(java.util.regex.Pattern.quote(anchor),
      java.util.regex.Matcher.quoteReplacement(replacement)), StandardCharsets.UTF_8);
  }
}
