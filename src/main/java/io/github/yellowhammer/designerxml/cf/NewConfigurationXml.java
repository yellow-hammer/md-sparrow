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

import io.github.yellowhammer.designerxml.DesignerXml;
import io.github.yellowhammer.designerxml.SchemaVersion;
import io.github.yellowhammer.designerxml.WriteOptions;

import jakarta.xml.bind.JAXBElement;
import jakarta.xml.bind.JAXBException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Новый «пустой» корневой {@link CfLayout#CONFIGURATION_XML}: объектная модель JAXB по XSD
 * и запись через {@link DesignerXml#write} (префиксы 1С — {@link WriteOptions#defaults()}).
 */
public final class NewConfigurationXml {

  private NewConfigurationXml() {
  }

  /**
   * Создаёт {@code Configuration.xml} с пустым {@code ChildObjects} и нейтральными значениями свойств.
   *
   * @param targetCfRoot      каталог {@code src/cf}
   * @param configurationName имя конфигурации
   * @param synonymRu         синоним ru
   * @param vendor            поставщик; {@code null} — пустая строка
   * @param appVersion        версия; {@code null} или пусто — {@code 1.0.0}
   * @param schemaVersion     версия XSD / JAXB
   */
  public static void write(
    Path targetCfRoot,
    String configurationName,
    String synonymRu,
    String vendor,
    String appVersion,
    SchemaVersion schemaVersion) throws IOException, JAXBException {
    Objects.requireNonNull(targetCfRoot, "targetCfRoot");
    Objects.requireNonNull(configurationName, "configurationName");
    Objects.requireNonNull(synonymRu, "synonymRu");
    Objects.requireNonNull(schemaVersion, "schemaVersion");
    CatalogNameConstraints.check(configurationName);

    String vendorFin = vendor == null ? "" : vendor;
    String verFin = appVersion == null || appVersion.isEmpty() ? "1.0.0" : appVersion;

    Path out = targetCfRoot.resolve(CfLayout.CONFIGURATION_XML);
    Files.createDirectories(targetCfRoot);

    switch (schemaVersion) {
      case V2_20 -> writeV220(out, configurationName, synonymRu, vendorFin, verFin);
      case V2_21 -> writeV221(out, configurationName, synonymRu, vendorFin, verFin);
    }
  }

  /**
   * Минимальная выгрузка как у пустой конфигурации в конфигураторе: {@link CfLayout#CONFIGURATION_XML},
   * {@link CfLayout#LANGUAGES_DIR}/{@link CfLayout#RUSSIAN_LANGUAGE_NAME}.xml (без {@link CfLayout#CONFIG_DUMP_INFO_XML}).
   *
   * @param synonymRu {@code null} или пусто — в свойствах пустой {@code Synonym}
   * @param vendor    {@code null} — {@code ""}; версия {@code appVersion} {@code null} — {@code ""}
   */
  public static void writeConfiguratorEmptyTree(
    Path targetCfRoot,
    String configurationName,
    String synonymRu,
    String vendor,
    String appVersion,
    SchemaVersion schemaVersion) throws IOException, JAXBException {
    Objects.requireNonNull(targetCfRoot, "targetCfRoot");
    Objects.requireNonNull(configurationName, "configurationName");
    Objects.requireNonNull(schemaVersion, "schemaVersion");
    CatalogNameConstraints.check(configurationName);

    String vendorFin = vendor == null ? "" : vendor;
    String verFin = appVersion == null ? "" : appVersion;

    CfTreeDelete.deleteAllContents(targetCfRoot);
    Files.createDirectories(targetCfRoot);
    NewLanguageXml.writeRussian(targetCfRoot, schemaVersion);

    Path out = targetCfRoot.resolve(CfLayout.CONFIGURATION_XML);
    switch (schemaVersion) {
      case V2_20 -> writeConfiguratorV220(out, configurationName, synonymRu, vendorFin, verFin);
      case V2_21 -> writeConfiguratorV221(out, configurationName, synonymRu, vendorFin, verFin);
    }
  }

  private static void writeConfiguratorV220(
    Path out,
    String configurationName,
    String synonymRu,
    String vendor,
    String appVersion) throws IOException, JAXBException {
    var factory = new io.github.yellowhammer.designerxml.jaxb.v2_20.mdclasses.ObjectFactory();
    io.github.yellowhammer.designerxml.jaxb.v2_20.mdclasses.MetaDataObject mdo = factory.createMetaDataObject();
    mdo.setVersion(SchemaVersion.V2_20.metadataObjectVersionAttribute());
    String seed = "newConfiguration|V2_20|configurator|" + configurationName;

    io.github.yellowhammer.designerxml.jaxb.v2_20.mdclasses.Configuration cfg = factory.createConfiguration();
    cfg.setUuid(GoldenUuid.from(seed, "configuration.uuid"));
    cfg.setFormatVersion(SchemaVersion.V2_20.metadataObjectVersionAttribute());
    cfg.setInternalInfo(ConfigurationInternalInfoTemplates.newInternalInfoV220(seed));
    var ch = factory.createConfigurationChildObjects();
    ch.getLanguage().add(CfLayout.RUSSIAN_LANGUAGE_NAME);
    cfg.setChildObjects(ch);

    var p = fillPropertiesV220(factory, configurationName, "", vendor, appVersion);
    applyConfiguratorPresentationV220(p, synonymRu);
    cfg.setProperties(p);

    mdo.setConfiguration(cfg);
    JAXBElement<io.github.yellowhammer.designerxml.jaxb.v2_20.mdclasses.MetaDataObject> root =
      factory.createMetaDataObject(mdo);
    DesignerXml.write(out, root, SchemaVersion.V2_20, WriteOptions.defaults());
  }

  private static void writeConfiguratorV221(
    Path out,
    String configurationName,
    String synonymRu,
    String vendor,
    String appVersion) throws IOException, JAXBException {
    var factory = new io.github.yellowhammer.designerxml.jaxb.v2_21.mdclasses.ObjectFactory();
    io.github.yellowhammer.designerxml.jaxb.v2_21.mdclasses.MetaDataObject mdo = factory.createMetaDataObject();
    mdo.setVersion(SchemaVersion.V2_21.metadataObjectVersionAttribute());
    String seed = "newConfiguration|V2_21|configurator|" + configurationName;

    io.github.yellowhammer.designerxml.jaxb.v2_21.mdclasses.Configuration cfg = factory.createConfiguration();
    cfg.setUuid(GoldenUuid.from(seed, "configuration.uuid"));
    cfg.setFormatVersion(SchemaVersion.V2_21.metadataObjectVersionAttribute());
    cfg.setInternalInfo(ConfigurationInternalInfoTemplates.newInternalInfoV221(seed));
    var ch = factory.createConfigurationChildObjects();
    ch.getLanguage().add(CfLayout.RUSSIAN_LANGUAGE_NAME);
    cfg.setChildObjects(ch);

    var p = fillPropertiesV221(factory, configurationName, "", vendor, appVersion);
    applyConfiguratorPresentationV221(p, synonymRu);
    cfg.setProperties(p);

    mdo.setConfiguration(cfg);
    JAXBElement<io.github.yellowhammer.designerxml.jaxb.v2_21.mdclasses.MetaDataObject> root =
      factory.createMetaDataObject(mdo);
    DesignerXml.write(out, root, SchemaVersion.V2_21, WriteOptions.defaults());
  }

  private static void writeV220(
    Path out,
    String configurationName,
    String synonymRu,
    String vendor,
    String appVersion) throws IOException, JAXBException {
    var factory = new io.github.yellowhammer.designerxml.jaxb.v2_20.mdclasses.ObjectFactory();
    io.github.yellowhammer.designerxml.jaxb.v2_20.mdclasses.MetaDataObject mdo = factory.createMetaDataObject();
    mdo.setVersion(SchemaVersion.V2_20.metadataObjectVersionAttribute());
    String seed = "newConfiguration|V2_20|basic|" + configurationName;

    io.github.yellowhammer.designerxml.jaxb.v2_20.mdclasses.Configuration cfg = factory.createConfiguration();
    cfg.setUuid(GoldenUuid.from(seed, "configuration.uuid"));
    cfg.setFormatVersion(SchemaVersion.V2_20.metadataObjectVersionAttribute());
    cfg.setChildObjects(factory.createConfigurationChildObjects());
    cfg.setProperties(fillPropertiesV220(factory, configurationName, synonymRu, vendor, appVersion));

    mdo.setConfiguration(cfg);
    JAXBElement<io.github.yellowhammer.designerxml.jaxb.v2_20.mdclasses.MetaDataObject> root =
      factory.createMetaDataObject(mdo);
    DesignerXml.write(out, root, SchemaVersion.V2_20, WriteOptions.defaults());
  }

  private static void writeV221(
    Path out,
    String configurationName,
    String synonymRu,
    String vendor,
    String appVersion) throws IOException, JAXBException {
    var factory = new io.github.yellowhammer.designerxml.jaxb.v2_21.mdclasses.ObjectFactory();
    io.github.yellowhammer.designerxml.jaxb.v2_21.mdclasses.MetaDataObject mdo = factory.createMetaDataObject();
    mdo.setVersion(SchemaVersion.V2_21.metadataObjectVersionAttribute());
    String seed = "newConfiguration|V2_21|basic|" + configurationName;

    io.github.yellowhammer.designerxml.jaxb.v2_21.mdclasses.Configuration cfg = factory.createConfiguration();
    cfg.setUuid(GoldenUuid.from(seed, "configuration.uuid"));
    cfg.setFormatVersion(SchemaVersion.V2_21.metadataObjectVersionAttribute());
    cfg.setChildObjects(factory.createConfigurationChildObjects());
    cfg.setProperties(fillPropertiesV221(factory, configurationName, synonymRu, vendor, appVersion));

    mdo.setConfiguration(cfg);
    JAXBElement<io.github.yellowhammer.designerxml.jaxb.v2_21.mdclasses.MetaDataObject> root =
      factory.createMetaDataObject(mdo);
    DesignerXml.write(out, root, SchemaVersion.V2_21, WriteOptions.defaults());
  }

  private static io.github.yellowhammer.designerxml.jaxb.v2_20.mdclasses.ConfigurationProperties fillPropertiesV220(
    io.github.yellowhammer.designerxml.jaxb.v2_20.mdclasses.ObjectFactory factory,
    String configurationName,
    String synonymRu,
    String vendor,
    String appVersion) {
    var coreOf = new io.github.yellowhammer.designerxml.jaxb.v2_20.v8_1_data_core.ObjectFactory();
    var xrOf = new io.github.yellowhammer.designerxml.jaxb.v2_20.v8_3_xcf_readable.ObjectFactory();
    io.github.yellowhammer.designerxml.jaxb.v2_20.mdclasses.ConfigurationProperties p =
      factory.createConfigurationProperties();
    p.setName(configurationName);
    p.setSynonym(localStringV220(synonymRu));
    p.setComment("");
    p.setNamePrefix("");
    p.setConfigurationExtensionCompatibilityMode(
      io.github.yellowhammer.designerxml.jaxb.v2_20.v8_3_xcf_enums.CompatibilityMode.VERSION_8_3_12);
    p.setDefaultRunMode(
      io.github.yellowhammer.designerxml.jaxb.v2_20.v8_2_managed_application_core.ClientRunMode.MANAGED_APPLICATION);
    io.github.yellowhammer.designerxml.jaxb.v2_20.v8_1_data_core.FixedArray purposes = coreOf.createFixedArray();
    purposes.getValue().add(
      io.github.yellowhammer.designerxml.jaxb.v2_20.v8_2_managed_application_core.ApplicationUsePurpose
        .PLATFORM_APPLICATION);
    p.setUsePurposes(purposes);
    p.setScriptVariant(io.github.yellowhammer.designerxml.jaxb.v2_20.v8_3_xcf_enums.ScriptVariant.RUSSIAN);
    p.setDefaultRoles(xrOf.createMDListType());
    p.setVendor(vendor);
    p.setVersion(appVersion);
    p.setUpdateCatalogAddress("");
    p.setParentConfigurations("");
    p.setManagedApplicationModule("");
    p.setSessionModule("");
    p.setExternalConnectionModule("");
    p.setIncludeHelpInContents(Boolean.FALSE);
    p.setHelp("");
    p.setUseManagedFormInOrdinaryApplication(Boolean.TRUE);
    p.setUseOrdinaryFormInManagedApplication(Boolean.FALSE);
    p.setAdditionalFullTextSearchDictionaries(xrOf.createMDListType());
    p.setCommonSettingsStorage("");
    p.setReportsUserSettingsStorage("");
    p.setReportsVariantsStorage("");
    p.setFormDataSettingsStorage("");
    p.setDynamicListsUserSettingsStorage("");
    p.setURLExternalDataStorage("");
    p.setContent(xrOf.createMDListType());
    p.setDefaultReportForm("");
    p.setDefaultReportVariantForm("");
    p.setDefaultReportSettingsForm("");
    p.setDefaultDynamicListSettingsForm("");
    p.setDefaultSearchForm("");
    p.setDefaultDataHistoryChangeHistoryForm("");
    p.setDefaultDataHistoryVersionDataForm("");
    p.setDefaultDataHistoryVersionDifferencesForm("");
    p.setDefaultCollaborationSystemUsersChoiceForm("");
    p.setDefaultReportAppearanceTemplate("");
    p.setRequiredMobileApplicationPermissions(
      new io.github.yellowhammer.designerxml.jaxb.v2_20.v8_2_managed_application_core.RequiredPermissions());
    p.setUsedMobileApplicationFunctionalities(
      new io.github.yellowhammer.designerxml.jaxb.v2_20.v8_2_managed_application_core.UsedFunctionality());
    p.setMobileApplicationURLs(coreOf.createFixedArray());
    p.setAllowedIncomingShareRequestTypes(coreOf.createFixedArray());
    p.setCommandInterface("");
    p.setMainSectionCommandInterface("");
    p.setMainSectionPicture("");
    p.setClientApplicationInterface("");
    p.setMainClientApplicationWindowMode(
      io.github.yellowhammer.designerxml.jaxb.v2_20.v8_1_data_core.MainClientApplicationWindowMode.NORMAL);
    p.setDefaultInterface("");
    p.setDefaultStyle("");
    p.setDefaultLanguage("");
    p.setBriefInformation(localStringV220(synonymRu));
    p.setDetailedInformation(localStringV220(synonymRu));
    p.setLogo("");
    p.setSplash("");
    p.setCopyright(localStringV220(""));
    p.setVendorInformationAddress(localStringV220(""));
    p.setConfigurationInformationAddress(localStringV220(""));
    p.setDataLockControlMode(
      io.github.yellowhammer.designerxml.jaxb.v2_20.v8_3_xcf_enums.DefaultDataLockControlMode.MANAGED);
    p.setBinaryDataStorageMode(
      io.github.yellowhammer.designerxml.jaxb.v2_20.v8_3_xcf_enums.BinaryDataStorageMode.DONT_USE);
    p.setBinaryDataBlockStorageUseMode(
      io.github.yellowhammer.designerxml.jaxb.v2_20.v8_3_xcf_enums.BinaryDataBlockStorageUseMode.DONT_USE);
    p.setObjectAutonumerationMode(
      io.github.yellowhammer.designerxml.jaxb.v2_20.v8_3_xcf_enums.ObjectAutonumerationMode.NOT_AUTO_FREE);
    p.setModalityUseMode(
      io.github.yellowhammer.designerxml.jaxb.v2_20.v8_3_xcf_enums.ModalityUseMode.USE_WITH_WARNINGS);
    p.setSynchronousPlatformExtensionAndAddInCallUseMode(
      io.github.yellowhammer.designerxml.jaxb.v2_20.v8_3_xcf_enums.SynchronousPlatformExtensionAndAddInCallUseMode
        .USE);
    p.setInterfaceCompatibilityMode(
      io.github.yellowhammer.designerxml.jaxb.v2_20.v8_3_xcf_enums.InterfaceCompatibilityMode.TAXI_ENABLE_VERSION_8_2);
    p.setCompatibilityMode(
      io.github.yellowhammer.designerxml.jaxb.v2_20.v8_3_xcf_enums.CompatibilityMode.VERSION_8_3_12);
    p.setStandaloneConfigurationRestrictionRoles(xrOf.createMDListType());
    p.setDatabaseTablespacesUseMode(
      io.github.yellowhammer.designerxml.jaxb.v2_20.v8_3_xcf_enums.DatabaseTablespacesUseMode.DONT_USE);
    return p;
  }

  private static io.github.yellowhammer.designerxml.jaxb.v2_20.v8_1_data_core.LocalStringType localStringV220(
    String ruContent) {
    var lst = new io.github.yellowhammer.designerxml.jaxb.v2_20.v8_1_data_core.LocalStringType();
    var item = new io.github.yellowhammer.designerxml.jaxb.v2_20.v8_1_data_core.LocalStringItemType();
    item.setLang("ru");
    item.setContent(ruContent == null ? "" : ruContent);
    lst.getItem().add(item);
    return lst;
  }

  private static io.github.yellowhammer.designerxml.jaxb.v2_21.mdclasses.ConfigurationProperties fillPropertiesV221(
    io.github.yellowhammer.designerxml.jaxb.v2_21.mdclasses.ObjectFactory factory,
    String configurationName,
    String synonymRu,
    String vendor,
    String appVersion) {
    var coreOf = new io.github.yellowhammer.designerxml.jaxb.v2_21.v8_1_data_core.ObjectFactory();
    var xrOf = new io.github.yellowhammer.designerxml.jaxb.v2_21.v8_3_xcf_readable.ObjectFactory();
    io.github.yellowhammer.designerxml.jaxb.v2_21.mdclasses.ConfigurationProperties p =
      factory.createConfigurationProperties();
    p.setName(configurationName);
    p.setSynonym(localStringV221(synonymRu));
    p.setComment("");
    p.setNamePrefix("");
    p.setConfigurationExtensionCompatibilityMode(
      io.github.yellowhammer.designerxml.jaxb.v2_21.v8_3_xcf_enums.CompatibilityMode.VERSION_8_3_12);
    p.setDefaultRunMode(
      io.github.yellowhammer.designerxml.jaxb.v2_21.v8_2_managed_application_core.ClientRunMode.MANAGED_APPLICATION);
    io.github.yellowhammer.designerxml.jaxb.v2_21.v8_1_data_core.FixedArray purposes = coreOf.createFixedArray();
    purposes.getValue().add(
      io.github.yellowhammer.designerxml.jaxb.v2_21.v8_2_managed_application_core.ApplicationUsePurpose
        .PLATFORM_APPLICATION);
    p.setUsePurposes(purposes);
    p.setScriptVariant(io.github.yellowhammer.designerxml.jaxb.v2_21.v8_3_xcf_enums.ScriptVariant.RUSSIAN);
    p.setDefaultRoles(xrOf.createMDListType());
    p.setVendor(vendor);
    p.setVersion(appVersion);
    p.setUpdateCatalogAddress("");
    p.setParentConfigurations("");
    p.setManagedApplicationModule("");
    p.setSessionModule("");
    p.setExternalConnectionModule("");
    p.setIncludeHelpInContents(Boolean.FALSE);
    p.setHelp("");
    p.setUseManagedFormInOrdinaryApplication(Boolean.TRUE);
    p.setUseOrdinaryFormInManagedApplication(Boolean.FALSE);
    p.setAdditionalFullTextSearchDictionaries(xrOf.createMDListType());
    p.setCommonSettingsStorage("");
    p.setReportsUserSettingsStorage("");
    p.setReportsVariantsStorage("");
    p.setFormDataSettingsStorage("");
    p.setDynamicListsUserSettingsStorage("");
    p.setURLExternalDataStorage("");
    p.setContent(xrOf.createMDListType());
    p.setDefaultReportForm("");
    p.setDefaultReportVariantForm("");
    p.setDefaultReportSettingsForm("");
    p.setDefaultDynamicListSettingsForm("");
    p.setDefaultSearchForm("");
    p.setDefaultDataHistoryChangeHistoryForm("");
    p.setDefaultDataHistoryVersionDataForm("");
    p.setDefaultDataHistoryVersionDifferencesForm("");
    p.setDefaultCollaborationSystemUsersChoiceForm("");
    p.setAuxiliaryReportForm("");
    p.setAuxiliaryReportVariantForm("");
    p.setAuxiliaryReportSettingsForm("");
    p.setAuxiliaryDynamicListSettingsForm("");
    p.setAuxiliaryDataHistoryChangeHistoryForm("");
    p.setAuxiliaryDataHistoryVersionDataForm("");
    p.setAuxiliaryDataHistoryVersionDifferencesForm("");
    p.setAuxiliaryCollaborationSystemUsersChoiceForm("");
    p.setDefaultReportAppearanceTemplate("");
    p.setRequiredMobileApplicationPermissions(
      new io.github.yellowhammer.designerxml.jaxb.v2_21.v8_2_managed_application_core.RequiredPermissions());
    p.setUsedMobileApplicationFunctionalities(
      new io.github.yellowhammer.designerxml.jaxb.v2_21.v8_2_managed_application_core.UsedFunctionality());
    p.setMobileApplicationURLs(coreOf.createFixedArray());
    p.setAllowedIncomingShareRequestTypes(coreOf.createFixedArray());
    p.setCommandInterface("");
    p.setMainSectionCommandInterface("");
    p.setMainSectionPicture("");
    p.setClientApplicationInterface("");
    p.setClientApplicationTheme(
      io.github.yellowhammer.designerxml.jaxb.v2_21.v8_1_data_enterprise.ClientApplicationTheme.AUTO);
    p.setMainClientApplicationWindowMode(
      io.github.yellowhammer.designerxml.jaxb.v2_21.v8_1_data_core.MainClientApplicationWindowMode.NORMAL);
    p.setDefaultInterface("");
    p.setDefaultStyle("");
    p.setDefaultLanguage("");
    p.setBriefInformation(localStringV221(synonymRu));
    p.setDetailedInformation(localStringV221(synonymRu));
    p.setLogo("");
    p.setSplash("");
    p.setCopyright(localStringV221(""));
    p.setVendorInformationAddress(localStringV221(""));
    p.setConfigurationInformationAddress(localStringV221(""));
    p.setDataLockControlMode(
      io.github.yellowhammer.designerxml.jaxb.v2_21.v8_3_xcf_enums.DefaultDataLockControlMode.MANAGED);
    p.setBinaryDataStorageMode(
      io.github.yellowhammer.designerxml.jaxb.v2_21.v8_3_xcf_enums.BinaryDataStorageMode.DONT_USE);
    p.setBinaryDataBlockStorageUseMode(
      io.github.yellowhammer.designerxml.jaxb.v2_21.v8_3_xcf_enums.BinaryDataBlockStorageUseMode.DONT_USE);
    p.setObjectAutonumerationMode(
      io.github.yellowhammer.designerxml.jaxb.v2_21.v8_3_xcf_enums.ObjectAutonumerationMode.NOT_AUTO_FREE);
    p.setModalityUseMode(
      io.github.yellowhammer.designerxml.jaxb.v2_21.v8_3_xcf_enums.ModalityUseMode.USE_WITH_WARNINGS);
    p.setSynchronousPlatformExtensionAndAddInCallUseMode(
      io.github.yellowhammer.designerxml.jaxb.v2_21.v8_3_xcf_enums.SynchronousPlatformExtensionAndAddInCallUseMode
        .USE);
    p.setInterfaceCompatibilityMode(
      io.github.yellowhammer.designerxml.jaxb.v2_21.v8_3_xcf_enums.InterfaceCompatibilityMode.TAXI_ENABLE_VERSION_8_2);
    p.setVersion85InterfaceMigrationMode(
      io.github.yellowhammer.designerxml.jaxb.v2_21.v8_3_xcf_enums.Version85InterfaceMigrationMode.DONT_USE);
    p.setCompatibilityMode(
      io.github.yellowhammer.designerxml.jaxb.v2_21.v8_3_xcf_enums.CompatibilityMode.VERSION_8_3_12);
    p.setStandaloneConfigurationRestrictionRoles(xrOf.createMDListType());
    p.setDatabaseTablespacesUseMode(
      io.github.yellowhammer.designerxml.jaxb.v2_21.v8_3_xcf_enums.DatabaseTablespacesUseMode.DONT_USE);
    p.setCaption(localStringV221(synonymRu));
    p.setShortCaption(localStringV221(synonymRu));
    p.setMainClientApplicationWindowInterfaceVariant(
      io.github.yellowhammer.designerxml.jaxb.v2_21.v8_1_data_enterprise.MainClientApplicationWindowInterfaceVariant
        .NAVIGATION_LEFT);
    p.setClientApplicationWindowsOpenVariant(
      io.github.yellowhammer.designerxml.jaxb.v2_21.v8_1_data_enterprise.ClientApplicationWindowsOpenVariant
        .OPEN_DATA_IN_TABS);
    return p;
  }

  private static io.github.yellowhammer.designerxml.jaxb.v2_21.v8_1_data_core.LocalStringType localStringV221(
    String ruContent) {
    var lst = new io.github.yellowhammer.designerxml.jaxb.v2_21.v8_1_data_core.LocalStringType();
    var item = new io.github.yellowhammer.designerxml.jaxb.v2_21.v8_1_data_core.LocalStringItemType();
    item.setLang("ru");
    item.setContent(ruContent == null ? "" : ruContent);
    lst.getItem().add(item);
    return lst;
  }

  private static void applyConfiguratorPresentationV220(
    io.github.yellowhammer.designerxml.jaxb.v2_20.mdclasses.ConfigurationProperties p,
    String synonymRu) {
    if (synonymRu == null || synonymRu.isEmpty()) {
      p.setSynonym(new io.github.yellowhammer.designerxml.jaxb.v2_20.v8_1_data_core.LocalStringType());
    } else {
      p.setSynonym(localStringV220(synonymRu));
    }
    p.setUseManagedFormInOrdinaryApplication(Boolean.FALSE);
    p.setModalityUseMode(io.github.yellowhammer.designerxml.jaxb.v2_20.v8_3_xcf_enums.ModalityUseMode.DONT_USE);
    p.setSynchronousPlatformExtensionAndAddInCallUseMode(
      io.github.yellowhammer.designerxml.jaxb.v2_20.v8_3_xcf_enums.SynchronousPlatformExtensionAndAddInCallUseMode
        .DONT_USE);
    p.setInterfaceCompatibilityMode(
      io.github.yellowhammer.designerxml.jaxb.v2_20.v8_3_xcf_enums.InterfaceCompatibilityMode.TAXI);
    p.setBriefInformation(new io.github.yellowhammer.designerxml.jaxb.v2_20.v8_1_data_core.LocalStringType());
    p.setDetailedInformation(new io.github.yellowhammer.designerxml.jaxb.v2_20.v8_1_data_core.LocalStringType());
    p.setCopyright(new io.github.yellowhammer.designerxml.jaxb.v2_20.v8_1_data_core.LocalStringType());
    p.setVendorInformationAddress(new io.github.yellowhammer.designerxml.jaxb.v2_20.v8_1_data_core.LocalStringType());
    p.setConfigurationInformationAddress(
      new io.github.yellowhammer.designerxml.jaxb.v2_20.v8_1_data_core.LocalStringType());
    p.setDefaultLanguage("Language." + CfLayout.RUSSIAN_LANGUAGE_NAME);
  }

  private static void applyConfiguratorPresentationV221(
    io.github.yellowhammer.designerxml.jaxb.v2_21.mdclasses.ConfigurationProperties p,
    String synonymRu) {
    if (synonymRu == null || synonymRu.isEmpty()) {
      p.setSynonym(new io.github.yellowhammer.designerxml.jaxb.v2_21.v8_1_data_core.LocalStringType());
    } else {
      p.setSynonym(localStringV221(synonymRu));
    }
    p.setUseManagedFormInOrdinaryApplication(Boolean.FALSE);
    p.setModalityUseMode(io.github.yellowhammer.designerxml.jaxb.v2_21.v8_3_xcf_enums.ModalityUseMode.DONT_USE);
    p.setSynchronousPlatformExtensionAndAddInCallUseMode(
      io.github.yellowhammer.designerxml.jaxb.v2_21.v8_3_xcf_enums.SynchronousPlatformExtensionAndAddInCallUseMode
        .DONT_USE);
    p.setInterfaceCompatibilityMode(
      io.github.yellowhammer.designerxml.jaxb.v2_21.v8_3_xcf_enums.InterfaceCompatibilityMode.TAXI);
    var empty = new io.github.yellowhammer.designerxml.jaxb.v2_21.v8_1_data_core.LocalStringType();
    p.setBriefInformation(empty);
    p.setDetailedInformation(new io.github.yellowhammer.designerxml.jaxb.v2_21.v8_1_data_core.LocalStringType());
    p.setCopyright(new io.github.yellowhammer.designerxml.jaxb.v2_21.v8_1_data_core.LocalStringType());
    p.setVendorInformationAddress(new io.github.yellowhammer.designerxml.jaxb.v2_21.v8_1_data_core.LocalStringType());
    p.setConfigurationInformationAddress(
      new io.github.yellowhammer.designerxml.jaxb.v2_21.v8_1_data_core.LocalStringType());
    p.setCaption(new io.github.yellowhammer.designerxml.jaxb.v2_21.v8_1_data_core.LocalStringType());
    p.setShortCaption(new io.github.yellowhammer.designerxml.jaxb.v2_21.v8_1_data_core.LocalStringType());
    p.setDefaultLanguage("Language." + CfLayout.RUSSIAN_LANGUAGE_NAME);
    p.setClientApplicationWindowsOpenVariant(
      io.github.yellowhammer.designerxml.jaxb.v2_21.v8_1_data_enterprise.ClientApplicationWindowsOpenVariant
        .OPEN_DATA_IN_DIALOGS);
    p.setDefaultConstantsForm("");
  }
}
