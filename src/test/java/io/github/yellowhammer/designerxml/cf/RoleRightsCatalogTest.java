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

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

class RoleRightsCatalogTest {

  @Test
  void everyRightOfEveryKindHasLabel() {
    Map<String, String> labels = UiLabels.rights();
    for (Map.Entry<String, List<String>> kind : RoleRightsCatalog.rightsByKind().entrySet()) {
      assertThat(kind.getValue()).as(kind.getKey()).isNotEmpty();
      for (String right : kind.getValue()) {
        assertThat(labels).as(kind.getKey() + "." + right).containsKey(right);
      }
    }
  }

  @Test
  void requiredRightsBelongToTheSameKind() {
    for (String name : RoleRightsCatalog.rightsByKind().keySet()) {
      RoleRightsCatalog.Kind kind = RoleRightsCatalog.kind(name);
      kind.requires().forEach((right, required) -> {
        assertThat(kind.rights()).as(name).contains(right);
        assertThat(kind.rights()).as(name + "." + right).containsAll(required);
      });
    }
  }

  @Test
  void grantedRightBringsRequiredOnes() {
    RoleRightsCatalog.Kind document = RoleRightsCatalog.kind("Document");

    assertThat(RoleRightsCatalog.withRequired(document, "InteractivePosting"))
      .containsExactly("InteractivePosting", "Read", "Update", "Posting", "View", "Edit");
  }

  @Test
  void revokedReadTakesEverythingButHistorySettings() {
    RoleRightsCatalog.Kind catalog = RoleRightsCatalog.kind("Catalog");
    List<String> expected = new ArrayList<>(catalog.rights());
    expected.remove("UpdateDataHistorySettings");

    assertThat(RoleRightsCatalog.withDependent(catalog, "Read")).containsExactlyInAnyOrderElementsOf(expected);
  }

  @Test
  void kindsWithoutRightsAreUnknown() {
    assertThat(RoleRightsCatalog.kind("Enum")).isNull();
    assertThat(RoleRightsCatalog.kind("CommonModule")).isNull();
  }
}
