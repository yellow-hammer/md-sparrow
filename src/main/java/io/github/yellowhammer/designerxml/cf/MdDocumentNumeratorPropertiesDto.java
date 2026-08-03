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
 * Поля {@code DocumentNumeratorProperties} для {@code cf-md-object-get/set} ({@code kind=documentNumerator}).
 * Нумерация общая для документов, которые ссылаются на нумератор.
 */
public final class MdDocumentNumeratorPropertiesDto {

  public String objectBelonging;
  public String extendedConfigurationObject;
  public String numberType;
  public String numberLength;
  public String numberAllowedLength;
  public String numberPeriodicity;
  public boolean checkUnique;
}
