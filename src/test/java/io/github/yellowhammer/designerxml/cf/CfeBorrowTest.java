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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

class CfeBorrowTest {

  @TempDir Path tempDir;

  @Test
  void borrowsCatalogIntoExtension() throws Exception {
    Path source = Ssl31SubmodulePaths.projectRoot()
      .resolve("src/cfe/_ДемоПустоеРасширение/Configuration.xml");
    Path extensionXml = tempDir.resolve("Configuration.xml");
    Files.copy(source, extensionXml, StandardCopyOption.REPLACE_EXISTING);
    Path objectXml = Ssl31SubmodulePaths.projectRoot().resolve("src/cf/Catalogs/_ДемоБанковскиеСчета.xml");

    Path created = CfeBorrow.borrowObject(objectXml, extensionXml, SchemaVersion.V2_20);

    assertThat(created).isEqualTo(tempDir.resolve("Catalogs").resolve("_ДемоБанковскиеСчета.xml"));
    MdObjectPropertiesDto adopted = MdObjectPropertiesEdit.readDto(created, SchemaVersion.V2_20);
    assertThat(adopted.kind).isEqualTo("catalog");
    assertThat(adopted.internalName).isEqualTo("_ДемоБанковскиеСчета");
    String xml = Files.readString(created);
    assertThat(xml).contains("<ObjectBelonging>Adopted</ObjectBelonging>");
    assertThat(xml).contains("<ChildObjects/>");
    assertThat(xml).contains("<xr:GeneratedType name=\"CatalogObject._ДемоБанковскиеСчета\"");
    // Идентификаторы свои: ни один uuid оригинала не переносится
    String original = Files.readString(objectXml);
    java.util.regex.Matcher ids = java.util.regex.Pattern
      .compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
      .matcher(xml);
    while (ids.find()) {
      assertThat(original).doesNotContain(ids.group());
    }

    String configuration = Files.readString(extensionXml);
    assertThat(configuration).contains("<Catalog>_ДемоБанковскиеСчета</Catalog>");

    assertThatThrownBy(() -> CfeBorrow.borrowObject(objectXml, extensionXml, SchemaVersion.V2_20))
      .hasMessageContaining("уже");
  }

  /**
   * Пустой {@code ChildObjects} допустим только у видов, у которых он есть в выгрузке.
   * У общего модуля и остальных видов без состава платформа отвергает этот элемент.
   */
  @Test
  void borrowedObjectKeepsChildObjectsOnlyForTypesThatHaveThem() throws Exception {
    Path extensionXml = tempDir.resolve("Configuration.xml");
    Files.copy(smallestExtensionConfiguration(), extensionXml, StandardCopyOption.REPLACE_EXISTING);

    String extension = Files.readString(extensionXml);
    int withChildObjects = 0;
    int withoutChildObjects = 0;
    for (Path objectXml : oneObjectPerCfDirectory()) {
      String source = Files.readString(objectXml);
      if (extension.contains(childEntry(source))) {
        continue;
      }
      Path created = CfeBorrow.borrowObject(objectXml, extensionXml, SchemaVersion.V2_20);
      String adopted = Files.readString(created);
      assertThat(adopted).as(objectXml.toString()).contains("<ObjectBelonging>Adopted</ObjectBelonging>");
      if (source.contains("<ChildObjects")) {
        assertThat(adopted).as(objectXml.toString()).contains("<ChildObjects/>");
        assertThat(adopted).as(objectXml.toString()).doesNotContain("<ChildObjects>");
        withChildObjects += 1;
      } else {
        assertThat(adopted).as(objectXml.toString()).doesNotContain("<ChildObjects");
        withoutChildObjects += 1;
      }
    }
    assertThat(withChildObjects).isPositive();
    assertThat(withoutChildObjects).isPositive();
  }

  /** Расширение с самым коротким составом: в него ещё не заимствованы объекты фикстуры. */
  private static Path smallestExtensionConfiguration() throws IOException {
    Path cfe = Ssl31SubmodulePaths.projectRoot().resolve("src").resolve("cfe");
    Path best = null;
    int bestSpan = Integer.MAX_VALUE;
    try (Stream<Path> walk = Files.walk(cfe)) {
      for (Path config : walk.filter(p -> p.getFileName().toString().equals("Configuration.xml")).toList()) {
        String xml = Files.readString(config);
        int open = xml.indexOf("<ChildObjects>");
        int close = xml.indexOf("</ChildObjects>");
        if (open < 0 || close < open) {
          continue;
        }
        int span = close - open;
        if (span < bestSpan) {
          bestSpan = span;
          best = config;
        }
      }
    }
    assertThat(best).isNotNull();
    return best;
  }

  /** Строка состава, как её пишет заимствование: {@code <Вид>Имя</Вид>}. */
  private static String childEntry(String objectXml) {
    Matcher root = Pattern.compile("<([A-Za-z]+) uuid=\"").matcher(objectXml);
    Matcher name = Pattern.compile("<Name>([^<]+)</Name>").matcher(objectXml);
    assertThat(root.find()).isTrue();
    assertThat(name.find()).isTrue();
    String tag = root.group(1);
    return "<" + tag + ">" + name.group(1).trim() + "</" + tag + ">";
  }

  /** По одному объекту из каждого каталога выгрузки, кроме служебного {@code Ext}. */
  private static List<Path> oneObjectPerCfDirectory() throws IOException {
    Path cf = Ssl31SubmodulePaths.projectRoot().resolve("src").resolve("cf");
    List<Path> samples = new ArrayList<>();
    try (Stream<Path> dirs = Files.list(cf)) {
      for (Path dir : dirs.filter(Files::isDirectory).sorted().toList()) {
        if ("Ext".equals(dir.getFileName().toString())) {
          continue;
        }
        try (Stream<Path> files = Files.list(dir)) {
          files.filter(Files::isRegularFile)
            .filter(p -> p.getFileName().toString().endsWith(".xml"))
            .sorted()
            .findFirst()
            .ifPresent(samples::add);
        }
      }
    }
    assertThat(samples).isNotEmpty();
    return samples;
  }
}
