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

import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Занято ли имя нового узла в составе объекта.
 *
 * <p>Имена платформа сравнивает без учёта регистра: второй реквизит «ЦЕНА» рядом с «Цена» она при
 * загрузке отвергает («Дублирование имени объекта метаданных»), и выгрузка целиком не загружается.
 * Поэтому занятость проверяется без учёта регистра, а сам узел правки ищется по имени, как его
 * написали.
 *
 * <p>Поля данных объекта - реквизиты, табличные части, измерения, ресурсы, признаки учёта и
 * реквизиты адресации - делят одно пространство имён: реквизит и табличную часть «Товары» у одного
 * справочника платформа отвергает («Задано одинаковое имя для реквизита и табличной части»), так же
 * измерение и ресурс регистра, реквизит и признак учёта плана счетов, реквизит и реквизит адресации
 * задачи. Команды, формы, макеты, перерасчёты, значения перечисления и признаки учёта субконто
 * называются независимо от полей и друг от друга: реквизит и команду, признак учёта и признак учёта
 * субконто с одним именем платформа загружает, такие пары есть и в ssl31.
 *
 * <p>У внешних отчёта и обработки поля одно имя делить могут: реквизит и табличную часть «Имя»
 * платформа собирает в файл без ошибок ({@code ibcmd infobase config import --out}) и выгружает обратно,
 * отвергает она только одноимённые узлы одного вида.
 *
 * <p>Ещё платформа не даёт полю данных объекта конфигурации имя его стандартного реквизита,
 * стандартной табличной части или свойства объекта («Недопустимое имя реквизита - Код»), а реквизиту
 * табличной части - имя стандартного реквизита табличной части. Эти имена - в {@link #RESERVED} и
 * {@link #TABULAR_RESERVED}.
 */
final class ChildNodeNames {

  private static final String CHILD_OBJECTS = "ChildObjects";
  private static final String PROPERTIES = "Properties";
  private static final String NAME = "Name";
  private static final String TABULAR_SECTION = "TabularSection";
  private static final String ATTRIBUTE = "Attribute";

  /** Приставка вида внешнего отчёта и внешней обработки. */
  private static final String EXTERNAL = "External";

  /** Поля данных объекта и то, как их назвать в отказе. */
  private static final Map<String, String> FIELDS = Map.of(
    ATTRIBUTE, "реквизит",
    TABULAR_SECTION, "табличная часть",
    "Dimension", "измерение",
    "Resource", "ресурс",
    "AccountingFlag", "признак учёта",
    "AddressingAttribute", "реквизит адресации");

  /**
   * Имена, которые платформа не даёт полям данных объекта конфигурации, по виду владельца: на обоих
   * языках встроенного языка.
   *
   * <p>Снято загрузкой {@code ibcmd infobase config import} на 8.3.23, 8.3.24, 8.3.27 и 8.5.1 (одно и то
   * же на всех): объекту каждого вида добавлены поля с именами всех стандартных реквизитов всех видов
   * ({@code standard-labels.json}) по-английски и по-русски, стандартных табличных частей и нескольких
   * свойств объекта ({@code ThisObject}, {@code RegisterRecords}, {@code DataExchange},
   * {@code AdditionalProperties}). Здесь те, что платформа отвергла. От настроек объекта список не
   * зависит: «Код» отвергается и при нулевой длине кода, «Владелец» и «Родитель» - у справочника без
   * владельцев и иерархии; «Порядок» плана счетов, «Предопределенный» справочника и реквизиты регистра
   * расчёта «Регистратор» и «Активность» платформа принимает. Имён вне проверенного набора, которые она
   * тоже отвергает, здесь может не быть.
   */
  private static final Map<String, Set<String>> RESERVED = Map.ofEntries(
    reserved("Catalog", "Code Код Description Наименование Ref Ссылка DeletionMark ПометкаУдаления"
      + " Owner Владелец Parent Родитель IsFolder ЭтоГруппа ThisObject ЭтотОбъект"),
    reserved("Document", "Ref Ссылка DeletionMark ПометкаУдаления Date Дата Number Номер Posted Проведен"
      + " ThisObject ЭтотОбъект RegisterRecords Движения"),
    reserved("ChartOfAccounts", "Code Код Description Наименование Ref Ссылка DeletionMark ПометкаУдаления"
      + " Parent Родитель Predefined Предопределенный PredefinedDataName ИмяПредопределенныхДанных"
      + " OffBalance Забалансовый Type Вид ThisObject ЭтотОбъект ExtDimensionTypes ВидыСубконто"),
    reserved("ChartOfCharacteristicTypes", "Code Код Description Наименование Ref Ссылка"
      + " DeletionMark ПометкаУдаления Parent Родитель IsFolder ЭтоГруппа Predefined Предопределенный"
      + " PredefinedDataName ИмяПредопределенныхДанных ValueType ТипЗначения ThisObject ЭтотОбъект"),
    reserved("ChartOfCalculationTypes", "Code Код Description Наименование Ref Ссылка"
      + " DeletionMark ПометкаУдаления Predefined Предопределенный PredefinedDataName ИмяПредопределенныхДанных"
      + " ActionPeriodIsBasic ПериодДействияБазовый ThisObject ЭтотОбъект BaseCalculationTypes БазовыеВидыРасчета"
      + " LeadingCalculationTypes ВедущиеВидыРасчета DisplacingCalculationTypes ВытесняющиеВидыРасчета"),
    reserved("ExchangePlan", "Code Код Description Наименование Ref Ссылка DeletionMark ПометкаУдаления"
      + " ReceivedNo НомерПринятого SentNo НомерОтправленного ThisNode ЭтотУзел ThisObject ЭтотОбъект"
      + " AdditionalProperties ДополнительныеСвойства"),
    reserved("BusinessProcess", "Ref Ссылка DeletionMark ПометкаУдаления Date Дата Number Номер"
      + " Completed Завершен HeadTask ВедущаяЗадача Started Стартован ThisObject ЭтотОбъект"),
    reserved("Task", "Description Наименование Ref Ссылка DeletionMark ПометкаУдаления Date Дата Number Номер"
      + " BusinessProcess БизнесПроцесс RoutePoint ТочкаМаршрута Executed Выполнена ThisObject ЭтотОбъект"),
    reserved("DataProcessor", "ThisObject ЭтотОбъект"),
    reserved("Report", "ThisObject ЭтотОбъект"),
    reserved("InformationRegister", "Period Период Recorder Регистратор LineNumber НомерСтроки Active Активность"),
    reserved("AccumulationRegister", "Period Период Recorder Регистратор LineNumber НомерСтроки"
      + " Active Активность RecordType ВидДвижения"),
    reserved("AccountingRegister", "Period Период Recorder Регистратор LineNumber НомерСтроки"
      + " Active Активность RecordType ВидДвижения Account Счет AccountDr СчетДт AccountCr СчетКт"),
    reserved("CalculationRegister", "CalculationType ВидРасчета RegistrationPeriod ПериодРегистрации"
      + " ReversingEntry Сторно ActionPeriod ПериодДействия BegOfActionPeriod ПериодДействияНачало"
      + " EndOfActionPeriod ПериодДействияКонец BegOfBasePeriod БазовыйПериодНачало"
      + " EndOfBasePeriod БазовыйПериодКонец"));

  /**
   * Имена, которые платформа не даёт реквизиту табличной части: номер строки у любой табличной части,
   * в том числе у внешних отчёта и обработки, и ссылка - у табличной части ссылочного объекта (в
   * {@link #TABULAR_WITH_REF}). Снято так же, как {@link #RESERVED}; у внешних - сборкой файла
   * ({@code config import --out}).
   */
  private static final Set<String> TABULAR_RESERVED = upper("LineNumber НомерСтроки");

  private static final Set<String> TABULAR_REF = upper("Ref Ссылка");

  /** Владельцы, у табличной части которых есть стандартный реквизит {@code Ref}. */
  private static final Set<String> TABULAR_WITH_REF = Set.of("Catalog", "Document", "ChartOfAccounts",
    "ChartOfCharacteristicTypes", "ChartOfCalculationTypes", "ExchangePlan", "BusinessProcess", "Task");

  /** Узел состава: вид (элемент выгрузки), имя и, у табличной части, её узлы. */
  record Node(String kind, String name, List<Node> children) {
  }

  private ChildNodeNames() {
  }

  /**
   * Отказ, если имя занято в корневом {@code ChildObjects}: узлом того же вида или, у поля данных
   * объекта конфигурации, другим полем.
   *
   * @param xml текст файла объекта
   * @param ownerLocal вид владельца: {@code Catalog}, {@code InformationRegister}
   * @param kind вид нового узла: {@code Attribute}, {@code Command}
   * @param name имя нового узла
   * @param keep имя узла этого вида, которому новое имя остаётся (переименование в другой регистр);
   *   {@code null} у добавления и копии
   * @param label вид узла в сообщении: «Реквизит»
   * @throws IllegalArgumentException если имя занято
   */
  static void ensureFree(String xml, String ownerLocal, String kind, String name, String keep, String label) {
    boolean field = FIELDS.containsKey(kind) && !ownerLocal.startsWith(EXTERNAL);
    for (Node node : rootNodes(xml, ownerLocal)) {
      if (!node.name().equalsIgnoreCase(name)) {
        continue;
      }
      if (node.kind().equals(kind)) {
        if (!node.name().equals(keep)) {
          throw new IllegalArgumentException(label + " уже существует: " + node.name());
        }
      } else if (field && FIELDS.containsKey(node.kind())) {
        throw new IllegalArgumentException(
          "Имя " + name + " уже занято: " + FIELDS.get(node.kind()) + " " + node.name());
      }
    }
    if (field && RESERVED.getOrDefault(ownerLocal, Set.of()).contains(upperName(name))) {
      throw new IllegalArgumentException(
        "Имя " + name + " занято платформой: так называется стандартный реквизит или свойство объекта");
    }
  }

  /**
   * Отказ, если имя занято реквизитом той же табличной части.
   *
   * @param xml текст файла объекта
   * @param ownerLocal вид владельца табличной части
   * @param tabularSection имя табличной части
   * @param name имя нового реквизита
   * @param keep имя реквизита, которому новое имя остаётся; {@code null} у добавления и копии
   * @throws IllegalArgumentException если имя занято
   */
  static void ensureFreeInTabularSection(
    String xml,
    String ownerLocal,
    String tabularSection,
    String name,
    String keep
  ) {
    for (Node section : rootNodes(xml, ownerLocal)) {
      if (!section.kind().equals(TABULAR_SECTION) || !section.name().equals(tabularSection)) {
        continue;
      }
      for (Node node : section.children()) {
        if (node.kind().equals(ATTRIBUTE) && node.name().equalsIgnoreCase(name) && !node.name().equals(keep)) {
          throw new IllegalArgumentException("Реквизит ТЧ уже существует: " + node.name());
        }
      }
    }
    String key = upperName(name);
    if (TABULAR_RESERVED.contains(key) || TABULAR_WITH_REF.contains(ownerLocal) && TABULAR_REF.contains(key)) {
      throw new IllegalArgumentException(
        "Имя " + name + " занято платформой: так называется стандартный реквизит табличной части");
    }
  }

  /**
   * Узлы корневого {@code ChildObjects} владельца вместе с узлами их {@code ChildObjects}.
   *
   * <p>Имя узла - {@code Properties/Name}, а у узла-ссылки на файл ({@code <Form>Имя</Form>}) - его текст.
   */
  static List<Node> rootNodes(String xml, String ownerLocal) {
    try {
      XMLStreamReader reader = XmlLines.reader(xml);
      try {
        return scan(reader, List.of("MetaDataObject", ownerLocal, CHILD_OBJECTS));
      } finally {
        reader.close();
      }
    } catch (XMLStreamException e) {
      throw new IllegalArgumentException("не удалось разобрать XML: " + e.getMessage(), e);
    }
  }

  /**
   * Глубины считаются от {@code ChildObjects} владельца ({@code root}): узел - на глубине
   * {@code root + 1}, его {@code Properties/Name} - на {@code root + 3}, вложенный узел - на
   * {@code root + 3} под {@code ChildObjects}, его имя - на {@code root + 5}.
   */
  private static List<Node> scan(XMLStreamReader reader, List<String> root) throws XMLStreamException {
    List<Node> nodes = new ArrayList<>();
    List<String> stack = new ArrayList<>();
    int base = root.size();
    Building top = null;
    Building nested = null;
    StringBuilder text = new StringBuilder();
    while (reader.hasNext()) {
      int event = reader.next();
      if (event == XMLStreamConstants.START_ELEMENT) {
        String local = reader.getLocalName();
        int depth = stack.size();
        if (depth == base && stack.equals(root)) {
          top = new Building(local);
        } else if (top != null && depth == base + 2 && CHILD_OBJECTS.equals(stack.get(depth - 1))) {
          nested = new Building(local);
        }
        text.setLength(0);
        stack.add(local);
      } else if (event == XMLStreamConstants.CHARACTERS || event == XMLStreamConstants.CDATA) {
        text.append(reader.getText());
      } else if (event == XMLStreamConstants.END_ELEMENT) {
        int depth = stack.size();
        String local = stack.remove(depth - 1);
        boolean propertyName = NAME.equals(local) && depth >= 2 && PROPERTIES.equals(stack.get(depth - 2));
        if (propertyName && nested != null && depth == base + 5) {
          nested.name = text.toString().trim();
        } else if (propertyName && top != null && nested == null && depth == base + 3) {
          top.name = text.toString().trim();
        } else if (top != null && nested != null && depth == base + 3) {
          top.children.add(nested.build(text));
          nested = null;
        } else if (top != null && depth == base + 1) {
          nodes.add(top.build(text));
          top = null;
        } else if (depth == base && CHILD_OBJECTS.equals(local) && stack.equals(root.subList(0, base - 1))) {
          return nodes;
        }
        text.setLength(0);
      }
    }
    return nodes;
  }

  private static Map.Entry<String, Set<String>> reserved(String ownerLocal, String names) {
    return Map.entry(ownerLocal, upper(names));
  }

  /** Имена через пробел - в верхнем регистре: платформа сравнивает их без учёта регистра. */
  private static Set<String> upper(String names) {
    return Arrays.stream(names.split(" ")).map(ChildNodeNames::upperName).collect(Collectors.toUnmodifiableSet());
  }

  private static String upperName(String name) {
    return name.toUpperCase(Locale.ROOT);
  }

  /** Узел, пока его читают. */
  private static final class Building {
    private final String kind;
    private final List<Node> children = new ArrayList<>();
    private String name;

    private Building(String kind) {
      this.kind = kind;
    }

    /** У узла-ссылки на файл имени в свойствах нет: имя - его текст. */
    private Node build(StringBuilder text) {
      return new Node(kind, name != null ? name : text.toString().trim(), List.copyOf(children));
    }
  }
}
