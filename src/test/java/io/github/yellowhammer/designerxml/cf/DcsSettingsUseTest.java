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

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import io.github.yellowhammer.designerxml.SchemaVersion;
import io.github.yellowhammer.designerxml.Ssl31SubmodulePaths;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Флаг использования элементов настроек ({@code dcsset:use}, {@code dcscor:use}) пишется только
 * значением {@code false}: {@code use=true} - умолчание, которое платформа не хранит и при загрузке
 * отбрасывает. Правило одно для всех писателей: выбранные поля, сортировка, отбор, параметры данных и вывода,
 * элементы, поля и значения условного оформления. {@code use: true} снимает записанный {@code false};
 * элемент запроса с {@code use: true} равен прочитанному элементу без {@code use}, поэтому эхо с
 * взведённым флажком окна - байтовый no-op на выгрузке ssl31 и её паре в 1С:EDT.
 */
class DcsSettingsUseTest {

  /** Вариант «Основной»: отбор корня с use=false, параметры данных и вывода с use=false. */
  private static final String ACCOUNTING_CHECK =
    "src/cf/Reports/РезультатыПроверкиУчета/Templates/ОсновнаяСхемаКомпоновкиДанных/Ext/Template.xml";

  /** Вариант «Дополнительный1» без секций: все секции создаются заново. */
  private static final String FILE_DYNAMICS =
    "src/cf/Reports/_ДемоДинамикаИзмененийФайлов/Templates/ОсновнаяСхемаКомпоновкиДанных/Ext/Template.xml";

  private static final String FRESH_VARIANT = "Дополнительный1";

  private static final List<String> NODE_LISTS = List.of("structure", "rows", "columns", "points", "series");

  private static final Gson GSON = new Gson();

  private static final Type MAP_TYPE = new TypeToken<Map<String, Object>>() { }.getType();

  @TempDir Path tempDir;

  // ------------------------------------------------------------------ новые элементы

  @Test
  void useTrueIsNotWrittenByAnyWriter() throws Exception {
    // одни и те же правки с use: true и без use дают один и тот же файл, и в нём нет use=true
    Path flagged = copySchema(FILE_DYNAMICS);
    Path plain = copySchema(FILE_DYNAMICS);
    String before = read(flagged);
    writeAllSections(flagged, true);
    writeAllSections(plain, null);
    String written = read(flagged);
    assertThat(written).isNotEqualTo(before).isEqualTo(read(plain))
      .doesNotContain("<dcsset:use>true</dcsset:use>").doesNotContain("<dcscor:use>true</dcscor:use>");
    assertThat(DcsXsdValidation.validate(flagged)).containsEntry("ok", true);

    // use: false пишется явно - у каждого вида элемента первым по xs:sequence
    Path unused = copySchema(FILE_DYNAMICS);
    writeAllSections(unused, Boolean.FALSE);
    String falses = read(unused);
    // выбранные поля 4, сортировка 2, отбор 3, оформление 2 (элемент и поле); значение оформления
    // и оба параметра - dcscor
    assertThat(count(falses, "<dcsset:use>false</dcsset:use>") - count(before, "<dcsset:use>false</dcsset:use>"))
      .isEqualTo(11);
    assertThat(count(falses, "<dcscor:use>false</dcscor:use>") - count(before, "<dcscor:use>false</dcscor:use>"))
      .isEqualTo(3);
    assertThat(falses).contains("<dcsset:item xsi:type=\"dcsset:SelectedItemAuto\">\r\n"
      + "\t\t\t\t\t<dcsset:use>false</dcsset:use>\r\n");
    assertThat(DcsXsdValidation.validate(unused)).containsEntry("ok", true);
  }

  // ------------------------------------------------------------------ записанный false

  @Test
  void useTrueRemovesWrittenFalseLine() throws Exception {
    Path dcs = copySchema(ACCOUNTING_CHECK);
    String before = read(dcs);

    // отбор корня: у первого сравнения use=false - use: true переписывает элемент без строки use,
    // соседи переносят байты
    List<Map<String, Object>> filter = items(settings(dcs, "Основной"), "filter");
    assertThat(filter.get(0)).containsEntry("use", false).containsEntry("left", "ВажностьПроблемы");
    List<Map<String, Object>> request = mutable(filter);
    request.get(0).put("use", true);
    DcsSettingsEdit.setFilter(dcs, SchemaVersion.V2_20, "Основной", Map.of("path", List.of(), "items", request));
    String filterUsed = removeLineBefore(before, "<dcsset:left xsi:type=\"dcscor:Field\">ВажностьПроблемы",
      "\t\t\t\t\t<dcsset:use>false</dcsset:use>\r\n");
    assertThat(read(dcs)).isEqualTo(filterUsed);
    assertThat(items(settings(dcs, "Основной"), "filter").get(0)).doesNotContainKey("use");

    // эхо перечитанного (без use) и с use: true - ни байта
    DcsSettingsEdit.setFilter(dcs, SchemaVersion.V2_20, "Основной",
      Map.of("path", List.of(), "items", items(settings(dcs, "Основной"), "filter")));
    DcsSettingsEdit.setFilter(dcs, SchemaVersion.V2_20, "Основной", Map.of("path", List.of(), "items", request));
    assertThat(read(dcs)).isEqualTo(filterUsed);

    // use: false возвращает строку на место - байтовый возврат к исходнику
    request.get(0).put("use", false);
    DcsSettingsEdit.setFilter(dcs, SchemaVersion.V2_20, "Основной", Map.of("path", List.of(), "items", request));
    assertThat(read(dcs)).isEqualTo(before);
  }

  // ------------------------------------------------------------------ записанный use=true

  @Test
  void writtenUseTrueSurvivesEcho() throws Exception {
    // записанный в файле use=true читается как есть, и эхо прочитанного его не трогает (платформа
    // снимает флаг при загрузке)
    Path dcs = copySchema(ACCOUNTING_CHECK);
    String responsible = "\r\n\t\t\t\t\t<dcscor:parameter>ВыводитьОтветственного";
    String importance = "\r\n\t\t\t\t\t<dcsset:left xsi:type=\"dcscor:Field\">ВажностьПроблемы";
    String text = read(dcs)
      .replace("<dcscor:use>false</dcscor:use>" + responsible, "<dcscor:use>true</dcscor:use>" + responsible)
      .replace("<dcsset:use>false</dcsset:use>" + importance, "<dcsset:use>true</dcsset:use>" + importance);
    assertThat(count(text, "use>true</")).isEqualTo(2);
    Files.writeString(dcs, text, StandardCharsets.UTF_8);

    List<Map<String, Object>> data = items(settings(dcs, "Основной"), "dataParameters");
    assertThat(byName(data, "ВыводитьОтветственного")).containsEntry("use", true);
    DcsSettingsEdit.setParameterValue(dcs, SchemaVersion.V2_20, "Основной",
      Map.of("section", "data", "parameter", "ВыводитьОтветственного", "use", true));
    List<Map<String, Object>> filter = items(settings(dcs, "Основной"), "filter");
    assertThat(filter.get(0)).containsEntry("use", true);
    DcsSettingsEdit.setFilter(dcs, SchemaVersion.V2_20, "Основной", Map.of("path", List.of(), "items", filter));
    // тот же элемент без use в запросе равен записанному use=true: байты переносятся
    List<Map<String, Object>> withoutUse = mutable(filter);
    withoutUse.get(0).remove("use");
    DcsSettingsEdit.setFilter(dcs, SchemaVersion.V2_20, "Основной", Map.of("path", List.of(), "items", withoutUse));
    assertThat(read(dcs)).isEqualTo(text);

    // use: false - настоящая правка: флаг заменяется на месте
    DcsSettingsEdit.setParameterValue(dcs, SchemaVersion.V2_20, "Основной",
      Map.of("section", "data", "parameter", "ВыводитьОтветственного", "use", false));
    assertThat(read(dcs)).isEqualTo(text.replace(
      "<dcscor:use>true</dcscor:use>" + responsible, "<dcscor:use>false</dcscor:use>" + responsible));
  }

  // ------------------------------------------------------------------ эхо по корпусам

  @Test
  void echoWithUseTrueIsByteNoOpOnBothCorpora() throws Exception {
    // эхо каждого элемента настроек, где флажок окна взведён (use: true там, где use в файле нет),
    // через JSON канала: выбранные поля, отбор, сортировка, параметры данных и вывода, условное
    // оформление - в корне и во всех узлах структуры обоих корпусов
    int[] designer = echoCorpus(schemaFiles(Ssl31SubmodulePaths.projectRoot().resolve("src")), "Template.xml");
    int[] edt = echoCorpus(schemaFiles(Path.of(System.getProperty("fixtures.ssl31edt.root"))), "Template.dcs");
    assertThat(List.of(designer[0], designer[1], edt[0], edt[1])).as("эхо и взведённые флажки")
      .containsExactly(2088, 4135, 2060, 4098);
  }

  // ------------------------------------------------------------------ помощники

  /**
   * Все писатели настроек варианта {@link #FRESH_VARIANT} с одинаковым флагом: выбранные поля
   * (авто, поле, папка с полем), сортировка (поле, авто), отбор (сравнение, группа со сравнением),
   * элемент условного оформления (элемент, поле, значение параметра), параметры данных и вывода.
   *
   * @param use {@code true}, {@code false} либо {@code null} - без ключа use
   */
  private void writeAllSections(Path dcs, Object use) throws Exception {
    List<Map<String, Object>> folderItems = List.of(flag(Map.of("kind", "field", "field", "Автор"), use));
    List<Map<String, Object>> selection = List.of(
      flag(Map.of("kind", "auto"), use),
      flag(Map.of("kind", "field", "field", "Файл"), use),
      flag(Map.of("kind", "folder", "title", "Папка", "items", folderItems), use));
    DcsSettingsEdit.setSelection(dcs, SchemaVersion.V2_20, FRESH_VARIANT, channel(Map.of("path", List.of(),
      "items", selection)));
    DcsSettingsEdit.setOrder(dcs, SchemaVersion.V2_20, FRESH_VARIANT, channel(Map.of("path", List.of(),
      "items", List.of(flag(Map.of("kind", "field", "field", "Файл"), use), flag(Map.of("kind", "auto"), use)))));
    DcsSettingsEdit.setFilter(dcs, SchemaVersion.V2_20, FRESH_VARIANT, channel(Map.of("path", List.of(),
      "items", List.of(
        flag(Map.of("kind", "comparison", "left", "Файл", "comparisonType", "Equal",
          "right", List.of(Map.of("type", "xs:string", "text", "х"))), use),
        flag(Map.of("kind", "group", "groupType", "OrGroup", "items", List.of(
          flag(Map.of("kind", "comparison", "left", "Автор", "comparisonType", "Filled"), use))), use)))));
    DcsSettingsEdit.addAppearanceItem(dcs, SchemaVersion.V2_20, FRESH_VARIANT, channel(Map.of("path", List.of(),
      "item", flag(Map.of(
        "fields", List.of(flag(Map.of("field", "Файл"), use)),
        "appearance", List.of(flag(Map.of("parameter", "ЦветТекста",
          "value", List.of(Map.of("type", "v8ui:Color", "text", "web:Red"))), use))), use))));
    Map<String, Object> data = new LinkedHashMap<>(Map.of("section", "data", "parameter", "Период"));
    data.put("value", null);
    DcsSettingsEdit.setParameterValue(dcs, SchemaVersion.V2_20, FRESH_VARIANT, flag(data, use));
    DcsSettingsEdit.setParameterValue(dcs, SchemaVersion.V2_20, FRESH_VARIANT,
      flag(Map.of("section", "output", "parameter", "Заголовок", "value", "Т"), use));
  }

  /** Копия объекта с флагом use; {@code null} - без ключа. */
  private static Map<String, Object> flag(Map<String, Object> item, Object use) {
    Map<String, Object> out = new LinkedHashMap<>(item);
    if (use != null) {
      out.put("use", use);
    }
    return out;
  }

  /**
   * Эхо элементов настроек каждой схемы с взведённым флагом; файл после всех эхо обязан совпасть
   * байт в байт.
   *
   * @return {отправлено эхом, из них с use: true, которого в файле нет}
   */
  private int[] echoCorpus(List<Path> schemas, String fileName) throws Exception {
    int[] counts = new int[2];
    int index = 0;
    for (Path schema : schemas) {
      Path copy = Files.createTempDirectory(tempDir, "echo" + index++).resolve(fileName);
      Files.copy(schema, copy);
      String before = read(copy);
      for (Map<String, Object> variant : list(DcsSettingsRead.info(copy, SchemaVersion.V2_20, null).get("variants"))) {
        String name = (String) variant.get("name");
        echoNode(copy, name, List.of(), "settings", settings(copy, name), before, counts);
      }
      assertThat(read(copy)).as(schema.toString()).isEqualTo(before);
    }
    return counts;
  }

  /** Эхо секций узла и рекурсивно - его вложенных узлов. */
  private void echoNode(Path dcs, String variant, List<Map<String, Object>> path, String kind,
    Map<String, Object> node, String before, int[] counts) throws Exception {
    String where = dcs.getParent().getFileName() + " " + variant + " " + path;
    boolean filterAndOrder = "settings".equals(kind) || "group".equals(kind);
    for (String section : filterAndOrder ? List.of("selection", "filter", "order") : List.of("selection")) {
      if (!(node.get(section) instanceof Map<?, ?> found) || !(found.get("items") instanceof List<?>)) {
        continue;
      }
      List<Map<String, Object>> request = mutable(list(found.get("items")));
      for (Map<String, Object> item : request) {
        counts[1] += raiseTree(item);
      }
      Map<String, Object> payload = channel(Map.of("path", path, "items", request));
      switch (section) {
        case "selection" -> DcsSettingsEdit.setSelection(dcs, SchemaVersion.V2_20, variant, payload);
        case "filter" -> DcsSettingsEdit.setFilter(dcs, SchemaVersion.V2_20, variant, payload);
        default -> DcsSettingsEdit.setOrder(dcs, SchemaVersion.V2_20, variant, payload);
      }
      assertThat(read(dcs)).as(where + " " + section).isEqualTo(before);
      counts[0]++;
    }
    for (String section : "settings".equals(kind) ? List.of("dataParameters", "outputParameters")
      : List.of("outputParameters")) {
      if (!(node.get(section) instanceof Map<?, ?> found) || !(found.get("items") instanceof List<?>)) {
        continue;
      }
      Set<String> seen = new HashSet<>();
      List<String> twice = new ArrayList<>();
      for (Map<String, Object> parameter : list(found.get("items"))) {
        if (!seen.add((String) parameter.get("parameter"))) {
          twice.add((String) parameter.get("parameter"));
        }
      }
      for (Map<String, Object> parameter : list(found.get("items"))) {
        if (twice.contains(parameter.get("parameter"))) {
          continue;
        }
        Object use = parameter.get("use") == null ? Boolean.TRUE : parameter.get("use");
        counts[1] += parameter.get("use") == null ? 1 : 0;
        DcsSettingsEdit.setParameterValue(dcs, SchemaVersion.V2_20, variant, channel(Map.of(
          "section", "dataParameters".equals(section) ? "data" : "output", "path", path,
          "parameter", parameter.get("parameter"), "use", use)));
        assertThat(read(dcs)).as(where + " " + section + " " + parameter.get("parameter")).isEqualTo(before);
        counts[0]++;
      }
    }
    if (node.get("conditionalAppearance") instanceof Map<?, ?> found && found.get("items") instanceof List<?>) {
      List<Map<String, Object>> appearance = mutable(list(found.get("items")));
      for (int i = 0; i < appearance.size(); i++) {
        Map<String, Object> item = appearance.get(i);
        counts[1] += raise(item);
        for (Map<String, Object> field : children(item, "fields")) {
          counts[1] += raise(field);
        }
        for (Map<String, Object> sub : children(item, "filter")) {
          counts[1] += raiseTree(sub);
        }
        for (Map<String, Object> parameter : children(item, "appearance")) {
          counts[1] += raise(parameter);
        }
        DcsSettingsEdit.setAppearanceItem(dcs, SchemaVersion.V2_20, variant,
          channel(Map.of("path", path, "index", i, "item", item)));
        assertThat(read(dcs)).as(where + " оформление " + i).isEqualTo(before);
        counts[0]++;
      }
    }
    for (String list : NODE_LISTS) {
      if (!(node.get(list) instanceof List<?> children)) {
        continue;
      }
      for (int i = 0; i < children.size(); i++) {
        Map<String, Object> child = map(children.get(i));
        if ("nestedObject".equals(child.get("kind"))) {
          continue;
        }
        List<Map<String, Object>> childPath = new ArrayList<>(path);
        childPath.add(Map.of("list", list, "index", i));
        echoNode(dcs, variant, childPath, (String) child.get("kind"), child, before, counts);
      }
    }
  }

  /** Взводит флаг элемента без {@code use}: так шлёт окно со взведённым флажком; 1 - взведён. */
  private static int raise(Map<String, Object> item) {
    if (item.get("use") != null) {
      return 0;
    }
    item.put("use", true);
    return 1;
  }

  /** Взводит флаг элемента и рекурсивно - вложенных {@code items} папки выбора и группы отбора. */
  private static int raiseTree(Map<String, Object> item) {
    int raised = raise(item);
    for (Map<String, Object> child : children(item, "items")) {
      raised += raiseTree(child);
    }
    return raised;
  }

  /** Вложенный список объектов по ключу; нет ключа - пусто. */
  private static List<Map<String, Object>> children(Map<String, Object> item, String key) {
    return item.get(key) instanceof List<?> ? list(item.get(key)) : List.of();
  }

  /** Ожидание: вырезана строка {@code line}, стоящая непосредственно перед первой строкой с {@code anchor}. */
  private static String removeLineBefore(String text, String anchor, String line) {
    int at = text.indexOf(anchor);
    assertThat(at).isNotNegative();
    int lineStart = text.lastIndexOf('\n', at) + 1;
    int previous = text.lastIndexOf('\n', lineStart - 2) + 1;
    assertThat(text.substring(previous, lineStart)).isEqualTo(line);
    return text.substring(0, previous) + text.substring(lineStart);
  }

  private static int count(String text, String what) {
    int count = 0;
    for (int at = text.indexOf(what); at >= 0; at = text.indexOf(what, at + what.length())) {
      count++;
    }
    return count;
  }

  /** Payload так, как его доставляет канал: через JSON. */
  private static Map<String, Object> channel(Map<String, ?> payload) {
    return GSON.fromJson(GSON.toJson(payload), MAP_TYPE);
  }

  /** Изменяемая глубокая копия списка элементов через JSON. */
  private static List<Map<String, Object>> mutable(List<Map<String, Object>> items) {
    return list(channel(Map.of("items", items)).get("items"));
  }

  private static Map<String, Object> settings(Path dcs, String variant) throws Exception {
    return map(list(DcsSettingsRead.info(dcs, SchemaVersion.V2_20, variant).get("variants")).get(0).get("settings"));
  }

  private static List<Map<String, Object>> items(Map<String, Object> settings, String section) {
    return list(map(settings.get(section)).get("items"));
  }

  private static Map<String, Object> byName(List<Map<String, Object>> items, String name) {
    return items.stream().filter(item -> name.equals(item.get("parameter"))).findFirst()
      .orElseThrow(() -> new AssertionError("нет параметра " + name));
  }

  private static String read(Path dcs) throws IOException {
    return Files.readString(dcs, StandardCharsets.UTF_8);
  }

  @SuppressWarnings("unchecked")
  private static List<Map<String, Object>> list(Object value) {
    assertThat(value).isInstanceOf(List.class);
    return (List<Map<String, Object>>) value;
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> map(Object value) {
    assertThat(value).isInstanceOf(Map.class);
    return (Map<String, Object>) value;
  }

  /** Схемы компоновки под каталогом: макеты выгрузки и проекта EDT. */
  private static List<Path> schemaFiles(Path root) throws IOException {
    try (Stream<Path> files = Files.walk(root)) {
      return files
        .filter(file -> {
          String name = file.getFileName().toString();
          return name.equals("Template.xml") || name.equals("Template.dcs");
        })
        .filter(file -> {
          try {
            return Files.readString(file, StandardCharsets.UTF_8).contains("<DataCompositionSchema");
          } catch (IOException e) {
            throw new IllegalStateException(e);
          }
        })
        .sorted()
        .toList();
    }
  }

  /** Копия схемы из submodule во временный каталог: индекс фикстур не трогается. */
  private Path copySchema(String relativePath) throws Exception {
    Path source = Ssl31SubmodulePaths.projectRoot().resolve(relativePath);
    Path copy = Files.createTempDirectory(tempDir, "dcs").resolve("Template.xml");
    Files.copy(source, copy, StandardCopyOption.REPLACE_EXISTING);
    return copy;
  }
}
