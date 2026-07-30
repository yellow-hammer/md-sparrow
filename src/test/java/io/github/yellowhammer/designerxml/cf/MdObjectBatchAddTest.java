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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Пакетное создание: набор появляется целиком либо не появляется вовсе.
 */
class MdObjectBatchAddTest {

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
  void наборСоздаётсяОднимВызовом() throws Exception {
    List<String> created = MdObjectBatchAdd.add(
      configurationXml,
      List.of(
        new MdObjectBatchAdd.Item("CATALOG", "Валюты", "Валюты организации"),
        new MdObjectBatchAdd.Item("DOCUMENT", "ЗаказКлиента", null),
        new MdObjectBatchAdd.Item("ENUM", "СтавкиНДС", null)),
      VERSION);

    assertThat(created).containsExactly("Catalog.Валюты", "Document.ЗаказКлиента", "Enum.СтавкиНДС");
    assertThat(CfLayout.catalogObjectXml(cf, "Валюты")).exists();
    assertThat(CfLayout.objectXmlInSubdir(cf, "Documents", "ЗаказКлиента")).exists();
    assertThat(CfLayout.objectXmlInSubdir(cf, "Enums", "СтавкиНДС")).exists();
    assertThat(configuration())
      .contains("<Catalog>Валюты</Catalog>")
      .contains("<Document>ЗаказКлиента</Document>")
      .contains("<Enum>СтавкиНДС</Enum>");
  }

  @Test
  void синонимДоходитДоОбъекта() throws Exception {
    MdObjectBatchAdd.add(
      configurationXml,
      List.of(new MdObjectBatchAdd.Item("CATALOG", "Валюты", "Валюты организации")),
      VERSION);

    assertThat(Files.readString(CfLayout.catalogObjectXml(cf, "Валюты"), StandardCharsets.UTF_8))
      .contains("Валюты организации");
  }

  @Test
  void ошибкаВСерединеНабораНеОставляетСледов() throws Exception {
    String before = configuration();

    assertThatThrownBy(() -> MdObjectBatchAdd.add(
      configurationXml,
      List.of(
        new MdObjectBatchAdd.Item("CATALOG", "Валюты", null),
        new MdObjectBatchAdd.Item("CATALOG", "Валюты", null)),
      VERSION))
      .isInstanceOf(IllegalArgumentException.class);

    assertThat(configuration()).isEqualTo(before);
    assertThat(CfLayout.catalogObjectXml(cf, "Валюты")).doesNotExist();
  }

  @Test
  void объектУжеЕстьВВыгрузкеНаборОткатывается() throws Exception {
    MdObjectAdd.add(configurationXml, "Валюты", VERSION, MdObjectAddType.CATALOG, null, false);
    String before = configuration();

    assertThatThrownBy(() -> MdObjectBatchAdd.add(
      configurationXml,
      List.of(
        new MdObjectBatchAdd.Item("CATALOG", "Организации", null),
        new MdObjectBatchAdd.Item("CATALOG", "Валюты", null)),
      VERSION))
      .isInstanceOf(IllegalArgumentException.class);

    assertThat(configuration()).isEqualTo(before);
    assertThat(CfLayout.catalogObjectXml(cf, "Организации")).doesNotExist();
    assertThat(CfLayout.catalogObjectXml(cf, "Валюты")).exists();
  }

  @Test
  void недопустимоеИмяЛовитсяДоЗаписи() throws Exception {
    String before = configuration();

    assertThatThrownBy(() -> MdObjectBatchAdd.add(
      configurationXml,
      List.of(
        new MdObjectBatchAdd.Item("CATALOG", "Валюты", null),
        new MdObjectBatchAdd.Item("CATALOG", "1Плохое Имя", null)),
      VERSION))
      .isInstanceOf(IllegalArgumentException.class);

    assertThat(configuration()).isEqualTo(before);
    assertThat(CfLayout.catalogObjectXml(cf, "Валюты")).doesNotExist();
  }

  @Test
  void синонимТолькоДляСправочника() {
    assertThatThrownBy(() -> MdObjectBatchAdd.add(
      configurationXml,
      List.of(new MdObjectBatchAdd.Item("DOCUMENT", "ЗаказКлиента", "Заказ")),
      VERSION))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("CATALOG");
  }

  @Test
  void пустойНаборЭтоОшибка() {
    assertThatThrownBy(() -> MdObjectBatchAdd.add(configurationXml, List.of(), VERSION))
      .isInstanceOf(IllegalArgumentException.class);
  }

  private String configuration() throws IOException {
    return Files.readString(configurationXml, StandardCharsets.UTF_8);
  }
}
