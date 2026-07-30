/*
 * This file is a part of md-sparrow.
 *
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package io.github.yellowhammer.designerxml.cf;

import io.github.yellowhammer.designerxml.SchemaVersion;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Пустое расширение: каркас из эталона выгрузки плюс свойства вызывающего.
 *
 * Эталоны сняты с платформы, поэтому проверка идёт по всем версиям формата, для которых
 * платформа умеет создать расширение. Для более старых операция честно отказывается,
 * вместо того чтобы собирать XML из головы.
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
    assertThat(xml).contains("<Role>" + GoldenScaffold.extensionDefaultRoleName(VERSION) + "</Role>");
    assertThat(xml).contains("Role." + GoldenScaffold.extensionDefaultRoleName(VERSION));
    assertThat(xml).doesNotContain("<Language>");
    assertThat(xml).doesNotContain("<CommonModule>");
    assertThat(root.resolve("Roles").resolve(GoldenScaffold.extensionDefaultRoleName(VERSION) + ".xml")).exists();
    assertThat(root.resolve(CfLayout.LANGUAGES_DIR)).doesNotExist();
  }

  @ParameterizedTest
  @EnumSource(SchemaVersion.class)
  void эталонЕстьИДаётРабочийКаркас(SchemaVersion version) throws IOException {
    if (!GoldenScaffold.hasExtensionGolden(version)) {
      return;
    }
    Path root = workspace.resolve("Расширение" + version.name());
    EmptyCfeScaffold.writeEmptyTree(
      root, "Расширение", null, "рас_", EmptyCfeScaffold.Purpose.ADD_ON, null, null, version);

    String xml = Files.readString(root.resolve(CfLayout.CONFIGURATION_XML), StandardCharsets.UTF_8);
    assertThat(xml).contains("version=\"" + version.metadataObjectVersionAttribute() + "\"");
    assertThat(xml).contains("<Name>Расширение</Name>");
    assertThat(xml).contains("<ObjectBelonging>Adopted</ObjectBelonging>");
    assertThat(xml).contains("<NamePrefix>рас_</NamePrefix>");
    String role = GoldenScaffold.extensionDefaultRoleName(version);
    assertThat(xml).contains("<Role>" + role + "</Role>");
    assertThat(root.resolve("Roles").resolve(role + ".xml")).exists();
  }

  @Test
  void эталоныЕстьДляВсехФорматовГдеПлатформаУмеетСоздатьРасширение() {
    // ibcmd научился создавать расширения с 8.3.21, то есть с формата 2.14; для более старых
    // платформ эталон снять нечем: в ibcmd тех версий режима расширений нет вовсе.
    List<String> covered = Arrays.stream(SchemaVersion.values())
      .filter(GoldenScaffold::hasExtensionGolden)
      .map(SchemaVersion::metadataObjectVersionAttribute)
      .toList();

    assertThat(covered).containsExactly("2.14", "2.15", "2.16", "2.17", "2.18", "2.19", "2.20", "2.21");
  }

  @Test
  void безЭталонаФорматаОперацияОтказывается() {
    assertThatThrownBy(() -> EmptyCfeScaffold.writeEmptyTree(
      workspace.resolve("Старое"),
      "Старое", null, "ст_", EmptyCfeScaffold.Purpose.ADD_ON, null, null, SchemaVersion.V2_10))
      .isInstanceOf(IOException.class)
      .hasMessageContaining("2.10");
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
    Path mainCf = workspace.resolve("cf");
    EmptyCfScaffold.writeEmptyTree(mainCf, CfLayout.DEFAULT_CONFIGURATION_NAME, null, null, null, VERSION);
    Path mainConfigurationXml = mainCf.resolve(CfLayout.CONFIGURATION_XML);
    String main = Files.readString(mainConfigurationXml, StandardCharsets.UTF_8);

    Path root = workspace.resolve("РасширениеПоКонфигурации");
    EmptyCfeScaffold.writeEmptyTreeFromConfiguration(
      root, "РасширениеПоКонфигурации", null, "рк_", EmptyCfeScaffold.Purpose.ADD_ON,
      mainConfigurationXml, VERSION);

    String xml = Files.readString(root.resolve(CfLayout.CONFIGURATION_XML), StandardCharsets.UTF_8);
    // платформа не принимает расширение с режимом выше, чем у расширяемой конфигурации
    assertThat(xml).contains(
      "<ConfigurationExtensionCompatibilityMode>" + leaf(main, "CompatibilityMode")
        + "</ConfigurationExtensionCompatibilityMode>");
    assertThat(xml).contains(
      "<InterfaceCompatibilityMode>" + leaf(main, "InterfaceCompatibilityMode")
        + "</InterfaceCompatibilityMode>");
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
