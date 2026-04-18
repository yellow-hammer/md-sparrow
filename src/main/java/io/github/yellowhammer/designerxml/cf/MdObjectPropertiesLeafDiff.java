/*
 * This file is a part of md-sparrow.
 *
 * Copyright (c) 2026
 * Ivan Karlo <i.karlo@outlook.com> and contributors
 *
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package io.github.yellowhammer.designerxml.cf;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Список изменённых «листьев» DTO для гранулярной записи XML без пересборки всего {@code Properties}.
 */
public final class MdObjectPropertiesLeafDiff {

  /**
   * Одна точечная замена XML.
   *
   * @param namedChildContainerLocal если не {@code null} — {@code Attribute} или {@code TabularSection};
   *   иначе замена прямого дочернего элемента {@code Properties} объекта ({@code Catalog} / …).
   * @param namedChildObjectInternalName имя из {@code Name} (реквизит / ТЧ), только для именованного дочернего объекта
   */
  public record GranularPatchChange(
    String mdElementLocalName,
    String replacementElementXml,
    String namedChildContainerLocal,
    String namedChildObjectInternalName) {

    public static GranularPatchChange objectProperty(String mdElementLocalName, String replacementElementXml) {
      return new GranularPatchChange(mdElementLocalName, replacementElementXml, null, null);
    }

    public static GranularPatchChange namedChild(
      String childContainerLocal,
      String objectInternalName,
      String mdElementLocalName,
      String replacementElementXml) {
      return new GranularPatchChange(mdElementLocalName, replacementElementXml, childContainerLocal, objectInternalName);
    }

    public boolean isNamedChildObject() {
      return namedChildContainerLocal != null;
    }
  }

  private MdObjectPropertiesLeafDiff() {
  }

  /**
   * Возвращает список точечных замен (корень {@code Properties}, опционально синоним/комментарий реквизитов/ТЧ).
   * Пусто, если нужна полная пересборка региона (смена состава/имён реквизитов, вложенные подсистемы и т.д.).
   */
  public static List<GranularPatchChange> computePropertyChanges(
    MdObjectPropertiesDto baseline,
    MdObjectPropertiesDto incoming) {
    if (baseline == null || incoming == null) {
      return List.of();
    }
    if (!Objects.equals(baseline.kind, incoming.kind)
      || !Objects.equals(baseline.internalName, incoming.internalName)) {
      return List.of();
    }
    if (!namedListSameStructure(baseline.attributes, incoming.attributes)
      || !namedListSameStructure(baseline.tabularSections, incoming.tabularSections)) {
      return List.of();
    }
    if (!MdObjectPropertiesDiff.listStringEquals(baseline.nestedSubsystems, incoming.nestedSubsystems)) {
      return List.of();
    }
    String kind = incoming.kind;
    if (kind == null) {
      return List.of();
    }
    return switch (kind) {
      case "subsystem" -> subsystemPropertyChanges(baseline, incoming);
      case "catalog" -> catalogPropertyChanges(baseline, incoming);
      case "document", "exchangePlan" -> docLikePropertyChanges(baseline, incoming);
      case "constant", "enum", "report", "dataProcessor", "task", "chartOfAccounts",
           "chartOfCharacteristicTypes", "chartOfCalculationTypes", "commonModule",
           "sessionParameter", "commonAttribute", "commonPicture", "documentNumerator",
           "externalDataSource", "role" -> docLikePropertyChanges(baseline, incoming);
      default -> List.of();
    };
  }

  /** Тот же состав имён и порядок; допускаются отличия synonym/comment. */
  private static boolean namedListSameStructure(
    java.util.List<MdNamedPropertyDto> a,
    java.util.List<MdNamedPropertyDto> b) {
    if (a == null) {
      a = new ArrayList<>();
    }
    if (b == null) {
      b = new ArrayList<>();
    }
    if (a.size() != b.size()) {
      return false;
    }
    for (int i = 0; i < a.size(); i++) {
      if (!Objects.equals(a.get(i).name, b.get(i).name)) {
        return false;
      }
    }
    return true;
  }

  private static void appendNamedChildSynonymComment(
    String childContainerLocal,
    java.util.List<MdNamedPropertyDto> b,
    java.util.List<MdNamedPropertyDto> i,
    List<GranularPatchChange> out) {
    if (b == null) {
      b = new ArrayList<>();
    }
    if (i == null) {
      i = new ArrayList<>();
    }
    for (int idx = 0; idx < b.size(); idx++) {
      MdNamedPropertyDto x = b.get(idx);
      MdNamedPropertyDto y = i.get(idx);
      if (!Objects.equals(x.synonymRu, y.synonymRu)) {
        out.add(GranularPatchChange.namedChild(
          childContainerLocal,
          y.name,
          "Synonym",
          MdCatalogPropertiesGranularSerial.synonymElementRu(y.synonymRu)));
      }
      if (!Objects.equals(x.comment, y.comment)) {
        out.add(GranularPatchChange.namedChild(
          childContainerLocal,
          y.name,
          "Comment",
          MdCatalogPropertiesGranularSerial.commentElement(y.comment)));
      }
    }
  }

  private static List<GranularPatchChange> docLikePropertyChanges(
    MdObjectPropertiesDto baseline,
    MdObjectPropertiesDto incoming) {
    List<GranularPatchChange> out = new ArrayList<>();
    if (!Objects.equals(baseline.synonymRu, incoming.synonymRu)) {
      out.add(GranularPatchChange.objectProperty(
        "Synonym",
        MdCatalogPropertiesGranularSerial.synonymElementRu(incoming.synonymRu)));
    }
    if (!Objects.equals(baseline.comment, incoming.comment)) {
      out.add(GranularPatchChange.objectProperty(
        "Comment",
        MdCatalogPropertiesGranularSerial.commentElement(incoming.comment)));
    }
    appendNamedChildSynonymComment("Attribute", baseline.attributes, incoming.attributes, out);
    appendNamedChildSynonymComment("TabularSection", baseline.tabularSections, incoming.tabularSections, out);
    return out;
  }

  private static List<GranularPatchChange> subsystemPropertyChanges(
    MdObjectPropertiesDto baseline,
    MdObjectPropertiesDto incoming) {
    List<GranularPatchChange> out = new ArrayList<>();
    if (!Objects.equals(baseline.synonymRu, incoming.synonymRu)) {
      out.add(GranularPatchChange.objectProperty(
        "Synonym",
        MdCatalogPropertiesGranularSerial.synonymElementRu(incoming.synonymRu)));
    }
    if (!Objects.equals(baseline.comment, incoming.comment)) {
      out.add(GranularPatchChange.objectProperty(
        "Comment",
        MdCatalogPropertiesGranularSerial.commentElement(incoming.comment)));
    }
    if (!MdObjectPropertiesDiff.listStringEquals(baseline.contentRefs, incoming.contentRefs)) {
      out.add(GranularPatchChange.objectProperty(
        "Content",
        MdCatalogPropertiesGranularSerial.mdListTypeRefsElement("Content", incoming.contentRefs)));
    }
    return out;
  }

  private static List<GranularPatchChange> catalogPropertyChanges(
    MdObjectPropertiesDto baseline,
    MdObjectPropertiesDto incoming) {
    MdCatalogPropertiesDto b = baseline.catalog;
    MdCatalogPropertiesDto i = incoming.catalog;
    if (i == null) {
      return List.of();
    }
    List<GranularPatchChange> out = new ArrayList<>();
    if (!Objects.equals(baseline.synonymRu, incoming.synonymRu)) {
      out.add(GranularPatchChange.objectProperty(
        "Synonym",
        MdCatalogPropertiesGranularSerial.synonymElementRu(incoming.synonymRu)));
    }
    if (!Objects.equals(baseline.comment, incoming.comment)) {
      out.add(GranularPatchChange.objectProperty(
        "Comment",
        MdCatalogPropertiesGranularSerial.commentElement(incoming.comment)));
    }
    if (b != null) {
      MdCatalogPropertiesGranularSerial.appendCatalogScalarChanges(b, i, out);
    }
    appendNamedChildSynonymComment("Attribute", baseline.attributes, incoming.attributes, out);
    appendNamedChildSynonymComment("TabularSection", baseline.tabularSections, incoming.tabularSections, out);
    return out;
  }
}
