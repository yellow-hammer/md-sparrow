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
 * Предупреждения строгой проверки {@code cf-dcs-validate} о {@code &}-параметрах запросов, не
 * объявленных в схеме ({@link DcsQueryParameters}): на корпусе ssl31 (выгрузка и EDT) их нет,
 * параметры внутри {@code {…}} учитываются, строки и комментарии — нет, регистр не важен, а
 * {@code ok} от предупреждений не меняется.
 */
class DcsQueryParametersTest {

  /** Схема с одним набором-запросом и параметрами ПериодОтчета, НачПериода, КонПериода. */
  private static final String HISTORY_SCHEMA =
    "src/cf/Reports/ИсторияРазмераПриложения/Templates/ОсновнаяСхемаКомпоновкиДанных/Ext/Template.xml";

  /** Два объединения с вложенными наборами-запросами. */
  private static final String UNION_SCHEMA =
    "src/cf/DataProcessors/ВыгрузкаЗагрузкаEnterpriseData/Templates/СхемаКомпоновкиДанных/Ext/Template.xml";

  @TempDir Path tempDir;

  @Test
  void именаПараметровТекстаЗапроса() {
    List<String> names = DcsQueryParameters.names("ВЫБРАТЬ &А, &Б1 + &а // &ВКомментарии\n"
      + "ИЗ Т {ГДЕ Т.П = &ВСкобках} ГДЕ Т.С = \"&ВСтроке\"\"&ИТут\"\"\" И Т.Д В (&_Список) И Т.Е = 1 & 2");

    // Повтор в другом регистре — тот же параметр; «&» без имени после него — не параметр
    assertThat(names).containsExactly("А", "Б1", "ВСкобках", "_Список");
  }

  @Test
  void наКорпусеSsl31ПредупрежденийНет() throws Exception {
    int schemas = 0;
    for (Path schema : schemas(Ssl31SubmodulePaths.projectRoot().resolve("src").resolve("cf"), "Template.xml")) {
      assertThat(DcsQueryParameters.warnings(schema)).as(schema.toString()).isEmpty();
      schemas++;
    }
    for (Path schema : schemas(Path.of(System.getProperty("fixtures.ssl31edt.root")), "Template.dcs")) {
      assertThat(DcsQueryParameters.warnings(schema)).as(schema.toString()).isEmpty();
      schemas++;
    }
    assertThat(schemas).isGreaterThan(100);
  }

  @Test
  void необъявленныйПараметрДаётПредупреждениеНоНеОшибку() throws Exception {
    Path dcs = copy(HISTORY_SCHEMA);
    DcsRead.setQuery(dcs, SchemaVersion.V2_20, "РазмерПриложений", "ВЫБРАТЬ\n"
      + "\tТ.Размер КАК Размер\n"
      + "ИЗ\n"
      + "\tРегистрСведений.РазмерОбъектовМетаданныхПриложений.СрезПоследних(&НаДату, ) КАК Т\n"
      + "ГДЕ\n"
      + "\tТ.Период >= &НАЧПЕРИОДА // &ВКомментарии\n"
      + "{ГДЕ\n"
      + "\tТ.Размер >= &МинимальныйРазмер}");

    Map<String, Object> answer = DcsXsdValidation.validate(dcs);

    assertThat(answer.get("ok")).isEqualTo(true);
    assertThat(answer.get("errors")).isEqualTo(List.of());
    // Объявленный НачПериода в другом регистре предупреждения не даёт; параметр в {…} — даёт.
    assertThat(answer.get("warnings")).isEqualTo(List.of(
      Map.of("message", "параметр запроса &НаДату не объявлен в схеме (набор данных РазмерПриложений)",
        "parameter", "НаДату", "dataSet", "РазмерПриложений", "kind", "undeclaredQueryParameter"),
      Map.of("message", "параметр запроса &МинимальныйРазмер не объявлен в схеме (набор данных РазмерПриложений)",
        "parameter", "МинимальныйРазмер", "dataSet", "РазмерПриложений", "kind", "undeclaredQueryParameter")));
  }

  @Test
  void вложенныйНаборНазываетсяПутём() throws Exception {
    Path dcs = copy(UNION_SCHEMA);
    DcsRead.setQuery(dcs, SchemaVersion.V2_20, "ОбщееКоличествоЭлементов/ОпределениеПолей",
      "ВЫБРАТЬ\n\tNULL КАК Тип,\n\t0 КАК ОбщееКоличество\nГДЕ\n\t&Отбор");

    List<Map<String, Object>> warnings = DcsQueryParameters.warnings(dcs);

    assertThat(warnings).extracting(w -> w.get("dataSet")).containsExactly("ОбщееКоличествоЭлементов/ОпределениеПолей");
    assertThat(warnings).extracting(w -> w.get("parameter")).containsExactly("Отбор");
  }

  @Test
  void каналReadJsonОтдаётПредупрежденияСтрогойПроверки() throws Exception {
    Path dcs = copy(HISTORY_SCHEMA);
    DcsRead.setQuery(dcs, SchemaVersion.V2_20, "РазмерПриложений",
      "ВЫБРАТЬ\n\tТ.Размер КАК Размер\nИЗ\n\tРегистрСведений.РазмерОбъектовМетаданныхПриложений КАК Т\n"
        + "ГДЕ\n\tТ.Период >= &НовыйПараметр");

    Map<String, Object> strict = readJson(Map.of("op", "cf-dcs-validate", "objectXml", dcs.toString(), "strict", true));
    assertThat(strict.get("ok")).isEqualTo(true);
    assertThat(String.valueOf(strict.get("warnings"))).contains("&НовыйПараметр не объявлен в схеме");

    // Ответ без strict — JAXB-чтение, без предупреждений
    Map<String, Object> plain = readJson(Map.of(
      "op", "cf-dcs-validate", "objectXml", dcs.toString(), "schemaVersion", "V2_20"));
    assertThat(plain.get("valid")).isEqualTo(true);
    assertThat(plain).doesNotContainKey("warnings");
  }

  // --- общее ---------------------------------------------------------------------------------

  private static List<Path> schemas(Path root, String fileName) throws Exception {
    List<Path> out = new ArrayList<>();
    try (Stream<Path> files = Files.walk(root)) {
      files.filter(p -> p.getFileName().toString().equals(fileName))
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

  private Path copy(String relativePath) throws Exception {
    Path copy = Files.createTempDirectory(tempDir, "dcs").resolve("Template.xml");
    Files.copy(Ssl31SubmodulePaths.projectRoot().resolve(relativePath), copy, StandardCopyOption.REPLACE_EXISTING);
    return copy;
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> readJson(Map<String, ?> params) throws Exception {
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
    return new Gson().fromJson(buffer.toString(StandardCharsets.UTF_8), Map.class);
  }
}
