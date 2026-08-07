/*
 * This file is a part of md-sparrow.
 *
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package io.github.yellowhammer.designerxml.cf;

import java.util.ArrayList;
import java.util.List;

/**
 * Содержимое управляемой формы: дерево элементов и данные формы.
 */
public final class FormContentDto {

  /** Заголовок формы (ru). */
  public String title;

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
