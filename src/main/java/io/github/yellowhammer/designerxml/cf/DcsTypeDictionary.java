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
import io.github.yellowhammer.edt.EdtConfigurationLists;
import io.github.yellowhammer.edt.EdtModel;

import jakarta.xml.bind.JAXBException;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Словарь окна «Тип значения» схемы компоновки ({@code cf-dcs-type-dictionary}): всё, что
 * можно выбрать новым типом, одним ответом.
 *
 * <p>Состав берётся из выгрузки над макетом, а подписи — из тех же таблиц, что у
 * {@code cf-enum-labels}: расширение не разбирает XML конфигурации и не держит своих подписей.
 * Вне выгрузки (внешний отчёт, отдельный файл) новыми бывают только примитивы: ссылочные типы
 * чужой базы проверить не по чему.
 */
public final class DcsTypeDictionary {

  private static final String CHARACTERISTIC_KIND = "ChartOfCharacteristicTypes";

  private static final String DEFINED_TYPE_KIND = "DefinedType";

  private DcsTypeDictionary() {
  }

  /**
   * Словарь типов для схемы.
   *
   * @param templateFile файл схемы: {@code Ext/Template.xml} выгрузки или {@code Template.dcs} EDT
   * @param version формат выгрузки (состав конфигурации читается моделью этого формата)
   * @return {@code configurationFound}, {@code anyRef}, {@code primitives}, {@code refKinds},
   *   {@code characteristics}, {@code definedTypes} ({@code name}, {@code typeId} и, если описание
   *   найдено, {@code composition} — состав формой {@code valueType})
   */
  public static Map<String, Object> read(Path templateFile, SchemaVersion version)
    throws IOException, JAXBException {
    DcsValueTypes.Context context = DcsValueTypes.Context.of(templateFile);
    Path configuration = context.configuration();
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("configurationFound", configuration != null);
    out.put("anyRef", context.anyRef());
    out.put("primitives", primitives());
    List<Map<String, Object>> refKinds = new ArrayList<>();
    List<String> characteristics = new ArrayList<>();
    List<Map<String, Object>> definedTypes = new ArrayList<>();
    if (configuration != null) {
      Map<String, List<String>> objects = objects(context, version);
      for (String kind : DcsValueTypes.REF_KINDS) {
        List<String> names = sorted(objects.get(kind));
        if (names.isEmpty()) {
          continue;
        }
        String refType = kind + "Ref";
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("kind", kind);
        row.put("label", UiLabels.objectKinds().getOrDefault(kind, kind));
        row.put("platformName", UiLabels.refTypeNames().getOrDefault(refType, refType));
        row.put("typeSet", "cfg:" + refType);
        List<String> types = new ArrayList<>();
        for (String name : names) {
          types.add("cfg:" + refType + "." + name);
        }
        row.put("types", types);
        refKinds.add(row);
      }
      for (String name : sorted(objects.get(CHARACTERISTIC_KIND))) {
        characteristics.add("cfg:Characteristic." + name);
      }
      // Состав конфигурации задаёт, какие определяемые типы есть; файл каждого — их UUID
      Map<String, String> typeIds = DcsValueTypes.definedTypes(configuration);
      for (String name : sorted(objects.get(DEFINED_TYPE_KIND))) {
        String typeId = typeIds.get(name);
        if (typeId == null) {
          continue;
        }
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("name", name);
        row.put("typeId", typeId);
        // В схеме определяемый тип записан одним UUID: окну нужен его состав, чтобы сверять и
        // очищать значение параметра так же, как запись
        Map<String, Object> composition = context.definedTypeValueType(name, version);
        if (composition != null) {
          row.put("composition", composition);
        }
        definedTypes.add(row);
      }
    }
    out.put("refKinds", refKinds);
    out.put("characteristics", characteristics);
    out.put("definedTypes", definedTypes);
    return out;
  }

  /**
   * Состав выгрузки над схемой: вид объекта — имена.
   *
   * <p>Тот же состав проверяет писатель типа: новая ссылка должна указывать на объект, который
   * словарь предложил бы, иначе платформа не загрузит схему.
   *
   * @param context контекст схемы с найденной выгрузкой
   */
  static Map<String, List<String>> objects(DcsValueTypes.Context context, SchemaVersion version)
    throws IOException, JAXBException {
    return context.edt()
      ? EdtConfigurationLists.all(context.configuration(), EdtModel.bundled())
      : ConfigurationChildObjectLister.listAll(context.configuration(), version);
  }

  /** Примитивы словаря с подписями и видом квалификатора. */
  private static List<Map<String, Object>> primitives() {
    List<Map<String, Object>> out = new ArrayList<>();
    for (Map.Entry<String, String> entry : DcsValueTypes.PRIMITIVE_QUALIFIERS.entrySet()) {
      Map<String, Object> row = new LinkedHashMap<>();
      row.put("type", entry.getKey());
      row.put("label", UiLabels.valueTypes().getOrDefault(entry.getKey(), entry.getKey()));
      if (!entry.getValue().isEmpty()) {
        row.put("qualifiers", entry.getValue());
      }
      out.add(row);
    }
    return out;
  }

  /** Имена по алфавиту: у EDT состав идёт в порядке файла, у выгрузки — уже отсортирован. */
  private static List<String> sorted(List<String> names) {
    if (names == null) {
      return List.of();
    }
    List<String> out = new ArrayList<>(names);
    Collections.sort(out);
    return out;
  }
}
