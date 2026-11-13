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

import io.github.yellowhammer.designerxml.cf.LocalString;

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
import io.github.yellowhammer.designerxml.cf.ConfigurationLanguage;
import io.github.yellowhammer.designerxml.cf.LocalStringProperties;
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
  private static final List<String> SKIPPED = List.of("standardAttributesXml", "characteristicsXml", "extendable");

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
    try {
      return ConfigurationLanguage.with(objectMdo, () -> readInLanguage(objectMdo, model));
    } catch (jakarta.xml.bind.JAXBException error) {
      throw new IOException(error);
    }
  }

  private static MdObjectPropertiesDto readInLanguage(Path objectMdo, EdtModel model) throws IOException {
    EdtNode node = EdtObjectReader.read(objectMdo);
    EClass eClass = model.classOf(node.kind());

    MdObjectPropertiesDto dto = new MdObjectPropertiesDto();
    dto.kind = decapitalize(node.kind());
    dto.internalName = node.name();
    dto.synonym = EdtPropertyValues.localized(node, "synonym");
    dto.comment = node.property("comment");
    dto.languageCode = ConfigurationLanguage.current();
    dto.localStringProperties = LocalStringProperties.forResponse();

    dto.objectBelonging = blankToNull(node.property("objectBelonging"));
    dto.propertyStates = extensionStates(node);
    fillChildren(dto, node, model);
    Object bridge = fillBridge(dto, node, eClass, model);
    // Свойства вида без своего блока контракт держит словарём, как и выгрузка
    if (bridge == null) {
      EdtScalarProperties.read(dto, node, eClass);
    }
    fillRefLists(dto, node, eClass, bridge);
    dto.contentMembers = EdtCommonAttributeContent.read(node, eClass);
    return dto;
  }

  /**
   * Заполняет списки ссылок вне свойств вида: состав, вложенные подсистемы,
   * входящие документы, движения.
   *
   * Список, который вид держит у себя, читается вместе с его свойствами.
   */
  private static void fillRefLists(MdObjectPropertiesDto dto, EdtNode node, EClass eClass, Object bridge) {
    for (String field : EdtContractNames.refFields()) {
      String feature = EdtContractNames.refsFeature(eClass, field);
      if (feature == null || EdtContractNames.declares(bridge, field)) {
        continue;
      }
      try {
        MdObjectPropertiesDto.class.getField(field).set(dto, new ArrayList<>(EdtPropertyValues.list(node, feature)));
      } catch (ReflectiveOperationException error) {
        throw new IllegalStateException("Не удалось заполнить список " + field, error);
      }
    }
  }

  /**
   * Заполняет подчинённые узлы объекта.
   *
   * Что у вида бывает своим - реквизиты, измерения, графы, операции - знает
   * схема, и названы они там так же, как поля контракта, кроме собранных в
   * {@link EdtContractNames}.
   */
  private static void fillChildren(MdObjectPropertiesDto dto, EdtNode node, EdtModel model) {
    for (EdtModel.Composition item : model.composition(node.kind())) {
      Field field;
      try {
        field = MdObjectPropertiesDto.class.getField(EdtContractNames.field(item.feature()));
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
   * Описание типа из свойства объекта в записи конфигуратора: например, тип определяемого типа,
   * которого общий контракт свойств не несёт.
   *
   * <p>Квалификаторы без записанных значений получают умолчания метамодели: 1С:EDT их в файл не пишет.
   *
   * @param objectMdo файл объекта
   * @param feature имя элемента с типом, например {@code type}
   * @param model метамодель EDT
   * @return описание либо {@code null}, если элемента нет
   * @throws IOException если файл не читается
   */
  public static MdTypeDescriptionDto readType(Path objectMdo, String feature, EdtModel model) throws IOException {
    return EdtTypeDescription.read(EdtObjectReader.read(objectMdo), feature, model);
  }

  /**
   * Список строк из свойства объекта, которого общий контракт свойств у этого вида не несёт: например,
   * документы последовательности ({@code documents}) для таблиц языка запросов.
   *
   * <p>Такие свойства не входят в {@link #readDto}: запись свойств ({@code EdtObjectWriter}) не умеет
   * писать их обратно.
   *
   * @param objectMdo файл объекта
   * @param feature имя повторяющегося элемента, например {@code documents}
   * @return значения в порядке файла без пустых; элемента нет — пустой список
   * @throws IOException если файл не читается
   */
  public static List<String> readList(Path objectMdo, String feature) throws IOException {
    return EdtPropertyValues.list(EdtObjectReader.read(objectMdo), feature);
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
    dto.synonym = object.synonym;
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
    object.synonym = dto.synonym;
    object.comment = dto.comment;
    EdtObjectWriter.writeDto(objectMdo, object, model);
  }

  /**
   * Заполняет свойства вида объекта.
   *
   * Контракт держит их отдельным объектом на каждый вид: {@code catalog},
   * {@code document}. Поля там названы так же, как свойства в файле EDT, и
   * заполняются по имени.
   *
   * @return свойства вида или {@code null}, если своих у вида нет
   */
  private static Object fillBridge(MdObjectPropertiesDto dto, EdtNode node, EClass eClass, EdtModel model) {
    String fieldName = BRIDGE_FIELDS.getOrDefault(dto.kind, dto.kind);
    Field field;
    try {
      field = MdObjectPropertiesDto.class.getField(fieldName);
    } catch (NoSuchFieldException absent) {
      // Виду без своих свойств хватает имени, синонима и подчинённых узлов
      return null;
    }

    try {
      Object bridge = field.getType().getDeclaredConstructor().newInstance();
      fillFields(bridge, node, eClass, model);
      field.set(dto, bridge);
      return bridge;
    } catch (ReflectiveOperationException error) {
      throw new IllegalStateException("Не удалось заполнить свойства вида " + dto.kind, error);
    }
  }

  /** Значения полей по именам свойств схемы. */
  private static void fillFields(Object target, EdtNode node, EClass eClass, EdtModel model)
      throws IllegalAccessException {
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
      } else if (type == MdTypeDescriptionDto.class) {
        MdTypeDescriptionDto description = EdtTypeDescription.read(node, name, model);
        // Тип без узла в файле пуст, как у общей команды без параметра; у конфигуратора это пустой элемент
        boolean declared = eClass != null && eClass.getEStructuralFeature(name) != null;
        field.set(target, description == null && declared ? new MdTypeDescriptionDto() : description);
      } else if (type == String.class) {
        // Синоним, подсказка и пояснение записаны парами язык-значение
        field.set(target, field.isAnnotationPresent(LocalString.class)
            ? EdtPropertyValues.localized(node, name)
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
      MdNamedPropertyDto dto = namedProperty(child, eClass, model);
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
  private static MdNamedPropertyDto namedProperty(EdtNode node, EClass eClass, EdtModel model) {
    MdNamedPropertyDto dto = new MdNamedPropertyDto();
    try {
      fillFields(dto, node, eClass, model);
    } catch (IllegalAccessException error) {
      throw new IllegalStateException("Не удалось прочитать узел " + node.kind(), error);
    }
    dto.name = node.name();
    dto.synonym = EdtPropertyValues.localized(node, "synonym");
    dto.toolTip = EdtPropertyValues.localized(node, "toolTip");
    dto.type = EdtTypeDescription.read(node, "type", model);
    dto.objectBelonging = blankToNull(node.property("objectBelonging"));
    dto.propertyStates = extensionStates(node);
    return dto;
  }

  /** Вид объекта в терминах контракта: {@code Catalog} - {@code catalog}. */
  /**
   * Состояния свойств заимствованного узла из его блока {@code extension}:
   * {@code Checked} контролируется, {@code Extended} изменено расширением.
   * Описание типа, дополненного расширением, лежит там же узлом и состоянием не считается.
   */
  private static java.util.Map<String, String> extensionStates(EdtNode node) {
    // У своего узла расширения состояний нет: его свойства целиком его
    if (!"Adopted".equals(node.property("objectBelonging"))) {
      return null;
    }
    java.util.Map<String, String> states = new java.util.LinkedHashMap<>();
    for (EdtNode extension : node.list("extension")) {
      for (EdtNode state : extension.children()) {
        if (state.children().isEmpty() && !state.value().isEmpty()) {
          states.put(state.kind(), state.value());
        }
      }
    }
    return states.isEmpty() ? null : states;
  }

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value;
  }

  private static String decapitalize(String kind) {
    return kind.isEmpty() ? kind : Character.toLowerCase(kind.charAt(0)) + kind.substring(1);
  }
}
