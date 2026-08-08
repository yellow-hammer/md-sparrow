/*
 * This file is a part of md-sparrow.
 *
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package io.github.yellowhammer.designerxml.cf;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Подписи значений перечислимых свойств так, как их называет конфигуратор.
 *
 * <p>Значение приходит из модели именем константы ({@code AS_DESCRIPTION}), и показывать его
 * человеку незачем. Словарь живёт здесь, а не у вызывающей стороны: набор значений задаёт формат
 * выгрузки, и разбираться в нём должна библиотека формата.
 *
 * <p>Ключ - имя константы: одно и то же имя у разных свойств почти всегда называется одинаково.
 * Свойства-исключения перечислены отдельно.
 */
public final class EnumValueLabels {

  private static final Map<String, String> LABELS = labels();

  /** Режимы совместимости: их три десятка, и с каждой несовместимой версией платформы прибавляется. */
  private static final Pattern VERSION = Pattern.compile("^VERSION_(\\d+)_(\\d+)(?:_(\\d+))?$");

  private EnumValueLabels() {
  }

  /** Свойства, у которых значение называется не так, как везде: {@code блок.свойство} -> подписи. */
  private static final Map<String, Map<String, String>> BY_PROPERTY = Map.of(
    "chartOfCalculationTypes.dependenceOnCalculationTypes", Map.of("DONT_USE", "Не зависит")
  );

  /**
   * Словарь подписей.
   *
   * @return имя константы -> подпись
   */
  public static Map<String, String> all() {
    return LABELS;
  }

  /**
   * Подписи значений у свойств, где они называются иначе.
   *
   * @return {@code блок.свойство} -> имя константы -> подпись
   */
  public static Map<String, Map<String, String>> byProperty() {
    return BY_PROPERTY;
  }

  /**
   * Подпись значения.
   *
   * @param constantName имя константы перечисления
   * @return подпись либо само имя, если значения в словаре нет
   */
  public static String labelOf(String constantName) {
    return labelOf(null, constantName);
  }

  /**
   * Подпись значения у свойства.
   *
   * @param property {@code блок.свойство}; пусто - подпись без привязки к свойству
   * @param constantName имя константы перечисления
   * @return подпись либо само имя, если значения в словаре нет
   */
  public static String labelOf(String property, String constantName) {
    if (constantName == null || constantName.isEmpty()) {
      return "";
    }
    String special = property == null ? null : BY_PROPERTY.getOrDefault(property, Map.of()).get(constantName);
    if (special != null) {
      return special;
    }
    String known = LABELS.get(constantName);
    if (known != null) {
      return known;
    }
    Matcher version = VERSION.matcher(constantName);
    if (version.matches()) {
      String patch = version.group(3) == null ? "" : "." + version.group(3);
      return "Версия " + version.group(1) + "." + version.group(2) + patch;
    }
    return constantName;
  }

  private static Map<String, String> labels() {
    Map<String, String> m = new LinkedHashMap<>();
    put(m, "USE", "Использовать");
    put(m, "DONT_USE", "Не использовать");
    put(m, "USE_WITH_WARNINGS", "Использовать с предупреждениями");
    put(m, "AUTO", "Авто");
    put(m, "AUTOMATIC", "Автоматический");
    put(m, "MANAGED", "Управляемый");
    put(m, "AUTOMATIC_AND_MANAGED", "Автоматический и управляемый");
    put(m, "ALLOW", "Разрешить");
    put(m, "DENY", "Запретить");
    put(m, "DONT_CHECK", "Не проверять");
    put(m, "SHOW_ERROR", "Выдавать ошибку");
    put(m, "MAKE_DISABLE", "Выключать");
    put(m, "DONT_CHANGE_BEHAVIOR", "Не менять поведение");

    put(m, "AS_CODE", "В виде кода");
    put(m, "AS_DESCRIPTION", "В виде наименования");
    put(m, "AS_NUMBER", "В виде номера");
    put(m, "STRING", "Строка");
    put(m, "NUMBER", "Число");
    put(m, "TEXT", "Текст");
    put(m, "PICTURE", "Картинка");
    put(m, "PICTURE_AND_TEXT", "Картинка и текст");

    put(m, "VARIABLE", "Переменная");
    put(m, "FIXED", "Фиксированная");
    put(m, "SINGLE", "Одиночный");
    put(m, "MULTIPLE", "Множественный");
    put(m, "SEPARATE", "Разделять");
    put(m, "INDEPENDENT", "Независимый");
    put(m, "INDEPENDENTLY", "Независимо");
    put(m, "INDEPENDENTLY_AND_SIMULTANEOUSLY", "Независимо и совместно");
    put(m, "RECORDER_SUBORDINATE", "Подчинение регистратору");
    put(m, "RECORDER_POSITION", "По позиции регистратора");

    put(m, "FOLDERS", "Группы");
    put(m, "ITEMS", "Элементы");
    put(m, "FOLDERS_AND_ITEMS", "Группы и элементы");
    put(m, "TO_FOLDERS", "Группам");
    put(m, "TO_ITEMS", "Элементам");
    put(m, "TO_FOLDERS_AND_ITEMS", "Группам и элементам");
    put(m, "HIERARCHY_FOLDERS_AND_ITEMS", "Иерархия групп и элементов");
    put(m, "HIERARCHY_OF_ITEMS", "Иерархия элементов");
    put(m, "WITHIN_SUBORDINATION", "В пределах подчинения");
    put(m, "WITHIN_OWNER_SUBORDINATION", "В пределах подчинения владельцу");
    put(m, "WHOLE_CATALOG", "Во всем справочнике");
    put(m, "WHOLE_CHARACTERISTIC_KIND", "Во всём плане видов характеристик");
    put(m, "WHOLE_CHART_OF_ACCOUNTS", "Во всём плане счетов");

    put(m, "IN_DIALOG", "В диалоге");
    put(m, "IN_LIST", "В списке");
    put(m, "BOTH_WAYS", "Обоими способами");
    put(m, "FROM_FORM", "Из формы");
    put(m, "QUICK_CHOICE", "Быстрый выбор");
    put(m, "DIRECTLY", "Непосредственно");
    put(m, "BEGIN", "Начало");
    put(m, "ANY_PART", "Любая часть");

    put(m, "INDEX", "Индексировать");
    put(m, "DONT_INDEX", "Не индексировать");
    put(m, "INDEX_WITH_ADDITIONAL_ORDER", "Индексировать с дополнительным упорядочиванием");

    put(m, "AUTO_UPDATE", "Обновлять автоматически");
    put(m, "DONT_AUTO_UPDATE", "Не обновлять автоматически");
    put(m, "AUTO_FILL", "Заполнять автоматически");
    put(m, "AUTO_FILL_OFF", "Не заполнять автоматически");
    put(m, "AUTO_DELETE", "Удалять автоматически");
    put(m, "AUTO_DELETE_ON_UNPOST", "Удалять автоматически при отмене проведения");
    put(m, "AUTO_DELETE_OFF", "Не удалять автоматически");
    put(m, "WRITE_MODIFIED", "Записывать модифицированные");
    put(m, "WRITE_SELECTED", "Записывать выбранные");

    put(m, "BALANCE", "Остатки");
    put(m, "TURNOVERS", "Обороты");
    put(m, "NONPERIODICAL", "Непериодический");
    put(m, "SECOND", "В пределах секунды");
    put(m, "DAY", "В пределах дня");
    put(m, "MONTH", "В пределах месяца");
    put(m, "QUARTER", "В пределах квартала");
    put(m, "YEAR", "В пределах года");
    put(m, "ON_ACTION_PERIOD", "По периоду действия");
    put(m, "ON_REGISTRATION_PERIOD", "По периоду регистрации");

    put(m, "DURING_SESSION", "На время сеанса");
    put(m, "DURING_REQUEST", "На время вызова");
    put(m, "BACKGROUND", "Фоновым заданием");
    put(m, "NATIVE", "Собственный");
    put(m, "BUSINESS_PROCESS_NUMBER", "Номер бизнес-процесса");
    put(m, "ADOPTED", "Заимствованный");

    put(m, "MANAGED_APPLICATION", "Управляемое приложение");
    put(m, "ORDINARY_APPLICATION", "Обычное приложение");
    put(m, "RUSSIAN", "Русский");
    put(m, "ENGLISH", "Английский");
    put(m, "AUTO_FREE", "Освобождать автоматически");
    put(m, "NOT_AUTO_FREE", "Не освобождать автоматически");
    put(m, "TAXI", "Такси");
    put(m, "TAXI_ENABLE_VERSION_8_2", "Такси с возможностью версии 8.2");
    put(m, "VERSION_8_2_ENABLE_TAXI", "Версия 8.2 с возможностью Такси");
    put(m, "VERSION_8_2", "Версия 8.2");
    put(m, "NORMAL", "Обычный");
    put(m, "WORKPLACE", "Рабочее место");
    put(m, "FULLSCREEN_WORKPLACE", "Полноэкранное рабочее место");
    put(m, "EMBEDDED_WORKPLACE", "Встроенное рабочее место");
    put(m, "KIOSK", "Киоск");
    put(m, "CUSTOMIZATION", "Адаптация");
    put(m, "ADD_ON", "Дополнение");
    put(m, "PATCH", "Исправление");
    put(m, "LIGHT", "Светлая");
    put(m, "DARK", "Тёмная");
    put(m, "OPEN_DATA_IN_TABS", "Открывать данные в закладках");
    put(m, "OPEN_DATA_IN_DIALOGS", "Открывать данные в отдельных окнах");
    put(m, "NAVIGATION_LEFT", "Навигация слева");
    put(m, "NAVIGATION_TOP", "Навигация сверху");
    return Map.copyOf(m);
  }

  private static void put(Map<String, String> m, String constantName, String label) {
    m.put(constantName, label);
  }
}
