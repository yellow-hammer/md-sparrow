/*
 * This file is a part of md-sparrow.
 *
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package io.github.yellowhammer.designerxml.cf;

import io.github.yellowhammer.designerxml.DesignerXml;
import io.github.yellowhammer.designerxml.SchemaVersion;
import io.github.yellowhammer.designerxml.WriteOptions;
import jakarta.xml.bind.JAXBElement;
import jakarta.xml.bind.JAXBException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import javax.xml.stream.XMLStreamException;

public final class ConfigurationPropertiesEdit {

  private ConfigurationPropertiesEdit() {
  }

  public static ConfigurationPropertiesDto read(Path configurationXml, SchemaVersion schemaVersion)
    throws JAXBException, IOException {
    return switch (schemaVersion) {
      case V2_20 -> readV20(configurationXml);
      case V2_21 -> readV21(configurationXml);
    };
  }

  public static void write(Path configurationXml, SchemaVersion schemaVersion, ConfigurationPropertiesDto dto)
    throws JAXBException, IOException {
    switch (schemaVersion) {
      case V2_20 -> writeV20(configurationXml, dto);
      case V2_21 -> writeV21(configurationXml, dto);
    }
  }

  private static ConfigurationPropertiesDto readV20(Path configurationXml) throws JAXBException, IOException {
    Object root = DesignerXml.read(configurationXml, SchemaVersion.V2_20);
    if (!(root instanceof JAXBElement<?> je)
      || !(je.getValue() instanceof io.github.yellowhammer.designerxml.jaxb.v2_20.mdclasses.MetaDataObject mdo)) {
      throw new IllegalArgumentException("expected JAXBElement<MetaDataObject>");
    }
    if (mdo == null || mdo.getConfiguration() == null || mdo.getConfiguration().getProperties() == null) {
      throw new IllegalArgumentException("unsupported MetaDataObject for configuration-properties");
    }
    var p = mdo.getConfiguration().getProperties();
    var out = new ConfigurationPropertiesDto();
    out.name = nvl(p.getName());
    out.synonymRu = LocalStringSync.firstRuV20(p.getSynonym());
    out.comment = nvl(p.getComment());
    out.defaultRunMode = p.getDefaultRunMode() == null ? "" : p.getDefaultRunMode().name();
    out.usePurposes = enumListToNamesV20(p.getUsePurposes());
    out.scriptVariant = p.getScriptVariant() == null ? "" : p.getScriptVariant().name();
    out.defaultRoles = p.getDefaultRoles() == null ? new ArrayList<>() : MdListTypeRefs.readItemTexts(p.getDefaultRoles().getItem());
    out.managedApplicationModule = nvl(p.getManagedApplicationModule());
    out.sessionModule = nvl(p.getSessionModule());
    out.externalConnectionModule = nvl(p.getExternalConnectionModule());
    out.briefInformationRu = LocalStringSync.firstRuV20(p.getBriefInformation());
    out.detailedInformationRu = LocalStringSync.firstRuV20(p.getDetailedInformation());
    out.copyrightRu = LocalStringSync.firstRuV20(p.getCopyright());
    out.vendorInformationAddressRu = LocalStringSync.firstRuV20(p.getVendorInformationAddress());
    out.configurationInformationAddressRu = LocalStringSync.firstRuV20(p.getConfigurationInformationAddress());
    out.vendor = nvl(p.getVendor());
    out.version = nvl(p.getVersion());
    out.updateCatalogAddress = nvl(p.getUpdateCatalogAddress());
    out.dataLockControlMode = p.getDataLockControlMode() == null ? "" : p.getDataLockControlMode().name();
    out.objectAutonumerationMode = p.getObjectAutonumerationMode() == null ? "" : p.getObjectAutonumerationMode().name();
    out.modalityUseMode = p.getModalityUseMode() == null ? "" : p.getModalityUseMode().name();
    out.synchronousPlatformExtensionAndAddInCallUseMode =
      p.getSynchronousPlatformExtensionAndAddInCallUseMode() == null
        ? ""
        : p.getSynchronousPlatformExtensionAndAddInCallUseMode().name();
    out.interfaceCompatibilityMode =
      p.getInterfaceCompatibilityMode() == null ? "" : p.getInterfaceCompatibilityMode().name();
    out.compatibilityMode = p.getCompatibilityMode() == null ? "" : p.getCompatibilityMode().name();
    return out;
  }

  private static ConfigurationPropertiesDto readV21(Path configurationXml) throws JAXBException, IOException {
    Object root = DesignerXml.read(configurationXml, SchemaVersion.V2_21);
    if (!(root instanceof JAXBElement<?> je)
      || !(je.getValue() instanceof io.github.yellowhammer.designerxml.jaxb.v2_21.mdclasses.MetaDataObject mdo)) {
      throw new IllegalArgumentException("expected JAXBElement<MetaDataObject>");
    }
    if (mdo == null || mdo.getConfiguration() == null || mdo.getConfiguration().getProperties() == null) {
      throw new IllegalArgumentException("unsupported MetaDataObject for configuration-properties");
    }
    var p = mdo.getConfiguration().getProperties();
    var out = new ConfigurationPropertiesDto();
    out.name = nvl(p.getName());
    out.synonymRu = LocalStringSync.firstRuV21(p.getSynonym());
    out.comment = nvl(p.getComment());
    out.defaultRunMode = p.getDefaultRunMode() == null ? "" : p.getDefaultRunMode().name();
    out.usePurposes = enumListToNamesV21(p.getUsePurposes());
    out.scriptVariant = p.getScriptVariant() == null ? "" : p.getScriptVariant().name();
    out.defaultRoles = p.getDefaultRoles() == null ? new ArrayList<>() : MdListTypeRefs.readItemTexts(p.getDefaultRoles().getItem());
    out.managedApplicationModule = nvl(p.getManagedApplicationModule());
    out.sessionModule = nvl(p.getSessionModule());
    out.externalConnectionModule = nvl(p.getExternalConnectionModule());
    out.briefInformationRu = LocalStringSync.firstRuV21(p.getBriefInformation());
    out.detailedInformationRu = LocalStringSync.firstRuV21(p.getDetailedInformation());
    out.copyrightRu = LocalStringSync.firstRuV21(p.getCopyright());
    out.vendorInformationAddressRu = LocalStringSync.firstRuV21(p.getVendorInformationAddress());
    out.configurationInformationAddressRu = LocalStringSync.firstRuV21(p.getConfigurationInformationAddress());
    out.vendor = nvl(p.getVendor());
    out.version = nvl(p.getVersion());
    out.updateCatalogAddress = nvl(p.getUpdateCatalogAddress());
    out.dataLockControlMode = p.getDataLockControlMode() == null ? "" : p.getDataLockControlMode().name();
    out.objectAutonumerationMode = p.getObjectAutonumerationMode() == null ? "" : p.getObjectAutonumerationMode().name();
    out.modalityUseMode = p.getModalityUseMode() == null ? "" : p.getModalityUseMode().name();
    out.synchronousPlatformExtensionAndAddInCallUseMode =
      p.getSynchronousPlatformExtensionAndAddInCallUseMode() == null
        ? ""
        : p.getSynchronousPlatformExtensionAndAddInCallUseMode().name();
    out.interfaceCompatibilityMode =
      p.getInterfaceCompatibilityMode() == null ? "" : p.getInterfaceCompatibilityMode().name();
    out.compatibilityMode = p.getCompatibilityMode() == null ? "" : p.getCompatibilityMode().name();
    return out;
  }

  private static void writeV20(Path configurationXml, ConfigurationPropertiesDto dto)
    throws JAXBException, IOException {
    ConfigurationPropertiesDto baseline = readV20(configurationXml);
    ConfigurationPropertiesDto incoming = normalizeIncoming(dto, baseline);
    if (equalsDto(baseline, incoming)) {
      return;
    }
    String originalXml = Files.readString(configurationXml, StandardCharsets.UTF_8);
    Object root = DesignerXml.read(configurationXml, SchemaVersion.V2_20);
    if (!(root instanceof JAXBElement<?> je)
      || !(je.getValue() instanceof io.github.yellowhammer.designerxml.jaxb.v2_20.mdclasses.MetaDataObject mdo)) {
      throw new IllegalArgumentException("expected JAXBElement<MetaDataObject>");
    }
    if (mdo == null || mdo.getConfiguration() == null || mdo.getConfiguration().getProperties() == null) {
      throw new IllegalArgumentException("unsupported MetaDataObject for configuration-properties");
    }
    var p = mdo.getConfiguration().getProperties();
    p.setName(nvl(incoming.name));
    LocalStringSync.setOrPutRuV20(p.getSynonym(), nvl(incoming.synonymRu));
    p.setComment(nvl(incoming.comment));
    p.setDefaultRunMode(enumOrKeepV20DefaultRunMode(incoming.defaultRunMode, p.getDefaultRunMode()));
    applyUsePurposesV20(p, incoming.usePurposes);
    p.setScriptVariant(enumOrKeepV20ScriptVariant(incoming.scriptVariant, p.getScriptVariant()));
    if (p.getDefaultRoles() != null) {
      MdListTypeRefs.replaceItemsV20(p.getDefaultRoles(), safeTrimmedList(incoming.defaultRoles));
    }
    p.setManagedApplicationModule(nvl(incoming.managedApplicationModule));
    p.setSessionModule(nvl(incoming.sessionModule));
    p.setExternalConnectionModule(nvl(incoming.externalConnectionModule));
    LocalStringSync.setOrPutRuV20(p.getBriefInformation(), nvl(incoming.briefInformationRu));
    LocalStringSync.setOrPutRuV20(p.getDetailedInformation(), nvl(incoming.detailedInformationRu));
    LocalStringSync.setOrPutRuV20(p.getCopyright(), nvl(incoming.copyrightRu));
    LocalStringSync.setOrPutRuV20(p.getVendorInformationAddress(), nvl(incoming.vendorInformationAddressRu));
    LocalStringSync.setOrPutRuV20(p.getConfigurationInformationAddress(), nvl(incoming.configurationInformationAddressRu));
    p.setVendor(nvl(incoming.vendor));
    p.setVersion(nvl(incoming.version));
    p.setUpdateCatalogAddress(nvl(incoming.updateCatalogAddress));
    p.setDataLockControlMode(enumOrKeepV20DataLock(incoming.dataLockControlMode, p.getDataLockControlMode()));
    p.setObjectAutonumerationMode(enumOrKeepV20AutoNum(incoming.objectAutonumerationMode, p.getObjectAutonumerationMode()));
    p.setModalityUseMode(enumOrKeepV20Modality(incoming.modalityUseMode, p.getModalityUseMode()));
    p.setSynchronousPlatformExtensionAndAddInCallUseMode(
      enumOrKeepV20SyncMode(incoming.synchronousPlatformExtensionAndAddInCallUseMode,
        p.getSynchronousPlatformExtensionAndAddInCallUseMode())
    );
    p.setInterfaceCompatibilityMode(
      enumOrKeepV20InterfaceCompatibility(incoming.interfaceCompatibilityMode, p.getInterfaceCompatibilityMode())
    );
    p.setCompatibilityMode(enumOrKeepV20Compatibility(incoming.compatibilityMode, p.getCompatibilityMode()));
    byte[] patched = tryGranularWrite(originalXml, root, SchemaVersion.V2_20, baseline, incoming)
      .orElseThrow(() -> new IllegalStateException(
        "Не удалось применить изменения точечно. Полная пересборка XML через JAXB предотвращена."
      ));
    Files.write(configurationXml, patched);
  }

  private static void writeV21(Path configurationXml, ConfigurationPropertiesDto dto)
    throws JAXBException, IOException {
    ConfigurationPropertiesDto baseline = readV21(configurationXml);
    ConfigurationPropertiesDto incoming = normalizeIncoming(dto, baseline);
    if (equalsDto(baseline, incoming)) {
      return;
    }
    String originalXml = Files.readString(configurationXml, StandardCharsets.UTF_8);
    Object root = DesignerXml.read(configurationXml, SchemaVersion.V2_21);
    if (!(root instanceof JAXBElement<?> je)
      || !(je.getValue() instanceof io.github.yellowhammer.designerxml.jaxb.v2_21.mdclasses.MetaDataObject mdo)) {
      throw new IllegalArgumentException("expected JAXBElement<MetaDataObject>");
    }
    if (mdo == null || mdo.getConfiguration() == null || mdo.getConfiguration().getProperties() == null) {
      throw new IllegalArgumentException("unsupported MetaDataObject for configuration-properties");
    }
    var p = mdo.getConfiguration().getProperties();
    p.setName(nvl(incoming.name));
    LocalStringSync.setOrPutRuV21(p.getSynonym(), nvl(incoming.synonymRu));
    p.setComment(nvl(incoming.comment));
    p.setDefaultRunMode(enumOrKeepV21DefaultRunMode(incoming.defaultRunMode, p.getDefaultRunMode()));
    applyUsePurposesV21(p, incoming.usePurposes);
    p.setScriptVariant(enumOrKeepV21ScriptVariant(incoming.scriptVariant, p.getScriptVariant()));
    if (p.getDefaultRoles() != null) {
      MdListTypeRefs.replaceItemsV21(p.getDefaultRoles(), safeTrimmedList(incoming.defaultRoles));
    }
    p.setManagedApplicationModule(nvl(incoming.managedApplicationModule));
    p.setSessionModule(nvl(incoming.sessionModule));
    p.setExternalConnectionModule(nvl(incoming.externalConnectionModule));
    LocalStringSync.setOrPutRuV21(p.getBriefInformation(), nvl(incoming.briefInformationRu));
    LocalStringSync.setOrPutRuV21(p.getDetailedInformation(), nvl(incoming.detailedInformationRu));
    LocalStringSync.setOrPutRuV21(p.getCopyright(), nvl(incoming.copyrightRu));
    LocalStringSync.setOrPutRuV21(p.getVendorInformationAddress(), nvl(incoming.vendorInformationAddressRu));
    LocalStringSync.setOrPutRuV21(p.getConfigurationInformationAddress(), nvl(incoming.configurationInformationAddressRu));
    p.setVendor(nvl(incoming.vendor));
    p.setVersion(nvl(incoming.version));
    p.setUpdateCatalogAddress(nvl(incoming.updateCatalogAddress));
    p.setDataLockControlMode(enumOrKeepV21DataLock(incoming.dataLockControlMode, p.getDataLockControlMode()));
    p.setObjectAutonumerationMode(enumOrKeepV21AutoNum(incoming.objectAutonumerationMode, p.getObjectAutonumerationMode()));
    p.setModalityUseMode(enumOrKeepV21Modality(incoming.modalityUseMode, p.getModalityUseMode()));
    p.setSynchronousPlatformExtensionAndAddInCallUseMode(
      enumOrKeepV21SyncMode(incoming.synchronousPlatformExtensionAndAddInCallUseMode,
        p.getSynchronousPlatformExtensionAndAddInCallUseMode())
    );
    p.setInterfaceCompatibilityMode(
      enumOrKeepV21InterfaceCompatibility(incoming.interfaceCompatibilityMode, p.getInterfaceCompatibilityMode())
    );
    p.setCompatibilityMode(enumOrKeepV21Compatibility(incoming.compatibilityMode, p.getCompatibilityMode()));
    byte[] patched = tryGranularWrite(originalXml, root, SchemaVersion.V2_21, baseline, incoming)
      .orElseThrow(() -> new IllegalStateException(
        "Не удалось применить изменения точечно. Полная пересборка XML через JAXB предотвращена."
      ));
    Files.write(configurationXml, patched);
  }

  private static Optional<byte[]> tryGranularWrite(
    String originalXml,
    Object rootAfterApply,
    SchemaVersion version,
    ConfigurationPropertiesDto baseline,
    ConfigurationPropertiesDto incoming) throws JAXBException {
    ByteArrayOutputStream buf = new ByteArrayOutputStream();
    DesignerXml.marshal(version, rootAfterApply, buf, WriteOptions.defaults());
    String updatedXml = buf.toString(StandardCharsets.UTF_8);
    List<String> changedTags = changedPropertyTags(baseline, incoming);
    if (changedTags.isEmpty()) {
      return Optional.of(originalXml.getBytes(StandardCharsets.UTF_8));
    }
    List<Replacement> reps = new ArrayList<>();
    try {
      for (String tag : changedTags) {
        MdObjectXmlRegions.Region updatedReg =
          MdObjectXmlRegions.findDirectChildOfPropertiesRegion(updatedXml, "Configuration", tag);
        if (!updatedReg.isValid()) {
          return Optional.empty();
        }
        String replacement = updatedXml.substring(updatedReg.start(), updatedReg.end());
        MdObjectXmlRegions.Region currentReg =
          MdObjectXmlRegions.findDirectChildOfPropertiesRegion(originalXml, "Configuration", tag);
        if (currentReg.isValid()) {
          reps.add(new Replacement(currentReg.start(), currentReg.end(), replacement));
          continue;
        }
        MdObjectXmlRegions.Region propertiesRegion = MdObjectXmlRegions.findPropertiesRegion(originalXml, "Configuration");
        if (!propertiesRegion.isValid()) {
          return Optional.empty();
        }
        int insertPos = propertiesCloseTagStart(originalXml, propertiesRegion);
        if (insertPos < 0) {
          return Optional.empty();
        }
        reps.add(new Replacement(insertPos, insertPos, insertionBeforePropertiesClose(originalXml, insertPos, replacement)));
      }
    } catch (XMLStreamException e) {
      return Optional.empty();
    }
    reps.sort(Comparator.comparingInt(Replacement::start).reversed());
    StringBuilder sb = new StringBuilder(originalXml);
    for (Replacement rep : reps) {
      sb.replace(rep.start, rep.end, rep.text);
    }
    byte[] out = sb.toString().getBytes(StandardCharsets.UTF_8);
    try {
      ConfigurationPropertiesDto verified = readDto(out, version);
      return equalsDto(verified, incoming) ? Optional.of(out) : Optional.empty();
    } catch (JAXBException e) {
      return Optional.empty();
    }
  }

  private static ConfigurationPropertiesDto readDto(byte[] xmlBytes, SchemaVersion version) throws JAXBException {
    Object root = DesignerXml.unmarshal(version, new ByteArrayInputStream(xmlBytes));
    return switch (version) {
      case V2_20 -> readFromRootV20(root);
      case V2_21 -> readFromRootV21(root);
    };
  }

  private static ConfigurationPropertiesDto readFromRootV20(Object root) {
    if (!(root instanceof JAXBElement<?> je)
      || !(je.getValue() instanceof io.github.yellowhammer.designerxml.jaxb.v2_20.mdclasses.MetaDataObject mdo)
      || mdo.getConfiguration() == null
      || mdo.getConfiguration().getProperties() == null) {
      throw new IllegalArgumentException("unsupported MetaDataObject for configuration-properties");
    }
    var p = mdo.getConfiguration().getProperties();
    var out = new ConfigurationPropertiesDto();
    out.name = nvl(p.getName());
    out.synonymRu = LocalStringSync.firstRuV20(p.getSynonym());
    out.comment = nvl(p.getComment());
    out.defaultRunMode = p.getDefaultRunMode() == null ? "" : p.getDefaultRunMode().name();
    out.usePurposes = enumListToNamesV20(p.getUsePurposes());
    out.scriptVariant = p.getScriptVariant() == null ? "" : p.getScriptVariant().name();
    out.defaultRoles = p.getDefaultRoles() == null ? new ArrayList<>() : MdListTypeRefs.readItemTexts(p.getDefaultRoles().getItem());
    out.managedApplicationModule = nvl(p.getManagedApplicationModule());
    out.sessionModule = nvl(p.getSessionModule());
    out.externalConnectionModule = nvl(p.getExternalConnectionModule());
    out.briefInformationRu = LocalStringSync.firstRuV20(p.getBriefInformation());
    out.detailedInformationRu = LocalStringSync.firstRuV20(p.getDetailedInformation());
    out.copyrightRu = LocalStringSync.firstRuV20(p.getCopyright());
    out.vendorInformationAddressRu = LocalStringSync.firstRuV20(p.getVendorInformationAddress());
    out.configurationInformationAddressRu = LocalStringSync.firstRuV20(p.getConfigurationInformationAddress());
    out.vendor = nvl(p.getVendor());
    out.version = nvl(p.getVersion());
    out.updateCatalogAddress = nvl(p.getUpdateCatalogAddress());
    out.dataLockControlMode = p.getDataLockControlMode() == null ? "" : p.getDataLockControlMode().name();
    out.objectAutonumerationMode = p.getObjectAutonumerationMode() == null ? "" : p.getObjectAutonumerationMode().name();
    out.modalityUseMode = p.getModalityUseMode() == null ? "" : p.getModalityUseMode().name();
    out.synchronousPlatformExtensionAndAddInCallUseMode =
      p.getSynchronousPlatformExtensionAndAddInCallUseMode() == null
        ? ""
        : p.getSynchronousPlatformExtensionAndAddInCallUseMode().name();
    out.interfaceCompatibilityMode =
      p.getInterfaceCompatibilityMode() == null ? "" : p.getInterfaceCompatibilityMode().name();
    out.compatibilityMode = p.getCompatibilityMode() == null ? "" : p.getCompatibilityMode().name();
    return out;
  }

  private static ConfigurationPropertiesDto readFromRootV21(Object root) {
    if (!(root instanceof JAXBElement<?> je)
      || !(je.getValue() instanceof io.github.yellowhammer.designerxml.jaxb.v2_21.mdclasses.MetaDataObject mdo)
      || mdo.getConfiguration() == null
      || mdo.getConfiguration().getProperties() == null) {
      throw new IllegalArgumentException("unsupported MetaDataObject for configuration-properties");
    }
    var p = mdo.getConfiguration().getProperties();
    var out = new ConfigurationPropertiesDto();
    out.name = nvl(p.getName());
    out.synonymRu = LocalStringSync.firstRuV21(p.getSynonym());
    out.comment = nvl(p.getComment());
    out.defaultRunMode = p.getDefaultRunMode() == null ? "" : p.getDefaultRunMode().name();
    out.usePurposes = enumListToNamesV21(p.getUsePurposes());
    out.scriptVariant = p.getScriptVariant() == null ? "" : p.getScriptVariant().name();
    out.defaultRoles = p.getDefaultRoles() == null ? new ArrayList<>() : MdListTypeRefs.readItemTexts(p.getDefaultRoles().getItem());
    out.managedApplicationModule = nvl(p.getManagedApplicationModule());
    out.sessionModule = nvl(p.getSessionModule());
    out.externalConnectionModule = nvl(p.getExternalConnectionModule());
    out.briefInformationRu = LocalStringSync.firstRuV21(p.getBriefInformation());
    out.detailedInformationRu = LocalStringSync.firstRuV21(p.getDetailedInformation());
    out.copyrightRu = LocalStringSync.firstRuV21(p.getCopyright());
    out.vendorInformationAddressRu = LocalStringSync.firstRuV21(p.getVendorInformationAddress());
    out.configurationInformationAddressRu = LocalStringSync.firstRuV21(p.getConfigurationInformationAddress());
    out.vendor = nvl(p.getVendor());
    out.version = nvl(p.getVersion());
    out.updateCatalogAddress = nvl(p.getUpdateCatalogAddress());
    out.dataLockControlMode = p.getDataLockControlMode() == null ? "" : p.getDataLockControlMode().name();
    out.objectAutonumerationMode = p.getObjectAutonumerationMode() == null ? "" : p.getObjectAutonumerationMode().name();
    out.modalityUseMode = p.getModalityUseMode() == null ? "" : p.getModalityUseMode().name();
    out.synchronousPlatformExtensionAndAddInCallUseMode =
      p.getSynchronousPlatformExtensionAndAddInCallUseMode() == null
        ? ""
        : p.getSynchronousPlatformExtensionAndAddInCallUseMode().name();
    out.interfaceCompatibilityMode =
      p.getInterfaceCompatibilityMode() == null ? "" : p.getInterfaceCompatibilityMode().name();
    out.compatibilityMode = p.getCompatibilityMode() == null ? "" : p.getCompatibilityMode().name();
    return out;
  }

  private static ConfigurationPropertiesDto normalizeIncoming(
    ConfigurationPropertiesDto incoming,
    ConfigurationPropertiesDto baseline) {
    ConfigurationPropertiesDto out = incoming == null ? new ConfigurationPropertiesDto() : incoming;
    if (baseline == null) {
      baseline = new ConfigurationPropertiesDto();
    }
    out.name = nvl(out.name);
    out.synonymRu = nvl(out.synonymRu);
    out.comment = nvl(out.comment);
    out.defaultRunMode = nvl(out.defaultRunMode);
    out.usePurposes = safeTrimmedList(out.usePurposes);
    out.scriptVariant = nvl(out.scriptVariant);
    out.defaultRoles = safeTrimmedList(out.defaultRoles);
    out.managedApplicationModule = nvl(out.managedApplicationModule);
    out.sessionModule = nvl(out.sessionModule);
    out.externalConnectionModule = nvl(out.externalConnectionModule);
    out.briefInformationRu = nvl(out.briefInformationRu);
    out.detailedInformationRu = nvl(out.detailedInformationRu);
    out.copyrightRu = nvl(out.copyrightRu);
    out.vendorInformationAddressRu = nvl(out.vendorInformationAddressRu);
    out.configurationInformationAddressRu = nvl(out.configurationInformationAddressRu);
    out.vendor = nvl(out.vendor);
    out.version = nvl(out.version);
    out.updateCatalogAddress = nvl(out.updateCatalogAddress);
    out.dataLockControlMode = nvl(out.dataLockControlMode);
    out.objectAutonumerationMode = nvl(out.objectAutonumerationMode);
    out.modalityUseMode = nvl(out.modalityUseMode);
    out.synchronousPlatformExtensionAndAddInCallUseMode = nvl(out.synchronousPlatformExtensionAndAddInCallUseMode);
    out.interfaceCompatibilityMode = nvl(out.interfaceCompatibilityMode);
    out.compatibilityMode = nvl(out.compatibilityMode);
    if (out.defaultRunMode.isEmpty()) {
      out.defaultRunMode = baseline.defaultRunMode;
    }
    if (out.scriptVariant.isEmpty()) {
      out.scriptVariant = baseline.scriptVariant;
    }
    if (out.dataLockControlMode.isEmpty()) {
      out.dataLockControlMode = baseline.dataLockControlMode;
    }
    if (out.objectAutonumerationMode.isEmpty()) {
      out.objectAutonumerationMode = baseline.objectAutonumerationMode;
    }
    if (out.modalityUseMode.isEmpty()) {
      out.modalityUseMode = baseline.modalityUseMode;
    }
    if (out.synchronousPlatformExtensionAndAddInCallUseMode.isEmpty()) {
      out.synchronousPlatformExtensionAndAddInCallUseMode = baseline.synchronousPlatformExtensionAndAddInCallUseMode;
    }
    if (out.interfaceCompatibilityMode.isEmpty()) {
      out.interfaceCompatibilityMode = baseline.interfaceCompatibilityMode;
    }
    if (out.compatibilityMode.isEmpty()) {
      out.compatibilityMode = baseline.compatibilityMode;
    }
    return out;
  }

  private static List<String> changedPropertyTags(ConfigurationPropertiesDto baseline, ConfigurationPropertiesDto incoming) {
    List<String> tags = new ArrayList<>();
    if (!nvl(baseline.name).equals(nvl(incoming.name))) {
      tags.add("Name");
    }
    if (!nvl(baseline.synonymRu).equals(nvl(incoming.synonymRu))) {
      tags.add("Synonym");
    }
    if (!nvl(baseline.comment).equals(nvl(incoming.comment))) {
      tags.add("Comment");
    }
    if (!nvl(baseline.defaultRunMode).equals(nvl(incoming.defaultRunMode))) {
      tags.add("DefaultRunMode");
    }
    if (!safeTrimmedList(baseline.usePurposes).equals(safeTrimmedList(incoming.usePurposes))) {
      tags.add("UsePurposes");
    }
    if (!nvl(baseline.scriptVariant).equals(nvl(incoming.scriptVariant))) {
      tags.add("ScriptVariant");
    }
    if (!safeTrimmedList(baseline.defaultRoles).equals(safeTrimmedList(incoming.defaultRoles))) {
      tags.add("DefaultRoles");
    }
    if (!nvl(baseline.managedApplicationModule).equals(nvl(incoming.managedApplicationModule))) {
      tags.add("ManagedApplicationModule");
    }
    if (!nvl(baseline.sessionModule).equals(nvl(incoming.sessionModule))) {
      tags.add("SessionModule");
    }
    if (!nvl(baseline.externalConnectionModule).equals(nvl(incoming.externalConnectionModule))) {
      tags.add("ExternalConnectionModule");
    }
    if (!nvl(baseline.briefInformationRu).equals(nvl(incoming.briefInformationRu))) {
      tags.add("BriefInformation");
    }
    if (!nvl(baseline.detailedInformationRu).equals(nvl(incoming.detailedInformationRu))) {
      tags.add("DetailedInformation");
    }
    if (!nvl(baseline.copyrightRu).equals(nvl(incoming.copyrightRu))) {
      tags.add("Copyright");
    }
    if (!nvl(baseline.vendorInformationAddressRu).equals(nvl(incoming.vendorInformationAddressRu))) {
      tags.add("VendorInformationAddress");
    }
    if (!nvl(baseline.configurationInformationAddressRu).equals(nvl(incoming.configurationInformationAddressRu))) {
      tags.add("ConfigurationInformationAddress");
    }
    if (!nvl(baseline.vendor).equals(nvl(incoming.vendor))) {
      tags.add("Vendor");
    }
    if (!nvl(baseline.version).equals(nvl(incoming.version))) {
      tags.add("Version");
    }
    if (!nvl(baseline.updateCatalogAddress).equals(nvl(incoming.updateCatalogAddress))) {
      tags.add("UpdateCatalogAddress");
    }
    if (!nvl(baseline.dataLockControlMode).equals(nvl(incoming.dataLockControlMode))) {
      tags.add("DataLockControlMode");
    }
    if (!nvl(baseline.objectAutonumerationMode).equals(nvl(incoming.objectAutonumerationMode))) {
      tags.add("ObjectAutonumerationMode");
    }
    if (!nvl(baseline.modalityUseMode).equals(nvl(incoming.modalityUseMode))) {
      tags.add("ModalityUseMode");
    }
    if (!nvl(baseline.synchronousPlatformExtensionAndAddInCallUseMode)
      .equals(nvl(incoming.synchronousPlatformExtensionAndAddInCallUseMode))) {
      tags.add("SynchronousPlatformExtensionAndAddInCallUseMode");
    }
    if (!nvl(baseline.interfaceCompatibilityMode).equals(nvl(incoming.interfaceCompatibilityMode))) {
      tags.add("InterfaceCompatibilityMode");
    }
    if (!nvl(baseline.compatibilityMode).equals(nvl(incoming.compatibilityMode))) {
      tags.add("CompatibilityMode");
    }
    return tags;
  }

  private static boolean equalsDto(ConfigurationPropertiesDto left, ConfigurationPropertiesDto right) {
    if (left == right) {
      return true;
    }
    if (left == null || right == null) {
      return false;
    }
    return nvl(left.name).equals(nvl(right.name))
      && nvl(left.synonymRu).equals(nvl(right.synonymRu))
      && nvl(left.comment).equals(nvl(right.comment))
      && nvl(left.defaultRunMode).equals(nvl(right.defaultRunMode))
      && safeTrimmedList(left.usePurposes).equals(safeTrimmedList(right.usePurposes))
      && nvl(left.scriptVariant).equals(nvl(right.scriptVariant))
      && safeTrimmedList(left.defaultRoles).equals(safeTrimmedList(right.defaultRoles))
      && nvl(left.managedApplicationModule).equals(nvl(right.managedApplicationModule))
      && nvl(left.sessionModule).equals(nvl(right.sessionModule))
      && nvl(left.externalConnectionModule).equals(nvl(right.externalConnectionModule))
      && nvl(left.briefInformationRu).equals(nvl(right.briefInformationRu))
      && nvl(left.detailedInformationRu).equals(nvl(right.detailedInformationRu))
      && nvl(left.copyrightRu).equals(nvl(right.copyrightRu))
      && nvl(left.vendorInformationAddressRu).equals(nvl(right.vendorInformationAddressRu))
      && nvl(left.configurationInformationAddressRu).equals(nvl(right.configurationInformationAddressRu))
      && nvl(left.vendor).equals(nvl(right.vendor))
      && nvl(left.version).equals(nvl(right.version))
      && nvl(left.updateCatalogAddress).equals(nvl(right.updateCatalogAddress))
      && nvl(left.dataLockControlMode).equals(nvl(right.dataLockControlMode))
      && nvl(left.objectAutonumerationMode).equals(nvl(right.objectAutonumerationMode))
      && nvl(left.modalityUseMode).equals(nvl(right.modalityUseMode))
      && nvl(left.synchronousPlatformExtensionAndAddInCallUseMode)
      .equals(nvl(right.synchronousPlatformExtensionAndAddInCallUseMode))
      && nvl(left.interfaceCompatibilityMode).equals(nvl(right.interfaceCompatibilityMode))
      && nvl(left.compatibilityMode).equals(nvl(right.compatibilityMode));
  }

  private record Replacement(int start, int end, String text) {
  }

  private static int propertiesCloseTagStart(String xmlUtf8, MdObjectXmlRegions.Region propertiesRegion) {
    return xmlUtf8.lastIndexOf("</", propertiesRegion.end() - 1);
  }

  private static String insertionBeforePropertiesClose(String xmlUtf8, int insertPos, String replacementElementXml) {
    String parentIndent = currentLineIndent(xmlUtf8, insertPos);
    String childIndent = parentIndent + "\t";
    String compact = replacementElementXml.trim().replace(">\r\n<", "><").replace(">\n<", "><");
    String expanded = compact.replace("><", ">\n<");
    String[] lines = expanded.split("\n");
    StringBuilder out = new StringBuilder(expanded.length() + childIndent.length() * lines.length + 2);
    out.append('\n');
    for (int i = 0; i < lines.length; i++) {
      if (i > 0) {
        out.append('\n');
      }
      out.append(childIndent).append(lines[i].trim());
    }
    return out.toString();
  }

  private static String currentLineIndent(String xmlUtf8, int startOffset) {
    int from = startOffset - 1;
    while (from >= 0 && xmlUtf8.charAt(from) != '\n' && xmlUtf8.charAt(from) != '\r') {
      from--;
    }
    from++;
    int i = from;
    while (i < xmlUtf8.length()) {
      char c = xmlUtf8.charAt(i);
      if (c != ' ' && c != '\t') {
        break;
      }
      i++;
    }
    return xmlUtf8.substring(from, i);
  }

  private static List<String> enumListToNamesV20(
    io.github.yellowhammer.designerxml.jaxb.v2_20.v8_1_data_core.FixedArray list) {
    List<String> out = new ArrayList<>();
    if (list == null || list.getValue() == null) {
      return out;
    }
    for (Object v : list.getValue()) {
      if (v != null) {
        out.add(v.toString());
      }
    }
    return out;
  }

  private static List<String> enumListToNamesV21(
    io.github.yellowhammer.designerxml.jaxb.v2_21.v8_1_data_core.FixedArray list) {
    List<String> out = new ArrayList<>();
    if (list == null || list.getValue() == null) {
      return out;
    }
    for (Object v : list.getValue()) {
      if (v != null) {
        out.add(v.toString());
      }
    }
    return out;
  }

  private static void applyUsePurposesV20(
    io.github.yellowhammer.designerxml.jaxb.v2_20.mdclasses.ConfigurationProperties p,
    List<String> values) {
    if (p.getUsePurposes() == null || p.getUsePurposes().getValue() == null) {
      return;
    }
    p.getUsePurposes().getValue().clear();
    for (String v : safeTrimmedList(values)) {
      try {
        p.getUsePurposes().getValue().add(
          io.github.yellowhammer.designerxml.jaxb.v2_20.v8_2_managed_application_core.ApplicationUsePurpose
            .valueOf(v)
        );
      } catch (IllegalArgumentException ignored) {
        // Игнорируем неизвестные значения, чтобы не ломать сохранение.
      }
    }
  }

  private static void applyUsePurposesV21(
    io.github.yellowhammer.designerxml.jaxb.v2_21.mdclasses.ConfigurationProperties p,
    List<String> values) {
    if (p.getUsePurposes() == null || p.getUsePurposes().getValue() == null) {
      return;
    }
    p.getUsePurposes().getValue().clear();
    for (String v : safeTrimmedList(values)) {
      try {
        p.getUsePurposes().getValue().add(
          io.github.yellowhammer.designerxml.jaxb.v2_21.v8_2_managed_application_core.ApplicationUsePurpose
            .valueOf(v)
        );
      } catch (IllegalArgumentException ignored) {
        // Игнорируем неизвестные значения, чтобы не ломать сохранение.
      }
    }
  }

  private static io.github.yellowhammer.designerxml.jaxb.v2_20.v8_2_managed_application_core.ClientRunMode enumOrKeepV20DefaultRunMode(
    String raw,
    io.github.yellowhammer.designerxml.jaxb.v2_20.v8_2_managed_application_core.ClientRunMode current) {
    return enumOrKeep(raw, current, io.github.yellowhammer.designerxml.jaxb.v2_20.v8_2_managed_application_core.ClientRunMode.class);
  }

  private static io.github.yellowhammer.designerxml.jaxb.v2_21.v8_2_managed_application_core.ClientRunMode enumOrKeepV21DefaultRunMode(
    String raw,
    io.github.yellowhammer.designerxml.jaxb.v2_21.v8_2_managed_application_core.ClientRunMode current) {
    return enumOrKeep(raw, current, io.github.yellowhammer.designerxml.jaxb.v2_21.v8_2_managed_application_core.ClientRunMode.class);
  }

  private static io.github.yellowhammer.designerxml.jaxb.v2_20.v8_3_xcf_enums.ScriptVariant enumOrKeepV20ScriptVariant(
    String raw,
    io.github.yellowhammer.designerxml.jaxb.v2_20.v8_3_xcf_enums.ScriptVariant current) {
    return enumOrKeep(raw, current, io.github.yellowhammer.designerxml.jaxb.v2_20.v8_3_xcf_enums.ScriptVariant.class);
  }

  private static io.github.yellowhammer.designerxml.jaxb.v2_21.v8_3_xcf_enums.ScriptVariant enumOrKeepV21ScriptVariant(
    String raw,
    io.github.yellowhammer.designerxml.jaxb.v2_21.v8_3_xcf_enums.ScriptVariant current) {
    return enumOrKeep(raw, current, io.github.yellowhammer.designerxml.jaxb.v2_21.v8_3_xcf_enums.ScriptVariant.class);
  }

  private static io.github.yellowhammer.designerxml.jaxb.v2_20.v8_3_xcf_enums.DefaultDataLockControlMode enumOrKeepV20DataLock(
    String raw,
    io.github.yellowhammer.designerxml.jaxb.v2_20.v8_3_xcf_enums.DefaultDataLockControlMode current) {
    return enumOrKeep(raw, current, io.github.yellowhammer.designerxml.jaxb.v2_20.v8_3_xcf_enums.DefaultDataLockControlMode.class);
  }

  private static io.github.yellowhammer.designerxml.jaxb.v2_21.v8_3_xcf_enums.DefaultDataLockControlMode enumOrKeepV21DataLock(
    String raw,
    io.github.yellowhammer.designerxml.jaxb.v2_21.v8_3_xcf_enums.DefaultDataLockControlMode current) {
    return enumOrKeep(raw, current, io.github.yellowhammer.designerxml.jaxb.v2_21.v8_3_xcf_enums.DefaultDataLockControlMode.class);
  }

  private static io.github.yellowhammer.designerxml.jaxb.v2_20.v8_3_xcf_enums.ObjectAutonumerationMode enumOrKeepV20AutoNum(
    String raw,
    io.github.yellowhammer.designerxml.jaxb.v2_20.v8_3_xcf_enums.ObjectAutonumerationMode current) {
    return enumOrKeep(raw, current, io.github.yellowhammer.designerxml.jaxb.v2_20.v8_3_xcf_enums.ObjectAutonumerationMode.class);
  }

  private static io.github.yellowhammer.designerxml.jaxb.v2_21.v8_3_xcf_enums.ObjectAutonumerationMode enumOrKeepV21AutoNum(
    String raw,
    io.github.yellowhammer.designerxml.jaxb.v2_21.v8_3_xcf_enums.ObjectAutonumerationMode current) {
    return enumOrKeep(raw, current, io.github.yellowhammer.designerxml.jaxb.v2_21.v8_3_xcf_enums.ObjectAutonumerationMode.class);
  }

  private static io.github.yellowhammer.designerxml.jaxb.v2_20.v8_3_xcf_enums.ModalityUseMode enumOrKeepV20Modality(
    String raw,
    io.github.yellowhammer.designerxml.jaxb.v2_20.v8_3_xcf_enums.ModalityUseMode current) {
    return enumOrKeep(raw, current, io.github.yellowhammer.designerxml.jaxb.v2_20.v8_3_xcf_enums.ModalityUseMode.class);
  }

  private static io.github.yellowhammer.designerxml.jaxb.v2_21.v8_3_xcf_enums.ModalityUseMode enumOrKeepV21Modality(
    String raw,
    io.github.yellowhammer.designerxml.jaxb.v2_21.v8_3_xcf_enums.ModalityUseMode current) {
    return enumOrKeep(raw, current, io.github.yellowhammer.designerxml.jaxb.v2_21.v8_3_xcf_enums.ModalityUseMode.class);
  }

  private static io.github.yellowhammer.designerxml.jaxb.v2_20.v8_3_xcf_enums.SynchronousPlatformExtensionAndAddInCallUseMode enumOrKeepV20SyncMode(
    String raw,
    io.github.yellowhammer.designerxml.jaxb.v2_20.v8_3_xcf_enums.SynchronousPlatformExtensionAndAddInCallUseMode current) {
    return enumOrKeep(raw, current, io.github.yellowhammer.designerxml.jaxb.v2_20.v8_3_xcf_enums.SynchronousPlatformExtensionAndAddInCallUseMode.class);
  }

  private static io.github.yellowhammer.designerxml.jaxb.v2_21.v8_3_xcf_enums.SynchronousPlatformExtensionAndAddInCallUseMode enumOrKeepV21SyncMode(
    String raw,
    io.github.yellowhammer.designerxml.jaxb.v2_21.v8_3_xcf_enums.SynchronousPlatformExtensionAndAddInCallUseMode current) {
    return enumOrKeep(raw, current, io.github.yellowhammer.designerxml.jaxb.v2_21.v8_3_xcf_enums.SynchronousPlatformExtensionAndAddInCallUseMode.class);
  }

  private static io.github.yellowhammer.designerxml.jaxb.v2_20.v8_3_xcf_enums.InterfaceCompatibilityMode enumOrKeepV20InterfaceCompatibility(
    String raw,
    io.github.yellowhammer.designerxml.jaxb.v2_20.v8_3_xcf_enums.InterfaceCompatibilityMode current) {
    return enumOrKeep(raw, current, io.github.yellowhammer.designerxml.jaxb.v2_20.v8_3_xcf_enums.InterfaceCompatibilityMode.class);
  }

  private static io.github.yellowhammer.designerxml.jaxb.v2_21.v8_3_xcf_enums.InterfaceCompatibilityMode enumOrKeepV21InterfaceCompatibility(
    String raw,
    io.github.yellowhammer.designerxml.jaxb.v2_21.v8_3_xcf_enums.InterfaceCompatibilityMode current) {
    return enumOrKeep(raw, current, io.github.yellowhammer.designerxml.jaxb.v2_21.v8_3_xcf_enums.InterfaceCompatibilityMode.class);
  }

  private static io.github.yellowhammer.designerxml.jaxb.v2_20.v8_3_xcf_enums.CompatibilityMode enumOrKeepV20Compatibility(
    String raw,
    io.github.yellowhammer.designerxml.jaxb.v2_20.v8_3_xcf_enums.CompatibilityMode current) {
    return enumOrKeep(raw, current, io.github.yellowhammer.designerxml.jaxb.v2_20.v8_3_xcf_enums.CompatibilityMode.class);
  }

  private static io.github.yellowhammer.designerxml.jaxb.v2_21.v8_3_xcf_enums.CompatibilityMode enumOrKeepV21Compatibility(
    String raw,
    io.github.yellowhammer.designerxml.jaxb.v2_21.v8_3_xcf_enums.CompatibilityMode current) {
    return enumOrKeep(raw, current, io.github.yellowhammer.designerxml.jaxb.v2_21.v8_3_xcf_enums.CompatibilityMode.class);
  }

  private static <T extends Enum<T>> T enumOrKeep(String raw, T current, Class<T> enumType) {
    String value = nvl(raw).trim();
    if (value.isEmpty()) {
      return current;
    }
    try {
      return Enum.valueOf(enumType, value);
    } catch (IllegalArgumentException ignored) {
      return current;
    }
  }

  private static List<String> safeTrimmedList(List<String> input) {
    List<String> out = new ArrayList<>();
    if (input == null) {
      return out;
    }
    for (String item : input) {
      String v = nvl(item).trim();
      if (!v.isEmpty()) {
        out.add(v);
      }
    }
    return out;
  }

  private static String nvl(String v) {
    return v == null ? "" : v;
  }
}
