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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
    if (!namedListEquals(a.enumValues, b.enumValues)) {
      return false;
    }
    if (!namedListEquals(a.dimensions, b.dimensions) || !namedListEquals(a.resources, b.resources)) {
      return false;
    }
    if (!namedListEquals(a.dimensions, b.dimensions) || !namedListEquals(a.resources, b.resources)) {
      return false;
    }
    if (!listStringEquals(a.nestedSubsystems, b.nestedSubsystems)) {
      return false;
    }
    if (!listStringEquals(a.contentRefs, b.contentRefs)) {
      return false;
    }
    if (!scalarsEqual(a.scalars, b.scalars)) {
      return false;
    }
    if (!listStringEquals(a.documents, b.documents)
      || !listStringEquals(a.registerRecords, b.registerRecords)) {
      return false;
    }
    if (!Objects.equals(
      a.contentMembers == null ? java.util.List.of() : a.contentMembers,
      b.contentMembers == null ? java.util.List.of() : b.contentMembers)) {
      return false;
    }
    return catalogEquals(a.catalog, b.catalog, lenientCatalogXmlBlobs)
      && documentEquals(a.document, b.document, lenientCatalogXmlBlobs)
      && simpleKindsEqual(a, b, lenientCatalogXmlBlobs);
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
    if (!namedListEquals(a.enumValues, b.enumValues)) {
      return false;
    }
    if (!listStringEquals(a.nestedSubsystems, b.nestedSubsystems)) {
      return false;
    }
    if (!listStringEquals(a.contentRefs, b.contentRefs)) {
      return false;
    }
    if (!scalarsEqual(a.scalars, b.scalars)) {
      return false;
    }
    if (!listStringEquals(a.documents, b.documents)
      || !listStringEquals(a.registerRecords, b.registerRecords)) {
      return false;
    }
    if (!Objects.equals(
      a.contentMembers == null ? java.util.List.of() : a.contentMembers,
      b.contentMembers == null ? java.util.List.of() : b.contentMembers)) {
      return false;
    }
    return catalogEquals(a.catalog, b.catalog, lenientCatalogXmlBlobs)
      && documentEquals(a.document, b.document, lenientCatalogXmlBlobs)
      && simpleKindsEqual(a, b, lenientCatalogXmlBlobs);
  }

  static boolean documentEquals(MdDocumentPropertiesDto a, MdDocumentPropertiesDto b, boolean lenientXmlBlobs) {
    if (a == b) {
      return true;
    }
    if (a == null || b == null) {
      return false;
    }
    boolean stdEq = lenientXmlBlobs
      ? looseXmlBlobEquals(a.standardAttributesXml, b.standardAttributesXml)
      : Objects.equals(a.standardAttributesXml, b.standardAttributesXml);
    boolean charEq = lenientXmlBlobs
      ? looseXmlBlobEquals(a.characteristicsXml, b.characteristicsXml)
      : Objects.equals(a.characteristicsXml, b.characteristicsXml);
    return Objects.equals(a.objectBelonging, b.objectBelonging)
      && Objects.equals(a.extendedConfigurationObject, b.extendedConfigurationObject)
      && a.useStandardCommands == b.useStandardCommands
      && Objects.equals(a.numerator, b.numerator)
      && Objects.equals(a.numberType, b.numberType)
      && Objects.equals(a.numberLength, b.numberLength)
      && Objects.equals(a.numberAllowedLength, b.numberAllowedLength)
      && Objects.equals(a.numberPeriodicity, b.numberPeriodicity)
      && a.checkUnique == b.checkUnique
      && a.autonumbering == b.autonumbering
      && stdEq
      && charEq
      && listStringEquals(a.basedOn, b.basedOn)
      && listStringEquals(a.inputByString, b.inputByString)
      && Objects.equals(a.createOnInput, b.createOnInput)
      && Objects.equals(a.searchStringModeOnInputByString, b.searchStringModeOnInputByString)
      && Objects.equals(a.fullTextSearchOnInputByString, b.fullTextSearchOnInputByString)
      && Objects.equals(a.choiceDataGetModeOnInputByString, b.choiceDataGetModeOnInputByString)
      && Objects.equals(a.choiceHistoryOnInput, b.choiceHistoryOnInput)
      && Objects.equals(a.defaultObjectForm, b.defaultObjectForm)
      && Objects.equals(a.defaultListForm, b.defaultListForm)
      && Objects.equals(a.defaultChoiceForm, b.defaultChoiceForm)
      && Objects.equals(a.auxiliaryObjectForm, b.auxiliaryObjectForm)
      && Objects.equals(a.auxiliaryListForm, b.auxiliaryListForm)
      && Objects.equals(a.auxiliaryChoiceForm, b.auxiliaryChoiceForm)
      && Objects.equals(a.objectModule, b.objectModule)
      && Objects.equals(a.managerModule, b.managerModule)
      && Objects.equals(a.posting, b.posting)
      && Objects.equals(a.realTimePosting, b.realTimePosting)
      && Objects.equals(a.registerRecordsDeletion, b.registerRecordsDeletion)
      && Objects.equals(a.registerRecordsWritingOnPost, b.registerRecordsWritingOnPost)
      && Objects.equals(a.sequenceFilling, b.sequenceFilling)
      && listStringEquals(a.registerRecords, b.registerRecords)
      && a.postInPrivilegedMode == b.postInPrivilegedMode
      && a.unpostInPrivilegedMode == b.unpostInPrivilegedMode
      && a.includeHelpInContents == b.includeHelpInContents
      && Objects.equals(a.help, b.help)
      && listStringEquals(a.dataLockFields, b.dataLockFields)
      && Objects.equals(a.dataLockControlMode, b.dataLockControlMode)
      && Objects.equals(a.fullTextSearch, b.fullTextSearch)
      && Objects.equals(a.objectPresentationRu, b.objectPresentationRu)
      && Objects.equals(a.extendedObjectPresentationRu, b.extendedObjectPresentationRu)
      && Objects.equals(a.listPresentationRu, b.listPresentationRu)
      && Objects.equals(a.extendedListPresentationRu, b.extendedListPresentationRu)
      && Objects.equals(a.explanationRu, b.explanationRu)
      && Objects.equals(a.dataHistory, b.dataHistory)
      && a.updateDataHistoryImmediatelyAfterWrite == b.updateDataHistoryImmediatelyAfterWrite
      && a.executeAfterWriteDataHistoryVersionProcessing == b.executeAfterWriteDataHistoryVersionProcessing
      && Objects.equals(a.additionalIndexes, b.additionalIndexes);
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
    if (na.replaceAll("\\s", "").equals(nb.replaceAll("\\s", ""))) {
      return true;
    }
    return semanticXmlEquals(na, nb);
  }

  /**
   * Семантическое сравнение XML: JAXB между запусками JVM по-разному раскладывает пространства имён
   * (какому URI достанется default, какие префиксы получат остальные), текст при этом описывает те же данные.
   */
  private static boolean semanticXmlEquals(String a, String b) {
    try {
      javax.xml.parsers.DocumentBuilderFactory dbf = javax.xml.parsers.DocumentBuilderFactory.newInstance();
      dbf.setNamespaceAware(true);
      dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
      org.w3c.dom.Document d1 =
        dbf.newDocumentBuilder().parse(new org.xml.sax.InputSource(new java.io.StringReader(a)));
      org.w3c.dom.Document d2 =
        dbf.newDocumentBuilder().parse(new org.xml.sax.InputSource(new java.io.StringReader(b)));
      return domElementsEqual(d1.getDocumentElement(), d2.getDocumentElement());
    } catch (Exception e) {
      return false;
    }
  }

  private static final String XMLNS_NS = "http://www.w3.org/2000/xmlns/";
  private static final String XSI_NS = "http://www.w3.org/2001/XMLSchema-instance";

  private static boolean domElementsEqual(org.w3c.dom.Element x, org.w3c.dom.Element y) {
    if (!Objects.equals(x.getLocalName(), y.getLocalName())
      || !Objects.equals(nz(x.getNamespaceURI()), nz(y.getNamespaceURI()))) {
      return false;
    }
    if (!domAttributes(x).equals(domAttributes(y))) {
      return false;
    }
    List<org.w3c.dom.Element> cx = domChildElements(x);
    List<org.w3c.dom.Element> cy = domChildElements(y);
    if (cx.size() != cy.size()) {
      return false;
    }
    for (int i = 0; i < cx.size(); i++) {
      if (!domElementsEqual(cx.get(i), cy.get(i))) {
        return false;
      }
    }
    return domText(x).equals(domText(y));
  }

  private static java.util.SortedMap<String, String> domAttributes(org.w3c.dom.Element el) {
    java.util.TreeMap<String, String> out = new java.util.TreeMap<>();
    org.w3c.dom.NamedNodeMap attrs = el.getAttributes();
    for (int i = 0; i < attrs.getLength(); i++) {
      org.w3c.dom.Attr attr = (org.w3c.dom.Attr) attrs.item(i);
      if (XMLNS_NS.equals(attr.getNamespaceURI())) {
        continue;
      }
      String key = nz(attr.getNamespaceURI()) + "|" + (attr.getLocalName() == null ? attr.getName() : attr.getLocalName());
      String value = attr.getValue();
      if (XSI_NS.equals(attr.getNamespaceURI()) && "type".equals(attr.getLocalName())) {
        value = resolveQNameValue(el, value);
      }
      out.put(key, value);
    }
    return out;
  }

  /** {@code xsi:type="prefix:Name"} → {@code {uri}Name}: имя префикса не несёт семантики. */
  private static String resolveQNameValue(org.w3c.dom.Element scope, String value) {
    int colon = value.indexOf(':');
    if (colon <= 0) {
      return "{" + nz(scope.lookupNamespaceURI(null)) + "}" + value;
    }
    String prefix = value.substring(0, colon);
    String uri = scope.lookupNamespaceURI(prefix);
    return uri == null ? value : "{" + uri + "}" + value.substring(colon + 1);
  }

  private static List<org.w3c.dom.Element> domChildElements(org.w3c.dom.Element el) {
    List<org.w3c.dom.Element> out = new ArrayList<>();
    org.w3c.dom.NodeList children = el.getChildNodes();
    for (int i = 0; i < children.getLength(); i++) {
      if (children.item(i) instanceof org.w3c.dom.Element child) {
        out.add(child);
      }
    }
    return out;
  }

  private static String domText(org.w3c.dom.Element el) {
    StringBuilder sb = new StringBuilder();
    org.w3c.dom.NodeList children = el.getChildNodes();
    for (int i = 0; i < children.getLength(); i++) {
      org.w3c.dom.Node node = children.item(i);
      if (node.getNodeType() == org.w3c.dom.Node.TEXT_NODE
        || node.getNodeType() == org.w3c.dom.Node.CDATA_SECTION_NODE) {
        sb.append(node.getNodeValue());
      }
    }
    return sb.toString().trim();
  }

  private static String nz(String s) {
    return s == null ? "" : s;
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
      case "document" -> documentMask(baseline, incoming);
      case "exchangePlan" -> docLikeMask(baseline, incoming);
      case "constant", "enum", "report", "dataProcessor", "documentJournal", "task", "businessProcess",
           "chartOfAccounts",
           "chartOfCharacteristicTypes", "chartOfCalculationTypes", "commonModule",
           "informationRegister", "accumulationRegister",
           "sessionParameter", "commonAttribute", "commonPicture", "documentNumerator",
           "eventSubscription", "scheduledJob", "commonCommand",
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

  private static ChangeMask documentMask(MdObjectPropertiesDto baseline, MdObjectPropertiesDto incoming) {
    boolean child =
      !namedListEquals(baseline.attributes, incoming.attributes)
        || !namedListEquals(baseline.tabularSections, incoming.tabularSections);
    boolean props =
      !Objects.equals(baseline.synonymRu, incoming.synonymRu)
        || !Objects.equals(baseline.comment, incoming.comment)
        || !documentEquals(baseline.document, incoming.document, false);
    return new ChangeMask(props, child);
  }

  private static ChangeMask docLikeMask(MdObjectPropertiesDto baseline, MdObjectPropertiesDto incoming) {
    boolean child =
      !namedListEquals(baseline.attributes, incoming.attributes)
        || !namedListEquals(baseline.tabularSections, incoming.tabularSections)
        || !namedListEquals(baseline.enumValues, incoming.enumValues)
        || !namedListEquals(baseline.dimensions, incoming.dimensions)
        || !namedListEquals(baseline.resources, incoming.resources);
    boolean props =
      !Objects.equals(baseline.synonymRu, incoming.synonymRu)
        || !Objects.equals(baseline.comment, incoming.comment)
        || !simpleKindsEqual(baseline, incoming, false);
    return new ChangeMask(props, child);
  }

  /** Плоские DTO перечисления, константы, общего модуля и регистров. */
  private static boolean simpleKindsEqual(MdObjectPropertiesDto a, MdObjectPropertiesDto b, boolean lenientXmlBlobs) {
    return MdFlatDtoSupport.equalsFlat(a.enumeration, b.enumeration, lenientXmlBlobs)
      && MdFlatDtoSupport.equalsFlat(a.constant, b.constant, lenientXmlBlobs)
      && MdFlatDtoSupport.equalsFlat(a.commonModule, b.commonModule, lenientXmlBlobs)
      && MdFlatDtoSupport.equalsFlat(a.register, b.register, lenientXmlBlobs)
      && MdFlatDtoSupport.equalsFlat(a.report, b.report, lenientXmlBlobs)
      && MdFlatDtoSupport.equalsFlat(a.documentJournal, b.documentJournal, lenientXmlBlobs)
      && MdFlatDtoSupport.equalsFlat(a.exchangePlan, b.exchangePlan, lenientXmlBlobs)
      && MdFlatDtoSupport.equalsFlat(
        a.chartOfCharacteristicTypes, b.chartOfCharacteristicTypes, lenientXmlBlobs)
      && MdFlatDtoSupport.equalsFlat(a.task, b.task, lenientXmlBlobs)
      && MdFlatDtoSupport.equalsFlat(a.businessProcess, b.businessProcess, lenientXmlBlobs)
      && MdFlatDtoSupport.equalsFlat(a.chartOfAccounts, b.chartOfAccounts, lenientXmlBlobs)
      && MdFlatDtoSupport.equalsFlat(
        a.chartOfCalculationTypes, b.chartOfCalculationTypes, lenientXmlBlobs)
      && MdFlatDtoSupport.equalsFlat(a.sessionParameter, b.sessionParameter, lenientXmlBlobs)
      && MdFlatDtoSupport.equalsFlat(a.documentNumerator, b.documentNumerator, lenientXmlBlobs)
      && MdFlatDtoSupport.equalsFlat(a.eventSubscription, b.eventSubscription, lenientXmlBlobs)
      && MdFlatDtoSupport.equalsFlat(a.scheduledJob, b.scheduledJob, lenientXmlBlobs)
      && MdFlatDtoSupport.equalsFlat(a.commonCommand, b.commonCommand, lenientXmlBlobs)
      && MdFlatDtoSupport.equalsFlat(a.commonAttribute, b.commonAttribute, lenientXmlBlobs)
      && MdFlatDtoSupport.equalsFlat(a.commonPicture, b.commonPicture, lenientXmlBlobs)
      && MdFlatDtoSupport.equalsFlat(a.role, b.role, lenientXmlBlobs)
      && MdFlatDtoSupport.equalsFlat(a.externalDataSource, b.externalDataSource, lenientXmlBlobs);
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

  /** Булево из JSON может прийти строкой: скаляры сравниваются по каноничному виду. */
  private static boolean scalarsEqual(java.util.Map<String, Object> a, java.util.Map<String, Object> b) {
    if (a == null || b == null) {
      return a == b;
    }
    if (!a.keySet().equals(b.keySet())) {
      return false;
    }
    for (java.util.Map.Entry<String, Object> entry : a.entrySet()) {
      String left = entry.getValue() == null ? "" : String.valueOf(entry.getValue());
      Object other = b.get(entry.getKey());
      String right = other == null ? "" : String.valueOf(other);
      if (!left.equals(right)) {
        return false;
      }
    }
    return true;
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
        || !Objects.equals(x.comment, y.comment)
        || !MdFlatDtoSupport.equalsFlat(x.type, y.type, false)
        || !paletteEquals(x, y)
        || !namedListEquals(x.attributes, y.attributes)) {
        return false;
      }
    }
    return true;
  }

  /** Свойства палитры узла состава: подсказка и перечислимые флаги. */
  private static boolean paletteEquals(MdNamedPropertyDto x, MdNamedPropertyDto y) {
    return Objects.equals(x.toolTipRu, y.toolTipRu)
      && Objects.equals(x.fillChecking, y.fillChecking)
      && Objects.equals(x.indexing, y.indexing)
      && Objects.equals(x.fullTextSearch, y.fullTextSearch)
      && Objects.equals(x.dataHistory, y.dataHistory)
      && Objects.equals(x.use, y.use)
      && Objects.equals(x.quickChoice, y.quickChoice)
      && Objects.equals(x.createOnInput, y.createOnInput)
      && Objects.equals(x.choiceHistoryOnInput, y.choiceHistoryOnInput)
      && Objects.equals(x.choiceForm, y.choiceForm)
      && choiceLinksEqual(x.choiceParameterLinks, y.choiceParameterLinks);
  }

  /** Связи параметров выбора: сравниваем поштучно, порядок значим как в XML. */
  private static boolean choiceLinksEqual(
    List<MdChoiceParameterLinkDto> a, List<MdChoiceParameterLinkDto> b) {
    List<MdChoiceParameterLinkDto> left = a == null ? new ArrayList<>() : a;
    List<MdChoiceParameterLinkDto> right = b == null ? new ArrayList<>() : b;
    if (left.size() != right.size()) {
      return false;
    }
    for (int i = 0; i < left.size(); i++) {
      if (!Objects.equals(left.get(i).name, right.get(i).name)
        || !Objects.equals(left.get(i).dataPath, right.get(i).dataPath)
        || !Objects.equals(left.get(i).mode, right.get(i).mode)) {
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
