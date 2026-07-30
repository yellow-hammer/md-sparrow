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

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Заданное имя объекта - обязательство.
 *
 * <p>Занять его нельзя - объект не создаётся, и вызывающий об этом слышит. Подбор свободного
 * имени остаётся отдельной операцией: она для того и есть.
 */
class MdObjectAddNameTest {

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
  }

  @Test
  void объектСоЗанятымИменемНеСоздаётся() throws Exception {
    MdObjectAdd.add(configurationXml, "Валюты", VERSION, MdObjectAddType.CATALOG, null, false);
    String before = Files.readString(configurationXml, StandardCharsets.UTF_8);
    String objectBefore = Files.readString(CfLayout.catalogObjectXml(cf, "Валюты"), StandardCharsets.UTF_8);

    assertThatThrownBy(() ->
      MdObjectAdd.add(configurationXml, "Валюты", VERSION, MdObjectAddType.CATALOG, null, false))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("Валюты");

    // ни подмены имени, ни перезаписи: выгрузка осталась прежней
    assertThat(Files.readString(configurationXml, StandardCharsets.UTF_8)).isEqualTo(before);
    assertThat(Files.readString(CfLayout.catalogObjectXml(cf, "Валюты"), StandardCharsets.UTF_8))
      .isEqualTo(objectBefore);
    assertThat(CfLayout.catalogObjectXml(cf, "Справочник1")).doesNotExist();
  }

  @Test
  void занятыйПутьОбъектаТожеОстанавливаетСоздание() throws Exception {
    Files.createDirectories(CfLayout.catalogObjectXml(cf, "Валюты"));
    String before = Files.readString(configurationXml, StandardCharsets.UTF_8);

    assertThatThrownBy(() ->
      MdObjectAdd.add(configurationXml, "Валюты", VERSION, MdObjectAddType.CATALOG, null, false))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("Catalogs/Валюты.xml");

    assertThat(Files.readString(configurationXml, StandardCharsets.UTF_8)).isEqualTo(before);
  }

  @Test
  void подборСвободногоИмениОстаётсяОтдельнойОперацией() throws Exception {
    MdObjectAdd.add(configurationXml, "Валюты", VERSION, MdObjectAddType.CATALOG, null, false);

    String created = MdObjectAdd.addWithNextAvailableName(
      configurationXml, VERSION, MdObjectAddType.CATALOG, null, false);

    assertThat(created).isNotEqualTo("Валюты");
    assertThat(CfLayout.catalogObjectXml(cf, created)).exists();
  }
}
