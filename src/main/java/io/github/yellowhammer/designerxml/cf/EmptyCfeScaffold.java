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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Пустое расширение конфигурации: {@code Configuration.xml} и роль по умолчанию.
 *
 * Каркас берётся из эталона выгрузки расширения нужной версии формата, поверх
 * подставляются свойства вызывающего: имя, синоним, префикс имён, назначение и
 * режимы совместимости. Режимы задаёт вызывающий, а не scaffold: расширение
 * должно совпадать по ним с той конфигурацией, к которой его подключают.
 */
public final class EmptyCfeScaffold {

  /** Назначение расширения: значения перечисления схемы формата. */
  public enum Purpose {
    PATCH("Patch"),
    CUSTOMIZATION("Customization"),
    ADD_ON("AddOn");

    private final String xmlValue;

    Purpose(String xmlValue) {
      this.xmlValue = xmlValue;
    }

    public String xmlValue() {
      return xmlValue;
    }

    /** Значение из CLI: {@code patch}, {@code customization}, {@code add-on}. */
    public static Purpose fromCliName(String name) {
      Objects.requireNonNull(name, "purpose");
      String normalized = name.trim().toLowerCase().replace("-", "").replace("_", "");
      for (Purpose purpose : values()) {
        if (purpose.xmlValue.toLowerCase().equals(normalized)) {
          return purpose;
        }
      }
      throw new IllegalArgumentException(
        "неизвестное назначение расширения: " + name + " (ожидается patch, customization или add-on)");
    }
  }

  /** Режим 8.3.13: последний, в котором переопределять свойства заимствованных объектов нельзя. */
  private static final int[] LAST_MODE_WITHOUT_OVERRIDES = {8, 3, 13};

  private static final Pattern DEFAULT_ROLES =
    Pattern.compile("(?s)\\R[\\t ]*(?:<DefaultRoles/>|<DefaultRoles>.*?</DefaultRoles>)");

  private static final Pattern INTERFACE_COMPATIBILITY_MODE = Pattern.compile(
    "\\R[\\t ]*(?:<InterfaceCompatibilityMode/>|<InterfaceCompatibilityMode>[^<]*</InterfaceCompatibilityMode>)");

  private static final Pattern CHILD_OBJECTS = Pattern.compile("(?s)<ChildObjects>(.*?)</ChildObjects>");

  private static final Pattern ROLE_ENTRY = Pattern.compile("\\R[\\t ]*<Role>[^<]*</Role>");

  private EmptyCfeScaffold() {
  }

  /**
   * Пишет каркас расширения в каталог.
   *
   * <p>В режиме совместимости 8.3.13 и ниже (и в {@code DontUse}, который платформа читает как 8.3.8)
   * расширение не может переопределять свойства заимствованной конфигурации, поэтому каркас
   * получается таким, каким его создаёт платформа:
   * без основных ролей, роли по умолчанию и режима совместимости интерфейса.
   *
   * @param targetCfeRoot каталог расширения (создаётся, содержимое очищается)
   * @param extensionName имя расширения
   * @param synonym синоним; пустой - синоним остаётся пустым, как у расширения платформы
   * @param namePrefix префикс имён объектов расширения; пустой - как в эталоне
   * @param purpose назначение расширения
   * @param compatibilityMode режим совместимости расширения из основной конфигурации
   * @param interfaceCompatibilityMode режим совместимости интерфейса; пустой - как в эталоне;
   *                                   в режиме совместимости 8.3.13 и ниже не пишется
   * @param version версия формата выгрузки
   */
  public static void writeEmptyTree(
    Path targetCfeRoot,
    String extensionName,
    String synonym,
    String namePrefix,
    Purpose purpose,
    String compatibilityMode,
    String interfaceCompatibilityMode,
    SchemaVersion version) throws IOException {
    writeEmptyTree(targetCfeRoot, extensionName, synonym, namePrefix, purpose, compatibilityMode,
      interfaceCompatibilityMode, version, ConfigurationLanguage.FALLBACK);
  }

  /**
   * @param language язык текстов расширяемой конфигурации: расширение живёт её языком
   */
  public static void writeEmptyTree(
    Path targetCfeRoot,
    String extensionName,
    String synonym,
    String namePrefix,
    Purpose purpose,
    String compatibilityMode,
    String interfaceCompatibilityMode,
    SchemaVersion version,
    String language) throws IOException {
    Objects.requireNonNull(targetCfeRoot, "targetCfeRoot");
    Objects.requireNonNull(purpose, "purpose");
    CatalogNameConstraints.check(extensionName);

    String xml = GoldenScaffold.generateEmptyExtension(extensionName, version);
    xml = LocalStringElement.retarget(xml, language);
    if (namePrefix != null && !namePrefix.isBlank()) {
      xml = ScaffoldPropertyEdit.setLeaf(xml, "NamePrefix", namePrefix);
    }
    xml = ScaffoldPropertyEdit.setLeaf(xml, "ConfigurationExtensionPurpose", purpose.xmlValue());
    if (compatibilityMode != null && !compatibilityMode.isBlank()) {
      xml = ScaffoldPropertyEdit.setLeaf(xml, "ConfigurationExtensionCompatibilityMode", compatibilityMode.trim());
    }
    boolean overrides = overridesAdoptedProperties(
      ScaffoldPropertyEdit.leaf(xml, "ConfigurationExtensionCompatibilityMode").orElse(""));
    if (overrides) {
      if (interfaceCompatibilityMode != null && !interfaceCompatibilityMode.isBlank()) {
        xml = ScaffoldPropertyEdit.setOrInsertLeaf(
          xml, "InterfaceCompatibilityMode", interfaceCompatibilityMode.trim(),
          "ConfigurationInformationAddress");
      }
    } else {
      xml = withoutAdoptedOverrides(xml);
    }
    if (synonym != null && !synonym.isBlank()) {
      xml = ScaffoldPropertyEdit.setSynonym(xml, synonym, language);
    }

    CfTreeDelete.deleteAllContents(targetCfeRoot);
    Files.createDirectories(targetCfeRoot);
    if (overrides) {
      Path rolesDir = targetCfeRoot.resolve("Roles");
      Files.createDirectories(rolesDir);
      Files.writeString(
        rolesDir.resolve(GoldenScaffold.extensionDefaultRoleName() + ".xml"),
        LocalStringElement.retarget(
          GoldenScaffold.generateExtensionDefaultRole(extensionName, version), language),
        StandardCharsets.UTF_8);
    }
    Files.writeString(targetCfeRoot.resolve(CfLayout.CONFIGURATION_XML), xml, StandardCharsets.UTF_8);
  }

  /**
   * Может ли расширение в этом режиме совместимости переопределять свойства заимствованных
   * объектов, в том числе самой конфигурации.
   *
   * <p>В режиме 8.3.13 и ниже платформа такое расширение не принимает («Переопределение свойств
   * заимствованных объектов в расширениях недопустимо в режиме совместимости 8.3.13 и ниже»).
   * {@code DontUse} платформа читает как 8.3.8: после загрузки выгружает {@code Version8_3_8} и
   * отвергает расширение с ролями (ibcmd 8.3.23, 8.3.24, 8.5.1, {@code config check --extension}),
   * а без ролей принимает, как и сама создаёт расширение к конфигурации в этом режиме.
   *
   * @param compatibilityMode значение {@code ConfigurationExtensionCompatibilityMode}, например {@code Version8_3_12}
   * @return {@code false} для режима 8.3.13 и ниже и для {@code DontUse}
   */
  static boolean overridesAdoptedProperties(String compatibilityMode) {
    return CompatibilityModes.compare(compatibilityMode, LAST_MODE_WITHOUT_OVERRIDES).orElse(-1) > 0;
  }

  /**
   * Расширение таким, каким его создаёт сама платформа в старом режиме совместимости: без
   * основных ролей, роли по умолчанию и режима совместимости интерфейса.
   *
   * <p>Отвергает платформа именно основные роли - это свойство заимствованной конфигурации.
   * Режим совместимости интерфейса проверку проходит, но платформа его в таком расширении не
   * пишет, а роль по умолчанию без основных ролей теряет смысл.
   */
  private static String withoutAdoptedOverrides(String xml) {
    String out = DEFAULT_ROLES.matcher(xml).replaceAll("");
    out = INTERFACE_COMPATIBILITY_MODE.matcher(out).replaceAll("");
    Matcher childObjects = CHILD_OBJECTS.matcher(out);
    if (!childObjects.find()) {
      return out;
    }
    String inner = ROLE_ENTRY.matcher(childObjects.group(1)).replaceAll("");
    String replacement = inner.isBlank() ? "<ChildObjects/>" : "<ChildObjects>" + inner + "</ChildObjects>";
    return out.substring(0, childObjects.start()) + replacement + out.substring(childObjects.end());
  }

  /**
   * То же, но режимы совместимости берутся из основной конфигурации.
   *
   * <p>Режим совместимости интерфейса платформа сверяет с расширяемой конфигурацией, поэтому
   * угадывать его нельзя.
   *
   * <p>Режим совместимости расширения - {@code CompatibilityMode} конфигурации: так его выбирает
   * и сама платформа ({@code ibcmd infobase config extension create} на базе в режиме 8.3.12
   * пишет расширению {@code Version8_3_12}, хотя {@code ConfigurationExtensionCompatibilityMode}
   * у конфигурации выше). Это свойство конфигурации берётся только как запасное.
   *
   * @param mainConfigurationXml {@code Configuration.xml} расширяемой конфигурации
   */
  public static void writeEmptyTreeFromConfiguration(
    Path targetCfeRoot,
    String extensionName,
    String synonym,
    String namePrefix,
    Purpose purpose,
    Path mainConfigurationXml,
    SchemaVersion version) throws IOException {
    Objects.requireNonNull(mainConfigurationXml, "mainConfigurationXml");
    String main = Files.readString(mainConfigurationXml, StandardCharsets.UTF_8);
    writeEmptyTree(
      targetCfeRoot,
      extensionName,
      synonym,
      namePrefix,
      purpose,
      ScaffoldPropertyEdit.leaf(main, "CompatibilityMode")
        .or(() -> ScaffoldPropertyEdit.leaf(main, "ConfigurationExtensionCompatibilityMode"))
        .orElse(null),
      ScaffoldPropertyEdit.leaf(main, "InterfaceCompatibilityMode").orElse(null),
      version,
      ConfigurationLanguage.codeOf(mainConfigurationXml));
  }

}
