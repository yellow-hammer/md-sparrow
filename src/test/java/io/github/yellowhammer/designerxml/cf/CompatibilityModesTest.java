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

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Сравнение режима совместимости с версией платформы: чистая утилита без чтения файлов.
 */
class CompatibilityModesTest {

  @Test
  void сравниваетПоЧастям() {
    assertThat(CompatibilityModes.compare("Version8_3_19", 8, 3, 19)).hasValue(0);
    assertThat(CompatibilityModes.compare("Version8_3_18", 8, 3, 19).getAsInt()).isNegative();
    assertThat(CompatibilityModes.compare("Version8_3_24", 8, 3, 19).getAsInt()).isPositive();
    assertThat(CompatibilityModes.compare("Version8_5_1", 8, 3, 19).getAsInt()).isPositive();
    assertThat(CompatibilityModes.compare("Version8_3_9", 8, 3, 19).getAsInt()).isNegative();
    assertThat(CompatibilityModes.compare(" Version8_3_19 ", 8, 3, 19)).hasValue(0);
  }

  @Test
  void лишниеИНедостающиеЧасти() {
    assertThat(CompatibilityModes.compare("Version8_3_13_1", 8, 3, 13).getAsInt()).isPositive();
    assertThat(CompatibilityModes.compare("Version8_3", 8, 3, 13).getAsInt()).isNegative();
    assertThat(CompatibilityModes.compare("Version8_3_0019", 8, 3, 19)).hasValue(0);
  }

  @Test
  void неВерсияНеСравнивается() {
    assertThat(CompatibilityModes.compare("DontUse", 8, 3, 19)).isEmpty();
    assertThat(CompatibilityModes.compare("", 8, 3, 19)).isEmpty();
    assertThat(CompatibilityModes.compare("Version", 8, 3, 19)).isEmpty();
    assertThat(CompatibilityModes.compare("Version8_x_19", 8, 3, 19)).isEmpty();
  }

  /** Значение приходит из файла пользователя: число вне int не должно ронять операцию. */
  @Test
  void числоВнеДиапазонаНеПадает() {
    assertThat(CompatibilityModes.compare("Version99999999999_3_1", 8, 3, 19).getAsInt()).isPositive();
    assertThat(CompatibilityModes.compare("Version8_99999999999", 8, 3, 13).getAsInt()).isPositive();
    assertThat(EmptyCfeScaffold.overridesAdoptedProperties("Version99999999999_3_1")).isTrue();
    assertThat(FormNamespaceRules.declaresCompositionSchema("Version8_3_99999999999")).isTrue();
  }
}
