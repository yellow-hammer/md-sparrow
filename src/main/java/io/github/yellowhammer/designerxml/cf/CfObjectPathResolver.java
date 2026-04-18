/*
 * This file is a part of md-sparrow.
 *
 * Copyright (c) 2026
 * Ivan Karlo <i.karlo@outlook.com> and contributors
 *
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package io.github.yellowhammer.designerxml.cf;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Поиск пути к XML объекта по имени внутри каталога выгрузки {@code src/cf}.
 */
public final class CfObjectPathResolver {

  private CfObjectPathResolver() {
  }

  /** {@code Catalogs/&lt;имя&gt;.xml}. */
  public static Optional<Path> catalogXml(Path cfRoot, String name) {
    Path p = CfLayout.catalogObjectXml(cfRoot, name);
    return Files.isRegularFile(p) ? Optional.of(p) : Optional.empty();
  }

  /** {@code Documents/&lt;имя&gt;.xml}. */
  public static Optional<Path> documentXml(Path cfRoot, String name) {
    Path p = CfLayout.objectXmlInSubdir(cfRoot, "Documents", name);
    return Files.isRegularFile(p) ? Optional.of(p) : Optional.empty();
  }

  /** {@code ExchangePlans/&lt;имя&gt;.xml}. */
  public static Optional<Path> exchangePlanXml(Path cfRoot, String name) {
    Path p = CfLayout.objectXmlInSubdir(cfRoot, "ExchangePlans", name);
    return Files.isRegularFile(p) ? Optional.of(p) : Optional.empty();
  }

  public static Optional<Path> objectXml(Path cfRoot, String objectType, String name) throws IOException {
    if (name == null || name.isEmpty()) {
      return Optional.empty();
    }
    return switch (objectType) {
      case "Catalog" -> catalogXml(cfRoot, name);
      case "Constant" -> inSubdir(cfRoot, "Constants", name);
      case "Enum" -> inSubdir(cfRoot, "Enums", name);
      case "Document" -> documentXml(cfRoot, name);
      case "DocumentJournal" -> inSubdir(cfRoot, "DocumentJournals", name);
      case "Report" -> inSubdir(cfRoot, "Reports", name);
      case "DataProcessor" -> inSubdir(cfRoot, "DataProcessors", name);
      case "Task" -> inSubdir(cfRoot, "Tasks", name);
      case "ChartOfAccounts" -> inSubdir(cfRoot, "ChartsOfAccounts", name);
      case "ChartOfCharacteristicTypes" -> inSubdir(cfRoot, "ChartsOfCharacteristicTypes", name);
      case "ChartOfCalculationTypes" -> inSubdir(cfRoot, "ChartsOfCalculationTypes", name);
      case "InformationRegister" -> inSubdir(cfRoot, "InformationRegisters", name);
      case "AccumulationRegister" -> inSubdir(cfRoot, "AccumulationRegisters", name);
      case "AccountingRegister" -> inSubdir(cfRoot, "AccountingRegisters", name);
      case "CalculationRegister" -> inSubdir(cfRoot, "CalculationRegisters", name);
      case "BusinessProcess" -> inSubdir(cfRoot, "BusinessProcesses", name);
      case "CommonModule" -> inSubdir(cfRoot, "CommonModules", name);
      case "Subsystem" -> subsystemXml(cfRoot, name);
      case "SessionParameter" -> inSubdir(cfRoot, "SessionParameters", name);
      case "ExchangePlan" -> exchangePlanXml(cfRoot, name);
      case "FilterCriterion" -> inSubdir(cfRoot, "FilterCriteria", name);
      case "EventSubscription" -> inSubdir(cfRoot, "EventSubscriptions", name);
      case "ScheduledJob" -> inSubdir(cfRoot, "ScheduledJobs", name);
      case "FunctionalOption" -> inSubdir(cfRoot, "FunctionalOptions", name);
      case "FunctionalOptionsParameter" -> inSubdir(cfRoot, "FunctionalOptionsParameters", name);
      case "DefinedType" -> inSubdir(cfRoot, "DefinedTypes", name);
      case "SettingsStorage" -> inSubdir(cfRoot, "SettingsStorages", name);
      case "CommonCommand" -> inSubdir(cfRoot, "CommonCommands", name);
      case "CommandGroup" -> inSubdir(cfRoot, "CommandGroups", name);
      case "CommonForm" -> inSubdir(cfRoot, "CommonForms", name);
      case "CommonTemplate" -> inSubdir(cfRoot, "CommonTemplates", name);
      case "CommonAttribute" -> inSubdir(cfRoot, "CommonAttributes", name);
      case "CommonPicture" -> inSubdir(cfRoot, "CommonPictures", name);
      case "XDTOPackage" -> inSubdir(cfRoot, "XDTOPackages", name);
      case "WebService" -> inSubdir(cfRoot, "WebServices", name);
      case "HTTPService" -> inSubdir(cfRoot, "HTTPServices", name);
      case "Interface" -> inSubdir(cfRoot, "Interfaces", name);
      case "WSReference" -> inSubdir(cfRoot, "WSReferences", name);
      case "WebSocketClient" -> inSubdir(cfRoot, "WebSocketClients", name);
      case "IntegrationService" -> inSubdir(cfRoot, "IntegrationServices", name);
      case "Bot" -> inSubdir(cfRoot, "Bots", name);
      case "StyleItem" -> inSubdir(cfRoot, "StyleItems", name);
      case "Style" -> inSubdir(cfRoot, "Styles", name);
      case "Language" -> inSubdir(cfRoot, "Languages", name);
      case "PaletteColor" -> inSubdir(cfRoot, "PaletteColors", name);
      case "DocumentNumerator" -> inSubdir(cfRoot, "DocumentNumerators", name);
      case "Sequence" -> inSubdir(cfRoot, "Sequences", name);
      case "ExternalDataSource" -> inSubdir(cfRoot, "ExternalDataSources", name);
      case "Role" -> inSubdir(cfRoot, "Roles", name);
      default -> Optional.empty();
    };
  }

  /**
   * Подсистема: рекурсивный поиск файла с заданным именем в каталоге {@code Subsystems}.
   */
  public static Optional<Path> subsystemXml(Path cfRoot, String name) throws IOException {
    Path root = cfRoot.resolve("Subsystems");
    if (!Files.isDirectory(root)) {
      return Optional.empty();
    }
    String fileName = name + ".xml";
    try (Stream<Path> walk = Files.walk(root)) {
      return walk.filter(p -> Files.isRegularFile(p) && p.getFileName().toString().equals(fileName)).findFirst();
    }
  }

  private static Optional<Path> inSubdir(Path cfRoot, String subdir, String name) {
    Path p = CfLayout.objectXmlInSubdir(cfRoot, subdir, name);
    return Files.isRegularFile(p) ? Optional.of(p) : Optional.empty();
  }
}
