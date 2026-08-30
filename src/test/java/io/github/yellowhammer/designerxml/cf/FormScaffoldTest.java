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

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

class FormScaffoldTest {

  @TempDir Path tempDir;

  private Path copyCatalog() throws Exception {
    Path src = Ssl31SubmodulePaths.projectRoot().resolve("src/cf/Catalogs/_ДемоБанковскиеСчета.xml");
    Path copy = tempDir.resolve(src.getFileName());
    Files.copy(src, copy, StandardCopyOption.REPLACE_EXISTING);
    return copy;
  }

  @Test
  void addFormCreatesFilesAndEntry() throws Exception {
    Path objectXml = copyCatalog();
    FormScaffold.addForm(objectXml, SchemaVersion.V2_20, "НоваяФорма");

    String xml = Files.readString(objectXml);
    assertThat(xml).contains("<Form>НоваяФорма</Form>");
    Path forms = tempDir.resolve("_ДемоБанковскиеСчета").resolve("Forms");
    assertThat(Files.isRegularFile(forms.resolve("НоваяФорма.xml"))).isTrue();
    assertThat(Files.isRegularFile(forms.resolve("НоваяФорма").resolve("Ext").resolve("Form.xml"))).isTrue();

    MdObjectStructureDto structure = MdObjectStructureRead.read(objectXml, SchemaVersion.V2_20);
    assertThat(structure.forms).contains("НоваяФорма");
    MdObjectPropertiesDto descriptor = MdObjectPropertiesEdit.readDto(
      forms.resolve("НоваяФорма.xml"), SchemaVersion.V2_20);
    assertThat(descriptor.kind).isEqualTo("form");
    assertThat(descriptor.internalName).isEqualTo("НоваяФорма");

    assertThatThrownBy(() -> FormScaffold.addForm(objectXml, SchemaVersion.V2_20, "НоваяФорма"))
      .hasMessageContaining("уже");
  }

  @Test
  void compileFormBuildsItemsReadableBySchema() throws Exception {
    Path objectXml = copyCatalog();
    String definition = """
      {
        "synonym": "Карточка счёта",
        "mainAttribute": {"name": "Объект", "type": "cfg:CatalogObject._ДемоБанковскиеСчета"},
        "items": [
          {"group": "Шапка", "direction": "horizontal", "items": [
            {"input": "Наименование", "dataPath": "Объект.Description"},
            {"check": "Основной", "dataPath": "Объект.Основной"}
          ]},
          {"label": "Подсказка", "title": "Реквизиты банка ниже"},
          {"table": "Счета", "dataPath": "Объект.Счета", "columns": [
            {"input": "Номер", "dataPath": "Объект.Счета.Номер"}
          ]}
        ]
      }
      """;
    FormScaffold.compileForm(objectXml, SchemaVersion.V2_20, "Карточка", definition);

    Path content = tempDir.resolve("_ДемоБанковскиеСчета").resolve("Forms")
      .resolve("Карточка").resolve("Ext").resolve("Form.xml");
    String xml = Files.readString(content);
    assertThat(xml).contains("<UsualGroup name=\"Шапка\"");
    assertThat(xml).contains("<Table name=\"Счета\"");
    assertThat(xml).contains("<MainAttribute>true</MainAttribute>");

    // Содержимое обязано читаться и нашей высокоуровневой операцией формы
    FormContentDtoReadCheck.check(content);
  }

  /** Отдельный хелпер: контент формы читается операцией cf-form-content-get. */
  static final class FormContentDtoReadCheck {
    static void check(Path content) throws Exception {
      Object root = io.github.yellowhammer.designerxml.DesignerXml.read(content, SchemaVersion.V2_20);
      assertThat(root).isNotNull();
    }
  }

  @Test
  void dcsInfoReadsSchema() throws Exception {
    java.nio.file.Path dcs = Ssl31SubmodulePaths.projectRoot().resolve(
      "src/erf/_ДемоОтчетНоменклатураОперации/_ДемоНоменклатураОперации/Templates/ОсновнаяСхемаКомпоновкиДанных/Ext/Template.xml");
    java.util.Map<String, Object> info = DcsRead.info(dcs, SchemaVersion.V2_20);
    assertThat(info.get("dataSets")).asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.LIST).isNotEmpty();
    java.util.Map<String, Object> valid = DcsRead.validate(dcs, SchemaVersion.V2_20);
    assertThat(valid.get("valid")).isEqualTo(true);
  }
}
