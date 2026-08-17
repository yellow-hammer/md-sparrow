/*
 * This file is a part of md-sparrow.
 *
 * SPDX-License-Identifier: LGPL-3.0-or-later
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
