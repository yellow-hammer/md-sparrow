/*
 * This file is a part of md-sparrow.
 *
 * Copyright (c) 2026
 * Ivan Karlo <i.karlo@outlook.com> and contributors
 *
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package io.github.yellowhammer.designerxml.cf;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.yellowhammer.designerxml.SchemaVersion;
import io.github.yellowhammer.designerxml.Ssl31SubmodulePaths;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

class CfeBorrowTest {

  @TempDir Path tempDir;

  @Test
  void borrowsCatalogIntoExtension() throws Exception {
    Path source = Ssl31SubmodulePaths.projectRoot()
      .resolve("src/cfe/_ДемоПустоеРасширение/Configuration.xml");
    Path extensionXml = tempDir.resolve("Configuration.xml");
    Files.copy(source, extensionXml, StandardCopyOption.REPLACE_EXISTING);
    Path objectXml = Ssl31SubmodulePaths.projectRoot().resolve("src/cf/Catalogs/_ДемоБанковскиеСчета.xml");

    Path created = CfeBorrow.borrowObject(objectXml, extensionXml, SchemaVersion.V2_20);

    assertThat(created).isEqualTo(tempDir.resolve("Catalogs").resolve("_ДемоБанковскиеСчета.xml"));
    MdObjectPropertiesDto adopted = MdObjectPropertiesEdit.readDto(created, SchemaVersion.V2_20);
    assertThat(adopted.kind).isEqualTo("catalog");
    assertThat(adopted.internalName).isEqualTo("_ДемоБанковскиеСчета");
    String xml = Files.readString(created);
    assertThat(xml).contains("<ObjectBelonging>Adopted</ObjectBelonging>");
    assertThat(xml).contains("<xr:GeneratedType name=\"CatalogObject._ДемоБанковскиеСчета\"");
    // Идентификаторы свои: ни один uuid оригинала не переносится
    String original = Files.readString(objectXml);
    java.util.regex.Matcher ids = java.util.regex.Pattern
      .compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
      .matcher(xml);
    while (ids.find()) {
      assertThat(original).doesNotContain(ids.group());
    }

    String configuration = Files.readString(extensionXml);
    assertThat(configuration).contains("<Catalog>_ДемоБанковскиеСчета</Catalog>");

    assertThatThrownBy(() -> CfeBorrow.borrowObject(objectXml, extensionXml, SchemaVersion.V2_20))
      .hasMessageContaining("уже");
  }
}
