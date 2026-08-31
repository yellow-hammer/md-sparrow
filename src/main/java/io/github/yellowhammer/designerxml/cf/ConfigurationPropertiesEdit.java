/*
 * This file is a part of md-sparrow.
 *
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package io.github.yellowhammer.designerxml.cf;

import io.github.yellowhammer.designerxml.DesignerXml;
import io.github.yellowhammer.designerxml.SchemaVersion;
import io.github.yellowhammer.designerxml.WriteOptions;
import io.github.yellowhammer.designerxml.reflect.JaxbReflect;
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

/**
 * Чтение/запись свойств конфигурации (DTO {@link ConfigurationPropertiesDto}) — версионно-нейтрально
 * через {@link JaxbReflect}.
 */
public final class ConfigurationPropertiesEdit {

  private static final String USE_PURPOSE_ENUM = ".v8_2_managed_application_core.ApplicationUsePurpose";

  private ConfigurationPropertiesEdit() {
  }

  public static ConfigurationPropertiesDto read(Path configurationXml, SchemaVersion schemaVersion)
    throws JAXBException, IOException {
    return fill(properties(DesignerXml.read(configurationXml, schemaVersion)), configurationXml, schemaVersion);
  }

  public static void write(Path configurationXml, SchemaVersion schemaVersion, ConfigurationPropertiesDto dto)
    throws JAXBException, IOException {
    ConfigurationPropertiesDto baseline = read(configurationXml, schemaVersion);
    ConfigurationPropertiesDto incoming = normalizeIncoming(dto, baseline);
    if (equalsDto(baseline, incoming)) {
      return;
    }
    String originalXml = Files.readString(configurationXml, StandardCharsets.UTF_8);
    Object root = DesignerXml.read(configurationXml, schemaVersion);
    Object p = properties(root);
    JaxbReflect.setOptional(p, "setName", nvl(incoming.name));
    LocalStringSync.setOrPutRu(JaxbReflect.getOptional(p, "getSynonym"), nvl(incoming.synonymRu));
    JaxbReflect.setOptional(p, "setComment", nvl(incoming.comment));
    JaxbReflect.setEnumOrKeep(p, "setDefaultRunMode", incoming.defaultRunMode);
    applyUsePurposes(schemaVersion, p, incoming.usePurposes);
    JaxbReflect.setEnumOrKeep(p, "setScriptVariant", incoming.scriptVariant);
    Object roles = JaxbReflect.getOptional(p, "getDefaultRoles");
    if (roles != null) {
      MdListTypeRefs.replaceItems(roles, safeTrimmedList(incoming.defaultRoles));
    }
    JaxbReflect.setOptional(p, "setManagedApplicationModule", nvl(incoming.managedApplicationModule));
    JaxbReflect.setOptional(p, "setSessionModule", nvl(incoming.sessionModule));
    JaxbReflect.setOptional(p, "setExternalConnectionModule", nvl(incoming.externalConnectionModule));
    LocalStringSync.setOrPutRu(JaxbReflect.getOptional(p, "getBriefInformation"), nvl(incoming.briefInformationRu));
    LocalStringSync.setOrPutRu(JaxbReflect.getOptional(p, "getDetailedInformation"), nvl(incoming.detailedInformationRu));
    LocalStringSync.setOrPutRu(JaxbReflect.getOptional(p, "getCopyright"), nvl(incoming.copyrightRu));
    LocalStringSync.setOrPutRu(JaxbReflect.getOptional(p, "getVendorInformationAddress"),
      nvl(incoming.vendorInformationAddressRu));
    LocalStringSync.setOrPutRu(JaxbReflect.getOptional(p, "getConfigurationInformationAddress"),
      nvl(incoming.configurationInformationAddressRu));
    JaxbReflect.setOptional(p, "setVendor", nvl(incoming.vendor));
    JaxbReflect.setOptional(p, "setVersion", nvl(incoming.version));
    JaxbReflect.setOptional(p, "setUpdateCatalogAddress", nvl(incoming.updateCatalogAddress));
    JaxbReflect.setEnumOrKeep(p, "setDataLockControlMode", incoming.dataLockControlMode);
    JaxbReflect.setEnumOrKeep(p, "setObjectAutonumerationMode", incoming.objectAutonumerationMode);
    JaxbReflect.setEnumOrKeep(p, "setModalityUseMode", incoming.modalityUseMode);
    JaxbReflect.setEnumOrKeep(p, "setSynchronousPlatformExtensionAndAddInCallUseMode",
      incoming.synchronousPlatformExtensionAndAddInCallUseMode);
    JaxbReflect.setEnumOrKeep(p, "setInterfaceCompatibilityMode", incoming.interfaceCompatibilityMode);
    // Режимы совместимости бывают вне перечисления модели. Неизменённое значение в модель не
    // переносим: точечная запись его не трогает, и в файле оно остаётся как было.
    setEnumIfChanged(p, "setCompatibilityMode", baseline.compatibilityMode, incoming.compatibilityMode);
    setEnumIfChanged(p, "setConfigurationExtensionCompatibilityMode",
      baseline.configurationExtensionCompatibilityMode, incoming.configurationExtensionCompatibilityMode);
    byte[] patched = tryGranularWrite(originalXml, root, schemaVersion, baseline, incoming)
      .orElseThrow(() -> new IllegalStateException(
        "Не удалось применить изменения точечно. Полная пересборка XML через JAXB предотвращена."
      ));
    Files.write(configurationXml, patched);
  }

  /** Переносит значение в модель, только если оно изменилось: неизвестное ей значение она отвергнет. */
  private static void setEnumIfChanged(Object p, String setter, String baseline, String incoming) {
    if (!nvl(baseline).equals(nvl(incoming))) {
      JaxbReflect.setEnumOrKeep(p, setter, incoming);
    }
  }

  /** {@code Configuration.Properties} любой версии; иначе — исключение. */
  private static Object properties(Object root) {
    if (!(root instanceof JAXBElement<?> je)) {
      throw new IllegalArgumentException("expected JAXBElement<MetaDataObject>");
    }
    Object cfg = JaxbReflect.get(je.getValue(), "getConfiguration");
    Object p = cfg == null ? null : JaxbReflect.get(cfg, "getProperties");
    if (p == null) {
      throw new IllegalArgumentException("unsupported MetaDataObject for configuration-properties");
    }
    return p;
  }

  private static ConfigurationPropertiesDto fill(Object p, Path configurationXml, SchemaVersion version) {
    return fill(p, (value, element) -> UnknownEnumValues.orFromXml(value, configurationXml, element), version);
  }

  private static ConfigurationPropertiesDto fill(Object p, byte[] xml, SchemaVersion version) {
    return fill(p, (value, element) -> UnknownEnumValues.orFromXml(value, xml, element), version);
  }

  /** Значение перечислимого свойства: от модели либо дочитанное из выгрузки. */
  private interface EnumValue {
    String of(String fromModel, String element);
  }

  private static ConfigurationPropertiesDto fill(Object p, EnumValue enumValue, SchemaVersion version) {
    var out = new ConfigurationPropertiesDto();
    out.name = nvl(JaxbReflect.getStringOptional(p, "getName"));
    out.synonymRu = LocalStringSync.firstRu(JaxbReflect.getOptional(p, "getSynonym"));
    out.comment = nvl(JaxbReflect.getStringOptional(p, "getComment"));
    out.defaultRunMode = JaxbReflect.enumNameOptional(p, "getDefaultRunMode");
    out.usePurposes = enumListToNames(JaxbReflect.getOptional(p, "getUsePurposes"));
    out.usePurposeOptions = usePurposeOptions(version);
    out.scriptVariant = JaxbReflect.enumNameOptional(p, "getScriptVariant");
    Object roles = JaxbReflect.getOptional(p, "getDefaultRoles");
    out.defaultRoles = roles == null
      ? new ArrayList<>()
      : MdListTypeRefs.readItemTexts(JaxbReflect.list(roles, "getItem"));
    out.managedApplicationModule = nvl(JaxbReflect.getStringOptional(p, "getManagedApplicationModule"));
    out.sessionModule = nvl(JaxbReflect.getStringOptional(p, "getSessionModule"));
    out.externalConnectionModule = nvl(JaxbReflect.getStringOptional(p, "getExternalConnectionModule"));
    out.briefInformationRu = LocalStringSync.firstRu(JaxbReflect.getOptional(p, "getBriefInformation"));
    out.detailedInformationRu = LocalStringSync.firstRu(JaxbReflect.getOptional(p, "getDetailedInformation"));
    out.copyrightRu = LocalStringSync.firstRu(JaxbReflect.getOptional(p, "getCopyright"));
    out.vendorInformationAddressRu = LocalStringSync.firstRu(JaxbReflect.getOptional(p, "getVendorInformationAddress"));
    out.configurationInformationAddressRu =
      LocalStringSync.firstRu(JaxbReflect.getOptional(p, "getConfigurationInformationAddress"));
    out.vendor = nvl(JaxbReflect.getStringOptional(p, "getVendor"));
    out.version = nvl(JaxbReflect.getStringOptional(p, "getVersion"));
    out.updateCatalogAddress = nvl(JaxbReflect.getStringOptional(p, "getUpdateCatalogAddress"));
    out.dataLockControlMode = JaxbReflect.enumNameOptional(p, "getDataLockControlMode");
    out.objectAutonumerationMode = JaxbReflect.enumNameOptional(p, "getObjectAutonumerationMode");
    out.modalityUseMode = JaxbReflect.enumNameOptional(p, "getModalityUseMode");
    out.synchronousPlatformExtensionAndAddInCallUseMode =
      JaxbReflect.enumNameOptional(p, "getSynchronousPlatformExtensionAndAddInCallUseMode");
    out.interfaceCompatibilityMode = JaxbReflect.enumNameOptional(p, "getInterfaceCompatibilityMode");
    out.compatibilityMode =
      enumValue.of(JaxbReflect.enumNameOptional(p, "getCompatibilityMode"), "CompatibilityMode");
    out.configurationExtensionCompatibilityMode = enumValue.of(
      JaxbReflect.enumNameOptional(p, "getConfigurationExtensionCompatibilityMode"),
      "ConfigurationExtensionCompatibilityMode");
    return out;
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
    return fill(properties(DesignerXml.unmarshal(version, new ByteArrayInputStream(xmlBytes))), xmlBytes, version);
  }

  private static List<String> enumListToNames(Object fixedArray) {
    List<String> out = new ArrayList<>();
    if (fixedArray == null) {
      return out;
    }
    for (Object v : JaxbReflect.<Object>list(fixedArray, "getValue")) {
      if (v != null) {
        out.add(v.toString());
      }
    }
    return out;
  }

  private static void applyUsePurposes(SchemaVersion version, Object p, List<String> values) {
    Object fixedArray = JaxbReflect.getOptional(p, "getUsePurposes");
    if (fixedArray == null) {
      return;
    }
    List<Object> vals = JaxbReflect.list(fixedArray, "getValue");
    vals.clear();
    Class<?> enumClass;
    try {
      enumClass = Class.forName("io.github.yellowhammer.designerxml.jaxb." + version.name().toLowerCase()
        + USE_PURPOSE_ENUM);
    } catch (ClassNotFoundException e) {
      throw new IllegalStateException("нет класса ApplicationUsePurpose для " + version, e);
    }
    for (String v : safeTrimmedList(values)) {
      try {
        vals.add(Enum.valueOf(enumClass.asSubclass(Enum.class), v));
      } catch (IllegalArgumentException e) {
        // Пропустить значение молча нельзя: правка исчезла бы без следа, а на диск легла бы
        // конфигурация с другим назначением использования.
        throw new IllegalArgumentException(
          "UsePurposes: недопустимое значение " + v
            + "; допустимы " + java.util.Arrays.toString(enumClass.getEnumConstants()), e);
      }
    }
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

  /** Константы назначений использования из модели формата: без своей копии списка. */
  private static List<String> usePurposeOptions(SchemaVersion version) {
    try {
      Class<?> enumClass = Class.forName(
        "io.github.yellowhammer.designerxml.jaxb." + version.name().toLowerCase(java.util.Locale.ROOT)
          + ".v8_2_managed_application_core.ApplicationUsePurpose");
      List<String> out = new ArrayList<>();
      for (Object constant : enumClass.getEnumConstants()) {
        out.add(((Enum<?>) constant).name());
      }
      return out;
    } catch (ClassNotFoundException e) {
      return new ArrayList<>();
    }
  }
}
