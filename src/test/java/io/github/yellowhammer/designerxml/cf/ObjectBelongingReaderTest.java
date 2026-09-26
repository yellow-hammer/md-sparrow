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

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/** Принадлежность объекта расширения читается потоково, без разбора файла целиком. */
class ObjectBelongingReaderTest {

  private static Path ssl31(String... parts) {
    String root = System.getProperty("fixtures.ssl31.root");
    assertThat(root).isNotBlank();
    return Path.of(root, parts);
  }

  @Test
  void заимствованныйОбъектРасширения() {
    Path object = ssl31("src", "cfe", "_ДемоРасширение", "Catalogs", "_ДемоКонтрагенты.xml");

    assertThat(ObjectBelongingReader.read(object)).isEqualTo("Adopted");
  }

  @Test
  void уОбъектаКонфигурацииПризнакаНет() {
    assertThat(ObjectBelongingReader.read(ssl31("src", "cf", "Catalogs", "Валюты.xml"))).isNull();
  }

  @Test
  void несуществующийФайлНеЛомаетЧтение() {
    assertThat(ObjectBelongingReader.read(ssl31("src", "cf", "нет-такого.xml"))).isNull();
  }
}
