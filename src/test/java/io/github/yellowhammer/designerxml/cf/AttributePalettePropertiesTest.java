/*
 * This file is a part of md-sparrow.
 *
 * Copyright (c) 2026
 * Ivan Karlo <i.karlo@outlook.com> and contributors
 *
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package io.github.yellowhammer.designerxml.cf;

import io.github.yellowhammer.designerxml.Ssl31SubmodulePaths;
import io.github.yellowhammer.designerxml.SchemaVersion;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Свойства палитры реквизита: проверка заполнения, индексирование, полнотекстовый
 * поиск, история данных и подсказка читаются и пишутся, иначе палитра показывает
 * их пустыми и теряет правку.
 */
class AttributePalettePropertiesTest {

  private static final String CATALOG = "src/cf/Catalogs/_ДемоБанковскиеСчета.xml";
  private static final String CATALOG_WITH_LINKS = "src/cf/Catalogs/_ДемоНоменклатура.xml";

  private static MdNamedPropertyDto attribute(MdObjectPropertiesDto dto, String name) {
    return dto.attributes.stream()
      .filter(a -> name.equals(a.name))
      .findFirst()
      .orElseThrow(() -> new AssertionError("нет реквизита " + name));
  }

  @Test
  void readsPalettePropertiesOfAttribute() throws Exception {
    Path xml = Ssl31SubmodulePaths.projectRoot().resolve(CATALOG);
    MdObjectPropertiesDto dto = MdObjectPropertiesEdit.readDto(xml, SchemaVersion.V2_20);

    assertThat(dto.attributes).isNotEmpty();
    MdNamedPropertyDto first = dto.attributes.get(0);
    assertThat(first.indexing).isNotBlank();
    assertThat(first.fillChecking).isNotBlank();
    assertThat(first.fullTextSearch).isNotBlank();
  }

  @Test
  void writesPalettePropertiesBack(@org.junit.jupiter.api.io.TempDir Path temp) throws Exception {
    Path source = Ssl31SubmodulePaths.projectRoot().resolve(CATALOG);
    Path xml = temp.resolve("_ДемоБанковскиеСчета.xml");
    Files.copy(source, xml, StandardCopyOption.REPLACE_EXISTING);

    MdObjectPropertiesDto dto = MdObjectPropertiesEdit.readDto(xml, SchemaVersion.V2_20);
    String name = dto.attributes.get(0).name;
    MdNamedPropertyDto edited = attribute(dto, name);
    edited.indexing = "INDEX";
    edited.fillChecking = "SHOW_ERROR";
    edited.toolTipRu = "Подсказка из палитры";
    MdObjectPropertiesEdit.writeDto(xml, SchemaVersion.V2_20, dto);

    MdObjectPropertiesDto again = MdObjectPropertiesEdit.readDto(xml, SchemaVersion.V2_20);
    MdNamedPropertyDto saved = attribute(again, name);
    assertThat(saved.indexing).isEqualTo("INDEX");
    assertThat(saved.fillChecking).isEqualTo("SHOW_ERROR");
    assertThat(saved.toolTipRu).isEqualTo("Подсказка из палитры");
  }

  @Test
  void readsChoiceParametersAndLinks() throws Exception {
    Path xml = Ssl31SubmodulePaths.projectRoot().resolve(CATALOG_WITH_LINKS);
    MdObjectPropertiesDto dto = MdObjectPropertiesEdit.readDto(xml, SchemaVersion.V2_20);

    MdNamedPropertyDto withLinks = dto.attributes.stream()
      .filter(a -> a.choiceParameterLinks != null && !a.choiceParameterLinks.isEmpty())
      .findFirst()
      .orElseThrow(() -> new AssertionError("в фикстуре нет реквизита со связями параметров выбора"));

    assertThat(withLinks.choiceParameterLinks.get(0).name).isNotBlank();
    assertThat(withLinks.choiceParameterLinks.get(0).dataPath).isNotBlank();
  }

  @Test
  void writesChoiceParameterLinksBack(@org.junit.jupiter.api.io.TempDir Path temp) throws Exception {
    Path source = Ssl31SubmodulePaths.projectRoot().resolve(CATALOG_WITH_LINKS);
    Path xml = temp.resolve("_ДемоНоменклатура.xml");
    Files.copy(source, xml, StandardCopyOption.REPLACE_EXISTING);

    MdObjectPropertiesDto dto = MdObjectPropertiesEdit.readDto(xml, SchemaVersion.V2_20);
    MdNamedPropertyDto attribute = dto.attributes.stream()
      .filter(a -> a.choiceParameterLinks != null && !a.choiceParameterLinks.isEmpty())
      .findFirst()
      .orElseThrow(() -> new AssertionError("в фикстуре нет реквизита со связями параметров выбора"));
    String name = attribute.name;
    attribute.choiceParameterLinks.get(0).dataPath = "Объект.Владелец";
    MdObjectPropertiesEdit.writeDto(xml, SchemaVersion.V2_20, dto);

    MdObjectPropertiesDto again = MdObjectPropertiesEdit.readDto(xml, SchemaVersion.V2_20);
    MdNamedPropertyDto saved = attribute(again, name);
    assertThat(saved.choiceParameterLinks.get(0).dataPath).isEqualTo("Объект.Владелец");
  }

  @Test
  void changesExactlyOneLinePerProperty(@org.junit.jupiter.api.io.TempDir Path temp) throws Exception {
    Path source = Ssl31SubmodulePaths.projectRoot().resolve(CATALOG);
    Path xml = temp.resolve("_ДемоБанковскиеСчета.xml");
    Files.copy(source, xml, StandardCopyOption.REPLACE_EXISTING);
    java.util.List<String> before = Files.readAllLines(xml);

    MdObjectPropertiesDto dto = MdObjectPropertiesEdit.readDto(xml, SchemaVersion.V2_20);
    MdNamedPropertyDto edited = dto.attributes.get(0);
    edited.indexing = "INDEX";
    MdObjectPropertiesEdit.writeDto(xml, SchemaVersion.V2_20, dto);

    java.util.List<String> after = Files.readAllLines(xml);
    assertThat(after).as("правка одного свойства не должна менять число строк").hasSameSizeAs(before);
    long changed = java.util.stream.IntStream.range(0, before.size())
      .filter(i -> !before.get(i).equals(after.get(i)))
      .count();
    assertThat(changed).as("изменилась ровно одна строка: %s", changed).isEqualTo(1L);
  }

  @Test
  void changesOnlyTouchedLinesForSeveralProperties(@org.junit.jupiter.api.io.TempDir Path temp) throws Exception {
    Path source = Ssl31SubmodulePaths.projectRoot().resolve(CATALOG);
    Path xml = temp.resolve("_ДемоБанковскиеСчета.xml");
    Files.copy(source, xml, StandardCopyOption.REPLACE_EXISTING);
    java.util.List<String> before = Files.readAllLines(xml);

    MdObjectPropertiesDto dto = MdObjectPropertiesEdit.readDto(xml, SchemaVersion.V2_20);
    MdNamedPropertyDto edited = dto.attributes.get(0);
    edited.indexing = "INDEX";
    edited.fillChecking = "SHOW_ERROR";
    edited.fullTextSearch = "DONT_USE";
    MdObjectPropertiesEdit.writeDto(xml, SchemaVersion.V2_20, dto);

    java.util.List<String> after = Files.readAllLines(xml);
    assertThat(after).hasSameSizeAs(before);
    long changed = java.util.stream.IntStream.range(0, before.size())
      .filter(i -> !before.get(i).equals(after.get(i)))
      .count();
    assertThat(changed).as("по строке на свойство: %s", changed).isLessThanOrEqualTo(3L);
  }

  @Test
  void keepsFileUntouchedWhenNothingChanged(@org.junit.jupiter.api.io.TempDir Path temp) throws Exception {
    Path source = Ssl31SubmodulePaths.projectRoot().resolve(CATALOG);
    Path xml = temp.resolve("_ДемоБанковскиеСчета.xml");
    Files.copy(source, xml, StandardCopyOption.REPLACE_EXISTING);
    byte[] before = Files.readAllBytes(xml);

    MdObjectPropertiesDto dto = MdObjectPropertiesEdit.readDto(xml, SchemaVersion.V2_20);
    MdObjectPropertiesEdit.writeDto(xml, SchemaVersion.V2_20, dto);

    assertThat(Files.readAllBytes(xml)).isEqualTo(before);
  }
}
