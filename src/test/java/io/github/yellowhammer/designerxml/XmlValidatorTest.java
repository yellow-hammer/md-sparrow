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
package io.github.yellowhammer.designerxml;

import io.github.yellowhammer.designerxml.cf.XmlGraphReader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.w3c.dom.Element;
import org.xml.sax.SAXParseException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/**
 * Подмены схем не глушат настоящие ошибки: в копию файла ssl31 вносится одна ошибка, проверка должна
 * указать на неё (строку и элемент).
 */
class XmlValidatorTest {

  /** Список функциональностей мобильного приложения в свойствах конфигурации. */
  private static final String FUNCTIONALITIES = "UsedMobileApplicationFunctionalities";

  @TempDir
  Path tempDir;

  private static Path xsdRoot() {
    String root = System.getProperty("xsd.root");
    assertThat(root).as("xsd.root").isNotBlank();
    return Path.of(root);
  }

  private static void validate(Path xml) throws Exception {
    XmlValidator.validate(xml, SchemaVersion.V2_20, xsdRoot());
  }

  @Test
  void platformObjectPasses() throws Exception {
    validate(Ssl31SubmodulePaths.anyCatalogObjectXml());
  }

  @Test
  void unknownPropertyIsError() throws Exception {
    Edit edit = edit(Ssl31SubmodulePaths.anyCatalogObjectXml(), lines -> {
      int at = lineWith(lines, "<Hierarchical>");
      lines.set(at, lines.get(at).replace("Hierarchical", "Hierarchycal"));
    });

    assertError(edit, "Hierarchycal", lineWith(edit.lines(), "<Hierarchycal>"));
  }

  @Test
  void repeatedPropertyIsError() throws Exception {
    Edit edit = edit(Ssl31SubmodulePaths.anyCatalogObjectXml(), lines -> {
      int at = lineWith(lines, "<Hierarchical>");
      lines.add(at + 1, lines.get(at));
    });

    assertError(edit, "Hierarchical", lineWith(edit.lines(), "<Hierarchical>") + 1);
  }

  @Test
  void wrongEnumerationValueIsError() throws Exception {
    Edit edit = edit(Ssl31SubmodulePaths.anyCatalogObjectXml(), lines -> {
      int at = lineWith(lines, "<CodeType>");
      lines.set(at, lines.get(at).replace(">String<", ">Text<"));
    });

    assertError(edit, "cvc-enumeration-valid", lineWith(edit.lines(), "<CodeType>"));
  }

  @Test
  void propertyOutsideOfPropertiesIsError() throws Exception {
    Edit edit = edit(Ssl31SubmodulePaths.anyCatalogObjectXml(), lines -> {
      String property = lines.remove(lineWith(lines, "<Hierarchical>"));
      lines.add(lineWith(lines, "<ChildObjects>") + 1, property);
    });

    assertError(edit, "Hierarchical", lineWith(edit.lines(), "<Hierarchical>"));
  }

  /** Порядок {@code ChildObjects} платформа соблюдает, поэтому он проверяется по-прежнему. */
  @Test
  void childObjectsOutOfOrderIsError() throws Exception {
    Edit edit = edit(Ssl31SubmodulePaths.anyCatalogObjectXml(), lines -> {
      String form = lines.remove(lineWith(lines, "<Form>"));
      lines.add(lineWith(lines, "<ChildObjects>") + 1, form);
    });

    assertError(edit, "Attribute", lineWith(edit.lines(), "<ChildObjects>") + 2);
  }

  /** Режим совместимости проверяется шаблоном {@code Version…}, а не перечнем версий. */
  @Test
  void compatibilityModeOutsideOfPatternIsError() throws Exception {
    Edit edit = edit(Ssl31SubmodulePaths.configurationXml(), lines -> {
      int at = lineWith(lines, "<CompatibilityMode>");
      lines.set(at, lines.get(at).replace(">Version", ">"));
    });

    assertError(edit, "cvc-pattern-valid", lineWith(edit.lines(), "<CompatibilityMode>"));
  }

  /** Права роли лежат в {@code Ext/Rights.xml} со своим пространством имён, схем для него в наборе нет. */
  @Test
  void rightsFileIsRejectedBeforeValidation() throws Exception {
    Path rights;
    try (Stream<Path> roles = Files.list(Ssl31SubmodulePaths.projectRoot().resolve("src/cf/Roles"))) {
      rights = roles
        .map(role -> role.resolve("Ext/Rights.xml"))
        .filter(Files::isRegularFile)
        .sorted()
        .findFirst()
        .orElseThrow();
    }

    assertThatThrownBy(() -> validate(rights))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("MetaDataObject")
      .hasMessageContaining("Rights");
  }

  /**
   * Файл проверяется только схемой своего формата: схема более нового формата допускает версии всех прежних.
   * Каждое описание конфигурации из эталонов, предложенное схеме другого формата, отклоняется до проверки.
   */
  @Test
  void fileOfOtherFormatIsRejected() {
    for (SchemaVersion format : SchemaVersion.values()) {
      Path xml = SamplesSubmodulePaths.bareObjects(format).resolve("Configuration.xml");
      for (SchemaVersion other : SchemaVersion.values()) {
        if (other == format) {
          continue;
        }
        assertThatThrownBy(() -> XmlValidator.validate(xml, other, xsdRoot()))
          .as(format + " по схеме " + other)
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining(format.metadataObjectVersionAttribute())
          .hasMessageContaining(other.metadataObjectVersionAttribute());
      }
    }
  }

  /**
   * Файлы не с корнем {@code MetaDataObject} - файлы конфигурации из {@code Ext}, форма, описание конфигурации
   * проекта 1С:EDT - отклоняются с их корнем в тексте и без утверждения, что схем для них нет: схемы форм и
   * {@code ClientApplicationInterface} в наборе есть.
   */
  @Test
  void nonObjectFileIsRejectedWithItsRoot() throws Exception {
    Path cf = Ssl31SubmodulePaths.projectRoot().resolve("src/cf");
    List<Path> files = new ArrayList<>();
    try (Stream<Path> ext = Files.list(cf.resolve("Ext"))) {
      ext.filter(file -> file.getFileName().toString().endsWith(".xml")).sorted().forEach(files::add);
    }
    try (Stream<Path> walk = Files.walk(cf.resolve("CommonForms"))) {
      walk.filter(file -> file.getFileName().toString().equals("Form.xml")).sorted().limit(1).forEach(files::add);
    }
    files.add(Path.of(System.getProperty("fixtures.ssl31edt.root"), "ssl31", "src", "Configuration", "Configuration.mdo"));
    assertThat(files).hasSizeGreaterThan(3);

    for (Path file : files) {
      Element root = XmlGraphReader.parse(file).getDocumentElement();
      assertThatThrownBy(() -> validate(file))
        .as(file.toString())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("{" + root.getNamespaceURI() + "}" + root.getLocalName())
        .hasMessageNotContaining("схем нет");
    }
  }

  /**
   * Функциональность мобильного приложения, которую платформа пишет только с более нового формата, схема старого
   * формата не пропускает, в том числе значения, которых нет в XDTO и которые добавляет подмена схемы. Блок
   * функциональности берётся из эталона следующего формата и вставляется в копию эталона предыдущего.
   */
  @Test
  void mobileFunctionalityOfLaterFormatIsError() throws Exception {
    Set<String> checked = new TreeSet<>();
    SchemaVersion[] versions = SchemaVersion.values();
    for (int i = 0; i + 1 < versions.length; i++) {
      SchemaVersion older = versions[i];
      Path olderXml = SamplesSubmodulePaths.bareObjects(older).resolve("Configuration.xml");
      List<String> olderLines = Files.readAllLines(olderXml, StandardCharsets.UTF_8);
      if (olderLines.stream().noneMatch(line -> line.contains("</" + FUNCTIONALITIES + ">"))) {
        continue;
      }
      List<String> newerLines = Files.readAllLines(
        SamplesSubmodulePaths.bareObjects(versions[i + 1]).resolve("Configuration.xml"), StandardCharsets.UTF_8);
      Set<String> known = functionalityNames(olderLines);
      for (int at = 0; at < newerLines.size(); at++) {
        String name = functionalityName(newerLines.get(at));
        if (name == null || known.contains(name)) {
          continue;
        }
        List<String> block = newerLines.subList(at - 1, at + 3);
        Edit edit = edit(olderXml, lines -> lines.addAll(lineWith(lines, "</" + FUNCTIONALITIES + ">"), block));

        SAXParseException error = catchThrowableOfType(
          SAXParseException.class, () -> XmlValidator.validate(edit.xml(), older, xsdRoot()));
        assertThat(error).as(name + " в формате " + older).isNotNull();
        assertThat(error.getMessage()).contains("cvc-enumeration-valid").contains(name);
        assertThat(error.getLineNumber()).isEqualTo(lineWith(edit.lines(), ">" + name + "<") + 1);
        checked.add(name);
      }
    }

    assertThat(checked).containsAll(XsdPlatformShims.MOBILE_FUNCTIONALITIES_MISSING_IN_XDTO.stream()
      .map(XsdPlatformShims.MobileFunctionality::name)
      .toList());
  }

  private static Set<String> functionalityNames(List<String> lines) {
    Set<String> names = new TreeSet<>();
    for (String line : lines) {
      String name = functionalityName(line);
      if (name != null) {
        names.add(name);
      }
    }
    return names;
  }

  /** Имя из строки {@code <app:functionality>Имя</app:functionality>}, иначе {@code null}. */
  private static String functionalityName(String line) {
    String open = "<app:functionality>";
    String close = "</app:functionality>";
    String trimmed = line.trim();
    if (!trimmed.startsWith(open) || !trimmed.endsWith(close) || trimmed.length() == open.length() + close.length()) {
      return null;
    }
    return trimmed.substring(open.length(), trimmed.length() - close.length());
  }

  private void assertError(Edit edit, String fragment, int zeroBasedLine) {
    SAXParseException error = catchThrowableOfType(SAXParseException.class, () -> validate(edit.xml()));
    assertThat(error).as("ошибка проверки").isNotNull();
    assertThat(error.getMessage()).contains(fragment);
    assertThat(error.getLineNumber()).as(error.getMessage()).isEqualTo(zeroBasedLine + 1);
  }

  /** Копия файла в каталоге теста с одной правкой строк; файл submodule не меняется. */
  private Edit edit(Path source, Consumer<List<String>> change) throws IOException {
    List<String> lines = new ArrayList<>(Files.readAllLines(source, StandardCharsets.UTF_8));
    change.accept(lines);
    Path copy = tempDir.resolve(source.getFileName());
    Files.writeString(copy, String.join("\n", lines), StandardCharsets.UTF_8);
    return new Edit(copy, lines);
  }

  private static int lineWith(List<String> lines, String fragment) {
    for (int i = 0; i < lines.size(); i++) {
      if (lines.get(i).contains(fragment)) {
        return i;
      }
    }
    throw new AssertionError("нет строки с " + fragment);
  }

  private record Edit(Path xml, List<String> lines) {
  }
}
