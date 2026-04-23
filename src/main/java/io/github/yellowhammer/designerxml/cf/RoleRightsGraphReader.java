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

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Чтение прав роли из {@code Roles/&lt;имя&gt;/Ext/Rights.xml} и сборка рёбер
 * {@link RelationKind#ROLE_OBJECT_RIGHTS} «роль → объект метаданных».
 *
 * <p>Дублирующие пары (роль; объект) сжимаются в одно ребро. Имя права в граф не выносится — это нагрузка для UI,
 * не для топологии. При необходимости детальный список прав остаётся в исходном XML.
 */
public final class RoleRightsGraphReader {

  private RoleRightsGraphReader() {
  }

  /**
   * @param rightsXml путь к файлу {@code Roles/&lt;имя&gt;/Ext/Rights.xml}
   * @return список уникальных рёбер «роль → объект метаданных»; пустой, если файл отсутствует
   */
  public static List<MdObjectGraphExtractor.OutEdge> readEdges(Path rightsXml) throws IOException {
    if (!Files.isRegularFile(rightsXml)) {
      return List.of();
    }
    Document doc = XmlGraphReader.parse(rightsXml);
    Element root = doc.getDocumentElement();
    if (root == null || !"Rights".equals(root.getLocalName())) {
      return List.of();
    }
    Set<String> seen = new LinkedHashSet<>();
    List<MdObjectGraphExtractor.OutEdge> out = new ArrayList<>();
    List<Element> objects = XmlGraphReader.childrenLocal(root, "object");
    int idx = 0;
    for (Element obj : objects) {
      Element nameEl = XmlGraphReader.firstChildLocal(obj, "name");
      Optional<String> key = MetadataRefParser.normalizeMdObjectRef(XmlGraphReader.text(nameEl));
      if (key.isPresent() && seen.add(key.get())) {
        out.add(new MdObjectGraphExtractor.OutEdge(
          key.get(),
          RelationKind.ROLE_OBJECT_RIGHTS,
          "rights[" + idx + "]"));
      }
      idx++;
    }
    return out;
  }
}
