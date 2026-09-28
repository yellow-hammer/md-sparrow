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
 * Дерево элементов {@code Ext/Form.xml} с границами в тексте: основа структурной правки.
 *
 * <p>Элемент - узел внутри {@code ChildItems} либо служебный узел рядом с ним (командная
 * панель, контекстное меню, подсказка, дополнения таблицы), как и при чтении формы.
 */
final class FormTree {

  /** Узлы, которые платформа держит рядом с {@code ChildItems}, а не внутри него. */
  static final Set<String> ATTACHED = Set.of(
    "ContextMenu", "AutoCommandBar", "ExtendedTooltip",
    "SearchStringAddition", "ViewStatusAddition", "SearchControlAddition");

  /** Элемент формы или сама форма. */
  static final class Node {
    final String type;
    final String name;
    final String id;
    final int start;
    int end;
    final Node parent;
    final boolean attached;
    /** Вложенные элементы из {@code ChildItems} в порядке файла. */
    final List<Node> items = new ArrayList<>();
    /** Служебные узлы элемента в порядке файла. */
    final List<Node> attachedNodes = new ArrayList<>();
    int childItemsStart = -1;
    int childItemsEnd = -1;
    /** Границы собственного {@code Events}; -1, если обработчиков нет. */
    int eventsStart = -1;
    int eventsEnd = -1;

    Node(String type, String name, String id, int start, Node parent, boolean attached) {
      this.type = type;
      this.name = name;
      this.id = id;
      this.start = start;
      this.parent = parent;
      this.attached = attached;
    }

    boolean isForm() {
      return parent == null;
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

  /** Реквизит, колонка реквизита, команда или параметр формы. */
  static final class Entry {
    final String name;
    final String id;
    final int start;
    int end;
    /** Реквизит колонки; у остальных {@code null}. */
    final Entry owner;
    /** Колонки реквизита-таблицы в порядке файла. */
    final List<Entry> columns = new ArrayList<>();
    int columnsStart = -1;
    int columnsEnd = -1;

    Entry(String name, String id, int start, Entry owner) {
      this.name = name;
      this.id = id;
      this.start = start;
      this.owner = owner;
    }

    /** Путь в данных формы: {@code Реквизит} или {@code Реквизит.Колонка}. */
    String path() {
      return owner == null ? name : owner.name + "." + name;
    }
  }

  final Node form;
  final List<Node> all = new ArrayList<>();
  /** Реквизиты формы в порядке файла. */
  final List<Entry> attributes = new ArrayList<>();
  /** Команды формы в порядке файла. */
  final List<Entry> commands = new ArrayList<>();
  /** Параметры формы в порядке файла. */
  final List<Entry> parameters = new ArrayList<>();
  /** Границы корневых {@code Attributes}, {@code Commands}, {@code Parameters}; -1, если узла нет. */
  int attributesEnd = -1;
  int commandsStart = -1;
  int commandsEnd = -1;
  int parametersStart = -1;
  int parametersEnd = -1;
  /** Границы условного оформления формы ({@code Attributes/ConditionalAppearance}); -1, если его нет. */
  int conditionalAppearanceStart = -1;
  int conditionalAppearanceEnd = -1;
  /** Начало корневого {@code Attributes}: перед ним встаёт {@code ChildItems} формы. */
  int attributesStart = -1;

  private FormTree(Node form) {
    this.form = form;
  }

  /** Элемент с заданным идентификатором среди элементов формы (не служебных), иначе {@code null}. */
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

  static FormTree parse(String xml) {
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

  /** Открытый узел разбора: имя, элемент формы и запись состава, если узел ими является. */
  private record Frame(String localName, Node node, Entry entry) {
    Frame(String localName, Node node) {
      this(localName, node, null);
    }
  }

  /** Корневой узел формы с таким именем: родитель - сама форма. */
  private static boolean rootChild(Deque<Frame> stack, String name) {
    return stack.size() == 2 && name.equals(stack.peek().localName);
  }

  private static FormTree parse(String xml, XMLStreamReader r) throws XMLStreamException {
    String root = rootElement(r);
    if (!"Form".equals(root)) {
      throw new IllegalArgumentException("Файл не содержимое формы: корень " + root);
    }
    FormTree tree = new FormTree(new Node(root, null, null, r.getLocation().getCharacterOffset(), null, false));
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
        Entry entry = null;
        if (rootChild(stack, "Attributes") && "Attribute".equals(local)) {
          entry = new Entry(attribute(r, "name"), attribute(r, "id"), offset, null);
          tree.attributes.add(entry);
        } else if ("Column".equals(local) && "Columns".equals(parent.localName) && columnsOwner(stack) != null) {
          Entry attribute = columnsOwner(stack);
          entry = new Entry(attribute(r, "name"), attribute(r, "id"), offset, attribute);
          attribute.columns.add(entry);
        } else if ("Columns".equals(local) && parent.entry != null && parent.entry.owner == null) {
          parent.entry.columnsStart = offset;
        } else if (rootChild(stack, "Commands") && "Command".equals(local)) {
          entry = new Entry(attribute(r, "name"), attribute(r, "id"), offset, null);
          tree.commands.add(entry);
        } else if (rootChild(stack, "Parameters") && "Parameter".equals(local)) {
          entry = new Entry(attribute(r, "name"), null, offset, null);
          tree.parameters.add(entry);
        } else if ("Events".equals(local) && parent.node != null) {
          parent.node.eventsStart = offset;
        } else if (stack.size() == 1 && "Commands".equals(local)) {
          tree.commandsStart = offset;
        } else if (stack.size() == 1 && "Parameters".equals(local)) {
          tree.parametersStart = offset;
        } else if ("ChildItems".equals(parent.localName) && owner(stack) != null) {
          node = new Node(local, attribute(r, "name"), attribute(r, "id"), offset, owner(stack), false);
          node.parent.items.add(node);
        } else if (ATTACHED.contains(local) && parent.node != null) {
          node = new Node(local, attribute(r, "name"), attribute(r, "id"), offset, parent.node, true);
          node.parent.attachedNodes.add(node);
        } else if ("ChildItems".equals(local) && parent.node != null) {
          parent.node.childItemsStart = offset;
        } else if (parent.node != null && parent.node.isForm() && "Attributes".equals(local)) {
          tree.attributesStart = offset;
        } else if ("ConditionalAppearance".equals(local) && formAttributes(stack)) {
          tree.conditionalAppearanceStart = offset;
        }
        if (node != null) {
          tree.all.add(node);
        }
        stack.push(new Frame(local, node, entry));
      } else if (event == XMLStreamConstants.END_ELEMENT) {
        Frame frame = stack.pop();
        int end = closingEnd(xml, r);
        Frame parent = stack.peek();
        if (frame.node != null) {
          frame.node.end = end;
        } else if (frame.entry != null) {
          frame.entry.end = end;
        } else if ("Columns".equals(frame.localName) && parent != null && parent.entry != null) {
          parent.entry.columnsEnd = end;
        } else if ("Events".equals(frame.localName) && parent != null && parent.node != null) {
          parent.node.eventsEnd = end;
        } else if (stack.size() == 1 && "Attributes".equals(frame.localName)) {
          tree.attributesEnd = end;
        } else if (stack.size() == 1 && "Commands".equals(frame.localName)) {
          tree.commandsEnd = end;
        } else if (stack.size() == 1 && "Parameters".equals(frame.localName)) {
          tree.parametersEnd = end;
        } else if ("ChildItems".equals(frame.localName) && parent != null && parent.node != null) {
          parent.node.childItemsEnd = end;
        } else if ("ConditionalAppearance".equals(frame.localName) && formAttributes(stack)) {
          tree.conditionalAppearanceEnd = end;
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

  /** На вершине стека {@code Attributes} самой формы: условное оформление формы лежит в нём. */
  private static boolean formAttributes(Deque<Frame> stack) {
    if (stack.size() != 2 || !"Attributes".equals(stack.peek().localName)) {
      return false;
    }
    return stack.peekLast().node != null && stack.peekLast().node.isForm();
  }

  /** Реквизит, которому принадлежит {@code Columns} на вершине стека; {@code null} у прочих. */
  private static Entry columnsOwner(Deque<Frame> stack) {
    var it = stack.iterator();
    it.next();
    Entry owner = it.hasNext() ? it.next().entry : null;
    return owner != null && owner.owner == null ? owner : null;
  }

  /** Владелец {@code ChildItems} на вершине стека: элемент или форма. */
  private static Node owner(Deque<Frame> stack) {
    var it = stack.iterator();
    it.next();
    return it.hasNext() ? it.next().node : null;
  }

  private static String attribute(XMLStreamReader r, String name) {
    for (int i = 0; i < r.getAttributeCount(); i++) {
      if (name.equals(r.getAttributeLocalName(i))) {
        return r.getAttributeValue(i);
      }
    }
    return null;
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
