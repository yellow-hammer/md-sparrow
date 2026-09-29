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
package io.github.yellowhammer.edt;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import io.github.yellowhammer.designerxml.cf.MdObjectAddType;

/**
 * Формы проекта EDT по формату проекта.
 *
 * Эталон - проекты, которые записала 1С:EDT при импорте выгрузки конфигуратора, по одному на каждый
 * вариант записи формы: общая форма, пустая форма справочника, форма с элементами и командой,
 * собранные в выгрузке теми же правками, и форма внешнего отчёта. Те же формы заводятся рядом и
 * должны совпасть с записью 1С:EDT с точностью до переводов строк.
 */
class EdtFormPlatformTest {

  /** Элементы формы в том порядке, в каком их добавляли в выгрузке. */
  private static final List<String> ITEMS = List.of(
      "{\"group\": \"ГруппаВертикальная\", \"title\": \"Заголовок\", \"direction\": \"vertical\", \"items\": ["
          + "{\"check\": \"Флажок\", \"dataPath\": \"Объект.DeletionMark\"},"
          + "{\"input\": \"Наименование\", \"dataPath\": \"Объект.Description\"}]}",
      "{\"group\": \"ГруппаГоризонтальная\", \"direction\": \"horizontal\", \"items\": ["
          + "{\"input\": \"Код\", \"dataPath\": \"Объект.Code\"}]}",
      "{\"pages\": \"Страницы\", \"items\": ["
          + "{\"page\": \"Страница\", \"title\": \"Страница\", \"items\": ["
          + "{\"label\": \"Надпись\", \"title\": \"Текст\"}]},"
          + "{\"page\": \"СтраницаБезЗаголовка\"}]}",
      "{\"table\": \"Товары\", \"dataPath\": \"Объект.Товары\", \"items\": ["
          + "{\"input\": \"ТоварыНоменклатура\", \"dataPath\": \"Объект.Товары.Номенклатура\"}]}");

  private static EdtModel model;
  private static Path fixture;

  @TempDir
  Path workDir;

  @BeforeAll
  static void locate() throws IOException {
    model = EdtModel.bundled();
    fixture = Path.of("src", "test", "resources", "edt-forms-platform").toAbsolutePath();
  }

  @ParameterizedTest
  @ValueSource(strings = {"8.3.17", "8.3.20", "8.3.27", "8.5.1"})
  void формыСовпадаютСЗаписьюEdtСвоегоФормата(String platform) throws Exception {
    Path root = workDir.resolve(platform);
    EdtExtensionScaffoldTest.copy(fixture.resolve(platform), root);
    Path src = root.resolve("Формы/src");
    Path catalog = src.resolve("Catalogs/Спр/Спр.mdo");

    EdtObjectScaffold.add(src.resolve("Configuration/Configuration.mdo"), model, MdObjectAddType.COMMON_FORM,
        "НоваяОбщаяФорма");
    assertSameText(src.resolve("CommonForms/НоваяОбщаяФорма/Form.form"),
        src.resolve("CommonForms/ОбщаяФорма/Form.form"));

    Path forms = src.resolve("Catalogs/Спр/Forms");
    EdtObjectScaffold.addForm(catalog, model, "НоваяФорма");
    assertSameText(forms.resolve("НоваяФорма/Form.form"), forms.resolve("Форма/Form.form"));

    EdtObjectScaffold.addForm(catalog, model, "НоваяФормаСЭлементами");
    Path form = forms.resolve("НоваяФормаСЭлементами/Form.form");
    EdtFormCompositionEdit.addAttribute(form, model,
        "{\"name\": \"Объект\", \"type\": {\"types\": [\"cfg:CatalogObject.Спр\"]}}");
    for (String item : ITEMS) {
      EdtFormItemStructureEdit.add(form, model, null, null, item);
    }
    EdtFormCompositionEdit.addCommand(form, model, "{\"name\": \"Заполнить\", \"title\": \"Заполнить\"}");
    assertSameText(form, forms.resolve("ФормаСЭлементами/Form.form"));

    Path report = root.resolve("ВнешнийОтчет/src/ExternalReports/ВнешнийОтчет");
    EdtObjectScaffold.addForm(report.resolve("ВнешнийОтчет.mdo"), model, "НоваяФорма");
    assertSameText(report.resolve("Forms/НоваяФорма/Form.form"), report.resolve("Forms/Форма/Form.form"));
  }

  /** Манифесты проектов, которые записала 1С:EDT. */
  @ParameterizedTest
  @CsvSource({
    "8.3.16, V2_10", "8.3.17, V2_10", "8.3.18, V2_10", "8.3.19, V2_10",
    "8.3.20, V2_13", "8.3.21, V2_13", "8.3.22, V2_13",
    "8.3.23, V2_16", "8.3.24, V2_16", "8.3.25, V2_16", "8.3.26, V2_16", "8.3.27, V2_16",
    "8.5.1, V2_21"})
  void записьПоФорматуПроекта(String runtime, EdtFormPlatform expected) throws Exception {
    assertThat(EdtFormPlatform.ofProject(fixture.resolve("runtime").resolve(runtime))).isEqualTo(expected);
  }

  @Test
  void безМанифестаЗаписьСамогоНовогоФормата() throws Exception {
    assertThat(EdtFormPlatform.ofProject(fixture.resolve("runtime"))).isEqualTo(EdtFormPlatform.V2_21);
  }

  private static void assertSameText(Path actual, Path expected) throws IOException {
    assertThat(read(actual)).as(actual.toString()).isEqualTo(read(expected));
  }

  private static String read(Path file) throws IOException {
    return Files.readString(file, StandardCharsets.UTF_8).replace("\r\n", "\n");
  }
}
