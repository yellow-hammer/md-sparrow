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
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

class CfeBorrowTest {

  @TempDir Path tempDir;

  /** Голый объект вида в эталоне: каталог выгрузки, файл и элемент объекта. */
  private record BorrowedKind(String folder, String file, String element) {
  }

  private static final List<BorrowedKind> KINDS = List.of(
    new BorrowedKind("Catalogs", "Справочник1.xml", "Catalog"),
    new BorrowedKind("Documents", "Документ1.xml", "Document"),
    new BorrowedKind("Subsystems", "Подсистема1.xml", "Subsystem"),
    new BorrowedKind("CommonForms", "ОбщаяФорма1.xml", "CommonForm"),
    new BorrowedKind("CommonTemplates", "ОбщийМакет1.xml", "CommonTemplate"),
    new BorrowedKind("CommonPictures", "ОбщаяКартинка1.xml", "CommonPicture"),
    new BorrowedKind("XDTOPackages", "ПакетXDTO1.xml", "XDTOPackage"),
    new BorrowedKind("DefinedTypes", "ОпределяемыйТип1.xml", "DefinedType"),
    new BorrowedKind("Roles", "Роль1.xml", "Role"));

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
   * комментария, с 2.15 - первой. Свойства вида платформа пишет последними.
   */
  @ParameterizedTest
  @EnumSource(SchemaVersion.class)
  void свойстваЗаимствованногоОбъектаВПорядкеПлатформыФормата(SchemaVersion version) throws Exception {
    Path cfe = SamplesSubmodulePaths.copy(
      SamplesSubmodulePaths.snapshot(version, GoldenSnapshots.CFE), tempDir.resolve(version.name()));
    List<String> common = version.compareTo(SchemaVersion.V2_14) <= 0
      ? List.of("Name", "Comment", "ObjectBelonging")
      : List.of("ObjectBelonging", "Name", "Comment");
    for (BorrowedKind kind : KINDS) {
      Path objectXml = SamplesSubmodulePaths.bareObjects(version).resolve(kind.folder()).resolve(kind.file());

      Path created = CfeBorrow.borrowObject(objectXml, cfe.resolve(CfLayout.CONFIGURATION_XML), version);

      List<String> expected = new ArrayList<>(common);
      expected.addAll(kindProperties(kind.element(), version));
      assertThat(properties(GoldenSnapshots.read(created), kind.element()).stream().map(XmlLines.Node::name))
        .as("%s, формат %s", kind.element(), version)
        .containsExactlyElementsOf(expected);
    }
  }

  /**
   * Свойства вида, которые платформа пишет у заимствованного объекта в формате: так выгружает
   * расширение ibcmd каждой линейки. Движения, состав, тип определяемого типа и вид формы - во всех
   * форматах, вид макета - с 2.15, доступность картинки - до 2.14, пространство имён пакета XDTO - до
   * 2.18.
   */
  private static List<String> kindProperties(String element, SchemaVersion version) {
    return switch (element) {
      case "Document" -> List.of("RegisterRecords");
      case "Subsystem" -> List.of("Content");
      case "DefinedType" -> List.of("Type");
      case "CommonForm" -> List.of("FormType");
      case "CommonTemplate" -> version.compareTo(SchemaVersion.V2_15) >= 0 ? List.of("TemplateType") : List.of();
      case "CommonPicture" -> version.compareTo(SchemaVersion.V2_14) <= 0
        ? List.of("AvailabilityForChoice", "AvailabilityForAppearance")
        : List.of();
      case "XDTOPackage" -> version.compareTo(SchemaVersion.V2_18) <= 0 ? List.of("Namespace") : List.of();
      default -> List.of();
    };
  }

  /**
   * Свойство вида, у которого в EDT нет состояния, состоянием не считается: только что заимствованный
   * объект ничего не контролирует, и панель не показывает контролируемыми ни движения, ни состав, ни
   * вид формы или макета. Доступность общей картинки (до 2.14) и пространство имён пакета XDTO (до
   * 2.18) панель, как и в выгрузке самой платформы, читает контролируемыми: контроль выгрузка отмечает
   * только наличием свойства.
   */
  @ParameterizedTest
  @EnumSource(SchemaVersion.class)
  void свойствоВидаНеДелаетЗаимствованныйОбъектКонтролирующим(SchemaVersion version) throws Exception {
    Path cfe = SamplesSubmodulePaths.copy(
      SamplesSubmodulePaths.snapshot(version, GoldenSnapshots.CFE), tempDir.resolve(version.name()));
    for (BorrowedKind kind : KINDS) {
      if (kind.element().equals("CommonPicture") || kind.element().equals("XDTOPackage")) {
        continue;
      }
      Path objectXml = SamplesSubmodulePaths.bareObjects(version).resolve(kind.folder()).resolve(kind.file());

      Path created = CfeBorrow.borrowObject(objectXml, cfe.resolve(CfLayout.CONFIGURATION_XML), version);

      MdObjectPropertiesDto dto = MdObjectPropertiesEdit.readDto(created, version);
      assertThat(dto.objectBelonging).as("%s, формат %s", kind.element(), version).isEqualTo(AdoptedStates.ADOPTED);
      assertThat(dto.propertyStates == null ? Map.of() : dto.propertyStates)
        .as("%s, формат %s", kind.element(), version)
        .doesNotContainValue(AdoptedStates.CHECKED);
    }
  }

  /**
   * Движения документа, состав подсистемы и тип определяемого типа платформа пишет пустыми: в них
   * попадает только то, что добавит расширение. Вид формы и макета, доступность картинки и пространство
   * имён пакета XDTO - как у оригинала, в том числе не те, что у голого объекта.
   */
  @ParameterizedTest
  @EnumSource(SchemaVersion.class)
  void свойствоВидаЗаимствованногоОбъектаПустоеИлиКакУОригинала(SchemaVersion version) throws Exception {
    Path cfe = SamplesSubmodulePaths.copy(
      SamplesSubmodulePaths.snapshot(version, GoldenSnapshots.CFE), tempDir.resolve(version.name()));
    Path extensionXml = cfe.resolve(CfLayout.CONFIGURATION_XML);
    Path bare = SamplesSubmodulePaths.bareObjects(version);

    assertThat(propertyXml(borrow(bare.resolve("Documents/Документ1.xml"), extensionXml, version),
      "Document", "RegisterRecords")).isEqualTo("<RegisterRecords/>");
    assertThat(propertyXml(borrow(bare.resolve("Subsystems/Подсистема1.xml"), extensionXml, version),
      "Subsystem", "Content")).isEqualTo("<Content/>");
    assertThat(propertyXml(borrow(bare.resolve("DefinedTypes/ОпределяемыйТип1.xml"), extensionXml, version),
      "DefinedType", "Type")).isEqualTo("<Type/>");
    assertThat(propertyXml(borrow(bare.resolve("CommonForms/ОбщаяФорма1.xml"), extensionXml, version),
      "CommonForm", "FormType")).isEqualTo("<FormType>Managed</FormType>");

    Path template = original(version, bare, "CommonTemplates/ОбщийМакет1.xml",
      "<TemplateType>SpreadsheetDocument</TemplateType>", "<TemplateType>TextDocument</TemplateType>");
    Path picture = original(version, bare, "CommonPictures/ОбщаяКартинка1.xml",
      "<AvailabilityForAppearance>false</AvailabilityForAppearance>",
      "<AvailabilityForAppearance>true</AvailabilityForAppearance>");
    Path xdto = original(version, bare, "XDTOPackages/ПакетXDTO1.xml",
      "<Namespace>http://www.sample-package.org</Namespace>", "<Namespace>urn:проверка</Namespace>");
    String borrowedTemplate = borrow(template, extensionXml, version);
    String borrowedPicture = borrow(picture, extensionXml, version);
    String borrowedXdto = borrow(xdto, extensionXml, version);
    if (version.compareTo(SchemaVersion.V2_15) >= 0) {
      assertThat(propertyXml(borrowedTemplate, "CommonTemplate", "TemplateType"))
        .isEqualTo("<TemplateType>TextDocument</TemplateType>");
    }
    if (version.compareTo(SchemaVersion.V2_14) <= 0) {
      assertThat(propertyXml(borrowedPicture, "CommonPicture", "AvailabilityForChoice"))
        .isEqualTo("<AvailabilityForChoice>false</AvailabilityForChoice>");
      assertThat(propertyXml(borrowedPicture, "CommonPicture", "AvailabilityForAppearance"))
        .isEqualTo("<AvailabilityForAppearance>true</AvailabilityForAppearance>");
    }
    if (version.compareTo(SchemaVersion.V2_18) <= 0) {
      assertThat(propertyXml(borrowedXdto, "XDTOPackage", "Namespace")).isEqualTo("<Namespace>urn:проверка</Namespace>");
    }
  }

  /** Голый объект эталона с одной заменой в тексте - оригинал, у которого свойство не как по умолчанию. */
  private Path original(SchemaVersion version, Path bare, String relative, String from, String to) throws IOException {
    String xml = GoldenSnapshots.read(bare.resolve(relative));
    assertThat(xml).as(relative).contains(from);
    Path copy = tempDir.resolve(version.name() + "-cf").resolve(relative);
    Files.createDirectories(copy.getParent());
    Files.writeString(copy, xml.replace(from, to));
    return copy;
  }

  /**
   * Свойства заимствованного объекта те же, что выгрузила платформа у объектов, заимствованных в
   * _ДемоРасширение (ssl31, формат 2.20) и ничем не дополненных.
   */
  @Test
  void свойстваЗаимствованногоОбъектаКакВДемоРасширении() throws Exception {
    Path src = Ssl31SubmodulePaths.projectRoot().resolve("src");
    Path extensionXml = tempDir.resolve("Configuration.xml");
    Files.copy(src.resolve("cfe/_ДемоПустоеРасширение/Configuration.xml"), extensionXml,
      StandardCopyOption.REPLACE_EXISTING);
    for (String object : List.of(
      "Catalogs/_ДемоКонтрагенты.xml",
      "Documents/_ДемоПоступлениеТоваров.xml",
      "Subsystems/_ДемоУправлениеДоступом.xml",
      "CommonForms/РедактированиеТабличногоДокумента.xml",
      "DefinedTypes/ДенежнаяСуммаНеотрицательная.xml")) {
      Path created = CfeBorrow.borrowObject(src.resolve("cf").resolve(object), extensionXml, SchemaVersion.V2_20);

      assertThat(propertiesBlock(GoldenSnapshots.read(created))).as(object)
        .isEqualTo(propertiesBlock(GoldenSnapshots.read(src.resolve("cfe/_ДемоРасширение").resolve(object))));
    }
  }

  private static String borrow(Path objectXml, Path extensionXml, SchemaVersion version) throws Exception {
    return GoldenSnapshots.read(CfeBorrow.borrowObject(objectXml, extensionXml, version));
  }

  private static List<XmlLines.Node> properties(String xml, String element) {
    return XmlLines.children(xml, List.of("MetaDataObject", element, "Properties"));
  }

  /** Свойство объекта как оно записано в файле. */
  private static String propertyXml(String xml, String element, String property) {
    XmlLines.Node node = properties(xml, element).stream()
      .filter(child -> child.name().equals(property))
      .findFirst()
      .orElseThrow(() -> new AssertionError("нет свойства " + property));
    return xml.substring(node.start(), node.end());
  }

  /** Блок {@code Properties} объекта с переводами строк LF. */
  private static String propertiesBlock(String xml) {
    int start = xml.indexOf("<Properties>");
    int end = xml.indexOf("</Properties>");
    assertThat(start).isNotNegative();
    return xml.substring(start, end).replace("\r\n", "\n");
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
