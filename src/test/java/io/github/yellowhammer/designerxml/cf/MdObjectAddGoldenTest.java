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
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * add-md-object пишет то же, что выгрузила бы платформа: для каждого вида и каждого формата
 * созданные файлы (описание и всё из каталога объекта: {@code Ext/Rights.xml} роли,
 * {@code Ext/Form.xml} общей формы, {@code Ext/WSDefinition.xml} WS-ссылки) совпадают с эталоном
 * {@code snapshots/<формат>/cf-bare-objects} байт в байт после замены имени на имя прототипа и UUID
 * на метки ({@code xr:ClassId} сравнивается как есть).
 *
 * <p>Эталоны видов, которых нет среди прежних 19, сняты не во всех форматах. Там, где эталона нет,
 * объект читается моделью формата, проходит проверку по XSD, а выгрузка - проверку целостности.
 * Вид, появившийся позже формата, не создаётся.
 */
class MdObjectAddGoldenTest {

  @TempDir
  Path workspace;

  @ParameterizedTest
  @EnumSource(SchemaVersion.class)
  void каждыйВидСовпадаетСВыгрузкойПлатформы(SchemaVersion version) throws Exception {
    Path cf = workspace.resolve(version.name());
    EmptyCfScaffold.writeEmptyTree(cf, CfLayout.DEFAULT_CONFIGURATION_NAME, null, null, null, version);
    Path configuration = cf.resolve(CfLayout.CONFIGURATION_XML);
    // Эталон снят с семени в режиме совместимости 8.3.12, а от режима зависит корень общей формы
    FormNamespaceRulesTest.setCompatibilityMode(configuration, "Version8_3_12");
    Path snapshot = GoldenSnapshots.format(version).resolve(GoldenSnapshots.CF);
    Path xsdRoot = Path.of(System.getProperty("xsd.root"));

    SoftAssertions softly = new SoftAssertions();
    int compared = 0;
    for (MdObjectAddType type : MdObjectAddType.values()) {
      String proto = GoldenScaffold.protoName(type);
      String name = "Нов" + proto;
      if (!type.existsIn(version)) {
        String before = GoldenSnapshots.read(configuration);
        softly.assertThatThrownBy(() -> MdObjectAdd.add(configuration, name, version, type))
          .as("%s в формате %s", type, version)
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("появился в формате");
        softly.assertThat(GoldenSnapshots.read(configuration)).as("состав после отказа").isEqualTo(before);
        softly.assertThat(cf.resolve(type.cfSubdir())).as("каталог %s после отказа", type).doesNotExist();
        softly.assertThat(snapshot.resolve(type.cfSubdir())).as("выгрузка платформы %s", type).doesNotExist();
        continue;
      }
      MdObjectAdd.add(configuration, name, version, type);

      Map<String, Path> expected = objectFiles(snapshot, type, proto, proto);
      Map<String, Path> created = objectFiles(cf, type, name, proto);
      if (expected.isEmpty()) {
        // эталон формата снят семенем, где этого вида не было: проверки модели и схемы
        Path description = CfLayout.objectXmlInSubdir(cf, type.cfSubdir(), name);
        DesignerXml.read(description, version);
        XmlValidator.validate(description, version, xsdRoot);
        softly.assertThat(created.keySet()).as("файлы %s в формате %s", type, version)
          .containsExactlyInAnyOrderElementsOf(GoldenScaffold.prototypeFiles(type));
        for (Path file : created.values()) {
          if (isManagedForm(file)) {
            DesignerXml.read(file, version);
          }
        }
        continue;
      }
      softly.assertThat(created.keySet()).as("файлы %s в формате %s", type, version)
        .containsExactlyInAnyOrderElementsOf(expected.keySet());
      for (Map.Entry<String, Path> file : expected.entrySet()) {
        if (created.containsKey(file.getKey())) {
          softly.assertThat(normalized(created.get(file.getKey()), name, proto))
            .as("%s в формате %s", file.getKey(), version)
            .isEqualTo(GoldenSnapshots.normalizeUuids(GoldenSnapshots.read(file.getValue())));
          compared++;
        }
      }
    }
    softly.assertAll();

    assertThat(compared).as("сверено файлов с выгрузкой платформы %s", version)
      .isEqualTo(prototypeFilesInSnapshot(snapshot));
    assertThat(CfDumpValidation.validate(cf)).as("находки validate-dump в формате %s", version).isEmpty();
    // выгрузка платформы идёт с CRLF: одиночный LF в Configuration.xml - порча файла
    assertThat(GoldenSnapshots.read(configuration).replace("\r\n", "")).doesNotContain("\n");
  }

  /**
   * Состав конфигурации после добавления всех видов в любом порядке - как у платформы: там, где эталон
   * снят со всеми видами формата, {@code ChildObjects} совпадает с ним строка в строку.
   */
  @ParameterizedTest
  @EnumSource(SchemaVersion.class)
  void составВЛюбомПорядкеДобавленияКакУПлатформы(SchemaVersion version) throws Exception {
    Path snapshot = GoldenSnapshots.format(version).resolve(GoldenSnapshots.CF);
    List<MdObjectAddType> kinds = new ArrayList<>();
    for (MdObjectAddType type : MdObjectAddType.values()) {
      if (type.existsIn(version)) {
        kinds.add(type);
      }
    }
    // эталон снят прежним семенем - в нём не все виды формата
    boolean complete = kinds.stream()
      .allMatch(type -> Files.isRegularFile(snapshot.resolve(type.cfSubdir()).resolve(GoldenScaffold.protoName(type) + ".xml")));
    if (!complete) {
      return;
    }
    List<String> expected = childObjects(GoldenSnapshots.read(snapshot.resolve(CfLayout.CONFIGURATION_XML)));

    List<List<MdObjectAddType>> orders = new ArrayList<>();
    List<MdObjectAddType> reversed = new ArrayList<>(kinds);
    Collections.reverse(reversed);
    orders.add(reversed);
    List<MdObjectAddType> shuffled = new ArrayList<>(kinds);
    Collections.shuffle(shuffled, new Random(version.ordinal()));
    orders.add(shuffled);
    for (int i = 0; i < orders.size(); i++) {
      Path cf = workspace.resolve(version.name() + "-" + i);
      EmptyCfScaffold.writeEmptyTree(cf, CfLayout.DEFAULT_CONFIGURATION_NAME, null, null, null, version);
      Path configuration = cf.resolve(CfLayout.CONFIGURATION_XML);
      for (MdObjectAddType type : orders.get(i)) {
        MdObjectAdd.add(configuration, GoldenScaffold.protoName(type), version, type);
      }
      assertThat(childObjects(GoldenSnapshots.read(configuration)))
        .as("порядок %d в формате %s", i, version)
        .containsExactlyElementsOf(expected);
    }
  }

  /** Строки {@code Configuration/ChildObjects} без отступов. */
  private static List<String> childObjects(String configuration) {
    int start = configuration.indexOf("<ChildObjects>");
    int end = configuration.indexOf("</ChildObjects>", start);
    assertThat(start).as("ChildObjects").isNotNegative();
    return configuration.substring(start + "<ChildObjects>".length(), end).lines()
      .map(String::strip)
      .filter(line -> !line.isEmpty())
      .toList();
  }

  /** Сколько файлов прототипов лежит в эталоне формата: описания и файлы из каталогов объектов. */
  private static int prototypeFilesInSnapshot(Path snapshot) throws IOException {
    Set<String> files = new LinkedHashSet<>();
    for (MdObjectAddType type : MdObjectAddType.values()) {
      String proto = GoldenScaffold.protoName(type);
      files.addAll(objectFiles(snapshot, type, proto, proto).keySet());
    }
    return files.size();
  }

  /** Содержимое управляемой формы: у прав и описания WS-ссылки моделей нет. */
  private static boolean isManagedForm(Path file) {
    return file.endsWith(Path.of("Ext", "Form.xml"));
  }

  /**
   * Файлы объекта в каталоге выгрузки: описание и всё из каталога объекта.
   *
   * @return путь относительно каталога выгрузки, где имя объекта заменено именем прототипа, - файл
   */
  private static Map<String, Path> objectFiles(Path root, MdObjectAddType type, String name, String proto)
    throws IOException {
    Map<String, Path> files = new TreeMap<>();
    Path description = CfLayout.objectXmlInSubdir(root, type.cfSubdir(), name);
    if (Files.isRegularFile(description)) {
      files.put(type.cfSubdir() + "/" + proto + ".xml", description);
    }
    Path directory = root.resolve(type.cfSubdir()).resolve(name);
    if (Files.isDirectory(directory)) {
      try (Stream<Path> walk = Files.walk(directory)) {
        for (Path file : walk.filter(Files::isRegularFile).toList()) {
          files.put(type.cfSubdir() + "/" + proto + "/" + directory.relativize(file).toString().replace('\\', '/'), file);
        }
      }
    }
    return files;
  }

  private static String normalized(Path file, String name, String proto) {
    Pattern token = Pattern.compile("(?<![\\p{L}\\p{N}_])" + Pattern.quote(name) + "(?![\\p{L}\\p{N}_])");
    String renamed = token.matcher(GoldenSnapshots.read(file)).replaceAll(Matcher.quoteReplacement(proto));
    return GoldenSnapshots.normalizeUuids(renamed);
  }
}
