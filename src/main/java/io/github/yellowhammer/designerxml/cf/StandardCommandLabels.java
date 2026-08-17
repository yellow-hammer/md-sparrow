/*
 * This file is a part of md-sparrow.
 *
 * SPDX-License-Identifier: LGPL-3.0-or-later
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
 *
 * <p>И оттуда же - чем платформа наполняет панель, у которой своих кнопок нет. Набор снят с пустых
 * форм: на них видно и состав, и порядок, и сторону. В файле формы этого нет вовсе: там записан
 * только флаг автозаполнения.
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
   * Подписи стандартных команд таблицы.
   *
   * <p>Кнопка ссылается на них через сам элемент: {@code Form.Item.<Таблица>.StandardCommand.Add}.
   * Имена частью совпадают с командами формы, а подписи у них свои, поэтому словарь отдельный.
   *
   * @return имя команды без приставки -&gt; подпись
   */
  public static Map<String, String> ofTable() {
    return LABELS.table;
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
   * Чем платформа наполняет командную панель формы, по виду её главного реквизита.
   *
   * <p>Набор снят с пустых форм: тех, где панель платформа набирает целиком. Разбивка идёт по виду
   * главного реквизита ({@code CatalogObject}, {@code DocumentObject}), а не по виду
   * объекта-владельца: форма обработки с главным реквизитом-справочником получает набор
   * справочника. Вид, которого в словаре нет, набора не имеет: вызывающая сторона показывает, что
   * панель наполняет платформа, но чем именно - не знает.
   *
   * @return вид главного реквизита -&gt; кнопки в том порядке, в каком они идут на панели
   */
  public static Map<String, List<FormStandardCommandDto>> ofAutoCommandBar() {
    return LABELS.autoCommandBar;
  }

  /**
   * Чем платформа наполняет командную панель таблицы, по виду её данных.
   *
   * <p>Набор снят с той же пустой формы, что и панель самой формы: у таблицы табличной части
   * ({@code ObjectTablePart}) панель платформа набирает целиком. У динамического списка панель
   * таблицы приходится включать свойством: в выгрузке она везде записана с положением «нет».
   * Кнопку, которую платформа рисует значком, назвать нечем, и у неё заполнено одно представление.
   *
   * @return вид данных таблицы -&gt; кнопки в том порядке, в каком они идут на панели
   */
  public static Map<String, List<FormStandardCommandDto>> ofTableCommandBar() {
    return LABELS.tableCommandBar;
  }

  /**
   * Весь словарь одним объектом: то, что отдаёт операция чтения.
   *
   * @return подписи, правая часть панели, наборы по видам и версия платформы
   */
  public static FormStandardCommandsDto dto() {
    FormStandardCommandsDto dto = new FormStandardCommandsDto();
    dto.labels = ofForm();
    dto.tableLabels = ofTable();
    dto.atRight = atRight();
    dto.autoCommandBar = ofAutoCommandBar();
    dto.tableCommandBar = ofTableCommandBar();
    dto.platformVersion = platformVersion();
    return dto;
  }

  private static Labels load() {
    try (InputStream stream = StandardCommandLabels.class.getResourceAsStream(RESOURCE)) {
      if (stream == null) {
        throw new IllegalStateException("ресурс не найден: " + RESOURCE);
      }
      try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
        Gson gson = new Gson();
        return new Labels(gson, gson.fromJson(reader, JsonObject.class));
      }
    } catch (IOException e) {
      throw new IllegalStateException("не прочитан ресурс " + RESOURCE, e);
    }
  }

  private static final class Labels {
    private final String platformVersion;
    private final Map<String, String> form;
    private final Map<String, String> table;
    private final List<String> atRight;
    private final Map<String, List<FormStandardCommandDto>> autoCommandBar;
    private final Map<String, List<FormStandardCommandDto>> tableCommandBar;

    private Labels(Gson gson, JsonObject root) {
      platformVersion = root.get("platformVersion").getAsString();
      form = readLabels(root, "form");
      table = readLabels(root, "table");
      List<String> right = new ArrayList<>();
      JsonArray rightNode = root.getAsJsonArray("atRight");
      if (rightNode != null) {
        for (JsonElement name : rightNode) {
          right.add(name.getAsString());
        }
      }
      atRight = Collections.unmodifiableList(right);
      autoCommandBar = readBars(gson, root, "autoCommandBar");
      tableCommandBar = readBars(gson, root, "tableCommandBar");
    }

    private static Map<String, String> readLabels(JsonObject root, String node) {
      Map<String, String> labels = new LinkedHashMap<>();
      JsonObject labelsNode = root.getAsJsonObject(node);
      if (labelsNode != null) {
        for (String name : labelsNode.keySet()) {
          String label = labelsNode.get(name).getAsString();
          if (!label.isEmpty()) {
            labels.put(name, label);
          }
        }
      }
      return Collections.unmodifiableMap(labels);
    }

    private static Map<String, List<FormStandardCommandDto>> readBars(
        Gson gson, JsonObject root, String node) {
      Map<String, List<FormStandardCommandDto>> bars = new LinkedHashMap<>();
      JsonObject barsNode = root.getAsJsonObject(node);
      if (barsNode != null) {
        for (String kind : barsNode.keySet()) {
          List<FormStandardCommandDto> buttons = new ArrayList<>();
          for (JsonElement button : barsNode.getAsJsonArray(kind)) {
            buttons.add(gson.fromJson(button, FormStandardCommandDto.class));
          }
          bars.put(kind, Collections.unmodifiableList(buttons));
        }
      }
      return Collections.unmodifiableMap(bars);
    }
  }
}
