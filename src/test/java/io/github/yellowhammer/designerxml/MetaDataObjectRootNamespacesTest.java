/*
 * This file is a part of md-sparrow.
 *
 * Copyright (c) 2026
 * Ivan Karlo <i.karlo@outlook.com> and contributors
 *
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package io.github.yellowhammer.designerxml;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class MetaDataObjectRootNamespacesTest {

  @TempDir
  Path tempDir;

  @Test
  void mergeAddsNamespacesFromOriginalWhenMarshallerOmittedThem() throws Exception {
    Path src = Ssl31SubmodulePaths.anyCatalogObjectXml();
    Path copy = tempDir.resolve("with-extra.xml");
    String xml = Files.readString(src, StandardCharsets.UTF_8);
    int ins = xml.indexOf("<MetaDataObject");
    assertThat(ins).isGreaterThanOrEqualTo(0);
    int gt = xml.indexOf('>', ins);
    assertThat(gt).isGreaterThan(ins);
    String open = xml.substring(ins, gt + 1);
    String openExtra =
      open.substring(0, open.length() - 1) + " xmlns:unusedtest=\"http://yellowhammer.test/ns\">";
    String patched = xml.substring(0, ins) + openExtra + xml.substring(gt + 1);
    Files.writeString(copy, patched, StandardCharsets.UTF_8);

    Object root = DesignerXml.read(copy, SchemaVersion.V2_20);
    ByteArrayOutputStream buf = new ByteArrayOutputStream();
    DesignerXml.marshal(SchemaVersion.V2_20, root, buf, WriteOptions.defaults());
    byte[] merged =
      MetaDataObjectRootNamespaces.mergeMarshalledBytes(
        copy, buf.toByteArray(), WriteOptions.defaults().formatPretty());
    String out = new String(merged, StandardCharsets.UTF_8);
    assertThat(out).contains("unusedtest");
    assertThat(out).contains("http://yellowhammer.test/ns");
  }

}
