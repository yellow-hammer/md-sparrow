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
  @LocalString
  public String synonym;
  /** Язык, на котором прочитаны и будут записаны тексты конфигурации. */
  public String languageCode;
  /** Свойства, за которыми стоит язык: панель подписывает их языком. */
  public List<String> localStringProperties;
  public String comment;

  public String defaultRunMode;
  public List<String> usePurposes;
  /** Допустимые назначения использования: константы модели текущей версии формата. */
  public List<String> usePurposeOptions;
  public String scriptVariant;
  public List<String> defaultRoles;

  public String managedApplicationModule;
  public String sessionModule;
  public String externalConnectionModule;

  @LocalString
  public String briefInformation;
  @LocalString
  public String detailedInformation;
  @LocalString
  public String copyright;
  @LocalString
  public String vendorInformationAddress;
  @LocalString
  public String configurationInformationAddress;

  public String vendor;
  public String version;
  public String updateCatalogAddress;

  public String dataLockControlMode;
  public String objectAutonumerationMode;
  public String modalityUseMode;
  public String synchronousPlatformExtensionAndAddInCallUseMode;
  public String interfaceCompatibilityMode;
  public String compatibilityMode;
  /** Режим совместимости расширения; у конфигурации свойство тоже есть. */
  public String configurationExtensionCompatibilityMode;

  public ConfigurationPropertiesDto() {
    this.usePurposes = new ArrayList<>();
    this.usePurposeOptions = new ArrayList<>();
    this.defaultRoles = new ArrayList<>();
  }
}
