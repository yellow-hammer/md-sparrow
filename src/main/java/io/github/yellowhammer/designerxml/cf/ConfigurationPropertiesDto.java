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
