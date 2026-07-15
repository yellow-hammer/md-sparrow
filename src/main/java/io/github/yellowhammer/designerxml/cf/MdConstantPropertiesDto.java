/*
 * This file is a part of md-sparrow.
 *
 * Copyright (c) 2026
 * Ivan Karlo <i.karlo@outlook.com> and contributors
 *
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package io.github.yellowhammer.designerxml.cf;

/**
 * Поля {@code ConstantProperties} для {@code cf-md-object-get/set} ({@code kind=constant}).
 * Enum-значения — имена Java-констант ({@code DONT_CHECK}, {@code AUTO}).
 *
 * <p>Границы, параметры выбора и связь по типу здесь не представлены: при записи они остаются
 * нетронутыми.
 */
public final class MdConstantPropertiesDto {

  public String objectBelonging;
  public String extendedConfigurationObject;
  /** Описание типа значения константы. */
  public MdTypeDescriptionDto type;
  public boolean useStandardCommands;
  public String defaultForm;
  public String extendedPresentationRu;
  public String explanationRu;
  public boolean passwordMode;
  public String formatRu;
  public String editFormatRu;
  public String toolTipRu;
  public boolean markNegatives;
  public String mask;
  public boolean multiLine;
  public boolean extendedEdit;
  public String fillChecking;
  public String choiceFoldersAndItems;
  public String quickChoice;
  public String choiceForm;
  public String choiceHistoryOnInput;
  public String valueManagerModule;
  public String managerModule;
  public String dataLockControlMode;
  public String dataHistory;
  public boolean updateDataHistoryImmediatelyAfterWrite;
  public boolean executeAfterWriteDataHistoryVersionProcessing;
}
