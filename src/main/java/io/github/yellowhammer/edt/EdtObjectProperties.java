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

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.eclipse.emf.ecore.EClass;

import io.github.yellowhammer.designerxml.cf.ExternalArtifactPropertiesDto;
import io.github.yellowhammer.designerxml.cf.MdNamedPropertyDto;
import io.github.yellowhammer.designerxml.cf.MdObjectPropertiesDto;
import io.github.yellowhammer.designerxml.cf.MdTypeDescriptionDto;
import io.github.yellowhammer.edt.EdtObjectReader.EdtNode;

/**
 * Свойства объекта 1С:EDT для панели свойств.
 *
 * Отдаётся тот же контракт, что у выгрузки конфигуратора: поля названы
 * одинаково в обоих форматах, поэтому заполняются по именам, а типы и умолчания
 * берутся из схемы. Ручной росписи свойств под каждый вид объекта нет: она
 * отставала бы от метамодели на каждую новую версию EDT.
 */
public final class EdtObjectProperties {

  /** Виды объектов, чьи свойства контракт держит в поле с другим именем. */
  private static final Map<String, String> BRIDGE_FIELDS = Map.of(
      "enum", "enumeration",
      "informationRegister", "register",
      "accumulationRegister", "register",
      "dataProcessor", "report");

  /** Свойства, которых в формате EDT нет: конфигуратор пишет их сырым XML. */
  private static final List<String> SKIPPED = List.of("standardAttributesXml", "characteristicsXml");

  private EdtObjectProperties() {
  }

  /**
   * Читает свойства объекта.
   *
   * @param objectMdo файл {@code <Тип>/<Имя>/<Имя>.mdo}
   * @param model метамодель EDT
   * @return свойства в общем контракте
   * @throws IOException если файл не читается
   */
  public static MdObjectPropertiesDto readDto(Path objectMdo, EdtModel model) throws IOException {
    EdtNode node = EdtObjectReader.read(objectMdo);
    EClass eClass = model.classOf(node.kind());

    MdObjectPropertiesDto dto = new MdObjectPropertiesDto();
    dto.kind = decapitalize(node.kind());
    dto.internalName = node.name();
    dto.synonymRu = EdtPropertyValues.russian(node, "synonym");
    dto.comment = node.property("comment");
    dto.nestedSubsystems = EdtPropertyValues.list(node, "subsystems");
    dto.contentRefs = EdtPropertyValues.list(node, "content");

    fillChildren(dto, node, model);
    fillBridge(dto, node, eClass);
    return dto;
  }

  /**
   * Заполняет подчинённые узлы объекта.
   *
   * Что у вида бывает своим - реквизиты, измерения, графы, операции - знает
   * схема, и названы они там так же, как поля контракта.
   */
  private static void fillChildren(MdObjectPropertiesDto dto, EdtNode node, EdtModel model) {
    for (EdtModel.Composition item : model.composition(node.kind())) {
      Field field;
      try {
        field = MdObjectPropertiesDto.class.getField(item.feature());
      } catch (NoSuchFieldException absent) {
        // Формы и макеты панель свойств не показывает
        continue;
      }
      if (field.getGenericType().getTypeName().endsWith("<" + MdNamedPropertyDto.class.getName() + ">")) {
        try {
          field.set(dto, children(node, model, item));
        } catch (IllegalAccessException error) {
          throw new IllegalStateException("Не удалось прочитать узлы " + item.feature(), error);
        }
      }
    }
  }

  /**
   * Читает свойства внешнего отчёта или обработки.
   *
   * Внешний артефакт в EDT описан таким же файлом, как объект конфигурации,
   * поэтому и читается так же.
   *
   * @param objectMdo файл объекта
   * @param model метамодель EDT
   * @return вид, имя, синоним и комментарий
   * @throws IOException если файл не читается
   */
  public static ExternalArtifactPropertiesDto readExternalDto(Path objectMdo, EdtModel model)
      throws IOException {
    MdObjectPropertiesDto object = readDto(objectMdo, model);
    ExternalArtifactPropertiesDto dto = new ExternalArtifactPropertiesDto();
    dto.kind = object.kind;
    dto.name = object.internalName;
    dto.synonymRu = object.synonymRu;
    dto.comment = object.comment;
    return dto;
  }

  /**
   * Записывает свойства внешнего отчёта или обработки.
   *
   * @param objectMdo файл объекта
   * @param dto свойства целиком
   * @param model метамодель EDT
   * @throws IOException если файл не читается или не пишется
   */
  public static void writeExternalDto(Path objectMdo, ExternalArtifactPropertiesDto dto, EdtModel model)
      throws IOException {
    MdObjectPropertiesDto object = readDto(objectMdo, model);
    if (dto.name != null && !dto.name.equals(object.internalName)) {
      throw new IllegalArgumentException("Переименование внешнего объекта правится своей командой.");
    }
    object.synonymRu = dto.synonymRu;
    object.comment = dto.comment;
    EdtObjectWriter.writeDto(objectMdo, object, model);
  }

  /**
   * Заполняет свойства вида объекта.
   *
   * Контракт держит их отдельным объектом на каждый вид: {@code catalog},
   * {@code document}. Поля там названы так же, как свойства в файле EDT, и
   * заполняются по имени.
   */
  private static void fillBridge(MdObjectPropertiesDto dto, EdtNode node, EClass eClass) {
    String fieldName = BRIDGE_FIELDS.getOrDefault(dto.kind, dto.kind);
    Field field;
    try {
      field = MdObjectPropertiesDto.class.getField(fieldName);
    } catch (NoSuchFieldException absent) {
      // Виду без своих свойств хватает имени, синонима и подчинённых узлов
      return;
    }

    try {
      Object bridge = field.getType().getDeclaredConstructor().newInstance();
      fillFields(bridge, node, eClass);
      field.set(dto, bridge);
    } catch (ReflectiveOperationException error) {
      throw new IllegalStateException("Не удалось заполнить свойства вида " + dto.kind, error);
    }
  }

  /** Значения полей по именам свойств схемы. */
  private static void fillFields(Object target, EdtNode node, EClass eClass) throws IllegalAccessException {
    for (Field field : target.getClass().getFields()) {
      if (Modifier.isStatic(field.getModifiers()) || SKIPPED.contains(field.getName())) {
        continue;
      }
      String name = field.getName();
      Class<?> type = field.getType();
      if (type == boolean.class || type == Boolean.class) {
        field.set(target, EdtPropertyValues.flag(node, eClass, name));
      } else if (type == List.class && field.getGenericType().getTypeName().endsWith("<java.lang.String>")) {
        field.set(target, EdtPropertyValues.list(node, name));
      } else if (type == String.class) {
        // Синоним, подсказка и пояснение записаны парами язык-значение
        field.set(target, name.endsWith("Ru")
            ? EdtPropertyValues.russian(node, name.substring(0, name.length() - 2))
            : EdtPropertyValues.text(node, eClass, name));
      }
    }
  }

  /** Подчинённые узлы одного вида: реквизиты, измерения, команды. */
  private static List<MdNamedPropertyDto> children(EdtNode node, EdtModel model, EdtModel.Composition item) {
    List<EdtNode> nodes = node.list(item.feature());
    if (nodes.isEmpty()) {
      return List.of();
    }
    // Класс узла записан не в файле, а в схеме владельца: в файле у реквизита
    // стоит имя свойства, а не его вида
    EClass eClass = model.classOf(item.objectType());
    List<MdNamedPropertyDto> children = new ArrayList<>();
    for (EdtNode child : nodes) {
      MdNamedPropertyDto dto = namedProperty(child, eClass);
      // У табличной части свои реквизиты
      for (EdtModel.Composition nested : model.composition(item.objectType())) {
        if (nested.feature().equals("attributes")) {
          dto.attributes = children(child, model, nested);
        }
      }
      children.add(dto);
    }
    return children;
  }

  /** Реквизит, измерение или команда: имя, синоним и свойства из схемы. */
  private static MdNamedPropertyDto namedProperty(EdtNode node, EClass eClass) {
    MdNamedPropertyDto dto = new MdNamedPropertyDto();
    try {
      fillFields(dto, node, eClass);
    } catch (IllegalAccessException error) {
      throw new IllegalStateException("Не удалось прочитать узел " + node.kind(), error);
    }
    dto.name = node.name();
    dto.synonymRu = EdtPropertyValues.russian(node, "synonym");
    dto.toolTipRu = EdtPropertyValues.russian(node, "toolTip");
    dto.type = typeDescription(node);
    return dto;
  }

  /** Тип значения: EDT записывает его именами платформы, без пространств имён. */
  private static MdTypeDescriptionDto typeDescription(EdtNode node) {
    List<EdtNode> types = node.list("type");
    if (types.isEmpty()) {
      return null;
    }
    MdTypeDescriptionDto dto = new MdTypeDescriptionDto();
    dto.types = EdtPropertyValues.list(types.get(0), "types");
    dto.typeSets = EdtPropertyValues.list(types.get(0), "typeSet");
    return dto;
  }

  /** Вид объекта в терминах контракта: {@code Catalog} - {@code catalog}. */
  private static String decapitalize(String kind) {
    return kind.isEmpty() ? kind : Character.toLowerCase(kind.charAt(0)) + kind.substring(1);
  }
}
