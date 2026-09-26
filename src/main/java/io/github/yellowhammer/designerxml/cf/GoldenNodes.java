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

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Новый дочерний узел объекта (реквизит, табличная часть, команда, измерение…) из эталона платформы.
 *
 * <p>Эталон - объект-владелец канонического формата с узлом каждого вида, как его выгрузила
 * платформа ({@code snapshots/<формат>/cf-object-nodes} в samples-1c-platform, в jar -
 * {@code golden/nodes/<вид владельца>.xml}). Узел свой у каждого вида владельца: набор и порядок
 * свойств реквизита у справочника и у плана видов характеристик разные, у табличной части
 * обработки нет {@code LineNumberLength}. Узел формата V вырезается из проекции всего эталона
 * в формат V ({@link FormatProjection}), затем получает имя, язык подписи и детерминированные UUID.
 *
 * <p>Эталон новой табличной части - пустая табличная часть владельца, эталон реквизита
 * табличной части - реквизит его непустой табличной части.
 */
final class GoldenNodes {

  private static final String RESOURCE = "golden/nodes/";
  private static final String TABULAR_SECTION = "TabularSection";
  private static final String ATTRIBUTE = "Attribute";
  private static final String EMPTY_CHILDREN = "<ChildObjects/>";
  private static final Pattern NAME = Pattern.compile("<Name>([^<]*)</Name>");

  /** Проекции эталонов по формату и виду владельца. */
  private static final Map<String, String> PROJECTED = new ConcurrentHashMap<>();

  private GoldenNodes() {
  }

  /**
   * Новый узел верхнего уровня {@code ChildObjects} владельца.
   *
   * @param version формат
   * @param ownerLocal вид владельца: {@code Catalog}, {@code InformationRegister}
   * @param nodeLocal вид узла: {@code Attribute}, {@code TabularSection}, {@code Command}
   * @param ownerName имя владельца: у табличной части оно входит в имена порождаемых типов
   * @param name имя узла; им же подписан узел
   * @param language код языка подписи
   * @param uuidSeed зерно идентификаторов узла
   * @return разметка узла: первая строка без отступа, вложенные - с отступом от неё
   * @throws IOException если в jar нет эталонов или формат новее канонического
   * @throws IllegalArgumentException если эталона такого узла у вида нет
   */
  static String node(
    SchemaVersion version,
    String ownerLocal,
    String nodeLocal,
    String ownerName,
    String name,
    String language,
    String uuidSeed
  ) throws IOException {
    String golden = projected(version, ownerLocal);
    for (XmlLines.Node child : rootChildren(golden, ownerLocal)) {
      if (!child.name().equals(nodeLocal)) {
        continue;
      }
      String text = golden.substring(child.start(), child.end());
      if (TABULAR_SECTION.equals(nodeLocal) && !text.contains(EMPTY_CHILDREN)) {
        continue;
      }
      return instantiate(golden, dedented(golden, child.start(), child.end()), ownerName, name, language, uuidSeed);
    }
    throw absent(ownerLocal, nodeLocal);
  }

  /**
   * Новый реквизит табличной части владельца.
   *
   * @param version формат
   * @param ownerLocal вид владельца табличной части
   * @param ownerName имя владельца
   * @param name имя реквизита
   * @param language код языка подписи
   * @param uuidSeed зерно идентификаторов узла
   * @return разметка реквизита: первая строка без отступа, вложенные - с отступом от неё
   * @throws IOException если в jar нет эталонов или формат новее канонического
   * @throws IllegalArgumentException если эталона реквизита табличной части у вида нет
   */
  static String tabularAttribute(
    SchemaVersion version,
    String ownerLocal,
    String ownerName,
    String name,
    String language,
    String uuidSeed
  ) throws IOException {
    String golden = projected(version, ownerLocal);
    for (XmlLines.Node child : rootChildren(golden, ownerLocal)) {
      if (!child.name().equals(TABULAR_SECTION)) {
        continue;
      }
      // у реквизита табличной части вложенных узлов нет: первый закрывающий тег - его
      int start = golden.indexOf("<" + ATTRIBUTE + " ", child.start());
      int close = start < 0 ? -1 : golden.indexOf("</" + ATTRIBUTE + ">", start);
      if (start < 0 || close < 0 || close > child.end()) {
        continue;
      }
      int end = close + ATTRIBUTE.length() + 3;
      return instantiate(golden, dedented(golden, start, end), ownerName, name, language, uuidSeed);
    }
    throw absent(ownerLocal, TABULAR_SECTION + "." + ATTRIBUTE);
  }

  private static List<XmlLines.Node> rootChildren(String golden, String ownerLocal) {
    return XmlLines.children(golden, List.of("MetaDataObject", ownerLocal, "ChildObjects"));
  }

  /**
   * Узел без отступа, с которым он стоит в эталоне: вставка выравнивает его под своё место в файле.
   */
  private static String dedented(String xml, int start, int end) {
    String indent = XmlLines.indentAt(xml, start);
    String node = xml.substring(start, end);
    return indent.isEmpty() ? node : node.replace("\n" + indent, "\n");
  }

  /**
   * Имена эталона - владельца и узла - заменяются целым словом за один проход, подписи
   * переводятся на язык конфигурации, UUID получают новые значения от зерна.
   */
  private static String instantiate(
    String golden,
    String node,
    String ownerName,
    String name,
    String language,
    String uuidSeed
  ) {
    Map<String, String> names = Map.of(
      firstName(golden), escapeXml(ownerName),
      firstName(node), escapeXml(name));
    Pattern token = Pattern.compile("(?<![\\p{L}\\p{N}_])("
      + names.keySet().stream().map(Pattern::quote).reduce((a, b) -> a + "|" + b).orElseThrow()
      + ")(?![\\p{L}\\p{N}_])");
    Matcher matcher = token.matcher(node);
    StringBuilder renamed = new StringBuilder(node.length());
    while (matcher.find()) {
      matcher.appendReplacement(renamed, Matcher.quoteReplacement(names.get(matcher.group(1))));
    }
    matcher.appendTail(renamed);
    return DistinctUuidRewrite.remapDeterministic(
      LocalStringElement.retarget(renamed.toString(), language), uuidSeed);
  }

  /** Первое имя в тексте: у объекта - его собственное, у узла - имя узла. */
  private static String firstName(String xml) {
    Matcher matcher = NAME.matcher(xml);
    if (!matcher.find()) {
      throw new IllegalStateException("в эталоне узла нет имени");
    }
    return matcher.group(1);
  }

  private static String projected(SchemaVersion version, String ownerLocal) throws IOException {
    String key = version.name() + "|" + ownerLocal;
    String cached = PROJECTED.get(key);
    if (cached != null) {
      return cached;
    }
    String resource = RESOURCE + ownerLocal + ".xml";
    if (GoldenNodes.class.getClassLoader().getResource(resource) == null) {
      throw absent(ownerLocal, "");
    }
    String projected = GoldenScaffold.projected(resource, version);
    PROJECTED.put(key, projected);
    return projected;
  }

  private static IllegalArgumentException absent(String ownerLocal, String nodeLocal) {
    return new IllegalArgumentException(nodeLocal.isEmpty()
      ? "Нет эталона узлов вида " + ownerLocal
      : "Нет эталона узла " + nodeLocal + " вида " + ownerLocal);
  }

  private static String escapeXml(String value) {
    return value.replace("&", "&amp;")
      .replace("<", "&lt;")
      .replace(">", "&gt;")
      .replace("\"", "&quot;")
      .replace("'", "&apos;");
  }
}
