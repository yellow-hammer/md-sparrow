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
 * Поля {@code CommonAttributeProperties} для {@code cf-md-object-get/set} ({@code kind=commonAttribute}).
 * Разделение данных и автоиспользование решают, где реквизит появится.
 */
public final class MdCommonAttributePropertiesDto {

  public String objectBelonging;
  public String extendedConfigurationObject;
  public MdTypeDescriptionDto type;
  public String autoUse;
  public String dataSeparation;
  public String separatedDataUse;
  public String dataSeparationValue;
  public String dataSeparationUse;
  public String conditionalSeparation;
  public String usersSeparation;
  public String authenticationSeparation;
  public String configurationExtensionsSeparation;
  public String indexing;
  public String fullTextSearch;
  public String dataHistory;
  public String toolTipRu;
  public boolean passwordMode;
  public boolean multiLine;
  public String mask;
  public String quickChoice;
  public String createOnInput;
  public String choiceHistoryOnInput;
  public String fillChecking;
  public String choiceForm;
}
