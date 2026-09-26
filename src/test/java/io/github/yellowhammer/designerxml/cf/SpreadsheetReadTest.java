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

import static org.assertj.core.api.Assertions.assertThat;

import io.github.yellowhammer.designerxml.SchemaVersion;
import io.github.yellowhammer.designerxml.Ssl31SubmodulePaths;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;

class SpreadsheetReadTest {

  @Test
  void readsPrintTemplateFromModel() throws Exception {
    Path template = Ssl31SubmodulePaths.projectRoot().resolve(
      "src/cf/Documents/_ДемоПеремещениеТоваров/Templates/ПФ_MXL_НакладнаяНаПеремещение/Ext/Template.xml");
    Map<String, Object> sheet = SpreadsheetRead.read(template, SchemaVersion.V2_20);

    assertThat(sheet.get("rowCount")).isEqualTo(24);
    assertThat(areas(sheet)).contains("Заголовок", "ШапкаТаблицы", "Строка", "Подвал");
    assertThat(cellText(sheet, 8, 1)).isEqualTo("Организация:");
    assertThat(parameter(sheet, 8, 5)).isEqualTo("ОрганизацияПредставление");
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> fonts = (List<Map<String, Object>>) sheet.get("fonts");
    assertThat(fonts).isNotEmpty();
    assertThat(fonts.get(0).get("bold")).isEqualTo(true);
    assertThat(((Number) fonts.get(0).get("sizePt")).doubleValue()).isEqualTo(14d);
  }

  @Test
  void writesCellText() throws Exception {
    Path file = Files.createTempFile("sheet", ".xml");
    Files.copy(Path.of("src/test/resources/spreadsheet/cell-text.xml"), file, StandardCopyOption.REPLACE_EXISTING);
    SpreadsheetRead.setCellText(file, 2, 0, "Стало <&>", false);
    SpreadsheetRead.setCellText(file, 2, 3, "Номер", true);
    String xml = Files.readString(file);
    assertThat(xml).contains("<v8:content>Стало &lt;&amp;&gt;</v8:content>");
    assertThat(xml).contains("<parameter>Номер</parameter>");
    assertThat(xml).doesNotContain("Было");
    assertThat(xml).doesNotContain(">Имя<");
  }

  @Test
  void readsMxlAreasAndCell() throws Exception {
    Map<String, Object> sheet = SpreadsheetRead.read(mxl("areas.txt"), SchemaVersion.V2_20);
    assertThat(cellText(sheet, 0, 0)).isEqualTo("Шапка");
    assertThat(areaKind(sheet, "Заголовок")).isEqualTo("rows");
    assertThat(areaKind(sheet, "Колонка")).isEqualTo("columns");
  }

  @Test
  void writesMxlCellText() throws Exception {
    Path file = mxl("cell.txt");
    SpreadsheetRead.setCellText(file, 0, 0, "Подвал", false);
    Map<String, Object> sheet = SpreadsheetRead.read(file, SchemaVersion.V2_20);
    assertThat(cellText(sheet, 0, 0)).isEqualTo("Подвал");
    assertThat(new String(Files.readAllBytes(file), StandardCharsets.UTF_8)).contains("MOXCEL");
  }

  @SuppressWarnings("unchecked")
  private static List<String> areas(Map<String, Object> sheet) {
    return ((List<Map<String, Object>>) sheet.get("areas")).stream()
      .map(area -> String.valueOf(area.get("name")))
      .toList();
  }

  @SuppressWarnings("unchecked")
  private static String cellText(Map<String, Object> sheet, int row, int column) {
    for (Map<String, Object> item : (List<Map<String, Object>>) sheet.get("rows")) {
      if (((Number) item.get("index")).intValue() != row) {
        continue;
      }
      for (Map<String, Object> cell : (List<Map<String, Object>>) item.get("cells")) {
        if (((Number) cell.get("column")).intValue() == column) {
          return String.valueOf(cell.get("text"));
        }
      }
    }
    return "";
  }

  @SuppressWarnings("unchecked")
  private static String parameter(Map<String, Object> sheet, int row, int column) {
    for (Map<String, Object> item : (List<Map<String, Object>>) sheet.get("rows")) {
      if (((Number) item.get("index")).intValue() != row) {
        continue;
      }
      for (Map<String, Object> cell : (List<Map<String, Object>>) item.get("cells")) {
        if (((Number) cell.get("column")).intValue() == column && Boolean.TRUE.equals(cell.get("parameter"))) {
          return String.valueOf(cell.get("text"));
        }
      }
    }
    return "";
  }

  @SuppressWarnings("unchecked")
  private static String areaKind(Map<String, Object> sheet, String name) {
    for (Map<String, Object> area : (List<Map<String, Object>>) sheet.get("areas")) {
      if (name.equals(area.get("name"))) {
        return String.valueOf(area.get("kind"));
      }
    }
    return "";
  }

  /** Файл *.mxl из текстового тела в ресурсах теста. */
  private static Path mxl(String bodyName) throws IOException {
    byte[] text = Files.readAllBytes(Path.of("src/test/resources/spreadsheet", bodyName));
    byte[] header = new byte[] {
      'M', 'O', 'X', 'C', 'E', 'L', 0,
      8, 0, 1, 0, 12, 0,
      (byte) 0xef, (byte) 0xbb, (byte) 0xbf
    };
    byte[] out = new byte[header.length + text.length];
    System.arraycopy(header, 0, out, 0, header.length);
    System.arraycopy(text, 0, out, header.length, text.length);
    Path file = Files.createTempFile("sheet", ".mxl");
    Files.write(file, out);
    return file;
  }
}
