/*
 * This file is a part of md-sparrow.
 *
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package io.github.yellowhammer.designerxml.cf;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.yellowhammer.designerxml.Ssl31SubmodulePaths;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

/**
 * Шапка объекта читается одним проходом: дереву нужны идентификатор,
 * принадлежность и синоним, а файл объекта большой.
 */
class ObjectHeadTest {

  private static Path ssl31(String... parts) {
    Path path = Ssl31SubmodulePaths.projectRoot();
    for (String part : parts) {
      path = path.resolve(part);
    }
    return path;
  }

  @Test
  void объектКонфигурацииОтдаётИдентификаторИСиноним() {
    ObjectHead.Head head = ObjectHead.read(ssl31("src", "cf", "Catalogs", "_ДемоБанковскиеСчета.xml"));

    assertThat(head.uuid()).isNotBlank();
    assertThat(head.objectBelonging()).isNull();
    assertThat(head.synonym()).containsKey("ru");
    assertThat(head.synonym().get("ru")).isNotBlank();
  }

  @Test
  void заимствованныйОбъектРасширенияПомеченПринадлежностью() {
    ObjectHead.Head head = ObjectHead.read(
      ssl31("src", "cfe", "_ДемоРасширение", "Catalogs", "_ДемоПартнеры.xml"));

    assertThat(head.objectBelonging()).isEqualTo("Adopted");
  }

  @Test
  void синонимБерётсяУсамогоОбъекта() {
    // Дальше в файле идут синонимы реквизитов и форм: до них чтение не доходит
    ObjectHead.Head head = ObjectHead.read(ssl31("src", "cf", "Catalogs", "_ДемоБанковскиеСчета.xml"));

    assertThat(head.synonym().get("ru")).doesNotContain("Форма");
  }

  @Test
  void файлаНетИшапкаПустая() {
    ObjectHead.Head head = ObjectHead.read(ssl31("src", "cf", "нет-такого.xml"));

    assertThat(head).isEqualTo(ObjectHead.Head.EMPTY);
  }
}
