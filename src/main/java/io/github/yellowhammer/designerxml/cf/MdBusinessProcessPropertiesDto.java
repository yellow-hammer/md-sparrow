/*
 * This file is a part of md-sparrow.
 *
 * Copyright (c) 2026
 * Ivan Karlo <i.karlo@outlook.com> and contributors
 *
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package io.github.yellowhammer.designerxml.cf;

import java.util.ArrayList;
import java.util.List;

/**
 * Поля {@code BusinessProcessProperties} для {@code cf-md-object-get/set}
 * ({@code kind=businessProcess}).
 * Enum-значения — имена Java-констант ({@code NUMBER}, {@code BOTH_WAYS}).
 */
public final class MdBusinessProcessPropertiesDto {

  public String objectBelonging;
  public String extendedConfigurationObject;
  public boolean useStandardCommands;
  public String objectModule;
  public String managerModule;
  /** Карта маршрута бизнес-процесса. */
  public String flowchart;
  public String editType;
  public List<String> inputByString;
  public String createOnInput;
  public String searchStringModeOnInputByString;
  public String choiceDataGetModeOnInputByString;
  public String fullTextSearchOnInputByString;
  public String defaultObjectForm;
  public String defaultListForm;
  public String defaultChoiceForm;
  public String auxiliaryObjectForm;
  public String auxiliaryListForm;
  public String auxiliaryChoiceForm;
  public String choiceHistoryOnInput;
  public String numberType;
  public String numberLength;
  public String numberAllowedLength;
  public boolean checkUnique;
  public String standardAttributesXml;
  public String characteristicsXml;
  public boolean autonumbering;
  public List<String> basedOn;
  public String numberPeriodicity;
  /** Задача бизнес-процесса ({@code Task.Имя}). */
  public String task;
  public boolean createTaskInPrivilegedMode;
  public List<String> dataLockFields;
  public String dataLockControlMode;
  public boolean includeHelpInContents;
  public String fullTextSearch;
  public String objectPresentationRu;
  public String extendedObjectPresentationRu;
  public String listPresentationRu;
  public String extendedListPresentationRu;
  public String explanationRu;
  public String dataHistory;
  public boolean updateDataHistoryImmediatelyAfterWrite;
  public boolean executeAfterWriteDataHistoryVersionProcessing;
  public String additionalIndexes;

  public MdBusinessProcessPropertiesDto() {
    this.inputByString = new ArrayList<>();
    this.basedOn = new ArrayList<>();
    this.dataLockFields = new ArrayList<>();
  }
}
