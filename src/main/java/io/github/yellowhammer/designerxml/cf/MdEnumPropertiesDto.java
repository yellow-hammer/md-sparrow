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
 * Поля {@code EnumProperties} для {@code cf-md-object-get/set} ({@code kind=enum}).
 * Enum-значения — имена Java-констант ({@code BOTH_WAYS}, {@code AUTO}).
 */
public final class MdEnumPropertiesDto {

  public String objectBelonging;
  public String extendedConfigurationObject;
  public boolean useStandardCommands;
  public String standardAttributesXml;
  public String characteristicsXml;
  public boolean quickChoice;
  public String choiceMode;
  public String defaultListForm;
  public String defaultChoiceForm;
  public String auxiliaryListForm;
  public String auxiliaryChoiceForm;
  public String managerModule;
  public String listPresentationRu;
  public String extendedListPresentationRu;
  public String explanationRu;
  public String choiceHistoryOnInput;
}
