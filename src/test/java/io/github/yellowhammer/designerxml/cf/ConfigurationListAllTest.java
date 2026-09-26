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

import io.github.yellowhammer.designerxml.SchemaVersion;
import io.github.yellowhammer.designerxml.Ssl31SubmodulePaths;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

class ConfigurationListAllTest {

  @Test
  void listAllReturnsEveryKindInOneRead() throws Exception {
    Map<String, List<String>> all = ConfigurationChildObjectLister.listAll(
      Ssl31SubmodulePaths.projectRoot().resolve("src/cf/Configuration.xml"), SchemaVersion.V2_20);
    assertThat(all).containsKeys("Catalog", "Document", "CommonModule", "FunctionalOption", "Subsystem");
    assertThat(all.get("Catalog")).isNotEmpty().isSorted();
    // Языки тоже объекты состава: дерево состава показывает всё
    assertThat(all).containsKey("Language");
  }
}
