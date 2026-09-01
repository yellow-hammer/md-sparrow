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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.github.yellowhammer.designerxml.cf.MdObjectStructureDto;
import io.github.yellowhammer.designerxml.cf.StandardAttributeLabels;
import io.github.yellowhammer.designerxml.cf.MdTypeDescriptionDto;
import io.github.yellowhammer.edt.EdtObjectReader.EdtNode;

/**
 * Строение объекта 1С:EDT: чем он раскрывается в дереве метаданных.
 *
 * Что бывает у вида объекта своим, знает схема, и названо это там так же, как
 * поля контракта. Исключение одно: значения перечисления контракт зовёт короче.
 */
public final class EdtObjectStructure {

  /** Поле контракта, названное иначе, чем свойство схемы. */
  private static final Map<String, String> RENAMED = Map.of("values", "enumValues");

  private EdtObjectStructure() {
  }

  /**
   * Читает строение объекта.
   *
   * @param objectMdo файл {@code <Тип>/<Имя>/<Имя>.mdo}
   * @param model метамодель EDT
   * @return строение в общем контракте
   * @throws IOException если файл не читается
   */
  public static MdObjectStructureDto read(Path objectMdo, EdtModel model) throws IOException {
    EdtNode node = EdtObjectReader.read(objectMdo);

    MdObjectStructureDto dto = new MdObjectStructureDto();
    dto.kind = decapitalize(node.kind());
    dto.internalName = node.name();
    dto.attributes = nodes(node.list("attributes"));
    dto.tabularSections = tabularSections(node);
    dto.standardAttributes = names(node.list("standardAttributes"));
    // Подпись стандартного реквизита даёт платформа, а файл - только переопределения
    dto.standardAttributeSynonyms = new LinkedHashMap<>(StandardAttributeLabels.ofObject(dto.kind));
    dto.standardAttributeSynonyms.putAll(synonyms(node.list("standardAttributes")));
    dto.commandSynonyms = synonyms(node.list("commands"));
    dto.childSynonyms = childSynonyms(node, model);

    fillLists(dto, node);
    return dto;
  }

  /**
   * Заполняет списки имён подчинённых узлов.
   *
   * Реквизиты и табличные части прочитаны отдельно: у них своё описание, а
   * остальное дерево показывает именами.
   */
  private static void fillLists(MdObjectStructureDto dto, EdtNode node) {
    for (Field field : MdObjectStructureDto.class.getFields()) {
      if (Modifier.isStatic(field.getModifiers()) || field.getType() != List.class) {
        continue;
      }
      if (!field.getGenericType().getTypeName().endsWith("<java.lang.String>")) {
        continue;
      }
      String feature = RENAMED.getOrDefault(field.getName(), field.getName());
      List<EdtNode> children = node.list(feature);
      if (children.isEmpty()) {
        continue;
      }
      try {
        field.set(dto, names(children));
      } catch (IllegalAccessException error) {
        throw new IllegalStateException("Не удалось прочитать узлы " + feature, error);
      }
    }
  }

  /** Синонимы полей данных: измерения, ресурсы и прочие узлы, которые в дереве идут именами. */
  private static Map<String, String> childSynonyms(EdtNode node, EdtModel model) {
    Map<String, String> synonyms = new LinkedHashMap<>();
    for (EdtModel.Composition item : model.composition(node.kind())) {
      synonyms.putAll(synonyms(node.list(item.feature())));
    }
    return synonyms;
  }

  private static List<String> names(List<EdtNode> nodes) {
    return nodes.stream().map(EdtNode::name).filter(name -> !name.isEmpty()).toList();
  }

  private static Map<String, String> synonyms(List<EdtNode> nodes) {
    Map<String, String> synonyms = new LinkedHashMap<>();
    for (EdtNode node : nodes) {
      String synonym = EdtPropertyValues.russian(node, "synonym");
      if (!node.name().isEmpty() && !synonym.isEmpty()) {
        synonyms.put(node.name(), synonym);
      }
    }
    return synonyms;
  }

  private static List<MdObjectStructureDto.MdNodeDto> nodes(List<EdtNode> children) {
    List<MdObjectStructureDto.MdNodeDto> nodes = new ArrayList<>();
    for (EdtNode child : children) {
      MdObjectStructureDto.MdNodeDto node = new MdObjectStructureDto.MdNodeDto(
          child.name(),
          EdtPropertyValues.russian(child, "synonym"),
          child.property("comment"));
      node.type = typeDescription(child);
      nodes.add(node);
    }
    return nodes;
  }

  private static List<MdObjectStructureDto.MdTabularSectionDto> tabularSections(EdtNode node) {
    List<MdObjectStructureDto.MdTabularSectionDto> sections = new ArrayList<>();
    for (EdtNode child : node.list("tabularSections")) {
      MdObjectStructureDto.MdTabularSectionDto section = new MdObjectStructureDto.MdTabularSectionDto();
      section.name = child.name();
      section.synonymRu = EdtPropertyValues.russian(child, "synonym");
      section.comment = child.property("comment");
      section.attributes = nodes(child.list("attributes"));
      section.standardAttributes = names(child.list("standardAttributes"));
      section.standardAttributeSynonyms = new LinkedHashMap<>(StandardAttributeLabels.ofTabularSection());
      section.standardAttributeSynonyms.putAll(synonyms(child.list("standardAttributes")));
      sections.add(section);
    }
    return sections;
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
