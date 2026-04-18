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

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Извлекает плоский список ссылок из {@code Configuration/ChildObjects} через JAXB (порядок — как в XSD:
 * блоки по типу в порядке {@code sequence}).
 */
public final class ConfigurationChildObjectsExtractor {

  private ConfigurationChildObjectsExtractor() {
  }

  /**
   * @param configurationXml путь к {@code Configuration.xml}
   * @param version          версия XSD/JAXB (должна совпадать с выгрузкой)
   */
  public static List<ChildObjectEntry> readChildObjects(Path configurationXml, SchemaVersion version)
    throws JAXBException, IOException {
    return switch (version) {
      case V2_20 -> readV20(configurationXml);
      case V2_21 -> readV21(configurationXml);
    };
  }

  private static List<ChildObjectEntry> readV20(Path configurationXml) throws JAXBException, IOException {
    Object root = DesignerXml.read(configurationXml, SchemaVersion.V2_20);
    io.github.yellowhammer.designerxml.jaxb.v2_20.mdclasses.MetaDataObject mdo = unwrapV20(root);
    io.github.yellowhammer.designerxml.jaxb.v2_20.mdclasses.Configuration cfg = mdo.getConfiguration();
    if (cfg == null) {
      throw new IOException("В Configuration.xml нет элемента Configuration");
    }
    var child = cfg.getChildObjects();
    if (child == null) {
      return List.of();
    }
    List<ChildObjectEntry> out = new ArrayList<>();
    append(out, "Language", child.getLanguage());
    append(out, "Subsystem", child.getSubsystem());
    append(out, "StyleItem", child.getStyleItem());
    append(out, "Style", child.getStyle());
    append(out, "CommonPicture", child.getCommonPicture());
    append(out, "Interface", child.getInterface());
    append(out, "SessionParameter", child.getSessionParameter());
    append(out, "Role", child.getRole());
    append(out, "CommonTemplate", child.getCommonTemplate());
    append(out, "FilterCriterion", child.getFilterCriterion());
    append(out, "CommonModule", child.getCommonModule());
    append(out, "CommonAttribute", child.getCommonAttribute());
    append(out, "ExchangePlan", child.getExchangePlan());
    append(out, "XDTOPackage", child.getXDTOPackage());
    append(out, "WebService", child.getWebService());
    append(out, "HTTPService", child.getHTTPService());
    append(out, "WSReference", child.getWSReference());
    append(out, "EventSubscription", child.getEventSubscription());
    append(out, "ScheduledJob", child.getScheduledJob());
    append(out, "SettingsStorage", child.getSettingsStorage());
    append(out, "FunctionalOption", child.getFunctionalOption());
    append(out, "FunctionalOptionsParameter", child.getFunctionalOptionsParameter());
    append(out, "DefinedType", child.getDefinedType());
    append(out, "CommonCommand", child.getCommonCommand());
    append(out, "CommandGroup", child.getCommandGroup());
    append(out, "Constant", child.getConstant());
    append(out, "CommonForm", child.getCommonForm());
    append(out, "Catalog", child.getCatalog());
    append(out, "Document", child.getDocument());
    append(out, "DocumentNumerator", child.getDocumentNumerator());
    append(out, "Sequence", child.getSequence());
    append(out, "DocumentJournal", child.getDocumentJournal());
    append(out, "Enum", child.getEnum());
    append(out, "Report", child.getReport());
    append(out, "DataProcessor", child.getDataProcessor());
    append(out, "InformationRegister", child.getInformationRegister());
    append(out, "AccumulationRegister", child.getAccumulationRegister());
    append(out, "ChartOfCharacteristicTypes", child.getChartOfCharacteristicTypes());
    append(out, "ChartOfAccounts", child.getChartOfAccounts());
    append(out, "AccountingRegister", child.getAccountingRegister());
    append(out, "ChartOfCalculationTypes", child.getChartOfCalculationTypes());
    append(out, "CalculationRegister", child.getCalculationRegister());
    append(out, "BusinessProcess", child.getBusinessProcess());
    append(out, "Task", child.getTask());
    append(out, "ExternalDataSource", child.getExternalDataSource());
    append(out, "IntegrationService", child.getIntegrationService());
    append(out, "Bot", child.getBot());
    append(out, "WebSocketClient", child.getWebSocketClient());
    return out;
  }

  private static List<ChildObjectEntry> readV21(Path configurationXml) throws JAXBException, IOException {
    Object root = DesignerXml.read(configurationXml, SchemaVersion.V2_21);
    io.github.yellowhammer.designerxml.jaxb.v2_21.mdclasses.MetaDataObject mdo = unwrapV21(root);
    io.github.yellowhammer.designerxml.jaxb.v2_21.mdclasses.Configuration cfg = mdo.getConfiguration();
    if (cfg == null) {
      throw new IOException("В Configuration.xml нет элемента Configuration");
    }
    var child = cfg.getChildObjects();
    if (child == null) {
      return List.of();
    }
    List<ChildObjectEntry> out = new ArrayList<>();
    append(out, "Language", child.getLanguage());
    append(out, "Subsystem", child.getSubsystem());
    append(out, "StyleItem", child.getStyleItem());
    append(out, "Style", child.getStyle());
    append(out, "CommonPicture", child.getCommonPicture());
    append(out, "Interface", child.getInterface());
    append(out, "SessionParameter", child.getSessionParameter());
    append(out, "Role", child.getRole());
    append(out, "CommonTemplate", child.getCommonTemplate());
    append(out, "FilterCriterion", child.getFilterCriterion());
    append(out, "CommonModule", child.getCommonModule());
    append(out, "CommonAttribute", child.getCommonAttribute());
    append(out, "ExchangePlan", child.getExchangePlan());
    append(out, "XDTOPackage", child.getXDTOPackage());
    append(out, "WebService", child.getWebService());
    append(out, "HTTPService", child.getHTTPService());
    append(out, "WSReference", child.getWSReference());
    append(out, "EventSubscription", child.getEventSubscription());
    append(out, "ScheduledJob", child.getScheduledJob());
    append(out, "SettingsStorage", child.getSettingsStorage());
    append(out, "FunctionalOption", child.getFunctionalOption());
    append(out, "FunctionalOptionsParameter", child.getFunctionalOptionsParameter());
    append(out, "DefinedType", child.getDefinedType());
    append(out, "CommonCommand", child.getCommonCommand());
    append(out, "CommandGroup", child.getCommandGroup());
    append(out, "Constant", child.getConstant());
    append(out, "CommonForm", child.getCommonForm());
    append(out, "Catalog", child.getCatalog());
    append(out, "Document", child.getDocument());
    append(out, "DocumentNumerator", child.getDocumentNumerator());
    append(out, "Sequence", child.getSequence());
    append(out, "DocumentJournal", child.getDocumentJournal());
    append(out, "Enum", child.getEnum());
    append(out, "Report", child.getReport());
    append(out, "DataProcessor", child.getDataProcessor());
    append(out, "InformationRegister", child.getInformationRegister());
    append(out, "AccumulationRegister", child.getAccumulationRegister());
    append(out, "ChartOfCharacteristicTypes", child.getChartOfCharacteristicTypes());
    append(out, "ChartOfAccounts", child.getChartOfAccounts());
    append(out, "AccountingRegister", child.getAccountingRegister());
    append(out, "ChartOfCalculationTypes", child.getChartOfCalculationTypes());
    append(out, "CalculationRegister", child.getCalculationRegister());
    append(out, "BusinessProcess", child.getBusinessProcess());
    append(out, "Task", child.getTask());
    append(out, "ExternalDataSource", child.getExternalDataSource());
    append(out, "IntegrationService", child.getIntegrationService());
    append(out, "Bot", child.getBot());
    append(out, "WebSocketClient", child.getWebSocketClient());
    append(out, "PaletteColor", child.getPaletteColor());
    return out;
  }

  private static void append(List<ChildObjectEntry> out, String xmlTag, List<String> values) {
    if (values == null) {
      return;
    }
    for (String s : values) {
      if (s == null) {
        continue;
      }
      String t = s.trim();
      if (!t.isEmpty()) {
        out.add(new ChildObjectEntry(xmlTag, t));
      }
    }
  }

  private static io.github.yellowhammer.designerxml.jaxb.v2_20.mdclasses.MetaDataObject unwrapV20(
    Object root) {
    if (root instanceof JAXBElement<?> je
      && je.getValue()
        instanceof io.github.yellowhammer.designerxml.jaxb.v2_20.mdclasses.MetaDataObject m) {
      return m;
    }
    if (root instanceof io.github.yellowhammer.designerxml.jaxb.v2_20.mdclasses.MetaDataObject m) {
      return m;
    }
    throw new IllegalArgumentException("Ожидался MetaDataObject (2.20), получено: " + root.getClass());
  }

  private static io.github.yellowhammer.designerxml.jaxb.v2_21.mdclasses.MetaDataObject unwrapV21(
    Object root) {
    if (root instanceof JAXBElement<?> je
      && je.getValue()
        instanceof io.github.yellowhammer.designerxml.jaxb.v2_21.mdclasses.MetaDataObject m) {
      return m;
    }
    if (root instanceof io.github.yellowhammer.designerxml.jaxb.v2_21.mdclasses.MetaDataObject m) {
      return m;
    }
    throw new IllegalArgumentException("Ожидался MetaDataObject (2.21), получено: " + root.getClass());
  }
}
