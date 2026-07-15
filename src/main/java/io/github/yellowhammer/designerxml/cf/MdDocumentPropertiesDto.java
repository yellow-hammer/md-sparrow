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
 * Поля {@code DocumentProperties} для {@code cf-md-object-get/set} ({@code kind=document}).
 * Enum-значения — имена Java-констант ({@code ALLOW}, {@code NONPERIODICAL}).
 */
public final class MdDocumentPropertiesDto {

  public String objectBelonging;
  public String extendedConfigurationObject;
  public boolean useStandardCommands;
  /** Ссылка на нумератор ({@code DocumentNumerator.Имя}) либо пустая строка. */
  public String numerator;
  public String numberType;
  public String numberLength;
  public String numberAllowedLength;
  public String numberPeriodicity;
  public boolean checkUnique;
  public boolean autonumbering;
  public String standardAttributesXml;
  public String characteristicsXml;
  public List<String> basedOn;
  public List<String> inputByString;
  public String createOnInput;
  public String searchStringModeOnInputByString;
  public String fullTextSearchOnInputByString;
  public String choiceDataGetModeOnInputByString;
  public String choiceHistoryOnInput;
  public String defaultObjectForm;
  public String defaultListForm;
  public String defaultChoiceForm;
  public String auxiliaryObjectForm;
  public String auxiliaryListForm;
  public String auxiliaryChoiceForm;
  public String objectModule;
  public String managerModule;
  public String posting;
  public String realTimePosting;
  public String registerRecordsDeletion;
  public String registerRecordsWritingOnPost;
  public String sequenceFilling;
  /** Состав движений: ссылки на регистры ({@code AccumulationRegister.Имя} и т. п.). */
  public List<String> registerRecords;
  public boolean postInPrivilegedMode;
  public boolean unpostInPrivilegedMode;
  public boolean includeHelpInContents;
  public String help;
  public List<String> dataLockFields;
  public String dataLockControlMode;
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

  public MdDocumentPropertiesDto() {
    this.basedOn = new ArrayList<>();
    this.inputByString = new ArrayList<>();
    this.registerRecords = new ArrayList<>();
    this.dataLockFields = new ArrayList<>();
  }
}
