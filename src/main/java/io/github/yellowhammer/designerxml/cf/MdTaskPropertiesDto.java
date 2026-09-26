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
 * Поля {@code TaskProperties} для {@code cf-md-object-get/set} ({@code kind=task}).
 * Enum-значения — имена Java-констант ({@code STRING}, {@code BOTH_WAYS}).
 */
public final class MdTaskPropertiesDto {

  public String objectBelonging;
  public String extendedConfigurationObject;
  public boolean useStandardCommands;
  public String objectModule;
  public String managerModule;
  public String numberType;
  public String numberLength;
  public String numberAllowedLength;
  public boolean checkUnique;
  public boolean autonumbering;
  /** Автопрефикс номера: не использовать либо по реквизиту адресации. */
  public String taskNumberAutoPrefix;
  public String descriptionLength;
  /** Регистр сведений адресации ({@code InformationRegister.Имя}) либо пусто. */
  public String addressing;
  /** Основной реквизит адресации ({@code Task.Имя.AddressingAttribute.Имя}). */
  public String mainAddressingAttribute;
  /** Реквизит текущего исполнителя. */
  public String currentPerformer;
  public List<String> basedOn;
  public String standardAttributesXml;
  public String characteristicsXml;
  public String defaultPresentation;
  public String editType;
  public List<String> inputByString;
  public String searchStringModeOnInputByString;
  public String fullTextSearchOnInputByString;
  public String choiceDataGetModeOnInputByString;
  public String createOnInput;
  public String defaultObjectForm;
  public String defaultListForm;
  public String defaultChoiceForm;
  public String auxiliaryObjectForm;
  public String auxiliaryListForm;
  public String auxiliaryChoiceForm;
  public String choiceHistoryOnInput;
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

  public MdTaskPropertiesDto() {
    this.basedOn = new ArrayList<>();
    this.inputByString = new ArrayList<>();
    this.dataLockFields = new ArrayList<>();
  }
}
