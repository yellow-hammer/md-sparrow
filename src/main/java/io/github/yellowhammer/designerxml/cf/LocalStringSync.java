/*
 * This file is a part of md-sparrow.
 *
 * Copyright (c) 2026
 * Ivan Karlo <i.karlo@outlook.com> and contributors
 *
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package io.github.yellowhammer.designerxml.cf;

/**
 * Синоним и представления ru для полей Designer XML (JAXB v2_20 / v2_21).
 */
public final class LocalStringSync {

  private LocalStringSync() {
  }

  public static String firstRuV20(io.github.yellowhammer.designerxml.jaxb.v2_20.v8_1_data_core.LocalStringType lst) {
    if (lst == null) {
      return "";
    }
    for (var it : lst.getItem()) {
      if ("ru".equals(it.getLang())) {
        return it.getContent() == null ? "" : it.getContent();
      }
    }
    return "";
  }

  public static String firstRuV21(io.github.yellowhammer.designerxml.jaxb.v2_21.v8_1_data_core.LocalStringType lst) {
    if (lst == null) {
      return "";
    }
    for (var it : lst.getItem()) {
      if ("ru".equals(it.getLang())) {
        return it.getContent() == null ? "" : it.getContent();
      }
    }
    return "";
  }

  public static void setOrPutRuV20(
    io.github.yellowhammer.designerxml.jaxb.v2_20.v8_1_data_core.LocalStringType lst,
    String content) {
    if (lst == null) {
      return;
    }
    for (var it : lst.getItem()) {
      if ("ru".equals(it.getLang())) {
        it.setContent(content);
        return;
      }
    }
    var item = new io.github.yellowhammer.designerxml.jaxb.v2_20.v8_1_data_core.LocalStringItemType();
    item.setLang("ru");
    item.setContent(content);
    lst.getItem().add(item);
  }

  public static void replaceRuV20(
    io.github.yellowhammer.designerxml.jaxb.v2_20.v8_1_data_core.LocalStringType lst,
    String content) {
    if (lst == null) {
      return;
    }
    for (var it : lst.getItem()) {
      if ("ru".equals(it.getLang())) {
        it.setContent(content);
        return;
      }
    }
  }

  public static void setOrPutRuV21(
    io.github.yellowhammer.designerxml.jaxb.v2_21.v8_1_data_core.LocalStringType lst,
    String content) {
    if (lst == null) {
      return;
    }
    for (var it : lst.getItem()) {
      if ("ru".equals(it.getLang())) {
        it.setContent(content);
        return;
      }
    }
    var item = new io.github.yellowhammer.designerxml.jaxb.v2_21.v8_1_data_core.LocalStringItemType();
    item.setLang("ru");
    item.setContent(content);
    lst.getItem().add(item);
  }

  public static void replaceRuV21(
    io.github.yellowhammer.designerxml.jaxb.v2_21.v8_1_data_core.LocalStringType lst,
    String content) {
    if (lst == null) {
      return;
    }
    for (var it : lst.getItem()) {
      if ("ru".equals(it.getLang())) {
        it.setContent(content);
        return;
      }
    }
  }
}
