/*
 * This file is a part of md-sparrow.
 *
 * Copyright (c) 2026
 * Ivan Karlo <i.karlo@outlook.com> and contributors
 *
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package io.github.yellowhammer.designerxml.cf;

import io.github.yellowhammer.designerxml.SchemaVersion;
import io.github.yellowhammer.designerxml.Ssl31SubmodulePaths;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class CatalogFormEditTest {

  @TempDir
  Path tempDir;

  @Test
  void readDto_fromSsl31_catalog() throws Exception {
    Path any = Ssl31SubmodulePaths.anyCatalogObjectXml();
    CatalogFormDto dto = CatalogFormEdit.readDto(any, SchemaVersion.V2_20);
    assertThat(dto.internalName).isNotBlank();
    assertThat(dto.synonymRu).isNotNull();
    assertThat(dto.comment).isNotNull();
  }

  @Test
  void writeDto_roundTrip_preservesFormFields() throws Exception {
    Path src = Ssl31SubmodulePaths.anyCatalogObjectXml();
    Path copy = tempDir.resolve(src.getFileName());
    Files.copy(src, copy);

    CatalogFormDto before = CatalogFormEdit.readDto(copy, SchemaVersion.V2_20);
    CatalogFormEdit.writeDto(copy, SchemaVersion.V2_20, before);
    CatalogFormDto after = CatalogFormEdit.readDto(copy, SchemaVersion.V2_20);

    assertThat(after.internalName).isEqualTo(before.internalName);
    assertThat(after.synonymRu).isEqualTo(before.synonymRu);
    assertThat(after.comment).isEqualTo(before.comment);
  }
}
