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

/**
 * Одна кнопка набора, которым платформа наполняет командную панель.
 *
 * <p>Набор снят с предпросмотра конфигуратора на пустых формах, поэтому имя команды известно не у
 * каждой кнопки: часть платформа рисует значком, часть подписывает так, что команду по подписи не
 * назвать. У такой кнопки заполнена {@link #label} либо не заполнено ничего, кроме представления.
 */
public final class FormStandardCommandDto {

  /** Имя команды без приставки {@code Form.StandardCommand.}; не задано, если назвать не вышло. */
  public String command;

  /** Подпись, когда команду назвать не вышло; иначе подпись берётся из словаря подписей. */
  public String label;

  /** Как платформа рисует кнопку: {@code Text} или {@code Picture}. */
  public String representation;

  /** Кнопка по умолчанию: платформа выделяет её цветом. */
  public Boolean defaultButton;

  /** Кнопка раскрывает подменю. */
  public Boolean submenu;

  /**
   * Кнопка стоит после записанных кнопок панели, а не перед ними.
   *
   * <p>Набор платформа кладёт в панель перед тем, что записано в файле формы, но не весь: на
   * снятой форме документа кнопка «Создать на основании» встала за записанным подменю печати.
   * Сторона снята с той же формы, что и сам набор.
   */
  public Boolean afterOwnButtons;
}
