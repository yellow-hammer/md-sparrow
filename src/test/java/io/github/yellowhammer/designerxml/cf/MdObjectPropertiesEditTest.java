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
    NewConfigurationXml.writeConfiguratorEmptyTree(
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
