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

import static io.github.yellowhammer.edt.EdtXmlText.escape;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import javax.xml.stream.XMLStreamException;

import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EStructuralFeature;

import io.github.yellowhammer.designerxml.cf.CatalogNameConstraints;
import io.github.yellowhammer.designerxml.cf.DcsTemplateScaffold;
import io.github.yellowhammer.designerxml.cf.UiLabels;
import io.github.yellowhammer.edt.EdtObjectRegions.Region;

/**
 * Макеты схемы компоновки данных проекта 1С:EDT.
 *
 * <p>Описание макета - блок {@code <templates uuid>} в {@code .mdo} владельца (общий макет - свой
 * {@code .mdo} в {@code src/CommonTemplates/<Имя>/} и ссылка в составе конфигурации), содержимое -
 * {@code Template.dcs}: байты того же эталона платформы, что у выгрузки конфигуратора, но без BOM -
 * так пишет 1С:EDT (фикстура ssl31-edt). Синоним нового макета не пишется: пустой синоним 1С:EDT
 * не пишет вовсе.
 */
public final class EdtDcsTemplates {

  private static final String CONFIGURATION = "Configuration";
  private static final String TEMPLATES = "templates";
  private static final String TEMPLATES_DIRECTORY = "Templates";
  private static final String COMMON_TEMPLATE = "CommonTemplate";
  private static final String COMMON_TEMPLATES_DIRECTORY = "CommonTemplates";
  private static final String TEMPLATE_FILE = "Template.dcs";
  private static final String MAIN_SCHEMA = "mainDataCompositionSchema";
  private static final String TEMPLATE_TYPE = "templateType";
  private static final String DCS_TEMPLATE_TYPE = "DataCompositionSchema";
  private static final String INDENT = "  ";

  private EdtDcsTemplates() {
  }

  /**
   * Добавляет макет СКД. Владелец {@code Configuration.mdo} получает общий макет, прочие виды -
   * блок {@code templates} в своём {@code .mdo} и {@code Templates/<Имя>/Template.dcs}.
   *
   * @param ownerMdo описание владельца
   * @param model метамодель EDT
   * @param name имя нового макета
   * @param makeMain заполнить {@code mainDataCompositionSchema} владельца; только у видов,
   *   имеющих это свойство
   */
  public static void add(Path ownerMdo, EdtModel model, String name, boolean makeMain) throws IOException {
    CatalogNameConstraints.check(name);
    if (!Files.isRegularFile(ownerMdo)) {
      throw new IllegalArgumentException("Файл объекта не найден: " + ownerMdo);
    }
    String kind = EdtObjectReader.read(ownerMdo).kind();
    if (CONFIGURATION.equals(kind)) {
      if (makeMain) {
        throw new IllegalArgumentException("У вида " + CONFIGURATION + " нет свойства " + MAIN_SCHEMA + ".");
      }
      addCommonTemplate(ownerMdo, model, name);
      return;
    }
    EClass eClass = model.classOf(kind);
    if (eClass == null || eClass.getEStructuralFeature(TEMPLATES) == null) {
      throw new IllegalArgumentException("У вида " + kind + " нет макетов.");
    }
    if (makeMain && eClass.getEStructuralFeature(MAIN_SCHEMA) == null) {
      throw new IllegalArgumentException("У вида " + kind + " нет свойства " + MAIN_SCHEMA + ".");
    }
    Path templateDir = ownerMdo.getParent().resolve(TEMPLATES_DIRECTORY).resolve(name);
    if (Files.exists(templateDir)) {
      throw new IllegalArgumentException("Макет уже есть: " + name);
    }
    String xml = Files.readString(ownerMdo, StandardCharsets.UTF_8);
    String eol = eol(xml);
    try {
      List<Region> templates = EdtObjectRegions.properties(xml, TEMPLATES);
      if (EdtObjectRegions.names(xml, templates).contains(name)) {
        throw new IllegalArgumentException("Макет уже объявлен в описании объекта: " + name);
      }
      // Идентификатор выводится из правки и файла: та же правка того же проекта даёт тот же
      String uuid = EdtObjectScaffold.derivedUuid(
        EdtObjectScaffold.seed("dcs-template|" + name, xml), TEMPLATES);
      String entry = INDENT + "<" + TEMPLATES + " uuid=\"" + uuid + "\">" + eol
        + INDENT + INDENT + "<name>" + escape(name) + "</name>" + eol
        + INDENT + INDENT + "<" + TEMPLATE_TYPE + ">" + DCS_TEMPLATE_TYPE + "</" + TEMPLATE_TYPE + ">" + eol
        + INDENT + "</" + TEMPLATES + ">" + eol;
      int at = templates.isEmpty()
        ? EdtObjectRegions.insertionPoint(xml, order(model, kind), TEMPLATES)
        : lineEnd(xml, templates.get(templates.size() - 1).end());
      String updated = xml.substring(0, at) + entry + xml.substring(at);
      if (makeMain) {
        updated = withMainSchema(updated, model, kind,
          kind + "." + objectName(xml) + ".Template." + name, eol);
      }
      Files.createDirectories(templateDir);
      Files.write(templateDir.resolve(TEMPLATE_FILE), templateContent());
      Files.writeString(ownerMdo, updated, StandardCharsets.UTF_8);
    } catch (XMLStreamException error) {
      throw new IOException("Не удалось разобрать файл объекта: " + ownerMdo, error);
    }
  }

  /**
   * Удаляет макет СКД: блок {@code templates} и каталог содержимого; свойство
   * {@code mainDataCompositionSchema}, указывавшее на макет, уходит - пустым 1С:EDT его не пишет.
   *
   * @param ownerMdo описание владельца
   * @param name имя макета
   */
  public static void remove(Path ownerMdo, EdtModel model, String name) throws IOException {
    if (!Files.isRegularFile(ownerMdo)) {
      throw new IllegalArgumentException("Файл объекта не найден: " + ownerMdo);
    }
    String kind = EdtObjectReader.read(ownerMdo).kind();
    if (CONFIGURATION.equals(kind)) {
      removeCommonTemplate(ownerMdo, name);
      return;
    }
    String xml = Files.readString(ownerMdo, StandardCharsets.UTF_8);
    try {
      Region block = EdtObjectRegions.byName(xml, EdtObjectRegions.properties(xml, TEMPLATES), name);
      if (!block.found()) {
        throw new IllegalArgumentException("Макет не найден: " + name);
      }
      requireDcsTemplateType(xml.substring(block.start(), block.end()), name);
    } catch (XMLStreamException error) {
      throw new IOException("Не удалось разобрать файл объекта: " + ownerMdo, error);
    }
    EdtChildMutations.delete(ownerMdo, null, null, TEMPLATES, name);
    clearMainSchema(ownerMdo, name);
    deleteRecursively(ownerMdo.getParent().resolve(TEMPLATES_DIRECTORY).resolve(name));
  }

  /** Общий макет: свой {@code .mdo}, содержимое и ссылка в составе конфигурации. */
  private static void addCommonTemplate(Path configurationMdo, EdtModel model, String name) throws IOException {
    Path sourceRoot = EdtObjectScaffold.sourceRoot(configurationMdo);
    Path templateDir = sourceRoot.resolve(COMMON_TEMPLATES_DIRECTORY).resolve(name);
    String configuration = Files.readString(configurationMdo, StandardCharsets.UTF_8);
    try {
      List<String> listed = EdtObjectRegions.properties(configuration, "commonTemplates").stream()
        .map(region -> EdtObjectRegions.text(configuration, region))
        .toList();
      if (Files.exists(templateDir) || listed.contains(COMMON_TEMPLATE + "." + name)) {
        throw new IllegalArgumentException(UiLabels.alreadyExists(COMMON_TEMPLATE, name));
      }
    } catch (XMLStreamException error) {
      throw new IOException("Не удалось разобрать состав конфигурации: " + configurationMdo, error);
    }
    String eol = eol(configuration);
    String uuid = EdtObjectScaffold.derivedUuid(
      EdtObjectScaffold.seed("dcs-common-template|" + name, configuration), COMMON_TEMPLATES_DIRECTORY);
    String mdo = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" + eol
      + "<mdclass:CommonTemplate xmlns:mdclass=\"http://g5.1c.ru/v8/dt/metadata/mdclass\" uuid=\"" + uuid + "\">" + eol
      + INDENT + "<name>" + escape(name) + "</name>" + eol
      + INDENT + "<" + TEMPLATE_TYPE + ">" + DCS_TEMPLATE_TYPE + "</" + TEMPLATE_TYPE + ">" + eol
      + "</mdclass:CommonTemplate>" + eol;
    Files.createDirectories(templateDir);
    Files.writeString(templateDir.resolve(name + ".mdo"), mdo, StandardCharsets.UTF_8);
    Files.write(templateDir.resolve(TEMPLATE_FILE), templateContent());
    EdtObjectScaffold.appendReference(configurationMdo, model, COMMON_TEMPLATE, name);
  }

  private static void removeCommonTemplate(Path configurationMdo, String name) throws IOException {
    Path sourceRoot = EdtObjectScaffold.sourceRoot(configurationMdo);
    Path templateMdo = sourceRoot.resolve(COMMON_TEMPLATES_DIRECTORY).resolve(name).resolve(name + ".mdo");
    if (!Files.isRegularFile(templateMdo)) {
      throw new IllegalArgumentException("Макет не найден: " + name);
    }
    requireDcsTemplateType(Files.readString(templateMdo, StandardCharsets.UTF_8), name);
    // Сначала состав, потом файлы: каталог макета уносится целиком
    EdtObjectMutations.delete(configurationMdo, templateMdo, COMMON_TEMPLATE, name);
  }

  /**
   * Удалять можно только схему компоновки. 1С:EDT не пишет вид макета по умолчанию, поэтому
   * блок без {@code templateType} - табличный документ, а не схема.
   */
  private static void requireDcsTemplateType(String block, String name) {
    if (!block.contains("<" + TEMPLATE_TYPE + ">" + DCS_TEMPLATE_TYPE + "</" + TEMPLATE_TYPE + ">")) {
      throw new IllegalArgumentException(
        "Макет «" + name + "» — не схема компоновки данных: удалите его средствами платформы.");
    }
  }

  /**
   * Заполняет {@code mainDataCompositionSchema} владельца: существующее свойство заменяется на
   * месте, отсутствующее встаёт на место, которое отводит ему схема.
   */
  private static String withMainSchema(String xml, EdtModel model, String kind, String reference, String eol)
    throws XMLStreamException {
    String element = "<" + MAIN_SCHEMA + ">" + escape(reference) + "</" + MAIN_SCHEMA + ">";
    Region existing = EdtObjectRegions.property(xml, MAIN_SCHEMA);
    if (existing.found()) {
      return xml.substring(0, existing.start()) + element + xml.substring(existing.end());
    }
    int at = EdtObjectRegions.insertionPoint(xml, order(model, kind), MAIN_SCHEMA);
    return xml.substring(0, at) + INDENT + element + eol + xml.substring(at);
  }

  /** Свойство основной схемы, указывавшее на удалённый макет, уходит целой строкой. */
  private static void clearMainSchema(Path ownerMdo, String name) throws IOException {
    String xml = Files.readString(ownerMdo, StandardCharsets.UTF_8);
    Region main;
    try {
      main = EdtObjectRegions.property(xml, MAIN_SCHEMA);
    } catch (XMLStreamException error) {
      throw new IOException("Не удалось разобрать файл объекта: " + ownerMdo, error);
    }
    if (!main.found() || !EdtObjectRegions.text(xml, main).endsWith(".Template." + name)) {
      return;
    }
    int start = EdtObjectRegions.lineStart(xml, main.start());
    Files.writeString(ownerMdo, xml.substring(0, start) + xml.substring(lineEnd(xml, main.end())),
      StandardCharsets.UTF_8);
  }

  /** Имя объекта из свойства {@code name}: ссылка основной схемы строится по нему. */
  private static String objectName(String xml) throws XMLStreamException {
    Region name = EdtObjectRegions.property(xml, "name");
    if (!name.found()) {
      throw new IllegalArgumentException("У владельца нет свойства name.");
    }
    return EdtObjectRegions.text(xml, name);
  }

  /**
   * Содержимое пустой схемы: байты эталона платформы без BOM. Переводы строк эталона (CRLF) -
   * как у {@code Template.dcs}, записанных 1С:EDT (фикстура ssl31-edt).
   */
  private static byte[] templateContent() throws IOException {
    byte[] golden = DcsTemplateScaffold.templateContentBytes();
    if (golden.length >= 3 && (golden[0] & 0xFF) == 0xEF && (golden[1] & 0xFF) == 0xBB && (golden[2] & 0xFF) == 0xBF) {
      byte[] withoutBom = new byte[golden.length - 3];
      System.arraycopy(golden, 3, withoutBom, 0, withoutBom.length);
      return withoutBom;
    }
    return golden;
  }

  /** Порядок свойств класса по схеме; класс вида уже проверен {@link #add}. */
  private static List<String> order(EdtModel model, String className) {
    List<String> order = new ArrayList<>();
    for (EStructuralFeature feature : model.classOf(className).getEAllStructuralFeatures()) {
      order.add(feature.getName());
    }
    return order;
  }

  private static void deleteRecursively(Path directory) throws IOException {
    if (!Files.isDirectory(directory)) {
      return;
    }
    try (Stream<Path> files = Files.walk(directory)) {
      for (Path file : files.sorted(Comparator.reverseOrder()).toList()) {
        Files.delete(file);
      }
    }
  }

  private static int lineEnd(String xml, int end) {
    int line = xml.indexOf('\n', end);
    return line < 0 ? xml.length() : line + 1;
  }

  private static String eol(String xml) {
    return xml.contains("\r\n") ? "\r\n" : "\n";
  }
}
