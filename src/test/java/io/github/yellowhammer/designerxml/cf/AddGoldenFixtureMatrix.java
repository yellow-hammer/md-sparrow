/*
 * This file is a part of md-sparrow.
 *
 * Copyright (c) 2026
 * Ivan Karlo <i.karlo@outlook.com> and contributors
 *
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package io.github.yellowhammer.designerxml.cf;

import java.util.List;

/**
 * Матрица покрытия add-команд fixture/snapshot эталонами.
 */
final class AddGoldenFixtureMatrix {
  private static final String CMD_ADD_MD_OBJECT = "add-md-object";
  private static final String JAXB_DIFF =
    "Имя и UUID меняются детерминированно; профиль формируется JAXB/XSD без шаблонов.";

  enum FixtureSource {
    SNAPSHOT_FILE,
    JAXB_XSD,
    NO_FIXTURE
  }

  record Entry(
    String command,
    String typeKey,
    FixtureSource source,
    String fixtureRef,
    String allowedDiff) {
  }

  private AddGoldenFixtureMatrix() {
  }

  static List<Entry> allEntries() {
    return List.of(
      new Entry(
        "add-catalog",
        "Catalog",
        FixtureSource.SNAPSHOT_FILE,
        "snapshots/2.20/cf/empty-full-objects/Catalogs/Справочник1.xml",
        "Разрешено только пустое <ChildObjects/> по умолчанию; профиль Properties и namespace как в snapshot."),
      new Entry(
        CMD_ADD_MD_OBJECT,
        "Enum",
        FixtureSource.JAXB_XSD,
        "generated via NewEnumXml",
        JAXB_DIFF),
      new Entry(
        CMD_ADD_MD_OBJECT,
        "Constant",
        FixtureSource.JAXB_XSD,
        "generated via NewConstantXml",
        JAXB_DIFF),
      new Entry(
        CMD_ADD_MD_OBJECT,
        "Document",
        FixtureSource.JAXB_XSD,
        "generated via NewDocumentXml",
        JAXB_DIFF),
      new Entry(
        CMD_ADD_MD_OBJECT,
        "Report",
        FixtureSource.JAXB_XSD,
        "generated via NewReportXml",
        JAXB_DIFF),
      new Entry(
        CMD_ADD_MD_OBJECT,
        "DataProcessor",
        FixtureSource.JAXB_XSD,
        "generated via NewDataProcessorXml",
        JAXB_DIFF),
      new Entry(
        CMD_ADD_MD_OBJECT,
        "Task",
        FixtureSource.JAXB_XSD,
        "generated via NewTaskXml",
        JAXB_DIFF),
      new Entry(
        CMD_ADD_MD_OBJECT,
        "ChartOfAccounts",
        FixtureSource.JAXB_XSD,
        "generated via NewChartOfAccountsXml",
        JAXB_DIFF),
      new Entry(
        CMD_ADD_MD_OBJECT,
        "ChartOfCharacteristicTypes",
        FixtureSource.JAXB_XSD,
        "generated via NewChartOfCharacteristicTypesXml",
        JAXB_DIFF),
      new Entry(
        CMD_ADD_MD_OBJECT,
        "ChartOfCalculationTypes",
        FixtureSource.JAXB_XSD,
        "generated via NewChartOfCalculationTypesXml",
        JAXB_DIFF),
      new Entry(
        CMD_ADD_MD_OBJECT,
        "CommonModule",
        FixtureSource.JAXB_XSD,
        "generated via NewCommonModuleXml",
        JAXB_DIFF),
      new Entry(
        CMD_ADD_MD_OBJECT,
        "Subsystem",
        FixtureSource.JAXB_XSD,
        "generated via NewSubsystemXml",
        JAXB_DIFF),
      new Entry(
        CMD_ADD_MD_OBJECT,
        "SessionParameter",
        FixtureSource.JAXB_XSD,
        "generated via NewSessionParameterXml",
        JAXB_DIFF),
      new Entry(
        CMD_ADD_MD_OBJECT,
        "ExchangePlan",
        FixtureSource.JAXB_XSD,
        "generated via NewExchangePlanXml",
        JAXB_DIFF),
      new Entry(
        CMD_ADD_MD_OBJECT,
        "CommonAttribute",
        FixtureSource.JAXB_XSD,
        "generated via NewCommonAttributeXml",
        JAXB_DIFF),
      new Entry(
        CMD_ADD_MD_OBJECT,
        "CommonPicture",
        FixtureSource.JAXB_XSD,
        "generated via NewCommonPictureXml",
        JAXB_DIFF),
      new Entry(
        CMD_ADD_MD_OBJECT,
        "DocumentNumerator",
        FixtureSource.JAXB_XSD,
        "generated via NewDocumentNumeratorXml",
        JAXB_DIFF),
      new Entry(
        CMD_ADD_MD_OBJECT,
        "ExternalDataSource",
        FixtureSource.JAXB_XSD,
        "generated via NewExternalDataSourceXml",
        JAXB_DIFF),
      new Entry(
        CMD_ADD_MD_OBJECT,
        "Role",
        FixtureSource.JAXB_XSD,
        "generated via NewRoleXml + RoleRightsXmlWriter",
        "Имя и UUID детерминированы; Rights.xml генерируется программно."),
      new Entry(
        "external-artifact-add",
        "REPORT",
        FixtureSource.NO_FIXTURE,
        "нет fixture в workspace",
        "Пока только детерминизм + golden-format + JAXB-readability."),
      new Entry(
        "external-artifact-add",
        "DATA_PROCESSOR",
        FixtureSource.NO_FIXTURE,
        "нет fixture в workspace",
        "Пока только детерминизм + golden-format + JAXB-readability.")
    );
  }

  static List<Entry> withFixtures() {
    return allEntries().stream().filter(e -> e.source != FixtureSource.NO_FIXTURE).toList();
  }

  static List<Entry> withoutFixtures() {
    return allEntries().stream().filter(e -> e.source == FixtureSource.NO_FIXTURE).toList();
  }
}
