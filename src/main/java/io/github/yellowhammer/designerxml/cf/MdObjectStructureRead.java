/*
 * This file is a part of md-sparrow.
 *
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package io.github.yellowhammer.designerxml.cf;

import io.github.yellowhammer.designerxml.DesignerXml;
import io.github.yellowhammer.designerxml.SchemaVersion;

import jakarta.xml.bind.JAXBElement;
import jakarta.xml.bind.JAXBException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Чтение структуры объекта метаданных (реквизиты, ТЧ, формы, команды, макеты).
 */
public final class MdObjectStructureRead {

  private static final List<KindDef> KINDS = List.of(
    new KindDef("catalog", "getCatalog"),
    new KindDef("constant", "getConstant"),
    new KindDef("enum", "getEnum"),
    new KindDef("document", "getDocument"),
    new KindDef("documentJournal", "getDocumentJournal"),
    new KindDef("report", "getReport"),
    new KindDef("dataProcessor", "getDataProcessor"),
    new KindDef("externalReport", "getExternalReport"),
    new KindDef("externalDataProcessor", "getExternalDataProcessor"),
    new KindDef("task", "getTask"),
    new KindDef("chartOfAccounts", "getChartOfAccounts"),
    new KindDef("chartOfCharacteristicTypes", "getChartOfCharacteristicTypes"),
    new KindDef("chartOfCalculationTypes", "getChartOfCalculationTypes"),
    new KindDef("informationRegister", "getInformationRegister"),
    new KindDef("accumulationRegister", "getAccumulationRegister"),
    new KindDef("accountingRegister", "getAccountingRegister"),
    new KindDef("calculationRegister", "getCalculationRegister"),
    new KindDef("businessProcess", "getBusinessProcess"),
    new KindDef("commonModule", "getCommonModule"),
    new KindDef("subsystem", "getSubsystem"),
    new KindDef("sessionParameter", "getSessionParameter"),
    new KindDef("exchangePlan", "getExchangePlan"),
    new KindDef("filterCriterion", "getFilterCriterion"),
    new KindDef("eventSubscription", "getEventSubscription"),
    new KindDef("scheduledJob", "getScheduledJob"),
    new KindDef("functionalOption", "getFunctionalOption"),
    new KindDef("functionalOptionsParameter", "getFunctionalOptionsParameter"),
    new KindDef("definedType", "getDefinedType"),
    new KindDef("settingsStorage", "getSettingsStorage"),
    new KindDef("commonCommand", "getCommonCommand"),
    new KindDef("commandGroup", "getCommandGroup"),
    new KindDef("commonForm", "getCommonForm"),
    new KindDef("commonTemplate", "getCommonTemplate"),
    new KindDef("commonAttribute", "getCommonAttribute"),
    new KindDef("commonPicture", "getCommonPicture"),
    new KindDef("xdtoPackage", "getXDTOPackage"),
    new KindDef("webService", "getWebService"),
    new KindDef("httpService", "getHTTPService"),
    new KindDef("interface", "getInterface"),
    new KindDef("wsReference", "getWSReference"),
    new KindDef("webSocketClient", "getWebSocketClient"),
    new KindDef("integrationService", "getIntegrationService"),
    new KindDef("bot", "getBot"),
    new KindDef("styleItem", "getStyleItem"),
    new KindDef("style", "getStyle"),
    new KindDef("language", "getLanguage"),
    new KindDef("paletteColor", "getPaletteColor"),
    new KindDef("documentNumerator", "getDocumentNumerator"),
    new KindDef("sequence", "getSequence"),
    new KindDef("externalDataSource", "getExternalDataSource"),
    new KindDef("role", "getRole")
  );

  private MdObjectStructureRead() {
  }

  public static MdObjectStructureDto read(Path objectXml, SchemaVersion version) throws IOException, JAXBException {
    if (!Files.isRegularFile(objectXml)) {
      throw new IllegalArgumentException("file not found: " + objectXml);
    }
    Object root = DesignerXml.read(objectXml, version);
    if (!(root instanceof JAXBElement<?> je)) {
      throw new IllegalArgumentException("expected JAXBElement root");
    }
    return readFromRoot(je);
  }

  public static MdObjectStructureDto read(byte[] utf8Xml, SchemaVersion version) throws JAXBException {
    Object root = DesignerXml.unmarshal(version, new ByteArrayInputStream(utf8Xml));
    if (!(root instanceof JAXBElement<?> je)) {
      throw new IllegalArgumentException("expected JAXBElement root");
    }
    return readFromRoot(je);
  }

  private static MdObjectStructureDto readFromRoot(JAXBElement<?> je) {
    Object metaDataObject = je.getValue();
    KindHandle handle = resolveKind(metaDataObject);
    if (handle == null) {
      throw new IllegalArgumentException("unsupported MetaDataObject for cf-md-object-structure");
    }
    Object props = invokeNoArg(handle.objectNode, "getProperties");
    MdObjectStructureDto dto = new MdObjectStructureDto();
    dto.kind = handle.kind;
    dto.internalName = safeString(invokeNoArgOrNull(props, "getName"));

    Object childObjects = invokeNoArgOrNull(handle.objectNode, "getChildObjects");
    if (childObjects == null) {
      return dto;
    }

    List<Object> attributes = listOrEmpty(invokeNoArgOrNull(childObjects, "getAttribute"));
    for (Object attr : attributes) {
      dto.attributes.add(readNamedNode(attr));
    }

    List<Object> tabularSections = listOrEmpty(invokeNoArgOrNull(childObjects, "getTabularSection"));
    for (Object ts : tabularSections) {
      MdObjectStructureDto.MdTabularSectionDto tsDto = new MdObjectStructureDto.MdTabularSectionDto();
      Object tsProps = invokeNoArg(ts, "getProperties");
      tsDto.name = safeString(invokeNoArgOrNull(tsProps, "getName"));
      tsDto.synonymRu = readLocalStringRu(invokeNoArgOrNull(tsProps, "getSynonym"));
      tsDto.comment = safeString(invokeNoArgOrNull(tsProps, "getComment"));
      Object tsChildObjects = invokeNoArgOrNull(ts, "getChildObjects");
      if (tsChildObjects != null) {
        List<Object> tsAttributes = listOrEmpty(invokeNoArgOrNull(tsChildObjects, "getAttribute"));
        for (Object tsAttr : tsAttributes) {
          tsDto.attributes.add(readNamedNode(tsAttr));
        }
      }
      dto.tabularSections.add(tsDto);
    }

    dto.forms.addAll(readStringItems(listOrEmpty(invokeNoArgOrNull(childObjects, "getForm"))));
    dto.commands.addAll(readStringItems(listOrEmpty(invokeNoArgOrNull(childObjects, "getCommand"))));
    dto.templates.addAll(readStringItems(listOrEmpty(invokeNoArgOrNull(childObjects, "getTemplate"))));
    dto.values.addAll(readStringItems(listOrEmpty(invokeNoArgOrNull(childObjects, "getEnumValue"))));
    dto.columns.addAll(readStringItems(listOrEmpty(invokeNoArgOrNull(childObjects, "getColumn"))));
    dto.accountingFlags.addAll(readStringItems(listOrEmpty(invokeNoArgOrNull(childObjects, "getAccountingFlag"))));
    dto.extDimensionAccountingFlags.addAll(readStringItems(
      listOrEmpty(invokeNoArgOrNull(childObjects, "getExtDimensionAccountingFlag"))));
    dto.dimensions.addAll(readStringItems(listOrEmpty(invokeNoArgOrNull(childObjects, "getDimension"))));
    dto.resources.addAll(readStringItems(listOrEmpty(invokeNoArgOrNull(childObjects, "getResource"))));
    dto.recalculations.addAll(readStringItems(listOrEmpty(invokeNoArgOrNull(childObjects, "getRecalculation"))));
    dto.addressingAttributes.addAll(readStringItems(
      listOrEmpty(invokeNoArgOrNull(childObjects, "getAddressingAttribute"))));
    dto.operations.addAll(readStringItems(listOrEmpty(invokeNoArgOrNull(childObjects, "getOperation"))));
    dto.urlTemplates.addAll(readStringItems(listOrEmpty(invokeNoArgOrNull(childObjects, "getURLTemplate"))));
    dto.channels.addAll(readStringItems(listOrEmpty(invokeNoArgOrNull(childObjects, "getChannel"))));
    dto.tables.addAll(readStringItems(listOrEmpty(invokeNoArgOrNull(childObjects, "getTable"))));
    dto.cubes.addAll(readStringItems(listOrEmpty(invokeNoArgOrNull(childObjects, "getCube"))));
    dto.functions.addAll(readStringItems(listOrEmpty(invokeNoArgOrNull(childObjects, "getFunction"))));
    return dto;
  }

  private static MdObjectStructureDto.MdNodeDto readNamedNode(Object node) {
    Object props = invokeNoArg(node, "getProperties");
    return new MdObjectStructureDto.MdNodeDto(
      safeString(invokeNoArgOrNull(props, "getName")),
      readLocalStringRu(invokeNoArgOrNull(props, "getSynonym")),
      safeString(invokeNoArgOrNull(props, "getComment")));
  }

  static KindHandle resolveKind(Object metaDataObject) {
    for (KindDef kindDef : KINDS) {
      Object objectNode = invokeNoArgOrNull(metaDataObject, kindDef.getterName);
      if (objectNode != null) {
        return new KindHandle(kindDef.kind, objectNode);
      }
    }
    return null;
  }

  private static String readLocalStringRu(Object localString) {
    if (localString == null) {
      return "";
    }
    List<Object> items = listOrEmpty(invokeNoArgOrNull(localString, "getItem"));
    for (Object item : items) {
      String lang = safeString(invokeNoArgOrNull(item, "getLang"));
      if ("ru".equals(lang)) {
        return safeString(invokeNoArgOrNull(item, "getContent"));
      }
    }
    return "";
  }

  private static List<String> readStringItems(List<Object> raw) {
    List<String> out = new ArrayList<>();
    for (Object item : raw) {
      String value = extractItemName(item);
      if (!value.isBlank()) {
        out.add(value);
      }
    }
    return out;
  }

  private static String extractItemName(Object item) {
    if (item == null) {
      return "";
    }
    if (item instanceof String s) {
      return s;
    }
    Object properties = invokeNoArgOrNull(item, "getProperties");
    if (properties != null) {
      Object nestedName = invokeNoArgOrNull(properties, "getName");
      if (nestedName != null) {
        return safeString(nestedName);
      }
    }
    Object name = invokeNoArgOrNull(item, "getName");
    if (name != null) {
      return safeString(name);
    }
    Object value = invokeNoArgOrNull(item, "getValue");
    if (value != null) {
      return safeString(value);
    }
    Object content = invokeNoArgOrNull(item, "getContent");
    if (content != null) {
      return safeString(content);
    }
    return safeString(item);
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

  private static String safeString(Object value) {
    return value == null ? "" : String.valueOf(value);
  }

  @SuppressWarnings("unchecked")
  private static List<Object> listOrEmpty(Object value) {
    if (value instanceof List<?>) {
      return (List<Object>) value;
    }
    return new ArrayList<>();
  }

  static final class KindHandle {
    final String kind;
    final Object objectNode;

    KindHandle(String kind, Object objectNode) {
      this.kind = kind;
      this.objectNode = objectNode;
    }
  }

  private record KindDef(String kind, String getterName) {
  }
}
