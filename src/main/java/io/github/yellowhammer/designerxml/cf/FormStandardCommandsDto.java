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

import java.util.List;
import java.util.Map;

/**
 * Что платформа знает про стандартные команды формы, а файл формы не пишет.
 *
 * <p>Кнопка ссылается на команду именем {@code Form.StandardCommand.<Имя>}: ни подписи, ни стороны
 * панели у такой кнопки в файле нет. И то и другое снято с конфигуратора.
 */
public final class FormStandardCommandsDto {

  /** Подписи: имя команды без приставки -&gt; подпись, которую ставит платформа. */
  public Map<String, String> labels;

  /** То же для стандартных команд таблицы: у них своя приставка и свои подписи. */
  public Map<String, String> tableLabels;

  /** Команды, которые стоят у правого края панели, в том порядке, в каком идут там. */
  public List<String> atRight;

  /** Чем платформа наполняет панель: вид главного реквизита формы -&gt; кнопки по порядку. */
  public Map<String, List<FormStandardCommandDto>> autoCommandBar;

  /** Чем платформа наполняет панель таблицы: вид данных таблицы -&gt; кнопки по порядку. */
  public Map<String, List<FormStandardCommandDto>> tableCommandBar;

  /** Версия платформы, с которой снят словарь. */
  public String platformVersion;
}
