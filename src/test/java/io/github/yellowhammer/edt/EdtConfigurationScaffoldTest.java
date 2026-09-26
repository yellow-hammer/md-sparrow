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
package io.github.yellowhammer.edt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import io.github.yellowhammer.designerxml.SchemaVersion;
import io.github.yellowhammer.designerxml.cf.ConfigurationPropertiesDto;

/**
 * Новый проект конфигурации EDT.
 *
 * Образец - проекты, которые записала сама 1С:EDT 2026.1 при импорте выгрузки
 * платформы: голых объектов каждого формата (там значения семени) и
 * конфигурации, свойства которой платформа 8.5.1 заполнила сама, как у новой
 * конфигурации.
 */
class EdtConfigurationScaffoldTest {

  private static final Pattern UUID = Pattern.compile(
      "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
  private static final Pattern NAME = Pattern.compile("(?m)^  <name>([^<]*)</name>");
  private static final Pattern COMPATIBILITY = Pattern.compile("<compatibilityMode>[^<]*</compatibilityMode>");
  private static final Pattern RUN_MODE = Pattern.compile("(?m)^  <defaultRunMode>[^<]*</defaultRunMode>\\n");
  /** Ссылка на объект в составе конфигурации: {@code <catalogs>Catalog.Справочник1</catalogs>}. */
  private static final Pattern REFERENCE = Pattern.compile("^  <(\\w+)>(\\w+)\\.[^<]+</\\1>$");
  private static final String LANGUAGES_END = "  </languages>";
  static final String CONFIGURATION = "src/Configuration/Configuration.mdo";
  static final String MANIFEST = "DT-INF/PROJECT.PMF";

  /** Проекты, которые 1С:EDT записала при импорте выгрузки платформы. */
  static final Path IMPORTS = Path.of("src", "test", "resources", "edt-import").toAbsolutePath();

  private static EdtModel model;

  @TempDir
  Path workDir;

  @BeforeAll
  static void load() throws Exception {
    model = EdtModel.bundled();
    assertThat(IMPORTS.resolve("cf-bare-objects")).isDirectory();
  }

  /**
   * Проект, который EDT записала при импорте голых объектов формата.
   *
   * @param version формат выгрузки
   * @return каталог проекта
   */
  static Path bareImport(SchemaVersion version) {
    return IMPORTS.resolve("cf-bare-objects").resolve(version.metadataObjectVersionAttribute());
  }

  @ParameterizedTest
  @EnumSource(SchemaVersion.class)
  void проектКакИмпортEDTНовойКонфигурацииФормата(SchemaVersion version) throws Exception {
    Path reference = bareImport(version);
    Path project = workDir.resolve("Проект");

    Path mdo = EdtConfigurationScaffold.create(project, null, "Основа", null, null, null, version, model);

    assertThat(mdo).isEqualTo(project.toAbsolutePath().normalize().resolve(CONFIGURATION));
    assertThat(read(project.resolve(MANIFEST)))
        .contains("Runtime-Version: " + version.platformLine() + "\n")
        .isEqualTo(read(reference.resolve(MANIFEST)));
    String imported = withoutComposition(read(reference.resolve(CONFIGURATION)), Set.of());
    assertThat(masked(read(mdo))).isEqualTo(masked(newConfiguration(imported, version, "Основа")));
  }

  @Test
  void значенияНовойКонфигурацииКакУКонфигурацииОтПлатформы() throws Exception {
    // Режимы совместимости и назначение этой конфигурации заполнила платформа 8.5.1
    Path reference = IMPORTS.resolve("new-configuration-8.5.1");
    Path project = workDir.resolve("Проект");

    Path mdo = EdtConfigurationScaffold.create(project, null, "Основа", null, null, null, SchemaVersion.V2_21, model);

    assertThat(read(project.resolve(MANIFEST))).isEqualTo(read(reference.resolve(MANIFEST)));
    String expected = NAME.matcher(read(reference.resolve(CONFIGURATION))).replaceFirst("  <name>Основа</name>");
    // Язык у той конфигурации записан без синонима: сравниваются свойства до языков
    assertThat(masked(beforeLanguages(read(mdo)))).isEqualTo(masked(beforeLanguages(expected)));
  }

  @Test
  void имяПроектаИзПараметраИлиКаталога() throws Exception {
    Path named = workDir.resolve("Каталог");
    EdtConfigurationScaffold.create(named, "Основа", "Конфигурация", null, null, null, SchemaVersion.V2_20, model);
    Path unnamed = workDir.resolve("Основа2");
    EdtConfigurationScaffold.create(unnamed, " ", "Конфигурация", null, null, null, SchemaVersion.V2_20, model);

    String golden = EdtObjectScaffold.golden("Configuration/ЭталонСемя/.project");
    assertThat(read(named.resolve(".project"))).isEqualTo(normalizedEol(golden.replace("ЭталонСемя", "Основа")));
    assertThat(EdtExtensionScaffold.projectName(unnamed)).isEqualTo("Основа2");
    assertThat(EdtExtensionScaffold.projectName(named)).isEqualTo("Основа");
    assertThat(read(named.resolve(".settings/org.eclipse.core.resources.prefs"))).isEqualTo(
        normalizedEol(EdtObjectScaffold.golden("Configuration/ЭталонСемя/.settings/org.eclipse.core.resources.prefs")));
  }

  @ParameterizedTest
  @ValueSource(strings = {"Основа/Копия", "Основа:1", "Основа."})
  void имяПроектаСНедопустимымиСимволамиОтклоняется(String projectName) {
    Path project = workDir.resolve("Проект");

    assertThatThrownBy(() -> EdtConfigurationScaffold.create(
        project, projectName, "Основа", null, null, null, SchemaVersion.V2_21, model))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("имя проекта");
    assertThat(project).doesNotExist();
  }

  @Test
  void синонимПоставщикИВерсияПишутсяСвойствамиКонфигурации() throws Exception {
    Path mdo = EdtConfigurationScaffold.create(workDir.resolve("Проект"), null, "Торговля", "Управление торговлей",
        "ООО Ромашка", "1.0.1", SchemaVersion.V2_20, model);

    ConfigurationPropertiesDto dto = EdtConfigurationProperties.read(mdo, model);
    assertThat(dto.name).isEqualTo("Торговля");
    assertThat(dto.synonym).isEqualTo("Управление торговлей");
    assertThat(dto.vendor).isEqualTo("ООО Ромашка");
    assertThat(dto.version).isEqualTo("1.0.1");
    assertThat(dto.usePurposes).containsExactly("PERSONAL_COMPUTER");
    assertThat(read(mdo)).contains(
        "<compatibilityMode>8.3.27</compatibilityMode>",
        "<configurationExtensionCompatibilityMode>8.3.27</configurationExtensionCompatibilityMode>");
  }

  @Test
  void идентификаторыДетерминированыАНомераКлассовКакУEDT() throws Exception {
    Path first = EdtConfigurationScaffold.create(
        workDir.resolve("a").resolve("Основа"), null, "Основа", null, null, null, SchemaVersion.V2_21, model);
    Path same = EdtConfigurationScaffold.create(
        workDir.resolve("b").resolve("Основа"), null, "Основа", null, null, null, SchemaVersion.V2_21, model);
    Path other = EdtConfigurationScaffold.create(
        workDir.resolve("c").resolve("Другая"), null, "Основа", null, null, null, SchemaVersion.V2_21, model);

    assertThat(read(same)).isEqualTo(read(first));
    String golden = EdtObjectScaffold.golden("Configuration/ЭталонСемя/" + CONFIGURATION);
    Set<String> goldenIds = EdtExtensionScaffoldTest.objectIds(golden);
    assertThat(goldenIds).isNotEmpty();
    for (String uuid : EdtExtensionScaffoldTest.objectIds(read(first))) {
      assertThat(goldenIds).doesNotContain(uuid);
      assertThat(read(other)).doesNotContain(uuid);
    }
    // Номера классов хранимых данных у всех конфигураций одни: так их пишет EDT
    Path real = Path.of(System.getProperty("fixtures.ssl31edt.root"), "ssl31", CONFIGURATION);
    assertThat(EdtExtensionScaffoldTest.classIds(read(first)))
        .hasSize(7)
        .isEqualTo(EdtExtensionScaffoldTest.classIds(read(real)));
  }

  @Test
  void непустойКаталогИНеверноеИмяОтклоняются() throws Exception {
    Path busy = workDir.resolve("Занят");
    Files.createDirectories(busy);
    Files.writeString(busy.resolve("readme.txt"), "", StandardCharsets.UTF_8);

    assertThatThrownBy(() -> EdtConfigurationScaffold.create(
        busy, null, "Основа", null, null, null, SchemaVersion.V2_21, model))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("не пуст");
    assertThatThrownBy(() -> EdtConfigurationScaffold.create(
        workDir.resolve("Новый"), null, "1Основа", null, null, null, SchemaVersion.V2_21, model))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(workDir.resolve("Новый")).doesNotExist();
  }

  @Test
  void эталонСнятСФорматаНовейшейПлатформы() throws Exception {
    SchemaVersion[] all = SchemaVersion.values();
    assertThat(EdtConfigurationScaffold.canonicalVersion()).isEqualTo(all[all.length - 1]);
  }

  /**
   * Описание конфигурации из импорта EDT со значениями новой конфигурации.
   *
   * Импорт голых объектов несёт значения семени: имя, режим совместимости
   * 8.3.12 и пустое назначение. У новой конфигурации платформы режим
   * совместимости - её версия, а назначение - персональный компьютер;
   * остальное EDT пишет по формату выгрузки так же.
   *
   * @param imported описание конфигурации, записанное EDT
   * @param version формат выгрузки
   * @param name имя новой конфигурации
   * @return ожидаемое описание новой конфигурации
   */
  static String newConfiguration(String imported, SchemaVersion version, String name) {
    String text = NAME.matcher(imported).replaceFirst(Matcher.quoteReplacement("  <name>" + name + "</name>"));
    text = COMPATIBILITY.matcher(text)
        .replaceFirst("<compatibilityMode>" + version.platformLine() + "</compatibilityMode>");
    Matcher runMode = RUN_MODE.matcher(text);
    assertThat(runMode.find()).isTrue();
    return text.substring(0, runMode.end()) + "  <usePurposes>PersonalComputer</usePurposes>\n"
        + text.substring(runMode.end());
  }

  /**
   * Описание конфигурации без ссылок на объекты, кроме оставленных видов.
   *
   * @param text описание конфигурации с переводами строк {@code \n}
   * @param kept виды объектов, ссылки на которые остаются: {@code Catalog}
   * @return описание без прочих ссылок состава
   */
  static String withoutComposition(String text, Set<String> kept) {
    StringBuilder out = new StringBuilder();
    boolean afterLanguages = false;
    for (String line : text.split("\n", -1)) {
      Matcher reference = REFERENCE.matcher(line);
      if (afterLanguages && reference.matches() && !kept.contains(reference.group(2))) {
        continue;
      }
      out.append(line).append('\n');
      afterLanguages |= line.equals(LANGUAGES_END);
    }
    return out.substring(0, out.length() - 1);
  }

  /**
   * Виды объектов, на которые ссылается состав конфигурации.
   *
   * Ссылки состава идут за языками: до них ссылками записаны и свойства,
   * например язык по умолчанию.
   *
   * @param text описание конфигурации
   * @return вид объекта - имя первого объекта этого вида
   */
  static Map<String, String> composition(String text) {
    Map<String, String> kinds = new LinkedHashMap<>();
    boolean afterLanguages = false;
    for (String line : normalizedEol(text).split("\n")) {
      Matcher reference = REFERENCE.matcher(line);
      if (afterLanguages && reference.matches()) {
        String value = line.substring(line.indexOf('>') + 1, line.lastIndexOf('<'));
        kinds.putIfAbsent(reference.group(2), value.substring(value.indexOf('.') + 1));
      }
      afterLanguages |= line.equals(LANGUAGES_END);
    }
    return kinds;
  }

  /** Свойства конфигурации до её языков. */
  private static String beforeLanguages(String text) {
    int languages = text.indexOf("  <languages");
    assertThat(languages).isPositive();
    return text.substring(0, languages);
  }

  /**
   * Идентификаторы по порядку заменены метками; номера классов остаются как есть.
   *
   * @param text текст файла
   * @return текст с метками вместо идентификаторов
   */
  static String masked(String text) {
    Map<String, String> seen = new LinkedHashMap<>();
    Matcher matcher = UUID.matcher(text);
    StringBuilder out = new StringBuilder();
    while (matcher.find()) {
      String uuid = matcher.group();
      String replacement = text.startsWith("classId=\"", matcher.start() - "classId=\"".length())
          ? uuid
          : seen.computeIfAbsent(uuid, key -> "uuid-" + seen.size());
      matcher.appendReplacement(out, Matcher.quoteReplacement(replacement));
    }
    matcher.appendTail(out);
    return out.toString();
  }

  /**
   * Текст файла с переводами строк {@code \n}.
   *
   * @param file файл
   * @return текст
   * @throws IOException если файл не читается
   */
  static String read(Path file) throws IOException {
    return normalizedEol(Files.readString(file, StandardCharsets.UTF_8));
  }

  private static String normalizedEol(String text) {
    return text.replace("\r\n", "\n");
  }
}
