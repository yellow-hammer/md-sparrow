/*
 * This file is a part of md-sparrow.
 *
 * Copyright (c) 2026
 * Ivan Karlo <i.karlo@outlook.com> and contributors
 *
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package io.github.yellowhammer.designerxml.cf;

import io.github.yellowhammer.designerxml.Ssl31SubmodulePaths;
import io.github.yellowhammer.designerxml.SchemaVersion;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Ссылочные типы конфигурации: панель типов берёт их отсюда, своей карты
 * «вид объекта - суффикс ссылки» потребитель не держит.
 */
class ConfigurationRefTypeListerTest {

  private static Map<String, List<String>> read() throws Exception {
    return ConfigurationRefTypeLister.listRefTypes(
      Ssl31SubmodulePaths.projectRoot().resolve("src/cf/Configuration.xml"), SchemaVersion.V2_20);
  }

  @Test
  void listsCatalogRefTypes() throws Exception {
    Map<String, List<String>> types = read();

    assertThat(types).containsKey("Catalog");
    assertThat(types.get("Catalog")).allSatisfy(type -> assertThat(type).startsWith("cfg:CatalogRef."));
    assertThat(types.get("Catalog")).hasSizeGreaterThan(10);
  }

  @Test
  void coversReferenceKindsOnly() throws Exception {
    Map<String, List<String>> types = read();

    assertThat(types).containsKeys("Catalog", "Document", "Enum", "ChartOfCharacteristicTypes");
    // Регистры ссылочного типа не имеют: набор записей типом реквизита не бывает
    assertThat(types).doesNotContainKeys("InformationRegister", "AccumulationRegister");
  }
}
