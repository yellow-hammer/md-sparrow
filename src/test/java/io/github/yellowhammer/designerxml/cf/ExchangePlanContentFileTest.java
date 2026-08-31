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

import io.github.yellowhammer.designerxml.SchemaVersion;
import io.github.yellowhammer.designerxml.Ssl31SubmodulePaths;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

class ExchangePlanContentFileTest {

  @TempDir Path tempDir;

  @Test
  void readsMembersWithAutoRecord() throws Exception {
    List<MdContentMemberDto> members = ExchangePlanContentFile.read(
      Ssl31SubmodulePaths.projectRoot().resolve("src/cf/ExchangePlans/_ДемоАвтономнаяРабота.xml"));
    assertThat(members).isNotEmpty();
    assertThat(members)
      .anyMatch(member -> "Document._ДемоСчетНаОплатуПокупателю".equals(member.ref) && "Deny".equals(member.mode));
  }

  @Test
  void writeKeepsPrologAndRoundTrips() throws Exception {
    Path planDir = tempDir.resolve("План");
    Files.createDirectories(planDir);
    Path planXml = tempDir.resolve("План.xml");
    Files.writeString(planXml, "<MetaDataObject/>");
    Path source = Ssl31SubmodulePaths.projectRoot()
      .resolve("src/cf/ExchangePlans/_ДемоАвтономнаяРабота/Ext/Content.xml");
    Path target = ExchangePlanContentFile.contentPath(planXml);
    Files.createDirectories(target.getParent());
    Files.copy(source, target);

    List<MdContentMemberDto> members = ExchangePlanContentFile.read(planXml);
    members.removeIf(member -> "Deny".equals(member.mode) && member.ref.startsWith("Catalog."));
    members.add(new MdContentMemberDto("Catalog._ДемоНоменклатура", "Allow", ""));
    ExchangePlanContentFile.write(planXml, SchemaVersion.V2_20, members);

    List<MdContentMemberDto> after = ExchangePlanContentFile.read(planXml);
    assertThat(after).containsExactlyElementsOf(members);
    String text = Files.readString(target);
    assertThat(text).startsWith("\uFEFF<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
    assertThat(text).contains("xmlns=\"http://v8.1c.ru/8.3/xcf/extrnprops\"");
  }

  @Test
  void writeCreatesFileWhenMissing() throws Exception {
    Path planXml = tempDir.resolve("Новый.xml");
    Files.writeString(planXml, "<MetaDataObject/>");
    ExchangePlanContentFile.write(planXml, SchemaVersion.V2_20,
      List.of(new MdContentMemberDto("Document.Заказ", "Deny", "")));
    List<MdContentMemberDto> members = ExchangePlanContentFile.read(planXml);
    assertThat(members).hasSize(1);
    assertThat(members.get(0).ref).isEqualTo("Document.Заказ");
  }
}
