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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamReader;

import org.eclipse.emf.ecore.EStructuralFeature;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import io.github.yellowhammer.designerxml.SchemaVersion;
import io.github.yellowhammer.designerxml.cf.MdObjectAddType;
import io.github.yellowhammer.designerxml.cf.MdObjectPropertiesDto;
import io.github.yellowhammer.designerxml.cf.MdObjectStructureDto;
import io.github.yellowhammer.designerxml.cf.ProjectMetadataTreeBuilder;
import io.github.yellowhammer.designerxml.cf.ProjectMetadataTreeDto;

/**
 * Новые объекты и формы проекта EDT.
 *
 * Заготовки записала сама 1С:EDT; здесь проверяется, что они встают в проект
 * под своим именем, со своими идентификаторами и на своё место в составе.
 */
class EdtObjectScaffoldTest {

  private static final Pattern UUID_TOKEN = Pattern.compile(
      "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");

  private static EdtModel model;
  private static Path fixture;

  @TempDir
  Path workDir;

  @BeforeAll
  static void locate() throws Exception {
    model = EdtModel.bundled();
    fixture = Path.of(System.getProperty("fixtures.ssl31edt.root"), "ssl31", "src");
    assertThat(fixture).exists();
  }

  /** Проект из описания конфигурации и одного справочника: фикстуру не правим. */
  private Path source() throws IOException {
    Path root = workDir.resolve("src");
    copy(fixture.resolve("Configuration"), root.resolve("Configuration"));
    copy(fixture.resolve("Catalogs/Валюты"), root.resolve("Catalogs/Валюты"));
    return root;
  }

  private static void copy(Path from, Path to) throws IOException {
    try (Stream<Path> files = Files.walk(from)) {
      for (Path file : files.toList()) {
        Path target = to.resolve(from.relativize(file).toString());
        if (Files.isDirectory(file)) {
          Files.createDirectories(target);
        } else {
          Files.createDirectories(target.getParent());
          Files.copy(file, target);
        }
      }
    }
  }

  private static Path configuration(Path root) {
    return root.resolve("Configuration/Configuration.mdo");
  }

  private static List<String> names(Path root, String objectType) throws IOException {
    return EdtConfigurationLists.names(configuration(root), model, objectType);
  }

  @Test
  void создаётСправочникПодСвободнымИменем() throws Exception {
    Path root = source();

    String name = EdtObjectScaffold.addWithNextAvailableName(configuration(root), model, MdObjectAddType.CATALOG);

    assertThat(name).isEqualTo("Справочник1");
    Path mdo = root.resolve("Catalogs/Справочник1/Справочник1.mdo");
    assertThat(mdo).exists();
    MdObjectPropertiesDto dto = EdtObjectProperties.readDto(mdo, model);
    assertThat(dto.kind).isEqualTo("catalog");
    assertThat(dto.internalName).isEqualTo("Справочник1");
    assertThat(dto.synonym).isEqualTo("Справочник1");
    assertThat(names(root, "Catalog")).contains("Валюты", "Справочник1");
    // Второй объект того же вида получает следующий номер
    assertThat(EdtObjectScaffold.addWithNextAvailableName(configuration(root), model, MdObjectAddType.CATALOG))
        .isEqualTo("Справочник2");
  }

  @Test
  void идентификаторыНовогоОбъектаСвои() throws Exception {
    Path root = source();
    EdtObjectScaffold.add(configuration(root), model, MdObjectAddType.CATALOG, "Первый");
    EdtObjectScaffold.add(configuration(root), model, MdObjectAddType.CATALOG, "Второй");

    java.util.Set<String> first = uuids(root.resolve("Catalogs/Первый/Первый.mdo"));
    java.util.Set<String> second = uuids(root.resolve("Catalogs/Второй/Второй.mdo"));
    // У справочника идентификатор объекта и пять пар порождаемых типов
    assertThat(first).hasSize(11);
    assertThat(first).doesNotContainAnyElementsOf(second);
  }

  private static java.util.Set<String> uuids(Path file) throws IOException {
    java.util.Set<String> found = new java.util.LinkedHashSet<>();
    Matcher matcher = UUID_TOKEN.matcher(Files.readString(file, StandardCharsets.UTF_8));
    while (matcher.find()) {
      found.add(matcher.group());
    }
    return found;
  }

  @Test
  void ссылкаВСоставеВстаётПоПорядкуСхемы() throws Exception {
    Path root = source();
    // В составе фикстуры нумераторов документов нет: ссылка встаёт на место по схеме
    EdtObjectScaffold.add(configuration(root), model, MdObjectAddType.DOCUMENT_NUMERATOR, "Нумератор");

    String xml = Files.readString(configuration(root), StandardCharsets.UTF_8);
    int numerator = xml.indexOf("<documentNumerators>DocumentNumerator.Нумератор</documentNumerators>");
    assertThat(numerator).isPositive();
    assertThat(numerator).isGreaterThan(xml.lastIndexOf("<documents>"));
    assertThat(numerator).isLessThan(xml.indexOf("<enums>"));
    assertThat(names(root, "DocumentNumerator")).containsExactly("Нумератор");
  }

  @Test
  void рольСоздаётсяВместеСПравами() throws Exception {
    Path root = source();
    EdtObjectScaffold.add(configuration(root), model, MdObjectAddType.ROLE, "Кладовщик");

    assertThat(root.resolve("Roles/Кладовщик/Кладовщик.mdo")).exists();
    assertThat(root.resolve("Roles/Кладовщик/Rights.rights")).exists();
    assertThat(names(root, "Role")).contains("Кладовщик");
  }

  @Test
  void каждыйВидОбъектаСоздаётсяИЧитается() throws Exception {
    Path root = source();
    for (MdObjectAddType kind : MdObjectAddType.values()) {
      String name = EdtObjectScaffold.addWithNextAvailableName(configuration(root), model, kind);
      Path mdo = root.resolve(kind.cfSubdir()).resolve(name).resolve(name + ".mdo");
      assertThat(mdo).as(kind.name()).exists();
      assertThat(EdtObjectProperties.readDto(mdo, model).internalName).as(kind.name()).isEqualTo(name);
      assertThat(names(root, kind.configurationXmlTag())).as(kind.name()).contains(name);
    }
  }

  @Test
  void занятоеИмяОтклоняется() throws Exception {
    Path root = source();

    assertThatThrownBy(() -> EdtObjectScaffold.add(configuration(root), model, MdObjectAddType.CATALOG, "Валюты"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("уже есть");
  }

  @Test
  void формаДобавляетсяИУдаляется() throws Exception {
    Path root = source();
    Path mdo = root.resolve("Catalogs/Валюты/Валюты.mdo");
    String before = Files.readString(mdo, StandardCharsets.UTF_8);

    EdtObjectScaffold.addForm(mdo, model, "ФормаПроверки");

    Path form = root.resolve("Catalogs/Валюты/Forms/ФормаПроверки/Form.form");
    assertThat(form).exists();
    MdObjectStructureDto structure = EdtObjectStructure.read(mdo, model);
    assertThat(structure.forms).extracting(item -> item.name).contains("ФормаПроверки");
    // Разметка формы читается тем же кодом, что у форм проекта: у пустой формы одна командная панель
    assertThat(EdtFormContent.read(form, model).items).extracting(item -> item.type).containsExactly("AutoCommandBar");
    String added = Files.readString(mdo, StandardCharsets.UTF_8);
    assertThat(added).contains("<name>ФормаПроверки</name>");
    // Запись встала за последней формой объекта
    assertThat(added.indexOf("<name>ФормаПроверки</name>")).isGreaterThan(added.lastIndexOf("<name>ФормаЭлемента</name>"));

    EdtObjectScaffold.deleteForm(mdo, "ФормаПроверки");

    assertThat(form).doesNotExist();
    assertThat(root.resolve("Catalogs/Валюты/Forms/ФормаПроверки")).doesNotExist();
    assertThat(Files.readString(mdo, StandardCharsets.UTF_8)).isEqualTo(before);
  }

  @Test
  void перваяФормаВстаётНаМестоПоСхеме() throws Exception {
    Path root = source();
    EdtObjectScaffold.add(configuration(root), model, MdObjectAddType.CATALOG, "Новый");
    Path mdo = root.resolve("Catalogs/Новый/Новый.mdo");

    EdtObjectScaffold.addForm(mdo, model, "ФормаСписка");

    String xml = Files.readString(mdo, StandardCharsets.UTF_8);
    assertThat(xml.indexOf("<forms uuid=")).isGreaterThan(xml.indexOf("<choiceMode>"));
    assertThat(EdtObjectStructure.read(mdo, model).forms)
      .extracting(form -> form.name).containsExactly("ФормаСписка");
  }

  @Test
  void формаПишетсяСПереводамиСтрокОбъекта() throws Exception {
    for (String eol : List.of("\r\n", "\n")) {
      Path root = source();
      Path mdo = root.resolve("Catalogs/Валюты/Валюты.mdo");
      Files.writeString(mdo, Files.readString(mdo, StandardCharsets.UTF_8).replace("\r\n", "\n").replace("\n", eol),
          StandardCharsets.UTF_8);

      EdtObjectScaffold.addForm(mdo, model, "ФормаПроверки");

      for (Path file : List.of(mdo, root.resolve("Catalogs/Валюты/Forms/ФормаПроверки/Form.form"))) {
        String text = Files.readString(file, StandardCharsets.UTF_8);
        // Все строки файла кончаются одинаково, лишнего возврата каретки нет
        assertThat(text.replace(eol, "")).as(file + " " + eol.length()).doesNotContain("\n", "\r");
      }
      EdtObjectScaffold.deleteForm(mdo, "ФормаПроверки");
      deleteTree(root);
    }
  }

  private static void deleteTree(Path root) throws IOException {
    try (Stream<Path> files = Files.walk(root)) {
      for (Path file : files.sorted(java.util.Comparator.reverseOrder()).toList()) {
        Files.delete(file);
      }
    }
  }

  @Test
  void формаУВидаБезФормОтклоняется() throws Exception {
    // У этих видов в схеме нет форм: 1С:EDT такую форму не загружает
    for (String directory : List.of("Subsystems", "Roles", "CommonModules", "Constants", "SessionParameters")) {
      Path mdo = firstObject(directory);
      String before = Files.readString(mdo, StandardCharsets.UTF_8);

      assertThatThrownBy(() -> EdtObjectScaffold.addForm(mdo, model, "Форма"))
          .as(directory)
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("нет форм");
      assertThat(Files.readString(mdo, StandardCharsets.UTF_8)).as(directory).isEqualTo(before);
      assertThat(mdo.resolveSibling("Forms")).as(directory).doesNotExist();
    }
    for (String directory : List.of("Catalogs", "Documents", "DataProcessors", "Reports", "InformationRegisters")) {
      Path mdo = firstObject(directory);

      EdtObjectScaffold.addForm(mdo, model, "ФормаПроверки");

      assertThat(mdo.resolveSibling("Forms/ФормаПроверки/Form.form")).as(directory).exists();
    }
  }

  /** Первый объект вида из фикстуры, скопированный в рабочий каталог. */
  private Path firstObject(String directory) throws IOException {
    Path first;
    try (Stream<Path> objects = Files.list(fixture.resolve(directory))) {
      first = objects.filter(Files::isDirectory).sorted().findFirst().orElseThrow();
    }
    Path target = workDir.resolve(directory).resolve(first.getFileName().toString());
    Path mdo = first.resolve(first.getFileName() + ".mdo");
    Files.createDirectories(target);
    Files.copy(mdo, target.resolve(mdo.getFileName().toString()));
    return target.resolve(mdo.getFileName().toString());
  }

  @Test
  void повторнаяФормаОтклоняется() throws Exception {
    Path root = source();
    Path mdo = root.resolve("Catalogs/Валюты/Валюты.mdo");

    assertThatThrownBy(() -> EdtObjectScaffold.addForm(mdo, model, "ФормаЭлемента"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("уже");
  }

  /**
   * Проект с манифестом: описание конфигурации и манифест фикстуры, версия платформы - {@code runtime}.
   *
   * @return каталог исходников проекта
   */
  private Path project(String runtime) throws IOException {
    Path project = workDir.resolve("ws").resolve("ssl31");
    Path root = project.resolve("src");
    copy(fixture.resolve("Configuration"), root.resolve("Configuration"));
    String manifest = Files.readString(fixture.resolveSibling("DT-INF").resolve("PROJECT.PMF"), StandardCharsets.UTF_8);
    Path target = project.resolve("DT-INF").resolve("PROJECT.PMF");
    Files.createDirectories(target.getParent());
    Files.writeString(target,
        manifest.replaceFirst("(?m)^Runtime-Version:.*$", Matcher.quoteReplacement("Runtime-Version: " + runtime)),
        StandardCharsets.UTF_8);
    return root;
  }

  /**
   * Каждый вид: файлы нового объекта - все файлы эталона 1С:EDT, совпадают с ним после замены имени и
   * идентификаторов ({@code classId} как есть), читаются свойствами, структурой и деревом проекта.
   */
  @Test
  void каждыйВидСовпадаетСЭталономИЧитается() throws Exception {
    Path root = project("8.5.1");
    Map<String, String> created = new LinkedHashMap<>();
    for (MdObjectAddType kind : MdObjectAddType.values()) {
      String proto = kind.namePrefix() + "1";
      String name = "Нов" + proto;
      EdtObjectScaffold.add(configuration(root), model, kind, name);
      created.put(kind.configurationXmlTag() + "." + name, kind.cfSubdir() + "/" + name + "/" + name + ".mdo");

      String directory = kind.cfSubdir() + "/" + proto + "/";
      List<String> golden = EdtObjectScaffold.goldenFiles(directory);
      Path objectDir = root.resolve(kind.cfSubdir()).resolve(name);
      List<String> files;
      try (Stream<Path> walk = Files.walk(objectDir)) {
        files = walk.filter(Files::isRegularFile)
            .map(file -> directory + objectDir.relativize(file).toString().replace('\\', '/').replace(name, proto))
            .toList();
      }
      assertThat(files).as(kind.name()).isNotEmpty().containsExactlyInAnyOrderElementsOf(golden);
      for (String file : golden) {
        Path written = root.resolve(file.replace(proto, name));
        assertThat(normalized(Files.readString(written, StandardCharsets.UTF_8), name, proto))
            .as(file)
            .isEqualTo(normalized(EdtObjectScaffold.golden(file), proto, proto));
      }

      Path mdo = objectDir.resolve(name + ".mdo");
      assertThat(EdtObjectProperties.readDto(mdo, model).internalName).as(kind.name()).isEqualTo(name);
      EdtObjectStructure.read(mdo, model);
    }

    Map<String, String> tree = new LinkedHashMap<>();
    ProjectMetadataTreeDto.MetadataSourceDto main = ProjectMetadataTreeBuilder.build(workDir.resolve("ws"))
        .sources().getFirst();
    for (ProjectMetadataTreeDto.MetadataGroupDto group : main.groups()) {
      List<ProjectMetadataTreeDto.MetadataItemDto> items = new ArrayList<>(group.items());
      group.subgroups().forEach(subgroup -> items.addAll(subgroup.items()));
      items.forEach(item -> tree.put(item.objectType() + "." + item.name(), item.relativePath()));
    }
    for (Map.Entry<String, String> object : created.entrySet()) {
      assertThat(tree).as("дерево проекта").containsEntry(object.getKey(), "ssl31/src/" + object.getValue());
    }
  }

  /** Имя - имя прототипа, идентификаторы - метки по порядку появления; {@code classId} как есть. */
  private static String normalized(String text, String name, String proto) {
    String renamed = EdtObjectScaffold.renamed(text, name, proto);
    Map<String, String> labels = new HashMap<>();
    Matcher uuids = UUID_TOKEN.matcher(renamed);
    StringBuilder out = new StringBuilder();
    while (uuids.find()) {
      String label = renamed.startsWith("classId=\"", uuids.start() - "classId=\"".length())
          ? uuids.group()
          : labels.computeIfAbsent(uuids.group(), uuid -> "UUID-" + labels.size());
      uuids.appendReplacement(out, Matcher.quoteReplacement(label));
    }
    uuids.appendTail(out);
    return out.toString();
  }

  /**
   * Ссылки в составе конфигурации идут в порядке признаков класса {@code Configuration} схемы, в каком
   * бы порядке ни добавлялись объекты.
   */
  @Test
  void составКонфигурацииИдётВПорядкеСхемы() throws Exception {
    Path root = project("8.5.1");
    List<MdObjectAddType> kinds = new ArrayList<>(List.of(MdObjectAddType.values()));
    Collections.reverse(kinds);
    for (MdObjectAddType kind : kinds) {
      String name = EdtObjectScaffold.addWithNextAvailableName(configuration(root), model, kind);
      assertThat(names(root, kind.configurationXmlTag())).as(kind.name()).contains(name);
    }

    List<String> order = new ArrayList<>();
    for (EStructuralFeature feature : model.classOf("Configuration").getEAllStructuralFeatures()) {
      order.add(feature.getName());
    }
    int previous = -1;
    String previousElement = null;
    for (String element : topLevelElements(configuration(root))) {
      int index = order.indexOf(element);
      assertThat(index).as("признак %s", element).isNotNegative();
      assertThat(index).as("%s после %s", element, previousElement).isGreaterThanOrEqualTo(previous);
      previous = index;
      previousElement = element;
    }
  }

  /** Имена элементов верхнего уровня описания по порядку. */
  private static List<String> topLevelElements(Path mdo) throws Exception {
    List<String> elements = new ArrayList<>();
    XMLStreamReader reader = XMLInputFactory.newFactory()
        .createXMLStreamReader(new java.io.StringReader(Files.readString(mdo, StandardCharsets.UTF_8)));
    int depth = 0;
    while (reader.hasNext()) {
      int event = reader.next();
      if (event == XMLStreamConstants.START_ELEMENT) {
        depth++;
        if (depth == 2) {
          elements.add(reader.getLocalName());
        }
      } else if (event == XMLStreamConstants.END_ELEMENT) {
        depth--;
      }
    }
    return elements;
  }

  /**
   * Вид, которого у платформы проекта ещё нет, не создаётся: платформа берётся из
   * {@code Runtime-Version} манифеста, формат - по линейке платформы.
   */
  @ParameterizedTest
  @EnumSource(SchemaVersion.class)
  void видыПроектаПоВерсииПлатформы(SchemaVersion version) throws Exception {
    Path root = project(version.platformLine());
    for (MdObjectAddType kind : MdObjectAddType.values()) {
      if (kind.existsIn(version)) {
        String name = EdtObjectScaffold.addWithNextAvailableName(configuration(root), model, kind);
        assertThat(root.resolve(kind.cfSubdir()).resolve(name)).as("%s в %s", kind, version).isDirectory();
        continue;
      }
      String before = Files.readString(configuration(root), StandardCharsets.UTF_8);
      assertThatThrownBy(() -> EdtObjectScaffold.addWithNextAvailableName(configuration(root), model, kind))
          .as("%s в %s", kind, version)
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Платформа проекта " + version.platformLine())
          .hasMessageContaining("вид " + kind.configurationXmlTag() + " появился в формате");
      assertThat(root.resolve(kind.cfSubdir())).as("%s в %s", kind, version).doesNotExist();
      assertThat(Files.readString(configuration(root), StandardCharsets.UTF_8)).isEqualTo(before);
    }
  }
}
