/*
 * This file is a part of md-sparrow.
 *
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package io.github.yellowhammer.designerxml.cf;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Табличный документ в файле {@code *.mxl} (MXL8, сигнатура MOXCEL).
 *
 * <p>Схемы у формата нет. Наружу отдаётся тот же JSON, что у макета
 * {@code Template.xml}: расширение формат не разбирает.
 */
final class MxlSpreadsheet {

  private static final int ROWS_START = 15;

  private MxlSpreadsheet() {
  }

  static boolean isMxl(Path file) throws IOException {
    try (var in = Files.newInputStream(file)) {
      byte[] magic = in.readNBytes(6);
      return magic.length == 6 && magic[0] == 'M' && magic[1] == 'O' && magic[2] == 'X'
        && magic[3] == 'C' && magic[4] == 'E' && magic[5] == 'L';
    }
  }

  static Map<String, Object> read(Path file) throws IOException {
    return document(Files.readAllBytes(file)).sheet;
  }

  static void setCellText(Path file, int row, int column, String text) throws IOException {
    byte[] data = Files.readAllBytes(file);
    Document doc = document(data);
    Span span = null;
    for (Span item : doc.spans) {
      if (item.row == row && item.column == column) {
        span = item;
        break;
      }
    }
    if (span == null) {
      throw new IllegalArgumentException("В этой ячейке нет текста.");
    }
    String body = new String(data, 16, data.length - 16, StandardCharsets.UTF_8);
    String next = body.substring(0, span.start) + text.replace("\"", "\"\"") + body.substring(span.end);
    byte[] encoded = next.getBytes(StandardCharsets.UTF_8);
    byte[] out = new byte[16 + encoded.length];
    System.arraycopy(data, 0, out, 0, 16);
    System.arraycopy(encoded, 0, out, 16, encoded.length);
    Files.write(file, out);
  }

  private static Document document(byte[] data) {
    if (data.length < 16 || data[0] != 'M' || data[1] != 'O' || data[2] != 'X'
      || data[3] != 'C' || data[4] != 'E' || data[5] != 'L') {
      throw new IllegalArgumentException("Файл не является табличным документом.");
    }
    if (data[6] == 0 && data[7] == 0 && data[8] == 0 && data[9] == 0 && data[10] == 0
      && (data[11] == 6 || data[11] == 7)) {
      throw new IllegalArgumentException("Файл сохранён в формате 1С 7.7. Его открывает конфигуратор.");
    }
    int versionHigh = (data[7] & 0xff) | ((data[8] & 0xff) << 8);
    int versionLow = (data[9] & 0xff) | ((data[10] & 0xff) << 8);
    if (data[6] != 0 || versionHigh != 8 || versionLow != 1
      || (data[13] & 0xff) != 0xef || (data[14] & 0xff) != 0xbb || (data[15] & 0xff) != 0xbf) {
      throw new IllegalArgumentException("Файл табличного документа повреждён.");
    }
    String text = new String(data, 16, data.length - 16, StandardCharsets.UTF_8);
    List<Val> top = parseBody(text);
    List<Val> root = asList(top.isEmpty() ? null : top.get(0));
    if (root == null) {
      throw new IllegalArgumentException("Файл табличного документа повреждён.");
    }
    List<Span> spans = new ArrayList<>();
    Rows parsed = root.size() > ROWS_START ? parseRows(root, ROWS_START, spans) : null;
    if (parsed == null || parsed.rows.isEmpty()) {
      parsed = null;
      int last = Math.min(40, root.size());
      for (int start = 3; start < last; start++) {
        spans.clear();
        Rows attempt = parseRows(root, start, spans);
        if (attempt != null && !attempt.rows.isEmpty()) {
          parsed = attempt;
          break;
        }
      }
    }
    if (parsed == null) {
      throw new IllegalArgumentException("В файле не нашлось строк табличного документа.");
    }
    Formats tables = findFormats(root, parsed.end);
    List<Map<String, Object>> formats = tables == null ? List.of() : tables.formats;
    int rowCount = 0;
    for (Map<String, Object> row : parsed.rows) {
      rowCount = Math.max(rowCount, ((Number) row.get("index")).intValue() + 1);
    }
    List<Map<String, Object>> merges = List.of();
    for (int i = parsed.end; i < root.size(); i++) {
      List<Map<String, Object>> found = mergesFrom(root.get(i));
      if (found != null) {
        merges = found;
        break;
      }
    }
    Map<String, Object> sheet = new LinkedHashMap<>();
    sheet.put("rowCount", rowCount);
    sheet.put("rows", parsed.rows);
    sheet.put("columnSets", columnSets(root, parsed.end));
    sheet.put("formats", formats);
    sheet.put("fonts", tables == null ? List.of() : tables.fonts);
    sheet.put("lines", findLines(root));
    sheet.put("merges", merges);
    sheet.put("areas", areasFrom(root));
    sheet.put("drawings", List.of());
    return new Document(sheet, spans);
  }

  private static List<Val> parseBody(String text) {
    List<List<Val>> stack = new ArrayList<>();
    stack.add(new ArrayList<>());
    int n = text.length();
    int i = 0;
    while (i < n) {
      char ch = text.charAt(i);
      if (ch == ' ' || ch == '\t' || ch == '\r' || ch == '\n' || ch == ',') {
        i += 1;
        continue;
      }
      if (ch == '{') {
        stack.add(new ArrayList<>());
        i += 1;
        continue;
      }
      if (ch == '}') {
        if (stack.size() < 2) {
          throw new IllegalArgumentException("Файл табличного документа повреждён.");
        }
        List<Val> done = stack.remove(stack.size() - 1);
        stack.get(stack.size() - 1).add(new Lst(done));
        i += 1;
        continue;
      }
      if (ch == '"') {
        StringBuilder s = new StringBuilder();
        i += 1;
        int rawStart = i;
        while (true) {
          if (i >= n) {
            throw new IllegalArgumentException("Файл табличного документа повреждён.");
          }
          if (text.charAt(i) == '"') {
            if (i + 1 < n && text.charAt(i + 1) == '"') {
              s.append('"');
              i += 2;
              continue;
            }
            break;
          }
          s.append(text.charAt(i));
          i += 1;
        }
        int rawEnd = i;
        i += 1;
        stack.get(stack.size() - 1).add(new Str(s.toString(), rawStart, rawEnd));
        continue;
      }
      int start = i;
      while (i < n && " \t\r\n,{}".indexOf(text.charAt(i)) < 0) {
        i += 1;
      }
      String token = text.substring(start, i);
      if (token.matches("-?\\d+")) {
        try {
          stack.get(stack.size() - 1).add(new Num(Long.parseLong(token)));
          continue;
        } catch (NumberFormatException ignored) {
          // слишком длинное число остаётся атомом
        }
      }
      stack.get(stack.size() - 1).add(new Atom(token));
    }
    if (stack.size() != 1) {
      throw new IllegalArgumentException("Файл табличного документа повреждён.");
    }
    return stack.get(0);
  }

  private static Rows parseRows(List<Val> root, int start, List<Span> spans) {
    Long nrows = numAt(root, start);
    if (nrows == null || nrows < 0 || nrows > 1_000_000) {
      return null;
    }
    int i = start + 1;
    List<Map<String, Object>> rows = new ArrayList<>();
    long prev = -1;
    for (long r = 0; r < nrows; r++) {
      Long index = numAt(root, i);
      Long format = numAt(root, i + 1);
      Long ncells = numAt(root, i + 2);
      if (index == null || format == null || ncells == null || index <= prev || ncells < 0 || ncells > 100_000) {
        return null;
      }
      prev = index;
      i += 3;
      List<Map<String, Object>> cells = new ArrayList<>();
      for (long c = 0; c < ncells; c++) {
        Long column = numAt(root, i);
        List<Val> cellFields = asList(i + 1 < root.size() ? root.get(i + 1) : null);
        if (column == null || column < 0 || cellFields == null) {
          return null;
        }
        CellText cell = cellText(cellFields);
        if (cell != null && (cell.text != null || cell.format > 0)) {
          Map<String, Object> item = new LinkedHashMap<>();
          item.put("column", column.intValue());
          item.put("text", cell.text == null ? "" : cell.text);
          item.put("formatIndex", cell.format);
          item.put("parameter", false);
          item.put("sourceIndex", index.intValue());
          cells.add(item);
          if (cell.text != null && cell.start >= 0) {
            spans.add(new Span(index.intValue(), column.intValue(), cell.start, cell.end));
          }
        }
        i += 2;
      }
      Map<String, Object> row = new LinkedHashMap<>();
      row.put("index", index.intValue());
      row.put("sourceIndex", index.intValue());
      row.put("formatIndex", Math.max(0, format.intValue()));
      row.put("cells", cells);
      rows.add(row);
    }
    return new Rows(rows, i);
  }

  private static CellText cellText(List<Val> fields) {
    if (fields.size() < 2 || !(fields.get(0) instanceof Num marker)) {
      return null;
    }
    int format = (int) Math.max(0, fields.get(1) instanceof Num n ? n.value : 0);
    String text = null;
    int start = -1;
    int end = -1;
    int next = 2;
    if (hasBit(marker.value, 1) && next < fields.size() && fields.get(next) instanceof Lst typed
      && typed.items.size() >= 2 && typed.items.get(0) instanceof Str kind && "S".equals(kind.value)
      && typed.items.get(1) instanceof Str value) {
      text = value.value;
      start = value.start;
      end = value.end;
      next += 1;
    } else if (hasBit(marker.value, 1)) {
      next += 1;
    }
    if (hasBit(marker.value, 2)) {
      next += 1;
    }
    if (hasBit(marker.value, 4) && next < fields.size() && fields.get(next) instanceof Lst localized
      && localized.items.size() >= 3 && localized.items.get(1) instanceof Num flag && flag.value == 1
      && localized.items.get(2) instanceof Lst pair && pair.items.size() >= 2 && pair.items.get(1) instanceof Str value) {
      text = value.value;
      start = value.start;
      end = value.end;
    }
    return new CellText(text, format, start, end);
  }

  private static Formats findFormats(List<Val> root, int start) {
    for (int i = start; i + 1 < root.size(); i++) {
      Long n = asNum(root.get(i));
      if (n == null || n <= 0 || n > 10_000 || i + n + 1 >= root.size()) {
        continue;
      }
      boolean entriesOk = true;
      for (int k = 0; k < n; k++) {
        if (asNum(first(asList(root.get(i + 1 + k)))) == null) {
          entriesOk = false;
          break;
        }
      }
      if (!entriesOk) {
        continue;
      }
      int j = i + n.intValue() + 1;
      Long m = asNum(root.get(j));
      if (m == null || m <= 0 || m > 1000 || j + m >= root.size()) {
        continue;
      }
      boolean fontsOk = true;
      for (int k = 0; k < m; k++) {
        Long tag = asNum(first(asList(root.get(j + 1 + k))));
        if (tag == null || (tag != 6 && tag != 7)) {
          fontsOk = false;
          break;
        }
      }
      if (!fontsOk) {
        continue;
      }
      List<Map<String, Object>> formats = new ArrayList<>();
      for (int k = 0; k < n; k++) {
        formats.add(formatFrom(asList(root.get(i + 1 + k))));
      }
      List<Map<String, Object>> fonts = new ArrayList<>();
      for (int k = 0; k < m; k++) {
        fonts.add(fontFrom(asList(root.get(j + 1 + k))));
      }
      return new Formats(formats, fonts);
    }
    return null;
  }

  private static Map<String, Object> formatFrom(List<Val> fields) {
    Map<String, Object> format = new LinkedHashMap<>();
    if (fields == null || fields.isEmpty() || !(fields.get(0) instanceof Num flags) || flags.value < 0) {
      return format;
    }
    int vi = 1;
    for (int bit = 0; bit < 40; bit++) {
      if (!hasBit(flags.value, bit)) {
        continue;
      }
      Long value = vi < fields.size() ? asNum(fields.get(vi)) : null;
      vi += 1;
      if (value == null || value < 0) {
        continue;
      }
      switch (bit) {
        case 0 -> format.put("font", value.intValue());
        case 1 -> format.put("leftBorder", value.intValue());
        case 2 -> format.put("topBorder", value.intValue());
        case 3 -> format.put("rightBorder", value.intValue());
        case 4 -> format.put("bottomBorder", value.intValue());
        case 6 -> format.put("height", value.intValue());
        case 7 -> format.put("width", value.intValue());
        case 8 -> format.put("hAlign", value == 2 ? "right" : value == 4 ? "justify" : value == 6 ? "center" : "left");
        case 9 -> format.put("vAlign", value == 8 ? "bottom" : value == 24 ? "center" : "top");
        case 14 -> format.put("wrap", value == 3);
        default -> {
          // остальные биты формата на показ не влияют
        }
      }
    }
    return format;
  }

  private static Map<String, Object> fontFrom(List<Val> fields) {
    Map<String, Object> font = new LinkedHashMap<>();
    font.put("face", "Arial");
    font.put("sizePt", 10);
    font.put("bold", false);
    font.put("italic", false);
    font.put("underline", false);
    font.put("strikeout", false);
    if (fields != null && fields.size() > 1 && fields.get(1) instanceof Num kind && kind.value == 0) {
      Long height = fields.size() > 3 ? asNum(fields.get(3)) : null;
      if (height != null && height > 0) {
        font.put("sizePt", height / 10.0);
      }
      font.put("bold", num(fields, 7) >= 600);
      font.put("italic", num(fields, 8) != 0);
      font.put("underline", num(fields, 9) != 0);
      font.put("strikeout", num(fields, 10) != 0);
      if (fields.size() > 16 && fields.get(16) instanceof Str face) {
        font.put("face", face.value);
      }
    }
    return font;
  }

  private static List<Map<String, Object>> findLines(List<Val> root) {
    for (Val field : root) {
      List<Val> items = asList(field);
      Long n = items == null ? null : asNum(items.isEmpty() ? null : items.get(0));
      if (items == null || n == null || n < 1 || items.size() != 1 + 3 * n) {
        continue;
      }
      List<Map<String, Object>> lines = new ArrayList<>();
      boolean ok = true;
      for (int k = 0; k < n; k++) {
        Map<String, Object> line = lineFrom(asList(items.get(2 + 3 * k)));
        if (line == null) {
          ok = false;
          break;
        }
        lines.add(line);
      }
      if (ok) {
        return lines;
      }
    }
    return List.of();
  }

  private static Map<String, Object> lineFrom(List<Val> fields) {
    if (fields == null || num(fields, 0) != 4 || num(fields, 1) != 0) {
      return null;
    }
    int kind = (int) Math.max(0, num(fields, 3));
    int thickness = (int) Math.max(0, num(fields, 4));
    String style = kind == 0 || thickness == 0 ? "none"
      : kind == 2 ? "dotted" : kind == 3 ? "double" : kind >= 4 ? "dashed" : "solid";
    Map<String, Object> line = new LinkedHashMap<>();
    line.put("style", style);
    line.put("width", thickness <= 1 ? 1 : thickness);
    return line;
  }

  private static List<Map<String, Object>> mergesFrom(Val value) {
    List<Val> fields = asList(value);
    Long n = fields == null || fields.isEmpty() ? null : asNum(fields.get(0));
    if (fields == null || n == null || n <= 0 || fields.size() != n + 1) {
      return null;
    }
    List<Map<String, Object>> merges = new ArrayList<>();
    for (int i = 1; i < fields.size(); i++) {
      List<Val> rect = asList(fields.get(i));
      if (rect == null || rect.size() != 5) {
        return null;
      }
      long[] nums = new long[5];
      for (int k = 0; k < 5; k++) {
        Long part = asNum(rect.get(k));
        if (part == null) {
          return null;
        }
        nums[k] = part;
      }
      if (nums[4] != 0 || nums[0] < 0 || nums[1] < 0 || nums[2] < nums[0] || nums[3] < nums[1]) {
        continue;
      }
      Map<String, Object> merge = new LinkedHashMap<>();
      merge.put("row", (int) nums[1]);
      merge.put("column", (int) nums[0]);
      merge.put("rowSpan", (int) (nums[3] - nums[1] + 1));
      merge.put("colSpan", (int) (nums[2] - nums[0] + 1));
      merges.add(merge);
    }
    return merges.isEmpty() ? null : merges;
  }

  private static List<Map<String, Object>> areasFrom(List<Val> root) {
    for (Val field : root) {
      List<Val> items = asList(field);
      Long count = items == null || items.isEmpty() ? null : asNum(items.get(0));
      if (items == null || count == null || count <= 0 || items.size() != 1 + count * 2) {
        continue;
      }
      List<Map<String, Object>> areas = new ArrayList<>();
      boolean ok = true;
      for (int k = 0; k < count; k++) {
        if (!(items.get(1 + k * 2) instanceof Str name) || !(items.get(2 + k * 2) instanceof Lst body)) {
          ok = false;
          break;
        }
        List<Val> rect = body.items.size() > 1 ? asList(body.items.get(1)) : null;
        if (rect == null || rect.size() < 5) {
          ok = false;
          break;
        }
        Long type = asNum(rect.get(0));
        Long beginColumn = asNum(rect.get(1));
        Long beginRow = asNum(rect.get(2));
        Long endColumn = asNum(rect.get(3));
        Long endRow = asNum(rect.get(4));
        if (type == null || beginColumn == null || beginRow == null || endColumn == null || endRow == null) {
          ok = false;
          break;
        }
        Map<String, Object> area = new LinkedHashMap<>();
        area.put("name", name.value);
        area.put("kind", areaKind(type, beginColumn, endColumn, beginRow, endRow));
        area.put("beginRow", beginRow.intValue());
        area.put("endRow", endRow.intValue());
        area.put("beginColumn", beginColumn.intValue());
        area.put("endColumn", endColumn.intValue());
        areas.add(area);
      }
      if (ok) {
        return areas;
      }
    }
    return List.of();
  }

  private static String areaKind(long type, long beginColumn, long endColumn, long beginRow, long endRow) {
    if (type == 2 || (beginRow < 0 && endRow < 0)) {
      return "columns";
    }
    if (beginColumn < 0 && endColumn < 0) {
      return "rows";
    }
    return "rectangle";
  }

  private static List<Map<String, Object>> columnSets(List<Val> root, int start) {
    List<Val> group = start < root.size() ? asList(root.get(start)) : null;
    Map<String, Object> set = new LinkedHashMap<>();
    List<Map<String, Object>> columns = new ArrayList<>();
    set.put("columns", columns);
    if (group == null || group.size() < 4 || !(group.get(3) instanceof Num pairs)
      || pairs.value < 0 || group.size() != 4 + pairs.value * 2) {
      set.put("size", 0);
      return List.of(set);
    }
    set.put("size", group.get(0) instanceof Num size ? Math.max(0, size.value) : 0);
    for (int k = 0; k < pairs.value; k++) {
      Long column = asNum(group.get(4 + k * 2));
      Long format = asNum(group.get(5 + k * 2));
      if (column == null || format == null) {
        set.put("size", 0);
        set.put("columns", List.of());
        return List.of(set);
      }
      Map<String, Object> item = new LinkedHashMap<>();
      item.put("column", column.intValue());
      item.put("formatIndex", format.intValue());
      columns.add(item);
    }
    return List.of(set);
  }

  private static boolean hasBit(long flags, int bit) {
    if (flags <= 0 || bit < 0 || bit > 62) {
      return false;
    }
    return ((flags / (1L << bit)) % 2) == 1;
  }

  private static Long numAt(List<Val> root, int index) {
    return index >= 0 && index < root.size() ? asNum(root.get(index)) : null;
  }

  private static Long asNum(Val value) {
    return value instanceof Num num ? num.value : null;
  }

  private static List<Val> asList(Val value) {
    return value instanceof Lst list ? list.items : null;
  }

  private static Val first(List<Val> items) {
    return items == null || items.isEmpty() ? null : items.get(0);
  }

  private static long num(List<Val> fields, int index) {
    if (fields == null || index >= fields.size() || !(fields.get(index) instanceof Num num)) {
      return 0;
    }
    return num.value;
  }

  private record Num(long value) implements Val {
  }

  private record Str(String value, int start, int end) implements Val {
  }

  private record Atom(String value) implements Val {
  }

  private record Lst(List<Val> items) implements Val {
  }

  private sealed interface Val permits Num, Str, Atom, Lst {
  }

  private record Span(int row, int column, int start, int end) {
  }

  private record Document(Map<String, Object> sheet, List<Span> spans) {
  }

  private record Rows(List<Map<String, Object>> rows, int end) {
  }

  private record Formats(List<Map<String, Object>> formats, List<Map<String, Object>> fonts) {
  }

  private record CellText(String text, int format, int start, int end) {
  }
}
