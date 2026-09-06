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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import javax.xml.stream.XMLStreamException;

/**
 * Точечная замена прямых дочерних элементов {@code Properties} в исходной UTF-8 строке без пересборки всего блока.
 */
public final class MdObjectPropertiesGranularPatch {

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
    return tryApply(xmlUtf8, containerLocal, version, baseline, incoming, null);
  }

  /**
   * @param extendable правимые свойства заимствованных узлов по элементам выгрузки: объект под
   *     пустым ключом; {@code null}, если проверка не нужна
   */
  public static Optional<byte[]> tryApply(
    String xmlUtf8,
    String containerLocal,
    SchemaVersion version,
    MdObjectPropertiesDto baseline,
    MdObjectPropertiesDto incoming,
    Map<String, List<String>> extendable) {
    if (baseline == null || incoming == null || containerLocal == null || containerLocal.isEmpty()) {
      return Optional.empty();
    }
    List<MdObjectPropertiesLeafDiff.GranularPatchChange> changes =
      MdObjectPropertiesLeafDiff.computePropertyChanges(baseline, incoming);
    if (changes.isEmpty()) {
      return Optional.empty();
    }
    List<XmlGranularPatch.Replacement> reps = new ArrayList<>();
    // Изменённые свойства заимствованных узлов: объект под пустым ключом, подчинённые под своим
    Map<String, Set<String>> extended = new LinkedHashMap<>();
    try {
      AdoptedStates.Frame root = AdoptedStates.scan(xmlUtf8.getBytes(StandardCharsets.UTF_8));
      for (MdObjectPropertiesLeafDiff.GranularPatchChange ch : changes) {
        String nodeKey = ch.isNamedChildObject()
          ? ch.namedChildContainerLocal() + ":" + ch.namedChildObjectInternalName()
          : "";
        AdoptedStates.Frame frame = ch.isNamedChildObject() ? root.children.get(nodeKey) : root;
        if (frame != null && frame.adopted()) {
          if (ch.mdElementLocalName().equals("Type")) {
            throw new IllegalArgumentException(
              "Тип заимствованного реквизита правится в расширяемой конфигурации: " + ch.namedChildObjectInternalName());
          }
          List<String> allowed = extendable == null
            ? null
            : extendable.get(ch.isNamedChildObject() ? ch.namedChildContainerLocal() : "");
          if (allowed != null && AdoptedStates.stateful(ch.mdElementLocalName())
            && !allowed.contains(AdoptedStates.key(ch.mdElementLocalName()))) {
            throw new IllegalArgumentException(
              "Свойство " + ch.mdElementLocalName() + " заимствованного узла принадлежит расширяемой конфигурации.");
          }
          if (AdoptedStates.recorded(ch.mdElementLocalName())) {
            extended.computeIfAbsent(nodeKey, key -> new LinkedHashSet<>()).add(ch.mdElementLocalName());
          }
        }
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
          MdObjectXmlRegions.Region propertiesRegion = propertiesRegion(xmlUtf8, containerLocal, ch);
          if (!propertiesRegion.isValid()) {
            return Optional.empty();
          }
          String owner = ch.isNamedChildObject() ? ch.namedChildContainerLocal() : containerLocal;
          AdoptedStatesPatch.Insertion place = AdoptedStatesPatch.insertionPoint(
            xmlUtf8, propertiesRegion, AdoptedStatesPatch.propertyOrder(version, owner), ch.mdElementLocalName());
          String insertion = XmlGranularPatch.formatInsertion(xmlUtf8, place.indent(), ch.replacementElementXml())
            + XmlGranularPatch.fileEol(xmlUtf8);
          reps.add(new XmlGranularPatch.Replacement(place.at(), place.at(), insertion));
          continue;
        }
        String replacement = XmlGranularPatch.formatReplacementPreservingIndent(
          xmlUtf8,
          reg.start(),
          XmlGranularPatch.dropRedundantNamespaces(xmlUtf8, ch.replacementElementXml()));
        reps.add(new XmlGranularPatch.Replacement(reg.start(), reg.end(), replacement));
      }
      for (Map.Entry<String, Set<String>> entry : extended.entrySet()) {
        String key = entry.getKey();
        MdObjectXmlRegions.Region node = key.isEmpty()
          ? MdObjectXmlRegions.findObjectRegion(xmlUtf8, containerLocal)
          : MdObjectXmlRegions.findNamedChildObjectRegion(
            xmlUtf8, containerLocal, key.substring(0, key.indexOf(':')), key.substring(key.indexOf(':') + 1));
        if (!node.isValid()) {
          return Optional.empty();
        }
        reps.add(AdoptedStatesPatch.extended(xmlUtf8, node, entry.getValue()));
      }
    } catch (XMLStreamException e) {
      return Optional.empty();
    }
    Optional<List<XmlGranularPatch.Replacement>> safe = XmlGranularPatch.withoutOverlaps(reps);
    if (safe.isEmpty()) {
      return Optional.empty();
    }
    byte[] bytes = XmlGranularPatch.apply(xmlUtf8, safe.get()).getBytes(StandardCharsets.UTF_8);
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
      case "documentJournal" -> "DocumentJournal";
      case "report" -> "Report";
      case "dataProcessor" -> "DataProcessor";
      case "task" -> "Task";
      case "businessProcess" -> "BusinessProcess";
      case "chartOfAccounts" -> "ChartOfAccounts";
      case "chartOfCharacteristicTypes" -> "ChartOfCharacteristicTypes";
      case "chartOfCalculationTypes" -> "ChartOfCalculationTypes";
      case "commonModule" -> "CommonModule";
      case "informationRegister" -> "InformationRegister";
      case "accumulationRegister" -> "AccumulationRegister";
      case "exchangePlan" -> "ExchangePlan";
      case "subsystem" -> "Subsystem";
      case "sessionParameter" -> "SessionParameter";
      case "commonAttribute" -> "CommonAttribute";
      case "commonPicture" -> "CommonPicture";
      case "documentNumerator" -> "DocumentNumerator";
      case "eventSubscription" -> "EventSubscription";
      case "scheduledJob" -> "ScheduledJob";
      case "commonCommand" -> "CommonCommand";
      case "externalDataSource" -> "ExternalDataSource";
      case "role" -> "Role";
      // Контейнер совпадает с именем корневого элемента выгрузки: без него
      // запись отвечала «не удалось применить изменения точечно» у любого
      // вида из общего чтения
      case "externalReport" -> "ExternalReport";
      case "externalDataProcessor" -> "ExternalDataProcessor";
      case "form" -> "Form";
      case "template" -> "Template";
      case "commonForm" -> "CommonForm";
      case "commonTemplate" -> "CommonTemplate";
      case "webService" -> "WebService";
      case "httpService" -> "HTTPService";
      case "integrationService" -> "IntegrationService";
      case "filterCriterion" -> "FilterCriterion";
      case "settingsStorage" -> "SettingsStorage";
      case "functionalOption" -> "FunctionalOption";
      case "functionalOptionsParameter" -> "FunctionalOptionsParameter";
      case "definedType" -> "DefinedType";
      case "commandGroup" -> "CommandGroup";
      case "xdtoPackage" -> "XDTOPackage";
      case "wsReference" -> "WSReference";
      case "style" -> "Style";
      case "styleItem" -> "StyleItem";
      case "language" -> "Language";
      case "interface" -> "Interface";
      case "bot" -> "Bot";
      case "webSocketClient" -> "WebSocketClient";
      case "sequence" -> "Sequence";
      case "accountingRegister" -> "AccountingRegister";
      case "calculationRegister" -> "CalculationRegister";
      default -> "";
    };
  }

  /** Границы {@code Properties} узла, которому принадлежит правка. */
  private static MdObjectXmlRegions.Region propertiesRegion(
    String xmlUtf8,
    String containerLocal,
    MdObjectPropertiesLeafDiff.GranularPatchChange ch) throws XMLStreamException {
    if (!ch.isNamedChildObject()) {
      return MdObjectXmlRegions.findPropertiesRegion(xmlUtf8, containerLocal);
    }
    MdObjectXmlRegions.Region child = MdObjectXmlRegions.findNamedChildObjectRegion(
      xmlUtf8, containerLocal, ch.namedChildContainerLocal(), ch.namedChildObjectInternalName());
    if (!child.isValid()) {
      return child;
    }
    for (AdoptedStatesPatch.Element element : AdoptedStatesPatch.children(xmlUtf8, child)) {
      if (element.name().equals("Properties")) {
        return new MdObjectXmlRegions.Region(element.start(), element.end());
      }
    }
    return new MdObjectXmlRegions.Region(-1, -1);
  }
}
