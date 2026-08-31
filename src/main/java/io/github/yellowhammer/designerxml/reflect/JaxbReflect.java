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
package io.github.yellowhammer.designerxml.reflect;

import jakarta.xml.bind.JAXBElement;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlType;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Версионно-нейтральная работа с JAXB-объектами через рефлексию.
 * <p>
 * Сгенерированные XJC-классы каждой версии схем ({@code jaxb.v2_10.*}…{@code jaxb.v2_21.*})
 * имеют идентичную структуру (одинаковые геттеры/сеттеры), различаясь лишь пакетом. Эти хелперы
 * позволяют писать один код вместо пар «для 2.20»/«для 2.21» под каждую версию.
 * <p>
 * При отсутствии ожидаемого метода бросается {@link IllegalStateException} — это сигнал о реальном
 * изменении структуры новой версии схемы (а не молчаливое игнорирование).
 */
public final class JaxbReflect {

  private JaxbReflect() {
  }

  /**
   * Снимает обёртку {@link JAXBElement}, возвращая значение; иначе сам объект.
   */
  public static Object value(Object root) {
    return root instanceof JAXBElement<?> je ? je.getValue() : root;
  }

  /**
   * Вызывает геттер без аргументов. {@code null}-цель → {@code null}.
   */
  public static Object get(Object target, String getter) {
    if (target == null) {
      return null;
    }
    try {
      Method m = target.getClass().getMethod(getter);
      return m.invoke(target);
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException(target.getClass().getName() + "#" + getter + "()", e);
    }
  }

  /**
   * Геттер «по возможности»: если метода нет в этой версии схемы — {@code null} (а не исключение).
   * Для версионно-вариативных листовых свойств (одни форматы их имеют, другие — нет).
   */
  public static Object getOptional(Object target, String getter) {
    if (target == null) {
      return null;
    }
    Method m;
    try {
      m = target.getClass().getMethod(getter);
    } catch (NoSuchMethodException e) {
      return null;
    }
    try {
      return m.invoke(target);
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException(target.getClass().getName() + "#" + getter + "()", e);
    }
  }

  /** Строковое значение геттера «по возможности» ({@code null}, если метода/значения нет). */
  public static String getStringOptional(Object target, String getter) {
    Object v = getOptional(target, getter);
    return v == null ? null : v.toString();
  }

  /** {@code boolean}-значение геттера «по возможности» ({@code false}, если метода/значения нет). */
  public static boolean getBooleanOptional(Object target, String getter) {
    Object v = getOptional(target, getter);
    return v instanceof Boolean b && b;
  }

  /**
   * Сеттер «по возможности»: если метода нет в этой версии схемы — ничего не делает.
   *
   * @return {@code true}, если значение было установлено
   */
  public static boolean setOptional(Object target, String setter, Object arg) {
    if (target == null) {
      return false;
    }
    Method m = method1OrNull(target.getClass(), setter);
    if (m == null) {
      return false;
    }
    try {
      m.invoke(target, arg);
      return true;
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException(target.getClass().getName() + "#" + setter, e);
    }
  }

  /**
   * Как {@link #ensure}, но «по возможности»: если геттера/сеттера нет в этой версии — {@code null}.
   */
  public static Object ensureOptional(Object target, String getter, String setter) {
    if (target == null) {
      return null;
    }
    Object v = getOptional(target, getter);
    if (v != null) {
      return v;
    }
    Method m = method1OrNull(target.getClass(), setter);
    if (m == null) {
      return null;
    }
    Object created = newInstance(m.getParameterTypes()[0].getName());
    setOptional(target, setter, created);
    return created;
  }

  /**
   * Последовательно применяет геттеры (с предварительным {@link #value}); при {@code null} в цепочке
   * возвращает {@code null}.
   */
  public static Object path(Object root, String... getters) {
    Object o = value(root);
    for (String g : getters) {
      o = get(o, g);
      if (o == null) {
        return null;
      }
    }
    return o;
  }

  /**
   * Список из геттера (например {@code getCatalog()} у {@code ChildObjects}); {@code null} → пустой список.
   */
  @SuppressWarnings("unchecked")
  public static <T> List<T> list(Object target, String getter) {
    Object v = get(target, getter);
    return v == null ? new ArrayList<>() : (List<T>) v;
  }

  /**
   * Список из геттера, которого у типа может не быть.
   *
   * Состав {@code ChildObjects} свой у каждого вида объекта: у справочника нет
   * графы, у журнала документов нет реквизита. Отсутствие геттера здесь - не
   * ошибка, а «у этого вида такого состава не бывает».
   *
   * @param target Узел JAXB
   * @param getter Имя геттера
   * @return Список или пустой список, если геттера нет
   */
  @SuppressWarnings("unchecked")
  public static <T> List<T> listOptional(Object target, String getter) {
    Object v = getOptional(target, getter);
    return v == null ? new ArrayList<>() : (List<T>) v;
  }

  /**
   * Вызывает метод с одним аргументом, подбирая его по имени (единственная перегрузка с 1 параметром).
   */
  public static Object call1(Object target, String method, Object arg) {
    if (target == null) {
      return null;
    }
    Method m = method1(target.getClass(), method);
    try {
      return m.invoke(target, arg);
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException(target.getClass().getName() + "#" + method, e);
    }
  }

  /**
   * Сеттер строкового/простого значения: {@code set<Prop>(arg)}.
   */
  public static void set(Object target, String setter, Object arg) {
    call1(target, setter, arg);
  }

  /**
   * Строковое значение геттера ({@code null}-цель/результат → {@code null}).
   */
  public static String getString(Object target, String getter) {
    Object v = get(target, getter);
    return v == null ? null : v.toString();
  }

  /**
   * {@code boolean}-значение геттера ({@code is<Prop>}); {@code null} → {@code false}.
   */
  public static boolean getBoolean(Object target, String getter) {
    Object v = get(target, getter);
    return v instanceof Boolean b && b;
  }

  /**
   * Имя enum-константы значения ({@code null} → {@code ""}).
   */
  public static String enumName(Object enumValueOrNull) {
    return enumValueOrNull == null ? "" : ((Enum<?>) enumValueOrNull).name();
  }

  /**
   * Имя enum-константы из геттера ({@code null} → {@code ""}).
   */
  public static String enumName(Object target, String getter) {
    return enumName(get(target, getter));
  }

  /**
   * Имя enum-константы из геттера «по возможности»: {@code ""}, если геттера нет в этой версии схемы.
   */
  public static String enumNameOptional(Object target, String getter) {
    return enumName(getOptional(target, getter));
  }

  /**
   * Устанавливает enum-значение по имени константы. Класс enum выводится из типа параметра сеттера.
   * Пустое имя — значение не меняется (keep); неизвестная константа — также keep (как в JSON-правке свойств);
   * отсутствие сеттера в этой версии схемы — пропуск (свойство версионно-вариативно).
   */
  public static void setEnumOrKeep(Object target, String setter, String constantName) {
    if (target == null) {
      return;
    }
    String v = constantName == null ? "" : constantName.trim();
    if (v.isEmpty()) {
      return;
    }
    Method m = method1OrNull(target.getClass(), setter);
    if (m == null) {
      return;
    }
    Class<?> pt = m.getParameterTypes()[0];
    if (!pt.isEnum()) {
      throw new IllegalStateException(target.getClass().getName() + "#" + setter + ": параметр не enum (" + pt + ")");
    }
    Object value;
    try {
      value = Enum.valueOf(pt.asSubclass(Enum.class), v);
    } catch (IllegalArgumentException e) {
      // Молча оставить прежнее значение нельзя: правка пользователя потерялась бы, а запись
      // потом упала бы сверкой с невнятным «причина не определена».
      throw new IllegalArgumentException(
        setter.replaceFirst("^set", "") + ": недопустимое значение " + v
          + "; допустимы " + java.util.Arrays.toString(pt.getEnumConstants()), e);
    }
    try {
      m.invoke(target, value);
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException(target.getClass().getName() + "#" + setter, e);
    }
  }

  /**
   * Возвращает значение геттера; если {@code null} — создаёт экземпляр типа параметра сеттера,
   * присваивает его и возвращает. Тип берётся из сигнатуры {@code set<Prop>} (не требует знания пакета версии).
   */
  public static Object ensure(Object target, String getter, String setter) {
    Object v = get(target, getter);
    if (v != null) {
      return v;
    }
    Method m = method1(target.getClass(), setter);
    Object created = newInstance(m.getParameterTypes()[0].getName());
    set(target, setter, created);
    return created;
  }

  /**
   * Новый экземпляр класса по полному имени (конструктор без аргументов).
   */
  public static Object newInstance(String fullyQualifiedClassName) {
    try {
      return Class.forName(fullyQualifiedClassName).getDeclaredConstructor().newInstance();
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException("newInstance " + fullyQualifiedClassName, e);
    }
  }

  /**
   * Списочные свойства объекта в порядке XSD-{@code sequence} (из {@code @XmlType(propOrder)}),
   * с XML-именем тега (из {@code @XmlElement(name)}). Версионно-нейтрально учитывает добавленные
   * в новых форматах элементы (например {@code PaletteColor} в 2.21).
   *
   * @return пары «XML-тег → список строк», только для свойств-{@link List}
   */
  public static List<Map.Entry<String, List<String>>> orderedStringLists(Object obj) {
    List<Map.Entry<String, List<String>>> out = new ArrayList<>();
    if (obj == null) {
      return out;
    }
    Class<?> cls = obj.getClass();
    XmlType xt = cls.getAnnotation(XmlType.class);
    String[] order = xt != null ? xt.propOrder() : new String[0];
    for (String prop : order) {
      if (prop == null || prop.isEmpty()) {
        continue;
      }
      String tag = xmlTagFor(cls, prop);
      // Геттер выводим из XML-имени тега, а не из имени поля: для зарезервированных слов XJC переименовывает
      // поле (например элемент Enum → поле _enum, но геттер getEnum()).
      String getter = "get" + Character.toUpperCase(tag.charAt(0)) + tag.substring(1);
      Object v;
      try {
        v = cls.getMethod(getter).invoke(obj);
      } catch (ReflectiveOperationException e) {
        continue;
      }
      if (!(v instanceof List<?> listValue)) {
        continue;
      }
      @SuppressWarnings("unchecked")
      List<String> sl = (List<String>) listValue;
      out.add(new AbstractMap.SimpleImmutableEntry<>(tag, sl));
    }
    return out;
  }

  private static String xmlTagFor(Class<?> cls, String prop) {
    try {
      Field f = cls.getDeclaredField(prop);
      XmlElement xe = f.getAnnotation(XmlElement.class);
      if (xe != null && !"##default".equals(xe.name())) {
        return xe.name();
      }
    } catch (NoSuchFieldException ignored) {
      // нет поля — используем имя свойства с заглавной буквы
    }
    return Character.toUpperCase(prop.charAt(0)) + prop.substring(1);
  }

  private static Method method1(Class<?> type, String name) {
    Method m = method1OrNull(type, name);
    if (m == null) {
      throw new IllegalStateException(type.getName() + "#" + name + "(1 arg) не найден");
    }
    return m;
  }

  private static Method method1OrNull(Class<?> type, String name) {
    for (Method m : type.getMethods()) {
      if (m.getName().equals(name) && m.getParameterCount() == 1) {
        return m;
      }
    }
    return null;
  }
}
