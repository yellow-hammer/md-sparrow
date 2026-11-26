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

import io.github.yellowhammer.designerxml.SchemaVersion;

import jakarta.xml.bind.JAXBException;

import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Чтение вариантов настроек схемы компоновки данных ({@code settingsVariant} корня):
 * список вариантов или один вариант целиком.
 *
 * <p>Читается текстовый слой файла, а не JAXB-модель: после анмаршалинга {@code xs:string},
 * {@code dcscor:Field} и {@code dcscor:DesignTimeValue} неразличимы, а редактору нужен
 * {@code xsi:type} дословно как в файле. Варианты берутся только из прямых детей корня:
 * вложенные схемы несут свои {@code settingsVariant}, и их имена не адресуются.
 */
public final class DcsSettingsRead {

  private static final String XSI_NS = "http://www.w3.org/2001/XMLSchema-instance";

  /**
   * Типы значений, которые пишет редактор. Прочие {@code xsi:type}, кроме перечислений
   * {@code dcsset:*}/{@code dcscor:*}, отдаются с {@code opaque:true} (только для чтения).
   * {@code v8ui:ChartType} записываемый: его пишет мастер настроек, лексемы известны из XSD.
   * Под-параметры {@code ТипДиаграммы.*} несут другие типы {@code v8ui:*} и не записываются.
   */
  private static final Set<String> WRITABLE_TYPES = Set.of(
    "xs:string", "xs:boolean", "xs:decimal", "xs:dateTime",
    "dcscor:Field", "dcscor:DesignTimeValue",
    "v8:LocalStringType", "v8:StandardPeriod", "v8:StandardBeginningDate", "v8:ValueListType",
    "v8ui:Color", "v8ui:ChartType");

  /** Десять областей использования элемента условного оформления: элемент файла → ключ DTO. */
  private static final List<Map.Entry<String, String>> USE_IN_AREAS = List.of(
    Map.entry("useInGroup", "group"),
    Map.entry("useInHierarchicalGroup", "hierarchicalGroup"),
    Map.entry("useInOverall", "overall"),
    Map.entry("useInFieldsHeader", "fieldsHeader"),
    Map.entry("useInHeader", "header"),
    Map.entry("useInParameters", "parameters"),
    Map.entry("useInFilter", "filter"),
    Map.entry("useInResourceFieldsHeader", "resourceFieldsHeader"),
    Map.entry("useInOverallHeader", "overallHeader"),
    Map.entry("useInOverallResourceFieldsHeader", "overallResourceFieldsHeader"));

  private DcsSettingsRead() {
  }

  /**
   * Варианты настроек схемы для JSON-ответа.
   *
   * @param templateXml файл схемы (выгрузка или {@code Template.dcs} EDT)
   * @param version не влияет на чтение: настройки одинаковы во всех версиях схем
   * @param variant имя варианта; пусто — только список {@code {name, presentation}}
   * @return {@code {variants: [...]}}: без имени — все прямые варианты корня,
   *     с именем — один вариант с полем {@code settings}
   * @throws IllegalArgumentException если файл не схема компоновки, вариант не найден
   *     или имя встречается дважды
   */
  public static Map<String, Object> info(Path templateXml, SchemaVersion version, String variant)
    throws IOException, JAXBException {
    return ConfigurationLanguage.with(templateXml, () -> {
      String text = Files.readString(templateXml, StandardCharsets.UTF_8);
      List<El> all = parseRoot(text).all("settingsVariant");
      List<Map<String, Object>> variants = new ArrayList<>();
      if (variant == null || variant.isBlank()) {
        for (El el : all) {
          variants.add(variantRow(el, false));
        }
      } else {
        List<El> matched = all.stream().filter(el -> variant.equals(el.childText("name"))).toList();
        if (matched.isEmpty()) {
          throw new IllegalArgumentException("Вариант настроек не найден: " + variant);
        }
        if (matched.size() > 1) {
          throw new IllegalArgumentException("Имя варианта настроек встречается дважды: " + variant);
        }
        variants.add(variantRow(matched.get(0), true));
      }
      Map<String, Object> out = new LinkedHashMap<>();
      out.put("variants", variants);
      return out;
    });
  }

  /** Строка списка вариантов; настройки раскрываются только у запрошенного (ленивая загрузка). */
  private static Map<String, Object> variantRow(El variant, boolean withSettings) {
    Map<String, Object> row = new LinkedHashMap<>();
    row.put("name", variant.childText("name"));
    putLocalized(row, "presentation", variant.child("presentation"));
    if (withSettings) {
      El settings = variant.child("settings");
      row.put("settings", settings == null ? new LinkedHashMap<>() : settingsDto(settings));
    }
    return row;
  }

  // ------------------------------------------------------------------ Settings-DTO

  private static Map<String, Object> settingsDto(El settings) {
    Map<String, Object> out = new LinkedHashMap<>();
    El userFields = settings.child("userFields");
    if (userFields != null) {
      out.put("userFields", userFields.all("item").stream().map(DcsSettingsRead::userField).toList());
    }
    putSection(out, "selection", settings.child("selection"), DcsSettingsRead::selectionItem);
    putSection(out, "filter", settings.child("filter"), DcsSettingsRead::filterItem);
    putSection(out, "dataParameters", settings.child("dataParameters"), DcsSettingsRead::parameterItem);
    putSection(out, "order", settings.child("order"), DcsSettingsRead::orderItem);
    putSection(out, "conditionalAppearance", settings.child("conditionalAppearance"),
      DcsSettingsRead::appearanceItem);
    putSection(out, "outputParameters", settings.child("outputParameters"), DcsSettingsRead::parameterItem);
    putNodes(out, "structure", settings.all("item"));
    if (settings.child("additionalProperties") != null) {
      out.put("hasAdditionalProperties", true);
    }
    putText(out, "itemsViewMode", settings.childText("itemsViewMode"));
    putText(out, "itemsUserSettingID", settings.childText("itemsUserSettingID"));
    putLocalized(out, "itemsUserSettingPresentation", settings.child("itemsUserSettingPresentation"));
    return out;
  }

  /**
   * Секция: {@code items} и хвост секции. Секция без элементов, с одним хвостом
   * ({@code viewMode}/{@code userSettingID}), в выгрузках обычна и сохраняется.
   */
  private static void putSection(
    Map<String, Object> out, String key, El section, Function<El, Map<String, Object>> item) {
    if (section == null) {
      return;
    }
    Map<String, Object> dto = new LinkedHashMap<>();
    dto.put("items", section.all("item").stream().map(item).toList());
    tail(dto, section);
    out.put(key, dto);
  }

  /** Хвост элемента или секции: режим просмотра и неприкосновенный идентификатор настройки. */
  private static void tail(Map<String, Object> dto, El el) {
    putText(dto, "viewMode", el.childText("viewMode"));
    putText(dto, "userSettingID", el.childText("userSettingID"));
    putLocalized(dto, "userSettingPresentation", el.child("userSettingPresentation"));
  }

  /** Пользовательское поле, только для чтения. */
  private static Map<String, Object> userField(El el) {
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("kind", "UserFieldCase".equals(localType(el.type())) ? "case" : "expression");
    out.put("dataPath", el.childText("dataPath"));
    El lwsTitle = el.child("lwsTitle");
    putText(out, "title", lwsTitle != null ? localized(lwsTitle) : el.childText("title"));
    putText(out, "detailExpression", el.childText("detailExpression"));
    putText(out, "totalExpression", el.childText("totalExpression"));
    El cases = el.child("cases");
    if (cases != null) {
      out.put("casesCount", cases.all("item").size());
    }
    return out;
  }

  private static Map<String, Object> selectionItem(El el) {
    String kind = switch (localType(el.type())) {
      case "SelectedItemField" -> "field";
      case "SelectedItemAuto" -> "auto";
      case "SelectedItemFolder" -> "folder";
      default -> throw new IllegalArgumentException("Неизвестный вид поля выбора: " + el.type());
    };
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("kind", kind);
    putFlag(out, "use", el.childText("use"));
    putText(out, "field", el.childText("field"));
    El lwsTitle = el.child("lwsTitle");
    El title = el.child("title");
    if (lwsTitle != null) {
      // заголовок бывает в двух формах; titleKind запоминает исходную, чтобы запись её вернула
      putText(out, "title", localized(lwsTitle));
      out.put("titleKind", "lws");
    } else if (title != null) {
      putText(out, "title", title.text());
      out.put("titleKind", "string");
    }
    putText(out, "placement", el.childText("placement"));
    List<El> nested = el.all("item");
    if (!nested.isEmpty()) {
      out.put("items", nested.stream().map(DcsSettingsRead::selectionItem).toList());
    }
    putText(out, "viewMode", el.childText("viewMode"));
    return out;
  }

  private static Map<String, Object> filterItem(El el) {
    Map<String, Object> out = new LinkedHashMap<>();
    switch (localType(el.type())) {
      case "FilterItemComparison" -> {
        out.put("kind", "comparison");
        putFlag(out, "use", el.childText("use"));
        putText(out, "left", el.childText("left"));
        putText(out, "comparisonType", el.childText("comparisonType"));
        List<El> rights = el.all("right");
        if (!rights.isEmpty()) {
          // right повторяется: NotInList хранит несколько значений повтором элемента
          out.put("right", rights.stream().map(DcsSettingsRead::value).toList());
        }
        putLocalized(out, "presentation", el.child("presentation"));
        putText(out, "application", el.childText("application"));
      }
      case "FilterItemGroup" -> {
        out.put("kind", "group");
        putFlag(out, "use", el.childText("use"));
        putText(out, "groupType", el.childText("groupType"));
        out.put("items", el.all("item").stream().map(DcsSettingsRead::filterItem).toList());
        putLocalized(out, "presentation", el.child("presentation"));
        putText(out, "application", el.childText("application"));
      }
      default -> throw new IllegalArgumentException("Неизвестный вид элемента отбора: " + el.type());
    }
    tail(out, el);
    return out;
  }

  private static Map<String, Object> orderItem(El el) {
    String kind = switch (localType(el.type())) {
      case "OrderItemField" -> "field";
      case "OrderItemAuto" -> "auto";
      default -> throw new IllegalArgumentException("Неизвестный вид элемента сортировки: " + el.type());
    };
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("kind", kind);
    putFlag(out, "use", el.childText("use"));
    putText(out, "field", el.childText("field"));
    putText(out, "orderType", el.childText("orderType"));
    putFlag(out, "autoOrder", el.childText("autoOrder"));
    putFlag(out, "distinctNullValues", el.childText("distinctNullValues"));
    putText(out, "viewMode", el.childText("viewMode"));
    return out;
  }

  /**
   * Параметр данных, вывода или оформления: {@code value} массивом, {@code xsi:nil} даёт
   * {@code [{nil:true}]}. Подчинённые параметры только для чтения.
   */
  private static Map<String, Object> parameterItem(El el) {
    Map<String, Object> out = new LinkedHashMap<>();
    putFlag(out, "use", el.childText("use"));
    out.put("parameter", el.childText("parameter"));
    List<El> values = el.all("value");
    if (!values.isEmpty()) {
      out.put("value", values.stream().map(DcsSettingsRead::value).toList());
    }
    List<El> items = el.all("item");
    if (!items.isEmpty()) {
      out.put("items", items.stream().map(DcsSettingsRead::parameterItem).toList());
    }
    tail(out, el);
    return out;
  }

  private static Map<String, Object> appearanceItem(El el) {
    Map<String, Object> out = new LinkedHashMap<>();
    putFlag(out, "use", el.childText("use"));
    El selection = el.child("selection");
    if (selection != null) {
      List<Map<String, Object>> fields = new ArrayList<>();
      for (El field : selection.all("item")) {
        Map<String, Object> row = new LinkedHashMap<>();
        putFlag(row, "use", field.childText("use"));
        row.put("field", field.childText("field"));
        fields.add(row);
      }
      out.put("fields", fields);
    }
    El filter = el.child("filter");
    if (filter != null) {
      out.put("filter", filter.all("item").stream().map(DcsSettingsRead::filterItem).toList());
    }
    El appearance = el.child("appearance");
    if (appearance != null) {
      out.put("appearance", appearance.all("item").stream().map(DcsSettingsRead::parameterItem).toList());
    }
    putLocalized(out, "presentation", el.child("presentation"));
    Map<String, String> useIn = new LinkedHashMap<>();
    for (Map.Entry<String, String> area : USE_IN_AREAS) {
      String mode = el.childText(area.getKey());
      if (!mode.isEmpty()) {
        useIn.put(area.getValue(), mode);
      }
    }
    if (!useIn.isEmpty()) {
      out.put("useIn", useIn);
    }
    tail(out, el);
    return out;
  }

  /** Поле группировки; платформа всегда пишет все пять элементов. */
  private static Map<String, Object> groupField(El el) {
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("field", el.childText("field"));
    putText(out, "groupType", el.childText("groupType"));
    putText(out, "periodAdditionType", el.childText("periodAdditionType"));
    El begin = el.child("periodAdditionBegin");
    if (begin != null) {
      out.put("periodAdditionBegin", value(begin));
    }
    El end = el.child("periodAdditionEnd");
    if (end != null) {
      out.put("periodAdditionEnd", value(end));
    }
    return out;
  }

  // ------------------------------------------------------------------ структура

  /**
   * Узел структуры, вид по {@code xsi:type}. Строки, колонки, точки, серии и их вложенные группы
   * пишутся без {@code xsi:type} (тип не полиморфный) и читаются как группировка. Настройки
   * вложенного объекта не раскрываются: операции правки их не адресуют.
   */
  private static Map<String, Object> node(El el) {
    String kind = switch (localType(el.type())) {
      case "StructureItemGroup", "" -> "group";
      case "StructureItemTable" -> "table";
      case "StructureItemChart" -> "chart";
      case "StructureItemNestedObject" -> "nestedObject";
      default -> throw new IllegalArgumentException("Неизвестный вид узла структуры: " + el.type());
    };
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("kind", kind);
    putFlag(out, "use", el.childText("use"));
    putText(out, "name", el.childText("name"));
    switch (kind) {
      case "group" -> {
        El groupItems = el.child("groupItems");
        if (groupItems != null) {
          List<Map<String, Object>> groupFields = new ArrayList<>();
          for (El item : groupItems.all("item")) {
            if ("GroupItemField".equals(localType(item.type()))) {
              groupFields.add(groupField(item));
            }
          }
          out.put("groupFields", groupFields);
        }
        putSection(out, "filter", el.child("filter"), DcsSettingsRead::filterItem);
        putSection(out, "order", el.child("order"), DcsSettingsRead::orderItem);
        putSection(out, "selection", el.child("selection"), DcsSettingsRead::selectionItem);
        putSection(out, "conditionalAppearance", el.child("conditionalAppearance"),
          DcsSettingsRead::appearanceItem);
        putSection(out, "outputParameters", el.child("outputParameters"), DcsSettingsRead::parameterItem);
        putNodes(out, "structure", el.all("item"));
      }
      case "table" -> {
        putNodes(out, "columns", el.all("column"));
        putNodes(out, "rows", el.all("row"));
        putSection(out, "selection", el.child("selection"), DcsSettingsRead::selectionItem);
        putSection(out, "conditionalAppearance", el.child("conditionalAppearance"),
          DcsSettingsRead::appearanceItem);
        putSection(out, "outputParameters", el.child("outputParameters"), DcsSettingsRead::parameterItem);
      }
      case "chart" -> {
        putNodes(out, "points", el.all("point"));
        putNodes(out, "series", el.all("series"));
        putSection(out, "selection", el.child("selection"), DcsSettingsRead::selectionItem);
        putSection(out, "conditionalAppearance", el.child("conditionalAppearance"),
          DcsSettingsRead::appearanceItem);
        putSection(out, "outputParameters", el.child("outputParameters"), DcsSettingsRead::parameterItem);
      }
      default -> out.put("objectID", el.childText("objectID"));
    }
    tail(out, el);
    return out;
  }

  private static void putNodes(Map<String, Object> out, String key, List<El> items) {
    if (!items.isEmpty()) {
      out.put(key, items.stream().map(DcsSettingsRead::node).toList());
    }
  }

  // ------------------------------------------------------------------ значения

  /**
   * Типизированное значение: {@code type} ({@code xsi:type} дословно), {@code attrs} (атрибуты
   * шрифта, картинки, линии), {@code opaque:true} у типа вне белого списка записи.
   */
  private static Map<String, Object> value(El el) {
    Map<String, Object> out = new LinkedHashMap<>();
    String type = el.type();
    if (type != null) {
      out.put("type", type);
    }
    if (el.nil()) {
      out.put("nil", true);
    } else {
      switch (type == null ? "" : type) {
        case "v8:LocalStringType" -> putText(out, "decoded", localized(el));
        case "v8:StandardPeriod" -> out.put("period", period(el));
        case "v8:StandardBeginningDate" -> out.put("beginningDate", beginningDate(el));
        case "v8:ValueListType" -> out.put("list", valueList(el));
        default -> {
          putText(out, "text", el.text());
          if ("xs:boolean".equals(type)) {
            out.put("decoded", "true".equals(el.text()) || "1".equals(el.text()));
          } else if ("xs:decimal".equals(type)) {
            putDecimal(out, el.text());
          }
        }
      }
    }
    if (!el.attrs().isEmpty()) {
      out.put("attrs", el.attrs());
    }
    if (opaque(type)) {
      out.put("opaque", true);
    }
    return out;
  }

  private static Map<String, Object> period(El el) {
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("variant", el.childText("variant"));
    putText(out, "startDate", el.childText("startDate"));
    putText(out, "endDate", el.childText("endDate"));
    return out;
  }

  private static Map<String, Object> beginningDate(El el) {
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("variant", el.childText("variant"));
    putText(out, "date", el.childText("date"));
    return out;
  }

  /** Список значений; служебные {@code valueType}/{@code lastId} не отдаются. */
  private static Map<String, Object> valueList(El el) {
    List<Map<String, Object>> items = new ArrayList<>();
    for (El item : el.all("item")) {
      Map<String, Object> row = new LinkedHashMap<>();
      El itemValue = item.child("value");
      row.put("value", itemValue == null ? Map.of("nil", true) : value(itemValue));
      putLocalized(row, "presentation", item.child("presentation"));
      items.add(row);
    }
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("items", items);
    return out;
  }

  private static boolean opaque(String type) {
    if (type == null || type.isEmpty() || WRITABLE_TYPES.contains(type)) {
      return false;
    }
    return !type.startsWith("dcsset:") && !type.startsWith("dcscor:");
  }

  // ------------------------------------------------------------------ фрагменты для сплайса

  /**
   * DTO элемента секции из фрагмента файла в представлении чтения варианта: по нему
   * {@link DcsSettingsEdit} находит элементы запроса, которые не изменились.
   *
   * @param fragment текст элемента {@code item} из файла
   * @param xmlns объявления пространств имён корня, ими оборачивается фрагмент
   * @param section имя элемента секции: {@code selection}, {@code filter}, {@code order},
   *     {@code groupItems}, {@code conditionalAppearance} или {@code appearance}
   */
  static Map<String, Object> sectionItemDto(String fragment, String xmlns, String section) {
    El el = parseFragment(fragment, xmlns);
    return switch (section) {
      case "selection" -> selectionItem(el);
      case "filter" -> filterItem(el);
      case "order" -> orderItem(el);
      case "groupItems" -> groupField(el);
      case "conditionalAppearance" -> appearanceItem(el);
      case "appearance" -> parameterItem(el);
      default -> throw new IllegalArgumentException("Секция не поддержана сплайсом: " + section);
    };
  }

  /**
   * Элемент {@code value} параметра данных или вывода из фрагмента файла в представлении чтения
   * варианта. По нему запись узнаёт эхо прочитанного значения и не переписывает элемент.
   *
   * @param fragment текст элемента {@code value} из файла
   * @param xmlns объявления пространств имён корня, ими оборачивается фрагмент
   */
  static Map<String, Object> settingsValue(String fragment, String xmlns) {
    return value(parseFragment(fragment, xmlns));
  }

  /**
   * Значение параметра схемы ({@code parameter/value} корня) в форме значений настроек: ключ
   * {@code valueTyped} ответа {@code cf-dcs-info} и сверка эха в {@code cf-dcs-set-parameter}.
   * {@code v8:UUID} здесь не {@code opaque}: его пишет {@link DcsValueTypeEdit#parameterValueXml}.
   *
   * @param fragment текст элемента {@code value} из файла
   * @param xmlns объявления пространств имён корня, ими оборачивается фрагмент
   */
  static Map<String, Object> schemaParameterValue(String fragment, String xmlns) {
    El el = parseFragment(fragment, xmlns);
    Map<String, Object> out = value(el);
    if ("v8:UUID".equals(el.type())) {
      out.remove("opaque");
    }
    return out;
  }

  /**
   * Строка локализованного элемента (заголовка, представления) из фрагмента файла, как её отдаёт
   * чтение. По ней запись узнаёт эхо прочитанной строки и не переписывает элемент.
   *
   * @param fragment текст элемента из файла
   * @param xmlns объявления пространств имён корня, ими оборачивается фрагмент
   */
  static String localizedText(String fragment, String xmlns) {
    return localized(parseFragment(fragment, xmlns));
  }

  /** Фрагмент файла деревом текстового слоя; префиксы элементов объявляет обёртка. */
  private static El parseFragment(String fragment, String xmlns) {
    try {
      XMLStreamReader reader = XmlLines.reader("<splice-wrap" + xmlns + ">" + fragment + "</splice-wrap>");
      try {
        boolean wrapper = false;
        while (reader.hasNext()) {
          if (reader.next() != XMLStreamConstants.START_ELEMENT) {
            continue;
          }
          if (!wrapper) {
            wrapper = true;
            continue;
          }
          return readEl(reader);
        }
        throw new IllegalArgumentException("пустой фрагмент элемента секции");
      } finally {
        reader.close();
      }
    } catch (XMLStreamException e) {
      throw new IllegalArgumentException("не удалось разобрать фрагмент: " + e.getMessage(), e);
    }
  }

  // ------------------------------------------------------------------ разбор текста

  /**
   * Узел текстового слоя: {@code attrs} без {@code xsi:type}/{@code xsi:nil}, {@code text}
   * только у элементов без детей.
   */
  private record El(String name, String type, boolean nil, Map<String, String> attrs, String text,
                    List<El> children) {

    El child(String childName) {
      for (El el : children) {
        if (el.name.equals(childName)) {
          return el;
        }
      }
      return null;
    }

    List<El> all(String childName) {
      List<El> out = new ArrayList<>();
      for (El el : children) {
        if (el.name.equals(childName)) {
          out.add(el);
        }
      }
      return out;
    }

    String childText(String childName) {
      El el = child(childName);
      return el == null ? "" : el.text;
    }
  }

  private static El parseRoot(String xml) {
    try {
      XMLStreamReader reader = XmlLines.reader(xml);
      try {
        while (reader.hasNext()) {
          if (reader.next() == XMLStreamConstants.START_ELEMENT) {
            if (!"DataCompositionSchema".equals(reader.getLocalName())) {
              throw new IllegalArgumentException("Файл не является схемой компоновки данных.");
            }
            return readEl(reader);
          }
        }
        throw new IllegalArgumentException("Файл не является схемой компоновки данных.");
      } finally {
        reader.close();
      }
    } catch (XMLStreamException e) {
      throw new IllegalArgumentException("не удалось разобрать XML: " + e.getMessage(), e);
    }
  }

  /** Курсор на START_ELEMENT узла; после вызова - на его END_ELEMENT. */
  private static El readEl(XMLStreamReader reader) throws XMLStreamException {
    String name = reader.getLocalName();
    String type = null;
    boolean nil = false;
    Map<String, String> attrs = new LinkedHashMap<>();
    for (int i = 0; i < reader.getAttributeCount(); i++) {
      String local = reader.getAttributeLocalName(i);
      String attrValue = reader.getAttributeValue(i);
      if (XSI_NS.equals(reader.getAttributeNamespace(i))) {
        if ("type".equals(local)) {
          type = attrValue;
        } else if ("nil".equals(local)) {
          nil = "true".equals(attrValue) || "1".equals(attrValue);
        }
        continue;
      }
      String prefix = reader.getAttributePrefix(i);
      attrs.put(prefix == null || prefix.isEmpty() ? local : prefix + ":" + local, attrValue);
    }
    List<El> children = new ArrayList<>();
    StringBuilder text = new StringBuilder();
    while (reader.hasNext()) {
      int event = reader.next();
      if (event == XMLStreamConstants.START_ELEMENT) {
        children.add(readEl(reader));
      } else if (event == XMLStreamConstants.CHARACTERS || event == XMLStreamConstants.CDATA) {
        text.append(reader.getText());
      } else if (event == XMLStreamConstants.END_ELEMENT) {
        // текст рядом с детьми: только отступы выгрузки, смешанного содержимого в формате нет
        return new El(name, type, nil, attrs, children.isEmpty() ? text.toString() : "",
          List.copyOf(children));
      }
    }
    throw new IllegalStateException("элемент " + name + " не закрыт до конца текста");
  }

  // ------------------------------------------------------------------ помощники

  private static String localType(String type) {
    return type == null ? "" : type.substring(type.indexOf(':') + 1);
  }

  /**
   * Строка локализованного значения: элемент {@code v8:item} языка конфигурации, иначе первый;
   * без {@code v8:item} — собственный текст (форма {@code xsi:type="xs:string"}).
   */
  private static String localized(El el) {
    if (el == null) {
      return "";
    }
    List<El> items = el.all("item");
    if (items.isEmpty()) {
      return el.text();
    }
    String first = "";
    for (El item : items) {
      String content = item.childText("content");
      if (first.isEmpty()) {
        first = content;
      }
      if (ConfigurationLanguage.current().equals(item.childText("lang"))) {
        return content;
      }
    }
    return first;
  }

  private static void putText(Map<String, Object> row, String key, String value) {
    if (value != null && !value.isEmpty()) {
      row.put(key, value);
    }
  }

  private static void putLocalized(Map<String, Object> row, String key, El el) {
    putText(row, key, localized(el));
  }

  private static void putFlag(Map<String, Object> row, String key, String text) {
    if (!text.isEmpty()) {
      row.put(key, "true".equals(text) || "1".equals(text));
    }
  }

  private static void putDecimal(Map<String, Object> row, String text) {
    try {
      row.put("decoded", new BigDecimal(text.trim()));
    } catch (NumberFormatException e) {
      // лексика не числовая: text уже отдан дословно
    }
  }
}
