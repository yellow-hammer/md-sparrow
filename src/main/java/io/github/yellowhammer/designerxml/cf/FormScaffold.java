/*
 * This file is a part of md-sparrow.
 *
 * Copyright (c) 2026
 * Ivan Karlo <i.karlo@outlook.com> and contributors
 *
 * SPDX-License-Identifier: LGPL-3.0-or-later
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
import java.util.List;
import java.util.Map;

/**
 * Создание форм объектов из эталона платформы и сборка содержимого формы из
 * JSON-описания элементов.
 *
 * <p>Эталон - пустая управляемая форма, выгруженная платформой: описание
 * параметризуется именем, содержимое собирается текстом по образцам выгрузки и
 * проверяется обратным чтением JAXB-моделью схемы logform.
 */
public final class FormScaffold {

  private static final String GOLDEN_DESCRIPTOR = "Форма.xml";
  private static final String GOLDEN_CONTENT = "Ext.xml";

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

    String descriptorXml = GoldenObjectTemplate.parametrize(
      readGolden(GOLDEN_DESCRIPTOR, version),
      "Форма",
      formName,
      "form|" + version.name() + "|" + formName);
    String contentXml = readGolden(GOLDEN_CONTENT, version);

    String updated = insertFormEntry(objectText, formName);
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
    SupportRules.ensureEditable(objectXml);
    Path descriptor = formDescriptorPath(objectXml, formName);
    if (!Files.exists(descriptor)) {
      addForm(objectXml, version, formName);
    }
    FormDefinition definition = FormDefinition.parse(definitionJson);
    String contentXml = buildContent(version, definition);
    verifyContent(contentXml, version);
    Files.writeString(formContentPath(objectXml, formName), contentXml, StandardCharsets.UTF_8);
    if (definition.synonym != null && !definition.synonym.isBlank()) {
      String descriptorXml = Files.readString(descriptor, StandardCharsets.UTF_8);
      Files.writeString(
        descriptor,
        ScaffoldPropertyEdit.setSynonymRu(descriptorXml, definition.synonym),
        StandardCharsets.UTF_8);
    }
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
    String golden = readGolden(GOLDEN_CONTENT, version);
    String eol = golden.contains("\r\n") ? "\r\n" : "\n";
    int open = golden.indexOf('>', golden.indexOf("<Form"));
    StringBuilder body = new StringBuilder();
    body.append(eol).append("\t<AutoCommandBar name=\"ФормаКоманднаяПанель\" id=\"-1\"/>").append(eol);
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
    return golden.substring(0, open + 1) + body + "</Form>";
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
    out.append(pad).append("\t\t<v8:lang>ru</v8:lang>").append(eol);
    out.append(pad).append("\t\t<v8:content>").append(escape(title)).append("</v8:content>").append(eol);
    out.append(pad).append("\t</v8:item>").append(eol);
    out.append(pad).append("</Title>").append(eol);
  }

  private static void appendTooltip(StringBuilder out, String name, IdCounter ids, String pad, String eol) {
    out.append(pad).append("<ExtendedTooltip name=\"").append(escape(name))
      .append("РасширеннаяПодсказка\" id=\"").append(ids.next()).append("\"/>").append(eol);
  }

  /** Запись формы в ChildObjects: после последней формы либо первой строкой состава. */
  private static String insertFormEntry(String objectXml, String formName) {
    String eol = objectXml.contains("\r\n") ? "\r\n" : "\n";
    String entry = "<Form>" + escape(formName) + "</Form>";
    int lastForm = objectXml.lastIndexOf("</Form>");
    if (lastForm >= 0 && objectXml.lastIndexOf("<Form>", lastForm) >= 0) {
      int lineEnd = objectXml.indexOf('\n', lastForm);
      int lineStart = objectXml.lastIndexOf('\n', lastForm);
      String indent = objectXml.substring(lineStart + 1, objectXml.indexOf('<', lineStart));
      return objectXml.substring(0, lineEnd + 1) + indent + entry + eol + objectXml.substring(lineEnd + 1);
    }
    int selfClosed = objectXml.indexOf("<ChildObjects/>");
    if (selfClosed >= 0) {
      int lineStart = objectXml.lastIndexOf('\n', selfClosed);
      String indent = objectXml.substring(lineStart + 1, selfClosed);
      return objectXml.substring(0, selfClosed)
        + "<ChildObjects>" + eol
        + indent + '\t' + entry + eol
        + indent + "</ChildObjects>"
        + objectXml.substring(selfClosed + "<ChildObjects/>".length());
    }
    int openTag = objectXml.indexOf("<ChildObjects>");
    if (openTag < 0) {
      throw new IllegalArgumentException("В объекте нет узла ChildObjects.");
    }
    int lineEnd = objectXml.indexOf('\n', openTag);
    int lineStart = objectXml.lastIndexOf('\n', openTag);
    String indent = objectXml.substring(lineStart + 1, openTag);
    return objectXml.substring(0, lineEnd + 1) + indent + '\t' + entry + eol + objectXml.substring(lineEnd + 1);
  }

  private static Path formDescriptorPath(Path objectXml, String formName) {
    return formsDir(objectXml).resolve(formName + ".xml");
  }

  private static Path formContentPath(Path objectXml, String formName) {
    return formsDir(objectXml).resolve(formName).resolve("Ext").resolve("Form.xml");
  }

  private static Path formsDir(Path objectXml) {
    Path normalized = objectXml.toAbsolutePath().normalize();
    String stem = normalized.getFileName().toString().replaceFirst("[.][Xx][Mm][Ll]$", "");
    return normalized.getParent().resolve(stem).resolve("Forms");
  }

  private static String readGolden(String file, SchemaVersion version) throws IOException {
    String resource = "golden-form/" + version.metadataObjectVersionAttribute() + "/" + file;
    try (InputStream in = FormScaffold.class.getClassLoader().getResourceAsStream(resource)) {
      if (in == null) {
        throw new IOException(
          "Нет эталона формы формата " + version.metadataObjectVersionAttribute()
            + ". Добавьте выгрузку в samples-1c-platform (external-files/empty-full-objects).");
      }
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }
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
