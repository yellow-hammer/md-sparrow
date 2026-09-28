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
package io.github.yellowhammer.edt;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Обычные формы проекта EDT: md-sparrow читает и правит содержимое только управляемых форм.
 *
 * <p>Вид формы по умолчанию в метамодели - управляемая, поэтому обычная записана явным
 * {@code <formType>Ordinary</formType>}: у формы объекта - в её записи {@code forms} в описании
 * владельца, у общей формы - в её собственном описании.
 */
public final class EdtOrdinaryForms {

  private static final Pattern FORM_ENTRY = Pattern.compile("<forms\\b[^>]*>(.*?)</forms>", Pattern.DOTALL);
  private static final String ORDINARY = "<formType>Ordinary</formType>";

  private EdtOrdinaryForms() {
  }

  /**
   * Отказывает, если форма обычная.
   *
   * @param formFile файл {@code Form.form}
   * @throws IllegalArgumentException если форма обычная
   */
  public static void refuse(Path formFile) throws IOException {
    Path owner = EdtFormItemStructureEdit.ownerMdo(formFile);
    if (owner == null || !Files.isRegularFile(owner)) {
      return;
    }
    String mdo = Files.readString(owner, StandardCharsets.UTF_8);
    String name = String.valueOf(formFile.toAbsolutePath().normalize().getParent().getFileName());
    boolean ordinary;
    if (owner.getParent().equals(formFile.toAbsolutePath().normalize().getParent())) {
      // Общая форма: вид записан в её описании, форм-записей у неё нет
      ordinary = mdo.contains(ORDINARY) && !FORM_ENTRY.matcher(mdo).find();
    } else {
      ordinary = false;
      Matcher entry = FORM_ENTRY.matcher(mdo);
      while (entry.find()) {
        if (entry.group(1).contains("<name>" + name + "</name>")) {
          ordinary = entry.group(1).contains(ORDINARY);
          break;
        }
      }
    }
    if (ordinary) {
      throw new IllegalArgumentException("Форма " + name + " обычная (formType Ordinary): "
          + "md-sparrow читает и правит содержимое только управляемых форм.");
    }
  }
}
