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

  private CfMdObjectMutations() {
  }

  public static void delete(Path configurationXml, Path objectXml, String xmlTag, String objectName)
    throws IOException {
    validateInputs(configurationXml, objectXml, xmlTag, objectName);
    ConfigurationChildObjectMutator.remove(configurationXml, xmlTag, objectName);
    deleteRoleExtIfExists(objectXml, xmlTag, objectName);
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
    moveRoleExtIfExists(objectXml, xmlTag, oldName, newName);
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
    copyRoleExtIfExists(objectXml, xmlTag, sourceName, newName);
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

  private static boolean isRoleTag(String xmlTag) {
    return "Role".equals(xmlTag);
  }

  private static void deleteRoleExtIfExists(Path objectXml, String xmlTag, String roleName) throws IOException {
    if (!isRoleTag(xmlTag)) {
      return;
    }
    Path roleDir = objectXml.getParent().resolve(roleName);
    if (Files.isDirectory(roleDir)) {
      deleteRecursively(roleDir);
    }
  }

  private static void moveRoleExtIfExists(Path objectXml, String xmlTag, String oldName, String newName)
    throws IOException {
    if (!isRoleTag(xmlTag)) {
      return;
    }
    Path srcDir = objectXml.getParent().resolve(oldName);
    if (!Files.isDirectory(srcDir)) {
      return;
    }
    Path dstDir = objectXml.getParent().resolve(newName);
    if (Files.exists(dstDir)) {
      throw new IllegalArgumentException("role ext dir already exists: " + dstDir);
    }
    try {
      Files.move(srcDir, dstDir, StandardCopyOption.ATOMIC_MOVE);
    } catch (AtomicMoveNotSupportedException e) {
      Files.move(srcDir, dstDir);
    }
  }

  private static void copyRoleExtIfExists(Path objectXml, String xmlTag, String sourceName, String newName)
    throws IOException {
    if (!isRoleTag(xmlTag)) {
      return;
    }
    Path srcDir = objectXml.getParent().resolve(sourceName);
    if (!Files.isDirectory(srcDir)) {
      return;
    }
    Path dstDir = objectXml.getParent().resolve(newName);
    if (Files.exists(dstDir)) {
      throw new IllegalArgumentException("role ext dir already exists: " + dstDir);
    }
    copyRecursively(srcDir, dstDir);
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
