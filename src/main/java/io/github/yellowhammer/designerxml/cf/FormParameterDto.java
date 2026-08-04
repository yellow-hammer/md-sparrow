/*
 * This file is a part of md-sparrow.
 *
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package io.github.yellowhammer.designerxml.cf;

/**
 * Параметр формы.
 */
public final class FormParameterDto {

  public String name;

  public MdTypeDescriptionDto type;

  /** Ключевой параметр. */
  public boolean key;
}
