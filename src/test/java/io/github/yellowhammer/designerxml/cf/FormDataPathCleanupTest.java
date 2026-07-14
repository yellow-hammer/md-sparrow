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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Зачистка привязок в формах при удалении и переименовании реквизитов (поведение конфигуратора).
 */
class FormDataPathCleanupTest {

  @Test
  void deleteAttribute_removesBoundFormItemsAndPropertyRefs() throws Exception {
    Path objectXml = copyCatalogWithForms();
    // Искусственно добавляем ссылку на удаляемый реквизит во «Ввод по строке».
    String xml = Files.readString(objectXml, StandardCharsets.UTF_8);
    xml = xml.replace(
      "<xr:Field>Catalog._ДемоБанковскиеСчета.StandardAttribute.Code</xr:Field>",
      "<xr:Field>Catalog._ДемоБанковскиеСчета.StandardAttribute.Code</xr:Field>\r\n"
        + "\t\t\t\t<xr:Field>Catalog._ДемоБанковскиеСчета.Attribute.Комментарий</xr:Field>");
    Files.writeString(objectXml, xml, StandardCharsets.UTF_8);

    MdObjectChildMutations.deleteAttribute(objectXml, SchemaVersion.V2_20, "Комментарий");

    String objectAfter = Files.readString(objectXml, StandardCharsets.UTF_8);
    assertThat(objectAfter).doesNotContain(".Attribute.Комментарий</xr:Field>");

    String itemForm = readForm(objectXml, "ФормаЭлемента");
    assertThat(itemForm).doesNotContain("<DataPath>Объект.Комментарий</DataPath>");
    assertThat(itemForm).doesNotContain("<InputField name=\"Комментарий\"");
    // Целостность: другие элементы не задеты.
    assertThat(itemForm).contains("<DataPath>Объект.НомерСчета</DataPath>");
  }

  @Test
  void renameAttribute_updatesDataPathsInAllForms() throws Exception {
    Path objectXml = copyCatalogWithForms();

    MdObjectChildMutations.renameAttribute(objectXml, SchemaVersion.V2_20, "Валюта", "ВалютаНовая");

    String itemForm = readForm(objectXml, "ФормаЭлемента");
    assertThat(itemForm).contains("<DataPath>Объект.ВалютаНовая</DataPath>");
    assertThat(itemForm).doesNotContain("<DataPath>Объект.Валюта</DataPath>");
    String listForm = readForm(objectXml, "ФормаСписка");
    assertThat(listForm).contains("<DataPath>Список.ВалютаНовая</DataPath>");
    assertThat(listForm).doesNotContain("<DataPath>Список.Валюта</DataPath>");
  }

  @Test
  void deleteTabularSection_removesTableWithColumns() throws Exception {
    // ТЧ «Сотрудники» с таблицей на форме элемента.
    Path objectXml = copyObjectWithForms("_ДемоПодразделения");
    boolean hasBoundTable = false;
    try (Stream<Path> forms = Files.walk(objectXml.resolveSibling("_ДемоПодразделения").resolve("Forms"))) {
      hasBoundTable = forms
        .filter(p -> p.getFileName().toString().equals("Form.xml"))
        .anyMatch(p -> {
          try {
            return Files.readString(p, StandardCharsets.UTF_8).contains("<DataPath>Объект.Сотрудники</DataPath>");
          } catch (IOException e) {
            return false;
          }
        });
    }
    MdObjectChildMutations.deleteTabularSection(objectXml, SchemaVersion.V2_20, "Сотрудники");

    assertThat(hasBoundTable).as("фикстура должна содержать таблицу, привязанную к ТЧ").isTrue();
    try (Stream<Path> forms = Files.walk(objectXml.resolveSibling("_ДемоПодразделения").resolve("Forms"))) {
      forms.filter(p -> p.getFileName().toString().equals("Form.xml")).forEach(p -> {
        try {
          String text = Files.readString(p, StandardCharsets.UTF_8);
          assertThat(text).doesNotContain("<DataPath>Объект.Сотрудники</DataPath>");
          assertThat(text).doesNotContain("<DataPath>Объект.Сотрудники.");
        } catch (IOException e) {
          throw new IllegalStateException(e);
        }
      });
    }
  }

  private static String readForm(Path objectXml, String formName) throws IOException {
    String stem = objectXml.getFileName().toString().replaceFirst("\\.xml$", "");
    return Files.readString(
      objectXml.resolveSibling(stem).resolve("Forms").resolve(formName).resolve("Ext").resolve("Form.xml"),
      StandardCharsets.UTF_8);
  }

  private static Path copyCatalogWithForms() throws IOException {
    return copyObjectWithForms("_ДемоБанковскиеСчета");
  }

  private static Path copyObjectWithForms(String name) throws IOException {
    Path src = Path.of(System.getProperty("fixtures.ssl31.root"), "src", "cf", "Catalogs");
    Path dir = Files.createTempDirectory("form-cleanup-");
    Path objectXml = dir.resolve(name + ".xml");
    Files.copy(src.resolve(name + ".xml"), objectXml);
    Path formsSrc = src.resolve(name).resolve("Forms");
    if (Files.isDirectory(formsSrc)) {
      try (Stream<Path> walk = Files.walk(formsSrc)) {
        walk.forEach(p -> {
          try {
            Path target = dir.resolve(name).resolve("Forms").resolve(formsSrc.relativize(p).toString());
            if (Files.isDirectory(p)) {
              Files.createDirectories(target);
            } else {
              Files.createDirectories(target.getParent());
              Files.copy(p, target);
            }
          } catch (IOException e) {
            throw new IllegalStateException(e);
          }
        });
      }
    }
    return objectXml;
  }
}
