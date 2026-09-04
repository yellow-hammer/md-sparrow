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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.emf.ecore.EEnum;
import org.eclipse.emf.ecore.EEnumLiteral;

import io.github.yellowhammer.designerxml.cf.CatalogNameConstraints;
import io.github.yellowhammer.designerxml.cf.EmptyCfeScaffold.Purpose;

/**
 * Новое расширение проекта 1С:EDT.
 *
 * Заготовкой служит проект расширения, который сама 1С:EDT записала при
 * импорте пустого расширения конфигуратора: описание проекта, его манифест с
 * базовым проектом и описание расширения с идентификаторами. У заготовки
 * меняются имя, синоним, префикс, назначение и идентификаторы, а режим
 * совместимости берётся у расширяемой конфигурации.
 */
public final class EdtExtensionScaffold {

  /** Эталон: проект расширения «Пустое» к конфигурации «Основа». */
  private static final String GOLDEN = "Extension/Основа.Пустое/";
  private static final String PROTO_NAME = "Пустое";
  private static final String PROTO_BASE = "Основа";
  private static final List<String> FILES = List.of(
      ".project",
      ".settings/org.eclipse.core.resources.prefs",
      "DT-INF/PROJECT.PMF",
      "src/Configuration/Configuration.mdo",
      "src/Roles/ОсновнаяРоль/ОсновнаяРоль.mdo");

  private static final Pattern SYNONYM = Pattern.compile("(<synonym>\\s*<key>ru</key>\\s*<value>)[^<]*(</value>)");
  private static final Pattern NAME_PREFIX = Pattern.compile("<namePrefix>[^<]*</namePrefix>");
  private static final Pattern EXTENSION_COMPATIBILITY = Pattern.compile(
      "<configurationExtensionCompatibilityMode>[^<]*</configurationExtensionCompatibilityMode>");
  private static final Pattern PURPOSE = Pattern.compile("<configurationExtensionPurpose>[^<]*</configurationExtensionPurpose>");
  private static final Pattern COMPATIBILITY = Pattern.compile("<compatibilityMode>([^<]*)</compatibilityMode>");
  private static final Pattern RUNTIME_VERSION = Pattern.compile("^Runtime-Version:\\s*(.+)$", Pattern.MULTILINE);
  private static final Pattern PROJECT_NAME = Pattern.compile("<name>([^<]+)</name>");

  private EdtExtensionScaffold() {
  }

  /**
   * Создаёт проект расширения рядом с расширяемым.
   *
   * @param baseConfigurationMdo описание расширяемой конфигурации
   * @param targetProjectDir каталог нового проекта; его ещё не должно быть
   * @param name имя расширения
   * @param synonymRu синоним; пустой заменяется именем
   * @param namePrefix префикс имён новых объектов; пустой не записывается
   * @param purpose назначение расширения
   * @param model метамодель EDT
   * @throws IOException если файлы не читаются или не пишутся
   */
  public static void create(Path baseConfigurationMdo, Path targetProjectDir, String name, String synonymRu,
      String namePrefix, Purpose purpose, EdtModel model) throws IOException {
    CatalogNameConstraints.check(name);
    if (Files.exists(targetProjectDir)) {
      throw new IllegalArgumentException("Каталог уже есть: " + targetProjectDir);
    }
    Path baseProject = EdtObjectScaffold.sourceRoot(baseConfigurationMdo).getParent();
    if (baseProject == null || !EdtLayout.isProject(baseProject)) {
      throw new IllegalArgumentException("Расширяемая конфигурация лежит не в проекте EDT: " + baseConfigurationMdo);
    }
    String baseName = projectName(baseProject);
    String synonym = synonymRu == null || synonymRu.isBlank() ? name : synonymRu.trim();
    String compatibility = compatibilityMode(baseConfigurationMdo);
    String runtime = runtimeVersion(baseProject);

    for (String file : FILES) {
      String text = EdtObjectScaffold.golden(GOLDEN + file);
      // Имя проекта сложено из имени базового проекта и имени расширения
      text = EdtObjectScaffold.renamed(EdtObjectScaffold.renamed(text, PROTO_BASE, baseName), PROTO_NAME, name);
      if (file.endsWith("PROJECT.PMF") && runtime != null) {
        text = RUNTIME_VERSION.matcher(text).replaceFirst("Runtime-Version: " + Matcher.quoteReplacement(runtime));
      } else if (file.endsWith("Configuration.mdo")) {
        text = configuration(text, name, synonym, namePrefix, purpose, compatibility, model);
      }
      text = EdtObjectScaffold.freshUuids(text);
      Path target = targetProjectDir.resolve(file);
      Files.createDirectories(target.getParent());
      Files.writeString(target, text, StandardCharsets.UTF_8);
    }
  }

  /** Описание расширения под именем, синонимом, префиксом, назначением и режимом совместимости. */
  private static String configuration(String golden, String name, String synonym, String namePrefix,
      Purpose purpose, String compatibility, EdtModel model) {
    String text = golden.replace("<name>" + PROTO_NAME + "</name>", "<name>" + escape(name) + "</name>");
    text = SYNONYM.matcher(text).replaceFirst("$1" + Matcher.quoteReplacement(escape(synonym)) + "$2");
    text = namePrefix == null || namePrefix.isBlank()
        ? text.replaceAll("(?m)^[ \\t]*<namePrefix>[^<]*</namePrefix>\\r?\\n", "")
        : NAME_PREFIX.matcher(text).replaceFirst(
            Matcher.quoteReplacement("<namePrefix>" + escape(namePrefix.trim()) + "</namePrefix>"));
    if (compatibility != null) {
      text = EXTENSION_COMPATIBILITY.matcher(text).replaceFirst(Matcher.quoteReplacement(
          "<configurationExtensionCompatibilityMode>" + compatibility + "</configurationExtensionCompatibilityMode>"));
    }
    return PURPOSE.matcher(text).replaceFirst(Matcher.quoteReplacement(
        "<configurationExtensionPurpose>" + purposeLiteral(purpose, model) + "</configurationExtensionPurpose>"));
  }

  /** Литерал назначения из схемы EDT: написание конфигуратора совпадает с ним без учёта регистра. */
  private static String purposeLiteral(Purpose purpose, EdtModel model) {
    org.eclipse.emf.ecore.EClass configuration = model.classOf("Configuration");
    org.eclipse.emf.ecore.EStructuralFeature feature = configuration == null
        ? null
        : configuration.getEStructuralFeature("configurationExtensionPurpose");
    if (feature != null && feature.getEType() instanceof EEnum type) {
      for (EEnumLiteral literal : type.getELiterals()) {
        if (literal.getLiteral().equalsIgnoreCase(purpose.xmlValue())) {
          return literal.getLiteral();
        }
      }
    }
    throw new IllegalArgumentException("Схема EDT не знает назначение расширения " + purpose.xmlValue());
  }

  /**
   * Режим совместимости расширения: как у расширяемой конфигурации.
   *
   * Платформа не пускает расширение с режимом выше, чем у конфигурации,
   * поэтому берётся её {@code compatibilityMode}; без него остаётся эталонный.
   */
  private static String compatibilityMode(Path configurationMdo) throws IOException {
    Matcher matcher = COMPATIBILITY.matcher(Files.readString(configurationMdo, StandardCharsets.UTF_8));
    return matcher.find() && !matcher.group(1).isBlank() ? matcher.group(1).trim() : null;
  }

  /** Версия платформы проекта из манифеста; без манифеста остаётся эталонная. */
  private static String runtimeVersion(Path projectDir) throws IOException {
    Path manifest = projectDir.resolve("DT-INF").resolve("PROJECT.PMF");
    if (!Files.isRegularFile(manifest)) {
      return null;
    }
    Matcher matcher = RUNTIME_VERSION.matcher(Files.readString(manifest, StandardCharsets.UTF_8));
    return matcher.find() ? matcher.group(1).trim() : null;
  }

  /** Имя проекта из его описания: каталог может называться иначе. */
  static String projectName(Path projectDir) throws IOException {
    Path description = projectDir.resolve(EdtLayout.PROJECT_FILE);
    if (Files.isRegularFile(description)) {
      Matcher matcher = PROJECT_NAME.matcher(Files.readString(description, StandardCharsets.UTF_8));
      if (matcher.find()) {
        return matcher.group(1).trim();
      }
    }
    return projectDir.getFileName().toString();
  }

  private static String escape(String value) {
    return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
  }
}
