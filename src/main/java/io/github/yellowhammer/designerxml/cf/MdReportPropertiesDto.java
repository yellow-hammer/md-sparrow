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
 * Поля {@code ReportProperties} и {@code DataProcessorProperties} для
 * {@code cf-md-object-get/set} ({@code kind=report} и {@code kind=dataProcessor}).
 *
 * <p>Наборы свойств совпадают, кроме схемы компоновки, вариантов и хранилищ настроек,
 * которых у обработки нет: DTO один, у обработки такие поля пусты.
 */
public final class MdReportPropertiesDto {

  public String objectBelonging;
  public String extendedConfigurationObject;
  public boolean useStandardCommands;
  public String defaultForm;
  public String auxiliaryForm;
  public String objectModule;
  public String managerModule;
  public boolean includeHelpInContents;
  @LocalString
  public String extendedPresentation;
  @LocalString
  public String explanation;

  // Только отчёт
  /** Основная схема компоновки данных. */
  public String mainDataCompositionSchema;
  public String defaultSettingsForm;
  public String auxiliarySettingsForm;
  public String defaultVariantForm;
  public String auxiliaryVariantForm;
  /** Хранилище вариантов отчёта. */
  public String variantsStorage;
  /** Хранилище настроек отчёта. */
  public String settingsStorage;
}
