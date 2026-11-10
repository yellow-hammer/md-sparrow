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
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Разбор списка выборки запроса 1С: имена полей для автозаполнения набора данных.
 *
 * <p>Разбирается только текст запроса, без метаданных: платформа при автозаполнении выводит и
 * типы полей, а здесь тип взять неоткуда, поэтому результат - лишь имена. Поля набора даёт
 * последний запрос пакета, который является выборкой: первые запросы пакета обычно готовят
 * временные таблицы ({@code ПОМЕСТИТЬ}, {@code ДОБАВИТЬ}) или уничтожают их ({@code УНИЧТОЖИТЬ}),
 * и их выборка в набор не попадает. У объединения берётся первый запрос; список выборки читается
 * от {@code ВЫБРАТЬ} до следующего предложения верхнего уровня — {@code ИЗ}, {@code ПОМЕСТИТЬ},
 * {@code ОБЪЕДИНИТЬ}, а у запроса без источника {@code ГДЕ}, {@code УПОРЯДОЧИТЬ} и прочих
 * (русские и английские ключевые слова равноправны). Строки {@code "…"}, комментарии
 * {@code //…} и вложенные скобки не рвут выражения: записи делят только запятые верхнего уровня.
 */
final class DcsAutofill {

  /** Поле списка выборки: имя (псевдоним или имя колонки пути) и его выражение. */
  record Field(String name, String expression) {
  }

  /** Пропущенная запись списка выборки: выражение и причина, по которой имя не определить. */
  record Skipped(String expression, String reason) {
  }

  /** Результат разбора списка выборки: поля в порядке запроса и пропущенные записи. */
  record Selection(List<Field> fields, List<Skipped> skipped) {
  }

  /** Первое слово запроса выборки; другое первое слово значит, что список выборки не найти. */
  private static final Set<String> SELECT_WORDS = Set.of("ВЫБРАТЬ", "SELECT");

  /** Префиксы выборки без параметров: к полям не относятся и пропускаются. */
  private static final Set<String> FLAG_PREFIXES = Set.of("РАЗЛИЧНЫЕ", "DISTINCT", "РАЗРЕШЕННЫЕ", "ALLOWED");

  /** Префиксы выборки с числом записей: пропускаются вместе с числом. */
  private static final Set<String> TOP_PREFIXES = Set.of("ПЕРВЫЕ", "TOP");

  /**
   * Слова верхнего уровня, на которых список выборки заканчивается: начало любого следующего
   * предложения. У запроса без источника за списком сразу идёт {@code ГДЕ} — так БСП описывает
   * поля набора-заглушки ({@code ВЫБРАТЬ NULL КАК Поле ГДЕ ЛОЖЬ}), и без этой границы последняя
   * запись списка поглотила бы условие и ушла бы в пропущенные.
   */
  private static final Set<String> LIST_END_WORDS = Set.of(
    "ИЗ", "FROM", "ПОМЕСТИТЬ", "INTO", "ОБЪЕДИНИТЬ", "UNION",
    "ГДЕ", "WHERE", "СГРУППИРОВАТЬ", "GROUP", "ИМЕЮЩИЕ", "HAVING", "УПОРЯДОЧИТЬ", "ORDER",
    "АВТОУПОРЯДОЧИВАНИЕ", "AUTOORDER", "ИТОГИ", "TOTALS", "ИНДЕКСИРОВАТЬ", "INDEX", "ДЛЯ", "FOR");

  /**
   * Предложения из двух слов: граница — только пара целиком. Одно первое слово бывает и именем
   * ({@code КАК Order}, таблица {@code Index}), и список обрывался бы посреди выборки.
   */
  private static final Map<String, String> CLAUSE_SECOND_WORDS = Map.of(
    "СГРУППИРОВАТЬ", "ПО", "GROUP", "BY", "УПОРЯДОЧИТЬ", "ПО", "ORDER", "BY",
    "ИНДЕКСИРОВАТЬ", "ПО", "INDEX", "BY", "ДЛЯ", "ИЗМЕНЕНИЯ", "FOR", "UPDATE");

  /** Слова псевдонима: следующее за ними слово — имя колонки или таблицы, а не предложение. */
  private static final Set<String> ALIAS_WORDS = Set.of("КАК", "AS");

  /** Слова верхнего уровня, с которыми запрос пишет во временную таблицу, а не отдаёт выборку. */
  private static final Set<String> TEMP_TABLE_WORDS = Set.of("ПОМЕСТИТЬ", "INTO", "ДОБАВИТЬ", "ADD");

  /** Путь к полю: идентификаторы через точки, например {@code Т.Поле}. */
  private static final Pattern FIELD_PATH =
    Pattern.compile("[\\p{L}_][\\p{L}\\p{N}_]*(?:\\s*\\.\\s*[\\p{L}_][\\p{L}\\p{N}_]*)*");

  /** Выборка всех полей: {@code *} сама по себе или в конце пути {@code Т.*}. */
  private static final Pattern STAR = Pattern.compile("(?:[\\p{L}_][\\p{L}\\p{N}_]*\\s*\\.\\s*)*\\*");

  /** Псевдоним после КАК/AS обязан быть идентификатором: им станет путь к данным поля. */
  private static final Pattern IDENTIFIER = Pattern.compile("[\\p{L}_][\\p{L}\\p{N}_]*");

  private DcsAutofill() {
  }

  /**
   * Разбирает список выборки последнего запроса пакета, который является выборкой.
   *
   * @param queryText текст запроса набора данных
   * @return поля в порядке выборки и пропущенные записи с причинами
   * @throws IllegalArgumentException если ни один запрос пакета не начинается с ВЫБРАТЬ, в пакете
   *     нет запроса выборки, либо в выборке звёздочка: её разворачивает платформа, и разбор
   *     текста имён не даст
   */
  static Selection parseSelection(String queryText) {
    String text = queryText == null ? "" : queryText;
    int start = -1;
    boolean anySelect = false;
    List<int[]> statements = statements(text);
    for (int k = statements.size() - 1; k >= 0 && start < 0; k--) {
      int[] statement = statements.get(k);
      int[] first = nextWord(text, statement[0]);
      if (first[1] < 0 || first[0] >= statement[1] || !SELECT_WORDS.contains(upper(text, first[0], first[1]))) {
        // УНИЧТОЖИТЬ и прочее, что не выборка: полей набора не даёт
        continue;
      }
      anySelect = true;
      if (!writesTempTable(text, first[1], statement[1])) {
        start = first[0];
      }
    }
    if (start < 0) {
      throw new IllegalArgumentException(anySelect
        ? "В пакете нет запроса выборки: каждый запрос помещает данные во временную таблицу (ПОМЕСТИТЬ, "
          + "ДОБАВИТЬ) или уничтожает её, а полям набора взяться неоткуда."
        : "Текст запроса не начинается с ВЫБРАТЬ (SELECT): список выборки не разобрать.");
    }
    int[] select = nextWord(text, start);
    List<Field> fields = new ArrayList<>();
    List<Skipped> skipped = new ArrayList<>();
    for (String entry : splitTopLevel(text, skipPrefixes(text, select[1]))) {
      parseEntry(entry, fields, skipped);
    }
    return new Selection(List.copyOf(fields), List.copyOf(skipped));
  }

  /**
   * Запросы пакета: границы {@code [начало, конец)} между {@code ;} верхнего уровня. Точка с
   * запятой внутри строки, комментария, скобок или блока {@code {…}} запросы не делит.
   */
  private static List<int[]> statements(String text) {
    List<int[]> out = new ArrayList<>();
    int depth = 0;
    int braces = 0;
    int start = 0;
    int i = 0;
    while (i < text.length()) {
      char c = text.charAt(i);
      if (c == '"') {
        i = stringEnd(text, i) + 1;
        continue;
      }
      if (c == '/' && i + 1 < text.length() && text.charAt(i + 1) == '/') {
        while (i < text.length() && text.charAt(i) != '\n') {
          i++;
        }
        continue;
      }
      if (c == '{') {
        braces++;
      } else if (c == '}') {
        braces = Math.max(0, braces - 1);
      } else if (c == '(') {
        depth++;
      } else if (c == ')') {
        depth = Math.max(0, depth - 1);
      } else if (c == ';' && depth == 0 && braces == 0) {
        out.add(new int[] {start, i});
        start = i + 1;
      }
      i++;
    }
    out.add(new int[] {start, text.length()});
    return out;
  }

  /**
   * Пишет ли запрос во временную таблицу: {@code ПОМЕСТИТЬ}/{@code ДОБАВИТЬ} верхнего уровня на
   * участке {@code [from, to)}. Слово после точки или псевдонима {@code КАК} — имя поля, не ключевое;
   * после {@code &} — имя параметра: {@code &Добавить} в условии не делает выборку записью во
   * временную таблицу, иначе единственный запрос набора получал бы отказ «нет запроса выборки».
   */
  private static boolean writesTempTable(String text, int from, int to) {
    int depth = 0;
    int braces = 0;
    String previous = "";
    char beforeWord = ' ';
    int i = from;
    while (i < to) {
      char c = text.charAt(i);
      if (Character.isLetterOrDigit(c) || c == '_') {
        int end = i;
        while (end < to && (Character.isLetterOrDigit(text.charAt(end)) || text.charAt(end) == '_')) {
          end++;
        }
        String word = upper(text, i, end);
        if (depth == 0 && braces == 0 && beforeWord != '.' && beforeWord != '&'
          && !"КАК".equals(previous) && !"AS".equals(previous) && TEMP_TABLE_WORDS.contains(word)) {
          return true;
        }
        previous = word;
        beforeWord = ' ';
        i = end;
        continue;
      }
      if (c == '"') {
        i = stringEnd(text, i) + 1;
        previous = "";
        continue;
      }
      if (c == '/' && i + 1 < to && text.charAt(i + 1) == '/') {
        while (i < to && text.charAt(i) != '\n') {
          i++;
        }
        continue;
      }
      if (!Character.isWhitespace(c)) {
        beforeWord = c;
        previous = "";
      }
      if (c == '{') {
        braces++;
      } else if (c == '}') {
        braces = Math.max(0, braces - 1);
      } else if (c == '(') {
        depth++;
      } else if (c == ')') {
        depth = Math.max(0, depth - 1);
      }
      i++;
    }
    return false;
  }

  /** Пропускает префиксы выборки (РАЗЛИЧНЫЕ, ПЕРВЫЕ N, РАЗРЕШЕННЫЕ): полей они не называют. */
  private static int skipPrefixes(String text, int from) {
    int i = from;
    while (true) {
      int[] word = nextWord(text, i);
      if (word[1] < 0) {
        return i;
      }
      String token = upper(text, word[0], word[1]);
      if (FLAG_PREFIXES.contains(token)) {
        i = word[1];
        continue;
      }
      if (!TOP_PREFIXES.contains(token)) {
        return i;
      }
      i = word[1];
      int[] count = nextWord(text, i);
      // съедается только число записей: ПЕРВЫЕ без числа - забота валидатора платформы
      if (count[1] > 0 && digits(text, count[0], count[1])) {
        i = count[1];
      }
    }
  }

  /**
   * Режет список выборки по запятым верхнего уровня до его границы.
   *
   * <p>Комментарий заменяется пробелом, чтобы не склеить соседние слова; блок {@code {…}}
   * расширения языка запросов СКД пропускается целиком: это настройка автополей и условий,
   * а не выражение выборки.
   */
  private static List<String> splitTopLevel(String text, int from) {
    List<String> entries = new ArrayList<>();
    StringBuilder entry = new StringBuilder();
    boolean sawComma = false;
    int depth = 0;
    int wordStart = -1;
    int i = from;
    while (i < text.length()) {
      char c = text.charAt(i);
      if (Character.isLetterOrDigit(c) || c == '_') {
        if (wordStart < 0) {
          wordStart = entry.length();
        }
        entry.append(c);
        i++;
        continue;
      }
      // слово закончилось: на верхнем уровне оно могло оказаться границей списка
      if (wordStart >= 0 && depth == 0 && isListEnd(entry, wordStart, text, i)) {
        entry.setLength(wordStart);
        flush(entries, entry, sawComma);
        return entries;
      }
      wordStart = -1;
      if (c == '"') {
        i = copyString(text, i, entry);
        continue;
      }
      if (c == '/' && i + 1 < text.length() && text.charAt(i + 1) == '/') {
        while (i < text.length() && text.charAt(i) != '\n') {
          i++;
        }
        entry.append(' ');
        continue;
      }
      if (c == '{' && depth == 0) {
        i = skipBraces(text, i);
        continue;
      }
      if (c == ';' && depth == 0) {
        // выбранный запрос пакета кончается здесь: дальше идут следующие запросы
        break;
      }
      if (c == ',' && depth == 0) {
        entries.add(entry.toString());
        entry.setLength(0);
        sawComma = true;
        i++;
        continue;
      }
      if (c == '(') {
        depth++;
      } else if (c == ')') {
        depth = Math.max(0, depth - 1);
      }
      entry.append(c);
      i++;
    }
    if (wordStart >= 0 && depth == 0 && isListEnd(entry, wordStart, text, text.length())) {
      entry.setLength(wordStart);
    }
    flush(entries, entry, sawComma);
    return entries;
  }

  /**
   * Слово в конце записи — граница списка выборки. Именем, а не границей, слово остаётся, если оно
   * стоит сразу после точки или {@code &} ({@code Т.Итоги}, {@code &Где}), после {@code КАК}/{@code AS}
   * ({@code Т.Код КАК Итоги}), перед точкой (таблица {@code Итоги.Ссылка}) или если у предложения из двух
   * слов нет второго ({@code КАК Order}): иначе такие имена молча обрывали бы список, и автозаполнение
   * теряло бы колонки.
   *
   * @param after позиция в тексте сразу за словом
   */
  private static boolean isListEnd(StringBuilder entry, int wordStart, String text, int after) {
    String word = entry.substring(wordStart).toUpperCase(Locale.ROOT);
    if (!LIST_END_WORDS.contains(word)) {
      return false;
    }
    int i = wordStart - 1;
    while (i >= 0 && Character.isWhitespace(entry.charAt(i))) {
      i--;
    }
    if (i >= 0 && (entry.charAt(i) == '.' || entry.charAt(i) == '&')) {
      return false;
    }
    int previousEnd = i + 1;
    while (i >= 0 && (Character.isLetterOrDigit(entry.charAt(i)) || entry.charAt(i) == '_')) {
      i--;
    }
    if (previousEnd > i + 1 && ALIAS_WORDS.contains(entry.substring(i + 1, previousEnd).toUpperCase(Locale.ROOT))) {
      return false;
    }
    int[] next = nextWord(text, after);
    if (next[1] < 0 && next[0] < text.length() && text.charAt(next[0]) == '.') {
      return false;
    }
    String second = CLAUSE_SECOND_WORDS.get(word);
    return second == null || (next[1] >= 0 && second.equals(upper(text, next[0], next[1])));
  }

  /** Хвост списка: пустой без единой запятой - это пустой список выборки, а не запись. */
  private static void flush(List<String> entries, StringBuilder entry, boolean sawComma) {
    if (!sawComma && entry.toString().isBlank()) {
      return;
    }
    entries.add(entry.toString());
  }

  /** Копирует строковый литерал целиком: внутри него запятые и ключевые слова - просто текст. */
  private static int copyString(String text, int from, StringBuilder entry) {
    entry.append('"');
    int i = from + 1;
    while (i < text.length()) {
      char c = text.charAt(i);
      entry.append(c);
      i++;
      if (c == '"') {
        if (i < text.length() && text.charAt(i) == '"') {
          // "" внутри строки - экранированная кавычка, литерал продолжается
          entry.append('"');
          i++;
          continue;
        }
        return i;
      }
    }
    return i;
  }

  /** Пропускает блок {@code {…}} расширения языка запросов СКД; строки внутри учитываются. */
  private static int skipBraces(String text, int from) {
    int depth = 0;
    int i = from;
    while (i < text.length()) {
      char c = text.charAt(i);
      if (c == '"') {
        i = copyString(text, i, new StringBuilder());
        continue;
      }
      if (c == '{') {
        depth++;
      } else if (c == '}') {
        depth--;
        if (depth == 0) {
          return i + 1;
        }
      }
      i++;
    }
    return i;
  }

  /**
   * Разбирает одну запись списка: имя - псевдоним после КАК/AS, без него - имя колонки пути
   * ({@link #implicitName}); выражение без того и другого имени не даёт и уходит в пропущенные
   * с причиной.
   */
  private static void parseEntry(String raw, List<Field> fields, List<Skipped> skipped) {
    // пробельные цепочки схлопываются: в ответе выражение показывается одной строкой
    String expression = raw.strip().replaceAll("\\s+", " ");
    if (expression.isEmpty()) {
      skipped.add(new Skipped(expression, "пустое выражение в списке выборки"));
      return;
    }
    if (STAR.matcher(expression).matches()) {
      throw new IllegalArgumentException("В списке выборки звёздочка (" + expression
        + "): её разворачивает платформа, перечислите поля по именам.");
    }
    int[] alias = lastTopLevelAlias(expression);
    if (alias != null) {
      String name = expression.substring(alias[1]).strip();
      if (!IDENTIFIER.matcher(name).matches()) {
        skipped.add(new Skipped(expression, "псевдоним после КАК (AS) не разобран"));
        return;
      }
      fields.add(new Field(name, expression.substring(0, alias[0]).strip()));
      return;
    }
    if (FIELD_PATH.matcher(expression).matches()) {
      fields.add(new Field(implicitName(expression), expression));
      return;
    }
    skipped.add(new Skipped(expression, "у выражения нет псевдонима (КАК), а само оно не путь к полю"));
  }

  /**
   * Имя колонки пути без псевдонима: сегменты после первого склеиваются, {@code Т.Поле} -
   * {@code Поле}, {@code Т.Автор.Наименование} - {@code АвторНаименование}. Так колонку разыменования
   * называет платформа, и её автозаполнение пишет поле набора под этим именем: имя по последнему
   * сегменту не узнало бы такое поле, и каждый ОК конструктора запроса дописывал бы лишнее.
   * Первый сегмент считается алиасом таблицы: по одному тексту источники не узнать, а без алиаса
   * поля пишут лишь в запросе с единственной таблицей.
   */
  private static String implicitName(String path) {
    String[] segments = path.split("\\s*\\.\\s*");
    if (segments.length == 1) {
      return segments[0].strip();
    }
    StringBuilder name = new StringBuilder();
    for (int i = 1; i < segments.length; i++) {
      name.append(segments[i].strip());
    }
    return name.toString();
  }

  /** Последнее слово КАК/AS верхнего уровня записи: {@code [начало, конец)} либо {@code null}. */
  private static int[] lastTopLevelAlias(String expression) {
    int depth = 0;
    int wordStart = -1;
    int[] alias = null;
    // проход с виртуальным пробелом в конце: слово у края записи тоже должно закрыться
    for (int i = 0; i <= expression.length(); i++) {
      char c = i < expression.length() ? expression.charAt(i) : ' ';
      if (Character.isLetterOrDigit(c) || c == '_') {
        if (wordStart < 0) {
          wordStart = i;
        }
        continue;
      }
      if (wordStart >= 0 && depth == 0) {
        String word = expression.substring(wordStart, i).toUpperCase(Locale.ROOT);
        if ("КАК".equals(word) || "AS".equals(word)) {
          alias = new int[] {wordStart, i};
        }
      }
      wordStart = -1;
      if (c == '"') {
        i = stringEnd(expression, i);
      } else if (c == '(') {
        depth++;
      } else if (c == ')') {
        depth = Math.max(0, depth - 1);
      }
    }
    return alias;
  }

  /** Индекс закрывающей кавычки строки, открытой в {@code from}; {@code ""} - экранирование. */
  private static int stringEnd(String expression, int from) {
    int i = from + 1;
    while (i < expression.length()) {
      if (expression.charAt(i) == '"') {
        if (i + 1 < expression.length() && expression.charAt(i + 1) == '"') {
          i += 2;
          continue;
        }
        return i;
      }
      i++;
    }
    return i;
  }

  /**
   * Следующее слово со смещения {@code from}: пробелы и комментарии пропускаются.
   *
   * @return {@code [начало, конец)}; конец {@code -1}, если дальше не слово или текст кончился
   */
  private static int[] nextWord(String text, int from) {
    int i = from;
    while (i < text.length()) {
      char c = text.charAt(i);
      if (Character.isWhitespace(c)) {
        i++;
        continue;
      }
      if (c == '/' && i + 1 < text.length() && text.charAt(i + 1) == '/') {
        while (i < text.length() && text.charAt(i) != '\n') {
          i++;
        }
        continue;
      }
      break;
    }
    if (i >= text.length() || !(Character.isLetterOrDigit(text.charAt(i)) || text.charAt(i) == '_')) {
      return new int[] {i, -1};
    }
    int start = i;
    while (i < text.length() && (Character.isLetterOrDigit(text.charAt(i)) || text.charAt(i) == '_')) {
      i++;
    }
    return new int[] {start, i};
  }

  private static String upper(String text, int from, int to) {
    return text.substring(from, to).toUpperCase(Locale.ROOT);
  }

  /** Только цифры: так выглядит число записей после ПЕРВЫЕ/TOP. */
  private static boolean digits(String text, int from, int to) {
    for (int i = from; i < to; i++) {
      if (!Character.isDigit(text.charAt(i))) {
        return false;
      }
    }
    return true;
  }
}
