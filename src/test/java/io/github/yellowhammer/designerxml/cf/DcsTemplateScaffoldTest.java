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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Создание и удаление макета СКД «как конфигуратор» ({@link DcsTemplateScaffold}): на каноническом
 * формате 2.20 результат совпадает с выгрузкой платформы (эталон cf-dcs) байт в байт, описания для
 * 2.12-2.19 читаются моделью формата и проходят XSD, форматы без эталона получают отказ. Удаление
 * возвращает владельца к выгрузке до макета и сбрасывает основную схему; повтор операций даёт те
 * же байты.
 */
class DcsTemplateScaffoldTest {

  private static final String DCS = "cf-dcs";

  private static final String TEMPLATE_NAME = "ОсновнаяСхемаКомпоновкиДанных";

  private static final String COMMON_NAME = "ОбщийМакет1";

  private static final String REPORT_XML = "Reports/Отчет1.xml";

  private static final SchemaVersion CANONICAL = SchemaVersion.V2_20;

  @TempDir
  Path workspace;

  // --- канонический формат: байт в байт с выгрузкой платформы ---------------------------------

  @Test
  void отчётПослеДобавленияМакетаСовпадаетСВыгрузкойПлатформы() throws Exception {
    Path owner = copy(GoldenSnapshots.format(CANONICAL).resolve(GoldenSnapshots.CF).resolve(REPORT_XML),
      workspace.resolve(REPORT_XML));

    DcsTemplateScaffold.add(owner, CANONICAL, TEMPLATE_NAME, true);

    // Владелец совпадает целиком: запись в составе, заполненная основная схема, байтовая гигиена
    assertThat(GoldenSnapshots.read(owner)).isEqualTo(dcsGolden(REPORT_XML));
    Path templates = workspace.resolve("Reports").resolve("Отчет1").resolve("Templates");
    assertThat(GoldenSnapshots.normalizeUuids(GoldenSnapshots.read(templates.resolve(TEMPLATE_NAME + ".xml"))))
      .isEqualTo(GoldenSnapshots.normalizeUuids(dcsGolden("Reports/Отчет1/Templates/" + TEMPLATE_NAME + ".xml")));
    assertThat(Files.readAllBytes(templates.resolve(TEMPLATE_NAME).resolve("Ext").resolve("Template.xml")))
      .isEqualTo(dcsGoldenBytes("Reports/Отчет1/Templates/" + TEMPLATE_NAME + "/Ext/Template.xml"));
  }

  @Test
  void общийМакетПослеДобавленияСовпадаетСВыгрузкойПлатформы() throws Exception {
    Path configuration = configurationWithoutCommonTemplate();

    DcsTemplateScaffold.add(configuration, CANONICAL, COMMON_NAME, false);

    assertThat(GoldenSnapshots.read(configuration)).isEqualTo(dcsGolden(CfLayout.CONFIGURATION_XML));
    Path commons = workspace.resolve("CommonTemplates");
    assertThat(GoldenSnapshots.normalizeUuids(GoldenSnapshots.read(commons.resolve(COMMON_NAME + ".xml"))))
      .isEqualTo(GoldenSnapshots.normalizeUuids(dcsGolden("CommonTemplates/" + COMMON_NAME + ".xml")));
    assertThat(Files.readAllBytes(commons.resolve(COMMON_NAME).resolve("Ext").resolve("Template.xml")))
      .isEqualTo(dcsGoldenBytes("CommonTemplates/" + COMMON_NAME + "/Ext/Template.xml"));
  }

  // --- удаление --------------------------------------------------------------------------------

  @Test
  void удалениеВозвращаетОтчётКВыгрузкеДоМакетаИСбрасываетОсновнуюСхему() throws Exception {
    Path owner = copyDcsReport();

    DcsTemplateScaffold.remove(owner, CANONICAL, TEMPLATE_NAME);

    // Голый отчёт эталона - тот же объект до создания макета: пустая основная схема, <ChildObjects/>
    assertThat(GoldenSnapshots.read(owner))
      .isEqualTo(GoldenSnapshots.read(GoldenSnapshots.format(CANONICAL).resolve(GoldenSnapshots.CF).resolve(REPORT_XML)));
    Path templates = workspace.resolve("Reports").resolve("Отчет1").resolve("Templates");
    assertThat(Files.exists(templates.resolve(TEMPLATE_NAME + ".xml"))).isFalse();
    assertThat(Files.exists(templates.resolve(TEMPLATE_NAME))).isFalse();
  }

  @Test
  void удалениеОбщегоМакетаУбираетЗаписьФайлИКаталог() throws Exception {
    Path configuration = copy(dcsPath(CfLayout.CONFIGURATION_XML), workspace.resolve(CfLayout.CONFIGURATION_XML));
    copy(dcsPath("CommonTemplates/" + COMMON_NAME + ".xml"),
      workspace.resolve("CommonTemplates").resolve(COMMON_NAME + ".xml"));
    copy(dcsPath("CommonTemplates/" + COMMON_NAME + "/Ext/Template.xml"),
      workspace.resolve("CommonTemplates").resolve(COMMON_NAME).resolve("Ext").resolve("Template.xml"));
    String withoutEntry = withoutCommonTemplateLine(GoldenSnapshots.read(configuration));

    DcsTemplateScaffold.remove(configuration, CANONICAL, COMMON_NAME);

    assertThat(GoldenSnapshots.read(configuration)).isEqualTo(withoutEntry);
    assertThat(Files.exists(workspace.resolve("CommonTemplates").resolve(COMMON_NAME + ".xml"))).isFalse();
    assertThat(Files.exists(workspace.resolve("CommonTemplates").resolve(COMMON_NAME))).isFalse();
  }

  @Test
  void повторДобавленияПослеУдаленияДаётТеЖеБайты() throws Exception {
    Path owner = copy(GoldenSnapshots.format(CANONICAL).resolve(GoldenSnapshots.CF).resolve(REPORT_XML),
      workspace.resolve(REPORT_XML));
    Path descriptor = workspace.resolve("Reports").resolve("Отчет1").resolve("Templates")
      .resolve(TEMPLATE_NAME + ".xml");

    DcsTemplateScaffold.add(owner, CANONICAL, TEMPLATE_NAME, true);
    byte[] ownerFirst = Files.readAllBytes(owner);
    byte[] descriptorFirst = Files.readAllBytes(descriptor);
    DcsTemplateScaffold.remove(owner, CANONICAL, TEMPLATE_NAME);
    DcsTemplateScaffold.add(owner, CANONICAL, TEMPLATE_NAME, true);

    // UUID выводятся из владельца до правки, а удаление возвращает его байты: повтор неотличим
    assertThat(Files.readAllBytes(owner)).isEqualTo(ownerFirst);
    assertThat(Files.readAllBytes(descriptor)).isEqualTo(descriptorFirst);
  }

  // --- отказы ----------------------------------------------------------------------------------

  @Test
  void занятоеИмяИМакетДругогоТипаПолучаютОтказ() throws Exception {
    Path owner = copyDcsReport();
    assertThatThrownBy(() -> DcsTemplateScaffold.add(owner, CANONICAL, TEMPLATE_NAME, false))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("уже существует");

    assertThatThrownBy(() -> DcsTemplateScaffold.remove(owner, CANONICAL, "НетТакого"))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("Макет не найден");

    // Макет другого вида: у голого общего макета эталона TemplateType - SpreadsheetDocument
    Path configuration = copy(dcsPath(CfLayout.CONFIGURATION_XML), workspace.resolve(CfLayout.CONFIGURATION_XML));
    copy(GoldenSnapshots.format(CANONICAL).resolve(GoldenSnapshots.CF)
        .resolve("CommonTemplates").resolve(COMMON_NAME + ".xml"),
      workspace.resolve("CommonTemplates").resolve(COMMON_NAME + ".xml"));
    assertThatThrownBy(() -> DcsTemplateScaffold.remove(configuration, CANONICAL, COMMON_NAME))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("не схема компоновки");
  }

  @Test
  void видБезМакетовИНедоступныйMakeMainПолучаютОтказ() throws Exception {
    Path language = copy(dcsPath("Languages/Русский.xml"), workspace.resolve("Languages").resolve("Русский.xml"));
    assertThatThrownBy(() -> DcsTemplateScaffold.add(language, CANONICAL, "Макет", false))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("нет подчинённых Template");

    Path catalog = copy(GoldenSnapshots.format(CANONICAL).resolve(GoldenSnapshots.CF)
        .resolve("Catalogs").resolve("Справочник1.xml"),
      workspace.resolve("Catalogs").resolve("Справочник1.xml"));
    assertThatThrownBy(() -> DcsTemplateScaffold.add(catalog, CANONICAL, "Макет", true))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("нет свойства MainDataCompositionSchema");

    Path configuration = configurationWithoutCommonTemplate();
    assertThatThrownBy(() -> DcsTemplateScaffold.add(configuration, CANONICAL, "Макет", true))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("нет свойства MainDataCompositionSchema");
  }

  // --- все форматы: проекция канона либо отказ -------------------------------------------------

  @ParameterizedTest
  @EnumSource(SchemaVersion.class)
  void форматПолучаетПроекциюКанонаЛибоОтказ(SchemaVersion version) throws Exception {
    Path cf = workspace.resolve(version.name());
    EmptyCfScaffold.writeEmptyTree(cf, CfLayout.DEFAULT_CONFIGURATION_NAME, null, null, null, version);
    MdObjectAdd.add(cf.resolve(CfLayout.CONFIGURATION_XML), "Отчет1", version, MdObjectAddType.REPORT);
    Path owner = CfLayout.objectXmlInSubdir(cf, MdObjectAddType.REPORT.cfSubdir(), "Отчет1");

    if (version.compareTo(SchemaVersion.V2_12) < 0) {
      assertThatThrownBy(() -> DcsTemplateScaffold.add(owner, version, TEMPLATE_NAME, true))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("нет полного набора XSD СКД");
      return;
    }
    if (version.compareTo(CANONICAL) > 0) {
      assertThatThrownBy(() -> DcsTemplateScaffold.add(owner, version, TEMPLATE_NAME, true))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Эталон cf-dcs не снят для формата " + version.metadataObjectVersionAttribute());
      return;
    }

    DcsTemplateScaffold.add(owner, version, TEMPLATE_NAME, true);
    DcsTemplateScaffold.add(cf.resolve(CfLayout.CONFIGURATION_XML), version, COMMON_NAME, false);

    // Описания читаются моделью формата и проходят XSD формата; содержимое - XSD СКД
    Path descriptor = owner.resolveSibling("Отчет1").resolve("Templates").resolve(TEMPLATE_NAME + ".xml");
    Path common = cf.resolve("CommonTemplates").resolve(COMMON_NAME + ".xml");
    for (Path file : List.of(descriptor, common)) {
      assertThat(DesignerXml.read(file, version)).as("модель формата %s: %s", version, file).isNotNull();
      XmlValidator.validate(file, version, Path.of(System.getProperty("xsd.root")));
      String text = GoldenSnapshots.read(file);
      assertThat(text).as("BOM %s", file).startsWith("﻿");
      assertThat(text.replace("\r\n", "")).as("CRLF %s", file).doesNotContain("\n");
      assertThat(text).contains("version=\"" + version.metadataObjectVersionAttribute() + "\"");
    }
    MdObjectStructureDto structure = MdObjectStructureRead.read(owner, version);
    assertThat(structure.templates).extracting(template -> template.name).containsExactly(TEMPLATE_NAME);
    for (Path content : List.of(
      descriptor.resolveSibling(TEMPLATE_NAME).resolve("Ext").resolve("Template.xml"),
      common.resolveSibling(COMMON_NAME).resolve("Ext").resolve("Template.xml"))) {
      DcsRead.verify(GoldenSnapshots.read(content), version);
      Map<String, Object> verdict = DcsXsdValidation.validate(content);
      assertThat(verdict.get("ok")).as("XSD СКД %s", content).isEqualTo(Boolean.TRUE);
    }
    // Основная схема заполнена по месту свойства в sequence
    assertThat(GoldenSnapshots.read(owner))
      .contains("<MainDataCompositionSchema>Report.Отчет1.Template." + TEMPLATE_NAME + "</MainDataCompositionSchema>");
  }

  // --- канал apply-mutation ---------------------------------------------------------------------

  @Test
  void каналApplyMutationСоздаётИУдаляетМакет() throws Exception {
    Path owner = copy(GoldenSnapshots.format(CANONICAL).resolve(GoldenSnapshots.CF).resolve(REPORT_XML),
      workspace.resolve(REPORT_XML));
    String bare = GoldenSnapshots.read(owner);

    assertThat(applyMutation(Map.of(
      "op", "cf-dcs-template-add", "objectXml", owner.toString(), "schemaVersion", "V2_20",
      "name", TEMPLATE_NAME, "makeMain", true))).isZero();
    assertThat(GoldenSnapshots.read(owner)).isEqualTo(dcsGolden(REPORT_XML));

    assertThat(applyMutation(Map.of(
      "op", "cf-dcs-template-remove", "objectXml", owner.toString(), "schemaVersion", "V2_20",
      "name", TEMPLATE_NAME))).isZero();
    assertThat(GoldenSnapshots.read(owner)).isEqualTo(bare);

    // Отказ канала: удалять больше нечего, файл не меняется
    assertThat(applyMutation(Map.of(
      "op", "cf-dcs-template-remove", "objectXml", owner.toString(), "schemaVersion", "V2_20",
      "name", TEMPLATE_NAME))).isEqualTo(2);
    assertThat(GoldenSnapshots.read(owner)).isEqualTo(bare);
  }

  /** Запуск канала apply-mutation с параметрами из UTF-8 JSON-файла. */
  private int applyMutation(Map<String, ?> params) throws Exception {
    Path paramsFile = Files.createTempFile(workspace, "params", ".json");
    Files.writeString(paramsFile, new com.google.gson.Gson().toJson(params),
      java.nio.charset.StandardCharsets.UTF_8);
    return new picocli.CommandLine(new io.github.yellowhammer.designerxml.cli.DesignerXmlCli())
      .execute("apply-mutation", "--params", paramsFile.toString());
  }

  // --- вспомогательное -------------------------------------------------------------------------

  /** Выгрузка cf-dcs: отчёт с макетом и общий макет, снятые платформой. */
  private static Path dcsPath(String relative) {
    return GoldenSnapshots.format(CANONICAL).resolve(DCS).resolve(relative);
  }

  private static String dcsGolden(String relative) {
    return GoldenSnapshots.read(dcsPath(relative));
  }

  private static byte[] dcsGoldenBytes(String relative) throws Exception {
    return Files.readAllBytes(dcsPath(relative));
  }

  /** Копия отчёта эталона cf-dcs с макетом и его файлами. */
  private Path copyDcsReport() throws Exception {
    Path owner = copy(dcsPath(REPORT_XML), workspace.resolve(REPORT_XML));
    copy(dcsPath("Reports/Отчет1/Templates/" + TEMPLATE_NAME + ".xml"),
      workspace.resolve("Reports").resolve("Отчет1").resolve("Templates").resolve(TEMPLATE_NAME + ".xml"));
    copy(dcsPath("Reports/Отчет1/Templates/" + TEMPLATE_NAME + "/Ext/Template.xml"),
      workspace.resolve("Reports").resolve("Отчет1").resolve("Templates").resolve(TEMPLATE_NAME)
        .resolve("Ext").resolve("Template.xml"));
    return owner;
  }

  /** Конфигурация эталона cf-dcs без записи об общем макете: место для добавления. */
  private Path configurationWithoutCommonTemplate() throws Exception {
    Path configuration = workspace.resolve(CfLayout.CONFIGURATION_XML);
    Files.writeString(configuration, withoutCommonTemplateLine(dcsGolden(CfLayout.CONFIGURATION_XML)));
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
