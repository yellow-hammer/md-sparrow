/*
 * This file is a part of md-sparrow.
 *
 * Copyright (c) 2026
 * Ivan Karlo <i.karlo@outlook.com> and contributors
 *
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package io.github.yellowhammer.designerxml.cf;

import org.w3c.dom.Element;

import java.util.ArrayList;
import java.util.List;

/**
 * Текстовые ссылки из элементов {@code Item} в {@code MDListType} (например состав подсистемы).
 */
public final class MdListTypeRefs {

  private MdListTypeRefs() {
  }

  public static void replaceItemsV20(
    io.github.yellowhammer.designerxml.jaxb.v2_20.v8_3_xcf_readable.MDListType list,
    List<String> texts) {
    if (list == null) {
      return;
    }
    list.getItem().clear();
    if (texts == null) {
      return;
    }
    for (String t : texts) {
      list.getItem().add(t);
    }
  }

  public static void replaceItemsV21(
    io.github.yellowhammer.designerxml.jaxb.v2_21.v8_3_xcf_readable.MDListType list,
    List<String> texts) {
    if (list == null) {
      return;
    }
    list.getItem().clear();
    if (texts == null) {
      return;
    }
    for (String t : texts) {
      list.getItem().add(t);
    }
  }

  public static List<String> readItemTexts(List<Object> items) {
    List<String> out = new ArrayList<>();
    if (items == null) {
      return out;
    }
    for (Object o : items) {
      if (o instanceof Element el) {
        String t = el.getTextContent();
        if (t != null) {
          out.add(t.trim());
        }
      } else if (o != null) {
        out.add(o.toString());
      }
    }
    return out;
  }
}
