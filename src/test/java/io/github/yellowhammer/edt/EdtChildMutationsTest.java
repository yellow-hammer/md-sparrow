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

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.yellowhammer.designerxml.cf.MdNamedPropertyDto;
import io.github.yellowhammer.designerxml.cf.MdObjectPropertiesDto;

/** Правка состава объекта 1С:EDT. */
class EdtChildMutationsTest {

  private static EdtModel model;
  private static Path source;

  @TempDir
  Path workDir;

  @BeforeAll
  static void locate() throws Exception {
    model = EdtModel.bundled();
    source = Path.of(System.getProperty("fixtures.ssl31edt.root"), "ssl31", "src");
    assertThat(source).exists();
  }

  private Path copyOf(String objectPath) throws IOException {
    Path original = source.resolve(objectPath);
    Path copy = workDir.resolve(original.getFileName());
    Files.copy(original, copy);
    return copy;
  }

  private static List<String> names(List<MdNamedPropertyDto> nodes) {
    return nodes.stream().map(node -> node.name).toList();
  }

  private List<String> attributesOf(Path file) throws IOException {
    return names(EdtObjectProperties.readDto(file, model).attributes);
  }

  @Test
  void добавляетРеквизит() throws Exception {
    Path file = copyOf("Catalogs/Валюты/Валюты.mdo");

    EdtChildMutations.add(file, model, "attributes", "НовыйРеквизит");

    MdObjectPropertiesDto dto = EdtObjectProperties.readDto(file, model);
    assertThat(names(dto.attributes)).endsWith("НовыйРеквизит");
    MdNamedPropertyDto added = dto.attributes.get(dto.attributes.size() - 1);
    assertThat(added.synonymRu).isEqualTo("НовыйРеквизит");
    assertThat(added.type.types).containsExactly("String");
    // Идентификатор узла платформа требует у каждого
    assertThat(Files.readString(file, StandardCharsets.UTF_8))
        .containsPattern("<attributes uuid=\"[0-9a-f-]{36}\">\\s+<name>НовыйРеквизит</name>");
  }

  @Test
  void добавленныйРеквизитВстаётПослеПрежних() throws Exception {
    Path file = copyOf("Catalogs/Валюты/Валюты.mdo");
    String before = Files.readString(file, StandardCharsets.UTF_8);

    EdtChildMutations.add(file, model, "attributes", "НовыйРеквизит");

    String after = Files.readString(file, StandardCharsets.UTF_8);
    // Реквизиты идут до табличных частей, как и в схеме
    assertThat(after.indexOf("НовыйРеквизит")).isLessThan(after.indexOf("<tabularSections"));
    assertThat(after.indexOf("НовыйРеквизит")).isGreaterThan(after.lastIndexOf("<attributes uuid",
        after.indexOf("<tabularSections")) - 1);
    assertThat(before.lines().count()).isLessThan(after.lines().count());
  }

  @Test
  void переименовываетРеквизит() throws Exception {
    Path file = copyOf("Catalogs/Валюты/Валюты.mdo");

    EdtChildMutations.rename(file, null, null, "attributes", "Наценка", "НаценкаПоУмолчанию");

    assertThat(attributesOf(file)).contains("НаценкаПоУмолчанию").doesNotContain("Наценка");
  }

  @Test
  void удаляетРеквизитЦеликом() throws Exception {
    Path file = copyOf("Catalogs/Валюты/Валюты.mdo");
    String before = Files.readString(file, StandardCharsets.UTF_8);

    EdtChildMutations.delete(file, null, null, "attributes", "Наценка");

    String after = Files.readString(file, StandardCharsets.UTF_8);
    assertThat(attributesOf(file)).doesNotContain("Наценка");
    assertThat(after).doesNotContain("Наценка");
    assertThat(after.lines().count()).isLessThan(before.lines().count());
    // Соседние узлы на месте
    assertThat(attributesOf(file)).contains("ЗагружаетсяИзИнтернета", "НаименованиеПолное");
  }

  @Test
  void копируетРеквизитСоСвоимИдентификатором() throws Exception {
    Path file = copyOf("Catalogs/Валюты/Валюты.mdo");

    EdtChildMutations.duplicate(file, null, null, "attributes", "Наценка", "НаценкаКопия");

    MdObjectPropertiesDto dto = EdtObjectProperties.readDto(file, model);
    assertThat(names(dto.attributes)).contains("Наценка", "НаценкаКопия");

    EdtObjectReader.EdtNode node = EdtObjectReader.read(file);
    List<String> uuids = node.list("attributes").stream().map(EdtObjectReader.EdtNode::uuid).toList();
    assertThat(uuids).doesNotHaveDuplicates();

    MdNamedPropertyDto copy = dto.attributes.stream()
        .filter(attribute -> attribute.name.equals("НаценкаКопия"))
        .findFirst()
        .orElseThrow();
    MdNamedPropertyDto origin = dto.attributes.stream()
        .filter(attribute -> attribute.name.equals("Наценка"))
        .findFirst()
        .orElseThrow();
    assertThat(copy.type.types).isEqualTo(origin.type.types);
  }

  @Test
  void переставляетРеквизиты() throws Exception {
    Path file = copyOf("Catalogs/Валюты/Валюты.mdo");
    List<String> before = attributesOf(file);
    List<String> order = new java.util.ArrayList<>(before);
    java.util.Collections.reverse(order);

    EdtChildMutations.reorder(file, null, null, "attributes", order);

    assertThat(attributesOf(file)).isEqualTo(order);
    assertThat(Files.readString(file, StandardCharsets.UTF_8).lines().count())
        .isEqualTo(source.resolve("Catalogs/Валюты/Валюты.mdo").toFile().length() > 0
            ? Files.readString(source.resolve("Catalogs/Валюты/Валюты.mdo"), StandardCharsets.UTF_8).lines().count()
            : 0);
  }

  @Test
  void правитРеквизитыТабличнойЧасти() throws Exception {
    Path file = copyOf("Catalogs/Валюты/Валюты.mdo");

    EdtChildMutations.addNested(file, model, "tabularSections", "Представления", "attributes", "Примечание");

    MdObjectPropertiesDto dto = EdtObjectProperties.readDto(file, model);
    MdNamedPropertyDto section = dto.tabularSections.get(0);
    assertThat(names(section.attributes)).endsWith("Примечание");
    // Реквизит объекта от реквизита табличной части не пострадал
    assertThat(names(dto.attributes)).doesNotContain("Примечание");

    EdtChildMutations.rename(file, "tabularSections", "Представления", "attributes", "Примечание", "Пояснение");
    assertThat(names(EdtObjectProperties.readDto(file, model).tabularSections.get(0).attributes))
        .contains("Пояснение").doesNotContain("Примечание");

    EdtChildMutations.delete(file, "tabularSections", "Представления", "attributes", "Пояснение");
    assertThat(names(EdtObjectProperties.readDto(file, model).tabularSections.get(0).attributes))
        .doesNotContain("Пояснение");
  }

  @Test
  void добавляетТабличнуюЧасть() throws Exception {
    Path file = copyOf("Catalogs/Валюты/Валюты.mdo");

    EdtChildMutations.add(file, model, "tabularSections", "Курсы");

    MdObjectPropertiesDto dto = EdtObjectProperties.readDto(file, model);
    assertThat(names(dto.tabularSections)).containsExactly("Представления", "Курсы");
  }

  @Test
  void уНовойТабличнойЧастиЕстьСвоиТипы() throws Exception {
    Path file = copyOf("Catalogs/Валюты/Валюты.mdo");

    EdtChildMutations.add(file, model, "tabularSections", "Курсы");

    // Без своих типов 1С:EDT выдаёт им новые идентификаторы при каждой выгрузке
    String after = Files.readString(file, StandardCharsets.UTF_8);
    int section = after.lastIndexOf("<tabularSections uuid");
    String node = after.substring(section);
    assertThat(node).contains("<producedTypes>", "<objectType typeId=", "<rowType typeId=");
    assertThat(node.split("valueTypeId=", -1)).hasSize(3);

    // У реквизита своих типов не бывает, и схема об этом знает
    EdtChildMutations.add(file, model, "attributes", "НовыйРеквизит");
    String withAttribute = Files.readString(file, StandardCharsets.UTF_8);
    int attribute = withAttribute.lastIndexOf("<attributes uuid");
    assertThat(withAttribute.substring(attribute, withAttribute.indexOf("</attributes>", attribute)))
        .doesNotContain("producedTypes");
  }

  @Test
  void добавляетЗначениеПеречисления() throws Exception {
    Path file = copyOf("Enums/_ДемоПолФизическогоЛица/_ДемоПолФизическогоЛица.mdo");

    EdtChildMutations.add(file, model, "enumValues", "НеУказан");

    assertThat(names(EdtObjectProperties.readDto(file, model).enumValues))
        .containsExactly("Мужской", "Женский", "НеУказан");
  }

  @Test
  void добавляетИзмерениеРегистра() throws Exception {
    Path file = copyOf("InformationRegisters/_ДемоГрафикиРаботы/_ДемоГрафикиРаботы.mdo");

    EdtChildMutations.add(file, model, "dimensions", "Организация");

    assertThat(names(EdtObjectProperties.readDto(file, model).dimensions))
        .containsExactly("Дата", "Организация");
  }

  @Test
  void повторноеИмяОтклоняется() throws Exception {
    Path file = copyOf("Catalogs/Валюты/Валюты.mdo");
    byte[] before = Files.readAllBytes(file);

    assertThatThrownBy(() -> EdtChildMutations.add(file, model, "attributes", "Наценка"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Наценка");
    assertThat(Files.readAllBytes(file)).isEqualTo(before);
  }

  @Test
  void неизвестныйУзелОтклоняется() throws Exception {
    Path file = copyOf("Catalogs/Валюты/Валюты.mdo");
    byte[] before = Files.readAllBytes(file);

    assertThatThrownBy(() -> EdtChildMutations.rename(file, null, null, "attributes", "Нет", "Другое"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(Files.readAllBytes(file)).isEqualTo(before);
  }

  @Test
  void файлСLfОстаётсяСLf() throws Exception {
    Path file = copyOf("Catalogs/Валюты/Валюты.mdo");
    Files.writeString(file, Files.readString(file, StandardCharsets.UTF_8).replace("\r\n", "\n"),
        StandardCharsets.UTF_8);

    EdtChildMutations.add(file, model, "attributes", "НовыйРеквизит");

    assertThat(Files.readString(file, StandardCharsets.UTF_8)).doesNotContain("\r");
    assertThat(attributesOf(file)).endsWith("НовыйРеквизит");
  }
}
