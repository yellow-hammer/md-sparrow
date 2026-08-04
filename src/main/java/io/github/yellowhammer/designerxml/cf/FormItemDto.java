/*
 * This file is a part of md-sparrow.
 *
 * SPDX-License-Identifier: LGPL-3.0-or-later
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

  /** Заголовок (ru). */
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

  /** Обработчики событий элемента. */
  public List<FormEventDto> events = new ArrayList<>();

  /** Вложенные элементы в порядке файла. */
  public List<FormItemDto> items = new ArrayList<>();
}
