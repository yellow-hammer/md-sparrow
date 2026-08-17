/*
 * This file is a part of md-sparrow.
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
 * Подписи стандартных реквизитов, которые ставит сама платформа.
 *
 * <p>Синоним стандартного реквизита конфигуратор пишет в файл только переопределённым: у обычного
 * в {@code <xr:Synonym/>} пусто, а подпись на форме всё равно есть. Модель формата её не объявляет:
 * XSD описывает состав узлов, а не то, как платформа их называет.
 *
 * <p>Поэтому словарь снят с самой платформы и лежит в ресурсах файлом
 * {@code standard-labels.json}; как его снимают заново, описано в {@code tools/standard-labels}.
 * Стандартный реквизит, которого в словаре нет, остаётся без подписи, и вызывающая сторона берёт имя.
 */
public final class StandardAttributeLabels {

  private static final String RESOURCE = "standard-labels.json";

  /**
   * Подписи стандартных реквизитов табличной части.
   *
   * <p>Единственное, чего платформа не отдаёт: у стандартных реквизитов табличной части и синоним,
   * и представление пустые, хотя конфигуратор пишет в заголовке колонки {@code N}. Запись сверена
   * с конфигуратором; тот же {@code LineNumber} у регистров платформа называет так же.
   */
  private static final Map<String, String> TABULAR_SECTION = Map.of("LineNumber", "N");

  private static final Labels LABELS = load();

  private StandardAttributeLabels() {
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
   * Подписи стандартных реквизитов объекта.
   *
   * @param kind вид объекта, как его называет {@link MdObjectStructureDto#kind}
   * @return имя стандартного реквизита -&gt; подпись; пустая карта, если у вида их нет
   */
  public static Map<String, String> ofObject(String kind) {
    return LABELS.objects.getOrDefault(tagOf(kind), Map.of());
  }

  /**
   * Подписи стандартных реквизитов табличной части.
   *
   * @return имя стандартного реквизита -&gt; подпись
   */
  public static Map<String, String> ofTabularSection() {
    return TABULAR_SECTION;
  }

  /**
   * Подписи стандартных реквизитов стандартной табличной части: виды субконто плана счетов,
   * базовые и вытесняющие виды расчёта плана видов расчёта.
   *
   * @param kind    вид объекта, как его называет {@link MdObjectStructureDto#kind}
   * @param section имя стандартной табличной части
   * @return имя стандартного реквизита -&gt; подпись; пустая карта, если такой части нет
   */
  public static Map<String, String> ofStandardTabularSection(String kind, String section) {
    Section entry = standardTabularSection(kind, section);
    return entry == null ? Map.of() : entry.standardAttributes();
  }

  /**
   * Подпись самой стандартной табличной части.
   *
   * @param kind    вид объекта, как его называет {@link MdObjectStructureDto#kind}
   * @param section имя стандартной табличной части
   * @return подпись; пустая строка, если такой части нет
   */
  public static String standardTabularSectionLabel(String kind, String section) {
    Section entry = standardTabularSection(kind, section);
    return entry == null ? "" : entry.label();
  }

  private static Section standardTabularSection(String kind, String section) {
    Map<String, Section> sections = LABELS.standardTabularSections.get(tagOf(kind));
    return sections == null ? null : sections.get(section);
  }

  /**
   * Вид объекта в написании формата выгрузки: там он с прописной ({@code Catalog}), в модели
   * md-sparrow со строчной ({@code catalog}).
   */
  private static String tagOf(String kind) {
    if (kind == null || kind.isEmpty()) {
      return "";
    }
    return Character.toUpperCase(kind.charAt(0)) + kind.substring(1);
  }

  private static Labels load() {
    try (InputStream stream = StandardAttributeLabels.class.getResourceAsStream(RESOURCE)) {
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
    private final Map<String, Map<String, String>> objects = new LinkedHashMap<>();
    private final Map<String, Map<String, Section>> standardTabularSections = new LinkedHashMap<>();

    private Labels(JsonObject root) {
      platformVersion = root.get("platformVersion").getAsString();
      JsonObject objectsNode = root.getAsJsonObject("objects");
      for (String kind : objectsNode.keySet()) {
        objects.put(kind, Collections.unmodifiableMap(readLabels(objectsNode.getAsJsonObject(kind))));
      }
      JsonObject sectionsNode = root.getAsJsonObject("standardTabularSections");
      for (String kind : sectionsNode.keySet()) {
        JsonObject kindNode = sectionsNode.getAsJsonObject(kind);
        Map<String, Section> sections = new LinkedHashMap<>();
        for (String name : kindNode.keySet()) {
          JsonObject sectionNode = kindNode.getAsJsonObject(name);
          sections.put(name, new Section(
            sectionNode.get("label").getAsString(),
            Collections.unmodifiableMap(readLabels(sectionNode.getAsJsonObject("standardAttributes")))));
        }
        standardTabularSections.put(kind, Collections.unmodifiableMap(sections));
      }
    }

    private static Map<String, String> readLabels(JsonObject node) {
      Map<String, String> labels = new LinkedHashMap<>();
      for (String name : node.keySet()) {
        String label = node.get(name).getAsString();
        if (!label.isEmpty()) {
          labels.put(name, label);
        }
      }
      return labels;
    }
  }

  private record Section(String label, Map<String, String> standardAttributes) {
  }
}
