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
package io.github.yellowhammer.bsl;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Строковые версии в исходном коде регистрации БСП. */
public final class BspVersions {
  private static final String VERSION = "версия";
  private static final String LIBRARY_METHOD = "придобавленииподсистемы";
  private static final String BSP = "ОбновлениеИнформационнойБазыБСП";
  private static final String REGISTRY = "ПодсистемыКонфигурацииПереопределяемый";
  private static final String IDENTIFIER = "[\\p{L}_][\\p{L}\\p{N}_]*";
  // Длинные строки разбираются порциями без рекурсивного перебора по символам.
  private static final Pattern LEXEME = Pattern.compile(
    "#[^\\r\\n]*|//[^\\r\\n]*|\"(?:[^\"\\r\\n]++|\"\"|\\R[ \\t]*+\\|)*+\"|'[^'\\r\\n]*'|"
      + IDENTIFIER + "|[^\\s\\uFEFF]");
  private static final Set<String> BRANCH = Set.of(
    "тогда", "then", "иначе", "else", "цикл", "do", "попытка", "try", "исключение", "except");
  private static final Set<String> END_BRANCH = Set.of(
    "конецесли", "endif", "конеццикла", "enddo", "конецпопытки", "endtry");

  private BspVersions() {
  }

  public record Library(String name, String version, String file) {
  }

  private record Token(String text, int start, int end) {
    String name() {
      return text.toLowerCase(Locale.ROOT);
    }

    boolean isString() {
      return text.startsWith("\"") && text.length() >= 2;
    }
  }

  private record Method(String parameter, List<Token> body) {
  }

  private record Statement(List<Token> tokens, boolean conditional) {
  }

  private record Assignment(List<Token> value, boolean conditional) {
    Token literal() {
      return conditional ? null : stringToken(value);
    }
  }

  private record Declaration(String name, Assignment version) {
  }

  private static final class Structure {
    private Token version;

    Structure(Token version) {
      this.version = version;
    }
  }

  /** Версия, записанная литералом в возвращаемой структуре. */
  public static String externalVersion(Path module) throws IOException {
    return stringValue(externalLiteral(readModule(module)));
  }

  public static void setExternalVersion(Path module, String version) throws IOException {
    String source = readModule(module);
    replace(module, source, externalLiteral(source), version);
  }

  public static void setLibraryVersion(Path module, String version) throws IOException {
    String source = readModule(module);
    Declaration declaration = libraryDeclaration(source);
    Token token = declaration == null || declaration.version == null ? null : declaration.version.literal();
    replace(module, source, token, version);
  }

  /** Список библиотек основной конфигурации по порядку регистрации с обязательным модулем БСП. */
  public static List<Library> libraries(Path configuration, boolean edt, String configurationName) throws IOException {
    Path configurationDir = configuration.toAbsolutePath().getParent();
    Path commonModules = (edt ? configurationDir.getParent() : configurationDir).resolve("CommonModules");
    if (!Files.isDirectory(commonModules)) {
      return List.of();
    }
    Map<String, Path> byName = new HashMap<>();
    try (var directories = Files.list(commonModules)) {
      directories.filter(Files::isDirectory).forEach(dir -> byName.put(lower(dir.getFileName().toString()),
        dir.resolve(edt ? "Module.bsl" : "Ext/Module.bsl")));
    }
    Set<String> registered = new LinkedHashSet<>();
    registered.add(lower(BSP));
    Method registry = method(readModule(byName.get(lower(REGISTRY))), "придобавленииподсистем", false);
    if (registry != null) {
      for (Statement statement : statements(registry)) {
        List<Token> tokens = statement.tokens;
        if (tokens.size() == 6 && at(tokens, 0).equals(registry.parameter) && at(tokens, 1).equals(".")
            && Set.of("добавить", "add").contains(at(tokens, 2))
            && at(tokens, 3).equals("(") && at(tokens, 5).equals(")")) {
          String name = stringValue(stringToken(tokens.subList(4, 5)));
          if (name != null && name.matches(IDENTIFIER)) {
            registered.add(lower(name));
          }
        }
      }
    }
    List<Library> libraries = new ArrayList<>();
    for (String name : registered) {
      Path file = byName.get(name);
      String source = readModule(file);
      Declaration declaration = libraryDeclaration(source);
      if (declaration != null && !declaration.name.equalsIgnoreCase(configurationName)) {
        String version = libraryDisplayVersion(declaration.version, source, byName);
        libraries.add(new Library(declaration.name, version, file.toString()));
      }
    }
    return libraries;
  }

  private static String libraryDisplayVersion(Assignment assignment, String source, Map<String, Path> byName)
      throws IOException {
    if (assignment == null || assignment.conditional) {
      return null;
    }
    List<Token> expression = assignment.value;
    Token literal = assignment.literal();
    if (literal == null && at(expression, expression.size() - 2).equals("(")
        && at(expression, expression.size() - 1).equals(")")) {
      if (expression.size() == 3) {
        literal = helperLiteral(source, at(expression, 0));
      } else if (expression.size() == 5 && at(expression, 1).equals(".")) {
        literal = helperLiteral(readModule(byName.get(at(expression, 0))), at(expression, 2));
      }
    }
    return stringValue(literal);
  }

  private static Token helperLiteral(String source, String name) {
    Method helper = method(source, name, true);
    if (helper == null) {
      return null;
    }
    List<Statement> body = statements(helper);
    if (body.size() != 1 || !isReturn(at(body.getFirst().tokens, 0))) {
      return null;
    }
    return stringToken(body.getFirst().tokens.subList(1, body.getFirst().tokens.size()));
  }

  private static Declaration libraryDeclaration(String source) {
    Method registration = method(source, LIBRARY_METHOD, false);
    if (registration == null || registration.parameter.isEmpty()) {
      return null;
    }
    String name = null;
    Assignment version = null;
    for (Statement statement : statements(registration)) {
      List<Token> tokens = statement.tokens;
      if (at(tokens, 0).equals(registration.parameter) && at(tokens, 1).equals(".")
          && at(tokens, 3).equals("=")) {
        Assignment assignment = new Assignment(tokens.subList(4, tokens.size()), statement.conditional);
        switch (at(tokens, 2)) {
          case "имя" -> name = stringValue(assignment.literal());
          case VERSION -> version = assignment;
          default -> { }
        }
      }
    }
    return name == null ? null : new Declaration(name, version);
  }

  private static Token externalLiteral(String source) {
    Method registration = method(source, "сведенияовнешнейобработке", true);
    if (registration == null) {
      return null;
    }
    Map<String, Token> scalars = new HashMap<>();
    Map<String, Structure> structures = new HashMap<>();
    Set<Token> returns = new LinkedHashSet<>();
    for (Statement statement : statements(registration)) {
      List<Token> tokens = statement.tokens;
      String receiver = at(tokens, 0);
      if (isReturn(receiver)) {
        returns.add(structure(tokens.subList(1, tokens.size()), scalars, structures).version);
      } else if (at(tokens, 1).equals("=")) {
        List<Token> value = tokens.subList(2, tokens.size());
        scalars.put(receiver, statement.conditional ? null : scalarValue(value, scalars));
        Structure assigned = structure(value, scalars, structures);
        if (statement.conditional) {
          assigned.version = null;
          Structure previous = structures.get(receiver);
          if (previous != null) {
            previous.version = null;
          }
        }
        structures.put(receiver, assigned);
      } else if (at(tokens, 1).equals(".")) {
        updateStructure(statement, scalars, structures);
      }
    }
    return returns.size() == 1 ? returns.iterator().next() : null;
  }

  private static void updateStructure(Statement statement, Map<String, Token> scalars, Map<String, Structure> structures) {
    List<Token> tokens = statement.tokens;
    String property = at(tokens, 2);
    List<Token> value;
    if (property.equals(VERSION) && at(tokens, 3).equals("=")) {
      value = tokens.subList(4, tokens.size());
    } else if (Set.of("вставить", "insert", "удалить", "delete").contains(property) && at(tokens, 3).equals("(")) {
      List<List<Token>> args = arguments(tokens.subList(3, tokens.size()));
      if (args.isEmpty() || !VERSION.equalsIgnoreCase(stringValue(stringToken(args.getFirst())))) {
        return;
      }
      value = args.size() == 2 && Set.of("вставить", "insert").contains(property) ? args.get(1) : List.of();
    } else {
      return;
    }
    Structure structure = structures.computeIfAbsent(at(tokens, 0), name -> new Structure(null));
    structure.version = statement.conditional ? null : scalarValue(value, scalars);
  }

  private static Structure structure(List<Token> value, Map<String, Token> scalars, Map<String, Structure> structures) {
    if (value.size() == 1 && structures.containsKey(at(value, 0))) {
      return structures.get(at(value, 0));
    }
    return new Structure(constructorVersion(value, scalars));
  }

  private static Token constructorVersion(List<Token> value, Map<String, Token> scalars) {
    if (!Set.of("новый", "new").contains(at(value, 0))
        || !Set.of("структура", "structure").contains(at(value, 1)) || !at(value, 2).equals("(")) {
      return null;
    }
    List<List<Token>> args = arguments(value.subList(2, value.size()));
    String keys = args.isEmpty() ? null : stringValue(stringToken(args.getFirst()));
    if (keys != null) {
      String[] fields = keys.split(",");
      int index = -1;
      for (int i = 0; i < fields.length; i++) {
        if (VERSION.equalsIgnoreCase(fields[i].strip())) {
          if (index != -1) {
            return null;
          }
          index = i + 1;
        }
      }
      if (index >= 0 && index < args.size()) {
        return scalarValue(args.get(index), scalars);
      }
    }
    return null;
  }

  private static Token scalarValue(List<Token> value, Map<String, Token> scalars) {
    Token literal = stringToken(value);
    return literal != null ? literal : value.size() == 1 ? scalars.get(at(value, 0)) : null;
  }

  private static List<List<Token>> arguments(List<Token> tokens) {
    List<List<Token>> result = new ArrayList<>();
    int start = 1;
    int depth = 0;
    for (int i = 1; i < tokens.size(); i++) {
      switch (at(tokens, i)) {
        case "(", "[" -> depth++;
        case ")", "]" -> {
          if (depth == 0) {
            if (i != tokens.size() - 1) {
              return List.of();
            }
            result.add(tokens.subList(start, i));
            return result;
          }
          depth--;
        }
        case "," -> {
          if (depth == 0) {
            result.add(tokens.subList(start, i));
            start = i + 1;
          }
        }
        default -> { }
      }
    }
    return List.of();
  }

  private static Method method(String source, String name, boolean function) {
    Matcher matcher = LEXEME.matcher(source);
    List<Token> tokens = new ArrayList<>();
    while (matcher.find()) {
      if (!matcher.group().startsWith("//")) {
        tokens.add(new Token(matcher.group(), matcher.start(), matcher.end()));
      }
    }
    Set<String> begins = function ? Set.of("функция", "function") : Set.of("процедура", "procedure");
    Set<String> ends = function ? Set.of("конецфункции", "endfunction") : Set.of("конецпроцедуры", "endprocedure");
    Method found = null;
    for (int i = 0; i < tokens.size(); i++) {
      if (!begins.contains(at(tokens, i)) || !at(tokens, i + 1).equals(name) || !at(tokens, i + 2).equals("(")) {
        continue;
      }
      if (found != null) {
        return null;
      }
      int close = i + 3;
      while (close < tokens.size() && !at(tokens, close).equals(")")) {
        close++;
      }
      int body = close + 1;
      if (Set.of("экспорт", "export").contains(at(tokens, body))) {
        body++;
      }
      int end = body;
      while (end < tokens.size() && !ends.contains(at(tokens, end))) {
        end++;
      }
      if (end >= tokens.size()) {
        return null;
      }
      int first = i + 3;
      int parameter = Set.of("знач", "val").contains(at(tokens, first)) ? first + 1 : first;
      found = new Method(parameter < close ? at(tokens, parameter) : "", tokens.subList(body, end));
      i = end;
    }
    return found;
  }

  private static List<Statement> statements(Method method) {
    List<Statement> result = new ArrayList<>();
    List<Token> tokens = method.body;
    int start = 0;
    int level = 0;
    for (int i = 0; i <= tokens.size(); i++) {
      String token = at(tokens, i);
      if (token.startsWith("#")) {
        String directive = token.substring(1).strip().split("\\s+", 2)[0];
        if (Set.of("если", "if").contains(directive)) {
          level++;
        } else if (Set.of("конецесли", "endif").contains(directive)) {
          level = Math.max(0, level - 1);
        }
        start = i + 1;
        continue;
      }
      if (i == tokens.size() || token.equals(";") || END_BRANCH.contains(token)) {
        if (i > start) {
          result.add(new Statement(tokens.subList(start, i), level > 0));
        }
        start = i + 1;
        if (END_BRANCH.contains(token)) {
          level = Math.max(0, level - 1);
        }
      } else if (BRANCH.contains(token)) {
        if (Set.of("если", "if", "для", "for", "пока", "while", "попытка", "try").contains(at(tokens, start))) {
          level++;
        }
        start = i + 1;
      }
    }
    return result;
  }

  private static boolean isReturn(String token) {
    return token.equals("возврат") || token.equals("return");
  }

  private static Token stringToken(List<Token> tokens) {
    return tokens.size() == 1 && tokens.getFirst().isString() ? tokens.getFirst() : null;
  }

  private static String stringValue(Token token) {
    if (token == null) {
      return null;
    }
    String value = token.text.substring(1, token.text.length() - 1).replace("\"\"", "\"")
      .replaceAll("\\R[ \\t]*\\|", "\n").strip();
    return value.isEmpty() ? null : value;
  }

  private static String at(List<Token> tokens, int i) {
    return i >= 0 && i < tokens.size() ? tokens.get(i).name() : "";
  }

  private static String lower(String name) {
    return name.toLowerCase(Locale.ROOT);
  }

  private static String readModule(Path file) throws IOException {
    if (file == null) {
      return "";
    }
    try {
      return Files.readString(file, StandardCharsets.UTF_8);
    } catch (NoSuchFileException e) {
      return "";
    }
  }

  private static void replace(Path module, String source, Token token, String version) throws IOException {
    if (token == null || token.text.contains("\n") || token.text.contains("\r")) {
      throw new IllegalArgumentException("Версию нужно изменить вручную в модуле " + module);
    }
    if (version.isBlank() || version.contains("\n") || version.contains("\r")) {
      throw new IllegalArgumentException("Укажите непустую версию в одну строку.");
    }
    String replacement = "\"" + version.replace("\"", "\"\"") + "\"";
    if (!replacement.equals(token.text)) {
      Files.writeString(module, source.substring(0, token.start) + replacement + source.substring(token.end),
        StandardCharsets.UTF_8);
    }
  }
}
