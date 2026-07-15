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
  public String listPresentationRu;
  public String extendedListPresentationRu;
  public String explanationRu;
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
  public String recordPresentationRu;
  public String extendedRecordPresentationRu;
  public String dataHistory;
  public boolean updateDataHistoryImmediatelyAfterWrite;
  public boolean executeAfterWriteDataHistoryVersionProcessing;

  // Регистр накопления
  /** Вид регистра: остатки либо обороты. */
  public String registerType;
  public boolean enableTotalsSplitting;
  public String aggregates;
}
