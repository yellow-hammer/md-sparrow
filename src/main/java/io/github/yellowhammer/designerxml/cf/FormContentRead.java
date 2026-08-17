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

import java.io.ByteArrayInputStream;
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

  /**
   * Чтение содержимого из UTF-8 байтов (сверка после точечной записи).
   *
   * @param utf8Xml байты {@code Ext/Form.xml}
   * @param version версия формата выгрузки
   */
  public static FormContentDto read(byte[] utf8Xml, SchemaVersion version) throws JAXBException {
    return readForm(JaxbReflect.value(DesignerXml.unmarshal(version, new ByteArrayInputStream(utf8Xml))));
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
    Object settings = JaxbReflect.getOptional(attribute, "getSettings");
    if (settings != null) {
      Object mainTable = JaxbReflect.getOptional(settings, "getMainTable");
      dto.mainTable = mainTable == null ? null : String.valueOf(mainTable);
    }
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
    dto.showTitle = enumOrBoolText(item, "ShowTitle");
    dto.titleLocation = enumText(JaxbReflect.getOptional(item, "getTitleLocation"));
    dto.representation = enumText(JaxbReflect.getOptional(item, "getRepresentation"));
    dto.visible = booleanOrNull(JaxbReflect.getOptional(item, "isVisible"));
    dto.enabled = booleanOrNull(JaxbReflect.getOptional(item, "isEnabled"));
    dto.readOnly = booleanOrNull(JaxbReflect.getOptional(item, "isReadOnly"));
    dto.width = decimalText(JaxbReflect.getOptional(item, "getWidth"));
    dto.height = decimalText(JaxbReflect.getOptional(item, "getHeight"));
    dto.horizontalStretch = enumOrBoolText(item, "HorizontalStretch");
    dto.verticalStretch = enumOrBoolText(item, "VerticalStretch");
    dto.choicePresentation = choicePresentation(item);
    dto.events = readEvents(item);
    dto.properties = writtenProperties(item);
    dto.items = readItems(item);
    return dto;
  }

  /**
   * Значения свойств, которые реально записаны в файле: имя узла XML -> текст.
   *
   * <p>Типизированных полей DTO хватает на отрисовку, а палитре нужен весь набор: свойства у видов
   * элементов разные, поэтому обходим модель, а не перечисляем их руками.
   */
  private static java.util.Map<String, String> writtenProperties(Object item) {
    java.util.Map<String, String> out = new java.util.LinkedHashMap<>();
    for (Class<?> c = item.getClass(); c != null && !c.equals(Object.class); c = c.getSuperclass()) {
      for (java.lang.reflect.Field field : c.getDeclaredFields()) {
        jakarta.xml.bind.annotation.XmlElement element =
          field.getAnnotation(jakarta.xml.bind.annotation.XmlElement.class);
        jakarta.xml.bind.annotation.XmlAttribute attribute =
          field.getAnnotation(jakarta.xml.bind.annotation.XmlAttribute.class);
        if (element == null && attribute == null) {
          continue;
        }
        String name = element != null ? element.name() : attribute.name();
        if (out.containsKey(name)) {
          continue;
        }
        Object value = fieldValue(field, item);
        String text = scalarText(value);
        if (text != null) {
          out.put(name, text);
        }
      }
    }
    return out;
  }

  private static Object fieldValue(java.lang.reflect.Field field, Object item) {
    try {
      field.setAccessible(true);
      return field.get(item);
    } catch (ReflectiveOperationException | RuntimeException e) {
      return null;
    }
  }

  /** Текст скалярного значения; составные узлы палитре пока не нужны, поэтому их пропускаем. */
  private static String scalarText(Object value) {
    if (value == null) {
      return null;
    }
    if (value instanceof String s) {
      return s.isEmpty() ? null : s;
    }
    if (value instanceof Boolean || value instanceof Enum<?> || value instanceof BigDecimal) {
      return enumText(value) == null ? null : (value instanceof BigDecimal d ? decimalText(d) : enumText(value));
    }
    // Заголовок подсказки и надписи - наследник строки на языках, поэтому смотрим всю цепочку.
    for (Class<?> c = value.getClass(); c != null; c = c.getSuperclass()) {
      if ("LocalStringType".equals(c.getSimpleName())) {
        String ru = LocalStringSync.firstRu(value);
        return ru == null || ru.isEmpty() ? null : ru;
      }
    }
    return null;
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

  /**
   * Представление значения списка выбора: у переключателя и флажка платформа рисует рядом с ним
   * именно его, а не заголовок элемента.
   */
  private static String choicePresentation(Object item) {
    Object list = JaxbReflect.getOptional(item, "getChoiceList");
    Object entry = list == null ? null : JaxbReflect.getOptional(list, "getItem");
    if (entry == null) {
      return null;
    }
    Object value = JaxbReflect.getOptional(entry, "getValue");
    String ru = value == null ? null : LocalStringSync.firstRu(JaxbReflect.getOptional(value, "getPresentation"));
    if (ru != null && !ru.isEmpty()) {
      return ru;
    }
    String plain = JaxbReflect.getStringOptional(entry, "getPresentation");
    return plain == null || plain.isEmpty() ? null : plain;
  }

  /** Свойство у одних видов элементов булево, у других - «да/нет/авто», отсюда два имени метода. */
  private static String enumOrBoolText(Object item, String property) {
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
    return value instanceof Enum<?> e ? FormEnumText.of(e) : String.valueOf(value);
  }
}
