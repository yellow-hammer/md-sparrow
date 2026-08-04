/*
 * This file is a part of md-sparrow.
 *
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package io.github.yellowhammer.designerxml.cf;

import io.github.yellowhammer.designerxml.SchemaVersion;
import io.github.yellowhammer.designerxml.cf.FormItemPropertyDictionary.FormItemPropertyDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Состав свойств видов элементов формы снимается с модели формата. */
class FormItemPropertyDictionaryTest {

  private static FormItemPropertyDto property(List<FormItemPropertyDto> properties, String name) {
    return properties.stream().filter(p -> name.equals(p.name)).findFirst().orElseThrow(
      () -> new AssertionError("нет свойства " + name));
  }

  @Test
  void отдаётПолныйСоставСвойствПоляВвода() {
    List<FormItemPropertyDto> properties = FormItemPropertyDictionary.forVersion(SchemaVersion.V2_20).get("InputField");

    assertThat(properties).hasSizeGreaterThan(50);
    assertThat(property(properties, "DataPath").kind).isEqualTo("string");
    assertThat(property(properties, "Visible").defaultValue).isEqualTo("true");
    assertThat(property(properties, "Title").kind).isEqualTo("localString");
    assertThat(property(properties, "TitleLocation").values).contains("Auto", "None");
  }

  @Test
  void составФормыСвойствомНеСчитается() {
    Map<String, List<FormItemPropertyDto>> dictionary = FormItemPropertyDictionary.forVersion(SchemaVersion.V2_20);

    for (Map.Entry<String, List<FormItemPropertyDto>> kind : dictionary.entrySet()) {
      assertThat(kind.getValue()).describedAs(kind.getKey()).noneMatch(
        property -> dictionary.containsKey(property.name)
          || "ChildItems".equals(property.name)
          || "Events".equals(property.name));
    }
  }

  @ParameterizedTest
  @EnumSource(SchemaVersion.class)
  void словарьСобираетсяДляВсехВерсий(SchemaVersion version) {
    Map<String, List<FormItemPropertyDto>> dictionary = FormItemPropertyDictionary.forVersion(version);

    assertThat(dictionary).containsKeys("InputField", "UsualGroup", "Table", "Button", "AutoCommandBar");
    assertThat(dictionary.get("UsualGroup")).extracting(p -> p.name).contains("Group", "Title", "Visible");
  }
}
