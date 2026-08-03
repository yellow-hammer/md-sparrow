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
 * Поля {@code CommonPictureProperties} для {@code cf-md-object-get/set} ({@code kind=commonPicture}).
 * Файл картинки правится не панелью, здесь только признаки доступности.
 */
public final class MdCommonPicturePropertiesDto {

  public String objectBelonging;
  public String extendedConfigurationObject;
  public boolean availabilityForChoice;
  public boolean availabilityForAppearance;
}
