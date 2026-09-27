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

import io.github.yellowhammer.designerxml.SchemaVersion;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Команда {@code init-empty-cfe}: отказ отвечает одной строкой и кодом 2, как {@code apply-mutation}.
 */
class InitEmptyCfeCmdTest {

  @TempDir
  Path workspace;

  @Test
  void нечитаемаяОсновнаяКонфигурацияОтвечаетОднойСтрокой() {
    Path missing = workspace.resolve("нет").resolve("Configuration.xml");

    ByteArrayOutputStream err = new ByteArrayOutputStream();
    PrintStream prev = System.err;
    int exit;
    try {
      System.setErr(new PrintStream(err, true, StandardCharsets.UTF_8));
      exit = new CommandLine(new DesignerXmlCli()).execute(
        "init-empty-cfe", workspace.resolve("cfe").toString(), "--name=Ext", "-v", SchemaVersion.V2_20.name(),
        "--from-configuration=" + missing);
    } finally {
      System.setErr(prev);
    }

    String text = err.toString(StandardCharsets.UTF_8).strip();
    assertThat(exit).isEqualTo(2);
    assertThat(text).contains("Configuration.xml").doesNotContain("\n").doesNotContain("at ");
  }
}
