/*
 * This file is a part of md-sparrow.
 *
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package io.github.yellowhammer.designerxml.cf;

import io.github.yellowhammer.designerxml.SchemaVersion;
import jakarta.xml.bind.JAXBException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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
    Path cfRoot = configurationXml.getParent();
    if (cfRoot == null || !Files.isRegularFile(configurationXml)) {
      throw new IllegalArgumentException("configuration XML must exist: " + configurationXml);
    }

    String name = resolveNonConflictingName(configurationXml, version, type, cfRoot, objectName);
    Path out = CfLayout.objectXmlInSubdir(cfRoot, type.cfSubdir(), name);
    if (Files.exists(out)) {
      throw new IllegalArgumentException("object file already exists: " + out);
    }

    String text = generateObjectXml(type, name, version, catalogSynonymRu, catalogSynonymEmpty);
    Files.createDirectories(out.getParent());
    Files.writeString(out, text, StandardCharsets.UTF_8);
    if (type.roleWithExtRights()) {
      writeRoleRights(cfRoot, name, version);
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

  private static void writeRoleRights(Path cfRoot, String roleName, SchemaVersion version) throws IOException {
    Path rightsXml = CfLayout.roleExtRightsXml(cfRoot, roleName);
    Files.createDirectories(rightsXml.getParent());
    Files.writeString(rightsXml, GoldenScaffold.generateRoleRights(roleName, version), StandardCharsets.UTF_8);
  }

  /**
   * Новый объект — параметризация эталона (golden) «голого» объекта нужной версии (см. {@link GoldenScaffold}).
   * Работает для любого формата, у которого есть эталон. Для справочника применяется опция синонима.
   */
  private static String generateObjectXml(
    MdObjectAddType type,
    String name,
    SchemaVersion version,
    String catalogSynonymRu,
    boolean catalogSynonymEmpty)
    throws IOException {
    String xml = GoldenScaffold.generateObject(type, name, version);
    if (type == MdObjectAddType.CATALOG) {
      xml = applyCatalogSynonym(xml, name, catalogSynonymRu, catalogSynonymEmpty);
    }
    return xml;
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
