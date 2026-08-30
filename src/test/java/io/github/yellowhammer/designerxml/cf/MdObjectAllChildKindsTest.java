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

import io.github.yellowhammer.designerxml.Ssl31SubmodulePaths;
import io.github.yellowhammer.designerxml.SchemaVersion;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Свойства узлов состава читаются у всех видов, а не только у реквизитов,
 * табличных частей, значений, измерений и ресурсов: иначе палитра свойств
 * показывает выделенной команде или графе пустую карточку.
 */
class MdObjectAllChildKindsTest {

  private static MdObjectPropertiesDto read(String relativePath) throws Exception {
    Path xml = Ssl31SubmodulePaths.projectRoot().resolve(relativePath);
    assertThat(Files.isRegularFile(xml)).as("фикстура %s", relativePath).isTrue();
    return MdObjectPropertiesEdit.readDto(xml, SchemaVersion.V2_20);
  }

  @Test
  void catalogCommandsCarrySynonym() throws Exception {
    MdObjectPropertiesDto dto = read("src/cf/Catalogs/_ДемоБанковскиеСчета.xml");
    assertThat(dto.commands).isNotNull();
    for (MdNamedPropertyDto command : dto.commands) {
      assertThat(command.name).isNotBlank();
    }
  }

  @Test
  void tabularSectionCarriesItsAttributes() throws Exception {
    MdObjectPropertiesDto dto = read("src/cf/Documents/_ДемоЗаказПокупателя.xml");
    List<MdNamedPropertyDto> withAttributes = dto.tabularSections.stream()
      .filter(section -> section.attributes != null && !section.attributes.isEmpty())
      .toList();
    assertThat(withAttributes)
      .as("у табличных частей документа есть реквизиты")
      .isNotEmpty();
    for (MdNamedPropertyDto attribute : withAttributes.get(0).attributes) {
      assertThat(attribute.name).isNotBlank();
    }
  }

  @Test
  void chartOfAccountsCarriesAccountingFlags() throws Exception {
    MdObjectPropertiesDto dto = read("src/cf/ChartsOfAccounts/_ДемоОсновной.xml");
    assertThat(dto.accountingFlags).isNotNull();
    assertThat(dto.extDimensionAccountingFlags).isNotNull();
  }

  @Test
  void formDescriptorIsReadable() throws Exception {
    // Свойства формы лежат в её собственном файле, а не в составе объекта
    MdObjectPropertiesDto dto = read("src/cf/Catalogs/_ДемоБанковскиеСчета/Forms/РеквизитыБанка.xml");
    assertThat(dto.kind).isEqualTo("form");
    assertThat(dto.internalName).isEqualTo("РеквизитыБанка");
    assertThat(dto.synonymRu).isEqualTo("Реквизиты банка");
  }

  @Test
  void templateDescriptorIsReadable() throws Exception {
    MdObjectPropertiesDto dto =
      read("src/cf/Catalogs/_ДемоМестаХранения/Templates/ДополнительныеДанныеПечати.xml");
    assertThat(dto.kind).isEqualTo("template");
    assertThat(dto.internalName).isEqualTo("ДополнительныеДанныеПечати");
    assertThat(dto.synonymRu).isEqualTo("Дополнительные данные печати");
  }

  @Test
  void externalReportIsReadAsRegularObject() throws Exception {
    // Внешний файл описывается так же, как объект конфигурации: одна панель на оба
    MdObjectPropertiesDto dto = read(
      "src/erf/_ДемоОтчетПоСчетамНаОплатуКонтекстный/_ДемоОтчетПоСчетамНаОплатуКонтекстный.xml");
    assertThat(dto.kind).isEqualTo("externalReport");
    assertThat(dto.internalName).isEqualTo("_ДемоОтчетПоСчетамНаОплатуКонтекстный");
    assertThat(dto.attributes).isNotNull();
    assertThat(dto.tabularSections).isNotNull();
  }

  @Test
  void everyTreeKindIsReadable() throws Exception {
    // Панель свойств открывается у любого узла дерева: раньше эти виды
    // отвечали «unsupported MetaDataObject» и палитра оставалась пустой
    record Sample(String kind, String relativePath) {
    }
    List<Sample> samples = List.of(
      new Sample("commonForm", "src/cf/CommonForms/_ДемоМоиНастройки.xml"),
      new Sample("commonTemplate", "src/cf/CommonTemplates/_ДемоОформлениеОтчетовБежевый.xml"),
      new Sample("webService", "src/cf/WebServices/EnterpriseDataExchange_1_0_1_1.xml"),
      new Sample("httpService", "src/cf/HTTPServices/Биллинг.xml"),
      new Sample("accountingRegister", "src/cf/AccountingRegisters/_ДемоЖурналПроводокБухгалтерскогоУчета.xml"),
      new Sample("calculationRegister", "src/cf/CalculationRegisters/_ДемоОсновныеНачисления.xml"),
      new Sample("sequence", "src/cf/Sequences/_ДемоДвижениеТоваров.xml"),
      new Sample("filterCriterion", "src/cf/FilterCriteria/СвязанныеДокументы.xml"),
      new Sample("settingsStorage", "src/cf/SettingsStorages/ХранилищеВариантовОтчетов.xml"),
      new Sample("functionalOption", "src/cf/FunctionalOptions/_ДемоИспользоватьХарактеристики.xml"),
      new Sample("definedType", "src/cf/DefinedTypes/БезопасныйРежим.xml"),
      new Sample("commandGroup", "src/cf/CommandGroups/Печать.xml"),
      new Sample("language", "src/cf/Languages/Русский.xml"),
      new Sample("styleItem", "src/cf/StyleItems/АктуальнаяПодпискаЦвет.xml"),
      new Sample("xdtoPackage", "src/cf/XDTOPackages/AgentScripts.xml")
    );
    for (Sample sample : samples) {
      MdObjectPropertiesDto dto = read(sample.relativePath());
      assertThat(dto.kind).as(sample.relativePath()).isEqualTo(sample.kind());
      assertThat(dto.internalName).as(sample.relativePath()).isNotBlank();
    }
  }

  @Test
  void accountingRegisterCarriesDimensions() throws Exception {
    MdObjectPropertiesDto dto =
      read("src/cf/AccountingRegisters/_ДемоЖурналПроводокБухгалтерскогоУчета.xml");
    assertThat(dto.dimensions).isNotEmpty();
    assertThat(dto.resources).isNotEmpty();
  }

  @Test
  void webServiceCarriesOperations() throws Exception {
    MdObjectPropertiesDto dto = read("src/cf/WebServices/EnterpriseDataExchange_1_0_1_1.xml");
    assertThat(dto.operations).isNotEmpty();
  }

  @Test
  void synonymAndCommentAreWritableForGenericKinds(@org.junit.jupiter.api.io.TempDir java.nio.file.Path tempDir)
    throws Exception {
    // Запись у видов без своего моста идёт точечным патчем: без контейнера в
    // карте она падала с «не удалось применить изменения точечно»
    List<String> samples = List.of(
      "src/cf/CommonForms/_ДемоМоиНастройки.xml",
      "src/cf/CommonTemplates/_ДемоОформлениеОтчетовБежевый.xml",
      "src/cf/WebServices/EnterpriseDataExchange_1_0_1_1.xml",
      "src/cf/Sequences/_ДемоДвижениеТоваров.xml",
      "src/cf/AccountingRegisters/_ДемоЖурналПроводокБухгалтерскогоУчета.xml",
      "src/cf/FunctionalOptions/_ДемоИспользоватьХарактеристики.xml"
    );
    for (String relative : samples) {
      java.nio.file.Path src = io.github.yellowhammer.designerxml.Ssl31SubmodulePaths.projectRoot().resolve(relative);
      java.nio.file.Path copy = tempDir.resolve(src.getFileName());
      java.nio.file.Files.copy(src, copy, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
      MdObjectPropertiesDto dto = MdObjectPropertiesEdit.readDto(copy, SchemaVersion.V2_20);
      dto.synonymRu = "Новый синоним";
      dto.comment = "Новый комментарий";
      MdObjectPropertiesEdit.writeDto(copy, SchemaVersion.V2_20, dto);
      MdObjectPropertiesDto after = MdObjectPropertiesEdit.readDto(copy, SchemaVersion.V2_20);
      assertThat(after.synonymRu).as(relative).isEqualTo("Новый синоним");
      assertThat(after.comment).as(relative).isEqualTo("Новый комментарий");
    }
  }

  @Test
  void attributeAddWorksOnExternalDataProcessor(@org.junit.jupiter.api.io.TempDir java.nio.file.Path tempDir)
    throws Exception {
    // Добавление реквизита внешней обработке отвечало «Тип объекта не
    // поддерживает ChildObjects»: контейнер внешних видов не был известен
    java.nio.file.Path src = java.nio.file.Path.of(
      "fixtures/samples-1c-platform/snapshots/2.20/external-files/empty/ВнешняяОбработка1/ВнешняяОбработка1.xml");
    java.nio.file.Path copy = tempDir.resolve(src.getFileName());
    java.nio.file.Files.copy(src, copy, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    MdObjectChildMutations.addAttribute(copy, SchemaVersion.V2_20, "НовыйРеквизит");
    MdObjectPropertiesDto dto = MdObjectPropertiesEdit.readDto(copy, SchemaVersion.V2_20);
    assertThat(dto.attributes).extracting(item -> item.name).contains("НовыйРеквизит");
    MdObjectPropertiesDto reportDto = MdObjectPropertiesEdit.readDto(
      copyOf(tempDir, "fixtures/samples-1c-platform/snapshots/2.20/external-files/empty/ВнешнийОтчет1/ВнешнийОтчет1.xml"),
      SchemaVersion.V2_20);
    assertThat(reportDto.kind).isEqualTo("externalReport");
  }

  private static java.nio.file.Path copyOf(java.nio.file.Path tempDir, String relative) throws java.io.IOException {
    java.nio.file.Path src = relative.startsWith("src/")
      ? io.github.yellowhammer.designerxml.Ssl31SubmodulePaths.projectRoot().resolve(relative)
      : java.nio.file.Path.of(relative);
    java.nio.file.Path copy = tempDir.resolve(src.getFileName());
    java.nio.file.Files.copy(src, copy, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    return copy;
  }

  @Test
  void scalarsAreReadForGenericKinds() throws Exception {
    MdObjectPropertiesDto language = read("src/cf/Languages/Русский.xml");
    assertThat(language.scalars).containsEntry("LanguageCode", "ru");
    assertThat(language.scalarMeta.get("LanguageCode").type).isEqualTo("string");

    MdObjectPropertiesDto webService = read("src/cf/WebServices/EnterpriseDataExchange_1_0_1_1.xml");
    assertThat(webService.scalars).isNotEmpty();
    assertThat(webService.scalars.keySet().stream().anyMatch(name -> name.contains("Namespace"))).isTrue();

    // У видов со своим мостом скаляров нет: их свойства несут типизированные DTO
    MdObjectPropertiesDto role = read("src/cf/Roles/АдминистраторСистемы.xml");
    assertThat(role.scalars).isNull();
  }

  @Test
  void scalarsAreWritableThroughGranularPatch(@org.junit.jupiter.api.io.TempDir java.nio.file.Path tempDir)
    throws Exception {
    java.nio.file.Path src = io.github.yellowhammer.designerxml.Ssl31SubmodulePaths.projectRoot()
      .resolve("src/cf/Languages/Русский.xml");
    java.nio.file.Path copy = tempDir.resolve(src.getFileName());
    java.nio.file.Files.copy(src, copy, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    MdObjectPropertiesDto dto = MdObjectPropertiesEdit.readDto(copy, SchemaVersion.V2_20);
    dto.scalars.put("LanguageCode", "kz");
    MdObjectPropertiesEdit.writeDto(copy, SchemaVersion.V2_20, dto);
    MdObjectPropertiesDto after = MdObjectPropertiesEdit.readDto(copy, SchemaVersion.V2_20);
    assertThat(after.scalars).containsEntry("LanguageCode", "kz");
  }

  @Test
  void enumScalarRoundTrips(@org.junit.jupiter.api.io.TempDir java.nio.file.Path tempDir) throws Exception {
    java.nio.file.Path src = io.github.yellowhammer.designerxml.Ssl31SubmodulePaths.projectRoot()
      .resolve("src/cf/CommonForms/_ДемоМоиНастройки.xml");
    java.nio.file.Path copy = tempDir.resolve(src.getFileName());
    java.nio.file.Files.copy(src, copy, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    MdObjectPropertiesDto dto = MdObjectPropertiesEdit.readDto(copy, SchemaVersion.V2_20);
    java.util.Optional<String> enumProp = dto.scalarMeta.entrySet().stream()
      .filter(e -> "enum".equals(e.getValue().type) && e.getValue().allowed.size() > 1)
      .map(java.util.Map.Entry::getKey)
      .findFirst();
    assertThat(enumProp).isPresent();
    String name = enumProp.get();
    String current = String.valueOf(dto.scalars.get(name));
    String next = dto.scalarMeta.get(name).allowed.stream()
      .filter(v -> !v.equals(current))
      .findFirst()
      .orElseThrow();
    dto.scalars.put(name, next);
    MdObjectPropertiesEdit.writeDto(copy, SchemaVersion.V2_20, dto);
    MdObjectPropertiesDto after = MdObjectPropertiesEdit.readDto(copy, SchemaVersion.V2_20);
    assertThat(after.scalars.get(name)).as(name).isEqualTo(next);
  }

  @Test
  void functionalOptionCarriesContentRefs() throws Exception {
    MdObjectPropertiesDto option = read("src/cf/FunctionalOptions/_ДемоИспользоватьХарактеристики.xml");
    assertThat(option.contentRefs).contains("Catalog._ДемоХарактеристики");

    MdObjectPropertiesDto parameter =
      read("src/cf/FunctionalOptionsParameters/_ДемоОрганизация.xml");
    assertThat(parameter.contentRefs).contains("Catalog._ДемоОрганизации");
  }

  @Test
  void moduleRefsAreNotScalars() throws Exception {
    MdObjectPropertiesDto webService = read("src/cf/WebServices/EnterpriseDataExchange_1_0_1_1.xml");
    assertThat(webService.scalars).doesNotContainKeys("Module");
    MdObjectPropertiesDto template = read("src/cf/CommonTemplates/_ДемоОформлениеОтчетовБежевый.xml");
    assertThat(template.scalars).doesNotContainKeys("Template");
  }

  @Test
  void functionalOptionContentIsWritable(@org.junit.jupiter.api.io.TempDir java.nio.file.Path tempDir)
    throws Exception {
    java.nio.file.Path copy = copyOf(tempDir, "src/cf/FunctionalOptions/_ДемоИспользоватьХарактеристики.xml");
    MdObjectPropertiesDto dto = MdObjectPropertiesEdit.readDto(copy, SchemaVersion.V2_20);
    dto.contentRefs.add("Catalog._ДемоНоменклатура");
    MdObjectPropertiesEdit.writeDto(copy, SchemaVersion.V2_20, dto);
    MdObjectPropertiesDto after = MdObjectPropertiesEdit.readDto(copy, SchemaVersion.V2_20);
    assertThat(after.contentRefs).contains("Catalog._ДемоНоменклатура", "Catalog._ДемоХарактеристики");

    java.nio.file.Path parameter = copyOf(tempDir, "src/cf/FunctionalOptionsParameters/_ДемоОрганизация.xml");
    MdObjectPropertiesDto parameterDto = MdObjectPropertiesEdit.readDto(parameter, SchemaVersion.V2_20);
    parameterDto.contentRefs = new java.util.ArrayList<>(java.util.List.of("Catalog._ДемоПодразделения"));
    MdObjectPropertiesEdit.writeDto(parameter, SchemaVersion.V2_20, parameterDto);
    MdObjectPropertiesDto parameterAfter = MdObjectPropertiesEdit.readDto(parameter, SchemaVersion.V2_20);
    assertThat(parameterAfter.contentRefs).containsExactly("Catalog._ДемоПодразделения");
  }

  @Test
  void subsystemMembershipIsWritable(@org.junit.jupiter.api.io.TempDir java.nio.file.Path tempDir) throws Exception {
    // Флажок участия на панели свойств пишет состав подсистемы точечно
    java.nio.file.Path copy = copyOf(tempDir, "src/cf/Subsystems/_ДемоАнкетирование.xml");
    MdObjectPropertiesDto dto = MdObjectPropertiesEdit.readDto(copy, SchemaVersion.V2_20);
    dto.contentRefs.add("ChartOfAccounts._ДемоОсновной");
    MdObjectPropertiesEdit.writeDto(copy, SchemaVersion.V2_20, dto);
    MdObjectPropertiesDto after = MdObjectPropertiesEdit.readDto(copy, SchemaVersion.V2_20);
    assertThat(after.contentRefs).contains("ChartOfAccounts._ДемоОсновной");
  }

  @Test
  void sequenceDocumentsAndRecordsAreWritable(@org.junit.jupiter.api.io.TempDir java.nio.file.Path tempDir)
    throws Exception {
    java.nio.file.Path copy = copyOf(tempDir, "src/cf/Sequences/_ДемоДвижениеТоваров.xml");
    MdObjectPropertiesDto dto = MdObjectPropertiesEdit.readDto(copy, SchemaVersion.V2_20);
    assertThat(dto.documents).contains("Document._ДемоРеализацияТоваров");
    assertThat(dto.registerRecords).isNotEmpty();

    dto.documents.add("Document._ДемоСписаниеТоваров");
    dto.registerRecords = new java.util.ArrayList<>(
      java.util.List.of("AccumulationRegister._ДемоОстаткиТоваровВМестахХранения"));
    MdObjectPropertiesEdit.writeDto(copy, SchemaVersion.V2_20, dto);
    MdObjectPropertiesDto after = MdObjectPropertiesEdit.readDto(copy, SchemaVersion.V2_20);
    assertThat(after.documents).contains("Document._ДемоСписаниеТоваров", "Document._ДемоРеализацияТоваров");
    assertThat(after.registerRecords)
      .containsExactly("AccumulationRegister._ДемоОстаткиТоваровВМестахХранения");
  }

  @Test
  void subsystemScalarsAreReadAndWritable(@org.junit.jupiter.api.io.TempDir java.nio.file.Path tempDir)
    throws Exception {
    java.nio.file.Path copy = copyOf(tempDir, "src/cf/Subsystems/_ДемоАнкетирование.xml");
    MdObjectPropertiesDto dto = MdObjectPropertiesEdit.readDto(copy, SchemaVersion.V2_20);
    assertThat(dto.scalars).containsEntry("IncludeInCommandInterface", true);
    dto.scalars.put("IncludeInCommandInterface", false);
    MdObjectPropertiesEdit.writeDto(copy, SchemaVersion.V2_20, dto);
    MdObjectPropertiesDto after = MdObjectPropertiesEdit.readDto(copy, SchemaVersion.V2_20);
    assertThat(after.scalars).containsEntry("IncludeInCommandInterface", false);
  }

  @Test
  void filterCriterionContentIsWritable(@org.junit.jupiter.api.io.TempDir java.nio.file.Path tempDir)
    throws Exception {
    java.nio.file.Path copy = copyOf(tempDir, "src/cf/FilterCriteria/СвязанныеДокументы.xml");
    MdObjectPropertiesDto dto = MdObjectPropertiesEdit.readDto(copy, SchemaVersion.V2_20);
    assertThat(dto.contentRefs).isNotEmpty();
    String removed = dto.contentRefs.remove(0);
    MdObjectPropertiesEdit.writeDto(copy, SchemaVersion.V2_20, dto);
    MdObjectPropertiesDto after = MdObjectPropertiesEdit.readDto(copy, SchemaVersion.V2_20);
    assertThat(after.contentRefs).doesNotContain(removed).hasSize(dto.contentRefs.size());
  }

  @Test
  void commonAttributeMembersAreWritable(@org.junit.jupiter.api.io.TempDir java.nio.file.Path tempDir)
    throws Exception {
    java.nio.file.Path copy = copyOf(tempDir, "src/cf/CommonAttributes/КомментарийЯзык1.xml");
    MdObjectPropertiesDto dto = MdObjectPropertiesEdit.readDto(copy, SchemaVersion.V2_20);
    assertThat(dto.contentMembers).isNotEmpty();
    assertThat(dto.contentMembers.get(0).mode).isEqualTo("USE");
    dto.contentMembers.get(0).mode = "DONT_USE";
    dto.contentMembers.add(new MdContentMemberDto("Catalog._ДемоНоменклатура", "USE", null));
    MdObjectPropertiesEdit.writeDto(copy, SchemaVersion.V2_20, dto);
    MdObjectPropertiesDto after = MdObjectPropertiesEdit.readDto(copy, SchemaVersion.V2_20);
    assertThat(after.contentMembers.get(0).mode).isEqualTo("DONT_USE");
    assertThat(after.contentMembers)
      .anyMatch(member -> "Catalog._ДемоНоменклатура".equals(member.ref) && "USE".equals(member.mode));
  }

  @Test
  void accountingFlagsAreMutable(@org.junit.jupiter.api.io.TempDir java.nio.file.Path tempDir) throws Exception {
    java.nio.file.Path copy = copyOf(tempDir, "src/cf/ChartsOfAccounts/_ДемоОсновной.xml");
    MdObjectChildMutations.addAccountingFlag(copy, SchemaVersion.V2_20, "НовыйПризнак");
    MdObjectChildMutations.renameAccountingFlag(copy, SchemaVersion.V2_20, "НовыйПризнак", "Налоговый");
    MdObjectChildMutations.addExtDimensionAccountingFlag(copy, SchemaVersion.V2_20, "СубконтоПризнак");
    MdObjectPropertiesDto dto = MdObjectPropertiesEdit.readDto(copy, SchemaVersion.V2_20);
    assertThat(dto.accountingFlags).extracting(item -> item.name).contains("Налоговый", "Валютный");
    assertThat(dto.extDimensionAccountingFlags).extracting(item -> item.name).contains("СубконтоПризнак");
    MdObjectChildMutations.deleteAccountingFlag(copy, SchemaVersion.V2_20, "Налоговый");
    MdObjectPropertiesDto after = MdObjectPropertiesEdit.readDto(copy, SchemaVersion.V2_20);
    assertThat(after.accountingFlags).extracting(item -> item.name).doesNotContain("Налоговый");
  }

  @Test
  void formDeleteRemovesEntryFilesAndSlots(@org.junit.jupiter.api.io.TempDir java.nio.file.Path tempDir)
    throws Exception {
    java.nio.file.Path src = io.github.yellowhammer.designerxml.Ssl31SubmodulePaths.projectRoot()
      .resolve("src/cf/Catalogs/_ДемоБанковскиеСчета.xml");
    java.nio.file.Path copy = tempDir.resolve(src.getFileName());
    java.nio.file.Files.copy(src, copy, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    java.nio.file.Path formsDir = tempDir.resolve("_ДемоБанковскиеСчета").resolve("Forms");
    java.nio.file.Files.createDirectories(formsDir.resolve("ФормаЭлемента").resolve("Ext"));
    java.nio.file.Files.writeString(formsDir.resolve("ФормаЭлемента.xml"), "<x/>");
    java.nio.file.Files.writeString(
      formsDir.resolve("ФормаЭлемента").resolve("Ext").resolve("Form.xml"), "<x/>");

    MdObjectChildMutations.deleteForm(copy, SchemaVersion.V2_20, "ФормаЭлемента");

    String xml = java.nio.file.Files.readString(copy);
    assertThat(xml).doesNotContain("<Form>ФормаЭлемента</Form>");
    assertThat(xml).contains("<DefaultObjectForm/>");
    assertThat(java.nio.file.Files.exists(formsDir.resolve("ФормаЭлемента.xml"))).isFalse();
    assertThat(java.nio.file.Files.exists(formsDir.resolve("ФормаЭлемента"))).isFalse();
    MdObjectPropertiesDto dto = MdObjectPropertiesEdit.readDto(copy, SchemaVersion.V2_20);
    assertThat(dto.internalName).isEqualTo("_ДемоБанковскиеСчета");
  }

  @Test
  void newListsAreNeverNull() throws Exception {
    MdObjectPropertiesDto dto = read("src/cf/Catalogs/_ДемоВидыНоменклатуры.xml");
    assertThat(dto.commands).isNotNull();
    assertThat(dto.columns).isNotNull();
    assertThat(dto.accountingFlags).isNotNull();
    assertThat(dto.extDimensionAccountingFlags).isNotNull();
    assertThat(dto.addressingAttributes).isNotNull();
    assertThat(dto.recalculations).isNotNull();
    assertThat(dto.operations).isNotNull();
    assertThat(dto.urlTemplates).isNotNull();
    assertThat(dto.channels).isNotNull();
    assertThat(dto.tables).isNotNull();
    assertThat(dto.cubes).isNotNull();
    assertThat(dto.functions).isNotNull();
  }
}
