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
import java.util.Map;
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

  /**
   * Каталог выгрузки для типа объекта: {@code "Catalog"} - {@code "Catalogs"} и так далее.
   *
   * <p>Подсистемы в таблице тоже есть, но вложенные лежат внутри родителя: путь к ним
   * ищет {@link #subsystemXml}, а по таблице находится только каталог верхнего уровня.
   */
  private static final Map<String, String> SUBDIR_BY_TYPE = Map.ofEntries(
    Map.entry("Subsystem", "Subsystems"),
    Map.entry("AccountingRegister", "AccountingRegisters"),
    Map.entry("AccumulationRegister", "AccumulationRegisters"),
    Map.entry("Bot", "Bots"),
    Map.entry("BusinessProcess", "BusinessProcesses"),
    Map.entry("CalculationRegister", "CalculationRegisters"),
    Map.entry("Catalog", "Catalogs"),
    Map.entry("ChartOfAccounts", "ChartsOfAccounts"),
    Map.entry("ChartOfCalculationTypes", "ChartsOfCalculationTypes"),
    Map.entry("ChartOfCharacteristicTypes", "ChartsOfCharacteristicTypes"),
    Map.entry("CommandGroup", "CommandGroups"),
    Map.entry("CommonAttribute", "CommonAttributes"),
    Map.entry("CommonCommand", "CommonCommands"),
    Map.entry("CommonForm", "CommonForms"),
    Map.entry("CommonModule", "CommonModules"),
    Map.entry("CommonPicture", "CommonPictures"),
    Map.entry("CommonTemplate", "CommonTemplates"),
    Map.entry("Constant", "Constants"),
    Map.entry("DataProcessor", "DataProcessors"),
    Map.entry("DefinedType", "DefinedTypes"),
    Map.entry("Document", "Documents"),
    Map.entry("DocumentJournal", "DocumentJournals"),
    Map.entry("DocumentNumerator", "DocumentNumerators"),
    Map.entry("Enum", "Enums"),
    Map.entry("EventSubscription", "EventSubscriptions"),
    Map.entry("ExchangePlan", "ExchangePlans"),
    Map.entry("ExternalDataSource", "ExternalDataSources"),
    Map.entry("FilterCriterion", "FilterCriteria"),
    Map.entry("FunctionalOption", "FunctionalOptions"),
    Map.entry("FunctionalOptionsParameter", "FunctionalOptionsParameters"),
    Map.entry("HTTPService", "HTTPServices"),
    Map.entry("InformationRegister", "InformationRegisters"),
    Map.entry("IntegrationService", "IntegrationServices"),
    Map.entry("Interface", "Interfaces"),
    Map.entry("Language", "Languages"),
    Map.entry("PaletteColor", "PaletteColors"),
    Map.entry("Report", "Reports"),
    Map.entry("Role", "Roles"),
    Map.entry("ScheduledJob", "ScheduledJobs"),
    Map.entry("Sequence", "Sequences"),
    Map.entry("SessionParameter", "SessionParameters"),
    Map.entry("SettingsStorage", "SettingsStorages"),
    Map.entry("Style", "Styles"),
    Map.entry("StyleItem", "StyleItems"),
    Map.entry("Task", "Tasks"),
    Map.entry("WSReference", "WSReferences"),
    Map.entry("WebService", "WebServices"),
    Map.entry("WebSocketClient", "WebSocketClients"),
    Map.entry("XDTOPackage", "XDTOPackages"));

  /** Соответствие типа объекта и каталога выгрузки; ключи - имена элементов {@code ChildObjects}. */
  public static Map<String, String> subdirsByType() {
    return SUBDIR_BY_TYPE;
  }

  public static Optional<Path> objectXml(Path cfRoot, String objectType, String name) throws IOException {
    if (name == null || name.isEmpty()) {
      return Optional.empty();
    }
    if ("Subsystem".equals(objectType)) {
      return subsystemXml(cfRoot, name);
    }
    String subdir = SUBDIR_BY_TYPE.get(objectType);
    return subdir == null ? Optional.empty() : inSubdir(cfRoot, subdir, name);
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
