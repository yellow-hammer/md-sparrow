/*
 * This file is a part of md-sparrow.
 *
 * Copyright (c) 2026
 * Ivan Karlo <i.karlo@outlook.com> and contributors
 *
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package io.github.yellowhammer.designerxml.cf;

/**
 * Поля {@code CommonCommandProperties} для {@code cf-md-object-get/set} ({@code kind=commonCommand}).
 * Группа команды и тип параметра решают, где команда появится.
 */
public final class MdCommonCommandPropertiesDto {

  public String objectBelonging;
  public String extendedConfigurationObject;
  public String group;
  public String representation;
  public String toolTipRu;
  public String shortcut;
  public String commandModule;
  public boolean includeHelpInContents;
  public MdTypeDescriptionDto commandParameterType;
  public String parameterUseMode;
  public boolean modifiesData;
  public String onMainServerUnavalableBehavior;
}
