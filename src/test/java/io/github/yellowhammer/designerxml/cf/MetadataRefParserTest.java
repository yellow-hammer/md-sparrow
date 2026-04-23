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

import io.github.yellowhammer.designerxml.Ssl31SubmodulePaths;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Тесты {@link MetadataRefParser} на реальных строках-ссылках из submodule {@code fixtures/ssl31}.
 *
 * <p>Никаких синтетических литералов «как могло бы быть» в тестовых данных — все входные строки берутся
 * из настоящих Designer XML (подсистемы, справочник, права роли). Это одновременно проверка парсера и
 * регрессия на форматах ссылок реальной типовой выгрузки.
 */
class MetadataRefParserTest {

  private static final String XSI_NS = "http://www.w3.org/2001/XMLSchema-instance";

  /** Делегирует к продакшн-константе, избегая копии. */
  private static final Set<String> KNOWN_ROOT_TYPES = MetadataRefParser.KNOWN_ROOT_TYPES;

  @Test
  void mdObjectRefsFromNestedSubsystemContent() throws Exception {
    Path xml = findFirstNestedSubsystemXml();
    Document doc = XmlGraphReader.parse(xml);
    Element root = XmlGraphReader.findMetadataObjectRoot(doc).orElseThrow();

    List<String> rawRefs = XmlGraphReader.descendantsLocal(root, "Item").stream()
      .filter(e -> {
        String type = e.getAttributeNS(XSI_NS, "type");
        return type != null && type.endsWith("MDObjectRef");
      })
      .map(XmlGraphReader::text)
      .filter(Objects::nonNull)
      .toList();

    assertThat(rawRefs)
      .as("в %s ожидаются <Item xsi:type=\"xr:MDObjectRef\">", xml)
      .isNotEmpty();

    for (String raw : rawRefs) {
      Optional<String> parsed = MetadataRefParser.normalizeMdObjectRef(raw);
      assertThat(parsed).as("ref %s", raw).isPresent();
      String[] parts = parsed.orElseThrow().split("\\.", 2);
      assertThat(parts[0]).as("objectType in %s", parsed).isIn(KNOWN_ROOT_TYPES);
      assertThat(parts[1]).as("name in %s", parsed).isNotBlank();
    }
  }

  @Test
  void typeRefsFromCatalogObjectCoverCfgAndPrimitives() throws Exception {
    Path xml = anyCatalogXmlContaining("cfg:CatalogRef");
    Document doc = XmlGraphReader.parse(xml);
    Element root = XmlGraphReader.findMetadataObjectRoot(doc).orElseThrow();

    boolean sawCfgRef = false;
    boolean sawPrimitive = false;

    for (Element typeNode : XmlGraphReader.descendantsLocal(root, "Type")) {
      String raw = XmlGraphReader.text(typeNode);
      if (raw == null) {
        continue;
      }
      Optional<String> parsed = MetadataRefParser.normalizeTypeRef(raw);
      if (raw.startsWith("cfg:")) {
        sawCfgRef = true;
        assertThat(parsed).as("cfg-type %s", raw).isPresent();
        String[] parts = parsed.orElseThrow().split("\\.", 2);
        assertThat(parts[0]).as("objectType in %s", parsed).isIn(KNOWN_ROOT_TYPES);
        assertThat(parts[1]).as("name in %s", parsed).isNotBlank();
      } else if (raw.startsWith("xs:") || isUnqualifiedPrimitive(raw)) {
        sawPrimitive = true;
        assertThat(parsed).as("primitive type %s", raw).isEmpty();
      }
    }

    assertThat(sawCfgRef).as("в %s ожидался хотя бы один cfg:*Ref", xml).isTrue();
    assertThat(sawPrimitive).as("в %s ожидался хотя бы один примитивный тип", xml).isTrue();
  }

  @Test
  void typeRefsWithObjectSuffixFromEventSubscriptionSourceResolve() throws Exception {
    Path xml = eventSubscriptionXmlContaining("cfg:DocumentObject");
    Document doc = XmlGraphReader.parse(xml);
    Element root = XmlGraphReader.findMetadataObjectRoot(doc).orElseThrow();

    List<Element> typeNodes = XmlGraphReader.descendantsLocal(root, "Type");
    List<String> objectTypes = typeNodes.stream()
      .map(XmlGraphReader::text)
      .filter(Objects::nonNull)
      .filter(t -> t.contains(":") && t.substring(t.indexOf(':') + 1).matches("[A-Za-z]+(Object|RecordSet)\\..*"))
      .toList();

    assertThat(objectTypes)
      .as("в %s ожидаются cfg:*Object или cfg:*RecordSet ссылки в Source", xml)
      .isNotEmpty();

    for (String raw : objectTypes) {
      Optional<String> parsed = MetadataRefParser.normalizeTypeRef(raw);
      assertThat(parsed).as("cfg:*Object ref %s должен разрешиться", raw).isPresent();
      String[] parts = parsed.orElseThrow().split("\\.", 2);
      assertThat(parts[0]).as("objectType в %s", parsed).isIn(KNOWN_ROOT_TYPES);
      assertThat(parts[1]).as("name в %s", parsed).isNotBlank();
    }
  }

  @Test
  void roleRightsObjectNamesParseIntoStableKeys() throws Exception {
    Path xml = anyRoleRightsXml();
    Document doc = XmlGraphReader.parse(xml);
    Element root = doc.getDocumentElement();

    List<String> objectNames = XmlGraphReader.descendantsLocal(root, "object").stream()
      .map(o -> XmlGraphReader.firstChildLocal(o, "name"))
      .map(XmlGraphReader::text)
      .filter(Objects::nonNull)
      .toList();

    assertThat(objectNames).as("в %s ожидаются <object><name>…</name>", xml).isNotEmpty();

    boolean sawNestedSubsystem = false;
    for (String raw : objectNames) {
      Optional<String> parsed = MetadataRefParser.normalizeMdObjectRef(raw);
      assertThat(parsed).as("rights ref %s", raw).isPresent();
      String key = parsed.orElseThrow();
      String[] parts = key.split("\\.", 2);
      assertThat(parts[0]).as("objectType in %s", key).isIn(KNOWN_ROOT_TYPES);
      assertThat(parts[1]).as("name in %s", key).isNotBlank();
      if (raw.startsWith("Subsystem.") && raw.split("\\.").length > 2) {
        sawNestedSubsystem = true;
        String last = raw.substring(raw.lastIndexOf('.') + 1);
        assertThat(key).isEqualTo("Subsystem." + last);
      }
    }
    assertThat(sawNestedSubsystem)
      .as("ожидаем хотя бы один путь к вложенной подсистеме в правах роли %s", xml)
      .isTrue();
  }

  /** Любой реквизит {@code <Type>} с примитивным типом без префикса {@code xs:} — например, {@code String}. */
  private static boolean isUnqualifiedPrimitive(String raw) {
    return Set.of("String", "Number", "Date", "Boolean", "UUID", "Type", "ValueStorage", "BinaryData")
      .contains(raw);
  }

  private static Path findFirstNestedSubsystemXml() throws Exception {
    Path subsystemsRoot = Ssl31SubmodulePaths.projectRoot().resolve("src/cf/Subsystems");
    try (Stream<Path> stream = Files.walk(subsystemsRoot)) {
      Path found = stream
        .filter(Files::isRegularFile)
        .filter(p -> p.getFileName().toString().endsWith(".xml"))
        .filter(p -> p.getParent().getFileName().toString().equals("Subsystems"))
        .filter(MetadataRefParserTest::hasMdObjectRefItem)
        .sorted()
        .findFirst()
        .orElse(null);
      assertThat(found).as("вложенная подсистема с <Item xsi:type=\"xr:MDObjectRef\"> в %s", subsystemsRoot)
        .isNotNull();
      return found;
    }
  }

  private static boolean hasMdObjectRefItem(Path xml) {
    try {
      Document doc = XmlGraphReader.parse(xml);
      Element root = XmlGraphReader.findMetadataObjectRoot(doc).orElse(null);
      if (root == null) {
        return false;
      }
      return XmlGraphReader.descendantsLocal(root, "Item").stream()
        .anyMatch(e -> {
          String type = e.getAttributeNS(XSI_NS, "type");
          return type != null && type.endsWith("MDObjectRef");
        });
    } catch (Exception ignored) {
      return false;
    }
  }

  private static Path anyCatalogXmlContaining(String marker) throws Exception {
    Path catalogsRoot = Ssl31SubmodulePaths.projectRoot().resolve("src/cf/Catalogs");
    try (Stream<Path> stream = Files.list(catalogsRoot)) {
      Path found = stream
        .filter(Files::isRegularFile)
        .filter(p -> p.getFileName().toString().endsWith(".xml"))
        .sorted()
        .filter(p -> {
          try {
            return Files.readString(p).contains(marker);
          } catch (Exception e) {
            return false;
          }
        })
        .findFirst()
        .orElse(null);
      assertThat(found).as("каталог с %s в %s", marker, catalogsRoot).isNotNull();
      return found;
    }
  }

  private static Path anyRoleRightsXml() throws Exception {
    Path rolesRoot = Ssl31SubmodulePaths.projectRoot().resolve("src/cf/Roles");
    try (Stream<Path> stream = Files.walk(rolesRoot)) {
      Path found = stream
        .filter(Files::isRegularFile)
        .filter(p -> p.getFileName().toString().equals("Rights.xml"))
        .filter(p -> {
          try {
            String txt = Files.readString(p);
            return txt.contains("<object>") && txt.contains("Subsystem.");
          } catch (Exception e) {
            return false;
          }
        })
        .sorted()
        .findFirst()
        .orElse(null);
      assertThat(found).as("Rights.xml с правами на подсистемы в %s", rolesRoot).isNotNull();
      return found;
    }
  }

  private static Path eventSubscriptionXmlContaining(String marker) throws Exception {
    Path subsRoot = Ssl31SubmodulePaths.projectRoot().resolve("src/cf/EventSubscriptions");
    try (Stream<Path> stream = Files.list(subsRoot)) {
      Path found = stream
        .filter(Files::isRegularFile)
        .filter(p -> p.getFileName().toString().endsWith(".xml"))
        .sorted()
        .filter(p -> {
          try {
            return Files.readString(p).contains(marker);
          } catch (Exception e) {
            return false;
          }
        })
        .findFirst()
        .orElse(null);
      assertThat(found).as("EventSubscription XML с %s в %s", marker, subsRoot).isNotNull();
      return found;
    }
  }
}
