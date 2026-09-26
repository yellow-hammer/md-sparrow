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

import io.github.yellowhammer.designerxml.Ssl31SubmodulePaths;
import io.github.yellowhammer.designerxml.SchemaVersion;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

class WriteProbeTest {
  @TempDir Path tempDir;

  @Test
  void probe() throws Exception {
    String relative = "src/cf/CommonForms/_ДемоМоиНастройки.xml";
    Path src = Ssl31SubmodulePaths.projectRoot().resolve(relative);
    Path copy = tempDir.resolve(src.getFileName());
    Files.copy(src, copy, StandardCopyOption.REPLACE_EXISTING);
    MdObjectPropertiesDto baseline = MdObjectPropertiesEdit.readDto(copy, SchemaVersion.V2_20);
    MdObjectPropertiesDto dto = MdObjectPropertiesEdit.readDto(copy, SchemaVersion.V2_20);
    dto.synonym = "Новый синоним";
    List<MdObjectPropertiesLeafDiff.GranularPatchChange> changes =
      MdObjectPropertiesLeafDiff.computePropertyChanges(baseline, dto);
    System.out.println("PROBE kind=" + baseline.kind + " changes=" + changes.size());
    for (MdObjectPropertiesLeafDiff.GranularPatchChange ch : changes) {
      System.out.println("PROBE change element=" + ch.mdElementLocalName());
    }
    try {
      MdObjectPropertiesEdit.writeDto(copy, SchemaVersion.V2_20, dto);
      System.out.println("PROBE write ok");
    } catch (Exception e) {
      System.out.println("PROBE write err: " + e.getMessage());
    }
  }
}
