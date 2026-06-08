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

import java.util.List;

/**
 * Чтение/запись ru-представления у {@code LocalStringType} Designer XML — версионно-нейтрально
 * (через {@link JaxbReflect}; структура {@code getItem()/getLang()/getContent()} одинакова во всех версиях).
 */
public final class LocalStringSync {

  private static final String RU = "ru";

  private LocalStringSync() {
  }

  /**
   * Содержимое ru-элемента или {@code ""} (включая {@code null}-аргумент).
   */
  public static String firstRu(Object localString) {
    if (localString == null) {
      return "";
    }
    for (Object item : JaxbReflect.<Object>list(localString, "getItem")) {
      if (RU.equals(JaxbReflect.getString(item, "getLang"))) {
        String c = JaxbReflect.getString(item, "getContent");
        return c == null ? "" : c;
      }
    }
    return "";
  }

  /**
   * Устанавливает ru-содержимое; если ru-элемента нет — добавляет его.
   */
  public static void setOrPutRu(Object localString, String content) {
    if (localString == null) {
      return;
    }
    List<Object> items = JaxbReflect.list(localString, "getItem");
    for (Object item : items) {
      if (RU.equals(JaxbReflect.getString(item, "getLang"))) {
        JaxbReflect.set(item, "setContent", content);
        return;
      }
    }
    Object item = JaxbReflect.newInstance(localString.getClass().getPackageName() + ".LocalStringItemType");
    JaxbReflect.set(item, "setLang", RU);
    JaxbReflect.set(item, "setContent", content);
    items.add(item);
  }

  /**
   * Обновляет ru-содержимое, только если ru-элемент уже есть (иначе ничего не делает).
   */
  public static void replaceRu(Object localString, String content) {
    if (localString == null) {
      return;
    }
    for (Object item : JaxbReflect.<Object>list(localString, "getItem")) {
      if (RU.equals(JaxbReflect.getString(item, "getLang"))) {
        JaxbReflect.set(item, "setContent", content);
        return;
      }
    }
  }
}
