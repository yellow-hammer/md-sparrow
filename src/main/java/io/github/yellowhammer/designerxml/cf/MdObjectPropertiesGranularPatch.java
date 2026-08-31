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
import java.util.List;
import java.util.Optional;

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
    if (baseline == null || incoming == null || containerLocal == null || containerLocal.isEmpty()) {
      return Optional.empty();
    }
    List<MdObjectPropertiesLeafDiff.GranularPatchChange> changes =
      MdObjectPropertiesLeafDiff.computePropertyChanges(baseline, incoming);
    if (changes.isEmpty()) {
      return Optional.empty();
    }
    List<XmlGranularPatch.Replacement> reps = new ArrayList<>();
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
          String insertion = XmlGranularPatch.fileEol(xmlUtf8)
            + XmlGranularPatch.formatInsertion(
              xmlUtf8,
              XmlGranularPatch.currentLineIndent(xmlUtf8, insertPos) + "\t",
              ch.replacementElementXml());
          reps.add(new XmlGranularPatch.Replacement(insertPos, insertPos, insertion));
          continue;
        }
        String replacement = XmlGranularPatch.formatReplacementPreservingIndent(
          xmlUtf8,
          reg.start(),
          XmlGranularPatch.dropRedundantNamespaces(xmlUtf8, ch.replacementElementXml()));
        reps.add(new XmlGranularPatch.Replacement(reg.start(), reg.end(), replacement));
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

  private static int propertiesClosingTagStart(String xmlUtf8, MdObjectXmlRegions.Region propertiesRegion) {
    return xmlUtf8.lastIndexOf("</", propertiesRegion.end() - 1);
  }
}
