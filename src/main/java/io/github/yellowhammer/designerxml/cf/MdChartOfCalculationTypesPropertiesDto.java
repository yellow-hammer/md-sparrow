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
 * Поля {@code ChartOfCalculationTypesProperties} для {@code cf-md-object-get/set}
 * ({@code kind=chartOfCalculationTypes}).
 * Enum-значения — имена Java-констант ({@code STRING}, {@code BOTH_WAYS}).
 */
public final class MdChartOfCalculationTypesPropertiesDto {

  public String objectBelonging;
  public String extendedConfigurationObject;
  public boolean useStandardCommands;
  public String codeLength;
  public String descriptionLength;
  public String codeType;
  public String codeAllowedLength;
  public String editType;
  public List<String> inputByString;
  public String createOnInput;
  public String searchStringModeOnInputByString;
  public String choiceDataGetModeOnInputByString;
  public String fullTextSearchOnInputByString;
  public String choiceHistoryOnInput;
  public String defaultObjectForm;
  public String defaultListForm;
  public String defaultChoiceForm;
  public String auxiliaryObjectForm;
  public String auxiliaryListForm;
  public String auxiliaryChoiceForm;
  public String objectModule;
  public String managerModule;
  public List<String> basedOn;
  /** Зависимость от видов расчёта: не зависит, по периоду действия, по периоду регистрации. */
  public String dependenceOnCalculationTypes;
  /** Базовые виды расчёта: ссылки на планы видов расчёта. */
  public List<String> baseCalculationTypes;
  /** Использование периода действия. */
  public boolean actionPeriodUse;
  public String standardAttributesXml;
  public String characteristicsXml;
  public String predefinedDataUpdate;
  public boolean includeHelpInContents;
  public List<String> dataLockFields;
  public String dataLockControlMode;
  public String fullTextSearch;
  @LocalString
  public String objectPresentation;
  @LocalString
  public String extendedObjectPresentation;
  @LocalString
  public String listPresentation;
  @LocalString
  public String extendedListPresentation;
  @LocalString
  public String explanation;
  public String dataHistory;
  public boolean updateDataHistoryImmediatelyAfterWrite;
  public boolean executeAfterWriteDataHistoryVersionProcessing;
  public String additionalIndexes;

  public MdChartOfCalculationTypesPropertiesDto() {
    this.inputByString = new ArrayList<>();
    this.basedOn = new ArrayList<>();
    this.baseCalculationTypes = new ArrayList<>();
    this.dataLockFields = new ArrayList<>();
  }
}
