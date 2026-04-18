/*
 * This file is a part of md-sparrow.
 *
 * Copyright (c) 2026
 * Ivan Karlo <i.karlo@outlook.com> and contributors
 *
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package io.github.yellowhammer.designerxml.cf;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Сравнение DTO свойств объекта метаданных и классификация изменений для точечной записи XML.
 */
public final class MdObjectPropertiesDiff {

  private static final Gson GSON = new GsonBuilder().serializeNulls().create();

  private MdObjectPropertiesDiff() {
  }

  /**
   * Входящий DTO отличается от baseline только полем {@link MdObjectPropertiesDto#comment} (остальное совпадает).
   * Без Gson: JSON round-trip ломал сравнение вложенного {@link MdCatalogPropertiesDto} и срывал точечную запись.
   */
  public static boolean onlyCommentChanged(MdObjectPropertiesDto baseline, MdObjectPropertiesDto incoming) {
    if (baseline == null || incoming == null) {
      return false;
    }
    if (Objects.equals(baseline.comment, incoming.comment)) {
      return false;
    }
    // Для «только комментарий» сравниваем вложенный catalog с lenient XML: webview пересобирает catalog из DOM,
    // textarea могут слегка сместить пробелы/CRLF в standardAttributesXml/characteristicsXml относительно baseline.
    return equalsDtoExceptComment(baseline, incoming, true);
  }

  /**
   * То же, что {@link #equalsDto(MdObjectPropertiesDto, MdObjectPropertiesDto)}, но без сравнения {@code comment}.
   */
  public static boolean equalsDtoExceptComment(MdObjectPropertiesDto a, MdObjectPropertiesDto b) {
    return equalsDtoExceptComment(a, b, false);
  }

  /**
   * То же, что {@link #equalsDtoExceptComment(MdObjectPropertiesDto, MdObjectPropertiesDto)}, с выбором
   * строгости для {@link MdCatalogPropertiesDto#standardAttributesXml} и {@code characteristicsXml}.
   *
   * @param lenientCatalogXmlBlobs как в {@link #equalsDto(MdObjectPropertiesDto, MdObjectPropertiesDto, boolean)}
   */
  public static boolean equalsDtoExceptComment(MdObjectPropertiesDto a, MdObjectPropertiesDto b,
    boolean lenientCatalogXmlBlobs) {
    if (a == b) {
      return true;
    }
    if (a == null || b == null) {
      return false;
    }
    if (!Objects.equals(a.kind, b.kind)
      || !Objects.equals(a.internalName, b.internalName)
      || !Objects.equals(a.synonymRu, b.synonymRu)) {
      return false;
    }
    if (!namedListEquals(a.attributes, b.attributes)) {
      return false;
    }
    if (!namedListEquals(a.tabularSections, b.tabularSections)) {
      return false;
    }
    if (!listStringEquals(a.nestedSubsystems, b.nestedSubsystems)) {
      return false;
    }
    if (!listStringEquals(a.contentRefs, b.contentRefs)) {
      return false;
    }
    return catalogEquals(a.catalog, b.catalog, lenientCatalogXmlBlobs);
  }

  /**
   * Сравнение DTO через JSON без учёта пробельных символов — запасной вариант проверки после фрагментной
   * JAXB-записи, когда строгое {@link #equalsDto(MdObjectPropertiesDto, MdObjectPropertiesDto, boolean)} ещё
   * расходится из‑за формата вложенных строк/XML.
   */
  public static boolean equalsDtoLenientJson(MdObjectPropertiesDto a, MdObjectPropertiesDto b) {
    if (a == b) {
      return true;
    }
    if (a == null || b == null) {
      return false;
    }
    String sa = GSON.toJson(a).replaceAll("\\s+", "");
    String sb = GSON.toJson(b).replaceAll("\\s+", "");
    return sa.equals(sb);
  }

  /**
   * Минимальная проверка после фрагментной записи: ключевые поля и состав реквизитов/ТЧ совпадают с ожидаемым DTO,
   * если полное сравнение после JAXB round-trip недостижимо.
   */
  public static boolean matchesAfterSpliceStructural(MdObjectPropertiesDto v, MdObjectPropertiesDto e) {
    if (v == null || e == null) {
      return false;
    }
    if (!Objects.equals(v.kind, e.kind) || !Objects.equals(v.internalName, e.internalName)) {
      return false;
    }
    if (!Objects.equals(v.comment, e.comment) || !Objects.equals(v.synonymRu, e.synonymRu)) {
      return false;
    }
    if (!namedListNamesOnly(v.attributes, e.attributes) || !namedListNamesOnly(v.tabularSections, e.tabularSections)) {
      return false;
    }
    if (!listStringEquals(v.nestedSubsystems, e.nestedSubsystems)
      || !listStringEquals(v.contentRefs, e.contentRefs)) {
      return false;
    }
    if (e.catalog != null && v.catalog != null) {
      return Objects.equals(v.catalog.codeLength, e.catalog.codeLength)
        && Objects.equals(v.catalog.codeType, e.catalog.codeType)
        && Objects.equals(v.catalog.hierarchical, e.catalog.hierarchical);
    }
    return e.catalog == null && v.catalog == null;
  }

  private static boolean namedListNamesOnly(List<MdNamedPropertyDto> a, List<MdNamedPropertyDto> b) {
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

  /**
   * Какие логические области файла изменились относительно базового снимка.
   *
   * @param propertiesRegion блок {@code Properties} под объектом (Catalog/Document/…)
   * @param childObjectsRegion блок {@code ChildObjects} (реквизиты, ТЧ, вложенные подсистемы)
   */
  public record ChangeMask(boolean propertiesRegion, boolean childObjectsRegion) {
  }

  /**
   * Глубокое сравнение DTO (для no-op перед записью).
   */
  public static boolean equalsDto(MdObjectPropertiesDto a, MdObjectPropertiesDto b) {
    return equalsDto(a, b, false);
  }

  /**
   * Глубокое сравнение DTO.
   *
   * @param lenientCatalogXmlBlobs если {@code true}, поля {@link MdCatalogPropertiesDto#standardAttributesXml} и
   *   {@link MdCatalogPropertiesDto#characteristicsXml} сравниваются после нормализации пробелов по краям и
   *   переводов строк — для проверки результата фрагментной JAXB-записи (round-trip может слегка менять формат).
   */
  public static boolean equalsDto(MdObjectPropertiesDto a, MdObjectPropertiesDto b, boolean lenientCatalogXmlBlobs) {
    if (a == b) {
      return true;
    }
    if (a == null || b == null) {
      return false;
    }
    if (!Objects.equals(a.kind, b.kind)
      || !Objects.equals(a.internalName, b.internalName)
      || !Objects.equals(a.synonymRu, b.synonymRu)
      || !Objects.equals(a.comment, b.comment)) {
      return false;
    }
    if (!namedListEquals(a.attributes, b.attributes)) {
      return false;
    }
    if (!namedListEquals(a.tabularSections, b.tabularSections)) {
      return false;
    }
    if (!listStringEquals(a.nestedSubsystems, b.nestedSubsystems)) {
      return false;
    }
    if (!listStringEquals(a.contentRefs, b.contentRefs)) {
      return false;
    }
    return catalogEquals(a.catalog, b.catalog, lenientCatalogXmlBlobs);
  }

  static boolean looseXmlBlobEquals(String x, String y) {
    if (Objects.equals(x, y)) {
      return true;
    }
    String a = x == null ? "" : x;
    String b = y == null ? "" : y;
    String na = a.replace("\r\n", "\n").replace('\r', '\n').trim();
    String nb = b.replace("\r\n", "\n").replace('\r', '\n').trim();
    if (na.equals(nb)) {
      return true;
    }
    if (na.replaceAll("\\s+", " ").equals(nb.replaceAll("\\s+", " "))) {
      return true;
    }
    return na.replaceAll("\\s", "").equals(nb.replaceAll("\\s", ""));
  }

  /**
   * Вычисляет маску изменений: какие регионы XML нужно перезаписать.
   */
  public static ChangeMask computeChangeMask(MdObjectPropertiesDto baseline, MdObjectPropertiesDto incoming) {
    String kind = incoming.kind;
    if (kind == null) {
      return new ChangeMask(true, true);
    }
    return switch (kind) {
      case "catalog" -> catalogMask(baseline, incoming);
      case "document", "exchangePlan" -> docLikeMask(baseline, incoming);
      case "constant", "enum", "report", "dataProcessor", "task", "chartOfAccounts",
           "chartOfCharacteristicTypes", "chartOfCalculationTypes", "commonModule",
           "sessionParameter", "commonAttribute", "commonPicture", "documentNumerator",
           "externalDataSource", "role" -> docLikeMask(baseline, incoming);
      case "subsystem" -> subsystemMask(baseline, incoming);
      default -> new ChangeMask(true, true);
    };
  }

  private static ChangeMask catalogMask(MdObjectPropertiesDto baseline, MdObjectPropertiesDto incoming) {
    boolean child =
      !namedListEquals(baseline.attributes, incoming.attributes)
        || !namedListEquals(baseline.tabularSections, incoming.tabularSections);
    boolean props =
      !Objects.equals(baseline.synonymRu, incoming.synonymRu)
        || !Objects.equals(baseline.comment, incoming.comment)
        || !catalogEquals(baseline.catalog, incoming.catalog, false);
    return new ChangeMask(props, child);
  }

  private static ChangeMask docLikeMask(MdObjectPropertiesDto baseline, MdObjectPropertiesDto incoming) {
    boolean child =
      !namedListEquals(baseline.attributes, incoming.attributes)
        || !namedListEquals(baseline.tabularSections, incoming.tabularSections);
    boolean props =
      !Objects.equals(baseline.synonymRu, incoming.synonymRu)
        || !Objects.equals(baseline.comment, incoming.comment);
    return new ChangeMask(props, child);
  }

  private static ChangeMask subsystemMask(MdObjectPropertiesDto baseline, MdObjectPropertiesDto incoming) {
    boolean child = !listStringEquals(baseline.nestedSubsystems, incoming.nestedSubsystems);
    boolean props =
      !Objects.equals(baseline.synonymRu, incoming.synonymRu)
        || !Objects.equals(baseline.comment, incoming.comment)
        || !listStringEquals(baseline.contentRefs, incoming.contentRefs);
    return new ChangeMask(props, child);
  }

  private static boolean catalogEquals(MdCatalogPropertiesDto a, MdCatalogPropertiesDto b, boolean lenientCatalogXmlBlobs) {
    if (a == b) {
      return true;
    }
    if (a == null || b == null) {
      return false;
    }
    boolean stdAttrEq =
      lenientCatalogXmlBlobs ? looseXmlBlobEquals(a.standardAttributesXml, b.standardAttributesXml)
        : Objects.equals(a.standardAttributesXml, b.standardAttributesXml);
    boolean charEq =
      lenientCatalogXmlBlobs ? looseXmlBlobEquals(a.characteristicsXml, b.characteristicsXml)
        : Objects.equals(a.characteristicsXml, b.characteristicsXml);
    return Objects.equals(a.objectBelonging, b.objectBelonging)
      && Objects.equals(a.extendedConfigurationObject, b.extendedConfigurationObject)
      && a.hierarchical == b.hierarchical
      && Objects.equals(a.hierarchyType, b.hierarchyType)
      && a.limitLevelCount == b.limitLevelCount
      && Objects.equals(a.levelCount, b.levelCount)
      && a.foldersOnTop == b.foldersOnTop
      && a.useStandardCommands == b.useStandardCommands
      && listStringEquals(a.owners, b.owners)
      && Objects.equals(a.subordinationUse, b.subordinationUse)
      && Objects.equals(a.codeLength, b.codeLength)
      && Objects.equals(a.descriptionLength, b.descriptionLength)
      && Objects.equals(a.codeType, b.codeType)
      && Objects.equals(a.codeAllowedLength, b.codeAllowedLength)
      && Objects.equals(a.codeSeries, b.codeSeries)
      && a.checkUnique == b.checkUnique
      && a.autonumbering == b.autonumbering
      && Objects.equals(a.defaultPresentation, b.defaultPresentation)
      && stdAttrEq
      && charEq
      && Objects.equals(a.predefined, b.predefined)
      && Objects.equals(a.predefinedDataUpdate, b.predefinedDataUpdate)
      && Objects.equals(a.editType, b.editType)
      && a.quickChoice == b.quickChoice
      && Objects.equals(a.choiceMode, b.choiceMode)
      && listStringEquals(a.inputByString, b.inputByString)
      && Objects.equals(a.searchStringModeOnInputByString, b.searchStringModeOnInputByString)
      && Objects.equals(a.fullTextSearchOnInputByString, b.fullTextSearchOnInputByString)
      && Objects.equals(a.choiceDataGetModeOnInputByString, b.choiceDataGetModeOnInputByString)
      && Objects.equals(a.defaultObjectForm, b.defaultObjectForm)
      && Objects.equals(a.defaultFolderForm, b.defaultFolderForm)
      && Objects.equals(a.defaultListForm, b.defaultListForm)
      && Objects.equals(a.defaultChoiceForm, b.defaultChoiceForm)
      && Objects.equals(a.defaultFolderChoiceForm, b.defaultFolderChoiceForm)
      && Objects.equals(a.auxiliaryObjectForm, b.auxiliaryObjectForm)
      && Objects.equals(a.auxiliaryFolderForm, b.auxiliaryFolderForm)
      && Objects.equals(a.auxiliaryListForm, b.auxiliaryListForm)
      && Objects.equals(a.auxiliaryChoiceForm, b.auxiliaryChoiceForm)
      && Objects.equals(a.auxiliaryFolderChoiceForm, b.auxiliaryFolderChoiceForm)
      && Objects.equals(a.objectModule, b.objectModule)
      && Objects.equals(a.managerModule, b.managerModule)
      && a.includeHelpInContents == b.includeHelpInContents
      && Objects.equals(a.help, b.help)
      && listStringEquals(a.basedOn, b.basedOn)
      && listStringEquals(a.dataLockFields, b.dataLockFields)
      && Objects.equals(a.dataLockControlMode, b.dataLockControlMode)
      && Objects.equals(a.fullTextSearch, b.fullTextSearch)
      && Objects.equals(a.objectPresentationRu, b.objectPresentationRu)
      && Objects.equals(a.extendedObjectPresentationRu, b.extendedObjectPresentationRu)
      && Objects.equals(a.listPresentationRu, b.listPresentationRu)
      && Objects.equals(a.extendedListPresentationRu, b.extendedListPresentationRu)
      && Objects.equals(a.explanationRu, b.explanationRu)
      && Objects.equals(a.createOnInput, b.createOnInput)
      && Objects.equals(a.choiceHistoryOnInput, b.choiceHistoryOnInput)
      && Objects.equals(a.dataHistory, b.dataHistory)
      && a.updateDataHistoryImmediatelyAfterWrite == b.updateDataHistoryImmediatelyAfterWrite
      && a.executeAfterWriteDataHistoryVersionProcessing == b.executeAfterWriteDataHistoryVersionProcessing
      && Objects.equals(a.additionalIndexes, b.additionalIndexes);
  }

  static boolean namedListEquals(List<MdNamedPropertyDto> a, List<MdNamedPropertyDto> b) {
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
      MdNamedPropertyDto x = a.get(i);
      MdNamedPropertyDto y = b.get(i);
      if (!Objects.equals(x.name, y.name)
        || !Objects.equals(x.synonymRu, y.synonymRu)
        || !Objects.equals(x.comment, y.comment)) {
        return false;
      }
    }
    return true;
  }

  static boolean listStringEquals(List<String> a, List<String> b) {
    if (a == null) {
      a = new ArrayList<>();
    }
    if (b == null) {
      b = new ArrayList<>();
    }
    return a.equals(b);
  }
}
