/*
 * This file is a part of md-sparrow.
 *
 * Copyright (c) 2026
 * Ivan Karlo <i.karlo@outlook.com> and contributors
 *
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package io.github.yellowhammer.designerxml.cli;

import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Проверяет канал {@code apply-mutation}: кириллические значения приходят из UTF-8 JSON без искажения
 * (в отличие от {@code argv}, который на Windows зависит от кодовой страницы ОС).
 */
class ApplyMutationCmdTest {

  private static final String CYRILLIC_NAME = "КириллическийРеквизитИзJson";

  @Test
  void addAttribute_viaParamsFile_keepsCyrillicName() throws Exception {
    Path objectXml = copyToTemp(sampleDocumentXml());
    Path params = writeParams(
      "{"
        + "\"op\":\"cf-md-attribute-add\","
        + "\"objectXml\":" + json(objectXml.toString()) + ","
        + "\"name\":" + json(CYRILLIC_NAME) + ","
        + "\"schemaVersion\":\"V2_20\""
        + "}");

    int exit = new CommandLine(new DesignerXmlCli()).execute("apply-mutation", "--params", params.toString());

    assertThat(exit).isZero();
    assertThat(Files.readString(objectXml, StandardCharsets.UTF_8)).contains(CYRILLIC_NAME);
  }

  @Test
  void objectSet_viaParamsFile_updatesCatalogScalarsGranularly() throws Exception {
    // Паттерн панели свойств: прочитать полный DTO, изменить поля, записать целиком.
    Path objectXml = copyToTemp(sampleCatalogXml());
    String before = Files.readString(objectXml, StandardCharsets.UTF_8);
    io.github.yellowhammer.designerxml.cf.MdObjectPropertiesDto dto =
      io.github.yellowhammer.designerxml.cf.MdObjectPropertiesEdit.readDto(
        objectXml, io.github.yellowhammer.designerxml.SchemaVersion.V2_20);
    String newSynonym = "Демо: Банковские счета (изменено)";
    dto.synonymRu = newSynonym;
    dto.catalog.choiceMode = "QUICK_CHOICE";
    Path params = writeParams(
      "{"
        + "\"op\":\"cf-md-object-set\","
        + "\"objectXml\":" + json(objectXml.toString()) + ","
        + "\"schemaVersion\":\"V2_20\","
        + "\"payloadJson\":" + json(new com.google.gson.Gson().toJson(dto))
        + "}");

    int exit = new CommandLine(new DesignerXmlCli()).execute("apply-mutation", "--params", params.toString());

    assertThat(exit).isZero();
    String after = Files.readString(objectXml, StandardCharsets.UTF_8);
    assertThat(after).contains(newSynonym);
    assertThat(after).contains("<ChoiceMode>QuickChoice</ChoiceMode>");
    // Гранулярность: незатронутые узлы не переписаны, владельцы сохранены.
    assertThat(after).contains("Catalog._ДемоОрганизации");
    assertThat(countLines(after)).isEqualTo(countLines(before));
  }

  @Test
  void objectSet_ownerAndInputByStringLists_writeGranularly() throws Exception {
    Path objectXml = copyToTemp(sampleCatalogXml());
    String before = Files.readString(objectXml, StandardCharsets.UTF_8);
    io.github.yellowhammer.designerxml.cf.MdObjectPropertiesDto dto =
      io.github.yellowhammer.designerxml.cf.MdObjectPropertiesEdit.readDto(
        objectXml, io.github.yellowhammer.designerxml.SchemaVersion.V2_20);
    dto.catalog.owners = java.util.List.of("Catalog._ДемоОрганизации");
    dto.catalog.inputByString = java.util.List.of("Catalog._ДемоБанковскиеСчета.StandardAttribute.Code");
    Path params = writeParams(
      "{"
        + "\"op\":\"cf-md-object-set\","
        + "\"objectXml\":" + json(objectXml.toString()) + ","
        + "\"schemaVersion\":\"V2_20\","
        + "\"payloadJson\":" + json(new com.google.gson.Gson().toJson(dto))
        + "}");

    int exit = new CommandLine(new DesignerXmlCli()).execute("apply-mutation", "--params", params.toString());

    assertThat(exit).isZero();
    String after = Files.readString(objectXml, StandardCharsets.UTF_8);
    assertThat(after).doesNotContain("Catalog._ДемоКонтрагенты</xr:Item>");
    assertThat(after).contains("<xr:Field>Catalog._ДемоБанковскиеСчета.StandardAttribute.Code</xr:Field>");
    // Гранулярность: из списков ушло по одной строке, остальной файл не переформатирован.
    assertThat(countLines(before) - countLines(after)).isEqualTo(2);
  }

  @Test
  void objectSet_scalarOnlyPayload_keepsAttributesAndOwners() throws Exception {
    // Частичный payload без списков: реквизиты и владельцы не должны потеряться.
    Path objectXml = copyToTemp(sampleCatalogXml());
    Path params = writeParams(
      "{"
        + "\"op\":\"cf-md-object-set\","
        + "\"objectXml\":" + json(objectXml.toString()) + ","
        + "\"schemaVersion\":\"V2_20\","
        + "\"payloadJson\":" + json(
          "{"
            + "\"kind\":\"catalog\","
            + "\"internalName\":\"_ДемоБанковскиеСчета\","
            + "\"synonymRu\":\"Демо: Банковские счета (частично)\""
            + "}")
        + "}");

    int exit = new CommandLine(new DesignerXmlCli()).execute("apply-mutation", "--params", params.toString());

    assertThat(exit).isZero();
    String after = Files.readString(objectXml, StandardCharsets.UTF_8);
    assertThat(after).contains("Демо: Банковские счета (частично)");
    assertThat(after).contains("БИКБанка");
    assertThat(after).contains("Catalog._ДемоОрганизации");
  }

  private static long countLines(String text) {
    return text.lines().count();
  }

  @Test
  void objectSet_blobPrefixesFromAnotherJvm_staysGranular() throws Exception {
    // JAXB между запусками JVM назначает ns-префиксы в блобах по-разному: это не изменение данных.
    Path objectXml = copyToTemp(sampleCatalogXml());
    String before = Files.readString(objectXml, StandardCharsets.UTF_8);
    io.github.yellowhammer.designerxml.cf.MdObjectPropertiesDto dto =
      io.github.yellowhammer.designerxml.cf.MdObjectPropertiesEdit.readDto(
        objectXml, io.github.yellowhammer.designerxml.SchemaVersion.V2_20);
    dto.synonymRu = "Демо: Банковские счета (префиксы)";
    dto.catalog.standardAttributesXml = dto.catalog.standardAttributesXml
      .replace("ns9:", "nsTMP:").replace("xmlns:ns9=", "xmlns:nsTMP=");
    Path params = writeParams(
      "{"
        + "\"op\":\"cf-md-object-set\","
        + "\"objectXml\":" + json(objectXml.toString()) + ","
        + "\"schemaVersion\":\"V2_20\","
        + "\"payloadJson\":" + json(new com.google.gson.Gson().toJson(dto))
        + "}");

    int exit = new CommandLine(new DesignerXmlCli()).execute("apply-mutation", "--params", params.toString());

    assertThat(exit).isZero();
    String after = Files.readString(objectXml, StandardCharsets.UTF_8);
    assertThat(after).contains("Демо: Банковские счета (префиксы)");
    assertThat(countLines(after)).isEqualTo(countLines(before));
  }

  @Test
  void attributeReorder_swapsBlocksGranularly() throws Exception {
    Path objectXml = copyToTemp(sampleCatalogXml());
    String before = Files.readString(objectXml, StandardCharsets.UTF_8);
    Path params = writeParams(
      "{"
        + "\"op\":\"cf-md-attribute-reorder\","
        + "\"objectXml\":" + json(objectXml.toString()) + ","
        + "\"schemaVersion\":\"V2_20\","
        + "\"payloadJson\":" + json("[\"БИКБанка\",\"Валюта\"]")
        + "}");

    int exit = new CommandLine(new DesignerXmlCli()).execute("apply-mutation", "--params", params.toString());

    assertThat(exit).isZero();
    String after = Files.readString(objectXml, StandardCharsets.UTF_8);
    assertThat(countLines(after)).isEqualTo(countLines(before));
    assertThat(after.indexOf("<Name>БИКБанка</Name>")).isLessThan(after.indexOf("<Name>Валюта</Name>"));
  }

  @Test
  void objectSet_clearDefaultForm_writesEmptyElement() throws Exception {
    Path objectXml = copyToTemp(sampleCatalogXml());
    String before = Files.readString(objectXml, StandardCharsets.UTF_8);
    io.github.yellowhammer.designerxml.cf.MdObjectPropertiesDto dto =
      io.github.yellowhammer.designerxml.cf.MdObjectPropertiesEdit.readDto(
        objectXml, io.github.yellowhammer.designerxml.SchemaVersion.V2_20);
    dto.catalog.defaultObjectForm = "";
    Path params = writeParams(
      "{"
        + "\"op\":\"cf-md-object-set\","
        + "\"objectXml\":" + json(objectXml.toString()) + ","
        + "\"schemaVersion\":\"V2_20\","
        + "\"payloadJson\":" + json(new com.google.gson.Gson().toJson(dto))
        + "}");

    int exit = new CommandLine(new DesignerXmlCli()).execute("apply-mutation", "--params", params.toString());

    assertThat(exit).isZero();
    String after = Files.readString(objectXml, StandardCharsets.UTF_8);
    assertThat(after).contains("<DefaultObjectForm/>");
    assertThat(after).doesNotContain("<DefaultObjectForm>Catalog.");
    assertThat(countLines(after)).isEqualTo(countLines(before));
  }

  @Test
  void tabularSectionAdd_writesInternalInfoAndRenameUpdatesIt() throws Exception {
    Path objectXml = copyToTemp(sampleCatalogXml());
    Path addParams = writeParams(
      "{"
        + "\"op\":\"cf-md-tabular-section-add\","
        + "\"objectXml\":" + json(objectXml.toString()) + ","
        + "\"name\":\"НоваяТЧ\","
        + "\"schemaVersion\":\"V2_20\""
        + "}");
    assertThat(new CommandLine(new DesignerXmlCli()).execute("apply-mutation", "--params", addParams.toString())).isZero();
    String afterAdd = Files.readString(objectXml, StandardCharsets.UTF_8);
    assertThat(afterAdd).contains("name=\"CatalogTabularSection._ДемоБанковскиеСчета.НоваяТЧ\" category=\"TabularSection\"");
    assertThat(afterAdd).contains("name=\"CatalogTabularSectionRow._ДемоБанковскиеСчета.НоваяТЧ\" category=\"TabularSectionRow\"");

    Path renameParams = writeParams(
      "{"
        + "\"op\":\"cf-md-tabular-section-rename\","
        + "\"objectXml\":" + json(objectXml.toString()) + ","
        + "\"oldName\":\"НоваяТЧ\","
        + "\"newName\":\"Переименованная\","
        + "\"schemaVersion\":\"V2_20\""
        + "}");
    assertThat(new CommandLine(new DesignerXmlCli()).execute("apply-mutation", "--params", renameParams.toString())).isZero();
    String afterRename = Files.readString(objectXml, StandardCharsets.UTF_8);
    assertThat(afterRename).contains("name=\"CatalogTabularSection._ДемоБанковскиеСчета.Переименованная\"");
    assertThat(afterRename).doesNotContain(".НоваяТЧ\"");
    assertThat(afterRename).doesNotContain("<Name>НоваяТЧ</Name>");
  }

  @Test
  void objectSet_documentScalarsAndRegisterRecords_writeGranularly() throws Exception {
    Path objectXml = copyToTemp(sampleDocumentXml());
    String before = Files.readString(objectXml, StandardCharsets.UTF_8);
    io.github.yellowhammer.designerxml.cf.MdObjectPropertiesDto dto =
      io.github.yellowhammer.designerxml.cf.MdObjectPropertiesEdit.readDto(
        objectXml, io.github.yellowhammer.designerxml.SchemaVersion.V2_20);
    assertThat(dto.document).as("документ должен читаться гранулярно").isNotNull();
    dto.synonymRu = "Демо: Заказ покупателя 222";
    dto.document.numberLength = "15";
    dto.document.posting = "DENY";
    dto.document.registerRecords = new java.util.ArrayList<>(dto.document.registerRecords);
    if (!dto.document.registerRecords.isEmpty()) {
      dto.document.registerRecords.remove(dto.document.registerRecords.size() - 1);
    }
    Path params = writeParams(
      "{"
        + "\"op\":\"cf-md-object-set\","
        + "\"objectXml\":" + json(objectXml.toString()) + ","
        + "\"schemaVersion\":\"V2_20\","
        + "\"payloadJson\":" + json(new com.google.gson.Gson().toJson(dto))
        + "}");

    int exit = new CommandLine(new DesignerXmlCli()).execute("apply-mutation", "--params", params.toString());

    assertThat(exit).isZero();
    String after = Files.readString(objectXml, StandardCharsets.UTF_8);
    assertThat(after).contains("Демо: Заказ покупателя 222");
    assertThat(after).contains("<NumberLength>15</NumberLength>");
    assertThat(after).contains("<Posting>Deny</Posting>");
    // Гранулярность: файл не переформатирован (могла уйти строка из RegisterRecords).
    assertThat(countLines(before) - countLines(after)).isBetween(0L, 1L);
  }

  @Test
  void objectSet_enumScalars_writeGranularly() throws Exception {
    Path objectXml = copyToTemp(sampleEnumXml());
    String before = Files.readString(objectXml, StandardCharsets.UTF_8);
    io.github.yellowhammer.designerxml.cf.MdObjectPropertiesDto dto =
      io.github.yellowhammer.designerxml.cf.MdObjectPropertiesEdit.readDto(
        objectXml, io.github.yellowhammer.designerxml.SchemaVersion.V2_20);
    assertThat(dto.enumeration).as("перечисление должно читаться гранулярно").isNotNull();
    dto.synonymRu = "Демо: Статусы заказов 222";
    dto.enumeration.quickChoice = !dto.enumeration.quickChoice;
    dto.enumeration.choiceMode = "QUICK_CHOICE";
    dto.enumeration.explanationRu = "Пояснение из теста";
    Path params = writeParams(
      "{"
        + "\"op\":\"cf-md-object-set\","
        + "\"objectXml\":" + json(objectXml.toString()) + ","
        + "\"schemaVersion\":\"V2_20\","
        + "\"payloadJson\":" + json(new com.google.gson.Gson().toJson(dto))
        + "}");

    int exit = new CommandLine(new DesignerXmlCli()).execute("apply-mutation", "--params", params.toString());

    assertThat(exit).isZero();
    String after = Files.readString(objectXml, StandardCharsets.UTF_8);
    assertThat(after).contains("Демо: Статусы заказов 222");
    assertThat(after).contains("<ChoiceMode>QuickChoice</ChoiceMode>");
    assertThat(after).contains("Пояснение из теста");
    // Гранулярность: пустое пояснение раскрылось в локализованную строку, остальное не переформатировано.
    assertThat(countLines(after) - countLines(before)).isBetween(0L, 6L);
  }

  @Test
  void objectSet_constantScalars_writeGranularly() throws Exception {
    Path objectXml = copyToTemp(sampleConstantXml());
    String before = Files.readString(objectXml, StandardCharsets.UTF_8);
    io.github.yellowhammer.designerxml.cf.MdObjectPropertiesDto dto =
      io.github.yellowhammer.designerxml.cf.MdObjectPropertiesEdit.readDto(
        objectXml, io.github.yellowhammer.designerxml.SchemaVersion.V2_20);
    assertThat(dto.constant).as("константа должна читаться гранулярно").isNotNull();
    dto.constant.passwordMode = !dto.constant.passwordMode;
    dto.constant.fillChecking = "SHOW_ERROR";
    dto.constant.toolTipRu = "Подсказка из теста";
    Path params = writeParams(
      "{"
        + "\"op\":\"cf-md-object-set\","
        + "\"objectXml\":" + json(objectXml.toString()) + ","
        + "\"schemaVersion\":\"V2_20\","
        + "\"payloadJson\":" + json(new com.google.gson.Gson().toJson(dto))
        + "}");

    int exit = new CommandLine(new DesignerXmlCli()).execute("apply-mutation", "--params", params.toString());

    assertThat(exit).isZero();
    String after = Files.readString(objectXml, StandardCharsets.UTF_8);
    assertThat(after).contains("<FillChecking>ShowError</FillChecking>");
    assertThat(after).contains("Подсказка из теста");
    // Тип значения не трогали — остаётся как был.
    assertThat(after).contains("<Type>");
    // Гранулярность: пустая подсказка раскрылась в локализованную строку, остальное не переформатировано.
    assertThat(countLines(after) - countLines(before)).isBetween(0L, 6L);
  }

  @Test
  void objectSet_constantType_writesTypeWithQualifiersGranularly() throws Exception {
    Path objectXml = copyToTemp(sampleConstantXml());
    String before = Files.readString(objectXml, StandardCharsets.UTF_8);
    io.github.yellowhammer.designerxml.cf.MdObjectPropertiesDto dto =
      io.github.yellowhammer.designerxml.cf.MdObjectPropertiesEdit.readDto(
        objectXml, io.github.yellowhammer.designerxml.SchemaVersion.V2_20);
    assertThat(dto.constant.type).as("тип значения должен читаться").isNotNull();
    assertThat(dto.constant.type.types).containsExactly("xs:boolean");

    // Булево -> строка длиной 25, как это делает конфигуратор.
    dto.constant.type.types = new java.util.ArrayList<>(java.util.List.of("xs:string"));
    io.github.yellowhammer.designerxml.cf.MdTypeDescriptionDto.MdStringQualifiersDto q =
      new io.github.yellowhammer.designerxml.cf.MdTypeDescriptionDto.MdStringQualifiersDto();
    q.length = "25";
    q.allowedLength = "VARIABLE";
    dto.constant.type.stringQualifiers = q;
    Path params = writeParams(
      "{"
        + "\"op\":\"cf-md-object-set\","
        + "\"objectXml\":" + json(objectXml.toString()) + ","
        + "\"schemaVersion\":\"V2_20\","
        + "\"payloadJson\":" + json(new com.google.gson.Gson().toJson(dto))
        + "}");

    int exit = new CommandLine(new DesignerXmlCli()).execute("apply-mutation", "--params", params.toString());

    assertThat(exit).isZero();
    String after = Files.readString(objectXml, StandardCharsets.UTF_8);
    assertThat(after).contains("<v8:Type xmlns:xs=\"http://www.w3.org/2001/XMLSchema\">xs:string</v8:Type>");
    assertThat(after).contains("<v8:Length>25</v8:Length>");
    assertThat(after).contains("<v8:AllowedLength>Variable</v8:AllowedLength>");
    assertThat(after).doesNotContain("xs:boolean");
    // Гранулярность: тип раскрылся в квалификаторы, остальное не переформатировано.
    assertThat(countLines(after) - countLines(before)).isBetween(0L, 5L);
    // Тип читается обратно.
    io.github.yellowhammer.designerxml.cf.MdObjectPropertiesDto reread =
      io.github.yellowhammer.designerxml.cf.MdObjectPropertiesEdit.readDto(
        objectXml, io.github.yellowhammer.designerxml.SchemaVersion.V2_20);
    assertThat(reread.constant.type.types).containsExactly("xs:string");
    assertThat(reread.constant.type.stringQualifiers.length).isEqualTo("25");
    assertThat(reread.constant.type.stringQualifiers.allowedLength).isEqualTo("VARIABLE");
  }

  @Test
  void objectSet_commonModuleFlags_writeGranularly() throws Exception {
    Path objectXml = copyToTemp(sampleCommonModuleXml());
    String before = Files.readString(objectXml, StandardCharsets.UTF_8);
    io.github.yellowhammer.designerxml.cf.MdObjectPropertiesDto dto =
      io.github.yellowhammer.designerxml.cf.MdObjectPropertiesEdit.readDto(
        objectXml, io.github.yellowhammer.designerxml.SchemaVersion.V2_20);
    assertThat(dto.commonModule).as("общий модуль должен читаться гранулярно").isNotNull();
    dto.commonModule.serverCall = !dto.commonModule.serverCall;
    dto.commonModule.privileged = !dto.commonModule.privileged;
    Path params = writeParams(
      "{"
        + "\"op\":\"cf-md-object-set\","
        + "\"objectXml\":" + json(objectXml.toString()) + ","
        + "\"schemaVersion\":\"V2_20\","
        + "\"payloadJson\":" + json(new com.google.gson.Gson().toJson(dto))
        + "}");

    int exit = new CommandLine(new DesignerXmlCli()).execute("apply-mutation", "--params", params.toString());

    assertThat(exit).isZero();
    String after = Files.readString(objectXml, StandardCharsets.UTF_8);
    assertThat(after).contains("<Privileged>" + dto.commonModule.privileged + "</Privileged>");
    assertThat(after).contains("<ServerCall>" + dto.commonModule.serverCall + "</ServerCall>");
    assertThat(countLines(after)).isEqualTo(countLines(before));
  }

  @Test
  void unknownOp_returnsError() throws Exception {
    Path params = writeParams("{\"op\":\"no-such-op\"}");
    int exit = new CommandLine(new DesignerXmlCli()).execute("apply-mutation", "--params", params.toString());
    assertThat(exit).isEqualTo(2);
  }

  @Test
  void missingRequiredField_returnsError() throws Exception {
    Path params = writeParams("{\"op\":\"cf-md-attribute-add\",\"schemaVersion\":\"V2_20\"}");
    int exit = new CommandLine(new DesignerXmlCli()).execute("apply-mutation", "--params", params.toString());
    assertThat(exit).isEqualTo(2);
  }

  @Test
  void readStructure_viaParamsFile_keepsCyrillicPathAndName() throws Exception {
    // Путь к объекту содержит кириллицу (имя файла) — проверяем чтение через UTF-8 JSON, не argv.
    Path objectXml = sampleDocumentXml();
    Path params = writeParams(
      "{"
        + "\"op\":\"cf-md-object-structure-get\","
        + "\"objectXml\":" + json(objectXml.toString()) + ","
        + "\"schemaVersion\":\"V2_20\""
        + "}");

    ByteArrayOutputStream out = new ByteArrayOutputStream();
    PrintStream prev = System.out;
    int exit;
    try {
      System.setOut(new PrintStream(out, true, StandardCharsets.UTF_8));
      exit = new CommandLine(new DesignerXmlCli()).execute("read-json", "--params", params.toString());
    } finally {
      System.setOut(prev);
    }

    assertThat(exit).isZero();
    assertThat(out.toString(StandardCharsets.UTF_8)).contains("\"document\"");
  }

  private static String json(String raw) {
    return "\"" + raw.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
  }

  private static Path writeParams(String content) throws IOException {
    Path dir = Files.createTempDirectory("apply-mutation-");
    Path file = dir.resolve("params.json");
    Files.writeString(file, content, StandardCharsets.UTF_8);
    return file;
  }

  private static Path copyToTemp(Path source) throws IOException {
    Path dir = Files.createTempDirectory("apply-mutation-obj-");
    Path target = dir.resolve(source.getFileName().toString());
    Files.copy(source, target);
    return target;
  }

  private static Path sampleDocumentXml() {
    String fixturesRoot = System.getProperty("fixtures.ssl31.root");
    return Path.of(fixturesRoot)
      .resolve("src")
      .resolve("cf")
      .resolve("Documents")
      .resolve("_ДемоЗаказПокупателя.xml");
  }

  private static Path sampleEnumXml() {
    return cfDir().resolve("Enums").resolve("_ДемоСтатусыЗаказовПокупателей.xml");
  }

  private static Path sampleConstantXml() {
    return cfDir().resolve("Constants").resolve("_ДемоИспользоватьНесколькоОрганизаций.xml");
  }

  private static Path sampleCommonModuleXml() {
    return cfDir().resolve("CommonModules").resolve("_ДемоЗаметки.xml");
  }

  private static Path cfDir() {
    return Path.of(System.getProperty("fixtures.ssl31.root")).resolve("src").resolve("cf");
  }

  private static Path sampleCatalogXml() {
    String fixturesRoot = System.getProperty("fixtures.ssl31.root");
    return Path.of(fixturesRoot)
      .resolve("src")
      .resolve("cf")
      .resolve("Catalogs")
      .resolve("_ДемоБанковскиеСчета.xml");
  }
}
