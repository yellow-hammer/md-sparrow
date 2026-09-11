/*
 * This file is a part of md-sparrow.
 *
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package io.github.yellowhammer.designerxml.cf;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.yellowhammer.designerxml.SchemaVersion;
import io.github.yellowhammer.designerxml.Ssl31SubmodulePaths;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

/**
 * Язык текстов берётся у конфигурации.
 *
 * <p>Фикстура {@code cf-language-de} собрана пустой конфигурацией с двумя языками,
 * основным немецким, и загружена платформой 8.3.27 без замечаний.
 */
class ConfigurationLanguageTest {

  private static final Path FIXTURE = Path.of("src", "test", "resources", "cf-language-de").toAbsolutePath();

  @TempDir
  Path tempDir;

  @BeforeEach
  void forgetLanguages() {
    ConfigurationLanguage.forget();
  }

  @Test
  void основнойЯзыкКонфигурацииДаётКодСтрок() {
    assertThat(ConfigurationLanguage.codeOf(FIXTURE.resolve("Catalogs/Партнеры.xml"))).isEqualTo("de");
  }

  @Test
  void русскаяКонфигурацияОстаётсяРусской() throws Exception {
    assertThat(ConfigurationLanguage.codeOf(Ssl31SubmodulePaths.anyCatalogObjectXml())).isEqualTo("ru");
  }

  @Test
  void проектEdtЧитаетсяПоСвоемуОписанию() {
    Path edt = Path.of("src", "test", "resources", "edt-extension", "Основа", "src", "Catalogs", "Товары", "Товары.mdo")
      .toAbsolutePath();
    // У фикстуры проекта языков нет вовсе: остаётся запасной код
    assertThat(ConfigurationLanguage.codeOf(edt)).isEqualTo(ConfigurationLanguage.FALLBACK);
  }

  @Test
  void внешнийОбъектБезКонфигурацииИдётЗапаснымКодом() {
    assertThat(ConfigurationLanguage.codeOf(tempDir.resolve("Одинокий.xml")))
      .isEqualTo(ConfigurationLanguage.FALLBACK);
  }

  @Test
  void синонимЧитаетсяНаЯзыкеКонфигурации() throws Exception {
    Path copy = copyFixture();

    MdObjectPropertiesDto dto = MdObjectPropertiesEdit.readDto(
      copy.resolve("Catalogs/Партнеры.xml"), SchemaVersion.V2_20);

    assertThat(dto.synonym).isEqualTo("Geschäftspartner");
    // Панели нужен язык, чтобы человек видел, на каком языке правит подпись
    assertThat(dto.languageCode).isEqualTo("de");
  }

  @Test
  void синонимПишетсяНаЯзыкеКонфигурации() throws Exception {
    Path copy = copyFixture();
    Path object = copy.resolve("Catalogs/Партнеры.xml");

    MdObjectPropertiesDto dto = MdObjectPropertiesEdit.readDto(object, SchemaVersion.V2_20);
    dto.synonym = "Partner";
    MdObjectPropertiesEdit.writeDto(object, SchemaVersion.V2_20, dto);

    String synonym = synonymBlock(object);
    assertThat(synonym).contains("<v8:lang>de</v8:lang>").doesNotContain("<v8:lang>ru</v8:lang>");
    assertThat(MdObjectPropertiesEdit.readDto(object, SchemaVersion.V2_20).synonym).isEqualTo("Partner");
  }

  @Test
  void текстНаДругомЯзыкеПереживаетПравку() throws Exception {
    Path copy = copyFixture();
    Path object = copy.resolve("Catalogs/Партнеры.xml");
    Files.writeString(
      object,
      Files.readString(object, StandardCharsets.UTF_8).replaceFirst(
        "<Synonym>",
        "<Synonym><v8:item><v8:lang>en</v8:lang><v8:content>Partners</v8:content></v8:item>"),
      StandardCharsets.UTF_8);

    MdObjectPropertiesDto dto = MdObjectPropertiesEdit.readDto(object, SchemaVersion.V2_20);
    dto.synonym = "Partner";
    MdObjectPropertiesEdit.writeDto(object, SchemaVersion.V2_20, dto);

    String xml = Files.readString(object, StandardCharsets.UTF_8);
    assertThat(xml).contains("<v8:content>Partners</v8:content>");
    assertThat(xml).contains("<v8:content>Partner</v8:content>");
  }

  /** Блок синонима объекта: у остальных свойств свои строки, и они здесь не при чём. */
  private static String synonymBlock(Path object) throws IOException {
    String xml = Files.readString(object, StandardCharsets.UTF_8);
    int start = xml.indexOf("<Synonym");
    int end = xml.indexOf("</Synonym>", start);
    return end < 0 ? xml.substring(start, start + 20) : xml.substring(start, end);
  }

  @Test
  void свойстваКонфигурацииЧитаютсяНаЕёЯзыке() throws Exception {
    Path copy = copyFixture();

    ConfigurationPropertiesDto dto = ConfigurationPropertiesEdit.read(
      copy.resolve("Configuration.xml"), SchemaVersion.V2_20);

    assertThat(dto.languageCode).isEqualTo("de");
    assertThat(dto.localStringProperties).contains("synonym", "copyright", "briefInformation");
  }

  @Test
  void свойстваКонфигурацииПишутсяНаЕёЯзыке() throws Exception {
    Path copy = copyFixture();
    Path configuration = copy.resolve("Configuration.xml");

    ConfigurationPropertiesDto dto = ConfigurationPropertiesEdit.read(configuration, SchemaVersion.V2_20);
    dto.synonym = "Zweisprachige Demo";
    dto.copyright = "Alle Rechte vorbehalten";
    ConfigurationPropertiesEdit.write(configuration, SchemaVersion.V2_20, dto);

    String xml = Files.readString(configuration, StandardCharsets.UTF_8);
    assertThat(xml).contains("<v8:lang>de</v8:lang>").doesNotContain("<v8:lang>ru</v8:lang>");
    ConfigurationPropertiesDto again = ConfigurationPropertiesEdit.read(configuration, SchemaVersion.V2_20);
    assertThat(again.synonym).isEqualTo("Zweisprachige Demo");
    assertThat(again.copyright).isEqualTo("Alle Rechte vorbehalten");
  }
  @Test
  void новыйОбъектЗаводитсяНаЯзыкеКонфигурации() throws Exception {
    Path copy = copyFixture();

    String created = MdObjectAdd.addWithNextAvailableName(
      copy.resolve("Configuration.xml"), SchemaVersion.V2_20, MdObjectAddType.CATALOG, null, false);

    String xml = Files.readString(copy.resolve("Catalogs/" + created + ".xml"), StandardCharsets.UTF_8);
    assertThat(xml).contains("<v8:lang>de</v8:lang>").doesNotContain("<v8:lang>ru</v8:lang>");
  }

  @Test
  void заготовкаОстаётсяОдинаковойКромеЯзыкаИИдентификаторов() throws Exception {
    Path copy = copyFixture();
    String created = MdObjectAdd.addWithNextAvailableName(
      copy.resolve("Configuration.xml"), SchemaVersion.V2_20, MdObjectAddType.CATALOG, null, false);

    // Эталон один на версию формата, и от него отличаются только идентификаторы, имя и язык
    String xml = Files.readString(copy.resolve("Catalogs/" + created + ".xml"), StandardCharsets.UTF_8);
    assertThat(xml).contains("			<v8:item>");
    assertThat(xml.lines().filter(line -> line.contains("<Synonym>")).count()).isEqualTo(1L);
  }

  @Test
  void новыйРеквизитПодписываетсяНаЯзыкеКонфигурации() throws Exception {
    Path copy = copyFixture();
    Path object = copy.resolve("Catalogs/Партнеры.xml");

    MdObjectChildMutations.addAttribute(object, SchemaVersion.V2_20, "Адрес");

    String xml = Files.readString(object, StandardCharsets.UTF_8);
    int attribute = xml.indexOf("<Name>Адрес</Name>");
    assertThat(xml.substring(attribute, attribute + 300)).contains("<v8:lang>de</v8:lang>");
  }

  private Path copyFixture() throws IOException {
    Path target = tempDir.resolve("cf");
    copyTree(FIXTURE, target);
    return target;
  }

  private static void copyTree(Path source, Path target) throws IOException {
    if (Files.isDirectory(source)) {
      Files.createDirectories(target);
      try (Stream<Path> children = Files.list(source)) {
        for (Path child : children.toList()) {
          copyTree(child, target.resolve(child.getFileName().toString()));
        }
      }
      return;
    }
    Files.createDirectories(target.getParent());
    Files.copy(source, target);
  }
}
