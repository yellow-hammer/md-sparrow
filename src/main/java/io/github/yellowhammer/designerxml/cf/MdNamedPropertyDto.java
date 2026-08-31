/*
 * This file is a part of md-sparrow.
 *
 * Copyright (c) 2026
 * Ivan Karlo <i.karlo@outlook.com> and contributors
 *
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package io.github.yellowhammer.designerxml.cf;

/**
 * Реквизит, табличная часть, значение перечисления, измерение или ресурс: имя не меняется через DTO,
 * только синоним ru, комментарий, тип и свойства палитры.
 *
 * <p>{@link #type} есть у того, у чего платформа его требует; у табличных частей и значений
 * перечисления он {@code null}. Свойства палитры версионно-вариативны и есть не у всякого вида
 * узла: чего в схеме нет, то приходит пустым и при записи не трогается.
 */
public final class MdNamedPropertyDto {

  public String name;
  public String synonymRu;
  public String comment;
  public MdTypeDescriptionDto type;
  /** Подсказка на русском. */
  public String toolTipRu;
  /** Проверка заполнения: {@code DONT_CHECK}, {@code SHOW_ERROR}. */
  public String fillChecking;
  /** Индексирование: {@code DONT_INDEX}, {@code INDEX}, {@code INDEX_WITH_ADDITIONAL_ORDER}. */
  public String indexing;
  /** Полнотекстовый поиск: {@code USE}, {@code DONT_USE}. */
  public String fullTextSearch;
  /** История данных: {@code USE}, {@code DONT_USE}. */
  public String dataHistory;
  /** Использование реквизита: {@code FOR_ITEM}, {@code FOR_FOLDER}, {@code FOR_FOLDER_AND_ITEM}. */
  public String use;
  /** Быстрый выбор: {@code AUTO}, {@code USE}, {@code DONT_USE}. */
  public String quickChoice;
  /** Создание при вводе: {@code AUTO}, {@code USE}, {@code DONT_USE}. */
  public String createOnInput;
  /** История выбора при вводе: {@code AUTO}, {@code DONT_USE}. */
  public String choiceHistoryOnInput;
  /** Форма выбора: полное имя формы либо пусто. */
  public String choiceForm;
  /** Параметры выбора: значение типизировано, поэтому только для чтения. */
  public java.util.List<MdChoiceParameterDto> choiceParameters;
  /** Связи параметров выбора: читаются и пишутся целиком. */
  public java.util.List<MdChoiceParameterLinkDto> choiceParameterLinks;
  /** Реквизиты табличной части; у остальных видов узлов пусто. */
  public java.util.List<MdNamedPropertyDto> attributes;

  public MdNamedPropertyDto() {
  }

  public MdNamedPropertyDto(String name, String synonymRu, String comment) {
    this.name = name;
    this.synonymRu = synonymRu;
    this.comment = comment;
  }
}
