/*
 * This file is a part of md-sparrow.
 *
 * Copyright (c) 2026
 * Ivan Karlo <i.karlo@outlook.com> and contributors
 *
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package io.github.yellowhammer.designerxml.cf;

import com.ctc.wstx.stax.WstxInputFactory;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Зачистка привязок в формах объекта при удалении и переименовании реквизитов и табличных частей
 * (поведение конфигуратора): элементы формы с {@code DataPath} на удалённый реквизит убираются,
 * при переименовании пути обновляются. Обрабатываются пути через главные реквизиты формы
 * ({@code MainAttribute}): {@code Объект.<Реквизит>}, {@code Список.<Реквизит>} и т. п.
 */
public final class FormDataPathCleanup {

  private static final Pattern MAIN_ATTRIBUTE_NAME = Pattern.compile(
    "<Attribute name=\"([^\"]+)\"[^>]*>(?:(?!</Attribute>).)*?<MainAttribute>true</MainAttribute>",
    Pattern.DOTALL);

  private FormDataPathCleanup() {
  }

  /**
   * Убирает из форм объекта элементы, привязанные к удалённому реквизиту.
   *
   * @param objectXml путь к XML объекта метаданных
   * @param pathTail хвост пути данных: {@code Имя}, {@code ТЧ} или {@code ТЧ.Имя}
   */
  public static void afterChildDelete(Path objectXml, String pathTail) throws IOException {
    for (Path formXml : objectFormXmlFiles(objectXml)) {
      String xml = Files.readString(formXml, StandardCharsets.UTF_8);
      Set<String> targets = targetsFor(xml, pathTail);
      if (targets.isEmpty()) {
        continue;
      }
      String updated;
      try {
        updated = removeBoundItems(xml, targets);
      } catch (XMLStreamException e) {
        throw new IOException("Не удалось разобрать форму " + formXml + ": " + e.getMessage(), e);
      }
      writeIfChangedAndValid(formXml, xml, updated);
    }
  }

  /**
   * Обновляет пути данных в формах объекта при переименовании реквизита или табличной части.
   *
   * @param objectXml путь к XML объекта метаданных
   * @param oldTail старый хвост пути данных ({@code Имя}, {@code ТЧ} или {@code ТЧ.Имя})
   * @param newTail новый хвост пути данных
   */
  public static void afterChildRename(Path objectXml, String oldTail, String newTail) throws IOException {
    for (Path formXml : objectFormXmlFiles(objectXml)) {
      String xml = Files.readString(formXml, StandardCharsets.UTF_8);
      String updated = xml;
      for (String main : mainAttributeNames(xml)) {
        String oldPath = main + "." + oldTail;
        String newPath = main + "." + newTail;
        updated = updated.replace("<DataPath>" + oldPath + "</DataPath>", "<DataPath>" + newPath + "</DataPath>");
        updated = updated.replace("<DataPath>" + oldPath + ".", "<DataPath>" + newPath + ".");
      }
      writeIfChangedAndValid(formXml, xml, updated);
    }
  }

  /** {@code Forms/<Имя>/Ext/Form.xml} рядом с XML объекта. */
  private static List<Path> objectFormXmlFiles(Path objectXml) throws IOException {
    String stem = objectXml.getFileName().toString().replaceFirst("\\.xml$", "");
    Path formsDir = objectXml.resolveSibling(stem).resolve("Forms");
    if (!Files.isDirectory(formsDir)) {
      return List.of();
    }
    List<Path> out = new ArrayList<>();
    try (DirectoryStream<Path> ds = Files.newDirectoryStream(formsDir, Files::isDirectory)) {
      for (Path formDir : ds) {
        Path formXml = formDir.resolve("Ext").resolve("Form.xml");
        if (Files.isRegularFile(formXml)) {
          out.add(formXml);
        }
      }
    }
    out.sort(Comparator.comparing(Path::toString));
    return out;
  }

  private static Set<String> targetsFor(String formXml, String pathTail) {
    Set<String> targets = new LinkedHashSet<>();
    for (String main : mainAttributeNames(formXml)) {
      targets.add(main + "." + pathTail);
    }
    return targets;
  }

  private static List<String> mainAttributeNames(String formXml) {
    List<String> out = new ArrayList<>();
    Matcher m = MAIN_ATTRIBUTE_NAME.matcher(formXml);
    while (m.find()) {
      out.add(m.group(1));
    }
    return out;
  }

  /**
   * Убирает элементы формы, у которых прямой дочерний {@code DataPath} равен цели или начинается с
   * {@code цель.} (колонки таблицы удаляемой ТЧ и т. п.).
   */
  private static String removeBoundItems(String xml, Set<String> targets) throws XMLStreamException {
    List<int[]> removals = boundItemRegions(xml, targets);
    if (removals.isEmpty()) {
      return xml;
    }
    // Вложенные регионы поглощаются внешними.
    removals.sort(Comparator.comparingInt(a -> a[0]));
    List<int[]> flat = new ArrayList<>();
    int lastEnd = -1;
    for (int[] region : removals) {
      if (region[0] >= lastEnd) {
        flat.add(region);
        lastEnd = region[1];
      }
    }
    String out = xml;
    for (int i = flat.size() - 1; i >= 0; i--) {
      out = removeRegionWithLine(out, flat.get(i)[0], flat.get(i)[1]);
    }
    return out;
  }

  private static List<int[]> boundItemRegions(String xml, Set<String> targets) throws XMLStreamException {
    XMLInputFactory f = new WstxInputFactory();
    f.setProperty(XMLInputFactory.IS_NAMESPACE_AWARE, Boolean.TRUE);
    XMLStreamReader r = f.createXMLStreamReader(new StringReader(xml));
    // Элемент стека: [смещение начала тега, признак совпавшего DataPath]
    Deque<int[]> stack = new ArrayDeque<>();
    List<int[]> removals = new ArrayList<>();
    try {
      while (r.hasNext()) {
        int ev = r.next();
        if (ev == XMLStreamConstants.START_ELEMENT) {
          if ("DataPath".equals(r.getLocalName()) && !stack.isEmpty()) {
            String text = r.getElementText().trim();
            if (matchesTarget(text, targets)) {
              stack.peek()[1] = 1;
            }
            continue;
          }
          int start = safeCharOffset(r);
          stack.push(new int[]{start, 0});
        } else if (ev == XMLStreamConstants.END_ELEMENT) {
          if (stack.isEmpty()) {
            continue;
          }
          int[] top = stack.pop();
          if (top[1] == 1 && top[0] >= 0) {
            int endTagStart = safeCharOffset(r);
            int gt = endTagStart >= 0 ? xml.indexOf('>', endTagStart) : -1;
            if (gt >= 0) {
              removals.add(new int[]{top[0], gt + 1});
            }
          }
        }
      }
    } finally {
      r.close();
    }
    return removals;
  }

  private static boolean matchesTarget(String dataPath, Set<String> targets) {
    for (String target : targets) {
      if (dataPath.equals(target) || dataPath.startsWith(target + ".")) {
        return true;
      }
    }
    return false;
  }

  /** Удаляет регион вместе со строкой, если блок занимал свои строки (без остаточных отступов). */
  private static String removeRegionWithLine(String xml, int start, int end) {
    String left = xml.substring(0, start);
    String right = xml.substring(end);
    int cut = left.length();
    while (cut > 0 && (left.charAt(cut - 1) == '\t' || left.charAt(cut - 1) == ' ')) {
      cut--;
    }
    boolean leftAtLineStart = cut == 0 || left.charAt(cut - 1) == '\n';
    if (leftAtLineStart && (right.startsWith("\r\n") || right.startsWith("\n"))) {
      left = left.substring(0, cut);
      right = right.startsWith("\r\n") ? right.substring(2) : right.substring(1);
    }
    return left + right;
  }

  private static void writeIfChangedAndValid(Path formXml, String original, String updated) throws IOException {
    if (updated.equals(original)) {
      return;
    }
    try {
      requireWellFormed(updated);
    } catch (XMLStreamException e) {
      throw new IOException("Зачистка сломала форму " + formXml + ": " + e.getMessage(), e);
    }
    Files.writeString(formXml, updated, StandardCharsets.UTF_8);
  }

  private static void requireWellFormed(String xml) throws XMLStreamException {
    XMLInputFactory f = new WstxInputFactory();
    f.setProperty(XMLInputFactory.IS_NAMESPACE_AWARE, Boolean.TRUE);
    XMLStreamReader r = f.createXMLStreamReader(new StringReader(xml));
    try {
      while (r.hasNext()) {
        r.next();
      }
    } finally {
      r.close();
    }
  }

  private static int safeCharOffset(XMLStreamReader r) {
    javax.xml.stream.Location loc = r.getLocation();
    if (loc == null) {
      return -1;
    }
    return loc.getCharacterOffset();
  }
}
