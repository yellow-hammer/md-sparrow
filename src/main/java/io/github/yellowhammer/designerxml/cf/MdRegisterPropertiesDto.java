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
 * Поля {@code InformationRegisterProperties} и {@code AccumulationRegisterProperties} для
 * {@code cf-md-object-get/set} ({@code kind=informationRegister} и {@code kind=accumulationRegister}).
 *
 * <p>Виды регистров различаются лишь частью полей, поэтому DTO один: у регистра сведений пусты
 * поля оборотного регистра и наоборот. Enum-значения — имена Java-констант ({@code NONPERIODICAL},
 * {@code BALANCE}).
 */
public final class MdRegisterPropertiesDto {

  public String objectBelonging;
  public String extendedConfigurationObject;
  public boolean useStandardCommands;
  public String standardAttributesXml;
  public String defaultListForm;
  public String auxiliaryListForm;
  public boolean includeHelpInContents;
  public String help;
  public String recordSetModule;
  public String managerModule;
  public String dataLockControlMode;
  public String fullTextSearch;
  @LocalString
  public String listPresentation;
  @LocalString
  public String extendedListPresentation;
  @LocalString
  public String explanation;
  public String additionalIndexes;

  // Регистр сведений
  /** Способ редактирования: в списке, в диалоге, обоими способами. */
  public String editType;
  public String defaultRecordForm;
  public String auxiliaryRecordForm;
  public String informationRegisterPeriodicity;
  /** Режим записи: независимый либо подчинённый регистратору. */
  public String writeMode;
  public boolean mainFilterOnPeriod;
  public boolean enableTotalsSliceFirst;
  public boolean enableTotalsSliceLast;
  @LocalString
  public String recordPresentation;
  @LocalString
  public String extendedRecordPresentation;
  public String dataHistory;
  public boolean updateDataHistoryImmediatelyAfterWrite;
  public boolean executeAfterWriteDataHistoryVersionProcessing;

  // Регистр накопления
  /** Вид регистра: остатки либо обороты. */
  public String registerType;
  public boolean enableTotalsSplitting;
  public String aggregates;
}
