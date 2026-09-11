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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import org.junit.jupiter.api.Test;

import io.github.yellowhammer.designerxml.SchemaVersion;
import io.github.yellowhammer.designerxml.cf.MdObjectPropertyEnums;

/** Словарь значений перечислимых свойств сверяется с выгрузкой конфигуратора. */
class EdtPropertyEnumsTest {

  /**
   * Свойства, где наборы значений расходятся по существу.
   *
   * Режимы совместимости перечисляют версии платформы: схемы EDT свежее, а
   * отказ от режима формат EDT записывает отсутствием свойства.
   */
  private static final List<String> COMPATIBILITY = List.of(
      "configuration.compatibilityMode",
      "configuration.configurationExtensionCompatibilityMode",
      "configuration.interfaceCompatibilityMode");

  @Test
  void значенияСовпадаютСВыгрузкойКонфигуратора() throws Exception {
    Map<String, List<String>> edt = EdtPropertyEnums.all(EdtModel.bundled());
    Map<String, List<String>> designer = MdObjectPropertyEnums.forVersion(SchemaVersion.V2_21);

    List<String> mismatches = new ArrayList<>();
    for (Map.Entry<String, List<String>> entry : designer.entrySet()) {
      List<String> fromEdt = edt.get(entry.getKey());
      if (fromEdt == null) {
        mismatches.add(entry.getKey() + ": в схемах EDT свойства нет");
        continue;
      }
      if (COMPATIBILITY.contains(entry.getKey())) {
        continue;
      }
      if (!new TreeSet<>(fromEdt).equals(new TreeSet<>(entry.getValue()))) {
        mismatches.add("%s: EDT %s, конфигуратор %s".formatted(entry.getKey(), fromEdt, entry.getValue()));
      }
    }

    assertThat(designer).hasSizeGreaterThan(200);
    assertThat(mismatches).as("расхождения словарей").isEmpty();
  }

  @Test
  void версииПлатформыПишутсяКакВКонтракте() throws Exception {
    Map<String, List<String>> edt = EdtPropertyEnums.all(EdtModel.bundled());

    assertThat(edt.get("configuration.compatibilityMode")).contains("VERSION_8_3_12", "VERSION_8_1");
    assertThat(edt.get("catalog.hierarchyType"))
        .containsExactly("HIERARCHY_FOLDERS_AND_ITEMS", "HIERARCHY_OF_ITEMS");
    // Реквизит справочника метамодель зовёт CatalogAttribute, панель - реквизитом
    assertThat(edt.get("attribute.fullTextSearch")).containsExactlyInAnyOrder("USE", "DONT_USE");
  }
}
