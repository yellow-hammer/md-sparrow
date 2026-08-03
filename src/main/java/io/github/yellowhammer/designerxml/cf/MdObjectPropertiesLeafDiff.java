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
      || !namedListSameStructure(baseline.tabularSections, incoming.tabularSections)
      || !namedListSameStructure(baseline.enumValues, incoming.enumValues)
      || !namedListSameStructure(baseline.dimensions, incoming.dimensions)
      || !namedListSameStructure(baseline.resources, incoming.resources)) {
      return List.of();
    }
    if (!MdObjectPropertiesDiff.listStringEquals(baseline.nestedSubsystems, incoming.nestedSubsystems)) {
      return List.of();
    }
    String kind = incoming.kind;
    if (kind == null) {
      return List.of();
    }
    List<GranularPatchChange> out = new ArrayList<>(kindPropertyChanges(kind, baseline, incoming));
    // Синонимы и комментарии дочерних узлов собираем один раз на все виды: иначе одна правка
    // попадала бы в список дважды и вторая резала бы уже изменённый XML по старым смещениям.
    appendNamedChildSynonymComment("Attribute", baseline.attributes, incoming.attributes, out);
    appendNamedChildSynonymComment("TabularSection", baseline.tabularSections, incoming.tabularSections, out);
    return out;
  }

  private static List<GranularPatchChange> kindPropertyChanges(
    String kind,
    MdObjectPropertiesDto baseline,
    MdObjectPropertiesDto incoming) {
    return switch (kind) {
      case "subsystem" -> subsystemPropertyChanges(baseline, incoming);
      case "catalog" -> catalogPropertyChanges(baseline, incoming);
      case "document" -> documentPropertyChanges(baseline, incoming);
      case "exchangePlan" -> exchangePlanPropertyChanges(baseline, incoming);
      case "enum" -> enumPropertyChanges(baseline, incoming);
      case "constant" -> constantPropertyChanges(baseline, incoming);
      case "commonModule" -> commonModulePropertyChanges(baseline, incoming);
      case "sessionParameter" -> simpleKindChanges(baseline, incoming,
        (b, i, out) -> MdSimplePropertiesGranularSerial.appendSessionParameterScalarChanges(
          b.sessionParameter, i.sessionParameter, out),
        baseline.sessionParameter != null && incoming.sessionParameter != null);
      case "documentNumerator" -> simpleKindChanges(baseline, incoming,
        (b, i, out) -> MdSimplePropertiesGranularSerial.appendDocumentNumeratorScalarChanges(
          b.documentNumerator, i.documentNumerator, out),
        baseline.documentNumerator != null && incoming.documentNumerator != null);
      case "eventSubscription" -> simpleKindChanges(baseline, incoming,
        (b, i, out) -> MdSimplePropertiesGranularSerial.appendEventSubscriptionScalarChanges(
          b.eventSubscription, i.eventSubscription, out),
        baseline.eventSubscription != null && incoming.eventSubscription != null);
      case "scheduledJob" -> simpleKindChanges(baseline, incoming,
        (b, i, out) -> MdSimplePropertiesGranularSerial.appendScheduledJobScalarChanges(
          b.scheduledJob, i.scheduledJob, out),
        baseline.scheduledJob != null && incoming.scheduledJob != null);
      case "commonCommand" -> simpleKindChanges(baseline, incoming,
        (b, i, out) -> MdSimplePropertiesGranularSerial.appendCommonCommandScalarChanges(
          b.commonCommand, i.commonCommand, out),
        baseline.commonCommand != null && incoming.commonCommand != null);
      case "informationRegister", "accumulationRegister" -> registerPropertyChanges(baseline, incoming);
      case "report", "dataProcessor" -> reportPropertyChanges(baseline, incoming);
      case "documentJournal" -> documentJournalPropertyChanges(baseline, incoming);
      case "chartOfCharacteristicTypes" -> chartOfCharacteristicTypesPropertyChanges(baseline, incoming);
      case "task" -> taskPropertyChanges(baseline, incoming);
      case "businessProcess" -> businessProcessPropertyChanges(baseline, incoming);
      case "chartOfAccounts" -> chartOfAccountsPropertyChanges(baseline, incoming);
      case "chartOfCalculationTypes" -> chartOfCalculationTypesPropertyChanges(baseline, incoming);
      case "commonAttribute" -> simpleKindChanges(baseline, incoming,
        (b, i, out) -> MdSimplePropertiesGranularSerial.appendCommonAttributeScalarChanges(
          b.commonAttribute, i.commonAttribute, out),
        baseline.commonAttribute != null && incoming.commonAttribute != null);
      case "commonPicture" -> simpleKindChanges(baseline, incoming,
        (b, i, out) -> MdSimplePropertiesGranularSerial.appendCommonPictureScalarChanges(
          b.commonPicture, i.commonPicture, out),
        baseline.commonPicture != null && incoming.commonPicture != null);
      case "role" -> simpleKindChanges(baseline, incoming,
        (b, i, out) -> MdSimplePropertiesGranularSerial.appendRoleScalarChanges(b.role, i.role, out),
        baseline.role != null && incoming.role != null);
      case "externalDataSource" -> simpleKindChanges(baseline, incoming,
        (b, i, out) -> MdSimplePropertiesGranularSerial.appendExternalDataSourceScalarChanges(
          b.externalDataSource, i.externalDataSource, out),
        baseline.externalDataSource != null && incoming.externalDataSource != null);
      default -> List.of();
    };
  }

  private static List<GranularPatchChange> reportPropertyChanges(
    MdObjectPropertiesDto baseline,
    MdObjectPropertiesDto incoming) {
    if (baseline.report == null || incoming.report == null) {
      return docLikePropertyChanges(baseline, incoming);
    }
    List<GranularPatchChange> out = docLikePropertyChanges(baseline, incoming);
    MdSimplePropertiesGranularSerial.appendReportScalarChanges(baseline.report, incoming.report, out);
    return out;
  }

  private static List<GranularPatchChange> chartOfCalculationTypesPropertyChanges(
    MdObjectPropertiesDto baseline,
    MdObjectPropertiesDto incoming) {
    if (baseline.chartOfCalculationTypes == null || incoming.chartOfCalculationTypes == null) {
      return docLikePropertyChanges(baseline, incoming);
    }
    List<GranularPatchChange> out = docLikePropertyChanges(baseline, incoming);
    MdSimplePropertiesGranularSerial.appendChartOfCalculationTypesScalarChanges(
      baseline.chartOfCalculationTypes, incoming.chartOfCalculationTypes, out);
    return out;
  }

  private static List<GranularPatchChange> chartOfAccountsPropertyChanges(
    MdObjectPropertiesDto baseline,
    MdObjectPropertiesDto incoming) {
    if (baseline.chartOfAccounts == null || incoming.chartOfAccounts == null) {
      return docLikePropertyChanges(baseline, incoming);
    }
    List<GranularPatchChange> out = docLikePropertyChanges(baseline, incoming);
    MdSimplePropertiesGranularSerial.appendChartOfAccountsScalarChanges(
      baseline.chartOfAccounts, incoming.chartOfAccounts, out);
    return out;
  }

  private static List<GranularPatchChange> businessProcessPropertyChanges(
    MdObjectPropertiesDto baseline,
    MdObjectPropertiesDto incoming) {
    if (baseline.businessProcess == null || incoming.businessProcess == null) {
      return docLikePropertyChanges(baseline, incoming);
    }
    List<GranularPatchChange> out = docLikePropertyChanges(baseline, incoming);
    MdSimplePropertiesGranularSerial.appendBusinessProcessScalarChanges(
      baseline.businessProcess, incoming.businessProcess, out);
    return out;
  }

  private static List<GranularPatchChange> taskPropertyChanges(
    MdObjectPropertiesDto baseline,
    MdObjectPropertiesDto incoming) {
    if (baseline.task == null || incoming.task == null) {
      return docLikePropertyChanges(baseline, incoming);
    }
    List<GranularPatchChange> out = docLikePropertyChanges(baseline, incoming);
    MdSimplePropertiesGranularSerial.appendTaskScalarChanges(baseline.task, incoming.task, out);
    return out;
  }

  private static List<GranularPatchChange> chartOfCharacteristicTypesPropertyChanges(
    MdObjectPropertiesDto baseline,
    MdObjectPropertiesDto incoming) {
    if (baseline.chartOfCharacteristicTypes == null || incoming.chartOfCharacteristicTypes == null) {
      return docLikePropertyChanges(baseline, incoming);
    }
    List<GranularPatchChange> out = docLikePropertyChanges(baseline, incoming);
    MdSimplePropertiesGranularSerial.appendChartOfCharacteristicTypesScalarChanges(
      baseline.chartOfCharacteristicTypes, incoming.chartOfCharacteristicTypes, out);
    return out;
  }

  private static List<GranularPatchChange> exchangePlanPropertyChanges(
    MdObjectPropertiesDto baseline,
    MdObjectPropertiesDto incoming) {
    if (baseline.exchangePlan == null || incoming.exchangePlan == null) {
      return docLikePropertyChanges(baseline, incoming);
    }
    List<GranularPatchChange> out = docLikePropertyChanges(baseline, incoming);
    MdSimplePropertiesGranularSerial.appendExchangePlanScalarChanges(
      baseline.exchangePlan, incoming.exchangePlan, out);
    return out;
  }

  private static List<GranularPatchChange> documentJournalPropertyChanges(
    MdObjectPropertiesDto baseline,
    MdObjectPropertiesDto incoming) {
    if (baseline.documentJournal == null || incoming.documentJournal == null) {
      return docLikePropertyChanges(baseline, incoming);
    }
    List<GranularPatchChange> out = docLikePropertyChanges(baseline, incoming);
    MdSimplePropertiesGranularSerial.appendDocumentJournalScalarChanges(
      baseline.documentJournal, incoming.documentJournal, out);
    return out;
  }

  private static List<GranularPatchChange> enumPropertyChanges(
    MdObjectPropertiesDto baseline,
    MdObjectPropertiesDto incoming) {
    if (baseline.enumeration == null || incoming.enumeration == null) {
      return docLikePropertyChanges(baseline, incoming);
    }
    List<GranularPatchChange> out = docLikePropertyChanges(baseline, incoming);
    MdSimplePropertiesGranularSerial.appendEnumScalarChanges(baseline.enumeration, incoming.enumeration, out);
    appendNamedChildSynonymComment("EnumValue", baseline.enumValues, incoming.enumValues, out);
    return out;
  }

  private static List<GranularPatchChange> constantPropertyChanges(
    MdObjectPropertiesDto baseline,
    MdObjectPropertiesDto incoming) {
    if (baseline.constant == null || incoming.constant == null) {
      return docLikePropertyChanges(baseline, incoming);
    }
    List<GranularPatchChange> out = docLikePropertyChanges(baseline, incoming);
    MdSimplePropertiesGranularSerial.appendConstantScalarChanges(baseline.constant, incoming.constant, out);
    return out;
  }

  private static List<GranularPatchChange> commonModulePropertyChanges(
    MdObjectPropertiesDto baseline,
    MdObjectPropertiesDto incoming) {
    if (baseline.commonModule == null || incoming.commonModule == null) {
      return docLikePropertyChanges(baseline, incoming);
    }
    List<GranularPatchChange> out = docLikePropertyChanges(baseline, incoming);
    MdSimplePropertiesGranularSerial.appendCommonModuleScalarChanges(
      baseline.commonModule, incoming.commonModule, out);
    return out;
  }

  private static List<GranularPatchChange> registerPropertyChanges(
    MdObjectPropertiesDto baseline,
    MdObjectPropertiesDto incoming) {
    if (baseline.register == null || incoming.register == null) {
      return docLikePropertyChanges(baseline, incoming);
    }
    List<GranularPatchChange> out = docLikePropertyChanges(baseline, incoming);
    MdSimplePropertiesGranularSerial.appendRegisterScalarChanges(baseline.register, incoming.register, out);
    appendNamedChildSynonymComment("Dimension", baseline.dimensions, incoming.dimensions, out);
    appendNamedChildSynonymComment("Resource", baseline.resources, incoming.resources, out);
    return out;
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
      if (!MdFlatDtoSupport.equalsFlat(x.type, y.type, false)) {
        out.add(GranularPatchChange.namedChild(
          childContainerLocal,
          y.name,
          "Type",
          MdTypeDescriptionSerial.typeElement("Type", y.type)));
      }
    }
  }

  private static List<GranularPatchChange> documentPropertyChanges(
    MdObjectPropertiesDto baseline,
    MdObjectPropertiesDto incoming) {
    if (incoming.document == null) {
      return docLikePropertyChanges(baseline, incoming);
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
    if (baseline.document != null) {
      MdDocumentPropertiesGranularSerial.appendDocumentScalarChanges(baseline.document, incoming.document, out);
    }
    return out;
  }

  /** Изменения вида с плоским блоком свойств: общая часть плюс скаляры блока. */
  private interface KindScalarChanges {
    void append(
      MdObjectPropertiesDto baseline,
      MdObjectPropertiesDto incoming,
      List<GranularPatchChange> out);
  }

  private static List<GranularPatchChange> simpleKindChanges(
    MdObjectPropertiesDto baseline,
    MdObjectPropertiesDto incoming,
    KindScalarChanges scalars,
    boolean hasBlock) {
    List<GranularPatchChange> out = docLikePropertyChanges(baseline, incoming);
    if (hasBlock) {
      scalars.append(baseline, incoming, out);
    }
    return out;
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
    return out;
  }
}
