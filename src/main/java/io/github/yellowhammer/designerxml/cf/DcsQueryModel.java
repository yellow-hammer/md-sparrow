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

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/** Модель запроса 1С, общая для CLI и конструктора в редакторе СКД. */
final class DcsQueryModel {

  private DcsQueryModel() {
  }

  record Field(String name, String expression) {
  }

  record Skipped(String expression, String reason) {
  }

  record Selection(List<Field> fields, List<Skipped> skipped) {
  }

  static synchronized Selection parseSelection(String query) {
    Value selection = Script.API.invokeMember("autofillSelection", query);
    Value error = selection.getMember("error");
    if (error != null) {
      throw new IllegalArgumentException(error.asString());
    }
    List<Field> fields = new ArrayList<>();
    Value selected = selection.getMember("fields");
    for (long i = 0; i < selected.getArraySize(); i++) {
      Value field = selected.getArrayElement(i);
      fields.add(new Field(field.getMember("name").asString(), field.getMember("expression").asString()));
    }
    List<Skipped> skipped = new ArrayList<>();
    Value omitted = selection.getMember("skipped");
    for (long i = 0; i < omitted.getArraySize(); i++) {
      Value entry = omitted.getArrayElement(i);
      skipped.add(new Skipped(entry.getMember("expression").asString(), entry.getMember("reason").asString()));
    }
    return new Selection(List.copyOf(fields), List.copyOf(skipped));
  }

  static synchronized List<String> parametersOf(String query) {
    Value names = Script.API.invokeMember("parametersOf", query);
    List<String> out = new ArrayList<>();
    for (long i = 0; i < names.getArraySize(); i++) {
      out.add(names.getArrayElement(i).asString());
    }
    return List.copyOf(out);
  }

  /** Один контекст на процесс. Входной запрос передаётся строкой в функцию из ресурса JAR. */
  private static final class Script {

    private static final Context CONTEXT = Context.newBuilder("js")
      .option("engine.WarnInterpreterOnly", "false")
      .build();
    private static final Value API = load();

    private static Value load() {
      URL resource = DcsQueryModel.class.getResource("dcs-editor-query-model.js");
      if (resource == null) {
        throw new IllegalStateException("В сборке нет модели запроса СКД.");
      }
      try {
        CONTEXT.eval(Source.newBuilder("js", resource).build());
        return CONTEXT.getBindings("js").getMember("DcsQueryModel");
      } catch (IOException e) {
        throw new UncheckedIOException("Не удалось загрузить модель запроса СКД.", e);
      }
    }
  }
}
