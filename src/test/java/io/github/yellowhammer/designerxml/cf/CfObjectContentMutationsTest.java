/*
 * This file is a part of md-sparrow.
 *
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package io.github.yellowhammer.designerxml.cf;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.yellowhammer.designerxml.SchemaVersion;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Содержимое объекта переезжает вместе с описанием.
 *
 * <p>Формы, макеты, команды и модули лежат в каталоге рядом с описанием, и описание
 * ссылается на них именами. Каталог, оставшийся под прежним именем, делает выгрузку
 * незагружаемой, поэтому переименование, копирование и удаление ведут его за собой.
 */
class CfObjectContentMutationsTest {

  private static final SchemaVersion VERSION = SchemaVersion.V2_20;
  private static final Pattern FORM_UUID = Pattern.compile("<Form uuid=\"([^\"]+)\"");

  @TempDir
  Path workspace;

  private Path cf;
  private Path configurationXml;
  private Path objectXml;

  @BeforeEach
  void createDumpWithContent() throws Exception {
    cf = workspace.resolve("cf");
    EmptyCfScaffold.writeEmptyTree(cf, CfLayout.DEFAULT_CONFIGURATION_NAME, null, null, null, VERSION);
    configurationXml = cf.resolve(CfLayout.CONFIGURATION_XML);
    MdObjectAdd.add(configurationXml, "Заказы", VERSION, MdObjectAddType.CATALOG, null, false);
    objectXml = CfLayout.catalogObjectXml(cf, "Заказы");
    copyContent(Path.of("src", "test", "resources", "cf-object-content").toAbsolutePath(), content("Заказы"));
  }

  @Test
  void переименованиеВедётКаталогСодержимогоЗаСобой() throws Exception {
    CfMdObjectMutations.rename(configurationXml, objectXml, "Catalog", "Заказы", "Продажи");

    assertThat(content("Заказы")).doesNotExist();
    assertThat(content("Продажи").resolve("Forms/ФормаСписка.xml")).exists();
    assertThat(content("Продажи").resolve("Ext/ManagerModule.bsl")).exists();
  }

  @Test
  void удалениеУноситКаталогСодержимого() throws Exception {
    CfMdObjectMutations.delete(configurationXml, objectXml, "Catalog", "Заказы");

    assertThat(content("Заказы")).doesNotExist();
    assertThat(objectXml).doesNotExist();
  }

  @Test
  void копияПолучаетСвоёСодержимоеСоСвоимиИдентификаторами() throws Exception {
    String before = formUuid(content("Заказы"));

    CfMdObjectMutations.duplicate(configurationXml, objectXml, "Catalog", "Заказы", "ЗаказыКопия");

    assertThat(content("Заказы").resolve("Forms/ФормаСписка.xml")).exists();
    assertThat(content("ЗаказыКопия").resolve("Ext/ManagerModule.bsl")).exists();
    assertThat(formUuid(content("ЗаказыКопия"))).isNotEqualTo(before);
    assertThat(formUuid(content("Заказы"))).isEqualTo(before);
  }

  @Test
  void модульКопииОстаётсяБайтВБайт() throws Exception {
    byte[] before = Files.readAllBytes(content("Заказы").resolve("Ext/ManagerModule.bsl"));

    CfMdObjectMutations.duplicate(configurationXml, objectXml, "Catalog", "Заказы", "ЗаказыКопия");

    assertThat(Files.readAllBytes(content("ЗаказыКопия").resolve("Ext/ManagerModule.bsl"))).isEqualTo(before);
  }

  private Path content(String objectName) {
    return cf.resolve("Catalogs").resolve(objectName);
  }

  private static String formUuid(Path contentDir) throws IOException {
    String xml = Files.readString(contentDir.resolve("Forms/ФормаСписка.xml"), StandardCharsets.UTF_8);
    Matcher uuid = FORM_UUID.matcher(xml);
    assertThat(uuid.find()).isTrue();
    return uuid.group(1);
  }

  private static void copyContent(Path source, Path target) throws IOException {
    if (Files.isDirectory(source)) {
      Files.createDirectories(target);
      try (Stream<Path> children = Files.list(source)) {
        for (Path child : children.toList()) {
          copyContent(child, target.resolve(child.getFileName().toString()));
        }
      }
      return;
    }
    Files.createDirectories(target.getParent());
    Files.copy(source, target);
  }
}
