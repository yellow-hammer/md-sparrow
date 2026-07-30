/*
 * This file is a part of md-sparrow.
 *
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package io.github.yellowhammer.designerxml.cf;

import io.github.yellowhammer.designerxml.SchemaVersion;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Проверка выгрузки: целая выгрузка находок не даёт, испорченная - даёт ровно ту, что ждём.
 *
 * <p>Выгрузка для проверок собирается тем же кодом, которым инструмент создаёт конфигурацию и
 * объекты: если проверка начнёт ругаться на собственный результат, тест это покажет.
 */
class CfDumpValidationTest {

  private static final SchemaVersion VERSION = SchemaVersion.V2_20;

  @TempDir
  Path workspace;

  private Path cf;
  private Path configurationXml;

  @BeforeEach
  void createDump() throws Exception {
    cf = workspace.resolve("cf");
    EmptyCfScaffold.writeEmptyTree(cf, CfLayout.DEFAULT_CONFIGURATION_NAME, null, null, null, VERSION);
    configurationXml = cf.resolve(CfLayout.CONFIGURATION_XML);
    MdObjectAdd.add(configurationXml, "Валюты", VERSION, MdObjectAddType.CATALOG, null, false);
    MdObjectAdd.add(configurationXml, "СтавкаНДС", VERSION, MdObjectAddType.ENUM, null, false);
  }

  @Test
  void целаяВыгрузкаНаходокНеДаёт() throws IOException {
    assertThat(CfDumpValidation.validate(cf)).isEmpty();
  }

  @Test
  void каталогБезConfigurationXmlНеСчитаетсяВыгрузкой() throws IOException {
    List<CfDumpFinding> findings = CfDumpValidation.validate(workspace.resolve("пусто"));

    assertThat(findings).singleElement()
      .extracting(CfDumpFinding::kind).isEqualTo(CfDumpValidation.KIND_CONFIGURATION_MISSING);
  }

  @Test
  void неподдерживаемаяВерсияФорматаОстанавливаетПроверку() throws IOException {
    replaceInConfiguration("version=\"2.20\"", "version=\"9.99\"");

    List<CfDumpFinding> findings = CfDumpValidation.validate(cf);

    assertThat(findings).singleElement()
      .extracting(CfDumpFinding::kind).isEqualTo(CfDumpValidation.KIND_VERSION_UNSUPPORTED);
    assertThat(findings.get(0).message()).contains("9.99");
  }

  @Test
  void объявленныйОбъектБезФайла() throws IOException {
    Files.delete(CfLayout.catalogObjectXml(cf, "Валюты"));

    assertThat(CfDumpValidation.validate(cf))
      .singleElement()
      .satisfies(finding -> {
        assertThat(finding.kind()).isEqualTo(CfDumpValidation.KIND_MISSING_FILE);
        assertThat(finding.objectName()).isEqualTo("Валюты");
        assertThat(finding.path()).isEqualTo("Catalogs/Валюты.xml");
      });
  }

  @Test
  void файлОбъектаБезОбъявленияВСоставе() throws IOException {
    Files.copy(CfLayout.catalogObjectXml(cf, "Валюты"), CfLayout.catalogObjectXml(cf, "Забытый"));

    assertThat(CfDumpValidation.validate(cf))
      .singleElement()
      .satisfies(finding -> {
        assertThat(finding.kind()).isEqualTo(CfDumpValidation.KIND_ORPHAN_FILE);
        assertThat(finding.objectType()).isEqualTo("Catalog");
        assertThat(finding.objectName()).isEqualTo("Забытый");
      });
  }

  @Test
  void объектОбъявленДважды() throws IOException {
    replaceInConfiguration("<Catalog>Валюты</Catalog>", "<Catalog>Валюты</Catalog><Catalog>Валюты</Catalog>");

    assertThat(CfDumpValidation.validate(cf))
      .singleElement()
      .extracting(CfDumpFinding::kind).isEqualTo(CfDumpValidation.KIND_DUPLICATE_ENTRY);
  }

  @Test
  void неизвестныйТипВСоставе() throws IOException {
    replaceInConfiguration("<Catalog>Валюты</Catalog>", "<Catalog>Валюты</Catalog><QuantumRegister>Кубиты</QuantumRegister>");

    assertThat(CfDumpValidation.validate(cf))
      .extracting(CfDumpFinding::kind)
      .containsOnlyOnce(CfDumpValidation.KIND_UNKNOWN_TYPE);
  }

  @Test
  void типыВСоставеИдутНеВТомПорядке() throws IOException {
    replaceInConfiguration(
      "<Language>Русский</Language>",
      "");
    replaceInConfiguration(
      "<Enum>СтавкаНДС</Enum>",
      "<Enum>СтавкаНДС</Enum><Language>Русский</Language>");

    assertThat(CfDumpValidation.validate(cf))
      .singleElement()
      .satisfies(finding -> {
        assertThat(finding.kind()).isEqualTo(CfDumpValidation.KIND_CHILD_OBJECTS_ORDER);
        assertThat(finding.objectType()).isEqualTo("Language");
      });
  }

  @Test
  void версияФорматаОбъектаОтличаетсяОтКонфигурации() throws IOException {
    Path catalog = CfLayout.catalogObjectXml(cf, "Валюты");
    Files.writeString(
      catalog,
      Files.readString(catalog, StandardCharsets.UTF_8).replace("version=\"2.20\"", "version=\"2.19\""),
      StandardCharsets.UTF_8);

    assertThat(CfDumpValidation.validate(cf))
      .singleElement()
      .satisfies(finding -> {
        assertThat(finding.kind()).isEqualTo(CfDumpValidation.KIND_VERSION_MISMATCH);
        assertThat(finding.message()).contains("2.19").contains("2.20");
      });
  }

  @Test
  void файлОбъектаБезВерсииФормата() throws IOException {
    Files.writeString(
      CfLayout.catalogObjectXml(cf, "Валюты"),
      "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<Каша/>\n",
      StandardCharsets.UTF_8);

    assertThat(CfDumpValidation.validate(cf))
      .singleElement()
      .satisfies(finding -> {
        assertThat(finding.kind()).isEqualTo(CfDumpValidation.KIND_VERSION_UNREADABLE);
        assertThat(finding.objectName()).isEqualTo("Валюты");
      });
  }

  @Test
  void ссылкаНаНесуществующийОбъект() throws IOException {
    replaceInConfiguration(
      "<ChildObjects>",
      "<DefaultRoles><xr:Item xsi:type=\"xr:MDObjectRef\">Role.ПолныеПрава</xr:Item></DefaultRoles><ChildObjects>");

    assertThat(CfDumpValidation.validate(cf))
      .singleElement()
      .satisfies(finding -> {
        assertThat(finding.kind()).isEqualTo(CfDumpValidation.KIND_DANGLING_REFERENCE);
        assertThat(finding.objectName()).isEqualTo("ПолныеПрава");
      });
  }

  @Test
  void ссылкаНаЯзыкИзЭталонаЦелая() throws IOException {
    assertThat(Files.readString(configurationXml, StandardCharsets.UTF_8)).contains("Language.Русский");

    assertThat(CfDumpValidation.validate(cf)).isEmpty();
  }

  @Test
  void версияConfigDumpInfoОтличаетсяОтКонфигурации() throws IOException {
    ConfigDumpInfoXml.write(cf, SchemaVersion.V2_19);

    assertThat(CfDumpValidation.validate(cf))
      .singleElement()
      .extracting(CfDumpFinding::kind).isEqualTo(CfDumpValidation.KIND_DUMP_INFO_VERSION);
  }

  @Test
  void вConfigDumpInfoЗаписанОбъектКоторогоНет() throws IOException {
    ConfigDumpInfoXml.write(cf, VERSION);
    Path dumpInfo = cf.resolve(CfLayout.CONFIG_DUMP_INFO_XML);
    Files.writeString(
      dumpInfo,
      Files.readString(dumpInfo, StandardCharsets.UTF_8)
        .replace("<ConfigVersions/>", "<ConfigVersions><Metadata name=\"Catalog.Удалённый\" id=\"x\"/></ConfigVersions>"),
      StandardCharsets.UTF_8);

    assertThat(CfDumpValidation.validate(cf))
      .singleElement()
      .satisfies(finding -> {
        assertThat(finding.kind()).isEqualTo(CfDumpValidation.KIND_DUMP_INFO_EXTRA);
        assertThat(finding.objectName()).isEqualTo("Удалённый");
      });
  }

  private void replaceInConfiguration(String from, String to) throws IOException {
    String text = Files.readString(configurationXml, StandardCharsets.UTF_8);
    assertThat(text).contains(from);
    Files.writeString(configurationXml, text.replace(from, to), StandardCharsets.UTF_8);
  }
}
