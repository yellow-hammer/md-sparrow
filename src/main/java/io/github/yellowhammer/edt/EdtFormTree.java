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

import java.io.StringReader;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Set;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import com.ctc.wstx.stax.WstxInputFactory;

/**
 * Дерево элементов {@code Form.form} с границами в тексте: основа структурной правки формы EDT.
 *
 * <p>Элемент - узел {@code items} либо служебный узел рядом с ним (командная панель, контекстное
 * меню, подсказка, дополнения таблицы), как и при чтении формы. Имя, номер и вид у EDT записаны
 * вложенными узлами {@code name}, {@code id} и {@code type}.
 */
final class EdtFormTree {

  /** Служебные узлы элемента: у каждого свой узел, а не общий список {@code items}. */
  static final Set<String> ATTACHED = Set.of(
      "autoCommandBar", "commandBar", "extendedTooltip", "contextMenu",
      "searchStringAddition", "viewStatusAddition", "searchControlAddition");

  /** Элемент формы или сама форма. */
  static final class Node {
    /** Узел файла: {@code items}, {@code contextMenu} и прочие, у формы - {@code Form}. */
    final String tag;
    /** Класс по атрибуту {@code xsi:type} без префикса: {@code FormField}, {@code FormGroup}. */
    final String xsiType;
    String name;
    String id;
    /** Вид, записанный узлом {@code type}: {@code InputField}, {@code UsualGroup}; пусто, если не записан. */
    String type = "";
    final int start;
    int end;
    final Node parent;
    /** Вложенные элементы ({@code items}) в порядке файла. */
    final List<Node> items = new ArrayList<>();
    /** Служебные узлы элемента в порядке файла. */
    final List<Node> attachedNodes = new ArrayList<>();

    Node(String tag, String xsiType, int start, Node parent) {
      this.tag = tag;
      this.xsiType = xsiType;
      this.start = start;
      this.parent = parent;
    }

    boolean isForm() {
      return parent == null;
    }

    boolean attached() {
      return ATTACHED.contains(tag);
    }

    /** Все узлы поддерева, включая сам узел и служебные узлы. */
    List<Node> subtree() {
      List<Node> out = new ArrayList<>();
      collect(this, out);
      return out;
    }

    private static void collect(Node node, List<Node> out) {
      out.add(node);
      node.attachedNodes.forEach(child -> collect(child, out));
      node.items.forEach(child -> collect(child, out));
    }
  }

  final Node form;
  final List<Node> all = new ArrayList<>();

  private EdtFormTree(Node form) {
    this.form = form;
  }

  /** Элемент с заданным номером среди элементов формы (не служебных), иначе {@code null}. */
  Node item(String id) {
    for (Node node : all) {
      if (!node.isForm() && id.equals(node.id)) {
        return node;
      }
    }
    return null;
  }

  /** Наибольший номер элемента формы; у корневой командной панели он отрицательный и не в счёт. */
  int maxId() {
    int max = 0;
    for (Node node : all) {
      if (node.id != null && !node.id.isEmpty()) {
        try {
          max = Math.max(max, Integer.parseInt(node.id));
        } catch (NumberFormatException e) {
          throw new IllegalArgumentException("Нечисловой номер элемента формы: " + node.id, e);
        }
      }
    }
    return max;
  }

  /** Есть ли элемент или служебный узел с таким именем. */
  boolean hasName(String name) {
    return all.stream().anyMatch(node -> !node.isForm() && name.equals(node.name));
  }

  static EdtFormTree parse(String xml) {
    try {
      XMLInputFactory factory = new WstxInputFactory();
      factory.setProperty(XMLInputFactory.IS_NAMESPACE_AWARE, Boolean.TRUE);
      XMLStreamReader r = factory.createXMLStreamReader(new StringReader(xml));
      try {
        return parse(xml, r);
      } finally {
        r.close();
      }
    } catch (XMLStreamException e) {
      throw new IllegalArgumentException("Содержимое формы не разбирается: " + e.getMessage(), e);
    }
  }

  /** Открытый узел разбора: имя и элемент формы, если узел им является. */
  private record Frame(String localName, Node node) {
  }

  private static EdtFormTree parse(String xml, XMLStreamReader r) throws XMLStreamException {
    String root = rootElement(r);
    if (!"Form".equals(root)) {
      throw new IllegalArgumentException("Файл не содержимое формы EDT: корень " + root);
    }
    EdtFormTree tree = new EdtFormTree(new Node(root, "Form", r.getLocation().getCharacterOffset(), null));
    tree.all.add(tree.form);
    Deque<Frame> stack = new ArrayDeque<>();
    stack.push(new Frame(root, tree.form));
    while (r.hasNext() && !stack.isEmpty()) {
      int event = r.next();
      if (event == XMLStreamConstants.START_ELEMENT) {
        String local = r.getLocalName();
        int offset = r.getLocation().getCharacterOffset();
        Frame parent = stack.peek();
        Node node = null;
        if (parent.node != null && ("items".equals(local) || ATTACHED.contains(local))) {
          node = new Node(local, xsiType(r), offset, parent.node);
          (ATTACHED.contains(local) ? parent.node.attachedNodes : parent.node.items).add(node);
        } else if (parent.node != null && !parent.node.isForm()
            && ("name".equals(local) || "id".equals(local) || "type".equals(local))) {
          // Текст узла читается сразу: закрывающий тег разбор проходит сам
          String text = r.getElementText().trim();
          switch (local) {
            case "name" -> parent.node.name = text;
            case "id" -> parent.node.id = text;
            default -> parent.node.type = text;
          }
          continue;
        }
        if (node != null) {
          tree.all.add(node);
        }
        stack.push(new Frame(local, node));
      } else if (event == XMLStreamConstants.END_ELEMENT) {
        Frame frame = stack.pop();
        if (frame.node != null) {
          frame.node.end = closingEnd(xml, r);
        }
      }
    }
    return tree;
  }

  /** Имя корневого элемента; разбор остаётся на его открывающем теге. */
  private static String rootElement(XMLStreamReader r) throws XMLStreamException {
    while (r.hasNext()) {
      if (r.next() == XMLStreamConstants.START_ELEMENT) {
        return r.getLocalName();
      }
    }
    throw new IllegalArgumentException("Пустое содержимое формы.");
  }

  private static String xsiType(XMLStreamReader r) {
    for (int i = 0; i < r.getAttributeCount(); i++) {
      if ("type".equals(r.getAttributeLocalName(i))
          && "http://www.w3.org/2001/XMLSchema-instance".equals(r.getAttributeNamespace(i))) {
        String value = r.getAttributeValue(i);
        int colon = value.indexOf(':');
        return colon < 0 ? value : value.substring(colon + 1);
      }
    }
    return "";
  }

  private static int closingEnd(String xml, XMLStreamReader r) {
    int at = r.getLocation().getCharacterOffset();
    int gt = xml.indexOf('>', at);
    if (gt < 0) {
      throw new IllegalArgumentException("Содержимое формы оборвано.");
    }
    return gt + 1;
  }
}
