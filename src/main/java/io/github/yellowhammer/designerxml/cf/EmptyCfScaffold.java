/*
 * This file is a part of md-sparrow.
 *
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package io.github.yellowhammer.designerxml.cf;

import io.github.yellowhammer.designerxml.SchemaVersion;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Инициализация каталога пустой выгрузки конфигурации из эталона (golden) нужной версии — для любого формата
 * (см. {@link GoldenScaffold}). Configuration.xml = эталон с обрезанным до Языка ChildObjects + параметризация;
 * Languages/Русский.xml — из эталона.
 */
public final class EmptyCfScaffold {

  private EmptyCfScaffold() {
  }

  public static void writeEmptyTree(
    Path targetCfRoot,
    String configurationName,
    String synonymRu,
    String vendor,
    String appVersion,
    SchemaVersion version) throws IOException {
    Objects.requireNonNull(targetCfRoot, "targetCfRoot");
    CatalogNameConstraints.check(configurationName);

    CfTreeDelete.deleteAllContents(targetCfRoot);
    Files.createDirectories(targetCfRoot);

    Path langDir = targetCfRoot.resolve(CfLayout.LANGUAGES_DIR);
    Files.createDirectories(langDir);
    Files.writeString(
      langDir.resolve(CfLayout.RUSSIAN_LANGUAGE_NAME + ".xml"),
      GoldenScaffold.generateRussianLanguage(version),
      StandardCharsets.UTF_8);

    String cfg = GoldenScaffold.generateEmptyConfiguration(configurationName, version);
    cfg = applyOptions(cfg, synonymRu, vendor, appVersion);
    Files.writeString(targetCfRoot.resolve(CfLayout.CONFIGURATION_XML), cfg, StandardCharsets.UTF_8);
  }

  private static String applyOptions(String xml, String synonymRu, String vendor, String appVersion) {
    if (vendor != null && !vendor.isEmpty()) {
      xml = setLeaf(xml, "Vendor", vendor);
    }
    if (appVersion != null && !appVersion.isEmpty()) {
      xml = setLeaf(xml, "Version", appVersion);
    }
    if (synonymRu != null && !synonymRu.isEmpty()) {
      xml = setSynonym(xml, synonymRu);
    }
    return xml;
  }

  private static String setLeaf(String xml, String tag, String value) {
    String selfClose = "<" + tag + "/>";
    int i = xml.indexOf(selfClose);
    if (i >= 0) {
      return xml.substring(0, i) + "<" + tag + ">" + escape(value) + "</" + tag + ">"
        + xml.substring(i + selfClose.length());
    }
    Matcher m = Pattern.compile("(?s)<" + tag + ">.*?</" + tag + ">").matcher(xml);
    if (m.find()) {
      return xml.substring(0, m.start()) + "<" + tag + ">" + escape(value) + "</" + tag + ">" + xml.substring(m.end());
    }
    return xml;
  }

  private static String setSynonym(String xml, String ru) {
    String block = "<Synonym>\n\t\t\t\t<v8:item>\n\t\t\t\t\t<v8:lang>ru</v8:lang>\n\t\t\t\t\t<v8:content>"
      + escape(ru) + "</v8:content>\n\t\t\t\t</v8:item>\n\t\t\t</Synonym>";
    int i = xml.indexOf("<Synonym/>");
    if (i >= 0) {
      return xml.substring(0, i) + block + xml.substring(i + "<Synonym/>".length());
    }
    Matcher m = Pattern.compile("(?s)<Synonym>.*?</Synonym>").matcher(xml);
    if (m.find()) {
      String replaced = m.group().replaceFirst(
        "(<v8:content>).*?(</v8:content>)",
        "$1" + Matcher.quoteReplacement(escape(ru)) + "$2");
      return xml.substring(0, m.start()) + replaced + xml.substring(m.end());
    }
    return xml;
  }

  private static String escape(String s) {
    return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
  }
}
