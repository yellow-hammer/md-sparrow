/*
 * This file is a part of md-sparrow.
 *
 * Copyright (c) 2026
 * Ivan Karlo <i.karlo@outlook.com> and contributors
 *
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package io.github.yellowhammer.designerxml.cli;

import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Проверяет канал {@code apply-mutation}: кириллические значения приходят из UTF-8 JSON без искажения
 * (в отличие от {@code argv}, который на Windows зависит от кодовой страницы ОС).
 */
class ApplyMutationCmdTest {

  private static final String CYRILLIC_NAME = "КириллическийРеквизитИзJson";

  @Test
  void addAttribute_viaParamsFile_keepsCyrillicName() throws Exception {
    Path objectXml = copyToTemp(sampleDocumentXml());
    Path params = writeParams(
      "{"
        + "\"op\":\"cf-md-attribute-add\","
        + "\"objectXml\":" + json(objectXml.toString()) + ","
        + "\"name\":" + json(CYRILLIC_NAME) + ","
        + "\"schemaVersion\":\"V2_20\""
        + "}");

    int exit = new CommandLine(new DesignerXmlCli()).execute("apply-mutation", "--params", params.toString());

    assertThat(exit).isZero();
    assertThat(Files.readString(objectXml, StandardCharsets.UTF_8)).contains(CYRILLIC_NAME);
  }

  @Test
  void unknownOp_returnsError() throws Exception {
    Path params = writeParams("{\"op\":\"no-such-op\"}");
    int exit = new CommandLine(new DesignerXmlCli()).execute("apply-mutation", "--params", params.toString());
    assertThat(exit).isEqualTo(2);
  }

  @Test
  void missingRequiredField_returnsError() throws Exception {
    Path params = writeParams("{\"op\":\"cf-md-attribute-add\",\"schemaVersion\":\"V2_20\"}");
    int exit = new CommandLine(new DesignerXmlCli()).execute("apply-mutation", "--params", params.toString());
    assertThat(exit).isEqualTo(2);
  }

  @Test
  void readStructure_viaParamsFile_keepsCyrillicPathAndName() throws Exception {
    // Путь к объекту содержит кириллицу (имя файла) — проверяем чтение через UTF-8 JSON, не argv.
    Path objectXml = sampleDocumentXml();
    Path params = writeParams(
      "{"
        + "\"op\":\"cf-md-object-structure-get\","
        + "\"objectXml\":" + json(objectXml.toString()) + ","
        + "\"schemaVersion\":\"V2_20\""
        + "}");

    ByteArrayOutputStream out = new ByteArrayOutputStream();
    PrintStream prev = System.out;
    int exit;
    try {
      System.setOut(new PrintStream(out, true, StandardCharsets.UTF_8));
      exit = new CommandLine(new DesignerXmlCli()).execute("read-json", "--params", params.toString());
    } finally {
      System.setOut(prev);
    }

    assertThat(exit).isZero();
    assertThat(out.toString(StandardCharsets.UTF_8)).contains("\"document\"");
  }

  private static String json(String raw) {
    return "\"" + raw.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
  }

  private static Path writeParams(String content) throws IOException {
    Path dir = Files.createTempDirectory("apply-mutation-");
    Path file = dir.resolve("params.json");
    Files.writeString(file, content, StandardCharsets.UTF_8);
    return file;
  }

  private static Path copyToTemp(Path source) throws IOException {
    Path dir = Files.createTempDirectory("apply-mutation-obj-");
    Path target = dir.resolve(source.getFileName().toString());
    Files.copy(source, target);
    return target;
  }

  private static Path sampleDocumentXml() {
    String fixturesRoot = System.getProperty("fixtures.ssl31.root");
    return Path.of(fixturesRoot)
      .resolve("src")
      .resolve("cf")
      .resolve("Documents")
      .resolve("_ДемоЗаказПокупателя.xml");
  }
}
