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
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.stream.XMLStreamException;

import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EPackage;

import com.google.gson.Gson;

import io.github.yellowhammer.designerxml.cf.CatalogNameConstraints;
import io.github.yellowhammer.designerxml.cf.CfObjectPathResolver;
import io.github.yellowhammer.designerxml.cf.ConfigurationLanguage;
import io.github.yellowhammer.designerxml.cf.FormAttributeDto;
import io.github.yellowhammer.designerxml.cf.FormContentDto;
import io.github.yellowhammer.designerxml.cf.FormItemDto;
import io.github.yellowhammer.designerxml.cf.FormItemStructureEdit;
import io.github.yellowhammer.designerxml.cf.FormScaffold;
import io.github.yellowhammer.designerxml.cf.MdObjectStructureDto;
import io.github.yellowhammer.edt.EdtObjectReader.EdtNode;
import io.github.yellowhammer.edt.EdtObjectRegions.Region;

/**
 * Структурная правка элементов управляемой формы 1С:EDT: добавить, удалить, переименовать,
 * перенести, привязать к данным.
 *
 * <p>Правится текст {@code Form.form}, а при переименовании и {@code ConditionalAppearance.dcssca}
 * рядом с ним: меняются только строки затронутого элемента. Контракт тот же, что у правки
 * выгрузки конфигуратора ({@link FormItemStructureEdit}): те же описания, номера и отказы. Новый
 * элемент пишется так, как 1С:EDT записывает элемент, который конфигуратор выгружает по
 * описанию {@link FormScaffold}: со значениями по умолчанию, которые EDT пишет явно.
 */
public final class EdtFormItemStructureEdit {

  private static final String FORM = "http://g5.1c.ru/v8/dt/form";
  private static final String XSI = "http://www.w3.org/2001/XMLSchema-instance";
  private static final String INDENT = "  ";
  private static final String CONDITIONAL_APPEARANCE = "ConditionalAppearance.dcssca";
  /** Ссылки на элемент по имени в файле формы: источник дополнения, группа настроек, таблица команды. */
  private static final List<Pattern> REFERENCES = List.of(
      Pattern.compile("(<source>)([^<]*)(</source>)"),
      Pattern.compile("(<userSettingsGroup>)([^<]*)(</userSettingsGroup>)"),
      Pattern.compile("(<associatedTableElementId\\b[^>]*>\\s*<value>)([^<]*)(</value>)"));
  private static final Pattern SELECTION = Pattern.compile("<selection>(.*?)</selection>", Pattern.DOTALL);
  private static final Pattern SELECTION_FIELD = Pattern.compile("(<field>)([^<]*)(</field>)");
  private static final Pattern NAME = Pattern.compile("<name>([^<]*)</name>");

  private EdtFormItemStructureEdit() {
  }

  // ---------- операции ----------

  /**
   * Добавляет элемент в форму.
   *
   * @param parentId   номер элемента-владельца; пусто - сама форма
   * @param beforeId   номер элемента того же владельца, перед которым встаёт новый; пусто - в конец
   * @param definition описание элемента в JSON, как у формы конфигуратора
   * @return номер нового элемента
   */
  public static String add(Path formFile, EdtModel model, String parentId, String beforeId, String definition)
      throws IOException {
    FormScaffold.FormItemDef item = FormScaffold.FormItemDef.parse(
        new Gson().fromJson(required(definition, "payloadJson"), Map.class));
    refuseTables(item);
    String[] created = new String[1];
    edit(formFile, model, form -> {
      EdtFormTree.Node parent = parent(form, parentId);
      FormItemStructureEdit.checkPlacement(
          parent.isForm() ? null : form.type(parent), parent.name, FormItemStructureEdit.kindType(item.kind()));
      Set<String> names = new HashSet<>();
      FormItemStructureEdit.collectNames(item, names);
      for (String name : names) {
        if (form.tree.hasName(name)) {
          throw new IllegalArgumentException("В форме уже есть элемент " + name + ".");
        }
      }
      int[] ids = {form.tree.maxId()};
      created[0] = String.valueOf(ids[0] + 1);
      Insertion at = insertion(form, parent, beforeId);
      StringBuilder block = new StringBuilder();
      appendItem(block, item, ids, at.indent, form.eol);
      List<Edit> edits = new ArrayList<>();
      edits.add(new Edit(at.at, at.at, block.toString()));
      Edit namespace = xsiNamespace(form.xml);
      if (namespace != null) {
        edits.add(namespace);
      }
      return edits;
    }, updated -> requireItem(updated, created[0]));
    return created[0];
  }

  /**
   * Удаляет элемент со всем вложенным. Элемент, на который ссылаются другие элементы формы
   * или её условное оформление, не удаляется: ссылка стала бы битой.
   */
  public static void delete(Path formFile, EdtModel model, String itemId) throws IOException {
    edit(formFile, model, form -> {
      EdtFormTree.Node node = ownItem(form.tree, itemId);
      Set<String> names = new HashSet<>();
      node.subtree().forEach(n -> names.add(n.name));
      for (Reference reference : form.references()) {
        boolean inside = reference.inForm && reference.start >= node.start && reference.start < node.end;
        if (!inside && names.contains(reference.name)) {
          throw new IllegalArgumentException("На элемент " + reference.name + " ссылается форма (" + reference.kind
              + "): сначала уберите ссылку.");
        }
      }
      return List.of(removal(form.xml, node));
    }, updated -> requireNoItem(updated, itemId));
  }

  /**
   * Переименовывает элемент. Служебные узлы с именем от старого имени переименовываются вместе
   * с ним, а ссылки формы и её условного оформления переходят на новые имена.
   */
  public static void rename(Path formFile, EdtModel model, String itemId, String newName) throws IOException {
    CatalogNameConstraints.check(newName);
    edit(formFile, model, form -> {
      EdtFormTree.Node node = ownItem(form.tree, itemId);
      String old = node.name;
      if (old.equals(newName)) {
        return List.of();
      }
      Map<String, String> renames = new LinkedHashMap<>();
      renames.put(old, newName);
      List<EdtFormTree.Node> renamed = new ArrayList<>();
      renamed.add(node);
      collectServiceRenames(node, old, newName, renames, renamed);
      for (String name : renames.values()) {
        if (form.tree.hasName(name)) {
          throw new IllegalArgumentException("В форме уже есть элемент " + name + ".");
        }
      }
      List<Edit> out = new ArrayList<>();
      for (EdtFormTree.Node target : renamed) {
        out.add(nameNode(form.xml, target, renames.get(target.name)));
      }
      for (Reference reference : form.references()) {
        String to = renames.get(reference.name);
        if (to != null) {
          out.add(new Edit(reference.start, reference.end, escape(to), reference.inForm));
        }
      }
      return out;
    }, updated -> requireName(updated, itemId, newName));
  }

  /**
   * Переносит элемент к другому владельцу или на другое место у того же. Номера не меняются.
   *
   * @param parentId новый владелец; пусто - сама форма
   * @param beforeId элемент нового владельца, перед которым встаёт перенесённый; пусто - в конец
   */
  public static void move(Path formFile, EdtModel model, String itemId, String parentId, String beforeId)
      throws IOException {
    edit(formFile, model, form -> {
      EdtFormTree.Node node = ownItem(form.tree, itemId);
      EdtFormTree.Node parent = parent(form, parentId);
      if (node.subtree().contains(parent)) {
        throw new IllegalArgumentException("Элемент нельзя перенести в самого себя.");
      }
      FormItemStructureEdit.checkPlacement(
          parent.isForm() ? null : form.type(parent), parent.name, form.type(node));
      if (itemId.equals(beforeId)) {
        return List.of();
      }
      if (parent == node.parent && node.parent.items.size() == 1) {
        return List.of();
      }
      Insertion at = insertion(form, parent, beforeId);
      int from = EdtObjectRegions.lineStart(form.xml, node.start);
      String block = reindent(form.xml.substring(from, lineEnd(form.xml, node.end)),
          at.indent.length() - indent(form.xml, node.start).length());
      return List.of(removal(form.xml, node), new Edit(at.at, at.at, block));
    }, updated -> requireItem(updated, itemId));
  }

  /**
   * Привязывает элемент к данным: пишет {@code dataPath}, проверив путь, как у формы конфигуратора.
   */
  public static void bind(Path formFile, EdtModel model, String itemId, String dataPath) throws IOException {
    String path = required(dataPath, "dataPath").trim();
    edit(formFile, model, form -> {
      EdtFormTree.Node node = ownItem(form.tree, itemId);
      EClass eClass = form.formPackage.getEClassifier(node.xsiType) instanceof EClass found ? found : null;
      if (eClass == null || eClass.getEStructuralFeature("dataPath") == null) {
        throw new IllegalArgumentException("Элемент " + node.name + " не привязывается к данным.");
      }
      checkDataPath(formFile, model, form, path);
      Region region = new Region(node.start, node.end);
      String indent = indent(form.xml, node.start) + INDENT;
      String element = "<dataPath xsi:type=\"form:DataPath\">" + form.eol
          + indent + INDENT + "<segments>" + escape(path) + "</segments>" + form.eol
          + indent + "</dataPath>";
      for (EdtObjectRegions.Child child : EdtObjectRegions.children(form.xml, region)) {
        if ("dataPath".equals(child.name())) {
          return List.of(new Edit(child.region().start(), child.region().end(), element));
        }
      }
      int at = EdtFormItemPropertyEdit.insertionPoint(form.xml, region, eClass, "dataPath");
      return List.of(new Edit(at, at, indent + element + form.eol));
    }, updated -> requireDataPath(updated, itemId, path));
  }

  // ---------- общее ----------

  /** Правка участка файла формы или, с {@code inForm = false}, её условного оформления. */
  private record Edit(int start, int end, String text, boolean inForm) {
    Edit(int start, int end, String text) {
      this(start, end, text, true);
    }
  }

  /** Ссылка на элемент по имени: в файле формы либо в условном оформлении. */
  private record Reference(String kind, String name, int start, int end, boolean inForm) {
  }

  /** Разобранная форма: текст, дерево, виды элементов и условное оформление. */
  private static final class Form {
    final String xml;
    final String appearance;
    final EdtFormTree tree;
    final EPackage formPackage;
    final EClass formClass;
    final String eol;
    final Map<String, String> types = new HashMap<>();

    Form(String xml, String appearance, EdtModel model) throws IOException {
      this.xml = xml;
      this.appearance = appearance;
      this.tree = EdtFormTree.parse(xml);
      this.formPackage = model.packageOf(FORM);
      if (formPackage == null) {
        throw new IllegalStateException("В сборке нет метамодели формы EDT.");
      }
      this.formClass = formPackage.getEClassifier("Form") instanceof EClass found ? found : null;
      this.eol = xml.contains("\r\n") ? "\r\n" : "\n";
      collectTypes(EdtFormContent.read(EdtObjectReader.parse(xml), model).items, types);
    }

    /** Вид элемента в записи конфигуратора: {@code InputField}, {@code UsualGroup}, {@code Table}. */
    String type(EdtFormTree.Node node) {
      return types.getOrDefault(node.id, node.type);
    }

    List<Reference> references() {
      List<Reference> out = new ArrayList<>();
      for (Pattern pattern : REFERENCES) {
        Matcher m = pattern.matcher(xml);
        while (m.find()) {
          out.add(new Reference(tag(m.group(1)), m.group(2).trim(), m.start(2), m.end(2), true));
        }
      }
      if (appearance != null) {
        Matcher selection = SELECTION.matcher(appearance);
        while (selection.find()) {
          Matcher field = SELECTION_FIELD.matcher(appearance).region(selection.start(1), selection.end(1));
          while (field.find()) {
            out.add(new Reference("условное оформление", field.group(2).trim(), field.start(2), field.end(2), false));
          }
        }
      }
      return out;
    }

    private static void collectTypes(List<FormItemDto> items, Map<String, String> out) {
      for (FormItemDto item : items) {
        if (item.id != null) {
          out.put(item.id, item.type);
        }
        collectTypes(item.items, out);
      }
    }
  }

  @FunctionalInterface
  private interface Change {
    List<Edit> edits(Form form) throws IOException, XMLStreamException;
  }

  @FunctionalInterface
  private interface Check {
    void verify(FormContentDto updated);
  }

  private static void edit(Path formFile, EdtModel model, Change change, Check check) throws IOException {
    if (!Files.isRegularFile(formFile)) {
      throw new IllegalArgumentException("Файл формы не найден: " + formFile);
    }
    Path owner = ownerMdo(formFile);
    if (owner != null) {
      EdtSupportRules.ensureEditable(owner);
    }
    try {
      ConfigurationLanguage.with(formFile, () -> {
        editInLanguage(formFile, model, change, check);
        return null;
      });
    } catch (jakarta.xml.bind.JAXBException error) {
      throw new IOException(error);
    }
  }

  private static void editInLanguage(Path formFile, EdtModel model, Change change, Check check)
      throws IOException {
    String xml = Files.readString(formFile, StandardCharsets.UTF_8);
    Path appearanceFile = formFile.resolveSibling(CONDITIONAL_APPEARANCE);
    String appearance = Files.isRegularFile(appearanceFile)
        ? Files.readString(appearanceFile, StandardCharsets.UTF_8)
        : null;
    List<Edit> edits;
    try {
      edits = change.edits(new Form(xml, appearance, model));
    } catch (XMLStreamException error) {
      throw new IOException("Не удалось разобрать форму: " + formFile, error);
    }
    if (edits.isEmpty()) {
      return;
    }
    String updated = patched(xml, edits.stream().filter(Edit::inForm).toList());
    String updatedAppearance = appearance == null
        ? null
        : patched(appearance, edits.stream().filter(edit -> !edit.inForm()).toList());
    EdtFormTree.parse(updated);
    check.verify(EdtFormContent.read(EdtObjectReader.parse(updated), model));
    Files.writeString(formFile, updated, StandardCharsets.UTF_8);
    if (updatedAppearance != null && !updatedAppearance.equals(appearance)) {
      Files.writeString(appearanceFile, updatedAppearance, StandardCharsets.UTF_8);
    }
  }

  /**
   * Описание владельца формы: {@code <Объект>/Forms/<Форма>/Form.form} - {@code <Объект>/<Объект>.mdo},
   * у общей формы - её собственное описание рядом.
   */
  private static Path ownerMdo(Path formFile) {
    Path form = formFile.toAbsolutePath().normalize().getParent();
    if (form == null) {
      return null;
    }
    Path forms = form.getParent();
    if (forms != null && "Forms".equals(String.valueOf(forms.getFileName())) && forms.getParent() != null) {
      Path object = forms.getParent();
      return object.resolve(object.getFileName() + ".mdo");
    }
    return form.resolve(form.getFileName() + ".mdo");
  }

  private static EdtFormTree.Node parent(Form form, String parentId) {
    if (parentId == null || parentId.isBlank()) {
      return form.tree.form;
    }
    EdtFormTree.Node parent = form.tree.item(parentId.trim());
    if (parent == null || parent.attached()) {
      throw new IllegalArgumentException("В форме нет элемента с номером " + parentId + ".");
    }
    String type = form.type(parent);
    if (!FormItemStructureEdit.container(type)) {
      throw new IllegalArgumentException("Элемент " + parent.name + " (" + type + ") не содержит элементов.");
    }
    return parent;
  }

  private static EdtFormTree.Node ownItem(EdtFormTree tree, String itemId) {
    EdtFormTree.Node node = tree.item(required(itemId, "itemId").trim());
    if (node == null) {
      throw new IllegalArgumentException("В форме нет элемента с номером " + itemId + ".");
    }
    if (node.attached()) {
      throw new IllegalArgumentException("Служебный узел " + node.name + " правится вместе со своим элементом.");
    }
    return node;
  }

  /**
   * Таблицу EDT записывает с десятками значений по умолчанию, и в эталонах нет таблицы, по
   * которой их можно сверить с описанием: добавлять её пока не берёмся.
   */
  private static void refuseTables(FormScaffold.FormItemDef item) {
    if ("table".equals(item.kind())) {
      throw new IllegalArgumentException("Добавление таблицы в форму 1С:EDT пока не поддержано.");
    }
    item.items().forEach(EdtFormItemStructureEdit::refuseTables);
  }

  /** Служебные узлы элемента, названные от его имени: у них меняется только начало имени. */
  private static void collectServiceRenames(EdtFormTree.Node node, String old, String newName,
      Map<String, String> renames, List<EdtFormTree.Node> out) {
    for (EdtFormTree.Node service : node.attachedNodes) {
      if (service.name != null && service.name.startsWith(old)
          && FormItemStructureEdit.serviceSuffix(service.name.substring(old.length()))) {
        renames.put(service.name, newName + service.name.substring(old.length()));
        out.add(service);
        collectServiceRenames(service, old, newName, renames, out);
      }
    }
  }

  /** Имя элемента - первый его узел {@code name}. */
  private static Edit nameNode(String xml, EdtFormTree.Node node, String newName) {
    Matcher name = NAME.matcher(xml).region(node.start, node.end);
    if (!name.find() || !name.group(1).equals(escape(node.name))) {
      throw new IllegalStateException("У элемента " + node.name + " нет имени в файле.");
    }
    return new Edit(name.start(1), name.end(1), escape(newName));
  }

  private static String tag(String opening) {
    Matcher m = Pattern.compile("<(\\w+)").matcher(opening);
    String last = "";
    while (m.find()) {
      last = m.group(1);
    }
    return last;
  }

  // ---------- путь к данным ----------

  private static void checkDataPath(Path formFile, EdtModel model, Form form, String path) throws IOException {
    EdtNode root = EdtObjectReader.parse(form.xml);
    FormContentDto content = EdtFormContent.read(root, model);
    // Колонки реквизита-таблицы чтение формы не отдаёт: они берутся из файла
    for (EdtNode node : root.list("attributes")) {
      for (FormAttributeDto attribute : content.attributes) {
        if (attribute.name.equals(node.name())) {
          for (EdtNode column : node.list("columns")) {
            FormAttributeDto dto = new FormAttributeDto();
            dto.name = column.name();
            attribute.columns.add(dto);
          }
        }
      }
    }
    try {
      FormItemStructureEdit.checkDataPath(content.attributes, path, (kind, name) -> objectOf(formFile, model, kind, name));
    } catch (jakarta.xml.bind.JAXBException error) {
      throw new IOException(error);
    }
  }

  /** Строение объекта конфигурации: владелец формы либо объект из каталога исходников проекта. */
  private static Optional<MdObjectStructureDto> objectOf(Path formFile, EdtModel model, String kind, String name)
      throws IOException {
    Path owner = ownerMdo(formFile);
    if (owner != null && Files.isRegularFile(owner)) {
      MdObjectStructureDto structure = EdtObjectStructure.read(owner, model);
      if (name.equals(structure.internalName)) {
        return Optional.of(structure);
      }
    }
    Path root = EdtSupportRules.sourceRoot(formFile);
    String subdir = CfObjectPathResolver.subdirsByType().get(kind);
    if (root == null || subdir == null) {
      return Optional.empty();
    }
    Path mdo = root.resolve(subdir).resolve(name).resolve(name + ".mdo");
    if (!Files.isRegularFile(mdo)) {
      throw new IllegalArgumentException("Объекта " + kind + "." + name + " нет в конфигурации.");
    }
    return Optional.of(EdtObjectStructure.read(mdo, model));
  }

  // ---------- текст ----------

  /** Место нового элемента и его отступ. */
  private record Insertion(int at, String indent) {
  }

  private static Insertion insertion(Form form, EdtFormTree.Node parent, String beforeId)
      throws XMLStreamException {
    String xml = form.xml;
    boolean before = beforeId != null && !beforeId.isBlank();
    if (!parent.items.isEmpty()) {
      String indent = indent(xml, parent.items.get(0).start);
      if (before) {
        EdtFormTree.Node sibling = parent.items.stream()
            .filter(item -> beforeId.trim().equals(item.id))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("У владельца нет элемента с номером " + beforeId + "."));
        return new Insertion(EdtObjectRegions.lineStart(xml, sibling.start), indent);
      }
      return new Insertion(lineEnd(xml, parent.items.get(parent.items.size() - 1).end), indent);
    }
    if (before) {
      throw new IllegalArgumentException("У владельца нет вложенных элементов.");
    }
    EClass ownerClass = parent.isForm()
        ? form.formClass
        : form.formPackage.getEClassifier(parent.xsiType) instanceof EClass found ? found : null;
    int at = EdtFormItemPropertyEdit.insertionPoint(xml, new Region(parent.start, parent.end), ownerClass, "items");
    return new Insertion(at, parent.isForm() ? INDENT : indent(xml, parent.start) + INDENT);
  }

  /** Строки элемента вместе с переводом строки за ним. */
  private static Edit removal(String xml, EdtFormTree.Node node) {
    return new Edit(EdtObjectRegions.lineStart(xml, node.start), lineEnd(xml, node.end), "");
  }

  /** Корню формы нужен префикс {@code xsi}: новая форма EDT пишется без него. */
  private static Edit xsiNamespace(String xml) {
    int root = xml.indexOf("<form:Form");
    int close = root < 0 ? -1 : xml.indexOf('>', root);
    if (root < 0 || close < 0 || xml.substring(root, close).contains("xmlns:xsi=")) {
      return null;
    }
    int at = root + "<form:Form".length();
    return new Edit(at, at, " xmlns:xsi=\"" + XSI + "\"");
  }

  private static String patched(String text, List<Edit> edits) {
    List<Edit> ordered = new ArrayList<>(edits);
    ordered.sort(Comparator.comparingInt(Edit::start).reversed());
    StringBuilder out = new StringBuilder(text);
    int guard = Integer.MAX_VALUE;
    for (Edit edit : ordered) {
      if (edit.end() > guard) {
        throw new IllegalStateException("Правки формы пересекаются.");
      }
      guard = edit.start();
      out.replace(edit.start(), edit.end(), edit.text());
    }
    return out.toString();
  }

  private static int lineEnd(String xml, int offset) {
    int newline = xml.indexOf('\n', offset);
    return newline < 0 ? xml.length() : newline + 1;
  }

  private static String indent(String xml, int offset) {
    int start = EdtObjectRegions.lineStart(xml, offset);
    int i = start;
    while (i < xml.length() && xml.charAt(i) == ' ') {
      i++;
    }
    return xml.substring(start, i);
  }

  /** Сдвигает каждую строку блока на {@code delta} пробелов. */
  private static String reindent(String block, int delta) {
    if (delta == 0) {
      return block;
    }
    StringBuilder out = new StringBuilder();
    for (String line : block.split("(?<=\n)")) {
      if (delta > 0) {
        out.append(" ".repeat(delta)).append(line);
      } else {
        int strip = 0;
        while (strip < -delta && strip < line.length() && line.charAt(strip) == ' ') {
          strip++;
        }
        out.append(line.substring(strip));
      }
    }
    return out.toString();
  }

  // ---------- новый элемент ----------

  /**
   * Элемент в записи EDT. Номера раздаются в том же порядке, что и у формы конфигуратора:
   * элемент, контекстное меню, подсказка, вложенные.
   */
  private static void appendItem(StringBuilder out, FormScaffold.FormItemDef item, int[] ids, String pad, String eol) {
    String in = pad + INDENT;
    switch (item.kind()) {
      case "input", "check" -> {
        int id = ++ids[0];
        int menu = ++ids[0];
        int tooltip = ++ids[0];
        open(out, "FormField", item.name(), id, pad, eol);
        if (item.title() != null) {
          title(out, item.title(), in, eol);
        }
        common(out, in, eol);
        if (item.dataPath() != null) {
          out.append(in).append("<dataPath xsi:type=\"form:DataPath\">").append(eol);
          out.append(in).append(INDENT).append("<segments>").append(escape(item.dataPath())).append("</segments>")
              .append(eol);
          out.append(in).append("</dataPath>").append(eol);
        }
        tooltip(out, item.name(), tooltip, in, eol);
        contextMenu(out, item.name(), menu, in, eol);
        boolean input = "input".equals(item.kind());
        line(out, in, "<type>" + (input ? "InputField" : "CheckBoxField") + "</type>", eol);
        line(out, in, "<editMode>Enter</editMode>", eol);
        line(out, in, "<showInHeader>true</showInHeader>", eol);
        line(out, in, "<headerHorizontalAlign>Left</headerHorizontalAlign>", eol);
        line(out, in, "<showInFooter>true</showInFooter>", eol);
        if (input) {
          line(out, in, "<extInfo xsi:type=\"form:InputFieldExtInfo\">", eol);
          for (String flag : List.of("autoMaxWidth", "autoMaxHeight", "wrap", "chooseType", "typeDomainEnabled",
              "textEdit")) {
            line(out, in + INDENT, "<" + flag + ">true</" + flag + ">", eol);
          }
          line(out, in, "</extInfo>", eol);
        } else {
          line(out, in, "<extInfo xsi:type=\"form:CheckBoxFieldExtInfo\"/>", eol);
        }
      }
      case "label" -> {
        int id = ++ids[0];
        int menu = ++ids[0];
        int tooltip = ++ids[0];
        open(out, "Decoration", item.name(), id, pad, eol);
        title(out, item.title() != null ? item.title() : item.name(), in, eol);
        common(out, in, eol);
        tooltip(out, item.name(), tooltip, in, eol);
        contextMenu(out, item.name(), menu, in, eol);
        labelTail(out, in, eol);
      }
      case "group", "page", "pages" -> {
        int id = ++ids[0];
        int tooltip = ++ids[0];
        open(out, "FormGroup", item.name(), id, pad, eol);
        for (FormScaffold.FormItemDef child : item.items()) {
          appendItem(out, child, ids, in, eol);
        }
        common(out, in, eol);
        // Заголовок страниц конфигуратор по описанию не пишет
        if (item.title() != null && !"pages".equals(item.kind())) {
          title(out, item.title(), in, eol);
        }
        tooltip(out, item.name(), tooltip, in, eol);
        switch (item.kind()) {
          case "group" -> {
            line(out, in, "<type>UsualGroup</type>", eol);
            line(out, in, "<extInfo xsi:type=\"form:UsualGroupExtInfo\">", eol);
            // Горизонтальная группировка у EDT по умолчанию и не пишется
            if (!"horizontal".equals(item.direction())) {
              line(out, in + INDENT, "<group>Vertical</group>", eol);
            }
            line(out, in + INDENT, "<behavior>Auto</behavior>", eol);
            line(out, in + INDENT, "<representation>WeakSeparation</representation>", eol);
            line(out, in + INDENT, "<showLeftMargin>true</showLeftMargin>", eol);
            line(out, in + INDENT, "<united>true</united>", eol);
            if (item.title() != null) {
              line(out, in + INDENT, "<showTitle>true</showTitle>", eol);
            }
            line(out, in + INDENT, "<throughAlign>Auto</throughAlign>", eol);
            line(out, in + INDENT, "<currentRowUse>Auto</currentRowUse>", eol);
            line(out, in, "</extInfo>", eol);
          }
          case "page" -> {
            line(out, in, "<type>Page</type>", eol);
            line(out, in, "<extInfo xsi:type=\"form:PageGroupExtInfo\">", eol);
            line(out, in + INDENT, "<group>Vertical</group>", eol);
            line(out, in + INDENT, "<showTitle>true</showTitle>", eol);
            line(out, in, "</extInfo>", eol);
          }
          default -> {
            line(out, in, "<type>Pages</type>", eol);
            line(out, in, "<extInfo xsi:type=\"form:PagesGroupExtInfo\">", eol);
            line(out, in + INDENT, "<pagesRepresentation>Auto</pagesRepresentation>", eol);
            line(out, in + INDENT, "<currentRowUse>Auto</currentRowUse>", eol);
            line(out, in, "</extInfo>", eol);
          }
        }
      }
      default -> throw new IllegalArgumentException("Неизвестный элемент формы: " + item.kind());
    }
    out.append(pad).append("</items>").append(eol);
  }

  private static void open(StringBuilder out, String xsiType, String name, int id, String pad, String eol) {
    line(out, pad, "<items xsi:type=\"form:" + xsiType + "\">", eol);
    line(out, pad + INDENT, "<name>" + escape(name) + "</name>", eol);
    line(out, pad + INDENT, "<id>" + id + "</id>", eol);
  }

  /** Видимость и доступность, которые EDT пишет у каждого элемента. */
  private static void common(StringBuilder out, String pad, String eol) {
    line(out, pad, "<visible>true</visible>", eol);
    line(out, pad, "<enabled>true</enabled>", eol);
    line(out, pad, "<userVisible>", eol);
    line(out, pad + INDENT, "<common>true</common>", eol);
    line(out, pad, "</userVisible>", eol);
  }

  private static void title(StringBuilder out, String title, String pad, String eol) {
    line(out, pad, "<title>", eol);
    line(out, pad + INDENT, "<key>" + ConfigurationLanguage.current() + "</key>", eol);
    line(out, pad + INDENT, "<value>" + escape(title) + "</value>", eol);
    line(out, pad, "</title>", eol);
  }

  private static void tooltip(StringBuilder out, String owner, int id, String pad, String eol) {
    line(out, pad, "<extendedTooltip>", eol);
    line(out, pad + INDENT, "<name>" + escape(owner) + "РасширеннаяПодсказка</name>", eol);
    line(out, pad + INDENT, "<id>" + id + "</id>", eol);
    labelTail(out, pad + INDENT, eol);
    line(out, pad, "</extendedTooltip>", eol);
  }

  /** Вид надписи и её значения по умолчанию: так же пишется и расширенная подсказка. */
  private static void labelTail(StringBuilder out, String pad, String eol) {
    line(out, pad, "<type>Label</type>", eol);
    line(out, pad, "<autoMaxWidth>true</autoMaxWidth>", eol);
    line(out, pad, "<autoMaxHeight>true</autoMaxHeight>", eol);
    line(out, pad, "<extInfo xsi:type=\"form:LabelDecorationExtInfo\">", eol);
    line(out, pad + INDENT, "<horizontalAlign>Left</horizontalAlign>", eol);
    line(out, pad, "</extInfo>", eol);
  }

  private static void contextMenu(StringBuilder out, String owner, int id, String pad, String eol) {
    line(out, pad, "<contextMenu>", eol);
    line(out, pad + INDENT, "<name>" + escape(owner) + "КонтекстноеМеню</name>", eol);
    line(out, pad + INDENT, "<id>" + id + "</id>", eol);
    line(out, pad + INDENT, "<autoFill>true</autoFill>", eol);
    line(out, pad, "</contextMenu>", eol);
  }

  private static void line(StringBuilder out, String pad, String text, String eol) {
    out.append(pad).append(text).append(eol);
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

  private static void requireDataPath(FormContentDto content, String id, String path) {
    FormItemDto item = find(content.items, id);
    if (item == null || !path.equals(item.dataPath)) {
      throw new IllegalStateException("После привязки у элемента " + id + " другой путь к данным.");
    }
  }

  private static String required(String value, String field) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("обязательное поле не задано: " + field);
    }
    return value;
  }
}
