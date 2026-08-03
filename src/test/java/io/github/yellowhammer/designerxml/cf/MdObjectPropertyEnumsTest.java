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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MdObjectPropertyEnumsTest {

  @Test
  void codeSeries_listsOnlyConstantsOfTheFormat() {
    Map<String, List<String>> enums = MdObjectPropertyEnums.forVersion(SchemaVersion.V2_21);

    assertThat(enums.get("chartOfCharacteristicTypes.codeSeries"))
      .containsExactlyInAnyOrder("WHOLE_CHARACTERISTIC_KIND", "WITHIN_SUBORDINATION");
    assertThat(enums.get("catalog.codeSeries"))
      .containsExactlyInAnyOrder("WHOLE_CATALOG", "WITHIN_OWNER_SUBORDINATION", "WITHIN_SUBORDINATION");
    assertThat(enums.get("chartOfCalculationTypes.dependenceOnCalculationTypes"))
      .containsExactlyInAnyOrder("DONT_USE", "ON_ACTION_PERIOD", "ON_REGISTRATION_PERIOD");
  }

  @Test
  void sharedBlocks_unionValuesOfBothKinds() {
    Map<String, List<String>> enums = MdObjectPropertyEnums.forVersion(SchemaVersion.V2_21);

    // отчёт и обработка описаны одним блоком DTO, регистры сведений и накопления - тоже
    assertThat(enums).containsKey("report.objectBelonging");
    assertThat(enums.get("register.writeMode")).containsExactlyInAnyOrder("INDEPENDENT", "RECORDER_SUBORDINATE");
    assertThat(enums.get("register.informationRegisterPeriodicity")).contains("NONPERIODICAL", "SECOND", "RECORDER_POSITION");
  }

  @Test
  void nonEnumProperties_areNotInDictionary() {
    Map<String, List<String>> enums = MdObjectPropertyEnums.forVersion(SchemaVersion.V2_21);

    // ссылка на форму и длина кода - не перечисления, значения им задаёт не модель
    assertThat(enums).doesNotContainKey("catalog.defaultObjectForm");
    assertThat(enums).doesNotContainKey("catalog.codeLength");
  }

  @ParameterizedTest
  @EnumSource(SchemaVersion.class)
  void everyFormatHasDictionary(SchemaVersion version) {
    Map<String, List<String>> enums = MdObjectPropertyEnums.forVersion(version);

    assertThat(enums).as("словарь формата %s", version).isNotEmpty();
    assertThat(enums.get("catalog.editType")).as("способ редактирования в формате %s", version)
      .containsExactlyInAnyOrder("IN_DIALOG", "IN_LIST", "BOTH_WAYS");
    assertThat(enums.values()).allSatisfy(values -> assertThat(values).isNotEmpty());
  }
}
