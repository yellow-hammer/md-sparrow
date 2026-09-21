/*
 * This file is a part of md-sparrow.
 *
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package io.github.yellowhammer.designerxml.cf;

import io.github.yellowhammer.designerxml.SchemaVersion;
import io.github.yellowhammer.designerxml.reflect.JaxbReflect;

import jakarta.xml.bind.JAXBElement;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Unmarshaller;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Табличный документ: XML выгрузки и EDT ({@code Template.xml}, {@code Template.mxlx})
 * и двоичный файл {@code *.mxl}.
 *
 * <p>Корень XML в XSD объявлен как {@code choice} без повтора, поэтому документ целиком
 * модель не принимает. Читаются его элементы — строки, колонки, форматы, шрифты —
 * типами той же схемы. Файл {@code *.mxl} схемы не имеет, его читает {@link MxlSpreadsheet}
 * в тот же JSON.
 */
public final class SpreadsheetRead {

  private static final String SPREADSHEET = "v8_2_data_spreadsheet";
  private static final String UI = "v8_1_data_ui";

  private SpreadsheetRead() {
  }

  /**
   * Состав листа для просмотра.
   *
   * @param templateXml файл табличного документа
   * @param version     версия схем, которой собран JAXB
   */
  public static Map<String, Object> read(Path templateXml, SchemaVersion version)
    throws IOException, JAXBException {
    if (MxlSpreadsheet.isMxl(templateXml)) {
      return MxlSpreadsheet.read(templateXml);
    }
    String pkg = "io.github.yellowhammer.designerxml.jaxb."
      + version.name().toLowerCase(Locale.ROOT) + ".";
    Class<?> rowsItem = type(pkg, SPREADSHEET, "RowsItem");
    Class<?> columns = type(pkg, SPREADSHEET, "Columns");
    Class<?> format = type(pkg, SPREADSHEET, "Format");
    Class<?> namedItem = type(pkg, SPREADSHEET, "NamedItem");
    Class<?> namedCells = type(pkg, SPREADSHEET, "NamedItemCells");
    Class<?> merge = type(pkg, SPREADSHEET, "Merge");
    Class<?> drawing = type(pkg, SPREADSHEET, "Drawing");
    Class<?> font = type(pkg, UI, "Font");
    Class<?> line = type(pkg, UI, "Line");

    List<Object> rowNodes = new ArrayList<>();
    List<Map<String, Object>> columnSets = new ArrayList<>();
    List<Map<String, Object>> formats = new ArrayList<>();
    List<Map<String, Object>> fonts = new ArrayList<>();
    List<Map<String, Object>> lines = new ArrayList<>();
    List<Map<String, Object>> merges = new ArrayList<>();
    List<Map<String, Object>> areas = new ArrayList<>();
    List<Map<String, Object>> drawings = new ArrayList<>();
    Integer height = null;

    XMLInputFactory factory = XMLInputFactory.newFactory();
    factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
    factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
    Unmarshaller unmarshaller = version.jaxbContext().createUnmarshaller();
    try (InputStream in = Files.newInputStream(templateXml)) {
      XMLStreamReader reader = factory.createXMLStreamReader(in);
      if (!reader.hasNext()) {
        throw new IOException("Табличный документ пуст.");
      }
      reader.next();
      boolean inDocument = false;
      while (true) {
        boolean consumed = false;
        if (reader.getEventType() == XMLStreamConstants.START_ELEMENT) {
          if (!inDocument) {
            inDocument = "document".equals(reader.getLocalName());
          } else {
            String name = reader.getLocalName();
            switch (name) {
              case "rowsItem" -> {
                rowNodes.add(unmarshal(unmarshaller, reader, rowsItem));
                consumed = true;
              }
              case "columns" -> {
                columnSets.add(columnSet(unmarshal(unmarshaller, reader, columns)));
                consumed = true;
              }
              case "format" -> {
                formats.add(format(unmarshal(unmarshaller, reader, format)));
                consumed = true;
              }
              case "font" -> {
                fonts.add(font(unmarshal(unmarshaller, reader, font)));
                consumed = true;
              }
              case "line" -> {
                lines.add(line(unmarshal(unmarshaller, reader, line)));
                consumed = true;
              }
              case "merge" -> {
                merges.add(merge(unmarshal(unmarshaller, reader, merge)));
                consumed = true;
              }
              case "drawing" -> {
                drawings.add(drawing(unmarshal(unmarshaller, reader, drawing)));
                consumed = true;
              }
              case "namedItem" -> {
                String xsi = reader.getAttributeValue("http://www.w3.org/2001/XMLSchema-instance", "type");
                Class<?> kind = xsi != null && xsi.endsWith("NamedItemCells") ? namedCells : namedItem;
                Map<String, Object> area = area(unmarshal(unmarshaller, reader, kind));
                if (area != null) {
                  areas.add(area);
                }
                consumed = true;
              }
              case "height" -> height = integer(reader.getElementText());
              default -> skip(reader);
            }
          }
        }
        if (consumed) {
          continue;
        }
        if (!reader.hasNext()) {
          break;
        }
        reader.next();
      }
    } catch (XMLStreamException e) {
      throw new IOException("Табличный документ не прочитан: " + e.getMessage(), e);
    }

    List<Map<String, Object>> rows = new ArrayList<>();
    for (Object rowNode : rowNodes) {
      Map<String, Object> row = row(rowNode, formats);
      rows.add(row);
      Integer indexTo = (Integer) row.get("indexTo");
      int index = ((Number) row.get("index")).intValue();
      if (indexTo != null && indexTo > index) {
        for (int extra = index + 1; extra <= indexTo; extra++) {
          Map<String, Object> copy = new LinkedHashMap<>(row);
          copy.put("index", extra);
          copy.remove("indexTo");
          rows.add(copy);
        }
        row.remove("indexTo");
      }
    }

    int last = 0;
    for (Map<String, Object> row : rows) {
      last = Math.max(last, ((Number) row.get("index")).intValue() + 1);
    }
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("rowCount", Math.max(height == null ? 0 : height, last));
    out.put("rows", rows);
    out.put("columnSets", columnSets);
    out.put("formats", formats);
    out.put("fonts", fonts);
    out.put("lines", lines);
    out.put("merges", merges);
    out.put("areas", areas);
    out.put("drawings", drawings);
    return out;
  }

  /**
   * Записывает текст ячейки: обычный текст или имя параметра.
   *
   * @param row       строка, как в файле (нумерация с нуля)
   * @param column    колонка, как в файле
   * @param text      новый текст
   * @param parameter {@code true} — правится параметр, иначе локализованный текст.
   *                  У файла {@code *.mxl} параметра нет, пишется текст ячейки
   */
  public static void setCellText(Path file, int row, int column, String text, boolean parameter) throws IOException {
    if (MxlSpreadsheet.isMxl(file)) {
      MxlSpreadsheet.setCellText(file, row, column, text == null ? "" : text);
      return;
    }
    String xml = java.nio.file.Files.readString(file, java.nio.charset.StandardCharsets.UTF_8);
    String updated = replaceCell(xml, row, column, text == null ? "" : text, parameter);
    java.nio.file.Files.writeString(file, updated, java.nio.charset.StandardCharsets.UTF_8);
  }

  private static String replaceCell(String xml, int row, int column, String text, boolean parameter) {
    int pos = 0;
    while (pos < xml.length()) {
      int start = indexOfOpen(xml, "rowsItem", pos);
      if (start < 0) {
        break;
      }
      int end = elementEnd(xml, start);
      int index = elementInt(xml.substring(start, end), "index");
      int indexTo = elementInt(xml.substring(start, end), "indexTo");
      if (indexTo < index) {
        indexTo = index;
      }
      if (row >= index && row <= indexTo) {
        String block = xml.substring(start, end);
        String replaced = replaceInRow(block, column, text, parameter);
        return xml.substring(0, start) + replaced + xml.substring(end);
      }
      pos = end;
    }
    throw new IllegalArgumentException("Ячейка не найдена.");
  }

  private static String replaceInRow(String block, int column, String text, boolean parameter) {
    int rowStart = indexOfOpen(block, "row", 0);
    if (rowStart < 0) {
      throw new IllegalArgumentException("Ячейка не найдена.");
    }
    int rowTagEnd = tagEnd(block, rowStart);
    int rowEnd = elementEnd(block, rowStart);
    int cursor = rowTagEnd;
    int current = 0;
    boolean placed = false;
    while (cursor < rowEnd) {
      int group = indexOfOpen(block, "c", cursor);
      if (group < 0 || group >= rowEnd) {
        break;
      }
      int groupEnd = elementEnd(block, group);
      int explicit = elementInt(block.substring(group, groupEnd), "i");
      if (explicit >= 0) {
        current = explicit;
      } else if (placed) {
        current += 1;
      }
      placed = true;
      if (current == column) {
        String inner = block.substring(group, groupEnd);
        String updated = parameter ? replaceSimple(inner, "parameter", text) : replaceContent(inner, text);
        return block.substring(0, group) + updated + block.substring(groupEnd);
      }
      cursor = groupEnd;
    }
    throw new IllegalArgumentException("Ячейка не найдена.");
  }

  /** Текст локализованной строки: первый {@code content} внутри ячейки. */
  private static String replaceContent(String cell, String text) {
    int open = indexOfOpen(cell, "content", 0);
    if (open < 0) {
      throw new IllegalArgumentException("В этой ячейке нет текста.");
    }
    int from = tagEnd(cell, open);
    int close = cell.indexOf('<', from);
    if (close < 0) {
      throw new IllegalArgumentException("Файл табличного документа повреждён.");
    }
    return cell.substring(0, from) + escapeText(text) + cell.substring(close);
  }

  private static String replaceSimple(String cell, String name, String text) {
    int open = indexOfOpen(cell, name, 0);
    if (open < 0) {
      throw new IllegalArgumentException("В этой ячейке нет параметра.");
    }
    int from = tagEnd(cell, open);
    int close = cell.indexOf('<', from);
    if (close < 0) {
      throw new IllegalArgumentException("Файл табличного документа повреждён.");
    }
    return cell.substring(0, from) + escapeText(text) + cell.substring(close);
  }

  private static String escapeText(String text) {
    return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
  }

  private static int elementInt(String block, String name) {
    int open = indexOfOpen(block, name, 0);
    if (open < 0) {
      return -1;
    }
    int from = tagEnd(block, open);
    int close = block.indexOf('<', from);
    if (close < 0) {
      return -1;
    }
    try {
      return Integer.parseInt(block.substring(from, close).trim());
    } catch (NumberFormatException e) {
      return -1;
    }
  }

  /** Позиция открывающего тега с данным локальным именем. */
  private static int indexOfOpen(String xml, String localName, int from) {
    int i = from;
    while (i < xml.length()) {
      int lt = xml.indexOf('<', i);
      if (lt < 0) {
        return -1;
      }
      if (lt + 1 < xml.length() && xml.charAt(lt + 1) == '/') {
        i = lt + 2;
        continue;
      }
      if (xml.startsWith("<!--", lt) || xml.startsWith("<?", lt) || xml.startsWith("<!", lt)) {
        i = lt + 2;
        continue;
      }
      if (localName.equals(tagName(xml, lt))) {
        return lt;
      }
      i = lt + 1;
    }
    return -1;
  }

  /** Конец элемента, включая закрывающий тег. {@code start} — позиция {@code <}. */
  private static int elementEnd(String xml, int start) {
    String name = tagName(xml, start);
    int after = tagEnd(xml, start);
    if (xml.charAt(after - 2) == '/') {
      return after;
    }
    int depth = 1;
    int i = after;
    while (i < xml.length() && depth > 0) {
      int lt = xml.indexOf('<', i);
      if (lt < 0) {
        break;
      }
      if (xml.startsWith("<!--", lt)) {
        int comment = xml.indexOf("-->", lt);
        i = comment < 0 ? xml.length() : comment + 3;
        continue;
      }
      if (!name.equals(tagName(xml, lt))) {
        i = lt + 1;
        continue;
      }
      boolean close = lt + 1 < xml.length() && xml.charAt(lt + 1) == '/';
      int end = tagEnd(xml, lt);
      boolean self = xml.charAt(end - 2) == '/';
      if (close) {
        depth -= 1;
        if (depth == 0) {
          return end;
        }
      } else if (!self) {
        depth += 1;
      }
      i = end;
    }
    throw new IllegalArgumentException("Файл табличного документа повреждён.");
  }

  private static int tagEnd(String xml, int lt) {
    boolean quote = false;
    for (int i = lt; i < xml.length(); i++) {
      char ch = xml.charAt(i);
      if (ch == '"') {
        quote = !quote;
      } else if (ch == '>' && !quote) {
        return i + 1;
      }
    }
    throw new IllegalArgumentException("Файл табличного документа повреждён.");
  }

  private static String tagName(String xml, int lt) {
    int i = lt + 1;
    if (i < xml.length() && xml.charAt(i) == '/') {
      i += 1;
    }
    int start = i;
    while (i < xml.length()) {
      char ch = xml.charAt(i);
      if (Character.isWhitespace(ch) || ch == '>' || ch == '/') {
        break;
      }
      i += 1;
    }
    String name = xml.substring(start, i);
    int colon = name.indexOf(':');
    return colon < 0 ? name : name.substring(colon + 1);
  }

  private static Class<?> type(String pkg, String schema, String name) {
    try {
      return Class.forName(pkg + schema + "." + name);
    } catch (ClassNotFoundException e) {
      throw new IllegalStateException("Модель табличного документа не собрана: " + name, e);
    }
  }

  private static Object unmarshal(Unmarshaller unmarshaller, XMLStreamReader reader, Class<?> type)
    throws JAXBException {
    JAXBElement<?> element = unmarshaller.unmarshal(reader, type);
    if (element == null || element.getValue() == null) {
      throw new IllegalArgumentException("Пустой элемент табличного документа: " + type.getSimpleName());
    }
    return element.getValue();
  }

  /** Пропускает элемент, которого в просмотре нет: читатель уже стоит на его начале. */
  private static void skip(XMLStreamReader reader) throws XMLStreamException {
    int depth = 1;
    while (depth > 0 && reader.hasNext()) {
      int event = reader.next();
      if (event == XMLStreamConstants.START_ELEMENT) {
        depth++;
      } else if (event == XMLStreamConstants.END_ELEMENT) {
        depth--;
      }
    }
  }

  private static Map<String, Object> row(Object rowsItem, List<Map<String, Object>> formats) {
    Map<String, Object> out = new LinkedHashMap<>();
    int index = integer(JaxbReflect.get(rowsItem, "getIndex"));
    out.put("index", index);
    out.put("sourceIndex", index);
    Integer indexTo = integer(JaxbReflect.getOptional(rowsItem, "getIndexTo"));
    if (indexTo != null && indexTo > index) {
      out.put("indexTo", indexTo);
    }
    Object row = JaxbReflect.get(rowsItem, "getRow");
    int formatIndex = integerOrZero(JaxbReflect.getOptional(row, "getFormatIndex"));
    out.put("formatIndex", formatIndex);
    String columnsId = text(JaxbReflect.getOptional(row, "getColumnsID"));
    if (!columnsId.isEmpty()) {
      out.put("columnsId", columnsId);
    }
    if (flag(row, "isEmpty", "getEmpty")) {
      out.put("empty", true);
    }
    List<Map<String, Object>> cells = new ArrayList<>();
    int column = 0;
    boolean placed = false;
    for (Object group : JaxbReflect.<Object>listOptional(row, "getC")) {
      Integer explicit = integer(JaxbReflect.getOptional(group, "getI"));
      if (explicit != null) {
        column = explicit;
      } else if (placed) {
        column += 1;
      }
      placed = true;
      Object cell = JaxbReflect.get(group, "getC");
      int cellFormat = integerOrZero(JaxbReflect.getOptional(cell, "getF"));
      String parameter = text(JaxbReflect.getOptional(cell, "getParameter"));
      String template = LocalStringSync.first(JaxbReflect.getOptional(cell, "getTl"));
      boolean parameterFill = cellFormat > 0 && cellFormat <= formats.size()
        && Boolean.TRUE.equals(formats.get(cellFormat - 1).get("parameterFill"));
      String value = template;
      boolean isParameter = false;
      if (!parameter.isEmpty() && (parameterFill || template.isEmpty())) {
        value = parameter;
        isParameter = true;
      }
      String detail = text(JaxbReflect.getOptional(cell, "getDetailParameter"));
      if (value.isEmpty() && cellFormat == 0 && detail.isEmpty()) {
        continue;
      }
      Map<String, Object> item = new LinkedHashMap<>();
      item.put("column", column);
      item.put("text", value);
      item.put("formatIndex", cellFormat);
      item.put("parameter", isParameter);
      if (!detail.isEmpty()) {
        item.put("detail", detail);
      }
      cells.add(item);
    }
    out.put("cells", cells);
    return out;
  }

  private static Map<String, Object> columnSet(Object columns) {
    Map<String, Object> out = new LinkedHashMap<>();
    String id = text(JaxbReflect.getOptional(columns, "getId"));
    if (!id.isEmpty()) {
      out.put("id", id);
    }
    out.put("size", integerOrZero(JaxbReflect.getOptional(columns, "getSize")));
    List<Map<String, Object>> items = new ArrayList<>();
    for (Object item : JaxbReflect.<Object>listOptional(columns, "getColumnsItem")) {
      Integer index = integer(JaxbReflect.getOptional(item, "getIndex"));
      Object column = JaxbReflect.getOptional(item, "getColumn");
      Integer format = integer(JaxbReflect.getOptional(column, "getFormatIndex"));
      if (index == null || format == null) {
        continue;
      }
      Map<String, Object> width = new LinkedHashMap<>();
      width.put("column", index);
      width.put("formatIndex", format);
      items.add(width);
    }
    out.put("columns", items);
    return out;
  }

  private static Map<String, Object> format(Object source) {
    Map<String, Object> out = new LinkedHashMap<>();
    putInt(out, "font", JaxbReflect.getOptional(source, "getFont"));
    putInt(out, "leftBorder", JaxbReflect.getOptional(source, "getLeftBorder"));
    putInt(out, "topBorder", JaxbReflect.getOptional(source, "getTopBorder"));
    putInt(out, "rightBorder", JaxbReflect.getOptional(source, "getRightBorder"));
    putInt(out, "bottomBorder", JaxbReflect.getOptional(source, "getBottomBorder"));
    putInt(out, "height", JaxbReflect.getOptional(source, "getHeight"));
    putInt(out, "width", JaxbReflect.getOptional(source, "getWidth"));
    String horizontal = enumText(JaxbReflect.getOptional(source, "getHorizontalAlignment"));
    if (!horizontal.isEmpty()) {
      out.put("hAlign", align(horizontal, true));
    }
    String vertical = enumText(JaxbReflect.getOptional(source, "getVerticalAlignment"));
    if (!vertical.isEmpty()) {
      out.put("vAlign", align(vertical, false));
    }
    String placement = enumText(JaxbReflect.getOptional(source, "getTextPlacement"));
    out.put("wrap", placement.toLowerCase(Locale.ROOT).contains("wrap"));
    String fill = enumText(JaxbReflect.getOptional(source, "getFillType"));
    out.put("parameterFill", fill.toLowerCase(Locale.ROOT).contains("parameter"));
    return out;
  }

  private static Map<String, Object> font(Object source) {
    Map<String, Object> out = new LinkedHashMap<>();
    String face = text(JaxbReflect.getOptional(source, "getFaceName"));
    out.put("face", face.isEmpty() ? "Arial" : face);
    Number height = number(JaxbReflect.getOptional(source, "getHeight"));
    out.put("sizePt", height == null || height.doubleValue() <= 0 ? 10 : height.doubleValue());
    out.put("bold", flag(source, "isBold", "getBold"));
    out.put("italic", flag(source, "isItalic", "getItalic"));
    out.put("underline", flag(source, "isUnderline", "getUnderline"));
    out.put("strikeout", flag(source, "isStrikeout", "getStrikeout"));
    return out;
  }

  private static Map<String, Object> line(Object source) {
    int width = integerOrZero(JaxbReflect.getOptional(source, "getWidth"));
    String style = enumText(JaxbReflect.getOptional(source, "getStyle")).toLowerCase(Locale.ROOT);
    String kind = "solid";
    if (style.contains("none") || width <= 0) {
      kind = "none";
    } else if (style.contains("dot")) {
      kind = "dotted";
    } else if (style.contains("double")) {
      kind = "double";
    } else if (style.contains("dash")) {
      kind = "dashed";
    }
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("style", kind);
    out.put("width", Math.max(width, 1));
    return out;
  }

  private static Map<String, Object> merge(Object source) {
    int row = integerOrZero(JaxbReflect.getOptional(source, "getR"));
    int column = integerOrZero(JaxbReflect.getOptional(source, "getC"));
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("row", row);
    out.put("column", column);
    out.put("rowSpan", integerOrZero(JaxbReflect.getOptional(source, "getH")) + 1);
    out.put("colSpan", integerOrZero(JaxbReflect.getOptional(source, "getW")) + 1);
    String columnsId = text(JaxbReflect.getOptional(source, "getColumnsID"));
    if (!columnsId.isEmpty()) {
      out.put("columnsId", columnsId);
    }
    return out;
  }

  private static Map<String, Object> area(Object source) {
    String name = text(JaxbReflect.getOptional(source, "getName"));
    Object area = JaxbReflect.getOptional(source, "getArea");
    if (name.isEmpty() || area == null) {
      return null;
    }
    String kindName = enumText(JaxbReflect.getOptional(area, "getType")).toLowerCase(Locale.ROOT);
    String kind = kindName.contains("column") ? "columns" : kindName.contains("rectangle") ? "rectangle" : "rows";
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("name", name);
    out.put("kind", kind);
    out.put("beginRow", integerOr(JaxbReflect.getOptional(area, "getBeginRow"), -1));
    out.put("endRow", integerOr(JaxbReflect.getOptional(area, "getEndRow"), -1));
    out.put("beginColumn", integerOr(JaxbReflect.getOptional(area, "getBeginColumn"), -1));
    out.put("endColumn", integerOr(JaxbReflect.getOptional(area, "getEndColumn"), -1));
    String columnsId = text(JaxbReflect.getOptional(area, "getColumnsID"));
    if (!columnsId.isEmpty()) {
      out.put("columnsId", columnsId);
    }
    return out;
  }

  private static Map<String, Object> drawing(Object source) {
    int row = integerOrZero(JaxbReflect.getOptional(source, "getBeginRow"));
    int column = integerOrZero(JaxbReflect.getOptional(source, "getBeginColumn"));
    int endRow = integerOrZero(JaxbReflect.getOptional(source, "getEndRow"));
    int endColumn = integerOrZero(JaxbReflect.getOptional(source, "getEndColumn"));
    String type = enumText(JaxbReflect.getOptional(source, "getDrawingType")).toLowerCase(Locale.ROOT);
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("row", row);
    out.put("column", column);
    out.put("rowSpan", Math.max(1, endRow - row + 1));
    out.put("colSpan", Math.max(1, endColumn - column + 1));
    out.put("label", type.contains("text") ? "Надпись" : "Рисунок");
    return out;
  }

  private static boolean flag(Object source, String... getters) {
    for (String getter : getters) {
      if (JaxbReflect.getOptional(source, getter) instanceof Boolean value) {
        return value;
      }
    }
    return false;
  }

  private static String align(String value, boolean horizontal) {
    String name = value.toLowerCase(Locale.ROOT);
    if (name.contains("center")) {
      return "center";
    }
    if (horizontal && name.contains("right")) {
      return "right";
    }
    if (horizontal && name.contains("justify")) {
      return "justify";
    }
    if (!horizontal && name.contains("bottom")) {
      return "bottom";
    }
    return horizontal ? "left" : "top";
  }

  private static void putInt(Map<String, Object> out, String key, Object value) {
    Integer number = integer(value);
    if (number != null) {
      out.put(key, number);
    }
  }

  private static String enumText(Object value) {
    Object unwrapped = JaxbReflect.value(value);
    if (unwrapped == null) {
      return "";
    }
    if (unwrapped instanceof Enum<?> typed) {
      return typed.name();
    }
    return unwrapped.toString();
  }

  private static String text(Object value) {
    return value == null ? "" : String.valueOf(value);
  }

  private static Number number(Object value) {
    Object unwrapped = JaxbReflect.value(value);
    if (unwrapped instanceof Number typed) {
      return typed;
    }
    if (unwrapped == null) {
      return null;
    }
    try {
      return new BigDecimal(unwrapped.toString());
    } catch (NumberFormatException e) {
      return null;
    }
  }

  private static Integer integer(Object value) {
    Number number = number(value);
    return number == null ? null : number.intValue();
  }

  private static int integerOrZero(Object value) {
    Integer number = integer(value);
    return number == null ? 0 : number;
  }

  private static int integerOr(Object value, int fallback) {
    Integer number = integer(value);
    return number == null ? fallback : number;
  }
}
