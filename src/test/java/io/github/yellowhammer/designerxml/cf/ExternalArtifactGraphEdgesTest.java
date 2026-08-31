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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Реквизиты внешних отчётов и обработок ссылаются на типы основной конфигурации,
 * и эти ссылки должны попадать в граф: иначе внешний файл рисуется на диаграмме
 * одиноким узлом без связей.
 */
class ExternalArtifactGraphEdgesTest {

  @TempDir
  Path tmp;

  @Test
  void externalReportAttributeRefBecomesEdge() throws IOException {
    Path xml = writeArtifact("ExternalReport", "ОтчётПоКонтрагентам", "cfg:CatalogRef.Контрагенты");

    MdObjectGraphExtractor.Inspection inspection =
      MdObjectGraphExtractor.inspect(xml, "ExternalReport");

    assertThat(inspection.partial()).isFalse();
    assertThat(inspection.synonymRu()).isEqualTo("Отчёт по контрагентам");
    assertThat(inspection.edges())
      .extracting(MdObjectGraphExtractor.OutEdge::targetKey)
      .contains("Catalog.Контрагенты");
  }

  @Test
  void externalDataProcessorAttributeRefBecomesEdge() throws IOException {
    Path xml = writeArtifact("ExternalDataProcessor", "ЗагрузкаЦен", "cfg:DocumentRef.УстановкаЦен");

    MdObjectGraphExtractor.Inspection inspection =
      MdObjectGraphExtractor.inspect(xml, "ExternalDataProcessor");

    assertThat(inspection.partial()).isFalse();
    assertThat(inspection.edges())
      .extracting(MdObjectGraphExtractor.OutEdge::targetKey)
      .contains("Document.УстановкаЦен");
  }

  @Test
  void externalArtifactTypesAreSupported() {
    assertThat(MdObjectGraphExtractor.isSupported("ExternalReport")).isTrue();
    assertThat(MdObjectGraphExtractor.isSupported("ExternalDataProcessor")).isTrue();
  }

  /** Минимальная выгрузка внешнего файла: корень {@code MetaDataObject} и один реквизит со ссылкой. */
  private Path writeArtifact(String objectType, String name, String typeRef) throws IOException {
    String xml = """
      <?xml version="1.0" encoding="UTF-8"?>
      <MetaDataObject xmlns="http://v8.1c.ru/8.3/MDClasses"\
       xmlns:cfg="http://v8.1c.ru/8.1/data/enterprise/current-config"\
       xmlns:v8="http://v8.1c.ru/8.1/data/core"\
       xmlns:xr="http://v8.1c.ru/8.3/xcf/readable" version="2.20">
        <%1$s uuid="00000000-0000-0000-0000-000000000001">
          <Properties>
            <Name>%2$s</Name>
            <Synonym>
              <v8:item>
                <v8:lang>ru</v8:lang>
                <v8:content>Отчёт по контрагентам</v8:content>
              </v8:item>
            </Synonym>
          </Properties>
          <ChildObjects>
            <Attribute uuid="00000000-0000-0000-0000-000000000002">
              <Properties>
                <Name>Контрагент</Name>
                <Type>
                  <v8:Type>%3$s</v8:Type>
                </Type>
              </Properties>
            </Attribute>
          </ChildObjects>
        </%1$s>
      </MetaDataObject>
      """.formatted(objectType, name, typeRef);
    Path file = tmp.resolve(name + ".xml");
    Files.writeString(file, xml, StandardCharsets.UTF_8);
    return file;
  }

  @Test
  void graphKeepsExternalArtifactEdgesInProject() throws IOException {
    Path projectRoot = tmp.resolve("проект");
    Path cfRoot = projectRoot.resolve("src").resolve("cf");
    Files.createDirectories(cfRoot);
    Files.writeString(cfRoot.resolve("Configuration.xml"), minimalConfiguration(), StandardCharsets.UTF_8);

    Path erfRoot = projectRoot.resolve("src").resolve("erf").resolve("ОтчётПоКонтрагентам");
    Files.createDirectories(erfRoot);
    Path artifact = writeArtifact("ExternalReport", "ОтчётПоКонтрагентам", "cfg:CatalogRef.Контрагенты");
    Files.copy(artifact, erfRoot.resolve("ОтчётПоКонтрагентам.xml"));

    ProjectMetadataGraphDto graph = ProjectMetadataGraphBuilder.build(projectRoot);
    List<String> targets = graph.edges().stream()
      .filter(edge -> edge.sourceKey().startsWith("ExternalReport."))
      .map(ProjectMetadataGraphDto.EdgeDto::targetKey)
      .toList();
    assertThat(targets).contains("Catalog.Контрагенты");
  }

  private String minimalConfiguration() {
    return """
      <?xml version="1.0" encoding="UTF-8"?>
      <MetaDataObject xmlns="http://v8.1c.ru/8.3/MDClasses"\
       xmlns:v8="http://v8.1c.ru/8.1/data/core" version="2.20">
        <Configuration uuid="00000000-0000-0000-0000-0000000000ff">
          <Properties>
            <Name>Конфигурация</Name>
          </Properties>
          <ChildObjects/>
        </Configuration>
      </MetaDataObject>
      """;
  }
}
