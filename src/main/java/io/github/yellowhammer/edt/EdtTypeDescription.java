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
package io.github.yellowhammer.edt;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.eclipse.emf.ecore.EAttribute;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EEnum;
import org.eclipse.emf.ecore.EEnumLiteral;
import org.eclipse.emf.ecore.EStructuralFeature;

import io.github.yellowhammer.designerxml.cf.MdTypeDescriptionDto;
import io.github.yellowhammer.designerxml.cf.MetadataRefParser;
import io.github.yellowhammer.edt.EdtObjectReader.EdtNode;

/**
 * Описание типа в двух записях: файла 1С:EDT и контракта выгрузки конфигуратора.
 *
 * EDT пишет имена типов платформы без пространств имён ({@code String},
 * {@code CatalogRef.Валюты}), конфигуратор - с префиксом пространства
 * ({@code xs:string}, {@code cfg:CatalogRef.Валюты}). Контракт панели свойств
 * один на оба формата, и это запись конфигуратора: на ней держатся словари
 * подписей и списки ссылочных типов.
 *
 * Пространство имён типа знает схема выгрузки: типы ядра платформы объявлены
 * схемой ядра, оформления - схемой оформления, компоновки данных - её схемами.
 * Всё, чего в схемах нет, принадлежит конфигурации. Семейства ссылок
 * ({@code CatalogRef} без имени, любая ссылка, определяемые типы и
 * характеристики) конфигуратор держит отдельным списком множеств типов.
 *
 * Квалификаторы примитивов EDT пишет без значений по умолчанию, и умолчания
 * спрашиваются у метамодели.
 */
final class EdtTypeDescription {

  /** Примитивы платформы: имя EDT - запись конфигуратора. */
  private static final Map<String, String> PRIMITIVES = Map.of(
      "String", "xs:string",
      "Number", "xs:decimal",
      "Boolean", "xs:boolean",
      "Date", "xs:dateTime");

  private static final String CONFIGURATION_PREFIX = "cfg";

  /** Множество любых ссылок: EDT и конфигуратор зовут его по-разному. */
  private static final String ANY_REF_EDT = "AnyRef";
  private static final String ANY_REF_DESIGNER = "cfg:AnyIBRef";

  /** Семейства, которые конфигуратор всегда считает множествами типов. */
  private static final List<String> SET_FAMILIES = List.of("DefinedType", "Characteristic");

  /** Суффиксы семейств без имени объекта: все справочники, все наборы записей. */
  private static final List<String> FAMILY_SUFFIXES = List.of("Ref", "RecordSet", "ValueManager");
  private static final String OBJECT_SUFFIX = "Object";

  /** Приставка типов компоновки данных в EDT: конфигуратор её не пишет. */
  private static final String DATA_COMPOSITION = "DataComposition";
  private static final List<String> DATA_COMPOSITION_PREFIXES = List.of("dcsset", "dcscor");

  /** Суффикс типа ядра, которого нет в имени EDT: {@code ValueListType} - {@code ValueList}. */
  private static final String TYPE_SUFFIX = "Type";

  private static final String TYPE_DESCRIPTION = "TypeDescription";
  private static final String INDENT = "  ";

  private EdtTypeDescription() {
  }

  /**
   * Описание типа из узла объекта.
   *
   * @param owner узел, которому тип принадлежит: реквизит, константа
   * @param feature имя элемента с типом: {@code type}, {@code valueType}
   * @param model метамодель EDT
   * @return описание в записи конфигуратора либо {@code null}, если типа нет
   */
  static MdTypeDescriptionDto read(EdtNode owner, String feature, EdtModel model) {
    List<EdtNode> nodes = owner.list(feature);
    return nodes.isEmpty() ? null : read(nodes.get(0), model);
  }

  /**
   * Описание типа из его узла.
   *
   * @param type узел {@code type}
   * @param model метамодель EDT
   * @return описание в записи конфигуратора
   */
  static MdTypeDescriptionDto read(EdtNode type, EdtModel model) {
    MdTypeDescriptionDto dto = new MdTypeDescriptionDto();
    for (String name : EdtPropertyValues.list(type, "types")) {
      Typed typed = designerType(name);
      (typed.set() ? dto.typeSets : dto.types).add(typed.text());
    }
    EClass description = model.classOf(TYPE_DESCRIPTION);

    EdtNode string = first(type, "stringQualifiers");
    if (string != null) {
      EClass qualifiers = qualifierClass(description, "stringQualifiers");
      dto.stringQualifiers = new MdTypeDescriptionDto.MdStringQualifiersDto();
      dto.stringQualifiers.length = EdtPropertyValues.text(string, qualifiers, "length");
      dto.stringQualifiers.allowedLength = EdtPropertyValues.flag(string, qualifiers, "fixed") ? "FIXED" : "VARIABLE";
    }
    EdtNode number = first(type, "numberQualifiers");
    if (number != null) {
      EClass qualifiers = qualifierClass(description, "numberQualifiers");
      dto.numberQualifiers = new MdTypeDescriptionDto.MdNumberQualifiersDto();
      dto.numberQualifiers.digits = EdtPropertyValues.text(number, qualifiers, "precision");
      dto.numberQualifiers.fractionDigits = EdtPropertyValues.text(number, qualifiers, "scale");
      dto.numberQualifiers.allowedSign =
          EdtPropertyValues.flag(number, qualifiers, "nonNegative") ? "NONNEGATIVE" : "ANY";
    }
    EdtNode date = first(type, "dateQualifiers");
    if (date != null) {
      EClass qualifiers = qualifierClass(description, "dateQualifiers");
      dto.dateQualifiers = new MdTypeDescriptionDto.MdDateQualifiersDto();
      dto.dateQualifiers.dateFractions = EdtPropertyValues.text(date, qualifiers, "dateFractions");
    }
    EdtNode binary = first(type, "binaryQualifiers");
    if (binary != null) {
      EClass qualifiers = qualifierClass(description, "binaryQualifiers");
      dto.binaryDataQualifiers = new MdTypeDescriptionDto.MdBinaryDataQualifiersDto();
      dto.binaryDataQualifiers.length = EdtPropertyValues.text(binary, qualifiers, "length");
      dto.binaryDataQualifiers.allowedLength =
          EdtPropertyValues.flag(binary, qualifiers, "fixed") ? "FIXED" : "VARIABLE";
    }
    return dto;
  }

  /**
   * Разметка описания типа для файла EDT.
   *
   * Порядок частей задаёт метамодель; значения по умолчанию не пишутся, так что
   * строка без ограничения длины остаётся пустым элементом квалификатора.
   *
   * @param dto описание в записи конфигуратора
   * @param element имя элемента: {@code type}, {@code valueType}
   * @param model метамодель EDT
   * @param indent отступ строки элемента
   * @param eol перевод строки файла
   * @return разметка без отступа первой строки и без перевода строки в конце
   */
  static String render(MdTypeDescriptionDto dto, String element, EdtModel model, String indent, String eol) {
    EClass description = model.classOf(TYPE_DESCRIPTION);
    if (description == null) {
      throw new IllegalStateException("В метамодели нет описания типа");
    }
    List<String> lines = new ArrayList<>();
    String inner = indent + INDENT;
    for (EStructuralFeature feature : description.getEAllStructuralFeatures()) {
      switch (feature.getName()) {
        case "types" -> {
          for (String type : dto.types) {
            lines.add(inner + "<types>" + escape(edtType(type)) + "</types>");
          }
          for (String typeSet : dto.typeSets) {
            lines.add(inner + "<types>" + escape(edtType(typeSet)) + "</types>");
          }
        }
        case "stringQualifiers" -> {
          if (dto.stringQualifiers != null) {
            lines.addAll(qualifier(feature, inner, List.of(
                new Value("length", dto.stringQualifiers.length),
                new Value("fixed", flagText("FIXED".equals(dto.stringQualifiers.allowedLength))))));
          }
        }
        case "numberQualifiers" -> {
          if (dto.numberQualifiers != null) {
            lines.addAll(qualifier(feature, inner, List.of(
                new Value("precision", dto.numberQualifiers.digits),
                new Value("scale", dto.numberQualifiers.fractionDigits),
                new Value("nonNegative", flagText("NONNEGATIVE".equals(dto.numberQualifiers.allowedSign))))));
          }
        }
        case "dateQualifiers" -> {
          if (dto.dateQualifiers != null) {
            lines.addAll(qualifier(feature, inner, List.of(
                new Value("dateFractions", dto.dateQualifiers.dateFractions))));
          }
        }
        case "binaryQualifiers" -> {
          if (dto.binaryDataQualifiers != null) {
            lines.addAll(qualifier(feature, inner, List.of(
                new Value("length", dto.binaryDataQualifiers.length),
                new Value("fixed", flagText("FIXED".equals(dto.binaryDataQualifiers.allowedLength))))));
          }
        }
        default -> {
          // Остальные свойства описания типа в файле объекта не записываются
        }
      }
    }
    StringBuilder text = new StringBuilder("<").append(element).append(">").append(eol);
    for (String line : lines) {
      text.append(line).append(eol);
    }
    return text.append(indent).append("</").append(element).append(">").toString();
  }

  /**
   * Тип в записи конфигуратора.
   *
   * @param text запись типа: {@code xs:string}, {@code cfg:CatalogRef.Валюты}
   * @param set множество типов, а не отдельный тип
   */
  record Typed(String text, boolean set) {
  }

  /**
   * Запись конфигуратора для имени типа из файла EDT.
   *
   * @param name имя типа EDT: {@code String}, {@code CatalogRef.Валюты}, {@code AnyRef}
   * @return тип с пространством имён и признаком множества
   */
  static Typed designerType(String name) {
    String primitive = PRIMITIVES.get(name);
    if (primitive != null) {
      return new Typed(primitive, false);
    }
    if (name.equals(ANY_REF_EDT)) {
      return new Typed(ANY_REF_DESIGNER, true);
    }
    int dot = name.indexOf('.');
    if (dot >= 0) {
      return new Typed(CONFIGURATION_PREFIX + ":" + name, SET_FAMILIES.contains(name.substring(0, dot)));
    }
    if (isFamily(name)) {
      return new Typed(CONFIGURATION_PREFIX + ":" + name, true);
    }
    DesignerTypeSchemas.Declared declared = declared(name);
    if (declared != null) {
      return new Typed(declared.text(), false);
    }
    return new Typed(CONFIGURATION_PREFIX + ":" + name, false);
  }

  /**
   * Имя типа для файла EDT по записи конфигуратора.
   *
   * @param text запись типа: {@code xs:string}, {@code v8:ValueListType}
   * @return имя без пространства имён: {@code String}, {@code ValueList}
   */
  static String edtType(String text) {
    String value = text == null ? "" : text.trim();
    for (Map.Entry<String, String> primitive : PRIMITIVES.entrySet()) {
      if (primitive.getValue().equals(value)) {
        return primitive.getKey();
      }
    }
    if (value.equals(ANY_REF_DESIGNER)) {
      return ANY_REF_EDT;
    }
    int colon = value.indexOf(':');
    if (colon < 0) {
      return value;
    }
    String prefix = value.substring(0, colon);
    String local = value.substring(colon + 1);
    if (DATA_COMPOSITION_PREFIXES.contains(prefix)) {
      return local.startsWith(DATA_COMPOSITION) ? local : DATA_COMPOSITION + local;
    }
    if (prefix.equals("v8") && local.endsWith(TYPE_SUFFIX)) {
      return local.substring(0, local.length() - TYPE_SUFFIX.length());
    }
    return local;
  }

  /**
   * Семейство типов без имени объекта: все справочники, все наборы записей.
   *
   * Объектный тип есть и у отчёта, но множеством конфигуратор считает только
   * виды, у которых есть ссылочный тип.
   */
  private static boolean isFamily(String name) {
    for (String suffix : FAMILY_SUFFIXES) {
      if (name.endsWith(suffix)) {
        return true;
      }
    }
    if (!name.endsWith(OBJECT_SUFFIX)) {
      return false;
    }
    String kind = name.substring(0, name.length() - OBJECT_SUFFIX.length());
    return MetadataRefParser.refTypeSuffixesByObjectType().containsKey(kind);
  }

  /**
   * Объявление типа платформы в схемах конфигуратора.
   *
   * Список значений ядро зовёт {@code ValueListType}, а типы компоновки данных
   * EDT пишет с приставкой, которой в схеме нет.
   */
  private static DesignerTypeSchemas.Declared declared(String name) {
    DesignerTypeSchemas.Declared declared = DesignerTypeSchemas.find(name);
    if (declared == null) {
      declared = DesignerTypeSchemas.find(name + TYPE_SUFFIX);
    }
    if (declared == null && name.startsWith(DATA_COMPOSITION)) {
      declared = DesignerTypeSchemas.find(name.substring(DATA_COMPOSITION.length()));
    }
    return declared;
  }

  /** Значение свойства квалификатора в записи контракта. */
  private record Value(String name, String text) {
  }

  /**
   * Строки квалификатора: без значений по умолчанию, пустой - одним элементом.
   */
  private static List<String> qualifier(EStructuralFeature feature, String indent, List<Value> values) {
    EClass qualifiers = (EClass) feature.getEType();
    List<String> lines = new ArrayList<>();
    for (EStructuralFeature property : qualifiers.getEAllStructuralFeatures()) {
      for (Value value : values) {
        if (!value.name().equals(property.getName()) || value.text() == null) {
          continue;
        }
        String literal = literal(property, value.text());
        if (!literal.isEmpty() && !isDefault(property, literal)) {
          lines.add(indent + INDENT + "<" + property.getName() + ">" + escape(literal) + "</" + property.getName() + ">");
        }
      }
    }
    if (lines.isEmpty()) {
      return List.of(indent + "<" + feature.getName() + "/>");
    }
    List<String> block = new ArrayList<>();
    block.add(indent + "<" + feature.getName() + ">");
    block.addAll(lines);
    block.add(indent + "</" + feature.getName() + ">");
    return block;
  }

  /** Значение в написании файла: у перечислимого свойства это литерал схемы. */
  private static String literal(EStructuralFeature property, String text) {
    String value = text.trim();
    if (!(property.getEType() instanceof EEnum type)) {
      return value;
    }
    for (EEnumLiteral literal : type.getELiterals()) {
      if (EdtPropertyValues.constantName(literal.getName()).equals(value) || literal.getLiteral().equals(value)) {
        return literal.getLiteral();
      }
    }
    throw new IllegalArgumentException("Квалификатор " + property.getName() + " не принимает значение " + text);
  }

  /** Значение, которое схема подразумевает без записи. */
  private static boolean isDefault(EStructuralFeature property, String literal) {
    if (!(property instanceof EAttribute attribute)) {
      return false;
    }
    Object fallback = attribute.getDefaultValue();
    if (fallback == null) {
      return false;
    }
    if (fallback instanceof EEnumLiteral enumLiteral) {
      return enumLiteral.getLiteral().equals(literal);
    }
    return String.valueOf(fallback).equals(literal);
  }

  private static String flagText(boolean value) {
    return value ? "true" : "false";
  }

  private static EClass qualifierClass(EClass description, String feature) {
    EStructuralFeature qualifiers = description == null ? null : description.getEStructuralFeature(feature);
    return qualifiers != null && qualifiers.getEType() instanceof EClass eClass ? eClass : null;
  }

  private static EdtNode first(EdtNode node, String kind) {
    List<EdtNode> nodes = node.list(kind);
    return nodes.isEmpty() ? null : nodes.get(0);
  }

  private static String escape(String value) {
    return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
  }
}
