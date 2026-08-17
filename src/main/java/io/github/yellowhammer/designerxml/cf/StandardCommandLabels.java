/*
 * This file is a part of md-sparrow.
 *
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package io.github.yellowhammer.designerxml.cf;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Подписи стандартных команд формы, которые ставит сама платформа.
 *
 * <p>Кнопка ссылается на команду именем: {@code Form.StandardCommand.PostAndClose}. Своего
 * заголовка у такой кнопки в файле нет, если его не переопределили, а подписывает её платформа.
 * Модель формата подписи не объявляет: XSD описывает состав узлов, а не то, как платформа их
 * называет; в выгрузках заголовок у таких кнопок записан только переопределённым, поэтому и оттуда
 * умолчания не собрать.
 *
 * <p>Поэтому словарь снят с конфигуратора и лежит в ресурсах файлом
 * {@code standard-command-labels.json}. Команда, которой в словаре нет, остаётся без подписи, и
 * вызывающая сторона берёт имя элемента.
 *
 * <p>Оттуда же приходит сторона панели: часть стандартных команд платформа держит у правого края,
 * хотя выравнивания у кнопки не записано, а умолчание у панели левое.
 */
public final class StandardCommandLabels {

  private static final String RESOURCE = "standard-command-labels.json";

  private static final Labels LABELS = load();

  private StandardCommandLabels() {
  }

  /**
   * Версия платформы, с которой сняты подписи.
   *
   * @return версия вида 8.5.1.1343
   */
  public static String platformVersion() {
    return LABELS.platformVersion;
  }

  /**
   * Подписи стандартных команд самой формы.
   *
   * @return имя команды без приставки {@code Form.StandardCommand.} -&gt; подпись
   */
  public static Map<String, String> ofForm() {
    return LABELS.form;
  }

  /**
   * Стандартные команды, которые платформа держит у правого края командной панели.
   *
   * <p>Записанного выравнивания у таких кнопок нет, а умолчание у панели левое: правый край им
   * задаёт сама платформа тем, что команда стандартная. Порядок списка - тот, в каком команды идут
   * на панели, после подменю «Еще».
   *
   * @return имена команд без приставки {@code Form.StandardCommand.}
   */
  public static List<String> atRight() {
    return LABELS.atRight;
  }

  /**
   * Весь словарь одним объектом: то, что отдаёт операция чтения.
   *
   * @return подписи, правая часть панели и версия платформы
   */
  public static FormStandardCommandsDto dto() {
    FormStandardCommandsDto dto = new FormStandardCommandsDto();
    dto.labels = ofForm();
    dto.atRight = atRight();
    dto.platformVersion = platformVersion();
    return dto;
  }

  private static Labels load() {
    try (InputStream stream = StandardCommandLabels.class.getResourceAsStream(RESOURCE)) {
      if (stream == null) {
        throw new IllegalStateException("ресурс не найден: " + RESOURCE);
      }
      try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
        return new Labels(new Gson().fromJson(reader, JsonObject.class));
      }
    } catch (IOException e) {
      throw new IllegalStateException("не прочитан ресурс " + RESOURCE, e);
    }
  }

  private static final class Labels {
    private final String platformVersion;
    private final Map<String, String> form;
    private final List<String> atRight;

    private Labels(JsonObject root) {
      platformVersion = root.get("platformVersion").getAsString();
      Map<String, String> labels = new LinkedHashMap<>();
      JsonObject formNode = root.getAsJsonObject("form");
      for (String name : formNode.keySet()) {
        String label = formNode.get(name).getAsString();
        if (!label.isEmpty()) {
          labels.put(name, label);
        }
      }
      form = Collections.unmodifiableMap(labels);
      List<String> right = new ArrayList<>();
      JsonArray rightNode = root.getAsJsonArray("atRight");
      if (rightNode != null) {
        for (JsonElement name : rightNode) {
          right.add(name.getAsString());
        }
      }
      atRight = Collections.unmodifiableList(right);
    }
  }
}
