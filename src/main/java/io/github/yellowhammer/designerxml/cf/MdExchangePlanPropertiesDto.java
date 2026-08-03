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
 * Поля {@code ExchangePlanProperties} для {@code cf-md-object-get/set}
 * ({@code kind=exchangePlan}).
 * Enum-значения — имена Java-констант ({@code VARIABLE}, {@code BOTH_WAYS}).
 *
 * <p>Состав плана обмена ({@code Content}) здесь не представлен: он правится отдельно,
 * при записи свойств остаётся нетронутым.
 */
public final class MdExchangePlanPropertiesDto {

  public String objectBelonging;
  public String extendedConfigurationObject;
  public boolean useStandardCommands;
  public String codeLength;
  public String codeAllowedLength;
  public String descriptionLength;
  /** Основное представление: в виде кода либо в виде наименования. */
  public String defaultPresentation;
  /** Способ редактирования: в списке, в диалоге, обоими способами. */
  public String editType;
  public boolean quickChoice;
  public String choiceMode;
  public List<String> inputByString;
  public String searchStringModeOnInputByString;
  public String fullTextSearchOnInputByString;
  public String choiceDataGetModeOnInputByString;
  public String choiceHistoryOnInput;
  public String createOnInput;
  public String defaultObjectForm;
  public String defaultListForm;
  public String defaultChoiceForm;
  public String auxiliaryObjectForm;
  public String auxiliaryListForm;
  public String auxiliaryChoiceForm;
  public String objectModule;
  public String managerModule;
  public String standardAttributesXml;
  public String characteristicsXml;
  public List<String> basedOn;
  /** Распределённая информационная база. */
  public boolean distributedInfoBase;
  public boolean includeConfigurationExtensions;
  public boolean includeHelpInContents;
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

  public MdExchangePlanPropertiesDto() {
    this.inputByString = new ArrayList<>();
    this.basedOn = new ArrayList<>();
    this.dataLockFields = new ArrayList<>();
  }
}
