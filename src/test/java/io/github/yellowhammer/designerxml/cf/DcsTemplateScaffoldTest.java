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

import io.github.yellowhammer.designerxml.DesignerXml;
import io.github.yellowhammer.designerxml.SchemaVersion;
import io.github.yellowhammer.designerxml.XmlValidator;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Создание и удаление макета СКД ({@link DcsTemplateScaffold}): в каждом формате результат совпадает
 * с выгрузкой платформы (эталон cf-dcs) байт в байт, кроме UUID нового описания. Удаление возвращает
 * владельца к выгрузке до макета; повтор операций даёт те же байты.
 */
class DcsTemplateScaffoldTest {

  private static final String DCS = "cf-dcs";

  private static final String TEMPLATE_NAME = "ОсновнаяСхемаКомпоновкиДанных";

  private static final String COMMON_NAME = "ОбщийМакет1";

  private static final String REPORT_XML = "Reports/Отчет1.xml";

  private static final String TEMPLATE_DESCRIPTOR = "Reports/Отчет1/Templates/" + TEMPLATE_NAME + ".xml";

  private static final String TEMPLATE_CONTENT = "Reports/Отчет1/Templates/" + TEMPLATE_NAME + "/Ext/Template.xml";

  private static final String COMMON_DESCRIPTOR = "CommonTemplates/" + COMMON_NAME + ".xml";

  private static final String COMMON_CONTENT = "CommonTemplates/" + COMMON_NAME + "/Ext/Template.xml";

  /** XSD схемы компоновки в наборе формата: без неё у формата нет модели СКД. */
  private static final String DCS_SCHEMA_XSD = "v8.1c.ru-8.1-data-composition-system-schema.xsd";

  @TempDir
  Path workspace;

  // --- все форматы: байт в байт с выгрузкой платформы формата ---------------------------------

  @ParameterizedTest
  @EnumSource(SchemaVersion.class)
  void макетыСовпадаютСВыгрузкойПлатформыСвоегоФормата(SchemaVersion version) throws Exception {
    Path owner = copy(GoldenSnapshots.format(version).resolve(GoldenSnapshots.CF).resolve(REPORT_XML),
      workspace.resolve(REPORT_XML));
    Path configuration = configurationWithoutCommonTemplate(version);

    DcsTemplateScaffold.add(owner, version, TEMPLATE_NAME, true);
    DcsTemplateScaffold.add(configuration, version, COMMON_NAME, false);

    assertThat(GoldenSnapshots.read(owner)).isEqualTo(dcsGolden(version, REPORT_XML));
    assertThat(GoldenSnapshots.read(configuration)).isEqualTo(dcsGolden(version, CfLayout.CONFIGURATION_XML));
    // Описания сверяются без UUID: у платформы они случайные
    for (String descriptor : List.of(TEMPLATE_DESCRIPTOR, COMMON_DESCRIPTOR)) {
      Path file = workspace.resolve(descriptor);
      assertThat(GoldenSnapshots.normalizeUuids(GoldenSnapshots.read(file))).as("описание %s", descriptor)
        .isEqualTo(GoldenSnapshots.normalizeUuids(dcsGolden(version, descriptor)));
      assertThat(DesignerXml.read(file, version)).as("модель формата: %s", descriptor).isNotNull();
      XmlValidator.validate(file, version, Path.of(System.getProperty("xsd.root")));
    }
    // Содержимое у каждого формата своё: у 2.21 свои объявления настроек варианта
    for (String content : List.of(TEMPLATE_CONTENT, COMMON_CONTENT)) {
      assertThat(Files.readAllBytes(workspace.resolve(content))).as("содержимое %s", content)
        .isEqualTo(Files.readAllBytes(dcsPath(version, content)));
    }
    assertThat(MdObjectStructureRead.read(owner, version).templates)
      .extracting(template -> template.name).containsExactly(TEMPLATE_NAME);
  }

  @ParameterizedTest
  @EnumSource(SchemaVersion.class)
  void проверкаМодельюФорматаБезXsdСкдОтказываетАСтрогаяПроходит(SchemaVersion version) throws Exception {
    Path owner = copy(GoldenSnapshots.format(version).resolve(GoldenSnapshots.CF).resolve(REPORT_XML),
      workspace.resolve(REPORT_XML));
    DcsTemplateScaffold.add(owner, version, TEMPLATE_NAME, true);
    String content = workspace.resolve(TEMPLATE_CONTENT).toString();

    Map<String, Object> read = readJson(Map.of(
      "op", "cf-dcs-validate", "objectXml", content, "schemaVersion", version.name()));
    Path xsd = Path.of(System.getProperty("xsd.root"), "schemas", "designer",
      version.metadataObjectVersionAttribute(), DCS_SCHEMA_XSD);
    if (Files.isRegularFile(xsd)) {
      assertThat(read).containsEntry("valid", true);
    } else {
      // Создание набора XSD СКД не требует, а проверка моделью формата без него невозможна
      assertThat(read).containsEntry("valid", false);
      assertThat(String.valueOf(read.get("error")))
        .contains("в формате " + version.metadataObjectVersionAttribute())
        .contains("нет полного набора схем СКД");
    }
    // Строгая проверка идёт по XSD самого нового формата
    Map<String, Object> strict = readJson(Map.of("op", "cf-dcs-validate", "objectXml", content, "strict", true));
    assertThat(strict).containsEntry("ok", true);
  }

  // --- удаление --------------------------------------------------------------------------------

  @ParameterizedTest
  @EnumSource(SchemaVersion.class)
  void удалениеВозвращаетОтчётКВыгрузкеДоМакетаИСбрасываетОсновнуюСхему(SchemaVersion version) throws Exception {
    Path owner = copyDcsReport(version);

    DcsTemplateScaffold.remove(owner, version, TEMPLATE_NAME);

    // Голый отчёт эталона: тот же объект до создания макета
    assertThat(GoldenSnapshots.read(owner))
      .isEqualTo(GoldenSnapshots.read(GoldenSnapshots.format(version).resolve(GoldenSnapshots.CF).resolve(REPORT_XML)));
    Path templates = workspace.resolve("Reports").resolve("Отчет1").resolve("Templates");
    assertThat(Files.exists(templates.resolve(TEMPLATE_NAME + ".xml"))).isFalse();
    assertThat(Files.exists(templates.resolve(TEMPLATE_NAME))).isFalse();
  }

  @Test
  void удалениеОбщегоМакетаУбираетЗаписьФайлИКаталог() throws Exception {
    SchemaVersion canonical = GoldenSnapshots.canonical();
    Path configuration = copy(dcsPath(canonical, CfLayout.CONFIGURATION_XML),
      workspace.resolve(CfLayout.CONFIGURATION_XML));
    copy(dcsPath(canonical, COMMON_DESCRIPTOR), workspace.resolve(COMMON_DESCRIPTOR));
    copy(dcsPath(canonical, COMMON_CONTENT), workspace.resolve(COMMON_CONTENT));
    String withoutEntry = withoutCommonTemplateLine(GoldenSnapshots.read(configuration));

    DcsTemplateScaffold.remove(configuration, canonical, COMMON_NAME);

    assertThat(GoldenSnapshots.read(configuration)).isEqualTo(withoutEntry);
    assertThat(Files.exists(workspace.resolve(COMMON_DESCRIPTOR))).isFalse();
    assertThat(Files.exists(workspace.resolve("CommonTemplates").resolve(COMMON_NAME))).isFalse();
  }

  @Test
  void повторДобавленияПослеУдаленияДаётТеЖеБайты() throws Exception {
    SchemaVersion canonical = GoldenSnapshots.canonical();
    Path owner = copy(GoldenSnapshots.format(canonical).resolve(GoldenSnapshots.CF).resolve(REPORT_XML),
      workspace.resolve(REPORT_XML));
    Path descriptor = workspace.resolve(TEMPLATE_DESCRIPTOR);

    DcsTemplateScaffold.add(owner, canonical, TEMPLATE_NAME, true);
    byte[] ownerFirst = Files.readAllBytes(owner);
    byte[] descriptorFirst = Files.readAllBytes(descriptor);
    DcsTemplateScaffold.remove(owner, canonical, TEMPLATE_NAME);
    DcsTemplateScaffold.add(owner, canonical, TEMPLATE_NAME, true);

    // UUID выводятся из владельца до правки, удаление возвращает его байты
    assertThat(Files.readAllBytes(owner)).isEqualTo(ownerFirst);
    assertThat(Files.readAllBytes(descriptor)).isEqualTo(descriptorFirst);
  }

  // --- отказы ----------------------------------------------------------------------------------

  @Test
  void занятоеИмяИМакетДругогоТипаПолучаютОтказ() throws Exception {
    SchemaVersion canonical = GoldenSnapshots.canonical();
    Path owner = copyDcsReport(canonical);
    assertThatThrownBy(() -> DcsTemplateScaffold.add(owner, canonical, TEMPLATE_NAME, false))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("уже существует");

    assertThatThrownBy(() -> DcsTemplateScaffold.remove(owner, canonical, "НетТакого"))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("Макет не найден");

    // Макет другого вида: у голого общего макета эталона TemplateType - SpreadsheetDocument
    Path configuration = copy(dcsPath(canonical, CfLayout.CONFIGURATION_XML),
      workspace.resolve(CfLayout.CONFIGURATION_XML));
    copy(GoldenSnapshots.format(canonical).resolve(GoldenSnapshots.CF).resolve(COMMON_DESCRIPTOR),
      workspace.resolve(COMMON_DESCRIPTOR));
    assertThatThrownBy(() -> DcsTemplateScaffold.remove(configuration, canonical, COMMON_NAME))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("не схема компоновки");
  }

  @Test
  void видБезМакетовИНедоступныйMakeMainПолучаютОтказ() throws Exception {
    SchemaVersion canonical = GoldenSnapshots.canonical();
    Path language = copy(dcsPath(canonical, "Languages/Русский.xml"),
      workspace.resolve("Languages").resolve("Русский.xml"));
    assertThatThrownBy(() -> DcsTemplateScaffold.add(language, canonical, "Макет", false))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("нет подчинённых Template");

    Path catalog = copy(GoldenSnapshots.format(canonical).resolve(GoldenSnapshots.CF)
        .resolve("Catalogs").resolve("Справочник1.xml"),
      workspace.resolve("Catalogs").resolve("Справочник1.xml"));
    assertThatThrownBy(() -> DcsTemplateScaffold.add(catalog, canonical, "Макет", true))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("нет свойства MainDataCompositionSchema");

    Path configuration = configurationWithoutCommonTemplate(canonical);
    assertThatThrownBy(() -> DcsTemplateScaffold.add(configuration, canonical, "Макет", true))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("нет свойства MainDataCompositionSchema");
  }

  // --- канал apply-mutation ---------------------------------------------------------------------

  @Test
  void каналApplyMutationСоздаётИУдаляетМакет() throws Exception {
    SchemaVersion canonical = GoldenSnapshots.canonical();
    Path owner = copy(GoldenSnapshots.format(canonical).resolve(GoldenSnapshots.CF).resolve(REPORT_XML),
      workspace.resolve(REPORT_XML));
    String bare = GoldenSnapshots.read(owner);

    assertThat(applyMutation(Map.of(
      "op", "cf-dcs-template-add", "objectXml", owner.toString(), "schemaVersion", canonical.name(),
      "name", TEMPLATE_NAME, "makeMain", true))).isZero();
    assertThat(GoldenSnapshots.read(owner)).isEqualTo(dcsGolden(canonical, REPORT_XML));

    assertThat(applyMutation(Map.of(
      "op", "cf-dcs-template-remove", "objectXml", owner.toString(), "schemaVersion", canonical.name(),
      "name", TEMPLATE_NAME))).isZero();
    assertThat(GoldenSnapshots.read(owner)).isEqualTo(bare);

    // Отказ канала: удалять больше нечего, файл не меняется
    assertThat(applyMutation(Map.of(
      "op", "cf-dcs-template-remove", "objectXml", owner.toString(), "schemaVersion", canonical.name(),
      "name", TEMPLATE_NAME))).isEqualTo(2);
    assertThat(GoldenSnapshots.read(owner)).isEqualTo(bare);
  }

  /** Запуск канала apply-mutation с параметрами из UTF-8 JSON-файла. */
  private int applyMutation(Map<String, ?> params) throws Exception {
    Path paramsFile = Files.createTempFile(workspace, "params", ".json");
    Files.writeString(paramsFile, new com.google.gson.Gson().toJson(params), StandardCharsets.UTF_8);
    return new picocli.CommandLine(new io.github.yellowhammer.designerxml.cli.DesignerXmlCli())
      .execute("apply-mutation", "--params", paramsFile.toString());
  }

  /** Запуск канала read-json: ответ stdout разобран как JSON-объект. */
  @SuppressWarnings("unchecked")
  private Map<String, Object> readJson(Map<String, ?> params) throws Exception {
    Path paramsFile = Files.createTempFile(workspace, "params", ".json");
    Files.writeString(paramsFile, new com.google.gson.Gson().toJson(params), StandardCharsets.UTF_8);
    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    PrintStream previous = System.out;
    int exit;
    try {
      System.setOut(new PrintStream(buffer, true, StandardCharsets.UTF_8));
      exit = new picocli.CommandLine(new io.github.yellowhammer.designerxml.cli.DesignerXmlCli())
        .execute("read-json", "--params", paramsFile.toString());
    } finally {
      System.setOut(previous);
    }
    assertThat(exit).isZero();
    return new com.google.gson.Gson().fromJson(buffer.toString(StandardCharsets.UTF_8), Map.class);
  }

  // --- вспомогательное -------------------------------------------------------------------------

  /** Выгрузка cf-dcs формата: отчёт с макетом и общий макет, снятые платформой. */
  private static Path dcsPath(SchemaVersion version, String relative) {
    return GoldenSnapshots.format(version).resolve(DCS).resolve(relative);
  }

  private static String dcsGolden(SchemaVersion version, String relative) {
    return GoldenSnapshots.read(dcsPath(version, relative));
  }

  /** Копия отчёта эталона cf-dcs с макетом и его файлами. */
  private Path copyDcsReport(SchemaVersion version) throws Exception {
    Path owner = copy(dcsPath(version, REPORT_XML), workspace.resolve(REPORT_XML));
    copy(dcsPath(version, TEMPLATE_DESCRIPTOR), workspace.resolve(TEMPLATE_DESCRIPTOR));
    copy(dcsPath(version, TEMPLATE_CONTENT), workspace.resolve(TEMPLATE_CONTENT));
    return owner;
  }

  /** Конфигурация эталона cf-dcs без записи об общем макете: место для добавления. */
  private Path configurationWithoutCommonTemplate(SchemaVersion version) throws Exception {
    Path configuration = workspace.resolve(CfLayout.CONFIGURATION_XML);
    Files.writeString(configuration, withoutCommonTemplateLine(dcsGolden(version, CfLayout.CONFIGURATION_XML)),
      StandardCharsets.UTF_8);
    return configuration;
  }

  /** Текст конфигурации без строки {@code <CommonTemplate>ОбщийМакет1</CommonTemplate>}. */
  private static String withoutCommonTemplateLine(String configuration) {
    List<XmlLines.Node> entries =
      XmlLines.children(configuration, List.of("MetaDataObject", "Configuration", "ChildObjects"));
    XmlLines.Node entry = entries.stream()
      .filter(node -> "CommonTemplate".equals(node.name()))
      .findFirst()
      .orElseThrow();
    int[] line = XmlLines.wholeLines(configuration, entry.start(), entry.end(), "CommonTemplate");
    return configuration.substring(0, line[0]) + configuration.substring(line[1]);
  }

  private static Path copy(Path source, Path target) throws Exception {
    Files.createDirectories(target.getParent());
    Files.copy(source, target);
    return target;
  }
}
