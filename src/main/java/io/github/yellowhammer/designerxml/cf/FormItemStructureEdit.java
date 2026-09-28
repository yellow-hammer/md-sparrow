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

import io.github.yellowhammer.designerxml.DesignerXml;
import io.github.yellowhammer.designerxml.SchemaVersion;

import jakarta.xml.bind.JAXBException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Структурная правка элементов управляемой формы в выгрузке конфигуратора: добавить,
 * удалить, переименовать, перенести, привязать к данным.
 *
 * <p>Правится текст {@code Ext/Form.xml}: меняются только строки затронутого элемента, остальной
 * файл остаётся байт в байт. Новый элемент пишется со служебными узлами, как его пишет платформа
 * ({@link FormScaffold}), и получает номера после наибольшего номера в форме. После правки форма
 * читается моделью формата и сверяется с тем, что просили.
 */
public final class FormItemStructureEdit {

  /** Виды элементов, в которые вкладываются другие элементы. */
  private static final Set<String> CONTAINERS = Set.of("UsualGroup", "Page", "Pages", "Table", "ColumnGroup");
  /** Виды, которые не бывают колонками таблицы. */
  private static final Set<String> NOT_COLUMNS = Set.of("UsualGroup", "Page", "Pages", "Table");
  /** Вид элемента по ключу описания. */
  private static final Map<String, String> KIND_TYPES = Map.of(
    "input", "InputField", "check", "CheckBoxField", "label", "LabelDecoration",
    "group", "UsualGroup", "page", "Page", "pages", "Pages", "table", "Table");
  /**
   * Суффиксы имён служебных узлов: по-русски, как пишет конфигуратор русской конфигурации,
   * и по-английски, как они встречаются в формах, созданных по английским шаблонам.
   */
  private static final List<String> SERVICE_SUFFIXES = List.of(
    "РасширеннаяПодсказка", "КонтекстноеМеню", "КоманднаяПанель",
    "СтрокаПоиска", "СостояниеПросмотра", "УправлениеПоиском",
    "ExtendedTooltip", "ContextMenu", "CommandBar", "SearchString", "ViewStatus", "SearchControl");
  /**
   * Ссылки на элемент по имени: источник дополнения таблицы, группа пользовательских настроек,
   * таблица команды. Поля отбора условного оформления формы ищутся отдельно.
   */
  private static final List<Pattern> REFERENCES = List.of(
    Pattern.compile("(<AdditionSource>\\s*<Item>)([^<]*)(</Item>)"),
    Pattern.compile("(<UserSettingsGroup>)([^<]*)(</UserSettingsGroup>)"),
    Pattern.compile("(<AssociatedTableElementId\\b[^>]*>)([^<]*)(</AssociatedTableElementId>)"));
  private static final Pattern SELECTION = Pattern.compile(
    "<(\\w+:)?selection>(.*?)</(\\w+:)?selection>", Pattern.DOTALL);
  private static final Pattern SELECTION_FIELD = Pattern.compile("(<(?:\\w+:)?field>)([^<]*)(</(?:\\w+:)?field>)");

  private FormItemStructureEdit() {
  }

  // ---------- операции ----------

  /**
   * Добавляет элемент в форму.
   *
   * @param parentId   номер элемента-владельца; пусто - сама форма
   * @param beforeId   номер элемента того же владельца, перед которым встаёт новый; пусто - в конец
   * @param definition описание элемента в JSON, как у {@code cf-form-compile}:
   *                   {@code {"input": "Имя", "dataPath": "Объект.Реквизит", "title": "…"}}, у группы,
   *                   страниц и таблицы - вложенные {@code items}
   * @return номер нового элемента
   */
  public static String add(Path formXml, SchemaVersion version, String parentId, String beforeId, String definition)
    throws IOException, JAXBException {
    FormScaffold.FormItemDef item = FormScaffold.FormItemDef.parse(
      new com.google.gson.Gson().fromJson(required(definition, "payloadJson"), Map.class));
    String[] created = new String[1];
    edit(formXml, version, (xml, tree) -> {
      FormTree.Node parent = parent(tree, parentId);
      checkPlacement(parent, KIND_TYPES.get(item.kind()));
      Set<String> names = new HashSet<>();
      collectNames(item, names);
      for (String name : names) {
        if (tree.hasName(name)) {
          throw new IllegalArgumentException("В форме уже есть элемент " + name + ".");
        }
      }
      FormScaffold.IdCounter ids = new FormScaffold.IdCounter(tree.maxId());
      created[0] = String.valueOf(tree.maxId() + 1);
      String eol = XmlLines.newline(xml);
      Insertion at = insertion(xml, tree, parent, beforeId);
      StringBuilder block = new StringBuilder();
      FormScaffold.appendItem(block, item, version, ids, at.depth, eol);
      return List.of(at.replacement(block.toString()));
    }, updated -> requireItem(updated, created[0]));
    return created[0];
  }

  /**
   * Удаляет элемент со всем вложенным. Элемент, на который ссылаются другие элементы формы
   * (источник дополнения, группа настроек, условное оформление), не удаляется: ссылка стала бы
   * битой.
   */
  public static void delete(Path formXml, SchemaVersion version, String itemId) throws IOException, JAXBException {
    edit(formXml, version, (xml, tree) -> {
      FormTree.Node node = ownItem(tree, itemId);
      Set<String> names = new HashSet<>();
      node.subtree().forEach(n -> names.add(n.name));
      for (Reference reference : references(xml, tree)) {
        boolean inside = reference.start >= node.start && reference.start < node.end;
        if (!inside && names.contains(reference.name)) {
          throw new IllegalArgumentException("На элемент " + reference.name + " ссылается форма (" + reference.kind
            + "): сначала уберите ссылку.");
        }
      }
      return List.of(removal(xml, node));
    }, updated -> requireNoItem(updated, itemId));
  }

  /**
   * Переименовывает элемент. Служебные узлы с именем от старого имени (подсказка, контекстное
   * меню, дополнения таблицы) переименовываются вместе с ним, как это делает конфигуратор, а
   * ссылки формы на них переходят на новые имена.
   */
  public static void rename(Path formXml, SchemaVersion version, String itemId, String newName)
    throws IOException, JAXBException {
    CatalogNameConstraints.check(newName);
    edit(formXml, version, (xml, tree) -> {
      FormTree.Node node = ownItem(tree, itemId);
      String old = node.name;
      if (old.equals(newName)) {
        return List.of();
      }
      Map<String, String> renames = new java.util.LinkedHashMap<>();
      renames.put(old, newName);
      List<FormTree.Node> renamed = new ArrayList<>();
      renamed.add(node);
      collectServiceRenames(node, old, newName, renames, renamed);
      for (String name : renames.values()) {
        if (tree.hasName(name)) {
          throw new IllegalArgumentException("В форме уже есть элемент " + name + ".");
        }
      }
      List<XmlGranularPatch.Replacement> out = new ArrayList<>();
      for (FormTree.Node target : renamed) {
        out.add(nameAttribute(xml, target, renames.get(target.name)));
      }
      for (Reference reference : references(xml, tree)) {
        String to = renames.get(reference.name);
        if (to != null) {
          out.add(new XmlGranularPatch.Replacement(reference.start, reference.end, to));
        }
      }
      return out;
    }, updated -> requireName(updated, itemId, newName));
  }

  /**
   * Переносит элемент к другому владельцу или на другое место у того же. Номера элементов
   * не меняются.
   *
   * @param parentId новый владелец; пусто - сама форма
   * @param beforeId элемент нового владельца, перед которым встаёт перенесённый; пусто - в конец
   */
  public static void move(Path formXml, SchemaVersion version, String itemId, String parentId, String beforeId)
    throws IOException, JAXBException {
    edit(formXml, version, (xml, tree) -> {
      FormTree.Node node = ownItem(tree, itemId);
      FormTree.Node parent = parent(tree, parentId);
      if (node.subtree().contains(parent)) {
        throw new IllegalArgumentException("Элемент нельзя перенести в самого себя.");
      }
      checkPlacement(parent, node.type);
      if (itemId.equals(beforeId)) {
        return List.of();
      }
      boolean alone = node.parent.items.size() == 1;
      if (alone && parent == node.parent) {
        return List.of();
      }
      Insertion at = insertion(xml, tree, parent, beforeId);
      int from = lineStart(xml, node.start);
      String block = reindent(xml.substring(from, lineEnd(xml, node.end)), at.depth - indent(xml, node.start));
      return List.of(removal(xml, node), at.replacement(block));
    }, updated -> requireItem(updated, itemId));
  }

  /**
   * Привязывает элемент к данным: пишет {@code DataPath}, проверив путь. Первая часть пути -
   * реквизит формы; у реквизита-таблицы дальше идёт колонка, у реквизита типа объекта
   * конфигурации - его реквизит, стандартный реквизит, табличная часть и её реквизит. Путь
   * внутри динамического списка и прочих типов проверяется только по реквизиту формы.
   */
  public static void bind(Path formXml, SchemaVersion version, String itemId, String dataPath)
    throws IOException, JAXBException {
    String path = required(dataPath, "dataPath").trim();
    FormContentDto content = FormContentRead.read(formXml, version);
    checkDataPath(content.attributes, path, (kind, name) -> objectOf(formXml, version, kind, name));
    FormItemPropertyChangeDto change = new FormItemPropertyChangeDto();
    change.itemId = required(itemId, "itemId");
    change.property = "DataPath";
    change.value = path;
    FormItemPropertyEdit.apply(formXml, version, List.of(change));
  }

  // ---------- общее ----------

  /** Правка текста формы по её разобранному дереву. */
  @FunctionalInterface
  interface Change {
    List<XmlGranularPatch.Replacement> replacements(String xml, FormTree tree) throws IOException, JAXBException;
  }

  /** Сверка формы, прочитанной моделью после правки, с тем, что просили. */
  @FunctionalInterface
  interface Check {
    void verify(FormContentDto updated);
  }

  /**
   * Правит форму: замены по тексту, проверка моделью формата и сверка, затем запись. Пустой список
   * замен - правка не нужна, файл не трогается.
   */
  static void edit(Path formXml, SchemaVersion version, Change change, Check check)
    throws IOException, JAXBException {
    OrdinaryForms.refuse(formXml);
    SupportRules.ensureEditable(formXml);
    ConfigurationLanguage.with(formXml, () -> {
      String xml = Files.readString(formXml, StandardCharsets.UTF_8);
      List<XmlGranularPatch.Replacement> replacements = change.replacements(xml, FormTree.parse(xml));
      if (replacements.isEmpty()) {
        return null;
      }
      String updated = XmlGranularPatch.apply(xml, replacements);
      FormContentDto read;
      try (InputStream in = new ByteArrayInputStream(updated.getBytes(StandardCharsets.UTF_8))) {
        if (DesignerXml.unmarshal(version, in) == null) {
          throw new IOException("Содержимое формы после правки не прочиталось моделью схемы.");
        }
        read = FormContentRead.read(updated.getBytes(StandardCharsets.UTF_8), version);
      }
      FormTree.parse(updated);
      check.verify(read);
      Files.writeString(formXml, updated, StandardCharsets.UTF_8);
      return null;
    });
  }

  private static FormTree.Node parent(FormTree tree, String parentId) {
    if (parentId == null || parentId.isBlank()) {
      return tree.form;
    }
    FormTree.Node parent = tree.item(parentId.trim());
    if (parent == null || parent.attached) {
      throw new IllegalArgumentException("В форме нет элемента с номером " + parentId + ".");
    }
    if (!CONTAINERS.contains(parent.type)) {
      throw new IllegalArgumentException("Элемент " + parent.name + " (" + parent.type + ") не содержит элементов.");
    }
    return parent;
  }

  private static FormTree.Node ownItem(FormTree tree, String itemId) {
    FormTree.Node node = tree.item(required(itemId, "itemId").trim());
    if (node == null) {
      throw new IllegalArgumentException("В форме нет элемента с номером " + itemId + ".");
    }
    if (node.attached) {
      throw new IllegalArgumentException("Служебный узел " + node.name + " правится вместе со своим элементом.");
    }
    return node;
  }

  private static void checkPlacement(FormTree.Node parent, String type) {
    checkPlacement(parent.isForm() ? null : parent.type, parent.name, type);
  }

  /**
   * Страница живёт только в страницах, у страниц - только страницы, у таблицы - колонки.
   *
   * @param parentType вид владельца в записи конфигуратора; {@code null} - сама форма
   * @param parentName имя владельца для текста отказа
   * @param type       вид размещаемого элемента
   */
  public static void checkPlacement(String parentType, String parentName, String type) {
    boolean ok;
    if ("Pages".equals(parentType)) {
      ok = "Page".equals(type);
    } else if ("Page".equals(type)) {
      ok = false;
    } else if ("Table".equals(parentType) || "ColumnGroup".equals(parentType)) {
      ok = !NOT_COLUMNS.contains(type);
    } else {
      ok = true;
    }
    if (!ok) {
      throw new IllegalArgumentException("Элемент вида " + type + " не размещается в "
        + (parentType == null ? "форме" : parentType + " " + parentName) + ".");
    }
  }

  /** Вид элемента в записи конфигуратора по ключу описания: {@code input} - {@code InputField}. */
  public static String kindType(String kind) {
    return KIND_TYPES.get(kind);
  }

  /** Может ли элемент этого вида содержать другие элементы. */
  public static boolean container(String type) {
    return CONTAINERS.contains(type);
  }

  /** Имена элемента и вложенных в описании; повтор имени - отказ. */
  public static void collectNames(FormScaffold.FormItemDef item, Set<String> out) {
    if (!out.add(item.name())) {
      throw new IllegalArgumentException("Имя " + item.name() + " повторяется в описании.");
    }
    item.items().forEach(child -> collectNames(child, out));
  }

  /** Служебные узлы элемента, названные от его имени: у них меняется только начало имени. */
  private static void collectServiceRenames(
    FormTree.Node node, String old, String newName, Map<String, String> renames, List<FormTree.Node> out) {
    for (FormTree.Node service : node.attachedNodes) {
      if (service.name != null && service.name.startsWith(old)
        && serviceSuffix(service.name.substring(old.length()))) {
        renames.put(service.name, newName + service.name.substring(old.length()));
        out.add(service);
        collectServiceRenames(service, old, newName, renames, out);
      }
    }
  }

  /** Строка целиком из суффиксов служебных узлов: {@code СтрокаПоискаКонтекстноеМеню}. */
  public static boolean serviceSuffix(String rest) {
    if (rest.isEmpty()) {
      return false;
    }
    String left = rest;
    while (!left.isEmpty()) {
      String current = left;
      Optional<String> suffix = SERVICE_SUFFIXES.stream().filter(current::endsWith).findFirst();
      if (suffix.isEmpty()) {
        return false;
      }
      left = left.substring(0, left.length() - suffix.get().length());
    }
    return true;
  }

  private static XmlGranularPatch.Replacement nameAttribute(String xml, FormTree.Node node, String newName) {
    int tagEnd = xml.indexOf('>', node.start);
    Matcher name = Pattern.compile("\\sname=\"([^\"]*)\"").matcher(xml).region(node.start, tagEnd);
    if (!name.find()) {
      throw new IllegalStateException("У элемента " + node.name + " нет имени в файле.");
    }
    return new XmlGranularPatch.Replacement(name.start(1), name.end(1), newName);
  }

  /** Ссылка формы на элемент по имени. */
  private record Reference(String kind, String name, int start, int end) {
  }

  private static List<Reference> references(String xml, FormTree tree) {
    List<Reference> out = new ArrayList<>();
    for (Pattern pattern : REFERENCES) {
      Matcher m = pattern.matcher(xml);
      while (m.find()) {
        out.add(new Reference(tag(m.group(1)), m.group(2).trim(), m.start(2), m.end(2)));
      }
    }
    if (tree.conditionalAppearanceStart >= 0) {
      Matcher selection = SELECTION.matcher(xml).region(tree.conditionalAppearanceStart, tree.conditionalAppearanceEnd);
      while (selection.find()) {
        Matcher field = SELECTION_FIELD.matcher(xml).region(selection.start(2), selection.end(2));
        while (field.find()) {
          out.add(new Reference("условное оформление", field.group(2).trim(), field.start(2), field.end(2)));
        }
      }
    }
    return out;
  }

  private static String tag(String opening) {
    Matcher m = Pattern.compile("<(\\w+)").matcher(opening);
    String last = "";
    while (m.find()) {
      last = m.group(1);
    }
    return last;
  }

  // ---------- текст ----------

  /** Место нового элемента: замена и отступ элемента в табуляциях. */
  private record Insertion(int at, int depth, String prefix, String suffix) {
    XmlGranularPatch.Replacement replacement(String block) {
      return new XmlGranularPatch.Replacement(at, at, prefix + block + suffix);
    }
  }

  private static Insertion insertion(String xml, FormTree tree, FormTree.Node parent, String beforeId) {
    boolean before = beforeId != null && !beforeId.isBlank();
    if (parent.childItemsStart >= 0) {
      int depth = indent(xml, parent.childItemsStart) + 1;
      if (before) {
        FormTree.Node sibling = parent.items.stream()
          .filter(item -> beforeId.trim().equals(item.id))
          .findFirst()
          .orElseThrow(() -> new IllegalArgumentException("У владельца нет элемента с номером " + beforeId + "."));
        return new Insertion(lineStart(xml, sibling.start), depth, "", "");
      }
      int closing = xml.lastIndexOf("</", parent.childItemsEnd - 1);
      return new Insertion(lineStart(xml, closing), depth, "", "");
    }
    if (before) {
      throw new IllegalArgumentException("У владельца нет вложенных элементов.");
    }
    String eol = XmlLines.newline(xml);
    int at;
    int depth;
    if (parent.isForm()) {
      // У формы вложенные элементы идут перед реквизитами
      int anchor = tree.attributesStart >= 0 ? tree.attributesStart : xml.lastIndexOf("</", parent.end - 1);
      at = lineStart(xml, anchor);
      depth = indent(xml, anchor);
    } else {
      // У элемента вложенные элементы - последний узел
      if (xml.charAt(parent.end - 2) == '/') {
        throw new IllegalArgumentException("Элемент " + parent.name + " записан пустым тегом.");
      }
      int closing = xml.lastIndexOf("</", parent.end - 1);
      at = lineStart(xml, closing);
      depth = indent(xml, parent.start) + 1;
    }
    String tabs = "\t".repeat(depth);
    return new Insertion(at, depth + 1, tabs + "<ChildItems>" + eol, tabs + "</ChildItems>" + eol);
  }

  /** Строки элемента; последний элемент уносит с собой и пустой теперь {@code ChildItems}. */
  private static XmlGranularPatch.Replacement removal(String xml, FormTree.Node node) {
    FormTree.Node parent = node.parent;
    if (parent.items.size() == 1) {
      return new XmlGranularPatch.Replacement(
        lineStart(xml, parent.childItemsStart), lineEnd(xml, parent.childItemsEnd), "");
    }
    return new XmlGranularPatch.Replacement(lineStart(xml, node.start), lineEnd(xml, node.end), "");
  }

  private static int lineStart(String xml, int offset) {
    int i = offset;
    while (i > 0 && xml.charAt(i - 1) != '\n') {
      i--;
    }
    return i;
  }

  /** Смещение за переводом строки, которым кончается строка с {@code offset}. */
  private static int lineEnd(String xml, int offset) {
    int newline = xml.indexOf('\n', offset);
    return newline < 0 ? xml.length() : newline + 1;
  }

  private static int indent(String xml, int offset) {
    int start = lineStart(xml, offset);
    int i = start;
    while (i < xml.length() && xml.charAt(i) == '\t') {
      i++;
    }
    return i - start;
  }

  /** Сдвигает каждую строку блока на {@code delta} табуляций. */
  private static String reindent(String block, int delta) {
    if (delta == 0) {
      return block;
    }
    StringBuilder out = new StringBuilder();
    for (String line : block.split("(?<=\n)")) {
      if (delta > 0) {
        out.append("\t".repeat(delta)).append(line);
      } else {
        int strip = 0;
        while (strip < -delta && strip < line.length() && line.charAt(strip) == '\t') {
          strip++;
        }
        out.append(line.substring(strip));
      }
    }
    return out.toString();
  }

  // ---------- путь к данным ----------

  /** Строение объекта конфигурации по виду и имени из типа реквизита формы. */
  @FunctionalInterface
  public interface ObjectLookup {
    /**
     * @param kind вид из типа: {@code Catalog} для {@code cfg:CatalogObject.Товары}
     * @param name имя объекта
     * @return строение объекта; пусто, если его не проверить (внешний объект, нет конфигурации)
     */
    Optional<MdObjectStructureDto> find(String kind, String name) throws IOException, JAXBException;
  }

  /**
   * Проверяет путь к данным элемента. Первая часть пути - реквизит формы; у реквизита-таблицы
   * дальше идёт колонка, у реквизита типа объекта конфигурации - его реквизит, стандартный
   * реквизит, табличная часть и её реквизит.
   */
  public static void checkDataPath(List<FormAttributeDto> attributes, String path, ObjectLookup lookup)
    throws IOException, JAXBException {
    String[] parts = path.split("\\.");
    FormAttributeDto attribute = attributes.stream()
      .filter(a -> parts[0].equals(a.name))
      .findFirst()
      .orElseThrow(() -> new IllegalArgumentException("У формы нет реквизита " + parts[0] + "."));
    if (parts.length == 1) {
      return;
    }
    if (!attribute.columns.isEmpty()) {
      if (attribute.columns.stream().noneMatch(c -> parts[1].equals(c.name))) {
        throw new IllegalArgumentException("У реквизита " + parts[0] + " нет колонки " + parts[1] + ".");
      }
      return;
    }
    Optional<MdObjectStructureDto> object = objectOf(attribute, lookup);
    if (object.isEmpty()) {
      return;
    }
    MdObjectStructureDto structure = object.get();
    String field = parts[1];
    for (MdObjectStructureDto.MdTabularSectionDto section : concat(structure.tabularSections,
      structure.standardTabularSections)) {
      if (field.equals(section.name)) {
        if (parts.length > 2 && section.attributes.stream().noneMatch(a -> parts[2].equals(a.name))
          && !section.standardAttributes.contains(parts[2])
          && !StandardAttributeLabels.ofTabularSection().containsKey(parts[2])
          && !StandardAttributeLabels.ofStandardTabularSection(structure.kind, field).containsKey(parts[2])) {
          throw new IllegalArgumentException("У табличной части " + field + " нет реквизита " + parts[2] + ".");
        }
        return;
      }
    }
    // Стандартные реквизиты в файле объекта записаны не всегда: у нового их нет вовсе
    boolean known = structure.attributes.stream().anyMatch(a -> field.equals(a.name))
      || structure.standardAttributes.contains(field)
      || StandardAttributeLabels.ofObject(structure.kind).containsKey(field)
      || structure.dimensions.contains(field)
      || structure.resources.contains(field);
    if (!known) {
      throw new IllegalArgumentException("У " + structure.kind + " " + structure.internalName + " нет реквизита "
        + field + ".");
    }
  }

  private static <T> List<T> concat(List<T> first, List<T> second) {
    List<T> out = new ArrayList<>(first);
    out.addAll(second);
    return out;
  }

  /** Объект конфигурации, чей тип у реквизита формы: {@code cfg:CatalogObject.Товары}. */
  private static Optional<MdObjectStructureDto> objectOf(FormAttributeDto attribute, ObjectLookup lookup)
    throws IOException, JAXBException {
    if (attribute.type == null || attribute.type.types == null || attribute.type.types.size() != 1) {
      return Optional.empty();
    }
    Matcher type = Pattern.compile("^cfg:(\\w+?)(Object|RecordManager)\\.(.+)$").matcher(attribute.type.types.get(0));
    if (!type.matches()) {
      return Optional.empty();
    }
    return lookup.find(type.group(1), type.group(3));
  }

  /** Объект конфигурации выгрузки: владелец формы либо объект из каталога конфигурации. */
  private static Optional<MdObjectStructureDto> objectOf(Path formXml, SchemaVersion version, String kind, String name)
    throws IOException, JAXBException {
    Path owner = ownerXml(formXml);
    if (owner != null && Files.isRegularFile(owner)) {
      MdObjectStructureDto structure = MdObjectStructureRead.read(owner, version);
      if (name.equals(structure.internalName)) {
        return Optional.of(structure);
      }
    }
    Path root = configurationRoot(formXml);
    if (root == null || kind.startsWith("External")) {
      return Optional.empty();
    }
    Optional<Path> xml = CfObjectPathResolver.objectXml(root, kind, name);
    if (xml.isEmpty()) {
      throw new IllegalArgumentException("Объекта " + kind + "." + name + " нет в конфигурации.");
    }
    return Optional.of(MdObjectStructureRead.read(xml.get(), version));
  }

  /** Описание владельца формы: {@code <Вид>/<Объект>/Forms/<Форма>/Ext/Form.xml} - {@code <Вид>/<Объект>.xml}. */
  private static Path ownerXml(Path formXml) {
    Path ext = formXml.toAbsolutePath().normalize().getParent();
    Path form = ext == null ? null : ext.getParent();
    Path forms = form == null ? null : form.getParent();
    Path owner = forms == null ? null : forms.getParent();
    if (owner == null || !"Forms".equals(String.valueOf(forms.getFileName()))) {
      return null;
    }
    return owner.resolveSibling(owner.getFileName() + ".xml");
  }

  private static Path configurationRoot(Path formXml) {
    Path current = formXml.toAbsolutePath().normalize().getParent();
    while (current != null) {
      if (Files.isRegularFile(current.resolve(CfLayout.CONFIGURATION_XML))) {
        return current;
      }
      current = current.getParent();
    }
    return null;
  }

  // ---------- сверка ----------

  private static FormItemDto find(List<FormItemDto> items, String id) {
    for (FormItemDto item : items) {
      if (id.equals(item.id)) {
        return item;
      }
      FormItemDto nested = find(item.items, id);
      if (nested != null) {
        return nested;
      }
    }
    return null;
  }

  private static void requireItem(FormContentDto content, String id) {
    if (find(content.items, id) == null) {
      throw new IllegalStateException("После правки в форме нет элемента " + id + ".");
    }
  }

  private static void requireNoItem(FormContentDto content, String id) {
    if (find(content.items, id) != null) {
      throw new IllegalStateException("После удаления в форме остался элемент " + id + ".");
    }
  }

  private static void requireName(FormContentDto content, String id, String name) {
    FormItemDto item = find(content.items, id);
    if (item == null || !name.equals(item.name)) {
      throw new IllegalStateException("После переименования у элемента " + id + " другое имя.");
    }
  }

  private static String required(String value, String field) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("обязательное поле не задано: " + field);
    }
    return value;
  }
}
