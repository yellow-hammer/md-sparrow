/*
 * This file is a part of md-sparrow.
 *
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package io.github.yellowhammer.designerxml.cf;

import io.github.yellowhammer.designerxml.DesignerXml;
import io.github.yellowhammer.designerxml.SchemaVersion;
import io.github.yellowhammer.designerxml.reflect.JaxbReflect;

import jakarta.xml.bind.JAXBException;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Имена объектов из {@code Configuration/ChildObjects} по локальному имени тега (как в XSD).
 * Версионно-нейтрально: навигация по JAXB-объекту через {@link JaxbReflect}.
 */
public final class ConfigurationChildObjectLister {

  private ConfigurationChildObjectLister() {
  }

  private static final Set<String> SUPPORTED_TAGS = Set.of(
    "Catalog", "Document", "Enum", "Constant", "Report", "DataProcessor", "Task",
    "ChartOfAccounts", "ChartOfCharacteristicTypes", "ChartOfCalculationTypes", "CommonModule",
    "Subsystem", "SessionParameter", "ExchangePlan", "CommonAttribute", "CommonPicture",
    "DocumentNumerator", "ExternalDataSource", "Role",
    "InformationRegister", "AccumulationRegister", "AccountingRegister", "CalculationRegister");

  /**
   * @param childTag например {@code Catalog}, {@code Document}, {@code Enum}, {@code Constant}
   */
  public static List<String> listNames(Path configurationXml, SchemaVersion version, String childTag)
    throws JAXBException, IOException {
    Objects.requireNonNull(childTag, "childTag");
    if (!SUPPORTED_TAGS.contains(childTag)) {
      throw new IllegalArgumentException("unsupported ChildObjects tag: " + childTag);
    }
    Object mdo = JaxbReflect.value(DesignerXml.read(configurationXml, version));
    Object cfg = JaxbReflect.get(mdo, "getConfiguration");
    if (cfg == null) {
      throw new IllegalStateException("Configuration.xml has no Configuration element");
    }
    Object child = JaxbReflect.get(cfg, "getChildObjects");
    if (child == null) {
      return new ArrayList<>();
    }
    List<String> raw = new ArrayList<>(JaxbReflect.<String>list(child, "get" + childTag));
    return sortedCopy(raw);
  }

  private static List<String> sortedCopy(List<String> raw) {
    List<String> sorted = new ArrayList<>(raw);
    Collections.sort(sorted);
    return sorted;
  }
}
