/*
 * This file is a part of md-sparrow.
 *
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package io.github.yellowhammer.designerxml.cf;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Элемент многоязычной строки в тексте выгрузки.
 *
 * <p>Синоним, представления и пояснение хранят по элементу на язык. Правка идёт
 * по одному языку, поэтому элемент не переписывается целиком: строки остальных
 * языков остаются как есть, иначе текст на них теряется при первом сохранении.
 */
public final class LocalStringElement {

  private static final Pattern ITEM = Pattern.compile(
    "<(?:[\\w.-]+:)?item>\\s*<(?:[\\w.-]+:)?lang>([^<]*)</(?:[\\w.-]+:)?lang>"
      + "\\s*<(?:[\\w.-]+:)?content>(.*?)</(?:[\\w.-]+:)?content>\\s*</(?:[\\w.-]+:)?item>",
    Pattern.DOTALL);

  private LocalStringElement() {
  }

  /** Строка на одном языке. */
  public record Item(String lang, String content) {
  }

  /** Элемент несёт строки по языкам. */
  public static boolean hasItems(String elementXml) {
    return elementXml != null && ITEM.matcher(elementXml).find();
  }

  /**
   * Строки элемента в порядке файла.
   *
   * <p>Содержимое остаётся в том написании, в каком лежит в файле: escape-последовательности
   * не разворачиваются, чтобы обратная запись не меняла байты чужих языков.
   */
  public static List<Item> items(String elementXml) {
    List<Item> items = new ArrayList<>();
    if (elementXml == null) {
      return items;
    }
    Matcher matcher = ITEM.matcher(elementXml);
    while (matcher.find()) {
      items.add(new Item(matcher.group(1).trim(), matcher.group(2)));
    }
    return items;
  }

  /** Код языка внутри строки. */
  private static final Pattern LANG = Pattern.compile("<(?:[\\w.-]+:)?lang>[^<]*</(?:[\\w.-]+:)?lang>");

  /**
   * Переводит языковые элементы разметки на заданный язык.
   *
   * <p>Эталон голого объекта снят конфигуратором на русской конфигурации, поэтому
   * подписи в нём записаны русскими. Эталон один на версию формата и остаётся
   * таким: язык у созданного объекта меняется здесь, вместе с текстом.
   *
   * @param xml разметка созданного объекта
   * @param code код языка конфигурации
   * @return разметка, в которой у каждого языкового элемента одна строка на этом языке
   */
  public static String retarget(String xml, String code) {
    Matcher items = ITEM.matcher(xml);
    StringBuilder out = new StringBuilder(xml.length());
    while (items.find()) {
      // Меняется только код языка: отступы, переводы строк и текст остаются байт в байт
      String retargeted = LANG.matcher(items.group()).replaceFirst(
        Matcher.quoteReplacement("<v8:lang>" + code + "</v8:lang>"));
      items.appendReplacement(out, Matcher.quoteReplacement(retargeted));
    }
    items.appendTail(out);
    return out.toString();
  }

  /**
   * Элемент, в котором заменена строка одного языка.
   *
   * @param localName имя элемента: {@code Synonym}, {@code ObjectPresentation}
   * @param existingXml элемент, как он лежит в файле; может быть {@code null}
   * @param code код языка правки
   * @param content новое содержимое; пусто - строка этого языка уходит
   * @return элемент со строками всех языков, пустой элемент, если не осталось ни одной
   */
  public static String merge(String localName, String existingXml, String code, String content) {
    List<Item> items = items(existingXml);
    List<Item> merged = new ArrayList<>();
    boolean written = false;
    for (Item item : items) {
      if (!item.lang().equals(code)) {
        merged.add(item);
        continue;
      }
      if (content != null && !content.isEmpty()) {
        merged.add(new Item(code, content));
      }
      written = true;
    }
    if (!written && content != null && !content.isEmpty()) {
      merged.add(new Item(code, content));
    }
    if (merged.isEmpty()) {
      return "<" + localName + "/>";
    }
    StringBuilder out = new StringBuilder("<").append(localName).append('>');
    for (Item item : merged) {
      out.append("<v8:item>")
        .append("<v8:lang>").append(item.lang()).append("</v8:lang>")
        .append("<v8:content>").append(item.content()).append("</v8:content>")
        .append("</v8:item>");
    }
    return out.append("</").append(localName).append('>').toString();
  }
}
