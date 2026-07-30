package io.github.yellowhammer.designerxml.cf;

import io.github.yellowhammer.designerxml.SchemaVersion;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

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

  private EmptyCfeScaffold() {
  }

  /**
   * Пишет каркас расширения в каталог.
   *
   * @param targetCfeRoot каталог расширения (создаётся, содержимое очищается)
   * @param extensionName имя расширения
   * @param synonymRu синоним на русском; пустой - берётся имя
   * @param namePrefix префикс имён объектов расширения; пустой - как в эталоне
   * @param purpose назначение расширения
   * @param compatibilityMode режим совместимости расширения из основной конфигурации
   * @param interfaceCompatibilityMode режим совместимости интерфейса; пустой - как в эталоне
   * @param version версия формата выгрузки
   */
  public static void writeEmptyTree(
    Path targetCfeRoot,
    String extensionName,
    String synonymRu,
    String namePrefix,
    Purpose purpose,
    String compatibilityMode,
    String interfaceCompatibilityMode,
    SchemaVersion version) throws IOException {
    Objects.requireNonNull(targetCfeRoot, "targetCfeRoot");
    Objects.requireNonNull(purpose, "purpose");
    CatalogNameConstraints.check(extensionName);
    if (!GoldenScaffold.hasExtensionGolden(version)) {
      throw new IOException("нет эталона пустого расширения для формата " + version.metadataObjectVersionAttribute());
    }

    CfTreeDelete.deleteAllContents(targetCfeRoot);
    Files.createDirectories(targetCfeRoot);

    Path rolesDir = targetCfeRoot.resolve("Roles");
    Files.createDirectories(rolesDir);
    Files.writeString(
      rolesDir.resolve(GoldenScaffold.extensionDefaultRoleName(version) + ".xml"),
      GoldenScaffold.generateExtensionDefaultRole(extensionName, version),
      StandardCharsets.UTF_8);

    String xml = GoldenScaffold.generateEmptyExtension(extensionName, version);
    if (namePrefix != null && !namePrefix.isBlank()) {
      xml = ScaffoldPropertyEdit.setLeaf(xml, "NamePrefix", namePrefix);
    }
    xml = ScaffoldPropertyEdit.setLeaf(xml, "ConfigurationExtensionPurpose", purpose.xmlValue());
    if (compatibilityMode != null && !compatibilityMode.isBlank()) {
      xml = ScaffoldPropertyEdit.setLeaf(xml, "ConfigurationExtensionCompatibilityMode", compatibilityMode.trim());
    }
    if (interfaceCompatibilityMode != null && !interfaceCompatibilityMode.isBlank()) {
      xml = ScaffoldPropertyEdit.setOrInsertLeaf(
        xml, "InterfaceCompatibilityMode", interfaceCompatibilityMode.trim(),
        "ConfigurationInformationAddress");
    }
    if (synonymRu != null && !synonymRu.isBlank()) {
      xml = ScaffoldPropertyEdit.setSynonymRu(xml, synonymRu);
    }
    Files.writeString(targetCfeRoot.resolve(CfLayout.CONFIGURATION_XML), xml, StandardCharsets.UTF_8);
  }

  /**
   * То же, но режимы совместимости берутся из основной конфигурации.
   *
   * <p>Платформа отвергает расширение, у которого режим совместимости или режим совместимости
   * интерфейса не совпал с расширяемой конфигурацией, поэтому угадывать их нельзя.
   *
   * <p>Берётся {@code CompatibilityMode} конфигурации: платформа требует, чтобы режим
   * совместимости расширения не превышал режим совместимости расширяемой конфигурации
   * («Режим совместимости расширения конфигурации больше режима совместимости основной
   * конфигурации»). Свойство {@code ConfigurationExtensionCompatibilityMode} у конфигурации
   * может быть выше и на роль источника не годится - оно берётся только как запасное.
   *
   * @param mainConfigurationXml {@code Configuration.xml} расширяемой конфигурации
   */
  public static void writeEmptyTreeFromConfiguration(
    Path targetCfeRoot,
    String extensionName,
    String synonymRu,
    String namePrefix,
    Purpose purpose,
    Path mainConfigurationXml,
    SchemaVersion version) throws IOException {
    Objects.requireNonNull(mainConfigurationXml, "mainConfigurationXml");
    String main = Files.readString(mainConfigurationXml, StandardCharsets.UTF_8);
    writeEmptyTree(
      targetCfeRoot,
      extensionName,
      synonymRu,
      namePrefix,
      purpose,
      ScaffoldPropertyEdit.leaf(main, "CompatibilityMode")
        .or(() -> ScaffoldPropertyEdit.leaf(main, "ConfigurationExtensionCompatibilityMode"))
        .orElse(null),
      ScaffoldPropertyEdit.leaf(main, "InterfaceCompatibilityMode").orElse(null),
      version);
  }

}
