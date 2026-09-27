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

import io.github.yellowhammer.designerxml.DesignerXml;
import io.github.yellowhammer.designerxml.SamplesSubmodulePaths;
import io.github.yellowhammer.designerxml.SchemaVersion;
import io.github.yellowhammer.designerxml.Ssl31SubmodulePaths;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.api.io.TempDir;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Пустое расширение: каркас из эталона выгрузки плюс свойства вызывающего.
 *
 * Каркас формата V - проекция канонического эталона, поэтому расширение создаётся во всех
 * форматах. Где платформа сняла эталон (2.14-2.21), результат сверяется с ним с точностью до UUID.
 */
class EmptyCfeScaffoldTest {

  private static final SchemaVersion VERSION = SchemaVersion.V2_20;

  @TempDir
  Path workspace;

  private String createExtension(String name, String prefix, EmptyCfeScaffold.Purpose purpose) throws IOException {
    Path root = workspace.resolve(name);
    EmptyCfeScaffold.writeEmptyTree(root, name, null, prefix, purpose, "Version8_3_24", null, VERSION);
    return Files.readString(root.resolve(CfLayout.CONFIGURATION_XML), StandardCharsets.UTF_8);
  }

  @Test
  void пишетКаркасРасширенияСоСвойствамиВызывающего() throws IOException {
    String xml = createExtension("МоеРасширение", "мо_", EmptyCfeScaffold.Purpose.ADD_ON);

    assertThat(xml).contains("<Name>МоеРасширение</Name>");
    assertThat(xml).contains("<NamePrefix>мо_</NamePrefix>");
    assertThat(xml).contains("<ConfigurationExtensionPurpose>AddOn</ConfigurationExtensionPurpose>");
    assertThat(xml).contains("<ConfigurationExtensionCompatibilityMode>Version8_3_24</ConfigurationExtensionCompatibilityMode>");
    assertThat(xml).contains("<ObjectBelonging>Adopted</ObjectBelonging>");
  }

  @Test
  void составТакойЖеКакУПлатформы() throws IOException {
    Path root = workspace.resolve("Пустое");
    EmptyCfeScaffold.writeEmptyTree(
      root, "Пустое", null, "пу_", EmptyCfeScaffold.Purpose.CUSTOMIZATION, null, null, VERSION);

    String xml = Files.readString(root.resolve(CfLayout.CONFIGURATION_XML), StandardCharsets.UTF_8);
    assertThat(xml).contains("<Role>" + GoldenScaffold.extensionDefaultRoleName() + "</Role>");
    assertThat(xml).contains("Role." + GoldenScaffold.extensionDefaultRoleName());
    assertThat(xml).doesNotContain("<Language>");
    assertThat(xml).doesNotContain("<CommonModule>");
    assertThat(root.resolve("Roles").resolve(GoldenScaffold.extensionDefaultRoleName() + ".xml")).exists();
    assertThat(root.resolve(CfLayout.LANGUAGES_DIR)).doesNotExist();
  }

  @ParameterizedTest
  @EnumSource(SchemaVersion.class)
  void каркасЕстьВоВсехФорматах(SchemaVersion version) throws Exception {
    Path root = workspace.resolve("Расширение" + version.name());
    EmptyCfeScaffold.writeEmptyTree(
      root, "Расширение", null, "рас_", EmptyCfeScaffold.Purpose.ADD_ON, null, null, version);

    String xml = Files.readString(root.resolve(CfLayout.CONFIGURATION_XML), StandardCharsets.UTF_8);
    assertThat(xml).contains("version=\"" + version.metadataObjectVersionAttribute() + "\"");
    assertThat(xml).contains("<Name>Расширение</Name>");
    assertThat(xml).contains("<ObjectBelonging>Adopted</ObjectBelonging>");
    assertThat(xml).contains("<NamePrefix>рас_</NamePrefix>");
    String role = GoldenScaffold.extensionDefaultRoleName();
    assertThat(xml).contains("<Role>" + role + "</Role>");
    Path roleXml = root.resolve("Roles").resolve(role + ".xml");
    assertThat(roleXml).exists();
    DesignerXml.read(root.resolve(CfLayout.CONFIGURATION_XML), version);
    DesignerXml.read(roleXml, version);
  }

  @ParameterizedTest
  @EnumSource(SchemaVersion.class)
  void совпадаетСРасширениемПлатформыСТочностьюДоUuid(SchemaVersion version) throws IOException {
    // эталоны есть с 2.14: ibcmd более старых платформ расширений не создаёт
    if (GoldenSnapshots.files(version, GoldenSnapshots.CFE).isEmpty()) {
      return;
    }
    String platform = GoldenSnapshots.read(version, GoldenSnapshots.CFE, CfLayout.CONFIGURATION_XML);
    String name = ScaffoldPropertyEdit.leaf(platform, "Name").orElseThrow();
    Path root = workspace.resolve("Платформа" + version.name());
    // эталон снят с назначением «дополнение», без префикса и режимов вызывающего
    EmptyCfeScaffold.writeEmptyTree(root, name, null, null, EmptyCfeScaffold.Purpose.ADD_ON, null, null, version);

    assertThat(GoldenSnapshots.normalizeUuids(GoldenSnapshots.read(root.resolve(CfLayout.CONFIGURATION_XML))))
      .isEqualTo(GoldenSnapshots.normalizeUuids(platform));
    String role = "Roles/" + GoldenScaffold.extensionDefaultRoleName() + ".xml";
    assertThat(GoldenSnapshots.normalizeUuids(GoldenSnapshots.read(root.resolve(role))))
      .isEqualTo(GoldenSnapshots.normalizeUuids(GoldenSnapshots.read(version, GoldenSnapshots.CFE, role)));
  }

  @Test
  void синонимЗадаётсяОтдельноОтИмени() throws IOException {
    Path root = workspace.resolve("Расш");
    EmptyCfeScaffold.writeEmptyTree(
      root, "Расш", "Моё расширение", "рс_", EmptyCfeScaffold.Purpose.PATCH, null, null, VERSION);

    String xml = Files.readString(root.resolve(CfLayout.CONFIGURATION_XML), StandardCharsets.UTF_8);
    assertThat(xml).contains("<v8:content>Моё расширение</v8:content>");
    assertThat(xml).contains("<ConfigurationExtensionPurpose>Patch</ConfigurationExtensionPurpose>");
  }

  @Test
  void идентификаторыРазныеУРазныхРасширений() throws IOException {
    String first = createExtension("Первое", "пе_", EmptyCfeScaffold.Purpose.ADD_ON);
    String second = createExtension("Второе", "вт_", EmptyCfeScaffold.Purpose.ADD_ON);

    assertThat(uuidOfConfiguration(first)).isNotEqualTo(uuidOfConfiguration(second));
  }

  @Test
  void одинаковоеИмяДаётОдинаковыйРезультат() throws IOException {
    String first = createExtension("Повтор", "по_", EmptyCfeScaffold.Purpose.ADD_ON);
    Path other = workspace.resolve("другой-каталог");
    EmptyCfeScaffold.writeEmptyTree(
      other, "Повтор", null, "по_", EmptyCfeScaffold.Purpose.ADD_ON, "Version8_3_24", null, VERSION);

    assertThat(Files.readString(other.resolve(CfLayout.CONFIGURATION_XML), StandardCharsets.UTF_8))
      .isEqualTo(first);
  }

  @Test
  void безПрефиксаПолучаетсяТоЖе_чтоСоздаётПлатформа() throws IOException {
    Path root = workspace.resolve("БезПрефикса");
    EmptyCfeScaffold.writeEmptyTree(
      root, "БезПрефикса", null, null, EmptyCfeScaffold.Purpose.ADD_ON, null, null, VERSION);

    // платформа в новом расширении оставляет префикс пустым, своего правила не выдумываем
    assertThat(Files.readString(root.resolve(CfLayout.CONFIGURATION_XML), StandardCharsets.UTF_8))
      .contains("<NamePrefix/>");
  }

  @Test
  void назначениеРазбираетсяИзИмениКоманднойСтроки() {
    assertThat(EmptyCfeScaffold.Purpose.fromCliName("add-on")).isEqualTo(EmptyCfeScaffold.Purpose.ADD_ON);
    assertThat(EmptyCfeScaffold.Purpose.fromCliName("Customization")).isEqualTo(EmptyCfeScaffold.Purpose.CUSTOMIZATION);
    assertThat(EmptyCfeScaffold.Purpose.fromCliName("patch")).isEqualTo(EmptyCfeScaffold.Purpose.PATCH);
    assertThatThrownBy(() -> EmptyCfeScaffold.Purpose.fromCliName("расширение"))
      .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void режимыСовместимостиБерутсяИзОсновнойКонфигурации() throws IOException {
    // Типовая конфигурация в новом режиме совместимости: расширение может переопределять её свойства
    Path mainConfigurationXml = Ssl31SubmodulePaths.configurationXml();
    String main = Files.readString(mainConfigurationXml, StandardCharsets.UTF_8);
    assertThat(EmptyCfeScaffold.overridesAdoptedProperties(leaf(main, "CompatibilityMode"))).isTrue();

    Path root = workspace.resolve("РасширениеПоКонфигурации");
    EmptyCfeScaffold.writeEmptyTreeFromConfiguration(
      root, "РасширениеПоКонфигурации", null, "рк_", EmptyCfeScaffold.Purpose.ADD_ON,
      mainConfigurationXml, VERSION);

    String xml = Files.readString(root.resolve(CfLayout.CONFIGURATION_XML), StandardCharsets.UTF_8);
    // режим расширения платформа сама берёт из режима совместимости основной конфигурации
    assertThat(xml).contains(
      "<ConfigurationExtensionCompatibilityMode>" + leaf(main, "CompatibilityMode")
        + "</ConfigurationExtensionCompatibilityMode>");
    assertThat(xml).contains(
      "<InterfaceCompatibilityMode>" + leaf(main, "InterfaceCompatibilityMode")
        + "</InterfaceCompatibilityMode>");
    String role = GoldenScaffold.extensionDefaultRoleName();
    assertThat(xml).contains("<DefaultRoles>").contains("<Role>" + role + "</Role>");
    assertThat(root.resolve("Roles").resolve(role + ".xml")).exists();
  }

  /**
   * В режиме совместимости 8.3.13 и ниже платформа не принимает расширение, которое переопределяет
   * свойства заимствованной конфигурации, а основные роли - такое свойство. Сама она создаёт в этом
   * режиме расширение без основных ролей, роли по умолчанию и режима совместимости интерфейса.
   */
  @ParameterizedTest
  @EnumSource(SchemaVersion.class)
  void приСтаромРежимеОсновнойРасширениеКакУПлатформы(SchemaVersion version) throws Exception {
    // Голые объекты сняты с базы в режиме совместимости 8.3.12
    Path mainConfigurationXml = SamplesSubmodulePaths.bareObjects(version).resolve(CfLayout.CONFIGURATION_XML);
    String main = Files.readString(mainConfigurationXml, StandardCharsets.UTF_8);
    assertThat(EmptyCfeScaffold.overridesAdoptedProperties(leaf(main, "CompatibilityMode"))).isFalse();

    Path root = workspace.resolve("Старое" + version.name());
    EmptyCfeScaffold.writeEmptyTreeFromConfiguration(
      root, "НовоеРасширение", null, null, EmptyCfeScaffold.Purpose.CUSTOMIZATION, mainConfigurationXml, version);

    String xml = Files.readString(root.resolve(CfLayout.CONFIGURATION_XML), StandardCharsets.UTF_8);
    assertThat(xml).contains(
      "<ConfigurationExtensionCompatibilityMode>" + leaf(main, "CompatibilityMode")
        + "</ConfigurationExtensionCompatibilityMode>");
    assertThat(xml).doesNotContain("DefaultRoles").doesNotContain("InterfaceCompatibilityMode");
    assertThat(xml).contains("<ChildObjects/>").doesNotContain("<Role>");
    assertThat(root.resolve("Roles")).doesNotExist();
    DesignerXml.read(root.resolve(CfLayout.CONFIGURATION_XML), version);
  }

  @Test
  void переопределятьСвойстваМожноСРежима8_3_14() {
    // Граница из сообщения платформы: «недопустимо в режиме совместимости 8.3.13 и ниже»
    assertThat(EmptyCfeScaffold.overridesAdoptedProperties("Version8_3_13")).isFalse();
    assertThat(EmptyCfeScaffold.overridesAdoptedProperties("Version8_3_14")).isTrue();
    assertThat(EmptyCfeScaffold.overridesAdoptedProperties("Version8_5_1")).isTrue();
  }

  @ParameterizedTest
  @EnumSource(SchemaVersion.class)
  void режимыСхемыФорматаНеДопускаютПереопределений(SchemaVersion version) throws Exception {
    // В перечислении режимов XDTO только DontUse и режимы до 8.3.12 включительно; DontUse
    // платформа читает как 8.3.8
    List<String> modes = compatibilityModesOfSchema(version);
    assertThat(modes).contains("DontUse");
    for (String mode : modes) {
      assertThat(EmptyCfeScaffold.overridesAdoptedProperties(mode)).as(mode).isFalse();
    }
  }

  /**
   * Режим {@code DontUse} основной конфигурации платформа читает как 8.3.8: расширение с основными
   * ролями она в таком режиме отвергает, а сама создаёт его без ролей и с пустым составом.
   */
  @ParameterizedTest
  @EnumSource(SchemaVersion.class)
  void приРежимеDontUseОсновнойРасширениеБезРолей(SchemaVersion version) throws Exception {
    Path main = SamplesSubmodulePaths.copy(
      SamplesSubmodulePaths.bareObjects(version), workspace.resolve("Основная" + version.name()));
    Path mainConfigurationXml = main.resolve(CfLayout.CONFIGURATION_XML);
    List<String> modes = compatibilityModesOfSchema(version);
    String dontUse = modes.stream().filter(mode -> !mode.startsWith("Version")).findFirst().orElseThrow();
    FormNamespaceRulesTest.setCompatibilityMode(mainConfigurationXml, dontUse);

    Path root = workspace.resolve("Расширение" + version.name());
    EmptyCfeScaffold.writeEmptyTreeFromConfiguration(
      root, "НовоеРасширение", null, null, EmptyCfeScaffold.Purpose.CUSTOMIZATION, mainConfigurationXml, version);

    String xml = Files.readString(root.resolve(CfLayout.CONFIGURATION_XML), StandardCharsets.UTF_8);
    assertThat(xml).contains(
      "<ConfigurationExtensionCompatibilityMode>" + dontUse + "</ConfigurationExtensionCompatibilityMode>");
    assertThat(xml).doesNotContain("DefaultRoles").doesNotContain("InterfaceCompatibilityMode");
    assertThat(xml).contains("<ChildObjects/>").doesNotContain("<Role>");
    assertThat(root.resolve("Roles")).doesNotExist();
    DesignerXml.read(root.resolve(CfLayout.CONFIGURATION_XML), version);
  }

  private static List<String> compatibilityModesOfSchema(SchemaVersion version) throws Exception {
    Path xsd = Path.of(System.getProperty("xsd.root"), version.xsdDirectoryName(), "v8.1c.ru-8.3-xcf-enums.xsd");
    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    factory.setNamespaceAware(true);
    NodeList types = factory.newDocumentBuilder().parse(xsd.toFile())
      .getElementsByTagNameNS(XMLConstants.W3C_XML_SCHEMA_NS_URI, "simpleType");
    for (int i = 0; i < types.getLength(); i++) {
      Element type = (Element) types.item(i);
      if (!"CompatibilityMode".equals(type.getAttribute("name"))) {
        continue;
      }
      NodeList values = type.getElementsByTagNameNS(XMLConstants.W3C_XML_SCHEMA_NS_URI, "enumeration");
      List<String> modes = new ArrayList<>();
      for (int j = 0; j < values.getLength(); j++) {
        modes.add(((Element) values.item(j)).getAttribute("value"));
      }
      return modes;
    }
    throw new IllegalStateException("нет перечисления CompatibilityMode в " + xsd);
  }

  private static String leaf(String xml, String tag) {
    int start = xml.indexOf("<" + tag + ">") + tag.length() + 2;
    return xml.substring(start, xml.indexOf("</" + tag + ">", start));
  }

  private static String uuidOfConfiguration(String xml) {
    int start = xml.indexOf("<Configuration uuid=\"") + "<Configuration uuid=\"".length();
    return xml.substring(start, xml.indexOf('"', start));
  }
}
