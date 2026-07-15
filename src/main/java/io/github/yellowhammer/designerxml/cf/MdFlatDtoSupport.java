/*
 * This file is a part of md-sparrow.
 *
 * Copyright (c) 2026
 * Ivan Karlo <i.karlo@outlook.com> and contributors
 *
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package io.github.yellowhammer.designerxml.cf;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Копирование, восполнение из базовой версии и сравнение DTO свойств: публичные поля
 * {@code String}, {@code boolean}, {@code List<String>} и вложенные DTO этого же пакета.
 * Поля с именем на {@code Xml} — маршалированные поддеревья, сравниваются как XML.
 */
final class MdFlatDtoSupport {

  private MdFlatDtoSupport() {
  }

  /** Полная копия (списки — новые экземпляры). */
  static <T> T copy(T source) {
    if (source == null) {
      return null;
    }
    T target = newInstance(source);
    for (Field field : fields(source.getClass())) {
      write(field, target, copyValue(field, read(field, source)));
    }
    return target;
  }

  /**
   * Поля, не переданные в JSON ({@code null}), берём из прочитанного XML: пустое значение и
   * «не передавали» должны различаться.
   */
  static <T> void coalesce(T incoming, T baseline) {
    if (incoming == null || baseline == null) {
      return;
    }
    for (Field field : fields(incoming.getClass())) {
      if (field.getType() == boolean.class) {
        continue;
      }
      if (read(field, incoming) == null) {
        write(field, incoming, copyValue(field, read(field, baseline)));
      }
    }
  }

  /**
   * @param lenientXmlBlobs сравнивать поля {@code *Xml} без учёта префиксов пространств имён
   *   (JAXB расставляет их по-разному от запуска к запуску)
   */
  static <T> boolean equalsFlat(T a, T b, boolean lenientXmlBlobs) {
    if (a == b) {
      return true;
    }
    if (a == null || b == null) {
      return false;
    }
    for (Field field : fields(a.getClass())) {
      if (!fieldEquals(field, a, b, lenientXmlBlobs)) {
        return false;
      }
    }
    return true;
  }

  private static boolean fieldEquals(Field field, Object a, Object b, boolean lenientXmlBlobs) {
    Object x = read(field, a);
    Object y = read(field, b);
    if (x instanceof List<?> || y instanceof List<?>) {
      return MdObjectPropertiesDiff.listStringEquals(stringList(x), stringList(y));
    }
    if (isNestedDto(field.getType())) {
      return equalsFlat(x, y, lenientXmlBlobs);
    }
    if (lenientXmlBlobs && field.getName().endsWith("Xml")) {
      return MdObjectPropertiesDiff.looseXmlBlobEquals((String) x, (String) y);
    }
    return Objects.equals(x, y);
  }

  /** Вложенное DTO этого пакета: сравниваем и копируем по значению, а не по ссылке. */
  private static boolean isNestedDto(Class<?> type) {
    return type.getName().startsWith("io.github.yellowhammer.designerxml.cf.") && type.getName().endsWith("Dto");
  }

  @SuppressWarnings("unchecked")
  private static List<String> stringList(Object value) {
    return value == null ? new ArrayList<>() : new ArrayList<>((List<String>) value);
  }

  private static Object read(Field field, Object target) {
    try {
      Object value = field.get(target);
      if (value instanceof List<?> list) {
        return new ArrayList<>(list);
      }
      return value;
    } catch (IllegalAccessException e) {
      throw new IllegalStateException("cannot read " + field.getName(), e);
    }
  }

  private static Object copyValue(Field field, Object value) {
    if (value != null && isNestedDto(field.getType())) {
      return copy(value);
    }
    return value;
  }

  private static void write(Field field, Object target, Object value) {
    try {
      if (value == null && field.getType() == boolean.class) {
        return;
      }
      if (value == null && List.class.isAssignableFrom(field.getType())) {
        field.set(target, new ArrayList<>());
        return;
      }
      field.set(target, value);
    } catch (IllegalAccessException e) {
      throw new IllegalStateException("cannot write " + field.getName(), e);
    }
  }

  @SuppressWarnings("unchecked")
  private static <T> T newInstance(T source) {
    try {
      return (T) source.getClass().getDeclaredConstructor().newInstance();
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException("cannot create " + source.getClass().getName(), e);
    }
  }

  private static List<Field> fields(Class<?> type) {
    List<Field> out = new ArrayList<>();
    for (Field field : type.getFields()) {
      if (!Modifier.isStatic(field.getModifiers()) && !field.isSynthetic()) {
        out.add(field);
      }
    }
    return out;
  }
}
