/*
 * This file is a part of md-sparrow.
 *
 * Copyright (c) 2026
 * Ivan Karlo <i.karlo@outlook.com> and contributors
 *
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package io.github.yellowhammer.designerxml.cf;

import io.github.yellowhammer.designerxml.DesignerXml;
import io.github.yellowhammer.designerxml.SchemaVersion;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Создание внешнего отчёта и обработки на диске: результат детерминирован, нормализован и читается
 * моделью своей версии. Проверяем в каждом поддерживаемом формате, а не в паре выбранных.
 */
class ExternalArtifactAddGoldenTest {
  private static final String CONTAINED_OBJECT_TAG = "<xr:ContainedObject>";
  private static final String REPORT_CLASS_ID = "e41aff26-25cf-4bb6-b6c1-3f478a75f374";
  private static final String DATA_PROCESSOR_CLASS_ID = "c3831ec8-d8d5-4f93-8a22-f9bfae07327f";

  @TempDir
  Path workspace;

  @ParameterizedTest
  @EnumSource(SchemaVersion.class)
  void createReportDeterministicAndNormalized(SchemaVersion version) throws Exception {
    String text = createTwiceAndAssertSame(version, ExternalArtifactKind.REPORT, "_ВнешнийОтчетТест", "erf");
    assertThat(text).contains("xmlns:cfg=");
    assertThat(text).contains("<xr:ClassId>" + REPORT_CLASS_ID + "</xr:ClassId>");
  }

  @ParameterizedTest
  @EnumSource(SchemaVersion.class)
  void createDataProcessorDeterministicAndReadable(SchemaVersion version) throws Exception {
    String text = createTwiceAndAssertSame(version, ExternalArtifactKind.DATA_PROCESSOR, "_ВнешняяОбработкаТест", "epf");
    assertThat(text).contains("<ExternalDataProcessor");
    assertThat(text).contains("<xr:ClassId>" + DATA_PROCESSOR_CLASS_ID + "</xr:ClassId>");
  }

  /**
   * @return текст первого созданного файла (второй обязан совпасть с ним)
   */
  private String createTwiceAndAssertSame(
    SchemaVersion version,
    ExternalArtifactKind kind,
    String name,
    String dirPrefix
  ) throws Exception {
    Path xmlA = NewExternalArtifactXml.create(workspace.resolve(dirPrefix + "A-" + version.name()), name, kind, version);
    Path xmlB = NewExternalArtifactXml.create(workspace.resolve(dirPrefix + "B-" + version.name()), name, kind, version);
    String textA = Files.readString(xmlA, StandardCharsets.UTF_8);
    assertThat(textA).isEqualTo(Files.readString(xmlB, StandardCharsets.UTF_8));
    assertThat(textA).contains("version=\"" + version.metadataObjectVersionAttribute() + "\"");
    assertThat(textA).contains("\n\t<");
    assertThat(textA).doesNotContain("standalone=\"yes\"");
    assertThat(textA).contains(CONTAINED_OBJECT_TAG);
    assertThat(textA).contains("<Synonym/>");
    // Порядок InternalInfo — как у конфигуратора.
    assertThat(textA.indexOf(CONTAINED_OBJECT_TAG)).isLessThan(textA.indexOf("<xr:GeneratedType"));
    DesignerXml.read(xmlA, version);
    return textA;
  }
}
