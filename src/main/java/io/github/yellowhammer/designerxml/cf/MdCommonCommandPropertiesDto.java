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

/**
 * Поля {@code CommonCommandProperties} для {@code cf-md-object-get/set} ({@code kind=commonCommand}).
 * Группа команды и тип параметра решают, где команда появится.
 */
public final class MdCommonCommandPropertiesDto {

  public String objectBelonging;
  public String extendedConfigurationObject;
  public String group;
  public String representation;
  @LocalString
  public String toolTip;
  public String shortcut;
  public String commandModule;
  public boolean includeHelpInContents;
  public MdTypeDescriptionDto commandParameterType;
  public String parameterUseMode;
  public boolean modifiesData;
  public String onMainServerUnavalableBehavior;
}
