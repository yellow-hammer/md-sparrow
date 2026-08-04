/*
 * This file is a part of md-sparrow.
 *
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package io.github.yellowhammer.designerxml.cf;

import io.github.yellowhammer.designerxml.DesignerXml;
import io.github.yellowhammer.designerxml.SchemaVersion;
import io.github.yellowhammer.designerxml.reflect.JaxbReflect;
import jakarta.xml.bind.JAXBElement;
import jakarta.xml.bind.JAXBException;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Чтение содержимого управляемой формы ({@code Forms/<Имя>/Ext/Form.xml}).
 *
 * <p>Свойства элементов объявлены у каждого типа по-своему, поэтому читаем их по именам методов и
 * пропускаем отсутствующие: так один читатель покрывает все типы элементов, а не только известные.
 */
public final class FormContentRead {

  private FormContentRead() {
  }

  /**
   * @param formXml путь к {@code Ext/Form.xml}
   * @param version версия формата выгрузки
   * @return содержимое формы
   */
  public static FormContentDto read(Path formXml, SchemaVersion version) throws IOException, JAXBException {
    if (!Files.isRegularFile(formXml)) {
      throw new IllegalArgumentException("file not found: " + formXml);
    }
    return readForm(JaxbReflect.value(DesignerXml.read(formXml, version)));
  }

  private static FormContentDto readForm(Object form) {
    FormContentDto dto = new FormContentDto();
    dto.title = titleText(form);
    dto.events = readEvents(form);
    dto.items = readItems(form);

    Object attributes = JaxbReflect.getOptional(form, "getAttributes");
    if (attributes != null) {
      for (Object attribute : JaxbReflect.list(attributes, "getAttribute")) {
        dto.attributes.add(readAttribute(attribute));
      }
    }
    Object commands = JaxbReflect.getOptional(form, "getCommands");
    if (commands != null) {
      for (Object command : JaxbReflect.list(commands, "getCommand")) {
        FormCommandDto c = new FormCommandDto();
        c.name = JaxbReflect.getStringOptional(command, "getName");
        c.title = titleText(command);
        Object action = JaxbReflect.getOptional(command, "getAction");
        c.action = action == null ? null : JaxbReflect.getStringOptional(action, "getValue");
        dto.commands.add(c);
      }
    }
    Object parameters = JaxbReflect.getOptional(form, "getParameters");
    if (parameters != null) {
      for (Object parameter : JaxbReflect.list(parameters, "getParameter")) {
        FormParameterDto p = new FormParameterDto();
        p.name = JaxbReflect.getStringOptional(parameter, "getName");
        p.type = MdTypeDescriptionBridge.read(JaxbReflect.getOptional(parameter, "getType"));
        p.key = JaxbReflect.getBooleanOptional(parameter, "isKeyParameter");
        dto.parameters.add(p);
      }
    }
    return dto;
  }

  private static FormAttributeDto readAttribute(Object attribute) {
    FormAttributeDto dto = new FormAttributeDto();
    dto.name = JaxbReflect.getStringOptional(attribute, "getName");
    dto.title = titleText(attribute);
    dto.type = MdTypeDescriptionBridge.read(JaxbReflect.getOptional(attribute, "getType"));
    dto.main = JaxbReflect.getBooleanOptional(attribute, "isMainAttribute");
    Object columns = JaxbReflect.getOptional(attribute, "getColumns");
    if (columns != null) {
      // Помимо колонок в этом списке лежит блок дополнительных колонок - у него нет имени.
      for (Object column : JaxbReflect.list(columns, "getColumns")) {
        if (JaxbReflect.getStringOptional(column, "getName") != null) {
          dto.columns.add(readAttribute(column));
        }
      }
    }
    return dto;
  }

  /**
   * Элементы, которые платформа держит отдельными узлами рядом с {@code ChildItems}, а не внутри него:
   * командная панель, контекстное меню, подсказка, строка поиска. В файле они идут перед {@code ChildItems},
   * в таком же порядке отдаём их и здесь.
   */
  private static final List<String> ATTACHED_ITEMS = List.of(
    "ContextMenu",
    "AutoCommandBar",
    "ExtendedTooltip",
    "SearchStringAddition",
    "ViewStatusAddition",
    "SearchControlAddition");

  private static List<FormItemDto> readItems(Object owner) {
    List<FormItemDto> out = new java.util.ArrayList<>();
    for (String type : ATTACHED_ITEMS) {
      Object attached = JaxbReflect.getOptional(owner, "get" + type);
      if (attached != null) {
        out.add(readItem(type, attached));
      }
    }
    Object childItems = JaxbReflect.getOptional(owner, "getChildItems");
    if (childItems != null) {
      for (JAXBElement<?> element : JaxbReflect.<JAXBElement<?>>list(childItems, "getItems")) {
        out.add(readItem(element.getName().getLocalPart(), element.getValue()));
      }
    }
    return out;
  }

  private static FormItemDto readItem(String type, Object item) {
    FormItemDto dto = new FormItemDto();
    dto.type = type;
    dto.name = JaxbReflect.getStringOptional(item, "getName");
    dto.id = decimalText(JaxbReflect.getOptional(item, "getId"));
    dto.title = titleText(item);
    dto.dataPath = JaxbReflect.getStringOptional(item, "getDataPath");
    dto.group = enumText(JaxbReflect.getOptional(item, "getGroup"));
    dto.showTitle = enumText(JaxbReflect.getOptional(item, "getShowTitle"));
    dto.titleLocation = enumText(JaxbReflect.getOptional(item, "getTitleLocation"));
    dto.representation = enumText(JaxbReflect.getOptional(item, "getRepresentation"));
    dto.visible = booleanOrNull(JaxbReflect.getOptional(item, "isVisible"));
    dto.enabled = booleanOrNull(JaxbReflect.getOptional(item, "isEnabled"));
    dto.readOnly = booleanOrNull(JaxbReflect.getOptional(item, "isReadOnly"));
    dto.width = decimalText(JaxbReflect.getOptional(item, "getWidth"));
    dto.height = decimalText(JaxbReflect.getOptional(item, "getHeight"));
    dto.horizontalStretch = stretchText(item, "HorizontalStretch");
    dto.verticalStretch = stretchText(item, "VerticalStretch");
    dto.events = readEvents(item);
    dto.items = readItems(item);
    return dto;
  }

  private static List<FormEventDto> readEvents(Object owner) {
    List<FormEventDto> out = new java.util.ArrayList<>();
    Object events = JaxbReflect.getOptional(owner, "getEvents");
    if (events == null) {
      return out;
    }
    for (Object event : JaxbReflect.list(events, "getEvent")) {
      FormEventDto dto = new FormEventDto();
      dto.name = JaxbReflect.getStringOptional(event, "getName");
      dto.handler = JaxbReflect.getStringOptional(event, "getValue");
      dto.callType = enumText(JaxbReflect.getOptional(event, "getCallType"));
      out.add(dto);
    }
    return out;
  }

  /** Растяжение у одних типов булево, у других - «да/нет/авто», отсюда два имени метода. */
  private static String stretchText(Object item, String property) {
    Object value = JaxbReflect.getOptional(item, "get" + property);
    if (value == null) {
      value = JaxbReflect.getOptional(item, "is" + property);
    }
    return value == null ? null : enumText(value);
  }

  /** Заголовок ru; пустой не отдаём, чтобы в модели не было пустых строк вместо «не задано». */
  private static String titleText(Object owner) {
    String title = LocalStringSync.firstRu(JaxbReflect.getOptional(owner, "getTitle"));
    return title == null || title.isEmpty() ? null : title;
  }

  private static Boolean booleanOrNull(Object value) {
    return value instanceof Boolean b ? b : null;
  }

  private static String decimalText(Object value) {
    return value instanceof BigDecimal d ? d.stripTrailingZeros().toPlainString() : null;
  }

  private static String enumText(Object value) {
    if (value == null) {
      return null;
    }
    return value instanceof Enum<?> e ? e.name() : String.valueOf(value);
  }
}
