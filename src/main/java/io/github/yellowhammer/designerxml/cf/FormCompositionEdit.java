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

import io.github.yellowhammer.designerxml.SchemaVersion;

import jakarta.xml.bind.JAXBException;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;

/**
 * Правка состава управляемой формы в выгрузке конфигуратора: реквизиты с колонками, команды,
 * параметры и обработчики событий формы и её элементов.
 *
 * <p>Правится текст {@code Ext/Form.xml}: меняются только строки затронутой записи, остальной файл
 * остаётся байт в байт. Новые записи пишутся в том порядке узлов, в каком их пишет платформа
 * (у реквизита заголовок перед типом, хотя схема перечисляет их наоборот), и получают номера после
 * наибольшего номера своего списка: у реквизитов, у колонок реквизита и у команд нумерация своя,
 * у параметров номера нет. После правки форма читается моделью формата и сверяется с тем, что
 * просили.
 */
public final class FormCompositionEdit {

  /**
   * Узлы со ссылкой на реквизит формы по пути к данным: путь элемента, картинки строки, заголовка
   * и подвала, связи параметров выбора, поле сохраняемых и всегда используемых данных, параметр
   * команды командного интерфейса, результат и расшифровка отчёта, оформление варианта.
   */
  private static final Pattern PATH_REFERENCE = Pattern.compile(
    "<((?:xr:)?DataPath|RowPictureDataPath|TitleDataPath|FooterDataPath|HeaderDataPath|Field|Attribute"
      + "|ReportResult|DetailsData|VariantAppearance)(\\s[^>]*)?>([^<]*)</\\1>");
  /** Таблица дополнительных колонок основного реквизита. */
  private static final Pattern TABLE_REFERENCE = Pattern.compile("<AdditionalColumns\\s[^>]*?table=\"([^\"]*)\"");
  /** Поле в отборе условного оформления формы. */
  private static final Pattern APPEARANCE_FIELD = Pattern.compile(
    "<dcsset:(left|right) xsi:type=\"dcscor:Field\">([^<]*)</dcsset:\\1>");
  /** Ссылка на команду формы: кнопка и командный интерфейс. */
  private static final Pattern COMMAND_REFERENCE = Pattern.compile(
    "<(CommandName|Command)>Form\\.Command\\.([^<]*)</\\1>");
  private static final Pattern MAIN_ATTRIBUTE_TYPE = Pattern.compile("<v8:Type>([^<]*)</v8:Type>");
  /** Хвост свойств корня формы перед командной панелью в порядке схемы logform. */
  private static final List<String> ROOT_TAIL = List.of("UseForFoldersAndItems", "GroupList", "AutoTime",
    "UsePostingMode", "RepostOnWrite", "ReportResult", "DetailsData", "ReportFormType", "VariantAppearance",
    "AutoShowState", "CustomSettingsFolder", "ReportResultViewMode", "ViewModeApplicationOnSetReportResult");
  private static final Pattern EVENT_NAME =Pattern.compile("[A-Za-z][A-Za-z0-9]*");
  private static final Pattern HANDLER_NAME = Pattern.compile("[\\p{L}_][\\p{L}\\p{N}_]*");
  /** Виды вызова обработчика формы расширения по схеме ({@code HandlerCallType}). */
  private static final List<String> CALL_TYPES = List.of("Before", "After", "Override", "ChangeAndValidate");
  /** Узлы команды, которые стоят после её действия. */
  private static final List<String> AFTER_ACTION = List.of(
    "FunctionalOptions", "Representation", "ModifiesSavedData", "CurrentRowUse", "AssociatedTableElementId",
    "ActionPurpose", "SelectedRowsUse");
  /** Префиксы, которые корень формы объявляет всегда: у типа их объявлять незачем. */
  private static final Pattern ROOT_NAMESPACE = Pattern.compile("\\sxmlns:(\\w+)=\"([^\"]*)\"");

  private FormCompositionEdit() {
  }

  // ---------- описания ----------

  /** Описание реквизита или колонки. */
  static final class AttributeDef {
    String name;
    MdTypeDescriptionDto type;
    String title;
    Boolean savedData;
    List<AttributeDef> columns;
  }

  /** Описание команды. */
  static final class CommandDef {
    String name;
    String title;
    String toolTip;
    String action;
  }

  /** Описание параметра. */
  static final class ParameterDef {
    String name;
    MdTypeDescriptionDto type;
    Boolean key;
  }

  static <T> T parse(String json, Class<T> type) {
    try {
      T parsed = new Gson().fromJson(required(json, "payloadJson"), type);
      if (parsed == null) {
        throw new IllegalArgumentException("Пустое описание в payloadJson.");
      }
      return parsed;
    } catch (JsonParseException error) {
      throw new IllegalArgumentException("Некорректное описание в payloadJson: " + error.getMessage(), error);
    }
  }

  // ---------- реквизиты ----------

  /**
   * Добавляет реквизит формы либо, если имя - путь {@code Реквизит.Колонка}, колонку реквизита-таблицы.
   *
   * @param definitionJson {@code {"name": "Товары", "type": {"types": ["v8:ValueTable"]}, "title": "…",
   *                       "savedData": true, "columns": [{"name": "Цена", "type": {…}}]}}
   * @return номер новой записи
   */
  public static String addAttribute(Path formXml, SchemaVersion version, String definitionJson)
    throws IOException, JAXBException {
    AttributeDef def = parse(definitionJson, AttributeDef.class);
    String path = required(def.name, "name").trim();
    checkType(def.type, path);
    String[] created = new String[1];
    FormItemStructureEdit.edit(formXml, version, (xml, tree) -> {
      int dot = path.indexOf('.');
      if (dot < 0) {
        CatalogNameConstraints.check(path);
        if (attribute(tree, path) != null) {
          throw new IllegalArgumentException("У формы уже есть реквизит " + path + ".");
        }
        created[0] = String.valueOf(maxId(tree.attributes) + 1);
        def.name = path;
        return List.of(insertAttribute(xml, tree, attributeXml(xml, def, created[0])));
      }
      FormTree.Entry owner = requireAttribute(tree, path.substring(0, dot));
      AttributeDef column = def;
      column.name = path.substring(dot + 1);
      CatalogNameConstraints.check(column.name);
      checkColumns(xml, owner);
      if (owner.columns.stream().anyMatch(c -> c.name.equals(column.name))) {
        throw new IllegalArgumentException("У реквизита " + owner.name + " уже есть колонка " + column.name + ".");
      }
      created[0] = String.valueOf(maxId(owner.columns) + 1);
      return List.of(insertColumn(xml, owner, columnXml(xml, column, created[0])));
    }, updated -> requireAttribute(updated, path, true));
    return created[0];
  }

  /**
   * Удаляет реквизит или колонку ({@code Реквизит.Колонка}). Реквизит, на путь которого ссылается
   * форма (элемент, условное оформление, командный интерфейс), не удаляется.
   */
  public static void deleteAttribute(Path formXml, SchemaVersion version, String name)
    throws IOException, JAXBException {
    String path = required(name, "name").trim();
    FormItemStructureEdit.edit(formXml, version, (xml, tree) -> {
      FormTree.Entry entry = entryByPath(tree, path);
      for (Reference reference : pathReferences(xml, tree)) {
        boolean inside = reference.start >= entry.start && reference.start < entry.end;
        if (!inside && covers(path, reference.value)) {
          throw new IllegalArgumentException("На реквизит " + path + " ссылается форма (" + reference.kind + ": "
            + reference.value + "): сначала уберите ссылку.");
        }
      }
      if (entry.owner == null) {
        return List.of(removeFromList(xml, entry, tree.attributes, tree.attributesStart, tree.attributesEnd,
          tree.conditionalAppearanceStart >= 0, "<Attributes/>"));
      }
      FormTree.Entry owner = entry.owner;
      boolean additional = xml.substring(owner.columnsStart, owner.columnsEnd).contains("<AdditionalColumns");
      return List.of(removeFromList(xml, entry, owner.columns, owner.columnsStart, owner.columnsEnd,
        additional, null));
    }, updated -> requireAttribute(updated, path, false));
  }

  /**
   * Переименовывает реквизит или колонку ({@code Реквизит.Колонка}); пути к данным элементов,
   * условного оформления и прочие ссылки формы переходят на новое имя.
   */
  public static void renameAttribute(Path formXml, SchemaVersion version, String oldName, String newName)
    throws IOException, JAXBException {
    String path = required(oldName, "oldName").trim();
    String name = required(newName, "newName").trim();
    CatalogNameConstraints.check(name);
    String[] renamed = new String[1];
    FormItemStructureEdit.edit(formXml, version, (xml, tree) -> {
      FormTree.Entry entry = entryByPath(tree, path);
      if (entry.name.equals(name)) {
        return List.of();
      }
      List<FormTree.Entry> siblings = entry.owner == null ? tree.attributes : entry.owner.columns;
      if (siblings.stream().anyMatch(e -> e.name.equals(name))) {
        throw new IllegalArgumentException("Имя " + name + " уже занято.");
      }
      String newPath = entry.owner == null ? name : entry.owner.name + "." + name;
      renamed[0] = newPath;
      List<XmlGranularPatch.Replacement> out = new ArrayList<>();
      out.add(nameAttribute(xml, entry.start, entry.name, name));
      for (Reference reference : pathReferences(xml, tree)) {
        if (covers(path, reference.value)) {
          out.add(new XmlGranularPatch.Replacement(reference.start, reference.end,
            escape(newPath + reference.value.substring(path.length()))));
        }
      }
      return out;
    }, updated -> {
      requireAttribute(updated, renamed[0], true);
      requireAttribute(updated, path, false);
    });
  }

  /** Меняет заголовок и тип реквизита или колонки: то, что задано в описании. */
  public static void setAttribute(Path formXml, SchemaVersion version, String name, String definitionJson)
    throws IOException, JAXBException {
    String path = required(name, "name").trim();
    AttributeDef def = parse(definitionJson, AttributeDef.class);
    if (def.type != null) {
      checkType(def.type, path);
    }
    FormItemStructureEdit.edit(formXml, version, (xml, tree) -> {
      FormTree.Entry entry = entryByPath(tree, path);
      String tag = entry.owner == null ? "Attribute" : "Column";
      List<XmlGranularPatch.Replacement> out = new ArrayList<>();
      int bodyEnd = entry.columnsStart >= 0 ? entry.columnsStart : entry.end;
      if (def.title != null) {
        out.add(child(xml, entry.start, bodyEnd, "Title", titleXml("Title", def.title), null));
      }
      if (def.type != null) {
        if (!entry.columns.isEmpty() && !tableType(def.type)) {
          throw new IllegalArgumentException("У реквизита " + path + " есть колонки: тип остаётся таблицей или деревом.");
        }
        out.add(child(xml, entry.start, bodyEnd, "Type", typeXml(xml, def.type), "Title"));
      }
      if (out.isEmpty()) {
        throw new IllegalArgumentException("В описании нет ни заголовка, ни типа " + tag + ".");
      }
      return out;
    }, updated -> requireAttribute(updated, path, true));
  }

  // ---------- команды ----------

  /**
   * Добавляет команду формы.
   *
   * @param definitionJson {@code {"name": "Заполнить", "title": "…", "toolTip": "…", "action": "Заполнить"}};
   *                       без действия процедурой команды считается процедура с именем команды
   * @return номер новой команды
   */
  public static String addCommand(Path formXml, SchemaVersion version, String definitionJson)
    throws IOException, JAXBException {
    CommandDef def = parse(definitionJson, CommandDef.class);
    String name = required(def.name, "name").trim();
    CatalogNameConstraints.check(name);
    String action = def.action == null || def.action.isBlank() ? name : def.action.trim();
    checkHandler(action);
    String[] created = new String[1];
    FormItemStructureEdit.edit(formXml, version, (xml, tree) -> {
      if (entry(tree.commands, name) != null) {
        throw new IllegalArgumentException("У формы уже есть команда " + name + ".");
      }
      created[0] = String.valueOf(maxId(tree.commands) + 1);
      StringBuilder command = new StringBuilder("<Command name=\"").append(escape(name)).append("\" id=\"")
        .append(created[0]).append("\">");
      if (def.title != null) {
        command.append(titleXml("Title", def.title));
      }
      if (def.toolTip != null) {
        command.append(titleXml("ToolTip", def.toolTip));
      }
      command.append("<Action>").append(escape(action)).append("</Action></Command>");
      int after = tree.attributesEnd;
      return List.of(insertIntoList(xml, tree.commandsStart, tree.commandsEnd, after, "Commands",
        command.toString()));
    }, updated -> requireCommand(updated, name, true));
    return created[0];
  }

  /** Удаляет команду; команду, которую вызывает кнопка или командный интерфейс формы, не удаляет. */
  public static void deleteCommand(Path formXml, SchemaVersion version, String name)
    throws IOException, JAXBException {
    String command = required(name, "name").trim();
    FormItemStructureEdit.edit(formXml, version, (xml, tree) -> {
      FormTree.Entry entry = requireEntry(tree.commands, command, "команды");
      for (Reference reference : commandReferences(xml)) {
        if (reference.value.equals(command)) {
          throw new IllegalArgumentException("Команду " + command + " вызывает форма (" + reference.kind
            + "): сначала уберите кнопку.");
        }
      }
      return List.of(removeFromList(xml, entry, tree.commands, tree.commandsStart, tree.commandsEnd, false, null));
    }, updated -> requireCommand(updated, command, false));
  }

  /** Переименовывает команду; кнопки и командный интерфейс формы переходят на новое имя. */
  public static void renameCommand(Path formXml, SchemaVersion version, String oldName, String newName)
    throws IOException, JAXBException {
    String old = required(oldName, "oldName").trim();
    String name = required(newName, "newName").trim();
    CatalogNameConstraints.check(name);
    FormItemStructureEdit.edit(formXml, version, (xml, tree) -> {
      FormTree.Entry entry = requireEntry(tree.commands, old, "команды");
      if (old.equals(name)) {
        return List.of();
      }
      if (entry(tree.commands, name) != null) {
        throw new IllegalArgumentException("У формы уже есть команда " + name + ".");
      }
      List<XmlGranularPatch.Replacement> out = new ArrayList<>();
      out.add(nameAttribute(xml, entry.start, entry.name, name));
      for (Reference reference : commandReferences(xml)) {
        if (reference.value.equals(old)) {
          out.add(new XmlGranularPatch.Replacement(reference.start, reference.end, escape(name)));
        }
      }
      return out;
    }, updated -> {
      requireCommand(updated, name, true);
      requireCommand(updated, old, false);
    });
  }

  /** Меняет заголовок, подсказку и действие команды: то, что задано в описании. */
  public static void setCommand(Path formXml, SchemaVersion version, String name, String definitionJson)
    throws IOException, JAXBException {
    String command = required(name, "name").trim();
    CommandDef def = parse(definitionJson, CommandDef.class);
    if (def.action != null) {
      checkHandler(def.action.trim());
    }
    FormItemStructureEdit.edit(formXml, version, (xml, tree) -> {
      FormTree.Entry entry = requireEntry(tree.commands, command, "команды");
      List<XmlGranularPatch.Replacement> out = new ArrayList<>();
      if (def.title != null) {
        out.add(child(xml, entry.start, entry.end, "Title", titleXml("Title", def.title), null));
      }
      if (def.toolTip != null) {
        out.add(child(xml, entry.start, entry.end, "ToolTip", titleXml("ToolTip", def.toolTip), "Title"));
      }
      if (def.action != null) {
        out.add(action(xml, entry, "<Action>" + escape(def.action.trim()) + "</Action>"));
      }
      if (out.isEmpty()) {
        throw new IllegalArgumentException("В описании нет ни заголовка, ни подсказки, ни действия команды.");
      }
      return out;
    }, updated -> requireCommand(updated, command, true));
  }

  // ---------- параметры ----------

  /**
   * Добавляет параметр формы.
   *
   * @param definitionJson {@code {"name": "Ключ", "type": {"types": ["cfg:CatalogRef.Валюты"]}, "key": true}}
   */
  public static void addParameter(Path formXml, SchemaVersion version, String definitionJson)
    throws IOException, JAXBException {
    ParameterDef def = parse(definitionJson, ParameterDef.class);
    String name = required(def.name, "name").trim();
    CatalogNameConstraints.check(name);
    checkType(def.type, name);
    FormItemStructureEdit.edit(formXml, version, (xml, tree) -> {
      if (entry(tree.parameters, name) != null) {
        throw new IllegalArgumentException("У формы уже есть параметр " + name + ".");
      }
      String parameter = "<Parameter name=\"" + escape(name) + "\">" + typeXml(xml, def.type)
        + (Boolean.TRUE.equals(def.key) ? "<KeyParameter>true</KeyParameter>" : "") + "</Parameter>";
      int after = tree.commandsEnd >= 0 ? tree.commandsEnd : tree.attributesEnd;
      return List.of(insertIntoList(xml, tree.parametersStart, tree.parametersEnd, after,
        "Parameters", parameter));
    }, updated -> requireParameter(updated, name, true));
  }

  /** Удаляет параметр формы. */
  public static void deleteParameter(Path formXml, SchemaVersion version, String name)
    throws IOException, JAXBException {
    String parameter = required(name, "name").trim();
    FormItemStructureEdit.edit(formXml, version, (xml, tree) -> {
      FormTree.Entry entry = requireEntry(tree.parameters, parameter, "параметра");
      return List.of(removeFromList(xml, entry, tree.parameters, tree.parametersStart, tree.parametersEnd,
        false, null));
    }, updated -> requireParameter(updated, parameter, false));
  }

  /** Переименовывает параметр формы. */
  public static void renameParameter(Path formXml, SchemaVersion version, String oldName, String newName)
    throws IOException, JAXBException {
    String old = required(oldName, "oldName").trim();
    String name = required(newName, "newName").trim();
    CatalogNameConstraints.check(name);
    FormItemStructureEdit.edit(formXml, version, (xml, tree) -> {
      FormTree.Entry entry = requireEntry(tree.parameters, old, "параметра");
      if (old.equals(name)) {
        return List.of();
      }
      if (entry(tree.parameters, name) != null) {
        throw new IllegalArgumentException("У формы уже есть параметр " + name + ".");
      }
      return List.of(nameAttribute(xml, entry.start, entry.name, name));
    }, updated -> {
      requireParameter(updated, name, true);
      requireParameter(updated, old, false);
    });
  }

  /** Меняет тип параметра и признак ключевого: то, что задано в описании. */
  public static void setParameter(Path formXml, SchemaVersion version, String name, String definitionJson)
    throws IOException, JAXBException {
    String parameter = required(name, "name").trim();
    ParameterDef def = parse(definitionJson, ParameterDef.class);
    if (def.type != null) {
      checkType(def.type, parameter);
    }
    FormItemStructureEdit.edit(formXml, version, (xml, tree) -> {
      FormTree.Entry entry = requireEntry(tree.parameters, parameter, "параметра");
      List<XmlGranularPatch.Replacement> out = new ArrayList<>();
      if (def.type != null) {
        out.add(child(xml, entry.start, entry.end, "Type", typeXml(xml, def.type), null));
      }
      if (def.key != null) {
        Region key = region(xml, entry.start, entry.end, "KeyParameter");
        if (def.key) {
          out.add(key == null
            ? child(xml, entry.start, entry.end, "KeyParameter", "<KeyParameter>true</KeyParameter>", "Type")
            : new XmlGranularPatch.Replacement(key.start, key.end, "<KeyParameter>true</KeyParameter>"));
        } else if (key != null) {
          out.add(lineRemoval(xml, key.start, key.end));
        }
      }
      if (out.isEmpty() && def.type == null && def.key == null) {
        throw new IllegalArgumentException("В описании нет ни типа, ни признака ключевого параметра.");
      }
      return out;
    }, updated -> requireParameter(updated, parameter, true));
  }

  // ---------- события ----------

  /**
   * Назначает обработчик события формы или её элемента либо снимает его.
   *
   * @param itemId   номер элемента; пусто - сама форма
   * @param event    имя события платформы: {@code OnCreateAtServer}, {@code OnChange}
   * @param handler  имя процедуры модуля формы; пусто - снять обработчик
   * @param callType вид вызова у формы расширения: {@code Before}, {@code After}, {@code Override},
   *                 {@code ChangeAndValidate}; пусто - обычный обработчик
   */
  public static void setEvent(Path formXml, SchemaVersion version, String itemId, String event, String handler,
    String callType) throws IOException, JAXBException {
    String eventName = required(event, "event").trim();
    if (!EVENT_NAME.matcher(eventName).matches()) {
      throw new IllegalArgumentException("Имя события пишется латиницей, как у платформы: " + eventName);
    }
    String procedure = handler == null ? "" : handler.trim();
    if (!procedure.isEmpty()) {
      checkHandler(procedure);
    }
    String call = callType == null ? "" : callType.trim();
    if (!call.isEmpty() && !CALL_TYPES.contains(call)) {
      throw new IllegalArgumentException("Вид вызова обработчика - один из " + CALL_TYPES + ", а не " + call);
    }
    FormItemStructureEdit.edit(formXml, version, (xml, tree) -> {
      FormTree.Node owner = itemId == null || itemId.isBlank() ? tree.form : tree.item(itemId.trim());
      if (owner == null) {
        throw new IllegalArgumentException("В форме нет элемента с номером " + itemId + ".");
      }
      String eventXml = "<Event name=\"" + eventName + "\"" + (call.isEmpty() ? "" : " callType=\"" + call + "\"")
        + ">" + escape(procedure) + "</Event>";
      Region current = owner.eventsStart < 0 ? null : eventRegion(xml, owner, eventName);
      if (procedure.isEmpty()) {
        if (current == null) {
          return List.of();
        }
        boolean last = eventCount(xml, owner) == 1;
        return List.of(last ? lineRemoval(xml, owner.eventsStart, owner.eventsEnd)
          : lineRemoval(xml, current.start, current.end));
      }
      List<XmlGranularPatch.Replacement> out = new ArrayList<>();
      out.addAll(mainAttributeDefaults(xml, tree, version));
      if (current != null) {
        if (!xml.substring(current.start, current.end).equals(eventXml)) {
          out.add(new XmlGranularPatch.Replacement(current.start, current.end, eventXml));
        }
      } else if (owner.eventsStart >= 0) {
        int closing = xml.lastIndexOf("</", owner.eventsEnd - 1);
        String indent = XmlGranularPatch.currentLineIndent(xml, owner.eventsStart) + "\t";
        out.add(new XmlGranularPatch.Replacement(lineStart(xml, closing), lineStart(xml, closing),
          indent + eventXml + XmlGranularPatch.fileEol(xml)));
      } else {
        out.add(newEvents(xml, owner, eventXml));
      }
      return out;
    }, updated -> requireEvent(updated, itemId, eventName, procedure));
  }

  /**
   * Свойства основного реквизита, которые платформа 2.15-2.17 дописывает форме с обработчиком события
   * формы или элемента ({@link FormScaffold#mainAttributeDefaultsOnEvent}): каждое встаёт перед первым из
   * следующих за ним свойств корня, которое уже есть, иначе перед командной панелью формы.
   */
  private static List<XmlGranularPatch.Replacement> mainAttributeDefaults(String xml, FormTree tree,
    SchemaVersion version) {
    String mainType = null;
    for (FormTree.Entry attribute : tree.attributes) {
      String text = xml.substring(attribute.start, attribute.end);
      Matcher type = MAIN_ATTRIBUTE_TYPE.matcher(text);
      if (text.contains("<MainAttribute>true</MainAttribute>") && type.find()) {
        mainType = type.group(1).trim();
      }
    }
    List<String> defaults = FormScaffold.mainAttributeDefaultsOnEvent(version, mainType, xml);
    FormTree.Node commandBar = tree.form.attachedNodes.stream()
      .filter(n -> "AutoCommandBar".equals(n.type)).findFirst().orElse(null);
    if (defaults.isEmpty() || commandBar == null) {
      return List.of();
    }
    String indent = XmlGranularPatch.currentLineIndent(xml, commandBar.start);
    Map<Integer, StringBuilder> inserts = new TreeMap<>();
    for (String property : defaults) {
      String name = property.substring(1, property.indexOf('>'));
      int at = lineStart(xml, commandBar.start);
      for (String next : ROOT_TAIL.subList(ROOT_TAIL.indexOf(name) + 1, ROOT_TAIL.size())) {
        Matcher present = Pattern.compile("^[ \\t]*<" + next + "[\\s>/]", Pattern.MULTILINE)
          .matcher(xml).region(tree.form.start, commandBar.start);
        if (present.find()) {
          at = present.start();
          break;
        }
      }
      inserts.computeIfAbsent(at, k -> new StringBuilder())
        .append(indent).append(property).append(XmlGranularPatch.fileEol(xml));
    }
    List<XmlGranularPatch.Replacement> out = new ArrayList<>();
    inserts.forEach((at, text) -> out.add(new XmlGranularPatch.Replacement(at, at, text.toString())));
    return out;
  }

  // ---------- ссылки ----------

  /** Ссылка формы по имени или пути: что за узел, значение и его границы в тексте. */
  private record Reference(String kind, String value, int start, int end) {
  }

  private static List<Reference> pathReferences(String xml, FormTree tree) {
    List<Reference> out = new ArrayList<>();
    Matcher m = PATH_REFERENCE.matcher(xml);
    while (m.find()) {
      out.add(new Reference(m.group(1), m.group(3).trim(), m.start(3), m.end(3)));
    }
    Matcher table = TABLE_REFERENCE.matcher(xml);
    while (table.find()) {
      out.add(new Reference("AdditionalColumns", table.group(1), table.start(1), table.end(1)));
    }
    if (tree.conditionalAppearanceStart >= 0) {
      Matcher field = APPEARANCE_FIELD.matcher(xml).region(tree.conditionalAppearanceStart,
        tree.conditionalAppearanceEnd);
      while (field.find()) {
        out.add(new Reference("условное оформление", field.group(2).trim(), field.start(2), field.end(2)));
      }
    }
    return out;
  }

  private static List<Reference> commandReferences(String xml) {
    List<Reference> out = new ArrayList<>();
    Matcher m = COMMAND_REFERENCE.matcher(xml);
    while (m.find()) {
      out.add(new Reference(m.group(1), m.group(2).trim(), m.start(2), m.end(2)));
    }
    return out;
  }

  /** Путь ссылается на реквизит (колонку) или на то, что внутри него. */
  static boolean covers(String path, String value) {
    return value.equals(path) || value.startsWith(path + ".");
  }

  // ---------- записи ----------

  private static FormTree.Entry entry(List<FormTree.Entry> entries, String name) {
    return entries.stream().filter(e -> name.equals(e.name)).findFirst().orElse(null);
  }

  private static FormTree.Entry requireEntry(List<FormTree.Entry> entries, String name, String what) {
    FormTree.Entry entry = entry(entries, name);
    if (entry == null) {
      throw new IllegalArgumentException("У формы нет " + what + " " + name + ".");
    }
    return entry;
  }

  private static FormTree.Entry attribute(FormTree tree, String name) {
    return entry(tree.attributes, name);
  }

  private static FormTree.Entry requireAttribute(FormTree tree, String name) {
    return requireEntry(tree.attributes, name, "реквизита");
  }

  /** Реквизит или колонка по пути {@code Реквизит} / {@code Реквизит.Колонка}. */
  private static FormTree.Entry entryByPath(FormTree tree, String path) {
    int dot = path.indexOf('.');
    if (dot < 0) {
      return requireAttribute(tree, path);
    }
    FormTree.Entry owner = requireAttribute(tree, path.substring(0, dot));
    String column = path.substring(dot + 1);
    FormTree.Entry entry = entry(owner.columns, column);
    if (entry == null) {
      throw new IllegalArgumentException("У реквизита " + owner.name + " нет колонки " + column + ".");
    }
    return entry;
  }

  private static int maxId(List<FormTree.Entry> entries) {
    int max = 0;
    for (FormTree.Entry entry : entries) {
      try {
        max = Math.max(max, Integer.parseInt(entry.id));
      } catch (NumberFormatException e) {
        throw new IllegalArgumentException("Нечисловой номер " + entry.name + ": " + entry.id, e);
      }
    }
    return max;
  }

  /** Колонки бывают только у таблицы значений и дерева значений. */
  private static void checkColumns(String xml, FormTree.Entry owner) {
    Region type = region(xml, owner.start, owner.columnsStart >= 0 ? owner.columnsStart : owner.end, "Type");
    String text = type == null ? "" : xml.substring(type.start, type.end);
    if (!text.contains("v8:ValueTable<") && !text.contains("v8:ValueTree<")) {
      throw new IllegalArgumentException("Колонки бывают у реквизита типа таблица или дерево значений, а "
        + owner.name + " - не таблица.");
    }
  }

  private static boolean tableType(MdTypeDescriptionDto type) {
    return type.types != null && type.types.size() == 1
      && ("v8:ValueTable".equals(type.types.get(0)) || "v8:ValueTree".equals(type.types.get(0)));
  }

  private static void checkType(MdTypeDescriptionDto type, String owner) {
    if (type == null || (type.types == null || type.types.isEmpty()) && (type.typeSets == null
      || type.typeSets.isEmpty())) {
      throw new IllegalArgumentException("Не задан тип " + owner + ".");
    }
    if (type.types != null && type.types.contains("cfg:DynamicList")) {
      throw new IllegalArgumentException("Динамический список добавляется с настройками запроса и списка: "
        + "такая правка пока не поддержана.");
    }
  }

  private static void checkHandler(String handler) {
    if (!HANDLER_NAME.matcher(handler).matches()) {
      throw new IllegalArgumentException("Имя процедуры модуля - идентификатор: " + handler);
    }
  }

  // ---------- текст ----------

  /** Участок текста. */
  private record Region(int start, int end) {
  }

  private static String attributeXml(String xml, AttributeDef def, String id) {
    StringBuilder out = new StringBuilder("<Attribute name=\"").append(escape(def.name)).append("\" id=\"")
      .append(id).append("\">");
    if (def.title != null) {
      out.append(titleXml("Title", def.title));
    }
    out.append(typeXml(xml, def.type));
    if (Boolean.TRUE.equals(def.savedData)) {
      out.append("<SavedData>true</SavedData>");
    }
    List<AttributeDef> columns = def.columns == null ? List.of() : def.columns;
    if (!columns.isEmpty()) {
      if (!tableType(def.type)) {
        throw new IllegalArgumentException("Колонки бывают у реквизита типа таблица или дерево значений.");
      }
      out.append("<Columns>");
      Map<String, Boolean> names = new HashMap<>();
      int columnId = 0;
      for (AttributeDef column : columns) {
        String name = required(column.name, "columns.name").trim();
        CatalogNameConstraints.check(name);
        if (names.put(name, true) != null) {
          throw new IllegalArgumentException("Колонка " + name + " повторяется в описании.");
        }
        checkType(column.type, name);
        column.name = name;
        out.append(columnXml(xml, column, String.valueOf(++columnId)));
      }
      out.append("</Columns>");
    }
    if (valueList(def.type)) {
      // Тип элементов списка значений платформа пишет всегда, пустым - если он не задан
      out.append("<Settings xsi:type=\"v8:TypeDescription\"/>");
    }
    return out.append("</Attribute>").toString();
  }

  private static String columnXml(String xml, AttributeDef column, String id) {
    StringBuilder out = new StringBuilder("<Column name=\"").append(escape(column.name)).append("\" id=\"")
      .append(id).append("\">");
    if (column.title != null) {
      out.append(titleXml("Title", column.title));
    }
    return out.append(typeXml(xml, column.type)).append("</Column>").toString();
  }

  private static boolean valueList(MdTypeDescriptionDto type) {
    return type.types != null && type.types.size() == 1 && "v8:ValueListType".equals(type.types.get(0));
  }

  /** Тип без объявлений префиксов, которые уже объявил корень формы. */
  private static String typeXml(String xml, MdTypeDescriptionDto type) {
    String element = MdTypeDescriptionSerial.typeElement("Type", type);
    Map<String, String> root = new HashMap<>();
    int rootEnd = xml.indexOf('>', xml.indexOf("<Form"));
    Matcher m = ROOT_NAMESPACE.matcher(xml.substring(0, Math.max(rootEnd, 0)));
    while (m.find()) {
      root.put(m.group(1), m.group(2));
    }
    Matcher local = ROOT_NAMESPACE.matcher(element);
    StringBuilder out = new StringBuilder();
    while (local.find()) {
      boolean declared = local.group(2).equals(root.get(local.group(1)));
      local.appendReplacement(out, declared ? "" : Matcher.quoteReplacement(local.group()));
    }
    local.appendTail(out);
    return out.toString();
  }

  private static String titleXml(String tag, String text) {
    return "<" + tag + "><v8:item><v8:lang>" + ConfigurationLanguage.current() + "</v8:lang><v8:content>"
      + escape(text) + "</v8:content></v8:item></" + tag + ">";
  }

  /** Новый реквизит встаёт в конец списка, но перед условным оформлением формы. */
  private static XmlGranularPatch.Replacement insertAttribute(String xml, FormTree tree, String attributeXml) {
    if (tree.attributesStart < 0) {
      throw new IllegalArgumentException("В форме нет списка реквизитов.");
    }
    if (emptyTag(xml, tree.attributesEnd)) {
      return expand(xml, tree.attributesStart, tree.attributesEnd, "Attributes", attributeXml);
    }
    int anchor = tree.conditionalAppearanceStart >= 0
      ? tree.conditionalAppearanceStart
      : xml.lastIndexOf("</", tree.attributesEnd - 1);
    String indent = XmlGranularPatch.currentLineIndent(xml, tree.attributesStart) + "\t";
    return lineInsertion(xml, lineStart(xml, anchor), indent, attributeXml);
  }

  /** Колонка встаёт за последней колонкой; у реквизита без колонок заводится список. */
  private static XmlGranularPatch.Replacement insertColumn(String xml, FormTree.Entry owner, String columnXml) {
    String indent = XmlGranularPatch.currentLineIndent(xml, owner.start) + "\t";
    if (!owner.columns.isEmpty()) {
      FormTree.Entry last = owner.columns.get(owner.columns.size() - 1);
      return lineInsertion(xml, lineEnd(xml, last.end), indent + "\t", columnXml);
    }
    if (owner.columnsStart >= 0) {
      int at = lineEnd(xml, xml.indexOf('>', owner.columnsStart));
      return lineInsertion(xml, at, indent + "\t", columnXml);
    }
    Region settings = region(xml, owner.start, owner.end, "Settings");
    int anchor = settings != null ? settings.start : xml.lastIndexOf("</", owner.end - 1);
    return lineInsertion(xml, lineStart(xml, anchor), indent, "<Columns>" + columnXml + "</Columns>");
  }

  /**
   * Запись встаёт в конец списка; если списка нет, он заводится за узлом {@code after} (реквизиты,
   * команды), как его пишет платформа.
   */
  private static XmlGranularPatch.Replacement insertIntoList(String xml, int listStart, int listEnd,
    int after, String listTag, String entryXml) {
    if (listStart >= 0) {
      if (emptyTag(xml, listEnd)) {
        return expand(xml, listStart, listEnd, listTag, entryXml);
      }
      String indent = XmlGranularPatch.currentLineIndent(xml, listStart) + "\t";
      return lineInsertion(xml, lineStart(xml, xml.lastIndexOf("</", listEnd - 1)), indent, entryXml);
    }
    if (after < 0) {
      throw new IllegalArgumentException("В форме нет списка реквизитов, за которым встаёт " + listTag + ".");
    }
    String indent = XmlGranularPatch.currentLineIndent(xml, lineStart(xml, after - 1));
    return lineInsertion(xml, lineEnd(xml, after - 1), indent, "<" + listTag + ">" + entryXml + "</" + listTag + ">");
  }

  /**
   * Строки записи; последняя запись уносит с собой и список, кроме реквизитов (у формы без реквизитов
   * платформа пишет пустой {@code Attributes}) и списка, где остаётся что-то ещё.
   */
  private static XmlGranularPatch.Replacement removeFromList(String xml, FormTree.Entry entry,
    List<FormTree.Entry> entries, int listStart, int listEnd, boolean keepList, String emptyList) {
    if (entries.size() > 1 || keepList) {
      return lineRemoval(xml, entry.start, entry.end);
    }
    if (emptyList != null) {
      String indent = XmlGranularPatch.currentLineIndent(xml, listStart);
      return new XmlGranularPatch.Replacement(lineStart(xml, listStart), lineEnd(xml, listEnd - 1),
        indent + emptyList + XmlGranularPatch.fileEol(xml));
    }
    return lineRemoval(xml, listStart, listEnd);
  }

  /** Узел с именем {@code tag} среди дочерних: первый такой после начала записи. */
  private static Region region(String xml, int from, int to, String tag) {
    Matcher m = Pattern.compile("<" + tag + "(\\s[^>]*)?(/>|>.*?</" + tag + ">)", Pattern.DOTALL)
      .matcher(xml).region(from, to);
    int open = xml.indexOf('>', from) + 1;
    while (m.find()) {
      if (m.start() >= open) {
        return new Region(m.start(), m.end());
      }
    }
    return null;
  }

  /**
   * Заменяет дочерний узел записи или вставляет его: после узла {@code after}, а без него - первым.
   */
  private static XmlGranularPatch.Replacement child(String xml, int start, int end, String tag, String elementXml,
    String after) {
    Region current = region(xml, start, end, tag);
    if (current != null) {
      return new XmlGranularPatch.Replacement(current.start, current.end,
        XmlGranularPatch.formatReplacementPreservingIndent(xml, current.start, elementXml));
    }
    Region previous = after == null ? null : region(xml, start, end, after);
    String indent = XmlGranularPatch.currentLineIndent(xml, start) + "\t";
    int at = previous != null ? lineEnd(xml, previous.end - 1) : lineEnd(xml, xml.indexOf('>', start));
    return lineInsertion(xml, at, indent, elementXml);
  }

  /** Действие команды: на месте прежнего либо перед узлами, которые идут после него. */
  private static XmlGranularPatch.Replacement action(String xml, FormTree.Entry command, String actionXml) {
    Region current = region(xml, command.start, command.end, "Action");
    if (current != null) {
      return new XmlGranularPatch.Replacement(current.start, current.end, actionXml);
    }
    String indent = XmlGranularPatch.currentLineIndent(xml, command.start) + "\t";
    int at = lineStart(xml, xml.lastIndexOf("</", command.end - 1));
    for (String tag : AFTER_ACTION) {
      Region next = region(xml, command.start, command.end, tag);
      if (next != null) {
        at = Math.min(at, lineStart(xml, next.start));
      }
    }
    return lineInsertion(xml, at, indent, actionXml);
  }

  private static Region eventRegion(String xml, FormTree.Node owner, String event) {
    Matcher m = Pattern.compile("<Event name=\"" + Pattern.quote(event) + "\"[^>]*>[^<]*</Event>")
      .matcher(xml).region(owner.eventsStart, owner.eventsEnd);
    return m.find() ? new Region(m.start(), m.end()) : null;
  }

  private static int eventCount(String xml, FormTree.Node owner) {
    Matcher m = Pattern.compile("<Event\\s").matcher(xml).region(owner.eventsStart, owner.eventsEnd);
    int count = 0;
    while (m.find()) {
      count++;
    }
    return count;
  }

  /**
   * Первого обработчика ещё нет: {@code Events} встаёт за служебными узлами элемента (у формы - за её
   * командной панелью) и перед вложенными элементами, как его пишет платформа.
   */
  private static XmlGranularPatch.Replacement newEvents(String xml, FormTree.Node owner, String eventXml) {
    String block = "<Events>" + eventXml + "</Events>";
    String indent = XmlGranularPatch.currentLineIndent(xml, owner.start) + "\t";
    if (!owner.attachedNodes.isEmpty()) {
      FormTree.Node last = owner.attachedNodes.get(owner.attachedNodes.size() - 1);
      return lineInsertion(xml, lineEnd(xml, last.end - 1), indent, block);
    }
    if (owner.childItemsStart >= 0) {
      return lineInsertion(xml, lineStart(xml, owner.childItemsStart), indent, block);
    }
    if (xml.charAt(owner.end - 2) == '/') {
      throw new IllegalArgumentException("Элемент " + owner.name + " записан пустым тегом.");
    }
    return lineInsertion(xml, lineStart(xml, xml.lastIndexOf("</", owner.end - 1)), indent, block);
  }

  private static XmlGranularPatch.Replacement nameAttribute(String xml, int start, String oldName, String newName) {
    int tagEnd = xml.indexOf('>', start);
    Matcher name = Pattern.compile("\\sname=\"([^\"]*)\"").matcher(xml).region(start, tagEnd);
    if (!name.find() || !name.group(1).equals(escape(oldName))) {
      throw new IllegalStateException("У записи " + oldName + " нет имени в файле.");
    }
    return new XmlGranularPatch.Replacement(name.start(1), name.end(1), escape(newName));
  }

  private static boolean emptyTag(String xml, int end) {
    return xml.charAt(end - 2) == '/';
  }

  private static XmlGranularPatch.Replacement expand(String xml, int start, int end, String tag, String entryXml) {
    String indent = XmlGranularPatch.currentLineIndent(xml, start);
    return new XmlGranularPatch.Replacement(lineStart(xml, start), lineEnd(xml, end - 1),
      XmlGranularPatch.formatInsertion(xml, indent, "<" + tag + ">" + entryXml + "</" + tag + ">")
        + XmlGranularPatch.fileEol(xml));
  }

  private static XmlGranularPatch.Replacement lineInsertion(String xml, int at, String indent, String elementXml) {
    return new XmlGranularPatch.Replacement(at, at,
      XmlGranularPatch.formatInsertion(xml, indent, elementXml) + XmlGranularPatch.fileEol(xml));
  }

  private static XmlGranularPatch.Replacement lineRemoval(String xml, int start, int end) {
    return new XmlGranularPatch.Replacement(lineStart(xml, start), lineEnd(xml, end - 1), "");
  }

  private static int lineStart(String xml, int offset) {
    int i = offset;
    while (i > 0 && xml.charAt(i - 1) != '\n') {
      i--;
    }
    return i;
  }

  private static int lineEnd(String xml, int offset) {
    int newline = xml.indexOf('\n', offset);
    return newline < 0 ? xml.length() : newline + 1;
  }

  private static String escape(String value) {
    return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
  }

  // ---------- сверка ----------

  private static void requireAttribute(FormContentDto content, String path, boolean present) {
    int dot = path.indexOf('.');
    Function<List<FormAttributeDto>, FormAttributeDto> find = list -> list.stream()
      .filter(a -> (dot < 0 ? path : path.substring(0, dot)).equals(a.name)).findFirst().orElse(null);
    FormAttributeDto attribute = find.apply(content.attributes);
    boolean found = attribute != null && (dot < 0
      || attribute.columns.stream().anyMatch(c -> path.substring(dot + 1).equals(c.name)));
    if (found != present) {
      throw new IllegalStateException(present
        ? "После правки у формы нет реквизита " + path + "."
        : "После правки у формы остался реквизит " + path + ".");
    }
  }

  private static void requireCommand(FormContentDto content, String name, boolean present) {
    if (content.commands.stream().anyMatch(c -> name.equals(c.name)) != present) {
      throw new IllegalStateException((present ? "После правки у формы нет команды " : "После правки осталась команда ")
        + name + ".");
    }
  }

  private static void requireParameter(FormContentDto content, String name, boolean present) {
    if (content.parameters.stream().anyMatch(p -> name.equals(p.name)) != present) {
      throw new IllegalStateException((present ? "После правки у формы нет параметра " : "После правки остался параметр ")
        + name + ".");
    }
  }

  private static void requireEvent(FormContentDto content, String itemId, String event, String handler) {
    List<FormEventDto> events;
    if (itemId == null || itemId.isBlank()) {
      events = content.events;
    } else {
      FormItemDto item = findItem(content.items, itemId.trim());
      events = item == null ? List.of() : item.events;
    }
    String written = events.stream().filter(e -> event.equals(e.name)).map(e -> e.handler).findFirst().orElse("");
    if (!written.equals(handler)) {
      throw new IllegalStateException("После правки обработчик события " + event + " - \"" + written
        + "\", а ожидали \"" + handler + "\".");
    }
  }

  private static FormItemDto findItem(List<FormItemDto> items, String id) {
    for (FormItemDto item : items) {
      if (id.equals(item.id)) {
        return item;
      }
      FormItemDto nested = findItem(item.items, id);
      if (nested != null) {
        return nested;
      }
    }
    return null;
  }

  private static String required(String value, String field) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("обязательное поле не задано: " + field);
    }
    return value;
  }
}
