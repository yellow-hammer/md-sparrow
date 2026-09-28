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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

/**
 * Обычные формы ({@code FormType} = {@code Ordinary}): их содержимое не управляемая форма, и
 * md-sparrow его не читает и не правит. Вместо разбора чужого файла операция честно отказывает.
 */
public final class OrdinaryForms {

  private static final Pattern ORDINARY = Pattern.compile("<FormType>\\s*Ordinary\\s*</FormType>");

  private OrdinaryForms() {
  }

  /**
   * Отказывает, если форма обычная: признак берётся из описания формы
   * {@code Forms/<Имя>.xml} (у общей формы - {@code CommonForms/<Имя>.xml}) рядом с каталогом формы.
   *
   * @param formXml файл содержимого {@code Forms/<Имя>/Ext/Form.xml}
   * @throws IllegalArgumentException если форма обычная
   */
  public static void refuse(Path formXml) throws IOException {
    Path ext = formXml.toAbsolutePath().normalize().getParent();
    Path form = ext == null ? null : ext.getParent();
    if (form == null || form.getFileName() == null) {
      return;
    }
    Path descriptor = form.resolveSibling(form.getFileName() + ".xml");
    if (Files.isRegularFile(descriptor)
        && ORDINARY.matcher(Files.readString(descriptor, StandardCharsets.UTF_8)).find()) {
      throw new IllegalArgumentException("Форма " + form.getFileName() + " обычная (FormType Ordinary): "
          + "md-sparrow читает и правит содержимое только управляемых форм.");
    }
  }
}
