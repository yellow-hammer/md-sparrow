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
 * Поля {@code CommonModuleProperties} для {@code cf-md-object-get/set} ({@code kind=commonModule}).
 * Enum-значения — имена Java-констант ({@code DONT_USE}).
 */
public final class MdCommonModulePropertiesDto {

  public String objectBelonging;
  public String extendedConfigurationObject;
  public boolean global;
  public boolean clientManagedApplication;
  public boolean server;
  public boolean externalConnection;
  public boolean clientOrdinaryApplication;
  public boolean client;
  public boolean serverCall;
  public boolean privileged;
  public String returnValuesReuse;
}
