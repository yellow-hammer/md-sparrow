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
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Имена объектов из {@code Configuration/ChildObjects} по локальному имени тега (как в XSD).
 */
public final class ConfigurationChildObjectLister {

  private ConfigurationChildObjectLister() {
  }

  /**
   * @param childTag например {@code Catalog}, {@code Document}, {@code Enum}, {@code Constant}
   */
  public static List<String> listNames(Path configurationXml, SchemaVersion version, String childTag)
    throws JAXBException, IOException {
    Objects.requireNonNull(childTag, "childTag");
    return switch (version) {
      case V2_20 -> listV20(configurationXml, childTag);
      case V2_21 -> listV21(configurationXml, childTag);
    };
  }

  private static List<String> sortedCopy(List<String> raw) {
    List<String> sorted = new ArrayList<>(raw);
    Collections.sort(sorted);
    return sorted;
  }

  private static List<String> listV20(Path configurationXml, String childTag) throws JAXBException, IOException {
    Object root = DesignerXml.read(configurationXml, SchemaVersion.V2_20);
    var mdo = unwrapV20(root);
    var cfg = mdo.getConfiguration();
    if (cfg == null) {
      throw new IllegalStateException("Configuration.xml has no Configuration element");
    }
    var child = cfg.getChildObjects();
    if (child == null) {
      return new ArrayList<>();
    }
    List<String> raw =
      switch (childTag) {
        case "Catalog" -> new ArrayList<>(Objects.requireNonNullElse(child.getCatalog(), List.of()));
        case "Document" -> new ArrayList<>(Objects.requireNonNullElse(child.getDocument(), List.of()));
        case "Enum" -> new ArrayList<>(Objects.requireNonNullElse(child.getEnum(), List.of()));
        case "Constant" -> new ArrayList<>(Objects.requireNonNullElse(child.getConstant(), List.of()));
        case "Report" -> new ArrayList<>(Objects.requireNonNullElse(child.getReport(), List.of()));
        case "DataProcessor" -> new ArrayList<>(Objects.requireNonNullElse(child.getDataProcessor(), List.of()));
        case "Task" -> new ArrayList<>(Objects.requireNonNullElse(child.getTask(), List.of()));
        case "ChartOfAccounts" -> new ArrayList<>(Objects.requireNonNullElse(child.getChartOfAccounts(), List.of()));
        case "ChartOfCharacteristicTypes" ->
          new ArrayList<>(Objects.requireNonNullElse(child.getChartOfCharacteristicTypes(), List.of()));
        case "ChartOfCalculationTypes" ->
          new ArrayList<>(Objects.requireNonNullElse(child.getChartOfCalculationTypes(), List.of()));
        case "CommonModule" -> new ArrayList<>(Objects.requireNonNullElse(child.getCommonModule(), List.of()));
        case "Subsystem" -> new ArrayList<>(Objects.requireNonNullElse(child.getSubsystem(), List.of()));
        case "SessionParameter" -> new ArrayList<>(Objects.requireNonNullElse(child.getSessionParameter(), List.of()));
        case "ExchangePlan" -> new ArrayList<>(Objects.requireNonNullElse(child.getExchangePlan(), List.of()));
        case "CommonAttribute" -> new ArrayList<>(Objects.requireNonNullElse(child.getCommonAttribute(), List.of()));
        case "CommonPicture" -> new ArrayList<>(Objects.requireNonNullElse(child.getCommonPicture(), List.of()));
        case "DocumentNumerator" ->
          new ArrayList<>(Objects.requireNonNullElse(child.getDocumentNumerator(), List.of()));
        case "ExternalDataSource" ->
          new ArrayList<>(Objects.requireNonNullElse(child.getExternalDataSource(), List.of()));
        case "Role" -> new ArrayList<>(Objects.requireNonNullElse(child.getRole(), List.of()));
        default -> throw new IllegalArgumentException("unsupported ChildObjects tag: " + childTag);
      };
    return sortedCopy(raw);
  }

  private static List<String> listV21(Path configurationXml, String childTag) throws JAXBException, IOException {
    Object root = DesignerXml.read(configurationXml, SchemaVersion.V2_21);
    var mdo = unwrapV21(root);
    var cfg = mdo.getConfiguration();
    if (cfg == null) {
      throw new IllegalStateException("Configuration.xml has no Configuration element");
    }
    var child = cfg.getChildObjects();
    if (child == null) {
      return new ArrayList<>();
    }
    List<String> raw =
      switch (childTag) {
        case "Catalog" -> new ArrayList<>(Objects.requireNonNullElse(child.getCatalog(), List.of()));
        case "Document" -> new ArrayList<>(Objects.requireNonNullElse(child.getDocument(), List.of()));
        case "Enum" -> new ArrayList<>(Objects.requireNonNullElse(child.getEnum(), List.of()));
        case "Constant" -> new ArrayList<>(Objects.requireNonNullElse(child.getConstant(), List.of()));
        case "Report" -> new ArrayList<>(Objects.requireNonNullElse(child.getReport(), List.of()));
        case "DataProcessor" -> new ArrayList<>(Objects.requireNonNullElse(child.getDataProcessor(), List.of()));
        case "Task" -> new ArrayList<>(Objects.requireNonNullElse(child.getTask(), List.of()));
        case "ChartOfAccounts" -> new ArrayList<>(Objects.requireNonNullElse(child.getChartOfAccounts(), List.of()));
        case "ChartOfCharacteristicTypes" ->
          new ArrayList<>(Objects.requireNonNullElse(child.getChartOfCharacteristicTypes(), List.of()));
        case "ChartOfCalculationTypes" ->
          new ArrayList<>(Objects.requireNonNullElse(child.getChartOfCalculationTypes(), List.of()));
        case "CommonModule" -> new ArrayList<>(Objects.requireNonNullElse(child.getCommonModule(), List.of()));
        case "Subsystem" -> new ArrayList<>(Objects.requireNonNullElse(child.getSubsystem(), List.of()));
        case "SessionParameter" -> new ArrayList<>(Objects.requireNonNullElse(child.getSessionParameter(), List.of()));
        case "ExchangePlan" -> new ArrayList<>(Objects.requireNonNullElse(child.getExchangePlan(), List.of()));
        case "CommonAttribute" -> new ArrayList<>(Objects.requireNonNullElse(child.getCommonAttribute(), List.of()));
        case "CommonPicture" -> new ArrayList<>(Objects.requireNonNullElse(child.getCommonPicture(), List.of()));
        case "DocumentNumerator" ->
          new ArrayList<>(Objects.requireNonNullElse(child.getDocumentNumerator(), List.of()));
        case "ExternalDataSource" ->
          new ArrayList<>(Objects.requireNonNullElse(child.getExternalDataSource(), List.of()));
        case "Role" -> new ArrayList<>(Objects.requireNonNullElse(child.getRole(), List.of()));
        default -> throw new IllegalArgumentException("unsupported ChildObjects tag: " + childTag);
      };
    return sortedCopy(raw);
  }

  private static io.github.yellowhammer.designerxml.jaxb.v2_20.mdclasses.MetaDataObject unwrapV20(Object root) {
    if (root instanceof JAXBElement<?> je
      && je.getValue()
        instanceof io.github.yellowhammer.designerxml.jaxb.v2_20.mdclasses.MetaDataObject m) {
      return m;
    }
    if (root instanceof io.github.yellowhammer.designerxml.jaxb.v2_20.mdclasses.MetaDataObject m) {
      return m;
    }
    throw new IllegalArgumentException("expected MetaDataObject (2.20), got " + root.getClass());
  }

  private static io.github.yellowhammer.designerxml.jaxb.v2_21.mdclasses.MetaDataObject unwrapV21(Object root) {
    if (root instanceof JAXBElement<?> je
      && je.getValue()
        instanceof io.github.yellowhammer.designerxml.jaxb.v2_21.mdclasses.MetaDataObject m) {
      return m;
    }
    if (root instanceof io.github.yellowhammer.designerxml.jaxb.v2_21.mdclasses.MetaDataObject m) {
      return m;
    }
    throw new IllegalArgumentException("expected MetaDataObject (2.21), got " + root.getClass());
  }
}
