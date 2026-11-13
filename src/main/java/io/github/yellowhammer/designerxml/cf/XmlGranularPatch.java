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
 *
 * <p>Раскладка фрагмента по строкам трогает только разметку между тегами. Текст элемента
 * переносится дословно: многострочное выражение, заголовок или значение не получают отступов
 * на строках продолжения, пустые строки и ведущие табуляции строк сохраняются. Меняется в тексте
 * только вид перевода строки — на перевод строки файла: XML-разбор всё равно отдаёт текст с LF,
 * а платформа хранит многострочный текст в CRLF-файле с CRLF.
 */
final class XmlGranularPatch {

  private XmlGranularPatch() {
  }

  /** Кусок фрагмента: тег (вместе с угловыми скобками) или текст между тегами. */
  private record Token(String text, boolean tag) {
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

  /**
   * Применяет правки с конца: смещения посчитаны по исходной строке. Вставки в одно
   * место встают в порядке списка: из последней в списке применяется первая, и каждая
   * следующая оказывается перед ней.
   */
  static String apply(String xmlUtf8, List<Replacement> reps) {
    List<Integer> order = new ArrayList<>();
    for (int i = 0; i < reps.size(); i++) {
      order.add(i);
    }
    order.sort(Comparator.<Integer>comparingInt(i -> reps.get(i).start()).thenComparingInt(i -> i).reversed());
    StringBuilder sb = new StringBuilder(xmlUtf8);
    for (int i : order) {
      Replacement r = reps.get(i);
      sb.replace(r.start(), r.end(), r.text());
    }
    return sb.toString();
  }

  /** Раскладывает многоузловой элемент по строкам от отступа заменяемого узла. */
  static String formatReplacementPreservingIndent(String xmlUtf8, int start, String replacementElementXml) {
    if (replacementElementXml == null || replacementElementXml.isEmpty()) {
      return replacementElementXml;
    }
    String eol = fileEol(xmlUtf8);
    if (!replacementElementXml.contains("><")) {
      // Один узел остаётся как есть; переводы строк в нём — текст элемента либо уже готовая
      // разметка, и у того и у другого вид перевода строки должен быть файловым
      return lineBreaks(replacementElementXml, eol);
    }
    String indent = currentLineIndent(xmlUtf8, start);
    List<String> lines = layoutLines(replacementElementXml, eol);
    StringBuilder out = new StringBuilder(replacementElementXml.length() + lines.size() * (indent.length() + 4));
    int depth = 0;
    for (int i = 0; i < lines.size(); i++) {
      String line = lines.get(i);
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
    List<String> lines = layoutLines(elementXml, eol);
    StringBuilder out = new StringBuilder(elementXml.length() + lines.size() * (indent.length() + 4));
    int depth = 0;
    for (int i = 0; i < lines.size(); i++) {
      String line = lines.get(i);
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

  /**
   * Приводит переводы строк ({@code \r\n}, одиночные {@code \r} и {@code \n}) к переводу строки
   * файла.
   *
   * <p>Нужно тексту элементов, который пишется в файл: прочитанный многострочный текст приходит
   * с LF (см. описание класса), и без приведения эхо давало бы смешанные концы строк.
   *
   * @param eol перевод строки файла ({@link #fileEol})
   */
  static String lineBreaks(String text, String eol) {
    if (text == null || (text.indexOf('\r') < 0 && text.indexOf('\n') < 0)) {
      return text;
    }
    String lf = text.replace("\r\n", "\n").replace('\r', '\n');
    return "\n".equals(eol) ? lf : lf.replace("\n", eol);
  }

  /**
   * Строки раскладки фрагмента: новая строка — у каждого тега, перед которым стоит тег, кроме
   * закрывающего тега сразу за своим открывающим ({@code <x></x>} — одна строка).
   *
   * <p>Пробельный текст с переводом строки между тегами — разметка исходника, она отбрасывается
   * и расставляется заново. Исключение — единственное содержимое элемента ({@code <x>\n</x>}):
   * пустой элемент платформа пишет самозакрытым, значит это значение, и его нельзя схлопывать.
   * Прочий текст остаётся в строке своего тега дословно, только с переводом строки файла: так
   * продолжения многострочного текста не получают отступа.
   */
  private static List<String> layoutLines(String fragment, String eol) {
    List<Token> tokens = tokens(fragment.trim());
    List<String> lines = new ArrayList<>();
    StringBuilder line = null;
    Token previous = null;
    for (int i = 0; i < tokens.size(); i++) {
      Token token = tokens.get(i);
      if (!token.tag()) {
        Token next = i + 1 < tokens.size() ? tokens.get(i + 1) : null;
        if (isLayoutWhitespace(token.text()) && !(next != null && ownContent(previous, next))) {
          continue;
        }
      }
      boolean breaks = line == null
        || token.tag() && previous != null && previous.tag() && !ownContent(previous, token);
      if (breaks) {
        if (line != null) {
          lines.add(line.toString());
        }
        line = new StringBuilder();
      }
      line.append(token.tag() ? token.text() : lineBreaks(token.text(), eol));
      previous = token;
    }
    if (line != null) {
      lines.add(line.toString());
    }
    return lines;
  }

  /** Пробелы с переводом строки: так выглядит только разметка между тегами. */
  private static boolean isLayoutWhitespace(String text) {
    boolean newline = false;
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      if (c == '\r' || c == '\n') {
        newline = true;
      } else if (c != ' ' && c != '\t') {
        return false;
      }
    }
    return newline;
  }

  /** Закрывающий тег {@code close} закрывает открывающий {@code open}: между ними — содержимое элемента. */
  private static boolean ownContent(Token open, Token close) {
    return open != null && open.tag() && close.tag() && isOpeningTag(open.text())
      && close.text().startsWith("</") && tagName(open.text()).equals(tagName(close.text()));
  }

  private static boolean isOpeningTag(String tag) {
    return tag.startsWith("<") && !tag.startsWith("</") && !tag.startsWith("<!") && !tag.startsWith("<?")
      && !tag.endsWith("/>");
  }

  /** Имя тега с префиксом: {@code <dcsset:item xsi:type="…">} — {@code dcsset:item}. */
  private static String tagName(String tag) {
    int from = tag.startsWith("</") ? 2 : 1;
    int i = from;
    while (i < tag.length() && " \t\r\n/>".indexOf(tag.charAt(i)) < 0) {
      i++;
    }
    return tag.substring(from, i);
  }

  /**
   * Теги и тексты фрагмента по порядку. Конец тега ищется с учётом кавычек атрибутов; секция
   * CDATA — текст, комментарий — тег. Незакрытый тег — текст до конца фрагмента: разметку
   * исправлять не нам, её отвергнет перечитка моделью.
   */
  private static List<Token> tokens(String fragment) {
    List<Token> out = new ArrayList<>();
    int i = 0;
    int length = fragment.length();
    while (i < length) {
      if (fragment.charAt(i) != '<') {
        int next = fragment.indexOf('<', i);
        int end = next < 0 ? length : next;
        out.add(new Token(fragment.substring(i, end), false));
        i = end;
        continue;
      }
      boolean cdata = fragment.startsWith("<![CDATA[", i);
      int end;
      if (cdata) {
        end = closingIndex(fragment, i, "]]>");
      } else if (fragment.startsWith("<!--", i)) {
        end = closingIndex(fragment, i, "-->");
      } else {
        end = tagEndIndex(fragment, i);
      }
      if (end < 0) {
        out.add(new Token(fragment.substring(i), false));
        break;
      }
      out.add(new Token(fragment.substring(i, end), !cdata));
      i = end;
    }
    return out;
  }

  /** Индекс за концом конструкции с маркером {@code closing}; {@code -1} — не закрыта. */
  private static int closingIndex(String fragment, int from, String closing) {
    int at = fragment.indexOf(closing, from);
    return at < 0 ? -1 : at + closing.length();
  }

  /** Индекс за {@code >} тега, начатого в {@code lt}; кавычки атрибутов учитываются. */
  private static int tagEndIndex(String fragment, int lt) {
    char quote = 0;
    for (int i = lt + 1; i < fragment.length(); i++) {
      char c = fragment.charAt(i);
      if (quote != 0) {
        if (c == quote) {
          quote = 0;
        }
      } else if (c == '"' || c == '\'') {
        quote = c;
      } else if (c == '>') {
        return i + 1;
      }
    }
    return -1;
  }

  private static boolean isOpeningTagWithoutInlineClose(String line) {
    return isOpeningTag(line) && !line.contains("</");
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
