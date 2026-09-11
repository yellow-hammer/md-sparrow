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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.emf.ecore.EEnum;
import org.eclipse.emf.ecore.EEnumLiteral;
import org.eclipse.emf.ecore.EPackage;

import io.github.yellowhammer.designerxml.cf.SupportRules;
import io.github.yellowhammer.edt.EdtObjectReader.EdtNode;

/**
 * Правила поддержки поставщика в проекте 1С:EDT.
 *
 * Конфигуратор держит их в двоичном {@code ParentConfigurations.bin}, а 1С:EDT
 * при импорте переписывает в {@code Configuration/Configuration.distr}: XML по
 * метамодели поставки, где у каждого объекта, реквизита, формы и макета своя
 * запись с режимом. Контракт наружу тот же, что у выгрузки конфигуратора:
 * режимы 0 (запрещено), 1 (разрешено), 2 (снято с поддержки), состояния
 * {@code locked} и {@code editable}, ключи элементов по операциям.
 */
public final class EdtSupportRules {

  /** Файл поставки рядом с описанием конфигурации. */
  public static final String RULES_FILE = "Configuration.distr";

  private static final String DISTRIBUTION_NAMESPACE = "http://g5.1c.ru/v8/dt/distribution/model";
  private static final String MODE_ENUM = "UserSupportMode";
  private static final String FILE_STATE_ENUM = "ConfigurationFileState";
  /** Состояние файла поставки у конфигурации на полной поддержке: изменение не включено. */
  private static final String DISTRIBUTIVE = "Distributive";
  private static final String CONFIGURATION_DIR = "Configuration";
  private static final String FORMS = "Forms";
  private static final String TEMPLATES = "Templates";

  private static final Pattern ITEM = Pattern.compile("<items\\b[^>]*\\buserId=\"([0-9a-fA-F-]+)\"[^>]*\\buserMode=\"([A-Za-z]+)\"");
  private static final Pattern INFO = Pattern.compile("<parentConfigurationInfos\\b[^>]*>");
  private static final Pattern ROOT = Pattern.compile("<distributionSupport:DistributionSupport\\b[^>]*>");
  private static final Pattern ROOT_UUID = Pattern.compile("<mdclass:[A-Za-z]+\\b[^>]*\\buuid=\"([0-9a-fA-F-]+)\"");
  private static final Pattern ATTRIBUTE = Pattern.compile("\\b(\\w+)=\"([^\"]*)\"");

  /** Операции правки по виду узла: ключ элемента складывается из них, как у выгрузки. */
  private static final Map<String, String> ELEMENT_OP_BY_NODE = Map.of(
      "attributes", "cf-md-attribute",
      "tabularSections", "cf-md-tabular-section",
      "dimensions", "cf-md-dimension",
      "resources", "cf-md-resource",
      "enumValues", "cf-md-enum-value",
      "commands", "cf-md-command",
      "accountingFlags", "cf-md-accounting-flag",
      "extDimensionAccountingFlags", "cf-md-ext-dimension-accounting-flag");

  private static volatile Map<String, Integer> modeByLiteral;
  private static volatile Map<Integer, String> literalByMode;

  private EdtSupportRules() {
  }

  /** Правила поставки проекта. */
  public static final class Rules {
    public String vendor = "";
    public String version = "";
    public String name = "";
    public final Map<String, Integer> modeByUuid = new HashMap<>();
    public String generationId = "";
    /**
     * Возможность изменения включена: файл поставки не в состоянии «Distributive».
     * На полной поддержке правила есть, но заперто всё.
     */
    public boolean editingEnabled;

    public boolean isEmpty() {
      return modeByUuid.isEmpty();
    }

    public String effectiveState(String uuid) {
      if (uuid == null) {
        return null;
      }
      Integer mode = modeByUuid.get(uuid.toLowerCase(Locale.ROOT));
      if (mode == null || mode == SupportRules.MODE_REMOVED) {
        return null;
      }
      return !editingEnabled || mode == SupportRules.MODE_NOT_EDITABLE ? "locked" : "editable";
    }

    public String configurationState() {
      if (isEmpty()) {
        return null;
      }
      return editingEnabled ? "editable" : "locked";
    }
  }

  /** Файл поставки проекта, которому принадлежит файл. */
  public static Path rulesFile(Path anyFile) {
    Path root = sourceRoot(anyFile);
    return root == null ? null : root.resolve(CONFIGURATION_DIR).resolve(RULES_FILE);
  }

  /** Есть ли у проекта файл поставки. */
  public static boolean present(Path anyFile) {
    Path file = rulesFile(anyFile);
    return file != null && Files.isRegularFile(file);
  }

  /**
   * Читает правила проекта.
   *
   * @param anyFile любой файл проекта
   * @return правила; без файла поставки пустые
   * @throws IOException если файл не читается
   */
  public static Rules read(Path anyFile) throws IOException {
    Rules rules = new Rules();
    Path file = rulesFile(anyFile);
    if (file == null || !Files.isRegularFile(file)) {
      return rules;
    }
    byte[] bytes = Files.readAllBytes(file);
    String xml = new String(bytes, StandardCharsets.UTF_8);
    rules.generationId = generationOf(bytes);
    Matcher info = INFO.matcher(xml);
    if (info.find()) {
      Map<String, String> attributes = attributes(info.group());
      rules.vendor = attributes.getOrDefault("providerName", "");
      rules.version = attributes.getOrDefault("configRelease", "");
      rules.name = attributes.getOrDefault("configName", "");
    }
    Matcher item = ITEM.matcher(xml);
    while (item.find()) {
      Integer mode = modes().get(item.group(2));
      if (mode != null) {
        rules.modeByUuid.put(item.group(1).toLowerCase(Locale.ROOT), mode);
      }
    }
    Matcher root = ROOT.matcher(xml);
    String fileState = root.find() ? attributes(root.group()).getOrDefault("fileState", "") : "";
    rules.editingEnabled = !distributive(fileState);
    return rules;
  }

  /** Состояние «Distributive» по литералу схемы: конфигурация на полной поддержке. */
  private static boolean distributive(String fileState) throws IOException {
    EPackage distribution = EdtModel.bundled().packageOf(DISTRIBUTION_NAMESPACE);
    if (distribution != null && distribution.getEClassifier(FILE_STATE_ENUM) instanceof EEnum states) {
      for (EEnumLiteral literal : states.getELiterals()) {
        if (literal.getLiteral().equals(fileState)) {
          return literal.getName().equals(DISTRIBUTIVE);
        }
      }
    }
    return false;
  }

  /**
   * Идентификатор объекта из шапки описания: без разбора всего файла, дерево
   * спрашивает его у каждого объекта.
   */
  public static String rootUuid(Path objectMdo) throws IOException {
    try (java.io.BufferedReader reader = Files.newBufferedReader(objectMdo, StandardCharsets.UTF_8)) {
      StringBuilder head = new StringBuilder();
      for (String line = reader.readLine(); line != null && head.length() < 4096; line = reader.readLine()) {
        head.append(line).append(' ');
        Matcher matcher = ROOT_UUID.matcher(head);
        if (matcher.find()) {
          return matcher.group(1);
        }
      }
    }
    return null;
  }

  /** Состояние субъекта файла: объекта, формы, макета либо владельца модуля. */
  public static String objectState(Path file) throws IOException {
    Rules rules = read(file);
    return rules.isEmpty() ? null : rules.effectiveState(subjectUuid(file));
  }

  /**
   * Состояния объекта, его форм, макетов и элементов одним чтением.
   *
   * Ключи: путь описания объекта от корня исходников, каталоги форм и макетов
   * ({@code Catalogs/Валюты/Forms/ФормаСписка}) и ключи элементов вида
   * {@code element:cf-md-attribute:Код}.
   */
  public static Map<String, String> statesForObject(Path file) throws IOException {
    Map<String, String> out = new LinkedHashMap<>();
    Rules rules = read(file);
    Path objectMdo = objectMdoOf(file);
    Path root = sourceRoot(file);
    if (rules.isEmpty() || objectMdo == null || root == null) {
      return out;
    }
    EdtNode object = EdtObjectReader.read(objectMdo);
    String objectDir = relative(root, objectMdo.getParent());
    out.put(relative(root, objectMdo), rules.effectiveState(object.uuid()));
    for (EdtNode form : object.list("forms")) {
      out.put(objectDir + "/" + FORMS + "/" + form.name(), rules.effectiveState(form.uuid()));
    }
    for (EdtNode template : object.list("templates")) {
      out.put(objectDir + "/" + TEMPLATES + "/" + template.name(), rules.effectiveState(template.uuid()));
    }
    for (Map.Entry<String, String> element : elementSubjects(object).entrySet()) {
      out.put(element.getKey(), rules.effectiveState(element.getValue()));
    }
    return out;
  }

  /** Ключи элементов объекта и их идентификаторы. */
  public static Map<String, String> elementSubjects(Path objectMdo) throws IOException {
    return elementSubjects(EdtObjectReader.read(objectMdo));
  }

  private static Map<String, String> elementSubjects(EdtNode object) {
    Map<String, String> out = new LinkedHashMap<>();
    for (EdtNode child : object.children()) {
      String op = ELEMENT_OP_BY_NODE.get(child.kind());
      if (op == null || child.uuid().isEmpty()) {
        continue;
      }
      out.put("element:" + op + ":" + child.name(), child.uuid());
      if (child.kind().equals("tabularSections")) {
        for (EdtNode nested : child.list("attributes")) {
          if (!nested.uuid().isEmpty()) {
            out.put("element:cf-md-tabular-attribute:" + child.name() + "/" + nested.name(), nested.uuid());
          }
        }
      }
    }
    return out;
  }

  /**
   * Ставит режим субъекту файла, при желании вместе со всем, что в нём.
   *
   * @param file описание объекта, файл формы, макета или модуля
   * @param mode 0 запретить, 1 разрешить, 2 снять с поддержки
   * @param includeChildren распространить на формы, макеты и элементы объекта; у конфигурации на всё
   * @param expectedGeneration поколение файла поставки, которое видел вызывающий, либо {@code null}
   */
  public static void setModeForFile(Path file, int mode, boolean includeChildren, String expectedGeneration)
      throws IOException {
    Path rulesFile = requireRulesFile(file);
    List<String> uuids = new ArrayList<>();
    String own = subjectUuid(file);
    if (own == null) {
      throw new IllegalArgumentException("У файла нет субъекта поддержки: " + file);
    }
    uuids.add(own);
    if (includeChildren) {
      Path objectMdo = objectMdoOf(file);
      if (objectMdo != null && objectMdo.getParent().getFileName().toString().equals(CONFIGURATION_DIR)) {
        uuids.addAll(read(file).modeByUuid.keySet());
      } else if (objectMdo != null) {
        collectUuids(EdtObjectReader.read(objectMdo), uuids);
      }
    }
    setModes(rulesFile, uuids, mode, expectedGeneration);
  }

  /**
   * Ставит режим элементу объекта вместе с его вложенными элементами.
   *
   * @param objectMdo описание объекта
   * @param elementKey ключ из {@link #statesForObject}
   */
  public static void setModeForElement(Path objectMdo, String elementKey, int mode, String expectedGeneration)
      throws IOException {
    Path rulesFile = requireRulesFile(objectMdo);
    Map<String, String> elements = elementSubjects(objectMdo);
    String uuid = elements.get(elementKey);
    if (uuid == null) {
      throw new IllegalArgumentException("В описании объекта нет элемента: " + elementKey);
    }
    List<String> uuids = new ArrayList<>();
    uuids.add(uuid);
    String nested = elementKey.substring(elementKey.lastIndexOf(':') + 1) + "/";
    for (Map.Entry<String, String> entry : elements.entrySet()) {
      String path = entry.getKey().substring(entry.getKey().lastIndexOf(':') + 1);
      if (path.startsWith(nested)) {
        uuids.add(entry.getValue());
      }
    }
    setModes(rulesFile, uuids, mode, expectedGeneration);
  }

  /** Снимает конфигурацию с поддержки: файл поставки удаляется. */
  public static void removeSupport(Path anyFile, String expectedGeneration) throws IOException {
    Path rulesFile = requireRulesFile(anyFile);
    ensureSameGeneration(rulesFile, expectedGeneration);
    Files.delete(rulesFile);
  }

  /**
   * Отказывает в правке того, что поставщик запретил менять.
   *
   * @throws IllegalStateException если субъект файла заперт
   */
  public static void ensureEditable(Path file) throws IOException {
    if (!SupportRules.isEnforced() || !present(file)) {
      return;
    }
    Rules rules = read(file);
    if ("locked".equals(rules.effectiveState(subjectUuid(file)))) {
      throw new IllegalStateException(lockedMessage("Объект", rules));
    }
  }

  /** Так же для элемента объекта по его ключу. */
  public static void ensureElementEditable(Path objectMdo, String elementKey) throws IOException {
    if (!SupportRules.isEnforced() || !present(objectMdo)) {
      return;
    }
    if ("locked".equals(statesForObject(objectMdo).get(elementKey))) {
      throw new IllegalStateException(lockedMessage("Элемент " + elementKey, read(objectMdo)));
    }
  }

  private static String lockedMessage(String subject, Rules rules) {
    return subject + " на поддержке поставщика «" + rules.vendor + "» без возможности изменения."
        + " Включите возможность изменения или снимите объект с поддержки.";
  }

  private static void setModes(Path rulesFile, List<String> uuids, int mode, String expectedGeneration)
      throws IOException {
    String literal = literals().get(mode);
    if (literal == null) {
      throw new IllegalArgumentException("Режим поддержки должен быть 0, 1 или 2, а не " + mode);
    }
    ensureSameGeneration(rulesFile, expectedGeneration);
    String xml = Files.readString(rulesFile, StandardCharsets.UTF_8);
    StringBuilder out = new StringBuilder();
    Matcher item = ITEM.matcher(xml);
    int changed = 0;
    int last = 0;
    while (item.find()) {
      boolean wanted = uuids.stream().anyMatch(uuid -> uuid.equalsIgnoreCase(item.group(1)));
      if (!wanted) {
        continue;
      }
      out.append(xml, last, item.start(2)).append(literal);
      last = item.end(2);
      changed++;
    }
    if (changed == 0) {
      throw new IllegalArgumentException("Объекта нет в поставке: правило поддержки не найдено.");
    }
    out.append(xml, last, xml.length());
    Files.writeString(rulesFile, out.toString(), StandardCharsets.UTF_8);
  }

  private static void ensureSameGeneration(Path rulesFile, String expectedGeneration) throws IOException {
    if (expectedGeneration == null || expectedGeneration.isBlank()) {
      return;
    }
    String actual = generationOf(Files.readAllBytes(rulesFile));
    if (!actual.equals(expectedGeneration.trim())) {
      throw new IllegalStateException("Файл поставки изменился с момента чтения, обновите данные и повторите.");
    }
  }

  private static Path requireRulesFile(Path file) {
    Path rulesFile = rulesFile(file);
    if (rulesFile == null || !Files.isRegularFile(rulesFile)) {
      throw new IllegalArgumentException("Конфигурация не на поддержке: файла поставки нет.");
    }
    return rulesFile;
  }

  /** Все идентификаторы объекта: формы, макеты и элементы всех уровней. */
  private static void collectUuids(EdtNode node, List<String> out) {
    for (EdtNode child : node.children()) {
      if (!child.uuid().isEmpty()) {
        out.add(child.uuid());
      }
      collectUuids(child, out);
    }
  }

  /**
   * Идентификатор субъекта файла.
   *
   * Описание объекта отвечает за себя, файл формы и макета за свою запись в
   * описании владельца, модуль и прочие файлы объекта за сам объект.
   */
  static String subjectUuid(Path file) throws IOException {
    Path normalized = file.toAbsolutePath().normalize();
    Path objectMdo = objectMdoOf(normalized);
    if (objectMdo == null) {
      return null;
    }
    EdtNode object = EdtObjectReader.read(objectMdo);
    if (normalized.equals(objectMdo.toAbsolutePath().normalize())) {
      return object.uuid();
    }
    Path inside = objectMdo.getParent().toAbsolutePath().normalize().relativize(normalized);
    if (inside.getNameCount() >= 2) {
      String section = inside.getName(0).toString();
      String name = inside.getName(1).toString();
      String feature = section.equals(FORMS) ? "forms" : section.equals(TEMPLATES) ? "templates" : null;
      if (feature != null) {
        for (EdtNode child : object.list(feature)) {
          if (child.name().equals(name)) {
            return child.uuid();
          }
        }
        return null;
      }
    }
    return object.uuid();
  }

  /** Описание объекта, которому принадлежит файл: вверх по каталогам до {@code <Имя>/<Имя>.mdo}. */
  static Path objectMdoOf(Path file) {
    Path current = file.toAbsolutePath().normalize();
    if (Files.isRegularFile(current) && current.toString().endsWith(".mdo")) {
      return current;
    }
    for (Path dir = Files.isDirectory(current) ? current : current.getParent(); dir != null; dir = dir.getParent()) {
      Path fileName = dir.getFileName();
      if (fileName == null) {
        return null;
      }
      Path candidate = dir.resolve(fileName + ".mdo");
      if (Files.isRegularFile(candidate)) {
        return candidate;
      }
      if (fileName.toString().equals(CONFIGURATION_DIR) && Files.isRegularFile(dir.resolve("Configuration.mdo"))) {
        return dir.resolve("Configuration.mdo");
      }
    }
    return null;
  }

  /** Корень исходников проекта: каталог с {@code Configuration/Configuration.mdo}; вне проекта {@code null}. */
  public static Path sourceRoot(Path file) {
    for (Path dir = file.toAbsolutePath().normalize(); dir != null; dir = dir.getParent()) {
      if (Files.isRegularFile(dir.resolve(EdtLayout.CONFIGURATION_MDO))) {
        return dir;
      }
    }
    return null;
  }

  private static String relative(Path root, Path file) {
    return root.relativize(file.toAbsolutePath().normalize()).toString().replace('\\', '/');
  }

  private static Map<String, String> attributes(String tag) {
    Map<String, String> out = new HashMap<>();
    Matcher matcher = ATTRIBUTE.matcher(tag);
    while (matcher.find()) {
      out.put(matcher.group(1), matcher.group(2).replace("&quot;", "\"").replace("&amp;", "&"));
    }
    return out;
  }

  /** Поколение файла: по нему вызывающий узнаёт, что правила менялись у него за спиной. */
  static String generationOf(byte[] bytes) {
    try {
      byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
      StringBuilder hex = new StringBuilder();
      for (int i = 0; i < 8; i++) {
        hex.append(String.format(Locale.ROOT, "%02x", digest[i]));
      }
      return hex.toString();
    } catch (NoSuchAlgorithmException error) {
      throw new IllegalStateException(error);
    }
  }

  /** Режимы из схемы поставки: значения литералов совпадают с режимами конфигуратора. */
  private static Map<String, Integer> modes() throws IOException {
    Map<String, Integer> loaded = modeByLiteral;
    if (loaded == null) {
      loadModes();
      loaded = modeByLiteral;
    }
    return loaded;
  }

  private static Map<Integer, String> literals() throws IOException {
    Map<Integer, String> loaded = literalByMode;
    if (loaded == null) {
      loadModes();
      loaded = literalByMode;
    }
    return loaded;
  }

  private static synchronized void loadModes() throws IOException {
    if (modeByLiteral != null) {
      return;
    }
    EPackage distribution = EdtModel.bundled().packageOf(DISTRIBUTION_NAMESPACE);
    if (distribution == null || !(distribution.getEClassifier(MODE_ENUM) instanceof EEnum modes)) {
      throw new IOException("В сборке нет метамодели поставки EDT.");
    }
    Map<String, Integer> byLiteral = new HashMap<>();
    Map<Integer, String> byMode = new HashMap<>();
    for (EEnumLiteral literal : modes.getELiterals()) {
      byLiteral.put(literal.getLiteral(), literal.getValue());
      byMode.put(literal.getValue(), literal.getLiteral());
    }
    modeByLiteral = byLiteral;
    literalByMode = byMode;
  }
}
