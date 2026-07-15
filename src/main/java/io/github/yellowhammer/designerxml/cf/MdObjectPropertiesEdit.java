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
    new SimpleKindDef("commonCommand", "getCommonCommand")
  );

  private MdObjectPropertiesEdit() {
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
    if ("document".equals(kind)) {
      MdDocumentPropertiesBridge.read(version, props, dto);
    }
    Object co = JaxbReflect.get(node, "getChildObjects");
    if (co != null) {
      for (Object a : JaxbReflect.<Object>list(co, "getAttribute")) {
        dto.attributes.add(namedDto(a));
      }
      for (Object ts : JaxbReflect.<Object>list(co, "getTabularSection")) {
        dto.tabularSections.add(namedDto(ts));
      }
    }
    return dto;
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
        applyCatalogLike(JaxbReflect.get(ep, "getProperties"), dto);
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
      case "commonModule" -> MdCommonModulePropertiesBridge.read(props, dto);
      case "informationRegister", "accumulationRegister" -> {
        MdRegisterPropertiesBridge.read(version, props, dto);
        readRegisterChildren(objectNode, dto);
      }
      default -> {
      }
    }
    return dto;
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
