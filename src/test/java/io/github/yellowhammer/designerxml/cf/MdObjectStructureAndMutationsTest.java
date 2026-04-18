/*
 * This file is a part of md-sparrow.
 *
 * Copyright (c) 2026
 * Ivan Karlo <i.karlo@outlook.com> and contributors
 *
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package io.github.yellowhammer.designerxml.cf;

import io.github.yellowhammer.designerxml.SchemaVersion;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class MdObjectStructureAndMutationsTest {

  @Test
  void readStructure_document_hasNestedContent() throws Exception {
    MdObjectStructureDto dto = MdObjectStructureRead.read(sampleDocumentXml(), SchemaVersion.V2_20);
    assertThat(dto.kind).isEqualTo("document");
    assertThat(dto.attributes).isNotEmpty();
    assertThat(dto.tabularSections).isNotEmpty();
    assertThat(dto.tabularSections.getFirst().attributes).isNotEmpty();
    assertThat(dto.forms).isNotEmpty();
    assertThat(dto.templates).isNotEmpty();
  }

  @Test
  void readStructure_enum_hasValues() throws Exception {
    MdObjectStructureDto dto = MdObjectStructureRead.read(anyObjectXmlInCfSubdir("Enums"), SchemaVersion.V2_20);
    assertThat(dto.kind).isEqualTo("enum");
    assertThat(dto.values).isNotEmpty();
  }

  @Test
  void readStructure_informationRegister_hasDimensionsAndResources() throws Exception {
    MdObjectStructureDto dto = MdObjectStructureRead.read(
      anyObjectXmlInCfSubdir("InformationRegisters"),
      SchemaVersion.V2_20);
    assertThat(dto.kind).isEqualTo("informationRegister");
    assertThat(dto.dimensions).isNotEmpty();
    assertThat(dto.resources).isNotEmpty();
  }

  @Test
  void readStructure_externalDataProcessor_hasForms() throws Exception {
    MdObjectStructureDto dto = MdObjectStructureRead.read(
      sampleExternalDataProcessorXml(),
      SchemaVersion.V2_20);
    assertThat(dto.kind).isEqualTo("externalDataProcessor");
    assertThat(dto.forms).isNotEmpty();
  }

  @Test
  void readStructure_externalReport_hasTemplates() throws Exception {
    MdObjectStructureDto dto = MdObjectStructureRead.read(sampleExternalReportXml(), SchemaVersion.V2_20);
    assertThat(dto.kind).isEqualTo("externalReport");
    assertThat(dto.templates).isNotEmpty();
  }

  @Test
  void addAttribute_isGranular() throws Exception {
    Path copy = copyToTemp(sampleDocumentXml());
    String before = Files.readString(copy, StandardCharsets.UTF_8);

    MdObjectChildMutations.addAttribute(copy, SchemaVersion.V2_20, "НовыйРеквизитДляТеста");

    String after = Files.readString(copy, StandardCharsets.UTF_8);
    MdObjectStructureDto dto = MdObjectStructureRead.read(copy, SchemaVersion.V2_20);
    assertThat(dto.attributes).extracting(a -> a.name).contains("НовыйРеквизитДляТеста");
    assertLowNoise(before, after);
  }

  @Test
  void addTabularAttribute_isGranular() throws Exception {
    Path copy = copyToTemp(sampleDocumentXml());
    String before = Files.readString(copy, StandardCharsets.UTF_8);

    MdObjectChildMutations.addTabularAttribute(copy, SchemaVersion.V2_20, "СчетаНаОплату", "НовыйРеквизитТЧДляТеста");

    String after = Files.readString(copy, StandardCharsets.UTF_8);
    MdObjectStructureDto dto = MdObjectStructureRead.read(copy, SchemaVersion.V2_20);
    assertThat(dto.tabularSections)
      .filteredOn(ts -> "СчетаНаОплату".equals(ts.name))
      .singleElement()
      .extracting(ts -> ts.attributes)
      .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.list(MdObjectStructureDto.MdNodeDto.class))
      .extracting(a -> a.name)
      .contains("НовыйРеквизитТЧДляТеста");
    assertLowNoise(before, after);
  }

  private static void assertLowNoise(String before, String after) {
    int prefix = commonPrefix(before, after);
    int suffix = commonSuffix(before, after, prefix);
    int changedBefore = before.length() - prefix - suffix;
    int changedAfter = after.length() - prefix - suffix;
    assertThat(changedBefore).isLessThan(6000);
    assertThat(changedAfter).isLessThan(6000);
  }

  private static int commonPrefix(String left, String right) {
    int max = Math.min(left.length(), right.length());
    int i = 0;
    while (i < max && left.charAt(i) == right.charAt(i)) {
      i++;
    }
    return i;
  }

  private static int commonSuffix(String left, String right, int prefix) {
    int max = Math.min(left.length() - prefix, right.length() - prefix);
    int i = 0;
    while (i < max && left.charAt(left.length() - 1 - i) == right.charAt(right.length() - 1 - i)) {
      i++;
    }
    return i;
  }

  private static Path sampleDocumentXml() {
    String fixturesRoot = System.getProperty("fixtures.ssl31.root");
    return Path.of(fixturesRoot)
      .resolve("src")
      .resolve("cf")
      .resolve("Documents")
      .resolve("_ДемоЗаказПокупателя.xml");
  }

  private static Path copyToTemp(Path source) throws IOException {
    Path dir = Files.createTempDirectory("md-child-mutations-");
    Path target = dir.resolve(source.getFileName().toString());
    Files.copy(source, target);
    return target;
  }

  private static Path anyObjectXmlInCfSubdir(String subdir) throws IOException {
    String fixturesRoot = System.getProperty("fixtures.ssl31.root");
    Path dir = Path.of(fixturesRoot).resolve("src").resolve("cf").resolve(subdir);
    try (Stream<Path> stream = Files.list(dir)) {
      Path found = stream
        .filter(Files::isRegularFile)
        .filter(p -> p.getFileName().toString().endsWith(".xml"))
        .sorted()
        .findFirst()
        .orElseThrow();
      return found;
    }
  }

  private static Path sampleExternalDataProcessorXml() {
    String fixturesRoot = System.getProperty("fixtures.ssl31.root");
    return Path.of(fixturesRoot)
      .resolve("src")
      .resolve("epf")
      .resolve("_ДемоВводНаОснованииОприходованийТоваров")
      .resolve("_ДемоВводНаОснованииОприходованийТоваров.xml");
  }

  private static Path sampleExternalReportXml() {
    String fixturesRoot = System.getProperty("fixtures.ssl31.root");
    return Path.of(fixturesRoot)
      .resolve("src")
      .resolve("erf")
      .resolve("_ДемоОтчетНоменклатураОперации")
      .resolve("_ДемоНоменклатураОперации.xml");
  }
}
