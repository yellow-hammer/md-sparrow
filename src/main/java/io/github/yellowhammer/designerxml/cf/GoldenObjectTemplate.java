/*
 * This file is a part of md-sparrow.
 *
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package io.github.yellowhammer.designerxml.cf;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Параметризация эталона (golden) «голого» объекта метаданных для создания нового объекта той же версии:
 * подстановка имени объекта (только как целого токена) и детерминированный ремап всех UUID.
 *
 * <p>Это ядро scaffold по подтверждённой архитектуре: значения по умолчанию для версии берутся ТОЛЬКО
 * из выгрузки конфигуратора этой версии (в XSD их нет), а новый объект получается параметризацией эталона
 * — НЕ повторной сборкой через JAXB (это сохраняет байт-в-байт форматирование) и НЕ копированием
 * {@code empty-full} (там формы и лишние ссылки). Результат должен совпадать с эталоном объекта с другим
 * именем байт-в-байт.
 */
public final class GoldenObjectTemplate {

  private GoldenObjectTemplate() {
  }

  /**
   * @param goldenXml  текст эталона голого объекта (как выгрузил конфигуратор)
   * @param sourceName имя объекта в эталоне (например {@code Справочник1})
   * @param targetName требуемое имя нового объекта
   * @param uuidSeed   зерно детерминированного ремапа UUID (уникальное на объект)
   * @return XML нового объекта: имя заменено, UUID переремаплены детерминированно
   */
  public static String parametrize(String goldenXml, String sourceName, String targetName, String uuidSeed) {
    Objects.requireNonNull(goldenXml, "goldenXml");
    Objects.requireNonNull(uuidSeed, "uuidSeed");
    if (sourceName == null || sourceName.isEmpty()) {
      throw new IllegalArgumentException("sourceName required");
    }
    if (targetName == null || targetName.isEmpty()) {
      throw new IllegalArgumentException("targetName required");
    }
    CatalogNameConstraints.check(targetName);
    String renamed = replaceWholeNameToken(goldenXml, sourceName, targetName);
    return DistinctUuidRewrite.remapDeterministic(renamed, uuidSeed);
  }

  /**
   * Заменяет {@code sourceName} только там, где он является целым токеном-именем 1С (границы — любой символ,
   * не являющийся буквой/цифрой/подчёркиванием). Так {@code Справочник1} не заденет {@code Справочник11},
   * а имя в {@code <Name>…</Name>}, {@code CatalogObject.Имя}, {@code Catalog.Имя.StandardAttribute.…} —
   * заменится корректно.
   */
  private static String replaceWholeNameToken(String xml, String sourceName, String targetName) {
    Pattern token = Pattern.compile(
      "(?<![\\p{L}\\p{N}_])" + Pattern.quote(sourceName) + "(?![\\p{L}\\p{N}_])");
    return token.matcher(xml).replaceAll(Matcher.quoteReplacement(targetName));
  }
}
