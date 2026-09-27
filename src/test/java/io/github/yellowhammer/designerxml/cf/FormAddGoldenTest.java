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

import io.github.yellowhammer.designerxml.DesignerXml;
import io.github.yellowhammer.designerxml.SchemaVersion;
import io.github.yellowhammer.designerxml.Ssl31SubmodulePaths;
import io.github.yellowhammer.designerxml.XmlValidator;

import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * cf-form-add в каждом формате: пустая форма - проекция эталона платформы. Где платформа сняла
 * внешний отчёт с формой ({@code snapshots/<формат>/external-files/empty-full-objects}), новая форма
 * внешнего отчёта совпадает с его формой байт в байт после замены имени и UUID; в остальных форматах
 * описание формы проходит XSD, а описание и содержимое читаются моделью формата. Запись в составе
 * объекта встаёт туда же, куда её пишет платформа.
 */
class FormAddGoldenTest {

  private static final String FORM_PROTO = "Форма";

  private static final String REPORT_FORMS = "ВнешнийОтчет1/ВнешнийОтчет1/Forms/";

  private static final String META_DATA_OBJECT = "MetaDataObject";

  private static final String CHILD_OBJECTS = "ChildObjects";

  private static final Pattern VERSION = Pattern.compile("<(?:MetaDataObject|Form)\\b[^>]*\\sversion=\"([^\"]+)\"");

  /** Основной реквизит формы: вид типа ({@code CatalogObject}) из {@code cfg:CatalogObject.Имя}. */
  private static final Pattern MAIN_ATTRIBUTE = Pattern.compile(
    "<Attribute name=\"[^\"]*\" id=\"\\d+\">\\s*<Type>\\s*<v8:Type>cfg:([A-Za-z]+)\\.[^<]*</v8:Type>\\s*</Type>"
      + "\\s*(?:<[^>]*>\\s*)*?<MainAttribute>true");

  @TempDir
  Path workspace;

  /**
   * У каждого объекта ssl31 с формами последняя форма убирается из состава и добавляется снова:
   * файл объекта совпадает с выгрузкой платформы байт в байт, в том числе у объектов, где за формами
   * идут табличные части, макеты и команды, а у табличных частей свой состав.
   */
  @Test
  void формаВстаётВСоставТамЖеГдеУПлатформы() throws Exception {
    Path cf = Ssl31SubmodulePaths.projectRoot().resolve("src/cf");
    List<Path> objects;
    try (Stream<Path> walk = Files.walk(cf, 2)) {
      objects = walk.filter(path -> path.getNameCount() == cf.getNameCount() + 2)
        .filter(path -> path.getFileName().toString().endsWith(".xml"))
        .sorted()
        .toList();
    }
    SoftAssertions softly = new SoftAssertions();
    int checked = 0;
    for (Path object : objects) {
      String original = GoldenSnapshots.read(object);
      List<XmlLines.Node> owner = XmlLines.children(original, List.of(META_DATA_OBJECT));
      if (owner.isEmpty()) {
        continue;
      }
      String kind = owner.get(0).name();
      List<XmlLines.Node> forms = XmlLines.children(original, List.of(META_DATA_OBJECT, kind, CHILD_OBJECTS))
        .stream()
        .filter(node -> "Form".equals(node.name()))
        .toList();
      if (forms.isEmpty()) {
        continue;
      }
      XmlLines.Node last = forms.get(forms.size() - 1);
      String formName = original.substring(original.indexOf('>', last.start()) + 1, original.lastIndexOf('<', last.end()));
      int[] line = XmlLines.wholeLines(original, last.start(), last.end(), "Form");
      Path copy = workspace.resolve(cf.relativize(object).toString());
      Files.createDirectories(copy.getParent());
      Files.writeString(copy, original.substring(0, line[0]) + original.substring(line[1]));

      FormScaffold.addForm(copy, versionOf(original), formName);

      softly.assertThat(GoldenSnapshots.read(copy)).as("%s: форма %s", cf.relativize(object), formName).isEqualTo(original);
      checked++;
    }
    softly.assertAll();
    assertThat(checked).as("объектов ssl31 с формами").isGreaterThan(100);
  }

  @ParameterizedTest
  @EnumSource(SchemaVersion.class)
  void формаНеПопадаетВСоставТабличнойЧасти(SchemaVersion version) throws Exception {
    Path cf = workspace.resolve(version.name());
    EmptyCfScaffold.writeEmptyTree(cf, CfLayout.DEFAULT_CONFIGURATION_NAME, null, null, null, version);
    for (MdObjectAddType type : List.of(MdObjectAddType.CATALOG, MdObjectAddType.DOCUMENT)) {
      String owner = "Нов" + GoldenScaffold.protoName(type);
      MdObjectAdd.add(cf.resolve(CfLayout.CONFIGURATION_XML), owner, version, type);
      Path ownerXml = CfLayout.objectXmlInSubdir(cf, type.cfSubdir(), owner);
      // у новой табличной части состав пустой - <ChildObjects/>, как у самого объекта
      MdObjectChildMutations.addTabularSection(ownerXml, version, "Товары");

      FormScaffold.addForm(ownerXml, version, "ФормаЭлемента");

      String xml = GoldenSnapshots.read(ownerXml);
      List<String> composition = XmlLines.children(xml, List.of(META_DATA_OBJECT, type.configurationXmlTag(), CHILD_OBJECTS))
        .stream()
        .map(XmlLines.Node::name)
        .toList();
      List<String> order = ChildObjectKinds.of(version, type.configurationXmlTag());
      assertThat(composition).as("состав %s в формате %s", type, version)
        .contains("Form", "TabularSection")
        .isSortedAccordingTo((left, right) -> Integer.compare(order.indexOf(left), order.indexOf(right)));
      assertThat(MdObjectStructureRead.read(ownerXml, version).forms)
        .extracting(form -> form.name)
        .containsExactly("ФормаЭлемента");
    }
  }

  @ParameterizedTest
  @EnumSource(SchemaVersion.class)
  void формаВнешнегоОтчётаСовпадаетСВыгрузкойПлатформы(SchemaVersion version) throws Exception {
    String owner = "Нов" + GoldenScaffold.externalProtoName(ExternalArtifactKind.REPORT);
    String formName = "Нов" + FORM_PROTO;
    Path ownerXml = NewExternalArtifactXml.create(workspace, owner, ExternalArtifactKind.REPORT, version);

    FormScaffold.addForm(ownerXml, version, formName);

    Path forms = workspace.resolve(owner).resolve(owner).resolve("Forms");
    Path descriptor = forms.resolve(formName + ".xml");
    Path content = forms.resolve(formName).resolve("Ext").resolve("Form.xml");
    readable(descriptor, content, version);

    List<String> platformFiles = GoldenSnapshots.files(version, GoldenSnapshots.EXTERNAL_FULL);
    if (version == GoldenSnapshots.canonical()) {
      assertThat(platformFiles).as("эталон формы канонического формата").contains(REPORT_FORMS + FORM_PROTO + ".xml");
    }
    if (platformFiles.contains(REPORT_FORMS + FORM_PROTO + ".xml")) {
      assertThat(normalized(GoldenSnapshots.read(descriptor), formName, FORM_PROTO))
        .isEqualTo(GoldenSnapshots.normalizeUuids(
          GoldenSnapshots.read(version, GoldenSnapshots.EXTERNAL_FULL, REPORT_FORMS + FORM_PROTO + ".xml")));
      assertThat(GoldenSnapshots.read(content))
        .isEqualTo(GoldenSnapshots.read(version, GoldenSnapshots.EXTERNAL_FULL, REPORT_FORMS + FORM_PROTO + "/Ext/Form.xml"));
    }
  }

  @ParameterizedTest
  @EnumSource(SchemaVersion.class)
  void формаСправочникаСоздаётсяВКаждомФормате(SchemaVersion version) throws Exception {
    Path cf = workspace.resolve(version.name());
    EmptyCfScaffold.writeEmptyTree(cf, CfLayout.DEFAULT_CONFIGURATION_NAME, null, null, null, version);
    String owner = "Нов" + GoldenScaffold.protoName(MdObjectAddType.CATALOG);
    MdObjectAdd.add(cf.resolve(CfLayout.CONFIGURATION_XML), owner, version, MdObjectAddType.CATALOG);
    Path ownerXml = CfLayout.objectXmlInSubdir(cf, MdObjectAddType.CATALOG.cfSubdir(), owner);

    FormScaffold.addForm(ownerXml, version, "ФормаЭлемента");

    MdObjectStructureDto structure = MdObjectStructureRead.read(ownerXml, version);
    assertThat(structure.forms).extracting(form -> form.name).containsExactly("ФормаЭлемента");
    Path forms = ownerXml.resolveSibling(owner).resolve("Forms");
    readable(forms.resolve("ФормаЭлемента.xml"), forms.resolve("ФормаЭлемента").resolve("Ext").resolve("Form.xml"), version);
  }

  /**
   * Пространство схемы компоновки форма объекта объявляет по режиму совместимости конфигурации,
   * как и общая форма: с 8.3.19. Эталоны голых объектов сняты с конфигурации в режиме 8.3.12,
   * пустая конфигурация md-sparrow - в режиме платформы формата (2.10 - 8.3.17, 2.11 - 8.3.18,
   * с 2.12 - 8.3.19 и выше).
   */
  @ParameterizedTest
  @EnumSource(SchemaVersion.class)
  void схемаКомпоновкиВФормеОбъектаПоРежимуСовместимости(SchemaVersion version) throws Exception {
    Path bare = workspace.resolve("bare");
    Path bareCatalogs = bare.resolve(MdObjectAddType.CATALOG.cfSubdir());
    Files.createDirectories(bareCatalogs);
    Path bareSnapshot = GoldenSnapshots.format(version).resolve(GoldenSnapshots.CF);
    Files.copy(bareSnapshot.resolve(CfLayout.CONFIGURATION_XML), bare.resolve(CfLayout.CONFIGURATION_XML));
    String proto = GoldenScaffold.protoName(MdObjectAddType.CATALOG);
    Path bareCatalog = bareCatalogs.resolve(proto + ".xml");
    Files.copy(bareSnapshot.resolve(MdObjectAddType.CATALOG.cfSubdir()).resolve(proto + ".xml"), bareCatalog);
    FormScaffold.addForm(bareCatalog, version, "ФормаЭлемента");
    assertThat(GoldenSnapshots.read(bareCatalogs.resolve(proto).resolve("Forms").resolve("ФормаЭлемента")
      .resolve("Ext").resolve("Form.xml")))
      .as("режим 8.3.12, формат %s", version)
      .doesNotContain("xmlns:dcssch=");

    Path fresh = workspace.resolve("fresh");
    EmptyCfScaffold.writeEmptyTree(fresh, CfLayout.DEFAULT_CONFIGURATION_NAME, null, null, null, version);
    MdObjectAdd.add(fresh.resolve(CfLayout.CONFIGURATION_XML), "Товары", version, MdObjectAddType.CATALOG);
    Path freshCatalog = CfLayout.objectXmlInSubdir(fresh, MdObjectAddType.CATALOG.cfSubdir(), "Товары");
    FormScaffold.addForm(freshCatalog, version, "ФормаЭлемента");
    String content = GoldenSnapshots.read(freshCatalog.resolveSibling("Товары").resolve("Forms")
      .resolve("ФормаЭлемента").resolve("Ext").resolve("Form.xml"));
    boolean declared = content.contains("xmlns:dcssch=");
    assertThat(declared)
      .as("режим платформы формата %s", version)
      .isEqualTo(version.compareTo(SchemaVersion.V2_12) >= 0);
  }

  private static void readable(Path descriptor, Path content, SchemaVersion version) throws Exception {
    assertThat(MdObjectPropertiesEdit.readDto(descriptor, version).kind).isEqualTo("form");
    XmlValidator.validate(descriptor, version, Path.of(System.getProperty("xsd.root")));
    assertThat(DesignerXml.read(content, version)).isNotNull();
    for (Path file : List.of(descriptor, content)) {
      String text = GoldenSnapshots.read(file);
      assertThat(text).as("BOM %s", file).startsWith("﻿");
      assertThat(text.replace("\r\n", "")).as("CRLF %s", file).doesNotContain("\n");
      assertThat(text).as("версия %s", file).contains("version=\"" + version.metadataObjectVersionAttribute() + "\"");
    }
  }

  /**
   * Состав свойств описания формы зависит от вида владельца: {@code ExtendedPresentation} платформа
   * пишет только у форм отчётов и обработок. Сверка с формами каждого вида в выгрузке ssl31.
   */
  @Test
  void свойстваОписанияФормыКакУФормВидаВВыгрузке() throws Exception {
    Path src = Ssl31SubmodulePaths.projectRoot().resolve("src");
    Map<String, Set<List<String>>> platform = new TreeMap<>();
    SchemaVersion version = null;
    List<Path> descriptors = new ArrayList<>();
    // формы расширения не берём: у заимствованных форм свой состав свойств
    for (String part : List.of("cf", "epf", "erf")) {
      try (Stream<Path> walk = Files.walk(src.resolve(part))) {
        descriptors.addAll(walk.filter(FormAddGoldenTest::isFormDescriptor).toList());
      }
    }
    for (Path descriptor : descriptors) {
      String text = GoldenSnapshots.read(descriptor);
      if (!text.contains("<FormType>Managed</FormType>")) {
        continue;
      }
      // Forms/<форма>.xml лежит в каталоге владельца <имя>, описание владельца - рядом: <имя>.xml
      Path ownerDir = descriptor.getParent().getParent();
      String ownerXml = GoldenSnapshots.read(ownerDir.resolveSibling(ownerDir.getFileName() + ".xml"));
      version = versionOf(text);
      platform.computeIfAbsent(XmlLines.children(ownerXml, List.of(META_DATA_OBJECT)).get(0).name(), key -> new HashSet<>())
        .add(properties(text));
    }
    assertThat(platform).as("виды владельцев форм в ssl31").containsKeys("Catalog", "Document", "DataProcessor", "Report");
    SoftAssertions softly = new SoftAssertions();
    for (Map.Entry<String, Set<List<String>>> kind : platform.entrySet()) {
      softly.assertThat(kind.getValue()).as("формы вида %s в ssl31 записаны одинаково", kind.getKey()).hasSize(1);
      String ours = GoldenScaffold.generateFormDescriptor(kind.getKey(), "Владелец", "", "Форма", version);
      softly.assertThat(properties(ours)).as("свойства формы вида %s", kind.getKey())
        .isEqualTo(kind.getValue().iterator().next());
    }
    softly.assertAll();
  }

  private static boolean isFormDescriptor(Path path) {
    return path.getFileName().toString().endsWith(".xml")
      && path.getParent() != null
      && "Forms".equals(path.getParent().getFileName().toString());
  }

  private static List<String> properties(String descriptor) {
    return XmlLines.children(descriptor, List.of(META_DATA_OBJECT, "Form", "Properties")).stream()
      .map(XmlLines.Node::name)
      .toList();
  }

  /**
   * Свойства корня, которые платформа 2.20+ дописывает форме по виду основного реквизита: у каждого
   * вида из ssl31 у собранной формы те из них, что есть у всех форм ssl31 с таким основным реквизитом.
   */
  @Test
  void свойстваФормыПоОсновномуРеквизитуКакВВыгрузке() throws Exception {
    Map<String, Set<String>> platform = new TreeMap<>();
    SchemaVersion version = null;
    try (Stream<Path> walk = Files.walk(Ssl31SubmodulePaths.projectRoot().resolve("src/cf"))) {
      for (Path content : walk.filter(path -> path.endsWith(Path.of("Ext", "Form.xml"))).toList()) {
        String text = GoldenSnapshots.read(content);
        Matcher main = MAIN_ATTRIBUTE.matcher(text);
        if (content.getParent().getParent().getParent().getFileName().toString().equals("Forms") && main.find()) {
          version = versionOf(text);
          Set<String> names = new HashSet<>(rootProperties(text));
          platform.merge(main.group(1), names, (left, right) -> {
            left.retainAll(right);
            return left;
          });
        }
      }
    }
    assertThat(platform).as("виды основных реквизитов форм ssl31").containsKeys("CatalogObject", "DocumentObject");

    Path owner = compileOwner(version);
    Map<String, List<String>> ours = new TreeMap<>();
    for (String kind : platform.keySet()) {
      ours.put(kind, rootProperties(compiled(owner, version, "Форма" + kind, "cfg:" + kind + ".Объект1")));
    }
    Set<String> candidates = new HashSet<>();
    ours.values().forEach(candidates::addAll);
    assertThat(candidates).as("свойства, которые дописывает платформа").isNotEmpty();
    SoftAssertions softly = new SoftAssertions();
    for (String kind : platform.keySet()) {
      Set<String> expected = new HashSet<>(candidates);
      expected.retainAll(platform.get(kind));
      softly.assertThat(ours.get(kind)).as("свойства формы с основным реквизитом %s", kind)
        .containsExactlyInAnyOrderElementsOf(expected);
    }
    softly.assertAll();
  }

  /**
   * Свойства корня формы с основным реквизитом, которые платформа формата дописывает при загрузке и
   * выгружает: загрузка и выгрузка форм cf-form-compile ibcmd всех двенадцати линеек
   * (tools/golden-snapshots/roundtrip.py). 8.3.22-8.3.24 (2.15-2.17) не дописывают ничего, остальные -
   * по виду реквизита. {@code ViewModeApplicationOnSetReportResult} модель знает с 2.12, 8.3.17 и
   * 8.3.18 его не пишут. Форма внешнего отчёта проверена загрузкой только с 8.3.23 (раньше ibcmd
   * внешние объекты не собирает); в 2.10-2.15 для неё взято правило формы отчёта той же платформы.
   */
  private static Map<String, List<String>> platformRootProperties(SchemaVersion version) {
    if (version.compareTo(SchemaVersion.V2_15) >= 0 && version.compareTo(SchemaVersion.V2_17) <= 0) {
      return Map.of();
    }
    List<String> report = version.compareTo(SchemaVersion.V2_12) < 0
      ? List.of("ReportFormType", "AutoShowState", "ReportResultViewMode")
      : List.of("ReportFormType", "AutoShowState", "ReportResultViewMode", "ViewModeApplicationOnSetReportResult");
    return Map.of(
      "CatalogObject", List.of("UseForFoldersAndItems"),
      "ChartOfCharacteristicTypesObject", List.of("UseForFoldersAndItems"),
      "DocumentObject", List.of("AutoTime", "UsePostingMode", "RepostOnWrite"),
      "ReportObject", report,
      "ExternalReportObject", report);
  }

  @ParameterizedTest
  @EnumSource(SchemaVersion.class)
  void свойстваФормыПоОсновномуРеквизитуКакУПлатформыФормата(SchemaVersion version) throws Exception {
    Path owner = compileOwner(version);
    SoftAssertions softly = new SoftAssertions();
    for (String kind : List.of("CatalogObject", "ChartOfCharacteristicTypesObject", "DocumentObject", "ReportObject",
      "DataProcessorObject", "TaskObject", "ChartOfAccountsObject", "ChartOfCalculationTypesObject",
      "ExchangePlanObject")) {
      softly.assertThat(rootProperties(compiled(owner, version, "Форма" + kind, "cfg:" + kind + ".Объект1")))
        .as("форма с основным реквизитом %s в формате %s", kind, version)
        .containsExactlyElementsOf(platformRootProperties(version).getOrDefault(kind, List.of()));
    }
    softly.assertAll();
  }

  @ParameterizedTest
  @EnumSource(SchemaVersion.class)
  void свойстваФормыВнешнегоОтчётаКакУПлатформыФормата(SchemaVersion version) throws Exception {
    String owner = "Нов" + GoldenScaffold.externalProtoName(ExternalArtifactKind.REPORT);
    Path ownerXml = NewExternalArtifactXml.create(workspace, owner, ExternalArtifactKind.REPORT, version);

    String ours = compiled(ownerXml, version, "ФормаОтчета", "cfg:ExternalReportObject." + owner);

    List<String> expected = platformRootProperties(version).getOrDefault("ExternalReportObject", List.of());
    assertThat(rootProperties(ours)).as("формат %s", version).containsExactlyElementsOf(expected);
    // Эталоны внешних объектов собраны 8.3.27 и только разобраны своей платформой: с выгрузкой
    // сравнимы там, где платформа формата сама дописывает те же свойства
    String report = REPORT_FORMS + "ФормаОтчета/Ext/Form.xml";
    if (!expected.isEmpty() && GoldenSnapshots.files(version, GoldenSnapshots.EXTERNAL_FULL).contains(report)) {
      assertThat(rootLines(ours))
        .as("формат %s", version)
        .isEqualTo(rootLines(GoldenSnapshots.read(version, GoldenSnapshots.EXTERNAL_FULL, report)));
    }
  }

  private Path compileOwner(SchemaVersion version) throws Exception {
    Path cf = workspace.resolve("compile-" + version.name());
    EmptyCfScaffold.writeEmptyTree(cf, CfLayout.DEFAULT_CONFIGURATION_NAME, null, null, null, version);
    String owner = "Нов" + GoldenScaffold.protoName(MdObjectAddType.DATA_PROCESSOR);
    MdObjectAdd.add(cf.resolve(CfLayout.CONFIGURATION_XML), owner, version, MdObjectAddType.DATA_PROCESSOR);
    return CfLayout.objectXmlInSubdir(cf, MdObjectAddType.DATA_PROCESSOR.cfSubdir(), owner);
  }

  private static String compiled(Path ownerXml, SchemaVersion version, String formName, String mainType) throws Exception {
    String definition = "{\"mainAttribute\": {\"name\": \"Объект\", \"type\": \"" + mainType + "\"}}";
    FormScaffold.compileForm(ownerXml, version, formName, definition);
    String owner = ownerXml.getFileName().toString().replaceFirst("[.]xml$", "");
    return GoldenSnapshots.read(ownerXml.resolveSibling(owner).resolve("Forms").resolve(formName).resolve("Ext").resolve("Form.xml"));
  }

  /** Строки свойств корня формы до командной панели. */
  private static List<String> rootLines(String content) {
    String head = content.substring(content.indexOf('>', content.indexOf("<Form ")) + 1, content.indexOf("<AutoCommandBar"));
    return head.lines().map(String::strip).filter(line -> !line.isEmpty()).toList();
  }

  private static List<String> rootProperties(String content) {
    return rootLines(content).stream().map(line -> line.substring(1, line.indexOf('>'))).toList();
  }

  /** Эталон снят на русской конфигурации; подпись новой формы - на основном языке своей конфигурации. */
  @Test
  void подписьФормыНаЯзыкеКонфигурации() throws Exception {
    Path fixture = Path.of("src", "test", "resources", "cf-language-de").toAbsolutePath();
    Path cf = workspace.resolve("cf");
    try (Stream<Path> walk = Files.walk(fixture)) {
      for (Path source : walk.toList()) {
        Path target = cf.resolve(fixture.relativize(source).toString());
        if (Files.isDirectory(source)) {
          Files.createDirectories(target);
        } else {
          Files.copy(source, target);
        }
      }
    }
    Path catalog;
    try (Stream<Path> catalogs = Files.list(cf.resolve("Catalogs"))) {
      catalog = catalogs.filter(path -> path.toString().endsWith(".xml")).findFirst().orElseThrow();
    }
    ConfigurationLanguage.forget();
    String language = ConfigurationLanguage.codeOf(catalog);
    assertThat(language).as("основной язык фикстуры").isNotEqualTo("ru");

    FormScaffold.addForm(catalog, versionOf(GoldenSnapshots.read(catalog)), "ФормаЭлемента");

    String descriptor = GoldenSnapshots.read(catalog.resolveSibling(catalog.getFileName().toString().replace(".xml", ""))
      .resolve("Forms").resolve("ФормаЭлемента.xml"));
    assertThat(LocalStringElement.items(descriptor))
      .isNotEmpty()
      .allSatisfy(item -> assertThat(item.lang()).isEqualTo(language));
  }

  /** Одноимённые формы разных объектов - разные объекты метаданных: одинаковый UUID платформа не примет. */
  @ParameterizedTest
  @EnumSource(SchemaVersion.class)
  void одноимённыеФормыРазныхОбъектовПолучаютРазныеUuid(SchemaVersion version) throws Exception {
    Path cf = workspace.resolve(version.name());
    EmptyCfScaffold.writeEmptyTree(cf, CfLayout.DEFAULT_CONFIGURATION_NAME, null, null, null, version);
    List<String> descriptors = new ArrayList<>();
    for (String owner : List.of("Первый", "Второй")) {
      MdObjectAdd.add(cf.resolve(CfLayout.CONFIGURATION_XML), owner, version, MdObjectAddType.CATALOG);
      Path ownerXml = CfLayout.objectXmlInSubdir(cf, MdObjectAddType.CATALOG.cfSubdir(), owner);
      FormScaffold.addForm(ownerXml, version, "ФормаЭлемента");
      descriptors.add(GoldenSnapshots.read(ownerXml.resolveSibling(owner).resolve("Forms").resolve("ФормаЭлемента.xml")));
    }

    assertThat(formUuid(descriptors.get(0))).isNotEqualTo(formUuid(descriptors.get(1)));
  }

  /**
   * Переименованный объект уносит свои формы вместе с их UUID, а новый объект с прежним именем - уже
   * другой объект: его одноимённая форма получает другой UUID. С одним UUID на две формы платформа
   * молча сливает их в одну при загрузке.
   */
  @ParameterizedTest
  @EnumSource(SchemaVersion.class)
  void формаНовогоОбъектаСИменемПереименованногоПолучаетДругойUuid(SchemaVersion version) throws Exception {
    Path cf = workspace.resolve(version.name());
    EmptyCfScaffold.writeEmptyTree(cf, CfLayout.DEFAULT_CONFIGURATION_NAME, null, null, null, version);
    Path configuration = cf.resolve(CfLayout.CONFIGURATION_XML);
    MdObjectAddType catalog = MdObjectAddType.CATALOG;
    String name = MdObjectAdd.addWithNextAvailableName(configuration, version, catalog, null, false);
    Path ownerXml = CfLayout.objectXmlInSubdir(cf, catalog.cfSubdir(), name);
    FormScaffold.addForm(ownerXml, version, "ФормаЭлемента");
    String renamed = name + "Прежний";
    CfMdObjectMutations.rename(configuration, ownerXml, catalog.configurationXmlTag(), name, renamed);

    assertThat(MdObjectAdd.addWithNextAvailableName(configuration, version, catalog, null, false))
      .as("имя нового объекта").isEqualTo(name);
    FormScaffold.addForm(ownerXml, version, "ФормаЭлемента");

    String renamedForm = GoldenSnapshots.read(ownerXml.resolveSibling(renamed).resolve("Forms").resolve("ФормаЭлемента.xml"));
    String newForm = GoldenSnapshots.read(ownerXml.resolveSibling(name).resolve("Forms").resolve("ФормаЭлемента.xml"));
    assertThat(formUuid(newForm)).isNotEqualTo(formUuid(renamedForm));
  }

  private static String formUuid(String descriptor) {
    Matcher uuid = Pattern.compile("<Form uuid=\"([^\"]+)\"").matcher(descriptor);
    assertThat(uuid.find()).as("UUID формы").isTrue();
    return uuid.group(1);
  }

  private static SchemaVersion versionOf(String objectXml) {
    Matcher version = VERSION.matcher(objectXml);
    assertThat(version.find()).as("версия формата в заголовке").isTrue();
    return SchemaVersion.byVersionAttribute(version.group(1)).orElseThrow();
  }

  private static String normalized(String text, String name, String proto) {
    Pattern token = Pattern.compile("(?<![\\p{L}\\p{N}_])" + Pattern.quote(name) + "(?![\\p{L}\\p{N}_])");
    return GoldenSnapshots.normalizeUuids(token.matcher(text).replaceAll(Matcher.quoteReplacement(proto)));
  }
}
