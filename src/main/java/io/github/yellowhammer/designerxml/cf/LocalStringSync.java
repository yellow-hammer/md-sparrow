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
 * Чтение и запись строки на языке конфигурации у {@code LocalStringType} Designer XML — версионно-нейтрально
 * (через {@link JaxbReflect}; структура {@code getItem()/getLang()/getContent()} одинакова во всех версиях).
 */
public final class LocalStringSync {

  private LocalStringSync() {
  }

  /**
   * Строка на языке конфигурации или {@code ""} (включая {@code null}-аргумент).
   */
  public static String first(Object localString) {
    String code = ConfigurationLanguage.current();
    if (localString == null) {
      return "";
    }
    for (Object item : JaxbReflect.<Object>list(localString, "getItem")) {
      if (code.equals(JaxbReflect.getString(item, "getLang"))) {
        String c = JaxbReflect.getString(item, "getContent");
        return c == null ? "" : c;
      }
    }
    return "";
  }

  /**
   * Ставит строку на языке конфигурации; если её ещё нет, добавляет.
   */
  public static void setOrPut(Object localString, String content) {
    String code = ConfigurationLanguage.current();
    if (localString == null) {
      return;
    }
    List<Object> items = JaxbReflect.list(localString, "getItem");
    for (Object item : items) {
      if (code.equals(JaxbReflect.getString(item, "getLang"))) {
        JaxbReflect.set(item, "setContent", content);
        return;
      }
    }
    Object item = JaxbReflect.newInstance(localString.getClass().getPackageName() + ".LocalStringItemType");
    JaxbReflect.set(item, "setLang", code);
    JaxbReflect.set(item, "setContent", content);
    items.add(item);
  }

  /**
   * Обновляет строку на языке конфигурации, только если она уже есть.
   */
  public static void replace(Object localString, String content) {
    String code = ConfigurationLanguage.current();
    if (localString == null) {
      return;
    }
    for (Object item : JaxbReflect.<Object>list(localString, "getItem")) {
      if (code.equals(JaxbReflect.getString(item, "getLang"))) {
        JaxbReflect.set(item, "setContent", content);
        return;
      }
    }
  }
}
