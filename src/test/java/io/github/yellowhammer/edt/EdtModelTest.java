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

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Чтение формата EDT по схемам из хранилища. */
class EdtModelTest {

  private static final String MDCLASS = "http://g5.1c.ru/v8/dt/metadata/mdclass";

  @Test
  void загружаетМетамодельИзСборки() throws Exception {
    EdtModel model = EdtModel.bundled();

    assertThat(model.namespaces()).contains(MDCLASS, "http://g5.1c.ru/v8/dt/mcore");
    assertThat(model.packageOf(MDCLASS).getEClassifiers()).hasSizeGreaterThan(200);
    assertThat(model.version()).matches("[0-9]{4}[.][0-9]+");
  }

  @Test
  void знаетКлассыОбъектовМетаданных() throws Exception {
    EdtModel model = EdtModel.bundled();

    assertThat(model.classOf("Catalog")).isNotNull();
    assertThat(model.classOf("Document")).isNotNull();
    assertThat(model.classOf("НетТакогоКласса")).isNull();
  }
}
