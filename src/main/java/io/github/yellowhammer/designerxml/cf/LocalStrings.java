/*
 * This file is a part of md-sparrow.
 *
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package io.github.yellowhammer.designerxml.cf;

import java.util.Map;

/**
 * Многоязычная строка метаданных: синоним, подсказка, пояснение.
 *
 * <p>Строка записана парами язык-значение, а показать нужно одну. Язык
 * разработки у конфигурации свой, поэтому жёстко русский не годится: на
 * конфигурации с другим языком строка оказалась бы пустой.
 */
public final class LocalStrings {

  /** Язык, на котором написана большая часть конфигураций рынка. */
  private static final String RUSSIAN = "ru";

  private LocalStrings() {
  }

  /**
   * Значение многоязычной строки.
   *
   * @param byLanguage код языка -> текст, в порядке файла
   * @return текст на языке конфигурации, иначе русский, иначе первый записанный, иначе пусто
   */
  public static String pick(Map<String, String> byLanguage) {
    return pick(byLanguage, ConfigurationLanguage.current());
  }

  /**
   * Значение многоязычной строки на заданном языке.
   *
   * @param byLanguage код языка -> текст, в порядке файла
   * @param language язык конфигурации; пусто, если он неизвестен
   * @return текст на этом языке, иначе русский, иначе первый записанный, иначе пусто
   */
  public static String pick(Map<String, String> byLanguage, String language) {
    if (byLanguage == null || byLanguage.isEmpty()) {
      return null;
    }
    if (language != null && !language.isBlank()) {
      String wanted = byLanguage.get(language.trim());
      if (wanted != null) {
        return wanted;
      }
    }
    String russian = byLanguage.get(RUSSIAN);
    return russian != null ? russian : byLanguage.values().iterator().next();
  }
}
