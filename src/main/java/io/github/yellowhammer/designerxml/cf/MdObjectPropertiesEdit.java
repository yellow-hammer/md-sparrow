/*
 * This file is a part of md-sparrow.
 *
 * Copyright (c) 2026
 * Ivan Karlo <i.karlo@outlook.com> and contributors
 *
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package io.github.yellowhammer.designerxml.cf;

import io.github.yellowhammer.designerxml.DesignerXml;
import io.github.yellowhammer.designerxml.SchemaVersion;
import io.github.yellowhammer.designerxml.reflect.JaxbReflect;

import jakarta.xml.bind.JAXBElement;
import jakarta.xml.bind.JAXBException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Чтение и запись расширенного DTO свойств объектов метаданных (справочник, документ, подсистема, план обмена) через JAXB.
 */
public final class MdObjectPropertiesEdit {

  private static final List<SimpleKindDef> SIMPLE_KINDS = List.of(
    new SimpleKindDef("constant", "getConstant"),
    new SimpleKindDef("enum", "getEnum"),
    new SimpleKindDef("report", "getReport"),
    new SimpleKindDef("dataProcessor", "getDataProcessor"),
    new SimpleKindDef("task", "getTask"),
    new SimpleKindDef("chartOfAccounts", "getChartOfAccounts"),
    new SimpleKindDef("chartOfCharacteristicTypes", "getChartOfCharacteristicTypes"),
    new SimpleKindDef("chartOfCalculationTypes", "getChartOfCalculationTypes"),
    new SimpleKindDef("commonModule", "getCommonModule"),
    new SimpleKindDef("informationRegister", "getInformationRegister"),
    new SimpleKindDef("accumulationRegister", "getAccumulationRegister"),
    new SimpleKindDef("sessionParameter", "getSessionParameter"),
    new SimpleKindDef("commonAttribute", "getCommonAttribute"),
    new SimpleKindDef("commonPicture", "getCommonPicture"),
    new SimpleKindDef("documentNumerator", "getDocumentNumerator"),
    new SimpleKindDef("externalDataSource", "getExternalDataSource"),
    new SimpleKindDef("role", "getRole"),
    new SimpleKindDef("eventSubscription", "getEventSubscription"),
    new SimpleKindDef("scheduledJob", "getScheduledJob"),
    new SimpleKindDef("commonCommand", "getCommonCommand"),
    new SimpleKindDef("documentJournal", "getDocumentJournal"),
    new SimpleKindDef("businessProcess", "getBusinessProcess"),
    // Форма и макет описываются своими файлами Forms/<Имя>.xml и Templates/<Имя>.xml:
    // в составе объекта они лежат одним именем, свойства - только там
    new SimpleKindDef("form", "getForm"),
    new SimpleKindDef("template", "getTemplate"),
    // Внешние отчёт и обработка описываются как обычные объекты метаданных,
    // поэтому и свойства у них читаются тем же путём
    new SimpleKindDef("externalReport", "getExternalReport"),
    new SimpleKindDef("externalDataProcessor", "getExternalDataProcessor"),
    // Виды без своего моста свойств: читаются имя, синоним, комментарий и
    // состав; пишутся синоним и комментарий общим путём. Панель свойств
    // работает у любого узла дерева, а не отвечает «unsupported MetaDataObject»
    new SimpleKindDef("accountingRegister", "getAccountingRegister"),
    new SimpleKindDef("calculationRegister", "getCalculationRegister"),
    new SimpleKindDef("filterCriterion", "getFilterCriterion"),
    new SimpleKindDef("settingsStorage", "getSettingsStorage"),
    new SimpleKindDef("webService", "getWebService"),
    new SimpleKindDef("httpService", "getHTTPService"),
    new SimpleKindDef("integrationService", "getIntegrationService"),
    new SimpleKindDef("functionalOption", "getFunctionalOption"),
    new SimpleKindDef("functionalOptionsParameter", "getFunctionalOptionsParameter"),
    new SimpleKindDef("definedType", "getDefinedType"),
    new SimpleKindDef("commonForm", "getCommonForm"),
    new SimpleKindDef("commonTemplate", "getCommonTemplate"),
    new SimpleKindDef("commandGroup", "getCommandGroup"),
    new SimpleKindDef("xdtoPackage", "getXDTOPackage"),
    new SimpleKindDef("wsReference", "getWSReference"),
    new SimpleKindDef("style", "getStyle"),
    new SimpleKindDef("styleItem", "getStyleItem"),
    new SimpleKindDef("language", "getLanguage"),
    new SimpleKindDef("interface", "getInterface"),
    new SimpleKindDef("bot", "getBot"),
    new SimpleKindDef("webSocketClient", "getWebSocketClient"),
    new SimpleKindDef("sequence", "getSequence")
  );

  private MdObjectPropertiesEdit() {
  }

  /**
   * Виды из {@code ChildObjects}, которые читает {@code cf-md-object-get}.
   *
   * @param childObjectType {@code Catalog}, {@code CommonForm}, …
   * @return {@code true}, если для вида есть разбор свойств
   */
  public static boolean supportsChildObjectType(String childObjectType) {
    if (childObjectType == null || childObjectType.isBlank()) {
      return false;
    }
    if (childObjectType.equals("Catalog")
        || childObjectType.equals("Document")
        || childObjectType.equals("Subsystem")
        || childObjectType.equals("ExchangePlan")) {
      return true;
    }
    String getter = "get" + childObjectType;
    for (SimpleKindDef def : SIMPLE_KINDS) {
      if (def.getterName().equals(getter)) {
        return true;
      }
    }
    return false;
  }

  public static MdObjectPropertiesDto readDto(Path objectXml, SchemaVersion version) throws IOException, JAXBException {
    if (!Files.isRegularFile(objectXml)) {
      throw new IllegalArgumentException("file not found: " + objectXml);
    }
    Object root = DesignerXml.read(objectXml, version);
    if (!(root instanceof JAXBElement<?> je)) {
      throw new IllegalArgumentException("expected JAXBElement root");
    }
    return readFromRoot(je, version);
  }

  /**
   * Чтение DTO из UTF-8 байтов (проверка после точечной записи).
   */
  public static MdObjectPropertiesDto readDto(byte[] utf8Xml, SchemaVersion version) throws JAXBException {
    Object root = DesignerXml.unmarshal(version, new ByteArrayInputStream(utf8Xml));
    if (!(root instanceof JAXBElement<?> je)) {
      throw new IllegalArgumentException("expected JAXBElement root");
    }
    return readFromRoot(je, version);
  }

  public static void writeDto(Path objectXml, SchemaVersion version, MdObjectPropertiesDto dto)
    throws IOException, JAXBException {
    if (dto == null || dto.kind == null || dto.internalName == null || dto.internalName.isEmpty()) {
      throw new IllegalArgumentException("kind and internalName required");
    }
    if (!Files.isRegularFile(objectXml)) {
      throw new IllegalArgumentException("file not found: " + objectXml);
    }
    SupportRules.ensureEditable(objectXml);
    MdObjectPropertiesDto baseline = readDto(objectXml, version);
    MdObjectPropertiesJsonCoalesce.coalesceFromBaseline(dto, baseline);
    if (dto.attributes == null) {
      dto.attributes = new ArrayList<>();
    }
    if (dto.tabularSections == null) {
      dto.tabularSections = new ArrayList<>();
    }
    if (dto.enumValues == null) {
      dto.enumValues = new ArrayList<>();
    }
    if (dto.dimensions == null) {
      dto.dimensions = new ArrayList<>();
    }
    if (dto.resources == null) {
      dto.resources = new ArrayList<>();
    }
    if (dto.nestedSubsystems == null) {
      dto.nestedSubsystems = new ArrayList<>();
    }
    if (dto.contentRefs == null) {
      dto.contentRefs = new ArrayList<>();
    }
    if (MdObjectPropertiesDiff.equalsDto(baseline, dto)) {
      return;
    }
    checkStemMatches(objectXml, dto.internalName);
    String xml = Files.readString(objectXml, StandardCharsets.UTF_8);
    String container = MdObjectPropertiesGranularPatch.containerLocalForKind(dto.kind);
    if (!container.isEmpty()) {
      Optional<byte[]> granular = MdObjectPropertiesGranularPatch.tryApply(xml, container, version, baseline, dto);
      if (granular.isPresent()) {
        Files.write(objectXml, granular.get());
        return;
      }
    }
    Object root = DesignerXml.read(objectXml, version);
    if (!(root instanceof JAXBElement<?> je)) {
      throw new IllegalArgumentException("expected JAXBElement root");
    }
    applyDto(je, version, dto);
    MdObjectPropertiesDiff.ChangeMask mask = MdObjectPropertiesDiff.computeChangeMask(baseline, dto);
    Optional<byte[]> spliced = MdObjectPropertiesSplice.trySplice(xml, version, je, dto, mask);
    if (spliced.isPresent()) {
      MdObjectPropertiesDto verified = readDto(spliced.get(), version);
      if (MdObjectPropertiesDiff.equalsDto(verified, dto, true)
        || MdObjectPropertiesDiff.equalsDtoLenientJson(verified, dto)
        || MdObjectPropertiesDiff.matchesAfterSpliceStructural(verified, dto)) {
        Files.write(objectXml, spliced.get());
        return;
      }
    }
    Optional<String> granularReason = MdObjectPropertiesGranularPatch.describeFirstUnpatchableChange(
      xml,
      container,
      baseline,
      dto);
    String reason = granularReason.orElse("причина не определена");
    throw new IllegalStateException(
      "Не удалось применить изменения точечно (" + reason + "). "
        + "Полная пересборка XML через JAXB предотвращена.");
  }

  private static void checkStemMatches(Path objectXml, String internalName) {
    String fn = objectXml.getFileName().toString();
    if (!fn.endsWith(".xml")) {
      throw new IllegalArgumentException("expected .xml file");
    }
    String stem = fn.substring(0, fn.length() - 4);
    if (!stem.equals(internalName)) {
      throw new IllegalArgumentException("file name must match internal name: " + stem + " vs " + internalName);
    }
  }

  private static MdObjectPropertiesDto readFromRoot(JAXBElement<?> je, SchemaVersion version) {
    Object mdo = je.getValue();
    Object cat = JaxbReflect.get(mdo, "getCatalog");
    if (cat != null) {
      return readCatalogLike(cat, "catalog", true, version);
    }
    Object doc = JaxbReflect.get(mdo, "getDocument");
    if (doc != null) {
      return readCatalogLike(doc, "document", false, version);
    }
    Object sub = JaxbReflect.get(mdo, "getSubsystem");
    if (sub != null) {
      return readSubsystem(sub);
    }
    Object ep = JaxbReflect.get(mdo, "getExchangePlan");
    if (ep != null) {
      return readCatalogLike(ep, "exchangePlan", false, version);
    }
    MdObjectPropertiesDto simple = tryReadSimpleKind(mdo, version);
    if (simple != null) {
      return simple;
    }
    throw new IllegalArgumentException("unsupported MetaDataObject for cf-md-object");
  }

  private static MdObjectPropertiesDto readCatalogLike(
    Object node, String kind, boolean catalog, SchemaVersion version) {
    Object props = JaxbReflect.get(node, "getProperties");
    MdObjectPropertiesDto dto = base(props, kind);
    if (catalog) {
      MdCatalogPropertiesBridge.read(version, props, dto);
    }
    if ("exchangePlan".equals(kind)) {
      MdExchangePlanPropertiesBridge.read(version, props, dto);
    }
    if ("document".equals(kind)) {
      MdDocumentPropertiesBridge.read(version, props, dto);
    }
    readCatalogLikeChildren(node, dto);
    readNamedChildren(node, dto);
    return dto;
  }

  /**
   * Прочие виды узлов состава: команды, графы, признаки учёта и остальные.
   *
   * Читаются у любого объекта: getter, которого у вида нет, возвращает пустой
   * список. Из-за этого палитра расширения показывает свойства у всех узлов,
   * а не только у реквизитов, табличных частей, значений, измерений и ресурсов.
   */
  private static final List<NamedChildDef> NAMED_CHILDREN = List.of(
    new NamedChildDef("getCommand", dto -> dto.commands),
    new NamedChildDef("getColumn", dto -> dto.columns),
    new NamedChildDef("getAccountingFlag", dto -> dto.accountingFlags),
    new NamedChildDef("getExtDimensionAccountingFlag", dto -> dto.extDimensionAccountingFlags),
    new NamedChildDef("getAddressingAttribute", dto -> dto.addressingAttributes),
    new NamedChildDef("getRecalculation", dto -> dto.recalculations),
    new NamedChildDef("getOperation", dto -> dto.operations),
    new NamedChildDef("getURLTemplate", dto -> dto.urlTemplates),
    new NamedChildDef("getChannel", dto -> dto.channels),
    new NamedChildDef("getTable", dto -> dto.tables),
    new NamedChildDef("getCube", dto -> dto.cubes),
    new NamedChildDef("getFunction", dto -> dto.functions)
  );

  /** Вид узла состава: имя getter в ChildObjects и список DTO, куда он читается. */
  private record NamedChildDef(
    String getterName,
    java.util.function.Function<MdObjectPropertiesDto, List<MdNamedPropertyDto>> target
  ) {
  }

  /** Читает все прочие виды узлов состава объекта. */
  private static void readNamedChildren(Object node, MdObjectPropertiesDto dto) {
    Object co = invokeNoArgOrNull(node, "getChildObjects");
    if (co == null) {
      return;
    }
    for (NamedChildDef def : NAMED_CHILDREN) {
      for (Object child : JaxbReflect.<Object>listOptional(co, def.getterName())) {
        // Перерасчёт в составе регистра расчёта лежит одним именем, его
        // описание - отдельным файлом; узлом с Properties он не является
        if (child instanceof String name) {
          def.target().apply(dto).add(new MdNamedPropertyDto(name, "", ""));
        } else {
          def.target().apply(dto).add(namedDto(child));
        }
      }
    }
  }

  /** Реквизиты и табличные части объекта: состав одинаков у справочника, документа, ПВХ и других. */
  private static void readCatalogLikeChildren(Object node, MdObjectPropertiesDto dto) {
    Object co = invokeNoArgOrNull(node, "getChildObjects");
    if (co == null) {
      return;
    }
    for (Object a : JaxbReflect.<Object>list(co, "getAttribute")) {
      dto.attributes.add(namedDto(a));
    }
    for (Object ts : JaxbReflect.<Object>list(co, "getTabularSection")) {
      dto.tabularSections.add(namedDto(ts));
    }
  }

  private static MdObjectPropertiesDto base(Object props, String kind) {
    MdObjectPropertiesDto dto = new MdObjectPropertiesDto();
    dto.kind = kind;
    dto.internalName = JaxbReflect.getString(props, "getName");
    dto.synonymRu = LocalStringSync.firstRu(JaxbReflect.get(props, "getSynonym"));
    String comment = JaxbReflect.getString(props, "getComment");
    dto.comment = comment == null ? "" : comment;
    return dto;
  }

  private static MdNamedPropertyDto namedDto(Object attrOrSection) {
    Object p = JaxbReflect.get(attrOrSection, "getProperties");
    String comment = JaxbReflect.getString(p, "getComment");
    MdNamedPropertyDto dto = new MdNamedPropertyDto(
      JaxbReflect.getString(p, "getName"),
      LocalStringSync.firstRu(JaxbReflect.get(p, "getSynonym")),
      comment == null ? "" : comment);
    // У табличной части и значения перечисления типа нет — getType вернёт null.
    dto.type = MdTypeDescriptionBridge.read(JaxbReflect.getOptional(p, "getType"));
    readPaletteProperties(p, dto);
    // Реквизиты табличной части: у остальных видов узлов ChildObjects нет
    Object nested = invokeNoArgOrNull(attrOrSection, "getChildObjects");
    if (nested != null) {
      List<MdNamedPropertyDto> inner = new ArrayList<>();
      for (Object a : JaxbReflect.<Object>listOptional(nested, "getAttribute")) {
        inner.add(namedDto(a));
      }
      if (!inner.isEmpty()) {
        dto.attributes = inner;
      }
    }
    return dto;
  }

  private static MdObjectPropertiesDto readSubsystem(Object sub) {
    Object props = JaxbReflect.get(sub, "getProperties");
    MdObjectPropertiesDto dto = base(props, "subsystem");
    Object content = JaxbReflect.get(props, "getContent");
    if (content != null) {
      dto.contentRefs.addAll(MdListTypeRefs.readItemTexts(JaxbReflect.list(content, "getItem")));
    }
    Object ch = JaxbReflect.get(sub, "getChildObjects");
    if (ch != null) {
      dto.nestedSubsystems.addAll(JaxbReflect.<String>list(ch, "getSubsystem"));
    }
    readScalars(props, dto);
    return dto;
  }

  /** Для тестов: применить DTO к корню JAXB без записи файла. */
  static void applyDtoForTest(JAXBElement<?> je, SchemaVersion version, MdObjectPropertiesDto dto) {
    applyDto(je, version, dto);
  }

  private static void applyDto(JAXBElement<?> je, SchemaVersion version, MdObjectPropertiesDto dto) {
    Object mdo = je.getValue();
    if (tryApplySimpleKind(mdo, version, dto)) {
      return;
    }
    switch (dto.kind) {
      case "catalog" -> {
        Object cat = require(mdo, "getCatalog", "Catalog");
        Object props = JaxbReflect.get(cat, "getProperties");
        if (dto.catalog != null) {
          MdCatalogPropertiesBridge.apply(version, props, dto);
        } else {
          applyCatalogLike(props, dto);
        }
        applyAttrs(JaxbReflect.get(cat, "getChildObjects"), dto);
      }
      case "document" -> {
        Object doc = require(mdo, "getDocument", "Document");
        Object docProps = JaxbReflect.get(doc, "getProperties");
        if (dto.document != null) {
          MdDocumentPropertiesBridge.apply(version, docProps, dto);
        } else {
          applyCatalogLike(docProps, dto);
        }
        applyAttrs(JaxbReflect.get(doc, "getChildObjects"), dto);
      }
      case "exchangePlan" -> {
        Object ep = require(mdo, "getExchangePlan", "ExchangePlan");
        Object epProps = JaxbReflect.get(ep, "getProperties");
        if (dto.exchangePlan != null) {
          MdExchangePlanPropertiesBridge.apply(version, epProps, dto);
        } else {
          applyCatalogLike(epProps, dto);
        }
        applyAttrs(JaxbReflect.get(ep, "getChildObjects"), dto);
      }
      case "subsystem" -> applySubsystem(require(mdo, "getSubsystem", "Subsystem"), dto);
      default -> throw new IllegalArgumentException("unknown kind: " + dto.kind);
    }
  }

  private static Object require(Object mdo, String getter, String label) {
    Object node = JaxbReflect.get(mdo, getter);
    if (node == null) {
      throw new IllegalArgumentException("MetaDataObject is not a " + label);
    }
    return node;
  }

  private static void applyCatalogLike(Object props, MdObjectPropertiesDto dto) {
    if (!dto.internalName.equals(JaxbReflect.getStringOptional(props, "getName"))) {
      throw new IllegalArgumentException("internalName mismatch with XML");
    }
    String syn = dto.synonymRu == null ? "" : dto.synonymRu;
    LocalStringSync.setOrPutRu(JaxbReflect.getOptional(props, "getSynonym"), syn);
    LocalStringSync.replaceRu(JaxbReflect.getOptional(props, "getObjectPresentation"), syn);
    LocalStringSync.replaceRu(JaxbReflect.getOptional(props, "getExtendedObjectPresentation"), syn);
    LocalStringSync.replaceRu(JaxbReflect.getOptional(props, "getListPresentation"), syn);
    LocalStringSync.replaceRu(JaxbReflect.getOptional(props, "getExtendedListPresentation"), syn);
    LocalStringSync.replaceRu(JaxbReflect.getOptional(props, "getExplanation"), syn);
    JaxbReflect.setOptional(props, "setComment", dto.comment == null ? "" : dto.comment);
  }

  private static void applyAttrs(Object co, MdObjectPropertiesDto dto) {
    if (co == null) {
      return;
    }
    List<Object> attrs = JaxbReflect.list(co, "getAttribute");
    List<Object> sections = JaxbReflect.list(co, "getTabularSection");
    validateNamed(dto.attributes, attrs, "attribute");
    validateNamed(dto.tabularSections, sections, "tabularSection");
    for (int i = 0; i < attrs.size(); i++) {
      applyNamedDto(attrs.get(i), dto.attributes.get(i), "attribute");
    }
    for (int i = 0; i < sections.size(); i++) {
      applyNamedDto(sections.get(i), dto.tabularSections.get(i), "tabular section");
    }
  }

  private static void applyNamedDto(Object node, MdNamedPropertyDto d, String label) {
    Object p = JaxbReflect.get(node, "getProperties");
    if (d == null || d.name == null || !d.name.equals(JaxbReflect.getString(p, "getName"))) {
      throw new IllegalArgumentException(label + " name mismatch");
    }
    if (d.type != null) {
      MdTypeDescriptionBridge.apply(JaxbReflect.ensureOptional(p, "getType", "setType"), d.type);
    }
    String syn = d.synonymRu == null ? "" : d.synonymRu;
    LocalStringSync.setOrPutRu(JaxbReflect.get(p, "getSynonym"), syn);
    JaxbReflect.set(p, "setComment", d.comment == null ? "" : d.comment);
    applyPaletteProperties(p, d);
  }

  /**
   * Свойства палитры узла состава: подсказка и перечислимые флаги.
   *
   * <p>Набор зависит от вида узла и версии формата: чего в схеме нет, у того нет и геттера,
   * и поле остаётся пустым.
   */
  private static void readPaletteProperties(Object props, MdNamedPropertyDto dto) {
    dto.toolTipRu = LocalStringSync.firstRu(JaxbReflect.getOptional(props, "getToolTip"));
    dto.fillChecking = JaxbReflect.enumNameOptional(props, "getFillChecking");
    dto.indexing = JaxbReflect.enumNameOptional(props, "getIndexing");
    dto.fullTextSearch = JaxbReflect.enumNameOptional(props, "getFullTextSearch");
    dto.dataHistory = JaxbReflect.enumNameOptional(props, "getDataHistory");
    dto.use = JaxbReflect.enumNameOptional(props, "getUse");
    dto.quickChoice = JaxbReflect.enumNameOptional(props, "getQuickChoice");
    dto.createOnInput = JaxbReflect.enumNameOptional(props, "getCreateOnInput");
    dto.choiceHistoryOnInput = JaxbReflect.enumNameOptional(props, "getChoiceHistoryOnInput");
    dto.choiceForm = JaxbReflect.getStringOptional(props, "getChoiceForm");
    readChoiceParameters(props, dto);
  }

  /**
   * Кладёт свойства палитры обратно в XML.
   *
   * <p>Пустое значение оставляет прежнее: панель присылает только то, что правила, а
   * отсутствующее в схеме свойство пропускается вместе со своим сеттером.
   */
  private static void applyPaletteProperties(Object props, MdNamedPropertyDto d) {
    if (d.toolTipRu != null) {
      Object toolTip = JaxbReflect.ensureOptional(props, "getToolTip", "setToolTip");
      if (toolTip != null) {
        LocalStringSync.setOrPutRu(toolTip, d.toolTipRu);
      }
    }
    JaxbReflect.setEnumOrKeep(props, "setFillChecking", d.fillChecking);
    JaxbReflect.setEnumOrKeep(props, "setIndexing", d.indexing);
    JaxbReflect.setEnumOrKeep(props, "setFullTextSearch", d.fullTextSearch);
    JaxbReflect.setEnumOrKeep(props, "setDataHistory", d.dataHistory);
    JaxbReflect.setEnumOrKeep(props, "setUse", d.use);
    JaxbReflect.setEnumOrKeep(props, "setQuickChoice", d.quickChoice);
    JaxbReflect.setEnumOrKeep(props, "setCreateOnInput", d.createOnInput);
    JaxbReflect.setEnumOrKeep(props, "setChoiceHistoryOnInput", d.choiceHistoryOnInput);
    if (d.choiceForm != null) {
      JaxbReflect.setOptional(props, "setChoiceForm", d.choiceForm);
    }
    applyChoiceParameterLinks(props, d);
  }

  /**
   * Параметры выбора и связи параметров выбора.
   *
   * <p>У параметра значение типизировано (строка, число, ссылка, список выбора формы), поэтому
   * оно читается текстом и не пишется: строкой его не восстановить. Связь состоит из имени, пути
   * к данным и режима изменения - её читаем и пишем целиком.
   */
  private static void readChoiceParameters(Object props, MdNamedPropertyDto dto) {
    Object parameters = JaxbReflect.getOptional(props, "getChoiceParameters");
    if (parameters != null) {
      List<MdChoiceParameterDto> out = new ArrayList<>();
      for (Object item : JaxbReflect.<Object>listOptional(parameters, "getItem")) {
        Object value = JaxbReflect.getOptional(item, "getValue");
        out.add(new MdChoiceParameterDto(
          JaxbReflect.getStringOptional(item, "getName"),
          value == null ? "" : MdListTypeRefs.readItemTexts(List.of(value)).stream().findFirst().orElse("")));
      }
      if (!out.isEmpty()) {
        dto.choiceParameters = out;
      }
    }
    Object links = JaxbReflect.getOptional(props, "getChoiceParameterLinks");
    if (links == null) {
      return;
    }
    List<MdChoiceParameterLinkDto> out = new ArrayList<>();
    for (Object link : JaxbReflect.<Object>listOptional(links, "getLink")) {
      out.add(new MdChoiceParameterLinkDto(
        firstText(link, "getName"),
        firstText(link, "getDataPath"),
        JaxbReflect.enumNameOptional(link, "getValueChange")));
    }
    if (!out.isEmpty()) {
      dto.choiceParameterLinks = out;
    }
  }

  /** Имя и путь к данным приходят списками из-за xs:choice схемы: берём первое значение. */
  private static String firstText(Object node, String getter) {
    List<String> values = JaxbReflect.listOptional(node, getter);
    return values.isEmpty() ? "" : values.get(0);
  }

  /**
   * Кладёт связи параметров выбора обратно.
   *
   * <p>Число и порядок связей должны совпадать с XML: панель их не добавляет и не удаляет,
   * а расхождение означало бы правку поверх устаревшего снимка.
   */
  private static void applyChoiceParameterLinks(Object props, MdNamedPropertyDto d) {
    if (d.choiceParameterLinks == null) {
      return;
    }
    Object links = JaxbReflect.getOptional(props, "getChoiceParameterLinks");
    if (links == null) {
      return;
    }
    List<Object> items = JaxbReflect.listOptional(links, "getLink");
    if (items.size() != d.choiceParameterLinks.size()) {
      throw new IllegalArgumentException("choiceParameterLinks: число связей не совпадает с XML");
    }
    for (int i = 0; i < items.size(); i++) {
      MdChoiceParameterLinkDto link = d.choiceParameterLinks.get(i);
      Object item = items.get(i);
      replaceFirstText(item, "getName", link.name);
      replaceFirstText(item, "getDataPath", link.dataPath);
      JaxbReflect.setEnumOrKeep(item, "setValueChange", link.mode);
    }
  }

  /** Заменяет первое значение списка строк, оставляя остальные как в файле. */
  private static void replaceFirstText(Object node, String getter, String value) {
    if (value == null) {
      return;
    }
    List<String> values = JaxbReflect.listOptional(node, getter);
    if (values.isEmpty()) {
      values.add(value);
      return;
    }
    values.set(0, value);
  }

  private static void applySubsystem(Object sub, MdObjectPropertiesDto dto) {
    Object props = JaxbReflect.get(sub, "getProperties");
    if (!dto.internalName.equals(JaxbReflect.getString(props, "getName"))) {
      throw new IllegalArgumentException("internalName mismatch with XML");
    }
    String syn = dto.synonymRu == null ? "" : dto.synonymRu;
    LocalStringSync.setOrPutRu(JaxbReflect.get(props, "getSynonym"), syn);
    JaxbReflect.set(props, "setComment", dto.comment == null ? "" : dto.comment);
    Object ch = JaxbReflect.get(sub, "getChildObjects");
    if (ch == null) {
      return;
    }
    if (dto.nestedSubsystems == null) {
      throw new IllegalArgumentException("nestedSubsystems required");
    }
    List<Object> nested = JaxbReflect.list(ch, "getSubsystem");
    nested.clear();
    nested.addAll(new ArrayList<>(dto.nestedSubsystems));
    applyScalars(props, dto);
  }

  private static <T> void validateNamed(List<MdNamedPropertyDto> dtos, List<T> xml, String label) {
    if (dtos == null) {
      throw new IllegalArgumentException("missing list: " + label);
    }
    if (dtos.size() != xml.size()) {
      throw new IllegalArgumentException("count mismatch for " + label + ": JSON " + dtos.size() + " vs XML " + xml.size());
    }
  }

  private static MdObjectPropertiesDto tryReadSimpleKind(Object metaDataObject, SchemaVersion version) {
    for (SimpleKindDef def : SIMPLE_KINDS) {
      Object child = invokeNoArgOrNull(metaDataObject, def.getterName);
      if (child != null) {
        return readSimpleDto(def.kind, child, version);
      }
    }
    return null;
  }

  private static boolean tryApplySimpleKind(Object metaDataObject, SchemaVersion version, MdObjectPropertiesDto dto) {
    SimpleKindDef def = simpleKindByName(dto.kind);
    if (def == null) {
      return false;
    }
    Object child = invokeNoArgOrNull(metaDataObject, def.getterName);
    if (child == null) {
      throw new IllegalArgumentException("MetaDataObject is not a " + dto.kind);
    }
    applySimpleDto(child, version, dto);
    return true;
  }

  /**
   * Виды без своего моста свойств: их скалярные свойства читаются и пишутся
   * рефлексией по Properties, панель показывает их единой формой.
   */
  private static final Set<String> GENERIC_SCALAR_KINDS = Set.of(
    "externalReport", "externalDataProcessor", "form", "template",
    "commonForm", "commonTemplate", "webService", "httpService",
    "integrationService", "filterCriterion", "settingsStorage",
    "functionalOption", "functionalOptionsParameter", "definedType",
    "commandGroup", "xdtoPackage", "wsReference", "style", "styleItem",
    "language", "interface", "bot", "webSocketClient", "sequence",
    "accountingRegister", "calculationRegister");

  /**
   * Свойства, которые в скаляры не попадают: имя, синоним и комментарий несут
   * свои поля, остальное - ссылки на файлы выгрузки и служебные отметки,
   * правка которых строкой ломает объект.
   */
  private static final Set<String> SCALAR_SKIPPED = Set.of(
    "Name", "Synonym", "Comment", "Uuid",
    "ExtendedConfigurationObject", "Form", "Help", "Picture",
    "Module", "ManagerModule", "RecordSetModule", "Template");

  private static MdObjectPropertiesDto readSimpleDto(String kind, Object objectNode, SchemaVersion version) {
    Object props = invokeNoArg(objectNode, "getProperties");
    MdObjectPropertiesDto dto = new MdObjectPropertiesDto();
    dto.kind = kind;
    dto.internalName = toStringOrEmpty(invokeNoArg(props, "getName"));
    dto.synonymRu = readLocalStringRu(invokeNoArgOrNull(props, "getSynonym"));
    dto.comment = toStringOrEmpty(invokeNoArgOrNull(props, "getComment"));
    switch (kind) {
      case "enum" -> {
        MdEnumPropertiesBridge.read(version, props, dto);
        readEnumValues(objectNode, dto);
      }
      case "constant" -> MdConstantPropertiesBridge.read(props, dto);
      case "report", "dataProcessor" -> MdReportPropertiesBridge.read(props, dto);
      case "documentJournal" -> MdDocumentJournalPropertiesBridge.read(version, props, dto);
      case "chartOfCalculationTypes" -> {
        MdChartOfCalculationTypesPropertiesBridge.read(version, props, dto);
        readCatalogLikeChildren(objectNode, dto);
      }
      case "chartOfAccounts" -> {
        MdChartOfAccountsPropertiesBridge.read(version, props, dto);
        readCatalogLikeChildren(objectNode, dto);
      }
      case "businessProcess" -> {
        MdBusinessProcessPropertiesBridge.read(version, props, dto);
        readCatalogLikeChildren(objectNode, dto);
      }
      case "task" -> {
        MdTaskPropertiesBridge.read(version, props, dto);
        readCatalogLikeChildren(objectNode, dto);
      }
      case "chartOfCharacteristicTypes" -> {
        MdChartOfCharacteristicTypesPropertiesBridge.read(version, props, dto);
        readCatalogLikeChildren(objectNode, dto);
      }
      case "commonModule" -> MdCommonModulePropertiesBridge.read(props, dto);
      case "sessionParameter" -> MdSessionParameterPropertiesBridge.read(props, dto);
      case "documentNumerator" -> MdDocumentNumeratorPropertiesBridge.read(props, dto);
      case "eventSubscription" -> MdEventSubscriptionPropertiesBridge.read(props, dto);
      case "scheduledJob" -> MdScheduledJobPropertiesBridge.read(props, dto);
      case "commonCommand" -> MdCommonCommandPropertiesBridge.read(props, dto);
      case "commonAttribute" -> MdCommonAttributePropertiesBridge.read(props, dto);
      case "commonPicture" -> MdCommonPicturePropertiesBridge.read(props, dto);
      case "role" -> MdRolePropertiesBridge.read(props, dto);
      case "externalDataSource" -> MdExternalDataSourcePropertiesBridge.read(props, dto);
      case "informationRegister", "accumulationRegister" -> {
        MdRegisterPropertiesBridge.read(version, props, dto);
        readRegisterChildren(objectNode, dto);
      }
      case "externalReport", "externalDataProcessor" -> readCatalogLikeChildren(objectNode, dto);
      // Регистры бухгалтерии и расчёта: измерения, ресурсы и реквизиты как у
      // остальных регистров; перерасчёты приходят общим чтением состава
      case "accountingRegister", "calculationRegister" -> readRegisterChildren(objectNode, dto);
      default -> {
      }
    }
    readNamedChildren(objectNode, dto);
    if (GENERIC_SCALAR_KINDS.contains(kind)) {
      readScalars(props, dto);
    }
    if ("functionalOption".equals(kind)) {
      readRefList(props, "getContent", dto);
    }
    if ("functionalOptionsParameter".equals(kind)) {
      readRefList(props, "getUse", dto);
    }
    if ("sequence".equals(kind)) {
      dto.documents = readRefTexts(props, "getDocuments");
      dto.registerRecords = readRefTexts(props, "getRegisterRecords");
    }
    if ("filterCriterion".equals(kind)) {
      readRefList(props, "getContent", dto);
    }
    if ("commonAttribute".equals(kind)) {
      readContentMembers(props, dto);
    }
    return dto;
  }

  /** Состав общего реквизита: ссылка, использование, условное разделение. */
  private static void readContentMembers(Object props, MdObjectPropertiesDto dto) {
    dto.contentMembers = new ArrayList<>();
    Object holder = invokeNoArgOrNull(props, "getContent");
    if (holder == null) {
      return;
    }
    for (Object item : JaxbReflect.<Object>listOptional(holder, "getItem")) {
      String ref = toStringOrEmpty(invokeNoArgOrNull(item, "getMetadata"));
      if (ref.isEmpty()) {
        continue;
      }
      Object use = invokeNoArgOrNull(item, "getUse");
      String mode = use instanceof Enum<?> constant ? constant.name() : toStringOrEmpty(use);
      Object separation = invokeNoArgOrNull(item, "getConditionalSeparation");
      dto.contentMembers.add(
        new MdContentMemberDto(ref, mode, separation == null ? "" : String.valueOf(separation)));
    }
  }

  /** Ссылки MDListType-списка свойства: пустой список, когда свойства нет. */
  private static List<String> readRefTexts(Object props, String getterName) {
    Object holder = invokeNoArgOrNull(props, getterName);
    if (holder == null) {
      return new ArrayList<>();
    }
    return new ArrayList<>(MdListTypeRefs.readItemTexts(JaxbReflect.listOptional(holder, "getItem")));
  }

  /**
   * Список ссылок на объекты метаданных: состав функциональной опции,
   * использование её параметра. Ссылки приходят в contentRefs, панель
   * показывает их той же вкладкой состава, что у подсистемы.
   */
  private static void readRefList(Object props, String getterName, MdObjectPropertiesDto dto) {
    Object holder = invokeNoArgOrNull(props, getterName);
    if (holder == null) {
      return;
    }
    for (Object item : JaxbReflect.<Object>listOptional(holder, "getObject")) {
      if (item instanceof String text && !text.isEmpty()) {
        dto.contentRefs.add(text);
      }
    }
    dto.contentRefs.addAll(MdListTypeRefs.readItemTexts(JaxbReflect.listOptional(holder, "getItem")));
  }

  /**
   * Скалярные свойства по полям JAXB-класса Properties: порядок полей повторяет
   * схему, значения переводятся в переносимый вид - перечисления именами
   * констант, числа строками.
   */
  private static void readScalars(Object props, MdObjectPropertiesDto dto) {
    dto.scalars = new java.util.LinkedHashMap<>();
    dto.scalarMeta = new java.util.LinkedHashMap<>();
    for (java.lang.reflect.Field field : props.getClass().getDeclaredFields()) {
      String name = field.getName();
      String capital = Character.toUpperCase(name.charAt(0)) + name.substring(1);
      if (SCALAR_SKIPPED.contains(capital)) {
        continue;
      }
      Object value;
      try {
        value = props.getClass().getMethod("get" + capital).invoke(props);
      } catch (ReflectiveOperationException e) {
        try {
          value = props.getClass().getMethod("is" + capital).invoke(props);
        } catch (ReflectiveOperationException ignored) {
          continue;
        }
      }
      Class<?> type = field.getType();
      if (type == String.class) {
        dto.scalars.put(capital, value == null ? "" : value);
        dto.scalarMeta.put(capital, new MdScalarPropertyMeta("string", List.of()));
      } else if (type == Boolean.class || type == boolean.class) {
        dto.scalars.put(capital, Boolean.TRUE.equals(value));
        dto.scalarMeta.put(capital, new MdScalarPropertyMeta("boolean", List.of()));
      } else if (Number.class.isAssignableFrom(type)) {
        dto.scalars.put(capital, value == null ? "" : value.toString());
        dto.scalarMeta.put(capital, new MdScalarPropertyMeta("number", List.of()));
      } else if (type.isEnum()) {
        dto.scalars.put(capital, value == null ? "" : ((Enum<?>) value).name());
        List<String> allowed = new ArrayList<>();
        for (Object constant : type.getEnumConstants()) {
          allowed.add(((Enum<?>) constant).name());
        }
        dto.scalarMeta.put(capital, new MdScalarPropertyMeta("enum", allowed));
      }
    }
  }

  /** Пишет скалярные свойства обратно: типы значений берутся из сеттеров. */
  private static void applyScalars(Object props, MdObjectPropertiesDto dto) {
    if (dto.scalars == null) {
      return;
    }
    for (java.util.Map.Entry<String, Object> entry : dto.scalars.entrySet()) {
      String name = entry.getKey();
      Object value = entry.getValue();
      java.lang.reflect.Method setter = null;
      for (java.lang.reflect.Method m : props.getClass().getMethods()) {
        if (m.getName().equals("set" + name) && m.getParameterCount() == 1) {
          setter = m;
          break;
        }
      }
      if (setter == null) {
        continue;
      }
      Class<?> param = setter.getParameterTypes()[0];
      try {
        if (param == String.class) {
          String text = value == null ? "" : String.valueOf(value);
          setter.invoke(props, text.isEmpty() ? null : text);
        } else if (param == Boolean.class || param == boolean.class) {
          setter.invoke(props, Boolean.TRUE.equals(value) || "true".equals(value));
        } else if (param == java.math.BigDecimal.class) {
          String text = value == null ? "" : String.valueOf(value);
          setter.invoke(props, text.isEmpty() ? null : new java.math.BigDecimal(text));
        } else if (param == java.math.BigInteger.class) {
          String text = value == null ? "" : String.valueOf(value);
          setter.invoke(props, text.isEmpty() ? null : new java.math.BigInteger(text));
        } else if (param.isEnum()) {
          String constant = value == null ? "" : String.valueOf(value);
          if (!constant.isEmpty()) {
            @SuppressWarnings({"unchecked", "rawtypes"})
            Object enumValue = Enum.valueOf((Class<? extends Enum>) param, constant);
            setter.invoke(props, enumValue);
          }
        }
      } catch (ReflectiveOperationException | IllegalArgumentException e) {
        throw new IllegalStateException("scalar write failed: " + name, e);
      }
    }
  }

  private static void applySimpleDto(Object objectNode, SchemaVersion version, MdObjectPropertiesDto dto) {
    Object props = invokeNoArg(objectNode, "getProperties");
    if ("enum".equals(dto.kind) && dto.enumeration != null) {
      MdEnumPropertiesBridge.apply(version, props, dto);
      applyEnumValues(objectNode, dto);
      return;
    }
    if ("constant".equals(dto.kind) && dto.constant != null) {
      MdConstantPropertiesBridge.apply(props, dto);
      return;
    }
    if (isReportKind(dto.kind) && dto.report != null) {
      MdReportPropertiesBridge.apply(props, dto);
      return;
    }
    if ("documentJournal".equals(dto.kind) && dto.documentJournal != null) {
      MdDocumentJournalPropertiesBridge.apply(version, props, dto);
      return;
    }
    if ("chartOfCalculationTypes".equals(dto.kind) && dto.chartOfCalculationTypes != null) {
      MdChartOfCalculationTypesPropertiesBridge.apply(version, props, dto);
      applyAttrs(JaxbReflect.get(objectNode, "getChildObjects"), dto);
      return;
    }
    if ("chartOfAccounts".equals(dto.kind) && dto.chartOfAccounts != null) {
      MdChartOfAccountsPropertiesBridge.apply(version, props, dto);
      applyAttrs(JaxbReflect.get(objectNode, "getChildObjects"), dto);
      return;
    }
    if ("businessProcess".equals(dto.kind) && dto.businessProcess != null) {
      MdBusinessProcessPropertiesBridge.apply(version, props, dto);
      applyAttrs(JaxbReflect.get(objectNode, "getChildObjects"), dto);
      return;
    }
    if ("task".equals(dto.kind) && dto.task != null) {
      MdTaskPropertiesBridge.apply(version, props, dto);
      applyAttrs(JaxbReflect.get(objectNode, "getChildObjects"), dto);
      return;
    }
    if ("chartOfCharacteristicTypes".equals(dto.kind) && dto.chartOfCharacteristicTypes != null) {
      MdChartOfCharacteristicTypesPropertiesBridge.apply(version, props, dto);
      applyAttrs(JaxbReflect.get(objectNode, "getChildObjects"), dto);
      return;
    }
    if ("sessionParameter".equals(dto.kind) && dto.sessionParameter != null) {
      MdSessionParameterPropertiesBridge.apply(props, dto);
      return;
    }
    if ("documentNumerator".equals(dto.kind) && dto.documentNumerator != null) {
      MdDocumentNumeratorPropertiesBridge.apply(props, dto);
      return;
    }
    if ("eventSubscription".equals(dto.kind) && dto.eventSubscription != null) {
      MdEventSubscriptionPropertiesBridge.apply(props, dto);
      return;
    }
    if ("scheduledJob".equals(dto.kind) && dto.scheduledJob != null) {
      MdScheduledJobPropertiesBridge.apply(props, dto);
      return;
    }
    if ("commonCommand".equals(dto.kind) && dto.commonCommand != null) {
      MdCommonCommandPropertiesBridge.apply(props, dto);
      return;
    }
    if ("commonAttribute".equals(dto.kind) && dto.commonAttribute != null) {
      MdCommonAttributePropertiesBridge.apply(props, dto);
      return;
    }
    if ("commonPicture".equals(dto.kind) && dto.commonPicture != null) {
      MdCommonPicturePropertiesBridge.apply(props, dto);
      return;
    }
    if ("role".equals(dto.kind) && dto.role != null) {
      MdRolePropertiesBridge.apply(props, dto);
      return;
    }
    if ("externalDataSource".equals(dto.kind) && dto.externalDataSource != null) {
      MdExternalDataSourcePropertiesBridge.apply(props, dto);
      return;
    }
    if ("commonModule".equals(dto.kind) && dto.commonModule != null) {
      MdCommonModulePropertiesBridge.apply(props, dto);
      return;
    }
    if (("informationRegister".equals(dto.kind) || "accumulationRegister".equals(dto.kind)) && dto.register != null) {
      MdRegisterPropertiesBridge.apply(version, props, dto);
      applyRegisterChildren(objectNode, dto);
      return;
    }
    String currentName = toStringOrEmpty(invokeNoArg(props, "getName"));
    if (!dto.internalName.equals(currentName)) {
      throw new IllegalArgumentException("internalName mismatch with XML");
    }
    String syn = dto.synonymRu == null ? "" : dto.synonymRu;
    writeLocalStringRu(props, syn);
    invokeSetterString(props, "setComment", dto.comment == null ? "" : dto.comment);
    if (GENERIC_SCALAR_KINDS.contains(dto.kind)) {
      applyScalars(props, dto);
    }
  }

  private static void readRegisterChildren(Object objectNode, MdObjectPropertiesDto dto) {
    Object childObjects = invokeNoArgOrNull(objectNode, "getChildObjects");
    if (childObjects == null) {
      return;
    }
    for (Object dimension : JaxbReflect.<Object>list(childObjects, "getDimension")) {
      dto.dimensions.add(namedDto(dimension));
    }
    for (Object resource : JaxbReflect.<Object>list(childObjects, "getResource")) {
      dto.resources.add(namedDto(resource));
    }
    for (Object attribute : JaxbReflect.<Object>list(childObjects, "getAttribute")) {
      dto.attributes.add(namedDto(attribute));
    }
  }

  private static void applyRegisterChildren(Object objectNode, MdObjectPropertiesDto dto) {
    Object childObjects = invokeNoArgOrNull(objectNode, "getChildObjects");
    if (childObjects == null) {
      return;
    }
    applyNamedList(JaxbReflect.list(childObjects, "getDimension"), dto.dimensions, "dimension");
    applyNamedList(JaxbReflect.list(childObjects, "getResource"), dto.resources, "resource");
    applyNamedList(JaxbReflect.list(childObjects, "getAttribute"), dto.attributes, "attribute");
  }

  private static void applyNamedList(List<Object> nodes, List<MdNamedPropertyDto> dtos, String label) {
    validateNamed(dtos, nodes, label);
    for (int i = 0; i < nodes.size(); i++) {
      applyNamedDto(nodes.get(i), dtos.get(i), label);
    }
  }

  private static void readEnumValues(Object objectNode, MdObjectPropertiesDto dto) {
    Object childObjects = invokeNoArgOrNull(objectNode, "getChildObjects");
    if (childObjects == null) {
      return;
    }
    for (Object value : JaxbReflect.<Object>list(childObjects, "getEnumValue")) {
      dto.enumValues.add(namedDto(value));
    }
  }

  private static void applyEnumValues(Object objectNode, MdObjectPropertiesDto dto) {
    Object childObjects = invokeNoArgOrNull(objectNode, "getChildObjects");
    if (childObjects == null) {
      return;
    }
    List<Object> values = JaxbReflect.list(childObjects, "getEnumValue");
    validateNamed(dto.enumValues, values, "enumValue");
    for (int i = 0; i < values.size(); i++) {
      applyNamedDto(values.get(i), dto.enumValues.get(i), "enum value");
    }
  }

  private static String readLocalStringRu(Object localString) {
    if (localString == null) {
      return "";
    }
    Object items = invokeNoArgOrNull(localString, "getItem");
    if (!(items instanceof List<?> list)) {
      return "";
    }
    for (Object item : list) {
      if ("ru".equals(toStringOrEmpty(invokeNoArgOrNull(item, "getLang")))) {
        return toStringOrEmpty(invokeNoArgOrNull(item, "getContent"));
      }
    }
    return "";
  }

  private static void writeLocalStringRu(Object props, String value) {
    Object localString = invokeNoArgOrNull(props, "getSynonym");
    Method getSynonym;
    try {
      getSynonym = props.getClass().getMethod("getSynonym");
    } catch (NoSuchMethodException e) {
      throw new IllegalStateException("synonym accessor not found", e);
    }
    if (localString == null) {
      localString = newInstance(getSynonym.getReturnType());
      invokeSetter(props, "setSynonym", getSynonym.getReturnType(), localString);
    }
    Object itemsObj = invokeNoArg(localString, "getItem");
    if (!(itemsObj instanceof List<?>)) {
      throw new IllegalStateException("local string items list not found");
    }
    @SuppressWarnings("unchecked")
    List<Object> items = (List<Object>) itemsObj;
    for (Object item : items) {
      if ("ru".equals(toStringOrEmpty(invokeNoArgOrNull(item, "getLang")))) {
        invokeSetterString(item, "setContent", value);
        return;
      }
    }
    Class<?> itemType = resolveLocalStringItemType(localString, items);
    Object newItem = newInstance(itemType);
    invokeSetterString(newItem, "setLang", "ru");
    invokeSetterString(newItem, "setContent", value);
    items.add(newItem);
  }

  private static Class<?> resolveLocalStringItemType(Object localString, List<Object> items) {
    if (!items.isEmpty()) {
      return items.get(0).getClass();
    }
    try {
      Method m = localString.getClass().getMethod("getItem");
      Type generic = m.getGenericReturnType();
      if (generic instanceof ParameterizedType p) {
        Type arg = p.getActualTypeArguments()[0];
        if (arg instanceof Class<?> c) {
          return c;
        }
      }
    } catch (NoSuchMethodException e) {
      throw new IllegalStateException("local string getItem not found", e);
    }
    throw new IllegalStateException("local string item type cannot be resolved");
  }

  private static Object invokeNoArg(Object target, String methodName) {
    Object value = invokeNoArgOrNull(target, methodName);
    if (value == null) {
      throw new IllegalStateException("method returned null: " + methodName);
    }
    return value;
  }

  private static Object invokeNoArgOrNull(Object target, String methodName) {
    try {
      Method method = target.getClass().getMethod(methodName);
      return method.invoke(target);
    } catch (NoSuchMethodException e) {
      return null;
    } catch (InvocationTargetException | IllegalAccessException e) {
      throw new IllegalStateException("invoke failed: " + methodName, e);
    }
  }

  private static void invokeSetterString(Object target, String methodName, String value) {
    invokeSetter(target, methodName, String.class, value);
  }

  private static void invokeSetter(Object target, String methodName, Class<?> argType, Object value) {
    try {
      Method m = target.getClass().getMethod(methodName, argType);
      m.invoke(target, value);
    } catch (NoSuchMethodException e) {
      throw new IllegalStateException("setter not found: " + methodName, e);
    } catch (InvocationTargetException | IllegalAccessException e) {
      throw new IllegalStateException("setter failed: " + methodName, e);
    }
  }

  private static Object newInstance(Class<?> type) {
    try {
      return type.getDeclaredConstructor().newInstance();
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException("cannot create instance of " + type.getName(), e);
    }
  }

  private static String toStringOrEmpty(Object value) {
    return value == null ? "" : String.valueOf(value);
  }

  /** Отчёт и обработка описываются одним DTO: наборы свойств совпадают, кроме полей отчёта. */
  static boolean isReportKind(String kind) {
    return "report".equals(kind) || "dataProcessor".equals(kind);
  }

  private static SimpleKindDef simpleKindByName(String kind) {
    for (SimpleKindDef def : SIMPLE_KINDS) {
      if (def.kind.equals(kind)) {
        return def;
      }
    }
    return null;
  }

  private record SimpleKindDef(String kind, String getterName) {
  }
}
