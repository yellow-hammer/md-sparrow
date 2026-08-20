/*
 * This file is a part of md-sparrow.
 *
 * Copyright (c) 2026
 * Ivan Karlo <i.karlo@outlook.com> and contributors
 *
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package io.github.yellowhammer.designerxml.cf;

import io.github.yellowhammer.designerxml.DesignerXml;
import io.github.yellowhammer.designerxml.SchemaVersion;
import io.github.yellowhammer.designerxml.Ssl31SubmodulePaths;
import jakarta.xml.bind.JAXBElement;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class MdObjectPropertiesEditTest {

  @TempDir
  Path tempDir;

  @Test
  void supportsChildObjectType_matchesCfMdObject() {
    assertThat(MdObjectPropertiesEdit.supportsChildObjectType("Catalog")).isTrue();
    assertThat(MdObjectPropertiesEdit.supportsChildObjectType("CommonModule")).isTrue();
    assertThat(MdObjectPropertiesEdit.supportsChildObjectType("CommonForm")).isFalse();
    assertThat(MdObjectPropertiesEdit.supportsChildObjectType("HTTPService")).isFalse();
  }

  @Test
  void readDto_catalog_fromSsl31() throws Exception {
    Path any = Ssl31SubmodulePaths.anyCatalogObjectXml();
    MdObjectPropertiesDto dto = MdObjectPropertiesEdit.readDto(any, SchemaVersion.V2_20);
    assertThat(dto.kind).isEqualTo("catalog");
    assertThat(dto.internalName).isNotBlank();
    assertThat(dto.synonymRu).isNotNull();
    assertThat(dto.comment).isNotNull();
    assertThat(dto.attributes).isNotNull();
    assertThat(dto.tabularSections).isNotNull();
    assertThat(dto.catalog).isNotNull();
    assertThat(dto.catalog.codeLength).isNotBlank();
    assertThat(dto.catalog.standardAttributesXml).isNotNull();
    assertThat(dto.catalog.characteristicsXml).isNotNull();
  }

  @Test
  void writeDto_roundTrip_catalog() throws Exception {
    Path src = Ssl31SubmodulePaths.anyCatalogObjectXml();
    Path copy = tempDir.resolve(src.getFileName());
    Files.copy(src, copy);

    MdObjectPropertiesDto before = MdObjectPropertiesEdit.readDto(copy, SchemaVersion.V2_20);
    MdObjectPropertiesEdit.writeDto(copy, SchemaVersion.V2_20, before);
    MdObjectPropertiesDto after = MdObjectPropertiesEdit.readDto(copy, SchemaVersion.V2_20);

    assertThat(after.kind).isEqualTo(before.kind);
    assertThat(after.internalName).isEqualTo(before.internalName);
    assertThat(after.synonymRu).isEqualTo(before.synonymRu);
    assertThat(after.comment).isEqualTo(before.comment);
    assertThat(after.attributes.size()).isEqualTo(before.attributes.size());
    assertThat(after.tabularSections.size()).isEqualTo(before.tabularSections.size());
  }

  @Test
  void writeDto_noOp_whenDtoUnchanged_doesNotTouchFile() throws Exception {
    Path src = Ssl31SubmodulePaths.anyCatalogObjectXml();
    Path copy = tempDir.resolve(src.getFileName());
    Files.copy(src, copy);
    byte[] beforeBytes = Files.readAllBytes(copy);

    MdObjectPropertiesDto dto = MdObjectPropertiesEdit.readDto(copy, SchemaVersion.V2_20);
    MdObjectPropertiesEdit.writeDto(copy, SchemaVersion.V2_20, dto);

    assertThat(Files.readAllBytes(copy)).isEqualTo(beforeBytes);
  }

  @Test
  void writeDto_onlySynonymChange_preservesBytesOutsideSynonymElement() throws Exception {
    Path src = Ssl31SubmodulePaths.anyCatalogObjectXml();
    Path copy = tempDir.resolve(src.getFileName());
    Files.copy(src, copy);

    String xmlBefore = Files.readString(copy, StandardCharsets.UTF_8);
    MdObjectXmlRegions.Region syn = MdObjectXmlRegions.findDirectChildOfPropertiesRegion(xmlBefore, "Catalog", "Synonym");
    assertThat(syn.isValid()).isTrue();

    MdObjectPropertiesDto dto = MdObjectPropertiesEdit.readDto(copy, SchemaVersion.V2_20);
    dto.synonymRu = "[md-sparrow-test-syn] " + (dto.synonymRu == null ? "" : dto.synonymRu);
    MdObjectPropertiesEdit.writeDto(copy, SchemaVersion.V2_20, dto);

    String xmlAfter = Files.readString(copy, StandardCharsets.UTF_8);
    int delta = xmlAfter.length() - xmlBefore.length();
    assertThat(xmlAfter.substring(0, syn.start())).isEqualTo(xmlBefore.substring(0, syn.start()));
    assertThat(xmlAfter.substring(syn.end() + delta)).isEqualTo(xmlBefore.substring(syn.end()));
    assertThat(MdObjectPropertiesEdit.readDto(copy, SchemaVersion.V2_20).synonymRu).isEqualTo(dto.synonymRu);
  }

  @Test
  void granularPatch_applies_whenOnlyAttributeSynonymChanged_preservesBytesOutsideThatSynonym() throws Exception {
    Path src = Ssl31SubmodulePaths.anyCatalogObjectXml();
    String xmlBefore = Files.readString(src, StandardCharsets.UTF_8);
    MdObjectPropertiesDto b = MdObjectPropertiesEdit.readDto(src, SchemaVersion.V2_20);
    MdObjectPropertiesDto i = MdObjectPropertiesEdit.readDto(src, SchemaVersion.V2_20);
    String firstName = i.attributes.get(0).name;
    i.attributes.get(0).synonymRu = "___granular_attr_test___";

    MdObjectXmlRegions.Region attrSyn =
      MdObjectXmlRegions.findDirectChildOfNamedChildObjectPropertiesRegion(
        xmlBefore, "Catalog", "Attribute", firstName, "Synonym");
    assertThat(attrSyn.isValid()).isTrue();

    Optional<byte[]> g = MdObjectPropertiesGranularPatch.tryApply(xmlBefore, "Catalog", SchemaVersion.V2_20, b, i);
    assertThat(g).isPresent();
    String xmlAfter = new String(g.get(), StandardCharsets.UTF_8);
    int delta = xmlAfter.length() - xmlBefore.length();
    assertThat(xmlAfter.substring(0, attrSyn.start())).isEqualTo(xmlBefore.substring(0, attrSyn.start()));
    assertThat(xmlAfter.substring(attrSyn.end() + delta)).isEqualTo(xmlBefore.substring(attrSyn.end()));
  }

  @Test
  void granularPatch_onlyCatalogSynonymChange_keepsFirstLineIndent() throws Exception {
    Path src = Ssl31SubmodulePaths.anyCatalogObjectXml();
    String xmlBefore = Files.readString(src, StandardCharsets.UTF_8);
    MdObjectPropertiesDto b = MdObjectPropertiesEdit.readDto(src, SchemaVersion.V2_20);
    MdObjectPropertiesDto i = MdObjectPropertiesEdit.readDto(src, SchemaVersion.V2_20);
    i.synonymRu = "[indent-guard] " + (i.synonymRu == null ? "" : i.synonymRu);

    MdObjectXmlRegions.Region syn =
      MdObjectXmlRegions.findDirectChildOfPropertiesRegion(xmlBefore, "Catalog", "Synonym");
    assertThat(syn.isValid()).isTrue();

    Optional<byte[]> g = MdObjectPropertiesGranularPatch.tryApply(xmlBefore, "Catalog", SchemaVersion.V2_20, b, i);
    assertThat(g).isPresent();
    String xmlAfter = new String(g.get(), StandardCharsets.UTF_8);

    String beforePrefix = linePrefixBeforeTag(xmlBefore, syn.start());
    int afterStart = xmlAfter.indexOf("<Synonym>", syn.start() - 32);
    assertThat(afterStart).isGreaterThanOrEqualTo(0);
    String afterPrefix = linePrefixBeforeTag(xmlAfter, afterStart);
    assertThat(afterPrefix).isEqualTo(beforePrefix);
  }

  @Test
  void granularPatch_onlyCatalogObjectPresentationChange_keepsFirstLineIndent() throws Exception {
    Path src = Ssl31SubmodulePaths.anyCatalogObjectXml();
    String xmlBefore = Files.readString(src, StandardCharsets.UTF_8);
    MdObjectPropertiesDto b = MdObjectPropertiesEdit.readDto(src, SchemaVersion.V2_20);
    MdObjectPropertiesDto i = MdObjectPropertiesEdit.readDto(src, SchemaVersion.V2_20);
    assertThat(i.catalog).isNotNull();
    i.catalog.objectPresentationRu = "[indent-guard-op] "
      + (i.catalog.objectPresentationRu == null ? "" : i.catalog.objectPresentationRu);

    MdObjectXmlRegions.Region op =
      MdObjectXmlRegions.findDirectChildOfPropertiesRegion(xmlBefore, "Catalog", "ObjectPresentation");
    assertThat(op.isValid()).isTrue();

    Optional<byte[]> g = MdObjectPropertiesGranularPatch.tryApply(xmlBefore, "Catalog", SchemaVersion.V2_20, b, i);
    assertThat(g).isPresent();
    String xmlAfter = new String(g.get(), StandardCharsets.UTF_8);

    String beforePrefix = linePrefixBeforeTag(xmlBefore, op.start());
    int afterStart = xmlAfter.indexOf("<ObjectPresentation>", op.start() - 32);
    assertThat(afterStart).isGreaterThanOrEqualTo(0);
    String afterPrefix = linePrefixBeforeTag(xmlAfter, afterStart);
    assertThat(afterPrefix).isEqualTo(beforePrefix);
  }

  @Test
  void granularPatch_onlyFirstAttributeSynonymChange_keepsFirstLineIndent() throws Exception {
    Path src = Ssl31SubmodulePaths.anyCatalogObjectXml();
    String xmlBefore = Files.readString(src, StandardCharsets.UTF_8);
    MdObjectPropertiesDto b = MdObjectPropertiesEdit.readDto(src, SchemaVersion.V2_20);
    MdObjectPropertiesDto i = MdObjectPropertiesEdit.readDto(src, SchemaVersion.V2_20);
    String firstName = i.attributes.get(0).name;
    i.attributes.get(0).synonymRu = "[indent-guard-attr] " + i.attributes.get(0).synonymRu;

    MdObjectXmlRegions.Region attrSyn =
      MdObjectXmlRegions.findDirectChildOfNamedChildObjectPropertiesRegion(
        xmlBefore, "Catalog", "Attribute", firstName, "Synonym");
    assertThat(attrSyn.isValid()).isTrue();

    Optional<byte[]> g = MdObjectPropertiesGranularPatch.tryApply(xmlBefore, "Catalog", SchemaVersion.V2_20, b, i);
    assertThat(g).isPresent();
    String xmlAfter = new String(g.get(), StandardCharsets.UTF_8);

    String beforePrefix = linePrefixBeforeTag(xmlBefore, attrSyn.start());
    int afterStart = xmlAfter.indexOf("<Synonym>", attrSyn.start() - 32);
    assertThat(afterStart).isGreaterThanOrEqualTo(0);
    String afterPrefix = linePrefixBeforeTag(xmlAfter, afterStart);
    assertThat(afterPrefix).isEqualTo(beforePrefix);
  }

  @Test
  void granularPatch_empty_whenFirstAttributeRenamed() throws Exception {
    Path src = Ssl31SubmodulePaths.anyCatalogObjectXml();
    String xml = Files.readString(src, StandardCharsets.UTF_8);
    MdObjectPropertiesDto b = MdObjectPropertiesEdit.readDto(src, SchemaVersion.V2_20);
    MdObjectPropertiesDto i = MdObjectPropertiesEdit.readDto(src, SchemaVersion.V2_20);
    i.attributes.get(0).name = "__renamed_only__";

    Optional<byte[]> g = MdObjectPropertiesGranularPatch.tryApply(xml, "Catalog", SchemaVersion.V2_20, b, i);
    assertThat(g).isEmpty();
  }

  @Test
  void writeDto_onlyFirstAttributeCommentChange_preservesBytesOutsideThatComment() throws Exception {
    Path src = Ssl31SubmodulePaths.anyCatalogObjectXml();
    Path copy = tempDir.resolve(src.getFileName());
    Files.copy(src, copy);

    String xmlBefore = Files.readString(copy, StandardCharsets.UTF_8);
    MdObjectPropertiesDto dto = MdObjectPropertiesEdit.readDto(copy, SchemaVersion.V2_20);
    String firstName = dto.attributes.get(0).name;
    MdObjectXmlRegions.Region attrComment =
      MdObjectXmlRegions.findDirectChildOfNamedChildObjectPropertiesRegion(
        xmlBefore, "Catalog", "Attribute", firstName, "Comment");
    assertThat(attrComment.isValid()).isTrue();

    dto.attributes.get(0).comment = "[attr-comment-test] " + dto.attributes.get(0).comment;
    MdObjectPropertiesEdit.writeDto(copy, SchemaVersion.V2_20, dto);

    String xmlAfter = Files.readString(copy, StandardCharsets.UTF_8);
    int delta = xmlAfter.length() - xmlBefore.length();
    assertThat(xmlAfter.substring(0, attrComment.start())).isEqualTo(xmlBefore.substring(0, attrComment.start()));
    assertThat(xmlAfter.substring(attrComment.end() + delta)).isEqualTo(xmlBefore.substring(attrComment.end()));
    assertThat(MdObjectPropertiesEdit.readDto(copy, SchemaVersion.V2_20).attributes.get(0).comment)
      .isEqualTo(dto.attributes.get(0).comment);
  }

  @Test
  void writeDto_granular_commentAndHierarchical_roundTrip() throws Exception {
    Path src = Ssl31SubmodulePaths.anyCatalogObjectXml();
    Path copy = tempDir.resolve(src.getFileName());
    Files.copy(src, copy);

    MdObjectPropertiesDto dto = MdObjectPropertiesEdit.readDto(copy, SchemaVersion.V2_20);
    dto.comment = "[mix] " + dto.comment;
    dto.catalog.hierarchical = !dto.catalog.hierarchical;
    MdObjectPropertiesEdit.writeDto(copy, SchemaVersion.V2_20, dto);

    MdObjectPropertiesDto after = MdObjectPropertiesEdit.readDto(copy, SchemaVersion.V2_20);
    assertThat(after.comment).isEqualTo(dto.comment);
    assertThat(after.catalog.hierarchical).isEqualTo(dto.catalog.hierarchical);
  }

  @Test
  void writeDto_onlyCommentChange_preservesBytesOutsideCommentElement() throws Exception {
    Path src = Ssl31SubmodulePaths.anyCatalogObjectXml();
    Path copy = tempDir.resolve(src.getFileName());
    Files.copy(src, copy);

    String xmlBefore = Files.readString(copy, StandardCharsets.UTF_8);
    MdObjectXmlRegions.Region comment = MdObjectXmlRegions.findObjectPropertiesCommentRegion(xmlBefore, "Catalog");
    assertThat(comment.isValid()).isTrue();

    MdObjectPropertiesDto dto = MdObjectPropertiesEdit.readDto(copy, SchemaVersion.V2_20);
    dto.comment = "[md-sparrow-test] " + dto.comment;
    MdObjectPropertiesEdit.writeDto(copy, SchemaVersion.V2_20, dto);

    String xmlAfter = Files.readString(copy, StandardCharsets.UTF_8);
    int delta = xmlAfter.length() - xmlBefore.length();
    assertThat(xmlAfter.substring(0, comment.start())).isEqualTo(xmlBefore.substring(0, comment.start()));
    assertThat(xmlAfter.substring(comment.end() + delta)).isEqualTo(xmlBefore.substring(comment.end()));
    assertThat(MdObjectPropertiesEdit.readDto(copy, SchemaVersion.V2_20).comment).isEqualTo(dto.comment);
  }

  @Test
  void readWrite_allBoilerplateKinds_supportBasicSynonymAndComment() throws Exception {
    Path cfRoot = tempDir.resolve("cf");
    EmptyCfScaffold.writeEmptyTree(
      cfRoot,
      CfLayout.DEFAULT_CONFIGURATION_NAME,
      "",
      "",
      "",
      SchemaVersion.V2_20
    );
    Path configurationXml = cfRoot.resolve("Configuration.xml");
    int idx = 100;
    for (MdObjectAddType type : MdObjectAddType.values()) {
      String name = type.namePrefix() + idx++;
      MdObjectAdd.add(configurationXml, name, SchemaVersion.V2_20, type);
      Path objectXml = CfObjectPathResolver.objectXml(cfRoot, type.configurationXmlTag(), name).orElseThrow();
      MdObjectPropertiesDto dto = MdObjectPropertiesEdit.readDto(objectXml, SchemaVersion.V2_20);
      assertThat(dto.kind).isEqualTo(kindToDtoKind(type));
      dto.synonymRu = "[all-kinds] " + (dto.synonymRu == null ? "" : dto.synonymRu);
      dto.comment = "[all-kinds] " + (dto.comment == null ? "" : dto.comment);
      MdObjectPropertiesEdit.writeDto(objectXml, SchemaVersion.V2_20, dto);
      MdObjectPropertiesDto after = MdObjectPropertiesEdit.readDto(objectXml, SchemaVersion.V2_20);
      assertThat(after.synonymRu).isEqualTo(dto.synonymRu);
      assertThat(after.comment).isEqualTo(dto.comment);
    }
  }

  @Test
  void readDto_report_fillsReportProperties() throws Exception {
    Path any = Ssl31SubmodulePaths.anyReportObjectXml();
    MdObjectPropertiesDto dto = MdObjectPropertiesEdit.readDto(any, SchemaVersion.V2_20);
    assertThat(dto.kind).isEqualTo("report");
    assertThat(dto.report).isNotNull();
    // ObjectBelonging пишется только в расширениях, у объекта конфигурации его нет
    assertThat(dto.report.defaultForm).isNotBlank();
    assertThat(dto.report.mainDataCompositionSchema).startsWith("Report.");
  }

  @Test
  void readDto_dataProcessor_fillsReportProperties_withoutReportOnlyFields() throws Exception {
    Path any = Ssl31SubmodulePaths.anyDataProcessorObjectXml();
    MdObjectPropertiesDto dto = MdObjectPropertiesEdit.readDto(any, SchemaVersion.V2_20);
    assertThat(dto.kind).isEqualTo("dataProcessor");
    assertThat(dto.report).isNotNull();
    // схемы компоновки и хранилищ у обработки нет
    assertThat(dto.report.mainDataCompositionSchema).isNull();
    assertThat(dto.report.variantsStorage).isNull();
  }

  @Test
  void writeDto_roundTrip_report_keepsProperties() throws Exception {
    assertReportKindRoundTrip(Ssl31SubmodulePaths.anyReportObjectXml());
  }

  @Test
  void writeDto_roundTrip_dataProcessor_keepsProperties() throws Exception {
    assertReportKindRoundTrip(Ssl31SubmodulePaths.anyDataProcessorObjectXml());
  }

  @Test
  void writeDto_report_onlyKindScalarChanged_isNotSkippedAsEqual() throws Exception {
    // равенство DTO решает, писать ли файл: без учёта блока вида правка одного
    // свойства отчёта молча терялась
    Path src = Ssl31SubmodulePaths.anyReportObjectXml();
    Path copy = tempDir.resolve(src.getFileName());
    Files.copy(src, copy);

    MdObjectPropertiesDto dto = MdObjectPropertiesEdit.readDto(copy, SchemaVersion.V2_20);
    boolean expected = !dto.report.useStandardCommands;
    dto.report.useStandardCommands = expected;
    MdObjectPropertiesEdit.writeDto(copy, SchemaVersion.V2_20, dto);

    MdObjectPropertiesDto after = MdObjectPropertiesEdit.readDto(copy, SchemaVersion.V2_20);
    assertThat(after.report.useStandardCommands).isEqualTo(expected);
  }

  @Test
  void writeDto_attributeSynonymChanged_keepsXmlIntact() throws Exception {
    // правка синонима реквизита попадала в список изменений дважды: вторая правка резала уже
    // изменённый XML по старым смещениям и рвала закрывающий тег
    Path src = Ssl31SubmodulePaths.anyChartOfAccountsObjectXml();
    Path copy = tempDir.resolve(src.getFileName());
    Files.copy(src, copy);

    MdObjectPropertiesDto dto = MdObjectPropertiesEdit.readDto(copy, SchemaVersion.V2_20);
    String changed = dto.attributes.getFirst().synonymRu + " (изменён)";
    dto.attributes.getFirst().synonymRu = changed;
    MdObjectPropertiesEdit.writeDto(copy, SchemaVersion.V2_20, dto);

    String xml = Files.readString(copy, StandardCharsets.UTF_8);
    assertThat(xml).doesNotContain("</Synonym>nym");
    assertThat(MdObjectPropertiesEdit.readDto(copy, SchemaVersion.V2_20).attributes.getFirst().synonymRu)
      .isEqualTo(changed);
  }

  @Test
  void readWriteDto_sessionParameter_roundTripsScalars() throws Exception {
    Path copy = copyAnyObject("SessionParameters");

    MdObjectPropertiesDto dto = MdObjectPropertiesEdit.readDto(copy, SchemaVersion.V2_20);
    assertThat(dto.sessionParameter).as("блок параметра сеанса").isNotNull();
    dto.comment = "Комментарий из теста";
    MdObjectPropertiesEdit.writeDto(copy, SchemaVersion.V2_20, dto);

    assertThat(MdObjectPropertiesEdit.readDto(copy, SchemaVersion.V2_20).comment).isEqualTo("Комментарий из теста");
  }

  @Test
  void writeDto_scheduledJob_changedScalarIsWritten() throws Exception {
    Path copy = copyAnyObject("ScheduledJobs");

    MdObjectPropertiesDto dto = MdObjectPropertiesEdit.readDto(copy, SchemaVersion.V2_20);
    assertThat(dto.scheduledJob).as("блок регламентного задания").isNotNull();
    boolean expected = !dto.scheduledJob.use;
    dto.scheduledJob.use = expected;
    dto.scheduledJob.key = "КлючИзТеста";
    MdObjectPropertiesEdit.writeDto(copy, SchemaVersion.V2_20, dto);

    MdObjectPropertiesDto after = MdObjectPropertiesEdit.readDto(copy, SchemaVersion.V2_20);
    assertThat(after.scheduledJob.use).isEqualTo(expected);
    assertThat(after.scheduledJob.key).isEqualTo("КлючИзТеста");
  }

  @Test
  void writeDto_commonCommand_changedScalarIsWritten() throws Exception {
    Path copy = copyAnyObject("CommonCommands");

    MdObjectPropertiesDto dto = MdObjectPropertiesEdit.readDto(copy, SchemaVersion.V2_20);
    assertThat(dto.commonCommand).as("блок общей команды").isNotNull();
    boolean expected = !dto.commonCommand.modifiesData;
    dto.commonCommand.modifiesData = expected;
    MdObjectPropertiesEdit.writeDto(copy, SchemaVersion.V2_20, dto);

    assertThat(MdObjectPropertiesEdit.readDto(copy, SchemaVersion.V2_20).commonCommand.modifiesData)
      .isEqualTo(expected);
  }

  @Test
  void writeDto_eventSubscription_changedHandlerIsWritten() throws Exception {
    Path copy = copyAnyObject("EventSubscriptions");

    MdObjectPropertiesDto dto = MdObjectPropertiesEdit.readDto(copy, SchemaVersion.V2_20);
    assertThat(dto.eventSubscription).as("блок подписки на событие").isNotNull();
    dto.eventSubscription.handler = "ОбщийМодуль.Тест.Обработчик";
    MdObjectPropertiesEdit.writeDto(copy, SchemaVersion.V2_20, dto);

    assertThat(MdObjectPropertiesEdit.readDto(copy, SchemaVersion.V2_20).eventSubscription.handler)
      .isEqualTo("ОбщийМодуль.Тест.Обработчик");
  }

  @Test
  void writeDto_commonAttribute_changedSeparationIsWritten() throws Exception {
    Path copy = copyAnyObject("CommonAttributes");

    MdObjectPropertiesDto dto = MdObjectPropertiesEdit.readDto(copy, SchemaVersion.V2_20);
    assertThat(dto.commonAttribute).as("блок общего реквизита").isNotNull();
    dto.comment = "Комментарий из теста";
    MdObjectPropertiesEdit.writeDto(copy, SchemaVersion.V2_20, dto);

    MdObjectPropertiesDto after = MdObjectPropertiesEdit.readDto(copy, SchemaVersion.V2_20);
    assertThat(after.comment).isEqualTo("Комментарий из теста");
    assertThat(after.commonAttribute.dataSeparation).isEqualTo(dto.commonAttribute.dataSeparation);
  }

  @Test
  void writeDto_role_keepsRightsFile() throws Exception {
    Path copy = copyAnyObject("Roles");

    MdObjectPropertiesDto dto = MdObjectPropertiesEdit.readDto(copy, SchemaVersion.V2_20);
    assertThat(dto.role).as("блок роли").isNotNull();
    dto.synonymRu = "Роль из теста";
    MdObjectPropertiesEdit.writeDto(copy, SchemaVersion.V2_20, dto);

    assertThat(MdObjectPropertiesEdit.readDto(copy, SchemaVersion.V2_20).synonymRu).isEqualTo("Роль из теста");
  }

  /** Копия произвольного объекта выгрузки ssl31 из подкаталога вида. */
  private Path copyAnyObject(String subdir) throws Exception {
    Path src = Ssl31SubmodulePaths.anyObjectXmlInCfSubdir(subdir);
    Path copy = tempDir.resolve(src.getFileName());
    Files.copy(src, copy);
    return copy;
  }

  @Test
  void writeDto_report_changedScalars_areWritten() throws Exception {
    Path src = Ssl31SubmodulePaths.anyReportObjectXml();
    Path copy = tempDir.resolve(src.getFileName());
    Files.copy(src, copy);

    MdObjectPropertiesDto dto = MdObjectPropertiesEdit.readDto(copy, SchemaVersion.V2_20);
    dto.synonymRu = "[report-test] " + (dto.synonymRu == null ? "" : dto.synonymRu);
    dto.report.useStandardCommands = !dto.report.useStandardCommands;
    dto.report.includeHelpInContents = !dto.report.includeHelpInContents;
    dto.report.explanationRu = "[report-test] пояснение";
    MdObjectPropertiesEdit.writeDto(copy, SchemaVersion.V2_20, dto);

    MdObjectPropertiesDto after = MdObjectPropertiesEdit.readDto(copy, SchemaVersion.V2_20);
    assertThat(after.synonymRu).isEqualTo(dto.synonymRu);
    assertThat(after.report.useStandardCommands).isEqualTo(dto.report.useStandardCommands);
    assertThat(after.report.includeHelpInContents).isEqualTo(dto.report.includeHelpInContents);
    assertThat(after.report.explanationRu).isEqualTo("[report-test] пояснение");
  }

  @Test
  void readDto_chartOfCalculationTypes_fillsDependence() throws Exception {
    Path any = Ssl31SubmodulePaths.anyChartOfCalculationTypesObjectXml();
    MdObjectPropertiesDto dto = MdObjectPropertiesEdit.readDto(any, SchemaVersion.V2_20);
    assertThat(dto.kind).isEqualTo("chartOfCalculationTypes");
    assertThat(dto.chartOfCalculationTypes).isNotNull();
    assertThat(dto.chartOfCalculationTypes.dependenceOnCalculationTypes).isNotBlank();
  }

  @Test
  void writeDto_chartOfCalculationTypes_baseTypesAreWritten() throws Exception {
    Path src = Ssl31SubmodulePaths.anyChartOfCalculationTypesObjectXml();
    Path copy = tempDir.resolve(src.getFileName());
    Files.copy(src, copy);

    MdObjectPropertiesDto dto = MdObjectPropertiesEdit.readDto(copy, SchemaVersion.V2_20);
    boolean expected = !dto.chartOfCalculationTypes.actionPeriodUse;
    dto.chartOfCalculationTypes.actionPeriodUse = expected;
    dto.chartOfCalculationTypes.descriptionLength = "80";
    MdObjectPropertiesEdit.writeDto(copy, SchemaVersion.V2_20, dto);

    MdObjectPropertiesDto after = MdObjectPropertiesEdit.readDto(copy, SchemaVersion.V2_20);
    assertThat(after.chartOfCalculationTypes.actionPeriodUse).isEqualTo(expected);
    assertThat(after.chartOfCalculationTypes.descriptionLength).isEqualTo("80");
    assertThat(after.chartOfCalculationTypes.baseCalculationTypes)
      .isEqualTo(dto.chartOfCalculationTypes.baseCalculationTypes);
  }

  @Test
  void readDto_chartOfAccounts_fillsExtDimensions() throws Exception {
    Path any = Ssl31SubmodulePaths.anyChartOfAccountsObjectXml();
    MdObjectPropertiesDto dto = MdObjectPropertiesEdit.readDto(any, SchemaVersion.V2_20);
    assertThat(dto.kind).isEqualTo("chartOfAccounts");
    assertThat(dto.chartOfAccounts).isNotNull();
    assertThat(dto.chartOfAccounts.maxExtDimensionCount).isNotBlank();
    assertThat(dto.attributes).isNotNull();
  }

  @Test
  void writeDto_chartOfAccounts_scalarChange_isWritten() throws Exception {
    Path src = Ssl31SubmodulePaths.anyChartOfAccountsObjectXml();
    Path copy = tempDir.resolve(src.getFileName());
    Files.copy(src, copy);

    MdObjectPropertiesDto dto = MdObjectPropertiesEdit.readDto(copy, SchemaVersion.V2_20);
    dto.chartOfAccounts.maxExtDimensionCount = "5";
    dto.chartOfAccounts.codeMask = "@@.@@";
    MdObjectPropertiesEdit.writeDto(copy, SchemaVersion.V2_20, dto);

    MdObjectPropertiesDto after = MdObjectPropertiesEdit.readDto(copy, SchemaVersion.V2_20);
    assertThat(after.chartOfAccounts.maxExtDimensionCount).isEqualTo("5");
    assertThat(after.chartOfAccounts.codeMask).isEqualTo("@@.@@");
  }

  @Test
  void readDto_businessProcess_fillsTaskAndFlowchart() throws Exception {
    Path any = Ssl31SubmodulePaths.anyBusinessProcessObjectXml();
    MdObjectPropertiesDto dto = MdObjectPropertiesEdit.readDto(any, SchemaVersion.V2_20);
    assertThat(dto.kind).isEqualTo("businessProcess");
    assertThat(dto.businessProcess).isNotNull();
    assertThat(dto.businessProcess.task).startsWith("Task.");
    assertThat(dto.businessProcess.numberLength).isNotBlank();
  }

  @Test
  void writeDto_businessProcess_scalarChange_isWritten() throws Exception {
    Path src = Ssl31SubmodulePaths.anyBusinessProcessObjectXml();
    Path copy = tempDir.resolve(src.getFileName());
    Files.copy(src, copy);

    MdObjectPropertiesDto dto = MdObjectPropertiesEdit.readDto(copy, SchemaVersion.V2_20);
    boolean expected = !dto.businessProcess.createTaskInPrivilegedMode;
    dto.businessProcess.createTaskInPrivilegedMode = expected;
    MdObjectPropertiesEdit.writeDto(copy, SchemaVersion.V2_20, dto);

    MdObjectPropertiesDto after = MdObjectPropertiesEdit.readDto(copy, SchemaVersion.V2_20);
    assertThat(after.businessProcess.createTaskInPrivilegedMode).isEqualTo(expected);
    assertThat(after.businessProcess.task).isEqualTo(dto.businessProcess.task);
  }

  @Test
  void readDto_task_fillsAddressingProperties() throws Exception {
    Path any = Ssl31SubmodulePaths.anyTaskObjectXml();
    MdObjectPropertiesDto dto = MdObjectPropertiesEdit.readDto(any, SchemaVersion.V2_20);
    assertThat(dto.kind).isEqualTo("task");
    assertThat(dto.task).isNotNull();
    assertThat(dto.task.numberLength).isNotBlank();
    assertThat(dto.attributes).isNotNull();
  }

  @Test
  void writeDto_task_scalarChange_isWritten() throws Exception {
    Path src = Ssl31SubmodulePaths.anyTaskObjectXml();
    Path copy = tempDir.resolve(src.getFileName());
    Files.copy(src, copy);

    MdObjectPropertiesDto dto = MdObjectPropertiesEdit.readDto(copy, SchemaVersion.V2_20);
    boolean expected = !dto.task.checkUnique;
    dto.task.checkUnique = expected;
    dto.task.descriptionLength = "120";
    MdObjectPropertiesEdit.writeDto(copy, SchemaVersion.V2_20, dto);

    MdObjectPropertiesDto after = MdObjectPropertiesEdit.readDto(copy, SchemaVersion.V2_20);
    assertThat(after.task.checkUnique).isEqualTo(expected);
    assertThat(after.task.descriptionLength).isEqualTo("120");
  }

  @Test
  void readDto_chartOfCharacteristicTypes_fillsTypeAndChildren() throws Exception {
    Path any = Ssl31SubmodulePaths.anyChartOfCharacteristicTypesObjectXml();
    MdObjectPropertiesDto dto = MdObjectPropertiesEdit.readDto(any, SchemaVersion.V2_20);
    assertThat(dto.kind).isEqualTo("chartOfCharacteristicTypes");
    assertThat(dto.chartOfCharacteristicTypes).isNotNull();
    assertThat(dto.chartOfCharacteristicTypes.type).isNotNull();
    assertThat(dto.chartOfCharacteristicTypes.codeLength).isNotBlank();
    assertThat(dto.attributes).isNotNull();
  }

  @Test
  void writeDto_chartOfCharacteristicTypes_scalarChange_isWritten() throws Exception {
    Path src = Ssl31SubmodulePaths.anyChartOfCharacteristicTypesObjectXml();
    Path copy = tempDir.resolve(src.getFileName());
    Files.copy(src, copy);

    MdObjectPropertiesDto dto = MdObjectPropertiesEdit.readDto(copy, SchemaVersion.V2_20);
    boolean expected = !dto.chartOfCharacteristicTypes.foldersOnTop;
    dto.chartOfCharacteristicTypes.foldersOnTop = expected;
    dto.chartOfCharacteristicTypes.descriptionLength = "150";
    MdObjectPropertiesEdit.writeDto(copy, SchemaVersion.V2_20, dto);

    MdObjectPropertiesDto after = MdObjectPropertiesEdit.readDto(copy, SchemaVersion.V2_20);
    assertThat(after.chartOfCharacteristicTypes.foldersOnTop).isEqualTo(expected);
    assertThat(after.chartOfCharacteristicTypes.descriptionLength).isEqualTo("150");
    assertThat(after.attributes.size()).isEqualTo(dto.attributes.size());
  }

  @Test
  void readDto_exchangePlan_fillsProperties() throws Exception {
    Path any = Ssl31SubmodulePaths.anyExchangePlanObjectXml();
    MdObjectPropertiesDto dto = MdObjectPropertiesEdit.readDto(any, SchemaVersion.V2_20);
    assertThat(dto.kind).isEqualTo("exchangePlan");
    assertThat(dto.exchangePlan).isNotNull();
    assertThat(dto.exchangePlan.codeLength).isNotBlank();
    assertThat(dto.exchangePlan.standardAttributesXml).isNotNull();
    assertThat(dto.attributes).isNotNull();
  }

  @Test
  void writeDto_exchangePlan_onlyKindScalarChanged_isWritten() throws Exception {
    Path src = Ssl31SubmodulePaths.anyExchangePlanObjectXml();
    Path copy = tempDir.resolve(src.getFileName());
    Files.copy(src, copy);

    MdObjectPropertiesDto dto = MdObjectPropertiesEdit.readDto(copy, SchemaVersion.V2_20);
    boolean expected = !dto.exchangePlan.distributedInfoBase;
    dto.exchangePlan.distributedInfoBase = expected;
    dto.exchangePlan.descriptionLength = "50";
    MdObjectPropertiesEdit.writeDto(copy, SchemaVersion.V2_20, dto);

    MdObjectPropertiesDto after = MdObjectPropertiesEdit.readDto(copy, SchemaVersion.V2_20);
    assertThat(after.exchangePlan.distributedInfoBase).isEqualTo(expected);
    assertThat(after.exchangePlan.descriptionLength).isEqualTo("50");
    assertThat(after.attributes.size()).isEqualTo(dto.attributes.size());
  }

  @Test
  void readDto_documentJournal_readsRegisteredDocuments() throws Exception {
    Path any = Ssl31SubmodulePaths.anyDocumentJournalObjectXml();
    MdObjectPropertiesDto dto = MdObjectPropertiesEdit.readDto(any, SchemaVersion.V2_20);
    assertThat(dto.kind).isEqualTo("documentJournal");
    assertThat(dto.documentJournal).isNotNull();
    assertThat(dto.documentJournal.registeredDocuments).isNotEmpty();
    assertThat(dto.documentJournal.registeredDocuments.get(0)).startsWith("Document.");
  }

  @Test
  void writeDto_documentJournal_registeredDocumentsAreWritten() throws Exception {
    Path src = Ssl31SubmodulePaths.anyDocumentJournalObjectXml();
    Path copy = tempDir.resolve(src.getFileName());
    Files.copy(src, copy);

    MdObjectPropertiesDto dto = MdObjectPropertiesEdit.readDto(copy, SchemaVersion.V2_20);
    String removed = dto.documentJournal.registeredDocuments.remove(0);
    MdObjectPropertiesEdit.writeDto(copy, SchemaVersion.V2_20, dto);

    MdObjectPropertiesDto after = MdObjectPropertiesEdit.readDto(copy, SchemaVersion.V2_20);
    assertThat(after.documentJournal.registeredDocuments).doesNotContain(removed);
    assertThat(after.documentJournal.registeredDocuments)
      .isEqualTo(dto.documentJournal.registeredDocuments);
  }

  @Test
  void granularPatch_documentJournalRegisteredDocuments_touchOnlyThatElement() throws Exception {
    Path src = Ssl31SubmodulePaths.anyDocumentJournalObjectXml();
    String xmlBefore = Files.readString(src, StandardCharsets.UTF_8);
    MdObjectPropertiesDto b = MdObjectPropertiesEdit.readDto(src, SchemaVersion.V2_20);
    MdObjectPropertiesDto i = MdObjectPropertiesEdit.readDto(src, SchemaVersion.V2_20);
    i.documentJournal.registeredDocuments.remove(0);

    MdObjectXmlRegions.Region region =
      MdObjectXmlRegions.findDirectChildOfPropertiesRegion(xmlBefore, "DocumentJournal", "RegisteredDocuments");
    assertThat(region.isValid()).isTrue();

    Optional<byte[]> g =
      MdObjectPropertiesGranularPatch.tryApply(xmlBefore, "DocumentJournal", SchemaVersion.V2_20, b, i);
    assertThat(g).isPresent();
    String xmlAfter = new String(g.get(), StandardCharsets.UTF_8);
    int delta = xmlAfter.length() - xmlBefore.length();
    assertThat(xmlAfter.substring(0, region.start())).isEqualTo(xmlBefore.substring(0, region.start()));
    assertThat(xmlAfter.substring(region.end() + delta)).isEqualTo(xmlBefore.substring(region.end()));
  }

  @Test
  void granularPatch_reportScalarChange_touchesOnlyThatElement() throws Exception {
    Path src = Ssl31SubmodulePaths.anyReportObjectXml();
    String xmlBefore = Files.readString(src, StandardCharsets.UTF_8);
    MdObjectPropertiesDto b = MdObjectPropertiesEdit.readDto(src, SchemaVersion.V2_20);
    MdObjectPropertiesDto i = MdObjectPropertiesEdit.readDto(src, SchemaVersion.V2_20);
    i.report.defaultForm = "CommonForm.ДругаяФормаОтчета";

    MdObjectXmlRegions.Region region =
      MdObjectXmlRegions.findDirectChildOfPropertiesRegion(xmlBefore, "Report", "DefaultForm");
    assertThat(region.isValid()).isTrue();

    Optional<byte[]> g = MdObjectPropertiesGranularPatch.tryApply(xmlBefore, "Report", SchemaVersion.V2_20, b, i);
    assertThat(g).isPresent();
    String xmlAfter = new String(g.get(), StandardCharsets.UTF_8);
    int delta = xmlAfter.length() - xmlBefore.length();
    // всё вне изменённого элемента должно остаться байт в байт
    assertThat(xmlAfter.substring(0, region.start())).isEqualTo(xmlBefore.substring(0, region.start()));
    assertThat(xmlAfter.substring(region.end() + delta)).isEqualTo(xmlBefore.substring(region.end()));
    assertThat(xmlAfter).contains("<DefaultForm>CommonForm.ДругаяФормаОтчета</DefaultForm>");
  }

  private void assertReportKindRoundTrip(Path src) throws Exception {
    Path copy = tempDir.resolve(src.getFileName());
    Files.copy(src, copy);

    MdObjectPropertiesDto before = MdObjectPropertiesEdit.readDto(copy, SchemaVersion.V2_20);
    MdObjectPropertiesEdit.writeDto(copy, SchemaVersion.V2_20, before);
    MdObjectPropertiesDto after = MdObjectPropertiesEdit.readDto(copy, SchemaVersion.V2_20);

    assertThat(after.kind).isEqualTo(before.kind);
    assertThat(after.internalName).isEqualTo(before.internalName);
    assertThat(after.report.defaultForm).isEqualTo(before.report.defaultForm);
    assertThat(after.report.objectModule).isEqualTo(before.report.objectModule);
    assertThat(after.report.managerModule).isEqualTo(before.report.managerModule);
    assertThat(after.report.mainDataCompositionSchema).isEqualTo(before.report.mainDataCompositionSchema);
    assertThat(after.report.extendedPresentationRu).isEqualTo(before.report.extendedPresentationRu);
  }

  @Test
  void trySplice_emptySource_returnsEmpty_soCallerFallsBackToFullWrite() throws Exception {
    Path src = Ssl31SubmodulePaths.anyCatalogObjectXml();
    MdObjectPropertiesDto baseline = MdObjectPropertiesEdit.readDto(src, SchemaVersion.V2_20);
    MdObjectPropertiesDto incoming = MdObjectPropertiesEdit.readDto(src, SchemaVersion.V2_20);
    incoming.comment = "[fallback-test] " + incoming.comment;

    Object root = DesignerXml.read(src, SchemaVersion.V2_20);
    assertThat(root).isInstanceOf(JAXBElement.class);
    @SuppressWarnings("unchecked")
    JAXBElement<?> je = (JAXBElement<?>) root;
    MdObjectPropertiesEdit.applyDtoForTest(je, SchemaVersion.V2_20, incoming);

    MdObjectPropertiesDiff.ChangeMask mask = MdObjectPropertiesDiff.computeChangeMask(baseline, incoming);
    Optional<byte[]> bad = MdObjectPropertiesSplice.trySplice("", SchemaVersion.V2_20, je, incoming, mask);
    assertThat(bad).isEmpty();
  }

  private static String linePrefixBeforeTag(String xml, int tagStartOffset) {
    int from = tagStartOffset - 1;
    while (from >= 0 && xml.charAt(from) != '\n' && xml.charAt(from) != '\r') {
      from--;
    }
    from++;
    return xml.substring(from, tagStartOffset);
  }

  private static String kindToDtoKind(MdObjectAddType type) {
    return switch (type) {
      case CATALOG -> "catalog";
      case ENUM -> "enum";
      case CONSTANT -> "constant";
      case DOCUMENT -> "document";
      case REPORT -> "report";
      case DATA_PROCESSOR -> "dataProcessor";
      case TASK -> "task";
      case CHART_OF_ACCOUNTS -> "chartOfAccounts";
      case CHART_OF_CHARACTERISTIC_TYPES -> "chartOfCharacteristicTypes";
      case CHART_OF_CALCULATION_TYPES -> "chartOfCalculationTypes";
      case COMMON_MODULE -> "commonModule";
      case SUBSYSTEM -> "subsystem";
      case SESSION_PARAMETER -> "sessionParameter";
      case EXCHANGE_PLAN -> "exchangePlan";
      case COMMON_ATTRIBUTE -> "commonAttribute";
      case COMMON_PICTURE -> "commonPicture";
      case DOCUMENT_NUMERATOR -> "documentNumerator";
      case EXTERNAL_DATA_SOURCE -> "externalDataSource";
      case ROLE -> "role";
    };
  }
}
