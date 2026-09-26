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
package io.github.yellowhammer.designerxml.cf;

import io.github.yellowhammer.designerxml.SchemaVersion;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Вид объекта из CLI: известный разбирается в любом написании, прочий отклоняется со списком допустимых.
 */
class MdObjectAddTypeTest {

  @Test
  void видыСоставаРазбираютсяВЛюбомНаписании() {
    for (MdObjectAddType type : MdObjectAddType.values()) {
      String cli = cliName(type.configurationXmlTag());
      assertThat(MdObjectAddType.fromCliName(cli)).isEqualTo(type);
      assertThat(MdObjectAddType.fromCliName(cli.toLowerCase(Locale.ROOT).replace('_', '-'))).isEqualTo(type);
    }
  }

  @Test
  void видСоставаКоторыйНеСоздаётсяОтклоняетсяСоСпискомДопустимых() throws Exception {
    SchemaVersion latest = SchemaVersion.values()[SchemaVersion.values().length - 1];
    Set<String> supported = Arrays.stream(MdObjectAddType.values())
      .map(MdObjectAddType::configurationXmlTag)
      .collect(Collectors.toSet());
    List<String> refused = ChildNodeOp.schemaChildren(latest, "Configuration").stream()
      .filter(kind -> !supported.contains(kind))
      .map(MdObjectAddTypeTest::cliName)
      .toList();

    assertThat(refused).isNotEmpty();
    for (String cli : refused) {
      assertThatThrownBy(() -> MdObjectAddType.fromCliName(cli))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining(cli)
        .hasMessageContainingAll(Arrays.stream(MdObjectAddType.values()).map(Enum::name).toArray(String[]::new));
    }
  }

  @Test
  void безВидаОтказСоСпискомДопустимых() {
    assertThatThrownBy(() -> MdObjectAddType.fromCliName(" "))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining(MdObjectAddType.values()[0].name());
  }

  /** {@code ChartOfAccounts} -&gt; {@code CHART_OF_ACCOUNTS}. */
  private static String cliName(String tag) {
    return tag.replaceAll("([a-z])([A-Z])", "$1_$2").toUpperCase(Locale.ROOT);
  }
}
