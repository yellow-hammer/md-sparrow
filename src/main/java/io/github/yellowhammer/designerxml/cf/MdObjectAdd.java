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

import io.github.yellowhammer.designerxml.SchemaVersion;
import jakarta.xml.bind.JAXBException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class MdObjectAdd {

  private MdObjectAdd() {
  }

  public static void add(Path configurationXml, String objectName, SchemaVersion version, MdObjectAddType type)
    throws IOException, JAXBException {
    add(configurationXml, objectName, version, type, null, false);
  }

  public static void add(
    Path configurationXml,
    String objectName,
    SchemaVersion version,
    MdObjectAddType type,
    String catalogSynonymRu,
    boolean catalogSynonymEmpty)
    throws IOException, JAXBException {
    CatalogNameConstraints.check(objectName);
    type.requireIn(version);
    Path cfRoot = requireCfRoot(configurationXml);
    requireFreeName(configurationXml, version, type, cfRoot, objectName);
    writeNewObject(configurationXml, cfRoot, objectName, version, type, catalogSynonymRu, catalogSynonymEmpty);
  }

  /**
   * Создаёт объект с первым свободным именем вида {@code ПрефиксN} (см. {@link MdObjectAddType#namePrefix()}).
   *
   * @return фактическое имя созданного объекта
   */
  public static String addWithNextAvailableName(
    Path configurationXml,
    SchemaVersion version,
    MdObjectAddType type,
    String catalogSynonymRu,
    boolean catalogSynonymEmpty)
    throws IOException, JAXBException {
    type.requireIn(version);
    Path cfRoot = requireCfRoot(configurationXml);
    String name = MdObjectAddNextName.nextFreeName(configurationXml, version, type, cfRoot);
    CatalogNameConstraints.check(name);
    writeNewObject(configurationXml, cfRoot, name, version, type, catalogSynonymRu, catalogSynonymEmpty);
    return name;
  }

  private static Path requireCfRoot(Path configurationXml) {
    Path cfRoot = configurationXml.getParent();
    if (cfRoot == null || !Files.isRegularFile(configurationXml)) {
      throw new IllegalArgumentException("configuration XML must exist: " + configurationXml);
    }
    return cfRoot;
  }

  private static void writeNewObject(
    Path configurationXml,
    Path cfRoot,
    String name,
    SchemaVersion version,
    MdObjectAddType type,
    String catalogSynonymRu,
    boolean catalogSynonymEmpty)
    throws IOException, JAXBException {
    // Все файлы прототипа из эталона: описание и то, что лежит в каталоге объекта (Ext/…)
    Map<String, String> files = GoldenScaffold.generateObjectFiles(type, name, version);
    for (String relative : files.keySet()) {
      if (Files.exists(cfRoot.resolve(relative))) {
        throw new IllegalArgumentException("object file already exists: " + cfRoot.resolve(relative));
      }
    }

    String language = ConfigurationLanguage.codeOf(configurationXml);
    String configuration = Files.readString(configurationXml, StandardCharsets.UTF_8);
    // Одно зерно на все файлы объекта: общие UUID описания и его Ext остаются общими
    String seed = GoldenUuid.from("add|" + version.name() + "|" + type + "|" + name, configuration);
    String compatibilityMode = FormNamespaceRules.compatibilityModeOf(configuration);
    String description = GoldenScaffold.objectRelative(type, name);
    Map<Path, String> texts = new LinkedHashMap<>();
    for (Map.Entry<String, String> file : files.entrySet()) {
      String text = file.getValue();
      if (type == MdObjectAddType.CATALOG && file.getKey().equals(description)) {
        text = applyCatalogSynonym(text, name, catalogSynonymRu, catalogSynonymEmpty);
      }
      // Эталон снят в режиме совместимости 8.3.12: форма объявляет пространства имён по режиму конфигурации
      text = FormNamespaceRules.forCompatibility(text, compatibilityMode);
      // Эталон снят на русской конфигурации: подписи нового объекта уезжают в язык этой
      text = DistinctUuidRewrite.remapDeterministic(LocalStringElement.retarget(text, language), seed);
      texts.put(cfRoot.resolve(file.getKey()), text);
    }
    // Состав готовится до записи: его отказ не оставляет на диске файлов объекта без ссылки
    String updated = ConfigurationChildObjectAppender.appended(configuration, type.configurationXmlTag(), name);

    CreatedFiles created = new CreatedFiles();
    try {
      for (Map.Entry<Path, String> file : texts.entrySet()) {
        created.write(file.getKey(), file.getValue());
      }
      ConfigurationChildObjectAppender.replace(configurationXml, updated);
    } catch (IOException | RuntimeException e) {
      created.rollback(e);
      throw e;
    }
    // Состав изменился - служебный файл версий объектов не должен от него отставать
    ConfigDumpInfoSync.sync(cfRoot);
  }

  /**
   * Файлы и каталоги, созданные добавлением объекта. Если запись не дошла до конца, они удаляются:
   * на диске не остаётся файлов объекта, на которые не ссылается состав.
   */
  private static final class CreatedFiles {

    private final Deque<Path> created = new ArrayDeque<>();

    void write(Path target, String text) throws IOException {
      List<Path> missing = new ArrayList<>();
      for (Path dir = target.getParent(); dir != null && !Files.exists(dir); dir = dir.getParent()) {
        missing.add(0, dir);
      }
      for (Path dir : missing) {
        Files.createDirectory(dir);
        created.push(dir);
      }
      Files.writeString(target, text, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
      created.push(target);
    }

    /** Удаляет созданное: сначала файлы, затем каталоги от вложенных к внешним. */
    void rollback(Exception failure) {
      while (!created.isEmpty()) {
        try {
          Files.deleteIfExists(created.pop());
        } catch (IOException e) {
          failure.addSuppressed(e);
        }
      }
    }
  }

  /**
   * Отказывает, если имя занято: оно есть в составе конфигурации, либо на диске уже лежит
   * описание или каталог объекта с этим именем. Тихо брать другое имя нельзя: вызывающий
   * обращается к объекту по своему имени, как и в проекте EDT.
   */
  private static void requireFreeName(
    Path configurationXml, SchemaVersion version, MdObjectAddType type, Path cfRoot, String objectName)
    throws IOException, JAXBException {
    Set<String> taken = MdObjectAddNextName.mergeTakenNames(configurationXml, version, type, cfRoot);
    if (taken.contains(objectName)
      || Files.exists(CfLayout.objectXmlInSubdir(cfRoot, type.cfSubdir(), objectName))
      || Files.isDirectory(cfRoot.resolve(type.cfSubdir()).resolve(objectName))) {
      throw new IllegalArgumentException(UiLabels.alreadyExists(type.configurationXmlTag(), objectName));
    }
  }

  /**
   * Синоним справочника: {@code catalogSynonymEmpty} → пустой {@code <Synonym/>}; явный {@code catalogSynonymRu}
   * → его текст; иначе оставляем как в эталоне (после параметризации это имя объекта).
   */
  private static String applyCatalogSynonym(
    String xml, String name, String catalogSynonymRu, boolean catalogSynonymEmpty) {
    java.util.regex.Matcher m = java.util.regex.Pattern.compile("(?s)<Synonym>.*?</Synonym>").matcher(xml);
    if (catalogSynonymEmpty) {
      return m.find() ? xml.substring(0, m.start()) + "<Synonym/>" + xml.substring(m.end()) : xml;
    }
    String ru = catalogSynonymRu == null ? "" : catalogSynonymRu.trim();
    if (ru.isEmpty() || ru.equals(name)) {
      return xml;
    }
    if (!m.find()) {
      return xml;
    }
    String block = m.group().replaceFirst(
      "(<v8:content>).*?(</v8:content>)",
      "$1" + java.util.regex.Matcher.quoteReplacement(ru) + "$2");
    return xml.substring(0, m.start()) + block + xml.substring(m.end());
  }
}
