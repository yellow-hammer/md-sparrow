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
 * Поля {@code ChartOfCharacteristicTypesProperties} для {@code cf-md-object-get/set}
 * ({@code kind=chartOfCharacteristicTypes}).
 * Enum-значения — имена Java-констант ({@code VARIABLE}, {@code BOTH_WAYS}).
 */
public final class MdChartOfCharacteristicTypesPropertiesDto {

  public String objectBelonging;
  public String extendedConfigurationObject;
  public boolean useStandardCommands;
  public boolean includeHelpInContents;
  /** Дополнительные значения характеристик: ссылка на справочник либо пусто. */
  public String characteristicExtValues;
  /** Тип значения характеристики. */
  public MdTypeDescriptionDto type;
  public boolean hierarchical;
  public boolean foldersOnTop;
  public String codeLength;
  public String codeAllowedLength;
  public String descriptionLength;
  /** Серии кодов: во всём плане либо в пределах подчинения. */
  public String codeSeries;
  public boolean checkUnique;
  public boolean autonumbering;
  public String defaultPresentation;
  public String standardAttributesXml;
  public String characteristicsXml;
  public String predefinedDataUpdate;
  public String editType;
  public boolean quickChoice;
  public String choiceMode;
  public List<String> inputByString;
  public String createOnInput;
  public String searchStringModeOnInputByString;
  public String choiceDataGetModeOnInputByString;
  public String fullTextSearchOnInputByString;
  public String choiceHistoryOnInput;
  public String defaultObjectForm;
  public String defaultFolderForm;
  public String defaultListForm;
  public String defaultChoiceForm;
  public String defaultFolderChoiceForm;
  public String auxiliaryObjectForm;
  public String auxiliaryFolderForm;
  public String auxiliaryListForm;
  public String auxiliaryChoiceForm;
  public String auxiliaryFolderChoiceForm;
  public String objectModule;
  public String managerModule;
  public List<String> basedOn;
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

  public MdChartOfCharacteristicTypesPropertiesDto() {
    this.inputByString = new ArrayList<>();
    this.basedOn = new ArrayList<>();
    this.dataLockFields = new ArrayList<>();
  }
}
