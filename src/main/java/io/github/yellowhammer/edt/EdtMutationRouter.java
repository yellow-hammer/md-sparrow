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
package io.github.yellowhammer.edt;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Операции над составом объекта 1С:EDT по именам команд.
 *
 * Команды у обоих форматов одни и те же, а различается только раскладка файлов,
 * поэтому вызывающей программе выбирать формат не приходится.
 */
public final class EdtMutationRouter {

  /**
   * Что правит команда.
   *
   * @param feature вид узла в схеме: {@code attributes}, {@code enumValues}
   * @param owner вид узла-владельца или {@code null}, если узел принадлежит объекту
   */
  private record Target(String feature, String owner) {
  }

  /** Часть имени команды между {@code cf-md-} и действием. */
  private static final Map<String, Target> TARGETS = Map.ofEntries(
      Map.entry("attribute", new Target("attributes", null)),
      Map.entry("tabular-section", new Target("tabularSections", null)),
      Map.entry("tabular-attribute", new Target("attributes", "tabularSections")),
      Map.entry("enum-value", new Target("enumValues", null)),
      Map.entry("dimension", new Target("dimensions", null)),
      Map.entry("resource", new Target("resources", null)),
      Map.entry("command", new Target("commands", null)),
      Map.entry("accounting-flag", new Target("accountingFlags", null)),
      Map.entry("ext-dimension-accounting-flag", new Target("extDimensionAccountingFlags", null)));

  /** Действия, которые команда может просить. */
  private static final List<String> ACTIONS = List.of("add", "rename", "delete", "duplicate", "reorder");

  private EdtMutationRouter() {
  }

  /**
   * Умеет ли формат EDT такую команду.
   *
   * @param operation имя команды: {@code cf-md-attribute-add}
   * @return {@code true}, если команда правит состав объекта
   */
  public static boolean handles(String operation) {
    return operation != null && target(operation) != null && action(operation) != null;
  }

  /**
   * Выполняет команду над составом объекта.
   *
   * @param operation имя команды
   * @param objectMdo файл объекта
   * @param model метамодель EDT
   * @param arguments значения аргументов команды
   * @throws IOException если файл не читается или не пишется
   */
  public static void apply(String operation, Path objectMdo, EdtModel model, Arguments arguments)
      throws IOException {
    Target target = target(operation);
    String action = action(operation);
    if (target == null || action == null) {
      throw new IllegalArgumentException("Команда " + operation + " формату 1С:EDT неизвестна.");
    }
    String owner = target.owner();
    String ownerName = owner == null ? null : arguments.require(arguments.tabularSection(), "tabularSection");

    switch (action) {
      case "add" -> EdtChildMutations.addNested(
          objectMdo, model, owner, ownerName, target.feature(), arguments.require(arguments.name(), "name"));
      case "rename" -> EdtChildMutations.rename(
          objectMdo, owner, ownerName, target.feature(),
          arguments.require(arguments.oldName(), "oldName"),
          arguments.require(arguments.newName(), "newName"));
      case "delete" -> EdtChildMutations.delete(
          objectMdo, owner, ownerName, target.feature(), arguments.require(arguments.name(), "name"));
      case "duplicate" -> EdtChildMutations.duplicate(
          objectMdo, owner, ownerName, target.feature(),
          arguments.require(arguments.sourceName(), "sourceName"),
          arguments.require(arguments.newName(), "newName"));
      case "reorder" -> EdtChildMutations.reorder(
          objectMdo, owner, ownerName, target.feature(), arguments.order());
      default -> throw new IllegalArgumentException("Команда " + operation + " формату 1С:EDT неизвестна.");
    }
  }

  /**
   * Аргументы команды.
   *
   * @param name имя узла
   * @param oldName прежнее имя при переименовании
   * @param newName новое имя
   * @param sourceName имя копируемого узла
   * @param tabularSection имя табличной части
   * @param order имена узлов в нужном порядке
   */
  public record Arguments(
      String name,
      String oldName,
      String newName,
      String sourceName,
      String tabularSection,
      List<String> order) {

    /** Значение обязательного аргумента. */
    String require(String value, String argument) {
      if (value == null || value.isBlank()) {
        throw new IllegalArgumentException("Не задан аргумент: " + argument);
      }
      return value;
    }
  }

  private static Target target(String operation) {
    if (!operation.startsWith("cf-md-")) {
      return null;
    }
    String rest = operation.substring("cf-md-".length());
    for (String action : ACTIONS) {
      if (rest.endsWith("-" + action)) {
        return TARGETS.get(rest.substring(0, rest.length() - action.length() - 1));
      }
    }
    return null;
  }

  private static String action(String operation) {
    for (String action : ACTIONS) {
      if (operation.endsWith("-" + action)) {
        return action;
      }
    }
    return null;
  }
}
