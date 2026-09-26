/*
 * This file is a part of md-sparrow.
 *
 * Copyright (c) 2026
 * Ivan Karlo <i.karlo@outlook.com> and contributors
 *
 * SPDX-License-Identifier: LGPL-3.0-or-later
 *
 * md-sparrow is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3.0 of the License, or (at your option) any later version.
 *
 * md-sparrow is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with md-sparrow.
 */
package io.github.yellowhammer.designerxml.cf;

import java.util.ArrayList;
import java.util.List;

/**
 * Реквизит формы; у реквизита-таблицы заполнены колонки.
 */
public final class FormAttributeDto {

  public String name;

  /** Заголовок на языке конфигурации. */
  public String title;

  public MdTypeDescriptionDto type;

  /** Основной реквизит формы. */
  public boolean main;

  /** Колонки реквизита-таблицы. */
  public List<FormAttributeDto> columns = new ArrayList<>();

  /**
   * Основная таблица динамического списка: {@code InformationRegister.ЗамерыОбластиСтатистики}.
   *
   * <p>Поля списка идут по её реквизитам, измерениям и ресурсам, а подписывает их платформа
   * синонимами оттуда же.
   */
  public String mainTable;
}
