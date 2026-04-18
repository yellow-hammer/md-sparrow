/*
 * This file is a part of md-sparrow.
 *
 * Copyright (c) 2026
 * Ivan Karlo <i.karlo@outlook.com> and contributors
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
import java.util.Deque;
import java.util.Iterator;

/**
 * Позиции {@code [start, end)} в строке XML (индексы по {@link String}) для прямых дочерних
 * {@code Properties} и {@code ChildObjects} под контейнером объекта (Catalog, Document, …).
 * Используется Woodstox для смещений символов.
 */
public final class MdObjectXmlRegions {

  static final String MD_CLASSES = "http://v8.1c.ru/8.3/MDClasses";

  /** Полуинтервал в исходной строке: {@code start} включительно, {@code end} исключительно. */
  public record Region(int start, int end) {
    public boolean isValid() {
      return start >= 0 && end > start;
    }
  }

  private static final String NON_MD = "\uFFFE";

  private MdObjectXmlRegions() {
  }

  /**
   * Находит границы первого прямого дочернего {@code Properties} под указанным контейнером.
   *
   * @param containerLocal без префикса: Catalog, Document, ExchangePlan, Subsystem
   */
  public static Region findPropertiesRegion(String xml, String containerLocal) throws XMLStreamException {
    return findDirectChildRegion(xml, containerLocal, "Properties");
  }

  /**
   * Находит границы первого прямого дочернего {@code ChildObjects} под указанным контейнером.
   */
  public static Region findChildObjectsRegion(String xml, String containerLocal) throws XMLStreamException {
    return findDirectChildRegion(xml, containerLocal, "ChildObjects");
  }

  /**
   * Границы первого прямого дочернего элемента {@code Properties} с заданным локальным именем (без префикса),
   * где {@code Properties} — прямой потомок {@code Catalog} / {@code Document} / {@code ExchangePlan} /
   * {@code Subsystem}.
   */
  public static Region findDirectChildOfPropertiesRegion(
    String xml,
    String containerLocal,
    String elementLocalName) throws XMLStreamException {
    XMLInputFactory f = new WstxInputFactory();
    f.setProperty(XMLInputFactory.IS_NAMESPACE_AWARE, Boolean.TRUE);
    XMLStreamReader r = f.createXMLStreamReader(new StringReader(xml));
    Deque<String> stack = new ArrayDeque<>();
    try {
      while (r.hasNext()) {
        int ev = r.next();
        if (ev == XMLStreamConstants.START_ELEMENT) {
          String ln = r.getLocalName();
          String uri = r.getNamespaceURI() == null ? "" : r.getNamespaceURI();
          boolean mdLike = MD_CLASSES.equals(uri) || uri.isEmpty();
          if (mdLike && elementLocalName.equals(ln) && stack.size() >= 2 && "Properties".equals(stack.peek())) {
            String parentOfProperties = peekAt(stack, 1);
            if (containerLocal.equals(parentOfProperties)) {
              int start = safeCharOffset(r);
              if (start < 0) {
                return new Region(-1, -1);
              }
              int end = skipElementAndGetEndExclusive(xml, r, elementLocalName);
              if (end < 0) {
                return new Region(-1, -1);
              }
              return new Region(start, end);
            }
          }
          if (mdLike) {
            stack.push(ln);
          } else {
            stack.push(NON_MD);
          }
        } else if (ev == XMLStreamConstants.END_ELEMENT) {
          if (!stack.isEmpty()) {
            stack.pop();
          }
        }
      }
      return new Region(-1, -1);
    } finally {
      r.close();
    }
  }

  /**
   * Границы узла {@code Comment}, являющегося прямым дочерним для {@code Properties}, которые в свою очередь
   * являются прямым дочерним для {@code Catalog} / {@code Document} / … (не комментарий реквизита).
   */
  public static Region findObjectPropertiesCommentRegion(String xml, String containerLocal)
    throws XMLStreamException {
    return findDirectChildOfPropertiesRegion(xml, containerLocal, "Comment");
  }

  /**
   * Границы прямого дочернего элемента {@code Synonym} или {@code Comment} под {@code Properties},
   * которые являются прямым дочерним для {@code Attribute} или {@code TabularSection} под
   * {@code ChildObjects} объекта ({@code Catalog}, {@code Document}, …), при совпадении текста
   * {@code Name} в этих {@code Properties} с {@code objectInternalName}.
   *
   * @param containerLocal без префикса: Catalog, Document, ExchangePlan
   * @param childContainerLocal {@code Attribute} или {@code TabularSection}
   */
  public static Region findDirectChildOfNamedChildObjectPropertiesRegion(
    String xml,
    String containerLocal,
    String childContainerLocal,
    String objectInternalName,
    String elementLocalName) throws XMLStreamException {
    if (objectInternalName == null || elementLocalName == null || childContainerLocal == null) {
      return new Region(-1, -1);
    }
    XMLInputFactory f = new WstxInputFactory();
    f.setProperty(XMLInputFactory.IS_NAMESPACE_AWARE, Boolean.TRUE);
    XMLStreamReader r = f.createXMLStreamReader(new StringReader(xml));
    Deque<String> stack = new ArrayDeque<>();
    String nameForChildObjectProps = null;
    boolean collectingName = false;
    StringBuilder nameBuf = new StringBuilder();
    try {
      while (r.hasNext()) {
        int ev = r.next();
        if (ev == XMLStreamConstants.START_ELEMENT) {
          String ln = r.getLocalName();
          String uri = r.getNamespaceURI() == null ? "" : r.getNamespaceURI();
          boolean mdLike = MD_CLASSES.equals(uri) || uri.isEmpty();
          if (mdLike && "Properties".equals(ln)) {
            if (stack.size() >= 1 && childContainerLocal.equals(stack.peek())) {
              nameForChildObjectProps = null;
            } else if (stack.size() >= 2 && "InternalInfo".equals(stack.peek())
              && childContainerLocal.equals(peekAt(stack, 1))) {
              nameForChildObjectProps = null;
            }
          }
          if (mdLike && elementLocalName.equals(ln)
            && isChildObjectPropertiesContext(stack, childContainerLocal, containerLocal)
            && objectInternalName.equals(nameForChildObjectProps)) {
            int start = safeCharOffset(r);
            if (start < 0) {
              return new Region(-1, -1);
            }
            int end = skipElementAndGetEndExclusive(xml, r, elementLocalName);
            if (end < 0) {
              return new Region(-1, -1);
            }
            return new Region(start, end);
          }
          if (mdLike && "Name".equals(ln) && isChildObjectPropertiesContext(stack, childContainerLocal, containerLocal)) {
            collectingName = true;
            nameBuf.setLength(0);
          }
          if (mdLike) {
            stack.push(ln);
          } else {
            stack.push(NON_MD);
          }
        } else if (ev == XMLStreamConstants.END_ELEMENT) {
          String endLn = r.getLocalName();
          if (collectingName && "Name".equals(endLn)) {
            nameForChildObjectProps = nameBuf.toString().trim();
            collectingName = false;
            nameBuf.setLength(0);
          }
          if (!stack.isEmpty()) {
            stack.pop();
          }
        } else if ((ev == XMLStreamConstants.CHARACTERS
          || ev == XMLStreamConstants.SPACE
          || ev == XMLStreamConstants.CDATA) && collectingName) {
          nameBuf.append(r.getText());
        }
      }
      return new Region(-1, -1);
    } finally {
      r.close();
    }
  }

  /**
   * Границы узла {@code childContainerLocal} под {@code containerLocal/ChildObjects}, где
   * {@code Properties/Name == objectInternalName}.
   */
  public static Region findNamedChildObjectRegion(
    String xml,
    String containerLocal,
    String childContainerLocal,
    String objectInternalName
  ) throws XMLStreamException {
    if (objectInternalName == null || objectInternalName.isBlank()) {
      return new Region(-1, -1);
    }
    XMLInputFactory f = new WstxInputFactory();
    f.setProperty(XMLInputFactory.IS_NAMESPACE_AWARE, Boolean.TRUE);
    XMLStreamReader r = f.createXMLStreamReader(new StringReader(xml));
    Deque<String> stack = new ArrayDeque<>();
    int currentStart = -1;
    int currentDepth = 0;
    String currentName = "";
    boolean collectingName = false;
    StringBuilder nameBuf = new StringBuilder();
    try {
      while (r.hasNext()) {
        int ev = r.next();
        if (ev == XMLStreamConstants.START_ELEMENT) {
          String ln = r.getLocalName();
          String uri = r.getNamespaceURI() == null ? "" : r.getNamespaceURI();
          boolean mdLike = MD_CLASSES.equals(uri) || uri.isEmpty();
          if (mdLike) {
            if (childContainerLocal.equals(ln)
              && stack.size() >= 2
              && "ChildObjects".equals(peekAt(stack, 0))
              && containerLocal.equals(peekAt(stack, 1))) {
              currentStart = safeCharOffset(r);
              currentDepth = stack.size() + 1;
              currentName = "";
            }
            if (currentStart >= 0
              && "Name".equals(ln)
              && stack.size() >= 1
              && "Properties".equals(peekAt(stack, 0))
              && stack.size() >= 2
              && childContainerLocal.equals(peekAt(stack, 1))) {
              collectingName = true;
              nameBuf.setLength(0);
            }
            stack.push(ln);
          } else {
            stack.push(NON_MD);
          }
        } else if (ev == XMLStreamConstants.END_ELEMENT) {
          String endLn = r.getLocalName();
          if (collectingName && "Name".equals(endLn)) {
            currentName = nameBuf.toString().trim();
            collectingName = false;
            nameBuf.setLength(0);
          }
          if (currentStart >= 0
            && childContainerLocal.equals(endLn)
            && stack.size() == currentDepth) {
            int endTagStart = safeCharOffset(r);
            if (endTagStart < 0) {
              return new Region(-1, -1);
            }
            int gt = xml.indexOf('>', endTagStart);
            if (gt < 0) {
              return new Region(-1, -1);
            }
            if (objectInternalName.equals(currentName)) {
              return new Region(currentStart, gt + 1);
            }
            currentStart = -1;
            currentDepth = 0;
            currentName = "";
          }
          if (!stack.isEmpty()) {
            stack.pop();
          }
        } else if ((ev == XMLStreamConstants.CHARACTERS
          || ev == XMLStreamConstants.SPACE
          || ev == XMLStreamConstants.CDATA) && collectingName) {
          nameBuf.append(r.getText());
        }
      }
      return new Region(-1, -1);
    } finally {
      r.close();
    }
  }

  /**
   * Границы узла {@code nestedChildContainerLocal} под
   * {@code containerLocal/ChildObjects/parentChildContainerLocal(Properties/Name=parentObjectName)/ChildObjects}.
   */
  public static Region findNamedNestedChildObjectRegion(
    String xml,
    String containerLocal,
    String parentChildContainerLocal,
    String parentObjectName,
    String nestedChildContainerLocal,
    String nestedObjectName
  ) throws XMLStreamException {
    if (parentObjectName == null || parentObjectName.isBlank() || nestedObjectName == null || nestedObjectName.isBlank()) {
      return new Region(-1, -1);
    }
    XMLInputFactory f = new WstxInputFactory();
    f.setProperty(XMLInputFactory.IS_NAMESPACE_AWARE, Boolean.TRUE);
    XMLStreamReader r = f.createXMLStreamReader(new StringReader(xml));
    Deque<String> stack = new ArrayDeque<>();

    int parentStart = -1;
    int parentDepth = 0;
    String currentParentName = "";
    boolean collectingParentName = false;
    StringBuilder parentNameBuf = new StringBuilder();

    int nestedStart = -1;
    int nestedDepth = 0;
    String currentNestedName = "";
    boolean collectingNestedName = false;
    StringBuilder nestedNameBuf = new StringBuilder();

    try {
      while (r.hasNext()) {
        int ev = r.next();
        if (ev == XMLStreamConstants.START_ELEMENT) {
          String ln = r.getLocalName();
          String uri = r.getNamespaceURI() == null ? "" : r.getNamespaceURI();
          boolean mdLike = MD_CLASSES.equals(uri) || uri.isEmpty();
          if (mdLike) {
            if (parentStart < 0
              && parentChildContainerLocal.equals(ln)
              && stack.size() >= 2
              && "ChildObjects".equals(peekAt(stack, 0))
              && containerLocal.equals(peekAt(stack, 1))) {
              parentStart = safeCharOffset(r);
              parentDepth = stack.size() + 1;
              currentParentName = "";
            }

            boolean insideMatchedParent = parentStart >= 0 && parentObjectName.equals(currentParentName);
            if (insideMatchedParent
              && nestedStart < 0
              && nestedChildContainerLocal.equals(ln)
              && stack.size() >= 2
              && "ChildObjects".equals(peekAt(stack, 0))
              && parentChildContainerLocal.equals(peekAt(stack, 1))) {
              nestedStart = safeCharOffset(r);
              nestedDepth = stack.size() + 1;
              currentNestedName = "";
            }

            if ("Name".equals(ln) && stack.size() >= 1 && "Properties".equals(peekAt(stack, 0))) {
              if (parentStart >= 0
                && stack.size() >= 2
                && parentChildContainerLocal.equals(peekAt(stack, 1))) {
                collectingParentName = true;
                parentNameBuf.setLength(0);
              }
              if (nestedStart >= 0
                && stack.size() >= 2
                && nestedChildContainerLocal.equals(peekAt(stack, 1))) {
                collectingNestedName = true;
                nestedNameBuf.setLength(0);
              }
            }

            stack.push(ln);
          } else {
            stack.push(NON_MD);
          }
        } else if (ev == XMLStreamConstants.END_ELEMENT) {
          String endLn = r.getLocalName();

          if (collectingParentName && "Name".equals(endLn)) {
            currentParentName = parentNameBuf.toString().trim();
            collectingParentName = false;
            parentNameBuf.setLength(0);
          }
          if (collectingNestedName && "Name".equals(endLn)) {
            currentNestedName = nestedNameBuf.toString().trim();
            collectingNestedName = false;
            nestedNameBuf.setLength(0);
          }

          if (nestedStart >= 0 && nestedChildContainerLocal.equals(endLn) && stack.size() == nestedDepth) {
            int endTagStart = safeCharOffset(r);
            if (endTagStart < 0) {
              return new Region(-1, -1);
            }
            int gt = xml.indexOf('>', endTagStart);
            if (gt < 0) {
              return new Region(-1, -1);
            }
            if (nestedObjectName.equals(currentNestedName) && parentObjectName.equals(currentParentName)) {
              return new Region(nestedStart, gt + 1);
            }
            nestedStart = -1;
            nestedDepth = 0;
            currentNestedName = "";
          }

          if (parentStart >= 0 && parentChildContainerLocal.equals(endLn) && stack.size() == parentDepth) {
            parentStart = -1;
            parentDepth = 0;
            currentParentName = "";
            nestedStart = -1;
            nestedDepth = 0;
            currentNestedName = "";
          }

          if (!stack.isEmpty()) {
            stack.pop();
          }
        } else if (ev == XMLStreamConstants.CHARACTERS
          || ev == XMLStreamConstants.SPACE
          || ev == XMLStreamConstants.CDATA) {
          if (collectingParentName) {
            parentNameBuf.append(r.getText());
          }
          if (collectingNestedName) {
            nestedNameBuf.append(r.getText());
          }
        }
      }
      return new Region(-1, -1);
    } finally {
      r.close();
    }
  }

  /**
   * {@code Properties} реквизита/ТЧ: прямой родитель — {@code Attribute}/{@code TabularSection} или
   * {@code InternalInfo} непосредственно под ними (как в выгрузке конфигуратора).
   */
  private static boolean isChildObjectPropertiesContext(
    Deque<String> stack,
    String childContainerLocal,
    String containerLocal) {
    if (stack.size() < 4 || !"Properties".equals(peekAt(stack, 0))) {
      return false;
    }
    String p1 = peekAt(stack, 1);
    if (childContainerLocal.equals(p1)) {
      return "ChildObjects".equals(peekAt(stack, 2)) && containerLocal.equals(peekAt(stack, 3));
    }
    if ("InternalInfo".equals(p1) && childContainerLocal.equals(peekAt(stack, 2))) {
      return "ChildObjects".equals(peekAt(stack, 3)) && containerLocal.equals(peekAt(stack, 4));
    }
    return false;
  }

  /**
   * Вершина стека — текущий открытый узел (innermost); {@code indexFromPeek} 0 — peek, 1 — родитель, …
   */
  private static String peekAt(Deque<String> stack, int indexFromPeek) {
    if (stack.size() <= indexFromPeek) {
      return "";
    }
    Iterator<String> it = stack.iterator();
    for (int i = 0; i < indexFromPeek; i++) {
      it.next();
    }
    return it.next();
  }

  private static Region findDirectChildRegion(String xml, String containerLocal, String childLocal)
    throws XMLStreamException {
    XMLInputFactory f = new WstxInputFactory();
    f.setProperty(XMLInputFactory.IS_NAMESPACE_AWARE, Boolean.TRUE);
    XMLStreamReader r = f.createXMLStreamReader(new StringReader(xml));
    Deque<String> stack = new ArrayDeque<>();
    try {
      while (r.hasNext()) {
        int ev = r.next();
        if (ev == XMLStreamConstants.START_ELEMENT) {
          String ln = r.getLocalName();
          String uri = r.getNamespaceURI() == null ? "" : r.getNamespaceURI();
          boolean mdLike = MD_CLASSES.equals(uri) || uri.isEmpty();
          if (mdLike) {
            String parent = stack.isEmpty() ? "" : stack.peek();
            if (childLocal.equals(ln) && containerLocal.equals(parent)) {
              int start = safeCharOffset(r);
              if (start < 0) {
                return new Region(-1, -1);
              }
              int end = skipElementAndGetEndExclusive(xml, r, ln);
              if (end < 0) {
                return new Region(-1, -1);
              }
              return new Region(start, end);
            }
            stack.push(ln);
          } else {
            stack.push(NON_MD);
          }
        } else if (ev == XMLStreamConstants.END_ELEMENT) {
          if (!stack.isEmpty()) {
            stack.pop();
          }
        }
      }
      return new Region(-1, -1);
    } finally {
      r.close();
    }
  }

  private static int safeCharOffset(XMLStreamReader r) {
    javax.xml.stream.Location loc = r.getLocation();
    if (loc == null) {
      return -1;
    }
    return loc.getCharacterOffset();
  }

  /**
   * Курсор на {@link XMLStreamConstants#START_ELEMENT} открывающего тега; после вызова позиция — за элементом.
   */
  private static int skipElementAndGetEndExclusive(String xml, XMLStreamReader r, String localName)
    throws XMLStreamException {
    int depth = 1;
    while (r.hasNext()) {
      int ev = r.next();
      if (ev == XMLStreamConstants.START_ELEMENT) {
        depth++;
      } else if (ev == XMLStreamConstants.END_ELEMENT) {
        depth--;
        if (depth == 0) {
          if (!localName.equals(r.getLocalName())) {
            return -1;
          }
          int endTagStart = safeCharOffset(r);
          if (endTagStart < 0) {
            return -1;
          }
          int gt = xml.indexOf('>', endTagStart);
          return gt >= 0 ? gt + 1 : -1;
        }
      }
    }
    return -1;
  }
}
