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

import jakarta.xml.bind.JAXBElement;
import jakarta.xml.bind.JAXBException;

import javax.xml.transform.stream.StreamSource;

import java.io.IOException;
import java.io.StringReader;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Запись типа значения ({@code valueType}) поля набора, вычисляемого поля и параметра схемы
 * компоновки, а у параметра — и его значения ({@code value}).
 *
 * <p>Тип пишется байтами платформы через {@link MdTypeDescriptionSerial#typeElement}: объявления,
 * которые уже стоят в корне схемы, снимаются, а ссылочный тип получает префикс {@code d{N}p1}, где N
 * равно глубине элемента {@code v8:Type}/{@code v8:TypeSet} от корня схемы (так пишет платформа).
 * {@link MdTypeDescriptionBridge#apply} не подходит: он не трогает {@code TypeSet}.
 *
 * <p>Правятся только типы из словаря {@link DcsValueTypes}. Тип из payload сравнивается с типом
 * в файле по смыслу: эхо ответа {@code cf-dcs-info} файл не меняет, поэтому формы вне словаря
 * остаются байтами исходника.
 */
final class DcsValueTypeEdit {

  private static final String CURRENT_CONFIG = "http://v8.1c.ru/8.1/data/enterprise/current-config";

  /** Объявление и префикс ссылочного типа на выходе {@link MdTypeDescriptionSerial#typeElement}. */
  private static final String CONFIG_DECLARATION = "xmlns:cfg=\"" + CURRENT_CONFIG + "\">cfg:";

  /**
   * Ключи {@code valueType} в payload, как в ответе {@code cf-dcs-info}. {@code editable} и
   * {@code readOnlyReason} приходят с эхом и пропускаются.
   */
  private static final List<String> PAYLOAD_KEYS = List.of(
    "types", "typeSets", "typeIds", "definedTypes", "stringQualifiers", "numberQualifiers",
    "dateQualifiers", "binaryDataQualifiers", "editable", "readOnlyReason");

  private static final Map<String, String> ALLOWED_LENGTH = enumForms("VARIABLE", "FIXED");

  private static final Map<String, String> ALLOWED_SIGN = enumForms("ANY", "NONNEGATIVE");

  private static final Map<String, String> DATE_FRACTIONS = enumForms("DATE", "TIME", "DATE_TIME");

  /** Объявление пространства имён с префиксом в открывающем теге. */
  private static final Pattern NAMESPACE = Pattern.compile("\\sxmlns:([\\w.-]+)=\"([^\"]*)\"");

  /** Все объявления открывающего тега корня, включая пространство по умолчанию. */
  private static final Pattern ANY_NAMESPACE = Pattern.compile("\\sxmlns(?::[\\w.-]+)?=\"[^\"]*\"");

  private static final Pattern XSI_TYPE = Pattern.compile("\\bxsi:type=\"([^\"]+)\"");

  private static final Pattern XSI_NIL = Pattern.compile("\\bxsi:nil=\"(?:true|1)\"");

  private static final String VALUE_LIST = "v8:ValueListType";

  /** Значение элемента списка значений: {@code <v8:value xsi:type="…">текст</v8:value>} либо пустой тег. */
  private static final Pattern LIST_ITEM_VALUE =
    Pattern.compile("<v8:value\\b([^>]*?)(?:/>|>([^<]*)</v8:value>)");

  private static final Pattern UUID_TEXT = Pattern.compile(
    "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");

  /** Значения, которые сверяются с типом параметра прямо по {@code xsi:type}. */
  private static final Set<String> TYPED_VALUES = Set.of(
    "xs:string", "xs:decimal", "xs:boolean", "xs:dateTime", "v8:StandardPeriod", "v8:StandardBeginningDate",
    "v8:UUID");

  private static final String CHARACTERISTIC_PREFIX = "cfg:Characteristic.";

  private static final String JOURNAL_PREFIX = "cfg:DocumentJournalRef";

  /** Наибольшая разрядность числа, которую хранит платформа (65 обрезается до 64). */
  private static final int MAX_DIGITS = 64;

  private DcsValueTypeEdit() {
  }

  /**
   * Правка типа одного владельца.
   *
   * @param fresh тип после правки (при эхе — тип в файле); пустой — типа не будет
   * @param changed тип меняется по смыслу; иначе элемент не трогается
   * @param xml новый элемент одной строкой; пусто — снять элемент
   */
  record Edit(MdTypeDescriptionDto fresh, boolean changed, String xml) {
  }

  /**
   * Правка типа владельца по payload.
   *
   * @param children прямые потомки владельца в {@code text}
   * @param payload {@code valueType} из payload: форма {@code cf-dcs-info} либо {@code null}
   * @param ownerDepth глубина владельца от корня схемы (корень — 1): у параметра и вычисляемого
   *   поля — 2, у поля набора верхнего уровня — 3
   * @throws IllegalArgumentException если тип вне словаря, не по форме или не найден в выгрузке
   */
  static Edit edit(Path templateXml, SchemaVersion version, String text, List<XmlLines.Node> children,
    Object payload, int ownerDepth) throws IOException, JAXBException {
    return editOver(templateXml, version, text, current(text, children, version), payload, ownerDepth);
  }

  /** Тип нового владельца: в файле его ещё нет, и все ссылки в нём новые. */
  static Edit added(Path templateXml, SchemaVersion version, String text, Object payload, int ownerDepth)
    throws IOException, JAXBException {
    return editOver(templateXml, version, text, null, payload, ownerDepth);
  }

  private static Edit editOver(Path templateXml, SchemaVersion version, String text, MdTypeDescriptionDto current,
    Object payload, int ownerDepth) throws IOException, JAXBException {
    DcsValueTypes.Context context = DcsValueTypes.Context.of(templateXml, version);
    MdTypeDescriptionDto fresh = parse(payload, context);
    if (same(fresh, current)) {
      return new Edit(current, false, null);
    }
    if (!isEmpty(fresh)) {
      check(fresh, current, context, version);
    }
    return new Edit(fresh, true, xml(text, fresh, ownerDepth));
  }

  /**
   * Элемент {@code valueType} одной строкой, без отступов.
   *
   * @param text файл схемы: объявления его корня с элемента снимаются
   * @param ownerDepth глубина владельца от корня схемы (корень — 1)
   * @return пусто, если типа нет: пустого {@code <valueType/>} платформа не пишет
   */
  static String xml(String text, MdTypeDescriptionDto dto, int ownerDepth) {
    if (isEmpty(dto)) {
      return "";
    }
    String element = MdTypeDescriptionSerial.typeElement("valueType", dto);
    String prefix = "d" + (ownerDepth + 2) + "p1";
    element = element.replace(CONFIG_DECLARATION, "xmlns:" + prefix + "=\"" + CURRENT_CONFIG + "\">" + prefix + ":");
    Matcher declared = NAMESPACE.matcher(rootTag(text));
    while (declared.find()) {
      element = element.replace(" xmlns:" + declared.group(1) + "=\"" + declared.group(2) + "\"", "");
    }
    return element;
  }

  /**
   * Тип в файле той же формой, что отдаёт {@code cf-dcs-info}.
   *
   * @return {@code null}, если у владельца нет {@code valueType}
   */
  static MdTypeDescriptionDto current(String text, List<XmlLines.Node> children, SchemaVersion version) {
    for (XmlLines.Node child : children) {
      if ("valueType".equals(child.name())) {
        return read(text, child.start(), child.end(), version);
      }
    }
    return null;
  }

  /**
   * Читает элемент {@code valueType} моделью формата в той форме, что отдаёт {@code cf-dcs-info}.
   *
   * <p>Объявления корня переносятся на элемент: типы ({@code xs:string}) записаны как QName, и без
   * объявлений префиксы не разрешаются.
   *
   * @param start смещение {@code <} элемента
   * @param end смещение за концом элемента
   */
  static MdTypeDescriptionDto read(String text, int start, int end, SchemaVersion version) {
    String element = text.substring(start, end);
    int nameEnd = 1;
    while (nameEnd < element.length() && !Character.isWhitespace(element.charAt(nameEnd))
      && element.charAt(nameEnd) != '>' && element.charAt(nameEnd) != '/') {
      nameEnd++;
    }
    StringBuilder declarations = new StringBuilder();
    Matcher declared = ANY_NAMESPACE.matcher(rootTag(text));
    while (declared.find()) {
      declarations.append(declared.group());
    }
    String fragment = element.substring(0, nameEnd) + declarations + element.substring(nameEnd);
    try {
      Class<?> typeClass = Class.forName("io.github.yellowhammer.designerxml.jaxb."
        + version.name().toLowerCase(Locale.ROOT) + ".v8_1_data_core.TypeDescription");
      JAXBElement<?> parsed = version.jaxbContext().createUnmarshaller()
        .unmarshal(new StreamSource(new StringReader(fragment)), typeClass);
      return MdTypeDescriptionBridge.read(parsed.getValue());
    } catch (ClassNotFoundException e) {
      throw new IllegalStateException("Модель типа значения не собрана для " + version, e);
    } catch (JAXBException e) {
      throw new IllegalStateException("тип значения в файле не читается моделью: " + e.getMessage(), e);
    }
  }

  /** Одинаковы ли типы по смыслу: те же элементы в том же порядке и те же квалификаторы. */
  static boolean same(MdTypeDescriptionDto a, MdTypeDescriptionDto b) {
    if (isEmpty(a) || isEmpty(b)) {
      return isEmpty(a) && isEmpty(b);
    }
    if (!a.types.equals(b.types) || !a.typeSets.equals(b.typeSets)) {
      return false;
    }
    List<String> idsA = ids(a);
    List<String> idsB = ids(b);
    if (idsA.size() != idsB.size()) {
      return false;
    }
    for (int i = 0; i < idsA.size(); i++) {
      if (!idsA.get(i).trim().equalsIgnoreCase(idsB.get(i).trim())) {
        return false;
      }
    }
    return sameString(a.stringQualifiers, b.stringQualifiers)
      && sameNumber(a.numberQualifiers, b.numberQualifiers)
      && sameDate(a.dateQualifiers, b.dateQualifiers)
      && sameBinary(a.binaryDataQualifiers, b.binaryDataQualifiers);
  }

  /** Тип без единого элемента: такой тип снимает {@code valueType} целиком. */
  static boolean isEmpty(MdTypeDescriptionDto dto) {
    return dto == null || dto.types.isEmpty() && dto.typeSets.isEmpty() && ids(dto).isEmpty();
  }

  // ------------------------------------------------------------------ payload

  /**
   * Тип из payload. Числа квалификаторов принимаются строкой или числом JSON, перечисления: именем
   * константы ({@code VARIABLE}) или текстом файла ({@code Variable}).
   *
   * <p>Имена {@code definedTypes} переводятся в UUID по выгрузке над схемой. Если заданы и
   * {@code typeIds}, списки должны совпасть: эхо {@code cf-dcs-info} несёт оба ключа.
   */
  private static MdTypeDescriptionDto parse(Object raw, DcsValueTypes.Context context) {
    MdTypeDescriptionDto dto = new MdTypeDescriptionDto();
    dto.typeIds = new ArrayList<>();
    if (raw == null) {
      return dto;
    }
    if (!(raw instanceof Map<?, ?> map)) {
      throw new IllegalArgumentException(
        "Значение valueType должно быть объектом {types, typeSets, typeIds, …} либо null (снять тип).");
    }
    for (Object key : map.keySet()) {
      if (!PAYLOAD_KEYS.contains(String.valueOf(key))) {
        throw new IllegalArgumentException("Неизвестное свойство типа значения: " + key);
      }
    }
    dto.types.addAll(strings(map.get("types"), "types"));
    dto.typeSets.addAll(strings(map.get("typeSets"), "typeSets"));
    dto.typeIds.addAll(strings(map.get("typeIds"), "typeIds"));
    List<String> names = strings(map.get("definedTypes"), "definedTypes");
    if (!names.isEmpty()) {
      List<String> resolved = definedTypeIds(names, context);
      if (dto.typeIds.isEmpty()) {
        dto.typeIds.addAll(resolved);
      } else if (!sameIds(dto.typeIds, resolved)) {
        throw new IllegalArgumentException("typeIds " + dto.typeIds + " не совпадают с definedTypes " + names
          + ": задайте определяемый тип чем-то одним.");
      }
    }
    dto.stringQualifiers = stringQualifiers(map.get("stringQualifiers"));
    dto.numberQualifiers = numberQualifiers(map.get("numberQualifiers"));
    dto.dateQualifiers = dateQualifiers(map.get("dateQualifiers"));
    dto.binaryDataQualifiers = binaryQualifiers(map.get("binaryDataQualifiers"));
    return dto;
  }

  /** UUID определяемых типов по именам из выгрузки над схемой. */
  private static List<String> definedTypeIds(List<String> names, DcsValueTypes.Context context) {
    if (context.configuration() == null) {
      throw new IllegalArgumentException("Определяемый тип по имени задаётся только у макета в выгрузке "
        + "конфигурации: вне её имя не с чем сопоставить.");
    }
    Map<String, String> byName = DcsValueTypes.definedTypes(context.configuration());
    List<String> out = new ArrayList<>();
    for (String name : names) {
      String typeId = byName.get(name);
      if (typeId == null) {
        // Имена объектов 1С не зависят от регистра
        for (Map.Entry<String, String> entry : byName.entrySet()) {
          if (entry.getKey().equalsIgnoreCase(name)) {
            typeId = entry.getValue();
          }
        }
      }
      if (typeId == null) {
        throw new IllegalArgumentException("Определяемый тип «" + name + "» не найден в выгрузке конфигурации.");
      }
      out.add(typeId);
    }
    return out;
  }

  private static List<String> strings(Object raw, String key) {
    if (raw == null) {
      return List.of();
    }
    if (!(raw instanceof List<?> list)) {
      throw new IllegalArgumentException("Значение valueType." + key + " должно быть списком строк.");
    }
    List<String> out = new ArrayList<>();
    for (Object item : list) {
      if (!(item instanceof String text) || text.isBlank()) {
        throw new IllegalArgumentException("Элемент valueType." + key + " должен быть непустой строкой.");
      }
      out.add(text.trim());
    }
    return out;
  }

  private static MdTypeDescriptionDto.MdStringQualifiersDto stringQualifiers(Object raw) {
    Map<?, ?> map = qualifierMap(raw, "stringQualifiers", List.of("length", "allowedLength"));
    if (map == null) {
      return null;
    }
    MdTypeDescriptionDto.MdStringQualifiersDto q = new MdTypeDescriptionDto.MdStringQualifiersDto();
    q.length = count(map.get("length"), "stringQualifiers.length");
    q.allowedLength = constant(map.get("allowedLength"), ALLOWED_LENGTH, "stringQualifiers.allowedLength");
    return q;
  }

  private static MdTypeDescriptionDto.MdNumberQualifiersDto numberQualifiers(Object raw) {
    Map<?, ?> map = qualifierMap(raw, "numberQualifiers", List.of("digits", "fractionDigits", "allowedSign"));
    if (map == null) {
      return null;
    }
    MdTypeDescriptionDto.MdNumberQualifiersDto q = new MdTypeDescriptionDto.MdNumberQualifiersDto();
    q.digits = count(map.get("digits"), "numberQualifiers.digits");
    q.fractionDigits = count(map.get("fractionDigits"), "numberQualifiers.fractionDigits");
    q.allowedSign = constant(map.get("allowedSign"), ALLOWED_SIGN, "numberQualifiers.allowedSign");
    return q;
  }

  private static MdTypeDescriptionDto.MdDateQualifiersDto dateQualifiers(Object raw) {
    Map<?, ?> map = qualifierMap(raw, "dateQualifiers", List.of("dateFractions"));
    if (map == null) {
      return null;
    }
    MdTypeDescriptionDto.MdDateQualifiersDto q = new MdTypeDescriptionDto.MdDateQualifiersDto();
    q.dateFractions = constant(map.get("dateFractions"), DATE_FRACTIONS, "dateQualifiers.dateFractions");
    return q;
  }

  private static MdTypeDescriptionDto.MdBinaryDataQualifiersDto binaryQualifiers(Object raw) {
    Map<?, ?> map = qualifierMap(raw, "binaryDataQualifiers", List.of("length", "allowedLength"));
    if (map == null) {
      return null;
    }
    MdTypeDescriptionDto.MdBinaryDataQualifiersDto q = new MdTypeDescriptionDto.MdBinaryDataQualifiersDto();
    q.length = count(map.get("length"), "binaryDataQualifiers.length");
    q.allowedLength = constant(map.get("allowedLength"), ALLOWED_LENGTH, "binaryDataQualifiers.allowedLength");
    return q;
  }

  private static Map<?, ?> qualifierMap(Object raw, String key, List<String> allowed) {
    if (raw == null) {
      return null;
    }
    if (!(raw instanceof Map<?, ?> map)) {
      throw new IllegalArgumentException("Значение valueType." + key + " должно быть объектом "
        + String.join(", ", allowed) + ".");
    }
    for (Object name : map.keySet()) {
      if (!allowed.contains(String.valueOf(name))) {
        throw new IllegalArgumentException("Неизвестное свойство valueType." + key + ": " + name);
      }
    }
    return map;
  }

  /** Целое неотрицательное число квалификатора текстом без хвостовых нулей; {@code null} — не задано. */
  private static String count(Object raw, String what) {
    if (raw == null) {
      return null;
    }
    BigDecimal number;
    try {
      number = new BigDecimal(raw instanceof Number ? raw.toString() : String.valueOf(raw).trim());
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException(what + " должно быть целым неотрицательным числом, а не " + raw + ".");
    }
    BigDecimal whole = number.stripTrailingZeros();
    if (whole.signum() < 0 || whole.scale() > 0) {
      throw new IllegalArgumentException(what + " должно быть целым неотрицательным числом, а не " + raw + ".");
    }
    return whole.toPlainString();
  }

  /** Имя константы перечисления квалификатора; {@code null} — не задано. */
  private static String constant(Object raw, Map<String, String> forms, String what) {
    if (raw == null) {
      return null;
    }
    String constant = forms.get(String.valueOf(raw).trim());
    if (constant == null) {
      throw new IllegalArgumentException(what + " должно быть одним из: "
        + String.join(", ", new LinkedHashSet<>(forms.values())) + ", а не " + raw + ".");
    }
    return constant;
  }

  /** Имя константы и текст файла ({@code DATE_TIME} и {@code DateTime}) — одно значение. */
  private static Map<String, String> enumForms(String... constants) {
    Map<String, String> out = new LinkedHashMap<>();
    for (String constant : constants) {
      out.put(constant, constant);
      out.put(MdCatalogPropertiesGranularSerial.enumConstantToXmlText(constant), constant);
    }
    return out;
  }

  // ------------------------------------------------------------------ словарь

  /**
   * Проверка нового типа по словарю.
   *
   * <p>Элементы, уже записанные в этом {@code valueType}, с составом выгрузки не сверяются: у внешнего
   * отчёта записанные ссылки сохраняются, а новые не добавляются.
   */
  private static void check(MdTypeDescriptionDto fresh, MdTypeDescriptionDto current,
    DcsValueTypes.Context context, SchemaVersion version) throws IOException, JAXBException {
    ConfigurationObjects objects = new ConfigurationObjects(context, version);
    Set<String> seen = new HashSet<>();
    for (String type : fresh.types) {
      if (!seen.add(type)) {
        throw new IllegalArgumentException("Тип «" + type + "» указан дважды.");
      }
      if (DcsValueTypes.PRIMITIVE_QUALIFIERS.containsKey(type)) {
        continue;
      }
      boolean recorded = current != null && current.types.contains(type);
      if (type.startsWith(JOURNAL_PREFIX)) {
        throw journal(type);
      }
      if (!DcsValueTypes.isDictionaryType(type)) {
        throw outside(type, recorded);
      }
      if (!recorded) {
        int ref = type.indexOf("Ref.");
        objects.require(type, type.substring("cfg:".length(), ref), type.substring(ref + "Ref.".length()));
      }
    }
    for (String typeSet : fresh.typeSets) {
      if (!seen.add(typeSet)) {
        throw new IllegalArgumentException("Набор типов «" + typeSet + "» указан дважды.");
      }
      if (typeSet.equals(context.anyRef())) {
        continue;
      }
      boolean recorded = current != null && current.typeSets.contains(typeSet);
      if (typeSet.equals(DcsValueTypes.ANY_REF_DESIGNER) || typeSet.equals(DcsValueTypes.ANY_REF_EDT)) {
        String format = context.edt() ? "проекте 1С:EDT" : "выгрузке конфигуратора " + version.metadataObjectVersionAttribute();
        throw new IllegalArgumentException("«Любая ссылка» в " + format + " пишется «" + context.anyRef()
          + "», а не «" + typeSet + "».");
      }
      if (typeSet.startsWith(JOURNAL_PREFIX)) {
        throw journal(typeSet);
      }
      if (!DcsValueTypes.isDictionaryTypeSet(typeSet, context.anyRef())) {
        throw outside(typeSet, recorded);
      }
      if (recorded) {
        continue;
      }
      if (typeSet.startsWith(CHARACTERISTIC_PREFIX)) {
        objects.require(typeSet, "ChartOfCharacteristicTypes", typeSet.substring(CHARACTERISTIC_PREFIX.length()));
      } else {
        objects.requireConfiguration(typeSet);
      }
    }
    checkTypeIds(fresh, context);
    checkQualifiers(fresh);
  }

  private static void checkTypeIds(MdTypeDescriptionDto fresh, DcsValueTypes.Context context) {
    List<String> typeIds = ids(fresh);
    if (typeIds.isEmpty()) {
      return;
    }
    // Составной тип с определяемым платформа хранит, но байтов платформы для его записи нет
    if (typeIds.size() > 1 || !fresh.types.isEmpty() || !fresh.typeSets.isEmpty()) {
      throw new IllegalArgumentException(
        "Определяемый тип задаётся один, без других типов: составной тип с ним правится в конфигураторе.");
    }
    String typeId = typeIds.get(0);
    if (!UUID_TEXT.matcher(typeId).matches()) {
      throw new IllegalArgumentException("Идентификатор определяемого типа должен быть UUID, а не «" + typeId + "».");
    }
    if (context.configuration() == null) {
      throw new IllegalArgumentException("Определяемый тип «" + typeId
        + "» распознаётся только у макета в выгрузке конфигурации.");
    }
    if (context.definedTypeName(typeId) == null) {
      throw new IllegalArgumentException(
        "Определяемый тип с идентификатором «" + typeId + "» не найден в выгрузке конфигурации.");
    }
  }

  /**
   * Квалификаторы пишутся только при своём типе, а у строки, числа и даты обязательны: так пишет
   * платформа. Разрядность больше {@link #MAX_DIGITS} загрузка молча обрезает, поэтому это отказ.
   * Длину строки платформа хранит как записано, поэтому сверху она не ограничена.
   */
  private static void checkQualifiers(MdTypeDescriptionDto fresh) {
    if (fresh.binaryDataQualifiers != null) {
      throw new IllegalArgumentException(
        "Квалификаторы двоичных данных правятся в конфигураторе: двоичных данных нет в словаре редактора.");
    }
    requireQualifier(fresh, "xs:string", fresh.stringQualifiers != null, "stringQualifiers {length, allowedLength}");
    requireQualifier(fresh, "xs:decimal", fresh.numberQualifiers != null,
      "numberQualifiers {digits, fractionDigits, allowedSign}");
    requireQualifier(fresh, "xs:dateTime", fresh.dateQualifiers != null, "dateQualifiers {dateFractions}");
    if (fresh.stringQualifiers != null) {
      required(fresh.stringQualifiers.length, "stringQualifiers.length");
      required(fresh.stringQualifiers.allowedLength, "stringQualifiers.allowedLength");
    }
    if (fresh.numberQualifiers != null) {
      String digits = required(fresh.numberQualifiers.digits, "numberQualifiers.digits");
      String fraction = required(fresh.numberQualifiers.fractionDigits, "numberQualifiers.fractionDigits");
      required(fresh.numberQualifiers.allowedSign, "numberQualifiers.allowedSign");
      if (new BigDecimal(digits).compareTo(BigDecimal.valueOf(MAX_DIGITS)) > 0) {
        throw new IllegalArgumentException("Разрядность числа (" + digits + ") больше " + MAX_DIGITS
          + ": платформа сохранит только " + MAX_DIGITS + " разряда.");
      }
      if (new BigDecimal(fraction).compareTo(new BigDecimal(digits)) > 0) {
        throw new IllegalArgumentException("Дробная часть числа (" + fraction + ") больше разрядности (" + digits + ").");
      }
    }
    if (fresh.dateQualifiers != null) {
      required(fresh.dateQualifiers.dateFractions, "dateQualifiers.dateFractions");
    }
  }

  private static void requireQualifier(MdTypeDescriptionDto fresh, String type, boolean present, String form) {
    boolean typed = fresh.types.contains(type);
    String key = form.substring(0, form.indexOf(' '));
    if (typed && !present) {
      throw new IllegalArgumentException("У типа " + type + " нужны квалификаторы " + form + ".");
    }
    if (!typed && present) {
      throw new IllegalArgumentException("Квалификаторы " + key + " заданы без типа " + type + ".");
    }
  }

  private static String required(String value, String what) {
    if (value == null) {
      throw new IllegalArgumentException("Не задано " + what + ": квалификатор пишется целиком.");
    }
    return value;
  }

  private static IllegalArgumentException journal(String type) {
    return new IllegalArgumentException("У журнала документов нет ссылочного типа: «" + type + "» не записать.");
  }

  private static IllegalArgumentException outside(String type, boolean recorded) {
    return new IllegalArgumentException(recorded
      ? "Тип «" + type + "» правится в конфигураторе: редактор переносит такой тип только без изменений."
      : "Тип «" + type + "» не из словаря редактора: такой тип задаётся в конфигураторе.");
  }

  /** Состав выгрузки над схемой: читается, только если в типе есть новая ссылка. */
  private static final class ConfigurationObjects {

    private final DcsValueTypes.Context context;
    private final SchemaVersion version;
    private Map<String, List<String>> byKind;

    ConfigurationObjects(DcsValueTypes.Context context, SchemaVersion version) {
      this.context = context;
      this.version = version;
    }

    /** Новый тип конфигурации допустим только у макета в выгрузке: вне её состав базы неизвестен. */
    void requireConfiguration(String type) {
      if (context.configuration() == null) {
        throw new IllegalArgumentException("Новый тип «" + type + "» доступен только макету в выгрузке конфигурации: "
          + "вне её новыми выбираются примитивы и «любая ссылка».");
      }
    }

    /** Объект вида с именем есть в выгрузке: иначе платформа не загрузит схему с таким типом. */
    void require(String type, String kind, String name) throws IOException, JAXBException {
      requireConfiguration(type);
      if (byKind == null) {
        byKind = DcsTypeDictionary.objects(context, version);
      }
      List<String> names = byKind.get(kind);
      if (names == null || !names.contains(name)) {
        throw new IllegalArgumentException("Объекта для типа «" + type + "» нет в выгрузке конфигурации.");
      }
    }
  }

  // ------------------------------------------------------------------ значение параметра

  /** «Значение не задано» параметра схемы: так его пишет платформа. */
  static final String NIL_VALUE = "<value xsi:nil=\"true\"/>";

  /**
   * Элементы {@code value} параметра схемы по payload в форме платформы.
   *
   * <p>Без списка платформа всегда пишет {@code value} (пустое как {@code xsi:nil}); при
   * {@code valueListAllowed=true} пустое значение означает ни одного {@code value}. Список хранится
   * повторяющимися {@code value} без представлений, поэтому представление элемента отклоняется.
   * {@code {nil: true}} загрузка платформы отбрасывает рядом с другими элементами списка, поэтому
   * он допустим только единственным.
   *
   * @return элементы по порядку, по одной строке; пустой список означает ни одного {@code value}
   * @throws IllegalArgumentException если значение не по форме, список задан без
   *     {@code valueListAllowed}, у элемента есть представление или {@code nil} стоит рядом с другими
   */
  static List<String> parameterValuesXml(Object raw, boolean listAllowed) {
    if (isValueList(raw)) {
      if (!listAllowed) {
        throw new IllegalArgumentException("Список значений у параметра схемы пишется только при "
          + "valueListAllowed=true: задайте его в том же payload.");
      }
      return listValuesXml((Map<?, ?>) raw);
    }
    String single = raw == null ? NIL_VALUE : parameterValueXml(raw);
    if (NIL_VALUE.equals(single)) {
      return listAllowed ? List.of() : List.of(NIL_VALUE);
    }
    return List.of(single);
  }

  /** Значение из payload — список значений (а не «не задано»). */
  static boolean isValueList(Object raw) {
    return raw instanceof Map<?, ?> value && VALUE_LIST.equals(value.get("type"))
      && !Boolean.TRUE.equals(value.get("nil"));
  }

  /** Элементы списка значений параметра схемы: по {@code value} на элемент, без представлений. */
  private static List<String> listValuesXml(Map<?, ?> value) {
    for (Map.Entry<?, ?> entry : value.entrySet()) {
      if (entry.getValue() != null && !List.of("type", "list").contains(String.valueOf(entry.getKey()))) {
        throw new IllegalArgumentException("Неизвестное свойство значения: " + entry.getKey());
      }
    }
    if (!(value.get("list") instanceof Map<?, ?> list)) {
      throw new IllegalArgumentException("обязательное поле не задано: list");
    }
    for (Map.Entry<?, ?> entry : list.entrySet()) {
      if (entry.getValue() != null && !"items".equals(String.valueOf(entry.getKey()))) {
        throw new IllegalArgumentException("Неизвестное свойство list: " + entry.getKey());
      }
    }
    Object rawItems = list.get("items");
    if (rawItems == null) {
      return List.of();
    }
    if (!(rawItems instanceof List<?> items)) {
      throw new IllegalArgumentException("Значение list.items должно быть списком объектов {value}.");
    }
    List<String> out = new ArrayList<>();
    for (Object rawItem : items) {
      if (!(rawItem instanceof Map<?, ?> item)) {
        throw new IllegalArgumentException("Элемент списка значений должен быть объектом {value}.");
      }
      for (Map.Entry<?, ?> entry : item.entrySet()) {
        if (entry.getValue() != null && !List.of("value", "presentation").contains(String.valueOf(entry.getKey()))) {
          throw new IllegalArgumentException("Неизвестное свойство элемента списка значений: " + entry.getKey());
        }
      }
      Object presentation = item.get("presentation");
      if (presentation != null && !"".equals(presentation)) {
        throw new IllegalArgumentException("Представление «" + presentation + "» элемента списка значений не "
          + "записать: платформа не хранит представления значений параметра схемы (загрузка оставляет одни "
          + "значения). Уберите presentation.");
      }
      if (item.get("value") == null) {
        throw new IllegalArgumentException("У элемента списка значений не задано value.");
      }
      out.add(parameterValueXml(item.get("value"), true));
    }
    // Сверка по выходной строке: {nil: true, type: "v8:UUID"} пишется тем же xsi:nil
    if (out.size() > 1 && out.contains(NIL_VALUE)) {
      throw new IllegalArgumentException("Элемент «значение не задано» (nil) рядом с другими элементами списка "
        + "значений не записать: загрузка платформы такой элемент отбрасывает "
        + "и оставляет одни заданные значения. Уберите его из списка или оставьте единственным элементом.");
    }
    return out;
  }

  /**
   * Элемент {@code value} параметра одной строкой; {@code null} даёт пустую строку. {@code v8:UUID}
   * пишется здесь: писатель значений настроек его не знает. Что из этого записать у параметра,
   * решает {@link #parameterValuesXml}.
   */
  static String parameterValueXml(Object raw) {
    return parameterValueXml(raw, false);
  }

  /** @param scalarOnly элемент списка значений: допустимы только простые скаляры */
  private static String parameterValueXml(Object raw, boolean scalarOnly) {
    if (raw == null) {
      return "";
    }
    // {nil: true} с типом UUID пишет общий писатель
    if (raw instanceof Map<?, ?> value && "v8:UUID".equals(value.get("type"))
      && !Boolean.TRUE.equals(value.get("nil"))) {
      for (Object key : value.keySet()) {
        if (!List.of("type", "text", "decoded").contains(String.valueOf(key))) {
          throw new IllegalArgumentException("Неизвестное свойство значения v8:UUID: " + key);
        }
      }
      Object text = value.get("text") != null ? value.get("text") : value.get("decoded");
      if (!(text instanceof String uuid) || !UUID_TEXT.matcher(uuid.trim()).matches()) {
        throw new IllegalArgumentException(
          "Значение v8:UUID пишется идентификатором вида 00000000-0000-0000-0000-000000000000, а не " + text + ".");
      }
      return "<value xsi:type=\"v8:UUID\">" + uuid.trim() + "</value>";
    }
    return DcsSettingsEdit.valueXml("value", raw, scalarOnly);
  }

  /**
   * Сверка значения параметра с итоговым типом. Если {@code value} в payload нет, а тип меняется,
   * сверяется значение в файле: параметр не должен остаться со значением чужого типа.
   *
   * @param templateXml файл схемы: по выгрузке над ним раскрывается состав определяемого типа
   * @param typeEdit правка типа; {@code null} — тип в payload не задан
   * @param valuesXml новые элементы {@code value} ({@link #parameterValuesXml}); {@code null} — значения
   *     в payload нет
   * @param list новые элементы — список значений: каждый сверяется как элемент списка
   */
  static void checkParameterValue(Path templateXml, String text, List<XmlLines.Node> children, Edit typeEdit,
    List<String> valuesXml, boolean list, SchemaVersion version) {
    MdTypeDescriptionDto type = withDefinedTypes(
      typeEdit != null ? typeEdit.fresh() : current(text, children, version),
      DcsValueTypes.Context.of(templateXml, version));
    if (valuesXml != null) {
      for (String valueXml : valuesXml) {
        if (!fits(valueXml, type, list)) {
          throw new IllegalArgumentException("Значение параметра не подходит к его типу: укажите значение этого "
            + "типа или null (значение не задано).");
        }
      }
      return;
    }
    if (typeEdit == null || !typeEdit.changed()) {
      return;
    }
    List<XmlLines.Node> written = children.stream().filter(child -> "value".equals(child.name())).toList();
    // Список в файле: несколько value либо одно при valueListAllowed
    boolean writtenList = written.size() > 1 || DcsRead.valueListAllowed(text, children);
    for (XmlLines.Node child : written) {
      if (!fits(text.substring(child.start(), child.end()), type, writtenList)) {
        throw new IllegalArgumentException(
          "Значение параметра не подходит к новому типу: укажите значение или очистите его.");
      }
    }
  }

  /**
   * Тип с раскрытым составом определяемых типов: в схеме записан только UUID, а значение сверяется
   * с составом. Не найденный определяемый тип не раскрывается, к нему подходит только «не задано».
   */
  private static MdTypeDescriptionDto withDefinedTypes(MdTypeDescriptionDto type, DcsValueTypes.Context context) {
    if (type == null || ids(type).isEmpty()) {
      return type;
    }
    MdTypeDescriptionDto out = new MdTypeDescriptionDto();
    out.types.addAll(type.types);
    out.typeSets.addAll(type.typeSets);
    out.typeIds = type.typeIds;
    for (String typeId : type.typeIds) {
      MdTypeDescriptionDto composition = context.definedTypeComposition(typeId);
      if (composition != null) {
        out.types.addAll(composition.types);
        out.typeSets.addAll(composition.typeSets);
      }
    }
    return out;
  }

  /**
   * Подходит ли значение к типу.
   *
   * <p>Всегда подходят {@code xsi:nil} и пустая строка (у элемента списка - только к строке).
   * Скаляры {@code xs:*}, стандартные период и дата начала и UUID подходят к тому же типу. Ссылка
   * конфигуратора ({@code dcscor:DesignTimeValue}) подходит к ссылке того же объекта, набору его
   * вида и «любой ссылке». Список сверяется по элементам, определяемый тип - по составу из
   * {@link #withDefinedTypes}. Прочее (поле, тип вне словаря, значение без {@code xsi:type}) не
   * подходит.
   *
   * @param item элемент списка значений
   */
  static boolean fits(String valueXml, MdTypeDescriptionDto type, boolean item) {
    if (isEmpty(type)) {
      return true;
    }
    String startTag = valueXml.substring(0, XmlLines.startTagEnd(valueXml, 0) + 1);
    if (XSI_NIL.matcher(startTag).find()) {
      return true;
    }
    Matcher typed = XSI_TYPE.matcher(startTag);
    if (!typed.find()) {
      return false;
    }
    String valueType = typed.group(1);
    if ("v8:ValueListType".equals(valueType)) {
      Matcher items = LIST_ITEM_VALUE.matcher(valueXml);
      while (items.find()) {
        String attributes = items.group(1);
        if (XSI_NIL.matcher(attributes).find()) {
          continue;
        }
        Matcher itemType = XSI_TYPE.matcher(attributes);
        if (!itemType.find() || !fitsTyped(itemType.group(1), items.group(2), type)) {
          return false;
        }
      }
      return true;
    }
    int bodyStart = startTag.length();
    int bodyEnd = valueXml.lastIndexOf("</");
    String body = startTag.endsWith("/>") || bodyEnd < bodyStart ? "" : valueXml.substring(bodyStart, bodyEnd);
    if ("xs:string".equals(valueType) && body.isEmpty() && !item) {
      return true;
    }
    return fitsTyped(valueType, body, type);
  }

  private static boolean fitsTyped(String valueType, String body, MdTypeDescriptionDto type) {
    if (TYPED_VALUES.contains(valueType)) {
      return type.types.contains(valueType);
    }
    if (!"dcscor:DesignTimeValue".equals(valueType) || body == null) {
      return false;
    }
    // Путь «Вид.Имя.Значение», вид русским или английским именем
    String[] parts = body.trim().split("\\.");
    if (parts.length < 2) {
      return false;
    }
    String kind = refKind(parts[0]);
    if (kind == null) {
      return false;
    }
    String refType = "cfg:" + kind + "Ref";
    return type.types.contains(refType + "." + parts[1])
      || type.typeSets.contains(refType)
      || type.typeSets.contains(DcsValueTypes.ANY_REF_DESIGNER)
      || type.typeSets.contains(DcsValueTypes.ANY_REF_EDT);
  }

  /** Вид объекта со ссылочным типом по имени вида в пути значения; {@code null} — не такой вид. */
  private static String refKind(String name) {
    for (MdObjectAddType kind : MdObjectAddType.values()) {
      if ((kind.configurationXmlTag().equals(name) || kind.namePrefix().equals(name))
        && DcsValueTypes.REF_KINDS.contains(kind.configurationXmlTag())) {
        return kind.configurationXmlTag();
      }
    }
    return null;
  }

  // ------------------------------------------------------------------ помощники

  private static List<String> ids(MdTypeDescriptionDto dto) {
    return dto.typeIds == null ? List.of() : dto.typeIds;
  }

  private static boolean sameIds(List<String> a, List<String> b) {
    if (a.size() != b.size()) {
      return false;
    }
    for (int i = 0; i < a.size(); i++) {
      if (!a.get(i).trim().equalsIgnoreCase(b.get(i).trim())) {
        return false;
      }
    }
    return true;
  }

  private static boolean sameString(
    MdTypeDescriptionDto.MdStringQualifiersDto a, MdTypeDescriptionDto.MdStringQualifiersDto b) {
    if (a == null || b == null) {
      return a == b;
    }
    return sameCount(a.length, b.length) && Objects.equals(a.allowedLength, b.allowedLength);
  }

  private static boolean sameNumber(
    MdTypeDescriptionDto.MdNumberQualifiersDto a, MdTypeDescriptionDto.MdNumberQualifiersDto b) {
    if (a == null || b == null) {
      return a == b;
    }
    return sameCount(a.digits, b.digits) && sameCount(a.fractionDigits, b.fractionDigits)
      && Objects.equals(a.allowedSign, b.allowedSign);
  }

  private static boolean sameDate(
    MdTypeDescriptionDto.MdDateQualifiersDto a, MdTypeDescriptionDto.MdDateQualifiersDto b) {
    if (a == null || b == null) {
      return a == b;
    }
    return Objects.equals(a.dateFractions, b.dateFractions);
  }

  private static boolean sameBinary(
    MdTypeDescriptionDto.MdBinaryDataQualifiersDto a, MdTypeDescriptionDto.MdBinaryDataQualifiersDto b) {
    if (a == null || b == null) {
      return a == b;
    }
    return sameCount(a.length, b.length) && Objects.equals(a.allowedLength, b.allowedLength);
  }

  /** Числа квалификаторов сравниваются как числа: {@code "0"} в файле и {@code 0} из JSON — одно. */
  private static boolean sameCount(String a, String b) {
    if (a == null || b == null) {
      return a == null && b == null;
    }
    try {
      return new BigDecimal(a.trim()).compareTo(new BigDecimal(b.trim())) == 0;
    } catch (NumberFormatException e) {
      return a.trim().equals(b.trim());
    }
  }

  /** Открывающий тег корня схемы: в нём объявлены префиксы, общие для всего файла. */
  private static String rootTag(String text) {
    int root = text.indexOf("<DataCompositionSchema");
    if (root < 0) {
      throw new IllegalArgumentException("Файл не является схемой компоновки данных.");
    }
    return text.substring(root, XmlLines.startTagEnd(text, root) + 1);
  }
}
