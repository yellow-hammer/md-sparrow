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
 * Поля {@code ScheduledJobProperties} для {@code cf-md-object-get/set} ({@code kind=scheduledJob}).
 * Расписание хранится строкой формата платформы и правится как есть.
 */
public final class MdScheduledJobPropertiesDto {

  public String objectBelonging;
  public String extendedConfigurationObject;
  public String methodName;
  public String description;
  public String key;
  public String schedule;
  public boolean use;
  public boolean predefined;
  public String restartCountOnFailure;
  public String restartIntervalOnFailure;
}
