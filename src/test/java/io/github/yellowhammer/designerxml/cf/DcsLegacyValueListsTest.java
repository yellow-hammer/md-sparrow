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

import com.google.gson.Gson;
import io.github.yellowhammer.designerxml.SchemaVersion;
import io.github.yellowhammer.designerxml.Ssl31SubmodulePaths;
import io.github.yellowhammer.designerxml.cli.DesignerXmlCli;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Список значений параметра схемы одним элементом {@code <value xsi:type="v8:ValueListType">} с
 * представлениями: md-sparrow его не разворачивает, а строгая {@code cf-dcs-validate} предупреждает
 * ({@link DcsLegacyValueLists}). Запись при этом не меняется: эхо — ни байта, правка соседнего свойства
 * форму оставляет, представление в новой записи — отказ, а снятие признака списка проходит и уводит
 * предупреждение, но не представления.
 */
class DcsLegacyValueListsTest {

  private static final String FILES =
    "src/cf/Reports/_ДемоФайлы/Templates/ОсновнаяСхемаКомпоновкиДанных/Ext/Template.xml";

  private static final String FILES_EDT =
    "ssl31/src/Reports/_ДемоФайлы/Templates/ОсновнаяСхемаКомпоновкиДанных/Template.dcs";

  /** Значение параметра «Размер» в ssl31; {@code %n} — перевод строки файла. */
  private static final String SIZE_VALUE = "\t\t<value xsi:type=\"xs:decimal\">0</value>%n";

  /** Конец параметра «Размер»: последнее доступное значение. */
  private static final String SIZE_TAIL = "\t\t</availableValue>%n\t</parameter>";

  /** Тот же конец со взведённым valueListAllowed: место признака по xs:sequence. */
  private static final String SIZE_LIST_TAIL =
    "\t\t</availableValue>%n\t\t<valueListAllowed>true</valueListAllowed>%n\t</parameter>";

  private static final String MESSAGE_TAIL = "; при загрузке платформа развернёт его в отдельные значения, а "
    + "представления потеряет — уберите представления или оставьте список как есть, если их потеря не важна";

  private static final Gson GSON = new Gson();

  @TempDir
  Path tempDir;

  @Test
  void элементValueListTypeСПредставлениямиДаётПредупреждениеНоНеОшибку() throws Exception {
    Path dcs = copy(FILES);
    String nl = "\r\n";
    write(dcs, withSize(read(dcs), nl, legacy(nl,
      item(nl, "<v8:value xsi:type=\"xs:decimal\">10</v8:value>", "<v8:presentation>десять</v8:presentation>"),
      item(nl, "<v8:value xsi:type=\"xs:decimal\">20</v8:value>"),
      // Пустое представление — не представление: терять нечего
      item(nl, "<v8:value xsi:type=\"xs:decimal\">30</v8:value>", "<v8:presentation/>")), true));

    Map<String, Object> answer = DcsXsdValidation.validate(dcs);

    // Строгая проверка от предупреждения не падает
    assertThat(answer.get("ok")).isEqualTo(true);
    assertThat(answer.get("errors")).isEqualTo(List.of());
    assertThat(answer.get("warnings")).isEqualTo(List.of(Map.of(
      "message", "параметр схемы Размер: список значений записан старой формой v8:ValueListType с представлениями "
        + "элементов (10 — «десять»)" + MESSAGE_TAIL,
      "parameter", "Размер",
      "kind", "valueListPresentationsLost")));

    // Признак в cf-dcs-info — представление у элемента valueTyped: форма платформы его не даёт
    Map<String, Object> size = parameter(dcs, "Размер");
    assertThat(String.valueOf(size.get("valueTyped"))).contains("presentation=десять");

    // Канал read-json отдаёт то же предупреждение
    Map<String, Object> strict = readJson(Map.of("op", "cf-dcs-validate", "objectXml", dcs.toString(), "strict", true));
    assertThat(strict.get("ok")).isEqualTo(true);
    assertThat(strict.get("warnings")).isEqualTo(answer.get("warnings"));
  }

  @Test
  void элементыСПредставлениямиНазываютсяКоротко() throws Exception {
    Path dcs = copy(FILES);
    String nl = "\r\n";
    write(dcs, withSize(read(dcs), nl, legacy(nl,
      item(nl, "<v8:value xsi:nil=\"true\"/>", "<v8:presentation>пусто</v8:presentation>"),
      item(nl, "<v8:value xsi:type=\"xs:decimal\">1</v8:value>", "<v8:presentation>один</v8:presentation>"),
      item(nl, "<v8:value xsi:type=\"xs:decimal\">2</v8:value>"),
      item(nl, "<v8:value xsi:type=\"xs:decimal\">3</v8:value>", "<v8:presentation>три</v8:presentation>"),
      item(nl, "<v8:value xsi:type=\"xs:decimal\">4</v8:value>", "<v8:presentation>четыре</v8:presentation>"),
      item(nl, "<v8:value xsi:type=\"xs:decimal\">5</v8:value>", "<v8:presentation>пять</v8:presentation>")), true));

    List<Map<String, Object>> warnings = DcsLegacyValueLists.warnings(dcs);

    // Три первых элемента с представлениями по имени, остальные — числом; элемент без представления не назван
    assertThat(warnings).extracting(w -> w.get("message")).containsExactly(
      "параметр схемы Размер: список значений записан старой формой v8:ValueListType с представлениями "
        + "элементов (не задано — «пусто», 1 — «один», 3 — «три» и ещё 2)" + MESSAGE_TAIL);
  }

  @Test
  void формаПлатформыИЭлементValueListTypeБезПредставленийПредупрежденийНеДают() throws Exception {
    Path dcs = copy(FILES);
    String nl = "\r\n";
    String original = read(dcs);

    // Форма платформы: повторяющиеся value (у «Размер» к тому же доступные значения с представлениями)
    write(dcs, withSize(original, nl, "\t\t<value xsi:type=\"xs:decimal\">1</value>\r\n"
      + "\t\t<value xsi:type=\"xs:decimal\">2</value>\r\n", true));
    assertThat(DcsLegacyValueLists.warnings(dcs)).isEmpty();
    assertThat(DcsXsdValidation.validate(dcs).get("warnings")).isEqualTo(List.of());

    // Элемент v8:ValueListType без представлений платформа развернёт без потерь — предупреждения нет
    write(dcs, withSize(original, nl, legacy(nl,
      item(nl, "<v8:value xsi:type=\"xs:decimal\">10</v8:value>"),
      item(nl, "<v8:value xsi:type=\"xs:decimal\">20</v8:value>", "<v8:presentation/>")), true));
    assertThat(DcsLegacyValueLists.warnings(dcs)).isEmpty();

    // Без признака списка значений правило не срабатывает — тоже без предупреждения
    write(dcs, withSize(original, nl, legacy(nl,
      item(nl, "<v8:value xsi:type=\"xs:decimal\">10</v8:value>", "<v8:presentation>десять</v8:presentation>")),
      false));
    assertThat(DcsLegacyValueLists.warnings(dcs)).isEmpty();
  }

  @Test
  void спискиЗначенийВНастройкахВариантаНеКасаются() throws Exception {
    // Отбор и параметры данных варианта платформа хранит элементом v8:ValueListType как есть
    Path dcs = copy(FILES);
    String text = read(dcs);
    String emptyRight = "\t\t\t\t\t<dcsset:right xsi:type=\"v8:ValueListType\">\r\n"
      + "\t\t\t\t\t\t<v8:valueType/>\r\n"
      + "\t\t\t\t\t\t<v8:lastId xsi:type=\"xs:decimal\">-1</v8:lastId>\r\n"
      + "\t\t\t\t\t</dcsset:right>\r\n";
    String presentedRight = "\t\t\t\t\t<dcsset:right xsi:type=\"v8:ValueListType\">\r\n"
      + "\t\t\t\t\t\t<v8:valueType/>\r\n"
      + "\t\t\t\t\t\t<v8:lastId xsi:type=\"xs:decimal\">0</v8:lastId>\r\n"
      + "\t\t\t\t\t\t<v8:item>\r\n"
      + "\t\t\t\t\t\t\t<v8:value xsi:type=\"xs:string\">А</v8:value>\r\n"
      + "\t\t\t\t\t\t\t<v8:presentation>Буква А</v8:presentation>\r\n"
      + "\t\t\t\t\t\t</v8:item>\r\n"
      + "\t\t\t\t\t</dcsset:right>\r\n";
    String period = "\t\t\t\t\t<dcscor:value xsi:type=\"v8:StandardPeriod\">\r\n"
      + "\t\t\t\t\t\t<v8:variant xsi:type=\"v8:StandardPeriodVariant\">Custom</v8:variant>\r\n"
      + "\t\t\t\t\t\t<v8:startDate>2012-01-01T00:00:00</v8:startDate>\r\n"
      + "\t\t\t\t\t\t<v8:endDate>2012-12-31T23:59:59</v8:endDate>\r\n"
      + "\t\t\t\t\t</dcscor:value>\r\n";
    String presentedValue = "\t\t\t\t\t<dcscor:value xsi:type=\"v8:ValueListType\">\r\n"
      + "\t\t\t\t\t\t<v8:valueType/>\r\n"
      + "\t\t\t\t\t\t<v8:lastId xsi:type=\"xs:decimal\">0</v8:lastId>\r\n"
      + "\t\t\t\t\t\t<v8:item>\r\n"
      + "\t\t\t\t\t\t\t<v8:value xsi:type=\"xs:string\">Б</v8:value>\r\n"
      + "\t\t\t\t\t\t\t<v8:presentation>Буква Б</v8:presentation>\r\n"
      + "\t\t\t\t\t\t</v8:item>\r\n"
      + "\t\t\t\t\t</dcscor:value>\r\n";
    assertThat(text).contains(emptyRight).contains(period);
    write(dcs, text.replaceFirst(java.util.regex.Pattern.quote(emptyRight), presentedRight)
      .replaceFirst(java.util.regex.Pattern.quote(period), presentedValue));

    Map<String, Object> answer = DcsXsdValidation.validate(dcs);

    assertThat(answer.get("ok")).isEqualTo(true);
    assertThat(answer.get("warnings")).isEqualTo(List.of());
  }

  /**
   * В корпусах ssl31 (выгрузка конфигурации и внешних отчётов) и EDT-пары у параметров корня нет ни
   * одного списка элементом {@code v8:ValueListType}, поэтому предупреждений 0 — счётчики подтверждают,
   * что корпус обойдён.
   */
  @Test
  void наКорпусахSsl31ИEdtПредупрежденийНет() throws Exception {
    int[] designer = corpus(Ssl31SubmodulePaths.projectRoot(), "Template.xml");
    int[] edt = corpus(Path.of(System.getProperty("fixtures.ssl31edt.root")), "Template.dcs");

    // {схем, параметров корня, из них со списком значений, значений элементом v8:ValueListType, предупреждений}
    assertThat(designer[0]).isGreaterThanOrEqualTo(70);
    assertThat(edt[0]).isGreaterThanOrEqualTo(70);
    assertThat(designer[1]).isEqualTo(247);
    assertThat(designer[2]).isEqualTo(46);
    assertThat(edt[1]).isEqualTo(244);
    assertThat(edt[2]).isEqualTo(46);
    assertThat(designer[3]).isZero();
    assertThat(edt[3]).isZero();
    assertThat(designer[4]).isZero();
    assertThat(edt[4]).isZero();
  }

  @Test
  void записьНеМеняетсяЭхоИПравкаСоседнегоСвойстваОставляютФормуИПредупреждение() throws Exception {
    Path dcs = copy(FILES);
    String nl = "\r\n";
    String legacy = withSize(read(dcs), nl, legacy(nl,
      item(nl, "<v8:value xsi:type=\"xs:decimal\">10</v8:value>", "<v8:presentation>десять</v8:presentation>"),
      item(nl, "<v8:value xsi:type=\"xs:decimal\">20</v8:value>")), true);
    write(dcs, legacy);
    List<Map<String, Object>> warned = DcsLegacyValueLists.warnings(dcs);
    assertThat(warned).hasSize(1);

    // Эхо valueTyped через канал — ни байта
    Map<String, Object> size = parameter(dcs, "Размер");
    assertThat(applyMutation(Map.of("op", "cf-dcs-set-parameter", "objectXml", dcs.toString(),
      "schemaVersion", "V2_20", "name", "Размер",
      "payloadJson", GSON.toJson(Map.of("value", size.get("valueTyped")))))).isZero();
    assertThat(read(dcs)).isEqualTo(legacy);

    // Правка соседнего свойства переносит элемент v8:ValueListType как есть — предупреждение остаётся
    DcsEdit.setParameter(dcs, SchemaVersion.V2_20, "Размер", Map.of("denyIncompleteValues", true));
    assertThat(read(dcs)).contains("<value xsi:type=\"v8:ValueListType\">")
      .contains("<v8:presentation>десять</v8:presentation>");
    assertThat(DcsLegacyValueLists.warnings(dcs)).isEqualTo(warned);

    // Представление в новой записи — отказ, файл не меняется
    String denied = read(dcs);
    Map<String, Object> presented = Map.of("type", "v8:ValueListType", "list", Map.of("items", List.of(
      Map.of("value", Map.of("type", "xs:decimal", "text", "10"), "presentation", "десять"))));
    assertThatThrownBy(() -> DcsEdit.setParameter(dcs, SchemaVersion.V2_20, "Размер", Map.of("value", presented)))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("платформа не хранит представления значений параметра схемы");
    assertThat(read(dcs)).isEqualTo(denied);

    // Список без представлений пишется формой платформы — предупреждение уходит
    DcsEdit.setParameter(dcs, SchemaVersion.V2_20, "Размер", Map.of("value", Map.of("type", "v8:ValueListType",
      "list", Map.of("items", List.of(Map.of("value", Map.of("type", "xs:decimal", "text", "10")),
        Map.of("value", Map.of("type", "xs:decimal", "text", "20")))))));
    assertThat(read(dcs)).doesNotContain("<value xsi:type=\"v8:ValueListType\">")
      .contains("\t\t<value xsi:type=\"xs:decimal\">10</value>\r\n\t\t<value xsi:type=\"xs:decimal\">20</value>\r\n");
    assertThat(DcsLegacyValueLists.warnings(dcs)).isEmpty();
  }

  /**
   * Снятие valueListAllowed у списка элементом v8:ValueListType проходит: отказ «список из N значений»
   * считает элементы value, а здесь элемент один. Элемент с представлениями остаётся байт в байт, признак
   * уходит, и предупреждение тоже, хотя представления в файле те же: снятие признака — не способ убрать
   * представления (docs/cf-dcs.md, «Строгая проверка схемы»).
   */
  @Test
  void снятиеПризнакаУЭлементаValueListTypeПроходитИУбираетПредупреждениеНоНеПредставления() throws Exception {
    String nl = "\r\n";
    String legacyValue = legacy(nl,
      item(nl, "<v8:value xsi:type=\"xs:decimal\">10</v8:value>", "<v8:presentation>десять</v8:presentation>"),
      item(nl, "<v8:value xsi:type=\"xs:decimal\">20</v8:value>"));

    // false — прямым вызовом, null — через канал apply-mutation: оба снимают признак
    Path direct = copy(FILES);
    write(direct, withSize(read(direct), nl, legacyValue, true));
    assertThat(DcsLegacyValueLists.warnings(direct)).hasSize(1);
    DcsEdit.setParameter(direct, SchemaVersion.V2_20, "Размер", Map.of("valueListAllowed", false));

    Path channel = copy(FILES);
    write(channel, withSize(read(channel), nl, legacyValue, true));
    assertThat(applyMutation(Map.of("op", "cf-dcs-set-parameter", "objectXml", channel.toString(),
      "schemaVersion", "V2_20", "name", "Размер", "payloadJson", "{\"valueListAllowed\":null}"))).isZero();

    for (Path dcs : List.of(direct, channel)) {
      // Элемент v8:ValueListType — байт в байт, признака у «Размер» нет (у других параметров схемы он есть)
      assertThat(sizeParameter(read(dcs))).contains(legacyValue).doesNotContain("<valueListAllowed>");
      // Предупреждения нет: правило требует признака — а представления на месте
      assertThat(DcsLegacyValueLists.warnings(dcs)).isEmpty();
      Map<String, Object> strict = DcsXsdValidation.validate(dcs);
      assertThat(strict.get("ok")).isEqualTo(true);
      assertThat(strict.get("warnings")).isEqualTo(List.of());
      Map<String, Object> size = parameter(dcs, "Размер");
      assertThat(size.get("valueListAllowed")).isNotEqualTo(true);
      assertThat(String.valueOf(size.get("valueTyped"))).contains("presentation=десять");
    }

    // Для сравнения: тот же список формой платформы (два value) — отказ, файл не меняется
    Path platform = copy(FILES);
    write(platform, withSize(read(platform), nl, "\t\t<value xsi:type=\"xs:decimal\">10</value>\r\n"
      + "\t\t<value xsi:type=\"xs:decimal\">20</value>\r\n", true));
    String before = read(platform);
    assertThatThrownBy(() -> DcsEdit.setParameter(platform, SchemaVersion.V2_20, "Размер",
      Map.of("valueListAllowed", false)))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("снять valueListAllowed можно, только задав в том же payload одно значение value");
    assertThat(read(platform)).isEqualTo(before);
  }

  @Test
  void схемаEdtПредупреждаетТакЖе() throws Exception {
    Path dcs = Files.createTempDirectory(tempDir, "edt").resolve("Template.dcs");
    Files.copy(Path.of(System.getProperty("fixtures.ssl31edt.root")).resolve(FILES_EDT), dcs);
    String nl = XmlLines.newline(read(dcs));
    write(dcs, withSize(read(dcs), nl, legacy(nl,
      item(nl, "<v8:value xsi:type=\"xs:decimal\">10</v8:value>", "<v8:presentation>десять</v8:presentation>")),
      true));

    Map<String, Object> answer = DcsXsdValidation.validate(dcs);

    assertThat(answer.get("ok")).isEqualTo(true);
    assertThat(answer.get("warnings")).isEqualTo(List.of(Map.of(
      "message", "параметр схемы Размер: список значений записан старой формой v8:ValueListType с представлениями "
        + "элементов (10 — «десять»)" + MESSAGE_TAIL,
      "parameter", "Размер",
      "kind", "valueListPresentationsLost")));
  }

  // --- общее ---------------------------------------------------------------------------------

  /** Элемент списка v8:ValueListType с отступом места (параметр корня: элемент на третьем уровне). */
  private static String item(String nl, String... children) {
    StringBuilder out = new StringBuilder("\t\t\t<v8:item>").append(nl);
    for (String child : children) {
      out.append("\t\t\t\t").append(child).append(nl);
    }
    return out.append("\t\t\t</v8:item>").append(nl).toString();
  }

  /** Значение параметра корня одним элементом {@code v8:ValueListType}. */
  private static String legacy(String nl, String... items) {
    return "\t\t<value xsi:type=\"v8:ValueListType\">" + nl
      + "\t\t\t<v8:valueType/>" + nl
      + "\t\t\t<v8:lastId xsi:type=\"xs:decimal\">" + (items.length - 1) + "</v8:lastId>" + nl
      + String.join("", items)
      + "\t\t</value>" + nl;
  }

  /** «Размер» с другим значением и, по желанию, взведённым valueListAllowed. */
  private static String withSize(String text, String nl, String value, boolean listAllowed) {
    String out = inSize(text, SIZE_VALUE.replace("%n", nl), value);
    return listAllowed ? inSize(out, SIZE_TAIL.replace("%n", nl), SIZE_LIST_TAIL.replace("%n", nl)) : out;
  }

  /** Текст параметра «Размер» целиком. */
  private static String sizeParameter(String text) {
    for (XmlLines.Node node : XmlLines.children(text, List.of("DataCompositionSchema"))) {
      if ("parameter".equals(node.name()) && "Размер".equals(node.leaves().get("name"))) {
        return text.substring(node.start(), node.end());
      }
    }
    throw new AssertionError("параметр Размер не найден");
  }

  /** Замена первого вхождения внутри параметра «Размер» (такие же строки есть у других параметров). */
  private static String inSize(String text, String from, String to) {
    for (XmlLines.Node node : XmlLines.children(text, List.of("DataCompositionSchema"))) {
      if ("parameter".equals(node.name()) && "Размер".equals(node.leaves().get("name"))) {
        int at = text.indexOf(from, node.start());
        assertThat(at).as(from).isNotNegative();
        assertThat(at + from.length()).as(from).isLessThanOrEqualTo(node.end());
        return text.substring(0, at) + to + text.substring(at + from.length());
      }
    }
    throw new AssertionError("параметр Размер не найден");
  }

  /**
   * Обход корпуса: {схем, параметров корня, со списком значений, значений элементом v8:ValueListType,
   * предупреждений}. Такое значение — единственный {@code value} параметра с
   * {@code xsi:type="v8:ValueListType"}.
   */
  private static int[] corpus(Path root, String fileName) throws IOException {
    int[] out = new int[5];
    for (Path schema : schemas(root, fileName)) {
      out[0]++;
      String text = read(schema);
      for (XmlLines.Node node : XmlLines.children(text, List.of("DataCompositionSchema"))) {
        if (!"parameter".equals(node.name())) {
          continue;
        }
        out[1]++;
        List<XmlLines.Node> children = XmlLines.childrenAt(text, node.start());
        if (DcsRead.valueListAllowed(text, children)) {
          out[2]++;
        }
        List<XmlLines.Node> values = children.stream().filter(child -> "value".equals(child.name())).toList();
        if (values.size() == 1 && text.substring(values.get(0).start(), values.get(0).end())
          .contains("xsi:type=\"v8:ValueListType\"")) {
          out[3]++;
        }
      }
      out[4] += DcsLegacyValueLists.warnings(schema).size();
    }
    return out;
  }

  private static List<Path> schemas(Path root, String fileName) throws IOException {
    List<Path> out = new ArrayList<>();
    try (Stream<Path> files = Files.walk(root)) {
      for (Path file : files.filter(p -> p.getFileName().toString().equals(fileName)).sorted().toList()) {
        if (read(file).contains("<DataCompositionSchema")) {
          out.add(file);
        }
      }
    }
    return out;
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> parameter(Path dcs, String name) throws Exception {
    List<Map<String, Object>> rows =
      (List<Map<String, Object>>) DcsRead.info(dcs, SchemaVersion.V2_20).get("parameters");
    return rows.stream().filter(row -> name.equals(row.get("name"))).findFirst()
      .orElseThrow(() -> new AssertionError("параметр не найден: " + name));
  }

  private Path copy(String relativePath) throws IOException {
    Path target = Files.createTempDirectory(tempDir, "dcs").resolve("Template.xml");
    Files.copy(Ssl31SubmodulePaths.projectRoot().resolve(relativePath), target);
    return target;
  }

  private static String read(Path file) throws IOException {
    return Files.readString(file, StandardCharsets.UTF_8);
  }

  private static void write(Path file, String text) throws IOException {
    Files.writeString(file, text, StandardCharsets.UTF_8);
  }

  private int applyMutation(Map<String, ?> params) throws Exception {
    Path paramsFile = Files.createTempFile(tempDir, "params", ".json");
    Files.writeString(paramsFile, GSON.toJson(params), StandardCharsets.UTF_8);
    return new CommandLine(new DesignerXmlCli()).execute("apply-mutation", "--params", paramsFile.toString());
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> readJson(Map<String, ?> params) throws Exception {
    Path paramsFile = Files.createTempFile(tempDir, "params", ".json");
    Files.writeString(paramsFile, GSON.toJson(params), StandardCharsets.UTF_8);
    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    PrintStream prev = System.out;
    int exit;
    try {
      System.setOut(new PrintStream(buffer, true, StandardCharsets.UTF_8));
      exit = new CommandLine(new DesignerXmlCli()).execute("read-json", "--params", paramsFile.toString());
    } finally {
      System.setOut(prev);
    }
    assertThat(exit).isZero();
    return GSON.fromJson(buffer.toString(StandardCharsets.UTF_8), Map.class);
  }
}
