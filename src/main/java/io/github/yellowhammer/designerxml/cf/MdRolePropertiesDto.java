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
 * Поля {@code RoleProperties} для {@code cf-md-object-get/set} ({@code kind=role}).
 * Состав прав живёт отдельным файлом Rights.xml и здесь не правится.
 */
public final class MdRolePropertiesDto {

  public String objectBelonging;
  public String extendedConfigurationObject;
}
