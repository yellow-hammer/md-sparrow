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

    SupportRules.enableRules(root, SupportRules.MODE_NOT_EDITABLE, null);

    byte[] after = Files.readAllBytes(SupportRules.rulesPath(root));
    assertThat(after).hasSameSizeAs(before);
    SupportRules.Rules rules = SupportRules.parse(after);
    assertThat(rules.rulesEnabled).isTrue();
    assertThat(rules.effectiveState(UUID_A)).isEqualTo("locked");
    assertThat(rules.modeByUuid.get(UUID_A)).isEqualTo(0);
  }

  @Test
  void enableRulesWithEditableDefaultOpensObjects(@TempDir Path dir) throws Exception {
    Path root = dir.resolve("cf");
    Files.createDirectories(root.resolve("Ext"));
    Files.write(SupportRules.rulesPath(root), rulesFile("1", "1", "1", "1"));

    SupportRules.enableRules(root, SupportRules.MODE_EDITABLE, null);

    SupportRules.Rules rules = SupportRules.parse(Files.readAllBytes(SupportRules.rulesPath(root)));
    assertThat(rules.rulesEnabled).isTrue();
    assertThat(rules.effectiveState(UUID_A)).isEqualTo("editable");
  }

  @Test
  void setObjectModeChangesOneDigitOnly(@TempDir Path dir) throws Exception {
    Path root = dir.resolve("cf");
    Files.createDirectories(root.resolve("Ext"));
    byte[] before = rulesFile("0", "0", "0", "0");
    Files.write(SupportRules.rulesPath(root), before);

    SupportRules.setObjectMode(root, UUID_B, SupportRules.MODE_EDITABLE, null);

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

    assertThatThrownBy(() -> SupportRules.setObjectMode(root, UUID_A, SupportRules.MODE_EDITABLE, null))
      .isInstanceOf(IllegalStateException.class)
      .hasMessageContaining("возможность изменения");
    assertThat(Files.readAllBytes(SupportRules.rulesPath(root))).isEqualTo(before);
  }

  @Test
  void objectStateFollowsRulesOfItsDump() throws Exception {
    Path objectXml = Ssl31SubmodulePaths.projectRoot()
      .resolve("src/cf/Catalogs/_ДемоГруппыДоступаПартнеров.xml");
    assertThat(SupportRules.objectState(objectXml)).isEqualTo("locked");
    SupportRules.Rules rules = SupportRules.rulesFor(objectXml);
    assertThat(rules).isNotNull();
    assertThat(rules.vendor).contains("1С");
  }

  @Test
  void formAndTemplateCarryTheirOwnRule() throws Exception {
    // У формы и макета свои записи в правилах: режим объекта за них не решает
    Path objectXml = Ssl31SubmodulePaths.projectRoot()
      .resolve("src/cf/Catalogs/_ДемоБанковскиеСчета.xml");
    Path formXml = Ssl31SubmodulePaths.projectRoot()
      .resolve("src/cf/Catalogs/_ДемоБанковскиеСчета/Forms/ФормаЭлемента.xml");
    Path formContent = Ssl31SubmodulePaths.projectRoot()
      .resolve("src/cf/Catalogs/_ДемоБанковскиеСчета/Forms/ФормаЭлемента/Ext/Form.xml");

    assertThat(SupportRules.supportSubjectXml(formXml)).isEqualTo(formXml);
    assertThat(SupportRules.supportSubjectXml(formContent)).isEqualTo(formXml);
    assertThat(SupportRules.supportSubjectXml(objectXml)).isEqualTo(objectXml);

    java.util.Map<String, String> states = SupportRules.statesForObject(objectXml);
    assertThat(states).containsEntry("Catalogs/_ДемоБанковскиеСчета.xml", "locked");
    assertThat(states).containsEntry("Catalogs/_ДемоБанковскиеСчета/Forms/ФормаЭлемента.xml", "locked");
  }

  @Test
  void formOfSupportedObjectIsLockedThroughItsOwner() throws Exception {
    // У формы свой uuid, в правилах его нет: состояние берётся у объекта-владельца
    Path objectXml = Ssl31SubmodulePaths.projectRoot()
      .resolve("src/cf/Catalogs/_ДемоБанковскиеСчета.xml");
    for (String relative : java.util.List.of(
      "src/cf/Catalogs/_ДемоБанковскиеСчета/Ext/ObjectModule.bsl",
      "src/cf/Catalogs/_ДемоБанковскиеСчета/Ext/ManagerModule.bsl")) {
      Path inside = Ssl31SubmodulePaths.projectRoot().resolve(relative);
      assertThat(SupportRules.supportSubjectXml(inside)).isEqualTo(objectXml);
      assertThat(SupportRules.objectState(inside)).isEqualTo("locked");
    }
    Path formXml = Ssl31SubmodulePaths.projectRoot()
      .resolve("src/cf/Catalogs/_ДемоБанковскиеСчета/Forms/ФормаЭлемента/Ext/Form.xml");
    assertThatThrownBy(() -> SupportRules.ensureEditable(formXml))
      .isInstanceOf(IllegalStateException.class)
      .hasMessageContaining("на поддержке");
  }

  @Test
  void emptyVendorPayloadDirectoryBlocksRuleWrites(@TempDir Path dir) throws Exception {
    // Каталог поставок заведён, а файла поставки нет: следующая выгрузка платформы его не вернёт
    Path root = dir.resolve("cf");
    Files.createDirectories(SupportRules.vendorPayloadDir(root));
    byte[] before = rulesFile("1", "1", "1", "1");
    Files.write(SupportRules.rulesPath(root), before);

    assertThatThrownBy(() -> SupportRules.enableRules(root, SupportRules.MODE_NOT_EDITABLE, null))
      .isInstanceOf(IllegalStateException.class)
      .hasMessageContaining("нет ни одного файла поставки");
    assertThat(Files.readAllBytes(SupportRules.rulesPath(root))).isEqualTo(before);

    Files.writeString(SupportRules.vendorPayloadDir(root).resolve("Поставка.cf"), "payload");
    SupportRules.enableRules(root, SupportRules.MODE_NOT_EDITABLE, null);
    assertThat(SupportRules.parse(Files.readAllBytes(SupportRules.rulesPath(root))).rulesEnabled).isTrue();
  }

  @Test
  void removeSupportDropsVendorPayloadToo(@TempDir Path dir) throws Exception {
    Path root = dir.resolve("cf");
    Files.createDirectories(SupportRules.vendorPayloadDir(root));
    Files.write(SupportRules.rulesPath(root), rulesFile("1", "1", "1", "1"));
    Files.writeString(SupportRules.vendorPayloadDir(root).resolve("Поставка.cf"), "payload");

    SupportRules.removeSupport(root, null);

    assertThat(Files.exists(SupportRules.rulesPath(root))).isFalse();
    assertThat(Files.exists(SupportRules.vendorPayloadDir(root))).isFalse();
  }

  @Test
  void modeForFileTouchesOnlySubjectUnlessChildrenAsked(@TempDir Path dir) throws Exception {
    // Объект и его форма - разные субъекты правила: режим объекта форму не трогает
    String eol = System.lineSeparator();
    Path root = dir.resolve("cf");
    Path forms = root.resolve("Catalogs/Товары/Forms");
    Files.createDirectories(forms);
    Files.createDirectories(root.resolve("Ext"));
    Files.writeString(root.resolve("Configuration.xml"), "<MetaDataObject/>");
    Files.writeString(root.resolve("Catalogs/Товары.xml"), "<MetaDataObject>" + eol + "<Catalog uuid=\"" + UUID_A + "\">" + eol);
    Files.writeString(forms.resolve("ФормаЭлемента.xml"), "<MetaDataObject>" + eol + "<Form uuid=\"" + UUID_B + "\">" + eol);
    Files.write(SupportRules.rulesPath(root), rulesFile("0", "0", "0", "0"));

    SupportRules.setModeForFile(root.resolve("Catalogs/Товары.xml"), SupportRules.MODE_EDITABLE, false, null);
    SupportRules.Rules afterObject = SupportRules.parse(Files.readAllBytes(SupportRules.rulesPath(root)));
    assertThat(afterObject.effectiveState(UUID_A)).isEqualTo("editable");
    assertThat(afterObject.effectiveState(UUID_B)).isEqualTo("locked");

    SupportRules.setModeForFile(root.resolve("Catalogs/Товары.xml"), SupportRules.MODE_EDITABLE, true, null);
    SupportRules.Rules withChildren = SupportRules.parse(Files.readAllBytes(SupportRules.rulesPath(root)));
    assertThat(withChildren.effectiveState(UUID_B)).isEqualTo("editable");
  }

  @Test
  void everyElementOfObjectIsRuleSubject() throws Exception {
    // Правило заведено на каждый uuid выгрузки, а не только на файл объекта
    Path objectXml = Ssl31SubmodulePaths.projectRoot()
      .resolve("src/cf/Catalogs/_ДемоБанковскиеСчета.xml");

    java.util.Map<String, String> elements = SupportRules.elementSubjects(objectXml);
    assertThat(elements).containsKey("element:cf-md-attribute:НомерСчета");
    assertThat(elements.keySet()).allMatch(key -> key.startsWith("element:cf-md-"));

    SupportRules.Rules rules = SupportRules.rulesFor(objectXml);
    assertThat(rules).isNotNull();
    assertThat(elements.values()).allMatch(uuid -> rules.modeByUuid.containsKey(uuid));

    java.util.Map<String, String> states = SupportRules.statesForObject(objectXml);
    assertThat(states).containsEntry("element:cf-md-attribute:НомерСчета", "locked");
  }

  @Test
  void modeForElementTouchesOnlyThatElement(@TempDir Path dir) throws Exception {
    String eol = System.lineSeparator();
    Path root = dir.resolve("cf");
    Files.createDirectories(root.resolve("Catalogs"));
    Files.createDirectories(root.resolve("Ext"));
    Files.writeString(root.resolve("Configuration.xml"), "<MetaDataObject/>");
    Files.writeString(root.resolve("Catalogs/Товары.xml"),
      "<MetaDataObject>" + eol
        + "<Catalog uuid=\"" + UUID_A + "\">" + eol
        + "<Properties><Name>Товары</Name></Properties>" + eol
        + "<ChildObjects>" + eol
        + "<Attribute uuid=\"" + UUID_B + "\"><Properties><Name>Артикул</Name></Properties></Attribute>" + eol
        + "<TabularSection uuid=\"" + UUID_C + "\"><Properties><Name>Состав</Name></Properties>" + eol
        + "</TabularSection>" + eol
        + "</ChildObjects></Catalog></MetaDataObject>");
    Files.write(SupportRules.rulesPath(root), rulesFile("0", "0", "0", "0"));
    Path objectXml = root.resolve("Catalogs/Товары.xml");

    assertThat(SupportRules.elementSubjects(objectXml))
      .containsEntry("element:cf-md-attribute:Артикул", UUID_B)
      .containsEntry("element:cf-md-tabular-section:Состав", UUID_C);

    SupportRules.setModeForElement(objectXml, "element:cf-md-attribute:Артикул", SupportRules.MODE_EDITABLE, null);

    SupportRules.Rules rules = SupportRules.parse(Files.readAllBytes(SupportRules.rulesPath(root)));
    assertThat(rules.effectiveState(UUID_B)).isEqualTo("editable");
    assertThat(rules.effectiveState(UUID_A)).isEqualTo("locked");
    assertThat(rules.effectiveState(UUID_C)).isEqualTo("locked");

    // Подчинённые объекта - не только формы и макеты: элементы файла тоже
    SupportRules.setModeForFile(objectXml, SupportRules.MODE_EDITABLE, true, null);
    SupportRules.Rules withChildren = SupportRules.parse(Files.readAllBytes(SupportRules.rulesPath(root)));
    assertThat(withChildren.effectiveState(UUID_C)).isEqualTo("editable");

    assertThatThrownBy(() ->
      SupportRules.setModeForElement(objectXml, "element:cf-md-attribute:Нет", SupportRules.MODE_EDITABLE, null))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("нет элемента");
  }

  @Test
  void lockedElementIsRefusedWhileItsObjectIsEditable(@TempDir Path dir) throws Exception {
    String eol = System.lineSeparator();
    Path root = dir.resolve("cf");
    Files.createDirectories(root.resolve("Catalogs"));
    Files.createDirectories(root.resolve("Ext"));
    Files.writeString(root.resolve("Configuration.xml"), "<MetaDataObject/>");
    Files.writeString(root.resolve("Catalogs/Товары.xml"),
      "<MetaDataObject>" + eol
        + "<Catalog uuid=\"" + UUID_A + "\">" + eol
        + "<Properties><Name>Товары</Name></Properties>" + eol
        + "<ChildObjects>" + eol
        + "<Attribute uuid=\"" + UUID_B + "\"><Properties><Name>Артикул</Name></Properties></Attribute>" + eol
        + "</ChildObjects></Catalog></MetaDataObject>");
    // Объект менять разрешено, его реквизит - нет
    Files.write(SupportRules.rulesPath(root), rulesFile("0", "1", "0", "0"));
    Path objectXml = root.resolve("Catalogs/Товары.xml");

    SupportRules.ensureEditable(objectXml);
    assertThatThrownBy(() ->
      SupportRules.ensureElementEditable(objectXml, "element:cf-md-attribute:Артикул"))
      .isInstanceOf(IllegalStateException.class)
      .hasMessageContaining("Элемент на поддержке");

    // Элемента нет в правилах - правку не ограничиваем
    SupportRules.ensureElementEditable(objectXml, "element:cf-md-attribute:Нет");
  }

  @Test
  void staleGenerationStopsRuleWrite(@TempDir Path dir) throws Exception {
    // Правка поверх устаревшего снимка: файл успел измениться, запись отклоняется
    Path root = dir.resolve("cf");
    Files.createDirectories(root.resolve("Ext"));
    Files.write(SupportRules.rulesPath(root), rulesFile("0", "0", "0", "0"));
    String staleGeneration = SupportRules.read(root).generationId;
    assertThat(staleGeneration).isNotBlank();

    byte[] changed = rulesFile("0", "1", "0", "0");
    Files.write(SupportRules.rulesPath(root), changed);

    assertThatThrownBy(() ->
      SupportRules.setObjectMode(root, UUID_B, SupportRules.MODE_EDITABLE, staleGeneration))
      .isInstanceOf(IllegalStateException.class)
      .hasMessageContaining("изменились после чтения");
    assertThat(Files.readAllBytes(SupportRules.rulesPath(root))).isEqualTo(changed);

    // Свежий отпечаток правку пропускает
    SupportRules.setObjectMode(root, UUID_B, SupportRules.MODE_EDITABLE, SupportRules.generationOf(changed));
    assertThat(SupportRules.parse(Files.readAllBytes(SupportRules.rulesPath(root))).effectiveState(UUID_B))
      .isEqualTo("editable");
  }

  @Test
  void removeSupportDeletesRulesFile(@TempDir Path dir) throws Exception {
    Path root = dir.resolve("cf");
    Files.createDirectories(root.resolve("Ext"));
    Files.write(SupportRules.rulesPath(root), rulesFile("1", "1", "1", "1"));

    SupportRules.removeSupport(root, null);

    assertThat(Files.exists(SupportRules.rulesPath(root))).isFalse();
    assertThat(SupportRules.read(root).isEmpty()).isTrue();
    assertThatThrownBy(() -> SupportRules.removeSupport(root, null))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("не на поддержке");
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
