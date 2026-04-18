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

import jakarta.xml.bind.JAXBException;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

import javax.xml.stream.XMLStreamException;

/**
 * Точечная замена прямых дочерних элементов {@code Properties} в исходной UTF-8 строке без пересборки всего блока.
 */
public final class MdObjectPropertiesGranularPatch {

  private static final Pattern INTER_TAG_WS = Pattern.compile(">\\s+<");

  private MdObjectPropertiesGranularPatch() {
  }

  /**
   * @param containerLocal без префикса: {@code Catalog}, {@code Document}, {@code ExchangePlan}, {@code Subsystem}
   */
  public static Optional<byte[]> tryApply(
    String xmlUtf8,
    String containerLocal,
    SchemaVersion version,
    MdObjectPropertiesDto baseline,
    MdObjectPropertiesDto incoming) {
    if (baseline == null || incoming == null || containerLocal == null || containerLocal.isEmpty()) {
      return Optional.empty();
    }
    List<MdObjectPropertiesLeafDiff.GranularPatchChange> changes =
      MdObjectPropertiesLeafDiff.computePropertyChanges(baseline, incoming);
    if (changes.isEmpty()) {
      return Optional.empty();
    }
    List<Replacement> reps = new ArrayList<>();
    try {
      for (MdObjectPropertiesLeafDiff.GranularPatchChange ch : changes) {
        MdObjectXmlRegions.Region reg;
        if (ch.isNamedChildObject()) {
          reg = MdObjectXmlRegions.findDirectChildOfNamedChildObjectPropertiesRegion(
            xmlUtf8,
            containerLocal,
            ch.namedChildContainerLocal(),
            ch.namedChildObjectInternalName(),
            ch.mdElementLocalName());
        } else {
          reg = MdObjectXmlRegions.findDirectChildOfPropertiesRegion(
            xmlUtf8, containerLocal, ch.mdElementLocalName());
        }
        if (!reg.isValid()) {
          if (ch.isNamedChildObject()) {
            return Optional.empty();
          }
          MdObjectXmlRegions.Region propertiesRegion = MdObjectXmlRegions.findPropertiesRegion(xmlUtf8, containerLocal);
          if (!propertiesRegion.isValid()) {
            return Optional.empty();
          }
          int insertPos = propertiesClosingTagStart(xmlUtf8, propertiesRegion);
          if (insertPos < 0) {
            return Optional.empty();
          }
          String insertion = formatInsertionBeforePropertiesClose(
            xmlUtf8,
            insertPos,
            ch.replacementElementXml());
          reps.add(new Replacement(insertPos, insertPos, insertion));
          continue;
        }
        String replacement = formatReplacementPreservingIndent(xmlUtf8, reg, ch.replacementElementXml());
        reps.add(new Replacement(reg.start(), reg.end(), replacement));
      }
    } catch (XMLStreamException e) {
      return Optional.empty();
    }
    reps.sort(Comparator.comparingInt(Replacement::start).reversed());
    StringBuilder sb = new StringBuilder(xmlUtf8);
    for (Replacement r : reps) {
      sb.replace(r.start, r.end, r.text);
    }
    byte[] bytes = sb.toString().getBytes(StandardCharsets.UTF_8);
    try {
      MdObjectPropertiesDto verified = MdObjectPropertiesEdit.readDto(bytes, version);
      if (MdObjectPropertiesDiff.equalsDto(verified, incoming, true)
        || MdObjectPropertiesDiff.equalsDtoLenientJson(verified, incoming)) {
        return Optional.of(bytes);
      }
    } catch (JAXBException e) {
      return Optional.empty();
    }
    return Optional.empty();
  }

  /**
   * Возвращает текст первой причины, почему точечная запись не может быть применена.
   */
  public static Optional<String> describeFirstUnpatchableChange(
    String xmlUtf8,
    String containerLocal,
    MdObjectPropertiesDto baseline,
    MdObjectPropertiesDto incoming) {
    if (baseline == null || incoming == null || containerLocal == null || containerLocal.isEmpty()) {
      return Optional.of("некорректные входные данные для гранулярной записи");
    }
    List<MdObjectPropertiesLeafDiff.GranularPatchChange> changes =
      MdObjectPropertiesLeafDiff.computePropertyChanges(baseline, incoming);
    if (changes.isEmpty()) {
      return Optional.empty();
    }
    try {
      for (MdObjectPropertiesLeafDiff.GranularPatchChange ch : changes) {
        MdObjectXmlRegions.Region reg;
        if (ch.isNamedChildObject()) {
          reg = MdObjectXmlRegions.findDirectChildOfNamedChildObjectPropertiesRegion(
            xmlUtf8,
            containerLocal,
            ch.namedChildContainerLocal(),
            ch.namedChildObjectInternalName(),
            ch.mdElementLocalName());
          if (!reg.isValid()) {
            return Optional.of("не найден узел "
              + ch.namedChildContainerLocal()
              + "/Properties/"
              + ch.mdElementLocalName()
              + " для объекта "
              + ch.namedChildObjectInternalName());
          }
        } else {
          reg = MdObjectXmlRegions.findDirectChildOfPropertiesRegion(
            xmlUtf8, containerLocal, ch.mdElementLocalName());
          if (!reg.isValid()) {
            return Optional.of("не найден узел "
              + containerLocal
              + "/Properties/"
              + ch.mdElementLocalName());
          }
        }
      }
    } catch (XMLStreamException e) {
      return Optional.of("ошибка разбора XML: " + e.getMessage());
    }
    return Optional.empty();
  }

  /** Локальное имя корневого элемента объекта по полю {@link MdObjectPropertiesDto#kind}. */
  public static String containerLocalForKind(String kind) {
    if (kind == null) {
      return "";
    }
    return switch (kind) {
      case "catalog" -> "Catalog";
      case "constant" -> "Constant";
      case "enum" -> "Enum";
      case "document" -> "Document";
      case "report" -> "Report";
      case "dataProcessor" -> "DataProcessor";
      case "task" -> "Task";
      case "chartOfAccounts" -> "ChartOfAccounts";
      case "chartOfCharacteristicTypes" -> "ChartOfCharacteristicTypes";
      case "chartOfCalculationTypes" -> "ChartOfCalculationTypes";
      case "commonModule" -> "CommonModule";
      case "exchangePlan" -> "ExchangePlan";
      case "subsystem" -> "Subsystem";
      case "sessionParameter" -> "SessionParameter";
      case "commonAttribute" -> "CommonAttribute";
      case "commonPicture" -> "CommonPicture";
      case "documentNumerator" -> "DocumentNumerator";
      case "externalDataSource" -> "ExternalDataSource";
      case "role" -> "Role";
      default -> "";
    };
  }

  private record Replacement(int start, int end, String text) {
  }

  private static String formatReplacementPreservingIndent(
    String xmlUtf8,
    MdObjectXmlRegions.Region reg,
    String replacementElementXml) {
    if (replacementElementXml == null
      || replacementElementXml.isEmpty()
      || !replacementElementXml.contains("><")) {
      return replacementElementXml;
    }
    String indent = currentLineIndent(xmlUtf8, reg.start());
    String compact = INTER_TAG_WS.matcher(replacementElementXml.trim()).replaceAll("><");
    if (!compact.contains("><")) {
      return replacementElementXml;
    }
    String expanded = compact.replace("><", ">\n<");
    String[] lines = expanded.split("\n");
    StringBuilder out = new StringBuilder(expanded.length() + lines.length * 2);
    int depth = 0;
    for (int i = 0; i < lines.length; i++) {
      String line = lines[i].trim();
      if (line.startsWith("</")) {
        depth = Math.max(0, depth - 1);
      }
      if (i > 0) {
        out.append('\n');
        out.append(indent);
        for (int j = 0; j < depth; j++) {
          out.append('\t');
        }
      }
      out.append(line);
      if (isOpeningTagWithoutInlineClose(line)) {
        depth++;
      }
    }
    return out.toString();
  }

  private static boolean isOpeningTagWithoutInlineClose(String line) {
    return line.startsWith("<")
      && !line.startsWith("</")
      && !line.endsWith("/>")
      && !line.contains("</");
  }

  private static String currentLineIndent(String xmlUtf8, int startOffset) {
    int from = startOffset - 1;
    while (from >= 0 && xmlUtf8.charAt(from) != '\n' && xmlUtf8.charAt(from) != '\r') {
      from--;
    }
    from++;
    int i = from;
    while (i < xmlUtf8.length()) {
      char c = xmlUtf8.charAt(i);
      if (c != ' ' && c != '\t') {
        break;
      }
      i++;
    }
    return xmlUtf8.substring(from, i);
  }

  private static int propertiesClosingTagStart(String xmlUtf8, MdObjectXmlRegions.Region propertiesRegion) {
    return xmlUtf8.lastIndexOf("</", propertiesRegion.end() - 1);
  }

  private static String formatInsertionBeforePropertiesClose(
    String xmlUtf8,
    int insertPos,
    String replacementElementXml) {
    String parentIndent = currentLineIndent(xmlUtf8, insertPos);
    String childIndent = parentIndent + "\t";
    String compact = INTER_TAG_WS.matcher(replacementElementXml.trim()).replaceAll("><");
    String expanded = compact.replace("><", ">\n<");
    String[] lines = expanded.split("\n");
    StringBuilder out = new StringBuilder(expanded.length() + childIndent.length() * lines.length + 2);
    out.append('\n');
    for (int i = 0; i < lines.length; i++) {
      if (i > 0) {
        out.append('\n');
      }
      out.append(childIndent).append(lines[i].trim());
    }
    return out.toString();
  }
}
