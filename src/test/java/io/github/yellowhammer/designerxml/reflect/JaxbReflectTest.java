/*
 * This file is a part of md-sparrow.
 *
 * Copyright (c) 2026
 * Ivan Karlo <i.karlo@outlook.com> and contributors
 *
 * SPDX-License-Identifier: LGPL-3.0-or-later
 *
 * md-sparrow is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3.0 of the License, or (at your option) any later version.
 *
 * md-sparrow is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with md-sparrow.
 */
package io.github.yellowhammer.designerxml.reflect;

import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlType;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Версионно-нейтральные хелперы рефлексии: строгие методы падают на отсутствующем методе,
 * tolerant-методы (Optional/EnumOrKeep) — пропускают (свойство есть не во всех форматах схем 1С).
 */
class JaxbReflectTest {

  enum Mode {
    A,
    B
  }

  public static final class Sample {
    private String name = "n0";
    private Mode mode = Mode.A;
    private Object child;

    public String getName() {
      return name;
    }

    public void setName(String v) {
      name = v;
    }

    public Mode getMode() {
      return mode;
    }

    public void setMode(Mode m) {
      mode = m;
    }

    public Object getChild() {
      return child;
    }

    public void setChild(Sample c) {
      child = c;
    }
  }

  /** Имитация {@code ChildObjects}: зарезервированное слово Enum → поле {@code _enum}, геттер {@code getEnum()}. */
  @XmlType(propOrder = {"catalog", "_enum"})
  public static final class ChildObjectsSample {
    @XmlElement(name = "Catalog")
    private final List<String> catalog = new ArrayList<>(List.of("Альфа", "Бета"));
    @XmlElement(name = "Enum")
    private final List<String> _enum = new ArrayList<>(List.of("Пол"));

    public List<String> getCatalog() {
      return catalog;
    }

    public List<String> getEnum() {
      return _enum;
    }
  }

  @Test
  void getOptionalReturnsNullForMissingMethodButStrictThrows() {
    Sample s = new Sample();
    assertThat(JaxbReflect.getOptional(s, "getAdditionalIndexes")).isNull();
    assertThat(JaxbReflect.getStringOptional(s, "getAdditionalIndexes")).isNull();
    assertThat(JaxbReflect.getBooleanOptional(s, "isMissing")).isFalse();
    assertThatThrownBy(() -> JaxbReflect.get(s, "getAdditionalIndexes"))
      .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void getOptionalReadsExistingValue() {
    Sample s = new Sample();
    assertThat(JaxbReflect.getStringOptional(s, "getName")).isEqualTo("n0");
  }

  @Test
  void setOptionalSetsWhenPresentAndSkipsWhenAbsent() {
    Sample s = new Sample();
    assertThat(JaxbReflect.setOptional(s, "setName", "n1")).isTrue();
    assertThat(s.getName()).isEqualTo("n1");
    assertThat(JaxbReflect.setOptional(s, "setMissing", "x")).isFalse();
  }

  @Test
  void setEnumOrKeepSetsKeepsAndToleratesAbsentSetter() {
    Sample s = new Sample();
    JaxbReflect.setEnumOrKeep(s, "setMode", "B");
    assertThat(s.getMode()).isEqualTo(Mode.B);
    JaxbReflect.setEnumOrKeep(s, "setMode", "");
    assertThat(s.getMode()).isEqualTo(Mode.B);
    JaxbReflect.setEnumOrKeep(s, "setMissingEnum", "B");
  }

  @Test
  void setEnumOrKeepRejectsUnknownConstantWithAllowedValues() {
    Sample s = new Sample();
    JaxbReflect.setEnumOrKeep(s, "setMode", "B");

    // прежнее значение молча не сохраняем: иначе правка теряется без объяснения
    assertThatThrownBy(() -> JaxbReflect.setEnumOrKeep(s, "setMode", "UNKNOWN"))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("Mode")
      .hasMessageContaining("UNKNOWN")
      .hasMessageContaining("допустимы");
    assertThat(s.getMode()).isEqualTo(Mode.B);
  }

  @Test
  void ensureOptionalCreatesWhenSetterPresentAndNullWhenAbsent() {
    Sample s = new Sample();
    Object created = JaxbReflect.ensureOptional(s, "getChild", "setChild");
    assertThat(created).isInstanceOf(Sample.class);
    assertThat(s.getChild()).isSameAs(created);
    assertThat(JaxbReflect.ensureOptional(s, "getMissing", "setMissing")).isNull();
  }

  @Test
  void enumNameHandlesNull() {
    assertThat(JaxbReflect.enumName(null)).isEmpty();
    Sample s = new Sample();
    assertThat(JaxbReflect.enumName(s, "getMode")).isEqualTo("A");
    assertThat(JaxbReflect.enumNameOptional(s, "getMissing")).isEmpty();
  }

  @Test
  void orderedStringListsUsesXmlTagAndHandlesReservedWordGetter() {
    List<Map.Entry<String, List<String>>> lists = JaxbReflect.orderedStringLists(new ChildObjectsSample());
    assertThat(lists).hasSize(2);
    assertThat(lists.get(0).getKey()).isEqualTo("Catalog");
    assertThat(lists.get(0).getValue()).containsExactly("Альфа", "Бета");
    // Главное: тег Enum (поле _enum) разрешился в геттер getEnum(), а не get_enum().
    assertThat(lists.get(1).getKey()).isEqualTo("Enum");
    assertThat(lists.get(1).getValue()).containsExactly("Пол");
  }
}
