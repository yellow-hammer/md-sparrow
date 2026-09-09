/*
 * This file is a part of md-sparrow.
 *
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package io.github.yellowhammer.designerxml.cf;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Операции переименования/удаления/дублирования metadata-объекта в выгрузке CF.
 */
public final class CfMdObjectMutations {

  /** Описание объекта: только у таких файлов копии выдаются новые идентификаторы. */
  private static final Pattern METADATA_OBJECT_ROOT = Pattern.compile(
    "\\A[\\uFEFF\\s]*(?:<\\?[^>]*\\?>\\s*)?<(?:[\\w.-]+:)?MetaDataObject[\\s>]");

  private CfMdObjectMutations() {
  }

  public static void delete(Path configurationXml, Path objectXml, String xmlTag, String objectName)
    throws IOException {
    validateInputs(configurationXml, objectXml, xmlTag, objectName);
    ConfigurationChildObjectMutator.remove(configurationXml, xmlTag, objectName);
    deleteContentIfExists(objectXml, objectName);
    Files.deleteIfExists(objectXml);
  }

  public static void rename(
    Path configurationXml,
    Path objectXml,
    String xmlTag,
    String oldName,
    String newName
  ) throws IOException {
    validateInputs(configurationXml, objectXml, xmlTag, oldName);
    CatalogNameConstraints.check(newName);
    if (oldName.equals(newName)) {
      return;
    }
    Path targetXml = objectXml.resolveSibling(newName + ".xml");
    if (Files.exists(targetXml)) {
      throw new IllegalArgumentException("object file already exists: " + targetXml);
    }
    String source = Files.readString(objectXml, StandardCharsets.UTF_8);
    String renamed = replaceObjectName(source, oldName, newName);
    Files.writeString(targetXml, renamed, StandardCharsets.UTF_8);
    moveContentIfExists(objectXml, oldName, newName);
    Files.deleteIfExists(objectXml);
    ConfigurationChildObjectMutator.rename(configurationXml, xmlTag, oldName, newName);
  }

  public static void duplicate(
    Path configurationXml,
    Path objectXml,
    String xmlTag,
    String sourceName,
    String newName
  ) throws IOException {
    validateInputs(configurationXml, objectXml, xmlTag, sourceName);
    CatalogNameConstraints.check(newName);
    Path targetXml = objectXml.resolveSibling(newName + ".xml");
    if (Files.exists(targetXml)) {
      throw new IllegalArgumentException("object file already exists: " + targetXml);
    }
    String source = Files.readString(objectXml, StandardCharsets.UTF_8);
    String renamed = replaceObjectName(source, sourceName, newName);
    String remapped = DistinctUuidRewrite.remap(renamed);
    Files.writeString(targetXml, remapped, StandardCharsets.UTF_8);
    copyContentIfExists(objectXml, sourceName, newName);
    ConfigurationChildObjectAppender.append(configurationXml, xmlTag, newName);
  }

  private static void validateInputs(Path configurationXml, Path objectXml, String xmlTag, String objectName) {
    Objects.requireNonNull(configurationXml, "configurationXml");
    Objects.requireNonNull(objectXml, "objectXml");
    Objects.requireNonNull(xmlTag, "xmlTag");
    Objects.requireNonNull(objectName, "objectName");
    if (!Files.isRegularFile(configurationXml)) {
      throw new IllegalArgumentException("configuration XML must exist: " + configurationXml);
    }
    if (!Files.isRegularFile(objectXml)) {
      throw new IllegalArgumentException("object XML must exist: " + objectXml);
    }
    if (xmlTag.isBlank()) {
      throw new IllegalArgumentException("xmlTag required");
    }
    CatalogNameConstraints.check(objectName);
  }

  private static String replaceObjectName(String xml, String oldName, String newName) {
    String q = Pattern.quote(oldName);
    Pattern namePattern = Pattern.compile("(<(?:[\\w.-]+:)?Name>\\s*)" + q + "(\\s*</(?:[\\w.-]+:)?Name>)");
    Matcher matcher = namePattern.matcher(xml);
    if (!matcher.find()) {
      throw new IllegalArgumentException("Не найдено поле Name в XML объекта.");
    }
    String replacement = matcher.group(1) + Matcher.quoteReplacement(newName) + matcher.group(2);
    return matcher.replaceFirst(replacement);
  }

  /**
   * Каталог содержимого объекта: формы, макеты, команды, модули и права лежат
   * рядом с описанием, в каталоге с тем же именем. Каталога может не быть.
   */
  private static void deleteContentIfExists(Path objectXml, String objectName) throws IOException {
    Path content = objectXml.getParent().resolve(objectName);
    if (Files.isDirectory(content)) {
      deleteRecursively(content);
    }
  }

  private static void moveContentIfExists(Path objectXml, String oldName, String newName) throws IOException {
    Path source = objectXml.getParent().resolve(oldName);
    if (!Files.isDirectory(source)) {
      return;
    }
    Path target = objectXml.getParent().resolve(newName);
    if (Files.exists(target)) {
      throw new IllegalArgumentException("object content dir already exists: " + target);
    }
    try {
      Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
    } catch (AtomicMoveNotSupportedException e) {
      Files.move(source, target);
    }
  }

  private static void copyContentIfExists(Path objectXml, String sourceName, String newName) throws IOException {
    Path source = objectXml.getParent().resolve(sourceName);
    if (!Files.isDirectory(source)) {
      return;
    }
    Path target = objectXml.getParent().resolve(newName);
    if (Files.exists(target)) {
      throw new IllegalArgumentException("object content dir already exists: " + target);
    }
    copyRecursively(source, target);
    remapContentUuids(target);
  }

  /**
   * Собственные идентификаторы вложенных описаний копии.
   *
   * Формы, макеты и команды это отдельные объекты со своими uuid, и копия не
   * должна повторять исходные. Содержимое формы и прав идентификаторов объекта
   * не несёт, поэтому правятся только описания.
   */
  private static void remapContentUuids(Path directory) throws IOException {
    try (Stream<Path> stream = Files.walk(directory)) {
      for (Path file : stream.filter(Files::isRegularFile).toList()) {
        if (!file.getFileName().toString().endsWith(".xml")) {
          continue;
        }
        String xml = Files.readString(file, StandardCharsets.UTF_8);
        if (!METADATA_OBJECT_ROOT.matcher(xml).find()) {
          continue;
        }
        Files.writeString(file, DistinctUuidRewrite.remap(xml), StandardCharsets.UTF_8);
      }
    }
  }

  private static void deleteRecursively(Path path) throws IOException {
    if (Files.isDirectory(path)) {
      try (Stream<Path> stream = Files.list(path)) {
        for (Path child : stream.toList()) {
          deleteRecursively(child);
        }
      }
    }
    Files.delete(path);
  }

  private static void copyRecursively(Path source, Path target) throws IOException {
    if (Files.isDirectory(source)) {
      Files.createDirectories(target);
      try (Stream<Path> stream = Files.list(source)) {
        for (Path child : stream.toList()) {
          copyRecursively(child, target.resolve(child.getFileName().toString()));
        }
      }
      return;
    }
    Files.createDirectories(target.getParent());
    Files.copy(source, target);
  }
}
