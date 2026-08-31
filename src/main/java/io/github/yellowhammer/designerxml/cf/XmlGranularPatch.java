/*
 * This file is a part of md-sparrow.
 *
 * Copyright (c) 2026
 * Ivan Karlo <i.karlo@outlook.com> and contributors
 *
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package io.github.yellowhammer.designerxml.cf;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Точечная правка XML как текста: замены участков исходной строки без пересборки файла.
 *
 * <p>Общая часть для свойств объекта метаданных и свойств элемента формы: посчитать смещения,
 * сохранить отступы и перевод строки исходника, применить правки с конца.
 */
final class XmlGranularPatch {

  /**
   * Отступы разметки между тегами: обязателен перевод строки, иначе пробел -
   * это значение элемента, и схлопывать его нельзя.
   */
  private static final Pattern INTER_TAG_WS = Pattern.compile(">[ \\t]*[\\r\\n]\\s*<");

  /** Пара «открыли и сразу закрыли»: значение элемента пустое, ломать строку нельзя. */
  private static final Pattern EMPTY_ELEMENT = Pattern.compile("<([A-Za-z0-9:_.\\-]+)([^>]*)></\\1>");
  private static final String EMPTY_ELEMENT_MARK = String.valueOf((char) 0);

  private XmlGranularPatch() {
  }

  /** Замена участка {@code [start, end)} на текст; {@code start == end} — вставка. */
  record Replacement(int start, int end, String text) {
  }

  /**
   * Отсеивает повторную правку того же участка и запрещает пересекающиеся правки.
   *
   * Одну и ту же область нельзя править дважды: смещения считаются по исходному тексту, и вторая
   * правка резала бы уже изменённый XML. Повтор с тем же содержимым безвреден и просто отбрасывается,
   * а несовместимое пересечение отменяет точечную запись.
   *
   * @param reps Правки в порядке появления.
   * @return Правки без повторов либо пусто, если правки пересекаются по-разному.
   */
  static Optional<List<Replacement>> withoutOverlaps(List<Replacement> reps) {
    List<Replacement> out = new ArrayList<>();
    for (Replacement candidate : reps) {
      Replacement same = null;
      for (Replacement kept : out) {
        boolean intersects = candidate.start() < kept.end() && kept.start() < candidate.end();
        if (!intersects) {
          continue;
        }
        if (kept.start() == candidate.start() && kept.end() == candidate.end()
          && kept.text().equals(candidate.text())) {
          same = kept;
          continue;
        }
        return Optional.empty();
      }
      if (same == null) {
        out.add(candidate);
      }
    }
    return Optional.of(out);
  }

  /** Применяет правки с конца: смещения посчитаны по исходной строке. */
  static String apply(String xmlUtf8, List<Replacement> reps) {
    List<Replacement> ordered = new ArrayList<>(reps);
    ordered.sort(Comparator.comparingInt(Replacement::start).reversed());
    StringBuilder sb = new StringBuilder(xmlUtf8);
    for (Replacement r : ordered) {
      sb.replace(r.start(), r.end(), r.text());
    }
    return sb.toString();
  }

  /** Раскладывает многоузловой элемент по строкам от отступа заменяемого узла. */
  static String formatReplacementPreservingIndent(String xmlUtf8, int start, String replacementElementXml) {
    if (replacementElementXml == null
      || replacementElementXml.isEmpty()
      || !replacementElementXml.contains("><")) {
      return replacementElementXml;
    }
    String indent = currentLineIndent(xmlUtf8, start);
    String eol = fileEol(xmlUtf8);
    String compact = INTER_TAG_WS.matcher(replacementElementXml.trim()).replaceAll("><");
    if (!compact.contains("><")) {
      return replacementElementXml;
    }
    String[] lines = splitByTags(compact);
    StringBuilder out = new StringBuilder(compact.length() + lines.length * 2);
    int depth = 0;
    for (int i = 0; i < lines.length; i++) {
      String line = lines[i].trim();
      if (line.startsWith("</")) {
        depth = Math.max(0, depth - 1);
      }
      if (i > 0) {
        out.append(eol);
        out.append(indent);
        out.append("\t".repeat(depth));
      }
      out.append(line);
      if (isOpeningTagWithoutInlineClose(line)) {
        depth++;
      }
    }
    return out.toString();
  }

  /**
   * Новый узел отдельной строкой с заданным отступом.
   *
   * @param indent Отступ строки узла.
   */
  static String formatInsertion(String xmlUtf8, String indent, String elementXml) {
    String eol = fileEol(xmlUtf8);
    String compact = INTER_TAG_WS.matcher(elementXml.trim()).replaceAll("><");
    String[] lines = splitByTags(compact);
    StringBuilder out = new StringBuilder(compact.length() + indent.length() * lines.length + 2);
    int depth = 0;
    for (int i = 0; i < lines.length; i++) {
      String line = lines[i].trim();
      if (line.startsWith("</")) {
        depth = Math.max(0, depth - 1);
      }
      if (i > 0) {
        out.append(eol);
      }
      out.append(indent).append("\t".repeat(depth)).append(line);
      if (isOpeningTagWithoutInlineClose(line)) {
        depth++;
      }
    }
    return out.toString();
  }

  /** Разбивает фрагмент по тегам, оставляя пустые элементы одной строкой. */
  private static String[] splitByTags(String compact) {
    String guarded = EMPTY_ELEMENT.matcher(compact).replaceAll(m ->
      "<" + m.group(1) + m.group(2) + ">" + EMPTY_ELEMENT_MARK + "</" + m.group(1) + ">");
    String[] lines = guarded.replace("><", ">\n<").split("\n");
    for (int i = 0; i < lines.length; i++) {
      lines[i] = lines[i].replace(EMPTY_ELEMENT_MARK, "");
    }
    return lines;
  }

  private static boolean isOpeningTagWithoutInlineClose(String line) {
    return line.startsWith("<")
      && !line.startsWith("</")
      && !line.endsWith("/>")
      && !line.contains("</");
  }

  /** Отступ строки, в которой начинается участок. */
  static String currentLineIndent(String xmlUtf8, int startOffset) {
    int from = startOffset - 1;
    while (from >= 0 && xmlUtf8.charAt(from) != '\n' && xmlUtf8.charAt(from) != '\r') {
      from--;
    }
    from++;
    int i = from;
    while (i < xmlUtf8.length()) {
      char c = xmlUtf8.charAt(i);
      if (c != ' ' && c != '\t') {
        break;
      }
      i++;
    }
    return xmlUtf8.substring(from, i);
  }

  /** Перевод строки исходного файла: не смешиваем CRLF и LF при точечных заменах. */
  /**
   * Убирает из фрагмента объявления пространств имён, которые уже стоят в корне файла.
   *
   * <p>Конфигуратор объявляет префиксы один раз в корневом теге, поэтому повторное объявление
   * на элементе - лишняя разница в diff, хотя платформа принимает и так.
   *
   * @param xmlUtf8 файл целиком
   * @param fragment вставляемый фрагмент
   * @return фрагмент без повторных объявлений
   */
  /** Объявление пространства имён в корневом теге. */
  private static final Pattern NAMESPACE_DECLARATION =
    Pattern.compile("xmlns:([A-Za-z0-9_.-]+)=\"([^\"]+)\"");

  static String dropRedundantNamespaces(String xmlUtf8, String fragment) {
    Matcher declared = NAMESPACE_DECLARATION.matcher(rootTag(xmlUtf8));
    Map<String, String> byPrefix = new LinkedHashMap<>();
    while (declared.find()) {
      byPrefix.put(declared.group(1), declared.group(2));
    }
    if (byPrefix.isEmpty()) {
      return fragment;
    }
    String out = fragment;
    for (Map.Entry<String, String> entry : byPrefix.entrySet()) {
      String declaration = " xmlns:" + entry.getKey() + "=\"" + entry.getValue() + "\"";
      out = out.replace(declaration, "");
    }
    return out;
  }

  /** Корневой тег файла: в нём конфигуратор и объявляет пространства имён. */
  private static String rootTag(String xmlUtf8) {
    int start = xmlUtf8.indexOf("<MetaDataObject");
    if (start < 0) {
      return "";
    }
    int end = xmlUtf8.indexOf('>', start);
    return end < 0 ? "" : xmlUtf8.substring(start, end);
  }

  static String fileEol(String xmlUtf8) {
    return xmlUtf8.contains("\r\n") ? "\r\n" : "\n";
  }
}
