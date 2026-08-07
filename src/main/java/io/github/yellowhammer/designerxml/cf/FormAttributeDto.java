/*
 * This file is a part of md-sparrow.
 *
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package io.github.yellowhammer.designerxml.cf;

import java.util.ArrayList;
import java.util.List;

/**
 * Реквизит формы; у реквизита-таблицы заполнены колонки.
 */
public final class FormAttributeDto {

  public String name;

  /** Заголовок (ru). */
  public String title;

  public MdTypeDescriptionDto type;

  /** Основной реквизит формы. */
  public boolean main;

  /** Колонки реквизита-таблицы. */
  public List<FormAttributeDto> columns = new ArrayList<>();
}
