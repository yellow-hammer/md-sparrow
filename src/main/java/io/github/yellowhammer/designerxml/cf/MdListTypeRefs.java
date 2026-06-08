/*
 * This file is a part of md-sparrow.
 *
 * Copyright (c) 2026
 * Ivan Karlo <i.karlo@outlook.com> and contributors
 *
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package io.github.yellowhammer.designerxml.cf;

import io.github.yellowhammer.designerxml.reflect.JaxbReflect;

import org.w3c.dom.Element;

import java.util.ArrayList;
import java.util.List;

/**
 * Текстовые ссылки из элементов {@code Item} в {@code MDListType} (например состав подсистемы).
 * Версионно-нейтрально через {@link JaxbReflect}.
 */
public final class MdListTypeRefs {

  private MdListTypeRefs() {
  }

  /**
   * Заменяет содержимое {@code MDListType} переданными строками ({@code null}-список — очистка).
   *
   * @param list объект {@code MDListType} любой версии (или {@code null})
   */
  public static void replaceItems(Object list, List<String> texts) {
    if (list == null) {
      return;
    }
    List<Object> items = JaxbReflect.list(list, "getItem");
    items.clear();
    if (texts == null) {
      return;
    }
    items.addAll(texts);
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
