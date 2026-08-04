/*
 * This file is a part of md-sparrow.
 *
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package io.github.yellowhammer.designerxml.cf;

import com.ctc.wstx.stax.WstxInputFactory;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import java.io.StringReader;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Позиции {@code [start, end)} в строке {@code Ext/Form.xml}: элемент формы по идентификатору и его
 * прямые дочерние узлы.
 *
 * <p>Идентификаторы уникальны среди элементов формы, но не по всему файлу: такой же {@code id} может
 * быть у реквизита и у команды. Поэтому элементом считается то же, что и при чтении: узел внутри
 * {@code ChildItems} либо узел, который платформа держит рядом с ним (командная панель, контекстное
 * меню, подсказка, строка поиска).
 */
final class FormXmlRegions {

  /** Узлы, которые платформа держит рядом с {@code ChildItems}, а не внутри него. */
  private static final Set<String> ATTACHED_ITEMS = Set.of(
    "ContextMenu",
    "AutoCommandBar",
    "ExtendedTooltip",
    "SearchStringAddition",
    "ViewStatusAddition",
    "SearchControlAddition");

  /** Узлы состава: новое свойство пишется перед ними, среди свойств им не место. */
  private static final Set<String> STRUCTURE_NODES = Set.of(
    "ContextMenu",
    "AutoCommandBar",
    "ExtendedTooltip",
    "SearchStringAddition",
    "ViewStatusAddition",
    "SearchControlAddition",
    "ChildItems",
    "Events");

  private FormXmlRegions() {
  }

  /**
   * Найденный элемент формы: границы самого узла и его прямых дочерних.
   *
   * @param type         имя узла в XML: {@code InputField}, {@code UsualGroup}
   * @param start        смещение {@code <} стартового тега
   * @param end          смещение за закрывающим {@code >} элемента
   * @param emptyTag     элемент записан пустым тегом {@code <Item …/>}
   * @param startTagEnd  смещение {@code >} стартового тега
   * @param properties   прямые дочерние узлы: имя узла -> границы
   * @param insertPos    начало строки, перед которой пишется новое свойство
   * @param insertIndent отступ строки нового свойства
   */
  record FormItemRegion(
    String type,
    int start,
    int end,
    boolean emptyTag,
    int startTagEnd,
    Map<String, MdObjectXmlRegions.Region> properties,
    int insertPos,
    String insertIndent) {
  }

  /**
   * Границы элемента формы с заданным идентификатором.
   *
   * @param xml    содержимое {@code Ext/Form.xml}
   * @param itemId значение атрибута {@code id}
   * @return элемент или {@code null}, если такого элемента в форме нет
   */
  static FormItemRegion findItem(String xml, String itemId) throws XMLStreamException {
    XMLInputFactory f = new WstxInputFactory();
    f.setProperty(XMLInputFactory.IS_NAMESPACE_AWARE, Boolean.TRUE);
    XMLStreamReader r = f.createXMLStreamReader(new StringReader(xml));
    Deque<String> stack = new ArrayDeque<>();
    Deque<Boolean> itemFlags = new ArrayDeque<>();
    int targetDepth = -1;
    int start = -1;
    String type = null;
    Map<String, MdObjectXmlRegions.Region> properties = new LinkedHashMap<>();
    List<String> childOrder = new ArrayList<>();
    try {
      while (r.hasNext()) {
        int ev = r.next();
        if (ev == XMLStreamConstants.START_ELEMENT) {
          String ln = r.getLocalName();
          boolean item = isItemNode(ln, stack, itemFlags);
          if (targetDepth >= 0 && stack.size() == targetDepth + 1) {
            int childStart = r.getLocation().getCharacterOffset();
            int childEnd = elementEndExclusive(xml, r, ln);
            properties.putIfAbsent(ln, new MdObjectXmlRegions.Region(childStart, childEnd));
            childOrder.add(ln);
            continue;
          }
          if (targetDepth < 0 && item && itemId.equals(idAttribute(r))) {
            targetDepth = stack.size();
            start = r.getLocation().getCharacterOffset();
            type = ln;
          }
          stack.push(ln);
          itemFlags.push(item);
        } else if (ev == XMLStreamConstants.END_ELEMENT) {
          if (targetDepth >= 0 && stack.size() == targetDepth + 1) {
            int end = closingTagEndExclusive(xml, r);
            return region(xml, type, start, end, properties, childOrder);
          }
          if (!stack.isEmpty()) {
            stack.pop();
            itemFlags.pop();
          }
        }
      }
      return null;
    } finally {
      r.close();
    }
  }

  private static FormItemRegion region(
    String xml,
    String type,
    int start,
    int end,
    Map<String, MdObjectXmlRegions.Region> properties,
    List<String> childOrder) {
    if (start < 0 || end <= start) {
      return null;
    }
    int tagEnd = startTagEnd(xml, start);
    if (tagEnd < 0) {
      return null;
    }
    boolean emptyTag = xml.charAt(tagEnd - 1) == '/';
    int before = insertBefore(xml, end, properties, childOrder);
    if (before < 0) {
      return null;
    }
    String indent = XmlGranularPatch.currentLineIndent(xml, before);
    return new FormItemRegion(
      type,
      start,
      end,
      emptyTag,
      tagEnd,
      properties,
      lineStart(xml, before),
      structureFirst(childOrder) ? indent : indent + "\t");
  }

  /**
   * Узел, перед которым пишется новое свойство: первый узел состава, иначе закрывающий тег элемента.
   * Платформа держит вложенные элементы и события последними, поэтому свойство идёт до них.
   */
  private static int insertBefore(
    String xml,
    int end,
    Map<String, MdObjectXmlRegions.Region> properties,
    List<String> childOrder) {
    for (String child : childOrder) {
      if (STRUCTURE_NODES.contains(child)) {
        return properties.get(child).start();
      }
    }
    int closing = xml.lastIndexOf("</", end - 1);
    return closing < 0 ? end : closing;
  }

  private static boolean structureFirst(List<String> childOrder) {
    return childOrder.stream().anyMatch(STRUCTURE_NODES::contains);
  }

  private static int lineStart(String xml, int offset) {
    int i = offset;
    while (i > 0 && xml.charAt(i - 1) != '\n' && xml.charAt(i - 1) != '\r') {
      i--;
    }
    return i;
  }

  /** Элемент формы: узел внутри {@code ChildItems} либо узел рядом с ним у формы или другого элемента. */
  private static boolean isItemNode(String localName, Deque<String> stack, Deque<Boolean> itemFlags) {
    String parent = stack.peek();
    if ("ChildItems".equals(parent)) {
      return true;
    }
    if (!ATTACHED_ITEMS.contains(localName)) {
      return false;
    }
    return "Form".equals(parent) || Boolean.TRUE.equals(itemFlags.peek());
  }

  private static String idAttribute(XMLStreamReader r) {
    for (int i = 0; i < r.getAttributeCount(); i++) {
      if ("id".equals(r.getAttributeLocalName(i))) {
        return r.getAttributeValue(i);
      }
    }
    return null;
  }

  /** Смещение {@code >} стартового тега; кавычки атрибутов пропускаем, чтобы не поймать {@code >} внутри. */
  private static int startTagEnd(String xml, int start) {
    char quote = 0;
    for (int i = start; i < xml.length(); i++) {
      char c = xml.charAt(i);
      if (quote != 0) {
        if (c == quote) {
          quote = 0;
        }
      } else if (c == '"' || c == '\'') {
        quote = c;
      } else if (c == '>') {
        return i;
      }
    }
    return -1;
  }

  /** Курсор на стартовом теге; после вызова позиция - за элементом. */
  private static int elementEndExclusive(String xml, XMLStreamReader r, String localName)
    throws XMLStreamException {
    int depth = 1;
    while (r.hasNext()) {
      int ev = r.next();
      if (ev == XMLStreamConstants.START_ELEMENT) {
        depth++;
      } else if (ev == XMLStreamConstants.END_ELEMENT) {
        depth--;
        if (depth == 0) {
          return localName.equals(r.getLocalName()) ? closingTagEndExclusive(xml, r) : -1;
        }
      }
    }
    return -1;
  }

  private static int closingTagEndExclusive(String xml, XMLStreamReader r) {
    int at = r.getLocation().getCharacterOffset();
    if (at < 0) {
      return -1;
    }
    int gt = xml.indexOf('>', at);
    return gt >= 0 ? gt + 1 : -1;
  }
}
