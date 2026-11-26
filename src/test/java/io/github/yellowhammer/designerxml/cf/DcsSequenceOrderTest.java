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

import static org.assertj.core.api.Assertions.assertThat;

import io.github.yellowhammer.designerxml.SchemaVersion;
import jakarta.xml.bind.annotation.XmlAnyAttribute;
import jakarta.xml.bind.annotation.XmlAnyElement;
import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlElementRef;
import jakarta.xml.bind.annotation.XmlElementRefs;
import jakarta.xml.bind.annotation.XmlElements;
import jakarta.xml.bind.annotation.XmlMixed;
import jakarta.xml.bind.annotation.XmlTransient;
import jakarta.xml.bind.annotation.XmlType;
import jakarta.xml.bind.annotation.XmlValue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.EnumMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Списки порядка подэлементов в {@link DcsEdit}, {@link DcsSettingsEdit} и {@link DcsRead} (вставка
 * вычисляемого поля) против модели JAXB схемы компоновки.
 *
 * <p>Правки вставляют подэлемент после последнего предшественника из такого списка, поэтому списки
 * повторяют xs:sequence типов формата. Модель JAXB собрана из тех же XSD: xs:sequence типа в ней -
 * {@code @XmlType(propOrder)} его класса, у типа-расширения базовая последовательность идёт впереди.
 * Тест сверяет каждый список с этим порядком во всех версиях формата, где модель собрана.
 */
class DcsSequenceOrderTest {

  /** Корень пакетов модели: дальше {@code v2_NN.<пакет пространства>.<класс>}. */
  private static final String JAXB_ROOT = "io.github.yellowhammer.designerxml.jaxb.";

  private static final String SCHEMA = "v8_1_dcs_schema.";

  private static final String COMMON = "v8_1_dcs_common.";

  private static final String SETTINGS = "v8_1_dcs_settings.";

  /** Имя в аннотации JAXB, когда оно не задано явно. */
  private static final String DEFAULT_NAME = "##default";

  /** Имена списков порядка в {@link #OWNERS}. */
  private static final Pattern ORDER_LIST = Pattern.compile(".+_(SEQUENCE|PREDECESSORS)");

  /** Классы, чьи правки вставляют подэлементы по спискам порядка. */
  private static final List<Class<?>> OWNERS = List.of(DcsEdit.class, DcsSettingsEdit.class, DcsRead.class);

  /** Последняя версия формата: у её типов есть все элементы списков. */
  private static final SchemaVersion LATEST = SchemaVersion.values()[SchemaVersion.values().length - 1];

  /** Как список соотносится с xs:sequence типа. */
  private enum Coverage {
    /** Весь xs:sequence: те же элементы в том же порядке. */
    SEQUENCE,
    /** Начало xs:sequence до последнего элемента списка включительно: предшественники вставки. */
    PREFIX,
    /** Конец xs:sequence: элементы, которые запись ставит последними. */
    TAIL,
    /** Список шире типа: элементы типа идут в нём в том же порядке, остальных имён у типа нет. */
    WIDER
  }

  /** Список порядка, его соотношение с типом и класс типа от пакета версии. */
  private record Case(String list, List<String> order, Coverage coverage, String type) {

    @Override
    public String toString() {
      return list + " ↔ " + type;
    }
  }

  /** Списки порядка и типы формата, которые они повторяют. */
  static Stream<Case> cases() {
    return Stream.of(
      cases("DcsEdit.QUERY_SEQUENCE", DcsEdit.QUERY_SEQUENCE, Coverage.SEQUENCE, SCHEMA + "DataSetQuery"),
      cases("DcsEdit.OBJECT_SEQUENCE", DcsEdit.OBJECT_SEQUENCE, Coverage.SEQUENCE, SCHEMA + "DataSetObject"),
      cases("DcsEdit.UNION_SEQUENCE", DcsEdit.UNION_SEQUENCE, Coverage.SEQUENCE, SCHEMA + "DataSetUnion"),
      // Поле встаёт в набор любого вида: name и field - начало базового DataSet
      cases("DcsEdit.FIELD_PREDECESSORS", DcsEdit.FIELD_PREDECESSORS, Coverage.PREFIX,
        SCHEMA + "DataSetQuery", SCHEMA + "DataSetObject", SCHEMA + "DataSetUnion"),
      cases("DcsEdit.FIELD_SEQUENCE", DcsEdit.FIELD_SEQUENCE, Coverage.SEQUENCE, SCHEMA + "DataSetFieldField"),
      cases("DcsEdit.USE_RESTRICTION_FLAGS", DcsEdit.USE_RESTRICTION_FLAGS, Coverage.SEQUENCE,
        SCHEMA + "FieldUseRestriction"),
      cases("DcsEdit.ROLE_SEQUENCE", DcsEdit.ROLE_SEQUENCE, Coverage.SEQUENCE, COMMON + "DataSetFieldRole"),
      cases("DcsEdit.DATA_SET_PREDECESSORS", DcsEdit.DATA_SET_PREDECESSORS, Coverage.PREFIX,
        SCHEMA + "DataCompositionSchema"),
      cases("DcsEdit.LINK_PREDECESSORS", DcsEdit.LINK_PREDECESSORS, Coverage.PREFIX,
        SCHEMA + "DataCompositionSchema"),
      cases("DcsEdit.LINK_SEQUENCE", DcsEdit.LINK_SEQUENCE, Coverage.SEQUENCE, SCHEMA + "DataSetLink"),
      cases("DcsEdit.LINK_REQUIRED", DcsEdit.LINK_REQUIRED, Coverage.PREFIX, SCHEMA + "DataSetLink"),
      cases("DcsEdit.TOTAL_FIELD_PREDECESSORS", DcsEdit.TOTAL_FIELD_PREDECESSORS, Coverage.PREFIX,
        SCHEMA + "DataCompositionSchema"),
      cases("DcsEdit.TOTAL_FIELD_SEQUENCE", DcsEdit.TOTAL_FIELD_SEQUENCE, Coverage.SEQUENCE,
        SCHEMA + "TotalField"),
      cases("DcsEdit.CALCULATED_FIELD_SEQUENCE", DcsEdit.CALCULATED_FIELD_SEQUENCE, Coverage.SEQUENCE,
        SCHEMA + "CalculatedField"),
      cases("DcsEdit.PARAMETER_PREDECESSORS", DcsEdit.PARAMETER_PREDECESSORS, Coverage.PREFIX,
        SCHEMA + "DataCompositionSchema"),
      cases("DcsEdit.PARAMETER_SEQUENCE", DcsEdit.PARAMETER_SEQUENCE, Coverage.SEQUENCE, SCHEMA + "Parameter"),
      cases("DcsRead.CALCULATED_FIELD_PREDECESSORS", DcsRead.CALCULATED_FIELD_PREDECESSORS, Coverage.PREFIX,
        SCHEMA + "DataCompositionSchema"),
      cases("DcsSettingsEdit.SETTINGS_SEQUENCE", DcsSettingsEdit.SETTINGS_SEQUENCE, Coverage.SEQUENCE,
        SETTINGS + "Settings"),
      // Группировка и группы строк, колонок, точек и серий - разные типы с одной последовательностью
      cases("DcsSettingsEdit.GROUP_SEQUENCE", DcsSettingsEdit.GROUP_SEQUENCE, Coverage.SEQUENCE,
        SETTINGS + "StructureItemGroup", SETTINGS + "TableGroup", SETTINGS + "ChartGroup"),
      cases("DcsSettingsEdit.TABLE_SEQUENCE", DcsSettingsEdit.TABLE_SEQUENCE, Coverage.SEQUENCE,
        SETTINGS + "StructureItemTable"),
      cases("DcsSettingsEdit.CHART_SEQUENCE", DcsSettingsEdit.CHART_SEQUENCE, Coverage.SEQUENCE,
        SETTINGS + "StructureItemChart"),
      cases("DcsSettingsEdit.SECTION_SEQUENCE", DcsSettingsEdit.SECTION_SEQUENCE, Coverage.SEQUENCE,
        SETTINGS + "ConditionalAppearance"),
      // Секции параметров - только item* базового ParameterValues: хвоста секции у них нет, и вставке
      // item его имена не мешают
      cases("DcsSettingsEdit.SECTION_SEQUENCE", DcsSettingsEdit.SECTION_SEQUENCE, Coverage.WIDER,
        SETTINGS + "DataParameterValues", SETTINGS + "CompositionOutputParameterValues",
        SETTINGS + "GroupOutputParameterValues", SETTINGS + "TableOutputParameterValues",
        SETTINGS + "TableGroupOutputParameterValues", SETTINGS + "ChartOutputParameterValues",
        SETTINGS + "ChartGroupOutputParameterValues"),
      // Области использования запись ставит последними, после представления и хвоста элемента оформления
      cases("DcsSettingsEdit.USE_IN_AREAS", DcsSettingsEdit.USE_IN_AREAS.stream().map(Map.Entry::getValue).toList(),
        Coverage.TAIL, SETTINGS + "ConditionalAppearanceItem"))
      .flatMap(Function.identity());
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("cases")
  void orderListFollowsPropOrderOfModel(Case c) {
    assertThat(c.order()).as("%s: повтор в списке", c.list()).doesNotHaveDuplicates();
    Map<SchemaVersion, List<String>> models = elementOrders(c.type());
    // Старая версия формата может не знать типа, последняя обязана: иначе сверка прошла бы впустую
    assertThat(models).as("%s: класса нет в модели %s", c, LATEST).containsKey(LATEST);
    for (Map.Entry<SchemaVersion, List<String>> model : models.entrySet()) {
      List<String> elements = model.getValue();
      String context = c + " в " + model.getKey() + ", элементы типа " + elements;
      // Элемент, которого старая версия ещё не знает (isMain роли до 2.21), в её сверке пропускается
      List<String> present = c.order().stream().filter(elements::contains).toList();
      List<String> expected = switch (c.coverage()) {
        case SEQUENCE, WIDER -> elements;
        case PREFIX -> present.isEmpty() ? List.of() : elements.subList(0, elements.indexOf(present.getLast()) + 1);
        case TAIL -> elements.subList(elements.size() - present.size(), elements.size());
      };
      assertThat(present).as(context).containsExactlyElementsOf(expected);
      if (model.getKey() == LATEST && c.coverage() != Coverage.WIDER) {
        assertThat(c.order()).as("%s: в списке имена вне типа", context).containsExactlyElementsOf(present);
      }
    }
  }

  /**
   * Список порядка без строки в {@link #cases()} падает здесь. Списки узнаются по суффиксу имени
   * {@code _SEQUENCE} или {@code _PREDECESSORS}, остальные перечислены в {@link #cases()} явно. Список
   * прямо в вызове ({@code List.of(...)}) тест не видит, поэтому списки порядка заводятся полями.
   */
  @Test
  void everyOrderListIsChecked() throws IllegalAccessException {
    // По ссылке, а не по составу: два списка с одинаковыми именами - всё равно два списка
    Set<Object> checked = Collections.newSetFromMap(new IdentityHashMap<>());
    cases().map(Case::order).forEach(checked::add);
    List<String> found = new ArrayList<>();
    List<String> unchecked = new ArrayList<>();
    for (Class<?> owner : OWNERS) {
      for (Field field : owner.getDeclaredFields()) {
        if (!Modifier.isStatic(field.getModifiers()) || !ORDER_LIST.matcher(field.getName()).matches()) {
          continue;
        }
        String name = owner.getSimpleName() + "." + field.getName();
        found.add(name);
        // Закрытый список тест не прочтёт: его нужно открыть пакету и добавить в cases()
        if (Modifier.isPrivate(field.getModifiers()) || !checked.contains(field.get(null))) {
          unchecked.add(name);
        }
      }
    }
    assertThat(found).as("списки порядка в %s", OWNERS).isNotEmpty();
    assertThat(unchecked).as("списки порядка без сверки с propOrder").isEmpty();
  }

  private static Stream<Case> cases(String list, List<String> order, Coverage coverage, String... types) {
    return Stream.of(types).map(type -> new Case(list, order, coverage, type));
  }

  /** Порядок элементов типа по версиям формата, где пакет его модели собран, в порядке версий. */
  private static Map<SchemaVersion, List<String>> elementOrders(String type) {
    String namespacePackage = type.substring(0, type.lastIndexOf('.'));
    Map<SchemaVersion, List<String>> out = new EnumMap<>(SchemaVersion.class);
    for (SchemaVersion version : SchemaVersion.values()) {
      String base = JAXB_ROOT + version.name().toLowerCase(Locale.ROOT) + ".";
      // В каталоге старого формата нет части схем СКД, и пакета модели нет целиком
      if (load(base + namespacePackage + ".ObjectFactory").isEmpty()) {
        continue;
      }
      Class<?> model = load(base + type)
        .orElseThrow(() -> new AssertionError("В модели " + version + " нет класса " + type));
      out.put(version, elementOrder(model));
    }
    return out;
  }

  private static Optional<Class<?>> load(String className) {
    try {
      return Optional.of(Class.forName(className, false, DcsSequenceOrderTest.class.getClassLoader()));
    } catch (ClassNotFoundException e) {
      return Optional.empty();
    }
  }

  /** Имена XML-элементов типа по порядку: базовые классы впереди, как в xs:extension. */
  private static List<String> elementOrder(Class<?> type) {
    Deque<Class<?>> chain = new ArrayDeque<>();
    for (Class<?> c = type; c != null && c.isAnnotationPresent(XmlType.class); c = c.getSuperclass()) {
      chain.push(c);
    }
    List<String> out = new ArrayList<>();
    for (Class<?> c : chain) {
      out.addAll(declaredElements(c));
    }
    return out;
  }

  private static List<String> declaredElements(Class<?> c) {
    List<String> props = Stream.of(c.getAnnotation(XmlType.class).propOrder()).filter(p -> !p.isEmpty()).toList();
    if (props.isEmpty()) {
      // Без propOrder порядок элементов не задан, и сверять список не с чем
      assertThat(elementFields(c)).as("%s: элементы без propOrder", c.getName()).isEmpty();
      return List.of();
    }
    List<String> out = new ArrayList<>();
    for (String prop : props) {
      out.add(elementName(c, prop));
    }
    return out;
  }

  private static String elementName(Class<?> c, String prop) {
    Field field;
    try {
      field = c.getDeclaredField(prop);
    } catch (NoSuchFieldException e) {
      throw new AssertionError(c.getName() + ": нет поля " + prop + " из propOrder", e);
    }
    if (field.isAnnotationPresent(XmlElements.class) || field.isAnnotationPresent(XmlElementRefs.class)
      || field.isAnnotationPresent(XmlAnyElement.class) || field.isAnnotationPresent(XmlMixed.class)) {
      // Выбор из нескольких элементов не даёт одного имени на месте xs:sequence
      throw new AssertionError(c.getName() + "." + prop + ": выбор элементов, сверка такое поле не разбирает");
    }
    XmlElement element = field.getAnnotation(XmlElement.class);
    XmlElementRef ref = field.getAnnotation(XmlElementRef.class);
    String name = DEFAULT_NAME;
    if (element != null) {
      name = element.name();
    } else if (ref != null) {
      name = ref.name();
    }
    return DEFAULT_NAME.equals(name) ? prop : name;
  }

  /** Поля класса, которые JAXB пишет элементами. */
  private static List<String> elementFields(Class<?> c) {
    return Stream.of(c.getDeclaredFields())
      .filter(f -> !Modifier.isStatic(f.getModifiers()) && !f.isSynthetic())
      .filter(f -> !f.isAnnotationPresent(XmlAttribute.class) && !f.isAnnotationPresent(XmlAnyAttribute.class)
        && !f.isAnnotationPresent(XmlTransient.class) && !f.isAnnotationPresent(XmlValue.class))
      .map(Field::getName)
      .toList();
  }
}
