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

import static io.github.yellowhammer.edt.EdtConfigurationScaffoldTest.CONFIGURATION;
import static io.github.yellowhammer.edt.EdtConfigurationScaffoldTest.MANIFEST;
import static io.github.yellowhammer.edt.EdtConfigurationScaffoldTest.masked;
import static io.github.yellowhammer.edt.EdtConfigurationScaffoldTest.read;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import io.github.yellowhammer.designerxml.SchemaVersion;
import io.github.yellowhammer.designerxml.cf.ExternalArtifactKind;
import io.github.yellowhammer.designerxml.cf.GoldenScaffold;
import io.github.yellowhammer.designerxml.cf.MdObjectAddType;
import io.github.yellowhammer.designerxml.cli.DesignerXmlCli;

import picocli.CommandLine;

/**
 * Пустой проект EDT из командной строки и всё, что на нём строится дальше.
 *
 * Проект создаёт {@code init-empty-cf} с форматом {@code edt}, затем в него
 * добавляются объекты всех видов, которые умеет EDT, к нему заводятся
 * расширение и внешние объекты. Всё это читается {@code read-json} и по
 * составу совпадает с тем, что записала сама 1С:EDT.
 */
class EdtEmptyProjectCliTest {

  private static final Gson GSON = new Gson();

  @TempDir
  Path workDir;

  @ParameterizedTest
  @EnumSource(SchemaVersion.class)
  void наПроектеСтроятсяОбъектыРасширениеИВнешниеОбъекты(SchemaVersion version) throws Exception {
    Path workspace = workDir.resolve("ws");
    Path project = workspace.resolve("Основа");
    Path configuration = project.resolve(CONFIGURATION);
    String line = version.platformLine();

    Run init = run("apply-mutation", Map.of(
        "op", "init-empty-cf",
        "format", "edt",
        "targetCfRoot", project.toString(),
        "schemaVersion", version.name(),
        "name", "Основа"));
    assertThat(init.exit()).as(init.err()).isZero();
    assertThat(read(project.resolve(MANIFEST))).contains("Runtime-Version: " + line + "\n");

    // Объекты всех видов, которые умеет EDT: имена те же, что в импорте EDT
    Path reference = EdtConfigurationScaffoldTest.bareImport(version).resolve(CONFIGURATION);
    Map<String, String> imported = EdtConfigurationScaffoldTest.composition(read(reference));
    List<String> names = new ArrayList<>();
    for (MdObjectAddType kind : MdObjectAddType.values()) {
      if (!hasEdtGolden(kind) || !GoldenScaffold.hasGolden(kind, version)) {
        continue;
      }
      String name = imported.getOrDefault(kind.configurationXmlTag(), kind.namePrefix() + "1");
      Run add = run("apply-mutation", Map.of(
          "op", "add-md-object",
          "configurationXml", configuration.toString(),
          "type", kind.name(),
          "name", name));
      assertThat(add.exit()).as(kind + ": " + add.err()).isZero();
      Path object = project.resolve("src").resolve(kind.cfSubdir()).resolve(name).resolve(name + ".mdo");
      JsonObject properties = readJson(Map.of("op", "cf-md-object-get", "objectXml", object.toString()))
          .getAsJsonObject();
      assertThat(properties.get("internalName").getAsString()).isEqualTo(name);
      names.add(name);
    }
    assertThat(names).containsAll(imported.values());

    // Описание конфигурации - как у импорта EDT, только со значениями новой конфигурации
    String expected = EdtConfigurationScaffoldTest.newConfiguration(read(reference), version, "Основа");
    String written = EdtConfigurationScaffoldTest.withoutComposition(read(configuration), imported.keySet());
    assertThat(masked(written)).isEqualTo(masked(expected));
    Map<String, List<String>> listed = allChildObjects(configuration);
    Map<String, List<String>> listedImport = allChildObjects(reference);
    listed.keySet().retainAll(listedImport.keySet());
    assertThat(listed).isEqualTo(listedImport);

    // Расширение: режимы и версия платформы берутся у созданной конфигурации
    Path extension = workspace.resolve("Основа.Расширение");
    Run cfe = run("apply-mutation", Map.of(
        "op", "init-empty-cfe",
        "mainConfigurationXml", configuration.toString(),
        "targetCfeRoot", extension.toString(),
        "name", "Расширение"));
    assertThat(cfe.exit()).as(cfe.err()).isZero();
    assertThat(read(extension.resolve(MANIFEST)))
        .contains("Base-Project: Основа\n", "Runtime-Version: " + line + "\n");
    JsonObject extensionProperties = readJson(Map.of(
        "op", "cf-configuration-properties-get",
        "configurationXml", extension.resolve(CONFIGURATION).toString())).getAsJsonObject();
    assertThat(extensionProperties.get("configurationExtensionCompatibilityMode").getAsString())
        .isEqualTo("VERSION_" + line.replace('.', '_'));
    assertThat(allChildObjects(extension.resolve(CONFIGURATION)))
        .isEqualTo(allChildObjects(golden("Extension/Основа.Пустое/" + CONFIGURATION)));

    // Внешние объекты: отдельные проекты с базовым проектом в манифесте
    for (ExternalArtifactKind kind : ExternalArtifactKind.values()) {
      String name = kind == ExternalArtifactKind.REPORT ? "ВнешнийОтчет1" : "ВнешняяОбработка1";
      Run add = run("apply-mutation", Map.of(
          "op", "external-artifact-add",
          "artifactsRoot", workspace.toString(),
          "mainConfigurationXml", configuration.toString(),
          "name", name,
          "kind", kind.name()));
      assertThat(add.exit()).as(add.err()).isZero();
      assertThat(read(workspace.resolve(name).resolve(MANIFEST)))
          .contains("Base-Project: Основа\n", "Runtime-Version: " + line + "\n");
      String proto = kind == ExternalArtifactKind.REPORT ? "Отчет1" : "Обработка1";
      Path goldenObject = kind == ExternalArtifactKind.REPORT
          ? golden("ExternalReport/Отчет1/src/ExternalReports/Отчет1/Отчет1.mdo")
          : golden("ExternalDataProcessor/Обработка1/src/ExternalDataProcessors/Обработка1/Обработка1.mdo");
      String properties = readJson(Map.of(
          "op", "external-artifact-properties-get", "objectXml", add.out().trim())).toString();
      String goldenProperties = readJson(Map.of(
          "op", "external-artifact-properties-get", "objectXml", goldenObject.toString())).toString();
      assertThat(properties).isEqualTo(goldenProperties.replace(proto, name));
    }

    // Дерево рабочей области: конфигурация со всеми объектами и её расширение
    JsonArray sources = readJson(Map.of("op", "project-metadata-tree", "projectRoot", workspace.toString()))
        .getAsJsonObject().getAsJsonArray("sources");
    assertThat(sources).hasSize(2);
    JsonObject main = sources.get(0).getAsJsonObject();
    assertThat(main.get("kind").getAsString()).isEqualTo("main");
    assertThat(main.get("label").getAsString()).isEqualTo("Основа");
    assertThat(itemNames(main)).containsAll(names);
    assertThat(sources.get(1).getAsJsonObject().get("kind").getAsString()).isEqualTo("extension");
  }

  @Test
  void командаСоздаётПроектСоСвойствамиКонфигурации() throws Exception {
    Path project = workDir.resolve("Каталог");

    int exit = new CommandLine(new DesignerXmlCli()).execute(
        "init-empty-cf", project.toString(), "-v", "V2_20", "--format", "EDT", "--project-name", "Торговля",
        "--name", "Торговля", "--synonym-ru", "Управление торговлей", "--vendor", "Поставщик",
        "--app-version", "1.0.1");

    assertThat(exit).isZero();
    assertThat(read(project.resolve(".project"))).contains("<name>Торговля</name>");
    assertThat(read(project.resolve(MANIFEST))).contains("Runtime-Version: 8.3.27\n");
    JsonObject properties = readJson(Map.of(
        "op", "cf-configuration-properties-get",
        "configurationXml", project.resolve(CONFIGURATION).toString())).getAsJsonObject();
    assertThat(properties.get("name").getAsString()).isEqualTo("Торговля");
    assertThat(properties.get("synonym").getAsString()).isEqualTo("Управление торговлей");
    assertThat(properties.get("vendor").getAsString()).isEqualTo("Поставщик");
    assertThat(properties.get("version").getAsString()).isEqualTo("1.0.1");
    assertThat(properties.get("compatibilityMode").getAsString()).isEqualTo("VERSION_8_3_27");
    assertThat(properties.getAsJsonArray("usePurposes")).containsExactly(GSON.toJsonTree("PERSONAL_COMPUTER"));
  }

  @Test
  void неизвестныйФорматИИмяПроектаБезEdtОтклоняются() throws Exception {
    Path target = workDir.resolve("cf");

    Run unknown = run("apply-mutation", Map.of(
        "op", "init-empty-cf", "format", "xml", "targetCfRoot", target.toString(), "schemaVersion", "V2_21"));
    Run named = run("apply-mutation", Map.of(
        "op", "init-empty-cf", "projectName", "Основа", "targetCfRoot", target.toString(), "schemaVersion", "V2_21"));

    assertThat(unknown.exit()).isEqualTo(2);
    assertThat(unknown.err()).contains("неизвестный формат исходников: xml", "designer, edt");
    assertThat(named.exit()).isEqualTo(2);
    assertThat(named.err()).contains("projectName");
    assertThat(target).doesNotExist();
  }

  /** Есть ли у вида эталон, записанный 1С:EDT. */
  private static boolean hasEdtGolden(MdObjectAddType kind) {
    String proto = kind.namePrefix() + "1";
    return EdtObjectScaffold.class.getResource(
        "/edt-golden/" + kind.cfSubdir() + "/" + proto + "/" + proto + ".mdo") != null;
  }

  /** Файл эталона EDT из сборки. */
  private static Path golden(String resource) throws URISyntaxException {
    return Path.of(EdtObjectScaffold.class.getResource("/edt-golden/" + resource).toURI());
  }

  private Map<String, List<String>> allChildObjects(Path configuration) throws IOException {
    JsonElement json = readJson(Map.of("op", "cf-list-all-child-objects", "configurationXml", configuration.toString()));
    Map<String, List<String>> all = new LinkedHashMap<>();
    for (Map.Entry<String, JsonElement> entry : json.getAsJsonObject().entrySet()) {
      List<String> names = new ArrayList<>();
      entry.getValue().getAsJsonArray().forEach(name -> names.add(name.getAsString()));
      all.put(entry.getKey(), names);
    }
    return all;
  }

  /** Имена объектов источника дерева: в группах и подгруппах. */
  private static List<String> itemNames(JsonObject source) {
    List<String> names = new ArrayList<>();
    for (JsonElement group : source.getAsJsonArray("groups")) {
      collect(group.getAsJsonObject(), names);
      JsonArray subgroups = group.getAsJsonObject().getAsJsonArray("subgroups");
      if (subgroups != null) {
        subgroups.forEach(subgroup -> collect(subgroup.getAsJsonObject(), names));
      }
    }
    return names;
  }

  private static void collect(JsonObject group, List<String> names) {
    JsonArray items = group.getAsJsonArray("items");
    if (items != null) {
      items.forEach(item -> names.add(item.getAsJsonObject().get("name").getAsString()));
    }
  }

  private JsonElement readJson(Map<String, String> params) throws IOException {
    Run read = run("read-json", params);
    assertThat(read.exit()).as(params.get("op") + ": " + read.err()).isZero();
    return JsonParser.parseString(read.out());
  }

  /** Результат команды. */
  private record Run(int exit, String out, String err) {
  }

  /** Команда с параметрами в UTF-8 JSON, как её вызывает расширение IDE. */
  private Run run(String command, Map<String, String> params) throws IOException {
    Path file = Files.createTempFile(workDir, "params", ".json");
    Files.writeString(file, GSON.toJson(params), StandardCharsets.UTF_8);
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    ByteArrayOutputStream err = new ByteArrayOutputStream();
    PrintStream stdout = System.out;
    PrintStream stderr = System.err;
    int exit;
    try {
      System.setOut(new PrintStream(out, true, StandardCharsets.UTF_8));
      System.setErr(new PrintStream(err, true, StandardCharsets.UTF_8));
      exit = new CommandLine(new DesignerXmlCli()).execute(command, "--params", file.toString());
    } finally {
      System.setOut(stdout);
      System.setErr(stderr);
    }
    return new Run(exit, out.toString(StandardCharsets.UTF_8), err.toString(StandardCharsets.UTF_8));
  }
}
