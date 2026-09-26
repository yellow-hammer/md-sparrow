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

import java.util.ArrayList;
import java.util.List;

/**
 * Описание типа значения ({@code v8:TypeDescription}) для JSON: состав типов и квалификаторы.
 *
 * <p>Типы — как в XML, с префиксом пространства имён: {@code xs:string}, {@code xs:decimal},
 * {@code xs:boolean}, {@code xs:dateTime}, {@code cfg:CatalogRef.Номенклатура}. Составной тип —
 * несколько элементов списка.
 *
 * <p>Enum-значения квалификаторов — имена Java-констант ({@code VARIABLE}, {@code NONNEGATIVE},
 * {@code DATE_TIME}).
 */
public final class MdTypeDescriptionDto {

  public List<String> types;
  /**
   * Наборы типов ({@code v8:TypeSet}): определяемый тип и характеристики.
   *
   * <p>Состав такого набора лежит у другого объекта конфигурации, поэтому здесь стоит только
   * ссылка: {@code cfg:DefinedType.ДенежнаяСумма}.
   */
  public List<String> typeSets;
  public MdStringQualifiersDto stringQualifiers;
  public MdNumberQualifiersDto numberQualifiers;
  public MdDateQualifiersDto dateQualifiers;
  public MdBinaryDataQualifiersDto binaryDataQualifiers;

  public MdTypeDescriptionDto() {
    this.types = new ArrayList<>();
    this.typeSets = new ArrayList<>();
  }

  /** Квалификаторы строки: длина и её изменяемость. */
  public static final class MdStringQualifiersDto {
    public String length;
    public String allowedLength;
  }

  /** Квалификаторы числа: разрядность, дробная часть и допустимый знак. */
  public static final class MdNumberQualifiersDto {
    public String digits;
    public String fractionDigits;
    public String allowedSign;
  }

  /** Квалификаторы даты: состав даты. */
  public static final class MdDateQualifiersDto {
    public String dateFractions;
  }

  /** Квалификаторы двоичных данных: длина и её изменяемость. */
  public static final class MdBinaryDataQualifiersDto {
    public String length;
    public String allowedLength;
  }
}
