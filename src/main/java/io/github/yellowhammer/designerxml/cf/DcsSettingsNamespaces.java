/*
 * This file is a part of md-sparrow.
 *
 * Copyright (c) 2026
 * Ivan Karlo <i.karlo@outlook.com> and contributors
 *
 * SPDX-License-Identifier: LGPL-3.0-or-later
 *
 * md-sparrow is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3.0 of the License, or (at your option) any later version.
 *
 * md-sparrow is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with md-sparrow.
 */
package io.github.yellowhammer.designerxml.cf;

import io.github.yellowhammer.designerxml.SchemaVersion;
import io.github.yellowhammer.edt.EdtLayout;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Объявления пространств имён значений оформления на верхнеуровневом {@code dcsset:settings}
 * варианта настроек схемы компоновки данных, как их пишет платформа в формате выгрузки.
 *
 * <p>Четвёрку {@code style}, {@code sys}, {@code web}, {@code win} платформа пишет во всех
 * форматах, остальное с формата из {@link #LATE}. Канонический эталон макета СКД снят в самом
 * новом формате: {@code Template.xml} старого формата получается из него удалением объявлений,
 * которых формат ещё не пишет ({@link #project}), а новый вариант настроек получает объявления
 * своего формата ({@link #declarations}).
 */
public final class DcsSettingsNamespaces {

  /** Путь к корню схемы для {@link XmlLines#children}. */
  private static final List<String> ROOT = List.of("DataCompositionSchema");

  /** Пространство имён и формат, с которого платформа объявляет его на настройках варианта. */
  private record LateNamespace(String prefix, String uri, SchemaVersion since) {

    /** Объявление в открывающем теге вместе с пробелом перед ним. */
    Pattern declaration() {
      return Pattern.compile("\\s+xmlns:" + Pattern.quote(prefix) + "=\"" + Pattern.quote(uri) + "\"");
    }
  }

  /**
   * Объявления, появившиеся на настройках варианта позже четвёрки; платформа ставит их перед ней.
   *
   * <p>Формат 2.21 объявляет {@code pal} первым, 2.10-2.20 не объявляют: {@code Ext/Template.xml}
   * эталонов {@code snapshots/<формат>/cf-dcs} между форматами отличается только этим. В корне
   * описаний {@code pal} появляется с того же формата ({@link FormatProjection}).
   */
  private static final List<LateNamespace> LATE = List.of(
    new LateNamespace("pal", "http://v8.1c.ru/8.1/data/ui/colors/palette", SchemaVersion.V2_21));

  /** Объявления, которые платформа пишет на настройках варианта во всех форматах. */
  private static final String ALWAYS =
    " xmlns:style=\"http://v8.1c.ru/8.1/data/ui/style\""
      + " xmlns:sys=\"http://v8.1c.ru/8.1/data/ui/fonts/system\""
      + " xmlns:web=\"http://v8.1c.ru/8.1/data/ui/colors/web\""
      + " xmlns:win=\"http://v8.1c.ru/8.1/data/ui/colors/windows\"";

  private DcsSettingsNamespaces() {
  }

  /**
   * Объявления нового верхнеуровневого {@code dcsset:settings}: поздние объявления формата, затем
   * четвёрка. У {@code Template.dcs} проекта 1С:EDT поздних объявлений нет (см. {@link #forEdt}).
   *
   * @param templateFile файл схемы
   * @param version формат выгрузки
   * @return объявления, каждое с пробелом впереди
   */
  static String declarations(Path templateFile, SchemaVersion version) {
    boolean edt = EdtLayout.isSchemaFile(templateFile.toString());
    StringBuilder out = new StringBuilder();
    for (LateNamespace namespace : LATE) {
      if (!edt && version.compareTo(namespace.since()) >= 0) {
        out.append(" xmlns:").append(namespace.prefix()).append("=\"").append(namespace.uri()).append('"');
      }
    }
    return out.append(ALWAYS).toString();
  }

  /**
   * Текст схемы из канонического эталона в формате {@code version}: у {@code dcsset:settings}
   * вариантов корня снимаются объявления, которых формат ещё не пишет. Остальной текст, BOM и
   * переводы строк не меняются.
   *
   * @param canonicalXml текст схемы, записанный платформой самого нового формата
   * @param version формат результата
   * @return текст схемы формата {@code version}
   */
  static String project(String canonicalXml, SchemaVersion version) {
    return without(canonicalXml, namespace -> version.compareTo(namespace.since()) < 0);
  }

  /**
   * Текст схемы из канонического эталона для {@code Template.dcs} проекта 1С:EDT: без поздних
   * объявлений. Формата выгрузки у проекта нет, эталона 1С:EDT для них тоже, а {@code Template.dcs}
   * фикстуры ssl31-edt {@code pal} не объявляют.
   *
   * @param canonicalXml текст схемы, записанный платформой самого нового формата
   * @return текст схемы без поздних объявлений
   */
  public static String forEdt(String canonicalXml) {
    return without(canonicalXml, namespace -> true);
  }

  private static String without(String xml, Predicate<LateNamespace> drop) {
    List<int[]> cuts = new ArrayList<>();
    for (XmlLines.Node variant : XmlLines.children(xml, ROOT)) {
      if (!"settingsVariant".equals(variant.name())) {
        continue;
      }
      for (XmlLines.Node child : XmlLines.childrenAt(xml, variant.start())) {
        if (!"settings".equals(child.name())) {
          continue;
        }
        String tag = xml.substring(child.start(), XmlLines.startTagEnd(xml, child.start()) + 1);
        for (LateNamespace namespace : LATE) {
          Matcher declared = namespace.declaration().matcher(tag);
          if (drop.test(namespace) && declared.find()) {
            cuts.add(new int[] {child.start() + declared.start(), child.start() + declared.end()});
          }
        }
      }
    }
    return XmlLines.removeAll(xml, cuts);
  }
}
