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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.yellowhammer.edt.EdtDcsTemplates;
import io.github.yellowhammer.edt.EdtModel;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

/**
 * Макеты СКД проекта 1С:EDT ({@link EdtDcsTemplates}): блок {@code templates} владельца и общий
 * макет со ссылкой в составе конфигурации. Форма блока сверяется с файлами, которые записала сама
 * 1С:EDT (фикстура ssl31-edt), содержимое {@code Template.dcs} - байты эталона платформы без BOM,
 * UUID детерминированные: повтор операций даёт те же байты.
 */
class DcsTemplateEdtTest {

  private static final String REPORT = "_ДемоФайлыВспомогательный";

  private static final String REPORT_TEMPLATE = "ПустаяСхемаКомпоновкиДанных";

  /** Общий макет СКД, записанный 1С:EDT: образец формы .mdo общего макета. */
  private static final String COMMON_PROTO = "ДанныеПечатиОбщиеРеквизиты";

  /** Блок синонима внутри блока макета: новый макет создаётся без синонима, как пишет 1С:EDT. */
  private static final Pattern TEMPLATE_SYNONYM = Pattern.compile(
    "(?m)^    <synonym>\\r?\\n(?:.*\\r?\\n)*?    </synonym>\\r?\\n");

  /** Блок синонима верхнего уровня в .mdo общего макета. */
  private static final Pattern OBJECT_SYNONYM = Pattern.compile(
    "(?m)^  <synonym>\\r?\\n(?:.*\\r?\\n)*?  </synonym>\\r?\\n");

  @TempDir
  Path workspace;

  @Test
  void удалениеИПовторноеДобавлениеВозвращаютБлокФикстуры() throws Exception {
    Path owner = copyReport(true);
    String fixture = Files.readString(owner, StandardCharsets.UTF_8);
    EdtModel model = EdtModel.bundled();

    EdtDcsTemplates.remove(owner, model, REPORT_TEMPLATE);

    String removed = Files.readString(owner, StandardCharsets.UTF_8);
    // Блок макета и основная схема уходят целиком: пустое свойство 1С:EDT не пишет
    assertThat(removed).doesNotContain("<templates").doesNotContain("mainDataCompositionSchema");
    assertThat(Files.exists(owner.getParent().resolve("Templates").resolve(REPORT_TEMPLATE))).isFalse();

    EdtDcsTemplates.add(owner, model, REPORT_TEMPLATE, true);

    // Форма блока и место основной схемы - как у 1С:EDT; новый макет без синонима
    String expected = TEMPLATE_SYNONYM.matcher(fixture).replaceFirst("");
    assertThat(GoldenSnapshots.normalizeUuids(Files.readString(owner, StandardCharsets.UTF_8)))
      .isEqualTo(GoldenSnapshots.normalizeUuids(expected));
    byte[] content = Files.readAllBytes(
      owner.getParent().resolve("Templates").resolve(REPORT_TEMPLATE).resolve("Template.dcs"));
    assertThat(content).isEqualTo(contentWithoutBom());
    assertThat(new String(content, StandardCharsets.UTF_8)).startsWith("<?xml").contains("\r\n");

    // Повтор операций даёт те же байты: UUID выводятся из правки и файла
    byte[] first = Files.readAllBytes(owner);
    EdtDcsTemplates.remove(owner, model, REPORT_TEMPLATE);
    EdtDcsTemplates.add(owner, model, REPORT_TEMPLATE, true);
    assertThat(Files.readAllBytes(owner)).isEqualTo(first);
  }

  @Test
  void общийМакетСоздаётсяПоКонвенциямEdtИУдаляетсяБезСледа() throws Exception {
    Path configuration = copyConfiguration();
    byte[] before = Files.readAllBytes(configuration);
    EdtModel model = EdtModel.bundled();

    EdtDcsTemplates.add(configuration, model, "МакетСКД", false);

    Path dir = workspace.resolve("src").resolve("CommonTemplates").resolve("МакетСКД");
    // Форма .mdo - как у общего макета СКД, записанного 1С:EDT (без синонима)
    String proto = GoldenSnapshots.read(edtFixture("src/CommonTemplates/" + COMMON_PROTO + "/" + COMMON_PROTO + ".mdo"));
    String expected = OBJECT_SYNONYM.matcher(proto).replaceFirst("")
      .replace(COMMON_PROTO, "МакетСКД");
    assertThat(GoldenSnapshots.normalizeUuids(GoldenSnapshots.read(dir.resolve("МакетСКД.mdo"))))
      .isEqualTo(GoldenSnapshots.normalizeUuids(expected));
    assertThat(Files.readAllBytes(dir.resolve("Template.dcs"))).isEqualTo(contentWithoutBom());
    // Ссылка встаёт последней среди своего вида
    String withReference = Files.readString(configuration, StandardCharsets.UTF_8);
    assertThat(withReference).contains("<commonTemplates>CommonTemplate.МакетСКД</commonTemplates>");
    assertThat(withReference.indexOf("CommonTemplate.МакетСКД"))
      .isGreaterThan(withReference.indexOf("CommonTemplate.fresh"));

    EdtDcsTemplates.remove(configuration, model, "МакетСКД");

    assertThat(Files.readAllBytes(configuration)).isEqualTo(before);
    assertThat(Files.exists(dir)).isFalse();
  }

  @Test
  void отказыПовторяютПравилаКонфигуратора() throws Exception {
    Path owner = copyReport(false);
    EdtModel model = EdtModel.bundled();

    assertThatThrownBy(() -> EdtDcsTemplates.add(owner, model, REPORT_TEMPLATE, false))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("уже объявлен");
    assertThatThrownBy(() -> EdtDcsTemplates.remove(owner, model, "НетТакого"))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("Макет не найден");

    // Табличный документ 1С:EDT пишет без templateType: это не схема компоновки
    Path processor = copy(
      edtFixture("src/DataProcessors/_ДемоГенерацияШтрихкода/_ДемоГенерацияШтрихкода.mdo"),
      workspace.resolve("DataProcessors").resolve("_ДемоГенерацияШтрихкода")
        .resolve("_ДемоГенерацияШтрихкода.mdo"));
    assertThatThrownBy(() -> EdtDcsTemplates.remove(processor, model, "Макет"))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("не схема компоновки");
    assertThatThrownBy(() -> EdtDcsTemplates.add(processor, model, "НовыйМакет", true))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("нет свойства mainDataCompositionSchema");

    Path module = copy(
      edtFixture("src/CommonModules/ZipАрхивы/ZipАрхивы.mdo"),
      workspace.resolve("CommonModules").resolve("ZipАрхивы").resolve("ZipАрхивы.mdo"));
    assertThatThrownBy(() -> EdtDcsTemplates.add(module, model, "Макет", false))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("нет макетов");

    Path configuration = copyConfiguration();
    assertThatThrownBy(() -> EdtDcsTemplates.add(configuration, model, "Макет", true))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("нет свойства mainDataCompositionSchema");
    assertThatThrownBy(() -> EdtDcsTemplates.remove(configuration, model, "НетТакого"))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("Макет не найден");
  }

  @Test
  void каналApplyMutationПравитПроектEdt() throws Exception {
    Path owner = copyReport(true);
    EdtModel model = EdtModel.bundled();

    assertThat(applyMutation(java.util.Map.of(
      "op", "cf-dcs-template-remove", "objectXml", owner.toString(), "name", REPORT_TEMPLATE))).isZero();
    assertThat(Files.readString(owner, StandardCharsets.UTF_8)).doesNotContain("<templates");

    assertThat(applyMutation(java.util.Map.of(
      "op", "cf-dcs-template-add", "objectXml", owner.toString(), "name", REPORT_TEMPLATE,
      "makeMain", true))).isZero();
    String added = Files.readString(owner, StandardCharsets.UTF_8);
    assertThat(added).contains("<templateType>DataCompositionSchema</templateType>")
      .contains("<mainDataCompositionSchema>Report." + REPORT + ".Template." + REPORT_TEMPLATE
        + "</mainDataCompositionSchema>");
    // Канал и прямой вызов делают одно и то же: детерминированные UUID
    Path direct = workspace.resolve("прямой").resolve(REPORT + ".mdo");
    Files.createDirectories(direct.getParent());
    Files.copy(owner, direct);
    EdtDcsTemplates.remove(direct, model, REPORT_TEMPLATE);
    EdtDcsTemplates.add(direct, model, REPORT_TEMPLATE, true);
    assertThat(Files.readAllBytes(direct)).isEqualTo(Files.readAllBytes(owner));
  }

  /** Запуск канала apply-mutation с параметрами из UTF-8 JSON-файла. */
  private int applyMutation(java.util.Map<String, ?> params) throws Exception {
    Path paramsFile = Files.createTempFile(workspace, "params", ".json");
    Files.writeString(paramsFile, new com.google.gson.Gson().toJson(params), StandardCharsets.UTF_8);
    return new picocli.CommandLine(new io.github.yellowhammer.designerxml.cli.DesignerXmlCli())
      .execute("apply-mutation", "--params", paramsFile.toString());
  }

  /** Файл фикстуры ssl31-edt: выгрузка той же конфигурации, записанная 1С:EDT. */
  private static Path edtFixture(String relative) {
    return Path.of(System.getProperty("fixtures.ssl31edt.root"), "ssl31").resolve(relative);
  }

  /** Копия отчёта фикстуры: описание и, по выбору, каталог макета. */
  private Path copyReport(boolean withTemplate) throws Exception {
    Path owner = copy(
      edtFixture("src/Reports/" + REPORT + "/" + REPORT + ".mdo"),
      workspace.resolve("Reports").resolve(REPORT).resolve(REPORT + ".mdo"));
    if (withTemplate) {
      copy(
        edtFixture("src/Reports/" + REPORT + "/Templates/" + REPORT_TEMPLATE + "/Template.dcs"),
        owner.getParent().resolve("Templates").resolve(REPORT_TEMPLATE).resolve("Template.dcs"));
    }
    return owner;
  }

  private Path copyConfiguration() throws Exception {
    return copy(
      edtFixture("src/Configuration/Configuration.mdo"),
      workspace.resolve("src").resolve("Configuration").resolve("Configuration.mdo"));
  }

  /** Байты эталона пустой схемы без BOM: содержимое Template.dcs, как его пишет 1С:EDT. */
  private static byte[] contentWithoutBom() throws Exception {
    byte[] golden = DcsTemplateScaffold.templateContentBytes();
    byte[] withoutBom = new byte[golden.length - 3];
    System.arraycopy(golden, 3, withoutBom, 0, withoutBom.length);
    return withoutBom;
  }

  private static Path copy(Path source, Path target) throws Exception {
    Files.createDirectories(target.getParent());
    Files.copy(source, target);
    return target;
  }
}
