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

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import javax.xml.stream.XMLStreamException;

import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EStructuralFeature;

import io.github.yellowhammer.designerxml.cf.CatalogNameConstraints;
import io.github.yellowhammer.designerxml.cf.ChildObjectEntry;
import io.github.yellowhammer.designerxml.cf.MdObjectAddType;
import io.github.yellowhammer.edt.EdtObjectRegions.Region;

/**
 * Новые объекты и формы проекта 1С:EDT.
 *
 * Заготовкой служит объект, который сама 1С:EDT записала при импорте пустой
 * выгрузки конфигуратора: такие файлы лежат в сборке эталонами. У заготовки
 * меняются имя и идентификаторы, состав конфигурации получает ссылку на своё
 * место по порядку схемы.
 *
 * Пустая форма заводится так же: разметка формы из эталона, запись в описании
 * объекта со своим идентификатором.
 */
public final class EdtObjectScaffold {

  private static final String GOLDEN = "/edt-golden/";
  private static final String CONFIGURATION = "Configuration";
  private static final String FORMS = "forms";
  private static final String FORMS_DIRECTORY = "Forms";
  private static final String FORM_FILE = "Form.form";
  private static final String FORM_PROTO = "ФормаЭлемента";
  private static final String RIGHTS_FILE = "Rights.rights";
  private static final String INDENT = "  ";
  private static final int MAX_SUFFIX = 999_999;

  private static final Pattern UUID_TOKEN = Pattern.compile(
      "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");

  private EdtObjectScaffold() {
  }

  /**
   * Добавляет объект с первым свободным именем вида: «Справочник1», «Справочник2».
   *
   * @param configurationMdo описание конфигурации
   * @param model метамодель EDT
   * @param kind вид объекта
   * @return имя созданного объекта
   * @throws IOException если файлы не читаются или не пишутся
   */
  public static String addWithNextAvailableName(Path configurationMdo, EdtModel model, MdObjectAddType kind)
      throws IOException {
    Set<String> taken = takenNames(configurationMdo, model, kind);
    for (int suffix = 1; suffix <= MAX_SUFFIX; suffix++) {
      String candidate = kind.namePrefix() + suffix;
      if (!taken.contains(candidate)) {
        add(configurationMdo, model, kind, candidate);
        return candidate;
      }
    }
    throw new IllegalStateException("Свободного имени для вида " + kind.configurationXmlTag() + " не нашлось");
  }

  /**
   * Добавляет объект с заданным именем.
   *
   * @param configurationMdo описание конфигурации
   * @param model метамодель EDT
   * @param kind вид объекта
   * @param name имя объекта
   * @throws IOException если файлы не читаются или не пишутся
   */
  public static void add(Path configurationMdo, EdtModel model, MdObjectAddType kind, String name)
      throws IOException {
    CatalogNameConstraints.check(name);
    Path sourceRoot = sourceRoot(configurationMdo);
    Path objectDir = sourceRoot.resolve(kind.cfSubdir()).resolve(name);
    if (Files.exists(objectDir)) {
      throw new IllegalArgumentException("Объект уже есть: " + name);
    }
    String proto = kind.namePrefix() + "1";
    String golden = golden(kind.cfSubdir() + "/" + proto + "/" + proto + ".mdo");
    Files.createDirectories(objectDir);
    Files.writeString(objectDir.resolve(name + ".mdo"), parametrize(golden, proto, name), StandardCharsets.UTF_8);
    if (kind.roleWithExtRights()) {
      Files.writeString(objectDir.resolve(RIGHTS_FILE),
          golden(kind.cfSubdir() + "/" + proto + "/" + RIGHTS_FILE), StandardCharsets.UTF_8);
    }
    appendReference(configurationMdo, model, kind.configurationXmlTag(), name);
  }

  /**
   * Добавляет объекту пустую управляемую форму.
   *
   * @param objectMdo описание объекта-владельца
   * @param model метамодель EDT
   * @param formName имя формы
   * @throws IOException если файлы не читаются или не пишутся
   */
  public static void addForm(Path objectMdo, EdtModel model, String formName) throws IOException {
    CatalogNameConstraints.check(formName);
    if (!Files.isRegularFile(objectMdo)) {
      throw new IllegalArgumentException("Файл объекта не найден: " + objectMdo);
    }
    Path formDir = objectMdo.getParent().resolve(FORMS_DIRECTORY).resolve(formName);
    if (Files.exists(formDir)) {
      throw new IllegalArgumentException("Форма уже есть: " + formName);
    }
    String xml = Files.readString(objectMdo, StandardCharsets.UTF_8);
    String eol = eol(xml);
    try {
      List<Region> forms = EdtObjectRegions.properties(xml, FORMS);
      if (EdtObjectRegions.names(xml, forms).contains(formName)) {
        throw new IllegalArgumentException("Форма уже объявлена в описании объекта: " + formName);
      }
      String entry = parametrize(golden(FORMS_DIRECTORY + "/" + FORM_PROTO + ".xml"), FORM_PROTO, formName)
          .replace("\n", eol);
      int at = forms.isEmpty()
          ? EdtObjectRegions.insertionPoint(xml, order(model, EdtObjectReader.read(objectMdo).kind()), FORMS)
          : lineEnd(xml, forms.get(forms.size() - 1).end());
      Files.createDirectories(formDir);
      Files.writeString(formDir.resolve(FORM_FILE),
          golden(FORMS_DIRECTORY + "/" + FORM_PROTO + "/" + FORM_FILE).replace("\n", eol), StandardCharsets.UTF_8);
      Files.writeString(objectMdo, xml.substring(0, at) + entry + xml.substring(at), StandardCharsets.UTF_8);
    } catch (XMLStreamException error) {
      throw new IOException("Не удалось разобрать файл объекта: " + objectMdo, error);
    }
  }

  /**
   * Удаляет форму объекта вместе с её файлами.
   *
   * @param objectMdo описание объекта-владельца
   * @param formName имя формы
   * @throws IOException если файлы не читаются или не пишутся
   */
  public static void deleteForm(Path objectMdo, String formName) throws IOException {
    EdtChildMutations.delete(objectMdo, null, null, FORMS, formName);
    Path formDir = objectMdo.getParent().resolve(FORMS_DIRECTORY).resolve(formName);
    if (Files.isDirectory(formDir)) {
      try (Stream<Path> files = Files.walk(formDir)) {
        for (Path file : files.sorted(Comparator.reverseOrder()).toList()) {
          Files.delete(file);
        }
      }
    }
  }

  /** Каталог исходников проекта: описание конфигурации лежит в его подкаталоге. */
  private static Path sourceRoot(Path configurationMdo) {
    Path directory = configurationMdo.getParent();
    if (!Files.isRegularFile(configurationMdo) || directory == null
        || !CONFIGURATION.equals(directory.getFileName().toString()) || directory.getParent() == null) {
      throw new IllegalArgumentException("Описание конфигурации EDT не найдено: " + configurationMdo);
    }
    return directory.getParent();
  }

  /** Имена, которые уже заняты: в составе конфигурации и каталогами на диске. */
  private static Set<String> takenNames(Path configurationMdo, EdtModel model, MdObjectAddType kind)
      throws IOException {
    Set<String> taken = new LinkedHashSet<>();
    for (ChildObjectEntry entry : EdtConfigurationReader.listChildObjects(configurationMdo, model)) {
      if (entry.objectType().equals(kind.configurationXmlTag())) {
        taken.add(entry.name());
      }
    }
    Path directory = sourceRoot(configurationMdo).resolve(kind.cfSubdir());
    if (Files.isDirectory(directory)) {
      try (Stream<Path> children = Files.list(directory)) {
        children.forEach(child -> taken.add(child.getFileName().toString()));
      }
    }
    return taken;
  }

  /**
   * Ссылка на объект в составе конфигурации.
   *
   * Встаёт за последней ссылкой того же вида, а если вид в составе ещё не
   * встречался, на место, которое отводит ему схема.
   */
  private static void appendReference(Path configurationMdo, EdtModel model, String objectType, String name)
      throws IOException {
    String feature = null;
    for (EdtModel.Composition item : model.composition(CONFIGURATION)) {
      if (item.objectType().equals(objectType)) {
        feature = item.feature();
      }
    }
    if (feature == null) {
      throw new IllegalArgumentException("Схема конфигурации не знает вид объекта " + objectType);
    }
    String xml = Files.readString(configurationMdo, StandardCharsets.UTF_8);
    try {
      List<Region> regions = EdtObjectRegions.properties(xml, feature);
      int at = regions.isEmpty()
          ? EdtObjectRegions.insertionPoint(xml, order(model, CONFIGURATION), feature)
          : lineEnd(xml, regions.get(regions.size() - 1).end());
      String element = INDENT + "<" + feature + ">" + objectType + "." + escape(name) + "</" + feature + ">" + eol(xml);
      Files.writeString(configurationMdo, xml.substring(0, at) + element + xml.substring(at), StandardCharsets.UTF_8);
    } catch (XMLStreamException error) {
      throw new IOException("Не удалось разобрать состав конфигурации: " + configurationMdo, error);
    }
  }

  /** Порядок свойств класса по схеме. */
  private static List<String> order(EdtModel model, String className) {
    EClass eClass = model.classOf(className);
    List<String> order = new ArrayList<>();
    if (eClass != null) {
      for (EStructuralFeature feature : eClass.getEAllStructuralFeatures()) {
        order.add(feature.getName());
      }
    }
    return order;
  }

  /**
   * Эталон под именем нового объекта.
   *
   * Имя заменяется целым словом, чтобы не задеть похожие; каждый идентификатор
   * эталона получает свой новый: по ним платформа отличает объекты и типы.
   */
  static String parametrize(String golden, String protoName, String name) {
    Pattern token = Pattern.compile("(?<![\\p{L}\\p{N}_])" + Pattern.quote(protoName) + "(?![\\p{L}\\p{N}_])");
    String renamed = token.matcher(golden).replaceAll(Matcher.quoteReplacement(escape(name)));
    Map<String, String> fresh = new HashMap<>();
    Matcher uuids = UUID_TOKEN.matcher(renamed);
    StringBuilder out = new StringBuilder();
    while (uuids.find()) {
      String next = fresh.computeIfAbsent(uuids.group(), old -> UUID.randomUUID().toString());
      uuids.appendReplacement(out, Matcher.quoteReplacement(next));
    }
    uuids.appendTail(out);
    return out.toString();
  }

  /** Эталон из сборки: файл, который 1С:EDT записала при импорте пустой выгрузки. */
  private static String golden(String resource) throws IOException {
    try (InputStream stream = EdtObjectScaffold.class.getResourceAsStream(GOLDEN + resource)) {
      if (stream == null) {
        throw new IOException("В сборке нет эталона объекта EDT: " + resource);
      }
      return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
    }
  }

  private static int lineEnd(String xml, int end) {
    int line = xml.indexOf('\n', end);
    return line < 0 ? xml.length() : line + 1;
  }

  private static String eol(String xml) {
    return xml.contains("\r\n") ? "\r\n" : "\n";
  }

  private static String escape(String value) {
    return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
  }
}
