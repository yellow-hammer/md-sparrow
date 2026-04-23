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

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Извлекает типизированные исходящие связи объекта метаданных и его синоним из его XML-файла.
 *
 * <p>Работает через DOM по локальным именам тегов: подходит для обеих поддерживаемых версий схемы (v2.20/v2.21),
 * не зависит от JAXB-моделей конкретной версии.
 */
public final class MdObjectGraphExtractor {

  /** Объекты, для которых поддержано извлечение исходящих связей. */
  private static final Set<String> SUPPORTED_TYPES = Set.of(
    "Catalog",
    "Document",
    "DocumentJournal",
    "Subsystem",
    "InformationRegister",
    "AccumulationRegister",
    "AccountingRegister",
    "CalculationRegister",
    "BusinessProcess",
    "Task",
    "ChartOfCharacteristicTypes",
    "ChartOfAccounts",
    "ChartOfCalculationTypes",
    "ExchangePlan",
    "FilterCriterion",
    "FunctionalOption",
    "FunctionalOptionsParameter",
    "Sequence",
    "DefinedType",
    "Constant",
    "CommonAttribute",
    "SessionParameter",
    "EventSubscription",
    "ScheduledJob",
    "Report",
    "DataProcessor",
    "CommonCommand");

  private MdObjectGraphExtractor() {
  }

  /** Возвращает {@code true}, если для типа есть специализированный извлекатель связей. */
  public static boolean isSupported(String objectType) {
    return SUPPORTED_TYPES.contains(objectType);
  }

  /**
   * Читает XML и собирает синоним и исходящие связи объекта.
   *
   * @param objectXml путь к XML-файлу объекта (например {@code Catalogs/Имя.xml})
   * @param objectType корневой тип объекта (Catalog, Document, …)
   * @return результат с синонимом и списком {@link OutEdge}; для неподдерживаемых типов — пустой список
   */
  public static Inspection inspect(Path objectXml, String objectType) throws IOException {
    Document doc = XmlGraphReader.parse(objectXml);
    Element metaRoot = XmlGraphReader.findMetadataObjectRoot(doc).orElse(null);
    if (metaRoot == null) {
      return new Inspection("", List.of(), false);
    }
    Element objectNode = XmlGraphReader.firstChildLocal(metaRoot, objectType);
    if (objectNode == null) {
      return new Inspection("", List.of(), false);
    }
    Element properties = XmlGraphReader.firstChildLocal(objectNode, "Properties");
    Element childObjects = XmlGraphReader.firstChildLocal(objectNode, "ChildObjects");
    String synonymRu = readSynonymRu(properties);
    if (!isSupported(objectType)) {
      return new Inspection(synonymRu, List.of(), true);
    }
    List<OutEdge> edges = new ArrayList<>();
    switch (objectType) {
      case "Catalog" -> extractCatalog(properties, childObjects, edges);
      case "Document" -> extractDocument(properties, childObjects, edges);
      case "DocumentJournal" -> extractDocumentJournal(properties, edges);
      case "Subsystem" -> extractSubsystem(properties, childObjects, edges);
      case "InformationRegister",
        "AccumulationRegister" -> extractRegister(childObjects, edges);
      case "AccountingRegister" -> extractAccountingRegister(properties, childObjects, edges);
      case "CalculationRegister" -> extractCalculationRegister(properties, childObjects, edges);
      case "BusinessProcess", "Task" -> extractCatalogLikeWithAttributes(childObjects, edges);
      case "ChartOfCharacteristicTypes" -> extractChartOfCharacteristicTypes(properties, childObjects, edges);
      case "ChartOfAccounts" -> extractChartOfAccounts(properties, childObjects, edges);
      case "ChartOfCalculationTypes" -> extractCatalogLikeWithAttributes(childObjects, edges);
      case "ExchangePlan" -> extractExchangePlan(childObjects, edges);
      case "FilterCriterion" -> extractFilterCriterion(properties, edges);
      case "FunctionalOption" -> extractFunctionalOption(properties, edges);
      case "FunctionalOptionsParameter" -> extractFunctionalOptionsParameter(properties, edges);
      case "Sequence" -> extractSequence(properties, edges);
      case "DefinedType", "Constant", "SessionParameter" -> extractTypedSingle(properties, edges);
      case "CommonAttribute" -> extractCommonAttribute(properties, edges);
      case "EventSubscription" -> extractEventSubscription(properties, edges);
      case "ScheduledJob" -> extractScheduledJob(properties, edges);
      case "Report", "DataProcessor" -> extractCatalogLikeWithAttributes(childObjects, edges);
      case "CommonCommand" -> extractCommonCommand(properties, edges);
      default -> {
        // Синоним прочитан выше; для этого типа специализированный извлекатель не реализован —
        // рёбра не извлекаются, объект попадёт в граф без исходящих связей.
      }
    }
    return new Inspection(synonymRu, edges, false);
  }

  private static void extractCatalog(Element properties, Element childObjects, List<OutEdge> edges) {
    addRefList(properties, "Owners", "xr:Item", "owners", RelationKind.CATALOG_OWNERS, edges);
    addRefList(properties, "BasedOn", "xr:Item", "basedOn", RelationKind.DOCUMENT_BASED_ON, edges);
    addAttributesAndTabularSections(childObjects, edges);
  }

  private static void extractDocument(Element properties, Element childObjects, List<OutEdge> edges) {
    addRefList(properties, "RegisterRecords", "xr:Item", "registerRecords", RelationKind.DOCUMENT_POSTING_REGISTERS, edges);
    addRefList(properties, "BasedOn", "xr:Item", "basedOn", RelationKind.DOCUMENT_BASED_ON, edges);
    addAttributesAndTabularSections(childObjects, edges);
  }

  private static void extractDocumentJournal(Element properties, List<OutEdge> edges) {
    addRefList(properties, "RegisteredDocuments", "xr:Item", "registeredDocuments",
      RelationKind.DOCUMENT_JOURNAL_ENTRIES, edges);
  }

  private static void extractSubsystem(Element properties, Element childObjects, List<OutEdge> edges) {
    addRefList(properties, "Content", "xr:Item", "content", RelationKind.SUBSYSTEM_MEMBERSHIP, edges);
    if (childObjects != null) {
      List<Element> nested = XmlGraphReader.childrenLocal(childObjects, "Subsystem");
      int idx = 0;
      for (Element n : nested) {
        String name = XmlGraphReader.text(n);
        if (name != null && !name.isEmpty()) {
          edges.add(new OutEdge(
            "Subsystem." + name,
            RelationKind.SUBSYSTEM_NESTING,
            "childObjects.subsystem[" + idx + "]"));
        }
        idx++;
      }
    }
  }

  private static void extractRegister(Element childObjects, List<OutEdge> edges) {
    extractTypedNamedChildren(childObjects, "Dimension", "dimensions", RelationKind.REGISTER_DIMENSION_TYPE, edges);
    extractTypedNamedChildren(childObjects, "Resource", "resources", RelationKind.REGISTER_RESOURCE_TYPE, edges);
    extractTypedNamedChildren(childObjects, "Attribute", "attributes", RelationKind.TYPE_COMPOSITE, edges);
  }

  private static void extractAccountingRegister(Element properties, Element childObjects, List<OutEdge> edges) {
    addSingleRef(properties, "ChartOfAccounts", "chartOfAccounts", RelationKind.REGISTER_CHART_OF_ACCOUNTS, edges);
    extractRegister(childObjects, edges);
  }

  private static void extractCalculationRegister(Element properties, Element childObjects, List<OutEdge> edges) {
    addSingleRef(properties, "ChartOfCalculationTypes", "chartOfCalculationTypes",
      RelationKind.REGISTER_CHART_OF_CALCULATION_TYPES, edges);
    extractRegister(childObjects, edges);
  }

  private static void extractChartOfAccounts(Element properties, Element childObjects, List<OutEdge> edges) {
    addSingleRef(properties, "ExtDimensionTypes", "extDimensionTypes",
      RelationKind.CHART_OF_ACCOUNTS_EXT_DIMENSIONS, edges);
    extractCatalogLikeWithAttributes(childObjects, edges);
  }

  private static void extractCatalogLikeWithAttributes(Element childObjects, List<OutEdge> edges) {
    addAttributesAndTabularSections(childObjects, edges);
  }

  private static void extractChartOfCharacteristicTypes(
    Element properties,
    Element childObjects,
    List<OutEdge> edges
  ) {
    addCompositeType(properties, "Type", "type", RelationKind.TYPE_COMPOSITE, edges);
    addSingleRef(properties, "CharacteristicExtValues", "characteristicExtValues",
      RelationKind.CHARACTERISTIC_EXT_VALUES, edges);
    addAttributesAndTabularSections(childObjects, edges);
  }

  private static void extractExchangePlan(Element childObjects, List<OutEdge> edges) {
    if (childObjects == null) {
      return;
    }
    Element content = XmlGraphReader.firstChildLocal(childObjects, "Content");
    if (content != null) {
      List<Element> items = XmlGraphReader.childrenLocal(content, "Item");
      int idx = 0;
      for (Element it : items) {
        Element metadata = XmlGraphReader.firstChildLocal(it, "Metadata");
        String text = XmlGraphReader.text(metadata);
        Optional<String> key = MetadataRefParser.normalizeMdObjectRef(text);
        if (key.isPresent()) {
          edges.add(new OutEdge(key.get(), RelationKind.EXCHANGE_PLAN_CONTENT, "content[" + idx + "]"));
        }
        idx++;
      }
    }
    addAttributesAndTabularSections(childObjects, edges);
  }

  private static void extractFilterCriterion(Element properties, List<OutEdge> edges) {
    addCompositeType(properties, "Type", "type", RelationKind.FILTER_CRITERION_TYPE, edges);
    addContentRefs(properties, "Content", "content", RelationKind.FILTER_CRITERION_CONTENT, edges);
  }

  private static void extractFunctionalOption(Element properties, List<OutEdge> edges) {
    if (properties == null) {
      return;
    }
    Element location = XmlGraphReader.firstChildLocal(properties, "Location");
    String locText = XmlGraphReader.text(location);
    Optional<String> locKey = MetadataRefParser.normalizeMdObjectRef(locText);
    locKey.ifPresent(k -> edges.add(new OutEdge(k, RelationKind.FUNCTIONAL_OPTION_LOCATION, "location")));
    addContentRefs(properties, "Content", "content", RelationKind.FUNCTIONAL_OPTION_AFFECTED, edges);
  }

  private static void extractFunctionalOptionsParameter(Element properties, List<OutEdge> edges) {
    addRefList(properties, "Use", "xr:Item", "use", RelationKind.FOP_USE_BINDING, edges);
  }

  private static void extractSequence(Element properties, List<OutEdge> edges) {
    addRefList(properties, "Documents", "xr:Item", "documents", RelationKind.SEQUENCE_DOCUMENTS, edges);
    addRefList(properties, "RegisterRecords", "xr:Item", "registerRecords", RelationKind.SEQUENCE_REGISTERS, edges);
  }

  private static void extractTypedSingle(Element properties, List<OutEdge> edges) {
    addCompositeType(properties, "Type", "type", RelationKind.TYPE_COMPOSITE, edges);
  }

  private static void extractCommonAttribute(Element properties, List<OutEdge> edges) {
    addCompositeType(properties, "Type", "type", RelationKind.TYPE_COMPOSITE, edges);
    if (properties == null) {
      return;
    }
    Element content = XmlGraphReader.firstChildLocal(properties, "Content");
    if (content == null) {
      return;
    }
    List<Element> items = XmlGraphReader.childrenLocal(content, "Item");
    int idx = 0;
    for (Element it : items) {
      Element metadata = XmlGraphReader.firstChildLocal(it, "Metadata");
      String text = XmlGraphReader.text(metadata);
      Optional<String> key = MetadataRefParser.normalizeMdObjectRef(text);
      if (key.isPresent()) {
        edges.add(new OutEdge(key.get(), RelationKind.COMMON_ATTRIBUTE_USAGE, "content[" + idx + "]"));
      }
      idx++;
    }
  }

  /**
   * EventSubscription/Properties:
   * <ul>
   *   <li>{@code Source} — типы объектов-источников события ({@code v8:Type}/{@code v8:TypeSet}),
   *       включая {@code cfg:DocumentObject.X}, {@code cfg:DefinedType.X} и т.п.;</li>
   *   <li>{@code Handler} — строка вида {@code CommonModule.МодульСобытий.ОбработчикСобытия}.</li>
   * </ul>
   */
  private static void extractEventSubscription(Element properties, List<OutEdge> edges) {
    if (properties == null) {
      return;
    }
    addCompositeType(properties, "Source", "source", RelationKind.SUBSCRIPTION_SOURCE, edges);
    Element handler = XmlGraphReader.firstChildLocal(properties, "Handler");
    String handlerText = XmlGraphReader.text(handler);
    MetadataRefParser.normalizeMdObjectRef(handlerText)
      .ifPresent(k -> edges.add(new OutEdge(k, RelationKind.SUBSCRIPTION_HANDLER, "handler")));
  }

  /**
   * ScheduledJob/Properties:
   * {@code MethodName} — строка вида {@code CommonModule.МодульСлужебный.ОбработчикЗадания}.
   */
  private static void extractScheduledJob(Element properties, List<OutEdge> edges) {
    if (properties == null) {
      return;
    }
    Element methodName = XmlGraphReader.firstChildLocal(properties, "MethodName");
    String methodText = XmlGraphReader.text(methodName);
    MetadataRefParser.normalizeMdObjectRef(methodText)
      .ifPresent(k -> edges.add(new OutEdge(k, RelationKind.SCHEDULED_JOB_HANDLER, "methodName")));
  }

  /**
   * CommonCommand/Properties/CommandParameterType — тип объекта, для которого активна команда.
   * Содержит {@code v8:Type}/{@code v8:TypeSet} (аналогично типам реквизитов).
   */
  private static void extractCommonCommand(Element properties, List<OutEdge> edges) {
    addCompositeType(properties, "CommandParameterType", "commandParameterType",
      RelationKind.COMMAND_PARAMETER_TYPE, edges);
  }

  private static void addAttributesAndTabularSections(Element childObjects, List<OutEdge> edges) {
    if (childObjects == null) {
      return;
    }
    List<Element> attributes = XmlGraphReader.childrenLocal(childObjects, "Attribute");
    int ai = 0;
    for (Element attr : attributes) {
      String name = readNamedNodeName(attr);
      String pathPrefix = "attributes." + (name == null ? "[" + ai + "]" : name);
      addCompositeTypeFromObjectProps(attr, pathPrefix, RelationKind.TYPE_COMPOSITE, edges);
      ai++;
    }
    List<Element> tss = XmlGraphReader.childrenLocal(childObjects, "TabularSection");
    int ti = 0;
    for (Element ts : tss) {
      String tsName = readNamedNodeName(ts);
      String tsBase = "tabularSections." + (tsName == null ? "[" + ti + "]" : tsName);
      Element tsChildren = XmlGraphReader.firstChildLocal(ts, "ChildObjects");
      if (tsChildren != null) {
        List<Element> tsAttrs = XmlGraphReader.childrenLocal(tsChildren, "Attribute");
        int tai = 0;
        for (Element ta : tsAttrs) {
          String taName = readNamedNodeName(ta);
          String pathPrefix = tsBase + ".attributes." + (taName == null ? "[" + tai + "]" : taName);
          addCompositeTypeFromObjectProps(ta, pathPrefix, RelationKind.TYPE_COMPOSITE, edges);
          tai++;
        }
      }
      ti++;
    }
  }

  /** Извлекает {@code <Тип>/v8:Type|v8:TypeSet} у Properties перечисленных дочерних узлов (Dimension, Resource, ...). */
  private static void extractTypedNamedChildren(
    Element childObjects,
    String tagLocal,
    String pathBucket,
    RelationKind kind,
    List<OutEdge> edges
  ) {
    if (childObjects == null) {
      return;
    }
    List<Element> nodes = XmlGraphReader.childrenLocal(childObjects, tagLocal);
    int i = 0;
    for (Element n : nodes) {
      String name = readNamedNodeName(n);
      String pathPrefix = pathBucket + "." + (name == null ? "[" + i + "]" : name);
      addCompositeTypeFromObjectProps(n, pathPrefix, kind, edges);
      i++;
    }
  }

  private static void addCompositeTypeFromObjectProps(
    Element objectNode,
    String pathPrefix,
    RelationKind kind,
    List<OutEdge> edges
  ) {
    Element props = XmlGraphReader.firstChildLocal(objectNode, "Properties");
    if (props == null) {
      return;
    }
    addCompositeType(props, "Type", pathPrefix + ".type", kind, edges);
  }

  /** Собирает все {@code v8:Type} и {@code v8:TypeSet} в один кортеж рёбер с указанным {@link RelationKind}. */
  private static void addCompositeType(
    Element properties,
    String typeContainerLocal,
    String pathPrefix,
    RelationKind kind,
    List<OutEdge> edges
  ) {
    if (properties == null) {
      return;
    }
    Element type = XmlGraphReader.firstChildLocal(properties, typeContainerLocal);
    if (type == null) {
      return;
    }
    int i = 0;
    for (Element t : XmlGraphReader.childrenLocal(type, "Type")) {
      Optional<String> key = MetadataRefParser.normalizeTypeRef(XmlGraphReader.text(t));
      if (key.isPresent()) {
        edges.add(new OutEdge(key.get(), kind, pathPrefix + "[" + i + "]"));
      }
      i++;
    }
    int j = 0;
    for (Element ts : XmlGraphReader.childrenLocal(type, "TypeSet")) {
      Optional<String> key = MetadataRefParser.normalizeTypeRef(XmlGraphReader.text(ts));
      if (key.isPresent()) {
        edges.add(new OutEdge(key.get(), kind, pathPrefix + "Set[" + j + "]"));
      }
      j++;
    }
  }

  /** Одиночный текстовый дочерний элемент Properties → единственное ссылочное ребро. */
  private static void addSingleRef(
    Element properties,
    String elementLocal,
    String via,
    RelationKind kind,
    List<OutEdge> edges
  ) {
    if (properties == null) {
      return;
    }
    Element el = XmlGraphReader.firstChildLocal(properties, elementLocal);
    MetadataRefParser.normalizeMdObjectRef(XmlGraphReader.text(el))
      .ifPresent(k -> edges.add(new OutEdge(k, kind, via)));
  }

  /** Контейнер с дочерними {@code xr:Item xsi:type="xr:MDObjectRef"} → ссылочные рёбра. */
  private static void addRefList(
    Element properties,
    String containerLocal,
    String childLocal,
    String pathBucket,
    RelationKind kind,
    List<OutEdge> edges
  ) {
    if (properties == null) {
      return;
    }
    Element container = XmlGraphReader.firstChildLocal(properties, containerLocal);
    if (container == null) {
      return;
    }
    String childName = stripPrefix(childLocal);
    List<Element> items = XmlGraphReader.childrenLocal(container, childName);
    int i = 0;
    for (Element it : items) {
      Optional<String> key = MetadataRefParser.normalizeMdObjectRef(XmlGraphReader.text(it));
      if (key.isPresent()) {
        edges.add(new OutEdge(key.get(), kind, pathBucket + "[" + i + "]"));
      }
      i++;
    }
  }

  /**
   * Универсальный обход контейнера Content/RegisteredDocuments/Use, в котором могут быть
   * {@code xr:Item}, {@code xr:Object}, {@code xr:Field}, {@code Metadata}-узлы.
   */
  private static void addContentRefs(
    Element properties,
    String containerLocal,
    String pathBucket,
    RelationKind kind,
    List<OutEdge> edges
  ) {
    if (properties == null) {
      return;
    }
    Element container = XmlGraphReader.firstChildLocal(properties, containerLocal);
    if (container == null) {
      return;
    }
    int i = 0;
    Set<String> seenAtIndex = new LinkedHashSet<>();
    org.w3c.dom.Node n = container.getFirstChild();
    while (n != null) {
      if (n.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE) {
        Element child = (Element) n;
        String text = XmlGraphReader.text(child);
        Optional<String> key = MetadataRefParser.normalizeMdObjectRef(text);
        if (key.isPresent()) {
          String dedupeKey = key.get() + "@" + i;
          if (seenAtIndex.add(dedupeKey)) {
            edges.add(new OutEdge(key.get(), kind, pathBucket + "[" + i + "]"));
          }
        } else {
          Element metadata = XmlGraphReader.firstChildLocal(child, "Metadata");
          Optional<String> nestedKey = MetadataRefParser.normalizeMdObjectRef(XmlGraphReader.text(metadata));
          if (nestedKey.isPresent()) {
            edges.add(new OutEdge(nestedKey.get(), kind, pathBucket + "[" + i + "].metadata"));
          }
        }
        i++;
      }
      n = n.getNextSibling();
    }
  }

  private static String readNamedNodeName(Element objectNode) {
    Element props = XmlGraphReader.firstChildLocal(objectNode, "Properties");
    if (props == null) {
      return null;
    }
    Element nameEl = XmlGraphReader.firstChildLocal(props, "Name");
    return XmlGraphReader.text(nameEl);
  }

  private static String readSynonymRu(Element properties) {
    if (properties == null) {
      return "";
    }
    Element synonym = XmlGraphReader.firstChildLocal(properties, "Synonym");
    if (synonym == null) {
      return "";
    }
    List<Element> items = XmlGraphReader.childrenLocal(synonym, "item");
    for (Element it : items) {
      Element lang = XmlGraphReader.firstChildLocal(it, "lang");
      Element content = XmlGraphReader.firstChildLocal(it, "content");
      String langText = XmlGraphReader.text(lang);
      if (langText != null && "ru".equals(langText.toLowerCase(Locale.ROOT))) {
        String txt = XmlGraphReader.text(content);
        return txt == null ? "" : txt;
      }
    }
    return "";
  }

  private static String stripPrefix(String prefixed) {
    int colon = prefixed.indexOf(':');
    return colon >= 0 ? prefixed.substring(colon + 1) : prefixed;
  }

  /**
   * Результат разбора одного объекта: синоним и список исходящих рёбер.
   */
  public record Inspection(String synonymRu, List<OutEdge> edges, boolean partial) {
  }

  /**
   * Исходящее ребро объекта: цель, тип связи и логический путь к источнику ссылки.
   */
  public record OutEdge(String targetKey, RelationKind kind, String via) {
  }
}
