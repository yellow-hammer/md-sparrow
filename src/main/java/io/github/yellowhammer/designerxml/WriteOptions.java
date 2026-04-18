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
package io.github.yellowhammer.designerxml;

import java.nio.file.Path;

/**
 * Параметры записи XML.
 */
public final class WriteOptions {

  private final boolean formatPretty;
  private final boolean oneCNamespacePrefixes;
  private final boolean normalizeLineEndings;
  private final Path preserveRootNamespacesFrom;

  private WriteOptions(Builder b) {
    this.formatPretty = b.formatPretty;
    this.oneCNamespacePrefixes = b.oneCNamespacePrefixes;
    this.normalizeLineEndings = b.normalizeLineEndings;
    this.preserveRootNamespacesFrom = b.preserveRootNamespacesFrom;
  }

  /**
   * Признак «красивой» печати XML marshaller'ом.
   *
   * @return {@code true}, если включены отступы и переносы строк
   */
  public boolean formatPretty() {
    return formatPretty;
  }

  /**
   * Префиксы пространств имён как в выгрузке конфигуратора ({@code xmlns}, {@code xr:}, {@code v8:} и т.д.).
   *
   * @return {@code true}, если включено сопоставление через {@link OneCDesignerXmlNamespacePrefixMapper}
   */
  public boolean oneCNamespacePrefixes() {
    return oneCNamespacePrefixes;
  }

  /**
   * Нормализация переводов строк к {@code \n} после маршалинга.
   *
   * @return {@code true}, если включена пост-обработка line endings
   */
  public boolean normalizeLineEndings() {
    return normalizeLineEndings;
  }

  /**
   * Если задано, после marshaller'а на корневой {@code MetaDataObject} дополнительно переносятся объявления
   * {@code xmlns} с этого файла (объединение с тем, что вывел JAXB).
   *
   * @return путь к исходному .xml до перезаписи или {@code null}
   */
  public Path preserveRootNamespacesFrom() {
    return preserveRootNamespacesFrom;
  }

  /**
   * Набор по умолчанию: {@linkplain #formatPretty() форматирование} и
   * {@linkplain #oneCNamespacePrefixes() префиксы 1С} включены.
   *
   * @return экземпляр с настройками по умолчанию
   */
  public static WriteOptions defaults() {
    return builder().build();
  }

  /**
   * Запись при редактировании свойств объекта метаданных ({@code cf-md-object-set}, формы в IDE): человекочитаемый
   * XML marshaller'ом; корневые {@code xmlns} дополняются с исходного файла. No-op и точечные патчи не проходят
   * через полную запись — лишний шум отступами не добавляется там, где файл не переписывается.
   *
   * @param preserveRootNamespacesFrom тот же путь к .xml, что перезаписывается (до записи на диске ещё старый файл)
   */
  public static WriteOptions forMdObjectEdit(Path preserveRootNamespacesFrom) {
    return builder()
      .formatPretty(true)
      .preserveRootNamespacesFrom(preserveRootNamespacesFrom)
      .build();
  }

  /**
   * Создаёт builder для пошаговой настройки.
   *
   * @return новый {@link Builder}
   */
  public static Builder builder() {
    return new Builder();
  }

  /** Построение {@link WriteOptions}. */
  public static final class Builder {

    private boolean formatPretty = true;
    private boolean oneCNamespacePrefixes = true;
    private boolean normalizeLineEndings = true;
    private Path preserveRootNamespacesFrom;

    /**
     * Включить или отключить форматирование (строки/отступы).
     *
     * @param formatPretty {@code true} — человекочитаемый XML
     * @return {@code this}
     */
    public Builder formatPretty(boolean formatPretty) {
      this.formatPretty = formatPretty;
      return this;
    }

    /**
     * Префиксы как в выгрузке 1С (по умолчанию {@code true}).
     *
     * @param oneCNamespacePrefixes {@code false} — поведение marshaller без маппера (например, {@code ns2:}…)
     * @return {@code this}
     */
    public Builder oneCNamespacePrefixes(boolean oneCNamespacePrefixes) {
      this.oneCNamespacePrefixes = oneCNamespacePrefixes;
      return this;
    }

    /**
     * Нормализовать переводы строк к {@code \n}.
     *
     * @param normalizeLineEndings {@code true} — CRLF/CR приводятся к LF
     * @return {@code this}
     */
    public Builder normalizeLineEndings(boolean normalizeLineEndings) {
      this.normalizeLineEndings = normalizeLineEndings;
      return this;
    }

    /**
     * Дополнять корень объявлениями {@code xmlns} с указанного файла (как у конфигуратора до правки).
     *
     * @param preserveRootNamespacesFrom путь к .xml или {@code null}, чтобы не сливать
     * @return {@code this}
     */
    public Builder preserveRootNamespacesFrom(Path preserveRootNamespacesFrom) {
      this.preserveRootNamespacesFrom = preserveRootNamespacesFrom;
      return this;
    }

    /**
     * Итоговые опции.
     *
     * @return неизменяемый {@link WriteOptions}
     */
    public WriteOptions build() {
      return new WriteOptions(this);
    }
  }
}
