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
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Типизированные свойства читаются и пишутся во ВСЕХ форматах выгрузки, а не только в текущем:
 * объект берётся из эталона нужного формата, а мосты работают JAXB-рефлексией по модели версии.
 */
class MdObjectPropertiesVersionsTest {

  @TempDir
  Path tempDir;

  @ParameterizedTest
  @EnumSource(SchemaVersion.class)
  void reportAndDataProcessor_roundTripInEveryFormat(SchemaVersion version) throws Exception {
    MdObjectPropertiesDto report = roundTrip(MdObjectAddType.REPORT, version);
    assertThat(report.report).as("блок отчёта в формате %s", version).isNotNull();

    MdObjectPropertiesDto processor = roundTrip(MdObjectAddType.DATA_PROCESSOR, version);
    assertThat(processor.report).as("блок обработки в формате %s", version).isNotNull();
    assertThat(processor.report.mainDataCompositionSchema).isNull();
  }

  @ParameterizedTest
  @EnumSource(SchemaVersion.class)
  void exchangePlanAndCharts_roundTripInEveryFormat(SchemaVersion version) throws Exception {
    assertThat(roundTrip(MdObjectAddType.EXCHANGE_PLAN, version).exchangePlan).isNotNull();
    assertThat(roundTrip(MdObjectAddType.CHART_OF_CHARACTERISTIC_TYPES, version).chartOfCharacteristicTypes)
      .isNotNull();
    assertThat(roundTrip(MdObjectAddType.CHART_OF_ACCOUNTS, version).chartOfAccounts).isNotNull();
    assertThat(roundTrip(MdObjectAddType.CHART_OF_CALCULATION_TYPES, version).chartOfCalculationTypes).isNotNull();
  }

  @ParameterizedTest
  @EnumSource(SchemaVersion.class)
  void task_roundTripInEveryFormat(SchemaVersion version) throws Exception {
    MdObjectPropertiesDto dto = roundTrip(MdObjectAddType.TASK, version);
    assertThat(dto.task).as("блок задачи в формате %s", version).isNotNull();
    assertThat(dto.task.numberLength).isNotBlank();
  }

  @ParameterizedTest
  @EnumSource(SchemaVersion.class)
  void changedScalar_isWrittenInEveryFormat(SchemaVersion version) throws Exception {
    Path objectXml = writeGolden(MdObjectAddType.REPORT, version, "ОтчетВерсия");
    MdObjectPropertiesDto dto = MdObjectPropertiesEdit.readDto(objectXml, version);
    boolean expected = !dto.report.useStandardCommands;
    dto.report.useStandardCommands = expected;
    dto.synonymRu = "Отчёт формата " + version.metadataObjectVersionAttribute();
    MdObjectPropertiesEdit.writeDto(objectXml, version, dto);

    MdObjectPropertiesDto after = MdObjectPropertiesEdit.readDto(objectXml, version);
    assertThat(after.report.useStandardCommands).isEqualTo(expected);
    assertThat(after.synonymRu).isEqualTo(dto.synonymRu);
  }

  /** Пишет объект из эталона формата и возвращает DTO после записи без изменений. */
  private MdObjectPropertiesDto roundTrip(MdObjectAddType type, SchemaVersion version) throws Exception {
    Path objectXml = writeGolden(type, version, type.namePrefix() + "Проверка");
    MdObjectPropertiesDto before = MdObjectPropertiesEdit.readDto(objectXml, version);
    MdObjectPropertiesEdit.writeDto(objectXml, version, before);
    MdObjectPropertiesDto after = MdObjectPropertiesEdit.readDto(objectXml, version);
    assertThat(after.internalName).isEqualTo(before.internalName);
    assertThat(after.synonymRu).isEqualTo(before.synonymRu);
    return after;
  }

  private Path writeGolden(MdObjectAddType type, SchemaVersion version, String name) throws Exception {
    String xml = GoldenScaffold.generateObject(type, name, version);
    // имя файла обязано совпадать с именем объекта, поэтому каждый случай в своём каталоге
    Path dir = Files.createDirectories(tempDir.resolve(version.name()).resolve(type.name()));
    Path objectXml = dir.resolve(name + ".xml");
    Files.writeString(objectXml, xml, StandardCharsets.UTF_8);
    return objectXml;
  }
}
