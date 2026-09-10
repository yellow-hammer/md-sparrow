/*
 * This file is a part of md-sparrow.
 *
 * SPDX-License-Identifier: LGPL-3.0-or-later
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
