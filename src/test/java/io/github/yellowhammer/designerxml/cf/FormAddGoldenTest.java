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

import io.github.yellowhammer.designerxml.DesignerXml;
import io.github.yellowhammer.designerxml.SchemaVersion;
import io.github.yellowhammer.designerxml.XmlValidator;

import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * cf-form-add в каждом формате: пустая форма - проекция эталона платформы. Где платформа сняла
 * внешний отчёт с формой ({@code snapshots/<формат>/external-files/empty-full-objects}), новая форма
 * внешнего отчёта совпадает с его формой байт в байт после замены имени и UUID; в остальных форматах
 * описание формы проходит XSD, а описание и содержимое читаются моделью формата.
 */
class FormAddGoldenTest {

  private static final String FORM_PROTO = "Форма";

  private static final String REPORT_FORMS = "ВнешнийОтчет1/ВнешнийОтчет1/Forms/";

  @TempDir
  Path workspace;

  @ParameterizedTest
  @EnumSource(SchemaVersion.class)
  void формаВнешнегоОтчётаСовпадаетСВыгрузкойПлатформы(SchemaVersion version) throws Exception {
    String owner = "Нов" + GoldenScaffold.externalProtoName(ExternalArtifactKind.REPORT);
    String formName = "Нов" + FORM_PROTO;
    Path ownerXml = NewExternalArtifactXml.create(workspace, owner, ExternalArtifactKind.REPORT, version);

    FormScaffold.addForm(ownerXml, version, formName);

    Path forms = workspace.resolve(owner).resolve(owner).resolve("Forms");
    Path descriptor = forms.resolve(formName + ".xml");
    Path content = forms.resolve(formName).resolve("Ext").resolve("Form.xml");
    readable(descriptor, content, version);

    List<String> platformFiles = GoldenSnapshots.files(version, GoldenSnapshots.EXTERNAL_FULL);
    if (version == GoldenSnapshots.canonical()) {
      assertThat(platformFiles).as("эталон формы канонического формата").contains(REPORT_FORMS + FORM_PROTO + ".xml");
    }
    if (platformFiles.contains(REPORT_FORMS + FORM_PROTO + ".xml")) {
      assertThat(normalized(GoldenSnapshots.read(descriptor), formName, FORM_PROTO))
        .isEqualTo(GoldenSnapshots.normalizeUuids(
          GoldenSnapshots.read(version, GoldenSnapshots.EXTERNAL_FULL, REPORT_FORMS + FORM_PROTO + ".xml")));
      assertThat(GoldenSnapshots.read(content))
        .isEqualTo(GoldenSnapshots.read(version, GoldenSnapshots.EXTERNAL_FULL, REPORT_FORMS + FORM_PROTO + "/Ext/Form.xml"));
    }
  }

  @ParameterizedTest
  @EnumSource(SchemaVersion.class)
  void формаСправочникаСоздаётсяВКаждомФормате(SchemaVersion version) throws Exception {
    Path cf = workspace.resolve(version.name());
    EmptyCfScaffold.writeEmptyTree(cf, CfLayout.DEFAULT_CONFIGURATION_NAME, null, null, null, version);
    String owner = "Нов" + GoldenScaffold.protoName(MdObjectAddType.CATALOG);
    MdObjectAdd.add(cf.resolve(CfLayout.CONFIGURATION_XML), owner, version, MdObjectAddType.CATALOG);
    Path ownerXml = CfLayout.objectXmlInSubdir(cf, MdObjectAddType.CATALOG.cfSubdir(), owner);

    FormScaffold.addForm(ownerXml, version, "ФормаЭлемента");

    MdObjectStructureDto structure = MdObjectStructureRead.read(ownerXml, version);
    assertThat(structure.forms).extracting(form -> form.name).containsExactly("ФормаЭлемента");
    Path forms = ownerXml.resolveSibling(owner).resolve("Forms");
    readable(forms.resolve("ФормаЭлемента.xml"), forms.resolve("ФормаЭлемента").resolve("Ext").resolve("Form.xml"), version);
  }

  private static void readable(Path descriptor, Path content, SchemaVersion version) throws Exception {
    assertThat(MdObjectPropertiesEdit.readDto(descriptor, version).kind).isEqualTo("form");
    XmlValidator.validate(descriptor, version, Path.of(System.getProperty("xsd.root")));
    assertThat(DesignerXml.read(content, version)).isNotNull();
    for (Path file : List.of(descriptor, content)) {
      String text = GoldenSnapshots.read(file);
      assertThat(text).as("BOM %s", file).startsWith("﻿");
      assertThat(text.replace("\r\n", "")).as("CRLF %s", file).doesNotContain("\n");
      assertThat(text).as("версия %s", file).contains("version=\"" + version.metadataObjectVersionAttribute() + "\"");
    }
  }

  private static String normalized(String text, String name, String proto) {
    Pattern token = Pattern.compile("(?<![\\p{L}\\p{N}_])" + Pattern.quote(name) + "(?![\\p{L}\\p{N}_])");
    return GoldenSnapshots.normalizeUuids(token.matcher(text).replaceAll(Matcher.quoteReplacement(proto)));
  }
}
