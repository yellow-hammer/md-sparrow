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

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.util.List;

import org.junit.jupiter.api.Test;

/** Список многоязычных свойств собирается по пометке, а не руками. */
class LocalStringPropertiesTest {

  @Test
  void свойстваСоЯзыкомПопадаютВСписок() {
    assertThat(LocalStringProperties.names())
      .contains("synonym", "toolTip", "objectPresentation", "explanation", "copyright");
  }

  @Test
  void обычныеСвойстваВСписокНеПопадают() {
    assertThat(LocalStringProperties.names())
      .doesNotContain("name", "comment", "internalName", "kind", "languageCode");
  }

  @Test
  void вСпискеВсеПомеченныеПоляОбъекта() {
    List<String> names = LocalStringProperties.names();
    for (Field field : MdObjectPropertiesDto.class.getFields()) {
      if (field.isAnnotationPresent(LocalString.class)) {
        assertThat(names).as(field.getName()).contains(field.getName());
      }
    }
  }

  @Test
  void ответДаётСвойСписок() {
    List<String> first = LocalStringProperties.forResponse();
    first.clear();

    assertThat(LocalStringProperties.names()).isNotEmpty();
  }
}
