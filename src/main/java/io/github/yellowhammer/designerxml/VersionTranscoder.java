/*
 * This file is a part of md-sparrow.
 *
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package io.github.yellowhammer.designerxml;

import io.github.yellowhammer.designerxml.reflect.JaxbReflect;

import jakarta.xml.bind.JAXBException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;

/**
 * Кросс-версионный транскодер объектов метаданных: читает XML моделью версии {@code from} и пересобирает
 * его моделью версии {@code to}, копируя только свойства, существующие в {@code to}. При понижении версии
 * (например 2.20 → 2.10) элементы, которых нет в старом формате, отпадают автоматически — итерируем
 * сеттеры/геттеры ЦЕЛЕВОГО типа. enum-значения переносятся по имени; отсутствующие в {@code to} — пропуск.
 *
 * <p>Назначение: получить «семя» в самом младшем формате из сгенерированного md-sparrow семени 2.20,
 * чтобы выгрузить эталоны (golden) всех форматов (см. docs/scaffold-golden.md). Точность проверяется
 * импортом в реальную ИБ (ibcmd), не XSD.
 */
public final class VersionTranscoder {

  private static final String JAXB_PKG_PREFIX = "io.github.yellowhammer.designerxml.jaxb.";

  private VersionTranscoder() {
  }

  /**
   * Транскодирует один XML объекта метаданных из версии {@code from} в {@code to}.
   *
   * @return XML в формате версии {@code to}
   */
  public static String transcode(Path srcXml, SchemaVersion from, SchemaVersion to)
    throws JAXBException, IOException {
    Object srcRoot = DesignerXml.read(srcXml, from);
    Object srcMdo = JaxbReflect.value(srcRoot);
    String mdPkg = JAXB_PKG_PREFIX + to.name().toLowerCase() + ".mdclasses";
    Object factory = JaxbReflect.newInstance(mdPkg + ".ObjectFactory");
    Object tgtMdo = JaxbReflect.newInstance(mdPkg + ".MetaDataObject");
    deepCopy(srcMdo, tgtMdo, to.name().toLowerCase());
    JaxbReflect.set(tgtMdo, "setVersion", to.metadataObjectVersionAttribute());
    Object jaxbElement = JaxbReflect.call1(factory, "createMetaDataObject", tgtMdo);
    ByteArrayOutputStream buf = new ByteArrayOutputStream();
    DesignerXml.marshal(to, jaxbElement, buf, WriteOptions.defaults());
    return buf.toString(StandardCharsets.UTF_8);
  }

  private static void deepCopy(Object src, Object tgt, String toPkg) {
    Class<?> tgtClass = tgt.getClass();
    for (Method setter : tgtClass.getMethods()) {
      if (!setter.getName().startsWith("set") || setter.getParameterCount() != 1) {
        continue;
      }
      Object srcVal = invokeGetter(src, setter.getName().substring(3));
      if (srcVal == null) {
        continue;
      }
      Object converted = convert(srcVal, setter.getParameterTypes()[0], toPkg);
      if (converted != null) {
        try {
          setter.invoke(tgt, converted);
        } catch (ReflectiveOperationException ignored) {
          // несовместимое свойство — пропускаем (даунгрейд)
        }
      }
    }
    copyListProperties(src, tgt, tgtClass, toPkg);
  }

  /** Списочные свойства (геттер возвращает {@link List}, сеттера нет) — переносим поэлементно. */
  private static void copyListProperties(Object src, Object tgt, Class<?> tgtClass, String toPkg) {
    for (Method getter : tgtClass.getMethods()) {
      if (getter.getParameterCount() != 0 || !List.class.isAssignableFrom(getter.getReturnType())) {
        continue;
      }
      String prop = getterProperty(getter.getName());
      if (prop == null || hasMethod(tgtClass, "set" + prop)) {
        continue;
      }
      Object srcList = invokeNoArg(src, getter.getName());
      if (!(srcList instanceof List<?> sourceItems) || sourceItems.isEmpty()) {
        continue;
      }
      List<Object> tgtList = listResult(getter, tgt);
      if (tgtList == null) {
        continue;
      }
      Class<?> elemType = listElementType(getter);
      for (Object item : sourceItems) {
        Object converted = convert(item, elemType == null ? Object.class : elemType, toPkg);
        if (converted != null) {
          tgtList.add(converted);
        }
      }
    }
  }

  private static Object convert(Object srcVal, Class<?> targetType, String toPkg) {
    Object value = JaxbReflect.value(srcVal);
    if (value == null || value instanceof List) {
      return null;
    }
    Class<?> vc = value.getClass();
    // Невёрсионные типы (String, Boolean, BigDecimal, QName, byte[], DOM …) — копируем как есть.
    if (!vc.getName().startsWith(JAXB_PKG_PREFIX)) {
      if (targetType == Object.class || targetType.isInstance(value) || wrap(targetType).isInstance(value)) {
        return value;
      }
      return null;
    }
    // Вёрсионный jaxb-тип — переотображаем по СОБСТВЕННОМУ классу значения в целевой пакет
    // (важно для Object/Serializable-списков, где targetType не несёт версию, напр. FixedArray.getValue()).
    Class<?> tgtClass = remap(vc, toPkg);
    if (tgtClass == null) {
      return null;
    }
    if (tgtClass.isEnum()) {
      // enum кладём только в типизированный enum-слот; в anyType/Object-список (напр. FixedArray.getValue())
      // голый enum не маршалится — пропускаем (apply заполнит значение по умолчанию).
      if (!targetType.isEnum()) {
        return null;
      }
      try {
        return Enum.valueOf(tgtClass.asSubclass(Enum.class), ((Enum<?>) value).name());
      } catch (RuntimeException e) {
        return null;
      }
    }
    Object tgt = JaxbReflect.newInstance(tgtClass.getName());
    deepCopy(value, tgt, toPkg);
    return tgt;
  }

  /** Класс той же структуры в целевом пакете версии ({@code .jaxb.vXXX.} → {@code .jaxb.<toPkg>.}). */
  private static Class<?> remap(Class<?> srcClass, String toPkg) {
    String name = srcClass.getName().replaceAll("\\.jaxb\\.v[0-9_]+\\.", ".jaxb." + toPkg + ".");
    try {
      return Class.forName(name);
    } catch (ClassNotFoundException e) {
      return null;
    }
  }

  private static Object invokeGetter(Object src, String prop) {
    Object v = invokeNoArgIfPresent(src, "get" + prop);
    return v != null ? v : invokeNoArgIfPresent(src, "is" + prop);
  }

  private static Object invokeNoArgIfPresent(Object target, String method) {
    if (!hasMethod(target.getClass(), method)) {
      return null;
    }
    return invokeNoArg(target, method);
  }

  private static Object invokeNoArg(Object target, String method) {
    try {
      return target.getClass().getMethod(method).invoke(target);
    } catch (ReflectiveOperationException e) {
      return null;
    }
  }

  @SuppressWarnings("unchecked")
  private static List<Object> listResult(Method getter, Object tgt) {
    try {
      return (List<Object>) getter.invoke(tgt);
    } catch (ReflectiveOperationException e) {
      return null;
    }
  }

  private static boolean hasMethod(Class<?> type, String name) {
    for (Method m : type.getMethods()) {
      if (m.getName().equals(name)) {
        return true;
      }
    }
    return false;
  }

  private static String getterProperty(String getterName) {
    if (getterName.startsWith("get") && getterName.length() > 3) {
      return getterName.substring(3);
    }
    if (getterName.startsWith("is") && getterName.length() > 2) {
      return getterName.substring(2);
    }
    return null;
  }

  private static Class<?> listElementType(Method getter) {
    Type generic = getter.getGenericReturnType();
    if (generic instanceof ParameterizedType pt) {
      Type arg = pt.getActualTypeArguments()[0];
      if (arg instanceof Class<?> c) {
        return c;
      }
    }
    return null;
  }

  private static Class<?> wrap(Class<?> c) {
    if (!c.isPrimitive()) {
      return c;
    }
    if (c == boolean.class) {
      return Boolean.class;
    }
    if (c == int.class) {
      return Integer.class;
    }
    if (c == long.class) {
      return Long.class;
    }
    if (c == double.class) {
      return Double.class;
    }
    if (c == float.class) {
      return Float.class;
    }
    if (c == short.class) {
      return Short.class;
    }
    if (c == byte.class) {
      return Byte.class;
    }
    if (c == char.class) {
      return Character.class;
    }
    return c;
  }
}
