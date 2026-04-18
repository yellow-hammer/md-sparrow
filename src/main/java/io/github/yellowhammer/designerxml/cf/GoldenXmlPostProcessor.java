/*
 * This file is a part of md-sparrow.
 *
 * Copyright (c) 2026
 * Ivan Karlo <i.karlo@outlook.com> and contributors
 *
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package io.github.yellowhammer.designerxml.cf;

import io.github.yellowhammer.designerxml.SchemaVersion;

import java.util.regex.Pattern;

/**
 * Единая пост-обработка XML для add-путей golden writer.
 */
final class GoldenXmlPostProcessor {
  private static final String META_DATA_OBJECT_START_V220 =
    "<MetaDataObject xmlns=\"http://v8.1c.ru/8.3/MDClasses\""
      + " xmlns:app=\"http://v8.1c.ru/8.2/managed-application/core\""
      + " xmlns:cfg=\"http://v8.1c.ru/8.1/data/enterprise/current-config\""
      + " xmlns:cmi=\"http://v8.1c.ru/8.2/managed-application/cmi\""
      + " xmlns:ent=\"http://v8.1c.ru/8.1/data/enterprise\""
      + " xmlns:lf=\"http://v8.1c.ru/8.2/managed-application/logform\""
      + " xmlns:style=\"http://v8.1c.ru/8.1/data/ui/style\""
      + " xmlns:sys=\"http://v8.1c.ru/8.1/data/ui/fonts/system\""
      + " xmlns:v8=\"http://v8.1c.ru/8.1/data/core\""
      + " xmlns:v8ui=\"http://v8.1c.ru/8.1/data/ui\""
      + " xmlns:web=\"http://v8.1c.ru/8.1/data/ui/colors/web\""
      + " xmlns:win=\"http://v8.1c.ru/8.1/data/ui/colors/windows\""
      + " xmlns:xen=\"http://v8.1c.ru/8.3/xcf/enums\""
      + " xmlns:xpr=\"http://v8.1c.ru/8.3/xcf/predef\""
      + " xmlns:xr=\"http://v8.1c.ru/8.3/xcf/readable\""
      + " xmlns:xs=\"http://www.w3.org/2001/XMLSchema\""
      + " xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\""
      + " version=\"2.20\">";
  private static final String META_DATA_OBJECT_START_V221 =
    META_DATA_OBJECT_START_V220.replace("version=\"2.20\"", "version=\"2.21\"");

  private static final Pattern GENERATED_TYPE_ATTR_ORDER = Pattern.compile(
    "<xr:GeneratedType\\s+category=\"([^\"]+)\"\\s+name=\"([^\"]+)\">");
  private static final Pattern EMPTY_PAIR_TAG = Pattern.compile("<([A-Za-z_][A-Za-z0-9_:.\\-]*)></\\1>");
  private static final Pattern LEADING_FOUR_SPACES = Pattern.compile("(?m)^( {4})+");

  private GoldenXmlPostProcessor() {
  }

  static String normalizeMetaDataObjectXml(String xml, SchemaVersion version) {
    String normalizedLineEndings = xml.replace("\r\n", "\n")
      .replace('\r', '\n')
      .replace(" standalone=\"yes\"", "");
    String normalizedRoot = normalizeMetaDataObjectStart(normalizedLineEndings, version);
    String reorderedGeneratedType = GENERATED_TYPE_ATTR_ORDER
      .matcher(normalizedRoot)
      .replaceAll("<xr:GeneratedType name=\"$2\" category=\"$1\">");
    String selfClosedEmptyTags = EMPTY_PAIR_TAG.matcher(reorderedGeneratedType).replaceAll("<$1/>");
    return LEADING_FOUR_SPACES.matcher(selfClosedEmptyTags).replaceAll(matchResult -> {
      int groups = matchResult.group().length() / 4;
      return "\t".repeat(groups);
    });
  }

  private static String normalizeMetaDataObjectStart(String xml, SchemaVersion version) {
    int start = xml.indexOf("<MetaDataObject");
    if (start < 0) {
      return xml;
    }
    int end = xml.indexOf('>', start);
    if (end < 0) {
      return xml;
    }

    String replacement = switch (version) {
      case V2_20 -> META_DATA_OBJECT_START_V220;
      case V2_21 -> META_DATA_OBJECT_START_V221;
    };
    return xml.substring(0, start) + replacement + xml.substring(end + 1);
  }
}
