/*
 * This file is a part of md-sparrow.
 *
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package io.github.yellowhammer.designerxml.cf;

import java.util.ArrayList;
import java.util.List;

/**
 * DTO свойств корневой Configuration.xml для webview (чтение/запись через JSON).
 */
public final class ConfigurationPropertiesDto {

  public String name;
  public String synonymRu;
  public String comment;

  public String defaultRunMode;
  public List<String> usePurposes;
  public String scriptVariant;
  public List<String> defaultRoles;

  public String managedApplicationModule;
  public String sessionModule;
  public String externalConnectionModule;

  public String briefInformationRu;
  public String detailedInformationRu;
  public String copyrightRu;
  public String vendorInformationAddressRu;
  public String configurationInformationAddressRu;

  public String vendor;
  public String version;
  public String updateCatalogAddress;

  public String dataLockControlMode;
  public String objectAutonumerationMode;
  public String modalityUseMode;
  public String synchronousPlatformExtensionAndAddInCallUseMode;
  public String interfaceCompatibilityMode;
  public String compatibilityMode;

  public ConfigurationPropertiesDto() {
    this.usePurposes = new ArrayList<>();
    this.defaultRoles = new ArrayList<>();
  }
}
