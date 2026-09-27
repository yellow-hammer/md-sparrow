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
package io.github.yellowhammer.designerxml.cli;

import io.github.yellowhammer.designerxml.SamplesSubmodulePaths;
import io.github.yellowhammer.designerxml.SchemaVersion;
import io.github.yellowhammer.designerxml.Ssl31SubmodulePaths;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Команда {@code validate}: несоответствие схеме — код 1 и место ошибки, без трассы стека; схема, которая не
 * собирается, — код 2 и место ошибки в XSD.
 */
class ValidateCmdTest {

  @TempDir
  Path tempDir;

  @Test
  void platformObjectIsOk() throws Exception {
    assertThat(validate(Ssl31SubmodulePaths.anyCatalogObjectXml()).exit()).isZero();
  }

  @Test
  void mismatchReportsFileAndLine() throws Exception {
    Path source = Ssl31SubmodulePaths.anyCatalogObjectXml();
    List<String> lines = new ArrayList<>(Files.readAllLines(source, StandardCharsets.UTF_8));
    int at = 0;
    while (!lines.get(at).contains("<Hierarchical>")) {
      at++;
    }
    lines.set(at, lines.get(at).replace("Hierarchical", "Hierarchycal"));
    Path broken = tempDir.resolve(source.getFileName());
    Files.writeString(broken, String.join("\n", lines), StandardCharsets.UTF_8);

    Result result = validate(broken);

    assertThat(result.exit()).isEqualTo(1);
    assertThat(result.err())
      .startsWith(broken + ":" + (at + 1) + ":")
      .contains("Hierarchycal")
      .doesNotContain("\tat ");
  }

  /** Схемы формата без одной из импортируемых: ошибка сборки схемы — не ошибка проверяемого файла. */
  @Test
  void missingImportedSchemaIsSchemaError() throws Exception {
    Path xsdDir = copyOfFormatSchemas();
    Path imported = xsdDir.resolve("v8.1c.ru-8.1-data-ui.xsd");
    Files.delete(imported);

    assertSchemaError(validate(Ssl31SubmodulePaths.anyCatalogObjectXml(), tempDir.resolve("xsd")), ".xsd:");
  }

  /** Испорченная главная схема формата: код 2 и место ошибки в ней, а не в проверяемом файле. */
  @Test
  void brokenMainSchemaIsSchemaError() throws Exception {
    Path xsdDir = copyOfFormatSchemas();
    Path main = xsdDir.resolve("v8.1c.ru-8.3-MDClasses.xsd");
    byte[] text = Files.readAllBytes(main);
    Files.write(main, Arrays.copyOf(text, text.length / 2));

    assertSchemaError(
      validate(Ssl31SubmodulePaths.anyCatalogObjectXml(), tempDir.resolve("xsd")), main.getFileName() + ":");
  }

  private static void assertSchemaError(Result result, String place) throws Exception {
    assertThat(result.exit()).as(result.err()).isEqualTo(2);
    assertThat(result.err())
      .doesNotStartWith(Ssl31SubmodulePaths.anyCatalogObjectXml().toString())
      .contains(place)
      .doesNotContain("\tat ");
  }

  /** Копия каталога схем формата V2_20 в корне {@code <tempDir>/xsd}; возвращает каталог формата. */
  private Path copyOfFormatSchemas() throws Exception {
    String format = SchemaVersion.V2_20.xsdDirectoryName();
    return SamplesSubmodulePaths.copy(
      Path.of(System.getProperty("xsd.root")).resolve(format), tempDir.resolve("xsd").resolve(format));
  }

  private static Result validate(Path xml) {
    return validate(xml, Path.of(System.getProperty("xsd.root")));
  }

  private static Result validate(Path xml, Path xsdRoot) {
    PrintStream original = System.err;
    ByteArrayOutputStream err = new ByteArrayOutputStream();
    System.setErr(new PrintStream(err, true, StandardCharsets.UTF_8));
    try {
      int exit = new CommandLine(new DesignerXmlCli())
        .execute("validate", xml.toString(), xsdRoot.toString(), "-v", "V2_20");
      return new Result(exit, err.toString(StandardCharsets.UTF_8));
    } finally {
      System.setErr(original);
    }
  }

  private record Result(int exit, String err) {
  }
}
