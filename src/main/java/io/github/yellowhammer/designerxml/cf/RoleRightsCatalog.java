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

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Права, которые платформа принимает у объекта каждого вида, и связи между ними.
 *
 * <p>Набора прав нет в XSD выгрузки: он снят с платформы загрузкой роли со всеми
 * правами и выгрузкой обратно. Лишнее право платформа молча отбрасывает, порядок
 * прав в выгрузке - её собственный.
 *
 * <p>Связи сняты так же: роль с одним правом платформа дополняет всеми, без которых
 * оно не действует ({@code requires}), а снятое право снимает все, что от него зависят.
 */
public final class RoleRightsCatalog {

  private static final String RESOURCE = "role-rights.json";
  private static final Map<String, Kind> KINDS = load();

  private RoleRightsCatalog() {
  }

  /**
   * Права вида объекта.
   *
   * @param rights права в порядке выгрузки
   * @param requires право -> права, которые платформа выдаёт вместе с ним
   */
  public record Kind(List<String> rights, Map<String, List<String>> requires) {
  }

  /** Права вида; {@code null}, если прав у вида нет или они не сняты с платформы. */
  public static Kind kind(String objectKind) {
    return KINDS.get(objectKind);
  }

  /** Виды, у которых есть права, и их права в порядке платформы. */
  public static Map<String, List<String>> rightsByKind() {
    Map<String, List<String>> out = new LinkedHashMap<>();
    KINDS.forEach((kind, value) -> out.put(kind, value.rights()));
    return out;
  }

  /** Право и все, без которых оно не действует. */
  public static Set<String> withRequired(Kind kind, String right) {
    Set<String> out = new LinkedHashSet<>();
    out.add(right);
    out.addAll(kind.requires().getOrDefault(right, List.of()));
    return out;
  }

  /** Право и все, которые без него не действуют. */
  public static Set<String> withDependent(Kind kind, String right) {
    Set<String> out = new LinkedHashSet<>();
    out.add(right);
    for (Map.Entry<String, List<String>> entry : kind.requires().entrySet()) {
      if (entry.getValue().contains(right)) {
        out.add(entry.getKey());
      }
    }
    return out;
  }

  private static Map<String, Kind> load() {
    try (InputStream stream = RoleRightsCatalog.class.getResourceAsStream(RESOURCE)) {
      if (stream == null) {
        throw new IllegalStateException("не найден ресурс " + RESOURCE);
      }
      try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
        JsonObject root = new Gson().fromJson(reader, JsonObject.class);
        Map<String, Kind> out = new LinkedHashMap<>();
        for (String kind : root.keySet()) {
          JsonObject node = root.getAsJsonObject(kind);
          List<String> rights = strings(node.getAsJsonArray("rights"));
          Map<String, List<String>> requires = new LinkedHashMap<>();
          JsonObject links = node.getAsJsonObject("requires");
          if (links != null) {
            for (String right : links.keySet()) {
              requires.put(right, strings(links.getAsJsonArray(right)));
            }
          }
          out.put(kind, new Kind(List.copyOf(rights), Collections.unmodifiableMap(requires)));
        }
        return Collections.unmodifiableMap(out);
      }
    } catch (IOException e) {
      throw new IllegalStateException("не прочитан ресурс " + RESOURCE, e);
    }
  }

  private static List<String> strings(JsonArray array) {
    List<String> out = new ArrayList<>();
    if (array != null) {
      for (JsonElement item : array) {
        out.add(item.getAsString());
      }
    }
    return out;
  }
}
