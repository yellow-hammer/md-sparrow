/*
 * This file is a part of md-sparrow.
 *
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package io.github.yellowhammer.designerxml.cf;

import io.github.yellowhammer.designerxml.SchemaVersion;
import jakarta.xml.bind.JAXBException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

public final class MdObjectAdd {

  private MdObjectAdd() {
  }

  public static void add(Path configurationXml, String objectName, SchemaVersion version, MdObjectAddType type)
    throws IOException, JAXBException {
    CatalogNameConstraints.check(objectName);
    Path cfRoot = configurationXml.getParent();
    if (cfRoot == null || !Files.isRegularFile(configurationXml)) {
      throw new IllegalArgumentException("configuration XML must exist: " + configurationXml);
    }

    String name = resolveNonConflictingName(configurationXml, version, type, cfRoot, objectName);
    Path out = CfLayout.objectXmlInSubdir(cfRoot, type.cfSubdir(), name);
    if (Files.exists(out)) {
      throw new IllegalArgumentException("object file already exists: " + out);
    }

    String text = generateObjectXml(type, name, version);
    Files.createDirectories(out.getParent());
    Files.writeString(out, text, StandardCharsets.UTF_8);
    if (type.roleWithExtRights()) {
      writeRoleRights(cfRoot, name, version);
    }
    ConfigurationChildObjectAppender.append(configurationXml, type.configurationXmlTag(), name);
  }

  private static String resolveNonConflictingName(
    Path configurationXml, SchemaVersion version, MdObjectAddType type, Path cfRoot, String objectName)
    throws IOException, JAXBException {
    Set<String> taken = MdObjectAddNextName.mergeTakenNames(configurationXml, version, type, cfRoot);
    if (!taken.contains(objectName)) {
      Path out = CfLayout.objectXmlInSubdir(cfRoot, type.cfSubdir(), objectName);
      if (!Files.exists(out)) {
        return objectName;
      }
    }
    return MdObjectAddNextName.nextFreeName(configurationXml, version, type, cfRoot);
  }

  private static void writeRoleRights(Path cfRoot, String roleName, SchemaVersion version) throws IOException {
    Path rightsXml = CfLayout.roleExtRightsXml(cfRoot, roleName);
    Files.createDirectories(rightsXml.getParent());
    Files.writeString(rightsXml, RoleRightsXmlWriter.generate(version, CfLayout.DEFAULT_CONFIGURATION_NAME), StandardCharsets.UTF_8);
  }

  private static String generateObjectXml(MdObjectAddType type, String name, SchemaVersion version)
    throws JAXBException, IOException {
    return switch (type) {
      case ENUM -> NewEnumXml.generate(name, version);
      case CONSTANT -> NewConstantXml.generate(name, version);
      case DOCUMENT -> NewDocumentXml.generate(name, version);
      case REPORT -> NewReportXml.generate(name, version);
      case DATA_PROCESSOR -> NewDataProcessorXml.generate(name, version);
      case TASK -> NewTaskXml.generate(name, version);
      case CHART_OF_ACCOUNTS -> NewChartOfAccountsXml.generate(name, version);
      case CHART_OF_CHARACTERISTIC_TYPES -> NewChartOfCharacteristicTypesXml.generate(name, version);
      case CHART_OF_CALCULATION_TYPES -> NewChartOfCalculationTypesXml.generate(name, version);
      case COMMON_MODULE -> NewCommonModuleXml.generate(name, version);
      case SUBSYSTEM -> NewSubsystemXml.generate(name, version);
      case SESSION_PARAMETER -> NewSessionParameterXml.generate(name, version);
      case EXCHANGE_PLAN -> NewExchangePlanXml.generate(name, version);
      case COMMON_ATTRIBUTE -> NewCommonAttributeXml.generate(name, version);
      case COMMON_PICTURE -> NewCommonPictureXml.generate(name, version);
      case DOCUMENT_NUMERATOR -> NewDocumentNumeratorXml.generate(name, version);
      case EXTERNAL_DATA_SOURCE -> NewExternalDataSourceXml.generate(name, version);
      case ROLE -> NewRoleXml.generate(name, version);
    };
  }
}
