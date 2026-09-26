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
 * Поля {@code ChartOfAccountsProperties} для {@code cf-md-object-get/set}
 * ({@code kind=chartOfAccounts}).
 * Enum-значения — имена Java-констант ({@code AS_CODE}, {@code BOTH_WAYS}).
 */
public final class MdChartOfAccountsPropertiesDto {

  public String objectBelonging;
  public String extendedConfigurationObject;
  public boolean useStandardCommands;
  public boolean includeHelpInContents;
  public List<String> basedOn;
  /** Виды субконто: план видов характеристик либо пусто. */
  public String extDimensionTypes;
  /** Максимальное количество субконто. */
  public String maxExtDimensionCount;
  public String codeMask;
  public String codeLength;
  public String descriptionLength;
  /** Серии кодов: во всём плане счетов либо в пределах подчинения. */
  public String codeSeries;
  public boolean checkUnique;
  public String defaultPresentation;
  public String standardAttributesXml;
  public String characteristicsXml;
  public String predefinedDataUpdate;
  public String editType;
  public boolean quickChoice;
  public String choiceMode;
  public List<String> inputByString;
  public String searchStringModeOnInputByString;
  public String fullTextSearchOnInputByString;
  public String choiceDataGetModeOnInputByString;
  public String createOnInput;
  public String choiceHistoryOnInput;
  public String defaultObjectForm;
  public String defaultListForm;
  public String defaultChoiceForm;
  public String auxiliaryObjectForm;
  public String auxiliaryListForm;
  public String auxiliaryChoiceForm;
  public String objectModule;
  public String managerModule;
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

  public MdChartOfAccountsPropertiesDto() {
    this.basedOn = new ArrayList<>();
    this.inputByString = new ArrayList<>();
    this.dataLockFields = new ArrayList<>();
  }
}
