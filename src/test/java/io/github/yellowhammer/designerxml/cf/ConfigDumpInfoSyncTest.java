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
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Служебный файл версий объектов не отстаёт от состава: после серии мутаций записи
 * {@code ConfigDumpInfo.xml} совпадают с {@code ChildObjects} и файлами на диске.
 *
 * <p>Файл кладётся в выгрузку отдельно: его пишет конфигуратор при выгрузке, а не scaffold.
 * Мутации на выгрузке без этого файла работают как раньше.
 */
class ConfigDumpInfoSyncTest {

  private static final SchemaVersion VERSION = SchemaVersion.V2_20;
  private static final Pattern ENTRY_NAME = Pattern.compile("<Metadata name=\"([^\"]+)\"");

  @TempDir
  Path workspace;

  private Path cf;
  private Path configurationXml;

  @BeforeEach
  void createDump() throws Exception {
    cf = workspace.resolve("cf");
    EmptyCfScaffold.writeEmptyTree(cf, CfLayout.DEFAULT_CONFIGURATION_NAME, null, null, null, VERSION);
    configurationXml = cf.resolve(CfLayout.CONFIGURATION_XML);
    ConfigDumpInfoXml.write(cf, VERSION);
  }

  @Test
  void добавленныйОбъектПопадаетВСлужебныйФайл() throws Exception {
    MdObjectAdd.add(configurationXml, "Валюты", VERSION, MdObjectAddType.CATALOG, null, false);

    assertThat(entryNames()).contains("Catalog.Валюты");
  }

  @Test
  void идентификаторЗаписиСовпадаетСИдентификаторомОбъекта() throws Exception {
    MdObjectAdd.add(configurationXml, "Валюты", VERSION, MdObjectAddType.CATALOG, null, false);

    String objectXml = Files.readString(CfLayout.catalogObjectXml(cf, "Валюты"), StandardCharsets.UTF_8);
    Matcher uuid = Pattern.compile("uuid=\"([^\"]+)\"").matcher(objectXml);
    assertThat(uuid.find()).isTrue();

    assertThat(dumpInfo()).contains("<Metadata name=\"Catalog.Валюты\" id=\"" + uuid.group(1) + "\"");
  }

  @Test
  void версияНовойЗаписиЗаполненаНулями() throws Exception {
    MdObjectAdd.add(configurationXml, "Валюты", VERSION, MdObjectAddType.CATALOG, null, false);

    assertThat(dumpInfo()).contains("configVersion=\"" + ConfigDumpInfoSync.UNKNOWN_CONFIG_VERSION + "\"");
  }

  @Test
  void удалённыйОбъектИзСлужебногоФайлаУходит() throws Exception {
    MdObjectAdd.add(configurationXml, "Валюты", VERSION, MdObjectAddType.CATALOG, null, false);
    MdObjectAdd.add(configurationXml, "Организации", VERSION, MdObjectAddType.CATALOG, null, false);

    CfMdObjectMutations.delete(configurationXml, CfLayout.catalogObjectXml(cf, "Валюты"), "Catalog", "Валюты");

    assertThat(entryNames()).containsExactlyInAnyOrder("Language.Русский", "Catalog.Организации");
  }

  @Test
  void переименованныйОбъектЗаписанПодНовымИменем() throws Exception {
    MdObjectAdd.add(configurationXml, "Валюты", VERSION, MdObjectAddType.CATALOG, null, false);

    CfMdObjectMutations.rename(
      configurationXml, CfLayout.catalogObjectXml(cf, "Валюты"), "Catalog", "Валюты", "Деньги");

    assertThat(entryNames()).containsExactlyInAnyOrder("Language.Русский", "Catalog.Деньги");
  }

  @Test
  void послеСерииМутацийСоставСовпадает() throws Exception {
    MdObjectAdd.add(configurationXml, "Валюты", VERSION, MdObjectAddType.CATALOG, null, false);
    MdObjectAdd.add(configurationXml, "Организации", VERSION, MdObjectAddType.CATALOG, null, false);
    MdObjectAdd.add(configurationXml, "СтавкаНДС", VERSION, MdObjectAddType.ENUM, null, false);
    CfMdObjectMutations.delete(
      configurationXml, CfLayout.catalogObjectXml(cf, "Организации"), "Catalog", "Организации");
    CfMdObjectMutations.rename(
      configurationXml, CfLayout.objectXmlInSubdir(cf, "Enums", "СтавкаНДС"), "Enum", "СтавкаНДС", "СтавкиНДС");

    assertThat(entryNames()).containsExactlyInAnyOrder("Catalog.Валюты", "Enum.СтавкиНДС", "Language.Русский");
  }

  @Test
  void записиСуществующихОбъектовПереносятсяВместеСВложенными() throws Exception {
    MdObjectAdd.add(configurationXml, "Валюты", VERSION, MdObjectAddType.CATALOG, null, false);
    Path dumpInfo = cf.resolve(CfLayout.CONFIG_DUMP_INFO_XML);
    String withChild = dumpInfo()
      .replaceFirst(
        "<Metadata name=\"Catalog\\.Валюты\"([^/]*)/>",
        "<Metadata name=\"Catalog.Валюты\"$1>\n"
          + "\t\t\t<Metadata name=\"Catalog.Валюты.Attribute.Код\" id=\"вложенный\"/>\n"
          + "\t\t</Metadata>");
    Files.writeString(dumpInfo, withChild, StandardCharsets.UTF_8);

    MdObjectAdd.add(configurationXml, "Организации", VERSION, MdObjectAddType.CATALOG, null, false);

    assertThat(dumpInfo()).contains("<Metadata name=\"Catalog.Валюты.Attribute.Код\" id=\"вложенный\"/>");
    assertThat(entryNames()).contains("Catalog.Валюты", "Catalog.Организации");
  }

  @Test
  void записьСамойКонфигурацииОстаётся() throws Exception {
    Path dumpInfo = cf.resolve(CfLayout.CONFIG_DUMP_INFO_XML);
    Files.writeString(
      dumpInfo,
      Files.readString(dumpInfo, StandardCharsets.UTF_8).replace(
        "<ConfigVersions/>",
        "<ConfigVersions>\n\t\t<Metadata name=\"Configuration.Конфигурация\" id=\"корень\"/>\n\t</ConfigVersions>"),
      StandardCharsets.UTF_8);

    MdObjectAdd.add(configurationXml, "Валюты", VERSION, MdObjectAddType.CATALOG, null, false);

    // в ChildObjects записи конфигурации нет и быть не может: сверка состава её не касается
    assertThat(dumpInfo()).contains("<Metadata name=\"Configuration.Конфигурация\" id=\"корень\"/>");
    assertThat(entryNames()).contains("Catalog.Валюты");
  }

  @Test
  void записиЧастейОбъектаЖивутВместеСНим() throws Exception {
    MdObjectAdd.add(configurationXml, "Валюты", VERSION, MdObjectAddType.CATALOG, null, false);
    MdObjectAdd.add(configurationXml, "Организации", VERSION, MdObjectAddType.CATALOG, null, false);
    Path dumpInfo = cf.resolve(CfLayout.CONFIG_DUMP_INFO_XML);
    // платформа держит записи частей объекта рядом с ним, на том же уровне
    Files.writeString(
      dumpInfo,
      dumpInfo().replace(
        "</ConfigVersions>",
        "\t<Metadata name=\"Catalog.Валюты.Form.ФормаСписка\" id=\"форма\"/>\n"
          + "\t<Metadata name=\"Catalog.Организации.Help\" id=\"справка\"/>\n"
          + "\t</ConfigVersions>"),
      StandardCharsets.UTF_8);

    CfMdObjectMutations.delete(
      configurationXml, CfLayout.catalogObjectXml(cf, "Организации"), "Catalog", "Организации");

    String after = dumpInfo();
    assertThat(after).contains("Catalog.Валюты.Form.ФормаСписка");
    assertThat(after).doesNotContain("Catalog.Организации");
  }

  @Test
  void безСлужебногоФайлаМутацииРаботаютКакРаньше() throws Exception {
    Files.delete(cf.resolve(CfLayout.CONFIG_DUMP_INFO_XML));

    MdObjectAdd.add(configurationXml, "Валюты", VERSION, MdObjectAddType.CATALOG, null, false);

    assertThat(cf.resolve(CfLayout.CONFIG_DUMP_INFO_XML)).doesNotExist();
    assertThat(CfLayout.catalogObjectXml(cf, "Валюты")).exists();
  }

  private String dumpInfo() throws IOException {
    return Files.readString(cf.resolve(CfLayout.CONFIG_DUMP_INFO_XML), StandardCharsets.UTF_8);
  }

  /** Имена записей верхнего уровня: вложенные содержат больше двух частей и сюда не попадают. */
  private List<String> entryNames() throws IOException {
    List<String> names = new ArrayList<>();
    Matcher matcher = ENTRY_NAME.matcher(dumpInfo());
    while (matcher.find()) {
      if (matcher.group(1).split("\\.").length == 2) {
        names.add(matcher.group(1));
      }
    }
    return names;
  }
}
