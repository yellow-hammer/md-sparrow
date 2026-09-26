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
 * Элемент формы: тип, свойства раскладки и вложенные элементы.
 *
 * <p>Свойства объявлены у разных типов элементов по-разному, поэтому незаполненные поля означают
 * «у этого типа такого свойства нет либо оно не записано в файл».
 */
public final class FormItemDto {

  /** Имя элемента в XML: {@code UsualGroup}, {@code InputField}, {@code Table} и т.д. */
  public String type;

  public String name;

  /** Идентификатор элемента внутри формы. */
  public String id;

  /** Заголовок на языке конфигурации. */
  public String title;

  /** Путь к данным формы. */
  public String dataPath;

  /** Направление группы: {@code Vertical}, {@code Horizontal}. */
  public String group;

  /** Показывать заголовок: {@code true}, {@code false}, {@code auto}. */
  public String showTitle;

  /** Расположение заголовка. */
  public String titleLocation;

  /** Вид представления: страницы, таблица, кнопка и т.д. */
  public String representation;

  public Boolean visible;
  public Boolean enabled;
  public Boolean readOnly;

  public String width;
  public String height;

  /** Растяжение по горизонтали: {@code true}, {@code false}, {@code auto}. */
  public String horizontalStretch;

  /** Растяжение по вертикали. */
  public String verticalStretch;

  /** Представление значения списка выбора: текст рядом с переключателем и флажком. */
  public String choicePresentation;

  /** Свойства, записанные в файле: имя узла XML -> значение. Незаписанные платформа берёт по умолчанию. */
  public java.util.Map<String, String> properties = new java.util.LinkedHashMap<>();

  /** Обработчики событий элемента. */
  public List<FormEventDto> events = new ArrayList<>();

  /** Вложенные элементы в порядке файла. */
  public List<FormItemDto> items = new ArrayList<>();
}
