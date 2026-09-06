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

import java.io.ByteArrayInputStream;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

/**
 * Принадлежность и состояния свойств заимствованного объекта в выгрузке конфигуратора.
 *
 * У заимствованного объекта выгрузка держит в {@code Properties} только те
 * свойства, которые расширение контролирует: их наличие и есть состояние
 * «Checked». Свойства, которые расширение меняет, отмечены в
 * {@code InternalInfo/xr:PropertyState} состоянием «Extended»; там же
 * {@code MultiState} у типа реквизита, дополненного расширением. Имя, комментарий
 * и сама принадлежность состояний не имеют: они есть у любого объекта.
 *
 * Состояния отдаются под именами свойств метамодели EDT, со строчной буквы:
 * так панель различает форматы не больше, чем во всём остальном.
 */
public final class AdoptedStates {

  /** Принадлежность заимствованного объекта в записи выгрузки. */
  public static final String ADOPTED = "Adopted";
  /** Свойство контролируется: значение сверяется с расширяемой конфигурацией. */
  public static final String CHECKED = "Checked";
  /** Свойство изменено расширением. */
  public static final String EXTENDED = "Extended";

  /**
   * Элементы, которые платформа пишет у заимствованного узла всегда, а не по
   * состоянию: имя, комментарий, принадлежность, движения документа, состав
   * подсистемы, тип формы. По проекту EDT того же расширения у них состояния нет.
   */
  private static final Set<String> STATELESS = Set.of(
      "Name", "Comment", "ObjectBelonging", "RegisterRecords", "Content", "FormType");
  /** Узлы, у которых тип пишется всегда: у реквизита, измерения, ресурса и определяемого типа. */
  private static final Set<String> TYPED_ALWAYS = Set.of("Attribute", "Dimension", "Resource", "DefinedType");
  /**
   * Свойства, которые у заимствованного узла записаны только изменёнными: синоним
   * расширение не контролирует, а переопределяет, и его наличие уже значит
   * «Extended». Записи состояния в {@code InternalInfo} платформа для них не держит.
   */
  private static final Set<String> IMPLIED_EXTENDED = Set.of("Synonym");
  private static final String INTERNAL_INFO = "InternalInfo";
  private static final String PROPERTIES = "Properties";
  private static final String CHILD_OBJECTS = "ChildObjects";
  private static final String PROPERTY_STATE = "PropertyState";

  private AdoptedStates() {
  }

  /** Состояния одного узла: объекта или его подчинённого. */
  public static final class Frame {
    public String objectBelonging;
    public final Map<String, String> states = new LinkedHashMap<>();
    /** Подчинённые узлы: элемент выгрузки и имя, например {@code Attribute:Код}. */
    public final Map<String, Frame> children = new LinkedHashMap<>();
    private final Set<String> present = new LinkedHashSet<>();
    private final Map<String, String> explicit = new LinkedHashMap<>();
    private final String element;
    private final int depth;
    private String name = "";

    private Frame(String element, int depth) {
      this.element = element;
      this.depth = depth;
    }

    /** Заимствован ли узел. */
    public boolean adopted() {
      return ADOPTED.equals(objectBelonging);
    }

    private void seal() {
      if (!adopted()) {
        return;
      }
      for (String property : present) {
        states.put(key(property), IMPLIED_EXTENDED.contains(property) ? EXTENDED : CHECKED);
      }
      explicit.forEach((property, state) -> states.put(key(property), state));
    }
  }

  /**
   * Читает принадлежность и состояния объекта и его подчинённых узлов.
   *
   * @param xml содержимое файла объекта
   * @return узел объекта; у своего объекта состояния пусты
   * @throws XMLStreamException если разметка не разбирается
   */
  public static Frame scan(byte[] xml) throws XMLStreamException {
    XMLInputFactory factory = XMLInputFactory.newFactory();
    factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
    factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
    XMLStreamReader reader = factory.createXMLStreamReader(new ByteArrayInputStream(xml));
    Frame root = null;
    Deque<Frame> frames = new ArrayDeque<>();
    Deque<String> path = new ArrayDeque<>();
    String stateProperty = null;
    String stateValue = null;
    StringBuilder text = new StringBuilder();
    try {
      while (reader.hasNext()) {
        int event = reader.next();
        if (event == XMLStreamConstants.START_ELEMENT) {
          String local = reader.getLocalName();
          String parent = path.isEmpty() ? "" : path.peek();
          path.push(local);
          text.setLength(0);
          int depth = path.size();
          Frame top = frames.peek();
          if (depth == 2 || (parent.equals(CHILD_OBJECTS) && top != null && depth == top.depth + 2)) {
            Frame frame = new Frame(local, depth);
            if (root == null) {
              root = frame;
            }
            frames.push(frame);
          } else if (top != null && parent.equals(PROPERTIES) && depth == top.depth + 2
              && !STATELESS.contains(local) && !(local.equals("Type") && TYPED_ALWAYS.contains(top.element))) {
            top.present.add(local);
          } else if (local.equals(PROPERTY_STATE)) {
            stateProperty = null;
            stateValue = null;
          }
        } else if (event == XMLStreamConstants.CHARACTERS || event == XMLStreamConstants.CDATA) {
          text.append(reader.getText());
        } else if (event == XMLStreamConstants.END_ELEMENT) {
          int depth = path.size();
          String local = path.pop();
          String parent = path.isEmpty() ? "" : path.peek();
          String value = text.toString().trim();
          text.setLength(0);
          Frame top = frames.peek();
          if (top == null) {
            continue;
          }
          if (parent.equals(PROPERTIES) && depth == top.depth + 2) {
            if (local.equals("ObjectBelonging")) {
              top.objectBelonging = value;
            } else if (local.equals("Name")) {
              top.name = value;
            }
          } else if (parent.equals(PROPERTY_STATE)) {
            if (local.equals("Property")) {
              stateProperty = value;
            } else if (local.equals("State")) {
              stateValue = value;
            }
          } else if (local.equals(PROPERTY_STATE) && parent.equals(INTERNAL_INFO) && depth == top.depth + 2
              && stateProperty != null && stateValue != null) {
            top.explicit.put(stateProperty, stateValue);
          } else if (depth == top.depth) {
            // Закрылся сам узел: имя известно, подчинённый встаёт под родителя
            Frame closed = frames.pop();
            closed.seal();
            Frame owner = frames.peek();
            if (owner != null) {
              owner.children.put(closed.element + ":" + closed.name, closed);
            }
          }
        }
      }
    } finally {
      reader.close();
    }
    return root == null ? new Frame("", 0) : root;
  }

  /** Есть ли у свойства состояние: имя, комментарий и подобные им пишутся у любого узла. */
  public static boolean stateful(String designerName) {
    return !STATELESS.contains(designerName);
  }

  /** Отмечается ли изменение свойства записью в {@code InternalInfo}, а не одним его наличием. */
  public static boolean recorded(String designerName) {
    return stateful(designerName) && !IMPLIED_EXTENDED.contains(designerName);
  }

  /** Имя свойства метамодели EDT: как в выгрузке, но со строчной буквы; подсказка у неё пишется слитно. */
  public static String key(String designerName) {
    if (designerName.isEmpty()) {
      return designerName;
    }
    if (designerName.equals("ToolTip")) {
      return "tooltip";
    }
    return designerName.substring(0, 1).toLowerCase(Locale.ROOT) + designerName.substring(1);
  }

  /**
   * Переносит принадлежность и состояния в описание объекта и его узлов.
   *
   * @param dto описание, прочитанное из выгрузки
   * @param xml содержимое файла объекта
   */
  public static void apply(MdObjectPropertiesDto dto, byte[] xml) {
    Frame root;
    try {
      root = scan(xml);
    } catch (XMLStreamException error) {
      return;
    }
    dto.objectBelonging = blankToNull(root.objectBelonging);
    dto.propertyStates = root.states.isEmpty() ? null : new LinkedHashMap<>(root.states);
    applyChildren(dto.attributes, "Attribute", root);
    applyChildren(dto.tabularSections, "TabularSection", root);
    applyChildren(dto.dimensions, "Dimension", root);
    applyChildren(dto.resources, "Resource", root);
    applyChildren(dto.enumValues, "EnumValue", root);
    applyChildren(dto.commands, "Command", root);
    applyChildren(dto.columns, "Column", root);
    applyChildren(dto.accountingFlags, "AccountingFlag", root);
    applyChildren(dto.extDimensionAccountingFlags, "ExtDimensionAccountingFlag", root);
    applyChildren(dto.addressingAttributes, "AddressingAttribute", root);
    applyChildren(dto.recalculations, "Recalculation", root);
  }

  private static void applyChildren(List<MdNamedPropertyDto> nodes, String container, Frame owner) {
    if (nodes == null) {
      return;
    }
    for (MdNamedPropertyDto node : nodes) {
      Frame frame = owner.children.get(container + ":" + node.name);
      if (frame == null) {
        continue;
      }
      node.objectBelonging = blankToNull(frame.objectBelonging);
      node.propertyStates = frame.states.isEmpty() ? null : new LinkedHashMap<>(frame.states);
      applyChildren(node.attributes, "Attribute", frame);
    }
  }

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value;
  }
}
