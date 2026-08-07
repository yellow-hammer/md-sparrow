/*
 * This file is a part of md-sparrow.
 *
 * SPDX-License-Identifier: LGPL-3.0-or-later
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
