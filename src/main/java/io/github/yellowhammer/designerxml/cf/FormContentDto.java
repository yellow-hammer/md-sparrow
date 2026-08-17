/*
 * This file is a part of md-sparrow.
 *
 * SPDX-License-Identifier: LGPL-3.0-or-later
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

  /** Заголовок формы (ru). */
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
