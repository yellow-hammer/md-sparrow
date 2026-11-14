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

import io.github.yellowhammer.edt.EdtLayout;
import io.github.yellowhammer.edt.EdtModel;
import io.github.yellowhammer.edt.EdtObjectProperties;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Составы наборов типов: определяемых типов ({@code cfg:DefinedType.X}) и характеристик
 * ({@code cfg:Characteristic.X}) из выгрузки или проекта 1С:EDT.
 *
 * <p>Тип поля называет набор одним именем (в схеме компоновки определяемый тип записан UUID), а состав
 * лежит в описании другого объекта. Составы отдаются записью конфигуратора, как типы
 * {@code cf-md-query-tables}: «любая ссылка» и для EDT {@code cfg:AnyIBRef}, перевод в
 * {@code cfg:AnyRef} делает {@link DcsValueTypes.Context}.
 *
 * <p>Вложенный набор подставляется своим составом; набор, замыкающий цикл, остаётся в
 * {@code typeSets}. Повторы убираются, порядок по первому вхождению: свои типы, затем вложенные.
 *
 * <p>Экземпляр живёт один вызов операции и кэширует прочитанное. Ненайденное или нечитаемое
 * описание даёт {@code null} без записи: операция из-за состава не падает.
 */
final class TypeCompositions {

  /** Префикс набора-определяемого типа в записи конфигуратора. */
  static final String DEFINED_TYPE_PREFIX = "cfg:DefinedType.";

  /** Префикс набора-характеристик в записи конфигуратора. */
  static final String CHARACTERISTIC_PREFIX = "cfg:Characteristic.";

  private static final String DEFINED_TYPES_DIR = "DefinedTypes";

  private static final String CHARACTERISTICS_DIR = "ChartsOfCharacteristicTypes";

  /**
   * Свойства объекта выгрузки: от первого {@code <Properties>} до первого закрывающего. Свойства
   * корня идут раньше подчинённых объектов, и {@code <Type>} реквизита в выборку не попадает, даже
   * если у самого объекта тип не записан.
   */
  private static final Pattern DESIGNER_PROPERTIES = Pattern.compile("(?s)<Properties>(.*?)</Properties>");

  /** Тип объекта в его свойствах: первый {@code <Type>}. */
  private static final Pattern DESIGNER_TYPE = Pattern.compile("(?s)<Type>(.*?)</Type>");

  /** Элемент состава: {@code <v8:Type>xs:decimal</v8:Type>}, {@code <v8:TypeSet>cfg:CatalogRef</v8:TypeSet>}. */
  private static final Pattern DESIGNER_TYPE_ENTRY = Pattern.compile(
    "<v8:(Type|TypeSet)>\\s*([^<\\s]+)\\s*</v8:(?:Type|TypeSet)>");

  /** Имя объекта, годное в путь файла: без разделителей каталогов, точек и двоеточий. */
  private static final Pattern OBJECT_NAME = Pattern.compile("[^\\\\/.:*?\"<>|\\s]+");

  private final Path configuration;

  private final boolean edt;

  /** UUID сгенерированного типа (в нижнем регистре) → имя определяемого типа; строится лениво. */
  private Map<String, String> namesById;

  /** Описание набора как записано в файле (ключ — {@code DefinedType.X}); {@code null} — не найдено. */
  private final Map<String, MdTypeDescriptionDto> written = new HashMap<>();

  /** Раскрытый состав набора верхнего уровня (ключ — {@code DefinedType.X}); {@code null} — не найдено. */
  private final Map<String, MdTypeDescriptionDto> expanded = new HashMap<>();

  /**
   * Записи {@code typeCompositions} по источнику в порядке первого обращения; ключ UUID — в нижнем
   * регистре, ключ набора — его текст.
   */
  private final Map<String, Map<String, Object>> collected = new LinkedHashMap<>();

  private TypeCompositions(Path configuration) {
    this.configuration = configuration;
    this.edt = configuration != null && EdtLayout.isObjectFile(configuration);
  }

  /**
   * @param anyFileInDump любой файл выгрузки или проекта EDT: объект, макет, схема
   * @return составы конфигурации над файлом; вне выгрузки — пустые (всё не найдено)
   */
  static TypeCompositions of(Path anyFileInDump) {
    return new TypeCompositions(ConfigurationLanguage.configurationFileOf(anyFileInDump));
  }

  /**
   * @param configuration {@code Configuration.xml} выгрузки или {@code Configuration.mdo} проекта EDT;
   *     {@code null} — выгрузки нет
   */
  static TypeCompositions ofConfiguration(Path configuration) {
    return new TypeCompositions(configuration);
  }

  /**
   * Каталог объектов одного вида: у выгрузки рядом с {@code Configuration.xml}, у EDT — в
   * {@code src} рядом с каталогом {@code Configuration}.
   */
  static Path objectsDirectory(Path configuration, String directory) {
    return EdtLayout.isObjectFile(configuration)
      ? configuration.toAbsolutePath().getParent().getParent().resolve(directory)
      : configuration.toAbsolutePath().resolveSibling(directory);
  }

  /** Имя определяемого типа по UUID сгенерированного типа (без учёта регистра); {@code null} — не найден. */
  String definedTypeName(String typeId) {
    if (configuration == null || typeId == null) {
      return null;
    }
    if (namesById == null) {
      // UUID в схемах редок: DefinedTypes читаются только по первому запросу
      Map<String, String> index = new HashMap<>();
      for (Map.Entry<String, String> entry : DcsValueTypes.definedTypes(configuration).entrySet()) {
        index.put(entry.getValue().toLowerCase(Locale.ROOT), entry.getKey());
      }
      namesById = index;
    }
    return namesById.get(typeId.trim().toLowerCase(Locale.ROOT));
  }

  /**
   * Состав набора типов.
   *
   * @param typeSet {@code cfg:DefinedType.X} или {@code cfg:Characteristic.X}
   * @return раскрытый состав ({@code types}, {@code typeSets}, без квалификаторов — они у словаря);
   *     {@code null} — набор другого вида или описание не найдено
   */
  MdTypeDescriptionDto ofTypeSet(String typeSet) {
    String key = keyOf(typeSet);
    MdTypeDescriptionDto composition = key == null ? null : expandedOf(key);
    if (composition != null) {
      collected.computeIfAbsent(typeSet, source -> record(source, null, composition));
    }
    return copy(composition);
  }

  /**
   * Состав определяемого типа по UUID сгенерированного типа (так тип записан в схеме компоновки).
   *
   * @return раскрытый состав; {@code null} — UUID неизвестен или описание не найдено
   */
  MdTypeDescriptionDto ofDefinedTypeId(String typeId) {
    String name = definedTypeName(typeId);
    MdTypeDescriptionDto composition = name == null ? null : expandedOf("DefinedType." + name);
    if (composition != null) {
      String source = typeId.trim();
      // один UUID в разном регистре даёт одну запись с текстом первого вхождения
      collected.computeIfAbsent(source.toLowerCase(Locale.ROOT), key -> record(source, name, composition));
    }
    return copy(composition);
  }

  /**
   * Записи для ключа ответа {@code typeCompositions}: {@code source} (набор или UUID), {@code name} —
   * только у UUID, {@code types}, {@code typeSets}. Порядок — первого обращения; не найденные
   * составы записей не дают.
   */
  List<Map<String, Object>> collected() {
    List<Map<String, Object>> out = new ArrayList<>();
    for (Map<String, Object> row : collected.values()) {
      Map<String, Object> copy = new LinkedHashMap<>(row);
      copy.put("types", new ArrayList<>(asList(row.get("types"))));
      copy.put("typeSets", new ArrayList<>(asList(row.get("typeSets"))));
      out.add(copy);
    }
    return out;
  }

  private static Map<String, Object> record(String source, String name, MdTypeDescriptionDto composition) {
    Map<String, Object> row = new LinkedHashMap<>();
    row.put("source", source);
    if (name != null) {
      row.put("name", name);
    }
    row.put("types", new ArrayList<>(composition.types));
    row.put("typeSets", new ArrayList<>(composition.typeSets));
    return row;
  }

  @SuppressWarnings("unchecked")
  private static List<String> asList(Object value) {
    return (List<String>) value;
  }

  /** Ключ кэша {@code DefinedType.X} / {@code Characteristic.X}; {@code null} — не набор-состав. */
  private static String keyOf(String typeSet) {
    if (typeSet == null) {
      return null;
    }
    String name;
    String kind;
    if (typeSet.startsWith(DEFINED_TYPE_PREFIX)) {
      kind = "DefinedType";
      name = typeSet.substring(DEFINED_TYPE_PREFIX.length());
    } else if (typeSet.startsWith(CHARACTERISTIC_PREFIX)) {
      kind = "Characteristic";
      name = typeSet.substring(CHARACTERISTIC_PREFIX.length());
    } else {
      return null;
    }
    // имя идёт в путь файла
    return OBJECT_NAME.matcher(name).matches() ? kind + "." + name : null;
  }

  /** Раскрытый состав набора верхнего уровня: каждый раз с чистым множеством посещённых. */
  private MdTypeDescriptionDto expandedOf(String key) {
    if (!expanded.containsKey(key)) {
      expanded.put(key, expand(key, new HashSet<>()));
    }
    return expanded.get(key);
  }

  /**
   * Подстановка вложенных наборов. Посещённые — путь от верхнего набора, а не все встреченные: общий
   * вложенный набор двух веток раскрывается в обеих, а циклом считается только возврат на путь.
   */
  private MdTypeDescriptionDto expand(String key, Set<String> path) {
    MdTypeDescriptionDto own = writtenOf(key);
    if (own == null) {
      return null;
    }
    path.add(key);
    Set<String> types = new LinkedHashSet<>(own.types);
    Set<String> typeSets = new LinkedHashSet<>();
    for (String typeSet : own.typeSets) {
      String nested = keyOf(typeSet);
      MdTypeDescriptionDto inner = nested == null || path.contains(nested) ? null : expand(nested, path);
      if (inner == null) {
        typeSets.add(typeSet);
      } else {
        types.addAll(inner.types);
        typeSets.addAll(inner.typeSets);
      }
    }
    path.remove(key);
    MdTypeDescriptionDto out = new MdTypeDescriptionDto();
    out.types.addAll(types);
    out.typeSets.addAll(typeSets);
    return out;
  }

  /** Описание набора как записано в файле, с кэшем. */
  private MdTypeDescriptionDto writtenOf(String key) {
    if (!written.containsKey(key)) {
      written.put(key, read(key));
    }
    return written.get(key);
  }

  private MdTypeDescriptionDto read(String key) {
    if (configuration == null) {
      return null;
    }
    int dot = key.indexOf('.');
    String name = key.substring(dot + 1);
    String directory = key.startsWith("DefinedType.") ? DEFINED_TYPES_DIR : CHARACTERISTICS_DIR;
    Path folder = objectsDirectory(configuration, directory);
    MdTypeDescriptionDto dto;
    try {
      dto = edt
        ? readEdt(folder.resolve(name).resolve(name + ".mdo"))
        : readDesigner(folder.resolve(name + ".xml"));
    } catch (IOException | RuntimeException e) {
      // нечитаемое описание равносильно ненайденному
      return null;
    }
    // у пустого типа раскрывать нечего: записи нет
    return dto == null || dto.types.isEmpty() && dto.typeSets.isEmpty() ? null : dto;
  }

  /**
   * Тип объекта проекта EDT — корневой {@code <type>} файла {@code .mdo}, переведённый в запись
   * конфигуратора ({@code v8:ValueStorage}, а не {@code cfg:ValueStorage}).
   */
  private static MdTypeDescriptionDto readEdt(Path file) throws IOException {
    if (!Files.isRegularFile(file)) {
      return null;
    }
    MdTypeDescriptionDto described = EdtObjectProperties.readType(file, "type", EdtModel.bundled());
    if (described == null) {
      return null;
    }
    MdTypeDescriptionDto out = new MdTypeDescriptionDto();
    out.types.addAll(described.types);
    out.typeSets.addAll(described.typeSets);
    return out;
  }

  /**
   * Тип объекта выгрузки — первый {@code <Type>} его свойств. Текст типов берётся как записан:
   * префиксы {@code v8}, {@code xs}, {@code cfg} конфигуратор объявляет у корня описания.
   */
  private static MdTypeDescriptionDto readDesigner(Path file) throws IOException {
    if (!Files.isRegularFile(file)) {
      return null;
    }
    Matcher properties = DESIGNER_PROPERTIES.matcher(Files.readString(file, StandardCharsets.UTF_8));
    if (!properties.find()) {
      return null;
    }
    Matcher body = DESIGNER_TYPE.matcher(properties.group(1));
    if (!body.find()) {
      return null;
    }
    MdTypeDescriptionDto out = new MdTypeDescriptionDto();
    Matcher entry = DESIGNER_TYPE_ENTRY.matcher(body.group(1));
    while (entry.find()) {
      ("Type".equals(entry.group(1)) ? out.types : out.typeSets).add(entry.group(2));
    }
    return out;
  }

  /** Копия наружу: кэш не должен меняться руками потребителя. */
  private static MdTypeDescriptionDto copy(MdTypeDescriptionDto dto) {
    if (dto == null) {
      return null;
    }
    MdTypeDescriptionDto out = new MdTypeDescriptionDto();
    out.types.addAll(dto.types);
    out.typeSets.addAll(dto.typeSets);
    return out;
  }
}
