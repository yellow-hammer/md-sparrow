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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.stream.XMLStreamException;

import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EPackage;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;

import io.github.yellowhammer.designerxml.cf.CatalogNameConstraints;
import io.github.yellowhammer.designerxml.cf.ConfigurationLanguage;
import io.github.yellowhammer.designerxml.cf.FormAttributeDto;
import io.github.yellowhammer.designerxml.cf.FormContentDto;
import io.github.yellowhammer.designerxml.cf.FormEventDto;
import io.github.yellowhammer.designerxml.cf.FormItemDto;
import io.github.yellowhammer.designerxml.cf.MdTypeDescriptionDto;
import io.github.yellowhammer.edt.EdtObjectRegions.Region;

/**
 * Правка состава управляемой формы 1С:EDT: реквизиты с колонками, команды, параметры и обработчики
 * событий. Контракт тот же, что у формы конфигуратора ({@code FormCompositionEdit}): те же описания,
 * номера и отказы.
 *
 * <p>Правится текст {@code Form.form}, а при переименовании реквизита и
 * {@code ConditionalAppearance.dcssca} рядом с ним. Записи пишутся так, как их пишет 1С:EDT для
 * того же описания: шаблоны сверены с парами форм ssl31 и ssl31-edt. Обработчик события своего
 * вида элемента (выбор у поля ввода, запись у формы объекта) EDT держит в описании вида
 * {@code extInfo}, общий - у самого элемента; куда какое событие ложится, взято из ssl31-edt.
 */
public final class EdtFormCompositionEdit {

  private static final String FORM = "http://g5.1c.ru/v8/dt/form";
  private static final String INDENT = "  ";
  private static final String CONDITIONAL_APPEARANCE = "ConditionalAppearance.dcssca";
  private static final String EXT_INFO = "extInfo";
  private static final Pattern NAME = Pattern.compile("<name>([^<]*)</name>");
  private static final Pattern ID = Pattern.compile("<id>(-?\\d+)</id>");
  /** Ссылки на реквизит путём к данным: сегменты пути и узлы формы отчёта. */
  private static final Pattern PATH_REFERENCE = Pattern.compile(
      "<(segments|reportResult|detailsInformation|currentVariantPresentationField)>([^<]*)</\\1>");
  private static final Pattern APPEARANCE_FIELD = Pattern.compile(
      "<(left|right) xsi:type=\"dcscor:Field\">([^<]*)</\\1>");
  private static final Pattern COMMAND_REFERENCE = Pattern.compile(
      "<(commandName|command)>Form\\.Command\\.([^<]*)</\\1>");
  private static final Pattern EVENT_NAME = Pattern.compile("[A-Za-z][A-Za-z0-9]*");
  private static final Pattern HANDLER_NAME = Pattern.compile("[\\p{L}_][\\p{L}\\p{N}_]*");

  /** События формы, которые EDT пишет в её корне. */
  private static final Set<String> FORM_EVENTS = Set.of(
      "BeforeClose", "BeforeLoadDataFromSettingsAtServer", "ChoiceProcessing", "ExternalEvent",
      "FillCheckProcessingAtServer", "NavigationProcessing", "NotificationProcessing", "OnClose", "OnCreateAtServer",
      "OnLoadDataFromSettingsAtServer", "OnMainServerAvailabilityChange", "OnOpen", "OnReopen",
      "OnSaveDataInSettingsAtServer", "URLGetProcessing", "URLProcessing");
  /** События формы объекта и отчёта: EDT пишет их в описание вида формы. */
  private static final Set<String> FORM_EXT_EVENTS = Set.of(
      "AfterWrite", "AfterWriteAtServer", "BeforeLoadUserSettingsAtServer", "BeforeLoadVariantAtServer",
      "BeforeWrite", "BeforeWriteAtServer", "OnLoadUserSettingsAtServer", "OnLoadVariantAtServer", "OnReadAtServer",
      "OnSaveUserSettingsAtServer", "OnSaveVariantAtServer", "OnUpdateUserSettingSetAtServer", "OnWriteAtServer");
  /** События элемента, которые EDT пишет у самого элемента. */
  private static final Map<String, Set<String>> ITEM_EVENTS = Map.of(
      "Table", Set.of("AfterDeleteRow", "BeforeAddRow", "BeforeCollapse", "BeforeDeleteRow", "BeforeEditEnd",
          "BeforeExpand", "BeforeRowChange", "ChoiceProcessing", "Drag", "DragCheck", "DragEnd", "DragStart",
          "NewWriteProcessing", "OnActivateCell", "OnActivateField", "OnActivateRow", "OnChange", "OnEditEnd",
          "OnStartEdit", "Selection", "ValueChoice"));
  /** События своего вида элемента: EDT пишет их в описание вида. */
  private static final Map<String, Set<String>> EXT_EVENTS = Map.ofEntries(
      Map.entry("InputField", Set.of("AutoComplete", "ChoiceProcessing", "Clearing", "Creating", "EditTextChange",
          "Opening", "StartChoice", "StartListChoice", "TextEditEnd", "Tuning")),
      Map.entry("LabelField", Set.of("Click", "URLProcessing")),
      Map.entry("PictureField", Set.of("Click")),
      Map.entry("CalendarField", Set.of("OnPeriodOutput", "Selection")),
      Map.entry("GraphicalSchemaField", Set.of("Selection")),
      Map.entry("HTMLDocumentField", Set.of("DocumentComplete", "OnClick")),
      Map.entry("SpreadSheetDocumentField", Set.of("AdditionalDetailProcessing", "BeforeWrite", "DetailProcessing",
          "Drag", "DragCheck", "OnActivate", "OnChangeAreaContent", "Selection")),
      Map.entry("LabelDecoration", Set.of("Click", "URLProcessing")),
      Map.entry("PictureDecoration", Set.of("Click")),
      Map.entry("ExtendedTooltip", Set.of("URLProcessing")),
      Map.entry("Pages", Set.of("OnCurrentPageChange")),
      Map.entry("Table", Set.of("OnGetDataAtServer")));
  /** Поля: изменение значения EDT у всех пишет у самого элемента. */
  private static final Set<String> FIELDS = Set.of(
      "InputField", "LabelField", "CheckBoxField", "RadioButtonField", "PictureField", "CalendarField",
      "SpreadSheetDocumentField", "TextDocumentField", "FormattedDocumentField", "HTMLDocumentField",
      "TrackBarField", "ProgressBarField", "ChartField", "GraphicalSchemaField", "DendrogramField",
      "GanttChartField", "PeriodField", "PlannerField", "GeographicalSchemaField", "PDFDocumentField");

  private EdtFormCompositionEdit() {
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

  private static <T> T parse(String json, Class<T> type) {
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

  /** Добавляет реквизит или колонку ({@code Реквизит.Колонка}); ответ - номер новой записи. */
  public static String addAttribute(Path formFile, EdtModel model, String definitionJson) throws IOException {
    AttributeDef def = parse(definitionJson, AttributeDef.class);
    String path = required(def.name, "name").trim();
    checkType(def.type, path);
    String[] created = new String[1];
    edit(formFile, model, form -> {
      int dot = path.indexOf('.');
      if (dot < 0) {
        CatalogNameConstraints.check(path);
        if (entry(form.attributes, path) != null) {
          throw new IllegalArgumentException("У формы уже есть реквизит " + path + ".");
        }
        created[0] = String.valueOf(maxId(form.attributes) + 1);
        def.name = path;
        String block = attributeXml(form, def, created[0], INDENT);
        return List.of(insertRoot(form, form.attributes, "attributes", block));
      }
      Entry owner = requireEntry(form.attributes, path.substring(0, dot), "реквизита");
      def.name = path.substring(dot + 1);
      CatalogNameConstraints.check(def.name);
      if (!tableType(form.xml.substring(owner.start, owner.end))) {
        throw new IllegalArgumentException("Колонки бывают у реквизита типа таблица или дерево значений, а "
            + owner.name + " - не таблица.");
      }
      if (entry(owner.columns, def.name) != null) {
        throw new IllegalArgumentException("У реквизита " + owner.name + " уже есть колонка " + def.name + ".");
      }
      created[0] = String.valueOf(maxId(owner.columns) + 1);
      String indent = INDENT + INDENT;
      String block = columnXml(form, def, created[0], indent);
      int at;
      if (!owner.columns.isEmpty()) {
        at = lineEnd(form.xml, owner.columns.get(owner.columns.size() - 1).end);
      } else {
        at = EdtFormItemPropertyEdit.insertionPoint(form.xml, new Region(owner.start, owner.end),
            classOf(form, "FormAttribute"), "columns");
      }
      return List.of(new Edit(at, at, indent + block + form.eol));
    }, updated -> requireAttribute(updated, path, true));
    return created[0];
  }

  /** Удаляет реквизит или колонку; реквизит, на путь которого ссылается форма, не удаляется. */
  public static void deleteAttribute(Path formFile, EdtModel model, String name) throws IOException {
    String path = required(name, "name").trim();
    edit(formFile, model, form -> {
      Entry entry = entryByPath(form, path);
      for (Reference reference : form.pathReferences()) {
        boolean inside = reference.inForm && reference.start >= entry.start && reference.start < entry.end;
        if (!inside && covers(path, reference.value)) {
          throw new IllegalArgumentException("На реквизит " + path + " ссылается форма (" + reference.kind + ": "
              + reference.value + "): сначала уберите ссылку.");
        }
      }
      return List.of(removal(form.xml, entry.start, entry.end));
    }, updated -> requireAttribute(updated, path, false));
  }

  /** Переименовывает реквизит или колонку; пути к данным формы и её условного оформления переходят. */
  public static void renameAttribute(Path formFile, EdtModel model, String oldName, String newName)
      throws IOException {
    String path = required(oldName, "oldName").trim();
    String name = required(newName, "newName").trim();
    CatalogNameConstraints.check(name);
    String[] renamed = new String[1];
    edit(formFile, model, form -> {
      Entry entry = entryByPath(form, path);
      if (entry.name.equals(name)) {
        return List.of();
      }
      List<Entry> siblings = entry.owner == null ? form.attributes : entry.owner.columns;
      if (entry(siblings, name) != null) {
        throw new IllegalArgumentException("Имя " + name + " уже занято.");
      }
      String newPath = entry.owner == null ? name : entry.owner.name + "." + name;
      renamed[0] = newPath;
      List<Edit> out = new ArrayList<>();
      out.add(nameNode(form.xml, entry, name));
      for (Reference reference : form.pathReferences()) {
        if (covers(path, reference.value)) {
          out.add(new Edit(reference.start, reference.end, escape(newPath + reference.value.substring(path.length())),
              reference.inForm));
        }
      }
      return out;
    }, updated -> {
      requireAttribute(updated, renamed[0], true);
      requireAttribute(updated, path, false);
    });
  }

  /** Меняет заголовок и тип реквизита или колонки. */
  public static void setAttribute(Path formFile, EdtModel model, String name, String definitionJson)
      throws IOException {
    String path = required(name, "name").trim();
    AttributeDef def = parse(definitionJson, AttributeDef.class);
    if (def.type != null) {
      checkType(def.type, path);
    }
    edit(formFile, model, form -> {
      Entry entry = entryByPath(form, path);
      String indent = indent(form.xml, entry.start) + INDENT;
      List<Edit> out = new ArrayList<>();
      EClass eClass = classOf(form, entry.owner == null ? "FormAttribute" : "FormAttributeColumn");
      if (def.title != null) {
        out.add(feature(form, entry, eClass, "title", titleXml(form, "title", def.title, indent), indent));
      }
      if (def.type != null) {
        if (!entry.columns.isEmpty() && !tableType(def.type)) {
          throw new IllegalArgumentException("У реквизита " + path + " есть колонки: тип остаётся таблицей или деревом.");
        }
        out.add(feature(form, entry, eClass, "valueType", typeXml(form, def.type, indent), indent));
      }
      if (out.isEmpty()) {
        throw new IllegalArgumentException("В описании нет ни заголовка, ни типа.");
      }
      return out;
    }, updated -> requireAttribute(updated, path, true));
  }

  // ---------- команды ----------

  /** Добавляет команду; ответ - номер новой команды. */
  public static String addCommand(Path formFile, EdtModel model, String definitionJson) throws IOException {
    CommandDef def = parse(definitionJson, CommandDef.class);
    String name = required(def.name, "name").trim();
    CatalogNameConstraints.check(name);
    String action = def.action == null || def.action.isBlank() ? name : def.action.trim();
    checkHandler(action);
    String[] created = new String[1];
    edit(formFile, model, form -> {
      if (entry(form.commands, name) != null) {
        throw new IllegalArgumentException("У формы уже есть команда " + name + ".");
      }
      created[0] = String.valueOf(maxId(form.commands) + 1);
      String in = INDENT + INDENT;
      String eol = form.eol;
      StringBuilder block = new StringBuilder("<formCommands>").append(eol);
      block.append(in).append("<name>").append(escape(name)).append("</name>").append(eol);
      if (def.title != null) {
        block.append(in).append(titleXml(form, "title", def.title, in)).append(eol);
      }
      block.append(in).append("<id>").append(created[0]).append("</id>").append(eol);
      if (def.toolTip != null) {
        block.append(in).append(titleXml(form, "toolTip", def.toolTip, in)).append(eol);
      }
      block.append(in).append("<use>").append(eol)
          .append(in).append(INDENT).append("<common>true</common>").append(eol)
          .append(in).append("</use>").append(eol);
      block.append(in).append(actionXml(form, action, in)).append(eol);
      block.append(in).append("<currentRowUse>Auto</currentRowUse>").append(eol);
      block.append(INDENT).append("</formCommands>");
      return List.of(insertRoot(form, form.commands, "formCommands", block.toString()));
    }, updated -> requireCommand(updated, name, true));
    return created[0];
  }

  /** Удаляет команду; команду, которую вызывает кнопка или командный интерфейс, не удаляет. */
  public static void deleteCommand(Path formFile, EdtModel model, String name) throws IOException {
    String command = required(name, "name").trim();
    edit(formFile, model, form -> {
      Entry entry = requireEntry(form.commands, command, "команды");
      for (Reference reference : form.commandReferences()) {
        if (reference.value.equals(command)) {
          throw new IllegalArgumentException("Команду " + command + " вызывает форма (" + reference.kind
              + "): сначала уберите кнопку.");
        }
      }
      return List.of(removal(form.xml, entry.start, entry.end));
    }, updated -> requireCommand(updated, command, false));
  }

  /** Переименовывает команду; кнопки и командный интерфейс переходят на новое имя. */
  public static void renameCommand(Path formFile, EdtModel model, String oldName, String newName) throws IOException {
    String old = required(oldName, "oldName").trim();
    String name = required(newName, "newName").trim();
    CatalogNameConstraints.check(name);
    edit(formFile, model, form -> {
      Entry entry = requireEntry(form.commands, old, "команды");
      if (old.equals(name)) {
        return List.of();
      }
      if (entry(form.commands, name) != null) {
        throw new IllegalArgumentException("У формы уже есть команда " + name + ".");
      }
      List<Edit> out = new ArrayList<>();
      out.add(nameNode(form.xml, entry, name));
      for (Reference reference : form.commandReferences()) {
        if (reference.value.equals(old)) {
          out.add(new Edit(reference.start, reference.end, escape(name)));
        }
      }
      return out;
    }, updated -> {
      requireCommand(updated, name, true);
      requireCommand(updated, old, false);
    });
  }

  /** Меняет заголовок, подсказку и действие команды. */
  public static void setCommand(Path formFile, EdtModel model, String name, String definitionJson) throws IOException {
    String command = required(name, "name").trim();
    CommandDef def = parse(definitionJson, CommandDef.class);
    if (def.action != null) {
      checkHandler(def.action.trim());
    }
    edit(formFile, model, form -> {
      Entry entry = requireEntry(form.commands, command, "команды");
      String indent = indent(form.xml, entry.start) + INDENT;
      EClass eClass = classOf(form, "FormCommand");
      List<Edit> out = new ArrayList<>();
      if (def.title != null) {
        out.add(feature(form, entry, eClass, "title", titleXml(form, "title", def.title, indent), indent));
      }
      if (def.toolTip != null) {
        out.add(feature(form, entry, eClass, "toolTip", titleXml(form, "toolTip", def.toolTip, indent), indent));
      }
      if (def.action != null) {
        out.add(feature(form, entry, eClass, "action", actionXml(form, def.action.trim(), indent), indent));
      }
      if (out.isEmpty()) {
        throw new IllegalArgumentException("В описании нет ни заголовка, ни подсказки, ни действия команды.");
      }
      return out;
    }, updated -> requireCommand(updated, command, true));
  }

  // ---------- параметры ----------

  /** Добавляет параметр формы. */
  public static void addParameter(Path formFile, EdtModel model, String definitionJson) throws IOException {
    ParameterDef def = parse(definitionJson, ParameterDef.class);
    String name = required(def.name, "name").trim();
    CatalogNameConstraints.check(name);
    checkType(def.type, name);
    edit(formFile, model, form -> {
      if (entry(form.parameters, name) != null) {
        throw new IllegalArgumentException("У формы уже есть параметр " + name + ".");
      }
      String in = INDENT + INDENT;
      String block = "<parameters>" + form.eol
          + in + "<name>" + escape(name) + "</name>" + form.eol
          + in + typeXml(form, def.type, in) + form.eol
          + (Boolean.TRUE.equals(def.key) ? in + "<keyParameter>true</keyParameter>" + form.eol : "")
          + INDENT + "</parameters>";
      return List.of(insertRoot(form, form.parameters, "parameters", block));
    }, updated -> requireParameter(updated, name, true));
  }

  /** Удаляет параметр формы. */
  public static void deleteParameter(Path formFile, EdtModel model, String name) throws IOException {
    String parameter = required(name, "name").trim();
    edit(formFile, model, form -> {
      Entry entry = requireEntry(form.parameters, parameter, "параметра");
      return List.of(removal(form.xml, entry.start, entry.end));
    }, updated -> requireParameter(updated, parameter, false));
  }

  /** Переименовывает параметр формы. */
  public static void renameParameter(Path formFile, EdtModel model, String oldName, String newName)
      throws IOException {
    String old = required(oldName, "oldName").trim();
    String name = required(newName, "newName").trim();
    CatalogNameConstraints.check(name);
    edit(formFile, model, form -> {
      Entry entry = requireEntry(form.parameters, old, "параметра");
      if (old.equals(name)) {
        return List.of();
      }
      if (entry(form.parameters, name) != null) {
        throw new IllegalArgumentException("У формы уже есть параметр " + name + ".");
      }
      return List.of(nameNode(form.xml, entry, name));
    }, updated -> {
      requireParameter(updated, name, true);
      requireParameter(updated, old, false);
    });
  }

  /** Меняет тип параметра и признак ключевого. */
  public static void setParameter(Path formFile, EdtModel model, String name, String definitionJson)
      throws IOException {
    String parameter = required(name, "name").trim();
    ParameterDef def = parse(definitionJson, ParameterDef.class);
    if (def.type != null) {
      checkType(def.type, parameter);
    }
    edit(formFile, model, form -> {
      Entry entry = requireEntry(form.parameters, parameter, "параметра");
      String indent = indent(form.xml, entry.start) + INDENT;
      EClass eClass = classOf(form, "FormParameter");
      List<Edit> out = new ArrayList<>();
      if (def.type != null) {
        out.add(feature(form, entry, eClass, "valueType", typeXml(form, def.type, indent), indent));
      }
      if (def.key != null) {
        Region key = child(form.xml, entry, "keyParameter");
        if (def.key) {
          if (key == null) {
            out.add(feature(form, entry, eClass, "keyParameter", "<keyParameter>true</keyParameter>", indent));
          }
        } else if (key != null) {
          out.add(removal(form.xml, key.start(), key.end()));
        }
      }
      return out;
    }, updated -> requireParameter(updated, parameter, true));
  }

  // ---------- события ----------

  /**
   * Назначает обработчик события формы или элемента либо снимает его.
   *
   * @param callType вид вызова у формы расширения; в EDT пока не поддержан
   */
  public static void setEvent(Path formFile, EdtModel model, String itemId, String event, String handler,
      String callType) throws IOException {
    String eventName = required(event, "event").trim();
    if (!EVENT_NAME.matcher(eventName).matches()) {
      throw new IllegalArgumentException("Имя события пишется латиницей, как у платформы: " + eventName);
    }
    if (callType != null && !callType.isBlank()) {
      throw new IllegalArgumentException("Вид вызова обработчика в форме 1С:EDT пока не поддержан: в ssl31-edt "
          + "нет образца его записи.");
    }
    String procedure = handler == null ? "" : handler.trim();
    if (!procedure.isEmpty() && !HANDLER_NAME.matcher(procedure).matches()) {
      throw new IllegalArgumentException("Имя процедуры модуля - идентификатор: " + procedure);
    }
    edit(formFile, model, form -> {
      boolean isForm = itemId == null || itemId.isBlank();
      EdtFormTree.Node node = isForm ? form.tree.form : form.tree.item(itemId.trim());
      if (node == null) {
        throw new IllegalArgumentException("В форме нет элемента с номером " + itemId + ".");
      }
      String kind = isForm ? "Form" : form.type(node);
      boolean inExtInfo = extInfoEvent(kind, eventName);
      Region owner = new Region(node.start, node.end);
      Region container = owner;
      Region extInfo = null;
      for (EdtObjectRegions.Child child : EdtObjectRegions.children(form.xml, owner)) {
        if (EXT_INFO.equals(child.name())) {
          extInfo = child.region();
        }
      }
      if (inExtInfo) {
        if (extInfo == null && isForm) {
          throw new IllegalArgumentException("У формы этого вида нет события " + eventName + ".");
        }
        container = extInfo;
      }
      Region current = null;
      int handlers = 0;
      Region lastHandler = null;
      if (container != null && !EdtObjectRegions.emptyTag(form.xml, container)) {
        for (EdtObjectRegions.Child child : EdtObjectRegions.children(form.xml, container)) {
          if ("handlers".equals(child.name())) {
            handlers++;
            lastHandler = child.region();
            String text = form.xml.substring(child.region().start(), child.region().end());
            if (text.contains("<event>" + eventName + "</event>")) {
              current = child.region();
            }
          }
        }
      }
      if (procedure.isEmpty()) {
        return current == null ? List.of() : List.of(removal(form.xml, current.start(), current.end()));
      }
      if (current != null) {
        Matcher name = NAME.matcher(form.xml).region(current.start(), current.end());
        if (!name.find()) {
          throw new IllegalStateException("У обработчика " + eventName + " нет процедуры в файле.");
        }
        return name.group(1).equals(escape(procedure))
            ? List.of()
            : List.of(new Edit(name.start(1), name.end(1), escape(procedure)));
      }
      String indent = (container == null ? indent(form.xml, node.start) + INDENT
          : indent(form.xml, container.start())) + INDENT;
      String block = "<handlers>" + form.eol
          + indent + INDENT + "<event>" + eventName + "</event>" + form.eol
          + indent + INDENT + "<name>" + escape(procedure) + "</name>" + form.eol
          + indent + "</handlers>";
      if (lastHandler != null) {
        int at = lineEnd(form.xml, lastHandler.end());
        return List.of(new Edit(at, at, indent + block + form.eol));
      }
      if (container == null) {
        // Описания вида ещё нет: оно встаёт в элемент вместе с обработчиком
        EClass extClass = EdtFormItemProperties.extInfo(form.formPackage, node.type.isEmpty() ? kind : node.type);
        if (extClass == null) {
          throw new IllegalArgumentException("У вида " + kind + " нет описания вида для события " + eventName + ".");
        }
        String outer = indent(form.xml, node.start) + INDENT;
        String info = "<extInfo xsi:type=\"form:" + extClass.getName() + "\">" + form.eol
            + outer + INDENT + block + form.eol + outer + "</extInfo>";
        int at = EdtFormItemPropertyEdit.insertionPoint(form.xml, owner, itemClass(form, node), EXT_INFO);
        return List.of(new Edit(at, at, outer + info + form.eol));
      }
      if (EdtObjectRegions.emptyTag(form.xml, container)) {
        String open = form.xml.substring(container.start(), container.end() - 2) + ">";
        String outer = indent(form.xml, container.start());
        return List.of(new Edit(container.start(), container.end(),
            open + form.eol + indent + block + form.eol + outer + "</" + EXT_INFO + ">"));
      }
      EClass containerClass = container == owner
          ? (isForm ? form.formClass : itemClass(form, node))
          : classOf(form, xsiType(form.xml, container));
      int at = EdtFormItemPropertyEdit.insertionPoint(form.xml, container, containerClass, "handlers");
      return List.of(new Edit(at, at, indent + block + form.eol));
    }, updated -> requireEvent(updated, itemId, eventName, procedure));
  }

  /** Где EDT держит обработчик: в описании вида или у самого элемента; неизвестное - отказ. */
  private static boolean extInfoEvent(String kind, String event) {
    if ("Form".equals(kind)) {
      if (FORM_EVENTS.contains(event)) {
        return false;
      }
      if (FORM_EXT_EVENTS.contains(event)) {
        return true;
      }
    } else {
      if (EXT_EVENTS.getOrDefault(kind, Set.of()).contains(event)) {
        return true;
      }
      if (ITEM_EVENTS.getOrDefault(kind, Set.of()).contains(event)
          || "OnChange".equals(event) && FIELDS.contains(kind)) {
        return false;
      }
    }
    throw new IllegalArgumentException("Куда 1С:EDT пишет обработчик события " + event + " у вида " + kind
        + ", не известно: в ssl31-edt такого обработчика нет.");
  }

  // ---------- общее ----------

  /** Правка участка файла формы или, с {@code inForm = false}, её условного оформления. */
  private record Edit(int start, int end, String text, boolean inForm) {
    Edit(int start, int end, String text) {
      this(start, end, text, true);
    }
  }

  private record Reference(String kind, String value, int start, int end, boolean inForm) {
  }

  /** Реквизит, колонка, команда или параметр: имя, номер, границы. */
  private static final class Entry {
    final String name;
    final String id;
    final int start;
    final int end;
    final Entry owner;
    final List<Entry> columns = new ArrayList<>();

    Entry(String name, String id, int start, int end, Entry owner) {
      this.name = name;
      this.id = id;
      this.start = start;
      this.end = end;
      this.owner = owner;
    }
  }

  /** Разобранная форма: текст, дерево элементов, состав и условное оформление. */
  private static final class Form {
    final String xml;
    final String appearance;
    final EdtFormTree tree;
    final EPackage formPackage;
    final EClass formClass;
    final EdtModel model;
    final String eol;
    final List<Entry> attributes = new ArrayList<>();
    final List<Entry> commands = new ArrayList<>();
    final List<Entry> parameters = new ArrayList<>();
    final Map<String, String> types = new java.util.HashMap<>();

    Form(String xml, String appearance, EdtModel model) throws IOException, XMLStreamException {
      this.xml = xml;
      this.appearance = appearance;
      this.model = model;
      this.tree = EdtFormTree.parse(xml);
      this.formPackage = model.packageOf(FORM);
      if (formPackage == null) {
        throw new IllegalStateException("В сборке нет метамодели формы EDT.");
      }
      this.formClass = formPackage.getEClassifier("Form") instanceof EClass found ? found : null;
      this.eol = xml.contains("\r\n") ? "\r\n" : "\n";
      for (EdtObjectRegions.Child child : EdtObjectRegions.children(xml, new Region(tree.form.start, tree.form.end))) {
        switch (child.name()) {
          case "attributes" -> {
            Entry attribute = entry(xml, child.region(), null);
            attributes.add(attribute);
            for (EdtObjectRegions.Child column : EdtObjectRegions.children(xml, child.region())) {
              if ("columns".equals(column.name())) {
                attribute.columns.add(entry(xml, column.region(), attribute));
              }
            }
          }
          case "formCommands" -> commands.add(entry(xml, child.region(), null));
          case "parameters" -> parameters.add(entry(xml, child.region(), null));
          default -> {
            // Остальные узлы корня состав формы не задают
          }
        }
      }
      collectTypes(EdtFormContent.read(EdtObjectReader.parse(xml), model).items);
    }

    private static Entry entry(String xml, Region region, Entry owner) {
      Matcher name = NAME.matcher(xml).region(region.start(), region.end());
      Matcher id = ID.matcher(xml).region(region.start(), region.end());
      return new Entry(name.find() ? unescape(name.group(1)) : "", id.find() ? id.group(1) : null,
          region.start(), region.end(), owner);
    }

    private void collectTypes(List<FormItemDto> items) {
      for (FormItemDto item : items) {
        if (item.id != null) {
          types.put(item.id, item.type);
        }
        collectTypes(item.items);
      }
    }

    /** Вид элемента в записи конфигуратора. */
    String type(EdtFormTree.Node node) {
      return types.getOrDefault(node.id, node.type);
    }

    List<Reference> pathReferences() {
      List<Reference> out = new ArrayList<>();
      Matcher m = PATH_REFERENCE.matcher(xml);
      while (m.find()) {
        out.add(new Reference(m.group(1), m.group(2).trim(), m.start(2), m.end(2), true));
      }
      if (appearance != null) {
        Matcher field = APPEARANCE_FIELD.matcher(appearance);
        while (field.find()) {
          out.add(new Reference("условное оформление", field.group(2).trim(), field.start(2), field.end(2), false));
        }
      }
      return out;
    }

    List<Reference> commandReferences() {
      List<Reference> out = new ArrayList<>();
      Matcher m = COMMAND_REFERENCE.matcher(xml);
      while (m.find()) {
        out.add(new Reference(m.group(1), m.group(2).trim(), m.start(2), m.end(2), true));
      }
      return out;
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
    Path owner = EdtFormItemStructureEdit.ownerMdo(formFile);
    if (owner != null) {
      EdtSupportRules.ensureEditable(owner);
    }
    EdtOrdinaryForms.refuse(formFile);
    try {
      ConfigurationLanguage.with(formFile, () -> {
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
          return null;
        }
        String updated = withXsiPrefix(patched(xml, edits.stream().filter(Edit::inForm).toList()));
        String updatedAppearance = appearance == null
            ? null
            : patched(appearance, edits.stream().filter(e -> !e.inForm()).toList());
        EdtFormTree.parse(updated);
        check.verify(EdtFormContent.read(EdtObjectReader.parse(updated), model));
        Files.writeString(formFile, updated, StandardCharsets.UTF_8);
        if (updatedAppearance != null && !updatedAppearance.equals(appearance)) {
          Files.writeString(appearanceFile, updatedAppearance, StandardCharsets.UTF_8);
        }
        return null;
      });
    } catch (jakarta.xml.bind.JAXBException error) {
      throw new IOException(error);
    }
  }

  /** Корню формы нужен префикс {@code xsi}, если правка его использует: новая форма EDT пишется без него. */
  private static String withXsiPrefix(String xml) {
    int root = xml.indexOf("<form:Form");
    int close = root < 0 ? -1 : xml.indexOf('>', root);
    if (root < 0 || close < 0 || xml.substring(root, close).contains("xmlns:xsi=") || !xml.contains("xsi:type=")) {
      return xml;
    }
    int at = root + "<form:Form".length();
    return xml.substring(0, at) + " xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"" + xml.substring(at);
  }

  // ---------- записи ----------

  private static Entry entry(List<Entry> entries, String name) {
    return entries.stream().filter(e -> name.equals(e.name)).findFirst().orElse(null);
  }

  private static Entry requireEntry(List<Entry> entries, String name, String what) {
    Entry entry = entry(entries, name);
    if (entry == null) {
      throw new IllegalArgumentException("У формы нет " + what + " " + name + ".");
    }
    return entry;
  }

  private static Entry entryByPath(Form form, String path) {
    int dot = path.indexOf('.');
    if (dot < 0) {
      return requireEntry(form.attributes, path, "реквизита");
    }
    Entry owner = requireEntry(form.attributes, path.substring(0, dot), "реквизита");
    Entry column = entry(owner.columns, path.substring(dot + 1));
    if (column == null) {
      throw new IllegalArgumentException("У реквизита " + owner.name + " нет колонки " + path.substring(dot + 1) + ".");
    }
    return column;
  }

  private static int maxId(List<Entry> entries) {
    int max = 0;
    for (Entry entry : entries) {
      try {
        max = Math.max(max, Integer.parseInt(entry.id));
      } catch (NumberFormatException e) {
        throw new IllegalArgumentException("Нечисловой номер " + entry.name + ": " + entry.id, e);
      }
    }
    return max;
  }

  private static boolean covers(String path, String value) {
    return value.equals(path) || value.startsWith(path + ".");
  }

  private static void checkType(MdTypeDescriptionDto type, String owner) {
    if (type == null || (type.types == null || type.types.isEmpty())
        && (type.typeSets == null || type.typeSets.isEmpty())) {
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

  private static boolean tableType(MdTypeDescriptionDto type) {
    return type.types != null && type.types.size() == 1
        && ("v8:ValueTable".equals(type.types.get(0)) || "v8:ValueTree".equals(type.types.get(0)));
  }

  private static boolean tableType(String attributeXml) {
    return attributeXml.contains("<types>ValueTable</types>") || attributeXml.contains("<types>ValueTree</types>");
  }

  // ---------- текст ----------

  private static String attributeXml(Form form, AttributeDef def, String id, String indent) {
    String in = indent + INDENT;
    String eol = form.eol;
    StringBuilder out = new StringBuilder("<attributes>").append(eol);
    out.append(in).append("<name>").append(escape(def.name)).append("</name>").append(eol);
    if (def.title != null) {
      out.append(in).append(titleXml(form, "title", def.title, in)).append(eol);
    }
    out.append(in).append("<id>").append(id).append("</id>").append(eol);
    out.append(in).append(typeXml(form, def.type, in)).append(eol);
    appendViewEdit(out, in, eol);
    if (Boolean.TRUE.equals(def.savedData)) {
      out.append(in).append("<savedData>true</savedData>").append(eol);
    }
    List<AttributeDef> columns = def.columns == null ? List.of() : def.columns;
    if (!columns.isEmpty() && !tableType(def.type)) {
      throw new IllegalArgumentException("Колонки бывают у реквизита типа таблица или дерево значений.");
    }
    Set<String> names = new HashSet<>();
    int columnId = 0;
    for (AttributeDef column : columns) {
      column.name = required(column.name, "columns.name").trim();
      CatalogNameConstraints.check(column.name);
      if (!names.add(column.name)) {
        throw new IllegalArgumentException("Колонка " + column.name + " повторяется в описании.");
      }
      checkType(column.type, column.name);
      out.append(in).append(columnXml(form, column, String.valueOf(++columnId), in)).append(eol);
    }
    String typeName = def.type.types != null && def.type.types.size() == 1 ? def.type.types.get(0) : "";
    if ("v8:ValueListType".equals(typeName)) {
      // Тип элементов списка значений EDT пишет всегда, пустым - если он не задан
      out.append(in).append("<extInfo xsi:type=\"form:ValueListExtInfo\">").append(eol)
          .append(in).append(INDENT).append("<itemValueType/>").append(eol)
          .append(in).append("</extInfo>").append(eol);
    } else if ("mxl:SpreadsheetDocument".equals(typeName)) {
      out.append(in).append("<extInfo xsi:type=\"form:SpreadsheetDocumentExtInfo\"/>").append(eol);
    }
    return out.append(indent).append("</attributes>").toString();
  }

  private static String columnXml(Form form, AttributeDef column, String id, String indent) {
    String in = indent + INDENT;
    String eol = form.eol;
    StringBuilder out = new StringBuilder("<columns>").append(eol);
    out.append(in).append("<name>").append(escape(column.name)).append("</name>").append(eol);
    if (column.title != null) {
      out.append(in).append(titleXml(form, "title", column.title, in)).append(eol);
    }
    out.append(in).append("<id>").append(id).append("</id>").append(eol);
    out.append(in).append(typeXml(form, column.type, in)).append(eol);
    appendViewEdit(out, in, eol);
    return out.append(indent).append("</columns>").toString();
  }

  /** Просмотр и редактирование: EDT пишет их у каждого реквизита и колонки. */
  private static void appendViewEdit(StringBuilder out, String in, String eol) {
    for (String feature : List.of("view", "edit")) {
      out.append(in).append('<').append(feature).append('>').append(eol)
          .append(in).append(INDENT).append("<common>true</common>").append(eol)
          .append(in).append("</").append(feature).append('>').append(eol);
    }
  }

  private static String typeXml(Form form, MdTypeDescriptionDto type, String indent) {
    MdTypeDescriptionDto dto = type;
    if (dto.types == null) {
      dto.types = new ArrayList<>();
    }
    if (dto.typeSets == null) {
      dto.typeSets = new ArrayList<>();
    }
    return EdtTypeDescription.render(dto, "valueType", form.model, indent, form.eol);
  }

  private static String titleXml(Form form, String tag, String text, String indent) {
    return "<" + tag + ">" + form.eol
        + indent + INDENT + "<key>" + ConfigurationLanguage.current() + "</key>" + form.eol
        + indent + INDENT + "<value>" + escape(text) + "</value>" + form.eol
        + indent + "</" + tag + ">";
  }

  private static String actionXml(Form form, String action, String indent) {
    return "<action xsi:type=\"form:FormCommandHandlerContainer\">" + form.eol
        + indent + INDENT + "<handler>" + form.eol
        + indent + INDENT + INDENT + "<name>" + escape(action) + "</name>" + form.eol
        + indent + INDENT + "</handler>" + form.eol
        + indent + "</action>";
  }

  /** Новая запись корня: за последней такой же, а без них - на место по порядку схемы. */
  private static Edit insertRoot(Form form, List<Entry> entries, String feature, String block)
      throws XMLStreamException {
    int at = entries.isEmpty()
        ? EdtFormItemPropertyEdit.insertionPoint(form.xml, new Region(form.tree.form.start, form.tree.form.end),
            form.formClass, feature)
        : lineEnd(form.xml, entries.get(entries.size() - 1).end);
    return new Edit(at, at, INDENT + block + form.eol);
  }

  /** Свойство записи: на месте прежнего либо на своё место по порядку схемы. */
  private static Edit feature(Form form, Entry entry, EClass eClass, String name, String elementXml, String indent)
      throws XMLStreamException {
    Region current = child(form.xml, entry, name);
    if (current != null) {
      return new Edit(current.start(), current.end(), elementXml);
    }
    int at = EdtFormItemPropertyEdit.insertionPoint(form.xml, new Region(entry.start, entry.end), eClass, name);
    return new Edit(at, at, indent + elementXml + form.eol);
  }

  private static Region child(String xml, Entry entry, String name) throws XMLStreamException {
    for (EdtObjectRegions.Child child : EdtObjectRegions.children(xml, new Region(entry.start, entry.end))) {
      if (name.equals(child.name())) {
        return child.region();
      }
    }
    return null;
  }

  private static Edit nameNode(String xml, Entry entry, String newName) {
    Matcher name = NAME.matcher(xml).region(entry.start, entry.end);
    if (!name.find() || !unescape(name.group(1)).equals(entry.name)) {
      throw new IllegalStateException("У записи " + entry.name + " нет имени в файле.");
    }
    return new Edit(name.start(1), name.end(1), escape(newName));
  }

  private static EClass classOf(Form form, String name) {
    return form.formPackage.getEClassifier(name) instanceof EClass found ? found : null;
  }

  private static EClass itemClass(Form form, EdtFormTree.Node node) {
    return classOf(form, node.xsiType);
  }

  private static String xsiType(String xml, Region region) {
    Matcher m = Pattern.compile("xsi:type=\"(?:\\w+:)?(\\w+)\"").matcher(xml)
        .region(region.start(), xml.indexOf('>', region.start()));
    return m.find() ? m.group(1) : "";
  }

  private static Edit removal(String xml, int start, int end) {
    return new Edit(EdtObjectRegions.lineStart(xml, start), lineEnd(xml, end), "");
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

  private static String unescape(String text) {
    return text.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&amp;", "&");
  }

  // ---------- сверка ----------

  private static void requireAttribute(FormContentDto content, String path, boolean present) {
    int dot = path.indexOf('.');
    String owner = dot < 0 ? path : path.substring(0, dot);
    FormAttributeDto attribute = content.attributes.stream().filter(a -> owner.equals(a.name)).findFirst()
        .orElse(null);
    boolean found = attribute != null
        && (dot < 0 || attribute.columns.stream().anyMatch(c -> path.substring(dot + 1).equals(c.name)));
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
