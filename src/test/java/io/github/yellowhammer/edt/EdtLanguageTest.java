/*
 * This file is a part of md-sparrow.
 *
 * SPDX-License-Identifier: LGPL-3.0-or-later
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
