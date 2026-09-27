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
package io.github.yellowhammer.designerxml.cf;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.yellowhammer.designerxml.SamplesSubmodulePaths;
import io.github.yellowhammer.designerxml.SchemaVersion;
import io.github.yellowhammer.designerxml.Ssl31SubmodulePaths;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

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

  /**
   * Заимствованный объект встаёт в состав расширения в порядке видов платформы, как и свой: в пустом
   * расширении платформы уже есть роль по умолчанию, и справочник или документ идут после неё, а
   * подсистема - перед ней. Строки - с отступом роли, без пустых строк.
   */
  @ParameterizedTest
  @EnumSource(SchemaVersion.class)
  void заимствованныеОбъектыВстаютВСоставВПорядкеПлатформы(SchemaVersion version) throws Exception {
    Path cfe = SamplesSubmodulePaths.copy(
      SamplesSubmodulePaths.snapshot(version, GoldenSnapshots.CFE), tempDir.resolve(version.name()));
    Path extensionXml = cfe.resolve(CfLayout.CONFIGURATION_XML);
    String indent = XmlLines.indentAt(GoldenSnapshots.read(extensionXml),
      GoldenSnapshots.read(extensionXml).indexOf("<Role>"));
    List<Path> objects = SamplesSubmodulePaths.objectXmls(SamplesSubmodulePaths.bareObjects(version));
    for (Path objectXml : objects) {
      CfeBorrow.borrowObject(objectXml, extensionXml, version);
    }

    String configuration = GoldenSnapshots.read(extensionXml);
    int open = configuration.indexOf('\n', configuration.indexOf("<ChildObjects>")) + 1;
    int close = configuration.lastIndexOf('\n', configuration.indexOf("</ChildObjects>")) + 1;
    List<String> lines = configuration.substring(open, close).lines().toList();
    assertThat(lines).as("строки состава").hasSize(objects.size() + 1)
      .allSatisfy(line -> assertThat(line).startsWith(indent + "<"));
    assertThat(CfDumpValidation.validate(cfe))
      .as("порядок состава")
      .noneMatch(finding -> CfDumpValidation.KIND_CHILD_OBJECTS_ORDER.equals(finding.kind()));
  }

  /**
   * Свойства заимствованного объекта в порядке платформы формата: загрузка и выгрузка расширения ibcmd
   * всех линеек, кроме 8.3.20 (tools/golden-snapshots/roundtrip.py; 8.3.20 расширение с ролью не
   * загружает, 2.13 выведен из 2.12 и 2.14). До 2.14 включительно принадлежность стоит после
   * комментария, с 2.15 - первой.
   */
  @ParameterizedTest
  @EnumSource(SchemaVersion.class)
  void свойстваЗаимствованногоОбъектаВПорядкеПлатформыФормата(SchemaVersion version) throws Exception {
    Path cfe = SamplesSubmodulePaths.copy(
      SamplesSubmodulePaths.snapshot(version, GoldenSnapshots.CFE), tempDir.resolve(version.name()));
    Path objectXml = SamplesSubmodulePaths.bareObjects(version).resolve("Catalogs").resolve("Справочник1.xml");

    Path created = CfeBorrow.borrowObject(objectXml, cfe.resolve(CfLayout.CONFIGURATION_XML), version);

    List<String> properties = XmlLines.children(GoldenSnapshots.read(created),
        List.of("MetaDataObject", "Catalog", "Properties")).stream()
      .map(XmlLines.Node::name)
      .toList();
    assertThat(properties).as("формат %s", version).containsExactlyElementsOf(
      version.compareTo(SchemaVersion.V2_14) <= 0
        ? List.of("Name", "Comment", "ObjectBelonging")
        : List.of("ObjectBelonging", "Name", "Comment"));
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
