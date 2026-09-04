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
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.yellowhammer.designerxml.cf.MdObjectAddType;
import io.github.yellowhammer.designerxml.cf.MdObjectPropertiesDto;
import io.github.yellowhammer.designerxml.cf.MdObjectStructureDto;

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
    assertThat(dto.synonymRu).isEqualTo("Справочник1");
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
    assertThat(structure.forms).contains("ФормаПроверки");
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
    assertThat(EdtObjectStructure.read(mdo, model).forms).containsExactly("ФормаСписка");
  }

  @Test
  void повторнаяФормаОтклоняется() throws Exception {
    Path root = source();
    Path mdo = root.resolve("Catalogs/Валюты/Валюты.mdo");

    assertThatThrownBy(() -> EdtObjectScaffold.addForm(mdo, model, "ФормаЭлемента"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("уже");
  }
}
