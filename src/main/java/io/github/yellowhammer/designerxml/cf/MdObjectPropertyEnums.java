/*
 * This file is a part of md-sparrow.
 *
 * Copyright (c) 2026
 * Ivan Karlo <i.karlo@outlook.com> and contributors
 *
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package io.github.yellowhammer.designerxml.cf;

import io.github.yellowhammer.designerxml.SchemaVersion;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Допустимые значения перечислимых свойств объектов метаданных для версии формата.
 *
 * <p>Ключ — путь в {@link MdObjectPropertiesDto} вида {@code chartOfCharacteristicTypes.codeSeries},
 * значение — имена констант перечисления модели. Список снимается с самой модели формата, поэтому
 * потребителю не нужно держать свою копию: перечисления меняются от версии к версии вместе с XSD.
 */
public final class MdObjectPropertyEnums {

  private static final String JAXB_PKG_PREFIX = "io.github.yellowhammer.designerxml.jaxb.";

  private MdObjectPropertyEnums() {
  }

  /**
   * Словарь допустимых значений: {@code блок.свойство} → константы перечисления.
   *
   * @param version версия формата выгрузки
   * @return отсортированный по ключу словарь; свойства без перечисления в него не попадают
   */
  public static Map<String, List<String>> forVersion(SchemaVersion version) {
    Map<String, List<String>> out = new LinkedHashMap<>();
    collect(out, "configuration", propertiesClass(version, "Configuration"));
    // Узлы состава лежат списками, а не блоками DTO: их перечисления собираются
    // под общим ключом вида, чтобы палитра реквизита знала допустимые значения
    for (String childKind : CHILD_KINDS) {
      collect(out, childKind, propertiesClass(version, capitalize(childKind)));
    }
    for (Field block : MdObjectPropertiesDto.class.getFields()) {
      String typeName = block.getType().getSimpleName();
      if (!typeName.startsWith("Md") || !typeName.endsWith("PropertiesDto")) {
        continue;
      }
      for (String local : propertiesLocalNames(typeName)) {
        collect(out, block.getName(), propertiesClass(version, local));
      }
    }
    return out;
  }

  /**
   * Классы свойств модели, которые описывает блок DTO.
   *
   * <p>Обычно блок соответствует одному виду объекта; отчёт с обработкой и регистры сведений с
   * регистрами накопления делят один блок, поэтому их перечисления объединяются.
   */
  private static List<String> propertiesLocalNames(String blockTypeSimpleName) {
    String local = blockTypeSimpleName.replaceFirst("^Md", "").replaceFirst("PropertiesDto$", "");
    return switch (local) {
      case "Report" -> List.of("Report", "DataProcessor");
      case "Register" -> List.of("InformationRegister", "AccumulationRegister");
      default -> List.of(local);
    };
  }

  private static Class<?> propertiesClass(SchemaVersion version, String local) {
    String fqcn = JAXB_PKG_PREFIX + version.name().toLowerCase() + ".mdclasses." + local + "Properties";
    try {
      return Class.forName(fqcn);
    } catch (ClassNotFoundException e) {
      throw new IllegalStateException("нет класса " + fqcn, e);
    }
  }

  /** Добавляет в словарь перечислимые свойства класса; при повторе ключа значения объединяются. */
  private static void collect(Map<String, List<String>> out, String block, Class<?> properties) {
    for (Method getter : properties.getMethods()) {
      if (getter.getParameterCount() != 0 || !getter.getName().startsWith("get")) {
        continue;
      }
      Class<?> type = getter.getReturnType();
      if (!type.isEnum()) {
        continue;
      }
      String key = block + "." + propertyName(getter.getName());
      List<String> constants = out.computeIfAbsent(key, k -> new ArrayList<>());
      for (Object constant : type.getEnumConstants()) {
        String name = ((Enum<?>) constant).name();
        if (!constants.contains(name)) {
          constants.add(name);
        }
      }
    }
  }

  /** Виды узлов состава, чьи перечислимые свойства нужны палитре. */
  private static final List<String> CHILD_KINDS = List.of(
    "attribute", "dimension", "resource", "accountingFlag", "extDimensionAccountingFlag", "addressingAttribute");

  private static String capitalize(String value) {
    return Character.toUpperCase(value.charAt(0)) + value.substring(1);
  }

  private static String propertyName(String getterName) {
    String name = getterName.substring(3);
    return Character.toLowerCase(name.charAt(0)) + name.substring(1);
  }
}
