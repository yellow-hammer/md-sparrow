/*
 * This file is a part of md-sparrow.
 *
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package io.github.yellowhammer.designerxml.cf;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Ядро scaffold: параметризация эталона голого объекта (имя как целый токен + детерминированный ремап UUID).
 */
class GoldenObjectTemplateTest {

  private static final String GOLDEN =
    "<MetaDataObject>\n"
      + "\t<Catalog uuid=\"aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa\">\n"
      + "\t\t<InternalInfo>\n"
      + "\t\t\t<xr:GeneratedType name=\"CatalogObject.Справочник1\" category=\"Object\">\n"
      + "\t\t\t\t<xr:TypeId>bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb</xr:TypeId>\n"
      + "\t\t\t\t<xr:ValueId>cccccccc-cccc-cccc-cccc-cccccccccccc</xr:ValueId>\n"
      + "\t\t\t</xr:GeneratedType>\n"
      + "\t\t</InternalInfo>\n"
      + "\t\t<Properties>\n"
      + "\t\t\t<Name>Справочник1</Name>\n"
      + "\t\t\t<InputByString>\n"
      + "\t\t\t\t<xr:Field>Catalog.Справочник1.StandardAttribute.Code</xr:Field>\n"
      + "\t\t\t</InputByString>\n"
      + "\t\t\t<Comment>см. Справочник11</Comment>\n"
      + "\t\t</Properties>\n"
      + "\t</Catalog>\n"
      + "</MetaDataObject>";

  @Test
  void substitutesNameAsWholeTokenEverywhere() {
    String out = GoldenObjectTemplate.parametrize(GOLDEN, "Справочник1", "МойСправочник", "seed");
    assertThat(out).contains("<Name>МойСправочник</Name>");
    assertThat(out).contains("name=\"CatalogObject.МойСправочник\"");
    assertThat(out).contains("Catalog.МойСправочник.StandardAttribute.Code");
    assertThat(out).doesNotContain("<Name>Справочник1<");
    assertThat(out).doesNotContain("CatalogObject.Справочник1");
  }

  @Test
  void doesNotTouchLongerNameSharingPrefix() {
    String out = GoldenObjectTemplate.parametrize(GOLDEN, "Справочник1", "МойСправочник", "seed");
    // "Справочник11" — другой токен, не должен задеться.
    assertThat(out).contains("Справочник11");
  }

  @Test
  void remapsAllUuidsDeterministicallyAndDistinctly() {
    String out = GoldenObjectTemplate.parametrize(GOLDEN, "Справочник1", "МойСправочник", "seedX");
    assertThat(out).doesNotContain("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    assertThat(out).doesNotContain("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    assertThat(out).doesNotContain("cccccccc-cccc-cccc-cccc-cccccccccccc");
    String again = GoldenObjectTemplate.parametrize(GOLDEN, "Справочник1", "МойСправочник", "seedX");
    assertThat(again).isEqualTo(out);
    String otherSeed = GoldenObjectTemplate.parametrize(GOLDEN, "Справочник1", "МойСправочник", "seedY");
    assertThat(otherSeed).isNotEqualTo(out);
  }

  @Test
  void rejectsEmptyNames() {
    assertThatThrownBy(() -> GoldenObjectTemplate.parametrize(GOLDEN, "Справочник1", "", "seed"))
      .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> GoldenObjectTemplate.parametrize(GOLDEN, "", "Имя", "seed"))
      .isInstanceOf(IllegalArgumentException.class);
  }
}
