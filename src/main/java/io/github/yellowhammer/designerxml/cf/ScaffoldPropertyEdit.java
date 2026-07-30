/*
 * This file is a part of md-sparrow.
 *
 * Copyright (c) 2026
 * Ivan Karlo <i.karlo@outlook.com> and contributors
 *
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package io.github.yellowhammer.designerxml.cf;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Точечная правка свойств в тексте эталона: для scaffold конфигурации и расширения.
 *
 * <p>Правка идёт по тексту, а не через модель, потому что эталон должен дойти до результата
 * ровно таким, каким его записала платформа: JAXB при обратной записи меняет объявления
 * пространств имён, отступы и порядок свойств, и файл перестаёт совпадать с выгрузкой
 * конфигуратора. Поэтому меняются только те места, которые задал вызывающий.
 *
 * <p>Отступ и перевод строки для вставок берутся из самого файла: выгрузка платформы идёт
 * табуляцией и CRLF, вставки не должны выбиваться.
 */
final class ScaffoldPropertyEdit {

  private ScaffoldPropertyEdit() {
  }

  /** Значение простого свойства; пустое {@code <tag/>} тоже заполняется. */
  static String setLeaf(String xml, String tag, String value) {
    String selfClose = "<" + tag + "/>";
    int i = xml.indexOf(selfClose);
    if (i >= 0) {
      return xml.substring(0, i) + "<" + tag + ">" + escape(value) + "</" + tag + ">"
        + xml.substring(i + selfClose.length());
    }
    Matcher m = Pattern.compile("(?s)<" + tag + ">.*?</" + tag + ">").matcher(xml);
    if (m.find()) {
      return xml.substring(0, m.start()) + "<" + tag + ">" + escape(value) + "</" + tag + ">"
        + xml.substring(m.end());
    }
    return xml;
  }

  /**
   * Как {@link #setLeaf}, но если свойства в эталоне нет - вставляет его сразу за {@code after}.
   *
   * @param after имя свойства, за которым свойство стоит по схеме формата
   */
  static String setOrInsertLeaf(String xml, String tag, String value, String after) {
    if (xml.contains("<" + tag + ">") || xml.contains("<" + tag + "/>")) {
      return setLeaf(xml, tag, value);
    }
    Matcher anchor = Pattern.compile(
      "(?s)([\\t ]*)<" + after + "(?:/>|>.*?</" + after + ">)").matcher(xml);
    if (!anchor.find()) {
      return xml;
    }
    String inserted = newline(xml) + anchor.group(1)
      + "<" + tag + ">" + escape(value) + "</" + tag + ">";
    return xml.substring(0, anchor.end()) + inserted + xml.substring(anchor.end());
  }

  /** Синоним на русском: заполняет пустой {@code <Synonym/>} или подменяет содержимое. */
  static String setSynonymRu(String xml, String ru) {
    Matcher empty = Pattern.compile("([\\t ]*)<Synonym/>").matcher(xml);
    if (empty.find()) {
      String indent = empty.group(1);
      String newline = newline(xml);
      String filled = indent + "<Synonym>" + newline
        + indent + "\t<v8:item>" + newline
        + indent + "\t\t<v8:lang>ru</v8:lang>" + newline
        + indent + "\t\t<v8:content>" + escape(ru) + "</v8:content>" + newline
        + indent + "\t</v8:item>" + newline
        + indent + "</Synonym>";
      return xml.substring(0, empty.start()) + filled + xml.substring(empty.end());
    }
    Matcher m = Pattern.compile("(?s)<Synonym>.*?</Synonym>").matcher(xml);
    if (!m.find()) {
      return xml;
    }
    String replaced = m.group().replaceFirst(
      "(<v8:content>).*?(</v8:content>)",
      "$1" + Matcher.quoteReplacement(escape(ru)) + "$2");
    return xml.substring(0, m.start()) + replaced + xml.substring(m.end());
  }

  /** Значение простого свойства из текста; {@code <ConfigurationExtension…>} сюда не попадает. */
  static Optional<String> leaf(String xml, String tag) {
    Matcher m = Pattern.compile("<" + tag + ">([^<]*)</" + tag + ">").matcher(xml);
    return m.find() ? Optional.of(m.group(1)) : Optional.empty();
  }

  static String escape(String value) {
    return value
      .replace("&", "&amp;")
      .replace("<", "&lt;")
      .replace(">", "&gt;");
  }

  private static String newline(String xml) {
    return xml.contains("\r\n") ? "\r\n" : "\n";
  }
}
