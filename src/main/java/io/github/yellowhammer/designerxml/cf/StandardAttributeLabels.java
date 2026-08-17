/*
 * This file is a part of md-sparrow.
 *
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package io.github.yellowhammer.designerxml.cf;

import java.util.Map;

/**
 * Подписи стандартных реквизитов, которые ставит сама платформа.
 *
 * <p>Синоним стандартного реквизита конфигуратор пишет в файл только переопределённым: у обычного
 * в {@code <xr:Synonym/>} пусто, а подпись на форме всё равно есть. Модель формата её не объявляет:
 * XSD описывает состав узлов, а не то, как платформа их называет.
 *
 * <p>Поэтому словарь живёт здесь и наполняется только сверенным с конфигуратором. Стандартный
 * реквизит, которого в словаре нет, остаётся без подписи, и вызывающая сторона берёт имя.
 */
public final class StandardAttributeLabels {

  /**
   * Подписи стандартных реквизитов табличной части.
   *
   * <p>Они есть у любой табличной части, даже когда файл их не перечисляет: узел
   * {@code StandardAttributes} конфигуратор пишет только с изменёнными настройками.
   */
  private static final Map<String, String> TABULAR_SECTION = Map.of("LineNumber", "N");

  private StandardAttributeLabels() {
  }

  /**
   * Подписи стандартных реквизитов табличной части.
   *
   * @return имя стандартного реквизита -> подпись
   */
  public static Map<String, String> ofTabularSection() {
    return TABULAR_SECTION;
  }
}
