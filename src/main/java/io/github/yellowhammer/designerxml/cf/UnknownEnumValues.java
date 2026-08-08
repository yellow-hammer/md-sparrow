/*
 * This file is a part of md-sparrow.
 *
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package io.github.yellowhammer.designerxml.cf;

import java.nio.file.Path;
import java.util.Locale;

/**
 * Значения перечислений, которых нет в модели.
 *
 * <p>Платформа пишет в выгрузку значения, которых её же XSD не объявляет: режим совместимости
 * перечислен по несовместимым версиям и обрывается на 8.3.12, а в файлах встречаются и 8.3.27,
 * и 8.5.1. Модель такое значение не разбирает, и свойство пропадает: примерно у половины
 * конфигураций режим совместимости прочитать было нельзя.
 *
 * <p>Поэтому значение дочитывается из самого файла и приводится к той же записи, в которой модель
 * отдаёт известные значения. Так вызывающая сторона получает одно и то же независимо от того,
 * знает модель это значение или нет.
 */
public final class UnknownEnumValues {

  private UnknownEnumValues() {
  }

  /**
   * Значение перечислимого свойства, известное модели или дочитанное из файла.
   *
   * @param fromModel имя константы от модели; пусто, если значение ей неизвестно
   * @param xml файл выгрузки
   * @param element имя элемента свойства в XML
   * @return имя константы либо пусто, если свойства в файле нет
   */
  public static String orFromXml(String fromModel, Path xml, String element) {
    if (fromModel != null && !fromModel.isEmpty()) {
      return fromModel;
    }
    return constantName(XmlElementTextReader.read(xml, element));
  }

  /**
   * То же для уже собранной разметки: ею проверяется результат точечной записи.
   *
   * @param fromModel имя константы от модели; пусто, если значение ей неизвестно
   * @param xml разметка выгрузки
   * @param element имя элемента свойства в XML
   * @return имя константы либо пусто, если свойства в разметке нет
   */
  public static String orFromXml(String fromModel, byte[] xml, String element) {
    if (fromModel != null && !fromModel.isEmpty()) {
      return fromModel;
    }
    return constantName(XmlElementTextReader.read(xml, element));
  }

  /**
   * Запись значения XML именем константы: {@code Version8_3_24} -> {@code VERSION_8_3_24}.
   *
   * <p>Так же имена константам даёт генератор модели, поэтому известные и неизвестные значения
   * приходят одинаковыми.
   *
   * @param xmlValue значение из файла
   * @return имя константы либо пусто
   */
  public static String constantName(String xmlValue) {
    if (xmlValue == null || xmlValue.isBlank()) {
      return "";
    }
    String value = xmlValue.trim();
    var out = new StringBuilder(value.length() + 4);
    for (int i = 0; i < value.length(); i++) {
      char c = value.charAt(i);
      boolean boundary = i > 0
        && (Character.isUpperCase(c) || (Character.isDigit(c) && !Character.isDigit(value.charAt(i - 1))))
        && out.charAt(out.length() - 1) != '_';
      if (boundary) {
        out.append('_');
      }
      out.append(Character.toUpperCase(c));
    }
    return out.toString().toUpperCase(Locale.ROOT);
  }
}
