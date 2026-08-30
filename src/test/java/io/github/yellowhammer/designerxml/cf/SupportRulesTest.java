/*
 * This file is a part of md-sparrow.
 *
 * Copyright (c) 2026
 * Ivan Karlo <i.karlo@outlook.com> and contributors
 *
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package io.github.yellowhammer.designerxml.cf;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.yellowhammer.designerxml.SchemaVersion;
import io.github.yellowhammer.designerxml.Ssl31SubmodulePaths;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

class SupportRulesTest {

  private static final String UUID_A = "aaaaaaaa-1111-4111-8111-111111111111";
  private static final String UUID_B = "bbbbbbbb-2222-4222-8222-222222222222";
  private static final String UUID_C = "cccccccc-3333-4333-8333-333333333333";

  /** Свой файл правил: поставщик, три записи с разными режимами. */
  private static byte[] rulesFile(String globalFlag, String modeA, String modeB, String modeC) {
    String text = "﻿{6," + globalFlag + ",1,"
      + "dddddddd-4444-4444-8444-444444444444,0,"
      + "eeeeeeee-5555-4555-8555-555555555555,"
      + "\"1.0.0.1\",\"Поставщик \"\"Тест\"\"\",\"ТестоваяПоставка\",3,"
      + modeA + ",0," + UUID_A + "," + UUID_A + ","
      + modeB + ",0," + UUID_B + "," + UUID_B + ","
      + modeC + ",0," + UUID_C + "," + UUID_C + ","
      + "0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}";
    return text.getBytes(StandardCharsets.UTF_8);
  }

  @Test
  void readsVendorFlagsAndRawModes() {
    SupportRules.Rules rules = SupportRules.parse(rulesFile("0", "0", "1", "2"));
    assertThat(rules.vendor).isEqualTo("Поставщик \"Тест\"");
    assertThat(rules.version).isEqualTo("1.0.0.1");
    assertThat(rules.rulesEnabled).isTrue();
    assertThat(rules.modeByUuid.get(UUID_A)).isEqualTo(0);
    assertThat(rules.modeByUuid.get(UUID_B)).isEqualTo(1);
    assertThat(rules.modeByUuid.get(UUID_C)).isEqualTo(2);
  }

  @Test
  void effectiveStatesFollowRulesAndGlobalFlag() {
    SupportRules.Rules open = SupportRules.parse(rulesFile("0", "0", "1", "2"));
    assertThat(open.effectiveState(UUID_A)).isEqualTo("locked");
    assertThat(open.effectiveState(UUID_B)).isEqualTo("editable");
    assertThat(open.effectiveState(UUID_C)).isNull();
    assertThat(open.effectiveState("00000000-0000-4000-8000-000000000000")).isNull();
    assertThat(open.configurationState()).isEqualTo("editable");

    // Глобальный флаг закрыт: правила скрыты, всё на поддержке заблокировано
    SupportRules.Rules closed = SupportRules.parse(rulesFile("1", "0", "1", "2"));
    assertThat(closed.effectiveState(UUID_B)).isEqualTo("locked");
    assertThat(closed.effectiveState(UUID_C)).isNull();
    assertThat(closed.configurationState()).isEqualTo("locked");
  }

  @Test
  void ssl31DemoBaseIsOnFullSupport() throws Exception {
    SupportRules.Rules rules = SupportRules.read(Ssl31SubmodulePaths.projectRoot().resolve("src/cf"));
    assertThat(rules.vendor).contains("1С");
    assertThat(rules.rulesEnabled).isFalse();
    assertThat(rules.configurationState()).isEqualTo("locked");
    assertThat(rules.effectiveState("4e1437bf-948b-4b05-9341-a2df3f301d7f")).isEqualTo("locked");
  }

  @Test
  void enableRulesOpensFlagsAndLocksEveryObject(@TempDir Path dir) throws Exception {
    Path root = dir.resolve("cf");
    Files.createDirectories(root.resolve("Ext"));
    byte[] before = rulesFile("1", "1", "1", "1");
    Files.write(SupportRules.rulesPath(root), before);

    SupportRules.enableRules(root);

    byte[] after = Files.readAllBytes(SupportRules.rulesPath(root));
    assertThat(after).hasSameSizeAs(before);
    SupportRules.Rules rules = SupportRules.parse(after);
    assertThat(rules.rulesEnabled).isTrue();
    assertThat(rules.effectiveState(UUID_A)).isEqualTo("locked");
    assertThat(rules.modeByUuid.get(UUID_A)).isEqualTo(0);
  }

  @Test
  void setObjectModeChangesOneDigitOnly(@TempDir Path dir) throws Exception {
    Path root = dir.resolve("cf");
    Files.createDirectories(root.resolve("Ext"));
    byte[] before = rulesFile("0", "0", "0", "0");
    Files.write(SupportRules.rulesPath(root), before);

    SupportRules.setObjectMode(root, UUID_B, SupportRules.MODE_EDITABLE);

    byte[] after = Files.readAllBytes(SupportRules.rulesPath(root));
    assertThat(after).hasSameSizeAs(before);
    int diffs = 0;
    for (int i = 0; i < before.length; i++) {
      if (before[i] != after[i]) {
        diffs++;
      }
    }
    assertThat(diffs).isEqualTo(1);
    SupportRules.Rules rules = SupportRules.parse(after);
    assertThat(rules.effectiveState(UUID_B)).isEqualTo("editable");
    assertThat(rules.effectiveState(UUID_A)).isEqualTo("locked");
  }

  @Test
  void setObjectModeIsRefusedWhileConfigurationIsFullyLocked(@TempDir Path dir) throws Exception {
    Path root = dir.resolve("cf");
    Files.createDirectories(root.resolve("Ext"));
    byte[] before = rulesFile("1", "1", "1", "1");
    Files.write(SupportRules.rulesPath(root), before);

    assertThatThrownBy(() -> SupportRules.setObjectMode(root, UUID_A, SupportRules.MODE_EDITABLE))
      .isInstanceOf(IllegalStateException.class)
      .hasMessageContaining("возможность изменения");
    assertThat(Files.readAllBytes(SupportRules.rulesPath(root))).isEqualTo(before);
  }

  @Test
  void writeIntoSupportedObjectIsRefusedAndFilesUntouched() throws Exception {
    Path objectXml = Ssl31SubmodulePaths.projectRoot().resolve("src/cf/Catalogs/_ДемоГруппыДоступаПартнеров.xml");
    byte[] before = Files.readAllBytes(objectXml);
    MdObjectPropertiesDto dto = MdObjectPropertiesEdit.readDto(objectXml, SchemaVersion.V2_20);
    dto.comment = "правка запрещена";
    assertThatThrownBy(() -> MdObjectPropertiesEdit.writeDto(objectXml, SchemaVersion.V2_20, dto))
      .isInstanceOf(IllegalStateException.class)
      .hasMessageContaining("на поддержке");
    assertThat(Files.readAllBytes(objectXml)).isEqualTo(before);
  }

  @Test
  void roleRightsEditOnSupportedRoleIsRefusedAndFilesUntouched() throws Exception {
    Path roleXml = Ssl31SubmodulePaths.projectRoot().resolve("src/cf/Roles/_ДемоБазовыеПраваБСП.xml");
    Path rightsXml = RoleRightsFile.rightsPath(roleXml);
    byte[] before = Files.readAllBytes(rightsXml);
    RoleRightsFile.Edit edit = new RoleRightsFile.Edit();
    edit.object = "Catalog.Валюты";
    edit.right = "Read";
    edit.value = true;
    assertThatThrownBy(() -> RoleRightsFile.applyEdits(roleXml, java.util.List.of(edit)))
      .isInstanceOf(IllegalStateException.class)
      .hasMessageContaining("на поддержке");
    assertThat(Files.readAllBytes(rightsXml)).isEqualTo(before);
  }
}
