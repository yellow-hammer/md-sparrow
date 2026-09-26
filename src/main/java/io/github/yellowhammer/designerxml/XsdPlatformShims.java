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
package io.github.yellowhammer.designerxml;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import javax.xml.XMLConstants;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Подмены XSD в памяти, приводящие XDTO-схемы 1С к файлам, которые пишет платформа.
 * <p>
 * Схемы {@code namespace-forest} — выгрузка {@code ФабрикаXDTO.ЭкспортСхемыXML} самой платформы. Они описывают
 * модель XDTO, а не файлы выгрузки: платформа не пишет свойства со значением по умолчанию и свойства,
 * вынесенные в отдельные файлы, у заимствованного объекта расширения пишет только часть свойств и т.п.
 * Каждая подмена ослабляет схему ровно на такое отличие модели от файла; неизвестные элементы, неверные значения
 * и элементы не на своём месте по-прежнему считаются ошибкой. Перечень — в {@link XmlValidator}.
 */
final class XsdPlatformShims {

  static final String MD_CLASSES_XSD = "v8.1c.ru-8.3-MDClasses.xsd";
  static final String XCF_READABLE_XSD = "v8.1c.ru-8.3-xcf-readable.xsd";
  static final String XCF_ENUMS_XSD = "v8.1c.ru-8.3-xcf-enums.xsd";
  static final String MANAGED_CORE_XSD = "v8.1c.ru-8.2-managed-application-core.xsd";

  /** Значения режима совместимости в файле: {@code DontUse} или {@code Version} с номером через {@code _}. */
  static final String COMPATIBILITY_MODE_PATTERN = "DontUse|Version\\d+(_\\d+)+";

  /**
   * Функциональности мобильного приложения, которых нет в перечислении XDTO ни одного формата, с форматом, с которого
   * их пишет платформа: эталоны {@code cf-bare-objects}, те же границы у записи конфигурации
   * ({@code cf/ConfigurationFormatRules}).
   */
  static final List<MobileFunctionality> MOBILE_FUNCTIONALITIES_MISSING_IN_XDTO = List.of(
    new MobileFunctionality("SpeechToText", SchemaVersion.V2_17),
    new MobileFunctionality("TextToSpeech", SchemaVersion.V2_18));

  private static final String XS = XMLConstants.W3C_XML_SCHEMA_NS_URI;
  private static final String NS_DATA_CORE = "http://v8.1c.ru/8.1/data/core";

  private XsdPlatformShims() {
  }

  /**
   * Функциональность мобильного приложения и формат, с которого её пишет платформа.
   *
   * @param name значение перечисления {@code MobileApplicationFunctionalities}
   * @param since первый формат, в выгрузке которого встречается значение
   */
  record MobileFunctionality(String name, SchemaVersion since) {
  }

  /**
   * Применяет подмены к разобранной схеме по имени её файла; схемы без подмен не меняются.
   *
   * @param xsdFileName имя файла XSD в каталоге версии
   * @param xsd разобранная схема (с учётом пространств имён), меняется на месте
   * @param version формат, которому принадлежит схема
   */
  static void apply(String xsdFileName, Document xsd, SchemaVersion version) {
    switch (xsdFileName) {
      case MD_CLASSES_XSD -> {
        unorderPropertiesTypes(xsd);
        placeLateConfigurationChildren(xsd);
        makeConfigurationFormatVersionOptional(xsd);
        makeUntypedElementsNillable(xsd);
      }
      case XCF_READABLE_XSD -> {
        topLevel(xsd, "complexType", "StandardAttributeDescription").ifPresent(XsdPlatformShims::unorder);
        topLevel(xsd, "complexType", "Picture").ifPresent(XsdPlatformShims::unorder);
        dropReferenceTypePattern(xsd);
        retypeDataPathAsString(xsd);
        deriveExtendedPropertyFromTypeDescription(xsd);
        interleaveInternalInfo(xsd);
        makeTypeLinkDataPathOptional(xsd);
        makeValueListItemsRepeatable(xsd);
        makeUntypedElementsNillable(xsd);
      }
      case XCF_ENUMS_XSD -> replaceCompatibilityModeEnumeration(xsd);
      case MANAGED_CORE_XSD -> {
        addMobileFunctionalities(xsd, version);
        makeRequiredPermissionDescriptionOptional(xsd);
      }
      default -> {
      }
    }
  }

  /**
   * Типы свойств объектов ({@code *Properties}): каждое свойство не больше одного раза, в любом порядке, любое
   * может отсутствовать ({@code xs:all}).
   * <ul>
   *   <li>Платформа не пишет {@code ObjectBelonging} со значением по умолчанию, модули, справку, состав, права
   *       и другие свойства из отдельных файлов, а у заимствованного объекта расширения — почти ничего, кроме
   *       {@code Name}.</li>
   *   <li>Порядок свойств в выводе платформы не совпадает с порядком XDTO: новые свойства XDTO дописывает в конец
   *       типа ({@code UseOneCommand} подсистемы, {@code UpdateDataHistoryImmediatelyAfterWrite} плана счетов),
   *       а {@code ObjectBelonging} заимствованного объекта платформа пишет перед {@code Name}.</li>
   *   <li>{@code ConfigurationProperties} и {@code CommonModuleProperties} XDTO описывает одиночным
   *       {@code xs:choice} (ровно одно свойство), а платформа пишет все свойства подряд.</li>
   * </ul>
   *
   * @param xsd схема {@code MDClasses}
   */
  static void unorderPropertiesTypes(Document xsd) {
    for (Element type : topLevel(xsd, "complexType")) {
      if (type.getAttribute("name").endsWith("Properties")) {
        unorder(type);
      }
    }
  }

  /**
   * Заменяет модель содержимого типа ({@code xs:sequence} или одиночный {@code xs:choice} из элементов) на
   * {@code xs:all}, где каждый элемент необязателен. Кроме свойств объектов так же устроены
   * {@code StandardAttributeDescription} и {@code Picture} из {@code xcf/readable}: в XDTO одиночный
   * {@code xs:choice}, а платформа пишет несколько свойств (у картинки — {@code Ref} и {@code LoadTransparent}).
   *
   * @param type глобальный {@code complexType}
   */
  static void unorder(Element type) {
    Optional<Element> model = firstChild(type, "sequence", "choice");
    if (model.isEmpty()) {
      return;
    }
    Element group = model.get();
    Element all = type.getOwnerDocument().createElementNS(XS, qualified(group, "all"));
    all.setAttribute("minOccurs", "0");
    for (Element element : children(group, "element")) {
      element.setAttribute("minOccurs", "0");
      element.removeAttribute("maxOccurs");
      all.appendChild(element);
    }
    type.replaceChild(all, group);
  }

  /**
   * {@code ConfigurationChildObjects}: виды, добавленные в XDTO позже, стоят в конце последовательности, а платформа
   * пишет их на своих местах — {@code WebSocketClient} после {@code WSReference}, {@code Bot} и {@code PaletteColor}
   * после {@code DefinedType}.
   *
   * @param xsd схема {@code MDClasses}
   */
  static void placeLateConfigurationChildren(Document xsd) {
    topLevel(xsd, "complexType", "ConfigurationChildObjects")
      .flatMap(type -> firstChild(type, "sequence"))
      .ifPresent(sequence -> {
        moveAfter(sequence, "WebSocketClient", "WSReference");
        moveAfter(sequence, "Bot", "DefinedType");
        moveAfter(sequence, "PaletteColor", "Bot");
      });
  }

  private static void moveAfter(Element sequence, String name, String anchorName) {
    Optional<Element> element = childElement(sequence, name);
    Optional<Element> anchor = childElement(sequence, anchorName);
    if (element.isPresent() && anchor.isPresent()) {
      sequence.insertBefore(element.get(), anchor.get().getNextSibling());
    }
  }

  /**
   * {@code Configuration/@formatVersion} обязателен в XDTO, но платформа его не пишет.
   *
   * @param xsd схема {@code MDClasses}
   */
  static void makeConfigurationFormatVersionOptional(Document xsd) {
    topLevel(xsd, "complexType", "Configuration").ifPresent(type -> {
      for (Element attribute : descendants(type, "attribute")) {
        if ("formatVersion".equals(attribute.getAttribute("name"))) {
          attribute.setAttribute("use", "optional");
        }
      }
    });
  }

  /**
   * Элементы без типа ({@code MinValue}, {@code MaxValue}, {@code FillValue} и т.п.) хранят значение любого типа;
   * «Неопределено» платформа пишет как {@code xsi:nil="true"}, а XDTO не объявляет их {@code nillable}.
   *
   * @param xsd схема, меняется на месте
   */
  static void makeUntypedElementsNillable(Document xsd) {
    for (Element element : descendants(xsd.getDocumentElement(), "element")) {
      boolean untyped = !element.hasAttribute("type") && !element.hasAttribute("ref")
        && firstChild(element, "complexType", "simpleType").isEmpty();
      if (untyped) {
        element.setAttribute("nillable", "true");
      }
    }
  }

  /**
   * Шаблон {@code ([A-Za-z0-9_])*} у {@code ReferenceType} (база {@code MDObjectRef}) не пропускает ни точку,
   * ни кириллицу, то есть ни одну ссылку вида {@code Role.ПолныеПрава}.
   *
   * @param xsd схема {@code xcf/readable}
   */
  static void dropReferenceTypePattern(Document xsd) {
    topLevel(xsd, "simpleType", "ReferenceType")
      .flatMap(type -> firstChild(type, "restriction"))
      .ifPresent(restriction -> {
        for (Element pattern : children(restriction, "pattern")) {
          restriction.removeChild(pattern);
        }
      });
  }

  /**
   * Путь к данным ({@code DataPath}) — строка: после снятия шаблона {@code ReferenceType} тип ничего не проверяет,
   * а в связях параметров выбора стандартных реквизитов платформа пишет {@code xsi:type="xs:string"}, который
   * от {@code DataPath} не выводится.
   *
   * @param xsd схема {@code xcf/readable}
   */
  static void retypeDataPathAsString(Document xsd) {
    Element schema = xsd.getDocumentElement();
    String own = schema.lookupPrefix(schema.getAttribute("targetNamespace"));
    if (own == null) {
      return;
    }
    for (Element element : descendants(schema, "element")) {
      if ((own + ":DataPath").equals(element.getAttribute("type"))) {
        element.setAttribute("type", qualified(schema, "string"));
      }
    }
  }

  /**
   * Расширяемое свойство заимствованного объекта платформа пишет на месте самого свойства с
   * {@code xsi:type="xr:ExtendedProperty"} ({@code Type} определяемого типа в расширении). В XDTO этот тип ни от чего
   * не выводится, поэтому такое значение не принимает ни одно свойство. Здесь он выводится расширением из
   * {@code v8:TypeDescription} (все элементы которого необязательны): так его принимают свойства типа
   * {@code TypeDescription}, и только они — других расширяемых свойств в файле объекта выгрузки не встречалось.
   *
   * @param xsd схема {@code xcf/readable}
   */
  static void deriveExtendedPropertyFromTypeDescription(Document xsd) {
    Element schema = xsd.getDocumentElement();
    String core = schema.lookupPrefix(NS_DATA_CORE);
    Optional<Element> type = topLevel(xsd, "complexType", "ExtendedProperty");
    Optional<Element> sequence = type.flatMap(t -> firstChild(t, "sequence"));
    if (core == null || sequence.isEmpty()) {
      return;
    }
    Element content = xsd.createElementNS(XS, qualified(schema, "complexContent"));
    Element extension = xsd.createElementNS(XS, qualified(schema, "extension"));
    extension.setAttribute("base", core + ":TypeDescription");
    type.get().replaceChild(content, sequence.get());
    content.appendChild(extension);
    extension.appendChild(sequence.get());
  }

  /**
   * {@code InternalInfo}: у внешних обработок и отчётов платформа пишет {@code ContainedObject} перед
   * {@code GeneratedType}. Эти два элемента идут вперемешку, {@code ThisNode} и {@code PropertyState} остаются
   * на своих местах.
   *
   * @param xsd схема {@code xcf/readable}
   */
  static void interleaveInternalInfo(Document xsd) {
    Optional<Element> sequence = topLevel(xsd, "complexType", "InternalInfo")
      .flatMap(type -> firstChild(type, "sequence"));
    if (sequence.isEmpty()) {
      return;
    }
    Element group = sequence.get();
    Optional<Element> generated = childElement(group, "GeneratedType");
    Optional<Element> contained = childElement(group, "ContainedObject");
    if (generated.isEmpty() || contained.isEmpty()) {
      return;
    }
    Element choice = xsd.createElementNS(XS, qualified(group, "choice"));
    choice.setAttribute("minOccurs", "0");
    choice.setAttribute("maxOccurs", "unbounded");
    group.insertBefore(choice, generated.get());
    for (Element element : List.of(generated.get(), contained.get())) {
      element.removeAttribute("minOccurs");
      element.removeAttribute("maxOccurs");
      choice.appendChild(element);
    }
  }

  /**
   * {@code TypeLink}: пустую связь по типу платформа пишет как {@code <LinkByType/>}, без {@code DataPath}.
   *
   * @param xsd схема {@code xcf/readable}
   */
  static void makeTypeLinkDataPathOptional(Document xsd) {
    topLevel(xsd, "complexType", "TypeLink")
      .flatMap(type -> firstChild(type, "sequence"))
      .flatMap(sequence -> childElement(sequence, "DataPath"))
      .ifPresent(element -> element.setAttribute("minOccurs", "0"));
  }

  /**
   * {@code ValueList} (список значений: {@code XDTOPackages} веб-сервиса, {@code Headers} клиента WebSocket) в XDTO
   * содержит ровно один {@code Item}, а платформа пишет и пустой список, и несколько элементов.
   *
   * @param xsd схема {@code xcf/readable}
   */
  static void makeValueListItemsRepeatable(Document xsd) {
    topLevel(xsd, "complexType", "ValueList")
      .flatMap(type -> firstChild(type, "sequence"))
      .flatMap(sequence -> childElement(sequence, "Item"))
      .ifPresent(element -> {
        element.setAttribute("minOccurs", "0");
        element.setAttribute("maxOccurs", "unbounded");
      });
  }

  /**
   * {@code CompatibilityMode}: перечисление XDTO — не перечень значений файла. Во всех форматах в нём
   * {@code DontUse} и режимы до {@code Version8_3_12}, а в файле встречаются и более новые режимы
   * ({@code Version8_3_27}), и номер версии самой платформы ({@code Version8_5_1}). Режима совместимости
   * 8.5 нет, потому что нет ничего, что поддерживает только 8.5, — так и должно быть. Поэтому значение
   * проверяется шаблоном {@link #COMPATIBILITY_MODE_PATTERN}.
   *
   * @param xsd схема {@code xcf/enums}
   */
  static void replaceCompatibilityModeEnumeration(Document xsd) {
    topLevel(xsd, "simpleType", "CompatibilityMode")
      .flatMap(type -> firstChild(type, "restriction"))
      .ifPresent(restriction -> {
        List<Element> values = children(restriction, "enumeration");
        if (values.isEmpty()) {
          return;
        }
        for (Element value : values) {
          restriction.removeChild(value);
        }
        Element pattern = xsd.createElementNS(XS, qualified(restriction, "pattern"));
        pattern.setAttribute("value", COMPATIBILITY_MODE_PATTERN);
        restriction.appendChild(pattern);
      });
  }

  /**
   * {@code MobileApplicationFunctionalities}: платформа пишет возможности, которых нет в перечислении XDTO
   * ({@link #MOBILE_FUNCTIONALITIES_MISSING_IN_XDTO}). Значение добавляется только в схемы форматов, которые его
   * пишут: платформа более раннего формата такой файл не загружает.
   *
   * @param xsd схема {@code managed-application/core}
   * @param version формат схемы
   */
  static void addMobileFunctionalities(Document xsd, SchemaVersion version) {
    topLevel(xsd, "simpleType", "MobileApplicationFunctionalities")
      .flatMap(type -> firstChild(type, "restriction"))
      .ifPresent(restriction -> {
        List<String> known = children(restriction, "enumeration").stream()
          .map(value -> value.getAttribute("value"))
          .toList();
        for (MobileFunctionality functionality : MOBILE_FUNCTIONALITIES_MISSING_IN_XDTO) {
          if (version.compareTo(functionality.since()) >= 0 && !known.contains(functionality.name())) {
            Element value = xsd.createElementNS(XS, qualified(restriction, "enumeration"));
            value.setAttribute("value", functionality.name());
            restriction.appendChild(value);
          }
        }
      });
  }

  /**
   * {@code RequiredPermission}: формат 2.10 пишет {@code app:permission} без {@code app:description}.
   *
   * @param xsd схема {@code managed-application/core}
   */
  static void makeRequiredPermissionDescriptionOptional(Document xsd) {
    topLevel(xsd, "complexType", "RequiredPermission")
      .flatMap(type -> firstChild(type, "sequence"))
      .flatMap(sequence -> childElement(sequence, "description"))
      .ifPresent(element -> element.setAttribute("minOccurs", "0"));
  }

  private static String qualified(Element sibling, String localName) {
    String prefix = sibling.getPrefix();
    return prefix == null ? localName : prefix + ":" + localName;
  }

  private static List<Element> topLevel(Document xsd, String kind) {
    return children(xsd.getDocumentElement(), kind);
  }

  private static Optional<Element> topLevel(Document xsd, String kind, String name) {
    return topLevel(xsd, kind).stream()
      .filter(type -> name.equals(type.getAttribute("name")))
      .findFirst();
  }

  /** Объявление {@code xs:element name="…"} среди прямых потомков группы. */
  private static Optional<Element> childElement(Element group, String name) {
    return children(group, "element").stream()
      .filter(element -> name.equals(element.getAttribute("name")))
      .findFirst();
  }

  private static Optional<Element> firstChild(Element parent, String... kinds) {
    List<String> wanted = List.of(kinds);
    for (Node n = parent.getFirstChild(); n != null; n = n.getNextSibling()) {
      if (n instanceof Element e && XS.equals(e.getNamespaceURI()) && wanted.contains(e.getLocalName())) {
        return Optional.of(e);
      }
    }
    return Optional.empty();
  }

  private static List<Element> children(Element parent, String kind) {
    List<Element> out = new ArrayList<>();
    for (Node n = parent.getFirstChild(); n != null; n = n.getNextSibling()) {
      if (n instanceof Element e && XS.equals(e.getNamespaceURI()) && kind.equals(e.getLocalName())) {
        out.add(e);
      }
    }
    return out;
  }

  private static List<Element> descendants(Element parent, String kind) {
    List<Element> out = new ArrayList<>();
    var nodes = parent.getElementsByTagNameNS(XS, kind);
    for (int i = 0; i < nodes.getLength(); i++) {
      out.add((Element) nodes.item(i));
    }
    return out;
  }
}
