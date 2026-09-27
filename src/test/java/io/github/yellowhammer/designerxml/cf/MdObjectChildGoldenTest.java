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
import io.github.yellowhammer.designerxml.XmlValidator;

import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Узлы, которые добавляет md-sparrow (реквизит, табличная часть и её реквизит, команда, значение
 * перечисления, измерение, ресурс, признаки учёта), пишутся так, как их пишет платформа.
 *
 * <p>Эталон - {@code snapshots/<формат>/cf-object-nodes}: объект-владелец каждого вида с узлом
 * каждого вида, выгруженный платформой. Узлы добавляются в пустой {@code ChildObjects} того же
 * объекта под другими именами и в обратном порядке видов: результат после возврата имён и замены
 * UUID метками совпадает с выгрузкой байт в байт. Так проверяются и свойства узла, и его место
 * среди соседей.
 */
class MdObjectChildGoldenTest {

  /** Набор эталонов с узлами. */
  private static final String NODES = GoldenSnapshots.NODES;

  /** Приставка новых имён: владелец и узлы получают имена, которых в эталоне нет. */
  private static final String PREFIX = "Нов";

  /** Окончание имени копии узла. */
  private static final String COPY = "Копия";

  private static final Pattern NAME = Pattern.compile("<Name>([^<]+)</Name>");

  private static final Pattern UUID = Pattern.compile(
    "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");

  @TempDir
  Path workspace;

  @ParameterizedTest
  @EnumSource(SchemaVersion.class)
  void узлыСовпадаютСВыгрузкойПлатформы(SchemaVersion version) throws Exception {
    List<String> owners = GoldenSnapshots.files(version, NODES);
    Assumptions.assumeFalse(owners.isEmpty(), "узлов этого формата платформа не снимала");

    SoftAssertions softly = new SoftAssertions();
    for (String relative : owners) {
      String expected = GoldenSnapshots.read(version, NODES, relative);
      Owner owner = Owner.of(expected);
      Path file = workspace.resolve(version.name()).resolve(relative);
      Files.createDirectories(file.getParent());
      Files.writeString(file, rename(owner.bare(), Map.of(owner.name(), PREFIX + owner.name())),
        StandardCharsets.UTF_8);

      Map<String, String> back = addAll(file, version, owner);

      softly.assertThat(GoldenSnapshots.normalizeUuids(rename(GoldenSnapshots.read(file), back)))
        .as("%s в формате %s", relative, version)
        .isEqualTo(GoldenSnapshots.normalizeUuids(expected));
    }
    softly.assertAll();
  }

  /**
   * Во всех форматах, в том числе не снятых платформой: объект с добавленными узлами читается
   * моделью формата и проходит XSD. Владелец - проекция канонического эталона в формат.
   */
  @ParameterizedTest
  @EnumSource(SchemaVersion.class)
  void узлыЧитаютсяМодельюКаждогоФормата(SchemaVersion version) throws Exception {
    SchemaVersion canonical = GoldenSnapshots.canonical();
    Path xsdRoot = Path.of(System.getProperty("xsd.root"));
    List<String> owners = GoldenSnapshots.files(canonical, NODES);
    assertThat(owners).as("узлы канонического формата %s", canonical).isNotEmpty();

    SoftAssertions softly = new SoftAssertions();
    for (String relative : owners) {
      Owner owner = Owner.of(FormatProjection.project(GoldenSnapshots.read(canonical, NODES, relative), version));
      Path file = workspace.resolve(version.name()).resolve(relative);
      Files.createDirectories(file.getParent());
      Files.writeString(file, owner.bare(), StandardCharsets.UTF_8);

      addAll(file, version, owner);

      DesignerXml.read(file, version);
      XmlValidator.validate(file, version, xsdRoot);
      for (Node node : owner.nodes()) {
        softly.assertThat(ChildNodeOp.of(node.kind()).names(file, version))
          .as("%s: %s в формате %s", relative, node.kind(), version)
          .contains(PREFIX + node.name());
      }
    }
    softly.assertAll();
  }

  /**
   * Каждый узел, который схема формата допускает у объекта, есть в эталоне: иначе его
   * добавление отказало бы. Вложенные файлы ({@code Recalculation}, {@code Table},
   * {@code Cube}) md-sparrow узлами не пополняет.
   */
  @ParameterizedTest
  @EnumSource(SchemaVersion.class)
  void эталонПокрываетВсеУзлыСхемы(SchemaVersion version) throws Exception {
    SchemaVersion canonical = GoldenSnapshots.canonical();
    Map<String, List<String>> golden = new LinkedHashMap<>();
    for (String relative : GoldenSnapshots.files(canonical, NODES)) {
      Owner owner = Owner.of(GoldenSnapshots.read(canonical, NODES, relative));
      golden.put(owner.kind(), owner.nodes().stream().map(Node::kind).distinct().toList());
    }

    SoftAssertions softly = new SoftAssertions();
    for (String kind : ChildNodeOp.schemaOwners(version)) {
      // узлы правятся у корня файла тех видов, что md-sparrow открывает как объект
      String structureKind = Character.toLowerCase(kind.charAt(0)) + kind.substring(1);
      if (!kind.equals(MdObjectPropertiesGranularPatch.containerLocalForKind(structureKind))
        || !FormatProjection.hasObjectKind(kind, version)) {
        continue;
      }
      List<String> allowed = new ArrayList<>(ChildNodeOp.schemaChildren(version, kind));
      allowed.retainAll(ChildNodeOp.elements());
      softly.assertThat(golden.getOrDefault(kind, List.of()))
        .as("узлы вида %s в эталоне", kind)
        .containsExactlyInAnyOrderElementsOf(allowed);
    }
    softly.assertAll();
  }

  /**
   * Правка повторяется байт в байт: UUID нового узла и копии выводятся из правки и текста файла
   * владельца, и в файле они не повторяются.
   */
  @ParameterizedTest
  @EnumSource(SchemaVersion.class)
  void идентификаторыУзловДетерминированы(SchemaVersion version) throws Exception {
    SchemaVersion canonical = GoldenSnapshots.canonical();
    SoftAssertions softly = new SoftAssertions();
    for (String relative : GoldenSnapshots.files(canonical, NODES)) {
      Owner owner = Owner.of(FormatProjection.project(GoldenSnapshots.read(canonical, NODES, relative), version));
      List<String> runs = new ArrayList<>();
      for (String run : List.of("первый", "второй")) {
        Path file = workspace.resolve(version.name() + "-" + run).resolve(relative);
        Files.createDirectories(file.getParent());
        Files.writeString(file, owner.bare(), StandardCharsets.UTF_8);
        addAll(file, version, owner);
        duplicateAll(file, version, owner);
        runs.add(GoldenSnapshots.read(file));
      }
      softly.assertThat(runs.get(1)).as("повтор правок %s в формате %s", relative, version).isEqualTo(runs.get(0));
      softly.assertThat(repeatedUuids(runs.get(0))).as("повторы UUID в %s, формат %s", relative, version).isEmpty();
    }
    softly.assertAll();
  }

  /**
   * Копия узла пишется так же, как узел-источник: те же строки с тем же отступом, другие только имя
   * и UUID. Источник - узел выгрузки платформы, поэтому такая копия совпадает с тем, как её выгрузит
   * платформа (у копии с другим отступом платформа при выгрузке переписывает весь блок).
   */
  @ParameterizedTest
  @EnumSource(SchemaVersion.class)
  void копияУзлаПишетсяКакИсточник(SchemaVersion version) throws Exception {
    List<String> owners = GoldenSnapshots.files(version, NODES);
    Assumptions.assumeFalse(owners.isEmpty(), "узлов этого формата платформа не снимала");

    SoftAssertions softly = new SoftAssertions();
    for (String relative : owners) {
      String golden = GoldenSnapshots.read(version, NODES, relative);
      Owner owner = Owner.of(golden);
      Path file = workspace.resolve(version.name()).resolve(relative);
      Files.createDirectories(file.getParent());
      Files.writeString(file, golden, StandardCharsets.UTF_8);
      for (Node node : owner.nodes()) {
        String copy = node.name() + COPY;
        if (duplicate(file, version, node.kind(), node.name(), copy)) {
          String xml = GoldenSnapshots.read(file);
          softly.assertThat(copyLines(xml, owner.kind(), node.kind(), copy, node.name()))
            .as("копия %s %s в %s, формат %s", node.kind(), node.name(), relative, version)
            .isEqualTo(sourceLines(xml, owner.kind(), node.kind(), node.name()));
        }
        for (String attribute : node.attributes()) {
          MdObjectChildMutations.duplicateTabularAttribute(file, version, node.name(), attribute, attribute + COPY);
          String xml = GoldenSnapshots.read(file);
          softly.assertThat(nestedLines(xml, owner.kind(), node.name(), attribute + COPY, attribute))
            .as("копия реквизита %s.%s в %s, формат %s", node.name(), attribute, relative, version)
            .isEqualTo(nestedLines(xml, owner.kind(), node.name(), attribute, attribute));
        }
      }
    }
    softly.assertAll();
  }

  /** Копирует узел, если у вида есть копирование. */
  private static boolean duplicate(Path file, SchemaVersion version, String kind, String source, String copy)
    throws Exception {
    switch (kind) {
      case "Attribute" -> MdObjectChildMutations.duplicateAttribute(file, version, source, copy);
      case "TabularSection" -> MdObjectChildMutations.duplicateTabularSection(file, version, source, copy);
      case "EnumValue" -> MdObjectChildMutations.duplicateEnumValue(file, version, source, copy);
      case "Dimension" -> MdObjectChildMutations.duplicateDimension(file, version, source, copy);
      case "Resource" -> MdObjectChildMutations.duplicateResource(file, version, source, copy);
      default -> {
        // команды и признаки учёта не копируются
        return false;
      }
    }
    return true;
  }

  /** Строки узла от начала строки его тега, имя копии возвращено имени источника, UUID - метки. */
  private static String copyLines(String xml, String owner, String kind, String name, String sourceName)
    throws Exception {
    return GoldenSnapshots.normalizeUuids(rename(lines(xml,
      MdObjectXmlRegions.findNamedChildObjectRegion(xml, owner, kind, name)), Map.of(name, sourceName)));
  }

  private static String sourceLines(String xml, String owner, String kind, String name) throws Exception {
    return GoldenSnapshots.normalizeUuids(lines(xml, MdObjectXmlRegions.findNamedChildObjectRegion(xml, owner, kind, name)));
  }

  private static String nestedLines(String xml, String owner, String section, String name, String sourceName)
    throws Exception {
    return GoldenSnapshots.normalizeUuids(rename(lines(xml, MdObjectXmlRegions.findNamedNestedChildObjectRegion(
      xml, owner, "TabularSection", section, "Attribute", name)), Map.of(name, sourceName)));
  }

  private static String lines(String xml, MdObjectXmlRegions.Region region) {
    assertThat(region.isValid()).as("узел найден").isTrue();
    return xml.substring(xml.lastIndexOf('\n', region.start()) + 1, region.end());
  }

  /** UUID, которые встречаются в файле не один раз; {@code xr:ClassId} - класс платформы, он не в счёт. */
  private static List<String> repeatedUuids(String xml) {
    Matcher matcher = UUID.matcher(xml);
    Set<String> seen = new HashSet<>();
    List<String> repeated = new ArrayList<>();
    while (matcher.find()) {
      if (!xml.startsWith("ClassId>", matcher.start() - "ClassId>".length()) && !seen.add(matcher.group())) {
        repeated.add(matcher.group());
      }
    }
    return repeated;
  }

  /**
   * Добавляет узлы владельца: виды - в обратном порядке схемы (каждый узел встаёт перед уже
   * добавленными соседями других видов), узлы одного вида - в порядке эталона.
   *
   * @return новые имена владельца и узлов в имена эталона
   */
  private static Map<String, String> addAll(Path file, SchemaVersion version, Owner owner) throws Exception {
    Map<String, String> back = new LinkedHashMap<>();
    back.put(PREFIX + owner.name(), owner.name());
    List<String> kinds = new ArrayList<>(ChildObjectKinds.of(version, owner.kind()));
    Collections.reverse(kinds);
    for (String kind : kinds) {
      for (Node node : owner.nodes()) {
        if (node.kind().equals(kind)) {
          ChildNodeOp.of(kind).add(file, version, PREFIX + node.name());
          back.put(PREFIX + node.name(), node.name());
        }
      }
    }
    for (Node node : owner.nodes()) {
      for (String attribute : node.attributes()) {
        MdObjectChildMutations.addTabularAttribute(file, version, PREFIX + node.name(), PREFIX + attribute);
        back.put(PREFIX + attribute, attribute);
      }
    }
    return back;
  }

  /** Копирует добавленные узлы, у которых копирование есть, и реквизиты табличных частей. */
  private static void duplicateAll(Path file, SchemaVersion version, Owner owner) throws Exception {
    for (Node node : owner.nodes()) {
      String source = PREFIX + node.name();
      String copy = source + COPY;
      switch (node.kind()) {
        case "Attribute" -> MdObjectChildMutations.duplicateAttribute(file, version, source, copy);
        case "TabularSection" -> MdObjectChildMutations.duplicateTabularSection(file, version, source, copy);
        case "EnumValue" -> MdObjectChildMutations.duplicateEnumValue(file, version, source, copy);
        case "Dimension" -> MdObjectChildMutations.duplicateDimension(file, version, source, copy);
        case "Resource" -> MdObjectChildMutations.duplicateResource(file, version, source, copy);
        default -> {
          // команды и признаки учёта не копируются
        }
      }
      for (String attribute : node.attributes()) {
        MdObjectChildMutations.duplicateTabularAttribute(
          file, version, source, PREFIX + attribute, PREFIX + attribute + COPY);
      }
    }
  }

  /** Имена целым словом, за один проход. */
  private static String rename(String xml, Map<String, String> names) {
    Pattern token = Pattern.compile("(?<![\\p{L}\\p{N}_])("
      + String.join("|", names.keySet().stream().map(Pattern::quote).toList())
      + ")(?![\\p{L}\\p{N}_])");
    Matcher matcher = token.matcher(xml);
    StringBuilder out = new StringBuilder();
    while (matcher.find()) {
      matcher.appendReplacement(out, Matcher.quoteReplacement(names.get(matcher.group(1))));
    }
    matcher.appendTail(out);
    return out.toString();
  }

  private static List<String> names(String xml) {
    List<String> names = new ArrayList<>();
    Matcher matcher = NAME.matcher(xml);
    while (matcher.find()) {
      names.add(matcher.group(1));
    }
    assertThat(names).as("имена в эталоне").isNotEmpty();
    return names;
  }

  /** Узел эталона: вид, имя и реквизиты, если это табличная часть. */
  private record Node(String kind, String name, List<String> attributes) {
  }

  /**
   * Объект-владелец эталона.
   *
   * @param bare тот же файл с пустым {@code ChildObjects}: так его выгружает платформа без узлов
   */
  private record Owner(String kind, String name, List<Node> nodes, String bare) {

    static Owner of(String xml) {
      String kind = XmlLines.children(xml, List.of("MetaDataObject")).get(0).name();
      XmlLines.Node children = XmlLines.children(xml, List.of("MetaDataObject", kind)).stream()
        .filter(node -> node.name().equals("ChildObjects"))
        .findFirst()
        .orElseThrow();
      List<Node> nodes = new ArrayList<>();
      for (XmlLines.Node child : XmlLines.children(xml, List.of("MetaDataObject", kind, "ChildObjects"))) {
        List<String> names = names(xml.substring(child.start(), child.end()));
        // у табличной части за её именем идут имена реквизитов
        List<String> attributes = child.name().equals("TabularSection") ? names.subList(1, names.size()) : List.of();
        nodes.add(new Node(child.name(), names.get(0), List.copyOf(attributes)));
      }
      String bare = xml.substring(0, children.start()) + "<ChildObjects/>" + xml.substring(children.end());
      return new Owner(kind, names(xml).get(0), List.copyOf(nodes), bare);
    }
  }
}
