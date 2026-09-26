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
package io.github.yellowhammer.edt;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.yellowhammer.designerxml.cf.ConfigurationLanguage;
import io.github.yellowhammer.designerxml.cf.MdObjectAddType;
import io.github.yellowhammer.designerxml.cf.MdObjectPropertiesDto;

/**
 * Язык текстов в проекте 1С:EDT.
 *
 * <p>Фикстура {@code edt-language-de} собрана из проекта, записанного самой
 * 1С:EDT: у него объявлены два языка, а основным стоит немецкий.
 */
class EdtLanguageTest {

  private static final Path FIXTURE =
    Path.of("src", "test", "resources", "edt-language-de").toAbsolutePath();

  private static EdtModel model;

  @TempDir
  Path workDir;

  @BeforeAll
  static void bundle() throws Exception {
    model = EdtModel.bundled();
  }

  @BeforeEach
  void forgetLanguages() {
    ConfigurationLanguage.forget();
  }

  @Test
  void основнойЯзыкПроектаДаётКодСтрок() throws Exception {
    Path project = copyFixture();

    assertThat(ConfigurationLanguage.codeOf(project.resolve("src/Catalogs/Товары/Товары.mdo")))
      .isEqualTo("de");
  }

  @Test
  void свойстваОбъектаЧитаютсяНаЯзыкеКонфигурации() throws Exception {
    Path object = copyFixture().resolve("src/Catalogs/Товары/Товары.mdo");

    MdObjectPropertiesDto dto = EdtObjectProperties.readDto(object, model);

    assertThat(dto.languageCode).isEqualTo("de");
    // На немецком подписи ещё нет: русская остаётся в файле, но контракт её не несёт
    assertThat(dto.synonym).isEmpty();
    assertThat(dto.localStringProperties).contains("synonym", "objectPresentation");
  }

  @Test
  void новаяПодписьВстаётРядомСРусской() throws Exception {
    Path object = copyFixture().resolve("src/Catalogs/Товары/Товары.mdo");

    MdObjectPropertiesDto dto = EdtObjectProperties.readDto(object, model);
    dto.synonym = "Waren";
    EdtObjectWriter.writeDto(object, dto, model);

    String xml = Files.readString(object, StandardCharsets.UTF_8);
    assertThat(xml).contains("<key>de</key>", "<value>Waren</value>");
    // Русский текст остался, и подписей стало две
    assertThat(xml).contains("<value>Товары</value>");
    assertThat(synonymBlocks(xml)).isEqualTo(2);
    assertThat(EdtObjectProperties.readDto(object, model).synonym).isEqualTo("Waren");
  }

  @Test
  void подписьНовогоРеквизитаНаЯзыкеКонфигурации() throws Exception {
    Path object = copyFixture().resolve("src/Catalogs/Товары/Товары.mdo");

    EdtChildMutations.add(object, model, "attributes", "Артикул");

    String xml = Files.readString(object, StandardCharsets.UTF_8);
    assertThat(xml).contains("<key>de</key>");
    MdObjectPropertiesDto dto = EdtObjectProperties.readDto(object, model);
    assertThat(dto.attributes).extracting(node -> node.name).contains("Артикул");
    assertThat(dto.attributes)
      .filteredOn(node -> node.name.equals("Артикул"))
      .extracting(node -> node.synonym)
      .containsExactly("Артикул");
  }

  @Test
  void подписиНовогоОбъектаНаЯзыкеКонфигурации() throws Exception {
    Path project = copyFixture();
    Path configuration = project.resolve("src/Configuration/Configuration.mdo");

    EdtObjectScaffold.add(configuration, model, MdObjectAddType.CATALOG, "Artikel");

    String xml = Files.readString(project.resolve("src/Catalogs/Artikel/Artikel.mdo"), StandardCharsets.UTF_8);
    // Эталон записан по-русски: все его подписи, не только синоним, уходят в немецкий
    assertThat(xml).contains("<key>de</key>").doesNotContain("<key>ru</key>");
    MdObjectPropertiesDto dto = EdtObjectProperties.readDto(project.resolve("src/Catalogs/Artikel/Artikel.mdo"), model);
    assertThat(dto.synonym).isEqualTo("Artikel");
  }

  @Test
  void подписьНовойФормыНаЯзыкеКонфигурации() throws Exception {
    Path object = copyFixture().resolve("src/Catalogs/Товары/Товары.mdo");

    EdtObjectScaffold.addForm(object, model, "Artikelform");

    String xml = Files.readString(object, StandardCharsets.UTF_8);
    String form = xml.substring(xml.indexOf("<name>Artikelform</name>"), xml.indexOf("</forms>", xml.indexOf("<name>Artikelform</name>")));
    assertThat(form).contains("<key>de</key>", "<value>Artikelform</value>").doesNotContain("<key>ru</key>");
  }

  @Test
  void синонимНовогоОбъектаЗадаётсяИлиОстаётсяПустым() throws Exception {
    Path project = copyFixture();
    Path configuration = project.resolve("src/Configuration/Configuration.mdo");

    EdtObjectScaffold.add(configuration, model, MdObjectAddType.CATALOG, "Waren", "Warenkatalog", false);
    EdtObjectScaffold.add(configuration, model, MdObjectAddType.CATALOG, "Lager", null, true);

    Path named = project.resolve("src/Catalogs/Waren/Waren.mdo");
    assertThat(EdtObjectProperties.readDto(named, model).synonym).isEqualTo("Warenkatalog");
    // Представления объекта остаются как в эталоне: меняется только синоним
    assertThat(Files.readString(named, StandardCharsets.UTF_8)).contains("<value>Waren</value>");
    Path empty = project.resolve("src/Catalogs/Lager/Lager.mdo");
    assertThat(synonymBlocks(Files.readString(empty, StandardCharsets.UTF_8))).isZero();
    assertThat(EdtObjectProperties.readDto(empty, model).synonym).isEmpty();
  }

  /** Число подписей верхнего уровня: у каждого языка свой элемент. */
  private static int synonymBlocks(String xml) {
    return xml.split("(?m)^  <synonym>", -1).length - 1;
  }

  private Path copyFixture() throws IOException {
    Path target = workDir.resolve("Двуязычная");
    copyTree(FIXTURE.resolve("Двуязычная"), target);
    return target;
  }

  private static void copyTree(Path source, Path target) throws IOException {
    try (Stream<Path> tree = Files.walk(source)) {
      for (Path path : tree.toList()) {
        Path copy = target.resolve(source.relativize(path).toString());
        if (Files.isDirectory(path)) {
          Files.createDirectories(copy);
        } else {
          Files.createDirectories(copy.getParent());
          Files.copy(path, copy);
        }
      }
    }
  }
}
