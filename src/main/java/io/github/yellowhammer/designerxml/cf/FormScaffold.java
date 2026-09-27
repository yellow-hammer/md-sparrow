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
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Создание форм объектов из эталона платформы и сборка содержимого формы из
 * JSON-описания элементов.
 *
 * <p>Эталон - пустая управляемая форма, выгруженная платформой ({@link GoldenScaffold}):
 * файл формата - проекция канонического, описание параметризуется именем, содержимое
 * собирается текстом по образцам выгрузки и проверяется обратным чтением JAXB-моделью
 * схемы logform.
 */
public final class FormScaffold {

  private static final String META_DATA_OBJECT = "MetaDataObject";
  private static final String CHILD_OBJECTS = "ChildObjects";
  private static final String FORM = "Form";

  /**
   * Форматы, в которых платформа выгружает форму с основным реквизитом как есть: 8.3.22-8.3.24
   * (2.15-2.17). Остальные линейки, 8.3.17-8.3.21 и 8.3.25-8.5.1, дописывают свойства по виду
   * основного реквизита ({@link #MAIN_ATTRIBUTE_DEFAULTS}).
   */
  private static final Set<SchemaVersion> MAIN_ATTRIBUTE_AS_IS =
    EnumSet.of(SchemaVersion.V2_15, SchemaVersion.V2_16, SchemaVersion.V2_17);

  private static final List<String> REPORT_FORM_DEFAULTS = List.of(
    "<ReportFormType>Main</ReportFormType>",
    "<AutoShowState>Auto</AutoShowState>",
    "<ReportResultViewMode>Auto</ReportResultViewMode>",
    "<ViewModeApplicationOnSetReportResult>Auto</ViewModeApplicationOnSetReportResult>");

  /**
   * Свойства корня формы, которые платформа при загрузке дописывает форме с основным реквизитом такого
   * вида, если их нет в файле, и потом выгружает; кроме форматов {@link #MAIN_ATTRIBUTE_AS_IS}.
   * В формат попадают только свойства, которые знает его модель: {@code ViewModeApplicationOnSetReportResult}
   * есть с 2.12, и 8.3.17-8.3.18 его не пишут. Источник - загрузка и выгрузка форм, собранных
   * cf-form-compile, ibcmd всех двенадцати линеек (tools/golden-snapshots/roundtrip.py). Форму внешнего
   * отчёта так проверить можно только с 8.3.23 (раньше ibcmd внешние объекты не собирает): там она ведёт
   * себя как форма отчёта той же платформы, и в 2.10-2.15 правило для неё взято с формы отчёта. У
   * обработки, задачи, плана счетов, плана видов расчёта и плана обмена платформа не дописывает ничего.
   */
  private static final Map<String, List<String>> MAIN_ATTRIBUTE_DEFAULTS = Map.of(
    "CatalogObject", List.of("<UseForFoldersAndItems>Items</UseForFoldersAndItems>"),
    "ChartOfCharacteristicTypesObject", List.of("<UseForFoldersAndItems>Items</UseForFoldersAndItems>"),
    "DocumentObject", List.of(
      "<AutoTime>CurrentOrLast</AutoTime>",
      "<UsePostingMode>Auto</UsePostingMode>",
      "<RepostOnWrite>true</RepostOnWrite>"),
    "ReportObject", REPORT_FORM_DEFAULTS,
    "ExternalReportObject", REPORT_FORM_DEFAULTS);

  private FormScaffold() {
  }

  /**
   * Добавляет объекту пустую управляемую форму: описание, содержимое и запись
   * в ChildObjects.
   *
   * @param objectXml XML объекта-владельца
   * @param formName имя новой формы
   */
  public static void addForm(Path objectXml, SchemaVersion version, String formName)
    throws IOException, JAXBException {
    ConfigurationLanguage.with(objectXml, () -> {
      addFormInLanguage(objectXml, version, formName);
      return null;
    });
  }

  private static void addFormInLanguage(Path objectXml, SchemaVersion version, String formName)
    throws IOException, JAXBException {
    SupportRules.ensureEditable(objectXml);
    CatalogNameConstraints.check(formName);
    Path descriptor = formDescriptorPath(objectXml, formName);
    if (Files.exists(descriptor)) {
      throw new IllegalArgumentException("Форма уже существует: " + formName);
    }
    String objectText = Files.readString(objectXml, StandardCharsets.UTF_8);
    if (objectText.contains("<Form>" + formName + "</Form>")) {
      throw new IllegalArgumentException("Форма уже объявлена в составе: " + formName);
    }

    String ownerKind = ownerKind(objectText);
    ChildObjectKinds.ensureAllowed(version, ownerKind, FORM);

    // Эталон снят на русской конфигурации: подпись уезжает в язык той, к которой относится форма
    String descriptorXml = LocalStringElement.retarget(
      GoldenScaffold.generateFormDescriptor(ownerKind, stem(objectXml), objectText, formName, version),
      ConfigurationLanguage.current());
    String contentXml = FormNamespaceRules.forCompatibility(
      GoldenScaffold.generateFormContent(version), compatibilityModeOf(objectXml, ownerKind));

    String updated = insertFormEntry(objectText, ownerKind, formName, version);
    MdObjectStructureRead.read(updated.getBytes(StandardCharsets.UTF_8), version);

    Files.createDirectories(descriptor.getParent());
    Files.writeString(descriptor, descriptorXml, StandardCharsets.UTF_8);
    Path content = formContentPath(objectXml, formName);
    Files.createDirectories(content.getParent());
    Files.writeString(content, contentXml, StandardCharsets.UTF_8);
    Files.writeString(objectXml, updated, StandardCharsets.UTF_8);
  }

  /**
   * Собирает содержимое формы из JSON-описания элементов.
   *
   * <p>Форма создаётся, если её ещё нет. Поддерживаются группы, поля ввода,
   * флажки, надписи, страницы и таблицы с колонками; основной реквизит
   * добавляется по описанию. Результат проверяется чтением модели схемы.
   *
   * @param definitionJson JSON: {@code {"synonym": "...", "mainAttribute": {"name", "type"},
   *   "items": [{"input"|"check"|"label"|"group"|"table"|"pages"|"page": "Имя", ...}]}}
   */
  public static void compileForm(Path objectXml, SchemaVersion version, String formName, String definitionJson)
    throws IOException, JAXBException {
    ConfigurationLanguage.with(objectXml, () -> {
      compileFormInLanguage(objectXml, version, formName, definitionJson);
      return null;
    });
  }

  private static void compileFormInLanguage(Path objectXml, SchemaVersion version, String formName,
    String definitionJson) throws IOException, JAXBException {
    SupportRules.ensureEditable(objectXml);
    Path descriptor = formDescriptorPath(objectXml, formName);
    if (!Files.exists(descriptor)) {
      addForm(objectXml, version, formName);
    }
    FormDefinition definition = FormDefinition.parse(definitionJson);
    String ownerKind = ownerKind(Files.readString(objectXml, StandardCharsets.UTF_8));
    String contentXml = FormNamespaceRules.forCompatibility(
      buildContent(version, definition), compatibilityModeOf(objectXml, ownerKind));
    verifyContent(contentXml, version);
    Files.writeString(formContentPath(objectXml, formName), contentXml, StandardCharsets.UTF_8);
    if (definition.synonym != null && !definition.synonym.isBlank()) {
      String descriptorXml = Files.readString(descriptor, StandardCharsets.UTF_8);
      Files.writeString(
        descriptor,
        ScaffoldPropertyEdit.setSynonym(descriptorXml, definition.synonym, ConfigurationLanguage.current()),
        StandardCharsets.UTF_8);
    }
  }

  /**
   * Режим совместимости конфигурации или расширения, которому принадлежит объект-владелец формы.
   *
   * <p>От него зависит, объявляет ли форма пространство схемы компоновки: в режиме ниже 8.3.19
   * платформа его не пишет (ibcmd 8.3.27, конфигурация в режиме 8.3.12: форма справочника после
   * загрузки и выгрузки теряет {@code xmlns:dcssch}). У расширения действует его собственный режим
   * ({@link FormNamespaceRules#compatibilityModeOf}). У внешних отчётов и обработок режима нет,
   * их формы платформа выгружает с объявлением.
   *
   * @return режим или {@code null}, если конфигурации нет
   */
  private static String compatibilityModeOf(Path objectXml, String ownerKind) throws IOException {
    if (ownerKind.startsWith("External")) {
      return null;
    }
    Path kindDir = objectXml.toAbsolutePath().getParent();
    Path cfRoot = kindDir == null ? null : kindDir.getParent();
    if (cfRoot == null) {
      return null;
    }
    Path configurationXml = cfRoot.resolve(CfLayout.CONFIGURATION_XML);
    if (!Files.isRegularFile(configurationXml)) {
      return null;
    }
    return FormNamespaceRules.compatibilityModeOf(Files.readString(configurationXml, StandardCharsets.UTF_8));
  }

  /** Содержимое формы обязано читаться моделью схемы: битую форму не пишем. */
  private static void verifyContent(String contentXml, SchemaVersion version) throws IOException {
    try (InputStream in = new ByteArrayInputStream(contentXml.getBytes(StandardCharsets.UTF_8))) {
      Object root = DesignerXml.unmarshal(version, in);
      if (root == null) {
        throw new IOException("Содержимое формы не прочиталось моделью схемы.");
      }
    } catch (JAXBException e) {
      throw new IOException("Содержимое формы не прошло проверку схемой: " + e.getMessage(), e);
    }
  }

  private static String buildContent(SchemaVersion version, FormDefinition definition) throws IOException {
    String golden = GoldenScaffold.generateFormContent(version);
    String eol = XmlLines.newline(golden);
    int open = golden.indexOf('>', golden.indexOf("<Form"));
    StringBuilder body = new StringBuilder(eol);
    if (!MAIN_ATTRIBUTE_AS_IS.contains(version) && definition.mainAttributeType != null) {
      for (String property : MAIN_ATTRIBUTE_DEFAULTS.getOrDefault(typeKind(definition.mainAttributeType), List.of())) {
        body.append('\t').append(property).append(eol);
      }
    }
    body.append("\t<AutoCommandBar name=\"ФормаКоманднаяПанель\" id=\"-1\"/>").append(eol);
    IdCounter ids = new IdCounter();
    if (!definition.items.isEmpty()) {
      body.append("\t<ChildItems>").append(eol);
      for (FormItemDef item : definition.items) {
        appendItem(body, item, ids, 2, eol);
      }
      body.append("\t</ChildItems>").append(eol);
    }
    if (definition.mainAttributeName != null) {
      body.append("\t<Attributes>").append(eol);
      body.append("\t\t<Attribute name=\"").append(escape(definition.mainAttributeName))
        .append("\" id=\"1\">").append(eol);
      body.append("\t\t\t<Type>").append(eol);
      body.append("\t\t\t\t<v8:Type>").append(escape(definition.mainAttributeType)).append("</v8:Type>").append(eol);
      body.append("\t\t\t</Type>").append(eol);
      body.append("\t\t\t<MainAttribute>true</MainAttribute>").append(eol);
      body.append("\t\t\t<SavedData>true</SavedData>").append(eol);
      body.append("\t\t</Attribute>").append(eol);
      body.append("\t</Attributes>").append(eol);
    } else {
      body.append("\t<Attributes/>").append(eol);
    }
    // свойства, которых модель формата не знает, проекция убирает
    return FormatProjection.project(golden.substring(0, open + 1) + body + "</Form>", version);
  }

  /** Вид типа без пространства и имени объекта: {@code cfg:CatalogObject.Товары} - {@code CatalogObject}. */
  private static String typeKind(String type) {
    String local = type.substring(type.indexOf(':') + 1);
    int dot = local.indexOf('.');
    return dot < 0 ? local : local.substring(0, dot);
  }

  private static void appendItem(StringBuilder out, FormItemDef item, IdCounter ids, int depth, String eol) {
    String pad = "\t".repeat(depth);
    switch (item.kind) {
      case "input", "check" -> {
        String tag = "input".equals(item.kind) ? "InputField" : "CheckBoxField";
        out.append(pad).append('<').append(tag).append(" name=\"").append(escape(item.name))
          .append("\" id=\"").append(ids.next()).append("\">").append(eol);
        if (item.dataPath != null) {
          out.append(pad).append("\t<DataPath>").append(escape(item.dataPath)).append("</DataPath>").append(eol);
        }
        if (item.title != null) {
          appendTitle(out, item.title, pad + "\t", eol);
        }
        out.append(pad).append("\t<ContextMenu name=\"").append(escape(item.name))
          .append("КонтекстноеМеню\" id=\"").append(ids.next()).append("\"/>").append(eol);
        appendTooltip(out, item.name, ids, pad + "\t", eol);
        out.append(pad).append("</").append(tag).append('>').append(eol);
      }
      case "label" -> {
        out.append(pad).append("<LabelDecoration name=\"").append(escape(item.name))
          .append("\" id=\"").append(ids.next()).append("\">").append(eol);
        appendTitle(out, item.title != null ? item.title : item.name, pad + "\t", eol);
        appendTooltip(out, item.name, ids, pad + "\t", eol);
        out.append(pad).append("</LabelDecoration>").append(eol);
      }
      case "group", "page" -> {
        String tag = "group".equals(item.kind) ? "UsualGroup" : "Page";
        out.append(pad).append('<').append(tag).append(" name=\"").append(escape(item.name))
          .append("\" id=\"").append(ids.next()).append("\">").append(eol);
        if (item.title != null) {
          appendTitle(out, item.title, pad + "\t", eol);
        }
        if ("group".equals(item.kind)) {
          out.append(pad).append("\t<Group>").append("horizontal".equals(item.direction) ? "Horizontal" : "Vertical")
            .append("</Group>").append(eol);
          out.append(pad).append("\t<ShowTitle>").append(item.title != null).append("</ShowTitle>").append(eol);
        }
        appendChildren(out, item, ids, pad, depth, eol);
        appendTooltip(out, item.name, ids, pad + "\t", eol);
        out.append(pad).append("</").append(tag).append('>').append(eol);
      }
      case "pages" -> {
        out.append(pad).append("<Pages name=\"").append(escape(item.name))
          .append("\" id=\"").append(ids.next()).append("\">").append(eol);
        appendChildren(out, item, ids, pad, depth, eol);
        appendTooltip(out, item.name, ids, pad + "\t", eol);
        out.append(pad).append("</Pages>").append(eol);
      }
      case "table" -> {
        out.append(pad).append("<Table name=\"").append(escape(item.name))
          .append("\" id=\"").append(ids.next()).append("\">").append(eol);
        if (item.dataPath != null) {
          out.append(pad).append("\t<DataPath>").append(escape(item.dataPath)).append("</DataPath>").append(eol);
        }
        if (item.title != null) {
          appendTitle(out, item.title, pad + "\t", eol);
        }
        out.append(pad).append("\t<ContextMenu name=\"").append(escape(item.name))
          .append("КонтекстноеМеню\" id=\"").append(ids.next()).append("\"/>").append(eol);
        out.append(pad).append("\t<AutoCommandBar name=\"").append(escape(item.name))
          .append("КоманднаяПанель\" id=\"").append(ids.next()).append("\"/>").append(eol);
        out.append(pad).append("\t<SearchStringAddition name=\"").append(escape(item.name))
          .append("СтрокаПоиска\" id=\"").append(ids.next()).append("\"/>").append(eol);
        out.append(pad).append("\t<ViewStatusAddition name=\"").append(escape(item.name))
          .append("СостояниеПросмотра\" id=\"").append(ids.next()).append("\"/>").append(eol);
        out.append(pad).append("\t<SearchControlAddition name=\"").append(escape(item.name))
          .append("УправлениеПоиском\" id=\"").append(ids.next()).append("\"/>").append(eol);
        appendChildren(out, item, ids, pad, depth, eol);
        appendTooltip(out, item.name, ids, pad + "\t", eol);
        out.append(pad).append("</Table>").append(eol);
      }
      default -> throw new IllegalArgumentException("Неизвестный элемент формы: " + item.kind);
    }
  }

  private static void appendChildren(StringBuilder out, FormItemDef item, IdCounter ids, String pad, int depth,
    String eol) {
    if (item.items.isEmpty()) {
      return;
    }
    out.append(pad).append("\t<ChildItems>").append(eol);
    for (FormItemDef child : item.items) {
      appendItem(out, child, ids, depth + 2, eol);
    }
    out.append(pad).append("\t</ChildItems>").append(eol);
  }

  private static void appendTitle(StringBuilder out, String title, String pad, String eol) {
    out.append(pad).append("<Title>").append(eol);
    out.append(pad).append("\t<v8:item>").append(eol);
    out.append(pad).append("\t\t<v8:lang>").append(ConfigurationLanguage.current())
        .append("</v8:lang>").append(eol);
    out.append(pad).append("\t\t<v8:content>").append(escape(title)).append("</v8:content>").append(eol);
    out.append(pad).append("\t</v8:item>").append(eol);
    out.append(pad).append("</Title>").append(eol);
  }

  private static void appendTooltip(StringBuilder out, String name, IdCounter ids, String pad, String eol) {
    out.append(pad).append("<ExtendedTooltip name=\"").append(escape(name))
      .append("РасширеннаяПодсказка\" id=\"").append(ids.next()).append("\"/>").append(eol);
  }

  /** Вид владельца: элемент под корнем {@code MetaDataObject} ({@code Catalog}, {@code ExternalReport}). */
  private static String ownerKind(String objectXml) {
    List<XmlLines.Node> roots = XmlLines.children(objectXml, List.of(META_DATA_OBJECT));
    if (roots.isEmpty()) {
      throw new IllegalArgumentException("Файл не описывает объект метаданных.");
    }
    return roots.get(0).name();
  }

  /**
   * Запись формы в корневой ChildObjects владельца - там, где её пишет платформа.
   *
   * <p>Подчинённые узлы платформа выгружает в порядке схемы формата ({@link ChildObjectKinds}):
   * у справочника формы идут за реквизитами и табличными частями, у документа - между
   * реквизитами и табличными частями, у задачи - перед реквизитами адресации, макеты и команды -
   * всегда после форм. Поэтому новая форма встаёт перед первым узлом, который в схеме идёт после
   * форм, то есть и после уже объявленных форм. ChildObjects табличных частей не затрагиваются.
   */
  private static String insertFormEntry(String xml, String ownerKind, String formName, SchemaVersion version) {
    String eol = XmlLines.newline(xml);
    String entry = "<" + FORM + ">" + escape(formName) + "</" + FORM + ">";
    XmlLines.Node childObjects = XmlLines.children(xml, List.of(META_DATA_OBJECT, ownerKind)).stream()
      .filter(node -> CHILD_OBJECTS.equals(node.name()))
      .findFirst()
      .orElseThrow(() -> new IllegalArgumentException("В объекте нет узла ChildObjects."));
    String indent = XmlLines.indentAt(xml, childObjects.start());
    if (xml.startsWith("/>", childObjects.end() - 2)) {
      return xml.substring(0, childObjects.start())
        + "<" + CHILD_OBJECTS + ">" + eol
        + indent + '\t' + entry + eol
        + indent + "</" + CHILD_OBJECTS + ">"
        + xml.substring(childObjects.end());
    }
    List<String> order = ChildObjectKinds.of(version, ownerKind);
    int formRank = order.indexOf(FORM);
    for (XmlLines.Node child : XmlLines.children(xml, List.of(META_DATA_OBJECT, ownerKind, CHILD_OBJECTS))) {
      if (order.indexOf(child.name()) > formRank) {
        return insertLineBefore(xml, child.start(), XmlLines.indentAt(xml, child.start()) + entry + eol);
      }
    }
    // Все узлы состава идут раньше форм: запись - последней строкой состава
    int closing = xml.lastIndexOf("</", childObjects.end() - 1);
    return insertLineBefore(xml, closing, indent + '\t' + entry + eol);
  }

  /** Вставляет строку {@code line} перед строкой, на которой стоит {@code offset}. */
  private static String insertLineBefore(String xml, int offset, String line) {
    int lineStart = xml.lastIndexOf('\n', offset - 1) + 1;
    return xml.substring(0, lineStart) + line + xml.substring(lineStart);
  }

  private static Path formDescriptorPath(Path objectXml, String formName) {
    return formsDir(objectXml).resolve(formName + ".xml");
  }

  private static Path formContentPath(Path objectXml, String formName) {
    return formsDir(objectXml).resolve(formName).resolve("Ext").resolve("Form.xml");
  }

  private static Path formsDir(Path objectXml) {
    Path normalized = objectXml.toAbsolutePath().normalize();
    return normalized.getParent().resolve(stem(normalized)).resolve("Forms");
  }

  /** Имя объекта по файлу описания: {@code Catalogs/Товары.xml} - {@code Товары}. */
  private static String stem(Path objectXml) {
    return objectXml.getFileName().toString().replaceFirst("[.][Xx][Mm][Ll]$", "");
  }

  private static String escape(String value) {
    return value == null
      ? ""
      : value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
  }

  private static final class IdCounter {
    private int value;

    int next() {
      value += 1;
      return value;
    }
  }

  /** Разобранное JSON-описание формы. */
  static final class FormDefinition {
    String synonym;
    String mainAttributeName;
    String mainAttributeType;
    List<FormItemDef> items = new ArrayList<>();

    @SuppressWarnings("unchecked")
    static FormDefinition parse(String json) {
      Map<String, Object> raw = new com.google.gson.Gson().fromJson(json, Map.class);
      if (raw == null) {
        throw new IllegalArgumentException("Пустое описание формы.");
      }
      FormDefinition out = new FormDefinition();
      Object synonym = raw.get("synonym");
      out.synonym = synonym == null ? null : String.valueOf(synonym);
      Object main = raw.get("mainAttribute");
      if (main instanceof Map<?, ?> holder) {
        out.mainAttributeName = String.valueOf(holder.get("name"));
        out.mainAttributeType = String.valueOf(holder.get("type"));
        if (out.mainAttributeName.isBlank() || out.mainAttributeType.isBlank()) {
          throw new IllegalArgumentException("У основного реквизита нужны name и type.");
        }
      }
      Object items = raw.get("items");
      if (items instanceof List<?> list) {
        for (Object item : list) {
          out.items.add(FormItemDef.parse(item));
        }
      }
      return out;
    }
  }

  /** Элемент формы из JSON: вид определяется по ключу с именем. */
  static final class FormItemDef {
    static final List<String> KINDS = List.of("group", "input", "check", "label", "table", "pages", "page");

    String kind;
    String name;
    String dataPath;
    String title;
    String direction;
    List<FormItemDef> items = new ArrayList<>();

    static FormItemDef parse(Object raw) {
      if (!(raw instanceof Map<?, ?> map)) {
        throw new IllegalArgumentException("Элемент формы должен быть объектом JSON.");
      }
      FormItemDef out = new FormItemDef();
      for (String kind : KINDS) {
        Object name = map.get(kind);
        if (name != null) {
          out.kind = kind;
          out.name = String.valueOf(name);
          break;
        }
      }
      if (out.kind == null) {
        throw new IllegalArgumentException(
          "У элемента формы нет вида: ожидается один из ключей " + String.join(", ", KINDS));
      }
      CatalogNameConstraints.check(out.name);
      Object dataPath = map.get("dataPath");
      out.dataPath = dataPath == null ? null : String.valueOf(dataPath);
      Object title = map.get("title");
      out.title = title == null ? null : String.valueOf(title);
      Object direction = map.get("direction");
      out.direction = direction == null ? null : String.valueOf(direction);
      Object columns = map.get("columns") != null ? map.get("columns") : map.get("items");
      if (columns instanceof List<?> list) {
        for (Object item : list) {
          out.items.add(parse(item));
        }
      }
      return out;
    }
  }
}
