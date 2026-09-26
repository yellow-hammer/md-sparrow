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
    String name = resolveNonConflictingName(configurationXml, version, type, cfRoot, objectName);
    writeNewObject(configurationXml, cfRoot, name, version, type, catalogSynonymRu, catalogSynonymEmpty);
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
    String compatibilityMode = ScaffoldPropertyEdit.leaf(configuration, "CompatibilityMode").orElse(null);
    String description = GoldenScaffold.objectRelative(type, name);
    for (Map.Entry<String, String> file : files.entrySet()) {
      String text = file.getValue();
      if (type == MdObjectAddType.CATALOG && file.getKey().equals(description)) {
        text = applyCatalogSynonym(text, name, catalogSynonymRu, catalogSynonymEmpty);
      }
      // Эталон снят в режиме совместимости 8.3.12: форма объявляет пространства имён по режиму конфигурации
      text = FormNamespaceRules.forCompatibility(text, compatibilityMode);
      // Эталон снят на русской конфигурации: подписи нового объекта уезжают в язык этой
      text = DistinctUuidRewrite.remapDeterministic(LocalStringElement.retarget(text, language), seed);
      Path target = cfRoot.resolve(file.getKey());
      Files.createDirectories(target.getParent());
      Files.writeString(target, text, StandardCharsets.UTF_8);
    }
    ConfigurationChildObjectAppender.append(configurationXml, type.configurationXmlTag(), name);
  }

  private static String resolveNonConflictingName(
    Path configurationXml, SchemaVersion version, MdObjectAddType type, Path cfRoot, String objectName)
    throws IOException, JAXBException {
    Set<String> taken = MdObjectAddNextName.mergeTakenNames(configurationXml, version, type, cfRoot);
    if (!taken.contains(objectName)) {
      Path out = CfLayout.objectXmlInSubdir(cfRoot, type.cfSubdir(), objectName);
      if (!Files.exists(out)) {
        return objectName;
      }
    }
    return MdObjectAddNextName.nextFreeName(configurationXml, version, type, cfRoot);
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
