/*
 * This file is a part of md-sparrow.
 *
 * Copyright (c) 2026
 * Ivan Karlo <i.karlo@outlook.com> and contributors
 *
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package io.github.yellowhammer.designerxml.cf;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Подписи того, чего нет в модели формата: права ролей, группы командного
 * интерфейса, стандартные команды объектов.
 *
 * <p>Набор задаёт платформа, а не XSD, поэтому значения лежат ресурсом
 * библиотеки: потребителю незачем держать свою копию и расходиться с ней.
 */
public final class UiLabels {

  private static final String RESOURCE = "ui-labels.json";
  private static final Map<String, Map<String, String>> SECTIONS = load();

  private UiLabels() {
  }

  /** Подписи прав роли: имя права -> подпись. */
  public static Map<String, String> rights() {
    return SECTIONS.getOrDefault("rights", Map.of());
  }

  /** Подписи групп командного интерфейса: имя группы -> подпись. */
  public static Map<String, String> commandGroups() {
    return SECTIONS.getOrDefault("commandGroups", Map.of());
  }

  /** Подписи стандартных команд объекта: имя команды -> подпись. */
  public static Map<String, String> objectStandardCommands() {
    return SECTIONS.getOrDefault("objectStandardCommands", Map.of());
  }

  /**
   * Подписи видов объектов и ссылочных типов: {@code Catalog} и
   * {@code CatalogRef} -> «Справочник».
   */
  public static Map<String, String> objectKinds() {
    return SECTIONS.getOrDefault("objectKinds", Map.of());
  }

  private static Map<String, Map<String, String>> load() {
    try (InputStream stream = UiLabels.class.getResourceAsStream(RESOURCE)) {
      if (stream == null) {
        throw new IllegalStateException("не найден ресурс " + RESOURCE);
      }
      try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
        JsonObject root = new Gson().fromJson(reader, JsonObject.class);
        Map<String, Map<String, String>> out = new LinkedHashMap<>();
        for (String section : root.keySet()) {
          Map<String, String> labels = new LinkedHashMap<>();
          JsonObject node = root.getAsJsonObject(section);
          for (String name : node.keySet()) {
            labels.put(name, node.get(name).getAsString());
          }
          out.put(section, Collections.unmodifiableMap(labels));
        }
        return Collections.unmodifiableMap(out);
      }
    } catch (IOException e) {
      throw new IllegalStateException("не прочитан ресурс " + RESOURCE, e);
    }
  }
}
