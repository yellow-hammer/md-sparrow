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
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.eclipse.emf.ecore.EAttribute;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.yellowhammer.designerxml.cf.MdNamedPropertyDto;
import io.github.yellowhammer.designerxml.cf.MdObjectStructureDto;

/**
 * Эталоны узлов, записанные 1С:EDT, и узлы, которые из них получаются.
 */
class EdtNodeGoldenTest {

  /** Свойство верхнего уровня узла: строка с отступом в четыре пробела. */
  private static final Pattern PROPERTY = Pattern.compile("(?m)^    <(\\w+)[ >/]");

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

  /** Виды объектов, у которых правится состав: всё, что лежит в конфигурации, и внешние объекты. */
  private static Set<String> owners() {
    Set<String> owners = new LinkedHashSet<>();
    for (EdtModel.Composition item : model.composition("Configuration")) {
      owners.add(item.objectType());
    }
    owners.add("ExternalDataProcessor");
    owners.add("ExternalReport");
    return owners;
  }

  /** Узлы, которые заводит {@link EdtMutationRouter}. */
  private static final Set<String> FEATURES = Set.of(
      "attributes", "tabularSections", "enumValues", "dimensions", "resources", "commands",
      "accountingFlags", "extDimensionAccountingFlags");

  @Test
  void узелЛюбогоВидаОбъектаЕстьВЭталонах() {
    List<String> missing = new ArrayList<>();
    for (String owner : owners()) {
      for (EdtModel.Composition item : model.composition(owner)) {
        if (!FEATURES.contains(item.feature())) {
          continue;
        }
        if (!EdtNodeGolden.exists(item.objectType())) {
          missing.add(owner + "." + item.objectType());
        }
        // Реквизиты табличной части - свой класс
        for (EdtModel.Composition nested : model.composition(item.objectType())) {
          if (nested.feature().equals("attributes") && !EdtNodeGolden.exists(nested.objectType())) {
            missing.add(owner + "." + item.objectType() + "." + nested.objectType());
          }
        }
      }
    }
    assertThat(missing).isEmpty();
  }

  @Test
  void вЭталонахЕстьОбязательныеСвойстваИПорядокСхемы() throws Exception {
    List<Path> goldens = goldens();
    assertThat(goldens).isNotEmpty();
    for (Path golden : goldens) {
      String kind = golden.getFileName().toString().replace(".xml", "");
      EClass eClass = model.classOf(kind);
      assertThat(eClass).as(kind).isNotNull();
      String text = Files.readString(golden, StandardCharsets.UTF_8).replace("\r\n", "\n");

      List<String> written = new ArrayList<>();
      Matcher property = PROPERTY.matcher(text);
      while (property.find()) {
        if (written.isEmpty() || !written.get(written.size() - 1).equals(property.group(1))) {
          written.add(property.group(1));
        }
      }
      // 1С:EDT пишет свойства в порядке схемы, только порождаемые типы идут первыми
      List<String> order = new ArrayList<>();
      order.add("producedTypes");
      for (EStructuralFeature feature : eClass.getEAllStructuralFeatures()) {
        if (!order.contains(feature.getName())) {
          order.add(feature.getName());
        }
      }
      assertThat(order).as(kind).containsAll(written);
      assertThat(written).as(kind).isSortedAccordingTo(
          (left, right) -> Integer.compare(order.indexOf(left), order.indexOf(right)));

      for (EStructuralFeature feature : eClass.getEAllStructuralFeatures()) {
        if (feature.getLowerBound() < 1 || feature.isTransient() || feature.isDerived()) {
          continue;
        }
        boolean present = feature instanceof EAttribute && text.contains(" " + feature.getName() + "=\"")
            || written.contains(feature.getName());
        assertThat(present).as(kind + "." + feature.getName()).isTrue();
      }
    }
  }

  @Test
  void командаПолучаетГруппуИОтображениеКакВEDT() throws Exception {
    Path file = copyOf("Catalogs/Валюты/Валюты.mdo");

    EdtChildMutations.add(file, model, "commands", "ЗагрузитьКурсы");

    String xml = Files.readString(file, StandardCharsets.UTF_8);
    String node = xml.substring(xml.lastIndexOf("<commands uuid"), xml.lastIndexOf("</commands>"));
    // Без группы платформа отвергает конфигурацию: «Не указана группа, в которую входит команда»
    assertThat(node).contains(
        "<name>ЗагрузитьКурсы</name>",
        "<value>ЗагрузитьКурсы</value>",
        "<group>FormCommandBarImportant</group>",
        "<representation>Auto</representation>");
    assertThat(EdtObjectStructure.read(file, model).commands)
        .contains("ЗагрузитьКурсы");
  }

  @Test
  void реквизитПолучаетЗначенияПоУмолчаниюПлатформы() throws Exception {
    Path file = copyOf("Catalogs/Валюты/Валюты.mdo");

    EdtChildMutations.add(file, model, "attributes", "Цена");

    String node = lastNode(Files.readString(file, StandardCharsets.UTF_8), "attributes");
    // Без этих строк 1С:EDT берёт первое значение перечисления схемы: DontUse
    assertThat(node).contains(
        "<fullTextSearch>Use</fullTextSearch>",
        "<dataHistory>Use</dataHistory>",
        "<minValue xsi:type=\"core:UndefinedValue\"/>",
        "<fillValue xsi:type=\"core:StringValue\"/>");
    MdNamedPropertyDto added = EdtObjectProperties.readDto(file, model).attributes.stream()
        .filter(attribute -> attribute.name.equals("Цена")).findFirst().orElseThrow();
    assertThat(added.type.types).containsExactly("xs:string");
    assertThat(added.type.stringQualifiers.length).isEqualTo("10");
  }

  @Test
  void измерениеРегистраСведенийВходитВОсновнойОтбор() throws Exception {
    Path file = copyOf("InformationRegisters/_ДемоГрафикиРаботы/_ДемоГрафикиРаботы.mdo");

    EdtChildMutations.add(file, model, "dimensions", "Организация");

    assertThat(lastNode(Files.readString(file, StandardCharsets.UTF_8), "dimensions"))
        .contains("<name>Организация</name>", "<mainFilter>true</mainFilter>", "<fullTextSearch>Use</fullTextSearch>");
  }

  @Test
  void пространстваИменОбъявляютсяВКорнеКакУEDT() throws Exception {
    Path root = workDir.resolve("src");
    Files.createDirectories(root.resolve("Configuration"));
    Files.copy(source.resolve("Configuration/Configuration.mdo"), root.resolve("Configuration/Configuration.mdo"));
    EdtObjectScaffold.add(root.resolve("Configuration/Configuration.mdo"), model,
        io.github.yellowhammer.designerxml.cf.MdObjectAddType.CATALOG, "Товары");
    Path file = root.resolve("Catalogs/Товары/Товары.mdo");
    // У нового справочника, как у записанного 1С:EDT, объявлено только mdclass
    assertThat(Files.readString(file, StandardCharsets.UTF_8)).doesNotContain("xmlns:core");

    EdtChildMutations.add(file, model, "attributes", "Цена");
    EdtChildMutations.add(file, model, "attributes", "Количество");

    String xml = Files.readString(file, StandardCharsets.UTF_8);
    assertThat(xml).containsOnlyOnce("xmlns:core=").containsOnlyOnce("xmlns:xsi=");
    assertThat(xml).containsPattern(
        "<mdclass:Catalog xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\""
            + " xmlns:core=\"http://g5.1c.ru/v8/dt/mcore\""
            + " xmlns:mdclass=\"http://g5.1c.ru/v8/dt/metadata/mdclass\" uuid=\"[0-9a-f-]{36}\">");
    assertThat(EdtObjectProperties.readDto(file, model).attributes)
        .extracting(attribute -> attribute.name).containsExactly("Цена", "Количество");
  }

  @Test
  void реквизитНовойТабличнойЧастиВстаётПоПорядкуСхемы() throws Exception {
    Path file = copyOf("Catalogs/Валюты/Валюты.mdo");

    EdtChildMutations.add(file, model, "tabularSections", "Курсы");
    EdtChildMutations.addNested(file, model, "tabularSections", "Курсы", "attributes", "Курс");

    String section = lastNode(Files.readString(file, StandardCharsets.UTF_8), "tabularSections");
    // Длину номера строки платформа ставит 9, а схема 1С:EDT без записи считает её 5
    assertThat(section).contains("<lineNumberLength>9</lineNumberLength>");
    assertThat(section.indexOf("<name>Курс</name>")).isPositive()
        .isLessThan(section.indexOf("<lineNumberLength>"));
    assertThat(section).contains("<dataHistory>Use</dataHistory>", "<fullTextSearch>Use</fullTextSearch>");
  }

  @Test
  void признакУчётаБулевоБезЗначенияЗаполнения() throws Exception {
    Path file = copyOf("ChartsOfAccounts/_ДемоОсновной/_ДемоОсновной.mdo");

    EdtChildMutations.add(file, model, "accountingFlags", "ПризнакПроверки");
    EdtChildMutations.add(file, model, "extDimensionAccountingFlags", "ПризнакСубконтоПроверки");

    String xml = Files.readString(file, StandardCharsets.UTF_8);
    for (String feature : List.of("accountingFlags", "extDimensionAccountingFlags")) {
      assertThat(lastNode(xml, feature)).as(feature)
          .contains("<types>Boolean</types>", "<fillValue xsi:type=\"core:UndefinedValue\"/>",
              "<dataHistory>Use</dataHistory>")
          .doesNotContain("String", "stringQualifiers");
    }
    MdObjectStructureDto structure = EdtObjectStructure.read(file, model);
    assertThat(structure.accountingFlags).contains("ПризнакПроверки");
    assertThat(structure.extDimensionAccountingFlags).contains("ПризнакСубконтоПроверки");
  }

  @Test
  void узелБезЭталонаОтклоняется() {
    assertThatThrownBy(() -> EdtNodeGolden.node("CubeCommand", "Команда", "ru", "  ", "\n", "зерно"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("CubeCommand");
  }

  /** Последний узел вида верхнего уровня, от открывающего до закрывающего тега. */
  private static String lastNode(String xml, String feature) {
    int start = xml.lastIndexOf("\n  <" + feature + " ");
    return xml.substring(start, xml.indexOf("\n  </" + feature + ">", start));
  }

  private static List<Path> goldens() throws IOException, URISyntaxException {
    Path directory = Path.of(EdtNodeGolden.class.getResource("/edt-golden/Nodes").toURI());
    try (Stream<Path> files = Files.list(directory)) {
      return files.filter(file -> file.getFileName().toString().endsWith(".xml")).sorted().toList();
    }
  }
}
