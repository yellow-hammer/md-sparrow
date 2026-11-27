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
package io.github.yellowhammer.designerxml.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.gson.Gson;
import com.google.gson.JsonParser;
import io.github.yellowhammer.bsl.BspVersions;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import picocli.CommandLine;

class SourceVersionsTest {
  @TempDir
  Path temporary;

  @ParameterizedTest
  @CsvSource({
    "ssl31/src/cf/Configuration.xml, Version",
    "ssl31/src/cfe/_ДемоРасширение/Configuration.xml, Version",
    "ssl31-edt/ssl31/src/Configuration/Configuration.mdo, version",
    "ssl31-edt/ssl31._ДемоРасширение/src/Configuration/Configuration.mdo, version"
  })
  void configurationVersionLeavesAllOtherBytesIncludingDefaultRolesUntouched(String fixture, String tag)
      throws Exception {
    Path file = temporary.resolve(Path.of(fixture).getFileName());
    Files.copy(Path.of("fixtures", fixture), file);
    String before = Files.readString(file);
    assertThat(before).contains("<" + tag + ">");
    assertThat(apply("configuration-version-set", "configurationXml", file, "91.92.93")).isZero();
    assertThat(Files.readString(file)).isEqualTo(before.replaceFirst(
      "(<" + tag + ">)[^<]*(</" + tag + ">)", "<" + tag + ">91.92.93</" + tag + ">"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"Ext/ObjectModule.bsl", "ObjectModule.bsl"})
  void externalVersionUpdatesOneLiteralInEitherLayout(String relative) throws Exception {
    Path file = temporary.resolve(relative);
    Files.createDirectories(file.getParent());
    String source = """
      Function СведенияОВнешнейОбработке() Export
        // Версия = "0.0";
        Версия = "2.3.4";
        Регистрация = New Structure;
        Регистрация.Insert("ВЕРСИЯ", Версия);
        ДругаяСтруктура.Версия = "0.1";
        Return Регистрация;
      EndFunction
      """;
    source = "\uFEFF" + source.replace("\n", "\r\n");
    Files.writeString(file, source);
    assertThat(BspVersions.externalVersion(file)).isEqualTo("2.3.4");
    assertThat(apply("bsp-external-version-set", "moduleFile", file, "8.9.10")).isZero();
    assertThat(Files.readString(file)).isEqualTo(source.replace("2.3.4", "8.9.10"));
    assertThat(BspVersions.externalVersion(file)).isEqualTo("8.9.10");
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "Возврат Новый Структура(\"Вид, Версия\", ОпределитьВид(\"Отчёт\", Истина), \"3.0\");",
    "Данные = Новый Структура; Данные.Версия = \"3.0\"; Возврат Данные;"
  })
  void externalVersionSupportsConstructorAndDirectProperty(String body) throws Exception {
    Path file = temporary.resolve("ObjectModule.bsl");
    String source = "Функция СведенияОВнешнейОбработке() Экспорт\n" + body + "\nКонецФункции";
    Files.writeString(file, source);
    assertThat(BspVersions.externalVersion(file)).isEqualTo("3.0");
    BspVersions.setExternalVersion(file, "3.\"1");
    assertThat(Files.readString(file)).isEqualTo(source.replace("3.0", "3.\"\"1"));
    assertThat(BspVersions.externalVersion(file)).isEqualTo("3.\"1");
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "Возврат Новый Структура(\"Версия\", ПолучитьВерсию());",
    "Если Условие Тогда Возврат Новый Структура(\"Версия\", \"1\"); КонецЕсли; "
      + "Возврат Новый Структура(\"Версия\", \"2\");",
    "Описание = Новый Структура(\"Версия\", \"1\");\n#Если Клиент Тогда\n"
      + "Описание.Версия = \"2\";\n#КонецЕсли\nВозврат Описание;"
  })
  void externalVersionRequiresAnUnambiguousLiteral(String body) throws Exception {
    Path file = temporary.resolve("ObjectModule.bsl");
    String source = "Функция СведенияОВнешнейОбработке() Экспорт\n" + body + "\nКонецФункции";
    Files.writeString(file, source);
    assertThat(BspVersions.externalVersion(file)).isNull();
    assertThatThrownBy(() -> BspVersions.setExternalVersion(file, "3")).hasMessageContaining(file.toString());
    assertThat(Files.readString(file)).isEqualTo(source);
  }

  @Test
  void readsVersionsInOneRequestEvenWhenAModuleContainsLongStrings() throws Exception {
    Path sources = Path.of("fixtures", "ssl31", "src", "epf").toAbsolutePath();
    String print = "_ДемоПечатьДеталейОперации";
    String input = "_ДемоВводНаОснованииОприходованийТоваров";
    String longModule = sources.resolve(print).resolve(print).resolve("Ext/ObjectModule.bsl").toString();
    String otherModule = sources.resolve(input).resolve(input).resolve("Ext/ObjectModule.bsl").toString();

    Path params = temporary.resolve("params.json");
    Files.writeString(params, new Gson().toJson(Map.of(
      "op", "bsp-external-versions-get", "moduleFiles", List.of(longModule, otherModule))));
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    PrintStream stdout = System.out;
    try (PrintStream captured = new PrintStream(output, true, StandardCharsets.UTF_8)) {
      System.setOut(captured);
      assertThat(new CommandLine(new DesignerXmlCli()).execute("read-json", "--params", params.toString())).isZero();
    } finally {
      System.setOut(stdout);
    }
    var versions = JsonParser.parseString(output.toString(StandardCharsets.UTF_8)).getAsJsonObject();
    assertThat(versions.get(longModule).getAsString()).isEqualTo("3.1.11.2");
    assertThat(versions.get(otherModule).getAsString()).isEqualTo("3.0.2.1");
  }

  @Test
  void missingRegistrationHasNoVersion() throws Exception {
    Path file = temporary.resolve("ObjectModule.bsl");
    Files.writeString(file, "Функция ДругойМетод() Версия = \"1.0\"; Возврат Версия; КонецФункции");
    assertThat(BspVersions.externalVersion(file)).isNull();
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void libraryListAndVersionUpdateUseRegisteredModulesAndAlwaysIncludeBsp(boolean edt) throws Exception {
    Path configuration = temporary.resolve(edt ? "src/Configuration/Configuration.mdo" : "Configuration.xml");
    Path modules = temporary.resolve(edt ? "src/CommonModules" : "CommonModules");
    Path bsp = writeModule(modules, edt, "ОбновлениеИнформационнойБазыБСП", library("БСП", "\"1.0\""));
    writeModule(modules, edt, "ПодсистемыКонфигурацииПереопределяемый", """
      Процедура ПриДобавленииПодсистем(Модули)
        Модули.Добавить("МодульБиблиотеки");
        Модули.Добавить("ОбновлениеИнформационнойБазыБСП");
        Модули.Добавить("Конфигурация");
      КонецПроцедуры
      """);
    Path linked = writeModule(modules, edt, "МодульБиблиотеки", library("Библиотека", "Служебный.Версия()"));
    writeModule(modules, edt, "Служебный", "Функция Версия() Возврат \"2.3\"; КонецФункции");
    writeModule(modules, edt, "Конфигурация", library("Приложение", "Метаданные.Версия"));
    writeModule(modules, edt, "НеЗарегистрирован", library("Лишняя", "\"3\""));

    assertThat(BspVersions.libraries(configuration, edt, "Приложение")).containsExactly(
      new BspVersions.Library("БСП", "1.0", bsp.toString()),
      new BspVersions.Library("Библиотека", "2.3", linked.toString()));
    assertThat(apply("bsp-library-version-set", "moduleFile", bsp, "4.5")).isZero();
    assertThat(Files.readString(bsp)).isEqualTo(library("БСП", "\"4.5\""));
    assertThat(BspVersions.libraries(configuration, edt, "Приложение").getFirst().version()).isEqualTo("4.5");
    assertThatThrownBy(() -> BspVersions.setLibraryVersion(linked, "5.0")).hasMessageContaining(linked.toString());
  }

  private int apply(String operation, String pathParam, Path file, String version) throws Exception {
    Path params = temporary.resolve("params.json");
    Files.writeString(params, new Gson().toJson(Map.of("op", operation, pathParam, file.toString(), "name", version)));
    return new CommandLine(new DesignerXmlCli()).execute("apply-mutation", "--params", params.toString());
  }

  private static Path writeModule(Path modules, boolean edt, String name, String source) throws Exception {
    Path module = modules.resolve(name).resolve(edt ? "Module.bsl" : "Ext/Module.bsl");
    Files.createDirectories(module.getParent());
    return Files.writeString(module, source);
  }

  private static String library(String name, String value) {
    return """
      Процедура ПриДобавленииПодсистемы(Описание) Экспорт
        Описание.Имя = "%s";
        Описание.Версия = %s;
      КонецПроцедуры
      """.formatted(name, value);
  }
}
