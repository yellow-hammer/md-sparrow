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
 * Поля {@code CatalogProperties} (кроме {@code Name}, {@code Synonym}, {@code Comment} — они в
 * {@link MdObjectPropertiesDto}). Для JSON {@code cf-md-object-get/set}.
 *
 * <p>Фрагменты {@link #standardAttributesXml} и {@link #characteristicsXml} — сериализация
 * соответствующих узлов JAXB для полного round-trip сложных поддеревьев.
 */
public final class MdCatalogPropertiesDto {

  public String objectBelonging;
  public String extendedConfigurationObject;
  public boolean hierarchical;
  public String hierarchyType;
  public boolean limitLevelCount;
  /** Десятичное значение уровней (как в XML). */
  public String levelCount;
  public boolean foldersOnTop;
  public boolean useStandardCommands;
  public List<String> owners;
  public String subordinationUse;
  public String codeLength;
  public String descriptionLength;
  public String codeType;
  public String codeAllowedLength;
  public String codeSeries;
  public boolean checkUnique;
  public boolean autonumbering;
  public String defaultPresentation;
  /** Сериализованный узел {@code StandardAttributes}. */
  public String standardAttributesXml;
  /** Сериализованный узел {@code Characteristics}. */
  public String characteristicsXml;
  public String predefined;
  public String predefinedDataUpdate;
  public String editType;
  public boolean quickChoice;
  public String choiceMode;
  public List<String> inputByString;
  public String searchStringModeOnInputByString;
  public String fullTextSearchOnInputByString;
  public String choiceDataGetModeOnInputByString;
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
  public boolean includeHelpInContents;
  public String help;
  public List<String> basedOn;
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
  public String createOnInput;
  public String choiceHistoryOnInput;
  public String dataHistory;
  public boolean updateDataHistoryImmediatelyAfterWrite;
  public boolean executeAfterWriteDataHistoryVersionProcessing;
  public String additionalIndexes;

  public MdCatalogPropertiesDto() {
    this.owners = new ArrayList<>();
    this.basedOn = new ArrayList<>();
    this.inputByString = new ArrayList<>();
    this.dataLockFields = new ArrayList<>();
  }
}
