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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Содержимое управляемой формы: дерево элементов и данные формы.
 */
public final class FormContentDto {

  /** Заголовок формы на языке конфигурации. */
  public String title;

  /**
   * Свойства самой формы, записанные в корне файла: имя узла XML -> значение.
   *
   * <p>Отдаются отдельно от свойств элементов: положение командной панели, автозаголовок, режим
   * сохранения данных и прочее записаны у корня {@code Form}, а не у какого-либо элемента.
   */
  public Map<String, String> properties = new LinkedHashMap<>();

  /** Команды, исключённые из состава формы ({@code CommandSet/ExcludedCommand}). */
  public List<String> excludedCommands = new ArrayList<>();

  /** Дерево элементов формы в порядке файла. */
  public List<FormItemDto> items = new ArrayList<>();

  /** Реквизиты формы. */
  public List<FormAttributeDto> attributes = new ArrayList<>();

  /** Команды формы. */
  public List<FormCommandDto> commands = new ArrayList<>();

  /** Параметры формы. */
  public List<FormParameterDto> parameters = new ArrayList<>();

  /** Обработчики событий формы. */
  public List<FormEventDto> events = new ArrayList<>();
}
